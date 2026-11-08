package br.edu.simpi.configuracao;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Carga optativa de cadastros fictícios, sem alterar o histórico de movimentações. */
@Component
@Profile("seed-desenvolvimento")
public class SeedDesenvolvimento implements CommandLineRunner {
    private static final Logger REGISTRO = LoggerFactory.getLogger(SeedDesenvolvimento.class);
    private static final List<String> AREAS = List.of("AREA", "ORIGEM", "DESTINO");
    private final JdbcTemplate banco;
    private final PasswordEncoder senhas;
    private final String senha;

    public SeedDesenvolvimento(JdbcTemplate banco, PasswordEncoder senhas,
            @Value("${SIMPI_SEED_SENHA:}") String senha) {
        this.banco = banco;
        this.senhas = senhas;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(String... argumentos) {
        validarDestino();
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException("Defina SIMPI_SEED_SENHA para executar o seed de desenvolvimento.");
        }
        carregarCadastros();
    }

    // Separado da entrada de execução para verificar a carga com rollback no banco de testes.
    void carregarCadastros() {
        // Serializa somente cargas concorrentes deste seed na mesma base.
        banco.execute("SELECT pg_advisory_xact_lock(73646721)");
        int existentes = contarCadastros();
        if (existentes == 19) {
            REGISTRO.info("Cadastros DEMO já presentes; nenhum dado ou senha foi redefinido.");
            return;
        }
        if (existentes != 0) {
            throw new IllegalStateException("Carga DEMO parcial ou códigos reservados em uso. "
                    + "Confira os cadastros antes de executar; nenhum registro será sobrescrito.");
        }
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        for (String area : AREAS) {
            criarArea(area);
        }
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        REGISTRO.info("Seed de desenvolvimento concluído: 3 setores, 7 usuários, 3 localizações e 6 bens. "
                + "Nenhuma solicitação criada.");
    }

    private void validarDestino() {
        String endereco = banco.execute((ConnectionCallback<String>) conexao -> conexao.getMetaData().getURL());
        if (endereco == null || !endereco.matches(
                "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]+)?/[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("Seed permitido somente em PostgreSQL local explícito, sem parâmetros na URL.");
        }
        String nomeBanco = banco.queryForObject("SELECT current_database()", String.class);
        if ("simpi_test".equals(nomeBanco) || "postgres".equals(nomeBanco)
                || nomeBanco == null || nomeBanco.startsWith("template")) {
            throw new IllegalStateException("Seed bloqueado no banco de integração ou em banco de sistema.");
        }
    }

    private int contarCadastros() {
        return banco.queryForObject("""
                SELECT
                  (SELECT count(*) FROM setor WHERE codigo IN ('DEMO-AREA','DEMO-ORIGEM','DEMO-DESTINO')) +
                  (SELECT count(*) FROM usuario WHERE email IN (
                    'gestor.area@simpi.example','comum.area@simpi.example','admin.area@simpi.example',
                    'gestor.origem@simpi.example','comum.origem@simpi.example',
                    'gestor.destino@simpi.example','comum.destino@simpi.example')) +
                  (SELECT count(*) FROM localizacao WHERE codigo IN ('DEMO-AREA','DEMO-ORIGEM','DEMO-DESTINO')) +
                  (SELECT count(*) FROM bem_patrimonial WHERE codigo IN (
                    'DEMO-AREA-1','DEMO-AREA-2','DEMO-ORIGEM-1','DEMO-ORIGEM-2',
                    'DEMO-DESTINO-1','DEMO-DESTINO-2'))
                """, Integer.class);
    }

    private void criarArea(String area) {
        String codigo = "DEMO-" + area;
        String sufixo = area.toLowerCase(java.util.Locale.ROOT) + "@simpi.example";
        Long setor = banco.queryForObject("SELECT nextval('sequencia_setor')", Long.class);
        Long gestor = banco.queryForObject("SELECT nextval('sequencia_usuario')", Long.class);
        banco.update("INSERT INTO setor (id,codigo,nome,gestor_id) VALUES (?,?,?,?)",
                setor, codigo, "Demonstração " + area, gestor);
        banco.update("INSERT INTO usuario (id,nome,email,senha_hash,perfil,setor_id) VALUES (?,?,?,?,?,?)",
                gestor, "Gestor " + area, "gestor." + sufixo, senhas.encode(senha), "GESTOR", setor);
        Long comum = banco.queryForObject("""
                INSERT INTO usuario (nome,email,senha_hash,perfil,setor_id)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, "Usuário " + area, "comum." + sufixo, senhas.encode(senha), "COMUM", setor);
        if (area.equals("AREA")) {
            banco.update("INSERT INTO usuario (nome,email,senha_hash,perfil,setor_id) VALUES (?,?,?,?,?)",
                    "Administrador de demonstração", "admin." + sufixo, senhas.encode(senha), "ADMINISTRADOR", setor);
        }
        Long localizacao = banco.queryForObject("INSERT INTO localizacao (codigo,nome) VALUES (?,?) RETURNING id",
                Long.class, codigo, "Sala de demonstração " + area);
        for (int indice = 1; indice <= 2; indice++) {
            banco.update("""
                    INSERT INTO bem_patrimonial (codigo,descricao,setor_id,localizacao_id,responsavel_id)
                    VALUES (?,?,?,?,?)
                    """, codigo + "-" + indice, "Bem fictício " + area + " " + indice, setor, localizacao, comum);
        }
    }
}
