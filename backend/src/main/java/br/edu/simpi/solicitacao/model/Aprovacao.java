package br.edu.simpi.solicitacao.model;

import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;

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
@Table(name = "aprovacao")
public class Aprovacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gerador_aprovacao")
    @SequenceGenerator(name = "gerador_aprovacao", sequenceName = "sequencia_aprovacao", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id", nullable = false, updatable = false)
    private Solicitacao solicitacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false, updatable = false, length = 40)
    private EtapaAprovacao etapa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aprovador_id", nullable = false, updatable = false)
    private Usuario aprovador;

    @Enumerated(EnumType.STRING)
    @Column(name = "decisao", nullable = false, length = 40)
    private DecisaoAprovacao decisao;

    @Column(name = "justificativa", nullable = true, columnDefinition = "text")
    private String justificativa;

    @Column(name = "decidida_em", nullable = true, columnDefinition = "timestamp with time zone")
    private Instant decididaEm;

    protected Aprovacao() {
    }

    public Aprovacao(Solicitacao solicitacao, EtapaAprovacao etapa, Usuario aprovador) {
        this.solicitacao = Objects.requireNonNull(solicitacao, "Campo obrigatório: solicitacao");
        this.etapa = Objects.requireNonNull(etapa, "Campo obrigatório: etapa");
        this.aprovador = Objects.requireNonNull(aprovador, "Campo obrigatório: aprovador");
        this.decisao = DecisaoAprovacao.PENDENTE;
    }

    public Long getId() {
        return id;
    }

    public void aprovar(Instant instante) {
        validarPendente();
        this.decididaEm = Objects.requireNonNull(instante, "Instante obrigatório");
        this.decisao = DecisaoAprovacao.APROVADA;
    }

    public void recusar(String justificativa, Instant instante) {
        validarPendente();
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Informe a justificativa da recusa.");
        }
        this.decididaEm = Objects.requireNonNull(instante, "Instante obrigatório");
        this.justificativa = justificativa.strip();
        this.decisao = DecisaoAprovacao.RECUSADA;
    }

    private void validarPendente() {
        if (decisao != DecisaoAprovacao.PENDENTE) {
            throw new IllegalStateException("Etapa já decidida.");
        }
    }

    public Solicitacao getSolicitacao() {
        return solicitacao;
    }

    public EtapaAprovacao getEtapa() {
        return etapa;
    }

    public Usuario getAprovador() {
        return aprovador;
    }

    public DecisaoAprovacao getDecisao() {
        return decisao;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public Instant getDecididaEm() {
        return decididaEm;
    }
}
