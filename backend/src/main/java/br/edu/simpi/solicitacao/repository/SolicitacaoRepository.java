package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.model.Solicitacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {
}
