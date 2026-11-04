package br.edu.simpi.consulta.dto;

public record UsuarioSelecaoResponse(Long id, String nome, SetorResponse setor) {
    public UsuarioSelecaoResponse(Long id, String nome, Long setorId, String setorNome) {
        this(id, nome, new SetorResponse(setorId, setorNome));
    }

    public record SetorResponse(Long id, String nome) { }
}
