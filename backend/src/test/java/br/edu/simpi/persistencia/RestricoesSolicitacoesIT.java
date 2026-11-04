package br.edu.simpi.persistencia;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
class RestricoesSolicitacoesIT {

    private final JdbcTemplate banco;
    private final TransactionTemplate transacao;

    @Autowired
    RestricoesSolicitacoesIT(JdbcTemplate banco, PlatformTransactionManager transacoes) {
        this.banco = banco;
        this.transacao = new TransactionTemplate(transacoes);
    }

    @Test
    void rejeitaAusenciaDosCamposObrigatorios() {
        String[][] campos = {
            {"solicitacao", "id", "numero", "bem_id", "solicitante_id", "setor_solicitante_id",
                "setor_origem_id", "localizacao_origem_id", "responsavel_origem_id",
                "setor_destino_id", "localizacao_destino_id", "responsavel_destino_id",
                "justificativa", "status", "criada_em"},
            {"aprovacao", "id", "solicitacao_id", "etapa", "aprovador_id", "decisao"},
            {"evento_solicitacao", "id", "solicitacao_id", "autor_id", "tipo", "status_novo", "ocorrido_em"}
        };
        for (String[] tabela : campos) {
            for (int indice = 1; indice < tabela.length; indice++) {
                String coluna = tabela[indice];
                verificarViolacao("23502", cenario -> banco.update(
                        "UPDATE " + tabela[0] + " SET " + coluna + " = NULL WHERE id = ?",
                        cenario.identificador(tabela[0])));
            }
        }
    }

    @Test
    void rejeitaReferenciasInexistentes() {
        String[][] referencias = {
            {"solicitacao", "bem_id", "solicitante_id", "setor_solicitante_id", "setor_origem_id",
                "localizacao_origem_id", "responsavel_origem_id", "setor_destino_id",
                "localizacao_destino_id", "responsavel_destino_id", "enviada_por_id", "recebida_por_id"},
            {"aprovacao", "solicitacao_id", "aprovador_id"},
            {"evento_solicitacao", "solicitacao_id", "autor_id"}
        };
        for (String[] tabela : referencias) {
            for (int indice = 1; indice < tabela.length; indice++) {
                String coluna = tabela[indice];
                verificarViolacao("23503", cenario -> {
                    if (coluna.equals("enviada_por_id") || coluna.equals("recebida_por_id")) {
                        String data = coluna.equals("enviada_por_id") ? "enviada_em" : "recebida_em";
                        banco.update("UPDATE solicitacao SET " + data + " = CURRENT_TIMESTAMP, "
                                + coluna + " = ? WHERE id = ?", Long.MAX_VALUE, cenario.solicitacaoId());
                    } else {
                        banco.update("UPDATE " + tabela[0] + " SET " + coluna + " = ? WHERE id = ?",
                                Long.MAX_VALUE, cenario.identificador(tabela[0]));
                    }
                });
            }
        }
    }

    @Test
    void rejeitaValoresForaDosDominiosFechados() {
        for (String[] campo : new String[][] {{"solicitacao", "status"}, {"aprovacao", "etapa"},
                {"aprovacao", "decisao"}, {"evento_solicitacao", "tipo"},
                {"evento_solicitacao", "status_anterior"}, {"evento_solicitacao", "status_novo"}}) {
            verificarViolacao("23514", cenario -> banco.update(
                    "UPDATE " + campo[0] + " SET " + campo[1] + " = 'INVALIDO' WHERE id = ?",
                    cenario.identificador(campo[0])));
        }
    }

    @Test
    void exigeJustificativaExplicitaNaoVaziaNaRecusa() {
        for (String justificativa : new String[] {null, "", "   ", " \t\n\r "}) {
            verificarViolacao("23514", cenario -> banco.update("""
                    UPDATE aprovacao SET decisao = 'RECUSADA', justificativa = ?, decidida_em = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, justificativa, cenario.aprovacaoId()));
        }
    }

    @Test
    void exigeDataSomenteParaDecisoesConcluidas() {
        verificarViolacao("23514", cenario -> banco.update(
                "UPDATE aprovacao SET decidida_em = CURRENT_TIMESTAMP WHERE id = ?", cenario.aprovacaoId()));
        for (String decisao : new String[] {"APROVADA", "RECUSADA"}) {
            verificarViolacao("23514", cenario -> banco.update(
                    "UPDATE aprovacao SET decisao = ?, justificativa = 'Motivo sintético' WHERE id = ?",
                    decisao, cenario.aprovacaoId()));
        }
    }

    @Test
    void exigePreenchimentoConjuntoDosParesDeEnvioERecebimento() {
        for (String[] par : new String[][] {{"enviada_em", "enviada_por_id"}, {"recebida_em", "recebida_por_id"}}) {
            verificarViolacao("23514", cenario -> banco.update(
                    "UPDATE solicitacao SET " + par[0] + " = CURRENT_TIMESTAMP WHERE id = ?", cenario.solicitacaoId()));
            verificarViolacao("23514", cenario -> banco.update(
                    "UPDATE solicitacao SET " + par[1] + " = ? WHERE id = ?",
                    cenario.dados().solicitanteId(), cenario.solicitacaoId()));
            transacao.executeWithoutResult(situacao -> {
                situacao.setRollbackOnly();
                Cenario cenario = preparar();
                assertEquals(1, banco.update("UPDATE solicitacao SET " + par[0]
                        + " = CURRENT_TIMESTAMP, " + par[1] + " = ? WHERE id = ?",
                        cenario.dados().solicitanteId(), cenario.solicitacaoId()));
            });
        }
    }

    @Test
    void impedeNumeroDuplicadoEAcimaDoLimite() {
        verificarViolacao("23505", cenario -> {
            Long outra = cenario.dados().inserirSolicitacao(banco, "CONCLUIDA");
            banco.update("UPDATE solicitacao SET numero = (SELECT numero FROM solicitacao WHERE id = ?) WHERE id = ?",
                    cenario.solicitacaoId(), outra);
        });
        verificarViolacao("22001", cenario -> banco.update(
                "UPDATE solicitacao SET numero = ? WHERE id = ?", "x".repeat(29), cenario.solicitacaoId()));
    }

    @Test
    void impedeEtapaDuplicadaMasPermiteMesmoAprovadorNasDuasEtapas() {
        verificarViolacao("23505", cenario -> cenario.dados().inserirAprovacao(
                banco, cenario.solicitacaoId(), "GESTOR_AREA"));
        transacao.executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            Cenario cenario = preparar();
            cenario.dados().inserirAprovacao(banco, cenario.solicitacaoId(), "GESTOR_RESPONSAVEL");
            assertEquals(2L, banco.queryForObject(
                    "SELECT count(*) FROM aprovacao WHERE solicitacao_id = ? AND aprovador_id = ?",
                    Long.class, cenario.solicitacaoId(), cenario.dados().origem().gestorId()));
        });
    }

    @Test
    void bloqueiaTodosOsEstadosAtivosELiberaTodosOsEstadosFinais() {
        for (String status : new String[] {"AGUARDANDO_GESTOR_AREA", "AGUARDANDO_GESTOR_RESPONSAVEL", "APROVADA", "EM_TRANSITO"}) {
            verificarViolacao("23505", cenario -> {
                banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", status, cenario.solicitacaoId());
                cenario.dados().inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
            });
        }
        for (String status : new String[] {"CONCLUIDA", "RECUSADA", "CANCELADA"}) {
            transacao.executeWithoutResult(situacao -> {
                situacao.setRollbackOnly();
                Cenario cenario = preparar();
                banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", status, cenario.solicitacaoId());
                assertNotNull(cenario.dados().inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA"));
            });
        }
    }

    @Test
    void reverteSolicitacaoEAprovacaoQuandoGravacaoDoEventoFalha() {
        AtomicReference<Cenario> gravado = new AtomicReference<>();
        RuntimeException falha = assertThrows(RuntimeException.class, () -> transacao.executeWithoutResult(situacao -> {
            Cenario cenario = preparar();
            gravado.set(cenario);
            banco.update("""
                    INSERT INTO evento_solicitacao (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                    VALUES (?, ?, 'CRIACAO', 'AGUARDANDO_GESTOR_AREA', CURRENT_TIMESTAMP)
                    """, cenario.solicitacaoId(), Long.MAX_VALUE);
        }));
        verificarEstadoSql("23503", falha);
        Cenario cenario = gravado.get();
        assertNotNull(cenario);
        for (String tabela : new String[] {"solicitacao", "aprovacao", "evento_solicitacao"}) {
            assertEquals(0L, banco.queryForObject("SELECT count(*) FROM " + tabela + " WHERE id = ?",
                    Long.class, cenario.identificador(tabela)));
        }
    }

    private Cenario preparar() {
        DadosSolicitacoesTeste dados = DadosSolicitacoesTeste.preparar(banco);
        Long solicitacaoId = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
        Long aprovacaoId = dados.inserirAprovacao(banco, solicitacaoId, "GESTOR_AREA");
        return new Cenario(dados, solicitacaoId, aprovacaoId, dados.inserirEvento(banco, solicitacaoId));
    }

    private void verificarViolacao(String estado, Consumer<Cenario> operacao) {
        RuntimeException falha = assertThrows(RuntimeException.class, () -> transacao.executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            operacao.accept(preparar());
        }));
        verificarEstadoSql(estado, falha);
    }

    private void verificarEstadoSql(String estado, RuntimeException falha) {
        Throwable causa = falha;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }
        assertNotNull(causa, "A falha deve ter origem no PostgreSQL");
        assertEquals(estado, ((SQLException) causa).getSQLState());
    }

    private record Cenario(DadosSolicitacoesTeste dados, Long solicitacaoId, Long aprovacaoId, Long eventoId) {
        Long identificador(String tabela) {
            return switch (tabela) {
                case "solicitacao" -> solicitacaoId;
                case "aprovacao" -> aprovacaoId;
                case "evento_solicitacao" -> eventoId;
                default -> throw new IllegalArgumentException("Tabela de fixture desconhecida");
            };
        }
    }
}
