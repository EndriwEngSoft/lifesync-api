# ADR 0001: Estratégia de Integração do Cliente LLM

- **Status:** aceito
- **Data da verificação das fontes:** 2026-11-06

## 1. Contexto
Precisamos integrar a LifeSync API a um provedor de LLM para a funcionalidade de "Plano Semanal Inteligente". O projeto utiliza Spring Boot 4.1. Precisamos decidir se utilizaremos uma abstração de alto nível (como o Spring AI) ou se construiremos um cliente próprio via HTTP puro para o MVP, visando maior controle e aprendizado arquitetural.

## 2. Opções Consideradas
* **Opção A: Spring AI 2.x**. Framework oficial, traz abstrações prontas (Prompts, ChatClient, integração com dezenas de provedores).
* **Opção B: Adaptador RestClient Próprio**. Uso do `RestClient` nativo do Spring para chamadas HTTP diretas à API REST do provedor de LLM escolhido, encapsulado por uma interface interna.

## 3. Decisão
Optamos pela **Opção B (Adaptador RestClient Próprio) para o MVP**, encapsulado sob uma interface de domínio chamada `LlmClient`.

## 4. Verificação da compatibilidade da Opção A

A decisão **não** se baseia em incompatibilidade: a documentação oficial confirma que a Opção A seria viável com esta versão do Spring Boot.

* A página [Getting Started do Spring AI](https://docs.spring.io/spring-ai/reference/getting-started.html) afirma: "Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x." Na data da verificação, a versão estável listada era a 2.0.2.
* A página de [requisitos do Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html) mostra a versão 4.1.2, com Java 17 ou superior e Spring Framework 7.0.10 ou superior. O projeto está no Spring Boot 4.1.0 (Java 21).
* Não verifiquei a qualidade nem a cobertura do starter do Gemini do Spring AI; a página consultada apenas aponta para a documentação de "Google GenAI".

## 5. Consequências

### Positivas
* **Controle Total:** Tratamento explícito de timeouts e mapeamento manual de erros HTTP (ex: HTTP 429 para rate limit, 5xx para indisponibilidade).
* **Aprendizado Técnico:** Força o entendimento prático do protocolo do provedor e da estrutura de payloads JSON em IA generativa.
* **Desacoplamento:** A interface `LlmClient` protege nossa regra de negócio. Se no futuro o Spring AI se mostrar necessário (ex: para RAG avançado), a refatoração será restrita à criação de uma nova implementação dessa interface.
* **Sem dependência nova:** o adaptador usa só o que o projeto já tem (`RestClient`, Jackson).

### Negativas
* **Trabalho Manual:** Precisamos criar os DTOs de Request/Response específicos do provedor e gerenciar retentativas (retry) e backoff manualmente. Isso foi feito no `GeminiLlmClient` (retry só para erros transitórios, com backoff exponencial e número máximo de tentativas).
* **Sem autoconfiguração do builder:** o `RestClient` é criado com `RestClient.builder()` explícito (`GeminiRestClientConfig`), em vez de usar o `RestClient.Builder` autoconfigurado pelo Spring Boot. Com isso, personalizações globais e instrumentação automática de HTTP client não se aplicam a ele. Se a instrumentação de métricas for necessária, vale reavaliar essa escolha.

## 6. Como Reverter
Basta adicionar a dependência do Spring AI no `pom.xml`, criar uma nova classe que implemente a interface `LlmClient` usando as abstrações do framework, e trocar a injeção de dependência na configuração (o provedor é escolhido por `lifesync.ai.provider`).
