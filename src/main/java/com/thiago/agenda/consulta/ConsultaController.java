package com.thiago.agenda.consulta;

import com.thiago.agenda.comum.NaoEncontradoException;
import com.thiago.agenda.paciente.PacienteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
public class ConsultaController {

    private final ConsultaRepository consultas;
    private final PacienteRepository pacientes;
    private final Clock clock;

    public ConsultaController(ConsultaRepository consultas, PacienteRepository pacientes, Clock clock) {
        this.consultas = consultas;
        this.pacientes = pacientes;
        this.clock = clock;
    }

    public record NovaConsulta(@NotNull Integer medicoId, @NotNull Integer pacienteId, @NotNull LocalDateTime inicio) {
    }

    public record Cancelamento(@NotBlank @Size(max = 200) String motivo) {
    }

    @PostMapping("/api/consultas")
    @ResponseStatus(HttpStatus.CREATED)
    public Consulta agendar(@Valid @RequestBody NovaConsulta dados) {
        // a coluna guarda minutos inteiros; segundos enviados pelo cliente sao descartados
        LocalDateTime inicio = dados.inicio().truncatedTo(ChronoUnit.MINUTES);
        long id = consultas.agendar(dados.medicoId(), dados.pacienteId(), inicio, agora());
        return buscar(id);
    }

    @GetMapping("/api/consultas/{id}")
    public Consulta buscar(@PathVariable long id) {
        return consultas.buscar(id).orElseThrow(() -> new NaoEncontradoException("Consulta", id));
    }

    @PostMapping("/api/consultas/{id}/cancelamento")
    public Consulta cancelar(@PathVariable long id, @Valid @RequestBody Cancelamento dados) {
        consultas.cancelar(id, dados.motivo().trim(), agora());
        return buscar(id);
    }

    @PostMapping("/api/consultas/{id}/realizada")
    public Consulta registrarRealizada(@PathVariable long id) {
        consultas.registrarRealizada(id, agora());
        return buscar(id);
    }

    @GetMapping("/api/pacientes/{id}/consultas")
    public List<Consulta> doPaciente(@PathVariable int id) {
        pacientes.buscarPorId(id).orElseThrow(() -> new NaoEncontradoException("Paciente", id));
        return consultas.doPaciente(id);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
