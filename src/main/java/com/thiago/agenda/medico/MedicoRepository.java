package com.thiago.agenda.medico;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MedicoRepository {

    private final JdbcClient jdbc;

    public MedicoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Medico> listar(String especialidade) {
        return jdbc.sql("""
                        SELECT id, nome, crm, especialidade, duracao_consulta_min
                        FROM   dbo.medico
                        WHERE  :especialidade IS NULL OR especialidade = :especialidade
                        ORDER BY nome
                        """)
                .param("especialidade", especialidade)
                .query(Medico.class)
                .list();
    }

    public Optional<Medico> buscar(int id) {
        return jdbc.sql("""
                        SELECT id, nome, crm, especialidade, duracao_consulta_min
                        FROM   dbo.medico
                        WHERE  id = :id
                        """)
                .param("id", id)
                .query(Medico.class)
                .optional();
    }

    public int inserir(Medico m) {
        KeyHolder chave = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO dbo.medico (nome, crm, especialidade, duracao_consulta_min)
                        VALUES (:nome, :crm, :especialidade, :duracao)
                        """)
                .param("nome", m.nome())
                .param("crm", m.crm())
                .param("especialidade", m.especialidade())
                .param("duracao", m.duracaoConsultaMin())
                .update(chave, "id");
        return chave.getKeyAs(Number.class).intValue();
    }

    public List<HorarioAtendimento> horarios(int medicoId) {
        return jdbc.sql("""
                        SELECT id, medico_id, dia_semana, hora_inicio, hora_fim
                        FROM   dbo.horario_atendimento
                        WHERE  medico_id = :medicoId
                        ORDER BY dia_semana, hora_inicio
                        """)
                .param("medicoId", medicoId)
                .query(HorarioAtendimento.class)
                .list();
    }

    /**
     * Insere a janela so se ela nao encostar em outra do mesmo dia. A checagem e
     * a insercao sao um unico comando, e UPDLOCK + HOLDLOCK travam o intervalo
     * lido, entao duas janelas sobrepostas cadastradas ao mesmo tempo nao passam.
     *
     * @return o id gerado, ou vazio se havia sobreposicao
     */
    public Optional<Integer> inserirHorarioSemSobreposicao(HorarioAtendimento h) {
        // o driver envia LocalTime como datetime (sendTimeAsDatetime), dai os CASTs
        return jdbc.sql("""
                        DECLARE @inicio TIME(0) = CAST(:inicio AS TIME(0)),
                                @fim    TIME(0) = CAST(:fim    AS TIME(0));

                        INSERT INTO dbo.horario_atendimento (medico_id, dia_semana, hora_inicio, hora_fim)
                        OUTPUT inserted.id
                        SELECT :medicoId, :dia, @inicio, @fim
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM   dbo.horario_atendimento WITH (UPDLOCK, HOLDLOCK)
                            WHERE  medico_id   = :medicoId
                              AND  dia_semana  = :dia
                              AND  hora_inicio < @fim
                              AND  hora_fim    > @inicio);
                        """)
                .param("medicoId", h.medicoId())
                .param("dia", h.diaSemana())
                .param("inicio", h.horaInicio())
                .param("fim", h.horaFim())
                .query(Integer.class)
                .optional();
    }

    public boolean removerHorario(int medicoId, int horarioId) {
        return jdbc.sql("DELETE FROM dbo.horario_atendimento WHERE id = :id AND medico_id = :medicoId")
                .param("id", horarioId)
                .param("medicoId", medicoId)
                .update() > 0;
    }
}
