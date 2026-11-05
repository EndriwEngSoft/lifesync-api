# Modelo de Ameaças: Módulo de IA (Plano Semanal)

| Ameaça / Risco | Impacto | Estratégia de Mitigação | Status |
| :--- | :--- | :--- | :--- |
| **Prompt Injection** | Manipulação do comportamento do LLM por inputs do usuário. | Uso de delimitadores XML (`<user_data>`) e regras explícitas no *System Prompt* isolando dados de instruções. | Planejado |
| **Vazamento de PII (LGPD)** | Exposição de dados sensíveis ao provedor de LLM. | Envio de DTOs minimizados (`UserContextDTO`). Exclusão de UUIDs, nomes, senhas e e-mails. Uso de *short indexes* (Ex: T1, H2). | Planejado |
| **Abuso de Custos (Rate Limit)** | Exaustão da cota do provedor de IA devido a chamadas em massa por um usuário mal-intencionado. | Controle de cota no banco de dados, limitando a X chamadas diárias por `userId` autenticado. | Planejado |
| **Saída Inválida / Alucinação** | LLM retorna texto livre em vez de JSON, quebrando o *parse* da API. | Solicitação explícita de JSON no prompt. Validação da resposta com `Bean Validation` e tratamento de erros de parsing. | Planejado |
| **Indisponibilidade do Provedor** | Falha em cascata, travando a thread da nossa API se a IA demorar a responder. | Configuração de timeouts de conexão e leitura no `RestClient`. Retorno de erro HTTP 502/503 claro ao cliente. | Planejado |