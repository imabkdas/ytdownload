package com.backend.ytdownload.configuration;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate Limiting Interceptor
 * Enforces rate limits on download endpoints to prevent abuse
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);
    
    private static final String VIDEO_DOWNLOAD_PATH = "/api/video/download";
    private static final String AUDIO_DOWNLOAD_PATH = "/api/audio/download";
    
    // Rate limiters for different endpoints
    private RateLimiter videoDownloadLimiter;
    private RateLimiter audioDownloadLimiter;

    /**
     * Constructor - inject rate limiters
     */
    public RateLimitingInterceptor(
            RateLimiter videoDownloadLimiter,
            RateLimiter audioDownloadLimiter) {
        this.videoDownloadLimiter = videoDownloadLimiter;
        this.audioDownloadLimiter = audioDownloadLimiter;
    }

    /**
     * Pre-handle: Apply rate limiting before request is processed
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);

        try {
            if (path.contains(VIDEO_DOWNLOAD_PATH)) {
                if (!videoDownloadLimiter.acquirePermission()) {
                    logRateLimitExceeded(clientIp, VIDEO_DOWNLOAD_PATH);
                    sendRateLimitResponse(response);
                    return false;
                }
                log.debug("Video download request permitted from IP: {}", clientIp);
            } 
            else if (path.contains(AUDIO_DOWNLOAD_PATH)) {
                if (!audioDownloadLimiter.acquirePermission()) {
                    logRateLimitExceeded(clientIp, AUDIO_DOWNLOAD_PATH);
                    sendRateLimitResponse(response);
                    return false;
                }
                log.debug("Audio download request permitted from IP: {}", clientIp);
            }
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for IP: {}, Path: {}", clientIp, path);
            sendRateLimitResponse(response);
            return false;
        }

        return true;
    }

    /**
     * Get client IP address from request
     * Handles X-Forwarded-For header for proxied requests
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, get the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Send rate limit exceeded response
     */
    private void sendRateLimitResponse(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        
        String jsonResponse = "{\"error\": \"Too many requests. Please wait before making another request.\", " +
                "\"message\": \"Rate limit: 10 requests per 60 seconds\"}";
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    /**
     * Log rate limit exceeded events
     */
    private void logRateLimitExceeded(String clientIp, String path) {
        log.warn("Rate limit exceeded - IP: {}, Path: {}, Limit: 10 requests per 60 seconds", 
                clientIp, path);
    }
}
