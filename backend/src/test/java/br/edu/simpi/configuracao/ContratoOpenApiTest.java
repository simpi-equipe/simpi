package br.edu.simpi.configuracao;

import br.edu.simpi.autenticacao.AutenticacaoController;
import br.edu.simpi.autenticacao.AutenticacaoService;
import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.solicitacao.service.ConsultaSolicitacaoService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ContratoOpenApiTest.ConfiguracaoTeste.class)
class ContratoOpenApiTest {
    private static final Map<String, List<String>> PARAMETROS = Map.of(
            "/api/setores", List.of(),
            "/api/localizacoes", List.of(),
            "/api/usuarios", List.of("setorId"),
            "/api/bens", List.of("busca", "setorId", "localizacaoId", "disponivelParaSolicitacao", "pagina", "tamanho"),
            "/api/bens/{id}", List.of("id"),
            "/api/bens/{id}/transferencias", List.of("id", "pagina", "tamanho"),
            "/api/solicitacoes", List.of("busca", "status", "visao", "pagina", "tamanho"),
            "/api/solicitacoes/{id}", List.of("id"),
            "/api/auth/me", List.of());

    @Autowired private WebApplicationContext contexto;
    @Autowired private ObjectMapper conversorJson;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        prepararAtor(contexto);
        reset(contexto.getBean(AutenticacaoService.class));
        mvc = criarMvc(contexto);
    }

    private JsonNode carregarDocumento() throws Exception {
        var resultado = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json")).andReturn();
        return conversorJson.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void documentarCriacaoComCorpoSemIdentidadeEResposta201() throws Exception {
        var documento = carregarDocumento();
        var operacao = documento.at("/paths/~1api~1solicitacoes/post");
        assertTrue(operacao.at("/security/0").has("bearerAuth"));
        assertTrue(operacao.path("parameters").isMissingNode() || operacao.path("parameters").isEmpty());
        assertEquals("#/components/schemas/CriarSolicitacaoRequest",
                operacao.at("/requestBody/content/application~1json/schema/$ref").asString());
        assertTrue(operacao.at("/requestBody/required").asBoolean());
        var propriedades = documento.at("/components/schemas/CriarSolicitacaoRequest/properties");
        assertEquals(5, propriedades.size());
        for (String campo : List.of("bemId", "setorDestinoId", "localizacaoDestinoId", "responsavelDestinoId", "justificativa")) {
            assertTrue(propriedades.has(campo), campo);
        }
        assertEquals(5, documento.at("/components/schemas/CriarSolicitacaoRequest/required").size());
        assertEquals("#/components/schemas/SolicitacaoResponse",
                operacao.at("/responses/201/content/application~1json/schema/$ref").asString());
        assertTrue(operacao.at("/responses/201/headers").has("Location"));
        for (String estado : List.of("400", "401", "403", "404", "409", "500")) {
            assertEquals("#/components/schemas/ErroResponse",
                    operacao.at("/responses/" + estado + "/content/application~1json/schema/$ref").asString());
        }
    }

    @Test
    void documentarSegurancaPorOperacaoSemExporIdentidadeComoEntrada() throws Exception {
        var documento = carregarDocumento();
        assertEquals("SIMPI - API patrimonial", documento.at("/info/title").asString());
        assertEquals("http", documento.at("/components/securitySchemes/bearerAuth/type").asString());
        assertEquals("bearer", documento.at("/components/securitySchemes/bearerAuth/scheme").asString());
        assertTrue(documento.path("security").isMissingNode() || documento.path("security").isEmpty());
        var caminhos = documento.get("paths");
        assertEquals(15, caminhos.size());
        var login = caminhos.get("/api/auth/login").get("post");
        assertTrue(login.path("security").isMissingNode() || login.path("security").isEmpty());
        assertTrue(login.path("parameters").isMissingNode() || login.path("parameters").isEmpty());
        assertEquals("#/components/schemas/AutenticacaoRequest",
                login.at("/requestBody/content/application~1json/schema/$ref").asString());
        assertEquals("#/components/schemas/AutenticacaoResponse",
                login.at("/responses/200/content/application~1json/schema/$ref").asString());
        assertEquals(2, documento.at("/components/schemas/AutenticacaoRequest/properties").size());
        assertTrue(documento.at("/components/schemas/AutenticacaoRequest/properties").has("email"));
        assertTrue(documento.at("/components/schemas/AutenticacaoRequest/properties").has("senha"));
        for (var entrada : PARAMETROS.entrySet()) {
            var operacao = caminhos.get(entrada.getKey()).get("get");
            assertEquals(1, operacao.get("security").size(), entrada.getKey());
            assertTrue(operacao.get("security").get(0).has("bearerAuth"), entrada.getKey());
            assertFalse(operacao.has("requestBody"), entrada.getKey());
            Set<String> nomes = new HashSet<>();
            for (var parametro : operacao.path("parameters")) {
                String nome = parametro.get("name").asString();
                nomes.add(nome);
                assertEquals(nome.equals("id") ? "path" : "query", parametro.get("in").asString());
                assertEquals(nome.equals("id") || (entrada.getKey().equals("/api/usuarios") && nome.equals("setorId")),
                        parametro.path("required").asBoolean(), entrada.getKey() + ": " + nome);
                if (nome.equals("id") || nome.endsWith("Id")) {
                    assertEquals("integer", parametro.at("/schema/type").asString());
                    assertEquals("int64", parametro.at("/schema/format").asString());
                }
            }
            assertEquals(Set.copyOf(entrada.getValue()), nomes, entrada.getKey());
            assertEquals(entrada.getValue().size(), operacao.path("parameters").size());
            assertTrue(operacao.get("responses").has("401"));
            assertTrue(operacao.get("responses").has("500"));
        }
        for (String nome : List.of("Usuario", "UserDetails", "PesquisarBensRequest", "PesquisarSolicitacoesRequest")) {
            assertFalse(documento.get("components").get("schemas").has(nome), nome);
        }
        verifyNoInteractions(contexto.getBean(ConsultaPatrimonialService.class),
                contexto.getBean(ConsultaSolicitacaoService.class), contexto.getBean(AutenticacaoService.class));
    }

    @Test
    void documentarAprovacaoRecusaECancelamentoComPrincipalOculto() throws Exception {
        var documento = carregarDocumento();
        for (String acao : List.of("aprovar", "recusar", "cancelar")) {
            String caminho = acao.equals("cancelar") ? "/api/solicitacoes/{id}/cancelar"
                    : "/api/solicitacoes/{solicitacaoId}/aprovacoes/{aprovacaoId}/" + acao;
            var operacao = documento.get("paths").get(caminho).get("post");
            assertTrue(operacao.at("/security/0").has("bearerAuth"));
            Set<String> nomes = new HashSet<>();
            for (var parametro : operacao.get("parameters")) {
                nomes.add(parametro.get("name").asString());
                assertEquals("path", parametro.get("in").asString());
                assertTrue(parametro.get("required").asBoolean());
                assertEquals("int64", parametro.at("/schema/format").asString());
            }
            assertEquals(acao.equals("cancelar") ? Set.of("id") : Set.of("solicitacaoId", "aprovacaoId"), nomes);
            if (acao.equals("aprovar")) {
                assertFalse(operacao.has("requestBody"));
            } else {
                String esquema = acao.equals("recusar") ? "RecusarAprovacaoRequest" : "CancelarSolicitacaoRequest";
                assertTrue(operacao.at("/requestBody/required").asBoolean());
                assertEquals("#/components/schemas/" + esquema,
                        operacao.at("/requestBody/content/application~1json/schema/$ref").asString());
                String campo = acao.equals("recusar") ? "justificativa" : "motivo";
                var dados = documento.get("components").get("schemas").get(esquema);
                assertEquals(1, dados.get("properties").size());
                assertTrue(dados.get("properties").has(campo));
                assertEquals(campo, dados.get("required").get(0).asString());
            }
            assertEquals("#/components/schemas/SolicitacaoResponse",
                    operacao.at("/responses/200/content/application~1json/schema/$ref").asString());
            for (String estado : List.of("400", "401", "403", "404", "409", "500")) {
                assertEquals("#/components/schemas/ErroResponse",
                        operacao.at("/responses/" + estado + "/content/application~1json/schema/$ref").asString());
            }
        }
    }

    @Test
    void documentarEnvioERecebimentoSemCorpoComPrincipalOculto() throws Exception {
        var documento = carregarDocumento();
        for (String acao : List.of("enviar", "receber")) {
            var operacao = documento.get("paths").get("/api/solicitacoes/{id}/" + acao).get("post");
            assertTrue(operacao.at("/security/0").has("bearerAuth"));
            assertFalse(operacao.has("requestBody"));
            assertEquals(1, operacao.get("parameters").size());
            var parametro = operacao.get("parameters").get(0);
            assertEquals("id", parametro.get("name").asString());
            assertEquals("path", parametro.get("in").asString());
            assertTrue(parametro.get("required").asBoolean());
            assertEquals("int64", parametro.at("/schema/format").asString());
            assertEquals("#/components/schemas/SolicitacaoResponse",
                    operacao.at("/responses/200/content/application~1json/schema/$ref").asString());
            for (String estado : List.of("400", "401", "403", "404", "409", "500")) {
                assertEquals("#/components/schemas/ErroResponse",
                        operacao.at("/responses/" + estado + "/content/application~1json/schema/$ref").asString());
            }
        }
    }

    @Test
    void documentarTiposObrigatoriedadePadroesELimitesSemAlterarBinding() throws Exception {
        var caminhos = carregarDocumento().get("paths");
        for (String rota : List.of("/api/bens", "/api/bens/{id}/transferencias", "/api/solicitacoes")) {
            var parametros = caminhos.get(rota).get("get").get("parameters");
            for (var parametro : parametros) {
                String nome = parametro.get("name").asString();
                if (nome.equals("pagina") || nome.equals("tamanho")) {
                    assertFalse(parametro.path("required").asBoolean());
                    var esquema = parametro.get("schema");
                    assertEquals("integer", esquema.get("type").asString());
                    assertEquals(nome.equals("pagina") ? 0 : 20, esquema.get("default").asInt());
                    assertEquals(nome.equals("pagina") ? 0 : 1, esquema.get("minimum").asInt());
                    if (nome.equals("tamanho")) assertEquals(100, esquema.get("maximum").asInt());
                }
            }
        }
        var setor = caminhos.get("/api/usuarios").get("get").get("parameters").get(0);
        assertTrue(setor.get("required").asBoolean());
        assertEquals("integer", setor.get("schema").get("type").asString());
        for (var parametro : caminhos.get("/api/bens").get("get").get("parameters")) {
            if (parametro.get("name").asString().equals("disponivelParaSolicitacao")) {
                assertEquals("boolean", parametro.get("schema").get("type").asString());
                assertFalse(parametro.get("schema").has("default"));
            }
        }
        for (var parametro : caminhos.get("/api/solicitacoes").get("get").get("parameters")) {
            String nome = parametro.get("name").asString();
            if (nome.equals("visao")) {
                assertEquals("DA_MINHA_AREA", parametro.at("/schema/default").asString());
                assertEquals(5, parametro.at("/schema/enum").size());
            }
            if (nome.equals("status")) assertEquals(7, parametro.at("/schema/enum").size());
        }
    }

    @Test
    void separarEsquemasDeSucessoEErro() throws Exception {
        var documento = carregarDocumento();
        var caminhos = documento.get("paths");
        Map<String, String> sucessos = Map.of(
                "/api/bens", "PaginaBensResponse", "/api/bens/{id}", "DetalheBemPatrimonialResponse",
                "/api/bens/{id}/transferencias", "PaginaTransferenciasBemResponse",
                "/api/solicitacoes", "PaginaSolicitacoesResponse", "/api/solicitacoes/{id}", "SolicitacaoResponse",
                "/api/auth/me", "UsuarioResponse");
        for (var entrada : sucessos.entrySet()) {
            assertEquals("#/components/schemas/" + entrada.getValue(), caminhos.get(entrada.getKey())
                    .at("/get/responses/200/content/application~1json/schema/$ref").asString());
        }
        Map<String, String> seletores = Map.of("/api/setores", "SetorSelecaoResponse",
                "/api/localizacoes", "LocalizacaoSelecaoResponse", "/api/usuarios", "UsuarioSelecaoResponse");
        for (var entrada : seletores.entrySet()) {
            var esquema = caminhos.get(entrada.getKey()).at("/get/responses/200/content/application~1json/schema");
            assertEquals("array", esquema.get("type").asString());
            assertEquals("#/components/schemas/" + entrada.getValue(), esquema.at("/items/$ref").asString());
        }
        for (var caminho : caminhos) {
            for (var operacao : caminho) {
                var respostas = operacao.get("responses");
                for (String codigo : List.of("400", "401", "403", "404", "500")) {
                    if (respostas.has(codigo)) {
                        var esquema = respostas.get(codigo).at("/content/application~1json/schema");
                        assertEquals("#/components/schemas/ErroResponse", esquema.path("$ref").asString(), codigo);
                        assertFalse(esquema.has("oneOf"));
                        assertFalse(esquema.has("allOf"));
                    }
                }
            }
        }
        assertEquals(3, documento.at("/components/schemas/ErroResponse/properties").size());
        for (String campo : List.of("codigo", "mensagem", "erros")) {
            assertTrue(documento.at("/components/schemas/ErroResponse/properties").has(campo));
        }
    }

    @Test
    void disponibilizarInterfaceEConfiguracaoSemAutenticacao() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/me", "/api/setores", "/api/localizacoes", "/api/usuarios?setorId=1",
            "/api/bens", "/api/bens/1", "/api/bens/1/transferencias", "/api/solicitacoes", "/api/solicitacoes/1"})
    void manterOperacoesProtegidas(String rota) throws Exception {
        verificarErro(mvc.perform(get(rota)), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(contexto.getBean(ConsultaPatrimonialService.class),
                contexto.getBean(ConsultaSolicitacaoService.class), contexto.getBean(AutenticacaoService.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({ConfiguracaoMvcConsultasTeste.class, ConfiguracaoOpenApi.class, AutenticacaoController.class,
            SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocSpecPropertiesConfiguration.class,
            SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
            SwaggerUiOAuthProperties.class})
    static class ConfiguracaoTeste {
        @Bean AutenticacaoService autenticacao() { return mock(AutenticacaoService.class); }
    }
}
