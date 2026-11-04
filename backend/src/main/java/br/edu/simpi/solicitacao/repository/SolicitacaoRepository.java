package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select solicitacao from Solicitacao solicitacao where solicitacao.id = :id")
    Optional<Solicitacao> buscarPorIdParaAtualizacao(@Param("id") Long id);

    @Query("select count(solicitacao) > 0 from Solicitacao solicitacao "
            + "where solicitacao.bem.id = :bemId and solicitacao.status in :estados")
    boolean existePorBemEEstados(@Param("bemId") Long bemId,
            @Param("estados") Collection<StatusSolicitacao> estados);
}
