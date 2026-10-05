package com.thiago.agenda.paciente;

import com.thiago.agenda.comum.ConflitoException;
import com.thiago.agenda.comum.Cpf;
import com.thiago.agenda.comum.NaoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteRepository pacientes;

    public PacienteController(PacienteRepository pacientes) {
        this.pacientes = pacientes;
    }

    public record NovoPaciente(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Cpf String cpf,
            @Email @Size(max = 200) String email,
            @Size(max = 20) String telefone) {
    }

    @GetMapping
    public List<Paciente> buscar(@RequestParam(required = false) String nome) {
        return pacientes.buscar(nome == null || nome.isBlank() ? null : nome.trim());
    }

    @GetMapping("/{id}")
    public Paciente buscarPorId(@PathVariable int id) {
        return pacientes.buscarPorId(id).orElseThrow(() -> new NaoEncontradoException("Paciente", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Paciente cadastrar(@Valid @RequestBody NovoPaciente dados) {
        Paciente paciente = new Paciente(null, dados.nome().trim(), dados.cpf(), dados.email(), dados.telefone());
        try {
            int id = pacientes.inserir(paciente);
            return new Paciente(id, paciente.nome(), paciente.cpf(), paciente.email(), paciente.telefone());
        } catch (DuplicateKeyException e) {
            throw new ConflitoException("Ja existe um paciente com esse CPF.");
        }
    }
}
