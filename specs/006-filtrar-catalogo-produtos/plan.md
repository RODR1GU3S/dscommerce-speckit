# Implementation Plan: Filtrar catálogo público de produtos por nome

**Branch**: `006-filtrar-catalogo-produtos` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: especificação revisada em `specs/006-filtrar-catalogo-produtos/spec.md`, commit `c3caf26eb9936aea379908118ae44b1201ff1614`; [checklist](checklists/requirements.md) 16/16 critérios documentais. Esse estado não comprova implementação.

## Summary

Planejar a extensão pública de `GET /products` com `name` opcional: trecho literal sem distinguir caixa, distinguindo acentos; remover exclusivamente espaços externos do filtro e preservar internos, nomes armazenados e resposta. Paginação, contagem e ordenação `name ASC, id ASC` referem-se ao conjunto filtrado. Ausência/vazio mantém a consulta sem filtro; nenhuma correspondência é página vazia; detalhes por ID permanecem independentes.

Reaproveitar controller, serviço, repositório, DTOs e mecanismos de validação/erro existentes. Extrair o normalizador puro da consulta administrativa para uso dos dois serviços e renomear somente sua consulta filtrada no repositório para um nome neutro. Nenhuma entidade, tabela, dependência, migração ou configuração produtiva nova. As decisões e alternativas estão na [pesquisa](research.md); as regras de dados, no [modelo](data-model.md); a extensão pública, no [contrato novo](contracts/public-products-openapi.yaml).

## Technical Context

**Language/Version**: Java 21, conforme `pom.xml` e constituição.

**Primary Dependencies**: Spring Boot 3.3.4; starters existentes Web, Data JPA, Validation, Security, OAuth2 Resource Server/JOSE. Sem atualização ou dependência adicional.

**Storage**: H2 em memória com JPA/Hibernate já configurados; sem alteração de schema/collation. H2 padrão não estabelece garantia para outro banco não configurado.

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test/MockMvc e HTTP servlet real existentes. Maven Wrapper seleciona Maven 3.9.16. Todos os comandos de validação funcional deste plano são futuros; nenhum Maven executado neste checkpoint.

**Target Platform**: backend JVM atual; validação futura local Windows/PowerShell pelo Wrapper. CI existente em Ubuntu/Java 21 permanece independente; nenhuma execução remota é inferida.

**Project Type**: serviço web Java/Spring Boot em um módulo Maven.

**Performance Goals**: paginação no banco, sem carregar e filtrar o catálogo completo em memória. A especificação não define SLA, volume de produção ou meta nova de latência; não inventar esses limites. Não se promete uso de índice para substring/LOWER.

**Constraints**: padrões 0/12, size 1–50 e validações/binding existentes; nenhum limite/truncamento de filtro ou política administrativa de parâmetros. Não normalizar nomes, expor entidades, alterar detalhes/autorização ou executar escrita durante consulta.

**Scale/Scope**: 4 histórias, 27 cenários, 14 FR e 6 SC; uma extensão de listagem, um normalizador de consulta compartilhado e uma consulta filtrada já existente reutilizada. Não inclui tasks, implementação ou alteração de testes neste checkpoint.

## Constitution Check

*Avaliação antes da pesquisa e reavaliação após o desenho. PASS abaixo significa aderência do desenho aos critérios próprios da Feature 006, não comprovação de código ou testes ainda inexistentes.*

| Gate e critério da constituição | Antes da pesquisa | Após desenho | Fundamentação e obrigação futura |
| --- | --- | --- | --- |
| I — Java/Spring Boot | PASS de desenho | PASS de desenho | Preservar stack e dependências existentes; nenhuma tecnologia alternativa. |
| II — camadas | PASS de desenho | PASS de desenho | Controller faz binding/validação; serviço decide normalização/ramo e mapeia DTO; repositório faz consulta parametrizada. Normalizador puro serve aos serviços, sem controller ou repository decidirem negócio. |
| III — contratos por DTO, sem entidades expostas | PASS de desenho | PASS de desenho | DTOs públicos existentes inalterados; query escalar `name` não é entidade nem corpo de entrada novo. Entidades permanecem internas. Contrato da 006 explicita extensão sem editar 001–005. |
| IV — persistência e validação antes de negócio | PASS de desenho | PASS de desenho | Bean Validation e conversão de page/size permanecem na fronteira antes do serviço. `name` aceita qualquer texto, sem restrição nova a validar; normalização posterior, em serviço. JPA em transação de leitura, sem diagnóstico de escrita ou lookup durante validador novo. Modelo sem alterações. |
| V — tratamento centralizado e REST | PASS de desenho | PASS de desenho | GET público, sucesso inclusive vazio, erros de paginação e detalhe preservados; sem try/catch de controller ou handler novo. Conversão nativa será caracterizada antes/depois por HTTP real, sem impor envelope novo. |
| Backend Constraints — requisitos e abstrações necessárias | PASS de desenho | PASS de desenho | FR-001–014 e matriz dos 27 cenários sustentam o desenho. Uma extração pura e um nome neutro eliminam duplicação concreta; sem arquitetura genérica, estados ou infraestrutura adicional. |
| Workflow/Governance — fontes, revisão, evidências | PASS para iniciar pesquisa | PASS para encerrar planejamento | Spec/checklist commitados, pesquisa e desenho revisados; nenhum esclarecimento material em aberto. Provas funcionais permanecem futuras. Reavaliar aderência e registrar comandos/revisões/resultados na implementação. |

**Resultado da Feature 006**: nenhum gate de desenho falhou, nenhuma exceção constitucional nova foi requerida. Validação de implementação/runtime está pendente por definição deste checkpoint; não há aprovação funcional antecipada.

**Registro histórico preservado da Feature 005**: seu [plano](../005-manter-produtos/plan.md), [pesquisa](../005-manter-produtos/research.md) e [fechamento](../005-manter-produtos/quickstart.md) mantêm o Gate IV como **EXCEÇÃO DOCUMENTADA, sem PASS estrito**, pela execução de lógica diagnóstica de serviços durante `isValid` na escrita. A Feature 006 não usa esse caminho, não estende a exceção, não altera sua classificação nem a constituição. A extração limita-se ao normalizador de consultas, sem modificar a normalização/validação da escrita. Mudança futura que viole esses limites exige reabrir o gate correspondente.

## Project Structure

### Documentation (this feature)

```text
specs/006-filtrar-catalogo-produtos/
├── spec.md                            # existente, revisado/commitado, preservado
├── checklists/requirements.md          # existente, preservado
├── plan.md                            # planejamento
├── research.md                        # Phase 0
├── data-model.md                      # Phase 1, sem mudanças de persistência
├── quickstart.md                      # Phase 1, validação futura dos 27 cenários
└── contracts/public-products-openapi.yaml # Phase 1, extensão pública planejada
```

`tasks.md` não é produzido pelo planejamento e não foi criado. Os contratos históricos permanecem nos diretórios de suas features.

### Source Code (repository root)

```text
src/main/java/com/devsuperior/dscommerce/
├── controllers/ProductController.java              # futura entrada name
├── services/ProductCatalogService.java             # futuro ramo de filtro
├── services/AdminProductQueryService.java           # futuro uso de helper/método neutro
├── services/ProductNameFilter.java                 # helper puro proposto, inexistente hoje
├── repositories/ProductRepository.java             # renomear somente consulta filtrada
├── dto/                                            # DTOs existentes, sem mudanças de contrato
└── entities/                                       # inalteradas
src/test/java/com/devsuperior/dscommerce/
├── controllers/                                    # cenários públicos e regressões existentes
├── services/                                       # orquestração e compartilhamento
├── repositories/                                   # consulta/count/ordem existentes
├── fixtures/                                       # suportes existentes reutilizáveis
└── config/                                         # isolamento existente, sem mudar defaults globais
```

**Structure Decision**: manter o módulo e as camadas existentes. Os caminhos de produção/testes acima indicam impacto futuro, não arquivos implementados neste checkpoint. Manter configuração de segurança, handler de erros, DTOs e entidades sem mudanças, salvo defeito concreto posteriormente demonstrado e tratado dentro do escopo.

### Desenho de execução e responsabilidades

1. `ProductController.listProducts` recebe `name` como string opcional com nome explícito, mantendo integralmente as anotações e defaults de page/size; delega a `ProductCatalogService.listProducts(name, page, size)`. Não impor tamanho ou importar o parser administrativo.
2. O serviço público, em transação de leitura, normaliza somente o filtro usando `ProductNameFilter.normalize`. O helper extraído percorre pontos de código externos conforme `isWhitespace || isSpaceChar`; não converte caixa, acentos, espaços internos nem dados persistidos. O serviço administrativo mantém sua guarda ADMIN antes das consultas e usa o mesmo helper, sem alterar seus DTOs.
3. Filtro vazio usa `findAll(PageRequest.of(page, size, Sort name/id))`, exatamente o caminho público sem filtro. Filtro não vazio usa `findPageByName(filter, PageRequest.of(page, size))`, renomeado de `findAdminPageByName`, com seleção/count equivalentes e ordem fixa já existente. Não criar consulta duplicada ou alias intermediário.
4. Manter JPQL `locate(lower(:name), lower(p.name)) > 0` para seleção e count. Parâmetro vinculado, sem interpolação; `%` e `_` não são curingas. Não aplicar trim/LOWER à coluna para ordenar, fetch de coleção, DISTINCT compensatório ou pós-filtro em memória. `LOWER` atua apenas na comparação; não há garantia adicional de equivalência linguística/canônica.
5. Mapear os produtos para os DTOs públicos atuais e copiar metadados de `Page`. Retornar 200 inclusive para nenhum resultado ou página além do total. Não realizar save/delete/flush/lock/mutação; `readOnly` será acompanhado de observação DML e snapshots na validação futura.
6. Detalhes por ID mantêm serviço/repositório/DTO/404 existentes. A consulta anterior não cria estado de sessão. Segurança pública, administração, login e pedidos mantêm suas cadeias e contratos.

### Impacto intencional nos testes existentes

A extensão sucede exclusivamente a expectativa de `name` ignorado. A [pesquisa R6](research.md#r6--expectativas-anteriores-a-suceder-sem-reescrever-história) identifica dois testes HTTP com essa expectativa: `AdminProductPublicRegressionIntegrationTest.adminFilterAndCategoryIdentifiersDoNotChangePublicContract` e `AdminProductQueryIntegrationTest.shouldKeepThePublicCatalogNumericAndUnfilteredByTheAdministrativeNameParameter`. Atualizar seus resultados e nomes descritivos na implementação, mantendo formatos, tokens, contextos e demais assertions. Os documentos/evidências antigos não serão reescritos.

Adaptar futuramente chamadas/stubs de `ProductControllerTest` e `ProductCatalogServiceTest` à assinatura com `name`; referências de `AdminQueryServicesTest`, `AdminProductRepositoryTest` e `ProductRepositoryAdminQueryComponentTest` à renomeação neutra. Preservar as regressões sem filtro, de detalhe, administrativas e de segurança. Não criar overload apenas para evitar atualizar testes de uma assinatura interna.

### Validação planejada e estado das evidências

O [quickstart](quickstart.md) mapeia individualmente os 27 cenários a fixtures/oráculos, camadas de verificação e comandos futuros. Prevê RED funcional via HTTP antes de mudar assinatura/produção, caracterização de conversão inválida, GREEN focal, regressões do compartilhamento e suíte completa pelo Wrapper. Sem contagem rígida de SELECT, testes por reflexão ou assertions que apenas repitam a implementação.

O novo grupo público será isolado, com H2 sem seeds globais, dados commitados antes da chamada, produção/security reais e OSIV desativado no contexto focal. Reaproveitar suporte de fixture/snapshot/SQL existente; sem transação externa que mascare commit ou dirty checking. Não alterar níveis globais de Logback; banco isolado não isola logging global.

Os 2.614 testes anteriores mencionados no pedido são histórico, não validação da implementação da Feature 006 ainda inexistente. Este planejamento não os reexecuta, reconta, associa a novo HEAD nem converte em aprovação atual. Neste checkpoint, evidência atual é leitura/revisão documental e as verificações documentais registradas na entrega.

## Complexity Tracking

Nenhuma violação nova a justificar. A extração do normalizador resolve duplicação entre duas consultas; a renomeação neutra reaproveita predicado, count e ordem existentes. Sem camada, entidade, tabela, dependência ou mecanismo de autorização adicional. A exceção histórica da 005 permanece no registro próprio descrito no gate IV e não é justificativa para uma violação da 006.
