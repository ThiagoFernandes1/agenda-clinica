package com.thiago.agenda.comum;

public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String recurso, Object id) {
        super(recurso + " " + id + " nao encontrado.");
    }
}
