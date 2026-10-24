# Implementation Plan: Cadastrar cliente

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
