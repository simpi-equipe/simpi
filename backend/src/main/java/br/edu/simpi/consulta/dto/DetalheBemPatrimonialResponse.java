package br.edu.simpi.consulta.dto;

import br.edu.simpi.consulta.dto.BemPatrimonialResponse.LocalizacaoResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.ResponsavelResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.SetorResponse;

public record DetalheBemPatrimonialResponse(Long id, String codigo, String plaqueta, String descricao,
        SetorResponse setor, LocalizacaoResponse localizacao, ResponsavelResponse responsavel,
        boolean ativo, boolean disponivelParaSolicitacao, SolicitacaoEmAndamentoResponse solicitacaoEmAndamento) {
    public record SolicitacaoEmAndamentoResponse(Long id, String numero) { }
}
