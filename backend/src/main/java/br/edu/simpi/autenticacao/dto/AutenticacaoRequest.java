package br.edu.simpi.autenticacao.dto;

public record AutenticacaoRequest(
        String email,
        String senha) {
}
