package br.edu.simpi.persistencia;

import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoDecisaoAprovacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class ConcorrenciaDecisaoAprovacaoIT {
    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final PlatformTransactionManager transacoes;
    private final SolicitacaoService servico;
    private final UsuarioRepository usuarios;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;

    @Autowired
    ConcorrenciaDecisaoAprovacaoIT(JdbcTemplate banco, EntityManager gerenciador,
            PlatformTransactionManager transacoes, SolicitacaoService servico,
            UsuarioRepository usuarios, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.transacoes = transacoes;
        this.servico = servico;
        this.usuarios = usuarios;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
    }

    @Test
    void aprovacoesSimultaneasTemUmVencedorSemDecidirEtapaSeguinte() throws Exception {
        disputar(false, false, false);
        disputar(false, false, true);
    }

    @Test
    void recusasSimultaneasTemUmVencedorSemDuplicarEvento() throws Exception {
        disputar(true, true, false);
        disputar(true, true, true);
    }

    @Test
    void aprovacaoERecusaSimultaneasConfirmamSomenteUmaDecisao() throws Exception {
        disputar(false, true, false);
        disputar(false, true, true);
    }

    private void disputar(boolean primeiraRecusa, boolean segundaRecusa, boolean segundaEtapa) throws Exception {
        Cenario cenario = prepararConfirmado(segundaEtapa);
        CyclicBarrier barreira = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        List<Future<String>> futuros = new ArrayList<>();
        try {
            for (boolean recusa : List.of(primeiraRecusa, segundaRecusa)) {
                futuros.add(executor.submit(() -> {
                    try {
                        novaTransacao().executeWithoutResult(situacao -> {
                            limitarEspera();
                            // Cada conexão carrega estado antigo antes de disputar o mesmo lock.
                            solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus();
                            aprovacoes.findById(cenario.aprovacaoId()).orElseThrow().getDecisao();
                            var ator = usuarios.findById(cenario.dados().origem().gestorId()).orElseThrow();
                            sincronizar(barreira);
                            if (recusa) {
                                servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Recusa concorrente", ator);
                            } else {
                                servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), ator);
                            }
                        });
                        return "CONFIRMADA";
                    } catch (ExcecaoDecisaoAprovacao falha) {
                        assertEquals(ETAPA_JA_DECIDIDA, falha.getMotivo());
                        return "CONFLITO";
                    }
                }));
            }
            List<String> resultados = new ArrayList<>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1L, resultados.stream().filter("CONFIRMADA"::equals).count());
            assertEquals(1L, resultados.stream().filter("CONFLITO"::equals).count());
            novaTransacao().executeWithoutResult(situacao -> {
                var pedido = solicitacoes.findById(cenario.pedido().id()).orElseThrow();
                var decidida = aprovacoes.findById(cenario.aprovacaoId()).orElseThrow();
                boolean recusada = decidida.getDecisao() == DecisaoAprovacao.RECUSADA;
                assertEquals(recusada ? StatusSolicitacao.RECUSADA : segundaEtapa
                        ? StatusSolicitacao.APROVADA : StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, pedido.getStatus());
                var etapas = aprovacoes.buscarPorSolicitacao(pedido.getId());
                assertEquals(segundaEtapa || !recusada ? 2 : 1, etapas.size());
                assertEquals(!segundaEtapa && !recusada ? 1L : 0L,
                        etapas.stream().filter(etapa -> etapa.getDecisao() == DecisaoAprovacao.PENDENTE).count());
                var historico = eventos.buscarPorSolicitacao(pedido.getId());
                assertEquals(cenario.pedido().eventos().size() + 1, historico.size());
                assertEquals(decidida.getDecididaEm(), historico.getLast().getOcorridoEm());
                assertEquals(cenario.dados().origem().gestorId(), historico.getLast().getAutor().getId());
                verificarBemInalterado(cenario.dados());
            });
        } finally {
            futuros.forEach(futuro -> futuro.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void revalidaContaPerfilESetorPersistidosAposEsperaRealPeloBloqueio() throws Exception {
        aguardarBloqueioEAlterarAtor("ativo = false", NAO_AUTENTICADO);
        aguardarBloqueioEAlterarAtor("perfil = 'COMUM'", ACAO_NAO_PERMITIDA);
        aguardarBloqueioEAlterarAtor("setor_id = %d", ACAO_NAO_PERMITIDA);
    }

    @Test
    void instanteDaDecisaoEPosteriorAEsperaRealPeloBloqueio() throws Exception {
        aguardarBloqueioEAlterarAtor("nome = nome", null);
    }

    private void aguardarBloqueioEAlterarAtor(String alteracao, MotivoFalhaDecisaoAprovacao esperado) throws Exception {
        Cenario cenario = prepararConfirmado(false);
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch conexaoPronta = new CountDownLatch(1);
        AtomicInteger conexao = new AtomicInteger();
        AtomicReference<SolicitacaoResponse> resposta = new AtomicReference<>();
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        List<Future<?>> futuros = new ArrayList<>();
        try {
            var detentor = executor.submit(() -> novaTransacao().executeWithoutResult(situacao -> {
                limitarEspera();
                solicitacoes.buscarPorIdParaAtualizacao(cenario.pedido().id()).orElseThrow();
                bloqueado.countDown();
                aguardar(liberar);
                banco.update("UPDATE usuario SET " + alteracao.formatted(cenario.dados().destino().setorId())
                        + " WHERE id = ?", cenario.dados().origem().gestorId());
            }));
            futuros.add(detentor);
            assertTrue(bloqueado.await(4, TimeUnit.SECONDS));
            var decisor = executor.submit(() -> {
                try {
                    novaTransacao().executeWithoutResult(situacao -> {
                        limitarEspera();
                        var ator = usuarios.findById(cenario.dados().origem().gestorId()).orElseThrow();
                        // Também força uso de entidades já presentes no contexto JPA.
                        solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus();
                        aprovacoes.findById(cenario.aprovacaoId()).orElseThrow().getDecisao();
                        assertTrue(gerenciador.contains(ator));
                        conexao.set(banco.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        conexaoPronta.countDown();
                        resposta.set(servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), ator));
                    });
                    return null;
                } catch (ExcecaoDecisaoAprovacao falha) {
                    return falha.getMotivo();
                }
            });
            futuros.add(decisor);
            assertTrue(conexaoPronta.await(4, TimeUnit.SECONDS));
            confirmarEsperaNoBanco(conexao.get());
            Instant liberadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
            liberar.countDown();
            detentor.get(10, TimeUnit.SECONDS);
            assertEquals(esperado, decisor.get(10, TimeUnit.SECONDS));
            novaTransacao().executeWithoutResult(situacao -> {
                var etapa = aprovacoes.findById(cenario.aprovacaoId()).orElseThrow();
                if (esperado == null) {
                    assertNotNull(resposta.get());
                    assertFalse(etapa.getDecididaEm().isBefore(liberadoEm));
                    assertEquals(resposta.get().aprovacoes().getFirst().decididaEm(), etapa.getDecididaEm());
                } else {
                    assertNull(resposta.get());
                    assertEquals(DecisaoAprovacao.PENDENTE, etapa.getDecisao());
                    assertNull(etapa.getDecididaEm());
                    assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                            solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus());
                    assertEquals(1, aprovacoes.buscarPorSolicitacao(cenario.pedido().id()).size());
                    assertEquals(1, eventos.buscarPorSolicitacao(cenario.pedido().id()).size());
                }
                verificarBemInalterado(cenario.dados());
            });
        } finally {
            liberar.countDown();
            futuros.forEach(futuro -> futuro.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void confirmarEsperaNoBanco(int conexao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(banco.queryForObject(
                    "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, conexao))) {
                return;
            }
            Thread.sleep(10);
        }
        fail("A decisão deve estar aguardando o lock da solicitação");
    }

    private void verificarBemInalterado(DadosSolicitacoesTeste dados) {
        var bem = banco.queryForMap("SELECT setor_id, localizacao_id, responsavel_id FROM bem_patrimonial WHERE id = ?",
                dados.origem().bemId());
        assertEquals(dados.origem().setorId(), bem.get("setor_id"));
        assertEquals(dados.origem().localizacaoId(), bem.get("localizacao_id"));
        assertEquals(dados.origem().gestorId(), bem.get("responsavel_id"));
    }

    private Cenario prepararConfirmado(boolean segundaEtapa) {
        // Dados sintéticos confirmados são mantidos em simpi_test, sem exclusões.
        return novaTransacao().execute(situacao -> {
            limitarEspera();
            var dados = DadosSolicitacoesTeste.preparar(banco);
            var pedido = servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                    dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética"),
                    usuarios.findById(dados.solicitanteId()).orElseThrow());
            if (segundaEtapa) {
                pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(),
                        usuarios.findById(dados.origem().gestorId()).orElseThrow());
            }
            return new Cenario(dados, pedido);
        });
    }

    private TransactionTemplate novaTransacao() {
        var transacao = new TransactionTemplate(transacoes);
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
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Disputa interrompida", falha);
        } catch (Exception falha) {
            throw new IllegalStateException("Falha ao sincronizar disputa", falha);
        }
    }

    private void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(4, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tempo de espera excedido");
            }
        } catch (InterruptedException falha) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera interrompida", falha);
        }
    }

    private record Cenario(DadosSolicitacoesTeste dados, SolicitacaoResponse pedido) {
        Long aprovacaoId() { return pedido.aprovacoes().getLast().id(); }
    }
}
