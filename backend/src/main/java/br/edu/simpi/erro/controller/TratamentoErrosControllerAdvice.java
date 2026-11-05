package br.edu.simpi.erro.controller;

import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.erro.dto.ErroCampoResponse;
import br.edu.simpi.erro.dto.ErroResponse;
import br.edu.simpi.solicitacao.service.ExcecaoCancelamentoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoConsultaSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoCriacaoSolicitacao;
import br.edu.simpi.solicitacao.service.ExcecaoDecisaoAprovacao;
import br.edu.simpi.solicitacao.service.ExcecaoExecucaoSolicitacao;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class TratamentoErrosControllerAdvice extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ExcecaoCriacaoSolicitacao.class)
    public ResponseEntity<Object> tratarCriacao(ExcecaoCriacaoSolicitacao excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case SOLICITANTE_INVALIDO, SOLICITANTE_INATIVO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case PERFIL_NAO_PERMITIDO -> responder(403, "ACAO_NAO_PERMITIDA", Map.of());
            case REFERENCIA_NAO_ENCONTRADA -> responder(404, "RECURSO_NAO_ENCONTRADO", excecao.getErros());
            case BEM_COM_SOLICITACAO_EM_ANDAMENTO -> responder(409, "BEM_COM_SOLICITACAO_EM_ANDAMENTO", Map.of());
            case GESTOR_DESIGNADO_INVALIDO -> responder(500, "ERRO_INTERNO", Map.of());
        };
    }

    @ExceptionHandler(ExcecaoDecisaoAprovacao.class)
    public ResponseEntity<Object> tratarDecisao(ExcecaoDecisaoAprovacao excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case NAO_AUTENTICADO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case ACAO_NAO_PERMITIDA -> responder(403, "ACAO_NAO_PERMITIDA", Map.of());
            case SOLICITACAO_NAO_ENCONTRADA -> responder(404, "SOLICITACAO_NAO_ENCONTRADA", Map.of());
            case APROVACAO_NAO_ENCONTRADA -> responder(404, "APROVACAO_NAO_ENCONTRADA", Map.of());
            case ETAPA_JA_DECIDIDA -> responder(409, "ETAPA_JA_DECIDIDA", Map.of());
            case ESTADO_INCOMPATIVEL -> responder(409, "ESTADO_INCOMPATIVEL", Map.of());
            case GESTOR_DESIGNADO_INVALIDO -> responder(500, "ERRO_INTERNO", Map.of());
        };
    }

    @ExceptionHandler(ExcecaoCancelamentoSolicitacao.class)
    public ResponseEntity<Object> tratarCancelamento(ExcecaoCancelamentoSolicitacao excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case NAO_AUTENTICADO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case ACAO_NAO_PERMITIDA -> responder(403, "ACAO_NAO_PERMITIDA", Map.of());
            case SOLICITACAO_NAO_ENCONTRADA -> responder(404, "SOLICITACAO_NAO_ENCONTRADA", Map.of());
            case ESTADO_INCOMPATIVEL -> responder(409, "ESTADO_INCOMPATIVEL", Map.of());
        };
    }

    @ExceptionHandler(ExcecaoExecucaoSolicitacao.class)
    public ResponseEntity<Object> tratarExecucao(ExcecaoExecucaoSolicitacao excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case NAO_AUTENTICADO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case ACAO_NAO_PERMITIDA -> responder(403, "ACAO_NAO_PERMITIDA", Map.of());
            case SOLICITACAO_NAO_ENCONTRADA -> responder(404, "SOLICITACAO_NAO_ENCONTRADA", Map.of());
            case ESTADO_INCOMPATIVEL -> responder(409, "ESTADO_INCOMPATIVEL", Map.of());
        };
    }

    @ExceptionHandler(ExcecaoConsultaSolicitacao.class)
    public ResponseEntity<Object> tratarConsultaSolicitacao(ExcecaoConsultaSolicitacao excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case NAO_AUTENTICADO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case ACAO_NAO_PERMITIDA -> responder(403, "ACAO_NAO_PERMITIDA", Map.of());
            case SOLICITACAO_NAO_ENCONTRADA -> responder(404, "SOLICITACAO_NAO_ENCONTRADA", Map.of());
        };
    }

    @ExceptionHandler(ExcecaoConsultaPatrimonial.class)
    public ResponseEntity<Object> tratarConsultaPatrimonial(ExcecaoConsultaPatrimonial excecao) {
        return switch (excecao.getMotivo()) {
            case DADOS_INVALIDOS -> responder(400, "DADOS_INVALIDOS", excecao.getErros());
            case NAO_AUTENTICADO -> responder(401, "NAO_AUTENTICADO", Map.of());
            case RECURSO_NAO_ENCONTRADO -> responder(404, "RECURSO_NAO_ENCONTRADO", excecao.getErros());
        };
    }

    // Rever este fallback ao integrar Spring Security, antes de ativar seus filtros/métodos.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tratarFalhaInesperada(Exception excecao) {
        return responder(500, "ERRO_INTERNO", Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception excecao, Object corpo,
            HttpHeaders cabecalhos, HttpStatusCode estado, WebRequest requisicao) {
        // Preserva inclusive os headers e corpos MVC ainda fora da padronização aprovada.
        if (estado.value() != 400 && estado.value() != 500) {
            return super.handleExceptionInternal(excecao, corpo, cabecalhos, estado, requisicao);
        }
        String codigo = estado.value() == 400 ? "DADOS_INVALIDOS" : "ERRO_INTERNO";
        List<ErroCampoResponse> erros = estado.value() == 400 ? errosDeEntrada(excecao) : List.of();
        return super.handleExceptionInternal(excecao, new ErroResponse(codigo, mensagem(codigo), erros),
                cabecalhos, estado, requisicao);
    }

    private List<ErroCampoResponse> errosDeEntrada(Exception excecao) {
        if (excecao instanceof MethodArgumentNotValidException validacao) {
            return validacao.getBindingResult().getFieldErrors().stream()
                    .map(erro -> new ErroCampoResponse(erro.getField(), "Revise o valor informado."))
                    .distinct().sorted(Comparator.comparing(ErroCampoResponse::campo)).toList();
        }
        if (excecao instanceof MissingServletRequestParameterException parametro) {
            return List.of(new ErroCampoResponse(parametro.getParameterName(), "Informe o parâmetro obrigatório."));
        }
        if (excecao instanceof MethodArgumentTypeMismatchException conversao) {
            return List.of(new ErroCampoResponse(conversao.getName(), "Informe um valor válido."));
        }
        if (excecao instanceof TypeMismatchException conversao && conversao.getPropertyName() != null) {
            return List.of(new ErroCampoResponse(conversao.getPropertyName(), "Informe um valor válido."));
        }
        if (excecao instanceof HandlerMethodValidationException validacao) {
            return validacao.getParameterValidationResults().stream()
                    .map(resultado -> resultado.getMethodParameter().getParameterName())
                    .filter(nome -> nome != null)
                    .distinct().sorted()
                    .map(nome -> new ErroCampoResponse(nome, "Revise o valor informado.")).toList();
        }
        // Não publica mensagens do parser, valores rejeitados ou campos globais inventados.
        return List.of();
    }

    private ResponseEntity<Object> responder(int estado, String codigo, Map<String, String> erros) {
        List<ErroCampoResponse> campos = erros.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(erro -> new ErroCampoResponse(erro.getKey(), erro.getValue())).toList();
        return ResponseEntity.status(estado).body(new ErroResponse(codigo, mensagem(codigo), campos));
    }

    private String mensagem(String codigo) {
        return switch (codigo) {
            case "DADOS_INVALIDOS" -> "Revise os campos informados.";
            case "NAO_AUTENTICADO" -> "Autenticação necessária.";
            case "ACAO_NAO_PERMITIDA" -> "Ação não permitida.";
            case "RECURSO_NAO_ENCONTRADO" -> "Recurso não encontrado.";
            case "SOLICITACAO_NAO_ENCONTRADA" -> "Solicitação não encontrada.";
            case "APROVACAO_NAO_ENCONTRADA" -> "Aprovação não encontrada.";
            case "BEM_COM_SOLICITACAO_EM_ANDAMENTO" -> "O bem possui solicitação em andamento.";
            case "ESTADO_INCOMPATIVEL" -> "Ação incompatível com o estado atual.";
            case "ETAPA_JA_DECIDIDA" -> "Etapa já decidida.";
            default -> "Não foi possível concluir a operação.";
        };
    }
}
