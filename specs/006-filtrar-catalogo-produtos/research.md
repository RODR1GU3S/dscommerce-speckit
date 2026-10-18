# Research: Filtrar catálogo público de produtos por nome

**Data**: 2026-10-18 | **Base inspecionada**: `c3caf26eb9936aea379908118ae44b1201ff1614` | [Spec](spec.md) | [Plano](plan.md)

Pesquisa por leitura do repositório e documentação primária, com agentes de pesquisa para consulta/reaproveitamento e validação/isolamento, conforme a skill de planejamento. Decisões abaixo são de desenho futuro. Nenhum Maven, teste de aplicação ou implementação foi executado neste checkpoint.

## R1 — Fronteira pública e validação existente

**Decision**: acrescentar `@RequestParam(name = "name", required = false) String name` ao método público de listagem e encaminhar o texto ao serviço. Conservar `@Validated`, `int page`, `int size`, seus `defaultValue`, `@Min` e `@Max`. Não criar DTO de entrada de corpo para um parâmetro escalar de consulta, nem importar `AdminQueryParameters`.

**Rationale**: [ProductController](../../src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java) já define a fronteira e a validação pública de paginação. A ausência de `name` chegará como `null`; vazio continuará texto vazio. A normalização de negócio permanece no serviço, depois da validação de paginação. Nenhum comprimento ou outra restrição de validade de `name` foi solicitado. Parâmetros de consulta escalares não expõem entidades JPA.

**Alternatives considered**: normalizar no controller deslocaria decisão de negócio; usar o parser administrativo transferiria rejeições de desconhecidos/repetidos e conversões diferentes; alterar validação global ou impor tamanho ao filtro ampliaria o escopo. Sem nova política pública para parâmetros desconhecidos/repetidos.

Fonte: [RequestParam, Spring Framework 6.1.13](https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/web/bind/annotation/RequestParam.html). A referência explica `required=false` e os padrões; não propõe atualização de dependências.

## R2 — Normalizador compartilhado, puro e limitado à busca

**Decision**: extrair o algoritmo privado de `AdminProductQueryService.trimFilter` para `services/ProductNameFilter`, classe pequena, sem estado, package-private, com operação estática `normalize(String)`. Ambos os serviços de consulta a utilizarão. `null` resulta em texto vazio. Percorrer pontos de código nas duas extremidades enquanto `Character.isWhitespace(cp) || Character.isSpaceChar(cp)`; devolver o trecho remanescente sem compactação, conversão de caixa ou normalização Unicode.

**Rationale**: [AdminProductQueryService](../../src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java) já concretiza a semântica aprovada: a união cobre também NBSP, que `isWhitespace` isolado não cobre. A extração reduz duplicação real entre duas consultas. A classe não faz acesso ao banco, validação de escrita, autorização, cache ou mutação. Nome armazenado e resposta nunca passam pelo normalizador.

**Alternatives considered**: `trim()`/`strip()` isolados não reproduzem a união existente; copiar o algoritmo criaria duas regras divergentes; um bean, framework de normalização ou pacote genérico de utilitários seria excessivo. Não alterar `ProductInputValidationService` nem o normalizador da escrita administrativa: o reaproveitamento é restrito aos serviços de consulta.

Fontes: [Character.isWhitespace, Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html#isWhitespace(int)), [Character.isSpaceChar, Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html#isSpaceChar(int)).

## R3 — Consulta literal e reaproveitamento sem autorização administrativa

**Decision**: renomear apenas `ProductRepository.findAdminPageByName` para `findPageByName`, mantendo sua consulta JPQL parametrizada, `countQuery` e ordenação. Público e administrativo chamarão esse método neutro. Predicado de seleção e contagem: `locate(lower(:name), lower(p.name)) > 0`; ordem: `p.name ASC, p.id ASC`. Não carregar categorias na página. A consulta administrativa sem filtro `findAdminPage` permanece como está.

**Rationale**: [ProductRepository](../../src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java) já contém essa consulta. `LOCATE` pesquisa substring, sem interpretação de `%`, `_`, barras ou apóstrofos como instruções. `LOWER` nos dois operandos permanece no mesmo banco. A preservação de acentos é a inferência do desenho já adotado sobre o H2 configurado, a confirmar pelos cenários futuros; não há remoção de diacríticos, alteração de collation ou equivalência Unicode canônica. O nome neutro descreve persistência compartilhada, sem chamar serviço/guarda/DTO administrativo.

**Alternatives considered**: duplicar JPQL/count aumentaria divergência; manter o nome administrativo para uso público confundiria responsabilidades; um alias novo apenas delegando à consulta antiga acrescentaria intermediação desnecessária. `LIKE` com escapes, regex, full text ou filtragem de todo o catálogo em memória não são necessários. Não renomear consultas de detalhe, lock ou outras operações administrativas.

Fontes: [Jakarta Persistence 3.1, funções de strings, §4.6.17.2.1](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html), [H2 LOCATE](https://h2database.com/html/functions.html#locate), [H2 LOWER](https://h2database.com/html/functions.html#lower). `LOWER` depende do banco; não se promete casefold linguístico universal.

## R4 — Página, DTOs e ausência de mutações

**Decision**: evoluir a assinatura pública para `ProductCatalogService.listProducts(String name, int page, int size)`. Depois de normalizar, filtro vazio usa o `findAll(PageRequest)` público existente com `Sort name ASC, id ASC`; filtro preenchido usa `findPageByName` com paginação sem ordem dinâmica adicional, pois a consulta fixa a mesma ordem. Manter `@Transactional(readOnly = true)` e o mapeamento para `ProductCatalogItemDTO` e `PageResponseDTO` atuais, incluindo os metadados de `Page`.

**Rationale**: [ProductCatalogService](../../src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java) já mantém a transação e a conversão fora do repositório. [ProductCatalogItemDTO](../../src/main/java/com/devsuperior/dscommerce/dto/ProductCatalogItemDTO.java) preserva `getName()` e preço numérico; não há razão para entidade, tabela, migração, dependência ou representação nova. Página sem correspondências e página posterior usam os mesmos mecanismos de `Page`, com totais correspondentes ao filtro. Não presumir execução física obrigatória de count em toda página: o provedor pode otimizar sem alterar o resultado.

**Alternatives considered**: aplicar o filtro depois da paginação produz conteúdo/totais incorretos; ordenar por `lower(name)` altera a ordem histórica. `readOnly` é intenção/otimização, não barreira universal contra escrita: não chamar `save`, `flush`, `replaceDetails`, locks, manutenção ou setters; observar futuramente zero DML e snapshots independentes. Não há estados ou eventos novos.

Fonte: [Spring Data JPA, transações](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), usada para a distinção entre transação de leitura e garantia de ausência de escrita, sem mudar as versões do projeto.

## R5 — Segurança, detalhes e erros preservados

**Decision**: não alterar `SecurityConfig`, `findProductDetails`, consulta de categorias ou DTO de detalhes. Preservar o fallback público sem processamento Bearer. Reutilizar a validação e o tratamento de erros atuais de paginação; não implementar tratamento novo de erro em razão de `name`.

**Rationale**: [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) separa pedidos, administração e fallback público. [GlobalExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java) produz os quatro campos públicos para violações Bean Validation, mas relança erros nativos 4xx públicos para MVC/servlet. Os testes atuais comprovam expectativas de limites numéricos, enquanto a conversão não numérica requer comparação HTTP futura com a base. O novo contrato não deve fingir uniformização já existente.

**Alternatives considered**: resource server global rejeitaria token irrelevante; chamar serviço administrativo exigiria ADMIN e mudaria preço/categorias. Uniformizar agora corpos de erros nativos mudaria comportamento fora do recorte. No contrato, 400 de limites mantém a referência `ErrorResponse`; falhas nativas de conversão são explicitamente preservadas sem prometer o mesmo corpo. A caracterização futura do corpo nativo não é requisito de negócio indefinido nem autorização para o alterar.

**Limite do schema**: a alternativa aberta de erro nativo torna a união `anyOf` de 400 permissiva. Sua validação isolada não impõe os quatro campos a erros de limite; esses casos devem ser conferidos diretamente contra `ErrorResponse`, inclusive nos exemplos documentais e nos testes HTTP futuros. A descrição do contrato distingue essa limitação de representação de uma alteração de comportamento público.

## R6 — Expectativas anteriores a suceder, sem reescrever história

**Decision**: na implementação futura, atualizar especificamente estes testes ativos, sem remover suas outras garantias:

| Teste existente | Expectativa atual | Evolução planejada |
| --- | --- | --- |
| `AdminProductPublicRegressionIntegrationTest.adminFilterAndCategoryIdentifiersDoNotChangePublicContract` | Quatro consultas, inclusive `name`, retornam Alpha e Beta | Sem filtro: ambos; `ALP`: somente Alpha; `does-not-exist` e `%_`: vazio. Manter anonimato/token inválido, preço numérico e categorias públicas deduplicadas versus IDs administrativos. |
| `AdminProductQueryIntegrationTest.shouldKeepThePublicCatalogNumericAndUnfilteredByTheAdministrativeNameParameter` | `name=fone` percorre seis itens sem filtro | Renomear o teste para descrever a extensão pública, esperar cinco itens filtrados em páginas 2/2/1/0, totais 5/3. Manter contextos raiz e `/api`, anonimato/token inválido e campos públicos. |

**Rationale**: [spec, evolução intencional](spec.md#relação-com-as-entregas-anteriores-e-mudança-intencional) sucede somente `name` ignorado. Os documentos/contratos/evidências históricos 001–005 não serão alterados. As referências técnicas de `ProductControllerTest` e `ProductCatalogServiceTest` precisam acompanhar a nova assinatura, e `AdminQueryServicesTest`, `AdminProductRepositoryTest` e `ProductRepositoryAdminQueryComponentTest` a renomeação do método, sem enfraquecer assertions. `AdminQueryServiceComponentIntegrationTest` e `AdminProductQueryIntegrationTest` cobrem a extração compartilhada.

**Alternatives considered**: manter testes com expectativa incompatível impediria a evolução; apagar o teste inteiro eliminaria regressões úteis; reescrever a documentação anterior adulteraria o histórico. Este checkpoint não altera qualquer teste ou documento anterior.

## R7 — Validação e limites de evidência

**Decision**: usar os 27 cenários no [quickstart](quickstart.md), com fixtures commitadas, H2 isolado, contexto real, oráculos fixos independentes da consulta e regressões de compartilhamento. Aproveitar `AdminProductTestConfig`, `AdminProductTestFixture.prepareV03` e `AdminProductDatabaseProbe`, sem mudar seeds/configuração global. Executar futuramente RED por HTTP com assinatura atual, GREEN focal, regressões afetadas e suíte completa.

**Rationale**: os suportes existentes isolam banco por contexto e distinguem preparação, janela de consulta e limpeza. A exceção constitucional IV da Feature 005 refere-se ao diagnóstico de escrita durante validação e permanece histórica, sem ser importada ao fluxo público. Os gates desta feature são avaliados no seu desenho, com comprovação de implementação ainda pendente. Os 2.614 testes anteriores mencionados no pedido não foram reexecutados ou auditados aqui e não validam código ainda inexistente da Feature 006.

**Alternatives considered**: seeds globais de 52 itens, IDs absolutos, mocks como única prova ou contagem exata de SELECT são frágeis. Snapshot sozinho não prova zero DML; `readOnly` sozinho tampouco. H2 não prova comportamento em bancos não configurados, desempenho, durabilidade ou estabilidade durante mutações concorrentes.

## Resolução da pesquisa

Nenhuma dúvida material de desenho permanece. Fases futuras devem comprovar seleção, envelopes preservados, isolamento e ausência de mutações; essa validação pendente não é tratada como execução aprovada neste planejamento. Fontes do PDF e decisões de negócio permanecem delimitadas exclusivamente na spec revisada.
