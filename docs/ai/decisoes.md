# Módulo de IA: decisões de design

> **Status:** em desenvolvimento. Este documento registra as decisões tomadas no desenho do módulo e o motivo de cada uma, para que quem ler o código no futuro entenda por que as coisas são como são. Ele é atualizado conforme o módulo evolui.

## Contexto

O módulo de IA vai gerar um **plano semanal** para o usuário a partir das tarefas, hábitos e metas dele, usando um modelo de linguagem (LLM). Cada usuário envia ao modelo apenas os próprios dados.

## Decisões

### 1. Entregar primeiro o plano semanal e deixar o assistente com ferramentas para depois

- **Decisão:** primeiro construo o plano semanal, que é uma chamada simples ao modelo. Um assistente capaz de consultar os dados sob demanda (o chamado *tool calling*) fica para uma etapa posterior.
- **Por quê:** o assistente depende de coisas que o plano semanal já exige: a camada de acesso ao modelo, o controle de uso por usuário e os testes. Construí-lo antes seria empilhar complexidade sobre uma base ainda não validada. Como o projeto é desenvolvido sozinho, no meu tempo livre, prefiro ter algo pequeno funcionando de ponta a ponta antes de ampliar o escopo.
- **O que isso custa:** durante um tempo o módulo terá menos recursos. Se o tempo faltar, o assistente pode nem chegar a existir.
- **Quando reavaliar:** quando o plano semanal estiver funcionando em produção e os testes estiverem passando no CI.

### 2. Não guardar o texto dos prompts no banco de dados

- **Decisão:** o registro de cada chamada ao modelo não vai conter o texto enviado nem a resposta recebida. Vai guardar apenas quem fez a chamada, quando, qual modelo foi usado, quantos tokens foram consumidos, quanto tempo levou e como terminou (sucesso ou tipo de falha).
- **Por quê:** o texto enviado ao modelo contém tarefas, hábitos e metas do usuário, que são dados pessoais. Guardar uma cópia criaria um segundo lugar com essas informações só para facilitar a depuração, o que vai contra o princípio de minimização de dados da Lei Geral de Proteção de Dados (LGPD). Para limitar o uso por usuário e acompanhar custo e desempenho, os metadados bastam.
- **O que isso custa:** quando uma resposta do modelo vier ruim, não será possível reabrir o texto exato que a gerou. Será preciso reproduzir o caso com dados de teste.
- **Quando reavaliar:** se a falta do texto atrapalhar a depuração de forma recorrente. Nesse caso, uma alternativa seria guardar uma versão reduzida e anonimizada, com prazo de retenção definido.

## Aprendizados

_Ainda sem entradas. Esta seção vai registrar o que falhar ou precisar mudar durante o desenvolvimento, e o que foi ajustado por causa disso._
