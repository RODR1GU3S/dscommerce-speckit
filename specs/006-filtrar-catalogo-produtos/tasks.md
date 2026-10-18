---
description: "Tarefas de implementação e validação da Feature 006, ainda não executadas"
---

# Tasks: Filtrar catálogo público de produtos por nome

**Input**: artefatos revisados em `specs/006-filtrar-catalogo-produtos/`, base `d2f76b0edaeebf070b861dc38038fd42d0e83a62`.

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato](contracts/public-products-openapi.yaml), [quickstart.md](quickstart.md) e [constituição](../../.specify/memory/constitution.md).

**Tests**: solicitados expressamente. Escrever testes antes das mudanças de comportamento; obter RED funcional na integração pública ausente. Para normalizador, consulta e regras já existentes, usar caracterização e regressão, sem provocar RED artificial. Todos os itens abaixo são trabalho futuro e nascem abertos; nenhuma tarefa foi executada na geração deste arquivo.

**Organization**: sete fases; preparação e fundamentos, quatro histórias P1 e fechamento. US1 instala a integração compartilhada necessária às demais; US2–US4 comprovam seus próprios cenários e corrigem somente lacunas reais. As fases não autorizam ampliar escopo ou reescrever requisitos/documentos históricos.

## Format: `[ID] [P?] [Story] Description`

- IDs T001–T030; `[US1]`–`[US4]` somente nas fases de história; `[P]` somente para edição independente de arquivos distintos.
- Cada item contém caminhos relativos à raiz, dependências explícitas e condição de conclusão. Registro de execução, quando autorizado, deve ser acrescentado ao `specs/006-filtrar-catalogo-produtos/quickstart.md`, preservando os registros anteriores.
- Maven é sempre sequencial no mesmo checkout/`target/`, inclusive entre itens `[P]`. Não executar os exemplos de comandos durante a geração das tarefas.

## Path Conventions

- Produção: `src/main/java/com/devsuperior/dscommerce/`.
- Testes/suporte: `src/test/java/com/devsuperior/dscommerce/`.
- Artefatos/evidências: `specs/006-filtrar-catalogo-produtos/`.
- `PublicCatalogNameFilterIntegrationTest.java` e seus métodos citados abaixo são propostos, ainda inexistentes. Os demais suportes/testes citados já existem. Nenhuma entidade, tabela, dependência ou configuração produtiva nova é prevista.

## Phase 1: Setup — orientação e baseline proporcional

**Purpose**: identificar a revisão a implementar e estabelecer a condição inicial sem inferir aprovação atual dos 2.614 testes históricos.

- [ ] T001 Confirmar raiz, branch real, HEAD, status e índice; ler `AGENTS.md`, `specs/006-filtrar-catalogo-produtos/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/public-products-openapi.yaml`, `checklists/requirements.md` e `.specify/memory/constitution.md`; registrar a revisão e o escopo no `specs/006-filtrar-catalogo-produtos/quickstart.md`. **Depende de:** nenhuma. **Concluir quando:** contexto correto e alterações anteriores preservados, gates de desenho distinguidos de evidência funcional e ausência de bloqueio confirmada; não trocar branch ou fazer commit/push automaticamente.
- [ ] T002 Estabelecer baseline focal antes de editar produção/testes existentes: executar pelo `mvnw.cmd` os testes `ProductControllerTest`, `ProductCatalogServiceTest`, `PublicCatalogSecurityIntegrationTest`, `PublicProductDetailSecurityIntegrationTest`, `AdminQueryServicesTest`, `AdminProductRepositoryTest` e `ProductRepositoryAdminQueryComponentTest` de `src/test/java/com/devsuperior/dscommerce/`; registrar comando/resultados no `specs/006-filtrar-catalogo-produtos/quickstart.md`. **Depende de:** T001. **Concluir quando:** preparação e comportamento existente estiverem observados e a baseline focal aprovada na revisão atual. Registrar impedimento sem marcar conclusão nem avançar até resolvê-lo; sem repetir suíte completa histórica como etapa inicial nem mudar produção para fabricar baseline.

**Checkpoint**: base focal observada, inclusive normalização/consulta administrativas já implementadas; nenhuma alegação de prova do filtro público novo.

## Phase 2: Foundational — contexto isolado e caracterização HTTP

**Purpose**: fornecer preparação e oráculos reutilizáveis antes das mudanças públicas.

- [ ] T003 Preparar `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` com contextos reais distintos de raiz e `/api`, perfil `test`, filtros reais e OSIV desativado localmente; reutilizar `src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java`, `src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java` e `src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductDatabaseProbe.java` sem alterar seus defaults globais. **Depende de:** T002. **Concluir quando:** controles sem filtro compilarem e usarem H2 sem seeds e com identidade de contexto própria, fixtures commitadas, IDs capturados, limpeza por ownership e SQL inspector somente do contexto focal; nenhuma transação externa em HTTP/proxy e nenhuma mudança global de logging.
- [ ] T004 Criar e executar a caracterização `characterizesExistingPublicPaginationHttp` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`: HTTP servlet real com `Accept: application/json`, contextos raiz e `/api`, com/sem `name`, incluindo defaults/valores vazios de paginação, size 1/50, page -1, size 0/51 e texto não numérico. **Depende de:** T003. **Concluir quando:** status, campos/corpo e cabeçalhos relevantes da base forem registrados no `specs/006-filtrar-catalogo-produtos/quickstart.md`, distinguindo os quatro campos de limites Bean Validation dos erros nativos; campos voláteis eventualmente presentes, como timestamp, devem ser comparados por presença/tipo/formato, sem exigir o mesmo instante literal nem mudar a resposta; controles de acesso sem token/token inválido devem ser válidos. Esse baseline é esperado GREEN e não redefine envelopes.

**Checkpoint**: condições de execução e resposta HTTP anterior registradas antes de qualquer alteração produtiva. A caracterização não substitui RED da seleção por nome.

## Phase 3: User Story 1 — Encontrar produtos por trecho literal do nome (Priority: P1)

**Goal**: integrar o filtro público reaproveitando a semântica administrativa, com caixa ignorada, acentos significativos e caracteres literais.

**Independent Test**: US1.1–US1.7 em catálogo conhecido: trecho/caixa/acentos/%/_, filtro de um caractere e de 81 caracteres; comparar IDs, ordem e totais, sem depender de assinatura interna futura.

### Tests for User Story 1 — antes da integração

- [ ] T005 [US1] Escrever `matchesNameSubstringCaseAccentsAndLiterals` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, cobrindo US1.1–US1.5 com manifestos independentes: Fone Azul/Telefone Preto/Cabo, Café Especial/Cafe Claro, Oferta 10%/Oferta 100, Kit_A/KitXA; conferir caixa equivalente e seleção literal com codificação correta no HTTP. **Depende de:** T004. **Concluir quando:** testes compilarem contra a rota atual e esperados não forem derivados da consulta de produção; nenhuma implementação nova ainda.
- [ ] T006 [US1] Escrever `acceptsShortAndLongNameFilters` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US1.6–US1.7, usando filtro `f` e dados legados de 80/81 letras preparados pelo suporte JDBC commitado. **Depende de:** T005. **Concluir quando:** o esperado distinguir exatamente o nome de 81 letras sem exigir tamanho, truncar filtro ou enfraquecer validação administrativa para preparar dados.
- [ ] T007 [US1] Executar o grupo novo `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` antes de mudar produção e registrar o RED no `specs/006-filtrar-catalogo-produtos/quickstart.md`. **Depende de:** T006. **Concluir quando:** houver falha funcional esperada de seleção/metadados por `name` ainda ignorado, com compilação/preparação/controles válidos. Casos que já passam continuam GREEN; falha de assinatura, contexto, seed ou autenticação não serve como RED, nem se deve alterar componente existente para fazê-lo falhar.

### Implementation for User Story 1 — reaproveitamento e integração

- [ ] T008 [US1] Renomear somente `findAdminPageByName` para `findPageByName` em `src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java` e na chamada de `src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java`; adaptar referências em `src/test/java/com/devsuperior/dscommerce/services/AdminQueryServicesTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductRepositoryTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryAdminQueryComponentTest.java` e no helper `repositoryPage` de `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java`. **Depende de:** T007. **Concluir quando:** todas as referências compilarem com o nome neutro; predicado parametrizado `locate(lower(:name), lower(p.name)) > 0`, count equivalente e ordem nome/ID forem preservados sem duplicar JPQL, criar alias, fetch de categorias ou alterar a consulta administrativa sem filtro; assertions anteriores mantidas, reservando a atualização das duas expectativas públicas para T011.
- [ ] T009 [US1] Extrair o normalizador de busca para `src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java` e usá-lo em `src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java`. **Depende de:** T008. **Concluir quando:** helper puro, sem estado e package-private reproduzir `null`→vazio e remoção externa por pontos de código `isWhitespace || isSpaceChar`, sem compactação/conversão/NFC; guarda ADMIN e DTO administrativo mantidos, sem chamada de validação de escrita, banco ou autorização pelo helper. A baseline T002 é a prova anterior do comportamento extraído, sem RED artificial de refatoração.
- [ ] T010 [US1] Integrar `name` opcional em `src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java` e `src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java`, adaptando e ampliando primeiro os testes pertinentes em `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java` e `src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java`. **Depende de:** T009. **Concluir quando:** `listProducts(name,page,size)` normalizar no serviço, ramo vazio conservar `findAll` com Sort nome/ID, ramo preenchido usar consulta neutra paginada e DTO/metadados atuais; sem novo limite/coerção, entidade, dependência, escrita, handler, parser administrativo ou mudança de segurança. RED válido é o de T007; adaptações de assinatura devem compilar e não contam como RED funcional.
- [ ] T011 [US1] Atualizar especificamente `adminFilterAndCategoryIdentifiersDoNotChangePublicContract` em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java` e `shouldKeepThePublicCatalogNumericAndUnfilteredByTheAdministrativeNameParameter` em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java`, com nomes descritivos da evolução quando necessário. **Depende de:** T010. **Concluir quando:** no primeiro, sem filtro mantiver Alpha/Beta, `ALP` selecionar Alpha e inexistente/`%_` retornarem vazio; no segundo, `fone` usar cinco correspondências em 2/2/1/0 com totais 5/3; preservar variantes de token/contexto, preço numérico, categorias públicas versus IDs administrativos, snapshots e todas as demais garantias. Não apagar os testes nem reescrever registros históricos.
- [ ] T012 [US1] Executar GREEN focal de US1, assinaturas adaptadas e regressão dos componentes compartilhados, selecionando `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java`, `src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/services/AdminQueryServicesTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/AdminProductRepositoryTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryAdminQueryComponentTest.java` e os dois testes atualizados em T011. **Depende de:** T011. **Concluir quando:** US1 e regressões passarem sem enfraquecer assertions, com zero DML/snapshots na janela de leitura, e comando/revisão/resultados forem registrados no `specs/006-filtrar-catalogo-produtos/quickstart.md`; corrigir somente defeitos demonstrados antes do checkpoint.

**Checkpoint**: US1 funcional por HTTP e compartilhamento sem regressão administrativa. É o primeiro incremento técnico demonstrável, não encerramento da feature de quatro histórias P1.

## Phase 4: User Story 2 — Pesquisar com espaços sem modificar os nomes (Priority: P1)

**Goal**: comprovar a distinção entre extremidades do filtro, espaços internos e nome armazenado, inclusive filtro ausente/vazio.

**Independent Test**: US2.1–US2.6 por seleção exata e nome original, com espaços comuns, tabulação, NBSP e U+2003. Depois da integração da US1, parte ou todos os casos podem passar imediatamente.

### Tests for User Story 2

- [ ] T013 [US2] Escrever `normalizesOnlyExternalFilterWhitespace` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US2.1, US2.2 e US2.5: ` FONE `, omitido/vazio/somente espaços e tabulação/NBSP/U+2003 nas extremidades, isolados e combinados. **Depende de:** T012. **Concluir quando:** cada variante tiver oráculo de IDs e metadados equivalente ao trecho aparado ou à mesma página sem filtro, sem limpar nome armazenado.
- [ ] T014 [US2] Escrever `preservesInternalSpacesAndStoredNames` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US2.3, US2.4 e US2.6: um/dois espaços internos, nome legado com extremos e NBSP interno. **Depende de:** T013. **Concluir quando:** seleção distinguir cada forma, resposta conservar texto exato e snapshot independente confirmar dados intactos; fixtures de cada cenário não interferirem entre si.
- [ ] T015 [US2] Completar em `src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java` os casos de `null`, vazio, espaços externos Unicode e internos literais, verificando o ramo sem filtro ou o texto normalizado encaminhado, com a semântica já coberta em `src/test/java/com/devsuperior/dscommerce/services/AdminQueryServicesTest.java`. **Depende de:** T014. **Concluir quando:** os testes observarem decisões do serviço sem reflexão, sem testar algoritmo copiado e sem alterar produção antes de avaliar os casos.

### Integração e checkpoint da User Story 2

- [ ] T016 [US2] Executar os casos US2 e os testes de serviço/normalização administrativos afetados; somente se falharem por discrepância real, corrigir `src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java`, `src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java` ou `src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java` dentro do desenho, e repetir a seleção afetada. **Depende de:** T015. **Concluir quando:** US2 estiver GREEN e o `specs/006-filtrar-catalogo-produtos/quickstart.md` registrar seleção, ausência de mutações e eventual causa/correção. Se tudo já passar, não editar produção nem criar RED artificial; preservar validação de escrita e regras administrativas.

**Checkpoint**: ausência/vazio e espaços comprovados no fluxo público, com nomes persistidos/devolvidos preservados.

## Phase 5: User Story 3 — Navegar pelos resultados filtrados e reconhecer sua ausência (Priority: P1)

**Goal**: comprovar conteúdo, contagem e ordem do mesmo conjunto filtrado e ausência de estado entre consultas.

**Independent Test**: US3.1–US3.6 com manifesto V03 de seis produtos/cinco correspondências, empate de nome e produto com duas categorias; páginas 2/2/1/0 e totais 5/3.

### Tests for User Story 3

- [ ] T017 [US3] Escrever `paginatesFilteredResultsInStoredNameIdOrder` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, reutilizando `prepareV03()` de `src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java` para US3.1–US3.2. **Depende de:** T016. **Concluir quando:** páginas 0/1/2 size2 tiverem 2/2/1, totais 5/3, IDs exatos sem perdas/duplicações e empate por ID, sem usar resultado de produção como esperado ou contar SELECTs rigidamente.
- [ ] T018 [US3] Escrever `returnsCoherentEmptyFilteredPages` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US3.3–US3.5: trecho inexistente em página válida inclusive não zero, página 3 além das cinco correspondências e banco vazio com/sem filtro. **Depende de:** T017. **Concluir quando:** 200/conteúdo vazio preservarem page/size e os totais adequados, distinguindo zero correspondências de página posterior, sem clamp/404 ou seeds globais.
- [ ] T019 [US3] Escrever `doesNotRetainNameBetweenRequests` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US3.6, pesquisando e depois omitindo `name` no mesmo contexto com a mesma paginação. **Depende de:** T018. **Concluir quando:** segunda resposta corresponder ao manifesto completo independente e não conservar restrição implícita, com dados intactos.

### Integração e checkpoint da User Story 3

- [ ] T020 [US3] Executar os novos casos US3 de `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`; se houver falha real, corrigir somente a integração em `src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java` ou a consulta/count/ordem em `src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java`, reexecutando também a regressão afetada pela correção. **Depende de:** T019. **Concluir quando:** US3 estiver GREEN, sem pós-filtro em memória, fetch de coleção, ordem normalizada ou mutação, com evidências no `specs/006-filtrar-catalogo-produtos/quickstart.md`. Se o reaproveitamento já satisfizer todos os casos, não exigir alteração produtiva/RED.

**Checkpoint**: navegação e páginas vazias verificadas sobre o conjunto filtrado, inclusive independência das solicitações.

## Phase 6: User Story 4 — Continuar usando o catálogo e os detalhes existentes (Priority: P1)

**Goal**: preservar acesso, representação, paginação, detalhes por ID, legado, administração e demais jornadas.

**Independent Test**: US4.1–US4.8 com os controles da base T004 e fixtures conhecidas; regressões verificam mudança somente de `name` ignorado para filtro ativo.

### Tests for User Story 4

- [ ] T021 [P] [US4] Completar a cobertura MVC em `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java` para US4.1–US4.3: encaminhamento de `name`, defaults independentes, paginação válida/inválida e campos/tipos públicos existentes. **Depende de:** T020. **Concluir quando:** testes cobrirem a fronteira sem mocks como única prova de consulta nem padronização nova de erro nativo; não editar o arquivo do grupo HTTP durante esta tarefa paralela.
- [ ] T022 [P] [US4] Escrever `preservesPublicPaginationShapeAndAccess` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US4.1–US4.4, com/sem filtro e sem token/token inválido/consumidor autenticado; comparar por HTTP real os casos nativos com a base T004, em raiz e `/api`. **Depende de:** T020. **Concluir quando:** defaults, size1/50, rejeições, campos exatos/preço numérico e acesso forem verificados; limites Bean Validation devem cumprir `ErrorResponse` diretamente, não apenas o `anyOf` permissivo de 400. Não executar Maven simultaneamente a outra tarefa.
- [ ] T023 [US4] Escrever `preservesDetailsAndLegacyIndependentlyOfFilter` em `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` para US4.5–US4.7: produto existente excluído da busca acessível por ID, categorias de nomes repetidos deduplicadas, inexistente 404 e produtos legados curtos/longos/espaçados, preço zero/sem categorias. **Depende de:** T022. **Concluir quando:** respostas originais e snapshots forem conferidos sem validação retroativa, correção de dados ou mudanças no contrato de detalhes.
- [ ] T024 [US4] Conferir US4.8 e as demais garantias nas alterações de T011 em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java`, reaproveitando seus controles administrativos, login, pedidos e histórico; assegurar que o grupo `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` observe zero DML e snapshots antes/depois dos GETs. **Depende de:** T023. **Concluir quando:** nenhuma assertion útil tiver sido removida, ADMIN continuar exigido somente nas rotas administrativas e leituras públicas não alterarem produtos/categorias/contas/pedidos/valores históricos; acrescentar teste somente para lacuna concreta, sem refatorar jornadas não afetadas.

### Integração e checkpoints da User Story 4

- [ ] T025 [US4] Executar o GREEN focal completo do `specs/006-filtrar-catalogo-produtos/quickstart.md`, selecionando `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java`, `src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java`. **Depende de:** T021, T024. **Concluir quando:** os 27 cenários e as expectativas atualizadas estiverem GREEN na mesma revisão final, com os erros nativos comparados a T004 e as evidências registradas; corrigir discrepâncias demonstradas antes de encerrar, sem ampliar contrato ou mudar segurança para contornar falha.
- [ ] T026 [US4] Executar sequencialmente a seleção de regressão definida em `specs/006-filtrar-catalogo-produtos/quickstart.md`, sobre `src/test/java/com/devsuperior/dscommerce/`: `AdminProductRepositoryTest`, `ProductRepositoryAdminQueryComponentTest`, `AdminQueryServicesTest`, `AdminQueryServiceComponentIntegrationTest`, `ProductRepositoryTest`, `PublicCatalogSecurityIntegrationTest`, `PublicProductDetailSecurityIntegrationTest`, `AdminProductGetHttpSecurityTest`, `GlobalExceptionHandlerRegressionTest` e `OrderPublicRegressionIntegrationTest`. **Depende de:** T025. **Concluir quando:** compartilhamento, detalhes, segurança e jornadas afetadas passarem, com comando/alcance/resultado no quickstart; ampliar ou repetir somente diante de mudança, falha ou interação concreta.

**Checkpoint**: todos os cenários e regressões afetadas aprovados na revisão observada, ainda sujeitos à auditoria e suíte completa final.

## Phase 7: Polish & Cross-Cutting Concerns — fechamento verificável

**Purpose**: comprovar rastreabilidade, gates, suíte e documentação final sem transformar histórico em execução atual.

- [ ] T027 Auditar a matriz deste `specs/006-filtrar-catalogo-produtos/tasks.md` contra métodos/assertions e relatórios dos testes em `src/test/java/com/devsuperior/dscommerce/`, verificando 27 cenários, 14 FR e 6 SC; confrontar implementação com `specs/006-filtrar-catalogo-produtos/contracts/public-products-openapi.yaml` e gates do `plan.md`. **Depende de:** T026. **Concluir quando:** cada cenário tiver prova atual identificável, contrato e ausência de mutações estiverem coerentes e gates próprios reavaliados com código, sem herdar/apagar a EXCEÇÃO DOCUMENTADA do Gate IV da 005. Registrar lacuna concreta e sua correção/validação antes de prosseguir, sem editar requisitos aprovados para acomodar código.
- [ ] T028 Executar uma suíte completa final por `.\mvnw.cmd test`, na raiz, após as mudanças funcionais e testes estarem estabilizados; registrar revisão, comando, contagens reais e resultado em `specs/006-filtrar-catalogo-produtos/quickstart.md` e examinar `target/surefire-reports/`. **Depende de:** T027. **Concluir quando:** suíte sem filtros/skips passar na revisão efetivamente entregue. Bloqueio ou falha deve ser registrado sem marcar conclusão; corrigir e obter nova validação apropriada antes de avançar. Não usar 2.614 como contagem esperada nem repetir a suíte por alteração exclusivamente documental posterior.
- [ ] T029 Consolidar em `specs/006-filtrar-catalogo-produtos/quickstart.md` as evidências de T002–T028: base HTTP, RED funcional, GREENs, suíte final, matriz cenário→método/assertion/execução, limitações e resultado dos gates. **Depende de:** T028. **Concluir quando:** histórico preservado e procedimentos futuros distinguidos de resultados reais; a validação OpenAPI 0.9.0 registrada no planejamento permanecer identificada como documental, sem substituir HTTP/testes. Se o contrato estiver inalterado, não repetir seu validador sem motivo; divergência que exija mudar artefato aprovado deve ser explicitada, sem mudança silenciosa de escopo.
- [ ] T030 Revisar diff completo, arquivos novos, caminhos/links e `git diff --check`, confirmar escopo em `src/main/`, `src/test/` e `specs/006-filtrar-catalogo-produtos/`, e relatar estado final do Git. **Depende de:** T029. **Concluir quando:** não houver trabalho alheio incluído, evidência sem execução ou pendência omitida; spec/checklist/contratos históricos/constituição preservados e resultado entregue sem stage, commit, push, troca de branch ou alteração de `main` não autorizados.

## Dependencies & Execution Order

### Phase Dependencies

```text
T001 → T002 → T003 → T004
  → US1: T005 → T006 → T007 (RED HTTP) → T008 → T009 → T010 → T011 → T012
  → US2: T013 → T014 → T015 → T016
  → US3: T017 → T018 → T019 → T020
  → US4: (T021 || T022 → T023 → T024) → T025 → T026
  → fechamento: T027 → T028 → T029 → T030
```

A dependência entre histórias decorre da integração única e dos arquivos compartilhados; não exige criar implementação diferente para cada história. Depois de US1, cada história é verificável isoladamente com suas fixtures/métodos, sem depender do estado deixado por outro teste. US2/US3/US4 podem apenas comprovar comportamento já entregue pelo reaproveitamento; correção produtiva é condicional a falha real.

### Regras de teste, execução e evidência

- T002 comprova a base; T004 caracteriza HTTP antes da mudança; T007 demonstra comportamento público ausente. Os testes administrativos existentes não precisam falhar para justificar sua extração/renomeação.
- Testes de US2–US4 precedem qualquer correção que revelem. Não fabricar defeito, enfraquecer assertion ou mudar requisito para obter RED/GREEN. Adaptação de assinatura que não compila é preparação, não RED funcional.
- Para checkpoints intermediários US2/US3, selecionar somente métodos novos/afetados do grupo HTTP e serviços correspondentes. Executar o grupo completo em T025 e a suíte completa em T028; não repetir as mesmas seleções sem alteração ou necessidade identificada.
- Usar Maven Wrapper Java 21/Maven 3.9.16 conforme `AGENTS.md`. Os comandos de GREEN focal/regressão/suíte estão no quickstart; baseline usa as classes de T002. Usar sufixos de relatório por checkpoint e registrar nomes/seleções realmente executados; sufixos e seletores não autorizam skips ou contagens fictícias.
- Nunca executar Maven concorrente no mesmo `target/`. `[P]` significa somente edição independente; não significa paralelizar JVMs, HTTP fixtures no mesmo banco ou escrita de evidências no mesmo arquivo.
- Ausência de mutação exige observar a janela de GET e snapshot independente depois do retorno e antes da limpeza. Não exigir número fixo de SELECT/count. Isolamento H2 não isola Logback global; manter configuração de teste focal e cleanup por ownership.
- O OpenAPI foi validado como documento antes desta geração, conforme quickstart. Esse fato não comprova `name` implementado, envelopes HTTP reais ou gates de implementação; os 2.614 testes históricos tampouco.

### Parallel Examples por história

| História | Exemplo seguro de execução | Motivo |
| --- | --- | --- |
| US1 | T005 → T006 → T007, depois T008–T012 | Grupo HTTP e componentes compartilhados exigem sequência; nenhum `[P]` artificial. |
| US2 | T013 → T014 → T015 → T016 | As duas primeiras tarefas editam o mesmo teste; avaliar casos antes de qualquer correção. |
| US3 | T017 → T018 → T019 → T020 | Mesmo grupo/fixtures; preparar e validar sequencialmente. |
| US4 | Depois de T020, editar T021 e T022 em paralelo; T023 após T022; reunir em T025 | Arquivos distintos somente em T021/T022. Todas as execuções Maven e escritas no quickstart continuam serializadas. |

## Rastreabilidade dos 27 cenários

Os métodos abaixo são nomes propostos dentro de `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, exceto as regressões existentes explicitamente indicadas. Cada linha é um cenário da spec, não uma execução já realizada. A tabela de FR/SC do quickstart complementa esta matriz sem ser alterada.

| Cenário | Teste/oráculo verificável | Tarefas de teste e entrega |
| --- | --- | --- |
| US1.1 | `matchesNameSubstringCaseAccentsAndLiterals`: somente Fone Azul/Telefone Preto para `fone` | T005, T007, T010–T012 |
| US1.2 | `matchesNameSubstringCaseAccentsAndLiterals`: `fone`/`FONE`/`FoNe`, IDs/ordem/totais iguais | T005, T007, T008–T012 |
| US1.3 | `matchesNameSubstringCaseAccentsAndLiterals`: CAFÉ e cafe selecionam formas distintas | T005, T007, T008–T012 |
| US1.4 | `matchesNameSubstringCaseAccentsAndLiterals`: `10%` literal, não Oferta 100 | T005, T007, T008–T012 |
| US1.5 | `matchesNameSubstringCaseAccentsAndLiterals`: `kit_` literal, não KitXA | T005, T007, T008–T012 |
| US1.6 | `acceptsShortAndLongNameFilters`: filtro `f` | T006–T007, T010–T012 |
| US1.7 | `acceptsShortAndLongNameFilters`: 81 letras selecionam somente 81, sem truncamento | T006–T007, T010–T012 |
| US2.1 | `normalizesOnlyExternalFilterWhitespace`: padding removido só do filtro | T013, T015–T016 |
| US2.2 | `normalizesOnlyExternalFilterWhitespace`: omitido/vazio/somente espaços = sem filtro | T013, T015–T016 |
| US2.3 | `preservesInternalSpacesAndStoredNames`: um/dois espaços distintos | T014–T016 |
| US2.4 | `preservesInternalSpacesAndStoredNames`: nome original com extremos/internos mantido e snapshot igual | T014–T016 |
| US2.5 | `normalizesOnlyExternalFilterWhitespace`: tab/NBSP/U+2003 externos | T013, T015–T016 |
| US2.6 | `preservesInternalSpacesAndStoredNames`: NBSP interno distinto de espaço | T014–T016 |
| US3.1 | `paginatesFilteredResultsInStoredNameIdOrder`: V03 em 2/2/1, totais 5/3 | T017, T020 |
| US3.2 | `paginatesFilteredResultsInStoredNameIdOrder`: empate Fone B por ID, união exata entre páginas | T017, T020 |
| US3.3 | `returnsCoherentEmptyFilteredPages`: nenhuma correspondência, totais zero | T018, T020 |
| US3.4 | `returnsCoherentEmptyFilteredPages`: página 3 vazia, totais 5/3 preservados | T018, T020 |
| US3.5 | `returnsCoherentEmptyFilteredPages`: banco vazio com/sem filtro | T018, T020 |
| US3.6 | `doesNotRetainNameBetweenRequests`: omitido após filtro recupera conjunto completo | T019–T020 |
| US4.1 | `preservesPublicPaginationShapeAndAccess` e testes MVC: defaults 0/12 | T004, T021–T022, T025 |
| US4.2 | `characterizesExistingPublicPaginationHttp` e comparação posterior: limites/conversões sem mudança | T004, T021–T022, T025 |
| US4.3 | `preservesPublicPaginationShapeAndAccess`: chaves exatas/preço numérico | T021–T022, T025 |
| US4.4 | `preservesPublicPaginationShapeAndAccess`: anonimato/token inválido/consumidor autenticado | T022–T023, T025–T026 |
| US4.5 | `preservesDetailsAndLegacyIndependentlyOfFilter`: ID fora dos resultados acessível | T023, T025–T026 |
| US4.6 | `preservesDetailsAndLegacyIndependentlyOfFilter`: categorias deduplicadas e inexistente 404 | T023, T025–T026 |
| US4.7 | `preservesDetailsAndLegacyIndependentlyOfFilter`: legado legível, nome exato e snapshot intacto | T023, T025–T026 |
| US4.8 | Observação DML/snapshots do grupo público e regressões em `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OrderPublicRegressionIntegrationTest.java` e serviços administrativos | T011–T012, T024–T026, T028 |

T027 audita todas as linhas contra métodos/assertions e execuções reais; T028–T030 encerram a validação e documentação. Total esperado: US1 7 cenários, US2 6, US3 6 e US4 8, sem cenário omitido. Os 14 FR e 6 SC permanecem vinculados pelas matrizes de spec/checklist/quickstart e pela auditoria final, sem nova regra de negócio.

## Implementation Strategy

### MVP técnico e entrega incremental

1. Concluir preparação e fundamentos antes de produção; não inicializar projeto, instalar dependências ou criar schema já existentes.
2. Concluir US1 com RED público válido e GREEN do reaproveitamento: primeiro incremento técnico demonstrável.
3. Comprovar US2, US3 e US4 por suas fixtures, testes e checkpoints; corrigir apenas falhas concretas que os testes revelarem. Todas as quatro histórias são P1 e exigidas para entregar a feature completa.
4. Auditar rastreabilidade/gates, executar a suíte completa uma vez na revisão final e consolidar evidências. Nenhum checkpoint autoriza implantação, stage, commit ou push por si só.

### Estado desta geração

30 tarefas planejadas: preparação 2, fundamentos 2, US1 8, US2 4, US3 4, US4 6 e fechamento 4. Todas abertas. Nenhuma execução dessas tarefas, Maven ou alteração de código/testes foi realizada ao gerar este arquivo. O ponteiro local foi preservado; hooks de extensões serão conferidos pelo fluxo de geração, sem executar implementação.
