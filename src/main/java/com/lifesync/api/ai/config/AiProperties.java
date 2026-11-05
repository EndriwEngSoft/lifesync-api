package com.lifesync.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "lifesync.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        Duration timeout,
        int maxTokens
) {}