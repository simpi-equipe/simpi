package br.edu.simpi.usuario;

import br.edu.simpi.consulta.dto.UsuarioSelecaoResponse;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("select usuario from Usuario usuario join fetch usuario.setor where usuario.email = :email")
    Optional<Usuario> buscarPorEmailComSetor(@Param("email") String email);

    @Query("select usuario from Usuario usuario join fetch usuario.setor where usuario.id = :id")
    Optional<Usuario> buscarPorIdComSetor(@Param("id") Long id);

    @Query("select new br.edu.simpi.consulta.dto.UsuarioSelecaoResponse(usuario.id, usuario.nome, setor.id, setor.nome) "
            + "from Usuario usuario join usuario.setor setor "
            + "where usuario.ativo = true and setor.id = :setorId order by usuario.nome, usuario.id")
    List<UsuarioSelecaoResponse> listarAtivosPorSetor(@Param("setorId") Long setorId);
}
