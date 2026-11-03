package br.edu.simpi.persistencia;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtecaoBancoIntegracaoIT {

    @Test
    void aceitaSomenteEnderecosLocaisExplicitos() {
        for (String endereco : new String[] {
                "jdbc:postgresql://localhost/simpi_test",
                "jdbc:postgresql://127.0.0.1:5433/simpi_test",
                "jdbc:postgresql://127.0.0.1:5432/simpi_test",
                "jdbc:postgresql://[::1]:5433/simpi_test",
                "jdbc:postgresql://[::1]/simpi_test",
                "jdbc:postgresql://localhost:65535/simpi_test"
        }) {
            assertDoesNotThrow(() -> inicializar(Map.of("spring.datasource.url", endereco)));
        }
    }

    @Test
    void rejeitaEnderecosAmbiguosRemotosOuComParametros() {
        for (String endereco : new String[] {
                "jdbc:postgresql://localhost/simpi_dev",
                "jdbc:postgresql://servidor.example.invalid/simpi_test",
                "jdbc:postgresql://localhost.example.invalid/simpi_test",
                "jdbc:postgresql://127.1/simpi_test",
                "jdbc:postgresql://2130706433/simpi_test",
                "jdbc:postgresql://0.0.0.0/simpi_test",
                "jdbc:postgresql://[::ffff:127.0.0.1]/simpi_test",
                "jdbc:postgresql://::1/simpi_test",
                "jdbc:postgresql://localhost,127.0.0.1/simpi_test",
                "jdbc:postgresql://usuario:segredo@localhost/simpi_test",
                "jdbc:postgresql://localhost:0/simpi_test",
                "jdbc:postgresql://localhost:05432/simpi_test",
                "jdbc:postgresql://localhost:65536/simpi_test",
                "jdbc:postgresql://localhost/simpi_test?currentSchema=public",
                "jdbc:postgresql://localhost/simpi_test#fragmento",
                "jdbc:postgresql://localhost/simpi%5ftest",
                "jdbc:postgresql:simpi_test",
                "jdbc:h2:mem:simpi_test",
                " jdbc:postgresql://localhost/simpi_test",
                ""
        }) {
            assertThrows(IllegalStateException.class,
                    () -> inicializar(Map.of("spring.datasource.url", endereco)));
        }
    }

    @Test
    void rejeitaAusenciaPlaceholderNaoResolvidoEConexoesAlternativas() {
        assertThrows(IllegalStateException.class, () -> inicializar(Map.of()));
        assertThrows(IllegalStateException.class, () -> inicializar(Map.of(
                "spring.datasource.url", "${ENDERECO_INEXISTENTE_PARA_TESTE}")));
        for (String propriedade : new String[] {
                "spring.flyway.url", "spring.datasource.hikari.jdbc-url", "spring.datasource.jndi-name"
        }) {
            assertThrows(IllegalStateException.class, () -> inicializar(Map.of(
                    "spring.datasource.url", "jdbc:postgresql://localhost/simpi_test",
                    propriedade, "alternativa")));
        }
    }

    @Test
    void resolveVariavelDoAmbienteSemIniciarContextoOuConexao() {
        assertDoesNotThrow(() -> inicializar(Map.of(
                "spring.datasource.url", "${DB_URL}",
                "DB_URL", "jdbc:postgresql://127.0.0.1:5433/simpi_test")));
    }

    @Test
    void naoExpoeEnderecoCredenciaisOuCausaNaFalha() {
        IllegalStateException falha = assertThrows(IllegalStateException.class,
                () -> inicializar(Map.of("spring.datasource.url",
                        "jdbc:postgresql://usuario:segredo@remoto/simpi_test")));
        assertFalse(falha.getMessage().contains("jdbc:"));
        assertFalse(falha.getMessage().contains("segredo"));
        assertNull(falha.getCause());
    }

    private void inicializar(Map<String, Object> propriedades) {
        try (GenericApplicationContext contexto = new GenericApplicationContext()) {
            contexto.setEnvironment(new MockEnvironment());
            contexto.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("propriedadesSinteticas", propriedades));
            new ProtecaoBancoIntegracao().initialize(contexto);
            assertFalse(contexto.isActive());
        }
    }
}
