package br.edu.simpi.autenticacao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AutenticacaoRequest(
        @NotBlank @Email String email,
        @NotBlank String senha) {
    public AutenticacaoRequest {
        email = email == null ? null : email.trim();
    }
}
