package com.thiago.agenda.paciente;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PacienteRepository {

    private final JdbcClient jdbc;

    public PacienteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Paciente> buscar(String nome) {
        return jdbc.sql("""
                        SELECT TOP (100) id, nome, cpf, email, telefone
                        FROM   dbo.paciente
                        WHERE  :nome IS NULL OR nome LIKE '%' + :nome + '%'
                        ORDER BY nome
                        """)
                .param("nome", nome)
                .query(Paciente.class)
                .list();
    }

    public Optional<Paciente> buscarPorId(int id) {
        return jdbc.sql("SELECT id, nome, cpf, email, telefone FROM dbo.paciente WHERE id = :id")
                .param("id", id)
                .query(Paciente.class)
                .optional();
    }

    public int inserir(Paciente p) {
        KeyHolder chave = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO dbo.paciente (nome, cpf, email, telefone)
                        VALUES (:nome, :cpf, :email, :telefone)
                        """)
                .param("nome", p.nome())
                .param("cpf", p.cpf())
                .param("email", p.email())
                .param("telefone", p.telefone())
                .update(chave, "id");
        return chave.getKeyAs(Number.class).intValue();
    }
}
