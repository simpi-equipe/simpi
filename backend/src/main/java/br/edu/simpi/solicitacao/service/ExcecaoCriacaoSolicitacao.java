package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaCriacaoSolicitacao;
import java.util.Map;

public class ExcecaoCriacaoSolicitacao extends RuntimeException {
    private final MotivoFalhaCriacaoSolicitacao motivo;
    private final Map<String, String> erros;

    public ExcecaoCriacaoSolicitacao(MotivoFalhaCriacaoSolicitacao motivo, String mensagem,
            Map<String, String> erros) {
        this(motivo, mensagem, erros, null);
    }

    public ExcecaoCriacaoSolicitacao(MotivoFalhaCriacaoSolicitacao motivo, String mensagem,
            Map<String, String> erros, Throwable causa) {
        super(mensagem, causa);
        this.motivo = motivo;
        this.erros = Map.copyOf(erros);
    }

    public MotivoFalhaCriacaoSolicitacao getMotivo() {
        return motivo;
    }

    public Map<String, String> getErros() {
        return erros;
    }
}
