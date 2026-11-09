# LifeSync API

> API REST de produtividade pessoal em Spring Boot: **tarefas** com subtarefas, **hábitos** com streak e histórico, e **metas** com progresso, tudo atrás de autenticação JWT com proteção contra IDOR.

[![CI](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml/badge.svg)](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**API no ar:** [Swagger UI](https://lifesync-api-ezkx.onrender.com/swagger-ui/index.html) · [Health check](https://lifesync-api-ezkx.onrender.com/actuator/health)

> A API roda no plano gratuito do Render, que desliga o serviço após 15 minutos sem acesso. O primeiro acesso depois disso pode levar cerca de um minuto para responder; os seguintes são normais.

---

## Índice

1. [Teste em 1 minuto](#teste-em-1-minuto)
2. [Decisões técnicas](#decisões-técnicas)
3. [Stack técnica](#stack-técnica)
4. [Arquitetura](#arquitetura)
5. [Endpoints da API](#endpoints-da-api)
6. [Testes](#testes)
7. [Como rodar localmente](#como-rodar-localmente)
8. [Variáveis de ambiente](#variáveis-de-ambiente)
9. [Deploy](#deploy)
10. [Licença](#licença)
11. [Autor](#autor)

---

## Teste em 1 minuto

Pelo navegador, sem instalar nada:

1. Abra o [Swagger UI](https://lifesync-api-ezkx.onrender.com/swagger-ui/index.html).
2. Em **Auth → `POST /api/auth/register`**, clique em *Try it out* e envie:
   ```json
   {
     "name": "Visitante",
     "username": "visitante01",
     "email": "visitante01@example.com",
     "password": "senha123"
   }
   ```
3. Copie o `accessToken` da resposta, clique em **Authorize** (cadeado no topo) e cole o token.
4. Crie um hábito em `POST /api/habits` e faça o check-in em `POST /api/habits/{habitId}/checkin`:
   ```json
   { "name": "Ler 20 páginas", "frequency": "DAILY", "targetPerPeriod": 1 }
   ```

Para ver a proteção contra IDOR, crie uma segunda conta e tente buscar o hábito da primeira: a resposta é `404`.

---

## Decisões técnicas

- **IDOR bloqueado por design.** Toda consulta de serviço é escopada por `(id, userId)`. Se o recurso existe mas pertence a outro usuário, a resposta é `404`, nunca `403`, para não revelar que o recurso existe.
- **IDs UUID**, não sequenciais. Evita enumeração de recursos e inferência de volume de dados pela URL.
- **Access e refresh tokens tipados.** O JWT carrega um claim `type` (`access`/`refresh`). Um refresh token vazado não autentica rotas protegidas, e um access token não serve para renovar a sessão.
- **Check-in calculado no fuso do usuário.** "Hoje" vem de um `Clock` injetado combinado com o fuso salvo no perfil (padrão `America/Sao_Paulo`), não do fuso do servidor (UTC na nuvem). Sem isso, um check-in às 23h em São Paulo cairia no dia seguinte. O `Clock` injetado também deixa os testes determinísticos.
- **Streak por período, não por data exata.** Hábitos `DAILY`, `WEEKLY` e `MONTHLY` comparam períodos: um check-in na sexta e outro na segunda seguinte contam como semanas consecutivas.
- **Check-in duplicado bloqueado em duas camadas.** Uma checagem rápida no serviço cobre o caso comum, e uma constraint `UNIQUE (habit_id, check_in_date)` no banco cobre a condição de corrida de duas requisições simultâneas. O `saveAndFlush` força o `INSERT` dentro do `try`, para a violação virar `409` em vez de erro genérico.
- **Soft delete em hábitos.** Remover um hábito marca `active = false`, preservando o histórico de check-ins e o streak.
- **Erros padronizados.** Um `@RestControllerAdvice` converte exceções de domínio, do framework e do banco no status HTTP correto (`400`, `401`, `403`, `404`, `405`, `409`, `415`), sempre com o mesmo corpo: `status`, `message`, `timestamp` e `path`.
- **Schema versionado e validado.** O Flyway cria o schema e o Hibernate roda com `ddl-auto: validate`: se uma entidade divergir da migration, a aplicação não sobe. O teste de integração prova isso contra um PostgreSQL real.

---

## Stack técnica

| Camada | Tecnologia |
|---|---|
| Linguagem / Runtime | Java 21, Spring Boot 4.1 |
| Web / Segurança | Spring Web, Spring Security (stateless) |
| Persistência | Spring Data JPA, Hibernate, PostgreSQL |
| Migrações | Flyway |
| Autenticação | JWT (jjwt 0.13.0), BCrypt |
| Validação | Bean Validation |
| Documentação | SpringDoc OpenAPI 3 (Swagger UI) |
| Testes | JUnit 5, Mockito, MockMvc, Testcontainers |
| Build | Maven (via wrapper `mvnw`) |
| Deploy | Docker (multi-stage), Render, Neon (PostgreSQL serverless) |
| CI | GitHub Actions |

---

## Arquitetura

### Estrutura de pacotes

Organização por domínio (feature package), não por camada técnica:

```
com.lifesync.api
├── common/       # BaseEntity, ApiErrorResponse (compartilhados entre domínios)
├── config/       # SecurityConfig, OpenApiConfig, ClockConfig
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
| `Task` → `User` | `@ManyToOne` (LAZY) | Sem cascade: apagar usuário não afeta tarefas. |
| `Task` → `Goal` | `@ManyToOne` (LAZY, opcional) | Sem cascade: apagar a meta apenas desvincula (`goal = null`). |
| `Task` → `SubTask` | `@OneToMany` (`cascade = ALL`, `orphanRemoval = true`) | Subtarefas são apagadas junto com a tarefa. |
| `Habit` → `Goal` | `@ManyToOne` (LAZY, opcional) | Sem cascade: desvincula ao apagar a meta. |
| `HabitHistory` → `Habit` | `@ManyToOne` (LAZY) | Sem cascade: preservado no soft delete do hábito. |
| `Goal` → `GoalProgress` | `@OneToMany` (`cascade = ALL`, `orphanRemoval = true`) | Histórico de progresso é apagado junto com a meta. |

### Migrações do banco (Flyway)

| Versão | Conteúdo |
|---|---|
| `V1__create_lifesync_schema.sql` | Schema base: `users`, `tasks`, `sub_tasks`, `habits`, `habit_history`. |
| `V2__create_goals_and_link_task_habit.sql` | Cria `goals` e `goal_progress`; adiciona FK `goal_id` em `tasks` e `habits`. |

---

## Endpoints da API

Base em produção: `https://lifesync-api-ezkx.onrender.com` · Base local: `http://localhost:8080` · Documentação interativa: `/swagger-ui/index.html`

Todos os endpoints exigem `Authorization: Bearer <access_token>`, exceto os de `/api/auth/*`, o Swagger e o `/actuator/health`.

#### Auth

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/register` | Registra um usuário e retorna access + refresh token |
| `POST` | `/api/auth/login` | Autentica e retorna os tokens |
| `POST` | `/api/auth/refresh` | Recebe o refresh token no header `Authorization` e retorna um novo par de tokens |

#### User

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/users/me` | Retorna o perfil do usuário autenticado |
| `PUT` | `/api/users/me` | Atualiza o perfil (nome, timezone etc.) |

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
| `POST` | `/api/habits/{habitId}/checkin` | Registra o check-in de hoje (no fuso do usuário) |
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

## Testes

**65 testes**, executados a cada push e pull request pelo CI (veja o badge no topo). A maioria é unitária (Mockito, sem banco); o teste de integração sobe um PostgreSQL real com Testcontainers.

| Classe | Tipo | Qtde. |
|---|---|---|
| `HabitServiceTest` | Unitário (Mockito + `Clock` fixo) | 19 (9 + 1 parametrizado com 10 casos de streak) |
| `GlobalExceptionHandlerTest` | Web (MockMvc) | 10 |
| `TaskServiceTest` | Unitário (Mockito) | 7 |
| `UserServiceTest` | Unitário (Mockito) | 7 |
| `JwtTokenProviderTest` | Unitário | 6 |
| `RegisterRequestValidationTest` | Unitário (Bean Validation) | 6 |
| `GoalServiceTest` | Unitário (Mockito) | 5 |
| `AuthControllerTest` | Fatia web (`@WebMvcTest` + MockMvc) | 3 |
| `JwtAuthFilterTest` | Unitário | 1 |
| `LifesyncApiApplicationTests` | Integração (Testcontainers, PostgreSQL 16) | 1 |

```bash
./mvnw clean test
```

O teste parametrizado do `HabitServiceTest` cobre as regras de streak por frequência: período consecutivo, mesmo período, lacuna e troca de frequência, para `DAILY`, `WEEKLY` e `MONTHLY`.

O teste de integração sobe o contexto Spring completo contra um container PostgreSQL, aplica as migrações Flyway (`V1` → `V2`) e, com `ddl-auto: validate`, falha se alguma entidade não bater com o schema.

---

## Como rodar localmente

### Pré-requisitos

- JDK 21+
- Docker (necessário para o teste de integração via Testcontainers)
- PostgreSQL 16+ (só para rodar a aplicação localmente)

### Passos

```bash
git clone https://github.com/EndriwEngSoft/lifesync-api.git
cd lifesync-api

./mvnw clean test          # roda a suíte completa (sobe Postgres via Testcontainers)
./mvnw spring-boot:run     # inicia a aplicação em http://localhost:8080
```

Sem variáveis de ambiente configuradas, a aplicação sobe com os valores de fallback de `application.yml` (banco local `lifesync_db`, senha `123456`, chave JWT de desenvolvimento). Esses fallbacks existem só para facilitar o setup local. Em produção, o perfil `prod` (`application-prod.yml`) não tem fallback: tudo vem do ambiente.

---

## Variáveis de ambiente

| Variável | Fallback (dev) | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (nenhum) | Use `prod` em produção |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/lifesync_db` | URL JDBC do banco |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `123456` | Senha do PostgreSQL |
| `JWT_SECRET` | chave de dev embutida | Chave HMAC-SHA256 para assinar os tokens (mínimo 32 caracteres / 256 bits) |
| `JWT_EXPIRATION` | `86400000` (24h) | Validade do access token, em ms |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7 dias) | Validade do refresh token, em ms |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origens permitidas no CORS (CSV) |
| `PORT` | `8080` | Porta HTTP (injetada automaticamente pelo Render) |

---

## Deploy

**Produção:** Render (Web Service, plano free, `runtime: docker`) + Neon (PostgreSQL serverless).

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

O primeiro estágio compila com Maven; o segundo leva só o `.jar` para uma imagem com JRE Alpine, sem Maven nem código-fonte. O `-Xmx400m` é proposital: o plano gratuito do Render tem 512 MB de RAM, e limitar o heap deixa margem para o restante da JVM e do sistema.

### CI

`.github/workflows/ci.yml` roda em todo push e pull request para `main`: configura o JDK 21 e executa `./mvnw -B clean test` em `ubuntu-latest`, que já tem Docker, então o Testcontainers funciona sem configuração extra. O build da imagem pula os testes (`-Dmaven.test.skip=true`); quem roda a suíte é o CI.

### Infraestrutura como código

O deploy no Render é definido em `render.yaml` (Blueprint): serviço, health check (`/actuator/health`) e variáveis de ambiente. As sensíveis ficam como `sync: false` e são preenchidas no painel, nunca no repositório.

---

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE).

---

## Autor

**Endriw Colvara Bento**, estudante de Engenharia de Software (Estácio), com foco em backend Java e Spring Boot.

- GitHub: [github.com/EndriwEngSoft](https://github.com/EndriwEngSoft)
- LinkedIn: [linkedin.com/in/endriw-bento](https://www.linkedin.com/in/endriw-bento/)
- Portfólio: [endriwdev.vercel.app](https://endriwdev.vercel.app/)
