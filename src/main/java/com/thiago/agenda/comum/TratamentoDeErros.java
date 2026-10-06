package com.thiago.agenda.comum;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte as excecoes em respostas RFC 9457 (application/problem+json).
 *
 * Estende ResponseEntityExceptionHandler para que os erros do proprio Spring MVC
 * (parametro faltando, JSON quebrado, rota inexistente, metodo errado) saiam no
 * mesmo formato que os erros da aplicacao, e nao no JSON padrao do Boot.
 */
@RestControllerAdvice
public class TratamentoDeErros extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NaoEncontradoException.class)
    public ProblemDetail naoEncontrado(NaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Nao encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, "Conflito", e.getMessage());
    }

    @ExceptionHandler(RegraDoBancoException.class)
    public ProblemDetail regraDoBanco(RegraDoBancoException e) {
        String titulo = switch (e.getStatus()) {
            case NOT_FOUND -> "Nao encontrado";
            case CONFLICT -> "Conflito de horario";
            default -> "Operacao nao permitida";
        };
        return problema(e.getStatus(), titulo, e.getMessage());
    }

    /** /api/medicos/abc, ?data=amanha: diz qual parametro veio no formato errado. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoErrado(MethodArgumentTypeMismatchException e) {
        String esperado = e.getRequiredType() == null ? "outro formato" : switch (e.getRequiredType().getSimpleName()) {
            case "int", "Integer", "long", "Long" -> "um numero inteiro";
            case "LocalDate" -> "uma data no formato AAAA-MM-DD";
            default -> e.getRequiredType().getSimpleName();
        };
        return problema(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "'" + e.getName() + "' recebeu \"" + e.getValue() + "\", mas espera " + esperado + ".");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getObjectName(), erro.getDefaultMessage()));

        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Dados invalidos", "Corrija os campos indicados.");
        pd.setProperty("campos", campos);
        return ResponseEntity.badRequest().body(pd);
    }

    static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        return pd;
    }
}
