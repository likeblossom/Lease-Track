package com.leasetrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Return service health status")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "lease-track");
    }
}
