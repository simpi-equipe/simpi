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
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
