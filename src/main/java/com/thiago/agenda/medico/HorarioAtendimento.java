package com.thiago.agenda.medico;

import java.time.LocalTime;

/** Janela semanal de atendimento; diaSemana segue a ISO 8601 (1 = segunda ... 7 = domingo). */
public record HorarioAtendimento(Integer id, int medicoId, int diaSemana, LocalTime horaInicio, LocalTime horaFim) {
}
