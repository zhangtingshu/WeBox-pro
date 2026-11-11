package com.webox.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * All runtime configuration comes from environment variables (see application.yml).
 * Bean validation runs at binding time, so a misconfigured deployment fails fast at startup.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "webox")
public class AppProperties {

    @NotBlank
    @Size(min = 32, message = "JWT secret must be at least 32 characters")
    private String jwtSecret;

    @Min(1)
    private int accessTokenMinutes = 30;

    @Min(1)
    private int refreshTokenDays = 7;

    @NotBlank
    private String corsOrigins = "http://localhost:5173,http://localhost:8081";

    @Min(1)
    private int menuCacheTtlSeconds = 30;

    @NotBlank
    private String llmBaseUrl = "https://api.openai.com/v1";

    /** Empty = built-in local recommendation engine (no external dependency). */
    private String llmApiKey = "";

    @NotBlank
    private String llmModel = "gpt-4o-mini";

    @NotBlank
    private String uploadDir = "/app/uploads";

    public boolean isLlmEnabled() {
        return llmApiKey != null && !llmApiKey.isBlank();
    }

    public String[] corsOriginArray() {
        return java.util.Arrays.stream(corsOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
