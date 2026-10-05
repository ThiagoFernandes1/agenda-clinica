package com.thiago.agenda.comum;

/** Cadastro repetido (CRM, CPF) ou horario que se sobrepoe a outro. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
