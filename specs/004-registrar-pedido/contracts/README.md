# Contrato: registro de pedido

[OpenAPI 3.0.3](orders-openapi.yaml) define somente `POST /orders`; contratos de [catálogo](../../001-consulta-catalogo-produtos/contracts/products-openapi.yaml), [detalhes](../../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml) e [login](../../003-login-autenticacao/contracts/login-openapi.yaml) permanecem intactos. O endpoint está implementado na branch `004-registrar-pedido`, com autenticação Bearer, autorização CLIENT/ADMIN, JSON estrito, cálculos monetários exatos, snapshots de preços e persistência atômica. As evidências da implementação e validação local estão no [quickstart](../quickstart.md), mantendo o escopo e os limites descritos abaixo.

## Sucesso e fronteiras

Entrada JSON contém exclusivamente items; cada linha contém productId e quantity inteiros positivos representáveis. Titular, preços, subtotal/total, id, instante e estado são determinados pelo sistema; fornecê-los, inclusive nulos, causa 400. Chaves JSON repetidas, campos extras, coerções e tokens decimais como quantity 1.0 são rejeitados. Repetir productId em linhas diferentes causa 422, sem consolidar. Não adicionar limite comercial ao carrinho.

Resposta 201 inclui id, accountId, createdAt UTC, status WAITING_PAYMENT, items (productId, quantity, unitPrice e subtotal) e total. Valores monetários são strings decimais de duas casas, até 99999999999999999.99. Não emitir Location que prometa GET de pedido; nenhuma consulta faz parte deste contrato. Ordem das linhas é a enviada pelo consumidor.

Preço é o vigente lido pelo sistema na transação de registro, estritamente positivo e copiado para a compra. Mudança posterior no produto não altera unitPrice/subtotal/total. subtotal = unitPrice × quantity; total = soma, sem frete/desconto/acréscimo. Overflow ou preço não representável é 422 sem gravação. Os limites completos estão no [modelo](../data-model.md).

Cada POST válido cria outro pedido; corpo igual ou perda da resposta anterior não provoca reutilização/deduplicação. Não há Idempotency-Key suportada ou recuperação de confirmação. Carrinho é do consumidor; não há pós-condição de esvaziamento no backend.

## Segurança e precedência de erros

Somente `Authorization: Bearer <JWT>` autentica a operação. Reusar assinatura HS256/chave e sub do login existente. Assinatura e intervalo temporal são verificados antes do lookup; sub aponta a UserAccount existente e authorities vêm de CLIENT/ADMIN no banco. Admin isolado é suficiente. Claims name/roles não escolhem titular nem concedem acesso.

Primeiro segurança (401/403/500 de resolução), depois leitura/Bean Validation (400), depois regras de negócio (404/422), depois persistência (500). Corpo inválido sem autenticação recebe 401; com conta sem perfil recebe 403. Quando há múltiplos erros de negócio não se promete qual mensagem será selecionada primeiro; qualquer rejeição impede toda a gravação.

| HTTP | error | message | Origem |
| --- | --- | --- | --- |
| 400 | Bad Request | Invalid order data | corpo ausente/ilegível, nulos, tipos, limites inteiros, campos extras/chaves repetidas/validação |
| 401 | Unauthorized | Authentication required or invalid | token ausente/inválido/expirado, claims/sub inválidos ou conta ausente |
| 403 | Forbidden | Order registration not permitted | conta existente sem Cliente nem Admin |
| 404 | Not Found | Product not found | um produto inexistente; reusa exceção atual |
| 422 | Unprocessable Entity | Duplicate product in order | produto repetido na mesma solicitação |
| 422 | Unprocessable Entity | Product price is not valid for order | preço null/zero/negativo/indisponível |
| 422 | Unprocessable Entity | Order value cannot be represented | casas não representáveis, overflow de subtotal/total |
| 500 | Internal Server Error | Order registration could not be completed | falha interna, inclusive lookup na segurança, gravação, flush ou commit |

Envelope único com exatamente status, error, message e path (URI externa, como /api/orders). Não incluir token, corpo recebido, hash, segredo, SQL ou stack trace. Erros 401 incluem WWW-Authenticate: Bearer; 403 pode usar challenge Bearer com insufficient_scope sem detalhes internos. Não alterar mensagens específicas de /login.

AuthenticationEntryPoint e AccessDeniedHandler resolvem falhas do filtro. Falhas internas de resolução de conta devem chegar ao adaptador de segurança como falha interna distinguível de credencial inválida e gerar 500. ControllerAdvice cobre falhas do MVC, inclusive guardas tardias de identidade/permissão e erros de commit. Falha na transmissão depois de commit não promete rollback; segue Q1.

## Aceitação do contrato

Testar exemplo 10.00×2 + 7.50×3 = 42.50, perfis, todos os limites/falhas, preço posterior, repetição entre envios e forma exata dos erros. Full-context usa JWT real do login; persistência/rollback exige leitura independente. [Quickstart](../quickstart.md) descreve procedimentos futuros, sem alegar execução atual.
