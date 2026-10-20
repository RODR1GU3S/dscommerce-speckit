---
description: "Tarefas executáveis da Feature 007 — Consultar meus pedidos"
---

# Tasks: Consultar meus pedidos

**Input**: [spec.md](spec.md), [checklist](checklists/requirements.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contrato](contracts/own-orders-openapi.yaml) e [quickstart.md](quickstart.md).

**Base documental**: `97ac0354d9f3835e041629fee4a661c0afe09eb7`, branch `007-consultar-meus-pedidos`. **Estado desta geração**: 34 tarefas abertas; nenhuma tarefa implementada ou execução funcional realizada. Todas as ações e comandos abaixo são para uma fase futura autorizada.

**Tests**: solicitados explicitamente. Para comportamento novo, escrever primeiro testes HTTP que compilem contra o backend existente, observar falha funcional e então implementar. Não exigir RED de autenticação JWT, cálculos, contratos ou regressões já existentes; registrar o GREEN de caracterização quando corresponder ao observado. Compilação/fixture/schema quebrados não são RED funcional. Não criar alteração defeituosa de propósito para fabricar RED.

**Organization**: preparação, fundação compartilhada, quatro histórias P1 da spec e fechamento. A fundação implementa a proteção comum exigida pela US3 antes de expor as leituras da US1/US2; a fase US3 completa e consolida sua prova. A lista é um primeiro incremento interno protegido; a Feature 007 só está concluída com as quatro histórias e o fechamento.

## Formato e convenções de caminhos

- Cada checkbox começa com `- [ ] Tnnn`; `[USn]` identifica a história e `[P]` somente trabalho em arquivos distintos após as dependências comuns.
- Caminhos de produção: `src/main/java/com/devsuperior/dscommerce/`; testes: `src/test/java/com/devsuperior/dscommerce/`. Caminhos completos aparecem em cada tarefa. Nomes novos são arquivos a criar na implementação, não arquivos já presentes.
- Uma tarefa só é concluída com seus critérios e evidências; marcar uma tarefa condicional exige registrar aplicação ou não aplicação justificada. Não executar commits automaticamente nos checkpoints.
- Constituição: [princípios e governança](../../.specify/memory/constitution.md). A 007 mantém Bean Validation puro antes do serviço de consulta. A Feature 005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**; não herdar nem reclassificar sua exceção.

## Protocolo de execução e evidência — obrigatório nas tarefas futuras

Executar Maven **sequencialmente**, na raiz, usando Java 21 e o Wrapper Maven 3.9.16. Não usar `clean`, comandos Maven concorrentes, mudanças de cache global/ordem/seed nem enfraquecimento de asserções. Depois de cada processo terminar, capturar seu exit code e só então preparar a execução seguinte.

Preservar console, comando/seleção, horários, ambiente, HEAD/diff e XML/TXT por execução em diretório inédito sob `target/feature007-validation/`. Antes de cada Maven, arquivar os relatórios recebidos de `target/surefire-reports/` por movimentação para destino inédito, sem sobrescrever: conferir previamente origem/destino absolutos resolvidos dentro de `target/`, sem sobreposição, e conferir hashes após a movimentação. Maven recria a área de relatórios. Se não houver relatórios, registrar isso. Assim, relatórios antigos permanecem preservados e não entram na contagem da execução nova. Nunca tomar upload, processo iniciado ou compilação como aprovação de testes.

Conferir nos XMLs métodos/invocações e classes realmente selecionados, inclusive aninhadas das suítes antigas. Não somar duas vezes casos agregados nem contêineres vazios. Nos novos testes HTTP, adotar classes concretas raiz e `Api` com cenários compartilhados; confirmar ambas no relatório, sem depender de descoberta de classe aninhada. Testes parametrizados contam por invocação executada; dispatches MockMvc e requisições HTTP são métricas distintas, não novos testes JUnit. Requisições de aceitação usam porta aleatória e cliente HTTP real. O login MockMvc da fixture existente, se usado no setup, deve ser identificado como dispatch interno; não substitui login/consultas HTTP dos cenários de jornada.

Um RED não autoriza implementar cenário ainda não escrito. Um GREEN anterior só pode ser reaproveitado no mesmo checkpoint se arquivos relevantes, configuração e seleção permanecerem idênticos; registrar essa correspondência. Repetir execução apenas por mudança pertinente, falha ou omissão efetiva de seleção. A suíte completa não é baseline inicial e será executada uma vez após as histórias; se nova correção exigir repetição, preservar ambas e explicar a necessidade.

### Seleções e checkpoints Maven

Os arquivos citados nos seletores novos serão criados pelas tarefas. Cada linha é uma execução; passar o argumento indicado ao Wrapper e acrescentar `test`, como no comando completo de B0. Todas as execuções obedecem ao protocolo acima.

| ID | Tarefa | Argumento ou comando | Finalidade |
| --- | --- | --- | --- |
| B0 | T002 | `.\mvnw.cmd '-Dtest=OrderSecurityIntegrationTest*,PublicCatalogNameFilterIntegrationTest*' test` | Baseline proporcional de POST protegido e catálogo público/filtro, sem suíte completa inicial. |
| F0-RED | T005 | `'-Dtest=OwnOrderQuerySecurity*#authenticationGate*'` | Novas restrições GET/HEAD antes da cadeia nova; observar 401/403 esperados ainda ausentes. |
| F0-GREEN | T008 | `'-Dtest=OwnOrderQuerySecurity*#authenticationGate*,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OrderSecurityIntegrationTest*,PublicCatalogNameFilterIntegrationTest*'` | Provar proteção comum e regressões diretamente afetadas pela alteração de segurança. |
| F1-RED | T010 | `'-Dtest=OwnOrderQueryList*'` | Lista e entrada HTTP novas antes de sua implementação. |
| F1-GREEN | T016 | `'-Dtest=OwnOrderQueryList*,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest#list*'` | Lista, P2 de binding, DTO, JPA e serviço em um único checkpoint. |
| F2-RED | T018 | `'-Dtest=OwnOrderQueryDetail*'` | Detalhe ainda ausente, antes de implementá-lo. |
| F2-GREEN | T022 | `'-Dtest=OwnOrderQueryDetail*,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest#detail*,OwnOrderQueryServiceTest#detail*'` | Detalhe/histórico; lista é consultada nos próprios cenários que exigem concordância. |
| F3 | T025/T027 | `'-Dtest=OwnOrderQueryList*,OwnOrderQueryDetail*,OwnOrderQuerySecurity*,OwnOrderQueryFailure*,OwnOrderQueryCurrentRolesIntegrationTest,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest'` | Checkpoint conjunto US1–US3 após completar titularidade/falhas, com os caminhos compartilhados. |
| FINAL | T030 | `.\mvnw.cmd test` | Suíte completa única, incluindo novos testes US4 e regressões 001–006. |

Métodos novos de fundação usam prefixo `authenticationGate`; serviços/repositórios usam `list` e `detail` para as seleções parciais. Não incluir testes US4 ainda inexistentes em seletores antecipados. B0 repetido em F0-GREEN tem propósito antes/depois da cadeia, não é uma segunda baseline. F3 não deve ser executado novamente em T027 quando T025 já aprovar o mesmo conteúdo; o FINAL incorpora US4 sem execução focal redundante imediatamente anterior.

## Fase 1 — Setup e baseline proporcional

**Objetivo**: registrar o ponto de partida e proteger evidências antes de mudar comportamento.

- [X] T001 Conferir raiz, branch real, HEAD/diff/status e seleção local007; ler os sete artefatos aprovados, `AGENTS.md`, `docs/harness.md` e `.specify/memory/constitution.md`; registrar contexto da implementação por acréscimo em `specs/007-consultar-meus-pedidos/quickstart.md`, distinguindo o SHA inicial real da base documental97ac035, sem modificar spec/checklist/plan/research/modelo/contrato ou o histórico da005. Dep.: nenhuma. Aceite: escopo, ambiente Java21/Wrapper e evidências históricas identificados sem alegação de validação nova.
- [X] T002 Executar B0 uma única vez antes da produção nova, após preservar relatórios recebidos; usar `src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, incluindo seus contextos/aninhadas; registrar seleção efetiva, resultado e limites em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T001. Aceite: baseline focal aprovada ou impedimento causal registrado; nenhuma suíte completa inicial, RED artificial ou reclassificação de CI histórica como execução local atual.

## Fase 2 — Fundação compartilhada: testes, identidade e proteção

**Objetivo**: suporte isolado e barreira de acesso antes das histórias de consulta. Bloqueia a implementação US1/US2.

- [X] T003 [P] Criar `src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryTestFixture.java`, reutilizando explicitamente `src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java` e a fixture administrativa existente; preparar contas CLIENT/ADMIN/ambos/nenhum, produtos e pedidos próprios/alheios com instantes/empates/posições e manifesto independente de valores esperados. Dep.: T002. Aceite: setup commitado, nenhuma construção com efeitos implícitos, limpeza só de itens/pedidos próprios antes de produtos/contas; URL H2 exclusiva e estável por ApplicationContext, create-drop e seeds globais preservados, sem limite comercial novo.
- [X] T004 [P] Criar `src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java` e `src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestObservationConfig.java`, reutilizando o probe/SqlObservation existentes e complementando contas completas/perfis por JDBC independente. Dep.: T002. Aceite: snapshots de produtos/categorias/associações/contas/perfis/pedidos/itens; falhas de comparação não imprimem hashes/senhas/JWT; observador só no contexto de teste, com janela sem setup/login/limpeza, sem alegar que readOnly/StatementInspector provam sozinhos ausência de escrita.
- [X] T005 Criar `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java`; escrever primeiro o subconjunto `authenticationGate*` e executar F0-RED. Dep.: T003–T004. Aceite: GET/HEAD de lista e segmento de detalhe, inclusive ID/página malformados, sem credencial/inválida/expirada/conta ausente =>401 e sem perfil=>403; com entrada válida e perfil permitido, aceitar provisoriamente404/405 por rota ausente ou200 após implementada, nunca401/403/500. O sucesso de negócio200 será exigido nas histórias de consulta e na US3. Contextos completos raiz/API com propriedades de identidade distintas, H2 por contexto, OSIV false, JWT/filtros/HTTP reais, sem transação externa ao HTTP. Registrar falha pela proteção nova ausente, não por compilação ou banco indisponível.
- [X] T006 Implementar `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessService.java` e `src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverter.java`, com testes em `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessServiceTest.java` e `src/test/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverterTest.java`. Dep.: T005 com RED funcional. Aceite: adaptar o conversor JWT rigoroso existente, recompor authorities por `findEffectiveAccountRolesById`, diferenciar conta ausente401/conta sem perfil403/falha interna500 e disponibilizar guarda readOnly ao serviço; não confiar em claims ou coleção de roles gerenciada, não alterar decoder/conversor global nem copiar parser JWT. Testes complementares devem compilar, sem registrar ausência de classe como RED.
- [X] T007 Acrescentar a cadeia exclusiva @Order(0) em `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java` e criar `src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAuthenticationEntryPoint.java` e `src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAccessDeniedHandler.java`. Dep.: T006. Aceite: somente GET/HEAD `/orders` e `/orders/*`, relativos ao servlet/context path, CLIENT ou ADMIN atual; envolver falha de resolução de modo distinguível e responder500 seguro;401 com Bearer challenge,403 fixo, HEAD sem corpo. Preservar as cadeias1/2/3, POST/orders, /admin, público e seus erros, sem sessão/operação de negócio nova.
- [X] T008 Executar F0-GREEN como checkpoint conjunto de segurança/componentes e regressões diretamente afetadas; registrar comando, casos concretos raiz/API, métodos aninhados antigos e resultados em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T006–T007. Aceite: proteção nova aprovada, B0 preservado após mudança, nenhum dado de compra exposto; não declarar a US3 inteira pronta enquanto não houver lista/detalhe e provas de titularidade.

**Checkpoint C1**: fundação de segurança/testes aprovada; candidato a commit lógico, somente se autorizado posteriormente.

## Fase 3 — US1: Encontrar minhas compras em uma lista paginada (P1)

**Objetivo**: lista própria com quatro campos, count próprio e entrada validada.

**Teste independente**: duas contas, cinco pedidos próprios e alheios intercalados; páginas2/2/1, empates resolvidos por ID, vazios coerentes e entrada inválida rejeitada. Segurança da fundação é pré-requisito, não item opcional do MVP.

- [X] T009 [US1] Escrever primeiro os cenários US1.1–US1.7 em `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListApiHttpIntegrationTest.java`, usando JSON/manifestos sem depender de DTOs produtivos ainda ausentes. Dep.: T008. Aceite: campos exatos id/createdAt/status/total, own count/pages, 2/2/1 e página posterior, desempate, contas sucessivas, defaults/limites e P2: `_page`, `_size`, `size[]`, `!page` separados/combinados/vazios/não inteiros/repetidos não mudam0/12 nem page1/size2 explícitos; page/size exatos inválidos/repetidos continuam400; accountId/buyerId/sort não ampliam consulta.
- [X] T010 [US1] Executar F1-RED para os testes escritos em `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java` e sua classe Api, registrando as causas em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T009. Aceite: falha observada de consulta/entrada ainda não implementada, sem erro de preparação; ausência da rota não é apresentada como reprodução específica do P2 de binder. Não alterar código para tornar vermelho teste já aprovado.
- [X] T011 [P] [US1] Criar `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderPageRequestDTO.java`, `src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderPageArgumentResolver.java` e `src/main/java/com/devsuperior/dscommerce/config/OwnOrderQueryWebMvcConfig.java`, com testes em `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryPageArgumentResolverTest.java`. Dep.: T010 com RED funcional. Aceite: selecionar nomes exatos/case-sensitive page/size do mapa já decodificado, preservar todas as ocorrências, rejeitar repetição antes de converter e descartar demais nomes antes de construção/binding/prefixos/[]; somente mapa filtrado chega ao binder, sem releitura @ModelAttribute da requisição original. DTO0/12 só por omissão, NotNull/Min/Max/AssertTrue puro, size1–50, offset long<=2147483647; conversão/Bean Validation antes do serviço, nenhum lookup, registro do resolver limitado à entrada da lista007.
- [X] T012 [P] [US1] Criar `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderSummaryDTO.java` e testes em `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`. Dep.: T010. Aceite: somente id/createdAt/status/total; UTC com milissegundos, status armazenado atual e dinheiro textual decimal19,2 exato, reutilizando verificação de formato existente no pacote DTO; PageResponseDTO permanece inalterado. Sem entidade, accountId ou dado sensível na saída.
- [X] T013 [P] [US1] Acrescentar página de IDs com countQuery próprio e fetch não paginado do lote em `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java`; criar `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java` com métodos `list*`. Dep.: T010. Aceite: mesmo buyer.id em conteúdo/count/lote, ordem createdAt DESC/id DESC, sem coleção no Pageable; left fetch só de items/product e sem categorias; testar H2 real, página2/2/1, alheios intercalados e raízes sem duplicação. Contexto de componentes com H2 próprio e fixture commitada; testes suplementares compilam após a interface existir, sem confundir compilação com RED.
- [X] T014 [US1] Implementar a lista em `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java` e testes `list*` em `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`. Dep.: T006, T012–T013. Aceite: guarda escalar atual antes de ler pedidos, owner exclusivamente do principal, DTOs calculados/mapeados dentro de readOnly, remontagem pela página de IDs e itens registrados; getSubtotal/getTotal BigDecimal, escala2 UNNECESSARY/toPlainString e capacidade vigente. Empty sem IN(), page/size/totais próprios sem clamp; totalPages calculado em long e convertido com faixa verificada; lote incompleto/financeiro incoerente =>500, sem parcial, arredondamento, save/flush/clear ou dependência de OSIV.
- [X] T015 [US1] Criar GET de lista em `src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java` e advice prioritário escopado em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java`. Dep.: T011, T014. Aceite: `@Valid OwnOrderPageRequestDTO` pelo resolver específico, sem binding automático original; métodos HTTP sem regras de negócio, serviço não chamado em400; segurança de infraestrutura pode resolver conta antes do MVC. Envelope exato e mensagens do contrato,401/403/400/500 seguros com path sem query; validação interna/de retorno não vira400; preservar status/headers nativos de método/negociação e comportamento das demais rotas. HEAD segue GET sem corpo.
- [X] T016 [US1] Executar F1-GREEN e conferir SQL/materialização e seleções em `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java`, testes complementares do seletor e `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T011–T015. Aceite: sete cenários e casos P2 aprovados por HTTP real em ambos contextos, Bean Validation provada na fronteira, own count e navegação exata; DTO acessível após retorno transacional com OSIV false, sem fetch paginado/N+1. Não impor número global rígido de SELECTs que impeça otimização legítima de count.

**Checkpoint C2 / incremento inicial**: lista protegida e seus testes aprovados. US2–US4 ainda obrigatórias para entrega da feature.

## Fase 4 — US2: Conferir itens e valores registrados da compra (P1)

**Objetivo**: detalhe próprio independente de página anterior, com nome atual e valores registrados.

**Teste independente**: pedido B/A, quantidades2/3, preços10.00/7.50, subtotais20.00/22.50 e total42.50; alterar catálogo e manter finanças, conferir valores pequenos/elevados e nomes iguais sem fundir itens.

- [ ] T017 [US2] Escrever primeiro US2.1–US2.7 em `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailApiHttpIntegrationTest.java`. Dep.: T016. Aceite: cabeçalho quatro campos mais items, item somente productId/productName/quantity/unitPrice/subtotal; posições B/A, detalhe fora da página, renome/preço posterior, nomes iguais, produto legado curto/longo/sem categorias/preço0, 0.01 e9007199254740993.01 e dois pedidos com preços históricos distintos; comparar lista/detalhe nos cenários aplicáveis. Testes compilam usando JSON e entidades existentes.
- [ ] T018 [US2] Executar F2-RED dos arquivos HTTP escritos em T017 e registrar a ausência funcional do detalhe em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T017. Aceite: falha pela nova consulta ainda ausente, sem tomar404 da rota ausente como prova da regra de ownership nem erro de compilação como RED. Não exigir falha dos cálculos BigDecimal já existentes.
- [ ] T019 [P] [US2] Criar `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderItemDTO.java` e `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java`; estender `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`. Dep.: T018. Aceite: campos exatos do contrato, coleções defensivas, dinheiro exato vigente e nome atual integral sem constraints administrativas de escrita; sem snapshot, accountId, posição/itemId internos, categorias ou preço atual na resposta.
- [ ] T020 [P] [US2] Acrescentar fetch de detalhe por id AND buyer.id em `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java` e testes `detail*` em `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java`. Dep.: T018. Aceite: left fetch não paginado de items/product, ordem position ASC existente, sem exists/find global de pedido alheio, sem categorias ou duplicações; pedido ausente/alheio retorna ausência restrita ao titular e raiz incoerente não desaparece por inner join.
- [ ] T021 [US2] Estender `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java`, `src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java` para GET/{id}; adicionar testes `detail*` em `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`. Dep.: T019–T020. Aceite: ID Long positivo na fronteira, guarda atual e owner obrigatório, ausente/alheio404 indistinto, cálculo compartilhado com lista a partir de quantity/unitPrice registrados, nome atual exato, position ASC sem fusão; DTO completo dentro de readOnly e nenhuma validação retroativa/correção do produto. Estado somente armazenado; corrupção/falha é500 seguro. Não modificar entidades/enum/POST ou adicionar Location ao contrato004.
- [ ] T022 [US2] Executar F2-GREEN nos arquivos HTTP da US2 e complementares do seletor; registrar resultados em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T021. Aceite: sete cenários aprovados nos contextos raiz/API, inclusive campos exatos, histórico financeiro, concordância lista/detalhe e materialização sem OSIV; identificar limites de consistência do plano, sem prometer snapshot imutável entre requisições.

**Checkpoint C3**: lista e detalhe funcionais/protegidos com histórico preservado; candidato a commit lógico após autorização, antes da consolidação de segurança/regressões.

## Fase 5 — US3: Proteger titularidade e informações restritas (P1)

**Objetivo**: completar a prova de identidade/perfis atuais, ownership e erros nas duas operações. Fundação e fases anteriores já devem aplicar essas regras; não deixá-las para serem implementadas só agora.

**Teste independente**: CLIENT, ADMIN isolado, ambos e nenhum; mesmo pedido consultado por titular/terceiro/ADMIN alheio; credenciais inválidas e falhas internas, com conteúdo/count/erros sem vazamento.

- [ ] T023 [P] [US3] Completar US3.1–US3.6 nos arquivos `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java`, reutilizando `authenticationGate*` sem duplicar casos; criar `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java`. Dep.: T022. Aceite: sucesso próprio para três combinações permitidas, conteúdo/count sempre próprios, alheio/inexistente mesmo404 inclusive ADMIN, precedência401/403 sobre entrada inválida, headers/HEAD/path e ausência de dados sensíveis. Alterar perfis no banco após JWT e testar concessão/revogação; provar projeção escalar com coleção gerenciada obsoleta em teste JPA distinto, sem simular HTTP dentro de transação externa nem confiar em claims. Não prometer serialização de revogação após a leitura autorizadora.
- [ ] T024 [P] [US3] Criar `src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryFailureInjectionTestConfig.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureApiHttpIntegrationTest.java` para US3.7 e erros de contrato. Dep.: T022. Aceite: injeções escopadas ao teste em lookup inicial/escalar, count, fetch, cálculo/mapeamento e conclusão transacional; assegurar fixture com count realmente executado quando necessário. HTTP real=>500 fixo, nunca401/404/vazio/parcial; nenhum SQL/stack/JWT/hash no erro. Distinguir conta removida na guarda tardia401, falta de perfil403 e entrada inválida400; método/negociação preservam semântica. Contextos de falha com H2 próprios, sem configuração global ou teste só de mock substituir HTTP.
- [ ] T025 [US3] Executar F3 uma vez após escrever T023–T024, antes de eventual correção adicional; registrar casos/resultados em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T023–T024. Aceite: checkpoint conjunto de lista/detalhe/segurança/falhas e componentes, com seleção concreta raiz/API e métodos parciais reconciliados. Testes que já passarem são evidência de conformidade existente; não exigir RED artificial nem alterar produção sem desvio observado.
- [ ] T026 [US3] Corrigir somente desvios da007 demonstrados por T025 nos caminhos pertinentes `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessService.java`, `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java`, `src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverter.java`, handlers de leitura ou `src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java`; registrar causa/escopo em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T025. Condição: se não houver desvio, registrar não aplicação justificada, sem editar produção. Aceite: filtros de titular/perfis e erros cumprem contrato, sem ampliar políticas ou tocar domínio/fluxos históricos para contornar falha de teste.
- [ ] T027 [US3] Consolidar o checkpoint F3 em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T026. Aceite: se T025 já aprovou a mesma revisão/configuração/seleção, reutilizar evidência sem novo Maven; após correção pertinente, executar F3-GREEN sequencial e preservar a execução anterior. Não fechar com erro/falha/omissão de contexto; identificar mensagens/status/headers reais e limites das injeções.

## Fase 6 — US4: Consultar sem modificar compras ou jornadas existentes (P1)

**Objetivo**: provar imutabilidade e compatibilidade 001–006; leitura continua distinta de novo registro.

**Teste independente**: snapshots e observação da janela de GET/HEAD; repetir leitura e depois POST válido com mesmos itens gera outro ID; jornadas anteriores mantêm contratos e rejeições.

- [ ] T028 [US4] Criar `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionApiHttpIntegrationTest.java` com US4.1 e US4.4. Dep.: T027. Aceite: HTTP real de sucesso/vazio/rejeição não modifica nenhuma tabela observada, titular/instante/status/posições/quantidades/preços intactos e zero DML Hibernate na janela, complementado por snapshot JDBC independente; repetir GET não cria pedido e POST válido posterior com mesmos itens cria novo ID conforme004, sem carrinho/idempotência. Asserções não expõem contas/hashes em falha. Não fabricar RED de imutabilidade ou registro já corretos.
- [ ] T029 [US4] Reconciliar US4.2/US4.3/US4.5 com as asserções existentes de `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/LoginControllerTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OrderRegistrationIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/AdminProductHistoryIntegrationTest.java`; acrescentar somente lacunas de interação pós-consulta aos novos testes de regressão de T028. Dep.: T028. Aceite: público com Bearer inválido irrelevante, filtro literal/case/acentos/%/_/espaços, JSON numérico/categorias/erros, login exato/confidencial, POST estrito/atômico e manutenção/FK/histórico cobertos sem copiar suítes antigas, enfraquecer asserções ou exigir RED anterior de comportamento entregue.
- [ ] T030 [US4] Executar FINAL uma vez após T029, usando a suíte de `src/test/java/com/devsuperior/dscommerce/` e registrar console/exit code/relatórios novos em `specs/007-consultar-meus-pedidos/quickstart.md`. Dep.: T029. Aceite: suíte completa inclui todas as invocações007 e regressões001–006, sem erros/falhas/ignorados não justificados; confirmar cenários/classes/contextos e preservação das asserções/correções em `src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java`. Não executar focal US4 redundante imediatamente antes da completa; registrar JUnit/dispatches/HTTP separadamente.
- [ ] T031 [US4] Analisar o resultado de T030 e fechar a evidência em `specs/007-consultar-meus-pedidos/quickstart.md`, com correções limitadas aos arquivos007 de `src/main/java/com/devsuperior/dscommerce/` ou `src/test/java/com/devsuperior/dscommerce/` quando uma causa concreta exigir. Dep.: T030. Condição: GREEN sem alteração reutiliza T030; falha exige diagnóstico, preservação dos relatórios e, se corrigida, focal afetado e nova completa somente quando a mudança/falha justificar, sempre sequenciais. Aceite: nenhuma regressão mascarada por cache/ordem/H2 compartilhado, whitelist de asserções reduzida ou mudança de domínio; impedimento externo/fora do escopo fica registrado e não conta como aprovação.

## Fase 7 — Fechamento e questões transversais

**Objetivo**: revisão final e rastreabilidade, sem nova operação, cosmética ou ampliação de escopo.

- [ ] T032 Revisar conformidade efetiva de `src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java`, resolver/serviço/repositório/segurança007 contra `specs/007-consultar-meus-pedidos/spec.md`, `specs/007-consultar-meus-pedidos/plan.md` e `specs/007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml`. Dep.: T031 aprovado. Aceite: Bean Validation puro termina antes do serviço; owner obrigatório em conteúdo/count/detalhe; P2 filtrado antes de transformar nomes; dinheiro histórico/nome atual/posição/estado corretos; nenhuma entidade/DTO antigo/OSIV global/POST alterado indevidamente, nem domínio, snapshot, transição, carrinho ou consulta administrativa alheia novos. Gates da007 são avaliados por evidência;005 segue EXCEÇÃO DOCUMENTADA sem PASS estrito.
- [ ] T033 Acrescentar a `specs/007-consultar-meus-pedidos/quickstart.md` a matriz final cenário=>classe/método/invocação/contexto=>comando/revisão/resultado/relatório, reconciliando as26 linhas abaixo, FR-001–018 e SC-001–006. Dep.: T032. Aceite: histórico preservado como prefixo, novos resultados locais separados de remotos/documentais; nenhuma contagem inferida só do seletor ou do total do console. Contrato inalterado reutiliza validação estrutural histórica; mudança contratual exige revisão/autorização e validação pertinente, sem alterar fonte aprovada silenciosamente. Ausência de CI não é aprovação; se houver CI em fase autorizada posterior, conferir URL/evento/SHA e relação HEAD/base, sem disparar execução nesta tarefa.
- [ ] T034 Fazer revisão final do diff/arquivos novos e estado do Git em `specs/007-consultar-meus-pedidos/tasks.md` e `specs/007-consultar-meus-pedidos/quickstart.md`, marcando somente tarefas com critérios comprovados e relatando pendências/limites. Dep.: T033. Aceite: caminhos/links/whitespace e escopo revisados, main e seleção007 preservadas, nenhum artefato aprovado reescrito; commits abaixo apenas propostos até autorização própria. Não concluir a feature com testes falhos ou lacuna material não resolvida; não fazer push, merge ou implantação como efeito do fechamento.

## Rastreabilidade dos 26 cenários

Referências USn.m correspondem às histórias/cenários numerados da spec; casos técnicos extras de binder/HEAD/falhas não alteram essa contagem. A execução FINAL é requisito transversal; cada linha indica a tarefa que prepara a prova e seu checkpoint focal quando existente.

| Cenário | Prova principal planejada | Tarefas / checkpoint | SC |
| --- | --- | --- | --- |
| US1.1 | Três próprios, dois alheios; campos e total próprio | T009, T013–T016 / F1 | 001–002 |
| US1.2 | Cinco próprios intercalados, páginas2/2/1 sem perda | T009, T013–T016 / F1 | 001–002 |
| US1.3 | Empate createdAt e id DESC entre páginas | T009, T013–T016 / F1 | 002 |
| US1.4 | Sem próprios, inclusive página posterior, totais0/0 | T009, T014–T016 / F1 | 001,005 |
| US1.5 | Página3 de cinco/size2 vazia com totais5/3 | T009, T014–T016 / F1 | 005 |
| US1.6 | Paginação exata inválida400, sem serviço/dados | T009–T011, T015–T016 / F1 | 005 |
| US1.7 | Identidades sucessivas não compartilham conteúdo/count | T009, T014–T016 / F1 | 001 |
| US2.1 | B/A e20.00+22.50=42.50, campos e estado | T017, T019–T022 / F2 | 003–004 |
| US2.2 | Detalhe próprio fora da página anterior | T017, T020–T022 / F2 | 003 |
| US2.3 | Renome/preço atual12.00, compra continua20.00 | T017, T019–T022 / F2 | 003–004 |
| US2.4 | Nomes atuais iguais, IDs e posições distintos | T017, T020–T022 / F2 | 003 |
| US2.5 | Produto legado/preço0/sem categorias legível | T017, T019–T022 / F2 | 003–004 |
| US2.6 | 0.01 e9007199254740993.01 textuais exatos | T017, T019–T022 / F2 | 004 |
| US2.7 | Dois pedidos com mesmo produto e preços próprios | T017, T020–T022 / F2 | 004 |
| US3.1 | CLIENT, ADMIN isolado e ambos acessam próprios | T005–T008, T023–T027 / F0 e F3 | 001 |
| US3.2 | Credencial ausente/inválida/expirada/conta ausente401 | T005–T008, T023–T027 / F0 e F3 | 001,005 |
| US3.3 | Conta sem perfil permitido403 | T005–T008, T023–T027 / F0 e F3 | 001 |
| US3.4 | Alheio/inexistente mesmo404 sem dados | T023–T027 / F3 | 001,005 |
| US3.5 | ADMIN alheio404 e count próprio | T023–T027 / F3 | 001 |
| US3.6 | Auth/perfil precedem entrada inválida | T005–T008, T023–T027 / F0 e F3 | 001 |
| US3.7 | Lookup/count/fetch/processamento falhos500 seguro | T024–T027 / F3 | 001,005 |
| US4.1 | Snapshots completos e zero DML em leituras | T004, T028, T030–T031 / FINAL | 006 |
| US4.2 | Público/catálogo/detalhe/filtro e JSON preservados | T029–T031 / FINAL | 006 |
| US4.3 | Login e confidencialidade preservados | T029–T031 / FINAL | 006 |
| US4.4 | Ler não cria; POST posterior cria outro pedido | T028, T030–T031 / FINAL | 006 |
| US4.5 | Registro/admin mantêm auth/atomicidade/FK/histórico | T029–T031 / FINAL | 006 |

FR-001–002: fundação, T014/T021 e US3; FR-003/009–013: US1; FR-004–008/018: US2 e cálculos compartilhados da lista; FR-014–015: advice/guarda e US3; FR-016–017: US4 e revisão transversal. P2 tem execução HTTP futura em T009/T016 e confirmação conjunta F3/FINAL; a análise estática original e a validade estrutural OpenAPI não substituem essa prova.

## Dependências e ordem de execução

```text
T001 → T002 → (T003 || T004) → T005 → T006 → T007 → T008
  → T009 → T010 → (T011 || T012 || T013) → T014 → T015 → T016
  → T017 → T018 → (T019 || T020) → T021 → T022
  → (T023 || T024) → T025 → T026 condicional → T027
  → T028 → T029 → T030 → T031 → T032 → T033 → T034
```

T014 depende de T012/T013 e T006; T015 também depende de T011. As histórias compartilham `OrderRepository`, serviço, controller e advice: não editar US1/US2 simultaneamente nesses arquivos. US3 reutiliza segurança implementada na fundação, não a posterga. US4 depende de ambas as consultas e da consolidação F3. Correção condicional não cria ciclo; volta-se apenas ao checkpoint afetado, registrando novas evidências quando necessário.

### Oportunidades e exemplos de trabalho paralelo

| Fase/história | Exemplo de trabalho simultâneo permitido | Restrição |
| --- | --- | --- |
| Fundação | T003 fixture e T004 probe/observação depois de T002 | Arquivos distintos; integração em T005. |
| US1 | T011 entrada/resolver, T012 DTO e T013 JPA depois de T010 | T014 espera DTO/JPA; T015 espera resolver/serviço. |
| US2 | T019 DTOs/contrato e T020 detalhe JPA depois de T018 | T021 integra ambos; não alterar serviço/controller concorrente. |
| US3 | T023 perfis/ownership e T024 falhas em classes distintas depois de T022 | F3 somente quando ambos terminarem; alterações compartilhadas coordenadas. |
| US4 | Revisão de fontes antigas de T029 pode ser preparada enquanto T028 escreve testes novos | Sem marca[P] porque T029 só integra/conclui após T028; nenhum Maven paralelo ou dois editores nos mesmos testes. |

`[P]` permite escrita/revisão independente; não autoriza execução paralela do Maven nem exige delegação. Testes são executados nos checkpoints comuns, não em processos concorrentes por tarefa.

## Estratégia incremental e checkpoints de commit

1. Preparar baseline e fundação; C1 após F0-GREEN.
2. Entregar US1 protegida como incremento inicial; C2 após F1-GREEN. Não publicar uma lista sem segurança nem declarar toda a feature pronta.
3. Entregar US2 com histórico e detalhe; C3 após F2-GREEN.
4. Completar US3/US4, executar F3 e FINAL conforme suas condições, revisar gates/rastreabilidade e concluir somente após T034. Quatro histórias são P1 e obrigatórias.

| Checkpoint lógico | Conteúdo revisável | Mensagem candidata, sem execução automática |
| --- | --- | --- |
| C1 — T008 | Fixtures/observação e proteção comum comprovada | `feat: protect own order query routes` |
| C2 — T016 | Lista, paginação própria, binding exato e provas | `feat: list own orders` |
| C3 — T022 | Detalhe, nome atual e histórico financeiro | `feat: read own order details` |
| C4 — T034 | Segurança/falhas complementares, imutabilidade, regressões e evidências finais | `test: verify own order query isolation and regressions` |

Checkpoint não faz stage/commit/push. A inclusão de arquivos e a mensagem de um futuro commit devem refletir o diff final e depender de autorização própria; não pressupor que a geração de tasks a concedeu.

## Limites desta geração

Somente este tasks.md é gerado. Spec/checklist e cinco artefatos de planejamento aprovados permanecem intactos. Revisão do formato/cobertura/dependências é documental e manual, sem testes ou validadores executados. Comandos, cenários, gates de implementação e checkpoints acima são futuros; não há contagem nova de testes, aprovação HTTP/JPA, CI, commit ou entrega funcional. Não há clarificação de negócio bloqueante identificada; seleção real de classes/contextos e resultados precisarão ser comprovados durante a execução das tarefas.
