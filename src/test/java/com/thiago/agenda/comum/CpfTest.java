package com.thiago.agenda.comum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"12345678909", "98765432100", "11144477735"})
    void aceitaCpfComDigitosCorretos(String cpf) {
        assertThat(Cpf.Validador.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678900", "12345678919", "98765432101"})
    void recusaDigitoVerificadorErrado(String cpf) {
        assertThat(Cpf.Validador.valido(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    void recusaDigitosTodosIguais(String cpf) {
        assertThat(Cpf.Validador.valido(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123.456.789-09", "1234567890", "123456789091", "abcdefghijk", ""})
    void recusaFormatoErrado(String cpf) {
        assertThat(Cpf.Validador.valido(cpf)).isFalse();
    }

    @Test
    void nuloFicaParaONotBlank() {
        assertThat(new Cpf.Validador().isValid(null, null)).isTrue();
    }
}
