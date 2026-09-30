# SDD e Harness Engineering no DSCommerce

Este projeto serve ao aprendizado de desenvolvimento de software e à construção de um portfólio. O processo deve permitir que outra pessoa entenda o problema, acompanhe as decisões e examine as evidências do resultado.

SDD (Specification-Driven Development) organiza o desenvolvimento a partir de requisitos explícitos. Neste repositório, o Spec Kit estrutura especificação, planejamento e tarefas. A [constituição](../.specify/memory/constitution.md) governa as decisões: Java/Spring Boot, arquitetura em camadas, DTOs, persistência, validação e tratamento centralizado de erros.

O harness reúne as instruções, o contexto e os mecanismos de verificação que apoiam o trabalho do agente. Nesta primeira etapa, [AGENTS.md](../AGENTS.md) oferece uma entrada curta para encontrar as fontes e trabalhar dentro do escopo. Este guia explica o processo e suas evidências. Os testes existentes fornecem feedback executável; Maven Wrapper e CI permanecem pendentes.

## Das necessidades à revisão

O fluxo usado como referência é:

```text
requisito → plano → task → teste/código → evidência → revisão
```

| Etapa | Fonte ou resultado | Pergunta que deve responder |
| --- | --- | --- |
| Requisito | `specs/<feature>/spec.md` | Qual comportamento é necessário e como será aceito? |
| Plano | `plan.md`, `research.md`, `data-model.md` e `contracts/` | Como implementar respeitando a constituição e os contratos? |
| Task | `tasks.md` | Qual parte foi solicitada, de que depende e quais condições se aplicam? |
| Teste/código | `src/test/` e `src/main/` | O comportamento esperado é exercitado e implementado? |
| Evidência | Comando, revisão testada, resultado e relatórios | O que foi realmente verificado e com quais limites? |
| Revisão | Diff, arquivos novos e comparação com os artefatos | O escopo foi atendido sem alterações indevidas? |

Para uma mudança de comportamento, os testes devem representar os critérios de aceitação e as regressões afetadas. Quando aplicável, a falha inicial precisa ocorrer porque o comportamento esperado está ausente ou incorreto. Um teste que não compila não estabelece essa evidência. Após implementar, executam-se as verificações pertinentes e registra-se o resultado observado.

A feature [003-login-autenticacao](../specs/003-login-autenticacao/spec.md) exemplifica esse encadeamento: o requisito pede login e preservação das consultas públicas; o [plano](../specs/003-login-autenticacao/plan.md) define BCrypt, JWT e responsabilidades; o [contrato](../specs/003-login-autenticacao/contracts/login-openapi.yaml) define a API; as [tasks](../specs/003-login-autenticacao/tasks.md) incluem sequências de falha inicial, implementação e aprovação, além de regressões. O [quickstart](../specs/003-login-autenticacao/quickstart.md) registra resultados e procedimentos de validação.

As consultas de [catálogo](../specs/001-consulta-catalogo-produtos/spec.md) e [detalhes do produto](../specs/002-visualizar-detalhes-produto/spec.md) são fontes de requisitos de regressão. Uma alteração em autenticação precisa respeitar o comportamento público já definido.

Nesta etapa, o pedido abrange somente os dois documentos do harness. A validação adequada é revisar caminhos, comandos, consistência, conteúdo e diff. A suíte da aplicação não precisa ser repetida porque não houve mudança de código, configuração ou comportamento.

## Estados que precisam ser distinguidos

| Estado | Significado | Evidência necessária |
| --- | --- | --- |
| `Draft` | Metadado editorial da especificação. Pode estar desatualizado. | Leitura do documento; isoladamente, não indica entrega nem ausência de implementação. |
| Feature selecionada | Contexto escolhido para o Spec Kit na máquina. | `.specify/feature.json`, quando disponível, e eventuais overrides `SPECIFY_FEATURE` ou `SPECIFY_FEATURE_DIRECTORY`. |
| Implementação local | Código presente no checkout, possivelmente ainda sem commit ou sem integração. | Código, diff e verificações executadas para a revisão analisada. |
| Feature integrada à branch de destino | Alterações incorporadas à branch de destino, por exemplo `main`. | Histórico do Git e presença dos commits/código na branch de destino; isso não comprova deploy. |

No diagnóstico de 30/09/2026, a feature selecionada localmente era `003-login-autenticacao`, mas a branch real era `main`. O HEAD era `e3cd01c825ffb8eaecde18a82d3b0d18a81bbc20`, cujo histórico registra o merge da feature. A especificação ainda dizia `Draft`. A conclusão apoiada por essas fontes é: feature implementada e integrada à `main` local, ainda selecionada no Spec Kit; nenhuma dessas observações comprova deploy.

O [arquivo de ignore do Spec Kit](../.specify/.gitignore) define `feature.json` como estado local, não compartilhado. Um checkout novo pode não conter esse ponteiro. Além disso, [common.ps1](../.specify/scripts/powershell/common.ps1) pode preencher `CURRENT_BRANCH` com o nome do diretório da feature; esse valor aparece como `BRANCH` no script de pré-requisitos. Para saber a branch real, use `git branch --show-current`.

Uma checkbox é um registro de acompanhamento, não uma prova de execução. A T051 da feature 003, por exemplo, só se aplica se o teste T050 falhar. Sua caixa aberta não basta para concluir que existe comportamento faltante. Leia a condição e examine os resultados.

## Linha de base observada

O diagnóstico inicial executou a suíte completa em 30/09/2026, no checkout limpo de `main`, com este HEAD:

```text
e3cd01c825ffb8eaecde18a82d3b0d18a81bbc20
```

Comando executado na raiz do repositório:

```powershell
mvn test
```

Resultado observado no console:

```text
Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 01:39 min
Finished at: 2026-09-30T14:44:19-03:00
```

O ambiente identificado nesse diagnóstico foi Java Azul Zulu `21.0.12.1` e Maven `3.9.16`. O [pom.xml](../pom.xml) configura Java 21 e Spring Boot `3.3.4`; ele não fixa a versão do Maven.

As fontes disponíveis são:

- O [quickstart da feature 003](../specs/003-login-autenticacao/quickstart.md), versionado, registra `mvn test`, 79 testes e ausência de falhas, erros e testes ignorados.
- Os relatórios locais XML e TXT em `target/surefire-reports/` registram resultados por classe. Na preparação desta etapa documental, a leitura dos 15 relatórios XML confirmou novamente o agregado de 79 testes, 0 falhas, 0 erros e 0 ignorados, sem executar a suíte outra vez.
- O console da execução no diagnóstico registrou `BUILD SUCCESS`, duração e horário acima. Este guia preserva o resumo dessa observação.

Limites da evidência: `target/` é ignorado pelo [Git](../.gitignore), portanto os relatórios podem não existir em outro checkout ou podem ser substituídos por uma execução posterior. Nem esses relatórios nem o resumo do quickstart atestam, por si, o hash testado; a associação ao commit acima veio da conferência do HEAD e do Git limpo no diagnóstico. Não há log integral versionado dessa execução nem resultado de CI confirmado. O quickstart não identifica o commit exato de sua própria execução histórica. A referência local `origin/main` coincidia com o HEAD, mas não houve consulta remota para confirmar seu estado atual.

Assim, os 79 testes são uma linha de base observada nessa revisão, não uma garantia para alterações futuras ou uma medida de cobertura integral dos requisitos. Nesta etapa documental, não foi feita nova execução de `mvn test`.

## Instruções e verificações têm papéis distintos

As instruções orientam escolhas do agente: quais fontes ler, qual escopo respeitar, como preservar alterações e quando considerar o trabalho concluído. O Codex usa o `AGENTS.md` da raiz como orientação de projeto e pode seguir seu link para este guia e para os artefatos da feature solicitada.

As verificações produzem evidências: Maven compila e executa testes; os testes comparam resultados com expectativas; `git diff --check` procura problemas de whitespace no diff. Essas ferramentas não substituem a revisão dos requisitos e do escopo. Uma regra escrita no `AGENTS.md` também não se torna automaticamente um bloqueio executável.

O [workflow existente do Spec Kit](../.specify/workflows/speckit/workflow.yml) organiza o ciclo SDD e revisões de spec e plan. Ele não é um workflow de CI que execute a suíte em pull requests. Neste estágio, o agente ou desenvolvedor ainda precisa chamar as verificações e registrar os resultados.

Uma entrega deve informar arquivos afetados, comandos realmente executados, resultados, limitações e estado do Git. Diferencie sempre evidência histórica, execução atual e procedimento ainda sugerido. Revise arquivos não rastreados diretamente: `git diff` e `git diff --check` comuns não incluem esses arquivos.

## Próximas etapas — pendentes

| Etapa | Finalidade | Situação nesta fundação |
| --- | --- | --- |
| Maven Wrapper | Fixar a distribuição Maven e oferecer entradas para Windows e Linux/macOS. | Pendente; `mvnw`, `mvnw.cmd` e `.mvn/` ainda não existem. O comando atual continua sendo `mvn test`. |
| README | Tornar o guia e os futuros comandos reproduzíveis fáceis de encontrar para quem avalia o portfólio. | Atualização pendente; o README existente foi somente consultado. |
| CI | Configurar Java 21, executar testes em PRs e disponibilizar relatórios associados à revisão executada. | Pendente; não existe workflow em `.github/workflows/`. |

Essas etapas exigem trabalho posterior no escopo correspondente. Esta primeira fundação cria apenas `AGENTS.md` e `docs/harness.md`, sem alterar aplicação, POM, especificações, constituição ou README.
