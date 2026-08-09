# LifeSync API

![CI](https://github.com/EndriwEngSoft/lifesync-api/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?logo=jsonwebtokens&logoColor=white)

API REST de organização e produtividade pessoal — tarefas, hábitos com cálculo de sequência (streak) e, nas próximas fases, metas, estudos e finanças. Construída do zero como projeto de portfólio, com foco em decisões de arquitetura defensáveis, não só em fazer o CRUD funcionar.

## Sobre o projeto

O LifeSync não é uma lista de tarefas. É uma tentativa de modelar produtividade pessoal como um sistema só, onde tarefas, hábitos e (adiante) metas, estudos e finanças compartilham a mesma base de autenticação e as mesmas regras de segurança, em vez de serem features isoladas coladas uma do lado da outra.

O projeto está sendo construído em fatias. A fatia atual cobre autenticação, perfil de usuário, tarefas com subtarefas e hábitos com streak — o módulo que exige mais raciocínio, porque o cálculo não é um contador simples: depende da frequência do hábito (diária, semanal ou mensal), do timezone de cada usuário, e do que acontece quando essa frequência muda no meio de uma sequência já em andamento.

Metas, estudos, finanças simples, gamificação e dashboard fazem parte do escopo original e ficam para as próximas fatias.

## Tecnologias

- **Java 21**
- **Spring Boot 4.1** — Web, Security, Data JPA, Validation, Actuator
- **Spring Security + JWT** (jjwt 0.13.0) — autenticação stateless, com distinção entre access e refresh token
- **PostgreSQL** + Hibernate
- **springdoc-openapi** — documentação interativa via Swagger UI
- **Bean Validation**
- **Lombok**
- **JUnit 5 + Mockito + Testcontainers**
- **Maven**
- **GitHub Actions** — CI rodando a suíte de testes a cada push

## Decisões de arquitetura

Alguns pontos que valem destaque — o raciocínio completo de cada um está no histórico de commits, não só aqui:

- **IDOR tratado na camada de Service, não no Controller.** Toda consulta a um recurso do usuário busca por `(id do recurso + id do usuário)` na mesma query — nunca por id sozinho com checagem depois. Se não bater, o usuário recebe 404: o recurso de outra pessoa simplesmente não existe pra ele.
- **UUID como chave primária**, não `Long` sequencial — evita expor volume de registros ou permitir enumeração de IDs (`/tasks/1`, `/tasks/2`...).
- **Access token e refresh token com um claim de tipo explícito.** Sem essa distinção, um refresh token vazado (vida de 7 dias) funcionaria como access token de vida longa em qualquer rota protegida.
- **Organização por feature** (`task/`, `habit/`, `auth/`...), não por camada. Cada pacote é praticamente um *bounded context* — o código de um módulo não fica espalhado entre pastas genéricas de controller/service/repository.
- **Soft delete em hábitos** (`active = false`), porque apagar de verdade destruiria o histórico de check-ins que dá sentido ao streak.
- **`/actuator/health` público, resto de `/actuator/**` autenticado** — pensado para quando o projeto for containerizado: probes de liveness/readiness normalmente não mandam credencial nenhuma.

## Funcionalidades

Todas as rotas abaixo, exceto as de `/api/auth`, exigem `Authorization: Bearer <token>`.

**Auth** — `/api/auth`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/register` | Cria usuário e retorna o par de tokens |
| POST | `/login` | Autentica e retorna o par de tokens |
| POST | `/refresh` | Renova o access token a partir de um refresh token válido |

**User** — `/api/users`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/me` | Dados do usuário autenticado |
| PUT | `/me` | Atualiza o próprio perfil |

**Task** — `/api/tasks`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/` | Cria task |
| GET | `/` | Lista as tasks do usuário (paginado) |
| GET | `/{taskId}` | Detalhe de uma task |
| PUT | `/{taskId}` | Atualiza uma task |
| PATCH | `/{taskId}/status` | Atualiza só o status (transição controlada) |
| DELETE | `/{taskId}` | Remove uma task |
| POST | `/{taskId}/subtasks` | Adiciona subtask |
| PUT | `/{taskId}/subtasks/{subtaskId}` | Atualiza título da subtask |
| PATCH | `/{taskId}/subtasks/{subtaskId}` | Alterna conclusão da subtask |
| DELETE | `/{taskId}/subtasks/{subtaskId}` | Remove subtask |

**Habit** — `/api/habits`

| Método | Rota | Descrição |
|---|---|---|
| POST | `/` | Cria hábito |
| GET | `/` | Lista os hábitos do usuário |
| GET | `/{habitId}` | Detalhe de um hábito (com streak atual) |
| PUT | `/{habitId}` | Atualiza um hábito |
| DELETE | `/{habitId}` | Remove um hábito (soft delete) |
| POST | `/{habitId}/checkin` | Registra que o hábito foi cumprido hoje |
| GET | `/{habitId}/history` | Histórico de check-ins (paginado) |

## Como rodar localmente

**Pré-requisitos:** JDK 21 e PostgreSQL rodando localmente. Maven não é obrigatório — o projeto já traz o wrapper (`mvnw`/`mvnw.cmd`).

1. Clone o repositório e entre na pasta do projeto.
2. Crie o banco no Postgres:
   ```sql
   CREATE DATABASE lifesync_db;
   ```
3. (Opcional) defina as variáveis de ambiente `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION` e `JWT_REFRESH_EXPIRATION`. Sem elas, a aplicação sobe com valores padrão de desenvolvimento já definidos no `application.yml` — úteis pra rodar local rápido, mas não pra usar fora do seu ambiente.
4. Rode a aplicação:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run

   # Linux/macOS
   ./mvnw spring-boot:run
   ```
5. Acesse a documentação interativa em `http://localhost:8080/swagger-ui/index.html`.

A aplicação sobe em `http://localhost:8080`. Rotas públicas: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**` e `/actuator/health` — todo o resto exige token.

## Testes

Testes automatizados com JUnit 5 e Mockito, cobrindo regra de negócio real — não só CRUD: defesa contra IDOR, distinção entre access e refresh token, condição de corrida no check-in duplicado de hábito, reset de streak na troca de frequência. Há também um teste de contexto completo do Spring, que sobe contra um PostgreSQL real via **Testcontainers** — exige apenas o Docker rodando localmente, sem necessidade de banco instalado à parte.

```bash
# Windows
.\mvnw.cmd clean test

# Linux/macOS
./mvnw clean test
```

A suíte também roda automaticamente a cada push via GitHub Actions (`.github/workflows/ci.yml`).

## Estrutura do projeto

Organização por feature — cada módulo de domínio segue o mesmo padrão interno (`entity/`, `repository/`, `service/`, `controller/`, `dto/`):

```
com.lifesync.api
├── common/       # BaseEntity, ApiErrorResponse
├── config/       # SecurityConfig
├── exception/    # exceptions customizadas + handler global
├── security/     # JWT, filtro de autenticação, UserDetails
├── user/
├── auth/
├── task/         # + subtasks
└── habit/        # + histórico de check-ins
```

## Autor

**Endriw Colvara Bento** — estudante de Engenharia de Software, focado em backend Java.

- GitHub: [github.com/EndriwEngSoft](https://github.com/EndriwEngSoft)
- Portfólio: [portfolio-endriw.vercel.app](https://portfolio-endriw.vercel.app)
