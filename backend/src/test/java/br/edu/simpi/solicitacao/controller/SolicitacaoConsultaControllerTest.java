package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.solicitacao.dto.*;
import br.edu.simpi.solicitacao.enums.*;
import br.edu.simpi.solicitacao.service.ConsultaSolicitacaoService;
import br.edu.simpi.solicitacao.service.ExcecaoConsultaSolicitacao;
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
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
class SolicitacaoConsultaControllerTest {
    @Autowired private WebApplicationContext contexto;
    @Autowired private ConsultaSolicitacaoService consultas;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @Test
    void pesquisarComEnumsPaginacaoEPrincipalCorretos() throws Exception {
        var filtros = new PesquisarSolicitacoesRequest("  SOL-8  ", StatusSolicitacao.APROVADA,
                VisaoSolicitacao.CRIADAS_POR_MIM, 1, 5);
        var setor = new SolicitacaoResponse.ReferenciaResponse(1L, "Setor");
        var resumo = new ResumoSolicitacaoResponse(8L, "SOL-8", Instant.parse("2026-10-24T18:30:00Z"),
                StatusSolicitacao.APROVADA, new ResumoSolicitacaoResponse.BemResumoResponse(4L, "PAT-4", "Projetor"),
                new SolicitacaoResponse.SolicitanteResponse(7L, "Pessoa", setor), setor, setor);
        when(consultas.pesquisar(filtros, ator)).thenReturn(new PaginaSolicitacoesResponse(List.of(resumo), 1, 5, 6, 2));
        mvc.perform(get("/api/solicitacoes").param("busca", "  SOL-8  ").param("status", "APROVADA")
                        .param("visao", "CRIADAS_POR_MIM").param("pagina", "1").param("tamanho", "5")
                        .param("atorId", "999").header("X-Usuario-Id", "999").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.conteudo[0].id").value(8))
                .andExpect(jsonPath("$.conteudo[0].status").value("APROVADA"))
                .andExpect(jsonPath("$.conteudo[0].criadaEm").value("2026-10-24T18:30:00Z"))
                .andExpect(jsonPath("$.conteudo[0].bem.codigo").value("PAT-4"))
                .andExpect(jsonPath("$.conteudo[0].solicitante.setor.id").value(1))
                .andExpect(jsonPath("$.conteudo[0].setorOrigem.id").value(1))
                .andExpect(jsonPath("$.conteudo[0].setorDestino.id").value(1))
                .andExpect(jsonPath("$.pagina").value(1)).andExpect(jsonPath("$.tamanho").value(5))
                .andExpect(jsonPath("$.totalElementos").value(6)).andExpect(jsonPath("$.totalPaginas").value(2));
        verify(consultas).pesquisar(eq(filtros), same(ator));
    }

    @Test
    void preservarOpcionaisAusentesParaPadroesDoService() throws Exception {
        var filtros = new PesquisarSolicitacoesRequest(null, null, null, null, null);
        when(consultas.pesquisar(filtros, ator)).thenReturn(new PaginaSolicitacoesResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/solicitacoes").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"conteudo":[],"pagina":0,"tamanho":20,"totalElementos":0,"totalPaginas":0}
                        """));
        verify(consultas).pesquisar(eq(filtros), same(ator));
    }

    @Test
    void preservarPaginaVaziaComTotaisInformadosPeloService() throws Exception {
        var filtros = new PesquisarSolicitacoesRequest(null, null, null, 9, 20);
        when(consultas.pesquisar(filtros, ator)).thenReturn(new PaginaSolicitacoesResponse(List.of(), 9, 20, 2, 1));
        mvc.perform(get("/api/solicitacoes?pagina=9&tamanho=20").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"conteudo":[],"pagina":9,"tamanho":20,"totalElementos":2,"totalPaginas":1}
                        """));
    }

    @Test
    void detalharNaRaizComEnumsDatasUtcENullsExplicitos() throws Exception {
        var instante = Instant.parse("2026-10-24T18:30:00Z");
        var setor = new SolicitacaoResponse.ReferenciaResponse(1L, "Setor");
        var pessoa = new SolicitacaoResponse.ReferenciaResponse(7L, "Pessoa");
        var referencias = new SolicitacaoResponse.ReferenciasResponse(setor,
                new SolicitacaoResponse.ReferenciaResponse(3L, "Sala"), pessoa);
        var aprovacao = new SolicitacaoResponse.AprovacaoResponse(9L, EtapaAprovacao.GESTOR_AREA, pessoa,
                DecisaoAprovacao.PENDENTE, null, null);
        var evento = new SolicitacaoResponse.EventoResponse(10L, TipoEventoSolicitacao.CRIACAO, pessoa,
                null, StatusSolicitacao.AGUARDANDO_GESTOR_AREA, null, instante);
        var resposta = new SolicitacaoResponse(8L, "SOL-8", StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                instante, "Transferência", new SolicitacaoResponse.BemResponse(4L, "PAT-4", null, "Projetor"),
                new SolicitacaoResponse.SolicitanteResponse(7L, "Pessoa", setor), referencias, referencias,
                List.of(aprovacao), null, null, List.of(evento), List.of());
        when(consultas.detalhar(8L, ator)).thenReturn(resposta);
        mvc.perform(get("/api/solicitacoes/8").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_GESTOR_AREA"))
                .andExpect(jsonPath("$.criadaEm").value("2026-10-24T18:30:00Z"))
                .andExpect(jsonPath("$.origem.setor.id").value(1))
                .andExpect(jsonPath("$.destino.responsavel.id").value(7))
                .andExpect(jsonPath("$.aprovacoes[0].etapa").value("GESTOR_AREA"))
                .andExpect(jsonPath("$.aprovacoes[0].decisao").value("PENDENTE"))
                .andExpect(jsonPath("$.eventos[0].tipo").value("CRIACAO"))
                .andExpect(jsonPath("$.eventos[0].ocorridoEm").value("2026-10-24T18:30:00Z"))
                .andExpect(jsonPath("$.acoesDisponiveis").isEmpty())
                .andExpect(respostaMvc -> {
                    var corpo = contexto.getBean(tools.jackson.databind.ObjectMapper.class)
                            .readTree(respostaMvc.getResponse().getContentAsString());
                    for (String campo : List.of("envio", "recebimento")) {
                        assertTrue(corpo.has(campo));
                        assertTrue(corpo.get(campo).isNull());
                    }
                    assertTrue(corpo.get("aprovacoes").get(0).get("decididaEm").isNull());
                    assertTrue(corpo.get("eventos").get(0).get("statusAnterior").isNull());
                });
        verify(consultas).detalhar(eq(8L), same(ator));
    }

    @ParameterizedTest
    @CsvSource({"/api/solicitacoes?status=DESCONHECIDO,status", "/api/solicitacoes?visao=DESCONHECIDA,visao",
            "/api/solicitacoes?pagina=abc,pagina", "/api/solicitacoes?tamanho=2147483648,tamanho",
            "/api/solicitacoes/abc,id"})
    void rejeitarConversoesInvalidas(String rota, String campo) throws Exception {
        verificarErro(mvc.perform(get(rota).header("Authorization", bearer(contexto))), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value(campo));
        verifyNoInteractions(consultas);
    }

    @Test
    void propagarValidacaoDePaginacaoSemAnteciparRegras() throws Exception {
        var filtros = new PesquisarSolicitacoesRequest(null, null, null, -1, 101);
        when(consultas.pesquisar(filtros, ator)).thenThrow(new ExcecaoConsultaSolicitacao(
                MotivoFalhaConsultaSolicitacao.DADOS_INVALIDOS, "interno", Map.of("pagina", "Informe uma página a partir de zero.")));
        verificarErro(mvc.perform(get("/api/solicitacoes?pagina=-1&tamanho=101").header("Authorization", bearer(contexto))),
                400, "DADOS_INVALIDOS").andExpect(jsonPath("$.erros[0].campo").value("pagina"));
        verify(consultas).pesquisar(eq(filtros), same(ator));
    }

    @ParameterizedTest
    @CsvSource({"NAO_AUTENTICADO,401", "ACAO_NAO_PERMITIDA,403", "SOLICITACAO_NAO_ENCONTRADA,404"})
    void propagarExcecoesDoService(String codigo, int estado) throws Exception {
        when(consultas.detalhar(8L, ator)).thenThrow(new ExcecaoConsultaSolicitacao(
                MotivoFalhaConsultaSolicitacao.valueOf(codigo), "detalhe interno", Map.of()));
        verificarErro(mvc.perform(get("/api/solicitacoes/8").header("Authorization", bearer(contexto))), estado, codigo)
                .andExpect(jsonPath("$.erros").isEmpty());
        verify(consultas).detalhar(eq(8L), same(ator));
    }

    @Test
    void propagarNegativaDeVisaoPeloService() throws Exception {
        var filtros = new PesquisarSolicitacoesRequest(null, null, VisaoSolicitacao.TODAS, null, null);
        when(consultas.pesquisar(filtros, ator)).thenThrow(new ExcecaoConsultaSolicitacao(
                MotivoFalhaConsultaSolicitacao.ACAO_NAO_PERMITIDA, "interno", Map.of()));
        verificarErro(mvc.perform(get("/api/solicitacoes?visao=TODAS").header("Authorization", bearer(contexto))),
                403, "ACAO_NAO_PERMITIDA");
        verify(consultas).pesquisar(eq(filtros), same(ator));
    }

    @Test
    void ocultarDetalhesDeFalhaInesperada() throws Exception {
        when(consultas.detalhar(8L, ator)).thenThrow(new IllegalStateException("SQL senha=segredo"));
        verificarErro(mvc.perform(get("/api/solicitacoes/8").header("Authorization", bearer(contexto))), 500, "ERRO_INTERNO")
                .andExpect(jsonPath("$.mensagem").value("Não foi possível concluir a operação."))
                .andExpect(jsonPath("$.erros").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/solicitacoes", "/api/solicitacoes/8"})
    void rejeitarAcessoSemToken(String rota) throws Exception {
        verificarErro(mvc.perform(get(rota)), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(consultas);
    }
}
