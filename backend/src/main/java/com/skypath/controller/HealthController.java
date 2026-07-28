package com.skypath.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Map;

@Controller("/health")
public class HealthController {

    @Get
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "skypath-backend"
        );
    }
}
