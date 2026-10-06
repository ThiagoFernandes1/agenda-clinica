package com.thiago.agenda.comum;

import com.thiago.agenda.consulta.AgendaController;
import com.thiago.agenda.consulta.ConsultaController;
import com.thiago.agenda.consulta.ConsultaRepository;
import com.thiago.agenda.medico.MedicoController;
import com.thiago.agenda.medico.MedicoRepository;
import com.thiago.agenda.paciente.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Erros do proprio Spring MVC saem no mesmo formato problem+json que os da aplicacao. */
@WebMvcTest({MedicoController.class, AgendaController.class, ConsultaController.class})
class TratamentoDeErrosTest {

    @Autowired MockMvc mvc;

    @MockitoBean MedicoRepository medicos;
    @MockitoBean ConsultaRepository consultas;
    @MockitoBean PacienteRepository pacientes;
    @MockitoBean JdbcClient jdbc;
    @MockitoBean Clock clock;

    @Test
    void idQueNaoENumero() throws Exception {
        mvc.perform(get("/api/medicos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("'id' recebeu \"abc\", mas espera um numero inteiro."));
    }

    @Test
    void dataEmFormatoErrado() throws Exception {
        mvc.perform(get("/api/medicos/1/agenda").param("data", "amanha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'data' recebeu \"amanha\", mas espera uma data no formato AAAA-MM-DD."));
    }

    @Test
    void parametroObrigatorioFaltando() throws Exception {
        mvc.perform(get("/api/medicos/1/agenda"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void jsonQuebrado() throws Exception {
        mvc.perform(post("/api/consultas").contentType(MediaType.APPLICATION_JSON).content("{\"medicoId\":1,"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void metodoNaoSuportado() throws Exception {
        mvc.perform(delete("/api/consultas/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void validacaoContinuaListandoOsCampos() throws Exception {
        mvc.perform(post("/api/consultas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.medicoId").exists())
                .andExpect(jsonPath("$.campos.inicio").exists());
    }
}
