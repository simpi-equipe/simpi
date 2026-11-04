package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoSolicitacaoRepository extends JpaRepository<EventoSolicitacao, Long> {
    @Query("select evento from EventoSolicitacao evento where evento.solicitacao.id = :solicitacaoId "
            + "order by evento.ocorridoEm, evento.id")
    List<EventoSolicitacao> buscarPorSolicitacao(@Param("solicitacaoId") Long solicitacaoId);
}
