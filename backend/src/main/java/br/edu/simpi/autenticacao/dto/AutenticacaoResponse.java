package br.edu.simpi.autenticacao.dto;

public record AutenticacaoResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario) {
}