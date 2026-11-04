package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaDecisaoAprovacao;
import java.util.Map;

public class ExcecaoDecisaoAprovacao extends RuntimeException {
    private final MotivoFalhaDecisaoAprovacao motivo;
    private final Map<String, String> erros;

    public ExcecaoDecisaoAprovacao(MotivoFalhaDecisaoAprovacao motivo, String mensagem,
            Map<String, String> erros) {
        super(mensagem);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaDecisaoAprovacao getMotivo() {
        return motivo;
    }

    public Map<String, String> getErros() {
        return erros;
    }
}
