package br.edu.simpi.setor;

import br.edu.simpi.consulta.dto.SetorSelecaoResponse;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SetorRepository extends JpaRepository<Setor, Long> {

    Optional<Setor> findByCodigo(String codigo);

    @Query("select new br.edu.simpi.consulta.dto.SetorSelecaoResponse(setor.id, setor.codigo, setor.nome) "
            + "from Setor setor where setor.ativo = true order by setor.nome, setor.id")
    List<SetorSelecaoResponse> listarAtivosParaSelecao();
}
