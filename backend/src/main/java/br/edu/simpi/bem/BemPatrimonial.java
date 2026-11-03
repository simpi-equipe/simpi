package br.edu.simpi.bem;

import br.edu.simpi.localizacao.Localizacao;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "bem_patrimonial")
public class BemPatrimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gerador_bem_patrimonial")
    @SequenceGenerator(name = "gerador_bem_patrimonial", sequenceName = "sequencia_bem_patrimonial", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String codigo;

    @Column(length = 50)
    private String plaqueta;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setor_id", nullable = false)
    private Setor setor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "localizacao_id", nullable = false)
    private Localizacao localizacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @Column(nullable = false)
    private boolean ativo = true;

    protected BemPatrimonial() {
    }

    public BemPatrimonial(String codigo, String plaqueta, String descricao, Setor setor,
            Localizacao localizacao, Usuario responsavel) {
        this.codigo = Objects.requireNonNull(codigo, "Campo obrigatório: codigo");
        this.plaqueta = plaqueta;
        this.descricao = Objects.requireNonNull(descricao, "Campo obrigatório: descricao");
        this.setor = Objects.requireNonNull(setor, "Campo obrigatório: setor");
        this.localizacao = Objects.requireNonNull(localizacao, "Campo obrigatório: localizacao");
        this.responsavel = Objects.requireNonNull(responsavel, "Campo obrigatório: responsavel");
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getPlaqueta() {
        return plaqueta;
    }

    public String getDescricao() {
        return descricao;
    }

    public Setor getSetor() {
        return setor;
    }

    public Localizacao getLocalizacao() {
        return localizacao;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
