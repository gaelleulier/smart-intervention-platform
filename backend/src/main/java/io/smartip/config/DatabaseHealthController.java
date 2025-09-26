package io.smartip.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;

@RestController
public class DatabaseHealthController {
    private final JdbcTemplate jdbcTemplate;
    public DatabaseHealthController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP", "db", one);
    }
}