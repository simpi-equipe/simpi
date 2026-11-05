package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.dto.ResumoSolicitacaoResponse;
import br.edu.simpi.solicitacao.dto.ResumoSolicitacaoResponse.BemResumoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.AprovacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.BemResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.EventoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.OperacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciaResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.ReferenciasResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse.SolicitanteResponse;
import br.edu.simpi.solicitacao.model.Aprovacao;
import br.edu.simpi.solicitacao.model.EventoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.solicitacao.repository.ConsultaSolicitacaoRepository.RegistroSolicitacao;
import br.edu.simpi.usuario.Usuario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoMapper {
    public ResumoSolicitacaoResponse mapearResumo(RegistroSolicitacao registro) {
        return new ResumoSolicitacaoResponse(registro.id(), registro.numero(),
                registro.criadaEm(), registro.status(),
                new BemResumoResponse(registro.bemId(), registro.bemCodigo(), registro.bemDescricao()),
                new SolicitanteResponse(registro.solicitanteId(), registro.solicitanteNome(),
                        new ReferenciaResponse(registro.setorSolicitanteId(), registro.setorSolicitanteNome())),
                new ReferenciaResponse(registro.setorOrigemId(), registro.setorOrigemNome()),
                new ReferenciaResponse(registro.setorDestinoId(), registro.setorDestinoNome()));
    }

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
        if (!RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, ator)) {
            return acoes;
        }
        if (aprovacoes.stream().anyMatch(aprovacao ->
                RegrasDecisaoAprovacao.avaliar(solicitacao, aprovacao, ator).isEmpty())) {
            acoes.add("APROVAR");
            acoes.add("RECUSAR");
        }
        if (RegrasCancelamentoSolicitacao.avaliar(solicitacao, ator).isEmpty()) {
            acoes.add("CANCELAR");
        }
        if (RegrasExecucaoSolicitacao.avaliarEnvio(solicitacao, ator).isEmpty()) {
            acoes.add("ENVIAR");
        }
        if (RegrasExecucaoSolicitacao.avaliarRecebimento(solicitacao, ator).isEmpty()) {
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
