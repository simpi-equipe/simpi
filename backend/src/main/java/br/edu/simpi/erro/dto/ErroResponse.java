package br.edu.simpi.erro.dto;

import java.util.List;

public record ErroResponse(String codigo, String mensagem, List<ErroCampoResponse> erros) {
    public ErroResponse {
        erros = List.copyOf(erros);
    }
}
