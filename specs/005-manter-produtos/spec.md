# Feature Specification: Manter produtos

**Feature Branch**: `005-manter-produtos`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "Criar somente a especificação da Feature 005 — Manter produtos: inclusão, atualização integral e exclusão por ADMIN autenticado; busca administrativa por trecho literal do nome, ignorando maiúsculas/minúsculas e distinguindo acentos; categorias existentes com consulta auxiliar de leitura; validação integral e preservação de produtos anteriores, contratos públicos e valores históricos das compras."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Localizar produtos e selecionar categorias (Priority: P1)

Como Admin autenticado, quero localizar produtos pelo nome e identificar categorias existentes para selecionar corretamente o produto e suas associações durante a manutenção.

**Why this priority**: Localização e seleção apoiam cadastro e edição sem exigir manutenção de categorias nem mudar as consultas públicas.

**Independent Test**: Com produtos e categorias previamente existentes, realizar consultas administrativas com e sem filtro, identificar categorias disponíveis e reconhecer as associações atuais de um produto, sem executar escrita.

**Acceptance Scenarios**:

1. **Given** um Admin autenticado e produtos existentes, **When** consulta produtos administrativamente sem filtro, com filtro vazio ou composto apenas por espaços, **Then** recebe a listagem paginada sem restrição por nome, ordenada por nome e com a mesma regra de desempate do catálogo existente.
2. **Given** os produtos `Fone Azul`, `Telefone Preto` e `Cabo`, **When** um Admin pesquisa ` FONE `, **Then** encontra os dois primeiros por trecho contínuo, sem diferenciar maiúsculas/minúsculas e desconsiderando os espaços externos do filtro.
3. **Given** os produtos `Café Especial` e `Cafe Claro`, **When** um Admin pesquisa `cafe`, **Then** encontra somente `Cafe Claro`; ao pesquisar `CAFÉ`, encontra somente `Café Especial`, pois acentos são distinguidos.
4. **Given** os produtos `Fone Azul` e `Fone  Azul`, **When** um Admin pesquisa `fone azul`, **Then** encontra somente o primeiro; ao pesquisar `fone  azul`, encontra somente o segundo, preservando espaços internos do trecho.
5. **Given** os produtos `Oferta 10%`, `Oferta 100` e `Kit_A`, **When** um Admin pesquisa `10%` ou `kit_`, **Then** encontra respectivamente `Oferta 10%` ou `Kit_A`, tratando os caracteres literalmente, sem curingas.
6. **Given** um Admin autenticado, **When** pesquisa um trecho sem correspondência ou uma página posterior à última dos resultados, **Then** recebe resultado vazio com paginação coerente; navegar pelas páginas da busca conserva a restrição por nome.
7. **Given** categorias existentes, inclusive duas de mesmo nome e uma ainda sem produtos, **When** um Admin consulta opções para seleção, **Then** identifica cada categoria por identificador e nome, sem fundir categorias distintas; na edição, reconhece as categorias atualmente associadas ao produto.
8. **Given** que não existem categorias disponíveis, **When** um Admin consulta opções de seleção, **Then** recebe indicação de ausência de opções e não consegue concluir inclusão ou atualização sem pelo menos uma categoria existente.

---

### User Story 2 - Incluir um produto válido (Priority: P1)

Como Admin autenticado, quero informar os dados completos de um produto para acrescentá-lo ao catálogo com categorias existentes e valores válidos.

**Why this priority**: Entrega a criação administrativa e estabelece as regras comuns de validação e armazenamento.

**Independent Test**: Incluir um produto válido com categorias existentes, conferir resultado e leitura posterior; repetir com limites e dados inválidos, verificando ausência de inclusão parcial.

**Acceptance Scenarios**:

1. **Given** um Admin autenticado e duas categorias existentes, **When** inclui um produto com nome, descrição, preço positivo representável, URL de imagem válida e essas categorias, **Then** recebe confirmação do produto criado, com identificação própria e os dados informados, e o produto pode ser consultado posteriormente.
2. **Given** dados restantes válidos, **When** o Admin informa nome `  Fone  Azul  ` e descrição `  Descrição  completa  `, **Then** o produto é validado e armazenado como `Fone  Azul` e `Descrição  completa`, removendo somente espaços externos e preservando internos.
3. **Given** dados restantes válidos, **When** o Admin informa nomes de 3 ou 80 caracteres após remoção dos espaços externos e descrição de exatamente 10 após a mesma remoção, **Then** a inclusão é aceita; nomes de 2 ou 81 e descrição de 9 são rejeitados, sem criar produto.
4. **Given** dados restantes válidos, **When** o Admin informa preço de 0,01 ou 99.999.999.999.999.999,99, **Then** a inclusão é aceita; preço ausente, zero, negativo, superior ao máximo ou que exija perda de casas decimais é rejeitado integralmente.
5. **Given** dados restantes válidos, **When** o Admin informa URL absoluta sintaticamente válida com `http` ou `https`, **Then** a imagem é aceita sem consultar seu destino, mesmo que este esteja indisponível; imagem ausente, vazia, somente com espaços, endereço relativo, URL malformada ou outro esquema é rejeitado.
6. **Given** solicitação examinável por Admin com nome de 2 caracteres, descrição de 9, preço zero, imagem relativa e categoria inexistente, **When** tenta incluir o produto, **Then** recebe indicação de todos esses campos inválidos detectáveis na mesma avaliação e nenhum produto ou vínculo com categoria é criado.
7. **Given** dados restantes válidos e seleção com uma categoria existente e outra inexistente, **When** o Admin tenta incluir, **Then** toda a inclusão é rejeitada, sem aproveitar somente a categoria válida nem criar novas categorias.
8. **Given** dados restantes válidos, **When** o Admin omite um dos cinco dados obrigatórios, informa nome ou descrição somente com espaços, ou não seleciona categoria, **Then** a inclusão é rejeitada, os campos inválidos detectáveis são identificados e nenhum produto é criado.
9. **Given** dados restantes válidos e categorias existentes A e B, **When** o Admin inclui um produto informando os identificadores A, A e B, **Then** a inclusão é aceita com exatamente uma associação a A e uma a B; informar somente A, A também é válido e resulta em uma única associação a A.

---

### User Story 3 - Atualizar integralmente um produto (Priority: P1)

Como Admin autenticado, quero consultar e substituir todos os dados de um produto existente para corrigir seu cadastro sem alterar sua identidade ou os valores das compras anteriores.

**Why this priority**: Completa a manutenção de dados e protege os valores históricos já registrados.

**Independent Test**: Substituir os cinco dados de um produto existente e conferir sua identidade e leitura posterior; verificar rejeições sem alteração e conservação dos valores de uma compra anterior.

**Acceptance Scenarios**:

1. **Given** um Admin autenticado e produto existente, **When** seleciona o produto para edição, **Then** consulta nome, preço, descrição, imagem e identifica suas categorias atuais.
2. **Given** produto existente com duas categorias, **When** o Admin fornece os cinco dados completos válidos e uma nova seleção de categorias existentes, **Then** todos os dados são substituídos, as associações refletem exatamente a nova seleção e a identificação do produto é conservada; categorias retiradas da seleção continuam existentes.
3. **Given** produto existente, **When** o Admin atualiza nome e descrição com espaços externos e internos, **Then** ambos são validados e armazenados sem os externos, conservando os internos, segundo os mesmos limites da inclusão.
4. **Given** produto existente, **When** o Admin envia atualização com campos obrigatórios ausentes, limites inválidos, imagem inválida ou categoria inexistente entre válidas, **Then** todos os campos inválidos detectáveis são informados e o produto inteiro, inclusive suas associações anteriores, permanece inalterado; campos omitidos não são mantidos como atualização parcial.
5. **Given** identificador sem produto correspondente, **When** o Admin tenta atualizar, **Then** recebe indicação de produto inexistente, sem criar produto, confirmar atualização ou alterar outro produto.
6. **Given** produto anterior com preço zero, nome fora dos novos limites ou sem categorias, **When** é consultado, **Then** continua legível sem correção automática; ao atualizá-lo integralmente, todos os cinco dados devem satisfazer as novas regras para que a alteração seja aceita.
7. **Given** produto vendido anteriormente por 10,00 em quantidade 2, **When** o Admin muda seu preço para 12,00, **Then** o catálogo apresenta 12,00 e a compra anterior conserva preço unitário 10,00, subtotal 20,00 e seu total anterior, sem exigir consulta de pedidos nesta feature.
8. **Given** produto existente, dados restantes válidos e categorias existentes A e B, **When** o Admin atualiza integralmente informando os identificadores A, A e B, **Then** as associações passam a ser exatamente uma a A e uma a B; informar somente A, A resulta em uma associação a A. Se a seleção também contiver identificador inexistente, mesmo repetido, toda a atualização é rejeitada e os dados e associações anteriores permanecem inalterados.

---

### User Story 4 - Excluir sem destruir dados relacionados (Priority: P1)

Como Admin autenticado, quero excluir produtos permitidos e receber rejeição clara quando a exclusão comprometer compras existentes.

**Why this priority**: Completa as três operações administrativas, protegendo categorias e histórico de compras.

**Independent Test**: Tentar excluir separadamente produto não utilizado em pedidos, outro utilizado e identificador inexistente, conferindo resultados e preservação dos dados relacionados.

**Acceptance Scenarios**:

1. **Given** produto existente não utilizado em pedidos e associado a categorias, **When** o Admin o exclui, **Then** recebe confirmação, o produto deixa de ser encontrado e as categorias continuam existentes; seus vínculos com outros produtos são preservados.
2. **Given** produto associado a um ou mais itens de pedidos, **When** o Admin tenta excluí-lo, **Then** recebe indicação de impedimento por integridade referencial e produto, associações de categorias, pedidos, itens e valores históricos permanecem inalterados.
3. **Given** identificador sem produto correspondente, **When** o Admin tenta excluir, **Then** recebe indicação de produto inexistente, sem informar exclusão bem-sucedida nem alterar outro produto.
4. **Given** produto cuja exclusão viola outra relação referencial existente, **When** o Admin tenta excluí-lo, **Then** recebe indicação do impedimento e nenhum dado relacionado é excluído para contornar a restrição.

---

### User Story 5 - Restringir a manutenção e preservar jornadas existentes (Priority: P1)

Como usuário do sistema, quero que somente Admin mantenha produtos e que consultas públicas, login e registro de pedidos continuem conforme as features anteriores.

**Why this priority**: Autorização, rejeição integral e ausência de regressões são condições obrigatórias de toda a entrega.

**Independent Test**: Exercitar operações administrativas com Admin isolado, ambos os perfis, Cliente, conta sem perfil e autenticação inválida; repetir jornadas das Features 001–004, inclusive leitura de produtos anteriores.

**Acceptance Scenarios**:

1. **Given** conta autenticada com ADMIN, inclusive sem CLIENT, **When** executa busca administrativa, consulta auxiliar de categorias, consulta para edição, inclusão, atualização ou exclusão com as demais condições satisfeitas, **Then** a operação é permitida; possuir também CLIENT não altera o resultado.
2. **Given** Cliente sem ADMIN ou conta autenticada sem nenhum dos dois perfis, **When** tenta qualquer operação administrativa desta feature, **Then** recebe rejeição por falta de permissão e nenhum dado é alterado.
3. **Given** autenticação ausente, inválida, expirada ou sem conta identificável, **When** tenta qualquer operação administrativa, **Then** recebe rejeição de autenticação e nenhum dado é alterado.
4. **Given** conta sem autorização administrativa e solicitação com dados inválidos, **When** tenta manter produtos, **Then** não obtém acesso administrativo nem informações de edição restritas; informar campos inválidos não concede permissão.
5. **Given** escrita autorizada, **When** ocorre falha interna antes da conclusão, **Then** não é informado sucesso e não permanecem mudanças parciais em produtos, categorias ou relações com pedidos.
6. **Given** visitante e produtos anteriores, inclusive com preço zero ou categorias vazias, **When** consulta catálogo e detalhes públicos sem autenticação ou com credencial inválida irrelevante, **Then** conserva comportamentos, informações, paginação, ordenação e erros das Features 001–002, sem exigir novas validações de escrita para ler esses produtos.
7. **Given** jornadas de login e registro de pedido, **When** são exercitadas após a introdução da manutenção, **Then** preservam comportamentos e contratos das Features 003–004, inclusive autorização de compra, preços históricos, rejeições e novo pedido por envio válido.
8. **Given** consultas públicas existentes, **When** a busca administrativa é introduzida, **Then** acesso público e contratos permanecem preservados; o novo filtro administrativo não restringe ou modifica consultas públicas.

### Edge Cases

- Nome/descrição somente com espaços ficam vazios após remoção dos externos e são inválidos; a contagem ocorre depois dessa remoção.
- Espaços internos de nome, descrição e filtro não são compactados. A busca compara trecho contínuo e distingue acentos.
- Filtro somente com espaços equivale a ausência; `%` e `_` são literais, não instruções de busca.
- Categorias de mesmo nome continuam distintas por identificação. Categorias sem produtos também estão disponíveis para seleção.
- Repetir identificador de categoria não cria vínculos adicionais. Cada identificador distinto deve existir, e a seleção deve representar pelo menos uma categoria distinta.
- Ausência de categorias impede escrita válida; referência inexistente não permite salvar só os demais dados ou associações.
- Preço positivo fora da capacidade existente não pode ser aceito por arredondamento ou perda silenciosa; casas adicionais compostas apenas por zeros não representam perda de valor.
- URL válida com destino indisponível continua aceita; não se verifica existência, conteúdo ou acessibilidade externa da imagem.
- Omissão de dados em atualização integral não conserva silenciosamente valores anteriores.
- Produto anterior inválido pelas novas regras continua legível; sua atualização integral deve regularizar os dados enviados, sem migração automática.
- Produto usado em qualquer item de pedido não pode ser excluído, independentemente do estado do pedido. Categorias associadas, por si só, não impedem excluir produto sem outra restrição.
- Informar todos os campos inválidos detectáveis pressupõe solicitação examinável e usuário autorizado; não exige inventar erros para dados não examináveis nem divulgar dados restritos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir somente a conta autenticada com ADMIN executar inclusão, atualização integral, exclusão, busca administrativa, consulta para edição e consulta auxiliar de categorias. ADMIN sem CLIENT DEVE ser suficiente; CLIENT isolado ou ausência de ADMIN NÃO DEVEM conceder acesso.
- **FR-002**: Autenticação ausente, inválida, expirada ou sem conta identificável DEVE impedir operações administrativas sem alteração de dados. Falta de ADMIN DEVE indicar falta de permissão, sem divulgar informação administrativa restrita.
- **FR-003**: O sistema DEVE fornecer consulta administrativa paginada de produtos, ordenada por nome com o desempate já estabelecido pelo catálogo, para localizar e selecionar produtos existentes.
- **FR-004**: A busca administrativa DEVE aceitar trecho contínuo literal do nome, ignorar maiúsculas/minúsculas e distinguir acentos. DEVE remover espaços externos do filtro, preservar internos e tratar caracteres especiais literalmente, sem curingas. Filtro ausente ou vazio após remoção NÃO DEVE restringir resultados.
- **FR-005**: A busca DEVE conservar o filtro entre páginas e informar resultados vazios com paginação coerente quando não houver correspondências ou a página estiver além dos resultados.
- **FR-006**: A consulta auxiliar DEVE permitir ao Admin identificar categorias existentes por identificador e nome, inclusive categorias sem produtos e categorias distintas de mesmo nome. A consulta para edição DEVE permitir reconhecer categorias atuais do produto. Essas consultas NÃO DEVEM manter categorias.
- **FR-007**: O sistema DEVE incluir produto completo com identificação própria a partir de nome, preço, descrição, URL da imagem e seleção de categorias, confirmando o cadastro somente quando todos os dados forem válidos.
- **FR-008**: Nome e descrição DEVEM ser obrigatórios, validados e armazenados sem espaços nas extremidades, preservando espaços internos. Após remoção, nome DEVE conter entre 3 e 80 caracteres, inclusive, e descrição DEVE conter pelo menos 10.
- **FR-009**: Preço DEVE ser obrigatório, estritamente positivo e representável sem perda na capacidade monetária existente: até 99.999.999.999.999.999,99, com duas casas decimais de valor. O menor valor positivo representável é 0,01. Valores superiores ao limite ou que exijam arredondamento DEVEM ser rejeitados integralmente.
- **FR-010**: Imagem DEVE ser obrigatória e conter URL absoluta sintaticamente válida com esquema `http` ou `https`. Ausência, vazio, somente espaços, endereço relativo, URL malformada ou outro esquema DEVEM ser rejeitados. O sistema NÃO DEVE acessar o destino para verificar disponibilidade ou conteúdo.
- **FR-011**: Inclusão e atualização DEVEM associar pelo menos uma categoria distinta existente. Cada identificador informado DEVE representar uma única associação; repetições NÃO DEVEM criar vínculos adicionais. Todas as categorias distintas informadas DEVEM existir. Qualquer categoria inexistente DEVE causar rejeição integral, sem salvar somente associações válidas nem criar categorias implicitamente.
- **FR-012**: O sistema DEVE informar todos os campos inválidos detectáveis na mesma avaliação de solicitação examinável e autorizada, inclusive seleção com referências inexistentes. A indicação DEVE identificar campos a corrigir; sua apresentação concreta não é definida nesta especificação.
- **FR-013**: O sistema DEVE permitir consultar os cinco dados atuais do produto e atualizá-los integralmente, conservando sua identificação. Associações DEVEM corresponder à nova seleção; categorias desassociadas DEVEM permanecer existentes. Omissão de campo obrigatório DEVE causar rejeição, não atualização parcial.
- **FR-014**: Atualização ou exclusão de produto inexistente DEVE ser rejeitada com indicação clara de inexistência, sem criar produto, declarar exclusão bem-sucedida ou alterar outro produto.
- **FR-015**: O sistema DEVE excluir produto existente sem impedimento referencial, confirmar exclusão e deixar de encontrá-lo em consultas posteriores. A exclusão NÃO DEVE excluir categorias nem modificar seus vínculos com outros produtos.
- **FR-016**: Produto utilizado em qualquer OrderItem NÃO DEVE ser excluído. O sistema DEVE informar impedimento por integridade referencial e preservar produto, associações, pedidos, itens e valores históricos. Outras violações referenciais existentes DEVEM igualmente impedir exclusão sem destruir dados relacionados para contorná-las.
- **FR-017**: Escritas rejeitadas ou não concluídas por falha interna NÃO DEVEM deixar alterações parciais nem informar sucesso. Atualização inválida DEVE preservar todos os dados e associações anteriores.
- **FR-018**: Alterar preço do catálogo NÃO DEVE alterar preços unitários registrados, subtotais ou totais de compras anteriores. Essa conservação NÃO DEVE depender de consulta de pedidos nesta feature.
- **FR-019**: Novas regras de escrita DEVEM ser exigidas em inclusões e atualizações integrais. Produtos anteriores DEVEM continuar legíveis sem validação retroativa impeditiva, correção ou migração automática, inclusive com preço zero ou dados fora das novas regras.
- **FR-020**: O sistema DEVE preservar comportamentos e contratos públicos das Features 001–004: catálogo, detalhes, login e registro de pedidos. A busca administrativa DEVE ser comportamento novo e restrito a ADMIN, sem alterar consultas públicas ou apresentar o filtro como já implementado.

### Key Entities *(include if feature involves data)*

- **Admin autenticado**: Conta existente com ADMIN, responsável por manutenção e consultas administrativas; não exige CLIENT.
- **Produto**: Item com identificação estável, nome, descrição, preço vigente, referência de imagem e categorias; admite criação, atualização integral ou exclusão permitida.
- **Categoria existente**: Classificação identificável e selecionável, associável a vários produtos. A feature consulta e altera associações de produtos, sem manter categorias.
- **Busca administrativa**: Trecho literal opcional e contexto de paginação usado pelo Admin para localizar produtos pelo nome.
- **Pedido e item de pedido (OrderItem)**: Compra existente e suas linhas, com relação ao produto e valores históricos preservados; a relação impede excluir produto utilizado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de autorização, ADMIN isolado ou combinado com CLIENT executa operações administrativas válidas, e contas sem ADMIN ou autenticação válida são rejeitadas sem alteração de dados.
- **SC-002**: Em 100% dos cenários de busca, resultados correspondem ao trecho literal e regras de maiúsculas/minúsculas, acentos e espaços, com paginação coerente e sem restrição para filtro ausente ou vazio.
- **SC-003**: Em todos os cenários de seleção, o Admin distingue categorias existentes, inclusive de mesmo nome ou sem produtos, e reconhece associações atuais sem criar, modificar ou excluir categorias.
- **SC-004**: Em 100% dos cenários válidos de inclusão/atualização, os cinco dados completos são salvos, nome/descrição conservam somente espaços internos, cada identificador distinto de categoria resulta em uma única associação e o Admin recebe confirmação em um único envio válido; atualização conserva identificação.
- **SC-005**: Em todos os cenários inválidos examináveis, 100% dos campos inválidos detectáveis são identificados na mesma avaliação e nenhuma escrita parcial permanece, inclusive com categoria inexistente entre válidas.
- **SC-006**: Em 100% dos cenários de exclusão permitida, somente produto e suas associações são removidos; diante de produto inexistente ou restrição referencial, a exclusão é rejeitada e dados relacionados são preservados.
- **SC-007**: Em todos os cenários de alteração posterior de preço ou tentativa de excluir produto vendido, preços unitários, subtotais, totais, pedidos e itens anteriores permanecem inalterados.
- **SC-008**: 100% dos cenários de regressão das Features 001–004 mantêm comportamentos e contratos, e produtos anteriores continuam legíveis sem correção automática ou autenticação nas consultas públicas.
- **SC-009**: Em 100% dos cenários de falha anterior à conclusão de escrita, não há confirmação de sucesso nem alteração parcial persistente.
- **SC-010**: Em 100% dos cenários de atualização de produto inexistente, a operação é rejeitada com indicação de inexistência, sem criar produto, confirmar atualização ou alterar outro produto.

## Assumptions

### Fontes e requisitos do documento de requisitos

- Documento de requisitos DSCommerce, arquivo local `C:/Users/ronal/Downloads/Documento de requisitos DSCommerce.pdf`, conferido por texto e inspeção visual no checkpoint de análise anterior. Permanece fora do repositório; nenhuma cópia é criada nesta especificação.
- Páginas 6–7, **Manter produtos**: Admin autenticado; inclusão, atualização e exclusão; catálogo com busca por nome; nome, preço, descrição, URL da imagem e categorias; limites de nome/descrição, preço positivo, categoria obrigatória; indicação dos campos inválidos, inexistência e impedimento referencial.
- Página 4, **Modelo conceitual**: o preço do item de pedido representa o valor histórico da venda e não acompanha alterações posteriores no produto.
- O PDF não define acentos/espaços da busca, normalização armazenada, esquema da imagem, consulta auxiliar de categorias, tratamento de identificadores de categoria repetidos ou formato dos erros. As decisões abaixo completam o recorte sem atribuí-las ao PDF.

### Decisões expressamente adotadas nesta sessão

- Busca **administrativa**, nova e restrita a ADMIN, literal, ignorando maiúsculas/minúsculas e **distinguindo acentos**. Substitui a proposta anterior de ignorar acentos; não estende busca às consultas públicas.
- Remover espaços externos do filtro; ausência ou vazio não restringe resultados. Leitura literal preserva espaços internos e não interpreta curingas.
- Imagem obrigatória com URL absoluta sintaticamente válida `http` ou `https`, sem consulta externa de disponibilidade.
- Validar e armazenar nome/descrição sem espaços externos, preservando internos.
- Atualização integral; preço positivo dentro da capacidade existente; categorias existentes com consulta auxiliar de leitura; rejeição integral de referência inexistente e operações inválidas; todos os campos inválidos detectáveis informados.
- Decisão desta sessão, expressamente adotada na correção documental: cada identificador de categoria informado representa uma única associação; repetições não criam vínculos adicionais. Todas as categorias distintas devem existir e deve permanecer pelo menos uma categoria distinta. Esta regra não é requisito atribuído ao PDF.
- Rejeitar atualização/exclusão de inexistente e exclusão de produto usado em OrderItem; preservar categorias e valores históricos.
- Aplicar novas regras em inclusão/atualização, sem migração automática ou impedimento da leitura anterior; preservar contratos públicos das Features 001–004.

### Assunções delimitadoras e dependências

- [Constituição](../../.specify/memory/constitution.md): governa o projeto. Escolhas de solução pertencem ao planejamento posterior.
- [Feature 001](../001-consulta-catalogo-produtos/spec.md) e [Feature 002](../002-visualizar-detalhes-produto/spec.md): fornecem catálogo/detalhes, dados públicos e regressões. A busca administrativa não está implementada nem definida nesses contratos.
- [Feature 003](../003-login-autenticacao/spec.md) e [Feature 004](../004-registrar-pedido/spec.md): fornecem autenticação, identidade/perfis e compras. Existir ADMIN não comprova manutenção administrativa autorizada ou entregue.
- Contas ADMIN e categorias já existem ou são preparadas para futura validação; seu provisionamento real não integra a feature. Ausência de categorias não autoriza cadastro sem categoria.
- A consulta auxiliar serve somente para identificar opções e associações atuais. Como assunção de recorte, não inclui busca própria de categorias, hierarquia ou relatórios; as opções devem permanecer acessíveis para seleção.
- A capacidade existente corresponde ao máximo e às duas casas de valor de FR-009; não se acrescentam moeda, conversão, arredondamento ou nova capacidade. Representações equivalentes sem perda, como 1,000 para 1,00, não mudam o valor.

### Dúvidas materiais e limites

- Dúvidas sobre acentos, validade da imagem e armazenamento de nome/descrição foram resolvidas expressamente nesta sessão; o tratamento de categorias repetidas foi adotado expressamente no pedido de correção documental. Não há marcador de clarificação ou dúvida material bloqueante restante neste recorte.
- Assunções delimitadoras permanecem identificadas como assunções, sem aprovação explícita inventada. O planejamento deve explicitar limites representacionais necessários sem ampliar escopo.
- Identificar campos inválidos é requisito de negócio; apresentação concreta, mensagens exatas e solução de implementação ficam abertas para desenho posterior.
- Conservar valores financeiros não implica congelar nome, descrição ou imagem da época da compra; não acrescenta esses históricos nem consulta de pedidos.
- Busca literal não inclui aproximação, correção ortográfica, palavras independentes ou remoção de acentos; não exige mudar ordenação anterior.
- Validade sintática da URL não garante existência, acessibilidade ou conteúdo adequado da imagem; nenhuma chamada externa é necessária.
- Prioridades não autorizam retirar operações: a entrega exige as três escritas, consultas administrativas, validações, autorização e preservações descritas.

### Fora do escopo

- Alterar consultas públicas para incorporar busca administrativa, restringir acesso ou mudar contratos das Features 001–004.
- Manter categorias, usuários ou perfis; sign up; provisionamento de ADMIN; gestão de credenciais.
- Atualização parcial, importação em lote, migração ou regularização automática de produtos anteriores.
- Consulta/listagem/relatório de pedidos; pagamentos e novas transições; apagar compras para permitir excluir produto.
- Histórico de nome/descrição/imagem da compra; reconhecimento de repetição e idempotência de manutenção.
- Upload, armazenamento ou inspeção externa de imagens; verificação de disponibilidade de URL.
- Estoque, promoções, frete, descontos, moeda ou conversão monetária.
- Interface, carrinho, implantação e validação de ambientes de produção.

Este artefato especifica comportamento futuro. Não afirma implementação, teste executado, planejamento iniciado ou alteração dos contratos existentes.
