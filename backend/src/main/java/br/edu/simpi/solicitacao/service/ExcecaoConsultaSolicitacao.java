package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaConsultaSolicitacao;
import java.util.Map;

public class ExcecaoConsultaSolicitacao extends RuntimeException {
    private final MotivoFalhaConsultaSolicitacao motivo;
    private final Map<String, String> erros;

    public ExcecaoConsultaSolicitacao(MotivoFalhaConsultaSolicitacao motivo,
            String mensagem, Map<String, String> erros) {
        super(mensagem);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaConsultaSolicitacao getMotivo() { return motivo; }
    public Map<String, String> getErros() { return erros; }
}
