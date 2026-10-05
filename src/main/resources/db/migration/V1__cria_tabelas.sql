/* =====================================================================
   Agenda de clinica: medicos, pacientes, horarios de atendimento e consultas
   ===================================================================== */

CREATE TABLE dbo.medico (
    id                    INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_medico PRIMARY KEY,
    nome                  NVARCHAR(150) NOT NULL,
    crm                   NVARCHAR(20)  NOT NULL CONSTRAINT UQ_medico_crm UNIQUE,
    especialidade         NVARCHAR(80)  NOT NULL,
    duracao_consulta_min  INT           NOT NULL CONSTRAINT DF_medico_duracao DEFAULT 30,

    CONSTRAINT CK_medico_duracao CHECK (duracao_consulta_min BETWEEN 10 AND 240)
);

CREATE TABLE dbo.paciente (
    id         INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_paciente PRIMARY KEY,
    nome       NVARCHAR(150) NOT NULL,
    cpf        CHAR(11)      NOT NULL CONSTRAINT UQ_paciente_cpf UNIQUE,
    email      NVARCHAR(200) NULL,
    telefone   NVARCHAR(20)  NULL,

    -- o digito verificador e conferido na API; aqui garantimos so o formato
    CONSTRAINT CK_paciente_cpf CHECK (LEN(cpf) = 11 AND cpf NOT LIKE '%[^0-9]%')
);

/* Janelas semanais em que cada medico atende.
   dia_semana segue a ISO 8601: 1 = segunda ... 7 = domingo. */
CREATE TABLE dbo.horario_atendimento (
    id           INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_horario_atendimento PRIMARY KEY,
    medico_id    INT     NOT NULL CONSTRAINT FK_horario_medico REFERENCES dbo.medico(id) ON DELETE CASCADE,
    dia_semana   TINYINT NOT NULL,
    hora_inicio  TIME(0) NOT NULL,
    hora_fim     TIME(0) NOT NULL,

    CONSTRAINT CK_horario_dia     CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT CK_horario_periodo CHECK (hora_fim > hora_inicio),
    CONSTRAINT UQ_horario         UNIQUE (medico_id, dia_semana, hora_inicio)
);

CREATE TABLE dbo.consulta (
    id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_consulta PRIMARY KEY,
    medico_id            INT           NOT NULL CONSTRAINT FK_consulta_medico   REFERENCES dbo.medico(id),
    paciente_id          INT           NOT NULL CONSTRAINT FK_consulta_paciente REFERENCES dbo.paciente(id),
    inicio               DATETIME2(0)  NOT NULL,
    fim                  DATETIME2(0)  NOT NULL,
    status               VARCHAR(10)   NOT NULL CONSTRAINT DF_consulta_status DEFAULT 'AGENDADA',
    motivo_cancelamento  NVARCHAR(200) NULL,
    criado_em            DATETIME2(0)  NOT NULL CONSTRAINT DF_consulta_criado_em DEFAULT SYSDATETIME(),

    CONSTRAINT CK_consulta_periodo CHECK (fim > inicio),
    CONSTRAINT CK_consulta_status  CHECK (status IN ('AGENDADA', 'CANCELADA', 'REALIZADA')),
    CONSTRAINT CK_consulta_motivo  CHECK (status <> 'CANCELADA' OR motivo_cancelamento IS NOT NULL)
);

/* Indices filtrados: a checagem de conflito so olha consultas AGENDADAS,
   entao as canceladas e realizadas nem entram no indice. */
CREATE INDEX IX_consulta_medico_agendada
    ON dbo.consulta (medico_id, inicio) INCLUDE (fim) WHERE status = 'AGENDADA';

CREATE INDEX IX_consulta_paciente_agendada
    ON dbo.consulta (paciente_id, inicio) INCLUDE (fim) WHERE status = 'AGENDADA';

CREATE INDEX IX_consulta_paciente_historico
    ON dbo.consulta (paciente_id, inicio DESC);
