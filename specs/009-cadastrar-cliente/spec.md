# Feature Specification: Cadastrar cliente

**Feature Branch**: `009-cadastrar-cliente`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Cadastro público cria nova conta somente CLIENT; name continua login único exato e sensível à caixa, separado de fullName; aplicar as oito políticas cadastrais e de senha aprovadas, preservar contas anteriores e Features 001–008, confirmar sem token/login automático, rejeitar duplicidade e repetição sem sobrescrita e garantir concorrência, proteção dos segredos e ausência de gravação parcial. Gerar somente especificação e checklist, sem planejamento, implementação ou testes."

## User Scenarios & Testing *(mandatory)*

Estes são critérios para validação futura, não resultados executados. Todas as cinco histórias P1 são necessárias ao recorte; a prioridade não autoriza entregar cadastro sem suas garantias. Unidades, espaços e referência de data são definidos em Assumptions para tornar os limites verificáveis.

### User Story 1 - Criar uma conta de cliente e depois efetuar login (Priority: P1)

Como pessoa ainda não identificada, quero cadastrar meus dados e escolher login e senha para utilizar as jornadas de cliente sem depender de uma conta previamente preparada.

**Why this priority**: Entrega a entrada pública de clientes sem conceder privilégios administrativos ou substituir o login.

**Independent Test**: Cadastrar dados válidos, conferir conta e perfil efetivos, ausência de autenticação automática e login posterior com as credenciais exatas.

**Acceptance Scenarios**:

1. **Given** name e email disponíveis e os seis dados obrigatórios válidos, **When** uma pessoa sem autenticação solicita cadastro, **Then** é criada uma nova conta identificável com os dados aceitos e somente CLIENT; a confirmação identifica o cadastro e seu name, sem senha, hash ou token.
2. **Given** cadastro concluído, **When** a pessoa tenta usar uma jornada protegida sem login, **Then** continua não autenticada; após login separado com name e senha exatos, recebe a autenticação existente e pode registrar e consultar somente seus próprios pedidos. Essas ações posteriores são distintas dos efeitos do cadastro.
3. **Given** conta com name `Ana` e fullName `Ana da Silva`, **When** usa `Ana` no login, **Then** mantém autenticação por name; fullName e email não são identificadores alternativos. Se o texto de um email coincidir com o name de conta legada, somente a busca existente por name dessa conta continua aplicável.
4. **Given** names `Ana` e `ana`, emails diferentes e demais dados válidos, **When** ambas as pessoas se cadastram, **Then** podem existir duas contas; a caixa dos logins é preservada e o login não passa a ignorá-la.
5. **Given** pessoas com o mesmo fullName, telefone e nascimento, mas names e emails distintos, **When** solicitam cadastros válidos, **Then** são criadas contas distintas; homônimos e compartilhamento de telefone não causam duplicidade de identidade.
6. **Given** qualquer solicitante, inclusive alguém já cadastrado, **When** tenta escolher ADMIN/outro perfil, fornecer identidade de conta existente, hash ou substituir dados de conta pelo cadastro público, **Then** a tentativa é rejeitada sem nova conta, alteração da existente ou mudança de perfis.

---

### User Story 2 - Informar dados cadastrais válidos e reconhecer rejeições (Priority: P1)

Como pessoa que se cadastra, quero conhecer os limites dos dados e preservar o significado do que informei, sem correções silenciosas fora das normalizações aprovadas.

**Why this priority**: Login, nome pessoal e contato precisam ser distintos e coerentes antes da criação.

**Independent Test**: Exercitar cada campo nos limites, com os demais válidos, e conferir conservação ou rejeição integral.

**Acceptance Scenarios**:

1. **Given** um dos campos name, fullName, email, telefone, nascimento ou senha ausente ou nulo, **When** o cadastro é avaliado, **Then** há rejeição segura de entrada inválida, sem gravação parcial.
2. **Given** name começando por letra/dígito ASCII e contendo somente letras/dígitos ASCII, ponto, sublinhado ou hífen, **When** possui exatamente 3 ou 40 caracteres, **Then** é aceito sob as demais regras; 2 ou 41 são rejeitados.
3. **Given** name com espaço em qualquer posição, acento, outro caractere não permitido ou começando por ponto, sublinhado ou hífen, **When** é informado, **Then** é rejeitado sem aparar, converter caixa ou substituir caracteres.
4. **Given** fullName com espaços externos, acentos, caixa e espaços internos conhecidos, **When** é aceito, **Then** somente os espaços externos são removidos, conservando a sequência restante. Comprimentos normalizados de 2 e 120 caracteres são aceitos; 1, 121 e conteúdo vazio após remoção são rejeitados.
5. **Given** email sintaticamente válido, com espaços externos e 254 caracteres após removê-los, **When** é informado, **Then** é aceito se disponível pela comparação sem caixa; 255 caracteres, vazio, falta de parte local/domínio ou espaços internos inválidos são rejeitados sem correção interna.
6. **Given** telefone com `+` seguido exatamente de 8 ou 15 dígitos ASCII, **When** as demais condições são válidas, **Then** é aceito; 7 ou 16 dígitos, ausência de `+`, dígitos não ASCII, espaços, parênteses, hífen ou outro sinal são rejeitados sem reformatação.
7. **Given** nascimento como data real em AAAA-MM-DD, **When** é igual ou anterior à data de referência, **Then** é aceito sem idade mínima; data posterior, impossível ou em outro formato é rejeitada. Datas bissextas válidas e inválidas têm os resultados correspondentes.

---

### User Story 3 - Preservar a senha exata e impedir exposição de segredos (Priority: P1)

Como pessoa que escolhe a senha, quero utilizá-la integralmente, sem transformação silenciosa ou divulgação pelo cadastro.

**Why this priority**: O segredo escolhido precisa continuar válido no login e protegido.

**Independent Test**: Comparar caracteres e bytes de senhas conhecidas, conferir rejeições, ausência de senha/hash em saídas e diagnósticos e login futuro com a sequência exata.

**Acceptance Scenarios**:

1. **Given** senhas de 12 e 64 letras ASCII `a`, **When** os demais dados são válidos, **Then** ambas são aceitas; 11 letras são rejeitadas pelo mínimo de caracteres e 65 pelo máximo de bytes.
2. **Given** senha de 32 caracteres `á` U+00E1, **When** é avaliada, **Then** 32 caracteres e 64 bytes UTF-8 satisfazem os limites; 33 desses caracteres, com 66 bytes, são rejeitados sem normalização ou truncamento.
3. **Given** senha de 12 caracteres `😀` U+1F600, **When** é avaliada, **Then** 12 caracteres e 48 bytes satisfazem os limites; 11 ocorrências são rejeitadas pelo mínimo e 17 pelo total de 68 bytes.
4. **Given** senha válida nos dois limites, não formada somente por espaços e contendo espaços externos/internos, **When** é cadastrada e depois usada no login, **Then** os espaços são parte do segredo, sem serem aparados. Senha somente de espaços conforme Assumptions é rejeitada mesmo dentro dos limites.
5. **Given** senhas válidas diferentes por caixa ou sequência Unicode, como acentos compostos/decompostos, **When** uma delas é escolhida, **Then** somente sua sequência exata é a senha da conta; não há composição obrigatória ou transformação entre representações.
6. **Given** cadastro bem-sucedido, inválido, duplicado ou com falha interna, **When** resultados e diagnósticos são examinados, **Then** senha e hash não são expostos, o segredo não é conservado como texto legível e nenhum sucesso informa token ou concede sessão autenticada.

---

### User Story 4 - Evitar contas duplicadas e resultados parciais (Priority: P1)

Como responsável pelas contas, quero que repetição, concorrência e falhas preservem identidades e contas anteriores, sem cadastro incompleto.

**Why this priority**: Uma verificação de disponibilidade não pode permitir duplicidade concorrente ou sobrescrita.

**Independent Test**: Tentar duplicidades isoladas/combinadas, coordenar concorrência e provocar falhas; conferir contas concluídas separadamente da entrega da resposta.

**Acceptance Scenarios**:

1. **Given** name já utilizado, email já utilizado ou ambos, **When** outra tentativa válida nos demais dados usa essas identidades, inclusive variantes de caixa/espaços externos do email, **Then** recebe a mesma categoria e mensagem genéricas de duplicidade, sem revelar campo/conta causadora, devolver cadastro existente ou sobrescrever dados.
2. **Given** duas tentativas válidas com o mesmo name exato e emails distintos disponíveis, **When** concorrem sem outras falhas, **Then** exatamente uma conclui e outra é rejeitada genericamente por duplicidade; existe somente uma conta com esse name e somente CLIENT nela.
3. **Given** duas tentativas válidas com names distintos e emails equivalentes pela comparação aprovada, **When** concorrem sem outras falhas, **Then** exatamente uma conclui e outra é rejeitada genericamente; não existem duas contas com o mesmo email de comparação.
4. **Given** duas tentativas válidas com o mesmo name e emails também equivalentes, **When** concorrem sem outras falhas, **Then** há uma aceitação e uma rejeição genérica, sem mesclar campos ou perfis das tentativas.
5. **Given** duas tentativas válidas com names exatos distintos, inclusive `Ana` e `ana`, e emails não equivalentes, **When** concorrem sem outras falhas, **Then** ambas concluem contas distintas, com seus próprios dados e somente CLIENT.
6. **Given** tentativa elegível, **When** falha anterior à efetivação impede concluir conta, dados cadastrais ou CLIENT, **Then** não é informado sucesso nem permanece parcela desse novo cadastro; contas e demais dados anteriores são preservados.
7. **Given** cadastro integralmente concluído cuja confirmação se perdeu na transmissão, **When** os mesmos dados são reenviados, **Then** ocorre rejeição genérica de duplicidade, sem nova conta, autenticação automática ou reutilização da confirmação; a conta e suas credenciais permanecem disponíveis ao login existente.
8. **Given** falha interna de processamento ou consulta de disponibilidade, **When** o resultado é apresentado, **Then** informa falha segura de processamento, sem presumir duplicidade, entrada inválida ou sucesso. Efetivação indeterminada não prova ausência da conta nem autoriza apagá-la ou repetir automaticamente.

---

### User Story 5 - Preservar contas anteriores e jornadas entregues (Priority: P1)

Como cliente ou administrador já cadastrado, quero manter minha conta e minhas compras sem preencher dados novos ou alterar credenciais por causa do cadastro público.

**Why this priority**: A nova entrada não pode invalidar contas anteriores ou ampliar permissões sobre dados alheios.

**Independent Test**: Usar contas anteriores com dados novos ausentes e credenciais fora dos novos limites; comparar identidades, perfis, compras e jornadas antes/depois da 009.

**Acceptance Scenarios**:

1. **Given** conta anterior sem fullName, email, telefone ou nascimento, com name ou senha fora dos novos limites, **When** usa jornadas já permitidas, **Then** mantém login e permissões sem dados fictícios, complementação obrigatória ou validação retroativa da 009.
2. **Given** contas anteriores CLIENT, ADMIN, ambos ou sem perfil autorizado, com pedidos/pagamentos existentes, **When** novos cadastros são aceitos ou rejeitados, **Then** IDs, names, hashes, perfis, vínculos e dados históricos dessas contas permanecem iguais; suas permissões não são ampliadas nem reduzidas.
3. **Given** conta legada com o name pretendido ou com email presente equivalente ao pretendido, **When** a tentativa pública é avaliada, **Then** ocorre rejeição genérica sem alterar a conta legada. Email legado ausente não recebe valor fictício nem é tratado como email de outra pessoa.
4. **Given** jornadas das Features 001–008, **When** são exercitadas após a extensão, **Then** preservam catálogo/detalhes públicos, filtro/paginação, login por name, registro próprio, manutenção administrativa, consultas próprias e quitação com autorização, histórico e integralidade.
5. **Given** qualquer resultado de cadastro, **When** seus efeitos são conferidos, **Then** não há pedido/pagamento criado, transição de compra ou alteração de carrinho, catálogo, categorias e contas alheias; no sucesso somente a nova conta, seus dados e CLIENT são acrescentados.

### Edge Cases

- `Ana` e `ana` não conflitam como logins; `Pessoa@Exemplo.com` e ` pessoa@exemplo.com ` conflitam como emails. Não aplicar a regra de email ao name.
- Homônimos não são duplicidade de login; telefone e nascimento não ganham unicidade.
- Remoção externa só em fullName/email; espaços em name/telefone são rejeitados e em senha válida são conservados.
- Caracteres Unicode compostos/decompostos e fora do plano básico têm contagens explícitas; representação interna não muda a unidade aprovada.
- Senha exige mínimo de 12 caracteres e máximo de 64 bytes UTF-8, não mínimo de 12 bytes nem máximo de 64 caracteres; nenhuma composição adicional.
- Email dentro do limite ainda precisa de sintaxe válida; não reparar parte interna nem verificar recebimento externo.
- Nascimento igual à referência é permitido; idade recente não é causa de rejeição.
- Entrada inválida e duplicidade simultâneas não impõem nova precedência; nunca divulgar conta existente ou segredos.
- Perdedor concorrente não altera/apaga vencedor; falha operacional não é duplicidade presumida.
- Confirmação representa cadastro concluído; perda de transmissão posterior não o desfaz. Efetivação indeterminada não promete reversão.
- Ausência legada não autoriza dados fictícios, reserva de email inexistente ou bloqueio de jornadas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE permitir cadastro público de nova conta sem autenticação prévia e atribuir somente CLIENT pelo próprio sistema.
- **FR-002**: O cadastro NÃO DEVE aceitar escolha de perfis, ADMIN, identidade de conta existente, hash ou alteração de conta existente; tais tentativas DEVEM ser rejeitadas sem mutação. Eventual autenticação do solicitante não autoriza substituir sua conta ou a de terceiros.
- **FR-003**: Novas contas DEVEM exigir name, fullName, email, telefone, nascimento e senha presentes e não nulos; vazio ou invalidade conforme os requisitos seguintes DEVEM impedir o cadastro integral.
- **FR-004**: Name DEVE permanecer login único por igualdade exata e sensível à caixa, sem normalização. Novos names DEVEM ter 3–40 caracteres ASCII, iniciar por letra/dígito ASCII e conter somente letras/dígitos ASCII, ponto, sublinhado ou hífen. Espaços e outros caracteres DEVEM causar rejeição sem correção.
- **FR-005**: FullName DEVE representar o nome pessoal, ter 2–120 caracteres após remover somente espaços externos e conservar caixa, acentos, espaços internos e sequência Unicode restante. Homônimos DEVEM ser permitidos; fullName NÃO DEVE substituir name no login.
- **FR-006**: Email DEVE ser obrigatório, não vazio, sintaticamente válido e ter no máximo 254 caracteres após remoção de espaços externos. Espaços internos inválidos NÃO DEVEM ser corrigidos; validade sintática NÃO DEVE depender de confirmação de contato ou consulta externa.
- **FR-007**: Email DEVE ser único entre contas com email presente, ignorando caixa em toda a sequência e espaços externos. Essa equivalência NÃO DEVE atingir name. Email NÃO DEVE passar a autenticar o usuário ou substituir o login por name.
- **FR-008**: Telefone DEVE conter exatamente `+` seguido de 8–15 dígitos ASCII. Espaços, pontuação adicional, outro sinal e dígitos não ASCII DEVEM ser rejeitados sem reformatação; não se exige prova de existência ou titularidade da linha.
- **FR-009**: Nascimento DEVE ser data válida em AAAA-MM-DD, não posterior à referência definida em Assumptions, sem idade mínima ou correção de data impossível.
- **FR-010**: Senha DEVE ter no mínimo 12 caracteres, contados por pontos de código Unicode, e no máximo 64 bytes UTF-8 da sequência exata, sem composição obrigatória. As condições DEVEM ser verificadas independentemente; unidades e exemplos de Assumptions são normativos.
- **FR-011**: Senha somente de espaços conforme Assumptions DEVE ser rejeitada. Em senha de resto válida, espaços, caixa e sequência Unicode DEVEM ser conservados; NÃO aparar, normalizar, alterar caixa, substituir caracteres ou truncar. A senha de login DEVE ser a sequência exata escolhida.
- **FR-012**: Senha NÃO DEVE ser armazenada como texto legível nem exposta em confirmação, rejeição ou diagnóstico; hash e demais segredos também NÃO DEVEM ser divulgados. A compatibilidade integral da proteção existente com a política DEVE ser conferida no planejamento; esta especificação não a declara comprovada nem autoriza transformar senha ou relaxar limites para contornar incompatibilidade.
- **FR-013**: Sucesso DEVE confirmar a nova conta e seu name somente depois da conclusão integral; NÃO emitir token, criar sessão autenticada ou substituir o login separado. A confirmação não cria uma nova operação de consulta de dados pessoais.
- **FR-014**: Name exato ou email equivalente já existente DEVEM produzir a mesma categoria e mensagem genéricas de duplicidade, inclusive contra conta anterior com esses dados presentes; NÃO indicar campo/conta causadora, devolver cadastro anterior ou sobrescrever dados.
- **FR-015**: Duas tentativas válidas concorrentes sem outras falhas disputando o mesmo name exato ou email equivalente DEVEM produzir exatamente um cadastro concluído e uma rejeição genérica. Nunca criar duas contas para a identidade disputada, mesclar dados ou aproveitar perfis da tentativa perdedora.
- **FR-016**: Duas tentativas concorrentes válidas sem outras falhas com names exatos distintos e emails não equivalentes DEVEM concluir duas contas distintas, mesmo que fullName, telefone ou nascimento coincidam.
- **FR-017**: Conta, dados cadastrais e CLIENT DEVEM formar uma única operação integral. Rejeição ou falha anterior à efetivação que impeça concluir NÃO DEVE deixar parcelas do cadastro nem informar sucesso; dados anteriores DEVEM permanecer intactos.
- **FR-018**: Falhas internas DEVEM produzir falha segura de processamento, distinta de entrada inválida/duplicidade, sem exposição interna ou de credenciais; NÃO presumir sucesso ou conflito. Efetivação indeterminada NÃO prova ausência da conta nem autoriza compensação destrutiva ou repetição automática.
- **FR-019**: Cadastro concluído DEVE permanecer salvo se a confirmação não for recebida; novo envio dos mesmos dados DEVE ser rejeitado genericamente por duplicidade, sem outra conta, sucesso reutilizado ou autenticação automática.
- **FR-020**: Contas anteriores DEVEM conservar IDs, name, hashes, perfis, pedidos/pagamentos, vínculos e histórico. FullName, email, telefone e nascimento podem continuar ausentes nelas, sem preenchimento fictício, migração automática, complementação obrigatória ou bloqueio de jornadas.
- **FR-021**: Novas regras cadastrais NÃO DEVEM atingir retroativamente contas anteriores ou alterar login por name exato/senha. Novas credenciais DEVEM funcionar no login existente, sem email/fullName como alternativas.
- **FR-022**: O sistema DEVE preservar comportamentos e contratos das Features 001–008: consultas públicas, manutenção administrativa, autenticação, perfis atuais, registro/consultas próprias de pedidos, pagamento, titularidade e conservação financeira; NÃO ampliar privilégios ou leitura alheia.
- **FR-023**: Cadastro NÃO DEVE criar pedido/pagamento, executar transição de compra ou modificar carrinho, catálogo, categorias ou contas alheias. Somente nova conta, seus dados e CLIENT podem ser acrescentados no sucesso.

### Key Entities *(include if feature involves data)*

- **Nova conta de cliente**: Identificação própria, login name, nome pessoal fullName, email, telefone, nascimento, senha protegida e somente CLIENT.
- **Identidade de login**: Name único por igualdade exata e sensível à caixa, distinto do nome pessoal e do contato.
- **Email de contato**: Obrigatório em nova conta; unicidade ignora caixa/espaços externos, sem torná-lo credencial.
- **Senha escolhida**: Sequência secreta exata, com mínimo de caracteres e máximo de bytes distintos; nunca dado de saída/diagnóstico.
- **Conta anterior**: Existente antes da entrega da 009; preservada mesmo sem dados novos ou com credenciais fora dos novos limites.
- **Tentativa de cadastro**: Solicitação concluída ou rejeitada; repetir não recupera idempotentemente a confirmação anterior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Em 100% dos casos válidos de US1, a pessoa conclui um cadastro em um envio, reconhece conta/name e obtém somente CLIENT; não recebe token ou autenticação automática.
- **SC-002**: Em 100% dos casos de US2, limites inclusivos e regras de cada campo têm o resultado definido; só as normalizações permitidas ocorrem e rejeições não criam conta parcial.
- **SC-003**: Em 100% dos casos de US3, mínimo de caracteres e máximo de bytes são respeitados separadamente, a senha não sofre transformação e senha/hash não aparecem nas saídas/diagnósticos examinados.
- **SC-004**: Em 100% das duplicidades/repetições de US4, contas anteriores ou vencedoras permanecem intactas e a rejeição é genérica. Cada par concorrente válido disputando name/email sem outras falhas produz uma aceitação; pares de identidades distintas produzem duas.
- **SC-005**: Em 100% das rejeições e falhas anteriores à efetivação que impeçam conclusão, não há cadastro parcial ou sucesso. No caso de confirmação perdida após conclusão, a conta permanece utilizável e o reenvio é rejeitado por duplicidade.
- **SC-006**: Em 100% dos casos de US5, contas anteriores preservam identidades, credenciais, perfis e compras, sem dados fictícios ou novas exigências de login; contratos e comportamentos 001–008 continuam atendidos.
- **SC-007**: Em 100% dos casos de efeitos do cadastro, somente nova conta, seus dados e CLIENT são acrescentados no sucesso; rejeições não alteram o domínio e nenhum resultado cria pedido/pagamento, altera carrinho ou concede ADMIN.

## Assumptions

### Fontes: exigências explícitas do PDF

- Fonte reutilizada da investigação anterior: `C:/Users/ronal/Downloads/Documento de requisitos DSCommerce.pdf`, oito páginas, SHA-256 `ed1b2063012f25f44f944830351bcaa966e0562c03715948f5fd0932d9434a83`. Não houve nova investigação do PDF, cópia para o repositório ou consulta do protótipo nesta especificação.
- Página 3, seção 2, **Visão geral do sistema**: cadastro com nome, email, telefone, nascimento e senha; cadastro público e Cliente por padrão.
- Página 4, seção 5, **Casos de uso (visão geral)**: Sign up público, definido como cadastrar-se.
- Página 5, **Atores**: anônimo pode realizar sign up; Cliente mantém dados próprios e consulta próprios pedidos; Admin também pode fazer o que Cliente faz. Isso não autoriza atribuir ADMIN no cadastro público.
- Página 7, seção 6, **Login**: credenciais nome/senha e token depois de login válido; não exige email como login ou autenticação automática no sign up.
- O PDF não detalha sign up, separação name/fullName, limites, unicidade/normalização de email, telefone, idade, contagem Unicode, senha, concorrência ou repetição. São políticas do projeto, não exigências atribuídas ao PDF ou UML. Limites de nome de produto não são transferidos ao usuário.
- O `Livro Java Spring Professional.pdf`, na mesma pasta e consultado antes, é somente apoio técnico: seção 5.10, páginas impressas 168–169 (174–175 do PDF), aborda proteção por hash. Seus exemplos não definem novas regras, permissões ou contratos da 009.

### Oito políticas expressamente aprovadas

1. Cadastro público cria nova conta somente CLIENT; não escolhe ADMIN/outros perfis nem modifica conta existente (FR-001–002).
2. Name continua login único exato e sensível à caixa; novos cadastros seguem 3–40 caracteres ASCII e alfabeto aprovado, sem aparar espaços (FR-004).
3. FullName separado, 2–120 caracteres após remoção externa; preservar caixa, acentos e espaços internos e permitir homônimos (FR-005).
4. Email obrigatório, sintaticamente válido, até 254 caracteres após remoção externa e único ignorando caixa/espaços externos; sem correção interna ou login por email (FR-006–007).
5. Telefone obrigatório `+` e 8–15 dígitos; nascimento obrigatório, válido e não futuro, sem idade mínima (FR-008–009).
6. Senha: mínimo de 12 caracteres e máximo de 64 bytes UTF-8; sem composição obrigatória, somente espaços rejeitados e nenhuma transformação; compatibilidade do encoder a conferir no planejamento (FR-010–012).
7. Contas anteriores e jornadas intactas, sem dados fictícios ou regras novas retroativas (FR-020–022).
8. Confirmação sem token/login automático; duplicidade genérica de name/email sem sobrescrita, inclusive reenvio após perda da resposta, sem sucesso reutilizado (FR-013–019).

### Convenções inequívocas adotadas como assunções

- **Caracteres**: contar pontos de código Unicode válidos, não bytes, unidades de representação interna ou caracteres visuais compostos. `á` U+00E1 conta 1; `a` U+0061 seguido de acento U+0301 conta 2; `😀` U+1F600 conta 1. Sequência Unicode inválida é rejeitada, sem substituição silenciosa. Vale para fullName, email e senha; em name ASCII as contagens coincidem. A convenção concretiza a contagem solicitada, sem inventar aprovação expressa de uma unidade não nomeada pelo usuário.
- **Bytes da senha**: contar UTF-8 da sequência exata, sem marcador de ordem de bytes e sem normalização. Cada `a` ocupa 1 byte; `á`, 2; `😀`, 4. Doze caracteres podem ocupar 12, 24 ou 48 bytes; o teto permanece 64 bytes. Tamanho do hash não participa dessas contagens.
- **Espaços**: conjunto fechado U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F e U+3000. Aplicar à remoção externa de fullName/email e à rejeição de senha somente de espaços. Não descartar caracteres fora desse conjunto; não remover nada de senha, name ou telefone. Os alfabetos destes últimos já rejeitam espaços.
- **Email**: igualdade sem caixa abrange parte local e domínio após remoção externa. Não criar equivalências de provedor, como remover pontos ou sufixos após `+`. Exemplo válido: `pessoa@example.com`; inválidos: `pessoa exemplo@example.com`, `@example.com` e `pessoa@`. Representação concreta da comparação e detalhamento da sintaxe interoperável ficam para planejamento, sem reduzir validade à presença de `@` ou prometer existência/recebimento.
- **Data**: calendário gregoriano, ano 0001–9999, referência na data civil UTC no início da avaliação, mantida nessa tentativa. Igualdade é válida; não há hora de nascimento ou idade mínima. Fuso e faixa de ano são assunções de representação/comparação, não regras do PDF.
- **Legados e unicidade**: obrigatoriedade só para novas contas. Ausência legada não é email compartilhado e não autoriza preenchimento fictício. Email presente em conta anterior participa da disponibilidade sem alterar aquela conta; fullName, telefone e nascimento não ganham unicidade.
- **Erros simultâneos**: sem nova precedência entre entrada inválida e duplicidade; causas isoladas têm resultados verificáveis e nenhuma combinação revela conta existente. Mensagens exatas, códigos e campos de resposta ficam para planejamento/contratos futuros.
- **Conclusão e entrega**: ausência de resposta não prova ausência de cadastro. Ausência de parcial aplica-se à falha que impede efetivação; não prometer reversão de resultado desconhecido ou de operação cuja entrega posterior falhou.

### Dependências e preservações

- [Constituição](../../.specify/memory/constitution.md), [AGENTS.md](../../AGENTS.md) e [guia do harness](../../docs/harness.md) governam o projeto. Tecnologia, rotas, mensagens, desenho da proteção/persistência e sua verificação pertencem ao planejamento.
- [003](../003-login-autenticacao/spec.md) e seu [plano](../003-login-autenticacao/plan.md) estabelecem login exato por name/senha e proteção existente. [UserAccount](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java) é hoje conta mínima; sua existência não entrega sign up ou cadastro completo. Não se alteram esses artefatos históricos.
- [001](../001-consulta-catalogo-produtos/spec.md), [002](../002-visualizar-detalhes-produto/spec.md) e [006](../006-filtrar-catalogo-produtos/spec.md): catálogo/detalhes/filtro públicos. [004](../004-registrar-pedido/spec.md): compra própria, valores históricos e criação WAITING_PAYMENT; carrinho/esvaziamento continuam do consumidor.
- [005](../005-manter-produtos/spec.md): manutenção administrativa e proteção das compras. Seu [Gate IV](../005-manter-produtos/plan.md) permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito**; 009 não o reclassifica ou herda como aprovação própria.
- [007](../007-consultar-meus-pedidos/spec.md) e [008](../008-registrar-pagamento/spec.md): consultas próprias e quitação administrativa com autorização atual. O [snapshot de consultas 008](../008-registrar-pagamento/contracts/own-orders-openapi.yaml) sucede o histórico 007 nas revisões com 008 entregue; 009 não amplia campos/privilégios dessas consultas.
- Base confirmada: main limpa em `de1d8ffb91f4c73d8ee2d10912d56c244164084b`, sincronizada com origin/main, com 001–008 presentes. Draft, checkbox e estado editorial histórico não são provas isoladas de entrega; a análise anterior considerou código e integração.

### Dúvidas remanescentes e fronteira da fase

- Não há decisão material de negócio bloqueante identificada após as oito políticas aprovadas. Convenções adicionais são assunções explícitas revisáveis, não novas aprovações atribuídas ao usuário.
- Compatibilidade integral do encoder com senha exata e ambos os limites é obrigação futura de planejamento, não comprovação técnica desta fase. Incompatibilidade deverá ser exposta/resolvida antes de implementar, sem truncamento ou alteração silenciosa da política.
- Esta especificação não comprova testes, migração, durabilidade ou funcionamento em PostgreSQL/produção. Evidências anteriores Windows/H2 e Ubuntu/H2 conservam seus alcances e não aprovam código inexistente da 009.
- Percentuais de sucesso referem-se aos conjuntos finitos de cenários definidos, não a SLA, capacidade comercial ou aprovação obtida.

### Fora do escopo

- Recuperação/troca de senha, confirmação de email/telefone, comunicação externa de cadastro e autenticação automática.
- Edição/atualização de perfil, consulta nova de cadastro, CRUD administrativo de usuários, gestão de perfis e provisionamento de ADMIN.
- Regularização/migração automática de contas anteriores, dados fictícios e bloqueio por dados novos ausentes.
- Carrinho no servidor, gestão/esvaziamento pelo cadastro, interface visual e implantação.
- Manutenção de categorias, relatórios, novas transições de pedidos, compras/pagamentos ou movimentação financeira.
- Repetição idempotente, recuperação/reutilização de confirmação e compensação por perda da resposta.
- Nesta fase: plan, research, data-model, contratos, tasks, implementação e testes. Somente spec.md, checklist e seleção local autorizada; sem stage, commit ou push.
