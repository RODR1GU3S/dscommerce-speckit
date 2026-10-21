# Implementation Plan: Consultar meus pedidos

**Branch**: `007-consultar-meus-pedidos` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Input**: especificação aprovada, 26 cenários, 18 requisitos e seis critérios de sucesso; [checklist](checklists/requirements.md) documental 16/16. Base inspecionada: `d69ef4690e2c684c540b12a7f304978671c3ba7a`, descendente da integração 001–006 em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

**Estado**: planejamento, sem implementação, tasks ou execução de testes. Componentes novos abaixo são desenho futuro; somente os identificados como existentes foram observados no checkout.

## Summary

Conta autenticada existente com CLIENT ou ADMIN consulta exclusivamente seus pedidos em lista paginada e detalhe. Acrescentar GET `/orders` e `/orders/{id}`, segurança específica, DTOs próprios, guarda de perfis atuais e consultas com titular obrigatório. Paginar IDs primeiro; carregar os agregados selecionados e produtos sem paginação de coleção; mapear dentro de transação de leitura. Reutilizar cálculos BigDecimal de quantidade/preço registrados e obter somente o nome atual do produto.

A [pesquisa](research.md), o [modelo](data-model.md), o [contrato](contracts/own-orders-openapi.yaml) e o [guia de validação](quickstart.md) detalham o desenho. PDF e decisões de apresentação permanecem separados na spec: pedidos próprios e preço da venda têm fonte documental; campos/paginação/nome atual foram adotados no recorte; escolhas HTTP/JPA deste plano são técnicas.

## Technical Context

**Language/Version**: Java 21; Spring Boot 3.3.4, conforme [pom.xml](../../pom.xml). Sem atualização de dependências.

**Primary Dependencies**: Spring MVC/Validation 6.1, Security 6.3/OAuth2 Resource Server JWT, Data JPA 3.3, Hibernate ORM 6.5 e Jackson, gerenciados pelo parent existente. Fontes compatíveis em research.md.

**Storage**: H2/JPA existente; pedidos/itens, produtos, contas/perfis. unit_price decimal19,2 e position existentes; nenhuma entidade, tabela, constraint, índice, snapshot ou migração novo requerido.

**Testing**: JUnit 5, Spring Boot Test, HTTP real em porta aleatória, H2 isolado e JDBC independente. Maven Wrapper 3.9.16 somente na implementação futura. Nesta fase: validação documental/contratual, sem Maven.

**Target Platform**: backend JVM; ambiente local Windows e CI configurada Ubuntu 24.04/Java 21. Descreve ambientes existentes, não valida execução da 007 neles.

**Project Type**: API REST em projeto único, arquitetura Controller/Service/Repository.

**Performance Goals**: paginação no banco e carregamento em lote, sem N+1 por pedido/item. Sem SLA/throughput inventado; não carregar todas as compras nem paginar collection fetch em memória.

**Constraints**: page 0/size 12 por omissão, size 1–50, page int32 não negativo, offset até 2147483647; ordem fixa createdAt DESC/id DESC. Conteúdo/count só do titular. Dinheiro textual positivo decimal19,2 exato. DTOs completos com OSIV false. Sem limite novo/truncamento de itens por pedido, mutações ou exposição sensível.

**Scale/Scope**: duas consultas, 26 cenários e regressões 001–006. Sem interface, pagamento, transições, administração de terceiros ou idempotência; sem fotografia imutável entre requisições concorrentes.

## Constitution Check

Pré-pesquisa: confronto do recorte com a [constituição 1.0.0](../../.specify/memory/constitution.md). Pós-desenho: revisão R1–R7/artefatos. **PASS significa conformidade do desenho, não código/testes aprovados.**

| Gate | Pré-pesquisa | Pós-desenho | Evidência/condição futura |
| --- | --- | --- | --- |
| I — Java/Spring Boot | PASS | PASS | Stack mantida; MVC, Security, Validation e JPA. |
| II — Controller/Service/Repository | PASS | PASS | HTTP/validação no controller; guarda/cálculo/mapeamento/transação no serviço; consultas no repository. |
| III — DTOs | PASS | PASS | DTOs próprios e página existente; sem entidade/proxy ou accountId na resposta. |
| IV — persistência/validação | PASS | PASS estrito de desenho | JPA deliberada; Bean Validation puro termina antes do serviço; sem diagnóstico de negócio em isValid. Segurança de infraestrutura precede MVC. |
| V — erros centralizados/REST | PASS | PASS | GET de recursos; handlers/advice escopados; status seguros e 404 sem confirmação de terceiro. |
| Requisitos antes de implementação | PASS | PASS | Spec/checklist commitados; decisões técnicas dentro do recorte; rastreabilidade integral. |
| Verificações pertinentes | PASS | PASS de estratégia | Provas futuras de API, serviços/JPA, dinheiro, ausência de escrita, classes aninhadas e regressões; não executadas. |

O **Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, conforme seu [plano](../005-manter-produtos/plan.md). Diagnóstico em serviços durante isValid daquela feature não é reutilizado nem reclassificado. Na 007, validação de entrada termina antes da guarda de negócio. Sem exceção constitucional nova ou bloqueio de planejamento; conformidade efetiva depende de implementação/revisão posteriores.

## Project Structure

### Documentation (this feature)

```text
specs/007-consultar-meus-pedidos/
├── spec.md                         # existente, preservado
├── checklists/requirements.md      # existente, preservado
├── plan.md
├── research.md
├── data-model.md
├── contracts/own-orders-openapi.yaml
└── quickstart.md
```

Não produzir tasks.md nesta fase.

### Source Code (repository root)

```text
src/main/java/com/devsuperior/dscommerce/
├── controllers/                    # novos OwnOrderQueryController e
│                                   # OwnOrderPageArgumentResolver (entrada da lista)
│   └── handlers/                   # novo OwnOrderQueryExceptionHandler
├── dto/                            # novos OwnOrderPageRequestDTO,
│                                   # OwnOrderSummaryDTO, OwnOrderDetailDTO,
│                                   # OwnOrderItemDTO; página existente
├── services/                       # novo OwnOrderQueryService e guarda de leitura
├── security/                       # adaptador JWT e handlers de leitura
├── config/SecurityConfig.java      # futura cadeia GET/HEAD
├── repositories/OrderRepository.java # novas consultas com titular
└── entities/                       # preservadas
src/test/java/com/devsuperior/dscommerce/
├── controllers/                    # HTTP raiz e /api
├── services/                       # regras, erros e mapeamento
├── repositories/                   # paginação, count e fetch
├── config/                         # isolamento explícito dos contextos 007
└── fixtures/                       # setup/limpeza de pedidos e snapshots
```

**Structure Decision**: estender o projeto existente sem módulo novo. Componentes novos são propostas, não arquivos criados. Reusar conversor JWT, projeção escalar de perfis, cálculos das entidades e PageResponseDTO. Sem mudar DTOs/contratos anteriores, entidades, JSON público, OSIV global ou perfil global de teste.

## Fluxo técnico futuro

1. Cadeia exclusiva @Order(0) cobre GET/HEAD `/orders` e `/orders/*`, inclusive ID malformado; preserva cadeias 1/2/3 existentes. Decoder/conversor existente mais adaptador de autoridades escalares atuais. CLIENT ou ADMIN, sem privilégio sobre terceiros. HEAD acompanha GET sem corpo, não constitui operação nova.
2. Entrada de listagem: resolução HTTP exclusiva de `@Valid OwnOrderPageRequestDTO` seleciona somente nomes exatos `page` e `size` do mapa decodificado pelo servlet, preservando todas as ocorrências, antes de qualquer construção/binding ou transformação de nomes do binder. Rejeita repetição desses nomes antes da conversão e fornece apenas o mapa filtrado ao binder MVC, sem binding automático posterior da requisição original por `@ModelAttribute`. DTO com construtor sem argumentos conserva padrões 0/12 somente por omissão; conversão e Bean Validation configurada pelo Spring são enforced na fronteira, antes do serviço. Binder limitado a page/size é proteção complementar. Desconhecidos, inclusive `_page`, `_size`, `size[]` e `!page`, são descartados antes de poderem alterar campos ou provocar validação; não escolhem dono/sort. Resolver/validator não chama serviço/repositório. Sem alteração de binder global ou política de outras rotas. ID do detalhe continua Long positivo. Advice escopado diferencia entrada inválida (400) de falha interna/de retorno (500). O ajuste P2 é documental, identificado estaticamente, com comprovação HTTP futura.
3. Serviço readOnly revalida principal, existência/perfis por projeção escalar antes de ler pedidos. Titular exclusivamente principal.accountId(). Conta ausente/credencial inválida 401, sem perfil 403, lookup falho 500. Não prometer serialização de revogação concorrente após a leitura.
4. Lista: página de IDs/count com mesmo titular, sem coleção; fetch não paginado dos selecionados com itens/produtos e titular novamente; montagem pela ordem dos IDs. Empty mantém page/size/totais próprios; zero páginas se zero elementos; sem clamp. Limite de offset é validado antes da consulta. Overflow dos metadados não é saturado silenciosamente.
5. Detalhe: ID AND titular com itens/produtos; ausente/alheio mesmo 404, sem exists global. position ASC preserva ordem dos itens. Left fetch conserva raiz para distinguir agregado incoerente de ausência; não carregar categorias/perfis do comprador.
6. Serviço calcula pelo modelo existente, formata escala exata e mapeia DTOs antes de retornar. Nome é atual integral; nenhuma validação retroativa do produto. Estado é armazenado; hoje só WAITING_PAYMENT. Sem transição, save, flush, clear ou correção.
7. Erros fixos no contrato, path sem query. 500 para lookup/leitura/cálculo/mapeamento/conclusão transacional, sem vazio/404/parcial. Preservar semântica de erros nativos de método/negociação. Nenhum acesso lazy após o serviço.

## Rastreabilidade e testes futuros

| Requisitos | Desenho | Cenários | Resultado observável |
| --- | --- | --- | --- |
| FR-001–002 | R3/R4 | US1.7; US3.1–6 | CLIENT, ADMIN e ambos só próprios; claims/parâmetros não ampliam acesso. |
| FR-003 | R1/R2 | US1.1–2 | Resumo quatro campos e metadados próprios. |
| FR-004 | R1/R4 | US2.1–2, US2.4 | Detalhe quatro campos e itens cinco campos na posição original. |
| FR-005 | R4/R5 | US2.3, US2.5 | Nome atual integral, sem revalidar produto para ler. |
| FR-006–008 | R5 | US2.1, US2.3, US2.6–7; US4.2 | Histórico exato; lista/detalhe concordam; catálogo numérico preservado. |
| FR-009–010 | R2/R4 | US1.1–3, US1.7 | Conteúdo/count próprios, páginas 2/2/1 e desempate por ID. |
| FR-011–013 | R2/R4/R6 | US1.4–6 | Vazios coerentes e entrada inválida distinta. |
| FR-014–015 | R3/R4/R6 | US1.6; US3.2–7 | 401/403/400/404/500 seguros, sem vazamento/falso sucesso. |
| FR-016 | R4/R7 | US4.1, US4.4 | Snapshots íntegros e zero DML na janela de leitura. |
| FR-017 | R3/R5/R7 | US4.2–5 | Contratos/asserções anteriores preservados. |
| FR-018 | R4/R5 | US2.1; US4.1 | Somente estado armazenado. |

O quickstart enumera os 26 cenários e SC-001–006. Casos técnicos adicionais: defaults/limites/offset, repetidos/desconhecidos, HEAD, context path, ordem da validação e perfis concedidos/revogados com coleção gerenciada obsoleta. OSIV false prova materialização no serviço; falhas injetadas não substituem provas reais de sucesso/JPA. URLs H2 exclusivas e estáveis por contexto, preservando correções da 006, seeds, create-drop e asserções.

## Complexity Tracking

Sem violação nova. Cadeia/DTOs de leitura isolam proteção, erros e campos. Lookup escalar após o conversor evita duplicação do parser rigoroso e mantém perfis atuais; guarda adicional protege o serviço. Duas etapas de consulta evitam collection fetch paginado e reutilizam cálculos existentes. Sem abstrações de pagamento, snapshots, novo domínio ou relatório.

## Contratos anteriores preservados

| Fonte | Fronteira preservada |
| --- | --- |
| [001 — catálogo](../001-consulta-catalogo-produtos/contracts/products-openapi.yaml) | Campos públicos e preço numérico, metadados de paginação; extensão de nome regida pela006. |
| [002 — detalhe](../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml) | Consulta pública por ID, produto/categorias e erros existentes. |
| [003 — login](../003-login-autenticacao/contracts/login-openapi.yaml) | POST login por nome/senha, emissão e erros; credenciais exatas preservadas. |
| [004 — registro](../004-registrar-pedido/contracts/orders-openapi.yaml) | POST, JSON estrito, confirmação201 com accountId, dinheiro textual, atomicidade e novo pedido por envio válido. Não acrescentar Location ou mudar o contrato antigo por existir GET novo. |
| [005 — administração](../005-manter-produtos/contracts/product-maintenance-openapi.yaml) | Rotas/regras administrativas, proteção de produto referenciado e erros; sem acesso administrativo a compras alheias. |
| [006 — filtro público](../006-filtrar-catalogo-produtos/contracts/public-products-openapi.yaml) | Busca literal por trecho, espaços internos/acentos, paginação/count/ordem e comportamento público com Bearer inválido irrelevante. |

## Resultado e limites

Fases 0 e 1 documentadas; sem dúvida de negócio bloqueante. Validações atuais e procedimentos futuros estão separados no quickstart. PASS documental/OpenAPI válido não demonstra comportamento em execução. Sem testes Maven, stage, commit ou push. Spec/checklist, seleção local e histórico preservados.
