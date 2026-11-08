package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.erro.dto.ErroResponse;
import br.edu.simpi.solicitacao.dto.CancelarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.RecusarAprovacaoRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solicitacoes")
@Tag(name = "04 - Solicitações")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitação completa atualizada.", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = SolicitacaoResponse.class))),
        @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACAO_NAO_PERMITIDA", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "404", description = "SOLICITACAO_NAO_ENCONTRADA; nas decisões, também APROVACAO_NAO_ENCONTRADA.",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "409", description = "ESTADO_INCOMPATIVEL; nas decisões, também ETAPA_JA_DECIDIDA.",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
})
public class SolicitacaoAcaoController {
    private final SolicitacaoService solicitacoes;

    public SolicitacaoAcaoController(SolicitacaoService solicitacoes) {
        this.solicitacoes = solicitacoes;
    }

    @PostMapping("/{solicitacaoId}/aprovacoes/{aprovacaoId}/aprovar")
    @Operation(summary = "Aprovar etapa da solicitação", description = "Sem corpo. Exige gestor ativo designado para a etapa "
            + "e pertencimento atual ao setor correspondente. Cada aprovação exige uma decisão explícita, mesmo com o mesmo gestor "
            + "nas duas etapas. Repetir uma etapa decidida retorna 409; não decide a próxima etapa. Não altera o bem.")
    public SolicitacaoResponse aprovar(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("solicitacaoId") Long solicitacaoId,
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("aprovacaoId") Long aprovacaoId,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return solicitacoes.aprovar(solicitacaoId, aprovacaoId, ator);
    }

    @PostMapping("/{solicitacaoId}/aprovacoes/{aprovacaoId}/recusar")
    @Operation(summary = "Recusar etapa da solicitação", description = "Exige o gestor ativo designado para a etapa "
            + "e justificativa não vazia. Registra a decisão RECUSADA e o evento RECUSA; encerra o pedido e libera o bem "
            + "para nova solicitação, sem alterar seus dados.")
    public SolicitacaoResponse recusar(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("solicitacaoId") Long solicitacaoId,
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("aprovacaoId") Long aprovacaoId,
            @RequestBody RecusarAprovacaoRequest entrada,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return solicitacoes.recusar(solicitacaoId, aprovacaoId, entrada.justificativa(), ator);
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar solicitação própria", description = "Somente o solicitante ativo, com motivo não vazio, "
            + "em AGUARDANDO_GESTOR_AREA, AGUARDANDO_GESTOR_RESPONSAVEL ou APROVADA. Preserva decisões anteriores "
            + "e não decide etapas pendentes. Não permite cancelamento em trânsito ou após encerramento. Não altera o bem.")
    public SolicitacaoResponse cancelar(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("id") Long id,
            @RequestBody CancelarSolicitacaoRequest entrada,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return solicitacoes.cancelar(id, entrada.motivo(), ator);
    }
}
