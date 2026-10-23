---
description: "Tarefas de implementação e comprovação da Feature 008 — Registrar pagamento"
---

# Tasks: Registrar pagamento

**Input**: [spec.md](spec.md), [checklist aprovado](checklists/requirements.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contratos](contracts/README.md) e [quickstart.md](quickstart.md).

**Encerramento local — T036 (2026-10-23)**: T001–T036 concluídas, **36/36**, conforme [revisão final e evidências](quickstart.md#t036--revisão-final-e-encerramento-local-2026-10-23). Implementação e validação local Windows/Java21/H2; validação remota, PostgreSQL e integração em main continuam pendentes. Os textos de estado/contagem/comandos futuros da geração abaixo são históricos daquela fase; o estado atual é dado pelos marcadores e registros identificados no quickstart. Nenhuma evidência histórica é reclassificada por este fechamento; Gate IV005 permanece EXCEÇÃO DOCUMENTADA.

**Base documental**: `db5b790db0a7cd2338d964ae4f322eda93243937`, branch `008-registrar-pagamento`, seleção local008. A [constituição](../../.specify/memory/constitution.md), [AGENTS.md](../../AGENTS.md) e o [harness](../../docs/harness.md) governam a execução futura.

**Estado**: somente este arquivo foi gerado. Todas as tarefas estão abertas; comandos, implementação, testes, checkpoints e publicações abaixo são futuros. O PASS de desenho não comprova segurança, persistência ou comportamento funcional. As restrições de não implementar das fases históricas de spec/plan não autorizam implementação nesta geração de tarefas.

**Tests**: solicitados explicitamente. Escrever testes antes da alteração do comportamento correspondente e observar RED funcional onde ele estiver ausente. Não provocar RED artificial em comportamento existente ou já atendido pelo núcleo; registrar o GREEN observado e a evidência específica. Erro de compilação, fixture, isolamento ou instrumentação não comprova requisito ausente. Uma rota ausente comprova ausência da operação, não reproduz isoladamente cada regra de tempo, autorização ou concorrência.

**Organization**: oito fases, cinco histórias P1 na ordem da spec. São incrementos verificáveis, com dependências reais sobre a mesma operação. US1 já inclui as guardas mínimas de segurança, elegibilidade, unicidade e transação; US2–US4 aprofundam sua comprovação e corrigem somente lacunas demonstradas. A entrega exige as cinco histórias e a fase final.

## Format: `[ID] [P?] [Story] Description`

- `- [ ] Tnnn`: tarefa ainda não executada; `[USn]` identifica a história. `[P]` permite autoria em arquivos distintos depois dos pré-requisitos, não Maven simultâneo nem alterações concorrentes no mesmo arquivo.
- Caminhos são relativos à raiz e aparecem explicitamente nas tarefas. Classes novas são alvos futuros, não arquivos já existentes.
- Ao executar futuramente, acrescentar evidências ao quickstart em seção nova, preservando o registro documental/histórico como prefixo. Não reescrever spec, decisões aprovadas ou contratos históricos para acomodar falhas.

## Path Conventions e disciplina de execução

Projeto Maven único, Java21, Boot3.3.4 e Wrapper3.9.16 existentes; nenhuma dependência nova de aplicação prevista. Não alterar Jackson/OSIV/perfil/seed globais, nem usar PostgreSQL presumido ou migrações não planejadas.

Uma invocação Maven por vez. Antes da seguinte, copiar console e `target/surefire-reports/` para um diretório exclusivo sob `target/feature008-evidence/<etapa>-<execucao>/`, ignorado pelo Git; registrar comando, horário, HEAD/diff, ambiente, exit code e casos efetivamente executados. Não aceitar seleção vazia, falha escondida, teste desabilitado ou contagem de compilação como GREEN. Seletores abaixo são procedimentos futuros; confirmar classes, métodos herdados, parâmetros e contextos nos relatórios, sem inferir cobertura apenas do nome do filtro.

Testes unitários/MockMvc verificam regras locais e caminhos controlados; HTTP real em porta aleatória verifica filtro, binding, proxy e serialização em ROOT e `/api`. JDBC independente e SQL observado verificam estado commitado e locks no H2 configurado. `StatementInspector` não observa JDBC direto; snapshots iguais não provam ausência de toda mutação transitória. Nunca registrar essas provas como equivalentes ou como prova de outro banco, crash recovery ou rede de produção.

## Phase 1: Setup — conferência e baseline proporcional

**Objetivo**: fixar revisão, fontes e uma referência executável anterior à mudança, sem reinicializar o projeto.

- [X] T001 Conferir raiz/branch/HEAD/status real, seleção008 e ancestralidade da base documental; ler `AGENTS.md`, `.specify/memory/constitution.md` e todos os arquivos aprovados em `specs/008-registrar-pagamento/`; registrar em seção futura de `specs/008-registrar-pagamento/quickstart.md` o HEAD efetivo, alterações preservadas, Java/Wrapper e contrato vigente. Dep.: autorização futura para execução. Aceite: reconhecer que hoje007 só admite WAITING_PAYMENT e que os dois contratos008 são alvos, sem trocar branch ou incluir trabalho alheio.
- [X] T002 Estabelecer BASE para `src/test/java/com/devsuperior/dscommerce/entities/OrderAggregateTest.java`, `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/EffectiveAccountRolesRepositoryTest.java` e classes HTTP existentes de lista/detalhe007 ROOT e Api; registrar relatórios em `specs/008-registrar-pagamento/quickstart.md`. Dep.: T001. Aceite: usar a seleção BASE abaixo se não houver execução confiável para a mesma base de código; separar evidência anterior de execução nova, diagnosticar falhas preexistentes sem alterar asserções/ambiente global e sem exigir RED de regressão existente. A suíte completa fica para FINAL.

**Checkpoint**: baseline identificada; qualquer impedimento tem comando e causa, não uma aprovação presumida.

## Phase 2: Foundational — suporte compartilhado de testes

**Objetivo**: preparar observação e fixtures compiláveis sem depender de classes de pagamento ainda inexistentes. Bloqueia o trabalho funcional das histórias.

- [X] T003 [P] Criar `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java`. Dep.: T002. Aceite: URL H2 exclusiva e estável por contexto008, imports explícitos de teste, porta aleatória ROOT/Api, login/JWT reais e Clock controlável; preparar asserções de READ_COMMITTED nas conexões relevantes, ausência de transação externa ao request e proxy real, para exercê-las quando o serviço008 existir. Não alegar essa prova na fundação. Não modificar `src/test/resources/application-test.properties`, o seed global ou os contextos históricos para ocultar interferência de create-drop; DB_CLOSE_DELAY não protege contra DROP de outro contexto.
- [X] T004 [P] Criar `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java` e `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java`, compondo o probe007 existente. Dep.: T002. Aceite: contas/perfis e pedidos commitados antes das chamadas, rastreamento dos IDs próprios e limpeza em finally/AfterEach/AfterAll na ordem Payment→itens→pedidos→demais linhas próprias; nenhuma limpeza global. Snapshot inclui as sete tabelas anteriores e tb_payment, sem expor credenciais. Antes de o schema008 existir, apenas setup/cleanup pode verificar sua presença por metadata; a prova funcional deve falhar explicitamente se faltar tb_payment, sem convertê-la em lista vazia ou omitir sua observação. Assertar o resultado HTTP esperado antes de consultar uma tabela ainda ausente no RED inicial.
- [X] T005 Preparar `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java` com observação scoped de SQL, entrada/retorno do proxy, referência de negócio e coordenação por latches/barreiras. Dep.: T003–T004. Aceite: transações/conexões independentes, timeouts finitos, liberação de locks/threads/conexões em finally e Clock contado somente no serviço, distinguindo chamadas do JWT. Sem sleeps arbitrários, bean falso de produção, mutex global ou instrumentação vazando para outros contextos; ausência inicial do serviço não pode virar sucesso de teste.

**Checkpoint**: suporte pronto para escrever/executar os primeiros testes. Sensibilidade de tb_payment e comportamento do schema real serão comprovados em T007/T013; a mera existência do suporte não satisfaz essas provas.

## Phase 3: User Story 1 — Registrar quitação integral com ocorrência (P1)

**Goal**: entregar uma operação administrativa protegida, com ocorrência conservada, confirmação mínima e histórico intacto.

**Independent Test**: pedidos próprios/alheios, ADMIN isolado/ambos, total42,50 e compra histórica20,00 com catálogo12,00; limites inclusivos e confirmação201 somente após commit. Seis cenários US1.1–6.

### Tests first

O primeiro RED de HTTP/metadata não deve preparar um snapshot de oito tabelas que ainda exige tb_payment. Após T008 disponibilizar o schema, completar as fotografias antes/depois e os testes específicos antes dos respectivos corpos de repository/serviço/endpoint. Essa progressão mantém o RED compilável e não transforma falha de preparação em prova das demais regras.

- [X] T006 [P] [US1] Escrever primeiro `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationApiHttpIntegrationTest.java` para os seis cenários US1 e para a fronteira mínima segura do contrato POST008. Dep.: T005. Aceite: requests JSON/HTTP compiláveis sem importar tipos de produção inexistentes; 201 com exatamente orderId/occurredAt/status, sem Location, valor ou comprador; iguais regras para titular próprio/alheio e ADMIN sem CLIENT. Incluir rejeições contratuais básicas de autenticação, corpo extra, janela temporal e nova tentativa antes de implementar suas guardas. Executar S1-RED e registrar a causa funcional; depois da rota existir, cada rejeição precisa de asserção própria, não do antigo404 de roteamento.
- [X] T007 [P] [US1] Escrever testes do modelo real em `src/test/java/com/devsuperior/dscommerce/repositories/PaymentPersistenceIntegrationTest.java` e de sensibilidade em `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationDatabaseProbeTest.java`. Dep.: T005. Aceite: começar por metadata/JDBC e asserção explícita de schema ausente, sem erro de preparação; antes dos corpos respectivos, acrescentar testes específicos às APIs Java quando estiverem compiláveis. Exigir PK/FK/NOT NULL, ausência de ID independente/amount, tb_order com colunas preservadas, enum ampliado e round-trip temporal exato; duplicidade, órfão e ocorrência nula devem falhar no banco. Provar que o probe detecta alteração controlada de Payment e conserva observação das sete tabelas anteriores; provar limpeza seletiva também após teste falho, sem alterar probes históricos. A criação artificial de schema de teste não aprova o mapeamento de produção.

### Implementation

- [X] T008 [US1] Modelar `src/main/java/com/devsuperior/dscommerce/entities/Payment.java`, acrescentar PAID em `src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java` e transição restrita em `src/main/java/com/devsuperior/dscommerce/entities/Order.java`. Dep.: T006–T007 com RED pertinente. Aceite: Payment com @MapsId, order_id PK/FK nomeada fk_payment_order e occurred_at NOT NULL TIMESTAMP(3) WITH TIME ZONE/@FractionalSeconds(3), relação obrigatória LAZY sem REMOVE/ALL; criação ainda WAITING_PAYMENT. Não acrescentar version, associação inversa, montante ou outros estados; markPaid não substitui comprador/criação/itens.
- [X] T009 [US1] Criar `src/main/java/com/devsuperior/dscommerce/repositories/PaymentRepository.java`, `src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepository.java`, `src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepositoryImpl.java`, `src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepository.java` e `src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryImpl.java`; integrar fragmento/projeção em `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java`. Dep.: T008 e testes prévios pertinentes de T007. Aceite: find PESSIMISTIC_WRITE seguido de refresh com lock antes de usar estado/criação, somente raiz por ID; procurar pagamento e projetar productId/position/quantity/unitPrice depois do lock, sem Product, JOIN FETCH de coleção, DISTINCT ou filtro de estado/titular na aquisição. Inserção por persist explícito, nunca merge/save que possa atualizar quitação anterior; manter consultas próprias existentes.
- [X] T010 [US1] Criar `src/main/java/com/devsuperior/dscommerce/dto/PaymentRegistrationRequestDTO.java`, `src/main/java/com/devsuperior/dscommerce/dto/PaymentConfirmationDTO.java` e reader exclusivo em `src/main/java/com/devsuperior/dscommerce/config/PaymentRequestJsonConfig.java`, com testes prévios em `src/test/java/com/devsuperior/dscommerce/dto/PaymentRegistrationDtoContractTest.java`. Dep.: T009 e testes prévios T006–T007. Aceite: @NotNull Instant/Bean Validation na fronteira, request fechado somente occurredAt e confirmação somente três campos. Gramática R3 completa: offset explícito até±18:00, ano local/UTC0001–9999, segundos e T/Z maiúsculos, fração até9 apenas se exata em ms; aceitar zeros adicionais, rejeitar subms sem arredondar, -00:00, leap second, hora24, datas impossíveis, espaços, tipos/coerções/duplicatas/trailing JSON. Saída UTC.SSS preserva Instant; sem mudar mapper global, serviço ou IO no reader/validator.
- [X] T011 [US1] Implementar `src/main/java/com/devsuperior/dscommerce/services/PaymentAccessService.java`, `src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java` e `src/main/java/com/devsuperior/dscommerce/services/exceptions/PaymentRegistrationException.java`, precedidos de testes de regras em `src/test/java/com/devsuperior/dscommerce/services/PaymentRegistrationServiceTest.java` assim que suas APIs mínimas compilarem. Dep.: T009–T010. Aceite: primeira instrução de negócio captura Clock.instant nativo uma única vez; guarda escalar atual antes de ler pedido e após espera, antes de mutar; conta ausente401/sem ADMIN403/lookup falho500. Sob lock, pagamento existente409 precede estado incompatível409, ausente404, janela inválida400. Calcular histórico exato BigDecimal/UNNECESSARY/decimal19,2, tratar corrupção500 e não ler preço atual. REQUIRED/READ_COMMITTED/rollbackFor Exception insere Payment e PAID, flush e DTO na mesma transação; falhas propagam sem retry/consulta em transação inválida. Não serializar revogação depois da última leitura nem prometer commit se outro chamador criar transação externa.
- [X] T012 [US1] Expor `src/main/java/com/devsuperior/dscommerce/controllers/AdminPaymentController.java` junto da cadeia específica em `src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java`, handlers `src/main/java/com/devsuperior/dscommerce/security/PaymentAuthenticationEntryPoint.java` e `src/main/java/com/devsuperior/dscommerce/security/PaymentAccessDeniedHandler.java` e advice `src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java`; ajustar somente o fallback necessário em `src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java`. Dep.: T010–T011 e testes T006. Aceite: @Order(-1) no caminho /admin/orders/*/payments em todos os métodos, decoder/conversor existentes com erros adaptados, ADMIN atual antes de MVC, sem CLIENT adicional; Long @Positive, @Valid e nenhuma query. Controller sem transação externa constrói201 após retorno do proxy dono do commit; erros seguros exatamente como contrato, validação de retorno500, 405/406/415 nativos com headers. Não expor a operação sem a proteção, ampliar /admin/** ou alterar a guarda007.
- [X] T013 [US1] Executar S1-GREEN dos testes de `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java`, sua classe Api, DTO, serviço, persistência e probe; registrar em `specs/008-registrar-pagamento/quickstart.md` SQL/schema, confirmação e snapshots por conexão independente. Dep.: T008–T012. Aceite: seis cenáriosUS1 e guardas compartilhadas exercitados; Payment único, PAID e histórico preservado, precisão comprovada no H2, proxy físico confirmado e limpeza sem resíduos. Nenhuma anotação isolada ou DTO construído internamente conta como commit.

**Checkpoint C1**: primeiro incremento interno validado. Não declarar a008 entregue ou publicar o novo contrato como vigente sem US2–US5 e FINAL.

## Phase 4: User Story 2 — Impedir registros não autorizados ou inválidos (P1)

**Goal**: comprovar e completar rejeições seguras e permissões atuais, inclusive após esperas.

**Independent Test**: nove cenários US2.1–9; cada causa isolada, sem alteração causada pela tentativa e sem dados restritos. Os testes abaixo precedem qualquer extensão/correção das regras correspondentes; GREEN já obtido no núcleo não exige regressão artificial.

### Tests first

- [X] T014 [P] [US2] Acrescentar matriz completa em `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationApiHttpIntegrationTest.java`. Dep.: T013. Aceite: token ausente/inválido/expirado/conta removida401 com Bearer, CLIENT/sem perfil403, ADMIN isolado/ambos aceitos em compra própria/alheia e claims antigos/forjados sem autoridade. Mudar perfis após emissão e entre filtro/serviço; lookup falho500, sem convertê-lo em401. Entradas inválidas também passam primeiro pelo filtro; observar ausência de leitura restrita/DML em negativas precoces e snapshots completos nas demais.
- [X] T015 [P] [US2] Escrever casos HTTP em `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputApiHttpIntegrationTest.java`, complementados por MockMvc/validação em `src/test/java/com/devsuperior/dscommerce/controllers/PaymentBoundaryValidationTest.java`. Dep.: T013. Aceite: ausência/null/tipo e formato de ocorrência, ID inválido/faixa, query, JSON extra/duplicado/trailing, tentativa de amount/buyer/createdAt/items/status e toda gramática R3; 400 seguro antes do serviço para validação local, 404 para pedido ausente, 409 para PAID sem Payment, sem reparar. Entrada temporal inválida e retorno interno inválido não podem compartilhar indevidamente400; verificar @Valid/@Positive e ausência de IO no reader/validator. Omissão de um segmento que não identifica a operação deve ser distinguida de entrada inválida em uma rota efetivamente selecionada, sem aprovar US2.5 apenas por404 de roteamento.
- [X] T016 [P] [US2] Criar `src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java` usando o suporte008. Dep.: T013. Aceite: segurar raiz em transação auxiliar, observar referência capturada antes da espera, avançar Clock e comprovar rejeição de ocorrência posterior à referência original e aceitação de igualdade representável. Comparar nanos nativos sem truncar, inclusive referência subms; incluir limite inferior e instantes imediatamente fora da janela. Durante espera, revogar ADMIN em outra transação e exigir releitura escalar/rejeição sem escrita. Pré-carregar roles antigas para demonstrar bypass do cache; separar a alteração externa controlada dos efeitos da tentativa no baseline observado. Esperas limitadas e liberação garantida.

### Implementation e checkpoint

- [X] T017 [US2] Confrontar esses testes com `src/main/java/com/devsuperior/dscommerce/services/PaymentAccessService.java`, `src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java`, `src/main/java/com/devsuperior/dscommerce/config/PaymentRequestJsonConfig.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java`; corrigir somente lacunas demonstradas, incluindo os handlers de segurança de T012 se afetados. Dep.: T014–T016 escritos/executados antes da correção. Aceite: mesma referência em toda tentativa, guardas atuais anteriores a qualquer mutação, elegibilidade conjunta e mensagens/categorias do contrato; não confundir validação persistente/interna com entrada inválida nem fazer lookup em Bean Validation. Se já atender, registrar revisão e GREEN sem mudança cosmética ou enfraquecimento de teste.
- [X] T018 [US2] Executar S2 conjunto para as classes T014–T016 e repetir o núcleo S1 afetado na mesma seleção; registrar resultados/contextos em `specs/008-registrar-pagamento/quickstart.md`. Dep.: T017. Aceite: nove cenáriosUS2, seisUS1 e segurança anterior à MVC comprovados; rejeições conservam dados e segredos. Conferir que o matcher/handlers novos não capturam consultas007 nem rotas administrativas de produtos.

**Checkpoint C2**: US1+US2 verificadas, com temporalidade e perfil atual após lock. Nenhuma promessa de bloquear revogação depois da última leitura.

## Phase 5: User Story 3 — Preservar quitação única sob repetição e concorrência (P1)

**Goal**: obter uma aceitação por pedido e permitir ambas as aceitações para pedidos distintos.

**Independent Test**: cinco cenários US3.1–5, com ocorrências iguais/diferentes e dois ADMINs; uma linha e ocorrência vencedora, ou duas quitações com vínculos exclusivos em pedidos distintos.

### Tests first

- [X] T019 [P] [US3] Criar `src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java`. Dep.: T018. Aceite: repetição por mesmo/outro ADMIN, ocorrência igual/diferente e WAITING_PAYMENT com Payment anterior dão409 sem sobrescrita/reparo. Coordenar duas chamadas HTTP válidas por conexões/transações reais ao mesmo pedido; observar entrada/lock/commit, esperar término limitado e exigir exatamente um201/um409, Payment único e ocorrência da vencedora. Timeout do experimento é falha, não prova da rejeição de negócio.
- [X] T020 [P] [US3] Criar `src/test/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentDistinctOrdersIntegrationTest.java`. Dep.: T018. Aceite: contexto JPA pré-carrega WAITING_PAYMENT, outra transação efetiva quitação e o fragmento lock+refresh devolve estado atual; isso é teste de repository, não prova de commit na entrada HTTP com transação externa. Para pedidos distintos, manter A bloqueado enquanto B confirma commit em outra conexão, liberar A e exigir ambas201, vínculos/ocorrências/valores exclusivos. Inspecionar SQL: lock somente da raiz, sem lock global, Product ou predicado que esconda estado incompatível. Não usar sleeps, NOWAIT ou SKIP LOCKED para fazer o teste passar.

### Implementation e checkpoint

- [X] T021 [US3] Revisar/corrigir, se necessário, `src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryImpl.java`, `src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepositoryImpl.java` e `src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java`. Dep.: T019–T020 antes de qualquer ajuste. Aceite: verificar pagamento/estado atuais sob lock até commit; persist nunca atualiza Payment existente. Somente constatação segura de pagamento anterior produz 409 de repetição. Sem pagamento anterior, estado incompatível produz seu 409 específico. PK inesperada, FK, falha de flush, timeout/deadlock ou indisponibilidade de pool não são genericamente classificados como duplicidade. Exceção de banco propaga para rollback/500, sem consulta/retry na transação falha ou sucesso reutilizado. O núcleo já correto não precisa ser reimplementado.
- [X] T022 [US3] Executar S3 para T019–T020, constraints de T007 e casos temporais/perfis sob espera de T016; registrar conexões, marcos e resultados em `specs/008-registrar-pagamento/quickstart.md`. Dep.: T021. Aceite: cinco cenáriosUS3 e nenhuma perda da referência/guarda; banco real mantém PK/FK/NOT NULL, ambos os pedidos distintos concluem sem mutex global. Separar falhas operacionais dos pares válidos sem outras falhas da spec e não contabilizá-las como sucesso de concorrência.

**Checkpoint C3**: unicidade e independência por pedido comprovadas no H2/contextos observados; não extrapolar para outro banco ou throughput de produção.

## Phase 6: User Story 4 — Distinguir falha transacional de confirmação não recebida (P1)

**Goal**: comprovar atomicidade antes da efetivação e preservação após commit.

**Independent Test**: quatro cenários US4.1–4, com baseline independente e controle positivo; perdas posteriores não autorizam apagar pagamento ou devolver sucesso anterior em novo POST.

### Tests first

- [X] T023 [US4] Criar `src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java`, usando como referência a instrumentação de produtos e `src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java`. Dep.: T022. Aceite: failpoints somente de teste, scoped ao serviço008; observar proxy real dono da transação, primeiro DML efetivamente executado (sem presumir ordem de insert/update), flush, DTO interno, beforeCommit, tentativa/delegação/sucesso de Connection.commit e rollback/completion. Instrumentação defeituosa deve falhar como tal, não simular aprovação de atomicidade; nenhum controle/failpoint na aplicação de produção.
- [X] T024 [P] [US4] Escrever `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackApiHttpIntegrationTest.java`. Dep.: T023. Aceite: falha SQL, após primeiro DML, após flush, no mapeamento, beforeCommit e exceção JDBC antes de delegar commit não entregam sucesso e restauram baseline, confirmado por conexão independente depois da conclusão; controle sem injeção confirma commit e201. Diferenciar falha interna de lookup/lock/persistência de401/404/409; não contar beforeCommit como falha do driver, nem pré-delegação como falha física/resultado desconhecido.
- [X] T025 [P] [US4] Criar `src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseDeliveryIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseTransportHttpIntegrationTest.java`. Dep.: T023. Aceite: primeiro simular entrega impedida com filtro/buffer MockMvc após retorno transacional e commit observado por JDBC independente; depois realizar experimento HTTP/socket/proxy de teste, recebendo confirmação a montante e encerrando a jusante sem entregar o corpo. Usar limites finitos e registrar bytes/marcos reais. Payment/PAID permanecem, reenvio409 e titular GET vê PAID; HEAD mantém autorização/sem corpo, não é usado para ler status de negócio no corpo. Só descartar DTO/response não vale como falha de transmissão; nenhuma dessas simulações comprova pane de rede/banco em produção. A leitura após pagamento depende também da comprovação contratual de US5.

### Implementation e checkpoint

- [X] T026 [US4] Revisar/corrigir fronteira em `src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java`, `src/main/java/com/devsuperior/dscommerce/controllers/AdminPaymentController.java` e `src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java`. Dep.: T024–T025 antes do ajuste. Aceite: proxy físico conclui commit antes de201; erro anterior não deixa Payment sem PAID ou PAID sem Payment, não há catch/continuação em transação falha. Flush/DTO não equivalem a commit. Exceção após efetivação ou commit com resultado indeterminado não promete rollback, compensação nem retry automático; 500/ausência de resposta não demonstram ausência do pagamento. Guardas corretas já existentes são preservadas.
- [X] T027 [US4] Executar S4 de rollback/entrega/transporte e núcleo S1/S3 afetado; registrar em `specs/008-registrar-pagamento/quickstart.md` cada failpoint realmente atingido, transação, commit/delegação, leitura independente e limite da prova. Dep.: T026. Aceite: quatro cenáriosUS4, nenhum sucesso parcial e repetição409 após commit; evidências de MockMvc, HTTP/socket e JDBC separadas. Resultado indeterminado continua explicitamente fora da garantia de rollback; não inventar um experimento de crash não realizado.

**Checkpoint C4**: falhas antes/depois da efetivação distinguíveis. O transporte e as leituras finais serão conciliados com US5/FINAL; não declarar toda a jornada por testes somente de mocks.

## Phase 7: User Story 5 — Reconhecer PAID nas consultas próprias e preservar jornadas (P1)

**Goal**: tornar PAID observável ao titular com o contrato008 de consultas, conservando a007 histórica e as jornadas001–007.

**Independent Test**: seis cenários US5.1–6; list/detail com mesmos campos e valores, terceiros excluídos inclusive ADMIN pagador, cinco pedidos2/2/1 por criação/ID e nenhuma mutação nas leituras.

### Tests first

- [X] T028 [P] [US5] Estender variantes PAID em `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java` e `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`. Dep.: T027. Aceite: fixtures coerentes Payment+PAID, mesmas chaves/representação monetária e estado armazenado; usar o alvo `specs/008-registrar-pagamento/contracts/own-orders-openapi.yaml` e preservar origens007. Não falsificar somente manifesto nem adicionar dados de pagamento às respostas. A projeção atual de status pode passar sem mudança de código; registrar GREEN, sem exigir RED artificial.
- [X] T029 [P] [US5] Estender os suportes compartilhados de `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java`, preservando suas classes Api existentes. Dep.: T027. Aceite: quitar por operação008 real em jornada, observar com fixture/probe008 sem mudar significado dos probes históricos, usar banco isolado do contexto e limpar Payment antes de pedidos. Titular lê PAID em lista/detalhe; ADMIN pagador de terceiro não obtém conteúdo/count e tem404 igual ao inexistente; autorização GET/HEAD e erros continuam próprios. Cinco pedidos em2/2/1, empate criação/ID, pagar antigo não reordena; nomes atuais, itens e dinheiro histórico intactos, nenhuma chave de pagamento extra. HTTP real ROOT/Api, não substituição por MockMvc.

### Implementation e regressões

- [X] T030 [US5] Confrontar os testes com `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java`, `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java`, `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderSummaryDTO.java` e `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java`, ajustando apenas se os testes demonstrarem necessidade. Dep.: T028–T029 antes de eventual alteração. Aceite: ler o status armazenado ampliado por008, manter campos, predicates de titularidade/conteúdo/count/detail, paginação/ordenação criação DESC/ID DESC, valores e erros; nenhuma nova consulta administrativa ou dado de Payment. A ausência de código novo é válida se a implementação atual já satisfizer o alvo; o contrato007 fechado não se amplia sozinho por isso.
- [X] T031 [US5] Estender `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionApiHttpIntegrationTest.java` com jornadas após quitação e observação008. Dep.: T030. Aceite: repetir GET/HEAD bem-sucedido/vazio/rejeitado sem DML nem mudança de tb_payment; comparar oito tabelas após sucesso/rejeição de pagamento, com única alteração permitida Payment+status do pedido alvo. Manter catálogo/detalhe/filtro/paginação públicos, login, escrita administrativa/proteção de compras e registro que cria novo pedido WAITING_PAYMENT por envio válido. Observar ausência de tentativas de integração financeira com instrumento de teste adequado à janela e registrar seu alcance; inspeção dos caminhos produtivos confirma ausência de gateway/cobrança, sem realizar chamadas financeiras externas para testar essa exclusão. Carrinho permanece do consumidor, sem criar tabela/fluxo novo para testá-lo.
- [X] T032 [US5] Executar S5 focado em DTO/repository/service e HTTP007 de lista/detalhe/segurança/regressão ROOT/Api alterados, registrando em `specs/008-registrar-pagamento/quickstart.md` o contrato alvo e os resultados. Dep.: T028–T031. Aceite: seis cenáriosUS5 e preservações aprovados no ambiente observado; não reutilizar validação estrutural documental como prova de endpoint. Não alterar `specs/007-consultar-meus-pedidos/spec.md` ou seus contratos; preparar a referência vigente pós-entrega para publicação em T035, sem dois contratos alternativos vigentes.

**Checkpoint C5**: PAID observável ao titular sem novos privilégios; as consultas007 históricas permanecem intactas como artefatos. FINAL ainda é obrigatório para a008.

## Phase 8: Polish & Cross-Cutting Concerns — conformidade e fechamento

- [X] T033 Auditar conjuntamente implementação e evidências contra `specs/008-registrar-pagamento/spec.md`, `specs/008-registrar-pagamento/plan.md`, `specs/008-registrar-pagamento/contracts/README.md` e `.specify/memory/constitution.md`. Dep.: T032. Aceite: reconciliar a matriz abaixo, classes/métodos/contextos e lacunas; gates008 agora precisam de código e prova, sem herdar a EXCEÇÃO DOCUMENTADA do Gate IV005 nem reclassificá-la como PASS estrito. Rever ausência de IO em validação, JPA/DTO/camadas, proxy, SQL, schema, classificação segura, precisão e inexistência de integrações fora do recorte. Se houver lacuna funcional, testar/reproduzir e corrigir no escopo antes de FINAL; não ajustar requisito/contrato silenciosamente.
- [X] T034 Executar FINAL uma vez na revisão estabilizada com `./mvnw` ou `mvnw.cmd` e a suíte de `src/test/java/com/devsuperior/dscommerce/`, registrando evidências novas em `specs/008-registrar-pagamento/quickstart.md`. Dep.: T033 e eventuais correções verificadas. Aceite: suíte completa sem filtro inclui todas as invocações008 e regressões001–007, sem falhas/erros/ignorados não justificados; conferir efetivamente as classes/contextos e integridade de `src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java`. Sem reexecução focal redundante imediatamente antes; preservar relatórios e distinguir contagens JUnit/HTTP/failpoints. Falha exige causa e nova revisão apropriada, não repetição até obter verde.
- [X] T035 Acrescentar resultados e matriz cenário→classe/método/invocação/contexto→comando/revisão/resultado/relatório em `specs/008-registrar-pagamento/quickstart.md` e apontar `README.md` para os contratos008 conforme `specs/008-registrar-pagamento/contracts/README.md`, somente após FINAL aprovado. Dep.: T034. Aceite: preservar evidências históricas, identificar o snapshot008 completo como substituto vigente do contrato de consultas007 após entrega, além do contrato POST008, sem editar artefatos007. Não manter metadados públicos contraditórios sobre vigência: eventual atualização do estado editorial dos contratos008 deve refletir a entrega comprovada, sem mudar schemas/regras aprovadas. Reutilizar validação estrutural existente quando conteúdo normativo não mudou; se mudar, exigir revisão e validação documental pertinente na fase autorizada. Não confundir resultado local com CI; não disparar push/CI nesta tarefa.
- [X] T036 Revisar o diff, arquivos novos, referências, documentação de vigência e whitespace de `specs/008-registrar-pagamento/tasks.md`, `specs/008-registrar-pagamento/quickstart.md`, `README.md` e arquivos de código/teste afetados; conferir status final e seleção008. Dep.: T035. Aceite: `git diff --check` e conferência dos não rastreados na execução futura, somente mudanças do recorte, tarefas marcadas apenas por evidência efetiva e nenhuma lacuna escondida. Não fazer stage/commit/push/merge ou alterar main sem autorização específica; reportar impedimentos e limites, preservando a exceção005 e os contratos históricos.

## Dependencies & Execution Order

```text
T001 → T002 → (T003 || T004) → T005
  → (T006 || T007) → T008 → T009 → T010 → T011 → T012 → T013
  → (T014 || T015 || T016) → T017 → T018
  → (T019 || T020) → T021 → T022
  → T023 → (T024 || T025) → T026 → T027
  → (T028 || T029) → T030 → T031 → T032
  → T033 → T034 → T035 → T036
```

`||` representa somente autoria possível em arquivos distintos, depois dos pré-requisitos. Testes/redes/locks usam concorrência interna controlada, não builds Maven paralelos. DTOs/reader T010 precedem o serviço T011, e a operação só é exposta com segurança em T012. Não há dependência circular entre guardas e histórias: o núcleo de US1 aplica desde o início as invariantes comuns; as histórias seguintes acrescentam casos específicos antes de alterar esse núcleo.

### História, cobertura e critério independente

| História | Tarefas | Depende de | Critério para encerrar o incremento |
| --- | --- | --- | --- |
| US1 P1 | T006–T013 (8) | Foundational | Seis cenários de quitação/ocorrência/histórico e proteção mínima em HTTP/JDBC, C1. |
| US2 P1 | T014–T018 (5) | US1 | Nove causas/perfis/tempo, inclusive espera; nenhuma mutação/divulgação indevida, C2. |
| US3 P1 | T019–T022 (4) | US1+US2 | Cinco casos de repetição/concorrência, uma aceitação no mesmo pedido e ambas em distintos, C3. |
| US4 P1 | T023–T027 (5) | US1–US3 | Quatro casos e controles que separam rollback, commit e transporte, C4. |
| US5 P1 | T028–T032 (5) | US1–US4 | Seis casos de consultas/preservações com contrato pós008 e acesso próprio, C5. |

Setup2 + Foundational3 + histórias27 + fechamento4 = **36 tarefas abertas**. A ordem das histórias resolve compartilhamento de serviço, segurança, fixtures e leituras; não alega independência de arquivos onde ela não existe.

## Seleções focais e checkpoints conjuntos

Todos os comandos abaixo são futuros. Na raiz/PowerShell: `.\mvnw.cmd "-Dtest=<seletor da linha>" test`. Em Linux usar `./mvnw`. O seletor nunca deve ocultar classes herdadas/Api; verificar relatórios e não usar failIfNoTests=false.

| Etapa | Seletor / comando | Finalidade e checkpoint |
| --- | --- | --- |
| BASE/T002 | `OrderAggregateTest,OwnOrderQueryDtoContractTest,EffectiveAccountRolesRepositoryTest,OwnOrderQueryList*HttpIntegrationTest,OwnOrderQueryDetail*HttpIntegrationTest` | Estado anterior de histórico, perfis e consultas reais, sem suíte completa repetida. |
| S1-RED/T006–T007 | Classes `PaymentRegistrationHttpIntegrationTest,PaymentRegistrationApiHttpIntegrationTest,PaymentPersistenceIntegrationTest,PaymentRegistrationDatabaseProbeTest` somente quando escritas | Observar ausência da operação/modelo por asserção funcional; não considerar preparação inválida como RED. |
| S1-GREEN/T013 | `PaymentRegistration*Test,PaymentPersistenceIntegrationTest` | Núcleo HTTP, DTO, serviço e suporte/probe; conferir que todas as classes T006–T012 estão incluídas. C1. |
| S2/T018 | `PaymentAuthorization*Test,PaymentInput*Test,PaymentBoundaryValidationTest,PaymentTemporalAndCurrentRolesIntegrationTest,PaymentRegistration*Test,PaymentPersistenceIntegrationTest` | Fronteira/perfis/tempo junto do núcleo; testar antes de T017 e confirmar após mudanças necessárias. C2. |
| S3/T022 | `PaymentConcurrencyIntegrationTest,PaymentDistinctOrdersIntegrationTest,OrderPaymentLockRepositoryTest,PaymentPersistenceIntegrationTest,PaymentTemporalAndCurrentRolesIntegrationTest` | Unicidade, refresh, constraints e guardas durante espera; execução anterior a T021 e posterior se corrigido. C3. |
| S4/T027 | `PaymentRollback*Test,PaymentResponse*Test,PaymentRegistrationHttpIntegrationTest,PaymentRegistrationApiHttpIntegrationTest,PaymentConcurrencyIntegrationTest` | Atomicidade/transporte com sucesso e repetição; testes antes de T026 e confirmação posterior se alterado. C4. |
| S5/T032 | `OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest,OwnOrderQueryList*HttpIntegrationTest,OwnOrderQueryDetail*HttpIntegrationTest,OwnOrderQuerySecurity*HttpIntegrationTest,OwnOrderQueryRegression*HttpIntegrationTest` | Consultas reais e invariantes antigas após quitação. Executar antes de eventual T030 e após ajuste; se nenhuma alteração necessária, um GREEN basta. C5. |
| FINAL/T034 | `.\mvnw.cmd test` / Linux `./mvnw --batch-mode --no-transfer-progress test` | Única suíte completa na revisão estabilizada, regressões001–007 e todas as provas008. |

Rodar apenas classes que já existam e acrescentar à seleção qualquer teste novo pertinente que o seletor não alcance. Testes de DTO/serviço criados em T010/T011 precisam de seu RED específico antes do respectivo corpo; o antigo404 não os substitui. Depois de um checkpoint conjunto aprovado, não repeti-lo sem mudança, falha ou preocupação nova que justifique. FINAL continua necessário pelo alcance das regressões. Se algum experimento de transporte ficar impedido, registrar causa e lacuna; não concluir V07 por mocks ou descartar a exigência.

## Rastreabilidade: 30 cenários, 22 FR e oito SC

Esta matriz agenda provas, não registra execução. As notações USn.m são as histórias/cenários da spec; a evidência final acrescentará métodos/invocações reais em T035. Guards e atomicidade comuns podem ser testados em mais de uma história sem duplicar tarefas de implementação.

| Cenário | Tarefas/prova principal | Grupo do plano |
| --- | --- | --- |
| US1.1 | T006–T013: pedido alheio,42,50,confirmação/commit | V01,V03,V04,V06 |
| US1.2 | T006,T011–T014: próprio e ADMIN isolado | V01,V03 |
| US1.3 | T006,T009–T011,T013: catálogo12,00/histórico20,00 | V04 |
| US1.4 | T006,T010–T011,T013: conservar ocorrência | V01,V02 |
| US1.5 | T006,T013,T016: igualdade à criação | V02 |
| US1.6 | T006,T013,T016: igualdade à referência/espera | V02 |
| US2.1 | T014,T017–T018: credenciais/conta401 | V01,V03 |
| US2.2 | T014,T017–T018: CLIENT/sem ADMIN403 | V03 |
| US2.3 | T014,T016–T018: perfil atual, inclusive após lock | V03 |
| US2.4 | T015,T017–T018: inexistente404 sem criar | V01,V04 |
| US2.5 | T010,T015,T017–T018: entradas obrigatórias/válidas | V01,V02 |
| US2.6 | T015–T018: anterior à criação400 | V02 |
| US2.7 | T015–T018: posterior à referência fixa400 | V02 |
| US2.8 | T006,T010,T015,T018: somente pedido/ocorrência | V01,V04 |
| US2.9 | T015,T017–T018: estado incompatível sem reparo | V04 |
| US3.1 | T019,T021–T022: repetição igual409 | V05 |
| US3.2 | T019,T021–T022: outra ocorrência/outro ADMIN | V05 |
| US3.3 | T019,T021–T022: Payment anterior com WAITING_PAYMENT | V04,V05 |
| US3.4 | T019–T022: mesmo pedido,um201/um409 | V05 |
| US3.5 | T020–T022: pedidos distintos,ambos201 | V05 |
| US4.1 | T023–T024,T026–T027: falhas de processamento | V06 |
| US4.2 | T023–T024,T026–T027: DML/flush/commit sem parcial | V06 |
| US4.3 | T025–T027,T029,T032: pós-commit,novo409,titular lê | V07,V08 |
| US4.4 | T014,T024,T026–T027: lookup/interno500 seguro | V01,V03,V06 |
| US5.1 | T028–T030,T032,T035: PAID/mesmos campos/precedência | V08,V10 |
| US5.2 | T029–T030,T032: ADMIN pagador segue sem leitura alheia | V03,V08 |
| US5.3 | T029–T030,T032: cinco pedidos2/2/1 criação-ID | V08 |
| US5.4 | T028–T032: campos/erros/vazios/histórico/zero DML | V08,V09 |
| US5.5 | T031–T034: jornadas001–007 preservadas | V09 |
| US5.6 | T004,T013–T034: só alvo alterado,sem cobrança externa | V04,V08,V09 |

| FR | Tarefas de implementação/comprovação | SC |
| --- | --- | --- |
| FR-001 | T006,T011–T012,T014,T016–T018 | SC-001–002 |
| FR-002 | T006,T011–T014,T029–T032 | SC-001,SC-007 |
| FR-003 | T006,T010–T012,T015,T018 | SC-005 |
| FR-004 | T006,T009–T011,T013,T015 | SC-001,SC-006 |
| FR-005 | T009–T011,T015,T018–T022 | SC-001,SC-004–005 |
| FR-006 | T006,T010–T011,T015–T018 | SC-003,SC-005 |
| FR-007 | T006,T010–T013 | SC-001,SC-003 |
| FR-008 | T008–T011,T013,T023–T027 | SC-001,SC-005 |
| FR-009 | T004,T006,T009–T011,T013,T028–T034 | SC-006–008 |
| FR-010 | T009–T011,T019–T022,T025–T027 | SC-004–005 |
| FR-011 | T007–T011,T019–T022 | SC-004 |
| FR-012 | T014–T024,T027 | SC-002–005 |
| FR-013 | T011–T012,T023–T027 | SC-005 |
| FR-014 | T025–T027,T029,T032 | SC-004–005 |
| FR-015 | T011–T012,T014–T018,T024–T027 | SC-002,SC-005 |
| FR-016 | T028–T030,T032–T035 | SC-007 |
| FR-017 | T028–T030,T032–T035 | SC-007 |
| FR-018 | T014,T029–T030,T032 | SC-007 |
| FR-019 | T028–T030,T032 | SC-006–007 |
| FR-020 | T004,T013,T018,T022,T027,T029–T034 | SC-007–008 |
| FR-021 | T028–T034 | SC-007–008 |
| FR-022 | T031,T033–T035 | SC-008 |

SC-001 é encerrado por C1; SC-002 por C2; SC-003 por C1+C2; SC-004 por C3+C4; SC-005 por C2+C3+C4; SC-006 por C1+C5; SC-007 por C5; SC-008 por C5+FINAL. Todos dependem da reconciliação de T033/T035 para conclusão da feature. Quantificadores100% referem-se aos casos definidos, não a SLA ou cobertura universal.

## Parallel Examples por história

| História | Autoria paralela permitida | Convergência/limite |
| --- | --- | --- |
| US1 | T006 HTTP e T007 persistência/probe, após T005 | Maven sequencial; modelo/repos T008–T009, DTOs T010, serviço T011 e endpoint protegido T012 seguem suas dependências. |
| US2 | T014 autorização, T015 fronteira e T016 tempo/perfis, após T013 | Arquivos distintos; alterações comuns só em T017 após conciliar casos. |
| US3 | T019 mesmo pedido/repetição e T020 refresh/pedidos distintos, após T018 | Não compartilhar um banco de contexto entre autores/execuções concorrentes; convergem em T021. |
| US4 | T024 rollback e T025 resposta/transporte, após T023 | Sem alterar simultaneamente o harness compartilhado; convergem em T026. |
| US5 | T028 DTO/repository/service e T029 HTTP, após T027 | Contrato e fixture008 comuns já fixados; convergem em T030. |

Esses exemplos não autorizam delegação automática, builds paralelos, branches novas ou commits. Alterações de fixture/quickstart compartilhadas devem ser sequenciadas.

## Implementation Strategy e checkpoints de commit

Começar por Setup+Foundational e US1 como **primeiro incremento interno**, com controles mínimos já ativos. US1 sozinha não é MVP publicável do recorte aprovado: as cinco histórias são necessárias, inclusive confirmação observável nas consultas. Acrescentar as comprovações US2, US3, US4 e US5, preservando os checkpoints anteriores; depois auditar e executar FINAL na revisão estabilizada. Não implementar todas as histórias antes de observar qualquer RED/GREEN.

Checkpoints lógicos sugeridos para commits futuros, somente com autorização específica e revisão do diff: C1 (suporte+núcleo), C2 (perfis/entrada/tempo), C3 (unicidade/concorrência), C4 (atomicidade/transporte), C5 (consultas/regressões) e fechamento T035–T036 (evidências/vigência). Podem ser agrupados quando não houver mudança produtiva no checkpoint; não criar commits vazios nem um commit por checkbox. Nenhum commit, stage, push ou implantação é executado ou autorizado por este arquivo.

## Notes e limites desta geração

O PDF exige registro dos dados/instante e acesso Admin conforme as referências da spec; quitação integral única, ocorrência informada, repetição rejeitada e ADMIN para qualquer titular são decisões aprovadas, não questões abertas. A referência fixa e sua precisão seguem a spec/R3. Não há gateway, cobrança, movimentação financeira externa, parcial, meio de pagamento, comprovante, parcelamento, estorno, cancelamento, entrega, relatório, consulta administrativa geral, carrinho ou interface neste trabalho.

O contrato008 de consultas é snapshot completo e substitui007 após entrega, nunca alternativa simultânea. Não reescrever a spec007 nem seus contratos, testes históricos como evidência retroativa ou a EXCEÇÃO DOCUMENTADA do Gate IV005. Alterar testes existentes para cobrir008 preserva as asserções antigas; não altera o significado dos resultados históricos.

Formato, contagens, referências e cobertura desta lista são revistos documentalmente nesta geração, sem executar testes ou validadores. Nenhuma tarefa foi concluída por essa revisão. Não há decisão material de negócio pendente; continuam futuras todas as provas funcionais, classificação de falhas reais, ambientes e seleção efetiva de casos descritos acima.
