package br.edu.simpi.persistencia;

import br.edu.simpi.solicitacao.model.NumeroSolicitacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class ConcorrenciaSolicitacoesIT {

    private final JdbcTemplate banco;
    private final NumeroSolicitacaoService numeros;
    private final PlatformTransactionManager transacoes;

    @Autowired
    ConcorrenciaSolicitacoesIT(JdbcTemplate banco, NumeroSolicitacaoService numeros,
            PlatformTransactionManager transacoes) {
        this.banco = banco;
        this.numeros = numeros;
        this.transacoes = transacoes;
    }

    @Test
    void apenasUmaTransacaoConfirmaPedidoAtivoParaOMesmoBem() throws Exception {
        DadosSolicitacoesTeste dados = prepararConfirmado();
        List<String> resultados = disputar(2, barreira -> () -> {
            try {
                return novaTransacao().execute(situacao -> {
                    limitarEspera();
                    sincronizar(barreira);
                    Long solicitacaoId = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
                    dados.inserirAprovacao(banco, solicitacaoId, "GESTOR_AREA");
                    dados.inserirEvento(banco, solicitacaoId);
                    return "CONFIRMADA";
                });
            } catch (DataIntegrityViolationException excecao) {
                Throwable causa = excecao;
                while (causa != null && !(causa instanceof SQLException)) {
                    causa = causa.getCause();
                }
                assertNotNull(causa);
                assertEquals("23505", ((SQLException) causa).getSQLState());
                assertTrue(causa.getMessage().contains("uq_solicitacao_bem_ativa"));
                return "CONFLITO";
            }
        });
        assertEquals(1L, resultados.stream().filter("CONFIRMADA"::equals).count());
        assertEquals(1L, resultados.stream().filter("CONFLITO"::equals).count());
        assertEquals(1L, banco.queryForObject("SELECT count(*) FROM solicitacao WHERE bem_id = ?",
                Long.class, dados.origem().bemId()));
        for (String tabela : new String[] {"aprovacao", "evento_solicitacao"}) {
            assertEquals(1L, banco.queryForObject("SELECT count(*) FROM " + tabela
                    + " WHERE solicitacao_id IN (SELECT id FROM solicitacao WHERE bem_id = ?)",
                    Long.class, dados.origem().bemId()));
        }
    }

    @Test
    void numerosPersistidosSaoUnicosEntreTransacoesConcorrentes() throws Exception {
        DadosSolicitacoesTeste dados = prepararConfirmado();
        List<String> resultados = disputar(4, barreira -> () -> novaTransacao().execute(situacao -> {
            limitarEspera();
            sincronizar(barreira);
            Long identificador = dados.inserirSolicitacao(banco, "CONCLUIDA");
            return banco.queryForObject("SELECT numero FROM solicitacao WHERE id = ?", String.class, identificador);
        }));
        assertEquals(4, new HashSet<>(resultados).size());
        assertEquals(4L, banco.queryForObject("SELECT count(DISTINCT numero) FROM solicitacao WHERE bem_id = ?",
                Long.class, dados.origem().bemId()));
    }

    @Test
    void rollbackNaoReutilizaNumeroNemReiniciaSequenciaNaMudancaDeAno() {
        NumeroSolicitacao revertido = novaTransacao().execute(situacao -> {
            limitarEspera();
            situacao.setRollbackOnly();
            return numeros.gerar(java.time.Instant.parse("2026-12-31T23:59:59Z"));
        });
        NumeroSolicitacao seguinte = novaTransacao().execute(situacao -> {
            limitarEspera();
            return numeros.gerar(java.time.Instant.parse("2027-01-01T00:00:00Z"));
        });
        assertNotNull(revertido);
        assertNotNull(seguinte);
        assertNotEquals(revertido.valor(), seguinte.valor());
        assertTrue(seguinte.valor().startsWith("SOL-2027-"));
        assertTrue(Long.parseLong(seguinte.valor().substring(9)) > Long.parseLong(revertido.valor().substring(9)));
    }

    private DadosSolicitacoesTeste prepararConfirmado() {
        // Dados sintéticos confirmados são mantidos em simpi_test; não há limpeza destrutiva.
        return Objects.requireNonNull(novaTransacao().execute(situacao -> {
            limitarEspera();
            return DadosSolicitacoesTeste.preparar(banco);
        }));
    }

    private TransactionTemplate novaTransacao() {
        TransactionTemplate transacao = new TransactionTemplate(transacoes);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transacao.setTimeout(8);
        return transacao;
    }

    private void limitarEspera() {
        banco.execute("SET LOCAL lock_timeout = '3s'");
        banco.execute("SET LOCAL statement_timeout = '5s'");
    }

    private void sincronizar(CyclicBarrier barreira) {
        try {
            barreira.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concorrência interrompida", excecao);
        } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException excecao) {
            throw new IllegalStateException("Falha na sincronização concorrente", excecao);
        }
    }

    private <T> List<T> disputar(int participantes,
            java.util.function.Function<CyclicBarrier, Callable<T>> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(participantes, Thread.ofPlatform().daemon().factory());
        List<Future<T>> futuros = new ArrayList<>();
        try {
            CyclicBarrier barreira = new CyclicBarrier(participantes);
            for (int indice = 0; indice < participantes; indice++) {
                futuros.add(executor.submit(tarefa.apply(barreira)));
            }
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(10, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            futuros.forEach(futuro -> futuro.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "As tarefas devem encerrar dentro do limite");
        }
    }
}
