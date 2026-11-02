package br.edu.simpi;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoads() {
    }

    @Test
    void connectsToPostgresAndValidatesMigrations() {
        assertEquals(
                Integer.valueOf(1),
                jdbcTemplate.queryForObject("SELECT 1", Integer.class)
        );
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }
}
