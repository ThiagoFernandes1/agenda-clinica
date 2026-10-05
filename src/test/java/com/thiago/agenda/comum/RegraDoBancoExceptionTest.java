package com.thiago.agenda.comum;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class RegraDoBancoExceptionTest {

    private static DataAccessException falha(int numero, String mensagem) {
        return new UncategorizedSQLException("EXEC", "EXEC dbo.sp", new SQLException(mensagem, "S0001", numero));
    }

    @Test
    void numeroDoThrowViraStatusHttp() {
        assertThat(RegraDoBancoException.de(falha(50409, "O medico ja tem uma consulta nesse horario.")))
                .hasValueSatisfying(e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("O medico ja tem uma consulta nesse horario.");
                });
        assertThat(RegraDoBancoException.de(falha(50404, "x"))).map(RegraDoBancoException::getStatus)
                .contains(HttpStatus.NOT_FOUND);
        assertThat(RegraDoBancoException.de(falha(50422, "x"))).map(RegraDoBancoException::getStatus)
                .contains(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void errosDoProprioSqlServerContinuamComoEstao() {
        // 1205 = deadlock, 2627 = chave duplicada: nao sao regras de negocio
        assertThat(RegraDoBancoException.de(falha(1205, "deadlock"))).isEmpty();
        assertThat(RegraDoBancoException.de(falha(2627, "duplicada"))).isEmpty();
    }

    @Test
    void numerosQueNaoSaoStatus4xxSaoIgnorados() {
        assertThat(RegraDoBancoException.de(falha(50500, "x"))).isEmpty();
        assertThat(RegraDoBancoException.de(falha(50001, "x"))).isEmpty();
    }
}
