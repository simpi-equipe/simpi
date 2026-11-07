package br.edu.simpi.solicitacao.controller;

import br.edu.simpi.solicitacao.dto.PaginaSolicitacoesResponse;
import br.edu.simpi.solicitacao.dto.PesquisarSolicitacoesRequest;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.VisaoSolicitacao;
import br.edu.simpi.solicitacao.service.ConsultaSolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.erro.dto.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solicitacoes")
@Tag(name = "04 - Solicitações")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACAO_NAO_PERMITIDA", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
})
public class SolicitacaoConsultaController {
    private final ConsultaSolicitacaoService consultas;

    public SolicitacaoConsultaController(ConsultaSolicitacaoService consultas) {
        this.consultas = consultas;
    }

    @GetMapping
    @Operation(summary = "Pesquisar solicitações permitidas", description = "Filtros combinados com as permissões. "
            + "Ordenação por criadaEm e id decrescentes. Visão incompatível com o perfil retorna 403. "
            + "Lista vazia ou página além do fim retorna 200, mantendo os totais visíveis.")
    @ApiResponse(responseCode = "200", description = "Página de resumos de solicitações.", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = PaginaSolicitacoesResponse.class)))
    public PaginaSolicitacoesResponse pesquisar(
            @Parameter(description = "Parte do número da solicitação ou código do bem, sem distinguir maiúsculas. Espaços externos são removidos.")
            @RequestParam(value = "busca", required = false) String busca,
            @Parameter(description = "Estado da solicitação; omitido: sem filtro de estado.")
            @RequestParam(value = "status", required = false) StatusSolicitacao status,
            @Parameter(description = "DA_MINHA_AREA: setor participa; CRIADAS_POR_MIM: autor atual; "
                    + "AGUARDANDO_MINHA_APROVACAO: exclusiva de GESTOR elegível; ENVIOS_E_RECEBIMENTOS: fila operacional; "
                    + "TODAS: exclusiva de ADMINISTRADOR.", schema = @Schema(implementation = VisaoSolicitacao.class,
                            defaultValue = "DA_MINHA_AREA"))
            @RequestParam(value = "visao", required = false) VisaoSolicitacao visao,
            @Parameter(description = "Página a partir de zero.", schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(value = "pagina", required = false) Integer pagina,
            @Parameter(description = "Quantidade por página.", schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(value = "tamanho", required = false) Integer tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.pesquisar(new PesquisarSolicitacoesRequest(busca, status, visao, pagina, tamanho), ator);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar solicitação permitida", description = "Inclui aprovações, histórico e ações disponíveis "
            + "calculadas para o usuário autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação completa diretamente na raiz.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = SolicitacaoResponse.class))),
            @ApiResponse(responseCode = "404", description = "SOLICITACAO_NAO_ENCONTRADA", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public SolicitacaoResponse detalhar(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("id") Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.detalhar(id, ator);
    }
}
