package com.backend.ytdownload.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class Config {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Autowired
    private RateLimitingInterceptor rateLimitingInterceptor;

    @Bean
    public WebMvcConfigurer webMvcConfigurer(){
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = "*".equals(allowedOrigins) 
                    ? new String[]{"*"} 
                    : allowedOrigins.split(",");
                
                registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedOrigins(origins)
                        .allowedHeaders("*")
                        .maxAge(3600);
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // Add rate limiting interceptor first (higher priority)
                registry.addInterceptor(rateLimitingInterceptor);
                // Add security headers interceptor
                registry.addInterceptor(new SecurityHeadersInterceptor());
            }
        };
    }

    /**
     * Interceptor to add security headers to all responses
     */
    public static class SecurityHeadersInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler) throws Exception {
            
            // Prevent MIME type sniffing
            response.setHeader("X-Content-Type-Options", "nosniff");
            
            // Prevent clickjacking attacks
            response.setHeader("X-Frame-Options", "DENY");
            
            // Enable XSS protection in older browsers
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // HSTS - enforce HTTPS (only for production)
            String scheme = request.getScheme();
            if ("https".equals(scheme)) {
                response.setHeader("Strict-Transport-Security", 
                    "max-age=31536000; includeSubDomains; preload");
            }
            
            // Content Security Policy - restrict resource loading
            response.setHeader("Content-Security-Policy", 
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; font-src 'self'; connect-src 'self'");
            
            // Referrer policy - control referrer information
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            return true;
        }
    }
}
