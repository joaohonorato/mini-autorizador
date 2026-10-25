package br.com.vr.miniautorizador.api;

import br.com.vr.miniautorizador.domain.compartilhado.ValorInvalidoException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaInvalidaException;
import br.com.vr.miniautorizador.domain.transacao.ChaveIdempotenciaReutilizadaException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Traduz exceções para HTTP. Entradas inválidas respondem 400 em formato ProblemDetail, com um
 * {@code detail} em português que diz qual campo falhou. Recusas de transação não passam por aqui:
 * são um resultado, tratado em {@code TransacaoController}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Map<String, String> MOTIVO_POR_RESTRICAO = Map.of(
            "NotBlank", "não pode ficar em branco",
            "NotNull", "é obrigatório",
            "Positive", "deve ser maior que zero");
    private static final String MOTIVO_PADRAO = "é inválido";
    private static final String CORPO_AUSENTE_OU_MALFORMADO = "Corpo da requisição ausente ou malformado";

    @ExceptionHandler(ValorInvalidoException.class)
    ProblemDetail valorInvalido(ValorInvalidoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaInvalidaException.class)
    ProblemDetail chaveInvalida(ChaveIdempotenciaInvalidaException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaReutilizadaException.class)
    ProblemDetail chaveReutilizada(ChaveIdempotenciaReutilizadaException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException excecao, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        String camposInvalidos = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + " " + MOTIVO_POR_RESTRICAO.getOrDefault(erro.getCode(), MOTIVO_PADRAO))
                .sorted()
                .collect(Collectors.joining("; "));
        return detalhar(excecao, "Campos inválidos: " + camposInvalidos, cabecalhos, status, requisicao);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException excecao, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        String detalhe = campoComTipoInvalido(excecao)
                .map(campo -> "Campo " + campo + " com tipo ou valor inválido")
                .orElse(CORPO_AUSENTE_OU_MALFORMADO);
        return detalhar(excecao, detalhe, cabecalhos, status, requisicao);
    }

    /**
     * O caminho do campo no JSON, quando o erro é de tipo ou de valor (um número onde se espera texto,
     * por exemplo). JSON malformado ou corpo ausente não têm campo.
     */
    private static Optional<String> campoComTipoInvalido(HttpMessageNotReadableException excecao) {
        return Optional.ofNullable(excecao.getCause())
                .filter(JsonMappingException.class::isInstance)
                .map(JsonMappingException.class::cast)
                .map(erro -> erro.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(".")))
                .filter(caminho -> !caminho.isEmpty());
    }

    private ResponseEntity<Object> detalhar(
            Exception excecao, String detalhe, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        return handleExceptionInternal(excecao, ProblemDetail.forStatusAndDetail(status, detalhe), cabecalhos, status, requisicao);
    }
}
