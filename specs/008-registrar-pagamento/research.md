# Research: Registrar pagamento

**Estado**: decisões de desenho da Feature 008, não implementação. Base inspecionada: 24a29a9c031fc554f2b6deb6a4b5c2fde708719d. [Spec](spec.md) e [checklist](checklists/requirements.md) aprovados permanecem intactos. A pesquisa prevista na skill foi dividida entre agentes de segurança, persistência e contratos, todos somente em leitura; o desenho consolidado abaixo resolve as escolhas técnicas. Não há clarificação pendente.

## R1 — Operação e isolamento dos erros

**Decision**: POST /admin/orders/{id}/payments, id positivo int64; corpo somente occurredAt; confirmação 201 somente orderId, occurredAt e status PAID. Não criar GET de pagamento, relatório ou Location que prometa operação inexistente. Erros de negócio/entrada usam ErrorResponseDTO existente, exatamente status/error/message/path. Entrada inválida, inclusive ocorrência fora do intervalo, 400; autenticação 401; perfil 403; pedido ausente 404; repetição e estado incompatível 409 com mensagens distintas; falha interna/lock timeout/commit não concluído 500.

**Rationale**: separa operação sobre pedido identificado da consulta própria. [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) já cobre /admin/**, mas seus handlers e o ramo administrativo de [GlobalExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java) são de produtos. Esse handler também reconhece especificamente ValidProductInput ao tratar Bean Validation. Reutilizá-los diretamente daria mensagens de produto ou classificação incorreta de falhas. Planejar cadeia específica anterior a /admin/** e advice scoped ao novo controller, seguindo o isolamento da [007](../007-consultar-meus-pedidos/plan.md).

**Alternatives considered**: POST /admin/payments com orderId no corpo é possível, mas desnecessário; PUT idempotente conflita com repetição rejeitada. 422 seria válido para janela temporal, mas 400 mantém uma categoria clara de entrada inválida. Não transformar toda exceção de integridade em 409.

## R2 — ADMIN efetivo e fronteira de validação

**Decision**: reutilizar decoder e conversor JWT existentes, sem confiar em roles/name claims. Antes de ler pedido, exigir conta e ADMIN atuais por projeção escalar; repetir após obter o lock e imediatamente antes de mutar. Conta ausente 401; conta sem ADMIN 403; consulta falha 500. Guarda de pagamento própria não filtra titular. Não modificar a guarda da 007.

**Rationale**: [AuthenticatedAccountService](../../src/main/java/com/devsuperior/dscommerce/services/AuthenticatedAccountService.java) resolve entidade/coleção que pode já estar gerenciada. [UserAccountRepository](../../src/main/java/com/devsuperior/dscommerce/repositories/UserAccountRepository.java) oferece findEffectiveAccountRolesById, com flushMode COMMIT; a manutenção atual já revalida essa projeção. Autoridade obtida antes de uma espera não deve substituir releitura posterior. Não há promessa de serializar revogação concorrente depois da última leitura.

**Decision**: DTO de corpo PaymentRegistrationRequestDTO com Instant occurredAt @NotNull, @Valid no controller; id Long @Positive na validação MVC nativa. Reader Jackson exclusivo rejeita forma/tipo e faz parse temporal estrito antes de Bean Validation; não muda ObjectMapper global. Validator não consulta serviço/banco. Janela temporal dependente da compra e elegibilidade são regras posteriores do serviço.

**Alternatives considered**: usar AdminAccessService sozinho pode conservar coleção antiga; copiar seu texto de erro de produto é inadequado. Validator com lookup ampliaria a exceção histórica do Gate IV da 005. A 008 segue validação local na fronteira, depois lógica de serviço, sem essa exceção.

## R3 — Instante e referência que não avança

**Decision**: primeira instrução do método de negócio transacional captura reference = clock.instant(), antes de qualquer guarda, consulta ou espera de lock. Reutilizar [OrderClockConfig](../../src/main/java/com/devsuperior/dscommerce/config/OrderClockConfig.java). Não truncar nem recapturar reference. Ela é transitória, não entrada, coluna ou saída.

**Decision**: entrada textual RFC3339 restrita: ano 0001–9999, T e Z maiúsculos, segundos obrigatórios, offset explícito Z ou ±HH:MM, fração opcional de 1–9 dígitos. Aceitar somente instante exatamente representável em milissegundos: nano % 1000000 == 0. Zeros adicionais são aceitos (.123000000 equivale a .123); .123000001 é rejeitado, sem arredondar. Rejeitar -00:00, leap second, hora 24, espaços, datas impossíveis, offsets além de ±18:00 e ano UTC normalizado fora de 0001–9999. Normalizar por OffsetDateTime estrito para Instant; emitir UTC com exatamente três casas. Persistir occurredAt com precisão 3 explícita.

**Rationale**: normalizar offset e zeros não altera o instante aceito. A referência mantém precisão nativa, mesmo maior que a precisão aceita da entrada; igualdade superior continua válida quando representável, exercitada com Clock fixo em milissegundos. Comparar Instants, nunca textos: createdAt <= occurredAt <= reference. O tempo decorrido esperando lock não muda o resultado.

**Alternatives considered**: descartar precisão silenciosamente viola a spec. Nanos em toda a cadeia são viáveis em H2, mas desnecessários aqui. Só aceitar Z reduziria interoperabilidade sem benefício de integridade. Não alterar a criação da 004, já capturada em milissegundos.

Fontes primárias: [Java 21 OffsetDateTime](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/OffsetDateTime.html), [Instant](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Instant.html), [Hibernate 6.5 FractionalSeconds](https://docs.hibernate.org/orm/6.5/javadocs/org/hibernate/annotations/FractionalSeconds.html). A gramática/precisão escolhida é decisão da 008, não requisito atribuído ao PDF.

## R4 — Modelo mínimo e integridade física

**Decision**: nova tb_payment com order_id como PK/FK de tb_order e occurred_at não nulo TIMESTAMP(3) WITH TIME ZONE, mapeada por Payment, @MapsId e @OneToOne sem cascade REMOVE. Sem ID independente, valor duplicado, comprador, meio de pagamento ou referência externa. Valor integral deriva sempre dos itens históricos. Acrescentar somente PAID ao enum OrderStatus e método de transição em Order; conservar as quatro colunas existentes de tb_order.

**Rationale**: [OrderItem](../../src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java) registra quantidade, posição e unit_price decimal19,2; [Order](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java) soma subtotais. PK impede dois pagamentos por pedido; FK impede órfão e remoção do pedido referenciado. Não há validação de preço atual. Precisão temporal explícita evita o arredondamento implícito do banco sobre valores aceitos.

**Alternatives considered**: ID próprio com UNIQUE(order_id) duplica identidade sem necessidade. Guardar valor pago exige mais um valor a sincronizar sem apoiar pagamento parcial. Triggers para equivalência entre PAID e pagamento ampliariam desenho: essa equivalência e limites com criação/referência são regras de serviço/transação, não garantias de CHECK entre tabelas. SQL arbitrário que contorne o serviço não recebe garantia universal de coerência do domínio. Para chave compartilhada, usar inserção explícita EntityManager.persist por fragmento PaymentInsertRepository; não usar merge/save de entidade com ID atribuído, que poderia atualizar registro existente em vez de violar a PK. Nenhum caminho modifica uma quitação já existente.

## R5 — Lock por raiz e releitura

**Decision**: transação READ_COMMITTED; repository fragment exclusivo findPaymentOrderForUpdate, usando EntityManager.find com PESSIMISTIC_WRITE e refresh com esse lock antes de acessar estado/criação. Sem predicate de estado, titular, JOIN FETCH de coleção ou DISTINCT no lock. Após o lock, consultar Payment por order_id e reler itens em projeção escalar ordenada por position, sem carregar Product/preço atual. Revalidar ADMIN e decidir no serviço. Payment existente tem precedência sobre estado incompatível quando ambos explicam a rejeição; demais combinações de erro não ganham precedência nova.

**Rationale**: lock não é refresh do primeiro nível. O refresh explícito evita estado antigo em persistence context já carregado. A segunda tentativa espera commit/rollback da primeira e verifica o resultado atual; não pode sobrescrevê-lo. Pedidos diferentes usam raízes distintas. PK é a defesa final contra caminhos que não obedeçam ao lock.

**Alternatives considered**: synchronized/mutex global não protege múltiplas instâncias e serializa compras distintas. SERIALIZABLE global, NOWAIT, SKIP LOCKED e lock de produtos não são necessários; em especial, SKIP LOCKED poderia aparentar inexistência. Timeout/deadlock é falha operacional 500, não repetição inferida. Não usar classificação genérica de DataIntegrityViolationException como confirmação de pagamento existente.

Fontes: [JPA 3.1 EntityManager](https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html), [especificação JPA 3.1, locks](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.pdf), [H2 2.2.224 SELECT e tipos](https://raw.githubusercontent.com/h2database/h2database/version-2.2.224/h2/src/main/org/h2/res/help.csv), [H2 2.2.224 isolamento](https://raw.githubusercontent.com/h2database/h2database/version-2.2.224/h2/src/docsrc/html/advanced.html). As provas reais do comportamento gerado ainda serão executadas.

## R6 — Transação, retorno e falhas

**Decision**: serviço via proxy Spring, REQUIRED, READ_COMMITTED, rollbackFor Exception; controller sem transação externa. Capturar referência primeiro, guardar conta, lock/refresh, repetir guarda, verificar existência/estado/tempo, calcular histórico, persistir Payment, marcar PAID, flush e mapear confirmação dentro da transação. Controller só constrói 201 após retorno bem-sucedido do proxy, que deve possuir a transação física nessa entrada HTTP.

**Rationale**: flush não é commit. O retorno do corpo do serviço ou a construção de DTO interno não provam sucesso. REQUIRED em chamador com transação externa não garante conclusão nesse retorno; testes da entrada HTTP devem demonstrar ausência de transação externa. Não criar autochamada que contorne o proxy nem catch/continuação depois de falha de flush/commit. Conflito de domínio é decidido antes de DML; exceção do banco propaga para fora da transação e é centralizada.

**Alternatives considered**: REQUIRES_NEW imporia independência de chamador sem requisito de orquestração atual. Capturar exceção dentro da transação para consultar/repetir o pagamento é inválido. Uma falha de commit antes da efetivação conhecida deve deixar baseline restaurada; falha posterior à efetivação ou resultado indeterminado não autoriza prometer rollback. Não há mensagem de sucesso reutilizado, compensação ou retry automático.

Fonte: [TransactionAspectSupport 6.1.13](https://raw.githubusercontent.com/spring-projects/spring-framework/v6.1.13/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java). A implementação existente de [OrderRollbackIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java) distingue SQL, pós-flush e beforeCommit; [AdminProductFailureInjectionTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductFailureInjectionTestConfig.java) também instrumenta tentativa/delegação do commit. São referências inspecionadas, não testes executados nesta fase.

## R7 — Contrato das consultas após a entrega

**Decision**: criar [snapshot completo de consultas pós-008](contracts/own-orders-openapi.yaml), versão 0.2.0, derivado do [contrato histórico da 007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml). Somente ampliar enum de status em resumo/detalhe para WAITING_PAYMENT e PAID, ajustar metadados/descrições e acrescentar exemplos PAID. Manter campos, erros, segurança, paginação e representações. Não usar referência externa que herde o enum fechado anterior.

**Rationale**: o código de [OwnOrderQueryService](../../src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java) já projeta estado armazenado, mas isso não amplia automaticamente contrato nem domínio. Enquanto 008 não for entregue, contrato007 permanece vigente. Após entrega, snapshot008 substitui integralmente o contrato de consultas007; este continua histórico e intacto. O [README](contracts/README.md) define essa precedência.

**Alternatives considered**: deixar ambos como vigentes é ambíguo; alterar007 agora viola o escopo; acrescentar pagamento ao detalhe viola FR-017. A enumeração fechada pode exigir atualização de consumidores; isso será verificado antes da entrega. A referência a [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3.html) orienta validação estrutural, não comprova runtime.

## R8 — Testes e limites das simulações

**Decision**: testes futuros em H2 2.2.224 real, transações/conexões independentes, contextos isolados, HTTP real raiz e /api, e snapshots incluindo tb_payment. Não executar Maven neste planejamento. [Quickstart](quickstart.md) separa limites temporais, segurança, locks, constraints, rollback, commit e transmissão.

**Rationale**: fixture007 cria WAITING_PAYMENT e probe007 cobre sete tabelas, sem pagamento. Criar probe008 compondo o anterior com Payment e limpar somente linhas próprias, pagamento antes do pedido. Não enfraquecer testes005–007 nem mudar bancos/seed globais para contornar isolamento. StatementInspector não vê JDBC direto; comparar snapshots depois de respostas e antes da limpeza complementa a observação, mas não prova ausência de toda mutação transitória.

**Alternatives considered**: usar somente mocks ou descartar a resposta não prova SQL concorrente nem perda de transporte. beforeCommit não é falha no driver commit; lançar antes de delegar Connection.commit comprova somente não efetivação conhecida. Injeção controlada após commit não prova queda física de rede ou crash do banco.

## R9 — Ambiente efetivo

**Decision**: manter Java21, Boot3.3.4, Spring6.1.13, Hibernate6.5.3.Final e H2 2.2.224 gerenciados pelo [POM](../../pom.xml), sem dependência nova de aplicação. [Configuração principal](../../src/main/resources/application.properties) usa H2 em memória/create; [teste](../../src/test/resources/application-test.properties), create-drop. Wrapper3.9.16 preservado. PostgreSQL não está configurado nem é critério aprovado desta fase.

**Rationale**: testes em H2 não comprovam outro banco ou durabilidade de produção. Se outro banco for adotado futuramente, repetir schema, precisão, locks, constraints e commit nesse ambiente; modo de compatibilidade H2 não substitui essa prova.

Fonte de versões: [tag oficial Spring Boot 3.3.4](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.3.4/spring-boot-project/spring-boot-dependencies/build.gradle). Nenhum teste da aplicação, servidor, comando Maven ou chamada HTTP da 008 foi executado nesta pesquisa.
