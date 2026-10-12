---
description: "Tarefas executáveis para Manter produtos, com TDD e evidência independente"
---

# Tasks: Manter produtos

**Input**: [spec.md](spec.md), [checklist](checklists/requirements.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato OpenAPI](contracts/product-maintenance-openapi.yaml), [regras do contrato](contracts/README.md) e [quickstart.md](quickstart.md).

**Base de geração**: `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `238d7ba6d1ae37087ba0caccb68ee1253843bf28`, Git limpo na preparação; 2026-10-12. Constituição e [AGENTS.md](../../AGENTS.md) governam a execução; [harness](../../docs/harness.md) distingue evidência atual de histórica. As cinco histórias têm prioridade **P1**; a ordem US1 → US2 → US3 → US4 → US5 respeita compartilhamento de componentes, sem reduzir o escopo final.

**Estado**: somente este arquivo é criado nesta geração. Todas as tarefas são futuras e permanecem abertas; não há implementação, testes/validadores executados ou autorização implícita para stage, commit, publicação ou ações remotas. Os relatos históricos em outros artefatos descrevem seus checkpoints, não impedem a geração agora solicitada. Testes antigos/926 históricos, checklist 16/16 e validação OpenAPI histórica não concluem nenhuma tarefa.

**Testes**: TDD solicitado. Escrever testes de comportamento novo antes de implementar, executar RED por assertion da regra ausente e então implementar/GREEN. Preferir HTTP bruto com tipos existentes para RED antes das novas classes; superfície mínima compilável de componente novo pode preceder teste de serviço. Falha de compilação/preparação não é RED funcional. Se um caso já está GREEN, registrar/preservar; não quebrar código/assertions para fabricar falha. Tarefas de correção condicionais exigem falha real e, se dispensadas, registro da revisão sem alteração. Suporte/fixtures e verificações de constraints já existentes não precisam de falha artificial.

## Formato e caminhos

Cada tarefa usa `- [ ] Tnnn [P?] [USn?] objetivo — Dep.: ...`. IDs são únicos e sequenciais; label de história aparece somente em suas fases. Dependências explícitas prevalecem sobre a ordem visual. `[P]` somente cabe quando a tarefa inteira, incluindo sua verificação e registro, independe de arquivos, build, banco, relatórios e evidências compartilhados. Nesta revisão nenhuma tarefa mantém `[P]`: executar as tarefas em sequência, concluindo as verificações e registros previstos nela antes da dependente; provas agrupadas posteriores permanecem nos respectivos checkpoints.

Projeto Maven único: caminhos completos sob `src/main/java/com/devsuperior/dscommerce/` e `src/test/java/com/devsuperior/dscommerce/`. Caminhos de classes novas são alvos futuros, não arquivos existentes; diretórios novos (como `validation/`) serão criados apenas durante implementação autorizada. Nomes concretizam as responsabilidades do plano; não renomear os componentes existentes de pedidos por terem nome histórico.

Para cada execução futura, preservar relatórios anteriores, registrar comando/data/branch/HEAD/diff/versões/exit/cause/fontes e relatórios próprios no quickstart. Há uma única invocação Maven por vez neste workspace: aguardar seu término, examinar e preservar os relatórios e concluir o registro antes da próxima, inclusive quando classes/contextos H2 forem diferentes. Não executar outra tarefa que edite fontes, helpers ou quickstart durante o build/verificação atual. `target/`, `target/surefire-reports/` e o documento de evidências são compartilhados; bancos separados não tornam as invocações independentes. Integração HTTP/proxy sem `@Transactional` externo; fixtures commitadas antes do request; comparar snapshots de outra conexão após retorno e antes de limpeza. Não somar relatórios de invocações diferentes como testes distintos.

Comandos de execução futura na raiz, Windows/PowerShell (não executados nesta geração):

```powershell
.\mvnw.cmd --version
.\mvnw.cmd '-Dtest=AdminProductSecurityChainTest,AdminAccessServiceTest' test
.\mvnw.cmd test
```

Para os demais grupos focalizados, substituir a lista de `-Dtest` pelos nomes das classes `.java` explicitamente indicadas na tarefa, depois de criá-las; nunca usar execução sem testes encontrados como GREEN. Preservar relatórios antes de cada chamada. Se uma prova posterior revelar RED, reabrir/corrigir a tarefa de implementação responsável e repetir somente as verificações afetadas antes do checkpoint; não enfraquecer assertions nem prosseguir com dependente marcado como concluído. Linux/macOS futuro usa `./mvnw`, conforme requisitos do harness; nenhuma execução nesses sistemas é presumida.

**Gate IV**: [constituição](../../.specify/memory/constitution.md) exige validação antes de lógica de serviço; o plano mantém **EXCEÇÃO DOCUMENTADA**, justificada por FR-012/US2.6, nunca PASS estrito. Diagnóstico pode normalizar/avaliar regras e ler referências com guarda atual, mas não mutar DTO/entidades, emitir DML ou entrar na manutenção. Validator adapta; decisões ficam nos serviços; manutenção revalida na transação. T027/T039/T040/T051/T067/T079 e o fechamento tornam observáveis os limites e o acompanhamento exigido por Governance.

## Fase 1: Preparação

**Objetivo**: Preparar execução reproduzível e suporte isolado sem alterar seeds compartilhadas.

**Teste independente / checkpoint**: Contextos administrativos novos sem carga automática de seeds; fixtures commitadas no H2 próprio, manifesto V03, JWT do login e leitura independente utilizáveis. T004 comprova isolamento, preparação e limpeza antes dos testes consumidores; regressões existentes mantêm sua configuração e são registradas pelo resultado atual.

**Pré-requisitos**: Sem dependências; confirmar contexto novamente antes da execução futura.

- [X] T001 Registrar raiz/branch/HEAD/status, limites da autorização, Java 21 e Maven 3.9.16 selecionado por `mvnw.cmd`, preservando evidências anteriores de `target/surefire-reports/`; conferir `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, `AGENTS.md` e `docs/harness.md`, e registrar comando/versões/impedimentos em `specs/005-manter-produtos/quickstart.md`. — Dep.: nenhuma.
- [X] T002 Criar `src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java` com H2 de nome único por contexto, segredo descartável e filtros/decoder reais; nos contextos administrativos novos, desabilitar explicitamente tanto a inicialização SQL automática do Spring quanto a carga de import.sql pelo Hibernate, antes da criação das fixtures. Não basta mudar a URL H2: o baseline possui 52 produtos e três categorias. Preservar `src/test/resources/application-test.properties` e `src/test/resources/import.sql`, bem como a configuração/seeds dos testes anteriores; não acionar `src/test/java/com/devsuperior/dscommerce/demo/OrderDemoApplication.java`. — Dep.: T001.
- [X] T003 Criar `src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java` com ownership/limpeza apenas de suas linhas, preparação commitada de ADMIN, CLIENT, ambos e nenhum perfil, categorias de nomes iguais/sem produto, produtos legados e compradores; oferecer preparações separadas por cenário, sem criar todos os produtos/categorias por padrão. Para V03, fornecer preparação dos seis produtos e manifesto imutável de IDs/nomes/associações/ordens esperadas conforme seção de infraestrutura abaixo; fornecer também cenário sem categorias. Obter JWT válido via POST /login real, sem logar credenciais/token e sem usar token mockado como prova de autenticação. — Dep.: T002.
- [X] T004 Criar `src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductDatabaseProbe.java` para conexão/DataSource independente, snapshots por IDs/valores das cinco tabelas e observação de SQL apenas em testes; verificar suporte em `src/test/java/com/devsuperior/dscommerce/config/AdminProductTestSupportTest.java`. Provar ausência de produtos/categorias importados antes da fixture, isolamento entre contextos, preparação V03 com exatamente seis produtos/cinco correspondentes e par de categorias do produto escolhido, manifesto esperado, commit positivo visível e limpeza por ownership após assertions. Provar preparação separada com zero categorias, ausência de transação externa em HTTP/proxy e auxiliares de concatenação/comparação de páginas sem consultar a busca sob teste para construir o esperado. Esses auxiliares devem estar prontos antes de T014/T020/T022/T026. — Dep.: T003.
- [X] T005 Executar futuramente regressões existentes focalizadas com `mvnw.cmd`: ProductControllerTest, PublicCatalogSecurityIntegrationTest, PublicProductDetailSecurityIntegrationTest, LoginControllerTest, OrderSecurityIntegrationTest e OrderPublicRegressionIntegrationTest em `src/test/java/com/devsuperior/dscommerce/controllers/`; registrar baseline atual em `specs/005-manter-produtos/quickstart.md`, preservando assertions GREEN e tratando falha de ambiente separadamente de RED funcional. — Dep.: T004.

### Infraestrutura e fixture futuras de V03/V04

T002 impede a carga dos seeds somente nos novos contextos; T003 prepara dados via suporte JDBC/transação de fixture, sem depender de POST administrativo. Cada caso começa sem linhas de produtos/categorias de outro cenário. T004 verifica essas pré-condições por conexão independente antes de consumir os endpoints; nenhuma preparação é executada nesta revisão documental.

- **Dataset V03**: inserir, nessa ordem, `Telefone D`, `Fone C`, dois produtos distintos chamados `Fone B`, `Fone A` e `Cabo`; para cada um, descrição `Descricao da fixture V03`, imagem `https://example.invalid/v03.jpg` e preço decimal exato `10.00`. Guardar os IDs efetivamente gerados; não presumir valores fixos. O filtro literal `fone` corresponde a cinco produtos; `Cabo` não corresponde. Criar duas categorias distintas com nome `Categoria V03` e associar ambas somente a `Fone A`; os outros produtos ficam sem associações nessa fixture de leitura. Nenhum produto legado ou de outro caso integra esse dataset.
- **Manifesto da fixture**: retornar em estrutura imutável os seis IDs/nomes, os dois IDs de categoria e os pares esperados. A ordem filtrada esperada é `Fone A`, os dois `Fone B` por ID crescente, `Fone C`, `Telefone D`; sem filtro, `Cabo` precede essa sequência. Construir esse esperado a partir dos dados preparados, sem reutilizar consulta/mapping da aplicação sob teste.
- **Auxiliares compartilhados**: T003 mantém o manifesto e ownership; T004 fornece leitura independente dos pares/valores e comparação das páginas com o manifesto. Com `size=2`, reapresentar o filtro nas páginas 0/1/2 e esperar 2/2/1, `totalElements=5` e `totalPages=3` em todas. Comparar IDs concatenados, ordem e frequência exatamente uma para cada produto, inclusive `Fone A`; página 3 vazia conserva totais. Sem filtro, navegar pelas três páginas 2/2/2, totalElements=6, totalPages=3 e os seis IDs uma vez.
- **Outros casos**: preparar Café/Cafe, espaços internos, caracteres especiais e legado separadamente, com seu próprio manifesto e limpeza posterior, sem contaminar os totais de V03. Para US1.8/V04 e os testes de escrita sem categorias, oferecer preparação exclusiva com zero categorias, sem apagar seeds ou linhas alheias. Fixture/snapshot não substituem a prova HTTP/repository de T014/T020/T022 nem a rejeição de escrita de T026/T044.
- **Ordem obrigatória**: T002 → T003 → T004 → fundação T006–T013 → T014; T020 espera também T019, e T022 espera T021. Erro de seed, manifesto ou preparação é impedimento de infraestrutura, não RED de busca/paginação.

## Fase 2: Fundação obrigatória

**Objetivo**: Isolar segurança/erros administrativos e guardas reutilizáveis antes de expor consultas ou escritas.

**Teste independente / checkpoint**: 401/403 do namespace inteiro com filtros reais, guardas atuais sem acesso indevido e regressões anteriores intactas.

**Pré-requisitos**: T001–T005 concluídas; nenhuma história começa antes de T013.

- [ ] T006 Escrever e executar RED em `src/test/java/com/devsuperior/dscommerce/config/AdminProductSecurityChainTest.java`: requisições sem token/CLIENT para /admin/products, /admin/products/1 e /admin/categories, inclusive verbos/rotas não suportados, devem dar 401/403 antes de corpo; contraste com GET público anônimo/token inválido irrelevante e POST /orders existente. Usar contexto completo, não slice sem filtros. — Dep.: T005.
- [ ] T007 Criar `src/main/java/com/devsuperior/dscommerce/dto/AdminErrorResponseDTO.java` e `src/main/java/com/devsuperior/dscommerce/dto/AdminFieldErrorDTO.java` com exatamente status/error/message/path/errors e field/code/message; errors vazio quando não há campo. Conservar `src/main/java/com/devsuperior/dscommerce/dto/ErrorResponseDTO.java` e envelopes antigos. — Dep.: T006.
- [ ] T008 Criar `src/main/java/com/devsuperior/dscommerce/security/AdminAuthenticationEntryPoint.java` e `src/main/java/com/devsuperior/dscommerce/security/AdminAccessDeniedHandler.java`: 401 com Bearer challenge, 403 sem ADMIN, resolução interna 500 seguro e path externo sem query; mensagens próprias do contrato, sem corpo/token/SQL/stack trace. — Dep.: T007.
- [ ] T009 Inserir cadeia /admin/** em prioridade 2 em `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java`, com hasRole("ADMIN") para qualquer método; manter POST /orders em 1 e mover apenas fallback público para 3. Reutilizar JwtDecoder e `src/main/java/com/devsuperior/dscommerce/security/OrderJwtAuthenticationConverter.java`, traduzindo falha interna no adaptador administrativo, sem resource server global nem claims concedendo perfil. — Dep.: T008.
- [ ] T010 Definir superfície mínima compilável de `src/main/java/com/devsuperior/dscommerce/services/AdminAccessService.java` para exigir conta/ADMIN atual a partir do principal existente, sem inventar nova identidade/claims; a assinatura permite testar a regra antes de implementá-la. Não contar erro de compilação como RED. — Dep.: T009.
- [ ] T011 Escrever/executar RED da guarda nova em `src/test/java/com/devsuperior/dscommerce/services/AdminAccessServiceTest.java`: ADMIN isolado aceito, CLIENT/sem roles recusados, conta ausente/ADMIN revogado rejeitados e falha de lookup distinguida; regra ausente no componente novo deve causar assertion comportamental, não defeito fabricado em `src/main/java/com/devsuperior/dscommerce/services/AuthenticatedAccountService.java`. — Dep.: T010.
- [ ] T012 Implementar guarda atual em `src/main/java/com/devsuperior/dscommerce/services/AdminAccessService.java` antes de qualquer lookup administrativo, usando AuthenticatedAccountService; adaptar somente /admin/** em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java` para 401/403/500 e erros básicos do DTO próprio, mantendo caminhos e envelopes das Features 001–004. — Dep.: T011.
- [ ] T013 Executar GREEN de T006/T011 e regressões afetadas em `src/test/java/com/devsuperior/dscommerce/config/AdminProductSecurityChainTest.java`, `src/test/java/com/devsuperior/dscommerce/services/AdminAccessServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java`; registrar comando/exit/fontes em `specs/005-manter-produtos/quickstart.md`, sem chamar essa fundação de entrega das seis operações. — Dep.: T012.

## Fase 3: US1 — Localizar produtos e selecionar categorias (P1)

**Objetivo**: Entregar GET administrativos de lista, detalhe e opções, com busca literal e dados legados legíveis.

**Teste independente / checkpoint**: Com fixtures existentes e JWT ADMIN, consultar sem escrever; mesma seleção paginada no repository H2 e HTTP, IDs de categorias completos.

**Pré-requisitos**: Fundação T013; fixtures não dependem da inclusão via API. US1.8 só terá a rejeição de escrita comprovada em US2/US3.

- [ ] T014 [US1] Escrever/executar RED HTTP em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java` para os três GETs: US1.1–US1.8 (parte de leitura), lista/detalhe/escolha sem escrita, filtro/ordem/paginação, legado e 404; incluir page/size/id inválidos, query desconhecida/repetida, categorias sem query. Usar o manifesto/auxiliares V03 e o cenário sem categorias já verificados em T004; incluir desde este RED as assertions de IDs únicos, totais e navegação V03, antes das consultas de T017. URLs públicas permanecem controle GREEN. — Dep.: T004, T013.
- [ ] T015 [US1] Criar `src/main/java/com/devsuperior/dscommerce/dto/AdminProductItemDTO.java`, `src/main/java/com/devsuperior/dscommerce/dto/AdminProductDetailDTO.java` e `src/main/java/com/devsuperior/dscommerce/dto/CategoryOptionDTO.java`: preço string com duas casas e categorias por ID/nome, permitindo legado; reutilizar `src/main/java/com/devsuperior/dscommerce/dto/PageResponseDTO.java` sem alterar DTOs públicos ou colocar constraints de escrita nas leituras. — Dep.: T014.
- [ ] T016 [US1] Criar `src/main/java/com/devsuperior/dscommerce/repositories/CategoryRepository.java` com leitura de todas as opções ordenadas name/id, incluindo nomes iguais e sem produtos, e consulta em lote de IDs distintos; não acrescentar regras de negócio, manutenção ou busca própria de categoria. — Dep.: T015.
- [ ] T017 [US1] Adicionar consultas administrativas a `src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java`: filtro vinculado locate(lower(:name), lower(p.name)), count com mesmo predicado, ordem name/id e leitura de detalhe; paginação sem fetch de coleção. Manter consultas públicas intactas. — Dep.: T015, T016.
- [ ] T018 [US1] Criar `src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java` e `src/main/java/com/devsuperior/dscommerce/services/AdminCategoryQueryService.java`: guarda atual antes do lookup, remover só extremos Character.isWhitespace/isSpaceChar do filtro, vazio/ausente sem restrição, mapping fora de repository; detalhe distingue IDs e não valida retroativamente legado. — Dep.: T017.
- [ ] T019 [US1] Criar GET /admin/products e GET /admin/products/{id} em `src/main/java/com/devsuperior/dscommerce/controllers/AdminProductController.java` e GET /admin/categories em `src/main/java/com/devsuperior/dscommerce/controllers/AdminCategoryController.java`, delegando regras aos serviços e respeitando formatos/sucessos/404 do contrato. — Dep.: T018.
- [ ] T020 [US1] Escrever prova H2/HTTP V03 em `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductRepositoryTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java`: consumir a fixture/manifesto/auxiliares preparados em T003 e verificados em T004; cinco correspondentes mais um não correspondente, Fone A com duas categorias distintas, size=2, páginas 2/2/1, totalElements=5 e totalPages=3; concatenar IDs sem repetição/perda, página posterior vazia com totais e sem filtro páginas 2/2/2, totalElements=6/totalPages=3 e seis IDs uma vez; comparar ambas as sequências com o manifesto independente. Cobrir Fone/Telefone, Café/Cafe, espaços, %, _, barra/apóstrofo e empates. Se já GREEN, preservar esse resultado. — Dep.: T004, T014, T019.
- [ ] T021 [US1] Tratar query/path administrativos em `src/main/java/com/devsuperior/dscommerce/controllers/AdminProductController.java`, `src/main/java/com/devsuperior/dscommerce/controllers/AdminCategoryController.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`: defaults page=0/size=12, size de 1–50, IDs Long positivos, parâmetros repetidos/desconhecidos 400 seguro; manter sem filtro próprio de categorias e sem lógica de negócio no controller. — Dep.: T020.
- [ ] T022 [US1] Executar GREEN de T014/T020 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductRepositoryTest.java`; provar V04 (IDs iguais em nome, categorias não usadas/lista vazia e associações atuais), usar o cenário vazio preparado/verificado em T003/T004, ausência de DML nos GETs e leitura pública legada preservada. — Dep.: T004, T021.
- [ ] T023 [US1] Registrar checkpoint de leitura, V03/V04 e comandos/resultados em `specs/005-manter-produtos/quickstart.md`; marcar explicitamente a parte de US1.8 dependente de T026/T040/T051 e preservar spec/checklist/contratos anteriores. MVP é somente consultas protegidas, não manutenção completa. — Dep.: T022.

## Fase 4: US2 — Incluir um produto válido (P1)

**Objetivo**: Entregar POST completo com normalização, validação conjunta e commit observável.

**Teste independente / checkpoint**: JWT ADMIN, POST válido →201/Location/detalhe e SQL independente; inválidos →400 agregado sem mutação, mantendo categorias.

**Pré-requisitos**: T023 e suporte T004; T024 → T025 → T026 → T027 executam RED sequencialmente, preservando relatórios e evidências antes da próxima invocação. T028 espera todas.

- [ ] T024 [US2] Escrever/executar RED de US2.1/US2.9 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductMaintenanceIntegrationTest.java`: criação por um envio, novo ID, 201/Location com context path, releitura ADMIN/pública/SQL, A,A,B e A,A geram pares únicos e não criam categorias. — Dep.: T023.
- [ ] T025 [US2] Escrever/executar RED HTTP em `src/test/java/com/devsuperior/dscommerce/dto/AdminProductInputContractTest.java` para JSON bruto via MockMvc real: tipos simultaneamente errados, extras/chaves duplicadas, array parcialmente inválido, null/ausente, trailing JSON/lixo, raiz/corpo/sintaxe inválidos; price string e IDs tokens inteiros sem coerção. Não referenciar tipos de produção inexistentes nem chamar compilação falha de RED. — Dep.: T024.
- [ ] T026 [US2] Escrever/executar RED de US2.2–US2.8 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductValidationIntegrationTest.java`: 3/80/2/81 e 10/9 após trim Unicode; internos intactos; 0.01/máximo/overflow/zero/negativo/1.000/1.001; URI http/https/relativa/malformada/indisponível, textos >255, cinco obrigatórios, seleção vazia/sem categorias e referências mistas. Oráculo inclui snapshot independente e ausência de acesso HTTP/DNS externo. — Dep.: T004, T025.
- [ ] T027 [US2] Escrever/executar RED V02/V06 de US2.6 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDiagnosticIntegrationTest.java`: nome de 2 caracteres/descrição de 9/preço zero/imagem relativa/categoria comprovadamente ausente dão os cinco campos; ID malformado não oculta outros legíveis. Instrumentar SQL sem DML e nenhuma entrada na manutenção durante diagnóstico rejeitado; checar DTO imutável após sua criação em T039. — Dep.: T026.
- [ ] T028 [US2] Criar `src/main/java/com/devsuperior/dscommerce/dto/ProductWriteRequestDTO.java` e `src/main/java/com/devsuperior/dscommerce/dto/ProductInputDiagnostic.java` com cinco campos de contrato e metadados internos de presença/formato/índices, nunca serializados como entrada/saída; representar valores normalizados em objeto interno imutável, sem entidade exposta. — Dep.: T024, T025, T026, T027.
- [ ] T029 [US2] Criar `src/main/java/com/devsuperior/dscommerce/config/AdminProductRequestJsonConfig.java` com leitor streaming somente para ProductWriteRequestDTO: consumir objeto examinável, guardar erros independentes/extras/duplicados sem fail-fast, conservar índices legíveis e rejeitar corpo não examinável com erro body; não modificar Jackson global/login/pedidos nem coagir tokens. — Dep.: T028.
- [ ] T030 [US2] Criar `src/main/java/com/devsuperior/dscommerce/services/ProductInputValidationService.java`: normalização imutável e contagem por pontos de código; nome de 3–80/descrição >=10; BigDecimal exato, setScale(2,UNNECESSARY), precisão<=19 e positividade; URI/parseServerAuthority sem rede, imagem original; regras/decisões ficam nos serviços, sem consulta ao banco nesta política local. — Dep.: T029.
- [ ] T031 [US2] Criar `src/main/java/com/devsuperior/dscommerce/services/ProductCategoryDiagnosticService.java`: repetir guarda antes de leitura, consultar todos IDs distintos legíveis em lote via CategoryRepository, diagnosticar cada índice inválido/inexistente sem salvar subconjunto; não mutar DTO/entidades, emitir DML ou abrir transação de manutenção. — Dep.: T030.
- [ ] T032 [US2] Criar `src/main/java/com/devsuperior/dscommerce/validation/ValidProductInput.java` e `src/main/java/com/devsuperior/dscommerce/validation/ProductInputConstraintValidator.java`, anotar ProductWriteRequestDTO e adaptar os diagnósticos dos serviços em violations por campo na fronteira @Valid; sem repository direto, regra de negócio no validator, fail-fast/group sequence ou mutação do DTO. Preservar limites da exceção IV. — Dep.: T031.
- [ ] T033 [US2] Agregar/ordenar/deduplicar erros administrativos em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java` por campo/índice/code e trio field/code/message, conforme contracts/README; distinguir body não examinável de cinco campos conhecidos e ValidationException com causas de guarda 401/403 versus falha interna 500. Preservar envelopes antigos. — Dep.: T032.
- [ ] T034 [US2] Adequar `src/main/java/com/devsuperior/dscommerce/entities/Product.java` para description/image em texto grande/CLOB sem máximo255 comercial, preservando name/NUMERIC(19,2)/PositiveOrZero/legado e associações sem cascade REMOVE/ALL; não executar migração corretiva, truncar nem reescrever dados anteriores. — Dep.: T033.
- [ ] T035 [US2] Criar/executar `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductSchemaTest.java`: metadata e SQL direto provam capacidade NUMERIC(19,2), par produto/categoria PK/unique equivalente, NOT NULL e FKs, par duplicado/referência ausente rejeitados, descrição/imagem válidas >255 sem truncamento. Ajustar somente mapping faltante em `src/main/java/com/devsuperior/dscommerce/entities/Product.java`, sem duplicar constraint equivalente; testes existentes GREEN não recebem falha artificial. — Dep.: T034.
- [ ] T036 [US2] Criar inclusão em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java` e inválidos agregados em `src/main/java/com/devsuperior/dscommerce/services/exceptions/InvalidProductInputException.java`: transação REQUIRED via proxy, guarda atual e revalidação completa/referências antes de DML, consolidar IDs, salvar/flush/mapear detalhe e só confirmar após commit; não continuar transação falha nem modificar Category. — Dep.: T035.
- [ ] T037 [US2] Adicionar POST /admin/products em `src/main/java/com/devsuperior/dscommerce/controllers/AdminProductController.java` com @Valid antes da manutenção, 201 e Location do GET real preservando context path; preço/normalização vêm dos serviços e response chega após conclusão transacional. — Dep.: T036.
- [ ] T038 [US2] Executar GREEN de T024–T027 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductMaintenanceIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/dto/AdminProductInputContractTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductValidationIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDiagnosticIntegrationTest.java`; confirmar snapshot externo sem delta nas rejeições e sucesso visível, sem fabricar RED de caso já atendido. — Dep.: T037.
- [ ] T039 [US2] Complementar provas da exceção IV em `src/test/java/com/devsuperior/dscommerce/services/ProductInputValidationServiceTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDiagnosticIntegrationTest.java`: comparar DTO/metadados antes/depois de isValid, observar leituras de referências e zero DML/zero chamada à manutenção em diagnóstico válido isolado e inválido; inspecionar que decisões pertencem aos serviços e validator só adapta, sem instrumentação produtiva. — Dep.: T038.
- [ ] T040 [US2] Escrever em `src/test/java/com/devsuperior/dscommerce/services/ProductMaintenanceServiceTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductReferenceRaceIntegrationTest.java` prova de chamada direta ao proxy sem @Valid e de categoria perdida/ADMIN revogado entre boundary e escrita; revalidar antes de DML, preservar snapshot e distinguir FK ausente comprovada 400 de falha interna 500. Executar RED somente de lacuna observada, com barreiras/conexões reais para corrida. — Dep.: T039.
- [ ] T041 [US2] Completar apenas lacunas demonstradas por T040 em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java`, `src/main/java/com/devsuperior/dscommerce/services/ProductCategoryDiagnosticService.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`; classificar categoria ausente tardia só por operação/FK inequívoca, sem reconsulta na transação abortada nem promessa de rediagnóstico completo tardio. Se T040 já GREEN, revisar e registrar sem alterar comportamento. — Dep.: T040.
- [ ] T042 [US2] Executar GREEN de política/diagnóstico/revalidação em `src/test/java/com/devsuperior/dscommerce/services/ProductInputValidationServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/services/ProductMaintenanceServiceTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductReferenceRaceIntegrationTest.java`, além das consultas afetadas; registrar ausência de rede/DML no diagnóstico, sem confundir rejeição antes de DML com rollback após escrita. — Dep.: T041.
- [ ] T043 [US2] Registrar checkpoint V02/V05/V06/V08 e alcance observado da exceção IV em `specs/005-manter-produtos/quickstart.md`; conservar status EXCEÇÃO DOCUMENTADA em `specs/005-manter-produtos/plan.md` e `specs/005-manter-produtos/research.md`, sem PASS estrito, e completar US1.8 no caminho de inclusão sem categorias. — Dep.: T042.

## Fase 5: US3 — Atualizar integralmente um produto (P1)

**Objetivo**: Substituir os cinco dados sem mudar ID, com legado legível e preço histórico independente.

**Teste independente / checkpoint**: Fixtures existentes bastam: PUT altera tudo ou nada, inexistente nunca vira upsert, leitura SQL anterior/posterior conserva histórico.

**Pré-requisitos**: T043; compartilha políticas e manutenção de US2, sem criar dependência de POST para fixtures.

- [ ] T044 [US3] Escrever/executar RED PUT em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductReplacementIntegrationTest.java` para US3.1–US3.5/US3.8: mesmo ID, nova seleção exata, categorias retiradas existentes, normalização, omissões/múltiplos inválidos/repetições e snapshot integral; corpo válido + alvo inexistente 404, corpo inválido + mesmo alvo 400 agregado. — Dep.: T043.
- [ ] T045 [US3] Escrever/executar testes US3.6 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductLegacyReadIntegrationTest.java`: preço zero/nome fora dos limites/sem categorias permanecem legíveis sem correção; PUT completo válido aceito e inválido preserva estado. Leitura já GREEN permanece GREEN; exigir RED apenas da atualização faltante. — Dep.: T044.
- [ ] T046 [US3] Escrever/executar RED V10/US3.7 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductHistoryIntegrationTest.java`: pedido real via /orders com JWT real por 10.00 × 2; ler IDs/vínculos/position/quantity/unit_price independentemente, calcular BigDecimal unitPrice×quantity e soma dos itens (20.00), comparar esperados após PUT do catálogo para 12.00 e pedido posterior a 12.00 × 2 = 24.00; nunca capturar subtotal/total como colunas ou usar getters da aplicação como único oráculo. — Dep.: T045.
- [ ] T047 [US3] Adicionar substituição explícita dos cinco dados e associações à entidade gerenciada em `src/main/java/com/devsuperior/dscommerce/entities/Product.java`, conservando ID e categorias; não reconstruir outra entidade com ID para merge/upsert nem atualizar OrderItem. — Dep.: T044, T045, T046.
- [ ] T048 [US3] Adicionar lookup administrativo com PESSIMISTIC_WRITE em `src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java`, sem fetch de coleção na consulta de lock; manter consultas públicas e nenhuma promessa de lock nas compras. — Dep.: T047.
- [ ] T049 [US3] Adicionar replace transacional em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java`: guarda antes de lookup/lock do alvo, 404 inexistente, revalidação completa/referências dentro da transação antes de mutar, substituição integral, flush e mapping pós-sucesso; preços históricos/pedidos/itens não são regravados. — Dep.: T048.
- [ ] T050 [US3] Adicionar PUT /admin/products/{id} em `src/main/java/com/devsuperior/dscommerce/controllers/AdminProductController.java`: @Valid/diagnóstico precedem existência, 200 detalhe com ID original, sem PATCH ou campo omitido preservado implicitamente; conservar envelope de erro administrativo. — Dep.: T049.
- [ ] T051 [US3] Complementar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductReferenceRaceIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/services/ProductMaintenanceServiceTest.java` para PUT: conta removida/ADMIN revogado/categoria perdida entre boundary e serviço, referência mista e inexistente repetida, direct proxy sem @Valid; snapshot de todos os dados/pares anterior preservado e sem acesso ao alvo antes da guarda. — Dep.: T050.
- [ ] T052 [US3] Criar prova Admin/Admin em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductConcurrencyIntegrationTest.java`: duas transações/conexões e barreiras reais mostram substituições completas serializadas no mesmo produto, sem estados mistos; não exigir ETag/detecção de editor antigo ou vencedor universal. Corrigir apenas lacuna evidenciada no lock/replace em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java` ou `src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java`. — Dep.: T051.
- [ ] T053 [US3] Executar GREEN dos testes T044–T046/T051/T052 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductReplacementIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductLegacyReadIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductHistoryIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductConcurrencyIntegrationTest.java`; repetir leitura independente antes da limpeza, preservar criação/consultas e completar US1.8 na atualização sem categorias. — Dep.: T052.
- [ ] T054 [US3] Registrar checkpoint V07/V10/V12 em `specs/005-manter-produtos/quickstart.md`, separando cálculos históricos de colunas SQL, revalidação de referências, inexistência US3.5/SC-010 e leitura legada US3.6; resultado atual não usa 926 históricos como prova. — Dep.: T053.

## Fase 6: US4 — Excluir sem destruir dados relacionados (P1)

**Objetivo**: Excluir só produto livre e seus pares; FK final protege categorias/compras e concorrência.

**Teste independente / checkpoint**: Fixtures com produto livre, vendido, inexistente e FK extra de teste;204/404/409 com snapshots independentes e nenhuma cascata destrutiva.

**Pré-requisitos**: T054; reutiliza lock/serviço de manutenção. Pedidos e referências são preparados pela fixture, sem depender de CRUD adicional.

- [ ] T055 [US4] Escrever/executar RED DELETE US4.1–US4.4 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDeletionIntegrationTest.java`: livre 204/depois GET 404, categorias e vínculos de outro produto intactos; inexistente 404; qualquer OrderItem independente de status 409; FK adicional temporária exclusivamente de teste também 409, sem destruir relacionados. — Dep.: T054.
- [ ] T056 [US4] Escrever/executar prova JDBC independente em `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductReferentialIntegrityTest.java`: FK real impede SQL direto apagar pai vendido/produzir órfão, metadata comprova restrição sem cascade/setnull; FK extra de teste documenta só aquela relação, não todas as futuras. Constraints existentes que já GREEN não serão enfraquecidas para obter RED. — Dep.: T055.
- [ ] T057 [US4] Criar `src/main/java/com/devsuperior/dscommerce/repositories/OrderItemRepository.java` com existência de qualquer item por productId, sem filtro por status/ramificação de negócio; precheck não substitui FK e não modifica `src/main/java/com/devsuperior/dscommerce/services/OrderService.java`. — Dep.: T055, T056.
- [ ] T058 [US4] Conferir/nomear FKs pertinentes em `src/main/java/com/devsuperior/dscommerce/entities/Product.java` e `src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java`: fk_order_item_product, fk_product_category_product e fk_product_category_category, conservando PK/unique e demais constraints; sem REMOVE/ALL para Category/Order/OrderItem e sem SET NULL. Só corrigir mapping necessário para o DDL real comprovado em T056. — Dep.: T057.
- [ ] T059 [US4] Adicionar delete transacional em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java`: guarda atual, alvo lockado e 404 se inexistente, precheck OrderItem, remoção somente dos pares e produto, flush/conclusão; nenhuma continuação depois da falha, exclusão de dados para contornar FK ou soft delete fora do plano. — Dep.: T058.
- [ ] T060 [US4] Criar `src/main/java/com/devsuperior/dscommerce/services/exceptions/ProductDeletionIntegrityException.java` e completar classificação após proxy em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`: operação/FK/metadata/cause confirmada 409, categoria pai ausente em escrita400 quando inequívoca, falha desconhecida/timeout/deadlock 500 seguro; distinguir exceptions Bean Validation/Hibernate e alcançar flush/commit, sem basear decisão em texto SQL bruto. — Dep.: T059.
- [ ] T061 [US4] Adicionar DELETE /admin/products/{id} em `src/main/java/com/devsuperior/dscommerce/controllers/AdminProductController.java`, retornando 204 sem corpo apenas após conclusão do serviço; não confirmar sucesso para inexistente/referência/falha tardia nem alterar categorias/pedidos. — Dep.: T060.
- [ ] T062 [US4] Executar GREEN T055/T056 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDeletionIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductReferentialIntegrityTest.java`; conferir causa/FK realmente atingida e estado por outra conexão antes da limpeza, com controle positivo de exclusão permitida. — Dep.: T061.
- [ ] T063 [US4] Completar V10 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductHistoryIntegrationTest.java`: após alteração do catálogo, DELETE impedido e pedido posterior, reler todos itens/campos históricos por conexão independente e comparar unit_price/quantity/posições/IDs e cálculos exatos com fixture esperada, preservando produto/pares e compras anteriores. — Dep.: T062.
- [ ] T064 [US4] Completar corrida compra/delete em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductConcurrencyIntegrationTest.java`: duas conexões/barreiras, ambos interleavings, compra referenciando primeiro impede delete e delete primeiro impede item órfão; observar SQL/resultados/snapshots, não usar sleeps arbitrários ou mudar contrato de /orders. Falha segura de timeout não substitui prova do caminho pretendido. — Dep.: T063.
- [ ] T065 [US4] Executar GREEN T063/T064 e regressões de pedidos em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductHistoryIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductConcurrencyIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java`; registrar checkpoint V08–V10/V12 e limites de concorrência em `specs/005-manter-produtos/quickstart.md`. — Dep.: T063, T064.

## Fase 7: US5 — Restringir manutenção e preservar jornadas (P1)

**Objetivo**: Completar matriz real de segurança, erros, atomicidade e regressões de todas as seis operações.

**Teste independente / checkpoint**: Full-context/JWT/banco reais; snapshots independentes provam rejeições/rollback, público/login/pedidos seguem contratos antigos.

**Pré-requisitos**: T065; a proteção básica já existe desde T013. Casos já satisfeitos devem permanecer GREEN.

- [ ] T066 [US5] Completar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductSecurityIntegrationTest.java` para as seis operações/US5.1–US5.4: ADMIN isolado/ambos, CLIENT/nenhum, sem token/assinatura inválida/expirado/sub ou conta ausente; claims de roles/name forjadas não concedem perfil do banco. JWT positivo sempre do login real; negativos forjados explicitamente identificados e nenhuma escrita/dado restrito em rejeição. — Dep.: T065.
- [ ] T067 [US5] Criar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductLateGuardIntegrationTest.java`: revogar ADMIN/remover conta entre filtro e diagnóstico e entre boundary e manutenção com barreiras/instrumentação exclusiva de teste; comprovar 401/403 antes de lookup do alvo e quando causa conhecida vem envolvida em ValidationException, além de falha real de lookup em H2 isolado 500. — Dep.: T066.
- [ ] T068 [US5] Criar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductFrameworkHttpErrorIntegrationTest.java`:405/406/415 nativos com Allow/Accept pertinentes, envelope seguro quando escrevível, precedência 401/403 sobre corpo/verbo/media type inválido, path/Location com /api, query/IDs inválidos e nenhuma exposição de SQL/token/valores recebidos. — Dep.: T067.
- [ ] T069 [US5] Criar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java` para US5.6–US5.8: GET públicos sem auth/token inválido irrelevante, preço numérico/zero, categorias por nomes deduplicados, login e pedidos CLIENT|ADMIN/envelope415/histórico; filtro ADMIN não muda público e seeds/demo antigos não são ativados. Preservar os testes existentes GREEN em `src/test/java/com/devsuperior/dscommerce/controllers/`. — Dep.: T068.
- [ ] T070 [US5] Executar casos T066–T069 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductLateGuardIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductFrameworkHttpErrorIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java`; registrar RED apenas de lacunas atuais por comportamento, mantendo GREEN dos casos já implementados e das regressões, sem fabricar falhas. — Dep.: T066, T067, T068, T069.
- [ ] T071 [US5] Revisar/corrigir somente lacunas de T070 em `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java`, `src/main/java/com/devsuperior/dscommerce/security/AdminAuthenticationEntryPoint.java`, `src/main/java/com/devsuperior/dscommerce/security/AdminAccessDeniedHandler.java`, `src/main/java/com/devsuperior/dscommerce/services/AdminAccessService.java` e `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java`; manter authorities do banco e guardas antes de consulta, sem resource server global, mudança de jornadas anteriores ou escrita de provisão de usuários. — Dep.: T070.
- [ ] T072 [US5] Revisar/corrigir somente lacunas HTTP/wrappers de T070 em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`: causas conhecidas de guarda 401/403, resolução interna 500, erro de campo 400 e nativos 405/406/415 com headers, scoped ADMIN; não converter indiscriminadamente erro MVC para 500 nem afetar contratos antigos. — Dep.: T071.
- [ ] T073 [US5] Adicionar assertions de sanitização em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductFrameworkHttpErrorIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductSecurityIntegrationTest.java`: body/path/errors e logs normais não expõem payload, token, segredo, senha/hash, SQL, conta/perfis restritos ou stack trace. Corrigir só exposição comprovada nos adaptadores/advice administrativos em `src/main/java/com/devsuperior/dscommerce/security/` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`. — Dep.: T072.
- [ ] T074 [US5] Escrever/executar prova US5.5/V11 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductRollbackIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/config/AdminProductFailureInjectionTestConfig.java`: falha após DML real, após flush, beforeCommit e Connection.commit antes de delegar; instrumentação somente de teste confirma ponto atingido, proxy real e ausência de transação externa, com snapshot independente antes da limpeza. RED só para lacuna observada; falha de preparação não comprova rollback. — Dep.: T073.
- [ ] T075 [US5] Revisar/corrigir só lacunas T074 em `src/main/java/com/devsuperior/dscommerce/services/ProductMaintenanceService.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`: rollback das alterações da mesma transação, sem sucesso ou continuação após falha, classificação segura depois do proxy; não acrescentar hooks artificiais em produção, retry ou promessa de desfazer commit confirmado. — Dep.: T074.
- [ ] T076 [US5] Completar `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductRollbackIntegrationTest.java` para POST/PUT/DELETE, snapshots de produtos/categorias/pares/pedidos/itens por ID/valor e controles positivos de commit visível; perda de referências e exceções internas após alteração não deixam dados parciais. Separar rejeição antes de DML, flush, beforeCommit, pré-delegação JDBC e falha de transmissão pós-commit, sem alegar queda física/recuperação/durabilidade do H2 memória. — Dep.: T075.
- [ ] T077 [US5] Executar GREEN das provas T074/T076 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductRollbackIntegrationTest.java`, confirmar transação/SQL/callback realmente exercitados e registrar alcance de cada simulação em `specs/005-manter-produtos/quickstart.md`, sem somar execuções distintas ou trocar retornoHTTP por prova de estado. — Dep.: T076.
- [ ] T078 [US5] Executar GREEN T066–T069/T073 e regressões existentes afetadas em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductLateGuardIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductFrameworkHttpErrorIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/GlobalExceptionHandlerRegressionTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OrderFrameworkHttpErrorIntegrationTest.java`; nenhum resultado histórico fecha a história. — Dep.: T077.
- [ ] T079 [US5] Revisar alcance real da exceção IV contra `.specify/memory/constitution.md`, `specs/005-manter-produtos/plan.md` e `specs/005-manter-produtos/research.md`: inspeção de controllers/validator/services e provas T039/T040/T051/T067 demonstram decisões nos serviços, DTO imutável/zero DML no diagnóstico e guarda/revalidação transacional. Registrar limitações; mutação ou expansão indevida reabre gate e impede fechamento, sem transformar exceção em PASS estrito. — Dep.: T078.
- [ ] T080 [US5] Registrar checkpoint V01/V02/V11/V13 e revisão Governance em `specs/005-manter-produtos/quickstart.md`, distinguindo comandos/fontes/HEAD/diff/relatórios atuais, preservação dos contratos e pendências reais; não alterar checklist aprovado para afirmar entrega funcional. — Dep.: T079.

## Fase 8: Fechamento e revisão transversal

**Objetivo**: Confrontar entrega com todas as fontes e produzir evidência final atual.

**Teste independente / checkpoint**: 37 cenários/20FR/10SC com testes concretos, suíte completa atual aprovada e limitações constitucionais/operacionais registradas.

**Pré-requisitos**: Todas as histórias e checkpoints T023/T043/T054/T065/T080; a suíte completa ocorre só após os grupos afetados GREEN.

- [ ] T081 Conferir a matriz abaixo contra `specs/005-manter-produtos/spec.md`, `specs/005-manter-produtos/plan.md` e casos reais em `src/test/java/com/devsuperior/dscommerce/controllers/`, `src/test/java/com/devsuperior/dscommerce/services/` e `src/test/java/com/devsuperior/dscommerce/repositories/`: cada um dos 37 cenários, 20 FR e 10 SC precisa de prova correspondente e estado observado; apontar lacuna concreta e resolvê-la antes de fechar, sem marcar task por 926 testes históricos. — Dep.: T080.
- [ ] T082 Revisar rotas/DTOs/status/headers/erros/legado reais contra `specs/005-manter-produtos/contracts/product-maintenance-openapi.yaml` e `specs/005-manter-produtos/contracts/README.md`; conferir links/comandos e alinhamento de `specs/005-manter-produtos/data-model.md`/`specs/005-manter-produtos/quickstart.md`, inclusive V03/V10 e exceção IV. Preservar invocação/resultado OpenAPI históricos; não repetir validador comprovado por rotina nem modificar contrato de negócio para encobrir falha. — Dep.: T081.
- [ ] T083 Executar futuramente suíte completa na raiz com `.\mvnw.cmd test`, após grupos novos/afetados GREEN; preservar relatórios anteriores e examinar somente XML/TXT atualizados em `target/surefire-reports/`, registrar versões/HEAD/diff/comando/exit/contagens/causas em `specs/005-manter-produtos/quickstart.md`. Falha exige correção e nova execução justificada, sem atribuir CI/926 históricos à revisão atual. — Dep.: T082.
- [ ] T084 Atualizar evidências de implementação em `specs/005-manter-produtos/quickstart.md` e estado/limitações pertinentes em `specs/005-manter-produtos/plan.md`/`specs/005-manter-produtos/research.md` sem reescrever histórico: resultados V01–V13, RED/GREEN efetivos, snapshot/cálculos, exceção IV e limites de rollback; preservar `specs/005-manter-produtos/spec.md` e `specs/005-manter-produtos/checklists/requirements.md` aprovados. — Dep.: T083.
- [ ] T085 Revisar diff e arquivos novos de `src/main/`, `src/test/` e `specs/005-manter-produtos/tasks.md`, executar git diff --check e conferência de arquivos não rastreados, comparar caminhos/escopo com `AGENTS.md`; remover somente inconsistências introduzidas pela implementação autorizada, sem stage/commit/push/ações remotas automáticas. — Dep.: T084.
- [ ] T086 Emitir relato final baseado na revisão de `specs/005-manter-produtos/tasks.md`, `specs/005-manter-produtos/quickstart.md` e `.specify/memory/constitution.md`: resultados atuais, pendências, limitações e Git; considerar completo somente após evidências pertinentes e revisão da exceção, mantendo caixas abertas de trabalho não executado ou bloqueado. Nenhuma task autoriza integração/publicação implicitamente. — Dep.: T085.

## Dependências e checkpoints de execução

```text
Preparação T001–T005
  → Fundação T006–T013 (segurança obrigatória)
  → US1 T014–T023 (consultas)
  → US2 T024–T043 (inclusão/políticas comuns)
  → US3 T044–T054 (substituição/lock/histórico)
  → US4 T055–T065 (exclusão/FK/corridas)
  → US5 T066–T080 (matriz completa/rollback/regressões)
  → Fechamento T081–T086 (revisão/suíte/documentação)
```

- Fundação é pré-requisito real de todas as histórias: nenhuma rota ADMIN é exposta pelo fallback público durante os incrementos.
- US1 funciona com dados previamente preparados, sem POST. US1.8 entrega opções vazias em T022 e só comprova impossibilidade de escrita sem categoria em T026/T040 (incluir) e T044/T051 (atualizar); o checkpoint de leitura não declara esse cenário inteiro entregue.
- US2 usa CategoryRepository/DTOs e detalhe para Location/releitura da US1. US3 usa política/diagnóstico/DTOs e manutenção da US2; US4 usa lock da US3. Escritas compartilham ProductMaintenanceService/AdminProductController/GlobalExceptionHandler: não implementar histórias concorrentes nesses arquivos.
- US5 começa na fundação com segurança básica; sua fase completa exige os seis métodos para matriz funcional, rollback e preservações. Casos antigos e casos novos já GREEN permanecem GREEN.
- GREEN de cada fase exige saída da execução pertinente, não mera existência de teste. Qualquer falha de ambiente interrompe o checkpoint até diagnóstico; limite de simulação deve permanecer registrado.
- Fechamento exige T079 (revisão Governance), T081/T082 (rastreabilidade/contrato) e T083 (suíte completa). Mutação/DML no diagnóstico ou expansão da exceção reabre o gate e impede T086.

## Paralelismo revisado e execução sequencial

Não há tarefas marcadas `[P]` nesta revisão. Arquivos de autoria distintos não bastam para independência da tarefa inteira quando sua verificação usa o mesmo Maven/build/banco/relatórios ou registra evidências no quickstart. Executar uma tarefa por vez, incluindo as verificações previstas nela; provas agrupadas posteriores permanecem nos checkpoints definidos. Não abrir outra invocação Maven enquanto houver uma em curso. Manter H2 exclusivo por contexto, helpers sem edição concorrente e registro dos relatórios antes da próxima execução.

| História | Pré-requisito concluído | Ordem dos grupos antes apresentados como paralelos |
| --- | --- | --- |
| US1 | T014 | T015 → T016 → T017; integrar/revisar DTOs e repository em sequência. |
| US2 | T023/T004 | T024 → T025 → T026 → T027 → T028; RED e evidências de cada tarefa antes da seguinte. |
| US3 | T043 | T044 → T045 → T046 → T047; preservar leituras GREEN e executar RED da atualização faltante em sequência. |
| US4 | T054 | T055 → T056 → T057; DELETE e SQL direto não compartilham execução simultânea. |
| US4 | T062 | T063 → T064 → T065; histórico, corrida e GREEN em sequência. |
| US5 | T065 | T066 → T067 → T068 → T069 → T070; concluir autoria/revisão de cada tarefa antes da próxima e executar o grupo sem outra invocação concorrente. |

Essa serialização entre tarefas não elimina as duas transações/conexões simultâneas controladas **dentro** dos testes de corrida T040/T051/T052/T064/T067. As barreiras desses testes são requisito da prova; não autorizam outro build ou outra tarefa sobre infraestrutura compartilhada.

## Rastreabilidade dos cenários para tarefas concretas

A matriz conserva os requisitos/critérios de cada linha do plano e acrescenta tarefas de teste e implementação. FR-001/FR-002 são transversais a todas as operações; sua matriz completa está na US5 e a proteção inicial em T006–T013. Testes referenciados devem conter assertions do cenário, não somente nome/citação.

| Cenário | Requisitos | Critérios | Tarefas concretas (teste, implementação, verificação) |
| --- | --- | --- | --- |
| US1.1 | FR-003, FR-004 | SC-002 | T014, T017, T018, T019, T020, T021, T022 |
| US1.2 | FR-004 | SC-002 | T014, T017, T018, T019, T020, T022 |
| US1.3 | FR-004 | SC-002 | T014, T017, T020, T022 |
| US1.4 | FR-004 | SC-002 | T014, T017, T018, T020, T022 |
| US1.5 | FR-004 | SC-002 | T014, T017, T020, T022 |
| US1.6 | FR-003, FR-005 | SC-002 | T014, T017, T019, T020, T021, T022 |
| US1.7 | FR-006 | SC-003 | T014, T015, T016, T018, T019, T022 |
| US1.8 | FR-006, FR-011 | SC-003, SC-005 | T014, T016, T022, T026, T031, T038, T040, T044, T051, T053 |
| US2.1 | FR-007, FR-011 | SC-004 | T024, T034, T035, T036, T037, T038 |
| US2.2 | FR-008 | SC-004 | T026, T030, T036, T038 |
| US2.3 | FR-008 | SC-004, SC-005 | T026, T030, T038 |
| US2.4 | FR-009 | SC-004, SC-005 | T025, T026, T030, T035, T038 |
| US2.5 | FR-010 | SC-004, SC-005 | T026, T030, T034, T038, T042 |
| US2.6 | FR-008, FR-009, FR-010, FR-011, FR-012, FR-017 | SC-005 | T025, T026, T027, T029, T030, T031, T032, T033, T038, T039, T042 |
| US2.7 | FR-011, FR-012, FR-017 | SC-005 | T026, T031, T036, T038, T040, T041, T042 |
| US2.8 | FR-007, FR-008, FR-011, FR-012, FR-017 | SC-005 | T025, T026, T028, T029, T032, T038 |
| US2.9 | FR-011 | SC-004 | T024, T026, T031, T035, T036, T038 |
| US3.1 | FR-006, FR-013 | SC-003 | T014, T019, T044, T050, T053 |
| US3.2 | FR-013 | SC-004 | T044, T047, T048, T049, T050, T053 |
| US3.3 | FR-008, FR-013 | SC-004 | T030, T044, T049, T053 |
| US3.4 | FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-017 | SC-005 | T044, T049, T050, T051, T053 |
| US3.5 | FR-014 | SC-010 | T044, T049, T050, T053 |
| US3.6 | FR-008, FR-009, FR-010, FR-011, FR-013, FR-019 | SC-004, SC-005, SC-008 | T045, T049, T050, T053, T069, T078 |
| US3.7 | FR-018 | SC-007 | T046, T049, T050, T053 |
| US3.8 | FR-011, FR-013, FR-017 | SC-004, SC-005 | T035, T044, T049, T051, T053 |
| US4.1 | FR-015 | SC-006 | T055, T057, T059, T061, T062 |
| US4.2 | FR-016, FR-017, FR-018 | SC-006, SC-007 | T055, T056, T058, T059, T060, T062, T063, T065 |
| US4.3 | FR-014 | SC-006 | T055, T059, T061, T062 |
| US4.4 | FR-016, FR-017 | SC-006 | T055, T056, T058, T060, T062 |
| US5.1 | FR-001 | SC-001 | T006, T009, T013, T066, T070, T071, T078 |
| US5.2 | FR-001, FR-002 | SC-001 | T006, T011, T012, T066, T070, T071, T078 |
| US5.3 | FR-002 | SC-001 | T006, T008, T012, T066, T067, T070, T078 |
| US5.4 | FR-001, FR-002, FR-012 | SC-001 | T027, T039, T066, T067, T068, T070, T072, T078 |
| US5.5 | FR-017 | SC-009 | T074, T075, T076, T077 |
| US5.6 | FR-019, FR-020 | SC-008 | T045, T069, T078 |
| US5.7 | FR-018, FR-020 | SC-007, SC-008 | T046, T063, T065, T069, T078 |
| US5.8 | FR-020 | SC-008 | T014, T069, T078 |

### Grupos de prova do quickstart

| Grupo | Tarefas que produzem prova atual na implementação futura |
| --- | --- |
| V01 — segurança real | T006/T011/T013, T066/T067/T070–T073/T078. |
| V02 — estrutura/HTTP/agregação | T025/T027/T029/T032/T033/T038/T039, T068/T072/T073/T078. |
| V03 — busca/paginação sem duplicação | T002–T004 preparam/verificam isolamento, dataset e manifesto antes de T014/T017–T022; T020 compara páginas filtradas/sem filtro, ocorrência única e totais; T023 registra. |
| V04 — categorias/legado | T002–T004 fornecem também cenário sem categorias antes de T014–T019/T022 e T026; T045/T053 preservam legado. |
| V05 — inclusão/commit visível | T024/T036–T038, T004 fornece leitura independente. |
| V06 — limites/referências/exceção IV | T026/T027/T030–T033/T038–T042. |
| V07 — substituição/revalidação | T044/T047–T053. |
| V08 — schema/SQL independente | T035/T056/T058/T062. |
| V09 — exclusão/FKs | T055/T057–T062. |
| V10 — histórico calculado independentemente | US3.7/US4.2/US5.7, FR-018/SC-007: T046/T053/T063/T065; cálculo decimal a partir de campos persistidos, antes/depois de preço/DELETE/pedido posterior. US4.4 cobre outra FK e não substitui esses cenários. |
| V11 — falhas/rollback/commit delimitado | T004/T074–T077. |
| V12 — concorrência real | T052/T053/T064/T065. |
| V13 — regressões | T005/T013/T022/T042/T053/T065/T069/T078/T083. |

SC-001 → segurança; SC-002 → busca; SC-003 → categorias/detalhe; SC-004 → escritas válidas; SC-005 → rejeições agregadas; SC-006 → exclusão; SC-007 → histórico; SC-008 → legado/regressões; SC-009 → atomicidade de falhas; SC-010 → PUT inexistente. A matriz acima associa cada critério a cenários e tarefas, e T081/T083 conferem cobertura e resultados efetivos.

## Estratégia de implementação incremental

1. Preparar ambiente/fixtures/regressões e concluir fundação protegida.
2. Entregar US1 como MVP de **consultas administrativas** e testar por fixtures; não anunciar inclusão/atualização/exclusão ou entrega total. A rejeição de escrita de US1.8 aguarda US2/US3.
3. Acrescentar inclusão com políticas comuns e provas da exceção IV; depois substituição/histórico; depois exclusão/FKs/corridas, usando os checkpoints de cada fase.
4. Completar US5 em todas as operações, com rollback e regressões; corrigir somente lacunas observadas, preservando GREEN anterior.
5. Revisar contrato/rastreabilidade/Governance, executar suíte completa e registrar evidência atual antes do relato final. Não há deploy/demo público, push, CI dispatch, PR ou commit automático neste plano.

## Correções documentais após speckit-analyze

U1 é tratado documentalmente por T002–T004 e dependências explícitas de T014/T020/T022/T026: desativação de seeds somente nos novos contextos, cenários separados, manifesto/auxiliares e verificação da preparação antes dos consumidores. U2 é tratado pela remoção dos 17 marcadores de tarefa `[P]`, encadeamento dos antigos grupos e execução/verificação/evidências sequenciais. Nenhuma dessas preparações ou verificações funcionais foi executada nesta correção.

**I1 — pendência externa preservada**: o título do oráculo V10 em `specs/005-manter-produtos/quickstart.md` cita US4.4 em vez dos cenários históricos US3.7/US4.2/US5.7. A matriz e o grupo V10 deste tasks mantêm as associações corretas; T082 deve apontar a inconsistência. Corrigir o título exige edição futura autorizada de quickstart, fora do escopo exclusivo de tasks. Não há decisão nova de comportamento nem alteração de outro artefato nesta revisão.

## Limites e verificação desta geração

A geração resolve organização e rastreabilidade documental, não comprova comportamento. Não há dúvida material adicional identificada nos artefatos; as provas funcionais e a revisão do alcance real da exceção continuam pendentes. O histórico OpenAPI em quickstart permanece documental e não precisa ser repetido para gerar tarefas.

O template ativo foi retornado por `.specify/scripts/powershell/setup-tasks.ps1 -Json`; fases, dependências, exemplos de paralelismo e estratégia seguem essa estrutura. Conferir antes/depois a existência de `.specify/extensions.yml`: ausente na preparação, sem hook registrado. A conferência final desta geração avalia formato/IDs/labels, cobertura, dependências/caminhos, links e whitespace; seus resultados são relatados na entrega, sem aprovação funcional.
