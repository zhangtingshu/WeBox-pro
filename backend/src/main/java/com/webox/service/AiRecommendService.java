package com.webox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webox.common.AppClock;
import com.webox.common.JsonLog;
import com.webox.common.Money;
import com.webox.common.TextUtil;
import com.webox.config.AppProperties;
import com.webox.dto.Dtos.AiDishView;
import com.webox.entity.DailyMenuEntity;
import com.webox.entity.DishEntity;
import com.webox.entity.PreferenceEntity;
import com.webox.repository.DailyMenuRepository;
import com.webox.repository.DishRepository;
import com.webox.repository.OrderRepository;
import com.webox.repository.PreferenceRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming AI meal assistant.
 * - With LLM_API_KEY: real SSE streaming from any OpenAI-compatible endpoint (OpenAI, Ollama, vLLM).
 * - Without a key: the built-in local ranking engine still streams results, so the feature
 *   is always runnable on-site without external credentials.
 * Hard constraints (allergens, last-7-day repeats, sold-out) are enforced by the SERVER,
 * never delegated to the model.
 */
@Service
public class AiRecommendService {
    private static final Pattern DISH_MARKER = Pattern.compile("\\[\\[DISH:(\\d+)\\]\\]");
    private static final String SYSTEM_PROMPT =
            "You are WeBox, an English-only canteen assistant. "
            + "Recommend ONLY dishes whose IDs appear in CANDIDATES. "
            + "Never recommend a dish containing a flagged allergen or one ordered in the last 7 days "
            + "(those are already excluded from CANDIDATES). "
            + "Output exactly three lines, each formatted as: [[DISH:12]] <short personalised English reason>. "
            + "No preamble, no markdown, no other text.";

    private record Candidate(DishEntity dish, int remaining, int score) {}

    private final DishRepository dishRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final PreferenceRepository preferenceRepository;
    private final OrderRepository orderRepository;
    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "webox-ai-stream");
        thread.setDaemon(true);
        return thread;
    });

    public AiRecommendService(DishRepository dishRepository,
                              DailyMenuRepository dailyMenuRepository,
                              PreferenceRepository preferenceRepository,
                              OrderRepository orderRepository,
                              AppProperties props) {
        this.dishRepository = dishRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.preferenceRepository = preferenceRepository;
        this.orderRepository = orderRepository;
        this.props = props;
    }

    public SseEmitter stream(Long userId, String rawQuery) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String query = TextUtil.clean(rawQuery, 500);
        executor.submit(() -> run(emitter, userId, query));
        return emitter;
    }

    private void run(SseEmitter emitter, Long userId, String query) {
        try {
            send(emitter, "status", Map.of("message",
                    "Reviewing today's menu, your preferences and your last 7 days of orders..."));
            LocalDate today = AppClock.today();
            PreferenceEntity preference = preferenceRepository.findById(userId).orElse(null);
            List<String> flagged = preference == null ? List.of()
                    : TextUtil.splitCsv(preference.getAllergens());
            Set<Long> recent = new HashSet<>(orderRepository.recentlyOrderedDishIds(
                    userId, AppClock.now().minusDays(7)));

            Map<Long, Integer> stock = new HashMap<>();
            for (DailyMenuEntity row : dailyMenuRepository.findByMenuDate(today)) {
                stock.put(row.getDishId(), row.getRemainingQty());
            }
            List<Candidate> candidates = new ArrayList<>();
            for (DishEntity dish : dishRepository.findAllById(stock.keySet())) {
                int remaining = stock.getOrDefault(dish.getId(), 0);
                if (!dish.isActive() || remaining <= 0) continue;
                if (!AllergenMatcher.conflicts(TextUtil.splitCsv(dish.getAllergens()), flagged).isEmpty()) continue;
                if (recent.contains(dish.getId())) continue;
                candidates.add(new Candidate(dish, remaining,
                        score(dish, preference, query)));
            }
            candidates.sort(Comparator.<Candidate>comparingInt(Candidate::score)
                    .reversed().thenComparing(c -> c.dish().getId()));
            if (candidates.isEmpty()) {
                send(emitter, "status", Map.of("message",
                        "No suitable dishes are available today. Please adjust your preferences."));
                send(emitter, "done", Map.of("count", 0, "engine", "local"));
                emitter.complete();
                return;
            }

            Map<Long, String> llmReasons = new LinkedHashMap<>();
            List<Long> chosen = new ArrayList<>();
            String engine = "local";
            if (props.isLlmEnabled()) {
                try {
                    send(emitter, "status", Map.of("message",
                            "Connected to " + props.getLlmModel() + ". Generating your recommendations..."));
                    String text = streamFromLlm(emitter, candidates, preference, query, flagged, recent);
                    llmReasons.putAll(parseReasons(text));
                    for (Long id : llmReasons.keySet()) {
                        if (candidates.stream().anyMatch(c -> c.dish().getId().equals(id))) {
                            chosen.add(id);
                        }
                    }
                    engine = "llm";
                } catch (Exception e) {
                    JsonLog.warn("llm_stream_fallback", "detail", String.valueOf(e.getMessage()));
                    send(emitter, "status", Map.of("message",
                            "The external model is unavailable. Using the built-in recommendation engine."));
                }
            } else {
                send(emitter, "status", Map.of("message",
                        "Using the built-in local recommendation engine (no LLM key configured)."));
            }

            if (chosen.isEmpty()) {
                chosen = candidates.stream().limit(3).map(c -> c.dish().getId()).toList();
                engine = "local";
                for (Long id : chosen) {
                    Candidate candidate = find(candidates, id);
                    if (candidate == null) continue;
                    streamReason(emitter, buildReason(candidate, preference, query));
                }
            }

            List<AiDishView> results = new ArrayList<>();
            for (Long id : chosen.stream().distinct().limit(3).toList()) {
                Candidate candidate = find(candidates, id);
                if (candidate == null) continue;
                DishEntity dish = candidate.dish();
                String reason = llmReasons.getOrDefault(id, buildReason(candidate, preference, query));
                AiDishView view = new AiDishView(dish.getId(), dish.getName(), reason,
                        Money.toCents(dish.getPrice()), dish.getImageUrl(), dish.getCategory(),
                        candidate.remaining());
                results.add(view);
                send(emitter, "dish", view);
                Thread.sleep(120L); // cards arrive progressively, not as one blocking payload
            }
            send(emitter, "done", Map.of("count", results.size(), "engine", engine));
            emitter.complete();
            JsonLog.info("ai_recommendation", "userId", userId, "count", results.size(), "engine", engine);
        } catch (Exception e) {
            JsonLog.error("ai_stream_failed", "detail", String.valueOf(e.getMessage()));
            try {
                send(emitter, "status", Map.of("message",
                        "The assistant could not finish this request. Please try again."));
                send(emitter, "done", Map.of("count", 0, "engine", "error"));
            } catch (Exception ignored) {
                // client already disconnected
            }
            emitter.complete();
        }
    }

    private String streamFromLlm(SseEmitter emitter, List<Candidate> candidates,
                                 PreferenceEntity preference, String query,
                                 List<String> flagged, Set<Long> recent) throws Exception {
        String url = props.getLlmBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        List<Map<String, Object>> candidateJson = candidates.stream().limit(12)
                .map(c -> Map.<String, Object>of(
                        "id", c.dish().getId(),
                        "name", c.dish().getName(),
                        "category", c.dish().getCategory(),
                        "protein", c.dish().getProtein(),
                        "allergens", TextUtil.splitCsv(c.dish().getAllergens()),
                        "spice", c.dish().getSpiceLevel(),
                        "description", c.dish().getDescription(),
                        "priceCny", Money.toDecimal(Money.toCents(c.dish().getPrice())).toPlainString()))
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("candidates", candidateJson);
        context.put("preferences", preference == null ? Map.of() : PreferenceService.toView(preference));
        context.put("flaggedAllergens", flagged);
        context.put("excludedRecentDishIds", recent);
        context.put("userRequest", query);

        String body = mapper.writeValueAsString(Map.of(
                "model", props.getLlmModel(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", mapper.writeValueAsString(context)))));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getLlmApiKey())
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("LLM returned HTTP " + response.statusCode());
        }
        StringBuilder accumulated = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JsonNode node = mapper.readTree(data);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        accumulated.append(delta);
                        send(emitter, "delta", Map.of("text", delta));
                    }
                } catch (Exception ignored) {
                    // keep-alive or non-JSON chunk
                }
            }
        }
        return accumulated.toString();
    }

    private static Map<Long, String> parseReasons(String text) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return result;
        Matcher matcher = DISH_MARKER.matcher(text);
        List<int[]> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new int[]{matcher.start(), matcher.end(),
                    Integer.parseInt(matcher.group(1))});
        }
        for (int i = 0; i < markers.size(); i++) {
            int[] current = markers.get(i);
            int end = i + 1 < markers.size() ? markers.get(i + 1)[0] : text.length();
            String reason = text.substring(current[1], end).replaceAll("\\s+", " ").trim();
            if (!reason.isEmpty() && result.size() < 3) {
                result.put((long) current[2], reason);
            }
        }
        return result;
    }

    private int score(DishEntity dish, PreferenceEntity preference, String query) {
        int value = 0;
        String lowered = query.toLowerCase(Locale.ROOT);
        String haystack = (dish.getName() + " " + dish.getDescription() + " "
                + dish.getCategory() + " " + dish.getProtein()).toLowerCase(Locale.ROOT);
        for (String word : lowered.split("[^a-z]+")) {
            if (word.length() >= 4 && haystack.contains(word)) value += 4;
        }
        if (lowered.contains("light") && dish.getCategory().equals("Light Meal")) value += 6;
        if (lowered.contains("protein") && !"None".equals(dish.getProtein())) value += 6;
        if (lowered.contains("spicy") && !"None".equals(dish.getSpiceLevel())) value += 5;
        if (preference != null) {
            if (TextUtil.splitCsv(preference.getCuisines()).contains(dish.getCategory())) value += 5;
            if (preference.getSpiceLevel().equals(dish.getSpiceLevel())) value += 3;
            long budgetMax = Money.toCents(preference.getBudgetMax());
            if (budgetMax > 0 && Money.toCents(dish.getPrice()) <= budgetMax) value += 2;
            if ("Light".equals(preference.getTaste())
                    && dish.getCategory().equals("Light Meal")) value += 2;
        }
        return value;
    }

    private static String buildReason(Candidate candidate, PreferenceEntity preference, String query) {
        DishEntity dish = candidate.dish();
        List<String> clauses = new ArrayList<>();
        if (preference != null) {
            List<String> cuisines = TextUtil.splitCsv(preference.getCuisines());
            if (cuisines.contains(dish.getCategory())) {
                clauses.add("it matches your " + dish.getCategory() + " preference");
            }
            if (preference.getSpiceLevel().equals(dish.getSpiceLevel())) {
                clauses.add("suits your " + dish.getSpiceLevel() + " spice level");
            }
            long budgetMax = Money.toCents(preference.getBudgetMax());
            if (budgetMax > 0 && Money.toCents(dish.getPrice()) <= budgetMax) {
                clauses.add("stays within your " + Money.format(budgetMax) + " budget");
            }
            if (!TextUtil.splitCsv(preference.getAllergens()).isEmpty()) {
                clauses.add("avoids every allergen you flagged");
            }
        }
        if (!"None".equals(dish.getProtein())) {
            clauses.add("provides " + dish.getProtein() + " protein");
        }
        clauses.add("you have not ordered it in the last 7 days");
        String request = query.isBlank() ? "" : " Given your request \"" + query + "\",";
        return "Recommended because " + String.join(", ", clauses) + "." + request
                + " Only " + candidate.remaining() + " portions remain today.";
    }

    private static Candidate find(List<Candidate> candidates, Long id) {
        return candidates.stream().filter(c -> c.dish().getId().equals(id)).findFirst().orElse(null);
    }

    private void streamReason(SseEmitter emitter, String text) throws InterruptedException {
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i += 3) {
            String chunk = String.join(" ",
                    Arrays.copyOfRange(words, i, Math.min(i + 3, words.length))) + " ";
            send(emitter, "delta", Map.of("text", chunk));
            Thread.sleep(30L);
        }
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (Exception e) {
            throw new IllegalStateException("SSE client disconnected", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
