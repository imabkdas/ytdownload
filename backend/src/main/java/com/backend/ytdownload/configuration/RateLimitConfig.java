package com.backend.ytdownload.configuration;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate Limiting Configuration
 * Configures Resilience4j rate limiters to prevent abuse and DoS attacks
 */
@Configuration
public class RateLimitConfig {

    /**
     * Create a rate limiter registry for managing multiple rate limiters
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }

    /**
     * Video download rate limiter: 10 requests per 60 seconds
     * Prevents excessive video download requests from the same client
     */
    @Bean(name = "videoDownloadLimiter")
    public RateLimiter videoDownloadLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(2))
                .build();

        return registry.rateLimiter("video-download", config);
    }

    /**
     * Audio download rate limiter: 10 requests per 60 seconds
     * Prevents excessive audio download requests from the same client
     */
    @Bean(name = "audioDownloadLimiter")
    public RateLimiter audioDownloadLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofSeconds(2))
                .build();

        return registry.rateLimiter("audio-download", config);
    }
}
