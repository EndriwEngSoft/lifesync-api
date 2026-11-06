# ADR 0002: Provedor de LLM (Gemini) e camada de uso

- **Status:** aceito, com uma decisão em aberto (camada de uso em produção, seção 6)
- **Data da verificação das fontes:** 2026-11-06

## 1. Contexto

O módulo de IA precisa de um modelo de linguagem para gerar o plano semanal. O provedor é acessado por um adaptador próprio sobre `RestClient` atrás da interface `LlmClient` (ver [ADR 0001](adr-0001-cliente-llm.md)), então trocar de provedor é uma mudança restrita a uma classe.

O cliente de cada usuário envia ao provedor um resumo das tarefas, hábitos e metas dele. Mesmo minimizado (sem nome, e-mail, ids nem descrições), o texto contém títulos escritos livremente, que podem conter dados pessoais. A política de dados do provedor, portanto, faz parte da decisão.

## 2. Opções consideradas

* **A. Gemini, camada gratuita.** Sem custo; adequada para desenvolvimento e demonstração com dados sintéticos.
* **B. Gemini, camada paga (faturamento ativado).** Custo por token; política de dados diferente (ver seção 3).
* **C. Outros provedores.** Não foram comparados em detalhe nesta decisão. O adaptador permite avaliar outros no futuro.

## 3. O que as fontes oficiais dizem (verificado em 2026-11-06)

**Política de dados** — [Termos adicionais da Gemini API](https://ai.google.dev/gemini-api/terms) (a URL redireciona para `aistudio.google.com/docs/terms`; a página indica vigência a partir de 23/03/2026):

| | Serviços gratuitos ("Unpaid") | Serviços pagos ("Paid") |
|---|---|---|
| Uso do conteúdo para melhorar produtos e modelos | Sim: o conteúdo é usado para "provide, improve, and develop Google products and services and machine learning technologies" | Não: "Google doesn't use your prompts" ou respostas "to improve our products" |
| Revisão humana | Sim: "human reviewers may read, annotate, and process your API input and output" (os dados são desvinculados da conta, da chave e do projeto antes) | A página não menciona revisão humana |
| Retenção | A página não informa um prazo geral | Prompts e respostas são registrados "for a limited period of time" apenas para detectar abuso e cumprir obrigações legais |
| Recomendação do provedor | "Do not submit sensitive, confidential, or personal information to the Unpaid Services" | — |

A [página de preços](https://ai.google.dev/gemini-api/docs/pricing) (redireciona para `aistudio.google.com/docs/pricing`) confirma, linha a linha para os modelos Flash e Flash-Lite, que a camada gratuita tem "Used to improve our products: Yes" e a paga, "No".

**Limites de uso** — [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) (redireciona para `aistudio.google.com/docs/rate-limits`):

* Os limites são medidos em requisições por minuto (RPM), tokens de entrada por minuto (TPM) e requisições por dia (RPD), e são aplicados **por projeto**, não por chave. As cotas diárias reiniciam à meia-noite do horário do Pacífico.
* **Os valores numéricos da camada gratuita não estão nessa página**: ela manda consultar os limites ativos no AI Studio. **Não verifiquei esses números.**
* A página cita `429 RESOURCE_EXHAUSTED` para limites baseados em gasto; não confirmei que o mesmo erro é o devolvido ao exceder RPM, TPM ou RPD. O adaptador trata qualquer HTTP 429 como limite do provedor.

**Modelos** — [Lista de modelos](https://ai.google.dev/gemini-api/docs/models) (redireciona para `aistudio.google.com/docs/models`):

* `gemini-2.5-flash` está marcado como **descontinuado**; a partir de 01/12/2026 as requisições a ele passam a ser roteadas para `gemini-3.5-flash-lite`.
* `gemini-3.5-flash-lite` e `gemini-3.6-flash` aparecem como estáveis. O padrão do módulo é `gemini-3.5-flash-lite`, configurável por `LIFESYNC_AI_MODEL`.

**Contrato da API** — [Referência de `generateContent`](https://ai.google.dev/api/generate-content) (redireciona para `aistudio.google.com/api-reference/generate-content`): endpoint `POST /v1beta/models/{model}:generateContent`; `systemInstruction`; `generationConfig.maxOutputTokens`, `responseMimeType` (`application/json`) e `responseJsonSchema`; `finishReason`; `promptFeedback.blockReason`; `usageMetadata` com `thoughtsTokenCount`. A referência não documenta `responseSchema`.

## 4. Decisão

Usar o **Gemini** por REST, pelo adaptador `GeminiLlmClient`, com a configuração em `lifesync.ai.*` (provedor, modelo, limites de tempo, tamanho máximo de saída, cota diária por usuário, tentativas). A chave vem somente da variável de ambiente `LLM_API_KEY`.

## 5. Consequências

* **A camada gratuita não é adequada para dados reais de usuários.** Os próprios termos desaconselham enviar informação pessoal, e o conteúdo pode ser lido por revisores e usado para melhorar produtos. Com a camada gratuita, o módulo só deve ser usado com dados sintéticos ou de quem consentiu de forma explícita.
* A minimização do contexto (sem nome, e-mail, ids e descrições; mascaramento de e-mails, CPFs e números longos nos títulos; limite de tamanho e de quantidade) reduz o risco, mas **não o elimina**: títulos livres podem conter dados de terceiros (ver o [modelo de ameaças](modelo-ameacas.md)).
* O módulo nasce desligado em produção (`LIFESYNC_AI_ENABLED=false`) e só liga com provedor e chave configurados.
* Os limites do provedor valem por projeto e são compartilhados por todos os usuários da aplicação. Por isso o módulo tem cota própria por usuário (`lifesync.ai.daily-quota`) e responde 502 ao cliente quando o provedor devolve 429.
* Mudanças de modelo (descontinuações) exigem apenas alterar a configuração, mas a qualidade do plano com outro modelo precisa ser reavaliada.

## 6. Decisão em aberto e itens não verificados

* **Em aberto (cabe ao mantenedor):** qual camada usar em produção. Para usuários reais, a opção coerente com a política de dados é a camada paga (faturamento ativado), com custo e prazo de retenção de logs a avaliar; a alternativa é manter o módulo desligado ou restrito a contas de demonstração.
* **Não verificado:**
  * valores numéricos de RPM, TPM e RPD da camada gratuita;
  * prazo exato de retenção dos logs na camada paga e localização do processamento;
  * se `responseJsonSchema` aceita exatamente o esquema enviado (tipos em minúsculas) no endpoint `generateContent`; a página de saída estruturada que consultei descrevia outro endpoint (Interactions API). A resposta é sempre revalidada no servidor, então uma rejeição do esquema (HTTP 400) aparece como erro do provedor e se corrige em `WeeklyPlanSchema`;
  * se os tokens de raciocínio (`thoughtsTokenCount`) contam dentro de `maxOutputTokens`; o padrão (4096) deixa margem;
  * qualquer comportamento real do modelo: nenhuma chamada ao provedor foi feita durante o desenvolvimento, e os testes automatizados nunca chamam um LLM.
