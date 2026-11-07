package br.edu.simpi.configuracao;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfiguracaoOpenApi {
    @Bean
    public OpenAPI documentacaoApi() {
        return new OpenAPI()
                .info(new Info().title("SIMPI - API patrimonial").version("1.1")
                        .description("Execute o login e copie accessToken para Authorize, sem o prefixo Bearer. "
                                + "Consultas exigem usuário ativo. Arrays vazios usam []; campos opcionais previstos "
                                + "usam null; datas e horas são ISO 8601 em UTC, com Z."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token obtido em POST /api/auth/login.")));
    }
}
