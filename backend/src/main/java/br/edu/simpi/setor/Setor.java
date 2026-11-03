package br.edu.simpi.setor;

import br.edu.simpi.usuario.PerfilUsuario;
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
@Table(name = "setor")
public class Setor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gerador_setor")
    @SequenceGenerator(name = "gerador_setor", sequenceName = "sequencia_setor", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestor_id", nullable = false)
    private Usuario gestor;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Setor() {
    }

    // A carga inicial circular é preparada por SQL transacional; não por cascatas JPA.
    public void designarGestor(Usuario gestor) {
        Objects.requireNonNull(gestor, "Gestor obrigatório");
        if (gestor.getPerfil() != PerfilUsuario.GESTOR) {
            throw new IllegalArgumentException("O gestor deve possuir perfil GESTOR");
        }
        if (getId() == null || !getId().equals(gestor.getSetor().getId())) {
            throw new IllegalArgumentException("O gestor deve pertencer ao setor gerenciado");
        }
        this.gestor = gestor;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public Usuario getGestor() {
        return gestor;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
