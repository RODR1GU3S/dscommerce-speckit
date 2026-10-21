# Feature Specification: Consultar meus pedidos

**Feature Branch**: `007-consultar-meus-pedidos`

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "Especificar a Feature 007: conta autenticada com CLIENT ou ADMIN consulta exclusivamente seus próprios pedidos, em lista paginada e detalhe. Mostrar nome atual do produto e valores históricos da compra, preservar ordem registrada dos itens, representação monetária exata e comportamentos das Features 001–006. Não incluir pagamentos, transições, edição, relatório, consultas administrativas de terceiros ou interface."

## User Scenarios & Testing *(mandatory)*

Os cenários são critérios para validação futura, não resultados executados. As quatro histórias são necessárias ao recorte. Titularidade e preservação financeira têm a mesma prioridade da consulta bem-sucedida.

### User Story 1 - Encontrar minhas compras em uma lista paginada (Priority: P1)

Como Cliente ou Admin autenticado, quero navegar somente pelos meus pedidos e reconhecer cada compra por sua identificação, instante, estado e total.

**Why this priority**: Entrega o acesso posterior às compras sem repetir seu registro e impede que conteúdo ou totais revelem compras de outras contas.

**Independent Test**: Preparar pedidos de duas contas, com instantes conhecidos e empates, e percorrer a lista de uma delas, comparando identificação, ordem, totais e ausência dos pedidos alheios.

**Acceptance Scenarios**:

1. **Given** três pedidos da conta autenticada e dois de outra conta, **When** solicita uma página que comporta seus três pedidos, **Then** recebe somente suas três compras; cada resumo contém somente identificador, instante de criação, estado atual armazenado e total, e o total de pedidos informado é três.
2. **Given** cinco pedidos próprios e pedidos alheios intercalados por instante, **When** percorre as três primeiras páginas com tamanho dois e dados estáveis, **Then** recebe respectivamente 2, 2 e 1 pedidos próprios, sem perdas ou duplicações, com cinco pedidos e três páginas informados em todas elas.
3. **Given** pedidos próprios com instantes diferentes e dois com o mesmo instante, **When** navega pela lista, **Then** encontra primeiro os mais recentes e, no empate, o de identificador maior primeiro, mantendo essa ordem entre páginas.
4. **Given** conta sem pedidos e outras contas com compras, **When** consulta a primeira página ou uma página posterior válida, **Then** recebe lista vazia, zero pedidos e zero páginas, conservando a posição e o tamanho solicitados.
5. **Given** cinco pedidos próprios e tamanho dois, **When** solicita a quarta página, **Then** recebe lista vazia nessa posição, mantendo o tamanho dois, o total de cinco pedidos e as três páginas existentes, sem ajustar a posição solicitada.
6. **Given** conta autenticada e autorizada, **When** fornece posição de página negativa, tamanho zero ou negativo, ou valor não inteiro para posição ou tamanho, **Then** recebe rejeição clara da paginação inválida, sem apresentação de pedidos ou alteração de dados.
7. **Given** consultas sucessivas por duas contas autorizadas diferentes, **When** cada uma solicita sua lista, **Then** conteúdo e totais correspondem exclusivamente à identidade da respectiva consulta, sem herdar a titularidade anterior.

---

### User Story 2 - Conferir os itens e os valores registrados da compra (Priority: P1)

Como titular de um pedido, quero consultar seus detalhes e identificar os produtos pelo nome atual, sem confundir o preço do catálogo com o valor histórico da compra.

**Why this priority**: Permite reconhecer a composição da compra e preserva o significado financeiro já estabelecido no registro.

**Independent Test**: Consultar pedido próprio conhecido, modificar posteriormente nome e preço dos produtos e consultar novamente, conferindo nome atual, ordem registrada dos itens e valores históricos exatos.

**Acceptance Scenarios**:

1. **Given** pedido próprio com dois produtos registrados na ordem B e A, por preços unitários de 10,00 e 7,50 e quantidades de 2 e 3, **When** consulta o detalhe, **Then** recebe somente identificador do pedido, instante de criação, estado atual armazenado, total e itens; cada item contém somente identificador e nome atual do produto, quantidade registrada, preço unitário histórico e subtotal; a ordem permanece B e A, os subtotais são 20,00 e 22,50 e o total é 42,50.
2. **Given** pedido próprio que não aparece na página de pedidos atualmente consultada, **When** solicita diretamente esse pedido por sua identificação, **Then** recebe seus detalhes independentemente da página anterior.
3. **Given** compra de duas unidades a 10,00 de produto chamado Nome antigo, cujo cadastro depois passa a Nome atual e preço 12,00, **When** consulta lista e detalhe da compra, **Then** encontra total 20,00 em ambos; no detalhe, o nome é Nome atual, a quantidade é dois, o preço unitário é 10,00 e o subtotal é 20,00.
4. **Given** pedido com produtos de identificadores diferentes que atualmente possuem o mesmo nome, **When** consulta o detalhe, **Then** os itens continuam distintos por identificação e mantêm a ordem registrada, sem fusão nem reordenação pelo nome ou identificador do produto.
5. **Given** pedido válido já registrado e produto cujo cadastro atual contém preço zero, nome legado fora dos limites atuais de escrita ou categorias vazias, **When** consulta a compra, **Then** o pedido continua legível, apresenta o nome atual exato e seus valores históricos, sem validação retroativa impeditiva ou correção automática.
6. **Given** pedidos válidos cujos valores conhecidos incluem 0,01 e 9.007.199.254.740.993,01, **When** consulta lista e detalhe, **Then** os montantes preservam exatamente seus valores e a apresentação textual decimal existente com ponto e duas casas, inclusive `0.01` e `9007199254740993.01`, sem aproximação, notação exponencial ou arredondamento.
7. **Given** dois pedidos próprios com os mesmos produtos, registrados a preços históricos diferentes, **When** consulta a lista e cada detalhe, **Then** os pedidos permanecem separados, cada qual com suas quantidades, preços, subtotais e total, sem substituição pelos valores da outra compra ou do catálogo.

---

### User Story 3 - Proteger titularidade e informações restritas (Priority: P1)

Como usuário do sistema, quero que as consultas exijam identificação e permissão válidas e que uma conta não descubra compras de outra.

**Why this priority**: O acesso indevido comprometeria a privacidade mesmo se a consulta e os cálculos estivessem corretos.

**Independent Test**: Exercitar lista e detalhe com Cliente, Admin isolado, ambos os perfis, conta sem perfil autorizado, autenticação inválida e pedidos de terceiros, verificando conteúdo, totais e rejeições.

**Acceptance Scenarios**:

1. **Given** conta autenticada com CLIENT, ADMIN sem CLIENT, ou ambos os perfis, **When** consulta sua lista ou um pedido próprio, **Then** recebe seus dados sob as mesmas regras, sem exigir a combinação dos perfis.
2. **Given** consulta sem autenticação, com autenticação inválida ou expirada, ou sem conta existente identificável, **When** solicita lista ou detalhe, **Then** recebe rejeição de autenticação, sem pedidos, itens, contagens ou dados de contas.
3. **Given** conta existente autenticada sem CLIENT nem ADMIN, **When** solicita lista ou detalhe, **Then** recebe rejeição por falta de permissão, sem dados de pedidos.
4. **Given** conta autorizada, um pedido alheio existente e uma identificação sem pedido correspondente, **When** solicita separadamente seus detalhes, **Then** recebe a mesma indicação de pedido não encontrado nos dois casos, sem revelar existência, titular, itens, valores ou estado da compra alheia.
5. **Given** Admin autenticado, inclusive sem CLIENT, e pedido de outra conta, **When** solicita esse detalhe, **Then** o perfil administrativo não amplia o acesso: recebe a mesma indicação de pedido não encontrado aplicável a qualquer pedido fora de sua titularidade.
6. **Given** consulta sem autenticação válida ou sem perfil autorizado, inclusive com paginação ou identificação de pedido inválidas, **When** tenta consultar, **Then** não obtém dados restritos nem confirmação de existência de pedidos; dados inválidos não concedem acesso.
7. **Given** falha interna que impede identificar a conta ou concluir a leitura, **When** solicita lista ou detalhe, **Then** recebe indicação segura de falha de processamento, distinta de ausência de pedidos, pedido não encontrado e autenticação inválida, sem falso sucesso ou informações sensíveis.

---

### User Story 4 - Consultar sem modificar compras ou jornadas existentes (Priority: P1)

Como usuário, quero que consultar compras seja uma leitura e que as demais jornadas continuem com os comportamentos entregues.

**Why this priority**: A nova consulta complementa o registro e a manutenção existentes, sem alterar dados, contratos ou decisões anteriores.

**Independent Test**: Comparar dados antes/depois das consultas e revalidar os comportamentos afetados das Features 001–006, distinguindo leitura de novo registro.

**Acceptance Scenarios**:

1. **Given** produtos, categorias, contas, perfis, pedidos e itens conhecidos, **When** são realizadas consultas bem-sucedidas, vazias e rejeitadas, **Then** esses dados permanecem inalterados, inclusive titularidade, instante, ordem dos itens, estado e valores financeiros das compras.
2. **Given** consultas públicas de catálogo e detalhes existentes, **When** são exercitadas após esta extensão, **Then** preservam acesso sem autenticação ou com credencial inválida irrelevante, filtro de nome, paginação, ordenação, representações, preços numéricos, categorias e erros das Features 001/002/006.
3. **Given** tentativas de login com dados válidos, inválidos ou incompletos, **When** são realizadas após esta extensão, **Then** preservam autenticação por nome e senha, confirmação, rejeições e confidencialidade da Feature 003.
4. **Given** pedido já registrado, **When** é consultado repetidamente e depois há outro envio válido de registro com os mesmos itens, **Then** as leituras não criam pedidos e o novo envio cria outro pedido conforme a Feature 004; nenhum pedido anterior é reutilizado, e a consulta não modifica nem esvazia carrinho.
5. **Given** manutenção administrativa e registro de pedidos existentes, **When** suas jornadas e rejeições são exercitadas após a extensão, **Then** preservam autorização, validações, integridade referencial, ausência de gravação parcial e conservação dos valores históricos das Features 004–005.

### Edge Cases

- Uma conta sem compras não recebe pedidos de terceiros para preencher a página; seus totais permanecem zero mesmo quando outras contas têm muitos pedidos.
- O filtro de titularidade alcança conteúdo, contagem e detalhes. Não é suficiente ocultar itens de uma lista cujos totais revelem o conjunto global.
- Empates de instante são resolvidos pelo identificador do pedido, inclusive quando divididos entre páginas.
- A identificação do detalhe não depende da página anterior nem estabelece estado de seleção compartilhado entre contas.
- Pedido alheio e inexistente têm a mesma indicação de não encontrado para conta autorizada. Autenticação inválida e falta de permissão continuam sendo resultados distintos.
- Nomes iguais não fundem produtos diferentes; mudar o nome não reordena itens registrados.
- O nome apresentado é o atual, preservado integralmente; nome anterior ou imagem da época da compra não passam a existir como histórico.
- Preço atual zero ou diferente do registrado não altera elegibilidade de leitura, subtotais ou total de uma compra válida anterior.
- Quantidades e preços unitários são os registrados; nenhum valor financeiro é obtido do preço atual do catálogo.
- Consulta vazia não se confunde com falha interna. Falhas não produzem lista vazia ou conteúdo parcial apresentado como sucesso.
- A leitura do estado armazenado não executa pagamento nem qualquer transição.
- Repetir leitura não equivale a reenviar registro: continua válida a decisão anterior de novo pedido por envio válido.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir lista e detalhe somente a conta autenticada existente com CLIENT ou ADMIN. ADMIN isolado DEVE ser suficiente; ausência de autenticação válida ou desses perfis DEVE impedir a consulta.
- **FR-002**: A identidade autenticada DEVE determinar exclusivamente a titularidade em cada consulta. Nenhuma conta, inclusive ADMIN, DEVE acessar pedidos de terceiros por esta funcionalidade ou ampliar o acesso escolhendo outro titular.
- **FR-003**: A lista DEVE apresentar somente identificador do pedido, instante de criação, estado atual armazenado e total em cada resumo, acompanhados de informações coerentes para navegação paginada.
- **FR-004**: O detalhe DEVE apresentar somente os quatro dados do resumo e os itens na ordem registrada. Cada item DEVE conter somente identificador e nome atual do produto, quantidade registrada, preço unitário histórico e subtotal.
- **FR-005**: O nome do produto DEVE ser lido como dado atual, conservando seu texto original. Não é snapshot histórico; a consulta NÃO DEVE criar histórico de nome ou imagem nem impor validação retroativa de escrita para permitir ler a compra.
- **FR-006**: Quantidade e preço unitário de cada item DEVEM ser os registrados na compra. Alterações posteriores de nome, preço ou categorias do produto NÃO DEVEM modificar os valores financeiros apresentados.
- **FR-007**: O subtotal DEVE corresponder exclusivamente à quantidade registrada multiplicada pelo preço unitário histórico; o total DEVE ser exclusivamente a soma desses subtotais, sem frete, desconto ou acréscimo novo. Lista e detalhe DEVEM apresentar o mesmo total para a mesma compra.
- **FR-008**: Os montantes da compra DEVEM preservar a representação monetária exata existente nos pedidos: texto decimal de base dez, com ponto e exatamente duas casas decimais, sem aproximação, arredondamento, notação exponencial, mudança de capacidade ou conversão para o preço atual. Isso NÃO DEVE modificar a representação numérica pública do catálogo e dos detalhes de produto.
- **FR-009**: Conteúdo, contagem de pedidos e quantidade de páginas DEVEM considerar somente pedidos do titular autenticado, com a mesma restrição em todas as páginas.
- **FR-010**: A lista DEVE ordenar pedidos por instante de criação decrescente e identificador decrescente como desempate. Em conjunto estável, navegar pelas páginas DEVE apresentar cada pedido próprio uma única vez, sem perdas ou duplicações.
- **FR-011**: Ausência de pedidos próprios DEVE produzir sucesso com conteúdo vazio, zero pedidos e zero páginas, conservando a posição e o tamanho solicitados, inclusive em página posterior válida.
- **FR-012**: Página válida posterior ao conjunto próprio DEVE produzir sucesso com conteúdo vazio e preservar os totais desse conjunto, a posição e o tamanho solicitados, sem ajustar a página.
- **FR-013**: A consulta DEVE aceitar paginação válida e rejeitar posição negativa, tamanho não positivo ou valores não inteiros com indicação clara de entrada inválida. Omissão de preferências DEVE permitir consultar a primeira página com padrões documentados no planejamento.
- **FR-014**: Para conta autenticada e autorizada, detalhe de pedido inexistente ou alheio DEVE produzir a mesma indicação de pedido não encontrado, sem confirmar existência ou divulgar dados da compra ou de seu titular.
- **FR-015**: Rejeições de autenticação, falta de permissão, entrada inválida e falha interna DEVEM ser identificáveis sem expor credenciais, segredos, dados de terceiros ou detalhes internos. Falha de processamento NÃO DEVE ser convertida em ausência de pedidos, não encontrado ou sucesso parcial.
- **FR-016**: As consultas NÃO DEVEM modificar produtos, categorias, contas, perfis, pedidos ou itens, nem criar pedidos, corrigir dados, alterar valores ou esvaziar carrinho. Repetição de consulta NÃO DEVE provocar qualquer registro.
- **FR-017**: O sistema DEVE preservar os comportamentos e contratos das Features 001–006, inclusive acesso público, busca por nome, detalhes, login, manutenção administrativa, registro atômico, preços históricos e novo pedido por envio válido.
- **FR-018**: O estado apresentado DEVE ser exclusivamente o estado atual armazenado do pedido. Consultar NÃO DEVE criar pagamento, registrar quitação, executar transição ou exigir a implementação de estados futuros.

### Key Entities *(include if feature involves data)*

- **Conta titular**: Identidade autenticada existente com CLIENT ou ADMIN, cujas compras delimitam conteúdo e totais da consulta.
- **Pedido próprio**: Compra registrada para essa conta, com identificador, instante de criação, estado armazenado e itens. Seu total deriva dos valores registrados.
- **Item registrado**: Produto identificado, quantidade e preço unitário históricos, subtotal derivado e posição original na compra.
- **Produto atual**: Cadastro existente do produto referenciado pelo item. Somente seu nome atual complementa a identificação da compra; seu preço atual não participa do cálculo histórico.
- **Página de pedidos próprios**: Subconjunto ordenado das compras do titular, com posição, tamanho e totais do mesmo conjunto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de titularidade e permissão, Cliente e Admin acessam somente suas próprias compras; zero pedidos, itens ou contagens alheios são expostos nas consultas ou rejeições.
- **SC-002**: Com cinco pedidos próprios e pedidos de terceiros intercalados, navegar em páginas de tamanho dois apresenta exatamente os cinco próprios em 2/2/1, sem perda ou duplicação, com totais cinco/três e ordem determinística, inclusive nos empates.
- **SC-003**: Em 100% das consultas válidas de detalhe, o usuário reconhece a compra selecionada pelos dados corretos e pelos itens na ordem registrada, com nome atual e identificação de cada produto, sem mistura com outro pedido ou dependência da página anterior.
- **SC-004**: Em 100% dos cenários financeiros, inclusive alteração posterior do catálogo e valores pequenos ou elevados, quantidade, preço unitário, subtotal e total correspondem exatamente à compra registrada; lista e detalhe concordam sem perda de valor ou de representação.
- **SC-005**: Em 100% dos cenários de lista vazia, página posterior, pedido não encontrado, entrada inválida e falha interna, o usuário recebe o resultado correspondente, sem substituição por dados de outra compra ou falso sucesso.
- **SC-006**: Em 100% dos cenários de leitura e regressão definidos, as consultas deixam os dados inalterados e os comportamentos afetados das Features 001–006 permanecem atendidos, incluindo a distinção entre ler uma compra e registrar uma nova.

## Assumptions

### Fontes: requisitos expressos e papel do modelo

- Fonte principal: `C:/Users/ronal/Downloads/Documento de requisitos DSCommerce.pdf`, oito páginas, SHA-256 `ed1b2063012f25f44f944830351bcaa966e0562c03715948f5fd0932d9434a83`. Reaproveita-se a investigação anterior desta conversa, que leu o texto e examinou os diagramas; não houve nova investigação integral do PDF nem criação de sua cópia nesta especificação.
- Página 5, seção 5, **Visualizar pedidos**: usuário logado visualiza os pedidos que ele próprio já fez. Na mesma página, **Atores** define Cliente como responsável por seu histórico e afirma que Admin também pode realizar as ações de Cliente.
- Página 3, seção 2, **Visão geral do sistema**: pedidos têm instante, estado e itens referentes a produto e quantidade; clientes podem visualizar seus próprios pedidos. A definição de cadastro e de outros estados não autoriza implementar suas demais jornadas nesta feature.
- Página 4, seção 4, **Modelo conceitual**: preço também é armazenado no item para preservar o valor da venda quando o preço do produto mudar. O modelo complementa essa regra de domínio; não determina tecnologia, formato de consulta ou operações novas.
- O PDF não detalha fluxo, campos de lista/detalhe ou paginação de **Visualizar pedidos**. Não se atribuem a ele as decisões de apresentação abaixo, nem se acrescentam funcionalidades a partir do UML ou do livro do curso.

### Decisões de apresentação e escopo adotadas no pedido atual

- Conta com CLIENT ou ADMIN consulta exclusivamente seus próprios pedidos, inclusive Admin sem CLIENT. Não há autorização administrativa para compras de terceiros.
- Lista paginada com identificação, instante, estado atual armazenado e total; detalhe com os mesmos dados e itens na ordem registrada.
- Item com identificação e nome atual do produto, quantidade registrada, preço histórico e subtotal. Nome atual não é nome da época da compra; não acrescentar snapshots de nome ou imagem.
- Contagem e paginação restritas ao titular, ordem determinística, resultados vazios coerentes, leitura sem mutações e sem informação sensível.
- Preservar representação monetária exata e comportamentos das Features 001–006. Exclusões expressas aparecem na seção de escopo abaixo.

### Assunções delimitadoras

- Para tornar a ordem determinística verificável, adota-se como assunção a apresentação do mais recente primeiro e, no empate, identificador decrescente. É a concretização da recomendação anterior, não regra atribuída ao PDF nem nova aprovação explícita inventada.
- Pedido alheio e inexistente têm o mesmo resultado de negócio para conta autorizada. Isso não promete igualdade de tempo de processamento ou outro comportamento operacional não especificado.
- Navegação sem perdas considera conjunto estável entre solicitações. Não se acrescenta garantia de fotografia imutável diante de novas compras ou alterações concorrentes.
- Padrões e limites operacionais de paginação, representação concreta de seus controles e mensagens/códigos de falha serão detalhados no planejamento, respeitando os resultados de negócio definidos. Não se herdam automaticamente os valores do catálogo nem sua ordenação por nome.
- A leitura apresenta o estado que já existe, sem exigir novos estados. No recorte integrado anterior, o registro inicia a compra aguardando pagamento; isso não constitui pagamento implementado.
- Pedidos já registrados e seus produtos associados são a base da consulta. Não se exige sign up, migração de compras, regularização do cadastro atual ou histórico adicional para torná-los consultáveis.
- Não há dúvida material bloqueante de negócio neste recorte; escolhas de implementação não são tratadas como clarificações de negócio pendentes. Esta especificação não comprova implementação ou execução dos cenários.

### Dependências e decisões anteriores preservadas

- A [constituição](../../.specify/memory/constitution.md) governa o projeto. Rotas, representações técnicas, DTOs, consultas e organização da solução pertencem ao planejamento posterior.
- [Feature 001](../001-consulta-catalogo-produtos/spec.md), [Feature 002](../002-visualizar-detalhes-produto/spec.md) e [Feature 006](../006-filtrar-catalogo-produtos/spec.md): catálogo, detalhes públicos e filtro de nome continuam independentes das consultas protegidas de compras.
- [Feature 003](../003-login-autenticacao/spec.md): fornece login por nome e senha e identificação autenticada existente; emissão de credencial não prova que a nova consulta já esteja autorizada.
- [Feature 004](../004-registrar-pedido/spec.md) e seu [contrato de registro](../004-registrar-pedido/contracts/README.md): fornecem titularidade, instante, ordem dos itens, valores históricos e representação monetária exata. A confirmação de criação não equivale à consulta posterior.
- A 004 excluiu consulta e pagamento de seu recorte. Esta feature acrescenta acesso posterior sem reescrever aquele histórico ou classificar sua ausência anterior como defeito. Cada envio válido continua criando outro pedido, inclusive após perda da resposta anterior; ler não é recuperar idempotentemente uma confirmação.
- O carrinho permanece mantido pelo consumidor; gestão e esvaziamento pertencem à futura interface, sem pós-condição nova nesta consulta.
- [Feature 005](../005-manter-produtos/spec.md): manutenção administrativa preserva compras e impede excluir produto referenciado; alteração do cadastro atual não reescreve valores da venda. Nome, descrição ou imagem antigos não foram congelados por essa garantia financeira.
- A exceção histórica do Gate IV da 005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito**, conforme seu [plano](../005-manter-produtos/plan.md). Esta especificação não a amplia, corrige ou reclassifica.
- Base de origem: `b092a6849177a7d72c40c67f896f0b70a56e0f10`, main com Features 001–006 integradas, conforme investigação anterior. Esta etapa cria somente requisitos e checklist; resultados anteriores de testes e CI não são execução ou aprovação da Feature 007.

### Fora do escopo

- Pagamento, quitação, transições de estado, cancelamento, envio, entrega ou edição de pedidos/itens.
- Relatório ou filtro por data, agregações de vendas e consultas administrativas de compras de terceiros.
- Gestão ou esvaziamento de carrinho, cadastro público, atualização de perfil ou manutenção de contas/perfis.
- Interface visual, implantação e validação de ambiente de produção.
- Recuperação idempotente de confirmação, reconhecimento de repetição ou alteração da decisão de novo pedido por envio válido.
- Snapshots ou migração de nome/imagem, histórico adicional de produtos, novas regras de preço, frete, descontos, moeda ou conversão.
- Alterar documentos/contratos históricos ou a constituição para acomodar esta extensão.
- Planejamento, contratos novos, tarefas, implementação ou execução de testes neste checkpoint.
