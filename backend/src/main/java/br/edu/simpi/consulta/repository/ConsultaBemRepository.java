package br.edu.simpi.consulta.repository;

import br.edu.simpi.consulta.dto.PesquisarBensRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultaBemRepository {
    private static final String DISPONIBILIDADE = "(bem.ativo AND andamento.id IS NULL)";
    private static final String RELACOES = """
             FROM bem_patrimonial bem
             JOIN setor setor ON setor.id = bem.setor_id
             JOIN localizacao localizacao ON localizacao.id = bem.localizacao_id
             JOIN usuario responsavel ON responsavel.id = bem.responsavel_id
             LEFT JOIN solicitacao andamento ON andamento.bem_id = bem.id
                AND andamento.status IN ('AGUARDANDO_GESTOR_AREA', 'AGUARDANDO_GESTOR_RESPONSAVEL', 'APROVADA', 'EM_TRANSITO')
            """;
    private static final String COLUNAS = """
            SELECT bem.id AS bem_id, bem.codigo AS bem_codigo, bem.plaqueta AS bem_plaqueta,
                bem.descricao AS bem_descricao, setor.id AS setor_id, setor.nome AS setor_nome,
                localizacao.id AS localizacao_id, localizacao.nome AS localizacao_nome,
                responsavel.id AS responsavel_id, responsavel.nome AS responsavel_nome, bem.ativo AS bem_ativo,
            """ + DISPONIBILIDADE + " AS disponivel,\n" + """
                andamento.id AS solicitacao_id, andamento.numero AS solicitacao_numero,
                andamento.setor_solicitante_id AS setor_solicitante_id,
                andamento.setor_origem_id AS setor_origem_id, andamento.setor_destino_id AS setor_destino_id
            """;
    private final EntityManager gerenciador;

    public ConsultaBemRepository(EntityManager gerenciador) {
        this.gerenciador = gerenciador;
    }

    public long contar(PesquisarBensRequest filtros) {
        Predicados predicados = montarPredicados(filtros);
        Query consulta = gerenciador.createNativeQuery("SELECT count(*)" + RELACOES + predicados.sql());
        vincular(consulta, predicados.parametros());
        return ((Number) consulta.getSingleResult()).longValue();
    }

    public List<RegistroBem> pesquisar(PesquisarBensRequest filtros, long deslocamento) {
        Predicados predicados = montarPredicados(filtros);
        Query consulta = gerenciador.createNativeQuery(COLUNAS + RELACOES + predicados.sql()
                + " ORDER BY bem.codigo, bem.id LIMIT :tamanho OFFSET :deslocamento");
        vincular(consulta, predicados.parametros());
        // Query.setFirstResult recebe int. O OFFSET SQL é vinculado como Long, sem estreitamento.
        consulta.setParameter("tamanho", filtros.tamanho());
        consulta.setParameter("deslocamento", deslocamento);
        return lerRegistros(consulta);
    }

    public Optional<RegistroBem> detalhar(Long bemId) {
        Query consulta = gerenciador.createNativeQuery(COLUNAS + RELACOES + " WHERE bem.id = :bemId");
        consulta.setParameter("bemId", bemId);
        return lerRegistros(consulta).stream().findFirst();
    }

    private Predicados montarPredicados(PesquisarBensRequest filtros) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> parametros = new LinkedHashMap<>();
        if (filtros.busca() != null) {
            sql.append(" AND (LOWER(bem.codigo) LIKE LOWER(:busca) ESCAPE '!'"
                    + " OR LOWER(bem.plaqueta) LIKE LOWER(:busca) ESCAPE '!'"
                    + " OR LOWER(bem.descricao) LIKE LOWER(:busca) ESCAPE '!')");
            String literal = filtros.busca().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parametros.put("busca", "%" + literal + "%");
        }
        if (filtros.setorId() != null) {
            sql.append(" AND bem.setor_id = :setorId");
            parametros.put("setorId", filtros.setorId());
        }
        if (filtros.localizacaoId() != null) {
            sql.append(" AND bem.localizacao_id = :localizacaoId");
            parametros.put("localizacaoId", filtros.localizacaoId());
        }
        if (filtros.disponivelParaSolicitacao() != null) {
            sql.append(" AND ").append(DISPONIBILIDADE).append(" = :disponivel");
            parametros.put("disponivel", filtros.disponivelParaSolicitacao());
        }
        return new Predicados(sql.toString(), Map.copyOf(parametros));
    }

    private void vincular(Query consulta, Map<String, Object> parametros) {
        parametros.forEach(consulta::setParameter);
    }

    @SuppressWarnings("unchecked")
    private List<RegistroBem> lerRegistros(Query consulta) {
        List<Object[]> linhas = consulta.getResultList();
        return linhas.stream().map(linha -> new RegistroBem(numero(linha[0]), (String) linha[1],
                (String) linha[2], (String) linha[3], numero(linha[4]), (String) linha[5],
                numero(linha[6]), (String) linha[7], numero(linha[8]), (String) linha[9],
                (Boolean) linha[10], (Boolean) linha[11], numero(linha[12]), (String) linha[13],
                numero(linha[14]), numero(linha[15]), numero(linha[16]))).toList();
    }

    private Long numero(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private record Predicados(String sql, Map<String, Object> parametros) { }

    /** Projeção interna; nunca é retornada como DTO público, pois pode conter pedido oculto. */
    public record RegistroBem(Long id, String codigo, String plaqueta, String descricao,
            Long setorId, String setorNome, Long localizacaoId, String localizacaoNome,
            Long responsavelId, String responsavelNome, boolean ativo, boolean disponivel,
            Long solicitacaoId, String solicitacaoNumero, Long setorSolicitanteId,
            Long setorOrigemId, Long setorDestinoId) { }
}
