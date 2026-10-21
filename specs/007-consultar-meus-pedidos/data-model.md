# Modelo de dados — Feature 007

**Estado**: desenho de leitura sobre modelo existente; sem alteração de entidades, tabelas, constraints, enum ou migração. Ver [spec](spec.md), [pesquisa](research.md) e [contrato](contracts/own-orders-openapi.yaml).

## Entidades existentes reutilizadas

| Entidade/tabela | Campos e relações relevantes | Natureza e uso na consulta |
| --- | --- | --- |
| UserAccount / tb_user_account | id Long; name; passwordHash; roles em tb_user_account_role | id autenticado delimita o titular. Existência/perfis atuais por projeção escalar. Nome/hash/perfis fora da resposta. |
| Order / tb_order | id Long; buyer LAZY obrigatório; createdAt Instant; status enum STRING; items ordenados | id, titular e instante registrados. status atual armazenado, sem transição. total derivado, não coluna. |
| OrderItem / tb_order_item | id interno; order; product LAZY; position inteiro >=0; quantity inteiro >0; unitPrice decimal19,2 positivo | Quantidade, preço unitário e posição registrados/históricos. subtotal derivado. id interno/position/order fora da resposta. |
| Product / tb_product | id Long e name, além dos demais campos atuais | Identificação referenciada e nome atual integral. price, descrição, imagem e categorias não participam do cálculo/elegibilidade de leitura. |

Uma conta possui pedidos; cada pedido possui itens; cada item referencia produto existente. Unicidades `(order_id, product_id)` e `(order_id, position)`, FKs e verificações de quantidade/preço/posição já existem. `Order.items` declara `@OrderBy("position ASC")`. A 005 impede excluir produto referenciado. Não congelar nome/imagem ou criar associação nova.

Fontes: [Order](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java), [OrderItem](../../src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java), [Product](../../src/main/java/com/devsuperior/dscommerce/entities/Product.java), [UserAccount](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java), [OrderStatus](../../src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java).

## Projeções de API propostas

| Representação futura | Campos exatos | Origem |
| --- | --- | --- |
| OwnOrderSummaryDTO | id, createdAt, status, total | Identificador/instante registrados; estado atual; total histórico derivado. |
| PageResponseDTO<OwnOrderSummaryDTO> | content, page, size, totalElements, totalPages | Página do conjunto próprio; tipo existente sem alteração. |
| OwnOrderDetailDTO | id, createdAt, status, total, items | Mesmos quatro campos do resumo e linhas na ordem registrada. Não herdar campos da confirmação. |
| OwnOrderItemDTO | productId, productName, quantity, unitPrice, subtotal | productId referenciado; productName atual; quantity/unitPrice históricos; subtotal histórico derivado. |

Sem accountId, buyer, nome de conta, roles, senha/hash, itemId, posição interna, preço atual, imagem ou categorias nas saídas. IDs int64 positivos; quantidade int32 positiva. `createdAt` segue o formato existente UTC com milissegundos `uuuu-MM-dd'T'HH:mm:ss.SSS'Z'`. O enum atual só contém WAITING_PAYMENT; não acrescentar estados. Estado futuro requer evolução pela respectiva feature.

## Invariantes financeiros

`subtotal = unitPrice registrado × quantity registrada`; `total = soma dos subtotais`. Reutilizar `OrderItem.getSubtotal()` e `Order.getTotal()`. Alteração de produto não muda os valores financeiros da compra. Não persistir total/subtotal novo.

Montantes são strings positivas de base dez, ponto e duas casas, sem expoente/arredondamento, respeitando decimal19,2 vigente (até 17 dígitos inteiros). Escala 2 com UNNECESSARY e toPlainString; verificação de capacidade/formato sem escrita. `0.01` e `9007199254740993.01` não passam por float/double. Corrupção inesperada gera 500 seguro, sem reparação ou aumento de capacidade.

Nome é **atual**, não snapshot histórico: texto integral, inclusive espaços/acentos. Não acionar validator administrativo nem validar todo o grafo; preço atual zero, nome legado fora do tamanho de escrita ou categorias vazias não invalidam compra anterior.

## Entrada e acesso propostos

`OwnOrderPageRequestDTO` não contém titular. page/size Integer inicializados em 0/12 somente por omissão; vazio/inválido rejeitado. Bean Validation: NotNull/Min(0) page, NotNull/Min(1)/Max(50) size, AssertTrue puro para produto long page×size <=2147483647, tolerando nulidade/limites básicos inválidos já tratados por constraints próprias. Não expor propriedade auxiliar do offset na resposta. ID Long positivo validado antes do serviço.

Somente nomes exatos `page` e `size` do mapa de parâmetros decodificado pelo servlet participam da entrada de listagem. Resolução HTTP específica desse DTO copia suas listas completas e descarta os demais nomes antes de qualquer construção/binding ou transformação de nomes do binder. `_page`, `_size`, `size[]` e `!page`, mesmo repetidos/vazios/inválidos, não são aliases aceitos e não modificam defaults ou valores explícitos nem causam rejeição. Repetição dos nomes exatos permitidos é rejeitada antes de conversão/binding. Resolver cria o DTO pelo construtor sem argumentos, vincula somente o mapa filtrado e aciona Bean Validation do parâmetro `@Valid` antes do serviço, sem lookup. Não reler a requisição original por binding automático `@ModelAttribute`; limitar campos do binder é defesa complementar, não substitui a filtragem prévia. Sem mudança de outras rotas ou binder global. Trata-se de correção documental do P2 estático; prova HTTP fica para a implementação.

Titular vem exclusivamente de `AuthenticatedAccountPrincipal`, nunca query/path/body ou claim de role. Projeção escalar existente dá existência/perfis atuais, inclusive ausência de role por left join. Conta ausente 401, sem CLIENT/ADMIN 403, falha interna 500. ADMIN não remove titularidade.

## Consultas futuras

1. Página de `Order.id`, `o.buyer.id = :ownerId`, ordem `o.createdAt DESC, o.id DESC`; offset/limit só nessa consulta. countQuery `count(o.id)` com o mesmo titular, sem joins de itens.
2. Lote: Order com left fetch de items/product, `o.id IN :selectedIds AND o.buyer.id = :ownerId`, **sem Pageable**. Left joins conservam raiz para detectar agregado incoerente sem falso 404. Uma coleção somente, sem categorias. Página vazia dispensa fetch.
3. Detalhe: mesmo fetch, `o.id = :id AND o.buyer.id = :ownerId`. Ausente =>404 indistinto, sem exists global. Raiz encontrada mas incoerente =>500.
4. Serviço mapeia raízes por ID e percorre sequência paginada, verificando correspondência completa; itens por position ASC. Expansão SQL não duplica respostas. Não depender da ordem do IN ou agrupar por nome.

Guarda, page/count/fetch, cálculos e DTOs dentro do serviço readOnly, sem save/flush/clear/correção; sem acesso lazy após retorno, OSIV ou locks novos. Não prometer fotografia imutável entre requisições. readOnly é intenção/otimização, não prova de ausência de mutação.

totalElements int64 e totalPages int32 seguem o DTO existente. Ceil inteiro seguro, zero páginas se zero elementos; overflow não é saturado silenciosamente. Página posterior válida conserva page/size/totais próprios. Contagem nunca inclui outra conta ou linhas expandidas.

## Ciclo de vida e compatibilidade

Sem comandos, transições ou pagamento. GET/HEAD não alteram produtos, categorias, contas, roles, pedidos, itens ou carrinho. Sem snapshot, migração ou regularização de cadastro. Modelo/DTOs de registro 004, manutenção 005 e consultas públicas 001/002/006 preservados; contrato novo acrescenta apenas leituras próprias.
