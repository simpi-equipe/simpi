package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import java.util.Map;

public class ExcecaoCancelamentoSolicitacao extends RuntimeException {
    private final MotivoFalhaCancelamentoSolicitacao motivo;
    private final Map<String, String> erros;

    public ExcecaoCancelamentoSolicitacao(MotivoFalhaCancelamentoSolicitacao motivo,
            String mensagem, Map<String, String> erros) {
        super(mensagem);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaCancelamentoSolicitacao getMotivo() {
        return motivo;
    }

    public Map<String, String> getErros() {
        return erros;
    }
}
