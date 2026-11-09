package br.edu.simpi.configuracao;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.criarMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
@TestPropertySource(locations = "classpath:application.properties", properties =
        "CORS_ORIGENS_PERMITIDAS=https://frontend.simpi.example, https://outro.simpi.example")
class ConfiguracaoCorsPublicacaoTest {
    @Autowired private WebApplicationContext contexto;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = criarMvc(contexto);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://frontend.simpi.example", "https://outro.simpi.example"})
    void aceitarOrigensConfiguradasPelaVariavelDeAmbiente(String origem) throws Exception {
        mvc.perform(options("/api/solicitacoes").header("Origin", origem)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origem));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173", "https://nao-permitido.example"})
    void substituirOrigensLocaisPelaListaPublicada(String origem) throws Exception {
        mvc.perform(options("/api/bens").header("Origin", origem)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
