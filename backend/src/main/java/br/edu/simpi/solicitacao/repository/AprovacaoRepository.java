package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.model.Aprovacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AprovacaoRepository extends JpaRepository<Aprovacao, Long> {
}
