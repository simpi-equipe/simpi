package br.edu.simpi.consulta.service;

import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import java.util.Map;

public class ExcecaoConsultaPatrimonial extends RuntimeException {
    private final MotivoFalhaConsultaPatrimonial motivo;
    private final Map<String, String> erros;

    public ExcecaoConsultaPatrimonial(MotivoFalhaConsultaPatrimonial motivo,
            String mensagem, Map<String, String> erros) {
        super(mensagem);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaConsultaPatrimonial getMotivo() { return motivo; }
    public Map<String, String> getErros() { return erros; }
}
