package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.model.NumeroSolicitacao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NumeroSolicitacaoService {

    private final JdbcTemplate banco;

    public NumeroSolicitacaoService(JdbcTemplate banco) {
        this.banco = banco;
    }

    public NumeroSolicitacao gerar(Instant criadaEm) {
        Instant instantePersistido = Objects.requireNonNull(criadaEm, "Campo obrigatório: criadaEm")
                .truncatedTo(ChronoUnit.MICROS);
        long sequencial = Objects.requireNonNull(banco.queryForObject(
                "SELECT nextval('sequencia_numero_solicitacao')", Long.class));
        return new NumeroSolicitacao(sequencial, instantePersistido);
    }
}
