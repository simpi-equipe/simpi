package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoExecucaoSolicitacao;
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
import java.util.LinkedHashMap;
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

import static br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class EnvioRecebimentoSolicitacaoServiceIT {
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
    EnvioRecebimentoSolicitacaoServiceIT(JdbcTemplate banco, EntityManager gerenciador,
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
    void envioRegistraAtorInstanteEEventoSemAlterarBemOuHistorico() {
        emTransacao(false, cenario -> {
            Usuario ator = usuario(cenario.dados().solicitanteId());
            assertTrue(respostaAtual(cenario, ator).acoesDisponiveis().contains("ENVIAR"));
            var resposta = servico.enviar(cenario.pedido().id(), ator);
            assertEquals(StatusSolicitacao.EM_TRANSITO, resposta.status());
            assertEquals(ator.getId(), resposta.envio().realizadoPor().id());
            assertEquals(resposta.envio().realizadoEm(), resposta.eventos().getLast().ocorridoEm());
            assertNull(resposta.recebimento());
            verificarHistorico(cenario, resposta, TipoEventoSolicitacao.ENVIO);
            assertFalse(resposta.acoesDisponiveis().contains("ENVIAR"));
            assertFalse(resposta.acoesDisponiveis().contains("CANCELAR"));
            assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
        });
    }

    @Test
    void recebimentoAplicaDestinoPreservadoSemSubstituirResponsavelPeloRecebedor() {
        emTransacao(true, cenario -> {
            Usuario recebedor = inserirUsuario(PerfilUsuario.COMUM, cenario.dados().destino().setorId());
            assertNotEquals(cenario.pedido().destino().responsavel().id(), recebedor.getId());
            assertTrue(respostaAtual(cenario, recebedor).acoesDisponiveis().contains("RECEBER"));
            var resposta = servico.receber(cenario.pedido().id(), recebedor);
            assertEquals(StatusSolicitacao.CONCLUIDA, resposta.status());
            assertEquals(recebedor.getId(), resposta.recebimento().realizadoPor().id());
            assertEquals(resposta.recebimento().realizadoEm(), resposta.eventos().getLast().ocorridoEm());
            assertEquals(cenario.pedido().envio(), resposta.envio());
            assertEquals(List.of(), resposta.acoesDisponiveis());
            verificarHistorico(cenario, resposta, TipoEventoSolicitacao.RECEBIMENTO);
            verificarDestinoAplicado(cenario);
        });
    }

    @Test
    void qualquerPerfilAtivoDoSetorExigidoPodeExecutar() {
        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            emTransacao(false, cenario -> {
                Usuario expedidor = inserirUsuario(perfil, cenario.dados().origem().setorId());
                Usuario recebedor = inserirUsuario(perfil, cenario.dados().destino().setorId());
                assertTrue(respostaAtual(cenario, expedidor).acoesDisponiveis().contains("ENVIAR"));
                var enviada = servico.enviar(cenario.pedido().id(), expedidor);
                assertEquals(expedidor.getId(), enviada.envio().realizadoPor().id());
                assertTrue(respostaAtual(cenario, recebedor).acoesDisponiveis().contains("RECEBER"));
                var recebida = servico.receber(cenario.pedido().id(), recebedor);
                assertEquals(recebedor.getId(), recebida.recebimento().realizadoPor().id());
                verificarDestinoAplicado(cenario);
            });
        }
    }

    @Test
    void mesmoSetorPodeEnviarEReceberEmDuasAcoesExplicitas() {
        emDados(dados -> {
            var entrada = new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                    dados.destino().localizacaoId(), dados.origem().gestorId(), "Mudança de localização");
            var pedido = aprovarPedido(servico.criar(entrada, usuario(dados.solicitanteId())), dados);
            Usuario ator = usuario(dados.solicitanteId());
            var enviada = servico.enviar(pedido.id(), ator);
            assertNull(enviada.recebimento());
            assertEquals(List.of("RECEBER"), enviada.acoesDisponiveis());
            var recebida = servico.receber(pedido.id(), ator);
            assertEquals(ator.getId(), recebida.recebimento().realizadoPor().id());
            assertEquals(pedido.destino().responsavel().id(), cadastroBem(dados).get("responsavel_id"));
            assertEquals(pedido.destino().localizacao().id(), cadastroBem(dados).get("localizacao_id"));
        });
    }

    @Test
    void preservaPoliticaDoPrototipoMesmoComCadastrosAlteradosOuInativados() {
        for (String alteracao : List.of("BEM_INATIVO", "ORIGEM_INATIVA", "DESTINO_INATIVO",
                "LOCALIZACAO_INATIVA", "RESPONSAVEL_INATIVO", "RESPONSAVEL_MUDOU_SETOR", "ORIGEM_DIVERGENTE")) {
            emTransacao(false, cenario -> {
                var dados = cenario.dados();
                Usuario recebedor = inserirUsuario(PerfilUsuario.COMUM, dados.destino().setorId());
                switch (alteracao) {
                    case "BEM_INATIVO" -> banco.update("UPDATE bem_patrimonial SET ativo = false WHERE id = ?", dados.origem().bemId());
                    case "ORIGEM_INATIVA" -> banco.update("UPDATE setor SET ativo = false WHERE id = ?", dados.origem().setorId());
                    case "DESTINO_INATIVO" -> banco.update("UPDATE setor SET ativo = false WHERE id = ?", dados.destino().setorId());
                    case "LOCALIZACAO_INATIVA" -> banco.update("UPDATE localizacao SET ativo = false WHERE id = ?", dados.destino().localizacaoId());
                    case "RESPONSAVEL_INATIVO" -> banco.update("UPDATE usuario SET ativo = false WHERE id = ?", dados.destino().gestorId());
                    case "RESPONSAVEL_MUDOU_SETOR" -> banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?",
                            dados.origem().setorId(), dados.destino().gestorId());
                    case "ORIGEM_DIVERGENTE" -> banco.update("""
                            UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ? WHERE id = ?
                            """, dados.destino().setorId(), dados.origem().localizacaoId(), dados.solicitanteId(), dados.origem().bemId());
                    default -> throw new IllegalArgumentException(alteracao);
                }
                // Objetos antigos continuam no contexto: refresh do bem deve preservar seus atributos atuais.
                Map<String, Object> cadastroAtual = cadastroBem(dados);
                var atual = new Cenario(dados, cenario.pedido(), cadastroAtual);
                var enviada = servico.enviar(cenario.pedido().id(), usuario(dados.origem().gestorId()));
                assertEquals(cadastroAtual, cadastroBem(dados));
                var recebida = servico.receber(enviada.id(), recebedor);
                verificarDestinoAplicado(atual);
                assertEquals(cenario.pedido().origem(), recebida.origem());
                assertEquals(cenario.pedido().destino(), recebida.destino());
                assertEquals(cenario.pedido().aprovacoes(), recebida.aprovacoes());
                if (alteracao.equals("RESPONSAVEL_MUDOU_SETOR")) {
                    assertEquals(dados.origem().setorId(), banco.queryForObject(
                            "SELECT setor_id FROM usuario WHERE id = ?", Long.class, dados.destino().gestorId()));
                }
                if (alteracao.equals("RESPONSAVEL_INATIVO")) {
                    assertFalse(banco.queryForObject("SELECT ativo FROM usuario WHERE id = ?", Boolean.class, dados.destino().gestorId()));
                }
                if (alteracao.equals("DESTINO_INATIVO")) {
                    assertFalse(banco.queryForObject("SELECT ativo FROM setor WHERE id = ?", Boolean.class, dados.destino().setorId()));
                }
                if (alteracao.equals("LOCALIZACAO_INATIVA")) {
                    assertFalse(banco.queryForObject("SELECT ativo FROM localizacao WHERE id = ?", Boolean.class, dados.destino().localizacaoId()));
                }
            });
        }
    }

    @Test
    void rejeitaAtorAusenteSemIdInexistenteOuInativoEmAmbasOperacoes() {
        for (boolean recebimento : List.of(false, true)) {
            emTransacao(recebimento, cenario -> {
                verificarFalha(NAO_AUTENTICADO, () -> executar(recebimento, cenario.pedido().id(), null));
                Usuario semId = new Usuario("Ator sintético", "ator@example.invalid", DadosPatrimoniaisTeste.HASH_SINTETICO,
                        PerfilUsuario.COMUM, setores.findById(cenario.dados().origem().setorId()).orElseThrow());
                verificarFalha(NAO_AUTENTICADO, () -> executar(recebimento, cenario.pedido().id(), semId));
                ReflectionTestUtils.setField(semId, "id", Long.MAX_VALUE);
                verificarFalha(NAO_AUTENTICADO, () -> executar(recebimento, cenario.pedido().id(), semId));
                Usuario ator = ator(cenario, recebimento);
                banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
                verificarFalha(NAO_AUTENTICADO, () -> executar(recebimento, cenario.pedido().id(), ator));
                assertFalse(respostaAtual(cenario, ator).acoesDisponiveis().contains(recebimento ? "RECEBER" : "ENVIAR"));
                verificarSemAlteracoes(cenario);
            });
        }
    }

    @Test
    void perfilESetorSaoRecarregadosEAutorizacaoPrecedeEstado() {
        for (boolean recebimento : List.of(false, true)) {
            for (PerfilUsuario perfil : PerfilUsuario.values()) {
                emTransacao(recebimento, cenario -> {
                    Usuario ator = ator(cenario, recebimento);
                    Long outroSetor = recebimento ? cenario.dados().origem().setorId() : cenario.dados().destino().setorId();
                    banco.update("UPDATE usuario SET perfil = ?, setor_id = ? WHERE id = ?", perfil.name(), outroSetor, ator.getId());
                    banco.update("UPDATE solicitacao SET status = 'CANCELADA' WHERE id = ?", cenario.pedido().id());
                    verificarFalha(ACAO_NAO_PERMITIDA, () -> executar(recebimento, cenario.pedido().id(), ator));
                    assertFalse(respostaAtual(cenario, ator).acoesDisponiveis().contains(recebimento ? "RECEBER" : "ENVIAR"));
                    assertEquals(cenario.pedido().eventos().size(), eventos.buscarPorSolicitacao(cenario.pedido().id()).size());
                });
            }
        }
    }

    @Test
    void validaIdentificadoresEPedidoAusenteSemGravar() {
        for (boolean recebimento : List.of(false, true)) {
            emTransacao(recebimento, cenario -> {
                Usuario ator = ator(cenario, recebimento);
                for (Long id : new Long[] {null, 0L, -1L}) {
                    var falha = verificarFalha(DADOS_INVALIDOS, () -> executar(recebimento, id, ator));
                    assertTrue(falha.getErros().containsKey("solicitacaoId"));
                }
                verificarFalha(SOLICITACAO_NAO_ENCONTRADA, () -> executar(recebimento, Long.MAX_VALUE, ator));
                verificarSemAlteracoes(cenario);
            });
        }
    }

    @Test
    void rejeitaEstadosIncompativeisERecebimentoSemEnvio() {
        for (boolean recebimento : List.of(false, true)) {
            for (StatusSolicitacao estado : StatusSolicitacao.values()) {
                if (!recebimento && estado == StatusSolicitacao.APROVADA) {
                    continue;
                }
                emTransacao(false, cenario -> {
                    // EM_TRANSITO sem envio registrado também é inválido para recebimento.
                    banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", estado.name(), cenario.pedido().id());
                    Usuario ator = ator(cenario, recebimento);
                    verificarFalha(ESTADO_INCOMPATIVEL, () -> executar(recebimento, cenario.pedido().id(), ator));
                    assertFalse(respostaAtual(cenario, ator).acoesDisponiveis().contains(recebimento ? "RECEBER" : "ENVIAR"));
                    assertEquals(cenario.pedido().eventos().size(), eventos.buscarPorSolicitacao(cenario.pedido().id()).size());
                    assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
                });
            }
        }
    }

    @Test
    void repeticoesNaoSobrescrevemDatasAtoresOuDuplicamEventos() {
        emTransacao(false, cenario -> {
            Usuario expedidor = ator(cenario, false);
            Usuario recebedor = inserirUsuario(PerfilUsuario.COMUM, cenario.dados().destino().setorId());
            var enviada = servico.enviar(cenario.pedido().id(), expedidor);
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.enviar(enviada.id(), expedidor));
            var recebida = servico.receber(enviada.id(), recebedor);
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.receber(recebida.id(), recebedor));
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.enviar(recebida.id(), expedidor));
            assertEquals(recebida, respostaAtual(cenario, recebedor));
            assertEquals(enviada.envio(), recebida.envio());
        });
    }

    @Test
    void relogioECapturadoUmaVezDepoisDosBloqueiosComPrecisaoDeMicrossegundos() {
        for (boolean recebimento : List.of(false, true)) {
            emTransacao(recebimento, cenario -> {
                gerenciador.clear();
                var pedido = solicitacoes.findById(cenario.pedido().id()).orElseThrow();
                var bem = bens.findById(cenario.dados().origem().bemId()).orElseThrow();
                assertEquals(LockModeType.NONE, gerenciador.getLockMode(pedido));
                AtomicInteger leituras = new AtomicInteger();
                Clock relogio = new Clock() {
                    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                    @Override public Clock withZone(ZoneId zona) { return this; }
                    @Override public Instant instant() {
                        assertEquals(LockModeType.PESSIMISTIC_WRITE, gerenciador.getLockMode(pedido));
                        assertEquals(recebimento ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE,
                                gerenciador.getLockMode(bem));
                        leituras.incrementAndGet();
                        return Instant.parse("2027-01-01T01:02:03.123456789Z");
                    }
                };
                var comRelogio = servicoCom(eventos, relogio);
                var resposta = recebimento ? comRelogio.receber(pedido.getId(), ator(cenario, true))
                        : comRelogio.enviar(pedido.getId(), ator(cenario, false));
                assertEquals(1, leituras.get());
                assertEquals(Instant.parse("2027-01-01T01:02:03.123456Z"), resposta.eventos().getLast().ocorridoEm());
            });
        }
    }

    @Test
    void transferenciasConcluidasUsamPedidosComoFonteHistoricaSemReescreverAnterior() {
        emTransacao(true, cenario -> {
            var dados = cenario.dados();
            var primeira = servico.receber(cenario.pedido().id(), usuario(dados.destino().gestorId()));
            var segunda = servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                    dados.origem().localizacaoId(), dados.origem().gestorId(), "Retorno sintético"), usuario(dados.solicitanteId()));
            assertEquals(primeira.destino(), segunda.origem());
            segunda = servico.aprovar(segunda.id(), segunda.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
            segunda = servico.aprovar(segunda.id(), segunda.aprovacoes().getLast().id(), usuario(dados.destino().gestorId()));
            segunda = servico.enviar(segunda.id(), usuario(dados.destino().gestorId()));
            segunda = servico.receber(segunda.id(), usuario(dados.origem().gestorId()));
            gerenciador.clear();
            var anterior = respostaAtual(cenario, usuario(dados.destino().gestorId()));
            assertEquals(primeira.origem(), anterior.origem());
            assertEquals(primeira.destino(), anterior.destino());
            assertEquals(primeira.aprovacoes(), anterior.aprovacoes());
            assertEquals(primeira.eventos(), anterior.eventos());
            assertEquals(List.of(primeira.id(), segunda.id()), banco.queryForList("""
                    SELECT id FROM solicitacao WHERE bem_id = ? AND status = 'CONCLUIDA' ORDER BY recebida_em, id
                    """, Long.class, dados.origem().bemId()));
            Map<String, Object> atual = cadastroBem(dados);
            verificarFalha(ESTADO_INCOMPATIVEL, () -> servico.receber(primeira.id(), usuario(dados.destino().gestorId())));
            assertEquals(atual, cadastroBem(dados));
        });
    }

    @Test
    void falhasRevertemBemPedidoAtoresDatasEEventosSemContinuarNaTransacao() {
        for (boolean recebimento : List.of(false, true)) {
            for (String ponto : recebimento ? List.of("BEM", "PEDIDO", "EVENTO", "APOS_GRAVAR") : List.of("EVENTO", "APOS_GRAVAR")) {
                Cenario cenario = prepararConfirmado(recebimento);
                Usuario ator = novaTransacao().execute(situacao -> ator(cenario, recebimento));
                RuntimeException sintetica = new IllegalStateException("Falha sintética após flush");
                var comFalha = servicoCom(eventosComFalha(cenario, ponto, sintetica), Clock.systemUTC());
                Runnable operacao = () -> {
                    if (recebimento) {
                        comFalha.receber(cenario.pedido().id(), ator);
                    } else {
                        comFalha.enviar(cenario.pedido().id(), ator);
                    }
                };
                if (ponto.equals("APOS_GRAVAR")) {
                    assertSame(sintetica, assertThrows(IllegalStateException.class, operacao::run));
                } else {
                    assertThrows(DataIntegrityViolationException.class, operacao::run);
                }
                novaTransacao().executeWithoutResult(situacao -> verificarSemAlteracoes(cenario));
                assertEquals(recebimento ? StatusSolicitacao.CONCLUIDA : StatusSolicitacao.EM_TRANSITO,
                        executar(recebimento, cenario.pedido().id(), ator).status());
            }
        }
    }

    private EventoSolicitacaoRepository eventosComFalha(Cenario cenario, String ponto, RuntimeException sintetica) {
        return EventoSolicitacaoRepository.class.cast(Proxy.newProxyInstance(EventoSolicitacaoRepository.class.getClassLoader(),
                new Class<?>[] {EventoSolicitacaoRepository.class}, (procurador, metodo, argumentos) -> {
                    if (!metodo.getName().equals("save")) {
                        fail("Não consultar ou gravar após falha de persistência");
                    }
                    solicitacoes.flush();
                    switch (ponto) {
                        case "BEM" -> banco.update("UPDATE bem_patrimonial SET responsavel_id = ? WHERE id = ?",
                                Long.MAX_VALUE, cenario.dados().origem().bemId());
                        case "PEDIDO" -> banco.update("UPDATE solicitacao SET status = 'INVALIDO' WHERE id = ?", cenario.pedido().id());
                        case "EVENTO" -> banco.update("""
                                INSERT INTO evento_solicitacao (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                                VALUES (?, ?, 'RECEBIMENTO', 'CONCLUIDA', CURRENT_TIMESTAMP)
                                """, cenario.pedido().id(), Long.MAX_VALUE);
                        case "APOS_GRAVAR" -> {
                            eventos.save((EventoSolicitacao) argumentos[0]);
                            solicitacoes.flush();
                            throw sintetica;
                        }
                        default -> throw new IllegalArgumentException(ponto);
                    }
                    fail("O banco deve rejeitar a gravação inválida");
                    return null;
                }));
    }

    private void verificarHistorico(Cenario cenario, SolicitacaoResponse resposta, TipoEventoSolicitacao tipo) {
        assertEquals(cenario.pedido().id(), resposta.id());
        assertEquals(cenario.pedido().numero(), resposta.numero());
        assertEquals(cenario.pedido().criadaEm(), resposta.criadaEm());
        assertEquals(cenario.pedido().justificativa(), resposta.justificativa());
        assertEquals(cenario.pedido().solicitante(), resposta.solicitante());
        assertEquals(cenario.pedido().origem(), resposta.origem());
        assertEquals(cenario.pedido().destino(), resposta.destino());
        assertEquals(cenario.pedido().aprovacoes(), resposta.aprovacoes());
        assertEquals(cenario.pedido().eventos().size() + 1, resposta.eventos().size());
        assertEquals(cenario.pedido().eventos(), resposta.eventos().subList(0, cenario.pedido().eventos().size()));
        assertEquals(tipo, resposta.eventos().getLast().tipo());
        assertEquals(cenario.pedido().status(), resposta.eventos().getLast().statusAnterior());
        assertEquals(resposta.status(), resposta.eventos().getLast().statusNovo());
    }

    private void verificarDestinoAplicado(Cenario cenario) {
        Map<String, Object> esperado = new LinkedHashMap<>(cenario.cadastroBem());
        esperado.put("setor_id", cenario.pedido().destino().setor().id());
        esperado.put("localizacao_id", cenario.pedido().destino().localizacao().id());
        esperado.put("responsavel_id", cenario.pedido().destino().responsavel().id());
        assertEquals(esperado, cadastroBem(cenario.dados()));
    }

    private void verificarSemAlteracoes(Cenario cenario) {
        gerenciador.clear();
        var atual = respostaAtual(cenario, ator(cenario, cenario.pedido().status() == StatusSolicitacao.EM_TRANSITO));
        assertEquals(cenario.pedido().status(), atual.status());
        assertEquals(cenario.pedido().envio(), atual.envio());
        assertEquals(cenario.pedido().recebimento(), atual.recebimento());
        assertEquals(cenario.pedido().aprovacoes(), atual.aprovacoes());
        assertEquals(cenario.pedido().eventos(), atual.eventos());
        assertEquals(cenario.cadastroBem(), cadastroBem(cenario.dados()));
    }

    private SolicitacaoResponse respostaAtual(Cenario cenario, Usuario ator) {
        Long id = cenario.pedido().id();
        return mapeador.mapear(solicitacoes.findById(id).orElseThrow(), aprovacoes.buscarPorSolicitacao(id),
                eventos.buscarPorSolicitacao(id), ator);
    }

    private ExcecaoExecucaoSolicitacao verificarFalha(MotivoFalhaExecucaoSolicitacao motivo, Runnable operacao) {
        var falha = assertThrows(ExcecaoExecucaoSolicitacao.class, operacao::run);
        assertEquals(motivo, falha.getMotivo());
        return falha;
    }

    private Usuario inserirUsuario(PerfilUsuario perfil, Long setorId) {
        Long id = banco.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id)
                VALUES ('Operador sintético', ?, ?, ?, ?) RETURNING id
                """, Long.class, DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO, perfil.name(), setorId);
        return usuario(id);
    }

    private void emTransacao(boolean emTransito, Consumer<Cenario> verificacao) {
        emDados(dados -> verificacao.accept(criarCenario(dados, emTransito)));
    }

    private void emDados(Consumer<DadosSolicitacoesTeste> verificacao) {
        novaTransacao().executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            limitarEspera();
            verificacao.accept(DadosSolicitacoesTeste.preparar(banco));
        });
    }

    private Cenario prepararConfirmado(boolean emTransito) {
        // Fixtures confirmadas permanecem em simpi_test, sem limpeza destrutiva.
        return novaTransacao().execute(situacao -> criarCenario(DadosSolicitacoesTeste.preparar(banco), emTransito));
    }

    private Cenario criarCenario(DadosSolicitacoesTeste dados, boolean emTransito) {
        var pedido = aprovarPedido(servico.criar(entrada(dados), usuario(dados.solicitanteId())), dados);
        if (emTransito) {
            pedido = servico.enviar(pedido.id(), usuario(dados.origem().gestorId()));
        }
        return new Cenario(dados, pedido, cadastroBem(dados));
    }

    private SolicitacaoResponse aprovarPedido(SolicitacaoResponse pedido, DadosSolicitacoesTeste dados) {
        var primeira = servico.aprovar(pedido.id(), pedido.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
        return servico.aprovar(pedido.id(), primeira.aprovacoes().getLast().id(), usuario(dados.origem().gestorId()));
    }

    private CriarSolicitacaoRequest entrada(DadosSolicitacoesTeste dados) {
        return new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa sintética");
    }

    private Map<String, Object> cadastroBem(DadosSolicitacoesTeste dados) {
        return banco.queryForMap("SELECT * FROM bem_patrimonial WHERE id = ?", dados.origem().bemId());
    }

    private Usuario ator(Cenario cenario, boolean recebimento) {
        return usuario(recebimento ? cenario.dados().destino().gestorId() : cenario.dados().origem().gestorId());
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow();
    }

    private SolicitacaoResponse executar(boolean recebimento, Long id, Usuario ator) {
        return recebimento ? servico.receber(id, ator) : servico.enviar(id, ator);
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
