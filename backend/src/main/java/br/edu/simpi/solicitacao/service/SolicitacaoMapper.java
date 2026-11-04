package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.AprovacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.BemResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.EventoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciaResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciasResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.SolicitanteResponse;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.Usuario;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoMapper {
    /** Resposta da abertura; não executa as ações anunciadas. */
    public SolicitacaoResponse mapearCriacao(Solicitacao solicitacao, Aprovacao aprovacao,
            EventoSolicitacao evento, Usuario solicitante) {
        var bem = solicitacao.getBem();
        List<String> acoes = new ArrayList<>();
        if (aprovacao.getAprovador().getId().equals(solicitante.getId())) {
            acoes.add("APROVAR");
            acoes.add("RECUSAR");
        }
        acoes.add("CANCELAR");
        return new SolicitacaoResponse(solicitacao.getId(), solicitacao.getNumero(),
                solicitacao.getStatus(), solicitacao.getCriadaEm(), solicitacao.getJustificativa(),
                new BemResponse(bem.getId(), bem.getCodigo(), bem.getPlaqueta(), bem.getDescricao()),
                new SolicitanteResponse(solicitante.getId(), solicitante.getNome(),
                        new ReferenciaResponse(solicitacao.getSetorSolicitante().getId(),
                                solicitacao.getSetorSolicitante().getNome())),
                new ReferenciasResponse(
                        new ReferenciaResponse(solicitacao.getSetorOrigem().getId(), solicitacao.getSetorOrigem().getNome()),
                        new ReferenciaResponse(solicitacao.getLocalizacaoOrigem().getId(), solicitacao.getLocalizacaoOrigem().getNome()),
                        referencia(solicitacao.getResponsavelOrigem())),
                new ReferenciasResponse(
                        new ReferenciaResponse(solicitacao.getSetorDestino().getId(), solicitacao.getSetorDestino().getNome()),
                        new ReferenciaResponse(solicitacao.getLocalizacaoDestino().getId(), solicitacao.getLocalizacaoDestino().getNome()),
                        referencia(solicitacao.getResponsavelDestino())),
                List.of(new AprovacaoResponse(aprovacao.getId(), aprovacao.getEtapa(),
                        referencia(aprovacao.getAprovador()), aprovacao.getDecisao(),
                        aprovacao.getJustificativa(), aprovacao.getDecididaEm())),
                null, null,
                List.of(new EventoResponse(evento.getId(), evento.getTipo(), referencia(evento.getAutor()),
                        evento.getStatusAnterior(), evento.getStatusNovo(), evento.getDescricao(), evento.getOcorridoEm())),
                acoes);
    }

    private ReferenciaResponse referencia(Usuario usuario) {
        return new ReferenciaResponse(usuario.getId(), usuario.getNome());
    }
}
