package br.edu.simpi.consulta.dto;

import java.util.List;

public record PaginaBensResponse(List<BemPatrimonialResponse> conteudo, int pagina, int tamanho,
        long totalElementos, long totalPaginas) {
    public PaginaBensResponse {
        conteudo = List.copyOf(conteudo);
    }
}
