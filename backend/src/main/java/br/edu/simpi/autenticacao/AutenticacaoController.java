package br.edu.simpi.autenticacao;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.simpi.autenticacao.dto.AutenticacaoRequest;
import br.edu.simpi.autenticacao.dto.AutenticacaoResponse;
import br.edu.simpi.autenticacao.dto.UsuarioResponse;
import br.edu.simpi.erro.dto.ErroResponse;
import br.edu.simpi.usuario.Usuario;
import org.springframework.security.core.AuthenticationException;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;

    public AutenticacaoController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AutenticacaoRequest request) {

        try {
            AutenticacaoResponse response = autenticacaoService.autenticar(request);

            return ResponseEntity.ok(response);

        } catch (DisabledException e) {

            ErroResponse erro = new ErroResponse(
                    "USUARIO_DESATIVADO",
                    "Usuário desativado.",
                    List.of());

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(erro);

        } catch (AuthenticationException e) {

            ErroResponse erro = new ErroResponse(
                    "CREDENCIAIS_INVALIDAS",
                    "E-mail ou senha inválidos.",
                    List.of());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(erro);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }
}
