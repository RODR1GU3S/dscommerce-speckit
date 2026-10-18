# Feature Specification: Filtrar catálogo público de produtos por nome

**Feature Branch**: `006-filtrar-catalogo-produtos`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "Especificar a Feature 006 conforme a delimitação consolidada: filtro público opcional name por trecho literal do nome, sem distinguir maiúsculas/minúsculas e com acentos significativos; normalizar somente espaços externos do filtro; preservar dados armazenados, paginação, ordenação, resposta pública e detalhes por ID. Registrar a evolução intencional de name, anteriormente ignorado, sem reescrever as Features 001/005 nem antecipar implementação."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Encontrar produtos por trecho literal do nome (Priority: P1)

Como visitante ou consumidor autenticado, quero localizar produtos por parte do nome para reduzir o catálogo aos itens que procuro, sem precisar conhecer o nome completo.

**Why this priority**: A busca por nome é o comportamento ausente na consulta pública e entrega o valor principal desta feature.

**Independent Test**: Consultar um catálogo conhecido com filtros de nome e comparar os produtos encontrados com as correspondências esperadas, sem executar manutenção administrativa.

**Acceptance Scenarios**:

1. **Given** os produtos `Fone Azul`, `Telefone Preto` e `Cabo`, **When** o consumidor pesquisa `fone`, **Then** recebe somente os dois primeiros, por correspondência de trecho contínuo em qualquer posição do nome.
2. **Given** o mesmo catálogo sem alterações, **When** pesquisa `fone`, `FONE` e `FoNe`, **Then** recebe os mesmos produtos, na mesma ordem e com os mesmos totais.
3. **Given** os produtos `Café Especial` e `Cafe Claro`, **When** pesquisa `CAFÉ`, **Then** encontra somente `Café Especial`; ao pesquisar `cafe`, encontra somente `Cafe Claro`.
4. **Given** os produtos `Oferta 10%` e `Oferta 100`, **When** pesquisa `10%`, **Then** encontra somente `Oferta 10%`, pois o percentual é literal.
5. **Given** os produtos `Kit_A` e `KitXA`, **When** pesquisa `kit_`, **Then** encontra somente `Kit_A`, pois o sublinhado é literal.
6. **Given** os produtos `Fone Azul` e `Cabo`, **When** pesquisa o único caractere `f`, **Then** encontra `Fone Azul`, sem exigir tamanho mínimo adicional para o filtro.
7. **Given** um produto anterior cujo nome contém 81 letras `a` consecutivas e outro cujo nome contém somente 80, **When** pesquisa 81 letras `A` consecutivas, **Then** encontra somente o primeiro, sem rejeitar pelo comprimento, truncar o filtro ou corrigir os nomes anteriores.

---

### User Story 2 - Pesquisar com espaços sem modificar os nomes (Priority: P1)

Como consumidor, quero que espaços acidentais nas extremidades da pesquisa sejam desconsiderados e que os espaços pertencentes ao trecho e aos nomes continuem significativos.

**Why this priority**: A distinção entre normalizar a entrada e preservar o nome evita resultados inesperados e alteração dos dados exibidos.

**Independent Test**: Comparar consultas com ausência de filtro, filtro vazio, espaços externos e trechos com um ou dois espaços internos; conferir também o nome devolvido e a ausência de alteração dos dados.

**Acceptance Scenarios**:

1. **Given** os produtos `Fone Azul` e `Cabo`, **When** pesquisa ` FONE ` ou `FONE`, **Then** recebe os mesmos produtos e metadados.
2. **Given** um catálogo conhecido, **When** consulta sem `name`, com `name` vazio ou composto somente por espaços externos removíveis, **Then** recebe a mesma listagem pública sem filtro, com os mesmos parâmetros de paginação.
3. **Given** os produtos `Fone Azul` e `Fone  Azul`, **When** pesquisa `fone azul`, **Then** encontra somente o primeiro; ao pesquisar `fone  azul`, encontra somente o segundo.
4. **Given** um produto anterior com nome armazenado `"  Fone  Azul  "`, **When** pesquisa `" fone  azul "`, **Then** encontra esse produto e recebe o nome original `"  Fone  Azul  "`; pesquisar `"fone azul"` não o encontra. As aspas delimitam os exemplos e não integram os textos.
5. **Given** o trecho `FONE`, **When** suas extremidades contêm tabulação, espaço não separável (NBSP, U+00A0) ou espaço eme (U+2003), **Then** esses caracteres externos são removidos e o resultado equivale ao de `FONE`; se o filtro contiver somente esses espaços, equivale à ausência de filtro.
6. **Given** os nomes `Fone Azul` e `Fone[NBSP]Azul`, em que `[NBSP]` representa um único espaço U+00A0, **When** pesquisa `fone azul` e `fone[NBSP]azul`, respectivamente, **Then** cada pesquisa encontra somente a forma correspondente, sem converter ou compactar espaços internos.

---

### User Story 3 - Navegar pelos resultados filtrados e reconhecer sua ausência (Priority: P1)

Como consumidor, quero navegar somente pelas correspondências da pesquisa e reconhecer quando não existem resultados, usando informações de paginação coerentes.

**Why this priority**: A busca só é útil se os produtos e os totais descrevem o mesmo conjunto em todas as páginas.

**Independent Test**: Usar um catálogo estável com cinco correspondências e produtos não correspondentes, percorrer páginas de tamanho dois e conferir ordem, identificação, totais e páginas vazias.

**Acceptance Scenarios**:

1. **Given** cinco correspondências e outros produtos que não correspondem ao filtro, **When** solicita as páginas 0, 1 e 2 com o mesmo filtro e `size=2`, **Then** recebe respectivamente 2, 2 e 1 produtos correspondentes, sem perdas ou duplicações, com `totalElements=5` e `totalPages=3` em todas elas.
2. **Given** correspondências com nomes diferentes e duas com o mesmo nome e IDs distintos, **When** percorre os resultados com o mesmo filtro, **Then** recebe os produtos por nome crescente, com ID crescente como desempate, preservando a ordenação existente sobre os nomes armazenados.
3. **Given** um catálogo sem correspondências para o trecho informado, **When** consulta uma página válida, **Then** recebe sucesso com `content=[]`, `totalElements=0` e `totalPages=0`, mantendo `page` e `size` da solicitação; ausência de resultados não é produto inexistente nem erro de consulta.
4. **Given** cinco correspondências e `size=2`, **When** solicita `page=3` com o mesmo filtro, **Then** recebe `content=[]`, `page=3`, `size=2`, `totalElements=5` e `totalPages=3`.
5. **Given** um catálogo sem produtos, **When** consulta com ou sem filtro e paginação válida, **Then** recebe página vazia e totais zero, sem erro.
6. **Given** uma pesquisa que restringe resultados, **When** remove o filtro em uma nova consulta com a mesma paginação, **Then** recebe a página correspondente do catálogo completo; a pesquisa anterior não restringe implicitamente consultas seguintes.

---

### User Story 4 - Continuar usando o catálogo e os detalhes existentes (Priority: P1)

Como consumidor, quero continuar consultando o catálogo público e os detalhes de qualquer produto existente, independentemente da pesquisa realizada.

**Why this priority**: A extensão deve preservar as jornadas já entregues e mudar somente o comportamento delimitado do filtro público.

**Independent Test**: Consultar catálogo e detalhes com e sem filtro, conferir os dados públicos existentes, os padrões e rejeições de paginação e o acesso público sem credenciais válidas.

**Acceptance Scenarios**:

1. **Given** um catálogo com produtos, **When** consulta com ou sem filtro sem informar paginação, **Then** recebe `page=0` e `size=12`; quando o filtro estiver ausente ou vazio após remoção externa de espaços, mantém o resultado da consulta sem filtro existente.
2. **Given** uma consulta com ou sem filtro, **When** informa `size=1` ou `size=50` e página válida, **Then** o tamanho é aceito; `page=-1`, `size=0`, `size=51` ou valores não numéricos de paginação continuam rejeitados com 400 e a resposta de erro pública existente.
3. **Given** resultados públicos, **When** consulta com ou sem filtro, **Then** cada item contém somente `id`, `name`, `image` e `price`, com preço numérico; a página contém somente `content`, `page`, `size`, `totalElements` e `totalPages`, com os mesmos significados e tipos existentes.
4. **Given** um visitante sem token ou uma solicitação com token inválido irrelevante, **When** consulta o catálogo, inclusive com filtro, ou detalhes de produto existente, **Then** mantém acesso público e recebe os mesmos dados que receberia sem esse token.
5. **Given** um produto existente excluído dos resultados de uma pesquisa, **When** consulta seus detalhes por ID em `GET /products/{id}`, **Then** recebe os dados desse produto normalmente, sem depender de sua presença na página ou da pesquisa anterior.
6. **Given** um produto com categorias de nomes repetidos ou um ID inexistente, **When** consulta detalhes, **Then** o existente mantém `id`, `name`, `description`, `image`, preço numérico e nomes de categorias sem duplicação, e o inexistente mantém 404 e o erro público existente.
7. **Given** produtos anteriores com nome fora dos limites de escrita atuais, espaços nas extremidades, preço zero ou sem categorias, **When** consulta catálogo ou detalhes, **Then** eles continuam legíveis conforme as regras dessas consultas, sem validação retroativa impeditiva, correção automática ou alteração de valores.
8. **Given** consultas administrativas de produtos e jornadas existentes de login e registro de pedido, **When** são exercitadas após esta extensão, **Then** mantêm seus comportamentos e contratos; o filtro público não concede acesso administrativo nem modifica produtos, categorias, contas, pedidos ou valores históricos.

### Edge Cases

- Ausência de filtro, texto vazio e texto composto somente por espaços externos removíveis equivalem a nenhuma restrição por nome; isso não dispensa validar a paginação como antes.
- O trecho é contínuo: `fone azul` não equivale a `fone  azul`, nem significa procurar palavras independentes.
- `%` e `_` representam seus próprios caracteres; não selecionam produtos por expansão de curingas.
- Espaços externos pertencentes ao filtro são removidos; espaços existentes no nome armazenado não são removidos da comparação ou da resposta. Um filtro composto somente por espaços não pesquisa nomes por espaços.
- A normalização externa abrange os mesmos espaços da busca administrativa, incluindo tabulação, NBSP e espaço eme; não converte espaços internos.
- Não há limite de tamanho de negócio para o filtro nem truncamento; os limites de nome na escrita administrativa não são limites de pesquisa nem impedimentos à leitura de produtos anteriores.
- Nenhuma correspondência e catálogo vazio produzem totais zero; página além das correspondências mantém os totais do conjunto filtrado, mesmo com conteúdo vazio.
- Nomes iguais usam o desempate existente por ID. A busca não muda a ordenação para nome sem acentos, em minúsculas ou normalizado.
- Detalhes de produto existente continuam acessíveis por ID mesmo quando esse produto não corresponde ao filtro; ID inexistente continua sendo caso de detalhe não encontrado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE disponibilizar o filtro opcional `name` na consulta pública `GET /products`, para visitantes e consumidores autenticados, sem exigir autenticação ou ADMIN e sem passar a rejeitar token inválido irrelevante nessas consultas públicas.
- **FR-002**: Com filtro não vazio após normalização externa, o sistema DEVE selecionar somente produtos cujo nome contenha o trecho contínuo literal informado, em qualquer posição, sem distinguir maiúsculas/minúsculas e distinguindo acentos.
- **FR-003**: O sistema DEVE tratar `%`, `_` e os demais caracteres do trecho literalmente, sem interpretar curingas ou expressões de busca.
- **FR-004**: O sistema DEVE remover somente os espaços nas extremidades do filtro, com a mesma abrangência de espaços já adotada na busca administrativa, incluindo os exemplos da US2.5. DEVE preservar a quantidade, a posição e os caracteres dos espaços internos do trecho.
- **FR-005**: Filtro ausente ou vazio após essa remoção DEVE manter a consulta pública sem restrição por nome. Uma consulta anterior com filtro NÃO DEVE restringir implicitamente consultas seguintes; cada página de uma pesquisa usa o filtro informado na respectiva solicitação.
- **FR-006**: O sistema NÃO DEVE normalizar nomes armazenados para pesquisar ou apresentar resultados, além da comparação sem distinguir caixa definida em FR-002. DEVE conservar espaços, acentos e texto original devolvido e NÃO DEVE modificar os dados persistidos durante a consulta.
- **FR-007**: O sistema DEVE aplicar paginação e contagem ao mesmo conjunto filtrado, informando `page`, `size`, `totalElements` e `totalPages` coerentes com esse conjunto. Em um catálogo estável, percorrer as páginas com o mesmo filtro DEVE apresentar todas as correspondências, sem perdas ou duplicações.
- **FR-008**: O sistema DEVE preservar a ordenação existente `name ASC, id ASC`: nome armazenado crescente e ID crescente como desempate. A comparação do filtro NÃO DEVE introduzir nova ordenação por caixa, acentos ou relevância.
- **FR-009**: Sem correspondências ou sem produtos, o sistema DEVE retornar sucesso com conteúdo vazio e totais zero, conservando página e tamanho solicitados. Para página válida além dos resultados, DEVE retornar conteúdo vazio e preservar os totais do conjunto filtrado, sem ajustar a página solicitada.
- **FR-010**: O sistema DEVE manter paginação iniciada em zero, padrões `page=0` e `size=12`, tamanhos permitidos de 1 a 50 e rejeições públicas atuais de paginação inválida, com ou sem filtro. NÃO DEVE corrigir valores inválidos por causa da pesquisa nem adotar regras administrativas adicionais de parâmetros.
- **FR-011**: O sistema DEVE preservar a representação pública existente: itens somente com `id`, `name`, `image` e `price` numérico, e página somente com `content`, `page`, `size`, `totalElements` e `totalPages`. NÃO DEVE acrescentar descrição, categorias ou dados administrativos aos itens da listagem.
- **FR-012**: O sistema DEVE manter o contrato de detalhes da Feature 002, incluindo os dados do produto selecionado, nomes de categorias sem duplicação e resultado de inexistência. A consulta de detalhes por ID DEVE ser independente do filtro, das correspondências e da página do catálogo.
- **FR-013**: O sistema NÃO DEVE impor comprimento mínimo ou máximo de negócio ao filtro, truncá-lo, aplicar coerções novas ou transferir limites de escrita administrativa para a pesquisa. Produtos anteriores DEVEM continuar legíveis, sem validação retroativa impeditiva ou correção automática.
- **FR-014**: O sistema DEVE manter os comportamentos das consultas administrativas e das jornadas de login e registro de pedido. A consulta pública filtrada NÃO DEVE conceder permissões administrativas, escrever dados ou alterar valores históricos.

### Key Entities *(include if feature involves data)*

- **Produto do catálogo**: Produto existente identificado por ID, cujo nome é pesquisado e cujos nome, imagem e preço atuais são apresentados sem modificar o conteúdo armazenado.
- **Filtro de nome**: Trecho opcional informado pelo consumidor, com remoção exclusiva de espaços externos, que restringe a consulta segundo caixa, acentos, literalidade e espaços internos definidos nesta feature.
- **Página de catálogo**: Subconjunto ordenado das correspondências, acompanhado da página atual, tamanho solicitado, total de produtos correspondentes e total de páginas.
- **Detalhes do produto**: Visão existente de um produto identificado por ID, incluindo descrição e nomes de categorias, independente da pesquisa no catálogo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de busca por nome, o consumidor encontra exatamente os produtos esperados para trecho, caixa, acentos e caracteres literais, inclusive filtros de um caractere e superiores a 80 caracteres, sem correspondências introduzidas por truncamento.
- **SC-002**: Em 100% dos cenários de espaços e ausência de filtro, o consumidor obtém a seleção esperada, distinguindo espaços internos e mantendo integralmente os nomes originais apresentados.
- **SC-003**: Ao percorrer um catálogo estável com cinco correspondências em páginas de tamanho dois, o consumidor obtém as cinco uma única vez, em ordem consistente, nas quantidades 2/2/1, e reconhece cinco resultados distribuídos em três páginas.
- **SC-004**: Em 100% dos cenários sem correspondências, catálogo vazio ou página além dos resultados, o consumidor recebe uma página vazia com totais e posição coerentes, sem erro indevido ou substituição por outro produto.
- **SC-005**: Em 100% dos cenários de preservação do catálogo, permanecem acesso público, dados e formatos apresentados, padrões, limites e rejeições anteriores de paginação, com a única evolução intencional de passar a aplicar o filtro de nome definido nesta feature.
- **SC-006**: Em 100% dos cenários de detalhes e regressão das demais jornadas, o consumidor continua acessando por ID o produto existente, mesmo fora da pesquisa, reconhece a inexistência quando aplicável e mantém os comportamentos anteriores sem alteração dos dados consultados ou históricos.

## Assumptions

### Fontes e distinção entre PDF e decisões do recorte

- O levantamento anterior do documento de requisitos DSCommerce, reaproveitado sem nova análise integral do PDF, está registrado na [Feature 005, fontes e requisitos](../005-manter-produtos/spec.md#fontes-e-requisitos-do-documento-de-requisitos). Ele identifica o arquivo local `C:/Users/ronal/Downloads/Documento de requisitos DSCommerce.pdf` e registra, nas páginas 6–7, no contexto de **Manter produtos**, catálogo com busca por nome.
- Essa referência de negócio não é apresentada como definição explícita no PDF de filtro público opcional `name`, comparação por trecho sem distinguir caixa, acentos significativos, caracteres literais, tratamento de espaços ou metadados filtrados. A extensão pública e essas regras são decisões expressamente adotadas na delimitação desta feature.
- As regras finas acompanham a semântica vigente de busca da [Feature 005, FR-004–005 e US1](../005-manter-produtos/spec.md). A proposta antiga de ignorar acentos foi expressamente substituída naquela feature por distingui-los; esta feature mantém a decisão vigente.
- A remoção externa de espaços do filtro mantém a abrangência administrativa existente, incluindo os casos de US2.5, sem inventar outro conjunto de caracteres ou alterar os espaços dos nomes. Essa delimitação é decisão de compatibilidade, não requisito atribuído ao PDF.

### Relação com as entregas anteriores e mudança intencional

- A [Feature 001](../001-consulta-catalogo-produtos/spec.md) e seu [contrato de catálogo](../001-consulta-catalogo-produtos/contracts/products-openapi.yaml) entregaram a consulta pública paginada e não definiram `name`. No HEAD de origem `20495b487379e260a4c967823997bff872920dc9`, `name` era ignorado no catálogo público, inclusive quando preenchido com trecho sem correspondências.
- Esta feature estende intencionalmente esse comportamento: `name` não vazio após normalização passa a restringir produtos e totais. Preserva a consulta sem filtro, a ordenação, os formatos, as regras de paginação e o acesso público. Não declara o filtro público como já implementado nem classifica sua ausência anterior como defeito da Feature 001.
- A [Feature 005, FR-020 e US5.8](../005-manter-produtos/spec.md) exigiu que a introdução da busca administrativa não alterasse as consultas públicas. O [teste histórico correspondente](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java) inclui `adminFilterAndCategoryIdentifiersDoNotChangePublicContract` e registra `name` sem efeito no público.
- A Feature 006 sucede especificamente essa expectativa de `name` ignorado. As demais garantias de FR-020/US5.8 permanecem: isolamento administrativo, acesso público, representações e preservação das outras jornadas. A especificação, contratos e evidências anteriores permanecem como registro histórico de seus escopos; não são reescritos nesta etapa.
- A [Feature 002](../002-visualizar-detalhes-produto/spec.md) e seu [contrato de detalhes](../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml) continuam sendo a referência para detalhes por ID. O filtro é uma restrição da listagem, sem criar impedimento à consulta de outro produto existente.

### Dependências e limites

- A [constituição](../../.specify/memory/constitution.md) governa o projeto. Esta etapa especifica comportamento observável; solução, contratos novos ou revisados e organização da implementação pertencem a fases posteriores.
- Os exemplos usam produtos já existentes, inclusive dados anteriores às regras de escrita da Feature 005. Não autorizam criar ou editar produtos fora daquelas regras. A leitura não regulariza esses dados.
- As verificações de navegação consideram um catálogo estável durante a sequência comparada. Não se acrescenta garantia de fotografia imutável entre solicitações diante de alterações concorrentes.
- A comparação sem distinguir caixa mantém o significado já adotado na busca administrativa. Não acrescenta equivalência Unicode canônica, transliteração, remoção de acentos ou uma nova promessa de equivalência linguística.
- A ausência de limite de negócio para o filtro não é uma promessa de capacidade operacional ilimitada. Esta especificação não cria teto alternativo, truncamento ou coerção.
- Não há dúvida material bloqueante de negócio neste recorte. Os cenários são critérios para validação futura, não resultados de testes executados ou evidência de implementação.

### Fora do escopo

- Busca por categoria, preço, descrição ou disponibilidade; busca aproximada, palavras independentes, correção ortográfica, relevância, curingas e expressões regulares.
- Remoção de acentos, equivalência Unicode canônica, compactação ou conversão de espaços internos, normalização de nomes armazenados ou dos dados retornados.
- Limites novos de comprimento do filtro, truncamento, coerções novas, ordenação configurável e mudanças nos padrões, limites ou rejeições atuais de paginação.
- Transferir ao contrato público as regras administrativas que rejeitam parâmetros desconhecidos ou repetidos. Esta feature não redefine o tratamento público desses parâmetros.
- Escrita de produtos, gestão de categorias, usuários ou perfis, mudanças em login/pedidos, estoque, pagamentos, interface visual, migração ou correção de produtos anteriores.
- Planejamento, tasks, implementação, execução de testes, alteração dos contratos anteriores ou da constituição, implantação e publicação.
