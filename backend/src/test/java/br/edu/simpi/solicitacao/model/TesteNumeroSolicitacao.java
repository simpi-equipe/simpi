package br.edu.simpi.solicitacao.model;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TesteNumeroSolicitacao {

    @Test
    void formataSemTruncarSequenciaisAcimaDeQuatroDigitos() {
        Instant criadaEm = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals("SOL-2026-0043", NumeroSolicitacao.formatar(43, criadaEm));
        assertEquals("SOL-2026-9999", NumeroSolicitacao.formatar(9999, criadaEm));
        assertEquals("SOL-2026-10000", NumeroSolicitacao.formatar(10000, criadaEm));
        assertEquals("SOL-2026-9223372036854775807", NumeroSolicitacao.formatar(Long.MAX_VALUE, criadaEm));
        assertEquals(new NumeroSolicitacao("SOL-2026-10000", criadaEm), new NumeroSolicitacao(10000, criadaEm));
    }

    @Test
    void mantemAnoDoInstanteUtcQueSeraPersistidoMesmoNaViradaDoAno() {
        NumeroSolicitacao numero = new NumeroSolicitacao("SOL-2026-0043",
                Instant.parse("2026-12-31T23:59:59.999999999Z"));
        assertEquals(Instant.parse("2026-12-31T23:59:59.999999Z"), numero.criadaEm());
        assertEquals("SOL-2026-0043", numero.valor());
        assertThrows(IllegalArgumentException.class, () -> new NumeroSolicitacao(
                "SOL-2027-0043", numero.criadaEm()));
    }

    @Test
    void rejeitaSequenciaisInvalidosEFormatosNaoCanonicos() {
        Instant criadaEm = Instant.parse("2026-01-01T00:00:00Z");
        for (String valor : new String[] {"SOL-2026-0000", "SOL-2026-00043",
                "SOL-2026-43", "SOL-2026-9223372036854775808"}) {
            assertThrows(IllegalArgumentException.class, () -> new NumeroSolicitacao(valor, criadaEm));
        }
        assertThrows(IllegalArgumentException.class, () -> NumeroSolicitacao.formatar(-1, criadaEm));
    }
}
