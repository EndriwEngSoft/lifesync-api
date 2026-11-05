# ADR 0001: Estratégia de Integração do Cliente LLM

## 1. Contexto
Precisamos integrar a LifeSync API a um provedor de LLM para a funcionalidade de "Plano Semanal Inteligente". O projeto utiliza Spring Boot 4.1.0. Precisamos decidir se utilizaremos uma abstração de alto nível (como o Spring AI) ou se construiremos um cliente próprio via HTTP puro para o MVP, visando maior controle e aprendizado arquitetural.

## 2. Opções Consideradas
* **Opção A: Spring AI 2.x**. Framework oficial, traz abstrações prontas (Prompts, ChatClient, integração com dezenas de provedores).
* **Opção B: Adaptador RestClient Próprio**. Uso do `RestClient` nativo do Spring para chamadas HTTP diretas à API REST do provedor de LLM escolhido, encapsulado por uma interface interna.

## 3. Decisão
Optamos pela **Opção B (Adaptador RestClient Próprio) para o MVP**, encapsulado sob uma interface de domínio chamada `LlmClient`.

## 4. Consequências

### Positivas
* **Controle Total:** Tratamento explícito de timeouts e mapeamento manual de erros HTTP (ex: HTTP 429 para rate limit, 5xx para indisponibilidade).
* **Aprendizado Técnico:** Força o entendimento prático do protocolo do provedor e da estrutura de payloads JSON em IA generativa.
* **Desacoplamento:** A interface `LlmClient` protege nossa regra de negócio. Se no futuro o Spring AI se mostrar necessário (ex: para RAG avançado), a refatoração será restrita à criação de uma nova implementação dessa interface.

### Negativas
* **Trabalho Manual:** Precisaremos criar os DTOs de Request/Response específicos do provedor e gerenciar retentativas (retry) e backoff manualmente.

## 5. Como Reverter
Basta adicionar a dependência do Spring AI no `pom.xml`, criar uma nova classe que implemente a interface `LlmClient` usando as abstrações do framework, e trocar a injeção de dependência na configuração.