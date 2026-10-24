# Research: Cadastrar cliente

**Data**: 2026-10-24. **Base examinada**: `e45bef69b8fd78fb651f0bde4317bc5ec88c9408`. [Spec](spec.md), [plano](plan.md), [modelo](data-model.md), [contrato](contracts/customer-registration-openapi.yaml). Pesquisa estática e documental; nenhum Maven, encoder, teste da aplicação ou banco foi executado nesta fase.

## R1 — Fontes, precedência e stack real

**Decisão**: preservar as oito políticas e as convenções explícitas da spec. O PDF exige cadastro público com dados pessoais e CLIENT por padrão; limites, identidades separadas, concorrência e repetição são políticas do projeto. Não reabrir requisitos com exemplos do livro ou elementos UML.

**Fundamento**: [constituição 1.0.0](../../.specify/memory/constitution.md), [AGENTS](../../AGENTS.md), [harness](../../docs/harness.md), spec e checklist foram lidos. O `pom.xml` usa Java 21/Spring Boot 3.3.4. O BOM instalado em `.m2/repository/org/springframework/boot/spring-boot-dependencies/3.3.4/` resolve Spring Security 6.3.3, Spring Framework 6.1.13, Hibernate ORM 6.5.3.Final, Hibernate Validator 8.0.1.Final, Jackson 2.17.2 e H2 2.2.224. Não foi usado `dependency:tree` nem executado Maven. Configuração efetiva examinada: [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java), [application.properties](../../src/main/resources/application.properties), [perfil test](../../src/test/resources/application-test.properties).

**Alternativas**: introduzir outro framework, banco externo ou requisitos derivados do livro não resolve uma necessidade deste recorte. PostgreSQL e produção permanecem fora da validação.

## R2 — Uma conta, identidade exata e dados pessoais opcionais no legado

**Decisão**: evoluir [UserAccount](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java), sem entidade Cliente paralela. Preservar PK IDENTITY, name, passwordHash, roles e referências de pedidos. Novos campos nullable: fullName, email, emailKey, phone e birthDate. Obrigatoriedade e limites novos ficam no DTO de cadastro; não adicionar constraints de entidade que invalidem contas antigas. Manter construtor legado e capacidade física de name 255, sem reduzir a 40.

**Fundamento**: hoje não existem esses dados pessoais na entidade; não há email legado a preencher ou migrar automaticamente. `findByName` permanece exato. H2 atual usa VARCHAR comum, sem IGNORECASE ou collation customizada. Declarar `uk_user_account_name` explicitamente no lugar da unicidade anônima, sem manter dois índices únicos equivalentes.

**Alternativas**: `lower(name)`, login por email, migração de names/hashes, contas duplicadas em outra entidade, NOT NULL global dos novos dados e valores fictícios violam a spec. Fontes primárias H2: [tipos](https://h2database.github.io/html/datatypes.html), [UNIQUE/NULLS DISTINCT](https://h2database.github.io/html/grammar.html#constraint), [IGNORECASE](https://h2database.github.io/html/commands.html#set_ignorecase). As páginas atuais apoiam o desenho; comportamento da versão 2.2.224 deverá ser provado nos testes futuros.

## R3 — Email: grafia preservada e chave binária de comparação

**Decisão**: armazenar email com remoção apenas dos espaços externos da lista fechada da spec, preservando a grafia restante. Construir emailKey separada, por ponto de código válido: `Character.toLowerCase(Character.toUpperCase(cp))`, Java 21, depois UTF-8 exato. A comparação sem caixa é simples e independente de locale, em toda a parte local/domínio; não é case folding completo com expansão, nem normalização Unicode, remoção de pontos ou sufixos `+`. A chave é VARBINARY com UNIQUE nomeado `uk_user_account_email_key`; NULL apenas para ausência legada. Comparar e persistir a mesma chave, sem comparar bytes do email original.

**Fundamento**: representação binária evita dependência de collation e expansão de `String.toLowerCase` em certos caracteres. Chaves presentes têm no máximo 1.016 bytes (254 pontos de código vezes quatro). Dados textuais suportam até 508 unidades UTF-16 para email e 240 para fullName; limite físico não substitui contagem por pontos de código. [Character Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html) define os mapeamentos simples e Unicode 15.0. Fixar vetores, inclusive ASCII, Kelvin e sigma, e reavaliar mudanças de JDK/Unicode antes de mudar chaves existentes.

**Sintaxe escolhida**: validação Bean Validation `@Email` do Hibernate Validator 8.0.1.Final sobre o valor já aparado, acrescida de presença, Unicode válido e limite total em pontos de código. Essa é a interpretação técnica de sintaxe interoperável permitida pela spec, não promessa de aceitar todo endereço concebível nem prova de entrega. A regra do provider inclui parte local de até 64 unidades UTF-16 e domínio validado pelo provider; aceita determinadas formas Unicode e entre aspas. Não impor ASCII ao email ou remover espaços internos: o parser sintático decide se são válidos. Manter exemplos positivos/negativos, incluindo comprimento total 254 com partes sintaticamente válidas. Fontes: [EmailValidator 8.0.1.Final](https://github.com/hibernate/hibernate-validator/blob/8.0.1.Final/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/bv/EmailValidator.java), [AbstractEmailValidator](https://github.com/hibernate/hibernate-validator/blob/8.0.1.Final/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/AbstractEmailValidator.java).

**Alternativas**: unicidade só em consulta permite corrida; LOWER SQL/collation global pode mudar name; hash de email exigiria lidar com colisões desnecessárias. Havendo dados pessoais presentes em uma base externa futura, ativação exige diagnóstico e decisão sobre conflitos antes de constraints; não consolidar, apagar ou inventar dados. Não há migração operacional de produção neste plano.

## R4 — Incompatibilidades materiais de senha e login: decisão D009-01

**Estado: APROVADA pelo usuário em 2026-10-24; bloqueio decisório D009-01 resolvido.** Aprovação restrita ao planejamento: não autoriza implementação, dependência no POM, tasks, Maven/testes, stage, commit ou push. Base/branch/seleção conferidas novamente no HEAD acima; os cinco artefatos não rastreados recebidos foram preservados e revisados.

**Limites expressos da aprovação**:

- Argon2id exclusivamente para senhas das novas contas cadastradas pela Feature 009.
- Hashes BCrypt existentes preservados, sem conversão ou rehash automático.
- Verificação pelo formato do hash armazenado.
- Validação de senha do login aceita a união do domínio anteriormente aceito com as senhas válidas da 009.
- Nenhuma aplicação retroativa de mínimo de 12 caracteres/máximo de 64 bytes às contas anteriores.
- Login por name, preservando campos, token e mensagens existentes.
- Nenhum trim, normalização, truncamento ou transformação de senha.

A aprovação resolve a escolha e o alcance da evolução do login, sem comprovar integração de binários ou comportamento. Demais políticas vigentes; contratos históricos 001–008 intactos. O delta autorizado está nos artefatos da 009.

### Evidência estática do encoder real

`SecurityConfig.passwordEncoder()` retorna `new BCryptPasswordEncoder()`: Spring Security 6.3.3, versão $2a, custo 10, sem pré-processamento personalizado. A implementação converte a senha em UTF-8, acrescenta byte NUL e consome ciclicamente os bytes no key schedule. O máximo de 64 bytes impede a perda nominal após 72 bytes, mas **não** estabelece distinção integral entre todas as entradas aceitas.

Contraexemplo derivado do código, sem reprodução executada: P = `abcdefghijkl` (12 pontos de código/12 bytes); Q = `abcdefghijkl\u0000abcdefghijkl` (25/25), onde `\u0000` representa um NUL real. Ambas são válidas pela spec. Após o terminador do BCrypt, Q corresponde a duas repetições do bloco P+NUL; a sequência cíclica de chave é igual para o mesmo salt. Logo a verificação BCrypt não distingue essas duas escolhas. Isto não é alegação de teste realizado ou busca de colisão criptográfica: é equivalência estrutural de entrada. Fontes exatas: [BCryptPasswordEncoder 6.3.3](https://github.com/spring-projects/spring-security/blob/6.3.3/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.java), [BCrypt 6.3.3](https://github.com/spring-projects/spring-security/blob/6.3.3/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java), métodos `hashpw`, `streamtowords`, `key`, `ekskey` e `crypt_raw`.

Há uma segunda incompatibilidade independente: [LoginRequestDTO](../../src/main/java/com/devsuperior/dscommerce/dto/LoginRequestDTO.java) usa `@NotBlank` na senha. O [NotBlankValidator 8.0.1.Final](https://github.com/hibernate/hibernate-validator/blob/8.0.1.Final/engine/src/main/java/org/hibernate/validator/internal/constraintvalidators/bv/NotBlankValidator.java) usa `String.trim().length() > 0`; `trim` remove extremos até U+0020. Doze NULs ou doze U+001C são senhas válidas na spec (não pertencem ao conjunto fechado de espaços), mas o login atual as rejeita antes do encoder. A senha não é transformada pelo validator; a incompatibilidade está no domínio aceito. [String.trim Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#trim()).

### Alternativas avaliadas antes da aprovação

| Alternativa | Consequência para políticas e contratos |
| --- | --- |
| **Escolhida em D009-01: Argon2id para novas contas, verificação por formato, extensão aditiva da validação de senha do login** | Preserva caracteres/limites aprovados e hashes BCrypt legados. Planeja dependência e novo formato; amplia explicitamente o domínio do login. Aprovação expressa registrada acima; comprovação executável futura. |
| Manter BCrypt e validação atuais, excluir explicitamente as entradas incompatíveis da política | Exige revisão de negócio da spec em etapa autorizada; rejeitar somente NUL não resolve o domínio de `@NotBlank`. Não é a política entregue nem pode ser assumido silenciosamente. |
| Manter decisão pendente | Artefatos permanecem proposta de desenho bloqueada para implementação; não alegar compatibilidade ou gate aprovado integralmente. |

PBKDF2-HMAC-SHA256 não é solução integral para NUL: P e P+NUL com menos de 64 bytes produzem a mesma chave HMAC após padding com zeros. Scrypt também usa PBKDF2. Não usar pré-hash/adaptação silenciosa para contornar a política. Fontes: [RFC 2104 §2](https://www.rfc-editor.org/rfc/rfc2104#section-2), [Pbkdf2PasswordEncoder 6.3.3](https://github.com/spring-projects/spring-security/blob/6.3.3/crypto/src/main/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.java). A primeira pergunta apresentada durante esta pesquisa mencionou PBKDF2; foi corrigida explicitamente antes de qualquer decisão ou desenho definitivo.

### Desenho aprovado para planejamento, sem implementação autorizada

**Dependência e versão**: planejar `org.bouncycastle:bcprov-jdk18on:1.86` com versão explícita, sem acrescentá-la ao POM nesta fase. O BOM Spring Boot 3.3.4 examinado não gerencia esse artefato. A [distribuição oficial Bouncy Castle](https://www.bouncycastle.org/download/bouncy-castle-java/) e a [tag oficial r1rv86](https://github.com/bcgit/bc-java/tree/r1rv86) confirmam 1.86, linha Java 8 ou posterior aplicável ao Java 21. Fixar versão torna a integração reproduzível e evita escolher release antiga apenas por exemplo do Spring. Disponibilidade oficial e leitura do código não comprovam compatibilidade binária com Security 6.3.3 nem ausência de vulnerabilidades. Resolução do artefato, integração Java 21 e revisão de avisos da versão permanecem verificações futuras, sem atualizar Boot/Security neste recorte.

**Parâmetros explícitos**: Argon2id v=19 (0x13), memória **19.456 KiB (19 MiB)**, t=2 iterações, p=1 lane, salt aleatório novo de 16 bytes por cadastro e saída de 32 bytes. Construtor de `Argon2PasswordEncoder`: saltLength=16, hashLength=32, parallelism=1, memory=19456, iterations=2. Esse perfil adota o mínimo Argon2id [recomendado pela OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id), em vez de copiar o default v5_8 do Spring (16.384 KiB). É refinamento técnico da proposta anterior de16 MiB, sem mudar a política de entrada. Não é um dos perfis de memória da RFC9106 nem benchmark de produção. Nunca reduzir parâmetros em testes; tuning/capacidade não aferidos. Novo perfil exige revisão explícita do reconhecimento.

**Componentes**: `RegistrationPasswordHasher` encapsula Argon2 como detalhe privado, somente no cadastro009, sem novo bean PasswordEncoder. Manter o único bean BCrypt para fixtures/jornadas anteriores e o dummy BCrypt para name inexistente. `AccountPasswordVerifier` despacha pelo hash persistido, sem escolher pelo tamanho/conteúdo da senha. Não usar prefixação/regravação automática, `upgradeEncoding`, fallback de algoritmo ou rehash em login. passwordHash continua VARCHAR255 e `@JsonIgnore`; PHC planejado tem97 caracteres, sem novo campo/tabela de algoritmo.

### Reconhecimento fechado dos formatos e falha segura

| Hash armazenado | Identificação antes da derivação | Resultado planejado |
| --- | --- | --- |
| BCrypt legado | String inteira `$2$` de59 caracteres ou `$2a$`/`$2b$`/`$2y$` de60, custo decimal de dois dígitos04–31 e53 caracteres `[./A-Za-z0-9]` após custo/separador. Sem wrapper `{bcrypt}`, espaços ou sufixos; não restringir a custo10. | BCrypt existente sobre a senha recebida exata; não alterar hash/custo. Match falso é401; falha operacional é500 seguro. |
| Argon2id009 | PHC inteiro `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`, ordem/valores exatos. Base64 padrão canônica sem padding; salt decodificado de16 bytes e hash de32. Validar estrutura, comprimento total97 e campos antes de decodificar/alocar/derivar; sem dados extras. | Somente Argon2id no perfil009; match falso é401; falha operacional é500 seguro. |
| NULL, vazio, desconhecido, malformado ou perfil não suportado | Inclui `$2x$`, custo BCrypt fora da faixa, prefixo inventado, `{id}`, Argon2i/d, versão ausente/diferente, parâmetros repetidos/reordenados/enormes, base64 inválida/não canônica, tamanho errado e sufixo. Não confiar só em `startsWith`. | Não correspondência:401 `Invalid credentials`, sem token, escrita, fallback ou chamada ao encoder. Não reinterpretar como texto puro ou BCrypt. |

401 para formato não reconhecido preserva a rejeição genérica que `BCryptPasswordEncoder.matches` atual faz para hash ausente/vazio/não reconhecido. Não cria mensagem externa de corrupção. Exceção operacional após identificação válida continua500 `Authentication could not be completed`; name inexistente continua dummy BCrypt e401. Custo BCrypt inválido também falha fechado antes da derivação, sem prometer a mesma classificação interna do parser antigo para material corrompido. Não prometer tempo igual entre algoritmos. Diagnóstico deve omitir senha/hash e causa de parser que os contenha.

A [implementação Argon2 do Spring6.3.3](https://github.com/spring-projects/spring-security/blob/6.3.3/crypto/src/main/java/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.java) deriva segundo parâmetros armazenados e registra exceção de parse; o [Argon2EncodingUtils](https://github.com/spring-projects/spring-security/blob/6.3.3/crypto/src/main/java/org/springframework/security/crypto/argon2/Argon2EncodingUtils.java) aceita mais variantes que o perfil009. A barreira fechada antecede `matches`; não enviar material malformado ao logger interno. Limites do PHC são limites da representação persistida, nunca novos limites da senha legada.

### União exata dos domínios de senha do login

Definir A = domínio aceito pela constraint anterior: `password != null && password.trim().length() > 0`, conforme Hibernate Validator8.0.1.Final. B = senhas válidas009: Unicode válido, mínimo12 pontos de código, máximo64 bytes UTF-8 exatos e não somente espaços da lista fechada. A nova constraint aceita **A ∪ B**, sem intersectar A com regras009. `trim` descreve apenas o predicado histórico; nunca passar resultado aparado ao encoder. Prova futura deve conferir inclusão do domínio antigo, além de amostras009.

Null/vazio e somente espaços ASCII continuam inválidos. NBSP isolado continua aceito pela parcela A embora inválido para cadastro; doze NULs ou doze U+001C entram pela parcela B. Não adicionar rejeição global de Unicode inválido ao login se A o aceitava; a validação estrita pertence ao cadastro/B. A expansão pode mudar400 para200 (credencial correta),401 (não corresponde/name inexistente) ou500 (falha operacional), nas categorias/mensagens atuais. Name, parser, campos e JWT permanecem; email/fullName não autenticam.

**Senha exata, fundamento ainda estático**: encoder transmite char[]; [Argon2Parameters1.86](https://github.com/bcgit/bc-java/blob/r1rv86/core/src/main/java/org/bouncycastle/crypto/params/Argon2Parameters.java) usa UTF-8; [Strings1.86](https://github.com/bcgit/bc-java/blob/r1rv86/core/src/main/java/org/bouncycastle/util/Strings.java) trata NUL e pares suplementares válidos; [Argon2BytesGenerator1.86](https://github.com/bcgit/bc-java/blob/r1rv86/core/src/main/java/org/bouncycastle/crypto/generators/Argon2BytesGenerator.java) incorpora comprimento e bytes. [RFC9106 §3.2](https://www.rfc-editor.org/rfc/rfc9106.html#section-3.2) define comprimento explícito. Sem pré-hash, normalização, transformação ou exclusão de NUL. Provas reais de vetores, P/Q, Unicode, limites, login aditivo, BCrypt fixo, formatos inválidos, logs e regressões no [quickstart](quickstart.md#oráculos-independentes-e-observação); nenhuma executada. Não prometer ausência matemática de colisões.

**Reavaliação D009-01/gates**: decisão resolvida e desenho coerente com FR-010–012/020–022 após registrar o delta na spec009; sem bloqueio decisório remanescente identificado. Gates I–V e compatibilidade adequados no plano estático, sujeitos a provas futuras; nenhum PASS funcional. Gate IV005 permanece exceção histórica, sem PASS estrito. Próxima fase exige solicitação própria; esta aprovação não autoriza código/tasks/testes.

## R5 — Validação local e referência civil única

**Decisão**: DTO próprio, seis campos de wire `name/fullName/email/phone/birthDate/password`, `@Valid` na fronteira MVC e constraints Bean Validation puramente locais, sem serviços/repositórios. Parser streaming exclusivo rejeita tipos incorretos, campos repetidos/desconhecidos, raiz não objeto/null e JSON adicional. Não mudar ObjectMapper global ou leitores anteriores. Não usar `@Size`/`@NotBlank` padrão como substitutos das regras Unicode novas.

Referência: componente request-scoped de tentativa, inicializado por interceptor MVC exclusivo de POST /customers, captura uma única `Clock.instant()` antes da resolução dos argumentos. Guarda data UTC imutável, disponível ao validator de nascimento e ao comando interno; não é campo JSON. O interceptor não consulta dados nem decide cadastro. Validação usa essa referência mesmo que leitura/consultas/esperas atravessem meia-noite. Reader permanece só sintático. Fora de HTTP, validação explícita usa referência fornecida/fixa; não capturar novo relógio em cada campo.

**Fundamento**: contar pontos de código só após rejeitar surrogates isolados; UTF-8 exato sem BOM; lista fechada de espaços da spec; telefone ASCII; data ISO estrita com ano 0001–9999 e resolução STRICT. Ausência/null é inválida. FullName/email são aparados uma vez por helper puro compartilhado entre validação e comando; campos raw podem conservar original até conversão, sem setters que escondam transformação da senha. Nascimento hoje é inclusivo, sem idade mínima.

**Alternativas**: `String.trim/strip`, regex `\s`, `@Past` ou relógio repetido discordam dos limites. Validação que consulta disponibilidade viola o Gate IV. Referência de commit não é a referência da spec.

## R6 — Unicidade concorrente e transação com fronteira verificável

**Decisão**: facade de serviço não transacional prepara comando validado/hash; worker separado, chamado por proxy, usa `REQUIRES_NEW`, `READ_COMMITTED`, rollback para Exception. Cria entidade nova com id null e coleção exatamente CLIENT; fragmento de repository usa `EntityManager.persist`, não merge/save de identidade recebida. Conta/dados e coleção de perfis são gravados na mesma transação. Flush explícito antes de retornar DTO interno; retorno ao facade só depois de commit do proxy. HTTP201 construído depois desse retorno. Não serializar todas as chamadas com lock global/JVM/tabela.

Consultas de disponibilidade dentro da operação podem antecipar rejeição, mas não são prova de exclusividade. Constraints de name/emailKey decidem a disputa. Capturar e classificar exceções **fora** do worker transacional; não consultar banco depois de falha nem continuar transação marcada rollback-only.

**Classificação fechada**: aceitar duplicidade somente com SQLState 23505/código H2 correspondente **e** identificação inequívoca de um dos dois índices/constraints reconhecidos de name/emailKey, na tabela e colunas esperadas. Metadados H2 são capturados/verificados antes das tentativas, em componente de persistência; não depender de sufixo variável, fragmento de mensagem ou qualquer DataIntegrityViolationException. Decodificador específico da versão deve extrair nome estruturado e compará-lo à lista exata; ausência/ambiguidade é500. Não enviar/logar causa SQL que contenha valores. PK, unique de role, FK, null, tamanho, timeout/deadlock, erro de encoder e commit genérico são500, nunca409 inferido. Falha na inicialização/verificação da lista bloqueia reconhecimento; não torna erro genérico duplicidade.

**Alternativas**: REQUIRED que entre em transação externa impede garantir commit antes de sucesso; `saveAndFlush` sozinho não comprova commit; catches dentro da transação ou nova consulta de disponibilidade após erro produzem diagnósticos falsos. Documentação primária/versionada: [TransactionTemplate 6.1.13](https://github.com/spring-projects/spring-framework/blob/v6.1.13/spring-tx/src/main/java/org/springframework/transaction/support/TransactionTemplate.java), [propagação](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html). O desenho adota worker proxy separado, não execução manual da template em produção.

## R7 — Conclusão, rollback e confirmação perdida

**Decisão**: distinguir falha conhecida anterior ao commit, commit indeterminado e falha de entrega posterior a commit observado. Primeiro caso: nenhum cadastro parcial. Segundo:500 seguro quando ainda possível, sem prometer rollback, sucesso, compensação ou retry. Terceiro: conta preservada; reenvio válido produz409 genérico, não confirmação reutilizada. Não existe idempotency key ou novo GET de conta.

**Fundamento**: conclusão transacional não equivale a flush, retorno do corpo do worker ou entrega de bytes ao cliente. Controle de teste deve observar commit JDBC e consultar estado por conexão nova, após encerramento das tentativas.

**Alternativas**: apagar conta ao perder resposta, devolver confirmação antiga ou inferir rollback de500/timeout violam FR-017–019.

## R8 — API pública, segurança e erros isolados

**Decisão**: POST /customers relativo ao servlet context, sem query, cria apenas CLIENT; resposta201 `{id,name}`, sem Location de recurso GET inexistente. Reutilizar fallback stateless permitAll de SecurityConfig, sem nova cadeia JWT. Token ausente/inválido/válido não concede perfil nem bloqueia cadastro público. Rejeitar payload com id, roles, ADMIN, hash ou outros campos em vez de ignorar.

Advice prioritário exclusivo de CustomerRegistrationController reutiliza ErrorResponseDTO e mensagens fixas:400 `Invalid customer registration data`;409 `Customer registration not available`;500 `Customer registration could not be completed`. Erros não carregam campos/valores rejeitados ou causa interna.405/406/415 preservam semântica e headers nativos, sem prometer envelope JSON quando não negociável. Não mudar GlobalExceptionHandler, Jackson global, segurança/admin/pedidos, contratos001–008 ou retornar entidade. Login separado não é chamado pelo cadastro.

**Alternativas**: ampliar `/admin/**`, autenticar automaticamente, confiar em role do corpo/token ou usar handlers globais novos ampliaria indevidamente o alcance. A extensão aprovada do login em R4 limita-se ao verificador e à união de domínios; demais fronteiras preservadas.

## R9 — Estratégia de evidência e limites

**Decisão**: matriz de 32 cenários/23FR/7SC no plano e roteiro no quickstart. Oráculos esperados independentes de helpers de produção; transações e conexões separadas para disputas; fixtures commitadas antes da janela concorrente; banco UUID próprio por contexto; limpeza seletiva no finally depois de terminar workers. Não reutilizar banco global create-drop ou modificar fixtures antigas para encobrir interferência. Preservar Surefire/logs antes de próxima invocação Maven e usar Maven sequencial.

**Fundamento**: padrões existentes [AdminProductTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java), [PaymentRegistrationExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java), [PaymentRequestJsonConfig](../../src/main/java/com/devsuperior/dscommerce/config/PaymentRequestJsonConfig.java), e procedimentos históricos008 são referências, não aprovação009. HTTP real raiz e /api é necessário, com MockMvc complementar.

**Alternativas**: suíte apenas unitária, concorrência por sleep, mocks de commit, compartilhamento de transação ou descarte simples de DTO não demonstram essas garantias. Windows/H2 histórico e Ubuntu/H2 histórico permanecem separados. Nenhum resultado009 funcional existe nesta fase. Gate IV005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito**.
