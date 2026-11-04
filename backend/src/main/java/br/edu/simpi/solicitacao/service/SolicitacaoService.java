package br.edu.simpi.solicitacao.service;

import br.edu.simpi.bem.BemPatrimonial;
import br.edu.simpi.bem.BemPatrimonialRepository;
import br.edu.simpi.localizacao.Localizacao;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao.*;

@Service
public class SolicitacaoService {
    private static final List<StatusSolicitacao> ESTADOS_ATIVOS = List.of(
            StatusSolicitacao.AGUARDANDO_GESTOR_AREA, StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL,
            StatusSolicitacao.APROVADA, StatusSolicitacao.EM_TRANSITO);

    private final UsuarioRepository usuarios;
    private final BemPatrimonialRepository bens;
    private final SetorRepository setores;
    private final LocalizacaoRepository localizacoes;
    private final SolicitacaoRepository solicitacoes;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final NumeroSolicitacaoService numeros;
    private final SolicitacaoMapper mapeador;
    private final EntityManager gerenciador;
    private final Clock relogio;

    @Autowired
    public SolicitacaoService(UsuarioRepository usuarios, BemPatrimonialRepository bens,
            SetorRepository setores, LocalizacaoRepository localizacoes, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos,
            NumeroSolicitacaoService numeros, SolicitacaoMapper mapeador, EntityManager gerenciador) {
        this(usuarios, bens, setores, localizacoes, solicitacoes, aprovacoes, eventos,
                numeros, mapeador, gerenciador, Clock.systemUTC());
    }

    // Permite controlar o instante sem configuração global ou dependência de autenticação.
    public SolicitacaoService(UsuarioRepository usuarios, BemPatrimonialRepository bens,
            SetorRepository setores, LocalizacaoRepository localizacoes, SolicitacaoRepository solicitacoes,
            AprovacaoRepository aprovacoes, EventoSolicitacaoRepository eventos,
            NumeroSolicitacaoService numeros, SolicitacaoMapper mapeador, EntityManager gerenciador, Clock relogio) {
        this.usuarios = usuarios;
        this.bens = bens;
        this.setores = setores;
        this.localizacoes = localizacoes;
        this.solicitacoes = solicitacoes;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.numeros = numeros;
        this.mapeador = mapeador;
        this.gerenciador = gerenciador;
        this.relogio = relogio;
    }

    /** O solicitante deve vir da integração interna confiável, nunca do corpo HTTP. */
    @Transactional
    public SolicitacaoResponse criar(CriarSolicitacaoRequest entrada, Usuario solicitante) {
        validarEntrada(entrada);
        Usuario solicitanteAtual = recarregarSolicitante(solicitante);
        BemPatrimonial bem = bens.buscarPorIdParaAtualizacao(entrada.bemId())
                .orElseThrow(() -> referenciaAusente("bemId"));
        // A consulta com lock pode encontrar uma entidade já presente no contexto JPA.
        gerenciador.refresh(bem);
        validarAtivo(bem.isAtivo(), "bemId");
        if (solicitacoes.existePorBemEEstados(bem.getId(), ESTADOS_ATIVOS)) {
            throw conflito(null);
        }

        Setor setorDestino = setores.findById(entrada.setorDestinoId())
                .orElseThrow(() -> referenciaAusente("setorDestinoId"));
        Localizacao localizacaoDestino = localizacoes.findById(entrada.localizacaoDestinoId())
                .orElseThrow(() -> referenciaAusente("localizacaoDestinoId"));
        Usuario responsavelDestino = usuarios.findById(entrada.responsavelDestinoId())
                .orElseThrow(() -> referenciaAusente("responsavelDestinoId"));
        validarDestino(bem, setorDestino, localizacaoDestino, responsavelDestino);

        boolean solicitanteComum = solicitanteAtual.getPerfil() == PerfilUsuario.COMUM;
        EtapaAprovacao etapa = solicitanteComum ? EtapaAprovacao.GESTOR_AREA : EtapaAprovacao.GESTOR_RESPONSAVEL;
        StatusSolicitacao estado = solicitanteComum
                ? StatusSolicitacao.AGUARDANDO_GESTOR_AREA : StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL;
        Setor setorAprovador = solicitanteComum ? solicitanteAtual.getSetor() : bem.getSetor();
        Usuario aprovador = validarGestorDesignado(setorAprovador);
        Instant referenciaTemporal = relogio.instant();
        var numero = numeros.gerar(referenciaTemporal);
        Solicitacao solicitacao = new Solicitacao(numero, bem, solicitanteAtual,
                setorDestino, localizacaoDestino, responsavelDestino, entrada.justificativa().strip(), estado);
        Aprovacao aprovacao = new Aprovacao(solicitacao, etapa, aprovador);
        String descricao = solicitanteComum ? "Solicitação encaminhada ao gestor da área."
                : "Solicitação encaminhada ao gestor responsável.";
        EventoSolicitacao evento = new EventoSolicitacao(solicitacao, solicitanteAtual,
                TipoEventoSolicitacao.CRIACAO, null, estado, descricao, numero.criadaEm());
        try {
            solicitacoes.save(solicitacao);
            aprovacoes.save(aprovacao);
            eventos.save(evento);
            solicitacoes.flush();
        } catch (DataIntegrityViolationException excecao) {
            if (violouIndiceDeBemAtivo(excecao)) {
                throw conflito(excecao);
            }
            throw excecao;
        }
        return mapeador.mapearCriacao(solicitacao, aprovacao, evento, solicitanteAtual);
    }

    /** O ator é fornecido pela integração interna confiável, nunca pelo corpo HTTP. */
    @Transactional
    public SolicitacaoResponse aprovar(Long solicitacaoId, Long aprovacaoId, Usuario ator) {
        return decidir(solicitacaoId, aprovacaoId, ator, null, false);
    }

    /** A justificativa é validada também em chamadas diretas ao service. */
    @Transactional
    public SolicitacaoResponse recusar(Long solicitacaoId, Long aprovacaoId, String justificativa, Usuario ator) {
        if (justificativa == null || justificativa.isBlank()) {
            throw falhaDecisao(MotivoFalhaDecisaoAprovacao.DADOS_INVALIDOS,
                    "Informe a justificativa.", Map.of("justificativa", "Informe a justificativa."));
        }
        return decidir(solicitacaoId, aprovacaoId, ator, justificativa.strip(), true);
    }

    /** O ator vem da integração interna confiável; o motivo não altera a justificativa inicial. */
    @Transactional
    public SolicitacaoResponse cancelar(Long solicitacaoId, String motivo, Usuario ator) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (solicitacaoId == null || solicitacaoId <= 0) {
            erros.put("solicitacaoId", "Informe um identificador positivo.");
        }
        if (motivo == null || motivo.isBlank()) {
            erros.put("motivo", "Informe o motivo do cancelamento.");
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoCancelamentoSolicitacao(MotivoFalhaCancelamentoSolicitacao.DADOS_INVALIDOS,
                    "Revise os campos informados.", erros);
        }
        recarregarAtorCancelamento(ator);
        Solicitacao solicitacao = solicitacoes.buscarPorIdParaAtualizacao(solicitacaoId)
                .orElseThrow(() -> falhaCancelamento(MotivoFalhaCancelamentoSolicitacao.SOLICITACAO_NAO_ENCONTRADA));
        gerenciador.refresh(solicitacao, LockModeType.PESSIMISTIC_WRITE);
        Usuario atorAtual = recarregarAtorCancelamento(ator);
        RegrasCancelamentoSolicitacao.avaliar(solicitacao, atorAtual)
                .ifPresent(impedimento -> { throw falhaCancelamento(impedimento); });

        Instant instante = relogio.instant().truncatedTo(ChronoUnit.MICROS);
        StatusSolicitacao estadoAnterior = solicitacao.getStatus();
        solicitacao.cancelar();
        eventos.save(new EventoSolicitacao(solicitacao, atorAtual, TipoEventoSolicitacao.CANCELAMENTO,
                estadoAnterior, solicitacao.getStatus(), motivo.strip(), instante));
        // Sem lock no bem: a criação já usa bem -> verificação de pedido ativo.
        // Propagar falhas imediatamente; nenhuma consulta após erro de persistência.
        solicitacoes.flush();
        // Uma aprovação concorrente pode ter sido confirmada enquanto aguardávamos o lock.
        List<Aprovacao> etapas = aprovacoes.buscarPorSolicitacao(solicitacaoId);
        etapas.forEach(gerenciador::refresh);
        return mapeador.mapear(solicitacao, etapas,
                eventos.buscarPorSolicitacao(solicitacaoId), atorAtual);
    }

    private Usuario recarregarAtorCancelamento(Usuario ator) {
        return recarregarUsuarioAtivo(ator)
                .orElseThrow(() -> falhaCancelamento(MotivoFalhaCancelamentoSolicitacao.NAO_AUTENTICADO));
    }

    private ExcecaoCancelamentoSolicitacao falhaCancelamento(MotivoFalhaCancelamentoSolicitacao motivo) {
        String mensagem = switch (motivo) {
            case DADOS_INVALIDOS -> "Revise os campos informados.";
            case NAO_AUTENTICADO -> "Usuário interno ausente, inexistente ou inativo.";
            case ACAO_NAO_PERMITIDA -> "Ação não permitida para este usuário.";
            case SOLICITACAO_NAO_ENCONTRADA -> "Solicitação não encontrada.";
            case ESTADO_INCOMPATIVEL -> "O pedido não pode ser cancelado neste estado.";
        };
        return new ExcecaoCancelamentoSolicitacao(motivo, mensagem, Map.of());
    }

    private SolicitacaoResponse decidir(Long solicitacaoId, Long aprovacaoId, Usuario ator,
            String justificativa, boolean recusa) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (solicitacaoId == null || solicitacaoId <= 0) {
            erros.put("solicitacaoId", "Informe um identificador positivo.");
        }
        if (aprovacaoId == null || aprovacaoId <= 0) {
            erros.put("aprovacaoId", "Informe um identificador positivo.");
        }
        if (!erros.isEmpty()) {
            throw falhaDecisao(MotivoFalhaDecisaoAprovacao.DADOS_INVALIDOS, "Revise os campos informados.", erros);
        }
        recarregarAtor(ator);
        Solicitacao solicitacao = solicitacoes.buscarPorIdParaAtualizacao(solicitacaoId)
                .orElseThrow(() -> falhaDecisao(MotivoFalhaDecisaoAprovacao.SOLICITACAO_NAO_ENCONTRADA));
        // O lock serializa decisões; refresh também cobre entidades já carregadas pelo chamador.
        gerenciador.refresh(solicitacao, LockModeType.PESSIMISTIC_WRITE);
        Usuario atorAtual = recarregarAtor(ator);
        if (!RegrasDecisaoAprovacao.temAcesso(solicitacao, atorAtual)) {
            throw falhaDecisao(MotivoFalhaDecisaoAprovacao.ACAO_NAO_PERMITIDA);
        }
        Aprovacao aprovacao = aprovacoes.buscarPorIdESolicitacao(aprovacaoId, solicitacaoId)
                .orElseThrow(() -> falhaDecisao(MotivoFalhaDecisaoAprovacao.APROVACAO_NAO_ENCONTRADA));
        gerenciador.refresh(aprovacao);
        RegrasDecisaoAprovacao.avaliar(solicitacao, aprovacao, atorAtual)
                .ifPresent(motivo -> { throw falhaDecisao(motivo); });

        Aprovacao proxima = !recusa && aprovacao.getEtapa() == EtapaAprovacao.GESTOR_AREA
                ? new Aprovacao(solicitacao, EtapaAprovacao.GESTOR_RESPONSAVEL,
                        gestorDaSegundaEtapa(solicitacao)) : null;
        // Capturado somente depois do lock e das revalidações; uma leitura por decisão.
        Instant instante = relogio.instant().truncatedTo(ChronoUnit.MICROS);
        StatusSolicitacao estadoAnterior = solicitacao.getStatus();
        if (recusa) {
            aprovacao.recusar(justificativa, instante);
            solicitacao.recusarEtapa(aprovacao.getEtapa());
        } else {
            aprovacao.aprovar(instante);
            solicitacao.aprovarEtapa(aprovacao.getEtapa());
        }
        if (proxima != null) {
            aprovacoes.save(proxima);
        }
        eventos.save(new EventoSolicitacao(solicitacao, atorAtual,
                recusa ? TipoEventoSolicitacao.RECUSA : TipoEventoSolicitacao.APROVACAO,
                estadoAnterior, solicitacao.getStatus(),
                recusa ? justificativa : "Etapa " + aprovacao.getEtapa() + " aprovada.", instante));
        // Não capturar falhas de persistência: a transação deve terminar com rollback.
        solicitacoes.flush();
        return mapeador.mapear(solicitacao, aprovacoes.buscarPorSolicitacao(solicitacaoId),
                eventos.buscarPorSolicitacao(solicitacaoId), atorAtual);
    }

    private Usuario recarregarAtor(Usuario ator) {
        return recarregarUsuarioAtivo(ator)
                .orElseThrow(() -> falhaDecisao(MotivoFalhaDecisaoAprovacao.NAO_AUTENTICADO));
    }

    private Optional<Usuario> recarregarUsuarioAtivo(Usuario ator) {
        if (ator == null || ator.getId() == null) {
            return Optional.empty();
        }
        return usuarios.findById(ator.getId()).map(atual -> {
            gerenciador.refresh(atual);
            return atual;
        }).filter(Usuario::isAtivo);
    }

    private Usuario gestorDaSegundaEtapa(Solicitacao solicitacao) {
        Setor origem = solicitacao.getSetorOrigem();
        gerenciador.refresh(origem);
        Usuario gestor = origem.getGestor();
        if (gestor == null) {
            throw falhaDecisao(MotivoFalhaDecisaoAprovacao.GESTOR_DESIGNADO_INVALIDO);
        }
        gerenciador.refresh(gestor);
        if (!gestor.isAtivo() || gestor.getPerfil() != PerfilUsuario.GESTOR
                || !origem.getId().equals(gestor.getSetor().getId())) {
            throw falhaDecisao(MotivoFalhaDecisaoAprovacao.GESTOR_DESIGNADO_INVALIDO);
        }
        return gestor;
    }

    private ExcecaoDecisaoAprovacao falhaDecisao(MotivoFalhaDecisaoAprovacao motivo) {
        String mensagem = switch (motivo) {
            case NAO_AUTENTICADO -> "Usuário interno ausente, inexistente ou inativo.";
            case ACAO_NAO_PERMITIDA -> "Ação não permitida para este usuário.";
            case SOLICITACAO_NAO_ENCONTRADA -> "Solicitação não encontrada.";
            case APROVACAO_NAO_ENCONTRADA -> "Aprovação não encontrada para a solicitação.";
            case ETAPA_JA_DECIDIDA -> "Etapa já decidida.";
            case ESTADO_INCOMPATIVEL -> "O pedido não aguarda esta etapa.";
            case GESTOR_DESIGNADO_INVALIDO -> "Gestor designado inválido.";
            case DADOS_INVALIDOS -> "Revise os campos informados.";
        };
        return falhaDecisao(motivo, mensagem, Map.of());
    }

    private ExcecaoDecisaoAprovacao falhaDecisao(MotivoFalhaDecisaoAprovacao motivo,
            String mensagem, Map<String, String> erros) {
        return new ExcecaoDecisaoAprovacao(motivo, mensagem, erros);
    }

    private void validarEntrada(CriarSolicitacaoRequest entrada) {
        if (entrada == null) {
            throw falha(DADOS_INVALIDOS, "Informe os dados da solicitação.", Map.of());
        }
        Map<String, String> erros = new LinkedHashMap<>();
        validarId(entrada.bemId(), "bemId", erros);
        validarId(entrada.setorDestinoId(), "setorDestinoId", erros);
        validarId(entrada.localizacaoDestinoId(), "localizacaoDestinoId", erros);
        validarId(entrada.responsavelDestinoId(), "responsavelDestinoId", erros);
        if (entrada.justificativa() == null || entrada.justificativa().isBlank()) {
            erros.put("justificativa", "Informe a justificativa.");
        }
        if (!erros.isEmpty()) {
            throw falha(DADOS_INVALIDOS, "Revise os campos informados.", erros);
        }
    }

    private void validarId(Long id, String campo, Map<String, String> erros) {
        if (id == null) {
            erros.put(campo, "Informe o identificador.");
        }
    }

    private Usuario recarregarSolicitante(Usuario solicitante) {
        if (solicitante == null || solicitante.getId() == null) {
            throw falha(SOLICITANTE_INVALIDO, "Solicitante interno inválido.", Map.of());
        }
        Usuario atual = usuarios.findById(solicitante.getId())
                .orElseThrow(() -> falha(SOLICITANTE_INVALIDO, "Solicitante não encontrado.", Map.of()));
        gerenciador.refresh(atual);
        if (!atual.isAtivo()) {
            throw falha(SOLICITANTE_INATIVO, "Solicitante inativo.", Map.of());
        }
        if (atual.getPerfil() != PerfilUsuario.COMUM && atual.getPerfil() != PerfilUsuario.GESTOR) {
            throw falha(PERFIL_NAO_PERMITIDO, "Perfil sem permissão para criar solicitações.", Map.of());
        }
        return atual;
    }

    private void validarDestino(BemPatrimonial bem, Setor setor, Localizacao localizacao, Usuario responsavel) {
        validarAtivo(setor.isAtivo(), "setorDestinoId");
        validarAtivo(localizacao.isAtivo(), "localizacaoDestinoId");
        validarAtivo(responsavel.isAtivo(), "responsavelDestinoId");
        if (!responsavel.getSetor().getId().equals(setor.getId())) {
            throw falha(DADOS_INVALIDOS, "Responsável não pertence ao setor de destino.",
                    Map.of("responsavelDestinoId", "Selecione um responsável do setor de destino."));
        }
        if (bem.getSetor().getId().equals(setor.getId())
                && bem.getLocalizacao().getId().equals(localizacao.getId())
                && bem.getResponsavel().getId().equals(responsavel.getId())) {
            throw falha(DADOS_INVALIDOS, "O destino deve diferir da origem.", Map.of());
        }
    }

    private Usuario validarGestorDesignado(Setor setor) {
        Usuario gestor = setor.getGestor();
        if (gestor == null || !gestor.isAtivo() || gestor.getPerfil() != PerfilUsuario.GESTOR
                || !setor.getId().equals(gestor.getSetor().getId())) {
            throw falha(GESTOR_DESIGNADO_INVALIDO, "Gestor designado inválido.", Map.of());
        }
        return gestor;
    }

    private void validarAtivo(boolean ativo, String campo) {
        if (!ativo) {
            throw falha(DADOS_INVALIDOS, "Referência inativa.", Map.of(campo, "Selecione um registro ativo."));
        }
    }

    private ExcecaoCriacaoSolicitacao referenciaAusente(String campo) {
        return falha(REFERENCIA_NAO_ENCONTRADA, "Referência não encontrada.",
                Map.of(campo, "Registro não encontrado."));
    }

    private ExcecaoCriacaoSolicitacao falha(MotivoFalhaCriacaoSolicitacao motivo,
            String mensagem, Map<String, String> erros) {
        return new ExcecaoCriacaoSolicitacao(motivo, mensagem, erros);
    }

    private ExcecaoCriacaoSolicitacao conflito(Throwable causa) {
        return new ExcecaoCriacaoSolicitacao(BEM_COM_SOLICITACAO_EM_ANDAMENTO,
                "O bem possui solicitação em andamento.", Map.of(), causa);
    }

    private boolean violouIndiceDeBemAtivo(Throwable excecao) {
        for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacao
                    && "23505".equals(violacao.getSQLState())
                    && "uq_solicitacao_bem_ativa".equals(violacao.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
