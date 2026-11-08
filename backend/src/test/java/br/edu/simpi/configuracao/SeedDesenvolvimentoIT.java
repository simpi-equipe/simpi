package br.edu.simpi.configuracao;

import br.edu.simpi.persistencia.ProtecaoBancoIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
@Timeout(30)
class SeedDesenvolvimentoIT {
    @Autowired private JdbcTemplate banco;
    @Autowired private PasswordEncoder senhas;

    @Test
    void carregarCadastrosCoerentesEReexecutarSemRedefinirDados() {
        assertEquals(0, contarSetores());
        var seed = new SeedDesenvolvimento(banco, senhas, "senha-sintetica-de-teste");
        // Exercita só a carga dentro da transação de teste, desfeita após o caso.
        // A entrada pública continua bloqueando simpi_test, conforme o teste abaixo.
        seed.carregarCadastros();
        assertEquals(3, contarSetores());
        assertEquals(3L, banco.queryForObject("SELECT count(*) FROM localizacao WHERE codigo LIKE 'DEMO-%'", Long.class));
        assertEquals(6L, banco.queryForObject("SELECT count(*) FROM bem_patrimonial WHERE codigo LIKE 'DEMO-%'", Long.class));
        var contas = banco.queryForList("SELECT email,senha_hash FROM usuario WHERE email LIKE '%@simpi.example' ORDER BY email");
        assertEquals(7, contas.size());
        for (var conta : contas) {
            assertTrue(senhas.matches("senha-sintetica-de-teste", (String) conta.get("senha_hash")));
        }
        assertEquals(0L, banco.queryForObject("""
                SELECT count(*) FROM setor s JOIN usuario u ON u.id=s.gestor_id
                WHERE s.codigo LIKE 'DEMO-%' AND (u.perfil <> 'GESTOR' OR u.setor_id <> s.id)
                """, Long.class));
        assertEquals(0L, banco.queryForObject("""
                SELECT count(*) FROM bem_patrimonial b JOIN usuario u ON u.id=b.responsavel_id
                WHERE b.codigo LIKE 'DEMO-%' AND u.setor_id <> b.setor_id
                """, Long.class));
        assertEquals(0L, banco.queryForObject("""
                SELECT count(*) FROM solicitacao s JOIN bem_patrimonial b ON b.id=s.bem_id
                WHERE b.codigo LIKE 'DEMO-%'
                """, Long.class));
        banco.update("UPDATE bem_patrimonial SET descricao='Alterado na demonstração' WHERE codigo='DEMO-ORIGEM-1'");
        banco.update("UPDATE usuario SET ativo=false WHERE email='comum.area@simpi.example'");
        seed.carregarCadastros();
        assertEquals(3, contarSetores());
        assertEquals("Alterado na demonstração", banco.queryForObject(
                "SELECT descricao FROM bem_patrimonial WHERE codigo='DEMO-ORIGEM-1'", String.class));
        assertEquals(false, banco.queryForObject("SELECT ativo FROM usuario WHERE email='comum.area@simpi.example'", Boolean.class));
        var contasDepois = banco.queryForList("SELECT email,senha_hash FROM usuario WHERE email LIKE '%@simpi.example' ORDER BY email");
        assertTrue(contas.equals(contasDepois), "Reexecução deve preservar contas e hashes sem redefini-los.");
    }

    @Test
    void cargaParcialFalhaAntesDeInserirOutrosCadastros() {
        assertEquals(0, contarSetores());
        banco.update("INSERT INTO localizacao (codigo,nome) VALUES ('DEMO-AREA','Conflito sintético')");
        var seed = new SeedDesenvolvimento(banco, senhas, "senha-sintetica-de-teste");
        assertThrows(IllegalStateException.class, seed::carregarCadastros);
        assertEquals(0, contarSetores());
    }

    @Test
    void entradaPublicaDoSeedRecusaBancoDeIntegracaoReal() {
        var seed = new SeedDesenvolvimento(banco, senhas, "senha-sintetica-de-teste");
        assertThrows(IllegalStateException.class, seed::run);
        assertEquals(0, contarSetores());
    }

    private int contarSetores() {
        return banco.queryForObject(
                "SELECT count(*) FROM setor WHERE codigo IN ('DEMO-AREA','DEMO-ORIGEM','DEMO-DESTINO')", Integer.class);
    }
}
