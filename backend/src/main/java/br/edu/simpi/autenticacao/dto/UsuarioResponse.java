package br.edu.simpi.autenticacao.dto;

import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;

public record UsuarioResponse(
        long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        SetorResponse setor) {

    public static UsuarioResponse from(Usuario usuario) {

        SetorResponse setorResponse = null;

        if (usuario.getSetor() != null) {
            setorResponse = new SetorResponse(
                    usuario.getSetor().getId(),
                    usuario.getSetor().getNome());
        }

        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                setorResponse);
    }
}
