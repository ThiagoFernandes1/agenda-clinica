package com.thiago.agenda.medico;

import com.thiago.agenda.comum.ConflitoException;
import com.thiago.agenda.comum.NaoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoRepository medicos;

    public MedicoController(MedicoRepository medicos) {
        this.medicos = medicos;
    }

    public record NovoMedico(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(max = 20) String crm,
            @NotBlank @Size(max = 80) String especialidade,
            @Min(10) @Max(240) int duracaoConsultaMin) {
    }

    public record NovoHorario(
            @Min(1) @Max(7) int diaSemana,
            @NotNull LocalTime horaInicio,
            @NotNull LocalTime horaFim) {

        @AssertTrue(message = "horaFim deve ser depois de horaInicio")
        public boolean isPeriodoValido() {
            return horaInicio == null || horaFim == null || horaFim.isAfter(horaInicio);
        }
    }

    @GetMapping
    public List<Medico> listar(@RequestParam(required = false) String especialidade) {
        return medicos.listar(especialidade);
    }

    @GetMapping("/{id}")
    public Medico buscar(@PathVariable int id) {
        return medicos.buscar(id).orElseThrow(() -> new NaoEncontradoException("Medico", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Medico cadastrar(@Valid @RequestBody NovoMedico dados) {
        Medico medico = new Medico(null, dados.nome().trim(), dados.crm().trim(),
                dados.especialidade().trim(), dados.duracaoConsultaMin());
        try {
            int id = medicos.inserir(medico);
            return new Medico(id, medico.nome(), medico.crm(), medico.especialidade(), medico.duracaoConsultaMin());
        } catch (DuplicateKeyException e) {
            throw new ConflitoException("Ja existe um medico com o CRM " + medico.crm() + ".");
        }
    }

    @GetMapping("/{id}/horarios")
    public List<HorarioAtendimento> horarios(@PathVariable int id) {
        buscar(id);
        return medicos.horarios(id);
    }

    @PostMapping("/{id}/horarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public HorarioAtendimento adicionarHorario(@PathVariable int id, @Valid @RequestBody NovoHorario dados) {
        buscar(id);
        HorarioAtendimento horario = new HorarioAtendimento(null, id, dados.diaSemana(), dados.horaInicio(), dados.horaFim());
        int novoId = medicos.inserirHorarioSemSobreposicao(horario)
                .orElseThrow(() -> new ConflitoException("A janela se sobrepoe a outro horario do medico nesse dia."));
        return new HorarioAtendimento(novoId, id, horario.diaSemana(), horario.horaInicio(), horario.horaFim());
    }

    @DeleteMapping("/{id}/horarios/{horarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerHorario(@PathVariable int id, @PathVariable int horarioId) {
        if (!medicos.removerHorario(id, horarioId)) {
            throw new NaoEncontradoException("Horario", horarioId);
        }
    }
}
