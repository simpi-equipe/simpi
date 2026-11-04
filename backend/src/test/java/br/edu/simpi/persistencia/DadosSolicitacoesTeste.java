package br.edu.simpi.persistencia;

import br.edu.simpi.solicitacao.model.NumeroSolicitacao;
import br.edu.simpi.solicitacao.service.NumeroSolicitacaoService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;

record DadosSolicitacoesTeste(DadosPatrimoniaisTeste origem, DadosPatrimoniaisTeste destino,
        Long solicitanteId) {

    static final Instant CRIADA_EM = Instant.parse("2026-12-31T23:59:59.999999999Z");

    static DadosSolicitacoesTeste preparar(JdbcTemplate banco) {
        DadosPatrimoniaisTeste origem = DadosPatrimoniaisTeste.preparar(banco);
        DadosPatrimoniaisTeste destino = DadosPatrimoniaisTeste.preparar(banco);
        Long solicitanteId = banco.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id)
                VALUES ('Solicitante sintético', ?, ?, 'COMUM', ?) RETURNING id
                """, Long.class, DadosPatrimoniaisTeste.novoEmail(),
                DadosPatrimoniaisTeste.HASH_SINTETICO, origem.setorId());
        banco.execute("SET CONSTRAINTS ALL IMMEDIATE");
        return new DadosSolicitacoesTeste(origem, destino, solicitanteId);
    }

    Long inserirSolicitacao(JdbcTemplate banco, String status) {
        NumeroSolicitacao numero = new NumeroSolicitacaoService(banco).gerar(CRIADA_EM);
        return banco.queryForObject("""
                INSERT INTO solicitacao (numero, bem_id, solicitante_id, setor_solicitante_id,
                    setor_origem_id, localizacao_origem_id, responsavel_origem_id,
                    setor_destino_id, localizacao_destino_id, responsavel_destino_id,
                    justificativa, status, criada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Justificativa sintética', ?, ?) RETURNING id
                """, Long.class, numero.valor(), origem.bemId(), solicitanteId, origem.setorId(),
                origem.setorId(), origem.localizacaoId(), origem.gestorId(), destino.setorId(),
                destino.localizacaoId(), destino.gestorId(), status, numero.criadaEm().atOffset(ZoneOffset.UTC));
    }

    Long inserirAprovacao(JdbcTemplate banco, Long solicitacaoId, String etapa) {
        return banco.queryForObject("""
                INSERT INTO aprovacao (solicitacao_id, etapa, aprovador_id)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, solicitacaoId, etapa, origem.gestorId());
    }

    Long inserirEvento(JdbcTemplate banco, Long solicitacaoId) {
        return banco.queryForObject("""
                INSERT INTO evento_solicitacao (solicitacao_id, autor_id, tipo, status_novo, ocorrido_em)
                VALUES (?, ?, 'CRIACAO', 'AGUARDANDO_GESTOR_AREA', ?) RETURNING id
                """, Long.class, solicitacaoId, solicitanteId,
                CRIADA_EM.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
    }
}
