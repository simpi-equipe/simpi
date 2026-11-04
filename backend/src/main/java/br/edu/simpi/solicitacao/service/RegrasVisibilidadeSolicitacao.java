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
        Long setorId = ator.getSetor().getId();
        return ator.getPerfil() == PerfilUsuario.ADMINISTRADOR
                || setorId.equals(solicitacao.getSetorSolicitante().getId())
                || setorId.equals(solicitacao.getSetorOrigem().getId())
                || setorId.equals(solicitacao.getSetorDestino().getId());
    }
}
