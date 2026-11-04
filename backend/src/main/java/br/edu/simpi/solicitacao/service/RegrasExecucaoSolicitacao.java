package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao;
import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.Usuario;
import java.util.Optional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaExecucaoSolicitacao.*;

/** Autorização e estado compartilhados pelas operações e pelas ações do DTO. */
public final class RegrasExecucaoSolicitacao {
    private RegrasExecucaoSolicitacao() { }

    public static Optional<MotivoFalhaExecucaoSolicitacao> avaliarEnvio(Solicitacao solicitacao, Usuario ator) {
        var impedimento = avaliarPermissao(solicitacao, ator, solicitacao.getSetorOrigem().getId());
        if (impedimento.isPresent()) {
            return impedimento;
        }
        return solicitacao.estaEmEstadoDeEnvio() ? Optional.empty() : Optional.of(ESTADO_INCOMPATIVEL);
    }

    public static Optional<MotivoFalhaExecucaoSolicitacao> avaliarRecebimento(Solicitacao solicitacao, Usuario ator) {
        var impedimento = avaliarPermissaoRecebimento(solicitacao, ator);
        if (impedimento.isPresent()) {
            return impedimento;
        }
        return solicitacao.estaEmEstadoDeRecebimento() ? Optional.empty() : Optional.of(ESTADO_INCOMPATIVEL);
    }

    /** Usada na leitura preliminar; o estado só é validado depois de adquirir os locks. */
    public static Optional<MotivoFalhaExecucaoSolicitacao> avaliarPermissaoRecebimento(
            Solicitacao solicitacao, Usuario ator) {
        return avaliarPermissao(solicitacao, ator, solicitacao.getSetorDestino().getId());
    }

    private static Optional<MotivoFalhaExecucaoSolicitacao> avaliarPermissao(
            Solicitacao solicitacao, Usuario ator, Long setorExigido) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return Optional.of(NAO_AUTENTICADO);
        }
        if (!RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, ator)
                || !setorExigido.equals(ator.getSetor().getId())) {
            return Optional.of(ACAO_NAO_PERMITIDA);
        }
        return Optional.empty();
    }
}
