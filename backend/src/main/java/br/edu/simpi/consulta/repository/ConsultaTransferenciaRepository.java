package br.edu.simpi.consulta.repository;

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
public class ConsultaTransferenciaRepository {
    private static final String RELACOES = """
             FROM solicitacao pedido
             JOIN setor origem ON origem.id = pedido.setor_origem_id
             JOIN localizacao localizacao_origem ON localizacao_origem.id = pedido.localizacao_origem_id
             JOIN usuario responsavel_origem ON responsavel_origem.id = pedido.responsavel_origem_id
             JOIN setor destino ON destino.id = pedido.setor_destino_id
             JOIN localizacao localizacao_destino ON localizacao_destino.id = pedido.localizacao_destino_id
             JOIN usuario responsavel_destino ON responsavel_destino.id = pedido.responsavel_destino_id
            """;
    private final EntityManager gerenciador;

    public ConsultaTransferenciaRepository(EntityManager gerenciador) {
        this.gerenciador = gerenciador;
    }

    public long contar(Long bemId, Usuario ator) {
        Predicados predicados = montarPredicados(bemId, ator);
        Query consulta = gerenciador.createNativeQuery("SELECT count(*)" + RELACOES + predicados.sql());
        predicados.parametros().forEach(consulta::setParameter);
        return ((Number) consulta.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<RegistroTransferencia> listar(Long bemId, Usuario ator, int tamanho, long deslocamento) {
        Predicados predicados = montarPredicados(bemId, ator);
        Query consulta = gerenciador.createNativeQuery("""
                SELECT pedido.id, pedido.numero, pedido.recebida_em,
                    origem.id, origem.nome, localizacao_origem.id, localizacao_origem.nome,
                    responsavel_origem.id, responsavel_origem.nome,
                    destino.id, destino.nome, localizacao_destino.id, localizacao_destino.nome,
                    responsavel_destino.id, responsavel_destino.nome
                """ + RELACOES + predicados.sql()
                + " ORDER BY pedido.recebida_em DESC, pedido.id DESC LIMIT :tamanho OFFSET :deslocamento");
        predicados.parametros().forEach(consulta::setParameter);
        consulta.setParameter("tamanho", tamanho);
        consulta.setParameter("deslocamento", deslocamento);
        List<Object[]> linhas = consulta.getResultList();
        return linhas.stream().map(linha -> new RegistroTransferencia(numero(linha[0]), (String) linha[1],
                instante(linha[2]), numero(linha[3]), (String) linha[4], numero(linha[5]), (String) linha[6],
                numero(linha[7]), (String) linha[8], numero(linha[9]), (String) linha[10],
                numero(linha[11]), (String) linha[12], numero(linha[13]), (String) linha[14])).toList();
    }

    private Predicados montarPredicados(Long bemId, Usuario ator) {
        String sql = " WHERE pedido.bem_id = :bemId AND pedido.status = 'CONCLUIDA'";
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("bemId", bemId);
        // Mesma visibilidade de RegrasVisibilidadeSolicitacao, aplicada antes de contar e paginar.
        if (ator.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            sql += " AND (pedido.setor_solicitante_id = :setorId OR pedido.setor_origem_id = :setorId"
                    + " OR pedido.setor_destino_id = :setorId)";
            parametros.put("setorId", ator.getSetor().getId());
        }
        return new Predicados(sql, Map.copyOf(parametros));
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

    public record RegistroTransferencia(Long solicitacaoId, String numero, Instant recebidaEm,
            Long setorOrigemId, String setorOrigemNome, Long localizacaoOrigemId, String localizacaoOrigemNome,
            Long responsavelOrigemId, String responsavelOrigemNome, Long setorDestinoId, String setorDestinoNome,
            Long localizacaoDestinoId, String localizacaoDestinoNome, Long responsavelDestinoId, String responsavelDestinoNome) { }
}
