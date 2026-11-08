package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.erro.dto.ErroResponse;
import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solicitacoes")
@Tag(name = "04 - Solicitações")
@SecurityRequirement(name = "bearerAuth")
public class SolicitacaoCriacaoController {
    private final SolicitacaoService solicitacoes;

    public SolicitacaoCriacaoController(SolicitacaoService solicitacoes) {
        this.solicitacoes = solicitacoes;
    }

    @PostMapping
    @Operation(summary = "Criar solicitação de movimentação", description = "Solicitante identificado pelo JWT. "
            + "COMUM e ADMINISTRADOR iniciam em GESTOR_AREA; GESTOR inicia em GESTOR_RESPONSAVEL. "
            + "Pedido, primeira aprovação e evento são gravados na mesma transação. Repetição pode retornar 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitação criada.",
                    headers = @Header(name = "Location", description = "Rota de consulta da solicitação criada.",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SolicitacaoResponse.class))),
            @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "ACAO_NAO_PERMITIDA", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "RECURSO_NAO_ENCONTRADO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "BEM_COM_SOLICITACAO_EM_ANDAMENTO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<SolicitacaoResponse> criar(@RequestBody CriarSolicitacaoRequest entrada,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        var resposta = solicitacoes.criar(entrada, ator);
        return ResponseEntity.created(URI.create("/api/solicitacoes/" + resposta.id())).body(resposta);
    }
}
