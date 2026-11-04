package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.model.Aprovacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AprovacaoRepository extends JpaRepository<Aprovacao, Long> {
    @Query("select aprovacao from Aprovacao aprovacao where aprovacao.id = :id "
            + "and aprovacao.solicitacao.id = :solicitacaoId")
    Optional<Aprovacao> buscarPorIdESolicitacao(@Param("id") Long id,
            @Param("solicitacaoId") Long solicitacaoId);

    @Query("select aprovacao from Aprovacao aprovacao where aprovacao.solicitacao.id = :solicitacaoId")
    List<Aprovacao> buscarPorSolicitacao(@Param("solicitacaoId") Long solicitacaoId);
}
