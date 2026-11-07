package br.edu.simpi.autenticacao;

import br.edu.simpi.erro.dto.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TratamentoAcessoNegado implements AccessDeniedHandler {
    private final ObjectMapper conversorJson;

    public TratamentoAcessoNegado(ObjectMapper conversorJson) {
        this.conversorJson = conversorJson;
    }

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
            AccessDeniedException excecao) throws IOException {
        resposta.setStatus(HttpStatus.FORBIDDEN.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        conversorJson.writeValue(resposta.getWriter(),
                new ErroResponse("ACAO_NAO_PERMITIDA", "Ação não permitida.", List.of()));
    }
}
