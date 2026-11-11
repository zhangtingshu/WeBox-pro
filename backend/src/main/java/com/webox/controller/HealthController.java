package com.webox.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "webox-backend");
        return body;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", "READY");
            body.put("database", "mysql");
            body.put("probe", result);
        } catch (Exception e) {
            body.put("status", "NOT_READY");
            body.put("database", "mysql");
        }
        return body;
    }
}
