package br.edu.simpi.persistencia;

import br.edu.simpi.autenticacao.JwtService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class AcoesSolicitacaoHttpIT {
    @Autowired private WebApplicationContext contexto;
    @Autowired private JdbcTemplate banco;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FilterChainProxy.class)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMUM", "ADMINISTRADOR"})
    void duasEtapasExigemDecisoesExplicitasMesmoComMesmoGestor(String perfil) throws Exception {
        var cenario = criarPedido(perfil);
        Long gestor = cenario.dados().origem().gestorId();
        var primeira = ler(decidir(cenario, "aprovar", gestor, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_GESTOR_RESPONSAVEL"))
                .andExpect(jsonPath("$.aprovacoes[0].decisao").value("APROVADA"))
                .andExpect(jsonPath("$.aprovacoes[1].decisao").value("PENDENTE")));
        long segunda = primeira.get("aprovacoes").get(1).get("id").asLong();
        decidir(cenario, "aprovar", gestor, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ETAPA_JA_DECIDIDA"));
        assertEquals("PENDENTE", banco.queryForObject("SELECT decisao FROM aprovacao WHERE id=?", String.class, segunda));
        mvc.perform(post("/api/solicitacoes/" + cenario.id() + "/aprovacoes/" + segunda + "/aprovar")
                        .header("Authorization", token(gestor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.aprovacoes[1].decisao").value("APROVADA"));
        assertEquals(3L, banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id=?", Long.class, cenario.id()));
        verificarBemInalterado(cenario);
    }

    @Test
    void movimentacaoCompletaPelaApiAtualizaBemSomenteNoRecebimento() throws Exception {
        var cenario = criarPedido("COMUM");
        var dados = cenario.dados();
        Long origem = dados.origem().gestorId();
        Long destino = dados.destino().gestorId();
        var primeira = ler(decidir(cenario, "aprovar", origem, "").andExpect(status().isOk()));
        long segunda = primeira.get("aprovacoes").get(1).get("id").asLong();
        mvc.perform(post("/api/solicitacoes/" + cenario.id() + "/aprovacoes/" + segunda + "/aprovar")
                        .header("Authorization", token(origem)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        executar(cenario, "enviar", destino).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACAO_NAO_PERMITIDA"));
        executar(cenario, "receber", destino).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INCOMPATIVEL"));
        executar(cenario, "enviar", origem).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_TRANSITO"))
                .andExpect(jsonPath("$.envio.realizadoPor.id").value(origem))
                .andExpect(jsonPath("$.eventos[3].tipo").value("ENVIO"));
        verificarBemInalterado(cenario);
        executar(cenario, "enviar", origem).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INCOMPATIVEL"));
        executar(cenario, "receber", origem).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACAO_NAO_PERMITIDA"));
        executar(cenario, "receber", destino).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.recebimento.realizadoPor.id").value(destino))
                .andExpect(jsonPath("$.eventos[4].tipo").value("RECEBIMENTO"))
                .andExpect(jsonPath("$.acoesDisponiveis").isEmpty());
        executar(cenario, "receber", destino).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INCOMPATIVEL"));
        var bem = banco.queryForMap("SELECT setor_id,localizacao_id,responsavel_id FROM bem_patrimonial WHERE id=?",
                dados.origem().bemId());
        assertEquals(dados.destino().setorId(), bem.get("setor_id"));
        assertEquals(dados.destino().localizacaoId(), bem.get("localizacao_id"));
        assertEquals(destino, bem.get("responsavel_id"));
        assertEquals(5L, banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id=?",
                Long.class, cenario.id()));
    }

    private ResultActions executar(Cenario cenario, String acao, Long ator) throws Exception {
        return mvc.perform(post("/api/solicitacoes/" + cenario.id() + "/" + acao)
                .header("Authorization", token(ator)));
    }

    @ParameterizedTest
    @CsvSource({"enviar,0", "receber,0", "enviar,-1", "receber,-1"})
    void rejeitarIdNaoPositivoComCampoInformadoPeloServiceReal(String acao, Long id) throws Exception {
        var dados = DadosSolicitacoesTeste.preparar(banco);
        mvc.perform(post("/api/solicitacoes/" + id + "/" + acao)
                        .header("Authorization", token(dados.solicitanteId())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json("""
                        {"codigo":"DADOS_INVALIDOS","mensagem":"Revise os campos informados.",
                         "erros":[{"campo":"solicitacaoId","mensagem":"Informe um identificador positivo."}]}
                        """));
    }

    @Test
    void recusarRegistraJustificativaEventoELiberaBemParaNovoPedido() throws Exception {
        var cenario = criarPedido("COMUM");
        decidir(cenario, "recusar", cenario.dados().origem().gestorId(), "{\"justificativa\":\"  Equipamento indisponível  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.aprovacoes[0].decisao").value("RECUSADA"))
                .andExpect(jsonPath("$.aprovacoes[0].justificativa").value("Equipamento indisponível"))
                .andExpect(jsonPath("$.eventos[1].tipo").value("RECUSA"))
                .andExpect(jsonPath("$.eventos[1].descricao").value("Equipamento indisponível"));
        criar(cenario.dados()).andExpect(status().isCreated());
        verificarBemInalterado(cenario);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMUM", "ADMINISTRADOR"})
    void solicitanteCancelaSemDecidirEtapaPendenteELiberaBem(String perfil) throws Exception {
        var cenario = criarPedido(perfil);
        cancelar(cenario, cenario.dados().solicitanteId(), "{\"motivo\":\"  Necessidade atendida  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.aprovacoes[0].decisao").value("PENDENTE"))
                .andExpect(jsonPath("$.eventos[1].tipo").value("CANCELAMENTO"))
                .andExpect(jsonPath("$.eventos[1].descricao").value("Necessidade atendida"))
                .andExpect(jsonPath("$.acoesDisponiveis").isEmpty());
        criar(cenario.dados()).andExpect(status().isCreated());
        verificarBemInalterado(cenario);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprovar", "recusar", "cancelar"})
    void bloquearAtorNaoDesignadoSemAlterarPedido(String acao) throws Exception {
        var cenario = criarPedido("COMUM");
        Long outro = cenario.dados().destino().gestorId();
        var resultado = acao.equals("cancelar") ? cancelar(cenario, outro, "{\"motivo\":\"Teste\"}")
                : decidir(cenario, acao, outro, "{\"justificativa\":\"Teste\"}");
        resultado.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACAO_NAO_PERMITIDA"));
        assertEquals("AGUARDANDO_GESTOR_AREA", banco.queryForObject("SELECT status FROM solicitacao WHERE id=?", String.class, cenario.id()));
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id=?", Long.class, cenario.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EM_TRANSITO", "CONCLUIDA", "RECUSADA", "CANCELADA"})
    void impedirCancelamentoEmEstadoIncompativel(String estado) throws Exception {
        var dados = DadosSolicitacoesTeste.preparar(banco);
        Long id = dados.inserirSolicitacao(banco, estado);
        var cenario = new Cenario(dados, id, 0L);
        cancelar(cenario, dados.solicitanteId(), "{\"motivo\":\"Teste\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_INCOMPATIVEL"));
        assertEquals(estado, banco.queryForObject("SELECT status FROM solicitacao WHERE id=?", String.class, id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"recusar", "cancelar"})
    void rejeitarTextoVazioSemRegistrarEvento(String acao) throws Exception {
        var cenario = criarPedido("COMUM");
        var resultado = acao.equals("recusar")
                ? decidir(cenario, acao, cenario.dados().origem().gestorId(), "{\"justificativa\":\"  \"}")
                : cancelar(cenario, cenario.dados().solicitanteId(), "{\"motivo\":\"  \"}");
        resultado.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value(acao.equals("recusar") ? "justificativa" : "motivo"));
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id=?", Long.class, cenario.id()));
    }

    @Test
    void aprovacaoDeOutroPedidoRetorna404SemDecidirNenhumaEtapa() throws Exception {
        var primeiro = criarPedido("COMUM");
        var outro = criarPedido("COMUM");
        var rotaIncorreta = new Cenario(primeiro.dados(), primeiro.id(), outro.aprovacaoId());
        decidir(rotaIncorreta, "aprovar", primeiro.dados().origem().gestorId(), "")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("APROVACAO_NAO_ENCONTRADA"));
        assertEquals("PENDENTE", banco.queryForObject("SELECT decisao FROM aprovacao WHERE id=?", String.class, primeiro.aprovacaoId()));
        assertEquals("PENDENTE", banco.queryForObject("SELECT decisao FROM aprovacao WHERE id=?", String.class, outro.aprovacaoId()));
    }

    private Cenario criarPedido(String perfil) throws Exception {
        // A fixture anterior valida a FK circular imediatamente; nova carga precisa adiá-la.
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var dados = DadosSolicitacoesTeste.preparar(banco);
        banco.update("UPDATE usuario SET perfil=? WHERE id=?", perfil, dados.solicitanteId());
        var pedido = ler(criar(dados).andExpect(status().isCreated()));
        return new Cenario(dados, pedido.get("id").asLong(), pedido.get("aprovacoes").get(0).get("id").asLong());
    }

    private ResultActions criar(DadosSolicitacoesTeste dados) throws Exception {
        return mvc.perform(post("/api/solicitacoes").header("Authorization", token(dados.solicitanteId()))
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "bemId", dados.origem().bemId(), "setorDestinoId", dados.destino().setorId(),
                        "localizacaoDestinoId", dados.destino().localizacaoId(),
                        "responsavelDestinoId", dados.destino().gestorId(), "justificativa", "Movimentação HTTP sintética"))));
    }

    private ResultActions decidir(Cenario cenario, String acao, Long ator, String corpo) throws Exception {
        var requisicao = post("/api/solicitacoes/" + cenario.id() + "/aprovacoes/" + cenario.aprovacaoId() + "/" + acao)
                .header("Authorization", token(ator));
        if (acao.equals("recusar")) requisicao.contentType("application/json").content(corpo);
        return mvc.perform(requisicao);
    }

    private ResultActions cancelar(Cenario cenario, Long ator, String corpo) throws Exception {
        return mvc.perform(post("/api/solicitacoes/" + cenario.id() + "/cancelar").header("Authorization", token(ator))
                .contentType("application/json").content(corpo));
    }

    private String token(Long ator) { return "Bearer " + jwt.gerarToken(ator); }

    private JsonNode ler(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void verificarBemInalterado(Cenario cenario) {
        var dados = cenario.dados();
        var bem = banco.queryForMap("SELECT setor_id,localizacao_id,responsavel_id FROM bem_patrimonial WHERE id=?", dados.origem().bemId());
        assertEquals(dados.origem().setorId(), bem.get("setor_id"));
        assertEquals(dados.origem().localizacaoId(), bem.get("localizacao_id"));
        assertEquals(dados.origem().gestorId(), bem.get("responsavel_id"));
    }

    private record Cenario(DadosSolicitacoesTeste dados, Long id, Long aprovacaoId) { }
}
