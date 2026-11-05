package br.edu.simpi.consulta.dto;

import br.edu.simpi.consulta.dto.BemPatrimonialResponse.LocalizacaoResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.ResponsavelResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.SetorResponse;
import java.time.Instant;

public record TransferenciaBemResponse(Long solicitacaoId, String numero, Instant recebidaEm,
        ReferenciasTransferenciaResponse origem, ReferenciasTransferenciaResponse destino) {
    public record ReferenciasTransferenciaResponse(SetorResponse setor,
            LocalizacaoResponse localizacao, ResponsavelResponse responsavel) { }
}
