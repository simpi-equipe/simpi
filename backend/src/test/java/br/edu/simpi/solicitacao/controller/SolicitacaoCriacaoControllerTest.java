package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
class SolicitacaoCriacaoControllerTest {
    private static final String CORPO = """
            {"bemId":4,"setorDestinoId":2,"localizacaoDestinoId":3,
             "responsavelDestinoId":9,"justificativa":"Mover para atendimento"}
            """;
    private static final CriarSolicitacaoRequest ENTRADA =
            new CriarSolicitacaoRequest(4L, 2L, 3L, 9L, "Mover para atendimento");
    @Autowired private WebApplicationContext contexto;
    @Autowired private SolicitacaoService solicitacoes;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @ParameterizedTest
    @EnumSource(PerfilUsuario.class)
    void criarComPrincipalDoJwtERetornar201ComLocation(PerfilUsuario perfil) throws Exception {
        ReflectionTestUtils.setField(ator, "perfil", perfil);
        when(solicitacoes.criar(ENTRADA, ator)).thenReturn(resposta());
        mvc.perform(post("/api/solicitacoes").header("Authorization", bearer(contexto))
                        .contentType("application/json").content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/solicitacoes/8"))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.numero").value("SOL-8"))
                .andExpect(jsonPath("$.acoesDisponiveis[0]").value("CANCELAR"));
        verify(solicitacoes).criar(eq(ENTRADA), same(ator));
        verifyNoMoreInteractions(solicitacoes);
    }

    @Test
    void identidadeNoCorpoOuQueryNaoSubstituiPrincipal() throws Exception {
        when(solicitacoes.criar(ENTRADA, ator)).thenReturn(resposta());
        mvc.perform(post("/api/solicitacoes?solicitanteId=999")
                        .header("Authorization", bearer(contexto)).contentType("application/json")
                        .content(CORPO.replace("\"bemId\":4", "\"solicitanteId\":999,\"bemId\":4")))
                .andExpect(status().isCreated());
        verify(solicitacoes).criar(eq(ENTRADA), same(ator));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{\"bemId\":{}}"})
    void rejeitarCorpoAusenteOuIncompativelSemChamarService(String corpo) throws Exception {
        verificarErro(mvc.perform(post("/api/solicitacoes").header("Authorization", bearer(contexto))
                        .contentType("application/json").content(corpo)), 400, "DADOS_INVALIDOS");
        verifyNoInteractions(solicitacoes);
    }

    @Test
    void validacaoDeCamposContinuaNoService() throws Exception {
        var vazia = new CriarSolicitacaoRequest(null, null, null, null, null);
        when(solicitacoes.criar(vazia, ator)).thenThrow(new ExcecaoCriacaoSolicitacao(
                MotivoFalhaCriacaoSolicitacao.DADOS_INVALIDOS, "interno",
                Map.of("bemId", "Informe o identificador.", "justificativa", "Informe a justificativa.")));
        verificarErro(mvc.perform(post("/api/solicitacoes").header("Authorization", bearer(contexto))
                        .contentType("application/json").content("{}")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("bemId"))
                .andExpect(jsonPath("$.erros[1].campo").value("justificativa"));
        verify(solicitacoes).criar(eq(vazia), same(ator));
    }

    @ParameterizedTest
    @CsvSource({"DADOS_INVALIDOS,400,DADOS_INVALIDOS", "SOLICITANTE_INATIVO,401,NAO_AUTENTICADO",
            "SOLICITANTE_INVALIDO,401,NAO_AUTENTICADO", "PERFIL_NAO_PERMITIDO,403,ACAO_NAO_PERMITIDA",
            "REFERENCIA_NAO_ENCONTRADA,404,RECURSO_NAO_ENCONTRADO",
            "BEM_COM_SOLICITACAO_EM_ANDAMENTO,409,BEM_COM_SOLICITACAO_EM_ANDAMENTO",
            "GESTOR_DESIGNADO_INVALIDO,500,ERRO_INTERNO"})
    void traduzirFalhasDoServiceSemLocation(String motivo, int estado, String codigo) throws Exception {
        when(solicitacoes.criar(ENTRADA, ator)).thenThrow(new ExcecaoCriacaoSolicitacao(
                MotivoFalhaCriacaoSolicitacao.valueOf(motivo), "detalhe interno", Map.of()));
        verificarErro(mvc.perform(post("/api/solicitacoes").header("Authorization", bearer(contexto))
                        .contentType("application/json").content(CORPO)), estado, codigo)
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer invalido"})
    void rejeitarAusenciaOuFalhaDoToken(String autorizacao) throws Exception {
        verificarErro(mvc.perform(post("/api/solicitacoes").header("Authorization", autorizacao)
                        .contentType("application/json").content(CORPO)), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(solicitacoes);
    }

    @Test
    void ocultarFalhaInesperada() throws Exception {
        when(solicitacoes.criar(ENTRADA, ator)).thenThrow(new IllegalStateException("SQL segredo"));
        verificarErro(mvc.perform(post("/api/solicitacoes").header("Authorization", bearer(contexto))
                        .contentType("application/json").content(CORPO)), 500, "ERRO_INTERNO")
                .andExpect(jsonPath("$.mensagem").value("Não foi possível concluir a operação."));
    }

    private SolicitacaoResponse resposta() {
        return new SolicitacaoResponse(8L, "SOL-8", StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                Instant.parse("2026-11-08T00:00:00Z"), ENTRADA.justificativa(), null, null, null, null,
                List.of(), null, null, List.of(), List.of("CANCELAR"));
    }
}
