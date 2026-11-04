package br.edu.simpi.localizacao;

import br.edu.simpi.consulta.dto.LocalizacaoSelecaoResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LocalizacaoRepository extends JpaRepository<Localizacao, Long> {
    @Query("select new br.edu.simpi.consulta.dto.LocalizacaoSelecaoResponse(localizacao.id, localizacao.codigo, localizacao.nome) "
            + "from Localizacao localizacao where localizacao.ativo = true order by localizacao.nome, localizacao.id")
    List<LocalizacaoSelecaoResponse> listarAtivasParaSelecao();
}
