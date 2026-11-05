package br.edu.simpi.solicitacao.dto;

import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciaResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.SolicitanteResponse;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import java.time.Instant;

public record ResumoSolicitacaoResponse(Long id, String numero, Instant criadaEm,
        StatusSolicitacao status, BemResumoResponse bem, SolicitanteResponse solicitante,
        ReferenciaResponse setorOrigem, ReferenciaResponse setorDestino) {
    public record BemResumoResponse(Long id, String codigo, String descricao) { }
}
