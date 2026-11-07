package br.edu.simpi.consulta.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.consulta.dto.*;
import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
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
class BemConsultaControllerTest {
    @Autowired private WebApplicationContext contexto;
    @Autowired private ConsultaPatrimonialService consultas;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @Test
    void pesquisarComFiltrosSemNormalizarNoController() throws Exception {
        var filtros = new PesquisarBensRequest("  Projetor  ", 2L, 3L, false, 1, 5);
        var bem = new BemPatrimonialResponse(4L, "PAT-4", null, "Projetor",
                new BemPatrimonialResponse.SetorResponse(2L, "Origem"),
                new BemPatrimonialResponse.LocalizacaoResponse(3L, "Sala"),
                new BemPatrimonialResponse.ResponsavelResponse(7L, "Pessoa"), true, false);
        when(consultas.pesquisarBens(filtros, ator)).thenReturn(new PaginaBensResponse(List.of(bem), 1, 5, 6, 2));
        mvc.perform(get("/api/bens").param("busca", "  Projetor  ").param("setorId", "2")
                        .param("localizacaoId", "3").param("disponivelParaSolicitacao", "false")
                        .param("pagina", "1").param("tamanho", "5").param("atorId", "999")
                        .header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.conteudo[0].codigo").value("PAT-4"))
                .andExpect(jsonPath("$.conteudo[0].plaqueta").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.conteudo[0].setor.id").value(2))
                .andExpect(jsonPath("$.conteudo[0].localizacao.id").value(3))
                .andExpect(jsonPath("$.conteudo[0].responsavel.id").value(7))
                .andExpect(jsonPath("$.conteudo[0].ativo").value(true))
                .andExpect(jsonPath("$.conteudo[0].disponivelParaSolicitacao").value(false))
                .andExpect(jsonPath("$.pagina").value(1)).andExpect(jsonPath("$.tamanho").value(5))
                .andExpect(jsonPath("$.totalElementos").value(6)).andExpect(jsonPath("$.totalPaginas").value(2));
        verify(consultas).pesquisarBens(eq(filtros), same(ator));
    }

    @Test
    void preservarOpcionaisAusentesParaPadroesDoService() throws Exception {
        var filtros = new PesquisarBensRequest(null, null, null, null, null, null);
        when(consultas.pesquisarBens(filtros, ator)).thenReturn(new PaginaBensResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/bens").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"conteudo":[],"pagina":0,"tamanho":20,"totalElementos":0,"totalPaginas":0}
                        """));
        verify(consultas).pesquisarBens(eq(filtros), same(ator));
        when(consultas.listarTransferencias(4L, null, null, ator))
                .thenReturn(new PaginaTransferenciasBemResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/bens/4/transferencias").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conteudo").isEmpty());
        verify(consultas).listarTransferencias(eq(4L), isNull(), isNull(), same(ator));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "on,true", "off,false", "yes,true", "no,false", "1,true", "0,false"})
    void usarConversaoPadraoDeBooleanos(String valor, boolean esperado) throws Exception {
        var filtros = new PesquisarBensRequest(null, null, null, esperado, null, null);
        when(consultas.pesquisarBens(filtros, ator)).thenReturn(new PaginaBensResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/bens").param("disponivelParaSolicitacao", valor).header("Authorization", bearer(contexto)))
                .andExpect(status().isOk());
        verify(consultas).pesquisarBens(eq(filtros), same(ator));
    }

    @Test
    void detalharBemDiretamenteNaRaizComNullExplicito() throws Exception {
        when(consultas.detalharBem(4L, ator)).thenReturn(new DetalheBemPatrimonialResponse(4L, "PAT-4", null,
                "Projetor", null, null, null, true, false, null));
        mvc.perform(get("/api/bens/4").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.disponivelParaSolicitacao").value(false))
                .andExpect(resposta -> {
                    var corpo = contexto.getBean(tools.jackson.databind.ObjectMapper.class)
                            .readTree(resposta.getResponse().getContentAsString());
                    assertTrue(corpo.has("solicitacaoEmAndamento"));
                    assertTrue(corpo.get("solicitacaoEmAndamento").isNull());
                });
        verify(consultas).detalharBem(eq(4L), same(ator));
    }

    @Test
    void serializarSolicitacaoEmAndamentoQuandoInformada() throws Exception {
        when(consultas.detalharBem(4L, ator)).thenReturn(new DetalheBemPatrimonialResponse(4L, "PAT-4", null,
                "Projetor", null, null, null, true, false,
                new DetalheBemPatrimonialResponse.SolicitacaoEmAndamentoResponse(8L, "SOL-8")));
        mvc.perform(get("/api/bens/4").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.solicitacaoEmAndamento.id").value(8))
                .andExpect(jsonPath("$.solicitacaoEmAndamento.numero").value("SOL-8"));
    }

    @Test
    void listarTransferenciasComDataUtcEReferencias() throws Exception {
        var referencias = new TransferenciaBemResponse.ReferenciasTransferenciaResponse(
                new BemPatrimonialResponse.SetorResponse(2L, "Setor"),
                new BemPatrimonialResponse.LocalizacaoResponse(3L, "Sala"),
                new BemPatrimonialResponse.ResponsavelResponse(7L, "Pessoa"));
        var transferencia = new TransferenciaBemResponse(8L, "SOL-8", Instant.parse("2026-10-24T19:15:00Z"),
                referencias, referencias);
        when(consultas.listarTransferencias(4L, 2, 1, ator))
                .thenReturn(new PaginaTransferenciasBemResponse(List.of(transferencia), 2, 1, 3, 3));
        mvc.perform(get("/api/bens/4/transferencias?pagina=2&tamanho=1").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conteudo[0].solicitacaoId").value(8))
                .andExpect(jsonPath("$.conteudo[0].recebidaEm").value("2026-10-24T19:15:00Z"))
                .andExpect(jsonPath("$.conteudo[0].origem.setor.id").value(2))
                .andExpect(jsonPath("$.conteudo[0].destino.responsavel.id").value(7))
                .andExpect(jsonPath("$.pagina").value(2)).andExpect(jsonPath("$.tamanho").value(1))
                .andExpect(jsonPath("$.totalElementos").value(3)).andExpect(jsonPath("$.totalPaginas").value(3));
        verify(consultas).listarTransferencias(eq(4L), eq(2), eq(1), same(ator));
    }

    @Test
    void preservarTotaisEmPaginaAlemDoFim() throws Exception {
        when(consultas.listarTransferencias(4L, 9, 20, ator))
                .thenReturn(new PaginaTransferenciasBemResponse(List.of(), 9, 20, 2, 1));
        mvc.perform(get("/api/bens/4/transferencias?pagina=9&tamanho=20").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"conteudo":[],"pagina":9,"tamanho":20,"totalElementos":2,"totalPaginas":1}
                        """));
    }

    @ParameterizedTest
    @CsvSource({"/api/bens?setorId=abc,setorId", "/api/bens?localizacaoId=abc,localizacaoId",
            "/api/bens?disponivelParaSolicitacao=talvez,disponivelParaSolicitacao",
            "/api/bens?pagina=2147483648,pagina", "/api/bens?tamanho=abc,tamanho",
            "/api/bens/abc,id", "/api/bens/abc/transferencias,id",
            "/api/bens/4/transferencias?pagina=abc,pagina", "/api/bens/4/transferencias?tamanho=abc,tamanho"})
    void rejeitarConversoesInvalidas(String rota, String campo) throws Exception {
        verificarErro(mvc.perform(get(rota).header("Authorization", bearer(contexto))), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value(campo));
        verifyNoInteractions(consultas);
    }

    @Test
    void delegarLimitesDePaginacaoAoService() throws Exception {
        var filtros = new PesquisarBensRequest(null, null, null, null, -1, 101);
        when(consultas.pesquisarBens(filtros, ator)).thenThrow(new ExcecaoConsultaPatrimonial(
                MotivoFalhaConsultaPatrimonial.DADOS_INVALIDOS, "interno", Map.of("tamanho", "Informe um tamanho entre 1 e 100.")));
        verificarErro(mvc.perform(get("/api/bens?pagina=-1&tamanho=101").header("Authorization", bearer(contexto))),
                400, "DADOS_INVALIDOS");
        verify(consultas).pesquisarBens(eq(filtros), same(ator));
    }

    @Test
    void propagarRevalidacaoDoAtorPeloService() throws Exception {
        when(consultas.detalharBem(4L, ator)).thenThrow(new ExcecaoConsultaPatrimonial(
                MotivoFalhaConsultaPatrimonial.NAO_AUTENTICADO, "interno", Map.of()));
        verificarErro(mvc.perform(get("/api/bens/4").header("Authorization", bearer(contexto))), 401, "NAO_AUTENTICADO");
        verify(consultas).detalharBem(eq(4L), same(ator));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/bens", "/api/bens/4", "/api/bens/4/transferencias"})
    void rejeitarAcessoSemToken(String rota) throws Exception {
        verificarErro(mvc.perform(get(rota)), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(consultas);
    }
}
