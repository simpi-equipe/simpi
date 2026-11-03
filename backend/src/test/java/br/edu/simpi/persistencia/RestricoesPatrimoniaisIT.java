package br.edu.simpi.persistencia;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
class RestricoesPatrimoniaisIT {

    private final JdbcTemplate banco;
    private final TransactionTemplate transacao;

    @Autowired
    RestricoesPatrimoniaisIT(JdbcTemplate banco, PlatformTransactionManager transacoes) {
        this.banco = banco;
        this.transacao = new TransactionTemplate(transacoes);
    }

    @Test
    void rejeitaAusenciaDeTodosOsCamposObrigatorios() {
        for (String tabela : new String[] {"setor", "usuario", "localizacao", "bem_patrimonial"}) {
            String[] colunas = switch (tabela) {
                case "setor" -> new String[] {"id", "codigo", "nome", "gestor_id", "ativo"};
                case "usuario" -> new String[] {
                    "id", "nome", "email", "senha_hash", "perfil", "setor_id", "ativo"
                };
                case "localizacao" -> new String[] {"id", "codigo", "nome", "ativo"};
                default -> new String[] {
                    "id", "codigo", "descricao", "setor_id", "localizacao_id", "responsavel_id", "ativo"
                };
            };
            for (String coluna : colunas) {
                verificarViolacao("23502", dados -> banco.update(
                        "UPDATE " + tabela + " SET " + coluna + " = NULL WHERE id = ?",
                        identificador(tabela, dados)));
            }
        }
    }

    @Test
    void rejeitaTextosAcimaDosLimitesDefinidos() {
        // Os identificadores abaixo são constantes do teste, nunca entrada externa.
        Object[][] limites = {
            {"setor", "codigo", 30}, {"setor", "nome", 150},
            {"usuario", "nome", 150}, {"usuario", "email", 254},
            {"usuario", "senha_hash", 255}, {"usuario", "perfil", 20},
            {"localizacao", "codigo", 30}, {"localizacao", "nome", 150},
            {"bem_patrimonial", "codigo", 30}, {"bem_patrimonial", "plaqueta", 50}
        };
        for (Object[] limite : limites) {
            String tabela = (String) limite[0];
            String coluna = (String) limite[1];
            String texto = "x".repeat((Integer) limite[2] + 1);
            verificarViolacao("22001", dados -> banco.update(
                    "UPDATE " + tabela + " SET " + coluna + " = ? WHERE id = ?",
                    texto, identificador(tabela, dados)));
        }
    }

    @Test
    void rejeitaCodigosEEmailDuplicados() {
        for (String tabela : new String[] {"setor", "localizacao", "bem_patrimonial", "usuario"}) {
            String coluna = tabela.equals("usuario") ? "email" : "codigo";
            verificarViolacao("23505", dados -> {
                DadosPatrimoniaisTeste outrosDados = DadosPatrimoniaisTeste.preparar(banco);
                banco.update("UPDATE " + tabela + " SET " + coluna + " = (SELECT " + coluna
                                + " FROM " + tabela + " WHERE id = ?) WHERE id = ?",
                        identificador(tabela, outrosDados), identificador(tabela, dados));
            });
        }
    }

    @Test
    void rejeitaPerfisForaDoDominioPorSqlDireto() {
        for (String perfil : new String[] {"INVALIDO", "gestor", "", "0"}) {
            verificarViolacao("23514", dados -> banco.update(
                    "UPDATE usuario SET perfil = ? WHERE id = ?", perfil, dados.gestorId()));
        }
    }

    @Test
    void rejeitaTodasAsReferenciasInexistentes() {
        verificarViolacao("23503", dados -> banco.update(
                "UPDATE setor SET gestor_id = ? WHERE id = ?", Long.MAX_VALUE, dados.setorId()));
        verificarViolacao("23503", dados -> banco.update(
                "UPDATE usuario SET setor_id = ? WHERE id = ?", Long.MAX_VALUE, dados.gestorId()));
        for (String coluna : new String[] {"setor_id", "localizacao_id", "responsavel_id"}) {
            verificarViolacao("23503", dados -> banco.update(
                    "UPDATE bem_patrimonial SET " + coluna + " = ? WHERE id = ?",
                    Long.MAX_VALUE, dados.bemId()));
        }
    }

    @Test
    void impedeExclusaoDeRegistrosReferenciadosSemCascata() {
        for (String tabela : new String[] {"setor", "usuario", "localizacao"}) {
            verificarViolacao("23503", dados -> {
                banco.execute("SET CONSTRAINTS ALL IMMEDIATE");
                banco.update("DELETE FROM " + tabela + " WHERE id = ?", identificador(tabela, dados));
            });
        }
    }

    @Test
    void permiteConfirmarCargaCircularCompleta() {
        // Este único teste confirma dados sintéticos no banco exclusivo simpi_test.
        // Não há limpeza destrutiva; os demais cenários usam rollback.
        DadosPatrimoniaisTeste dados = transacao.execute(situacao -> DadosPatrimoniaisTeste.preparar(banco));
        assertNotNull(dados);
        assertEquals(dados.gestorId(), banco.queryForObject(
                "SELECT gestor_id FROM setor WHERE id = ?", Long.class, dados.setorId()));
        assertEquals(dados.setorId(), banco.queryForObject(
                "SELECT setor_id FROM usuario WHERE id = ? AND perfil = 'GESTOR'",
                Long.class, dados.gestorId()));
    }

    @Test
    void falhaNoFechamentoComGestorInexistenteERollbackIntegral() {
        String codigoLocalizacao = DadosPatrimoniaisTeste.novoCodigo();
        String codigoSetor = DadosPatrimoniaisTeste.novoCodigo();
        String emailGestor = DadosPatrimoniaisTeste.novoEmail();
        AtomicReference<DadosPatrimoniaisTeste> dadosPreparados = new AtomicReference<>();
        RuntimeException falha = assertThrows(RuntimeException.class, () -> transacao.execute(situacao -> {
            DadosPatrimoniaisTeste dados = DadosPatrimoniaisTeste.preparar(banco);
            dadosPreparados.set(dados);
            banco.update("UPDATE localizacao SET codigo = ? WHERE id = ?",
                    codigoLocalizacao, dados.localizacaoId());
            banco.update("UPDATE usuario SET email = ? WHERE id = ?", emailGestor, dados.gestorId());
            banco.update("UPDATE setor SET codigo = ?, gestor_id = ? WHERE id = ?",
                    codigoSetor, Long.MAX_VALUE, dados.setorId());
            // Sem antecipar a FK: a falha deve acontecer no commit real.
            return null;
        }));
        verificarEstadoSql("23503", falha);
        assertEquals(0L, banco.queryForObject(
                "SELECT count(*) FROM localizacao WHERE codigo = ?", Long.class, codigoLocalizacao));
        assertEquals(0L, banco.queryForObject(
                "SELECT count(*) FROM setor WHERE codigo = ?", Long.class, codigoSetor));
        assertEquals(0L, banco.queryForObject(
                "SELECT count(*) FROM usuario WHERE email = ?", Long.class, emailGestor));
        assertNotNull(dadosPreparados.get());
        assertEquals(0L, banco.queryForObject("SELECT count(*) FROM bem_patrimonial WHERE id = ?",
                Long.class, dadosPreparados.get().bemId()));
    }

    @Test
    void mantemFkDeGestorAdiavelEInicialmenteAdiada() {
        assertTrue(Boolean.TRUE.equals(banco.queryForObject("""
                SELECT condeferrable AND condeferred
                FROM pg_constraint
                WHERE conrelid = 'setor'::regclass AND conname = 'fk_setor_gestor'
                """, Boolean.class)));
    }

    private void verificarViolacao(String estadoEsperado, Consumer<DadosPatrimoniaisTeste> operacao) {
        RuntimeException falha = assertThrows(RuntimeException.class, () -> transacao.execute(situacao -> {
            situacao.setRollbackOnly();
            DadosPatrimoniaisTeste dados = DadosPatrimoniaisTeste.preparar(banco);
            operacao.accept(dados);
            // Rollback sozinho não validaria uma FK ainda adiada.
            banco.execute("SET CONSTRAINTS ALL IMMEDIATE");
            return null;
        }));
        verificarEstadoSql(estadoEsperado, falha);
    }

    private void verificarEstadoSql(String estadoEsperado, RuntimeException falha) {
        Throwable causa = falha;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }
        assertNotNull(causa, "A falha deve ter origem no PostgreSQL");
        assertEquals(estadoEsperado, ((SQLException) causa).getSQLState());
    }

    private Long identificador(String tabela, DadosPatrimoniaisTeste dados) {
        return switch (tabela) {
            case "setor" -> dados.setorId();
            case "usuario" -> dados.gestorId();
            case "localizacao" -> dados.localizacaoId();
            case "bem_patrimonial" -> dados.bemId();
            default -> throw new IllegalArgumentException("Tabela de fixture desconhecida");
        };
    }
}
