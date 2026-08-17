# LifeSync API

> API REST de produtividade pessoal construída em Spring Boot, unificando **tarefas**, **hábitos** e **metas** sob uma camada de autenticação JWT com proteção contra IDOR.

[![CI](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml/badge.svg)](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Índice

1. [Visão geral](#visão-geral)
2. [Stack técnica](#stack-técnica)
3. [Arquitetura](#arquitetura)
4. [Endpoints da API](#endpoints-da-api)
5. [Como rodar localmente](#como-rodar-localmente)
6. [Variáveis de ambiente](#variáveis-de-ambiente)
7. [Testes](#testes)
8. [Deploy](#deploy)
9. [Licença](#licença)
10. [Autor](#autor)

---

## Visão geral

A LifeSync API reúne três domínios de produtividade pessoal — **tarefas** (ações pontuais), **hábitos** (recorrência com sequência/streak) e **metas** (progresso mensurável em direção a um alvo) — em uma única API autenticada. Cada domínio é um pacote autocontido (entidade, DTO, repositório, serviço, controller) e todos compartilham as mesmas regras de segurança e o mesmo padrão de acesso a dados.

Pontos que valem destaque técnico:

- **IDOR bloqueado por design.** Toda consulta de serviço é escopada por `(id, userId)`. Se o recurso existe mas pertence a outro usuário, a resposta é `404` — nunca `403` — para não expor a existência de recursos alheios.
- **IDs são UUID**, não sequenciais. Evita enumeração de recursos e inferência de volume de dados pela URL.
- **Tokens de acesso e refresh são tipados.** O JWT carrega um claim `type` (`access`/`refresh`), então um refresh token vazado não pode ser usado para autenticar em rotas protegidas.
- **Soft delete em hábitos.** Remover um hábito marca `active = false` em vez de apagar — o histórico de check-ins (e o streak) é preservado.
- **Testes de integração rodam contra PostgreSQL real** via Testcontainers, tanto localmente quanto no CI — sem banco em memória mascarando comportamento específico do Postgres.

---

## Stack técnica

| Camada | Tecnologia |
|---|---|
| Linguagem / Runtime | Java 21, Spring Boot 4.1 |
| Web / Segurança | Spring Web, Spring Security |
| Persistência | Spring Data JPA, Hibernate, PostgreSQL |
| Migrações | Flyway |
| Autenticação | JWT (jjwt 0.13.0) |
| Documentação | SpringDoc OpenAPI 3 (Swagger UI) |
| Testes | JUnit 5, Mockito, Testcontainers |
| Build | Maven (via wrapper `mvnw`) |
| Deploy | Docker (multi-stage), Render, Neon (PostgreSQL serverless) |
| CI/CD | GitHub Actions |

---

## Arquitetura

### Estrutura de pacotes

Organização por domínio (feature package), não por camada técnica:

```
com.lifesync.api
├── common/       # BaseEntity, ApiErrorResponse — compartilhados entre domínios
├── config/       # SecurityConfig, OpenApiConfig
├── exception/    # Exceções de domínio + GlobalExceptionHandler
├── security/     # JwtTokenProvider, JwtAuthFilter, UserDetailsServiceImpl
├── auth/         # Registro, login, refresh de token
├── user/         # Perfil do usuário autenticado (/me)
├── task/         # Tarefas + subtarefas
├── habit/        # Hábitos + histórico de check-ins
├── goal/         # Metas + histórico de progresso
└── LifesyncApiApplication.java
```

### Relacionamento entre entidades

| Relação | Tipo | Comportamento no delete |
|---|---|---|
| `Task` → `User` | `@ManyToOne` (LAZY) | Sem cascade — apagar usuário não afeta tarefas. |
| `Task` → `Goal` | `@ManyToOne` (LAZY, opcional) | Sem cascade — apagar a meta apenas desvincula (`goal = null`). |
| `Task` → `SubTask` | `@OneToMany` (`cascade = ALL`, `orphanRemoval = true`) | Subtarefas são apagadas junto com a tarefa. |
| `Habit` → `Goal` | `@ManyToOne` (LAZY, opcional) | Sem cascade — desvincula ao apagar a meta. |
| `HabitHistory` → `Habit` | `@ManyToOne` (LAZY) | Sem cascade — preservado no soft delete do hábito. |
| `Goal` → `GoalProgress` | `@OneToMany` (`cascade = ALL`, `orphanRemoval = true`) | Histórico de progresso é apagado junto com a meta. |

### Migrações do banco (Flyway)

| Versão | Conteúdo |
|---|---|
| `V1__create_lifesync_schema.sql` | Schema base: `users`, `tasks`, `sub_tasks`, `habits`, `habit_history`. |
| `V2__create_goals_and_link_task_habit.sql` | Cria `goals`, `goal_progress`; adiciona FK `goal_id` em `tasks` e `habits`. |

---

## Endpoints da API

Base local: `http://localhost:8080` · Documentação interativa: `/swagger-ui/index.html`

Todos os endpoints exigem `Authorization: Bearer <access_token>`, exceto os de `/api/auth/*`.

#### Auth

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/register` | Registra um usuário e retorna access + refresh token |
| `POST` | `/api/auth/login` | Autentica e retorna os tokens |
| `POST` | `/api/auth/refresh` | Troca um refresh token válido por um novo access token |

#### User

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/users/me` | Retorna o perfil do usuário autenticado |
| `PUT` | `/api/users/me` | Atualiza o perfil (nome, timezone, etc.) |

#### Task

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/tasks` | Cria uma tarefa (`goalId` opcional) |
| `GET` | `/api/tasks/{taskId}` | Busca uma tarefa por ID |
| `GET` | `/api/tasks` | Lista tarefas (paginado, filtro por status/prioridade) |
| `PUT` | `/api/tasks/{taskId}` | Atualiza campos da tarefa |
| `DELETE` | `/api/tasks/{taskId}` | Remove a tarefa (cascata para subtarefas) |
| `PATCH` | `/api/tasks/{taskId}/status` | Atualiza apenas o status |
| `POST` | `/api/tasks/{taskId}/subtasks` | Adiciona uma subtarefa |
| `PUT` | `/api/tasks/{taskId}/subtasks/{subtaskId}` | Atualiza o título de uma subtarefa |
| `PATCH` | `/api/tasks/{taskId}/subtasks/{subtaskId}` | Alterna conclusão da subtarefa |
| `DELETE` | `/api/tasks/{taskId}/subtasks/{subtaskId}` | Remove uma subtarefa |

#### Habit

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/habits` | Cria um hábito (frequência + meta por período) |
| `GET` | `/api/habits/{habitId}` | Busca um hábito por ID |
| `GET` | `/api/habits` | Lista hábitos (paginado) |
| `PUT` | `/api/habits/{habitId}` | Atualiza campos do hábito |
| `DELETE` | `/api/habits/{habitId}` | Soft delete (histórico é preservado) |
| `POST` | `/api/habits/{habitId}/checkin` | Registra o check-in de hoje |
| `GET` | `/api/habits/{habitId}/history` | Histórico de check-ins (paginado) |

#### Goal

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/goals` | Cria uma meta com valor alvo e unidade |
| `GET` | `/api/goals/{goalId}` | Busca uma meta por ID |
| `GET` | `/api/goals` | Lista metas (paginado, filtro por status) |
| `PUT` | `/api/goals/{goalId}` | Atualiza campos da meta |
| `DELETE` | `/api/goals/{goalId}` | Remove a meta (desvincula tasks/habits, apaga histórico) |
| `POST` | `/api/goals/{goalId}/progress` | Registra progresso (valor absoluto) |
| `GET` | `/api/goals/{goalId}/progress` | Histórico de progresso |

---

## Como rodar localmente

### Pré-requisitos

- JDK 21+
- Docker (necessário para os testes de integração via Testcontainers)
- PostgreSQL 16+ (opcional, só se não quiser usar Testcontainers)

### Passos

```bash
git clone https://github.com/EndriwEngSoft/lifesync-api.git
cd lifesync-api

./mvnw clean test          # roda a suíte completa (sobe Postgres via Testcontainers)
./mvnw spring-boot:run      # inicia a aplicação em http://localhost:8080
```

Sem nenhuma variável de ambiente configurada, a aplicação sobe com valores de fallback definidos em `application.yml` (banco local `lifesync_db`, senha `123456`, chave JWT de desenvolvimento). Esses fallbacks existem só para facilitar o setup local — em produção o perfil `prod` exige que tudo venha do ambiente (`application-prod.yml`).

---

## Variáveis de ambiente

| Variável | Fallback (dev) | Descrição |
|---|---|---|
| `DB_PASSWORD` | `123456` | Senha do PostgreSQL |
| `JWT_SECRET` | chave de dev embutida | Chave HMAC-SHA256 para assinatura dos tokens (mínimo 256 bits / 32 caracteres) |
| `JWT_EXPIRATION` | `86400000` (24h) | TTL do access token, em ms |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 dias) | TTL do refresh token, em ms |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origens permitidas no CORS |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/lifesync_db` | URL JDBC do banco |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuário do banco |
| `PORT` | `8080` | Porta HTTP (injetada automaticamente por plataformas de deploy) |

---

## Testes

**34 testes**, todos passando — combinando testes unitários (Mockito, sem banco) e um teste de integração (Testcontainers, PostgreSQL real).

| Classe | Tipo | Qtde. |
|---|---|---|
| `TaskServiceTest` | Unitário (Mockito) | 7 |
| `HabitServiceTest` | Unitário (Mockito) | 6 |
| `JwtTokenProviderTest` | Unitário (puro) | 6 |
| `GoalServiceTest` | Unitário (Mockito) | 5 |
| `UserServiceTest` | Unitário (Mockito) | 4 |
| `AuthControllerTest` | Unitário (MockMvc) | 3 |
| `LifesyncApiApplicationTests` | Integração (Testcontainers) | 2 |
| `JwtAuthFilterTest` | Unitário (puro) | 1 |

```bash
./mvnw clean test
```

O teste de integração sobe o contexto Spring completo com um container PostgreSQL real, executa as migrações Flyway (`V1` → `V2`) e valida o schema resultante.

---

## Deploy

**Stack de produção:** Render (Web Service, plano free, `runtime: docker`) + Neon (PostgreSQL serverless).

### Docker (build multi-stage)

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY --from=build /app/target/lifesync-api.jar app.jar
ENTRYPOINT ["java", "-Xmx400m", "-jar", "/app.jar"]
```

O `-Xmx400m` é proposital: o plano gratuito do Render dá 512MB de RAM, e reservar margem para o SO evita `OutOfMemoryError`.

### CI/CD

`.github/workflows/ci.yml` roda em todo push/PR para `main`: sobe JDK 21, dá permissão de execução ao `mvnw` e executa `./mvnw -B clean test` em `ubuntu-latest` — que já vem com Docker, então os Testcontainers funcionam sem configuração extra.

### Infraestrutura como código

O deploy no Render é guiado por `render.yaml` (Blueprint), que define o serviço, o health check (`/actuator/health`) e as variáveis de ambiente sensíveis como `sync: false` (preenchidas manualmente no painel).

---

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para o texto completo.

---

## Autor

**Endriw Bento** — estudante de Engenharia de Software (Estácio, 2024–2028), focado em backend Java/Spring Boot.

- GitHub: [github.com/EndriwEngSoft](https://github.com/EndriwEngSoft)
- Portfólio: [endriwdev.vercel.app](https://endriwdev.vercel.app/)
