package br.edu.simpi.persistencia;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

record DadosPatrimoniaisTeste(Long setorId, Long gestorId, Long localizacaoId, Long bemId) {

    // Valor sintético com formato BCrypt; nenhum segredo ou usuário real.
    static final String HASH_SINTETICO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    static DadosPatrimoniaisTeste preparar(JdbcTemplate banco) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A fixture exige uma transação ativa");
        }
        Long setorId = banco.queryForObject("SELECT nextval('sequencia_setor')", Long.class);
        Long gestorId = banco.queryForObject("SELECT nextval('sequencia_usuario')", Long.class);
        banco.update("INSERT INTO setor (id, codigo, nome, gestor_id) VALUES (?, ?, ?, ?)",
                setorId, novoCodigo(), "Setor sintético", gestorId);
        banco.update("""
                INSERT INTO usuario (id, nome, email, senha_hash, perfil, setor_id)
                VALUES (?, ?, ?, ?, 'GESTOR', ?)
                """, gestorId, "Gestor sintético", novoEmail(), HASH_SINTETICO, setorId);
        Long localizacaoId = banco.queryForObject("""
                INSERT INTO localizacao (codigo, nome) VALUES (?, ?) RETURNING id
                """, Long.class, novoCodigo(), "Localização sintética");
        Long bemId = banco.queryForObject("""
                INSERT INTO bem_patrimonial (codigo, descricao, setor_id, localizacao_id, responsavel_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, novoCodigo(), "Bem sintético", setorId, localizacaoId, gestorId);
        return new DadosPatrimoniaisTeste(setorId, gestorId, localizacaoId, bemId);
    }

    static String novoCodigo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    static String novoEmail() {
        return novoCodigo() + "@example.invalid";
    }
}
