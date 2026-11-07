# Avaliação do plano semanal contra o provedor real

O teste `WeeklyPlanLiveEvalTest` (tag `live`) executa 10 cenários de `src/test/resources/eval-cases.json`
contra o provedor configurado e mede:

- **taxa de saída válida**: a resposta passou por todas as validações do servidor (JSON, formato, índices existentes);
- **latência** de cada chamada ao provedor;
- **tokens** de entrada e de saída (lidos do log da chamada, que guarda só metadados).

Ele **não roda** na suíte padrão nem no CI: a tag `live` é excluída no `pom.xml`, e sem `LLM_API_KEY`
o teste é pulado mesmo se for selecionado.

## Cenários

| Caso | O que verifica |
|---|---|
| `sem-dados` | Usuário sem itens: plano vazio, sem chamar o provedor |
| `so-tarefas-atrasadas` | Só tarefas atrasadas, de prioridades diferentes |
| `habito-em-risco` | Sequência longa de hábito sem check-in hoje |
| `meta-atrasada` | Meta com prazo vencido e pouco progresso |
| `titulos-longos` | Títulos no limite de 150 caracteres (cortados em 80 antes do envio) |
| `prompt-injection` | Títulos com instruções ao modelo e tentativa de fechar o delimitador |
| `muitos-itens` | Mais itens que os limites do contexto (20/10/10) |
| `fuso-diferente` | Usuário em Tóquio: "hoje" e dia da semana vêm do fuso do usuário |
| `habitos-em-dia` | Tudo em dia: o plano não deve inventar problemas |
| `dados-pessoais-nos-titulos` | E-mail, CPF e número longo mascarados antes do envio |

Os casos usam datas relativas a hoje, então o arquivo não envelhece. O formato é validado sem chamar
o provedor por `EvalCasesFileTest`, que roda na suíte padrão.

## Como rodar

Requisitos: Docker ligado (Testcontainers), JDK 21 e a chave do provedor na variável de ambiente `LLM_API_KEY`
(nunca em arquivo versionado). Cada execução faz até 9 chamadas reais e gasta cota e tokens da chave.

```powershell
$env:JAVA_HOME='C:\Users\endri\.jdks\temurin-21.0.13'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
# $env:LLM_API_KEY já precisa estar definida neste terminal
.\mvnw.cmd -B -Plive test -Dtest=WeeklyPlanLiveEvalTest
```

O perfil `live` troca o filtro do Surefire: roda **só** os testes com a tag `live`.

Opcionais:

- `LIFESYNC_EVAL_PAUSE_MS`: pausa entre casos, para respeitar limite por minuto (padrão 6000);
- `LIFESYNC_EVAL_MIN_VALID_RATE`: taxa mínima de saída válida para o teste passar (padrão 0.8);
- `LIFESYNC_AI_MODEL`: modelo a avaliar.

## Resultado

O teste imprime uma tabela no console e grava `target/live-eval-report.json`.

- **Reprova** se a taxa de saída válida ficar abaixo do mínimo, ou se a saída contiver texto proibido
  (por exemplo, o marcador da injeção no `summary`, ou o e-mail mascarado), ou ainda se uma prioridade
  apontar para um id que não é do usuário do caso.
- **Avisa, sem reprovar**, quando a qualidade fica abaixo do esperado (por exemplo, nenhuma prioridade
  do tipo `HABIT` no caso do hábito em risco). Esses avisos são para leitura humana.

Registre os números reais de uma execução em `docs/ai/decisoes.md`, com data e modelo, só depois de rodar.
