# Quickstart: Registrar pedido

Este guia descreve **validação futura após implementação**. POST /orders, entidades, perfis e classes de teste aqui planejados ainda não existem. Comandos e resultados esperados abaixo não foram executados neste checkpoint. [Contrato](contracts/README.md), [OpenAPI](contracts/orders-openapi.yaml), [modelo](data-model.md) e [plano](plan.md) são as referências.

## Pré-requisitos futuros

- Java 21 instalado; Maven 3.9.16 selecionado pelo Wrapper. Executar na raiz do checkout que contenha a implementação futura.
- Starter Resource Server, schema e testes descritos no plano implementados; banco H2 isolado para cenários de pedidos e mutações de preço.
- Conta demo/secret123 já pertence ao seed de teste. Preparar explicitamente seu perfil CLIENT para demonstração e contas de teste ADMIN isolado, ambos e sem perfis. Não criar contas/perfis em recursos de produção nem conceder Cliente a todas as contas automaticamente.
- Produtos existentes positivos. O exemplo aritmético usa fixture isolada com produtos 1 e 2 a 10.00 e 7.50; esses valores não são os da seed atual. Não alterar os 52 produtos compartilhados para fazer os exemplos coincidirem. Usar os valores retornados pelo catálogo na demonstração comum.
- Nenhuma operação HTTP prepara dados ou atribui perfis; isso pertence a fixtures/test resources de implementação. H2 é efêmero; reiniciar contexto pode apagar pedidos. Não usar reinício como prova de rollback.

## Comandos automatizados previstos

Na raiz, Windows/PowerShell, depois de as classes de teste serem implementadas:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd '-Dtest=OrderServiceTest,OrderControllerTest,OrderRepositoryTest,OrderSecurityIntegrationTest,OrderRegistrationIntegrationTest,OrderRollbackIntegrationTest' test
.\mvnw.cmd '-Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest,LoginControllerTest,GlobalExceptionHandlerRegressionTest,JwtConfigurationTest,JwtTokenServiceTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,UserAccountRepositoryTest,PasswordEncoderConfigurationTest,H2ConsoleSecurityIntegrationTest' test
.\mvnw.cmd test
```

Em Linux, suíte completa prevista: `./mvnw --batch-mode --no-transfer-progress test`, com requisitos do [harness](../../docs/harness.md). Estes comandos não alegam sucesso ou existência atual das classes novas. Na execução futura, registrar comando, HEAD/diff, versões, resultado e relatórios de target/surefire-reports; total de testes não é medida de cobertura integral. Não substituir resultados históricos de `mvn test` por supostas execuções pelo Wrapper.

## Demonstração HTTP prevista

Depois da implementação e preparação explícita de perfis/dados de teste, iniciar com classpath de teste, para carregar application-test.properties/import.sql. Perfil fornece chave descartável; não copiar chave para produção. Comando previsto, ainda não executado:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.profiles=test'
```

Em outro PowerShell, com a aplicação futura rodando:

```powershell
$orderBaseUrl = 'http://localhost:8080'
$orderLoginBody = @{ name = 'demo'; password = 'secret123' } | ConvertTo-Json
$orderLogin = Invoke-RestMethod -Method Post -Uri "$orderBaseUrl/login" -ContentType 'application/json' -Body $orderLoginBody
$orderHeaders = @{ Authorization = "Bearer $($orderLogin.accessToken)" }
$orderBody = @{ items = @(@{ productId = 1; quantity = 2 }, @{ productId = 2; quantity = 3 }) } | ConvertTo-Json -Depth 4
$orderResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$orderBaseUrl/orders" -Headers $orderHeaders -ContentType 'application/json' -Body $orderBody
$orderResponse.StatusCode
$orderResponse.Content | ConvertFrom-Json
```

Esperado: login 200 com exatamente accessToken/tokenType Bearer; registro 201 com conta de demo, id próprio, createdAt UTC, WAITING_PAYMENT, dois itens e valores históricos. Com a fixture de 10.00/7.50, subtotais 20.00/22.50 e total 42.50 como strings. Não imprimir token, senha ou segredo nos registros de evidência.

Novo envio válido de `$orderBody` deve produzir outro id. Para simular resposta perdida, executar primeiro POST e descartar seu conteúdo; repetir e verificar pelo banco de teste que existem dois pedidos completos, preservando o primeiro. Não usar GET /orders; não existe consulta entregue. A interface futura cuidará do carrinho.

Consultas públicas previstas continuam sem token:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "$orderBaseUrl/products?page=0&size=12"
Invoke-WebRequest -UseBasicParsing -Uri "$orderBaseUrl/products/1"
```

Esperado: mesmos dados/status/ordenação/paginação/categorias das features 001/002. Testes devem repetir também com Authorization inválido irrelevante nessas rotas e no login, sem ativar a chain de pedidos.

## Matriz de validação futura

| Caso | Resultado esperado | Prova adicional |
| --- | --- | --- |
| Cliente; Admin sem Cliente; ambos | 201 para a conta autenticada | identidade/FK e linhas completas confirmadas no banco |
| sem token; adulterado/malformado/chave ou alg errado | 401 genérico, challenge Bearer | filtro/decoder reais; nenhuma escrita |
| expirado há poucos segundos, exp/iat/sub ausentes, sub inválido/conta ausente, iat/nbf futuros | 401 | claims realmente assinadas; sem tolerância de expiração |
| conta sem perfis, mesmo com roles ADMIN no JWT | 403 | authorities derivadas do banco; login continua 200 |
| banco falha durante resolução da conta | 500 seguro | não converter em 401; registro não executado |
| corpo malformado/inválido com token ausente ou inválido | 401, antes da leitura do corpo | repetir as combinações com JSON ilegível e itens inválidos; nenhum serviço de pedidos/escrita |
| corpo malformado/inválido com conta existente sem CLIENT/ADMIN | 403, antes da leitura do corpo | token válido, perfis reais do banco; nenhum serviço de pedidos/escrita |
| corpo malformado/inválido e falha interna na resolução da conta | 500 seguro, antes da leitura do corpo | não substituir falha de segurança por 400/401; nenhum processamento de pedido |
| conta autorizada, entrada inválida e produto inexistente/repetido | 400, antes das regras de negócio | não consultar produtos nem salvar; segurança já aprovada |
| conta removida depois da resolução no filtro e antes da revalidação no serviço | 401 pelo tratamento MVC | mudança confirmada em transação separada; principal inicialmente válido, nenhuma linha nova |
| CLIENT/ADMIN revogados depois da resolução no filtro e antes da revalidação no serviço | 403 pelo tratamento MVC | conta ainda existe; remover ambos se presentes, confirmar mudança e provar guarda tardia sem escrita |
| carrinho vazio/nulo/linha nula/campos ausentes | 400 | sem serviço/escrita na falha de entrada |
| quantity 0/-1/1.5/1.0/string/bool ou >2147483647 | 400 | não truncar/coagir; testar raw JSON, não DTO já tipado |
| productId ausente/null/0/-1, >9223372036854775807 ou token 1.5/1.0/1e0/string/bool/array/objeto | 400 | testar cada caso em raw JSON com quantity válida; somente token inteiro positivo Long, sem coerção |
| campos extras, titular/preços fornecidos, propriedades JSON repetidas | 400 | root e linhas, inclusive valores nulos |
| produto inexistente entre válidos | 404 | nenhum pedido/item novo |
| produto repetido; preço nulo/zero/negativo/indisponível entre positivos | 422 | não consolidar nem gravar válidos separadamente; preço nulo usa fixture de serviço, sem violar NOT NULL de Product |
| centavo mínimo/teto e quantity máxima com cálculo representável | 201 | decimal e persistência exatos |
| overflow subtotal ou soma; fração monetária não representável | 422 | sem arredondar/gravar; usar unidade para valor além do schema Product |
| mudança positiva antes; alteração depois da compra | preço lido no registro; histórico conservado | verificar snapshot e totais por banco, sem novo GET |
| dois envios iguais, inclusive primeira resposta descartada | dois pedidos com ids diferentes | primeiro permanece; não exigir sequência contínua |
| context path /api | mesmos status/corpos | matcher lógico /orders e path externo /api/orders |

Precedência planejada: segurança (401/403/500 de resolução), leitura/Bean Validation (400), regras de negócio (404/422) e persistência/conclusão transacional (500). Para múltiplos erros de negócio, não exigir uma ordem entre mensagens 404/422; toda rejeição impede a gravação. Guardas tardias são verificações adicionais dentro da transação do serviço, após a aprovação inicial do filtro, e também devem ser exercitadas em chamada direta ao serviço via proxy. Na integração HTTP, coordenar a mudança da conta/perfis com instrumentação exclusiva de teste entre a resolução e a revalidação, mantendo filtros e serviço reais; conferir envelope, challenge Bearer no 401 e contagens após o retorno. Não simular essas guardas apenas com token rejeitado pelo filtro.

## Prova obrigatória de rollback no banco

1. Usar OrderRollbackIntegrationTest full-context, filtros ativos e serviço Spring real/proxy; **sem @Transactional na classe/método de teste**, inclusive ao usar MockMvc síncrono. Não desabilitar segurança, não mockar save e não usar rollback automático do teste como prova.
2. Em transação separada, preparar e confirmar conta/perfis/produtos; capturar baseline de tb_order/tb_order_item. Usar banco/contexto dedicados para não contaminar seed pública.
3. Provocar falha SQL tardia mediante constraint temporária restrita ao segundo produto no banco de teste. Observar inserts reais do pedido e primeiro item antes da falha. Não aceitar falha de startup/validação antes de gravar como evidência de rollback.
4. Após retorno/saída do proxy, abrir leitura JDBC independente e comparar baseline: nenhum pedido ou item daquela tentativa permanece; HTTP 500 sem 201. Remover constraint e limpar fixtures explicitamente depois das assertivas, em outra transação.
5. Complementar com falha de processamento após saveAndFlush real, **ainda dentro do corpo do método transacional**: spy somente de teste delega persistência, confirma rows na mesma transação e então lança RuntimeException. Leitura posterior deve provar desfazimento e HTTP 500 seguro, sem 201. Esse cenário não exercita a conclusão da transação pelo interceptor.
6. Em cenário separado, provocar **falha na conclusão da transação antes da confirmação do commit**: instrumentação exclusiva de teste delega saveAndFlush real e registra `TransactionSynchronization` na transação ativa via `TransactionSynchronizationManager`. Deixar o corpo do serviço concluir e mapear normalmente o DTO; no callback `beforeCommit`, confirmar rows na mesma transação, registrar que o callback foi alcançado e lançar RuntimeException. A exceção ocorre na fase de conclusão conduzida pelo interceptor, antes do commit físico, e deve escapar do proxy até o tratamento MVC: HTTP 500 seguro, sem 201. Após a saída excepcional, leitura JDBC independente deve encontrar exatamente o baseline, sem pedido/itens da tentativa. Não lançar em `afterCommit` nem apenas no spy após flush: esses pontos não demonstram esta falha anterior ao commit. Isso prova rollback nessa fase de conclusão, sem alegar simulação de perda de conexão durante o commit físico.
7. Demonstrar controle positivo: sucesso deixa pedido/itens visíveis em conexão independente depois do retorno. Flush não é commit; avanço de IDENTITY após rollback é permitido. Falha de transporte depois do commit preserva o pedido e pertence ao cenário de resposta perdida/Q1, não às provas de rollback acima.

Para preços negativos, fixture JDBC em banco isolado evita que @PositiveOrZero rejeite a preparação antes de testar a compra. Não mudar Product nem desligar suas validações. Para overflow de escala não persistível em Product, testar regra de serviço com BigDecimal de fixture sem alegar que essa preparação existe no banco atual.

Preço nulo será preparado em OrderServiceTest com produto retornado pelo acesso de teste contendo `price = null`, entre produtos positivos. Esperar rejeição 422 (preço inválido/indisponível) e nenhuma chamada de gravação, sem NullPointerException convertida em 500. A coluna atual de Product é NOT NULL: não remover essa constraint nem afirmar que a fixture nula foi persistida. As provas de rollback usam inserts reais nos cenários separados acima.

## Evidências históricas e verificações deste planejamento

O [harness](../../docs/harness.md) registra 79 testes históricos Windows e a execução Linux do commit 1d49b28, com relatórios examinados naquela etapa. Esses resultados não foram repetidos agora e não aprovam Feature 004.

Nesta etapa: leitura das fontes/código e verificação de raiz/branch/HEAD/status/feature selecionada; setup-plan.ps1 -Json executado com saída para esta feature; research e desenho gerados. O checkout inicial estava limpo em `ed640d710e6ee4c3f1ad6beaef8c3006be8e7417`. Nenhum teste de aplicação, chamada HTTP, criação de schema ou prova de rollback foi executado neste planejamento.

Verificações documentais executadas em 2026-10-01:

- Revisão integral dos seis artefatos novos, incluindo conteúdo não rastreado, critérios FR-001–017/SC-001–009 e cobertura planejada dos 27 cenários. Gates constitucionais pré/pós-desenho atendidos como revisão documental.
- 35 links locais conferidos e existentes; ausência de placeholders do template e marcadores de clarificação nos documentos gerados. Q1, A1, A2/A3, rejeição de preço zero/negativo e fronteiras de escopo harmonizadas.
- `git diff --check` sem erros; cada arquivo novo também conferido com `git diff --no-index --check -- /dev/null <arquivo>`, além da revisão de newline e espaços finais. Apenas aviso local de futura conversão LF/CRLF, sem diagnóstico de whitespace.
- Sintaxe do contrato lida com SnakeYAML 2.2, SafeConstructor e rejeição de chaves duplicadas, código de saída 0. Foram conferidas 19 referências internas de schema e as respostas 201/400/401/403/404/422/500. Isso não equivale a validação OpenAPI completa ou execução HTTP.
- `git diff --exit-code` confirmou ausência de alterações em arquivos rastreados, incluindo spec/checklist commitados, código, POM e fontes das features anteriores. tasks.md da Feature 004 permanece ausente.
- `.specify/extensions.yml` ausente nas conferências anterior e posterior à geração: nenhum hook antes/depois de plan foi executado. Não houve stage, commit, push, merge ou mudança de branch.

Comando efetivamente usado para a conferência YAML, reutilizando auxiliar local já existente em target (não criado neste planejamento):

```powershell
java --class-path C:/Users/ronal/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar C:/Users/ronal/dscommerce-speckit/target/harness-validation/ci-review/ReadWorkflowYaml.java C:/Users/ronal/dscommerce-speckit/specs/004-registrar-pedido/contracts/orders-openapi.yaml
```

O auxiliar é ignorado pelo Git e não é requisito para rodar a aplicação ou os testes futuros. Estado de entrega: branch `004-registrar-pedido`, HEAD preservado, somente seis documentos de planejamento novos/não rastreados. A aprovação funcional continua dependendo da implementação e das execuções futuras descritas neste guia.

## Fechamento da revisão documental — 2026-10-02

Este registro complementa a etapa de 2026-10-01 acima, cujo texto e evidências históricas foram preservados. A revisão foi retomada após interrupção, mantendo as alterações já presentes. Não houve nova execução de setup-plan, geração de tasks ou implementação.

Verificações realmente executadas nesta revisão:

- `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ed640d710e6ee4c3f1ad6beaef8c3006be8e7417`. No início e na retomada, os seis artefatos de planejamento já estavam não rastreados; o checkout não estava limpo. O ponteiro local seleciona a Feature 004.
- Leitura dos artefatos e revisão dos diffs por `git diff --no-index -- <cópia anterior> <arquivo>`, usando cópias locais ignoradas em `target/harness-validation/feature-004-doc-review-2026-10-02/`. Ajustes restritos a quickstart.md, data-model.md, orders-openapi.yaml e harmonização necessária em plan.md/research.md; contracts/README.md permaneceu idêntico à cópia anterior.
- Conferência ponto a ponto: quickstart explicita precedência, guardas tardias, preço nulo e tokens incompatíveis de productId, além de separar falha de processamento após saveAndFlush da falha em beforeCommit na conclusão pelo interceptor. O modelo define CHECKs nomeados via @Check, NOT NULL em user_account_id/role e proteção da coleção não vazia no agregado. OpenAPI explicita token JSON inteiro positivo Long para productId de entrada, sem coerção, distinguindo a restrição de token do schema integer.
- 39 links locais conferidos por resolução de caminhos e `Test-Path`, todos existentes; 19 referências internas de schema conferidas no YAML, todas com destino presente. Não foi feita validação integral OpenAPI.
- O comando Java/SnakeYAML registrado na seção anterior foi executado novamente, com saída em `target/harness-validation/feature-004-doc-review-2026-10-02/yaml-review.txt`: código 0, parsing concluído com SafeConstructor e rejeição de chaves duplicadas. Essa é uma conferência de sintaxe, não execução HTTP.
- `git diff --check` e `git diff --exit-code`: código 0, sem problemas de whitespace ou alterações rastreadas. Os seis artefatos não rastreados também foram conferidos por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnósticos de whitespace, e por leitura direta para espaços finais, espaço antes de tab e newline final. O código 1 de no-index indica diferença em relação a /dev/null; a primeira conferência foi interrompida por interpretá-lo como erro, e a conferência corrigida passou para os seis arquivos. Avisos anteriores de conversão LF/CRLF não foram tratados como falha de conteúdo.
- `Get-FileHash -Algorithm SHA256` antes/depois confirmou spec.md e checklists/requirements.md preservados. A comparação com a cópia anterior confirmou o bloco histórico deste quickstart intacto. `Test-Path specs/004-registrar-pedido/tasks.md` retornou False. Código, POM e contratos anteriores permanecem sem alterações rastreadas; nenhum arquivo foi incluído no stage.
- Conferência local do BOM Boot 3.3.4 identificou Hibernate 6.5.3.Final; a API oficial Hibernate 6.5 foi consultada para @Check. `javap -classpath C:/Users/ronal/.m2/repository/org/hibernate/orm/hibernate-core/6.5.3.Final/hibernate-core-6.5.3.Final.jar org.hibernate.annotations.Check` terminou inicialmente com AccessDeniedException no sandbox e, repetido com permissão, concluiu com código 0 e confirmou name/constraints. A página Spring 6.1.13 de TransactionSynchronization não ficou acessível pela ferramenta web; a API corrente foi consultada como apoio, com essa distinção registrada em research.md.

Limites do fechamento: nenhum teste de aplicação, chamada HTTP, geração de schema ou prova de rollback foi executado; todos os cenários funcionais continuam previstos para a implementação futura. Não foram identificadas inconsistências documentais restantes no escopo solicitado. Permanecem a validação executável do desenho e a validação OpenAPI completa, sem alegação de aprovação nesta revisão. Estado final: mesma branch/HEAD, seis artefatos de planejamento não rastreados, nenhum arquivo rastreado alterado; sem tasks.md, stage, commit, push, merge ou mudança de branch.

## Execução limitada de T001/T002 — 2026-10-02

Este registro é posterior aos checkpoints de planejamento acima, cujas evidências e datas permanecem preservadas. A autorização desta execução abrange somente a conferência da base (T001) e a linha de base de testes existentes (T002), com parada antes de T003. Não representa implementação ou aprovação funcional do registro de pedidos. U1 (conteúdo residual no JSON) e U2 (ativação de fixtures na demonstração), apresentados antes da execução, continuam sugestões não aplicadas.

### T001 — base e escopo conferidos

Em 2026-10-02, às 19:30:54 -03:00, a conferência inicial por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all` identificou raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `4afb5e7634ce5236df2aeb2ba57dc228578be292` e checkout limpo, sem alterações preexistentes a preservar. `.specify/feature.json` seleciona esta feature; a branch real foi confirmada separadamente pelo Git.

Executado `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com código 0: FEATURE_DIR absoluto da Feature 004 e research.md, data-model.md, contracts/, quickstart.md e tasks.md disponíveis. Spec, plano, pesquisa, modelo, quickstart, os dois arquivos de contratos e tarefas foram conferidos com a constituição 1.0.0 e as orientações do harness. O checklist `checklists/requirements.md` tem 16 itens marcados e nenhum aberto; permaneceu sem edição. `.specify/extensions.yml` estava ausente. `.gitignore` já cobre target e os artefatos Java; nenhuma configuração foi alterada.

Escopo preservado: compra para a própria conta por CLIENT ou ADMIN, Q1 com novo pedido por envio, A1 com rejeição de duplicatas e preço estritamente positivo aprovados; A2/A3 permanecem assunções adotadas. Sem pagamentos, consulta de pedidos, carrinho no backend, CRUD/provisionamento real de perfis, alterações em Product ou nas features anteriores. T001 cumprida; nenhuma tarefa a partir de T003 foi iniciada.

### T002 — versões e testes existentes executados

Todas as execuções abaixo ocorreram na raiz, em Windows/PowerShell, com HEAD `4afb5e7634ce5236df2aeb2ba57dc228578be292`. Ao iniciar T002, o diff para esse HEAD continha somente o registro de T001 neste quickstart e sua checkbox em tasks.md; código, POM, configurações e testes estavam idênticos à revisão commitada. O índice permaneceu vazio. Os horários abaixo são de 2026-10-02, com offset -03:00.

| Execução / comando | Início → fim | Resultado observado | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version`, no sandbox | 19:32:08 → 19:32:12 | Bootstrap PowerShell falhou com `NullArray` / “Não é possível indexar em uma matriz nula” e `Cannot start maven from wrapper`; Maven não iniciou | 1 |
| `.\mvnw.cmd --version`, repetição autorizada fora do sandbox | 19:38:19 → 19:38:30 | Apache Maven 3.9.16 pelo Wrapper; Java 21.0.12.1, Azul Systems, Inc.; runtime `C:\Program Files\Zulu\zulu-21`; Windows 10, amd64 | 0 |
| Regressões selecionadas pelo comando abaixo, fora do sandbox com autorização | 19:39:38 → 19:41:21 | 79 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`; duração Maven 01:36 min | 0 |
| `.\mvnw.cmd test`, fora do sandbox com autorização | 19:41:22 → 19:42:22 | 79 testes, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`; duração Maven 48.695 s | 0 |

Comando efetivamente executado para as regressões existentes listadas na seção de comandos previstos:

```powershell
.\mvnw.cmd '-Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest,LoginControllerTest,GlobalExceptionHandlerRegressionTest,JwtConfigurationTest,JwtTokenServiceTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,UserAccountRepositoryTest,PasswordEncoderConfigurationTest,H2ConsoleSecurityIntegrationTest' test
```

A versão do Maven foi selecionada pela distribuição fixada em `.mvn/wrapper/maven-wrapper.properties`, sem edição do Wrapper ou de variáveis para contornar o bootstrap. O Maven home informado foi `C:\Users\ronal\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0`. A falha inicial foi do bootstrap observado no ambiente restrito, não um resultado de testes; a mesma consulta concluiu na repetição autorizada. Não foi demonstrada uma causa interna mais específica para o NullArray, nem alterada a aplicação para contorná-lo.

Evidências locais preservadas em `target/harness-validation/feature-004-t001-t002-2026-10-02-1931/`, ignoradas pelo Git:

- `wrapper-version.txt` e `wrapper-version-result.json`: tentativa inicial com código 1; `wrapper-version-authorized.txt` e `wrapper-version-authorized-result.json`: versão obtida na repetição com código 0.
- `regressions.txt` / `regressions-result.json` e `full-suite.txt` / `full-suite-result.json`: logs, comandos, horários, códigos e destinos dos relatórios de cada execução.
- `regressions-surefire-reports/` e `full-suite-surefire-reports/`: cópias separadas dos 15 XMLs e 15 TXTs produzidos em cada execução, realizadas antes da substituição dos relatórios pela execução seguinte. `reports-summary.json` registra as classes e os agregados conferidos.

Os XMLs das duas execuções foram lidos e somados: cada conjunto contém exatamente as 15 classes previstas, 79 testes, 0 falhas, 0 erros e 0 ignorados. Os 15 resumos TXT de cada conjunto também foram conferidos sem falhas/erros/ignorados. A cópia da suíte completa foi comparada por SHA-256 com os arquivos atuais de `target/surefire-reports/`, todos iguais. A seleção de regressões abrange todas as classes de teste existentes nesta base; por isso os totais coincidem, embora sejam duas execuções distintas. Estes resultados são novos, locais e vinculados ao HEAD/diff acima; não substituem os 79 testes históricos Windows nem a execução remota Linux do harness.

T002 cumprida após a repetição autorizada do Wrapper e as duas execuções de testes existentes. A falha inicial permanece registrada. Nenhum teste novo de pedidos, chamada HTTP manual, demonstração de T051 ou prova de rollback da Feature 004 foi executado. T003 e todas as tarefas seguintes continuam pendentes; o resultado não comprova funcionamento ou aprovação funcional do registro de pedidos.

Fechamento documental: diff revisado e `git diff --check` concluído com código 0; `git diff --cached --exit-code` confirmou índice vazio. A comparação com o conteúdo anterior à execução confirmou todo o histórico deste quickstart preservado e tasks.md alterado somente nas caixas T001/T002, agora `[X]`; as 53 tarefas T003–T055 permanecem abertas. Raiz, branch e HEAD foram reconfirmados sem mudança. `git status --short --untracked-files=all` mostrou somente quickstart.md e tasks.md modificados, ambos sem stage; código, POM, configurações, testes e demais artefatos permaneceram sem alterações. `.specify/extensions.yml` continuou ausente, sem hooks before_implement/after_implement. Não houve stage, commit ou push.

## Execução limitada de T003/T004 — 2026-10-06

Escopo autorizado e concluído: somente T003 e T004, com parada antes de T005. Base inicial confirmada por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `71de00517e9b95b7348b6391e89d61bc0c1e1142` (checkpoint T001/T002) e checkout limpo. Nenhuma alteração preexistente foi encontrada.

Leituras: AGENTS.md, skill speckit-implement, constituição 1.0.0, harness, spec, tasks, plano, pesquisa, modelo, contratos, quickstart e implementação/configuração/testes existentes. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` concluiu com código 0 e FEATURE_DIR absoluto desta feature; todos os artefatos esperados estavam disponíveis. `checklists/requirements.md` foi conferido somente em leitura: 16 itens marcados, nenhum aberto. `.gitignore` já cobre target, classes, JARs e os demais artefatos Java pertinentes; nenhuma mudança de ignore foi necessária. `.specify/extensions.yml` estava ausente antes e depois da implementação; nenhum hook before_implement/after_implement foi executado.

### Mudanças e papel do suporte

- T003: `pom.xml` recebeu apenas a dependência `org.springframework.boot:spring-boot-starter-oauth2-resource-server`, sem versão explícita. Parent Spring Boot 3.3.4, Java 21 e JOSE existente preservados; a resolução Maven observada trouxe o starter 3.3.4 e Resource Server 6.3.3. Nenhuma alteração em SecurityConfig, JwtConfig, chave ou claims do login; nenhuma autenticação de /orders foi implementada.
- T004: `src/test/java/com/devsuperior/dscommerce/config/OrderTestConfig.java` é @TestConfiguration, restrita ao perfil test e importada explicitamente. Disponibiliza Clock primário controlável, inicialmente UTC em `2030-01-01T12:00:00.123Z`, com ajuste/avanço do instante e views de zona compartilhando o mesmo tempo. Gera uma URL H2 `order-test-<UUID>` por contexto, estável durante seus acessos. `isolatedDatabase(DynamicPropertyRegistry)` deve ser chamado no método @DynamicPropertySource próprio de cada classe que muta dados; o contexto deve ativar test e importar OrderTestConfig. Não reutilizar a URL compartilhada dscommerce-test nem gerar uma nova URL a cada conexão. Os encoders BCrypt/JWT e o decoder são os beans reais existentes; a chave descartável vem de application-test.properties, sem cópia ou substituição.
- `src/test/java/com/devsuperior/dscommerce/fixtures/OrderTestFixture.java` prepara contas e produtos nas tabelas existentes por JDBC com chaves geradas. Cada preparação/alteração/limpeza usa uma transação separada REQUIRES_NEW, confirmada antes do retorno, exclusivamente no suporte de teste. Rastreia os ids criados e limpa somente essas contas/produtos e vínculos de categoria dos produtos próprios. Permite alteração JDBC de preço somente dos produtos próprios, incluindo preço negativo sem mudar a validação ORM de Product. Recusa bancos fora do prefixo isolado. `signedToken` aceita claims explícitas e assina com o JwtEncoder HS256 existente. Nenhum perfil é provisionado: suporte de roles e limpeza de dependências de pedidos só serão acrescentados nas tarefas futuras após o schema correspondente; consumidores futuros devem limpar seus pedidos antes das contas/produtos.
- `src/test/java/com/devsuperior/dscommerce/config/OrderTestSupportTest.java` verifica o relógio, integração com encoders/decoder reais, commit das fixtures e sua limpeza, além de dois contextos H2 separados. Foi escrito antes dos corpos do suporte. Não houve ciclo RED comportamental exigido ou alegado para esta preparação; não foi executado teste de funcionalidades de pedidos ainda ausentes.

### Comandos e resultados realmente observados

Todas as execuções ocorreram na raiz em Windows/PowerShell com o mesmo HEAD acima. Na regressão de T003, o diff continha apenas a adição no POM. Nas execuções de T004 e da suíte completa, continha também os três arquivos Java novos em src/test; os registros documentais e checkboxes T003/T004 foram atualizados depois dos resultados. O índice permaneceu vazio.

A primeira tentativa do comando de regressões abaixo no sandbox falhou com código 1: bootstrap PowerShell `NullArray` / “Não é possível indexar em uma matriz nula” e `Cannot start maven from wrapper`. Maven e testes não iniciaram nessa tentativa. O resultado foi observado na saída da ferramenta, sem arquivo de log separado. A repetição fora do sandbox foi autorizada; nenhuma variável, Wrapper ou configuração da aplicação foi alterada para contornar o impedimento. Não foi demonstrada uma causa interna mais específica para o NullArray.

Horários de 2026-10-06, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version`, autorizado fora do sandbox | 15:37:14 → 15:37:21 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1, Azul Systems, Inc.; runtime `C:\Program Files\Zulu\zulu-21` | 0 |
| Regressões T003, comando completo abaixo, autorizado fora do sandbox | 15:37:33 → 15:39:12 | 15 classes, 79 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |
| `.\mvnw.cmd '-Dtest=OrderTestSupportTest' test`, autorizado fora do sandbox | 15:41:15 → 15:41:58 | 1 classe, 3 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |
| `.\mvnw.cmd test`, autorizado fora do sandbox | 15:42:15 → 15:43:17 | 16 classes, 82 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

Comando de regressões efetivamente executado nesta etapa:

```powershell
.\mvnw.cmd '-Dtest=PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,ProductRepositoryTest,LoginControllerTest,GlobalExceptionHandlerRegressionTest,JwtConfigurationTest,JwtTokenServiceTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,UserAccountRepositoryTest,PasswordEncoderConfigurationTest,H2ConsoleSecurityIntegrationTest' test
```

Os três testes de suporte confirmaram: ajuste/avanço e zona do Clock; contas/produtos visíveis por JDBC em conexão independente mesmo depois de rollback da transação do chamador; preço negativo preparado via JDBC em produto próprio; proteção contra alteração de produto da seed; seed de 52 produtos e conta demo intacta em outro contexto; limpeza explícita/idempotente preservando todos os dados e vínculos da seed; hash BCrypt válido, login real da conta criada e assinatura JWT HS256 aceita pelo decoder existente. Não se trata de prova de rollback de pedidos: o rollback usado aqui apenas demonstra independência da preparação. A suíte completa confirmou as regressões existentes junto do suporte novo, sem exigir importação de OrderTestConfig nos testes anteriores.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t003-t004-2026-10-06/`: wrapper-version.txt / wrapper-version-result.json, t003-regressions.txt / t003-regressions-result.json, t004-support.txt / t004-support-result.json e full-suite.txt / full-suite-result.json. Cópias separadas dos XMLs/TXTs em t003-surefire-reports (15 classes), t004-surefire-reports (somente a classe de suporte) e full-suite-surefire-reports (16 classes); reports-summary.json registra os totais somados diretamente dos XMLs. A cópia T003 foi feita antes dos testes de suporte, e a cópia do suporte antes da suíte completa; relatórios antigos não foram somados ao resultado isolado de T004. Estes resultados são desta execução local; não substituem a linha de base T001/T002 nem as evidências históricas Windows/Linux.

### Limites e fechamento do checkpoint

T003 e T004 concluídas e verificadas: somente suas duas checkboxes foram alteradas agora. T001/T002 permanecem [X]; T005–T055, exatamente 51 tarefas, permanecem abertas. Não houve execução de T005+, provisionamento de perfis, entidades/tabelas/serviços de pedido ou hooks de falha em produção. application-test.properties, import.sql, demais seeds/configurações compartilhadas, src/main, Wrapper e contratos foram preservados. U1/U2 continuam sugestões não aplicadas.

O Clock de teste não modifica o tempo do JwtTokenService ou do decoder compartilhado, que conservam seus comportamentos existentes; claims temporais de testes futuros devem ser coordenadas explicitamente. Ainda não há autenticação/endpoint de pedidos nem prova de aceitação ou rollback da Feature 004. Nenhuma validação remota Linux/GitHub foi executada neste checkpoint.

Diff rastreado e os três arquivos novos revisados. `git diff --check` sem erros; os arquivos novos foram conferidos também com `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>` e leitura direta de whitespace/newline, sem diagnósticos. Os avisos de futura conversão LF/CRLF não indicam erro de whitespace. `git diff --exit-code -- src/main src/test/resources .mvn` e `git diff --cached --exit-code` concluíram com código 0, confirmando fontes/configurações preservadas e índice vazio. Estado final desta entrega, conferido após a documentação: mesma raiz, branch e HEAD; pom.xml, quickstart.md e tasks.md modificados, mais os três arquivos Java novos/não rastreados em src/test. Não houve stage, commit ou push.

## Execução exclusiva de T005 — RED de perfis — 2026-10-06

Base inicial confirmada por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `da1cae9910fc06bfdaf78ab26ff88d03fd953326`, checkpoint T003/T004 commitado, checkout limpo. Escopo autorizado: escrever/executar somente T005, com parada antes de T006 e sem stage, commit ou push.

AGENTS.md, constituição 1.0.0, tarefa real/dependência T004, desenho de identidade/modelo/contrato e suporte existente foram conferidos. A skill speckit-implement já lida nesta sessão foi aplicada com o escopo limitado pelo usuário. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` concluiu com código 0, FEATURE_DIR absoluto desta feature e artefatos disponíveis. Checklist requirements.md conferido somente em leitura: 16 itens marcados, nenhum aberto. `.gitignore` já cobre target e artefatos Java; nenhum ajuste foi necessário. `.specify/extensions.yml` ausente antes e depois: nenhum hook before_implement/after_implement executado.

### Testes escritos antes da preparação mínima

Criado `src/test/java/com/devsuperior/dscommerce/repositories/UserAccountRoleRepositoryTest.java` com contexto Spring Boot real, perfil test, importação explícita de OrderTestConfig, URL H2 isolada pelo @DynamicPropertySource próprio e open-in-view desativado somente neste teste. Sem @Transactional envolvendo a classe: preparação de contas reutiliza OrderTestFixture; escrita/leitura JPA usa transações separadas e o retorno da consulta é examinado fora da transação. A verificação de coleção carregada ocorre antes de qualquer acesso que pudesse disparar lazy loading. Não há novo schema, enum ou coleção em produção.

A cobertura escrita exige: tabela PUBLIC.TB_USER_ACCOUNT_ROLE; exatamente user_account_id/role, tipos adequados, ambas NOT NULL, FK para tb_user_account.id e índice único do par; ausência de linhas representando conjunto vazio; quatro round-trips JPA (vazio, CLIENT, ADMIN, ambos), conferência JDBC dos valores e consulta por id com perfis já carregados; conta inexistente retornando Optional.empty(); rejeições JDBC por FK, par duplicado e cada coluna nula, com SQLStates H2 23506/23505/23502. A unicidade também exige aceitar CLIENT/ADMIN na mesma conta e CLIENT em outra conta. Os testes não importam um UserRole ainda inexistente: reflexão restrita ao teste exige a futura coleção Set de enum com CLIENT/ADMIN, sem criar tipos/mapeamentos artificiais.

Antes de qualquer insert que dependa da tabela nova, requireRoleColumns verifica tabela/colunas em INFORMATION_SCHEMA/DatabaseMetaData. A conta usada para demonstrar a consulta ausente depende somente da tabela existente tb_user_account, sendo válida nesta base. Limpeza explícita remove apenas perfis dos ids próprios se a tabela existir e depois chama fixture.cleanUp(); não tenta limpar tabela ausente. Configuração/fixture T004 e seeds compartilhados não foram editados.

Depois de escrever o teste, foi acrescentada em `src/main/java/com/devsuperior/dscommerce/repositories/UserAccountRepository.java` somente a preparação indispensável à sua chamada compilável: método default temporário findByIdWithRoles(Long id), retornando Optional.empty(), mais comentário de remoção em T006. Não há consulta, @Query, @EntityGraph, delegação a findById ou assinatura abstrata sujeita à derivação. findByName e seus testes existentes permanecem intactos.

### Execuções locais e evidência de RED

Comandos na raiz em Windows/PowerShell, HEAD acima, diff composto somente pelo teste novo e método default; documentação/checkbox foi atualizada após as execuções. Todos os comandos Wrapper foram autorizados fora do sandbox, considerando a falha de bootstrap NullArray já observada nos checkpoints anteriores desta sessão; essa falha não foi repetida nem atribuída à execução atual. Horários abaixo de 2026-10-06, offset -03:00.

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 16:12:15 → 16:12:17 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1, Azul Systems, Inc.; runtime `C:\Program Files\Zulu\zulu-21` | 0 |
| `.\mvnw.cmd '-Dtest=UserAccountRoleRepositoryTest' test`, versão inicial | 16:12:35 → 16:13:18 | 11 testes, 10 falhas, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |
| `.\mvnw.cmd '-Dtest=UserAccountRoleRepositoryTest' test`, versão final | 16:15:08 → 16:15:48 | 11 testes, 10 falhas, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |
| `.\mvnw.cmd '-Dtest=UserAccountRepositoryTest' test` | 16:16:05 → 16:16:30 | regressão existente: 4 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

O primeiro log mostra compilação dos 25 arquivos de produção e 20 fontes de teste em Java 21, abertura do H2 isolado e `Started UserAccountRoleRepositoryTest` às 16:13:15.781. A execução final recompilou as fontes de teste e registrou `Started UserAccountRoleRepositoryTest` às 16:15:45.101, banco `jdbc:h2:mem:order-test-079adb09-7b68-4f8c-81d6-dbeb2fef6f4b`. Portanto, o RED não é falha de compilação, startup ou fixture.

Nas duas execuções, nove falhas são AssertionFailedError com a mensagem `T006 must map the missing table PUBLIC.TB_USER_ACCOUNT_ROLE`, antes de inserir perfis ou tentar round-trip. A décima é AssertionError no teste da conta existente sem perfis: `findByIdWithRoles must find an existing account, including one with no roles`; esperava Optional presente, mas o default temporário devolveu vazio. O caso de conta inexistente passou. XMLs/TXTs foram examinados e confirmam 11/10/0/0, sem falha de preparação ou limpeza. As verificações de constraints, enum, round-trip e coleção carregada estão escritas, mas seus trechos posteriores à guarda de metadados não foram alcançados nesta base; não se alega GREEN desses requisitos.

Entre as duas execuções, a revisão local da biblioteca Hibernate 6.5.3.Final confirmou suporte a EnumJdbcType no H2Dialect e seleção do tipo SQL ENUM para enum STRING. Ajustada somente a assertiva de tipo do teste, aceitando VARCHAR ou ENUM nativo, sem exigir uma representação física incompatível com o dialect nem aceitar ordinal inteiro. Por isso houve a segunda execução do alvo. A consulta `javap -c -p -classpath C:/Users/ronal/.m2/repository/org/hibernate/orm/hibernate-core/6.5.3.Final/hibernate-core-6.5.3.Final.jar` para H2Dialect e EnumJavaType, com Select-String dos métodos de enum, concluiu autorizada com código 0. A primeira leitura de H2Dialect no sandbox emitiu AccessDeniedException; o código do pipeline não foi tratado como leitura bem-sucedida. Nenhuma tabela experimental ou mapeamento JPA foi criado para esta conferência.

Evidências em `target/harness-validation/feature-004-t005-2026-10-06/`, ignoradas pelo Git: wrapper-version.txt / wrapper-version-result.json; t005-red.txt / t005-red-result.json (primeira execução); t005-red-final.txt / t005-red-final-result.json; account-regression.txt / account-regression-result.json. XML/TXT de cada classe copiados separadamente em t005-red-initial-reports/, t005-red-final-reports/ e account-regression-reports/ antes de prosseguir; reports-summary.json contém os totais e causas por caso. Os originais são produzidos em target/surefire-reports/. Nenhum relatório antigo foi somado aos resultados desses alvos. A regressão de findByName é uma execução atual de quatro testes; os 82 testes do checkpoint T003/T004 continuam evidência histórica, não repetida agora. Não foi executada suíte completa nem validação remota neste checkpoint.

### Conclusão de T005 e exigências de T006

T005 foi cumprida como tarefa de escrita/execução de testes com RED esperado, não como implementação aprovada de perfis. Somente sua checkbox foi marcada nesta etapa: T001–T005 [X], T006–T055 abertas (50). O próximo passo exigirá enum CLIENT/ADMIN, coleção JPA STRING, tabela com FK/NOT NULL/unicidade, persistência dos conjuntos e substituição do default por consulta explícita com left join fetch, mantendo recuperável a conta sem perfis. T006 deve obter GREEN dos novos testes e preservar findByName/login; não foi iniciada aqui.

Diff rastreado e arquivo novo revisados; `git diff --check` sem erros. O arquivo novo também passou por `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/repositories/UserAccountRoleRepositoryTest.java`, sem diagnósticos (código 1 pela diferença com /dev/null), e leitura direta: nenhum espaço final, newline presente. Índice vazio; POM, entidades, configurações, recursos/seeds, suporte T004 e testes anteriores preservados. Estado final conferido: mesma raiz/branch/HEAD; somente UserAccountRepository.java, quickstart.md e tasks.md modificados, além do novo UserAccountRoleRepositoryTest.java não rastreado. Sem T006+, U1/U2, stage, commit ou push.

## Execução exclusiva de T006 — GREEN de perfis — 2026-10-06

Escopo autorizado: somente T006, com parada antes de T007. Base reconfirmada por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `da1cae9910fc06bfdaf78ab26ff88d03fd953326`. Havia alterações locais de T005 em quickstart.md, tasks.md e UserAccountRepository.java, além de UserAccountRoleRepositoryTest.java não rastreado. O teste e a documentação/evidências de RED foram preservados; somente o corpo temporário do repositório foi substituído conforme T006.

AGENTS.md, skill speckit-implement, constituição, tarefa real, plano, data-model, pesquisa, contratos e suporte existente foram consultados. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou código 0, FEATURE_DIR absoluto correto e artefatos disponíveis. Checklist requirements.md: 16 marcados, nenhum aberto, sem edição. Ignore Java existente adequado, sem alteração. `.specify/extensions.yml` ausente nas verificações anterior/posterior, sem hooks de implementação.

### Implementação e preservação do escopo

- `src/main/java/com/devsuperior/dscommerce/entities/UserRole.java`: enum com somente CLIENT e ADMIN.
- `src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java`: Set<UserRole> inicialmente vazio, @ElementCollection(fetch = LAZY), @Enumerated(STRING), tabela tb_user_account_role, join column user_account_id NOT NULL e role NOT NULL; @ForeignKey nomeada fk_user_account_role_account e @UniqueConstraint do par nomeada uk_user_account_role. Getter getRoles com @JsonIgnore evita exposição por serialização da entidade. id/name/passwordHash, construtores, equals/hashCode permanecem intactos; nenhum perfil é concedido automaticamente e nenhuma seed foi alterada.
- `src/main/java/com/devsuperior/dscommerce/repositories/UserAccountRepository.java`: método abstrato Optional<UserAccount> findByIdWithRoles(@Param("id") Long id), com a consulta exata `select distinct account from UserAccount account left join fetch account.roles where account.id = :id`. Removidos comentário e corpo default provisórios; findByName preservado. O left join mantém contas sem linhas de perfis; fetch carrega a coleção durante a consulta, e distinct evita repetir a conta quando há ambos os perfis. Outras consultas mantêm o mapeamento lazy.

Os testes de T005 não foram editados nem enfraquecidos. A comparação SHA256 de 40 arquivos presentes na captura inicial (src/test, recursos/seeds de produção, Wrapper, POM e arquivos de evidência de T005) confirmou zero mudanças. O histórico anterior deste quickstart foi mantido como prefixo integral; tasks.md mudou nesta etapa somente a caixa T006.

### Comandos e resultados locais reais

Executados na raiz Windows/PowerShell, com o HEAD acima e diff local de T005/T006. Wrapper autorizado fora do sandbox devido ao impedimento de bootstrap já observado anteriormente; nenhuma tentativa falha nova no sandbox foi atribuída a esta etapa. Horários de 2026-10-06, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 16:37:46 → 16:37:48 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime `C:\Program Files\Zulu\zulu-21` | 0 |
| `.\mvnw.cmd '-Dtest=UserAccountRoleRepositoryTest,UserAccountRepositoryTest' test` | 16:38:15 → 16:39:01 | 15 testes: 11 de perfis + 4 existentes do repositório; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |
| Regressões de login e suporte, comando completo abaixo | 16:39:30 → 16:40:27 | 45 testes: 42 de login/JWT/BCrypt/handlers + 3 de suporte; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

```powershell
.\mvnw.cmd '-Dtest=LoginControllerTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,JwtTokenServiceTest,JwtConfigurationTest,PasswordEncoderConfigurationTest,GlobalExceptionHandlerRegressionTest,OrderTestSupportTest' test
```

Totais por classe na segunda execução: LoginControllerTest 13; AuthenticationServiceTest 4; AuthenticationInternalFailureTest 4; AuthenticationSensitiveLoggingTest 4; JwtTokenServiceTest 1; JwtConfigurationTest 6; PasswordEncoderConfigurationTest 1; GlobalExceptionHandlerRegressionTest 9; OrderTestSupportTest 3. Todos com zero falhas/erros/ignorados. São 60 testes executados nesta etapa: 11 de perfis e 49 regressões/suporte, sem somar os resultados históricos. A suíte completa não foi executada; nenhuma validação remota foi feita.

Os 11 casos de perfis passaram integralmente: metadados (duas colunas NOT NULL, tipo textual, FK e unicidade), conta sem linhas com conjunto vazio carregado, conta inexistente, quatro round-trips (vazio/CLIENT/ADMIN/ambos), leitura JDBC dos valores e consulta com coleção carregada antes do acesso, seguida de acesso fora da transação com open-in-view desativado. As rejeições JDBC agora foram efetivamente executadas: FK inválida SQLState 23506, par duplicado 23505, account_id nulo e role nulo 23502. O caso de unicidade também inseriu CLIENT/ADMIN na mesma conta e CLIENT em outra conta, conservando três linhas após a rejeição. A guarda de metadados deixou de bloquear esses trechos; não se usa falha de Bean Validation como evidência de constraint.

O log repository-green.txt registra contexto inicializado e o DDL H2 `user_account_id bigint not null, role enum ('ADMIN','CLIENT') not null`, com chave primária composta nomeada uk_user_account_role (gerada pelo Hibernate para este Set, garantindo unicidade do par) e FK nomeada fk_user_account_role_account. ENUM nativo do H2 representa os nomes de @Enumerated(STRING), não ordinais. O RED de T005 permanece registrado acima e nos relatórios próprios, com 11 testes/10 falhas/0 erros/0 ignorados; não foi reexecutado nem reescrito como GREEN histórico.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t006-2026-10-06/`: wrapper-version.txt / wrapper-version-result.json, repository-green.txt / repository-green-result.json, login-regressions.txt / login-regressions-result.json. XMLs/TXTs copiados dos originais em target/surefire-reports/ para repository-green-reports/ (somente as duas classes, antes da execução seguinte) e login-regressions-reports/ (somente as nove classes executadas). reports-summary.json agrega diretamente esses XMLs e lista todos os casos; relatórios antigos não entram nos totais. preserved-before.json e cópias documentais iniciais permitem conferir a preservação de T005.

### Fechamento e limitações

T006 concluída e validada. T001–T006 [X]; T007–T055 abertas (49). Nenhuma implementação/provisionamento de fixtures de perfis de T007, principal, resolução autenticada, pedido, endpoint ou política HTTP foi acrescentada. U1/U2 não aplicadas. As constraints foram verificadas no schema H2 gerado, sem migração ou validação de outro banco.

Diff e arquivos novos revisados; `git diff --check` e conferência específica de whitespace/newline dos não rastreados sem problemas. Índice vazio. Estado final: mesma raiz, branch e HEAD; quickstart.md, tasks.md, UserAccount.java e UserAccountRepository.java modificados; UserRole.java novo/não rastreado e UserAccountRoleRepositoryTest.java ainda não rastreado, preservado de T005. Sem stage, commit ou push.

## Execução exclusiva de T007 — fixtures de perfis e principal mínimo — 2026-10-06

Escopo autorizado: somente T007, com parada antes de T008. Conferidos raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `3bc1ef590ca50abf04a442ab356be06167d900db` (checkpoint T005/T006) e checkout inicialmente limpo, usando `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`.

AGENTS.md, skill speckit-implement já lida, constituição, tarefa real/dependência T006, modelo, desenho de identidade, pesquisa, contrato, quickstart e suporte de teste foram consultados. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou código 0 e FEATURE_DIR absoluto correto. Checklist requirements.md conferido sem edição: 16 marcados, nenhum aberto. Ignore existente adequado para Java/target, sem alteração. `.specify/extensions.yml` ausente antes e depois, sem hooks before_implement/after_implement.

### Mudanças e cobertura pertinente

- `src/test/java/com/devsuperior/dscommerce/fixtures/OrderTestFixture.java`: sobrecarga `createAccount(name, rawPassword, Set<UserRole>)` aceita explicitamente CLIENT, ADMIN isolado, ambos ou conjunto vazio. Copia o Set antes de gravar, usa BCrypt real e insere a conta e todas as linhas de perfis por JDBC na mesma transação REQUIRES_NEW confirmada antes do retorno; rastreia o id somente depois do sucesso. A assinatura anterior de dois argumentos permanece e delega com Set vazio. Nenhum perfil automático. Extraído insertRow para compartilhar a inserção com chave gerada sem abrir outra transação dentro do provisionamento. Produtos, alteração de preço e encoder JWT mantêm seus comportamentos anteriores.
- Limpeza remove tb_user_account_role apenas dos accountIds próprios antes de remover suas contas, respeitando a FK; produtos/vínculos próprios continuam sendo limpos como antes. A restrição ao prefixo H2 isolado order-test permanece. Não há alterações em seeds ou configurações compartilhadas, nem provisionamento de conta real.
- `src/test/java/com/devsuperior/dscommerce/config/OrderTestSupportTest.java`: duas novas provas de integração, além dos três testes anteriores. Uma prepara as quatro combinações dentro de um chamador que sofre rollback e verifica contas/perfis por conexão JDBC independente; depois comprova limpeza idempotente preservando conta/perfil de outro proprietário no mesmo H2 e restaurando a seed. Outra abre dois contextos H2 distintos, cria contas com o mesmo nome e perfis diferentes, limpa o primeiro e comprova que o segundo conserva sua conta ADMIN; após ambas as limpezas, verifica as linhas de seed. O teste anterior de encoders agora também verifica ausência de linhas de perfis e login via POST /login com MockMvc sobre contexto real e FilterChainProxy real: HTTP 200, payload com dois campos, Bearer e JWT aceito pelo decoder existente. Login pelo AuthenticationService real também permanece verificado, sem claims de roles/hash/segredo, e a conta continua sem perfis depois do HTTP.
- `src/main/java/com/devsuperior/dscommerce/security/AuthenticatedAccountPrincipal.java`: record transitório com somente Long accountId, sem name, roles, hash, segredo, anotação de persistência ou registro Spring. Foi compilado no build GREEN e conferido integralmente em leitura. É um valor mínimo da identidade autenticada, distinto da entidade UserAccount persistida e de seus dados de conta/permissões. Ainda não está conectado ao fluxo HTTP ou à resolução de conta.

### Execuções realmente observadas

Testes novos escritos antes da lógica. Para permitir compilação inicial, a sobrecarga de fixture recebeu provisoriamente corpo delegando à criação anterior sem perfis. Executado o novo teste de quatro combinações: contexto inicializado, 1 teste, 1 falha, 0 erros, 0 ignorados; assertiva JDBC esperava CLIENT, mas encontrou conjunto vazio. Nenhuma falha de compilação/startup/preparação/limpeza foi usada como RED. Depois, a delegação provisória foi substituída pelo provisionamento completo, sem reduzir assertivas.

Comandos na raiz Windows/PowerShell, HEAD acima e diff de T007. Wrapper autorizado fora do sandbox considerando o impedimento de bootstrap observado anteriormente; nenhuma nova falha de sandbox foi atribuída a esta etapa. Horários de 2026-10-06, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd '-Dtest=OrderTestSupportTest#shouldCommitAllRoleFixturesAndCleanOnlyOwnedRows' test` | 16:58:33 → 16:59:10 | RED: 1 teste, 1 falha pela ausência de CLIENT persistido, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |
| `.\mvnw.cmd --version` | 17:00:18 → 17:00:19 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime `C:\Program Files\Zulu\zulu-21` | 0 |
| GREEN e regressões, comando abaixo | 17:00:20 → 17:01:26 | 37 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

```powershell
.\mvnw.cmd '-Dtest=OrderTestSupportTest,UserAccountRoleRepositoryTest,UserAccountRepositoryTest,LoginControllerTest,AuthenticationServiceTest' test
```

Totais atuais por classe: OrderTestSupportTest 5 (dois novos e três anteriores, com login ampliado); UserAccountRoleRepositoryTest 11; UserAccountRepositoryTest 4; LoginControllerTest 13; AuthenticationServiceTest 4. Todos passaram sem falhas/erros/ignorados. Os testes de T005/T006 foram preservados e exercitaram novamente constraints JDBC, round-trip e carregamento explícito; a fixture nova permaneceu compatível com eles. O teste anterior de suporte confirmou também preservação dos 52 produtos, categorias/vínculos e dados da seed, Clock e encoders reais. Não se trata de prova de rollback de pedidos: o rollback do chamador demonstra somente independência do commit das fixtures. Nenhuma suíte completa ou validação remota foi executada neste checkpoint; os 60 aprovados em T006 permanecem evidência histórica separada.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t007-2026-10-06/`: fixture-red.txt / fixture-red-result.json; wrapper-version.txt / wrapper-version-result.json; fixture-green-regressions.txt / fixture-green-regressions-result.json. XMLs/TXTs copiados de target/surefire-reports/ para fixture-red-reports/ antes do GREEN (somente OrderTestSupportTest, 1 caso) e fixture-green-regressions-reports/ após o GREEN (somente as cinco classes executadas); reports-summary.json agrega os XMLs e lista os casos, sem somar relatórios antigos. preserved-before.json e cópias iniciais dos documentos/fontes registram a base e sua preservação.

### Fechamento e limites

T007 concluída e validada, marcada somente após GREEN. T001–T007 [X]; T008–T055 abertas (48). O histórico anterior deste quickstart foi preservado integralmente; tasks.md alterado somente na caixa T007. Comparação SHA256 dos 54 arquivos capturados confirmou zero alterações nos recursos/seeds compartilhados, POM, Wrapper, OrderTestConfig, teste de perfis e evidências preservadas de T005/T006. Não foram implementados resolução autenticada, filtro, proteção HTTP, pedidos ou qualquer tarefa T008+; U1/U2 não aplicadas.

Diff e arquivo novo revisados; `git diff --check` e conferência de whitespace/newline do principal sem erros. Índice vazio. Raiz, branch e HEAD reconfirmados sem mudança. Estado final: quickstart.md, tasks.md, OrderTestSupportTest.java e OrderTestFixture.java modificados; AuthenticatedAccountPrincipal.java novo/não rastreado. Não houve stage, commit ou push.

## Execução exclusiva de T008 — contrato unitário de resolução de conta (RED) — 2026-10-07

Escopo autorizado: somente T008, com parada antes de T009. Base conferida por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `27850dddc8a21dd1b6542b9cf5f7aa9d9ccf273c` (checkpoint T007) e checkout inicialmente limpo, sem alterações preexistentes. A execução atual não reatribui os 37 testes aprovados em 06/10/2026 a uma execução nova.

Lidas as instruções do projeto e da skill speckit-implement, constituição, tarefas/dependência T007, plano, spec, pesquisa D1/D2/D7, modelo de identidade, contratos, quickstart e fontes pertinentes. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou código 0, FEATURE_DIR absoluto correto e research.md, data-model.md, contracts/, quickstart.md e tasks.md disponíveis. Checklist requirements.md conferido somente em leitura: 16 marcados, nenhum aberto. Ignore existente cobre target e artefatos Java; não foi alterado. Não havia extensions.yml nem hooks before_implement.

### Teste escrito primeiro e suporte mínimo

Criado primeiro `src/test/java/com/devsuperior/dscommerce/services/AuthenticatedAccountServiceTest.java`, instanciando diretamente o serviço com UserAccountRepository mockado, sem contexto Spring ou transação de teste. O contrato exige:

- CLIENT, ADMIN isolado, ambos e conjunto vazio: principal somente com accountId e authorities ROLE_CLIENT/ROLE_ADMIN derivadas exclusivamente dos perfis da conta devolvida pelo repositório; nenhum perfil automático. A saída conserva os valores após mudança na coleção da conta de preparação.
- Uso exclusivo de `findByIdWithRoles(accountId)`, rejeitando lookup comum por id/nome, escrita ou outra interação. A consulta existente tem left join fetch e mantém conta sem linhas de perfis recuperável.
- Conta ausente: AuthenticationException; indisponibilidade de lookup: OrderAccountResolutionException distinta de AuthenticationException e InvalidCredentialsException, com causa preservada internamente e mensagem sem os detalhes privados da causa.
- Saída transitória com exatamente principal/authorities, principal somente accountId; sem entidade UserAccount, nome, hash ou segredo.

Somente depois do teste foi criado `src/main/java/com/devsuperior/dscommerce/services/AuthenticatedAccountService.java`: construtor recebe o repositório, `resolve(Long accountId)` retorna provisoriamente null e o record aninhado ResolvedAccount define somente principal/authorities. Não há lógica de consulta/mapeamento/tradução de falha, anotação Service/Component/Transactional ou registro por Bean. A exceção OrderAccountResolutionException não foi criada: o teste verifica seu nome de tipo em runtime para não antecipar T009.

Estes mocks verificam o contrato da chamada e do resultado; não comprovam carregamento JPA, ausência de LazyInitializationException em uso real ou funcionamento de proxy transacional. O fetch explícito sem sessão aberta na view é exigido pela escolha da consulta e pela saída sem entidade; a prova JPA existente em UserAccountRoleRepositoryTest (open-in-view=false, isLoaded e leitura fora da transação) pertence a T005/T006 e não foi executada novamente nesta etapa. Não foi inicializado contexto de aplicação nesta execução unitária.

### Execuções realmente observadas

Comandos na raiz Windows/PowerShell, no HEAD acima com somente os dois arquivos Java novos no diff de trabalho durante o teste. Executados fora do sandbox com autorização após falha de criação do processo no ambiente restrito (`helper_unknown_error: setup refresh had errors`); a tentativa de patch também falhou ao gravar. Esses impedimentos de ferramenta não foram contados como RED. Nenhum erro de bootstrap, compilação, startup ou preparação foi apresentado como falha comportamental.

Horários de 2026-10-07, offset -03:00:

| Comando | Início → fim | Resultado observado | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 15:44:09 → 15:44:33 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime C:\Program Files\Zulu\zulu-21 | 0 |
| `.\mvnw.cmd '-Dtest=AuthenticatedAccountServiceTest' test` | 15:44:33 → 15:45:47 | RED: 8 testes, 8 falhas, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |

Compilação concluída: 28 arquivos de produção e 21 arquivos de teste, javac release 21. As oito falhas são comportamentais: quatro combinações de perfis e a saída mínima receberam null; conta ausente e falha interna não lançaram exceção; a verificação de consulta registrou "Wanted but not invoked" para findByIdWithRoles(42L), com zero interações no mock. Os XMLs confirmam sete AssertionErrors por valor/exceção ausente e uma falha de verificação Mockito; nenhum erro de execução. As assertivas posteriores de mapeamento, confidencialidade e tipo de falha permanecem exigências para o GREEN de T009, sem alegação de que já passaram.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t008-2026-10-07/`: wrapper-version.txt / wrapper-version-result.json; resolution-red.txt / resolution-red-result.json; reports-summary.json; cópias dos dois fontes executados. XML/TXT de AuthenticatedAccountServiceTest foram copiados de `target/surefire-reports/` para `resolution-red-reports/`, sem incluir/somar relatórios antigos. preserved-before.json registra SHA256 dos 123 arquivos rastreados; quickstart-before.md e tasks-before.md preservam os documentos anteriores. Antes do registro documental, a comparação confirmou zero mudanças nos 123 arquivos rastreados. Nenhuma suíte completa, regressão adicional, chamada HTTP ou validação remota foi executada.

### Fechamento e limites

T008 cumprida como tarefa de escrever/executar testes e demonstrar RED, marcada somente após conferência das falhas e dos relatórios. T001–T008 [X]; T009–T055 abertas (47). T009 deverá substituir o corpo provisório por resolução via consulta explícita, mapear principal/authorities dentro de transação read-only pelo proxy Spring, registrar o serviço e distinguir conta ausente de falha interna, obtendo GREEN sem enfraquecer este contrato.

Histórico deste quickstart preservado por acréscimo; tasks.md alterado somente na checkbox T008. Não houve implementação de T009+, filtro, handler ou proteção HTTP; U1/U2 não aplicadas. Sem stage, commit, push ou mudança de branch. O RED permanece intencional até T009.

Revisão final: a conferência dos novos arquivos com autocrlf desativado detectou um CRLF final misturado a LF; ambos foram normalizados para LF, sem mudança lógica. As cópias dos fontes efetivamente executados permanecem nas evidências e a comparação após normalizar finais de linha confirmou conteúdo idêntico. Não foi repetido o teste por essa correção de formatação. Whitespace dos novos arquivos reconferido com `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnósticos; código 1 indica conteúdo novo no modo no-index. A revisão e `git diff --check` abrangem também os documentos; nenhum arquivo além dos dois fontes novos e dos dois documentos do checkpoint foi alterado.

## Execução exclusiva de T009 — resolução autenticada e proxy read-only (GREEN) — 2026-10-07

Escopo autorizado: somente T009, com parada antes de T010. Conferidos raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `27850dddc8a21dd1b6542b9cf5f7aa9d9ccf273c` e status por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`. Índice inicialmente vazio. Alterações locais preexistentes de T008: quickstart.md/tasks.md modificados; AuthenticatedAccountService.java e AuthenticatedAccountServiceTest.java não rastreados. Foram preservadas, com evolução autorizada somente do suporte provisório do serviço e dos dois documentos.

Instruções do projeto/skill já lida, T009/dependência T008, pesquisa D1/D2/D7, modelo de identidade, desenho, contratos e testes pertinentes conferidos. Pré-requisitos do Spec Kit retornaram código 0 e FEATURE_DIR absoluto da Feature 004. Checklist requirements.md: 16 marcados, nenhum aberto, sem edição. Ignore existente suficiente e preservado; extensions.yml ausente antes da execução.

### Implementação e distinção de falhas

AuthenticatedAccountService agora é serviço Spring com método público resolve(Long) e transação read-only. Usa exclusivamente findByIdWithRoles; o left join fetch existente carrega a conta e seus perfis. Principal mínimo com accountId e authorities ROLE_CLIENT/ROLE_ADMIN são construídos dentro do método transacional. O resultado contém valores transitórios e conjunto imutável, sem entidade JPA, nome, hash ou segredo. Conta existente sem perfis é resolvida com authorities vazias, sem conceder perfil e sem antecipar autorização de pedidos.

Ausência da conta lança BadCredentialsException com mensagem genérica "Authentication required or invalid", fora do catch de falhas internas. RuntimeException de consulta/mapeamento é encapsulada em OrderAccountResolutionException, criada no caminho de T009, com mensagem "Account resolution could not be completed" e causa preservada internamente. A exceção interna não é AuthenticationException nem InvalidCredentialsException. O futuro adaptador distinguirá autenticação inválida (401) de falha interna (500); nenhum handler HTTP foi implementado nesta etapa.

AuthenticatedAccountServiceTest de T008 permaneceu byte a byte inalterado, incluindo todas as assertivas. O histórico e os 12 artefatos RED arquivados de T008 também foram preservados por SHA256. Não houve repetição do RED nem enfraquecimento da cobertura.

### Prova de registro e execução pelo proxy real

Criado antes da lógica de T009 o teste adicional AuthenticatedAccountServiceIntegrationTest, com SpringBootTest, perfil test, repositório JPA real, fixtures JDBC confirmadas e H2 próprio por DynamicPropertySource; sem transação na classe/métodos de teste, sem mock/spy de repositório ou serviço e com open-in-view=false.

Quatro casos exercitam CLIENT, ADMIN isolado, ambos e nenhum perfil pelo serviço injetado e proxy Spring. StatementInspector exclusivo de teste observa a consulta real com left join de tb_user_account_role e transação ativa/read-only. Advice observador é anexado dentro do proxy já existente, após o TransactionInterceptor: somente registra o resultado mapeado e a transação ativa/read-only antes de sua conclusão; não cria transação, não altera consulta/resultado e não substitui o interceptor real. Assim, a prova não se limita a inspecionar anotações ou mocks.

Depois do retorno do proxy, os testes comprovam ausência de transação e EntityManager ligado à thread, limpam as contas/perfis das fixtures e usam accountId/authorities conservados no resultado, incluindo conjunto vazio e imutabilidade. Dois casos adicionais comprovam conta ausente e falha SQL real: renomeiam temporariamente a tabela de perfis apenas no H2 isolado, observam OrderAccountResolutionException com causa DataAccessException e restauram a tabela em finally antes da limpeza. Não se trata de simulação de falha física de conexão nem de prova de rollback de pedidos.

### Execuções realmente observadas

Na raiz Windows/PowerShell, HEAD acima com suporte do serviço de T008 substituído pela implementação e os dois novos arquivos de T009. Java 21.0.12.1 Azul Systems, Inc., runtime C:\Program Files\Zulu\zulu-21; Maven 3.9.16 selecionado pelo Wrapper. Horários de 2026-10-07, offset -03:00:

| Comando | Início → fim | Resultado observado | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 16:14:26 → 16:14:29 | Maven 3.9.16 e Java 21 confirmados | 0 |
| GREEN de resolução, comando abaixo | 16:14:29 → 16:15:38 | 14 testes: 8 unitários de T008 e 6 de integração; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |
| Regressões de conta/login, comando abaixo | 16:18:39 → 16:20:47 | 62 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

```powershell
.\mvnw.cmd '-Dtest=AuthenticatedAccountServiceTest,AuthenticatedAccountServiceIntegrationTest' test
.\mvnw.cmd '-Dtest=OrderTestSupportTest,UserAccountRoleRepositoryTest,UserAccountRepositoryTest,LoginControllerTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,JwtTokenServiceTest,JwtConfigurationTest,PasswordEncoderConfigurationTest,GlobalExceptionHandlerRegressionTest' test
```

Regressões por classe: OrderTestSupportTest 5; UserAccountRoleRepositoryTest 11; UserAccountRepositoryTest 4; LoginControllerTest 13; AuthenticationServiceTest 4; AuthenticationInternalFailureTest 4; AuthenticationSensitiveLoggingTest 4; JwtTokenServiceTest 1; JwtConfigurationTest 6; PasswordEncoderConfigurationTest 1; GlobalExceptionHandlerRegressionTest 9. Os 76 testes selecionados em dois grupos disjuntos não são uma execução da suíte completa. As regressões incluem suporte/limpeza das fixtures, perfis e constraints reais, repositório/login, falhas internas, confidencialidade, JWT/encoders e tratamento MVC existente.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t009-2026-10-07/`: wrapper-version.txt / wrapper-version-result.json; resolution-green.txt / resolution-green-result.json; account-login-regressions.txt / account-login-regressions-result.json; reports-summary.json. XMLs/TXTs copiados de target/surefire-reports para resolution-green-reports (somente duas classes) e account-login-regression-reports (somente onze classes); totais agregados apenas desses relatórios atuais, sem somar arquivos antigos. Fontes executados copiados, preserved-before.json registra hashes da base/fontes/artefatos RED e quickstart-before.md/tasks-before.md/service-before.java preservam a base local.

### Fechamento e limites

T009 concluída após GREEN, prova real do proxy/H2 e regressões. T001–T009 [X]; T010–T055 abertas (46). Histórico do quickstart preservado por acréscimo e tasks.md alterado neste checkpoint somente na checkbox T009. Diff do serviço contra o suporte preexistente e arquivos novos revisados; git diff --check e conferência no-index de whitespace dos fontes novos sem diagnósticos (código 1 do modo no-index indica conteúdo novo).

Somente serviço, exceção, teste adicional e os dois documentos foram alterados nesta execução; o teste unitário e as evidências RED de T008 permanecem preservados. H2 é a prova local, sem alegação sobre outro banco ou ambiente. Suíte completa e CI não executadas; não foi testada falha de abertura/conclusão transacional nem implantado adaptador HTTP. Nenhuma tarefa T010+, filtro, handler, proteção HTTP ou pedido foi implementado. U1/U2 não aplicadas; sem stage, commit, push ou mudança de branch.

## Execução exclusiva de T010 — revisão e validação conjunta da fundação — 2026-10-07

Escopo autorizado: somente revisão e validação de T010, com parada antes de T011 e sem implementação adicional. Conferidos por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `545569b8d08cd9500a2f211e5df77007c82bb5a9` (checkpoint T008–T009 esperado) e checkout inicialmente limpo, com índice vazio.

Lidas as instruções do projeto/skill speckit-implement, constituição, tarefa T010/dependência T009, plano, pesquisa D1/D2/D7, modelo de identidade, contratos, testes e fontes da fundação/login/respostas públicas. Pré-requisitos do Spec Kit retornaram código 0 e FEATURE_DIR absoluto correto. Checklist requirements.md: 16 marcados, nenhum aberto, conferido somente em leitura. Ignore existente suficiente e preservado; extensions.yml ausente, sem hooks antes/depois.

### Conclusões da revisão contra modelo e pesquisa

| Ponto revisado | Constatação e evidência |
| --- | --- |
| Perfis mínimos, lazy, sem atribuição automática | UserRole contém somente CLIENT/ADMIN. UserAccount usa Set inicialmente vazio, ElementCollection LAZY e EnumType.STRING; construtor preserva somente id/name/passwordHash. Nenhum caminho de produção concede perfis automaticamente; seed e login não foram modificados. Fixtures provisionam perfis explicitamente apenas em testes. |
| FK, unicidade e NOT NULL | CollectionTable define FK para tb_user_account, unicidade de (user_account_id, role) e NOT NULL nas duas colunas. UserAccountRoleRepositoryTest confere metadados do H2 e violações reais por JDBC, sem usar Bean Validation ou apenas inspeção de anotações como prova das constraints. |
| Consulta explícita, inclusive conjunto vazio | findByIdWithRoles usa select distinct com left join fetch; findByName permanece preservado. Teste JPA verifica isLoaded antes de acessar roles, round-trip dos quatro conjuntos e uso fora da transação; conta sem linhas de perfis continua recuperável. |
| Principal e saída internos mínimos | AuthenticatedAccountPrincipal contém somente accountId. ResolvedAccount contém principal e authorities imutáveis ROLE_CLIENT/ROLE_ADMIN derivadas dos perfis do banco; conjunto vazio permanece vazio. Não retorna entidade JPA, nome, hash ou segredo; teste unitário verifica o shape serializado e os valores após mutação da conta de preparação. |
| Serviço Spring, proxy e read-only | Serviço injetado é proxy real. Integração com repositório real/H2 próprio e open-in-view=false observa SQL com left join e transação ativa/read-only, além do mapeamento antes da conclusão transacional. Após retorno, comprova ausência de transação/EntityManager ligado à thread e utiliza identidade/authorities mesmo após excluir as fixtures. Advice exclusivo de teste observa o proxy já existente, sem criar transação ou substituir seu interceptor. |
| Conta ausente versus falha interna | Conta ausente gera BadCredentialsException com mensagem genérica. RuntimeException de consulta/mapeamento gera OrderAccountResolutionException distinta de AuthenticationException/InvalidCredentialsException, com causa interna preservada e mensagem genérica. Testes unitários e integração cobrem essa distinção; a integração provoca falha SQL real somente no H2 isolado e restaura a tabela em finally. |
| Ausência de perfis em respostas públicas/login | Controllers de produtos retornam DTOs de catálogo/detalhes sem dados de conta/perfis; login retorna exatamente accessToken/tokenType. AuthenticationService não lê roles, findByName não recebeu fetch adicional e JwtTokenService não emite claim roles. LoginControllerTest confere shape exato e ausência de roles/permissions; OrderTestSupportTest confirma JWT sem roles/hash/segredo e login HTTP 200 de conta sem perfil, que continua sem perfis depois do login. getRoles/getPasswordHash também têm JsonIgnore. O claim name histórico do JWT foi preservado; ele não integra o principal nem a saída do resolver. |
| Seeds e contratos existentes | Comparação Git entre 4afb5e7 (base antes da implementação) e HEAD não encontrou mudanças em recursos/seeds de produção, controllers, DTOs, serviços de login/JWT, configuração, serviço de catálogo, artefatos das Features 001–003 ou contratos da Feature 004. OrderTestSupportTest confirma preservação dos 52 produtos, contas e vínculos da seed após limpeza de fixtures, além de isolamento entre contextos. |

Conclusão: fundação coerente com data-model.md e decisões D1/D2/D7 no escopo de T010, sem divergência pendente que exija implementação. A ausência de perfil não impede resolução/login; autorização de pedidos pertence às tarefas seguintes. A configuração compartilhada ainda usa o default de open-in-view nos outros contextos; a prova do resolver desativa essa opção explicitamente e demonstra que ele não depende dela, sem alterar configuração global.

### Execuções realmente observadas

Comandos na raiz Windows/PowerShell, HEAD acima e fontes de produção/teste inalterados. Horários de 2026-10-07, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 16:44:53 → 16:44:55 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime C:\Program Files\Zulu\zulu-21 | 0 |
| Validação conjunta, comando abaixo | 16:45:02 → 16:46:09 | 76 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS | 0 |

```powershell
.\mvnw.cmd '-Dtest=AuthenticatedAccountServiceTest,AuthenticatedAccountServiceIntegrationTest,OrderTestSupportTest,UserAccountRoleRepositoryTest,UserAccountRepositoryTest,LoginControllerTest,AuthenticationServiceTest,AuthenticationInternalFailureTest,AuthenticationSensitiveLoggingTest,JwtTokenServiceTest,JwtConfigurationTest,PasswordEncoderConfigurationTest,GlobalExceptionHandlerRegressionTest' test
```

Totais por classe nesta execução: AuthenticatedAccountServiceTest 8; AuthenticatedAccountServiceIntegrationTest 6; UserAccountRoleRepositoryTest 11; OrderTestSupportTest 5; UserAccountRepositoryTest 4; LoginControllerTest 13; AuthenticationServiceTest 4; AuthenticationInternalFailureTest 4; AuthenticationSensitiveLoggingTest 4; JwtTokenServiceTest 1; JwtConfigurationTest 6; PasswordEncoderConfigurationTest 1; GlobalExceptionHandlerRegressionTest 9. Todas com zero falhas/erros/ignorados. Os 62 casos de regressão incluem os 11 de perfis e os 5 de suporte; somados aos 14 de resolução, totalizam 76, sem contagem duplicada.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t010-2026-10-07/`: wrapper-version.txt / wrapper-version-result.json; foundation-regressions.txt / foundation-regressions-result.json; reports-summary.json. XMLs/TXTs das treze classes efetivamente executadas copiados de target/surefire-reports para foundation-regression-reports/, com timestamps conferidos como posteriores ao início desta execução; nenhum relatório antigo foi somado. preserved-before.json/baseline.json capturam hashes e revisão inicial; quickstart-before.md/tasks-before.md preservam os documentos; contracts-seeds-review.json registra a comparação histórica. preservation-before-docs.json confirmou os 180 arquivos capturados inalterados após testes e antes do registro documental: 127 rastreados e 53 artefatos históricos de T008/T009.

### Limites e fechamento

Esta é uma execução local selecionada, não a suíte completa nem CI. Mocks comprovam o contrato unitário, enquanto os testes JPA/H2 e de integração sustentam as provas de constraints, fetch e proxy. Não se afirma validação de outro banco, migração de produção, falha física de conexão, falha de abertura/conclusão de transação, validação criptográfica no resolver, proteção HTTP de pedidos ou rollback de compras. Os warnings de OSIV nos contextos de regressão não invalidam a prova específica com OSIV desativado. Falhas esperadas de inicialização usadas pelos testes de configuração JWT não são falhas da execução: os relatórios finais registram zero erros.

T010 marcada somente após revisão e validação aprovadas. T001–T010 [X]; T011–T055 permanecem abertas (45). Histórico anterior do quickstart, incluindo RED de T008 e GREEN de T009, preservado integralmente por acréscimo; tasks.md altera somente a checkbox T010. Diff revisado e `git diff --check` com configuração normal do repositório sem erros de whitespace. Checagem adicional `git -c core.autocrlf=false diff --check` retornou 2 ao apontar CRLF nas linhas históricas 392/394 de quickstart.md; esses bytes já existiam na cópia inicial e foram preservados, não corrigidos nesta tarefa. A checagem no-index entre quickstart-before.md e quickstart.md, com autocrlf=false, verifica somente o acréscimo sem diagnósticos de whitespace (código 1 indica diferença de conteúdo). Resultados dos comandos ficam em diff-check-result.json e diff-check-final.json. Comparação de preservação final mantém os outros 125 arquivos rastreados e os 53 artefatos anteriores inalterados, com índice vazio. Raiz/branch/HEAD preservados; Git final contém somente quickstart.md e tasks.md modificados.

A fundação permite resolver accountId/perfis do banco como valores mínimos utilizáveis após a transação, com ausência de conta distinta de falha interna. US1 construirá o registro da compra: agregado/itens, snapshot e cálculo exato, DTOs, autenticação/autorização do recurso e POST /orders com 201 após commit, vinculado à própria conta e com novo pedido por envio. Nada de T011+ foi executado ou implementado nesta etapa. U1/U2 não aplicadas; sem stage, commit, push ou troca de branch.

## Execução exclusiva de T011 — contrato integrado US1.1–10 (RED) — 2026-10-07

Escopo autorizado: somente criação/execução de OrderRegistrationIntegrationTest, com parada antes de T012. Conferidos por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (checkpoint T010 esperado), checkout inicialmente limpo e índice vazio. Não havia alterações preexistentes.

Instruções do projeto e skill speckit-implement, constituição, T011/dependência T010, cenários US1.1–10, plano/modelo/pesquisa, contrato OpenAPI/README, procedimentos do quickstart e suporte existente conferidos. Pré-requisitos do Spec Kit retornaram código 0 e FEATURE_DIR absoluto correto. Checklist requirements.md: 16 marcados, nenhum aberto, sem edição. Ignore existente cobre target/artefatos Java e permanece preservado; extensions.yml ausente antes/depois, sem hooks.

### Teste criado antes da implementação

Criado somente `src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java`: SpringBootTest full-context, AutoConfigureMockMvc com addFilters=true, perfil test, OrderTestConfig/OrderTestFixture de T007, H2 próprio por DynamicPropertySource e open-in-view=false. Sem mocks, jwt()/WithMockUser, transação externa na classe/métodos ou alteração da segurança de produção. A impressão automática de requests/responses está desativada para não registrar JWT/senha nos relatórios de falha.

Preparação usa apenas tb_product, tb_user_account e tb_user_account_role já existentes. Perfis provisionados explicitamente; login por JSON bruto em POST /login exige 200 e exatamente accessToken/tokenType. JWT real é decodificado pelo decoder existente, verificando HS256, subject da conta e ausência de roles/hash/segredo. Bearer é enviado no POST /orders por MockMvc com filtros existentes ativos; a chain de pedidos ainda não foi implementada. Não foi criado domínio, schema, repositório, serviço, filtro, handler ou endpoint de pedidos.

Nove casos executáveis contêm as exigências dos dez cenários:

| Casos escritos | Critérios exigidos para o GREEN futuro |
| --- | --- |
| Três invocações de shouldRegisterExactlyOneCompleteOrderForTheAuthenticatedAccount — US1.1–4 | CLIENT, ADMIN isolado e ambos; titular autenticado distinto de outra conta de preparação; um único pedido completo por confirmação; 10.00×2 + 7.50×3 = 42.50, subtotais 20.00/22.50; conta/id positivos Long, instante, estado, todos os itens e persistência correspondente. |
| shouldUseTheCurrentPositivePriceChangedBeforePurchase — US1.5 | Mesmo JSON de carrinho preparado antes da alteração; preço confirmado por fixture/JDBC muda de 10.00 para 11.25 antes do POST; snapshot 11.25, subtotal 22.50 e total 45.00, sem preço vindo do consumidor. |
| shouldKeepStoredPricesSubtotalsAndTotalAfterProductPriceChanges — US1.6 | Depois de 201, preços vigentes dos produtos mudam para 99.99/25.00; leitura independente deve conservar snapshots 10.00/7.50 e quantidades, subtotais 20.00/22.50 e total 42.50; sem GET /orders. |
| shouldConfirmOnlyOrderDataWithoutLocationOrCartClearingPromise — US1.7 | Shape exato da confirmação, sem Location de consulta, estado de carrinho ou promessa de esvaziamento; não se testa interface/carrinho no backend. |
| shouldGiveDistinctPurchasesTheirOwnIdsAndCreationInstants — US1.8 | Duas compras distintas para a mesma conta; ids diferentes e instantes próprios; dois agregados completos visíveis, sem exigir ids consecutivos. |
| shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded — US1.9 | Descartar o corpo da primeira resposta após conferir 201; observar o primeiro pedido pelo JDBC, repetir o mesmo JSON e exigir outro id/instante e ambos os pedidos completos, primeiro preservado. |
| shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation — US1.10 | Consumir/validar a primeira confirmação, repetir o mesmo JSON e exigir outro id/instante, dois pedidos completos e preservação do primeiro. |

Helper comum exige 201 antes de interpretar a confirmação e antes das assertivas JDBC de pedidos. Shape da resposta é exatamente id/accountId/createdAt/status/items/total; cada item contém somente productId/quantity/unitPrice/subtotal. Estado WAITING_PAYMENT, valores monetários textuais com duas casas, ids/quantidades como inteiros JSON. Produtos são criados em ordem inversa à enviada, para detectar reordenação por id. Sem nome, hash, segredo ou perfis na confirmação.

Clock de teste é ajustado após o login para o segundo atual mais 123 ms e nanos adicionais; expectativa de criação usa truncamento a milissegundos e texto UTC com Z. Avanço de um segundo distingue os registros subsequentes sem envelhecer artificialmente o JWT do login. Isso prepara a prova futura do Clock injetável e precisão; não altera o emissor/decoder compartilhado.

Leitura de pedidos/itens usa DriverManagerDataSource com conexões novas para o mesmo H2, fora do EntityManager da aplicação e sem transação externa; asserts verificam também ausência de transação/recurso de persistência ligado à thread. Consulta por buyer_id e ordenação por position conferem identidade e ordem, snapshots/quantidades são fonte do cálculo histórico, sem exigir colunas subtotal/total que o modelo não prevê. Nenhuma consulta/assertiva a tb_order/tb_order_item ocorre antes do sucesso HTTP. Limpeza confere existência das tabelas via information_schema e remove dependências somente das contas próprias quando existirem; depois limpa fixtures existentes. Tabelas ausentes não causaram erro de limpeza neste RED.

### Execuções realmente observadas

Comandos na raiz Windows/PowerShell, HEAD acima e somente o teste novo no diff de código. Horários de 2026-10-07, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 17:03:07 → 17:03:08 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime C:\Program Files\Zulu\zulu-21 | 0 |
| `.\mvnw.cmd '-Dtest=OrderRegistrationIntegrationTest' test` | 17:03:14 → 17:04:00 | RED: 9 testes, 9 falhas, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |

Compilação de produção já atualizada; compilação de teste concluída com javac release 21 para 23 arquivos. Contexto Spring iniciou com sucesso. XML/TXT confirmam nove AssertionErrors com mensagem `Status expected:<201> but was:<404>`, no helper submit, linha 274. Todos os casos chegaram ao primeiro POST após preparar produtos/conta/perfis, conferir filtros existentes e completar login 200/validação do JWT. A causa comportamental é ausência de POST /orders (controllers de produção continuam somente com login e produtos). Não houve erro de compilação, startup, fixture, SQL ou limpeza contado como RED.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t011-2026-10-07/`: wrapper-version.txt / wrapper-version-result.json; order-registration-red.txt / order-registration-red-result.json; reports-summary.json; OrderRegistrationIntegrationTest-executed.java. XML/TXT da única classe executada copiados de target/surefire-reports para order-registration-red-reports/, sem agregar relatórios antigos. baseline.json/preserved-before.json e quickstart-before.md/tasks-before.md preservam revisão, hashes e documentos iniciais; preservation-before-docs.json confirmou os 448 arquivos capturados inalterados após a execução, antes de registrar este checkpoint. Fonte final conferido por SHA256 igual à cópia executada.

### Limites e fechamento

Este RED demonstra ausência do 201 com preparação/compilação/contexto válidos. Assertivas posteriores de shape, titular, tempo, preços, persistência, segundo envio e preservação do primeiro ainda não foram alcançadas: são contratos escritos para o GREEN futuro, não comportamentos comprovados nesta execução. Não se afirma resolução/autorização JWT no fluxo de pedidos, commit/rollback de pedidos, existência de suas tabelas ou aprovação funcional de US1. Resposta descartada será simulada pelo não consumo do corpo, não por falha física de transporte. A ausência de promessa de esvaziamento é verificada no contrato de resposta; não representa entrega de carrinho/interface.

T011 concluída como tarefa de escrever/executar testes e obter RED comportamental, marcada somente após conferir execução e relatórios. T001–T011 [X]; T012–T055 permanecem abertas (44). Histórico anterior deste quickstart preservado por acréscimo; tasks.md altera somente a checkbox T011. Produção, suporte/fixtures anteriores, seeds, contratos e evidências anteriores preservados. Teste novo/diff documental revisados e whitespace conferido; índice vazio, raiz/branch/HEAD preservados. Estado final: quickstart.md e tasks.md modificados; OrderRegistrationIntegrationTest.java novo/não rastreado.

Somente essa classe de teste foi executada; regressões, suíte completa e CI não executadas. Nenhuma tarefa T012+, U1/U2, stage, commit, push ou troca de branch. O RED permanece intencional para implementação nas tarefas seguintes, que não foram iniciadas aqui.

## Execução exclusiva de T012 — contrato unitário do agregado (RED) — 2026-10-07

Escopo autorizado: somente T012, com parada antes de T013. Conferidos por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (checkpoint T010 esperado), índice vazio. Trabalho local preexistente de T011: quickstart.md/tasks.md modificados e OrderRegistrationIntegrationTest.java não rastreado. Preservado integralmente; os documentos recebem apenas acréscimo deste checkpoint/checkbox T012.

Instruções do projeto/skill speckit-implement, constituição, T012/dependência T011, modelo do agregado, regras FR-002/006/008/009 e decisão D5 conferidos, com plano/contrato/quickstart como contexto. Pré-requisitos do Spec Kit retornaram código 0 e FEATURE_DIR absoluto correto. Checklist requirements.md: 16 marcados, nenhum aberto, sem edição. Ignore existente suficiente e preservado; extensions.yml ausente antes/depois, sem hooks.

### Teste escrito primeiro e suporte mínimo

Criado primeiro `src/test/java/com/devsuperior/dscommerce/entities/OrderAggregateTest.java`, teste JUnit unitário sem SpringBootTest, mocks, banco, contexto de aplicação ou transação externa. Preparação usa UserAccount/Product existentes em memória. Mudança de preço usa ReflectionTestUtils somente sobre o objeto Product de teste, sem alterar sua classe ou API de produção.

O contrato exige construção com buyer, createdAt e lista completa; estado WAITING_PAYMENT; rejeição por IllegalArgumentException de lista nula, vazia ou com item nulo entre linhas válidas; vínculo dos itens ao pedido e posições zero-based na ordem recebida, usando ids de produtos fora de ordem. Exige também proteção contra clear/add/set na lista original e na lista devolvida por getItems; nesta última, as tentativas devem lançar UnsupportedOperationException sem mudar o agregado.

Valores históricos de preço/quantidade devem permanecer após mudança do preço de Product. Subtotais devem ser exatos: 10.00×2=20.00, 7.50×3=22.50 e 0.10×3=0.30; o teste de subtotal fornece produto com preço atual 999.99 e snapshot próprio para detectar dependência incorreta de Product.price. Total esperado dos dois itens é 42.50. Caso adicional muda os dois produtos para 99.99/25.00 depois da construção e exige snapshots, quantidades, subtotais e total históricos conservados.

Somente depois do teste foram preparados três tipos concretos compiláveis em src/main/java/com/devsuperior/dscommerce/entities/:

- Order.java: construtores protegido sem argumentos e público (UserAccount, Instant, List<OrderItem>), atribuição simples dos argumentos e acessores. Lista recebida é mantida diretamente; getStatus/getTotal retornam provisoriamente null.
- OrderItem.java: construtores protegido sem argumentos e público (Product, Integer, BigDecimal), atribuição simples de produto/quantidade/preço fornecidos e acessores. getOrder/getPosition/getSubtotal retornam provisoriamente null.
- OrderStatus.java: somente a constante WAITING_PAYMENT.

Sem Entity, anotações/imports/mapeamentos JPA, registro Spring, validação das entradas, construção de vínculos/posições, cópia defensiva, proteção da coleção, inicialização de estado ou cálculos. Não implementadas as invariantes/regras de T017 ou persistência de T018. Product, UserAccount e trabalho de T011 não foram alterados.

### Execuções realmente observadas

Comandos na raiz Windows/PowerShell, HEAD acima com somente os quatro novos arquivos Java de T012 adicionais ao trabalho preexistente de T011. Horários de 2026-10-07, offset -03:00:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 17:26:05 → 17:26:07 | Maven 3.9.16 pelo Wrapper; Java 21.0.12.1 Azul Systems, Inc.; runtime C:\Program Files\Zulu\zulu-21 | 0 |
| `.\mvnw.cmd '-Dtest=OrderAggregateTest' test` | 17:26:13 → 17:26:38 | RED: 18 testes, 17 falhas, 1 aprovado, 0 erros, 0 ignorados; BUILD FAILURE esperado | 1 |

Compilação concluída com javac release 21: 32 arquivos de produção e 24 de teste. Somente OrderAggregateTest foi executado; compilar o teste de T011 não equivale a executá-lo. Nenhum contexto Spring/startup de aplicação foi tentado neste teste unitário.

| Grupo de assertivas | Resultado observado e causa |
| --- | --- |
| Criação completa — 1 caso | Falha: buyer, instante e lista fornecidos são conservados pelo suporte; estado esperado WAITING_PAYMENT veio null. |
| Listas inválidas — 3 casos | Falhas: construtor aceita lista nula/vazia/com item nulo; nenhuma exceção foi lançada. |
| Vínculo/posição — 2 casos | Falhas: getOrder e getPosition retornam null, não o pedido e posições 0/1. |
| Mutação externa — 6 casos | Falhas: clear/add/set na lista original altera os itens do pedido; os mesmos métodos na lista exposta também são aceitos, sem UnsupportedOperationException. |
| Cálculos históricos — 5 casos | Falhas: três subtotais, total 42.50 e caso após mudança dos produtos recebem null nos acessores de cálculo; assertivas reportam valor ausente, sem NullPointerException de preparação. |
| Conservação simples de preço/quantidade — 1 caso | Aprovado: argumentos armazenados continuam 10.00/2 após mudar Product.price para 99.99. Não foi forçado RED de um comportamento já atendido por atribuição/acessores básicos. |

XML/TXT registram 12 java.lang.AssertionError e 5 org.opentest4j.AssertionFailedError, todos como falhas de assertiva; zero erros de execução. Nenhuma falha de compilação, startup, fixture, descoberta JPA ou preparação foi usada como RED. Assertivas posteriores às primeiras falhas permanecem escritas para T017; não se afirma que já passaram.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t012-2026-10-07/`: wrapper-version.txt / wrapper-version-result.json; order-aggregate-red.txt / order-aggregate-red-result.json; reports-summary.json; quatro cópias em executed-sources/. XML/TXT da única classe executada copiados de target/surefire-reports para order-aggregate-red-reports/, sem somar relatórios antigos. baseline.json/preserved-before.json e quickstart-before.md/tasks-before.md preservam revisão, hashes e documentos locais iniciais. preservation-before-docs.json confirmou os 464 arquivos capturados inalterados após o teste e antes de registrar este checkpoint, incluindo o teste e os 15 artefatos históricos RED de T011. Os quatro fontes finais coincidem por SHA256 com suas cópias executadas.

### Fechamento e limites

T012 cumprida como escrita/execução dos testes e demonstração de RED comportamental, marcada somente após conferir resultados/causas. T001–T012 [X]; T013–T055 permanecem abertas (43). Histórico anterior do quickstart preservado integralmente por acréscimo, incluindo T011; tasks.md altera nesta etapa somente a checkbox T012, mantendo T011 [X]. Fontes novos e diff revisados; git diff --check e conferência no-index do whitespace dos arquivos novos sem erros (código 1 de no-index indica conteúdo novo). Comparação de preservação final mantém os outros 462 arquivos capturados inalterados, incluindo Product, teste/evidências de T011 e suporte anterior.

Prova exclusivamente unitária do contrato de domínio provisório: sem JPA/schema, persistência, commit/rollback, transação por proxy, HTTP ou aprovação funcional de pedidos. O caso aprovado não torna o agregado completo: validação, vínculos, posições, proteção de coleção, estado e cálculos permanecem ausentes para T017. T011 não foi repetida; regressões, suíte completa e CI não executadas. T013+ e U1/U2 não executadas/aplicadas; sem stage, commit, push ou troca de branch.

Estado final: quickstart.md e tasks.md modificados; OrderAggregateTest.java, Order.java, OrderItem.java e OrderStatus.java novos/não rastreados. OrderRegistrationIntegrationTest.java continua não rastreado e byte a byte preservado de T011. Índice vazio; raiz/branch/HEAD mantidos. O RED permanece intencional para as tarefas de implementação posteriores, que não foram iniciadas aqui.

## Checkpoint T013 — persistência: testes escritos e RED por schema ausente (2026-10-07)

### Escopo e estado inicial

Executada exclusivamente T013 da Feature 004, sem iniciar T014. Conferidos raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado) e status pelo Git; índice vazio. Preexistiam quickstart.md/tasks.md modificados e os cinco fontes não rastreados de T011/T012: OrderRegistrationIntegrationTest.java, OrderAggregateTest.java, Order.java, OrderItem.java e OrderStatus.java. Preservados integralmente.

Conferidas instruções do projeto/skill speckit-implement, constituição, T013/dependência T012 e requisitos de persistência de data-model.md, com spec/research/plan como contexto. Pré-requisitos do Spec Kit: código 0, FEATURE_DIR correto; checklist requirements.md: 16 marcados, nenhum aberto. Ignore suficiente e preservado; extensions.yml ausente, sem hooks.

### Cobertura preparada

Criado somente `src/test/java/com/devsuperior/dscommerce/repositories/OrderRepositoryTest.java` no código. Contexto completo SpringBootTest com perfil test, open-in-view=false, configuração/fixtures T007 e H2 em memória isolado por UUID. Não importa nem cria a interface OrderRepository. Não adiciona entidade, mapeamento, tabela, migração ou implementação de T017/T018; Product, UserAccount e os tipos provisórios de T012 permanecem inalterados.

Cada caso confere primeiro a existência das tabelas PUBLIC por JDBC. Os dois casos específicos de tabela verificam existência antes dos respectivos metadados; os outros 21 exigem ambas as tabelas e todos os metadados antes de preparar conta/produtos, inserir pedidos ou persistir Order. Limpeza condicionada à existência das tabelas, restrita às linhas próprias; a falha de schema não cria fixtures de pedidos nem provoca falha de limpeza.

| Grupo | Casos | Assertivas preparadas após a barreira de schema |
| --- | ---: | --- |
| Tabelas/metadados | 2 | Colunas previstas, PK BIGINT/IDENTITY, NOT NULL, FKs buyer/order/product, Instant, enum textual, DECIMAL(19,2), pares únicos e os três CHECKs nomeados. |
| Round-trip EntityManager/JDBC | 1 | Agregado completo com duas linhas, cascade de persistência, id gerado, flush/clear/find, conta, Instant com .123Z, WAITING_PAYMENT, vínculos, snapshots, posições 0/1 em ordem de entrada e total 42.50; leitura JDBC no mesmo teste. Produtos criados em ordem de ids inversa à ordem das linhas. |
| Identidade SQL | 1 | Ids gerados positivos e distintos em pedidos/itens, sem informar id na inserção e sem exigir sequência contínua. |
| Nulos SQL | 8 | buyer_id/created_at/status do pedido; order_id/product_id/position/quantity/unit_price do item. SQLState H2 23502 esperado. |
| Vínculos inválidos SQL | 3 | Conta, pedido e produto inexistentes; SQLState 23506 esperado. |
| Unicidades SQL | 2 | Rejeição de (order_id, product_id) e (order_id, position), isolando a causa; mesmos produto/posição permitidos em pedidos diferentes. SQLState 23505 esperado. |
| CHECKs SQL | 5 | quantity 0/-1; position -1; unit_price 0/-0.01. SQLState 23513 e nome físico da constraint na SQLException. |
| Limites válidos SQL | 1 | position 0, quantity positiva até Integer.MAX_VALUE e preços 0.01/99999999999999999.99, com leitura decimal e ordem por position. É capacidade da coluna/constraints, não prova de compra válida ou cálculo agregado no teto. |

CHECKs exigidos: `ck_order_item_quantity_positive`, `ck_order_item_position_nonnegative`, `ck_order_item_unit_price_positive`; conferência do nome e expressão em INFORMATION_SCHEMA. Rejeições usam PreparedStatement/JdbcTemplate diretamente e exigem DataIntegrityViolationException com causa SQLException e SQLState correspondente. Não usam falha de Bean Validation/ORM como prova de integridade SQL. Os pedidos vazios inseridos pelos futuros casos SQL servem apenas à verificação de linhas; a invariável de agregado não vazio permanece no domínio/T017, sem trigger solicitado.

O round-trip usa TransactionTemplate para flush/mapeamento, com rollback-only de teste e transação encerrada antes da limpeza. A leitura JDBC dessa transação não é independente nem comprova commit, rollback de negócio ou persistência após retorno HTTP. Não há transação externa envolvendo toda a classe; nenhuma afirmação de prova transacional futura decorre deste RED.

### Comandos e resultados reais

Windows/PowerShell na raiz, revisão acima, em 2026-10-07 (offset -03:00):

| Comando | Referência temporal observada | Resultado real |
| --- | --- | --- |
| `.\mvnw.cmd --version` | 17:39:46 → 17:39:48 | Código 0; Maven Wrapper 3.9.16, Java 21.0.12.1 Azul Systems, runtime C:\Program Files\Zulu\zulu-21. |
| `.\mvnw.cmd '-Dtest=OrderRepositoryTest' test` — tentativa inicial | 17:39:55 → 17:40:15 | Código 1 em testCompile: ambiguidade de inferência AssertJ/queryForObject na leitura de Instant. Nenhum teste executado; não constitui RED. Corrigido somente o teste, atribuindo o resultado a variável Instant antes da assertiva. |
| Mesmo comando — fonte corrigido | Contexto iniciado com sucesso às 17:41:41.738; XML escrito às 17:41:44.098; tempo Surefire 16.311 s | Compilação válida; somente OrderRepositoryTest executado: 23 testes, 23 falhas de assertiva, 0 erros, 0 ignorados. RED comportamental por ausência das tabelas. Código nativo final do Maven não capturado, conforme limitação abaixo. |

Na tentativa corrigida, o console registra compilação dos 25 fontes de teste com javac release 21 e inicialização válida de EntityManagerFactory/contexto Spring/H2 (`jdbc:h2:mem:order-test-e7b0fef1-5fcd-49f4-9edc-380651f639fe`). Compilar T011/T012 não significa executá-las. Todos os 23 casos estão identificados no XML e TXT atuais da classe alvo; não foram somados relatórios de outras classes.

Causa observada: PUBLIC contém somente TB_CATEGORY, TB_PRODUCT, TB_PRODUCT_CATEGORY, TB_USER_ACCOUNT e TB_USER_ACCOUNT_ROLE. Faltam TB_ORDER e TB_ORDER_ITEM. Os dois casos de tabela falham em requireTable; os outros 21 em requireSchema. Todas as falhas são java.lang.AssertionError, antes de fixtures/inserções de pedidos ou EntityManager.persist de Order. Não há erro de startup, preparação, descoberta JPA ou limpeza usado como RED.

Limitação da captura: ErrorActionPreference=Stop do PowerShell interrompeu a coleta do console no aviso ByteBuddy sobre carregamento dinâmico de agente Java, gerando NativeCommandError e código 1 do processo de captura. Isso não é a causa das falhas de teste nem equivale ao código nativo final do Maven. O console preservado termina após o startup; o resumo final/BUILD e horários exatos de início/fim dessa invocação não foram capturados. Os relatórios Surefire completos comprovam, separadamente, os 23 casos e respectivas falhas no schema. Não foi necessária nova execução para obter essa evidência.

### Evidências e assertivas bloqueadas

Artefatos locais ignorados pelo Git: `target/harness-validation/feature-004-t013-2026-10-07/`. Contém wrapper-version.txt/result.json; order-schema-compile-attempt.txt/result.json e fonte da tentativa inicial; order-schema-red.txt/result.json (inclui explicitamente a limitação da captura/código nativo ausente); OrderRepositoryTest-executed.java; reports-summary.json; XML/TXT copiados para order-schema-red-reports/. baseline.json, preserved-before.json, quickstart-before.md e tasks-before.md registram o estado inicial. preservation-before-docs.json confirma os 486 arquivos capturados inalterados antes deste acréscimo, inclusive fontes e 15/18 artefatos históricos de T011/T012. SHA256 do teste final coincide com a cópia executada corrigida.

Ficaram bloqueadas por schema ausente todas as verificações posteriores de colunas/tipos, PK IDENTITY, FKs, NOT NULL, unicidades, CHECKs, inserções e rejeições SQL, limites positivos e round-trip/mapeamento JPA/JDBC. Foram escritas, mas não atingidas nem aprovadas. Não se afirma que o banco já assegura as constraints, que existe cascade/ordenação correta ou que as entidades provisórias estão mapeadas. O RED registra precisamente essa ausência para futura T018, após T017.

### Fechamento

T013 cumprida como escrita/execução do teste e RED esperado, marcada somente após conferir os relatórios. T001–T013 [X]; T014–T055 abertas (42). Histórico do quickstart preservado por acréscimo; tasks.md altera nesta etapa exclusivamente a checkbox T013. Diff/fontes novos e whitespace conferidos; comparação final preserva os outros 484 arquivos capturados e verifica o histórico documental. Sem implementação de persistência/domínio, repetição de T011/T012, suíte completa ou CI, T014+, U1/U2, stage, commit, push ou troca de branch.

Estado final: quickstart.md/tasks.md modificados e OrderRepositoryTest.java novo/não rastreado, somados aos cinco fontes preexistentes não rastreados de T011/T012, preservados byte a byte. Índice vazio; raiz, branch e HEAD mantidos. A persistência de pedidos continua ausente, conforme o RED observado.

## Checkpoint T014 — contrato unitário de OrderService e RED (2026-10-07)

### Escopo e estado inicial

Executada exclusivamente T014 da Feature 004, encerrada antes de T015. Git conferido antes das edições: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado), índice vazio. Preexistiam quickstart.md/tasks.md modificados e seis fontes não rastreados de T011–T013: OrderRegistrationIntegrationTest.java, OrderAggregateTest.java, OrderRepositoryTest.java, Order.java, OrderItem.java e OrderStatus.java. Arquivos e evidências anteriores preservados integralmente.

Consultados AGENTS.md, skill speckit-implement, constituição, tasks/plan, T014/dependência T012, decisões D3–D6 do research, data-model, spec/contrato e quickstart. Pré-requisitos do Spec Kit: código 0, FEATURE_DIR absoluto correto. Checklist requirements.md: 16/16 marcados, zero abertos, sem edição. Ignore existente suficiente; extensions.yml ausente nas conferências antes/depois, sem hooks.

### Teste escrito primeiro e suporte mínimo

Criado primeiro `src/test/java/com/devsuperior/dscommerce/services/OrderServiceTest.java`. test-written-first.json e cópia do fonte registram que os seis tipos de suporte ainda estavam ausentes. Testes JUnit/Mockito sem SpringBootTest, MockitoExtension, contexto Spring, banco ou transação. Instanciação direta de OrderService; mocks de ProductRepository, OrderRepository e UserAccountRepository, conta CLIENT e Clock fixo. Product existente preparado em memória; ReflectionTestUtils muda somente o preço do objeto de teste, sem alterar Product ou seu contrato.

| Cobertura preparada | Casos | Expectativa |
| --- | ---: | --- |
| Preços vigentes positivos | 3 | 0.01, 7.50 e 12345.67, quantidade 1; snapshot no agregado e valores monetários da confirmação. Não é cobertura da rejeição de zero/negativo, que pertence às tarefas posteriores. |
| Busca em lote restrita | 1 | Uma chamada findAllById contendo exclusivamente os dois ids enviados, sem duplicá-los; nenhuma outra interação com ProductRepository (catálogo, consultas individuais ou com categorias). Mocks verificam seleção da API/argumentos, não SQL ou carregamento JPA. |
| Retorno fora de ordem | 1 | Repositório devolve produtos 11/91; solicitação usa 91/11. Agregado e resposta preservam ordem, quantidades 2/3, preços 10.00/7.50 e posições 0/1. |
| Preço alterado antes da leitura | 1 | Objeto Product passa de 10.00 para 12.50 antes da chamada; snapshot 12.50, quantidade 2, subtotal/total 25.00. |
| Snapshot após construção | 1 | Answer do mock saveAndFlush altera preços para 99.99/25.00 antes do retorno/mapping; agregado e resposta devem conservar preços 10.00/7.50, quantidades e valores 20.00/22.50/42.50. É intercalação simulada em memória, não concorrência real ou prova de transação. |
| Normalização exata | 1 | Preço 1.000 copiado como BigDecimal 1.00 (inclusive escala 2), quantidade 3, subtotal/total 3.00 e strings com duas casas; Product continua com preço original 1.000. |
| Aritmética exata | 1 | 10.00×2 + 7.50×3 = 42.50; subtotais/total históricos no agregado e strings da resposta, sem float/double, frete ou acréscimos. |
| Mesmo objeto de entrada enviado duas vezes | 1 | Dois saveAndFlush, dois agregados e itens distintos por identidade em memória, duas consultas de preços; totais 42.50. Sem geração de id pelo mock ou alegação de commit. |
| Envio igual após mudança de preço | 1 | Nova leitura/agregado com 12.50×2=25.00; primeiro agregado/resposta continuam com 10.00×2=20.00, sem reaproveitamento entre chamadas. |

Somente depois do teste foram preparados os tipos indispensáveis à compilação:

- `src/main/java/com/devsuperior/dscommerce/services/OrderService.java`: classe concreta, construtor (ProductRepository, OrderRepository, UserAccountRepository, Clock) com corpo vazio; register(OrderRequestDTO, AuthenticatedAccountPrincipal) retorna provisoriamente null, sem chamadas a dependências.
- `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java`: interface Java comum, somente Order saveAndFlush(Order). Sem herança Spring Data, RepositoryDefinition, Repository ou registro de bean.
- `src/main/java/com/devsuperior/dscommerce/dto/OrderRequestDTO.java` e `OrderItemRequestDTO.java`: records estruturais com items e productId/quantity.
- `src/main/java/com/devsuperior/dscommerce/dto/OrderResponseDTO.java` e `OrderItemResponseDTO.java`: records estruturais com os campos previstos de confirmação e strings monetárias, sem construtor de mapping ou regras.

Nenhum Service/Component/Bean/Transactional, consulta, validação, cálculo, normalização, snapshot, cópia defensiva, mapping final, endpoint ou segurança foi implementado. Sem Entity, JPA ou alteração de scan. ProductRepository, Product, UserAccount e domínio provisório de T012 permanecem byte a byte inalterados. T017–T020 não foram antecipadas.

### Execução real e causas do RED

Comandos Windows na raiz, revisão acima, em 2026-10-07 (offset -03:00):

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 18:01:50.827 → 18:01:52.565 | Maven Wrapper 3.9.16; Java 21.0.12.1 Azul Systems, runtime C:\Program Files\Zulu\zulu-21. | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceTest" test` | 18:01:52.674 → 18:02:21.171 | RED: 11 testes, 11 falhas, 0 erros, 0 ignorados; BUILD FAILURE esperado por falhas de teste. | 1 |

Única execução do alvo, compilação válida com javac release 21 (38 fontes de produção, 26 de teste). Somente OrderServiceTest aparece como classe executada; compilação dos testes anteriores não equivale à sua execução. Surefire: 3.738 s; Maven: 23.398 s. Nenhum contexto/startup Spring foi tentado.

Dez casos falharam com java.lang.AssertionError na assertiva explícita de confirmação não nula: register retorna null porque a resolução/orquestração do pedido está ausente. O caso da consulta em lote falhou com Wanted but not invoked: findAllById esperado nunca foi chamado, sem qualquer interação no mock ProductRepository. XML/TXT registram todos como failures, não errors. Não foi utilizado erro de compilação, preparação, startup ou fixture como RED.

As assertivas posteriores de preços, normalização, snapshots, ordem, cálculos, saveAndFlush e identidade distinta permanecem escritas, mas não foram atingidas após a primeira falha de cada caso. Os callbacks simulados de mudança de preço não foram alcançados pelo suporte provisório. Não se afirma que esses comportamentos já funcionam ou foram comprovados. O teste prepara seu contrato para T017/T019/T020.

### Captura, preservação e limites

Captura feita por `cmd.exe /d /c`, redirecionando stdout e stderr juntos para arquivo, com código real do processo colhido imediatamente por LASTEXITCODE e horários gravados. Saída completa inclui comandos/fases, compilação, aviso ByteBuddy/OpenJDK, todos os stack traces, resumo e BUILD FAILURE. Os avisos sobre agente dinâmico não interromperam esta captura nem causaram o RED; não foi necessária correção/repetição do alvo. A limitação de console da execução anterior de T013 permanece apenas como evidência histórica, sem reescrita.

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t014-2026-10-07/`: wrapper-version.txt/result.json; order-service-red.txt/result.json; reports-summary.json; XML/TXT copiados para order-service-red-reports/; sete fontes em executed-sources/; OrderServiceTest-written-first.java/test-written-first.json. baseline.json/preserved-before.json e quickstart-before.md/tasks-before.md capturam estado inicial. preservation-before-docs.json confirmou os 506 arquivos preexistentes inalterados, incluindo arquivos/evidências de T011–T013; fontes finais coincidem por SHA256 com as sete cópias executadas. Nenhum relatório antigo foi somado aos 11 testes desta execução.

Prova exclusivamente unitária em memória com mocks: não comprova consulta SQL, schema, persistência, ids gerados, commit/rollback, proxy transacional, concorrência real ou sucesso HTTP. Nenhuma regressão, suíte completa ou CI foi executada; T011–T013 não foram repetidas. O código provisório ainda não registra pedidos e não se torna componente da aplicação.

### Fechamento

T014 marcada somente após escrita/execução e conferência do RED comportamental. T001–T014 [X]; T015–T055 abertas (41). Quickstart atualizado somente por acréscimo, preservando o histórico; nesta etapa tasks.md altera exclusivamente a checkbox T014. Diff e os sete fontes novos revisados, git diff --check e whitespace dos novos arquivos sem erros. Conferência final mantém os outros 504 arquivos capturados inalterados, o prefixo documental anterior byte a byte e a alteração única de checkbox.

Estado final: quickstart.md/tasks.md modificados; os sete fontes desta etapa novos/não rastreados somam-se aos seis fontes preexistentes de T011–T013, preservados. Índice vazio; raiz/branch/HEAD mantidos. Encerrado antes de T015, sem U1/U2, stage, commit, push, troca de branch ou implementação adicional.

## Checkpoint T015 — contrato HTTP e cenários positivos de segurança em RED (2026-10-07)

### Escopo e estado inicial

Executada exclusivamente T015 da Feature 004, encerrada antes de T016. Conferidos pelo Git raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado) e índice vazio. Preexistiam quickstart.md/tasks.md modificados e 13 fontes não rastreados de T011–T014. Todos esses arquivos e suas evidências foram preservados; os documentos recebem somente acréscimo deste checkpoint/checkbox T015.

Instruções do projeto/skill speckit-implement e constituição consideradas; T015/dependência T012, spec US1.1–4/FR-001/003/007/010, contrato POST /orders, plan, research D1–D3 e modelo revisados. Pré-requisitos do Spec Kit: código 0, FEATURE_DIR correto. Checklist requirements.md: 16 marcados, zero abertos, sem edição. Ignore existente suficiente e preservado; extensions.yml ausente antes/depois, sem hooks.

### Testes escritos e preparação válida

Criados somente os dois fontes autorizados:

- `src/test/java/com/devsuperior/dscommerce/controllers/OrderControllerTest.java`: três testes com JSON bruto, SpringBootTest/MockMvc, filtros ativos e componentes reais existentes. Exigem primeiro 201, depois shape exato id/accountId/createdAt/status/items/total e productId/quantity/unitPrice/subtotal em cada item; ids como tokens numéricos inteiros positivos representáveis em Long, quantidades Integer, UTC com milissegundos, WAITING_PAYMENT e ordem enviada. Valores monetários devem ser strings decimais com exatamente duas casas e capacidade19,2, sem expoente/coerção numérica. Casos: 10.00×2 + 7.50×3 = 42.50 (produtos criados em ordem de ids inversa à enviada), preço/totais 99999999999999999.99 com quantidade 1 e 0.10×3=0.30. Sem Location de consulta.
- `src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java`: nove cenários, cruzando CLIENT, ADMIN isolado e ambos com JWT original do login, variante com name de outra conta existente/roles vazio e variante com esse outro name/roles somente ROLE_AUDITOR. Todos exigem 201 para o titular original de sub, usando os perfis autorizados existentes no banco. Name aponta para uma conta sem perfis, diferente da identificada por sub; os claims falsos não contêm permissão CLIENT/ADMIN.

OrderControllerTest.java contém também OrderHttpTestSupport, suporte abstrato compartilhado apenas entre esses dois testes, para reduzir duplicação de preparação/login/limpeza/assertivas. Não é componente Spring e não simula endpoint, autenticação ou resposta de pedido. Ambas as classes usam perfil test, open-in-view=false e OrderTestConfig/OrderTestFixture T007; cada DynamicPropertySource fornece seu próprio H2 isolado, sem alteração de configurações compartilhadas ou scan.

Preparação usa somente contas, perfis e produtos existentes. POST /login real deve responder 200 com exatamente accessToken/tokenType Bearer. O decoder real verifica cada JWT antes do envio; sub deve corresponder à conta da fixture e o token de login não deve conter roles. Clock de teste é alinhado ao iat do login mais 123 ms, dentro da validade do token, evitando que o instante inicial histórico de T007 invalide cenários positivos futuros.

As variantes são geradas por OrderTestFixture.signedToken, reutilizando encoder HS256 real e chave descartável do perfil test. Preservam os claims originais, incluindo sub/iat/exp; substituem somente name/roles. Decoder real aceita as variantes; assertivas conferem sub/iat/exp conservados e claims alterados. Perfis da conta titular e ausência de perfis da outra conta são verificados por JDBC antes do POST. Não foram usados jwt(), WithMockUser, decoder mock ou componentes fictícios de pedidos.

Sem transação externa de teste. Assertivas JDBC de pedidos no teste de segurança são escritas somente depois do sucesso HTTP/contrato: conexão independente deve observar buyer_id igual à conta de sub, exatamente um pedido desse titular e nenhum da conta apontada por name. Limpeza tem guardas de existência de tabelas e remove apenas linhas próprias, antes da limpeza de contas/produtos das fixtures. A ausência atual de tabelas não causa falha de preparação/limpeza.

### Comandos e resultados reais

Windows na raiz, revisão acima, 2026-10-07 (offset -03:00). Alvos executados serialmente, cada um uma única vez:

| Comando | Início → fim | Resultado real | Código |
| --- | --- | --- | --- |
| `.\mvnw.cmd --version` | 18:12:20.622 → 18:12:22.264 | Maven Wrapper 3.9.16, Java 21.0.12.1 Azul Systems, runtime C:\Program Files\Zulu\zulu-21. | 0 |
| `.\mvnw.cmd "-Dtest=OrderControllerTest" test` | 18:12:22.358 → 18:13:05.208 | 3 testes, 3 falhas, 0 erros, 0 ignorados; RED na expectativa de 201, resposta real 404; BUILD FAILURE por falhas de assertiva. | 1 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 18:14:08.097 → 18:14:40.956 | 9 testes, 9 falhas, 0 erros, 0 ignorados; RED na expectativa de 201, resposta real 404; BUILD FAILURE por falhas de assertiva. | 1 |

Compilação válida: a primeira execução compila os 28 fontes de teste com javac release 21; produção já estava compilada. Na segunda os fontes permanecem atualizados. Somente a classe selecionada é executada em cada comando; compilar testes anteriores não significa executá-los. Surefire registra 19.236 s/20.455 s e Maven 37.862 s/27.826 s, respectivamente.

Contextos inicializados com sucesso (OrderControllerTest às 18:13:01.277; OrderSecurityIntegrationTest às 18:14:34.649). H2 distintos: order-test-00fe7443-84e1-4a65-8316-57f2b1944f2f e order-test-1844ac5d-3043-4c08-9f98-17c1b9162829. Todos os casos alcançaram a expectativa de 201 após preparação, consulta de perfis, POST /login 200 e decode real; as seis variantes assinadas também foram aceitas pelo decoder e seus claims conferidos antes dessa expectativa. Nenhum erro de compilação, startup, fixture, login ou limpeza foi usado como RED.

Os 12 casos falharam com java.lang.AssertionError e mensagem Status expected:<201> but was:<404>, na assertiva HTTP de criação; a operação POST /orders permanece ausente. A falha não é um 401/403 atribuído a JWT ou perfil inválido. Não foi criado controller, chain, converter ou serviço Spring de pedidos, nem ativado JPA ou implementado T017–T022.

### Evidências, confidencialidade e limites

Captura completa de stdout/stderr por cmd.exe /d /c, código real do processo via LASTEXITCODE e horários gravados após cada comando. MockMvcPrint.NONE evita imprimir Authorization, corpos de login e tokens. Nenhum token/chave/senha foi registrado no quickstart ou nos logs/relatórios; verificação dos arquivos de console/XML/TXT não encontrou tokens JWT, chave do perfil de teste nem senhas das fixtures. O suporte também evita imprimir objetos Jwt/valores de token em falhas; LoggedAccount.toString omite essas informações. Avisos ByteBuddy/OpenJDK ficaram registrados sem interromper a captura e não causaram o RED.

Artefatos ignorados pelo Git em `target/harness-validation/feature-004-t015-2026-10-07/`: wrapper-version.txt/result.json; order-controller-red.txt/result.json e order-security-red.txt/result.json; resumos order-controller-reports-summary.json/order-security-reports-summary.json; XML/TXT específicos copiados para order-controller-red-reports/ e order-security-red-reports/; dois fontes em executed-sources/. baseline.json/preserved-before.json e quickstart-before.md/tasks-before.md registram o estado inicial. preservation-before-docs.json confirmou os 537 arquivos capturados inalterados, inclusive arquivos/evidências de T011–T014; os dois fontes finais coincidem por SHA256 com as cópias executadas. Os 3+9 resultados pertencem somente a estas execuções locais, sem somar relatórios antigos.

Assertivas posteriores ainda não alcançadas: content-type/ausência de Location, campos exatos, ids int64, instante/estado, ordem/quantidades, strings monetárias e valores calculados; titularidade na confirmação, ausência de associação ao name falso, leitura independente de buyer_id e contagens de pedidos. Permanecem escritas, sem alegação de aprovação.

O decode explícito comprova validade dos JWTs usados na preparação; filtros reais e contexto ativos não comprovam que a chain HTTP já valida bearer, usa sub/perfis do banco ou ignora claims falsos. A configuração existente permite requisições e o endpoint/segurança de pedidos ainda não foram implementados. A resolução/autorização integrada positiva e o contrato de criação continuam bloqueados pelo 404. Também não há prova atual de persistência, commit/rollback ou atomicidade. Não se afirma segurança/registro funcional a partir deste RED.

### Fechamento

T015 marcada somente após cumprir escrita, execuções serializadas e conferência do RED comportamental. T001–T015 [X]; T016–T055 abertas (40). Histórico anterior do quickstart preservado por acréscimo; tasks.md altera nesta etapa somente a checkbox T015. Diff/fontes novos e whitespace conferidos; preservação final mantém os outros 535 arquivos capturados inalterados e o prefixo documental anterior byte a byte.

Estado final: quickstart.md/tasks.md modificados, os dois testes novos/não rastreados e os 13 fontes preexistentes de T011–T014 não rastreados preservados. Índice vazio; raiz, branch e HEAD mantidos. Sem repetição de testes anteriores, suíte completa, CI, T016+, U1/U2, stage, commit, push ou troca de branch. O RED permanece intencional para as tarefas seguintes autorizadas separadamente.

## Checkpoint T016 — revisão conjunta e execuções RED serializadas (2026-10-07)

### Escopo e estado inicial

Executada exclusivamente T016 da Feature 004; encerrada antes de T017. Git conferido antes das edições: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado), índice vazio. Preexistiam quickstart.md/tasks.md modificados e os 15 fontes não rastreados de T011–T015. Todas as alterações e os arquivos históricos de evidência foram preservados; só os dois documentos recebem atualização neste checkpoint.

T016 e dependências T013/T014/T015 conferidas, com instruções AGENTS.md/skill speckit-implement, constituição, plano, spec US1.1–10, data-model, research e contrato POST /orders como fontes da revisão. Pré-requisitos do Spec Kit: código 0, FEATURE_DIR absoluto correto. Checklist requirements.md: 16 marcados, zero abertos, sem edição. Ignore existente suficiente; extensions.yml ausente antes/depois, sem hooks. Não há configuração de execução paralela habilitada nos locais conferidos do projeto; cada alvo foi executado em comando/processo separado, terminado antes de iniciar o próximo.

### Revisão dos seis testes contra T011–T015

| Classe / origem | Cobertura revisada | Preparação e condição de prova |
| --- | --- | --- |
| OrderRegistrationIntegrationTest / T011 | US1.1–10: CLIENT/ADMIN isolado/ambos, titular, contrato exato, id/UTC/milissegundos/WAITING_PAYMENT, ordem, 42.50, preço antes/depois, envios distintos/iguais e resposta descartada; sem Location de consulta ou promessa de esvaziamento do carrinho. | Contexto completo, filtros ativos, login/JWT/decoder reais, JSON bruto. submit exige 201 e só então libera leitura de pedidos; conexão JDBC independente e ausência de transação externa. A resposta descartada também passa primeiro pela assertiva de 201. |
| OrderAggregateTest / T012 | Construção completa, listas inválidas, vínculo/position, defesa contra clear/add/set nas listas original/exposta, snapshots de preço/quantidade, subtotais e total históricos. | Unitário em memória, sem Spring/JPA/banco. Mudança do Product somente no objeto de teste; domínio provisório compilável, sem Entity/mapeamentos. Caso já aprovado de conservação simples permanece íntegro. |
| OrderRepositoryTest / T013 | Metadados, round-trip EntityManager/JDBC, PK IDENTITY/FKs obrigatórias, Instant/DECIMAL(19,2), position ASC, unicidades, NOT NULL e três CHECKs nomeados; rejeições SQL de nulos, zero/negativos, duplicatas e vínculos inválidos. | requireTable/requireSchema antes de fixtures, inserts ou persist de Order não mapeado. Metadados precedem operação de pedidos. Rejeições por JDBC/SQLException/SQLState, não Bean Validation/ORM; transação de mapeamento explicitamente não é prova de commit/rollback. Não importa OrderRepository. |
| OrderServiceTest / T014 | Preço vigente positivo, lote somente dos ids enviados, retorno fora de ordem, snapshot, 1.000→1.00 exato, 42.50 e agregado/itens novos em envios iguais, inclusive após mudança de preço. | Unitário com mocks, instanciação direta, sem contexto Spring. Serviço provisório retorna null, sem regras/interações; interface OrderRepository comum para mock, sem Spring Data/registro. DTOs continuam somente estruturais. |
| OrderControllerTest / T015 | 201/shape exato; ids numéricos inteiros positivos representáveis em int64, quantidades Integer, strings monetárias com duas casas/sem expoente, capacidade19,2 e centavos, UTC/milissegundos/estado/ordem. | HTTP completo inicializável só com componentes existentes, JSON bruto, login/filtros reais. Assertivas posteriores somente após 201; nenhum controller fictício ou resposta de sucesso simulada. |
| OrderSecurityIntegrationTest / T015 | CLIENT, ADMIN sem CLIENT e ambos; JWT original e seis variantes assinadas com sub/iat/exp mantidos, name de outra conta e roles vazios/não autorizados; exigir titularidade de sub/perfis do banco. | Encoder/decoder e filtros reais, JWT obtido por login. Perfis JDBC e validade das variantes conferidos antes do POST. Assertivas de titularidade/SQL independente só após 201; sem jwt(), WithMockUser ou decoder mock como prova. |

H2/fixtures: cada uma das quatro classes com banco declara DynamicPropertySource para URL order-test com UUID próprio; OrderTestFixture exige esse prefixo e prepara contas/perfis/produtos existentes com commits próprios. Limpeza remove somente dados próprios e protege acesso a tb_order/tb_order_item por existência das tabelas. As classes unitárias de domínio/serviço não usam banco. Confirmados Product/ProductRepository/UserAccount/seeds/configurações preservados.

Suporte provisório conferido: Order/OrderItem/OrderStatus sem Entity ou mapeamento JPA; OrderService sem Service/Component/Bean/Transactional e com retorno null; OrderRepository sem herança Spring Data, RepositoryDefinition ou bean. Não houve alteração de scan, configuração produtiva ou registro de componentes para fazer contexto iniciar ou POST retornar sucesso.

**Correções necessárias nos testes/suporte: nenhuma.** Fontes/quantidade de testes/assertivas preservados byte a byte, sem enfraquecimento, Disabled/skip introduzido ou implementação para obter GREEN. Ajustes auxiliares do script local de captura foram concluídos antes de iniciar Maven, sem executar teste como evidência de RED por erro do script.

### Execuções atuais — comandos separados e serializados

Windows na raiz, revisão acima, 2026-10-07 (offset -03:00). Wrapper confirmado por `.\mvnw.cmd --version` às 18:24:28.626–18:24:30.267, código 0: Maven 3.9.16, Java 21.0.12.1 Azul Systems, runtime C:\Program Files\Zulu\zulu-21.

| Comando real | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" test` | 18:24:30.485 → 18:25:03.436 | 9 | 0 | 9 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderAggregateTest" test` | 18:25:20.543 → 18:25:34.094 | 18 | 1 | 17 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test` | 18:26:09.264 → 18:26:38.491 | 23 | 0 | 23 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderServiceTest" test` | 18:26:48.858 → 18:27:05.233 | 11 | 0 | 11 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderControllerTest" test` | 18:27:28.958 → 18:27:59.503 | 3 | 0 | 3 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 18:28:32.023 → 18:29:06.404 | 9 | 0 | 9 | 0 | 0 | 1 |
| **Somente estes seis alvos locais** | Sem sobreposição entre processos | **73** | **1** | **72** | **0** | **0** | Todos 1 por falhas de teste |

Cada alvo executado uma única vez nesta tarefa. Compilação/preparação válidas: todos os comandos avançaram até Surefire e produziram relatórios atuais das classes selecionadas, sem erros de compilação. Os quatro contextos Spring/H2 inicializaram com sucesso; os dois unitários não tentaram iniciar aplicação. Login, fixtures, decode/variantes e limpeza não falharam. A serialização foi conferida também pelos horários gravados, não apenas pela ordem planejada. Não houve suíte completa ou CI; resultados históricos não foram somados a estes 73 casos.

### Causas observadas e assertivas ainda bloqueadas

| Classe | Causa real do resultado | Provas posteriores não alcançadas |
| --- | --- | --- |
| OrderRegistrationIntegrationTest | 9 AssertionError: Status expected:<201> but was:<404>. POST /orders ausente. | Contrato de sucesso, persistência independente/snapshots, novo id por envio, resposta descartada e demais observações posteriores ao primeiro POST. |
| OrderAggregateTest | 17 falhas: estado/vínculo/position/cálculos null; listas inválidas aceitas; listas original/exposta permitem mutação. 12 AssertionError e 5 AssertionFailedError. | Assertivas após a primeira falha de cada caso (incluindo preservação após tentativa de mutação e cálculos subsequentes). Nenhum comportamento ausente foi implementado. |
| OrderRepositoryTest | 23 AssertionError por TB_ORDER/TB_ORDER_ITEM ausentes: 2 em requireTable, 21 em requireSchema. | Todos os metadados de pedidos, round-trip, inserts, rejeições SQL, limites/identidade/constraints; não houve persist de entidade não mapeada ou insert em tabela ausente como causa do RED. |
| OrderServiceTest | 10 AssertionError porque register retorna null; 1 Wanted but not invoked porque findAllById não foi chamado. | Valores/ordem/normalização/snapshots, callbacks simulados, saves e agregados distintos posteriores à primeira assertiva. Mocks não comprovam SQL/JPA/proxy/commit. |
| OrderControllerTest | 3 AssertionError: 201 esperado, 404 real. | Content-type/Location, campos/tipos, ids, instante/estado, ordem e strings monetárias/valores. |
| OrderSecurityIntegrationTest | 9 AssertionError: 201 esperado, 404 real, após login/decodes/perfis/variantes válidos. | Titularidade na confirmação/banco e comportamento integrado que ignora name/roles falsos; SQL só existiria após sucesso. |

A aprovação única de OrderAggregateTest é `shouldKeepTheHistoricalPriceAndQuantityWhenTheProductPriceChanges`: preço unitário/quantidade armazenados continuam históricos após mudar o preço do Product. Foi preservada e novamente aprovada; não é prova de agregado completo ou GREEN da classe. Nenhum erro de compilação, contexto ou fixture foi classificado como RED.

Limites: decode explícito comprova validade dos JWTs de preparação; filtros existentes ativos não demonstram que a chain HTTP já autentica/autoriza pedidos ou resolve principal/perfis do banco. A chain existente permite requisições e a operação não está entregue. Metadados/constraints/JPA de pedidos, proxy de OrderService, commit/rollback, atomicidade e contrato de sucesso continuam sem prova funcional. O RED é o checkpoint pré-implementação, não aprovação da US1.

### Evidências e preservação

Arquivos locais ignorados pelo Git em `target/harness-validation/feature-004-t016-2026-10-07/`: para cada classe, `Classe.log` com stdout/stderr completos, `Classe-result.json` com comando/horários/código real do processo, `Classe-summary.json` com casos e `Classe-reports/` com XML/TXT Surefire. Seis fontes em executed-sources/ e execution-summary.json registram revisão executada/contagens/serialização; wrapper-version.txt/result.json registram ferramentas. capture-selected-test.ps1 é somente o procedimento auxiliar de captura, sem registro de componente ou alteração do projeto.

MockMvcPrint.NONE permanece nos testes HTTP; verificação dos logs/XML/TXT atuais não encontrou tokens JWT, chave do perfil test ou senhas das fixtures. Avisos ByteBuddy/OpenJDK foram capturados sem interromper o processo e não causaram o RED. Códigos Maven 1 completos desta etapa não reescrevem a limitação histórica da captura de T013. Relatórios ativos em target/surefire-reports representam as execuções atuais; cópias históricas anteriores permanecem intactas em seus arquivos T011–T015.

baseline.json/preserved-before.json e quickstart-before.md/tasks-before.md capturam o estado inicial. preservation-before-docs.json confirmou os 561 arquivos capturados inalterados após as seis execuções, inclusive fontes e todas as evidências históricas anteriores. Fontes finais coincidem por SHA256 com as seis cópias executadas. Conferência final protege os outros 559 arquivos, preserva integralmente o prefixo documental anterior e restringe tasks.md a uma única alteração de byte da checkbox T016.

### Fechamento

T016 marcada somente após revisão e seis execuções concluídas/verificadas. T001–T016 [X]; T017–T055 abertas (39). Quickstart atualizado somente por acréscimo, sem substituir evidências históricas. Diff/arquivos novos preexistentes revisados e whitespace conferido sem erros. Não foram alterados testes, suporte, código produtivo, configurações, seeds ou outros documentos.

Estado final: quickstart.md/tasks.md modificados; os 15 fontes preexistentes continuam não rastreados e preservados. Nenhum novo fonte de aplicação/teste nesta etapa; somente evidências locais ignoradas. Índice vazio; raiz/branch/HEAD mantidos. Encerrado antes de T017, sem T017–T022, suíte completa, CI, U1/U2, stage, commit, push ou troca de branch.

## Checkpoint T017 — domínio do agregado GREEN (2026-10-07)

### Escopo e ponto de partida

Executada exclusivamente T017, encerrada antes de T018. Raiz confirmada: `C:/Users/ronal/dscommerce-speckit`; branch real `004-registrar-pedido`; HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado). Índice vazio; preexistiam quickstart.md/tasks.md modificados e 15 fontes não rastreados de T011–T015. Lidos T017/dependência T016, data-model.md e OrderAggregateTest, conforme instruções do projeto e skill speckit-implement. Pré-requisitos do Spec Kit: código 0; checklist requirements.md: 16 concluídos, zero abertos. Ignore existente suficiente; extensions.yml ausente, sem hooks.

Ponto de partida histórico: execução de OrderAggregateTest em T016, 18 casos, 17 falhas comportamentais, um aprovado, zero erros/ignorados, Maven 1. As causas e relatórios RED anteriores foram preservados, sem repetir aquela execução. O fonte do teste permaneceu idêntico por SHA256 à cópia executada em T016; nenhuma assertiva foi removida, enfraquecida ou desabilitada.

### Domínio implementado

Alterados somente Order.java e OrderItem.java no domínio. OrderStatus.java foi revisado e preservado: seu enum WAITING_PAYMENT já atende T017. Assinaturas públicas existentes e construtores sem argumentos protegidos mantidos, sem Entity, imports/mapeamentos JPA ou validação ORM/SQL.

Order exige comprador e instante, copia a lista completa, rejeita lista nula/vazia/item nulo e valida toda a lista antes de vincular os itens. Cada item recebe o pedido proprietário e position zero-based na ordem enviada. A cópia protege contra clear/add/set na lista original; a coleção exposta não permite essas operações. Guardas também impedem reutilizar o mesmo objeto de item ou reatribuir item já pertencente a outro agregado, preservando os vínculos. Essas duas guardas adicionais foram revisadas no código; não há caso unitário específico para elas entre os 18 existentes.

Estado inicial WAITING_PAYMENT. Comprador, instante, preço e quantidade de compra sem setters públicos; vínculo/position definidos pelo agregado por método de acesso de pacote, sem setter público. Product permanece somente como referência do item; seu preço atual não é consultado nos cálculos. Subtotal usa BigDecimal.multiply com a quantidade inteira histórica; total soma esses subtotais exatamente, sem float/double, MathContext, arredondamento ou normalização silenciosa. Preservados valores como 10.00×2 + 7.50×3 = 42.50 e histórico após mudança dos preços dos produtos.

### Execução atual — somente OrderAggregateTest

Windows, 2026-10-07, horários com offset -03:00. `.\mvnw.cmd --version`, 18:46:33.573 → 18:46:35.228, código 0: Maven 3.9.16; Java 21.0.12.1 Azul/Zulu, runtime `C:\Program Files\Zulu\zulu-21`.

| Comando real | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderAggregateTest" test` | 18:46:35.322 → 18:47:01.279 | 18 | 18 | 0 | 0 | 0 | 0 |

BUILD SUCCESS; Surefire 0.986 s, tempo Maven 21.030 s. Compilação válida de 38 fontes produtivos e 28 fontes de teste com release 21; somente o alvo acima foi executado, uma única vez. O GREEN contempla todos os casos originais: construção, listas inválidas, vínculos/positions, proteção das duas coleções, histórico de preço/quantidade, subtotais e total exatos. Compilar os demais fontes não equivale a executar seus testes.

Captura integral de stdout/stderr por cmd.exe /d /c, código real via LASTEXITCODE, horários e HEAD registrados em JSON. Artefatos locais ignorados pelo Git em `target/harness-validation/feature-004-t017-2026-10-07/`:

- `wrapper-version.txt`, `wrapper-version-result.json`;
- `order-aggregate-green.txt`, `order-aggregate-green-result.json`, `reports-summary.json`;
- `order-aggregate-green-reports/TEST-com.devsuperior.dscommerce.entities.OrderAggregateTest.xml` e `order-aggregate-green-reports/com.devsuperior.dscommerce.entities.OrderAggregateTest.txt`;
- `executed-sources/`: Order.java, OrderItem.java, OrderStatus.java e OrderAggregateTest.java efetivamente compilados/executados;
- `baseline.json`, `preserved-before.json`, `quickstart-before.md`, `tasks-before.md`, `domain-before/` e `OrderAggregateTest-before.java`; conferências de preservação e whitespace ao fechar a tarefa.

### Limites e fechamento

Esta é prova unitária do domínio em memória. Não prova persistência/mapeamento, constraints SQL, commit/rollback, transação por proxy, HTTP, DTO final ou segurança. Normalização monetária exata para escala 2/capacidade 19,2 no serviço e as validações de entrada/preço das tarefas posteriores permanecem fora deste escopo. Os outros cinco alvos de T016 não foram repetidos; seus resultados RED permanecem evidências históricas, sem alegação de execução atual, suíte completa ou CI.

Preservados Product, UserAccount, testes, fixtures, DTOs e serviços/repositórios provisórios, configurações/seeds e todas as evidências T011–T016. Conferência antes da atualização documental: dos 609 arquivos capturados, só Order.java/OrderItem.java mudaram. O fechamento confere os outros 605 arquivos protegidos, as quatro cópias de fontes executados, o prefixo anterior do quickstart byte a byte e a mudança única da checkbox T017 em tasks.md.

T017 marcada após implementação, GREEN e revisão. T001–T017 [X]; T018–T055 abertas (38). Quickstart atualizado por acréscimo, preservando o histórico RED. Diff/arquivos locais e whitespace conferidos. Estado final: dois documentos modificados e 15 fontes preexistentes não rastreados (dois alterados nesta etapa); índice vazio, raiz/branch/HEAD mantidos. Sem T018+, Entity/mapeamentos/tabelas, alterações de Product/UserAccount, outros alvos, suíte completa, CI, U1/U2, stage, commit ou push.

## Checkpoint T018 — mapeamento JPA e integridade SQL GREEN (2026-10-07)

### Escopo e estado inicial

Executada exclusivamente T018, encerrada antes de T019. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec` (ead6974 esperado), índice vazio. Preexistiam dois documentos modificados e 15 fontes não rastreados. Lidos T018/dependências T017/T013, OrderRepositoryTest, data-model, plano, decisões D4/D5 e contratos/instruções do projeto, conforme skill speckit-implement. Pré-requisitos: código 0; requirements.md: 16 concluídos, zero abertos. Ignore suficiente; extensions.yml ausente antes/depois, sem hooks.

Ponto de partida: RED histórico de T016, OrderRepositoryTest com 23 falhas nas assertivas de schema ausente, compilação/contexto/fixtures válidos. Não repetido como RED novo nem substituído. Domínio GREEN de T017 preservado, incluindo construtores protegidos, construção completa, vínculos/positions, defesa das coleções e cálculos históricos.

### Mapeamentos implementados

Order/OrderItem agora são entidades com acesso por campo, tabelas tb_order/tb_order_item e ids Long IDENTITY com getters, sem setters públicos. Buyer/order/product são ManyToOne LAZY, optional=false, JoinColumn com nullable=false; nenhuma cascata para conta/produto. Order.items usa mappedBy="order", cascade somente PERSIST, OrderBy("position ASC"), NotEmpty/Valid e elementos NotNull. Não há cascade MERGE/REMOVE/ALL ou orphanRemoval.

CreatedAt persiste Instant em created_at NOT NULL; status usa Enumerated STRING e NOT NULL, com WAITING_PAYMENT inicial preservado. Position é Integer NotNull/PositiveOrZero, quantity Integer NotNull/Positive; unit_price BigDecimal NOT NULL DECIMAL(19,2), NotNull/DecimalMin("0.01")/Digits(integer=17,fraction=2). Unicidades uk_order_item_order_product e uk_order_item_order_position garantem os pares (order_id,product_id)/(order_id,position). Três Check Hibernate explícitos geram ck_order_item_quantity_positive (quantity > 0), ck_order_item_position_nonnegative (position >= 0) e ck_order_item_unit_price_positive (unit_price > 0).

Subtotal/total continuam derivados por BigDecimal exato do histórico, com Transient e sem colunas redundantes. Nenhuma regra de construção ou cálculo de T017 foi trocada. OrderRepository estende JpaRepository<Order,Long>; saveAndFlush herdado preserva o suporte de mocks compilável. Descoberta Spring Data nos contextos registra três interfaces JPA, com as duas existentes e OrderRepository ativada. OrderService permanece provisório, sem registro Spring nem lógica.

Schema gerado exclusivamente por Hibernate 6.5.3.Final e ddl-auto=create-drop existente. Sem DDL de criação em teste, migração ou trigger. Product, UserAccount, repositórios existentes e seeds não foram alterados.

### Compatibilidade do teste com metadados H2

Primeira execução atual: 23 casos, um aprovado e 22 falhas (AssertionError), zero erros/ignorados, Maven 1. Tb_order passou seus metadados; os outros casos pararam na comparação da expressão do CHECK monetário. O schema já existia e H2 representou unit_price > 0 com CAST(0 AS NUMERIC(1)); após a normalização antiga de espaços/aspas/parênteses, o valor observado era unit_price>cast0asnumeric1, esperado unit_price>0. Contexto/compilação não falharam; inserts/round-trip/rejeições posteriores àquela guarda ainda ficaram bloqueados nesta tentativa.

Correção exclusiva em namedItemChecks de OrderRepositoryTest: reconhecer a representação cast0asnumeric1 como zero após a normalização textual existente. Comparações dos nomes, operadores e expressões esperadas permanecem intactas, assim como todas as assertivas de metadados/round-trip/rejeições SQL. Não há aceitação genérica de qualquer CHECK. Não se mudou o mapeamento para acomodar a comparação nem se removeu teste. Fonte/relatórios da tentativa inicial e fonte anterior estão preservados. A correção depende da representação observada de H2; não se afirma compatibilidade automática com todos os dialetos.

### Execuções atuais — serializadas

Windows, 2026-10-07, offset -03:00. Wrapper confirmado por `.\mvnw.cmd --version`, código 0: Maven 3.9.16, Java 21.0.12.1 Azul/Zulu. Cada comando em processo separado e terminado antes do próximo; repetição de OrderRepositoryTest justificada pela correção de metadados. Os outros quatro alvos foram executados uma vez cada.

| Comando real | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test` | 18:55:07.194 → 18:55:49.974 | 23 | 1 | 22 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test` | 18:56:15.761 → 18:57:00.052 | 23 | 23 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderAggregateTest" test` | 18:57:13.663 → 18:57:26.717 | 18 | 18 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=ProductRepositoryTest" test` | 18:57:27.171 → 18:57:54.004 | 6 | 6 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=UserAccountRepositoryTest" test` | 18:57:54.160 → 18:58:21.211 | 4 | 4 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=UserAccountRoleRepositoryTest" test` | 18:58:21.320 → 18:58:52.755 | 11 | 11 | 0 | 0 | 0 | 0 |

Validação final dos cinco alvos: 62 aprovados (23+18+6+4+11), zero falhas/erros/ignorados, todos BUILD SUCCESS/Maven 0. Os 23 casos da primeira tentativa com falhas não são somados aos 62 GREEN. Compilação válida de todos os fontes; nenhum outro alvo foi executado. Avisos existentes do Hibernate/H2 e ByteBuddy/OpenJDK foram capturados, sem interromper testes ou coleta.

### Assertivas efetivamente alcançadas e limites

OrderRepositoryTest GREEN percorreu: tabelas/metadados completos sem subtotal/total, BIGINT IDENTITY/PK, FKs obrigatórias, Instant e decimal19,2, enum STRING, unicidades e três CHECKs nomeados. Round-trip de agregado completo por EntityManager persist/flush/clear/find com JDBC na transação verifica titular, instante de milissegundos, WAITING_PAYMENT, posições 0/1 e produtos na ordem enviada, preço/quantidade históricos e total 42.50. Identidades distintas/positivas conferidas.

Rejeições diretas JDBC confirmaram SQLState 23502 para oito colunas obrigatórias, 23506 para três vínculos inexistentes, 23505 para os dois pares únicos e 23513 com nomes dos CHECKs para cinco casos zero/negativos. Limites aceitos: position zero, quantidade Integer.MAX_VALUE, preço mínimo 0.01 e máximo 99999999999999999.99. Essas provas são do banco, sem Bean Validation/ORM interceptando os inserts inválidos. Não há assertiva de persistência ainda bloqueada neste alvo após a correção.

OrderAggregateTest preservado byte a byte: 18 casos continuam GREEN. Regressões existentes: ProductRepositoryTest 6, UserAccountRepositoryTest 4, UserAccountRoleRepositoryTest 11; contratos e seeds exercitados preservados. H2 de pedidos/perfis usa UUID isolado via configuração de testes existente; slices JPA de produto/conta usam banco embutido substituído pelo suporte existente. Limpeza preservada, somente dados próprios.

O round-trip usa transação JPA de teste marcada rollback-only: prova mapeamento/flush/leitura, não commit/rollback do serviço de registro. Rejeições JDBC provam integridade SQL, não atomicidade do fluxo HTTP. LAZY/cascade e anotações Bean Validation foram revisados no mapeamento; não há caso específico novo para cada plano de carregamento ou erro de validação ORM. O teste de round-trip usa EntityManager, sem alegar execução de saveAndFlush por OrderRepository naquele caso. Não foram implementados DTOs finais, serviço, segurança ou endpoint, nem executada suíte completa/CI.

### Evidências e fechamento

Captura completa de stdout/stderr por cmd.exe /d /c e código real do processo via LASTEXITCODE; comandos/horários/HEAD em JSON. Artefatos ignorados em `target/harness-validation/feature-004-t018-2026-10-07/`: wrapper-version.txt/result.json; order-repository-run1.txt/result.json e order-repository-run2.txt/result.json com XML/TXT arquivados nas respectivas pastas *-reports; OrderAggregateTest, ProductRepositoryTest, UserAccountRepositoryTest e UserAccountRoleRepositoryTest com console .txt, *-result.json e *-reports/ separados; reports-summary.json com contagens/casos. executed-sources-run1/ e executed-sources/ preservam oito fontes por revisão; sources-before/ e snapshots documentais registram o ponto de partida. baseline.json/preserved-before.json e conferências finais de preservação/diff/whitespace completam a captura.

Dos 632 arquivos capturados, somente quatro fontes e os dois documentos têm alterações desta etapa. Os outros 626 arquivos, inclusive evidências anteriores, são protegidos por SHA256; quickstart anterior preservado byte a byte por acréscimo e tasks altera somente um byte da checkbox T018. OrderAggregateTest e as três regressões permaneceram intactos; OrderRepositoryTest teve somente a correção de representação de metadados documentada acima.

T018 marcada após implementação, revisão e validações. T001–T018 [X]; T019–T055 abertas (37). Diff e whitespace conferidos, incluindo os 15 fontes não rastreados. Estado final: quickstart/tasks modificados e os 15 fontes preexistentes não rastreados; índice vazio, raiz/branch/HEAD mantidos. Encerrado antes de T019, sem outros alvos, suíte completa, CI, U1/U2, stage, commit ou push.

## Checkpoint T019 — DTOs imutáveis e contrato JSON GREEN (2026-10-08)

### Escopo e base

Executada exclusivamente T019, conforme solicitação, com parada antes de T020. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`, índice inicialmente vazio. Preexistiam quickstart/tasks modificados e 15 fontes não rastreados, incluindo os quatro DTOs provisórios de T014. T018/T014/T015 concluídas, T019 aberta na base. Evidências e alterações anteriores preservadas.

Lidos skill speckit-implement, AGENTS.md, constituição, tasks/dependências, plano, pesquisa, data-model, contratos OpenAPI/README, quickstart e fontes pertinentes. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR absoluto desta feature e artefatos disponíveis. Checklist requirements.md somente em leitura: 16 marcados, nenhum aberto. Ignore Java suficiente, sem alteração. `.specify/extensions.yml` ausente na preparação; nenhum hook before_implement.

### Decisões dos quatro DTOs

- `OrderRequestDTO`: record com somente `items: List<OrderItemRequestDTO>`. Cópia defensiva não modificável por `Collections.unmodifiableList(new ArrayList<>(items))`, quando a lista não é null. Lista null/vazia, elementos null e valores ainda inválidos são preservados para a futura fronteira de validação; não se antecipa T033.
- `OrderItemRequestDTO`: record com somente `productId: Long` e `quantity: Integer`; documentação definitiva, sem novas regras, desserializador ou anotações Bean Validation.
- `OrderResponseDTO`: record com exatamente id/accountId/createdAt/status/items/total, sem referência a entidade JPA. `List.copyOf` impede mutação pelo accessor e pela lista original, preservando ordem; elementos são records imutáveis. IDs Long mantêm números JSON inteiros. Instant recebe `@JsonFormat` somente nesta propriedade, STRING/UTC com padrão `uuuu-MM-dd'T'HH:mm:ss.SSS'Z'`, inclusive `.000Z`; não há alteração global do Jackson. Status continua o enum WAITING_PAYMENT existente, que não é entidade JPA.
- `OrderItemResponseDTO`: record com somente productId/quantity/unitPrice/subtotal. UnitPrice, subtotal e total permanecem String, com guarda de representação usando o padrão Money do OpenAPI: valores positivos de `0.01` a `99999999999999999.99`, duas casas, sem sinal, zeros iniciais, separadores ou expoente. O verificador de pacote é compartilhado pelos dois DTOs de saída. Não calcula, converte para float/double, normaliza nem arredonda: strings fora do contrato são recusadas com IllegalArgumentException.

Assinaturas canônicas e tipos preparados em T014 preservados. Mapping do agregado, conversão BigDecimal exata com UNNECESSARY/toPlainString, cálculo e validação de capacidade/negócio no serviço pertencem a T020/T034. Captura/truncamento do instante pertence ao serviço em T020; a anotação define apenas sua apresentação JSON. Nenhum DTO consulta Product, Order ou UserAccount; OrderService permaneceu provisório, sem registro Spring e sem lógica. Não foi criado endpoint.

### Testes escritos antes da implementação e execuções reais

Novo alvo exclusivo: `src/test/java/com/devsuperior/dscommerce/dto/OrderDtoContractTest.java`, slice `@JsonTest` com ObjectMapper configurado pelo Boot 3.3.4. Sem serviço de pedidos, controller, banco, fixtures novas ou alteração dos testes anteriores. Não configura/coage o mapper no teste para obter o formato desejado.

Windows, 2026-10-08, offset -03:00. `.\mvnw.cmd --version`, 08:45:35.941 → 08:45:54.068, código 0: Maven 3.9.16 selecionado pelo Wrapper, Java 21.0.12.1 Azul/Zulu, runtime `C:\Program Files\Zulu\zulu-21`.

| Fase | Comando real na raiz | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RED, DTOs provisórios | `.\mvnw.cmd "-Dtest=OrderDtoContractTest" test` | 08:45:54.444 → 08:46:54.594 | 36 | 13 | 23 | 0 | 0 | 1 |
| GREEN, DTOs finais | `.\mvnw.cmd "-Dtest=OrderDtoContractTest" test` | 08:47:42.875 → 08:48:24.591 | 36 | 36 | 0 | 0 | 0 | 0 |

RED executável: compilação e contexto JSON válidos. Dezenove casos falharam na primeira assertiva de recusa de unitPrice fora do contrato; nessas invocações, as assertivas subsequentes de subtotal/total ainda não foram atingidas. Três casos falharam pela coleção ainda mutável/compartilhada (duas provas de resposta e uma de entrada), e um pelo instante sem `.000`. Os 13 casos já aprovados não foram artificialmente quebrados. Fontes e relatórios dessa revisão preservados antes da implementação.

GREEN: BUILD SUCCESS, Surefire 8.194 s, tempo Maven 36.535 s, código real 0. Compilação dos 38 fontes produtivos e 29 fontes de teste com release 21; somente OrderDtoContractTest foi executado. Os 36 casos incluem invocações parametrizadas, não 36 métodos. Nenhum outro alvo, suíte completa ou CI executado. Avisos existentes ByteBuddy/OpenJDK capturados, sem erros de execução.

Assertivas GREEN alcançadas: campos exatos do request/root/item de resposta; entrada tipada Long/Integer e round-trip dos limites; ids até Long.MAX_VALUE e quantity até Integer.MAX_VALUE como números JSON; status textual; instante UTC com `.123Z` e `.000Z`, preservando o Instant; ordem dos dois itens; strings monetárias exatas em oito valores entre centavo e teto decimal19,2. Cada caso inválido alcançou as três recusas (unitPrice/subtotal/total), incluindo null, escala incorreta, fração adicional, expoente, overflow e formatos não canônicos. Cópia defensiva da resposta comprovada após set/clear/add da lista original, com JSON inalterado; clear/add/set pelo accessor impedidos. Proteção equivalente de entrada testada, mantendo dados ainda não validados.

### Evidências e limites

Captura integral stdout/stderr via cmd.exe /d /c e código real por LASTEXITCODE. Evidências ignoradas em `target/harness-validation/feature-004-t019-2026-10-08/`: baseline.json, preserved-before.json (737 arquivos preexistentes), dto-before/, quickstart-before.md, tasks-before.md; wrapper-version.txt/result.json; red/green-console.txt, red/green-result.json e red/green-reports/ com XML/TXT separados; red/green-sources/ com os cinco fontes de cada revisão; reports-summary.json com contagens e nomes dos casos; green-source-check.json com SHA256 idêntico aos fontes finais. O runner run-focused.ps1 apenas captura o alvo acima, serialmente, sem substituir capturas anteriores. Comparação antes da atualização documental confirmou alteração somente dos quatro DTOs dentre os 737 arquivos preexistentes; todos os testes, serviços, entidades e evidências anteriores permaneceram iguais.

Prova restrita ao contrato dos DTOs e sua serialização pelo mapper do Boot: não demonstra mapping real do agregado, normalização/cálculos no serviço, reconsulta de conta/perfis, transação, persistência, commit/rollback, segurança ou HTTP 201. A guarda monetária protege representação de saída; não substitui as futuras rejeições 422 de negócio. Entrada estrita/Bean Validation permanecem em T033, sem alegação de rejeição de JSON extra, duplicado ou coercível. Resultados históricos não foram reexecutados nem reatribuídos.

### Fechamento de T019

Conferência executada por `& 'target/harness-validation/feature-004-t019-2026-10-08/final-review.ps1'`, código 0: dos 737 arquivos preexistentes capturados, somente os quatro DTOs e os dois documentos mudaram; os outros 731, inclusive todos os testes e evidências anteriores, mantiveram SHA256. Prefixo anterior do quickstart preservado byte a byte; tasks mudou somente um byte, a checkbox T019. Conteúdo dos quatro DTOs e do teste novo revisado, incluindo whitespace/newline de arquivos não rastreados; `git diff --check` código 0, sem diagnósticos de whitespace, com avisos de conversão LF/CRLF existentes nos documentos. `git diff --cached --exit-code` código 0. Uma primeira invocação inline da conferência final não iniciou por erro de parser PowerShell (chave de fechamento ausente, código 1); corrigida pelo script acima, sem execução de seu bloco na tentativa inválida e sem repetição de testes.

T019 marcada [X] após implementação, GREEN e revisão. T001–T019 concluídas; T020–T055 abertas (36). Estado final: quickstart/tasks modificados e 16 fontes não rastreados (os 15 anteriores mais OrderDtoContractTest), índice vazio; raiz/branch/HEAD mantidos. `.specify/extensions.yml` ausente também no fechamento, sem hooks after_implement. Resultado da conferência em final-review.json. Encerrado antes de T020, sem outros alvos, suíte completa, CI, U1/U2, stage, commit ou push.

## Checkpoint T020 — serviço transacional e Clock produtivo GREEN (2026-10-08)

### Escopo, base e pré-requisito autorizado

Executada exclusivamente T020, com parada antes do converter JWT/chain de T021. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`, índice inicialmente vazio. Preexistiam quickstart/tasks modificados e 16 fontes não rastreados. T019/T009 concluídas; T020 aberta na base.

Skill speckit-implement aplicada com leituras de T020/dependências, research D4–D8, data-model, OrderServiceTest e fontes de domínio/repositórios/fixtures, usando o contexto de plano/constituição/contratos já revisado. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR absoluto correto, artefatos disponíveis. Checklist requirements.md somente em leitura: 16 marcados, zero abertos. Ignore existente suficiente; extensions.yml ausente na preparação, sem hooks before_implement.

Busca em src/main confirmou ausência de bean Clock produtivo. Antecipado exclusivamente `src/main/java/com/devsuperior/dscommerce/config/OrderClockConfig.java`, conforme autorização expressa deste pedido, como pré-requisito de inicialização de OrderService. `@Configuration(proxyBeanMethods=false)` fornece `@Bean orderClock()` com `Clock.systemUTC()`. O Clock primário controlável de OrderTestConfig foi preservado, sem mudar fixtures anteriores. T021 permanece aberta: converter, chain prioritária e autenticação HTTP não implementados.

### Implementação do caminho de sucesso

`OrderService` agora é `@Service`, com quatro dependências finais injetadas pelo construtor existente. `register` é público e recebe OrderRequestDTO e AuthenticatedAccountPrincipal; comprador vem exclusivamente da conta reconsultada por principal.accountId, por `findByIdWithRoles`, dentro de `@Transactional(propagation=REQUIRED, rollbackFor=Exception.class)`. Perfis reais CLIENT ou ADMIN são conferidos, sem usar titular do request ou claims JWT.

Ids das linhas enviados a uma chamada de `ProductRepository.findAllById`. Resultado indexado por id; itens reconstruídos percorrendo a ordem da solicitação, independentemente da ordem do lote. Sem alteração do repositório existente, consultas individuais, catálogo/categorias, locks ou consolidação de linhas. Preço positivo lido é copiado em BigDecimal imutável, normalizado com `setScale(2, UNNECESSARY)` sem mudar Product. Cada subtotal e soma acumulada são exatos e conferidos quanto à precisão máxima 19 após escala 2, antes de qualquer persistência. Sem float/double, MathContext de arredondamento, frete, desconto ou acréscimo.

Instant obtido pelo Clock e truncado a milissegundos. Cada chamada constrói novos Order/OrderItems completos, vinculados/posicionados pelo domínio e em WAITING_PAYMENT. Um `saveAndFlush` do agregado; DTO mapeado no corpo transacional a partir de comprador, ids e valores históricos do agregado retornado, com `toPlainString` de escala 2. Alterar Product.price após a construção não altera resposta/histórico. Sem self-invocation, transações independentes por item ou deduplicação entre chamadas. Flush executa SQL e não equivale a commit; o proxy conclui a transação ao sair do método.

Guardas mínimas necessárias usam BadCredentialsException para identidade/conta ausente, AccessDeniedException para perfil não permitido e ProductNotFoundException existente quando falta produto do lote. Pré-condições monetárias usam IllegalArgumentException e a falha de UNNECESSARY pode propagar ArithmeticException. Não foi criado InvalidOrderException nem concluída tradução 422/motivos/envelopes; rejeições completas, duplicatas, entrada estrita e guardas coordenadas de revogação permanecem nas tarefas posteriores. Nenhum controller, configuração JWT/Jackson global, handler HTTP, DTO, entidade, repositório, fixture ou seed foi alterado nesta etapa.

### RED histórico e execuções atuais

Ponto de partida: RED de T016 em 2026-10-07, `.\mvnw.cmd "-Dtest=OrderServiceTest" test`, 18:26:48.858 → 18:27:05.233 (-03:00), código 1, 11 testes/11 falhas/zero erros e ignorados. Dez AssertionError por register retornar null; um Wanted but not invoked pelo lote não consultado. Não repetido como RED atual. SHA256 de OrderServiceTest atual idêntico à cópia executada em T016; nenhuma assertiva removida, alterada ou desabilitada. Relatórios históricos permanecem intactos.

Novo `OrderServiceContextTest` escrito antes da implementação do serviço/Clock, sem executar um RED extra. Contexto Boot real, perfil test, repositórios/JPA reais, H2 order-test UUID isolado e open-in-view desativado. Sem @Transactional de teste, mock repository, bean substituto de Clock, endpoint ou adaptação JWT. Fixtures existentes instanciadas no teste; OrderTestConfig não é importado, utilizando apenas seu auxiliar estático de URL. Observadores exclusivamente de teste acompanham SQL e retorno do método dentro do advisor transacional existente, sem abrir transações.

Windows, 2026-10-08, offset -03:00. `.\mvnw.cmd --version`, 09:23:21.054 → 09:23:22.836, código 0: Maven 3.9.16 pelo Wrapper, Java 21.0.12.1 Azul/Zulu, runtime `C:\Program Files\Zulu\zulu-21`.

| Comando real na raiz | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderServiceTest" test` | 09:23:22.899 → 09:23:58.755 | 11 | 11 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceContextTest" test` | 09:24:14.775 → 09:24:50.621 | 4 | 4 | 0 | 0 | 0 | 0 |

Dois alvos executados serialmente, uma vez cada, ambos BUILD SUCCESS. Surefire: unitário 4.262 s/contexto 22.47 s; tempo Maven 30.819 s/30.534 s. Compilação de 39 fontes produtivos e 30 fontes de teste, release 21, na primeira execução; compilar outros testes não significa executá-los. Total atual destes dois alvos: 15 aprovados. Avisos existentes Hibernate/H2 e ByteBuddy/OpenJDK capturados, sem falhas de contexto ou execução. Nenhuma suíte completa, CI ou outro alvo repetido.

### Evidência unitária, proxy/persistência e limites

Unitário GREEN preservado: lote somente dos ids enviados e ausência de outras chamadas ao ProductRepository, ordenação, preço vigente positivo, mudança antes da leitura, snapshot durante/depois de construção, 1.000→1.00 exato sem alterar Product, cálculo 42.50, novo agregado/itens por chamada e nova leitura em envio igual. Instanciação direta e mocks não provam proxy, geração real de ids, SQL, commit ou rollback.

Contexto GREEN: exatamente um Clock, produtivo UTC; serviço AOP proxy com metadados REQUIRED/read-write e regra rollbackFor para Exception verificada. Essa regra de metadata não é uma execução de rollback. Duas chamadas reais para conta ADMIN isolada geraram dois ids positivos distintos, duas consultas de conta/perfis, dois inserts de pedido e quatro de item. SQL observado dentro de transação ativa/read-write de OrderService.register; dois retornos com DTO observado ainda dentro dessa fronteira, antes de completar pelo interceptor. Resposta confirma conta, WAITING_PAYMENT, ordem enviada, 42.50 e instante de milissegundos.

Leituras JDBC depois do retorno das chamadas, fora de transação de teste e sem contexto de persistência compartilhado com o serviço, confirmaram dois pedidos/quatro itens e ordem persistida: evidência positiva local de persistência/commit do serviço via proxy, independente dos mocks. Outro caso confirma preço/subtotal/total no teto 99999999999999999.99 e preço histórico lido por JDBC. Overflow de subtotal (teto×2) e soma (dois valores 50000000000000000.00) recusado antes de inserts observados, sem pedidos novos. Isso prova checagem pré-persistência, não rollback de inserts. Limpeza de pedidos/itens próprios somente após as assertivas, antes de limpar contas/produtos próprios pela fixture; nenhuma seed pública alterada.

Não se demonstra HTTP 201, autenticação JWT, revogação coordenada, precedência/envelopes de erros, matriz completa de rejeições, falha depois de SQL/flush ou conclusão transacional com falha. Três provas de rollback permanecem futuras. Inicialização é local com configuração produtiva do Clock e dependências reais no perfil test; não é execução em produção ou CI. SaveAndFlush isoladamente não foi usado como prova de commit.

### Capturas e preservação

Evidências em `target/harness-validation/feature-004-t020-2026-10-08/`, ignoradas pelo Git: baseline.json/preserved-before.json (723 arquivos de Git e evidências históricas capturados), snapshots documentais e de serviço/teste, historical-red-check.json, wrapper-version.txt/result.json, unit/context-console.txt e respectivos result.json, XML/TXT separados em unit/context-reports/, fontes compilados em unit/context-sources/, reports-summary.json. Stdout/stderr completos por cmd.exe /d /c; código real por LASTEXITCODE, horários/HEAD registrados. Relatórios que já estavam em target/surefire-reports foram copiados a surefire-before/ antes de qualquer execução, preservando originais mesmo quando Surefire atualiza o relatório do alvo unitário. Não se somam relatórios antigos ao resultado atual. preservation-before-docs.json confirmou que somente OrderService mudou entre os 723 arquivos capturados antes da atualização documental.

### Fechamento de T020

`& 'target/harness-validation/feature-004-t020-2026-10-08/final-review.ps1'` executado com código 0: somente OrderService e os dois documentos mudaram entre os 723 arquivos preexistentes capturados; os outros 720, inclusive todos os testes, DTOs, repositórios, entidades, configurações anteriores e evidências históricas, mantiveram SHA256. Prefixo anterior de quickstart preservado byte a byte; tasks altera somente um byte da checkbox T020. Fontes finais iguais por SHA256 às cópias das duas execuções. Diff do serviço, documentos e conteúdo dos dois arquivos novos revisados; conferência específica de whitespace/newline inclui arquivos não rastreados. `git diff --check` e `git diff --cached --exit-code`: códigos 0, índice vazio. Avisos de conversão LF/CRLF dos documentos registrados, sem erro de whitespace. Conferência em final-review.json.

T020 marcada [X] após implementação, GREEN dos dois alvos e revisão. T001–T020 concluídas; T021–T055 abertas (35). Estado final: quickstart/tasks modificados e 18 fontes não rastreados (os 16 anteriores mais OrderClockConfig/OrderServiceContextTest); raiz, branch e HEAD mantidos. `.specify/extensions.yml` ausente também no fechamento, sem hooks after_implement. Clock antecipado exclusivamente como pré-requisito autorizado; T021 não concluída. Encerrado antes da adaptação JWT/chain, sem controller, outros alvos, suíte completa, CI, U1/U2, stage, commit ou push.

## Checkpoint T021 — adaptação JWT e chain restrita; HTTP 201 ainda RED (2026-10-08)

### Escopo e base preservada

Executada exclusivamente T021, com parada antes de T022. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`, índice inicialmente vazio. Preexistiam quickstart/tasks modificados e 18 fontes não rastreados de T011–T020. T020/T015/T009 concluídas; T021 aberta na base. Nenhuma troca de branch ou alteração das evidências anteriores.

Skill speckit-implement aplicada, com leitura de T021/dependências, research D1/D2/D7/D8, contrato README/OpenAPI, testes de segurança de T015 e fontes reais de login/JWT/resolução/segurança; contexto de plano/modelo/constituição preservado. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto; requirements.md 16/16 concluídos, zero abertos, PASS. Checklists somente lidas; ignore existente suficiente. `.specify/extensions.yml` ausente antes e depois, sem hooks executáveis.

### Implementação e suporte aos testes

Novo OrderJwtAuthenticationConverter recebe por construtor o AuthenticatedAccountService existente e converte JWT já decodificado: sub representável como Long positivo, resolução de conta/perfis atuais no banco pelo serviço existente, principal AuthenticatedAccountPrincipal contendo somente accountId e authorities fornecidas pela resolução. Sem entidades, passwordHash ou credenciais no principal/token de autenticação. Claims name/roles são ignoradas. BadCredentialsException de conta ausente/credencial inválida e OrderAccountResolutionException de falha interna propagam com seus tipos distintos, sem transformar falha interna em 401.

SecurityConfig fornece o converter e duas SecurityFilterChain ordenadas: @Order(1), AntPathRequestMatcher somente `/orders` com método POST, Bearer JWT com o bean JwtDecoder HS256 já existente em JwtConfig, autorização hasAnyRole CLIENT/ADMIN (OR, ADMIN isolado suficiente); @Order(2), fallback permitAll existente. Ambas stateless, CSRF desativado e frameOptions SAMEORIGIN. Outros métodos/caminhos, login, catálogo/detalhes e H2 continuam no fallback. Não alterados encoder, decoder, chave, propriedades ou emissão de login. OrderClockConfig produtivo UTC já provido em T020 é mantido sem alteração ou bean produtivo novo.

Validação temporal continua a do decoder existente, com suas regras/tolerância atuais. Este passo não implementa sub canônico estrito, exp/iat/nbf estritos nem uso adicional de Clock no converter (T035). Os handlers/envelopes de rejeição e a tradução HTTP final da falha interna permanecem em T037. Guardas transacionais completas/revogação coordenada permanecem futuras. Nenhum controller, endpoint substituto, alteração de Jackson, leitura JSON estrita ou rejeição HTTP completa foi criado.

Novos OrderJwtAuthenticationConverterTest (unitário, 11 casos) e OrderSecurityChainTest (Boot real, 20 casos). Para os três slices MVC públicos preexistentes, importado OrderSecuritySliceTestConfig com mocks das novas dependências JwtDecoder/AuthenticatedAccountService, ativados somente pela propriedade de teste `test.order-security.public-mvc-slice=true`. LoginControllerTest, ProductControllerTest e GlobalExceptionHandlerRegressionTest recebem exclusivamente essa preparação/annotations; corpos e assertivas preservados. Esses mocks não constituem prova JWT. OrderSecurityIntegrationTest de T015 permanece byte a byte inalterado, com todas as assertivas 201.

### Comandos e resultados reais

Windows, 2026-10-08, offset -03:00. `.\mvnw.cmd --version`, 10:00:02.712 → 10:00:04.587, código 0: Maven 3.9.16 pelo Wrapper, Java 21.0.12.1 Azul/Zulu. Todas as seleções abaixo executadas serialmente, sem suíte completa ou CI. A coluna captura identifica cada revisão/resultado independente; compilar fontes de outros testes não significa executá-los.

| Comando real na raiz | Captura | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" test` | converter-red | 10:00:04.806 → 10:00:33.447 | 11 | 0 | 11 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" test` | converter-green | 10:03:08.265 → 10:03:36.760 | 11 | 11 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest" test` | chain | 10:03:37.132 → 10:04:09.252 | 20 | 20 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | security-integration | 10:04:58.574 → 10:05:32.881 | 9 | 0 | 9 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=LoginControllerTest" test` | LoginControllerTest | 10:06:14.582 → 10:06:38.784 | 13 | 13 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=JwtConfigurationTest" test` | JwtConfigurationTest | 10:06:39.164 → 10:07:16.780 | 6 | 5 | 1 | 0 | 0 | 1 |
| `.\mvnw.cmd "-Dtest=JwtConfigurationTest" test` | JwtConfigurationTest-retry | 10:10:31.425 → 10:11:18.624 | 6 | 6 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=LoginControllerTest" test` | LoginControllerTest-retry | 10:11:36.842 → 10:12:00.497 | 13 | 13 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=JwtTokenServiceTest" test` | JwtTokenServiceTest | 10:12:00.871 → 10:12:29.503 | 1 | 1 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=ProductControllerTest" test` | ProductControllerTest | 10:12:29.720 → 10:12:52.871 | 11 | 11 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" test` | GlobalExceptionHandlerRegressionTest | 10:12:53.064 → 10:13:16.459 | 9 | 9 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=H2ConsoleSecurityIntegrationTest" test` | H2ConsoleSecurityIntegrationTest | 10:13:16.650 → 10:13:46.230 | 1 | 1 | 0 | 0 | 0 | 0 |

RED unitário obtido antes da implementação do converter, com scaffold compilável retornando null: 11 falhas comportamentais, zero erros de preparação/compilação/contexto. GREEN após implementação: 11/11; comprova uso da identidade/perfis resolvidos para CLIENT, ADMIN, ambos e nenhum, rejeição de sub básico inutilizável antes do lookup e propagação exata das duas exceções. Não prova criptografia, filtros ou banco por usar objetos Jwt e mock do resolvedor.

Chain GREEN 20/20: contexto Boot real, JWT obtido em POST /login, decoder/encoder HS256, resolvedor e banco H2 reais, filtros habilitados. Quinze combinações de rota/método, incluindo context path, mostram que somente POST /orders seleciona filtro Bearer e que CSRF continua ausente; CLIENT/ADMIN/ambos atravessam autorização até 404 pela ausência do controller. Filtro exclusivamente observador de teste lê o SecurityContext depois dos filtros reais, confirmando principal mínimo, credenciais nulas, authorities do banco, mesmo com name/roles forjados em token realmente assinado. Perfil vazio recebe 403 apesar das claims forjadas; ausência de Bearer recebe 401. Sem sessão/cookie nos caminhos autorizados; catálogo/detalhes/login públicos continuam acessíveis com Bearer malformado irrelevante. Nenhum endpoint de pedido fictício foi usado.

OrderSecurityIntegrationTest: os 9 casos falharam exclusivamente com `Status expected:<201> but was:<404>`. XML conferido: nove mensagens idênticas, zero erros e ignorados, sem falha de compilação/contexto. Login, JWT real, resolução e filtros chegaram à fronteira ainda sem OrderController. As assertivas posteriores ao 201 não foram atingidas: não se demonstra registro HTTP, titular persistido, valores/ordem/instante da resposta, commit/rollback ou linhas do pedido a partir desse RED. Não declarar HTTP 201 concluído. Histórico RED de T016 e demais checkpoints mantidos.

A primeira regressão JwtConfigurationTest teve uma falha não esperada: o WebApplicationContextRunner que escaneia a aplicação carregou também a configuração de mocks dos slices, produzindo dois AuthenticatedAccountService (NoUniqueBeanDefinitionException). Não é o RED autorizado de 404 e não foi tratado como aprovação. Corrigido exclusivamente o isolamento da configuração de teste com a propriedade explícita acima; nova execução 6/6 GREEN, incluindo contexto válido com encoder/decoder únicos. Os cinco casos de propriedades inválidas continuam esperando falha de contexto. LoginControllerTest repetido porque sua annotation passou a ativar a propriedade corrigida; 13/13 GREEN. Capturas da falha e das duas revisões preservadas; nenhum fallback/mocking produtivo foi introduzido.

Resultados finais por classe, sem somar tentativas repetidas: 72 aprovados nas oito classes GREEN; os 9 casos de integração permanecem RED esperado 404. Regressões comprovam contrato MVC/login existente, encoder/decoder HS256 e emissão, catálogo/erros públicos e SAMEORIGIN do H2. Não executados alvos das tarefas seguintes, suíte completa, CI ou U1/U2. Avisos Hibernate/H2, ByteBuddy/OpenJDK e de conversão LF/CRLF capturados, sem ocultar resultados.

### Capturas e preservação

Evidências ignoradas em `target/harness-validation/feature-004-t021-2026-10-08/`: baseline.json/preserved-before.json (808 arquivos preexistentes de Git e evidências históricas), snapshots de documentos/fontes, surefire-before/ (todos os relatórios originais copiados antes dos testes), wrapper-version.txt/result.json, console/result.json por captura, XML/TXT em diretórios separados e fontes de cada execução; reports-summary.json produzido por summarize-reports.ps1 com causas e contagens reais. Runner run-focused.ps1 captura stdout/stderr por cmd.exe /d /c, LASTEXITCODE real, horário e HEAD, sem substituir capturas anteriores. A falha de isolamento e os retries têm diretórios separados.

Conferência final automatizada em final-review.ps1/json verifica SHA256 dos arquivos preexistentes, prefixo histórico do quickstart, única checkbox T021, tarefas posteriores abertas, assertivas MVC preservadas, fontes finais iguais às cópias executadas, conteúdo/whitespace/newline dos quatro fontes novos, diff, índice e base Git. Nenhuma evidência anterior foi reatribuída às execuções atuais.

### Fechamento de T021

`& 'target/harness-validation/feature-004-t021-2026-10-08/final-review.ps1'` executado com código 0: entre os 808 arquivos preexistentes capturados, somente SecurityConfig, preparação dos três slices MVC e quickstart/tasks mudaram; os outros 802 mantiveram SHA256, inclusive OrderClockConfig, JwtConfig/JwtTokenService, AuthenticatedAccountService, todos os fontes anteriores da feature e evidências históricas. Os 180014 bytes anteriores de quickstart foram preservados; tasks altera somente o byte 20666, de espaço para X na checkbox T021. Assertivas MVC conferidas idênticas após remover somente imports/annotations novos da comparação; fontes produtivos e classes executadas iguais às respectivas capturas finais.

A primeira execução desse script de conferência retornou código 1 por tratamento incorreto de array aninhado do ConvertFrom-Json em PowerShell 5.1, comparando o manifesto inteiro em uma iteração. Não era mudança efetiva de arquivos nem verificação aprovada; corrigida a atribuição do array, nova execução concluiu as verificações acima. Tentativa registrada em final-review-first-attempt.json; nenhum fonte/documento foi modificado pelo script de revisão, nem teste de aplicação repetido por esse problema.

Conteúdo do diff e dos quatro fontes novos revisado; `git diff --check` código 0 e `git diff --cached --exit-code` código 0, índice vazio. Avisos LF/CRLF sem erros de whitespace. Resultado anterior a este parágrafo preservado em final-review-before-closing.json; conferência final reaplicada após o acréscimo exclusivamente documental, sem repetir testes.

T021 marcada [X] após cumprir seu escopo de segurança; T001–T021 concluídas e T022–T055 abertas (34). Última tarefa concluída T021; próxima pendente T022 (OrderController). Estado final: seis arquivos rastreados modificados e 22 fontes não rastreados (18 anteriores mais converter e três fontes de teste); raiz/branch/HEAD mantidos. Encerrado antes de T022, sem controller, suíte completa, CI, U1/U2, stage, commit ou push. HTTP 201 e os envelopes/regras estritas futuros continuam sem alegação de entrega.

Na reaplicação com redirecionamento PowerShell de stderr para stdout, um aviso nativo LF/CRLF do git foi promovido a NativeCommandError por ErrorActionPreference=Stop (código 1), interrompendo a conferência; captura mantida em final-review-console.txt. A correção de captura remove esse redirecionamento externo, sem alterar configuração Git ou suprimir a conferência de whitespace; não houve nova execução de testes de aplicação.

## Checkpoint T022 — POST /orders positivo GREEN (2026-10-08)

### Base, escopo e implementação

Executada exclusivamente T022, com parada antes de T023. Confirmados por Git: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`. Estado inicial: seis arquivos rastreados modificados, 22 fontes não rastreados, índice vazio. T021 conferida [X] em tasks e no fechamento anterior, com converter/chain reais presentes, GREEN focado registrado e RED integrado 201/404 preservado. T022 estava aberta.

Skill speckit-implement aplicada ao escopo solicitado; lidos T022/dependência, contrato OpenAPI/README, os dois testes positivos e seu suporte, serviço/principal/DTOs existentes e contexto de plano, modelo, research e constituição. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto. requirements.md: 16/16 concluídos, zero abertos, PASS, sem edição de checklist. Ignore Java existente suficiente. `.specify/extensions.yml` ausente na abertura e na conferência de fechamento, sem hooks executáveis.

Único fonte novo: `src/main/java/com/devsuperior/dscommerce/controllers/OrderController.java`. RestController com injeção por construtor de OrderService, somente @PostMapping("/orders"). Recebe OrderRequestDTO por @Valid/@RequestBody e o AuthenticatedAccountPrincipal mínimo por @AuthenticationPrincipal, produzido pelo converter real. Chama `orderService.register(request, principal)` e somente após seu retorno constrói ResponseEntity<OrderResponseDTO> com 201, sem Location.

O controller não abre transação, instancia serviço, faz self-invocation, captura exceção para devolver sucesso, calcula valores, lê produtos, grava itens ou decide permissões. A identidade vem do principal, sem campo comprador no DTO. Serviço Spring existente mantém REQUIRED/rollbackFor Exception.class e mapeamento dentro da transação; sua chamada externa atravessa o proxy e a conclusão transacional antes da construção da resposta. SaveAndFlush sozinho não é apresentado como commit. Nenhuma alteração em DTOs, serviço, segurança, Clock, entidades, repositórios, testes, fixtures, seed ou configuração global. @Valid conecta a fronteira MVC; não acrescenta as constraints/leitura estrita futuras de T033.

### RED histórico e execuções atuais

Histórico preservado, sem repetir como execução atual: OrderControllerTest em T016, 2026-10-07, 18:27:28.958 → 18:27:59.503 (-03:00), código 1, três falhas esperadas por 201/404; OrderSecurityIntegrationTest em T021, 2026-10-08, 10:04:58.574 → 10:05:32.881, código 1, nove falhas exclusivamente `Status expected:<201> but was:<404>`, zero erros/ignorados. O teste integrado atual tem SHA256 idêntico à cópia daquele RED. Não foi criado endpoint fictício nem enfraquecida qualquer assertiva para obter GREEN.

Windows, 2026-10-08, offset -03:00. `.\mvnw.cmd --version`, 10:24:40.443 → 10:24:42.179, código 0: Maven 3.9.16 pelo Wrapper, Java 21.0.12.1 Azul/Zulu, runtime `C:\Program Files\Zulu\zulu-21`.

| Comando real na raiz | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderControllerTest" test` | 10:24:42.351 → 10:25:28.057 | 3 | 3 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 10:25:46.557 → 10:26:20.693 | 9 | 9 | 0 | 0 | 0 | 0 |

Dois alvos executados serialmente, uma vez cada, ambos BUILD SUCCESS; 12 aprovados, nenhuma falha ou correção necessária. Surefire: 19.16 s e 21.30 s; Maven: 40.687 s e 29.086 s. A primeira execução recompilou os fontes; compilar outros testes não significa executá-los. Avisos existentes Hibernate/H2 e ByteBuddy/OpenJDK capturados, sem falhas de compilação, contexto ou preparação. Sem outros alvos, suíte completa, CI ou U1/U2.

### Assertivas após 201 efetivamente alcançadas e limites

Ambas as classes usam contexto Boot real, filtros habilitados, open-in-view desativado, H2 UUID isolado, login HTTP real, encoder/decoder HS256 reais, resolução/serviço/repositórios reais e Clock UTC controlável exclusivo de teste. Não usam autenticação simulada, decoder mock, serviço mock ou transação de teste envolvendo o HTTP. Fontes e assertivas anteriores mantidos byte a byte; zero casos ignorados. GREEN implica execução das assertivas incondicionais posteriores ao status 201.

OrderControllerTest: campos exatos da resposta e de cada item, IDs JSON inteiros representáveis como Long, quantity inteira, accountId autenticado, createdAt UTC com milissegundos igual ao Clock, WAITING_PAYMENT, ordem enviada, valores monetários textuais com duas casas/sem expoente, soma 42.50, teto 99999999999999999.99 e caso 0.10×3=0.30. Content-Type JSON e ausência de Location também verificados depois do 201.

OrderSecurityIntegrationTest: nove combinações de CLIENT, ADMIN isolado e ambos com token original de login, name de outra conta/roles vazios e name de outra conta/roles sem relação. Cada caso passou por todas as verificações de confirmação e depois confirmou accountId diferente da conta decoy. Fora de transação de teste, conexão independente leu buyer_id correto, exatamente um pedido da conta autenticada e zero pedidos da conta decoy. Essa é evidência positiva local de titularidade e persistência/commit depois do retorno HTTP, com JWT/filtros/serviço reais; não é prova baseada em mocks ou apenas em flush.

A sequência do código mantém construção de 201 depois do retorno do serviço via proxy; não foi instrumentado o instante exato do commit nesta execução. Não foram exercitadas falhas durante flush/conclusão, rollback, perda de resposta, envio repetido, revogação coordenada, entrada estrita, precedência ou envelopes de rejeição. Essas provas permanecem nas tarefas posteriores. Não se declara o contrato inteiro implementado nem T023 concluída: OrderRegistrationIntegrationTest e sua matriz US1.1–8 não foram executados.

Limite observado por leitura: o OrderSecurityChainTest auxiliar de T021 ainda contém três casos autorizados com corpo items vazio e expectativa 404 baseada na ausência do controller. A premissa desses casos mudou; suas expectativas/preparação precisarão de revisão na etapa de regressões apropriada. Esse alvo não foi executado nem alterado nesta T022, preservando seu fonte e resultado histórico; seu GREEN de T021 não aprova a revisão atual. Não foi inventado um resultado atual para essa classe ou para a suíte completa.

### Capturas e preservação

Evidências ignoradas em `target/harness-validation/feature-004-t022-2026-10-08/`: baseline.json/preserved-before.json (973 arquivos preexistentes de Git e evidências históricas), quickstart-before.md/tasks-before.md, sources-before/, surefire-before/ com todos os relatórios originais antes dos testes, historical-red-check.json, wrapper-version.txt/result.json, controller/security-integration-console.txt e respectivos result.json, XML/TXT e oito fontes relevantes separados por execução. Runner run-focused.ps1 permite somente os dois alvos autorizados e captura stdout/stderr por cmd.exe /d /c, LASTEXITCODE real, horários e HEAD; não substitui capturas anteriores.

`& 'target/harness-validation/feature-004-t022-2026-10-08/review.ps1'`: código 0 antes da atualização documental; todos os 973 arquivos preexistentes mantiveram SHA256. Fontes finais idênticos aos snapshots das duas execuções, XMLs conferidos com 3/9 casos e zero falhas/erros/ignorados, controller novo revisado incluindo whitespace/newline, `git diff --check` e `git diff --cached --exit-code` com códigos 0. Resultado em before-docs-review.json; avisos de conversão LF/CRLF capturados sem erro de whitespace.

T022 marcada [X] após implementação, GREEN dos dois alvos e revisão; T001–T022 concluídas, T023–T055 abertas (33). Última concluída T022; próxima pendente T023. Estado Git: os mesmos seis arquivos rastreados modificados e 23 fontes não rastreados (22 anteriores mais OrderController), índice vazio, raiz/branch/HEAD mantidos. Encerrado antes de T023, sem GET, pagamento, carrinho, outros endpoints, outros alvos, suíte completa, CI, U1/U2, stage, commit ou push.

Conferência após a atualização documental: `& 'target/harness-validation/feature-004-t022-2026-10-08/review.ps1' -Final`, código 0. Dos 973 arquivos preexistentes capturados, somente quickstart/tasks mudaram; os outros 971 mantiveram SHA256. Os 192812 bytes históricos do quickstart permaneceram intactos e tasks mudou somente o byte 21443 da checkbox T022. Fontes executados conferidos iguais, diff sem erro de whitespace, índice vazio, base Git mantida e hooks ausentes. Histórico RED do controller também conferido no XML de T016 (3/3 falhas 201/404, zero erros), em historical-controller-red-check.json. Resultado anterior a este acréscimo preservado em final-review-before-closing.json; reaplicação final registrada em final-review.json, sem repetir testes de aplicação.

## Checkpoint T023 — US1.1–8 e revisão da fronteira da chain GREEN (2026-10-08)

### Escopo, base e seleção

Executada T023 com parada antes de T024 e revisão dos três casos auxiliares da chain expressamente autorizada no pedido. Confirmados: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`. Git inicial: seis arquivos rastreados modificados, 23 fontes não rastreados, índice vazio. T022 concluída no registro anterior; T023 aberta. Alterações e evidências anteriores preservadas.

Skill speckit-implement aplicada somente a esse escopo; lidos T023/T024, cenários US1.1–8 da spec, contrato README/OpenAPI, testes selecionados, preparação JDBC/fixtures e limitação registrada em T022; contexto de plano/modelo/research/constituição mantido. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto. requirements.md 16/16 concluídos, zero abertos, PASS; checklist somente lida, ignore Java existente suficiente. `.specify/extensions.yml` ausente na abertura e conferências, sem hooks executáveis.

OrderRegistrationIntegrationTest selecionado por cinco métodos: shouldRegisterExactlyOneCompleteOrderForTheAuthenticatedAccount (3 perfis, US1.1–4), shouldUseTheCurrentPositivePriceChangedBeforePurchase (US1.5), shouldKeepStoredPricesSubtotalsAndTotalAfterProductPriceChanges (US1.6), shouldConfirmOnlyOrderDataWithoutLocationOrCartClearingPromise (US1.7), shouldGiveDistinctPurchasesTheirOwnIdsAndCreationInstants (US1.8). Total 7 execuções. Os dois métodos shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded e shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation não foram selecionados; US1.9/10 e T024 permanecem futuros. O caso US1.8 usa dois corpos distintos, sem testar repetição idêntica ou resposta perdida.

### Revisão restrita dos três casos da chain

Único fonte alterado: `src/test/java/com/devsuperior/dscommerce/config/OrderSecurityChainTest.java`. O método parametrizado para CLIENT, ADMIN isolado e ambos foi renomeado de UntilTheMissingController para AtTheOrderController, mantendo os três conjuntos de perfis. A expectativa 404 era adequada à ausência do endpoint em T021; depois de T022, o corpo items vazio não poderia demonstrar a autorização até uma operação válida.

Preparação agora cria pela fixture um produto próprio com preço 10.00 e envia uma linha com productId real e quantity 2. Expectativa restrita a essa fronteira: handlerType OrderController real e status 201 para a solicitação válida. Não houve substituição indiscriminada de status nem tolerância a respostas alternativas. Adicionada limpeza dos pedidos/itens somente da conta criada pelo teste antes de excluir seus produtos/conta, necessária pelos novos vínculos FK. Limpeza ocorre depois das assertivas e não é prova de rollback.

Preservadas todas as assertivas originais de observação do SecurityContext após filtros reais: principal mínimo/accountId, authorities exatamente dos perfis do banco, credenciais nulas, ausência de Set-Cookie/sessão. Tokens continuam obtidos por login e realmente assinados/decodificados mesmo com name/roles forjados. Mantidos sem alteração os 15 casos de seleção de rota/método/context path, CSRF ausente, negação sem Bearer = 401, conta sem perfil = 403 apesar de claims falsas e fallback público. Comparação integral normalizada verifica que fora da preparação/limpeza/nome/expectativa autorizados o fonte permaneceu igual. Nenhuma assertiva desabilitada ou enfraquecida.

Não houve falha dos alvos de aplicação nem alteração produtiva: controller, serviço, segurança, DTOs, entidades, repositórios, Clock e fixtures anteriores mantiveram SHA256. OrderRegistrationIntegrationTest e OrderSecurityIntegrationTest também permanecem byte a byte iguais. Nenhum GET /orders ou endpoint adicional criado.

### Comandos e resultados reais

Windows, 2026-10-08, offset -03:00. `.\mvnw.cmd --version`, 10:39:15.030 → 10:39:16.861, código 0: Maven 3.9.16 pelo Wrapper e Java 21.0.12.1 Azul/Zulu. Os três alvos foram executados serialmente, uma vez cada, com os fontes já ajustados; não executados suíte completa, CI, U1/U2 ou outros alvos.

| Comando real na raiz | Início → fim | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest#shouldRegisterExactlyOneCompleteOrderForTheAuthenticatedAccount+shouldUseTheCurrentPositivePriceChangedBeforePurchase+shouldKeepStoredPricesSubtotalsAndTotalAfterProductPriceChanges+shouldConfirmOnlyOrderDataWithoutLocationOrCartClearingPromise+shouldGiveDistinctPurchasesTheirOwnIdsAndCreationInstants" test` | 10:39:17.017 → 10:40:01.917 | 7 | 7 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 10:40:02.198 → 10:40:35.318 | 9 | 9 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest" test` | 10:40:35.521 → 10:41:07.707 | 20 | 20 | 0 | 0 | 0 | 0 |

Total atual: 36 aprovados, zero falhas/erros/ignorados, três BUILD SUCCESS. Tempos Surefire 21.07 s, 20.83 s e 19.85 s; Maven 40.023 s, 28.129 s e 27.261 s. Compilar fontes de testes fora da seleção não significa executá-los. XML conferido para exatamente os cinco métodos selecionados, com 3+1+1+1+1 execuções e nenhum caso US1.9/10; três casos ajustados da chain presentes. Avisos Hibernate/H2 e ByteBuddy/OpenJDK capturados, sem falha de compilação/contexto/preparação. REDs históricos de T016/T021 e limite histórico de T022 não foram reescritos nem atribuídos à execução atual.

### Evidências por cenário e fronteira de persistência

Os testes de registro/segurança usam Boot, login HTTP, JWT/decoder HS256, filtros, resolvedor, serviço/proxy e repositórios reais, com open-in-view desativado e H2 UUID isolado por contexto. Nenhuma autenticação simulada, decoder/serviço/repositório mock ou transação externa de teste. Clock controlável é somente de teste. Em registro, assertivas verificam ausência de transação ativa e de recurso EntityManager antes/depois do POST e nas leituras; DriverManagerDataSource abre conexões novas fora do contexto de persistência da aplicação. As consultas de pedidos/itens só são permitidas depois de HTTP 201.

| Cenário/classe | Execuções aprovadas | Evidência alcançada depois do 201 |
| --- | ---: | --- |
| US1.1–4 / registro | 3 | CLIENT, ADMIN isolado e ambos; exatamente um pedido completo da conta autenticada e nenhum da outra conta; pedido/itens visíveis por conexão independente |
| US1.5 / registro | 1 | Preço positivo confirmado antes do POST muda 10.00→11.25; snapshot 11.25×2=22.50, segunda linha 7.50×3=22.50, total 45.00 na resposta e nos dados históricos |
| US1.6 / registro | 1 | Depois da compra, preços atuais mudam para 99.99 e 25.00 e são confirmados por SQL; preços históricos continuam 10.00/7.50, subtotais 20.00/22.50 e total 42.50 |
| US1.7 / registro | 1 | Somente dados de pedido, sem Location ou campos/promessa de carrinho esvaziado; persistência completa conferida |
| US1.8 / registro | 1 | Duas compras distintas com ids diferentes e instantes próprios separados por um segundo; ambas preservadas, totais 42.50 e 7.50 |
| Segurança integrada | 9 | CLIENT, ADMIN isolado e ambos × token original/name falso com roles vazios/name falso com roles sem relação; comprador correto e zero pedidos para a conta decoy, por conexão independente |
| Chain ajustada | 20 | Três perfis autorizados chegam ao controller real com 201; 15 seleções de rota/método, negações e fallback público preservados |

Em todos os casos de registro, confirmação verifica campos exatos, ids positivos JSON inteiros/int64, quantities inteiras, comprador, ordem enviada, WAITING_PAYMENT e createdAt UTC com precisão de milissegundos igual ao Clock truncado (o Clock também contém nanos além do milissegundo). Produtos são criados em ordem inversa à solicitação para impedir que ordenação por productId satisfaça acidentalmente a ordem solicitada. SQL independente confirma id/buyer_id/created_at/status e cada linha/posição/quantidade/preço. Strings monetárias da resposta têm duas casas e aritmética exata 10.00×2 + 7.50×3 = 42.50.

Subtotais/total não são colunas redundantes: a verificação independente deriva-os com BigDecimal a partir de unit_price e quantity históricos lidos de tb_order_item, sem recalcular com Product.price. No US1.6, as leituras históricas são repetidas depois de confirmar os novos preços atuais, sem GET de pedido. Isso constitui evidência positiva local de persistência/commit após retorno HTTP e conservação histórica; saveAndFlush ou mocks não são a prova. Limpeza explícita depois das assertivas não demonstra rollback.

Limites: não executados resposta perdida/reenvio idêntico (T024), matriz de rejeições, guardas coordenadas de revogação, temporal/JSON estritos, envelopes, falhas de flush/conclusão ou rollback. Não se prova comportamento em outro banco/produção/CI nem o instante físico exato do commit. OrderSecurityChainTest é evidência de seleção/autorização, não substitui a prova de linhas por conexão independente dos testes de registro. Não se declara toda a feature entregue.

### Capturas, revisão e fechamento

Capturas ignoradas em `target/harness-validation/feature-004-t023-2026-10-08/`: baseline.json/preserved-before.json (1077 arquivos preexistentes de Git e evidências históricas), snapshots documentais/fontes, surefire-before/ com todos os relatórios originais antes da execução, wrapper-version.txt/result.json, console/result.json por alvo, XML/TXT e dez fontes relevantes por execução. Runner run-focused.ps1 restringe seleção aos três alvos acima e registra stdout/stderr por cmd.exe /d /c, LASTEXITCODE real, horários, seleção e HEAD; nenhum relatório histórico foi perdido com a atualização de target/surefire-reports.

`& 'target/harness-validation/feature-004-t023-2026-10-08/review.ps1'`: código 0 antes de atualizar documentação. Dos 1077 arquivos preexistentes, somente OrderSecurityChainTest mudou; os outros 1076 mantiveram SHA256. Fontes finais iguais às cópias executadas, contagens/nomes XML conferidos, demais assertivas da chain preservadas, conteúdo e whitespace/newline do fonte alterado revisados. `git diff --check` e `git diff --cached --exit-code`: códigos 0, índice vazio. Resultado em before-docs-review.json; avisos LF/CRLF capturados sem erro de whitespace.

A primeira conferência retornou código 1 por substituição ampla no texto normalizado de comparação que também atingia .content(body) do helper de login; o fonte do teste não havia sido alterado ali. Corrigida somente a comparação para o bloco exato POST /orders, nova conferência aprovada. Uma tentativa de diagnóstico juntou caminho já absoluto com cwd e falhou antes de escrever; diagnóstico corrigido mostrou a diferença exclusiva da comparação (git diff --no-index código 1 esperado por diferença). Registro em review-first-attempt.json e textos normalizados preservados; nenhum teste de aplicação repetido por esse problema.

T023 marcada [X] após GREEN/revisão; T001–T023 concluídas e T024–T055 abertas (32). Última concluída T023; próxima pendente T024. Git mantém seis arquivos rastreados modificados e 23 fontes não rastreados, índice vazio, mesma raiz/branch/HEAD. Somente o teste auxiliar da chain e quickstart/tasks alterados nesta etapa. Encerrado antes de T024, sem alteração produtiva, GET /orders, implementação de rejeições/rollback, suíte completa, CI, U1/U2, stage, commit ou push.

Conferência após atualização documental: & 'target/harness-validation/feature-004-t023-2026-10-08/review.ps1' -Final, código 0. Dos 1077 arquivos preexistentes, somente o teste da chain e quickstart/tasks mudaram; 1074 mantiveram SHA256. Prefixo histórico de quickstart (201978 bytes) intacto e alteração de tasks limitada ao byte 21932 da checkbox T023. Seleção US1.1–8, ausência de US1.9/10, demais assertivas da chain, fontes executados, diff, índice e base Git conferidos; hooks ausentes. Resultado anterior ao acréscimo final preservado em final-review-before-closing.json; reaplicação final em final-review.json, sem repetir testes de aplicação.

## Checkpoint T024 — dois POSTs iguais, confirmações recebidas/descartada GREEN (2026-10-08)

Executada exclusivamente T024, com parada antes de T025. Raiz confirmada `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`. Git inicial: seis arquivos rastreados modificados e 23 fontes não rastreados, índice vazio. T023 concluída, T024 aberta. Skill speckit-implement aplicada com leitura da task/dependência, métodos US1.9/10, helpers reais e contrato, usando o contexto de plano/modelo/research/constituição e checkpoints anteriores. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto. Checklist requirements.md 16/16 concluídos, zero abertos, PASS, somente lido. Ignore Java existente suficiente; `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Preparação mínima e seleção exclusiva

Removidas somente duas linhas `clock.advance(Duration.ofSeconds(1))` de OrderRegistrationIntegrationTest, uma em cada método US1.9/10. O Clock fica fixo entre os POSTs de cada cenário: timestamps podem coincidir e não são usados para exigir unicidade. Todas as assertivas existentes foram preservadas, inclusive igualdade com o Clock, IDs distintos (sem exigir contiguidade), primeiro pedido preservado e leitura completa dos dois pedidos. O avanço de Clock de US1.8 permaneceu intacto e aquele método não foi executado nesta etapa. Sem alteração produtiva ou RED artificial.

Somente estes métodos foram selecionados: shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation (US1.10, ambas as respostas consumidas) e shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded (US1.9, primeira confirmação não consumida). Nenhuma outra classe/cenário foi executada; compilar outros fontes não significa executar seus testes.

### Comando e resultado real

Windows, 2026-10-08, offset -03:00. Comando executado uma vez na raiz:

```powershell
.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest#shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation+shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded" test
```

13:29:48.846 → 13:30:31.138, código Maven 0, BUILD SUCCESS. Dois testes aprovados, zero falhas/erros/ignorados. Surefire 18.05 s, Maven 36.866 s. XML confere exatamente os dois nomes selecionados. Wrapper configurado para Maven 3.9.16 conforme propriedades lidas; XML atual registra Java 21.0.12.1 Azul Systems e java.home `C:\Program Files\Zulu\zulu-21`. Avisos existentes Hibernate/H2 e ByteBuddy/OpenJDK capturados, sem falha de compilação/contexto/preparação. Resultado GREEN mantido, sem alteração de produção ou execução RED artificial.

| Cenário | Resultado | Evidência após cada operação |
| --- | --- | --- |
| US1.10 — duas confirmações recebidas | 1 aprovado | Primeiro POST retorna 201 e sua confirmação é lida; primeira compra completa é conferida por conexão independente. Segundo POST usa exatamente o mesmo body/token, retorna 201 com outro id; ambos os pedidos completos e primeiro preservado são conferidos, total de dois pedidos |
| US1.9 — primeira confirmação descartada no consumidor | 1 aprovado | Primeiro POST retorna 201, mas o teste ignora o MvcResult/JSON da confirmação; SQL independente encontra exatamente um pedido e seus itens completos. Segundo POST usa o mesmo body/token e sua confirmação é lida; id novo, dois pedidos, primeiro registro preservado e ambos os conjuntos de itens completos |

Os dois cenários usam Boot, POST /login, JWT HS256/decoder, filtros, resolvedor, controller, serviço/proxy, repositórios e H2 UUID isolado reais, com open-in-view desativado. Sem jwt()/WithMockUser, decoder/serviço/repositório mock ou transação externa de teste. assertOutsideTransaction verifica transação inativa e ausência de recurso EntityManager antes/depois do HTTP e das leituras. DriverManagerDataSource abre conexões independentes da aplicação. Consultas de pedidos somente após sucesso HTTP, e limpeza própria somente depois das assertivas.

Após cada POST, SQL confirma comprador autenticado, id/instante/WAITING_PAYMENT e duas linhas na ordem enviada: productId, position, quantity 2/3 e unit_price histórico 10.00/7.50. Subtotais 20.00/22.50 e total 42.50 são derivados exatamente com BigDecimal dos valores históricos lidos; não são colunas redundantes nem usam o preço atual do Product. Depois do segundo POST, dois ids distintos correspondem a dois pedidos completos (quatro itens), mantendo o primeiro. Não se exige diferença de uma unidade entre ids nem diferença entre instantes; nesta preparação, ambas as criações usam o mesmo Clock e passam nas assertivas de timestamp.

As confirmações consumidas têm campos exatos id/accountId/createdAt/status/items/total, itens com productId/quantity/unitPrice/subtotal, valores monetários como strings de duas casas, IDs JSON inteiros e instante UTC truncado a milissegundos. Forma exata exclui campos/promessas de esvaziamento de carrinho; ausência de Location também verificada. Carrinho permanece responsabilidade do consumidor. Sem GET /orders, deduplicação ou idempotência nova.

Limite explícito: descartar o MvcResult no consumidor é simulação de confirmação não consumida. MockMvc recebeu normalmente HTTP 201; não houve falha real de transporte, interrupção de rede/socket ou comprovação desses fenômenos. A leitura independente depois do retorno fornece evidência positiva local de persistência/commit e conservação do primeiro pedido, não prova de rollback/falha de commit nem mera conclusão baseada em saveAndFlush. Rejeições, atomicidade com falhas, outros cenários, outro banco/produção/CI e T025+ não foram executados.

### Capturas e preservação

Evidências ignoradas em `target/harness-validation/feature-004-t024-2026-10-08/`: baseline.json/preserved-before.json (1199 arquivos preexistentes de Git e evidências históricas), snapshots documentais, teste original, surefire-before/ com todos os relatórios originais antes da execução, us1-9-10-console.txt/result.json, XML/TXT e sete fontes relevantes em diretórios separados. Runner run-focused.ps1 contém somente a seleção autorizada, captura stdout/stderr por cmd.exe /d /c, LASTEXITCODE real, seleção, horários e HEAD; relatórios históricos não foram substituídos.

A captura de auditoria cedeu uma sessão de shell ainda ativa antes de terminar, e a cópia do teste foi feita depois da edição de preparação. O SHA256 anterior já constava do manifesto. Recuperada a versão original da cópia executada em T023, cujo hash é idêntico ao manifesto; a cópia feita durante a edição também foi mantida. Registro em source-backup-recovery.json e OrderRegistrationIntegrationTest-capture-during-edit.java. Nenhuma evidência histórica ou alteração anterior foi perdida/alterada. As conferências posteriores usam o original recuperado e verificam os hashes.

`& 'target/harness-validation/feature-004-t024-2026-10-08/review.ps1'`: código 0 antes de atualizar os documentos; dos 1199 arquivos preexistentes, somente o teste de registro mudou, e os outros 1198 mantiveram SHA256. Comparação integral verifica apenas as duas linhas de Clock removidas, preservando todas as assertivas e demais métodos. Fontes finais iguais às cópias executadas; XML confirma somente dois casos GREEN. Diff específico original/current-test em test-scope-diff.txt, git diff --no-index código 1 esperado pelas duas remoções, revisado; nenhuma outra alteração do teste. Conteúdo/whitespace/newline conferidos, `git diff --check` e `git diff --cached --exit-code` códigos 0, índice vazio. Resultado em before-docs-review.json; avisos LF/CRLF capturados sem erro de whitespace.

T024 marcada [X] após GREEN/revisão. Conferência exige T001–T024 [X] e T025–T055 abertas (31); somente a checkbox T024 muda nesta etapa. Última concluída T024, próxima pendente T025. Git mantém seis arquivos rastreados modificados e 23 fontes não rastreados, índice vazio, mesma raiz/branch/HEAD. Alterados somente preparação dos dois casos no teste, quickstart e tasks. Encerrado antes de T025, sem outras classes, suíte completa, CI, U1/U2, stage, commit ou push.

Conferência após atualização documental: & 'target/harness-validation/feature-004-t024-2026-10-08/review.ps1' -Final, código 0. Dos 1199 arquivos preexistentes, somente o teste de registro e quickstart/tasks mudaram; 1196 mantiveram SHA256. Prefixo histórico de quickstart (214482 bytes) intacto; tasks alterou somente o byte 22470 da checkbox T024. Todas as assertivas preservadas, XML com somente dois casos GREEN, fontes executados iguais, T001–T024 [X] e T025–T055 abertas conferidos. Diff/whitespace sem erro, índice vazio e base Git mantida; hooks ausentes. Resultado anterior a este acréscimo preservado em final-review-before-closing.json; reaplicação final em final-review.json, sem repetir testes de aplicação.

## Checkpoint T025 — cobertura positiva US1.1–10 consolidada (2026-10-08)

Executada exclusivamente T025; parada antes de T026. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`. Git inicial: seis arquivos rastreados modificados e 23 fontes não rastreados, índice vazio; T001–T024 [X], T025–T055 abertas. Skill speckit-implement aplicada somente ao escopo solicitado. Prerequisites `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto. Checklist requirements.md 16/16 concluídos, zero abertos, PASS, sem edição. Contexto: tasks/dependências, spec US1.1–10 e SC selecionados, contrato OpenAPI, plano/modelo/research/constituição e evidências anteriores. Ignore Java existente suficiente. `.specify/extensions.yml` ausente antes/depois; sem hooks executáveis.

### Seleção da fase e ajuste necessário ao contrato

Identificadas dez classes: as seis de T011–T016, complementadas por OrderDtoContractTest (T019), OrderServiceContextTest (T020), OrderJwtAuthenticationConverterTest e OrderSecurityChainTest (T021–T023). Toda essa cobertura foi executada por classe, serialmente e uma vez por alvo; integrações incluem todos os métodos positivos, não apenas seleções de T023/T024. Login/JWT reais são exercitados pelas classes HTTP, sem repetir classes da fundação T001–T010 ou regressões de fases posteriores. Compilar os demais fontes não significa executar outras classes.

A revisão prévia identificou em US1.8 uma exigência além da spec e incompatível com a instrução atual: Clock avançado artificialmente e `assertThat(secondTime).isAfter(firstTime)`. Removidas somente essa preparação e essa assertiva de diferença temporal, com atualização do comentário e remoção do import Duration sem uso. Preservadas as assertivas de instante de cada criação, truncamento/UTC, ids distintos e ambos os pedidos completos; Clock fixo permite timestamps coincidentes. US1.9/10 e todas as outras assertivas permanecem iguais à captura inicial. Sem exigir ids contíguos, sem RED artificial e sem alteração produtiva. Histórico T023 sobre instantes separados continua preservado como evidência daquele momento, sem ser reescrito como execução atual.

### Comandos e resultados efetivamente obtidos

Windows/PowerShell, 2026-10-08, offset -03:00. Todos os comandos abaixo executados na raiz com o Maven Wrapper configurado para Maven 3.9.16. XML atual registra Java 21.0.12.1, Azul Systems, `java.home` em Zulu 21. Não executado `--version` novamente nesta etapa; não atribuir a execução histórica desse comando a T025.

| Comando real | Início → fim (-03:00) | Testes aprovados | Falhas / erros / ignorados | Surefire (s) | Tempo Maven | Código |
| --- | --- | ---: | --- | ---: | --- | ---: |
| `.\mvnw.cmd "-Dtest=OrderAggregateTest" test` | 13:51:53.199 → 13:52:19.421 | 18 | 0 / 0 / 0 | 0.765 | 21.293 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test` | 13:52:19.781 → 13:52:52.003 | 23 | 0 / 0 / 0 | 20.022 | 27.297 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderDtoContractTest" test` | 13:52:52.393 → 13:53:11.536 | 36 | 0 / 0 / 0 | 6.659 | 14.232 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceTest" test` | 13:53:11.833 → 13:53:28.064 | 11 | 0 / 0 / 0 | 3.871 | 11.237 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceContextTest" test` | 13:53:28.361 → 13:53:58.577 | 4 | 0 / 0 / 0 | 17.814 | 25.213 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderControllerTest" test` | 13:53:58.874 → 13:54:30.660 | 3 | 0 / 0 / 0 | 19.168 | 26.798 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" test` | 13:54:30.957 → 13:54:46.973 | 11 | 0 / 0 / 0 | 3.257 | 10.948 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest" test` | 13:54:47.302 → 13:55:20.770 | 20 | 0 / 0 / 0 | 20.463 | 28.202 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" test` | 13:55:21.192 → 13:55:55.138 | 9 | 0 / 0 / 0 | 21.305 | 28.636 s | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 13:55:55.435 → 13:56:28.576 | 9 | 0 / 0 / 0 | 20.929 | 28.262 s | 0 |

Total atual: **144 testes aprovados em dez classes, zero falhas, erros ou ignorados, dez BUILD SUCCESS e códigos Maven 0**. Horários/resultados reais estão nos result.json, consoles e XML/TXT próprios por classe. XML confere todas as nove execuções de registro: três variações de perfis em US1.1–4 e seis métodos US1.5–10; as nove variações de autenticação integrada também foram alcançadas. A revisão dos intervalos confirma builds sem sobreposição. Avisos Hibernate/H2 e ByteBuddy/OpenJDK capturados, sem falha de compilação/contexto/preparação. REDs históricos preservados; o GREEN atual corresponde aos fontes capturados nesta etapa.

As classes completas também contêm guardas já existentes da fase: invariantes do agregado, constraints SQL, capacidade monetária de saída e verificações básicas do conversor/chain. Elas foram mantidas, não desabilitadas nem apresentadas como implementação/validação da matriz de rejeições US2. Nenhum teste T026+ foi criado ou executado.

### Natureza das evidências

| Classe | Evidência atual e limite |
| --- | --- |
| OrderAggregateTest | Agregado completo, vínculos/positions, coleção protegida, quantidades/preços históricos e totais derivados. Unidade de domínio, sem prova de commit |
| OrderRepositoryTest | Metadados H2, identidade/FKs, DECIMAL(19,2), instante, ordem e round-trip JPA/JDBC, constraints nomeadas existentes. Transação de teste de mapeamento não prova commit/rollback da operação HTTP |
| OrderDtoContractTest | Mapper real, campos exatos, entrada Long/Integer, saída imutável sem entidades, proteção da coleção, IDs numéricos int64, instante ISO8601 UTC em milissegundos e strings monetárias de 0.01 até 99999999999999999.99, sem expoente/arredondamento. Não prova leitura estrita de entrada |
| OrderServiceTest | Mocks: preços vigentes positivos, consulta somente dos ids enviados em lote, reposição da ordem, snapshot, escala 2 UNNECESSARY, 42.50 e novo agregado por chamada. Mocks não provam proxy/persistência/commit/rollback |
| OrderServiceContextTest | Boot/proxy e repositórios reais, Clock produtivo UTC único, REQUIRED read-write e regra rollbackFor Exception. SQL observado dentro da transação e mapping no mesmo contexto; após saída do proxy, dados dos novos pedidos e teto monetário consultados. Metadados de rollback e overflow antes de inserts não provam rollback após escrita |
| OrderControllerTest | Login/JWT/filtros/decoder/controller/serviço reais; HTTP 201, forma/tipos/UTC, 42.50, 0.30 e teto decimal como texto, sem Location. Não substitui leitura independente específica do registro |
| OrderJwtAuthenticationConverterTest | Unidade com resolvedor mock: principal mínimo accountId, authorities CLIENT/ADMIN/ambos, claims name/roles ignorados, distinção credencial inválida/falha interna. Não prova assinatura/filtros por si só |
| OrderSecurityChainTest | Login/JWT/decoder/filtros reais, seleção exclusiva POST /orders, fallback dos demais métodos/rotas, principal/perfis do banco e CLIENT/ADMIN isolado/ambos chegando ao controller real. Os três ajustes históricos T023 permanecem; negações básicas não encerram US2 |
| OrderRegistrationIntegrationTest | Boot, login/JWT/HS256/filtros/controller/serviço/proxy/repositórios reais; US1.1–10 e JDBC por conexão independente após HTTP 201, sem transação externa de teste |
| OrderSecurityIntegrationTest | Nove casos reais: CLIENT/ADMIN isolado/ambos × token original/name falso com roles vazios/name falso com roles sem relação. Sub e perfis do banco determinam comprador/autorização; JDBC independente confirma compra da conta autenticada e nenhuma compra da conta decoy |

Nas integrações de registro/autenticação, H2 UUID isolado, fixtures confirmadas, open-in-view desativado e nenhuma autenticação jwt()/WithMockUser ou decoder/serviço/repositório mock. Em registro, assertOutsideTransaction verifica transação inativa e ausência de recurso EntityManager antes/depois de HTTP e das leituras. DriverManagerDataSource abre novas conexões fora do contexto de persistência da aplicação; SQL de pedidos só é permitido após sucesso 201. Essas leituras demonstram persistência/commit positivo local após retorno; a prova não é saveAndFlush. Não demonstram instante físico exato do commit, rollback ou outro banco/produção.

### Rastreabilidade por requisito — spec e contrato

Nesta tabela, os nomes de métodos sem classe pertencem a OrderRegistrationIntegrationTest, todos confirmados no XML atual. O contrato positivo corresponde a POST /orders, resposta 201, OrderResponse/OrderItemResponse, PositiveId/Quantity/Money e Bearer HS256 com comprador/perfis obtidos do banco.

| Requisito | Evidência correspondente efetivamente executada | Conclusão / lacuna |
| --- | --- | --- |
| US1.1 | shouldRegisterExactlyOneCompleteOrderForTheAuthenticatedAccount, variação CLIENT; OrderControllerTest e segurança integrada | 10.00×2=20.00 + 7.50×3=22.50 = 42.50, compra completa da própria conta, confirmação e banco |
| US1.2 | Mesmo método, variação ADMIN isolado, e variações ADMIN em OrderSecurityIntegrationTest/OrderSecurityChainTest | ADMIN sem CLIENT registra para si, conforme autorização OR |
| US1.3 | Mesmo método, variação CLIENT+ADMIN, e segurança integrada | Exatamente um pedido completo naquela confirmação, sem duplicação por perfis |
| US1.4 | assertConfirmation/assertStoredOrder alcançados nas nove execuções; OrderControllerTest, OrderDtoContractTest, OrderRepositoryTest | Campos exatos id/accountId/createdAt/status/items/total e productId/quantity/unitPrice/subtotal; todos os itens na ordem enviada, WAITING_PAYMENT, UTC/milissegundos, IDs numéricos e valores como strings |
| US1.5 | shouldUseTheCurrentPositivePriceChangedBeforePurchase; OrderServiceTest | Preço vigente positivo confirmado por SQL antes do POST: 10.00→11.25; snapshot 11.25×2=22.50, segunda linha 22.50, total 45.00. Nenhuma rejeição de preço inválido inferida |
| US1.6 | shouldKeepStoredPricesSubtotalsAndTotalAfterProductPriceChanges; domínio/serviço/repositório | Novos preços 99.99 e 25.00 confirmados por SQL depois da compra; leitura histórica permanece 10.00/7.50, subtotais 20.00/22.50, total 42.50. Sem GET /orders |
| US1.7 | shouldConfirmOnlyOrderDataWithoutLocationOrCartClearingPromise; forma exata nos testes HTTP/DTO | Sem campos/promessa de carrinho esvaziado ou Location de consulta; consumidor mantém carrinho. Não se prova uma interface futura |
| US1.8 | shouldGiveDistinctPurchasesTheirOwnIdsAndCreationInstants; identidade de repositório e novo agregado no serviço/contexto | Compras distintas, ids diferentes, cada instante conferido com seu Clock; ambos os pedidos completos por JDBC, totais 42.50/7.50. Instantes podem coincidir, ids não precisam ser contíguos |
| US1.9 | shouldCreateAnotherOrderAfterTheFirstConfirmationIsDiscarded | Primeira confirmação não consumida; JDBC verifica primeiro pedido completo antes do reenvio e ambos depois: ids distintos, dois pedidos/quatro itens e primeiro preservado. **Lacuna de transporte:** descarte do MvcResult não simula falha real de rede/socket; MockMvc recebeu 201 normalmente |
| US1.10 | shouldCreateAnotherOrderForAnIdenticalPostAfterReceivingConfirmation | Ambas as confirmações consumidas; SQL verifica primeiro antes e ambos após segundo POST idêntico, dois ids/dois pedidos/quatro itens, histórico e primeiro preservados |
| SC-001 | US1.1–4, três perfis, confirmação exata + JDBC e nove variações de segurança integrada | Todos os cenários positivos executados passaram; “100%” refere-se à matriz testada, sem garantia universal sobre entradas não exercitadas |
| SC-002 | US1.1/5; OrderServiceTest (normalização 1.000 exata), DTO/controller/contexto (centavo, 0.30 e teto decimal19,2) | Preços positivos vigentes, subtotais e soma exatos nos casos executados. Rejeições de preço/quantidade/overflow e sua ausência de gravação continuam reservadas a US2 |
| SC-003 | US1.6 e testes de histórico em domínio/serviço/repositório | Alterações posteriores confirmadas não mudam valores históricos, verificados independentemente |
| SC-005 | US1.1–3, OrderJwtAuthenticationConverterTest, OrderSecurityChainTest e nove integrações com claims falsos | Titularidade e permissões positivas comprovadas localmente. **Critério integral ainda parcial:** ausência de compra sem perfis, revogação/conta removida e demais falhas exigem matriz US2 e leitura independente sem novas linhas; 403 básico da chain não encerra essa prova |
| SC-007 | US1.4/7, OrderControllerTest e OrderDtoContractTest | Confirmação contém produtos/quantidades/preços/subtotais/total, sem consultar histórico, executar pagamento ou prometer carrinho esvaziado |
| SC-008 | US1.9/10, novo agregado no OrderServiceTest e novos ids no contexto real | Dois envios idênticos criam dois pedidos completos distintos e preservam primeiro, recebendo ou descartando primeira confirmação. Limite de perda real de transporte permanece explícito em US1.9 |

Ordem é verificada com produtos criados em ordem inversa à solicitação e leitura de position ASC, evitando aprovação acidental por productId. Subtotais/total não são colunas redundantes: SQL independente deriva valores por BigDecimal a partir de unit_price e quantity históricos, nunca de Product.price. Os reenvios idênticos usam o mesmo Clock e preservam leituras completas após cada operação; nenhum teste exige contiguidade de ids ou timestamps diferentes.

### Fronteiras que permanecem abertas

Este checkpoint fecha somente a validação positiva da US1. O contrato completo inclui regras ainda planejadas: entrada estrita/Bean Validation/campos ou chaves extras/coerção (T026/T033), rejeições de produtos/quantidades/preços/capacidade e guardas (T028/T034/T036), temporal/sub estritos (T027/T035), envelopes e precedência de erros (T037/T038), atomicidade com falha de gravação/flush/conclusão antes do commit (T029–T031/T040) e regressões completas das jornadas públicas/login (US3/T042+). Não executados esses alvos, suíte completa ou CI. SC-004/006/009 e a porção negativa de SC-005 não são aprovados por estes resultados. Default 401/403, invariant checks e regra rollbackFor não são substitutos da matriz negativa ou de rollback real. Não se declara toda a feature entregue.

### Capturas, preservação e revisão

Evidências ignoradas em `target/harness-validation/feature-004-t025-2026-10-08/`: baseline.json/preserved-before.json (1287 arquivos preexistentes de Git e evidências históricas), originais de quickstart/tasks/teste, surefire-before/ com relatórios anteriores, execution-plan.json, run-all-focused.ps1, console/result.json e XML/TXT por classe. executed-sources/ e executed-source-manifest.json capturam os 74 fontes Java usados por todos os alvos; hashes conferidos antes de cada build e após a sequência. Captura inicial terminou antes da edição; nenhuma recuperação de backup necessária. Relatórios históricos preservados apesar da atualização normal de target/surefire-reports.

`& 'target/harness-validation/feature-004-t025-2026-10-08/review.ps1'`: código 0 antes de atualizar documentação. Dos 1287 arquivos preexistentes, somente OrderRegistrationIntegrationTest mudou; 1286 mantiveram SHA256. Comparação integral limita a diferença ao ajuste US1.8/comment/import; todas as demais assertivas, US1.9/10 e evidências históricas intactos. Fontes finais iguais às capturas executadas, classes/casos/contagens/intervalos XML conferidos. test-scope-diff.txt revisado; git diff --no-index código 1 esperado pela diferença do teste, sem falha de aplicação. Conteúdo/whitespace/newline do fonte alterado revisados; `git diff --check` e `git diff --cached --exit-code` códigos 0, índice vazio. Avisos LF/CRLF capturados sem erro de whitespace.

T025 marcada [X] somente após validação/revisão. Conferência exige T001–T025 [X] e T026–T055 abertas (30), com somente a checkbox T025 alterada nesta etapa. Última concluída T025; próxima pendente T026. Git mantém seis arquivos rastreados modificados e 23 fontes não rastreados, índice vazio, mesma raiz/branch/HEAD. Alterados nesta etapa somente OrderRegistrationIntegrationTest, quickstart e tasks. Produção e demais testes preservados. Encerrado antes de T026; sem suíte completa, CI, U1/U2, stage, commit ou push.

Conferência após atualização documental: & 'target/harness-validation/feature-004-t025-2026-10-08/review.ps1' -Final, código 0. Dos 1287 arquivos preexistentes, somente o teste de registro e quickstart/tasks mudaram; 1284 mantiveram SHA256. Prefixo histórico de quickstart (223589 bytes) intacto; tasks alterou somente o byte 22915 da checkbox T025. Dez classes/144 testes GREEN, execuções seriais, todas as nove execuções US1.1–10 e fontes executados iguais conferidos. T001–T025 [X] e T026–T055 abertas; diff/whitespace sem erro, índice vazio e base Git mantida; hooks ausentes. Resultado anterior a este acréscimo preservado em final-review-before-closing.json; reaplicação final em final-review.json, sem repetir testes de aplicação.

## Checkpoint de versionamento US1 — implementação positiva T011–T025 (2026-10-08)

Fechamento solicitado para `feat: implement successful order registration`, sem repetir testes. Base conferida por Git: raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD pai esperado `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`; status inicial com seis modificados rastreados e 23 arquivos novos, índice vazio. Conferência de tasks: T001–T025 [X] e T026–T055 abertas (30). Fundação T001–T010 já pertence ao HEAD pai; este checkpoint reúne somente o incremento acumulado da US1 e seus registros.

### Evidência existente conferida, sem nova execução

Conferidos diretamente execution-plan.json, dez result.json, dez consoles e dez XML/TXT da captura T025, além do resumo final e dos fontes executados. Comandos efetivamente executados naquela etapa, em sequência, e contagens agora confirmadas:

| Comando de T025, não repetido | Testes aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | ---: | ---: | ---: | ---: | ---: |
| `.\mvnw.cmd "-Dtest=OrderAggregateTest" test` | 18 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test` | 23 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderDtoContractTest" test` | 36 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceTest" test` | 11 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderServiceContextTest" test` | 4 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderControllerTest" test` | 3 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" test` | 11 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest" test` | 20 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" test` | 9 | 0 | 0 | 0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test` | 9 | 0 | 0 | 0 | 0 |

Total confirmado: **144 aprovados, zero falhas/erros/ignorados, dez BUILD SUCCESS**, execução serial em 2026-10-08 entre 13:51:53.199 e 13:56:28.576 (-03:00). Nenhuma evidência faltante ou falha de aplicação não resolvida encontrada no checkpoint positivo. Os REDs históricos e a falha de isolamento de slice em T021 foram preservados como história; retries/correção registrados e GREEN posterior não foram reatribuídos a este fechamento. Nesta etapa, zero testes executados.

Cobertura positiva completa da fase T011–T024: domínio/agregado, schema/mapeamento/persistência, contrato e imutabilidade dos DTOs, serviço unitário, bean Clock produtivo/proxy transacional real, controller, conversor JWT, seleção/autorização da chain, US1.1–10 e autenticação integrada. XML registra três variações CLIENT/ADMIN isolado/ambos e os seis métodos US1.5–10, mais nove combinações de perfil/claims reais na integração de segurança. Rastreabilidade detalhada de cada cenário e SC-001/002/003/005/007/008 permanece na matriz T025 acima.

Preservadas as provas por conexão independente depois de HTTP 201 e fora de transação externa de teste, compra da conta autenticada, ordem das linhas, snapshots de preços, aritmética 42.50 e dois pedidos completos com ids distintos nos reenvios, mantendo o primeiro. Fonte atual de US1.8/9/10 não exige ids contíguos nem timestamps diferentes. Os 74 fontes Java atuais têm SHA256 igual às capturas utilizadas em T025.

Limitações explícitas: primeira resposta descartada em US1.9 é simulação do consumidor, não falha real de transporte. Testes positivos e saveAndFlush não substituem provas de rejeição/rollback; mocks e transação de mapeamento de teste não demonstram commit HTTP. Entrada/JWT estritos, envelopes, guardas e matriz US2/atomicidade permanecem pendentes; regressões completas US3 também. A porção negativa de SC-005 e SC-004/006/009 não estão aprovadas. Não se declara a Feature 004 integralmente entregue. Nenhum teste, suíte completa, CI, T026+, U1/U2 ou push autorizado/executado por este fechamento.

### Lista nominal revisada para stage e commit

Revisados o diff dos seis arquivos rastreados e o conteúdo integral dos 23 arquivos não rastreados. Todos os 29 caminhos abaixo pertencem ao checkpoint; stage deve usar somente a lista explícita, nunca git add . ou git add -A.

| Caminho relativo à raiz | Relação com US1 |
| --- | --- |
| `specs/004-registrar-pedido/quickstart.md` | Evidências/histórico T011–T025 e este registro |
| `specs/004-registrar-pedido/tasks.md` | Checkboxes acumuladas T011–T025; posteriores abertas |
| `src/main/java/com/devsuperior/dscommerce/config/OrderClockConfig.java` | Clock UTC necessário em T020 |
| `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java` | Chain exclusiva POST /orders e fallback T021 |
| `src/main/java/com/devsuperior/dscommerce/controllers/OrderController.java` | POST /orders, DTO/principal/proxy e 201 em T022 |
| `src/main/java/com/devsuperior/dscommerce/dto/OrderItemRequestDTO.java` | Entrada Long/Integer T019 |
| `src/main/java/com/devsuperior/dscommerce/dto/OrderItemResponseDTO.java` | Linha histórica imutável/formato monetário T019 |
| `src/main/java/com/devsuperior/dscommerce/dto/OrderRequestDTO.java` | Entrada somente items T019 |
| `src/main/java/com/devsuperior/dscommerce/dto/OrderResponseDTO.java` | Confirmação imutável/UTC/coleção protegida T019 |
| `src/main/java/com/devsuperior/dscommerce/entities/Order.java` | Agregado completo/domínio/mapeamento T017/T018 |
| `src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java` | Snapshot/posição/vínculos/constraints T017/T018 |
| `src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java` | WAITING_PAYMENT T017 |
| `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java` | Escrita JPA do agregado T018 |
| `src/main/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationConverter.java` | Sub/perfis do banco/principal mínimo T021 |
| `src/main/java/com/devsuperior/dscommerce/services/OrderService.java` | Registro/cálculos/snapshot/transação/mapping T020 |
| `src/test/java/com/devsuperior/dscommerce/config/OrderSecurityChainTest.java` | Chain real T021 e fronteira corrigida T023 |
| `src/test/java/com/devsuperior/dscommerce/config/OrderSecuritySliceTestConfig.java` | Dependências exclusivas de slices públicos em T021 |
| `src/test/java/com/devsuperior/dscommerce/controllers/GlobalExceptionHandlerRegressionTest.java` | Somente preparação de slice T021; assertivas intactas |
| `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java` | Somente preparação de slice T021; assertivas intactas |
| `src/test/java/com/devsuperior/dscommerce/controllers/OrderControllerTest.java` | Contrato HTTP positivo T015/T022 |
| `src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java` | US1.1–10 T011/T023/T024 e alinhamento temporal T025 |
| `src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java` | Identidade/perfis/claims com login/JWT reais T015 |
| `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java` | Somente preparação de slice T021; assertivas intactas |
| `src/test/java/com/devsuperior/dscommerce/dto/OrderDtoContractTest.java` | Serialização/tipos/valores/imutabilidade T019 |
| `src/test/java/com/devsuperior/dscommerce/entities/OrderAggregateTest.java` | Contrato do agregado T012/T017 |
| `src/test/java/com/devsuperior/dscommerce/repositories/OrderRepositoryTest.java` | Metadados/JPA/JDBC e defesas do schema T013/T018 |
| `src/test/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationConverterTest.java` | Conversão isolada T021 |
| `src/test/java/com/devsuperior/dscommerce/services/OrderServiceContextTest.java` | Clock/proxy/JPA reais T020 |
| `src/test/java/com/devsuperior/dscommerce/services/OrderServiceTest.java` | Orquestração unitária positiva T014/T020 |

Três testes antigos entram por dependência técnica da nova SecurityConfig; import/propriedade/annotations de slice são as únicas mudanças, conforme diff e registro T021. Não são novos testes de rejeição nem conclusão da fase de regressões. As invariantes/constraints e pré-condições monetárias já registradas em T017–T020 não constituem antecipação da matriz T026+. Sem arquivos de desserialização estrita, novos handlers/envelopes, instrumentação de rollback HTTP ou ampliação de endpoints. Product, repositório de produtos, login/JWT produtivos, Jackson global, seeds, pom, Wrapper e demais arquivos da fundação mantidos. Nenhuma mudança alheia à US1 encontrada na lista; evidências locais em target permanecem ignoradas e fora do stage.

### Auditoria deste fechamento

Captura própria em `target/harness-validation/feature-004-us1-checkpoint-2026-10-08/`: baseline.json, manifestos de 150 arquivos do workspace e 193 arquivos de evidência T025, cópias documentais, whitelist nominal verified-paths.json e review-checkpoint.ps1. `& 'target/harness-validation/feature-004-us1-checkpoint-2026-10-08/review-checkpoint.ps1' -Phase BeforeDocs`: código 0; 150 arquivos preservados, 193 evidências preservadas e 74 fontes Java idênticos à execução. Conferidos comandos/contagens/nomes XML/consoles, intervalos seriais, tasks, whitespace/newline dos 29 caminhos, Git base e índice vazio. Nenhum teste executado; documentação acrescentada preservando integralmente seu prefixo histórico e sem modificar tasks nesta etapa.

Uma leitura auxiliar combinou sintaxe de expansão de caminhos de outro shell e recebeu erro de parser PowerShell antes de executar qualquer comando; corrigida usando o diretório explícito. Sem escrita/teste/falha de aplicação nessa tentativa. O diff staged deve corresponder integralmente à whitelist e aos conteúdos revisados, com whitespace aprovado antes do commit. Hash e estado Git pós-commit serão informados no relato de entrega e registrados na captura local; não inserir hash autorreferente neste documento. Próximo ponto de retomada: **T026**, sem execução nessa etapa.

A primeira conferência BeforeStage retornou código 1 por incluir na busca a tabela T025 e a tabela repetida deste resumo; registros corretos, sem falha de teste/aplicação. Limitada a comparação à seção T025, nova conferência BeforeStage aprovada com código 0: 144 testes históricos confirmados, 149 dos 150 arquivos iniciais intactos (único acréscimo em quickstart), 193 evidências T025 e 74 fontes preservados, prefixo histórico de 241064 bytes intacto, tasks inalterado, índice vazio e whitespace aprovado. Capturas da primeira tentativa e do resultado após correção preservadas. Nenhum teste repetido.

## Checkpoint T026 — matriz de leitura MVC escrita e RED comportamental registrado (2026-10-08)

Executada exclusivamente T026, com parada antes de T027. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`), Git inicialmente limpo e índice vazio. T025 concluída no checkpoint commitado; T026 aberta. Skill speckit-implement aplicada somente ao escopo solicitado, com T026/US2.1/2/7/13, pesquisa D3, contrato OpenAPI/README, plano/modelo/constituição e registros anteriores revisados. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR correto. Checklist requirements.md 16/16 concluídos, zero abertos, PASS, somente lido. Ignore Java existente suficiente. `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Preparação de teste e preservação dos positivos

Alterado somente OrderControllerTest.java nos fontes. Duas classes JUnit @Nested e @NestedTestConfiguration(OVERRIDE) permitem contextos separados dentro do mesmo alvo. SuccessfulRegistration conserva SpringBootTest, perfil test, filtros reais, import OrderTestConfig, banco isolado, serviço real e os três métodos positivos anteriores. Seus corpos, fixtures e assertivas permanecem iguais após remover somente a indentação adicional do novo contexto. OrderHttpTestSupport, usado também por OrderSecurityIntegrationTest, permanece integralmente igual. Nenhum teste anterior desabilitado/removido ou assertiva enfraquecida; os nomes dos três métodos foram mantidos, com novo nome de suite aninhada no XML.

InputBoundary usa WebMvcTest com o OrderController real, mapper/advice existentes e somente OrderService mockado. Filtros desativados e SecurityAutoConfiguration excluída nesse contexto; não é prova de autorização, identidade JWT real, banco, commit ou rollback. Preparação verifica mock e ausência de ProductRepository/OrderRepository no slice. Não exige autenticação ou comprador no mock; a fronteira testada é leitura/validação antes da delegação. O stub retorna uma confirmação válida mesmo se MVC delegar uma entrada inválida, para que falta de validação apareça como 201/chamada de serviço, sem NPE artificial. Essa resposta mockada não demonstra compra real.

Cada entrada inválida exige, via assertAll, HTTP 400, Content-Type JSON e igualdade exata da árvore de resposta com `{"status":400,"error":"Bad Request","message":"Invalid order data","path":"/orders"}`, além de verifyNoInteractions(OrderService). São verificadas as três condições mesmo quando uma falha; subassertivas não são contadas como testes adicionais. JSON bruto é enviado diretamente a MVC, preservando chaves repetidas; somente a resposta é lida como árvore para conferir o envelope. Ausência do serviço na fronteira é condição exigida pelos testes, ainda não satisfeita em todos os casos.

### Matriz efetivamente executada

| Grupo / método no InputBoundary | Casos | Resultado atual |
| --- | ---: | --- |
| shouldRejectInvalidBodiesAndContainersBeforeService | 24 | RED: corpo ausente/vazio/espaços/ilegível; root null/array/string/número/bool; items ausente/null/vazio/objeto/string/número/bool; linha null/array/string/número/bool/objeto vazio e campos ausentes |
| shouldRejectInvalidIntegerFieldsBeforeService | 28 | RED: productId e quantity com null/0/-1, overflow positivo e negativo, 1.0/1.5/1e0, strings numérica/textual, true/false, array e objeto |
| shouldRejectExtraOrSystemFieldsBeforeService | 40 | RED: dez campos no root e na linha, cada um com valor e null: accountId/buyerId/buyer/id/createdAt/status/unitPrice/subtotal/total/unexpected |
| shouldRejectRepeatedJsonKeysBeforeService | 13 | RED: items repetido com valor igual/diferente/null/vazio e null anterior; productId/quantity repetidos com valores iguais/diferentes, zero anterior e null posterior |
| shouldRejectTheWholeInputWithAZeroQuantityBetweenValidLinesBeforeService | 1 | RED: US2.13 com linhas válidas antes/depois da quantidade zero, exigindo rejeição antes do serviço |
| shouldReadPositiveIntegerTokensIncludingBothMaximumsExactly | 2 | GREEN: tokens inteiros 1/1 e Long.MAX_VALUE/Integer.MAX_VALUE chegam exatamente como Long/Integer ao serviço observado, uma chamada por request |

Os controles máximos são exclusivamente de leitura: não afirmam existência de produto, cálculo de pedido com essas grandezas ou sucesso de persistência. Os três controles reais preservados também ficaram GREEN: shouldReturn201WithExactlyTheOrderAndItemContractFields, shouldReturnDecimalCapacityAsTextWithoutExponentOrNumericCoercion e shouldReturnTwoDecimalPlacesForUnitPriceSubtotalAndTotal. Confirmam, nesta execução, os mesmos contratos positivos anteriores com login/JWT/filtros/serviço reais, valores 42.50/0.30/teto decimal19,2 e instante UTC. Não executadas outras classes ou integrações.

### Comando, resultado e causas reais

Windows/PowerShell, 2026-10-08, offset -03:00. Uma execução, somente o alvo abaixo; Wrapper configurado para Maven 3.9.16 e XML registra Java 21.0.12.1 Azul/Zulu. Não executado --version novamente.

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest" test
```

14:37:12.327 → 14:38:02.272, código Maven **1**, **BUILD FAILURE por falhas de testes comportamentais**. Tempo Maven 44.771 s. Total **111 testes: 5 aprovados, 106 falhas, zero erros e zero ignorados**. Sem falha de compilação, contexto ou fixture. GREENs existentes não foram artificialmente quebrados. Os 106 REDs são o resultado autorizado da escrita/execução de T026; não são rejeições já implementadas ou aprovação da implementação de US2.

| Suite XML atual | Testes | Aprovados | Falhas | Erros | Ignorados | Surefire (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OrderControllerTest$SuccessfulRegistration | 3 | 3 | 0 | 0 | 0 | 12.113 |
| OrderControllerTest$InputBoundary | 108 | 2 | 106 | 0 | 0 | 12.423 |
| OrderControllerTest (container) | 0 | 0 | 0 | 0 | 0 | 24.662 |

O tempo do container inclui seus filhos; não deve ser somado como duração independente nem como testes extras. Compilar os demais fontes não significa executar outras classes. Logs/relatórios têm exatamente os três positivos preservados, dois controles de leitura e todos os 106 inválidos parametrizados/singulares. Falhas são MultipleFailuresError de assertivas, não erros de preparação.

Classificação independente pelas 106 observações `T026_BOUNDARY` e mensagens XML:

- **36 entradas já recebem 400 com zero chamadas ao serviço**. O status/rejeição básica dessa leitura é GREEN existente, preservado; o teste completo continua RED porque Content-Type/envelope JSON do pedido está ausente. O advice atual repropaga leitura ilegível fora de /login, e o resolvedor MVC retorna 400 sem o corpo contratual. Não se altera esse comportamento em T026.
- **70 entradas chegam ao serviço mockado uma vez e retornam 201**. Falham status esperado 400, envelope exato e ausência de delegação. Mapper/DTOs atuais ainda aceitam parte dos nulos/estruturas/coerções, campos extras e chaves repetidas; a validação estrita da entrada permanece T033. O 201 vem do stub de fronteira, sem cálculo/gravação reais; não é evidência de pedido inválido persistido.

Exemplo de causa registrada no XML: root extra accountId=99 recebe `Status expected:<400> but was:<201>`, confirmação com seis campos de pedido em vez do envelope de quatro campos e NoInteractionsWanted apontando a delegação do controller. Nos 36 já rejeitados, status e ausência de delegação passam; a assertiva JSON falha por resposta sem Content-Type. Essas causas são preservadas, sem adaptar o teste para aceitar o comportamento incompleto.

### Evidências, limites e fechamento

Capturas ignoradas em `target/harness-validation/feature-004-t026-2026-10-08/`: baseline.json/preserved-before.json (1502 arquivos preexistentes de Git e evidências históricas), originais de quickstart/tasks/teste, surefire-before/ com todos os relatórios anteriores, run-focused.ps1, order-controller-console.txt/result.json, reports/ com três XML/TXT, executed-sources/ e manifesto dos 74 fontes Java usados, case-results.json e input-observations.json. Captura inicial concluída antes da edição; relatórios/evidências T025 e do checkpoint commitado preservados. Runner captura stdout/stderr por cmd.exe /d /c e LASTEXITCODE real; seu término administrativo com código 0 não transforma o código Maven 1 em GREEN.

`& 'target/harness-validation/feature-004-t026-2026-10-08/review.ps1'`: código 0 antes de atualizar documentação. Dos 1502 arquivos preexistentes, somente OrderControllerTest mudou; os demais 1501 preservaram SHA256. Corpos/fixtures/assertivas dos três positivos conferidos iguais após normalizar indentação, suporte compartilhado integralmente idêntico, demais fontes e evidências intactos; fontes finais iguais às capturas executadas. XML/contagens/nomes/causas/observações revisados, `git diff --check` e `git diff --cached --exit-code` códigos 0, índice vazio; whitespace/newline conferidos. Avisos LF/CRLF existentes não são erros de whitespace. Primeira tentativa de patch com âncora de import ainda ausente foi rejeitada sem escrever; patch aplicado com âncora existente, sem execução intermediária de testes.

T026 marcada [X] após concluir a escrita dos testes e registrar a execução, como tarefa de testes antes da implementação. T001–T026 [X], T027–T055 abertas (29). Próxima pendente T027, sem execução. Este fechamento não afirma GREEN do contrato negativo, ausência de gravação em rejeições HTTP, segurança real pelo slice ou rollback. T033/T038 devem implementar leitura/Bean Validation e envelope; demais tarefas futuras mantêm seus próprios limites. Sem alteração de produção, parser, DTOs, segurança, advice, fixtures, pom, seeds ou checkpoint anterior. Alterados somente OrderControllerTest, quickstart e checkbox T026 em tasks. Sem suíte completa, CI, outras classes, U1/U2, stage, commit ou push; mesma raiz/branch/HEAD.

Conferência após atualização documental: & 'target/harness-validation/feature-004-t026-2026-10-08/review.ps1' -Final, código 0. Dos 1502 arquivos preexistentes, somente OrderControllerTest e quickstart/tasks mudaram; 1499 mantiveram SHA256. Prefixo histórico de quickstart (251739 bytes) intacto; tasks alterou somente o byte 24166 da checkbox T026. Três positivos e suporte compartilhado preservados, 74 fontes iguais às capturas executadas, 111 testes com 5 GREEN/106 falhas comportamentais/zero erros e ignorados, observações 36 vezes 400 sem serviço e 70 vezes 201 com mock conferidos. T001–T026 [X] e T027–T055 abertas; diff/whitespace aprovados, índice vazio e base Git preservada; hooks ausentes. Resultado anterior a este acréscimo preservado em final-review-before-closing.json; conferência reaplicada em final-review.json, sem repetir testes de aplicação.

## Checkpoint T027 — matriz de segurança real e guardas tardias escrita/executada (2026-10-08)

Executada exclusivamente T027/US2.4–6, com parada antes de T028. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial: quickstart.md, tasks.md e OrderControllerTest.java modificados por T026; índice vazio, sem arquivos não rastreados. Essas alterações e as evidências históricas foram preservadas. Skill speckit-implement aplicada ao escopo solicitado; tarefa/dependências, US2, pesquisa D1/D2/D7/D8, plano, constituição e contrato OpenAPI/README conferidos. Pré-requisitos PowerShell retornaram FEATURE_DIR correto, código 0. Checklist requirements.md: 16 concluídos, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente, sem hooks executáveis.

### Preparação e matriz de testes

Único fonte alterado nesta etapa: OrderSecurityIntegrationTest.java. Preservados integralmente os nove casos positivos, seus corpos, assertivas e fonte authorizedScenarios; OrderControllerTest.java e o suporte compartilhado da T026 permaneceram byte a byte iguais ao início desta etapa. Contexto SpringBootTest completo, filtros ativos, decoder/encoder reais, login real, serviços/repositórios reais, H2 isolado e open-in-view=false. Sem mock de autenticação, decoder, serviço ou persistência; sem @Transactional externo no teste.

| Grupo / método | Casos finais | GREEN | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims | 9 | 9 | 0 | 0 | 0 |
| shouldRejectInvalidJwtBeforeReadingBody | 44 | 0 | 44 | 0 | 0 |
| shouldForbidAccountWithoutProfilesEvenWithAdminClaim | 2 | 0 | 2 | 0 | 0 |
| shouldReturnInternalErrorForResolutionFailureBeforeReadingBody | 2 | 0 | 2 | 0 | 0 |
| shouldRecheckAccountAndProfilesAfterFilterBeforeService | 12 | 0 | 12 | 0 | 0 |
| shouldRejectInvalidIdentityThroughRealServiceProxy | 5 | 5 | 0 | 0 | 0 |
| shouldRecheckCurrentAccountAndProfilesThroughRealServiceProxy | 8 | 8 | 0 | 0 | 0 |
| shouldKeepRealAuthenticationAndConfirmationWithServletContextPath | 1 | 1 | 0 | 0 | 0 |
| **Total** | **83** | **23** | **60** | **0** | **0** |

Os 44 casos de JWT são 22 variantes, cada uma com corpo válido em /orders e corpo JSON ilegível contendo canary em /api/orders, usando contextPath /api e servletPath /orders. Cobrem ausência, formato ilegível, adulteração do payload conservando assinatura original, chave errada, algoritmo HS384, expiração recente, exp/iat/sub ausentes, exp igual/anterior a iat, sub com zero inicial/sinal positivo/espaço inicial/final/zero/negativo/overflow/decimal e conta ausente, iat futuro e nbf futuro. Somente ausência e formato/adulteração são deliberadamente sem JWT válido; as demais variantes têm assinatura real. A assinatura HS256 é conferida com MACVerifier; o token de chave errada é realmente assinado e não verifica com a chave original. O HS384 usa HMAC JCA com a mesma chave descartável, alterando somente o algoritmo, sem a restrição de tamanho de chave do MACSigner Nimbus para HS384. Não alterados decoder, algoritmo ou chave produtivos.

Referência temporal: depois de login/fixtures, captura de Instant.now truncada a segundos, atribuída ao ControlledClock UTC existente. Base: iat = referência -120 s e exp = referência +120 s. Expiração recente = referência -5 s; iat/nbf futuros = referência +5 s. O preparo exige envio antes de referência +5 s. São intervalos próximos do relógio real, dentro da tolerância padrão do decoder compartilhado; a futura verificação estrita da operação deve usar Clock, sem tolerância adicional. exp <= iat usa iat = referência -10 s e exp = referência -10/-11 s. Nesse caso o intervalo também é expirado por consequência lógica; não se apresenta como isolamento entre duas condições incompatíveis com um token temporalmente válido. Cada variante mantém os demais dados válidos sempre que possível, sem sleeps nem exigência de instantes diferentes entre compras.

Contas sem perfis fazem login real 200 e recebem JWT realmente assinado com roles ADMIN/ROLE_ADMIN; banco continua sem perfis. Cada uma é testada com corpo válido e ilegível/context path. A falha interna é injetada por MethodInterceptor temporário no proxy real de UserAccountRepository, somente em findByIdWithRoles da conta do caso, removido no teardown; demais métodos delegam normalmente. Essa falha ocorre **antes do SQL**, com causa contendo canaries de SQL, corpo e credenciais descartáveis, para testar classificação/confidencialidade; não simula uma indisponibilidade física real do banco. O converter e AuthenticatedAccountService reais são alcançados, uma chamada ao lookup em cada caso.

Um GenericFilterBean de teste, importado somente nessa classe e ordenado depois de Spring Security, coordena remoção da conta ou revogação de todos os perfis. Antes de mutar, exige autenticação real, principal mínimo correspondente e ROLE_CLIENT ou ROLE_ADMIN. Faz REQUIRES_NEW, confirma retorno da transação antes de delegar ao MVC e registra que a transação estava ativa. Testados CLIENT, ADMIN isolado e ambos, nas duas URIs, para ambas as mutações: 12 casos. JDBC independente confirma conta ausente ou conta existente sem linhas de perfil. Observador temporário acrescentado dentro dos advisors do serviço real confirma uma chamada de register, com transação Spring ativa, depois da mutação. Nenhum ponto de falha entrou em produção.

As 13 chamadas diretas usam o OrderService injetado e seu proxy transacional, não uma instância manual: principal ausente, id null/zero/negativo/conta ausente; conta removida e perfis revogados para conjunto vazio/CLIENT/ADMIN/ambos. Conferem BadCredentialsException ou AccessDeniedException e mensagem fixa, transação ativa no serviço, ausência de transação externa após retorno e contagens independentes intactas. Os guards já existentes ficaram GREEN. O controle positivo em /api/orders usa login de ADMIN real, confirma 201, titular/total 20.00, ausência de Location e pedido/item visíveis em conexão independente; os nove positivos originais continuam conferindo o contrato completo de confirmação.

Cada tentativa negativa exige, com assertAll, status 401/403/500 conforme o caso, Content-Type JSON, árvore exata com somente status/error/message/path e mensagem fixa do contrato, path externo e WWW-Authenticate exatamente Bearer no 401. Também exige contagens de tb_order/tb_order_item iguais ao baseline, por DriverManagerDataSource/JdbcTemplate independentes depois da operação síncrona, e ausência de transação externa. Subassertivas não contam como testes adicionais. A exceção de resolução interna que atualmente escapa de MockMvc é observada somente pelo seu tipo conhecido e falha na assertiva que exige resposta HTTP; não é convertida artificialmente em 500. Outros erros inesperados continuam propagando.

OutputCaptureExtension captura os logs, limitados ao intervalo da operação após fixtures/login. Corpo da resposta, headers e logs são conferidos contra token enviado/do login, hash real da conta, senha descartável, chave descartável em UTF-8/Base64, corpo bruto e canaries, além de padrões SQL, hash BCrypt e stack trace Java. Assertivas usam booleanos para não imprimir segredos na mensagem de falha. Nenhuma dessas assertivas de confidencialidade falhou na execução final. Isso é evidência dos caminhos/nível de logging executados, não auditoria de todos os níveis/configurações; stack traces do próprio JUnit/Surefire nos relatórios RED não são logs da aplicação. Não se afirma conformidade dos envelopes enquanto faltam respostas ou bodies.

### Comandos, resultados reais e correção de fixture

Executado somente o alvo abaixo, duas vezes serialmente; nenhuma outra classe/suíte/CI executada. Compilação dos outros fontes não constitui execução de seus testes. Windows/PowerShell, offset -03:00, Java 21.0.12.1 registrado no XML; Maven 3.9.16 selecionado pelas propriedades do Wrapper. Não repetido comando de versão.

```powershell
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test
```

| Execução em 2026-10-08 | Intervalo -03:00 | Testes | GREEN | Falhas | Erros | Ignorados | Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Primeira preparação | 14:56:59.435 → 14:58:04.982 | 83 | 23 | 56 | 4 | 0 | exit 1, BUILD FAILURE, tempo 01:00 min |
| Final, fixture corrigida | 14:59:53.338 → 15:00:57.804 | 83 | 23 | 60 | 0 | 0 | exit 1, BUILD FAILURE, tempo 59.443 s |

Primeira execução: quatro erros IllegalArgumentException `expiresAt must be after issuedAt`, nos dois casos exp <= iat vezes duas variantes de request. O encoder Spring já assina, mas tenta construir um Jwt que rejeita o intervalo durante a preparação; esses erros **não são RED comportamental**. Corrigida somente a fixture local do teste: JWTClaimsSet/SignedJWT Nimbus assinados em HS256 com a mesma chave descartável, mantendo exp/iat inválidos, sem normalizar claims. A segunda execução foi necessária para efetivamente alcançar decoder/filtros nesses quatro casos. Também ampliada a inspeção para o corpo bruto válido e observada a transação real dentro do proxy do serviço; nenhuma assertiva anterior enfraquecida. Primeira execução/fontes/relatórios preservados em attempt-1/. Não houve falha de compilação/contexto em nenhuma execução; final sem erros de fixture.

### GREEN existente e causas dos REDs finais

- Nove positivos anteriores GREEN, mais contexto /api/orders GREEN e 13 guardas diretas pelo proxy GREEN: 23 testes completos aprovados. Nenhum GREEN foi artificialmente quebrado.
- **30 casos JWT** (15 variantes × duas URIs/corpos) já retornam 401 e não gravam: ausência/malformado/adulterado/chave errada/algoritmo errado, sub ausente, exp igual/anterior a iat, espaços inicial/final, zero/negativo/overflow/decimal e conta ausente. Testes completos RED por Content-Type/body contratual ausente e, em parte deles, challenge padrão com error/error_description/error_uri em vez de Bearer sem diagnóstico. Exemplo real: token malformado retorna 401, body vazio e challenge contendo descrição de decoder `Malformed token`.
- **14 casos JWT** (sete variantes × duas URIs/corpos) revelam regras estritas ainda ausentes: expiração há 5 s, exp ausente, iat ausente, sub com zero inicial, sub com sinal +, iat futuro e nbf futuro. Com corpo válido: **sete respostas 201, cada uma com +1 pedido/+1 item** visíveis por leitura independente. Com corpo ilegível: sete respostas 400, zero gravações, em vez do 401 que deveria preceder a leitura. Não se declara rejeição/ausência de gravação nesses sete envios aceitos. Fixtures e pedidos indevidos de teste são limpos explicitamente somente depois das assertivas.
- **Dois casos sem perfis** retornam 403 e zero gravações, inclusive com roles ADMIN no token e corpo inválido; login real 200 preservado. RED pelo envelope/Content-Type ausentes.
- **Dois casos de lookup interno** alcançam o resolver e deixam zero gravações, mas OrderAccountResolutionException escapa sem resposta HTTP. Falha de assertiva pela ausência do 500/envelope; não erro de fixture, não 401 e não um 500 já demonstrado.
- **12 guardas tardias HTTP** alcançam filtro de mutação, transação separada confirmada e serviço real dentro da transação. Conta removida: seis 401; perfis revogados: seis 403; zero gravações em todos. Status/guardas já GREEN; testes completos RED somente pelo envelope/Content-Type ausentes. A adaptação MVC/segurança permanece futura.

As 60 observações HTTP negativas finais são: 36 respostas 401, oito 403, sete 201, sete 400 e duas exceções de resolução sem resposta. **53 observações sem novas linhas e sete com gravações indevidas**; não confundir com 83 testes completos ou com rollback. Envelope contratual ainda não aprovado. Regras estritas/envelopes e adaptação das exceções permanecem para T035–T038, sem correção produtiva nesta etapa.

### Evidências, preservação e limite do checkpoint

Capturas ignoradas em `target/harness-validation/feature-004-t027-2026-10-08/`: baseline.json, preserved-before.json com 1661 arquivos preexistentes (Git/evidências históricas), originals/ com documentos e ambos os testes de controller/segurança, surefire-before/, run-focused.ps1, result.json e order-security-console.txt, reports/ com XML/TXT atuais, executed-sources/ e manifesto dos 74 fontes Java, attempt-1/ com resultado/console/relatórios/fontes anteriores, case-results.json, method-results.json e http-observations.json/txt. Runner captura stdout/stderr via cmd.exe /d /c e LASTEXITCODE Maven real; código administrativo 0 do runner não significa GREEN do Maven.

`& 'target/harness-validation/feature-004-t027-2026-10-08/review.ps1'`: código 0 antes da documentação; 1660 dos 1661 arquivos preexistentes preservados, único alterado OrderSecurityIntegrationTest.java. Corpos/fonte dos nove positivos intactos, 74 fontes finais iguais aos efetivamente executados, T026/relatórios e demais históricos preservados, contagens/observações/XML conferidos; `git diff --check` e índice vazio conferidos com código 0. Uma leitura auxiliar usou inicialmente o diretório orders para OrderTestFixture e recebeu caminho inexistente; localização corrigida por rg --files para fixtures antes de editar/testar, sem alterar arquivos ou produzir RED de aplicação.

T027 marcada [X] depois de concluir testes e registrar execução, como tarefa de escrita de testes antes da implementação. T001–T027 [X], T028–T055 abertas (28). Não se afirma rejeição integral GREEN, rollback após SQL, persistência desfeita ou aprovação das regressões futuras. Não realizada simulação/falha real de transporte; permanecem os limites históricos de T024/T025. MockMvc síncrono não comprova entrega de resposta por rede. Sem GET, endpoint fictício, mudança produtiva, parser/DTO/advice/segurança/Clock, fixtures comuns, pom ou seeds; sem T028+, U1/U2, suíte completa, CI, stage, commit ou push. Próximo ponto de retomada: **T028**.

### Conferência final T027 — isolamento da adulteração e terceira execução (2026-10-08)

Após o registro acima, a revisão encontrou uma condição adicional no caso adulterado: o payload alterava sub para uma conta ausente. Corrigido somente o teste para adulterar **name**, uma claim ignorada, mantendo sub de conta existente, iat/exp válidos e perfis permitidos. O teste confere que a claim mudou, preservou sub/iat/exp e que a assinatura original deixou de verificar com MACVerifier. Isso impede atribuir 401 à ausência da conta em vez da assinatura inválida. Incluída a claim name de controle na base dos tokens; produção e assertivas anteriores preservadas.

Executado novamente, exclusivamente `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" test`: **15:07:24.601 → 15:08:29.141 -03:00**, exit Maven **1**, **BUILD FAILURE por assertivas comportamentais**, tempo Maven **59.549 s**. Resultado final: **83 testes, 23 GREEN, 60 falhas, zero erros, zero ignorados**. Mesma distribuição por método e mesmas causas descritas acima; nove positivos anteriores GREEN, 13 guards diretos GREEN e um controle de context path GREEN. Sem falha de compilação/contexto/fixture. As 60 observações HTTP mantiveram 36 respostas 401, oito 403, sete 201, sete 400 e duas exceções de resolução sem resposta; 53 baselines intactos e sete pedidos/itens indevidos visíveis. Confidencialidade das respostas/headers/logs capturados não apresentou falhas nas assertivas. A adulteração corrigida recebeu 401 sem novas gravações nas duas variantes de corpo/URI, mas o teste completo continua RED pelo contrato de erro.

Esta é a **execução final atual**; a tabela denominada final acima descreve a segunda execução, anterior ao refinamento. Foram três execuções seriais da mesma classe: primeira com quatro erros de fixture, segunda após corrigir a assinatura dos intervalos exp <= iat, terceira após isolar a adulteração. Não somar execuções repetidas como nova cobertura: permanecem 83 casos únicos na matriz. Nenhuma outra classe executada. As duas repetições decorreram de correções demonstradas no próprio teste, sem provocar RED artificial ou alterar produção.

Resultado/console/XML/fontes/observações da segunda execução e o registro documental feito então foram preservados em attempt-2/. attempt-1/ conserva a primeira. Arquivos na raiz da captura T027 e reports/executed-sources refletem a terceira execução atual. case-results.json, method-results.json e http-observations.json/txt regenerados das evidências finais reais.

Conferência final por `& 'target/harness-validation/feature-004-t027-2026-10-08/review.ps1' -Final`: código 0. Dos 1661 arquivos preexistentes, 1658 mantiveram SHA256; somente OrderSecurityIntegrationTest e os documentos quickstart/tasks mudaram nesta etapa. Prefixo histórico de quickstart com **262856 bytes** intacto, abrangendo toda T026; tasks igual ao início exceto checkbox T027. OrderControllerTest/T026 integralmente preservado, nove positivos intactos e 74 fontes iguais às capturas executadas. T001–T027 [X] e T028–T055 abertas; diff/whitespace aprovados, índice vazio e raiz/branch/HEAD preservados, sem arquivos não rastreados. Hooks continuam ausentes. Sem teste adicional após este registro, produção, T028+, suíte completa, CI, U1/U2, stage, commit ou push. Próxima tarefa: **T028**.

## Checkpoint T028 — rejeições e limites unitários do serviço escritos/executados (2026-10-08)

Executada exclusivamente T028/US2.3/8/10–12, com parada antes de T029. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial: quickstart.md, tasks.md, OrderControllerTest.java e OrderSecurityIntegrationTest.java modificados pelas etapas T026/T027; índice vazio, nenhum arquivo não rastreado. Preservados integralmente os fontes e as evidências dessas etapas e os positivos anteriores. Skill speckit-implement aplicada somente ao escopo autorizado; T028/dependência T025, requisitos US2, contrato OpenAPI/README, modelo monetário, pesquisa D4/D6/D7, plano/constituição e registros anteriores revisados. Pré-requisitos PowerShell: código 0, FEATURE_DIR correto. Checklist requirements.md: 16/16 concluídos, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Preparação compilável e fronteira da prova

Único fonte alterado: OrderServiceTest.java. Mantidos integralmente os corpos/assertivas dos 11 positivos anteriores e seus helpers. Continuam a provar, somente na unidade, preços vigentes, busca em lote, ordem restaurada, alteração anterior/posterior de preço, normalização exata de 1.000, aritmética 42.50 e novo agregado/itens por chamada. O serviço continua instanciado diretamente com Clock fixo e repositórios Mockito; não foi criado contexto Spring ou chamado proxy nesta tarefa.

As rejeições usam catchThrowable somente ao invocar register, seguido de assertAll: exigir exceção, nome completo do tipo, mensagem fixa, reconsulta de accountId no repositório de contas e zero interações com OrderRepository. Nada é lançado artificialmente pelo stub para simular rejeição. O saveAndFlush mockado conserva o agregado recebido, como nos positivos anteriores, permitindo detectar duplicata aceita sem NPE/erro de fixture artificial. O nome completo `com.devsuperior.dscommerce.services.exceptions.InvalidOrderException` é conferido como string, sem import, Class.forName ou criação da classe produtiva ausente. Assim os testes compilam contra o estado atual e falham por exceção/taxonomia comportamental incorreta; implementação e motivos explícitos continuam em T034. ProductNotFoundException existente corresponde ao 404 planejado; InvalidOrderException corresponde ao 422 planejado, sem alegar resposta HTTP nesta prova unitária.

Preço null/indisponível é uma única representação definida em research D4: Product existente com price null. Não inventada indisponibilidade física ou getter que lance falha interna. Preço zero/negativo/fração também são fixtures unitárias em memória; nenhum EntityManager, Bean Validation, schema ou JDBC é usado aqui. Product, suas validações e schema foram preservados; preparação JDBC de negativo permanece para a integração própria. Preços são BigDecimal construídos de strings, sem float/double, MathContext ou arredondamento.

### Matriz e resultados efetivamente obtidos

| Grupo / método | Testes | GREEN | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| Positivos anteriores preservados | 11 | 11 | 0 | 0 | 0 |
| shouldRejectAMissingProductBetweenValidLinesWithoutSaving | 1 | 1 | 0 | 0 | 0 |
| shouldRejectRepeatedProductWithDifferentQuantitiesWithoutConsolidatingOrSaving | 1 | 0 | 1 | 0 | 0 |
| shouldRejectInvalidPriceBetweenPositiveLinesWithoutSaving | 3 | 0 | 3 | 0 | 0 |
| shouldAcceptExactMinimumMaximumAndIntegerMaximumQuantity | 5 | 5 | 0 | 0 | 0 |
| shouldAcceptAnExactSumAtTheDecimalCapacity | 1 | 1 | 0 | 0 | 0 |
| shouldRejectPrecisionLossOrUnitAndSubtotalOverflowBetweenValidLines | 6 | 0 | 6 | 0 | 0 |
| shouldRejectSumOverflowEvenWhenEachPriceAndSubtotalFits | 1 | 0 | 1 | 0 | 0 |
| shouldRejectInvalidPrincipalBeforeRepositories | 4 | 4 | 0 | 0 | 0 |
| shouldRejectMissingAccountOrEmptyProfilesBeforeReadingProducts | 2 | 2 | 0 | 0 | 0 |
| shouldRequeryIdentityAndProfilesOnEveryCallAndNotSaveAfterRevocation | 6 | 6 | 0 | 0 | 0 |
| shouldResolveTheIdentityPassedToEachCallWithoutReusingPreviousBuyer | 1 | 1 | 0 | 0 | 0 |
| **Total** | **42** | **31** | **11** | **0** | **0** |

- US2.3: produto ausente no meio de dois válidos, lote retornando os existentes fora da ordem; ProductNotFoundException / `Product not found`, sem chamada ao repositório de pedidos: GREEN.
- US2.8: linhas FIRST quantity=2, SECOND quantity=3, FIRST quantity=4, com preços válidos. Exigida rejeição `Duplicate product in order`, sem consolidação/mutação das linhas e sem save; request permanece com as três quantidades originais. **RED**: nenhuma exceção; uma chamada a saveAndFlush no mock. Isso demonstra aceitação do agregado na unidade, não gravação no banco nem comportamento da constraint JPA.
- US2.11/12 e complemento preço indisponível: item intermediário com null/zero/-0.01, entre dois preços positivos. Exigidos InvalidOrderException / `Product price is not valid for order`, preços originais intactos e nenhum save. **Três REDs**: guarda numérica já rejeita antes do save, mas lança IllegalArgumentException / `Order value must be positive`, sem o motivo de domínio exigido.
- US2.10, positivos: 0.01×1=0.01; teto 99999999999999999.99×1; 0.01×Integer.MAX_VALUE=21474836.47; 10.00×Integer.MAX_VALUE=21474836470.00; 33333333333333333.33×3=teto. Verificados subtotal/total do agregado e strings exatas no DTO, quantity intacta e preço do Product inalterado. **Cinco GREENs**. Soma 99999999999999999.98+0.01=teto: **um GREEN**, com ordem dos itens preservada.
- US2.10, precisão: preços 1.001, 1.005 (quantity=2), 0.009 e 1.0000000000000000000001, sempre em linha entre válidas. **Quatro REDs** por ArithmeticException / `Rounding necessary` em vez de InvalidOrderException / `Order value cannot be represented`; nenhum save e Product inalterado. Rejeição numérica sem arredondamento já está GREEN como subcondição, sem quebrá-la artificialmente.
- US2.10, capacidade: preço 100000000000000000.00; subtotal 50000000000000000.00×2; soma teto+0.01, com cada preço/subtotal da soma representável. **Três REDs** por IllegalArgumentException / `Order value exceeds decimal19,2` em vez do tipo/mensagem de domínio; nenhum save. Valores válidos anteriores não são gravados separadamente.
- Guardas: principal ausente/id null/zero/negativo rejeitados com BadCredentialsException antes dos três repositórios: **quatro GREENs**. Conta ausente ou sem perfis: reconsulta por accountId, BadCredentialsException ou AccessDeniedException com mensagem fixa, sem leitura de produtos/save: **dois GREENs**.
- Reconsulta entre chamadas: CLIENT, ADMIN isolado e ambos, combinados com conta removida ou todos os perfis revogados no retorno seguinte do mock: **seis GREENs**. Primeira chamada válida salva uma vez; segunda reconsulta, rejeita antes de produtos e não acrescenta save. Agregado/DTO da primeira chamada continuam históricos. Os seis diagnósticos registram saveCalls=1 e baselineSaveCalls=1, isto é, delta zero na segunda chamada; não são seis saves indevidos. Outra identidade ADMIN passada na segunda chamada resolve seu próprio id, buyer e agregado: **um GREEN**.

Os **31 GREENs** são 11 positivos existentes e 20 casos novos atendidos pelo comportamento atual. Os **11 REDs** são uma duplicata não rejeitada e dez rejeições com tipo/mensagem genéricos; não representam 11 entradas salvas. Doze observações de rejeição de negócio: uma ProductNotFoundException sem save, seis IllegalArgumentException sem save, quatro ArithmeticException sem save e uma duplicata sem exceção com save. Outras 12 observações de guardas confirmam os resultados unitários descritos. Subassertivas de assertAll não são testes adicionais. Sem erro de compilação, contexto, preparação ou fixture.

### Comando e execução atual

Uma execução, exclusivamente esta classe, Windows/PowerShell, 2026-10-08, offset -03:00. Java 21.0.12.1 registrado no XML; Maven 3.9.16 selecionado pelo Wrapper. Não executado --version novamente.

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTest" test
```

**15:19:24.137 → 15:19:54.843**, código Maven **1**, **BUILD FAILURE por falhas comportamentais**. Tempo Maven **25.640 s**, Surefire desta classe **4.990 s**. XML: **42 testes, 31 aprovados, 11 falhas, zero erros e zero ignorados**. Compilar demais fontes não significa executar outras classes. Nenhum alvo repetido, suíte completa, CI ou teste de T029+ executado. Exemplo de causa no XML: soma excedente esperava InvalidOrderException / `Order value cannot be represented`, recebeu IllegalArgumentException / `Order value exceeds decimal19,2`; duplicata falhou por ausência de exceção e NoInteractionsWanted apontando saveAndFlush do serviço real instanciado diretamente.

### Evidências, preservação e limites

Capturas ignoradas em `target/harness-validation/feature-004-t028-2026-10-08/`: baseline.json, preserved-before.json com 1983 arquivos preexistentes (Git/evidências históricas), originals/ com quickstart/tasks/OrderServiceTest, surefire-before/ com os relatórios anteriores, run-focused.ps1, result.json/order-service-console.txt, reports/ com XML/TXT desta execução, executed-sources/ e manifesto dos 74 fontes Java, case-results.json, method-results.json, observations.txt e source-diff.txt. Runner usa cmd.exe /d /c para stdout/stderr e captura LASTEXITCODE Maven real; código administrativo 0 do runner não transforma Maven exit 1 em aprovação.

`& 'target/harness-validation/feature-004-t028-2026-10-08/review.ps1'`: código 0 antes da documentação. Dos 1983 arquivos iniciais, 1982 preservaram SHA256; único fonte alterado OrderServiceTest.java. T026/T027 e suas evidências históricas intactas, 11 positivos e helpers originais iguais, todos os 74 fontes finais iguais às capturas executadas. XML/contagens/24 observações revisados; diff/whitespace aprovados (`git diff --check`, código 0), índice vazio (`git diff --cached --exit-code`, código 0). Avisos LF/CRLF não são erros de whitespace.

T028 marcada [X] somente após escrever/compilar/executar os testes e registrar o resultado, como tarefa de testes antes da implementação. T001–T028 [X]; T029–T055 abertas (27). Não implementados InvalidOrderException, rejeições produtivas ou correções de OrderService; essas responsabilidades permanecem T034/T036. Não alterados Product/validações/schema, produção, DTOs, segurança, parser, advice, Clock, fixtures comuns, pom ou seeds. Sem U1/U2, stage, commit ou push.

Mocks demonstram cálculos, dados entregues ao repositório e presença/ausência de chamadas; **não comprovam persistência, commit, rollback ou execução pelo proxy Spring**. Evidência de proxy/guardas reais pertence a T027, preservada, sem repetir sua classe. Ausência de interações nesta unidade não substitui a leitura independente do banco das integrações; provas após SQL permanecem para suas tarefas próprias. Regressões e rejeições HTTP futuras não são aprovadas por estes GREENs. Próximo ponto de retomada: **T029**, sem execução nesta etapa.

Conferência documental final: & 'target/harness-validation/feature-004-t028-2026-10-08/review.ps1' -Final, código 0. Dos 1983 arquivos iniciais, 1980 mantiveram SHA256; somente OrderServiceTest e quickstart/tasks mudaram nesta etapa. Prefixo histórico de quickstart de 280643 bytes intacto, incluindo T026/T027; tasks alterou exclusivamente a checkbox T028 em relação ao início. Positivos/helpers e 74 fontes executados conferidos, 42 testes com 31 GREEN/11 falhas/zero erros e ignorados, diff/whitespace aprovado e índice vazio. Git final contém os quatro caminhos modificados anteriores mais OrderServiceTest, sem não rastreados; raiz/branch/HEAD iguais ao início. Conferência reaplicada após este acréscimo sem repetir testes de aplicação.

## Checkpoint T029 — falha SQL tardia e leitura independente de rollback (2026-10-08)

Executada exclusivamente T029, com parada antes de T030. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial: quickstart.md, tasks.md, OrderControllerTest.java, OrderSecurityIntegrationTest.java e OrderServiceTest.java modificados por T026–T028; índice vazio, nenhum arquivo não rastreado. Preservados integralmente esses fontes, positivos e evidências anteriores. Skill speckit-implement aplicada somente ao escopo autorizado; T029/dependência T025, US2, pesquisa D6/D8, modelo, plano/constituição, contrato OpenAPI e procedimentos do quickstart conferidos. Pré-requisitos PowerShell: código 0, FEATURE_DIR correto. Checklist requirements.md 16/16 concluídos, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Preparação e observação de SQL real

Criado somente `src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java` nos fontes. SpringBootTest com H2 exclusivo `order-test-<UUID>`, perfil test, open-in-view=false, MockMvc síncrono/filtros ativos, login/JWT/decoder reais, OrderService Spring com proxy e repositórios/JPA reais. Sem mocks, autenticação simulada, @Transactional no teste/classe ou transação externa envolvendo a operação. Reutilizados OrderTestConfig, Clock e OrderTestFixture sem editar seus fontes. Fixtures de conta/perfis/produtos são confirmadas em REQUIRES_NEW pelo suporte existente; presença e preço são conferidos em conexão JDBC independente antes da requisição. Produto negativo preparado por setProductPrice/JDBC em transação separada; Product, suas validações e schema produtivo preservados. Null permanece na unidade de T028.

Uma TestConfiguration importada exclusivamente nesta classe decora o DataSource real, preservando conexões/PreparedStatements reais. Não substitui gravação por mock nem injeta exceção no repositório/serviço. Connection/PreparedStatement proxies delegam chamadas ao driver; só depois de executeUpdate/executeLargeUpdate/execute retornar com sucesso registram affected rows e leem pedido/itens **na mesma conexão física**, ainda na transação do serviço. Os parâmetros de product_id identificam a linha executada. Após SQLException real, registram SQLState e as linhas ainda visíveis nessa conexão, antes da propagação da exceção. Não consomem generatedKeys, não fazem commit/rollback e não usam SQL impresso em log como prova. A sonda fica desarmada nas fixtures/limpeza. Não há ponto de falha em produção.

Na falha tardia, instalado CHECK temporário `ck_t029_second_<UUID>` que rejeita somente o segundo productId. DDL executado por DriverManagerDataSource independente/autocommit, antes do POST e fora da transação do pedido. Existência confirmada em information_schema; constraint permanece instalada durante todas as assertivas de SQL, baseline e HTTP. Removida no @AfterEach da subclasse, somente depois das verificações (inclusive quando falham), antes da limpeza herdada de fixtures. Baseline é snapshot completo de tb_order/tb_order_item, com IDs, vínculos, instante/estado, posições, quantidades e preços; leitura final usa nova conexão independente após retorno síncrono/saída excepcional do proxy e antes de DDL/cleanup.

### Evidência 1 — inserts concluídos antes da falha tardia

Execução final registrou, em ordem:

| Operação JDBC real | Retorno / linhas afetadas | Linhas visíveis na conexão do pedido | Transação | Resultado |
| --- | --- | --- | --- | --- |
| INSERT tb_order | sucesso, 1 | um pedido, zero itens | ativa, autoCommit=false | concluído |
| INSERT primeiro tb_order_item, productId 70 | sucesso, 1 | mesmo pedido e primeiro item | ativa, autoCommit=false | concluído |
| INSERT segundo tb_order_item, productId 71 | SQLException 23513 | mesmo pedido e somente primeiro item | ativa, autoCommit=false | CHECK exclusivo identificado |

Os IDs 70/71 são observados nesta execução, não expectativas fixas/contíguas. O primeiro item observado tem position=0, quantity=2, unitPrice=10.00 e vínculo ao pedido observado; subtotal derivado 20.00. A view após a falha é igual à view após o primeiro item. A exceção corresponde à constraint temporária identificada, não a uma rejeição de validação ORM ou falha de preparação. Todas essas assertivas passaram. Driver retornou sucesso antes da leitura das linhas: não é callback anterior ao execute, contagem só de tentativas, save mockado ou SQL no log.

### Evidência 2 — rollback integral depois da saída

Falha tardia: baseline independente **zero pedidos/zero itens**; após a exceção sair do proxy/requisição, snapshot independente voltou integralmente ao baseline **zero/zero**, com a constraint ainda presente e sem limpeza intermediária. Passou a assertiva de igualdade do snapshot completo. Isso comprova rollback do pedido e primeiro item realmente inseridos nessa transação, no H2 isolado desta execução. **Rollback já GREEN, preservado; nenhuma correção produtiva necessária/demonstrada para esta fronteira.** Não confundir o RED HTTP abaixo com falha de rollback.

Controle positivo com ADMIN isolado: login real 200, POST /orders **201**, confirmação completa 42.50 (10.00×2 + 7.50×3), id/titular/UTC em milissegundos/WAITING_PAYMENT, ordem das linhas e strings monetárias conferidos pelo suporte positivo preservado. Após retorno, conexão independente encontrou **+1 pedido/+2 itens**, vinculados ao comprador, posições 0/1, quantidades 2/3, unitPrice 10.00/7.50, subtotais 20.00/22.50 e soma 42.50; nenhum Location de consulta. Sonda observou três inserts concluídos na transação. Esse controle demonstra persistência visível após sucesso; não se exigem IDs contíguos nem instantes diferentes.

### Evidência 3 — status e envelope HTTP

Falha SQL tardia exige HTTP 500 e JSON exatamente `{"status":500,"error":"Internal Server Error","message":"Order registration could not be completed","path":"/orders"}`, sem confirmação 201, Location ou token. Resultado atual: DataIntegrityViolationException **escapa de MockMvc sem resposta HTTP**, pois falta o adaptador MVC correspondente; não foi fabricado um status 500. O teste completo é RED somente na assertiva de resposta HTTP, enquanto inserts reais, rollback integral e constraint durante as verificações estão GREEN. Não se declara envelope seguro já implementado quando não há resposta.

O helper registra a exceção real tanto quando escapa quanto quando futuramente for resolvida pelo MVC via MvcResult.getResolvedException; a assertiva continua exigindo DataIntegrityViolationException real na falha SQL. Capture de ServletException reconhece somente as causas atuais conhecidas DataIntegrityViolationException/IllegalArgumentException; falhas inesperadas de contexto/instrumentação/fixture continuam erros. Nunca cria endpoint/advice fake ou transforma exceção em resposta de teste. AssertAll permite conferir baseline/SQL mesmo quando o contrato HTTP falta; subassertivas não são testes adicionais.

### Evidência 4 — rejeições anteriores à gravação e fronteiras atuais

| Caso no mesmo contexto | HTTP previsto | HTTP atual | Inserts concluídos / falhos | Snapshot independente após operação | Resultado completo |
| --- | ---: | --- | --- | --- | --- |
| Controle positivo | 201 | 201, confirmação exata | 3 / 0 | +1 pedido/+2 itens | GREEN |
| Inexistente entre válidos | 404 | 404, envelope exato Product not found | 0 / 0 | baseline intacto | GREEN |
| Repetido com quantities 2 e 4 e outros válidos | 422 | sem resposta; DataIntegrityViolationException | 3 / 1 | baseline intacto | RED |
| Preço zero entre positivos | 422 | sem resposta; IllegalArgumentException | 0 / 0 | baseline intacto | RED |
| Preço negativo -0.01 entre positivos, fixture JDBC confirmada | 422 | sem resposta; IllegalArgumentException | 0 / 0 | baseline intacto | RED |
| Quantity zero entre válidos | 400 | sem resposta; IllegalArgumentException | 0 / 0 | baseline intacto | RED |
| CHECK SQL tardio exclusivo do segundo produto | 500 | sem resposta; DataIntegrityViolationException | 2 / 1 | baseline intacto | RED somente HTTP |

Inexistente/preços zero/negativo/quantity zero: **quatro rejeições sem inserts** observadas; somente inexistente já cumpre status/envelope completos. Guardas numéricas impedem save para preços/quantity inválidos, mas não implementam ainda a taxonomia de domínio/validação e respostas contratuais. Não afirmar que o 400/422 foi entregue.

Duplicata: pedido e dois primeiros itens foram inseridos com sucesso dentro da transação; terceira tentativa de item repetido falhou na unicidade real, SQLState **23505**. As views confirmam um pedido com zero/um/dois itens antes da falha; snapshot independente pós-proxy voltou a zero/zero. Portanto a defesa SQL e rollback funcionam, mas **a rejeição de negócio antes de qualquer gravação ainda falta**, além do HTTP 422/envelope. O teste exige zero SQL e permanece RED nessa dimensão, sem enfraquecer a assertiva para aceitar fallback da constraint. Não houve consolidação nem nova linha persistida após saída.

### Comandos, contagens e causas reais

Executado somente o alvo abaixo, duas vezes serialmente, Windows/PowerShell, 2026-10-08, offset -03:00. Java 21.0.12.1 no XML; Maven 3.9.16 pelo Wrapper. Não repetido --version, outras classes, suíte completa ou CI.

```powershell
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest" test
```

| Execução | Início → fim -03:00 | Testes | GREEN | Falhas | Erros | Ignorados | Resultado Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Primeira | 15:39:33.128 → 15:40:20.655 | 7 | 2 | 5 | 0 | 0 | exit 1, BUILD FAILURE, 42.090 s |
| Final | 15:41:56.417 → 15:42:42.983 | 7 | 2 | 5 | 0 | 0 | exit 1, BUILD FAILURE, 41.577 s |

Surefire final **20.239 s**, **7 testes: 2 aprovados, 5 falhas comportamentais, zero erros e zero ignorados**. Os dois métodos singulares são controle positivo e falha SQL tardia; o método parametrizado tem cinco casos. GREENs não foram artificialmente quebrados. Falhas são MultipleFailuresError de assertivas: duplicata falha por SQL alcançado e ausência de resposta; late SQL/preços/quantity falham somente por ausência da resposta HTTP. Sem falha de compilação/contexto/fixture em nenhuma execução. Compilar outros fontes não significa executar seus testes.

A revisão após a primeira execução identificou que a observação do Throwable deveria também usar getResolvedException quando o futuro handler entregar HTTP 500. Corrigida somente a preparação desse observador e mantidas todas as exigências de SQL/rollback/tipo/status/envelope; uma segunda execução desta mesma classe confirmou as fontes finais. Nenhuma correção de produção ou RED artificial. Primeira execução, console, relatórios e fontes preservados em attempt-1/. São sete casos únicos, não 14 cenários de cobertura.

### Evidências, preservação e limite do checkpoint

Capturas ignoradas em `target/harness-validation/feature-004-t029-2026-10-08/`: baseline.json e preserved-before.json com **2139 arquivos preexistentes**, originals/ (quickstart/tasks), surefire-before/, run-focused.ps1, result.json/order-rollback-console.txt, reports/ XML/TXT, executed-sources/ e manifesto dos **75 fontes Java**, attempt-1/, case-results.json, result-observations.json e observations.txt (retornos JDBC e baselines). O runner captura stdout/stderr via cmd.exe /d /c e LASTEXITCODE Maven real; código administrativo 0 não converte Maven exit 1 em GREEN. Os arquivos próprios desta captura estão ignorados e fora do stage.

`& 'target/harness-validation/feature-004-t029-2026-10-08/review.ps1'`: código 0 antes da documentação. Todos os **2139 arquivos preexistentes** mantiveram SHA256; somente o novo teste estava não rastreado. T026–T028 e seus relatórios/fontes históricos integralmente preservados, 75 fontes finais iguais aos efetivamente executados, XML/contagens/observações revisados. `git diff --check` código 0; conteúdo completo do novo arquivo lido e whitespace/newline conferidos diretamente e por `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`: sem diagnósticos, código 1 pela diferença do arquivo novo. Índice vazio conferido com código 0. Avisos LF/CRLF não são falhas de whitespace.

T029 marcada [X] somente após concluir a escrita/execução e registrar as dimensões reais da prova. T001–T029 [X]; T030–T055 abertas (26). Nenhuma mudança produtiva, Product/validações/schema permanente, mapper/advice/segurança/DTOs/serviço/repositórios/fixtures comuns/pom/seeds alterados. CHECK é temporário exclusivo deste banco de teste e foi removido depois das verificações. Não implementados spy que lança depois de saveAndFlush ou callback beforeCommit; cenários T030/T031 permanecem futuros.

Limites: prova de rollback por constraint SQL no H2 local, não falha física de commit, processamento posterior ao flush, callbacks de conclusão, perda real de transporte ou segurança completa/regressões futuras. Ausência de rows após guardas que não executaram insert não é prova de rollback após SQL. Envelopes 400/422/500 continuam pendentes para implementação própria; o resultado Maven RED não invalida a evidência de rollback já GREEN. Sem T030+, suíte completa, CI, U1/U2, stage, commit ou push. Próximo ponto de retomada: **T030**.

Conferência final documental por & 'target/harness-validation/feature-004-t029-2026-10-08/review.ps1' -Final: código 0. Dos 2139 arquivos preexistentes, 2137 preservaram SHA256; somente quickstart/tasks mudaram nesta etapa, além do novo OrderRollbackIntegrationTest não rastreado. Prefixo histórico de quickstart com 292751 bytes intacto, abrangendo T026–T028; tasks difere do início exclusivamente pela checkbox T029. Fontes anteriores preservados e 75 fontes finais iguais aos executados, 7 testes/2 GREEN/5 RED/zero erros e ignorados, inserts reais e retorno ao baseline conferidos separadamente do HTTP. Conteúdo/diff do novo arquivo e dos documentos registrados em new-source-diff.txt/documentation-diff.txt, whitespace/newline aprovados, índice vazio e base Git mantida. T001–T029 [X], T030–T055 abertas; única não rastreada é a classe nova. Auditoria reaplicada após este acréscimo, sem repetir testes de aplicação.

## Checkpoint T030 — falha de processamento após saveAndFlush real (2026-10-08)

Executada exclusivamente T030, dependente de T029, com parada antes de T031. Raiz confirmada `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial/final contém as modificações anteriores em quickstart.md, tasks.md, OrderControllerTest.java, OrderSecurityIntegrationTest.java e OrderServiceTest.java, além de OrderRollbackIntegrationTest.java não rastreado desde T029. Índice vazio. Nenhuma alteração anterior descartada ou incluída no stage. Skill speckit-implement aplicada somente ao pedido; T030, dependência T029, US2.9/FR-012/SC-004, plano, pesquisa D6/D8, modelo, constituição, contrato e histórico conferidos. Pré-requisitos da feature: código 0, diretório correto. Checklist requirements.md: 16/16, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente, sem hooks executáveis.

### Preparação exclusiva de teste e preservação de T029

Acrescentado o método `shouldRollbackAfterRealSaveAndFlushReturnsBeforeServiceCompletes` à classe existente OrderRollbackIntegrationTest. Mantidos SpringBootTest/H2 isolado, perfil test, MockMvc síncrono com filtros reais, login/JWT/decoder reais, serviço Spring chamado por seu proxy e JPA reais. Sem @Transactional no teste/classe e sem transação externa envolvendo o POST. Fixtures de conta/perfis/produtos são confirmadas em transações separadas pelo suporte existente e conferidas por JDBC independente antes do baseline. Product, validações, schema produtivo, serviço, DTOs, segurança, advice e fixtures comuns não foram alterados.

Uma RepositorySpyConfig importada exclusivamente por esta classe cria um spy Mockito do repositório efetivamente injetado pelo construtor de OrderService. A identidade dessa referência é verificada no alvo do proxy do serviço. Uma interface de encaminhamento sem Advised evita o desembrulhamento automático de proxies pelo resolvedor Mockito do Spring Boot; cada método encaminha ao proxy Spring Data original, que não é mock. O spy chama essa implementação de encaminhamento normalmente: **nenhum save/saveAndFlush é stubado ou substituído por resultado simulado**. O saveAndFlush original executa JPA/SQL reais, com os interceptores originais do repositório preservados.

Somente depois de saveAndFlush retornar normalmente, a sonda armada de T030 captura o id e observa pedido/itens por JdbcTemplate ligado ao DataSource da transação do serviço. Desembrulha o ConnectionProxy que suprime close antes de conferir DataSourceUtils.isConnectionTransactional; verifica transação ativa e autoCommit=false e lê usando essa mesma conexão vinculada. Confirma um pedido com os dois itens antes de lançar a RuntimeException exclusiva PostFlushTestFailure. O serviço ainda aguarda o retorno da chamada ao repositório: o ponto está no corpo de register, antes do mapping/retorno do DTO e da conclusão transacional pelo interceptor. Não registra TransactionSynchronization, beforeCommit/afterCommit ou callback de T031.

T030 exige ausência da constraint temporária `CK_T029_SECOND_%` e não a instala. A sonda SQL de T029 continua somente observadora de retornos efetivos do driver. Seu código e as assertivas de T029 (falha SQL, cinco rejeições e controle positivo) foram preservados. O helper de saída excepcional passou a reconhecer também a causa de teste PostFlushTestFailure, além das causas já previstas; conserva a exceção real por identidade e não fabrica resposta/status HTTP. Erros inesperados de preparação/instrumentação continuam erros, não RED comportamental. A sonda nova é desarmada somente no AfterEach, depois das verificações e antes da limpeza herdada.

### Evidência 1 — flush real concluído e rows na mesma transação

Na execução final, houve exatamente uma chamada ao spy de saveAndFlush. O proxy real retornou o pedido com id **2** (valor observado, sem expectativa fixa ou contígua). Três inserts JDBC retornaram sucesso, uma linha afetada cada, nesta ordem: tb_order, primeiro tb_order_item e segundo tb_order_item; zero inserts falhos. A leitura depois do retorno de saveAndFlush encontrou **um pedido e dois itens**, transação ativa, conexão confirmada como vinculada, autoCommit=false. Essas assertivas passaram; não é prova baseada em SQL impresso no log ou em tentativas de insert.

Conferidos no snapshot transacional: id retornado, comprador vindo do login real, createdAt UTC truncado a milissegundos, WAITING_PAYMENT, vínculo dos dois itens ao mesmo pedido, ordem/position 0 e 1, produtos na ordem solicitada, quantidades 2 e 3, unitPrice 10.00 e 7.50. Subtotais derivados exatos 20.00/22.50 e soma 42.50. Subtotais/total derivam das quantidades e preços históricos; não se afirma que sejam colunas persistidas. Nenhuma exigência de IDs contíguos ou instantes diferentes. SaveAndFlush concluído **não equivale a commit**.

Observação real: `T030_FLUSH returned=true orderId=2 tx=true boundConnection=true autoCommit=false visibleOrders=1 visibleItems=2 completedInserts=3 failedInserts=0`.

### Evidência 2 — RuntimeException no ponto previsto

Depois de confirmar o retorno real e as linhas, a sonda lançou a RuntimeException exclusiva de teste. Passaram as assertivas de ponto alcançado e de identidade: a exceção que saiu do serviço/proxy/MVC é o mesmo objeto PostFlushTestFailure lançado pela sonda. Não ocorreu falha SQL, constraint temporária, save simulado, falha física de commit ou callback de conclusão. A chamada de repositório ainda não havia devolvido o controle ao restante do corpo do serviço.

Observação real: `T030_FAULT thrown=true sameException=true http=NONE escaped=PostFlushTestFailure`.

### Evidência 3 — rollback integral por leitura independente antes de limpeza

Baseline completo de tb_order/tb_order_item: **zero pedidos/zero itens**. Após a operação sair do proxy/MockMvc, um bloco finally executou snapshot por DriverManagerDataSource independente, em nova conexão, sem transação de teste. Essa leitura ocorre inclusive diante de exceção MVC inesperada, antes de qualquer desarme/DDL/limpeza de fixtures. Na execução final, voltou a **zero/zero**, com igualdade do snapshot completo ao baseline; a assertiva passou. Pedido e ambos os itens efetivamente inseridos foram revertidos pelo proxy real. **Rollback desta fronteira já GREEN; preservado sem correção produtiva.**

Observação real: `T030_INDEPENDENT baselineOrders=0 baselineItems=0 afterOrders=0 afterItems=0 baselineRestored=true`. Os três inserts observados e a leitura transacional antecedem a exceção; a leitura independente posterior antecede cleanup. A evidência não é ausência de escrita em mocks nem limpeza automática de uma transação externa de teste.

### Evidência 4 — HTTP e envelope ainda RED

Mantida a exigência de HTTP **500**, sem 201/Location, application/json e envelope exatamente `{"status":500,"error":"Internal Server Error","message":"Order registration could not be completed","path":"/orders"}`. O corpo exato e a mensagem fixa vedam dados recebidos, token/hash/segredo, SQL, stack trace e diagnósticos internos. A assertiva adicional de ausência do token permanece.

Resultado real: PostFlushTestFailure escapa como causa de ServletException pelo MockMvc **sem resposta HTTP**. A única falha final é `[failure must reach MVC as an HTTP error response] Expecting actual not to be null`, agrupada por assertAll em MultipleFailuresError com **uma** subfalha. Não se fabricou um 500 nem se aprovou envelope seguro ausente. Nenhuma confirmação 201 foi recebida nesse cenário. As dimensões de flush real, exceção prevista e rollback estão GREEN; **o RED HTTP não significa falha de rollback**. Tratamento produtivo do erro permanece nas tarefas próprias; não corrigido nesta etapa.

### Controle positivo preservado

Executado, sem alterar suas assertivas, `shouldCommitCompleteOrderAndItemsVisibleAfterHttp201`: login ADMIN isolado real 200, POST /orders **201**, confirmação exata de 42.50 com comprador/id/instante UTC em milissegundos/WAITING_PAYMENT, itens na ordem e strings monetárias, sem Location. Leitura independente após retorno confirmou **+1 pedido/+2 itens**, snapshots anteriores preservados, vínculos/position/quantity/unitPrice/subtotais/soma corretos. Sonda observou três inserts concluídos. Nova falha fica desarmada neste controle e a delegação real persiste normalmente.

Observação real: `T029_CONTROL status=201 ordersDelta=1 itemsDelta=2 completedInserts=3`. GREEN final preservado. Os métodos de constraint/rejeições de T029 não foram executados; somente suas fontes/assertivas foram conferidas por leitura.

### Comando e contagens efetivamente executadas

Executado **somente o filtro abaixo**, quatro vezes serialmente por necessidade demonstrada de corrigir preparação exclusiva do teste. Em todas as tentativas, apenas esta classe e estes dois métodos foram selecionados. Não executar a classe inteira, outros alvos, suíte completa ou CI. Windows/PowerShell, 2026-10-08, offset -03:00; Java 21.0.12.1 no XML, Maven 3.9.16 pelo Wrapper. Não repetido --version. Compilação dos outros fontes não equivale a executar seus testes.

```powershell
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest#shouldRollbackAfterRealSaveAndFlushReturnsBeforeServiceCompletes+shouldCommitCompleteOrderAndItemsVisibleAfterHttp201" test
```

| Tentativa | Início → fim -03:00 | Testes | GREEN | Falhas | Erros | Ignorados | Maven / causa |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Preparação 1 | 15:59:23.542 → 16:00:06.193 | 2 | 0 | 0 | 2 | 0 | exit 1, BUILD FAILURE, 37.314 s; contexto bloqueado por resolvedor Mockito desembrulhando proxy JPA durante stubbing |
| Preparação 2 | 16:01:20.510 → 16:02:08.204 | 2 | 1 | 0 | 1 | 0 | exit 1, BUILD FAILURE, 42.797 s; ClassCastException antes do POST por inferência da sobrecarga de reflexão |
| Preparação 3 | 16:02:35.495 → 16:03:24.387 | 2 | 1 | 0 | 1 | 0 | exit 1, BUILD FAILURE, 43.914 s; sonda comparava wrapper close-suppressing em vez da conexão vinculada |
| Final | 16:04:46.169 → 16:05:34.851 | 2 | 1 | 1 | 0 | 0 | exit 1, BUILD FAILURE, 43.693 s; somente resposta HTTP 500/envelope ausentes |

Surefire final **21.347 s**, **2 testes únicos: 1 GREEN, 1 falha comportamental, zero erros e zero ignorados**. Método positivo 2.860 s; novo cenário 0.547 s. Não são oito cenários distintos. As três primeiras tentativas são falhas de contexto/fixture/instrumentação e **não contam como RED comportamental**; não demonstram regra produtiva ausente. As correções foram restritas à configuração/observação do teste: interface de encaminhamento real sem Advised e sem stubbing, variável tipada OrderService antes de ReflectionTestUtils.getField e getTargetConnection antes da verificação da conexão vinculada. O código local de DataSourceUtils foi conferido por javap para esta última causa. Compilação passou em todas; na execução final contexto, fixtures e todas as sondas passaram. Não se enfraqueceu qualquer assertiva nem se provocou RED artificial de rollback ou controle positivo.

### Evidências, revisão e limite da etapa

Capturas ignoradas em `target/harness-validation/feature-004-t030-2026-10-08/`: baseline.json, preserved-before.json com **2376 arquivos preexistentes**, originals/ de quickstart/tasks/classe existente não rastreada, surefire-before/, run-focused.ps1, result.json, order-post-flush-console.txt, reports/ XML/TXT, executed-sources/ e manifesto dos **75 fontes Java**, case-results.json, observations.txt e review.ps1. Tentativas de preparação completas preservadas em attempt-1-fixture/, attempt-2-fixture/ e attempt-3-fixture/, cada uma com comando/tempos/console/relatórios/fontes executados. O runner captura stdout/stderr via cmd.exe /d /c e LASTEXITCODE real do Maven; código administrativo 0 do runner não transforma Maven exit 1 em GREEN.

Conferência antes da documentação por `& 'target/harness-validation/feature-004-t030-2026-10-08/review.ps1'`: código 0. **2375/2376 arquivos** preservaram SHA256; somente a classe existente OrderRollbackIntegrationTest mudou até esse ponto. T026–T028 e todos os históricos T029 preservados. Conferidos literalmente os métodos/fixtures/controle positivo/rejeições/sonda SQL anteriores na classe, ressalvada a ampliação explícita do helper para reconhecer a nova exceção de teste. Os 75 fontes finais conferem com os efetivamente executados. XML contém exatamente os dois métodos filtrados e a única falha HTTP prevista. Raiz/branch/HEAD mantidos, índice vazio e nenhuma não rastreada nova além da classe já existente.

Nesta etapa os únicos arquivos de trabalho alterados são `src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`, `specs/004-registrar-pedido/quickstart.md` (somente acréscimo) e `specs/004-registrar-pedido/tasks.md` (somente checkbox T030). Histórico de quickstart de **307538 bytes** preservado integralmente, incluindo T026–T029. T030 marcada [X] após concluir testes e registrar resultados; **T001–T030 [X], T031–T055 abertas (25)**.

Limites: prova local no H2 de falha de processamento após flush real e antes da conclusão do corpo do serviço, não prova de callback beforeCommit, falha física de commit, transporte perdido ou rollback após commit. Não antecipa T031 ou implementações de rejeições/adaptadores/regressões futuras. Resultado HTTP/envelope 500 permanece pendente apesar do rollback GREEN. Sem produção, outros métodos/classes, suíte completa, CI, U1/U2, stage, commit ou push. Próximo ponto de retomada: **T031**, sem execução nesta etapa.

Conferência final documental por `& 'target/harness-validation/feature-004-t030-2026-10-08/review.ps1' -Final`: código 0. Dos 2376 arquivos preexistentes, 2373 mantiveram SHA256; somente quickstart/tasks/classe de rollback mudaram nesta etapa. Prefixo histórico de 307538 bytes intacto, incluindo T026–T029; tasks difere do início somente na checkbox T030. Blocos anteriores de T029 e todos os demais fontes preservados; 75 fontes finais iguais aos efetivamente executados. Dois métodos filtrados, 1 GREEN/1 falha HTTP/zero erros e ignorados, flush real/RuntimeException/rollback conferidos separadamente do envelope. Diff da classe existente não rastreada revisado em source-diff.txt e acréscimo documental em quickstart-diff.txt. `git diff --check` código 0; fonte não rastreado conferido também por `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`, sem diagnósticos, código 1 por diferença de arquivo novo; whitespace/newline aprovados. Avisos LF/CRLF não são falhas de whitespace. Índice vazio, raiz/branch/HEAD mantidos. Status final permanece com cinco caminhos rastreados modificados e a classe de rollback não rastreada; três fontes de T026–T028 modificados anteriormente não foram tocados. T001–T030 [X], T031–T055 abertas. Auditoria reaplicada após este acréscimo sem repetir testes de aplicação.

## Checkpoint T031 — beforeCommit após corpo real produzir o DTO (2026-10-08)

Executada exclusivamente T031, dependência T030 concluída e evidenciada, com parada antes de T032. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial: quickstart.md, tasks.md, OrderControllerTest.java, OrderSecurityIntegrationTest.java e OrderServiceTest.java modificados anteriormente; OrderRollbackIntegrationTest.java não rastreado desde T029. Índice vazio. Preservadas alterações e evidências de T026–T030. Skill speckit-implement aplicada somente ao pedido; T031/US2.9/FR-012/SC-004, plano, pesquisa D6/D8, modelo, constituição, contrato e histórico revisados. Pré-requisitos PowerShell: código 0, FEATURE_DIR correto. Checklist requirements.md: 16/16 concluídos, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente, sem hooks executáveis.

### Instrumentação somente de teste e fronteira do proxy

Acrescentado o método separado `shouldRollbackWhenBeforeCommitFailsAfterServiceProducesDto` à classe existente OrderRollbackIntegrationTest. Contexto/H2 exclusivos, MockMvc síncrono, login/JWT/decoder/filtros reais, OrderService Spring chamado pelo proxy e JPA/persistência reais preservados. Sem @Transactional no teste/classe ou transação externa envolvendo MockMvc. Fixtures de conta/perfis/produtos confirmadas em transação separada pelo suporte existente e verificadas por JDBC independente antes do baseline.

CompletionProbe instala dois MethodInterceptors exclusivos deste método no Advised real do serviço. Confere um único TransactionInterceptor Spring, proxy não congelado e ordem relativa dos interceptores. O observador interno fica **depois do interceptor transacional**, já com transação/sincronização ativas, registra TransactionSynchronization e delega ao corpo real de register. Quando invocation.proceed retorna, captura o OrderResponseDTO efetivamente produzido pelo mapping do serviço, ainda dentro da transação, e registra DTO_PRODUCED. Ele devolve esse DTO ao interceptor transacional; isso **não é o retorno externo do proxy**. O observador externo fica antes do TransactionInterceptor e só registraria PROXY_RETURNED se a conclusão transacional devolvesse normalmente ao chamador. Nesta falha, entrou, mas nunca alcançou esse retorno.

No beforeCommit da sincronização, registra o callback alcançado e o DTO que já existia, observa as linhas por conexão vinculada à mesma transação e lança a RuntimeException exclusiva BeforeCommitTestFailure. StackWalker registra que o callback ocorre durante TransactionAspectSupport.commitTransactionAfterReturning, corroborando a conclusão pelo interceptor real. DataSourceUtils.getTargetConnection remove o wrapper de close do JdbcTemplate antes de isConnectionTransactional; não abre conexão independente dentro do callback. Conexão vinculada, transação ativa e autoCommit=false são exigidos.

Sem alteração do serviço, repositório ou configuração produtiva. Spy/forwarding real de T030 fica preservado e sua falha permanece desarmada; T031 não lança em saveAndFlush. A constraint exclusiva de T029 é exigida ausente e não é instalada. Não há callback afterCommit nem simulação de falha física de conexão/commit. Observadores removidos no AfterEach, depois das verificações e antes da limpeza herdada, inclusive após falha de assertiva. Helper excepcional reconhece também BeforeCommitTestFailure, conservando a exceção real sem inventar resposta HTTP; outras falhas inesperadas seguem como erros de preparação/instrumentação.

### Evidência 1 — corpo concluído e DTO real produzido

Passaram as assertivas de DTO não nulo com id positivo, comprador do login, createdAt igual ao Clock truncado a milissegundos, WAITING_PAYMENT, itens na ordem solicitada e strings monetárias exatas. DTO completo: produtos enviados em posições 0/1, quantities 2/3, unitPrice 10.00/7.50, subtotais 20.00/22.50 e total 42.50. O DTO foi capturado após retorno do corpo real, antes de qualquer beforeCommit, com transação ainda ativa. No callback, callbackDto é a **mesma instância** capturada. Observador externo foi alcançado, mas proxyReturned permaneceu false; nenhum DTO foi devolvido ao controller nesta falha.

Observação real: `T031_BODY produced=true tx=true total=42.50 callbackBeforeBody=false proxyReturned=false`. Produção interna do DTO não é confirmação HTTP nem commit.

### Evidência 2 — beforeCommit alcançado com rows presentes

SaveAndFlush real terminou normalmente e o mapping também terminou. Sonda JDBC preservada observou **três inserts concluídos**, uma linha afetada cada, na ordem tb_order/primeiro tb_order_item/segundo tb_order_item; zero inserts falhos. No beforeCommit, leitura na conexão da mesma transação encontrou **um pedido e dois itens**. Conferidos id/vínculo ao comprador, instante/estado iguais ao DTO, ordem/position, quantidades, preços históricos, subtotais derivados e soma exata 42.50. Não se exigem IDs contíguos ou instantes diferentes, nem se afirmam subtotais/total como colunas persistidas. Prova baseada em retorno real do driver e leitura de rows, não SQL impresso ou save mockado.

Observação real: `T031_BEFORE_COMMIT reached=true hadDto=true interceptorCompletion=true tx=true boundConnection=true autoCommit=false readOnly=false visibleOrders=1 visibleItems=2 completedInserts=3 failedInserts=0`. Essas assertivas passaram. beforeCommit ocorre durante a conclusão transacional pelo interceptor, antes do commit físico; não equivale a afterCommit.

### Evidência 3 — exceção lançada no callback

Passaram as assertivas do callback alcançado, da exceção lançada e de identidade entre o objeto lançado e a causa real que saiu pelo proxy/MockMvc. A ordem dos eventos foi exatamente **SYNC_REGISTERED → DTO_PRODUCED → BEFORE_COMMIT → ROWS_OBSERVED → CALLBACK_EXCEPTION**, sem PROXY_RETURNED. A exceção foi lançada depois da leitura no beforeCommit, nunca pelo saveAndFlush ou pelo corpo do serviço.

Observação real: `T031_FAULT thrown=true sameException=true events=[SYNC_REGISTERED, DTO_PRODUCED, BEFORE_COMMIT, ROWS_OBSERVED, CALLBACK_EXCEPTION]`. Falhas de observação/instrumentação impediriam o lançamento previsto e continuariam erros, não RED comportamental. Não houve esse impedimento nesta execução.

### Evidência 4 — rollback integral antes de qualquer limpeza

Baseline independente completo de tb_order/tb_order_item: **zero pedidos/zero itens**. Bloco finally executou leitura por DriverManagerDataSource/conexão JDBC independente após saída síncrona do proxy/requisição, com transação inativa, antes de remover observadores/desarmar sondas/limpar fixtures. Esse finally também lê diante de escape MVC inesperado. Snapshot completo posterior retornou a **zero/zero**, igual ao baseline. Passou a assertiva de rollback do pedido e ambos os itens realmente inseridos e vistos no callback. **Rollback já GREEN nesta fronteira; nenhuma correção produtiva necessária/demonstrada.**

Observação real: `T031_INDEPENDENT baselineOrders=0 baselineItems=0 afterOrders=0 afterItems=0 baselineRestored=true`. Não se usa cleanup como prova, rollback automático de teste, ausência de save em mocks ou rejeição antes do SQL para demonstrar atomicidade.

### Evidência 5 — resultado HTTP/envelope permanece RED

Exigidos HTTP **500**, sem 201/Location, application/json e envelope exatamente `{"status":500,"error":"Internal Server Error","message":"Order registration could not be completed","path":"/orders"}`. Mensagem fixa/campos exatos excluem corpo recebido, token/hash/segredo, SQL, stack trace e diagnósticos internos; assertiva adicional de ausência do token mantida.

Resultado real: BeforeCommitTestFailure escapou como causa de ServletException pelo MockMvc **sem resposta HTTP**. Observação `T031_HTTP status=NONE escaped=BeforeCommitTestFailure`. O cenário completo ficou RED apenas por `[failure must reach MVC as an HTTP error response] Expecting actual not to be null`; assertAll registrou MultipleFailuresError com **uma** subfalha. Nenhuma confirmação 201 foi recebida, mas não se fabricou HTTP 500 nem se aprovou envelope seguro ausente. As quatro dimensões anteriores passaram. **Falha apenas no HTTP/envelope não significa falha de rollback.** Adaptador produtivo permanece para as tarefas próprias; produção não foi corrigida.

### Controle positivo e comando efetivamente executado

Preservado e executado `shouldCommitCompleteOrderAndItemsVisibleAfterHttp201`: login ADMIN isolado real 200, POST /orders **201**, confirmação exata com total 42.50, titular/id/UTC em milissegundos/WAITING_PAYMENT, ordem dos itens/strings monetárias e ausência de Location. JDBC independente após retorno confirmou **+1 pedido/+2 itens**, baseline anterior preservado, vínculos, posições, quantidades, preços históricos, subtotais e soma corretos; três inserts reais concluídos. Observação `T029_CONTROL status=201 ordersDelta=1 itemsDelta=2 completedInserts=3`. Controle GREEN, sem alterar suas assertivas. Sonda beforeCommit não é instalada nesse método.

Executado **uma única vez**, somente os dois métodos abaixo; não executados outros cenários T029/T030, classe inteira ou outras classes. Windows/PowerShell, 2026-10-08, offset -03:00; Java 21.0.12.1 no XML, Maven 3.9.16 pelo Wrapper. Não repetido --version. Compilar outros fontes não significa executar seus testes.

```powershell
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest#shouldRollbackWhenBeforeCommitFailsAfterServiceProducesDto+shouldCommitCompleteOrderAndItemsVisibleAfterHttp201" test
```

| Início → fim -03:00 | Testes | GREEN | Falhas | Erros | Ignorados | Resultado Maven |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 16:20:27.556 → 16:21:17.490 | 2 | 1 | 1 | 0 | 0 | exit 1, BUILD FAILURE, 44.850 s |

Surefire **21.667 s**. Controle positivo **2.454 s**, novo cenário **0.625 s**. São dois testes únicos, não cinco testes pelas dimensões/subassertivas. Compilação, contexto, fixtures, instalação/remoção de observadores e observação transacional passaram; **zero erros de preparação**. A única falha comportamental é resposta/envelope ausentes. Não se provocou RED artificial nem se enfraqueceram assertivas GREEN.

### Evidências, preservação e limites

Capturas ignoradas em `target/harness-validation/feature-004-t031-2026-10-08/`: baseline.json/preserved-before.json com **2778 arquivos preexistentes**, originals/ de quickstart/tasks/classe existente não rastreada, surefire-before/, run-focused.ps1, result.json, order-before-commit-console.txt, reports/ XML/TXT, executed-sources/ e manifesto dos **75 fontes Java**, case-results.json, observations.txt e review.ps1. Histórico de relatórios anteriores copiado antes da execução; capturas T026–T030 preservadas. Runner usa cmd.exe /d /c para stdout/stderr e captura LASTEXITCODE real do Maven; retorno administrativo 0 não converte Maven exit 1 em GREEN.

Auditoria pré-documentação por `& 'target/harness-validation/feature-004-t031-2026-10-08/review.ps1'`: código 0, **2777/2778 arquivos** mantiveram SHA256; somente a classe de rollback mudou até esse ponto. Métodos T029/T030, controle positivo, rejeições, fixtures, sonda SQL, configuração do spy e sonda pós-flush conferidos literalmente contra o original; helper de saída apenas ampliado para a nova causa de teste. Os 75 fontes finais são iguais aos executados. XML confere os dois métodos filtrados, contagens e uma única subfalha HTTP. Raiz/branch/HEAD mantidos e índice vazio; única não rastreada permanece a classe criada em T029.

Arquivos de trabalho desta etapa: `src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`, `specs/004-registrar-pedido/quickstart.md` (acréscimo) e `specs/004-registrar-pedido/tasks.md` (somente checkbox T031). Histórico de quickstart com **322938 bytes** preservado integralmente, incluindo T026–T030. T031 marcada [X] após concluir a escrita/execução dos testes e registrar evidências reais; **T001–T031 [X], T032–T055 abertas (24)**. Checkbox indica conclusão desta tarefa de teste, não implementação do envelope 500 pendente.

Limites: prova local H2 de exceção em beforeCommit durante conclusão pelo interceptor, após corpo/mapping real produzirem DTO e antes do commit físico. Não é falha física de conexão no commit, callback afterCommit, rollback após commit ou perda real de transporte. Não comprova rejeições, segurança completa ou regressões ainda futuras; não repetiu outros alvos. Sem mudanças de produção, constraints temporárias, callbacks adicionais, T032+, suíte completa, CI, U1/U2, stage, commit ou push. Próximo ponto de retomada: **T032**, sem execução nesta etapa.

Conferência final documental por `& 'target/harness-validation/feature-004-t031-2026-10-08/review.ps1' -Final`: código 0. Dos 2778 arquivos preexistentes, 2775 mantiveram SHA256; somente quickstart/tasks/classe de rollback mudaram nesta etapa. Prefixo histórico de 322938 bytes intacto, incluindo T026–T030; tasks difere do início exclusivamente pela checkbox T031. Métodos/sondas/configuração anteriores preservados literalmente, 75 fontes finais iguais aos efetivamente executados. XML/console conferidos: 2 testes, 1 GREEN/1 falha apenas HTTP, zero erros e ignorados, corpo/DTO-beforeCommit/exceção/rollback integral comprovados separadamente da ausência do envelope. Conteúdo e diff da classe existente não rastreada revisados em source-diff.txt e acréscimo documental em quickstart-diff.txt. `git diff --check` código 0; `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java` sem diagnósticos, código 1 pela diferença do arquivo novo; whitespace/newline aprovados. Avisos LF/CRLF não são falhas de whitespace. Índice vazio, raiz/branch/HEAD mantidos, cinco rastreados modificados anteriores e somente a classe de rollback não rastreada. T001–T031 [X], T032–T055 abertas; .specify/extensions.yml continua ausente. Auditoria reaplicada após este acréscimo, sem repetir testes de aplicação.

## Checkpoint T032 — execução serial consolidada de T026–T031 (2026-10-08)

Executada exclusivamente T032, com parada antes de T033. Raiz real `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Status inicial/final mantém quickstart.md, tasks.md, OrderControllerTest.java, OrderSecurityIntegrationTest.java e OrderServiceTest.java modificados anteriormente, mais OrderRollbackIntegrationTest.java não rastreado desde T029. Índice vazio. T026–T031, fontes, assertivas e evidências preservados. Skill speckit-implement aplicada somente ao pedido; T032/dependências, US2, plano, pesquisa D6/D8, modelo, constituição, contrato e histórico conferidos. Pré-requisitos PowerShell: código 0, FEATURE_DIR correto. Checklist requirements.md 16/16 concluídos, zero abertos, PASS, somente leitura. Ignore Java existente suficiente; `.specify/extensions.yml` ausente, sem hooks executáveis.

### Comando e escopo efetivamente executados

Uma única execução pelo Maven Wrapper, com filtro **somente de classes**, sem filtro de métodos: todos os casos preparados e controles nas quatro classes autorizadas, incluindo as duas classes Nested de OrderControllerTest. Paralelismo JUnit explicitamente desligado, fork único reutilizado e ordem Surefire alfabética. Não executados outros alvos, suíte completa ou CI. Não repetido --version. Compilar/carregar dependências não significa executar outras classes de teste.

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest,OrderSecurityIntegrationTest,OrderServiceTest,OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" "-Dsurefire.runOrder=alphabetical" test
```

Execução Windows/PowerShell em **2026-10-08, 16:32:54.580 → 16:34:01.885 -03:00**. Java **21.0.12.1** nos seis XMLs; Maven **3.9.16** pelo Wrapper existente. Resultado real: **Maven exit 1, BUILD FAILURE**, Total time impresso **01:02 min**. O retorno administrativo 0 do runner não converte esse resultado em GREEN.

Ordem observada no mesmo processo: **OrderControllerTest** (InputBoundary → SuccessfulRegistration), **OrderRollbackIntegrationTest**, **OrderSecurityIntegrationTest**, **OrderServiceTest**. Propriedade junit.jupiter.execution.parallel.enabled=false confirmada em todos os XMLs. Não se usaram execuções Maven independentes como prova de isolamento conjunto.

| Classe autorizada | Testes | GREEN existente | RED / falhas | Erros | Ignorados | Tempo Surefire s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OrderControllerTest, incluindo Nested | 111 | 5 | 106 | 0 | 0 | 24.761, container |
| OrderSecurityIntegrationTest | 83 | 23 | 60 | 0 | 0 | 21.882 |
| OrderServiceTest | 42 | 31 | 11 | 0 | 0 | 0.985 |
| OrderRollbackIntegrationTest | 9 | 2 | 7 | 0 | 0 | 5.646 |
| **Consolidado** | **245** | **61** | **184** | **0** | **0** | não somar containers/nested |

Controller: InputBoundary **108 testes, 2 GREEN/106 falhas**, 12.641 s; SuccessfulRegistration **3 GREEN**, 12.010 s. XML do container externo registra zero testes próprios e tempo 24.761 s; não somado como cobertura adicional. Foram seis relatórios de suítes, quatro classes de escopo e **245 casos únicos**. Nenhum teste ignorado/desabilitado; nenhuma falha de compilação, contexto, fixture ou desmontagem. Os 184 REDs são falhas de assertivas pela implementação ainda incompleta, não erros de preparação.

### GREEN existente e causas reais dos REDs

**OrderControllerTest:** três confirmações HTTP reais continuam GREEN (shape exato, teto decimal19,2 como string, duas casas). Os dois controles de leitura do slice preservam Long.MAX_VALUE/Integer.MAX_VALUE sem coerção. Slice usa serviço mockado apenas para leitura/delegação; não prova segurança, persistência ou rollback. Os 106 REDs distribuem-se em 24 corpos/containers, 28 tokens/campos inteiros, 40 extras/dados do sistema, 13 repetições de chaves JSON bruto e um quantity zero entre válidos. Há coerções/extras aceitos com 201 e chamada indevida do serviço, ausências/nulos sem validação ou erros de leitura sem o envelope contratual. Assertivas continuam exigindo 400 exato e nenhuma chamada; produção/parser/DTO/advice não corrigidos. Não se transformaram esses casos em GREEN por aceitar o comportamento atual.

**OrderSecurityIntegrationTest:** 23 GREENs = nove variações de perfis/claims com confirmação real, um controle real com servlet context /api e treze guardas diretas pelo proxy Spring (cinco identidades inválidas e oito alterações de conta/perfis). JWTs, login, filtros, decoder e banco reais. Os 60 REDs = 44 casos JWT/corpo/contexto, dois sem perfis mesmo com claim ADMIN, dois lookup interno e doze guardas tardias via HTTP. Faltam verificações estritas e/ou respostas/envelopes de 401/403/500; falha interna e guardas tardias ainda podem escapar do MVC sem resposta. Guardas diretas GREEN não aprovam adaptação HTTP pendente.

Nesta execução, **sete casos JWT com corpo válido indevidamente receberam 201 e persistiram um pedido/um item cada**, comprovados por leitura independente antes da limpeza: expiração recente, exp ausente, iat ausente, sub com zero inicial, sub com sinal +, iat futuro e nbf futuro. Os sete correspondentes com corpo inválido também divergem da precedência exigida; são quatorze divergências comportamentais de regras estritas, além das falhas de envelope dos demais casos. O token é assinado de verdade para isolar a regra. Referência temporal renovada imediatamente antes do dispatch; expiração/claims futuros ±5 s ficam intencionalmente dentro da tolerância padrão do decoder, e a assertiva de intervalo da fixture passou. Não se atribui esse RED a expiração incidental durante a execução conjunta. As linhas indevidas são lacuna produtiva já registrada em T027, não contaminação de fixture entre casos, e não foram corrigidas nesta tarefa.

**OrderServiceTest:** 31 GREENs mantêm cálculos exatos, ordem das linhas, preços históricos, nova leitura/nova identidade em chamadas sucessivas, agregado novo para entrada igual, centavo mínimo/teto decimal19,2/quantity Integer.MAX_VALUE em cálculo representável e guardas sem gravação. Onze REDs: duplicata aceita com save mockado (um), preços null/zero/negativo (três), precisão/preço/subtotal (seis) e soma excedente (um). Dez regras numéricas já lançam IllegalArgumentException/ArithmeticException sem save, mas não a exceção/mensagem de domínio correspondente a 422; duplicata ainda não é rejeitada antes de save. Não implementada InvalidOrderException, não alterados Product/validações/schema. Mocks não são prova de persistência, rollback ou proxy real.

**OrderRollbackIntegrationTest:** nove casos executados juntos: controle positivo e inexistente GREEN; três provas de rollback, duplicata, preço zero, preço negativo e quantity zero RED. As três provas de rollback falham **somente** na resposta HTTP; suas assertivas de execução SQL/ponto da exceção/baseline estão GREEN. Duplicata também falha pela exigência de rejeição antes de qualquer insert, enquanto seu rollback SQL funciona. Zero/negativo/quantity zero não executam insert e mantêm baseline, mas não entregam 422/422/400 contratuais. Nenhuma dessas ausências de insert é apresentada como rollback depois de SQL.

### Três provas de rollback — dimensões observadas separadamente

| Prova | Ponto e SQL efetivamente alcançados | Leitura na transação | Leitura independente após saída do proxy, antes de limpeza | HTTP/envelope |
| --- | --- | --- | --- | --- |
| T029, constraint SQL tardia | INSERT pedido e primeiro item concluídos, affected=1 cada; segundo item falha real SQLState 23513, CHECK exclusivo identificado | mesma conexão física: um pedido/um item; tx ativa, autoCommit=false | baseline 0/0 → após 0/0, snapshot completo igual; constraint ainda instalada durante verificações | sem resposta, DataIntegrityViolationException escapa; RED apenas 500/envelope |
| T030, processamento pós-flush | saveAndFlush delegado ao proxy/implementação real terminou; três inserts concluídos; RuntimeException da sonda ainda no corpo do serviço | conexão vinculada: um pedido/dois itens; tx ativa, autoCommit=false; mesma exceção confirmada | baseline 0/0 → após 0/0, snapshot completo igual em finally antes de teardown | sem resposta, PostFlushTestFailure escapa; RED apenas 500/envelope |
| T031, conclusão beforeCommit | saveAndFlush real e mapping concluíram; três inserts concluídos; callback visto via interceptor e RuntimeException nele | DTO real produzido antes do callback, proxyReturned=false; beforeCommit vê mesmo DTO e um pedido/dois itens na conexão vinculada | baseline 0/0 → após 0/0, snapshot completo igual em finally antes de teardown | sem resposta, BeforeCommitTestFailure escapa; RED apenas 500/envelope |

T029 nesta execução: productIds **72/73** observados, sem expectativa fixa/contígua; primeiro item visível e segunda falha no CHECK real. Sonda registra retorno efetivo do driver, não só SQL impresso. Constraint instalada fora da transação do pedido, antes do POST, e removida somente depois de todas as verificações.

T030: `T030_FLUSH returned=true orderId=2 tx=true boundConnection=true autoCommit=false visibleOrders=1 visibleItems=2 completedInserts=3 failedInserts=0`; `T030_FAULT thrown=true sameException=true http=NONE escaped=PostFlushTestFailure`; baselineRestored=true na leitura independente. Id 2 é observação, não assertiva fixa. Corpo do serviço não conclui o mapping nesta prova: a exceção ocorre enquanto ainda aguarda retorno da chamada ao spy.

T031: `T031_BODY produced=true tx=true total=42.50 callbackBeforeBody=false proxyReturned=false`; beforeCommit reached/hadDto/interceptorCompletion=true, tx/boundConnection=true, autoCommit/readOnly=false, visibleOrders=1/visibleItems=2, três inserts concluídos/zero falhos. Ordem exata: **SYNC_REGISTERED → DTO_PRODUCED → BEFORE_COMMIT → ROWS_OBSERVED → CALLBACK_EXCEPTION**. Objeto lançado corresponde à causa que saiu do proxy. Produção interna de DTO não é retorno externo/201. Sem constraint T029 ou falha do spy T030 armada.

Conferidos nas views/snapshots: comprador do login, id/vínculos, instante UTC em milissegundos, WAITING_PAYMENT, ordem/posição dos itens, quantities 2/3, unitPrice históricos 10.00/7.50, subtotais derivados 20.00/22.50 e soma 42.50 (na falha SQL, somente primeiro item efetivamente concluído). Sem IDs contíguos ou instantes diferentes exigidos. O teste positivo preservado devolveu 201 e JDBC independente confirmou **+1 pedido/+2 itens**, total 42.50, baseline anterior preservado, sem Location.

Todas as três provas continuam exigindo HTTP 500 e envelope exatamente `{"status":500,"error":"Internal Server Error","message":"Order registration could not be completed","path":"/orders"}`, sem 201/Location/token/dados internos. Cada relatório registra MultipleFailuresError com **uma** subfalha: resposta HTTP nula. Não se aprovou envelope seguro ausente nem se fabricou status. **Rollback integral GREEN em todas as três; falha apenas HTTP não é falha de rollback.** Não há necessidade demonstrada de correção produtiva de atomicidade desta fronteira.

Rejeições no mesmo contexto: inexistente 404/envelope exato, zero inserts e baseline íntegro; duplicata três inserts concluídos e falha de unicidade 23505 no item repetido, rollback ao baseline, mas falta rejeição de negócio antecipada/422; zero/negativo/quantity zero zero inserts, baseline íntegro e IllegalArgumentException sem resposta. Preço negativo preparado por JDBC isolado e confirmado; preço null permanece somente na unidade. Product e schema produtivo preservados.

### Isolamento conjunto e comparação histórica

Processo **6084**, thread main nos quatro startups Spring; três bancos H2 reais distintos, conferidos no console: Controller SuccessfulRegistration `order-test-693ad40a-6bb7-47d9-8ed7-ed88986eda5b`, Rollback `order-test-491da02b-6a51-4d47-af3c-68da8c290867`, Security `order-test-075b1c38-71fb-43c9-9fba-79c506be7d8a`. Slice não recebe repositórios reais e unidade usa mocks próprios. Configurações/spies/sondas importadas somente nos contextos que as exigem; não há mudança global de Jackson, segurança ou transação.

Na classe de rollback, a ordem observada foi controle positivo → T030 → cinco rejeições → T029 SQL tardio → T031. Assim, o teste beforeCommit passou após a remoção do CHECK T029 e o desarme da falha T030, ambos exigidos nas assertivas. Sonda SQL limpa antes/depois de cada caso, baseline zero/zero em cada rejeição/prova; observadores T031 removidos em AfterEach com assertivas de sucesso e transação inativa, antes da limpeza de fixtures. Em seguida, Security e unidade mantiveram todos os resultados anteriores. Probes de segurança removidos/desarmados por caso, guardas/preparação sem erros, fixtures limpas explicitamente e contas/produtos próprios. Não se usou cleanup como prova de rollback nem como desculpa para as sete gravações indevidas vistas antes dele.

`& 'target/harness-validation/feature-004-t032-2026-10-08/compare-history.ps1'`: código 0, **245/245 casos** mantêm resultado GREEN/FAILURE e tipo da falha em comparação aos relatórios finais preservados de T026–T031; zero divergências. A comparação usa identidade de classe/método/índice da invocação, sem duplicar o controle positivo nos relatórios T029/T030/T031. Causas atuais revisadas nos XMLs/por grupos, não deduzidas apenas do histórico. Portanto não apareceu interferência observável nesta sequência; não se promete isolamento em toda permutação/paralelismo futuro. **Nenhum ajuste de preparação foi necessário e nenhum fonte/assertiva foi editado nesta etapa.**

### Capturas, preservação e limite do checkpoint

Evidências ignoradas em `target/harness-validation/feature-004-t032-2026-10-08/`: baseline.json/preserved-before.json com **2937 arquivos preexistentes**, originals/ dos documentos/quatro testes, surefire-before/ com relatórios anteriores copiados antes da execução, run-focused.ps1, result.json, us2-phase-console.txt, reports/ com seis XMLs/TXTs atuais, executed-sources/ e manifesto dos **75 fontes Java**, summarize.ps1, case-results.json, suite-results.json, class-totals.json, method-totals.json, observations.txt, context-isolation.txt, compare-history.ps1/historical-comparison.json e review.ps1. Capturas T026–T031 preservadas. Runner usa cmd.exe /d /c para stdout/stderr e LASTEXITCODE real; não confundir código administrativo 0 com Maven GREEN.

Auditoria antes da documentação por `& 'target/harness-validation/feature-004-t032-2026-10-08/review.ps1'`: código 0, **2937/2937 arquivos** preexistentes mantiveram SHA256, zero fontes alterados. Todos os 75 fontes executados conferem com o início e o estado final; relatórios atuais conferidos como posteriores ao início da execução, seis suites/paralelismo false/scope exato/245 casos únicos/61 GREEN/184 falhas/zero erros e ignorados. Preservados T026–T031, incluindo a classe não rastreada e históricos. Três rollbacks conferidos separadamente do HTTP, sete gaps de segurança com leitura independente e estados históricos iguais verificados. Índice vazio, raiz/branch/HEAD mantidos.

Alterados nesta etapa somente `specs/004-registrar-pedido/quickstart.md` (acréscimo) e `specs/004-registrar-pedido/tasks.md` (checkbox T032). Prefixo histórico de quickstart com **337417 bytes** preservado integralmente. T032 marcada [X] somente após executar/revisar e registrar resultados reais; **T001–T032 [X], T033–T055 abertas (23)**. A checkbox fecha esta tarefa de validação RED/GREEN, não aprova rejeições ou envelopes que continuam ausentes.

Limites: execução local, serial, um único fork/ordem observada e H2 de teste, sem CI. Slice/mocks não comprovam segurança/persistência/rollback; provas transacionais usam proxy/banco reais e leitura independente antes da limpeza. As provas de falha não representam conexão física falhando no commit, afterCommit, rollback após commit ou falha real de transporte. Positivos preservados não aprovam rejeições/rollback/regressões futuras por inferência; apenas dimensões efetivamente observadas foram registradas. Nenhuma produção, preparação de testes, parser/DTO/advice/segurança/entidades/schema alterados; sem T033+, outros alvos, suíte completa, U1/U2, stage, commit ou push. Próximo ponto de retomada: **T033**, sem execução nesta etapa.

Conferência final documental por `& 'target/harness-validation/feature-004-t032-2026-10-08/review.ps1' -Final`: código 0. Dos 2937 arquivos preexistentes, 2935 mantiveram SHA256; somente quickstart/tasks mudaram nesta etapa. Prefixo histórico de 337417 bytes intacto, incluindo T026–T031; tasks difere do início exclusivamente pela checkbox T032. Todos os 75 fontes iguais ao início e aos executados; classe de rollback não rastreada preservada sem edição. Seis relatórios atuais conferidos, 245 casos únicos/61 GREEN/184 falhas/zero erros e ignorados; 245 resultados/tipos iguais ao histórico. Scope de classes, paralelismo false, processo 6084/três bancos distintos, três pontos de falha/SQL efetivo/baselines anteriores à limpeza e HTTP ausente separados, além das sete gravações indevidas de JWT, conferidos. Acréscimo documental/diff revisado em quickstart-diff.txt; diff de tasks somente T032. `git diff --check` código 0; fonte não rastreado também conferido por `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`, sem diagnósticos, código 1 pela diferença do arquivo novo; whitespace/newline aprovados. Avisos LF/CRLF não são falhas de whitespace. Índice vazio e base Git mantida. Status final conserva cinco rastreados modificados e a única não rastreada anterior; T001–T032 [X], T033–T055 abertas. .specify/extensions.yml permanece ausente. Auditoria reaplicada após este acréscimo sem repetir testes de aplicação.

## T033 — leitura estrita e Bean Validation de pedidos — 2026-10-08

Execução exclusivamente de T033, após T032; parada antes de T034. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD inicial/final `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Conferidos por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`. Estado inicial: quickstart/tasks e OrderControllerTest/OrderSecurityIntegrationTest/OrderServiceTest modificados, OrderRollbackIntegrationTest não rastreado. Essas alterações de T026–T032 foram preservadas; não houve stage/commit/push/troca de branch.

Skill `speckit-implement` aplicada apenas ao escopo autorizado. Pré-requisitos identificam Feature 004; requirements.md com 16/16 itens concluídos, zero abertos. Consultados constituição, plano, T033/dependências, D3/D4 de research, modelo, contrato e testes existentes. `.specify/extensions.yml` ausente, sem hooks a executar. `.gitignore` existente cobre target/ e artefatos Java; preservado. A execução RED histórica de T032 permanece registrada acima, sem repetição de outros alvos nesta tarefa.

### Implementação restrita à entrada

Criado `src/main/java/com/devsuperior/dscommerce/config/OrderRequestJsonConfig.java`, com `@JsonComponent` e dois StdDeserializer tipados, somente para OrderRequestDTO e OrderItemRequestDTO. Leitura streaming: root e linha exigem objetos; somente items é permitido no root, e somente productId/quantity na linha. Booleano de presença no root e conjunto de nomes por linha rejeitam chaves repetidas durante a leitura dos tokens, antes de criar DTO ou árvore JSON. Extras são recusados mesmo quando null, inclusive titular/preços/total/id/instante/estado; nenhum readTree é usado para processar esses inputs.

Items exige array ou null encaminhado à validação; cada linha não nula usa o leitor de OrderItemRequestDTO. Campos numéricos aceitam apenas VALUE_NUMBER_INT ou null encaminhado à validação. Strings/booleanos/arrays/objetos e VALUE_NUMBER_FLOAT (incluindo 1.0/1.5/1e0) não são convertidos. getLongValue/getIntValue verificam os limites sem truncamento, arredondamento, float/double ou coerção textual. Valores inteiros representáveis, inclusive os dois máximos, não recebem limite comercial adicional. Ausências/nulos/zero/negativos ficam sujeitos à Bean Validation antes do serviço.

OrderRequestDTO agora tem `@NotEmpty @Valid List<@NotNull OrderItemRequestDTO>`; OrderItemRequestDTO tem `@NotNull @Positive` nos dois campos e `@Max(2147483647)` em quantity. Mantidas imutabilidade e cópia defensiva da lista de entrada. Os construtores continuam permitindo representar dados incompletos para validação na fronteira; o teste de construção direta preexistente continua GREEN. OrderController mantém seu `@Valid @RequestBody` antes da delegação, sem edição.

JacksonConfig, configurações/coerções globais, leitor de login, APIs de catálogo/detalhes, DTOs/serialização de saída, OrderService, JWT, segurança, GlobalExceptionHandler, entidades/schema, testes e fixtures permanecem byte a byte iguais ao início. Não foi implementado o envelope de T038 nem qualquer tarefa T034+. A preservação de código dessas outras rotas não equivale a executar suas regressões; estas não foram executadas nesta etapa.

### Comando e resultados efetivamente executados

Uma única execução local pelo Maven Wrapper, sem filtro de métodos, serial, um fork reutilizado e ordem alfabética de classes:

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest,OrderDtoContractTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" "-Dsurefire.runOrder=alphabetical" test
```

Início **2026-10-08T16:53:53.5924002-03:00**, fim **16:54:51.9878467-03:00**; Total time Maven **52.941 s**, Java **21.0.12.1**, Maven **3.9.16**. Compilação de produção/testes e inicialização dos contextos concluídas. Maven **código 1, BUILD FAILURE**, pelas 106 falhas contratuais de envelope abaixo. O runner PowerShell terminou com código administrativo 0; isso não transforma o resultado Maven em GREEN.

| Classe efetivamente executada | Casos | GREEN integral | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| OrderControllerTest (duas classes Nested) | 111 | 5 | 106 | 0 | 0 |
| OrderDtoContractTest | 36 | 36 | 0 | 0 | 0 |
| **Total de casos únicos** | **147** | **41** | **106** | **0** | **0** |

Surefire registra InputBoundary: 108 casos/2 GREEN/106 falhas, 13.289 s; SuccessfulRegistration: 3/3 GREEN, 11.942 s; OrderDtoContractTest: 36/36 GREEN, 0.858 s. O relatório externo de OrderControllerTest tem zero casos próprios e 25.372 s de duração agregada: não foi contado novamente. Sequência real: InputBoundary → SuccessfulRegistration → OrderDtoContractTest, paralelismo false, processo 3212. Nenhuma outra classe foi executada; nenhum erro de compilação/contexto/fixture foi classificado como RED comportamental. Não foram modificados testes, assertivas ou preparação.

### Provas separadas da fronteira e envelope ainda pendente

**1. Entradas inválidas: 106/106 receberam HTTP 400.** As linhas `T026_BOUNDARY case=... status=400 serviceCalls=0`, já emitidas pelo teste, foram extraídas do console com regex ancorada no status final, inclusive quando a descrição contém um campo chamado status. São 106 descrições únicas, todas correspondentes aos casos executados: corpos/containers/linhas/campos ausentes (24), tokens numéricos inválidos/nulos/zero/negativos/overflow (28), extras/sistema inclusive null (40), chaves repetidas em JSON bruto (13) e quantity zero entre válidos (1). Não se deduziu o status a partir da exceção do parser.

**2. Nenhum desses 106 inputs chamou OrderService.** As 106 observações têm serviceCalls=0. O assertAll existente executa status, envelope e verifyNoInteractions separadamente; nos XMLs cada caso registra exatamente **uma** subfalha, sempre em `lambda$assertInvalidInput$1` (envelope), sem subfalha em `$0` (status) ou `$2` (serviço). Conferida a correspondência individual entre descrição/falha/observação. O serviço observado é mock exclusivo do slice MVC, sem filtros/repositórios reais; esta prova não é evidência de autenticação real, persistência ou rollback desses inputs.

**3. Controles positivos e DTOs: 41 GREEN integrais.** Dois controles do slice aceitaram 1/1 e Long.MAX_VALUE/Integer.MAX_VALUE, devolveram 201 e o ArgumentCaptor confirmou os dois inteiros exatos, sem interações adicionais. Três positivos HTTP de contexto completo, preservados, passaram: contrato exato/order/42.50, teto decimal19,2 como texto sem expoente e 0.10 × 3 = 0.30 com duas casas; seus filtros/JWT/login/serviço/persistência reais continuam usados. Não se ampliou o alcance das evidências além das assertivas desses testes. Os 36 testes JsonTest com mapper Boot mantêm leitura/serialização dos campos exatos/tipos/limites, instante UTC em milissegundos, strings monetárias de 0.01 ao teto, rejeição de 19 formatos monetários inválidos sem arredondamento e proteção/cópia das coleções. DTOs de saída não foram alterados.

**4. RED restante apenas pelo envelope de T038: 106 casos.** Em **92**, o JSON Content-Type da resposta está ausente (assertiva de Content-Type recebeu null); o teste não chega às assertivas seguintes de corpo/igualdade nessa lambda. Não se declara um envelope aprovado nem se infere conteúdo não inspecionado. Em **14**, Bean Validation fornece status/error/path corretos, mas `message: "Validation failed"` diverge de `"Invalid order data"`. São sete containers/linhas/campos ausentes, seis productId/quantity null/zero/negativo e o caso quantity zero entre válidos. Todos têm status 400 e zero chamadas ao serviço, comprovados separadamente. As assertivas exigem sem flexibilização o envelope exato `{"status":400,"error":"Bad Request","message":"Invalid order data","path":"/orders"}`. Não foi alterado GlobalExceptionHandler para obter GREEN completo; o número de casos integralmente RED permanece 106 mesmo com as dimensões de leitura/validação/delegação agora GREEN.

Análise read-only após a execução por `& 'target/harness-validation/feature-004-t033-2026-10-08/summarize.ps1'` e `& 'target/harness-validation/feature-004-t033-2026-10-08/verify-boundary.ps1'`: ambos código 0; conferidos os 147 casos únicos, 106 status 400/zero chamadas, 92+14 falhas exclusivamente da lambda de envelope, cinco controles positivos e 36 DTOs GREEN. Não executam testes de aplicação.

### Preservação, revisão e limites

Capturas ignoradas em `target/harness-validation/feature-004-t033-2026-10-08/`: baseline.json/preserved-before.json de **3115 arquivos preexistentes**, originals/ dos documentos/DTOs/testes, surefire-before/ antes da execução, run-focused.ps1/result.json/strict-input-console.txt, reports/ com quatro XMLs/TXTs atuais, executed-sources/ e manifesto dos **76 fontes Java**, summarize.ps1/case-results.json/suite-results.json/class-totals.json/method-totals.json, verify-boundary.ps1/boundary-observations.json/boundary-verification.json e review.ps1. Os relatórios atuais são posteriores ao início do comando; todos os fontes executados correspondem ao estado após a implementação, sem edição posterior que exigisse repetir a prova. Capturas e evidências T026–T032 preservadas.

Antes da documentação, `& 'target/harness-validation/feature-004-t033-2026-10-08/review.ps1'` concluiu com código 0: **3113/3115 arquivos** preexistentes intactos, só os dois DTOs de entrada alterados; o leitor é novo. A primeira conferência do helper havia forçado core.autocrlf=false também no diff de rastreados, apontando CRLF em duas linhas históricas de quickstart (392/394), cujo SHA256 inicial estava preservado. Corrigido somente o helper ignorado para usar `git diff --check` com a configuração real do repositório (`core.autocrlf=true`), que passou; nenhum byte histórico foi reescrito. Arquivos não rastreados continuam verificados individualmente com no-index/core.autocrlf=false, sem diagnósticos. A repetição foi de auditoria documental, sem repetir testes.

T033 marcada [X] somente após execução, análise e registro do escopo comprovado. **T001–T033 [X], T034–T055 abertas (22)**; a checkbox não aprova o envelope pendente. Alterações desta etapa: OrderRequestJsonConfig novo, dois DTOs de entrada, acréscimo em quickstart e somente checkbox T033 em tasks. Testes de T026–T032 e suas evidências permanecem intactos. Próximo ponto de retomada: **T034**, sem execução nesta etapa.

Limites: prova de inputs inválidos é slice MVC com serviço observado; as 106 falhas de envelope continuam reais. Não se aprovam regras de serviço, JWT estrito, envelopes, rejeições/rollback/regressões posteriores com base nesta execução. Transporte real não foi simulado nem demonstrado; nenhum GET, deduplicação/idempotência ou endpoint adicional. Sem outras classes, suíte completa, CI, T034+, U1/U2, stage, commit ou push.

Conferência final por `& 'target/harness-validation/feature-004-t033-2026-10-08/review.ps1' -Final`: código 0. Dos 3115 arquivos preexistentes, 3111 preservam SHA256; somente os dois DTOs de entrada e quickstart/tasks mudaram nesta etapa. Leitor novo revisado integralmente; testes/código fora do escopo e capturas anteriores intactos. Prefixo histórico de quickstart com 355733 bytes preservado; tasks difere do início somente pela checkbox T033. Os 76 fontes executados correspondem ao estado final; quatro relatórios atuais/147 casos únicos/41 GREEN/106 falhas exclusivamente de envelope/zero erros e ignorados conferidos, com 106 status 400 e zero chamadas ao serviço. Diff dos DTOs/acréscimo documental e conteúdo dos não rastreados revisados; `git diff --check` código 0 e no-index --check de ambos os não rastreados sem diagnósticos (código 1 pela diferença contra /dev/null). Índice vazio; raiz/branch/HEAD mantidos. Status final: sete rastreados modificados e duas não rastreadas, incluindo as alterações anteriores preservadas. T001–T033 [X], T034–T055 abertas; hooks ausentes. Auditoria documental reaplicada após este acréscimo sem repetir testes de aplicação.

## T034 — rejeições de negócio e valores exatos no serviço — 2026-10-08

Executada exclusivamente T034, após T033, com parada antes de T035. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD inicial/final `0344dc0199fd8135f98e852e7d6fedc06b124d9a` (`feat: implement successful order registration`). Conferidos `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`. Estado inicial: sete rastreados modificados (quickstart/tasks, dois DTOs de entrada e três testes) e duas não rastreadas (OrderRequestJsonConfig e OrderRollbackIntegrationTest), todos preservados fora dos acréscimos autorizados nos documentos.

Skill `speckit-implement` aplicada ao escopo solicitado. Pré-requisitos confirmam Feature 004; checklist requirements.md: 16/16 concluídos, zero abertos, PASS. Consultados T034/dependências T033/T028, spec FR-004/005/008/012/014, plano/constituição já revisados, research D3/D4/D6, modelo, contrato e OrderServiceTest integralmente. `.specify/extensions.yml` ausente, sem hooks; ignore existente cobre target/ e artefatos Java, sem alteração.

### Implementação e fronteira preservada

Criada `src/main/java/com/devsuperior/dscommerce/services/exceptions/InvalidOrderException.java`, RuntimeException com enum Reason e getter do motivo, limitado às três mensagens fixas do contrato:

| Motivo | Mensagem |
| --- | --- |
| DUPLICATE_PRODUCT | Duplicate product in order |
| INVALID_PRODUCT_PRICE | Product price is not valid for order |
| UNREPRESENTABLE_VALUE | Order value cannot be represented |

Sem @ResponseStatus, handler ou envelope novo; HTTP 422 correspondente continua para T038. Mensagens não incorporam dados do consumidor nem diagnósticos internos.

Em OrderService, conjunto de ids detecta duplicatas antes da consulta/gravação; a lista original não é consolidada/reordenada. Mesmo produto com quantidades diferentes rejeita integralmente. Mantidos findAllById em lote no ProductRepository existente e reconstrução das linhas na ordem da solicitação. Produto não retornado usa a classe/mensagem existentes `ProductNotFoundException("Product not found")`; nenhuma alteração nessa exceção ou nos detalhes públicos.

Preço vigente é capturado uma vez por linha. Null/indisponível, zero ou negativo rejeitam com INVALID_PRODUCT_PRICE antes de normalizar/construir item, sem NPE. Para preço positivo/subtotal/soma, setScale(2, UNNECESSARY) e precision <= 19 verificam a capacidade decimal19,2. ArithmeticException é capturada **somente na chamada setScale** e traduzida como UNREPRESENTABLE_VALUE; excesso de precisão recebe o mesmo motivo. Nenhum catch amplo envolve lookup, construção, cálculo, saveAndFlush, Clock, mapping ou conclusão transacional. Falhas internas não são indiscriminadamente convertidas em rejeição de negócio.

BigDecimal permanece exato, sem MathContext/arredondamento/float/double; 1.000 normaliza para 1.00 sem alterar Product. Multiplicação inteira e somas usam snapshots históricos, verificando cada subtotal e soma antes de save. Não há persistência item a item: todos os itens devem passar para construir o agregado completo e realizar a única chamada saveAndFlush. Rejeição pode encerrar a validação assim que encontrada, sem prometer precedência entre múltiplos erros de negócio.

Preservados método público Spring, injeção por construtor, REQUIRED/rollbackFor Exception, reconsulta de conta/perfis, Clock UTC/milissegundos, WAITING_PAYMENT, mapping dentro da transação, ordem/posições e novo agregado/itens por envio válido. saveAndFlush continua sendo flush, não commit; proxy/contexto não foram novamente exercitados por esta execução unitária. Guardas já existentes não foram modificadas nem se executou T036.

Product/validações/schema, repositórios, parser, DTOs, Jackson global, JWT/segurança, ClockConfig, controller/advice, testes/fixtures e evidências T026–T033 preservados por hash. Nenhuma implementação de T035–T038, U1/U2 ou outros endpoints.

### Única execução de testes desta etapa

Sem filtro de métodos; somente OrderServiceTest, serial e com um fork:

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" "-Dsurefire.runOrder=alphabetical" test
```

Início **2026-10-08T17:05:05.6361096-03:00**, fim **17:05:39.1213415-03:00**; Maven **3.9.16**, Java **21.0.12.1**, Total time Maven **28.108 s**, duração Surefire **4.833 s**. Compilação concluída. Maven **código 0, BUILD SUCCESS**: **42 testes, 42 GREEN, zero falhas, zero erros, zero ignorados**. Nenhuma outra classe executada; paralelismo false no relatório atual.

| Grupo efetivamente executado | Casos | GREEN | Falhas/erros/ignorados |
| --- | ---: | ---: | --- |
| Positivos e limites representáveis | 18 | 18 | 0/0/0 |
| Guardas/reconsulta de identidade e perfis já existentes | 12 | 12 | 0/0/0 |
| Rejeições de negócio (incluindo inexistente) | 12 | 12 | 0/0/0 |
| **Total** | **42** | **42** | **0/0/0** |

Rejeições: inexistente entre válidos (1), duplicata com quantidades diferentes (1), preço null/zero/negativo entre positivos (3), frações não nulas além de duas casas/overflow unitário ou subtotal (6) e overflow da soma em um centavo (1). Todos os **12** registros T028_REJECTION têm **saveCalls=0**; onze com InvalidOrderException e um ProductNotFoundException. Assertivas mantidas exigem tipo exato, mensagem exata, verifyNoInteractions(orders) e reconsulta de conta/perfis. Nenhuma assertiva flexibilizada para aceitar exceções genéricas. Preço null/fração permanece fixture unitária; não se mudou Product para persistir valores inválidos.

Os 18 positivos preservam preços vigentes, ids enviados em lote, ordem restabelecida, preço alterado antes da leitura, preço histórico após mudança, normalização sem perda de 1.000, 10.00 × 2 + 7.50 × 3 = 42.50, agregado/itens novos em chamadas iguais, nova leitura de preços a cada envio e titular correspondente à identidade de cada chamada. Incluem cinco casos de limite (0.01, teto 99999999999999999.99, quantity Integer.MAX_VALUE com preço 0.01 e 10.00, subtotal exatamente no teto) e soma exatamente no teto. Não exigem IDs contíguos/instantes diferentes; o mock não gera IDs, portanto não se alegam IDs persistidos.

Doze guardas preexistentes continuam GREEN: principal ausente/id null/zero/negativo (4), conta ausente/perfis vazios (2), remoção/revogação após uma chamada válida com CLIENT/ADMIN/ambos (6). Zero saves nos seis casos iniciais; nos seis sequenciais somente o save da primeira chamada válida permanece (baselineSaveCalls=1), sem nova gravação após rejeição. Esta é evidência unitária de chamadas, não de JDBC/proxy/commit/rollback.

Comparação read-only com os relatórios T032: os mesmos **42 casos**, **31 GREEN anteriores mantidos** e **11 RED anteriores agora GREEN** (duplicata 1, preço inválido 3, precisão/preço/subtotal 6, soma 1). O inexistente já era GREEN e permaneceu. Nenhum RED artificial e nenhum teste de aplicação repetido antes da implementação; os REDs históricos compiláveis de T028/T032 e os fontes correspondentes permanecem intactos.

### Capturas, auditoria e limites

Captura ignorada `target/harness-validation/feature-004-t034-2026-10-08/`: baseline/preserved-before dos **3293 arquivos preexistentes**, originals/ dos documentos/serviço/testes, surefire-before/ anterior à execução, run-focused.ps1/result.json/order-service-console.txt, reports/ XML/TXT atuais, executed-sources/ e manifesto dos **77 fontes Java**, summarize.ps1/case-results/suite-results/class-totals/method-totals, review.ps1/business-rejections/historical-comparison e auditorias. Relatório atual posterior ao início do comando; estado executado preservado, sem fontes alterados após o teste.

`& 'target/harness-validation/feature-004-t034-2026-10-08/summarize.ps1'`: código 0, conferidos 42/42 GREEN e os vinte grupos de métodos. Auditoria documental por review.ps1 inicialmente encontrou erro no próprio helper ao filtrar diretamente o array JSON de T032 em pipeline PowerShell 5.1. Corrigido **somente helper ignorado**, separando parsing da filtragem; original preservado em review-first-audit.ps1 e causa em review-preparation.txt. Não houve falha de aplicação/teste/fixture nem edição de relatórios históricos ou repetição de Maven por isso.

Após o ajuste, `& 'target/harness-validation/feature-004-t034-2026-10-08/review.ps1'` concluiu com código 0: **3292/3293 arquivos** preexistentes intactos, somente OrderService alterado antes da documentação; InvalidOrderException é novo. Conferidos 42 casos únicos/GREEN, comando/código 0, scope de uma classe, relatório atual, 12 rejeições sem save, 31 GREEN mantidos/11 RED corrigidos e todos os 77 fontes executados iguais ao estado final. Não confundir análise de relatórios com nova execução de testes.

Alterações desta etapa: OrderService, InvalidOrderException novo, acréscimo neste quickstart e somente checkbox T034 em tasks. T034 marcada [X] apenas após execução GREEN, análise e registro. **T001–T034 [X], T035–T055 abertas (21)**. Próxima retomada: **T035**, sem execução nesta etapa.

Limites: OrderServiceTest usa construção direta/mocks; não comprova HTTP 422/envelope, decoder/filtros reais, persistência, commit/rollback nem chamada pelo proxy Spring. Histórico dessas outras provas preservado, sem reexecutá-las nem atribuir sua aprovação ao código novo. Rejeições unitárias sem save não são prova de rollback após SQL. Transporte real, falha física de commit e regressões públicas não foram executados. Sem outras classes, suíte completa, CI, T035+, stage, commit ou push.

Conferência final por `& 'target/harness-validation/feature-004-t034-2026-10-08/review.ps1' -Final`: código 0. Dos 3293 arquivos preexistentes, 3290 mantiveram SHA256; somente OrderService/quickstart/tasks mudaram nesta etapa, além da nova InvalidOrderException revisada integralmente. Prefixo histórico de quickstart com 368142 bytes intacto, incluindo T026–T033; tasks difere do início apenas pela checkbox T034. Todos os 77 fontes correspondem aos efetivamente executados; relatório atual/42 casos únicos GREEN/zero falhas, erros e ignorados/12 rejeições sem save/31 GREEN anteriores e 11 RED corrigidos conferidos. Diff do serviço, novo fonte e acréscimo documental revisados. `git diff --check` código 0; no-index --check dos três não rastreados sem diagnósticos, código 1 pela diferença contra /dev/null. Não reescritas linhas históricas por avisos LF/CRLF. Índice vazio, base Git preservada; status final mantém oito rastreados modificados e três não rastreados, incluindo o trabalho anterior intacto. T001–T034 [X], T035–T055 abertas; hooks ausentes. Auditoria documental reaplicada após este acréscimo, sem repetir testes de aplicação.

## T035 — validação JWT estrita da operação — 2026-10-09

Executada exclusivamente T035; parada antes de T036. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD inicial/final `0344dc0199fd8135f98e852e7d6fedc06b124d9a`, conferidos com os quatro comandos Git solicitados. Alterações locais e evidências T026–T034 preservadas. A implementação estrita do converter, sua injeção de Clock em SecurityConfig, os testes unitários ampliados e a observação T035_SERVICE já estavam presentes no início desta retomada. A captura preexistente `target/harness-validation/feature-004-t035-2026-10-08/` foi preservada como execução anterior, sem reatribuir seus resultados à execução atual.

Skill speckit-implement aplicada somente a esta task; pré-requisitos identificaram a Feature 004 e requirements.md apresentou 16/16 marcados, zero pendentes. Consultados task/dependências T034/T027, constituição, spec, plano, pesquisa D1/D2/D7/D8, modelo, contratos e harness. Ignore Java existente suficiente. `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Implementação conferida e cobertura complementada

OrderJwtAuthenticationConverter exige exp/iat como Instant utilizáveis; nbf presente também deve ser utilizável. Usa uma leitura do Clock UTC injetado por autenticação: exp > agora, exp > iat, iat <= agora e nbf <= agora, sem tolerância adicional. Subject precisa ser String correspondente integralmente a `[1-9][0-9]*`, positivo e representável como Long. Não há trim ou coerção para aceitar formatos inválidos. Como Nimbus pode normalizar um sub numérico para String, a adaptação compara também o tipo/valor do subject no payload original, somente após o decoder verificar o token. O fluxo real JwtAuthenticationProvider decodifica/verifica antes de chamar o converter; accounts.resolve ocorre depois das validações temporais e de subject.

BadCredentialsException e OrderAccountResolutionException mantêm tipos distintos; os testes preservam a mesma instância da exceção de resolução. Name/roles não escolhem identidade ou authorities; sem novos claims obrigatórios iss/aud/roles. Decoder compartilhado, encoder/login, Clock produtivo e fontes produtivos permaneceram iguais ao início desta retomada. Não implementados handlers/envelopes nem guardas de T036.

Acrescentado `src/test/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationPipelineTest.java`: JwtConfig/decoder e JwtAuthenticationProvider reais, converter real e somente resolvedor mockado. Seus 25 casos verificam assinatura adulterada/chave incorreta/token malformado, claims temporais ausentes ou inutilizáveis, limites temporais, subjects inválidos inclusive JSON numérico normalizado pelo decoder, limites positivos 1/42/Long.MAX_VALUE e releitura do Clock entre autenticações. Toda rejeição exige zero novas interações com a resolução. Esta prova usa criptografia real, mas não é uma prova HTTP/banco; a integração separada cobre essas dimensões.

A regressão OrderSecurityChainTest inicialmente apresentou quatro falhas de preparação temporal: Clock controlável ainda fixo em 2030 e tokens do login emitidos no instante real. Ajustados somente import/injeção do Clock existente e alinhamento de seu instante imediatamente após o login/decoder. Preservadas todas as assertivas, inclusive CLIENT/ADMIN/ambos, conta sem perfil, titularidade, stateless e rotas. Nenhuma tolerância produtiva foi adicionada.

### Execuções atuais pelo Wrapper

Maven 3.9.16 e Java Zulu 21.0.12.1 confirmados por `.\mvnw.cmd --version`. Todos os comandos abaixo usam `-Djunit.jupiter.execution.parallel.enabled=false`, a partir da raiz, com stdout/stderr capturados via cmd.exe /d /c; relatórios XML/TXT copiados após cada execução.

| Comando (além da opção serial) | Casos | GREEN | Falhas/erros/ignorados | Código Maven |
| --- | ---: | ---: | --- | ---: |
| `.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest,OrderJwtAuthenticationPipelineTest" test` | 73 | 73 | 0/0/0 | 0 |
| `.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest#shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims+shouldRejectInvalidJwtBeforeReadingBody" test` | 55 | 9 | 46/0/0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest,JwtTokenServiceTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest" test` (antes de alinhar a fixture) | 30 | 26 | 4/0/0 | 1 |
| `.\mvnw.cmd "-Dtest=OrderSecurityChainTest" test` (após alinhar a fixture) | 20 | 20 | 0/0/0 | 0 |

Intervalos atuais: unidade/pipeline 2026-10-09T14:50:18.1674020-03:00 → 2026-10-09T14:50:37.5125547-03:00; HTTP 2026-10-09T14:52:19.0174866-03:00 → 2026-10-09T14:53:14.0277797-03:00; regressões iniciais 2026-10-09T14:54:10.0746576-03:00 → 2026-10-09T14:54:48.1006171-03:00; chain corrigida 2026-10-09T14:57:57.0589792-03:00 → 2026-10-09T14:58:45.0356679-03:00. Primeira tentativa administrativa de unidade interrompida por PowerShell NativeCommandError ao receber aviso ByteBuddy em stderr; preservada separadamente, sem LASTEXITCODE confiável nem alegação de RED. A repetição com captura corrigida produziu os 73 GREEN acima.

Converter: 48/48 GREEN, incluindo fronteiras de um nanossegundo, exp/iat obrigatórios/utilizáveis, nbf opcional, sub sem coerção e distinção das exceções. Pipeline: 25/25 GREEN. HTTP real: nove positivos com CLIENT/ADMIN/ambos e claims falsos preservaram 201/titular correto/linhas confirmadas. Nas 46 rejeições, incluindo corpo inválido em /api/orders, observados 401, serviceCalls=0 e ordersDelta=itemsDelta=0 em leitura independente antes da limpeza. Nenhuma gravação indevida dos sete gaps temporais/subject históricos voltou a ocorrer.

**A execução HTTP não está GREEN como contrato completo**: todas as 46 falhas são MultipleFailuresError relativas a Content-Type/envelope JSON ausente; 12 também apontam challenge detalhado padrão em lugar de Bearer exato. Status, confidencialidade verificada pelas assertivas existentes, ausência de serviço/gravação e precedência sobre leitura do corpo não falharam. Envelopes/challenge ficam em T037, sem alteração de assertivas ou implementação antecipada. Não executados os métodos de guardas tardias/chamada direta/resolução interna dessa classe nesta retomada. Não se aprova HTTP 500 de resolução por inferência da unidade.

Regressões finais: 20/20 da chain, 7/7 catálogo público, 2/2 detalhes públicos e 1/1 emissão JWT, zero falhas/erros/ignorados nessas execuções finais. Só a classe que exigiu ajuste de fixture foi repetida. Sem suíte completa, CI ou tarefas posteriores; fechamento integrado e demais cenários continuam nos checkpoints próprios.

### Preservação e fechamento

Capturas ignoradas em `target/harness-validation/feature-004-t035-2026-10-09/`: baseline/manifesto de 3723 arquivos preexistentes, cópias de quickstart/tasks e surefire-before, versões, comandos/intervalos/códigos em result.json por execução, consoles e relatórios por execução, causas HTTP, manifesto dos fontes executados e auditoria final. Evidências anteriores, inclusive T026–T034 e a captura anterior de T035, não foram sobrescritas. Prefixo histórico de quickstart preservado byte a byte; tasks difere da entrada somente pela checkbox T035.

Nesta retomada, alterados somente OrderSecurityChainTest (preparação do Clock), quickstart (acréscimo) e checkbox T035; novo OrderJwtAuthenticationPipelineTest. Implementação estrita preexistente preservada. Diff e fonte novo revisados; git diff --check e no-index --check do teste novo executados, sem diagnósticos de whitespace. Auditoria compara todos os arquivos preexistentes e permite diferenças somente nesses três arquivos. Índice permanece vazio, raiz/branch/HEAD mantidos, sem stage/commit/push/merge/troca de branch. T001–T035 [X], T036–T055 abertas (20). T035 concluída no seu escopo; parada antes de T036.

## T035 — complemento: duas classes completas, serialmente — 2026-10-09

Após o checkpoint anterior, executadas exclusivamente as duas classes solicitadas, nesta ordem: OrderJwtAuthenticationConverterTest e OrderSecurityIntegrationTest. A segunda foi executada completa, sem filtro de métodos. Estas execuções complementam o registro anterior; não o substituem nem reatribuem seus resultados. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido` e HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` reconfirmados com git rev-parse --show-toplevel, git branch --show-current, git rev-parse HEAD e git status --short. Sem troca de branch, stage, commit, push ou merge.

### Comandos e resultados atuais

Na raiz, pelo Maven Wrapper, mantendo Maven 3.9.16/Java 21 do ambiente confirmado acima. Cada comando teve um fork e paralelismo JUnit desabilitado; stdout/stderr capturados por cmd.exe /d /c, com LASTEXITCODE registrado. As execuções não se sobrepuseram.

```powershell
.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Classe | Intervalo local (-03:00), 2026-10-09 | Testes | GREEN | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OrderJwtAuthenticationConverterTest | 15:06:57.8691517 → 15:07:15.1697522 | 48 | 48 | 0 | 0 | 0 | 0 |
| OrderSecurityIntegrationTest | 15:08:07.7228473 → 15:09:01.8385929 | 85 | 23 | 62 | 0 | 0 | 0 | 1 |

Os arquivos result.json registram comandos/horários completos, código real, classe única efetivamente executada e ausência de alteração de fontes/status. Capturas novas, com relatórios anteriores copiados antes de cada execução:

- [Resultado do converter](../../target/harness-validation/feature-004-t035-converter-only-2026-10-09-150657/result.json), console.txt e reports/ XML/TXT no mesmo diretório.
- [Resultado da integração completa](../../target/harness-validation/feature-004-t035-security-only-2026-10-09-150807/result.json), console.txt, reports/ XML/TXT, method-results.json e http-observations.txt no mesmo diretório.

Esses caminhos em target/ são evidências locais ignoradas pelo Git. As contagens e causas ficam registradas aqui para manter o relato compartilhável, sem pressupor que outro checkout contenha as capturas.

### Comprovações separadas

**JWT inválido antes do serviço e sem novas linhas:** 46 casos, correspondentes a 23 variantes em dois contextos, todos retornaram 401, escaped=false, serviceCalls=0, ordersDelta=0 e itemsDelta=0. Há 23 casos com corpo válido e 23 com corpo ilegível em /api/orders. As contagens de tb_order/tb_order_item são lidas por JdbcTemplate/conexão independente depois da solicitação síncrona e antes da limpeza, sem transação de teste externa. Conta ausente está entre os casos de autenticação rejeitada; não se confunde seu lookup necessário com uma falha anterior de validação. [Observações por caso](../../target/harness-validation/feature-004-t035-security-only-2026-10-09-150807/invalid-jwt-proof.json).

**Sem lookup quando a validação anterior falha:** 39 testes atuais do converter passaram exigindo verifyNoInteractions(accounts): 20 de exp/iat/nbf inválidos ou inutilizáveis, cinco de accountId inutilizável, 13 de sub não canônico/não textual e um de sub numérico original normalizado pelo decoder. O único caminho de lookup é converter → AuthenticatedAccountService.resolve → UserAccountRepository.findByIdWithRoles, depois das validações. Essa prova unitária observa ausência de chamada ao resolvedor mockado; não é instrumentação de SQL. [Resumo e métodos](../../target/harness-validation/feature-004-t035-converter-only-2026-10-09-150657/no-lookup-proof-summary.json).

Para a etapa criptográfica, o resumo identifica separadamente a evidência anterior do pipeline real, executada às 14:50:18.1674020 → 14:50:37.5125547: 21 rejeições com verifyNoInteractions(accounts), incluindo assinatura adulterada/chave incorreta, decoder/provider reais e somente resolvedor mockado. Hashes dos fontes continuam iguais aos daquela execução. OrderJwtAuthenticationPipelineTest não foi executado novamente nesta rodada restrita às duas classes solicitadas; o resultado anterior não é contado entre seus 48/85 casos.

**Segurança antes do corpo inválido:** 23 casos de autenticação inválida retornaram 401 e um de conta sem perfis retornou 403, sem chegar ao 400 de leitura. No caso de falha interna com corpo inválido, a resolução foi alcançada e OrderAccountResolutionException escapou; não foi convertida em 401/400. Falta seu adaptador HTTP 500 de T037. Nos 25 casos, zero chamadas ao OrderService e zero novas linhas. [Comprovação por caso](../../target/harness-validation/feature-004-t035-security-only-2026-10-09-150807/security-before-invalid-body-proof.json).

**Positivos preservados:** dez controles HTTP passaram com login/JWT/decoder/filtros reais: nove combinações CLIENT/ADMIN/ambos com variantes de claims name/roles e um controle positivo em /api/orders. Mantidas assertivas 201, titular da conta correta, valores/itens e persistência após retorno. Testes, suporte OrderHttpTestSupport (definido em OrderControllerTest.java) e OrderTestFixture permanecem iguais por hash ao início da retomada de T035. Os outros 13 GREEN da integração são cinco identidades inválidas e oito reconsultas de conta/perfis através do proxy real; exercitam guardas preexistentes, sem nova implementação de T036. [Controles positivos e hashes](../../target/harness-validation/feature-004-t035-security-only-2026-10-09-150807/positive-controls-proof.json).

### Causas restantes e atribuição às tarefas futuras

As 62 falhas da integração são MultipleFailuresError em assertivas de resposta HTTP. Não houve erros de compilação/startup/fixture, remoção de cenários ou relaxamento de assertivas.

| Grupo de falhas | Quantidade | Causa observada | Tarefa futura |
| --- | ---: | --- | --- |
| JWT inválido | 46 | Content-Type/envelope JSON ausente; 12 casos também têm challenge detalhado padrão em lugar de Bearer exato | T037 |
| Conta sem perfis | 2 | 403 correto, envelope JSON ausente | T037 |
| Falha interna na resolução | 2 | OrderAccountResolutionException escapa sem resposta HTTP; falta 500 seguro. Um caso tem corpo válido, outro inválido | T037 |
| Conta removida/perfis revogados após o filtro | 12 | 401/403 corretos, uma chamada real ao serviço, zero novas linhas, mas envelope ausente | T038, coordenada com T037 |
| **Total** | **62** | **Contrato HTTP de erros ainda pendente** | **T037/T038** |

[Classificação por teste e mensagem observada](../../target/harness-validation/feature-004-t035-security-only-2026-10-09-150807/future-task-failures.json). As 46 rejeições JWT e as 12 guardas tardias mantiveram contagens independentes de pedidos/itens. A causa de falha interna conserva seu tipo; a classificação distingue conta ausente/credencial inválida de OrderAccountResolutionException, sem conversão indiscriminada em 401. A ausência de resposta nos dois casos internos não é apresentada como HTTP 500 aprovado.

Não se exige GREEN completo da integração para concluir o escopo de T035 antecipando T037/T038. T035 continua marcada; T036, T037 e T038 permanecem abertas. Guardas novas, adaptadores de segurança e ControllerAdvice não foram implementados nesta rodada. Perfis continuam vindo do banco, CLIENT ou ADMIN autoriza; claims name/roles são ignoradas, sem novos iss/aud/roles obrigatórios e com nbf opcional. JwtConfig, chave/assinatura HS256 e emissão de login preservados. Wiring mínimo do Clock já documentado no checkpoint anterior; nenhum fonte ou preparo de testes foi alterado nestas duas execuções ou nas análises posteriores.

### Revisão documental desta complementação

Alterado somente este quickstart por acréscimo. Prefixo anterior preservado byte a byte; todos os demais arquivos rastreados/não rastreados e as capturas atuais selecionadas conferidos por hash. Tasks não alterado, índice vazio e Git na mesma raiz/branch/HEAD, com 12 rastreados modificados e quatro não rastreados preexistentes. Comandos/contagens conferidos nos XMLs e JSONs; links locais revisados e git diff --check executado. As análises de evidência não constituem novas execuções Maven. Sem nova suíte completa, regressões adicionais, CI ou avanço para T036–T038.

## T036 — guardas transacionais de identidade e perfis — 2026-10-09

Escopo autorizado exclusivamente T036, com parada antes de T037. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido` e HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` confirmados com `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`. Entrada: 12 arquivos rastreados modificados e quatro não rastreados; índice vazio. Alterações anteriores preservadas. Skill speckit-implement aplicada ao escopo restrito; prerequisites apontam Feature 004, checklist requirements.md com 16/16 marcados e zero abertos. `.specify/extensions.yml` ausente, sem hooks.

### Gate de T035: consulta histórica, sem repetir a execução

Antes da validação de T036 foram consultados os registros existentes de T035, seus comandos/resultados, XMLs e provas por caso. Evidência de conclusão presente; nenhuma lacuna impeditiva. Os 81 fontes de src/ continuam iguais por SHA-256 aos executados no último checkpoint de T035. Não foram reexecutadas classes de converter, pipeline ou chain nesta etapa. Auditoria local: [t035-gate.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/t035-gate.json).

Os comandos históricos abaixo foram executados em 2026-10-09, pelo Wrapper, na forma `.\mvnw.cmd "-Dtest=<seleção>" "-Djunit.jupiter.execution.parallel.enabled=false" test`. As duas últimas linhas acrescentaram `"-DforkCount=1" "-DreuseForks=true"` antes de test. Não se atribuem esses resultados às novas execuções de T036.

| Seleção histórica de -Dtest | Classes/contagens | Resultado | Código Maven |
| --- | --- | --- | ---: |
| OrderJwtAuthenticationConverterTest,OrderJwtAuthenticationPipelineTest | converter 48; pipeline 25 | 73 aprovados, zero falhas | 0 |
| OrderSecurityIntegrationTest#shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims+shouldRejectInvalidJwtBeforeReadingBody | integração, 55 | 9 aprovados; 46 falhas de envelope/challenge | 1 |
| OrderSecurityChainTest,JwtTokenServiceTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest | chain 20; emissão 1; catálogo 7; detalhe 2 | 26 aprovados; quatro falhas da fixture de Clock na chain | 1 |
| OrderSecurityChainTest, após alinhamento da fixture | chain, 20 | 20 aprovados, zero falhas | 0 |
| OrderJwtAuthenticationConverterTest, classe completa | converter, 48 | 48 aprovados, zero falhas | 0 |
| OrderSecurityIntegrationTest, classe completa | integração, 85 | 23 aprovados; 62 falhas de contrato HTTP | 1 |

Todas essas execuções históricas tiveram zero erros e zero ignorados. Capturas anteriores preservadas em feature-004-t035-2026-10-09/, feature-004-t035-converter-only-2026-10-09-150657/ e feature-004-t035-security-only-2026-10-09-150807/ dentro de target/harness-validation/. Comandos integrais, intervalos e códigos constam dos JSONs/console/XML nesses diretórios e das seções anteriores deste quickstart.

As sete variantes anteriormente aceitas — EXPIRED_RECENTLY, MISSING_EXP, MISSING_IAT, SUB_LEADING_ZERO, SUB_PLUS, FUTURE_IAT e FUTURE_NBF — estão comprovadamente impedidas: 14 casos (sete com corpo válido e sete com corpo inválido em /api/orders), todos 401, escaped=false, serviceCalls=0, ordersDelta=0 e itemsDelta=0. A prova histórica completa cobre 46 rejeições/23 variantes. As verificações unitárias de ausência de lookup e o pipeline real são evidências distintas da ausência de chamadas ao OrderService observada por HTTP. Validação JWT concluída não significa aprovação dos envelopes ainda pendentes em T037/T038.

Em OrderSecurityChainTest, a fixture de Clock inicialmente fixa em 2030 fazia o JWT de login real parecer expirado depois da validação estrita. T035 acrescentou a injeção do ControlledClock existente e `clock.setInstant(Instant.now())` após decodificar o login, com import de Instant. Cenários, perfis, rotas e assertivas foram preservados; nenhuma tolerância de produção foi acrescentada. O novo OrderJwtAuthenticationPipelineTest usa decoder de JwtConfig, JwtAuthenticationProvider e converter reais, com chave descartável e somente resolução da conta mockada. Seus 25 testes históricos são 21 rejeições sem lookup, três subjects canônicos positivos e uma reautenticação que reconsulta o Clock e impede lookup adicional em exp. Comprova a ordem criptografia/claims/lookup; não substitui os controles HTTP de login/persistência reais. Nenhum desses dois arquivos foi alterado ou executado novamente em T036.

### Revisão das guardas existentes: correção dispensada

OrderService.register já atende ao planejamento; preservada a implementação, sem alteração artificial de produção ou testes:

- Método público chamado externamente pelo controller e pelo proxy Spring, com `@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)`. Reconsulta, leituras, construção e única chamada saveAndFlush pertencem à mesma transação pública; não há self-invocation nem transação separada de guarda no serviço.
- Principal ausente, accountId null, zero ou negativo são rejeitados por BadCredentialsException antes de qualquer repositório. AccountId positivo é consultado por findByIdWithRoles; conta ausente também gera BadCredentialsException.
- Perfis são novamente lidos do banco pela consulta com left join fetch de roles. Exige pelo menos CLIENT ou ADMIN (OR); sem ambos, AccessDeniedException. Não confia nas authorities antigas do filtro, em claims name/roles, nem substitui o comprador por identidade alternativa.
- Consulta/guardas precedem inclusive request.items, leitura de produtos e qualquer gravação. Mensagens fixas de autenticação/acesso permanecem distinguíveis. Falhas internas de resolução continuam OrderAccountResolutionException; não foram capturadas indiscriminadamente como credencial inválida.
- OrderController recebe AuthenticatedAccountPrincipal via @AuthenticationPrincipal e chama o método público do serviço. Verificados POST /orders e /api/orders com servlet context /api, além das chamadas diretas pelo bean/proxy real.

### Execuções atuais: somente duas classes completas e serialmente

Java 21.0.12.1, Azul (runtime 21.0.12.1+1-LTS), confirmado nas propriedades dos XMLs. Wrapper configurado para Maven 3.9.16. Na raiz, comandos completos, um fork e paralelismo JUnit desabilitado:

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

Stdout/stderr capturados por cmd.exe /d /c; LASTEXITCODE registrado imediatamente. A segunda execução começou depois da conclusão e inspeção da primeira; sem seleção de métodos, outras classes, suíte completa ou CI.

| Classe | Intervalo local (-03:00), 2026-10-09 | Testes | Aprovados | Falhas | Erros | Ignorados | Código Maven |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OrderServiceTest | 15:31:32.9287810 → 15:31:50.4578762 | 42 | 42 | 0 | 0 | 0 | 0 |
| OrderSecurityIntegrationTest | 15:32:06.5578452 → 15:33:00.9288985 | 85 | 23 | 62 | 0 | 0 | 0 | 1 |

Captura nova: `target/harness-validation/feature-004-t036-2026-10-09-153101/`. [service-result.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/service-result.json) e [security-result.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/security-result.json) registram comando/intervalo/código e a única classe efetivamente executada em cada comando. Consoles service-console.txt/security-console.txt, XMLs/TXTs em service-reports/security-reports e cópia dos relatórios anteriores em reports-before. Essas capturas são locais e ignoradas pelo Git; este registro mantém as contagens/causas compartilháveis.

### Guardas, transação, ausência de gravação e positivos

**Unidade: 12 casos de guarda aprovados**, dentro dos 42/42: quatro identidades inválidas sem interação com qualquer repositório; conta ausente e conta sem perfis, sem leitura de produtos ou gravação; seis combinações CLIENT/ADMIN/ambos × conta removida/perfis revogados entre duas chamadas. Nos seis, a primeira chamada positiva realiza a gravação de referência, a segunda reconsulta a conta e é rejeitada: duas consultas de conta, somente uma leitura de produtos e um saveAndFlush total (baselineSaveCalls=1), sem gravação adicional. Mensagens/tipos BadCredentialsException e AccessDeniedException exigidos. O teste de identidade por chamada também confirma compradores diferentes 42/43 sem reutilização do anterior. [unit-guards-proof.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/unit-guards-proof.json), service-cases.json e XML da unidade.

**Proxy Spring real: 13/13 aprovados**: cinco identidades inválidas (incluindo conta inexistente Long.MAX_VALUE) e oito cenários DELETE_ACCOUNT/REVOKE_PROFILES × perfis originais vazio/CLIENT/ADMIN/ambos. Conta removida gera BadCredentialsException; conta existente sem perfis permitidos gera AccessDeniedException. Cada chamada entra uma vez no serviço com transação ativa. Não há transação externa de teste; mutações de fixture são concluídas antes da chamada. Contagens por conexão JDBC independente permanecem iguais depois da chamada, antes da limpeza. A preparação exige AopUtils.isAopProxy(orderService); advice de observação somente de teste é colocado dentro do interceptor transacional e removido na limpeza. [direct-proxy-proof.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/direct-proxy-proof.json).

**HTTP tardio: 12 casos com guardas/status/sem gravação comprovados**, mas com contrato de envelope pendente. Depois de autenticação/autorização reais, uma fixture de teste confirma remoção da conta ou revogação dos perfis em transação separada concluída antes do controller. Matriz CLIENT/ADMIN/ambos × duas mutações × /orders ou /api/orders. O serviço é chamado uma vez, dentro da própria transação, reconsulta o mesmo accountId e impede a gravação: seis contas removidas retornam 401, seis revogações retornam 403; escaped=false, ordersDelta=0 e itemsDelta=0 nos 12. Assertivas de mutação concluída, transação do serviço ativa, estado atual de conta/perfis e confidencialidade não falharam. Os 12 XMLs têm somente uma falha agregada de Content-Type null em assertRejected: JSON/envelope ausente, não falha das guardas. [late-http-proof.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/late-http-proof.json) contém observações e mensagens/stack por caso. O 401/403 observado é separado da aprovação do envelope.

**Positivos preservados**: dez controles HTTP aprovados com login/JWT/decoder/filtros/persistência reais. Nove combinam CLIENT/ADMIN/ambos com claims name/roles normais, falsos/vazios ou alheios; um confirma ADMIN em /api/orders. Assertivas 201, comprador autenticado correto, valores/itens e gravação observada após retorno permanecem intactas. Os 23 aprovados da integração são esses dez positivos mais 13 diretos pelo proxy. Nenhum cenário/assertiva/preparo de Clock/claims foi alterado. [positive-controls-proof.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/positive-controls-proof.json) e [scope-proof-summary.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/scope-proof-summary.json).

### Pendências HTTP separadas do escopo comprovado de T036

| Grupo atual | Casos | Resultado observado e causa restante | Tarefa futura |
| --- | ---: | --- | --- |
| JWT inválido | 46 | 401, zero chamadas ao serviço e zero novas linhas; Content-Type/envelope ausente, 12 também com challenge detalhado em lugar de Bearer exato | T037 |
| Conta sem perfis mesmo com claim ADMIN | 2 | 403, zero chamadas ao serviço e zero novas linhas; envelope ausente | T037 |
| Falha interna de resolução | 2 | OrderAccountResolutionException escapa, sem resposta HTTP e sem gravação; adaptador 500 seguro ausente. Não é 401 nem HTTP 500 aprovado | T037 |
| Guardas tardias | 12 | 401/403 corretos, serviço/transação reais e zero novas linhas; envelope ausente | T038, coordenada com T037 |
| **Total de falhas contratuais** | **62** | **Zero erros de execução; contrato HTTP completo ainda não GREEN** | **T037/T038** |

Classificação por teste em [future-task-failures.json](../../target/harness-validation/feature-004-t036-2026-10-09-153101/future-task-failures.json). Em corpo inválido, os 23 casos JWT retornam 401 e o caso sem perfis retorna 403 antes de leitura/400; a falha interna preserva seu tipo e ainda não recebe 500 HTTP. Não houve novos adaptadores, ControllerAdvice, wiring, requisitos de claims ou alterações de JwtConfig/HS256/emissão de login. Não se antecipa T037/T038 para forçar GREEN completo.

T036 comprovada no seu escopo por revisão e execução dos testes existentes, sem correção de código necessária. Autorizada a marcação somente de T036; T001–T036 ficam [X] e T037–T055 abertas (19). Parada antes de T037. Não aplicados U1/U2, stage, commit ou push.

### Preservação, diff e Git final de T036

Alterados nesta etapa somente quickstart.md por acréscimo e tasks.md pela checkbox T036. Manifesto de entrada de 3927 arquivos: 3773 evidências históricas em target/harness-validation e 154 arquivos do inventário Git; comparação SHA-256 permite somente esses dois documentos como diferenças. Todos os fontes/testes, arquivos não rastreados anteriores e evidências de T026–T035 preservados. Prefixo histórico do quickstart mantido byte a byte; tasks difere da entrada exclusivamente de `[ ] T036` para `[X] T036`. [Auditoria de preservação](../../target/harness-validation/feature-004-t036-2026-10-09-153101/final-preservation.json).

Diff incremental contra as cópias de entrada revisado (quickstart-incremental.diff/tasks-incremental.diff); estatística do diff total distingue alterações preexistentes da entrega documental atual. Links de evidências conferidos. `git diff --check` executado com código 0, sem erro de whitespace. Também executado `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>` para cada um dos quatro não rastreados preexistentes: código 1 por comparação com arquivo novo, zero diagnósticos de whitespace. Revisão em review-checks.json/untracked-whitespace-check.json; causas de falha de envelope conferidas também nos stacks (Content-Type na linha 521; challenge na linha 525 de OrderSecurityIntegrationTest).

Conferidos 55 IDs únicos: T001–T036 [X] (36), T037–T055 abertas (19). Pós-hook dispensado por ausência de .specify/extensions.yml. Raiz/branch/HEAD finais iguais aos de entrada; git status --short mantém os mesmos 12 rastreados modificados e quatro não rastreados, índice vazio. OrderService continua modificado apenas pelo trabalho anterior, sem edição em T036. HEAD final `0344dc0199fd8135f98e852e7d6fedc06b124d9a`, branch `004-registrar-pedido`. Sem outras classes, suíte completa, CI, T037+, U1/U2, stage, commit, push, merge ou troca de branch. Encerramento em T036 com pendências HTTP explicitamente reservadas a T037/T038.

## T037 — adaptadores de erros dos filtros de pedidos — 2026-10-09

Retomada exclusivamente de T037 após a auditoria da interrupção elétrica, com parada antes de T038. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido` e HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` confirmados inicialmente e no fechamento. Entrada: 12 arquivos rastreados modificados, quatro não rastreados e índice vazio; trabalho anterior de T001–T036 preservado. O diff total contra HEAD inclui esse trabalho anterior e não representa somente T037.

Skill speckit-implement aplicada ao escopo autorizado. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` concluiu com código 0, FEATURE_DIR absoluto desta feature e os artefatos de planejamento disponíveis. Checklist requirements.md conferido somente em leitura: 16/16 marcados, zero abertos. Constituição, tarefas, plano, pesquisa, modelo, contrato e registros pertinentes do quickstart consultados. Git/ignores verificados: target, classes, JARs, build e artefatos locais já cobertos; nenhuma alteração de ignore necessária. `.specify/extensions.yml` ausente antes e depois, sem hooks before_implement/after_implement.

### Base de testes anterior e preservação

A auditoria e a captura anterior de T036 demonstravam a ausência dos dois adaptadores e o RED contratual de OrderSecurityIntegrationTest: 85 casos concluídos, 23 aprovados, 62 falhas, zero erros/ignorados e exit code 1. Dessas falhas, 50 eram dos filtros (46 JWT, duas permissões, duas resoluções internas); 12 eram guardas tardias. Essa evidência foi consultada como histórica; não houve nova execução RED nem modificação dos testes para provocar falha.

Captura nova em `target/harness-validation/feature-004-t037-2026-10-09-170505/`, ignorada pelo Git: manifesto de entrada de 4025 arquivos (154 do inventário Git e 3871 evidências históricas), cópias anteriores de SecurityConfig/converter/quickstart/tasks e reports-before com os relatórios Surefire preexistentes. Os relatórios antigos substituídos pelo Maven permanecem nessa cópia e nas capturas históricas. Fontes executados copiados para executed-sources antes das execuções.

Uma primeira conferência administrativa de hashes usou Convert.ToHexString, indisponível no .NET deste PowerShell. Seu resultado em preservation-incompatible-net-invalid.json é inválido e não comprova preservação; código de saída isolado não seria aprovação. A repetição com SHA256.ComputeHash e BitConverter.ToString concluiu corretamente: todos os 4025 arquivos comparados, única diferença antes da documentação em SecurityConfig; evidências históricas e testes intactos. Resultado em preservation-before-documentation.json. Essa correção do auxiliar não alterou a aplicação nem exigiu repetir testes.

### Implementação exclusivamente de T037

- Criados OrderAuthenticationEntryPoint.java e OrderAccessDeniedHandler.java, serializando ErrorResponseDTO com exatamente status, error, message e path, application/json e mensagens fixas. 401 / Unauthorized / Authentication required or invalid; 403 / Forbidden / Order registration not permitted; 500 / Internal Server Error / Order registration could not be completed.
- O 401 inclui exatamente WWW-Authenticate: Bearer. O 403 não emite challenge opcional. Path usa request.getRequestURI(), preservando o URI externo e o context path /api. Adaptadores não usam sendError, ControllerAdvice, mensagem da exceção ou causa como conteúdo, nem registram credencial, corpo, SQL ou stack trace.
- Instâncias locais ligadas somente à orderSecurityFilterChain em SecurityConfig, tanto no Resource Server quanto na tradução de exceções dessa chain. Matcher permanece somente POST /orders; fallback público, métodos/rotas restantes, H2/SAMEORIGIN, stateless e CSRF preservados.
- A ligação JWT delega ao converter existente e captura somente OrderAccountResolutionException, encapsulando-a em OAuth2AuthenticationException com código server_error e causa tipada. O entry point reconhece essa causa e produz 500 genérico; não converte falha interna em BadCredentialsException/401. Outros erros mantêm seu fluxo de autenticação.
- OrderJwtAuthenticationConverter.java permanece igual à entrada: validação criptográfica pelo decoder antes da conversão, validação temporal/sub antes do lookup, perfis derivados do banco e claims name/roles ignorados. Preservados também decoder/chave/login, OrderService, controller, ControllerAdvice e todos os testes/assertions.

Arquivos desta entrega: SecurityConfig.java, os dois adaptadores novos, este acréscimo no quickstart e a checkbox T037 em tasks.md. Demais diffs e os quatro não rastreados anteriores pertencem ao trabalho preexistente.

### Duas execuções atuais, completas e serializadas

Executadas somente as classes autorizadas, sem seleção de métodos, outras classes, suíte completa ou CI. A segunda começou após a conclusão e inspeção do resultado da primeira. Comandos na raiz:

```powershell
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityChainTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

Wrapper fixa Maven 3.9.16; propriedades dos XMLs confirmam Java 21.0.12.1, Azul. Stdout/stderr capturados por cmd.exe /d /c e LASTEXITCODE registrado imediatamente. XMLs novos, escritos após o início de cada execução, corroboram os totais; consoles registram BUILD SUCCESS e Finished at.

| Classe | Intervalo local (-03:00), 2026-10-09 | Testes | Aprovados | Falhas | Erros | Ignorados | Exit code |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OrderSecurityIntegrationTest | 17:08:29.2355565 → 17:10:19.8214626 | 85 | 85 | 0 | 0 | 0 | 0 |
| OrderSecurityChainTest | 17:10:26.8250812 → 17:10:59.4307181 | 20 | 20 | 0 | 0 | 0 | 0 |

Resultados: [security-result.json](../../target/harness-validation/feature-004-t037-2026-10-09-170505/security-result.json) e [chain-result.json](../../target/harness-validation/feature-004-t037-2026-10-09-170505/chain-result.json). Logs security-console.txt/chain-console.txt e cópias XML/TXT em security-reports/chain-reports registram as duas execuções separadamente; run-selected.ps1 preserva o procedimento de captura. Estes 105 casos aprovados são novos e distintos dos resultados históricos de T035/T036.

### Comprovação por grupo e limites de T038

Os 85 testes da integração passam sem alteração dos fontes de teste, fixtures ou assertions:

| Grupo | Casos aprovados | Comprovação |
| --- | ---: | --- |
| JWT inválido/ausente e conta inexistente | 46 | 23 variantes × /orders com corpo válido ou /api/orders com corpo inválido; 401, JSON exato e Bearer exato |
| Conta sem perfis com claim ADMIN falso | 2 | Login válido preservado; 403 e JSON contratual antes da leitura do corpo |
| Falha interna de lookup | 2 | 500 HTTP real, genérico, sem exceção escapando; inclui /api/orders e corpo inválido |
| Guardas tardias | 12 | Conta removida → 401, perfis revogados → 403; uma chamada real ao serviço por caso e nenhuma nova linha |
| Positivos de login/JWT/persistência | 10 | CLIENT/ADMIN/ambos, claims name/roles e confirmação em /api/orders preservados |
| Chamadas diretas pelo proxy Spring | 13 | Identidade/existência/perfis atuais e ausência de gravação nas rejeições preservados |

As 50 rejeições dos filtros têm zero chamadas ao OrderService, nenhuma nova linha de pedido/item e nenhuma exceção escapando. Metade (25) combina corpo inválido e context path /api, demonstrando precedência da segurança sobre a leitura. Assertivas existentes de igualdade integral do JSON conferem os quatro campos, mensagens e path; as de confidencialidade em resposta, headers e logs conferem token, senha/hash, segredo, corpo, SQL e stack trace. Não houve falha dessas assertivas. [security-proof.json](../../target/harness-validation/feature-004-t037-2026-10-09-170505/security-proof.json), http-observations.json e service-observations.json contêm grupos e observações sanitizadas.

Os 20 casos da chain passam: 15 seleções de rota/método com context path, três perfis autorizados, uma rejeição de token/perfis e uma preservação do fallback público com Bearer malformado irrelevante. Catálogo, detalhe e login são exercitados por esse controle dentro da classe autorizada.

Não restam falhas nas duas classes executadas. Os 12 casos de guardas tardias receberam o envelope pela tradução de exceções da chain, consequência da ligação dos adaptadores; não houve implementação MVC. T038 continua aberta: o ControllerAdvice de pedidos para leitura/validação, negócio, processamento/gravação/flush/conclusão e seus demais cenários não foi implementado ou validado nesta etapa. GREEN destas classes não comprova toda T038, US2, rollback ou a suíte completa.

### Fechamento restrito

T037 comprovada e marcada somente após os resultados. T001–T037 [X] (37), T038–T055 abertas (18); checklists intactos. Diff incremental comparado com as cópias de entrada; diff total e conteúdo dos dois adaptadores revisados. Whitespace conferido por git diff --check e no-index --check de todos os seis não rastreados, sem diagnósticos; código 1 de no-index representa diferença contra arquivo vazio, não erro de whitespace. Prefixo histórico do quickstart preservado byte a byte; tasks difere da entrada somente de [ ] T037 para [X] T037. Auditoria final de hashes permite diferenças somente em SecurityConfig, quickstart e tasks, com os dois adaptadores como únicos novos arquivos do inventário Git. Resultados em final-preservation.json, review-checks.json e git-final.json nesta captura.

Raiz/branch/HEAD finais mantidos; git status --short: 12 rastreados modificados e seis não rastreados (quatro anteriores e os dois adaptadores), índice vazio. Nenhuma alteração de testes, ControllerAdvice, T038+, U1/U2, stage, commit, push, merge ou troca de branch; nenhuma suíte completa ou CI. Encerramento exclusivamente em T037.


## T038 — tradução dos erros MVC de pedidos — 2026-10-09

Execução exclusivamente de T038, com parada antes de T039. Skill speckit-implement aplicada ao escopo autorizado. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido` e HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` confirmados na entrada. Git inicial: 12 rastreados modificados, seis não rastreados, índice vazio; T001–T037 [X], T038–T055 abertas. O diff total contra HEAD inclui as alterações anteriores e não identifica isoladamente o trabalho de T038.

Pré-requisitos do Spec Kit executados com `-Json -RequireTasks -IncludeTasks`: código 0, FEATURE_DIR absoluto desta feature. Checklist requirements.md conferido somente em leitura: 16/16 marcados, zero abertos. T038/dependências, contrato, handlers, implementação e evidências existentes consultados; constituição e artefatos de planejamento preservados. Ignores existentes suficientes; nenhuma alteração de configuração de ignore. Ausência de `.specify/extensions.yml` confirmada antes e depois, sem hooks de implementação.

### Implementação e limites

- Alterado GlobalExceptionHandler exclusivamente para `POST /orders`, comparando o caminho da aplicação sem context path e devolvendo `request.getRequestURI()` no envelope. `/api/orders` conserva o URI externo. DTO ErrorResponseDTO mantém exatamente status, error, message e path.
- Leitura/desserialização e Bean Validation do DTO: 400 / Bad Request / Invalid order data. Produto ausente: 404 / Not Found / Product not found. Rejeições explícitas de InvalidOrderException: 422 / Unprocessable Entity e mensagem fixa selecionada pelo motivo (Duplicate product in order; Product price is not valid for order; Order value cannot be represented), sem usar a mensagem diagnóstica da exceção.
- Guardas tardias MVC: 401 / Unauthorized / Authentication required or invalid, com WWW-Authenticate: Bearer; 403 / Forbidden / Order registration not permitted. Os handlers existentes de credenciais/processamento conservam suas mensagens fora de pedidos.
- Falhas internas de processamento, resolução no serviço, gravação, flush e conclusão da transação: 500 / Internal Server Error / Order registration could not be completed. O fallback captura Exception somente para a operação de pedidos; fora dela, relança a mesma exceção. ConstraintViolationException proveniente do serviço/persistência é falha interna 500; validação do corpo por @Valid chega como MethodArgumentNotValidException e permanece 400. Nenhuma falha interna foi convertida em rejeição de negócio ou sucesso.
- Sem logging de exceções/corpo/token/SQL no advice. Respostas usam apenas campos e mensagens fixos; testes verificam envelope exato e ausência dos canários diagnósticos na resposta e nos logs capturados da matriz MVC. Sem try/catch no controller, mudanças no serviço, entidades, fronteira transacional ou segurança produtiva. Adaptadores dos filtros de T037 preservados; as 12 guardas tardias já válidas não foram alteradas para fabricar RED.
- OrderControllerTest recebeu 28 casos MVC de exceções (14 por URI, com serviço mockado e filtros desabilitados) e dois casos adicionais de context path para leitura/Bean Validation. Assertions/fixtures anteriores preservadas. Essa matriz prova tradução MVC, sem substituir a integração de JWT/banco.
- GlobalExceptionHandlerRegressionTest recebeu três casos: mensagem pública de produto e URI com/sem context path, e propagação de falha pública inesperada sem transformá-la em erro de pedidos. A preparação passou a importar OrderClockConfig existente para satisfazer o Clock requerido pela segurança. Não foi alterada a configuração produtiva nem a fixture compartilhada de segurança.
- OrderSecurityIntegrationTest e OrderRollbackIntegrationTest ficaram byte a byte intactos. Os testes de rollback existentes já exigiam a resposta 500 exata, ausência de Location/token e leitura JDBC independente após a saída do proxy e antes da limpeza; aproveitados sem enfraquecer assertions.

### Execuções atuais, seriais e restritas

Somente as quatro classes autorizadas foram selecionadas pelo Maven Wrapper, com Java 21.0.12.1 e Maven 3.9.16. Comandos reais, em ordem:

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

Horários locais UTC−03:00. Contagens extraídas exclusivamente dos XMLs Surefire novos de cada execução; suites externas com zero casos não foram contadas como testes adicionais. Todos os comandos tiveram término Maven e exit code capturado imediatamente.

| Etapa / classe | Início → término | Testes | Falhas | Erros | Ignorados | Exit Maven / término |
|---|---|---:|---:|---:|---:|---|
| RED OrderControllerTest, antes do advice | 17:25:33 → 17:26:30 | 137 | 132 | 0 | 0 | 1 / BUILD FAILURE |
| GREEN OrderControllerTest | 17:29:11 → 17:30:08 | 141 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| GREEN OrderSecurityIntegrationTest | 17:30:13 → 17:31:06 | 85 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| GREEN OrderRollbackIntegrationTest | 17:31:20 → 17:31:57 | 9 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| GlobalExceptionHandlerRegressionTest, preparação bloqueada | 17:32:13 → 17:32:34 | 12 | 0 | 12 | 0 | 1 / BUILD FAILURE |
| GREEN GlobalExceptionHandlerRegressionTest, após importar Clock existente | 17:33:45 → 17:34:25 | 12 | 0 | 0 | 0 | 0 / BUILD SUCCESS |

RED comportamental: 106 casos anteriores de entrada rejeitada, dois de context path e 24 novos de tradução MVC falharam por envelope/mensagem ausentes, inadequados ou exceção conhecida escapando do MVC. Zero erros de preparação; cinco controles positivos aprovados (dois de leitura e três de registro). Antes do GREEN foram acrescentadas mais quatro combinações para os tipos de exceção já tratados pelo advice de login, garantindo que a operação de pedidos não herdasse mensagens de login. A diferença 137 → 141 é expansão explícita da cobertura, não desaparecimento de casos.

A primeira execução da regressão pública foi bloqueada por NoSuchBeanDefinitionException de java.time.Clock na inicialização do slice. Os 12 erros não provam comportamento ausente nem aprovação; nenhuma assertion chegou a ser comprovada nessa tentativa. Importada somente OrderClockConfig produtiva existente no próprio teste; repetida exclusivamente a mesma classe. Todos os 12 casos então concluídos com sucesso. Entre as duas capturas de fontes, apenas esse import/preparação mudou; fontes produtivos e demais testes permaneceram idênticos, dispensando repetição das outras classes já aprovadas.

Total das quatro execuções finais GREEN: **247 testes, zero falhas, zero erros, zero ignorados**. Não somar RED ou a tentativa bloqueada como testes aprovados, nem reutilizar reports antigos como prova desta revisão.

### Evidências comprovadas

- OrderControllerTest: 138 casos InputBoundary (106 rejeições anteriores, dois limites inteiros positivos, 28 traduções MVC e dois context paths) e três SuccessfulRegistration. Traduções MVC verificam exatamente os quatro campos, mensagens/status, /orders e /api/orders, challenge dos 401, causa original resolvida e ausência de canários diagnósticos. Incluem os três motivos 422, produto 404, autenticação/permissão e sete tipos internos: IllegalStateException, AuthenticationProcessingException, DataIntegrityViolationException, TransactionSystemException, OrderAccountResolutionException, ConstraintViolationException e IllegalArgumentException. Erros internos permanecem 500.
- OrderSecurityIntegrationTest: 50 rejeições dos filtros preservadas (46 JWT, duas permissões e duas falhas internas de resolução); 12 guardas tardias HTTP; 13 chamadas diretas ao proxy; dez controles positivos com JWT/login/decoder/perfis do banco e persistência reais. Precedência de segurança sobre corpo inválido, identidade e /api/orders continuam comprovados. Nenhuma assertion, token fixture ou chain foi alterada nesta etapa.
- OrderRollbackIntegrationTest: três falhas reais/injetadas após SQL efetivo, cinco rejeições integrais e um controle positivo de commit, todos aprovados. No erro SQL tardio, dois INSERTs concluídos e o terceiro falhou: HTTP 500, baseline de zero pedidos/itens restaurado. Após saveAndFlush real, três INSERTs concluídos e linhas visíveis na conexão transacional antes da exceção: HTTP 500, baseline restaurado. Na conclusão pelo interceptor, DTO produzido com total 42.50, callback beforeCommit depois do corpo, três INSERTs concluídos/um pedido/dois itens visíveis na mesma conexão, proxy sem retornar sucesso: HTTP 500, baseline restaurado. Leitura independente fora da transação da operação, antes da limpeza, para cada falha; não se usa rollback automático do teste como evidência.
- GlobalExceptionHandlerRegressionTest: nove casos anteriores de validação pública/detalhes/login e três casos novos aprovados, mantendo mensagens públicas, mensagens genéricas próprias do login e context path; exceção pública inesperada conserva a causa original.

Captura desta etapa: `target/harness-validation/feature-004-t038-2026-10-09-172320/`. [Resultados do controller](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderControllerTest-result.json), [segurança](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderSecurityIntegrationTest-result.json), [rollback](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderRollbackIntegrationTest-result.json) e [regressão final](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-retry-GlobalExceptionHandlerRegressionTest-result.json). Cada resultado tem comando, timestamps, exit code, classes, contagens e término; consoles e XML/TXT estão nos diretórios correspondentes. [RED atual](../../target/harness-validation/feature-004-t038-2026-10-09-172320/red-OrderControllerTest-result.json) e [preparação bloqueada](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-GlobalExceptionHandlerRegressionTest-result.json) preservados separadamente. [Observações independentes de rollback](../../target/harness-validation/feature-004-t038-2026-10-09-172320/rollback-observations.txt), [contagens dos casos de segurança](../../target/harness-validation/feature-004-t038-2026-10-09-172320/security-case-counts.json) e [transição dos fontes executados](../../target/harness-validation/feature-004-t038-2026-10-09-172320/validation-source-transition.json).

Limitações: matriz MVC com serviço mockado prova tradução, não atomicidade; atomicidade demonstrada separadamente em H2 pelas três falhas e controle positivo existentes. Não testada falha de transmissão após commit nem crash de processo. O console da falha SQL sintética contém avisos do ORM sobre a constraint; isso não é vazamento no envelope HTTP nem aprovação global de sanitização de todos os logs da aplicação. Nenhum corpo/token/SQL/stack foi adicionado às respostas ou ao logging dos handlers. Não executadas outras classes, suíte completa, CI ou matriz integral das histórias posteriores. T039 e tarefas seguintes continuam abertas; não se declara a feature inteira concluída.

### Preservação e fechamento de T038

Capturados antes de editar 4204 arquivos: 156 do inventário Git e 4048 evidências históricas de target/harness-validation, além das cópias de entrada dos cinco arquivos envolvidos e reports-before. Relatórios Surefire anteriores sobrescritos pelo Maven continuam nessa cópia e nas capturas históricas. Duas cópias dos 83 fontes executados conservam a preparação anterior/final da regressão. Uma comparação auxiliar de transição inicialmente encapsulou o array JSON no PowerShell e abortou sem comprovar os arquivos; corrigida a leitura, confirmou 83 fontes e apenas a preparação da regressão alterada. Esse impedimento administrativo não foi contado como verificação aprovada nem exigiu mudança na aplicação.

Auditoria final SHA-256 contra o manifesto de entrada permite diferenças somente em GlobalExceptionHandler.java, OrderControllerTest.java, GlobalExceptionHandlerRegressionTest.java, quickstart.md e tasks.md. Todos os outros fontes, os seis não rastreados anteriores e as 4048 evidências históricas preservados. Prefixo histórico do quickstart conservado byte a byte; tasks difere exclusivamente na checkbox T038. [Auditoria final](../../target/harness-validation/feature-004-t038-2026-10-09-172320/final-preservation.json).

Diff incremental dos cinco arquivos revisado, sem remoção/enfraquecimento de assertions. `git -c core.safecrlf=false diff --check`: código 0, sem diagnósticos de whitespace. Os seis arquivos não rastreados revisados também com `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: diferenças de arquivo novo não significam whitespace inválido; zero diagnósticos. [Conferências de revisão/Git](../../target/harness-validation/feature-004-t038-2026-10-09-172320/review-checks.json).

Marcada somente T038 com escopo comprovado. T001–T038 [X] (38); T039–T055 abertas (17), 55 IDs únicos. Próximo passo, somente após autorização: ler a condição de T039 e avaliar as evidências já disponíveis, sem criar mudanças artificiais para reproduzir falha. Nesta etapa T039 não foi executada nem marcada.

Raiz/branch/HEAD finais iguais aos de entrada; `git status --short` final: 14 rastreados modificados, os mesmos seis não rastreados, índice vazio. Alterações próprias de T038 limitadas aos cinco arquivos citados; demais alterações são anteriores. HEAD final `0344dc0199fd8135f98e852e7d6fedc06b124d9a`, branch `004-registrar-pedido`. Sem T039+, U1/U2, stage, commit, push, merge, troca de branch, suíte completa ou CI.


## T039 — decisão sobre a condição de correção da atomicidade — 2026-10-09

**Correção dispensada por condição: atomicidade já comprovada**.

Executada exclusivamente a avaliação de T039, com parada antes de T040. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` confirmados. Entrada: 14 rastreados modificados, seis não rastreados, índice vazio; T001–T038 [X], T039–T055 abertas. Skill speckit-implement aplicada ao escopo autorizado; pré-requisitos PowerShell `-Json -RequireTasks -IncludeTasks` concluídos com código 0 e FEATURE_DIR correto; checklist requirements.md 16/16 marcados, zero abertos, somente leitura. Ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks.

### Condição, código e provas existentes

T039 exige correção somente se T029–T031 demonstrarem falha de atomicidade. Os registros históricos desses checkpoints distinguiam rollback já comprovado de RED do envelope HTTP. A execução final de T038 demonstrou também as respostas contratuais; não há lacuna ou contradição que justifique alteração produtiva.

Conferidos em leitura:

- OrderController chama o método público OrderService.register pelo bean Spring; só devolve 201 depois do retorno do proxy, sem try/catch.
- Register usa `@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)`. Revalidação de conta/perfis, leituras de produtos, snapshot dos preços em OrderItem, cálculos, construção do agregado, saveAndFlush e mapping do DTO estão dentro do mesmo método/fronteira. Não há REQUIRES_NEW ou gravação independente de itens nesse fluxo.
- Order agrega os itens com CascadeType.PERSIST; OrderRepository é JpaRepository real. Os testes exigem AopUtils.isAopProxy e, na sonda de conclusão, exatamente um TransactionInterceptor Spring; o spy de repositório encaminha para o proxy/JPA reais, sem stubbing de save.
- Falhas SQL, pós-flush e beforeCommit propagam até MVC; o advice de T038 produz 500 / Internal Server Error / Order registration could not be completed, com exatamente status, error, message e path. O resultado preserva a exceção original e exige ausência de 201/Location/token.
- CHECK temporário, PostFlushProbe/PostFlushTestFailure e CompletionProbe/BeforeCommitTestFailure existem exclusivamente em OrderRollbackIntegrationTest. Nenhuma sonda/ponto de falha produtivo. Os testes não usam @Transactional externo nem rollback automático para comprovar o serviço.

| Prova distinta | Ponto efetivamente observado em T038 | HTTP e leitura independente depois da saída do proxy, antes da limpeza |
|---|---|---|
| T029 — SQL tardio | Pedido e primeiro item inseridos: dois INSERTs concluídos. Segundo item falha no CHECK exclusivo, SQLState 23513; terceiro INSERT falho. Observação na conexão transacional com autoCommit=false. | 500 contratual; snapshot completo baseline 0 pedidos/0 itens → 0/0, igualdade integral; constraint ainda instalada nas verificações. |
| T030 — após saveAndFlush | Implementação JPA real retorna; três INSERTs concluídos e um pedido/dois itens visíveis na conexão vinculada. Sonda lança a mesma PostFlushTestFailure enquanto o corpo de register aguarda a chamada de repositório, antes do DTO/conclusão. Sem callback de T031 armado. | 500 contratual; snapshot completo baseline 0/0 → 0/0, em finally antes do desarme/cleanup. |
| T031 — beforeCommit após corpo | Corpo real produz DTO total 42.50 com transação ativa. Depois, callback vê a mesma instância e um pedido/dois itens, três INSERTs concluídos, conexão vinculada e autoCommit=false. Ordem SYNC_REGISTERED → DTO_PRODUCED → BEFORE_COMMIT → ROWS_OBSERVED → CALLBACK_EXCEPTION; proxyReturned=false e interceptorCompletion=true. | 500 contratual; mesma BeforeCommitTestFailure propagada; snapshot completo baseline 0/0 → 0/0, em finally antes de remover observadores/cleanup. |

**A falha de T031 ocorre no callback beforeCommit, anterior ao commit físico**, durante a conclusão transacional pelo interceptor, após o corpo produzir o DTO. Não foi simulada/comprovada falha física do commit, de conexão durante commit ou de transmissão após commit.

Leitura posterior usa JdbcTemplate com DriverManagerDataSource independente, e snapshot exige transação inativa. Nos três casos, ocorre depois da saída síncrona do proxy/MockMvc e antes de @AfterEach/limpeza herdada; não se atribui o rollback ao teardown. Controle positivo `shouldCommitCompleteOrderAndItemsVisibleAfterHttp201`: 201, confirmação exata total 42.50, três INSERTs concluídos e leitura independente com **+1 pedido/+2 itens** completos e vínculos/posições/preços corretos, antes da limpeza. Assim, sucesso realmente grava e falha realmente desfaz inserções anteriores.

### Execução histórica utilizada, sem repetição

Execução final de OrderRollbackIntegrationTest em T038, **2026-10-09 17:31:20.054 → 17:31:57.975 UTC−03:00**:

```powershell
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

**9 testes, zero falhas, zero erros, zero ignorados; exit Maven 0 e BUILD SUCCESS**. XML/TXT e console conferem os três métodos de falha, o controle positivo e cinco rejeições integrais. As nove aprovações são de T038, não execução nova de T039. Nenhum teste foi repetido nesta etapa.

Evidências consultadas: checkpoints T029/T030/T031 e fechamento T038 acima; [resultado completo de T038](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderRollbackIntegrationTest-result.json), [XML com os nove casos](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderRollbackIntegrationTest-reports/TEST-com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.xml), [console com controle positivo e término](../../target/harness-validation/feature-004-t038-2026-10-09-172320/green-OrderRollbackIntegrationTest-console.txt) e [observações das falhas/leituras independentes](../../target/harness-validation/feature-004-t038-2026-10-09-172320/rollback-observations.txt). Hashes atuais dos 83 fontes coincidem com a captura final de fontes de T038; produção e teste de rollback correspondem à revisão efetivamente executada. Não usar relatórios antigos/parciais ou o RED histórico do envelope como aprovação completa ou falha de atomicidade.

### Alterações documentais e estado final

Alterados somente quickstart.md por acréscimo e a checkbox T039 de tasks.md. Conferência SHA-256 de entrada/saída dos **4555 arquivos fora desses dois documentos**: 154 arquivos do inventário Git, 4335 evidências históricas em target/harness-validation e 66 reports Surefire; conteúdo e inventário preservados. SHA-256 agregado ordenado (caminho + hash do arquivo): `3A1CABD6D802E915D855384A155D52690572A8A61E208F5FCDC55FDAD2AF674B`. Prefixo histórico de quickstart de 436204 bytes conservado; tasks difere exclusivamente de [ ] T039 para [X] T039. Nenhum arquivo de produção/teste ou evidência anterior modificado; nenhum arquivo novo criado.

Diff incremental documental revisado. `git -c core.safecrlf=false diff --check` concluído com código 0 e sem diagnósticos de whitespace. Os seis não rastreados anteriores também conferidos com no-index --check, sem diagnósticos; nenhuma alteração neles. Links das quatro evidências conferidos.

T001–T039 [X] (39), **T040–T055 abertas (16)**; somente T039 marcada nesta etapa. Raiz/branch/HEAD mantidos. Git final: mesmos 14 rastreados modificados e seis não rastreados, índice vazio; o diff contra HEAD ainda inclui trabalho anterior. Sem T040+, U1/U2, testes, suíte completa, CI, stage, commit, push, merge ou troca de branch. Próxima tarefa é T040, não executada.


## T040 — execução serial de US2 e conferência da cobertura — 2026-10-09

**Seis classes GREEN; T040 permanece aberta por lacuna de cobertura no complemento de precedência.** Nenhuma alteração de produção ou testes foi feita para ampliar o escopo ou fabricar aprovação. Parada antes de T041.

Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido` e HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` confirmados inicialmente e no fechamento. Entrada: 14 rastreados modificados, seis não rastreados, índice vazio; T001–T039 [X], T040–T055 abertas. O diff contra HEAD inclui trabalho anterior e não representa somente esta etapa.

Skill speckit-implement aplicada exclusivamente a T040. Pré-requisitos PowerShell `-Json -RequireTasks -IncludeTasks` concluídos com código 0 e FEATURE_DIR absoluto correto; requirements.md 16/16 marcados, zero abertos, somente leitura. T040/dependência T039, US2.1–13, complementos do quickstart, contrato, fontes e decisões pertinentes de plano/pesquisa/modelo/constituição conferidos. Ignores existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks de implementação.

### Comandos, horários e resultados atuais

Somente as seis classes autorizadas, uma execução por classe, serialmente e na ordem abaixo. Nenhuma repetição, alvo extra, suíte completa ou CI. Maven Wrapper configurado para 3.9.16; XMLs atuais registram Java 21.0.12.1. Preservadas as configurações existentes: perfil test, H2 `order-test-<UUID>` isolado, open-in-view=false nos contextos integrados, Clock/fixtures existentes. Consoles comprovam cinco URLs H2 distintas (controller integrado, segurança, rollback e dois repositórios); unidade não usa banco e slice MVC não usa JPA. [Prova de isolamento](../../target/harness-validation/feature-004-t040-2026-10-09-175211/isolation-proof.json).

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=UserAccountRoleRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

2026-10-09, horários UTC−03:00. Exit codes Maven capturados imediatamente; todos os comandos têm término BUILD SUCCESS e relatórios novos. A soma usa exclusivamente os XMLs da classe selecionada e seus nested tests produzidos naquela execução. Relatórios antigos não foram somados; a suite externa de OrderControllerTest com zero casos não acrescenta testes.

| Classe | Início → término | Testes | Falhas | Erros | Ignorados | Exit Maven |
|---|---|---:|---:|---:|---:|---:|
| OrderControllerTest | 17:52:41.906 → 17:53:20.795 | 141 | 0 | 0 | 0 | 0 |
| OrderSecurityIntegrationTest | 17:53:39.007 → 17:54:30.807 | 85 | 0 | 0 | 0 | 0 |
| OrderServiceTest | 17:55:18.625 → 17:55:35.984 | 42 | 0 | 0 | 0 | 0 |
| OrderRollbackIntegrationTest | 17:55:56.824 → 17:56:33.431 | 9 | 0 | 0 | 0 | 0 |
| OrderRepositoryTest | 17:56:51.714 → 17:57:24.329 | 23 | 0 | 0 | 0 | 0 |
| UserAccountRoleRepositoryTest | 17:57:34.034 → 17:58:06.220 | 11 | 0 | 0 | 0 | 0 |
| **Total atual** | **seis execuções sem sobreposição** | **311** | **0** | **0** | **0** | **todos 0** |

[Resumo das seis execuções](../../target/harness-validation/feature-004-t040-2026-10-09-175211/results-summary.json). Na captura `target/harness-validation/feature-004-t040-2026-10-09-175211/`, cada `<Classe>-result.json` contém comando, início/término, exit code, classes, contagens e término Maven; `<Classe>-console.txt` e `<Classe>-reports/` conservam console e XML/TXT daquela execução. [Contagens por método/caso](../../target/harness-validation/feature-004-t040-2026-10-09-175211/case-counts.json). Não há falha de compilação, contexto, preparação ou comportamento nessas seis execuções; isso não elimina a lacuna de cobertura abaixo.

### Cobertura da matriz US2.1–13 por camada

| Cenário | Evidência atual e limite |
|---|---|
| US2.1 — estruturas ausentes/nulas/vazias/inválidas | InputBoundary envia JSON bruto e exige 400 exato e zero interações com serviço. Slice MVC, filtros desabilitados/serviço mockado; não prova JWT/banco. |
| US2.2 — quantidades e tipos/limites inteiros | Slice verifica null/zero/negativo/overflow e tokens decimal/expoente/string/bool/array/objeto, sem coerção; dois controles positivos leem Long.MAX_VALUE/Integer.MAX_VALUE exatamente. O 201 desses dois controles é do mock, não compra real desses IDs. |
| US2.3 — produto inexistente entre válidos | Unidade rejeita sem save; rollback integrado retorna 404 exato, zero INSERTs e snapshot independente igual ao baseline. |
| US2.4 — autenticação/token inválido | 46 rejeições HTTP com decoder/filtros reais, JWTs assinados/adulterados ou malformados, sem escrita/chamada ao serviço. Combinações com corpo ilegível estão comprovadas; itens inválidos em JSON bem formado não estão. |
| US2.5 — subject/conta | Subject ausente/inválido/numérico/não canônico/fora Long e conta ausente; guardas tardias e chamadas diretas ao proxy verificam identidade atual. Falha interna de resolução distingue 500 de 401. |
| US2.6 — permissões | Perfis reais do banco, CLIENT/ADMIN/ambos, conta sem perfis mesmo com claim ADMIN; 403 e revogação tardia preservados. Guardas tardias usam serviço/proxy/transação reais. |
| US2.7 — dados determinados pelo sistema | 40 casos de extras/titular/preços/total/id/instante/estado no root/linhas, inclusive nulos; 13 casos de chaves repetidas. 400 antes do serviço; prova slice. |
| US2.8 — produto repetido | Unidade exige rejeição sem consolidação/save; integração retorna 422 exato e baseline independente intacto, nenhum INSERT. |
| US2.9 — falhas internas/atomicidade | Três falhas distintas após SQL real, todas com 500 contratual e rollback integral; controle positivo de commit separado, descrito abaixo. |
| US2.10 — precisão/capacidade | Unidade: normalização exata, cinco combinações de mínimo/teto/quantity máxima, soma no teto, seis rejeições de fração/preço/subtotal e overflow de soma. Controller integrado: capacidade decimal como texto e duas casas; JDBC: decimal19,2 e limites de quantity/preço. Tradução MVC de motivo 422 também coberta com mock. Não atribuir a unidade/JDBC raw uma execução HTTP real de todas as combinações. |
| US2.11 — preço zero | Unidade e integração 422 entre positivos; zero gravações e leitura independente sem linhas novas. |
| US2.12 — preço negativo | Unidade e integração 422; fixture JDBC negativa isolada, sem alterar Product/@PositiveOrZero/schema; baseline intacto. Preço null/indisponível usa unidade, não foi persistido contra NOT NULL de Product. |
| US2.13 — quantity zero entre válidos | Slice 400 antes do serviço; integração HTTP com JWT real retorna 400, sem INSERTs e com baseline independente preservado. |

OrderControllerTest: 138 InputBoundary (106 entradas rejeitadas, dois controles de leitura, 28 traduções MVC de exceções e dois context paths) + três SuccessfulRegistration com Spring/HTTP/JWT reais. Segurança: 50 rejeições dos filtros, 12 guardas tardias HTTP, 13 chamadas diretas ao proxy e dez controles positivos. Unidade: 42 casos de leitura, regra, aritmética, snapshot e guardas com construção direta/mocks, sem comprovar transação, commit ou rollback de operação.

### Precedência, constraints, IDs e rollback

Segurança → leitura: filtros reais aprovam precedência de 401/403/500 sobre **JSON ilegível**, com zero chamadas ao serviço de pedidos/novas linhas. Leitura → negócio: os casos de leitura/Bean Validation no slice impedem qualquer chamada ao serviço, portanto não consultam produtos nem gravam. Negócio → persistência: rejeições unitárias exigem zero save e as cinco rejeições integradas mostram zero INSERTs/baseline preservado. Guardas tardias são adicionais dentro da transação; continuam válidas após a autenticação inicial.

Contrato não promete prioridade entre múltiplos erros de negócio 404/422; os casos isolam a causa que pretendem provar e não acrescentam uma assertion ou promessa de ordem entre causas simultâneas. A ordem observada no algoritmo não é uma nova garantia pública.

OrderRepositoryTest: dois casos de metadata, round-trip JPA/JDBC do agregado, IDs positivos/distintos, oito NOT NULL, três FKs, duas unicidades por pedido (produto e position), cinco CHECKs nomeados e um controle de limites JDBC. Rejeições SQL chegam como SQLException/SQLState verificado, não validação Bean anterior ao driver. Decimal19,2, centavo mínimo, teto 99999999999999999.99, quantity Integer.MAX_VALUE e position zero conferidos. Inserts raw provam constraints/capacidade das colunas, não aceitação de um agregado completo pelas regras do serviço. IDs de pedidos/itens são distintos e positivos, sem exigir IDs consecutivos ou reutilização após rollback.

O round-trip JPA do repositório usa TransactionTemplate com rollbackOnly para testar mapping/flush/reload; **não é prova de atomicidade da operação**. UserAccountRoleRepositoryTest: 11 casos de metadata/FK/NOT NULL/unicidade, conta ausente/sem perfis e quatro conjuntos de perfis, com lookup já carregado e leitura detached; SQL direto verifica as constraints. Contextos/repositórios não substituem o proxy de registro nas provas de rollback.

Rollback atual, sem @Transactional externo de teste, com fixtures confirmadas e JDBC independente após saída do proxy, antes de limpeza:

| Falha | Observação anterior à falha | Resposta e leitura posterior |
|---|---|---|
| SQL tardio T029 | Pedido + primeiro item: dois INSERTs concluídos; segundo item falha no CHECK, terceiro INSERT falho. | 500 exato, baseline 0/0 → 0/0; constraint ainda instalada nas verificações. |
| Pós-flush T030, corpo ainda não concluído | saveAndFlush real retorna; três INSERTs, um pedido/dois itens visíveis na conexão vinculada, autoCommit=false; PostFlushTestFailure lançada pela sonda. | 500 exato, mesma exceção e baseline 0/0 → 0/0 em finally antes do desarme/cleanup. |
| beforeCommit T031, depois do corpo | DTO 42.50 produzido; callback/interceptor alcançados depois dele, três INSERTs, um pedido/dois itens visíveis, mesma conexão/transação; proxyReturned=false. | 500 exato, mesma BeforeCommitTestFailure e baseline 0/0 → 0/0 em finally antes da remoção/cleanup. |

Controle positivo: 201, três INSERTs concluídos, +1 pedido/+2 itens completos visíveis em conexão independente após retorno, antes da limpeza. Flush não é commit; beforeCommit é falha no callback anterior ao commit físico, não falha física do commit/conexão. [Observações atuais](../../target/harness-validation/feature-004-t040-2026-10-09-175211/rollback-observations.txt).

### Lacuna impeditiva e demais limites

O complemento do quickstart pede repetir a precedência com **JSON ilegível e itens inválidos**. Em OrderSecurityIntegrationTest, `INVALID_BODY` na linha 95 é somente `{"T027_BODY_CANARY":`, JSON ilegível. As linhas 210/238/264 selecionam esse texto ou `validBody`; este contém itens válidos/quantity 2 (linha 565). Assim, as 46 rejeições JWT, duas de perfis e duas de resolução interna não exercitam **JSON bem formado com itens inválidos**. Os demais testes selecionados não suprem essa combinação: InputBoundary desabilita filtros e mocka serviço; o 400 integrado de quantity zero usa conta autorizada, sem falha de segurança concorrente.

Essa lacuna não demonstra defeito produtivo ou falha dos 311 testes. Impede declarar comprovada a matriz completa de T040. **T040 fica [ ]**, preservando todas as checkboxes anteriores. Para fechar, será necessária autorização específica para complementar os casos integrados com itens inválidos em JSON bem formado e autenticação ausente/inválida, ausência de perfis e falha interna de resolução; exigir 401/403/500 contratuais antes de 400, challenge do 401, zero serviço/escrita, incluindo context path. Nenhum desses casos foi acrescentado/executado nesta etapa.

Complementos de snapshot e novo agregado em envios iguais têm prova unitária atual; IDs distintos têm prova JDBC atual. As provas HTTP/banco de alteração de preço após compra e reenvio com confirmação recebida/descartada estão em OrderRegistrationIntegrationTest e evidências históricas de US1: essa classe não foi executada agora, e resultados históricos não aprovam automaticamente a revisão atual. Não declarada nova cobertura integrada desses complementos. Regressões públicas/login/handlers fora da seleção, transporte perdido após commit, falha física de commit, suíte completa e CI não foram executados. Consoles podem conter avisos JVM/ORM da preparação/constraint sintética; o envelope HTTP permanece seguro. [Revisão de cobertura e limites](../../target/harness-validation/feature-004-t040-2026-10-09-175211/coverage-review.json).

### Preservação, diff e Git final

Captura de entrada de 4491 arquivos: 156 do inventário Git e 4335 evidências históricas. Quickstart/tasks copiados antes de editar, 66 reports Surefire anteriores arquivados em reports-before antes de substituição pelo Maven, 83 fontes executados copiados/hashados antes das seis execuções. Relatórios atuais separados por classe. Nenhuma evidência histórica foi reescrita ou tratada como execução atual.

Auditoria SHA-256 final contra entrada permite diferença **somente em quickstart.md**, por acréscimo. Todos os 83 fontes, demais arquivos do inventário e 4335 evidências históricas preservados; inventário Git igual. Prefixo histórico de quickstart de 444130 bytes intacto; tasks.md byte a byte igual à entrada. [Auditoria final](../../target/harness-validation/feature-004-t040-2026-10-09-175211/final-preservation.json).

Diff incremental documental revisado; `git -c core.safecrlf=false diff --check`: código 0, sem diagnósticos de whitespace. Os seis não rastreados anteriores também conferidos com no-index --check, sem diagnósticos; nenhum modificado. Links de evidências conferidos. [Conferências finais](../../target/harness-validation/feature-004-t040-2026-10-09-175211/review-checks.json).

**T001–T039 [X] (39); T040–T055 abertas (16). Nenhuma checkbox alterada.** Raiz/branch/HEAD mantidos, Git final com os mesmos 14 rastreados modificados e seis não rastreados, índice vazio. Alterado nesta etapa somente quickstart.md, além da nova captura ignorada de evidências. Sem produção/testes, T041+, U1/U2, outras classes, suíte completa, CI, stage, commit, push, merge ou troca de branch. Encerramento da execução autorizada com T040 ainda pendente por cobertura.


## T040 — fechamento exclusivo da lacuna de precedência — 2026-10-09

**Lacuna fechada, execução GREEN e T040 [X]. Parada antes de T041.** Este complemento atualiza a decisão histórica imediatamente acima, sem apagar a execução anterior de 311 testes ou seus limites. Não houve alteração de produção.

Entrada confirmada: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a`; 14 rastreados modificados, seis não rastreados, índice vazio. T001–T039 [X], T040–T055 abertas na entrada. Skill speckit-implement aplicada somente à lacuna autorizada; pré-requisitos concluídos com código 0, feature correta e checklist requirements.md 16/16, somente leitura. T040, contrato e matriz anterior conferidos. Não executadas tarefas posteriores.

### Ampliação integrada e preservação dos casos anteriores

Alterada somente `src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java` entre os fontes. Preservados contexto Spring completo, filtros habilitados, perfil test, banco isolado, login/encoder/decoder reais, assinatura/adulteração de JWT e sondas anteriores. Não usada autenticação simulada. Os dois corpos novos são enviados como JSON bruto bem formado: `{"items":[]}` e `{"items":[{"productId":<ID existente da fixture>,"quantity":0}]}`. O helper verifica a forma do JSON e, para quantity zero, confirma por JDBC independente que o produto existe.

A matriz original tinha 23 cenários de token × duas combinações corpo/path, mais duas rejeições de perfis e duas de resolução: 50 casos de filtros. Essas combinações permanecem: corpo válido em `/orders` e corpo ilegível em `/api/orders`. Acrescentados dois corpos inválidos × dois paths × (23 cenários de token + conta sem perfil + falha interna de resolução), **100 novos casos**. Cada corpo/contexto cobre ausência de token e todas as variantes já existentes: formato/assinatura/chave/algoritmo, claims temporais/ausentes/inconsistentes, subject inválido/numérico e conta inexistente. Conta sem perfil mantém claim ADMIN forjado e perfis reais do banco; falha de lookup mantém a instrumentação exclusiva de teste no repositório real e exige uma chamada ao lookup.

| Casos novos | `/orders` | `/api/orders` | Total | Resultado esperado e observado |
|---|---:|---:|---:|---|
| items vazio + ausência/variantes inválidas de token | 23 | 23 | 46 | 401 |
| quantity zero + ausência/variantes inválidas de token | 23 | 23 | 46 | 401 |
| items vazio + conta sem perfil / falha interna | 2 | 2 | 4 | dois 403, dois 500 |
| quantity zero + conta sem perfil / falha interna | 2 | 2 | 4 | dois 403, dois 500 |
| **Total acrescentado** | **50** | **50** | **100** | **92 × 401, 4 × 403, 4 × 500** |

Todos usam as assertions anteriores de HTTP: JSON exato com somente status/error/message/path, `401 / Unauthorized / Authentication required or invalid`, `403 / Forbidden / Order registration not permitted` ou `500 / Internal Server Error / Order registration could not be completed`; `WWW-Authenticate: Bearer` no 401 e path externo exato, incluindo `/api`. Comparação integral dos counts de pedidos/itens pela conexão independente depois da resposta síncrona, antes da limpeza, com transação de teste inativa. Acrescentada assertion explícita de **zero chamadas a OrderService** nos três grupos de rejeição pelos filtros, inclusive nas combinações antigas. As assertions de confidencialidade do corpo/headers/logs continuam presentes. Falha interna permanece 500, não 401/400/erro de negócio.

Os 35 outros casos permanecem: dez positivos, 12 guardas tardias HTTP e 13 guardas diretas pelo proxy. Guardas tardias conservam a passagem válida pela chain e a chamada real ao serviço; não lhes foi imposta a assertion de zero chamadas dos filtros. Helpers/assertions anteriores, positivos, guardas e fixtures de JWT conferidos sem alteração textual. [Preservação das assertions](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/assertion-preservation.json), [diff incremental do teste](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/test-incremental.diff) e [matriz observada, casos e contagens](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/coverage-completion.json).

### Única execução nesta etapa

```powershell
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

**2026-10-09 18:14:57.643 → 18:16:31.530 UTC−03:00**. Maven Wrapper 3.9.16, mesmas configurações de isolamento; execução serial, um fork. **185 testes, zero falhas, zero erros, zero ignorados; exit Maven 0, BUILD SUCCESS**, um XML novo da classe selecionada. Não houve repetição das outras cinco classes. Comando, horários, exit code capturado imediatamente e término: [resultado atual](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/OrderSecurityIntegrationTest-result.json); [XML atual](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/OrderSecurityIntegrationTest-reports/TEST-com.devsuperior.dscommerce.controllers.OrderSecurityIntegrationTest.xml); [console completo](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/OrderSecurityIntegrationTest-console.txt). Os reports anteriores foram arquivados em reports-before antes da execução; não foram usados como resultado novo.

### Consolidação de T040 sem contagem repetida

Usada a última execução relevante de cada classe. Os 83 fontes foram capturados antes da execução; comparação com a captura dos 311 testes identifica diferença somente em OrderSecurityIntegrationTest. Produção, suporte/fixtures e outras cinco classes permanecem idênticos, justificando utilizar suas aprovações anteriores sem repetição. [Comparação de fontes](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/source-comparison.json).

| Classe | Execução selecionada, 2026-10-09 UTC−03:00 | Testes | Falhas / erros / ignorados | Exit Maven |
|---|---|---:|---|---:|
| OrderControllerTest | histórica T040, 17:52:41.906 → 17:53:20.795 | 141 | 0 / 0 / 0 | 0 |
| OrderSecurityIntegrationTest | atual, 18:14:57.643 → 18:16:31.530 | 185 | 0 / 0 / 0 | 0 |
| OrderServiceTest | histórica T040, 17:55:18.625 → 17:55:35.984 | 42 | 0 / 0 / 0 | 0 |
| OrderRollbackIntegrationTest | histórica T040, 17:55:56.824 → 17:56:33.431 | 9 | 0 / 0 / 0 | 0 |
| OrderRepositoryTest | histórica T040, 17:56:51.714 → 17:57:24.329 | 23 | 0 / 0 / 0 | 0 |
| UserAccountRoleRepositoryTest | histórica T040, 17:57:34.034 → 17:58:06.220 | 11 | 0 / 0 / 0 | 0 |
| **Total consolidado** | **uma execução relevante por classe** | **411** | **0 / 0 / 0** | **todos 0** |

**311 − 85 + 185 = 411 casos consolidados**, com 100 novos; não somar novamente os 85 casos de segurança nem apresentar as cinco execuções históricas como executadas agora. [Consolidação com comandos, horários e resultados selecionados](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/consolidated-results.json).

A matriz US2.1–13 e os complementos previstos em T040 mantêm as evidências por camada descritas na seção anterior; o complemento de segurança com itens inválidos está agora comprovado pela integração HTTP/MockMvc com JWT real, filtros e banco. O slice MVC continua distinto da unidade e da integração/persistência. A precedência segurança → leitura → negócio → persistência está coberta; não se promete prioridade entre múltiplos erros de negócio. Mantidas provas de limites monetários, constraints JDBC e IDs únicos sem exigir sequência contínua. Atomicidade continua comprovada pelas três falhas distintas e controle positivo de commit em OrderRollbackIntegrationTest, com leitura independente após saída do proxy e antes da limpeza, sem rollback automático de teste. beforeCommit comprova falha na conclusão pelo interceptor anterior ao commit físico; não comprova falha física do commit.

Mantidos os limites históricos: snapshot/reenvio são unitários nesta seleção; provas HTTP/banco de preço alterado/reenvio estão fora dessas seis classes e não foram novamente executadas. Regressões de login/catálogo/detalhes, retomada de positivos US1 e revisão adicional pertencem a T041 ou tarefas posteriores; não há nova aprovação delas, de transporte perdido após commit, suíte completa ou CI. Nenhuma falha comportamental observada; nenhuma correção produtiva necessária para fechar esta lacuna.

### Preservação, revisão e encerramento

Entrada capturada em 4685 arquivos: 156 do inventário Git e 4529 evidências históricas. Arquivados os 66 reports Surefire de entrada; copiados/hashados os 83 fontes executados. Preservadas todas as evidências anteriores, incluindo os 311 testes GREEN. Somente três arquivos do inventário podem diferir: teste de segurança, quickstart por acréscimo e tasks somente [ ] → [X] T040. Prefixo histórico de quickstart de 459487 bytes intacto. [Auditoria final de preservação](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/final-preservation.json).

Diff incremental dos três arquivos revisado; `git -c core.safecrlf=false diff --check` e whitespace dos seis não rastreados anteriores conferidos, sem diagnósticos. Conteúdo desses seis arquivos preservado; links das novas evidências conferidos. [Conferências finais](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/review-checks.json).

**T001–T040 [X] (40); T041–T055 abertas (15). Somente T040 marcada nesta etapa.** Raiz/branch/HEAD mantidos; Git final com os mesmos 14 rastreados modificados e seis não rastreados, índice vazio. Alterados nesta etapa somente OrderSecurityIntegrationTest.java, quickstart.md e tasks.md, além da captura ignorada de evidências. Sem T041+, U1/U2, outras classes, suíte completa, CI, stage, commit, push, merge ou troca de branch.


## T041 — revisão e checkpoint US2 com repetição de US1/regressões — 2026-10-09

**T041 permanece [ ]: a regressão LoginControllerTest não inicializou o contexto.** A rastreabilidade de US2 foi conferida, os positivos US1 passaram e seis das sete execuções foram GREEN; isso não comprova o checkpoint completo. Nenhuma produção, teste, fixture ou assertion foi alterada. Parada antes de T042.

### Base, escopo e gate histórico de T040

Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a` e status: 14 rastreados modificados, seis não rastreados, índice vazio. Entrada T001–T040 [X], T041–T055 abertas. Skill speckit-implement aplicada exclusivamente a T041; check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks concluído com código 0/FEATURE_DIR correto; checklist requirements.md 16/16, zero abertos, somente leitura. Ignores existentes suficientes; extensão de hooks ausente antes/depois. Conferidos T041/dependência T040, spec, contrato README/OpenAPI, plano, pesquisa, modelo, constituição e complementos do quickstart.

Usados os **411 testes consolidados de T040**, uma última execução relevante por classe: OrderControllerTest 141, OrderSecurityIntegrationTest 185, OrderServiceTest 42, OrderRollbackIntegrationTest 9, OrderRepositoryTest 23 e UserAccountRoleRepositoryTest 11. Resultados, exit codes, XMLs e contagens conferem. Incluído o complemento de segurança de 100 combinações: JSON bem formado com items vazio/quantity zero, 92 respostas 401, quatro 403 e quatro 500, dois paths, envelope/challenge exatos e zero serviço/escrita. Não somados novamente os 85 casos antigos de segurança. [Gate das seis classes e reports selecionados](../../target/harness-validation/feature-004-t041-2026-10-09-182300/t040-gate.json).

Todos os 83 fontes continuam idênticos por SHA-256 à captura da execução final de T040. Nenhuma mudança ou preocupação observada nos alvos de T040 justificou repetir suas seis classes. As aprovações são **históricas consultadas nesta etapa**, não testes executados novamente. [Comparação de fontes](../../target/harness-validation/feature-004-t041-2026-10-09-182300/t040-source-comparison.json).

### Classes e configurações reais conferidas

- OrderRegistrationIntegrationTest e OrderSecurityChainTest: SpringBootTest, perfil test, MockMvc com filtros ativos, open-in-view=false, import explícito de OrderTestConfig, H2 order-test-UUID por contexto e Clock controlável já existente. Login/encoder/decoder/filtros/banco reais; sem autenticação simulada. Registration usa DriverManagerDataSource independente e exige transação inativa antes/depois da operação; limpeza explícita depois das assertions. Chain observa a autenticação real e seleção das chains, não injeta um usuário autenticado.
- OrderJwtAuthenticationConverterTest: unidade, resolução mockada e objeto Jwt de fixture; prova conversão/claims/guardas, não verificação criptográfica ou HTTP. OrderJwtAuthenticationPipelineTest: decoder produtivo de JwtConfig, JwtAuthenticationProvider e converter reais, tokens realmente assinados/adulterados e chave descartável; somente resolução de conta mockada. Prova assinatura/claims antes do lookup, não banco/HTTP/atomicidade.
- LoginControllerTest: WebMvcTest de LoginController, property test.order-security.public-mvc-slice=true, SecurityConfig e OrderSecuritySliceTestConfig importados, AuthenticationService mockado; decoder/resolução do suporte público também mocks. Não é prova de JWT real. O slice não importa OrderClockConfig, origem da falha registrada abaixo.
- JwtConfigurationTest: WebApplicationContextRunner com DscommerceApplication, propriedades próprias de chave/TTL e encoder/decoder reais; cinco configurações recusadas e um controle HS256 válido. Não usa o perfil test ou as fixtures de OrderTestConfig. GlobalExceptionHandlerRegressionTest: slice MVC de login/produto/sonda de validação, serviços mockados, suporte público e OrderClockConfig explicitamente importados; prova contratos antigos/advice/path/confidencialidade, não banco/JWT.

Nomes/packages e configurações conferidos antes de executar. [Configurações dos fontes preservados](../../target/harness-validation/feature-004-t041-2026-10-09-182300/class-configurations.json). Java 21.0.12.1 confirmado nos sete XMLs; Wrapper configurado para Maven 3.9.16. Contextos Registration/Chain usam URLs H2 isoladas distintas; unidade/slices não gravam pedidos. Os contextos de JwtConfigurationTest pertencem à configuração existente do runner, sem atribuir-lhes isolamento de OrderTestConfig. [Runtime e URLs observadas](../../target/harness-validation/feature-004-t041-2026-10-09-182300/runtime-isolation.json).

Não adicionada outra classe de login: a seleção solicitada cobre o caminho integrado válido, a chain pública diante de Bearer irrelevante, a configuração JWT e contratos de erro do advice. AuthenticationInternalFailureTest/AuthenticationSensitiveLoggingTest são unidades com colaboradores mockados; não suprem a falta de Clock no slice e suas regressões amplas estão previstas em T047. Não usar outra classe GREEN para substituir a execução de LoginControllerTest que falhou.

### Comandos, horários e resultados atuais

Somente as sete classes solicitadas, cada uma executada uma vez, serialmente, sem sobreposição ou repetição de T040. Depois da falha de login foram concluídas as duas verificações restantes explicitamente solicitadas, mantendo a falha e sem correção ou avanço de tarefa.

```powershell
.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityChainTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderJwtAuthenticationPipelineTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=LoginControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=JwtConfigurationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

2026-10-09, horários UTC−03:00; exit Maven capturado imediatamente e report novo da classe selecionada em cada execução. Relatórios antigos não foram tratados como atuais.

| Classe | Início → término | Casos reportados | Falhas | Erros | Ignorados | Exit Maven / término |
|---|---|---:|---:|---:|---:|---|
| OrderRegistrationIntegrationTest | 18:23:32.619 → 18:24:07.489 | 9 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| OrderSecurityChainTest | 18:24:23.009 → 18:24:56.381 | 20 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| OrderJwtAuthenticationConverterTest | 18:25:04.088 → 18:25:20.873 | 48 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| OrderJwtAuthenticationPipelineTest | 18:25:47.651 → 18:26:04.082 | 25 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| LoginControllerTest | 18:26:29.658 → 18:26:50.295 | 13 | 0 | 13 | 0 | 1 / BUILD FAILURE |
| JwtConfigurationTest | 18:27:55.087 → 18:28:36.871 | 6 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| GlobalExceptionHandlerRegressionTest | 18:28:51.387 → 18:29:14.895 | 12 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| **Total desta etapa** | **sete comandos seriais** | **133** | **0** | **13** | **0** | **seis sucesso, um falha** |

**120 casos aprovados; 13 erros de preparação do contexto, sem executar as assertions de login.** Não declarar 133 aprovados, falha comportamental de todos os logins ou checkpoint GREEN. Os 411 históricos de T040 continuam preservados e separados; os 120 aprovados atuais não eliminam os 13 erros. [Resumo real das sete execuções](../../target/harness-validation/feature-004-t041-2026-10-09-182300/results-summary.json), [contagens por método](../../target/harness-validation/feature-004-t041-2026-10-09-182300/case-counts.json). Na captura feature-004-t041-2026-10-09-182300, cada Classe-result.json conserva comando/início/término/exit/contagens/resultado, Classe-console.txt e Classe-reports/ conservam console e XML/TXT completos.

### Rastreabilidade US2 / FR / SC e evidência real

As siglas abaixo identificam a classe e sua última execução selecionada: C = OrderControllerTest (141), S = OrderServiceTest (42), I = OrderSecurityIntegrationTest (185), R = OrderRollbackIntegrationTest (9), P = OrderRepositoryTest (23), A = UserAccountRoleRepositoryTest (11). São as seis evidências históricas GREEN de T040, não novas execuções. A revisão [traceability-review.json](../../target/harness-validation/feature-004-t041-2026-10-09-182300/traceability-review.json) associa cada linha a métodos exatos, XMLs, comando/resultado/exit e horários: 13 cenários, 34 referências a métodos efetivamente presentes nos reports; as referências podem compartilhar um método parametrizado e não são contagem de testes novos.

| Cenário / requisitos | Contrato / cenário exercitado | Camada e evidência |
|---|---|---|
| US2.1 — FR-002/004/012, SC-004 | 400 para corpo/itens ausentes, null, vazios ou estruturas inválidas | C.InputBoundary: shouldRejectInvalidBodiesAndContainersBeforeService, 24 casos; slice de JSON bruto, zero serviço, sem prova própria de JDBC/JWT. |
| US2.2 — FR-004/012, SC-004 | 400 para quantidade/ID inválidos, fração/tipos/overflow; limites inteiros lidos sem coerção | C.InputBoundary: shouldRejectInvalidIntegerFieldsBeforeService, 28; shouldReadPositiveIntegerTokensIncludingBothMaximumsExactly, dois positivos com serviço mockado. Esses IDs máximos não são compras reais de produtos existentes. |
| US2.3 — FR-004/012, SC-004/009 | 404 Product not found com produto ausente entre válidos | S rejeita antes de save; R.shouldRejectTheWholeInputBeforeAnyInsert, variante MISSING_PRODUCT: JWT/HTTP/JPA reais, zero INSERTs e snapshot independente preservado. |
| US2.4 — FR-010/012, SC-004 | 401 Authentication required or invalid e WWW-Authenticate: Bearer | I.shouldRejectInvalidJwtBeforeReadingBody, 138 casos: 23 variantes × seis combinações de corpo/contexto, zero serviço/escrita, inclusive corpo ilegível e JSON bem formado inválido. |
| US2.5 — FR-003/010/012, SC-004 | 401 para subject/conta inválidos; 500 distinguível de falha interna | I cobre rejeições JWT/conta e guardas tardias; shouldReturnInternalErrorForResolutionFailureBeforeReadingBody, seis respostas 500 exatas, sem serviço/escrita. Cinco identidades inválidas em chamada direta pelo proxy, distintas da prova criptográfica. |
| US2.6 — FR-011/012, SC-004 | 403 Order registration not permitted, perfis do banco, sem confiar em ADMIN no JWT | I.shouldForbidAccountWithoutProfilesEvenWithAdminClaim, seis; reconsulta por proxy/guardas tardias; A comprova perfis carregados e conta sem linhas de roles recuperável. Login dessa conta continua 200 nos casos integrados. |
| US2.7 — FR-003/005/012/013, SC-004 | 400 para titular/valores/ID/instante/estado fornecidos, extras null e chaves repetidas | C.InputBoundary: 40 campos extras/do sistema + 13 chaves repetidas; slice de leitura estrita e zero serviço. |
| US2.8 — FR-012/014, SC-004/009 | 422 Duplicate product in order, sem consolidar | S rejeita sem save; R variante DUPLICATE_PRODUCT exige ausência integral; P prova unicidade por JDBC, distinta da regra de negócio. |
| US2.9 — FR-012, SC-004 | 500 seguro, sem sucesso/gravação parcial; controle positivo 201 | R: SQL tardio após dois INSERTs anteriores, falha depois de saveAndFlush real e falha beforeCommit após DTO; leituras independentes após proxy e antes da limpeza. Controle positivo grava +1 pedido/+2 itens. |
| US2.10 — FR-008/012, SC-004 | 422 Order value cannot be represented; cálculo 42.50, mínimos/tetos/overflow exatos | S prova sem save as seis perdas/overflow unitário/subtotal e overflow da soma; cinco limites positivos e soma no teto. C.SuccessfulRegistration prova capacidade decimal como string em HTTP real; P prova decimal19,2/quantity por JDBC. Não alegar que preço além do schema foi persistido. |
| US2.11 — FR-005/012, SC-004/009 | 422 Product price is not valid for order com preço zero entre positivos | S + R variante ZERO_PRICE; HTTP real/zero INSERTs/snapshot independente integral. |
| US2.12 — FR-005/012, SC-004/009 | Mesmo 422 para preço negativo entre positivos | S + R variante NEGATIVE_PRICE; fixture negativa por JDBC em H2 isolado, preservando as validações de Product. Preço null/indisponível tem prova unitária, não fixture persistida contra NOT NULL. |
| US2.13 — FR-004/012, SC-004/009 | 400 Invalid order data, quantity zero entre itens válidos | C.InputBoundary: zero serviço; R variante ZERO_QUANTITY: HTTP/JWT reais, zero INSERTs/snapshot integral. |

FR-002–005/008/010–014 e SC-004/009 estão vinculados aos cenários acima. A evidência é composta pelas camadas declaradas: não transformar unidade/slice em prova de HTTP/JWT/banco. Segurança → leitura/Bean Validation → negócio → persistência/conclusão continua coerente com contrato, código e evidências; guardas tardias são adicionais após a chain. Não se promete prioridade entre múltiplos erros de negócio. Constraints de NOT NULL/FK/CHECK/unicidades comprovadas por JDBC/metadados em P/A, não por Bean Validation; IDs únicos sem exigir sequência contínua. Nenhuma contradição adicional de US2 encontrada na revisão, além da regressão de preparação de login que impede fechar T041.

### US1 e regressões repetidas na mesma revisão

OrderRegistrationIntegrationTest passou nove casos cobrindo US1.1–10: três conjuntos de perfis autorizados, conta correta, shape exato/ordem/UTC-milissegundos/WAITING_PAYMENT/strings monetárias e total 42.50; preço vigente alterado antes da compra; snapshot conservado após alterações posteriores; confirmação sem Location ou promessa de esvaziar carrinho; compras distintas e dois reenvios iguais, com confirmação anterior recebida ou descartada. JDBC independente depois de 201 e antes da limpeza comprova pedidos/itens completos e históricos, com IDs diferentes. [Resultado US1 atual](../../target/harness-validation/feature-004-t041-2026-10-09-182300/OrderRegistrationIntegrationTest-result.json).

Os 20 casos da chain comprovam seleção Bearer somente no POST lógico /orders, inclusive context path, CLIENT/ADMIN/ambos, principal mínimo e sessão/cookie ausentes; fallback de catálogo/detalhe/login mantém Bearer malformado irrelevante. Converter 48 e pipeline 25 preservam claims, perfis, validação antes do lookup e falha interna distinta de credencial inválida. JwtConfigurationTest mantém recusa de chave/TTL inválidos e controle encoder/decoder HS256 real. GlobalExceptionHandlerRegressionTest mantém 12 casos de contratos antigos: mensagens específicas de produtos/validação/login, path externo com /api, envelope/confidencialidade e falha pública inesperada sem tradução artificial para erro de pedidos. [Resultado do advice atual](../../target/harness-validation/feature-004-t041-2026-10-09-182300/GlobalExceptionHandlerRegressionTest-result.json).

Assim, os positivos reais de US1 coexistem com as rejeições/atomicidade de US2 já comprovadas em T040, sobre os mesmos fontes; o checkpoint global permanece incompleto por LoginControllerTest. As execuções separadas não comprovam uma sequência completa de jornadas públicas depois de cada rejeição no mesmo cenário: isso é T046. Não declarar SC-006 ou regressões completas US3 satisfeitas.

### Impedimento observado e próximo passo mínimo

LoginControllerTest produziu **13 erros de contexto**. Causa inicial: UnsatisfiedDependencyException/NoSuchBeanDefinitionException — `orderJwtAuthenticationConverter` em SecurityConfig requer `java.time.Clock` no parâmetro 1, mas o slice importa somente SecurityConfig/OrderSecuritySliceTestConfig e não disponibiliza esse bean. Os erros seguintes incluem o limite de tentativas de inicialização do Spring, não 13 falhas de comportamento independentes. LoginControllerTest não chegou às assertions; nenhum dos seus 13 casos pode ser declarado aprovado nesta revisão. [Diagnóstico e correção sugerida não aplicada](../../target/harness-validation/feature-004-t041-2026-10-09-182300/login-preparation-failure.json), [resultado Maven](../../target/harness-validation/feature-004-t041-2026-10-09-182300/LoginControllerTest-result.json), [XML](../../target/harness-validation/feature-004-t041-2026-10-09-182300/LoginControllerTest-reports/TEST-com.devsuperior.dscommerce.controllers.LoginControllerTest.xml) e [console](../../target/harness-validation/feature-004-t041-2026-10-09-182300/LoginControllerTest-console.txt).

Próximo passo mínimo, não executado: completar **somente a preparação do slice de LoginControllerTest**, importando o OrderClockConfig existente como já faz GlobalExceptionHandlerRegressionTest, ou provendo Clock equivalente de teste; preservar todas as assertions e produção. Depois repetir somente LoginControllerTest serialmente pelo Wrapper e reavaliar T041. Não se justifica repetir as seis classes completas de T040 ou os outros seis alvos atuais se seus fontes/configurações permanecerem inalterados. Nenhuma correção aplicada nesta etapa; T041 continua aberta.

### Limites e preservação

Atomicidade usa as três provas distintas reais de R, sem @Transactional externo/rollback automático de teste. beforeCommit ocorre depois do corpo produzir DTO e antes do commit físico; não simula falha física de commit/conexão. Descartar a resposta em US1.9 simula seu não consumo; não reproduz interrupção física de transporte após commit. Falha de transmissão após commit não promete rollback, seguindo Q1. Sem idempotência/consulta/histórico/pagamento/carrinho/deploy. H2/MockMvc locais não aprovam outros bancos, transporte real, ambiente remoto, suíte completa ou CI.

**U1 (conteúdo residual no JSON) e U2 (ativação de fixtures na demonstração) permanecem explicitamente pendentes, não aplicadas.** Não reescrito o histórico ou seus procedimentos como execuções atuais. Regressões completas de catálogo/detalhes/H2/login/logging/BCrypt/repositórios e integração após rejeições permanecem em T042–T049; nenhuma dessas tarefas foi executada.

Captura anterior à execução: 4857 arquivos, sendo 156 do inventário Git e 4701 evidências históricas; 66 reports Surefire anteriores arquivados em reports-before; 83 fontes executados copiados/hashados. Auditoria SHA-256 final exige alteração somente em quickstart.md por acréscimo, prefixo histórico de 469602 bytes preservado, tasks.md byte a byte idêntico, todos os fontes/demais arquivos/evidências anteriores e inventário Git intactos. [Auditoria de preservação](../../target/harness-validation/feature-004-t041-2026-10-09-182300/final-preservation.json).

Diff incremental documental e whitespace conferidos, inclusive os seis não rastreados anteriores, sem diagnósticos; links de evidências conferidos. [Revisão final](../../target/harness-validation/feature-004-t041-2026-10-09-182300/review-checks.json). Alterado nesta etapa **somente quickstart.md**, além da captura ignorada de evidências. T001–T040 [X] (40), T041–T055 abertas (15), **nenhuma checkbox alterada**. Raiz/branch/HEAD mantidos; mesmos 14 rastreados modificados/seis não rastreados e índice vazio. Sem produção/testes, T042+, U1/U2, suíte completa, CI, stage, commit, push, merge ou troca de branch.

## T041 — conclusão após correção exclusiva da preparação de login (2026-10-10)

Solicitação autoriza concluir somente T041 da Feature 004 e parar antes de T042. Raiz reconfirmada pelos quatro comandos `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `0344dc0199fd8135f98e852e7d6fedc06b124d9a`. Estado inicial: 14 rastreados modificados, seis não rastreados, índice vazio. Prerequisites executado com `& .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, FEATURE_DIR correto. Checklist requirements.md 16/16, zero abertos, somente leitura. Constituição, spec, T040/T041/checkpoint/dependências, plano, pesquisa, modelo e contratos consultados. Ignores existentes suficientes; `.specify/extensions.yml` ausente antes e depois, sem hooks executáveis.

### Causa confirmada e correção mínima

Antes de editar, conferidos diagnóstico, console/XML de 2026-10-09 e fontes: SecurityConfig exige `Clock` no parâmetro 1 de `orderJwtAuthenticationConverter`; LoginControllerTest e OrderSecuritySliceTestConfig não forneciam esse bean. OrderClockConfig existente declara somente `orderClock()` com `Clock.systemUTC()`. GlobalExceptionHandlerRegressionTest já importa essa configuração explicitamente no slice.

Alterado somente LoginControllerTest: adicionado o import Java de OrderClockConfig e incluído `OrderClockConfig.class` no `@Import`, seguindo esse padrão. Nenhum novo bean, mock de Clock, override ou configuração duplicada; contexto inicializa com a configuração existente. Preservados byte a byte os demais trechos do teste, inclusive assertions, métodos, cenários e CRLF; mantidos WebMvcTest, filtros e verificações. Nenhum código de produção alterado nesta etapa.

Os **13 erros iniciais continuam preservados como falha de preparação**, não RED comportamental nem aprovações: exit 1 / BUILD FAILURE em 2026-10-09, ausência de Clock e erros subsequentes pelo limite de inicialização do Spring, sem alcançar assertions. A nova aprovação não reescreve esse resultado. [Diagnóstico inicial](../../target/harness-validation/feature-004-t041-2026-10-09-182300/login-preparation-failure.json), [resultado inicial](../../target/harness-validation/feature-004-t041-2026-10-09-182300/LoginControllerTest-result.json) e respectivos console/XML permanecem intactos.

### Única execução Maven desta etapa

Na raiz, Windows/PowerShell, serialmente:

```powershell
.\mvnw.cmd "-Dtest=LoginControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

Início `2026-10-10T17:09:03.1217704-03:00`, término `2026-10-10T17:10:39.8616022-03:00`; **13 testes, 0 falhas, 0 erros, 0 ignorados; exit code 0, BUILD SUCCESS**. XML novo confirmado para a classe selecionada; console registra somente LoginControllerTest em `Running`. Java `21.0.12.1` observado no XML; Maven 3.9.16 selecionado pelas propriedades do Wrapper. A recompilação dos 35 fontes de teste é preparação do build, não execução das outras classes. [Resultado atual](../../target/harness-validation/feature-004-t041-completion-2026-10-10/LoginControllerTest-result.json), [console](../../target/harness-validation/feature-004-t041-completion-2026-10-10/LoginControllerTest-console.txt) e [XML atual](../../target/harness-validation/feature-004-t041-completion-2026-10-10/LoginControllerTest-reports/TEST-com.devsuperior.dscommerce.controllers.LoginControllerTest.xml).

Não repetidas as outras seis classes de T041, nem a bateria de T040. Uma última execução relevante por classe, com XMLs, contagens e exit codes reconferidos:

| Classe | Execução selecionada (UTC−03:00) | Aprovações | Falhas / erros / ignorados | Exit / resultado |
| --- | --- | ---: | --- | --- |
| OrderRegistrationIntegrationTest | 2026-10-09 18:23:32.619 → 18:24:07.489 | 9 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| OrderSecurityChainTest | 2026-10-09 18:24:23.009 → 18:24:56.381 | 20 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| OrderJwtAuthenticationConverterTest | 2026-10-09 18:25:04.088 → 18:25:20.873 | 48 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| OrderJwtAuthenticationPipelineTest | 2026-10-09 18:25:47.651 → 18:26:04.082 | 25 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| LoginControllerTest | 2026-10-10 17:09:03.121 → 17:10:39.861 | 13 | 0 / 0 / 0 | 0 / BUILD SUCCESS atual |
| JwtConfigurationTest | 2026-10-09 18:27:55.087 → 18:28:36.871 | 6 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| GlobalExceptionHandlerRegressionTest | 2026-10-09 18:28:51.387 → 18:29:14.895 | 12 | 0 / 0 / 0 | 0 / BUILD SUCCESS histórico |
| **T041 consolidada** | **última execução relevante de sete classes** | **133** | **0 / 0 / 0** | **sete resultados selecionados GREEN** |

Total composto por **120 aprovações históricas + 13 atuais**, não 133 testes executados hoje, nem 146 pela soma das duas tentativas de login. [Consolidação com comandos completos, horários e caminhos por classe](../../target/harness-validation/feature-004-t041-completion-2026-10-10/t041-consolidated.json); comandos históricos permanecem na seção anterior e nos Classe-result.json originais.

### Checkpoint e rastreabilidade reconferidos

Conferidas novamente US2.1–13, FR-002–005/008/010–014 e SC-004/009 contra spec, contrato e tabela de rastreabilidade anterior. A revisão automatizada reconferiu **13 cenários e 34 referências a métodos**, contando suas invocações reais nos XMLs. As **411 aprovações consolidadas de T040** continuam separadas e históricas: Controller 141, SecurityIntegration 185 (inclui o complemento de 100 combinações), Service 42, RollbackIntegration 9, OrderRepository 23 e UserAccountRoleRepository 11; todos exit 0/zero falhas, erros e ignorados. Não somados novamente os 85 casos antigos de segurança. [Gate histórico de T040](../../target/harness-validation/feature-004-t041-2026-10-09-182300/t040-gate.json), [rastreabilidade histórica detalhada](../../target/harness-validation/feature-004-t041-2026-10-09-182300/traceability-review.json) e [reconferência atual](../../target/harness-validation/feature-004-t041-completion-2026-10-10/checkpoint-review.json).

Os 83 fontes eram idênticos à captura anterior antes da edição; agora somente a preparação de LoginControllerTest difere. Fontes/configurações das seis classes T041 preservadas e alvos T040 continuam iguais. As evidências sustentam segurança → leitura/Bean Validation → negócio → persistência/conclusão, sem prioridade prometida entre múltiplos erros de negócio; constraints por JDBC, mínimos/tetos exatos, rejeição integral e IDs sem exigir sequência contínua.

Atomicidade continua sustentada pelas três provas reais: SQL tardio após INSERTs, processamento após saveAndFlush real e beforeCommit após produção do DTO, além do controle positivo 201 visível em conexão independente. Leituras ocorrem depois da saída do proxy e antes da limpeza, sem transação externa/rollback automático do teste. Unidade e slice não são apresentados como prova de atomicidade/JWT/banco. Positivos reais de US1 (nove casos cobrindo US1.1–10) e regressões selecionadas de chain/converter/pipeline/JWT/advice coexistem com essa matriz; a regressão de preparação de login está resolvida. **Checkpoint T041 atendido, sem pendência no escopo: marcada somente T041 [X].**

### Limites, preservação e estado final

LoginControllerTest continua slice com AuthenticationService e suporte público mockados; não prova emissão/assinatura real de JWT, banco ou regressões completas de US3. H2/MockMvc locais não aprovam outro banco, transporte real, suíte completa ou CI. beforeCommit não simula falha física de conexão no commit; descarte de resposta não reproduz interrupção física de transporte. Não se declara SC-006 nem integração de jornadas após cada rejeição (T046) satisfeitos.

U1/U2 continuam pendentes, sem aplicação. T042–T055 permanecem abertas; parada antes de T042. Sem suíte completa, CI, stage, commit, push, merge ou troca de branch. Sondagem auxiliar `python -` não encontrou Python utilizável (alias da Microsoft Store); auditoria/consolidação realizadas com PowerShell, sem bloqueio da validação Maven.

Capturados hashes de **5054 arquivos preexistentes** e cópia dos reports Surefire antes da execução; evidências antigas preservadas e disponíveis mesmo onde Maven atualizou reports de trabalho. Auditoria final confere alteração incremental somente em LoginControllerTest, quickstart.md por acréscimo e a checkbox T041 em tasks.md; demais alterações/fontes/evidências e os seis não rastreados anteriores preservados. [Auditoria de preservação](../../target/harness-validation/feature-004-t041-completion-2026-10-10/final-preservation.json).

Diff incremental, links do acréscimo, conteúdo dos arquivos de evidência novos e whitespace revisados; `git diff --check` executado com exit 0, sem erros de whitespace; avisos de conversão LF/CRLF estão preservados no log da revisão, sem alteração dos trechos anteriores. Conferidos também os seis não rastreados anteriores, sem mudanças de conteúdo ou whitespace. [Revisão final e estado Git](../../target/harness-validation/feature-004-t041-completion-2026-10-10/review-checks.json). Arquivos alterados nesta etapa: `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`, `specs/004-registrar-pedido/quickstart.md` e `specs/004-registrar-pedido/tasks.md`, além de evidências ignoradas em target. Checkboxes finais: **T001–T041 [X] (41), T042–T055 [ ] (14)**. Raiz/branch/HEAD mantidos; Git final com **15 rastreados modificados, seis não rastreados e índice vazio**.

## T042 — regressão pública do catálogo com Bearer inválido irrelevante (2026-10-10)

Executada exclusivamente T042; parada antes de T043. Raiz, branch real, HEAD e status confirmados com `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: `C:/Users/ronal/dscommerce-speckit`, `004-registrar-pedido`, `73d3398965b25027d393202a2c5a984984abd0ca`, working tree inicialmente limpo e índice vazio. T041 concluída como dependência; suas 133 aprovações e as 411 de T040 permanecem históricas, sem repetição.

Skill speckit-implement aplicada no escopo autorizado. `& .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` executado com exit 0, FEATURE_DIR correto; checklist requirements.md 16/16, zero abertos, somente leitura. Conferidos T042, US3.1, FR-015, SC-006, spec/contrato OpenAPI da Feature 001, plano/decisões/modelo/constituição e procedimentos do quickstart. Ignores existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Testes entregues e preparação

PublicCatalogSecurityIntegrationTest mantém seus sete casos originais sem token e adiciona 26 invocações parametrizadas: 13 sem token e 13 com `Authorization: Bearer not-a-valid-jwt`. SpringBootTest/perfil test/MockMvc com `addFilters=true`, ObjectMapper, serviços, repositórios e H2 reais; sem MockBean, jwt(), WithMockUser ou autenticação simulada. A chain realmente selecionada para GET /products contém AuthorizationFilter e não BearerTokenAuthenticationFilter. SAMEORIGIN observado nas respostas confirma passagem pelos filtros; respostas públicas não recebem WWW-Authenticate. Isso não altera a chain de produção.

Os 31 casos do catálogo principal usam a seed original de 52 produtos, com SELECT de contagem nas novas verificações e sem escrita/limpeza de produtos. Dois casos adicionais de catálogo realmente vazio usam contexto completo/H2 próprio `catalog-empty-UUID`, sem carregar import.sql (`spring.sql.init.mode=never`, `spring.jpa.properties.hibernate.hbm2ddl.import_files=`). As contagens JDBC zero antes/depois confirmam a fixture vazia. Pools observados no console: catálogo principal `jdbc:h2:mem:dscommerce-test`; vazio `jdbc:h2:mem:catalog-empty-2e0e7563-ab38-4c82-b980-20b4b2d18f3e`. Nenhuma seed, recurso compartilhado ou regra do catálogo foi modificada.

ProductControllerTest foi inicialmente executado com sua preparação original: **11 erros de contexto, zero falhas comportamentais**, exit 1 / BUILD FAILURE, sem alcançar assertions. Console confirma UnsatisfiedDependencyException/NoSuchBeanDefinitionException: parâmetro 1 de `orderJwtAuthenticationConverter` requer `java.time.Clock`, ausente no slice. OrderSecuritySliceTestConfig não fornece Clock. Após confirmar a causa, adicionados somente o import Java de OrderClockConfig e `OrderClockConfig.class` ao @Import, seguindo LoginControllerTest/GlobalExceptionHandlerRegressionTest. Sem bean duplicado, contexto completo substituindo slice ou verificações desabilitadas. Preservados todos os 11 cenários, métodos e assertions existentes, inclusive os dois casos anteriores de detalhe que já pertencem a essa classe. Repetida somente ProductControllerTest; a integração aprovada não foi repetida.

### Comandos, execuções e contagens

Na raiz Windows/PowerShell, serialmente, sem sobreposição:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=ProductControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
# Depois de confirmar e corrigir exclusivamente a preparação de ProductControllerTest:
.\mvnw.cmd "-Dtest=ProductControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Execução | Início → término (UTC−03:00) | Casos | Falhas | Erros | Ignorados | Exit / resultado |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| PublicCatalogSecurityIntegrationTest | 2026-10-10 17:30:15.645 → 17:31:13.366 | 33 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| ProductControllerTest, preparação inicial | 2026-10-10 17:31:28.321 → 17:31:49.152 | 11 | 0 | 11 | 0 | 1 / BUILD FAILURE de preparação |
| ProductControllerTest, preparação corrigida | 2026-10-10 17:32:09.640 → 17:32:48.295 | 11 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| **Final selecionado de T042** | **última execução relevante de cada classe** | **44** | **0** | **0** | **0** | **33 + 11 aprovações** |

Os 11 erros iniciais permanecem separados, não são RED comportamental nem somados às 44 aprovações finais. Foram três comandos para apenas duas classes solicitadas; nenhuma suíte completa/CI ou outra classe executada. A recompilação dos fontes de teste não significa execução deles. Java `21.0.12.1` observado nos XMLs; Maven 3.9.16 selecionado pelo Wrapper. XMLs novos arquivados por execução; relatórios anteriores foram copiados antes dos testes.

Surefire atribui os 33 casos da integração ao XML de `$EmptyCatalog` e zero ao XML do contêiner externo; os testcase.classname distinguem **31 do catálogo principal e dois do vazio**. Contados os testcase/atributos efetivos dos dois XMLs, sem duplicação por contêiner. [Contagens, runtime e consolidação](../../target/harness-validation/feature-004-t042-2026-10-10/results-summary.json).

[Resultado da integração](../../target/harness-validation/feature-004-t042-2026-10-10/PublicCatalogSecurityIntegrationTest-result.json), [console da integração](../../target/harness-validation/feature-004-t042-2026-10-10/PublicCatalogSecurityIntegrationTest-console.txt), [falha inicial do slice](../../target/harness-validation/feature-004-t042-2026-10-10/ProductControllerTest-result.json), [console da falha inicial](../../target/harness-validation/feature-004-t042-2026-10-10/ProductControllerTest-console.txt) e [resultado final do slice](../../target/harness-validation/feature-004-t042-2026-10-10/product-prepared/ProductControllerTest-result.json). Cada execução conserva console e diretório Classe-reports com XML/TXT.

### Cobertura de US3.1 / FR-015 / parcela de SC-006

| Cenário da Feature 001 | Evidência integrada nova, sem token e com Bearer inválido |
| --- | --- |
| Shape/dados essenciais em todos os itens | Envelope com exatamente content/page/size/totalElements/totalPages; metadados inteiros; cada produto com exatamente id/name/image/price, id inteiro positivo, textos não vazios e preço numérico não negativo. Sem description/categories. Verificado em cada página positiva. |
| Paginação padrão | Dois casos: page=0, size=12, totalElements=52, totalPages=5, content=12. |
| Paginação explícita e limites válidos | Dez casos: page/size 1/2, 4/12, 0/1, 0/50, 1/50; tamanhos retornados 2/4/1/50/2 e totais coerentes. |
| Defaults independentes | Dois casos, cada um com duas chamadas: só page=1 mantém size=12; só size=2 mantém page=0. |
| Ordenação name ASC/id ASC e catálogo completo | Dois casos percorrem cinco páginas de 12, reunindo 52 IDs únicos: 51,52,50…1. Primeiros nomes Duplicate/Duplicate/Product 01/Product 02; comparação integral por name/id, inclusive fronteiras de páginas. |
| Resultado vazio por página além do total | Dois casos: page=999, size=12, content vazio, totalElements=52/totalPages=5 preservados. |
| Ausência real de produtos | Dois casos em H2 separado: 200, content vazio, page=0/size=12/totalElements=0/totalPages=0. |
| Parâmetros inválidos e mensagens contratuais | Seis casos: page=-1, size=0 e size=51 em ambos os modos; 400 com exatamente status/error/message/path. Mensagens `page must be greater than or equal to 0` e `size must be between 1 and 50`, path /products, sem desafio de autenticação. |

As sete invocações originais sem token permanecem intactas. ProductControllerTest conserva suas 11 aprovações de shape/paginação/ordenação/erros/detalhe em slice com ProductCatalogService e suporte público mockados: não é prova de JWT, banco ou integração com filtros reais. A prova de que Bearer inválido não interfere no catálogo vem dos novos casos com contexto/filtros reais.

**Comportamento do catálogo já preservado; nenhuma regressão comportamental observada e nenhuma correção de produção necessária. T042 marcada [X] após entrega e execução válida documentadas.** Erro de contexto do slice não comprova regressão do catálogo. Não criada falha artificial para demonstrar RED.

### Limites, preservação e revisão final

Esta etapa comprova o catálogo público no H2/MockMvc local com um Bearer malformado representativo. Não prova transporte real, todas as variantes JWT, outros bancos, CI ou suíte completa; não valida mensagens para tipos/formatos de query sem mensagem contratual definida. SC-006 global inclui detalhes/login e jornadas após rejeições, ainda em T043–T049; não é declarado integralmente atendido. Os casos de detalhe já existentes no slice executado não constituem execução de T043.

U1/U2 continuam pendentes, sem aplicação. Nenhuma T043+, stage, commit, push, merge ou troca de branch. Histórico anterior do quickstart preservado por acréscimo; snapshot SHA-256 dos 156 arquivos rastreados confirma apenas os quatro arquivos autorizados alterados e todos os fontes produtivos/seed demais arquivos preservados. Casos originais da integração e corpo do slice conferidos contra as cópias anteriores. [Revisão final e preservação](../../target/harness-validation/feature-004-t042-2026-10-10/final-review.json).

Diff dos quatro arquivos revisado; `git diff --check` executado com exit 0, sem erros de whitespace. Avisos Git de conversão LF/CRLF não são falhas de whitespace. Evidências novas em target são ignoradas pelo Git, com conteúdo/links conferidos. Estado final: mesmos raiz/branch/HEAD; **quatro rastreados modificados, nenhum não rastreado e índice vazio**. Arquivos: PublicCatalogSecurityIntegrationTest.java, ProductControllerTest.java (somente preparação), quickstart.md e tasks.md (somente checkbox T042). **T001–T042 [X] (42), T043–T055 abertas (13)**; parada antes de T043.

## T043 — regressão pública dos detalhes com Bearer inválido irrelevante (2026-10-10)

Executada exclusivamente T043; parada antes de T044. Conferidos `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Estado inicial: quatro rastreados modificados de T042 (PublicCatalogSecurityIntegrationTest.java, ProductControllerTest.java, quickstart.md e tasks.md), nenhum não rastreado e índice vazio. Essas alterações foram preservadas. T041 concluída satisfaz a dependência de T043; as 411 aprovações de T040, 133 de T041 e 44 selecionadas de T042 são evidências anteriores, sem nova execução nesta etapa.

Skill speckit-implement aplicada somente a T043. `powershell.exe -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` executado com exit 0 e FEATURE_DIR correto; checklist requirements.md conferido somente para leitura: 16/16, zero abertos. `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis; ignores Java existentes suficientes. Consultados T043/US3.2/FR-015/SC-006 e os artefatos da Feature 002 (spec, plan, research, data-model, tasks, quickstart e contracts/product-detail-openapi.yaml), além das decisões/modelo/contratos da Feature 004, constituição e implementação atual de detalhe/segurança/mapeamento.

### Integração, cenários e fixture

PublicProductDetailSecurityIntegrationTest mantém integralmente seus dois casos anônimos originais e adiciona **36 invocações parametrizadas: 18 sem token e 18 com `Authorization: Bearer not-a-valid-jwt`**. Mantidos SpringBootTest, perfil test e filtros reais de MockMvc, com `addFilters=true` explícito; ObjectMapper, serviços, repositórios e H2 reais. Nenhum MockBean, decoder mock, jwt(), WithMockUser ou filtro desabilitado. Nas novas requisições, conferida a chain efetivamente selecionada para GET /products e /products/{id}: AuthorizationFilter presente, BearerTokenAuthenticationFilter ausente. SAMEORIGIN observado e ausência de WWW-Authenticate reforçam a execução pública pelos filtros existentes.

| Cobertura nova, nos dois modos | Invocações | Verificações |
| --- | ---: | --- |
| Produto selecionado existente | 12 | IDs 1,2,3,50,51,52; exatamente id/name/description/image/price/categories. Tipos contratuais, textos não vazios, preço numérico não negativo e valores exatos da seed. image preservado; não imgUrl ou strings monetárias de pedido. IDs 51/52 distinguem produtos de mesmo nome por imagem/preço/id. |
| Navegação catálogo → detalhes | 2 | Duas páginas (50+2) fornecem 52 IDs únicos. Para cada ID, GET direto dos seus detalhes e comparação id/name/image/price com o item selecionado, mais todos os valores esperados. Lista conserva seus quatro campos, sem description/categories. Apenas chamadas auxiliares nesta classe; nenhuma classe de T042 repetida. |
| Uma, várias e nenhuma categoria | incluídas acima | ID 1: Computers/Electronics; ID 2: Accessories; demais: lista vazia. Todos os nomes associados, somente strings não vazias, sem IDs/objetos de categoria e sem repetição; ordenação determinística atual preservada. |
| Produto inexistente e envelope anterior | 10 | IDs 99999,0,-1,Long.MIN_VALUE,Long.MAX_VALUE; 404, exatamente status/error/message/path, error `Not Found`, message `Product not found`, path solicitado. Sem dados de outro produto. Não introduzida validação de id positivo: contrato int64 e lookup Long atuais preservados. |
| Conversão atual de identificadores equivalentes | 4 | /products/01 e /products/+1 continuam resolvendo somente o produto 1 e seus valores completos. |
| Identificadores não convertíveis para Long | 6 | abc, 1.5 e 9223372036854775808 retornam 400 via conversão MVC existente, com filtros reais e sem desafio de autenticação. Não inventada mensagem/envelope contratual para esses casos: OpenAPI define 200/404, não esse corpo 400. |
| Nomes repetidos em categorias distintas | 2 | Fixture JDBC confirma três IDs de categoria associados ao produto 1 e nomes Computers/Electronics/Electronics; resposta inclui somente Computers/Electronics, uma vez cada. Produto 2 mantém somente Accessories. |

Os 36 casos do contexto principal (dois originais + 34 novos) consultam a seed compartilhada sem escrita; novas verificações conferem contagens 52 produtos/3 categorias/3 vínculos. Para provar a deduplicação de nomes com dados reais, os dois casos de DuplicateCategoryNames usam contexto completo separado, com URL estável e exclusiva `jdbc:h2:mem:detail-duplicates-9f1d99fc-1cc7-48f4-97fa-e1b1095db672`. A seed é carregada sem alterar import.sql; somente nesse banco isolado é acrescentada a categoria id 4 com nome Electronics e o vínculo (produto 1, categoria 4), por JDBC com confirmação antes da chamada HTTP. Não há transação externa de teste ocultando a fixture. AfterEach remove somente esses dois registros locais e confirma novamente 3 categorias/3 vínculos; os 52 produtos permanecem intactos. O contexto principal usa `jdbc:h2:mem:dscommerce-test`; URLs/pools distintos observados no console.

Essa fixture é parte da prova de US1.6/FR-005/SC-003 da Feature 002, não uma correção de produção ou uma alteração da seed pública. **Não houve impedimento de contexto nem ajuste de preparação para resolvê-lo.** Nenhum cenário/assertion anterior foi reduzido; ProductControllerTest e PublicCatalogSecurityIntegrationTest permanecem byte a byte como ao início de T043.

### Execução válida e resultado

Na raiz Windows/PowerShell, executado somente um comando de teste pelo Maven Wrapper:

```powershell
.\mvnw.cmd "-Dtest=PublicProductDetailSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Início → término (UTC−03:00) | Casos | Falhas | Erros | Ignorados | Exit / resultado |
| --- | ---: | ---: | ---: | ---: | --- |
| 2026-10-10 17:43:42.145 → 17:44:38.011 | **38** | **0** | **0** | **0** | **0 / BUILD SUCCESS** |

Java `21.0.12.1` observado nos XMLs; Maven 3.9.16 selecionado pelo Wrapper. Surefire atribui 38 casos ao XML de `$DuplicateCategoryNames` e zero ao XML do contêiner externo; os testcase.classname distinguem **36 do contexto principal e dois da fixture isolada**. Conferidos atributos e 38 testcase reais, sem duplicação por contêiner. Recompilar 35 fontes de teste não significa executá-los. Linhas Running do console contêm somente a classe solicitada e sua nested class; nenhuma classe aprovada em T042 foi repetida.

[Resultado/exit code](../../target/harness-validation/feature-004-t043-2026-10-10/PublicProductDetailSecurityIntegrationTest-result.json), [console](../../target/harness-validation/feature-004-t043-2026-10-10/PublicProductDetailSecurityIntegrationTest-console.txt) e [contagens/casos/runtime](../../target/harness-validation/feature-004-t043-2026-10-10/results-summary.json). XML/TXT novos arquivados no diretório PublicProductDetailSecurityIntegrationTest-reports; relatórios Surefire anteriores copiados para reports-before antes da execução. Os avisos de conversão MVC esperados nos seis casos 400 não são falhas dos testes.

**GREEN: comportamento dos detalhes já preservado. Nenhuma regressão comportamental observada, RED artificial ou correção produtiva. T043 marcada [X] após entrega dos testes e documentação da execução válida.** Rastreia US3.2, FR-015 e a parcela de SC-006 relativa aos detalhes; cobre os seis cenários de US1 e os dois de US2 da Feature 002 por valores, seleção e resposta not-found.

### Limitações e revisão final

Evidência local H2/MockMvc com contexto/filtros reais e um Bearer malformado representativo; não demonstra transporte HTTP real, frontend/uma seleção visual, todas as variantes JWT, outros bancos, CI ou suíte completa. Navegação é comprovada por id de catálogo → GET detalhe, sem criação de interface. A seed possui preços positivos; a assertiva de contrato continua não negativa, sem criar fixture de preço zero/precisão extrema ou importar a regra >0 dos pedidos. Catálogo completo de T042 permanece evidência anterior; as chamadas auxiliares para seleção aqui não substituem seus testes de paginação/rejeições. SC-006 global e jornadas após rejeições ainda dependem de T044–T049, não declarados completos.

Somente PublicProductDetailSecurityIntegrationTest.java alterado em código; quickstart.md acrescido e tasks.md com somente T043 marcada nesta etapa. Snapshot SHA-256 de 156 rastreados confirma essas três diferenças incrementais, preservando fontes produtivos/seed e os dois testes alterados em T042. Prefixo histórico inteiro do quickstart preservado em bytes; alteração anterior T042 em tasks mantida. Conferidos também os hashes dos 93 arquivos de evidência de T042, todos preservados. [Revisão final/preservação/Git](../../target/harness-validation/feature-004-t043-2026-10-10/final-review.json).

Diff incremental e acumulado revisados; `git diff --check` com exit 0, sem erros de whitespace; avisos Git LF/CRLF não constituem falhas. Conteúdo/links das novas evidências conferidos; target permanece ignorado. Raiz/branch/HEAD inalterados. Estado final: **cinco rastreados modificados, nenhum não rastreado e índice vazio** (quatro de T042 preservados + integração de detalhes). **T001–T043 [X] (43), T044–T055 abertas (12)**. U1/U2 pendentes, sem aplicação. Não executada T044+, suíte completa/CI, stage, commit, push, merge ou troca de branch.

## T044 — preservação do login e revisão de evidências por camada (2026-10-10)

Executada exclusivamente T044; parada antes de T045. Confirmados raiz/branch real/HEAD/status por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: `C:/Users/ronal/dscommerce-speckit`, `004-registrar-pedido`, `73d3398965b25027d393202a2c5a984984abd0ca`. Inicialmente cinco rastreados modificados de T042–T043, nenhum não rastreado e índice vazio. Todas essas alterações preservadas; nenhuma classe aprovada em T042–T043 foi repetida.

Skill speckit-implement aplicada no escopo delimitado. `powershell.exe -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` executado com exit 0 e FEATURE_DIR correto; checklist requirements.md 16/16, zero abertos, sem edição. `.specify/extensions.yml` ausente antes/depois; nenhum hook executável. Ignores Java existentes suficientes. Revisados T044/dependência T041, US3.3/FR-015/SC-006, contrato login-openapi.yaml e spec/modelo/plano/pesquisa/quickstart da Feature 003, além das decisões da Feature 004, código de login/Jackson/segurança/JWT e evidências arquivadas pertinentes. Procedimentos antigos `mvn test`/curl do quickstart 003 não foram executados nem reclassificados como Wrapper atual.

### Entrega e execução atual: slice HTTP com serviço mockado

LoginControllerTest permanece WebMvcTest com AuthenticationService mockado e suporte OrderSecuritySliceTestConfig (decoder/resolução mockados). Mantidos os imports existentes SecurityConfig/OrderClockConfig/OrderSecuritySliceTestConfig; **nenhum impedimento de contexto, ajuste corretivo de preparação ou alteração produtiva necessário**. Preservados em conteúdo todos os métodos, fontes parametrizadas, helpers e assertions dos 13 casos originais. Acrescidas 70 invocações: **35 sem token e 35 com `Authorization: Bearer not-a-valid-jwt`**. Cada requisição nova percorre os filtros do slice: seleção da chain pública com AuthorizationFilter presente e sem BearerTokenAuthenticationFilter, SAMEORIGIN e ausência de WWW-Authenticate. Isso prova o comportamento HTTP nesse slice, não substitui as provas de login real abaixo.

| Cobertura nova no slice | Casos | Assertions principais |
| --- | ---: | --- |
| Credenciais exatas e sucesso | 2 | name/password com espaços e caixa preservados, uma chamada exata ao serviço, corpo exatamente accessToken/tokenType, Bearer e nenhum campo adicional ou credencial devolvida. ACCESS_TOKEN é stub, não JWT verificável. |
| Credenciais inválidas uniformes | 2 | Cada caso envia nome desconhecido e senha incorreta; corpos 401 idênticos, exatamente status/error/message/path, `Unauthorized` / `Invalid credentials` / `/login`, sem token ou dados recebidos. Duas chamadas esperadas e nenhuma extra. |
| Falha interna mockada | 2 | AuthenticationProcessingException com causa contendo canaries sensíveis; 500 exato `Internal Server Error` / `Authentication could not be completed`, sem credenciais, hash, token ou detalhes da causa. Não simula falha física real de banco/encoder. |
| Entrada estrita, 28 corpos × dois modos | 56 | Ausência/null/vazio/espaços em ambos os campos; tipos number/bool/object/array; campos extras; ausência de ambos; roots não objeto/null; corpo ausente/malformado; email/identifier não substituem name; roles não é entrada de login. 400 exato `Bad Request` / `Invalid login data`, sem token/eco e verifyNoInteractions(authenticationService). Incluídos novamente os 11 corpos inválidos anteriores, sem alterar seus testes originais. |
| Servlet context /api: quatro resultados × dois modos | 8 | 200/400/401/500 preservados e erros com path externo `/api/login`; mensagens específicas de login, sem adotar `Invalid order data`, `Authentication required or invalid` ou `Order registration could not be completed`. |
| **Novos / originais / total atual** | **70 / 13 / 83** | **Zero falhas, erros ou ignorados.** |

Na raiz Windows/PowerShell, somente um comando de teste pelo Maven Wrapper:

```powershell
.\mvnw.cmd "-Dtest=LoginControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Início → término (UTC−03:00) | Casos | Falhas | Erros | Ignorados | Exit / resultado |
| --- | ---: | ---: | ---: | ---: | --- |
| 2026-10-10 17:59:18.595 → 18:00:01.721 | **83** | **0** | **0** | **0** | **0 / BUILD SUCCESS** |

Java `21.0.12.1` observado no XML; Maven 3.9.16 pelo Wrapper. Um XML novo, 83 testcase efetivos e somente LoginControllerTest na linha Running. Recompilar fontes de teste não implica executá-los. [Resultado/exit code](../../target/harness-validation/feature-004-t044-2026-10-10/LoginControllerTest-result.json), [console](../../target/harness-validation/feature-004-t044-2026-10-10/LoginControllerTest-console.txt) e [contagens/casos/runtime](../../target/harness-validation/feature-004-t044-2026-10-10/results-summary.json). Reports XML/TXT arquivados por execução; cópia dos reports anteriores feita antes do Maven, preservando inclusive as falhas de preparação históricas de T041. Não houve novo erro de preparação ou RED comportamental.

### Evidências reais existentes: conferidas, sem nova execução

O stub `signed.jwt.token` não comprova assinatura, claims, BCrypt, lookup ou perfis de uma conta persistida. Nenhum token real foi gerado/decodificado por esta execução de LoginControllerTest. Os critérios que exigem login/JWT reais têm cobertura existente, revisada em código e relatórios; não foi criada outra classe nem ampliada a seleção Maven.

| Critério / camada | Evidência histórica válida consultada | Alcance e limite |
| --- | --- | --- |
| Conta persistida sem perfil pode realizar login | OrderTestSupportTest.shouldReuseRealEncodersAndDecoderWithTheTestProfileKey; grupo de regressões de T009 em 2026-10-07, exit 0; classe 5/0/0/0 | Contexto completo real/H2 isolado/fixture confirmada; JDBC verifica ausência de roles antes/depois, BCrypt real valida o hash, AuthenticationService real e POST /login com FilterChainProxy real retornam 200. Corpo com dois campos/tokenType Bearer; decoder real verifica sub da conta. Claims do token de serviço não contêm roles/passwordHash/secret. Não é execução atual dessa classe. |
| Bearer inválido irrelevante não impede login real de conta sem perfis | OrderSecurityChainTest.shouldPreservePublicFallbackWithAnIrrelevantMalformedBearer e helper login(Set.of(), false); T041 de 2026-10-09 18:24:23.009–18:24:56.381, exit 0; classe 20/0/0/0 | SpringBootTest/H2 isolado/filtros/serviços/encoder/decoder reais. Fixture cria conta sem roles; POST /login com Bearer malformado retorna 200, token é decodificado pelo decoder real e assertions conferem HS256/sub. Outras chamadas desse helper já exercitam o mesmo acesso com perfis diversos; nenhuma autenticação simulada. Esse caso não verifica sozinho todos os campos/claims, complementados pelas demais provas. |
| JWT verificável e claims previstos/confidenciais | JwtTokenServiceTest.shouldIssueNonEmptyVerifiableHs256TokenWithRequiredSafeClaims; T009 exit 0, classe 1/0/0/0; novamente classe 1/0/0/0 no grupo T035 de 2026-10-09 | Bean emissor/decoder reais: token não vazio, Bearer, HS256, sub=id, name exato, iat/exp presentes e duração igual ao TTL; nomes de claims sem password/hash/secret e valores/token sem material da chave. Emissão direta para id/name de teste, sem lookup persistido ou POST; combinada com as provas HTTP/conta acima. O grupo posterior T035 teve exit 1 por preparação temporal da chain: seu JWT GREEN não é apresentado como aprovação desse grupo. |
| Rejeição/fluxo e falhas internas da lógica de serviço | AuthenticationServiceTest 4, AuthenticationInternalFailureTest 4; grupo T009 exit 0 | AuthenticationService real com repository/PasswordEncoder/JwtTokenService mockados; conferem fluxo exato/dummy BCrypt, não emissão nas falhas e classificação segura de lookup/encoder/emissor. Não comprovam login integrado contra conta persistida; contratos HTTP são conferidos pelo slice atual. |
| Confidencialidade de logs | AuthenticationSensitiveLoggingTest 4; grupo T009 exit 0 | Captura de saída do serviço real com colaboradores mockados, incluindo sucesso/credenciais inválidas/falha. Não é captura de logs de uma nova jornada HTTP completa. Confidencialidade do corpo atual e dos claims reais são provas separadas. |

[Inventário dos relatórios e camadas históricas](../../target/harness-validation/feature-004-t044-2026-10-10/historical-layer-evidence.json), [execução T009](../../target/harness-validation/feature-004-t009-2026-10-07/account-login-regressions-result.json), [execução real da chain em T041](../../target/harness-validation/feature-004-t041-2026-10-09-182300/OrderSecurityChainTest-result.json) e [grupo histórico T035 com sua falha preservada](../../target/harness-validation/feature-004-t035-2026-10-09/regression-result.json). O inventário aponta os XMLs e os métodos realmente aprovados; contagens históricas não são somadas às 83 aprovações atuais.

Conferidos por SHA-256 **12 fontes/recursos reais relevantes** contra executed-source-hashes de T041, incluindo SecurityConfig/advice, AuthenticationService/JwtTokenService/JwtConfig/JwtProperties/JacksonConfig, OrderSecurityChainTest/OrderTestSupportTest/JwtTokenServiceTest e perfil/seed: todos idênticos. Comparação Git com o HEAD registrado em T009 (`27850dddc8a21dd1b6542b9cf5f7aa9d9ccf273c`) também não mostrou mudanças nos testes de suporte/emissor/serviço/falhas/logging, nos componentes básicos de autenticação/JWT/Jackson ou nos recursos de teste. SecurityConfig/advice evoluíram depois dessa base; sua cobertura mais recente está na chain real de T041 e no slice atual, sem atribuir a T009 validação de revisão posterior. [Correspondência dos fontes reais](../../target/harness-validation/feature-004-t044-2026-10-10/real-evidence-source-comparison.json).

**GREEN: comportamento do login preservado, sem regressão observada ou correção de produção. Nenhuma lacuna impeditiva encontrada nos critérios reais previstos em T044; conta sem perfis e assinatura/claims reais são comprovados pelas evidências existentes, não pelo token mockado. T044 marcada [X] somente após execução válida do slice e revisão dessas provas.** Não se declara uma nova execução integrada de 400/401/500 reais; esses contratos foram comprovados na camada HTTP com serviço mockado, e sua lógica correspondente tem unidades históricas específicas.

### Limites, preservação e revisão final

Esta etapa não é suíte completa/CI nem nova integração de todos os fluxos de login. H2/MockMvc e as provas históricas não aprovam outros bancos, transporte físico, logs de toda a stack ou todas as variantes JWT. JSON com propriedades repetidas não recebeu regra nova: o leitor estrito de pedidos não foi aplicado ao login; preservado o deserializador atual da Feature 003. JWT continua sem exigência de perfis, e nenhuma modificação de produção, seed, encoder ou configuração JWT foi feita. SC-006 global e jornadas após rejeições permanecem dependentes das tarefas futuras; não confundidos com a conclusão específica de T044.

Somente LoginControllerTest.java alterado em código nesta etapa; quickstart.md acrescido e tasks.md com somente T044 marcada. Conteúdo inteiro dos 13 cenários/assertions anteriores conferido contra cópia inicial; histórico do quickstart preservado em bytes. Snapshot de 156 rastreados confere apenas essas três diferenças incrementais; alterações e evidências de T042–T043, fontes produtivos/recursos e demais arquivos preservados. Diff incremental e acumulado revisados; `git diff --check` com exit 0, sem erros de whitespace (avisos LF/CRLF não são falhas). Links/conteúdo das novas evidências conferidos e target ignorado. [Revisão final/preservação/Git](../../target/harness-validation/feature-004-t044-2026-10-10/final-review.json).

Raiz/branch/HEAD mantidos. Git final: **seis rastreados modificados, nenhum não rastreado e índice vazio**: cinco alterações anteriores preservadas + LoginControllerTest.java. **T001–T044 [X] (44), T045–T055 abertas (11)**. U1/U2 pendentes, sem aplicação. Não executada T045+, suíte completa/CI, stage, commit, push, merge ou troca de branch.


## Execução delimitada de T045 — H2, advice, JWT e BCrypt (2026-10-10)

Escopo autorizado: exclusivamente T045, sem iniciar T046. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Estado inicial: seis rastreados modificados de T042–T044, nenhum não rastreado e índice vazio. Preservadas essas alterações e suas evidências; snapshot SHA-256 dos 156 rastreados e cópia dos reports Surefire anteriores feitos antes da edição/execução. [Git inicial](../../target/harness-validation/feature-004-t045-2026-10-10/git-before.json).

Skill speckit-implement aplicada somente a T045. Prerequisitos PowerShell `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit 0, FEATURE_DIR correto. Checklist requirements.md 16/16, zero abertos, sem edição. Conferidos T045/dependência T041, US3.3/4, FR-015, SC-006, plano/pesquisa/modelo e contratos anteriores products-openapi.yaml, product-detail-openapi.yaml e login-openapi.yaml; preservado o contrato de pedidos. Ignores Java suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Extensões e cobertura por camada

**H2ConsoleSecurityIntegrationTest: 15 aprovações (um caso anterior + 14 novos).** O caso anterior foi preservado integralmente, inclusive a assertion SAMEORIGIN. A classe passou a SpringBootTest RANDOM_PORT para acrescentar prova HTTP do servlet H2 real; o MockMvc anterior sozinho não despacha esse servlet e não comprova seu acesso. Filtros habilitados e reais, sem mocks de autenticação, serviço ou decoder. Dois contextos completos, raiz e `/api`; o contexto adicional usa banco H2 próprio por DynamicPropertySource, mantendo perfil, seed e configuração compartilhados intactos. Nenhuma correção de impedimento de contexto necessária.

- Duas verificações de configuração: console habilitado, path `/h2-console`, um JakartaWebServlet com mapping `/h2-console/*`, context path correto, chain pública contendo AuthorizationFilter e sem BearerTokenAuthenticationFilter/CsrfFilter. Trata-se de inspeção da configuração produtiva carregada, sem desabilitar filtros pelo teste.
- Doze GETs HTTP efetivos: página inicial, login.jsp e stylesheet.css × sem Authorization/com `Bearer not-a-valid-jwt` × raiz/`/api`. Todos 200, SAMEORIGIN único, MIME HTML/CSS e conteúdo, URI externa correta, sem WWW-Authenticate ou redirect. Cliente não segue redirects. Oito testcase pertencem à classe externa e sete à nested ServletContextPath. Surefire agregou os 15 no XML da nested e deixou o XML externo com zero; os testcase foram contados uma única vez.

**GlobalExceptionHandlerRegressionTest: 84 aprovações (12 invocações anteriores + 72 novas).** Mantidos todos os cenários, helpers e assertions anteriores, incluindo o controle de falha pública não traduzida como pedido. Continua WebMvcTest com os imports existentes SecurityConfig/OrderClockConfig/OrderSecuritySliceTestConfig e serviços mockados; não foi trocado por contexto completo. Cada caso novo seleciona a chain pública real do slice, percorre os filtros, verifica SAMEORIGIN e ausência de WWW-Authenticate. Decoder/resolução do suporte são mocks de preparação e não constituem prova JWT/login persistido.

| Matriz nova do advice | Casos | Comportamento preservado |
| --- | ---: | --- |
| Detalhes 404 | 8 | `Not Found`, `Product not found` e mensagem customizada anterior; exatamente status/error/message/path, URI externa sem query. |
| Catálogo 400 | 12 | page −1, size 0/51: mensagens exatas `page must be greater than or equal to 0` e `size must be between 1 and 50`; sem execução dos serviços. |
| Validação genérica fora de login/pedidos | 4 | Probe de teste conserva `Bad Request` / `Validation failed`, envelope de quatro campos e path externo. |
| Entrada de login 400 | 20 | Bean Validation, JSON malformado, name numérico, campo extra e corpo ausente: `Bad Request` / `Invalid login data`, envelope exato, sem eco sensível ou chamada ao serviço. |
| Login 401 | 8 | Nome desconhecido/senha incorreta induzidos no serviço mockado: `Unauthorized` / `Invalid credentials`, envelope exato, confidencialidade e nenhum challenge específico de pedidos. |
| Login 500 | 4 | AuthenticationProcessingException mockada com canary sensível: `Internal Server Error` / `Authentication could not be completed`, sem causa/hash/credencial/token no corpo. |
| Falha pública não relacionada | 4 | Mesmo IllegalStateException permanece causa raiz; não transformado artificialmente em 500 de registro de pedido. |
| Conversão MVC anterior | 12 | page/size/id não numéricos mantêm 400 com MethodArgumentTypeMismatchException, URI externa e ausência das mensagens de pedidos; não se inventa envelope de quatro campos para esses casos. |
| **Total novo** | **72** | Cada cenário cruza raiz/`/api` e sem token/Bearer inválido: 36 por modo de header, 36 por montagem. |

**JwtConfigurationTest: seis aprovações, fonte e assertions sem alteração.** Context runner carrega configuração real: Base64 inválido, chave decodificada curta, TTL zero/negativo/subsegundo causam falha de inicialização esperada pelo teste; configuração válida contém exatamente um encoder/decoder e emite/decodifica token HS256 não vazio com sub previsto. Esses cinco contextos deliberadamente rejeitados não são erros de preparação da execução JUnit.

**PasswordEncoderConfigurationTest: uma aprovação, fonte e assertions sem alteração.** Context runner com PasswordEncoder real único e BCrypt: hash difere do texto, matches(raw, encoded) verdadeiro. Não simula encoder nem altera configuração/chave/TTL/seed de produção.

### Execuções atuais, seriais e restritas

Na raiz Windows/PowerShell, somente estes quatro comandos de teste, nesta ordem:

```powershell
.\mvnw.cmd "-Dtest=H2ConsoleSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=JwtConfigurationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=PasswordEncoderConfigurationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Classe | Início → término (UTC−03:00, 2026-10-10) | Casos | Falhas | Erros | Ignorados | Exit / resultado |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| H2ConsoleSecurityIntegrationTest (inclui nested) | 18:13:15.310 → 18:14:14.165 | 15 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| GlobalExceptionHandlerRegressionTest | 18:14:22.048 → 18:14:49.743 | 84 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| JwtConfigurationTest | 18:14:53.888 → 18:15:35.680 | 6 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| PasswordEncoderConfigurationTest | 18:18:36.673 → 18:19:18.989 | 1 | 0 | 0 | 0 | 0 / BUILD SUCCESS |
| **Total selecionado de T045** | **Quatro invocações seriais** | **106** | **0** | **0** | **0** | **Todos exit 0** |

Java `21.0.12.1` observado nos XMLs; Maven 3.9.16 selecionado pelas propriedades do Wrapper. Cinco XMLs novos, 106 testcase efetivos, somente as quatro classes e a nested H2 nas linhas Running. Recompilação não significa execução das demais classes. [Resultado H2](../../target/harness-validation/feature-004-t045-2026-10-10/H2ConsoleSecurityIntegrationTest-result.json), [resultado advice](../../target/harness-validation/feature-004-t045-2026-10-10/GlobalExceptionHandlerRegressionTest-result.json), [resultado JWT](../../target/harness-validation/feature-004-t045-2026-10-10/JwtConfigurationTest-result.json) e [resultado BCrypt](../../target/harness-validation/feature-004-t045-2026-10-10/PasswordEncoderConfigurationTest-result.json). Cada resultado liga comando/horários/exit/contagens; consoles e XML/TXT arquivados no mesmo diretório, sem sobrescrever execuções anteriores.

**GREEN: SAMEORIGIN/acesso público/configuração H2 e contratos anteriores do advice/JWT/BCrypt preservados após as mudanças existentes de chain/advice. Nenhuma regressão comportamental observada, erro de preparação novo ou correção produtiva. T045 marcada [X] somente após entrega e execução válida das quatro classes.**

### Limitações, preservação e revisão final

Estas 106 aprovações são de classes selecionadas, não suíte completa, CI ou conclusão global de SC-006/US3.4. Jornadas após rejeições de pedidos e fechamento integrado continuam em T046+. H2 prova transporte HTTP local, servlet e filtros reais; não executa login de banco pelo console, comandos SQL ou renderização de frames em navegador. O advice prova tradução HTTP de falhas com serviços mockados, não nova falha física de banco nem login/JWT real. Configuração JWT/BCrypt real é uma camada distinta; as evidências reais de login/conta/claims já revisadas em T044 permanecem históricas e não foram somadas ao total atual.

Histórico integral do quickstart preservado em bytes, incluindo T040 (411), T041 (133 e os 13 erros iniciais de preparação), T042 (44), T043 (38) e T044 (83); As baterias de T040/T041 e as classes de T042–T044 não foram repetidas; somente os quatro alvos próprios de T045 foram executados. Snapshot inicial confirma somente quatro alterações incrementais: H2ConsoleSecurityIntegrationTest.java, GlobalExceptionHandlerRegressionTest.java, este quickstart e checkbox T045 em tasks.md. Arquivos/evidências anteriores, produção, recursos/seed, segurança, JWT e encoder preservados. Diff de código e acréscimo documental revisados; `git diff --check` exit 0, sem erros de whitespace, com os avisos existentes LF/CRLF. Links das novas evidências conferidos; target continua ignorado. [Revisão final, preservação e Git](../../target/harness-validation/feature-004-t045-2026-10-10/final-review.json).

Raiz/branch/HEAD mantidos. Git final: **oito rastreados modificados, nenhum não rastreado e índice vazio** (seis anteriores + dois testes desta tarefa). **T001–T045 [X] (45), T046–T055 abertas (10)**. U1/U2 continuam pendentes. Não executada T046+, suíte completa ou CI; sem stage, commit, push, merge ou troca de branch.


## Execução delimitada de T046 — jornadas públicas após pedidos rejeitados (2026-10-10)

Exclusivamente T046, parada antes de T047. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Estado inicial: oito rastreados modificados de T042–T045, nenhum não rastreado, índice vazio. Todas as alterações e evidências anteriores preservadas. Snapshot SHA-256 dos 156 rastreados, cópia integral dos reports Surefire anteriores e cópias dos documentos feitas antes da edição/execução; fontes e teste novo também capturados imediatamente antes do Maven. [Git inicial](../../target/harness-validation/feature-004-t046-2026-10-10/git-before.json).

Skill speckit-implement aplicada no escopo autorizado. Prerequisitos `powershell.exe -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit 0, FEATURE_DIR correto. Checklist requirements.md 16/16, zero abertos, sem alteração. Revisados T046/dependências T042–T045, US3.4, FR-012/015, SC-004/006, desenho/modelo/pesquisa, contratos orders-openapi.yaml e anteriores de catálogo/detalhes/login, infraestrutura existente e evidências de rollback. Ignores Java suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks executáveis.

### Teste entregue e isolamento

Criado OrderPublicRegressionIntegrationTest: SpringBootTest completo, perfil test, MockMvc com filtros reais habilitados, open-in-view=false e H2 próprio `order-test-<UUID>` via OrderTestConfig. Sem @Transactional de teste, jwt(), WithMockUser, decoder/serviço mockado ou desligamento de filtros. Reutiliza OrderHttpTestSupport para login real/decoder/clock controlado e preparação/limpeza de contas confirmadas em transação própria. Nenhuma fonte anterior alterada, nenhuma correção de preparação necessária.

Usa produtos da seed existente, sem criar/alterar/remover produtos ou categorias. Os 52 produtos, três categorias, três vínculos e conta demo são comparados com snapshots de leitura independente. Somente contas/perfis próprios são acrescentados no banco isolado. A conta usada nas consultas de login público não tem perfis, antes e depois do sucesso. Limpeza herdada remove apenas linhas próprias e roda após as assertions; não constitui a prova de ausência/rollback. Instrumentação é desarmada em finally após a requisição e novamente no teardown, antes da limpeza, inclusive se houver falha de assertion.

Cada uma das sete causas é executada na raiz e com contextPath/servletPath `/api` no MockMvc: **14 casos JUnit**. Na mesma instância de contexto, cada caso segue a ordem: login real de preparação → POST /orders real → snapshot independente após conclusão → assertions do erro/persistência e desarme → catálogo/detalhes/login sem token → as mesmas verificações com Bearer inválido irrelevante. Contexto, EntityManagerFactory e FilterChainProxy são conferidos por identidade; um único startup Spring observado, sem reinicialização entre rejeição e jornadas.

### Cobertura por rejeição e prova de persistência

| Status / causas | Casos raiz + /api | Mensagem contratual | Prova após requisição, antes da limpeza |
| --- | ---: | --- | --- |
| 400, quantity zero entre linhas válidas | 2 | `Invalid order data` | Zero inserts de pedido/item, delta 0/0 e baseline completo restaurado. |
| 401, sem token e Bearer malformado | 4 | `Authentication required or invalid` | Chain real de pedidos/BearerTokenAuthenticationFilter, challenge Bearer; zero inserts e baseline idêntico. |
| 403, JWT real de conta sem CLIENT/ADMIN | 2 | `Order registration not permitted` | Conta existe e login real foi 200; perfis do banco vazios, filtro real rejeita; zero inserts e baseline idêntico. |
| 404, id Long.MAX_VALUE ausente entre produtos existentes | 2 | `Product not found` | Ausência do id confirmada por JDBC; nenhuma linha válida salva separadamente; zero inserts e baseline idêntico. |
| 422, produto repetido entre linhas | 2 | `Duplicate product in order` | Não consolida duplicidade, zero inserts e baseline idêntico. |
| 500, processamento após saveAndFlush real | 2 | `Order registration could not be completed` | Três inserts reais completados dentro da transação, depois falha, depois baseline completo restaurado por conexão independente. |
| **Total** | **14** | **Envelopes exatamente status/error/message/path, status/error/mensagem fixos e path externo `/orders` ou `/api/orders`** | **Zero pedidos/itens remanescentes em todos os casos, sem 201/Location.** |

A leitura posterior usa DriverManagerDataSource distinto do datasource instrumentado, conexão nova com autoCommit=true e sem vínculo com a transação da aplicação; não lê entidades do persistence context. Compara todas as linhas/colunas de tb_order e tb_order_item, não somente contagens da conta. Baseline inicial 0 pedidos/0 itens e estado posterior idêntico; novos snapshots depois de cada modo público também são idênticos. Os 12 casos anteriores à persistência registram zero inserts; os dois 500 registram três inserts cada, sem confundir flush com commit ou limpeza com rollback.

**500 reutiliza o padrão válido de T030 em OrderRollbackIntegrationTest, sem executar essa classe.** Importa SqlObservationConfig/RepositorySpyConfig e usa SqlInsertProbe/PostFlushProbe existentes. Spy de teste encaminha todos os métodos ao proxy Spring Data/JPA real; nenhum save é stubado. Confere que o serviço Spring real/proxy recebeu esse spy e que o repositório delegado é real/proxy, não mock. Driver JDBC retorna sucesso em um insert tb_order e dois tb_order_item (affected=1, transação ativa, autoCommit=false). Antes da falha, leitura na conexão vinculada vê pedido e ambos os itens, buyer correto, produtos 1/2, positions 0/1, quantities 2/3 e preços da seed. saveAndFlush retorna, o probe lança a mesma PostFlushTestFailure verificada no resultado MVC e o serviço não conclui o registro. Requisição retorna 500 seguro; leitura independente encontra 0/0. PostFlushProbe buyer/transactionJdbc são null antes das jornadas públicas. **Seis inserts reais completados no total, dois rollbacks integrais comprovados.**

### Contratos públicos depois de cada rejeição

Cada caso repete **21 requisições públicas sem token e 21 com `Authorization: Bearer not-a-valid-jwt`**. Todas selecionam a chain pública real com AuthorizationFilter e sem BearerTokenAuthenticationFilter, percorrem filtros, conservam SAMEORIGIN e não emitem WWW-Authenticate. O Bearer inválido é enviado ao endpoint, sem simular identidade autenticada.

| Jornada por modo de header | Requisições | Assertions |
| --- | ---: | --- |
| Catálogo | 9 | Seis páginas 200: padrão 0/12, explícita 1/7, limite size=1, duas páginas size=50 cobrindo todos os 52 ids e página 99 vazia. Shape exato da página/item, metadados inteiros, name ASC/id ASC inclusive empate, valores/image e preço numérico do snapshot JDBC. Três rejeições 400 de page −1/size 0/51, envelopes e mensagens anteriores exatos, path externo sem query. |
| Detalhes | 4 | Produtos 1/2/3 com múltiplas/uma/nenhuma categoria; seis campos exatos, valores reais, image, preço numérico, nomes de categorias sem repetição. Produto ausente mantém 404/Not Found/Product not found e path externo. |
| Login | 8 | Sucesso real de conta persistida sem perfis: exatamente accessToken/tokenType Bearer, token não vazio, decoder real confirma HS256/sub/name/iat/exp e duração TTL. BCrypt real matches(raw, hash), sem roles obrigatórios/claims de senha/hash/segredo ou material da chave. Cinco entradas inválidas (blank, JSON malformado, name numérico, roles extra, campos ausentes) mantêm 400/Invalid login data; nome desconhecido/senha incorreta mantêm 401/Invalid credentials com corpos idênticos e envelope exato. |
| **Por modo / por caso / total** | **21 / 42 / 588** | **28 blocos públicos aprovados, no mesmo contexto das respectivas rejeições.** |

Contagens HTTP da execução: 14 rejeições de pedidos, 14 logins reais de preparação e 588 requisições públicas posteriores (**616 requisições ao MockMvc**). Dentro das 588: catálogo 252, detalhes 112, login 224, incluindo 28 sucessos de login real posteriores à rejeição. Não são 616 testes JUnit; o relatório contém 14 casos parametrizados, cada qual comprova a sequência inteira.

### Execução atual

Somente este comando de teste pelo Maven Wrapper, na raiz Windows/PowerShell:

```powershell
.\mvnw.cmd "-Dtest=OrderPublicRegressionIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Início → término (UTC−03:00) | Casos | Falhas | Erros | Ignorados | Exit / resultado |
| --- | ---: | ---: | ---: | ---: | --- |
| 2026-10-10 18:30:18.171 → 18:31:37.331 | **14** | **0** | **0** | **0** | **0 / BUILD SUCCESS** |

Java `21.0.12.1` observado no XML; Maven 3.9.16 pelo Wrapper. Um XML novo/14 testcase e somente OrderPublicRegressionIntegrationTest na linha Running. Compilar fontes/importar suporte de rollback não executa as classes anteriores. [Comando/horários/exit/resultado](../../target/harness-validation/feature-004-t046-2026-10-10/result.json), [console com os 14 registros de persistência, dois de rollback e 28 blocos públicos](../../target/harness-validation/feature-004-t046-2026-10-10/OrderPublicRegressionIntegrationTest-console.txt), [XML](../../target/harness-validation/feature-004-t046-2026-10-10/reports/TEST-com.devsuperior.dscommerce.controllers.OrderPublicRegressionIntegrationTest.xml) e [inventário da cobertura](../../target/harness-validation/feature-004-t046-2026-10-10/coverage-evidence.json). XML/TXT anteriores copiados e preservados; o histórico não foi reescrito como nova execução.

**GREEN: jornadas públicas preservadas após todas as rejeições 400/401/403/404/422/500 previstas em T046, com persistência e segurança reais. Nenhuma regressão observada, impedimento de compilação/contexto ou correção produtiva necessária. T046 marcada [X] somente após execução válida e conferência dos critérios/provas.** Os 500 esperados e induzidos pelo probe são cenários aprovados, não um RED de regressão.

### Limitações e revisão final

Resultado local de uma classe selecionada, não suíte completa/CI nem aprovação global de SC-004/006 ou checkpoint integrado de US3. T047+ continua pendente. São representantes das seis famílias de rejeição, não repetição de todas as variantes JWT, entradas/negócio/limites de US2 ou de todas as classes anteriores. Catálogo vazio é página fora do intervalo mantendo 52 produtos; não se esvazia a seed. Login 500 não é provocado novamente após cada rejeição; suas evidências anteriores de tradução segura permanecem em T044/T045. O 500 desta classe é falha de processamento após flush e antes da conclusão do corpo do serviço; não nova prova SQL tardia, beforeCommit ou falha física de commit. As três provas históricas de rollback permanecem distintas e preservadas. MockMvc usa contexto/filtros reais, mas contextPath `/api` é montagem da requisição de teste, sem servidor HTTP físico/navegador/outro banco.

Fontes anteriores/assertions, produção, configuração de segurança/JWT/encoder e recursos/seed mantidos por SHA-256; teste novo coincide com a revisão executada. Histórico integral deste quickstart preservado em bytes, incluindo T040 411, T041 133 e seus 13 erros iniciais de preparação, T042 44, T043 38, T044 83 e T045 106. Nenhuma dessas baterias ou classes de T042–T045 foi repetida. Somente três arquivos desta etapa: teste novo, acréscimo a quickstart.md e checkbox T046 em tasks.md. Diff e arquivo novo revisados; `git diff --check` exit 0. Conferência do novo arquivo com `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: exit 1 pela diferença, zero diagnósticos, newline presente e sem espaços finais. Links novos conferidos; target continua ignorado. [Auditoria final/preservação/Git](../../target/harness-validation/feature-004-t046-2026-10-10/final-review.json).

Raiz/branch/HEAD mantidos. Estado final: **oito rastreados modificados anteriores, um novo teste não rastreado, índice vazio**. **T001–T046 [X] (46), T047–T055 abertas (9)**. U1/U2 continuam pendentes. Não executada T047+, suíte completa ou CI; sem stage, commit, push, merge ou troca de branch.


## Execução T047 — bateria explícita e serial de regressões (2026-10-10)

Exclusivamente T047, parada antes de T048. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Confirmados pelo Git antes da execução: oito arquivos rastreados modificados e OrderPublicRegressionIntegrationTest.java não rastreado, todos de T042–T046; índice vazio. Nenhuma alteração de produção, testes, seed, segurança, JWT, encoder ou configuração nesta etapa. Checklist de requisitos: 16/16 marcada, nenhuma pendência. [Git inicial e inventário](../../target/harness-validation/feature-004-t047-2026-10-10/git-before.json).

### Seleção confirmada, sem duplicatas

T047 exige T042–T046 e todas as classes existentes listadas neste quickstart. Foram cruzados os dois comandos seletivos da seção inicial (seis classes de pedidos e quinze regressões públicas), os registros posteriores de resolução/perfis/suporte, segurança/JWT, agregado/DTO/contexto e o teste novo de T046. Resultado: 32 nomes reais e distintos, cada um com arquivo Java, package e referências por número de linha do quickstart confirmados antes do Maven. São nove classes de T042–T046, onze outras regressões/suporte e doze classes restantes já citadas nos registros. A lista coincide com o inventário atual de classes de teste, mas foi selecionada explicitamente pelos artefatos, sem descoberta indiscriminada pelo Maven; auxiliares e configurações sem testes não são alvos. A seção inicial permanece como planejamento histórico, sem reescrever suas alegações de então. [Lista selecionada, fontes, linhas e SHA-256](../../target/harness-validation/feature-004-t047-2026-10-10/selection.json).

Comandos efetivamente executados, exatamente na ordem abaixo, um processo Maven por classe, sem paralelismo JUnit. Java 21.0.12.1 e Maven 3.9.16 pelo Wrapper; a consulta de versão foi registrada separadamente, sem executar testes adicionais. Não usados comando de suíte completa, wildcard, seleção por pacote, CI ou T050:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=ProductControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=PublicProductDetailSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=LoginControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=H2ConsoleSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerRegressionTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=JwtConfigurationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=PasswordEncoderConfigurationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderPublicRegressionIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=ProductCatalogServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=ProductRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=JwtTokenServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=AuthenticationServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=AuthenticationInternalFailureTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=AuthenticationSensitiveLoggingTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=AuthenticatedAccountServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=AuthenticatedAccountServiceIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=UserAccountRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=UserAccountRoleRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderTestSupportTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderServiceTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderControllerTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRepositoryTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderServiceContextTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderJwtAuthenticationPipelineTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderJwtAuthenticationConverterTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderDtoContractTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderAggregateTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderSecurityChainTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

### Resultados atuais por classe

| Ordem | Classe selecionada | Testes | Aprovados | Falhas | Erros | Ignorados | Exit code | Resultado | Início → fim (UTC−03:00) |
|-------|--------------------|--------|-----------|--------|-------|-----------|-----------|-----------|-------------------------|
| 1 | `PublicCatalogSecurityIntegrationTest` | 33 | 33 | 0 | 0 | 0 | 0 | GREEN | 18:48:53 → 18:49:29 |
| 2 | `ProductControllerTest` | 11 | 11 | 0 | 0 | 0 | 0 | GREEN | 18:49:30 → 18:49:54 |
| 3 | `PublicProductDetailSecurityIntegrationTest` | 38 | 38 | 0 | 0 | 0 | 0 | GREEN | 18:49:54 → 18:50:29 |
| 4 | `LoginControllerTest` | 83 | 83 | 0 | 0 | 0 | 0 | GREEN | 18:50:30 → 18:50:57 |
| 5 | `H2ConsoleSecurityIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 0 | GREEN | 18:50:57 → 18:51:31 |
| 6 | `GlobalExceptionHandlerRegressionTest` | 84 | 84 | 0 | 0 | 0 | 0 | GREEN | 18:51:31 → 18:51:57 |
| 7 | `JwtConfigurationTest` | 6 | 6 | 0 | 0 | 0 | 0 | GREEN | 18:51:57 → 18:52:36 |
| 8 | `PasswordEncoderConfigurationTest` | 1 | 1 | 0 | 0 | 0 | 0 | GREEN | 18:52:36 → 18:53:08 |
| 9 | `OrderPublicRegressionIntegrationTest` | 14 | 14 | 0 | 0 | 0 | 0 | GREEN | 18:53:08 → 18:54:08 |
| 10 | `ProductCatalogServiceTest` | 6 | 6 | 0 | 0 | 0 | 0 | GREEN | 18:54:08 → 18:54:24 |
| 11 | `ProductRepositoryTest` | 6 | 6 | 0 | 0 | 0 | 0 | GREEN | 18:54:24 → 18:54:50 |
| 12 | `JwtTokenServiceTest` | 1 | 1 | 0 | 0 | 0 | 0 | GREEN | 18:54:50 → 18:55:21 |
| 13 | `AuthenticationServiceTest` | 4 | 4 | 0 | 0 | 0 | 0 | GREEN | 18:55:21 → 18:55:38 |
| 14 | `AuthenticationInternalFailureTest` | 4 | 4 | 0 | 0 | 0 | 0 | GREEN | 18:55:38 → 18:55:55 |
| 15 | `AuthenticationSensitiveLoggingTest` | 4 | 4 | 0 | 0 | 0 | 0 | GREEN | 18:55:55 → 18:56:12 |
| 16 | `AuthenticatedAccountServiceTest` | 8 | 8 | 0 | 0 | 0 | 0 | GREEN | 18:56:12 → 18:56:31 |
| 17 | `AuthenticatedAccountServiceIntegrationTest` | 6 | 6 | 0 | 0 | 0 | 0 | GREEN | 18:56:31 → 18:57:05 |
| 18 | `UserAccountRepositoryTest` | 4 | 4 | 0 | 0 | 0 | 0 | GREEN | 18:57:05 → 18:57:34 |
| 19 | `UserAccountRoleRepositoryTest` | 11 | 11 | 0 | 0 | 0 | 0 | GREEN | 18:57:34 → 18:58:10 |
| 20 | `OrderTestSupportTest` | 5 | 4 | 1 | 0 | 0 | 1 | PREPARATION_FAILURE | 18:58:10 → 18:59:01 |
| 21 | `OrderServiceTest` | 42 | 42 | 0 | 0 | 0 | 0 | GREEN | 18:59:01 → 18:59:20 |
| 22 | `OrderControllerTest` | 141 | 141 | 0 | 0 | 0 | 0 | GREEN | 18:59:20 → 19:00:05 |
| 23 | `OrderRepositoryTest` | 23 | 23 | 0 | 0 | 0 | 0 | GREEN | 19:00:05 → 19:00:43 |
| 24 | `OrderSecurityIntegrationTest` | 185 | 185 | 0 | 0 | 0 | 0 | GREEN | 19:00:43 → 19:02:06 |
| 25 | `OrderRegistrationIntegrationTest` | 9 | 9 | 0 | 0 | 0 | 0 | GREEN | 19:02:06 → 19:02:42 |
| 26 | `OrderRollbackIntegrationTest` | 9 | 9 | 0 | 0 | 0 | 0 | GREEN | 19:02:42 → 19:03:25 |
| 27 | `OrderServiceContextTest` | 4 | 3 | 1 | 0 | 0 | 1 | BEHAVIORAL_FAILURE | 19:03:25 → 19:04:01 |
| 28 | `OrderJwtAuthenticationPipelineTest` | 25 | 25 | 0 | 0 | 0 | 0 | GREEN | 19:04:01 → 19:04:20 |
| 29 | `OrderJwtAuthenticationConverterTest` | 48 | 48 | 0 | 0 | 0 | 0 | GREEN | 19:04:20 → 19:04:40 |
| 30 | `OrderDtoContractTest` | 36 | 36 | 0 | 0 | 0 | 0 | GREEN | 19:04:40 → 19:05:03 |
| 31 | `OrderAggregateTest` | 18 | 18 | 0 | 0 | 0 | 0 | GREEN | 19:05:03 → 19:05:17 |
| 32 | `OrderSecurityChainTest` | 20 | 20 | 0 | 0 | 0 | 0 | GREEN | 19:05:18 → 19:05:57 |
| **Total desta bateria** | **32 classes** | **904** | **902** | **2** | **0** | **0** | por classe acima | **30/32 GREEN** | |

A consolidação usa apenas os XML/TXT novos copiados de cada uma destas 32 execuções. Casos efetivos `testcase` foram contados uma única vez e confrontados com o resumo final de cada console Maven; atributos agregados de suites aninhadas não são somados novamente. A coleta auxiliar inicial do catálogo incluiu um item nulo de XML externo vazio (34); a recontagem comprovou **33** casos, de acordo com XML e console, e foi guardada em result-reviewed.json, preservando a coleta original. Isso é correção de metadados da coleta, não falha do teste/contexto nem uma nova execução. [Consolidação conferida](../../target/harness-validation/feature-004-t047-2026-10-10/summary.json).

Não somados os 411 de T040, 133 de T041, 44 de T042, 38 de T043, 83 de T044, 106 de T045 ou 14 de T046 históricos. As mesmas classes presentes nesta seleção contribuem apenas com sua execução atual. Nenhuma classe repetida nesta bateria.

### Cobertura e distinção das camadas

- Catálogo e detalhes: contexto completo/filtros reais, sem token e com Bearer inválido irrelevante; shape/valores/image, ordenação name ASC/id ASC, 52 produtos, paginação padrão/explícita, vazio, categorias únicas, identificadores e erros/mensagens anteriores. ProductControllerTest e GlobalExceptionHandlerRegressionTest conservam seus contratos e assertions de slice; ProductCatalogServiceTest é unidade, ProductRepositoryTest usa JPA real.
- Login: LoginControllerTest continua slice com serviço mockado, 83 casos de entrada estrita, 200/400/401/500, mensagens, tokenType/accessToken, Bearer irrelevante e context paths. Token mockado não comprova assinatura/claims/conta persistida. JwtTokenServiceTest e integrações de resolução/suporte/chain/pedidos exercitam os respectivos componentes reais; a integração pública pós-rejeição faz login real de conta persistida sem perfis e confere assinatura HS256, sub/name/iat/exp/TTL e ausência de claims sensíveis/perfis obrigatórios.
- Autenticação, falha interna e logging: unidades existentes de AuthenticationService, AuthenticationInternalFailure e AuthenticationSensitiveLogging preservadas; resolução interna unitária e integrada, repositórios de conta/perfis, configuração JWT e BCrypt real também executados. Falhas esperadas de configuração inválida em JwtConfigurationTest são cenários aprovados por assertions, não impedimentos de preparação desta bateria.
- H2: teste com servidor real/porta aleatória e infraestrutura de segurança, acesso previsto e SAMEORIGIN na raiz e em /api. Advice/slices preservam envelopes de login/catálogo/detalhes e paths externos; montagem de contextPath em MockMvc não equivale a deploy físico para essas outras classes.
- Pós-rejeição: OrderPublicRegressionIntegrationTest tem 14 casos (sete causas × raiz/api), 400/401 sem token/401 token malformado/403/404/422/500, status/envelope/mensagem exatos e baseline de pedidos/itens restaurado por conexão independente após requisição e antes da limpeza. São 28 blocos públicos, 588 requisições públicas após rejeição, além de 14 rejeições e 14 logins preparatórios, total 616 requisições HTTP via MockMvc; esses números não são testes JUnit adicionais. Mesmo contexto/filtros por sequência, um startup; instrumentação desarmada antes das jornadas públicas; seed pública preservada. As duas falhas 500 exigem três INSERTs reais concluídos cada, saveAndFlush retornado e rollback integral. [Provas atuais sanitizadas](../../target/harness-validation/feature-004-t047-2026-10-10/public-regression-proof.txt).
- Pedidos e infraestrutura já previstos: serviço/controller/repositório, sucesso integrado, JWT/converter/pipeline/chain, agregado/DTO/contexto e suporte existentes executados integralmente por nome explícito. OrderRollbackIntegrationTest mantém três provas distintas: falha SQL tardia, falha de processamento após flush real e falha em beforeCommit antes do commit físico, mais controle positivo visível após commit. Rollback automático de teste de repositório e mocks não substituem essas provas independentes. [Observações atuais de rollback](../../target/harness-validation/feature-004-t047-2026-10-10/rollback-proof.txt).

### Classificação, preservação e limites

**Bateria concluída, resultado não integralmente GREEN: 904 testes executados, 902 aprovações, 2 falha(s), 0 erros JUnit, 0 ignorados; 30/32 classes GREEN.** Resultados não GREEN são preservados, sem transformá-los em aprovação. Causas e casos constam em summary.json e nos relatórios originais; deverão ser avaliados na etapa seguinte autorizada. T047 registra a execução concluída, não aprovação da classe falha; T048 não foi executada.

**Falha de preparação/configuração confirmada em OrderTestSupportTest**: cinco testes, quatro aprovados e uma falha (AssertionError), exit code 1; zero erros/ignorados. O método shouldReuseRealEncodersAndDecoderWithTheTestProfileKey falha na linha 238, em hasSingleBean(Clock.class), porque o contexto iniciado contém os beans orderClock e orderTestClock. OrderClockConfig declara o Clock produtivo UTC; OrderTestConfig declara o ControlledClock @Primary. O WebApplicationContextRunner carrega DscommerceApplication e OrderTestConfig, portanto ambos existem legitimamente nesse preparo; @Primary seleciona a injeção, mas não remove o outro bean para a assertion de unicidade. hasNotFailed e unicidade de encoder/decoder/PasswordEncoder anteriores passam; a assertion de Clock impede chegar ao login/JWT/conta persistida desse método. Não é erro de startup ou impedimento de ambiente, nem RED comportamental de login, catálogo, detalhes ou pedidos. A classificação é preparação/configuração, embora Surefire a registre como failure e não error. Nenhuma assertion enfraquecida, bean removido ou alteração aplicada. As classes independentes seguintes continuaram sem repetir esta execução. [Causa confirmada e limites da evidência](../../target/harness-validation/feature-004-t047-2026-10-10/cause-review.json).

As quatro aprovações desta classe contam individualmente no total; a classe como conjunto permanece não GREEN. O login real desse método não foi comprovado agora, e suas evidências históricas não viram aprovação atual. A execução atual de OrderPublicRegressionIntegrationTest mantém a prova própria de login real sem perfis e assinatura/claims. Zero classes não executadas; os demais resultados estão na tabela, sem atribuir sucesso à assertion interrompida.

**RED comportamental executável em OrderServiceContextTest**, distinto de preparação: quatro testes, três aprovados e uma falha (AssertionError), exit code 1, zero erros/ignorados. shouldStopBeforeWritingWhenSubtotalOrSumExceedsDecimalCapacity (linha 184) chama o serviço/proxy real com subtotal além de DECIMAL(19,2), mas exige IllegalArgumentException; OrderService.exactMoney (linha 113) lança InvalidOrderException, motivo UNREPRESENTABLE_VALUE e mensagem fixa Order value cannot be represented. InvalidOrderException estende RuntimeException, sem herdar IllegalArgumentException. É divergência observável do tipo de exceção da chamada direta previsto na assertion legada, não falha de compilação/contexto. A implementação de US2/T034 explicita esse motivo de negócio e o contrato HTTP atual prevê 422 com essa mensagem; a falha, sozinha, não comprova regressão do envelope HTTP nem overflow aceito. Preservada como RED, sem mudar produção/expectativa ou fabricar falha. O segundo caso de overflow da soma e as assertions posteriores de zero INSERT/contagem/estado transacional nesse método não foram alcançados; não são apresentados como aprovados. Outros três métodos desta classe passaram. [Causas confirmadas das duas classes](../../target/harness-validation/feature-004-t047-2026-10-10/cause-review.json).

Evidências ignoradas pelo Git em `target/harness-validation/feature-004-t047-2026-10-10/`: selection.json, inventários/hash de entrada, cópias dos documentos, 74 reports anteriores em surefire-before/, diretório próprio de cada classe com console.txt/result.json e XML/TXT em reports/, summary.json, scripts auxiliares de coleta/conferência e diff completo. Cada relatório foi copiado antes da próxima invocação; relatórios antigos/stale não entram na contagem. Prefixo histórico integral do quickstart preservado por bytes; as evidências de T042–T046 foram verificadas por SHA-256 e nenhuma foi alterada. Os 13 erros iniciais de LoginControllerTest em T041 continuam registrados como falha de preparação por Clock ausente, distintos do resultado final daquele checkpoint e dos 83 aprovados agora.

Esta é uma bateria local explícita em processos separados, **não uma execução de suíte completa/CI**, nem prova de convivência de todos os testes num único processo/contexto. A coincidência com o inventário atual não substitui T050. Totais GREEN não provam cobertura de todo comportamento possível: mantidos os limites de T042–T046, sem falha física de commit, transporte perdido após commit, navegador/deploy/outro banco ou expansão silenciosa de casos. As três provas atuais de rollback continuam distintas; beforeCommit não é falha física do commit. A revisão global de US3/SC-006 permanece em T049. **U1 (conteúdo residual no JSON) e U2 (ativação de fixtures na demonstração) continuam pendentes e não aplicadas.**

Alterados nesta etapa somente quickstart.md por acréscimo e a checkbox T047 em tasks.md. Fontes, teste novo de T046, configurações e alterações preexistentes preservados por SHA-256. Diff incremental/acumulado e conteúdo do arquivo não rastreado conferidos; `git diff --check` exit 0 e `git diff --no-index --check -- NUL src/test/java/com/devsuperior/dscommerce/controllers/OrderPublicRegressionIntegrationTest.java` sem diagnósticos (exit 1 pelo arquivo diferente de NUL). [Conferência final](../../target/harness-validation/feature-004-t047-2026-10-10/final-review.json).

Raiz/branch/HEAD mantidos. Git final: oito rastreados modificados e um não rastreado de T046, índice vazio. **T001–T047 [X] (47); T048–T055 abertas (8); somente T047 marcada nesta etapa.** Sem T048+, T050, U1/U2, suíte completa, CI, stage, commit, push, merge ou troca de branch.


## Execução T048 — expectativas de Clock e overflow alinhadas (2026-10-10)

Exclusivamente T048, parada antes de T049. O usuário autorizou explicitamente acrescentar os dois alvos que falharam em T047: OrderTestSupportTest e OrderServiceContextTest. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Git inicial: oito rastreados modificados de T042–T047, teste novo de T046 não rastreado e índice vazio. Checklist 16/16, nenhuma pendência; script de pré-requisitos exit 0 e feature correta. Snapshot dos 157 arquivos do inventário, cópias dos dois testes/documentos, 700 arquivos de evidências T042–T047 hashados e 74 reports Surefire anteriores arquivados antes de editar. [Estado inicial](../../target/harness-validation/feature-004-t048-2026-10-10/git-before.json).

### Causas confirmadas antes das alterações

As duas fontes de entrada coincidem por SHA-256 com a revisão que falhou em T047; não se repetiu aquele RED. [Conferência das fontes RED](../../target/harness-validation/feature-004-t048-2026-10-10/red-source-confirmation.json).

- Clock: OrderTestSupportTest usa WebApplicationContextRunner com DscommerceApplication e OrderTestConfig. OrderClockConfig define orderClock UTC produtivo; OrderTestConfig define orderTestClock determinístico com @Primary. Os dois beans coexistem; @Primary escolhe a dependência por tipo, sem excluir o bean produtivo. A exigência antiga de hasSingleBean(Clock.class) era incompatível com essa preparação. A falha histórica ocorreu antes do login/JWT, não demonstrou regressão desses comportamentos.
- Overflow: spec US2.10/FR-008 exige rejeição integral sem perda/gravação; research D4 prevê overflow de subtotal/soma como 422 antes de gravar e D7 prevê InvalidOrderException para negócio. README/OpenAPI fixam Order value cannot be represented; a exceção vigente define UNREPRESENTABLE_VALUE. OrderService.exactMoney lança esse tipo/motivo quando precision > 19. A expectativa antiga de IllegalArgumentException no teste de chamada direta ficou incompatível com US2/T034. Não encontrada evidência de defeito produtivo que exigisse ampliar a correção.

### Correções limitadas aos dois testes

OrderTestSupportTest substitui somente a unicidade indevida de Clock por verificações explícitas de coexistência de orderClock/orderTestClock. Conserva unicidade de JwtEncoder/JwtDecoder/PasswordEncoder e a assertion de seleção por tipo. Confere o Clock produtivo distinto/UTC, o de teste no INITIAL_INSTANT determinístico e, por leitura do alvo do proxy OrderService real, que o campo clock efetivamente injetado é a mesma instância de teste. Ambos os beans/configurações foram preservados; nenhum mock ou remoção de Clock.

Todas as verificações reais posteriores foram mantidas: fixture persistida sem perfis; BCrypt real; AuthenticationService/login real; decoder/sub e ausência de claims sensíveis; POST /login com FilterChainProxy real, 200, tokenType/corpo e JWT decodificado; perfis ainda vazios e assinatura HS256 do token criado pela infraestrutura existente. Limpeza no finally preservada.

OrderServiceContextTest mantém os dois cenários sequenciais de overflow (subtotal e soma), agora com isInstanceOfSatisfying(InvalidOrderException.class), motivo UNREPRESENTABLE_VALUE e mensagem exata em cada caso. Não usa assertThrows genérico. Conserva integralmente as assertions posteriores de nenhuma instrução INSERT observada, zero pedidos da conta e nenhuma transação externa ativa; preservados também os outros três testes, fixtures, proxy, Clock produtivo e limpeza. Isso é rejeição anterior à persistência, não nova prova de rollback pós-SQL.

Nenhuma alteração de produção, seed, segurança, Jackson, JWT, encoder ou configuração compartilhada. Correções produtivas condicionais de chain/leitor/advice dispensadas para estas causas; não houve ajuste dos slices nem ampliação do escopo. Os imports e assertions são locais aos dois testes; nenhuma outra regressão diretamente afetada foi identificada, portanto não repetidas as outras 30 classes de T047.

### Execução atual e alcance das assertions

Somente os dois comandos abaixo, serialmente na raiz pelo Maven Wrapper:

```powershell
.\mvnw.cmd "-Dtest=OrderTestSupportTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderServiceContextTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

| Classe | Início → fim (UTC−03:00, 2026-10-10) | Testes/aprovações | Falhas | Erros | Ignorados | Exit / resultado |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| OrderTestSupportTest | 19:17:22.066 → 19:18:25.828 | **5/5** | 0 | 0 | 0 | **0 / BUILD SUCCESS** |
| OrderServiceContextTest | 19:18:33.207 → 19:19:03.872 | **4/4** | 0 | 0 | 0 | **0 / BUILD SUCCESS** |
| **Total atual selecionado** | dois comandos/classes | **9/9** | **0** | **0** | **0** | **ambos 0 / GREEN** |

Java 21.0.12.1 registrado nos XMLs; Maven 3.9.16 selecionado pelo Wrapper/propriedades preservadas, sem nova consulta de versão. Cada execução produziu um XML e um TXT novos, copiados para diretório próprio antes da próxima. Contagens conferidas entre testcase e resumo final do console; nenhum relatório stale ou classe fora dos dois alvos foi usado. [Suporte: comando/horários/contagens](../../target/harness-validation/feature-004-t048-2026-10-10/OrderTestSupportTest/result.json), [contexto: comando/horários/contagens](../../target/harness-validation/feature-004-t048-2026-10-10/OrderServiceContextTest/result.json).

Os XMLs confirmam sucesso, sem skip, dos métodos shouldReuseRealEncodersAndDecoderWithTheTestProfileKey e shouldStopBeforeWritingWhenSubtotalOrSumExceedsDecimalCapacity. Revisada sua sequência: sem retorno antecipado ou captura que suprima assertions; aprovação completa comprova que passaram os trechos antes interrompidos. No primeiro, coexistência/seleção/injeção de Clock e todos os checks reais de login/JWT; no segundo, ambas as rejeições com tipo/motivo/mensagem e as verificações finais de ausência de gravação. A evidência é o caso JUnit aprovado junto do fonte executado, sem adicionar marcadores artificiais ou simular autenticação. [Conferência das execuções/métodos alcançados](../../target/harness-validation/feature-004-t048-2026-10-10/verified-execution.json).

**GREEN dos dois alvos de T048: falhas resolvidas por atualização de expectativas antigas, mantendo contratos, assertions pertinentes e produção. Somente T048 marcada [X] após execução válida e documentação.** Nenhum impedimento de compilação, preparação/contexto ou ambiente nesta etapa.

### Histórico, limitações e revisão final

O resultado de T047 permanece intacto: 904 testes, 902 aprovações, duas falhas, 30/32 classes GREEN; seus XML/TXT/consoles, causas e classificações não foram reescritos como aprovação. T048 é nova execução de nove testes, não soma de checkpoints históricos nem repetição da bateria completa. Os 13 erros iniciais de preparação de LoginControllerTest em T041 e os resultados/evidências de T042–T046 também permanecem preservados.

Esta seleção local de duas classes **não é execução de suíte completa/CI**, não substitui T050 e não conclui a revisão global US3/SC-006 de T049. Login/JWT real é comprovado no método de suporte aprovado; isso não converte evidências mockadas dos slices anteriores em integração real. Não ampliados cenários, transporte/deploy/banco ou as três provas de rollback históricas. **U1/U2 continuam pendentes e não aplicadas.**

Evidências locais ignoradas pelo Git em `target/harness-validation/feature-004-t048-2026-10-10/`: inventários de entrada/execução, fontes anteriores, cópias integrais dos documentos/reports, resultados/consoles/XML/TXT por classe, fontes RED conferidas, revisão final e diff completo. Os 700 arquivos de evidências anteriores e os 70 reports de classes não selecionadas conservaram SHA-256; os 74 reports anteriores estão arquivados. Prefixo histórico integral do quickstart preservado por bytes. Dos 157 arquivos do inventário, somente quatro mudaram nesta etapa: os dois testes autorizados, quickstart.md por acréscimo e checkbox T048 em tasks.md; os demais 153, incluindo produção/configurações/seed e o teste novo de T046, mantêm SHA-256. Fontes executados iguais aos finais.

Diff incremental/acumulado e arquivo novo preexistente conferidos. `git diff --check`: exit 0, sem diagnósticos; `git diff --no-index --check -- NUL src/test/java/com/devsuperior/dscommerce/controllers/OrderPublicRegressionIntegrationTest.java`: exit 1 por diferença contra NUL, zero diagnósticos de whitespace. Links novos conferidos e target ignorado. [Revisão/preservação/Git final](../../target/harness-validation/feature-004-t048-2026-10-10/final-review.json).

Raiz/branch/HEAD mantidos. Estado final: **dez rastreados modificados, um não rastreado de T046, índice vazio**. **T001–T048 [X] (48); T049–T055 abertas (7); somente T048 marcada agora.** Sem T049+, T050, U1/U2, suíte completa, CI, stage, commit, push, merge ou troca de branch.

## Checkpoint T049 — revisão de preservação US3/FR-015/SC-006 (2026-10-10)

**Escopo exclusivamente T049, antes de T050.** Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `73d3398965b25027d393202a2c5a984984abd0ca`. Entrada: dez rastreados modificados, um teste não rastreado de T046 e índice vazio. Todas as alterações preexistentes preservadas. Checklist de requisitos: 16/16, sem modificação. Skill speckit-implement aplicada com o limite explícito da solicitação; pré-requisitos válidos e nenhuma extensão registrada.

### Fontes, revisão atual e escolha das evidências

Revisados [US3.1–4, FR-015 e SC-006](spec.md), [T049 e dependência T048](tasks.md), a constituição/plano e as especificações [001](../001-consulta-catalogo-produtos/spec.md), [002](../002-visualizar-detalhes-produto/spec.md) e [003](../003-login-autenticacao/spec.md). Contratos confrontados: [products-openapi.yaml](../001-consulta-catalogo-produtos/contracts/products-openapi.yaml), [product-detail-openapi.yaml](../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml) e [login-openapi.yaml](../003-login-autenticacao/contracts/login-openapi.yaml). Os contratos anteriores, mensagens e dados públicos permanecem intactos; não modificados produção, seed, testes ou configurações nesta revisão.

**Execução atual T049: revisão documental/de fontes e relatórios; zero comandos Maven, zero novos testes.** O auxiliar `& target/harness-validation/feature-004-t049-2026-10-10/review.ps1` terminou com exit 0: seleção explícita de 32 classes sem duplicatas, recontagem dos elementos XML `testcase`, confronto com metadados/exit codes e SHA-256 das fontes. Uma primeira chamada do auxiliar terminou com exit 1 por conversão de array no próprio script PowerShell; corrigido somente o coletor ignorado em target e concluída a revisão. Isso não foi falha de preparação da aplicação ou execução de testes.

Os 30 fontes aprovados em T047 correspondem à seleção/hash arquivada; os dois corrigidos correspondem ao inventário executado em T048. Todos os 86 arquivos de código/build conferidos contra T048 são idênticos. As alterações de T048 foram imports/assertions locais nesses dois testes, sem componente compartilhado alterado ou regressão adicional identificada. **Não há justificativa concreta para repetir cobertura nesta revisão.** Os comandos Maven, horários, contagens e exit codes utilizados estão preservados nas seções T047/T048 e no [inventário consolidado por classe](../../target/harness-validation/feature-004-t049-2026-10-10/effective-results.json); os nomes efetivamente aprovados e caminhos dos XMLs estão no [inventário de casos](../../target/harness-validation/feature-004-t049-2026-10-10/testcase-inventory.json).

**Linha de base de ampliação T042–T046:** catálogo/slice 44, detalhes 38, login/slice 83, H2/handlers/JWT/BCrypt 106, jornadas pós-rejeição 14 aprovações nos respectivos checkpoints. T047 reexecutou essas classes na seleção registrada; para conclusão final usa-se T047, substituindo exclusivamente as duas classes corrigidas em T048. T002 e os checkpoints anteriores, incluindo T040 (411), T041 (133) e os 13 erros iniciais de preparação de LoginControllerTest, continuam evidências históricas separadas. Nenhum desses totais é somado ao checkpoint atual.

### Matriz dos 22 cenários de aceitação anteriores

Identificação `Feature/US.cenário` corresponde à numeração das três specs. Todas as linhas abaixo foram confrontadas com fonte e casos aprovados; GREEN significa preservação no alcance de backend comprovado. Siglas de cobertura:

- **CAT:** PublicCatalogSecurityIntegrationTest, contexto completo/filtros reais, 33 aprovações em T047. Métodos de paginação/shape, ordenação global e vazio, incluindo banco vazio isolado; cada extensão é exercitada sem token e com Bearer inválido irrelevante.
- **DET:** PublicProductDetailSecurityIntegrationTest, contexto completo/filtros reais, 38 aprovações em T047. Métodos de valores selecionados, navegação pelos 52 IDs, categorias repetidas em banco isolado, 404 e conversão Long; extensões pareadas por Bearer.
- **LOG:** LoginControllerTest, slice/AuthenticationService mockado, 83 aprovações em T047. Treze cenários originais preservados e extensões de credenciais exatas, entrada estrita, 200/400/401/500, Bearer irrelevante e `/api`.
- **PUB:** OrderPublicRegressionIntegrationTest, contexto completo/filtros/banco/login/JWT reais, 14 aprovações em T047. Helpers verifyCatalog/verifyDetails/verifyLogin após cada rejeição, na raiz e `/api`, nos dois modos públicos.
- **REAL:** PUB mais JwtTokenServiceTest (1 em T047), OrderTestSupportTest (5 em T048), PasswordEncoderConfigurationTest (1 em T047) e UserAccountRepositoryTest (4 em T047). Provas complementares de conta real sem perfis, BCrypt, lookup exato/case sensitive, issuer/decoder e claims; cada classe mantém seu próprio alcance.

| Cenário | Comportamento preservado | Cobertura correspondente e conclusão |
| --- | --- | --- |
| 001/US1.1 | Primeira página limitada ao tamanho | CAT: shouldPreserveDefaultPaginationAndExactCatalogShape; page=0/size=12, defaults independentes. PUB: verifyCatalog. **GREEN** |
| 001/US1.2 | Páginas subsequentes/dados/metadados para navegar | CAT: shouldPreserveExplicitPaginationIncludingBothSizeLimits e percurso completo; size 1/50, 52 produtos. PUB: seis páginas/combinações. **GREEN** |
| 001/US2.1 | Nome, imagem e preço do produto listado | CAT: shape/tipos por item. DET: shouldNavigateFromEveryCatalogIdToItsOwnDetailsWithoutChangingCatalogItems compara listagem/detalhe/valores seed. PUB: assertProduct contra leitura independente. **GREEN** |
| 001/US2.2 | Conjunto mínimo consistente em todos os itens | CAT: assertCatalogPage para cada item/página; ProductControllerTest mantém suas assertions de slice. **GREEN** |
| 001/US3.1 | Nome crescente | CAT: shouldPreserveNameAscThenIdAscAcrossAllPagesWithoutMissingOrRepeatingProducts; ProductRepositoryTest/serviço complementares (6/6 cada em T047). **GREEN** |
| 001/US3.2 | Ordenação consistente entre páginas | CAT: todos os 52 IDs, sem repetição/omissão, name ASC/id ASC; PUB compara páginas com snapshot ordenado. **GREEN** |
| 002/US1.1 | Seleção apresenta nome/preço/descrição/image/categorias | DET: shouldPreserveExactDetailShapeAndValuesForTheSelectedProduct, seis IDs representativos e shape exato; PUB: verifyDetails. **GREEN** |
| 002/US1.2 | Todos os dados completos | DET: assertSeedProduct confere os seis campos contratuais, tipos e valores; percurso dos 52 produtos. **GREEN** |
| 002/US1.3 | Somente o produto selecionado | DET: shouldNavigateFromEveryCatalogIdToItsOwnDetailsWithoutChangingCatalogItems, igualdade do ID/valores com catálogo e seed. **GREEN** |
| 002/US1.4 | Uma categoria | DET/PUB: produto 2, lista exata Accessories. **GREEN** |
| 002/US1.5 | Todas as múltiplas categorias | DET/PUB: produto 1, Computers/Electronics, valores exatos. **GREEN** |
| 002/US1.6 | Nome de categoria repetido apresentado uma vez | DET: shouldReturnAllCategoryNamesOnlyOnceDespiteDistinctCategoryIds, categoria de outro ID com mesmo nome, fixture isolada/limpa. **GREEN** |
| 002/US2.1 | Produto inexistente claramente indicado | DET: shouldPreserveNotFoundEnvelopeWithoutReturningAnotherProductsData, 404/envelope exato. PUB: ID ausente real. **GREEN** |
| 002/US2.2 | Ausente não retorna dados de outro produto | DET/PUB: corpo apenas status/error/message/path, sem payload de produto; 404 Product not found. **GREEN** |
| 003/US1.1 | Credenciais válidas, token válido/não vazio | LOG preserva contrato HTTP; REAL comprova login real de conta persistida, resposta 200/Bearer e decoder HS256. PUB testa também Bearer inválido irrelevante. **GREEN** |
| 003/US1.2 | Token presente, senha/segredos ausentes | LOG: corpo exato accessToken/tokenType e erros confidenciais; REAL/PUB: JWT decodificado, sem senha/hash/chave/perfis, checks de conteúdo sensível. **GREEN** |
| 003/US2.1 | Nome existente/senha incorreta, rejeição genérica sem token | LOG: shouldPreserveIdenticalUnauthorizedResponsesDespiteIrrelevantBearer; PUB: credencial incorreta real, 401 Invalid credentials/envelope sem token. **GREEN** |
| 003/US2.2 | Nome desconhecido, mesma rejeição/sem token | LOG/PUB comparam integralmente respostas de nome desconhecido e senha incorreta. AuthenticationServiceTest complementa fluxo/dummy BCrypt (4 em T047). **GREEN** |
| 003/US2.3 | Credencial obrigatória ausente/vazia, dados inválidos/sem token | LOG: shouldPreserveStrictLoginInputBeforeAuthenticationDespiteIrrelevantBearer, missing/null/empty/blank e outros tipos, sem chamada ao serviço. PUB: rejeições reais representativas 400. **GREEN** |
| 003/US3.1 | Catálogo público sem token | CAT e PUB, filtros reais, contrato completo de listagem; Bearer inválido adicional não impede a operação. **GREEN** |
| 003/US3.2 | Detalhes públicos sem token | DET e PUB, filtros reais, produto existente e ausente; Bearer inválido adicional irrelevante. **GREEN** |
| 003/US3.3 | Todos os cenários 001/002 sem exigir autenticação | Quatorze cenários das duas primeiras features mapeados acima, nas integrações CAT/DET sem token e com Bearer inválido; journeys PUB complementares. **GREEN** |

### Contratos, bordas e complementos de US3

| Critério/cenário adicional | Evidência final e alcance | Conclusão |
| --- | --- | --- |
| 001: vazio real e página fora do intervalo | CAT nested EmptyCatalog usa banco vazio isolado; out-of-range na seed mantém metadados/content vazio. PUB repete página vazia após cada rejeição. | Preservado |
| 001: parâmetros/defaults/desempate | CAT cobre ausência independente de page/size, limites 1/50, page negativo, size 0/51, mensagens exatas; empates Duplicate em IDs 51/52 na ordem por ID. FR-007 permite rejeitar ou normalizar conforme regras documentadas: o contrato vigente rejeita inválidos com 400 e usa defaults 0/12 somente para parâmetros ausentes. A menção genérica a defaults no edge case não é usada para mudar o contrato. | Preservado |
| 002: categorias vazias/image/identificadores | DET/PUB preservam lista vazia do produto 3 e campo image; DET mantém Long equivalente 01/+1, 404 para 0/negativos/extremos sem impor regra nova de positividade, 400 por conversão impossível. | Preservado |
| 003: entrada estrita/name/password | LOG mantém valores exatos/case/espaços, tipos string, somente duas propriedades, sem email/identifier/roles, roots/malformed/body ausente. UserAccountRepositoryTest confirma lookup real exato/case sensitive; AuthenticationServiceTest verifica uma consulta exata e fluxo de BCrypt, com colaboradores mockados. | Preservado |
| 003: 200/400/401/500 e confidencialidade | LOG e GlobalExceptionHandlerRegressionTest conservam mensagens Invalid login data / Invalid credentials / Authentication could not be completed, envelopes e paths. AuthenticationInternalFailureTest (4) verifica causas internas em unidade; AuthenticationSensitiveLoggingTest (4) verifica canários/logging nessa camada. A emissão real é comprovada por REAL, não pelo token signed.jwt.token do slice. | Preservado nos alcances indicados |
| 003: claims/HS256/expiração/conta sem perfil | PUB verifica sub/name/iat/exp/TTL e assinatura por decoder real, ausência de claims sensíveis/roles e BCrypt/conta persistida sem perfis; JwtTokenServiceTest complementa issuer/decoder real. OrderTestSupportTest alcança todos os checks reais antes interrompidos. JwtConfigurationTest (6) conserva configurações inválidas rejeitadas como cenários esperados; não são erros pendentes. | Preservado |
| H2 console/SAMEORIGIN/acesso/configuração | H2ConsoleSecurityIntegrationTest, 15 em T047: servidor real/porta aleatória, recursos console/login.jsp/css, raiz e contexto físico /api, com/sem Bearer inválido; propriedade/path/registro servlet e chain real, sem desabilitar filtros. | Preservado |
| Handlers/envelopes/mensagens/paths | GlobalExceptionHandlerRegressionTest, 84 em T047: login/catálogo/detalhes/validação/conversão e falhas públicas alheias a pedidos, raiz e /api, com/sem Bearer; serviços mockados no slice e chain/advice reais. ProductControllerTest mantém seus 11 casos e assertions originais, aprovados em T047. Mensagens de pedidos não substituem as anteriores. | Preservado |
| US3.4: depois de 400 | PUB: INVALID_QUANTITY × raiz/api; endpoint real/envelope/mensagem, leitura independente sem novas linhas antes da limpeza, depois catálogo/detalhes/login nos dois modos. | GREEN, 2 casos |
| US3.4: depois de 401 | PUB: NO_TOKEN e MALFORMED_TOKEN × raiz/api; status/challenge/envelope, 0/0 linhas novas e jornadas posteriores públicas. | GREEN, 4 casos |
| US3.4: depois de 403 | PUB: NO_ROLE, login/JWT reais × raiz/api, rejeição sem escrita e jornadas posteriores públicas. | GREEN, 2 casos |
| US3.4: depois de 404 | PUB: MISSING × raiz/api, ausência real do produto, rejeição sem escrita e jornadas posteriores públicas. | GREEN, 2 casos |
| US3.4: depois de 422 | PUB: DUPLICATE × raiz/api, rejeição de negócio sem escrita e jornadas posteriores públicas. | GREEN, 2 casos |
| US3.4: depois de 500 | PUB: POST_FLUSH_FAILURE × raiz/api; spy delega ao saveAndFlush JPA real, três INSERTs concluídos, 1 pedido/2 itens visíveis na transação, falha pós-flush, baseline 0/0 restaurada em conexão independente após requisição/antes de limpeza. Instrumentação desarmada antes das consultas/login. | GREEN, 2 casos e rollback real pós-SQL |

Em PUB, cada rejeição é seguida de duas jornadas (28 blocos, 588 requisições públicas auxiliares; **14 casos JUnit**, sem inflar o total), mantendo as mesmas instâncias de contexto, EntityManagerFactory e FilterChainProxy. Snapshots independentes verificam pedidos/itens e preservação de produtos/categorias/contas seed, sem reinicializar contexto entre rejeição e jornadas. [Provas coletadas na execução T047](../../target/harness-validation/feature-004-t047-2026-10-10/public-regression-proof.txt). As três provas distintas de rollback de OrderRollbackIntegrationTest em T047 (9 aprovações) continuam documentadas separadamente: falha SQL tardia, falha pós-flush e falha em beforeCommit; [provas correspondentes](../../target/harness-validation/feature-004-t047-2026-10-10/rollback-proof.txt). Não confundir nenhuma escrita em overflow com rollback pós-SQL, nem beforeCommit com falha física do commit.

### Falhas históricas, substituições e resultado final

| Alvo de T047 | Resultado histórico preservado | Causa confirmada/resultado substituto de T048 |
| --- | --- | --- |
| OrderTestSupportTest | 5 casos, 4 aprovados/1 falha, exit 1; preparação/configuração | Unicidade indevida de Clock: orderClock produtivo e orderTestClock determinístico devem coexistir. T048 confere os dois, seleção por tipo e injeção efetiva do Clock de teste em OrderService, mantém encoder/decoder únicos e alcança login/JWT/BCrypt reais. **5/5, exit 0** |
| OrderServiceContextTest | 4 casos, 3 aprovados/1 falha, exit 1; incompatibilidade comportamental de expectativa | Expectativa antiga IllegalArgumentException para overflow, diante do contrato vigente InvalidOrderException/UNREPRESENTABLE_VALUE/Order value cannot be represented. T048 verifica tipo/motivo/mensagem em subtotal e soma, mantém nenhuma instrução INSERT/zero pedidos e alcança essas assertions finais. **4/4, exit 0** |

[T048: comandos, horários, resultados e métodos antes interrompidos](../../target/harness-validation/feature-004-t048-2026-10-10/verified-execution.json). Os XMLs dos dois métodos estão aprovados, sem skip; revisão dos fontes confirma execução sequencial das assertions, sem retorno antecipado ou captura que esconda erro. Não encontrada evidência de defeito produtivo que requeira alteração de produção. As outras 30 classes de T047 estavam GREEN e permanecem com fontes idênticos; **não existe outra falha pendente na seleção de T047/T048**.

**Resultado consolidado por última execução relevante: 32 classes, 904 aprovações, zero falhas/erros/ignorados.** Cálculo: **895 aprovações de 30 classes de T047 + 9 aprovações de duas classes de T048**. Os resultados antigos completos dessas duas classes são substituídos, incluindo suas sete aprovações antigas; não somados novamente. O histórico de T047 continua 904 executados/902 aprovados/duas falhas, 30/32 GREEN. Não alterados registros, causas ou reports históricos para convertê-los em GREEN. [Resumo da revisão atual](../../target/harness-validation/feature-004-t049-2026-10-10/review-summary.json).

**Checkpoint US3.1–4 / FR-015 / SC-006 comprovado para os cenários de aceitação de backend das Features 001–003 e integrações acima; somente T049 marcada [X].** Não identificada lacuna impeditiva em seu escopo.

### Limitações e preservação final

Esta consolidação usa execuções locais selecionadas, serializadas em processos distintos em T047/T048; **não é execução atual de suíte completa, não é CI e não substitui T050**. T049 não introduz teste/execução ou prova nova de comportamento. Os totais não demonstram convivência de todas as classes num único processo nem aprovam diff futuro.

Slices de login/handlers e unidades com mocks comprovam contratos/fluxos simulados, sem provar assinatura, conta persistida ou falha física de banco. Essas provas reais são indicadas especificamente em REAL/PUB e nos repositórios/configurações. O 500 de login é exercitado com falhas instrumentadas/mocks na camada correspondente, não como indisponibilidade física de banco depois de cada pedido rejeitado. PUB repete famílias representativas e não todas as combinações de US2; suas jornadas não incluem nova injeção de 500 de login. Context paths de MockMvc não são deploy físico (H2 possui prova por servidor real). A seleção de produto em um GET comprova o contrato backend da Feature 002, sem medir interface gráfica; os casos válidos de catálogo são evidência funcional, sem estimar SLA populacional de 95% da Feature 001. Mantidos limites de transporte, falha física de commit e outro banco. **U1 (conteúdo residual no JSON de pedidos) e U2 (ativação de fixtures na demonstração) continuam pendentes/não aplicadas.**

Evidências auxiliares ignoradas pelo Git em `target/harness-validation/feature-004-t049-2026-10-10/`: inventários/hash de entrada, documentos antes da revisão, matriz efetiva/casos/resumo, diff e conferência final. Preservados por SHA-256 os 801 arquivos de evidências T042–T048 e os 74 reports atuais; prefixo integral do quickstart preservado. Dos 157 arquivos inventariados, somente quickstart.md (acréscimo deste checkpoint) e tasks.md (checkbox T049) alterados, demais 155 idênticos, incluindo o novo teste preexistente de T046.

Diff incremental/acumulado e arquivo não rastreado de T046 conferidos; links novos existentes. `git diff --check`: exit 0/zero diagnósticos. `git diff --no-index --check -- NUL src/test/java/com/devsuperior/dscommerce/controllers/OrderPublicRegressionIntegrationTest.java`: exit 1 por diferença contra NUL, zero diagnósticos de whitespace. [Conferência final de preservação/whitespace/Git](../../target/harness-validation/feature-004-t049-2026-10-10/final-review.json).

Git final: raiz/branch/HEAD mantidos, **dez rastreados modificados, um não rastreado de T046, índice vazio**. **T001–T049 [X] (49); T050–T055 abertas (6); somente T049 marcada nesta revisão.** Sem T050+, CI, U1/U2, stage, commit, push, merge ou troca de branch.

## Execução T050 — suíte completa pelo Wrapper (2026-10-11)

**Exclusivamente T050, parada antes de T051.** Confirmados com `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`. **Working tree inicialmente limpo e índice vazio; nenhuma alteração preexistente.** O commit contém o checkpoint US3/T042–T049, dependência de T050; os resultados anteriores permanecem históricos.

Skill speckit-implement aplicada somente ao escopo autorizado. Pré-requisitos `& .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit 0, FEATURE_DIR absoluto correto e artefatos disponíveis. Checklist requirements.md: 16/16, zero abertos, somente leitura. Consultados T050/dependência T049, instruções locais AGENTS.md/harness, constituição, spec, plano, pesquisa, modelo, contratos e procedimentos/evidências deste quickstart. Ignores Java existentes suficientes. `.specify/extensions.yml` ausente antes/depois; nenhum hook executável.

### Preservação anterior à execução

Antes de qualquer sobrescrita pelo Maven, **74 arquivos de `target/surefire-reports/` foram copiados para `target/harness-validation/feature-004-t050-2026-10-11/surefire-before/` e conferidos por SHA-256**. Arquivados os documentos de entrada, inventário/hash dos 157 arquivos rastreados e hashes dos **5.806 arquivos de evidências anteriores**. As execuções/correções históricas de T047/T048 e a consolidação de T049 não foram reescritas como nova aprovação. [Estado inicial](../../target/harness-validation/feature-004-t050-2026-10-11/git-before.json), [inventário dos relatórios anteriores](../../target/harness-validation/feature-004-t050-2026-10-11/reports-before.json) e [conferência de preservação](../../target/harness-validation/feature-004-t050-2026-10-11/preservation-review.json).

POM e recursos/configurações locais examinados: nenhuma seleção de classes, exclusão ou desativação de testes adicionada. MAVEN_ARGS, MAVEN_OPTS, JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS e _JAVA_OPTIONS estavam ausentes; nenhuma variável foi alterada. Nenhum build Maven concorrente executado.

### Comandos, versões e resultado atual

Na raiz Windows/PowerShell, exatamente:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd test
```

O primeiro comando, entre 12:56:32.821 e 12:56:38.185 (UTC−03:00), terminou com exit 0 e confirmou **Apache Maven 3.9.16**, selecionado pelo Wrapper, e **Java 21.0.12.1, Azul Systems, Inc.**, runtime `C:\Program Files\Zulu\zulu-21`; Windows 10/amd64. Wrapper/propriedades/POM preservados. [Saída de versão](../../target/harness-validation/feature-004-t050-2026-10-11/wrapper-version.txt) e [comando/horários/exit](../../target/harness-validation/feature-004-t050-2026-10-11/wrapper-version-result.json).

**Uma única execução da suíte completa**, sem `-Dtest`, wildcard, seleção/exclusão de classes, skip ou parâmetro adicional para obter aprovação:

| Execução / revisão | Início → término (UTC−03:00, 2026-10-11) | Testes | Falhas | Erros | Ignorados | Resultado / exit |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `.\mvnw.cmd test`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`, código/build/testes sem diff | 12:57:10.782 → 13:00:48.019 | **904** | **0** | **0** | **0** | **BUILD SUCCESS / 0** |

Maven registrou duração `03:29 min` e término `2026-10-11T13:00:47-03:00`; os horários da tabela delimitam a invocação/captura pelo auxiliar. **904 aprovações observadas nesta execução**, não inferidas da consolidação de T049. A coincidência numérica não soma nem reutiliza resultados históricos: agora todas as classes foram executadas pela descoberta normal do Surefire na mesma invocação completa. **Nenhuma falha de build/teste, correção de aplicação/teste ou repetição de alvo/suíte foi necessária.**

### Conferência dos relatórios desta execução

Examinados os relatórios atuais em `target/surefire-reports/`: **37 XMLs e 37 TXTs**. Todos os XMLs têm modificação dentro da janela desta execução e correspondem por SHA-256 às cópias arquivadas em `target/harness-validation/feature-004-t050-2026-10-11/full-01/reports/`. Contados **904 elementos testcase**, confrontados com atributos de cada suite e com o resumo final do console Maven: zero failures/errors/skipped. Suites externas vazias e resultados de classes aninhadas foram tratados sem dupla contagem. As **32 classes-base com testes** do inventário de fontes correspondem às classes efetivamente executadas; nenhuma ausente. Os XMLs confirmam Java 21.0.12.1/Azul/Windows 10.

Evidências desta execução: [comando, HEAD, status de entrada, horários e exit code](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/result.json), [console completo](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/console.txt), [resumo conferido](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/summary.json), [inventário das suites](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json) e [inventário dos casos](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/testcase-inventory.json). Os auxiliares de captura/conferência e as cópias de relatórios ficam somente em target, ignorados pelo Git; a evidência versionada é este registro.

Tentativas auxiliares de `verify-preservation.ps1` retornaram exit 1 por PathTooLongException; uma tentativa posterior foi interrompida (exit 1). A primeira chamada de `final-audit.ps1` retornou exit 1 por NotSupportedException de formato de caminho. Diagnóstico: `@(Get-Content ... | ConvertFrom-Json)` envolvia o array JSON em outro array, fazendo o coletor tratar vários caminhos como um caminho composto. Corrigidos somente os dois auxiliares ignorados, removendo esse envolvimento e mantendo fallback Get-FileHash para caminhos longos. A conferência de preservação terminou com exit 0, confirmando os 5.806 arquivos anteriores intactos e as 74 cópias Surefire idênticas. Uma chamada da auditoria iniciada antes de essa conferência terminar retornou exit 1 pelo link de preservation-review.json ainda ausente; a auditoria foi repetida depois da geração desse arquivo. **Esses impedimentos dos coletores não são falhas Maven/JUnit, não interromperam a suíte e não motivaram alteração ou repetição dos testes.**

### Revisão final e limite da entrega

Somente quickstart.md por acréscimo e a checkbox T050 em tasks.md alterados. Prefixo histórico integral deste quickstart preservado em bytes; **155 demais arquivos rastreados idênticos por SHA-256**, incluindo produção, testes, contratos, recursos/seed, Wrapper e POM. Diff dos dois documentos revisado, links novos existentes; `git diff --check`: exit 0, sem diagnósticos de whitespace. Índice vazio e nenhum arquivo não rastreado fora dos ignores. [Conferência final de preservação, diff, checkboxes e Git](../../target/harness-validation/feature-004-t050-2026-10-11/final-review.json).

**T050 marcada [X] após aprovação da suíte completa. T001–T050 [X] (50); T051–T055 abertas (5).** Resultado local Windows/H2 para o código do HEAD indicado, sem alegar execução Linux/CI, demonstração HTTP de T051 ou conclusão de toda a feature. **U1 (conteúdo residual no JSON de pedidos) e U2 (ativação de fixtures na demonstração) permanecem pendentes/não aplicadas.** Não executada T051+, CI, stage, commit, push, merge ou troca de branch.

Git final: raiz/branch/HEAD mantidos; **somente dois rastreados modificados (quickstart.md e tasks.md), índice vazio, nenhum não rastreado**. HEAD `e18b4398638863e544404bdd37d77a57d567e428`.

## Execução T051 — demonstração HTTP e ativação explícita de fixtures / U2 (2026-10-11)

**Exclusivamente T051, parada antes de T052.** Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`, confirmados com os quatro comandos Git usuais. Entrada: quickstart.md/tasks.md modificados por T050, índice vazio, nenhum não rastreado. Essas alterações preexistentes foram preservadas. Antes de editar/iniciar, registrados hashes dos 157 rastreados e dos **243 arquivos de T050/relatórios Surefire atuais**; o prefixo integral deste documento, incluindo T050, foi conferido por tamanho/SHA-256. A suíte completa aprovada em T050 permanece evidência histórica separada e não foi repetida.

Skill speckit-implement aplicada no limite solicitado. Pré-requisitos PowerShell: exit 0, FEATURE_DIR absoluto correto. Checklist requirements.md 16/16, zero abertos, somente leitura. Conferidos T051/dependência T050, procedimento de demonstração, instruções locais e contexto/contratos já consultados. Ignores existentes suficientes; `.specify/extensions.yml` ausente antes/depois, nenhum hook executável.

### Preparação reproduzível e resolução delimitada de U2

`OrderTestFixture` é um auxiliar Java que exige datasource `jdbc:h2:mem:order-test-...`; seu construtor não popula dados. Seus métodos confirmam contas/perfis/produtos em transações REQUIRES_NEW. `OrderTestConfig` registra esse bean somente quando importado explicitamente com perfil test, além de fornecer um Clock controlável para testes. **Classpath de teste/perfil test, isoladamente, não acionam a criação das fixtures da demonstração.** Não havia lançador/runner para essa preparação.

Adicionado somente [OrderDemoApplication.java](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java), em código de teste. O ponto de entrada inclui explicitamente `DemoFixtures.class` como fonte de SpringApplication junto de DscommerceApplication. Essa configuração `@TestConfiguration`/`@Profile("test")` registra um único OrderTestFixture reutilizando JdbcTemplate, gerenciador transacional, BCrypt e JwtEncoder reais. O lançador obtém esse bean e chama createAccount com CLIENT e createProduct com 10.00/7.50; JDBC independente confirma o perfil e os GETs públicos confirmam os preços. Nenhum hook/endpoint de preparação ou bean produtivo adicionado.

O próprio lançador seleciona perfil test, URL H2 exclusiva via `OrderTestConfig.isolatedDatabaseUrl()`, endereço 127.0.0.1 e porta aleatória; opções de linha de comando têm precedência sobre os recursos compartilhados. Reutiliza apenas a fábrica de URL, **sem importar o Clock fixo em 2030**: OrderService e autenticação usam seus componentes/Clock produtivos reais. A seed test de 52 produtos é carregada intacta no banco isolado; as duas linhas próprias da fixture são adicionais, portanto catálogo com 54 produtos durante esta demonstração. Produtos/categorias/vínculos e conta original foram comparados integralmente com snapshots anteriores. Nenhuma atribuição de perfil em produção, modificação de seed pública, contratos ou recursos compartilhados.

Comando exato executado na raiz, também reproduzível por outro checkout com o novo arquivo:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.devsuperior.dscommerce.demo.OrderDemoApplication' '-Dspring-boot.run.profiles=test'
```

O descriptor local do plugin Spring Boot 3.3.4 confirmou `test-run`, fase prévia test-compile e propriedades main-class/profiles. O comando compila os fontes de teste e inicia **esse ponto de entrada explícito** com classpath de teste; não executa JUnit/Surefire. Ele prepara dados, realiza a demonstração e fecha o próprio contexto automaticamente. Não precisa de senha/token em argumentos, arquivos, variáveis de ambiente ou intervenção manual. Credenciais aleatórias existem apenas em memória; o JWT retornado pelo POST /login é guardado em memória para POST /orders. Logging de aplicação e SQL são desativados somente nessa invocação para evitar saída sensível; filtros/validações permanecem ativos. Evidência emitida por lista explícita de campos seguros, sem corpo de login, credenciais, JWT, hash ou segredo.

### Execução pelo servidor e filtros reais

Uma execução, entre **13:13:50.652 e 13:14:37.385 (UTC−03:00)**, com **BUILD SUCCESS / exit 0**. Servidor embarcado real em loopback/porta **58811**, HttpClient sem redirects, serviços/repositórios/filtros/encoder/decoder reais; sem MockMvc, autenticação simulada ou gravação direta de pedidos. SAMEORIGIN/Content-Type JSON conferidos nas respostas; nenhum Location prometendo consulta de pedidos. POST /orders sem token retornou 401 antes das duas compras, enquanto o JWT real de login autorizou os 201.

| Etapa | Resultado observado |
| --- | --- |
| Preparação confirmada | Conta própria id 2 com CLIENT; produtos próprios 53/54 a 10.00/7.50. Contagens iniciais: 52 produtos da seed, zero pedidos. |
| Consultas antes da compra | GET /products?page=0&size=50: 200, totalElements 54; GET /products/1 e /products/53 e /products/54: 200. Preços das fixtures conferidos pelo HTTP público. |
| Login antes | POST /login: 200; exatamente accessToken/tokenType Bearer, token não vazio. Decoder real confirma HS256 e sub da conta 2; nenhum valor de autenticação registrado. |
| Primeiro envio | POST /orders: **201**, id **1**, accountId **2**, createdAt **2026-10-11T16:14:36.849Z**, **WAITING_PAYMENT**. |
| Mesmo corpo novamente | POST /orders: **201**, id **2**, accountId **2**, createdAt **2026-10-11T16:14:36.974Z**, **WAITING_PAYMENT**; identificador distinto. |
| Itens/valores de ambos | Ordem 53/54, quantidades 2/3; unitPrice strings **10.00/7.50**, subtotal strings **20.00/22.50**, total string **42.50**. Exatamente seis campos no pedido e quatro por item; IDs/quantidades inteiros, instante UTC com milissegundos dentro da janela da requisição. |
| Persistência após respostas | Datasource/conexão JDBC independente: **dois pedidos e quatro itens**, comprador/estado/instante iguais aos 201; positions 0/1 e preços/quantidades históricos completos. Primeiro pedido e suas linhas permanecem idênticos depois do segundo envio. |
| Consultas e login depois | Catálogo/detalhe público novamente 200, respostas iguais às anteriores; novo POST /login 200 com JWT verificado, sem exigir pedido novo. Todos os registros originais da seed permanecem iguais. |
| Encerramento próprio | try-with-resources fecha HttpClient e somente o contexto iniciado pelo lançador; processo Java termina normalmente, Maven retorna 0. Marcador ORDER_DEMO_CLOSED, nenhuma instância desse ponto de entrada remanescente e porta 58811 fechada, conferidos depois. Nenhum outro processo encerrado. |

São **11 requisições HTTP**: quatro GETs iniciais, login inicial, rejeição sem token, dois pedidos, dois GETs posteriores e login posterior. As duas respostas foram recebidas normalmente; não alegada simulação de perda de resposta. Não criado/utilizado GET /orders. **Demonstração de sucesso/commit/repetição não substitui as três provas automatizadas de rollback** já registradas; nenhuma falha SQL/pós-flush/beforeCommit foi injetada aqui. Alcance local Windows/H2/loopback, sem CI, outro banco ou deploy.

Evidências sanitizadas em `target/harness-validation/feature-004-t051-2026-10-11/`: [estado inicial](../../target/harness-validation/feature-004-t051-2026-10-11/git-before.json), [comando/horários/exit](../../target/harness-validation/feature-004-t051-2026-10-11/execution.json), [console sanitizado](../../target/harness-validation/feature-004-t051-2026-10-11/demo-console.txt) e [respostas completas seguras, revisão de saída e encerramento](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json). O primeiro coletor de sanitização procurou a propriedade inexistente security.jwt.secret; leitura nula seguida de busca vazia gerou falso positivo e exit 1, sem expor chave. Corrigida somente a conferência auxiliar para security.jwt.secret-base64 com guarda de valor não vazio: exit 0, nenhuma credencial da fixture, JWT ou chave na saída. Nenhuma falha ou repetição da demonstração Maven.

### Revisão final e pendências

**T051 marcada [X] após a demonstração aprovada. U2 resolvido exclusivamente para ativação explícita e reproduzível das fixtures de demonstração. U1 permanece pendente/não aplicado.** Os registros anteriores que indicam U2 pendente são históricos e permanecem intactos. T001–T051 [X] (51); T052–T055 abertas (4); nenhuma dessas tarefas executada.

Alterações incrementais: novo lançador em src/test, acréscimo desta seção e somente checkbox T051. Preservadas as alterações documentais de T050, todos os **243 arquivos inventariados de T050/Surefire** por SHA-256 e os **155 demais rastreados**, incluindo produção/testes anteriores/seed/contratos/Wrapper/POM. Conteúdo do novo arquivo e diff incremental/acumulado revisados; `git diff --check`: exit 0. A primeira auditoria auxiliar foi interrompida pelo aviso Git LF/CRLF tratado como erro nativo no PowerShell (exit 1), sem defeito de whitespace. Repetida a conferência do Java não rastreado com `git -c core.autocrlf=false diff --no-index --check -- NUL src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java`: exit 1 pela diferença contra NUL, zero diagnósticos de whitespace. Auditoria final exit 0; links existentes. [Conferência final de preservação, sanitização, diff e Git](../../target/harness-validation/feature-004-t051-2026-10-11/final-review.json).

Git final: raiz/branch/HEAD mantidos, quickstart.md/tasks.md modificados (T050 preservada + T051), um Java novo não rastreado, índice vazio. HEAD `e18b4398638863e544404bdd37d77a57d567e428`. Sem suíte completa adicional, T052+, U1, CI, stage, commit, push, merge ou troca de branch.

## Revisão T052 — arquitetura, escopo e limites (2026-10-11)

**Exclusivamente T052, parada antes de T053; revisão documental, sem nova execução de testes ou demonstração.** Confirmados `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`. Entrada: quickstart.md/tasks.md modificados por T050/T051, OrderDemoApplication.java não rastreado, índice vazio. Esses registros e o launcher são a base preservada desta revisão, não alterações implementadas em T052.

Skill speckit-implement aplicada no limite autorizado. Pré-requisitos PowerShell: exit 0, FEATURE_DIR absoluto correto; requirements.md 16/16, zero abertos, somente leitura. T052 depende de T051 já concluída. `.specify/extensions.yml` ausente; ignores Java suficientes. Fontes: [constituição 1.0.0](../../.specify/memory/constitution.md), [plano](plan.md), [spec/FR-001–017](spec.md), [pesquisa D1–D8](research.md), [modelo](data-model.md), [README do contrato](contracts/README.md), [OpenAPI](contracts/orders-openapi.yaml) e instruções locais/harness. Os estados de planejamento desses documentos permanecem históricos; não foram reescritos como aprovação de implementação.

Antes de editar, inventariados por SHA-256 os **157 rastreados, o launcher não rastreado e 254 arquivos das evidências T050/T051 e Surefire atuais**, além do tamanho/hash do prefixo integral do quickstart e conteúdo de tasks.md. [Base da revisão](../../target/harness-validation/feature-004-t052-2026-10-11/baseline.json). Não sobrescritos relatórios ou evidências anteriores.

### Revisão de código contra os princípios e o desenho

As linhas abaixo referem-se aos arquivos presentes neste checkout. Trechos são citações do código; conclusões desta tabela são de **inspeção**, apoiadas separadamente pelas execuções identificadas adiante.

| Critério / fonte | Caminho, linhas e trecho relevante | Conclusão da revisão |
| --- | --- | --- |
| Constituição I–III; plano, fluxo 2/3 | [OrderController.java](../../src/main/java/com/devsuperior/dscommerce/controllers/OrderController.java), linhas 24–28: `@PostMapping("/orders")`, `@Valid @RequestBody OrderRequestDTO`, `@AuthenticationPrincipal AuthenticatedAccountPrincipal`, `orderService.register(request, principal)`; [OrderService.java](../../src/main/java/com/devsuperior/dscommerce/services/OrderService.java), 61–98: duplicatas, lookup em lote, preços, agregado e mapping. | Controller trata HTTP e devolve DTO; decisões e orquestração ficam no serviço. Repositórios são abstrações JPA, sem cálculo ou escolha de comprador. Java 21/Boot 3.3.4 constam em pom.xml; nenhuma stack ou camada paralela introduzida. |
| Constituição III/IV; D3, FR-002/004/013 | [OrderRequestDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderRequestDTO.java), 12–18: `@NotEmpty @Valid List<@NotNull OrderItemRequestDTO>`; [OrderItemRequestDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderItemRequestDTO.java), 8–9: `@NotNull @Positive` e `@Max(2147483647)`; [OrderRequestJsonConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/OrderRequestJsonConfig.java), 29–42/67–99: campos permitidos, detecção de chaves repetidas, `VALUE_NUMBER_INT`, `getLongValue()`/`getIntValue()`. | Fronteira recebe somente itens/productId/quantity, sem titular ou valores calculados. Bean Validation antecede o serviço; leitura específica evita coerções globais e não modifica o leitor do login. **A estrita leitura do corpo inteiro tem o limite U1**, descrito abaixo. |
| FR-001/003/010/011; D1/D2 | [OrderJwtAuthenticationConverter.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationConverter.java), 30–60: exp/iat/nbf contra Clock, subject canônico, comparação do sub no payload original já verificado, `accounts.resolve(accountId)`, autenticação com credenciais `null`; [AuthenticatedAccountService.java](../../src/main/java/com/devsuperior/dscommerce/services/AuthenticatedAccountService.java), 28–45: lookup read-only, principal só accountId e authorities `ROLE_` obtidas do banco; [UserAccountRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/UserAccountRepository.java), 14–15: `left join fetch account.roles`. | Decoder existente verifica HS256 antes do converter; converter não confia em name/roles do JWT. Expiração já alcançada, iat/nbf futuros e sub inválido são rejeitados. Hash/nome da conta não integram o principal nem a confirmação. |
| Guarda tardia e comprador; plano, fluxo 3 | OrderService.java, 52–59: `accounts.findByIdWithRoles(principal.accountId())`, conta ausente lança BadCredentialsException, ausência simultânea de CLIENT/ADMIN lança AccessDeniedException; 90/97: `new Order(buyer, createdAt, items)` e accountId de `saved.getBuyer()`. | Comprador vem da identidade resolvida; existência/perfis são revalidados dentro da transação. CLIENT **ou** ADMIN basta, sem privilégio inferido do request/token. A leitura transacional não promete bloquear revogação concorrente posterior a essa leitura. |
| Histórico e cálculos exatos; FR-005/008/009, D4–D6 | OrderService.java, 78–85/102–115: snapshot de `product.getPrice()`, rejeição de null/não positivo, `value.setScale(2, RoundingMode.UNNECESSARY)`, limite `precision() > 19`, subtotal e soma verificados antes do save; [OrderItem.java](../../src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java), 59–63/109–111: unitPrice persistido decimal19,2 e `unitPrice.multiply(BigDecimal.valueOf(quantity.longValue()))`; [Order.java](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java), 108–110: soma de OrderItem::getSubtotal. | Sem float/double/MathContext de arredondamento no caminho monetário. Normalizar zeros excedentes não perde valor; fração não representável e overflow causam rejeição. Subtotal/total derivam exclusivamente dos dados históricos, sem consultar Product.price novamente. Snapshot é o preço lido, sem promessa de último preço no instante físico do commit ou novo lock/versionamento. |
| Agregado e integridade; Constituição IV, D5/modelo | Order.java, 39–57/62–85/100–101: vínculos NOT NULL, `cascade = CascadeType.PERSIST`, `@OrderBy("position ASC")`, lista completa não vazia/sem nulos/itens já vinculados e retorno não mutável; OrderItem.java, 26–32/39–63: unicidades order/product e order/position, CHECKs quantity > 0, position >= 0, unit_price > 0, vínculos NOT NULL/FK e validação decimal; [UserAccount.java](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java), 40–48: coleção lazy de perfis, FK/NOT NULL/unicidade, conjunto inicialmente vazio. | Somente pedido propaga PERSIST aos itens; conta/produto não recebem cascade de escrita. Posições mantêm a ordem enviada; não há setters de mudança da compra. Construtor protegido JPA não é API de criação. Pedido não vazio depende da construção/Bean Validation, **não de CHECK por linha**; limites de totais derivados são verificados pelo serviço, não por colunas redundantes. Perfis vazios não autorizam nem recebem CLIENT automaticamente. |
| Unidade transacional e commit antes de 201; FR-012, D6 | OrderService.java, 50–51: `@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)` em método público; 55–98: revalidação, leituras, snapshot, saveAndFlush e mapping no mesmo método. OrderController.java, 27 antes de 28: retorno do serviço antecede construção do 201. | Chamada do controller usa o bean injetado/proxy, sem self-invocation, gravações assíncronas ou transação por item. Flush executa SQL e **não é commit**. Na jornada HTTP revisada, sem transação externa envolvendo a requisição, o interceptor conclui o commit antes de devolver o DTO ao controller. REQUIRED pode participar de transação de um chamador externo; não se generaliza essa ordem de commit a qualquer uso hipotético com transação externa. |
| Propagação e erros seguros; Constituição V, D7 | OrderService.java, 101–113: captura somente ArithmeticException da normalização, traduzida para erro de domínio; nenhum catch em save/flush/mapping. [GlobalExceptionHandler.java](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java), 103–142/145–163: mensagens fixas, guardas 401/403 e `@ExceptionHandler(Exception.class)` para POST /orders, incluindo conclusão transacional. [SecurityConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java), 58–65: falha interna de resolução distinguida como server_error; [OrderAuthenticationEntryPoint.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderAuthenticationEntryPoint.java), 28–38 e [OrderAccessDeniedHandler.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderAccessDeniedHandler.java), 26–30: envelope seguro no filtro. | Falhas de persistência/processamento/conclusão escapam do serviço e impedem o 201 nesse fluxo; advice não fabrica sucesso. Filtros têm adaptadores próprios, sem depender do MVC. Envelope contém status/error/message/path, sem causa, SQL, corpo, token/hash/chave. Não há logging de dados sensíveis nos componentes novos inspecionados. Isso não constitui auditoria universal de logs/ambientes. |
| Saída e escopo; FR-006/007/015–017, contrato | [OrderResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderResponseDTO.java), 10–18: seis campos, UTC/milissegundos, cópia da lista; [OrderItemResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderItemResponseDTO.java), 6–18: quatro campos e formato monetário; OrderService.java, 89–98: Instant truncado, `toPlainString()`; SecurityConfig.java, 46/53/70–80: chain exclusiva de POST /orders e fallback público. | Estado inicial WAITING_PAYMENT; strings de duas casas, sem entidade JPA na API. Inventário de mappings produtivos: POST /orders, POST /login e GETs de produtos. Sem GET/listagem/atualização de pedidos, Location, pagamento, idempotência, carrinho/estoque/frete/desconto ou provisionamento de perfis. Cada chamada válida cria outro agregado. Contratos/seed/Product preservados; APIs públicas apoiadas nas regressões existentes. |

### OrderDemoApplication e alcance de U2

Revisado [OrderDemoApplication.java](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java), preservado integralmente. Linhas 37–46: classe final sem anotação de componente ou configuração principal; **`new SpringApplication(DscommerceApplication.class, DemoFixtures.class)`** inclui explicitamente a configuração. Linhas 199–205: `@TestConfiguration(proxyBeanMethods = false)`, `@Profile("test")` e bean que constrói OrderTestFixture. Linhas 51–69: guardas de URL/perfil/bean único e chamadas explícitas `createAccount(..., Set.of(UserRole.CLIENT))`/`createProduct(...)`; o construtor do bean, classpath ou perfil isolados não populam os dados.

Linhas 43–46 usam test, H2 UUID exclusivo, loopback/porta aleatória e logging/SQL desativados nesta invocação. [OrderTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestConfig.java), 31–33 fornece somente a URL utilizada; seu bean Clock controlado **não é importado**. [OrderTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/OrderTestFixture.java), 36–46/53–72 exige URL order-test e confirma apenas dados próprios em REQUIRES_NEW; isso é preparação de teste, não transação independente dos itens de produção. Demo, linhas 55–59/98–102, compara seed original; 82–97 usa login e servidor/filtros reais; 153–164 consulta conexão independente; 103–113 imprime somente resultado seguro e encerra seu próprio contexto. Credenciais aleatórias/JWT ficam em memória; falha imprime somente nome da classe da exceção, não mensagem/corpo.

Ausência de ativação automática conferida por código e dependências locais: [DscommerceApplication.java](../../src/main/java/com/devsuperior/dscommerce/DscommerceApplication.java), 6–10 inicia somente a classe produtiva; pom.xml mantém starter-test com scope test; nenhuma referência/import do launcher ou DemoFixtures fora do próprio arquivo em src/pom/.mvn. `@TestConfiguration` tem meta-anotação TestComponent: no JAR **spring-boot-test 3.3.4** local, leitura de META-INF/spring.factories e inspeção com javap mostraram ExcludeFilterApplicationContextInitializer registrando TestTypeExcludeFilter, cujo match exclui TestComponent da varredura; SpringBootApplication usa TypeExcludeFilter. Os customizadores dos contextos de teste também registram a exclusão. A fonte explícita da demonstração permite ativação intencional sem transformar a configuração em bean automático nos demais contextos.

Comando reproduzível **executado em T051, não repetido em T052**:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.devsuperior.dscommerce.demo.OrderDemoApplication' '-Dspring-boot.run.profiles=test'
```

**U2 permanece resolvido somente quanto à ativação explícita e reproduzível das fixtures de demonstração**, comprovada na execução T051. Esta revisão não iniciou o aplicativo normal nem os demais testes após a inclusão do launcher; a ausência de ativação automática nesses caminhos é conclusão de inspeção, não uma nova prova executada de convivência. Não ampliada a resolução para provisionamento real, outras configurações/ambientes ou todos os cenários da feature.

### Evidências existentes, com natureza e revisão identificadas

| Evidência consultada | Resultado já executado / apoio à revisão | Limite |
| --- | --- | --- |
| [Suíte T050: resumo](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/summary.json) e [inventário](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json) | `.\mvnw.cmd test`, HEAD acima, BUILD SUCCESS/exit 0, 904 testes, 0 falhas/erros/ignorados. Inclui OrderServiceContextTest 4, OrderRepositoryTest 23, OrderRegistrationIntegrationTest 9 e OrderRollbackIntegrationTest 9; proxy/Clock/limites, metadados e rejeições JDBC, sucesso/histórico e rollback respectivamente. | Execução anterior, antes do novo launcher T051; não alegada suíte atual nem cobertura de qualquer entrada possível. Slices/mocks não equivalem a filtros ou banco reais. |
| [XML T050 de rollback](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/reports/TEST-com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.xml), system-out e assertions em [OrderRollbackIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java), 96–102/130–218/625–656 | Controle positivo: 201, +1 pedido/+2 itens, três INSERTs. Falha SQL tardia: dois INSERTs concluídos e terceiro rejeitado, HTTP 500, baseline independente restaurada. Falha pós-flush: saveAndFlush retornou, três INSERTs/1 pedido/2 itens visíveis, mesma exceção, HTTP 500, baseline restaurada. beforeCommit: DTO produzido, proxy ainda não retornou, callback no commitTransactionAfterReturning, três INSERTs reais, exceção/500 e baseline restaurada. | Três provas automatizadas distintas, sem transação externa do teste, verificadas antes da limpeza; **beforeCommit ocorre antes do commit físico e não simula falha física de conexão durante commit**. Os [registros T047](../../target/harness-validation/feature-004-t047-2026-10-10/rollback-proof.txt) continuam históricos; o XML citado é da suíte T050 no HEAD desta revisão. |
| [Demonstração T051](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json) e [comando/exit](../../target/harness-validation/feature-004-t051-2026-10-11/execution.json) | BUILD SUCCESS/0; fixtures explicitamente ativas em test/H2 isolado; HTTP/login/JWT reais; dois 201 completos com ids distintos, total 42.50; dois pedidos/quatro itens vistos por JDBC independente; consultas/login e seed preservados; contexto próprio encerrado e saída sanitizada. | Apenas demonstração de sucesso/repetição/commit e U2; **não é prova de rollback**, não injeta falhas, não simula resposta perdida e não substitui a suíte. |

### Achados, limites e fechamento desta tarefa

- **U1 pendente/não aplicado:** OrderRequestJsonConfig.java, 33–42 retorna ao alcançar END_OBJECT e não verifica EOF/conteúdo residual depois do objeto principal. A inspeção das configurações não encontrou FAIL_ON_TRAILING_TOKENS ou guarda equivalente. Logo, rejeição de chaves extras/repetidas e tokens inválidos dentro do objeto não comprova rejeição de um segundo valor ou lixo após ele; não declarada conformidade integral da leitura do corpo. Nenhuma reprodução, teste ou correção executada em T052; o achado é de código, preservando a pendência anterior.
- Nenhum outro desvio material identificado **nos caminhos revisados e nas evidências consultadas**. As constraints são evidenciadas em H2/JDBC; não garantem pedido não vazio ou limites de somas por SQL isolado. Não há prova sobre outro banco, falha física de commit de resultado incerto, transporte/serialização após commit, deploy, CI, desempenho/SLO ou todas as intercalações concorrentes. Perda da resposta após commit não desfaz compra, conforme Q1.
- Leitura do contrato serviu à revisão de arquitetura/escopo; **validação OpenAPI completa e conferência global de referências de T053 não executadas**. Preservados os limites históricos e a distinção entre requisitos, inspeção, testes e demonstração. U2 tem apenas o alcance acima; U1 impede afirmar conformidade integral da feature.

Conferência documental: `git diff --check` com a configuração original do repositório terminou com **exit 0**, sem defeito de whitespace; avisos de conversão LF/CRLF não alteram os arquivos. A primeira auditoria auxiliar retornou exit 1 porque PowerShell tratou esse aviso nativo como erro. Uma tentativa com `-c core.autocrlf=false` retornou exit 2 ao considerar CRs de trechos históricos preservados como whitespace; essa opção também mostra diferenças históricas de finais de linha contra o índice. Repetida a conferência normal com tratamento dos avisos: exit 0. Não normalizados os trechos anteriores; preservação integral conferida por hash do prefixo. Diff incremental/acumulado e links desta revisão conferidos, sem testes ou alteração de código.

**Revisão prevista em T052 concluída e somente T052 marcada agora. T001–T052 [X] (52); T053–T055 abertas (3).** Alterações incrementais restritas a este acréscimo e à checkbox de tasks.md; nenhum código corrigido. [Conferência final de preservação, diff, whitespace e Git](../../target/harness-validation/feature-004-t052-2026-10-11/final-review.json). HEAD/branch mantidos; quickstart.md/tasks.md seguem modificados e o launcher T051 segue não rastreado, índice vazio. Sem testes, demonstração, T053+, U1, CI, stage, commit, push, merge ou troca de branch.

## Conferência T053 — contrato, referências e impedimento OpenAPI (2026-10-11)

**Exclusivamente T053, parada antes de T054.** Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`, confirmados pelos quatro comandos Git usuais. Entrada: quickstart.md/tasks.md modificados por T050–T052, launcher T051 não rastreado, índice vazio. Antes de editar, inventariados por SHA-256 os **157 rastreados, o launcher e 256 arquivos de evidências T050–T052/Surefire atuais**, além do tamanho/hash do prefixo integral deste documento e conteúdo de tasks.md. [Base preservada](../../target/harness-validation/feature-004-t053-2026-10-11/baseline.json).

Skill speckit-implement aplicada ao limite autorizado; pré-requisitos PowerShell exit 0, FEATURE_DIR absoluto correto, checklist requirements.md 16/16, zero abertos, somente leitura. Dependência T052 concluída; extensions.yml ausente e ignores existentes suficientes. A skill Analytics Dashboard mencionada e sua referência foram lidas, assim como a orientação da capacidade de planilhas; sua criação de workbook não integra o escopo explícito desta solicitação, restrito a T053/documentação. Nenhum template ou planilha modificado/criado.

### Implementação confrontada com o contrato

Fontes normativas: [orders-openapi.yaml](contracts/orders-openapi.yaml), documento **OpenAPI 3.0.3**, e [README](contracts/README.md). As correspondências abaixo são **inspeção de código**, apoiada por evidências anteriores identificadas adiante; não são novos testes HTTP nem validação OpenAPI completa.

| Elemento do contrato | Implementação conferida / conclusão |
| --- | --- |
| Entrada OrderRequest/OrderItemRequest | [OrderController.java](../../src/main/java/com/devsuperior/dscommerce/controllers/OrderController.java), 24–28, aplica `@Valid @RequestBody`; [OrderRequestDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderRequestDTO.java), 12–18, exige lista não vazia/elementos não nulos/validação aninhada; [OrderItemRequestDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderItemRequestDTO.java), 8–9, exige productId/quantity positivos. [OrderRequestJsonConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/OrderRequestJsonConfig.java), 29–42/67–99, aceita somente items/productId/quantity, rejeita campos extras inclusive nulos/chaves repetidas e exige VALUE_NUMBER_INT com conversão Long/Integer. Tipos decimais, expoente, string ou boolean não são convertidos. Limites de representação: productId 1..9223372036854775807, quantity 1..2147483647. Sem teto comercial de itens. **Limite U1 sobre conteúdo residual após o objeto principal permanece aberto.** |
| Identidade/autorização bearerAuth | [SecurityConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java), 39–66: chain de POST /orders, decoder existente, CLIENT ou ADMIN. [OrderJwtAuthenticationConverter.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationConverter.java), 30–60, exige exp/iat, verifica tempos/nbf/sub canônico e resolve conta; [AuthenticatedAccountService.java](../../src/main/java/com/devsuperior/dscommerce/services/AuthenticatedAccountService.java), 28–45, obtém authorities do banco, sem confiar em name/roles do JWT. [JwtConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/JwtConfig.java), 40–44, restringe assinatura a HS256. [OrderService.java](../../src/main/java/com/devsuperior/dscommerce/services/OrderService.java), 52–59/90/97, revalida conta/perfis e associa comprador da autenticação; titular enviado no corpo é campo extra rejeitado. Nenhum segredo ou perfil integra a confirmação. |
| Resposta 201 / OrderResponse | OrderController.java, 27–28, só constrói 201 após retorno do serviço transacional. OrderService.java, 89–98, cria/mapeia id/accountId/createdAt/status/items/total; [OrderResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderResponseDTO.java), 10–18, fixa esses seis campos e UTC com milissegundos. Estado inicial [Order.java](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java), 48–57: WAITING_PAYMENT. Instante vem de Clock UTC e é truncado a milissegundos; não é timestamp do commit. IDs positivos gerados como Long; sem promessa de sequência contínua. |
| Itens e ordem / OrderItemResponse | OrderService.java, 73–86/93–96, percorre a ordem recebida e mapeia productId/quantity/unitPrice/subtotal. Order.java, 55–57/82–85, persiste posição e usa `@OrderBy("position ASC")`; [OrderItemResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OrderItemResponseDTO.java), 6–18, contém somente os quatro campos do schema e valida formato monetário. Lista de saída copiada/imutável, sem serialização de entidades JPA. |
| Money / preço histórico / limites | OrderService.java, 78–85/102–115: preço do produto lido na transação, > 0, copiado ao item; `setScale(2, UNNECESSARY)`, precision <= 19, subtotal/soma verificados antes de save. [OrderItem.java](../../src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java), 59–63/109–111: snapshot decimal19,2 e multiplicação BigDecimal exata; Order.java, 108–110, soma desses subtotais históricos. Saída `toPlainString()` em campos String, padrão Money positivo 0.01..99999999999999999.99, exatamente duas casas e até 20 caracteres, sem expoente/arredondamento. Catálogo mantém formato anterior. Overflow/casas não representáveis rejeitam integralmente com 422; não há frete/desconto/acréscimo. |
| Erros, envelope e precedência | [GlobalExceptionHandler.java](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java), 35–65/88–142/145–163, traduz os erros MVC/guardas/conclusão; [OrderAuthenticationEntryPoint.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderAuthenticationEntryPoint.java), 28–38 e [OrderAccessDeniedHandler.java](../../src/main/java/com/devsuperior/dscommerce/security/OrderAccessDeniedHandler.java), 26–30, tratam falhas dos filtros. [ErrorResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/ErrorResponseDTO.java) tem exatamente status/error/message/path. Segurança precede leitura/Bean Validation; serviço trata inexistência/duplicata/preço/capacidade; falhas internas de resolução ou persistência não viram credencial inválida/sucesso. |
| path / headers | Todos os escritores usam `request.getRequestURI()` para path externo, inclusive /api/orders; o matcher/advice usa caminho da aplicação sem perder o prefixo no envelope. EntryPoint, 32–33, e advice, 155–156, emitem **WWW-Authenticate: Bearer em 401**. 403 não adiciona challenge; o README permite esse header opcional, portanto não é divergência. Content-Type de erros de filtro é application/json; DTOs MVC usam conversor JSON. Falha interna produz mensagem fixa, sem token, corpo, SQL, hash, chave ou stack trace. |
| Ausência de consulta / Location / repetição | Inventário dos mappings produtivos confirma somente POST /orders novo, além de POST /login e GETs de produtos; **não existe GET /orders** ou outro método de pedido. OrderController retorna `.status(CREATED).body(response)`, sem `.created(...)` ou header Location; nenhum adaptador de pedido o adiciona. Cada chamada válida constrói novo agregado, sem idempotência/reutilização/limpeza de carrinho. A ordem de commit/201 e limites de falhas posteriores ao commit permanecem os registrados em T052. |

Mensagens/códigos conferidos contra README e exemplos YAML, sem alterar contratos:

| HTTP / error | message implementada |
| --- | --- |
| 400 / Bad Request | Invalid order data |
| 401 / Unauthorized | Authentication required or invalid |
| 403 / Forbidden | Order registration not permitted |
| 404 / Not Found | Product not found |
| 422 / Unprocessable Entity | Duplicate product in order; Product price is not valid for order; Order value cannot be represented — selecionada pelo motivo do erro |
| 500 / Internal Server Error | Order registration could not be completed |

### Parsing YAML e validação OpenAPI: resultados distintos

**Parsing atual do documento inteiro:** SnakeYAML **2.2**, auxiliar existente ReadWorkflowYaml.java com SafeConstructor e `setAllowDuplicateKeys(false)`, Java **21.0.12.1 / Zulu**. Executado na raiz:

```powershell
& 'C:/Program Files/Zulu/zulu-21/bin/java.exe' --class-path C:/Users/ronal/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar C:/Users/ronal/dscommerce-speckit/target/harness-validation/ci-review/ReadWorkflowYaml.java C:/Users/ronal/dscommerce-speckit/specs/004-registrar-pedido/contracts/orders-openapi.yaml
```

Resultado **exit 0**: arquivo completo lido como mapping, sintaxe YAML aceita e política de rejeição de chaves YAML duplicadas ativa. [Saída desta execução](../../target/harness-validation/feature-004-t053-2026-10-11/yaml-parse.txt). Isso não verifica todas as regras da especificação OpenAPI, exemplos ou comportamento HTTP; não é validação OpenAPI completa.

**Validação OpenAPI completa impedida por falta de ferramenta compatível instalada nos caminhos consultados.** Get-Command não encontrou redocly/swagger-cli/openapi-spec-validator/node/npm/npx/py no PATH; Python/python3 do PATH são aliases WindowsApps. Conferido Python **3.12.14** do runtime local: find_spec/distributions não encontraram openapi_spec_validator, openapi_schema_validator, prance, yaml ou jsonschema. Node **v24.19.0** desse runtime funciona, mas resolução de @apidevtools/swagger-parser, @redocly/cli, swagger-cli, yaml e ajv retornou unavailable; inventário de dependências locais/cache pnpm não encontrou os validadores. Diretórios de Swagger/OpenAPI no cache Maven consultado também ausentes. Não instalada ferramenta/dependência nem alterado POM/Wrapper/build do projeto.

Tentativa real de invocação do validador sobre o documento completo:

```powershell
& 'C:/Users/ronal/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -m openapi_spec_validator specs/004-registrar-pedido/contracts/orders-openapi.yaml
```

Resultado **exit 1**, **No module named openapi_spec_validator**. Versão do validador: **indisponível, módulo não instalado**. Nenhuma validação OpenAPI realizada por essa tentativa; esse exit não reprova o documento, registra o impedimento. [Diagnóstico da invocação](../../target/harness-validation/feature-004-t053-2026-10-11/openapi-validator-attempt-stderr.txt), [ferramentas, versões, comandos e resultados](../../target/harness-validation/feature-004-t053-2026-10-11/validation-result.json). Não substituída a ferramenta por validador caseiro, parsing ou revisão manual sob o nome de validação integral.

### Links e referências internas

Executado auxiliar documental com biblioteca padrão do Python **3.12.14**, em diretório ignorado de evidências, sem dependência do projeto:

```powershell
& 'C:/Users/ronal/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' target/harness-validation/feature-004-t053-2026-10-11/check-feature-links.py
```

Resultado **exit 0** na base anterior ao acréscimo T053: **8 arquivos Markdown**, **247 ocorrências de links locais**, todos com destino existente; sete links externos identificados, sem requisições de rede. Caminhos inline/definições de referência e anchors de heading/linha são conferidos fora de blocos/trechos de código; nenhum anchor local problemático encontrado. Os comandos/caminhos em exemplos de código foram examinados separadamente quanto às referências pertinentes à conferência, sem executar procedimentos futuros. Os links novos desta seção são conferidos na auditoria final.

No YAML já parseado, **19 ocorrências de $ref** apontam a schemas presentes: PositiveId, Quantity, Money, OrderRequest, OrderItemRequest, OrderResponse, OrderItemResponse e ErrorResponse (oito definições). Nenhum destino ausente; bearerAuth da operação corresponde ao securityScheme declarado. Referências numéricas dos artefatos confrontadas com T001–T055, FR-001–017 e SC-001–009, sem ID fora das faixas definidas. Sem links locais quebrados ou desvio de referência identificado. [Inventário e limites do coletor](../../target/harness-validation/feature-004-t053-2026-10-11/links-review.json), [comando/exit](../../target/harness-validation/feature-004-t053-2026-10-11/links-command.json). Resolução de paths/nomes de schema é conferência documental; não cobre por si só semântica completa do OpenAPI ou prova de cobertura dos requisitos.

### Evidências HTTP anteriores e divergências/limites

- **T050, execução automatizada anterior:** [inventário Surefire](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json) no HEAD indicado, suíte completa 904/0 falhas/0 erros/0 ignorados, BUILD SUCCESS/0. OrderControllerTest soma 141 casos (138 de entrada e 3 de sucesso; slices/mocks), OrderDtoContractTest 36, OrderRegistrationIntegrationTest 9, OrderSecurityIntegrationTest 185, OrderPublicRegressionIntegrationTest 14 e OrderServiceTest 42. Consultadas assertions de campos/tipos/ordem/histórico, limites, precedência, envelope externo /api/orders e Bearer em 401. Integrações reais e slices são evidências diferentes; suas aprovações não validam automaticamente o arquivo OpenAPI. Nenhum teste repetido aqui.
- **T051, demonstração anterior:** [resultado sanitizado](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json), servidor/login/JWT reais, dois 201 completos com ids distintos, WAITING_PAYMENT/UTC, itens na ordem e dinheiro String 10.00/7.50, subtotais 20.00/22.50, total 42.50; ausência de Location e persistência independente. Sucesso/repetição/ativação de fixtures não substituem provas automatizadas de rollback; nenhuma demonstração executada em T053.
- **U1 continua pendente/não aplicado:** o leitor de OrderRequestJsonConfig retorna em END_OBJECT sem conferir EOF, já apontado em T052. Assim, a descrição contratual de rejeição de JSON inválido não está integralmente comprovada para conteúdo após o objeto principal. Nenhuma reprodução/correção de produção ou testes executada agora; não declarada conformidade integral da entrada.
- **U2 mantém somente a resolução de T051**, ativação explícita e reproduzível das fixtures de demonstração. Não ampliada a prova para todos os testes/ambientes. Nenhum outro desvio material identificado nos elementos do contrato conferidos, com validação OpenAPI integral ainda impedida. As frases de planejamento em README/YAML e os registros históricos permanecem preservados; não são usados para negar a implementação atual nem para alegar novas execuções.

**T053 concluída e somente sua checkbox marcada agora**, conforme condição da tarefa de registrar a validação **ou seu impedimento real**. O bloqueio da validação integral permanece explícito; não declarado OpenAPI aprovado. **T001–T053 [X] (53); T054–T055 abertas (2).** Alterações incrementais: acréscimo neste quickstart e checkbox T053 em tasks.md, mais auxiliares/evidências documentais ignorados em target. Contratos, código produtivo/testes, launcher, POM/Wrapper/seed e evidências T050–T052 preservados. [Auditoria final de preservação, diff, links, whitespace e Git](../../target/harness-validation/feature-004-t053-2026-10-11/final-review.json). Branch/HEAD mantidos, dois documentos modificados e launcher T051 não rastreado, índice vazio. Sem testes, demonstração, T054+, U1, CI, stage, commit, push, merge ou troca de branch.

## Consolidação T054 — evidências e rastreabilidade (2026-10-11)

**Exclusivamente T054, parada antes de T055.** Confirmados por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`: raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`. Entrada preservada: quickstart.md/tasks.md modificados por T050–T053, launcher T051 não rastreado e índice vazio. Skill speckit-implement limitada à tarefa solicitada; pré-requisitos exit 0, feature correta, checklist requirements.md 16/16, zero abertos, somente leitura. Esta seção é um índice das evidências existentes, **sem nova execução de testes, demonstração ou validação OpenAPI**. A conferência de referências/diff desta alteração não executa T055.

Fontes da rastreabilidade: [spec.md](spec.md), [tasks.md](tasks.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato OpenAPI](contracts/orders-openapi.yaml) e [README do contrato](contracts/README.md). USn.k identifica o cenário da história, FR o requisito e SC o critério de sucesso, conforme a matriz de tasks.md. A ligação entre evidência e critério não declara cobertura integral de todas as combinações ou conformidade onde existe pendência.

### Datas, revisões e contagem

As datas abaixo são locais; horários detalhados e fuso UTC−03:00 estão nos registros de cada execução. As bases históricas permanecem distintas:

| Base | HEAD da execução / estado relevante |
| --- | --- |
| T005/T006, 2026-10-06 | `da1cae9910fc06bfdaf78ab26ff88d03fd953326`; T006 inclui implementação/alterações de trabalho registradas naquele checkpoint. |
| T016–T025, 2026-10-07/08 | `ead6974f6c29c583a25c37cd4df5a91f0ce58bec`; testes RED e etapas GREEN têm conteúdos de trabalho diferentes, descritos nos registros anteriores. |
| T026–T041, 2026-10-08/09/10 | `0344dc0199fd8135f98e852e7d6fedc06b124d9a`; alterações US2 e preparação dos slices identificadas por execução. |
| T047/T048/T049, 2026-10-10 | `73d3398965b25027d393202a2c5a984984abd0ca`; T048 altera somente expectativas de testes, preservando as falhas T047. |
| T050–T053, 2026-10-11 | `e18b4398638863e544404bdd37d77a57d567e428`; T050 começou com árvore limpa. T051 acrescentou o launcher de teste; T052/T053 são conferências posteriores com esses documentos/launcher existentes. |

HEAD sozinho não descreve um working tree modificado: os diffs, fontes executadas e inventários das etapas continuam nas evidências originais. **T050 não aprova retroativamente o launcher acrescentado em T051 nem representa teste dos documentos posteriores.** Nesta T054 não houve execução que aprove um novo estado funcional.

Os 904 casos consolidados em T049 vieram de 30 classes GREEN de T047 e duas classes corrigidas/reexecutadas em T048. T047 havia executado 904 casos, com 902 aprovações e duas falhas, em invocações separadas. **Isso não era suíte completa em uma invocação. T050 executou e contou novamente 904 casos na suíte completa**, com zero falhas/erros/ignorados. Não somar T047 + T048 + T050, REDs, tentativas ou requisições HTTP como testes distintos. Suites aninhadas/vazias não duplicam testcase; cada total refere-se ao conjunto e à execução identificados.

### RED/GREEN e falhas de preparação

Os comandos da tabela são históricos, executados na raiz; nenhum foi repetido nesta consolidação. BUILD FAILURE corresponde a exit 1 e BUILD SUCCESS a exit 0 nos registros abaixo.

| Data / tarefas / rastreabilidade | Comando e resultado documentados | Evidência e interpretação |
| --- | --- | --- |
| 2026-10-06, T005 → T006; US1.1–4/8 e US2.4–6, FR-003/010/011, SC-005 | RED: `.\mvnw.cmd '-Dtest=UserAccountRoleRepositoryTest' test`, 11 casos, 10 falhas, zero erros/ignorados; duas execuções preservadas, sem somar 22 casos distintos. GREEN: `.\mvnw.cmd '-Dtest=UserAccountRoleRepositoryTest,UserAccountRepositoryTest' test`, 15/0/0/0, sucesso/0. | [RED final](../../target/harness-validation/feature-004-t005-2026-10-06/t005-red-final-result.json) e [GREEN](../../target/harness-validation/feature-004-t006-2026-10-06/repository-green-result.json). RED comportamental com contexto iniciado: persistência de perfis ausente e lookup da conta divergente; não erro de compilação/preparação. |
| 2026-10-07/08, T016 → T017–T025; US1, FR-001–009/016/017, SC-001–003/005/007/008 | T016: seis comandos `.\mvnw.cmd "-Dtest={classe}" test`, concretizados em OrderRegistrationIntegrationTest (9/9), OrderAggregateTest (18/17), OrderRepositoryTest (23/23), OrderServiceTest (11/11), OrderControllerTest (3/3) e OrderSecurityIntegrationTest (9/9), valores testes/falhas, zero erros/ignorados; todos failure/1. T025: dez alvos separados GREEN, incluindo `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" test`, 9/0/0/0, sucesso/0. | [Comandos/resultados RED](../../target/harness-validation/feature-004-t016-2026-10-07/execution-summary.json) e [sucesso integrado T025](../../target/harness-validation/feature-004-t025-2026-10-08/OrderRegistrationIntegrationTest-result.json). RED executável: assertions sobre schema ausente, stubs, invariantes e HTTP 404 em vez de 201; contexto/fixtures válidos. Assertions posteriores bloqueadas pela primeira falha não são provas aprovadas. |
| 2026-10-07, T018; constraints de T013/T018, FR-008/009/012/014, SC-003/004/009 | `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test`: primeira tentativa 23 casos/22 falhas; segunda 23/0/0/0, sucesso/0. | [Segundo resultado e comando](../../target/harness-validation/feature-004-t018-2026-10-07/order-repository-run2-result.json), [relatórios](../../target/harness-validation/feature-004-t018-2026-10-07/reports-summary.json). Primeiro resultado decorreu da comparação textual dos metadados H2 (`CAST(0 AS NUMERIC(1))`), corrigida somente na normalização da assertion. Não foi novo RED de constraint ausente, nem remoção de constraint. |
| 2026-10-08, T026/T032 → T033–T040; US2, FR-002–005/008/010–014, SC-004/005/009 | T026: `.\mvnw.cmd "-Dtest=OrderControllerTest" test`, 111 casos/106 falhas/0 erros/0 ignorados. T032: comando integral abaixo, 245 casos/184 falhas/0 erros/0 ignorados, failure/1. T040 GREEN por alvo, seguido do complemento de precedência, sem reclassificar o RED original. | [Execução RED T032](../../target/harness-validation/feature-004-t032-2026-10-08/result.json) e [GREEN T040](../../target/harness-validation/feature-004-t040-2026-10-09-175211/results-summary.json). RED comportamental com contextos válidos. O slice de entrada de T026 usa serviço mockado/filtros desativados; um 201 indevido nesse slice não prova compra real. |
| 2026-10-09 → 10, T041; regressões de login/FR-015/SC-006 | LoginControllerTest: 13 erros de preparação por dependência Clock ausente no slice; após apoio local, mesmo alvo 13/0/0/0, sucesso/0. | [Checkpoint com primeira falha](../../target/harness-validation/feature-004-t041-2026-10-09-182300/results-summary.json), [comando e resultado final](../../target/harness-validation/feature-004-t041-completion-2026-10-10/LoginControllerTest-result.json). Falha de preparação, sem assertions HTTP alcançadas; não RED funcional de login. |
| 2026-10-10, T047 → T048; infraestrutura/limites monetários e regressões, FR-008/012/015, SC-004/006/009 | Seleção serial por classe descrita abaixo: OrderTestSupportTest 5 casos/1 falha e OrderServiceContextTest 4 casos/1 falha em T047; mesmos alvos em T048, respectivamente 5 e 4 casos aprovados, sucesso/0, zero erros/ignorados. | [Causas originais](../../target/harness-validation/feature-004-t047-2026-10-10/cause-review.json), [GREEN suporte](../../target/harness-validation/feature-004-t048-2026-10-10/OrderTestSupportTest/result.json), [GREEN contexto](../../target/harness-validation/feature-004-t048-2026-10-10/OrderServiceContextTest/result.json). Suporte: expectativa de Clock único incompatível com Clock produtivo e Clock de teste @Primary, classificação preparação/configuração. Contexto: RED comportamental da assertion legada que esperava IllegalArgumentException, enquanto o contrato implementado lança InvalidOrderException/UNREPRESENTABLE_VALUE. Não prova overflow aceito/regressão HTTP. Ajustadas expectativas, preservados contratos e assertions de ausência de escrita. |

Comando integral de T032, com ordem/execução serial explícitas:

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTest,OrderSecurityIntegrationTest,OrderServiceTest,OrderRollbackIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" "-Dsurefire.runOrder=alphabetical" test
```

Para T040, seu complemento de precedência e T047/T048, a abreviação `{classe}` na tabela seguinte representa **cada nome concreto indicado**, em invocação separada. Os result.json originais guardam o comando concreto, horários, resultado e exit; não se trata de seleção sugerida ou executada em T054:

```powershell
.\mvnw.cmd "-Dtest={classe}" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

### Evidências por requisito, critério e tarefa

| Grupo / rastreabilidade | Data, comando e resultado | Localização e alcance da evidência |
| --- | --- | --- |
| Constraints JDBC, posições/histórico; T012/T013/T018; FR-008/009/012/014, SC-003/004/009 | 2026-10-07, `.\mvnw.cmd "-Dtest=OrderRepositoryTest" test`, GREEN 23/0/0/0, sucesso/0; reexecutado em T047 e na suíte T050. | [Relatórios T018](../../target/harness-validation/feature-004-t018-2026-10-07/reports-summary.json) e [fonte do teste](../../src/test/java/com/devsuperior/dscommerce/repositories/OrderRepositoryTest.java). JDBC real: NOT NULL (23502), FK (23506), unicidade produto/posição por pedido (23505), CHECK (23513); zero/negativos rejeitados, centavo/teto decimal19,2, quantity máxima e posição zero aceitos. Roundtrip JPA/rollback do teste de repositório não prova commit do serviço. |
| Registro, comprador, confirmação e carrinho; US1.1–4/7/8, FR-001–003/006/007/016, SC-001/005/007; T011/T014/T015 → T020–T025 | 2026-10-08, `.\mvnw.cmd "-Dtest=OrderRegistrationIntegrationTest" test`, 9/0/0/0, sucesso/0. Mesma classe aprovada em T047 e T050. | [Comando/resultado T025](../../target/harness-validation/feature-004-t025-2026-10-08/OrderRegistrationIntegrationTest-result.json), [teste integrado](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java). JWT/filtros reais, CLIENT/ADMIN/ambos, comprador autenticado, seis campos, WAITING_PAYMENT, UTC, itens ordenados, dinheiro String e total 42.50. Sem operação backend que esvazie carrinho. |
| Preço lido antes da compra e snapshot posterior; US1.5/6, FR-005/008/009, SC-002/003; T011–T014 → T017–T020/T023 | 2026-10-08, mesmo comando T025 acima, sucesso/0. | [Resultado T025](../../target/harness-validation/feature-004-t025-2026-10-08/OrderRegistrationIntegrationTest-result.json). Mudança antes da leitura 10.00 → 11.25 produz total 45.00; mudança posterior para 99.99/25.00 preserva preços históricos 10.00/7.50 e total 42.50, conferidos no banco. Sem promessa de lock/congelamento do catálogo. |
| Repetição/Q1; US1.9/10, FR-017, SC-008; T011/T014 → T020/T024/T025, T051 | 2026-10-08, mesmo comando T025; 2026-10-11, comando de demonstração abaixo; ambos sucesso/0. | [Teste T025](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java), [resultado T051](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json). Dois envios criam dois IDs e preservam o primeiro pedido. Descartar o primeiro MvcResult modela o consumidor sem usar a confirmação; não simula perda física de transporte. Em T051 ambas as respostas foram recebidas. Sem idempotência e sem GET /orders. |
| Entrada estrita, extras, duplicatas, produtos/preços/overflow; US2.1–3/7/8/10–13, FR-002/004/005/008/012–014, SC-002/004/009; T026/T028/T032 → T033/T034/T038/T040 | 2026-10-09, seleção serial: OrderControllerTest 141, OrderServiceTest 42 e OrderRepositoryTest 23 casos, GREEN/0, zero falhas/erros/ignorados; T050 repete essas classes na suíte. | [Resultados T040](../../target/harness-validation/feature-004-t040-2026-10-09-175211/results-summary.json), [inventário T050](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json). Limites Long/Integer, decimal19,2, UNNECESSARY, overflow subtotal/soma e rejeições sem escrita. Slices, unidades e integrações têm alcances diferentes. **Conteúdo residual após o objeto JSON (U1) permanece fora da conformidade comprovada.** |
| Identidade/perfis, JWT, guardas tardias e precedência; US1.1–4/8 e US2.4–6, FR-003/010/011/012, SC-004/005; T005–T010/T015/T027/T028 → T035–T038/T040/T041 | 2026-10-09, OrderSecurityIntegrationTest GREEN inicial 85 casos; complemento no mesmo dia com o comando serial para essa classe: 185/0/0/0, sucesso/0. T047: OrderJwtAuthenticationConverterTest 48 e OrderJwtAuthenticationPipelineTest 25, GREEN/0. T050: mesmos conjuntos aprovados. | [Complemento de precedência](../../target/harness-validation/feature-004-t040-gap-2026-10-09-181144/OrderSecurityIntegrationTest-result.json), [inventário T050](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json). Conta/perfis obtidos e revalidados no banco, JWT real sem confiar em roles/name, guardas no proxy após autenticação, path raiz/api. Cem casos adicionais: 92 respostas 401, quatro 403, quatro 500 antes da entrada inválida; 185 substitui o conjunto anterior, não se soma a 85. |
| Três rollbacks e controle positivo; US2.9/FR-012/SC-004; T029–T032 → T038/T039/T040, T047/T050 | 2026-10-09, comando serial com OrderRollbackIntegrationTest, 17:55:56.824 → 17:56:33.431, 9/0/0/0, sucesso/0; aprovado novamente em T047 e em T050. | [Comando/resultados T040](../../target/harness-validation/feature-004-t040-2026-10-09-175211/OrderRollbackIntegrationTest-result.json), [observações T047](../../target/harness-validation/feature-004-t047-2026-10-10/rollback-proof.txt), [XML da suíte T050](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/reports/TEST-com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.xml). Detalhes e limites abaixo; não usar demonstração ou rollback automático de teste como substitutos. |
| Catálogo/detalhes; US3.1/2, FR-015, SC-006; T042/T043/T046–T049 | 2026-10-10, seleção serial T047: PublicCatalogSecurityIntegrationTest 33, ProductControllerTest 11, ProductCatalogServiceTest 6, ProductRepositoryTest 6, PublicProductDetailSecurityIntegrationTest 38, todas GREEN/0; T050 aprovadas. | [Prova pós-rejeição T047](../../target/harness-validation/feature-004-t047-2026-10-10/public-regression-proof.txt), [inventário T050](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json), [spec catálogo](../001-consulta-catalogo-produtos/spec.md), [spec detalhe](../002-visualizar-detalhes-produto/spec.md). Paginação/ordem/vazio/400, detalhe/categorias/404 e Bearer inválido irrelevante nas rotas públicas; contratos anteriores preservados. |
| Login, H2 console, handlers e jornadas públicas após pedidos; US3.3/4, FR-015/012, SC-006/004; T044–T049 | 2026-10-10, seleção serial T047: LoginControllerTest 83, H2ConsoleSecurityIntegrationTest 15, GlobalExceptionHandlerRegressionTest 84, OrderPublicRegressionIntegrationTest 14, todas GREEN/0; T050 aprovadas. | [Prova das jornadas](../../target/harness-validation/feature-004-t047-2026-10-10/public-regression-proof.txt), [inventário T050](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/suite-inventory.json), [spec login](../003-login-autenticacao/spec.md). Login 200/400/401/500, envelopes/paths/confidencialidade; H2 com servidor real e SAMEORIGIN. Quatorze casos pós-rejeição exercitam sete causas na raiz/api, 28 blocos públicos/588 requisições auxiliares, **não 588 testes extras**. Slice de login mocka serviço; login real/JWT têm provas próprias nas integrações. |
| Suíte completa T050; integração das três histórias, FR-001–017/SC-001–009 com limites descritos | 2026-10-11, 12:57:10.782 → 13:00:48.019, `.\mvnw.cmd test`; Java 21.0.12.1 Azul/Zulu, Maven Wrapper 3.9.16; **904/0/0/0, BUILD SUCCESS, exit 0**. | [Comando/HEAD/estado](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/result.json), [resumo](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/summary.json), [inventário dos casos](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/testcase-inventory.json). Uma invocação sem seleção/exclusões/skip, 32 classes-base, 37 XML + 37 TXT arquivados, Working tree de código limpo. Aprovação local Windows/H2, não CI/Linux ou prova de cobertura integral. |
| Demonstração T051 / U2; US1.1–4/8–10 e consultas/login de US3, FR-001/003/006/007/015/017, SC-001/005–008 | 2026-10-11, 13:13:50.652 → 13:14:37.385, comando abaixo; BUILD SUCCESS/0, 11 requisições reais. | [Execução](../../target/harness-validation/feature-004-t051-2026-10-11/execution.json), [resultado sanitizado](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json), [launcher de teste](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java). Fixtures explicitamente criadas em H2 isolado: 10.00/7.50 × 2/3 = 42.50; login/JWT reais, 401 sem token, dois 201 completos/IDs diferentes e JDBC independente após respostas; consultas/login antes/depois. Apenas processo/contexto próprio encerrado; credenciais/token/segredo não registrados. |
| Revisão T052; arquitetura/escopo, FR-003/008/009/012, SC-003–005, constituição/plano | 2026-10-11, leitura/confronto dos artefatos/código e `git diff --check` normal exit 0. **Revisão de código/documentação, sem testes ou demonstração.** | [Revisão final T052](../../target/harness-validation/feature-004-t052-2026-10-11/final-review.json), [constituição](../../.specify/memory/constitution.md); trechos/caminhos na seção T052 preservada. Serviço concentra regras, DTOs na fronteira, proxy transacional, exceptions propagadas, retorno do serviço antes do 201. Launcher @TestConfiguration test-only ativado explicitamente; não demonstrada nova suíte com launcher, nem conformidade integral por causa de U1. |
| Conferência T053; contrato/referências, FR-001–017/SC-001–009 com ressalvas | 2026-10-11, parsing SnakeYAML 2.2/Java 21.0.12.1 exit 0; tentativa `python -m openapi_spec_validator` pelo runtime local Python 3.12.14, exit 1 por módulo ausente; comandos completos preservados na seção T053. **Validação OpenAPI integral impedida.** | [Ferramentas/comandos/resultados](../../target/harness-validation/feature-004-t053-2026-10-11/validation-result.json), [diagnóstico](../../target/harness-validation/feature-004-t053-2026-10-11/openapi-validator-attempt-stderr.txt), [referências](../../target/harness-validation/feature-004-t053-2026-10-11/links-review.json). Inspeção de entrada/201/identidade/ordem/strings monetárias/status/limites/envelope/path/Bearer; sem GET /orders ou Location prometendo-o. Parsing YAML, referências existentes e HTTP anterior **não equivalem à validação OpenAPI completa**. |

Comando exato de T051, preparação explícita e reproduzível de fixtures em classpath/perfil de teste:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.devsuperior.dscommerce.demo.OrderDemoApplication' '-Dspring-boot.run.profiles=test'
```

`OrderDemoApplication` inclui explicitamente DemoFixtures na SpringApplication e chama createAccount/createProduct de [OrderTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/OrderTestFixture.java). Apenas o classpath/perfil não comprova ativação. URL H2 exclusiva, loopback/porta aleatória, sem importação do Clock controlado, alterações na seed pública ou atribuição de perfis produtivos. Não executado esse comando em T054.

### Atomicidade: observações e limites das provas de commit

As observações abaixo têm correspondência nos relatórios T040/T047 e no XML arquivado de T050 indicados na tabela; são testes automatizados de [OrderRollbackIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java), com serviço por proxy/filtros reais, sem transação externa do teste envolvendo a requisição. Contagens pós-resposta são obtidas por conexão independente **antes da limpeza**, confrontadas com baseline de pedidos/itens.

| Prova / tarefas | O que foi observado |
| --- | --- |
| SQL tardio, T029 | INSERT do pedido e do primeiro item concluídos; terceiro INSERT, do segundo item, falha com SQLState 23513. Uma ordem/um item eram visíveis dentro da transação. Resposta 500 e restauração integral do baseline por JDBC independente, sem parcial persistido. |
| Processamento após flush, T030 | saveAndFlush real retornou e três INSERTs concluíram; um pedido/dois itens visíveis na conexão vinculada à transação, autocommit false. Falha de processamento propagada; 500 e baseline integralmente restaurado. Flush não foi tratado como commit. |
| Conclusão em beforeCommit, T031 | Corpo produziu DTO com transação ativa; proxy ainda não havia retornado. Callback beforeCommit recebeu DTO já produzido, observou três INSERTs/um pedido/dois itens e lançou a falha antes do commit físico. 500, mesma falha propagada e baseline restaurado após conclusão. |
| Controle positivo pós-commit, T029 | Sem falha injetada, 201 após retorno do proxy; conexão independente confirmou incremento de um pedido/dois itens e três INSERTs concluídos. Não depender apenas do DTO ou da conexão da transação para afirmar persistência. |

O vínculo `@Transactional(REQUIRED, rollbackFor = Exception.class)` no método público, sua invocação pelo controller via proxy e a propagação sem captura da falha foram inspecionados em T052. Na requisição HTTP normal sem transação externa, o interceptor conclui a transação antes de devolver ao controller, que então constrói o 201. **Se outro chamador já tiver transação externa, REQUIRED participa dela: o retorno desse método não comprova o commit externo.**

beforeCommit comprova falha na fase anterior ao commit físico; **não é injeção de falha física em Connection.commit nem prova de resultado incerto de conexão durante commit**. Não existe promessa de rollback após commit concluído ou por falha posterior de serialização/transporte, nem de recuperação de uma resposta fisicamente perdida. A2/A3, concorrência, outro banco/ambiente e falhas de infraestrutura além dos pontos instrumentados continuam com os limites do planejamento. Nenhuma demonstração manual, mock, rollback automático de repositório ou contagem apenas na mesma conexão substitui essas três provas. T039 condicional não implica correção transacional adicional quando as provas já sustentavam a atomicidade existente.

### Histórico preservado e pendências

- **2026-10-01/02:** permanecem integralmente as seções de planejamento/revisão/geração e as evidências originais; parsing/links/checks documentais dessa época não eram testes funcionais de pedidos. [Evidência documental de 2026-10-02](../../target/harness-validation/feature-004-doc-review-2026-10-02/yaml-review.txt). T001/T002 usaram Wrapper em 2026-10-02, HEAD `4afb5e7634ce5236df2aeb2ba57dc228578be292`: linha de base 79 casos, seleção e suíte completa separadas, não 158 testes distintos. O primeiro erro NullArray no bootstrap do Wrapper é preparação, não RED da aplicação. [Resultado da suíte de base](../../target/harness-validation/feature-004-t001-t002-2026-10-02-1931/full-suite-result.json).
- **Windows/Linux anteriores:** [guia do harness](../../docs/harness.md) mantém a execução Windows de 2026-09-30 com **`mvn test`**, HEAD `e3cd01c825ffb8eaecde18a82d3b0d18a81bbc20`, 79/0/0/0, BUILD SUCCESS. Não reescrita como execução pelo Wrapper. O CI Linux de 2026-09-30 usa `./mvnw --batch-mode --no-transfer-progress test`, HEAD `1d49b28d46d6a73cf6c39b1077574c1306dba7f2`, run 36776432450, Ubuntu 24.04/Java 21 Zulu, 79/0/0/0. São registros históricos de revisões/eventos distintos; não aprovam o HEAD atual. Nenhuma consulta/execução remota feita agora; não presumida disponibilidade atual do artefato remoto com retenção limitada.
- **U1 pendente:** OrderRequestJsonConfig retorna após END_OBJECT sem comprovar rejeição de conteúdo residual após o objeto JSON. Não reproduzido/corrigido nesta etapa e não declarada conformidade integral da entrada.
- **U2 resolvido somente para ativação explícita e reproduzível das fixtures de demonstração**, comprovada em T051. Não ampliado para provisionamento produtivo, todos os testes/ambientes ou convivência JUnit posterior ao launcher.
- **OpenAPI integral impedido em T053:** validador compatível não disponível nos caminhos consultados; tentativa real exit 1 por módulo ausente. Parsing YAML aprovado, revisão manual e evidência HTTP anterior permanecem categorias distintas; não declarar OpenAPI integral aprovado.
- **T051 não substitui rollback automatizado.** O sucesso da demonstração comprova seu caminho real de login/registro/repetição/consultas e persistência independente, com os limites de commit acima. Testes locais H2/MockMvc/slices não aprovam automaticamente deploy, outro banco, todos os cenários de falha física ou todas as combinações de segurança.

Registros que dizem “futuro”, “tarefas abertas” ou “U2 pendente” continuam como estados datados do planejamento/execução anterior; esta consolidação não os altera nem os atribui ao estado atual. Evidências locais em target são ignoradas pelo Git e precisam permanecer disponíveis nesse workspace; este documento conserva os resultados e suas referências, sem versionar relatórios/logs. Sem testes, demonstração, novas validações, correção de U1, T055, CI, stage, commit ou push nesta tarefa.

**T054 concluída; somente T054 marcada nesta etapa. T001–T054 [X] (54), T055 aberta (1).** Conferidos os 57 destinos locais acrescentados, todos existentes. O prefixo integral anterior deste quickstart (657.571 bytes), os 156 demais arquivos de fontes/documentos inventariados (155 rastreados e launcher não rastreado) e os 6.074 arquivos de evidências em target/harness-validation e target/surefire-reports permanecem idênticos por SHA-256 à entrada. Comparação de tasks.md confirmou exclusivamente `[ ] T054` → `[X] T054`, sem alteração das marcações anteriores. Não criado arquivo auxiliar/relatório nesta tarefa; os inventários de conferência permaneceram em memória.

Diff incremental e acumulado revisados; `git diff --check` normal: **exit 0**, sem defeito de whitespace, somente avisos conhecidos de conversão LF/CRLF. Não normalizados finais de linha históricos. Raiz/branch/HEAD mantidos; status final com quickstart.md/tasks.md modificados e o launcher T051 não rastreado, índice vazio. HEAD `e18b4398638863e544404bdd37d77a57d567e428`. Pendências/alcances: U1 aberto, U2 apenas fixtures explícitas, OpenAPI integral impedido; demonstração e provas de commit conservam os limites acima. Nenhuma execução/entrega de T055 alegada.

## Fechamento T055 — diff, arquivo novo e ressalvas finais (2026-10-11)

**Exclusivamente T055; revisão final sem execução de testes, demonstração ou validações funcionais adicionais.** Confirmados `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`. Entrada com exatamente os três arquivos pendentes abaixo e índice vazio. Preservadas todas as alterações/evidências de T050–T054.

Skill speckit-implement aplicada ao limite autorizado. Pré-requisitos PowerShell exit 0, FEATURE_DIR absoluto correto; checklist requirements.md 16/16, zero abertos, somente leitura. [T055 e sua dependência T054](tasks.md) conferidas; T054 concluída antes desta revisão. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks. Nenhuma configuração Git alterada, final de linha histórico normalizado, stage, commit, push, CI ou correção de U1.

### Diff acumulado e arquivo completo revisados

Revisados `git diff HEAD -- specs/004-registrar-pedido/quickstart.md`, `git diff HEAD -- specs/004-registrar-pedido/tasks.md`, `git diff HEAD --name-only`, `git diff HEAD --numstat` e `git diff --cached --name-only`. Antes deste acréscimo, quickstart tinha **331 linhas acrescentadas/zero removidas**, somente seções datadas de T050–T054; tasks tinha cinco substituições de checkbox, T050–T054. Não encontrados outros rastreados modificados ou conteúdo staged. Estados antigos de planejamento, Windows/Linux e falhas anteriores continuam preservados, sem serem reescritos como resultados atuais.

| Arquivo pendente desde HEAD | Escopo conferido / situação final |
| --- | --- |
| [quickstart.md](quickstart.md) | Evidências de T050–T054 e este fechamento T055, com natureza/data/comando/revisão e limites; modificado, sem remover registros anteriores. |
| [tasks.md](tasks.md) | Marcações T050–T054 preexistentes preservadas; somente T055 marcada nesta etapa. Descrições, dependências e rastreabilidade mantidas. |
| [OrderDemoApplication.java](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java) | Único arquivo novo, **208 linhas lidas integralmente**, criado em T051 e não alterado em T055; continua não rastreado. |

Não há diferença contra HEAD em produção, testes rastreados, seed/recursos públicos, contratos, POM, dependências ou Wrapper. O apoio novo está dentro da preparação de demonstração autorizada em T051; não implementa regra de pedido, nova API, perfil produtivo ou correção de contrato.

### Launcher: ativação, isolamento e sanitização

- Linhas 37–46: classe final com main explícito, sem anotação de componente/configuração principal. `new SpringApplication(DscommerceApplication.class, DemoFixtures.class)` inclui a configuração intencionalmente; argumentos internos fixam perfil test, H2 UUID exclusivo, loopback/porta aleatória e desligam logging/SQL só nessa execução. Reutiliza apenas a fábrica de URL de OrderTestConfig, sem importar seu Clock de 2030.
- Linhas 51–69/199–205: guardas de URL `order-test`, perfil e fixture única; configuração aninhada `@TestConfiguration`/`@Profile("test")`. As chamadas createAccount com CLIENT e createProduct com 10.00/7.50 ativam os dados explicitamente. O construtor de OrderTestFixture exige banco isolado; apenas classpath/perfil não popula fixtures. [OrderTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/OrderTestFixture.java) e [OrderTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestConfig.java) permanecem rastreados sem diff.
- Linhas 72–102/117–178: HttpClient usa servidor/filtros/login/JWT reais; POST /orders, confirmações completas, repetição com outro ID e persistência por JDBC independente. Antes/depois confere catálogo/detalhes/login e snapshots da seed original. Nenhum GET /orders ou Location que o prometa. Dados próprios adicionais não alteram a seed pública do repositório.
- Linhas 62–64/82–83/103–113: credenciais aleatórias e token ficam em memória; a saída contém somente campos seguros explicitamente selecionados e pedidos com campos conferidos. Não imprime corpo de login, JWT, hash, segredo, mensagem da exceção ou stack trace; falha emite somente a classe da exceção. Snapshots de contas ficam em memória para comparação, sem impressão. try-with-resources fecha somente contexto/cliente próprios; System.exit(1) refere-se ao processo da demonstração em falha.
- Busca de referências em src/pom/.mvn encontrou OrderDemoApplication/DemoFixtures somente no próprio arquivo. [DscommerceApplication.java](../../src/main/java/com/devsuperior/dscommerce/DscommerceApplication.java) inicia apenas a classe produtiva; starter-test tem scope test. A exclusão de TestConfiguration/TestComponent nos scans foi examinada em T052. **Ausência de ativação automática nos demais contextos é conclusão de inspeção; não houve nova execução do aplicativo normal ou da suíte após acrescentar o launcher.**

SHA-256 atual do launcher: `D94CC459D3041BFE4F8E6B4C9AE2CE88E02F59D3A70190F2EB9EBFD9AEB8C856`, idêntico a NewJavaSha256 da [auditoria T051](../../target/harness-validation/feature-004-t051-2026-10-11/final-review.json). Isso vincula o arquivo revisado à demonstração executada, sem transformar inspeção em nova execução.

### Correspondência das evidências com o estado revisado

| Evidência existente utilizada | Resultado e revisão | Correspondência / limite |
| --- | --- | --- |
| [T050: invocação e estado](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/result.json), [resumo](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/summary.json) | Em 2026-10-11, `.\mvnw.cmd test`, 12:57:10.782–13:00:48.019 UTC−03:00, HEAD acima, status de entrada limpo; **904 aprovações, 0 falhas/erros/ignorados, BUILD SUCCESS/exit 0**, Java 21.0.12.1 Azul/Zulu, Maven Wrapper 3.9.16. | Produção, build e fontes de teste rastreados continuam iguais a HEAD. **Launcher ainda não existia em T050**; não há suíte completa comprovada para sua inclusão posterior. Somente leitura dos resultados nesta tarefa; não reapresentada como execução T055. |
| [T051: invocação](../../target/harness-validation/feature-004-t051-2026-10-11/execution.json), [revisão sanitizada](../../target/harness-validation/feature-004-t051-2026-10-11/demo-review.json) | Em 2026-10-11, comando spring-boot:test-run explícito registrado na seção T051, 13:13:50.652–13:14:37.385 UTC−03:00, BUILD SUCCESS/0; compilação do launcher e demonstração real, sem JUnit. SanitizedOutputScanPassed/OwnedContextClosed/DemoPortClosed true, zero processos próprios remanescentes no registro. | Hash do launcher coincide; preparação explícita e dois registros completos estão evidenciados. **Não substitui suíte completa nem rollback**, não simula perda física de resposta; encerramento/sanitização são observações daquela execução, não novos checks de processo/porta nesta revisão. |
| [T052: revisão final](../../target/harness-validation/feature-004-t052-2026-10-11/final-review.json), [T053: ferramentas e resultados](../../target/harness-validation/feature-004-t053-2026-10-11/validation-result.json), consolidação T054 neste documento | Revisões documentais/inspeção, arquitetura e contrato/referências; T053 parsing YAML exit 0 e tentativa OpenAPI exit 1 por validador não instalado. T054 liga RED/GREEN, JDBC, rollback, sucesso/Q1, segurança e regressões aos requisitos/critérios/tarefas. | Não são testes HTTP novos nem aprovação OpenAPI integral. Históricos RED/preparação e reexecuções conservados, sem somar repetições como casos distintos. |
| [XML T050 de rollback](../../target/harness-validation/feature-004-t050-2026-10-11/full-01/reports/TEST-com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.xml), detalhes T052/T054 | Três falhas distintas: SQL tardio, processamento após saveAndFlush real e beforeCommit; controle positivo pós-commit. Requisição por proxy sem transação externa de teste; SQL real e JDBC independente após resposta, antes da limpeza. | beforeCommit ocorre **antes do commit físico**, não injeta falha em Connection.commit. Não prova resultado incerto de conexão, rollback após commit, transporte/serialização posteriores, outro banco/deploy ou todas as intercalações concorrentes. REQUIRED com chamador em transação externa participa dela; o retorno do método não comprova seu commit externo. |

### Whitespace e conclusão com ressalvas

Executados com a configuração Git existente, na raiz:

```powershell
git diff --check
git diff --no-index --check -- NUL src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java
```

Primeiro comando: **exit 0**, zero diagnósticos de whitespace. Segundo: **exit 1 pela diferença do arquivo novo contra NUL**, zero diagnósticos de whitespace; esse exit não significa falha de testes nem defeito do Java. Ambos emitiram somente avisos conhecidos de futura conversão LF/CRLF. Leitura separada das 208 linhas confirmou ausência de espaços/tabs finais, ausência de tabs e newline final. Não aplicada opção `-c`, alterada configuração Git ou reformatado conteúdo histórico.

**T055 executada e somente sua checkbox marcada nesta etapa; T001–T055 [X] (55), zero tarefas abertas. Isso registra execução das tarefas previstas, não resolução de todas as pendências nem aprovação integral da feature.** U1 continua pendente: conteúdo residual após o objeto JSON não está rejeitado/comprovado integralmente. U2 está resolvido somente para ativação explícita e reproduzível das fixtures de demonstração. Validação OpenAPI integral permanece impedida em T053 por falta de ferramenta compatível instalada. A demonstração não substitui as provas automatizadas de rollback; limites de commit físico/externo/pós-commit permanecem os descritos acima.

Nenhum novo defeito material identificado no diff/launcher dentro do escopo desta revisão, além das pendências/limites registrados. Alterações de T055 restritas a este acréscimo e à checkbox T055; launcher, contratos, produção, recursos/seed, dependências e evidências existentes preservados. Nenhum teste, demonstração, correção de U1, validação OpenAPI adicional, CI, stage, commit, push ou merge executado. Encerramento após este registro, sem ação de integração automática.

Conferência final de preservação: prefixo anterior deste quickstart (**685.208 bytes**), **156 demais arquivos inventariados** (155 rastreados e launcher não rastreado) e **6.074 arquivos de evidências** em target/harness-validation e target/surefire-reports idênticos por SHA-256 à entrada. Comparação de tasks.md confirmou somente `[ ] T055` → `[X] T055`. Quinze referências locais deste acréscimo com destinos existentes; diff/whitespace conferidos novamente após a documentação, com os resultados acima. Inventários mantidos em memória, sem criar ou sobrescrever relatórios/auxiliares.

Git final: raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `e18b4398638863e544404bdd37d77a57d567e428`; **índice vazio**, exatamente estes arquivos pendentes:

```text
 M specs/004-registrar-pedido/quickstart.md
 M specs/004-registrar-pedido/tasks.md
?? src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java
```

## Investigação U1 — prova integrada de conteúdo residual (2026-10-11)

**Exclusivamente investigação/prova de U1; parada antes de qualquer correção produtiva.** Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `cab7716b1e6397c5d7715d696072b1d65a26b030` e status inicialmente limpo/índice vazio pelos comandos Git usuais. Esse HEAD contém o checkpoint T050–T055; não significa aprovação integral ou integração em main. Lidos AGENTS.md, registro anterior de U1, [contrato](contracts/README.md)/[OpenAPI](contracts/orders-openapi.yaml), leitor e apoios existentes de integração. Nenhuma marcação histórica alterada: **T001–T055 continuam [X]**, tasks.md permanece igual ao HEAD.

### Causa observada e desenho da prova

[OrderRequestJsonConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/OrderRequestJsonConfig.java), linhas 29–42, avança dentro do objeto raiz até `END_OBJECT` e imediatamente devolve OrderRequestDTO. Não avança novamente para verificar EOF após esse objeto. O leitor do item também encerra em seu END_OBJECT, mas esse término interno não comprova término do corpo. Não encontrada guarda global FAIL_ON_TRAILING_TOKENS ou conferência equivalente nos fontes/configurações produtivos consultados. A execução abaixo confirma que o caminho real MVC/segurança aceita o primeiro objeto e ignora os cinco sufixos exercitados, inclusive texto que seria ilegível se consumido.

Criado somente [OrderTrailingContentIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderTrailingContentIntegrationTest.java), `@SpringBootTest`, MockMvc com filtros ativos e impressão desativada, perfil test, import explícito de OrderTestConfig e URL H2 UUID própria. Reutiliza OrderHttpTestSupport: contas CLIENT confirmadas no banco, POST /login real, JWT real decodificado/HS256, Clock alinhado ao token e limpeza somente das linhas próprias. Produtos explícitos 10.00/7.50, criados em ordem de IDs inversa à ordem enviada, quantidades 2/3 e total 42.50. Nenhum mock de autenticação/serviço/persistência, configuração de produção ou seed pública alterado.

Dezoito casos parametrizados: cinco sufixos inválidos × dois contextos, mais quatro controles de whitespace × dois contextos. Corpo entregue como bytes UTF-8 brutos, sem parse/re-serialização anterior. `/api/orders` usa contextPath `/api` e servletPath `/orders`, exercitando a URI externa e filtros reais no contexto MockMvc. Isso não equivale a deploy/TCP real.

Antes/depois de cada requisição, JDBC por DriverManagerDataSource separado abre conexão independente, lê todas as linhas de pedidos/itens e registra autocommit/vínculo transacional. Não há @Transactional de teste ou transação externa envolvendo HTTP; flags após todas as requisições: **autoCommit=true, bound=false, externalTx=false**. A requisição usa andReturn, sem andExpect antecipado; snapshot posterior e marcador sanitizado U1_CASE são coletados **antes das assertions e do @AfterEach**. Mesmo exceção no processamento da requisição é capturada para permitir essa observação. assertAll verifica conjuntamente HTTP/envelope e ausência de alteração do banco, sem ocultar a gravação por falha antecipada de status. Cleanup posterior não integra a prova de rejeição.

Nos casos negativos, assertions exigem 400, exatamente status/error/message/path, status inteiro 400, error Bad Request, message Invalid order data e path externo correto, Content-Type JSON e banco idêntico ao baseline. Controles positivos verificam 201, ausência de Location, seis campos de confirmação, comprador/WAITING_PAYMENT/UTC, ordem dos itens, strings monetárias e agregado completo persistido com cálculo exato por JDBC independente.

### Execução focada e resultados por caso

Wrapper `--version`: exit 0, **Java 21.0.12.1 Azul/Zulu e Maven 3.9.16**. Uma única invocação de testes, na raiz, serial e restrita à nova classe:

```powershell
.\mvnw.cmd "-Dtest=OrderTrailingContentIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

**2026-10-11, 14:48:18.973–14:49:15.712 UTC−03:00: BUILD FAILURE, exit 1; 18 testes, 8 aprovados, 10 falhas, 0 erros, 0 ignorados.** Compilação e startup concluíram, todos os 18 casos fizeram login real/preparação válida e produziram observação HTTP/JDBC. requestFailure=none em todos. Surefire registra falhas de assertions, não erros de preparação; nenhuma correção ou repetição foi necessária para fabricar RED.

| Sufixo após o pedido válido | Esperado | `/orders`: observado / JUnit | `/api/orders`: observado / JUnit | Banco após cada requisição, antes da limpeza |
| --- | --- | --- | --- | --- |
| Outro objeto, ` {}` | 400, zero gravações | **201 / RED** | **201 / RED** | **+1 pedido, +2 itens** |
| Array, ` []` | 400, zero gravações | **201 / RED** | **201 / RED** | **+1 pedido, +2 itens** |
| ` null` | 400, zero gravações | **201 / RED** | **201 / RED** | **+1 pedido, +2 itens** |
| Escalar numérico, ` 42` | 400, zero gravações | **201 / RED** | **201 / RED** | **+1 pedido, +2 itens** |
| Texto inválido, ` not-json` | 400, zero gravações | **201 / RED** | **201 / RED** | **+1 pedido, +2 itens** |
| Somente três espaços | 201 completo | **201 / GREEN** | **201 / GREEN** | **+1 pedido, +2 itens**, snapshot/valores completos aprovados |
| Somente duas tabulações, `\t\t` | 201 completo | **201 / GREEN** | **201 / GREEN** | **+1 pedido, +2 itens**, snapshot/valores completos aprovados |
| Somente quebras, `\r\n\n` | 201 completo | **201 / GREEN** | **201 / GREEN** | **+1 pedido, +2 itens**, snapshot/valores completos aprovados |
| Whitespace misto, ` \t\r\n ` | 201 completo | **201 / GREEN** | **201 / GREEN** | **+1 pedido, +2 itens**, snapshot/valores completos aprovados |

Cada baseline tinha zero pedidos/itens. Os dez negativos falharam tanto pela resposta 201/forma de confirmação em vez do envelope 400 quanto pelo estado persistido diferente do baseline. Por exemplo, primeiro caso: HTTP 201, pedido WAITING_PAYMENT e dois itens históricos 10.00/7.50 com posições 0/1, quantidade 2/3 visíveis pela conexão independente. A mensagem agregada registra sete assertions falhas desse caso, mas isso é **um teste falho**, não sete testes. O resultado é RED comportamental da ausência de rejeição do conteúdo residual, com gravação realmente concluída; não falha de token, login, contexto, fixtures, compilação ou cleanup. Os controles positivos aprovados corroboram a validade do primeiro objeto e do caminho de persistência.

### Evidências, preservação e limites

Antes do Maven, os **74 relatórios anteriores** foram copiados/conferidos por SHA-256 em [surefire-before](../../target/harness-validation/feature-004-u1-2026-10-11-144602/surefire-before); inventariados os 158 rastreados e 6.074 arquivos de evidências anteriores. [Baseline e hashes](../../target/harness-validation/feature-004-u1-2026-10-11-144602/baseline.json). Relatórios atuais da nova classe são um XML e um TXT; relatórios de outras classes remanescentes não entram nos totais desta execução focada.

Evidências ignoradas pelo Git: [versões](../../target/harness-validation/feature-004-u1-2026-10-11-144602/wrapper-version.txt), [comando/HEAD/horários/exit](../../target/harness-validation/feature-004-u1-2026-10-11-144602/result.json), [console](../../target/harness-validation/feature-004-u1-2026-10-11-144602/console.txt), [resumo/casos](../../target/harness-validation/feature-004-u1-2026-10-11-144602/summary.json), [observações HTTP/JDBC antes da limpeza](../../target/harness-validation/feature-004-u1-2026-10-11-144602/case-observations.txt), [XML arquivado](../../target/harness-validation/feature-004-u1-2026-10-11-144602/red-01/reports/TEST-com.devsuperior.dscommerce.controllers.OrderTrailingContentIntegrationTest.xml), [fonte executada](../../target/harness-validation/feature-004-u1-2026-10-11-144602/executed-test.java). Marcadores e assertions não imprimem credencial, JWT, hash de senha ou chave; snapshots de pedidos/itens contêm apenas os dados não secretos da compra. SHA-256 da fonte executada: `76C4E18BE208918B2AFB36522AA4FAB266A508BD5A5914816DB51F4C05E410C5`.

Rastreabilidade suplementar: rejeição de JSON inválido/entrada e ausência integral de escrita, US2.1/2/13, FR-002/004/012, SC-004/009; controles de confirmação/persistência apoiam US1.1/4/8, FR-001/003/006/007, SC-001/005/007. **U1 permanece pendente e agora reproduzido por testes**, sem correção produtiva. Não alteradas caixas T001–T055 nem contratos para acomodar a divergência. U2 permanece resolvido somente para ativação das fixtures de demonstração; OpenAPI integral continua não validado/impedido em T053.

Limites: cinco categorias com exemplos concretos, escalar numérico apenas (sem matriz de strings/booleanos), quatro formas de whitespace e dois caminhos no MockMvc completo/H2. Não comprova todos os tamanhos/encodings/intercalações de rede, outro banco/deploy ou regressões globais. Não executada suíte completa, demonstração, OpenAPI ou CI. A aprovação histórica de 904 testes em T050 continua anterior ao launcher T051 e a esta nova classe; o RED atual evidencia uma lacuna dessa cobertura, não invalida/reclassifica as execuções anteriores como se tivessem esses novos casos. As três provas históricas de rollback/commit continuam com seu alcance documentado e não substituem a rejeição necessária antes da escrita aqui.

Alterações versionáveis desta investigação: este acréscimo em quickstart.md e nova classe de teste; produção/contratos/tarefas/seed/dependências/launcher preservados. Sem stage, commit ou push; HEAD mantido em `cab7716b1e6397c5d7715d696072b1d65a26b030`.

Conferência final: os **157 demais rastreados**, incluindo tasks.md/produção/contratos/launcher, e os **6.074 arquivos de evidências anteriores** permanecem idênticos por SHA-256; preservado o prefixo integral de 696.887 bytes deste quickstart. Fonte nova idêntica à executada, referências com destinos existentes. `git diff --check` exit 0; Java não rastreado conferido separadamente com `git diff --no-index --check -- NUL ...`, exit 1 pela diferença, zero defeitos de whitespace, somente avisos LF/CRLF. Conteúdo/diff revisados, sem reformatar trechos antigos. [Conferência de preservação, referências, whitespace e Git](../../target/harness-validation/feature-004-u1-2026-10-11-144602/final-review.json).

Status final: quickstart.md modificado, OrderTrailingContentIntegrationTest.java não rastreado, índice vazio; raiz/branch/HEAD mantidos. Investigação encerrada no RED comprovado, **antes de corrigir produção**.

## Correção U1 — EOF do pedido e validação com launcher presente (2026-10-11)

**Exclusivamente U1, sem commit.** Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `cab7716b1e6397c5d7715d696072b1d65a26b030`; entrada com quickstart.md modificado e OrderTrailingContentIntegrationTest.java não rastreado, índice vazio. Essas alterações e as evidências anteriores foram preservadas. AGENTS.md e diagnóstico/contrato já consultados orientam o escopo; não alterados testes/assertions, marcações históricas T001–T055, contratos, ObjectMapper global, leitor de itens, login, segurança, serviço, persistência, dependências, seed ou launcher.

### RED preservado e correção mínima

[RED anterior, 2026-10-11](../../target/harness-validation/feature-004-u1-2026-10-11-144602/summary.json): 18 testes, 10 falhas comportamentais, 8 controles aprovados, zero erros/ignorados, BUILD FAILURE/exit 1. Todos os cinco sufixos inválidos em ambos os caminhos retornavam 201 e gravavam um pedido/dois itens, conferidos antes da limpeza. Não reexecutado ou reclassificado esse RED como falha de preparação.

Alteradas somente **três linhas** no [OrderRequestJsonConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/OrderRequestJsonConfig.java), no leitor específico de OrderRequestDTO, imediatamente após completar o objeto raiz:

```java
if (parser.nextToken() != null) {
    throw invalidInput(parser);
}
```

O parser consome whitespace final e deve chegar a EOF/null. Qualquer segundo token causa JsonMappingException por invalidInput; texto inválido causa exceção de parsing ao tentar avançar. Não há captura que ignore o restante. O mecanismo existente converte o erro de leitura em HttpMessageNotReadableException e o [handler existente](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java), 52–65, entrega 400/Invalid order data. Nenhum setting compartilhado ou leitura isolada de OrderItemRequestDTO foi alterado.

[OrderTrailingContentIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderTrailingContentIntegrationTest.java) permanece **idêntico ao executado no RED**, SHA-256 `76C4E18BE208918B2AFB36522AA4FAB266A508BD5A5914816DB51F4C05E410C5`. Mantidas todas as assertions de envelope/path/nenhuma escrita, controles completos e coleta HTTP/JDBC anterior à limpeza, sem transação externa. As demais fontes de teste também não foram modificadas.

### Comandos, estado executado e totais reais

Java **21.0.12.1 Azul/Zulu**, Maven **3.9.16 pelo Wrapper**, Windows/H2. `.\mvnw.cmd --version` exit 0; [versões](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/wrapper-version.txt). MAVEN_ARGS/MAVEN_OPTS/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS ausentes, sem variáveis alteradas. [Registro do runtime](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/runtime.json).

Primeiro, execução serial somente da nova classe:

```powershell
.\mvnw.cmd "-Dtest=OrderTrailingContentIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
```

Após conferir GREEN, executada a suíte completa exatamente na raiz, sem seleção, exclusões, skip ou parâmetro adicional:

```powershell
.\mvnw.cmd test
```

| Execução em 2026-10-11 / UTC−03:00 | Início → término | Testes / aprovados | Falhas / erros / ignorados | Build / exit |
| --- | --- | --- | --- | --- |
| Classe focada, com correção U1 | 15:02:14.629 → 15:03:09.764 | **18 / 18** | **0 / 0 / 0** | **BUILD SUCCESS / 0** |
| Suíte completa, com correção U1 e launcher T051 presente | 15:04:47.198 → 15:07:42.402 | **922 / 918** | **0 / 4 / 0** | **BUILD FAILURE / 1** |

Totais conferidos por testcase e atributos nos relatórios próprios de cada execução. Suíte completa: **38 XML + 38 TXT**, **33 classes-base**, nenhuma classe esperada ausente. Os **18 casos de U1 passaram também nessa suíte**. Não somados os 18 focados aos 922 completos, nem usado o total histórico 904 para presumir contagem/aprovação. O estado executado inclui o diff de três linhas, a nova classe preservada e o launcher já commitado; HEAD sozinho não representa esse working tree.

| Casos, cada um em `/orders` e `/api/orders` | Resultado GREEN focado e na suíte completa | Banco independente após resposta e antes da limpeza |
| --- | --- | --- |
| Outro objeto, array, null, escalar 42 e texto inválido | **400**, exatamente status/error/message/path, error Bad Request, message **Invalid order data**, path externo correto, JSON | Baseline **0 pedidos/0 itens** preservado, nenhuma gravação |
| Espaços, tabulações, quebras de linha e whitespace misto | **201**, confirmação completa, comprador/WAITING_PAYMENT/UTC, ordem enviada, dinheiro String 10.00/7.50 × 2/3, total 42.50; sem Location | **1 pedido/2 itens**, snapshot histórico completo, cálculo e posições conferidos |

Nos 18 marcadores de cada execução: autoCommit=true, bound=false, externalTx=false, requestFailure=none. Aprovação do teste confirma também as assertions de envelope completo e banco, não só o status. [Observações focadas](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/u1-observations.txt) e [observações na suíte](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/u1-observations.txt).

### Suíte completa não aprovada: diagnóstico dos quatro erros

**A validação do estado posterior ao launcher revelou um problema distinto de U1.** OrderTestSupportTest executou cinco casos: um aprovado, quatro erros JUnit, zero failures/skips. Nos quatro WebApplicationContextRunner, o lookup `getBean(OrderTestFixture.class)` lança **NoUniqueBeanDefinitionException**, encontrando `orderDemoFixture` e `orderTestFixture`.

Casos afetados: shouldCommitAllRoleFixturesAndCleanOnlyOwnedRows, shouldReuseRealEncodersAndDecoderWithTheTestProfileKey, shouldIsolateRoleFixturesAndTheirCleanupAcrossContexts e shouldCommitFixturesAndCleanOnlyOwnedRowsInIsolatedContexts. Seus contextos chegaram a iniciar; a busca ambígua da fixture impede continuar os cenários. São **erros de integração/preparação**, distintos do RED comportamental anterior de U1 e de falha de compilação.

Inspeção causal: [OrderTestSupportTest.java](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestSupportTest.java), 39–43, usa ConfigDataApplicationContextInitializer e configura DscommerceApplication/OrderTestConfig com perfil test. A configuração aninhada DemoFixtures de [OrderDemoApplication.java](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java), já presente no HEAD de entrada, é descoberta nesses runners e registra também o bean de fixture. @TestConfiguration/profile test não bastaram para excluí-la nesse caminho. **Registrar o bean automaticamente não significa popular os dados automaticamente**: as chamadas createAccount/createProduct continuam somente no main explícito da demonstração.

A stack e a ordem do código mostram erro na obtenção da fixture, **antes de qualquer leitura de OrderRequestDTO** nesses cenários. O diff U1 não muda definição/registro de beans, scans ou contexto; launcher e OrderTestSupportTest são idênticos à entrada. Assim, o diagnóstico aponta um problema preexistente no estado com launcher, descoberto pela primeira suíte completa posterior à sua inclusão, não uma regressão causada pela guarda EOF. Não realizada execução comparativa temporariamente sem a correção; a atribuição causal se apoia no ponto de falha executado e na inspeção dessas fontes.

As conclusões de inspeção T052/T055 sobre exclusão automática nos demais contextos ficam **limitadas por esta evidência nova**: elas não comprovavam convivência executada e agora não se sustentam para os WebApplicationContextRunner acima. Seus registros históricos não foram apagados ou reescritos. **Não corrigido o launcher, runners ou assertions**, porque esta solicitação autoriza exclusivamente U1 no leitor. Sem esse ajuste fora do escopo, não há motivo para repetir alvos/suíte e alegar aprovação. [Causas por caso](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/cause-review.json) e [XML dos quatro erros](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/reports/TEST-com.devsuperior.dscommerce.config.OrderTestSupportTest.xml).

### Preservação, evidências e situação de U1

Antes de qualquer execução, inventariados 158 rastreados, fonte U1 não rastreada, prefixo integral do quickstart e **6.085 arquivos de evidências já arquivadas**; [baseline](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/baseline.json). **Antes de cada invocação de testes, copiados/conferidos por SHA-256 os 76 relatórios Surefire existentes**, em diretórios separados: [antes do foco](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/surefire-before), [inventário](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/reports-before.json), [antes da suíte](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/surefire-before) e [inventário](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/reports-before.json). O RED original e T050–T055 continuam preservados; relatórios atuais correspondem à suíte completa falha, sem misturar arquivos antigos com seus totais.

Evidências ignoradas pelo Git, sem credencial/JWT/chave nas observações: [foco: comando/estado/exit](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/result.json), [resumo](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/summary.json), [XML](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/focused-01/reports/TEST-com.devsuperior.dscommerce.controllers.OrderTrailingContentIntegrationTest.xml); [suíte: comando/estado/exit](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/result.json), [resumo por classe](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/summary.json), [console completo](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/console.txt). Cada diretório guarda cópias do leitor/teste executados e seus relatórios. Arquivos de evidência/coletores não entram no diff versionável.

**U1 resolvido quanto à rejeição de conteúdo residual solicitada, comprovada pelos 18 casos GREEN tanto no foco quanto na suíte; a suíte completa NÃO está aprovada por causa dos quatro erros do launcher.** Não declarada aprovação integral da feature ou integração em main. Mantidas as 55 marcações históricas, sem transformar checkbox em aprovação do estado novo. U2 continua resolvido somente quanto à ativação explícita das fixtures de demonstração; seu isolamento em runners tem agora a divergência acima. **Validação OpenAPI integral permanece pendente/impedida**, sem ferramenta instalada ou nova validação.

Limites da prova U1: exemplos definidos de cinco categorias/escalares numéricos, quatro formas de whitespace, contexto completo MockMvc/filtros/JWT/H2 e JDBC independente; não matriz de todos os escalares/encodings/tamanhos, TCP/deploy ou outro banco. A guarda exige EOF para o corpo do DTO de pedido; leitura de itens isolados e contratos anteriores permanecem preservados. As provas históricas de rollback/commit conservam seu alcance, sem simular falha física durante commit ou garantir rollback pós-commit. A suíte atual executou com launcher presente/compilado, mas **não executou seu main nem repetiu a demonstração**.

Alterações desta correção: somente três linhas produtivas no leitor e este acréscimo documental. Nova classe da investigação mantida integralmente, ainda não rastreada; nenhuma assertion alterada. Sem demonstração, OpenAPI, CI, stage, commit, fetch ou push. HEAD mantido em `cab7716b1e6397c5d7715d696072b1d65a26b030`.

Revisão final: [auditoria de preservação e whitespace](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/final-review.json) confirmou **156 outros arquivos rastreados e 6.085 evidências anteriores idênticos por SHA-256**, prefixo inicial de **707.724 bytes** do quickstart preservado, fonte U1 inalterada e leitor/teste atuais iguais às cópias executadas nas duas invocações. Os **76 relatórios anteriores a cada execução** foram preservados; os 76 atuais correspondem às cópias da suíte completa. Conferidos destinos das novas referências, diff de produção de três linhas, conteúdo integral da classe não rastreada e as 55 marcações históricas. `git diff --check` exit 0; conferência separada do arquivo não rastreado sem erro de whitespace (exit 1 de diff contra NUL por existir conteúdo). Saídas contêm somente avisos Git LF→CRLF já existentes; configurações Git não alteradas. Índice vazio; status final restrito a quickstart.md e OrderRequestJsonConfig.java modificados e OrderTrailingContentIntegrationTest.java não rastreado. A suíte completa continua **BUILD FAILURE/exit 1 pelos quatro erros documentados**, sem aprovação integral.

## Isolamento das fixtures — correção e validação após U1 (2026-10-11)

**Escopo exclusivo: descoberta da configuração de demonstração.** Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `004-registrar-pedido`, HEAD `cab7716b1e6397c5d7715d696072b1d65a26b030` e índice vazio. Entrada: quickstart.md/OrderRequestJsonConfig.java modificados e OrderTrailingContentIntegrationTest.java não rastreado. Preservados integralmente a guarda EOF de U1, a classe/assertions da investigação e todas as evidências anteriores. Não alteradas marcações T001–T055.

### Falha anterior e origem dos dois beans

A [suíte anterior com U1](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/summary.json), 2026-10-11, 15:04:47.198–15:07:42.402 UTC−03:00, executou **922 testes: 918 aprovados, zero falhas, quatro erros, zero ignorados; BUILD FAILURE/exit 1**. As 918 aprovações **não significavam aprovação da suíte**. Esse histórico não foi apagado ou reescrito.

Lidos os quatro erros completos no [TXT anterior](../../target/harness-validation/feature-004-u1-fix-2026-10-11-150109/full-01/reports/com.devsuperior.dscommerce.config.OrderTestSupportTest.txt) e XML correspondente. Todos eram NoUniqueBeanDefinitionException na obtenção de OrderTestFixture, encontrando orderDemoFixture/orderTestFixture, antes da leitura de pedido:

- shouldCommitAllRoleFixturesAndCleanOnlyOwnedRows;
- shouldReuseRealEncodersAndDecoderWithTheTestProfileKey;
- shouldIsolateRoleFixturesAndTheirCleanupAcrossContexts;
- shouldCommitFixturesAndCleanOnlyOwnedRowsInIsolatedContexts.

[OrderTestSupportTest.java](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestSupportTest.java), 43–46, prepara WebApplicationContextRunner com perfil test e registra explicitamente DscommerceApplication/OrderTestConfig. **orderTestFixture** vem do método @Bean em [OrderTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestConfig.java), 48–51, configuração necessária às fixtures/Clock dos testes, mantida intacta. **orderDemoFixture** vem do método @Bean da classe estática aninhada DemoFixtures em [OrderDemoApplication.java](../../src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java). Antes, essa classe tinha @TestConfiguration/@Profile(test); o scan da aplicação a descobriu nesses runners. A exclusão de configuração de teste não estava assegurada nessa preparação, como comprovaram os quatro lookups ambíguos. Construtor do bean não populava contas/produtos automaticamente, mas seu registro já quebrava a unicidade. [Diagnóstico com as quatro stacks completas](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/diagnosis.json).

### Correção mínima, somente em código de teste

Removidos de DemoFixtures **@TestConfiguration e seu import**, com comentário explicando o registro explícito. Mantidos **@Profile("test"), método @Bean/orderDemoFixture e new SpringApplication(DscommerceApplication.class, DemoFixtures.class)** no main. A classe passa a configuração lite: sem estereótipo @Component/@Configuration/@TestConfiguration, não é candidata ao component scan padrão; como fonte explicitamente registrada pelo launcher, seus métodos @Bean continuam processados. Não depende de flag implícita ou de um perfil adicional.

A correção fica inteiramente no launcher de src/test. Não alterados OrderTestConfig, contexto/assertions de OrderTestSupportTest, demais testes, produção, ObjectMapper, contratos, seed pública, POM ou U1. Sem @Primary para esconder ambiguidade, remoção da fixture necessária, skip/exclusão de testes ou substituição de encoders/decoder reais. O getBean por tipo nos cenários originais volta a funcionar; a demonstração também exige exatamente um bean de fixture. Nenhum ajuste extra de preparação foi necessário.

### Execuções atuais, em sequência e com relatórios preservados

Java **21.0.12.1 Azul/Zulu**, Maven **3.9.16 pelo Wrapper**, Windows: [versões](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/wrapper-version.txt), comando `.\mvnw.cmd --version`, exit 0. MAVEN_ARGS/MAVEN_OPTS/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS ausentes; não alteradas variáveis ou configurações Git.

Comandos na raiz, executados serialmente, sem alteração das assertions:

```powershell
.\mvnw.cmd "-Dtest=OrderTestSupportTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
.\mvnw.cmd "-Dtest=OrderTrailingContentIntegrationTest" "-Djunit.jupiter.execution.parallel.enabled=false" "-DforkCount=1" "-DreuseForks=true" test
# Somente depois de conferir ambos GREEN:
.\mvnw.cmd test
```

| Execução em 2026-10-11 / UTC−03:00 | Início → término | Testes / aprovados | Falhas / erros / ignorados | Build / exit |
| --- | --- | --- | --- | --- |
| OrderTestSupportTest | 15:24:10.784 → 15:25:14.413 | **5 / 5** | **0 / 0 / 0** | **BUILD SUCCESS / 0** |
| OrderTrailingContentIntegrationTest | 15:25:22.209 → 15:25:57.444 | **18 / 18** | **0 / 0 / 0** | **BUILD SUCCESS / 0** |
| Suíte completa com launcher corrigido e U1 preservado | 15:26:25.858 → 15:29:23.039 | **922 / 922** | **0 / 0 / 0** | **BUILD SUCCESS / 0** |

Totais conferidos nos relatórios próprios, por atributos e testcase: suíte com **38 XML/38 TXT, 33 classes-base e nenhuma classe esperada ausente**. OrderTestSupportTest 5/5 e U1 18/18 também nessa suíte. Sem presumir 904 ou somar execuções focadas/repetidas como testes distintos. O estado aprovado é o working tree com U1, seu teste preservado e a correção do launcher sobre o HEAD acima; não somente esse commit.

Em U1, tanto no foco quanto na suíte: cinco categorias inválidas em /orders e /api/orders retornaram **400**, envelope exato status/error/message/path, mensagem **Invalid order data**, path externo correto e **zero pedidos/itens gravados**, por JDBC independente antes da limpeza. Oito controles de whitespace final retornaram **201** e persistiram um pedido/dois itens integralmente. Sem transação externa do teste. [Marcadores focados](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/u1-01/u1-observations.txt) e [na suíte](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/full-01/u1-observations.txt).

Evidências atuais: [suporte: comando/exit](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/support-01/result.json), [resumo](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/support-01/summary.json), [XML](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/support-01/reports/TEST-com.devsuperior.dscommerce.config.OrderTestSupportTest.xml); [U1: comando/exit](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/u1-01/result.json), [resumo](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/u1-01/summary.json); [suíte: comando/exit](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/full-01/result.json), [resumo por classe](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/full-01/summary.json), [console](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/full-01/console.txt). Relatórios de cada execução arquivados separadamente, sem misturar antigos com os totais.

### Demonstração repetida e encerramento próprio

Após a suíte GREEN, executado o mesmo mecanismo explícito documentado em T051:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.devsuperior.dscommerce.demo.OrderDemoApplication' '-Dspring-boot.run.profiles=test'
```

2026-10-11, **15:29:48.371–15:30:12.671 UTC−03:00**, **BUILD SUCCESS/exit Maven 0**. Servidor/filtros reais, test/H2 UUID isolado, loopback/porta aleatória **59504**, exatamente uma fixture explicitamente registrada pelo launcher. Credenciais aleatórias/JWT permaneceram em memória, sem segredo em argumentos/saídas/evidências. [Resultado sanitizado e comando](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/demo-01/result.json), [console](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/demo-01/console.txt), [processos próprios observados, somente IDs/nomes](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/demo-01/owned-processes.json).

Catálogo/detalhes antes e depois e login real antes/depois aprovados. Fixture CLIENT autorizada, preços conferidos por HTTP 10.00/7.50, quantidades 2/3, strings monetárias e total **42.50**. Dois POST /orders retornaram **201**, ids **1 e 2**, comprador accountId 2, WAITING_PAYMENT, instantes **2026-10-11T18:30:12.130Z / 18:30:12.255Z**, ordem e confirmação completas, sem Location. JDBC independente confirmou **dois pedidos/quatro itens**, primeiro pedido preservado após repetição; seed original de **52 produtos**, categorias/vínculos/contas públicos preservados. Não criado/usado GET /orders.

O launcher fechou seu próprio contexto e terminou naturalmente: ORDER_DEMO_CLOSED ownedContext=true. **Limite do coletor:** após Maven exit 0, o auxiliar de captura saiu com **exit 1** ao exigir também término imediato de conhost.exe PID 6040, pertencente ao seu próprio PowerShell ainda em execução (PID 7244). Isso ocorreu depois da demonstração aprovada, não foi erro do aplicativo nem novo RED. A primeira observação foi mantida, sem sobrescrita. [Conferência posterior de encerramento](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/demo-01/shutdown-review.json), às **15:31:08.558**, confirmou host, ambas as JVMs e os cinco descendentes observados encerrados, **zero processos próprios restantes e porta fechada**. Nenhum Stop-Process ou encerramento de processo alheio; nenhuma repetição desnecessária da demonstração.

### Preservação, situação e limites

[Baseline anterior à alteração](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/baseline.json) inventariou 158 rastreados, U1 não rastreado, prefixo integral do quickstart e **6.336 arquivos de evidências anteriores**. Antes de **cada uma das quatro invocações Maven** (três testes e demonstração), **76 relatórios Surefire existentes** foram copiados e verificados por SHA-256 em `surefire-before` próprio, com manifest reports-before.json em cada diretório. A demonstração não executou JUnit nem substituiu os relatórios da suíte. Mantidos histórico RED/GREEN de U1, suíte anterior falha de 922, T050–T055 e registros Windows/Linux/2026-10-01/02.

**Isolamento corrigido nos contextos exercitados**, e ativação explícita da demonstração novamente comprovada. **U1 permanece resolvido no alcance documentado; U2 permanece resolvido somente para ativação explícita das fixtures**, agora com evidência de convivência dos testes solicitados e da suíte atual. Não ampliada a conclusão para todo contexto hipotético, provisionamento em produção ou integração em main. **OpenAPI integral continua pendente/impedido em T053**, sem instalação ou nova validação. T001–T055 permanecem [X] históricos, sem novas marcações.

Demonstração de sucesso/repetição não substitui provas automatizadas de rollback. Os testes existentes de rollback passaram na suíte, com o mesmo alcance anterior: falha SQL tardia, pós-flush e beforeCommit, controle positivo e JDBC independente; beforeCommit não simula falha física durante commit e não prova rollback pós-commit. Nenhuma aprovação integral sem essas ressalvas.

Alterações desta solicitação: somente launcher e este acréscimo documental. Correção produtiva U1 e teste não rastreado anteriores preservados. Sem stage, commit, push, CI, OpenAPI, mudanças de dependências/contratos/seed ou de configuração Git.

Revisão final: [auditoria de preservação, referências, diff e whitespace](../../target/harness-validation/feature-004-fixture-isolation-2026-10-11-152304/final-review.json) confirmou 156 outros rastreados e 6.336 evidências anteriores idênticos, prefixo de 721.129 bytes do quickstart preservado, U1/leitor/teste intactos e fontes atuais iguais às executadas. Conferidos os relatórios preservados antes das quatro invocações, o conteúdo integral do arquivo não rastreado e o diff acumulado (U1 de três linhas, correção mínima do launcher e acréscimos documentais). Removida somente uma linha vazia nova no EOF desta seção, detectada no primeiro check; nenhum trecho histórico reformatado. `git diff --check` final exit 0; arquivo não rastreado sem erro de whitespace (diff contra NUL exit 1 por conteúdo novo). Somente avisos LF→CRLF, sem mudança de configuração Git. Índice vazio; status final: quickstart.md, OrderRequestJsonConfig.java e OrderDemoApplication.java modificados, OrderTrailingContentIntegrationTest.java não rastreado. HEAD/branch mantidos; nenhuma task ou assertion alterada.
