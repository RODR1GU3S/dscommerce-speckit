# Guia de validação — Feature 007

**Estado em 2026-10-19**: planejamento sobre `d69ef4690e2c684c540b12a7f304978671c3ba7a`, branch `007-consultar-meus-pedidos`. As consultas ainda não foram implementadas. Este guia separa a validação documental atual dos procedimentos futuros. Nenhum comando Maven, teste de aplicação ou requisição de aceitação foi executado neste checkpoint.

Fontes: [spec](spec.md), [checklist aprovado](checklists/requirements.md), [plano](plan.md), [pesquisa](research.md), [modelo](data-model.md) e [OpenAPI](contracts/own-orders-openapi.yaml). O checklist 16/16 avalia requisitos; não substitui provas de comportamento.

## Preparação futura

1. Java 21 e Maven Wrapper existente (Maven 3.9.16), na raiz do projeto. Seguir [AGENTS](../../AGENTS.md), [harness](../../docs/harness.md) e constituição. Registrar branch real, HEAD, diff e estado do Git antes de qualquer implementação autorizada.
2. Preparar testes novos com prefixo `OwnOrderQuery`, sem mudar as asserções das Features 001–006. Esses testes não existem nesta fase; seus nomes são convenção planejada para seleção futura.
3. Criar contextos completos de teste explicitamente distintos: raiz e `server.servlet.context-path=/api`, ambos com OSIV false e filtros/decoder/JWT reais; testes de componentes usam contexto/banco próprios. Não envolver HTTP em transação do chamador. URLs e segredos de teste não são configuração de produção.
4. Reutilizar explicitamente `AdminProductTestConfig`, fixture de contas/produtos e probe existentes. O inicializador gera URL H2 com UUID uma vez por ApplicationContext e chave de teste, com create-drop e inicializações SQL/import desabilitadas somente no contexto isolado. Diferenciar propriedades de identidade dos contextos raiz/API para não reutilizar acidentalmente a mesma instância. A fixture usa MockMvc internamente; seus helpers não substituem as novas requisições HTTP reais pela porta aleatória.
5. Suporte 007 prepara pedidos por POST real nos testes de jornada, ou por setup persistido/commitado explícito quando necessário para controlar instantes/empates e dados legados. Esperados vêm de manifesto independente de IDs, posições, quantidades e preços; não calcular o esperado chamando o mesmo mapper/getTotal do código testado. Limpar somente itens/pedidos criados pelo teste antes de limpar produtos/contas da fixture; não remover dados alheios nem seeds globais.
6. Para leitura sem mutação, concluir setup/login, fotografar todas as tabelas afetadas por JDBC independente e iniciar a janela de observação SQL. Após GET/HEAD, comparar produtos/categorias/associações/contas completas/perfis/pedidos/itens e ausência de DML Hibernate. Não imprimir senhas, hashes, JWT ou chaves. Limpeza fica fora dessa janela. O probe atual não cobre sozinho nome/hash de conta; complementar essas colunas. StatementInspector não observa JDBC direto e readOnly não é prova suficiente sozinho.

## Matriz integral dos cenários da especificação — execução futura

Cada linha corresponde a um cenário de aceitação; variantes de perfil/contexto são invocações desse cenário, não novos requisitos. Todos os 26 devem aparecer em testes concretos e na conferência dos relatórios.

| Cenário | Preparação/ação e resultado esperado | FR | SC |
| --- | --- | --- | --- |
| US1.1 | Três próprios/dois alheios; página comporta três: somente próprios, quatro campos por resumo, totalElements 3. | 002–003,009 | 001–002 |
| US1.2 | Cinco próprios e terceiros intercalados; size2, páginas0/1/2 =>2/2/1, mesmos totais5/3, união de IDs exata sem repetição. | 009–010 | 001–002 |
| US1.3 | Instantes diferentes e empate dividido entre páginas; ordem exata createdAt DESC/id DESC. | 010 | 002 |
| US1.4 | Titular sem compras e terceiros com compras; primeira e página posterior válida =>content vazio, totais0/0, page/size conservados. | 009,011 | 001,005 |
| US1.5 | Cinco próprios, size2, page3 =>vazio, totais5/3, sem clamp. | 012 | 005 |
| US1.6 | Autorizado; page negativa, size0/negativa e não inteiros =>400 fixo, nenhum dado/escrita. | 013,015 | 005 |
| US1.7 | Duas contas sucessivas na mesma aplicação; cada conteúdo/count corresponde ao respectivo principal, sem estado compartilhado. | 002,009 | 001 |
| US2.1 | Itens B/A, quantidades2/3, preços10.00/7.50 =>subtotais20.00/22.50 e total42.50; cabeçalho quatro campos mais items; item cinco campos; estado armazenado. | 004,006–007,018 | 003–004 |
| US2.2 | Detalhe próprio que não pertence à página consultada =>200 independente da lista anterior. | 004 | 003 |
| US2.3 | Compra qty2 a10.00; produto muda nome/preço para12.00 =>nome atual, unitPrice10.00, subtotal/total20.00 em lista e detalhe. | 005–007 | 003–004 |
| US2.4 | Produtos diferentes com mesmo nome atual =>itens distintos, posições originais, sem fusão/reordenação. | 004–005 | 003 |
| US2.5 | Compra válida; produto atual com preço0, nome legado curto/longo e sem categorias =>leitura200, nome integral, finanças preservadas, sem autocorreção. | 005–007 | 003–004 |
| US2.6 | Compras com0.01 e9007199254740993.01 =>strings exatas com duas casas, sem expoente/float/arredondamento em lista/detalhe. | 008 | 004 |
| US2.7 | Mesmo produto em dois pedidos com preços históricos distintos =>cada compra mantém quantidades/preços/subtotais/total próprios. | 006–007 | 004 |
| US3.1 | CLIENT, ADMIN isolado e ambos; lista/detalhe próprios200, sem exigir combinação. | 001–002 | 001 |
| US3.2 | Sem credencial, inválida/expirada ou conta removida =>401, Bearer challenge, sem campos sensíveis ou dados de pedidos. | 001,015 | 001,005 |
| US3.3 | Conta existente sem ambos os perfis =>403 fixo, nenhum dado de pedidos. | 001,015 | 001 |
| US3.4 | Autorizado, ID alheio e inexistente válidos =>404, mesmo código/mensagem/campos, sem lookup global ou dados do dono. | 002,014–015 | 001,005 |
| US3.5 | ADMIN isolado consulta pedido de outro =>mesmo404; listas/totais também permanecem próprios. | 002,014 | 001 |
| US3.6 | Sem credencial/perfil, paginação/ID inválidos =>401/403 antes de binding, sem informação restrita ou confirmação de existência. | 001,015 | 001 |
| US3.7 | Falhas controladas em identificação, count, fetch, cálculo/mapeamento/conclusão transacional =>500 fixo, nunca vazio,404,401 ou sucesso parcial. | 015 | 001,005 |
| US4.1 | Snapshots antes/depois de leituras válidas, vazias/rejeitadas =>tabelas idênticas e zero DML na janela, inclusive status e posição. | 016,018 | 006 |
| US4.2 | Regressões reais001/002/006: público sem/invalidBearer, filtro por trecho/case/acentos/%/_, espaços externos/internos, ordenação, páginas, detalhe/categorias/erros e preços numéricos. | 008,017 | 006 |
| US4.3 | Login003 válido/inválido/incompleto =>contrato e confidencialidade mantidos, sem alterar credenciais. | 017 | 006 |
| US4.4 | GET repetido não cria compra; POST válido posterior com mesmos itens cria novo ID, preservando004, sem modificar carrinho. | 016–017 | 006 |
| US4.5 | Jornadas/rejeições004/005 mantêm auth, validação, atomicidade, FK de produto comprado, rollback e histórico financeiro. | 017 | 006 |

## Casos técnicos adicionais do contrato — futuros

- Omissão =>page0/size12; vazios/repetidos/fracionários/texto/overflow =>400; limites size1/50 aceitos e51 rejeitado. Offset calculado em long: page2147483647/size1 é válido e pode ser vazio; page2147483647/size2 é400 sem entrar no serviço. ID0/negativo/texto/fora de Long =>400 depois de autorização; ID válido ausente404.
- Query `accountId=<terceiro>&buyerId=<terceiro>&sort=id,asc` é ignorada sem mudar dono, count ou ordenação. Não há seleção de titular, filtro por data ou ordenação variável.
- P2 de binding, identificado por análise estática e ainda sem comprovação HTTP: em raiz e `/api`, enviar separadamente e combinados `_page=3`, `_size=1` e `size[]=1` (colchetes codificados na URL quando necessário), inclusive variantes vazias, não inteiras e repetidas desses nomes desconhecidos. Com `page/size` omitidos, esperar 200 com page0/size12 e o mesmo conteúdo/totais da consulta sem esses parâmetros; com `page=1&size=2` válidos, esperar a mesma página1/tamanho2, conteúdo, ordem e totais da consulta de controle. Incluir `!page=3` pelo mesmo mecanismo. Desconhecidos não alteram paginação nem causam 400. A seleção dos nomes exatos deve anteceder construção/binding e qualquer adaptação de prefixos ou `[]`; somente o mapa filtrado chega ao binder, sem releitura posterior da requisição original.
- Preservação de entradas permitidas no mesmo ensaio P2: `page/size` exatos válidos continuam aceitos; vazios/inválidos/fora dos limites continuam 400, mesmo acompanhados dos desconhecidos acima. `page=0&page=1` e `size=2&size=3`, com ou sem desconhecidos, continuam 400; não consolidar nem escolher uma ocorrência. Conferir que a resolução específica aciona Bean Validation antes do serviço e não muda o binding de outras rotas. Estes são procedimentos futuros, não testes executados nesta correção documental.
- Em raiz e `/api`, GET e HEAD próprios obedecem filtros reais; HEAD sem corpo, inclusive rejeições; ID alheio/não existente não revela compra. POST continua na cadeia anterior e público continua ignorando credencial inválida irrelevante.
- Perfis vêm do banco: alterar/revogar/conceder perfil depois da emissão do JWT e preparar coleção gerenciada obsoleta para provar que projeção escalar corrige tanto concessão quanto revogação. Token com claim de perfil privilegiado não amplia permissão. Conta ausente durante guarda de serviço também401 com challenge, falha de lookup500. Não prometer serializar mudança concorrente depois da leitura autorizadora.
- Validação pura de query/id termina antes de `OwnOrderQueryService`; observar ausência de chamada ao serviço nos400. Segurança de infraestrutura pode consultar conta antes disso. Violação interna de persistência/retorno é500, sem ser mascarada como400. Erros nativos de negociação/método mantêm semântica/headers.
- Page IDs/count usam mesmo titular; fetch em lote não tem Pageable e repete titular. Inspecionar SQL/plano de carregamento para ausência de N+1, coleção paginada ou paginação em memória. Variar número de pedidos/itens sem exigir número global rígido de SELECTs (otimizações legítimas de count podem variar). Materializar/serializar DTOs após retorno transacional com OSIV false. Nenhum acesso a preço atual para cálculo, categorias ou dados do comprador na resposta.
- Asserções de campo **exato** e ausência de accountId/buyer/roles/credenciais; schemas fechados não substituem comparação concreta das mensagens/status e headers por caso. Nome conserva espaços/acentos e não impõe limite de escrita. Não limitar a quantidade de itens para satisfazer desempenho.

## Comandos de aplicação — somente para fase futura autorizada

Os testes citados ainda devem ser criados. Na raiz Windows/PowerShell, seleção focal planejada:

Na execução futura, começar por casos HTTP que compilam contra a aplicação existente e falham pelo comportamento ausente (GET não disponível/protegido), antes de implementar. Erro de compilação, schema H2 ausente ou falha de fixture não demonstra RED funcional. Depois da implementação, confirmar GREEN focal e executar a regressão completa pertinente; registrar comandos/revisão/resultados de cada fase separadamente.

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQuery*' test
```

Depois da implementação e dos focais pertinentes, suíte completa de regressão:

```powershell
.\mvnw.cmd test
```

Equivalente Linux/CI já configurado:

```sh
./mvnw --batch-mode --no-transfer-progress test
```

**Não executados neste planejamento.** Antes de aceitar qualquer execução futura, conferir seleção efetiva nos XMLs Surefire: métodos/invocações dos dois contextos e de todas as classes aninhadas, não apenas total do console ou padrão `-Dtest`. Relatórios aninhados podem agregar casos externos; não somar casos duplicados/contêineres vazios. Se seleção focal omitir casos, corrigir seletor e registrar essa limitação, sem declarar cobertura pelo nome da classe. Suíte completa futura única sem filtro é a prova de regressão; não iniciar execução duplicada sem necessidade.

## Preservação dos contextos H2 e das evidências

O histórico da [006](../006-filtrar-catalogo-produtos/quickstart.md) registra DROP explícito de schema de banco compartilhado por fechamento de outro contexto create-drop, inclusive no contexto JWT. `DB_CLOSE_DELAY=-1` não evita esse DROP. Os novos contextos terão URL exclusiva estável por instância, com setup/limpeza próprios. Não mudar cache global, ordem dos testes, perfil/seed global, create-drop ou asserções para esconder falhas. Preservar as correções de H2ConsoleSecurityIntegrationTest e JwtTokenServiceTest.

Este plano não reexecuta nem reclassifica resultados locais/remotos anteriores. Relatórios recebidos e uma CI histórica aprovada para outro SHA não aprovam a 007. Evidência futura deve identificar comando, revisão/diff, ambiente, casos concretos, resultado e relatórios. Remota: registrar URL/evento/SHA efetivamente testado; em pull_request, associar merge testado ao HEAD e base. Ausência de CI não equivale a sucesso.

O **Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**. A 007 não herda esse desvio: Bean Validation puro antes do serviço, seguido da guarda de negócio. Gates de desenho no plano não são validação executável.

## Validação documental executada neste checkpoint

- Estado inicial conferido por Git: raiz/branch/HEAD esperados, limpo e seleção local007. `setup-plan.ps1 -Json` executado pelo fluxo, sem sobrescrever artefato prévio; template preenchido. Spec/checklist e código existente apenas lidos.
- Contrato OpenAPI3.0.3 validado pelo `openapi-spec-validator 0.9.0` já disponível no cache de uvx, em modo offline, sem dependência nova no projeto:

```powershell
& 'C:/Users/ronal/.local/bin/uvx.exe' --offline --from 'openapi-spec-validator==0.9.0' openapi-spec-validator --validation-errors all 'C:/Users/ronal/dscommerce-speckit/specs/007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml'
```

Resultado observado: `OK`, exit0. Validação do documento não comprova implementação, consultas SQL, filtros de segurança ou respostas HTTP.

Conferência complementar executada por Python via stdin no mesmo ambiente offline de uvx (`openapi-schema-validator 0.9.0`, PyYAML e jsonschema): zero erros OpenAPI, chaves YAML sem duplicação, sete schemas válidos, 26 referências internas resolvidas (12 destinos distintos), 16 exemplos de respostas e dois defaults validados. Calculados com Decimal os subtotais/totais dos exemplos; metadados das páginas conferidos. Limites monetários positivos/negativos e rejeição de campo extra verificados no schema; correspondência status/error/message conferida por caso, além do schema compartilhado. Matriz com exatamente os26 cenários únicos; spec preserva26 cenários/18FR/6SC e checklist16/16. Essas são verificações estáticas dos documentos, não testes da aplicação.

Revisão final documental por Python via stdin (`python -B -I -` do ambiente existente do Specify): exit0; 51 links Markdown locais com destinos existentes e quatro referências locais do contrato conferidos. Conteúdo dos cinco novos arquivos revisado, UTF-8 sem BOM e sem marcadores pendentes. `git diff --check`, `git diff --exit-code HEAD -- .` e `git diff --cached --exit-code`: exit0. Para arquivos não rastreados, aplicado individualmente `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`: nenhum diagnóstico de whitespace; exit1 corresponde à diferença esperada de arquivo novo, não falha de validação. Overrides de EOL só nesse comando, sem mudar configuração Git.

Estado conferido: apenas os cinco artefatos novos não rastreados, nenhum staged/rastreado modificado; branch/HEAD/main preservados; seleção local007 com mesmo SHA-256 do ponteiro. `.specify/extensions.yml` ausente antes e depois, sem hooks aplicáveis. tasks.md ausente. Não há evidência de execução funcional local ou remota da007 nesta etapa.

### Correção documental do P2 de binding — 2026-10-19

O P2 foi identificado por análise estática, sem reprodução HTTP. Alinhados somente plan, research, data-model e este guia: seleção dos nomes exatos page/size antes das transformações do binder, mapa filtrado como única fonte e Bean Validation antes do serviço; estratégia futura inclui _page, _size, size[] e !page sem interferência nos defaults/valores permitidos, preservando rejeições de entradas exatas inválidas/repetidas. O contrato permaneceu byte a byte igual, sem nova política de outras rotas e sem repetir o validador estrutural OpenAPI. Comprovação HTTP e implementação continuam futuras.

Revisão documental desta correção por Python via stdin no ambiente existente do Specify: exit0; 51 links locais, quatro referências locais do contrato, 26 referências internas e matriz dos26 cenários conferidos. Whitespace dos cinco não rastreados conferido individualmente por diff no-index, sem diagnósticos (exit1 esperado pela diferença de arquivo novo); git diff --check exit0. Comparação de SHA-256 confirmou preservação dos259 arquivos rastreados, do contrato e do ponteiro local. Nenhum código/teste/configuração/spec/checklist alterado, nenhum Maven executado, nenhum tasks/stage/commit/push.

## Pendências e limites

Sem decisão de negócio ou clarificação bloqueante. Implementação, geração de tasks, testes locais e CI da 007 pertencem às fases posteriores, mediante pedido próprio. O PDF local é fonte reaproveitada da investigação anterior, não arquivo versionado ou novamente auditado nesta etapa. Sem validação em PostgreSQL/produção, SLA, concorrência serializada ou implantação. Nesta fase são gerados somente os cinco artefatos de planejamento; não fazer stage/commit/push.
