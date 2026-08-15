# LifeSync API

> Uma API REST de produtividade pessoal que unifica **tarefas**, **hábitos** e **metas** sob uma única camada de autenticação com restrições de segurança reais.

[![CI](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml/badge.svg)](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![JWT](https://img.shields.io/badge/Auth-JWT-000000?logo=jsonwebtokens&logoColor=white)](https://jwt.io/)

---

## Índice

1. [Visão Geral](#visão-geral)
2. [Demonstração](#demonstração)
3. [Arquitetura](#arquitetura)
4. [Tecnologias](#tecnologias)
5. [API](#api)
6. [Guia de Desenvolvimento](#guia-de-desenvolvimento)
7. [Testes](#testes)
8. [Deploy](#deploy)
9. [Contribuição](#contribuição)
10. [Licença](#licença)
11. [Autor](#autor)
12. [Sobre](#sobre)

---

## Visão Geral

A **LifeSync API** é uma aplicação Spring Boot 4.1 que reúne três primitivos de produtividade — **tarefas** (acionáveis, binárias), **hábitos** (recorrentes, com sequência) e **metas** (progresso mensurável em direção a um alvo) — sob um modelo de segurança compartilhado. Cada módulo segue o mesmo padrão de consulta com escopo de propriedade (`id + userId`), utiliza UUIDs para identificadores e aplica proteção IDOR na camada de serviço.

### Principais diferenciais

- **Streaks de hábitos com fuso horário**: o cálculo de reset de sequência considera o fuso horário configurado pelo usuário, não o relógio do servidor.
- **Metas com histórico mensurável**: o progresso é registrado como um valor absoluto (não um delta), e cada atualização gera uma entrada histórica.
- **Autenticação JWT stateless**: tokens de acesso (24h) e refresh (7d) carregam um claim `type` para que um refresh token vazado não seja usado como token de acesso.
- **Testes de integração com Testcontainers**: a suíte completa roda contra uma instância real de PostgreSQL no Docker — localmente e no CI — eliminando discrepâncias de ambiente.

---

## Demonstração

### Swagger UI (documentação interativa)

A API possui documentação OpenAPI 3 integrada via SpringDoc. Após iniciar localmente:

```
http://localhost:8080/swagger-ui/index.html
```

### Health Check

```
http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP"
}
```

### Exemplo de fluxo completo

```bash
# 1. Registrar um usuário
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Endriw Bento",
    "username": "endriw",
    "email": "endriw@example.com",
    "password": "senha123"
  }'

# 2. Criar uma tarefa
ACCESS_TOKEN="<seu-access-token>"
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Pagar contas de luz",
    "description": "Pagar a conta de luz da casa",
    "priority": "HIGH",
    "dueDate": "2025-12-10"
  }'

# 3. Marcar a tarefa como concluída
TASK_ID="<seu-task-id>"
curl -X PATCH "http://localhost:8080/api/tasks/$TASK_ID/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
```

---

## Arquitetura

### Princípios de design

| Princípio | Implementação |
|---|---|
| **Proteção contra IDOR** | Toda consulta de serviço é escopada por `(id, userId)`. Um mismatch retorna `404` — nunca `403`, evitando enumeração de recursos que não pertencem ao usuário. |
| **Identificadores UUID** | Todos os IDs de entidade usam `java.util.UUID`, prevenindo enumeração sequencial e inferência de volume. |
| **Separação de tokens de acesso e refresh** | O claim JWT `type` (`access`/`refresh`) impede que um refresh token vazado autentique em rotas protegidas e vice-versa. |
| **Sessões stateless** | O gerenciamento de sessão é `STATELESS`. CSRF é desativado (JWT bearer, sem cookies). Apenas o filtro `Bearer` roda. |
| **Soft delete (hábitos)** | A remoção de um hábito define `active = false`; o histórico de check-ins é preservado para continuidade do streak. |
| **Pacotes por domínio** | Cada contexto limitado (`auth`, `goal`, `habit`, `task`, `user`) é autocontido: entidade, DTOs, repositório, serviço e controlador. |
| **Migrations versionadas** | Migrações Flyway em `src/main/resources/db/migration/`. O esquema é imutável após `V1__`; novas alterações exigem `V2__`, `V3__`, etc. |
| **Actuator seguro para produção** | Apenas `/actuator/health` é público (para probes de contêiner). `env`, `beans`, `mappings` permanecem atrás de autenticação. |

### Estrutura do projeto

```
com.lifesync.api
├── common/       # BaseEntity, ApiErrorResponse
├── config/       # SecurityConfig, OpenApiConfig
├── exception/    # Exceções de domínio + GlobalExceptionHandler
├── security/     # JWT provider, filtro de autenticação, UserDetails
├── auth/         # Registro, login, refresh
├── user/         # Gestão de perfil (/me)
├── task/         # Tarefas + subtarefas
├── habit/        # Hábitos + histórico de check-ins
├── goal/         # Metas + histórico de progresso
└── LifesyncApiApplication.java
```

### Relacionamento entre entidades

| Entidade | Relacionamento | Comportamento no delete |
|---|---|---|
| **Task** → **User** | `@ManyToOne` (LAZY) | Nenhum cascade — apagar um usuário não afeta tarefas. |
| **Task** → **Goal** | `@ManyToOne` (opcional) | Sem cascade. Deletar uma meta desvincula (`goal = null`), não deleta a tarefa. |
| **Task** → **SubTask** | `@OneToMany` (cascade ALL, orphanRemoval) | Subtasks deletadas em cascata com a tarefa. |
| **Habit** → **Goal** | `@ManyToOne` (opcional) | Sem cascade. Desvincula no delete da meta. |
| **HabitHistory** → **Habit** | `@ManyToOne` | Nenhum cascade. Preservado no soft delete. |
| **GoalProgress** → **Goal** | `@ManyToOne` (cascade ALL) | Deletado em cascata com a meta. |

### Migrações do banco de dados

| Versão | Descrição |
|---|---|
| `V1__create_lifesync_schema.sql` | Esquema base: `users`, `tasks`, `habits`, `habit_history`, `sub_tasks`. |
| `V2__create_goals_and_link_task_habit.sql` | Adiciona tabelas `goals`, `goal_progress`; coluna `goal_id` como FK em `tasks` e `habits`. |

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Runtime | Java 21, Spring Boot 4.1 |
| API | Spring Web, Spring Security, Spring Data JPA |
| Auth | JWT (jjwt 0.13.0), BCrypt |
| Banco de dados | PostgreSQL, Hibernate, Flyway |
| Documentação | SpringDoc OpenAPI 3 (Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers |
| Build | Maven (wrapper) |
| Deploy | Docker (multi-stage), Render, Neon |

---

## API

**URL base:** `https://lifesync-api-ezkx.onrender.com`

| Endpoint | Descrição |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| Health | `/actuator/health` |

**Autenticação:** Todos os endpoints exceto `auth/*` exigem o header `Authorization: Bearer <access_token>`.

### Endpoints

#### Auth

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/auth/register` | Registra um novo usuário — retorna tokens de acesso e refresh |
| `POST` | `/api/auth/login` | Autentica e retorna os tokens |
| `POST` | `/api/auth/refresh` | Troca um refresh token por um novo access token |

#### User

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/api/users/me` | Retorna o perfil do usuário autenticado |
| `PUT` | `/api/users/me` | Atualiza o perfil (timezone, nome de exibição, etc.) |

#### Task

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/tasks` | Cria uma tarefa (opcional `goalId` para vincular a uma meta) |
| `GET` | `/api/tasks/{id}` | Busca uma tarefa pelo ID |
| `GET` | `/api/tasks` | Lista tarefas (filtro por `status`, `priority`; paginado) |
| `PUT` | `/api/tasks/{id}` | Atualiza campos (título, descrição, prioridade, data de vencimento) |
| `DELETE` | `/api/tasks/{id}` | Remove uma tarefa (em cascata para subtarefas) |
| `PATCH` | `/api/tasks/{id}/status` | Atualiza apenas o status |
| `POST` | `/api/tasks/{id}/subtasks` | Adiciona uma subtarefa |
| `PUT` | `/api/tasks/{id}/subtasks/{subtaskId}` | Atualiza o título de uma subtarefa |
| `PATCH` | `/api/tasks/{id}/subtasks/{subtaskId}` | Alterna a conclusão da subtarefa |
| `DELETE` | `/api/tasks/{id}/subtasks/{subtaskId}` | Remove uma subtarefa |

#### Habit

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/habits` | Cria um hábito (com frequência e meta por período) |
| `GET` | `/api/habits/{id}` | Busca um hábito pelo ID |
| `GET` | `/api/habits` | Lista hábitos (paginado) |
| `PUT` | `/api/habits/{id}` | Atualiza campos do hábito |
| `DELETE` | `/api/habits/{id}` | Soft delete (preserva o histórico de check-ins) |
| `POST` | `/api/habits/{id}/checkin` | Registra check-in de hoje (409 se já feito) |
| `GET` | `/api/habits/{id}/history` | Histórico de check-ins (paginado) |

#### Goal

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/goals` | Cria uma meta com valor alvo e unidade |
| `GET` | `/api/goals/{id}` | Busca uma meta pelo ID |
| `GET` | `/api/goals` | Lista metas (filtro por `status`; paginado) |
| `PUT` | `/api/goals/{id}` | Atualiza campos da meta |
| `DELETE` | `/api/goals/{id}` | Remove uma meta (desvincula tasks/habits, deleta histórico de progresso) |
| `POST` | `/api/goals/{id}/progress` | Registra progresso (valor absoluto, transição automática para `COMPLETED`) |
| `GET` | `/api/goals/{id}/progress` | Histórico de progresso |

---

## Guia de desenvolvimento

### Pré-requisitos

- **JDK 21+** (Temurin recomendado)
- **Docker** (para testes de integração com Testcontainers)
- **PostgreSQL 16+** (para desenvolvimento local sem Testcontainers)

### Setup local

```bash
git clone https://github.com/EndriwEngSoft/lifesync-api.git
cd lifesync-api

# Opção A: Com Testcontainers (requer Docker rodando)
./mvnw clean test
./mvnw spring-boot:run

# Opção B: Com PostgreSQL local
createdb lifesync_db
./mvnw spring-boot:run
```

A aplicação inicia em `http://localhost:8080`.

Para desenvolvimento local, o `application.yml` fornece valores de fallback para `DB_PASSWORD` e `JWT_SECRET`, permitindo rodar sem variáveis de ambiente. **Esses fallbacks são para desenvolvimento apenas** — em produção, sempre se lê do ambiente (veja `application-prod.yml`).

### Variáveis de ambiente

| Variável | Padrão (dev) | Produção | Descrição |
|---|---|---|---|
| `DB_PASSWORD` | `123456` | (definida pela plataforma) | Senha do PostgreSQL |
| `JWT_SECRET` | chave de desenvolvimento | (definida pela plataforma) | Chave de assinatura HMAC-SHA256 (mínimo 256 bits) |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | (definida pela plataforma) | Origens CORS permitidas |
| `SPRING_DATASOURCE_URL` | — | (definida pela plataforma) | URL JDBC do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | — | (definida pela plataforma) | Usuário do banco |
| `JWT_EXPIRATION` | `86400000` (24h) | (herdado) | TTL do access token (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 dias) | (herdado) | TTL do refresh token (ms) |

---

## Testes

A suíte de testes distinguir entre **testes unitários** (Mockito, sem banco) e **testes de integração** (Testcontainers, PostgreSQL real no Docker).

### Comandos

```bash
# Suite completa (unitários + integração)
./mvnw clean test

# Apenas testes unitários (mais rápido, sem Docker)
./mvnw clean test -Dtest="*ServiceTest,AuthControllerTest"

# Teste de integração com PostgreSQL real
./mvnw clean test -Dtest="LifesyncApiApplicationTests"
```

### Contagem de testes

**34 testes** no total (unitários + integração), todos passando.

| Arquivo | Tipo | Testes |
|---|---|---|
| `AuthControllerTest` | Unitário (MockMvc) | 3 |
| `TaskServiceTest` | Unitário (Mockito) | 7 |
| `HabitServiceTest` | Unitário (Mockito) | 6 |
| `GoalServiceTest` | Unitário (Mockito) | 5 |
| `UserServiceTest` | Unitário (Mockito) | 4 |
| `JwtTokenProviderTest` | Unitário (puro) | 6 |
| `JwtAuthFilterTest` | Unitário (puro) | 1 |
| `LifesyncApiApplicationTests` | Integração (Testcontainers) | 2 |

O teste de integração (`LifesyncApiApplicationTests`) inicia o contexto Spring completo com uma instância PostgreSQL gerenciada pelo Testcontainers, executa migrações Flyway de `V1` a `V2` e valida o esquema.

---

## Deploy

### Stack de produção

- **Render** (Web Service gratuito) — executa a imagem Docker com `runtime: docker`
- **Neon** (PostgreSQL Serverless gratuito) — banco gerenciado com escala automática

### CI/CD

Todo *push* para `main` (ou *pull request*) dispara o pipeline do GitHub Actions:

```yaml
# .github/workflows/ci.yml
```

O pipeline executa `./mvnw -B clean test` em `ubuntu-latest`, que já inclui Docker por padrão — os Testcontainers funcionam no CI sem configuração adicional.

### Docker

Build multi-estágio:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY --from=build /app/target/lifesync-api.jar app.jar
ENTRYPOINT ["java", "-Xmx400m", "-jar", "/app.jar"]
```

O flag `-Xmx400m` é intencional: o plano gratuito do Render oferece 512MB de RAM, e reservar ~110MB para o SO previne `OutOfMemoryError`.

### Deploy via Render Blueprint

1. **Criar banco de dados no Neon** — crie um projeto e copie os dados de conexão.
2. **Gerar `JWT_SECRET`** (PowerShell):

   ```powershell
   $b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
   ```

3. **Fazer push para o GitHub** — o Render lê `render.yaml` do branch `main` automaticamente.
4. **Criar Blueprint no Render** — selecione seu repositório; o Render provisionará o serviço usando `render.yaml`.
5. **Preencher variáveis de ambiente** (marcadas como `sync: false` no `render.yaml`):

   | Variável | Valor |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | Usuário do Neon |
   | `DB_PASSWORD` | Senha do Neon |
   | `JWT_SECRET` | Gerado no passo 2 |
   | `APP_CORS_ALLOWED_ORIGINS` | URL do seu frontend (ex: `https://seu-app.vercel.app`) |

6. **Validar** na ordem:
   - `https://<seu-servico>.onrender.com/actuator/health` → `{"status":"UP"}`
   - `/swagger-ui/index.html` → renderiza
   - `POST /api/auth/register` → retorna tokens
   - `POST /api/auth/login` → retorna tokens
   - Use o access token via "Authorize" no Swagger, então teste `GET /api/tasks`

> **Cold start:** O plano gratuito do Render entra em hibernação após 15 minutos de inatividade. A primeira requisição após a hibernação leva ~60 segundos. Considere usar o [UptimeRobot](https://uptimerobot.com/) fazendo ping no `/actuator/health` a cada 10 minutos para manter o serviço ativo.

### Erros comuns de deploy

| Sintoma | Causa | Solução |
|---|---|---|
| `502` / app não inicia | `SPRING_DATASOURCE_URL` faltando `?sslmode=require` | Adicione `?sslmode=require` ao final da URL |
| `401` em todos os endpoints | `JWT_SECRET` com menos de 32 caracteres (256 bits) | Gere uma chave de 32+ bytes |
| CORS bloqueando o frontend | `APP_CORS_ALLOWED_ORIGINS` não inclui a URL do frontend | Adicione a URL exata no painel do Render |

---

## Contribuição

Contribuições são bem-vindas! Sinta-se à vonto para abrir uma *issue* ou enviar um *pull request*.

### Passos para contribuir

1. Faça um *fork* do repositório.
2. Crie uma *branch* para sua feature: `git checkout -b feat/minha-feature`.
3. Instale as dependências e rode os testes: `./mvnw clean test`.
4. Commit suas alterações: `git commit -m "feat: minha feature"`.
5. Faça *push* e abra um *pull request*.

### Convenções

- Use commits semânticos: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`.
- Mantenha os testes atualizados — features sem cobertura não serão aceitas.
- Siga o padrão de pacotes por domínio (`auth`, `user`, `task`, `habit`, `goal`).
- Valide migrações Flyway localmente antes de enviar.

---

## Licença

Este projeto está licenciado sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para o texto completo.

---

## Autor

**Endriw Bento** — estudante de Engenharia de Software na Estácio (2024–2028), focado em backend Java/Spring Boot.

- **GitHub:** [github.com/EndriwEngSoft](https://github.com/EndriwEngSoft)
- **Portfólio:** [endriwdev.vercel.app](https://endriwdev.vercel.app/)
- **E-mail:** [endriwbento@gmail.com](mailto:endriwbento@gmail.com)

---

## Sobre

API para organização pessoal e produtividade: tarefas, hábitos e metas. Java 21 · Spring Boot 4.1 · Spring Security · JPA · PostgreSQL. (Em desenvolvimento)