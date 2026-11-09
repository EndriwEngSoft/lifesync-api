# ADR 0002: Provedor de LLM (Gemini) e camada de uso

- **Status:** aceito; camada de uso em produção decidida em 2026-11-09 (seção 6)
- **Data da verificação das fontes:** 2026-11-06
- **Verificação por execução contra o provedor:** 2026-11-09 (seção 6)

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
* O módulo nasce desligado em produção (`LIFESYNC_AI_ENABLED=false`) e só liga com provedor e chave configurados; mesmo ligado, só atende os usuários da lista de permitidos (seção 6).
* Os limites do provedor valem por projeto e são compartilhados por todos os usuários da aplicação. Por isso o módulo tem cota própria por usuário (`lifesync.ai.daily-quota`) e responde 502 ao cliente quando o provedor devolve 429.
* Mudanças de modelo (descontinuações) exigem apenas alterar a configuração, mas a qualidade do plano com outro modelo precisa ser reavaliada.

## 6. Camada de uso em produção e itens não verificados

* **Decisão (2026-11-09): camada gratuita, restrita a uma conta de demonstração por lista de permitidos.** O módulo só atende os usuários cujos ids estão em `LIFESYNC_AI_ALLOWED_USERS`; para todos os outros a rota responde o mesmo 503 do módulo desligado, sem chamar o provedor nem gravar log. A lista vazia significa ninguém, e um id inválido derruba a subida da aplicação.
  * **Motivo:** os termos da camada gratuita permitem que o conteúdo seja lido por revisores e usado para melhorar produtos, e desaconselham enviar informação pessoal. O mascaramento dos títulos é de melhor esforço. A única forma de garantir que nenhum dado de usuário real saia é garantir quem pode chamar: uma conta mantida pelo autor, só com dados sintéticos. A lista fecha o risco por construção, em vez de depender de um aviso ao usuário.
  * **Opções descartadas:** manter tudo desligado (não permite demonstrar o módulo em produção); camada paga para todos (exige faturamento, e o prazo de retenção e o preço não foram verificados); camada gratuita para todos com consentimento (quem aparece no título de outra pessoa não consentiu).
  * **Limites da decisão:** a garantia depende de a conta permitida só ter dados sintéticos, o que é disciplina de quem a mantém, não algo que o código verifique. O curinga `*` (qualquer usuário) existe para desenvolvimento e testes com o provedor de mentira e não deve ser usado com o provedor real. O limite de uso da camada gratuita é por projeto.
  * **Estado:** a lista está implementada e testada; o `render.yaml` continua com o módulo desligado e sem valor para a lista. Ligar em produção exige definir, no painel, `LIFESYNC_AI_ENABLED`, `LIFESYNC_AI_PROVIDER`, `LLM_API_KEY` e `LIFESYNC_AI_ALLOWED_USERS`; isso ainda não foi feito.
  * **Quando reavaliar:** se o módulo for aberto a usuários reais, a camada paga volta a ser o caminho, com retenção e custo verificados antes.
* **Verificado por execução em 2026-11-09** (camada gratuita, só dados sintéticos; números em [decisoes.md](decisoes.md)):
  * o modelo `gemini-3.5-flash-lite` existe e aceita `generateContent` (consulta a `GET /v1beta/models/gemini-3.5-flash-lite`);
  * `generationConfig.responseJsonSchema` com tipos em minúsculas (`object`, `array`, `string`), `enum` e `required` é aceito pelo `generateContent`: 19 chamadas, nenhuma rejeitada com HTTP 400. A documentação continua sem um exemplo desse campo para esse endpoint: a página de saída estruturada descreve a Interactions API, e a referência do `generateContent` lista o campo sem detalhar o formato (consultadas de novo em 2026-11-09);
  * as 18 respostas da avaliação passaram na validação do servidor;
  * um erro 5xx do provedor ocorreu uma vez e a tentativa seguinte funcionou;
  * com 10 s de pausa entre as chamadas, nenhuma recebeu 429.
* **Não verificado:**
  * valores numéricos de RPM, TPM e RPD da camada gratuita;
  * prazo exato de retenção dos logs na camada paga e localização do processamento;
  * se os tokens de raciocínio (`thoughtsTokenCount`) contam dentro de `maxOutputTokens`; nas chamadas feitas a saída ficou entre 245 e 532 tokens, longe do teto de 4096. Uma resposta cortada é tratada como saída inválida e consome cota;
  * o comportamento com dados reais e em produção: as chamadas foram feitas pelo teste de avaliação, com dados sintéticos; a suíte padrão e o CI nunca chamam um LLM.
