/* =====================================================================
   Horarios livres de um medico em um dia.

   Funcao de tabela inline: o otimizador expande o corpo dentro da consulta
   que a chama, como se fosse uma view com parametros.

   Os horarios sao fatiados a partir do inicio de cada janela de atendimento,
   no tamanho da consulta do medico, e saem os que ja passaram ou que encostam
   em uma consulta agendada.
   ===================================================================== */

CREATE OR ALTER FUNCTION dbo.fn_horarios_livres
(
    @medico_id  INT,
    @data       DATE,
    @agora      DATETIME2(0)
)
RETURNS TABLE
AS
RETURN
    WITH numeros AS (
        -- 0..287: com consultas de no minimo 10 minutos, cabe um dia inteiro
        SELECT TOP (288) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS n
        FROM   sys.all_objects
    ),
    fatias AS (
        SELECT DATEADD(MINUTE,
                       DATEDIFF(MINUTE, CAST('00:00' AS TIME(0)), h.hora_inicio) + n.n * m.duracao_consulta_min,
                       CAST(@data AS DATETIME2(0)))                                   AS inicio,
               m.duracao_consulta_min                                                 AS duracao
        FROM   dbo.medico m
        JOIN   dbo.horario_atendimento h
               ON  h.medico_id  = m.id
               AND h.dia_semana = dbo.fn_dia_semana_iso(@data)
        JOIN   numeros n
               ON  DATEDIFF(MINUTE, CAST('00:00' AS TIME(0)), h.hora_inicio) + (n.n + 1) * m.duracao_consulta_min
                   <= DATEDIFF(MINUTE, CAST('00:00' AS TIME(0)), h.hora_fim)
        WHERE  m.id = @medico_id
    )
    SELECT f.inicio,
           DATEADD(MINUTE, f.duracao, f.inicio) AS fim
    FROM   fatias f
    WHERE  f.inicio > @agora
      AND  NOT EXISTS (
               SELECT 1
               FROM   dbo.consulta c
               WHERE  c.medico_id = @medico_id
                 AND  c.status    = 'AGENDADA'
                 AND  c.inicio    < DATEADD(MINUTE, f.duracao, f.inicio)
                 AND  c.fim       > f.inicio);
GO
