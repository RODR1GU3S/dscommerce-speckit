# Data Model: Registrar pedido

Desenho futuro derivado da [spec](spec.md) e das [decisões D1–D8](research.md). Nenhuma entidade ou tabela foi criada neste checkpoint.

## UserAccount existente e perfis mínimos

Preservar id Long gerado, name único/exato e passwordHash BCrypt não serializado. Acrescentar `roles: Set<UserRole>` lazy como `@ElementCollection`, `@Enumerated(STRING)`, tabela `tb_user_account_role(user_account_id, role)`, FK para tb_user_account e unicidade do par. Definir `user_account_id NOT NULL` com `@CollectionTable(joinColumns = @JoinColumn(name = "user_account_id", nullable = false))` e `role NOT NULL` com `@Column(name = "role", nullable = false)`. Enum fixo `CLIENT` e `ADMIN` representa Cliente/Admin. Conjunto vazio é permitido pela ausência de linhas, nunca por vínculo ou perfil nulo; não concede acesso a pedidos. Sem entidade de usuário paralela, CRUD de Role ou atribuição automática de perfil.

UserAccountRepository mantém findByName do login e recebe consulta por id com perfis carregados explicitamente. AuthenticatedAccountService usa transação read-only para mapear id/authorities, sem depender de sessão de persistência aberta na view. Nome/hash/perfis não são retornados no pedido. Fixtures com Cliente, Admin isolado, ambos e nenhum perfil pertencem apenas a testes.

## Order — tb_order

| Campo | Tipo / persistência | Regra |
| --- | --- | --- |
| id | Long, PK IDENTITY | gerado, positivo; único, sem promessa de sequência contínua |
| buyer | ManyToOne UserAccount, LAZY, buyer_id NOT NULL FK | conta autenticada existente; sem cascade para conta |
| createdAt | Instant, created_at NOT NULL | UTC, Clock do sistema no registro, truncado a milissegundos |
| status | OrderStatus, enum STRING NOT NULL | único estado entregue: WAITING_PAYMENT |
| items | List<OrderItem>, OneToMany mappedBy order | não vazia; cascade PERSIST, ordenada por position ASC |
| total | BigDecimal derivado, não persistido | soma dos subtotais históricos, dentro da capacidade19,2 |

Agregado criado por serviço após validar todos os itens. Associação buyer nunca é fornecida pelo request. Sem setters/operação pública de mudança de comprador, preço/quantidade de compra ou estado. Não incluir relação de pedidos em UserAccount/Product para navegação não necessária. Sem remoção em cascata para produto/conta ou gerenciamento de ciclo de vida fora do registro.

A coleção não vazia é invariância do agregado, além do `@NotEmpty` do DTO. A fábrica/construtor de domínio deve receber e validar a lista completa, rejeitar lista nula/vazia ou item nulo, estabelecer os vínculos e posições e impedir exposição de uma coleção mutável que permita esvaziá-la. O construtor sem argumentos exigido pelo JPA permanece protegido, sem servir como caminho público de criação de pedido vazio. Reforçar `items` com `@NotEmpty` e validação em cascata `@Valid`; o serviço só entrega o agregado completo à persistência. FK, NOT NULL e CHECK por linha não garantem que um pedido tenha ao menos um item: essa invariância depende da construção/validação do agregado, com testes de domínio e persistência planejados, sem trigger ou operação de gravação de pedido vazio.

## OrderItem — tb_order_item

| Campo | Tipo / persistência | Regra |
| --- | --- | --- |
| id | Long, PK IDENTITY | interno; não necessário na resposta de item |
| order | ManyToOne Order, LAZY, order_id NOT NULL FK | mesmo agregado; vínculo obrigatório |
| product | ManyToOne Product, LAZY, product_id NOT NULL FK | produto existente; sem cascade |
| position | Integer, NOT NULL | posição zero-based da linha enviada, >= 0 |
| quantity | Integer, NOT NULL | 1..2147483647 |
| unitPrice | BigDecimal DECIMAL(19,2), NOT NULL | snapshot estritamente positivo, 0.01..99999999999999999.99 |
| subtotal | BigDecimal derivado, não persistido | unitPrice × quantity, capacidade19,2 |

Unicidade `(order_id, product_id)` e `(order_id, position)`. Bean Validation na entidade reforça quantity positiva, position não negativa, unitPrice `@DecimalMin("0.01")` e `@Digits(integer=17, fraction=2)`. O serviço rejeita duplicata antes da persistência; constraint é defesa de integridade, não consolidação.

O mecanismo dos CHECKs será `org.hibernate.annotations.Check` na entidade OrderItem, com três anotações nomeadas: `ck_order_item_quantity_positive` (`quantity > 0`), `ck_order_item_position_nonnegative` (`position >= 0`) e `ck_order_item_unit_price_positive` (`unit_price > 0`). As expressões usam os nomes físicos das colunas, com `unitPrice` mapeado explicitamente para `unit_price`. A [API Hibernate 6.5 de @Check](https://docs.hibernate.org/orm/6.5/javadocs/org/hibernate/annotations/Check.html) define sua inclusão no DDL gerado; Hibernate 6.5.3.Final é gerenciado pelo Boot 3.3.4 deste projeto. Assim, `ddl-auto=create/create-drop` gera essas constraints junto de FK/NOT NULL/unicidades; não depender da inferência de Bean Validation para criá-las nem acrescentar migração de produção. Cada coluna continua NOT NULL, pois CHECK não substitui essa restrição. OrderRepositoryTest deverá conferir o schema H2 e rejeições por SQL/JDBC direto para quantity zero/negativa, position negativa e unit_price zero/negativo, além de nulos, FKs e duplicatas, sem usar falha prévia de Bean Validation como prova de CHECK. Nenhum DDL foi gerado nesta revisão.

Subtotais e total não precisam de colunas redundantes: quantity e unitPrice imutáveis são sua fonte histórica. Recalcular somente a partir desses campos, nunca do preço atual do produto. Qualquer mapeamento futuro preserva esse invariante; não há GET de pedido nesta feature.

## Product preservado

[Product atual](../../src/main/java/com/devsuperior/dscommerce/entities/Product.java) usa BigDecimal DECIMAL(19,2) e @PositiveOrZero. Continua aceitando zero no cadastro/modelo; a regra > 0 é do pedido e será avaliada em OrderService. Sem mudanças em Product, suas categorias, catálogo, detalhes ou suas validações. Preço negativo no teste da compra será preparado por JDBC isolado, sem remover validações existentes.

## Tipos e cálculos

- productId/accountId/order id: Long positivo, até `9223372036854775807`. quantity: Integer positivo, até `2147483647`; sem limitação comercial adicional.
- Preço, subtotal e total: BigDecimal decimal19,2; máximo `99999999999999999.99`, mínimo positivo `0.01`. Rejeitar preço nulo/zero/negativo ou indisponível, overflow e perda de precisão.
- Normalizar preço para escala 2 com UNNECESSARY. Multiplicar pela quantidade inteira e somar sem MathContext de arredondamento; checar cada subtotal e o total antes do save. Zeros além da segunda casa podem ser removidos sem perda; dígitos não nulos além dela são rejeitados.
- Nenhum frete/desconto/acréscimo, moeda adicional, congelamento no carrinho ou segunda confirmação. Instante corresponde à criação do agregado dentro da operação; não precisa coincidir com o instante exato do commit.
- Resposta serializa valores como strings com exatamente duas casas, formato decimal sem expoente. Não converter para float/double. IDs permanecem números JSON int64; consumidores precisam preservar sua representação inteira.

## DTOs de entrada e saída

| DTO | Campos | Validação / mapping |
| --- | --- | --- |
| OrderRequestDTO | items: List<OrderItemRequestDTO> | @NotEmpty, @Valid; cada elemento @NotNull; somente propriedade items |
| OrderItemRequestDTO | productId: Long, quantity: Integer | @NotNull/@Positive, quantity @Max(2147483647); somente essas propriedades |
| OrderResponseDTO | id, accountId, createdAt, status, items, total | DTO imutável; mapeado no serviço; status WAITING_PAYMENT, instante ISO8601 UTC |
| OrderItemResponseDTO | productId, quantity, unitPrice, subtotal | snapshot; strings monetárias, ordem da solicitação |

Desserializador específico rejeita tipos incompatíveis, token fracionário/decimal, número fora de capacidade, campos extras e chaves JSON repetidas. Bean Validation é aplicada no controller depois da leitura estrutural; unicidade/existência/preço/cálculos permanecem no serviço.

## Conceitos transitórios de segurança

Principal da operação contém accountId da UserAccount resolvida; authorities derivadas dos perfis no banco. Não é outra entidade persistida. JWT existente tem sub/name/iat/exp; não adicionar claim obrigatório de roles/iss/aud. Converter valida a operação e devolve autenticação com principal mínimo; OrderService recebe essa identidade, nunca um titular da entrada, e revalida conta/perfis em sua transação.

## Estado e unidade de persistência

```text
JWT/conta/perfil válidos + itens válidos
  -> capturar preços e calcular valores
  -> Order(WAITING_PAYMENT) + OrderItems
  -> flush e commit integral
  -> confirmação 201
```

Falha anterior à conclusão: rollback de todo agregado, sem sucesso. Falha de transporte após commit: pedido persiste e novo envio válido cria outro, conforme Q1. Nenhuma transição de pagamento, consulta ou atualização é modelada.

OrderService é método público invocado por proxy, REQUIRED com rollbackFor Exception. A transação abrange revalidação, leituras, snapshot, gravação e DTO; não há transação independente por item. saveAndFlush antecipa SQL mas não confirma commit. O teste deverá observar o banco depois da saída do proxy, sem transação de teste externa, conforme [quickstart](quickstart.md).
