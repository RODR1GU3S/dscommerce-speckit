# Research: Registrar pedido

**Data**: 2026-10-01 | **Base**: `ed640d710e6ee4c3f1ad6beaef8c3006be8e7417`

Pesquisa de desenho; nenhum comportamento foi implementado ou testado nesta etapa. A [especificação](spec.md) permanece a fonte funcional. As decisões abaixo são técnicas para revisão, não novas aprovações funcionais.

## Contexto conferido agora

- Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `004-registrar-pedido`, HEAD acima e checkout inicialmente limpo. `.specify/feature.json` seleciona esta feature; `SPECIFY_FEATURE` e `SPECIFY_FEATURE_DIRECTORY` não estão definidos. O campo BRANCH do setup foi cruzado com Git.
- [AGENTS.md](../../AGENTS.md), [constituição](../../.specify/memory/constitution.md), [harness](../../docs/harness.md), artefatos das features 001–003 e código existente foram consultados. Os planos antigos descrevem seus momentos históricos; o código atual prevalece para determinar dependências presentes.
- [Product](../../src/main/java/com/devsuperior/dscommerce/entities/Product.java) já usa BigDecimal, coluna de precisão 19 e escala 2, e `@PositiveOrZero`. Catálogo e detalhes retornam preços como números JSON. Não será alterado.
- [UserAccount](../../src/main/java/com/devsuperior/dscommerce/entities/UserAccount.java) contém id, name e passwordHash; ainda não tem perfis. Não há Order/OrderItem ou operação de pedidos.
- [JwtConfig](../../src/main/java/com/devsuperior/dscommerce/config/JwtConfig.java) fornece encoder/decoder HS256 com a mesma chave externa. [JwtTokenService](../../src/main/java/com/devsuperior/dscommerce/services/JwtTokenService.java) emite sub como id textual, name, iat e exp; não há claims de perfis, iss ou aud.
- [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) é stateless, sem CSRF, permite todas as requisições e usa SAMEORIGIN. Emitir JWT ainda não protege rotas.
- H2 é o banco local/de testes; o perfil test cria e remove o schema e usa import.sql. Os testes públicos dependem de 52 produtos. Slices MVC importam SecurityConfig; testes JPA usam transação de teste. Esses detalhes orientam isolamento e regressões.

## D1 — JWT e proteção restrita à nova operação

**Decision**: Adicionar o starter OAuth2 Resource Server gerenciado pelo BOM atual, reutilizando JOSE e o JwtDecoder existente. Uma SecurityFilterChain prioritária deve corresponder apenas a `POST /orders`; a chain restante conserva a política existente, inclusive login, catálogo, detalhes e H2 console. Nenhum servidor de autorização, issuer remoto ou novo token.

Na chain de pedidos, um converter/adaptador recebe o JWT já verificado criptograficamente. Exige exp e iat utilizáveis, exp > agora e exp > iat; rejeita iat futuro e nbf futuro, quando presente, sem tolerância temporal adicional. Usar Clock UTC injetável. O decoder existente admite tolerância temporal padrão; por isso a verificação adicional da operação é necessária para não aceitar um token já expirado. Essa restrição não muda o decoder compartilhado nem os testes que o usam isoladamente.

O sub deve ser uma string decimal canônica positiva (`[1-9][0-9]*`) representável como Long. Um serviço read-only resolve a conta e seus perfis por id no UserAccountRepository, com fetch explícito dos perfis. O principal transitório carrega somente accountId; não contém passwordHash e não cria outra identidade persistida. Ignorar name e possíveis roles enviados no JWT para titularidade/permissão. Conta ausente ou sub inválido resulta em 401; conta existente sem perfil permitido resulta em 403. Nenhum lookup é feito antes da validação criptográfica/temporal.

**Rationale**: Reaproveita o subject já emitido pelo login e limita a alteração de segurança ao recurso novo. A [documentação Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) descreve a verificação JWT e a conversão de autenticação. O [validador temporal 6.3.3](https://github.com/spring-projects/spring-security/blob/6.3.3/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/JwtTimestampValidator.java) fundamenta a cautela sobre tolerância e claims ausentes; não se presume que o default satisfaça sozinho FR-010.

**Alternatives considered**: Filtro JWT próprio duplica primitivas disponíveis; proteção global com bearer pode rejeitar cabeçalhos antes irrelevantes em rotas públicas; autenticar pelo name cria dependência em nome mutável; exigir iss/aud novos quebra o token existente. Todos rejeitados.

## D2 — Perfis na conta existente

**Decision**: Acrescentar `Set<UserRole>` à UserAccount como `@ElementCollection` de enum STRING `CLIENT`/`ADMIN`, representando Cliente/Admin, na tabela `tb_user_account_role`. Chave única `(user_account_id, role)`; conjunto vazio é permitido e não autoriza pedidos. Sem atribuição automática de Cliente a contas existentes. Authorities transitórias `ROLE_CLIENT` e `ROLE_ADMIN` vêm do banco; OR entre os perfis, inclusive Admin sem Cliente. Não incluir perfis no contrato de login ou de pedidos.

Manter associação lazy e consulta específica com perfis na resolução autenticada, para não ampliar automaticamente o lookup por name usado pelo login. O serviço de pedidos revalida existência e perfil da mesma accountId dentro de sua transação; o controller não contém regra de autorização. A verificação HTTP pertence à segurança e a guarda do serviço protege chamadas diretas e alterações de conta entre resolução e gravação.

**Rationale**: A própria UserAccount já é a identidade da compra. Enum/coleção bastam para dois perfis sem gestão administrativa. Contas sem perfil continuam podendo fazer login.

**Alternatives considered**: Nova entidade User/Customer paralela, Role com CRUD e permissões configuráveis, perfis no token e default privilegiado para todos foram rejeitados por duplicação ou aumento de escopo. Provisionamento real de perfis não é entregue aqui; fixtures de teste serão explícitas.

## D3 — Contrato e validação de entrada

**Decision**: `POST /orders` recebe somente `{items: [{productId, quantity}]}` e retorna 201 com OrderResponseDTO. DTOs de entrada/saída distintos. Sem Location que sugira uma consulta não entregue. A resposta identifica o titular por accountId, o pedido por id, instante UTC por createdAt, estado `WAITING_PAYMENT` e itens na ordem enviada.

Desserialização estrita restrita aos DTOs de pedido, seguindo o precedente local do login: objetos/lista corretos, sem campos extras em qualquer nível, inclusive titular, valores calculados e campos extras nulos. productId/quantity aceitam apenas tokens JSON inteiros dentro dos limites; string, boolean, decimal (`1.0` inclusive), array e objeto não são convertidos. Propriedades JSON repetidas devem ser rejeitadas neste leitor, antes de sua perda em uma árvore JSON. Não alterar coerções globais ou o leitor do login.

Bean Validation no controller: lista `@NotEmpty`, elementos `@NotNull @Valid`, productId `@NotNull @Positive`, quantity `@NotNull @Positive @Max(2147483647)`. Validação estrutural antecede serviço; unicidade por productId, existência de produtos e preço são regras do serviço. Resolver produtos com findAllById e mapear por id; não carregar catálogo nem categorias. Mesmo produto em duas linhas rejeita sem consolidar.

**Rationale**: O consumidor controla só produtos/quantidades; dados de titularidade e preço pertencem ao sistema. [Contrato](contracts/orders-openapi.yaml) define os limites e o formato novo sem alterar contratos públicos.

**Alternatives considered**: Reutilizar entidade JPA como payload, aceitar titular/preço do cliente, consolidar duplicatas ou normalizar frações violam os requisitos. Desabilitar coerção global arrisca regressões desnecessárias.

## D4 — Precisão e limites representacionais

**Decision**: productId/accountId/id são Long positivos até `9223372036854775807`; quantity é Integer de `1` a `2147483647`. É um limite de representação, não estoque ou limite comercial. Não acrescentar um teto arbitrário de linhas do carrinho.

Preço registrado, subtotal e total usam BigDecimal exato, capacidade decimal de 19 dígitos e 2 casas: `0.01` a `99999999999999999.99`. Preço obtido no registro é normalizado com `setScale(2, UNNECESSARY)`; multiplicação pela quantidade e soma são exatas, sem MathContext que arredonde. Valores com casas não nulas além de duas, overflow do subtotal ou da soma são rejeitados integralmente com 422 antes da gravação. Valores como `1.000` podem normalizar para `1.00` sem perda. Não usar float/double. Zero, negativo e null não são preços válidos no pedido; null é tratado como preço indisponível, também 422.

Saída monetária deste contrato usa strings decimais com exatamente duas casas, sem separador de milhares, para que o consumidor não perca valores de 19 dígitos ao interpretar números em IEEE754. Catálogo/detalhes continuam com seu formato numérico atual. Subtotal = snapshot × quantity; total = soma dos subtotais, sem frete, descontos ou acréscimos. Não introduzir moeda/conversão cambial.

**Rationale**: Compatível com a capacidade atual de Product sem arredondamento silencioso. [BigDecimal Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html) oferece operações decimais e falha quando UNNECESSARY requer arredondamento.

**Alternatives considered**: double, arredondamento HALF_UP e aceitar overflow no banco foram rejeitados por perda; JSON numérico seria possível para clientes decimais, mas torna o limite máximo menos portátil; ampliar escala mudaria a referência monetária existente.

## D5 — Agregado e conservação histórica

**Decision**: Order possui id gerado IDENTITY, buyer UserAccount, createdAt Instant e status enum STRING `WAITING_PAYMENT`. OrderItem possui id próprio, order, product, position, quantity e unitPrice histórico `DECIMAL(19,2)`. Constraint única `(order_id, product_id)` reforça A1; posição única por pedido preserva a ordem. Relações a conta/produto não propagam operações de escrita. Order propaga PERSIST aos itens; não há operação de atualização/remoção de pedidos nesta feature.

Persistir preço e quantidade; subtotal e total são derivados desses dados históricos, calculados e verificados antes do save, sem colunas redundantes. Não recalcular a partir de Product.price. O instante é obtido por Clock UTC e truncado a milissegundos para consistência entre resposta e round-trip de H2. Não há transição após WAITING_PAYMENT.

**Rationale**: Preço/quantidade históricos bastam para conservar subtotais/total sem duplicar fontes de verdade. [Modelo](data-model.md) detalha relacionamentos e invariantes.

**Alternatives considered**: Preço apenas por referência ao produto viola FR-009; tabelas de carrinho, pagamento e histórico consultável estão fora do escopo; PK composta é possível, mas id próprio simplifica mapeamento e mantém a unicidade do negócio em constraint separada.

## D6 — Fronteira transacional e momento do preço

**Decision**: Método público de OrderService chamado pelo proxy Spring, `@Transactional(rollbackFor = Exception.class)` com REQUIRED. Revalidar conta/perfis, rejeitar repetidos, buscar produtos e copiar os preços vigentes lidos nessa transação, validar cálculos, construir agregado, saveAndFlush e mapear resposta dentro da fronteira. Commit pelo interceptor ocorre antes de a chamada retornar ao controller, que só então devolve 201. Toda exceção deve escapar; não converter falha em sucesso nem abrir REQUIRES_NEW para itens. Erros de commit também devem chegar ao tratamento centralizado.

O preço de referência é o observado na leitura transacional do produto, sem congelamento anterior no carrinho e sem segunda confirmação. Uma alteração concorrente após a leitura não substitui o snapshot já escolhido. Não se promete que o preço será o último valor no instante físico do commit, nem se acrescentam locks ou versionamento de catálogo nesta feature. Testar alteração antes da leitura e depois da compra.

**Rationale**: Uma fronteira para agregado inteiro atende FR-012. A [API Transactional 6.1.13](https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/transaction/annotation/Transactional.html) documenta REQUIRED e regras de rollback; rollbackFor explícito cobre checked exceptions, sem depender de funcionalidades posteriores do framework.

**Alternatives considered**: Gravar item a item com transações independentes deixa compra parcial; self-invocation contorna proxy; capturar exceção e retornar DTO compromete rollback. Locks de preço e idempotência não são justificados por esta fronteira funcional.

Resposta perdida depois do commit não desfaz a compra. Cada novo envio válido cria outro agregado e outro id; não há chave de idempotência, deduplicação entre envios ou exigência de ids contíguos.

## D7 — Erros em dois pontos do fluxo

**Decision**: Reutilizar ErrorResponseDTO(status, error, message, path). AuthenticationEntryPoint produz 401 e AccessDeniedHandler produz 403 na chain de pedidos, com challenge Bearer adequado e sem detalhes do token. Diferenciar indisponibilidade/falha de lookup da conta de credencial inválida: adaptador de segurança converte falha interna conhecida em 500 genérico; não depender de ControllerAdvice para exceção ocorrida em filtro.

No MVC, ampliar GlobalExceptionHandler somente para os caminhos necessários ao pedido: 400 entrada ilegível/inválida, 404 ProductNotFoundException existente, 422 InvalidOrderException (duplicata, preço inválido ou valor não representável), 500 falha interna/gravação/commit. Guarda de conta/perfil no serviço lança exceções de autenticação/acesso; em MVC, mapear também para o mesmo 401/403 caso ocorram depois do filtro. Login conserva suas mensagens próprias e context-path handling; não mudar genericamente os erros de catálogo/detalhes.

**Rationale**: Falhas antes do controller precisam resposta consistente tanto quanto as de negócio. [README do contrato](contracts/README.md) fixa precedência, mensagens e responsabilidades.

**Alternatives considered**: Todos os erros como 400/401 escondem diferenças relevantes; try/catch em cada controller viola centralização; somente ControllerAdvice não cobre bearer inválido na segurança.

## D8 — Evidência de segurança e rollback

**Decision**: Planejar testes unitários, MVC, JPA e full-context. Sucesso integrado obtém JWT de POST /login e o usa em POST /orders com decoder, resolver e filtros reais. Casos temporais/assinatura/subject usam JWTs realmente assinados, adulterados ou malformados, nunca `jwt()`/`@WithMockUser` como prova de criptografia. Perfis de contas são fixtures explícitas; login de conta sem perfil continua válido e pedido retorna 403.

Testes de atomicidade sem `@Transactional` na classe/método de teste. Preparar fixtures em transação separada confirmada, chamar serviço proxy ou MockMvc com filtros ativos, consultar JDBC/nova transação após a chamada, limpar explicitamente e isolar contexto/banco das regressões. Injetar falha SQL tardia no segundo item com constraint temporária de teste, observar que pedido e primeiro item chegaram ao banco, esperar rollback e contagens originais. Complementar com spy de teste que delega um saveAndFlush real e lança RuntimeException após confirmar linhas na mesma transação. Repositórios permanecem reais; nenhum ponto de falha entra no código de produção.

Sucesso deve deixar linhas visíveis em conexão independente; isso evita um teste que só passa por nada ter sido salvo. [Transações de teste Spring](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html) explicam por que rollback gerido pelo teste não demonstra rollback do serviço. Rodar regressões após mudanças de segurança/Jackson/handlers e suíte completa no fechamento da implementação, sempre pelo Wrapper.

Complemento da revisão documental de 2026-10-02: distinguir a falha de processamento após saveAndFlush da falha na conclusão transacional. Para esta última, instrumentação exclusiva de teste registra TransactionSynchronization na transação ativa, deixa o corpo do serviço concluir e lança RuntimeException em beforeCommit, antes do commit físico pelo interceptor. Observar inserts reais, callback alcançado, propagação até MVC/500 sem 201 e baseline preservado em conexão independente. A [API de sincronização Spring](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#beforeCommit(boolean)) documenta propagação e rollback nesse callback; a página consultada é da versão corrente, sem adoção de APIs novas no projeto. Não lançar em afterCommit nem apresentar esse teste como falha física de conexão durante commit. O procedimento detalhado está no [quickstart](quickstart.md); nenhum cenário foi executado nesta revisão.

**Alternatives considered**: Mock de save só demonstra orquestração; teste JPA com rollback automático pode esconder commit indevido; falha antes de qualquer SQL não prova desfazimento; fixture alterando Product pelo ORM pode impedir o preço negativo antes de exercitar a regra do pedido. Para negativo, usar fixture JDBC isolada, preservando as validações de Product.

## Conclusão da pesquisa

Incertezas técnicas resolvidas pelo desenho: mecanismo de segurança, vínculo de conta, perfis, DTOs estritos, tipos e limites, persistência histórica, transação, erros e estratégia de testes. Nenhum marcador de clarificação aberto. Os limites e a representação por strings são decisões técnicas revisáveis, sem alterar A1/Q1 ou reabrir A2/A3. A distribuição de perfis de contas reais e banco de produção permanece fora da entrega; não há implementação, tasks, commit ou deploy nesta fase.
