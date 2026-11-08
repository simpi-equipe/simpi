package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoDecisaoAprovacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
class SolicitacaoAcaoControllerTest {
    @Autowired private WebApplicationContext contexto;
    @Autowired private SolicitacaoService solicitacoes;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @Test
    void aprovarSemCorpoUsaIdsDaRotaEPrincipalDoJwt() throws Exception {
        when(solicitacoes.aprovar(8L, 21L, ator)).thenReturn(resposta(StatusSolicitacao.APROVADA));
        mvc.perform(post(rota("aprovar") + "?atorId=999").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(header().doesNotExist("Location"));
        verify(solicitacoes).aprovar(eq(8L), eq(21L), same(ator));
        verifyNoMoreInteractions(solicitacoes);
    }

    @Test
    void recusarEncaminhaJustificativaSemSubstituirPrincipalPeloCorpo() throws Exception {
        when(solicitacoes.recusar(8L, 21L, "  Indisponível  ", ator)).thenReturn(resposta(StatusSolicitacao.RECUSADA));
        mvc.perform(post(rota("recusar")).header("Authorization", bearer(contexto)).contentType("application/json")
                        .content("{\"justificativa\":\"  Indisponível  \",\"atorId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECUSADA"));
        verify(solicitacoes).recusar(eq(8L), eq(21L), eq("  Indisponível  "), same(ator));
        verifyNoMoreInteractions(solicitacoes);
    }

    @Test
    void cancelarEncaminhaMotivoEPrincipalDoJwt() throws Exception {
        when(solicitacoes.cancelar(8L, "  Resolvido  ", ator)).thenReturn(resposta(StatusSolicitacao.CANCELADA));
        mvc.perform(post(rota("cancelar") + "?solicitanteId=999").header("Authorization", bearer(contexto))
                        .contentType("application/json").content("{\"motivo\":\"  Resolvido  \",\"solicitanteId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));
        verify(solicitacoes).cancelar(eq(8L), eq("  Resolvido  "), same(ator));
        verifyNoMoreInteractions(solicitacoes);
    }

    @ParameterizedTest
    @CsvSource({"aprovar,NAO_AUTENTICADO,401", "aprovar,ACAO_NAO_PERMITIDA,403",
            "aprovar,SOLICITACAO_NAO_ENCONTRADA,404", "aprovar,APROVACAO_NAO_ENCONTRADA,404",
            "aprovar,ETAPA_JA_DECIDIDA,409", "aprovar,ESTADO_INCOMPATIVEL,409",
            "recusar,NAO_AUTENTICADO,401", "recusar,ACAO_NAO_PERMITIDA,403",
            "recusar,SOLICITACAO_NAO_ENCONTRADA,404", "recusar,APROVACAO_NAO_ENCONTRADA,404",
            "recusar,ETAPA_JA_DECIDIDA,409", "recusar,ESTADO_INCOMPATIVEL,409"})
    void traduzirFalhasDeDecisao(String acao, String codigo, int estado) throws Exception {
        var falha = new ExcecaoDecisaoAprovacao(MotivoFalhaDecisaoAprovacao.valueOf(codigo), "interno", Map.of());
        if (acao.equals("aprovar")) when(solicitacoes.aprovar(8L, 21L, ator)).thenThrow(falha);
        else when(solicitacoes.recusar(8L, 21L, "Motivo sintético", ator)).thenThrow(falha);
        verificarErro(mvc.perform(requisicao(acao)), estado, codigo)
                .andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @CsvSource({"NAO_AUTENTICADO,401", "ACAO_NAO_PERMITIDA,403", "SOLICITACAO_NAO_ENCONTRADA,404", "ESTADO_INCOMPATIVEL,409"})
    void traduzirFalhasDeCancelamento(String codigo, int estado) throws Exception {
        when(solicitacoes.cancelar(8L, "Motivo sintético", ator)).thenThrow(new ExcecaoCancelamentoSolicitacao(
                MotivoFalhaCancelamentoSolicitacao.valueOf(codigo), "interno", Map.of()));
        verificarErro(mvc.perform(requisicao("cancelar")), estado, codigo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"recusar", "cancelar"})
    void camposAusentesSaoValidadosPeloService(String acao) throws Exception {
        String campo = acao.equals("recusar") ? "justificativa" : "motivo";
        if (acao.equals("recusar")) {
            when(solicitacoes.recusar(8L, 21L, null, ator)).thenThrow(new ExcecaoDecisaoAprovacao(
                    MotivoFalhaDecisaoAprovacao.DADOS_INVALIDOS, "interno", Map.of(campo, "Informe a justificativa.")));
        } else {
            when(solicitacoes.cancelar(8L, null, ator)).thenThrow(new ExcecaoCancelamentoSolicitacao(
                    MotivoFalhaCancelamentoSolicitacao.DADOS_INVALIDOS, "interno", Map.of(campo, "Informe o motivo.")));
        }
        verificarErro(mvc.perform(requisicao(acao).content("{}")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value(campo));
    }

    @ParameterizedTest
    @CsvSource({"recusar,ausente", "recusar,null", "recusar,invalido", "recusar,objeto",
            "cancelar,ausente", "cancelar,null", "cancelar,invalido", "cancelar,objeto"})
    void rejeitarCorpoAusenteOuIncompativelSemChamarService(String acao, String tipo) throws Exception {
        String campo = acao.equals("recusar") ? "justificativa" : "motivo";
        String corpo = switch (tipo) {
            case "ausente" -> "";
            case "null" -> "null";
            case "objeto" -> "{\"" + campo + "\":{}}";
            default -> "{";
        };
        verificarErro(mvc.perform(requisicao(acao).content(corpo)), 400, "DADOS_INVALIDOS");
        verifyNoInteractions(solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/solicitacoes/abc/aprovacoes/21/aprovar",
            "/api/solicitacoes/8/aprovacoes/abc/aprovar", "/api/solicitacoes/8/aprovacoes/abc/recusar",
            "/api/solicitacoes/abc/cancelar"})
    void rejeitarIdsMalformados(String rota) throws Exception {
        verificarErro(mvc.perform(post(rota).header("Authorization", bearer(contexto))
                .contentType("application/json").content("{}")), 400, "DADOS_INVALIDOS");
        verifyNoInteractions(solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprovar", "recusar", "cancelar"})
    void protegerTodasAsAcoesSemTokenEComTokenInvalido(String acao) throws Exception {
        verificarErro(mvc.perform(requisicaoSemToken(acao)), 401, "NAO_AUTENTICADO");
        verificarErro(mvc.perform(requisicaoSemToken(acao).header("Authorization", "Bearer invalido")), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(solicitacoes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprovar", "recusar", "cancelar"})
    void ocultarFalhasInesperadas(String acao) throws Exception {
        var falha = new IllegalStateException("SQL credencial interna");
        switch (acao) {
            case "aprovar" -> when(solicitacoes.aprovar(8L, 21L, ator)).thenThrow(falha);
            case "recusar" -> when(solicitacoes.recusar(8L, 21L, "Motivo sintético", ator)).thenThrow(falha);
            default -> when(solicitacoes.cancelar(8L, "Motivo sintético", ator)).thenThrow(falha);
        }
        verificarErro(mvc.perform(requisicao(acao)), 500, "ERRO_INTERNO")
                .andExpect(jsonPath("$.mensagem").value("Não foi possível concluir a operação."));
    }

    private MockHttpServletRequestBuilder requisicao(String acao) {
        return requisicaoSemToken(acao).header("Authorization", bearer(contexto));
    }

    private MockHttpServletRequestBuilder requisicaoSemToken(String acao) {
        var requisicao = post(rota(acao));
        if (!acao.equals("aprovar")) {
            String campo = acao.equals("recusar") ? "justificativa" : "motivo";
            requisicao.contentType("application/json").content("{\"" + campo + "\":\"Motivo sintético\"}");
        }
        return requisicao;
    }

    private String rota(String acao) {
        return acao.equals("cancelar") ? "/api/solicitacoes/8/cancelar"
                : "/api/solicitacoes/8/aprovacoes/21/" + acao;
    }

    private SolicitacaoResponse resposta(StatusSolicitacao estado) {
        return new SolicitacaoResponse(8L, "SOL-8", estado, Instant.parse("2026-11-08T00:00:00Z"),
                "Justificativa inicial", null, null, null, null, List.of(), null, null, List.of(), List.of());
    }
}
