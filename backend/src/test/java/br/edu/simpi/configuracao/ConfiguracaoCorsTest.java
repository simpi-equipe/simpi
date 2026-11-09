package br.edu.simpi.configuracao;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
@TestPropertySource(locations = "classpath:application.properties")
class ConfiguracaoCorsTest {
    @Autowired private WebApplicationContext contexto;
    @Autowired private ConsultaPatrimonialService consultas;
    @Autowired private SolicitacaoService solicitacoes;
    @Autowired private UsuarioRepository usuarios;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @ParameterizedTest
    @CsvSource({"http://localhost:5173,/api/auth/login,POST",
            "http://127.0.0.1:5173,/api/solicitacoes,POST",
            "http://localhost:5173,/api/bens,GET",
            "http://127.0.0.1:5173,/api/solicitacoes/1/receber,POST"})
    void permitirPreflightLocalSemToken(String origem, String rota, String metodo) throws Exception {
        mvc.perform(options(rota).header("Origin", origem)
                        .header("Access-Control-Request-Method", metodo)
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origem))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(metodo)))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(usuarios, consultas, solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5174", "https://nao-permitido.example", "null"})
    void bloquearPreflightDeOrigemNaoPermitida(String origem) throws Exception {
        mvc.perform(options("/api/solicitacoes").header("Origin", origem)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(usuarios, consultas, solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    void bloquearMetodoNaoPermitidoNoPreflight(String metodo) throws Exception {
        mvc.perform(options("/api/solicitacoes").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", metodo))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(usuarios, consultas, solicitacoes);
    }

    @Test
    void bloquearCabecalhoNaoPermitidoNoPreflight() throws Exception {
        mvc.perform(options("/api/solicitacoes").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-usuario-id"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(usuarios, consultas, solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer invalido"})
    void preservar401ComCabecalhosCorsNaOrigemPermitida(String autorizacao) throws Exception {
        verificarErro(mvc.perform(get("/api/setores").header("Origin", "http://localhost:5173")
                        .header("Authorization", autorizacao)), 401, "NAO_AUTENTICADO")
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        verifyNoInteractions(consultas);
    }

    @Test
    void permitirConsultaAutenticadaDaOrigemLocal() throws Exception {
        when(consultas.listarSetores(ator)).thenReturn(List.of());
        mvc.perform(get("/api/setores").header("Origin", "http://127.0.0.1:5173")
                        .header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("[]"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
        verify(consultas).listarSetores(same(ator));
    }

    @Test
    void bloquearChamadaRealDeOrigemNaoPermitidaAntesDaAutenticacao() throws Exception {
        mvc.perform(get("/api/setores").header("Origin", "https://nao-permitido.example")
                        .header("Authorization", bearer(contexto)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(usuarios, consultas);
    }

    @Test
    void exporLocationDaCriacaoParaOFrontend() throws Exception {
        var entrada = new CriarSolicitacaoRequest(1L, 2L, 3L, 4L, "Transferência");
        var resposta = new SolicitacaoResponse(8L, "SOL-8", StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                Instant.parse("2026-11-09T12:00:00Z"), "Transferência", null, null, null, null,
                List.of(), null, null, List.of(), List.of());
        when(solicitacoes.criar(entrada, ator)).thenReturn(resposta);
        mvc.perform(post("/api/solicitacoes").header("Origin", "http://localhost:5173")
                        .header("Authorization", bearer(contexto)).contentType("application/json")
                        .content("""
                                {"bemId":1,"setorDestinoId":2,"localizacaoDestinoId":3,
                                 "responsavelDestinoId":4,"justificativa":"Transferência"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/solicitacoes/8"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Location"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verify(solicitacoes).criar(eq(entrada), same(ator));
    }
}
