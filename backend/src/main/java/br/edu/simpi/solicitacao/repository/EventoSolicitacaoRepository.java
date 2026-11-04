package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.model.EventoSolicitacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoSolicitacaoRepository extends JpaRepository<EventoSolicitacao, Long> {
}
