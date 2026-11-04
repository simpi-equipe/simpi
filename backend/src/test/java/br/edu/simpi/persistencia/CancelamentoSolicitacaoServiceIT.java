package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import br.edu.simpi.solicitacao.service.SolicitacaoMapper;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class CancelamentoSolicitacaoServiceIT {
    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final PlatformTransactionManager transacoes;
    private final SolicitacaoService servico;
    private final UsuarioRepository usuarios;
    private final BemPatrimonialRepository bens;
    private final SetorRepository setores;
    private final LocalizacaoRepository localizacoes;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final NumeroSolicitacaoService numeros;
    private final SolicitacaoMapper mapeador;

    @Autowired
    CancelamentoSolicitacaoServiceIT(JdbcTemplate banco, EntityManager gerenciador,
            PlatformTransactionManager transacoes, SolicitacaoService servico,
            UsuarioRepository usuarios, BemPatrimonialRepository bens, SetorRepository setores,
            LocalizacaoRepository localizacoes, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos,
            NumeroSolicitacaoService numeros, SolicitacaoMapper mapeador) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.transacoes = transacoes;
        this.servico = servico;
        this.usuarios = usuarios;
        this.bens = bens;
        this.setores = setores;
        this.localizacoes = localizacoes;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.numeros = numeros;
        this.mapeador = mapeador;
    }

    @Test
    void cancelaNosTresEstadosPreservandoBemReferenciasEAprovacoes() {
        for (StatusSolicitacao estado : estadosPermitidos()) {
            emTransacao(estado, cenario -> {
                Usuario ator = usuario(cenario.dados().solicitanteId());
                assertTrue(respostaAtual(cenario, ator).acoesDisponiveis().contains("CANCELAR"));
                var resposta = servico.cancelar(cenario.pedido().id(), " \tNecessidade atendida\n ", ator);
                verificarCancelamento(cenario, resposta);
                assertEquals("Necessidade atendida", resposta.eventos().getLast().descricao());
                gerenciador.clear();
                assertEquals(StatusSolicitacao.CANCELADA,
                        solicitacoes.findById(resposta.id()).orElseThrow().getStatus());
                assertEquals(cenario.pedido().aprovacoes(), respostaAtual(cenario, usuario(ator.getId())).aprovacoes());
            });
        }
    }

    @Test
    void cancelaPedidoAbertoPorGestorSemCriarOuDecidirAprovacoes() {
        emDados(dados -> {
            Usuario gestor = usuario(dados.origem().gestorId());
            var pedido = servico.criar(entrada(dados), gestor);
            var cenario = new Cenario(dados, pedido, cadastroBem(dados));
            assertEquals(List.of("APROVAR", "RECUSAR", "CANCELAR"), pedido.acoesDisponiveis());
            verificarCancelamento(cenario, servico.cancelar(pedido.id(), "Necessidade atendida", gestor));
        });
    }

    @Test
    void aceitaMudancaParaCadaSetorParticipantePreservandoSetorSolicitanteHistorico() {
        for (String destino : List.of("SOLICITANTE", "ORIGEM", "DESTINO")) {
            emDados(dados -> {
                Long setorSolicitante = prepararTerceiroSetor().setorId();
                banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", setorSolicitante, dados.solicitanteId());
                Usuario ator = usuario(dados.solicitanteId());
                var pedido = servico.criar(entrada(dados), ator);
                Long setorAtual = switch (destino) {
                    case "ORIGEM" -> dados.origem().setorId();
                    case "DESTINO" -> dados.destino().setorId();
                    default -> setorSolicitante;
                };
                banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", setorAtual, ator.getId());
                var cenario = new Cenario(dados, pedido, cadastroBem(dados));
                var resposta = servico.cancelar(pedido.id(), "Necessidade atendida", ator);
                verificarCancelamento(cenario, resposta);
                assertEquals(setorAtual, ator.getSetor().getId());
                assertEquals(setorSolicitante, resposta.solicitante().setor().id());
            });
        }
    }

    @Test
    void mudancaParaSetorParticipanteMantemAcaoCancelarCoerenteComService() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
            Usuario ator = usuario(cenario.dados().solicitanteId());
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", cenario.dados().destino().setorId(), ator.getId());
            gerenciador.refresh(ator);
            assertTrue(respostaAtual(cenario, ator).acoesDisponiveis().contains("CANCELAR"));
            verificarCancelamento(cenario, servico.cancelar(cenario.pedido().id(), "Necessidade atendida", ator));
        });
    }

    @Test
    void perdaDeVisibilidadeImpedeCancelamentoAntesDeExporEstado() {
        for (StatusSolicitacao estado : List.of(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, StatusSolicitacao.CANCELADA)) {
            emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
                Usuario ator = usuario(cenario.dados().solicitanteId());
                Long terceiro = prepararTerceiroSetor().setorId();
                banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", terceiro, ator.getId());
                banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", estado.name(), cenario.pedido().id());
                verificarFalha(ACAO_NAO_PERMITIDA, () -> servico.cancelar(cenario.pedido().id(), "Motivo", ator));
                assertFalse(respostaAtual(cenario, ator).acoesDisponiveis().contains("CANCELAR"));
                assertEquals(cenario.pedido().eventos().size(), eventos.buscarPorSolicitacao(cenario.pedido().id()).size());
            });
        }
    }

    @Test
    void perfilAtualNaoSubstituiAutoriaEPreservaExcecaoDeVisibilidadeAdministrativa() {
        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
                Usuario ator = usuario(cenario.dados().solicitanteId());
                banco.update("UPDATE usuario SET perfil = ? WHERE id = ?", perfil.name(), ator.getId());
                if (perfil == PerfilUsuario.ADMINISTRADOR) {
                    banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", prepararTerceiroSetor().setorId(), ator.getId());
                }
                gerenciador.refresh(ator);
                assertTrue(respostaAtual(cenario, ator).acoesDisponiveis().contains("CANCELAR"));
                verificarCancelamento(cenario, servico.cancelar(cenario.pedido().id(), "Necessidade atendida", ator));
            });
        }
        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
                Usuario outro = usuario(cenario.dados().destino().gestorId());
                banco.update("UPDATE usuario SET perfil = ? WHERE id = ?", perfil.name(), outro.getId());
                verificarFalha(ACAO_NAO_PERMITIDA, () -> servico.cancelar(cenario.pedido().id(), "Motivo", outro));
                assertFalse(respostaAtual(cenario, outro).acoesDisponiveis().contains("CANCELAR"));
                verificarSemAlteracoes(cenario);
            });
        }
    }

    @Test
    void rejeitaAtorAusenteSemIdInexistenteOuInativoMesmoPreviamenteCarregado() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
            verificarFalha(NAO_AUTENTICADO, () -> servico.cancelar(cenario.pedido().id(), "Motivo", null));
            Usuario inexistente = new Usuario("Ator sintético", "ator@example.invalid",
                    DadosPatrimoniaisTeste.HASH_SINTETICO, PerfilUsuario.COMUM,
                    setores.findById(cenario.dados().origem().setorId()).orElseThrow());
            verificarFalha(NAO_AUTENTICADO, () -> servico.cancelar(cenario.pedido().id(), "Motivo", inexistente));
            ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
            verificarFalha(NAO_AUTENTICADO, () -> servico.cancelar(cenario.pedido().id(), "Motivo", inexistente));
            Usuario ator = usuario(cenario.dados().solicitanteId());
            banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
            verificarFalha(NAO_AUTENTICADO, () -> servico.cancelar(cenario.pedido().id(), "Motivo", ator));
            assertFalse(respostaAtual(cenario, ator).acoesDisponiveis().contains("CANCELAR"));
            verificarSemAlteracoes(cenario);
        });
    }

    @Test
    void validaMotivoEIdentificadorEmChamadaDiretaSemGravar() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
            Usuario ator = usuario(cenario.dados().solicitanteId());
            for (String motivo : new String[] {null, "", " ", "\t\n\r "}) {
                var falha = verificarFalha(DADOS_INVALIDOS, () -> servico.cancelar(cenario.pedido().id(), motivo, ator));
                assertTrue(falha.getErros().containsKey("motivo"));
            }
            for (Long id : new Long[] {null, 0L, -1L}) {
                var falha = verificarFalha(DADOS_INVALIDOS, () -> servico.cancelar(id, "Motivo", ator));
                assertTrue(falha.getErros().containsKey("solicitacaoId"));
            }
            verificarFalha(SOLICITACAO_NAO_ENCONTRADA, () -> servico.cancelar(Long.MAX_VALUE, "Motivo", ator));
            verificarSemAlteracoes(cenario);
        });
    }

    @Test
    void rejeitaEstadosFinaisTransitoEEnvioRegistrado() {
        for (StatusSolicitacao estado : List.of(StatusSolicitacao.EM_TRANSITO, StatusSolicitacao.CONCLUIDA,
                StatusSolicitacao.RECUSADA, StatusSolicitacao.CANCELADA)) {
            emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
                banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", estado.name(), cenario.pedido().id());
                verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.cancelar(cenario.pedido().id(), "Motivo",
                        usuario(cenario.dados().solicitanteId())));
                assertFalse(respostaAtual(cenario, usuario(cenario.dados().solicitanteId())).acoesDisponiveis().contains("CANCELAR"));
                assertEquals(estado, solicitacoes.findById(cenario.pedido().id()).orElseThrow().getStatus());
                assertEquals(cenario.pedido().eventos().size(), eventos.buscarPorSolicitacao(cenario.pedido().id()).size());
            });
        }
        emTransacao(StatusSolicitacao.APROVADA, cenario -> {
            banco.update("UPDATE solicitacao SET enviada_em = CURRENT_TIMESTAMP, enviada_por_id = ? WHERE id = ?",
                    cenario.dados().origem().gestorId(), cenario.pedido().id());
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.cancelar(cenario.pedido().id(), "Motivo",
                    usuario(cenario.dados().solicitanteId())));
            assertFalse(respostaAtual(cenario, usuario(cenario.dados().solicitanteId())).acoesDisponiveis().contains("CANCELAR"));
        });
    }

    @Test
    void repeticaoNaoCriaNovoEventoNemAlteraAprovacoes() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, cenario -> {
            Usuario ator = usuario(cenario.dados().solicitanteId());
            var cancelada = servico.cancelar(cenario.pedido().id(), "Necessidade atendida", ator);
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.cancelar(cancelada.id(), "Outro motivo", ator));
            assertEquals(cancelada, respostaAtual(cenario, ator));
            verificarFalha(ACAO_NAO_PERMITIDA, () -> servico.cancelar(cancelada.id(), "Motivo",
                    usuario(cenario.dados().origem().gestorId())));
        });
    }

    @Test
    void preservaReferenciasHistoricasMesmoAposMudancasCadastrais() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
            var dados = cenario.dados();
            banco.update("UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ? WHERE id = ?",
                    dados.destino().setorId(), dados.destino().localizacaoId(), dados.destino().gestorId(), dados.origem().bemId());
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), dados.solicitanteId());
            gerenciador.clear();
            var alterado = new Cenario(dados, cenario.pedido(), cadastroBem(dados));
            verificarCancelamento(alterado, servico.cancelar(cenario.pedido().id(), "Necessidade atendida", usuario(dados.solicitanteId())));
        });
    }

    @Test
    void capturaRelogioUmaVezAposBloqueioComPrecisaoDeMicrossegundos() {
        emTransacao(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, cenario -> {
            gerenciador.clear();
            var pedido = solicitacoes.findById(cenario.pedido().id()).orElseThrow();
            assertEquals(LockModeType.NONE, gerenciador.getLockMode(pedido));
            AtomicInteger leituras = new AtomicInteger();
            Clock relogio = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zona) { return this; }
                @Override public Instant instant() {
                    assertEquals(LockModeType.PESSIMISTIC_WRITE, gerenciador.getLockMode(pedido));
                    leituras.incrementAndGet();
                    return Instant.parse("2027-01-01T01:02:03.123456789Z");
                }
            };
            var resposta = servicoCom(eventos, relogio).cancelar(pedido.getId(), "Necessidade atendida",
                    usuario(cenario.dados().solicitanteId()));
            assertEquals(1, leituras.get());
            assertEquals(Instant.parse("2027-01-01T01:02:03.123456Z"), resposta.eventos().getLast().ocorridoEm());
            verificarCancelamento(cenario, resposta);
        });
    }

    @Test
    void cancelamentoConfirmadoLiberaBemParaNovoPedidoSemAlterarAnterior() {
        Cenario cenario = prepararConfirmado();
        Usuario ator = novaTransacao().execute(situacao -> usuario(cenario.dados().solicitanteId()));
        var cancelada = servico.cancelar(cenario.pedido().id(), "Necessidade atendida", ator);
        var nova = servico.criar(entrada(cenario.dados()), ator);
        assertNotEquals(cancelada.id(), nova.id());
        novaTransacao().executeWithoutResult(situacao -> {
            assertEquals(StatusSolicitacao.CANCELADA, solicitacoes.findById(cancelada.id()).orElseThrow().getStatus());
            assertEquals(cancelada, respostaAtual(cenario, usuario(ator.getId())));
            assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
        });
    }

    @Test
    void falhasDeEventoRevertemCancelamentoSemContinuarNaTransacaoComprometida() {
        for (boolean falhaReal : List.of(false, true)) {
            Cenario cenario = prepararConfirmado();
            Usuario ator = novaTransacao().execute(situacao -> usuario(cenario.dados().solicitanteId()));
            RuntimeException falhaSintetica = new IllegalStateException("Falha sintética após flush");
            EventoSolicitacaoRepository eventosComFalha = EventoSolicitacaoRepository.class.cast(Proxy.newProxyInstance(
                    EventoSolicitacaoRepository.class.getClassLoader(), new Class<?>[] {EventoSolicitacaoRepository.class},
                    (procurador, metodo, argumentos) -> {
                        if (metodo.getName().equals("save")) {
                            solicitacoes.flush();
                            if (falhaReal) {
                                banco.update("""
                                        INSERT INTO evento_solicitacao (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                                        VALUES (?, ?, 'CANCELAMENTO', 'CANCELADA', CURRENT_TIMESTAMP)
                                        """, cenario.pedido().id(), Long.MAX_VALUE);
                                fail("A FK deve rejeitar o autor inexistente");
                            }
                            eventos.save((EventoSolicitacao) argumentos[0]);
                            solicitacoes.flush();
                            throw falhaSintetica;
                        }
                        fail("Não consultar nem gravar após falha de persistência");
                        return null;
                    }));
            var comFalha = servicoCom(eventosComFalha, Clock.systemUTC());
            if (falhaReal) {
                assertThrows(DataIntegrityViolationException.class, () -> comFalha.cancelar(cenario.pedido().id(), "Motivo", ator));
            } else {
                assertSame(falhaSintetica, assertThrows(IllegalStateException.class,
                        () -> comFalha.cancelar(cenario.pedido().id(), "Motivo", ator)));
            }
            novaTransacao().executeWithoutResult(situacao -> verificarSemAlteracoes(cenario));
            var conflito = assertThrows(br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao.class,
                    () -> servico.criar(entrada(cenario.dados()), ator));
            assertEquals(br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao.BEM_COM_SOLICITACAO_EM_ANDAMENTO,
                    conflito.getMotivo());
            assertEquals(StatusSolicitacao.CANCELADA, servico.cancelar(cenario.pedido().id(), "Motivo", ator).status());
        }
    }

    private void verificarCancelamento(Cenario cenario, SolicitacaoResponse resposta) {
        assertEquals(StatusSolicitacao.CANCELADA, resposta.status());
        assertEquals(cenario.pedido().id(), resposta.id());
        assertEquals(cenario.pedido().numero(), resposta.numero());
        assertEquals(cenario.pedido().criadaEm(), resposta.criadaEm());
        assertEquals(cenario.pedido().justificativa(), resposta.justificativa());
        assertEquals(cenario.pedido().bem(), resposta.bem());
        assertEquals(cenario.pedido().solicitante(), resposta.solicitante());
        assertEquals(cenario.pedido().origem(), resposta.origem());
        assertEquals(cenario.pedido().destino(), resposta.destino());
        assertEquals(cenario.pedido().aprovacoes(), resposta.aprovacoes());
        assertEquals(cenario.pedido().eventos().size() + 1, resposta.eventos().size());
        assertEquals(cenario.pedido().eventos(), resposta.eventos().subList(0, cenario.pedido().eventos().size()));
        var evento = resposta.eventos().getLast();
        assertEquals(TipoEventoSolicitacao.CANCELAMENTO, evento.tipo());
        assertEquals(cenario.pedido().solicitante().id(), evento.autor().id());
        assertEquals(cenario.pedido().status(), evento.statusAnterior());
        assertEquals(StatusSolicitacao.CANCELADA, evento.statusNovo());
        assertNotNull(evento.ocorridoEm());
        assertEquals(List.of(), resposta.acoesDisponiveis());
        assertNull(resposta.envio());
        assertNull(resposta.recebimento());
        assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
    }

    private void verificarSemAlteracoes(Cenario cenario) {
        gerenciador.clear();
        var atual = respostaAtual(cenario, usuario(cenario.pedido().solicitante().id()));
        assertEquals(cenario.pedido().status(), atual.status());
        assertEquals(cenario.pedido().aprovacoes(), atual.aprovacoes());
        assertEquals(cenario.pedido().eventos(), atual.eventos());
        assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
    }

    private SolicitacaoResponse respostaAtual(Cenario cenario, Usuario ator) {
        Long id = cenario.pedido().id();
        return mapeador.mapear(solicitacoes.findById(id).orElseThrow(), aprovacoes.buscarPorSolicitacao(id),
                eventos.buscarPorSolicitacao(id), ator);
    }

    private Map<String, Object> cadastroBem(DadosSolicitacoesTeste dados) {
        return banco.queryForMap("SELECT * FROM bem_patrimonial WHERE id = ?", dados.origem().bemId());
    }

    private ExcecaoCancelamentoSolicitacao verificarFalha(MotivoFalhaCancelamentoSolicitacao motivo, Runnable operacao) {
        var falha = assertThrows(ExcecaoCancelamentoSolicitacao.class, operacao::run);
        assertEquals(motivo, falha.getMotivo());
        return falha;
    }

    private DadosPatrimoniaisTeste prepararTerceiroSetor() {
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var dados = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        return dados;
    }

    private void emTransacao(StatusSolicitacao estado, Consumer<Cenario> verificacao) {
        emDados(dados -> verificacao.accept(criarCenario(dados, estado)));
    }

    private void emDados(Consumer<DadosSolicitacoesTeste> verificacao) {
        novaTransacao().executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            limitarEspera();
            verificacao.accept(DadosSolicitacoesTeste.preparar(banco));
        });
    }

    private Cenario prepararConfirmado() {
        // Fixtures confirmadas permanecem em simpi_test, sem limpeza destrutiva.
        return novaTransacao().execute(situacao -> criarCenario(DadosSolicitacoesTeste.preparar(banco),
                StatusSolicitacao.AGUARDANDO_GESTOR_AREA));
    }

    private Cenario criarCenario(DadosSolicitacoesTeste dados, StatusSolicitacao estado) {
        var pedido = servico.criar(entrada(dados), usuario(dados.solicitanteId()));
        if (estado != StatusSolicitacao.AGUARDANDO_GESTOR_AREA) {
            pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        }
        if (estado == StatusSolicitacao.APROVADA) {
            pedido = servico.aprovar(pedido.id(), pedido.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        }
        assertEquals(estado, pedido.status());
        return new Cenario(dados, pedido, cadastroBem(dados));
    }

    private List<StatusSolicitacao> estadosPermitidos() {
        return List.of(StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, StatusSolicitacao.APROVADA);
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

    private SolicitacaoService servicoCom(EventoSolicitacaoRepository historico, Clock relogio) {
        var alvo = new SolicitacaoService(usuarios, bens, setores, localizacoes, solicitacoes,
                aprovacoes, historico, numeros, mapeador, gerenciador, relogio);
        var fabrica = new ProxyFactory(alvo);
        fabrica.addAdvice(new TransactionInterceptor(transacoes, new AnnotationTransactionAttributeSource()));
        return (SolicitacaoService) fabrica.getProxy();
    }

    private record Cenario(DadosSolicitacoesTeste dados, SolicitacaoResponse pedido, Map<String, Object> cadastroBem) { }
}
