package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonial;
import br.edu.simpi.localizacao.Localizacao;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.model.NumeroSolicitacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.usuario.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class SolicitacoesIT {

    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final NumeroSolicitacaoService numeros;

    @Autowired
    SolicitacoesIT(JdbcTemplate banco, EntityManager gerenciador, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos, NumeroSolicitacaoService numeros) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.numeros = numeros;
    }

    @Test
    void persisteEReleSolicitacaoAprovacaoEEventoComInstantesUtc() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        NumeroSolicitacao numero = numeros.gerar(DadosSolicitacoesTeste.CRIADA_EM);
        Solicitacao solicitacao = solicitacoes.saveAndFlush(criarSolicitacao(dados, numero));
        Aprovacao aprovacao = aprovacoes.saveAndFlush(new Aprovacao(solicitacao,
                EtapaAprovacao.GESTOR_AREA, gerenciador.getReference(Usuario.class, dados.origem().gestorId())));
        EventoSolicitacao evento = eventos.saveAndFlush(new EventoSolicitacao(solicitacao,
                gerenciador.getReference(Usuario.class, dados.solicitanteId()), TipoEventoSolicitacao.CRIACAO,
                null, solicitacao.getStatus(), null, numero.criadaEm()));
        gerenciador.clear();
        banco.execute("SET LOCAL TIME ZONE 'America/Sao_Paulo'");

        Solicitacao relida = solicitacoes.findById(solicitacao.getId()).orElseThrow();
        assertEquals(numero.valor(), relida.getNumero());
        assertEquals(Instant.parse("2026-12-31T23:59:59.999999Z"), relida.getCriadaEm());
        assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, relida.getStatus());
        assertNull(relida.getEnviadaEm());
        assertNull(relida.getEnviadaPor());
        assertNull(relida.getRecebidaEm());
        assertNull(relida.getRecebidaPor());
        Aprovacao etapa = aprovacoes.findById(aprovacao.getId()).orElseThrow();
        assertEquals(EtapaAprovacao.GESTOR_AREA, etapa.getEtapa());
        assertEquals(DecisaoAprovacao.PENDENTE, etapa.getDecisao());
        assertNull(etapa.getDecididaEm());
        assertNull(etapa.getJustificativa());
        EventoSolicitacao registro = eventos.findById(evento.getId()).orElseThrow();
        assertEquals(TipoEventoSolicitacao.CRIACAO, registro.getTipo());
        assertEquals(numero.criadaEm(), registro.getOcorridoEm());
        assertEquals(relida.getStatus(), registro.getStatusNovo());
        assertNull(registro.getStatusAnterior());
        assertEquals("AGUARDANDO_GESTOR_AREA", banco.queryForObject(
                "SELECT status FROM solicitacao WHERE id = ?", String.class, solicitacao.getId()));
    }

    @Test
    void preservaReferenciasHistoricasAposMudancaDoCadastroAtual() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        Solicitacao solicitacao = solicitacoes.saveAndFlush(criarSolicitacao(
                dados, numeros.gerar(DadosSolicitacoesTeste.CRIADA_EM)));
        banco.update("UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ? WHERE id = ?",
                dados.destino().setorId(), dados.destino().localizacaoId(), dados.destino().gestorId(),
                dados.origem().bemId());
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), dados.solicitanteId());
        gerenciador.clear();

        Solicitacao relida = solicitacoes.findById(solicitacao.getId()).orElseThrow();
        assertEquals(dados.origem().setorId(), relida.getSetorSolicitante().getId());
        assertEquals(dados.origem().setorId(), relida.getSetorOrigem().getId());
        assertEquals(dados.origem().localizacaoId(), relida.getLocalizacaoOrigem().getId());
        assertEquals(dados.origem().gestorId(), relida.getResponsavelOrigem().getId());
        assertEquals(dados.destino().setorId(), relida.getSetorDestino().getId());
        assertEquals(dados.destino().localizacaoId(), relida.getLocalizacaoDestino().getId());
        assertEquals(dados.destino().gestorId(), relida.getResponsavelDestino().getId());
        assertEquals(dados.destino().setorId(), relida.getBem().getSetor().getId());
    }

    @Test
    void mapeamentoImpedeRegravacaoAcidentalDasReferenciasHistoricas() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        Solicitacao solicitacao = solicitacoes.saveAndFlush(criarSolicitacao(
                dados, numeros.gerar(DadosSolicitacoesTeste.CRIADA_EM)));
        // Simula alteração interna acidental, apesar de não existirem setters públicos.
        ReflectionTestUtils.setField(solicitacao, "setorSolicitante",
                gerenciador.getReference(Setor.class, dados.destino().setorId()));
        ReflectionTestUtils.setField(solicitacao, "setorOrigem",
                gerenciador.getReference(Setor.class, dados.destino().setorId()));
        ReflectionTestUtils.setField(solicitacao, "localizacaoOrigem",
                gerenciador.getReference(Localizacao.class, dados.destino().localizacaoId()));
        ReflectionTestUtils.setField(solicitacao, "responsavelOrigem",
                gerenciador.getReference(Usuario.class, dados.destino().gestorId()));
        ReflectionTestUtils.setField(solicitacao, "setorDestino",
                gerenciador.getReference(Setor.class, dados.origem().setorId()));
        ReflectionTestUtils.setField(solicitacao, "localizacaoDestino",
                gerenciador.getReference(Localizacao.class, dados.origem().localizacaoId()));
        ReflectionTestUtils.setField(solicitacao, "responsavelDestino",
                gerenciador.getReference(Usuario.class, dados.origem().gestorId()));
        solicitacoes.flush();
        gerenciador.clear();
        Solicitacao relida = solicitacoes.findById(solicitacao.getId()).orElseThrow();
        assertEquals(dados.origem().setorId(), relida.getSetorSolicitante().getId());
        assertEquals(dados.origem().setorId(), relida.getSetorOrigem().getId());
        assertEquals(dados.origem().localizacaoId(), relida.getLocalizacaoOrigem().getId());
        assertEquals(dados.origem().gestorId(), relida.getResponsavelOrigem().getId());
        assertEquals(dados.destino().setorId(), relida.getSetorDestino().getId());
        assertEquals(dados.destino().localizacaoId(), relida.getLocalizacaoDestino().getId());
        assertEquals(dados.destino().gestorId(), relida.getResponsavelDestino().getId());
    }

    @Test
    void releTodosOsValoresDosEnumsPersistidosComoTexto() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        Long solicitacaoId = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
        Long aprovacaoId = dados.inserirAprovacao(banco, solicitacaoId, "GESTOR_AREA");
        Long eventoId = dados.inserirEvento(banco, solicitacaoId);
        for (StatusSolicitacao status : StatusSolicitacao.values()) {
            banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", status.name(), solicitacaoId);
            banco.update("UPDATE evento_solicitacao SET status_anterior = ?, status_novo = ? WHERE id = ?",
                    status.name(), status.name(), eventoId);
            gerenciador.clear();
            assertEquals(status, solicitacoes.findById(solicitacaoId).orElseThrow().getStatus());
            EventoSolicitacao evento = eventos.findById(eventoId).orElseThrow();
            assertEquals(status, evento.getStatusAnterior());
            assertEquals(status, evento.getStatusNovo());
        }
        for (EtapaAprovacao etapa : EtapaAprovacao.values()) {
            banco.update("UPDATE aprovacao SET etapa = ? WHERE id = ?", etapa.name(), aprovacaoId);
            gerenciador.clear();
            assertEquals(etapa, aprovacoes.findById(aprovacaoId).orElseThrow().getEtapa());
        }
        for (DecisaoAprovacao decisao : DecisaoAprovacao.values()) {
            banco.update("""
                    UPDATE aprovacao SET decisao = ?, justificativa = 'Motivo sintético',
                        decidida_em = CASE WHEN ? = 'PENDENTE' THEN NULL ELSE CURRENT_TIMESTAMP END WHERE id = ?
                    """, decisao.name(), decisao.name(), aprovacaoId);
            gerenciador.clear();
            assertEquals(decisao, aprovacoes.findById(aprovacaoId).orElseThrow().getDecisao());
        }
        for (TipoEventoSolicitacao tipo : TipoEventoSolicitacao.values()) {
            banco.update("UPDATE evento_solicitacao SET tipo = ? WHERE id = ?", tipo.name(), eventoId);
            gerenciador.clear();
            assertEquals(tipo, eventos.findById(eventoId).orElseThrow().getTipo());
        }
    }

    @Test
    void releDatasEAtoresDeDecisaoEnvioERecebimentoComoInstantes() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        Long solicitacaoId = dados.inserirSolicitacao(banco, "CONCLUIDA");
        Long aprovacaoId = dados.inserirAprovacao(banco, solicitacaoId, "GESTOR_RESPONSAVEL");
        OffsetDateTime envio = OffsetDateTime.parse("2027-01-01T00:00:00.123456-03:00");
        OffsetDateTime recebimento = envio.plusHours(1);
        banco.update("""
                UPDATE solicitacao SET enviada_em = ?, enviada_por_id = ?, recebida_em = ?, recebida_por_id = ?
                WHERE id = ?
                """, envio, dados.origem().gestorId(), recebimento, dados.destino().gestorId(), solicitacaoId);
        banco.update("UPDATE aprovacao SET decisao = 'APROVADA', decidida_em = ? WHERE id = ?",
                envio.minusHours(1), aprovacaoId);
        gerenciador.clear();
        banco.execute("SET LOCAL TIME ZONE 'America/Sao_Paulo'");

        Solicitacao relida = solicitacoes.findById(solicitacaoId).orElseThrow();
        assertEquals(envio.toInstant(), relida.getEnviadaEm());
        assertEquals(recebimento.toInstant(), relida.getRecebidaEm());
        assertEquals(dados.origem().gestorId(), relida.getEnviadaPor().getId());
        assertEquals(dados.destino().gestorId(), relida.getRecebidaPor().getId());
        assertEquals(envio.minusHours(1).toInstant(), aprovacoes.findById(aprovacaoId).orElseThrow().getDecididaEm());
    }

    private Solicitacao criarSolicitacao(DadosSolicitacoesTeste dados, NumeroSolicitacao numero) {
        return new Solicitacao(numero, gerenciador.getReference(BemPatrimonial.class, dados.origem().bemId()),
                gerenciador.getReference(Usuario.class, dados.solicitanteId()),
                gerenciador.getReference(Setor.class, dados.destino().setorId()),
                gerenciador.getReference(Localizacao.class, dados.destino().localizacaoId()),
                gerenciador.getReference(Usuario.class, dados.destino().gestorId()),
                "Justificativa sintética", StatusSolicitacao.AGUARDANDO_GESTOR_AREA);
    }
}
