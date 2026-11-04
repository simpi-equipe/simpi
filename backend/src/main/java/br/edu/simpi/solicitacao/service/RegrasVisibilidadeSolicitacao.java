package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.model.Solicitacao;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;

/** Visibilidade pelos setores preservados no pedido, com consulta global administrativa. */
public final class RegrasVisibilidadeSolicitacao {
    private RegrasVisibilidadeSolicitacao() { }

    public static boolean temAcesso(Solicitacao solicitacao, Usuario ator) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return false;
        }
        return temAcesso(ator, solicitacao.getSetorSolicitante().getId(),
                solicitacao.getSetorOrigem().getId(), solicitacao.getSetorDestino().getId());
    }

    /** Mesma regra para consultas projetadas; os IDs são sempre os preservados no pedido. */
    public static boolean temAcesso(Usuario ator, Long setorSolicitanteId, Long setorOrigemId, Long setorDestinoId) {
        if (ator == null || ator.getId() == null || !ator.isAtivo()) {
            return false;
        }
        Long setorId = ator.getSetor().getId();
        return ator.getPerfil() == PerfilUsuario.ADMINISTRADOR
                || setorId.equals(setorSolicitanteId)
                || setorId.equals(setorOrigemId)
                || setorId.equals(setorDestinoId);
    }
}
