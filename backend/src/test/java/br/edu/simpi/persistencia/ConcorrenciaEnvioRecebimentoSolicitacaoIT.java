package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoExecucaoSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoMapper;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

import static br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class ConcorrenciaEnvioRecebimentoSolicitacaoIT {
    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final PlatformTransactionManager transacoes;
    private final SolicitacaoService servico;
    private final UsuarioRepository usuarios;
    private final BemPatrimonialRepository bens;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final SolicitacaoMapper mapeador;

    @Autowired
    ConcorrenciaEnvioRecebimentoSolicitacaoIT(JdbcTemplate banco, EntityManager gerenciador,
            PlatformTransactionManager transacoes, SolicitacaoService servico, UsuarioRepository usuarios,
            BemPatrimonialRepository bens, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos, SolicitacaoMapper mapeador) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.transacoes = transacoes;
        this.servico = servico;
        this.usuarios = usuarios;
        this.bens = bens;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.mapeador = mapeador;
    }

    @Test
    void enviosConcorrentesConfirmamUmAtorUmaDataEUmEvento() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.APROVADA);
        var execucao = executarComOrdem(cenario, this::enviar, this::enviar, false);
        assertEquals(ESTADO_INCOMPATIVEL, assertInstanceOf(ExcecaoExecucaoSolicitacao.class, execucao.segunda().falha()).getMotivo());
        assertEquals(cenario.pedido().eventos().size() + 1, execucao.primeira().eventos().size());
        verificarConfirmado(cenario, execucao.primeira(), cenario.cadastroBem());
    }

    @Test
    void recebimentosConcorrentesConfirmamUmaAtualizacaoDoBemEUmEvento() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.EM_TRANSITO);
        var execucao = executarComOrdem(cenario, this::receber, this::receber, false);
        assertEquals(ESTADO_INCOMPATIVEL, assertInstanceOf(ExcecaoExecucaoSolicitacao.class, execucao.segunda().falha()).getMotivo());
        assertEquals(cenario.pedido().eventos().size() + 1, execucao.primeira().eventos().size());
        verificarConfirmado(cenario, execucao.primeira(), destinoAplicado(cenario));
    }

    @Test
    void envioECancelamentoSaoMutuamenteExclusivosNasDuasOrdens() throws Exception {
        for (boolean cancelarPrimeiro : List.of(false, true)) {
            Cenario cenario = prepararConfirmado(StatusSolicitacao.APROVADA);
            var execucao = cancelarPrimeiro
                    ? executarComOrdem(cenario, this::cancelar, this::enviar, false)
                    : executarComOrdem(cenario, this::enviar, this::cancelar, false);
            if (cancelarPrimeiro) {
                assertEquals(ESTADO_INCOMPATIVEL,
                        assertInstanceOf(ExcecaoExecucaoSolicitacao.class, execucao.segunda().falha()).getMotivo());
            } else {
                assertEquals(MotivoFalhaCancelamentoSolicitacao.ESTADO_INCOMPATIVEL,
                        assertInstanceOf(ExcecaoCancelamentoSolicitacao.class, execucao.segunda().falha()).getMotivo());
            }
            assertEquals(cenario.pedido().eventos().size() + 1, execucao.primeira().eventos().size());
            verificarConfirmado(cenario, execucao.primeira(), cenario.cadastroBem());
        }
    }

    @Test
    void recebimentoPodeAguardarEnvioEConcluirDepoisDeSeuCommit() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.APROVADA);
        var execucao = executarComOrdem(cenario, this::enviar, this::receber, false);
        assertNull(execucao.segunda().falha());
        var concluida = execucao.segunda().resposta();
        assertEquals(StatusSolicitacao.CONCLUIDA, concluida.status());
        assertEquals(execucao.primeira().envio(), concluida.envio());
        assertEquals(cenario.pedido().eventos().size() + 2, concluida.eventos().size());
        assertFalse(concluida.recebimento().realizadoEm().isBefore(execucao.liberadoEm()));
        verificarConfirmado(cenario, concluida, destinoAplicado(cenario));
    }

    @Test
    void envioAposAprovacaoConcorrenteReleDecisaoAnteriorNoDtoCompleto() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL);
        var execucao = executarComOrdem(cenario, atual -> servico.aprovar(atual.pedido().id(),
                atual.pedido().aprovacoes().getLast().id(), usuario(atual.dados().origem().gestorId())), this::enviar, false);
        assertNull(execucao.segunda().falha());
        assertEquals(execucao.primeira().aprovacoes(), execucao.segunda().resposta().aprovacoes());
        assertEquals(StatusSolicitacao.EM_TRANSITO, execucao.segunda().resposta().status());
        verificarConfirmado(cenario, execucao.segunda().resposta(), cenario.cadastroBem());
    }

    @Test
    void envioRevalidaAtorDepoisDaEsperaPelaSolicitacao() throws Exception {
        verificarRevalidacao(false, false);
    }

    @Test
    void recebimentoRevalidaAtorDepoisDaEsperaPeloBem() throws Exception {
        verificarRevalidacao(true, true);
    }

    @Test
    void recebimentoRevalidaAtorDepoisDeBloquearBemEAguardarSolicitacao() throws Exception {
        verificarRevalidacao(true, false);
    }

    private void verificarRevalidacao(boolean recebimento, boolean bloquearBem) throws Exception {
        for (String alteracao : List.of("INATIVO", "OUTRO_SETOR", "ADMIN_OUTRO_SETOR", "PERFIL")) {
            Cenario cenario = prepararConfirmado(recebimento ? StatusSolicitacao.EM_TRANSITO : StatusSolicitacao.APROVADA);
            var execucao = executarComOrdem(cenario, atual -> {
                if (bloquearBem) {
                    bens.buscarPorIdParaAtualizacao(atual.dados().origem().bemId()).orElseThrow();
                } else {
                    solicitacoes.buscarPorIdParaAtualizacao(atual.pedido().id()).orElseThrow();
                }
                Long atorId = recebimento ? atual.dados().destino().gestorId() : atual.dados().origem().gestorId();
                Long outroSetor = recebimento ? atual.dados().origem().setorId() : atual.dados().destino().setorId();
                switch (alteracao) {
                    case "INATIVO" -> banco.update("UPDATE usuario SET ativo = false WHERE id = ?", atorId);
                    case "OUTRO_SETOR" -> banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", outroSetor, atorId);
                    case "ADMIN_OUTRO_SETOR" -> banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR', setor_id = ? WHERE id = ?", outroSetor, atorId);
                    case "PERFIL" -> banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", atorId);
                    default -> throw new IllegalArgumentException(alteracao);
                }
                return atual.pedido();
            }, recebimento ? this::receber : this::enviar, false);
            if (alteracao.equals("PERFIL")) {
                assertNull(execucao.segunda().falha());
                assertFalse(execucao.segunda().resposta().eventos().getLast().ocorridoEm().isBefore(execucao.liberadoEm()));
                verificarConfirmado(cenario, execucao.segunda().resposta(), recebimento ? destinoAplicado(cenario) : cenario.cadastroBem());
            } else {
                assertEquals(alteracao.equals("INATIVO") ? NAO_AUTENTICADO : ACAO_NAO_PERMITIDA,
                        assertInstanceOf(ExcecaoExecucaoSolicitacao.class, execucao.segunda().falha()).getMotivo());
                verificarConfirmado(cenario, cenario.pedido(), cenario.cadastroBem());
            }
        }
    }

    @Test
    void recebimentoPreservaAtributosExternosConfirmadosEnquantoAguardaOBem() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.EM_TRANSITO);
        var execucao = executarComOrdem(cenario, atual -> {
            bens.buscarPorIdParaAtualizacao(atual.dados().origem().bemId()).orElseThrow();
            banco.update("""
                    UPDATE bem_patrimonial SET ativo = false, descricao = 'Cadastro externo sintético',
                        plaqueta = 'EXTERNA', setor_id = ?, responsavel_id = ? WHERE id = ?
                    """, atual.dados().destino().setorId(), atual.dados().solicitanteId(), atual.dados().origem().bemId());
            return atual.pedido();
        }, this::receber, false);
        assertNull(execucao.segunda().falha());
        Map<String, Object> esperado = destinoAplicado(cenario);
        esperado.put("ativo", false);
        esperado.put("descricao", "Cadastro externo sintético");
        esperado.put("plaqueta", "EXTERNA");
        verificarConfirmado(cenario, execucao.segunda().resposta(), esperado);
    }

    @Test
    void recebimentoECriacaoSerializamNoBemENovoPedidoCopiaNovaOrigem() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.EM_TRANSITO);
        var execucao = executarComOrdem(cenario, this::receber, this::criarRetorno, false);
        assertNull(execucao.segunda().falha());
        var nova = execucao.segunda().resposta();
        assertNotEquals(cenario.pedido().id(), nova.id());
        assertEquals(execucao.primeira().destino(), nova.origem());
        assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, nova.status());
        verificarConfirmado(cenario, execucao.primeira(), destinoAplicado(cenario));
    }

    @Test
    void rollbackDoRecebimentoMantemBemPedidoAtivoEBloqueiaCriacaoConcorrente() throws Exception {
        Cenario cenario = prepararConfirmado(StatusSolicitacao.EM_TRANSITO);
        var execucao = executarComOrdem(cenario, this::receber, this::criarRetorno, true);
        assertEquals(MotivoFalhaCriacaoSolicitacao.BEM_COM_SOLICITACAO_EM_ANDAMENTO,
                assertInstanceOf(ExcecaoCriacaoSolicitacao.class, execucao.segunda().falha()).getMotivo());
        verificarConfirmado(cenario, cenario.pedido(), cenario.cadastroBem());
    }

    private Execucao executarComOrdem(Cenario cenario, Function<Cenario, SolicitacaoResponse> primeira,
            Function<Cenario, SolicitacaoResponse> segunda, boolean reverterPrimeira) throws Exception {
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
                if (reverterPrimeira) {
                    situacao.setRollbackOnly();
                }
                return resposta;
            }));
            assertTrue(alterado.await(4, TimeUnit.SECONDS));
            concorrente = executor.submit(() -> {
                try {
                    var resposta = novaTransacao().execute(situacao -> {
                        limitarEspera();
                        // Força dados antigos no contexto JPA antes da espera real pelos locks.
                        solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus();
                        aprovacoes.buscarPorSolicitacao(cenario.pedido().id()).forEach(etapa -> etapa.getDecisao());
                        bens.findById(cenario.dados().origem().bemId()).orElseThrow().getCodigo();
                        usuario(cenario.dados().origem().gestorId()).getPerfil();
                        usuario(cenario.dados().destino().gestorId()).getPerfil();
                        assertTrue(gerenciador.isJoinedToTransaction());
                        conexao.set(banco.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        conexaoPronta.countDown();
                        return segunda.apply(cenario);
                    });
                    return new Resultado(resposta, null);
                } catch (RuntimeException falha) {
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

    private SolicitacaoResponse enviar(Cenario cenario) {
        return servico.enviar(cenario.pedido().id(), usuario(cenario.dados().origem().gestorId()));
    }

    private SolicitacaoResponse receber(Cenario cenario) {
        return servico.receber(cenario.pedido().id(), usuario(cenario.dados().destino().gestorId()));
    }

    private SolicitacaoResponse cancelar(Cenario cenario) {
        return servico.cancelar(cenario.pedido().id(), "Cancelamento concorrente", usuario(cenario.dados().solicitanteId()));
    }

    private SolicitacaoResponse criarRetorno(Cenario cenario) {
        var dados = cenario.dados();
        return servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                dados.origem().localizacaoId(), dados.origem().gestorId(), "Retorno sintético"), usuario(dados.solicitanteId()));
    }

    private void verificarConfirmado(Cenario cenario, SolicitacaoResponse esperado, Map<String, Object> bemEsperado) {
        novaTransacao().executeWithoutResult(situacao -> {
            Long id = cenario.pedido().id();
            var atual = mapeador.mapear(solicitacoes.findById(id).orElseThrow(), aprovacoes.buscarPorSolicitacao(id),
                    eventos.buscarPorSolicitacao(id), usuario(cenario.dados().solicitanteId()));
            assertEquals(esperado.status(), atual.status());
            assertEquals(esperado.envio(), atual.envio());
            assertEquals(esperado.recebimento(), atual.recebimento());
            assertEquals(esperado.aprovacoes(), atual.aprovacoes());
            assertEquals(esperado.eventos(), atual.eventos());
            assertEquals(cenario.pedido().origem(), atual.origem());
            assertEquals(cenario.pedido().destino(), atual.destino());
            assertEquals(cenario.pedido().solicitante(), atual.solicitante());
            assertEquals(bemEsperado, cadastroBem(cenario.dados()));
        });
    }

    private Map<String, Object> destinoAplicado(Cenario cenario) {
        Map<String, Object> esperado = new LinkedHashMap<>(cenario.cadastroBem());
        esperado.put("setor_id", cenario.pedido().destino().setor().id());
        esperado.put("localizacao_id", cenario.pedido().destino().localizacao().id());
        esperado.put("responsavel_id", cenario.pedido().destino().responsavel().id());
        return esperado;
    }

    private Cenario prepararConfirmado(StatusSolicitacao estado) {
        // Fixtures confirmadas permanecem em simpi_test, sem limpeza destrutiva.
        return novaTransacao().execute(situacao -> {
            limitarEspera();
            var dados = DadosSolicitacoesTeste.preparar(banco);
            var pedido = servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                    dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética"), usuario(dados.solicitanteId()));
            pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
            if (estado != StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL) {
                pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
            }
            if (estado == StatusSolicitacao.EM_TRANSITO) {
                pedido = servico.enviar(pedido.id(), usuario(dados.origem().gestorId()));
            }
            assertEquals(estado, pedido.status());
            return new Cenario(dados, pedido, cadastroBem(dados));
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
        fail("A operação deve estar aguardando um dos locks em outra conexão");
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

    private record Cenario(DadosSolicitacoesTeste dados, SolicitacaoResponse pedido, Map<String, Object> cadastroBem) { }
    private record Resultado(SolicitacaoResponse resposta, RuntimeException falha) { }
    private record Execucao(SolicitacaoResponse primeira, Resultado segunda, Instant liberadoEm) { }
}
