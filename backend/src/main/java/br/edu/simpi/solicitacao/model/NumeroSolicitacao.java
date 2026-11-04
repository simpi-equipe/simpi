package br.edu.simpi.solicitacao.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Mantém o número e o instante de criação que determinou seu ano inseparáveis. */
public record NumeroSolicitacao(String valor, Instant criadaEm) {

    public NumeroSolicitacao(long sequencial, Instant criadaEm) {
        this(formatar(sequencial, criadaEm), criadaEm);
    }

    public NumeroSolicitacao {
        Objects.requireNonNull(valor, "Campo obrigatório: valor");
        criadaEm = Objects.requireNonNull(criadaEm, "Campo obrigatório: criadaEm")
                .truncatedTo(ChronoUnit.MICROS);
        int ano = criadaEm.atOffset(ZoneOffset.UTC).getYear();
        if (ano < 1 || ano > 9999 || !valor.matches("SOL-[0-9]{4}-[0-9]{4,19}")
                || !valor.startsWith(String.format(Locale.ROOT, "SOL-%04d-", ano))) {
            throw new IllegalArgumentException("Número incompatível com o instante de criação");
        }
        try {
            long sequencial = Long.parseLong(valor.substring(9));
            if (sequencial <= 0 || !valor.equals(formatar(sequencial, criadaEm))) {
                throw new IllegalArgumentException("Sequencial inválido");
            }
        } catch (NumberFormatException excecao) {
            throw new IllegalArgumentException("Sequencial inválido");
        }
    }

    static String formatar(long sequencial, Instant criadaEm) {
        if (sequencial <= 0) {
            throw new IllegalArgumentException("Sequencial deve ser positivo");
        }
        int ano = Objects.requireNonNull(criadaEm, "Campo obrigatório: criadaEm")
                .atOffset(ZoneOffset.UTC).getYear();
        if (ano < 1 || ano > 9999) {
            throw new IllegalArgumentException("Ano deve possuir quatro dígitos");
        }
        return String.format(Locale.ROOT, "SOL-%04d-%04d", ano, sequencial);
    }
}
