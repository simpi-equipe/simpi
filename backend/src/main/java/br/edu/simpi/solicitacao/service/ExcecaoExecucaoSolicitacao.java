package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao;
import java.util.Map;

public class ExcecaoExecucaoSolicitacao extends RuntimeException {
    private final MotivoFalhaExecucaoSolicitacao motivo;
    private final Map<String, String> erros;

    public ExcecaoExecucaoSolicitacao(MotivoFalhaExecucaoSolicitacao motivo,
            String mensagem, Map<String, String> erros) {
        super(mensagem);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaExecucaoSolicitacao getMotivo() {
        return motivo;
    }

    public Map<String, String> getErros() {
        return erros;
    }
}
