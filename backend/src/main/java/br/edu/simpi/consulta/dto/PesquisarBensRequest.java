package br.edu.simpi.consulta.dto;

public record PesquisarBensRequest(String busca, Long setorId, Long localizacaoId,
        Boolean disponivelParaSolicitacao, Integer pagina, Integer tamanho) { }
