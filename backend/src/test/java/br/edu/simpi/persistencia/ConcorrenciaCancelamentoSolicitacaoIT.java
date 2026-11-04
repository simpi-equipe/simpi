package br.edu.simpi.persistencia;

import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoDecisaoAprovacao;
import br.edu.simpi.solicitacao.service.SolicitacaoMapper;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class ConcorrenciaCancelamentoSolicitacaoIT {
    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final PlatformTransactionManager transacoes;
    private final SolicitacaoService servico;
    private final UsuarioRepository usuarios;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final SolicitacaoMapper mapeador;

    @Autowired
    ConcorrenciaCancelamentoSolicitacaoIT(JdbcTemplate banco, EntityManager gerenciador,
            PlatformTransactionManager transacoes, SolicitacaoService servico,
            UsuarioRepository usuarios, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos, SolicitacaoMapper mapeador) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.transacoes = transacoes;
        this.servico = servico;
        this.usuarios = usuarios;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.mapeador = mapeador;
    }

    @Test
    void doisCancelamentosConcorrentesConfirmamUmaTransicaoEUmEvento() throws Exception {
        Cenario cenario = prepararConfirmado(false);
        var execucao = executarComOrdem(cenario, this::cancelar, this::cancelar);
        assertEquals(StatusSolicitacao.CANCELADA, execucao.primeira().status());
        var falha = assertInstanceOf(ExcecaoCancelamentoSolicitacao.class, execucao.segunda().falha());
        assertEquals(ESTADO_INCOMPATIVEL, falha.getMotivo());
        verificarConfirmado(cenario, execucao.primeira());
    }

    @Test
    void cancelamentoPrimeiroImpedeAprovacaoOuRecusaNasDuasEtapas() throws Exception {
        for (boolean segundaEtapa : List.of(false, true)) {
            for (boolean recusa : List.of(false, true)) {
                Cenario cenario = prepararConfirmado(segundaEtapa);
                var execucao = executarComOrdem(cenario, this::cancelar, atual -> decidir(atual, recusa));
                var falha = assertInstanceOf(ExcecaoDecisaoAprovacao.class, execucao.segunda().falha());
                assertEquals(MotivoFalhaDecisaoAprovacao.ESTADO_INCOMPATIVEL, falha.getMotivo());
                assertEquals(cenario.pedido().aprovacoes(), execucao.primeira().aprovacoes());
                verificarConfirmado(cenario, execucao.primeira());
            }
        }
    }

    @Test
    void aprovacaoPrimeiroPermiteCancelamentoPosteriorPreservandoDecisaoConfirmada() throws Exception {
        for (boolean segundaEtapa : List.of(false, true)) {
            Cenario cenario = prepararConfirmado(segundaEtapa);
            var execucao = executarComOrdem(cenario, atual -> decidir(atual, false), this::cancelar);
            assertNull(execucao.segunda().falha());
            var cancelada = execucao.segunda().resposta();
            assertEquals(StatusSolicitacao.CANCELADA, cancelada.status());
            assertEquals(execucao.primeira().aprovacoes(), cancelada.aprovacoes());
            assertEquals(cenario.pedido().eventos().size() + 2, cancelada.eventos().size());
            assertEquals(TipoEventoSolicitacao.APROVACAO, cancelada.eventos().get(cancelada.eventos().size() - 2).tipo());
            assertEquals(TipoEventoSolicitacao.CANCELAMENTO, cancelada.eventos().getLast().tipo());
            assertEquals(execucao.primeira().status(), cancelada.eventos().getLast().statusAnterior());
            verificarConfirmado(cenario, cancelada);
        }
    }

    @Test
    void recusaPrimeiroImpedeCancelamentoSemDuplicarEventoNasDuasEtapas() throws Exception {
        for (boolean segundaEtapa : List.of(false, true)) {
            Cenario cenario = prepararConfirmado(segundaEtapa);
            var execucao = executarComOrdem(cenario, atual -> decidir(atual, true), this::cancelar);
            assertEquals(StatusSolicitacao.RECUSADA, execucao.primeira().status());
            var falha = assertInstanceOf(ExcecaoCancelamentoSolicitacao.class, execucao.segunda().falha());
            assertEquals(ESTADO_INCOMPATIVEL, falha.getMotivo());
            verificarConfirmado(cenario, execucao.primeira());
        }
    }

    @Test
    void revalidaContaEPerdaDeAcessoAposEsperaRealPeloBloqueio() throws Exception {
        for (boolean inativar : List.of(false, true)) {
            Cenario cenario = prepararConfirmado(false);
            var execucao = executarComOrdem(cenario, atual -> {
                solicitacoes.buscarPorIdParaAtualizacao(atual.pedido().id()).orElseThrow();
                if (inativar) {
                    banco.update("UPDATE usuario SET ativo = false WHERE id = ?", atual.dados().solicitanteId());
                } else {
                    banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", atual.terceiroSetorId(), atual.dados().solicitanteId());
                }
                return atual.pedido();
            }, this::cancelar);
            var falha = assertInstanceOf(ExcecaoCancelamentoSolicitacao.class, execucao.segunda().falha());
            assertEquals(inativar ? NAO_AUTENTICADO : ACAO_NAO_PERMITIDA, falha.getMotivo());
            verificarConfirmado(cenario, cenario.pedido());
        }
    }

    @Test
    void mudancaParaSetorParticipanteDuranteEsperaMantemCancelamentoPermitido() throws Exception {
        Cenario cenario = prepararConfirmado(false);
        var execucao = executarComOrdem(cenario, atual -> {
            solicitacoes.buscarPorIdParaAtualizacao(atual.pedido().id()).orElseThrow();
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", atual.dados().destino().setorId(), atual.dados().solicitanteId());
            return atual.pedido();
        }, this::cancelar);
        assertNull(execucao.segunda().falha());
        var cancelada = execucao.segunda().resposta();
        assertEquals(StatusSolicitacao.CANCELADA, cancelada.status());
        assertEquals(cenario.pedido().solicitante(), cancelada.solicitante());
        assertFalse(cancelada.eventos().getLast().ocorridoEm().isBefore(execucao.liberadoEm()));
        verificarConfirmado(cenario, cancelada);
    }

    @Test
    void revalidaPerfilAtualPreservandoVisibilidadeGlobalDoSolicitanteAdministrador() throws Exception {
        Cenario cenario = prepararConfirmado(false);
        var execucao = executarComOrdem(cenario, atual -> {
            solicitacoes.buscarPorIdParaAtualizacao(atual.pedido().id()).orElseThrow();
            banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR', setor_id = ? WHERE id = ?",
                    atual.terceiroSetorId(), atual.dados().solicitanteId());
            return atual.pedido();
        }, this::cancelar);
        assertNull(execucao.segunda().falha());
        assertEquals(StatusSolicitacao.CANCELADA, execucao.segunda().resposta().status());
        verificarConfirmado(cenario, execucao.segunda().resposta());
    }

    @Test
    void criacaoSoEnxergaLiberacaoDepoisDoCommitDoCancelamentoSemOrdemInversaDeLocks() throws Exception {
        Cenario cenario = prepararConfirmado(false);
        CountDownLatch alterado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
        Future<SolicitacaoResponse> cancelamento = null;
        try {
            cancelamento = executor.submit(() -> novaTransacao().execute(situacao -> {
                limitarEspera();
                var resposta = cancelar(cenario);
                alterado.countDown();
                aguardar(liberar);
                return resposta;
            }));
            assertTrue(alterado.await(4, TimeUnit.SECONDS));
            Usuario ator = novaTransacao().execute(situacao -> usuario(cenario.dados().solicitanteId()));
            var falha = assertThrows(ExcecaoCriacaoSolicitacao.class, () -> servico.criar(entrada(cenario.dados()), ator));
            assertEquals(MotivoFalhaCriacaoSolicitacao.BEM_COM_SOLICITACAO_EM_ANDAMENTO, falha.getMotivo());
            liberar.countDown();
            var cancelada = cancelamento.get(10, TimeUnit.SECONDS);
            var nova = servico.criar(entrada(cenario.dados()), ator);
            assertNotEquals(cancelada.id(), nova.id());
            verificarConfirmado(cenario, cancelada);
        } finally {
            liberar.countDown();
            if (cancelamento != null) {
                cancelamento.cancel(true);
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Execucao executarComOrdem(Cenario cenario, Function<Cenario, SolicitacaoResponse> primeira,
            Function<Cenario, SolicitacaoResponse> segunda) throws Exception {
        CountDownLatch alterado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch conexaoPronta = new CountDownLatch(1);
        AtomicInteger conexao = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        Future<SolicitacaoResponse> detentor = null;
        Future<Resultado> concorrente = null;
        try {
            detentor = executor.submit(() -> novaTransacao().execute(situacao -> {
                limitarEspera();
                var resposta = primeira.apply(cenario);
                alterado.countDown();
                aguardar(liberar);
                return resposta;
            }));
            assertTrue(alterado.await(4, TimeUnit.SECONDS));
            concorrente = executor.submit(() -> {
                try {
                    var resposta = novaTransacao().execute(situacao -> {
                        limitarEspera();
                        // Estado e ator antigos ficam no contexto enquanto outra conexão segura o lock.
                        solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus();
                        aprovacoes.buscarPorSolicitacao(cenario.pedido().id())
                                .forEach(aprovacao -> aprovacao.getDecisao());
                        var ator = usuario(cenario.dados().solicitanteId());
                        assertTrue(gerenciador.contains(ator));
                        conexao.set(banco.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        conexaoPronta.countDown();
                        return segunda.apply(cenario);
                    });
                    return new Resultado(resposta, null);
                } catch (ExcecaoCancelamentoSolicitacao | ExcecaoDecisaoAprovacao falha) {
                    return new Resultado(null, falha);
                }
            });
            assertTrue(conexaoPronta.await(4, TimeUnit.SECONDS));
            confirmarEsperaNoBanco(conexao.get());
            Instant liberadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
            liberar.countDown();
            return new Execucao(detentor.get(10, TimeUnit.SECONDS), concorrente.get(10, TimeUnit.SECONDS), liberadoEm);
        } finally {
            liberar.countDown();
            if (detentor != null) {
                detentor.cancel(true);
            }
            if (concorrente != null) {
                concorrente.cancel(true);
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private SolicitacaoResponse cancelar(Cenario cenario) {
        return servico.cancelar(cenario.pedido().id(), "Cancelamento concorrente", usuario(cenario.dados().solicitanteId()));
    }

    private SolicitacaoResponse decidir(Cenario cenario, boolean recusa) {
        Long aprovacaoId = cenario.pedido().aprovacoes().getLast().id();
        Usuario gestor = usuario(cenario.dados().origem().gestorId());
        return recusa ? servico.recusar(cenario.pedido().id(), aprovacaoId, "Recusa concorrente", gestor)
                : servico.aprovar(cenario.pedido().id(), aprovacaoId, gestor);
    }

    private void verificarConfirmado(Cenario cenario, SolicitacaoResponse esperado) {
        novaTransacao().executeWithoutResult(situacao -> {
            Long id = cenario.pedido().id();
            var atual = mapeador.mapear(solicitacoes.findById(id).orElseThrow(), aprovacoes.buscarPorSolicitacao(id),
                    eventos.buscarPorSolicitacao(id), usuario(cenario.dados().solicitanteId()));
            assertEquals(esperado.status(), atual.status());
            assertEquals(esperado.aprovacoes(), atual.aprovacoes());
            assertEquals(esperado.eventos(), atual.eventos());
            assertEquals(cenario.pedido().origem(), atual.origem());
            assertEquals(cenario.pedido().destino(), atual.destino());
            assertEquals(cenario.pedido().solicitante(), atual.solicitante());
            assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
        });
    }

    private Cenario prepararConfirmado(boolean segundaEtapa) {
        // Fixtures confirmadas permanecem no banco simpi_test, sem limpeza destrutiva.
        return novaTransacao().execute(situacao -> {
            limitarEspera();
            var terceiro = DadosPatrimoniaisTeste.preparar(banco);
            var dados = DadosSolicitacoesTeste.preparar(banco);
            var pedido = servico.criar(entrada(dados), usuario(dados.solicitanteId()));
            if (segundaEtapa) {
                pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(), usuario(dados.origem().gestorId()));
            }
            return new Cenario(dados, pedido, terceiro.setorId(), cadastroBem(dados));
        });
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
        fail("A operação concorrente deve estar aguardando o lock da solicitação");
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

    private Map<String, Object> cadastroBem(DadosSolicitacoesTeste dados) {
        return banco.queryForMap("SELECT * FROM bem_patrimonial WHERE id = ?", dados.origem().bemId());
    }

    private CriarSolicitacaoRequest entrada(DadosSolicitacoesTeste dados) {
        return new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética");
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow();
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

    private record Cenario(DadosSolicitacoesTeste dados, SolicitacaoResponse pedido,
            Long terceiroSetorId, Map<String, Object> cadastroBem) { }
    private record Resultado(SolicitacaoResponse resposta, RuntimeException falha) { }
    private record Execucao(SolicitacaoResponse primeira, Resultado segunda, Instant liberadoEm) { }
}
