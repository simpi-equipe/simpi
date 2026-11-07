package br.edu.simpi.autenticacao;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "01 - Autenticação")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;

    public AutenticacaoController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário", description = "Público. Retorna accessToken para uso em Authorize, "
            + "sem o prefixo Bearer. Não cria usuário nem renova automaticamente a sessão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token JWT e identidade autenticada.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = AutenticacaoResponse.class))),
            @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "CREDENCIAIS_INVALIDAS", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<?> login(@Valid @RequestBody AutenticacaoRequest request) {

        try {
            AutenticacaoResponse response = autenticacaoService.autenticar(request);

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {

            ErroResponse erro = new ErroResponse(
                    "CREDENCIAIS_INVALIDAS",
                    "E-mail ou senha inválidos.",
                    List.of());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(erro);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar identidade autenticada", description = "Retorna o usuário diretamente na raiz, sem renovar o token.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identidade atual.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<UsuarioResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }
}
