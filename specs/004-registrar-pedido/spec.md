# Feature Specification: Registrar pedido

**Feature Branch**: `004-registrar-pedido`

**Created**: 2026-10-01

**Status**: Ready for planning — Q1, A1 e preço estritamente positivo no pedido aprovados; A2 e A3 adotadas como assunções para planejamento, sem pendência de revisão.

**Input**: User description: "Cliente ou Admin autenticado registra um pedido para si a partir de um carrinho não vazio, com produtos existentes, quantidades inteiras positivas, preços vigentes obtidos pelo sistema, identificação, instante e estado aguardando pagamento. Retornar itens e totais, preservar os preços registrados e rejeitar falhas sem gravação parcial. Preservar catálogo, detalhes públicos e login. O consumidor mantém o carrinho; seu esvaziamento pertence à futura interface. Registrar assunções propostas e dúvidas materiais. Não incluir pagamentos, histórico ou consulta de pedidos, CRUD administrativo, cadastro completo, frontend ou deploy."

## Clarifications

### Session 2026-10-01

- Q: Q1 — um novo envio equivalente pode criar outro pedido ou deve reconhecer a confirmação anterior, inclusive quando sua resposta foi perdida? → A: Decisão aprovada: cada solicitação válida de registro cria um novo pedido, com identificador próprio, mesmo quando seus itens são iguais aos de uma solicitação anterior. Se um pedido foi salvo e sua resposta se perdeu, um novo envio válido cria outro pedido. Reconhecimento de repetição e idempotência ficam fora desta feature. Esta decisão não aprova A1–A3.
- Q: A1 — o mesmo produto em mais de uma linha deve ser consolidado ou rejeitado? → A: Decisão aprovada nesta revisão: rejeitar integralmente a solicitação, sem consolidar quantidades e sem salvar pedido ou itens, mesmo quando os demais itens forem válidos.
- Q: O pedido pode conter produto com preço vigente zero ou negativo? → A: Decisão aprovada nesta revisão: cada produto do pedido deve ter preço vigente estritamente positivo. Um produto com preço zero ou negativo causa rejeição integral, mesmo quando os demais itens forem válidos, sem salvar pedido ou itens. A regra vale para o registro do pedido; o alinhamento do cadastro será tratado em etapa própria, preservando Product, catálogo, detalhes e suas validações nesta etapa.

## User Scenarios & Testing *(mandatory)*

Os cenários abaixo são critérios para validação futura. Sua presença não comprova implementação nem execução de testes. Q1, A1 e a exigência de preço estritamente positivo no pedido são decisões aprovadas. A2 e A3 são assunções adotadas para planejamento e tornam a especificação verificável; sua adoção não constitui uma nova declaração de aprovação explícita do usuário.

### User Story 1 - Registrar a compra para a própria conta (Priority: P1)

Como usuário autenticado com perfil Cliente ou Admin, quero confirmar os itens do meu carrinho e receber os dados do pedido criado para saber quais produtos, quantidades e valores compõem minha compra.

**Why this priority**: Entrega o resultado principal da feature, vinculando a compra à conta correta e registrando seus valores.

**Independent Test**: Confirmar um carrinho válido com dois produtos, uma vez como Cliente e outra como Admin, e verificar um pedido completo para a conta que confirmou cada compra, com identificação, instante, estado, preços, subtotais e total.

**Acceptance Scenarios**:

1. **Given** um Cliente autenticado e dois produtos existentes com preços vigentes de 10,00 e 7,50, **When** confirma respectivamente 2 e 3 unidades, **Then** recebe um pedido pertencente à sua conta, com identificador, instante de criação e estado aguardando pagamento; os itens registram os preços de 10,00 e 7,50, os subtotais são 20,00 e 22,50 e o total é 42,50, conforme A2 e A3.
2. **Given** um Admin autenticado, mesmo sem o perfil Cliente, e um carrinho válido, **When** confirma a compra, **Then** registra um pedido para si sob as mesmas regras aplicáveis ao Cliente.
3. **Given** uma conta autenticada com ambos os perfis, Cliente e Admin, **When** confirma uma compra válida, **Then** registra um único pedido para si naquela confirmação, sem duplicação por possuir dois perfis.
4. **Given** uma confirmação válida, **When** o registro termina com sucesso, **Then** todos os itens enviados são registrados e a resposta apresenta identificação do pedido, instante de criação correspondente ao registro, estado, identificação dos produtos, quantidades, preços unitários registrados, subtotais e total.
5. **Given** que o preço de um produto mudou desde sua inclusão no carrinho e o novo preço permanece estritamente positivo, **When** a compra é registrada, **Then** o item usa o preço vigente obtido pelo sistema no registro, conforme A2. Preço zero ou negativo segue a rejeição integral definida em FR-005 e US2.11/12.
6. **Given** um pedido registrado e uma alteração posterior no preço de um de seus produtos, **When** os valores já registrados são verificados na validação da feature, **Then** o preço unitário, o subtotal e o total da compra permanecem os mesmos, sem exigir uma funcionalidade de consulta de pedidos.
7. **Given** que o consumidor mantém seu carrinho, **When** o registro termina com sucesso, **Then** o sistema devolve a confirmação do pedido; esvaziar o carrinho é responsabilidade da futura interface e não é uma pós-condição entregue por esta feature.
8. **Given** duas confirmações válidas de compras distintas, **When** ambas são registradas, **Then** cada pedido recebe seu próprio identificador único e seu instante de criação.
9. **Given** um pedido salvo cuja resposta de confirmação se perdeu, **When** o usuário faz um novo envio válido com os mesmos itens, **Then** outro pedido é criado para sua conta, com identificador diferente, instante de criação do novo registro e estado aguardando pagamento; o pedido anterior permanece salvo e não é reutilizado nem substituído.
10. **Given** uma solicitação válida já registrada, **When** o usuário envia outra solicitação válida com os mesmos itens, **Then** é criado um novo pedido com identificador próprio, mesmo que a resposta anterior tenha sido recebida normalmente.

---

### User Story 2 - Impedir compras inválidas ou não autorizadas (Priority: P1)

Como usuário, quero que uma confirmação inválida seja rejeitada por inteiro para evitar compras atribuídas a outra pessoa, valores incorretos ou pedidos incompletos.

**Why this priority**: A integridade e a autorização são condições obrigatórias do registro, com a mesma prioridade do sucesso.

**Independent Test**: Enviar separadamente confirmações com dados inválidos, identidade inválida, perfil não autorizado e falha de gravação, verificando rejeição clara e ausência de pedido ou itens parcialmente salvos.

**Acceptance Scenarios**:

1. **Given** uma conta autorizada, **When** confirma uma solicitação sem itens, com lista vazia, item nulo ou item sem produto ou quantidade, **Then** o registro é rejeitado e nenhum pedido ou item é salvo.
2. **Given** uma conta autorizada, **When** informa quantidade nula, zero, negativa, fracionária, não representável ou de tipo incompatível, **Then** a solicitação é rejeitada sem converter uma fração em quantidade inteira e sem salvar pedido ou itens.
3. **Given** uma solicitação com um produto existente e outro inexistente, **When** confirma a compra, **Then** recebe indicação de produto não encontrado e nenhum dos itens nem o pedido é salvo.
4. **Given** uma confirmação sem autenticação ou com token inválido ou expirado, **When** tenta registrar o pedido, **Then** o registro é rejeitado sem gravação.
5. **Given** um token que não permite identificar uma conta existente, **When** tenta registrar o pedido, **Then** o registro é rejeitado sem associá-lo a outra conta e sem gravação.
6. **Given** uma conta autenticada que não possui Cliente nem Admin, **When** tenta confirmar a compra, **Then** recebe indicação de falta de permissão e nenhum pedido ou item é salvo.
7. **Given** um usuário autenticado, **When** tenta escolher outra conta como titular ou definir preços, subtotais, total, identificador, instante ou estado do pedido, **Then** a solicitação é rejeitada sem gravação; esses dados são determinados pelo sistema.
8. **Given** uma conta autorizada e uma solicitação com outros itens válidos, **When** envia o mesmo produto em mais de uma linha dessa solicitação, **Then** a confirmação inteira é rejeitada, sem consolidar quantidades e sem salvar pedido nem qualquer item, conforme A1 aprovada.
9. **Given** uma confirmação válida, **When** ocorre falha interna de processamento ou gravação antes da conclusão do registro, **Then** não é informado sucesso e não permanecem pedido ou itens parcialmente salvos.
10. **Given** uma confirmação cujos valores não podem ser representados sem perda, **When** o sistema calcula os valores da compra, **Then** rejeita integralmente a confirmação sem salvar pedido ou itens nem aceitar valores alterados silenciosamente.
11. **Given** uma conta autorizada e produtos existentes com quantidades inteiras positivas, sendo um produto com preço vigente zero e os demais com preços positivos, **When** confirma a compra, **Then** a solicitação inteira é rejeitada e nenhum pedido ou item é salvo, inclusive os itens válidos.
12. **Given** uma conta autorizada e produtos existentes com quantidades inteiras positivas, sendo um produto com preço vigente negativo e os demais com preços positivos, **When** confirma a compra, **Then** a solicitação inteira é rejeitada e nenhum pedido ou item é salvo, inclusive os itens válidos.
13. **Given** uma conta autorizada e uma solicitação com itens válidos antes e depois de um item com quantidade zero, **When** confirma a compra, **Then** a solicitação inteira é rejeitada sem salvar pedido ou qualquer item; os itens válidos não são registrados separadamente.

---

### User Story 3 - Preservar as jornadas já existentes (Priority: P2)

Como visitante ou usuário, quero continuar consultando o catálogo, visualizando detalhes públicos e realizando login com os mesmos comportamentos para que o registro de pedidos complemente as jornadas existentes.

**Why this priority**: Garante que a nova operação protegida não prejudique as features 001–003.

**Independent Test**: Validar todos os cenários de aceitação das features 001–003, incluindo consultas sem autenticação e login com credenciais válidas e inválidas.

**Acceptance Scenarios**:

1. **Given** um visitante sem autenticação, **When** consulta o catálogo, **Then** são preservados os dados apresentados, ordenação, paginação, resultados vazios e rejeições de entradas inválidas da feature 001.
2. **Given** um visitante sem autenticação, **When** consulta os detalhes de um produto existente ou inexistente, **Then** são preservados os dados, nomes de categorias sem repetição e indicação de produto não encontrado da feature 002.
3. **Given** credenciais válidas, inválidas ou incompletas, **When** solicita login, **Then** são preservadas as entradas por nome e senha, a emissão de token no sucesso, as rejeições e a confidencialidade da feature 003.
4. **Given** tentativas rejeitadas de registro de pedido, **When** são repetidas as jornadas de catálogo, detalhes e login, **Then** seus resultados permanecem compatíveis com as features existentes.

### Edge Cases

- Carrinho ausente, vazio, com item nulo ou informação obrigatória ausente: rejeitar integralmente.
- Um único item inválido entre itens válidos, por produto inexistente, quantidade inválida, preço zero ou negativo ou repetição do produto na mesma solicitação: rejeitar toda a confirmação, sem salvar pedido ou qualquer item.
- Quantidade fracionária não pode ser truncada; valores não representáveis não podem ser aceitos com perda silenciosa de informação.
- Produto repetido na mesma solicitação: A1 aprovada determina rejeição integral sem consolidação de quantidades e sem gravação. Isso não se confunde com itens iguais em solicitações válidas distintas, que criam novos pedidos conforme Q1.
- Produto com preço vigente zero ou negativo: rejeitar integralmente o registro do pedido, mesmo com os demais itens válidos. A existência de um produto de preço zero no catálogo não o torna elegível para esta compra.
- Mudança de preço antes do registro: aplicar A2; mudança depois do registro: preservar os valores da compra.
- Token recebido sem identidade utilizável ou referente a conta ausente: rejeitar; não deduzir outro comprador.
- Admin sem Cliente pode comprar para si; conta com ambos os perfis segue as mesmas regras; conta sem Cliente nem Admin não pode comprar.
- Falha ao registrar um dos itens: nenhum pedido ou conjunto incompleto de itens permanece salvo.
- Resposta perdida após registro completo: não equivale a gravação parcial. Um novo envio válido cria outro pedido com identificador próprio e mantém o anterior salvo, conforme Q1; não há reconhecimento de repetição ou idempotência nesta feature.
- Preservar preços de compras já registradas não implica entregar uma tela, listagem ou operação de consulta ao histórico.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir que uma conta autenticada com perfil Cliente ou Admin registre um pedido para si; Admin não precisa acumular Cliente para comprar.
- **FR-002**: O sistema DEVE receber os itens enviados pelo consumidor e exigir ao menos um item. A manutenção do carrinho fica no consumidor.
- **FR-003**: O pedido DEVE pertencer exclusivamente à conta autenticada. Tentativas de escolher outro titular DEVEM ser rejeitadas sem gravação.
- **FR-004**: Cada item DEVE referenciar um produto existente e apresentar quantidade inteira positiva. Informações obrigatórias ausentes, tipos incompatíveis e quantidades inválidas DEVEM ser rejeitados sem gravação.
- **FR-005**: O sistema DEVE obter o preço unitário vigente de cada produto no momento do registro, conforme A2, e exigir que seja estritamente positivo. Produto com preço zero ou negativo DEVE causar rejeição integral, mesmo quando os demais itens forem válidos, sem salvar pedido ou itens. No sucesso, o sistema DEVE guardar o preço unitário obtido. O consumidor não pode definir esse preço. A exigência de preço positivo no pedido é uma decisão aprovada nesta revisão.
- **FR-006**: O sistema DEVE atribuir um identificador único e um instante de criação ao pedido e iniciar seu estado como aguardando pagamento.
- **FR-007**: O resultado de sucesso DEVE apresentar identificação do pedido, instante, estado, itens com identificação dos produtos, quantidades e preços unitários registrados, subtotais e total.
- **FR-008**: Conforme A3, o subtotal de cada item DEVE ser seu preço unitário registrado multiplicado pela quantidade, e o total DEVE ser a soma dos subtotais. Valores não representáveis sem perda DEVEM causar rejeição integral, sem alteração silenciosa.
- **FR-009**: Alterações posteriores no preço do produto NÃO DEVEM modificar os preços registrados, os subtotais ou o total da compra já realizada.
- **FR-010**: O sistema DEVE validar o token existente e identificar a conta autenticada antes de permitir o registro. Autenticação ausente, inválida, expirada ou sem conta identificável DEVE impedir a gravação.
- **FR-011**: Uma conta autenticada sem Cliente nem Admin NÃO DEVE registrar pedidos. A rejeição por falta de permissão DEVE ser clara.
- **FR-012**: O registro DEVE ocorrer integralmente: dados inválidos, produtos inexistentes, preços zero ou negativos, produtos repetidos na mesma solicitação, autenticação inválida, falta de permissão ou falhas de processamento ou gravação antes da conclusão NÃO DEVEM deixar pedido ou itens parcialmente salvos nem informar sucesso. Um item inválido NÃO DEVE permitir a gravação separada dos demais itens válidos.
- **FR-013**: Tentativas de fornecer preço, subtotal, total, identificador, instante ou estado como dados escolhidos pelo consumidor DEVEM ser rejeitadas sem gravação.
- **FR-014**: Conforme A1 aprovada nesta revisão, o mesmo produto em mais de uma linha da solicitação DEVE causar rejeição integral, sem consolidação de quantidades e sem salvar pedido ou qualquer item, mesmo quando os demais itens forem válidos.
- **FR-015**: O sistema DEVE preservar todos os comportamentos e contratos das features 001, 002 e 003, inclusive acesso público ao catálogo e aos detalhes e login por nome e senha.
- **FR-016**: O sucesso do registro DEVE devolver a confirmação do pedido. O esvaziamento do carrinho pertence à futura interface e NÃO DEVE ser declarado como comportamento implementado pelo backend desta feature.
- **FR-017**: Cada solicitação válida de registro DEVE criar um novo pedido com identificador próprio, mesmo quando seus itens forem iguais aos de uma solicitação anterior. Se um pedido foi salvo e sua resposta se perdeu, um novo envio válido DEVE criar outro pedido, mantendo o anterior salvo. Reconhecimento de repetição e idempotência ficam fora desta feature, conforme a decisão aprovada de Q1.

### Key Entities *(include if feature involves data)*

- **Conta autenticada**: Identidade existente à qual o pedido pertence, com permissão de registro dada pelo perfil Cliente ou Admin. Não implica cadastro completo ou gestão de perfis nesta feature.
- **Produto**: Item existente do catálogo, identificado na confirmação e fonte do preço vigente no registro. Sua inclusão no pedido exige preço estritamente positivo, independentemente da aceitação de zero no modelo atual de produtos.
- **Carrinho do consumidor**: Conjunto de produtos e quantidades mantido pelo consumidor. Seus itens são enviados para confirmação; sua gestão e seu esvaziamento não são entregues pelo backend.
- **Pedido**: Compra registrada para a conta autenticada, com identificador, instante de criação, estado aguardando pagamento, itens e total.
- **Item do pedido**: Relação entre o pedido e um produto, contendo quantidade, preço unitário registrado e subtotal. Seu preço representa o valor da compra e não acompanha alterações futuras do catálogo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários de confirmação válida por Cliente e por Admin, o usuário recebe a confirmação de um pedido completo para si em um único envio, com identificação, instante, estado e todos os itens.
- **SC-002**: Em 100% dos cenários de registro bem-sucedido, os preços registrados são estritamente positivos e correspondem aos preços vigentes no registro, cada subtotal corresponde ao preço vezes a quantidade e o total corresponde à soma dos subtotais, conforme A2 e A3.
- **SC-003**: Em 100% dos cenários de alteração posterior de preço, os valores da compra já registrada permanecem inalterados.
- **SC-004**: Em 100% dos cenários de rejeição definidos na User Story 2, a tentativa não cria pedido nem itens; em 100% dos cenários de falha interna anterior à conclusão, não permanece gravação parcial nem é informado sucesso.
- **SC-005**: Em 100% dos cenários de titularidade e permissão, Cliente e Admin compram apenas para sua própria conta, e contas sem esses perfis não registram pedidos.
- **SC-006**: 100% dos cenários de aceitação das features 001–003 continuam atendidos, com acesso público ao catálogo e aos detalhes e resultados de login preservados.
- **SC-007**: Em todos os cenários de sucesso, o usuário consegue identificar os produtos, quantidades e valores de sua compra na própria confirmação, sem precisar consultar um histórico ou executar pagamento.
- **SC-008**: Em 100% dos cenários com duas solicitações válidas de registro contendo os mesmos itens, são criados dois pedidos completos com identificadores distintos. Isso inclui a perda da resposta do primeiro pedido após seu registro: o segundo envio válido cria outro pedido e o primeiro permanece salvo.
- **SC-009**: Em 100% dos cenários com produto repetido na mesma solicitação, preço vigente zero ou negativo ou um item inválido entre itens válidos, toda a solicitação é rejeitada e nenhum pedido ou item é salvo. Produtos repetidos não têm suas quantidades consolidadas.

## Assumptions

### Decisões aprovadas e assunções adotadas para planejamento

- **A1 — Produto repetido — aprovada nesta revisão**: O mesmo produto em mais de uma linha causa rejeição integral da solicitação, sem consolidação de quantidades e sem salvar pedido ou itens, mesmo quando os demais itens forem válidos. Aplicação em FR-014 e US2.8.
- **Preço no pedido — decisão aprovada nesta revisão**: Exigir preço vigente estritamente positivo para cada produto; zero ou negativo causa rejeição integral, mesmo com os demais itens válidos. Aplicação em FR-005, US2.11/12 e SC-002/009. Essa decisão não altera a situação atual do cadastro. A2 e A3 são adotadas separadamente como assunções para planejamento.
- **A2 — Preço vigente — assunção adotada para planejamento**: Usar o preço vigente obtido pelo sistema no registro, mesmo que difira do valor anteriormente mostrado no carrinho. Não congelar preços no carrinho nem solicitar uma segunda confirmação de preço nesta feature.
- **A3 — Cálculo — assunção adotada para planejamento**: Calcular subtotal como preço registrado × quantidade e total como soma dos subtotais, sem frete, descontos ou acréscimos. Não acrescentar funcionalidades de cálculo desses componentes.

### Dependências e fontes

- [Constituição](../../.specify/memory/constitution.md): rege o projeto; as soluções técnicas e a revisão de conformidade pertencem ao planejamento.
- [Feature 001](../001-consulta-catalogo-produtos/spec.md) e [feature 002](../002-visualizar-detalhes-produto/spec.md): fornecem produtos existentes e comportamentos públicos a preservar. Seus contratos existentes continuam como referência de regressão.
- [Feature 003](../003-login-autenticacao/spec.md): fornece login e emissão do token existente. Registrar pedidos depende adicionalmente de validar esse token recebido e identificar a conta a que ele se refere; emitir token não demonstra que uma operação já esteja protegida.
- É necessária a identificação dos perfis Cliente e Admin da conta autenticada. A emissão de tokens na feature 003 não definiu esses perfis nem a autorização para comprar; esta feature delimita somente a permissão necessária ao registro.
- A permissão de Admin comprar para si vem explicitamente da descrição de requisitos fornecida nesta solicitação. Cliente ou Admin autoriza o registro, sem exigir a combinação dos perfis nem conceder operações administrativas adicionais.
- Contas, credenciais e produtos já existem. A preparação de dados para validação não implica entregar cadastro ou manutenção desses recursos.
- O consumidor mantém o carrinho e envia seus itens. Esvaziá-lo após sucesso depende da futura interface, fora desta entrega.
- **Situação atual do cadastro de produtos**: [Product](../../src/main/java/com/devsuperior/dscommerce/entities/Product.java) atualmente aceita preço zero. A regra aprovada nesta revisão exige preço estritamente positivo para registrar o pedido. O alinhamento do cadastro será tratado em etapa própria; Product, catálogo, detalhes e suas validações permanecem fora das alterações desta etapa. A rejeição de preço negativo no pedido é exigida mesmo que esse valor não satisfaça a validação atual de produtos.

### Dúvidas materiais

- **Q1 — Repetição de confirmação — resolvida**: Decisão aprovada registrada em Clarifications e FR-017, com aceitação em US1.9/10 e SC-008. Cada solicitação válida cria outro pedido, inclusive após perda da resposta de um pedido salvo; não há reconhecimento de repetição ou idempotência nesta feature. A aprovação de A1 e da regra de preço positivo foi registrada separadamente; A2 e A3 são assunções adotadas para planejamento.
- **A1 e preço positivo — resolvidos**: As duas decisões aprovadas estão registradas em Clarifications, FR-005/014 e seus cenários de rejeição integral; não permanecem dúvidas sobre consolidação de quantidades ou aceitação de preço zero no pedido.
- **A2 e A3 — consolidadas**: São assunções adotadas para planejamento, sem pendência de revisão e sem nova declaração de aprovação explícita do usuário. Não são soluções técnicas nem regras já implementadas. O preço é obtido pelo sistema no registro, sem congelamento no carrinho ou segunda confirmação; subtotal = preço registrado × quantidade; total = soma dos subtotais, sem frete, descontos ou acréscimos.
- Cliente e Admin autorizados a comprar para si estão definidos pelo pedido atual e não permanecem como dúvida. Quantidades máximas e capacidade de representar totais devem ser explicitadas no planejamento e no futuro contrato, preservando a rejeição de perda silenciosa de informação.

### Fora do escopo

- Reconhecimento de repetição e idempotência: um novo envio válido não recupera, reutiliza ou substitui o pedido anterior; cria outro pedido conforme FR-017.
- Processar pagamentos, registrar quitação ou executar transições posteriores ao estado inicial.
- Histórico, listagem ou consulta de pedidos. A conservação dos preços da compra é uma regra de registro, não uma funcionalidade de acesso ao histórico.
- CRUD administrativo, cadastro completo, manutenção de contas ou atribuição administrativa de perfis.
- Alterar Product, catálogo, detalhes ou suas validações para alinhar o cadastro à exigência de preço estritamente positivo no pedido. Esse alinhamento será tratado em etapa própria.
- Frontend, gestão de carrinho no backend, esvaziamento do carrinho pela futura interface ou deploy.
- Estoque, entrega, frete, descontos e outras funcionalidades de compra não descritas nesta solicitação.

Este checkpoint conclui a especificação revisada e seu checklist de qualidade, prontos para planejamento. Não afirma comportamento implementado e não executa planejamento, geração de tasks ou implementação.
