package br.edu.simpi.autenticacao;

import br.edu.simpi.configuracao.SecurityConfig;
import br.edu.simpi.erro.controller.TratamentoErrosControllerAdvice;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.usuario.CustomUserDetailsService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ContratoAutenticacaoTest.ConfiguracaoTeste.class)
@TestPropertySource(properties = {
        "jwt.secret=" + ContratoAutenticacaoTest.CHAVE_TESTE,
        "jwt.expiration=3600000"
})
class ContratoAutenticacaoTest {
    static final String CHAVE_TESTE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String EMAIL = "pessoa@exemplo.com";
    private static final String SENHA = " senha de teste ";
    private static final String HASH_SENHA = new BCryptPasswordEncoder().encode(SENHA);

    @Autowired private WebApplicationContext contexto;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JwtService servicoJwt;
    @Autowired private ObjectMapper conversorJson;
    private MockMvc mvc;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        reset(usuarios);
        Setor setor = BeanUtils.instantiateClass(Setor.class);
        ReflectionTestUtils.setField(setor, "id", 1L);
        ReflectionTestUtils.setField(setor, "nome", "Setor de teste");
        usuario = new Usuario("Pessoa de teste", EMAIL, HASH_SENHA, PerfilUsuario.COMUM, setor);
        ReflectionTestUtils.setField(usuario, "id", 1L);
        when(usuarios.buscarPorEmailComSetor(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarios.buscarPorIdComSetor(1L)).thenReturn(Optional.of(usuario));
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FilterChainProxy.class)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inexistente", "senhaIncorreta", "inativo"})
    void ocultarMotivoDaFalhaDeLogin(String cenario) throws Exception {
        String senha = SENHA;
        if (cenario.equals("inexistente")) {
            when(usuarios.buscarPorEmailComSetor(EMAIL)).thenReturn(Optional.empty());
        } else if (cenario.equals("inativo")) {
            ReflectionTestUtils.setField(usuario, "ativo", false);
        } else {
            senha = "senha incorreta";
        }
        verificarErro(login(EMAIL, senha), 401, "CREDENCIAIS_INVALIDAS")
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos."));
    }

    @Test
    void permitirNovoLoginMesmoComBearerInvalido() throws Exception {
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(corpoLogin(EMAIL, SENHA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        verify(usuarios, never()).buscarPorIdComSetor(anyLong());
    }

    @Test
    void normalizarSomenteEspacosExternosDoEmailEPreservarSenha() throws Exception {
        login("  " + EMAIL + "  ", SENHA).andExpect(status().isOk());
        verify(usuarios).buscarPorEmailComSetor(EMAIL);
        verificarErro(login(EMAIL, SENHA.trim()), 401, "CREDENCIAIS_INVALIDAS");
    }

    @Test
    void preservarMaiusculasDoEmail() throws Exception {
        verificarErro(login("  Pessoa@exemplo.com  ", SENHA), 401, "CREDENCIAIS_INVALIDAS");
        verify(usuarios).buscarPorEmailComSetor("Pessoa@exemplo.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"email\":null,\"senha\":null}",
            "{\"email\":\"\",\"senha\":\"\"}",
            "{\"email\":\"   \",\"senha\":\"   \"}",
            "{\"email\":\"invalido\",\"senha\":\"teste\"}",
            "{\"email\":\"pes soa@exemplo.com\",\"senha\":\"teste\"}",
            "{\"email\":\"pessoa@exemplo.com\"}",
            "{\"senha\":\"teste\"}"
    })
    void validarCamposAntesDeAutenticar(String corpo) throws Exception {
        verificarErro(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)), 400, "DADOS_INVALIDOS").andExpect(jsonPath("$.erros").isNotEmpty());
        verifyNoInteractions(usuarios);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{"})
    void rejeitarCorpoAusenteOuJsonMalformado(String corpo) throws Exception {
        verificarErro(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo)), 400, "DADOS_INVALIDOS");
        verifyNoInteractions(usuarios);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "invalido", "expirado", "inativo", "inexistente"})
    void protegerRotaComValidacaoJwtReal(String cenario) throws Exception {
        var requisicao = get("/api/auth/me");
        if (!cenario.equals("ausente")) {
            String token = switch (cenario) {
                case "invalido" -> "invalido";
                case "expirado" -> new JwtService(CHAVE_TESTE, -1000).gerarToken(1L);
                default -> servicoJwt.gerarToken(1L);
            };
            requisicao.header("Authorization", "Bearer " + token);
        }
        if (cenario.equals("inativo")) ReflectionTestUtils.setField(usuario, "ativo", false);
        if (cenario.equals("inexistente")) when(usuarios.buscarPorIdComSetor(1L)).thenReturn(Optional.empty());
        verificarErro(mvc.perform(requisicao), 401, "NAO_AUTENTICADO");
    }

    @Test
    void preservarUsuarioComoPrincipal() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value(EMAIL));
        verify(usuarios).buscarPorIdComSetor(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void naoIgnorarJwtNosOutrosMetodosDoLogin(String metodo) throws Exception {
        verificarErro(mvc.perform(request(HttpMethod.valueOf(metodo), "/api/auth/login")
                .header("Authorization", "Bearer invalido")), 401, "NAO_AUTENTICADO");
    }

    @Test
    void naoIgnorarJwtEmOutrasRotasPost() throws Exception {
        verificarErro(mvc.perform(post("/api/auth/me").header("Authorization", "Bearer invalido")),
                401, "NAO_AUTENTICADO");
    }

    @Test
    void negarAcessoPorSegurancaDeMetodoSemConverterPara500() throws Exception {
        verificarErro(mvc.perform(get("/teste/seguranca/metodo").header("Authorization", bearer())),
                403, "ACAO_NAO_PERMITIDA").andExpect(jsonPath("$.mensagem").value("Ação não permitida."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"autenticacao", "autenticacaoEncapsulada", "acesso", "acessoEncapsulado"})
    void propagarFalhasDeSegurancaParaCadeiaSemDuplicarResposta(String tipo) throws Exception {
        boolean autenticacao = tipo.startsWith("autenticacao");
        verificarErro(mvc.perform(get("/teste/seguranca/falha/" + tipo)
                        .header("Authorization", bearer())),
                autenticacao ? 401 : 403, autenticacao ? "NAO_AUTENTICADO" : "ACAO_NAO_PERMITIDA");
    }

    @Test
    void preservarExcecaoOriginalAoPropagarCausaDeSeguranca() {
        var tratamentoErros = new TratamentoErrosControllerAdvice();
        for (RuntimeException causa : new RuntimeException[] {
                new BadCredentialsException("detalhe interno"), new AccessDeniedException("detalhe interno")}) {
            var original = new IllegalStateException("involucro", new IllegalArgumentException(causa));
            assertSame(original, assertThrows(IllegalStateException.class,
                    () -> tratamentoErros.tratarFalhaInesperada(original)));
        }
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpoLogin(email, senha)));
    }

    private String corpoLogin(String email, String senha) {
        return conversorJson.writeValueAsString(java.util.Map.of("email", email, "senha", senha));
    }

    private String bearer() {
        return "Bearer " + servicoJwt.gerarToken(1L);
    }

    private ResultActions verificarErro(ResultActions resultado, int estado, String codigo) throws Exception {
        return resultado.andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(resposta -> {
                    var envelope = conversorJson.readTree(resposta.getResponse().getContentAsString());
                    assertEquals(3, envelope.size());
                    if (estado != 400) assertTrue(envelope.get("erros").isEmpty());
                    for (var erro : envelope.get("erros")) {
                        assertEquals(2, erro.size());
                        assertTrue(erro.has("campo") && erro.has("mensagem"));
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, AutenticacaoController.class, AutenticacaoService.class,
            JwtService.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class,
            CustomUserDetailsService.class, TratamentoAcessoNegado.class, TratamentoErrosControllerAdvice.class})
    static class ConfiguracaoTeste {
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean ObjectMapper conversorJson() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        }
        @Bean MetodoProtegido metodoProtegido() { return new MetodoProtegido(); }
        @Bean RotasTesteController rotasTesteController(MetodoProtegido metodo) {
            return new RotasTesteController(metodo);
        }
    }

    static class MetodoProtegido {
        @PreAuthorize("hasRole('ADMINISTRADOR')")
        public String executar() { return "permitido"; }
    }

    @RestController
    static class RotasTesteController {
        private final MetodoProtegido metodo;
        RotasTesteController(MetodoProtegido metodo) { this.metodo = metodo; }
        @GetMapping("/teste/seguranca/metodo")
        public String metodo() { return metodo.executar(); }
        @GetMapping("/teste/seguranca/falha/{tipo}")
        public String falhar(@PathVariable String tipo) {
            RuntimeException causa = tipo.startsWith("autenticacao")
                    ? new BadCredentialsException("detalhe interno") : new AccessDeniedException("detalhe interno");
            if (tipo.endsWith("Encapsulada")) throw new IllegalStateException("involucro", causa);
            throw causa;
        }
    }
}
