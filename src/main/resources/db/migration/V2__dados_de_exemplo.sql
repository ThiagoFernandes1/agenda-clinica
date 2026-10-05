/* Medicos, horarios e pacientes de exemplo para testar a API logo apos subir.
   Nomes, CRMs e CPFs sao ficticios (os CPFs so tem digitos verificadores validos). */

INSERT INTO dbo.medico (nome, crm, especialidade, duracao_consulta_min) VALUES
    (N'Dra. Helena Prado',   N'CRM-SP 100001', N'Clínica geral', 30),
    (N'Dr. Rafael Moura',    N'CRM-SP 100002', N'Cardiologia',   45),
    (N'Dra. Júlia Andrade',  N'CRM-SP 100003', N'Dermatologia',  20);

-- Helena: segunda a sexta, 08h-12h e 14h-18h
INSERT INTO dbo.horario_atendimento (medico_id, dia_semana, hora_inicio, hora_fim)
SELECT 1, d.dia, p.inicio, p.fim
FROM   (VALUES (1), (2), (3), (4), (5)) AS d(dia)
CROSS JOIN (VALUES ('08:00', '12:00'), ('14:00', '18:00')) AS p(inicio, fim);

-- Rafael: terca e quinta, 13h-19h
INSERT INTO dbo.horario_atendimento (medico_id, dia_semana, hora_inicio, hora_fim) VALUES
    (2, 2, '13:00', '19:00'),
    (2, 4, '13:00', '19:00');

-- Julia: segunda, quarta e sabado, 09h-13h
INSERT INTO dbo.horario_atendimento (medico_id, dia_semana, hora_inicio, hora_fim) VALUES
    (3, 1, '09:00', '13:00'),
    (3, 3, '09:00', '13:00'),
    (3, 6, '09:00', '13:00');

INSERT INTO dbo.paciente (nome, cpf, email, telefone) VALUES
    (N'Marcos Teixeira', '12345678909', N'marcos@exemplo.com',  N'(11) 90000-0001'),
    (N'Paula Ribeiro',   '98765432100', N'paula@exemplo.com',   N'(11) 90000-0002'),
    (N'Lucas Ferreira',  '11144477735', N'lucas@exemplo.com',   N'(11) 90000-0003');
