package br.edu.simpi.consulta.controller;

import br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste;
import br.edu.simpi.consulta.dto.*;
import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.usuario.Usuario;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static br.edu.simpi.consulta.ConfiguracaoMvcConsultasTeste.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ConfiguracaoMvcConsultasTeste.class)
class CadastroConsultaControllerTest {
    @Autowired private WebApplicationContext contexto;
    @Autowired private ConsultaPatrimonialService consultas;
    private MockMvc mvc;
    private Usuario ator;

    @BeforeEach
    void preparar() {
        ator = prepararAtor(contexto);
        mvc = criarMvc(contexto);
    }

    @Test
    void listarCadastrosComPrincipalAutenticado() throws Exception {
        when(consultas.listarSetores(ator)).thenReturn(List.of(new SetorSelecaoResponse(1L, "ADM", "Administração")));
        when(consultas.listarLocalizacoes(ator)).thenReturn(List.of(new LocalizacaoSelecaoResponse(2L, "SALA", "Sala")));
        when(consultas.listarUsuarios(3L, ator)).thenReturn(List.of(new UsuarioSelecaoResponse(4L, "Pessoa", 3L, "Destino")));
        mvc.perform(get("/api/setores").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[0].codigo").value("ADM"))
                .andExpect(jsonPath("$[0].nome").value("Administração"));
        mvc.perform(get("/api/localizacoes").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("SALA"));
        mvc.perform(get("/api/usuarios").param("setorId", "3").param("atorId", "999")
                        .header("X-Usuario-Id", "999").header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].setor.id").value(3))
                .andExpect(jsonPath("$[0].setor.nome").value("Destino"))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist()).andExpect(jsonPath("$[0].email").doesNotExist());
        verify(consultas).listarSetores(same(ator));
        verify(consultas).listarLocalizacoes(same(ator));
        verify(consultas).listarUsuarios(eq(3L), same(ator));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/setores", "/api/localizacoes", "/api/usuarios?setorId=3"})
    void retornarArrayVazio(String rota) throws Exception {
        when(consultas.listarSetores(ator)).thenReturn(List.of());
        when(consultas.listarLocalizacoes(ator)).thenReturn(List.of());
        when(consultas.listarUsuarios(3L, ator)).thenReturn(List.of());
        mvc.perform(get(rota).header("Authorization", bearer(contexto)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/usuarios?setorId=abc", "/api/usuarios?setorId=9223372036854775808"})
    void rejeitarParametroAusenteOuIncompativel(String rota) throws Exception {
        verificarErro(mvc.perform(get(rota).header("Authorization", bearer(contexto))), 400, "DADOS_INVALIDOS")
                .andExpect(jsonPath("$.erros[0].campo").value("setorId"));
        verifyNoInteractions(consultas);
    }

    @Test
    void delegarValidacaoDeIdAoService() throws Exception {
        when(consultas.listarUsuarios(0L, ator)).thenThrow(new ExcecaoConsultaPatrimonial(
                MotivoFalhaConsultaPatrimonial.DADOS_INVALIDOS, "interno", Map.of("setorId", "Informe um identificador positivo.")));
        verificarErro(mvc.perform(get("/api/usuarios?setorId=0").header("Authorization", bearer(contexto))),
                400, "DADOS_INVALIDOS").andExpect(jsonPath("$.erros[0].campo").value("setorId"));
        verify(consultas).listarUsuarios(eq(0L), same(ator));
    }

    @Test
    void propagarReferenciaInexistente() throws Exception {
        when(consultas.listarUsuarios(3L, ator)).thenThrow(new ExcecaoConsultaPatrimonial(
                MotivoFalhaConsultaPatrimonial.RECURSO_NAO_ENCONTRADO, "interno", Map.of("setorId", "Registro não encontrado.")));
        verificarErro(mvc.perform(get("/api/usuarios?setorId=3").header("Authorization", bearer(contexto))),
                404, "RECURSO_NAO_ENCONTRADO").andExpect(jsonPath("$.erros[0].campo").value("setorId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/setores", "/api/localizacoes", "/api/usuarios?setorId=3"})
    void rejeitarAcessoSemToken(String rota) throws Exception {
        verificarErro(mvc.perform(get(rota)), 401, "NAO_AUTENTICADO")
                .andExpect(jsonPath("$.erros").isEmpty());
        verifyNoInteractions(consultas);
    }
}
