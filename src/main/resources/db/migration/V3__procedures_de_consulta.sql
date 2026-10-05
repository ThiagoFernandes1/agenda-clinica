/* =====================================================================
   Procedures de consulta. Toda a regra de agendamento mora aqui, para que
   qualquer cliente do banco (a API, um job, um relatorio) siga a mesma regra.

   Erros sao lancados com THROW e numeros que a API traduz em status HTTP:
     50404 -> 404 (nao encontrado)
     50409 -> 409 (conflito de horario)
     50422 -> 422 (regra de negocio)

   @agora e opcional: em producao vale SYSDATETIME(); os testes passam uma
   data fixa para controlar o que e "passado".
   ===================================================================== */

/* Dia da semana ISO (1 = segunda ... 7 = domingo) sem depender de SET DATEFIRST:
   1900-01-01 foi uma segunda-feira. */
CREATE OR ALTER FUNCTION dbo.fn_dia_semana_iso (@data DATE)
RETURNS TINYINT
WITH SCHEMABINDING
AS
BEGIN
    RETURN DATEDIFF(DAY, CAST('19000101' AS DATE), @data) % 7 + 1;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_agendar_consulta
    @medico_id    INT,
    @paciente_id  INT,
    @inicio       DATETIME2(0),
    @agora        DATETIME2(0) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;   -- qualquer erro desfaz a transacao inteira

    SET @agora = COALESCE(@agora, SYSDATETIME());

    DECLARE @duracao INT = (SELECT duracao_consulta_min FROM dbo.medico WHERE id = @medico_id);

    IF @duracao IS NULL
        THROW 50404, N'Medico nao encontrado.', 1;

    IF NOT EXISTS (SELECT 1 FROM dbo.paciente WHERE id = @paciente_id)
        THROW 50404, N'Paciente nao encontrado.', 2;

    IF @inicio <= @agora
        THROW 50422, N'Nao da para agendar uma consulta no passado.', 1;

    DECLARE @fim DATETIME2(0) = DATEADD(MINUTE, @duracao, @inicio);

    -- a consulta inteira precisa caber numa janela de atendimento do medico
    IF CAST(@fim AS DATE) <> CAST(@inicio AS DATE)
       OR NOT EXISTS (
            SELECT 1
            FROM   dbo.horario_atendimento
            WHERE  medico_id   = @medico_id
              AND  dia_semana  = dbo.fn_dia_semana_iso(CAST(@inicio AS DATE))
              AND  hora_inicio <= CAST(@inicio AS TIME(0))
              AND  hora_fim    >= CAST(@fim    AS TIME(0)))
        THROW 50422, N'Horario fora do expediente do medico.', 2;

    BEGIN TRANSACTION;

        /* UPDLOCK + HOLDLOCK travam o intervalo lido ate o COMMIT: uma segunda
           chamada concorrente para o mesmo horario espera aqui e, quando segue,
           ja enxerga a consulta inserida pela primeira. */
        IF EXISTS (
            SELECT 1
            FROM   dbo.consulta WITH (UPDLOCK, HOLDLOCK)
            WHERE  medico_id = @medico_id
              AND  status    = 'AGENDADA'
              AND  inicio    < @fim
              AND  fim       > @inicio)
            THROW 50409, N'O medico ja tem uma consulta nesse horario.', 1;

        IF EXISTS (
            SELECT 1
            FROM   dbo.consulta WITH (UPDLOCK, HOLDLOCK)
            WHERE  paciente_id = @paciente_id
              AND  status      = 'AGENDADA'
              AND  inicio      < @fim
              AND  fim         > @inicio)
            THROW 50409, N'O paciente ja tem uma consulta nesse horario.', 2;

        INSERT INTO dbo.consulta (medico_id, paciente_id, inicio, fim)
        VALUES (@medico_id, @paciente_id, @inicio, @fim);

        DECLARE @id BIGINT = SCOPE_IDENTITY();

    COMMIT TRANSACTION;

    SELECT @id AS id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_cancelar_consulta
    @consulta_id  BIGINT,
    @motivo       NVARCHAR(200),
    @agora        DATETIME2(0) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @agora = COALESCE(@agora, SYSDATETIME());

    -- a condicao de status no proprio UPDATE evita cancelar duas vezes em paralelo
    UPDATE dbo.consulta
    SET    status = 'CANCELADA',
           motivo_cancelamento = @motivo
    WHERE  id = @consulta_id
      AND  status = 'AGENDADA'
      AND  inicio > @agora;

    IF @@ROWCOUNT = 1
        RETURN;

    DECLARE @status VARCHAR(10), @inicio DATETIME2(0);
    SELECT @status = status, @inicio = inicio FROM dbo.consulta WHERE id = @consulta_id;

    IF @status IS NULL
        THROW 50404, N'Consulta nao encontrada.', 1;

    IF @status <> 'AGENDADA'
        THROW 50422, N'So consultas agendadas podem ser canceladas.', 1;

    THROW 50422, N'A consulta ja comecou; nao da mais para cancelar.', 2;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_registrar_realizada
    @consulta_id  BIGINT,
    @agora        DATETIME2(0) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @agora = COALESCE(@agora, SYSDATETIME());

    UPDATE dbo.consulta
    SET    status = 'REALIZADA'
    WHERE  id = @consulta_id
      AND  status = 'AGENDADA'
      AND  inicio <= @agora;

    IF @@ROWCOUNT = 1
        RETURN;

    DECLARE @status VARCHAR(10);
    SELECT @status = status FROM dbo.consulta WHERE id = @consulta_id;

    IF @status IS NULL
        THROW 50404, N'Consulta nao encontrada.', 1;

    IF @status <> 'AGENDADA'
        THROW 50422, N'So consultas agendadas podem ser marcadas como realizadas.', 1;

    THROW 50422, N'A consulta ainda nao comecou.', 2;
END;
GO
