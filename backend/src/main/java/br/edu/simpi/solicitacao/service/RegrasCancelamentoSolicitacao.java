package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.Usuario;
import java.util.Optional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaCancelamentoSolicitacao.*;

/** Critérios compartilhados pelo cancelamento e pela ação anunciada no DTO. */
public final class RegrasCancelamentoSolicitacao {
    private RegrasCancelamentoSolicitacao() { }

    public static Optional<MotivoFalhaCancelamentoSolicitacao> avaliar(Solicitacao solicitacao, Usuario ator) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return Optional.of(NAO_AUTENTICADO);
        }
        if (!RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, ator)
                || !solicitacao.getSolicitante().getId().equals(ator.getId())) {
            return Optional.of(ACAO_NAO_PERMITIDA);
        }
        return solicitacao.estaEmEstadoCancelavel() ? Optional.empty() : Optional.of(ESTADO_INCOMPATIVEL);
    }
}
