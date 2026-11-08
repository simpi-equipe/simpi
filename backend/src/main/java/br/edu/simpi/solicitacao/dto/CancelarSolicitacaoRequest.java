package br.edu.simpi.solicitacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CancelarSolicitacaoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Motivo não vazio do cancelamento.")
        String motivo) {
}
