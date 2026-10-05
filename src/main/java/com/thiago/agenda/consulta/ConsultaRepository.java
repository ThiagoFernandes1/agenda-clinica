package com.thiago.agenda.consulta;

import com.thiago.agenda.comum.RegraDoBancoException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Leituras em SQL direto; escritas sempre pelas procedures, que guardam as regras. */
@Repository
public class ConsultaRepository {

    private static final String SELECT_DETALHE = """
            SELECT c.id, c.medico_id, m.nome AS medico, c.paciente_id, p.nome AS paciente,
                   c.inicio, c.fim, c.status, c.motivo_cancelamento
            FROM   dbo.consulta c
            JOIN   dbo.medico   m ON m.id = c.medico_id
            JOIN   dbo.paciente p ON p.id = c.paciente_id
            """;

    private final JdbcClient jdbc;

    public ConsultaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long agendar(int medicoId, int pacienteId, LocalDateTime inicio, LocalDateTime agora) {
        return traduzindoErros(() -> jdbc.sql("EXEC dbo.sp_agendar_consulta :medico, :paciente, :inicio, :agora")
                .param("medico", medicoId)
                .param("paciente", pacienteId)
                .param("inicio", inicio)
                .param("agora", agora)
                .query(Long.class)
                .single());
    }

    public void cancelar(long id, String motivo, LocalDateTime agora) {
        traduzindoErros(() -> jdbc.sql("EXEC dbo.sp_cancelar_consulta :id, :motivo, :agora")
                .param("id", id)
                .param("motivo", motivo)
                .param("agora", agora)
                .update());
    }

    public void registrarRealizada(long id, LocalDateTime agora) {
        traduzindoErros(() -> jdbc.sql("EXEC dbo.sp_registrar_realizada :id, :agora")
                .param("id", id)
                .param("agora", agora)
                .update());
    }

    public Optional<Consulta> buscar(long id) {
        return jdbc.sql(SELECT_DETALHE + " WHERE c.id = :id")
                .param("id", id)
                .query(Consulta.class)
                .optional();
    }

    public List<Consulta> doPaciente(int pacienteId) {
        return jdbc.sql(SELECT_DETALHE + " WHERE c.paciente_id = :id ORDER BY c.inicio DESC")
                .param("id", pacienteId)
                .query(Consulta.class)
                .list();
    }

    private static <T> T traduzindoErros(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (DataAccessException e) {
            throw RegraDoBancoException.de(e).orElseThrow(() -> e);
        }
    }
}
