package br.edu.simpi.solicitacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecusarAprovacaoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Justificativa não vazia para recusar a etapa.")
        String justificativa) {
}
