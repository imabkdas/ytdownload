package com.backend.ytdownload.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ValidationUtil {
    
    private static final Set<String> VALID_QUALITIES = Set.of("HQ", "720", "480");
    private static final Set<String> VALID_FORMATS = Set.of("mp4", "webm", "mkv");
    private static final List<String> VALID_YOUTUBE_DOMAINS = Arrays.asList(
        "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com"
    );
    
    public static void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost().toLowerCase();
            
            // Check if it's a YouTube URL
            boolean isValidDomain = VALID_YOUTUBE_DOMAINS.stream()
                .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            
            if (!isValidDomain) {
                throw new IllegalArgumentException("Invalid YouTube URL: " + url);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format: " + url, e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid URL format: " + url, e);
        }
    }
    
    public static void validateQuality(String quality) {
        if (quality == null || quality.trim().isEmpty()) {
            throw new IllegalArgumentException("Quality cannot be null or empty");
        }
        
        if (!VALID_QUALITIES.contains(quality.toUpperCase())) {
            throw new IllegalArgumentException("Invalid quality. Allowed values: " + VALID_QUALITIES);
        }
    }
    
    public static void validateFormat(String format) {
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Format cannot be null or empty");
        }
        
        String lowerFormat = format.toLowerCase();
        if (!VALID_FORMATS.contains(lowerFormat)) {
            throw new IllegalArgumentException("Invalid format. Allowed values: " + VALID_FORMATS);
        }
    }
}

