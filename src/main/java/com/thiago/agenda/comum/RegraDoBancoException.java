package com.thiago.agenda.comum;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Erro de negocio lancado por uma procedure com THROW 50xxx. Os tres ultimos
 * digitos do numero sao o status HTTP (50404 -> 404, 50409 -> 409, 50422 -> 422),
 * e a mensagem do THROW vai direto para o cliente.
 */
public class RegraDoBancoException extends RuntimeException {

    private final HttpStatus status;

    public RegraDoBancoException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Extrai o erro de negocio de uma falha de acesso a dados, se for um. */
    public static Optional<RegraDoBancoException> de(DataAccessException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql) {
            int numero = sql.getErrorCode();
            if (numero >= 50000 && numero <= 50999) {
                HttpStatus status = HttpStatus.resolve(numero - 50000);
                if (status != null && status.is4xxClientError()) {
                    return Optional.of(new RegraDoBancoException(status, sql.getMessage()));
                }
            }
        }
        return Optional.empty();
    }
}
