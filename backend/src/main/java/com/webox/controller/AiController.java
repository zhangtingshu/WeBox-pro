package com.webox.controller;

import com.webox.common.ApiResponse;
import com.webox.common.RequestContext;
import com.webox.config.AppProperties;
import com.webox.dto.Dtos.AiRequest;
import com.webox.service.AiRecommendService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiRecommendService aiService;
    private final AppProperties props;

    public AiController(AiRecommendService aiService, AppProperties props) {
        this.aiService = aiService;
        this.props = props;
    }

    @PostMapping(value = "/recommend", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommend(@Valid @RequestBody AiRequest request) {
        return aiService.stream(RequestContext.userId(), request.query());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "engine", props.isLlmEnabled() ? "openai-compatible-llm" : "local-ranking-engine",
                "model", props.isLlmEnabled() ? props.getLlmModel() : "built-in",
                "streaming", true));
    }
}
