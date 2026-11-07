# Módulo de IA: decisões de design

> **Status:** em desenvolvimento. Este documento registra as decisões tomadas no desenho do módulo e o motivo de cada uma, para que quem ler o código no futuro entenda por que as coisas são como são. Ele é atualizado conforme o módulo evolui. Documentos relacionados: [ADR 0001](adr-0001-cliente-llm.md) (cliente LLM), [ADR 0002](adr-0002-provedor-llm.md) (provedor e política de dados) e [modelo de ameaças](modelo-ameacas.md).

## Contexto

O módulo de IA gera um **plano semanal** para o usuário a partir das tarefas, hábitos e metas dele, usando um modelo de linguagem (LLM). Cada usuário envia ao modelo apenas os próprios dados, minimizados. O endpoint é `POST /api/ai/weekly-plan`.

Fluxo de uma chamada: módulo ligado? → cota diária → contexto minimizado → (sem dados: devolve plano vazio, sem chamar o modelo) → chamada ao modelo → validação da saída → troca dos índices curtos pelos ids reais → registro da chamada.

## Decisões

### 1. Entregar primeiro o plano semanal e deixar o assistente com ferramentas para depois

- **Decisão:** primeiro construo o plano semanal, que é uma chamada simples ao modelo. Um assistente capaz de consultar os dados sob demanda (o chamado *tool calling*) fica para uma etapa posterior.
- **Por quê:** o assistente depende de coisas que o plano semanal já exige: a camada de acesso ao modelo, o controle de uso por usuário e os testes. Construí-lo antes seria empilhar complexidade sobre uma base ainda não validada. Como o projeto é desenvolvido sozinho, no meu tempo livre, prefiro ter algo pequeno funcionando de ponta a ponta antes de ampliar o escopo.
- **O que isso custa:** durante um tempo o módulo terá menos recursos. Se o tempo faltar, o assistente pode nem chegar a existir.
- **Quando reavaliar:** quando o plano semanal estiver funcionando em produção e os testes estiverem passando no CI.

### 2. Não guardar o texto dos prompts no banco de dados

- **Decisão:** o registro de cada chamada ao modelo não vai conter o texto enviado nem a resposta recebida. Vai guardar apenas quem fez a chamada, quando, qual recurso, qual modelo foi usado, quantos tokens foram consumidos, quanto tempo levou e como terminou (`SUCCESS`, `PROVIDER_ERROR`, `INVALID_OUTPUT` ou `TIMEOUT`).
- **Por quê:** o texto enviado ao modelo contém tarefas, hábitos e metas do usuário, que são dados pessoais. Guardar uma cópia criaria um segundo lugar com essas informações só para facilitar a depuração, o que vai contra o princípio de minimização de dados da Lei Geral de Proteção de Dados (LGPD). Para limitar o uso por usuário e acompanhar custo e desempenho, os metadados bastam.
- **O que isso custa:** quando uma resposta do modelo vier ruim, não será possível reabrir o texto exato que a gerou. Será preciso reproduzir o caso com dados de teste.
- **Quando reavaliar:** se a falta do texto atrapalhar a depuração de forma recorrente. Nesse caso, uma alternativa seria guardar uma versão reduzida e anonimizada, com prazo de retenção definido.

### 3. O modelo nunca vê nem devolve ids reais

- **Decisão:** cada item do contexto recebe um índice curto (`T1` para tarefas, `H1` para hábitos, `G1` para metas). O modelo responde com índices; o servidor guarda o mapa índice → id e faz a tradução depois de validar. Há dois DTOs: o que descreve a saída crua do modelo (`LlmWeeklyPlanDTO`, com índices) e o devolvido pela API (`WeeklyPlanResponseDTO`, com ids reais).
- **Por quê:** UUIDs não ajudam o modelo e são um identificador a menos fora da nossa infraestrutura. Além disso, validar "o índice existe no contexto que eu enviei" é a defesa mais simples contra alucinação: um índice inventado é rejeitado, e é impossível o modelo apontar para um item de outro usuário.
- **O que isso custa:** se o modelo citar um índice num texto livre, o servidor troca pelo título do item (o prompt também pede para usar títulos nos textos). Um índice citado de forma estranha pode sobrar no texto.

### 4. Rejeitar a resposta inteira quando há índice inventado

- **Decisão:** índice inexistente ou tipo incoerente com o índice faz a chamada falhar com 502 e ser registrada como `INVALID_OUTPUT`. Itens repetidos são apenas descartados.
- **Por quê:** descartar só o item ruim esconderia um defeito do prompt; a taxa de saídas inválidas é uma métrica de qualidade. O custo é que o usuário perde aquele plano e gasta uma unidade da cota.

### 5. Cota diária calculada pelo próprio log, no fuso do usuário

- **Decisão:** a cota por usuário é a contagem de chamadas do dia no log, onde "dia" é o dia no fuso do usuário. Contam `SUCCESS` e `INVALID_OUTPUT`; falhas do provedor e timeouts não consomem cota.
- **Por quê:** um contador separado poderia divergir do que de fato foi chamado, e o log já era necessário. Usar UTC faria a cota "virar" no meio da tarde para quem mora no Brasil.
- **O que isso custa:** a checagem e o registro não são atômicos; requisições simultâneas do mesmo usuário podem passar da cota em até o número de requisições paralelas. Como o provedor tem limite próprio e o custo é baixo, aceitei. Uma correção futura seria gravar uma linha de reserva antes da chamada. Também, durante uma queda do provedor, um cliente pode disparar várias tentativas sem gastar cota (cada uma limitada pelo retry).

### 6. Sem dados, não se chama o modelo

- **Decisão:** usuário sem tarefas em aberto, hábitos ativos nem metas em andamento recebe 200 com um plano vazio e uma mensagem; não há chamada ao provedor, linha de log nem consumo de cota.

### 7. Falhar fechado: o cliente de teste só existe se for pedido

- **Decisão:** `FakeLlmClient` só é criado com `lifesync.ai.provider=fake`; `GeminiLlmClient`, só com `gemini`. Sem provider, nenhum cliente existe e o endpoint responde 503. Em produção o módulo nasce desligado e sem provider.

### 8. Métricas só com números e enums

- **Decisão:** o `AiMetrics` (Micrometer) registra a duração da chamada ao provedor (`lifesync.ai.request.duration`), as chamadas por status (`lifesync.ai.requests`), os tokens de entrada e de saída (`lifesync.ai.tokens`) e os pedidos barrados antes do provedor, por módulo desligado ou cota excedida (`lifesync.ai.rejections`). As únicas tags são valores de enum e o nome do provedor.
- **Por quê:** a classe nem recebe o texto do prompt, a resposta, o id do usuário nem a chave, então eles não têm como chegar a uma métrica. Um teste percorre todas as métricas depois de chamadas com textos "secretos" e prova que nada disso aparece.
- **Consequências:** as métricas são lidas em `/actuator/metrics`, que exige token. Erro do provedor não informa tokens, então nesses casos os contadores de tokens não são incrementados (não se inventa valor). O id do usuário não é tag por causa da cardinalidade e da privacidade.

## Aprendizados

Registro do que deu errado ou surpreendeu durante a implementação e do que foi ajustado por causa disso.

1. **Um bean de teste sem condição vira o bean de produção.** O cliente de mentira estava marcado como `@Primary` sem condição, então em produção só ele responderia, e a resposta parece válida. Foi corrigido condicionando por propriedade e coberto com `ApplicationContextRunner`.
2. **O perfil de produção herda o arquivo base.** Os padrões de desenvolvimento (módulo ligado, provider `fake`) vazariam para produção se o `application-prod.yml` não os sobrescrevesse explicitamente. Agora ele os sobrescreve, e um teste carrega os arquivos reais para provar.
3. **Configuração com `null` só quebra na primeira chamada de um usuário.** A configuração passou a ter valores padrão e validação no construtor, de modo que configuração incoerente (módulo ligado sem chave, tempo limite zero) derruba a subida. A chave nunca aparece em `toString` nem em mensagens de erro.
4. **Documentação do provedor muda e as páginas redirecionam.** Ao conferir a API do Gemini, o campo documentado para o esquema de saída é `responseJsonSchema` (e não `responseSchema`), o modelo que seria o padrão (`gemini-2.5-flash`) está marcado como descontinuado, e as URLs de `ai.google.dev` redirecionam para `aistudio.google.com`. Os links dos ADRs trazem a data da verificação.
5. **Tokens de raciocínio são cobrados.** A resposta do Gemini traz `thoughtsTokenCount` separado de `candidatesTokenCount`; a contagem de tokens de saída do log soma os dois.
6. **A sequência (streak) guardada do hábito envelhece.** O valor só é atualizado no check-in, então um hábito parado há uma semana ainda mostraria a sequência antiga. Para o contexto do modelo, a sequência efetiva e o "em risco" são calculados na leitura, com as mesmas regras de período do módulo de hábitos (dia, semana começando na segunda, mês) e com o `Clock` injetado, sem alterar aquele módulo.
7. **Ordenar enum por texto no banco dá a ordem errada.** A prioridade da tarefa é um enum guardado como texto, então `ORDER BY` ordenaria alfabeticamente. O ranking das tarefas (atrasadas, prazo, prioridade) é feito em memória sobre um conjunto limitado.
8. **Spring Boot 4 e Jackson 3 mudaram nomes de pacotes e de métodos** usados nos testes (por exemplo `tools.jackson`, `asString()` no lugar de `asText()` e as anotações de teste de MVC em outro pacote). Custou tempo de compilação, não de design.
9. **Teste de timeout sem rede externa.** Um `ServerSocket` local que aceita a conexão e nunca responde prova que o timeout de leitura realmente vale.

## O que decidi não fazer (por enquanto)

- **Assistente com ferramentas (tool calling):** ver a decisão 1.
- **Guardar prompts e respostas:** ver a decisão 2.
- **Spring AI:** o cliente é próprio (ver o ADR 0001); a compatibilidade com o Spring Boot 4.1 foi verificada e não é o motivo.
- **Repetir a chamada quando a saída é inválida:** gastaria tokens e cota e esconderia a taxa de falha. A resposta inválida vira 502.
- **Cache de planos, resposta em fluxo (*streaming*) e escolha de modelo por usuário:** sem necessidade demonstrada.
- **Reserva atômica de cota:** ver a decisão 5.
- **Garantia de remoção de dados pessoais dos títulos:** o mascaramento é de melhor esforço (ver o modelo de ameaças).

## Pendências e o que ainda não foi verificado

- **Avaliação do plano gerado:** existem 10 casos e um teste que mede taxa de saídas válidas, latência e tokens (ver [avaliacao.md](avaliacao.md)), mas ele ainda não foi executado contra o provedor, então não há números. Nenhuma chamada ao provedor real foi feita; os testes automatizados usam apenas servidores simulados e o cliente de mentira, e o CI nunca chama um LLM.
- **Camada do provedor em produção:** decisão em aberto (ADR 0002, seção 6).
- **Limites numéricos da camada gratuita e retenção de logs na camada paga:** não verificados (ADR 0002).
- **Painel ou alerta sobre as métricas:** as métricas existem, mas não há exportador nem alerta configurados.
