package br.edu.simpi.solicitacao.dto;

import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.VisaoSolicitacao;

public record PesquisarSolicitacoesRequest(String busca, StatusSolicitacao status,
        VisaoSolicitacao visao, Integer pagina, Integer tamanho) { }
