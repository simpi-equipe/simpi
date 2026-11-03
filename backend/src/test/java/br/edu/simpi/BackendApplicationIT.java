package br.edu.simpi;

import br.edu.simpi.persistencia.ProtecaoBancoIntegracao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
class BackendApplicationIT {

    private final JdbcTemplate banco;
    private final Flyway flyway;

    @Autowired
    BackendApplicationIT(JdbcTemplate banco, Flyway flyway) {
        this.banco = banco;
        this.flyway = flyway;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void connectsToPostgresAndValidatesMigrations() {
        assertEquals(
                Integer.valueOf(1),
                banco.queryForObject("SELECT 1", Integer.class)
        );
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.info().pending().length);
    }
}
