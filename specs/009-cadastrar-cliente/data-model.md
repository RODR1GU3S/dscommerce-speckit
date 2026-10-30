# Data model: Cadastrar cliente

**Estado**: desenho futuro, não schema aplicado ou migração executada. [Spec](spec.md), [plano](plan.md), [research](research.md) e [contrato](contracts/customer-registration-openapi.yaml). D009-01 aprovada pelo usuário para o planejamento; limites e formatos em [R4](research.md#reconhecimento-fechado-dos-formatos-e-falha-segura). Provas executáveis futuras.

## UserAccount — mesma entidade/agregado

Preservar [UserAccount](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java) e tabela tb_user_account. Não criar entidade Cliente ou nova identificação paralela. Não alterar equals/hashCode, PK, IDs atuais ou referências de pedidos.

| Campo Java / coluna | Tipo/capacidade física planejada | Cadastro novo | Conta anterior |
| --- | --- | --- | --- |
| id / id | Long, BIGINT IDENTITY, PK | Gerado pelo banco; não recebido no corpo. | Preservado. |
| name / name | String, VARCHAR255 NOT NULL |3–40 ASCII, início alfanumérico, resto `[A-Za-z0-9._-]`, comparação exata/sensível à caixa, sem trim. | Mesma capacidade/valor/lookup; sem nova validação retroativa. |
| passwordHash / password_hash | String, VARCHAR255 NOT NULL | Somente hash gerado pelo sistema; Argon2id009 v19, m19456KiB/t2/p1, salt16/hash32, PHC97 caracteres sob D009-01 aprovada. | Hash BCrypt existente byte a byte, sem prefixação/regravação/rehash. |
| fullName / full_name | String, VARCHAR240 NULL |2–120 pontos de código após trim externo da lista fechada; preservar restante. | NULL permitido; nenhum nome fictício derivado de name. |
| email / email | String, VARCHAR508 NULL | Sintaxe válida e até254 pontos de código após trim; grafia aparada preservada. | NULL permitido; campo não existe no modelo atual. |
| emailKey / email_key | byte[], VARBINARY1016 NULL | UTF-8 da sequência de mapeamento simples sem caixa; UNIQUE. | NULL para ausência; não inventar chave/contato. |
| phone / phone | String, VARCHAR16 NULL | `+` seguido de8–15 dígitos ASCII, sem conversão. | NULL permitido; não condicionar login/compras a preenchimento. |
| birthDate / birth_date | LocalDate, DATE NULL | Gregorian0001–9999, ISO AAAA-MM-DD estrito, menor ou igual à referência UTC da tentativa. | NULL permitido; sem idade mínima ou validação retroativa. |
| roles / tb_user_account_role | Set<UserRole>, coleção atual LAZY | Coleção nova exatamente CLIENT. | Conservar CLIENT, ADMIN, ambos ou ausência, sem atribuição de perfil corretivo. |

Capacidades240/508 permitem pares UTF-16 suplementares; são armazenamento, não novos limites de negócio. `String.length`/`@Size` não comprovam contagem por pontos de código. As constraints cadastrais não devem ser adicionadas à entidade de modo a rejeitar updates/flush de contas antigas.

## Restrições e relacionamentos

- `uk_user_account_name`: UNIQUE(name), VARCHAR comum com comparação exata na configuração H2 atual. Substituir declaração anônima existente por constraint nomeada única, sem criar outra duplicada. Não usar VARCHAR_IGNORECASE, LOWER(name) ou collation global. Manter `findByName` existente.
- `uk_user_account_email_key`: UNIQUE(email_key), NULLS DISTINCT em H2 regular. Várias contas antigas sem email podem manter NULL; chaves presentes não podem duplicar. Campo original e chave são escritos juntos no serviço, na mesma conta/transação.
- Preservar tb_user_account_role(user_account_id,role), FK `fk_user_account_role_account`, UNIQUE `uk_user_account_role`, enum STRING e NOT NULL atuais. Não criar tabela de perfil nova, role recebido ou ADMIN por default.
- Pedidos continuam referenciando o mesmo ID da conta; pagamentos continuam ligados ao pedido. Não inserir/modificar/remover pedidos, itens, pagamentos, produtos ou categorias no cadastro. Carrinho continua responsabilidade do consumidor.
- Unicidade não se aplica a fullName/phone/birthDate. Não criar FK/NOT NULL nos campos novos que bloqueiem o legado.
- Presença obrigatória e sintaxe em novos cadastros são invariantes do caminho de criação/DTO; não atribuir ao schema garantias de Unicode, janela de data ou equivalência entre email e chave que ele não implementa sozinho. Constraints únicas e FKs defendem as corridas correspondentes, não todas as regras de negócio.

## Canonicalização e validação

1. Rejeitar Unicode inválido (surrogate isolado), sem converter para caractere de substituição. Name/phone usam gramática ASCII. FullName/email/senha contam pontos de código válidos.
2. Espaços: U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F, U+3000. Aparar só extremos de fullName/email. Senha só desse conjunto é inválida; outros espaços em senha válida permanecem. Não aplicar trim/strip global ou regex `\s` como substituto da lista.
3. EmailKey: para cada ponto de código do email aparado, lower(upper(cp)) por Character Java21; codificar UTF-8 sem BOM. Comparação binária das chaves; sem locale, composição Unicode, expansão de case folding, remoção de pontos ou `+`. Ex.: `Pessoa@Exemplo.com` e ` pessoa@exemplo.com ` disputam a mesma chave; `pessoa+tag@example.com` e `pessoa@example.com` são distintas. Sigma final/normal seguem o mesmo mapeamento simples; ß não se expande para ss. A grafia original não é substituída pela chave.
4. Sintaxe email: Bean Validation @Email do provider8.0.1.Final sobre valor aparado, com presença/limite total próprios; [R3](research.md#r3--email-grafia-preservada-e-chave-binária-de-comparação) fixa alcance e fontes. Nenhuma consulta DNS/SMTP ou correção interna. Datas usam regex de quatro/dois/dois dígitos e parsing STRICT; hoje inclusivo e sem idade mínima.
5. Senha: mínimo12 pontos de código, máximo64 bytes da sequência UTF-8 exata, sem BOM/transformação/truncamento; rejeitar apenas espaços conforme regra fechada, além de null/vazio/limites/Unicode inválido. Não persistir raw password. D009-01 resolve o desenho: somente novas contas009 recebem Argon2id; BCrypt legado é preservado sem rehash. Login aceita A ∪ B conforme R4; não aplicar limites009 retroativamente.

## Formatos de passwordHash e login

Nenhuma coluna de algoritmo ou conversão de hash legado. PHC009 inteiro `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>` com base64 canônica sem padding,16/32bytes decodificados e97 caracteres; perfil/parâmetros fechados antes de derivar. BCrypt legado `$2$` (59 caracteres) ou `$2a$`/`$2b$`/`$2y$` (60), custo04–31 e alfabeto próprio preservados. Demais formatos, versão/custo inválido, parâmetros excessivos, tamanhos/base64 errados ou sufixos:401 genérico sem encoder/fallback/escrita/token. Falha operacional após reconhecimento:500 existente, sem hash/causa sensível nos logs. Regras completas e fundamento em R4.

A validação da senha do login é a união A ∪ B, nunca a interseção: A é o domínio antigo de `@NotBlank` do provider atual; B é a senha válida009. O verificador recebe sempre a sequência original. Não restringir a parcela A por12/64, Unicode ou lista de espaços009. Não mudar name, dados de wire, token ou mensagens; contratos históricos permanecem como snapshots. Testes futuros devem provar inclusão de A e comportamento de B, além da ausência de rehash.

## Objetos transitórios, sem nova persistência

- **CustomerRegistrationRequestDTO**: seis campos raw string name/fullName/email/phone/birthDate/password. `@Valid` e constraints locais na fronteira. Não gerar toString com senha ou serializar o request. Unknown/duplicate JSON rejeitado antes do serviço.
- **RegistrationAttemptReference**: Clock.instant e sua data civil UTC imutável por tentativa, capturados em interceptor exclusivo antes de resolver argumentos. Não é campo de wire, de tabela ou input do cliente. Validator recebe essa referência fixa, sem IO de domínio.
- **Comando validado**: name exato, fullName/email aparados, emailKey, phone exato, birthDate e hash; sem ID ou roles do consumidor. Raw password só existe até derivação, nunca no agregado/evento/confirmação. Comando não é entidade paralela.
- **CustomerRegistrationConfirmationDTO**: somente id/name; construído internamente na transação, entregue como HTTP201 só após commit.
- **Erros**: reaproveitar ErrorResponseDTO(status,error,message,path). Exceções específicas não revelam campo de duplicidade, SQL ou valores; classificação de constraint é interna e fechada.

## Estados e operação atômica

Não acrescentar status cadastral persistido, flag de perfil incompleto, sessão ou token. Estados abaixo descrevem a tentativa, não novos enum/tabelas:

| Momento | Efeito permitido | Resultado |
| --- | --- | --- |
| Entrada inválida | Nenhuma escrita. |400 seguro. |
| Disponibilidade/derivação | Nenhuma escrita; falha interna não vira duplicidade. |409 apenas para duplicidade diagnosticada;500 interno. |
| Worker transacional | persist de conta nova + coleção CLIENT; flush sem sucesso público. | Falha conhecida anterior à efetivação desfaz todas as parcelas. |
| Commit concluído | Nova conta e CLIENT visíveis em conexão independente. |201 id/name; ainda não autenticada. |
| Entrega falha após commit | Conta permanece; nenhuma compensação. | Reenvio409, sem confirmação reutilizada. |
| Commit indeterminado | Não inferir presença/ausência de resposta. |500 quando entregável; sem retry, compensação ou sucesso presumido. |

O worker REQUIRES_NEW possui a transação física; facade/controller não abrem transação envolvente no caminho HTTP. Entidade nova tem id null e é persistida, nunca merged. Todas as constraints podem falhar no flush/commit; tradução ocorre após saída do proxy. Conexões de diagnóstico pós-falha não são usadas para reconstruir suposto sucesso.

## Concorrência e ativação do schema

Constraints são autoridade mesmo se duas disponibilidades lerem livre simultaneamente. Disputa por qualquer identidade produz um vencedor e uma rejeição genérica quando não há outras falhas. Distintas identidades podem concluir independentemente. Nenhum lock global/JVM para simular unicidade. Timeouts/deadlocks continuam internos.

Implementação futura inspeciona DDL/metadados H2 para as duas constraints e índices reais, NULLs múltiplos e comparação Ana/ana. A lista exata de identidades de índices por tabela/coluna sustenta reconhecimento seguro; outro23505/FK/PK/role é500. Nenhum sufixo de nome gerado ou texto contendo valor do consumidor é prova suficiente.

No projeto atual, bootstrap cria H2 em memória; não há migração de produção ou schema externo existente validado. Provar legado por fixtures anteriores à extensão do cadastro, preservando NULLs e hashes reais. Uma ativação futura sobre base persistente exige plano específico: adicionar campos nullable sem reconstruir dados históricos, diagnosticar emails presentes/conflitos sem resolvê-los automaticamente e somente então ativar a constraint. Não executar DDL, preenchimento ou migração nesta fase.
