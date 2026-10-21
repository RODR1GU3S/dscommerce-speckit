# Implementation Plan: Registrar pagamento

**Branch**: 008-registrar-pagamento | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

**Input**: especificação aprovada no HEAD 24a29a9c031fc554f2b6deb6a4b5c2fde708719d: 30 cenários, 22 requisitos e oito critérios de sucesso; [checklist](checklists/requirements.md) 16/16 após as correções. Estado inicial: raiz esperada, branch008, Git limpo e seleção local008.

**Estado**: planejamento exclusivo. Só documentos da 008 são gerados. Componentes e provas abaixo são futuros; nenhuma alteração de código/configuração/testes, contrato anterior, stage, commit, push ou Maven nesta fase.

## Summary

Acrescentar POST /admin/orders/{id}/payments para ADMIN atual quitar pedido de qualquer titular. Corpo somente occurredAt; confirmação201 somente orderId/occurredAt/status PAID. Capturar referência única no início da avaliação de negócio, antes de qualquer consulta/espera. Transação com lock pessimista na raiz, refresh, guarda atual e elegibilidade conjunta registra Payment e PAID integralmente, sem pagamento parcial ou cobrança externa. Repetição409 conserva registro original.

[Research](research.md) resolve decisões/alternativas e fontes; [modelo](data-model.md), [contratos](contracts/README.md) e [quickstart](quickstart.md) detalham desenho e validações futuras. O snapshot completo de consultas008 substituirá o contrato007 após entrega, ampliando somente os estados admitidos e documentação correspondente. O contrato007 continua vigente até lá e permanece histórico intacto.

## Technical Context

**Language/Version**: Java21, Spring Boot3.3.4; manter [pom.xml](../../pom.xml).

**Primary Dependencies**: MVC/Validation/Security/Data JPA/Jackson existentes; Spring6.1.13, Hibernate6.5.3.Final e H2 2.2.224 gerenciados pelo parent. Nenhuma dependência nova de aplicação.

**Storage**: H2 em memória/create na aplicação e create-drop nos testes atuais. Nova tb_payment com PK/FK order_id e occurred_at de precisão3; tb_order conserva colunas e admite PAID. Sem migração de produção ou banco externo presumido.

**Testing**: JUnit5, Spring Boot Test/MockMvc, HTTP real em porta aleatória, H2 isolado, JDBC independente e instrumentação de transação. Wrapper3.9.16 só em execução futura. Nesta fase, validação documental/OpenAPI/exemplos sem Maven.

**Target Platform**: backend JVM; Windows/PowerShell local e CI Linux configurada pelo [harness](../../docs/harness.md). Nenhuma execução funcional nova é inferida desses ambientes.

**Project Type**: web service REST, projeto Maven único em Controller/Service/Repository.

**Performance Goals**: serialização por pedido, não global; duas compras distintas devem concluir independentemente. Sem SLA, throughput ou quantidade comercial inventados. Lock só raiz; não bloquear produtos nem carregar preço atual.

**Constraints**: ADMIN atual; entrada estrita e validação na fronteira; referência única imutável; ocorrência sem perda em milissegundos; histórico exato; uma quitação; commit antes de construir sucesso HTTP; consultas próprias preservadas.

**Scale/Scope**: uma operação administrativa nova, duas consultas existentes evoluídas somente no enum; 30 cenários e regressões001–007. Sem gateway, meios de pagamento, comprovantes, parcial, parcelamento, estorno, cancelamento, entrega, relatório, consulta administrativa geral, carrinho ou interface.

## Constitution Check

Gate pré-pesquisa: confronto inicial com a [constituição1.0.0](../../.specify/memory/constitution.md). Pós-desenho: reavaliação com R1–R9 e os artefatos desta fase. **PASS abaixo é de desenho, não implementação ou teste aprovado.**

| Gate | Pré-pesquisa | Pós-desenho | Fundamento e obrigação futura |
| --- | --- | --- | --- |
| I — Java/Spring Boot | PASS de desenho | PASS de desenho | Stack e convenções existentes, sem framework novo. |
| II — camadas | PASS de desenho | PASS de desenho | HTTP no controller; referência, permissão, estado, dinheiro e transação no serviço; lock/refresh/consultas no repository. |
| III — DTOs | PASS de desenho | PASS de desenho | Entrada e confirmação específicas; sem entidades, comprador ou payment nos GETs. |
| IV — JPA e Bean Validation | PASS de desenho | PASS estrito de desenho da 008 | Relações deliberadas; @Valid/@NotNull e validação MVC do ID antes de serviço; reader/validator puros, sem IO de domínio. Regras dependentes de pedido/referência são posteriores no serviço. Testar fronteira e proxy. |
| V — REST/erros centralizados | PASS de desenho | PASS de desenho | POST resource-oriented, 201/400/401/403/404/409/500, handlers e advice scoped. |
| Requisitos/abstrações necessárias | PASS de desenho | PASS de desenho | Spec anterior ao desenho, sem novo requisito atribuído ao PDF; fragmento de lock e projeção histórica evitam cache antigo e preço atual. |
| Verificações pertinentes | PASS de estratégia | PASS de estratégia | Matriz abaixo e gruposV01–V10, com limites explícitos; nenhuma execução funcional. |

O **Gate IV da 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, conforme [plano005](../005-manter-produtos/plan.md). Seu diagnóstico em serviços durante isValid não é reutilizado. A 008 não redefine serviço como somente escrita para alegar conformidade: nenhum validator chama serviço/repository; segurança de infraestrutura precede MVC, validação local termina antes da avaliação de negócio. Não há exceção nova ou gate injustificado aberto. Conformidade efetiva depende da implementação/revisão futuras.

## Project Structure

### Documentation (this feature)

~~~text
specs/008-registrar-pagamento/
  spec.md                         existente, preservado
  checklists/requirements.md      existente, preservado
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/
    README.md
    payment-registration-openapi.yaml
    own-orders-openapi.yaml
~~~

Não gerar tasks.md nesta fase.

### Source Code (repository root)

Mudanças futuras, não realizadas:

~~~text
src/main/java/com/devsuperior/dscommerce/
  controllers/AdminPaymentController.java
  controllers/handlers/PaymentRegistrationExceptionHandler.java
  dto/PaymentRegistrationRequestDTO.java
  dto/PaymentConfirmationDTO.java
  services/PaymentRegistrationService.java
  services/PaymentAccessService.java
  services/exceptions/                 falhas específicas de pagamento
  repositories/PaymentRepository.java
  repositories/PaymentInsertRepository.java
  repositories/PaymentInsertRepositoryImpl.java
  repositories/OrderPaymentLockRepository.java
  repositories/OrderPaymentLockRepositoryImpl.java
  repositories/OrderRepository.java    fragmento e projeção histórica
  entities/Payment.java
  entities/Order.java                  transição restrita
  entities/OrderStatus.java            acrescentar PAID
  config/PaymentRequestJsonConfig.java
  config/SecurityConfig.java           cadeia scoped anterior a /admin/**
  security/                           entry point/denied de pagamento
src/test/java/com/devsuperior/dscommerce/
  controllers/ services/ repositories/ dto/ security/ config/ fixtures/
~~~

**Structure Decision**: manter projeto/pacote existentes. Nomes novos são alvos de implementação futura. Reutilizar ErrorResponseDTO, Clock, decoder/conversor JWT e projeção escalar de perfis. Não alterar configuração global de Jackson, OSIV, banco/seed global ou identidade das consultas007.

## Fluxo técnico e responsabilidades

1. Nova cadeia @Order(-1) cobre caminho /admin/orders/*/payments, todos os métodos, antes das cadeias0–3 existentes. JWT mantém assinatura/expiração/subject; conversor existente não toma roles do token como autoridade final. Handlers específicos adaptam falhas de resolução para500 e ausência de ADMIN para403; só401 tem WWW-Authenticate: Bearer. Não exigir CLIENT adicional.
2. Controller usa Long id @Positive, @Valid PaymentRegistrationRequestDTO(@NotNull Instant occurredAt) e rejeita qualquer query parameter. Reader Jackson exclusivo valida objeto/campo único/tipo/duplicatas/trailing JSON e gramática temporal de R3. Não chama serviço nem banco. Rejeições MVC têm advice scoped, inclusive validação de parâmetro/retorno. Erro de retorno é500, não400.
3. Controller sem transação externa chama proxy PaymentRegistrationService. Na primeira instrução do corpo, reference = clock.instant(), com precisão nativa; não reutilizar clock do JWT, recebimento ou commit. A referência fica em variável local e nunca é recapturada, mesmo após espera.
4. Serviço REQUIRED/READ_COMMITTED/rollbackFor Exception verifica conta/ADMIN por projeção escalar antes de pedido. Repository adquire PESSIMISTIC_WRITE na raiz por ID e refresh explícito, sem coleções/predicado de estado/titular. Repetir guarda efetiva depois da espera e antes de mutação. Perfil lido deve ser atual; não prometer serialização de revogação depois da última leitura.
5. Ainda sob lock: procurar Payment por order_id. Existente gera409 de repetição sem tocar ocorrência; sem pagamento e estado diferente de WAITING_PAYMENT gera409 incompatível. Pedido ausente404. Não reparar combinações incoerentes.
6. Comparar createdAt <= occurredAt <= reference como Instants. Fora da janela400, sem recapturar referência. Ler projeção escalar de itens históricos ordenada por position, sem Product; verificar agregado financeiro e calcular BigDecimal exato, escala2 e capacidade19,2, sem arredondar. Incoerência histórica é500, não ausência/pedido inválido do consumidor. Não validar preço/nome/categoria atuais.
7. Construir Payment com pedido gerenciado e occurredAt e inserir por PaymentInsertRepository usando EntityManager.persist, nunca merge/save de entidade com ID atribuído que possa sobrescrever pagamento existente. Aplicar Order.markPaid somente ao status. Sem cascade de remoção, novos itens ou substituição do agregado. Flush e criação de DTO dentro da transação; qualquer falha propaga para rollback, sem consulta/retry depois de erro do banco.
8. O proxy precisa possuir a transação física na entrada HTTP; retorno ao controller somente após commit. Testes não devem criar @Transactional externo. REQUIRED em outro chamador com transação externa não tem essa garantia; tal orquestração não faz parte da arquitetura planejada.
9. Controller constrói201 após o retorno. Perda de transmissão após commit preserva pagamento/PAID e novo POST é409; 500 ou ausência de resposta não são prova automática de rollback. Resultado indeterminado de commit não autoriza compensação ou sucesso presumido.
10. Advice/handlers mantêm quatro campos seguros. Adaptar exclusivamente a identificação desse caminho no fallback de GlobalExceptionHandler para preservar erros nativos405/406/415 e headers sem usar diagnóstico de produtos; não impor envelope próprio a corpo que a negociação não permite. Os dois contratos novos explicitam o alcance do schema.

## Persistência e concorrência

[Modelo](data-model.md) define tabela/relação/precisão. Locks até commit/rollback e PK(order_id) são complementares. Não usar locks JVM, lock global de tabela, SERIALIZABLE, NOWAIT ou SKIP LOCKED como solução da unicidade.

Duas chamadas válidas sem outras falhas no mesmo pedido: a segunda espera, relê e rejeita; exatamente um201/um409, um Payment e PAID com ocorrência vencedora. Para pedidos diferentes, ambas201 e vínculos exclusivos. Timeout/deadlock/pool indisponível é falha operacional500, não concorrência bem-sucedida; testes coordenados devem controlar timeouts, sem prometer SLA ou retry automático.

Banco garante PK, FK e NOT NULL. Equivalência entre estado e pagamento, janela temporal e valor integral são invariantes do serviço transacional; não atribuir ao schema garantias entre tabelas que ele não impõe.

## Contratos vigentes e evolução007

- Hoje: [contrato007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml), enum somente WAITING_PAYMENT; nenhuma operação008 existe.
- Alvo após entrega008: [payment-registration-openapi.yaml](contracts/payment-registration-openapi.yaml) para POST e [own-orders-openapi.yaml](contracts/own-orders-openapi.yaml), versão0.2.0, para consultas. O segundo substitui integralmente o contrato de consultas007 na documentação vigente, e não funciona como alternativa simultânea. [README](contracts/README.md) fixa precedência e preservação histórica.
- Não alterar contrato007 nesta fase ou reescrever sua spec. A implementação deve publicar/usar o snapshot008 como contrato atual das consultas; apontamentos/documentação e testes de schema precisam usar esse alvo, conservando007 como histórico.
- Campos, paths, titularidade/contagens, paginação/ordenação por criação-ID, erros, dinheiro e nomes atuais ficam iguais. Apenas admitir PAID, além de WAITING_PAYMENT, nos dois status; sem dados de pagamento nos GETs e sem privilégios de leitura alheia ao ADMIN.
- Acrescentar variantes PAID em OwnOrderQueryDtoContractTest, OwnOrderQueryRepositoryTest, OwnOrderQueryServiceTest, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryDetailHttpIntegrationTest e aliasesApi; estender ownershipUs34And35ForeignAndAbsentIdsHaveTheSame404EvenForAdmin em OwnOrderQuerySecurityHttpIntegrationTest e seu aliasApi com ADMIN que quitou a compra alheia. Não substituir HTTP real por MockMvc.
- Fixtures precisam de pagamento/estado coerentes; probe008 compõe probe007 e linhas de pagamento. Limpar Payment antes do pedido. Não falsificar PAID somente no manifesto nem ampliar record/probe005 de modo a quebrar seus usuários.

## Estratégia e rastreabilidade

GruposV são verificações futuras, não tasks ou execuções: V01 entrada/contrato; V02 tempo/precisão; V03 perfis atuais; V04 elegibilidade/histórico; V05 repetição/concorrência/constraints; V06 rollback/commit; V07 transmissão após commit; V08 consultas007; V09 regressões001–007; V10 conformidade/documentação. Detalhes e limites no [quickstart](quickstart.md).

| FR | Cenários | Critério | Grupos |
| --- | --- | --- | --- |
| 001 | US1.2; US2.1–3 | SC-001–002 | V01,V03 |
| 002 | US1.1–2; US5.2 | SC-001,007 | V03,V08 |
| 003 | US2.5,8 | SC-005 | V01 |
| 004 | US1.1,3; US2.8 | SC-001,006 | V04 |
| 005 | US1.1–2; US2.4,9; US3.1–3 | SC-001,004–005 | V04,V05 |
| 006 | US1.4–6; US2.5–7 | SC-003,005 | V01,V02 |
| 007 | US1.1–2,4–6 | SC-001,003 | V01,V02 |
| 008 | US1.1; US4.1–2 | SC-001,005 | V04,V06 |
| 009 | US1.3; US5.4,6 | SC-006–008 | V04,V08,V09 |
| 010 | US3.1–3; US4.3 | SC-004–005 | V05,V07 |
| 011 | US3.4–5 | SC-004 | V05 |
| 012 | US2.1–9; US3.1–4 | SC-002–005 | V01–V06 |
| 013 | US4.1–2,4 | SC-005 | V06 |
| 014 | US4.3 | SC-004–005 | V07 |
| 015 | US2.1–9; US3.1–4; US4.4 | SC-002,005 | V01,V03,V06 |
| 016–017 | US5.1 | SC-007 | V08,V10 |
| 018 | US5.2,4 | SC-007 | V03,V08 |
| 019 | US5.3–4 | SC-006–007 | V08 |
| 020 | US5.4,6 | SC-007–008 | V08,V09 |
| 021 | US5.1–5 | SC-007–008 | V08,V09 |
| 022 | US5.6 | SC-008 | V09 |

A notação US2.5,8 significa os cenários5 e8 da história2. Todos os 30 cenários estão cobertos; nenhum resultado histórico os aprova nesta revisão. Não criar classes vazias para obter seleção GREEN nem contar sucesso de upload/compilação como teste de comportamento.

## Complexity Tracking

Não há violação nova a justificar no desenho008. Guardas escalares, fragmento de lock/refresh, DTO/reader/advice scoped e snapshot contratual completo resolvem necessidades concretas. A exceção histórica005 permanece registrada acima, sem ser apagada ou apresentada como conformidade estrita.

## Estado de conclusão desta fase

Pesquisa e desenho resolvidos, sem decisão material bloqueante. Validações documentais efetivamente executadas e respectivos limites ficam no quickstart; implementação, schema gerado, SQL de lock, transações, HTTP e suíte seguem pendentes de comprovação. Nenhuma etapa de tasks ou implementação é iniciada por este plano.
