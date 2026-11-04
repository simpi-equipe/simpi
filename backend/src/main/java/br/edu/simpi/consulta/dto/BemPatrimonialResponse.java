package br.edu.simpi.consulta.dto;

public record BemPatrimonialResponse(Long id, String codigo, String plaqueta, String descricao,
        SetorResponse setor, LocalizacaoResponse localizacao, ResponsavelResponse responsavel,
        boolean ativo, boolean disponivelParaSolicitacao) {
    public record SetorResponse(Long id, String nome) { }
    public record LocalizacaoResponse(Long id, String nome) { }
    public record ResponsavelResponse(Long id, String nome) { }
}
