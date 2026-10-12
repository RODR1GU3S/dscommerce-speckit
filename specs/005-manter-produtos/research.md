# Research: Manter produtos

**Data**: 2026-10-11 | **Base**: `fba63732ddb2f27dc03da350d5684c05f3517b75` | [Plano](plan.md) | [Spec](spec.md)

Pesquisa por leitura do código, contratos, constituição e documentação primária. Os agentes de pesquisa previstos na skill examinaram segurança/erros e persistência/busca, sem editar ou testar. Não houve consulta ou ação no GitHub do projeto. Decisões abaixo são de desenho; requisitos do PDF e decisões de negócio continuam na spec. Não há `NEEDS CLARIFICATION` pendente.

## R1 — Rotas, perfis e isolamento de segurança

**Decision**: seis operações em `/admin/products`, `/admin/products/{id}` e `/admin/categories`, cadeia `/admin/**` com ADMIN para qualquer método, antes do fallback público. Reutilizar decoder, converter JWT, principal mínimo e serviço de identidade existentes; entry point/denied handler administrativos próprios. Revalidar conta/perfil nos serviços antes de acessar dados administrativos.

**Rationale**: `SecurityConfig.java:38–82` protege apenas POST de pedidos e deixa fallback sem processamento JWT. Converter `OrderJwtAuthenticationConverter.java:29–74` valida sub/tempo e usa perfis do banco por `AuthenticatedAccountService.java:28–45`; conta ausente e falha interna são distinguíveis. `OrderService.java:50–59` já aplica guarda tardia. ADMIN isolado satisfaz FR-001; CLIENT não deve autorizar. Proteger todos os métodos evita fuga por verbo desconhecido.

**Alternatives considered**: proteger `/products` alteraria GET público; resource server global mudaria token inválido irrelevante; confiar em roles no JWT permitiria perfil obsoleto/forjado; copiar mensagens de pedidos confundiria contratos. Não renomear classes existentes apenas por seu nome histórico.

Fonte: [Spring Security — cadeias e matchers](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html). A prioridade e o matcher determinam a cadeia aplicável, portanto a mudança deve ser isolada e testada com filtros reais.

## R2 — Busca literal e paginação

**Decision**: normalizar extremidades do filtro; ausência/vazio não restringe. Consulta JPQL administrativa parametrizada com `locate(lower(:name), lower(p.name)) > 0`, sem normalização de acentos ou espaços internos. Ordenar `name ASC,id ASC`; page padrão 0, size padrão 12, intervalo 1–50. Count e seleção usam a mesma restrição; sem fetch join de categorias na consulta paginada.

**Rationale**: `ProductCatalogService.java:30–37` estabelece o desempate e `ProductController.java:34–41` os limites. LOCATE evita transformar `%`, `_`, barras e apóstrofos em curingas/instruções. LOWER ocorre nos dois operandos no mesmo banco, sem misturar algoritmos Java/banco. H2 existente usa texto comum, não VARCHAR_IGNORECASE; não mudar collation global.

**Alternatives considered**: LIKE com escapes é possível, mas adiciona regras desnecessárias; regex/full text/remoção de acentos violam o trecho literal aprovado; filtrar tudo em memória prejudica paginação e contagem. Não prometer casefold linguístico, equivalência Unicode canônica ou transliteração: a regra técnica é LOWER do H2 sobre o texto armazenado, sem retirar acentos.

Fontes: [H2 LOCATE](https://h2database.com/html/functions.html#locate), [H2 LOWER](https://h2database.com/html/functions.html#lower). Verificação futura em H2 real inclui café/Cafe, caixa, especiais, espaços, empates e count.

## R3 — Categorias e legado

**Decision**: novo repository/serviço de consulta para todas as categorias ID/nome, em ordem name/id, sem filtro próprio nem escrita. Detalhe administrativo conserva cada ID, inclusive nomes iguais. IDs repetidos viram conjunto de associações únicas; todos os IDs distintos devem existir. Consultar em lote e comparar conjuntos; manter índices originais apenas para diagnóstico de campos.

**Rationale**: `Product.categories` é Set sem cascade (`Product.java:46–52`), `Category.equals` compara ID (`Category.java:41–54`). Não há consulta de categorias reutilizável. `ProductDetailDTO.java:29–33` perde IDs e deduplica nomes, adequado apenas ao contrato público. FR-011 agora contém decisão explícita da sessão, não suposição ou regra do PDF. Produto antigo com categoria vazia/preço zero deve continuar legível.

**Alternatives considered**: CRUD de categoria, autocomplete/filtro próprio ou hierarquia ampliariam escopo; buscar categoria por nome fundiria IDs; `getReferenceById` não comprova existência; Set Java sozinho não comprova unicidade no banco. A ausência de categorias retorna lista vazia e não autoriza escrita sem seleção.

## R4 — Leitura estrutural e diagnóstico completo

**Decision**: leitor streaming registrado somente para DTO administrativo, guardando defeitos examináveis sem interromper avaliação dos outros campos. Restrição Bean Validation de classe agrega defeitos e resultado de serviço de diagnóstico somente leitura, inclusive referências de categorias. Sem fail-fast, sem sequência de grupos que pule lookup. Serviço de escrita reavalia regras/referências em sua transação antes de mutar.

**Rationale**: `GlobalExceptionHandler.java:43–67` não informa campos; leitor de pedidos `OrderRequestJsonConfig.java:18–113` interrompe no primeiro defeito. Copiá-lo e usar @Valid usual antes do serviço esconderia categoria inexistente quando outro campo falha (US2.6). Validator adapta diagnósticos em violations, sem acessar repository diretamente ou transferir decisões de negócio para controller. Falha de diagnóstico DB é 500, não omissão silenciosa de erros.

**Alternatives considered**: validar só no serviço de escrita viola a fronteira constitucional; validar apenas cada campo e só depois categorias viola FR-012; alterar Jackson global afetaria login/pedidos; ecoar corpo/SQL nos erros exporia dados. JSON truncado/raiz errada não oferece objeto completo examinável; nesses casos informar apenas o defeito de corpo conhecido.

**Correção constitucional de 2026-10-12**: o princípio IV exige validação antes da lógica de serviço, não apenas antes da escrita. Portanto, o diagnóstico acima não fundamenta PASS estrito. Mantém-se exceção explícita à ordem de execução para FR-012/US2.6, documentada no Constitution Check do [plano](plan.md): somente normalização/regras, guarda atual e leitura de referências, sem mutação/DML ou entrada na manutenção. Preservar as decisões nos serviços atende princípio II/Backend Constraints; interromper a avaliação antes de todo serviço esconderia a categoria inexistente junto com outros campos inválidos. Consequências: leitura mesmo com defeitos locais, custo de banco, falhas de guarda/lookup e necessidade de revalidação transacional. V01/V02/V06 comprovarão agregação, segurança e ausência de escrita; revisar o alcance e registrar evidências antes de considerar a implementação completa, conforme Governance. A constituição permanece intacta; esta justificativa documental não é resultado funcional.

**Decision complementar**: normalização retorna valor interno imutável, sem modificar o DTO em isValid. RuntimeException lançada no validator pode ser envolvida em ValidationException; handler central ADMIN reconhece causas conhecidas da guarda do serviço de diagnóstico para preservar 401/403, e mantém resolução interna/erro inesperado em 500. Não aplicar esse tratamento aos contratos anteriores. Fonte: [Jakarta Bean Validation 3.0 — validação e exceções](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html). V01 deve cobrir a guarda dentro do boundary, não só a escrita.

Fontes: [Spring MVC — validação](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html), [Hibernate Validator 8 — restrições e fail-fast](https://docs.hibernate.org/validator/8.0/reference/en-US/html_single/#section-fail-fast). Parametrizar teste para MethodArgumentNotValid/validação de parâmetros e ordenar diagnósticos sem depender da ordem interna do provider.

## R5 — Textos, URL e dinheiro exato

**Decision**: remoção externa de pontos de código reconhecidos por `Character.isWhitespace` ou `Character.isSpaceChar`, mantendo internos; contagem de nome/descrição por pontos de código Unicode, sem NFC/compactação. Validar nome 3–80 e descrição ≥10 depois da remoção; armazenar essa forma. Imagem não é normalizada: string URI absoluta hierárquica, http/https case-insensitive e autoridade de servidor/host sintaticamente válida via `URI`/`parseServerAuthority`, sem rede; permitir componentes opcionais válidos, inclusive query/fragmento, sem lista de domínios.

**Rationale**: concretiza remoção de espaços e URL sintática já aprovadas; não acrescenta política de conteúdo/disponibilidade. Manter descrição/imagem em texto grande/CLOB, pois `Product.java:29–39` deixa tamanho padrão de coluna e a spec não lhes impõe máximo comercial. Não reduzir coluna de nome nem impor novas constraints globais ao legado. Isso é adequação futura de armazenamento, não correção de dados antigos.

**Decision monetária**: entrada administrativa price é string decimal de base 10, sem notação científica, vírgula, espaços ou coerção numérica. Parse direto em BigDecimal; valor >0, `setScale(2, UNNECESSARY)`, precisão normalizada ≤19. Zeros decimais adicionais são aceitos se não alterarem valor; resposta usa duas casas e `toPlainString`. Máximo 99999999999999999.99, mínimo 0.01. Leitura administrativa admite zero legado; públicas continuam número JSON conforme contratos anteriores.

**Alternatives considered**: double/arredondamento perderiam valor; @Digits aplicado ao bruto poderia rejeitar 1.000 equivalente a 1.00; alterar PositiveOrZero da entidade afetaria legado; limitar descrição/URL a 255 inventaria regra comercial; validar imagem por HTTP/DNS violaria escopo. Limites de memória/parser permanecem operacionais, não garantia de entrada ilimitada nem novo máximo de negócio.

Fontes: [Java 21 BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html), [Java 21 URI](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html), [Java Character](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html), [H2 CLOB](https://h2database.com/html/datatypes.html#character_large_object_type). DTO/contrato descrevem regras pós-normalização; não usar maxLength bruto que rejeite nome válido cercado de espaços.

## R6 — Substituição integral e persistência

**Decision**: serviço transacional REQUIRED/rollback de falhas; alterar Product gerenciado após todas as validações, por operação de substituição dos cinco dados, conservando ID. Categorias são entidades existentes; substituir somente associações. Produto com lock PESSIMISTIC_WRITE em atualização/exclusão; salvar/flush e mapear DTO na transação; controller recebe resultado após interceptor concluir commit.

**Rationale**: Product atual só expõe getters/construtores; precisa de substituição deliberada, não update parcial/upsert. Não há cascata REMOVE/ALL para categorias, e ela não será introduzida. Falha SQL/processamento/commit não pode produzir sucesso ou estado parcial; após falha não continuar a transação. Unicidade do par produto/categoria e FKs devem ser verificadas no DDL real.

**Alternatives considered**: salvar cada campo separadamente quebra atomicidade; merge de entidade reconstruída com ID pode gerar upsert/associações incorretas; nova infraestrutura de versionamento não é necessária para requisitos atuais. Lock entre Admins não garante detectar editor que leu estado antigo, nem serializar compra existente sem lock.

Fontes: [Hibernate 6.5 — many-to-many](https://docs.hibernate.org/orm/6.5/userguide/html_single/#associations-many-to-many), [Spring — transações declarativas](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html).

## R7 — Exclusão, integridade e concorrência

**Decision**: precheck de qualquer OrderItem e FK restritiva sem cascata para produto. Nomear FKs relevantes e classificar apenas violação referencial confirmada da exclusão como 409; outras falhas internas, deadlock/timeout e resultados indeterminados são 500 seguro. Eventual referência de categoria perdida entre diagnóstico e gravação somente vira 400 de categoryIds se a FK de referência ausente for inequivocamente identificada; não tratar toda DataIntegrityViolationException como erro de campo. Falha aborta a transação inteira.

**Rationale**: `OrderItem.java:49–52` exige produto e `OrderService.java:68–91` lê sem lock. Se compra estabelecer referência antes de delete, FK impede delete; se delete prevalecer, FK impede item órfão. O precheck não elimina TOCTOU. Não alterar contrato/serviço de pedidos para impor resultado novo a essa disputa. Outras FKs reais que restrinjam a exclusão também devem ser respeitadas, sem apagar dados relacionados.

**Alternatives considered**: cascade delete destruiria histórico; soft delete mudaria comportamento de catálogo sem necessidade; precheck isolado permite corrida; mapear qualquer SQLState de integridade como 409 encobriria defeitos internos. H2 distingue erro de apagar pai referenciado (23503) de referência ausente (23506); confirmar operação, FK/metadata e causa real, não texto bruto arbitrário.

Fontes: [H2 referential constraint](https://h2database.com/html/grammar.html#referential_constraint), [H2 locks/timeouts](https://h2database.com/html/features.html#locking_lock_timeout_deadlocks), [H2 23503 — filho existente](https://h2database.com/javadoc/org/h2/api/ErrorCode.html#REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1), [H2 23506 — pai ausente](https://h2database.com/javadoc/org/h2/api/ErrorCode.html#REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1). NO ACTION padrão tem proteção imediata equivalente a RESTRICT nesse banco. Testar ambos os interleavings por conexões distintas, sem concluir sucesso apenas pela ausência de erro Java.

## R8 — HTTP e falhas seguras

**Decision**: sucesso GET/PUT 200, POST 201 com Location administrativo existente, DELETE 204. Invalidade examinável/referências de categoria 400; autenticação 401 com Bearer challenge; autorização 403; produto inexistente 404; exclusão referencial 409; falha interna 500. Envelope administrativo base quatro campos mais errors, sem ampliar DTO público. Precedência e códigos no [README](contracts/README.md).

**Rationale**: spec não fixou formatos/status; agora são decisões técnicas. Diagnóstico completo antes do lookup do alvo resolve mistura de campos inválidos+ID inexistente sem ocultar campos. Advice existente precisa de adaptação administrativa central, mantendo branches anteriores; filtro tem adaptadores próprios. Erros MVC 4xx devem conservar status/cabeçalhos, como a correção histórica do 415 em pedidos já demonstra.

**Alternatives considered**: usar 422 para todos os dados possíveis criaria duas categorias desnecessárias de invalidade; ampliar ErrorResponseDTO mudaria shape de contratos existentes; capturar tudo como 500 esconderia 405/415; revelar exception.getMessage vaza SQL/dados. Nenhuma exceção desconhecida é automaticamente atribuída ao consumidor.

Fonte: [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3.html). Schemas exprimem estrutura; regras de normalização, categoria existente e commit precisam de prosa e provas funcionais futuras.

## R9 — Evidência independente e limites das simulações

**Decision**: separar unitário, contrato MVC, integração full-context, repository/SQL direto, rollback e corrida. Integração HTTP/proxy sem transação externa do teste, snapshots por DataSource/conexão diferente antes da limpeza; verificar produtos/categorias/vínculos/pedidos/itens e valores. Reutilizar estratégia de isolamento de fixtures da Feature 004, sem ativar sua demonstração automaticamente.

**Rationale**: `OrderRollbackIntegrationTest.java:95–103` exige proxy/transação independente, e `OrderRepositoryTest.java:174–183` ultrapassa validação Java com SQL inválido direto. Precheck 400 não comprova rollback após DML; flush não é commit; beforeCommit não é falha física no commit JDBC. Para injetar erro em Connection.commit, instrumentar somente teste e lançar antes de delegar ao driver: prova caminho de erro/rollback dessa simulação, não queda de energia ou falha depois de commit real.

**Alternatives considered**: mocks/contagens dentro da mesma transação podem ocultar estado real; afterCommit não permite desfazer compra; retorno 201 isolado não comprova visibilidade durável. H2 em memória não comprova durabilidade pós-processo. [Spring TransactionSynchronization](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html) distingue callbacks da conclusão real.

## Evidência histórica e conclusão da pesquisa

[Quickstart da Feature 004](../004-registrar-pedido/quickstart.md), seção da correção HTTP de 2026-10-11: suíte completa com 926 testes, 0 falhas/erros/ignorados, Wrapper no Windows. O pedido atual também referencia a CI histórica #6; não consultada remotamente nem usada para aprovar este desenho. O HEAD inicial contém a integração das Features 001–004 e o commit documental 005; não alegar que os 926 testes foram executados nesse commit.

Pesquisa resolve escolhas técnicas necessárias à fase 1. Permanecem procedimentos futuros de implementação/validação, não clarificações pendentes; assunções da spec e limites de ambiente são mantidos. Nenhum teste de aplicação, stage, commit, fetch, push ou implantação foi realizado.
