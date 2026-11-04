package br.edu.simpi.solicitacao.dto;

import br.edu.simpi.solicitacao.enums.DecisaoAprovacao;
import br.edu.simpi.solicitacao.enums.EtapaAprovacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.TipoEventoSolicitacao;
import java.time.Instant;
import java.util.List;

public record SolicitacaoResponse(Long id, String numero, StatusSolicitacao status,
        Instant criadaEm, String justificativa, BemResponse bem, SolicitanteResponse solicitante,
        ReferenciasResponse origem, ReferenciasResponse destino, List<AprovacaoResponse> aprovacoes,
        OperacaoResponse envio, OperacaoResponse recebimento, List<EventoResponse> eventos,
        List<String> acoesDisponiveis) {

    public SolicitacaoResponse {
        aprovacoes = List.copyOf(aprovacoes);
        eventos = List.copyOf(eventos);
        acoesDisponiveis = List.copyOf(acoesDisponiveis);
    }

    public record ReferenciaResponse(Long id, String nome) { }
    public record BemResponse(Long id, String codigo, String plaqueta, String descricao) { }
    public record SolicitanteResponse(Long id, String nome, ReferenciaResponse setor) { }
    public record ReferenciasResponse(ReferenciaResponse setor, ReferenciaResponse localizacao,
            ReferenciaResponse responsavel) { }
    public record AprovacaoResponse(Long id, EtapaAprovacao etapa, ReferenciaResponse aprovador,
            DecisaoAprovacao decisao, String justificativa, Instant decididaEm) { }
    public record OperacaoResponse(Instant realizadoEm, ReferenciaResponse realizadoPor) { }
    public record EventoResponse(Long id, TipoEventoSolicitacao tipo, ReferenciaResponse autor,
            StatusSolicitacao statusAnterior, StatusSolicitacao statusNovo, String descricao,
            Instant ocorridoEm) { }
}
