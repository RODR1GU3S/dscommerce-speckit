# SDD e Harness Engineering no DSCommerce

Este projeto serve ao aprendizado de desenvolvimento de software e à construção de um portfólio. O processo deve permitir que outra pessoa entenda o problema, acompanhe as decisões e examine as evidências do resultado.

SDD (Specification-Driven Development) organiza o desenvolvimento a partir de requisitos explícitos. Neste repositório, o Spec Kit estrutura especificação, planejamento e tarefas. A [constituição](../.specify/memory/constitution.md) governa as decisões: Java/Spring Boot, arquitetura em camadas, DTOs, persistência, validação e tratamento centralizado de erros.

O harness reúne as instruções, o contexto e os mecanismos de verificação que apoiam o trabalho do agente. [AGENTS.md](../AGENTS.md) oferece uma entrada curta para encontrar as fontes e trabalhar dentro do escopo. Este guia explica o processo e suas evidências. Os testes existentes fornecem feedback executável; a segunda etapa acrescenta o Maven Wrapper e documenta sua execução. A terceira acrescenta o workflow de CI e a primeira execução remota em Linux, registrada abaixo.

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

Na primeira etapa, o pedido abrangeu somente os dois documentos do harness. A validação consistiu em revisar caminhos, comandos, consistência, conteúdo e diff, sem repetir a suíte da aplicação. A segunda etapa muda a entrada de execução do build e, por isso, valida o Wrapper e executa a suíte existente pelo novo comando.

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

Limites da evidência: `target/` é ignorado pelo [Git](../.gitignore), portanto os relatórios podem não existir em outro checkout ou podem ser substituídos por uma execução posterior. Nem esses relatórios nem o resumo do quickstart atestam, por si, o hash testado; a associação ao commit acima veio da conferência do HEAD e do Git limpo no diagnóstico. Não há log integral versionado dessa execução; naquele diagnóstico, também não havia resultado de CI confirmado. O quickstart não identifica o commit exato de sua própria execução histórica. A referência local `origin/main` coincidia com o HEAD, mas não houve consulta remota naquele momento para confirmar seu estado atual.

Assim, os 79 testes são uma linha de base observada nessa revisão, não uma garantia para alterações futuras ou uma medida de cobertura integral dos requisitos. Na primeira etapa documental, não foi feita nova execução de `mvn test`. Os resultados novos da segunda etapa estão separados abaixo.

## Instruções e verificações têm papéis distintos

As instruções orientam escolhas do agente: quais fontes ler, qual escopo respeitar, como preservar alterações e quando considerar o trabalho concluído. O Codex usa o `AGENTS.md` da raiz como orientação de projeto e pode seguir seu link para este guia e para os artefatos da feature solicitada.

As verificações produzem evidências: Maven compila e executa testes; os testes comparam resultados com expectativas; `git diff --check` procura problemas de whitespace no diff. Essas ferramentas não substituem a revisão dos requisitos e do escopo. Uma regra escrita no `AGENTS.md` também não se torna automaticamente um bloqueio executável.

O [workflow existente do Spec Kit](../.specify/workflows/speckit/workflow.yml) organiza o ciclo SDD e revisões de spec e plan. O [workflow de CI](../.github/workflows/ci.yml), preparado na terceira etapa, define a execução automática da suíte nos eventos descritos abaixo. Sua presença local, isoladamente, não produz evidência remota: o agente ou desenvolvedor precisa revisar a configuração e registrar os resultados de uma execução real, como a primeira execução em Linux documentada neste guia.

Uma entrega deve informar arquivos afetados, comandos realmente executados, resultados, limitações e estado do Git. Diferencie sempre evidência histórica, execução atual e procedimento ainda sugerido. Revise arquivos não rastreados diretamente: `git diff` e `git diff --check` comuns não incluem esses arquivos.

## Segunda etapa: Maven Wrapper e comandos

A preparação confirmou checkout limpo em `chore/harness-foundation`, com HEAD `6580067d78aa86ff116d359490af91b4fa157d12`. `mvn --version` confirmou Maven `3.9.16` e Java Azul Zulu `21.0.12.1`, com código de saída 0. Essa versão do Maven foi mantida e sua distribuição foi confirmada no Maven Central e no servidor oficial Apache.

O [plugin oficial Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/wrapper-mojo.html), versão `3.3.4`, gerou os arquivos pelo tipo `only-script`. O comando executado foi:

```powershell
mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper `
  "-Dmaven=3.9.16" `
  "-Dtype=only-script" `
  "-DdistributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip" `
  "-DdistributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce"
```

O comando concluiu com `BUILD SUCCESS` e código de saída 0. Os scripts [mvnw](../mvnw) e [mvnw.cmd](../mvnw.cmd) não foram escritos ou adaptados manualmente: a comparação dos bytes com o ZIP oficial `maven-wrapper-distribution-3.3.4-only-script.zip` confirmou que foram preservados. O tipo `only-script` não adicionou JAR do Wrapper nem fonte Java ao projeto.

### Distribuição e procedência do checksum

As [propriedades do Wrapper](../.mvn/wrapper/maven-wrapper.properties) fixam a distribuição ZIP oficial do [Maven Central](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip). Para obter um SHA-256 verificável, foi baixado esse mesmo ZIP, de 9.395.475 bytes, e seu SHA-512 foi comparado com o [checksum publicado pela Apache](https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512). Somente após a igualdade foi calculado o SHA-256 com `Get-FileHash -Algorithm SHA256`.

```text
SHA-512 publicado e conferido:
ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3

SHA-256 calculado do ZIP conferido e configurado como distributionSha256Sum:
5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

O SHA-256 acima foi calculado localmente; não é apresentado como um valor publicado diretamente pela Apache. O ZIP e os hashes de conferência ficaram em `target/harness-validation/`, ignorado pelo Git. A configuração do Wrapper foi conferida contra esse arquivo. A [documentação Apache do Wrapper](https://maven.apache.org/tools/wrapper/) descreve a propriedade `distributionSha256Sum`.

### Como executar

Na raiz, em Windows/PowerShell:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd test
```

Em Linux/macOS, os comandos previstos são:

```sh
./mvnw --version
./mvnw test
```

O Wrapper lê `distributionUrl`, localiza o Maven correspondente no cache e, se necessário, baixa a distribuição, verifica o SHA-256 antes de extrair e chama o `mvn` dessa instalação com os argumentos recebidos. O cache padrão fica em `.m2/wrapper/dists` no diretório do usuário. Uma instalação já presente no cache é reutilizada; o script não recalcula o checksum a cada execução. `MAVEN_USER_HOME` pode mudar o cache e `MVNW_REPOURL` pode substituir a origem de download; nenhum dos dois estava definido na validação desta etapa.

O Wrapper seleciona o Maven, mas não instala nem fixa o JDK: Java 21 precisa estar instalado, com `JAVA_HOME` ou `PATH` configurado. No Windows, o script usa PowerShell para baixar e extrair. Em Linux/macOS, esta configuração com checksum do ZIP exige `unzip` e `sha256sum` ou `shasum`, além do JDK e rede inicial. Sem `unzip`, o script oficial tenta o TAR.GZ, cujo hash não corresponde ao SHA-256 do ZIP fixado aqui; por isso `unzip` é um requisito deste projeto.

Os finais de linha gerados foram preservados: LF em `mvnw` e CRLF em `mvnw.cmd`. No fechamento desta etapa, o arquivo [.gitattributes](../.gitattributes) fixa `/mvnw text eol=lf` e `/mvnw.cmd text eol=crlf`, mantendo esses finais de linha nos futuros checkouts. O índice Git usa LF para ambos os scripts.

Após o stage autorizado, `git update-index --chmod=+x -- mvnw` registra o script Unix como executável (`100755`); `mvnw.cmd` permanece com modo `100644`. Nesta máquina, `core.autocrlf=true` e `core.filemode=false`; os atributos e o modo registrado permitem compartilhar essas definições sem mudar a configuração local. As conferências do fechamento usam `git check-attr text eol`, `git ls-files --eol` e `git ls-files --stage` para revisar os atributos, os finais de linha no índice e no arquivo de trabalho e os modos dos scripts.

### Novos resultados no Windows — 30/09/2026

Esta validação foi feita no Windows, a partir do commit de preparação `6580067d78aa86ff116d359490af91b4fa157d12`, com os arquivos da segunda etapa ainda sem commit. Portanto, esse hash identifica a base, não uma revisão commitada que já contenha o Wrapper.

| Comando executado | Resultado observado | Código de saída |
| --- | --- | --- |
| `mvn --version` | Maven 3.9.16 local; Java Azul Zulu 21.0.12.1. | 0 |
| Comando de geração acima | Plugin 3.3.4, tipo `only-script`, Maven 3.9.16; `BUILD SUCCESS`. | 0 |
| `.\mvnw.cmd --version` | Maven 3.9.16 em `.m2/wrapper/dists`; Java 21.0.12.1, Azul, runtime `C:\Program Files\Zulu\zulu-21`. | 0 |
| `.\mvnw.cmd test` | 79 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`. | 0 |

A execução dos testes pelo Wrapper terminou em `2026-09-30T16:32:44-03:00`, com duração Maven de `48.398 s`. Esses resultados são novos e não substituem o diagnóstico histórico de 14:44. Logs de geração, versão e testes estão em `target/harness-validation/`; os relatórios atuais por classe estão em `target/surefire-reports/`. São evidências locais ignoradas pelo Git, que podem ser substituídas ou faltar em outro checkout.

A consulta inicial de rede no sandbox falhou com impossibilidade de conexão. A primeira tentativa de geração retornou código 1 ao resolver o parent Spring Boot, com `Permission denied: getsockopt`. A consulta e a geração foram repetidas com acesso autorizado e concluíram corretamente. Não houve alteração da aplicação para contornar esse bloqueio do ambiente.

Na segunda etapa, Linux/macOS recebeu somente revisão estática do script gerado, de sua origem e dos finais de linha; não houve execução nesses sistemas naquele momento. Os comandos de inicialização da aplicação documentados no README também não foram executados nesta etapa. A CI estava pendente; a validação remota posterior em Linux está registrada abaixo. macOS não foi executado.

## Terceira etapa: configuração e evidência remota de CI

A preparação confirmou Git limpo em `chore/harness-foundation`, com HEAD `6c7f04d053dcd0d0da339b9b6d7f7a26b8cbc3e1`, commit da segunda etapa. O [workflow](../.github/workflows/ci.yml) foi inicialmente criado no checkout, sem stage, commit ou publicação naquele momento. Seu fechamento foi commitado em `1d49b28d46d6a73cf6c39b1077574c1306dba7f2`. O push posterior disparou a primeira execução remota com sucesso, registrada nesta seção.

### Eventos e execução

| Evento | Quando se aplica |
| --- | --- |
| `pull_request` | PRs cuja branch de destino é `main`; o filtro não se refere à branch de origem. |
| `push` | Pushes em `main` ou `chore/harness-foundation`. |
| `workflow_dispatch` | Execução manual, disponível quando o workflow estiver na branch padrão, conforme a [documentação GitHub](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onworkflow_dispatch). |

O workflow se chama `CI - Testes Maven`; o job `tests` aparece como `Testes (Java 21 / Maven)`. Ele usa `ubuntu-24.04`, timeout de 20 minutos e `permissions: contents: read`. As etapas fazem checkout, configuram Java 21 Zulu e cache Maven, mostram `java --version` e `./mvnw --version` e executam na raiz:

```sh
./mvnw --batch-mode --no-transfer-progress test
```

O Wrapper continua selecionando Maven 3.9.16. Java é solicitado pela versão principal `21`; o patch efetivamente utilizado deverá ser observado no log da execução. O cache acelera a preparação, mas não substitui o resultado dos testes.

A etapa final envia `target/surefire-reports/` como artefato `surefire-reports`, com retenção de 14 dias. `if: ${{ always() }}` solicita o upload também após falhas; a falha da etapa de testes continua sendo falha do job mesmo se o upload tiver sucesso. Se não houver relatórios, `if-no-files-found: warn` emite um aviso, por exemplo após uma falha anterior à execução dos testes. Esses comportamentos seguem as [condições de execução](https://docs.github.com/en/actions/reference/workflows-and-actions/expressions#always) e os [inputs oficiais de upload](https://github.com/actions/upload-artifact/blob/043fb46d1a93c77aae656e7c1c64a875d1fc6a0a/action.yml).

### Actions e procedência dos SHAs

Em 30/09/2026, foram consultadas as releases estáveis dos repositórios oficiais. Para cada uma, o link do commit na página da release forneceu o SHA completo; em seguida, o `action.yml` desse SHA foi conferido. As referências fixadas no workflow são:

| Action | Release oficial | Commit fixado |
| --- | --- | --- |
| `actions/checkout` | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | [3d3c42e5aac5ba805825da76410c181273ba90b1](https://github.com/actions/checkout/commit/3d3c42e5aac5ba805825da76410c181273ba90b1) |
| `actions/setup-java` | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | [de7274f081f381c8f8158605e0321c36c376e2e6](https://github.com/actions/setup-java/commit/de7274f081f381c8f8158605e0321c36c376e2e6) |
| `actions/upload-artifact` | [v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1) | [043fb46d1a93c77aae656e7c1c64a875d1fc6a0a](https://github.com/actions/upload-artifact/commit/043fb46d1a93c77aae656e7c1c64a875d1fc6a0a) |

Os metadados de [checkout](https://github.com/actions/checkout/blob/3d3c42e5aac5ba805825da76410c181273ba90b1/action.yml), [setup-java](https://github.com/actions/setup-java/blob/de7274f081f381c8f8158605e0321c36c376e2e6/action.yml) e [upload-artifact](https://github.com/actions/upload-artifact/blob/043fb46d1a93c77aae656e7c1c64a875d1fc6a0a/action.yml) usam Node 24. O requisito mínimo indicado para esse runtime é Actions Runner `v2.327.1`, conforme o [README oficial](https://github.com/actions/setup-java/blob/de7274f081f381c8f8158605e0321c36c376e2e6/README.md). O runner selecionado é hospedado pelo GitHub; a [imagem Ubuntu 24.04](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md) documenta suas ferramentas. O cache e a distribuição Zulu são opções do setup-java; `path`, `if-no-files-found` e `retention-days` foram conferidos nos inputs do upload.

O SHA fixa o código da action consultada, enquanto o comentário identifica sua release. Isso não congela todos os componentes da imagem Ubuntu nem o patch do JDK. Uma atualização futura das actions precisa conferir novamente a release, seu commit e seus requisitos.

### Logs, relatórios e evidência

Abra a aba **Actions** do repositório, escolha `CI - Testes Maven` e a execução desejada. No job, consulte os logs das etapas, especialmente versões e testes. No resumo da execução, baixe o artefato `surefire-reports`, se tiver sido produzido. As fontes oficiais explicam como [consultar logs](https://docs.github.com/en/actions/how-tos/monitor-workflows/use-workflow-run-logs) e [baixar artefatos](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

Registre URL da execução, evento, revisão testada, versões observadas, resultado e relatórios. Em PRs, o checkout padrão testa a referência de merge preparada pelo GitHub, que pode diferir do HEAD local; confirme a revisão nos logs. Os 79 testes aprovados no Windows continuam como evidências locais históricas. Eles não demonstram sucesso em Linux ou no GitHub.

Configurar CI não torna seu check uma condição obrigatória de merge. Isso depende das regras do repositório, como [checks obrigatórios em proteção de branch](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging). Essas regras não foram configuradas ou verificadas nesta etapa.

### Validação local e limites

`actionlint` não foi encontrado no `PATH` com `Get-Command actionlint -ErrorAction SilentlyContinue`; por isso não foi executado. A sintaxe YAML foi lida com SnakeYAML 2.2 já presente no cache local, usando `SafeConstructor` e rejeição de chaves duplicadas, com código de saída 0. O comando foi:

```powershell
java --class-path C:/Users/ronal/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar target/harness-validation/ci-review/ReadWorkflowYaml.java .github/workflows/ci.yml
```

Esse comando é uma conferência local de sintaxe, feita no Windows com um auxiliar em `target/`, ignorado pelo Git; não é um comando necessário para executar o projeto. A estrutura foi revisada contra os requisitos: eventos, permissões, runner, timeout, referências oficiais, cache, comandos na raiz, condição de upload, caminho e retenção dos relatórios. Um parser YAML geral não substitui actionlint nem a execução no GitHub.

Na preparação local da terceira etapa, não foram repetidos os testes Windows ou a geração do Wrapper e ainda não havia execução de Maven em Linux nem execução de CI. Aplicação, POM, testes, Wrapper, especificações e constituição foram preservados. A preparação abrangeu somente o workflow e as partes pertinentes dos três documentos; publicação e validação remota ocorreram depois, em uma etapa autorizada separadamente.

### Primeira execução remota em Linux

Em 30/09/2026, o push de `chore/harness-foundation` publicou a fundação do harness e disparou a [execução 36776432450](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450), número 1, tentativa 1, do workflow `CI - Testes Maven`. A execução foi localizada já concluída; os metadados e os logs foram conferidos, sem atribuir a ela os resultados locais anteriores.

| Informação | Evidência observada |
| --- | --- |
| Evento e branch | `push` em `chore/harness-foundation`. |
| Commit testado | [`1d49b28d46d6a73cf6c39b1077574c1306dba7f2`](https://github.com/RODR1GU3S/dscommerce-speckit/commit/1d49b28d46d6a73cf6c39b1077574c1306dba7f2), confirmado nos metadados e no checkout dos logs. |
| Estado e conclusão | `completed` e `success`; checkout, preparação do Java, testes e upload concluídos com sucesso. |
| Ambiente | Runner hospedado pelo GitHub, imagem `ubuntu-24.04`, Linux `6.17.0-1022-azure`, arquitetura `amd64`. |
| Java efetivo | OpenJDK `21.0.12+8-LTS`, distribuição `Zulu21.52+15-CA`, fornecedor Azul Systems. |
| Maven efetivo | Apache Maven `3.9.16`, chamado pelo Wrapper de `.m2/wrapper/dists`. |
| Testes | 79 testes, 0 falhas, 0 erros e 0 ignorados; `BUILD SUCCESS`. |

Comando observado nos logs, executado na raiz no Linux:

```sh
./mvnw --batch-mode --no-transfer-progress test
```

O [artefato `surefire-reports`](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450/artifacts/11126240641), ID `11126240641`, foi enviado com sucesso: ZIP de 70.395 bytes, com 30 arquivos. No acompanhamento remoto desta sessão, o ZIP foi lido em memória e seus 15 XMLs e 15 TXTs foram examinados. O agregado dos XMLs e os resumos TXT confirmaram 79 testes, 0 falhas, 0 erros e 0 ignorados; os XMLs também registraram Java `21.0.12` e sistema `Linux`.

O SHA-256 calculado sobre o ZIP lido correspondeu ao digest do upload e dos metadados do artefato:

```text
53c6f0220dcd64943a2ae556ac47d540950dc05fd591e13733178121eeb2807e
```

A retenção configurada é de 14 dias. Os metadados consultados informaram `expired: false` e expiração em **14/10/2026 às 21:00:06 UTC** (`2026-10-14T21:00:06Z`). O resumo fica versionado neste guia, mas a disponibilidade do ZIP é limitada por essa retenção; o sucesso do upload, isoladamente, não seria prova de exame de seu conteúdo.

Fontes consultadas para este registro:

- [Metadados da execução na API GitHub](https://api.github.com/repos/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450): workflow, evento, branch, SHA, número, tentativa, estado e conclusão.
- [Logs do job `Testes (Java 21 / Maven)`](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450/job/110095399519): checkout, runner, versões, comando, resumo dos testes e upload.
- [Metadados dos artefatos da execução na API GitHub](https://api.github.com/repos/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450/artifacts): associação à execução e ao commit, tamanho, digest e expiração do artefato `11126240641`; o ZIP acessível pelo link do artefato forneceu os relatórios examinados.

Na preparação desta atualização documental, os metadados e os logs da execução foram conferidos novamente. Nenhum teste Windows foi repetido. Esta é uma evidência remota separada do diagnóstico e da validação Windows do Wrapper. Ela demonstra o resultado da suíte existente para o commit acima; não demonstra cobertura integral dos requisitos nem aprova revisões posteriores. macOS e os comandos de inicialização da aplicação continuam sem execução nesta validação do harness. Checks obrigatórios para merge não foram configurados nesta entrega.

## Etapas do harness

| Etapa | Finalidade | Situação |
| --- | --- | --- |
| Documentos de orientação | Explicar fontes, fluxo SDD, escopo e critérios de conclusão. | Primeira etapa commitada em `6580067d78aa86ff116d359490af91b4fa157d12`. |
| Maven Wrapper | Fixar Maven 3.9.16 e oferecer entradas para Windows e Linux/macOS. | Segunda etapa commitada em `6c7f04d053dcd0d0da339b9b6d7f7a26b8cbc3e1`, com atributos de finais de linha e permissão executável no Git; testes observados no Windows. |
| README | Tornar o guia e os comandos reproduzíveis fáceis de encontrar para quem avalia o portfólio. | Comandos e explicação da CI commitados; primeira evidência Linux vinculada nesta atualização documental. |
| CI | Configurar Java 21, executar testes em PRs e disponibilizar relatórios associados à revisão executada. | Workflow commitado em `1d49b28d46d6a73cf6c39b1077574c1306dba7f2`; [primeira execução remota em Linux](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/36776432450) concluída com sucesso e relatórios examinados. |

A geração e a validação da segunda etapa afetaram somente os três arquivos do Wrapper e as partes pertinentes de `AGENTS.md`, `docs/harness.md` e `README.md`, sem stage ou commit naquele momento. O fechamento autorizado acrescentou `.gitattributes` e versionou somente esses sete arquivos, com a mensagem `build: add Maven Wrapper and reproducible commands`, sem repetir a geração ou os testes já aprovados. Na preparação local da terceira etapa, foram alterados somente `.github/workflows/ci.yml`, `AGENTS.md`, `README.md` e `docs/harness.md`, ainda sem stage, commit, push, PR, merge ou deploy. O fechamento posterior foi commitado com a mensagem `ci: run Maven tests and retain Surefire reports`; a primeira execução remota após o push está registrada acima.
