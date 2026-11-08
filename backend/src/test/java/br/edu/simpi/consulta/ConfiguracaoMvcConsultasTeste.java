package br.edu.simpi.consulta;

import br.edu.simpi.autenticacao.CustomAuthenticationEntryPoint;
import br.edu.simpi.autenticacao.JwtAuthenticationFilter;
import br.edu.simpi.autenticacao.JwtService;
import br.edu.simpi.autenticacao.TratamentoAcessoNegado;
import br.edu.simpi.configuracao.SecurityConfig;
import br.edu.simpi.consulta.controller.BemConsultaController;
import br.edu.simpi.consulta.controller.CadastroConsultaController;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.erro.controller.TratamentoErrosControllerAdvice;
import br.edu.simpi.setor.Setor;
import br.edu.simpi.solicitacao.controller.SolicitacaoConsultaController;
import br.edu.simpi.solicitacao.controller.SolicitacaoCriacaoController;
import br.edu.simpi.solicitacao.service.ConsultaSolicitacaoService;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.CustomUserDetailsService;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import java.util.Optional;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration(proxyBeanMethods = false)
@EnableWebMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomAuthenticationEntryPoint.class,
        TratamentoAcessoNegado.class, CustomUserDetailsService.class, TratamentoErrosControllerAdvice.class,
        CadastroConsultaController.class, BemConsultaController.class, SolicitacaoConsultaController.class,
        SolicitacaoCriacaoController.class})
public class ConfiguracaoMvcConsultasTeste {
    @Bean ConsultaPatrimonialService consultasPatrimoniais() { return mock(ConsultaPatrimonialService.class); }
    @Bean ConsultaSolicitacaoService consultasSolicitacoes() { return mock(ConsultaSolicitacaoService.class); }
    @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
    @Bean SolicitacaoService solicitacoes() {
        return mock(SolicitacaoService.class);
    }
    @Bean ObjectMapper conversorJson() { return JsonMapper.builder().build(); }
    @Bean JwtService servicoJwt() {
        return new JwtService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 3600000);
    }

    public static Usuario prepararAtor(WebApplicationContext contexto) {
        reset(contexto.getBean(ConsultaPatrimonialService.class),
                contexto.getBean(ConsultaSolicitacaoService.class), contexto.getBean(UsuarioRepository.class),
                contexto.getBean(SolicitacaoService.class));
        Setor setor = BeanUtils.instantiateClass(Setor.class);
        ReflectionTestUtils.setField(setor, "id", 1L);
        ReflectionTestUtils.setField(setor, "nome", "Setor de teste");
        Usuario ator = new Usuario("Pessoa de teste", "pessoa@exemplo.com", "hash-teste",
                PerfilUsuario.COMUM, setor);
        ReflectionTestUtils.setField(ator, "id", 7L);
        when(contexto.getBean(UsuarioRepository.class).buscarPorIdComSetor(7L)).thenReturn(Optional.of(ator));
        return ator;
    }

    public static MockMvc criarMvc(WebApplicationContext contexto) {
        return MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FilterChainProxy.class)).build();
    }

    public static String bearer(WebApplicationContext contexto) {
        return "Bearer " + contexto.getBean(JwtService.class).gerarToken(7L);
    }

    public static ResultActions verificarErro(ResultActions resultado, int estado, String codigo) throws Exception {
        return resultado.andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.mensagem").isString())
                .andExpect(jsonPath("$.erros").isArray())
                .andExpect(resposta -> {
                    var corpo = JsonMapper.builder().build().readTree(resposta.getResponse().getContentAsString());
                    assertEquals(3, corpo.size());
                    for (var erro : corpo.get("erros")) {
                        assertEquals(2, erro.size());
                        assertTrue(erro.has("campo") && erro.has("mensagem"));
                    }
                });
    }
}
