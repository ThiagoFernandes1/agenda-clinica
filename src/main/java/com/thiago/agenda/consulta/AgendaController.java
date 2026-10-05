package com.thiago.agenda.consulta;

import com.thiago.agenda.comum.NaoEncontradoException;
import com.thiago.agenda.medico.MedicoRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Agenda de um medico em um dia: o que ja esta marcado e o que ainda da para marcar. */
@RestController
public class AgendaController {

    private final JdbcClient jdbc;
    private final MedicoRepository medicos;
    private final Clock clock;

    public AgendaController(JdbcClient jdbc, MedicoRepository medicos, Clock clock) {
        this.jdbc = jdbc;
        this.medicos = medicos;
        this.clock = clock;
    }

    public record Horario(LocalDateTime inicio, LocalDateTime fim) {
    }

    public record AgendaDoDia(int medicoId, LocalDate data, List<Horario> livres, List<Consulta> consultas) {
    }

    @GetMapping("/api/medicos/{id}/agenda")
    public AgendaDoDia agenda(@PathVariable int id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        medicos.buscar(id).orElseThrow(() -> new NaoEncontradoException("Medico", id));

        List<Horario> livres = jdbc.sql("""
                        SELECT inicio, fim
                        FROM   dbo.fn_horarios_livres(:medico, :data, :agora)
                        ORDER BY inicio
                        """)
                .param("medico", id)
                .param("data", data)
                .param("agora", LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
                .query(Horario.class)
                .list();

        List<Consulta> consultas = jdbc.sql("""
                        SELECT c.id, c.medico_id, m.nome AS medico, c.paciente_id, p.nome AS paciente,
                               c.inicio, c.fim, c.status, c.motivo_cancelamento
                        FROM   dbo.consulta c
                        JOIN   dbo.medico   m ON m.id = c.medico_id
                        JOIN   dbo.paciente p ON p.id = c.paciente_id
                        WHERE  c.medico_id = :medico
                          AND  c.inicio >= :dia AND c.inicio < :diaSeguinte
                        ORDER BY c.inicio
                        """)
                .param("medico", id)
                .param("dia", data.atStartOfDay())
                .param("diaSeguinte", data.plusDays(1).atStartOfDay())
                .query(Consulta.class)
                .list();

        return new AgendaDoDia(id, data, livres, consultas);
    }
}
