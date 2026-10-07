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
