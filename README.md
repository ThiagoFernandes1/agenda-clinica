# 🩺 Agenda Clínica — Java + SQL Server

API REST de agendamento de consultas em que **a regra de negócio mora no banco**: o agendamento, o cancelamento e o cálculo de horários livres são stored procedures e funções do SQL Server, chamadas pelo Java com Spring JDBC (sem ORM). Qualquer cliente do banco segue a mesma regra, não só a API.

![CI](https://github.com/ThiagoFernandes1/agenda-clinica/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![SQL Server](https://img.shields.io/badge/SQL%20Server-2022-CC2927?style=flat-square&logo=microsoftsqlserver&logoColor=white)
![T-SQL](https://img.shields.io/badge/T--SQL-procedures%20%26%20fun%C3%A7%C3%B5es-555?style=flat-square)

---

## O que a agenda garante

- a consulta precisa caber **inteira** numa janela de atendimento do médico (cada médico tem a sua duração de consulta);
- um médico não tem duas consultas ao mesmo tempo, e **um paciente também não**, mesmo com médicos diferentes;
- não dá para agendar no passado, cancelar uma consulta que já começou ou marcar como realizada uma que ainda não começou;
- cancelar libera o horário na hora;
- **dois agendamentos simultâneos para o mesmo horário: só um passa.** Há um teste que dispara 8 ao mesmo tempo contra um SQL Server real e confere isso.

---

## Como a regra fica no banco

| Objeto | Tipo | O que faz |
|---|---|---|
| `sp_agendar_consulta` | procedure | Valida médico, paciente, expediente e data; checa conflito do médico e do paciente e insere, tudo numa transação |
| `sp_cancelar_consulta` | procedure | Cancela só se ainda estiver agendada e não tiver começado |
| `sp_registrar_realizada` | procedure | Marca como realizada só depois do início |
| `fn_horarios_livres` | função de tabela inline | Fatia as janelas de atendimento do dia no tamanho da consulta e tira as ocupadas e as que já passaram |
| `fn_dia_semana_iso` | função escalar | Dia da semana ISO (1 = segunda) sem depender de `SET DATEFIRST` |

### Concorrência

A checagem de conflito lê as consultas com `WITH (UPDLOCK, HOLDLOCK)`. Isso trava o **intervalo** lido até o `COMMIT`, não só as linhas que existem: uma segunda chamada para o mesmo horário espera a primeira terminar e, quando segue, já enxerga a consulta inserida. Sem o `HOLDLOCK`, as duas veriam o horário vazio e as duas inseririam.

O mesmo padrão (`INSERT ... WHERE NOT EXISTS` sob `UPDLOCK, HOLDLOCK`) impede cadastrar janelas de atendimento sobrepostas.

### Erros do banco viram status HTTP

As procedures lançam `THROW` com números que carregam o status:

```sql
THROW 50409, N'O medico ja tem uma consulta nesse horario.', 1;
```

A API pega o número do erro, tira os 50000 e devolve `409` com a mensagem do banco, no formato [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "title": "Conflito de horario",
  "status": 409,
  "detail": "O medico ja tem uma consulta nesse horario.",
  "instance": "/api/consultas"
}
```

| Número | Status | Quando |
|---|---|---|
| `50404` | 404 | Médico, paciente ou consulta inexistente |
| `50409` | 409 | Conflito de horário |
| `50422` | 422 | Fora do expediente, no passado, já cancelada… |

Erros do próprio SQL Server (deadlock, chave duplicada) não entram nessa conversão.

### Outros detalhes do schema

- **Índices filtrados** (`WHERE status = 'AGENDADA'`): a checagem de conflito só olha consultas agendadas, então as canceladas e realizadas nem entram no índice.
- **Restrições `CHECK`**: formato do CPF, período da consulta, status válido, motivo obrigatório no cancelamento.
- **"Agora" vem da API**: as procedures recebem `@agora` (padrão `SYSDATETIME()`), o que deixa os testes controlarem o relógio.

---

## Endpoints

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/api/medicos?especialidade=` | Lista os médicos |
| `POST` | `/api/medicos` | Cadastra um médico |
| `GET` | `/api/medicos/{id}/horarios` | Janelas semanais de atendimento |
| `POST` | `/api/medicos/{id}/horarios` | Adiciona uma janela (`diaSemana` 1 = segunda … 7 = domingo) |
| `DELETE` | `/api/medicos/{id}/horarios/{horarioId}` | Remove uma janela |
| `GET` | `/api/medicos/{id}/agenda?data=2026-10-06` | Horários livres e consultas do dia |
| `GET` | `/api/pacientes?nome=` | Busca pacientes |
| `POST` | `/api/pacientes` | Cadastra um paciente (CPF validado pelos dígitos verificadores) |
| `GET` | `/api/pacientes/{id}/consultas` | Histórico do paciente |
| `POST` | `/api/consultas` | Agenda: `{"medicoId": 2, "pacienteId": 1, "inicio": "2026-10-06T13:00:00"}` |
| `GET` | `/api/consultas/{id}` | Detalhe da consulta |
| `POST` | `/api/consultas/{id}/cancelamento` | Cancela: `{"motivo": "..."}` |
| `POST` | `/api/consultas/{id}/realizada` | Marca como realizada |

A documentação interativa fica em `http://localhost:8080/swagger-ui.html`.

---

## Como rodar

Precisa de Java 21, Maven e Docker.

```bash
# sobe o SQL Server 2022 e cria o banco AgendaDB
docker compose up -d

# sobe a API em http://localhost:8080 (o Flyway cria tabelas, procedures, funções e dados de exemplo)
DB_PASSWORD=Agenda@2026 mvn spring-boot:run
```

No PowerShell, troque a segunda linha por:

```powershell
$env:DB_PASSWORD = "Agenda@2026"; mvn spring-boot:run
```

Os dados de exemplo trazem três médicos (clínica geral, cardiologia e dermatologia), com expedientes diferentes, e três pacientes.

---

## Testes

```bash
mvn test
```

- **`CpfTest`** e **`RegraDoBancoExceptionTest`**: unitários, rodam sempre.
- **`AgendamentoSqlServerTest`**: conflitos, expediente, passado, cancelamento, consulta realizada e o teste de concorrência, contra um SQL Server de verdade. Roda só quando `DB_URL` está definida:

```bash
DB_URL="jdbc:sqlserver://localhost:1433;databaseName=AgendaDB;encrypt=true;trustServerCertificate=true" \
DB_PASSWORD=Agenda@2026 mvn test
```

O [CI](.github/workflows/ci.yml) sobe um SQL Server 2022 a cada push e roda todos os testes contra ele.

---

## Estrutura

```
src/main/
├── java/com/thiago/agenda/
│   ├── comum/       # erros, tradução THROW -> HTTP, validação de CPF
│   ├── medico/      # médicos e janelas de atendimento
│   ├── paciente/    # pacientes
│   └── consulta/    # agendamento (procedures) e agenda do dia (função)
└── resources/db/migration/
    ├── V1__cria_tabelas.sql
    ├── V2__dados_de_exemplo.sql
    ├── V3__procedures_de_consulta.sql
    └── V4__funcao_horarios_livres.sql
```
