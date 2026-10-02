# Implementation Plan: Registrar pedido

**Branch**: `004-registrar-pedido` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Especificação commitada em `ed640d710e6ee4c3f1ad6beaef8c3006be8e7417`.

**Status**: Desenho concluído para revisão; código, tasks e verificações funcionais ainda não executados.

## Summary

Adicionar `POST /orders` para Cliente ou Admin registrar uma compra para sua própria UserAccount. Reutilizar JWT HS256 do login, resolver sub para a conta existente e obter perfis do banco. DTOs estritos recebem produtos/quantidades; OrderService verifica e grava Order/OrderItem em uma transação. Preço histórico BigDecimal e totais exatos compõem resposta 201. Erros usam o envelope existente, tanto na segurança quanto no MVC.

Cada envio válido cria novo pedido, inclusive após perda da resposta; sem idempotência. Carrinho permanece no consumidor. Pagamentos, consulta de pedidos, CRUD administrativo, cadastro completo, frontend e deploy ficam fora do desenho.

## Technical Context

**Language/Version**: Java 21; Maven 3.9.16 pelo Wrapper existente.

**Primary Dependencies**: Spring Boot 3.3.4, Web, Data JPA/Hibernate, Bean Validation, Jackson, Security e OAuth2 JOSE já presentes. Adicionar futuramente `spring-boot-starter-oauth2-resource-server`, gerenciado pelo Boot; sem atualização de stack ou Authorization Server.

**Storage**: H2 local/teste por JPA; schema derivado das entidades conforme configuração atual. Novas tabelas tb_order, tb_order_item e tb_user_account_role. Sem banco de produção ou migração de ambiente implantado.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Mockito e H2; integração com JWT/filtros reais e rollback sem transação de teste externa. Nenhum teste de aplicação executado no planejamento.

**Target Platform**: Serviço HTTP/JSON na JVM; desenvolvimento Windows e validação futura Linux pelo harness.

**Project Type**: Projeto Maven único, backend Spring Boot em camadas.

**Performance Goals**: Consultar apenas conta/produtos enviados, com lookup de ids em lote, sem carregar catálogo ou categorias. Uma gravação de agregado por solicitação. Sem SLO aprovado de latência/vazão.

**Constraints**: quantity `1..2147483647`; ids positivos Long; decimal19,2 com máximo `99999999999999999.99`, sem perda/arredondamento. Preço do pedido > 0. Saída monetária como strings de duas casas; APIs públicas preservadas. Segurança restrita ao POST novo. A2/A3 adotadas, A1/Q1 aprovadas, Product sem alteração.

**Scale/Scope**: Uma criação, quatro DTOs, agregado Order/OrderItem, dois perfis fixos na identidade existente, resolução autenticada, erros e testes. Sem teto comercial de quantidade/linhas, estoque, frete ou acréscimos.

## Constitution Check

Revisão de desenho contra [constituição 1.0.0](../../.specify/memory/constitution.md), antes da fase 0 e após a fase 1. PASS significa conformidade planejada, não teste ou entrega de código.

| Gate | Antes da pesquisa | Após desenho |
| --- | --- | --- |
| I — Java/Spring Boot | PASS: stack existente | PASS: Security/JPA, sem outra stack |
| II — Camadas | PASS: HTTP no controller; regra/transação no serviço | PASS: OrderService/AuthenticatedAccountService orquestram; converter adapta segurança; repositórios só acessam dados |
| III — DTOs | PASS: separar entrada/saída | PASS: quatro DTOs de pedido, principal mínimo, ErrorResponseDTO; sem entidades na API |
| IV — JPA/validação | PASS: modelar relações antes do código | PASS: constraints, Bean Validation aninhada no controller, leitura estrita e rollback integral definidos |
| V — Erros/REST | PASS: POST de recurso e centralização | PASS: 201 e 400/401/403/404/422/500; handlers de segurança e advice MVC |
| Requisitos antes do código / abstrações necessárias | PASS: FR-001–017 delimitam entrega | PASS: perfis mínimos/snapshot/testes; sem pagamento, carrinho ou usuário paralelo |

Não há violação constitucional. Reavaliar a implementação futura; checkbox não substitui evidência.

## Project Structure

### Documentation (this feature)

```text
specs/004-registrar-pedido/
  spec.md                         # existente, preservado
  checklists/requirements.md       # existente, preservado
  plan.md
  research.md
  data-model.md
  contracts/README.md
  contracts/orders-openapi.yaml
  quickstart.md
```

Não gerar tasks.md. [Pesquisa](research.md) registra decisões/racional/alternativas; [modelo](data-model.md), [contratos](contracts/README.md) e [quickstart](quickstart.md) completam fase 1.

### Source Code (repository root)

Layout existente a estender na implementação futura, não arquivos criados neste checkpoint:

```text
src/main/java/com/devsuperior/dscommerce/
  config/                       # SecurityConfig, JwtConfig, leitor de pedido
  security/                     # converter, principal e adaptadores de erro
  controllers/                  # OrderController novo
  controllers/handlers/         # GlobalExceptionHandler ampliado
  dto/                          # OrderRequestDTO, OrderItemRequestDTO,
                                # OrderResponseDTO, OrderItemResponseDTO
  entities/                     # UserAccount ampliada; UserRole, Order,
                                # OrderItem, OrderStatus novos
  repositories/                 # OrderRepository; UserAccountRepository ampliado;
                                # ProductRepository reutilizado
  services/                     # OrderService, AuthenticatedAccountService
  services/exceptions/          # InvalidOrderException e tradução de falhas
src/test/java/com/devsuperior/dscommerce/
  config/ controllers/ services/ repositories/ fixtures/
src/test/resources/              # fixtures/perfis e banco isolado
pom.xml                         # starter Resource Server gerenciado
```

**Structure Decision**: Projeto único e pacotes estabelecidos; security separa adaptação do filtro das regras de serviço. Reutilizar UserAccount e ProductRepository. Só OrderRepository grava o agregado; testes podem consultar itens sem criar API/repositório de escrita item a item.

## Fluxo e responsabilidades

1. Chain prioritária apenas para `POST /orders` lê Bearer e usa decoder HS256 existente. Converter exige sub/iat/exp e checagem temporal estrita da operação, resolve conta/perfis por serviço read-only; Cliente OU Admin autoriza. Fallback conserva login, catálogo, detalhes e H2 console, inclusive diante de Bearer inválido irrelevante.
2. Controller recebe principal transitório sem segredo e DTO com `@Valid`. Leitor específico rejeita coerções, campos extras e propriedades JSON repetidas; Bean Validation rejeita lista vazia/nulos/quantidades inválidas antes de OrderService.
3. OrderService via proxy transacional revalida accountId/perfis, rejeita repetidos e consulta ids em lote. Preço é o observado nessa leitura do registro. Produto ausente = 404; duplicata/preço inválido/valor não representável = 422.
4. Copiar snapshot, calcular subtotal/total exatamente e validar limites antes de salvar. Construir agregado WAITING_PAYMENT e instante UTC truncado a milissegundos. Posição persistida conserva ordem das linhas.
5. SaveAndFlush e mapeamento dentro de `@Transactional(rollbackFor = Exception.class)`, REQUIRED. Commit pelo proxy antes de retornar ao controller e emitir 201. Falha deve escapar, inclusive commit; sem REQUIRES_NEW por item ou self-invocation. Resposta perdida após commit segue Q1.

Não há locks/versionamento novo de catálogo: snapshot é o preço lido na transação, sem promessa do último preço no instante físico do commit. Depois da leitura/compra, alterações no produto não mudam snapshot. Subtotais e total derivam apenas de quantity/unitPrice persistidos.

Segurança resolve 401/403 e falha interna própria como 500, sem depender de ControllerAdvice para filtros. MVC resolve entrada/negócio/gravação e guardas tardias de identidade/permissão. Nunca expor causa interna, token, hash, chave ou valores recebidos. [OpenAPI](contracts/orders-openapi.yaml) fixa payload/códigos.

## Estratégia de testes e rastreabilidade

Todos os testes abaixo são **planejados**, não executados. Capturar regressões pelo Wrapper antes de mudar segurança. Quando aplicável, comprovar RED executável pela causa esperada, seguido de GREEN, regressões afetadas e suíte completa. Falha de compilação/preparação não comprova comportamento ausente.

| Critérios da spec | Cobertura planejada | Evidência exigida |
| --- | --- | --- |
| US1.1–4/8; FR-001/003/006/007; SC-001/005/007 | OrderSecurityIntegrationTest + OrderRegistrationIntegrationTest | JWT real vindo de login; Cliente, Admin isolado, ambos; 201/formato/conta/instante/estado/itens; linhas confirmadas após retorno |
| US1.5/6; FR-005/008/009; SC-002/003 | OrderServiceTest + integração | preço positivo alterado antes; snapshot depois; 10.00×2 + 7.50×3 = 42.50; round-trip exato |
| US1.7; FR-002/016 | OrderControllerTest | somente itens de entrada; confirmação sem promessa de carrinho esvaziado |
| US1.9/10; FR-017; SC-008 | OrderRegistrationIntegrationTest | dois POSTs iguais, descartando primeira resposta; ids distintos e primeira compra preservada |
| US2.1/2/7; FR-002/004/013 | OrderControllerTest + full-context | ausências/nulos/extras/propriedades JSON repetidas; fração/1.0/string/bool/overflow; 400 sem serviço/escrita |
| US2.3/8/11–13; FR-004/005/012/014; SC-004/009 | OrderServiceTest + banco | inexistente/repetido/zero/negativo ou quantity zero entre válidos; 404/422/400 e nenhuma linha nova |
| US2.4–6; FR-010/011 | OrderSecurityIntegrationTest | sem token, malformado, assinatura/chave/alg incorretos, expirado há poucos segundos, nbf/iat futuros, claims ausentes, sub inválido/conta ausente = 401; sem perfil = 403; roles falsos no JWT não autorizam |
| US2.9; FR-012; SC-004 | OrderRollbackIntegrationTest | falha SQL depois de inserts reais, falha de processamento após flush real e falha em beforeCommit na conclusão pelo interceptor; cenários distintos com leitura independente, rollback e ausência de 201 |
| US2.10; FR-008; SC-002/009 | serviço + integração | centavo mínimo, teto, quantity máxima, overflow subtotal/soma; 422 sem perda/gravação |
| US3.1–4; FR-015; SC-006 | suítes 001–003 + extensão pública | paginação/ordenação/shape/vazio/400, detalhe/categorias/404, login válido/inválido/500, confidencialidade/H2 SAMEORIGIN; sem header e com Bearer inválido |

OrderRepositoryTest deve cobrir round-trip de Instant/preço/quantidade, associação à UserAccount e Product, ordem por position e constraints de unicidade/FK/NOT NULL/positividade. O [modelo](data-model.md) define CHECKs explícitos via @Check no DDL, NOT NULL dos vínculos/perfis e coleção de itens não vazia no agregado; conferir restrições SQL com JDBC direto, sem confundir Bean Validation com prova de constraint. Testes JPA de mapeamento podem usar a transação padrão do teste; somente a prova de commit/rollback exige a integração sem essa transação externa.

Integração de segurança usa contexto/filtros/decoder reais, sem `jwt()`, `@WithMockUser` ou decoder mock. Claims especiais usam encoder real; adulteração/chave diversa cobrem falhas. Conferir contagens nas rejeições e 500 seguro quando resolução da conta falha internamente. Testar servlet context path /api e path externo no erro.

Exercitar a precedência com corpo inválido combinado com falha de autenticação/permissão/resolução (401/403/500 antes de 400) e entrada inválida antes de regras de negócio. Guardas tardias devem retornar 401 para conta removida e 403 para perfis revogados entre filtro e serviço, além de proteger chamada direta via proxy. Preço nulo deve gerar 422 sem save em fixture de serviço, preservando NOT NULL de Product. ProductId deve ter casos próprios de tokens incompatíveis em JSON bruto. A [matriz do quickstart](quickstart.md) detalha essas verificações futuras.

Rollback não usa `@Transactional` no teste nem transação envolvendo MockMvc. Fixtures são confirmadas antes. Constraint temporária no segundo produto força falha depois de SQL de pedido/primeiro item observado; spy de teste pode delegar flush real e falhar depois de confirmar inserts. Após saída do serviço, JDBC/conexão nova confronta baseline. Incluir sucesso persistido; limpeza não é prova; ids não precisam ser contíguos.

Separadamente da exceção de processamento após saveAndFlush, registrar sincronização exclusiva de teste na transação ativa e lançar em beforeCommit, deixando o corpo do serviço concluir normalmente. Provar callback alcançado durante a conclusão pelo interceptor, exceção propagada/500 sem 201 e baseline preservado em leitura independente. Não usar afterCommit como prova de rollback nem alegar falha física de conexão no commit; o [quickstart](quickstart.md) delimita o cenário.

Isolar contexto/banco para testes que mutam preços/constraints/contas. Preço negativo é fixture JDBC isolada, sem mudar/desativar validação de Product. Seed pública de 52 produtos e assertivas anteriores permanecem. Slices MVC com SecurityConfig recebem apoio local às novas dependências; não substituem integração real.

## Fases e saída

Fase 0: pesquisa técnica resolvida em research.md. Fase 1: modelo/contrato/guia e gate pós-desenho. Conferir hooks antes/depois conforme skill. Encerrar sem tasks, código, stage/commit, push ou merge.

## Pendências para revisão e limites

- Revisar escolhas técnicas de strings monetárias, Integer/decimal19,2, 422 e perfis enum. Estão resolvidas no desenho, sem dúvida funcional bloqueante.
- Provisionamento de perfis reais não é entregue; conjunto vazio nega pedido, sem conceder Cliente automaticamente. Fixtures só em recursos de teste.
- H2 permitirá prova local, sem alegação sobre outro banco/ambiente.
- Falhas de transporte/serialização depois do commit não desfazem compra; reenvio segue Q1.
- Verificações desta etapa e procedimentos futuros separados no quickstart.

## Complexity Tracking

Nenhuma violação constitucional. Principal transitório, perfis e adaptadores têm propósito direto; sem outro módulo implantável, usuário paralelo ou motor genérico de permissões.
