package br.edu.simpi.persistencia;

import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.ExcecaoDecisaoAprovacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import br.edu.simpi.solicitacao.service.SolicitacaoMapper;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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

import static br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Timeout(30)
class DecisaoAprovacaoServiceIT {
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
    DecisaoAprovacaoServiceIT(JdbcTemplate banco, EntityManager gerenciador,
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
    void aprovaDuasEtapasComMesmoGestorSomentePorDecisoesExplicitas() {
        emTransacao(cenario -> {
            var dados = cenario.dados();
            Usuario gestor = usuario(dados.origem().gestorId());
            var primeira = servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor);
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, primeira.status());
            assertEquals(List.of(EtapaAprovacao.GESTOR_AREA, EtapaAprovacao.GESTOR_RESPONSAVEL),
                    primeira.aprovacoes().stream().map(SolicitacaoResponse.AprovacaoResponse::etapa).toList());
            assertEquals(DecisaoAprovacao.APROVADA, primeira.aprovacoes().getFirst().decisao());
            var segunda = primeira.aprovacoes().get(1);
            assertEquals(DecisaoAprovacao.PENDENTE, segunda.decisao());
            assertNull(segunda.decididaEm());
            assertNull(segunda.justificativa());
            assertEquals(gestor.getId(), segunda.aprovador().id());
            assertEquals(List.of("APROVAR", "RECUSAR"), primeira.acoesDisponiveis());
            verificarHistorico(cenario, primeira, TipoEventoSolicitacao.APROVACAO);
            verificarFalha(ETAPA_JA_DECIDIDA,
                    () -> servico.aprovar(primeira.id(), cenario.aprovacaoId(), gestor));
            assertEquals(DecisaoAprovacao.PENDENTE, aprovacoes.findById(segunda.id()).orElseThrow().getDecisao());
            var finalizada = servico.aprovar(primeira.id(), segunda.id(), gestor);
            assertEquals(StatusSolicitacao.APROVADA, finalizada.status());
            assertEquals(3, finalizada.eventos().size());
            assertEquals(List.of("ENVIAR"), finalizada.acoesDisponiveis());
            assertTrue(finalizada.aprovacoes().stream().allMatch(item -> item.decisao() == DecisaoAprovacao.APROVADA));
            verificarBemInalterado(dados);
        });
    }

    @Test
    void gestorSolicitantePodeAutoaprovarSemEtapaFicticia() {
        emTransacaoNova(dados -> {
            Usuario gestor = usuario(dados.origem().gestorId());
            var pedido = servico.criar(entrada(dados), gestor);
            assertEquals(List.of("APROVAR", "RECUSAR", "CANCELAR"), pedido.acoesDisponiveis());
            var resposta = servico.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(), gestor);
            assertEquals(StatusSolicitacao.APROVADA, resposta.status());
            assertEquals(1, resposta.aprovacoes().size());
            assertEquals(EtapaAprovacao.GESTOR_RESPONSAVEL, resposta.aprovacoes().getFirst().etapa());
            assertEquals(gestor.getId(), resposta.solicitante().id());
            assertEquals(List.of("CANCELAR", "ENVIAR"), resposta.acoesDisponiveis());
            verificarBemInalterado(dados);
        });
    }

    @Test
    void usaOrigemHistoricaParaDesignarSegundaEtapaComGestoresDistintos() {
        emTransacaoNova(dados -> {
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), dados.solicitanteId());
            var pedido = servico.criar(entrada(dados), usuario(dados.solicitanteId()));
            banco.update("UPDATE bem_patrimonial SET setor_id = ?, localizacao_id = ?, responsavel_id = ? WHERE id = ?",
                    dados.destino().setorId(), dados.destino().localizacaoId(), dados.destino().gestorId(), dados.origem().bemId());
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.origem().setorId(), dados.solicitanteId());
            gerenciador.clear();
            var primeira = servico.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(), usuario(dados.destino().gestorId()));
            assertEquals(dados.origem().gestorId(), primeira.aprovacoes().get(1).aprovador().id());
            assertEquals(dados.destino().setorId(), primeira.solicitante().setor().id());
            assertEquals(dados.solicitanteId(), primeira.solicitante().id());
            assertEquals(dados.origem().setorId(), primeira.origem().setor().id());
            assertEquals(dados.origem().localizacaoId(), primeira.origem().localizacao().id());
            assertEquals(dados.origem().gestorId(), primeira.origem().responsavel().id());
            assertEquals(List.of(), primeira.acoesDisponiveis());
            var finalizada = servico.aprovar(pedido.id(), primeira.aprovacoes().get(1).id(), usuario(dados.origem().gestorId()));
            assertEquals(StatusSolicitacao.APROVADA, finalizada.status());
            assertEquals(dados.destino().setorId(), banco.queryForObject(
                    "SELECT setor_id FROM bem_patrimonial WHERE id = ?", Long.class, dados.origem().bemId()));
        });
    }

    @Test
    void recusaCadaEtapaComJustificativaELiberaNovaSolicitacao() {
        for (boolean segundaEtapa : List.of(false, true)) {
            emTransacao(cenario -> {
                Usuario gestor = usuario(cenario.dados().origem().gestorId());
                var pedido = segundaEtapa
                        ? servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor) : cenario.pedido();
                var etapa = pedido.aprovacoes().getLast();
                var resposta = servico.recusar(pedido.id(), etapa.id(), " \tRecusa justificada\n ", gestor);
                assertEquals(StatusSolicitacao.RECUSADA, resposta.status());
                assertEquals(segundaEtapa ? 2 : 1, resposta.aprovacoes().size());
                assertEquals(DecisaoAprovacao.RECUSADA, resposta.aprovacoes().getLast().decisao());
                assertEquals("Recusa justificada", resposta.aprovacoes().getLast().justificativa());
                assertEquals(List.of(), resposta.acoesDisponiveis());
                assertEquals(TipoEventoSolicitacao.RECUSA, resposta.eventos().getLast().tipo());
                assertEquals("Recusa justificada", resposta.eventos().getLast().descricao());
                assertEquals(resposta.aprovacoes().getLast().decididaEm(), resposta.eventos().getLast().ocorridoEm());
                verificarFalha(ETAPA_JA_DECIDIDA, () -> servico.recusar(pedido.id(), etapa.id(), "Outra", gestor));
                verificarFalha(ETAPA_JA_DECIDIDA, () -> servico.aprovar(pedido.id(), etapa.id(), gestor));
                verificarBemInalterado(cenario.dados());
                assertNotEquals(pedido.id(), servico.criar(entrada(cenario.dados()), usuario(cenario.dados().solicitanteId())).id());
            });
        }
    }

    @Test
    void validaIdsEJustificativaEmChamadasDiretasSemGravacoes() {
        emTransacao(cenario -> {
            Usuario gestor = usuario(cenario.dados().origem().gestorId());
            for (String texto : new String[] {null, "", " ", "\t\n\r "}) {
                var falha = verificarFalha(DADOS_INVALIDOS,
                        () -> servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), texto, gestor));
                assertTrue(falha.getErros().containsKey("justificativa"));
            }
            for (Long id : new Long[] {null, 0L, -1L}) {
                verificarFalha(DADOS_INVALIDOS, () -> servico.aprovar(id, cenario.aprovacaoId(), gestor));
                verificarFalha(DADOS_INVALIDOS, () -> servico.aprovar(cenario.pedido().id(), id, gestor));
            }
            verificarPendente(cenario);
        });
    }

    @Test
    void rejeitaAtorAusenteInexistenteOuInativoMesmoPreviamenteCarregado() {
        emTransacao(cenario -> {
            verificarFalha(NAO_AUTENTICADO, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), null));
            Usuario inexistente = new Usuario("Ator sintético", "ator@example.invalid",
                    DadosPatrimoniaisTeste.HASH_SINTETICO, PerfilUsuario.GESTOR,
                    setores.findById(cenario.dados().origem().setorId()).orElseThrow());
            verificarFalha(NAO_AUTENTICADO, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), inexistente));
            ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
            verificarFalha(NAO_AUTENTICADO, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), inexistente));
            Usuario gestor = usuario(cenario.dados().origem().gestorId());
            banco.update("UPDATE usuario SET ativo = false WHERE id = ?", gestor.getId());
            verificarFalha(NAO_AUTENTICADO, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor));
            verificarAcoesSemDecisao(cenario, gestor);
            verificarPendente(cenario);
        });
    }

    @Test
    void rejeitaPerfisDesignacaoESetorAtuaisSemExporDecisao() {
        for (String perfil : List.of("COMUM", "ADMINISTRADOR")) {
            emTransacao(cenario -> {
                Usuario gestor = usuario(cenario.dados().origem().gestorId());
                banco.update("UPDATE usuario SET perfil = ? WHERE id = ?", perfil, gestor.getId());
                verificarFalha(ACAO_NAO_PERMITIDA,
                        () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor));
                verificarAcoesSemDecisao(cenario, gestor);
                verificarPendente(cenario);
            });
        }
        emTransacao(cenario -> {
            Usuario gestor = usuario(cenario.dados().origem().gestorId());
            banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", cenario.dados().destino().setorId(), gestor.getId());
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor));
            verificarAcoesSemDecisao(cenario, gestor);
            Usuario outro = usuario(cenario.dados().destino().gestorId());
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), outro));
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Motivo", outro));
        });
        emTransacao(cenario -> {
            Usuario gestor = usuario(cenario.dados().origem().gestorId());
            servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Motivo", gestor);
            // Sem designação, o ator não recebe ETAPA_JA_DECIDIDA nem ESTADO_INCOMPATIVEL.
            verificarFalha(ACAO_NAO_PERMITIDA, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(),
                    usuario(cenario.dados().destino().gestorId())));
        });
    }

    @Test
    void verificaAcessoAntesDeRevelarAprovacaoAusenteOuEstado() {
        emTransacao(cenario -> {
            banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
            var terceiro = DadosPatrimoniaisTeste.preparar(banco);
            banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
            Usuario externo = usuario(terceiro.gestorId());
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.aprovar(cenario.pedido().id(), Long.MAX_VALUE, externo));
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Motivo", externo));
            verificarPendente(cenario);
        });
    }

    @Test
    void rejeitaPedidoAusenteEAprovacaoAusenteOuDeOutroPedido() {
        emTransacao(cenario -> {
            Usuario gestor = usuario(cenario.dados().origem().gestorId());
            verificarFalha(SOLICITACAO_NAO_ENCONTRADA,
                    () -> servico.aprovar(Long.MAX_VALUE, cenario.aprovacaoId(), gestor));
            verificarFalha(APROVACAO_NAO_ENCONTRADA,
                    () -> servico.aprovar(cenario.pedido().id(), Long.MAX_VALUE, gestor));
            banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
            var outrosDados = DadosSolicitacoesTeste.preparar(banco);
            var outroPedido = servico.criar(entrada(outrosDados), usuario(outrosDados.solicitanteId()));
            verificarFalha(APROVACAO_NAO_ENCONTRADA,
                    () -> servico.aprovar(cenario.pedido().id(), outroPedido.aprovacoes().getFirst().id(), gestor));
            verificarPendente(cenario);
        });
    }

    @Test
    void rejeitaEstadosIncompativeisEReleEntidadesPreviamenteCarregadas() {
        for (StatusSolicitacao estado : StatusSolicitacao.values()) {
            if (estado == StatusSolicitacao.AGUARDANDO_GESTOR_AREA) {
                continue;
            }
            emTransacao(cenario -> {
                aprovacoes.findById(cenario.aprovacaoId()).orElseThrow().getDecisao();
                banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", estado.name(), cenario.pedido().id());
                Usuario gestor = usuario(cenario.dados().origem().gestorId());
                verificarFalha(ESTADO_INCOMPATIVEL,
                        () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), gestor));
                verificarFalha(ESTADO_INCOMPATIVEL,
                        () -> servico.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Motivo", gestor));
                verificarAcoesSemDecisao(cenario, gestor);
                assertEquals(1L, quantidadeEventos(cenario.pedido().id()));
            });
        }
        emTransacao(cenario -> {
            aprovacoes.findById(cenario.aprovacaoId()).orElseThrow().getDecisao();
            banco.update("UPDATE aprovacao SET decisao = 'APROVADA', decidida_em = CURRENT_TIMESTAMP WHERE id = ?",
                    cenario.aprovacaoId());
            verificarFalha(ETAPA_JA_DECIDIDA, () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(),
                    usuario(cenario.dados().origem().gestorId())));
        });
    }

    @Test
    void trocaDoGestorCadastradoNaoSubstituiAprovadorExistente() {
        emTransacao(cenario -> {
            Long substitutoId = banco.queryForObject("""
                    INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id)
                    VALUES ('Novo gestor sintético', ?, ?, 'GESTOR', ?) RETURNING id
                    """, Long.class, DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO,
                    cenario.dados().origem().setorId());
            banco.update("UPDATE setor SET gestor_id = ? WHERE id = ?", substitutoId, cenario.dados().origem().setorId());
            verificarFalha(ACAO_NAO_PERMITIDA,
                    () -> servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), usuario(substitutoId)));
            var resposta = servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), usuario(cenario.dados().origem().gestorId()));
            assertEquals(cenario.dados().origem().gestorId(), resposta.aprovacoes().getFirst().aprovador().id());
            assertEquals(substitutoId, resposta.aprovacoes().getLast().aprovador().id());
            assertEquals(List.of(), resposta.acoesDisponiveis());
            assertEquals(StatusSolicitacao.APROVADA,
                    servico.aprovar(resposta.id(), resposta.aprovacoes().getLast().id(), usuario(substitutoId)).status());
        });
    }

    @Test
    void gestorInvalidoNaSegundaEtapaImpedeAvancoSemCodigoHttpInventado() {
        for (String alteracao : List.of("ativo = false", "perfil = 'COMUM'", "setor_id = %d")) {
            emTransacaoNova(dados -> {
                banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), dados.solicitanteId());
                var pedido = servico.criar(entrada(dados), usuario(dados.solicitanteId()));
                Usuario origem = usuario(dados.origem().gestorId());
                banco.update("UPDATE usuario SET " + alteracao.formatted(dados.destino().setorId()) + " WHERE id = ?", origem.getId());
                verificarFalha(GESTOR_DESIGNADO_INVALIDO, () -> servico.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(),
                        usuario(dados.destino().gestorId())));
                verificarPendente(new Cenario(dados, pedido));
            });
        }
    }

    @Test
    void capturaUmInstanteSobBloqueioEOrdenaRespostaCompleta() {
        emTransacao(cenario -> {
            // A criação já executou flush; elimina o modo herdado da inserção.
            gerenciador.clear();
            var solicitacao = solicitacoes.findById(cenario.pedido().id()).orElseThrow();
            assertEquals(jakarta.persistence.LockModeType.NONE, gerenciador.getLockMode(solicitacao));

            AtomicInteger leituras = new AtomicInteger();
            Instant instante = Instant.parse("2027-01-01T01:02:03.123456789Z");
            Clock relogio = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zona) { return this; }
                @Override public Instant instant() {
                    assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE,
                            gerenciador.getLockMode(solicitacoes.findById(cenario.pedido().id()).orElseThrow()));
                    leituras.incrementAndGet();
                    return instante;
                }
            };
            var resposta = servicoCom(aprovacoes, eventos, relogio).aprovar(cenario.pedido().id(), cenario.aprovacaoId(),
                    usuario(cenario.dados().origem().gestorId()));
            assertEquals(1, leituras.get());
            assertEquals(Instant.parse("2027-01-01T01:02:03.123456Z"), resposta.aprovacoes().getFirst().decididaEm());
            assertEquals(resposta.aprovacoes().getFirst().decididaEm(), resposta.eventos().getLast().ocorridoEm());
            var etapas = new ArrayList<>(aprovacoes.buscarPorSolicitacao(resposta.id()));
            var historico = new ArrayList<>(eventos.buscarPorSolicitacao(resposta.id()));
            Collections.reverse(etapas);
            Collections.reverse(historico);
            assertEquals(resposta, mapeador.mapear(solicitacoes.findById(resposta.id()).orElseThrow(), etapas, historico,
                    usuario(cenario.dados().origem().gestorId())));
            // Empate temporal deve ser desempatado pelo ID do evento.
            banco.update("UPDATE evento_solicitacao SET ocorrido_em = ? WHERE solicitacao_id = ?",
                    java.sql.Timestamp.from(instante), resposta.id());
            gerenciador.clear();
            var empatados = new ArrayList<>(eventos.buscarPorSolicitacao(resposta.id()));
            Collections.reverse(empatados);
            var reordenada = mapeador.mapear(solicitacoes.findById(resposta.id()).orElseThrow(),
                    aprovacoes.buscarPorSolicitacao(resposta.id()), empatados, usuario(cenario.dados().origem().gestorId()));
            assertTrue(reordenada.eventos().getFirst().id() < reordenada.eventos().getLast().id());
        });
    }

    @Test
    void falhasNaSegundaAprovacaoOuEventoRevertemDecisaoEEstado() {
        for (boolean falharEtapa : List.of(true, false)) {
            Cenario cenario = prepararConfirmado();
            Usuario ator = novaTransacao().execute(situacao -> usuario(cenario.dados().origem().gestorId()));
            RuntimeException falha = new IllegalStateException("Falha sintética depois de persistir");
            var comFalha = servicoCom(falharEtapa ? falharDepoisDeSalvar(AprovacaoRepository.class, aprovacoes, falha) : aprovacoes,
                    falharEtapa ? eventos : falharDepoisDeSalvar(EventoSolicitacaoRepository.class, eventos, falha), Clock.systemUTC());
            assertSame(falha, assertThrows(IllegalStateException.class,
                    () -> comFalha.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), ator)));
            novaTransacao().executeWithoutResult(situacao -> verificarPendente(cenario));
            assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL,
                    servico.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), ator).status());
        }
    }

    @Test
    void falhaRealDeBancoNoEventoReverteAprovacaoERecusaSemConsultasPosteriores() {
        for (boolean recusa : List.of(false, true)) {
            Cenario cenario = prepararConfirmado();
            Usuario ator = novaTransacao().execute(situacao -> usuario(cenario.dados().origem().gestorId()));
            EventoSolicitacaoRepository comFalha = EventoSolicitacaoRepository.class.cast(Proxy.newProxyInstance(
                    EventoSolicitacaoRepository.class.getClassLoader(), new Class<?>[] {EventoSolicitacaoRepository.class},
                    (procurador, metodo, argumentos) -> {
                        if (metodo.getName().equals("save")) {
                            solicitacoes.flush();
                            banco.update("""
                                    INSERT INTO evento_solicitacao (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                                    VALUES (?, ?, 'APROVACAO', 'APROVADA', CURRENT_TIMESTAMP)
                                    """, ((EventoSolicitacao) argumentos[0]).getSolicitacao().getId(), Long.MAX_VALUE);
                            fail("A FK deve rejeitar o autor inexistente");
                        }
                        if (metodo.getName().equals("buscarPorSolicitacao")) {
                            fail("Não consultar depois de falha de persistência");
                        }
                        return invocar(eventos, metodo, argumentos);
                    }));
            var servicoComFalha = servicoCom(aprovacoes, comFalha, Clock.systemUTC());
            assertThrows(DataIntegrityViolationException.class, () -> {
                if (recusa) {
                    servicoComFalha.recusar(cenario.pedido().id(), cenario.aprovacaoId(), "Motivo", ator);
                } else {
                    servicoComFalha.aprovar(cenario.pedido().id(), cenario.aprovacaoId(), ator);
                }
            });
            novaTransacao().executeWithoutResult(situacao -> verificarPendente(cenario));
        }
    }

    private void verificarHistorico(Cenario cenario, SolicitacaoResponse resposta, TipoEventoSolicitacao tipo) {
        assertEquals(cenario.pedido().numero(), resposta.numero());
        assertEquals(cenario.pedido().criadaEm(), resposta.criadaEm());
        assertEquals(cenario.pedido().justificativa(), resposta.justificativa());
        assertEquals(cenario.pedido().solicitante(), resposta.solicitante());
        assertEquals(cenario.pedido().origem(), resposta.origem());
        assertEquals(cenario.pedido().destino(), resposta.destino());
        assertEquals(2, resposta.eventos().size());
        assertEquals(cenario.pedido().eventos().getFirst(), resposta.eventos().getFirst());
        var evento = resposta.eventos().getLast();
        assertEquals(tipo, evento.tipo());
        assertEquals(cenario.dados().origem().gestorId(), evento.autor().id());
        assertEquals(cenario.pedido().status(), evento.statusAnterior());
        assertEquals(resposta.status(), evento.statusNovo());
        assertEquals(resposta.aprovacoes().getFirst().decididaEm(), evento.ocorridoEm());
        assertNull(resposta.envio());
        assertNull(resposta.recebimento());
    }

    private void verificarPendente(Cenario cenario) {
        gerenciador.clear();
        var pedido = solicitacoes.findById(cenario.pedido().id()).orElseThrow();
        assertEquals(StatusSolicitacao.AGUARDANDO_GESTOR_AREA, pedido.getStatus());
        var etapa = aprovacoes.findById(cenario.aprovacaoId()).orElseThrow();
        assertEquals(DecisaoAprovacao.PENDENTE, etapa.getDecisao());
        assertNull(etapa.getDecididaEm());
        assertNull(etapa.getJustificativa());
        assertEquals(1, aprovacoes.buscarPorSolicitacao(pedido.getId()).size());
        assertEquals(1L, quantidadeEventos(pedido.getId()));
        verificarBemInalterado(cenario.dados());
    }

    private void verificarAcoesSemDecisao(Cenario cenario, Usuario ator) {
        var resposta = mapeador.mapear(solicitacoes.findById(cenario.pedido().id()).orElseThrow(),
                aprovacoes.buscarPorSolicitacao(cenario.pedido().id()), eventos.buscarPorSolicitacao(cenario.pedido().id()), ator);
        assertFalse(resposta.acoesDisponiveis().contains("APROVAR"));
        assertFalse(resposta.acoesDisponiveis().contains("RECUSAR"));
    }

    private void verificarBemInalterado(DadosSolicitacoesTeste dados) {
        Map<String, Object> bem = banco.queryForMap(
                "SELECT setor_id, localizacao_id, responsavel_id FROM bem_patrimonial WHERE id = ?", dados.origem().bemId());
        assertEquals(dados.origem().setorId(), bem.get("setor_id"));
        assertEquals(dados.origem().localizacaoId(), bem.get("localizacao_id"));
        assertEquals(dados.origem().gestorId(), bem.get("responsavel_id"));
    }

    private long quantidadeEventos(Long solicitacaoId) {
        return banco.queryForObject("SELECT count(*) FROM evento_solicitacao WHERE solicitacao_id = ?", Long.class, solicitacaoId);
    }

    private ExcecaoDecisaoAprovacao verificarFalha(MotivoFalhaDecisaoAprovacao motivo, Runnable operacao) {
        var falha = assertThrows(ExcecaoDecisaoAprovacao.class, operacao::run);
        assertEquals(motivo, falha.getMotivo());
        return falha;
    }

    private void emTransacao(Consumer<Cenario> verificacao) {
        emTransacaoNova(dados -> verificacao.accept(criarCenario(dados)));
    }

    private void emTransacaoNova(Consumer<DadosSolicitacoesTeste> verificacao) {
        novaTransacao().executeWithoutResult(situacao -> {
            situacao.setRollbackOnly();
            verificacao.accept(DadosSolicitacoesTeste.preparar(banco));
        });
    }

    private Cenario prepararConfirmado() {
        // Fixtures confirmadas permanecem em simpi_test, sem limpeza destrutiva.
        return novaTransacao().execute(situacao -> criarCenario(DadosSolicitacoesTeste.preparar(banco)));
    }

    private Cenario criarCenario(DadosSolicitacoesTeste dados) {
        return new Cenario(dados, servico.criar(entrada(dados), usuario(dados.solicitanteId())));
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

    private SolicitacaoService servicoCom(AprovacaoRepository etapas, EventoSolicitacaoRepository historico, Clock relogio) {
        var alvo = new SolicitacaoService(usuarios, bens, setores, localizacoes, solicitacoes,
                etapas, historico, numeros, mapeador, gerenciador, relogio);
        var fabrica = new ProxyFactory(alvo);
        fabrica.addAdvice(new TransactionInterceptor(transacoes, new AnnotationTransactionAttributeSource()));
        return (SolicitacaoService) fabrica.getProxy();
    }

    private <T> T falharDepoisDeSalvar(Class<T> tipo, T repositorio, RuntimeException falha) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[] {tipo},
                (procurador, metodo, argumentos) -> {
                    Object resultado = invocar(repositorio, metodo, argumentos);
                    if (metodo.getName().equals("save")) {
                        solicitacoes.flush();
                        throw falha;
                    }
                    return resultado;
                }));
    }

    private Object invocar(Object alvo, java.lang.reflect.Method metodo, Object[] argumentos) throws Throwable {
        try {
            return metodo.invoke(alvo, argumentos);
        } catch (InvocationTargetException falha) {
            throw falha.getCause();
        }
    }

    private record Cenario(DadosSolicitacoesTeste dados, SolicitacaoResponse pedido) {
        Long aprovacaoId() { return pedido.aprovacoes().getFirst().id(); }
    }
}
