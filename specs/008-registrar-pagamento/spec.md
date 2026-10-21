# Feature Specification: Registrar pagamento

**Feature Branch**: `008-registrar-pagamento`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "ADMIN autenticado e com perfil atual registra quitação integral e única de pedido de qualquer titular, informando a ocorrência entre criação do pedido e referência única fixada no início da avaliação de negócio da tentativa. Aceitar somente pedido aguardando pagamento e sem quitação anterior; registrar pagamento e PAID integralmente, rejeitar repetição e impedir duas quitações concorrentes. Preservar dados históricos e consultas próprias da 007; documentar sua evolução sem alterar contratos anteriores nesta etapa. Sem cobrança real, movimentação financeira externa, planejamento, implementação ou testes."

## User Scenarios & Testing *(mandatory)*

Os cenários são critérios para validação futura, não resultados executados. As cinco histórias são necessárias ao recorte. As quatro decisões de negócio antes pendentes foram aprovadas pelo usuário e estão registradas em Assumptions.

### User Story 1 - Registrar uma quitação integral com sua ocorrência (Priority: P1)

Como ADMIN autenticado, quero registrar a quitação de um pedido existente, inclusive de outro titular, informando quando o pagamento ocorreu para que a compra passe a constar como paga.

**Why this priority**: Entrega o reconhecimento administrativo da quitação, sem executar cobrança ou movimentação financeira externa.

**Independent Test**: Quitar pedidos elegíveis com instantes conhecidos, conferir confirmação, ocorrência e estado final e comparar os dados históricos antes e depois.

**Acceptance Scenarios**:

1. **Given** ADMIN atual e pedido alheio aguardando pagamento, sem quitação, com 2 unidades a 10,00 e 3 a 7,50 nos valores históricos, **When** informa esse pedido e ocorrência válida, **Then** recebe confirmação com identificador do pedido, ocorrência informada e PAID; existe uma única quitação integral de 42,50, sem escolher outro valor.
2. **Given** ADMIN atual e pedido próprio elegível, **When** informa ocorrência válida, **Then** registra a quitação sob as mesmas regras de pedido alheio, sem exigir também CLIENT.
3. **Given** pedido com duas unidades vendidas a 10,00 e produto cujo preço atual passou a 12,00, **When** ADMIN registra a quitação, **Then** o valor integral continua 20,00, e comprador, criação, produtos, ordem dos itens, quantidades e preços históricos permanecem iguais.
4. **Given** pedido criado às 10h, ocorrência às 11h e referência da tentativa fixada às 12h do mesmo dia, **When** ADMIN informa a ocorrência das 11h, **Then** esse instante é conservado, sem substituição pela referência da tentativa ou pela conclusão definitiva do registro.
5. **Given** pedido elegível e ocorrência exatamente igual à criação, **When** ADMIN solicita a quitação e a referência da tentativa é igual ou posterior à criação, **Then** o limite inferior é aceito.
6. **Given** pedido elegível e ocorrência exatamente igual à referência única fixada no início da avaliação de negócio da tentativa, **When** ADMIN registra pagamento com as demais condições atendidas, **Then** o limite superior é aceito com essa mesma referência, mesmo que a conclusão definitiva do registro ocorra depois; recebimento e commit não substituem a referência.

---

### User Story 2 - Impedir registros não autorizados ou inválidos (Priority: P1)

Como responsável pelas compras, quero que somente ADMIN atual registre quitação válida, sem alterar pedidos por dados inválidos ou permissões antigas.

**Why this priority**: Autorização atual, integridade temporal e elegibilidade são condições obrigatórias da quitação.

**Independent Test**: Tentar a operação com identidades e perfis diferentes, pedidos inexistentes e dados inválidos; comparar os dados e conferir rejeições seguras.

**Acceptance Scenarios**:

1. **Given** autenticação ausente, inválida, expirada ou sem conta existente identificável, **When** tenta registrar pagamento, **Then** recebe rejeição de autenticação, sem dados restritos do pedido nem alteração.
2. **Given** conta autenticada somente com CLIENT ou sem perfil autorizado, **When** tenta registrar pagamento próprio ou alheio, **Then** recebe rejeição por falta de permissão, sem informação restrita da compra nem alteração.
3. **Given** credencial emitida antes de uma mudança de perfil, **When** a conta tenta registrar pagamento, **Then** a permissão depende do perfil atual: ADMIN removido impede a operação; ADMIN atualmente concedido, isolado ou combinado com CLIENT, permite a tentativa sob as demais regras.
4. **Given** ADMIN atual e identificação válida sem pedido correspondente, **When** tenta registrar pagamento, **Then** recebe indicação de pedido inexistente, sem criar pagamento, pedido ou outro dado.
5. **Given** ADMIN atual, **When** omite identificação do pedido ou ocorrência, ou informa identificação ou ocorrência inválida, **Then** recebe indicação clara de entrada inválida, sem alteração.
6. **Given** pedido criado às 10h, **When** ADMIN informa ocorrência anterior às 10h, **Then** a tentativa é rejeitada, sem ajustar automaticamente o instante ou alterar o pedido.
7. **Given** referência única fixada no início da avaliação de negócio da tentativa, **When** ADMIN informa ocorrência posterior a essa referência, **Then** a tentativa é rejeitada, mesmo que o tempo avance até seu término, sem deslocar o limite superior, acrescentar tolerância ou substituir a ocorrência pelo instante atual.
8. **Given** ADMIN atual e pedido elegível, **When** tenta escolher valor parcial ou diferente do total histórico, alterar comprador, itens, preços ou criação, ou definir diretamente o estado na solicitação, **Then** a tentativa é rejeitada sem alterar dados; somente pedido e ocorrência são entradas de negócio desta operação.
9. **Given** pedido em estado diferente de aguardando pagamento, mesmo sem quitação registrada, **When** ADMIN tenta registrar pagamento, **Then** recebe rejeição por estado incompatível, sem correção automática, pagamento ou mudança de estado. O cenário não exige implementar outros estados ou suas transições.

---

### User Story 3 - Preservar quitação única diante de repetição e concorrência (Priority: P1)

Como ADMIN, quero que novas tentativas não dupliquem nem substituam uma quitação já aceita, inclusive quando duas pessoas atuam ao mesmo tempo.

**Why this priority**: Uma compra paga deve corresponder a uma única quitação integral.

**Independent Test**: Repetir tentativa concluída e coordenar duas tentativas válidas concorrentes para o mesmo pedido; conferir resultados e a única ocorrência conservada.

**Acceptance Scenarios**:

1. **Given** pedido quitado com ocorrência conhecida, **When** ADMIN repete pedido e ocorrência, **Then** recebe rejeição de repetição, sem devolver sucesso reutilizado nem substituir ou criar pagamento.
2. **Given** pedido quitado, **When** ADMIN tenta registrar outra ocorrência que seria temporalmente válida, **Then** a nova tentativa é rejeitada e a ocorrência original permanece igual.
3. **Given** pedido com quitação anterior, mesmo que seu estado indique aguardando pagamento, **When** ADMIN tenta outra quitação, **Then** a existência anterior impede novo registro e nenhum dado é corrigido automaticamente.
4. **Given** pedido elegível e duas tentativas válidas concorrentes, por ADMINs autorizados, com ocorrências válidas iguais ou diferentes e sem outras falhas, **When** ambas terminam, **Then** exatamente uma é aceita e a outra rejeitada; existe um pagamento e o pedido está PAID com a ocorrência da tentativa aceita, sem sobrescrita pela perdedora.
5. **Given** dois pedidos elegíveis diferentes, **When** ADMINs autorizados enviam solicitações válidas de quitação concorrentemente e sem outras falhas, **Then** ambas as solicitações são aceitas, cada uma vinculada exclusivamente ao respectivo pedido, sem confundir identidades, ocorrências ou valores entre compras.

---

### User Story 4 - Distinguir operação não concluída de confirmação não recebida (Priority: P1)

Como ADMIN, quero que falhas anteriores à conclusão não deixem uma quitação incompleta e que a perda da resposta não apague um pagamento já registrado.

**Why this priority**: A integridade do resultado não pode depender da entrega da resposta ao consumidor.

**Independent Test**: Provocar falhas antes da conclusão definitiva e perda de transmissão depois dela; conferir separadamente os dados finais e o resultado de uma nova tentativa.

**Acceptance Scenarios**:

1. **Given** pedido elegível, **When** falha interna impede concluir o processamento da quitação, **Then** não é informado sucesso e o pedido permanece aguardando pagamento, sem pagamento novo ou outra alteração parcial.
2. **Given** tentativa que precisa registrar pagamento e PAID, **When** ocorre falha de gravação antes da conclusão definitiva, inclusive depois de uma dessas mudanças ter sido iniciada, **Then** após o término não permanece pagamento sem a mudança correspondente de estado nem PAID sem pagamento; os dados anteriores são preservados.
3. **Given** quitação concluída definitivamente, **When** ocorre falha posterior de transmissão da confirmação, **Then** pagamento e PAID permanecem registrados; nova tentativa é rejeitada como repetição e o titular pode consultar PAID. Não se promete desfazer a operação concluída porque sua resposta não foi recebida.
4. **Given** falha interna ao identificar a conta ou concluir a operação, **When** o sistema apresenta o resultado, **Then** informa falha de processamento com segurança, sem convertê-la em pedido inexistente, credencial inválida, repetição ou sucesso; não expõe credenciais, dados restritos de terceiros ou detalhes internos.

---

### User Story 5 - Reconhecer PAID nas consultas próprias e preservar jornadas (Priority: P1)

Como titular da compra, quero visualizar seu estado pago pela lista e pelo detalhe existentes, conservando o acesso restrito e os valores da compra.

**Why this priority**: A quitação precisa ser observável pelo titular sem transformar consulta própria em consulta administrativa geral.

**Independent Test**: Consultar antes/depois da quitação como titular e terceiro, comparar campos, paginação e valores e exercitar regressões das Features 001–007.

**Acceptance Scenarios**:

1. **Given** pagamento concluído, **When** o titular autorizado consulta lista e detalhe da 007, **Then** ambos apresentam PAID com os mesmos campos anteriores, sem acrescentar ocorrência, valor, identificação ou outros dados de pagamento às respostas.
2. **Given** ADMIN que quitou pedido de outra conta, **When** usa lista ou detalhe da 007, **Then** continua limitado aos próprios pedidos: o alheio não aparece em conteúdo ou contagens e seu detalhe tem o mesmo resultado de não encontrado de um pedido inexistente. O titular continua acessando sua compra.
3. **Given** cinco pedidos próprios com criações conhecidas e empates, **When** um pedido antigo é quitado e o titular percorre páginas de tamanho dois, **Then** continua recebendo os cinco em 2/2/1, com totais cinco/três e ordem por criação decrescente e identificador decrescente, sem reordenar pelo pagamento.
4. **Given** consultas próprias bem-sucedidas, vazias ou rejeitadas, **When** são repetidas após a extensão, **Then** preservam autorização, ausência de mutações, resultados vazios e falhas, ordem dos itens, nome atual do produto, representação monetária existente e valores históricos exatos; consultar não registra pagamento ou executa transição.
5. **Given** jornadas existentes, **When** são exercitadas após esta extensão, **Then** catálogo e detalhes continuam públicos, filtro de nome e paginação são preservados, login continua por nome e senha, registro continua atômico e cria outro pedido por envio válido, e manutenção administrativa mantém validações, autorização e proteção das compras.
6. **Given** operação de quitação bem-sucedida ou rejeitada, **When** seus efeitos são conferidos, **Then** nenhuma cobrança ou movimentação financeira externa foi executada e somente quitação e estado do pedido alvo podem ter sido alterados no sucesso; outros pedidos, produtos, categorias, contas, perfis e carrinho permanecem iguais.

### Edge Cases

- Limites temporais são inclusivos: criação do pedido <= ocorrência do pagamento <= referência da tentativa. A referência é fixada uma única vez no início da avaliação de negócio, antes de qualquer alteração, e não muda até o término. Instante ausente, inválido, anterior à criação ou posterior à referência não é corrigido silenciosamente.
- Ocorrência não é substituída pelo lançamento administrativo. Representação e precisão serão definidas no planejamento sem perda silenciosa do instante.
- Estar aguardando pagamento não basta se já existe quitação; não ter quitação não basta se o estado é incompatível. Não há reparação automática de dados inconsistentes.
- Repetição com ocorrência igual ou diferente continua rejeitada, mesmo por outro ADMIN ou após perda da resposta anterior.
- Duas tentativas concorrentes válidas e sem outras falhas produzem exatamente uma aceitação por pedido. Tentativa que falhou antes da conclusão não conta como quitação aceita; nunca podem existir duas.
- Rejeição posterior não apaga a quitação anterior. Falha anterior à conclusão definitiva e falha posterior de transmissão têm consequências distintas.
- Alterar cadastro atual de produto não reescreve a compra ou o valor integral; quitação não aplica retroativamente regras de escrita de produto à compra histórica.
- ADMIN sem CLIENT é suficiente; ter ambos não duplica a operação. CLIENT isolado não registra nem mesmo pagamento próprio.
- Poder quitar pedido alheio não permite procurá-lo por consulta administrativa nova ou lê-lo nas consultas próprias.
- PAID precisa ser permitido pelo contrato vigente de consulta antes de se declarar a entrega compatível; existir um campo de estado não basta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir registrar pagamento somente a conta autenticada existente com ADMIN atual. ADMIN isolado DEVE ser suficiente; CLIENT isolado, ausência de ADMIN ou ausência de autenticação válida DEVEM impedir a operação. Permissões antigas NÃO DEVEM substituir o perfil atual.
- **FR-002**: ADMIN DEVE poder quitar pedido próprio ou de qualquer outro titular exclusivamente nesta operação. Essa permissão NÃO DEVE ampliar consultas próprias da 007 nem criar consulta administrativa geral.
- **FR-003**: As únicas entradas de negócio DEVEM ser identificação do pedido e ocorrência informada pelo ADMIN. Tentativas de escolher montante, comprador, criação, itens, preços ou estado DEVEM ser rejeitadas sem alteração.
- **FR-004**: A quitação DEVE ser integral e única. Seu valor DEVE decorrer exclusivamente da soma das quantidades multiplicadas pelos preços históricos do pedido, conservando os valores exatos; preço atual NÃO DEVE participar, e pagamentos parciais NÃO DEVEM ser aceitos.
- **FR-005**: Somente pedido existente, aguardando pagamento e sem quitação anterior DEVE ser elegível. Pedido inexistente, estado incompatível ou quitação anterior DEVEM impedir registro e correção automática de dados.
- **FR-006**: A ocorrência DEVE ser obrigatória e válida e satisfazer criação do pedido <= ocorrência do pagamento <= referência da tentativa, com limites inclusivos. O sistema DEVE fixar essa referência uma única vez no início da avaliação de negócio da tentativa, antes de qualquer alteração, e conservá-la até seu término. Recebimento da solicitação e conclusão definitiva do registro (commit) NÃO DEVEM substituir a referência nem deslocar o limite superior. O sistema NÃO DEVE substituir a ocorrência, tolerar valor posterior à referência ou corrigir valor inválido silenciosamente.
- **FR-007**: No sucesso, o resultado DEVE apresentar identificador do pedido, ocorrência registrada e PAID, confirmando a quitação vinculada à compra. Não se exige devolver dados pessoais do comprador ou o conteúdo completo do pedido.
- **FR-008**: Registrar pagamento e mudar aguardando pagamento para PAID DEVEM constituir uma única operação integral, concluída antes de confirmar sucesso. Não DEVE permanecer apenas uma dessas mudanças quando a operação não se conclui.
- **FR-009**: A quitação NÃO DEVE alterar comprador, identificação e criação do pedido, produtos referenciados, ordem dos itens, quantidades, preços históricos, subtotais ou total.
- **FR-010**: Nova tentativa sobre pedido quitado DEVE ser rejeitada como repetição, preservando pagamento e ocorrência originais, mesmo com entrada igual, outra ocorrência ou outro ADMIN. A confirmação anterior NÃO DEVE ser devolvida como novo sucesso.
- **FR-011**: Concorrência NÃO DEVE produzir dois pagamentos para o mesmo pedido. Com duas tentativas válidas para pedido elegível e sem outras falhas, exatamente uma DEVE ser aceita e a outra rejeitada, preservando a ocorrência aceita. Para dois pedidos elegíveis distintos, ambas as solicitações válidas sem outras falhas DEVEM ser aceitas, cada uma vinculada exclusivamente ao respectivo pedido.
- **FR-012**: Rejeições por autenticação, permissão, dados inválidos, pedido inexistente, repetição ou estado incompatível NÃO DEVEM produzir pagamento, mudança de estado ou outra alteração parcial; quitação anterior DEVE permanecer intacta.
- **FR-013**: Falhas internas de processamento ou gravação anteriores à conclusão definitiva NÃO DEVEM informar sucesso nem deixar pagamento ou mudança de estado parciais. Falha de processamento DEVE ser distinguida de rejeição de negócio ou de autenticação.
- **FR-014**: Falha de transmissão após conclusão definitiva NÃO DEVE desfazer quitação ou PAID nem ser tratada como prova de ausência de registro. Novo envio DEVE seguir a rejeição de repetição; não há promessa de entrega da resposta ou recuperação idempotente da confirmação.
- **FR-015**: Resultados DEVEM ser seguros, sem credenciais, segredos ou detalhes internos. Conta não autenticada ou sem ADMIN NÃO DEVE obter informação restrita do pedido pela tentativa. ADMIN autorizado DEVE distinguir pedido inexistente, entrada inválida, repetição, estado incompatível e falha interna quando aplicáveis; códigos e mensagens concretos ficam para planejamento.
- **FR-016**: Após sucesso, lista e detalhe próprios da 007 DEVEM permitir apresentar PAID como estado atual armazenado. A evolução do contrato vigente, hoje restrito a WAITING_PAYMENT, DEVE ser atribuída à 008; a especificação histórica da 007 NÃO DEVE ser reescrita como entrega de pagamento.
- **FR-017**: Respostas de lista e detalhe da 007 DEVEM conservar os campos existentes, sem acrescentar identificação, ocorrência, valor ou outros dados de pagamento.
- **FR-018**: Consultas da 007 DEVEM continuar limitadas à conta autenticada com CLIENT ou ADMIN e aos próprios pedidos, incluindo conteúdo, totais e detalhe. ADMIN que quita pedido alheio NÃO DEVE obter leitura alheia nessas consultas; pedido alheio e inexistente DEVEM manter o mesmo resultado de não encontrado.
- **FR-019**: Paginação, contagens, resultados vazios, ordem por criação decrescente e identificador decrescente, ordem dos itens, nomes atuais, representação monetária existente e valores históricos exatos das consultas DEVEM ser preservados. Ocorrência de pagamento NÃO DEVE substituir criação nem reordenar pedidos.
- **FR-020**: Consultar lista ou detalhe NÃO DEVE registrar pagamento, mudar estado ou modificar dados. No sucesso da quitação, somente pagamento e estado do pedido alvo DEVEM mudar; outros pedidos, produtos, categorias, contas, perfis e carrinho DEVEM permanecer iguais.
- **FR-021**: Comportamentos e contratos das Features 001–007 DEVEM ser preservados, exceto a evolução expressa da 008 para admitir PAID nas consultas. Permanecem acesso público, filtro de nome, detalhes, login, registro atômico com novo pedido por envio válido, manutenção administrativa, proteção referencial e valores históricos.
- **FR-022**: Registrar pagamento DEVE apenas registrar quitação declarada por ADMIN; NÃO DEVE cobrar, verificar recebimento em serviço externo ou executar movimentação financeira externa.

### Key Entities *(include if feature involves data)*

- **ADMIN atual**: Conta autenticada existente com permissão atual para registrar quitação, independentemente de ser o comprador.
- **Pedido elegível**: Compra existente aguardando pagamento e sem quitação anterior, com comprador, criação e composição histórica preservados.
- **Quitação integral**: Único reconhecimento de pagamento de todo o valor histórico de um pedido, com ocorrência e vínculo à compra; não representa cobrança externa.
- **Ocorrência do pagamento**: Instante declarado pelo ADMIN, no intervalo inclusivo entre criação do pedido e referência da tentativa.
- **Referência da tentativa**: Instante único fixado pelo sistema quando inicia a avaliação de negócio da solicitação de quitação, antes de qualquer alteração, conservado até o término dessa tentativa. Define seu limite temporal superior; não é o recebimento nem a conclusão definitiva do registro (commit), não substitui a ocorrência informada e não constitui novo campo obrigatório de saída.
- **Consulta própria**: Lista ou detalhe existente da 007, que passa a admitir PAID conservando titularidade, campos e dados históricos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos cenários válidos de US1, ADMIN conclui quitação integral em uma solicitação, recebe pedido, ocorrência e PAID, e há exatamente um pagamento, tanto para compra própria quanto alheia.
- **SC-002**: Em 100% dos cenários de autenticação e perfil atual de US2.1–3, somente ADMIN atual registra pagamento; tentativas não autorizadas não alteram dados nem revelam informação restrita da compra.
- **SC-003**: Em 100% dos cenários temporais, a mesma referência única do início da avaliação de negócio é usada até o término da tentativa: ocorrência igual à criação ou à referência é aceita; anterior à criação ou posterior à referência é rejeitada. Avanço do tempo até a conclusão não altera o resultado, e a ocorrência válida informada é conservada sem substituição; recebimento e commit não servem como referências alternativas.
- **SC-004**: Em 100% das repetições, a quitação original permanece única e inalterada, sem sucesso reutilizado. Cada par de tentativas concorrentes válidas ao mesmo pedido sem outras falhas produz uma aceitação, uma rejeição e um pagamento. Para dois pedidos elegíveis distintos, ambas as solicitações válidas sem outras falhas são aceitas e cada quitação pertence exclusivamente ao respectivo pedido.
- **SC-005**: Em 100% das rejeições e falhas anteriores à conclusão definitiva, não há alteração parcial. Na perda da resposta após conclusão, quitação e PAID permanecem e novo envio é rejeitado, sem prometer desfazer registro concluído.
- **SC-006**: Em 100% dos cenários de preservação, comprador, criação, composição e valores históricos permanecem iguais. A compra de 2 unidades a 10,00 e 3 a 7,50 continua totalizando 42,50, independentemente do catálogo atual.
- **SC-007**: Em 100% dos cenários de US5.1–4, pedidos quitados apresentam PAID nas consultas próprias com campos anteriores, representação e valores históricos preservados; terceiros continuam excluídos, inclusive ADMIN; cinco pedidos permanecem em páginas 2/2/1 pela criação e identificação. Consultas vazias e rejeitadas conservam seus resultados e nenhuma consulta provoca mutações.
- **SC-008**: Em 100% das regressões de US5.5–6, os comportamentos preservados das Features 001–007 continuam atendidos e nenhuma tentativa de quitação executa cobrança ou movimentação financeira externa.

## Assumptions

### Fontes: exigências expressas do PDF e limites do levantamento

- Fonte principal: C:/Users/ronal/Downloads/Documento de requisitos DSCommerce.pdf, oito páginas, SHA-256 ed1b2063012f25f44f944830351bcaa966e0562c03715948f5fd0932d9434a83. Reutiliza-se o levantamento desta conversa, que leu as oito páginas e inspecionou o UML; não houve nova investigação integral ou cópia do PDF nesta especificação.
- Página 5, seção 5, **Registrar pagamento**: salvar dados de pagamento de um pedido, com acesso somente Admin.
- Página 3, seção 2, **Visão geral do sistema**: quando o usuário paga um pedido, seu instante deve ser registrado; o texto reconhece aguardando pagamento e pago entre os estados.
- Página 4, seção 4, **Modelo conceitual**: preservação do preço da venda no item fundamenta valores históricos. O UML complementa o domínio, mas não determina automaticamente a próxima feature ou o mecanismo de quitação.
- O PDF não detalha pagamento parcial, origem do instante, repetição, concorrência, limites temporais, abrangência por titular, confirmação ou mecanismos de implementação. Essas regras decorrem das decisões aprovadas e assunções abaixo, não de exigências inventadas do documento.
- A prioridade de Registrar pagamento é recomendação adotada pelo usuário após o levantamento, não ordem de entrega exigida pelo PDF ou pelo livro do curso.

### Quatro decisões de negócio aprovadas

1. **Quitação integral e única**: sem pagamentos parciais; valor decorre da compra histórica, sem escolha de montante pelo ADMIN.
2. **Ocorrência informada pelo ADMIN**: entre criação do pedido e referência única fixada no início da avaliação de negócio da tentativa, conforme FR-006; não usar automaticamente o lançamento como ocorrência.
3. **Repetição rejeitada**: preservar quitação original sem sucesso reutilizado, inclusive após perda da confirmação.
4. **ADMIN pode quitar qualquer titular**: autorização restrita ao pagamento; consultas da 007 permanecem exclusivamente próprias.

### Objetivo, pré-condições, fluxo e dados mínimos

- Objetivo: reconhecer administrativamente uma quitação declarada, sem cobrança real ou movimentação financeira externa.
- Ator executor: ADMIN autenticado e atual, com ou sem CLIENT. Comprador é o titular da compra, não um titular escolhido na solicitação.
- Pré-condições: conta ADMIN atual; pedido existente, aguardando pagamento e sem quitação; ocorrência válida no intervalo permitido.
- Fluxo principal: ADMIN identifica pedido e ocorrência; sistema fixa a referência única ao iniciar a avaliação de negócio e verifica autorização, dados e elegibilidade usando essa referência; registra integralmente quitação e PAID; confirma pedido, ocorrência e estado.
- Entradas mínimas: identificação do pedido e ocorrência. Não se acrescentam montante informado, meio de pagamento, comprovante ou dados do comprador.
- Saída mínima: identificador do pedido, ocorrência registrada e PAID. Não se exige identificador independente de pagamento ou dados pessoais do titular.
- Pós-condição: uma quitação integral associada ao pedido, estado PAID e dados históricos conservados; consulta própria apresenta o estado sem novos campos de pagamento.

### Assunções delimitadoras

- Intervalo temporal inclusivo concretiza a expressão aprovada "entre". Para esta regra, momento do registro significa a referência única fixada no início da avaliação de negócio de cada tentativa, antes de qualquer alteração. Recebimento, avaliação e conclusão definitiva do registro (commit) são eventos distintos, sem referências intercambiáveis: somente o início da avaliação fixa o limite superior, que permanece constante até o término. Formato, precisão e mecanismo técnico ficam para planejamento, sem redefinir esse evento ou acrescentar tolerância a valor posterior à referência.
- Ocorrência precisa identificar instante utilizável; formatos, precisão e mensagens ficam para planejamento, sem perda silenciosa, substituição ou correção de valores inválidos.
- Com mais de uma causa de rejeição, não se impõe nova precedência entre erros de negócio; autenticação e permissão continuam impedindo divulgação de dados restritos. Cada causa é verificável isoladamente.
- Concorrência garante uma quitação por pedido. Duas tentativas válidas sem outras falhas resultam em uma aceitação; não se promete sucesso quando ambas falham antes da conclusão.
- Conclusão definitiva é a efetivação integral do registro, distinta da transmissão posterior. Não se acrescenta garantia de entrega, reenvio automático, reversão ou confirmação idempotente.
- Estados incompatíveis e combinações inconsistentes são rejeitados sem reparação automática; não se exige criar outros estados, jornadas ou migrações para exercitar elegibilidade.
- Critérios quantitativos descrevem conjuntos finitos de cenários; não são SLA, capacidade de produção ou aprovação já obtida.

### Dependências e evolução intencional da Feature 007

- A [constituição](../../.specify/memory/constitution.md) governa o projeto. Rotas, DTOs, persistência, formatos técnicos, códigos concretos e mecanismo de concorrência pertencem ao planejamento posterior.
- [003](../003-login-autenticacao/spec.md) fornece login por nome e senha; entregas seguintes fornecem identificação de conta e perfis atuais. A 008 exige autorização atual própria para quitar.
- [004](../004-registrar-pedido/spec.md) fornece pedido, comprador, criação, ordem dos itens e valores históricos. Novo pedido continua iniciando em WAITING_PAYMENT; cada envio válido continua criando outro pedido. A rejeição de repetição do pagamento não altera essa decisão.
- [005](../005-manter-produtos/spec.md) preserva compras e impede excluir produto referenciado. Sua exceção histórica do Gate IV permanece documentada, sem reclassificação por esta especificação.
- A [especificação da 007](../007-consultar-meus-pedidos/spec.md), FR-018, exige apresentar o estado atual armazenado sem executar transição na consulta; não enumera estados fechados. A regra é compatível com ler PAID após a operação da 008.
- O [OpenAPI atual da 007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml), nos estados de resumo e detalhe, restringe valores a WAITING_PAYMENT. PAID não é aceito pelo contrato atual: ampliar valores admitidos e descrições de compatibilidade será necessário na evolução atribuída à 008, antes de declarar entrega funcional compatível. Consumidores com enumeração fechada precisam acompanhar essa evolução.
- Preservar campos de lista/detalhe, titularidade de conteúdo e contagens, paginação, ordenação por criação/ID, nomes atuais, ordem dos itens e valores históricos exatos. Não incluir dados de pagamento nem autorizar leitura alheia ao ADMIN que quitou a compra.
- A 007 não deve ser reescrita como se tivesse entregue pagamento. Nenhuma especificação, contrato ou evidência anterior é alterada neste checkpoint; a evolução futura é responsabilidade da 008.
- [001](../001-consulta-catalogo-produtos/spec.md), [002](../002-visualizar-detalhes-produto/spec.md) e [006](../006-filtrar-catalogo-produtos/spec.md) são fontes das consultas públicas e filtro a preservar. Carrinho continua mantido pelo consumidor.
- Base verificada: main limpa em 5cd16709ea531ccae27230db19753e6c5533d12d, com Features 001–007 integradas. A branch 008-registrar-pagamento foi criada sem sobrescrever branch ou artefatos existentes. Resultados anteriores de testes não aprovam a 008.

### Dúvidas e fora do escopo

- Não há dúvida material bloqueante: as quatro decisões foram aprovadas expressamente. Assunções estão identificadas; escolhas de solução não são perguntas de negócio pendentes.
- Excluídos: gateway, cobrança, consulta de recebimento ou movimentação financeira externa; meios de pagamento; comprovantes; parcelamento; pagamento parcial; estorno; cancelamento; envio e entrega; relatórios; consulta administrativa geral de pedidos; carrinho e interface.
- Excluídos também: alteração de comprador/itens/preços; novos encargos, descontos ou moeda; manutenção de contas/perfis; correção de dados inconsistentes; recuperação idempotente da confirmação e reversão por falha de transmissão posterior à conclusão.
- Nesta etapa são gerados somente especificação e checklist de requisitos, além da seleção local exigida pelo fluxo. Não há alteração de contratos anteriores, plano, tarefas, implementação, testes de aplicação, stage, commit, push ou implantação.
