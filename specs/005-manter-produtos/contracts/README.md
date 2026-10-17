# Contratos: manutenção administrativa de produtos

[OpenAPI 3.0.3](product-maintenance-openapi.yaml) é o contrato planejado da Feature 005, ainda não implementado. [Plano](../plan.md), [modelo](../data-model.md) e [quickstart](../quickstart.md) complementam regras não expressáveis integralmente por schema. Spec/checklist aprovados não foram modificados.

Contratos preservados: [catálogo](../../001-consulta-catalogo-produtos/contracts/products-openapi.yaml), [detalhe público](../../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml), [login](../../003-login-autenticacao/contracts/login-openapi.yaml), [registro de pedido](../../004-registrar-pedido/contracts/orders-openapi.yaml). GETs públicos continuam anônimos, com preço numérico e categorias por nome no detalhe; credencial inválida irrelevante não altera sua resposta. Nenhuma rota pública nova ou consulta de pedidos é definida.

## Operações

| Método/recurso | Sucesso | Dados/comportamento |
| --- | --- | --- |
| GET `/admin/products?name=&page=0&size=12` | 200 | Página `content,page,size,totalElements,totalPages`; itens id/name/image/price string; filtro literal novo somente ADMIN. |
| GET `/admin/products/{id}` | 200 | id/name/description/image/price/categories ID+nome; leitura para edição, inclusive legado. |
| POST `/admin/products` | 201 | Cinco campos completos; resposta detalhe e Location para GET administrativo com context path preservado. |
| PUT `/admin/products/{id}` | 200 | Substituição integral com mesmo ID; nenhum upsert ou campo omitido conservado implicitamente. |
| DELETE `/admin/products/{id}` | 204 | Sem corpo; produto e seus vínculos removidos, categorias e compras preservadas. |
| GET `/admin/categories` | 200 | Array de todas as opções ID/nome, inclusive vazias, não utilizadas ou de mesmo nome. |

ADMIN autenticado obrigatório para todas, sem exigir CLIENT. Namespace administrativo inteiro fica protegido, inclusive métodos/rotas não suportados. JWT HS256/temporal/sub reutilizados do login; identidade/perfis atuais vêm do banco. Claims de name/roles não concedem acesso. Filtros precedem leitura; serviços revalidam identidade/perfil antes de acessar dados administrativos.

Busca usa parâmetro opcional name; ausência/vazio após remover espaços externos não restringe. Maiúsculas/minúsculas ignoradas por LOWER dos dois operandos no H2; acentos e internos preservados; LOCATE não interpreta `%`, `_`, barras/apóstrofos como curingas. Ordenar pelo nome original ASC, depois ID ASC. page inteiro representável ≥0; size 1–50, default 12. Count/totalPages usam o mesmo filtro; página posterior mantém metadados e content vazio. Consumidor reapresenta name nas páginas seguintes; não há estado de busca de sessão. Dataset estável é a condição da ordem determinística entre consultas.

Somente name/page/size são parâmetros aceitos na lista administrativa; repetidos/desconhecidos causam 400 sem eco de valores. Demais operações não recebem query. GET de categorias não tem filtro, paginação própria, busca, hierarquia, relatórios ou escrita: mantém-se a assunção delimitadora da spec. Ordenar opções por name/id; ID distingue nomes iguais. Associações atuais aparecem somente no detalhe do produto.

## Entrada e representações

Objeto JSON exclusivamente com `name,description,price,image,categoryIds`; todos obrigatórios, inclusive PUT. Nome e descrição são strings: remover externos reconhecidos por Character.isWhitespace/isSpaceChar, contar pontos de código Unicode e armazenar a forma resultante. Nome 3–80, descrição ≥10; internos/acentos/caixa/forma Unicode preservados. Não aplicar maxLength bruto 80 a texto com padding válido. Não impor máximo comercial para descrição/imagem.

price é string decimal de base 10 com ponto, sem expoente/vírgula/espaços/+; formato no OpenAPI. Parse BigDecimal exato, valor positivo e normalizável para duas casas sem perda, até 99999999999999999.99. `1.000` é aceito como `1.00`; `1.001` é rejeitado. Tokens numéricos JSON não são aceitos nessa entrada administrativa. Saídas monetárias administrativas são strings com duas casas, permitindo `0.00` nas leituras legadas. Não mudar tipos públicos ou pedidos.

image é URI absoluta hierárquica http/https (esquema case-insensitive) com autoridade/host sintaticamente válidos; validação URI/parseServerAuthority, sem URL.openConnection/DNS/HTTP. Não aparar/reescrever a imagem enviada. Espaços não escapados, raiz relativa, esquema diferente, vazio/nulo ou sintaxe malformada rejeitam. Componentes opcionais sintaticamente válidos, query/fragmento inclusive, não ganham proibição de conteúdo. Indisponibilidade externa não interfere.

categoryIds é array não vazio de inteiros positivos Long representáveis, sem coerção de strings/booleanos/decimais. Repetição de ID é válida e produz uma associação por ID distinto; todos os IDs distintos devem existir. Categoria distinta com mesmo nome permanece distinta. Não aceitar subconjunto, criar categoria ou manter antigas para contornar seleção vazia. Resposta categories contém opções completas ordenadas name/id.

Leitor streaming scoped ao DTO administrativo avalia o objeto completo examinável. Tipos errados, propriedades extras ou chaves repetidas ficam como diagnósticos, sem esconder campos independentes; não configurar coerção/Jackson global. Campo duplicado é ambíguo e não fornece valor confiável para validar regra desse próprio campo; demais campos continuam examinados. categoryIds parcialmente malformado permite verificar IDs legíveis, preservando índices dos erros. Corpo ausente, raiz não objeto, sintaxe quebrada ou conteúdo residual recebe defeito de body, sem inventar todos os campos.

Schema expressa estrutura; positividade/capacidade decimal, remoção de espaços, existência de categorias, agregação, atomicidade e regras do legado exigem diagnóstico/serviços e testes futuros. Respostas de leitura não carregam constraints de escrita em nome, descrição, imagem ou categorias vazias.

## Envelope, códigos e precedência

Erros administrativos contêm **exatamente** `status,error,message,path,errors`. path é URI externa, como `/api/admin/products/9`, sem query. errors sempre é array, sem campos extras; cada item tem exatamente field/code/message, sem valor rejeitado. Contratos anteriores continuam com quatro campos e mensagens atuais. Identificar correção com mensagem segura; não retornar nome arbitrário de propriedade extra, payload, token, conta/perfis restritos, senha/hash, segredo, SQL ou stack trace.

| HTTP | message | errors | Origem |
| --- | --- | --- | --- |
| 400 | Invalid product maintenance data | diagnósticos conhecidos | corpo/parâmetros/campos inválidos; referência de categoria inexistente; nenhuma mutação parcial. |
| 401 | Authentication required or invalid | [] | credencial ausente/inválida/expirada, sub ou conta ausente; WWW-Authenticate: Bearer. |
| 403 | Product maintenance not permitted | [] | conta existente sem ADMIN, inclusive CLIENT ou sem nenhum perfil. |
| 404 | Product not found | [] | alvo inexistente em consulta/PUT válido/DELETE; não cria nem confirma alteração inexistente. |
| 409 | Product deletion violates referential integrity | [] | restrição referencial comprovada de exclusão, inclusive qualquer OrderItem. |
| 500 | Product maintenance could not be completed | [] | falha interna de resolução/diagnóstico/processamento/persistência/conclusão, inclusive timeout ou integridade não classificável. |
| 405/406/415 | Product maintenance request not supported | [] | erro HTTP nativo; manter status e cabeçalhos aplicáveis, como Allow/Accept, sem expor exception text. |

error corresponde à reason phrase do status. Em 406/transmissão, envelope só é garantido se a resposta puder ser escrita; isso não autoriza alterar dados ou prometer rollback pós-commit. O OpenAPI enumera métodos suportados; a regra de 405 para outros verbos está aqui, sem inventar operação PATCH/CRUD de categorias no contrato.

Campos: body, id, page, size, name, description, price, image, categoryIds e categoryIds[index] (índice zero-based). Ordenar por essa sequência de campos, depois índice numérico, depois code; deduplicar pelo trio field/code/message. CATEGORY_NOT_FOUND é informado em cada índice legível que referencie ID ausente, inclusive repetição ausente, sem ecoar o ID. Repetições válidas não geram erro. Falta de categoria inteira usa categoryIds/EMPTY_CATEGORIES; ausência/nulo usa REQUIRED. Tipo malformado usa INVALID_TYPE em vez de inventar ausência.

| code | Significado |
| --- | --- |
| REQUIRED | campo obrigatório ausente/nulo ou texto vazio após normalização |
| INVALID_TYPE | token do tipo incorreto, sem coerção |
| INVALID_FORMAT | string decimal/ID fora da representação aceita |
| INVALID_LENGTH | nome/descrição fora dos limites após normalização |
| NOT_POSITIVE | preço/identificador não positivo |
| UNREPRESENTABLE_MONEY | preço com perda de valor ou capacidade excedida |
| INVALID_IMAGE_URL | imagem não satisfaz sintaxe absoluta http/https |
| EMPTY_CATEGORIES | seleção sem categoria distinta |
| CATEGORY_NOT_FOUND | referência examinável de categoria ausente |
| UNKNOWN_FIELD / DUPLICATE_FIELD | propriedade extra/chave JSON repetida, sem eco de dados arbitrários |
| INVALID_BODY | corpo não examinável integralmente |
| INVALID_PARAMETER | parâmetro/path inválido, extra ou repetido |

Precedência técnica:

1. Assinatura/tempo/identidade e resolução da conta (401 ou 500 interno); autorização ADMIN (403), antes de corpo/dados restritos.
2. Representação HTTP e parâmetros; erros nativos 405/406/415 preservados. Segurança continua anterior a verbo/media type inválido.
3. Leitura e diagnóstico Bean Validation completo de solicitação examinável autorizada: regras locais e categorias legíveis na mesma avaliação (400). Falha interna de diagnóstico impede avaliar integralmente e retorna 500 seguro.
4. Com escrita válida, iniciar serviço transacional repetindo primeiro guarda da conta/ADMIN, antes de qualquer lookup administrativo. Guardas tardias podem devolver 401/403 sem escrita mesmo após filtro inicial válido. Em seguida localizar/lockar o produto alvo (404 para PUT/DELETE; nunca upsert). Corpo inválido + alvo inexistente resulta em 400 agregado na fronteira; corpo válido + alvo inexistente em PUT é US3.5/SC-010.
5. Com guarda e alvo válidos, reavaliar dados/referências na transação antes de mutar; verificar integridade de exclusão (409). Para POST não há lookup de alvo existente.
6. Persistência/flush/commit: somente falha referencial inequivocamente identificada é traduzida como negócio. FK de categoria ausente reconhecida em escrita pode causar 400/categoryIds; FK de entrada impeditiva comprovada em exclusão causa 409. Demais falhas são 500, nunca erro do consumidor inventado. Não continuar após falha.

No estágio 3, não interromper avaliação por primeiro campo inválido nem usar validação sequencial que esconda categoria inexistente junto com os quatro campos de US2.6. Resposta confirma somente quando todos os dados e a transação são válidos/concluídos.

O diagnóstico protegido invocado por ConstraintValidator pode lançar exceção runtime envolvida em ValidationException. Handler central ADMIN deve preservar 401/403 para causas conhecidas de conta/permissão e 500 para falha interna/inesperada; não confundir esse wrapper com dados inválidos 400. Não modificar DTO durante isValid. Falha de FK de categoria tardia informa somente referência ausente comprovada, sem prometer rediagnóstico completo depois de falha transacional.

## Integridade, concorrência e limites

Substituir somente o Product selecionado e suas associações, preservar categorias retiradas da seleção. Não modificar pedidos/itens em alteração de preço. Excluir produto usado rejeita integralmente; FK restritiva no banco protege a corrida não coberta pelo precheck. Lock PESSIMISTIC_WRITE administrativo ordena escritas no produto, mas não acrescenta lock ao registro de pedidos já existente.

Se compra vincular primeiro, exclusão falha sem remover definitivamente os vínculos; se exclusão prevalecer, não pode persistir item órfão. Tratamento do erro de compra conserva contrato 004. Deadlock/timeout ou falha não classificável não gera sucesso fictício. Não prometer ordem entre operações concorrentes nem recuperação idempotente de resposta perdida.

Flush não é commit; erro beforeCommit/commit antes da delegação é simulação delimitada. Falha após commit real ou durante transmissão da resposta pode deixar operação confirmada no banco; não há promessa de rollback ou protocolo de reconciliação novo. Validar estado por conexão independente, antes de limpeza, conforme quickstart.

## Aceitação documental e funcional futura

Contrato deve passar parser/validador OpenAPI e resolução de referências, separadamente da verificação de exemplos. Isso não prova autorização, SQL, normalização, agregação, concorrência ou rollback. A [matriz do plano](../plan.md) cobre todos os 20 requisitos/37 cenários/10 critérios; V01–V13 no quickstart descrevem provas futuras independentes. Nenhuma execução funcional é alegada por estes artefatos.
