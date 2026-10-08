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
