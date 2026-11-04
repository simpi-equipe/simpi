package br.edu.simpi.solicitacao.model;

import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;

import br.edu.simpi.bem.BemPatrimonial;
import br.edu.simpi.localizacao.Localizacao;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "solicitacao")
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gerador_solicitacao")
    @SequenceGenerator(name = "gerador_solicitacao", sequenceName = "sequencia_solicitacao", allocationSize = 1)
    private Long id;

    @Column(name = "numero", nullable = false, updatable = false, length = 28, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bem_id", nullable = false, updatable = false)
    private BemPatrimonial bem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false, updatable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_solicitante_id", nullable = false, updatable = false)
    private Setor setorSolicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_origem_id", nullable = false, updatable = false)
    private Setor setorOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "localizacao_origem_id", nullable = false, updatable = false)
    private Localizacao localizacaoOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_origem_id", nullable = false, updatable = false)
    private Usuario responsavelOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_destino_id", nullable = false, updatable = false)
    private Setor setorDestino;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "localizacao_destino_id", nullable = false, updatable = false)
    private Localizacao localizacaoDestino;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_destino_id", nullable = false, updatable = false)
    private Usuario responsavelDestino;

    @Column(name = "justificativa", nullable = false, updatable = false, columnDefinition = "text")
    private String justificativa;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusSolicitacao status;

    @Column(name = "criada_em", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant criadaEm;

    @Column(name = "enviada_em", nullable = true, columnDefinition = "timestamp with time zone")
    private Instant enviadaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "enviada_por_id", nullable = true)
    private Usuario enviadaPor;

    @Column(name = "recebida_em", nullable = true, columnDefinition = "timestamp with time zone")
    private Instant recebidaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "recebida_por_id", nullable = true)
    private Usuario recebidaPor;

    protected Solicitacao() {
    }

    public Solicitacao(NumeroSolicitacao numero, BemPatrimonial bem, Usuario solicitante,
            Setor setorDestino, Localizacao localizacaoDestino, Usuario responsavelDestino,
            String justificativa, StatusSolicitacao status) {
        Objects.requireNonNull(numero, "Campo obrigatório: numero");
        this.criadaEm = numero.criadaEm();
        this.numero = numero.valor();
        this.bem = Objects.requireNonNull(bem, "Campo obrigatório: bem");
        this.solicitante = Objects.requireNonNull(solicitante, "Campo obrigatório: solicitante");
        this.setorSolicitante = solicitante.getSetor();
        this.setorOrigem = bem.getSetor();
        this.localizacaoOrigem = bem.getLocalizacao();
        this.responsavelOrigem = bem.getResponsavel();
        this.setorDestino = Objects.requireNonNull(setorDestino, "Campo obrigatório: setorDestino");
        this.localizacaoDestino = Objects.requireNonNull(localizacaoDestino, "Campo obrigatório: localizacaoDestino");
        this.responsavelDestino = Objects.requireNonNull(responsavelDestino, "Campo obrigatório: responsavelDestino");
        this.justificativa = Objects.requireNonNull(justificativa, "Campo obrigatório: justificativa");
        this.status = Objects.requireNonNull(status, "Campo obrigatório: status");
    }

    public Long getId() {
        return id;
    }

    public void aprovarEtapa(EtapaAprovacao etapa) {
        validarEtapaAguardada(etapa);
        this.status = etapa == EtapaAprovacao.GESTOR_AREA
                ? StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL : StatusSolicitacao.APROVADA;
    }

    public void recusarEtapa(EtapaAprovacao etapa) {
        validarEtapaAguardada(etapa);
        this.status = StatusSolicitacao.RECUSADA;
    }

    private void validarEtapaAguardada(EtapaAprovacao etapa) {
        Objects.requireNonNull(etapa, "Etapa obrigatória");
        StatusSolicitacao esperado = etapa == EtapaAprovacao.GESTOR_AREA
                ? StatusSolicitacao.AGUARDANDO_GESTOR_AREA : StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL;
        if (status != esperado) {
            throw new IllegalStateException("O pedido não aguarda esta etapa.");
        }
    }

    public String getNumero() {
        return numero;
    }

    public BemPatrimonial getBem() {
        return bem;
    }

    public Usuario getSolicitante() {
        return solicitante;
    }

    public Setor getSetorSolicitante() {
        return setorSolicitante;
    }

    public Setor getSetorOrigem() {
        return setorOrigem;
    }

    public Localizacao getLocalizacaoOrigem() {
        return localizacaoOrigem;
    }

    public Usuario getResponsavelOrigem() {
        return responsavelOrigem;
    }

    public Setor getSetorDestino() {
        return setorDestino;
    }

    public Localizacao getLocalizacaoDestino() {
        return localizacaoDestino;
    }

    public Usuario getResponsavelDestino() {
        return responsavelDestino;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getEnviadaEm() {
        return enviadaEm;
    }

    public Usuario getEnviadaPor() {
        return enviadaPor;
    }

    public Instant getRecebidaEm() {
        return recebidaEm;
    }

    public Usuario getRecebidaPor() {
        return recebidaPor;
    }
}
