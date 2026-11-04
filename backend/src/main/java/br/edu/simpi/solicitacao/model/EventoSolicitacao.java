package br.edu.simpi.solicitacao.model;

import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;

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
@Table(name = "evento_solicitacao")
public class EventoSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gerador_evento_solicitacao")
    @SequenceGenerator(name = "gerador_evento_solicitacao", sequenceName = "sequencia_evento_solicitacao", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id", nullable = false, updatable = false)
    private Solicitacao solicitacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 40)
    private TipoEventoSolicitacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", nullable = true, updatable = false, length = 40)
    private StatusSolicitacao statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, updatable = false, length = 40)
    private StatusSolicitacao statusNovo;

    @Column(name = "descricao", nullable = true, updatable = false, columnDefinition = "text")
    private String descricao;

    @Column(name = "ocorrido_em", nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant ocorridoEm;

    protected EventoSolicitacao() {
    }

    public EventoSolicitacao(Solicitacao solicitacao, Usuario autor, TipoEventoSolicitacao tipo,
            StatusSolicitacao statusAnterior, StatusSolicitacao statusNovo, String descricao, Instant ocorridoEm) {
        this.solicitacao = Objects.requireNonNull(solicitacao, "Campo obrigatório: solicitacao");
        this.autor = Objects.requireNonNull(autor, "Campo obrigatório: autor");
        this.tipo = Objects.requireNonNull(tipo, "Campo obrigatório: tipo");
        this.statusAnterior = statusAnterior;
        this.statusNovo = Objects.requireNonNull(statusNovo, "Campo obrigatório: statusNovo");
        this.descricao = descricao;
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "Campo obrigatório: ocorridoEm");
    }

    public Long getId() {
        return id;
    }

    public Solicitacao getSolicitacao() {
        return solicitacao;
    }

    public Usuario getAutor() {
        return autor;
    }

    public TipoEventoSolicitacao getTipo() {
        return tipo;
    }

    public StatusSolicitacao getStatusAnterior() {
        return statusAnterior;
    }

    public StatusSolicitacao getStatusNovo() {
        return statusNovo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
