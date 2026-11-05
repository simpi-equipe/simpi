package br.edu.simpi.solicitacao.repository;

import br.edu.simpi.solicitacao.dto.PesquisarSolicitacoesRequest;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultaSolicitacaoRepository {
    private static final String RELACOES = """
             FROM solicitacao pedido
             JOIN bem_patrimonial bem ON bem.id = pedido.bem_id
             JOIN usuario solicitante ON solicitante.id = pedido.solicitante_id
             JOIN setor area ON area.id = pedido.setor_solicitante_id
             JOIN setor origem ON origem.id = pedido.setor_origem_id
             JOIN setor destino ON destino.id = pedido.setor_destino_id
            """;
    private static final String MINHA_AREA = """
            (pedido.setor_solicitante_id = :setorId OR pedido.setor_origem_id = :setorId
             OR pedido.setor_destino_id = :setorId)
            """;
    private final EntityManager gerenciador;

    public ConsultaSolicitacaoRepository(EntityManager gerenciador) {
        this.gerenciador = gerenciador;
    }

    public long contar(PesquisarSolicitacoesRequest filtros, Usuario ator) {
        Predicados predicados = montarPredicados(filtros, ator);
        Query consulta = gerenciador.createNativeQuery("SELECT count(*)" + RELACOES + predicados.sql());
        predicados.parametros().forEach(consulta::setParameter);
        return ((Number) consulta.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<RegistroSolicitacao> pesquisar(PesquisarSolicitacoesRequest filtros, Usuario ator,
            long deslocamento) {
        Predicados predicados = montarPredicados(filtros, ator);
        Query consulta = gerenciador.createNativeQuery("""
                SELECT pedido.id, pedido.numero, pedido.criada_em, pedido.status,
                    bem.id, bem.codigo, bem.descricao, solicitante.id, solicitante.nome,
                    area.id, area.nome, origem.id, origem.nome, destino.id, destino.nome
                """ + RELACOES + predicados.sql()
                + " ORDER BY pedido.criada_em DESC, pedido.id DESC LIMIT :tamanho OFFSET :deslocamento");
        predicados.parametros().forEach(consulta::setParameter);
        consulta.setParameter("tamanho", filtros.tamanho());
        consulta.setParameter("deslocamento", deslocamento);
        List<Object[]> linhas = consulta.getResultList();
        return linhas.stream().map(linha -> new RegistroSolicitacao(numero(linha[0]), (String) linha[1],
                instante(linha[2]), StatusSolicitacao.valueOf((String) linha[3]), numero(linha[4]),
                (String) linha[5], (String) linha[6], numero(linha[7]), (String) linha[8],
                numero(linha[9]), (String) linha[10], numero(linha[11]), (String) linha[12],
                numero(linha[13]), (String) linha[14])).toList();
    }

    private Predicados montarPredicados(PesquisarSolicitacoesRequest filtros, Usuario ator) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> parametros = new LinkedHashMap<>();
        if (ator.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            sql.append(" AND ").append(MINHA_AREA);
            parametros.put("setorId", ator.getSetor().getId());
        }
        switch (filtros.visao()) {
            case DA_MINHA_AREA -> {
                sql.append(" AND ").append(MINHA_AREA);
                parametros.put("setorId", ator.getSetor().getId());
            }
            case CRIADAS_POR_MIM -> {
                sql.append(" AND pedido.solicitante_id = :atorId");
                parametros.put("atorId", ator.getId());
            }
            case AGUARDANDO_MINHA_APROVACAO -> {
                // Espelha RegrasDecisaoAprovacao: designação, setor atual, pendência e estado.
                sql.append("""
                         AND EXISTS (SELECT 1 FROM aprovacao etapa
                            WHERE etapa.solicitacao_id = pedido.id AND etapa.aprovador_id = :atorId
                            AND etapa.decisao = 'PENDENTE' AND (
                                (etapa.etapa = 'GESTOR_AREA' AND pedido.status = 'AGUARDANDO_GESTOR_AREA'
                                    AND pedido.setor_solicitante_id = :setorId)
                                OR (etapa.etapa = 'GESTOR_RESPONSAVEL'
                                    AND pedido.status = 'AGUARDANDO_GESTOR_RESPONSAVEL'
                                    AND pedido.setor_origem_id = :setorId)))
                        """);
                parametros.put("atorId", ator.getId());
                parametros.put("setorId", ator.getSetor().getId());
            }
            case ENVIOS_E_RECEBIMENTOS -> {
                sql.append("""
                         AND ((pedido.status = 'APROVADA' AND pedido.setor_origem_id = :setorId)
                            OR (pedido.status = 'EM_TRANSITO' AND pedido.setor_destino_id = :setorId))
                        """);
                parametros.put("setorId", ator.getSetor().getId());
            }
            case TODAS -> { /* Perfil autorizado pelo service. */ }
        }
        if (filtros.status() != null) {
            sql.append(" AND pedido.status = :status");
            parametros.put("status", filtros.status().name());
        }
        if (filtros.busca() != null) {
            sql.append(" AND (LOWER(pedido.numero) LIKE LOWER(:busca) ESCAPE '!'"
                    + " OR LOWER(bem.codigo) LIKE LOWER(:busca) ESCAPE '!')");
            String literal = filtros.busca().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parametros.put("busca", "%" + literal + "%");
        }
        return new Predicados(sql.toString(), Map.copyOf(parametros));
    }

    private Long numero(Object valor) { return ((Number) valor).longValue(); }

    private Instant instante(Object valor) {
        if (valor instanceof OffsetDateTime data) {
            return data.toInstant();
        }
        if (valor instanceof Timestamp data) {
            return data.toInstant();
        }
        return (Instant) valor;
    }

    private record Predicados(String sql, Map<String, Object> parametros) { }

    public record RegistroSolicitacao(Long id, String numero, Instant criadaEm, StatusSolicitacao status,
            Long bemId, String bemCodigo, String bemDescricao, Long solicitanteId, String solicitanteNome,
            Long setorSolicitanteId, String setorSolicitanteNome, Long setorOrigemId, String setorOrigemNome,
            Long setorDestinoId, String setorDestinoNome) { }
}
