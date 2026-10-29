# Implementation Plan: Cadastrar cliente

**Estado atual — fechamento T107 (2026-10-29)**: implementado, validado e concluído **exclusivamente no escopo local Windows/H2**, na revisão `d773ff27bd2fa4a26d172def68bbf8cdd374bb93`, com **107/107 tarefas concluídas**. O [fechamento T107/CP-FINAL](quickstart.md#t107-fechamento-local) registra a revisão final e a proposta documental, sem commit executado. A [revisão T106](#t106-revisao-local) conserva o confronto I–V/D009-01/fronteiras; suas menções a T107 aberta descrevem o checkpoint anterior, sucedido por T107. Validação remota, PostgreSQL, publicação e integração em main permanecem não realizadas.

**Leitura histórica**: os registros originais abaixo, desde Branch/Date até Complexity Tracking, conservam o planejamento aprovado de2026-10-24. Expressões como “futuro”, “não adicionada nesta fase” e PASS de desenho referem-se àquela fase; a revisão T106 ao final registra suas conclusões, e o fechamento T107 referenciado acima fixa o estado atual. Não foram alterados requisitos ou conteúdo normativo dos contratos.

**Branch**: `009-cadastrar-cliente` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: especificação-base commitada em `e45bef69b8fd78fb651f0bde4317bc5ec88c9408`:32 cenários,23 requisitos e sete critérios; [checklist](checklists/requirements.md) documental16/16. Retomada em2026-10-24: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `009-cadastrar-cliente`, mesmo HEAD e seleção009 confirmados. Git recebido com cinco artefatos não rastreados (este plano, research, modelo, quickstart e contrato), preservados e revisados; sem alterações rastreadas na entrada. Main preservada em `de1d8ffb91f4c73d8ee2d10912d56c244164084b`. Spec/checklist009 recebem apenas o delta aprovado do login.

**Estado**: planejamento harmonizado com **D009-01 APROVADA** pelo usuário, nos limites registrados em [Research R4](research.md#r4--incompatibilidades-materiais-de-senha-e-login-decisão-d009-01). Bloqueio decisório resolvido; nenhuma comprovação funcional. Autorização desta fase cobre somente documentos009, sem implementação, dependência no POM, tasks ou execução Maven/testes.

## Summary

Acrescentar POST /customers público, relativo ao contexto servlet, para nova conta somente CLIENT. Evoluir UserAccount, preservar name como login exato/sensível à caixa e manter fullName separado. Normalizar apenas extremos de fullName/email; email recebe chave binária sem caixa, UNIQUE no banco. Transação própria insere conta e CLIENT integralmente;201 `{id,name}` somente após commit, sem login automático. Duplicidade reconhecida409 genérica; falhas internas500; perda de confirmação não desfaz cadastro nem torna reenvio idempotente.

A pesquisa constatou que BCrypt6.3.3 atual e `@NotBlank` da senha no login não cobrem integralmente o domínio aprovado. A solução aprovada para planejamento é Argon2id nas novas contas, verificador por formato com BCrypt legado preservado e extensão aditiva mínima da constraint de senha no login. Não muda o identificador para email nem regrava hashes. É evolução explícita de compatibilidade, não preservação literal de todo o código003.

## Technical Context

**Language/Version**: Java21, Spring Boot3.3.4, Maven Wrapper3.9.16.

**Primary Dependencies**: Spring6.1.13, Security6.3.3, Hibernate6.5.3.Final, Validator8.0.1.Final, Jackson2.17.2 existentes. D009-01 aprovada: Bouncy Castle `bcprov-jdk18on:1.86`, versão explícita para Argon2id do Spring, justificada pelas fontes oficiais em R4; não gerenciada pelo BOM atual. Integração binária futura. Não adicionada nesta fase.

**Storage**: H2 2.2.224 em memória, create na aplicação/create-drop em testes. Mesmas tb_user_account/tb_user_account_role; novos campos nullable e índices únicos nomeados. Sem entidade paralela ou migração de produção.

**Testing**: JUnit5, Bean Validation real, Boot Test, HTTP real em porta aleatória raiz e /api, MockMvc complementar, JDBC e transações independentes. Maven futuro sequencial, preservando relatórios antes da execução seguinte. Nesta fase somente documentação/OpenAPI.

**Target Platform**: JVM; Windows/PowerShell e CI Ubuntu conforme [harness](../../docs/harness.md). Windows/H2 e Ubuntu/H2 históricos permanecem distintos; PostgreSQL/produção sem validação.

**Project Type**: backend REST em Controller/Service/Repository, DTOs e advice centralizado por operação.

**Performance Goals**: sem SLA ou volume inventado; unicidade por índices, sem lock global. Argon2id usa19MiB por derivação, t=2/p=1, salt16/hash32/v19 conforme R4; capacidade/tuning não aferidos nem autorização para reduzir garantias.

**Constraints**: senha mínimo12 pontos de código/máximo64 bytes UTF-8, sem transformação; seis dados novos obrigatórios; legado sem aplicação retroativa. Referência UTC única anterior à avaliação/IO. Cadastro sem pedido/pagamento/carrinho/ADMIN/token.

**Scale/Scope**: uma operação pública, cinco histórias P1, seis campos de entrada e dois de confirmação. Extensão de verificação/validação de senha no login aprovada em D009-01; demais APIs001–008 preservadas.

## Constitution Check

Gates confrontados antes da pesquisa e reavaliados após aprovação/desenho. PASS significa somente adequação estática de desenho/estratégia; nenhum gate funcional executado. A coluna pré-pesquisa conserva o estado histórico, sem atribuir-lhe execução.

| Gate | Pré-pesquisa | Pós-desenho | Evidência/obrigação |
| --- | --- | --- | --- |
| I — Java/Spring Boot | PASS de desenho | PASS de desenho | Stack atual; biblioteca Argon2 justificada por incompatibilidade concreta. |
| II — camadas | PASS de desenho | PASS de desenho | HTTP no controller; CLIENT/transação/diagnóstico no serviço; persistência/metadados no repository. |
| III — DTOs | PASS de desenho | PASS de desenho | Entrada própria e confirmação id/name; entidade/hash nunca em HTTP. |
| IV — JPA/Bean Validation | PASS de estratégia | PASS estrito de desenho009 | @Valid local antes do serviço; referência request-scoped; JPA persist e roles na mesma transação; UNIQUE real. |
| V — REST/erros | PASS de desenho | PASS de desenho | POST201,400/409/500 seguros;405/406/415 nativos; reader/advice isolados. |
| Requisitos/compatibilidade | PASS de estratégia | PASS de desenho, D009-01 resolvida | Argon2id só cadastro009, BCrypt preservado, despacho fechado e A ∪ B no login; comprovação futura. |
| Comprovação pertinente | PASS de estratégia | PASS de estratégia | Matriz32/23/7 e provas futuras independentes; nenhuma execução funcional. |

Não há exceção constitucional nova proposta nem decisão material aberta identificada. Aprovação do desenho não dispensa provas executáveis. O **Gate IV005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, conforme [plano005](../005-manter-produtos/plan.md); não reutilizar validator com IO de domínio nem reclassificar exceção histórica.

## Project Structure

### Documentation (this feature)

~~~text
specs/009-cadastrar-cliente/
  spec.md                            delta aprovado do login
  checklists/requirements.md         rastreabilidade do delta
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/customer-registration-openapi.yaml
~~~

Sem tasks, README de contratos ou contrato003 substituto nesta fase.

### Source Code (repository root)

Alvos futuros, não arquivos implementados:

~~~text
src/main/java/com/devsuperior/dscommerce/
  controllers/CustomerRegistrationController.java
  controllers/handlers/CustomerRegistrationExceptionHandler.java
  dto/CustomerRegistrationRequestDTO.java
  dto/CustomerRegistrationConfirmationDTO.java
  validation/ValidCustomerRegistration.java e validator local
  validation/RegistrationInputRules.java
  config/CustomerRegistrationRequestJsonConfig.java
  config/CustomerRegistrationWebConfig.java
  services/RegistrationAttemptReference.java
  services/CustomerRegistrationService.java              facade
  services/CustomerRegistrationTransaction.java          worker com proxy
  services/exceptions/                                 falhas específicas
  repositories/UserAccountRepository.java               consulta emailKey
  repositories/UserAccountInsertRepository*.java        persist/diagnóstico
  entities/UserAccount.java                             novos campos nullable
  security/RegistrationPasswordHasher.java               aprovado D009-01
  security/AccountPasswordVerifier.java                  aprovado D009-01
  validation/ValidLoginPassword.java                     aprovado D009-01
  dto/LoginRequestDTO.java                               só constraint de senha, A ∪ B
  services/AuthenticationService.java                    só verificação por formato
pom.xml                                                 dependência planejada, não adicionada
src/test/java/com/devsuperior/dscommerce/
  controllers/ services/ repositories/ dto/ config/ fixtures/ validation/
~~~

**Structure Decision**: manter pacote/projeto. Reutilizar Clock, ErrorResponseDTO e único bean BCrypt PasswordEncoder. Hasher Argon2id aprovado não se registra como outro bean PasswordEncoder; preserva asserts de quantidade/tipo em PasswordEncoderConfigurationTest, AdminProductTestSupportTest e OrderPublicRegressionIntegrationTest. Nomes acima são desenho, não tasks.

## Fluxo e fronteiras

1. POST /customers usa fallback stateless permitAll existente, sem nova cadeia JWT. /api é contexto, não path duplicado no controller. Bearer ausente/inválido/válido não concede perfil nem bloqueia a operação pública.
2. Interceptor exclusivo captura Clock.instant uma vez em preHandle, antes de corpo/@Valid. Referência request-scoped guarda data UTC imutável; sem IO de domínio ou decisão de criação. Reader específico só faz parsing streaming dos seis campos string, sem coerção, campos extras/repetidos ou trailing JSON. Sem mudar Jackson global/readers anteriores.
3. @Valid executa constraints locais com referência fixa. Ausente/null/inválido é400 antes do serviço. Helper puro compartilhado aplica somente trim externo aprovado em fullName/email, contagem por pontos de código/UTF-8, sintaxe email, telefone e calendário estrito. Não logar DTO/BindingResult/valores do parser/ConstraintViolation. Comando interno inclui referência não fornecida pelo consumidor.
4. Facade não transacional prepara hash da senha exata e comando, sem token. Derivação Argon2id exclusivamente para novas contas009, perfil fixo de R4; nenhum pré-hash ou transformação. Falha é500 sem escrita. Não conservar raw password em entidade, eventos ou toString; reduzir tempo de vida sem prometer apagar String imutável da JVM.
5. Facade chama worker em bean separado por proxy REQUIRES_NEW/READ_COMMITTED/rollbackFor Exception. Nova entidade id null, dados normalizados permitidos, hash e coleção exatamente CLIENT. persist explícito pelo repository; sem merge/upsert ou identidade/perfil recebido. Conta e coleção são parte da mesma transação física.
6. Índices únicos são autoridade. Disponibilidade antecipada não evita corrida. Flush explícito permite observar SQL antes do commit; DTO interno id/name ainda não é sucesso HTTP. Falhas propagam para rollback; não capturar e continuar transação abortada.
7. Facade classifica falhas somente depois de terminar o proxy.409 exige SQLState/código único reconhecido e identificação exata de um dos dois índices/constraints de name/emailKey, previamente verificados em metadados. PK, unique de role, FK, timeout, encoder, mapeamento e commit genérico são500. Não consultar disponibilidade após erro, fazer retry ou usar causa SQL com valores em diagnóstico. Ver [R6](research.md#r6--unicidade-concorrente-e-transação-com-fronteira-verificável).
8. Controller constrói201 após commit, corpo exato id/name; sem Location para GET inexistente, dados pessoais, roles, senha/hash, token ou Set-Cookie autenticador. Advice scoped mantém400/409/500 e405/406/415 nativos. Nenhum novo handler global que altere APIs existentes.
9. Falha anterior conhecida: nenhum parcial. Commit indeterminado: não afirmar ausência/rollback, compensar ou repetir automaticamente. Commit observado e entrega falha: preservar conta; novo envio409 sem confirmação antiga.500/timeout isolado não prova rollback.
10. Sob D009-01 aprovada, verificador seleciona somente Argon2id009 ou BCrypt legado reconhecidos conforme R4; formato desconhecido/malformado/perfil incompatível é401 `Invalid credentials`, sem encoder/fallback/token/escrita. Falha operacional de encoder reconhecido mantém500 `Authentication could not be completed`. Sem rehash, alteração de roles/IDs/relações. Constraint de senha aceita união do domínio antigo com o domínio válido009, conforme R4; não impõe12/64 ou novos dados a contas antigas. Name lookup, parser, JWT, dummy e wire fields permanecem. A faixa adicional pode mudar400 para resultado de autenticação; isso é evolução explícita. Contrato003 histórico não é editado; delta aprovado documentado no contrato009.

## Persistência, concorrência e legado

[Modelo](data-model.md) fixa capacidades/invariantes. Name VARCHAR255 exato, limite3–40 só em cadastro. Email original aparado e chave binária sem caixa na mesma entidade. Múltiplos NULLs são ausência legada; fullName/phone/birthDate não únicos. Não introduzir IGNORECASE/collation global ou reduzir colunas históricas.

Disputa válida sem outras falhas por name/email/ambos: um201/um409 e uma conta CLIENT; vencedor não recebe campos do perdedor. Identidades distintas (Ana/ana incluídos) e emails distintos: dois201. Sem locks globais/JVM. Timeout/deadlock/pool falho são500, não prova de concorrência resolvida.

H2 memória/create não é migração durável. Dados novos ficam NULL em contas anteriores; IDs/names/hashes/perfis/pedidos/pagamentos permanecem. Não editar seeds/hashes ou preencher dados fictícios. Banco externo com emails prévios/conflitos exige diagnóstico antes de ativação, sem resolução automática ou alegação de PostgreSQL validado.

## Contratos e alcance

- [Contrato009](contracts/customer-registration-openapi.yaml): somente POST /customers; entrada name/fullName/email/phone/birthDate/password, confirmação id/name, erros fixos. Qualquer campo adicional é proibido, inclusive id/roles/passwordHash.
- OpenAPI representa forma/tipos e limites estáticos possíveis. Trim por lista fechada, bytes UTF-8, sintaxe provider, Unicode válido e data corrente exigem descrições/x-rules e provas próprias. Não aplicar maxLength120/254 ao raw antes do trim.
- D009-01 aprova evolução aditiva de validação/verificação de senha no login, sem login por email ou novo token/mensagem. Contrato003 preservado como histórico; x-login-evolution do contrato009 registra A ∪ B e x-password-storage fixa perfil/formatos. Não aplicar schema/limites cadastrais à parcela legada do login.
- Contratos001–008 intactos; snapshot de consultas [008](../008-registrar-pagamento/contracts/own-orders-openapi.yaml) já sucede007 na main entregue. Não acrescentar dados pessoais/hash a GETs de catálogo/pedido.
- Fora do escopo: recuperação/troca de senha, confirmação de contato, edição cadastral, gestão de perfis/ADMIN, interface, carrinho servidor, relatórios, compra/pagamento adicional, implantação e migração de produção.

## Estratégia e rastreabilidade

Grupos futuros: V01 contrato/fronteira; V02 normalização/limites/tempo; V03 segredo/encoder/login; V04 unicidade concorrente; V05 atomicidade/commit; V06 entrega; V07 legado/regressões. Nada executado. Matriz individual dos32cenários, cobrindo23FR/7SC; procedimentos no [quickstart](quickstart.md).

| Cenário | Requisitos | Critérios | Prova/grupo |
| --- | --- | --- | --- |
| US1.1 | FR-001, FR-003, FR-013 | SC-001, SC-007 | V01:201, conta e só CLIENT, sem segredo/token. |
| US1.2 | FR-013, FR-021 | SC-001, SC-006 | V03/V07: sem autoauth, login separado e jornadas próprias. |
| US1.3 | FR-004, FR-005, FR-007, FR-021 | SC-001, SC-006 | V03: name exato, fullName/email não autenticam; coincidência de name legado. |
| US1.4 | FR-004, FR-016, FR-021 | SC-001, SC-004 | V02/V04: Ana/ana distintos. |
| US1.5 | FR-005, FR-008, FR-009, FR-016 | SC-001, SC-002 | V02: homônimos e telefone/nascimento compartilhados. |
| US1.6 | FR-001, FR-002, FR-023 | SC-001, SC-007 | V01: ID/hash/role rejeitados, inclusive solicitante autenticado. |
| US2.1 | FR-003, FR-017 | SC-002, SC-005 | V01: seis ausências/null separados, nenhum parcial. |
| US2.2 | FR-004 | SC-002 | V02:3/40 aceitos,2/41 rejeitados. |
| US2.3 | FR-004 | SC-002 | V02: espaços/acentos/início inválido, sem correção. |
| US2.4 | FR-005 | SC-002 | V02:2/120 pontos de código após trim; sequência/interior exatos. |
| US2.5 | FR-006, FR-007 | SC-002, SC-004 | V02:254/255 após trim, sintaxe e comparação sem caixa. |
| US2.6 | FR-008 | SC-002 | V02:8/15 ASCII,7/16 e pontuação inválidos. |
| US2.7 | FR-009 | SC-002 | V02: hoje/ontem/amanhã, bissexto, UTC atravessando meia-noite. |
| US3.1 | FR-010, FR-012 | SC-003 | V03: ASCII11/12/64/65 e encoder real futuro. |
| US3.2 | FR-010, FR-011, FR-012 | SC-003 | V03:32/33á, bytes64/66, sem transformação. |
| US3.3 | FR-010, FR-011, FR-012 | SC-003 | V03:11/12/17emoji, unidades independentes. |
| US3.4 | FR-011, FR-021 | SC-003, SC-006 | V03: espaços exatos/lista fechada, login posterior. |
| US3.5 | FR-010, FR-011, FR-012, FR-021 | SC-003 | V03: Unicode/caixa/NUL e domínio login; D009-01. |
| US3.6 | FR-012, FR-013 | SC-003 | V01/V03: saídas/logs/armazenamento sem senha/hash divulgado. |
| US4.1 | FR-007, FR-014 | SC-004 | V04: mesmo409 para name/email/ambos, sem overwrite. |
| US4.2 | FR-015, FR-017 | SC-004, SC-005 | V04: mesmo name, emails diferentes, um201/um409. |
| US4.3 | FR-007, FR-015, FR-017 | SC-004, SC-005 | V04: names diferentes/email equivalente, um201/um409. |
| US4.4 | FR-015, FR-017 | SC-004, SC-005 | V04: ambas identidades disputadas, sem mescla. |
| US4.5 | FR-016 | SC-004 | V04: identidades distintas, ambas201. |
| US4.6 | FR-017, FR-023 | SC-005, SC-007 | V05: falhas entre conta/roles, pós-flush e pré-commit, sem parcial. |
| US4.7 | FR-014, FR-019 | SC-004, SC-005 | V06: entrega impedida pós-commit, repetir409/login funciona. |
| US4.8 | FR-018 | SC-005 | V05: lookup/encoder/FK/outro23505/timeout/commit incerto500. |
| US5.1 | FR-020, FR-021 | SC-006 | V07: dados NULL e credenciais antigas sem bloqueio. |
| US5.2 | FR-020, FR-022 | SC-006 | V07: CLIENT/ADMIN/ambos/nenhum e compras/pagamentos intactos. |
| US5.3 | FR-014, FR-020 | SC-004, SC-006 | V04/V07: identidades presentes reservadas, ausência sem dado fictício. |
| US5.4 | FR-022 | SC-006 | V07: regressões001–008, autorização/titularidade/contratos. |
| US5.5 | FR-023 | SC-007 | V05/V07: snapshots sem pedido/pagamento/carrinho/catálogo alterado. |

## Fases e condição de saída

Phase0 registra pesquisa, alternativas e aprovação expressa de D009-01. Phase1 harmoniza plano/modelo/contrato/roteiro e o delta pontual de spec/checklist009. Bloqueio decisório resolvido; revisão estática não demonstra runtime. Não gerar Phase2/tasks nem iniciar implementação nesta solicitação. Pendentes de fase futura autorizada: resolução/integração de bcprov1.86, vetores/round-trip/HTTP reais, domínio aditivo, BCrypt legado, formatos inválidos, logs, concorrência/commit e regressões completas.

## Complexity Tracking

Sem violação constitucional proposta. Componentes de senha aprovados em D009-01 preservam único bean BCrypt e permitem domínio integral; worker separado possui a transação física; referência request-scoped fixa data antes de MVC sem IO no validator. Sem entidade paralela, framework genérico ou novo subsistema de identidades.

<a id="t106-revisao-local"></a>

## Revisão T106 — implementação, gates e fronteiras — 2026-10-29

### Base da revisão e critério de conclusão

Revisão documental/estática autorizada exclusivamente para T106, sobre a [constituição1.0.0](../../.specify/memory/constitution.md), os requisitos009 e o desenho original deste plano. Produção, testes e asserções permanecem na revisão `d773ff27bd2fa4a26d172def68bbf8cdd374bb93`; T105 acrescentou somente documentação. O [registro T106 no quickstart](quickstart.md#t106-revisao-gates) identifica procedimentos, arquivos e verificações editoriais. Esta seção atualiza o estado editorial do plano, sem promover o PASS de planejamento a comprovação executada.

**F104** designa exclusivamente `T104-FINAL-20261029T122839Z-5f6084c4`: uma FINAL pelo Wrapper, sem seletor/exclusões, **7.310aprovados e zero falhas/erros/ignorados**. [Maven exit0 antes do arquivamento](../../target/feature009-evidence/T104-FINAL-20261029T122839Z-5f6084c4/execution-before-archive.json) e [runner exit0 independente](../../target/feature009-evidence/T104-FINAL-20261029T122839Z-5f6084c4/runner-result.json) têm fontes distintas. A [reconciliação existente](../../target/feature009-evidence/T104-FINAL-20261029T122839Z-5f6084c4/reconciliation.json) registra182classes/1.132pares classe-método/186XML/186TXT, sem omissões, extras ou duplicatas; [casos e relatórios](../../target/feature009-evidence/T104-FINAL-20261029T122839Z-5f6084c4/case-inventory.json) e [distribuição de suites nested](../../target/feature009-evidence/T104-FINAL-20261029T122839Z-5f6084c4/suite-grouping.json) preservam identidades. A consolidação [T105](quickstart.md#t105--consolidação-das-evidências-e-limitações-atuais--2026-10-29) separa superfícies, históricos e limites.

Nas tabelas, **ATENDIDO no escopo009 revisado** significa conclusão da inspeção estática fundamentada nas provas locais nomeadas. Não significa que um teste executou a constituição, que a contagem global prova cada requisito ou que houve certificação universal/validação remota. As classes abreviadas têm prefixo CustomerRegistration; famílias HTTP incluem as filhas concretas raiz e `/api`, com herança/índices/relatório resolvidos no catálogo F104. A matriz individual32/23/7 de T100 mantém o vínculo de aceitação.

### Gates I–V da009 contra implementação e evidências

| Gate | Constatação no código atual | Prova executada existente e alcance | Conclusão T106 |
| --- | --- | --- | --- |
| **I — Java/Spring Boot** | [POM](../../pom.xml) conserva Java21/Boot3.3.4; [hasher](../../src/main/java/com/devsuperior/dscommerce/security/RegistrationPasswordHasher.java) usa Argon2PasswordEncoder Spring/BC1.86 com perfil aprovado, sem substituir o bean BCrypt. Wiring por componentes, MVC, Security, Bean Validation e JPA do projeto, sem stack paralela. | F104 iniciou a aplicação/contextos e executou CryptoTest, PasswordEncoderConfigurationTest e filhas HTTP. `bouncyCastleMatchesThePublishedRfc9106Argon2idVector` confere o vetor publicado no BC; `registrationWrapperUsesTheApprovedProfileAndLiteralUtf8Bytes` prova o wrapper no perfil009, separadamente da resolução/chamada binária histórica T023. | **ATENDIDO no escopo009 revisado.** Integração local efetivamente executada, sem inferir outros sistemas operacionais, capacidade ou ausência universal de vulnerabilidades. |
| **II — camadas** | [Controller](../../src/main/java/com/devsuperior/dscommerce/controllers/CustomerRegistrationController.java) trata corpo/query/status; [fachada](../../src/main/java/com/devsuperior/dscommerce/services/CustomerRegistrationService.java) orquestra validação defensiva, conversão/hash, disponibilidade e classificação; [worker](../../src/main/java/com/devsuperior/dscommerce/services/CustomerRegistrationTransaction.java) cria a conta somente CLIENT e possui a transação. [Repository](../../src/main/java/com/devsuperior/dscommerce/repositories/UserAccountInsertRepositoryImpl.java) persiste/obtém metadados, sem decidir perfil ou mapear DTO. Regras de campo são locais/puras; diagnóstico de existência é posterior, no serviço. | F104: Flow, Field, Concurrency, Rollback e Operational. `invalidFieldValuesAreRejectedWithoutPartialWritesOrServiceCalls` observa bloqueio antes do serviço; `unarmedControlCommitsAccountAndClientThroughTheOriginalJdbcConnection` observa a fronteira física. A ausência de negócio no controller/repository é conclusão de inspeção, não apenas desses testes. | **ATENDIDO no escopo009 revisado.** Metadados JDBC ficam na abstração de persistência; não são validação de domínio dentro de isValid. |
| **III — DTOs** | [Request](../../src/main/java/com/devsuperior/dscommerce/dto/CustomerRegistrationRequestDTO.java) contém os seis campos raw/write-only e toString sem valores sensíveis; [confirmação](../../src/main/java/com/devsuperior/dscommerce/dto/CustomerRegistrationConfirmationDTO.java) só id/name. [Comando interno](../../src/main/java/com/devsuperior/dscommerce/services/CustomerRegistrationCommand.java) contém hash e referência, sem plaintext/ID/roles recebidos. Serviço cria entidade; repository não recebe payload HTTP nem produz DTO. | F104: JsonTest, Flow e SecretLoggingTest. `anonymousSignupPersistsAcceptedDataWithOnlyClientAndAnExactConfirmation` verifica201 e campos exatos; `dtoCommandEntityAndConfirmationHaveNoSecretRenderingOrPlaintextEntityField` e os casos HTTP de segredos complementam a leitura estática. | **ATENDIDO no escopo009 revisado.** Nenhuma entidade/hash é contrato de saída; ausência de segredo limita-se aos objetos/canais observados. |
| **IV — JPA e Bean Validation** | @Valid no controller e @ValidCustomerRegistration no DTO; [validator](../../src/main/java/com/devsuperior/dscommerce/validation/CustomerRegistrationValidator.java) usa regras locais e referência UTC fixa, sem serviço de domínio/repository/IO. O holder request-scoped só guarda instant/data; não faz diagnóstico cadastral. [Entidade](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java) conserva identidade/relações/nullable/DATE. Persist explícito e conta+CLIENT na mesma transação REQUIRES_NEW/READ_COMMITTED/rollbackFor Exception, por proxy separado. | F104: Validation/Reference/Field, Schema, Rollback e Concurrency. `registrationColumnsHaveTheApprovedPhysicalTypeCapacityAndNullableFlag`, `jpaReadsRegistrationValuesFromTheirRealColumnsWithoutTruncation` e `knownPreCommitFailuresRollBackEveryReachedWriteWithoutChangingPreviousData` têm oráculos de metadados/JDBC/estado externo distintos. Referência é fixada antes de corpo/IO; inválidos não chegam ao serviço. | **ATENDIDO no cadastro009 revisado, sem nova exceção identificada. Gate IV005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito.** Persistência/validação009 não reclassificam a dívida histórica005 nem transferem sua exceção para o novo validator. |
| **V — REST e erros centralizados** | POST /customers retorna DTO201 após fachada/proxy; [advice scoped](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/CustomerRegistrationExceptionHandler.java) mantém400/409/500 fixos e405/406/415 nativos, sem try/catch de negócio no controller ou alteração do handler global. [Reader próprio](../../src/main/java/com/devsuperior/dscommerce/config/CustomerRegistrationRequestJsonConfig.java) só atua no DTO009, descarta causas sensíveis e rejeita campos extras/repetidos/trailing JSON. | F104: JsonTest/Flow/Duplicate/Operational e SecretLogging. `unsupportedMethodsKeepNative405AndAllowPost`, `unacceptableRepresentationsKeepNative406WithoutForcingJson` e `unsupportedRequestTypesKeepNative415AndAdvertiseJson` verificam semântica/headers. Rejeição duplicada genérica difere de falha operacional500; entrega perdida tem prova própria. | **ATENDIDO no escopo009 revisado.** Não se promete envelope JSON quando não negociável nem rollback por status500; perda controlada de entrega não é novo contrato de erro de produção. |

A [exceção IV005](../005-manter-produtos/plan.md#constitution-check) continua relacionada ao diagnóstico de domínio em serviços durante a constraint de produto/US2.6. O validator009 não reutiliza esse fluxo: presença/sintaxe/limites/calendário são locais; existência/unicidade/CLIENT e escrita ficam nos serviços após a fronteira válida. A revisão não altera a constituição nem a spec para justificar lacuna.

### D009-01 e fronteiras do plano

| Fronteira revisada | Implementação e evidência aplicável | Conclusão/limite |
| --- | --- | --- |
| **D009-01: senha e login aditivo** | [Verificador](../../src/main/java/com/devsuperior/dscommerce/security/AccountPasswordVerifier.java) seleciona pelo hash inteiro; Argon2id v19/m19456/t2/p1/salt16/hash32 apenas novas009, BCrypt reconhecido sem encode/upgrade/save. [LoginPasswordValidator](../../src/main/java/com/devsuperior/dscommerce/validation/LoginPasswordValidator.java) aceita A ∪ B e [AuthenticationService](../../src/main/java/com/devsuperior/dscommerce/services/AuthenticationService.java) mantém lookup exato, dummy e token posterior. F104: Crypto/Format/LoginValidation/Password HTTP, inclusive `pQAndPPlusNulRemainDistinctCredentialsInBothHttpDirections` e `previousBcryptCredentialsKeepTheirHashNullFieldsAndUnrestrictedLoginDomain`. | **Preservada.** Senha original sem trim/normalização/truncamento; A é `password != null && password.trim().length() > 0`, sem12/64/Unicode estrito retroativo. Formato inválido401 sem derivação/fallback; falha operacional reconhecida500. Reconhecimento04–31 não significa executar custo31; não exigir distinção P/Q retroativa no BCrypt. |
| **Identidades, público e perfis — fluxo1/8/10** | [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) mantém fallback público stateless, sem nova cadeia009. Flow/Password/Duplicate e regressões F104 observam bearer ausente/inválido/válido, name exato/Ana-ana, email/fullName sem alias, extras rejeitados, somente CLIENT,201 sem token/cookie autenticador/Location e login separado. | **Preservada nos casos observados.** Nenhum ADMIN provisionado, recuperação de conta, novo GET, atualização de perfil ou autenticação automática. Demais rotas/autorização/titularidade/valores financeiros têm suas regressões F104, não aprovação por mera ausência de diff. |
| **Campos/tempo — fluxo2/3** | Reader DTO-local; [interceptor](../../src/main/java/com/devsuperior/dscommerce/config/CustomerRegistrationWebConfig.java) captura Clock.instant antes do corpo/@Valid e [referência](../../src/main/java/com/devsuperior/dscommerce/services/RegistrationAttemptReference.java) guarda data UTC única. Só fullName/email recebem trim fechado; chave lower(upper(cp))/UTF-8, telefone ASCII e data STRICT. F104: Json/Validation/Field/Reference/DateDiagnosis, com `theInitialUtcReferenceSurvivesBodyValidationAndIoWaits` nas filhas HTTP. | **Preservada com os limites T100/T105.** DATE/LocalDate mantido. Extremo HTTP9999/controles específicos DATE pertencem ao focal DATE-fix T056/T057 quando assim mapeados; não são transferidos por contagem a toda família F104. Capacidades físicas não são saturação provada. |
| **Persistência/concorrência — fluxo4–7** | Fachada deriva antes da escrita e rejeita transação envolvente; worker por proxy persiste conta/CLIENT e flush. Consultas antecipadas positivas diagnosticam identidade existente; na falha de escrita, classificador fora do proxy exige23505/código H2 e índice exato de name/emailKey em metadados prévios. F104: Duplicate/Classification/Concurrency/Rollback; `competingIdentitiesHaveOneWinnerAndAnUnmodifiedGenericLoser` e `distinctIdentitiesCommitWhileTheFirstAccountWriteIsStillHeld`. | **Preservada no H2 observado.** Nenhum merge/upsert/retry/lock global ou mescla do perdedor. Reads livres não garantem exclusividade; índices são autoridade. Identidades distintas concluem no controle; não é prova de todos os interleavings/carga. Classificador é específico H2 2.2.224, não PostgreSQL. |
| **Commit, rollback e entrega — fluxo6/8/9** | F104: Rollback/Operational/Delivery/Transport. `committedPhysicalOutcomeWithoutAcknowledgementRemainsSafe500` e `rolledBackPhysicalOutcomeWithoutAcknowledgementRemainsSafe500` distinguem resultados físicos por controle externo; `socketProxyClosesDownstreamAfterObservedCommitWithoutDeliveringConfirmation` distingue upstream201 de zero bytes/EOF downstream, conta/CLIENT utilizáveis e reenvio409, além do filtro bufferizado separado. | **Preservada sob controle.** Flush/DTO não são commit; callbacks UNKNOWN/ROLLED_BACK não estabelecem sozinhos resultado físico.500/timeout não provam ausência; indeterminado não aciona recuperação/compensação. Socket loopback/filtro não reproduzem incidente físico de rede/TLS/banco nem entregam idempotência. |
| **Segredos, legado e efeitos** | [Filtro diagnóstico](../../src/main/java/com/devsuperior/dscommerce/security/CustomerRegistrationDiagnosticLoggingFilter.java) adapta SqlExceptionHelper durante POST /customers síncrono/Logback, restaura escopo e preserva severidade. F104: SecretDiagnosticFilter/SecretLogging, LegacyAccount/RegressionPublic/Protected/Effects e regressões001–008. Snapshots observam somente nova conta/dados/CLIENT no sucesso e domínio prévio igual nos casos exigidos. | **Preservada nos canais/dados/casos observados.** Sem garantia universal sobre logs/dumps/outros níveis/threads/backends. Snapshots READ_COMMITTED exigem escritores encerrados; não são imagem atômica sob escrita livre. Carrinho permanece no consumidor; nenhuma migração/contato fictício/rehash legados. |

### Estado atual, históricos e pendência de fechamento

**Implementado e validado localmente em Windows/H2; revisão T106 satisfeita no alcance acima.** Não foi identificado desvio constitucional novo ou requisito/teste impeditivo da revisão local da009 nos artefatos/casos examinados. Essa conclusão deriva de código e provas nomeadas, não de um plano aprovado, checkbox ou total global. Não é declaração de aceite completo: **T107 permanece aberta**, com revisão final/proposta CP-FINAL ainda não executadas.

F104 é prova conjunta local própria; T100/T105 preservam as origens distintas de T023, T069, T096, T098, parcelas US4 e T101. Reprovações anteriores mantêm resultado/revisão/asserções interrompidas; runner indisponível de T101 não vira0 pelo runner0 de F104. Nenhuma execução foi somada ou arquivada/reconciliada novamente em T106.

Sem validação remota/CI, PostgreSQL, macOS/Linux desta revisão, integração em main, publicação ou deploy. Históricos Windows/H2/Ubuntu do harness mantêm seu alcance. Artefatos brutos em target ignorado são locais, não evidência transportada a outro checkout. Preservados a matriz32/23/7, contratos normativos009 e contratos históricos001–008, constituição/spec, DATE, D009-01, proteção delimitada dos logs e **Gate IV005: EXCEÇÃO DOCUMENTADA, sem PASS estrito**.
