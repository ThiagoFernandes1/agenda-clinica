package com.thiago.agenda.consulta;

import java.time.LocalDateTime;

public record Consulta(Long id,
                       Integer medicoId, String medico,
                       Integer pacienteId, String paciente,
                       LocalDateTime inicio, LocalDateTime fim,
                       String status, String motivoCancelamento) {
}
