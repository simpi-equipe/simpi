package br.edu.simpi.bem;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BemPatrimonialRepository extends JpaRepository<BemPatrimonial, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bem from BemPatrimonial bem where bem.id = :id")
    Optional<BemPatrimonial> buscarPorIdParaAtualizacao(@Param("id") Long id);
}
