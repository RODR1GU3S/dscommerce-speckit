# Data model: Manter produtos

**Base**: `fba63732ddb2f27dc03da350d5684c05f3517b75` | [Spec](spec.md) | [Plano](plan.md) | [Contrato](contracts/README.md)

Desenho futuro. Não cria entidades, migrations ou dados neste checkpoint. Reutiliza Product, Category, UserAccount/UserRole, Order e OrderItem existentes.

## Persistência e relações

| Elemento | Estado/desenho | Invariantes |
| --- | --- | --- |
| `tb_product.id` | Long identity existente | ID novo na inclusão; estável na substituição; não reutilizar/upsert em inexistente. |
| `tb_product.name` | String, coluna existente preservada | ADMIN escreve forma sem espaços externos, 3–80 pontos de código; leitura anterior sem nova validação. Não reduzir coluna a 80 e truncar legado. |
| `tb_product.description` | String; planejar texto grande/CLOB em vez do tamanho padrão atual | ADMIN escreve forma sem espaços externos com ≥10 pontos de código; sem máximo comercial; não compactar internos. |
| `tb_product.image` | String; planejar texto grande/CLOB | ADMIN escreve URL http/https sintaticamente válida, armazenada como enviada; nenhuma requisição externa. Legado continua legível mesmo fora da regra nova. |
| `tb_product.price` | BigDecimal, NUMERIC(19,2) existente | Escrita ADMIN >0, ≤99999999999999999.99, duas casas de valor sem perda; entidade conserva PositiveOrZero para legado. |
| `tb_category` | ID/nome existentes | Não incluir, renomear ou excluir categoria; distinguir IDs com nomes iguais e incluir opções sem produtos. |
| `tb_product_category` | Relação many-to-many existente | Um par `(product_id,category_id)` por associação; chave primária/única no banco e ambos NOT NULL/FKs. Nenhuma cascata REMOVE/ALL para Category. |
| `tb_order_item.product_id` | FK obrigatória para Product | Restrição imediata de integridade, sem cascade delete/set null. Existência de qualquer item impede exclusão, independente do status do pedido. |
| `tb_order_item.unit_price` | Snapshot BigDecimal NUMERIC(19,2) já existente | Não atualizar com preço do catálogo; subtotal continua unit_price × quantity. |
| `tb_order` e demais campos de item | Modelo existente preservado | Titular, instante, estado, posições, quantidades, preço unitário, subtotal e total históricos não são modificados pela manutenção. |

Nomear as FKs pertinentes para classificação segura futura: `fk_order_item_product`, `fk_product_category_product`, `fk_product_category_category`. Preservar constraints existentes de pedidos/itens. Verificar nomes, colunas e regra de exclusão na metadata real, não presumir DDL pela anotação. Se o par produto/categoria já possuir PK/unique equivalente gerada, conservar uma única proteção equivalente; não duplicar constraints sem propósito.

A ampliação de armazenamento de descrição/imagem evita máximo de 255 não aprovado; não corrige conteúdo antigo nem cria rotina de migração automática. O ambiente atual usa H2 em memória e recriação de schema; eventual rollout de alteração estrutural em banco persistente exigiria etapa operacional separada. Não reduzir/reescrever coluna ou valores existentes. Testar descrição/URL válidas >255 e leitura pública sem truncamento.

## DTOs e objetos de entrada/saída

| DTO planejado | Campos serializados | Observações |
| --- | --- | --- |
| `ProductWriteRequestDTO` | name, description, price, image, categoryIds | Somente entrada completa. Metadados internos de diagnóstico não são campos aceitos/serializados. |
| `AdminProductItemDTO` | id, name, image, price | Conteúdo da página; preço string com duas casas. |
| `AdminProductDetailDTO` | id, name, description, image, price, categories | categories é lista de ID/nome; permite lista vazia e dados legados fora das novas regras. |
| `CategoryOptionDTO` | id, name | Consulta auxiliar e associações atuais, ordenadas por name/id. |
| `PageResponseDTO<AdminProductItemDTO>` | content, page, size, totalElements, totalPages | Envelope de paginação existente, sem alterar DTOs públicos. |
| `AdminErrorResponseDTO` | status, error, message, path, errors | errors sempre presente; vazio para falha sem campo; formato específico administrativo. |
| `AdminFieldErrorDTO` | field, code, message | Sem valor rejeitado ou dados internos. Ordem determinística; ver códigos no contrato. |

Objeto normalizado interno transporta os valores aprovados para substituição, não entidade ou contrato novo. Principal mínimo contém accountId exclusivamente, proveniente do contexto autenticado. Corpo não pode escolher conta, perfis ou identidade do produto.

## Validação e normalização

### Estrutura examinável

Leitor específico identifica todo campo conhecido e mantém seu estado: ausente/nulo, formato válido ou inválido. Defeitos por campo não impedem examinar campos independentes. Chaves JSON repetidas são inválidas; isso é diferente de repetir valor em categoryIds, que é permitido. Campos extras são rejeitados sem ecoar seus nomes/valores arbitrários. Sintaxe quebrada, raiz não objeto ou conteúdo residual tornam o corpo não examinável integralmente; informar defeito de body, sem produzir diagnósticos inventados.

Restrição Bean Validation de classe adapta diagnóstico dos serviços para violations na fronteira do controller. Falta de formato de um ID não impede verificar outros IDs legíveis; nenhuma consulta usa o valor inválido. Diagnóstico somente leitura repete autorização antes de lookup de categorias. Resultado aprovado não substitui revalidação dentro da transação de escrita.

Não mutar DTO durante isValid: produzir normalização interna imutável e repetir no fluxo de escrita. ValidationException pode envolver falha de guarda/diagnóstico; adaptação central somente administrativa inspeciona causas conhecidas para manter 401/403 de identidade/permissão, enquanto lookup interno e defeito inesperado são 500 seguro.

### Textos

Remover pontos de código externos quando `Character.isWhitespace` ou `Character.isSpaceChar` for verdadeiro, incluindo espaço comum e espaços Unicode reconhecidos. Contar pontos de código Unicode após remoção. Preservar exatamente espaços internos, caixa, acentos e forma Unicode; não remover caracteres zero-width por hipótese nem normalizar NFC. Nome obrigatório 3–80; descrição obrigatória ≥10. DTO/schema da entrada não pode impor maxLength bruto 80 antes de remover espaços.

Filtro usa a mesma remoção externa e mantém internos; não é nome de cadastro e não recebe mínimo/máximo de nome. Ausente ou vazio vira consulta sem filtro. Busca compara LOWER de filtro/nome no H2, por LOCATE literal. Ordenação é nome original ASC/ID ASC, não ordem por texto sem acento ou LOWER. Para dataset estável, páginas têm resultado determinístico; alterações concorrentes entre consultas não prometem snapshot único de navegação.

Imagem obrigatória, string não vazia, URI absoluta hierárquica de autoridade de servidor/host válidos, esquema http/https sem distinguir caixa. `URI` e `parseServerAuthority` sem DNS/HTTP. Espaços não escapados/endereço relativo/esquema diferente/URI malformada rejeitados. Query, fragmento, porta e demais componentes sintaticamente válidos não são proibidos por política adicional. Armazenar a string original válida; URI válida não garante uma imagem real.

### Dinheiro

price recebido como string decimal `-?(0|[1-9][0-9]*)(\.[0-9]+)?`, sem expoente, vírgula, espaços ou sinal +. Parse exato de base 10, sem double. Sinal negativo/zero são diagnosticados como preço não positivo; magnitude excedente e fração com valor além de centavos são inválidas.

Normalizar com `setScale(2, RoundingMode.UNNECESSARY)` e exigir valor >0, precisão normalizada ≤19. `1`, `1.0`, `1.00`, `1.000` são 1.00; `1.001` não é representável. 0.01 e máximo são válidos, 0.00 e máximo+0.01 inválidos. Não aplicar @Digits(fraction=2) à forma bruta se isso rejeitar zeros excedentes sem perda. Resposta e storage usam duas casas de valor; GET administrativo aceita preço legado 0.00. Não mudar a serialização numérica dos GETs públicos nem strings monetárias dos pedidos.

### Categorias

categoryIds obrigatório, array de tokens inteiros positivos ≤Long.MAX_VALUE, com pelo menos um ID distinto. Não aceitar 1.0, string "1", booleano, nulo ou overflow. IDs repetidos válidos são consolidados; todas as categorias distintas devem existir. Manter mapa dos índices de origem para informar erro em cada ocorrência inválida/inexistente, com diagnósticos deduplicados pelo trio field/code/message. O conjunto de vínculos não depende da ordem do array.

Categorias selecionadas são carregadas em lote e comparadas com todo o conjunto solicitado. Categoria A e B com nome igual continuam duas associações. Validar todas as referências examináveis antes de mutar; não persistir subconjunto válido e não criar categorias implicitamente. Em atualização, conjunto vazio não conserva associações antigas: causa rejeição integral.

## Fronteiras e transições

| Operação | Pré-condições/passo final | Sucesso | Rejeição |
| --- | --- | --- | --- |
| Incluir | ADMIN atual, cinco dados válidos, todas categorias distintas existentes | Product com novo ID e pares únicos; confirmação após conclusão transacional | nenhum produto/vínculo parcial; IDs de sequência consumidos não precisam ser reutilizados. |
| Substituir | ADMIN, diagnóstico válido, Product existente lockado, referências rechecadas | Mesmo ID; todos campos e associações refletem nova seleção | inexistente não cria; qualquer falha preserva estado anterior integral. |
| Excluir | ADMIN, Product existente lockado, nenhum impedimento, FK final respeitada | Remove produto e somente seus pares; categorias e outros vínculos permanecem | qualquer OrderItem/outra relação impeditiva rejeita; produto/categorias/pedidos/itens preservados. |
| Consultar | ADMIN nas rotas novas; acesso público nos contratos antigos | Sem revalidar dados anteriores por regras novas | falhas de segurança não expõem dados administrativos. |

Serviços de escrita transacionais via proxy Spring, rollback de falhas; DTO construído dentro da transação após flush, entregue só após retorno externo do proxy. Não engolir falha e continuar salvando. Deleção/atualização usam lock do produto, sem fetch de coleção na consulta de lock; categorias podem ser inicializadas na transação. Lock ordena escritas ADMIN no mesmo produto, sem política nova de ETag/versionamento.

Guardas de conta/perfil são avaliações atuais no início do serviço, antes de consultar/lockar o alvo ou outras informações administrativas. Categorias/associações usam FK como proteção final se houver mudança externa concorrente. Referência ausente de categoria em FK inequivocamente identificada pode gerar 400 dos defeitos comprovados; sem nova avaliação independente não se promete descobrir todas as referências desaparecidas depois do diagnóstico original. Violação interna não classificada gera 500, sem inventar defeitos. Não consultar a mesma transação já falha para tentar recuperar/salvar.

## Exclusão concorrente e integridade final

Precheck de OrderItem por productId inclui todos os estados de pedidos. FK para produto sem cascade/SET NULL preserva referência obrigatória. Quando delete concorre com compra, o banco decide a ordem efetiva: compra já vinculada impede delete; produto removido não aceita novo item órfão. Não declarar que lock somente administrativo serializa OrderService, que atualmente lê sem lock. Preservar a classificação existente de falhas em POST /orders.

Tradução do delete para 409 requer contexto da operação e violação de FK confirmada por causa SQL/constraint/metadata. Não depende de trecho arbitrário de mensagem SQL; erros como coluna, CHECK, unique inesperada, conexão ou timeout não se tornam impedimento referencial. FK de produto em OrderItem deve ser reconhecida, e outras FKs de entrada reais também respeitadas. Late commit exception precisa ser classificada no handler central após proxy, não somente catch no corpo do serviço.

Verificações futuras: metadata e SQL direto para NOT NULL/FK/unicidade; tentativa real de excluir produto referenciado contornando service; duas conexões/barreiras; snapshots independentes das cinco tabelas após sucesso/rejeição. Flush/callback não prova commit físico; H2 memória não prova recuperação pós-queda de energia. Esses limites estão no [quickstart](quickstart.md).
