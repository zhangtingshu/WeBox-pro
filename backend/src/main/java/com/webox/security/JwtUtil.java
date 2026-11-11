package com.webox.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webox.common.ApiException;
import com.webox.common.ErrorCode;
import com.webox.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived HS256 access token. Refresh tokens live only in the database + httpOnly cookie. */
@Component
public class JwtUtil {
    public record Claims(long userId, String email, String name, String role) {}

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtUtil(AppProperties props) {
        this.props = props;
    }

    public String issue(long userId, String email, String name, String role) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("email", email);
        payload.put("name", name == null ? "" : name);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + props.getAccessTokenMinutes() * 60L);
        payload.put("jti", UUID.randomUUID().toString());
        String headerPart = encode(jsonBytes(Map.of("alg", "HS256", "typ", "JWT")));
        String payloadPart = encode(jsonBytes(payload));
        String signingInput = headerPart + "." + payloadPart;
        return signingInput + "." + encode(sign(signingInput));
    }

    public Claims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Please sign in to continue.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session is invalid. Please sign in again.");
        }
        String expected = encode(sign(parts[0] + "." + parts[1]));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session is invalid. Please sign in again.");
        }
        try {
            Map<String, Object> payload = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            if (exp <= Instant.now().getEpochSecond()) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session has expired. Please refresh.");
            }
            return new Claims(
                    Long.parseLong(String.valueOf(payload.get("sub"))),
                    String.valueOf(payload.get("email")),
                    String.valueOf(payload.getOrDefault("name", "")),
                    String.valueOf(payload.get("role")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Your session is invalid. Please sign in again.");
        }
    }

    private byte[] sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private byte[] jsonBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("JWT serialisation failed", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
