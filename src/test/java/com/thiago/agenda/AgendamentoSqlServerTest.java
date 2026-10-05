package com.thiago.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thiago.agenda.comum.RegraDoBancoException;
import com.thiago.agenda.consulta.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita as procedures e a funcao de horarios livres em um SQL Server de verdade.
 *
 * So roda quando DB_URL esta definida, como no job de CI que sobe o SQL Server.
 * Cada teste cria o proprio medico e os proprios pacientes, entao pode rodar
 * varias vezes no mesmo banco sem limpar nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AgendamentoSqlServerTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    // segunda-feira, longe o bastante para nao colidir com dados de exemplo
    private static final LocalDate SEGUNDA = LocalDate.of(2030, 1, 7);

    @TestConfiguration
    static class RelogioDeTeste {
        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel();
        }
    }

    /** Relogio que o teste pode adiantar, para testar "passado" e "ja comecou". */
    static class RelogioAjustavel extends Clock {
        private volatile Instant agora;

        void ajustar(LocalDateTime quando) {
            agora = quando.atZone(FUSO).toInstant();
        }

        @Override public ZoneId getZone() { return FUSO; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RelogioAjustavel relogio;
    @Autowired ConsultaRepository consultas;

    int medico;

    @BeforeEach
    void medicoComExpedienteNaSegunda() throws Exception {
        relogio.ajustar(SEGUNDA.minusDays(3).atTime(10, 0));

        medico = criar("/api/medicos", """
                {"nome":"Dr. Teste","crm":"CRM-T %s","especialidade":"Teste","duracaoConsultaMin":30}
                """.formatted(ThreadLocalRandom.current().nextInt(1_000_000_000)));
        criar("/api/medicos/" + medico + "/horarios", """
                {"diaSemana":1,"horaInicio":"08:00","horaFim":"10:00"}""");
    }

    @Test
    void agendaERecusaConflitosForaDoExpedienteEPassado() throws Exception {
        int ana = novoPaciente();
        int bruno = novoPaciente();

        agendar(medico, ana, "08:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.fim").value(SEGUNDA + "T08:30:00"))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        // medico ocupado de 08:00 a 08:30
        agendar(medico, bruno, "08:15").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O medico ja tem uma consulta nesse horario."));

        // a Ana nao pode estar em duas consultas ao mesmo tempo, mesmo com outro medico
        int outroMedico = criar("/api/medicos", """
                {"nome":"Dra. Outra","crm":"CRM-T %s","especialidade":"Teste","duracaoConsultaMin":30}
                """.formatted(ThreadLocalRandom.current().nextInt(1_000_000_000)));
        criar("/api/medicos/" + outroMedico + "/horarios", """
                {"diaSemana":1,"horaInicio":"08:00","horaFim":"12:00"}""");
        agendar(outroMedico, ana, "08:10").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O paciente ja tem uma consulta nesse horario."));

        // 09:45 + 30 min passa das 10:00
        agendar(medico, bruno, "09:45").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Horario fora do expediente do medico."));

        // termina exatamente no fim do expediente: pode
        agendar(medico, bruno, "09:30").andExpect(status().isCreated());

        relogio.ajustar(SEGUNDA.atTime(9, 0));
        agendar(medico, bruno, "08:30").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Nao da para agendar uma consulta no passado."));
    }

    @Test
    void cancelarLiberaOHorarioNaAgenda() throws Exception {
        int ana = novoPaciente();
        long consulta = id(agendar(medico, ana, "08:30").andExpect(status().isCreated()));

        assertThat(livres()).containsExactly("08:00", "09:00", "09:30");

        cancelar(consulta).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Imprevisto"));
        assertThat(livres()).containsExactly("08:00", "08:30", "09:00", "09:30");

        cancelar(consulta).andExpect(status().isUnprocessableEntity());

        // o horario liberado pode ser marcado de novo
        agendar(medico, novoPaciente(), "08:30").andExpect(status().isCreated());
    }

    @Test
    void soMarcaRealizadaDepoisQueAConsultaComeca() throws Exception {
        long consulta = id(agendar(medico, novoPaciente(), "09:00").andExpect(status().isCreated()));

        mvc.perform(post("/api/consultas/{id}/realizada", consulta))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A consulta ainda nao comecou."));

        relogio.ajustar(SEGUNDA.atTime(9, 10));
        cancelar(consulta).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A consulta ja comecou; nao da mais para cancelar."));
        mvc.perform(post("/api/consultas/{id}/realizada", consulta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REALIZADA"));
    }

    @Test
    void agendamentosSimultaneosNoMesmoHorarioSoUmPassa() throws Exception {
        int tentativas = 8;
        List<Integer> pacientes = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            pacientes.add(novoPaciente());
        }

        LocalDateTime inicio = SEGUNDA.atTime(8, 0);
        LocalDateTime agora = LocalDateTime.now(relogio);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        try {
            List<Future<HttpStatus>> resultados = new ArrayList<>();
            for (int paciente : pacientes) {
                Callable<HttpStatus> tentativa = () -> {
                    largada.await();
                    try {
                        consultas.agendar(medico, paciente, inicio, agora);
                        return HttpStatus.CREATED;
                    } catch (RegraDoBancoException e) {
                        return e.getStatus();
                    }
                };
                resultados.add(pool.submit(tentativa));
            }
            largada.countDown();

            List<HttpStatus> status = new ArrayList<>();
            for (Future<HttpStatus> r : resultados) {
                status.add(r.get());
            }
            assertThat(status).filteredOn(s -> s == HttpStatus.CREATED).hasSize(1);
            assertThat(status).filteredOn(s -> s == HttpStatus.CONFLICT).hasSize(tentativas - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    /* ------------------------------------------------------------------ */

    private ResultActions agendar(int medicoId, int pacienteId, String hora) throws Exception {
        return mvc.perform(post("/api/consultas").contentType(MediaType.APPLICATION_JSON).content("""
                {"medicoId":%d,"pacienteId":%d,"inicio":"%sT%s:00"}""".formatted(medicoId, pacienteId, SEGUNDA, hora)));
    }

    private ResultActions cancelar(long consulta) throws Exception {
        return mvc.perform(post("/api/consultas/{id}/cancelamento", consulta)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Imprevisto\"}"));
    }

    private List<String> livres() throws Exception {
        String corpo = mvc.perform(get("/api/medicos/{id}/agenda", medico).param("data", SEGUNDA.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> horas = new ArrayList<>();
        for (JsonNode h : json.readTree(corpo).get("livres")) {
            horas.add(h.get("inicio").asText().substring(11, 16));
        }
        return horas;
    }

    private int novoPaciente() throws Exception {
        return criar("/api/pacientes", """
                {"nome":"Paciente Teste","cpf":"%s"}""".formatted(cpfAleatorio()));
    }

    private int criar(String url, String corpo) throws Exception {
        return (int) id(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()));
    }

    private long id(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static String cpfAleatorio() {
        StringBuilder cpf = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            cpf.append(ThreadLocalRandom.current().nextInt(10));
        }
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
            }
            int resto = soma % 11;
            cpf.append(resto < 2 ? 0 : 11 - resto);
        }
        return cpf.toString();
    }
}
