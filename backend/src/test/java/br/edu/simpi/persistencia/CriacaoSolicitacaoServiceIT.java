package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import br.edu.simpi.solicitacao.service.SolicitacaoMapper;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class CriacaoSolicitacaoServiceIT {
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
    CriacaoSolicitacaoServiceIT(JdbcTemplate banco, EntityManager gerenciador,
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

    @ParameterizedTest
    @ValueSource(strings = {"COMUM", "ADMINISTRADOR"})
    void criaPedidoComAprovacaoAreaERespostaCompletaEReferenciasPreservadas(String perfil) {
        emTransacao(dados -> {
            // Setor solicitante difere da origem e do destino.
            // O preparo inicial tornou imediata a FK necessária à carga circular.
            banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
            var terceiro = DadosPatrimoniaisTeste.preparar(banco);
            banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
            banco.update("UPDATE usuario SET perfil = ?, setor_id = ? WHERE id = ?",
                    perfil, terceiro.setorId(), dados.solicitanteId());
            var resposta = servico.criar(entrada(dados), solicitante(dados));
            assertNotNull(resposta.id());
            assertNotNull(resposta.numero());
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, resposta.status());
            assertEquals("Justificativa sintética", resposta.justificativa());
            assertEquals(terceiro.setorId(), resposta.solicitante().setor().id());
            assertEquals(dados.origem().setorId(), resposta.origem().setor().id());
            assertEquals(dados.origem().localizacaoId(), resposta.origem().localizacao().id());
            assertEquals(dados.origem().gestorId(), resposta.origem().responsavel().id());
            assertEquals(dados.destino().setorId(), resposta.destino().setor().id());
            assertEquals(dados.destino().localizacaoId(), resposta.destino().localizacao().id());
            assertEquals(dados.destino().gestorId(), resposta.destino().responsavel().id());
            assertEquals(dados.origem().bemId(), resposta.bem().id());
            assertNotNull(resposta.bem().codigo());
            assertNull(resposta.bem().plaqueta());
            assertNotNull(resposta.bem().descricao());
            assertNotNull(resposta.solicitante().nome());
            assertEquals(dados.solicitanteId(), resposta.solicitante().id());
            assertEquals(1, resposta.aprovacoes().size());
            var aprovacao = resposta.aprovacoes().getFirst();
            assertNotNull(aprovacao.id());
            assertEquals(EtapaAprovacao.GESTOR_AREA, aprovacao.etapa());
            assertEquals(terceiro.gestorId(), aprovacao.aprovador().id());
            assertEquals(DecisaoAprovacao.PENDENTE, aprovacao.decisao());
            assertNull(aprovacao.decididaEm());
            assertNull(aprovacao.justificativa());
            assertNull(resposta.envio());
            assertNull(resposta.recebimento());
            assertEquals(List.of("CANCELAR"), resposta.acoesDisponiveis());
            assertEquals(1, resposta.eventos().size());
            var evento = resposta.eventos().getFirst();
            assertNotNull(evento.id());
            assertEquals(TipoEventoSolicitacao.CRIACAO, evento.tipo());
            assertEquals(dados.solicitanteId(), evento.autor().id());
            assertNull(evento.statusAnterior());
            assertEquals(resposta.status(), evento.statusNovo());
            assertEquals(resposta.criadaEm(), evento.ocorridoEm());
            assertEquals("Solicitação encaminhada ao gestor da área.", evento.descricao());
            verificarBemInalterado(dados);
            banco.update("UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ? WHERE id = ?",
                    dados.destino().setorId(), dados.destino().localizacaoId(), dados.destino().gestorId(), dados.origem().bemId());
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), dados.solicitanteId());
            gerenciador.clear();
            var relida = solicitacoes.findById(resposta.id()).orElseThrow();
            assertEquals(terceiro.setorId(), relida.getSetorSolicitante().getId());
            assertEquals(dados.origem().setorId(), relida.getSetorOrigem().getId());
            assertEquals(dados.origem().localizacaoId(), relida.getLocalizacaoOrigem().getId());
            assertEquals(dados.origem().gestorId(), relida.getResponsavelOrigem().getId());
        });
    }

    @Test
    void administradorDependeDasDuasAprovacoesDosGestoresDesignados() {
        emTransacao(dados -> {
            banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
            var area = DadosPatrimoniaisTeste.preparar(banco);
            banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
            banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR', setor_id = ? WHERE id = ?",
                    area.setorId(), dados.solicitanteId());

            var criada = servico.criar(entrada(dados), solicitante(dados));
            var primeira = criada.aprovacoes().getFirst();
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, criada.status());
            assertEquals(area.gestorId(), primeira.aprovador().id());
            assertEquals(List.of("CANCELAR"), criada.acoesDisponiveis());

            var aposArea = servico.aprovar(criada.id(), primeira.id(),
                    usuarios.findById(area.gestorId()).orElseThrow());
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, aposArea.status());
            assertEquals(2, aposArea.aprovacoes().size());
            assertEquals(DecisaoAprovacao.APROVADA, aposArea.aprovacoes().getFirst().decisao());
            var segunda = aposArea.aprovacoes().getLast();
            assertEquals(EtapaAprovacao.GESTOR_RESPONSAVEL, segunda.etapa());
            assertEquals(dados.origem().gestorId(), segunda.aprovador().id());
            assertEquals(DecisaoAprovacao.PENDENTE, segunda.decisao());

            var aprovada = servico.aprovar(criada.id(), segunda.id(),
                    usuarios.findById(dados.origem().gestorId()).orElseThrow());
            assertEquals(StatusSolicitacao.APROVADA, aprovada.status());
            assertTrue(aprovada.aprovacoes().stream()
                    .allMatch(aprovacao -> aprovacao.decisao() == DecisaoAprovacao.APROVADA));
            verificarBemInalterado(dados);
        });
    }

    @Test
    void gestorCriaSomenteEtapaResponsavelEAnunciaAutoaprovacaoSemDecidir() {
        emTransacao(dados -> {
            Usuario gestor = usuarios.findById(dados.origem().gestorId()).orElseThrow();
            var resposta = servico.criar(entrada(dados), gestor);
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, resposta.status());
            assertEquals(1, resposta.aprovacoes().size());
            assertEquals(EtapaAprovacao.GESTOR_RESPONSAVEL, resposta.aprovacoes().getFirst().etapa());
            assertEquals(gestor.getId(), resposta.aprovacoes().getFirst().aprovador().id());
            assertEquals(DecisaoAprovacao.PENDENTE, resposta.aprovacoes().getFirst().decisao());
            assertEquals(List.of("APROVAR", "RECUSAR", "CANCELAR"), resposta.acoesDisponiveis());
            assertEquals(1, resposta.eventos().size());
            verificarBemInalterado(dados);
        });
    }

    @Test
    void gestorDeOutroSetorRecebeSomenteAcaoCancelar() {
        emTransacao(dados -> {
            var gestor = usuarios.findById(dados.destino().gestorId()).orElseThrow();
            var resposta = servico.criar(entrada(dados), gestor);
            assertEquals(dados.origem().gestorId(), resposta.aprovacoes().getFirst().aprovador().id());
            assertEquals(List.of("CANCELAR"), resposta.acoesDisponiveis());
        });
    }

    @Test
    void usaPerfilESetorPersistidosMesmoComSolicitanteJaCarregado() {
        emTransacao(dados -> {
            Usuario antigo = solicitante(dados);
            banco.update("UPDATE usuario SET perfil = 'GESTOR', setor_id = ? WHERE id = ?",
                    dados.destino().setorId(), dados.solicitanteId());
            var resposta = servico.criar(entrada(dados), antigo);
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, resposta.status());
            assertEquals(dados.destino().setorId(), resposta.solicitante().setor().id());
        });
    }

    @Test
    void releOrigemAtualSobBloqueioMesmoComBemJaCarregado() {
        emTransacao(dados -> {
            bens.findById(dados.origem().bemId()).orElseThrow().getLocalizacao().getNome();
            banco.update("UPDATE bem_patrimonial SET localizacao_id = ? WHERE id = ?",
                    dados.destino().localizacaoId(), dados.origem().bemId());
            var resposta = servico.criar(entrada(dados), solicitante(dados));
            assertEquals(dados.destino().localizacaoId(), resposta.origem().localizacao().id());
            assertEquals(dados.origem().setorId(), resposta.origem().setor().id());
        });
    }

    @Test
    void capturaRelogioUmaVezEPreservaAnoUtcEMicrossegundos() {
        emTransacao(dados -> {
            AtomicInteger leituras = new AtomicInteger();
            Clock relogio = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zona) { return this; }
                @Override public Instant instant() {
                    return leituras.getAndIncrement() == 0
                            ? Instant.parse("2026-12-31T23:59:59.999999999Z")
                            : Instant.parse("2027-01-01T00:00:00Z");
                }
            };
            var resposta = servicoCom(aprovacoes, eventos, relogio).criar(entrada(dados), solicitante(dados));
            assertEquals(1, leituras.get());
            assertTrue(resposta.numero().startsWith("SOL-2026-"));
            assertEquals(Instant.parse("2026-12-31T23:59:59.999999Z"), resposta.criadaEm());
            assertEquals(resposta.criadaEm(), resposta.eventos().getFirst().ocorridoEm());
            gerenciador.clear();
            assertEquals(resposta.criadaEm(), solicitacoes.findById(resposta.id()).orElseThrow().getCriadaEm());
        });
    }

    @Test
    void validaCamposObrigatoriosEmChamadaDireta() {
        emTransacao(dados -> {
            var falha = assertThrows(ExcecaoCriacaoSolicitacao.class, () -> servico.criar(
                    new CriarSolicitacaoRequest(null, null, null, null, " \t\n "), solicitante(dados)));
            assertEquals(DADOS_INVALIDOS, falha.getMotivo());
            assertEquals(5, falha.getErros().size());
        });
        verificarRejeicao(dados -> null, DADOS_INVALIDOS);
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), null), DADOS_INVALIDOS);
    }

    @Test
    void rejeitaReferenciasInexistentes() {
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(Long.MAX_VALUE, dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Texto"), REFERENCIA_NAO_ENCONTRADA);
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), Long.MAX_VALUE,
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Texto"), REFERENCIA_NAO_ENCONTRADA);
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                Long.MAX_VALUE, dados.destino().gestorId(), "Texto"), REFERENCIA_NAO_ENCONTRADA);
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), Long.MAX_VALUE, "Texto"), REFERENCIA_NAO_ENCONTRADA);
    }

    @Test
    void rejeitaBemEDestinoInativos() {
        for (String tabela : List.of("bem_patrimonial", "setor", "localizacao", "usuario")) {
            emTransacao(dados -> {
                Long id = switch (tabela) {
                    case "bem_patrimonial" -> dados.origem().bemId();
                    case "setor" -> dados.destino().setorId();
                    case "localizacao" -> dados.destino().localizacaoId();
                    default -> dados.destino().gestorId();
                };
                banco.update("UPDATE " + tabela + " SET ativo = false WHERE id = ?", id);
                verificarFalha(dados, DADOS_INVALIDOS);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMUM", "ADMINISTRADOR"})
    void rejeitaSolicitanteInativoOuAusente(String perfil) {
        emTransacao(dados -> {
            Usuario antigo = solicitante(dados);
            banco.update("UPDATE usuario SET perfil = ?, ativo = false WHERE id = ?",
                    perfil, dados.solicitanteId());
            assertEquals(SOLICITANTE_INATIVO, assertThrows(ExcecaoCriacaoSolicitacao.class,
                    () -> servico.criar(entrada(dados), antigo)).getMotivo());
        });
        emTransacao(dados -> {
            assertEquals(SOLICITANTE_INVALIDO, assertThrows(ExcecaoCriacaoSolicitacao.class,
                    () -> servico.criar(entrada(dados), null)).getMotivo());
        });
    }

    @Test
    void rejeitaGestorDesignadoInativoComPerfilOuSetorInvalido() {
        for (String atribuicao : List.of("ativo = false", "perfil = 'COMUM'", "setor_id = ?")) {
            emTransacao(dados -> {
                if (atribuicao.contains("?")) {
                    banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?",
                            dados.destino().setorId(), dados.origem().gestorId());
                } else {
                    banco.update("UPDATE usuario SET " + atribuicao + " WHERE id = ?", dados.origem().gestorId());
                }
                verificarFalha(dados, GESTOR_DESIGNADO_INVALIDO);
            });
        }
    }

    @Test
    void rejeitaResponsavelDeOutroSetorEDestinoIgualAOrigem() {
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.origem().gestorId(), "Texto"), DADOS_INVALIDOS);
        verificarRejeicao(dados -> new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                dados.origem().localizacaoId(), dados.origem().gestorId(), "Texto"), DADOS_INVALIDOS);
    }

    @Test
    void aceitaMudancaSomenteDeLocalizacaoOuResponsavel() {
        emTransacao(dados -> {
            var resposta = servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                    dados.destino().localizacaoId(), dados.origem().gestorId(), "Texto"), solicitante(dados));
            assertNotNull(resposta.id());
        });
        emTransacao(dados -> {
            var resposta = servico.criar(new CriarSolicitacaoRequest(dados.origem().bemId(), dados.origem().setorId(),
                    dados.origem().localizacaoId(), dados.solicitanteId(), "Texto"), solicitante(dados));
            assertNotNull(resposta.id());
        });
    }

    @Test
    void bloqueiaTodosOsEstadosAtivosELiberaTodosOsFinais() {
        for (StatusSolicitacao estado : StatusSolicitacao.values()) {
            emTransacao(dados -> {
                dados.inserirSolicitacao(banco, estado.name());
                if (List.of(StatusSolicitacao.CONCLUIDA, StatusSolicitacao.RECUSADA,
                        StatusSolicitacao.CANCELADA).contains(estado)) {
                    assertNotNull(servico.criar(entrada(dados), solicitante(dados)).id());
                } else {
                    verificarFalha(dados, BEM_COM_SOLICITACAO_EM_ANDAMENTO);
                }
            });
        }
    }

    @Test
    void duasChamadasConcorrentesConfirmamUmUnicoConjunto() throws Exception {
        DadosSolicitacoesTeste dados = prepararConfirmado();
        Usuario autor = novaTransacao().execute(situacao -> solicitante(dados));
        CyclicBarrier barreira = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        List<Future<String>> futuros = new ArrayList<>();
        try {
            for (int indice = 0; indice < 2; indice++) {
                futuros.add(executor.submit(() -> {
                    barreira.await(4, TimeUnit.SECONDS);
                    try {
                        novaTransacao().executeWithoutResult(situacao -> {
                            limitarEspera();
                            servico.criar(entrada(dados), autor);
                        });
                        return "CONFIRMADA";
                    } catch (ExcecaoCriacaoSolicitacao falha) {
                        assertEquals(BEM_COM_SOLICITACAO_EM_ANDAMENTO, falha.getMotivo());
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
            verificarQuantidades(dados, 1);
        } finally {
            futuros.forEach(futuro -> futuro.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void falhaNaAprovacaoOuEventoReverteConjuntoELiberaBem() {
        for (boolean falharAprovacao : List.of(true, false)) {
            DadosSolicitacoesTeste dados = prepararConfirmado();
            Usuario autor = novaTransacao().execute(situacao -> solicitante(dados));
            RuntimeException falhaEsperada = new IllegalStateException("Falha sintética de gravação");
            var servicoComFalha = servicoCom(
                    falharAprovacao ? falharDepoisDeSalvar(AprovacaoRepository.class, aprovacoes, falhaEsperada) : aprovacoes,
                    falharAprovacao ? eventos : falharDepoisDeSalvar(EventoSolicitacaoRepository.class, eventos, falhaEsperada),
                    Clock.systemUTC());
            assertSame(falhaEsperada, assertThrows(IllegalStateException.class,
                    () -> servicoComFalha.criar(entrada(dados), autor)));
            verificarQuantidades(dados, 0);
            novaTransacao().executeWithoutResult(situacao -> verificarBemInalterado(dados));
            assertNotNull(servico.criar(entrada(dados), autor).id());
            verificarQuantidades(dados, 1);
        }
    }

    @Test
    void falhaRealNoBancoDuranteEventoReverteTudoSemMascararIntegridade() {
        DadosSolicitacoesTeste dados = prepararConfirmado();
        Usuario autor = novaTransacao().execute(situacao -> solicitante(dados));
        EventoSolicitacaoRepository eventosComFalha = EventoSolicitacaoRepository.class.cast(
                Proxy.newProxyInstance(EventoSolicitacaoRepository.class.getClassLoader(),
                        new Class<?>[] {EventoSolicitacaoRepository.class}, (procurador, metodo, argumentos) -> {
                            if (metodo.getName().equals("save")) {
                                solicitacoes.flush();
                                banco.update("""
                                        INSERT INTO evento_solicitacao
                                            (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                                        VALUES (?, ?, 'CRIACAO', 'AGUARDANDO_GESTOR_AREA', CURRENT_TIMESTAMP)
                                        """, ((EventoSolicitacao) argumentos[0])
                                                .getSolicitacao().getId(), Long.MAX_VALUE);
                                fail("A FK deve rejeitar o autor inexistente");
                            }
                            try {
                                return metodo.invoke(eventos, argumentos);
                            } catch (InvocationTargetException excecao) {
                                throw excecao.getCause();
                            }
                        }));
        var comFalha = servicoCom(aprovacoes, eventosComFalha, Clock.systemUTC());
        assertThrows(DataIntegrityViolationException.class, () -> comFalha.criar(entrada(dados), autor));
        verificarQuantidades(dados, 0);
        assertNotNull(servico.criar(entrada(dados), autor).id());
    }

    @Test
    void traduzViolacaoRealDoIndiceQuandoConsultaDeDisponibilidadeNaoDetectaPedido() {
        DadosSolicitacoesTeste dados = prepararConfirmado();
        Usuario autor = novaTransacao().execute(situacao -> solicitante(dados));
        servico.criar(entrada(dados), autor);
        // Simula um caminho de gravação que escapou da verificação preventiva.
        SolicitacaoRepository semVerificacao = SolicitacaoRepository.class.cast(
                Proxy.newProxyInstance(SolicitacaoRepository.class.getClassLoader(),
                        new Class<?>[] {SolicitacaoRepository.class}, (procurador, metodo, argumentos) -> {
                            if (metodo.getName().equals("existePorBemEEstados")) {
                                return false;
                            }
                            try {
                                return metodo.invoke(solicitacoes, argumentos);
                            } catch (InvocationTargetException excecao) {
                                throw excecao.getCause();
                            }
                        }));
        var semVerificacaoPreventiva = transacional(new SolicitacaoService(usuarios, bens, setores, localizacoes,
                semVerificacao, aprovacoes, eventos, numeros, mapeador, gerenciador));
        var falha = assertThrows(ExcecaoCriacaoSolicitacao.class,
                () -> semVerificacaoPreventiva.criar(entrada(dados), autor));
        assertEquals(BEM_COM_SOLICITACAO_EM_ANDAMENTO, falha.getMotivo());
        assertInstanceOf(DataIntegrityViolationException.class, falha.getCause());
        verificarQuantidades(dados, 1);
    }

    @Test
    void traduzSomenteUnicidadeDoIndiceAtivoSemContinuarAOperacao() {
        record ViolacaoTeste(String restricao, String estadoSql, boolean conflito) { }
        for (var cenario : List.of(
                new ViolacaoTeste("uq_solicitacao_bem_ativa", "23505", true),
                new ViolacaoTeste("uq_solicitacao_numero", "23505", false),
                new ViolacaoTeste("uq_solicitacao_bem_ativa", "23503", false))) {
            DadosSolicitacoesTeste dados = prepararConfirmado();
            Usuario autor = novaTransacao().execute(situacao -> solicitante(dados));
            var violacao = new ConstraintViolationException("Violação sintética",
                    new SQLException("Violação", cenario.estadoSql()), cenario.restricao());
            var falhaEsperada = new DataIntegrityViolationException("Falha sintética", violacao);
            AtomicInteger mapeamentos = new AtomicInteger();
            SolicitacaoMapper mapeadorVigiado = new SolicitacaoMapper() {
                @Override
                public SolicitacaoResponse mapearCriacao(Solicitacao solicitacao,
                        Aprovacao aprovacao, EventoSolicitacao evento, Usuario solicitante) {
                    mapeamentos.incrementAndGet();
                    return super.mapearCriacao(solicitacao, aprovacao, evento, solicitante);
                }
            };
            var servicoComFalha = transacional(new SolicitacaoService(usuarios, bens, setores, localizacoes,
                    solicitacoes, falharDepoisDeSalvar(AprovacaoRepository.class, aprovacoes, falhaEsperada),
                    eventos, numeros, mapeadorVigiado, gerenciador));
            if (cenario.conflito()) {
                var falha = assertThrows(ExcecaoCriacaoSolicitacao.class,
                        () -> servicoComFalha.criar(entrada(dados), autor));
                assertEquals(BEM_COM_SOLICITACAO_EM_ANDAMENTO, falha.getMotivo());
                assertSame(falhaEsperada, falha.getCause());
            } else {
                assertSame(falhaEsperada, assertThrows(DataIntegrityViolationException.class,
                        () -> servicoComFalha.criar(entrada(dados), autor)));
            }
            assertEquals(0, mapeamentos.get());
            verificarQuantidades(dados, 0);
        }
    }

    private CriarSolicitacaoRequest entrada(DadosSolicitacoesTeste dados) {
        return new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), " \tJustificativa sintética\n ");
    }

    private Usuario solicitante(DadosSolicitacoesTeste dados) {
        return usuarios.findById(dados.solicitanteId()).orElseThrow();
    }

    private void emTransacao(Consumer<DadosSolicitacoesTeste> verificacao) {
        novaTransacao().executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            limitarEspera();
            verificacao.accept(DadosSolicitacoesTeste.preparar(banco));
        });
    }

    private void verificarRejeicao(Function<DadosSolicitacoesTeste, CriarSolicitacaoRequest> entrada,
            MotivoFalhaCriacaoSolicitacao motivo) {
        emTransacao(dados -> {
            var falha = assertThrows(ExcecaoCriacaoSolicitacao.class,
                    () -> servico.criar(entrada.apply(dados), solicitante(dados)));
            assertEquals(motivo, falha.getMotivo());
            verificarQuantidades(dados, 0);
        });
    }

    private void verificarFalha(DadosSolicitacoesTeste dados, MotivoFalhaCriacaoSolicitacao motivo) {
        var falha = assertThrows(ExcecaoCriacaoSolicitacao.class,
                () -> servico.criar(entrada(dados), solicitante(dados)));
        assertEquals(motivo, falha.getMotivo());
    }

    private DadosSolicitacoesTeste prepararConfirmado() {
        // Dados sintéticos confirmados permanecem em simpi_test, como nas suítes existentes.
        return Objects.requireNonNull(novaTransacao().execute(situacao -> DadosSolicitacoesTeste.preparar(banco)));
    }

    private void verificarQuantidades(DadosSolicitacoesTeste dados, long quantidade) {
        assertEquals(quantidade, banco.queryForObject("SELECT count(*) FROM solicitacao WHERE bem_id = ?",
                Long.class, dados.origem().bemId()));
        for (String tabela : List.of("aprovacao", "evento_solicitacao")) {
            assertEquals(quantidade, banco.queryForObject("SELECT count(*) FROM " + tabela
                    + " WHERE solicitacao_id IN (SELECT id FROM solicitacao WHERE bem_id = ?)",
                    Long.class, dados.origem().bemId()));
        }
    }

    private void verificarBemInalterado(DadosSolicitacoesTeste dados) {
        var registro = banco.queryForMap("SELECT setor_id, localizacao_id, responsavel_id FROM bem_patrimonial WHERE id = ?",
                dados.origem().bemId());
        assertEquals(dados.origem().setorId(), registro.get("setor_id"));
        assertEquals(dados.origem().localizacaoId(), registro.get("localizacao_id"));
        assertEquals(dados.origem().gestorId(), registro.get("responsavel_id"));
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

    private SolicitacaoService servicoCom(AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos, Clock relogio) {
        return transacional(new SolicitacaoService(usuarios, bens, setores, localizacoes, solicitacoes,
                aprovacoes, eventos, numeros, mapeador, gerenciador, relogio));
    }

    private SolicitacaoService transacional(SolicitacaoService servico) {
        var fabrica = new ProxyFactory(servico);
        fabrica.addAdvice(new TransactionInterceptor(transacoes, new AnnotationTransactionAttributeSource()));
        return (SolicitacaoService) fabrica.getProxy();
    }

    private <T> T falharDepoisDeSalvar(Class<T> tipo, T repositorio, RuntimeException falha) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[] {tipo},
                (procurador, metodo, argumentos) -> {
                    try {
                        Object resultado = metodo.invoke(repositorio, argumentos);
                        if (metodo.getName().equals("save")) {
                            solicitacoes.flush();
                            throw falha;
                        }
                        return resultado;
                    } catch (InvocationTargetException excecao) {
                        throw excecao.getCause();
                    }
                }));
    }
}
