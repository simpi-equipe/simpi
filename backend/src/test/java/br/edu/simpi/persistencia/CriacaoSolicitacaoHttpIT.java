package br.edu.simpi.persistencia;

import br.edu.simpi.autenticacao.JwtService;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class CriacaoSolicitacaoHttpIT {
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
    @CsvSource({"COMUM,AGUARDANDO_GESTOR_AREA", "ADMINISTRADOR,AGUARDANDO_GESTOR_AREA",
            "GESTOR,AGUARDANDO_GESTOR_RESPONSAVEL"})
    void criarConsultarERejeitarDuplicacaoComSegurancaEPersistenciaReais(
            String perfil, StatusSolicitacao estado) throws Exception {
        var dados = DadosSolicitacoesTeste.preparar(banco);
        Long ator = dados.solicitanteId();
        if (perfil.equals("GESTOR")) {
            ator = dados.origem().gestorId();
        } else {
            banco.update("UPDATE usuario SET perfil = ? WHERE id = ?", perfil, ator);
        }
        String autorizacao = "Bearer " + jwt.gerarToken(ator);
        String corpo = json.writeValueAsString(Map.of(
                "bemId", dados.origem().bemId(), "setorDestinoId", dados.destino().setorId(),
                "localizacaoDestinoId", dados.destino().localizacaoId(),
                "responsavelDestinoId", dados.destino().gestorId(), "justificativa", "Movimentação HTTP sintética"));
        var criada = mvc.perform(post("/api/solicitacoes").header("Authorization", autorizacao)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(estado.name()))
                .andExpect(jsonPath("$.solicitante.id").value(ator))
                .andExpect(jsonPath("$.aprovacoes.length()").value(1))
                .andExpect(jsonPath("$.aprovacoes[0].decisao").value("PENDENTE"))
                .andExpect(jsonPath("$.eventos[0].tipo").value("CRIACAO"))
                .andReturn().getResponse();
        long id = json.readTree(criada.getContentAsString()).get("id").asLong();
        assertEquals("/api/solicitacoes/" + id, criada.getHeader("Location"));
        mvc.perform(get(criada.getHeader("Location")).header("Authorization", autorizacao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/solicitacoes").header("Authorization", autorizacao)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BEM_COM_SOLICITACAO_EM_ANDAMENTO"))
                .andExpect(header().doesNotExist("Location"));
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM solicitacao WHERE bem_id = ?",
                Long.class, dados.origem().bemId()));
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM aprovacao WHERE solicitacao_id = ?", Long.class, id));
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id = ?", Long.class, id));
        assertEquals(dados.origem().setorId(), banco.queryForObject(
                "SELECT setor_id FROM bem_patrimonial WHERE id = ?", Long.class, dados.origem().bemId()));
    }
}
