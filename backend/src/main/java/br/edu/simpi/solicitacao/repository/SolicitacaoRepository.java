package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {
    @Query("select count(solicitacao) > 0 from Solicitacao solicitacao "
            + "where solicitacao.bem.id = :bemId and solicitacao.status in :estados")
    boolean existePorBemEEstados(@Param("bemId") Long bemId,
            @Param("estados") Collection<StatusSolicitacao> estados);
}
