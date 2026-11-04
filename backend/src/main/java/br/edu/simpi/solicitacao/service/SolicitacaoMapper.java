package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.AprovacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.BemResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.EventoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.OperacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciaResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciasResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.SolicitanteResponse;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.Usuario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoMapper {
    /** Resposta da abertura; não executa as ações anunciadas. */
    public SolicitacaoResponse mapearCriacao(Solicitacao solicitacao, Aprovacao aprovacao,
            EventoSolicitacao evento, Usuario solicitante) {
        return mapear(solicitacao, List.of(aprovacao), List.of(evento), solicitante);
    }

    public SolicitacaoResponse mapear(Solicitacao solicitacao, List<Aprovacao> aprovacoes,
            List<EventoSolicitacao> eventos, Usuario ator) {
        var bem = solicitacao.getBem();
        var solicitante = solicitacao.getSolicitante();
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
                aprovacoes.stream().sorted(Comparator.comparing(Aprovacao::getEtapa))
                        .map(aprovacao -> new AprovacaoResponse(aprovacao.getId(), aprovacao.getEtapa(),
                                referencia(aprovacao.getAprovador()), aprovacao.getDecisao(),
                                aprovacao.getJustificativa(), aprovacao.getDecididaEm())).toList(),
                operacao(solicitacao.getEnviadaEm(), solicitacao.getEnviadaPor()),
                operacao(solicitacao.getRecebidaEm(), solicitacao.getRecebidaPor()),
                eventos.stream().sorted(Comparator.comparing(EventoSolicitacao::getOcorridoEm)
                                .thenComparing(EventoSolicitacao::getId))
                        .map(evento -> new EventoResponse(evento.getId(), evento.getTipo(), referencia(evento.getAutor()),
                                evento.getStatusAnterior(), evento.getStatusNovo(), evento.getDescricao(), evento.getOcorridoEm()))
                        .toList(),
                acoesDisponiveis(solicitacao, aprovacoes, ator));
    }

    private List<String> acoesDisponiveis(Solicitacao solicitacao, List<Aprovacao> aprovacoes, Usuario ator) {
        List<String> acoes = new ArrayList<>();
        if (!RegrasDecisaoAprovacao.temAcesso(solicitacao, ator)) {
            return acoes;
        }
        if (aprovacoes.stream().anyMatch(aprovacao ->
                RegrasDecisaoAprovacao.avaliar(solicitacao, aprovacao, ator).isEmpty())) {
            acoes.add("APROVAR");
            acoes.add("RECUSAR");
        }
        var estado = solicitacao.getStatus();
        boolean antesDoEnvio = estado == StatusSolicitacao.AGUARDANDO_GESTOR_AREA
                || estado == StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL || estado == StatusSolicitacao.APROVADA;
        if (antesDoEnvio && solicitacao.getEnviadaEm() == null
                && solicitacao.getSolicitante().getId().equals(ator.getId())) {
            acoes.add("CANCELAR");
        }
        if (estado == StatusSolicitacao.APROVADA
                && solicitacao.getSetorOrigem().getId().equals(ator.getSetor().getId())) {
            acoes.add("ENVIAR");
        }
        if (estado == StatusSolicitacao.EM_TRANSITO
                && solicitacao.getSetorDestino().getId().equals(ator.getSetor().getId())) {
            acoes.add("RECEBER");
        }
        return acoes;
    }

    private OperacaoResponse operacao(Instant instante, Usuario usuario) {
        return instante == null ? null : new OperacaoResponse(instante, referencia(usuario));
    }

    private ReferenciaResponse referencia(Usuario usuario) {
        return new ReferenciaResponse(usuario.getId(), usuario.getNome());
    }
}
