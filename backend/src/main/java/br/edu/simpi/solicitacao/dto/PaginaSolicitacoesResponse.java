package br.edu.simpi.solicitacao.dto;

import java.util.List;

public record PaginaSolicitacoesResponse(List<ResumoSolicitacaoResponse> conteudo,
        int pagina, int tamanho, long totalElementos, long totalPaginas) {
    public PaginaSolicitacoesResponse {
        conteudo = List.copyOf(conteudo);
    }
}
