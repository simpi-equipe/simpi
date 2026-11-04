package br.edu.simpi.solicitacao.dto;

/** A identidade do solicitante é fornecida internamente, fora destes dados. */
public record CriarSolicitacaoRequest(Long bemId, Long setorDestinoId,
        Long localizacaoDestinoId, Long responsavelDestinoId, String justificativa) {
}
