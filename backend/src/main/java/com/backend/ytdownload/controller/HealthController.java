package com.backend.ytdownload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for ECS and monitoring systems.
 * Provides endpoints for liveness and readiness probes.
 */
@RestController
public class HealthController {

    /**
     * Liveness probe endpoint
     * Used by ECS to verify if the container is still running
     *
     * @return Health status with HTTP 200
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis() + "");
        return ResponseEntity.ok(response);
    }
}
