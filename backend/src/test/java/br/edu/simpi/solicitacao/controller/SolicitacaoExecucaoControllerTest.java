package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoExecucaoSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class SolicitacaoExecucaoControllerTest {
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
    @CsvSource({"enviar,COMUM", "enviar,GESTOR", "enviar,ADMINISTRADOR",
            "receber,COMUM", "receber,GESTOR", "receber,ADMINISTRADOR"})
    void executarSemCorpoComPrincipalDoJwtERespostaCompleta(String acao, PerfilUsuario perfil) throws Exception {
        ReflectionTestUtils.setField(ator, "perfil", perfil);
        boolean envio = acao.equals("enviar");
        var resposta = resposta(envio);
        if (envio) when(solicitacoes.enviar(8L, ator)).thenReturn(resposta);
        else when(solicitacoes.receber(8L, ator)).thenReturn(resposta);
        String bloco = envio ? "envio" : "recebimento";
        mvc.perform(post(rota("8", acao) + "?atorId=999").header("Authorization", bearer(contexto))
                        .header("X-Usuario-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value(envio ? "EM_TRANSITO" : "CONCLUIDA"))
                .andExpect(jsonPath("$." + bloco + ".realizadoPor.id").value(7))
                .andExpect(jsonPath("$." + bloco + ".realizadoEm").value("2026-11-09T12:00:00Z"))
                .andExpect(jsonPath("$.destino.responsavel.id").value(9))
                .andExpect(jsonPath("$.eventos[0].tipo").value(envio ? "ENVIO" : "RECEBIMENTO"))
                .andExpect(jsonPath("$.acoesDisponiveis").isEmpty());
        if (envio) verify(solicitacoes).enviar(eq(8L), same(ator));
        else verify(solicitacoes).receber(eq(8L), same(ator));
        verifyNoMoreInteractions(solicitacoes);
    }

    @ParameterizedTest
    @CsvSource({"enviar,DADOS_INVALIDOS,400", "receber,DADOS_INVALIDOS,400",
            "enviar,NAO_AUTENTICADO,401", "receber,NAO_AUTENTICADO,401",
            "enviar,ACAO_NAO_PERMITIDA,403", "receber,ACAO_NAO_PERMITIDA,403",
            "enviar,SOLICITACAO_NAO_ENCONTRADA,404", "receber,SOLICITACAO_NAO_ENCONTRADA,404",
            "enviar,ESTADO_INCOMPATIVEL,409", "receber,ESTADO_INCOMPATIVEL,409"})
    void traduzirFalhasDeExecucao(String acao, String codigo, int estado) throws Exception {
        var falha = new ExcecaoExecucaoSolicitacao(MotivoFalhaExecucaoSolicitacao.valueOf(codigo),
                "detalhe interno", Map.of());
        if (acao.equals("enviar")) when(solicitacoes.enviar(8L, ator)).thenThrow(falha);
        else when(solicitacoes.receber(8L, ator)).thenThrow(falha);
        verificarErro(mvc.perform(post(rota("8", acao)).header("Authorization", bearer(contexto))), estado, codigo)
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @CsvSource({"enviar,abc", "receber,abc", "enviar,9223372036854775808", "receber,9223372036854775808"})
    void rejeitarIdIncompativelSemChamarService(String acao, String id) throws Exception {
        verificarErro(mvc.perform(post(rota(id, acao)).header("Authorization", bearer(contexto))), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("id"));
        verifyNoInteractions(solicitacoes);
    }

    @ParameterizedTest
    @CsvSource({"enviar,0", "receber,0", "enviar,-1", "receber,-1"})
    void preservarValidacaoDeIdNoService(String acao, Long id) throws Exception {
        var falha = new ExcecaoExecucaoSolicitacao(MotivoFalhaExecucaoSolicitacao.DADOS_INVALIDOS,
                "interno", Map.of("solicitacaoId", "Informe um identificador positivo."));
        if (acao.equals("enviar")) when(solicitacoes.enviar(id, ator)).thenThrow(falha);
        else when(solicitacoes.receber(id, ator)).thenThrow(falha);
        verificarErro(mvc.perform(post(rota(id.toString(), acao)).header("Authorization", bearer(contexto))),
                400, "DADOS_INVALIDOS").andExpect(jsonPath("$.erros[0].campo").value("solicitacaoId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"enviar", "receber"})
    void rejeitarAusenciaOuFalhaDoTokenSemChamarService(String acao) throws Exception {
        verificarErro(mvc.perform(post(rota("8", acao))), 401, "NAO_AUTENTICADO");
        verificarErro(mvc.perform(post(rota("8", acao)).header("Authorization", "Bearer invalido")),
                401, "NAO_AUTENTICADO");
        verifyNoInteractions(solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"enviar", "receber"})
    void ocultarFalhasInesperadas(String acao) throws Exception {
        var falha = new IllegalStateException("SQL credencial interna");
        if (acao.equals("enviar")) when(solicitacoes.enviar(8L, ator)).thenThrow(falha);
        else when(solicitacoes.receber(8L, ator)).thenThrow(falha);
        verificarErro(mvc.perform(post(rota("8", acao)).header("Authorization", bearer(contexto))), 500, "ERRO_INTERNO")
                .andExpect(jsonPath("$.mensagem").value("Não foi possível concluir a operação."));
    }

    private String rota(String id, String acao) {
        return "/api/solicitacoes/" + id + "/" + acao;
    }

    private SolicitacaoResponse resposta(boolean envio) {
        var instante = Instant.parse("2026-11-09T12:00:00Z");
        var pessoa = new SolicitacaoResponse.ReferenciaResponse(7L, "Pessoa de teste");
        var operacao = new SolicitacaoResponse.OperacaoResponse(instante, pessoa);
        var destino = new SolicitacaoResponse.ReferenciasResponse(
                new SolicitacaoResponse.ReferenciaResponse(2L, "Destino"),
                new SolicitacaoResponse.ReferenciaResponse(3L, "Sala"),
                new SolicitacaoResponse.ReferenciaResponse(9L, "Responsável de destino"));
        var estado = envio ? StatusSolicitacao.EM_TRANSITO : StatusSolicitacao.CONCLUIDA;
        var evento = new SolicitacaoResponse.EventoResponse(10L,
                envio ? TipoEventoSolicitacao.ENVIO : TipoEventoSolicitacao.RECEBIMENTO, pessoa,
                envio ? StatusSolicitacao.APROVADA : StatusSolicitacao.EM_TRANSITO, estado, "Operação registrada.", instante);
        return new SolicitacaoResponse(8L, "SOL-8", estado, instante, "Justificativa inicial",
                null, null, null, destino, List.of(), operacao, envio ? null : operacao,
                List.of(evento), List.of());
    }
}
