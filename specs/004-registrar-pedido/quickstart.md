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
