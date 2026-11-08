package br.edu.simpi.solicitacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A identidade do solicitante é fornecida internamente, fora destes dados. */
public record CriarSolicitacaoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long bemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long setorDestinoId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long localizacaoDestinoId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long responsavelDestinoId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Justificativa não vazia.") String justificativa) {
}
