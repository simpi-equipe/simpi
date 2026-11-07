package br.edu.simpi.consulta.controller;

import br.edu.simpi.consulta.dto.DetalheBemPatrimonialResponse;
import br.edu.simpi.consulta.dto.PaginaBensResponse;
import br.edu.simpi.consulta.dto.PaginaTransferenciasBemResponse;
import br.edu.simpi.consulta.dto.PesquisarBensRequest;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
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
@RequestMapping("/api/bens")
@Tag(name = "03 - Bens")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "404", description = "RECURSO_NAO_ENCONTRADO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
})
public class BemConsultaController {
    private final ConsultaPatrimonialService consultas;

    public BemConsultaController(ConsultaPatrimonialService consultas) {
        this.consultas = consultas;
    }

    @GetMapping
    @Operation(summary = "Pesquisar bens", description = "Filtros combinados; ordenação por codigo e id crescentes. "
            + "Sem resultados ou página além do fim: conteudo vazio com totais preservados.")
    @ApiResponse(responseCode = "200", description = "Página de bens.", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = PaginaBensResponse.class)))
    public PaginaBensResponse pesquisar(
            @Parameter(description = "Parte de código, plaqueta ou descrição, sem distinguir maiúsculas. Espaços externos são removidos.")
            @RequestParam(value = "busca", required = false) String busca,
            @Parameter(description = "Setor atual do bem.", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @RequestParam(value = "setorId", required = false) Long setorId,
            @Parameter(description = "Localização atual do bem.", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @RequestParam(value = "localizacaoId", required = false) Long localizacaoId,
            @Parameter(description = "Disponível = ativo e sem solicitação em andamento. Omitido: sem restrição de disponibilidade.")
            @RequestParam(value = "disponivelParaSolicitacao", required = false) Boolean disponivelParaSolicitacao,
            @Parameter(description = "Página a partir de zero.", schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(value = "pagina", required = false) Integer pagina,
            @Parameter(description = "Quantidade por página.", schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(value = "tamanho", required = false) Integer tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.pesquisarBens(new PesquisarBensRequest(busca, setorId, localizacaoId,
                disponivelParaSolicitacao, pagina, tamanho), ator);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar bem", description = "Solicitação em andamento é null quando ausente ou não visível; "
            + "a disponibilidade continua refletindo o bloqueio.")
    @ApiResponse(responseCode = "200", description = "Detalhe diretamente na raiz.", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = DetalheBemPatrimonialResponse.class)))
    public DetalheBemPatrimonialResponse detalhar(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("id") Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.detalharBem(id, ator);
    }

    @GetMapping("/{id}/transferencias")
    @Operation(summary = "Listar transferências concluídas do bem", description = "Somente pedidos visíveis ao usuário, "
            + "com origem e destino históricos. Ordenação por recebidaEm e solicitacaoId decrescentes. "
            + "Bem inativo também é consultável; página além do fim mantém os totais.")
    @ApiResponse(responseCode = "200", description = "Página de transferências, inclusive vazia.", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = PaginaTransferenciasBemResponse.class)))
    public PaginaTransferenciasBemResponse listarTransferencias(
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable("id") Long id,
            @Parameter(description = "Página a partir de zero.", schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(value = "pagina", required = false) Integer pagina,
            @Parameter(description = "Quantidade por página.", schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(value = "tamanho", required = false) Integer tamanho,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.listarTransferencias(id, pagina, tamanho, ator);
    }
}
