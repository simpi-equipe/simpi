package br.edu.simpi.usuario;

import br.edu.simpi.consulta.dto.UsuarioSelecaoResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("select new br.edu.simpi.consulta.dto.UsuarioSelecaoResponse(usuario.id, usuario.nome, setor.id, setor.nome) "
            + "from Usuario usuario join usuario.setor setor "
            + "where usuario.ativo = true and setor.id = :setorId order by usuario.nome, usuario.id")
    List<UsuarioSelecaoResponse> listarAtivosPorSetor(@Param("setorId") Long setorId);
}
