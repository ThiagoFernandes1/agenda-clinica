package com.thiago.agenda.comum;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** CPF com 11 digitos (sem pontuacao) e digitos verificadores corretos. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Cpf.Validador.class)
public @interface Cpf {

    String message() default "CPF invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Cpf, String> {

        @Override
        public boolean isValid(String cpf, ConstraintValidatorContext ctx) {
            // nulo fica a cargo do @NotBlank
            return cpf == null || valido(cpf);
        }

        public static boolean valido(String cpf) {
            if (!cpf.matches("\\d{11}")) {
                return false;
            }
            // 000.000.000-00, 111.111.111-11... passam na conta, mas nao existem
            if (cpf.chars().distinct().count() == 1) {
                return false;
            }
            return digito(cpf, 9) == cpf.charAt(9) - '0'
                    && digito(cpf, 10) == cpf.charAt(10) - '0';
        }

        private static int digito(String cpf, int posicao) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
            }
            int resto = soma % 11;
            return resto < 2 ? 0 : 11 - resto;
        }
    }
}
