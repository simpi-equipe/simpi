package br.edu.simpi.consulta.controller;

import br.edu.simpi.consulta.dto.LocalizacaoSelecaoResponse;
import br.edu.simpi.consulta.dto.SetorSelecaoResponse;
import br.edu.simpi.consulta.dto.UsuarioSelecaoResponse;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.erro.dto.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "02 - Cadastros", description = "Seletores de registros ativos, ordenados por nome e id.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "401", description = "NAO_AUTENTICADO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class))),
        @ApiResponse(responseCode = "500", description = "ERRO_INTERNO", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
})
public class CadastroConsultaController {
    private final ConsultaPatrimonialService consultas;

    public CadastroConsultaController(ConsultaPatrimonialService consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/setores")
    @Operation(summary = "Listar setores ativos")
    @ApiResponse(responseCode = "200", description = "Array de setores, inclusive vazio.", content = @Content(
            mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SetorSelecaoResponse.class))))
    public List<SetorSelecaoResponse> listarSetores(@Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.listarSetores(ator);
    }

    @GetMapping("/localizacoes")
    @Operation(summary = "Listar localizações ativas", description = "Não são restringidas pelo setor selecionado.")
    @ApiResponse(responseCode = "200", description = "Array de localizações, inclusive vazio.", content = @Content(
            mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = LocalizacaoSelecaoResponse.class))))
    public List<LocalizacaoSelecaoResponse> listarLocalizacoes(@Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.listarLocalizacoes(ator);
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Listar usuários ativos de um setor", description = "Consultar outro setor é permitido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Array de usuários; vazio se o setor não tiver usuários ativos.",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(
                            schema = @Schema(implementation = UsuarioSelecaoResponse.class)))),
            @ApiResponse(responseCode = "404", description = "RECURSO_NAO_ENCONTRADO: setor inexistente.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErroResponse.class)))
    })
    public List<UsuarioSelecaoResponse> listarUsuarios(
            @Parameter(description = "Identificador de setor existente.", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @RequestParam("setorId") Long setorId,
            @Parameter(hidden = true) @AuthenticationPrincipal Usuario ator) {
        return consultas.listarUsuarios(setorId, ator);
    }
}
