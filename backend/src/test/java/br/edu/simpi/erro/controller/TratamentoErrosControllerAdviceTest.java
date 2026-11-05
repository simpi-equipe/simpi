package br.edu.simpi.erro.controller;

import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.solicitacao.enums.*;
import br.edu.simpi.solicitacao.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TratamentoErrosControllerAdviceTest {
    private MockMvc mvc;
    private FalhasTesteController controlador;
    private LocalValidatorFactoryBean validador;

    @BeforeEach
    void preparar() {
        controlador = new FalhasTesteController();
        validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new TratamentoErrosControllerAdvice())
                .setValidator(validador).build();
    }

    @AfterEach
    void fecharValidador() {
        validador.close();
    }

    static Stream<Arguments> motivosDeNegocio() {
        // Matriz do contrato: cada linha especifica família, motivo, HTTP e código público.
        return """
                criacao DADOS_INVALIDOS 400 DADOS_INVALIDOS
                criacao SOLICITANTE_INVALIDO 401 NAO_AUTENTICADO
                criacao SOLICITANTE_INATIVO 401 NAO_AUTENTICADO
                criacao PERFIL_NAO_PERMITIDO 403 ACAO_NAO_PERMITIDA
                criacao REFERENCIA_NAO_ENCONTRADA 404 RECURSO_NAO_ENCONTRADO
                criacao BEM_COM_SOLICITACAO_EM_ANDAMENTO 409 BEM_COM_SOLICITACAO_EM_ANDAMENTO
                criacao GESTOR_DESIGNADO_INVALIDO 500 ERRO_INTERNO
                decisao DADOS_INVALIDOS 400 DADOS_INVALIDOS
                decisao NAO_AUTENTICADO 401 NAO_AUTENTICADO
                decisao ACAO_NAO_PERMITIDA 403 ACAO_NAO_PERMITIDA
                decisao SOLICITACAO_NAO_ENCONTRADA 404 SOLICITACAO_NAO_ENCONTRADA
                decisao APROVACAO_NAO_ENCONTRADA 404 APROVACAO_NAO_ENCONTRADA
                decisao ETAPA_JA_DECIDIDA 409 ETAPA_JA_DECIDIDA
                decisao ESTADO_INCOMPATIVEL 409 ESTADO_INCOMPATIVEL
                decisao GESTOR_DESIGNADO_INVALIDO 500 ERRO_INTERNO
                cancelamento DADOS_INVALIDOS 400 DADOS_INVALIDOS
                cancelamento NAO_AUTENTICADO 401 NAO_AUTENTICADO
                cancelamento ACAO_NAO_PERMITIDA 403 ACAO_NAO_PERMITIDA
                cancelamento SOLICITACAO_NAO_ENCONTRADA 404 SOLICITACAO_NAO_ENCONTRADA
                cancelamento ESTADO_INCOMPATIVEL 409 ESTADO_INCOMPATIVEL
                execucao DADOS_INVALIDOS 400 DADOS_INVALIDOS
                execucao NAO_AUTENTICADO 401 NAO_AUTENTICADO
                execucao ACAO_NAO_PERMITIDA 403 ACAO_NAO_PERMITIDA
                execucao SOLICITACAO_NAO_ENCONTRADA 404 SOLICITACAO_NAO_ENCONTRADA
                execucao ESTADO_INCOMPATIVEL 409 ESTADO_INCOMPATIVEL
                consultaSolicitacao DADOS_INVALIDOS 400 DADOS_INVALIDOS
                consultaSolicitacao NAO_AUTENTICADO 401 NAO_AUTENTICADO
                consultaSolicitacao ACAO_NAO_PERMITIDA 403 ACAO_NAO_PERMITIDA
                consultaSolicitacao SOLICITACAO_NAO_ENCONTRADA 404 SOLICITACAO_NAO_ENCONTRADA
                consultaPatrimonial DADOS_INVALIDOS 400 DADOS_INVALIDOS
                consultaPatrimonial NAO_AUTENTICADO 401 NAO_AUTENTICADO
                consultaPatrimonial RECURSO_NAO_ENCONTRADO 404 RECURSO_NAO_ENCONTRADO
                """.lines().map(linha -> linha.split(" "))
                .map(colunas -> Arguments.of(colunas[0], colunas[1], Integer.parseInt(colunas[2]), colunas[3]));
    }

    @ParameterizedTest
    @MethodSource("motivosDeNegocio")
    void traduzirTodosOsMotivos(String familia, String motivo, int estado, String codigo) throws Exception {
        controlador.falha = criarFalha(familia, motivo, Map.of());
        verificarErro(mvc.perform(get("/teste/erros/falha")), estado, codigo)
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    private static RuntimeException criarFalha(String familia, String motivo, Map<String, String> erros) {
        String mensagem = "detalhe-interno-sigiloso";
        return switch (familia) {
            case "criacao" -> new ExcecaoCriacaoSolicitacao(MotivoFalhaCriacaoSolicitacao.valueOf(motivo), mensagem, erros);
            case "decisao" -> new ExcecaoDecisaoAprovacao(MotivoFalhaDecisaoAprovacao.valueOf(motivo), mensagem, erros);
            case "cancelamento" -> new ExcecaoCancelamentoSolicitacao(MotivoFalhaCancelamentoSolicitacao.valueOf(motivo), mensagem, erros);
            case "execucao" -> new ExcecaoExecucaoSolicitacao(MotivoFalhaExecucaoSolicitacao.valueOf(motivo), mensagem, erros);
            case "consultaSolicitacao" -> new ExcecaoConsultaSolicitacao(MotivoFalhaConsultaSolicitacao.valueOf(motivo), mensagem, erros);
            case "consultaPatrimonial" -> new ExcecaoConsultaPatrimonial(MotivoFalhaConsultaPatrimonial.valueOf(motivo), mensagem, erros);
            default -> throw new IllegalArgumentException("Família de teste desconhecida.");
        };
    }

    @Test
    void preservarCamposDoServiceComOrdemEstavel() throws Exception {
        controlador.falha = criarFalha("criacao", "DADOS_INVALIDOS",
                Map.of("justificativa", "Informe a justificativa.", "bemId", "Informe o identificador."));
        verificarErro(mvc.perform(get("/teste/erros/falha")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.mensagem").value("Revise os campos informados."))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].campo").value("bemId"))
                .andExpect(jsonPath("$.erros[0].length()").value(2))
                .andExpect(jsonPath("$.erros[1].campo").value("justificativa"))
                .andExpect(jsonPath("$.erros[1].mensagem").value("Informe a justificativa."));
    }

    @Test
    void ocultarDetalhesDoGestorMesmoComErrosInternos() throws Exception {
        for (String familia : new String[]{"criacao", "decisao"}) {
            controlador.falha = criarFalha(familia, "GESTOR_DESIGNADO_INVALIDO", Map.of("gestor", "detalhe-interno-sigiloso"));
            verificarErro(mvc.perform(get("/teste/erros/falha")), 500, "ERRO_INTERNO")
                    .andExpect(jsonPath("$.erros.length()").value(0));
        }
    }

    @Test
    void ocultarMensagemECausaInesperadas() throws Exception {
        controlador.falha = new IllegalStateException("SQL senha=segredo token=credencial",
                new RuntimeException("detalhe-interno-sigiloso"));
        verificarErro(mvc.perform(get("/teste/erros/falha")), 500, "ERRO_INTERNO")
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("segredo"))))
                .andExpect(content().string(not(containsString("credencial"))));
    }

    @Test
    void ocultarCausaDoConflito() throws Exception {
        controlador.falha = new ExcecaoCriacaoSolicitacao(
                MotivoFalhaCriacaoSolicitacao.BEM_COM_SOLICITACAO_EM_ANDAMENTO,
                "detalhe-interno-sigiloso", Map.of(), new RuntimeException("SQL senha=segredo"));
        verificarErro(mvc.perform(get("/teste/erros/falha")), 409, "BEM_COM_SOLICITACAO_EM_ANDAMENTO")
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void tratarParametroObrigatorioAusente() throws Exception {
        verificarErro(mvc.perform(get("/teste/erros/parametros")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("setorId"));
    }

    @Test
    void tratarConversoesDeRotaEQuery() throws Exception {
        verificarErro(mvc.perform(get("/teste/erros/rota/invalido")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("id"));
        for (String[] parametro : new String[][]{{"setorId", "abc"}, {"ativo", "talvez"}, {"status", "DESCONHECIDO"}}) {
            var requisicao = get("/teste/erros/parametros")
                    .param(parametro[0], parametro[1]);
            if (!"setorId".equals(parametro[0])) {
                requisicao.param("setorId", "1");
            }
            verificarErro(mvc.perform(requisicao), 400, "DADOS_INVALIDOS")
                    .andExpect(jsonPath("$.erros[0].campo").value(parametro[0]));
        }
    }

    @Test
    void tratarBindingDeFiltros() throws Exception {
        verificarErro(mvc.perform(get("/teste/erros/filtros").param("pagina", "abc")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("pagina"));
    }

    @Test
    void tratarValidacaoDeCorpoEParametro() throws Exception {
        verificarErro(mvc.perform(post("/teste/erros/corpo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"justificativa\":\" \"}")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("justificativa"));
        verificarErro(mvc.perform(get("/teste/erros/validacao").param("tamanho", "0")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("tamanho"));
    }

    @Test
    void tratarErroGlobalSemInventarCampo() throws Exception {
        verificarErro(mvc.perform(post("/teste/erros/global").contentType(MediaType.APPLICATION_JSON)
                .content("{\"justificativa\":\"Texto\"}")), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros.length()").value(0));
    }

    @Test
    void tratarCorpoAusenteJsonMalformadoEValoresInvalidos() throws Exception {
        for (String corpo : new String[]{"", "{", "{\"numero\":\"segredo\"}", "{\"status\":\"segredo\"}"}) {
            verificarErro(mvc.perform(post("/teste/erros/corpo").contentType(MediaType.APPLICATION_JSON)
                    .content(corpo)), 400, "DADOS_INVALIDOS")
                    .andExpect(jsonPath("$.erros.length()").value(0))
                    .andExpect(content().string(not(containsString("segredo"))));
        }
    }

    @Test
    void preservarTratamentoPadraoDeRotaMetodoENegociacao() throws Exception {
        mvc.perform(get("/teste/erros/inexistente")).andExpect(status().isNotFound());
        mvc.perform(put("/teste/erros/corpo")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().string(not(containsString("ERRO_INTERNO"))));
        mvc.perform(post("/teste/erros/corpo").contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", containsString("application/json")))
                .andExpect(content().string(not(containsString("DADOS_INVALIDOS"))));
        mvc.perform(post("/teste/erros/corpo").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(not(containsString("ERRO_INTERNO"))));
    }

    private ResultActions verificarErro(ResultActions resultado, int estado, String codigo) throws Exception {
        return resultado.andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(jsonPath("$.solicitacaoExistenteId").doesNotExist())
                .andExpect(content().string(not(containsString("detalhe-interno-sigiloso"))));
    }

    // O perfil impede descoberta acidental pelos testes que carregam a aplicação inteira.
    // O standaloneSetup recebe a instância explicitamente e não depende de ativar o perfil.
    @RestController
    @Profile("teste-erros-mvc")
    @RequestMapping("/teste/erros")
    static class FalhasTesteController {
        private RuntimeException falha;

        @GetMapping("/falha")
        public void falhar() { throw falha; }

        @GetMapping("/rota/{id}")
        public void rota(@PathVariable("id") Long id) { }

        @GetMapping("/parametros")
        public void parametros(@RequestParam("setorId") Long setorId,
                @RequestParam(value = "ativo", required = false) Boolean ativo,
                @RequestParam(value = "status", required = false) StatusSolicitacao status) { }

        @GetMapping("/filtros")
        public void filtros(@ModelAttribute FiltrosTeste filtros) { }

        @GetMapping("/validacao")
        public void validar(@RequestParam("tamanho") @Min(1) int tamanho) { }

        @PostMapping(value = "/corpo", consumes = "application/json", produces = "application/json")
        public EntradaTeste corpo(@Valid @RequestBody EntradaTeste entrada) { return entrada; }

        @PostMapping("/global")
        public void global(@Valid @RequestBody EntradaGlobalTeste entrada) { }

        @InitBinder("entradaGlobalTeste")
        public void configurarValidacaoGlobal(WebDataBinder vinculador) {
            vinculador.addValidators(new Validator() {
                @Override
                public boolean supports(Class<?> tipo) { return EntradaGlobalTeste.class.equals(tipo); }

                @Override
                public void validate(Object entrada, Errors erros) {
                    erros.reject("invalido", "detalhe-interno-sigiloso");
                }
            });
        }
    }

    public record EntradaTeste(@NotBlank String justificativa, Long numero, StatusSolicitacao status) { }
    public record EntradaGlobalTeste(String justificativa) { }
    public record FiltrosTeste(Integer pagina) { }
}
