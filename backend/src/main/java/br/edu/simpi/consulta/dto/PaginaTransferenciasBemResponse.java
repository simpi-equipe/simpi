package br.edu.simpi.consulta.dto;

import java.util.List;

public record PaginaTransferenciasBemResponse(List<TransferenciaBemResponse> conteudo,
        int pagina, int tamanho, long totalElementos, long totalPaginas) {
    public PaginaTransferenciasBemResponse {
        conteudo = List.copyOf(conteudo);
    }
}
