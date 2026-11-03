package br.edu.simpi.persistencia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Executada antes da criação de beans, inclusive DataSource e Flyway. */
public final class ProtecaoBancoIntegracao
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern ENDERECO_PERMITIDO = Pattern.compile(
            "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])"
                    + "(?::([1-9][0-9]{0,4}))?/simpi_test");
    private static final String MENSAGEM_BLOQUEIO =
            "Integração bloqueada: use PostgreSQL local explícito e banco simpi_test, "
                    + "sem parâmetros ou endereços alternativos. Execução remota exige revisão da proteção.";

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        try {
            ConfigurableEnvironment ambiente = contexto.getEnvironment();
            String endereco = ambiente.getRequiredProperty("spring.datasource.url");
            Matcher correspondencia = ENDERECO_PERMITIDO.matcher(endereco);
            if (!correspondencia.matches()) {
                throw new IllegalStateException();
            }
            String porta = correspondencia.group(1);
            if (porta != null && Integer.parseInt(porta) > 65535) {
                throw new IllegalStateException();
            }
            // Impede que outra configuração desvie a conexão após validar a URL principal.
            for (String propriedade : new String[] {
                    "spring.flyway.url", "spring.datasource.hikari.jdbc-url", "spring.datasource.jndi-name"
            }) {
                if (ambiente.containsProperty(propriedade)) {
                    throw new IllegalStateException();
                }
            }
        } catch (RuntimeException excecao) {
            // Não propagar a causa: erros de resolução podem conter URL ou credenciais.
            throw new IllegalStateException(MENSAGEM_BLOQUEIO);
        }
    }
}
