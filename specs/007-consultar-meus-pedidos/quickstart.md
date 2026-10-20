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

## T001 — Contexto inicial da execução limitada — 2026-10-19

**Escopo executado**: somente T001 de [tasks.md](tasks.md), sem iniciar T002 ou tarefas posteriores. O histórico acima permanece como registro das etapas anteriores; os comandos de aplicação ali apresentados continuam procedimentos futuros.

### Estado inicial confirmado

- Raiz: `C:/Users/ronal/dscommerce-speckit`; branch real: `007-consultar-meus-pedidos`.
- HEAD inicial real: `5f1a3a440bcd610c23e376cf390523fdcd7e58c3`, commit das tarefas. A base documental das tarefas é `97ac0354d9f3835e041629fee4a661c0afe09eb7`, commit do planejamento; não é o HEAD desta execução. Spec/checklist foram commitados em `d69ef4690e2c684c540b12a7f304978671c3ba7a`.
- `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all` confirmaram esses valores e árvore inicialmente limpa. `git diff --name-status` e `git diff --cached --name-status` não exibiram alterações.
- Seleção local: `.specify/feature.json` aponta para `specs\007-consultar-meus-pedidos`; `SPECIFY_FEATURE` e `SPECIFY_FEATURE_DIRECTORY` ausentes. SHA-256 inicial do ponteiro: `A8C06986C0FC138BC352E10DC584D77DE64AE7C2BF661D4F03F953BDE2D6A929`.
- `git rev-parse main`: `b092a6849177a7d72c40c67f896f0b70a56e0f10`. A leitura do histórico confirma a sequência integração001–006 → especificação007 → planejamento007 → tarefas007; isso não comprova execução funcional da007.

### Procedimentos e resultados desta T001

- Lidas as instruções de [AGENTS.md](../../AGENTS.md), [harness](../../docs/harness.md), [constituição1.0.0](../../.specify/memory/constitution.md) e da skill local `speckit-implement`; T001 lida integralmente e situada nas dependências da lista de tarefas. A autorização limita o fluxo da skill a T001, sem preparação de fases posteriores.
- Lidos os sete artefatos aprovados: [spec](spec.md), [checklist](checklists/requirements.md), [plan](plan.md), [research](research.md), [data-model](data-model.md), [contrato](contracts/own-orders-openapi.yaml) e este quickstart. Reutilizadas as referências ao PDF registradas na spec, sem nova investigação do PDF ou de fontes externas.
- Executado `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0. `FEATURE_DIR` absoluto `C:\Users\ronal\dscommerce-speckit\specs\007-consultar-meus-pedidos`; `AVAILABLE_DOCS`: `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `tasks.md`. A resolução utilizou a seleção existente, sem override de ambiente.
- Checklist somente lido e contado: `requirements.md` com16 itens,16 marcados, zero abertos, **PASS documental**. As marcações qualificam os requisitos, não a implementação ou os testes.
- `java -version` exibiu OpenJDK Azul Zulu `21.0.12.1`, build `21.0.12.1+1-LTS`. `.\mvnw.cmd --version` terminou com exit0 e informou Apache Maven `3.9.16`, Java `21.0.12.1`, Azul Systems, runtime `C:\Program Files\Zulu\zulu-21`, Windows10/amd64. A distribuição Maven foi localizada no cache `.m2/wrapper/dists`. Não foi executado goal de build/teste.
- Lidas as propriedades existentes do Wrapper: `wrapperVersion=3.3.4`, tipo `only-script`, distribuição Maven3.9.16 e checksum configurado. Não houve regeneração do Wrapper ou nova auditoria do ZIP/checksum publicado.
- Verificação de setup somente em leitura: repositório Git confirmado; `.gitignore` existente cobre `target/`, `*.class`, `*.jar`, `build/`, `.gradle/` e padrões usuais locais. A busca de arquivos de Docker/ESLint/Prettier/npm/Terraform/Helm, fora de `.git/` e `target/`, não encontrou marcadores. Nenhum ignore, configuração, fixture ou diretório de preparação foi criado/alterado.
- `.specify/extensions.yml` ausente na entrada: nenhum hook prévio aplicável.

### Evidências históricas e limites

O harness registra separadamente os79 testes históricos locais no Windows e a primeira CI Linux da fundação, execução36776432450 para `1d49b28d46d6a73cf6c39b1077574c1306dba7f2`. O [quickstart da006](../006-filtrar-catalogo-produtos/quickstart.md) registra, entre suas evidências históricas, a suíte local corrigida de2.754 testes e os problemas de isolamento H2. Esses registros permanecem históricos, sem reexecução ou reclassificação nesta T001. As validações OpenAPI0.9.0 anteriores, registradas acima, continuam evidência estrutural documental; não foram repetidas. Nenhuma CI foi consultada, disparada ou atribuída ao HEAD atual.

O recorte preservado é CLIENT/ADMIN atuais consultando somente compras próprias, incluindo conteúdo/count/detalhe; dinheiro histórico exato, nome atual e ordem registrada. A proteção antecipada, o binding exato page/size antes das transformações do binder, a validação pura na fronteira e o isolamento H2 permanecem decisões para tarefas futuras. Nenhuma dessas propriedades foi comprovada funcionalmente aqui. A Feature005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**; a007 não herda essa exceção.

**Limite de execução**: sem baseline B0/T002, compilação, teste, dispatch MockMvc, requisição HTTP, inicialização Spring/H2, implementação ou preparação de testes. Relatórios existentes não foram movidos, sobrescritos ou tratados como testes desta execução. Nenhum stage, commit, push, troca de branch ou alteração de main. Sem impedimento identificado para atender ao escopo documental/ambiental da T001; a feature ainda não tem comprovação funcional neste checkpoint.

### Fechamento documental da T001

Revisão do diff e dos dois arquivos alterados concluída. Conferência local por PowerShell/.NET: 28 links Markdown locais dos dois documentos com destinos existentes; prefixo recebido do quickstart preservado byte a byte; tasks difere do HEAD somente pelo marcador de T001. Resultado: **1 tarefa concluída (T001), 33 abertas (T002–T034)**. Nenhum arquivo novo.

Comparação dos manifestos SHA-256 de caminhos/conteúdos confirmou preservação dos 263 rastreados fora de quickstart/tasks e dos 940 arquivos recebidos em `target/surefire-reports/`, sem mover relatórios ou apurar testes novos. Ponteiro local com o mesmo SHA-256 inicial; HEAD e main mantidos. `.specify/extensions.yml` continua ausente, sem hooks posteriores aplicáveis.

`git diff --check`: exit0, sem diagnóstico de whitespace; `git diff --cached --exit-code`: exit0, índice vazio. Estado final limitado a quickstart.md e tasks.md modificados, sem não rastreados ou staged. Estas são conferências documentais/de preservação, não comprovação HTTP, JPA, segurança, RED/GREEN ou CI da Feature007. T002 e os demais checkpoints continuam não executados.

## T002 — Baseline B0 recuperada após interrupção — 2026-10-20

**Escopo**: retomada e fechamento somente da T002, dependente da T001 já concluída. O texto e as evidências da T001 acima permanecem integrais, inclusive suas afirmações sobre o estado naquele checkpoint. A interrupção por limite de uso deixou o registro documental da T002 pendente; as evidências encontradas demonstram que o Maven e a auditoria original haviam terminado. Nenhum Maven foi iniciado nesta retomada, nem tarefa T003 ou posterior.

### Execução anterior recuperada

- Antes de decidir sobre nova execução, consultados os processos locais por `Get-CimInstance Win32_Process`: nenhum Java/Maven ativo. Encontrada uma execução B0 em `target/feature007-validation/t002-b0-20261019T232239386-dfb1e6/`, com console completo, exit code capturado, relatórios e auditoria. Nenhum motivo de repetição: revisão, arquivos relevantes, configuração e seleção permanecem iguais.
- Raiz e branch real mantidas: `C:/Users/ronal/dscommerce-speckit`, `007-consultar-meus-pedidos`; HEAD testado e atual `5f1a3a440bcd610c23e376cf390523fdcd7e58c3`; main `b092a6849177a7d72c40c67f896f0b70a56e0f10`. `session-start.json`, `received.diff`, cópias recebidas de quickstart/tasks e `tracked-before.json` identificam o checkout da execução: somente o registro e a marcação da T001, sem alteração de código/testes/configuração.
- Comando realmente executado na raiz em **2026-10-19**, antes desta retomada:

```powershell
.\mvnw.cmd '-Dtest=OrderSecurityIntegrationTest*,PublicCatalogNameFilterIntegrationTest*' test
```

- `result.json`: início `2026-10-19T23:26:30.0250952-03:00`, término `2026-10-19T23:30:06.5447774-03:00`, duração do processo `216,520 s`, **MavenExitCode=0**. Console termina com **BUILD SUCCESS**, duração Maven `03:26 min`, **269 testes, 0 falhas, 0 erros, 0 ignorados**. A duração Maven e a duração externa do processo são medidas distintas.
- Ambiente: XMLs registram Java Azul `21.0.12.1` e Windows10; Wrapper existente seleciona Maven `3.9.16`, conforme suas propriedades e a verificação de versão da T001. Não foi repetido comando de versão nesta retomada. As notas de preparação originais preservam uma interrupção de auxiliar de metadados (exit -1) antes de Maven/arquivamento e uma limitação de caminho longo corrigida antes dos testes; nenhuma delas constitui falha funcional ou RED.

### Seleção efetiva e conferência dos resultados

| Classe/contexto no Surefire | Métodos distintos | Invocações JUnit aprovadas |
| --- | --- | --- |
| `OrderSecurityIntegrationTest` | 8 | 185 |
| `PublicCatalogNameFilterIntegrationTest$ApiContext` | 11 | 42 |
| `PublicCatalogNameFilterIntegrationTest$RootContext` | 11 | 42 |
| `PublicCatalogNameFilterIntegrationTest` (contêiner externo vazio) | 0 | 0 |
| **Total, sem duplicatas** | **30 por classe concreta** | **269** |

Os XMLs foram relidos nesta retomada, com comparação dos resumos TXT e console, contagem de cada `testcase`, detecção de duplicatas por classe/nome e comparação da seleção histórica esperada. Confirmados todos os269 casos, sem omissões, casos inesperados, falhas, erros, ignorados ou duplicatas. A referência histórica serviu somente à conferência da seleção; a aprovação vem dos resultados locais da B0 recuperada.

Segurança: oito métodos, com invocações `9/8/5/6/6/12/1/138`, respectivamente para subject/perfis atuais, rechecagem pelo proxy, identidade inválida pelo proxy, ausência de perfis, falha de resolução, rechecagem entre filtro/serviço, servlet context path e JWT inválido. Catálogo em **cada** contexto: preparo1, paginação existente1, nomes/literais5, comprimentos2, espaços externos19, espaços internos3, paginação filtrada1, páginas vazias7, independência entre requisições1, acesso/formato público1 e detalhes/legado1. `executed-cases.json` da execução original e `methods.json` da auditoria de retomada preservam os nomes e invocações individuais.

As invocações parametrizadas contam individualmente. Dispatches MockMvc, chamadas diretas pelo proxy do serviço e requisições HTTP não são testes JUnit adicionais. A suíte de segurança utiliza MockMvc com filtros reais e chamadas pelo proxy; a suíte de catálogo usa HTTP real em porta aleatória nos contextos raiz e `/api`, inclusive os cenários de login/acesso. Esta baseline de contratos existentes não substitui os testes HTTP novos da007.

### Preservação e auditoria da retomada

- Antes do Maven original, os **940 arquivos recebidos** de `target/surefire-reports/` foram movidos para `received-surefire-reports/` dentro do diretório B0, com origem/destino absolutos conferidos dentro de `target/`, sem sobreposição ou sobrescrita. `session-start.json`, `received-reports-manifest.json` e `archive-preservation.json` registram a operação e a igualdade de hashes. A retomada reconferiu integralmente caminhos, tamanhos e SHA-256 desses940 arquivos; nenhum entra na contagem da B0.
- Os **oito relatórios novos** (quatro XML/quatro TXT) permanecem tanto em `target/surefire-reports/` quanto na cópia `reports/` da execução original; tamanhos e SHA-256 das duas áreas conferem com `new-reports-manifest.json`. Não foram movidos nem sobrescritos nesta retomada.
- Auditoria independente, sem Maven: `target/feature007-validation/t002-resume-20261020/audit-resume.ps1`, executada da raiz via PowerShell. Resultado final **exit0, Pass=true**, em `attempt3/resume-audit.json`, com suites, ambiente e métodos detalhados nos JSONs correspondentes. Duas tentativas auxiliares anteriores terminaram com exit1 por enumeração JSON do PowerShell5.1 e leitura de tamanho em caminho longo; notas e saídas parciais preservadas em diretórios distintos, sem alteração de relatórios ou rastreados. A leitura por caminho Windows estendido confirmou tamanho/hash, sem divergência de arquivo. Essas falhas de auditoria não são falhas de testes ou RED.
- Confirmada a preservação dos **964 arquivos** de evidência da execução original; dos **265 rastreados** no manifesto anterior ao Maven, todos correspondiam byte a byte ao iniciar o fechamento documental, incluindo as cópias recebidas de quickstart/tasks da T001. O fechamento apenas acrescenta esta seção ao quickstart e marca T002; a T001 permanece intacta. A seleção local mantém o SHA-256 registrado na T001.
- Reexecutado `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007 e artefatos esperados. Checklist de requisitos somente lido:16/16, **PASS documental**. `.specify/extensions.yml` ausente, sem hooks aplicáveis. Skill `speckit-implement` aplicada somente à T002 autorizada.

**Resultado e limites**: B0/T002 aprovada por evidência local completa, recuperada sem repetição. Não houve suíte completa inicial, `clean`, Maven concorrente, RED artificial, mudança de cache/ordem/seed/asserções, implementação ou preparação das próximas tarefas. Nenhuma CI foi consultada, disparada ou atribuída à revisão atual; os históricos locais/remotos anteriores permanecem separados. Não há prova nova das consultas GET/HEAD, titularidade da007, binding P2, dinheiro histórico nas novas respostas ou ausência de escrita dessas consultas. A Feature005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**. Nenhum stage, commit, push, merge ou troca de branch.

### Fechamento documental da T002

Revisão por PowerShell/.NET: exit0; prefixo recebido do quickstart preservado byte a byte; tasks recebida difere somente pelo marcador T002; os263 demais rastreados e os964 arquivos originais de evidência mantêm seus SHA-256. Conferidos28 links Markdown locais; ponteiro local preservado. `git diff --check`: exit0, sem diagnóstico de whitespace; `git diff --cached --exit-code`: exit0, índice vazio. Detalhes em `target/feature007-validation/t002-resume-20261020/final-review.json`. Diff e artefatos novos de auditoria revisados. Estado: **T001 e T002 concluídas;32 tarefas abertas (T003–T034)**; somente quickstart.md e tasks.md modificados no Git, sem staged ou não rastreados visíveis (`target/` ignorado). Branch, HEAD e main mantidos; sem novo Maven ou CI na retomada.

## T003 — Fixture explícita de pedidos próprios — 2026-10-20

**Escopo executado**: somente T003, dependente da B0/T002 já aprovada. Checkout inicial confirmado por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all`: raiz `C:/Users/ronal/dscommerce-speckit`, branch `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, Git limpo. Seleção local007 preservada; main permanece `b092a6849177a7d72c40c67f896f0b70a56e0f10`. T001–T002 e o histórico acima são preservados como prefixo, sem reclassificar a baseline como prova das consultas novas.

### Preparação implementada

- Criada [OwnOrderQueryTestFixture](../../src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryTestFixture.java), usando explicitamente a [fixture administrativa](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java) e seus requisitos de ativação por [AdminProductTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java). O construtor não insere dados. Preparação e limpeza são chamadas explícitas; cada pedido com seus itens é persistido por JDBC em transação `REQUIRES_NEW`, com ownership registrado somente após commit. “Setup commitado” significa confirmação no banco de teste, sem commit Git.
- `preparePaginationScenario()` prepara seis contas: CLIENT, ADMIN, ambos, nenhum, outro CLIENT com compras e CLIENT sem compras; dois produtos, uma categoria, duas associações, nove pedidos e dez itens. Cinco pedidos são do CLIENT principal; os demais pertencem ao outro CLIENT, ADMIN e ambos. Instantes em milissegundos, três próprios empatados atravessando o corte de páginas de tamanho2 e ordem esperada explícita por IDs. Nenhuma consulta produtiva, paginação ou autorização da007 foi implementada.
- Manifestos imutáveis de pedidos/itens, com IDs, titular, instante, estado, posições, quantidades e dinheiro esperado. A ordem B/A e os valores `20.00 + 22.50 = 42.50`, `0.01` e `9007199254740993.01` são preparados explicitamente; preço atual do catálogo difere do histórico. Esperados não usam DTO/mapper, `Order.getTotal()` ou `OrderItem.getSubtotal()`. `createOrder` aceita as linhas e o total esperado do cenário sem impor teto comercial novo; o teste com51 produtos/itens comprova que size50 de paginação não vira limite de itens da fixture.
- A limpeza remove somente IDs de itens/pedidos da própria instância, antes de delegar produtos/categorias/associações/contas à fixture existente. Dados de outra instância são preservados; limpeza pode ser repetida. O inicializador existente conserva URL H2 exclusiva, gerada uma vez por ApplicationContext; create-drop mantido, SQL/import desabilitados somente nesses contextos. Perfil, seeds e configurações globais permanecem intactos.

### Procedimentos e validação executada

Skill `speckit-implement` limitada à T003: lidos tarefa, dependências e artefatos pertinentes; `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0, feature007 e documentos esperados. Checklist somente lido:16/16, **PASS documental**. `.specify/extensions.yml` ausente, sem hooks prévios. `.gitignore` já cobre os padrões Java/locais; nenhum marcador adicional de ferramentas exigiu ignore novo. `java -version`: exit0, Azul Zulu21.0.12.1; Wrapper existente seleciona Maven3.9.16. Nenhuma execução Java/Maven estava ativa antes de cada tentativa.

Criado [OwnOrderQueryTestFixtureTest](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestFixtureTest.java): testes de suporte com `WebApplicationContextRunner`, exclusão existente de componentes de teste e `AdminProductTestConfig`, contextos completos e leitura por conexão JDBC independente. Sete ApplicationContexts são abertos/fechados nas seis provas, incluindo dois simultâneos para verificar que o DROP de um não afeta o outro. São **testes de preparação/persistência reais em H2, sem mocks, zero dispatches MockMvc e zero requisições HTTP reais**. O construtor herdado monta MockMvc, mas não faz login; `login()` apenas delega ao helper existente e não foi chamado nesta execução. Não há teste de endpoint nem RED funcional da007 nesta tarefa.

Comando focal realmente executado na raiz, sequencialmente:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryTestFixtureTest' test
```

| Tentativa | Evidências locais em `target/feature007-validation/` | Resultado observado |
| --- | --- | --- |
| 1 | `t003-fixture-20261020/` | 03:27:41.071–03:28:41.071 (-03:00), exit1, BUILD FAILURE em testCompile: sobrecarga ambígua de `assertThat(queryForObject(...))` para Instant. Nenhum teste/Surefire executado; não é RED funcional. |
| 2 | `t003-fixture-20261020-retry/` | 03:29:19.069–03:31:16.818 (-03:00), **exit0, BUILD SUCCESS;6 testes,0 falhas,0 erros,0 ignorados**. Duração Maven01:52; processo117,749s. |

A repetição foi necessária após corrigir a inferência de tipo com variável local `recordedAt`, mantendo a mesma comparação e todas as asserções. Primeira tentativa preservada, sem `clean`, baseline repetida, suíte completa, cache/ordem/seed alterados ou Maven concorrente. XML confirma Java21.0.12.1/Azul e Windows10. Compilação de outros fontes de teste não significa execução dessas suítes.

Seleção efetiva conferida no console, TXT e XML: somente `com.devsuperior.dscommerce.config.OwnOrderQueryTestFixtureTest`, seis casos únicos, sem classes aninhadas/parametrização/agregação. Métodos realmente executados:

- `preparesCommittedRolesOrdersTiesPositionsAndIndependentMoneyManifest`: construção vazia, perfis, dados confirmados, manifesto, posições, empates e imutabilidade.
- `commitsSetupIndependentlyOfCallerRollback`: pedido e itens visíveis por JDBC independente durante e após rollback do chamador.
- `cleansOnlyOwnedRowsBeforeCatalogAndRejectsForeignFixtureInputs`: limpeza na ordem das FKs, idempotência, dados alheios preservados e rejeição de entradas de outra fixture.
- `rollsBackTheWholeOrderWhenALineViolatesExistingDatabaseConstraints`: rollback integral por quantidade inválida na segunda linha e preparação válida posterior. Verifica a fixture, não a atomicidade do endpoint produtivo.
- `preparesAllRequestedItemsWithoutBorrowingThePageSizeAsAnItemLimit`:51 itens persistidos sem truncamento.
- `keepsDatabaseUrlStableAndSurvivesAnotherContextCreateDrop`: URLs distintas e estáveis, create-drop e isolamento ao fechar outro contexto.

### Preservação e limites

Antes da primeira tentativa, os oito relatórios recebidos da B0 foram movidos de `target/surefire-reports/` para `t003-fixture-20261020/received-surefire-reports/`. Origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita; caminhos/tamanhos/SHA-256 comparados após mover (`archive-preservation.json`:8, HashesMatch=true). Antes da segunda tentativa, não havia Surefire recebido, pois a primeira parou em compilação; essa ausência está registrada. As evidências originais da B0, inclusive seus940 relatórios históricos arquivados, permanecem preservadas.

Cada tentativa tem comando, horários, HEAD/status/diff, manifestos e cópias dos dois fontes novos. Console/exit code completos em `console.log` e `result.json`; os dois relatórios finais (XML/TXT) estão em `target/surefire-reports/` e na cópia `t003-fixture-20261020-retry/reports/`, com SHA-256. Auditoria executada da raiz por `& 'target/feature007-validation/t003-fixture-20261020-retry/audit.ps1'`: **exit0, Pass=true**. XML/TXT/console e seis métodos conferem; fontes idênticos aos executados;265 rastreados ainda idênticos antes deste fechamento documental e1000 arquivos de evidência prévia preservados, incluindo os relatórios B0 movidos na primeira tentativa.

Este GREEN valida somente a fixture. T004 (probe/observação reutilizável) e T005–T034 permanecem não executadas; os contadores privados do teste servem às provas da própria preparação e não antecipam o probe. Sem implementação produtiva, alteração de comportamento001–006, prova de GET/HEAD/titularidade/binding/ausência de escrita das consultas novas, CI, PostgreSQL ou produção. A exceção histórica da005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**. Sem stage, commit, push, alteração de main ou de documentos aprovados fora de quickstart/tasks.

### Fechamento da T003

Diff e dois arquivos Java novos revisados. `git diff --check`: exit0. Whitespace dos não rastreados conferido individualmente por `git diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico de whitespace, exit1 pela diferença esperada de arquivo novo.32 links locais conferidos. Auditoria `final-review.ps1`: exit0, Pass=true em `final-review.json`; prefixo recebido do quickstart preservado byte a byte, tasks difere somente pelo marcador T003,263 outros rastreados e1000 evidências prévias com hashes preservados. Notas de ajustes auxiliares dessa conferência estão em `review-note.txt`, sem mudança dos fontes testados ou novo Maven. `.specify/extensions.yml` continua ausente, sem hooks posteriores. Estado: **T001–T003 concluídas;31 tarefas abertas (T004–T034)**. Apenas quickstart/tasks modificados e os dois Java novos não rastreados; índice vazio, HEAD/branch/main e seleção007 preservados. Nenhum impedimento pendente; validação restrita à preparação, sem entrega funcional da007, stage, commit ou push.

## T004 — Probe completo e janela de observação — 2026-10-20

**Escopo**: somente T004, dependente da B0/T002 já aprovada. Estado recebido conferido por Git: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, seleção local007; somente quickstart/tasks da007 modificados, os dois Java da T003 não rastreados e índice vazio. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Os arquivos e registros T001–T003 são preservados, inclusive a falha de compilação da primeira tentativa da T003, que continua distinta de RED funcional e do GREEN posterior.

### Implementação restrita ao suporte de teste

- Criado [OwnOrderQueryDatabaseProbe](../../src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java), compondo o [probe administrativo existente](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductDatabaseProbe.java), sem alterá-lo. O snapshot reutilizado cobre todos os campos de produtos, categorias, associações, pedidos e itens; a extensão lê `id/name/password_hash` de todas as contas, inclusive sem perfil, e todas as linhas de perfis por JDBC independente. Cada conexão vem diretamente do driver, sem pool da aplicação ou EntityManager vinculado ao chamador. Listas/coleções capturadas são imutáveis.
- Estado completo das contas participa de igualdade, sem acessores públicos de credenciais. `assertUnchanged(before, after)` informa somente tabelas alteradas; representações de snapshots e observações redigem credenciais, resultado e SQL. Os testes capturam falhas de comparação do helper e de AssertJ e verificam que nome, senha/hash e JWT não aparecem nos diagnósticos. Asserções desta proteção também usam diagnóstico fixo, sem reimprimir um valor eventualmente exposto.
- `observeRead(sql, operação)` fotografa antes, limpa os eventos de setup/login, executa apenas a operação informada, copia os eventos e fotografa depois. Limpeza do chamador ocorre fora dessa captura; exceções da operação propagam. `assertNoWrites()` exige igualdade dos estados confirmados e somente SELECT nos eventos Hibernate observados, aceitando comentários iniciais; instruções desconhecidas/não SELECT são rejeitadas sem imprimir SQL. Não é parser SQL geral.
- Criada [OwnOrderQueryTestObservationConfig](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestObservationConfig.java): `@TestConfiguration`, perfil `test` e ativação explícita por import/configuração do teste. Expõe o probe e reutiliza `AdminProductDatabaseProbe.SqlObservation` via `HibernatePropertiesCustomizer`, sem instrumentar produção, trocar beans produtivos ou alterar configuração global/cache/ordem/seed. Outros contextos conservam seus próprios bancos H2 e não recebem esse observador por descoberta automática.

### Validação executada

Aplicada a skill `speckit-implement` somente à T004: tarefa/dependências e artefatos pertinentes lidos; `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007 e documentos esperados. Checklist somente lido:16/16, **PASS documental**; `.specify/extensions.yml` ausente, sem hooks prévios. Ignores Java existentes suficientes, sem novo arquivo de configuração necessário. `java -version`: exit0, Azul Zulu21.0.12.1; propriedades do Wrapper mantêm Maven3.9.16. Nenhum Java/Maven ativo antes da execução.

Criado [OwnOrderQueryDatabaseProbeTest](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryDatabaseProbeTest.java), selecionado isoladamente, sem executar `OwnOrderQueryTestFixtureTest`. A fixture T003 é usada somente como preparação dos dados necessários às provas do probe. Comando único da T004, realmente executado na raiz:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDatabaseProbeTest' test
```

Início `2026-10-20T03:44:28.0573493-03:00`, término `2026-10-20T03:46:22.5397920-03:00`, duração do processo114,482s, duração Maven01:48. Resultado: **BUILD SUCCESS, exit0;7 testes,0 falhas,0 erros,0 ignorados**. Ambiente XML: Java21.0.12.1, Azul, Windows10. Houve uma única execução; nenhuma repetição da T003/B0/suíte completa, RED artificial ou implementação defeituosa deliberada. Alterações controladas de dados pertencem às provas do observador em H2 isolado.

Console e Surefire XML/TXT conferidos: somente a classe concreta `com.devsuperior.dscommerce.config.OwnOrderQueryDatabaseProbeTest`, sete métodos/casos únicos, sem aninhadas ou parametrização. Métodos efetivamente executados:

- `capturesEveryDomainTableAndCompleteAccountsIncludingAccountsWithoutRoles`: cobertura das sete tabelas, contas sem perfil e coleções imutáveis.
- `detectsChangesInAllSevenTablesAndRedactsAccountComparisonFailures`: alterações detectadas em cada tabela, incluindo nome/hash de conta e perfis, com comparação segura e restauração dos dados.
- `readsCommittedStateOnFreshConnectionsDuringCallerRollback`: conexões distintas e estado confirmado independente da alteração ainda não commitada do chamador.
- `restrictsObservationToReadAfterSetupLoginAndBeforeCleanup`: janela exclui ruído de setup/login e operações posteriores; eventos copiados não mudam; resultado/JWT redigido e exceção propagada.
- `detectsHibernateDmlEvenWhenCommittedSnapshotsAreEqual`: UPDATE Hibernate que conserva o valor é detectado apesar dos snapshots iguais.
- `detectsDirectJdbcChangesWhichStatementInspectorCannotObserve`: UPDATE JDBC direto deixa zero eventos no inspector, mas é detectado pelo snapshot.
- `activatesOnlyByExplicitTestImportAndKeepsOtherContextsIndependent`: ativação explícita/perfil de teste, ausência em outro contexto, URL H2 distinta e primeiro contexto preservado após fechamento do segundo.

São **sete testes de infraestrutura com H2/JDBC e Hibernate reais, sem mocks**. O setup do teste de janela faz **um login por dispatch MockMvc interno**, usando a fixture existente; ele ocorre antes da captura. **Zero requisições HTTP reais** e nenhuma consulta GET/HEAD da007. Quantidade de contextos, SQLs e dispatches não é teste JUnit adicional. Uso de transação readOnly no controle de SELECT não é apresentado como prova autônoma de ausência de escrita.

### Evidências preservadas e limites

Evidência própria em `target/feature007-validation/t004-probe-20261020/`: `session-start.json`, `working.diff`, cópias dos cinco fontes usados e dos documentos recebidos, manifestos, console e `result.json`. Antes do Maven, os dois relatórios recebidos da T003 foram movidos de `target/surefire-reports/` para `received-surefire-reports/` nesse diretório inédito, após conferir origem/destino absolutos dentro de target, sem sobreposição/sobrescrita. Tamanhos/caminhos/SHA-256 conferidos após mover; `archive-preservation.json`:2, HashesMatch=true. Relatórios da T003 continuam separados dos dois XML/TXT novos da T004, mantidos em `target/surefire-reports/` e na cópia `reports/` da T004.

Auditoria executada na raiz por `& 'target/feature007-validation/t004-probe-20261020/audit.ps1'`: **exit0, Pass=true** em `audit.json`; console/XML/TXT e sete métodos conciliados, fontes idênticos aos usados,265 rastreados ainda iguais antes deste fechamento documental,1021 arquivos de evidência prévia intactos e os dois relatórios recebidos preservados. A produção, asserções existentes e os dois Java recebidos da T003 não foram alterados. A seleção/configuração do Maven não incluiu testes T003, B0 ou regressão completa; compilar os fontes existentes não significa executá-los.

**Limites**: snapshot composto usa duas transações de leitura independentes (domínio reutilizado e contas/perfis); não promete estado global atômico diante de escritores concorrentes. O protocolo exige setup/login concluídos e limpeza posterior, sem outros escritores concorrentes. StatementInspector não vê JDBC direto; igualdade dos snapshots não detecta escrita JDBC transitória revertida antes da segunda captura. ReadOnly, eventos ou snapshots isoladamente não bastam para alegar ausência universal de escrita. As provas atuais validam a infraestrutura complementar, sem comprovar imutabilidade/titularidade/segurança/binding das futuras consultas007 ou repetir validações funcionais001–006. Nenhuma alteração produtiva ou execução T005+; nenhuma CI, stage, commit, push ou alteração de main. A005 permanece **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**.

### Fechamento da T004

Revisados o diff em relação aos documentos recebidos e os três Java novos. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, índice vazio. Whitespace dos três não rastreados novos conferido por `git diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico, exit1 pela diferença esperada de arquivo novo.36 links locais conferidos. `final-review.ps1`: exit0, Pass=true em `final-review.json`; prefixo recebido do quickstart de42804 bytes intacto, tasks recebida difere somente por T004,263 outros rastreados e1021 evidências prévias com hashes preservados. Os cinco fontes, inclusive os dois recebidos da T003, correspondem às cópias usadas na execução; relatórios finais preservados nas duas áreas. Hooks posteriores ausentes (`.specify/extensions.yml` não existe). Estado: **T001–T004 concluídas;30 tarefas abertas (T005–T034)**; apenas quickstart/tasks modificados e cinco Java não rastreados (dois recebidos da T003 e três novos da T004). Sem impedimento pendente, staged, commit, push, mudança de branch/HEAD/main ou prova funcional das consultas007.

## T005 — Fundação de testes HTTP e F0-RED — 2026-10-20

**Escopo executado**: somente T005, após T003/T004 concluídas. Estado inicial confirmado por Git: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, seleção local007; somente quickstart/tasks modificados, exatamente os cinco Java recebidos de T003/T004 não rastreados e nada staged. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Suporte, asserções e evidências recebidos preservados, inclusive a falha de compilação anterior da T003 e os limites do snapshot composto da T004.

### Testes escritos antes de produção nova

Criados [OwnOrderQueryHttpTestSupport](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java), [OwnOrderQuerySecurityHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java) e [OwnOrderQuerySecurityApiHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java). As duas classes concretas compartilham os cenários `authenticationGate*` do suporte abstrato. Cada uma abre contexto completo com `RANDOM_PORT`, identidade de contexto distinta, OSIV false, perfil test, `AdminProductTestConfig` e import explícito da observação T004. URLs H2 exclusivas/estáveis e portas distintas foram conferidas. Não há cadeia de segurança, decoder ou bean produtivo substituído.

Preparação explícita uma vez por classe: contas CLIENT/ADMIN/ambos/nenhum e outra CLIENT, confirmadas no H2 pela fixture existente. Cinco logins por contexto usam **POST /login HTTP real**, `java.net.http.HttpClient`, sem redirecionamento e porta aleatória. Tokens frescos são conferidos pelo decoder real quanto à identidade; credenciais malformada, de assinatura alterada e expirada são rejeitadas pelo decoder real no setup. A expirada é assinada pelo encoder existente, com exp uma hora no passado, sem alterar relógio ou TTL global. A quinta conta é removida por transação JDBC confirmada após seu login, mantendo token assinado/vigente e conta efetivamente ausente. A limpeza posterior remove somente dados da própria fixture.

Matriz por contexto: GET e HEAD de `/orders?page=0&size=2`, `/orders?page=not-an-integer&size=0`, `/orders/1` e `/orders/not-an-integer`. Sem credencial, malformada, assinatura inválida, expirada e conta removida exigem401; conta existente sem perfil exige403, inclusive antes de paginação/ID malformados. Controles CLIENT, ADMIN e ambos usam as duas entradas válidas e aceitam provisoriamente200/404/405, vedando401/403/500. O sucesso de negócio200 será exigido nas histórias posteriores de lista/detalhe; nenhuma consulta ou proteção produtiva foi implementada aqui.

Os testes de rejeição também contêm asserções futuras de Bearer challenge em401, envelope exato de quatro campos em GET e corpo vazio em HEAD. Na execução RED atual, as rejeições falham primeiro pelo status; essas asserções posteriores não foram aprovadas. Os logs registram apenas contexto, classe de credencial, método, caminho, status e tamanho do corpo, sem credenciais/JWT/corpo de login. Cada requisição de cenário usa `observeRead` recebido da T004, depois de setup/login e antes de limpeza, mantendo os limites já documentados; igualdade dos estados e eventos SELECT-only observados não comprovam isoladamente ausência universal de escrita.

### Execução focal única

Aplicada a skill `speckit-implement` limitada à T005: tarefa/dependências e artefatos pertinentes lidos; `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007 e documentos esperados. Checklist somente lido:16/16, **PASS documental**. `.specify/extensions.yml` ausente, sem hooks prévios. Ignores Java existentes suficientes; nenhum marcador adicional de ferramentas exigiu arquivo novo de configuração. `java -version`: exit0, Azul Zulu21.0.12.1; Maven3.9.16 pelo Wrapper existente. Nenhum Java/Maven ativo antes da execução.

Comando F0-RED realmente executado na raiz, uma vez, sem concorrência:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQuerySecurity*#authenticationGate*' test
```

Início `2026-10-20T03:57:57.5334697-03:00`, término `2026-10-20T03:59:31.1512691-03:00`, duração do processo93,618s, duração Maven01:27. **BUILD FAILURE, exit1;120 testes,96 falhas,0 erros,0 ignorados;24 controles aprovados.** Compilação, preparação Spring/H2, logins e transporte HTTP completaram; não houve erro de fixture/schema/compilação registrado como RED. Ambiente XML: Java21.0.12.1/Azul e Windows10.

Seleção efetiva no console/XML/TXT: somente as duas classes concretas de segurança acima, cada uma com60 invocações,48 falhas e12 controles aprovados; nenhuma classe aninhada, contêiner agregado ou duplicação. Os três métodos herdados executados em **cada** classe foram:

| Método | Invocações por contexto | Resultado atual por contexto |
| --- | --- | --- |
| `authenticationGateRejectsMissingInvalidExpiredAndRemovedAccounts` | 40 | 40 falhas: esperado401, recebido404/405. |
| `authenticationGateRejectsAccountWithoutProfilesBeforeBinding` | 8 | 8 falhas: esperado403, recebido404/405. |
| `authenticationGateAllowsCurrentAuthorizedProfilesToReachRouting` | 12 | 12 aprovadas com404/405 provisórios. |

**Causa funcional confirmada**: a cadeia existente protege somente POST `/orders`; GET/HEAD continuam no fallback público, chegando ao roteamento. Lista responde405 por só existir POST; detalhe responde404 por não haver rota. Isso ocorre tanto sem/com credencial inválida quanto sem perfil e também com entrada malformada. A nova barreira401/403 está ausente. Todas as96 falhas XML são da asserção numérica de status `authentication gate status`, sem falha de preparação ou dos controles autorizados. O backend não foi alterado para produzir esse RED; T006/T007 tratarão a fundação produtiva em escopo posterior autorizado.

Contagem real: **120 requisições GET/HEAD (60 GET e60 HEAD) +10 POST /login =130 requisições HTTP reais**;120 invocações JUnit parametrizadas, não130 testes. Todos os HEAD atuais retornaram corpo vazio, ainda com status404/405. Foram observados60 status404 e60 status405 nas requisições de cenário. **Zero mocks e zero dispatches MockMvc**: o construtor herdado da fixture monta MockMvc, mas seu helper `login()` não foi chamado. Persistência e remoção de contas são setup JDBC real; tokens expirada/assinatura inválida são dados de credencial de teste. Nenhum teste de suporte T003/T004, B0 ou suíte completa foi executado novamente.

### Preservação, auditoria e limites

Evidência própria em `target/feature007-validation/t005-f0-red-20261020/`: comando/ambiente/HEAD/status em `session-start.json`, diff recebido, cópias dos oito fontes usados e dos documentos recebidos, manifestos, `console.log` e `result.json`. Antes do Maven, os dois relatórios recebidos da T004 foram movidos para `received-surefire-reports/` nesse diretório inédito: origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita, tamanhos/caminhos/SHA-256 conferidos após mover (`archive-preservation.json`:2, HashesMatch=true). Os quatro relatórios novos (dois XML/dois TXT) permanecem em `target/surefire-reports/` e na cópia `reports/` da T005.

Auditoria realmente executada na raiz por `& 'target/feature007-validation/t005-f0-red-20261020/audit-red.ps1'`: **exit0 da auditoria, ExpectedRedConfirmed=true e TestsPassed=false** em `audit-red.json`. Esse exit0 é da conferência das evidências, não aprovação do Maven. Confirmados exit1/BUILD FAILURE,120 casos únicos,96 falhas de status,24 controles aprovados, zero erros/ignorados, métodos das duas classes, matriz completa contexto/credencial/método/caminho e totais HTTP. `http-cases.json` preserva os120 casos e status reais sem tokens. Contextos têm URLs H2 e portas distintas, cinco logins reais em cada; o suporte T003/T004 e produção não foram alterados. Oito fontes conferem com as cópias executadas;265 rastreados estavam idênticos antes deste fechamento documental e1047 arquivos de evidência prévia continuam íntegros.

**Resultado da tarefa**: T005 atende à escrita dos testes e observação do F0-RED causal. A proteção da007 continua ausente e os testes permanecem vermelhos; não há GREEN de segurança, sucesso de lista/detalhe, prova completa de titularidade/imutabilidade ou conclusão da US3/feature. Os controles404/405 não comprovam autenticação efetivamente aplicada ao GET/HEAD. Mantidos todos os limites do snapshot composto, sem outros escritores durante a janela, sem promessa de snapshot global atômico ou detecção de JDBC transitório restaurado. T006–T034 permanecem abertas; sem produção nova, CI, nova validação funcional001–006, `clean`, cache/ordem/seed alterados, stage, commit, push ou mudança de main. A005 continua **EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV**.

### Fechamento da T005

Revisados o diff em relação aos documentos recebidos e os três Java novos. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, índice vazio. Whitespace dos três novos não rastreados conferido individualmente por `git diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico de whitespace, exit1 pela diferença esperada de arquivo novo.39 links locais conferidos. `final-review.ps1`: exit0 da revisão documental/de preservação, Pass=true, ExpectedRedConfirmed=true, ApplicationTestsPassed=false e MavenExitCode=1 em `final-review.json`. Prefixo recebido do quickstart de52473 bytes intacto; tasks recebida difere somente por T005;263 outros rastreados e1047 evidências prévias com hashes preservados; os oito fontes e relatórios finais mantidos. Hooks posteriores ausentes (`.specify/extensions.yml` não existe). Estado: **T001–T005 concluídas;29 tarefas abertas (T006–T034)**. Git: somente quickstart/tasks modificados, oito Java não rastreados (cinco recebidos e três novos), nada staged. Branch/HEAD/main e seleção007 preservados, sem commit/push. Sem impedimento de preparação; os96 testes de rejeição continuam falhando pela proteção ausente, a ser implementada em tarefas posteriores.

## T006 — Identidade e perfis atuais para leitura — 2026-10-20

**Escopo executado**: exclusivamente T006. Conferência inicial: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, seleção local Feature007; somente este quickstart e tasks modificados, oito Java não rastreados e nada staged. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Antes de alterar fontes, os oito caminhos recebidos e seus SHA-256 foram registrados em `target/feature007-validation/t006-components-20261020/received-java.json`:

```text
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryDatabaseProbeTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestFixtureTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestObservationConfig.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryTestFixture.java
```

Esses oito arquivos, suas asserções e as evidências T001–T005 permanecem intactos. Preservados o erro de compilação histórico da T003, seus resultados posteriores e os limites do snapshot composto da T004. O **F0-RED histórico da T005 permanece BUILD FAILURE/exit1,120 testes,96 falhas funcionais esperadas e24 controles aprovados, zero erros/ignorados**; não foi executado novamente nem reclassificado como GREEN.

### Componentes e contratos internos

Criados [OwnOrderQueryAccessService](../../src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessService.java), [OwnOrderQueryJwtAuthenticationConverter](../../src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverter.java) e os testes [OwnOrderQueryAccessServiceTest](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessServiceTest.java) e [OwnOrderQueryJwtAuthenticationConverterTest](../../src/test/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverterTest.java). Testes complementares escritos antes dos componentes, sem executar ausência de classe como RED.

O adaptador chama o `OrderJwtAuthenticationConverter` existente: conserva suas verificações rigorosas de tempos, subject textual canônico, payload original e lookup inicial; não copia parser nem altera decoder/conversor global. Depois, substitui integralmente as authorities anteriores pelo resultado de `findEffectiveAccountRolesById`. Nome/perfis do JWT e authorities provenientes da coleção gerenciada anterior não concedem acesso. CLIENT, ADMIN isolado e ambos são suficientes; nenhum perfil autoriza compras de outra conta.

O serviço usa exclusivamente a projeção escalar existente, com seu left join e flush mode COMMIT preservados. Resultado vazio lança `BadCredentialsException`; conta existente sem CLIENT/ADMIN lança `AccessDeniedException`; falha de lookup/mapeamento lança `OrderAccountResolutionException` com mensagem fixa e causa interna preservada. São categorias destinadas a401/403/500, **ainda sem tradução HTTP da007**. As duas etapas de lookup preservam a distinção entre ausência e falha interna. A T007 deverá integrar essas categorias, inclusive a falta de perfil durante a conversão, aos handlers de leitura; esta tarefa não afirma que o filtro já as trata.

`resolve(principal)` e a guarda pública `requireAccountId(authentication)` têm `@Transactional(readOnly=true)`. A guarda rejeita autenticação ausente, não autenticada, anônima ou principal sem ID positivo, antes do repositório; reconsulta existência/perfis a cada chamada e devolve exclusivamente o ID do principal. A futura integração no serviço de consulta pertence à T014. Nenhuma leitura de pedido, controller, DTO, cadeia HTTP ou handler foi antecipado.

### Validação executada e seleção efetiva

Aplicada a skill `speckit-implement` somente à T006; tarefa, dependência T005 e artefatos pertinentes conferidos. Prerequisitos do Spec Kit: exit0, feature007. Checklist de requisitos somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente. Java21.0.12.1/Azul Zulu e Wrapper Maven3.9.16 existentes; nenhum Java/Maven ativo antes de cada execução. Sem `clean`, baseline, T003–T005, F0-GREEN ou suíte completa.

Primeira execução, na raiz:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest' test
```

Início `2026-10-20T04:18:19.4117559-03:00`, término `2026-10-20T04:19:39.9479979-03:00`, processo80,536s. **BUILD FAILURE, exit1;42 testes,0 falhas,2 erros,0 ignorados**. Console/XML/TXT confirmam somente as duas classes:26 casos do conversor aprovados;16 do serviço,14 aprovados e2 erros. Os erros são `UnfinishedStubbingException` em `leftJoinRowWithoutRoleIsDeniedRatherThanAbsent` e `guardUsesPrincipalAndRechecksEachCallDespiteTokenAuthorities`: o helper criava/stubava outro mock dentro de um `thenReturn` ainda aberto. Isso é erro de preparação Mockito, **não RED funcional nem falha de compilação**. Os dois componentes compilaram.

Correção restrita ao teste do serviço: criar as projeções simuladas em variáveis locais antes de iniciar o stubbing do repositório. Nenhuma asserção, valor esperado, componente produtivo ou teste do conversor mudou. Após terminar a primeira execução, repetida somente a classe afetada:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryAccessServiceTest' test
```

Início `2026-10-20T04:20:42.1816359-03:00`, término `2026-10-20T04:21:43.0197238-03:00`, processo60,838s. **BUILD SUCCESS, exit0;16 testes,0 falhas,0 erros,0 ignorados**. Console/XML/TXT confirmam somente `OwnOrderQueryAccessServiceTest`, inclusive as três combinações de perfis e sete invocações de identidade inválida; nenhum teste aninhado ou contêiner adicional. A configuração estática interna do proxy não contém testes JUnit. O conversor aprovado não foi repetido.

Conciliação: **42 casos distintos da T006 aprovados entre as execuções:26 do conversor na primeira +16 do serviço na repetição**. Não houve uma execução única GREEN de42. Foram58 invocações efetivas somando as duas execuções; os dois erros históricos de preparação permanecem registrados. O teste do conversor e os componentes produtivos continuam idênticos aos usados nos26 casos aprovados; a única diferença no teste do serviço foi a montagem dos mocks.

### Alcance das provas e preservação dos relatórios

Os testes do serviço simulam o repositório/projeções e verificam roles atuais, ausência/sem perfil/falha interna, projeção incoerente, resultado imutável e rechecagem após revogação/remoção simuladas. O teste transacional abre seu próprio ApplicationContext mínimo e H2 com UUID exclusivo, sem seeds/esquema compartilhados, usando `DataSourceTransactionManager` real: ambas as entradas públicas passam por proxy Spring e encontram transação ativa/readOnly no lookup simulado; o banco é encerrado após o teste. Isso **não executa consulta JPA**, não prova coleção gerenciada obsoleta real e não transforma readOnly em garantia universal de ausência de escrita. A prova JPA específica continua na T023.

O teste do adaptador executa o conversor rigoroso existente com relógio fixo e fixtures de JWT assinado com chave descartável; simula os dois serviços de lookup. Confirma substituição de authorities, concessão sobre coleção anterior vazia, rejeição apesar de ADMIN anterior, preservação de categorias de exceção e rejeição de subject/tempos inválidos antes dos lookups, inclusive subject originalmente numérico normalizado. **Não executa decoder ou verificação de assinatura**; assinatura aqui prepara payload para o parser existente. Nas duas execuções: zero MockMvc, zero HTTP real, zero login e zero consultas JPA. O H2 do proxy fornece conexão/transação real, sem persistir contas/pedidos. Mocks não substituem F0/US3 HTTP.

Evidências separadas em `target/feature007-validation/t006-components-20261020/` e `target/feature007-validation/t006-access-retry-20261020/`: comandos/ambiente/HEAD/status, diff recebido, cópias dos12 fontes, documentos recebidos, manifestos, console, exit code e relatórios. Antes do primeiro Maven, os quatro relatórios recebidos da T005 foram movidos para `received-surefire-reports/` do primeiro diretório; antes da repetição, os quatro relatórios da primeira T006 foram movidos para a área correspondente do segundo. Origem/destino absolutos previamente conferidos dentro de target, sem sobreposição/sobrescrita; hashes conferidos após mover. Os relatórios de cada execução também têm cópias próprias em `reports/`; os dois XML/TXT da repetição permanecem em `target/surefire-reports/`.

Auditorias executadas na raiz por `& 'target/feature007-validation/t006-components-20261020/audit.ps1'` e `& 'target/feature007-validation/t006-access-retry-20261020/audit.ps1'`: ambas exit0 da **conferência**, sem alterar os resultados Maven. Primeira: `ComponentTestsPassed=false`, `FunctionalRed=false`, exit Maven1;265 rastreados e1080 evidências prévias preservados. Repetição: `ComponentTestsPassed=true` para os16 selecionados, exit Maven0;265 rastreados e1116 evidências prévias preservados. As contagens, classes e invocações XML/TXT foram conciliadas com console; oito Java recebidos intactos e cópias dos12 fontes conferidas por SHA-256.

**Fronteira**: T006 prepara a identidade/guarda e distingue as exceções. `SecurityConfig`, políticas de POST `/orders`, catálogo, detalhe público, login e `/admin/**` permanecem byte a byte inalterados. A cadeia exclusiva GET/HEAD e tradução segura401/403/500, Bearer challenge e HEAD pertencem à T007; o F0-GREEN e regressões diretamente afetadas pertencem à T008. Não há nova aprovação HTTP, prova funcional de lista/detalhe, conclusão da US3 ou da feature. Mantidos os limites da T004 e a ausência de garantia de serialização de revogação concorrente posterior à leitura autorizadora. A005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

### Fechamento da T006

Revisados o diff documental, os quatro Java novos e a correção de preparação entre as duas execuções. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, índice vazio. Whitespace dos quatro novos conferido individualmente com `git diff --no-index --check -- /dev/null <arquivo>`: sem erro de whitespace, exit1 pela diferença esperada de arquivo novo.43 links locais conferidos. `& 'target/feature007-validation/t006-access-retry-20261020/pre-final-review.ps1'`: exit0, Pass=true; prefixo recebido de62845 bytes do quickstart intacto, tasks recebida difere somente por T006,263 outros rastreados e1116 evidências anteriores preservados. Conferidos os12 fontes, os oito recebidos e a identidade do teste do conversor/produção entre as execuções; a correção do teste do serviço mantém todas as asserções.

Estado: **T001–T006 concluídas;28 tarefas abertas, T007–T034**. Somente quickstart/tasks modificados e12 Java não rastreados: oito recebidos intactos e quatro novos da T006. Nada staged; sem commit, push, CI, alteração de branch/HEAD/main ou seleção local. Sem impedimento da T006. A proteção HTTP continua pendente de T007/T008; os96 RED funcionais da T005 permanecem evidência histórica de proteção ausente, sem repetição ou presunção de aprovação. O erro inicial de preparação da T006 está preservado separadamente e sua correção foi validada pela repetição focal.

## T007 — Cadeia e handlers exclusivos das consultas — 2026-10-20

**Escopo executado**: somente T007, após T006. Estado inicial conferido: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, seleção local Feature007; apenas quickstart/tasks modificados, nada staged e exatamente12 Java não rastreados. Seus caminhos e SHA-256 foram registrados antes das alterações em `target/feature007-validation/t007-security-config-20261020/received-java.json`:

```text
src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverter.java
src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessService.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryDatabaseProbeTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestFixtureTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestObservationConfig.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryTestFixture.java
src/test/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverterTest.java
src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessServiceTest.java
```

Os12 arquivos recebidos e suas asserções permanecem inalterados. Preservadas as evidências T001–T006: F0-RED HTTP da T005 com120 testes,96 falhas funcionais esperadas e24 controles aprovados, sem erros/ignorados; primeira T006 com42 casos e2 erros de preparação Mockito, exit1;26 casos do conversor aprovados nessa execução e16 casos do serviço aprovados na repetição distinta, exit0. Não houve uma execução única GREEN de42 na T006. Nenhum desses grupos foi repetido nesta tarefa nem seus resultados reclassificados como aprovação da configuração nova. Preservados também a falha de compilação histórica da T003 e os limites do snapshot composto da T004.

### Implementação restrita

Acrescentados em [SecurityConfig](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) o bean do adaptador JWT recebido e a cadeia `ownOrderQuerySecurityFilterChain`, `@Order(0)`. Seu matcher contém apenas GET/HEAD de `/orders` e `/orders/*`, relativo ao servlet/context path, inclusive segmento de ID malformado. Requer CLIENT ou ADMIN, usa sessão STATELESS, decoder existente e adaptador da T006, sem ampliar o privilégio de ADMIN. Não adiciona operações de negócio ou consultas a pedidos. As cadeias1/2/3, incluindo todos os seus métodos de configuração, permanecem textualmente idênticas às recebidas; o diff de produção existente contém somente imports e os dois métodos novos. POST `/orders`, `/admin/**`, fallback público, catálogo, detalhes e login mantêm suas cadeias/políticas anteriores.

Criados [OwnOrderQueryAuthenticationEntryPoint](../../src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAuthenticationEntryPoint.java) e [OwnOrderQueryAccessDeniedHandler](../../src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAccessDeniedHandler.java). O entry point escreve401 fixo com `WWW-Authenticate: Bearer`; falha interna de resolução envolvida por `OAuth2AuthenticationException` mantém sua causa tipada e produz500 fixo. Falta de perfil lançada durante a conversão é envolvida com categoria `insufficient_scope` e causa `AccessDeniedException`; o entry point encaminha essa categoria ao handler403. Esse tratamento é necessário porque a conversão ocorre antes de `ExceptionTranslationFilter`; não deixar a falta de perfil escapar como erro genérico ou virar401. O mesmo handler é configurado para negação na autorização.

Respostas GET usam exatamente `{status,error,message,path}` e as mensagens do contrato: `Authentication required or invalid`, `Order query not permitted`, `Order query could not be completed`. URI mantém context path e exclui query; não são serializadas mensagens/campos das exceções ou seus diagnósticos. HEAD recebe status/headers correspondentes sem escrever corpo nos handlers. Decoder, conversor rigoroso original, serviço de acesso da T006, entidades, repositórios e handlers das operações anteriores não foram alterados.

### Validação focal da configuração

Criado [OwnOrderQuerySecurityConfigurationTest](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQuerySecurityConfigurationTest.java), específico da T007, antes da implementação produtiva, sem executar ausência de classe como RED. Usa contexto web mínimo com `@EnableWebSecurity` e `SecurityConfig` reais, serviços de lookup e decoder simulados. Confere os objetos das quatro cadeias construídas pelo Spring, seus matchers e respectivos AuthorizationManagers; acessa o AuthenticationManager configurado no filtro Bearer para chamar o provider diretamente e verificar o envolvimento das exceções, sem executar o filtro ou despachar requisição. Usa MockHttpServletRequest/Response para chamadas diretas aos handlers. **Não usa MockMvc, servidor embutido, HTTP real, JDBC/H2 ou JPA.**

Aplicada a skill `speckit-implement` limitada à T007: tarefa/dependências e artefatos pertinentes conferidos; `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes. `.specify/extensions.yml` ausente, sem hooks prévios. Java21.0.12.1/Azul Zulu e Wrapper Maven3.9.16 confirmados. Nenhum Java/Maven ativo antes da execução.

Único comando Maven, executado sequencialmente na raiz:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQuerySecurityConfigurationTest' test
```

Início `2026-10-20T04:31:19.4280752-03:00`, término `2026-10-20T04:32:34.0408323-03:00`, processo74,613s, tempo Maven01:08. **BUILD SUCCESS, exit0;58 testes,0 falhas,0 erros,0 ignorados.** Console, XML e TXT conciliados: somente a classe acima, cinco métodos parametrizados, sem classes aninhadas de teste, contêineres agregadores ou duplicação. A configuração estática interna não contém testes JUnit.

| Método | Invocações | Escopo comprovado nesta execução |
| --- | --- | --- |
| `selectsFirstChainForOnlyQueryMethodsAndSingleSegment` | 12 | GET/HEAD de lista, ID e ID malformado selecionam a primeira cadeia; campos de contexto raiz e `/api` simulados. |
| `preservesExistingChainSelectionAndPublicFallback` | 2 | Ordem0/1/2/3; POST/orders e admin selecionam suas cadeias; catálogo/detalhe/login, métodos fora do recorte e caminho mais profundo conservam fallback. |
| `configuresQueryAndExistingAuthorizationPolicies` | 10 | CLIENT, ADMIN, ambos, nenhum e AUDITOR; leitura/POST requerem CLIENT ou ADMIN, admin requer ADMIN e fallback permite acesso. |
| `configuredProviderKeepsLookupFailuresDistinct` | 10 | Falha de credencial/conta e falha interna nos lookups inicial/escalar; falta de perfil no escalar;401/403/500 e causas preservadas, saída fixa por chamada direta ao handler. |
| `writesFixedErrorsAndSuppressesHeadBodyWithoutDispatch` | 24 | Três status, GET/HEAD, lista/ID malformado e campos de contexto raiz/API; envelope exato, challenge somente401, path sem query e HEAD vazio, inclusive negação direta pelo handler. |

Foram34 ApplicationContexts mínimos independentes nos testes de configuração e24 invocações de handlers sem contexto Spring. `/api` é valor de contexto/caminho colocado nos requests simulados, **não implantação HTTP em `/api`**. Decoder e lookups são mocks. O JWT decodificado de preparação tem tempos/subject válidos e payload compacto não assinado; não prova assinatura/decoder/login reais. Contagem:58 invocações JUnit, zero dispatches servlet/MockMvc, zero requisições HTTP, zero conexão H2/JDBC e zero consulta JPA. Nenhum banco/contexto de persistência anterior é inicializado ou compartilhado.

Não executados T008/F0-GREEN, baseline B0, grupos T003–T006 ou suíte completa. Compilar todos os fontes existentes não equivale a executar seus testes. As asserções HTTP já recebidas permanecem intactas para o checkpoint autorizado posterior.

### Evidências e fronteira de aprovação

Diretório próprio `target/feature007-validation/t007-security-config-20261020/`: inventário inicial, documentos/segurança recebidos, comando/ambiente/HEAD/status, diff, cópias dos16 fontes considerados (12 recebidos,3 novos e SecurityConfig), manifestos, `console.log`, `result.json`, XML/TXT e auditorias. Antes do Maven, os dois relatórios recebidos da repetição T006 foram movidos de `target/surefire-reports/` para `received-surefire-reports/` neste diretório inédito. Origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita; caminhos/tamanhos/SHA-256 conferidos após mover. Os dois XML/TXT novos permanecem em `target/surefire-reports/` e na cópia `reports/` própria.

Auditoria executada por `& 'target/feature007-validation/t007-security-config-20261020/audit.ps1'`: exit0 da conferência, Pass=true e ConfigurationTestsPassed=true, FeatureHttpGreenProven=false. Confirmados seleção58/5 métodos, Mavenexit0,12 Java recebidos e16 fontes idênticos às cópias usadas,262 outros rastreados e1157 arquivos de evidência anterior íntegros, métodos das cadeias1/2/3 preservados e relatórios mantidos. Resultado da T007: configuração/provider/handlers validados no alcance descrito, sem alegar aprovação HTTP efetiva.

**Limites**: a cadeia está implementada, mas a aplicação completa ainda não foi exercitada nesta revisão com decoder/banco/JWT/filtros/transporte reais. Não foi executada novamente a matriz F0-RED e não se presume que os96 casos HTTP estejam GREEN; a comprovação comum raiz/API e regressões diretamente afetadas pertence à T008. Não há lista/detalhe funcional, prova de titularidade de pedidos, validação de falhas de consulta/conclusão transacional ou conclusão da US3/feature. Guarda no serviço, cenários JPA com coleção obsoleta e demais histórias continuam em suas tarefas posteriores. Nenhuma nova política para rotas fora do recorte, promessa de revogação serializada ou garantia de escrita baseada apenas em readOnly. A005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

### Fechamento da T007

Revisados diff de produção/documentação, três Java novos, referências e47 links locais. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, nada staged. Os três Java novos tiveram whitespace conferido individualmente por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico, exit1 pela diferença de arquivo novo; a opção é local ao comando e não altera configuração do Git. Quickstart recebido de74787 bytes preservado como prefixo; tasks difere do recebido somente pela marca T007. Os12 Java recebidos, asserções e1157 evidências anteriores permanecem íntegros; nenhuma aprovação histórica foi reescrita. Hooks posteriores ausentes (`.specify/extensions.yml` não existe).

Estado: **T001–T007 concluídas;27 tarefas abertas, T008–T034**. Três rastreados modificados: SecurityConfig, quickstart e tasks. Quinze Java não rastreados:12 recebidos intactos e3 novos (dois handlers e teste de configuração). Branch/HEAD/main e seleção007 preservados; sem stage, commit, push ou CI. Sem impedimento da T007 dentro da validação de configuração descrita. O checkpoint HTTP completo e suas regressões permanecem pendentes da T008; esta tarefa não presume resolução dos96 RED HTTP anteriores nem conclusão funcional da007.

## T008 — F0-GREEN conjunto de segurança e regressões — 2026-10-20

**Escopo executado**: exclusivamente T008, dependente de T006/T007 concluídas. Conferência inicial: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `b7d010302715076fa848f85602376ad390c080a1`, seleção local Feature007 e nada staged. Somente SecurityConfig, quickstart e tasks eram rastreados modificados. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Os15 Java não rastreados recebidos foram conferidos e registrados com SHA-256 antes da execução em `target/feature007-validation/t008-f0-green-20261020/received-java.json`:

```text
src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAccessDeniedHandler.java
src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryAuthenticationEntryPoint.java
src/main/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverter.java
src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessService.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryDatabaseProbeTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQuerySecurityConfigurationTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestFixtureTest.java
src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryTestObservationConfig.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java
src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryTestFixture.java
src/test/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverterTest.java
src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessServiceTest.java
```

Nenhum fonte, configuração ou asserção foi alterado na T008. Foram preservados T001–T007 e todas as evidências: B0 recuperada sem repetição na retomada; falha de compilação histórica da T003; F0-RED da T005 com120 testes,96 falhas funcionais esperadas e24 controles aprovados; erro de preparação Mockito da primeira T006 e aprovações históricas dos26 casos do conversor e16 do serviço em execuções distintas;58 testes de configuração da T007. Esses resultados históricos continuam separados desta execução atual.

### Execução exata autorizada

Aplicada a skill `speckit-implement` limitada à T008; tarefa integral, dependências, seleção e protocolo de preservação conferidos com os artefatos pertinentes. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente. Java21.0.12.1/Azul Zulu e Wrapper Maven3.9.16 existentes; nenhum Java/Maven ativo antes da execução.

Único comando Maven realmente executado na raiz, sequencialmente, com a seleção exata da T008:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQuerySecurity*#authenticationGate*,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OrderSecurityIntegrationTest*,PublicCatalogNameFilterIntegrationTest*' test
```

Início `2026-10-20T04:37:44.1373724-03:00`, término `2026-10-20T04:39:36.9367327-03:00`, processo112,799s, tempo Maven01:47. **BUILD SUCCESS, exit0;431 testes,0 falhas,0 erros,0 ignorados.** Não houve correção, segunda execução, B0 separada, suíte completa, `clean`, mudança de cache/ordem/seeds ou T009+. As269 regressões previstas integram este F0-GREEN para comparação após a cadeia nova; não constituem outra execução de baseline.

### Seleção efetiva e ausência de omissões

| Classe/contexto Surefire | Métodos distintos | Invocações aprovadas |
| --- | --- | --- |
| `OrderSecurityIntegrationTest` | 8 | 185 |
| `OwnOrderQuerySecurityApiHttpIntegrationTest` | 3 | 60 |
| `OwnOrderQuerySecurityHttpIntegrationTest` | 3 | 60 |
| `PublicCatalogNameFilterIntegrationTest$ApiContext` | 11 | 42 |
| `PublicCatalogNameFilterIntegrationTest$RootContext` | 11 | 42 |
| `PublicCatalogNameFilterIntegrationTest` (contêiner externo vazio) | 0 | 0 |
| `OwnOrderQueryJwtAuthenticationConverterTest` | 5 | 26 |
| `OwnOrderQueryAccessServiceTest` | 8 | 16 |
| **Total, sem duplicatas** | **49 por classe concreta** | **431** |

Console, oito XMLs e oito TXTs conciliados. Sete classes concretas com testes e um contêiner externo de zero testes, sem somar novamente os aninhados. Identidades `classe|nome da invocação` comparadas com269 casos de `executed-cases.json` da B0,120 casos dos XMLs F0-RED e os26/16 casos dos relatórios pertinentes da T006: **431 esperados e executados, zero ausentes, inesperados ou duplicados**. Fontes de testes da B0 e os oito fontes de suporte/HTTP usados na T005 continuam com os mesmos hashes, preservando todas as asserções. Os42 casos complementares agora aprovam conjuntamente nesta execução atual; isso não altera os resultados separados anteriores da T006.

`OwnOrderQuerySecurityConfigurationTest` não executou seus58 casos: embora o nome entre no padrão de classes, seus métodos não são `authenticationGate*`. Isso corresponde à restrição expressa do seletor, sem omissão dos métodos exigidos. T003/T004 e testes futuros também não foram executados.

### Evolução das mesmas asserções HTTP

Reutilizados, sem alterações, [OwnOrderQueryHttpTestSupport](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java), [contexto raiz](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java) e [contexto API](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java). Contextos Spring completos, HTTP real em portas aleatórias, filtros/encoder/decoder/JWT reais, OSIV false e H2 exclusivos/estáveis por contexto. Setup/login concluídos antes da observação, sem transação externa ao HTTP; limpeza depois das janelas.

| Contexto real | Porta observada | Banco observado | Logins HTTP | GET/HEAD |
| --- | --- | --- | --- | --- |
| raiz | 51429 | `admin-product-test-44182cf6-9127-4a12-af05-beb0731df822` | 5 | 60 |
| `/api` | 51427 | `admin-product-test-6cdf9bca-fbb2-4478-ad36-2dc8007e0edb` | 5 | 60 |

Os bancos têm URLs `jdbc:h2:mem:` distintas, confirmadas por conexão/ambiente. Contas/perfis são persistidos por JDBC commitado. Cada contexto faz login HTTP real para CLIENT, ADMIN, ambos, nenhum e outra conta removida após login. O decoder real confirma tokens frescos/identidade e rejeita as fixtures de assinatura inválida, malformada e expirada; o token da conta removida conserva assinatura e vigência válidas, com ausência efetivamente commitada no banco.

Em **cada contexto**, GET e HEAD de `/orders?page=0&size=2`, `/orders?page=not-an-integer&size=0`, `/orders/1` e `/orders/not-an-integer` mantiveram exatamente os mesmos casos, credenciais e expectativas da T005:

| Método compartilhado | Casos por contexto | F0-RED histórico | F0-GREEN atual |
| --- | --- | --- | --- |
| `authenticationGateRejectsMissingInvalidExpiredAndRemovedAccounts` | 40 | 40 falhas de status404/405 | 40 aprovados com401 |
| `authenticationGateRejectsAccountWithoutProfilesBeforeBinding` | 8 | 8 falhas de status404/405 | 8 aprovados com403 |
| `authenticationGateAllowsCurrentAuthorizedProfilesToReachRouting` | 12 | 12 controles aprovados com404/405 | mesmos12 controles aprovados e mesmos status404/405 |

Conciliação total: **96 rejeições anteriormente vermelhas agora aprovadas,24 controles preservados**. Foram80 respostas401 e16 respostas403; as24 autorizadas resultaram em12 respostas404 e12 respostas405. `red-green-evolution.json` registra cada um dos120 pares contexto/credencial/método/caminho, expectativa e status antes/depois. Os controles não foram ampliados ou relaxados.

As rejeições atuais passaram também pelas asserções posteriores que não haviam sido alcançadas no RED: challenge exatamente `Bearer` nas80 respostas401; JSON GET exatamente com quatro campos, status/error/message do contrato e URI sem query, incluindo `/api`; corpo vazio nas48 rejeições HEAD. Todos os60 HEAD, incluindo controles, ficaram sem corpo. As48 rejeições GET não expõem campos de compra/conta, JWT, hash ou diagnóstico. Houve401/403 inclusive com paginação ou segmento de ID malformados, sem conceder acesso por entrada inválida.

**Distinção de operação**: com CLIENT, ADMIN isolado ou ambos, GET/HEAD da lista ainda respondem405 porque só há POST; detalhe ainda responde404 por rota ausente. Esses controles provam passagem da barreira de segurança conforme o F0, não sucesso200, paginação, binding, resposta de detalhe ou404 de titularidade. Não foi criada listagem/detalhe para obter aprovação. A prova comum de segurança não conclui a US3 nem a feature.

### Regressões existentes, inclusive os aninhados

As mesmas asserções de [OrderSecurityIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderSecurityIntegrationTest.java) aprovaram185 invocações. Métodos efetivamente executados:

| Método | Invocações |
| --- | --- |
| `shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims` | 9 |
| `shouldRecheckCurrentAccountAndProfilesThroughRealServiceProxy` | 8 |
| `shouldRejectInvalidIdentityThroughRealServiceProxy` | 5 |
| `shouldForbidAccountWithoutProfilesEvenWithAdminClaim` | 6 |
| `shouldReturnInternalErrorForResolutionFailureBeforeReadingBody` | 6 |
| `shouldRecheckAccountAndProfilesAfterFilterBeforeService` | 12 |
| `shouldKeepRealAuthenticationAndConfirmationWithServletContextPath` | 1 |
| `shouldRejectInvalidJwtBeforeReadingBody` | 138 |

Os dois aninhados de [PublicCatalogNameFilterIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java), `$RootContext` e `$ApiContext`, aprovaram42 casos **cada**, com os mesmos11 métodos herdados:

| Método | Invocações em cada contexto |
| --- | --- |
| `preservesPublicPaginationShapeAndAccess` | 1 |
| `preparesAnIsolatedCommittedCatalogWithoutNameFilter` | 1 |
| `matchesNameSubstringCaseAccentsAndLiterals` | 5 |
| `preservesInternalSpacesAndStoredNames` | 3 |
| `returnsCoherentEmptyFilteredPages` | 7 |
| `characterizesExistingPublicPaginationHttp` | 1 |
| `paginatesFilteredResultsInStoredNameIdOrder` | 1 |
| `preservesDetailsAndLegacyIndependentlyOfFilter` | 1 |
| `doesNotRetainNameBetweenRequests` | 1 |
| `normalizesOnlyExternalFilterWhitespace` | 19 |
| `acceptsShortAndLongNameFilters` | 2 |

Assim, a alteração de segurança mantém aprovadas as269 regressões diretamente previstas, incluindo autenticação/perfis atuais/rechecagem/precedência e confirmação do POST, acesso público com Bearer irrelevante, filtro literal/case/acentos/espaços, paginação, representação e detalhes públicos no alcance dessas suítes. Não é execução de todas as Features001–006 ou de toda a política administrativa.

### Métricas de transporte e limites das provas

No segmento007: **120 requisições GET/HEAD (60 de cada método) +10 POST/login =130 requisições HTTP reais**,120 invocações JUnit, zero mocks e zero dispatches MockMvc. O construtor da fixture monta suporte MockMvc, mas seu helper de login não é chamado nesses testes. A classe legada de segurança185 usa MockMvc com filtros reais e chamadas diretas pelo proxy; são dispatches internos, não transporte HTTP real, e185 testes não significam185 dispatches. Os84 casos do catálogo usam HTTP real adicional nos dois contextos, com múltiplas requisições em alguns casos. Não há contador global instrumentado dessas duas suítes antigas; não atribuir130 como total HTTP das431 invocações nem inventar um total global de dispatches.

Os [26 casos do conversor](../../src/test/java/com/devsuperior/dscommerce/security/OwnOrderQueryJwtAuthenticationConverterTest.java) e [16 casos do acesso](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryAccessServiceTest.java) são componentes com lookups simulados. O conversor rigoroso real usa fixtures de JWT sem validar assinatura pelo decoder nesses testes; a prova de decoder/filtros reais vem do segmento HTTP007. O acesso verifica transação readOnly via proxy Spring em H2 próprio, com repositório simulado; não executa a projeção JPA real nesse teste unitário.

As120 janelas HTTP passaram por snapshots independentes e observação Hibernate, preservando os limites da T004: snapshot composto usa duas transações, não há garantia global atômica concorrente, StatementInspector não vê JDBC direto e igualdade antes/depois não detecta escrita JDBC transitória restaurada. F0 prepara contas, não compras; essas provas de janela não comprovam imutabilidade de futuras respostas200. Falhas500 da leitura, count/fetch/mapeamento/conclusão transacional e coleção JPA obsoleta continuam para as tarefas próprias;500 da007 não foi injetado por HTTP neste checkpoint. Permanecem pendentes consultas funcionais, titularidade de dados/count/detalhe e demais histórias. A005 continua EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

### Evidências e auditoria

Diretório próprio `target/feature007-validation/t008-f0-green-20261020/`: inventário inicial dos15 Java, documentos recebidos, comando/ambiente/HEAD/status, diff, cópias dos18 fontes considerados, manifestos, `console.log`, `result.json`,16 relatórios novos, `executed-cases.json`, `http-cases.json` e `red-green-evolution.json`. Antes do Maven, os dois relatórios recebidos da T007 foram movidos de `target/surefire-reports/` para `received-surefire-reports/` neste destino inédito: caminhos absolutos previamente verificados dentro de target, sem sobreposição/sobrescrita; caminhos/tamanhos/SHA-256 conferidos após mover. Os16 relatórios novos permanecem em `target/surefire-reports/` e na cópia `reports/` própria.

Auditoria executada na raiz por `& 'target/feature007-validation/t008-f0-green-20261020/audit.ps1'`: exit0 da conferência, Pass=true, Mavenexit0 e431 casos aprovados. Confirmados seleção exata, igualdade das identidades dos casos e das matrizes antes/depois,96 rejeições evoluídas e24 controles preservados, aninhados incluídos,0 omissões/duplicatas,15 Java recebidos e18 fontes idênticos,265 rastreados ainda intactos antes do fechamento documental e1197 arquivos de evidência anterior preservados. Nenhum ajuste de produção/teste foi necessário; somente quickstart e marcador T008 são atualizados nesta tarefa.

### Fechamento da T008

Revisados diff documental, comandos, matrizes, referências e54 links locais. Os15 Java não rastreados recebidos foram conferidos quanto à preservação/hashes e whitespace; nenhum Java foi criado ou alterado nesta tarefa. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, índice vazio. Conferência individual dos15 por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico de whitespace, exit1 pela diferença de arquivo não rastreado; opção restrita ao comando, sem alteração da configuração Git. Prefixo recebido do quickstart de86709 bytes preservado; tasks recebida difere somente por T008. Mantidos os263 outros rastreados, incluindo SecurityConfig recebido, os15 Java e1197 arquivos de evidência anteriores. Hooks posteriores ausentes (`.specify/extensions.yml` não existe).

Estado: **T001–T008 concluídas;26 tarefas abertas, T009–T034**. A T008 alterou somente quickstart e tasks; no Git permanecem três rastreados modificados (incluindo SecurityConfig recebido) e15 Java não rastreados recebidos, nada staged. Branch/HEAD/main e seleção007 preservados; sem commit, push ou CI. Checkpoint comum F0-GREEN/C1 atendido, sem impedimento ou correção necessária. Lista/detalhe e demais provas funcionais permanecem nas tarefas posteriores, sem aprovação presumida da US3/feature ou ação automática de commit.

## T009 — Escrita dos testes HTTP de lista — 2026-10-20

**Escopo executado**: exclusivamente T009. Conferência inicial: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, Git limpo e seleção local Feature007. Esse HEAD registra o checkpoint T003–T008, `feat: secure own order queries`; a fundação anterior permanece íntegra. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Nenhum arquivo de produção ou teste recebido foi alterado.

Criados somente [OwnOrderQueryListHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java) e [OwnOrderQueryListApiHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListApiHttpIntegrationTest.java). O primeiro arquivo contém a classe concreta raiz e o suporte abstrato compartilhado `OwnOrderQueryListHttpTestSupport`; a classe API herda os mesmos cenários. Não herdam `OwnOrderQueryHttpTestSupport`, evitando repetir seus `authenticationGate*` no seletor da lista. Não há `@Nested` ou duplicação das asserções entre os contextos. Respostas são inspecionadas como JSON e confrontadas com manifestos independentes; não há referência a DTO/controller/serviço de consulta ainda ausentes. `TokenResponseDTO`, já existente, é usado somente no procedimento de login.

### Cobertura escrita, ainda não executada

| Cenário/caso | Método compartilhado | Expectativa escrita |
| --- | --- | --- |
| US1.1 | `listUs11ReturnsOnlyThreeOwnSummariesWithExactFieldsAndOrderCount` | Par separado com três próprios/dois de outra conta, incluindo pedido com dois itens; somente os três resumos e totalElements3, não número de itens. As compras da fixture compartilhada são controles estrangeiros adicionais. |
| US1.2 | `listUs12TraversesTwoTwoOneWithoutLossDuplicationOrForeignCount` | Páginas0/1/2 de size2, conteúdo2/2/1, totais5/3, união exata dos cinco IDs sem duplicação. |
| US1.3 | `listUs13KeepsDescendingIdTieAcrossPageBoundary` | Três instantes iguais atravessam o corte; IDs decrescentes e ordem completa por manifesto, sem depender de consulta/comparador produtivo. |
| US1.4 | `listUs14KeepsEmptyOwnerAndRequestedPosition` | Titular sem compras, páginas0 e3 com size2, vazio e totais0/0, posição preservada apesar de compras de terceiros. |
| US1.5 | `listUs15KeepsTotalsAndPositionBeyondOwnLastPage` | Page3/size2 vazia, totais5/3 e posição3, sem clamp. |
| US1.6 | `listUs16RejectsInvalidEmptyRepeatedOrOverflowingPagination` |28 queries inválidas: negativos, size0/51, texto/fracionário/expoente/vazio/espaço, overflow, repetição inclusive valores iguais/vazios e offset excedido;400 exato. |
| US1.7 | `listUs17DoesNotRetainOwnerBetweenSuccessiveIdentities` | Na mesma aplicação: CLIENT, outro CLIENT, ADMIN isolado, ambos e CLIENT novamente; conteúdo/count próprios5/2/1/1/5. |
| Padrões/limites | `listDefaultsAndLimitsPreserveExactMetadata` | Nove casos: omissões separadas/conjuntas, defaults explícitos, size1/50, página int32 máxima com size1 e últimos offsets válidos para size2/50, sem clamp. |
| P2, ignorar aliases | `listP2IgnoresAliasesBeforeBindingWithDefaultsAndExplicitValues` |50 combinações: quatro aliases separados e conjunto dos quatro, cinco formas de valor e defaults0/12 ou page1/size2 explícitos. |
| P2, manter rejeição exata | `listP2NeverLetsIgnoredAliasesRepairInvalidExactParameters` |20 combinações: page negativa/repetida e size vazia/repetida continuam400 junto aos cinco conjuntos de aliases. |
| Titular/ordem/nome exato | `listIgnoresOwnerSelectorsAndRequestedSort` | accountId/buyerId de outras contas, sort ascendente/repetido e valores desconhecidos inválidos/repetidos não mudam conteúdo/count/ordem; Page/Size não são page/size. |

P2 usa `_page`, `_size`, `size[]` e `!page`, cada um separado e todos combinados, com inteiro, vazio, texto, fração e repetição contendo vazio/texto/inteiro. Os nomes são codificados na URI com `URLEncoder`, para que o servlet receba os nomes decodificados originais. Asserções exigem200 e os mesmos metadados/conteúdo esperados tanto com defaults quanto com page1/size2; aliases não podem causar400 ou alterar valores. Parâmetros exatos inválidos/repetidos continuam exigindo400, mesmo junto dos aliases. Não foram enfraquecidas as expectativas para aceitar405/404 na lista nova.

Cada resposta200 deve conter exatamente `content/page/size/totalElements/totalPages`; cada resumo, somente `id/createdAt/status/total`. IDs/metadados são números integrais, instante é texto UTC com milissegundos, status coincide com o armazenado e total é texto exato do manifesto, incluindo `42.50`, `0.01` e `9007199254740993.01`, sem obter esperado de `getTotal()` ou mapper produtivo. Página não pode revelar total/conteúdo alheio ou duplicar raízes por quantidade de itens. O400 exige envelope exato `{status,error,message,path}`, `Bad Request`/`Invalid order query data` e URI com contexto sem query, com diagnóstico fixo de asserção sem imprimir um eventual corpo sensível.

Revisão estática dos providers: seis métodos `@Test` e cinco parametrizados; previsão de115 invocações em cada classe concreta,230 no total. **São casos escritos/previstos, não contagem JUnit executada, descoberta pelo Surefire ou aprovação.** Alguns métodos contêm várias requisições sequenciais e podem parar na primeira asserção falha;230 também não é total observado de requisições HTTP. T010 deverá conferir a seleção real e as invocações, sem contar novamente suporte abstrato ou tipos auxiliares internos.

### Preparação e transporte previstos nos fontes

Os dois testes configuram aplicação completa com `RANDOM_PORT`, OSIV false, H2 console false, perfil test, inicializador `AdminProductTestConfig` e import explícito da observação T004. Diferem em context path raiz/API e propriedade `feature007.own-order-list-test-context=root/api`, também distinta da usada na fundação. Setup verifica contexto, seed vazio, create-drop e URL H2 exclusiva/estável via ambiente e conexão. Não há substituição de decoder, cadeia de segurança ou bean produtivo.

O procedimento escrito prepara uma vez por classe a fixture T003: seis contas, nove pedidos/dez itens, manifestos, instantes e empates controlados. Faz cinco logins HTTP reais das contas autorizadas, conferindo identidade pelo decoder real e perfis persistidos via snapshot. US1.1 usa outra instância da mesma fixture para seu par de contas e pedidos3/2, com login próprio e limpeza local. Preparação dos pedidos é JDBC commitado, necessária aos instantes controlados; não é jornada de registro POST/orders. Essa fixture local não remove os dados da compartilhada. Setup/login precedem cada janela de leitura; limpezas são posteriores e restritas às próprias linhas, itens/pedidos antes do catálogo/contas.

As chamadas previstas de login e GET usam `java.net.http.HttpClient`, porta aleatória, sem redirects e sem transação externa. Todos os GET passam pelo `observeRead` recebido, snapshot completo e observação SQL, conservando os limites da T004: duas transações de snapshot, sem fotografia global atômica concorrente; inspector não observa JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Não há mocks novos nem uso do login MockMvc da fixture; seu construtor somente monta o suporte herdado. A instrumentação escrita registra `T009_CONTEXT`, `T009_HTTP` e `T009_COUNTS`, com status/tamanho/contadores e sem tokens, senhas ou hashes.

Esses são **procedimentos preparados para execução posterior**. Nesta T009: zero inicializações da aplicação/H2, zero persistência, zero logins, zero requisições HTTP e zero dispatches MockMvc efetivamente executados.

### Verificações atuais e fronteira T010

Skill `speckit-implement` limitada à T009: tarefa, dependência T008 e artefatos pertinentes lidos; `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente. Raiz/branch/HEAD/status, links, fontes novos e whitespace revisados. Produção e todos os testes recebidos estão preservados; lista e detalhe continuam sem controller/serviço produtivo de consulta.

**Nenhum Maven ou compilador executado na T009.** Ela exige escrever os testes; a linha F1-RED da tabela de seleções pertence expressamente à T010. Não foi executada nem antecipada essa tarefa. Comando aprovado para T010, registrado apenas como procedimento futuro:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*' test
```

Não há console/exit code Maven, seleção Surefire nova, RED funcional ou GREEN destes testes. Compilação e preparação ainda precisam ser verificadas na T010; eventual erro nessas etapas não demonstrará comportamento ausente. A ausência atual da rota GET também não é reprodução específica do problema P2 de binder. Preservados, sem repetição ou reclassificação, F0-RED T005, erro de compilação T003, erro Mockito T006, suas aprovações distintas e F0-GREEN T008 com431 casos aprovados. Esse GREEN histórico não aprova os fontes novos da T009.

### Preservação e revisão

Evidência própria em `target/feature007-validation/t009-list-tests-20261020/`: `session-start.json`, manifestos de280 rastreados/1257 arquivos de evidências anteriores/16 relatórios Surefire, documentos recebidos e cópias/SHA-256 dos dois fontes novos. Como não houve Maven, **nenhum relatório foi movido ou sobrescrito**; os16 recebidos de F0-GREEN permanecem íntegros em `target/surefire-reports/` e em suas áreas históricas.

Revisão executada da raiz por `& 'target/feature007-validation/t009-list-tests-20261020/review.ps1'`: exit0 de conferência estática/preservação, `StaticAndPreservationReviewPassed=true`, `CompilationExecuted=false`, `JUnitTestsExecuted=0`, `ApplicationTestsApproved=false` e `FunctionalRedObserved=false`. Confirmados280 rastreados e1257 evidências anteriores intactos antes do fechamento documental,16 relatórios inalterados e dois fontes revisados. Não é execução ou aprovação de testes da aplicação.

### Fechamento da T009

Diff documental, dois fontes novos, referências e56 links locais revisados. `git diff --check`: exit0; `git diff --cached --exit-code`: exit0, índice vazio. Whitespace dos dois Java não rastreados conferido individualmente por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico, exit1 pela diferença esperada de arquivo novo; opção restrita ao comando. Prefixo histórico recebido do quickstart de102958 bytes preservado e tasks difere somente pelo marcador T009;278 outros rastreados,1257 evidências anteriores e16 relatórios Surefire mantidos. Hooks posteriores ausentes (`.specify/extensions.yml` não existe).

Estado: **T001–T009 concluídas;25 tarefas abertas, T010–T034**. T009 concluída quanto à escrita/revisão dos cenários; compilação, descoberta JUnit, preparação e F1-RED ainda não verificados, sem alegação de RED/GREEN ou entrega funcional. Git: somente quickstart/tasks modificados e os dois Java novos não rastreados, nada staged; HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, branch, main e seleção007 preservados. Sem Maven, stage, commit, push, CI ou implementação T010+. Não há impedimento para a escrita solicitada; as verificações executáveis continuam pendentes da tarefa própria.

## T010 — F1-RED da listagem — 2026-10-20

**Escopo executado**: exclusivamente T010, dependente dos testes escritos na T009. Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f` e seleção local Feature007. Somente quickstart/tasks modificados, somente `OwnOrderQueryListHttpIntegrationTest.java` e `OwnOrderQueryListApiHttpIntegrationTest.java` não rastreados, nada staged. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Nenhum Java recebido ou arquivo produtivo foi alterado; as asserções da T009 permaneceram idênticas.

Skill `speckit-implement` limitada à tarefa solicitada, com T009/T010, protocolo F1-RED e artefatos pertinentes lidos. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente, sem hooks aplicáveis. Nenhum Java/Maven ativo ou execução anterior da T010 encontrado antes de iniciar.

### Comando, ambiente e preservação

Uma única execução Maven nesta tarefa, sequencial, da raiz, com Java21.0.12.1 Azul Zulu e Wrapper Maven3.9.16:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*' test
```

Executada por `& 'target/feature007-validation/t010-f1-red-20261020/run.ps1'`, entre `2026-10-20T05:05:36.1284345-03:00` e `2026-10-20T05:07:23.9770842-03:00`. **BUILD FAILURE, exit code1**, por falhas de asserção dos testes. O seletor é exatamente o F1-RED aprovado, sem grupos anteriores, baseline, F0-GREEN ou suíte completa. Não foi usado clean.

Evidência própria e inédita: `target/feature007-validation/t010-f1-red-20261020/`, contendo `session-start.json`, `result.json`, `console.log`, `working.diff`, documentos recebidos, manifestos SHA-256 e cópias dos sete fontes relevantes. Os16 relatórios Surefire recebidos foram movidos de `target/surefire-reports/` para `received-surefire-reports/` antes do Maven. Caminhos absolutos resolvidos dentro de target e sem sobreposição conferidos; hashes/tamanhos/caminhos após a movimentação coincidem. Nenhum relatório antigo foi sobrescrito. A nova execução gerou somente quatro arquivos, dois XML e dois TXT, copiados integralmente para `reports/` e mantidos também na área Surefire atual.

### Seleção efetiva e resultado funcional observado

Console e XMLs confirmam somente as duas classes concretas esperadas, cada uma com115 invocações,115 falhas,0 erros e0 ignorados:

| Classe concreta | Contexto | JUnit | Falhas | Erros | Ignorados |
| --- | --- | ---: | ---: | ---: | ---: |
| `OwnOrderQueryListHttpIntegrationTest` | raiz |115|115|0|0|
| `OwnOrderQueryListApiHttpIntegrationTest` | `/api` |115|115|0|0|
| Total | dois contextos |230|230|0|0|

Conferidos os nomes de todos os métodos e índices das invocações parametrizadas em cada XML, sem omissões ou duplicatas:

| Método compartilhado | Invocações por classe | Total |
| --- | ---: | ---: |
| `listUs11ReturnsOnlyThreeOwnSummariesWithExactFieldsAndOrderCount` |1|2|
| `listUs12TraversesTwoTwoOneWithoutLossDuplicationOrForeignCount` |1|2|
| `listUs13KeepsDescendingIdTieAcrossPageBoundary` |1|2|
| `listUs14KeepsEmptyOwnerAndRequestedPosition` |2|4|
| `listUs15KeepsTotalsAndPositionBeyondOwnLastPage` |1|2|
| `listUs16RejectsInvalidEmptyRepeatedOrOverflowingPagination` |28|56|
| `listUs17DoesNotRetainOwnerBetweenSuccessiveIdentities` |1|2|
| `listDefaultsAndLimitsPreserveExactMetadata` |9|18|
| `listP2IgnoresAliasesBeforeBindingWithDefaultsAndExplicitValues` |50|100|
| `listP2NeverLetsIgnoredAliasesRepairInvalidExactParameters` |20|40|
| `listIgnoresOwnerSelectorsAndRequestedSort` |1|2|

Não há classes de teste `@Nested` neste recorte: `AliasCase` e `PageCase` são records auxiliares, sem testes; o suporte abstrato compartilha os11 métodos, mas não representa uma terceira suíte. As duas classes concretas executaram os seis métodos simples e as109 invocações parametrizadas cada, somando115. Nenhum `authenticationGate*` ou teste de regressão antigo foi repetido.

**As230 falhas são funcionais**, todas em `get`, na mesma asserção `own order list HTTP status`:134 esperavam200 e96 esperavam400; todas receberam405. Em cada contexto foram67 expectativas200 e48 expectativas400. Console registra230 ocorrências de `HttpRequestMethodNotSupportedException`; o controller existente oferece somente POST/orders e ainda não há rota GET de listagem. Não foram observados401/403/500, falha de compilação, preparação, transporte, contexto, limpeza ou infraestrutura. A compilação de testes concluiu e ambos os contextos chegaram às requisições reais. Nenhuma correção de suporte, teste ou produção foi necessária.

**Controles aprovados como casos JUnit:0.** Todos os230 casos selecionados exigem o contrato novo de lista/entrada e falharam no status; não se apresentam as etapas de preparação como testes aprovados. As verificações anteriores a essa asserção concluíram: H2 isolado/vazio/estável, perfis persistidos, logins e identidade JWT reais, ausência de transação do chamador e snapshot/observação de cada janela sem escrita detectada. Esses controles de preparação/observação permitem atribuir as falhas à operação ausente; não aprovam conteúdo, paginação ou validação. Os24 controles aprovados da T005 e o F0-GREEN da T008 continuam somente evidências históricas preservadas, sem repetição ou reclassificação.

O405 da rota ausente **não reproduz especificamente P2 do binder**: aliases, defaults, limites, repetição e entradas inválidas ainda não alcançam um resolver/controller de listagem. Não há prova atual de400 contratual,200 funcional, conteúdo/count próprios, ordenação, dinheiro ou materialização com OSIV false. As asserções de JSON e as requisições posteriores dos métodos de travessia/identidades param na primeira falha de status e continuam pendentes. Não se fabricou RED para comportamento já entregue nem se alteraram expectativas para aceitar405.

### Preparação, persistência e transporte efetivamente usados

Dois servidores reais em portas aleatórias: raiz51765, API51759. URLs H2 distintas, conferidas pelo ambiente e metadados JDBC: raiz `jdbc:h2:mem:admin-product-test-7cfbe87a-a7a2-40d2-8db7-21b3036ac83e`; API `jdbc:h2:mem:admin-product-test-c8dc0c75-15e5-4337-b9df-a3b47190b8ee`. Ambos com perfil test, create-drop, seeds/import desabilitados e OSIV false somente nesses contextos. Nenhum ajuste global de banco/cache/ordem foi feito.

A fixture compartilhada e o cenário local de US1.1 prepararam dados JDBC reais e commitados, com manifestos independentes e limpeza das próprias linhas. Isso é preparação de persistência, não uma jornada POST/orders. O transporte foi `java.net.http.HttpClient` contra os servidores, com filtros/decoder/autorização reais e sem mocks. Os marcadores `T009_CONTEXT`, `T009_HTTP` e `T009_COUNTS` existentes nos fontes escritos pela T009 foram reconciliados no console desta execução da T010, sem tokens/senhas/hashes nos diagnósticos.

Contagem observada: **230 GET/orders e12 POST/login HTTP reais,242 requisições ao todo;0 dispatches MockMvc**. Por contexto:115 GET e6 logins, cinco no setup compartilhado e um em US1.1. Não são242 testes JUnit; o total JUnit permanece230. O suporte herdado monta MockMvc, mas seu helper de login não foi chamado. A falha no primeiro GET de cada invocação impediu requisições posteriores escritas em alguns métodos, portanto a jornada futura completa demandará outra contagem observada.

Em cada GET, `observeRead`/`assertNoWrites` concluíram antes da falha de status. Mantêm-se os limites da T004: snapshot composto por duas transações, sem fotografia global atômica concorrente; StatementInspector não observa JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. O resultado é restrito às janelas de405 observadas, sem aprovação transversal de US4 nem de leituras bem-sucedidas ainda ausentes.

### Auditoria e fechamento da T010

Auditoria da evidência executada por `& 'target/feature007-validation/t010-f1-red-20261020/audit.ps1'`: exit0 da conferência; `ExpectedFunctionalRedConfirmed=true`, `ApplicationTestsApproved=false`, Mavenexit1. `case-audit.json` identifica230 casos, `http-audit.json` registra contextos/contadores/queries e `audit.json` reconcilia seleção, resultados e preservação. Confirmados280 rastreados intactos antes do fechamento documental, sete fontes idênticos,1271 arquivos de evidências anteriores preservados,16 relatórios arquivados íntegros e quatro relatórios novos. A conferência inicial desse script local de auditoria teve exit1 por enumeração de array JSON no PowerShell5.1; corrigida somente a enumeração do auditor, com registro em `audit-preparation.json` e cópia `audit-initial.ps1`. Isso ocorreu depois do Maven e não é erro de preparação JUnit, nova execução Maven ou RED funcional adicional.

Revisados diff, os dois Java recebidos não rastreados, referências, links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos Java conferido individualmente por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico; exit1 corresponde somente à diferença esperada de arquivo novo. Prefixo integral do quickstart recebido preservado; tasks recebida alterada somente no marcador T010. Os278 demais rastreados, os dois Java da T009, a seleção local e todas as evidências anteriores permanecem idênticos.

**T010 concluída quanto à observação e documentação do F1-RED exigido. T001–T010 marcadas; T011–T034 abertas,24 pendências.** Lista/detalhe e comprovação funcional continuam pendentes das tarefas próprias; a Feature007 não está concluída. Não houve implementação T011+, repetição de baseline/F0-GREEN/suíte completa, stage, commit, push, alteração de main ou CI. Git final: somente quickstart/tasks modificados e os dois Java recebidos não rastreados, nada staged; branch/HEAD/main/seleção007 preservados. Não há impedimento de preparação remanescente para o checkpoint RED solicitado.

## T011 — Entrada de paginação e resolver MVC — 2026-10-20

**Escopo executado**: exclusivamente T011. Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente quickstart/tasks modificados, somente os dois Java HTTP de lista recebidos não rastreados e nada staged. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. T010 é a dependência atendida: seu F1-RED registrou230 falhas funcionais por405 da rota ausente, sem erro de preparação; **não isolou o comportamento P2 do binder**. Essa evidência e todo o histórico T001–T010 permanecem preservados.

Criados somente os quatro arquivos previstos pela tarefa:

- [OwnOrderPageRequestDTO](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderPageRequestDTO.java): construtor sem argumentos, Integer page/size com padrões0/12, NotNull/Min e Max50 para size. AssertTrue puro calcula o offset em long e exige valor<=2147483647; tolera nulos/limites básicos inválidos para as constraints de campo diagnosticá-los. O helper é ignorado no JSON, sem propriedade extra de resposta ou chamada a serviço/repositório.
- [OwnOrderPageArgumentResolver](../../src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderPageArgumentResolver.java): atende somente `@Valid OwnOrderPageRequestDTO`, sem `@ModelAttribute`. Lê uma vez o mapa já decodificado pelo servlet, seleciona somente nomes exatos/case-sensitive page/size e copia os arrays. Repetições, inclusive valores iguais/vazios, são rejeitadas antes de criar DTO/binder ou converter. Todos os demais nomes são descartados antes de qualquer adaptação de prefixos/colchetes. Cria DTO com padrões, usa binder configurado pelo Spring limitado a page/size e fornece somente MutablePropertyValues filtrados; não chama bind da requisição original. Conversão e Bean Validation terminam antes de retornar o argumento ao handler.
- [OwnOrderQueryWebMvcConfig](../../src/main/java/com/devsuperior/dscommerce/config/OwnOrderQueryWebMvcConfig.java): registra somente esse resolver, antes do fallback MVC de ModelAttribute, sem binder/validator global ou mudança nas políticas das demais entradas.
- [OwnOrderQueryPageArgumentResolverTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryPageArgumentResolverTest.java): provas diretas das constraints/ordem/cópia/escopo e integração MVC com a configuração produtiva do resolver, conversion service e validator reais. Controller exclusivo de teste em `/t011/page` e `/t011/legacy`, protegido por perfil próprio contra descoberta nos demais contextos; não cria GET/orders produtivo.

Vazios/inválidos/overflow não recebem silenciosamente os padrões. Erros de conversão/constraints geram MethodArgumentNotValidException; multiplicidade gera ServletRequestBindingException com mensagem fixa. O status400 foi observado no MVC de teste; **o advice e envelope exato da consulta pertencem à T015**, sem adaptação do GlobalExceptionHandler nesta tarefa. Controller/serviço/repositório de listagem, DTO de resumo e componentes T012+ não foram criados. SecurityConfig, handlers de segurança, POST/orders, catálogo, login e administração permanecem idênticos aos recebidos.

### Procedimento e execução focal

Skill `speckit-implement` aplicada somente à T011, com leitura da tarefa/dependências e artefatos pertinentes. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis. Java/Maven não estavam ativos e não havia execução anterior da T011. Java21.0.12.1 Azul Zulu, Wrapper Maven3.9.16.

Uma única execução, da raiz, sem clean, concorrência ou repetição de outros grupos:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryPageArgumentResolverTest' test
```

Executada por `& 'target/feature007-validation/t011-page-resolver-20261020/run.ps1'`, de `2026-10-20T05:18:15.1728997-03:00` a `2026-10-20T05:19:35.1332565-03:00`. **BUILD SUCCESS, exit code0;254 testes,0 falhas,0 erros,0 ignorados**. Testes escritos para os critérios desta tarefa; nenhuma compilação falha ou ausência de classe foi apresentada como RED, e nenhum defeito artificial foi introduzido. F1-RED, baseline, F0-GREEN e suíte completa não foram repetidos.

Evidência própria em `target/feature007-validation/t011-page-resolver-20261020/`: comando/horários/ambiente/HEAD/status em `session-start.json` e `result.json`, console integral em `console.log`, diff recebido, documentos recebidos, manifestos SHA-256 e11 fontes copiados. Antes do Maven, os quatro relatórios recebidos da T010 foram movidos de `target/surefire-reports/` para `received-surefire-reports/`, depois de conferir caminhos absolutos dentro de target, ausência de sobreposição/destino preexistente e hashes após movimentação. Os dois relatórios novos, um XML e um TXT, permanecem na área Surefire e em `reports/`; nenhum relatório antigo foi sobrescrito.

### Seleção Surefire reconciliada

Console e XML confirmam somente `com.devsuperior.dscommerce.controllers.OwnOrderQueryPageArgumentResolverTest`. Conferidos13 métodos, índices de todas as invocações parametrizadas e254 identidades únicas, sem omissões/duplicatas. Não há `@Nested`; classes internas são configuração/controller/assinaturas/DTO auxiliares, sem casos JUnit próprios.

| Método | Invocações |
| --- | ---: |
| `beanValidationAcceptsDefaultsBoundsAndLargestValidOffsets` |9|
| `beanValidationRejectsInvalidFieldsWithPureIndependentConstraints` |10|
| `supportsOnlyTheDedicatedValidInputWithoutAutomaticModelAttribute` |5|
| `rejectsMultiplicityBeforeBinderCreationOrConversion` |10|
| `filtersAndCopiesOriginalNamesBeforeBinderCreationAndNeverRereadsTheRequest` |1|
| `configurationRegistersTheResolverBeforeTheAutomaticModelAttributeFallback` |1|
| `mvcConvertsOnlyPresentParametersAndValidatesBeforeCallingTheHandler` |18|
| `mvcRejectsInvalidExactQueriesBeforeCallingTheHandler` |56|
| `mvcIgnoresEveryAliasVariantWithDefaultsAndExplicitValues` |100|
| `mvcAliasesCannotRepairInvalidOrRepeatedExactParameters` |40|
| `mvcIgnoresOwnerSelectorsSortCaseVariantsAndNestedPropertyNames` |1|
| `mvcPreservesTheDefaultBinderForOtherInputTypes` |2|
| `helperConstraintDoesNotBecomeAnInputOrJsonResponseField` |1|

Os217 casos MVC realizam um dispatch cada:18 entradas válidas,56 inválidas exatas,100 variantes de aliases,40 inválidas com aliases, um conjunto de nomes desconhecidos e dois controles do binder de outro tipo. `_page`, `_size`, `size[]` e `!page` são exercitados separados/combinados, com inteiro/vazio/texto/fração/repetidos, tanto com defaults quanto page1/size2 explícitos. Nomes decodificados são fornecidos ao mock servlet, incluindo repetições preservadas. Queries exatas inválidas continuam400 e o handler não é chamado; aliases não alteram valores válidos nem causam400. Os controles de outro DTO conservam o tratamento nativo de `!page`, sem aplicar a filtragem007 indiscriminadamente.

Os37 casos restantes verificam constraints diretamente, suporte de assinatura, registro MVC, JSON e a fronteira de preparação do binder. Mockito é usado somente para NativeWebRequest/WebDataBinderFactory nos11 casos de ordem/multiplicidade, com bind/validação reais no caso de cópia. Provada uma única leitura do mapa e nenhuma interação com a fábrica em multiplicidade inválida; a mutação do mapa original durante a criação do binder não muda os valores já copiados. Não há mock de serviço, repositório, segurança ou banco substituindo prova HTTP de negócio.

### Alcance e limites

**254 testes JUnit,217 dispatches MockMvc,0 requisições HTTP reais e0 contextos de banco**. É um único contexto MVC mínimo com URLs simuladas raiz e `/api`, sem servidor/porta, filtros JWT, persistência, fixtures ou login. A preparação só inicializa MVC/validator/controller de teste. Nenhum H2 foi aberto ou compartilhado e nenhum perfil/seed/OSIV global foi alterado.

Este GREEN comprova o componente de binding/Bean Validation e seu registro MVC. Não aprova a listagem, ownership/count/dinheiro, proteção HTTP, envelope contratual nem P2 por HTTP real no endpoint produtivo ainda ausente. Não se reclassifica o405 da T010 como reprodução isolada de P2. Os cenários HTTP recebidos da T009 e suas asserções permanecem idênticos, para integração posterior em T015/T016; lista/detalhe e Feature007 continuam incompletos.

Auditoria executada por `& 'target/feature007-validation/t011-page-resolver-20261020/audit.ps1'`: exit0 de conferência, `FocalComponentTestsApproved=true`, `OwnOrderListingApproved=false`, `P2RealHttpApproved=false`. `case-audit.json` identifica as254 invocações. Confirmados280 rastreados intactos antes do fechamento documental,11 fontes testados idênticos, dois Java recebidos preservados,1321 arquivos de evidências anteriores íntegros, quatro relatórios recebidos arquivados e dois novos conferidos. Não foi necessária correção após o Maven nem segunda execução.

### Fechamento da T011

Diff e seis Java não rastreados revisados, incluindo os dois recebidos; referências/links locais e whitespace conferidos. `git diff --check` e `git diff --cached --exit-code`: exit0. Conferência individual dos não rastreados por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico de whitespace, exit1 esperado por arquivo novo. Prefixo integral do quickstart recebido preservado; tasks recebida difere somente pelo marcador T011. Os278 demais rastreados, os dois Java HTTP recebidos, relatórios e evidências anteriores, main, branch, HEAD e seleção007 permanecem íntegros.

**Somente T011 marcada nesta execução: T001–T011 concluídas, T012–T034 abertas,23 tarefas pendentes.** Git final: quickstart/tasks modificados, seis Java não rastreados (dois recebidos e quatro criados), nada staged. Sem impedimento remanescente no escopo solicitado, stage, commit, push, CI ou alteração de main. Nenhuma implementação T012+ realizada.

## T012 — Contrato JSON do resumo próprio — 2026-10-20

**Escopo executado**: exclusivamente T012, dependente de T010. Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente quickstart/tasks modificados e nada staged. Conferidos antes de alterar e registrados com caminho completo/SHA-256 em `received-java.json` os seis Java recebidos: OwnOrderQueryWebMvcConfig, OwnOrderPageArgumentResolver, OwnOrderPageRequestDTO, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryListApiHttpIntegrationTest e OwnOrderQueryPageArgumentResolverTest. Todos foram preservados, incluindo asserções e proteção recebidas. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Criados somente [OwnOrderSummaryDTO](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderSummaryDTO.java) e [OwnOrderQueryDtoContractTest](../../src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java). O DTO é um record de valores escalares imutáveis, com exatamente id/createdAt/status/total, sem entidade de pedido/comprador, accountId, items ou outros dados sensíveis. Reutiliza o enum OrderStatus existente e a formatação UTC com milissegundos de OrderResponseDTO. O construtor reutiliza diretamente `OrderItemResponseDTO.requireMoney(total)`, sem copiar/mudar o verificador monetário, normalizar/arredondar valores ou calcular total. A obtenção dos dados e o cálculo histórico permanecem na tarefa do serviço. PageResponseDTO, entidades/enum, DTOs anteriores, resolver e segurança não foram alterados. Nenhum componente T013+ foi criado.

### Procedimento e execução focal

Skill `speckit-implement` limitada à T012, com leitura integral da tarefa, dependência T010 e fontes pertinentes de requisitos, planejamento, modelo, contrato e evidências. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis. Não havia Java/Maven ativo nem execução anterior da T012. Java21.0.12.1 Azul Zulu, Wrapper Maven3.9.16.

Uma única execução, da raiz, sem clean e sem grupos anteriores:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDtoContractTest' test
```

Executada por `& 'target/feature007-validation/t012-summary-dto-20261020/run.ps1'`, entre `2026-10-20T05:26:15.8974226-03:00` e `2026-10-20T05:27:49.8496587-03:00`. **BUILD SUCCESS, exit code0:37 testes,0 falhas,0 erros,0 ignorados**. Testes escritos para o DTO novo antes da sua validação; nenhuma compilação falha foi apresentada como RED nem se exigiu RED artificial do verificador/formatos já existentes. Maven compilou os fontes necessários ao build, mas o Surefire executou somente o seletor focal; não houve repetição de F1-RED, resolver T011, baseline, F0-GREEN, testes DTO antigos ou suíte completa.

Evidência inédita em `target/feature007-validation/t012-summary-dto-20261020/`: `session-start.json`, `result.json`, `console.log`, diff/documentos recebidos, manifesto dos seis Java recebidos e cópias/SHA-256 dos13 fontes capturados. Os dois relatórios Surefire recebidos da T011 foram movidos antes do Maven para `received-surefire-reports/`, após conferir caminhos absolutos dentro de target, sem sobreposição e com destino inédito; hashes/tamanhos/caminhos depois da movimentação coincidem. Os dois relatórios novos, um XML e um TXT, permanecem em `target/surefire-reports/` e em `reports/`. Relatórios anteriores não foram sobrescritos.

### Seleção e resultados reconciliados

Console e XML confirmam somente `com.devsuperior.dscommerce.dto.OwnOrderQueryDtoContractTest`, sem classes aninhadas de teste. Conferidos sete métodos, todos os índices parametrizados e37 identidades únicas, sem omissões/duplicatas:

| Método | Invocações | Prova focal |
| --- | ---: | --- |
| `summarySerializesExactlyFourFieldsWithIntegralIdAndStoredStatus` |1| Campos exatos, id numérico integral até Long.MAX_VALUE, instante textual, estado WAITING_PAYMENT e total textual. |
| `summaryFormatsUtcMillisecondsWithoutChangingTheSuppliedInstant` |4| Milissegundos explícitos/zero, offset +03:00 convertido a UTC e nanos exibidos com três casas; Instant fornecido permanece idêntico no DTO. |
| `summaryPreservesExactMoneyTextFromOneCentToDecimalCapacity` |9|0.01 a99999999999999999.99, inclusive9007199254740993.01, sem perda numérica ou alteração textual. |
| `summaryRejectsNonContractMoneyWithoutRoundingOrReformatting` |19| Nulo, vazio, zero/negativo, escala ausente/incorreta, expoente, zeros iniciais, vírgula, overflow decimal19,2, espaços, sinal positivo, NaN e Infinity. |
| `summaryFitsTheUnchangedPageContractWithoutAddingBuyerOrItemData` |1| Cinco campos da página existente, metadados numéricos e dois resumos exatos na sequência fornecida. |
| `summaryPageSerializesSuppliedEmptyContentAndMetadata` |2| Content vazio e metadados fornecidos para primeira/página posterior; sem calcular ou ajustar a posição. |
| `summaryRoundTripPreservesExactScalarValuesAtMillisecondPrecision` |1| Escrita/leitura pelo mapper do slice preserva Long.MAX_VALUE, instante em milissegundos, estado e dinheiro grande textual. |

Teste usa `@JsonTest` e ObjectMapper configurado pelo Spring Boot com suporte temporal. **37 testes JUnit;0 mocks definidos,0 dispatches MockMvc,0 requisições HTTP reais e0 contextos de banco**. A preparação é somente do slice JSON; cada caso confere ausência dos beans de datasource, adapter MVC e filtros de segurança. Não há fixture, JDBC/JPA, servidor/porta ou login, nem alteração de H2/seed/OSIV/perfis globais. A exibição de nanos com três casas prova o formato de serialização, não precisão/normalização dos instantes persistidos. Página/conteúdo/count são valores fornecidos diretamente ao DTO, sem comprovar consulta, ordenação ou cálculo de metadados pelo serviço.

### Preservação, limites e fechamento

Auditoria executada por `& 'target/feature007-validation/t012-summary-dto-20261020/audit.ps1'`: exit0 de conferência, `DtoJsonContractApproved=true`, `OwnOrderListingApproved=false`, `P2RealHttpApproved=false`. `case-audit.json` identifica as37 invocações. Confirmados280 rastreados intactos antes do fechamento documental,13 fontes testados idênticos, seis Java recebidos preservados,1360 arquivos de evidências anteriores íntegros, dois relatórios recebidos arquivados e dois novos conferidos. Nenhuma correção após o Maven ou segunda execução foi necessária.

Evidências mantidas distintas: **T010 é RED por ausência da listagem, sem isolamento de P2; T011 é GREEN do resolver com217 dispatches MockMvc, sem HTTP real/banco; T012 é GREEN de representação JSON**. Nenhum desses resultados aprova por si a integração completa da consulta. Endpoint, serviço, consultas por titular, cálculo histórico, materialização, segurança em leituras bem-sucedidas e prova HTTP de P2 continuam pendentes das tarefas posteriores.

Revisados diff, oito Java não rastreados (seis recebidos e dois novos), referências/links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0. Conferência individual dos não rastreados por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico; exit1 esperado por arquivo novo. Prefixo integral do quickstart recebido preservado; tasks recebida alterada somente no marcador T012. Os278 outros rastreados, incluindo PageResponseDTO/verificador monetário/segurança, os seis Java recebidos, relatórios anteriores, branch/HEAD/main e seleção007 permanecem íntegros.

**Somente T012 marcada nesta execução: T001–T012 concluídas, T013–T034 abertas,22 tarefas pendentes.** Git final: quickstart/tasks modificados, oito Java não rastreados, nada staged. Sem impedimento remanescente no escopo, execução T013+, stage, commit, push, CI ou alteração de main. Lista/detalhe e Feature007 continuam incompletos.

## T013 — Página de IDs e fetch do lote por titular — 2026-10-20

**Escopo executado**: exclusivamente T013, dependente de T010. Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente quickstart/tasks modificados, oito Java recebidos não rastreados e nada staged. Caminhos completos e SHA-256 dos oito conferidos antes das alterações em `t013-list-repository-20261020/received-java.json`: OwnOrderQueryWebMvcConfig, OwnOrderPageArgumentResolver, OwnOrderPageRequestDTO, OwnOrderSummaryDTO, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryListApiHttpIntegrationTest, OwnOrderQueryPageArgumentResolverTest e OwnOrderQueryDtoContractTest. Preservados esses fontes, suas asserções e todas as evidências anteriores. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Alterado somente [OrderRepository](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java) em produção: `findOwnOrderIds(ownerId, pageable)` seleciona apenas IDs, com buyer.id obrigatório, ordem createdAt DESC/id DESC e countQuery do mesmo titular sem joins; `findOwnOrdersWithItemsByIds(ownerId, ids)` repete o titular e aplica IN aos IDs selecionados, com DISTINCT e LEFT JOIN FETCH somente de items/product, sem Pageable. Ambas as consultas declaram readOnly. Métodos herdados de escrita permanecem disponíveis e inalterados. Criado [OwnOrderQueryRepositoryTest](../../src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java), exclusivamente com métodos de teste `list*`. Nenhum serviço, controller, advice, consulta de detalhe ou componente T014+ foi implementado. Segurança, paginação, DTO aprovado, PageResponseDTO, entidades, schema e suporte recebido não foram alterados.

### Preparação e duas execuções focais identificadas

Skill `speckit-implement` limitada à T013; tarefa/dependência e artefatos pertinentes de requisitos, desenho JPA, modelo, contrato e procedimentos lidos. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis. Não havia Java/Maven ativo nem execução anterior da T013 antes de iniciar. Java21.0.12.1 Azul Zulu, Wrapper Maven3.9.16.

Comando único de seleção, usado sequencialmente nas duas execuções abaixo, da raiz e sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryRepositoryTest#list*' test
```

| Execução / diretório sob target/feature007-validation | Início/fim (-03:00) | Resultado observado |
| --- | --- | --- |
| `t013-list-repository-20261020/` |05:37:31.2120887 /05:39:16.3648398|16 testes,7 falhas,9 aprovados,0 erros/ignorados; BUILD FAILURE, exit1. |
| `t013-list-repository-20261020-retry/` |05:40:09.3154650 /05:41:52.4963648|Os mesmos16 testes,0 falhas/erros/ignorados; BUILD SUCCESS, exit0. |

A primeira execução falhou somente na nova asserção de formato SQL que exigia OFFSET também para a página zero. Os sete casos chegaram à comparação SQL depois de aprovar conteúdo/metadados; Hibernate gera legitimamente `FETCH FIRST` sem OFFSET quando o deslocamento é zero. Causas e identidades dos sete casos reconciliadas em `diagnosis.json`, sem erro de compilação, preparação ou infraestrutura. **Não é RED funcional da consulta nem reprodução de P2.** Alguns métodos interromperam requisições subsequentes ao repositório nessa primeira asserção falha; seus18 read windows/11 páginas/7 lotes não são a jornada completa da repetição.

Corrigido somente o teste novo: mantém exigência de FETCH FIRST em toda página de IDs e exige OFFSET para páginas posteriores, verificando sua ausência na primeira página conforme o SQL do provider utilizado. Conteúdo/count, ordem, titular, limites no banco, fetch, snapshots e todas as asserções funcionais permaneceram intactos. Nenhum ajuste produtivo foi feito para obter aprovação. A repetição focal foi necessária por essa correção concreta; console, fontes e relatórios da execução falha foram preservados. Não houve repetição de seleções anteriores, baseline, F0-GREEN, F1-RED ou suíte completa.

Cada diretório contém seu `run.ps1`, `session-start.json`, `result.json`, `console.log`, diff/documentos recebidos, manifestos SHA-256 e15 fontes copiados. Antes de cada Maven, os dois relatórios recebidos de `target/surefire-reports/` foram movidos para `received-surefire-reports/` do destino inédito daquela execução, depois de conferir caminhos absolutos dentro de target, ausência de sobreposição e hashes após a movimentação. A primeira arquivou os relatórios T012; a repetição arquivou os da primeira T013. Cada execução gerou um XML e um TXT próprios em `reports/`; os relatórios correntes são somente os dois da repetição. Nenhum resultado anterior foi sobrescrito ou contado como novo.

### Contexto isolado e seleção efetiva

Um contexto completo de componentes por execução, SpringBootTest MOCK, perfil test, OSIV false e H2 exclusivo/estável pelo AdminProductTestConfig recebido. URLs conferidas pelo ambiente e metadados JDBC: primeira `jdbc:h2:mem:admin-product-test-86fc166d-2948-46b2-9ee7-24ac5f102cda`; repetição `jdbc:h2:mem:admin-product-test-32a9965f-deb7-4dd1-ac9e-21ae60253df6`. Create-drop e inicialização SQL/import desabilitada somente nesses contextos. Propriedade própria `feature007.own-order-repository-test-context=list`; fail_on_pagination_over_collection_fetch=true somente no teste para impedir paginação de coleção em memória, sem mudança global.

Cada invocação prepara com a fixture recebida seis contas/nove pedidos/dez itens, por JDBC commitado, com manifestos independentes e terceiros intercalados. Limpeza remove somente linhas próprias, itens/pedidos antes do catálogo/contas; banco vazio e URL estável conferidos antes/depois. O caso de raiz incoerente remove apenas os itens de seu próprio pedido em preparação JDBC commitada fora da janela de leitura, sem desligar FK/constraints ou alterar entidades. A consulta deve conservar essa raiz sem itens; diagnóstico500 é responsabilidade futura do serviço, não aprovação produzida aqui.

Console/Surefire de ambas as execuções confirmam somente `com.devsuperior.dscommerce.repositories.OwnOrderQueryRepositoryTest`. Sete métodos,16 invocações idênticas antes/depois, índices parametrizados conferidos, sem omissões/duplicatas ou classes @Nested:

| Método | Invocações |
| --- | ---: |
| `listPagesTwoTwoOneWithStableTimestampIdOrderAndOwnCount` |1|
| `listPageContentAndCountUseTheSameOwner` |4|
| `listEmptyAndBeyondPagesKeepTheRequestedPositionAndOwnTotals` |4|
| `listPagesOrderIdsBeforeFetchingAllItemsWithoutACollectionLimit` |1|
| `listBatchFiltersMixedIdsByOwnerWithoutDuplicateRoots` |4|
| `listBatchRejectsForeignAndAbsentIdsEvenWhenExplicitlyRequested` |1|
| `listLeftFetchKeepsAnIncoherentRootWithoutItemsForTheLaterServiceToDiagnose` |1|

Na repetição aprovada: **16 testes de persistência JPA/H2 real,20 janelas de leitura,13 páginas de IDs e7 fetches de lote**. Observados30 SELECTs:13 de IDs,10 counts e7 lotes. SQL confirma predicado buyer_id em conteúdo/count/fetch, projeção de IDs sem coleção, paginação no banco, ordem fixa e fetch sem OFFSET/FETCH FIRST/categorias/contas. Count é exigido nos casos preparados para executá-lo; a otimização legítima do Spring Data em outras páginas é preservada. Esses números descrevem a execução focal, não orçamento global de consultas HTTP.

Asserções aprovadas: páginas2/2/1 com cinco próprios, empates por id DESC atravessando corte, conteúdo/count por quatro titulares de fixture distintos, vazio/página posterior, contagem de pedidos em vez de itens, lote com IDs mistos filtrado pelo titular e sem raízes duplicadas, ausência/alheios e raiz sem itens conservada por left fetch. Itens completos/posição/preços registrados e produtos estão acessíveis após retornar das transações do repositório, com categorias não inicializadas e sem SELECT tardio. A sequência do IN não é usada como ordenação da página; remontagem e cálculo/DTO transacional continuam na T014.

Cada leitura ocorre sem transação externa do chamador; os SELECTs observados estão em transações JPA. Snapshots JDBC completos antes/depois e observação SQL confirmam ausência de escrita detectada nessas20 janelas. Preservados os limites da T004: snapshot composto por duas transações, sem fotografia global atômica concorrente; StatementInspector não vê JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Preparação e limpeza ficam fora das janelas.

**0 mocks definidos,0 dispatches MockMvc e0 requisições HTTP reais**, em ambas as execuções. O contexto MOCK fornece o ambiente servlet necessário ao suporte recebido; a fixture apenas monta MockMvc, sem chamar login. Não há servidor/porta, autenticação HTTP ou jornada POST/orders. São testes de persistência, não testes unitários com repositório mockado nem aprovação da integração completa.

### Auditoria, limites e fechamento

Auditoria executada por `& 'target/feature007-validation/t013-list-repository-20261020-retry/audit.ps1'`: exit0 da conferência, `RepositoryComponentApproved=true`, `OwnOrderListingApproved=false`, `P2RealHttpApproved=false`. Confirmadas seleção idêntica das16 invocações, produção idêntica entre as execuções,20 janelas/30 SELECTs,15 fontes testados inalterados, oito Java recebidos preservados,1438 arquivos de evidências anteriores íntegros (incluindo a primeira T013) e relatórios arquivados/novos conferidos. `case-audit.json` identifica as invocações; os280 rastreados correspondiam à revisão testada antes do fechamento documental.

Evidências continuam distintas: T010 é RED pela rota ausente, sem isolar P2; T011 valida resolver com MockMvc, sem HTTP real/banco; T012 valida representação JSON; T013 valida consultas/carregamento JPA isolados. Endpoint, guarda/orquestração do serviço, remontagem, cálculo/mapeamento final e comprovação HTTP da lista/P2 permanecem nas tarefas posteriores. Não se declara integração completa, US1 entregue ou Feature007 concluída.

Revisados diff, repositório e nove Java não rastreados, referências/links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos não rastreados conferido individualmente por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico, exit1 esperado pela diferença de arquivo novo. Prefixo integral do quickstart recebido preservado; tasks recebida alterada somente no marcador T013. Os277 demais rastreados e oito Java recebidos, incluindo proteção/paginação/DTO/PageResponseDTO e suas asserções, permanecem idênticos ao início da T013.

**Somente T013 marcada nesta execução: T001–T013 concluídas, T014–T034 abertas,21 tarefas pendentes.** Git final: OrderRepository, quickstart e tasks modificados; nove Java não rastreados (oito recebidos e um criado); nada staged. Branch/HEAD/main/seleção007 preservados. Sem impedimento remanescente para a T013, execução T014+, stage, commit, push ou CI.

## T014 — Serviço de listagem própria e mapeamento transacional — 2026-10-20

**Escopo executado**: exclusivamente T014, dependente de T006 e T012–T013. Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente OrderRepository/quickstart/tasks rastreados modificados e nada staged. Os nove Java recebidos foram conferidos por caminho e SHA-256 em `target/feature007-validation/t014-list-service-20261020/received-java.json`: OwnOrderQueryWebMvcConfig, OwnOrderPageArgumentResolver, OwnOrderPageRequestDTO, OwnOrderSummaryDTO, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryListApiHttpIntegrationTest, OwnOrderQueryPageArgumentResolverTest, OwnOrderQueryDtoContractTest e OwnOrderQueryRepositoryTest. Todos permanecem intactos, assim como o repositório recebido, proteção, paginação, DTOs, entidades e asserções anteriores. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Criados somente [OwnOrderQueryService](../../src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java) e [OwnOrderQueryServiceTest](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java). O serviço `list(request, authentication)` executa a guarda escalar atual antes de ler pedidos e usa exclusivamente o ID devolvido pela guarda a partir do principal, em ambas as consultas existentes. Guarda, página/count, lote, cálculos e montagem dos DTOs terminam dentro de `@Transactional(readOnly=true)`. O lote é indexado por ID, conferido integralmente e remontado pela sequência da página, sem depender da ordem do IN; ID repetido, raiz extra/duplicada/alheia, lote incompleto e agregado incoerente falham internamente, sem sucesso parcial.

A leitura valida a coerência dos itens registrados e reutiliza `OrderItem.getSubtotal()` e `Order.getTotal()` com BigDecimal. Preço unitário, subtotal e total devem caber na capacidade positiva decimal19,2; normalização usa escala2/UNNECESSARY e a saída usa toPlainString. O DTO recebido continua aplicando seu verificador monetário existente. Nenhuma conversão float/double, arredondamento, reprecificação ou validação administrativa do produto. Página vazia dispensa fetch/IN; page/size/totais próprios são preservados, inclusive posição posterior. totalPages usa divisão/resto em long sem soma sujeita a overflow, faixa int32 conferida e conversão exata, sem usar a saturação de Page.getTotalPages(). Não há save, flush, clear, reparação, entidade retornada, dependência de OSIV ou componente T015+.

### Preparação e execução focal única

Skill `speckit-implement` limitada à T014; tarefa integral, dependências e fontes pertinentes lidas. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis. Java21.0.12.1 Azul Zulu conferido por `java -version`; Wrapper existente seleciona Maven3.9.16. Nenhum Java/Maven ativo antes da execução.

Testes complementares escritos antes do serviço, compilados e executados após disponibilizar sua interface. O F1-RED HTTP da T010 permanece evidência histórica de rota ausente; não se executou ausência de classe como RED nem se fabricou falha de cálculo já existente. Uma única execução sequencial na raiz, sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryServiceTest#list*' test
```

Executada por `& 'target/feature007-validation/t014-list-service-20261020/run.ps1'`, de `2026-10-20T14:28:56.9625850-03:00` a `2026-10-20T14:31:30.7098150-03:00`. **BUILD SUCCESS, exit0;39 testes,0 falhas,0 erros,0 ignorados**. Duração externa153,747s; duração Maven02:12. Não houve repetição de seleções anteriores, baseline, F0/F1-RED, T011–T013 ou suíte completa, nem mudança de cache/ordem/seed/configuração global.

Evidência própria em `target/feature007-validation/t014-list-service-20261020/`: comando/horários/ambiente/HEAD/status, diff recebido, documentos recebidos, manifestos, cópias de17 fontes, console e result.json. Antes do Maven, os dois relatórios recebidos da repetição aprovada da T013 foram movidos de `target/surefire-reports/` para `received-surefire-reports/` no diretório inédito da T014. Origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita; tamanhos/caminhos/SHA-256 conferidos após mover (`archive-preservation.json`:2, HashesMatch=true). Um XML e um TXT novos permanecem em `target/surefire-reports/` e na cópia `reports/` da T014. As duas execuções da T013, inclusive a primeira falha da expectativa SQL e sua justificativa/correção para OFFSET na página zero, permanecem preservadas como evidências distintas.

### Seleção efetiva e alcance das provas

Console/XML/TXT confirmam somente `com.devsuperior.dscommerce.services.OwnOrderQueryServiceTest`,13 métodos `list*`,39 invocações únicas, sem falhas/erros/ignorados, duplicatas, contêineres agregadores ou classes @Nested. As sete parametrizações têm todos os índices1..N conferidos em `invocation-index-audit.json`; nomes completos em `case-audit.json` e contagens em `methods.json`.

| Método | Invocações | Prova |
| --- | ---: | --- |
| `listRealPagesMapHistoricalTotalsAndKeepTimestampIdOrderAfterReturn` |1| JPA real, páginas2/2/1, empates entre páginas, manifesto independente e totais históricos42.50/20.00/0.01/9007199254740993.01/15.25. |
| `listRealSuccessivePrincipalsIncludeAdminAloneAndBothWithoutSharingOwner` |1| JPA real, cinco consultas sucessivas por CLIENT/outro/ADMIN/ambos/CLIENT, conteúdo e count próprios. |
| `listRealEmptyPagesKeepRequestedMetadataAndSkipBatch` |4| JPA real, vazio inicial/posterior, página além dos resultados e offset máximo válido; metadados preservados, sem IN. |
| `listRealGuardRechecksCurrentScalarRolesBeforeOrderQueries` |1| JPA real, revogação commitada apesar de authority ADMIN no token de teste, conta ausente e autenticação ausente; exceções de acesso distintas, sem consulta de pedidos. |
| `listRealRootWithoutItemsFailsWithoutRepairOrPartialSuccess` |1| JPA real, raiz mantida pelo left fetch após preparação JDBC sem itens; falha interna, sem reparação/escrita. |
| `listReordersShuffledBatchAndCompletesMappingInsideReadOnlySpringTransaction` |1| Guarda/repositório simulados, proxy Spring e transação JPA/H2 reais; guarda/consultas/getTotal dentro de readOnly, ordem dos IDs restaurada e nenhuma operação adicional no repositório. |
| `listComputesLongMetadataWithoutUsingSaturatingPageTotalPages` |4| Mocks, ceil em long, zero/página posterior, totalElements acima de int32 e totalPages no limite; sem getTotalPages/fetch vazio. |
| `listUnrepresentableTotalPagesFailsBeforeBatchWithoutLongOverflow` |2| Mocks, totalPages fora de int32, inclusive totalElements Long.MAX_VALUE; falha interna antes do lote. |
| `listInconsistentBatchFailsAsInternalErrorInsteadOfPartialContent` |8| Mocks/entidades em memória, lote incompleto/extra/duplicado/alheio, raiz sem itens/data/estado e IDs repetidos; sem conteúdo parcial. |
| `listUsesExactRegisteredBigDecimalMoneyWithoutCatalogRepricing` |5| Mocks/entidades em memória, centavo, valor acima da precisão binária, capacidade máxima e escalas exatas normalizáveis; produto atual com preço0. |
| `listRejectsIncoherentMoneyAndQuantityWithoutRounding` |7| Mocks/entidades em memória, preço zero/negativo/subcentavo/overflow, quantidade zero/negativa e subtotal acima da capacidade. |
| `listRejectsTotalBeyondCapacityEvenWhenEachItemAndSubtotalFits` |1| Mocks, soma não representável apesar de linhas individualmente representáveis. |
| `listReadFailuresPropagateWithoutEmptyOrAuthenticationSubstitution` |3| Mocks, falhas da guarda/página/lote propagam sem conversão em vazio/ausência/credencial inválida; guarda falha antes de qualquer leitura de pedidos. |

**Oito invocações de serviço com persistência JPA/H2 real e31 com mocks locais de guarda/repositório**; a do proxy usa transação real para inspecionar readOnly, com entidades em memória. Nenhum bean produtivo é substituído no ApplicationContext compartilhado. Os dados persistidos são preparados pela fixture recebida, em JDBC commitado fora da leitura; expectedTotal vem do manifesto, sem chamar o cálculo produtivo para produzir o esperado. Limpeza somente dos dados próprios após cada cenário. Um contexto completo SpringBootTest MOCK, perfil test, URL exclusiva/estável `jdbc:h2:mem:admin-product-test-37af2b81-4906-4807-ab14-2411a5c3636a`, create-drop e inicialização SQL/import desabilitada somente nesse contexto, OSIV false e coleção paginada configurada para falhar no teste.

Nas leituras bem-sucedidas,12 janelas incluem guarda/página/count quando executado/lote: há um fetch de lote para conteúdo não vazio e nenhum para vazio, com titular nos SQLs de pedidos e sem paginação de coleção. Serialização do DTO após retorno transacional não provoca SELECT tardio. As outras quatro chamadas reais rejeitadas (três de guarda e uma de agregado sem itens) também conferem snapshots antes/depois e somente SELECTs observados. Não há orçamento global rígido de SELECTs nem exigência de count onde Spring Data legitimamente o otimiza. Mantidos os limites do probe T004: snapshots em duas transações não são fotografia global atômica; StatementInspector não vê JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada.

**0 dispatches MockMvc,0 logins e0 requisições HTTP reais.** A fixture monta MockMvc, mas não o despacha; autenticações fornecidas diretamente ao serviço não são JWTs decodificados/filtros HTTP. A revogação aqui verifica a guarda real antes da leitura, sem substituir a prova específica de coleção gerenciada obsoleta da T023. Mocks de falhas não comprovam count SQL falho, commit falho ou envelope HTTP500. Exceções internas de incoerência são destinadas ao500 do advice futuro; status/mensagens/headers HTTP, validação MVC integrada e P2 HTTP permanecem nas T015/T016 e fases posteriores.

### Auditoria, preservação e fechamento

`& 'target/feature007-validation/t014-list-service-20261020/audit.ps1'`: exit0, Pass=true, ServiceComponentApproved=true, OwnOrderListingHttpApproved=false e P2RealHttpApproved=false. Conferidos290 arquivos iniciais (280 rastreados, nove Java recebidos e ponteiro local),17 fontes testados,1481 arquivos de evidência prévia e os relatórios recebidos/novos por SHA-256. A primeira tentativa do auxiliar terminou exit1 por enumeração de array JSON no PowerShell5.1; corrigida somente a leitura do auxiliar e repetida a auditoria, sem alterar fontes/testes ou repetir Maven. `audit-preparation-note.txt` registra a causa; isso não é RED nem falha do Maven.

Revisados serviço/teste novos, diff, nove Java recebidos, referências/links locais e whitespace. Fechamento documental altera somente este acréscimo e o marcador T014 em tasks; prefixo integral do quickstart recebido preservado byte a byte, incluindo as duas T013 e a justificativa SQL. `git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos11 Java não rastreados conferido individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 admissível pela diferença de arquivo novo). `final-review.ps1` registra a revisão em `final-review.json`; fontes executados, demais rastreados, branch/HEAD/main e seleção007 preservados.

**Somente T014 marcada nesta execução: T001–T014 concluídas, T015–T034 abertas,20 tarefas pendentes.** Git final: os mesmos três rastreados recebidos modificados (OrderRepository/quickstart/tasks),11 Java não rastreados (nove recebidos e os dois novos), nada staged. Sem impedimento remanescente da T014, componente T015+, stage, commit, push, CI ou alteração de main. Serviço validado no seu escopo; integração HTTP da lista, US1 e Feature007 continuam incompletas. A Feature005 mantém EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.
## T015 — Fronteira MVC da lista e advice exclusivo — 2026-10-20

**Escopo executado**: somente T015, dependente de T011/T014. Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente OrderRepository/quickstart/tasks rastreados modificados e nada staged. Conferidos os onze Java recebidos por caminho/SHA-256 em `target/feature007-validation/t015-query-controller-20261020/received-java.json`: OwnOrderQueryWebMvcConfig, OwnOrderPageArgumentResolver, OwnOrderPageRequestDTO, OwnOrderSummaryDTO, OwnOrderQueryService, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryListApiHttpIntegrationTest, OwnOrderQueryPageArgumentResolverTest, OwnOrderQueryDtoContractTest, OwnOrderQueryRepositoryTest e OwnOrderQueryServiceTest. Esses fontes e o repositório recebido foram preservados, assim como proteção, consultas por titular, paginação, DTO, serviço e evidências anteriores. Main mantida em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Criados [OwnOrderQueryController](../../src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java), [OwnOrderQueryExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java) e [OwnOrderQueryControllerTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryControllerTest.java). GET/orders usa `@Valid OwnOrderPageRequestDTO` sem `@ModelAttribute`, pelo resolver recebido, e delega ao serviço com a mesma Authentication. O controller não calcula, consulta banco, escolhe titular ou trata regras de negócio. HEAD passa pela mesma entrada/serviço; a resposta suprime explicitamente o corpo e mantém application/json. Não há rota de detalhe nova.

O advice tem prioridade máxima e `assignableTypes=OwnOrderQueryController.class`. Rejeições de binding/conversão/Bean Validation da entrada recebem400 fixo; BadCredentials401 com Bearer challenge; AccessDenied403; lookup, persistência, cálculos, validação interna/de retorno, mapeamento e conclusão transacional recebem500 seguro. Não percorre causas de validação para reclassificar falha interna como erro de acesso. Corpo com somente status/error/message/path, mensagens contratuais e requestURI sem query, inclusive context path; HEAD sem corpo. Erros MVC nativos de método/negociação conservam status/headers, sem forçar corpo incompatível. GlobalExceptionHandler, OrderController/POST, segurança e demais componentes/contratos001–006 não foram alterados.

### Preparação e duas execuções focais

Skill `speckit-implement` restrita à T015, com leitura da tarefa integral, dependências, requisitos e artefatos pertinentes. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis. Java21.0.12.1 Azul Zulu conferido por `java -version`; Wrapper seleciona Maven3.9.16. Nenhum Java/Maven ativo antes de cada execução. Teste focal escrito antes dos componentes novos, sem executar ausência de classe como RED; o F1-RED histórico da T010 permanece preservado.

Comando idêntico nas duas execuções sequenciais, na raiz, sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryControllerTest' test
```

| Diretório sob target/feature007-validation | Início/fim (-03:00) | Resultado |
| --- | --- | --- |
| `t015-query-controller-20261020/` |14:41:39.1718489 /14:42:43.1046836|31 testes,2 falhas,1 erro,28 aprovados,0 ignorados; BUILD FAILURE, exit1. |
| `t015-query-controller-20261020-retry/` |14:43:40.3131492 /14:44:40.4089472|Os mesmos31 testes,0 falhas/erros/ignorados; BUILD SUCCESS, exit0. |

Primeira execução diagnosticada em `diagnosis.json`, sem erro de compilação ou contexto: (1) `headFollowsTheListAndHasNoBodyIncludingFailures[1]` observou corpo no HEAD de sucesso no slice MockMvc; o controller passou a suprimi-lo explicitamente depois da mesma chamada ao serviço. Controller/advice também passaram a declarar application/json no HEAD. Isso não é reprodução por HTTP real. (2) O teste nativo exigia HEAD no Allow do405, mas o Spring informa os mappings explícitos GET/POST; corrigida a expectativa para conferir exatamente os métodos suportados e todos os valores dos headers fornecidos pela exceção nativa, sem alterar a resposta do framework nem asserções funcionais de status. HEAD implícito continua testado separadamente. (3) A segunda chamada `when(registration.register(...))` disparava o stub anterior ao preparar a próxima falha; substituída por doThrow().when(), sem mudar expectativas de403/500. Trata-se de erro de preparação Mockito, não falha produtiva do POST. Os casos nativo e de escopo interromperam dispatches posteriores:32 na primeira execução,35 na repetição completa. O construtor não obsoleto do resultado de validação de retorno foi usado na correção do teste.

A repetição foi necessária pelas correções concretas nos três arquivos novos; produção recebida, testes anteriores e suas asserções ficaram intactos. `execution-comparison.json` confirma os mesmos31 IDs de caso e17 outros fontes capturados idênticos entre as execuções. São31 casos distintos e62 invocações somadas, não62 casos diferentes. Nenhum RED artificial, seleção antiga, baseline, F1-GREEN/T016, suíte completa ou componente de detalhe foi executado. Cache, ordem, seeds e configuração global permaneceram inalterados.

Cada execução tem run.ps1, session-start.json, result.json, console integral, diff recebido, documentos e manifestos/cópias de20 fontes. Antes de cada Maven, os dois relatórios recebidos de `target/surefire-reports/` foram movidos para `received-surefire-reports/` do destino inédito correspondente, após conferir caminhos absolutos dentro de target, ausência de sobreposição/sobrescrita e hashes/tamanhos após mover. A primeira arquivou os relatórios T014; a repetição arquivou os da primeira T015. Cada execução tem seu XML/TXT em reports; os correntes pertencem somente à repetição. Toda a evidência anterior, inclusive as duas T013, sua justificativa SQL e a T014, permanece preservada.

### Seleção efetiva e limites

Console/XML/TXT confirmam somente `com.devsuperior.dscommerce.controllers.OwnOrderQueryControllerTest`, oito métodos e31 invocações únicas, mesmas identidades antes/depois. Conferidos todos os índices das seis parametrizações, sem duplicatas/omissões, contêineres agregadores ou testes @Nested. As classes internas são configuração/controller de controle, sem testes JUnit próprios. Nomes e índices em case-audit.json, methods.json e invocation-index-audit.json da repetição.

| Método | Invocações | Prova focal |
| --- | ---: | --- |
| `listDelegatesTheValidatedInputAndSameAuthenticationAndSerializesOnlyThePage` |2| Controller real, entrada default/explícita e Authentication idêntica; JSON exato da página fornecida pelo mock. |
| `listRejectsBindingAndBeanValidationErrorsBeforeCallingTheService` |8| Conversão/constraints/multiplicidade,400 exato sem chamada aos serviços, path sem query. |
| `listUsesTheDedicatedResolverWithoutBindingAliasesAgain` |2| Integração do resolver recebido com o controller real, aliases descartados sem rebinding; não é a matriz P2 HTTP completa. |
| `listTranslatesServiceFailuresWithSafeExactEnvelopeAndBearerChallenge` |10| Exceções simuladas de acesso/lookup/leitura/transação/finanças/validação/retorno/mapeamento;401/403/500 exatos e challenge só no401. |
| `headFollowsTheListAndHasNoBodyIncludingFailures` |5|200/400/401/403/500 sem corpo, application/json e challenge pertinente; serviço não chamado no400. |
| `nativeMethodAndNegotiationFailuresKeepStatusesAndHeadersWithoutServiceCalls` |1| PUT405 com headers nativos exatos e Accept incompatível406, sem serviço. |
| `nativeErrorsRaisedWithinTheSelectedHandlerAreNotChangedIntoInternalFailures` |2| Exceções MVC nativas simuladas dentro do handler selecionado preservam405/406 e headers. |
| `scopedAdviceLeavesRegistrationAndPublicErrorsToTheExistingGlobalAdvice` |1| Dispatches POST no MVC com serviço simulado mantém400/403/500 do registro; controller público de controle mantém404 do advice antigo. |

**35 dispatches MockMvc,0 logins,0 requisições HTTP reais e0 contextos de persistência na execução aprovada**. Uma AnnotationConfigWebApplicationContext somente MVC, resolver/validator/conversion service/controllers/advices/conversores Jackson reais e dois serviços Mockito (consulta e registro). Não há datasource, JPA/H2, servidor/porta, filtros de segurança, decoder ou JWT real. Requisições ROOT/API diferem pelo contextPath informado ao MockMvc, dentro desse único contexto; não são os dois servidores reais de aceitação. O controller público de controle tem perfil exclusivo de teste para impedir descoberta nos contextos completos.

Página, dinheiro e metadados de sucesso vêm diretamente do mock: não aprovam cálculo, titularidade, count ou consultas. Exceções de persistência, commit e escrita da resposta são injetadas na chamada simulada ao serviço; não representam falha real de SQL, conclusão transacional ou serialização parcial. O resultado de validação de retorno é montado no teste para exercitar sua classificação, sem alegação de novo contrato de retorno validado. Ausência de chamadas ao serviço no400 é prova da fronteira MVC; segurança de infraestrutura pode consultar conta antes do MVC em execução completa. Escopo do advice foi exercitado com POST e um controle público, sem reexecutar/aprovar todas as jornadas001–006. O checkpoint conjunto F1-GREEN, segurança/persistência integradas, materialização e P2 por HTTP real permanecem na T016 e fases posteriores.

### Auditoria e fechamento

`& 'target/feature007-validation/t015-query-controller-20261020-retry/audit.ps1'`: exit0, Pass=true, MvcBoundaryApproved=true, OwnOrderListingHttpApproved=false e P2RealHttpApproved=false. Conferidos292 arquivos iniciais (280 rastreados, onze Java recebidos e seleção local),20 fontes da revisão aprovada,1567 arquivos de evidência prévia, dois relatórios recebidos e dois novos por SHA-256. Nenhuma mudança de fonte após o GREEN; a primeira execução falha permanece identificada como tal.

Revisados diff, três Java novos e onze recebidos, caminhos/links e whitespace. Fechamento documental acrescenta somente esta seção e marca T015; prefixo integral recebido do quickstart preservado byte a byte. `git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos14 Java não rastreados conferido individualmente com `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 admissível por arquivo novo). A revisão final fica em final-review.json, gerada por final-review.ps1 da repetição; demais fontes, contratos, branch/HEAD/main e seleção007 preservados.

**Somente T015 marcada nesta execução: T001–T015 concluídas, T016–T034 abertas,19 tarefas pendentes.** Git final: os mesmos três rastreados recebidos modificados (OrderRepository/quickstart/tasks),14 Java não rastreados (onze recebidos e três novos), nada staged. Sem impedimento remanescente da T015, stage, commit, push, CI, detalhe de pedido ou alteração de main. A fronteira MVC está validada no recorte executado; não se declara US1, integração completa ou Feature007 concluída. Feature005 continua EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.
## T016 — Checkpoint conjunto F1-GREEN da US1 — 2026-10-20

**Escopo executado**: exclusivamente T016, após T011–T015. Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `1210b803c54faa3b7c4f529e1947af3a50f8d41f`, seleção local Feature007, somente OrderRepository/quickstart/tasks rastreados modificados e nada staged. Conferidos os quatorze caminhos Java não rastreados recebidos, com SHA-256 em `target/feature007-validation/t016-f1-green-20261020/received-java.json`: OwnOrderQueryWebMvcConfig, OwnOrderPageArgumentResolver, OwnOrderQueryController, OwnOrderQueryExceptionHandler, OwnOrderPageRequestDTO, OwnOrderSummaryDTO, OwnOrderQueryService, OwnOrderQueryControllerTest, OwnOrderQueryListHttpIntegrationTest, OwnOrderQueryListApiHttpIntegrationTest, OwnOrderQueryPageArgumentResolverTest, OwnOrderQueryDtoContractTest, OwnOrderQueryRepositoryTest e OwnOrderQueryServiceTest. Todos os fontes recebidos, contratos e asserções permaneceram intactos. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

### Conferência da T015 e do RED recebido

Relidos os registros e diagnósticos das duas falhas/um erro iniciais da T015 e comparadas as cópias antes/depois do teste: a asserção HEAD sem corpo/status/challenge foi mantida, com application/json acrescentado; o controller passou a suprimir explicitamente o corpo de sucesso depois da mesma chamada ao serviço. A expectativa incorreta de HEAD no Allow do405 foi substituída por comparação exata dos mappings GET/POST e de todos os headers nativos, sem alterar status ou resposta do framework; HEAD implícito continua coberto pelo método específico. O segundo stubbing Mockito passou a doThrow().when(), preservando as expectativas403/500 do POST. A adaptação da reflexão e do construtor do resultado de validação de retorno não mudou o500 esperado. Nenhum relaxamento contratual ou enfraquecimento funcional foi identificado. `t015-reviewed.json` confirma que controller/advice/teste atuais correspondem por hash às cópias da repetição aprovada. Essa revisão é estática/histórica; o focal31 da T015 não foi repetido na T016.

Os dois arquivos HTTP da listagem são byte a byte idênticos aos executados na T010, conforme `http-tests-vs-t010.json`. Preservado o F1-RED histórico:230 testes,230 falhas pelo GET ausente/405, zero erros/ignorados; aquele405 não comprovava validação, conteúdo nem P2. Na execução atual, as mesmas230 identidades de caso alcançaram as asserções completas de JSON/paginação; métodos com múltiplas consultas completaram também suas requisições posteriores antes interrompidas. Nenhum teste ou produção foi corrigido nesta T016.

### Execução única aprovada

Skill `speckit-implement` limitada à T016; tarefa integral, dependências, protocolo e fontes pertinentes lidos. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007. Checklist somente lida:16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks. `java -version`:21.0.12.1 Azul Zulu; Wrapper existente seleciona Maven3.9.16. Nenhum Java/Maven ativo ao preparar a execução. Sem clean, concorrência Maven, alteração de cache/ordem/seed ou configuração global.

Exatamente o seletor F1-GREEN de tasks.md, executado da raiz:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest#list*' test
```

Chamado por `& 'target/feature007-validation/t016-f1-green-20261020/run.ps1'`. Início `2026-10-20T14:52:14.2375410-03:00`, fim `2026-10-20T14:53:44.0134535-03:00`; duração externa89,776s e Maven01:24. **BUILD SUCCESS, exit code0;576 testes,0 falhas,0 erros,0 ignorados.** Uma única execução, sem repetição adicional, baseline, suíte completa, focal da T015, segurança F0/F3, detalhe ou tarefa T017+.

Antes do Wrapper, os dois relatórios recebidos da repetição da T015 foram movidos de `target/surefire-reports/` para `received-surefire-reports/` no diretório inédito da T016. Conferidos caminhos absolutos resolvidos dentro de target, ausência de sobreposição/sobrescrita e hashes/tamanhos após mover (`archive-preservation.json`:2, HashesMatch=true). Os12 relatórios novos (seis XML/seis TXT) estão em `target/surefire-reports/` e na cópia própria `reports/`. Console, exit code, comando, horários, ambiente, HEAD/status/diff, documentos recebidos, manifestos e cópias de20 fontes ficam no diretório da execução. Nenhum resultado antigo foi contado como novo.

### Seleção Surefire e transporte reconciliados

Console/XML/TXT conferem as seis classes concretas abaixo. São62 métodos por classe concreta,576 invocações únicas e todos os índices parametrizados esperados. As identidades foram confrontadas com as seleções históricas correspondentes e os testes selecionados conservam seus hashes. Não há testes @Nested neste seletor: suportes abstratos, records, configurações e controllers internos não são suítes adicionais nem casos omitidos. Nenhum método fora de list* do serviço foi incluído.

| Classe concreta | Invocações | Natureza da execução |
| --- | ---: | --- |
| `OwnOrderQueryListHttpIntegrationTest` |115| HTTP real/JWT/filtros/JPA, servidor raiz. |
| `OwnOrderQueryListApiHttpIntegrationTest` |115| HTTP real/JWT/filtros/JPA, servidor /api. |
| `OwnOrderQueryPageArgumentResolverTest` |254| Componente MVC/validação e provas diretas com mocks de infraestrutura do resolver. |
| `OwnOrderQueryDtoContractTest` |37| Slice JSON, sem banco/MVC/HTTP. |
| `OwnOrderQueryRepositoryTest` |16| JPA/H2 real, sem HTTP ou mocks do repositório. |
| `OwnOrderQueryServiceTest#list*` |39| Oito invocações com serviço/JPA reais;31 com mocks locais, incluindo proxy com transação real e dados em memória. |

**258 requisições HTTP reais:246 GET/orders e12 POST/login.** Em cada contexto:123 GET (75 respostas200 e48 respostas400) e seis logins. São230 casos JUnit HTTP, não258 testes; métodos de travessia/identidades fazem múltiplos GET. Os217 dispatches MockMvc pertencem somente ao teste do resolver; as fixtures JPA/HTTP montam MockMvc, mas não o despacham nem fazem login por ele. Não há mocks nos dois testes HTTP; os mocks de serviço e de infraestrutura são provas complementares separadas.

Contextos HTTP completos com porta aleatória, OSIV false, perfil test e bancos H2 exclusivos/estáveis pelo inicializador recebido: raiz porta61520, `jdbc:h2:mem:admin-product-test-5a943448-733b-40a2-aab3-87656eb4ec9c`; /api porta61468, `jdbc:h2:mem:admin-product-test-1beeb80b-9547-4e83-b231-fce66c65e83d`. Componentes usam outros dois H2: repositório `admin-product-test-acba21ef-537d-4bfc-b520-edf4dc899c54` e serviço `admin-product-test-69350d59-65e5-4088-8003-c55dd0a8eed0`. Create-drop e inicialização SQL/import desabilitada somente nos contextos isolados; seeds/perfis globais preservados. Dados são preparados por JDBC commitado com manifesto independente e limpeza própria; login é HTTP real. Não há jornada POST/orders nova nesta seleção.

### US1 e P2 comprovados na execução atual

Nos contextos raiz e /api, aprovados US1.1–US1.7: somente os próprios três pedidos no cenário3/2; navegação2/2/1 sobre cinco próprios com alheios intercalados; empates por createdAt resolvidos por id DESC atravessando páginas; vazios com totais0/0 e página posterior com totais5/3 sem clamp; entradas exatas inválidas rejeitadas; contas sucessivas CLIENT/outro/ADMIN isolado/ambos/CLIENT conservando conteúdo e contagens próprios. accountId/buyerId/sort e variantes desconhecidas não mudam dono ou ordem. JSON confere campos exatos da página/resumo, IDs/metadados integrais, instante UTC com milissegundos, estado armazenado e dinheiro textual do manifesto, inclusive42.50,20.00,0.01 e9007199254740993.01. O preço atual diferente do catálogo não substitui o histórico.

| Grupo HTTP, em cada contexto | Casos | Resultado específico atual |
| --- | ---: | --- |
| `listP2IgnoresAliasesBeforeBindingWithDefaultsAndExplicitValues` |50| Todos200; `_page`, `_size`, `size[]`, `!page`, separados e combinados, variantes inteiro/vazio/texto/fracionário/repetido: page0/size12 por omissão ou page1/size2 explícitos, conteúdo/totais esperados intactos. |
| `listP2NeverLetsIgnoredAliasesRepairInvalidExactParameters` |20| Todos400; quatro entradas exatas inválidas/repetidas combinadas com as cinco variantes de aliases, envelope seguro e path sem query. |
| `listUs16RejectsInvalidEmptyRepeatedOrOverflowingPagination` |28| Todos400; inválidos exatos, vazios, repetidos, limites/overflow preservados. |
| `listDefaultsAndLimitsPreserveExactMetadata` |9| Todos200; defaults, size1/50 e maiores offsets válidos, inclusive vazios sem clamp. |

`audit-p2.ps1`: exit0;140 variantes P2 (100 positivas e40 negativas) confrontadas individualmente por classe/caso/índice/query/status em XML e console, com nomes originais decodificados e todas as combinações previstas. Detalhes em `p2-variant-coverage.json` e `p2-results.json`; o page/size/conteúdo de cada resultado vem das asserções JUnit aprovadas, não é inferido só do status200. Os400 e a integração do resolver com Bean Validation são complementados pelos testes MVC da entrada, que verificam rejeição antes do handler; a ausência de chamada ao serviço no controller400 foi comprovada pelo focal histórico da T015, preservado sem nova execução. Segurança de infraestrutura pode consultar conta antes do MVC.

### SQL, materialização e ausência de escrita

Revisados [repositório](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java), serviço e asserções/logs dos componentes executados. Na prova JPA do repositório:20 janelas,13 páginas de IDs,10 counts efetivamente necessários e sete lotes,30 SELECTs observados. SQL mantém buyer_id em página/count/lote, ordenação createdAt DESC/id DESC, projeção de IDs sem coleção e offset/limite no banco. Fetch usa left joins somente de items/product, sem Pageable/limite/categorias, com raízes e itens completos, posições e valores históricos, sem duplicação ou SELECT tardio. A expectativa SQL corrigida na T013 (página zero legitimamente sem OFFSET) permaneceu intacta.

No serviço real:12 janelas bem-sucedidas,37 instruções observadas e oito lotes; conteúdo não vazio usa um lote e vazio nenhum, sem IN vazio. Guarda vem primeiro e consultas de pedidos têm titular obrigatório. DTOs serializados depois do retorno transacional com OSIV false não provocam SELECT adicional. As quatro chamadas reais rejeitadas do serviço (três de guarda e uma raiz sem itens) mantêm snapshots e SELECTs apenas. O teste complementar com mocks confirma guarda/consultas/cálculo dentro de readOnly e remontagem de lote embaralhado. Esses números descrevem as janelas de componentes, não um orçamento rígido de consultas HTTP. O plano de carregamento e as provas de materialização não dependem de impor count em páginas nas quais Spring Data o otimiza legitimamente. Detalhes em `repository-sql.json` e `query-materialization-audit.json`.

Cada GET HTTP foi envolvido por snapshot JDBC independente antes/depois e verificação de SELECTs somente na janela, excluindo setup/login/limpeza. As mesmas proteções passaram nas leituras de componentes selecionadas. Nenhuma mutação detectada em produtos/categorias/associações/contas completas/perfis/pedidos/itens; readOnly não é usado como prova isolada. Permanecem os limites do probe: snapshot composto em duas transações, sem fotografia global atômica concorrente; StatementInspector não vê JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Não se conclui US4 inteira por essas janelas.

A seleção atual prova acesso bem-sucedido próprio com JWT/filtros reais e três combinações permitidas de perfis, mais a guarda real do serviço nas rejeições selecionadas. Não reexecuta authenticationGate, rejeições JWT/HEAD por HTTP, detalhe/404 alheio, coleção gerenciada obsoleta, falhas HTTP500 injetadas, jornadas completas001–006 ou suíte completa. As evidências F0/T015 continuam históricas para seus respectivos recortes; consolidação US3/US4 e FINAL permanecem futuras. Navegação exata considera conjunto estável; não há promessa de snapshot entre requisições concorrentes.

### Auditoria e fechamento da T016

`& 'target/feature007-validation/t016-f1-green-20261020/audit.ps1'`: exit0, Pass=true, F1GreenApproved=true, P2RealHttpApproved=true e Feature007Complete=false. Conferidos295 arquivos iniciais (280 rastreados, quatorze Java recebidos e ponteiro),20 fontes capturados,1614 arquivos de evidência prévia, dois relatórios recebidos e12 novos por SHA-256. `case-audit.json`, `methods.json`, `suites.json`, `invocation-index-audit.json`, `http-case-results.json` e `http-contexts.json` identificam seleção, resultados e contextos. Nenhuma correção, segunda execução Maven ou fonte nova foi necessária.

Revisados diff, quatorze Java recebidos não rastreados, referências/links e whitespace. Alterados nesta tarefa somente este acréscimo e o marcador T016 em tasks. Prefixo integral do quickstart recebido, incluindo T010 e as duas execuções de T013/T015, preservado byte a byte. `git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos14 Java não rastreados conferido individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 admissível por arquivo novo). `final-review.ps1` registra o fechamento em final-review.json; demais arquivos e fontes executados permanecem íntegros.

**Somente T016 marcada nesta execução: T001–T016 concluídas, T017–T034 abertas,18 tarefas pendentes.** Checkpoint C2/US1 aprovado no recorte definido; US2–US4 e fechamento continuam obrigatórios para concluir a feature. Git final: os mesmos três rastreados recebidos modificados (OrderRepository/quickstart/tasks), os mesmos14 Java não rastreados, nada staged. Branch/HEAD/main/seleção007 preservados. Sem impedimento da T016, stage, commit, push, CI, detalhe ou execução T017+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T017 — Escrita dos testes HTTP do detalhe — 2026-10-20

**Escopo executado: exclusivamente T017, escrita e revisão estática.** Checkout inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, Git limpo, nada staged e seleção local Feature007. Esse HEAD contém a US1/T009–T016, `feat: list own orders`; main permanece em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Dependência T016 satisfeita pelo checkpoint registrado acima, sem repeti-lo.

Criados somente [OwnOrderQueryDetailHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java) e [OwnOrderQueryDetailApiHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailApiHttpIntegrationTest.java). O primeiro contém a classe concreta raiz e o suporte abstrato `OwnOrderQueryDetailHttpTestSupport`; a classe concreta API herda os mesmos sete métodos de cenário. Não há `@Nested` nem herança dos testes US1 ou `authenticationGate`. A seleção futura `OwnOrderQueryDetail*` deve descobrir somente essas duas classes concretas com testes, sem contar o suporte abstrato como suíte adicional.

### Cenários escritos — expectativas futuras, sem execução

| Cenário | Método compartilhado | Expectativa escrita |
| --- | --- | --- |
| US2.1 | `detailUs21ReturnsExactFieldsAndRecordedBAOrderWithHistoricalMoney` | A é inserido antes de B, mas os itens são registrados B/A nas posições0/1: qty2/3, unitPrice10.00/7.50, subtotal20.00/22.50 e total42.50. Conferência integral de campos, identificação, nomes, instante e estado, mais concordância com a lista. |
| US2.2 | `detailUs22ReadsOwnOrderOutsideThePreviouslyRequestedPage` | Três compras próprias: página0/size1 contém só a mais recente; detalhe direto da mais antiga exige200. Compara seu cabeçalho com página2 e repete o detalhe após consultar página1, sem estado de seleção da página anterior. |
| US2.3 | `detailUs23UsesRenamedCurrentProductWithoutReplacingHistoricalPrice` | Lista/detalhe antes e depois de alterar produto de Nome antigo/10.00 para Nome atual/12.00. Compra qty2 conserva unitPrice10.00, subtotal/total20.00 e passa a apresentar o nome atual. |
| US2.4 | `detailUs24KeepsDistinctProductIdsAndPositionsAfterNamesBecomeEqual` | Dois produtos diferentes são renomeados para ` Nome igual ` depois da compra. Itens continuam B/A, com IDs distintos em ordem inversa à de inserção dos produtos, nomes integrais iguais e finanças próprias, sem fusão. Lista/detalhe conferidos antes/depois. |
| US2.5 | `detailUs25ReadsLegacyNameZeroCurrentPriceAndNoCategoriesWithoutRepair` | Duas variantes do nome atual: `Á` (1 caractere) e espaço + `Café ` repetido17 vezes + espaço (87 caracteres). Fora dos limites administrativos3–80 e dentro do varchar255 existente. Após compra válida, prepara nome legado e preço atual0.00 por JDBC commitado; confirma ausência de categorias. Lista/detalhe devem preservar nome integral e compra20.00, sem correção dos dados. |
| US2.6 | `detailUs26PreservesExactSmallAndLargeTextualMoneyInListAndDetail` | Duas variantes: `0.01` e `9007199254740993.01`. unitPrice/subtotal/total são texto exato do manifesto e obedecem ao formato decimal19,2 vigente, com ponto e duas casas; lista/detalhe concordam, sem float, expoente ou arredondamento. |
| US2.7 | `detailUs27KeepsEachPurchasesOwnQuantitiesPricesSubtotalsAndTotal` | Mesmos dois produtos em duas compras distintas. Primeira B2×10.00 + A3×7.50 =42.50; segunda B3×12.00 + A2×8.00 =52.00. Lista contém ambos em ordem esperada; cada detalhe e nova consulta da segunda conservam suas quantidades, preços, subtotais e total, diferentes do catálogo99.00/100.00. |

Os helpers exigem exatamente `id/createdAt/status/total/items` no detalhe e `productId/productName/quantity/unitPrice/subtotal` em cada item. A lista exige os cinco campos da página e quatro por resumo. IDs/quantidades/metadados são números integrais, instante é texto UTC com milissegundos e status coincide com o manifesto armazenado `WAITING_PAYMENT`. Dinheiro é comparado como texto com valores explícitos, além da expressão do contrato; nenhum esperado vem de `getTotal()`, `getSubtotal()` ou mapper produtivo. Campos extras, inclusive accountId, itemId, posição, categorias e preço atual, são rejeitados pelas listas exatas de campos.

Previsão por revisão dos fontes: **cinco métodos `@Test` e dois parametrizados com duas variantes cada; nove invocações por contexto,18 no total**. Essa é contagem escrita/prevista, não descoberta Surefire, resultado JUnit ou número de requisições realizadas. Alguns métodos têm vários GET e podem interromper-se na primeira asserção falha. Todas as consultas exigem200: não se aceita404/405 como sucesso do detalhe nem como prova de ownership. A T018 deverá verificar a seleção e a causa real de eventual falha antes de qualquer implementação/correção.

### Suporte e transporte preparados, ainda não exercitados

Reutilizados sem edição `OwnOrderQueryTestFixture`, `AdminProductTestConfig`, `OwnOrderQueryTestObservationConfig` e `OwnOrderQueryDatabaseProbe` aprovados. Cada classe configura aplicação completa em porta aleatória, perfil test, OSIV false, H2 console false e propriedade exclusiva `feature007.own-order-detail-test-context=root/api`, distinta das propriedades dos contextos anteriores. Inicializador mantém URL H2 exclusiva/estável por ApplicationContext, create-drop e SQL/import desabilitados somente no contexto isolado. O setup escrito confere esses valores, metadata JDBC e banco vazio; nenhuma configuração/cache/seed global foi alterada.

Cada invocação prepara seu próprio CLIENT e compras por JDBC commitado, com manifesto independente e limpeza apenas das linhas da fixture, itens/pedidos antes de produtos/contas. Renomes e alterações de preço são preparação explícita posterior à compra em transação REQUIRES_NEW, fora das janelas observadas; não constituem implementação de manutenção nem jornada POST/orders. Setup também confere os perfis persistidos e faz login por `HttpClient`, verificando subject pelo decoder real. Não há mocks ou substituições de beans produtivos. A fixture monta seu suporte MockMvc no construtor, mas seu helper de login MockMvc não é chamado pelos novos testes.

Login e GET estão escritos como **requisições HTTP reais** pela porta aleatória, com JWT/filtros/JPA reais, sem transação externa ao HTTP e sem redirects. Cada GET usa `observeRead` e `assertNoWrites` antes de conferir status/JSON: snapshots JDBC independentes de produtos, categorias, associações, contas completas, perfis, pedidos e itens, mais instruções Hibernate somente SELECT. Permanecem os limites do probe: snapshots compostos em duas transações, sem fotografia global atômica; inspector não observa JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. readOnly isolado não é prova. Não há promessa de snapshot imutável entre lista/detalhe ou compras concorrentes.

Instrumentação preparada: `T017_CONTEXT`, `T017_HTTP` e `T017_COUNTS`, separando login, GET de lista e GET de detalhe, sem imprimir credenciais, JWT, hashes ou corpos de resposta. **Nesta T017 foram efetivamente executados zero testes JUnit, zero inicializações da aplicação/H2, zero operações de persistência da aplicação, zero requisições HTTP e zero dispatches MockMvc.** O transporte e a persistência descritos acima são o procedimento futuro escrito nos fontes, não resultados obtidos.

### Verificações atuais e limites

Skill `speckit-implement` restrita ao pedido: T017, dependência T016, procedimentos F2 e artefatos pertinentes lidos. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007; checklist de requisitos16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente, sem hooks anteriores/posteriores. Produção, suporte e testes anteriores preservados. A única mudança em tasks é o marcador T017.

**Não foram executados Maven, compilador, checkpoint RED, baseline, checkpoints da US1 ou suíte completa.** Os fontes usam JSON/Java/JUnit/Spring e tipos já existentes (`TokenResponseDTO`, manifestos e fixtures), sem depender dos DTOs/repositório/serviço de detalhe previstos para T019–T021. A revisão das assinaturas/imports e da estrutura dos cenários é estática; compilação e preparação do contexto ainda precisam ser confirmadas na T018. Não há novo console/exit code Maven, relatório Surefire, RED funcional, GREEN ou comprovação HTTP da US2. Erro de compilação/setup não será RED funcional.

Comando aprovado da T018 abaixo é **somente referência futura, não executado nesta tarefa**:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDetail*' test
```

Antes dessa execução, seguir o protocolo de preservação dos relatórios e conferir console/XML/TXT das duas classes concretas, invocações e ausência de omissões/duplicatas. Não somar suporte abstrato ou tipos auxiliares, nem tratar404 da rota ainda ausente como comprovação do404 de pedido alheio/inexistente. Esse comportamento e a consolidação de segurança permanecem no escopo futuro próprio.

### Preservação e fechamento da T017

Diretório próprio ignorado `target/feature007-validation/t017-detail-tests-20261020/`: contexto inicial, manifestos SHA-256 dos294 rastreados,1.680 arquivos de evidência anterior e12 relatórios Surefire recebidos; cópias dos documentos recebidos e fontes novos revisados. Nenhum relatório anterior foi movido, regravado ou contado como novo. O prefixo integral do quickstart recebido permanece byte a byte igual, incluindo F1-RED T010, duas execuções T013 e justificativa SQL, duas execuções/correções T015 e F1-GREEN T016. O resultado histórico de576 aprovados da T016 continua restrito à revisão/seleção daquela execução e não aprova os novos testes de detalhe.

Revisão estática executada por `& 'target/feature007-validation/t017-detail-tests-20261020/review.ps1'`: exit0; preservação dos demais292 rastreados e das evidências/relatórios recebidos, prefixo documental, único marcador, arquivos novos, imports locais, provider, links locais e escopo conferidos em `final-review.json`. A primeira chamada da conferência terminou com exit1 por erro do próprio helper PowerShell ao enumerar o array JSON do manifesto de evidências (ChildPath recebeu Object[]); corrigida somente a enumeração no script ignorado e repetida a conferência estática, sem mudar testes/produção. Isso não é falha nem RED da aplicação. `git diff --check` e `git diff --cached --exit-code`: exit0; whitespace dos dois Java novos conferido individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit0/1 conforme semântica do diff novo). Diff documental e ambos os fontes novos foram lidos integralmente. Essas conferências não executam testes nem comprovam compilação ou entrega do detalhe.

**Somente T017 marcada nesta tarefa: T001–T017 concluídas; T018–T034 abertas,17 pendências.** Critério de escrita dos sete cenários atendido; compilação/RED e implementação/validação do detalhe continuam posteriores. Listagem, proteção, paginação, contratos001–006 e asserções anteriores preservados, sem declaração de conclusão da US2 ou da feature. Git final: quickstart/tasks modificados e os dois Java de detalhe não rastreados, nada staged; branch/HEAD/main/seleção007 preservados. Sem stage, commit, push, CI ou execução T018+. Feature005 segue EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T018 — F2-RED do detalhe — 2026-10-20

**Escopo executado: exclusivamente T018.** Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, seleção local Feature007; somente quickstart/tasks modificados, os dois Java de detalhe da T017 não rastreados e nada staged. Main permanece em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Dependência T017 atendida pela escrita dos cenários; nenhuma outra tarefa foi executada.

Skill `speckit-implement` limitada ao pedido; T018, dependência, fontes e procedimentos F2 revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, diretório007; checklist de requisitos16/16, PASS documental. `.specify/extensions.yml` ausente, sem hooks anteriores/posteriores; ignores Java existentes suficientes. Produção, suporte e todas as asserções recebidas permaneceram intactos. Não houve impedimento de preparação nem correção de testes/suporte.

### Comando e resultado efetivamente executados

Na raiz, Java `21.0.12.1` Azul Zulu e Maven Wrapper existente, distribuição3.9.16:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDetail*' test
```

Executado uma única vez por `& 'target/feature007-validation/t018-f2-red-20261020/run.ps1'`. Início `2026-10-20T15:21:42.5932180-03:00`, fim `2026-10-20T15:23:08.2016454-03:00`; duração externa85,608s, Maven01:20. **BUILD FAILURE, exit code1;18 testes,18 falhas,0 erros,0 ignorados,0 testes JUnit aprovados.** Esse resultado atende ao checkpoint RED funcional pela consulta de detalhe ainda ausente; não é aprovação da aplicação. Não houve segunda execução Maven, T019+, baseline, checkpoint anterior ou suíte completa.

Compilação dos111 fontes de teste com javac release21 concluída e Surefire executado. Produção já estava compilada, sem necessidade de recompilar. Nenhum erro de compilação, inicialização Spring/H2, fixture, login, transporte ou limpeza. Warnings existentes de compilação/depreciação não foram tratados como falhas funcionais nem motivaram alterações fora do escopo.

Antes do Maven, os12 relatórios recebidos foram movidos de `target/surefire-reports/` para destino inédito `target/feature007-validation/t018-f2-red-20261020/received-surefire-reports/`. Origem/destino absolutos foram conferidos dentro de target, sem sobreposição/sobrescrita; caminhos, tamanhos e SHA-256 idênticos após mover (`archive-preservation.json`: Count12, HashesMatch=true). Os quatro relatórios novos, dois XML e dois TXT, permanecem em `target/surefire-reports/` e na cópia própria `reports/`. Nenhum resultado anterior entrou no total atual.

### Seleção efetiva e causas reconciliadas

| Classe concreta | Invocações executadas | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| `OwnOrderQueryDetailApiHttpIntegrationTest` |9|9|0|0|
| `OwnOrderQueryDetailHttpIntegrationTest` |9|9|0|0|

Console, XMLs, TXTs e cada nó `testcase` conferidos: sete métodos de cenário por classe concreta,14 combinações classe/método e18 invocações únicas. Os cinco métodos simples executaram uma vez cada; US2.5 e US2.6 executaram índices1 e2 em ambos os contextos. Confirmadas as duas classes concretas e nenhuma classe inesperada; não há `@Nested` nestes fontes. Suporte abstrato, configurações e tipos auxiliares não são casos adicionais. **Zero omissões, duplicatas, contêineres agregadores ou classes aninhadas a somar.** Os18 previstos na T017 foram confirmados pelos relatórios, não presumidos a partir do seletor.

Todas as18 falhas são `org.opentest4j.AssertionFailedError`, mensagem `[own order detail scenario HTTP status] expected: 200 but was: 404`, em `get` chamado por `assertDetail`. Cada testcase registra primeiro um GET de lista200 e depois o primeiro GET de detalhe404, confrontados individualmente com o console. A inspeção do [controller de consulta](../../src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java) confirma somente GET `/orders`, sem mapeamento de detalhe; produção não foi alterada. O404 observado é da rota funcional ainda ausente para uma compra própria preparada, **não comprovação do404 indistinto de pedido alheio/inexistente**, da titularidade do detalhe ou do envelope de erro futuro. Nenhuma falha ocorreu por cálculo BigDecimal já existente, nem foi fabricada alteração defeituosa para obter RED.

### Controles aprovados e limites por cenário

**Não há teste JUnit integralmente aprovado nesta seleção.** Existem controles anteriores ao ponto de falha que efetivamente passaram:18 logins reais com status200/token/subject conferidos,18 respostas de lista200 com JSON/metadados/cabeçalhos/totais esperados, e snapshots/observação sem escrita nos36 GET. São passos dentro de testes falhos, não18 testes de lista adicionais nem reexecução do checkpoint US1. A lista foi chamada somente pelos próprios cenários US2, como previsto em F2.

| Cenário | Invocações por contexto | Alcançado antes da falha | Não comprovado, pois interrompido |
| --- | ---: | --- | --- |
| US2.1 |1| Compra B/A preparada; posições do manifesto0/1; lista com resumo exato e total42.50. | Cabeçalho/itens do detalhe, ordem B/A na resposta, nomes, unitPrice/subtotais e concordância lista/detalhe. |
| US2.2 |1| Página0/size1 com compra mais recente, total3/páginas3; ID diferente da compra própria mais antiga diretamente solicitada. | Sucesso desse detalhe, consulta posterior da página2 e comparação de cabeçalho, página1 e repetição do detalhe. |
| US2.3 |1| Compra qty2×10.00 preparada; lista anterior à alteração com total20.00. | Primeiro detalhe; renome para Nome atual e preço12.00 nem chegaram a ser executados, tampouco as leituras posteriores. |
| US2.4 |1| Produtos distintos preparados em B/A; lista inicial com total42.50. | Primeiro detalhe; renomes para nomes iguais não foram executados, nem asserções posteriores de identificação/ordem/nome igual. |
| US2.5 |2| Nome legado curto/longo e preço atual0.00 preparados por JDBC após a compra; nome/preço e ausência de categorias conferidos no setup; lista legível com total histórico20.00 em ambas as variantes. | Nome integral e finanças na resposta do detalhe, comparação com a lista e conferência explícita do produto posterior ao detalhe. |
| US2.6 |2| Lista conserva texto exato do total `0.01` ou `9007199254740993.01`, com formato decimal vigente. | Representação de unitPrice/subtotal/total no detalhe e concordância entre as duas respostas. |
| US2.7 |1| Duas compras distintas com mesmos produtos e históricos diferentes; lista exata com totais52.00 e42.50, na ordem esperada. | Primeiro detalhe da compra mais recente; detalhe da outra, suas quantidades/preços/subtotais e repetição/comparações posteriores. |

Os métodos completos e índices reais estão em `method-invocations.json` e `case-audit.json`; `http-case-results.json` associa cada classe/caso a seus dois GET e resultados. Nenhuma asserção de JSON de detalhe foi alcançada: o status404 interrompe antes da desserialização/conferência dos campos. Também não se conclui ausência de fusão, ordenação ou histórico financeiro do detalhe com base no sucesso parcial da lista.

### Transporte, persistência e ausência de mutação

**54 requisições HTTP reais:18 POST `/login`,18 GET de lista200 e18 GET de detalhe404.** Em cada contexto: nove logins, nove listas e nove detalhes;18 GET e27 requisições totais. Esses números foram reconciliados entre contadores de execução, console e saída por testcase dos XMLs; são métricas de transporte, distintas das18 invocações JUnit. Zero dispatches MockMvc. A fixture constrói seu suporte MockMvc, mas o login desse helper não foi utilizado. Nenhum mock de serviço/repositório/decoder/segurança foi instalado.

Aplicação completa em porta aleatória, JWT/decoder/filtros/JPA reais, OSIV false e perfil test. Contextos H2 exclusivos/estáveis pelo inicializador aprovado: API porta61716, `jdbc:h2:mem:admin-product-test-3fd04f12-5f30-4a73-babe-83db3a4cc44d`; raiz porta61719, `jdbc:h2:mem:admin-product-test-6fb66044-8437-4e7c-a9a9-d01c8047e7d1`. Setup inicial confirmou banco sem seed, create-drop, import/SQL desabilitados e ausência de transação externa ao HTTP. Cada invocação usou seu CLIENT e dados próprios commitados; limpeza restrita às linhas da fixture e banco vazio verificado depois, sem erros. Não houve POST/orders de jornada, pagamento, transição ou escrita de produção nova.

Todos os36 GET passaram por `assertNoWrites` antes da asserção de status: snapshots JDBC independentes de produtos/categorias/associações/contas completas/perfis/pedidos/itens iguais e somente SELECT Hibernate observado em cada janela, excluindo setup/login/limpeza. Isso inclui os18 detalhes404 de rota ausente; não demonstra execução bem-sucedida de uma consulta de detalhe no repositório/serviço futuro. Alterações JDBC de legado são preparação fora da janela, não mutações causadas por GET. Mantidos os limites do probe: snapshot composto em duas transações, sem fotografia global atômica concorrente; StatementInspector não vê JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Não se conclui US4 inteira, snapshot imutável entre requisições, segurança completa ou regressão001–006 por esta seleção.

### Auditoria, preservação e fechamento

Diretório próprio `target/feature007-validation/t018-f2-red-20261020/`: comando/HEAD/status/diff/ambiente/horários, documentos recebidos, cópias e hashes dos oito fontes de teste/suporte/produção considerados, manifestos dos294 rastreados e1.694 arquivos de evidência anterior, console, exit code, relatórios recebidos/novos e auditorias. `& 'target/feature007-validation/t018-f2-red-20261020/audit.ps1'`: exit0; ExpectedFunctionalRedConfirmed=true, ApplicationTestsApproved=false,18 falhas funcionais, seleção/transportes conciliados e fontes intactos. Nenhuma correção nem repetição Maven necessária.

Revisados diff, dois Java recebidos não rastreados, referências/links locais e whitespace. Alterados na T018 somente este acréscimo e o marcador T018 em tasks; produção, suporte, testes/asserções e arquivos aprovados permanecem íntegros. Prefixo integral do quickstart recebido preservado byte a byte, incluindo T001–T017, F1-RED T010, duas execuções T013/justificativa SQL, duas execuções/correções T015 e F1-GREEN T016. A T017 continua registrada como escrita sem Maven naquele momento; a compilação/execução atuais são exclusivamente evidência da T018. Relatórios e1.694 arquivos prévios preservados sem reclassificar resultado histórico como novo.

`git diff --check` e `git diff --cached --exit-code`: exit0. Whitespace dos dois Java novos conferido individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 pela diferença de arquivo novo). Conferência final em `final-review.json`: demais292 rastreados, oito fontes capturados e dois Java recebidos preservados; links, prefixo documental e somente T018 alterada em tasks verificados. Não há staged.

**Somente T018 marcada nesta execução: T001–T018 concluídas; T019–T034 abertas,16 pendências.** Checkpoint RED atendido, mantendo as asserções200 e o detalhe sem implementação. Git final: quickstart/tasks modificados e os mesmos dois Java de detalhe não rastreados; nada staged, branch/HEAD/main/seleção007 preservados. Sem impedimento de preparação, stage, commit, push, CI, T019+ ou conclusão da US2/feature. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T019 — DTOs do detalhe próprio e contrato JSON — 2026-10-20

**Escopo executado: exclusivamente T019, dependente de T018.** Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, seleção local Feature007; somente quickstart/tasks modificados, os dois Java HTTP de detalhe recebidos não rastreados e nada staged. Seus caminhos completos e SHA-256 estão em `received-java.json`; ambos permaneceram intactos. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Criados [OwnOrderItemDTO](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderItemDTO.java) e [OwnOrderDetailDTO](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java); estendido [OwnOrderQueryDtoContractTest](../../src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java). São records de valores escalares: item contém exatamente productId/productName/quantity/unitPrice/subtotal; detalhe contém id/createdAt/status/total/items. Não contêm entidade, comprador/accountId, itemId/posição interna, categorias, preço atual, descrição, imagem ou snapshot de produto.

`productName` recebe texto atual já fornecido pelo futuro serviço, sem trim, normalização Unicode ou constraints administrativas. `quantity`, `unitPrice`, `subtotal` e `total` recebem os valores históricos; o DTO não consulta catálogo, recalcula, normaliza ou arredonda. Os três campos monetários reutilizam diretamente `OrderItemResponseDTO.requireMoney`, sem copiar/alterar o verificador vigente. O detalhe copia items com `List.copyOf`, preservando sequência e elementos imutáveis sem ordenar, fundir ou truncar; a coleção exposta não pode ser modificada. Instant segue a anotação UTC/milissegundos existente; enum somente `WAITING_PAYMENT`. Entidades, enum, DTOs anteriores, listagem, segurança, repositório, serviço, controller e advice permanecem intactos; nenhum componente T020+ implementado.

### Validação focal efetivamente executada

Skill `speckit-implement` restrita ao pedido; T019, dependência T018 e artefatos pertinentes revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, diretório007; checklist16/16, PASS documental. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignores Java existentes suficientes. Java21.0.12.1 Azul Zulu e Wrapper Maven3.9.16. Testes novos escritos para os DTOs e depois validados; sem fabricar RED do verificador monetário existente ou repetir F2-RED.

Única execução Maven na raiz, selecionando somente os métodos novos `detail*` da classe contratual:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDtoContractTest#detail*' test
```

Executado por `& 'target/feature007-validation/t019-detail-dtos-20261020/run.ps1'`. Início `2026-10-20T15:32:26.9422468-03:00`, fim `2026-10-20T15:33:28.5012673-03:00`; duração externa61,559s, Maven56,599s. **BUILD SUCCESS, exit code0:82 testes,0 falhas,0 erros,0 ignorados.** Nenhuma correção posterior ou segunda execução necessária. Maven compilou86 fontes de produção e111 de teste com release21; isso não significa execução das demais suítes. Não executados os37 casos `summary*`, testes HTTP de detalhe/lista, checkpoints anteriores, baseline ou suíte completa.

Antes do Maven, quatro relatórios recebidos da T018 movidos de `target/surefire-reports/` para destino inédito `target/feature007-validation/t019-detail-dtos-20261020/received-surefire-reports/`; caminhos absolutos verificados dentro de target, sem sobreposição/sobrescrita. Caminhos/tamanhos/SHA-256 conferidos depois da movimentação (`archive-preservation.json`: Count4, HashesMatch=true). Os dois relatórios novos, um XML e um TXT, estão em `target/surefire-reports/` e na cópia própria `reports/`. Nenhum resultado antigo foi somado ao atual.

### Seleção e provas do slice JSON

Console/XML/TXT conciliados: somente `com.devsuperior.dscommerce.dto.OwnOrderQueryDtoContractTest`,12 métodos `detail*`,82 identidades únicas, todos os índices parametrizados1..N, sem omissões/duplicatas/falhas/erros/ignorados. Não há classes `@Nested`, contêineres agregadores ou métodos `summary*` nesta seleção. Providers estáticos e helpers não são casos adicionais. Nomes e índices em `case-audit.json`; contagens por método em `audit.json`.

| Método | Invocações | Prova focal efetiva |
| --- | ---: | --- |
| `detailSerializesExactlyFiveFieldsAndRecordedBASequence` |1| Cabeçalho cinco campos e dois itens com cinco campos cada; B/A fornecidos com IDs8/3, nomes exatos, quantidades2/3, preços10.00/7.50, subtotais20.00/22.50 e total42.50. |
| `detailItemSerializesOnlyFiveFieldsWithIntegralIdentifiersAndQuantity` |1| Item isolado com productId Long.MAX_VALUE e quantity Integer.MAX_VALUE, ambos números integrais; valores monetários textuais fornecidos. |
| `detailFormatsUtcMillisecondsWithoutChangingSuppliedInstant` |4| Milissegundos explícitos/zero, offset +03:00 e nanos; JSON UTC com três casas e Instant original preservado no record. |
| `detailPreservesExactMoneyInEveryFinancialField` |9| unitPrice/subtotal/total textuais exatos de0.01 ao máximo99999999999999999.99, inclusive9007199254740993.01; sem perda numérica ou reformatar. |
| `detailRejectsNonContractMoneyInEveryFinancialField` |57|19 valores inválidos em cada campo unitPrice/subtotal/total: nulo/vazio, zero/negativo, escala inválida, expoente, zeros iniciais, vírgulas, overflow decimal19,2, espaços, sinal positivo, NaN/Infinity; rejeição sem arredondamento. |
| `detailPreservesCurrentProductNameWithoutAdministrativeWriteValidation` |4| Nome curto `Á`, longo87 caracteres com espaços, espaços internos/externos e acento decomposto `Cafe\u0301`; texto preservado em JSON e Bean Validation direta sem constraints administrativas impeditivas. |
| `detailKeepsProductsWithEqualNamesDistinctInSuppliedOrder` |1| Dois produtos fornecidos com nome igual permanecem distintos e na sequência8/3, sem fusão/ordenação pelo DTO. |
| `detailDefensivelyCopiesAndProtectsSuppliedItems` |1| Alterar/esvaziar lista original não altera record/JSON; add/set/remove pelo accessor são rejeitados. |
| `detailPreservesAllSuppliedItemsWithoutBorrowingThePageLimit` |1| Todos os51 itens fornecidos conservam campos e sequência; size máximo50 da lista de pedidos não é limite de itens do detalhe. |
| `detailRejectsNullItemCollectionAndNullEntries` |1| Cópia defensiva recusa coleção nula ou contendo elemento nulo. |
| `detailRoundTripPreservesExactValuesAndItemSequence` |1| Serializar/desserializar conserva Long.MAX_VALUE, nomes integrais, ordem B/A, montantes pequenos/grandes e lista imutável. |
| `detailSharesOnlyTheFourSummaryHeaderFields` |1| DTOs de resumo e detalhe fornecidos com os mesmos valores têm os quatro campos comuns iguais; detalhe acrescenta somente items. |

Teste usa `@JsonTest` e ObjectMapper configurado pelo Boot, com Bean Validation direta nos quatro casos de nome. Em cada invocação, o suporte existente confirma ausência de beans de datasource, adapter MVC e cadeia de segurança. **82 testes JUnit; zero mocks definidos, zero contextos de banco/persistência, zero dispatches MockMvc e zero requisições HTTP reais.** Não há fixture JDBC/JPA, servidor/porta, login ou alteração de H2/seed/OSIV/configuração global.

Os dados são fornecidos diretamente aos DTOs: esta seleção prova sua forma, preservação textual e imutabilidade, não obtenção do nome atual no banco, cálculo histórico, titularidade, consulta por posição, ordenação JPA ou serialização depois do serviço transacional. O DTO conserva a ordem fornecida; demonstrar que ela deriva da posição persistida pertence às tarefas futuras de repositório/serviço/HTTP. Da mesma forma, comparar quatro campos de objetos fornecidos não comprova concordância entre requisições reais de lista/detalhe. Formato UTC com nanos prova apresentação, não precisão dos instantes persistidos.

### Evidências anteriores e fronteira das próximas tarefas

**T018 continua RED histórico:18 falhas funcionais200 esperado/404 recebido pela ausência da rota de detalhe; não comprova ownership,404 de pedido alheio/inexistente ou asserções interrompidas.** Renomes posteriores à primeira leitura em US2.3/US2.4, nomes iguais na resposta, campos/itens do detalhe e comparações lista/detalhe não foram alcançados naquela execução. Esta T019 não repetiu HTTP e seu GREEN de DTO não transforma esses resultados em prova integrada. A rota continua sem implementação de detalhe; T020–T022 permanecem necessárias, assim como US3/US4 e fechamento.

Mantidas sem repetição/reclassificação F1-RED T010, as duas execuções T013 e justificativa do OFFSET na página zero, as duas execuções/correções T015, F1-GREEN T016 e a escrita T017. Testes/asserções do resumo também foram preservados: `original-tests-preserved.json` confirma reconstrução integral do fonte recebido, normalizando somente EOL para comparação, ao remover apenas imports novos, ampliação do comentário e o bloco novo do detalhe. Não houve edição de asserção/helper anterior nem reutilização de teste histórico como execução atual.

### Auditoria e fechamento da T019

Diretório próprio ignorado `target/feature007-validation/t019-detail-dtos-20261020/`: estado inicial/manifestos SHA-256, documentos/teste recebidos, comando/ambiente/HEAD/diff, cópias e hashes dos nove fontes capturados, console, horários, exit code, relatórios recebidos/novos e auditorias. `& 'target/feature007-validation/t019-detail-dtos-20261020/audit.ps1'`: exit0, DetailDtoJsonContractApproved=true, OwnOrderDetailEndpointApproved=false e Feature007Complete=false. Confirmados82 casos selecionados, nove fontes idênticos aos executados, dois Java HTTP recebidos intactos e1.740 arquivos de evidência anterior preservados.

Revisados diff, os quatro Java não rastreados (dois recebidos e dois DTOs novos), referências/links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0. Conferência individual dos quatro Java por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 pela diferença de arquivo novo). Prefixo integral do quickstart recebido preservado byte a byte; tasks difere somente pelo marcador T019. Conferência final em `final-review.json`: demais291 rastreados, fontes executados, testes recebidos, relatórios e evidências anteriores íntegros; main/branch/HEAD/seleção007 e índice vazio preservados.

**Somente T019 marcada nesta execução: T001–T019 concluídas; T020–T034 abertas,15 pendências.** Alterados nesta tarefa somente os dois DTOs novos, extensão do teste contratual, este acréscimo e o marcador T019. Git final: quickstart/tasks/teste DTO modificados; quatro Java não rastreados (dois HTTP recebidos e dois DTOs novos), nada staged. Sem impedimento, stage, commit, push, CI, execução T020+ ou declaração de detalhe/US2/feature concluídos. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T020 — Fetch JPA do detalhe por ID e titular — 2026-10-20

**Escopo executado: exclusivamente T020, dependente do RED funcional T018.** Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, seleção local Feature007; somente teste DTO/quickstart/tasks modificados e nada staged. Os quatro Java recebidos foram conferidos por caminho completo/SHA-256 em `received-java.json`: `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java`, `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderItemDTO.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailApiHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java`. Todos permanecem intactos e não rastreados. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Alterado somente [OrderRepository](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java) em produção: novo `Optional<Order> findOwnOrderWithItemsById(long ownerId, long id)`, `@Transactional(readOnly=true)`, JPQL `WHERE o.id = :id AND o.buyer.id = :ownerId`, DISTINCT e LEFT JOIN FETCH de items/product, sem Pageable, categorias ou lookup global. Reutiliza `@OrderBy("position ASC")` da entidade existente. As duas consultas de listagem e métodos herdados permanecem intactos; não há regra de autorização/perfil, cálculo, DTO ou decisão de erro HTTP no repositório.

Estendido somente [OwnOrderQueryRepositoryTest](../../src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java) com métodos `detail*`, helpers e contador do detalhe. `original-list-preserved.json` confirma reconstrução integral dos dois fontes recebidos ao remover apenas adições da T020, import/comentário e contador novos, normalizando EOL para comparação. Testes/asserções/helpers `list*`, ciclo de preparação/limpeza e configuração H2 anteriores foram preservados. DTOs, teste DTO recebido, entidades/schema, fixture/probe, segurança, serviço, controller e advice não foram alterados. Nenhum componente T021+ implementado.

### Preparação e execução focal

Skill `speckit-implement` limitada ao pedido; T020, dependência T018 e artefatos pertinentes de spec/plano/pesquisa/modelo/contrato/evidências revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007; checklist16/16, PASS documental. `.specify/extensions.yml` ausente antes/depois, sem hooks. Ignores Java existentes suficientes. Java21.0.12.1 Azul Zulu e Wrapper Maven3.9.16; nenhuma execução concorrente Java/Maven.

Única execução, na raiz e sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryRepositoryTest#detail*' test
```

Executada por `& 'target/feature007-validation/t020-detail-repository-20261020/run.ps1'`, início `2026-10-20T15:44:53.0555056-03:00`, fim `2026-10-20T15:46:17.3552169-03:00`; duração externa84,300s, Maven01:19. **BUILD SUCCESS, exit code0:17 testes,0 falhas,0 erros,0 ignorados.** Sem correção posterior ou segunda execução necessária. Maven compilou os fontes necessários, mas o Surefire executou somente os métodos novos do seletor focal: nenhum `list*`, DTO/JSON, HTTP, checkpoint anterior, baseline ou suíte completa. O RED exigido para a história permanece a execução histórica T018; não foi repetido nem fabricado RED do componente.

Antes do Maven, os dois relatórios recebidos da T019 foram movidos de `target/surefire-reports/` para destino inédito `target/feature007-validation/t020-detail-repository-20261020/received-surefire-reports/`. Origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita; caminhos/tamanhos/SHA-256 idênticos após mover (`archive-preservation.json`: Count2, HashesMatch=true). Os dois relatórios novos, XML/TXT, permanecem em `target/surefire-reports/` e na cópia própria `reports/`. Nenhum resultado anterior foi somado ao atual.

### Seleção Surefire e provas efetivas

Console/XML/TXT conciliados: somente `com.devsuperior.dscommerce.repositories.OwnOrderQueryRepositoryTest`, dez métodos `detail*`,17 identidades únicas e todos os índices parametrizados1..N conferidos. Sem omissões/duplicatas, classes `@Nested`, contêineres agregadores ou métodos antigos executados. Nomes/índices em `case-audit.json`; contagens e leituras por caso em `detail-sql.json`.

| Método | Invocações | Janelas/SELECTs | Prova focal efetiva |
| --- | ---: | ---: | --- |
| `detailLoadsOnlyRequestedOwnRootWithCompleteRecordedBAItems` |1|1| Uma raiz própria e todos os itens B/A, IDs/posição/quantidade/preço unitário/subtotal/total do manifesto; produtos atuais carregados com preços12.00/99.00 distintos dos históricos. |
| `detailFiltersTheSameOrderByEachSuppliedOwnerWithoutAGlobalProbe` |4|16| Para cada titular de fixture CLIENT/outro/ADMIN/ambos, retorna seu pedido e Optional vazio quando o mesmo ID é solicitado por cada um dos outros três titulares. Cada chamada usa uma única consulta restrita. |
| `detailReturnsEmptyForAbsentOrderOrNonOwningAccount` |3|3| Pedido ausente, titular sem compras e ID de conta ausente produzem Optional vazio no repositório, sem consulta global para diferenciar existência. |
| `detailLeftFetchKeepsARootWithoutItemsForTheLaterServiceToDiagnose` |1|1| Após preparação JDBC que remove somente os itens do pedido próprio, o left fetch conserva raiz/id/titular/instante/estado e coleção carregada vazia. Não corrige nem oculta a raiz. |
| `detailReadsRenamedCurrentProductsWithoutReplacingHistoricalValues` |1|2| Leituras antes/depois de renomear os produtos e alterar seus preços atuais para37.00/0.00 mantêm histórico2×10.00 +3×7.50, total42.50, ordem B/A e nomes atuais integrais. |
| `detailReadsEqualLegacyNamesZeroCurrentPricesAndNoCategoriesWithoutReordering` |2|2| Nomes iguais curtos `Á` ou longos87 caracteres, preços atuais0.00 e associações de categorias removidas no setup: duas linhas distintas B/A, nomes exatos e histórico42.50 legíveis, sem reordenação ou reparo. |
| `detailPreservesSmallAndLargeHistoricalAmountsInTheLoadedAggregate` |2|2| Quantidade/preço/subtotal/total BigDecimal carregados conservam numericamente0.01 e9007199254740993.01; sem confundir esta prova com o formato JSON da T019. |
| `detailKeepsDifferentPurchaseHistoriesForTheSameCurrentProduct` |1|2| Duas compras com o mesmo produto mantêm suas próprias quantidades2/1, preços10.00/15.25 e subtotais20.00/15.25 desse produto, com totais42.50/15.25 dos respectivos pedidos; nenhuma mistura com a outra compra ou preço atual. |
| `detailFetchesAllItemsWithoutBorrowingTheListPageLimit` |1|1| Pedido próprio com51 produtos/itens é carregado completo em um SELECT, sem truncamento/duplicação; ordem e todas as linhas/valores conferidos. |
| `detailOrdersByRecordedPositionEvenWhenItemIdentifiersHaveTheOppositeOrder` |1|1| Preparação JDBC troca posições usando posição temporária livre, preservando a constraint única. Retorno segue posições0/1 mesmo com IDs dos itens em ordem oposta, sem mudar quantidades/preços. |

Total: **17 invocações JUnit,31 janelas de detalhe e31 SELECTs, zero páginas de IDs e zero lotes da listagem**. Cada chamada de detalhe executa um único SELECT, inclusive nas ausências. SQL observado contém ID e buyer_id da mesma raiz no WHERE com AND, dois LEFT JOINs somente para items/product, DISTINCT e ORDER BY position; sem OFFSET/FETCH FIRST/LIMIT, count, IN, exists, categorias ou tabela de contas/perfis. A inspeção por caso concilia os31 eventos dos XMLs com o console e os contadores. Não há orçamento global de consultas HTTP inferido desse número.

Após retornar da transação do repositório, sem transação do chamador, as raízes válidas mantêm itens/produtos acessíveis e completos, sem SELECT tardio; categorias e comprador não são inicializados. O ID do comprador é conferido pela referência existente, sem carregar dados de conta. Coleções e identidades dos itens conferidas contra manifesto, sem duplicação de raízes/itens ou ordenação por nome/produto/ID interno. Os valores esperados vêm da preparação explícita, não de um mapper/consulta de produção; os cálculos BigDecimal já existentes são apenas exercitados no agregado carregado. DTOs e materialização dentro do futuro serviço transacional não foram testados aqui.

### Persistência real, isolamento e ausência de mutação

**17 testes de JPA/H2 real, zero mocks definidos, zero dispatches MockMvc e zero requisições HTTP reais.** Contexto SpringBoot `webEnvironment=MOCK`, OSIV false, perfil test, inicializador/fixture/probe aprovados sem edição; sem servidor/porta ou login. A fixture monta seu suporte MockMvc, mas não o despacha. A propriedade de identidade histórica do contexto continua `feature007.own-order-repository-test-context=list`; isso não significa execução dos testes da lista. Os marcadores reutilizados `T013_SQL/READ` nesta execução têm exclusivamente `kind=detail`; `T020_COUNTS` identifica31 consultas novas, e `T013_COUNTS` registra17 casos/31 leituras/idPages0/batchFetches0.

Banco exclusivo/estável desta execução: `jdbc:h2:mem:admin-product-test-4690b87a-826d-4b71-9128-b9b0ef533ef6`. UUID criado uma vez por ApplicationContext; create-drop e SQL/import desabilitados somente no contexto isolado. Verificados banco sem seed, estabilidade do URL/metadata antes/depois, preparação JDBC commitada dos nove pedidos/dez itens de controle por caso e limpeza das próprias linhas, com banco vazio ao final de cada invocação. O caso51 itens acrescenta seu pedido/produtos à mesma fixture; nenhum cache/ordem/seed/schema global foi alterado.

Renomes, preços/categorias, remoção de itens e troca de posições são preparação explícita REQUIRES_NEW fora das janelas de leitura, não comandos da funcionalidade. Mantidas FKs/constraints existentes; não foi simulado produto órfão desabilitando integridade. A raiz sem itens é a incoerência preparada, enquanto o LEFT JOIN de product também está confirmado no SQL. Diagnóstico seguro dessa raiz pelo serviço/HTTP permanece futuro.

Nas31 janelas, `assertNoWrites` passou: snapshots JDBC independentes de produtos/categorias/associações/contas completas/perfis/pedidos/itens iguais e somente SELECT Hibernate observado. Eventos SQL estão em transação JPA; métodos declaram readOnly e o chamador permanece sem transação/sincronização antes/depois. Não se usa readOnly como prova isolada. Limites do probe preservados: snapshot composto por duas transações, sem fotografia global atômica concorrente; inspector não vê JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Não se promete snapshot imutável entre requisições ou conclusão da US4 por este focal.

### Evidências, limites e fechamento

**T018 continua RED histórico por ausência do endpoint de detalhe, sem comprovar ownership HTTP ou asserções interrompidas. T019 continua GREEN isolado de DTO/JSON,82 casos, sem persistência/HTTP. T020 acrescenta prova do filtro por titular e fetch no repositório, sem autenticar contas/perfis nem produzir status/envelopes HTTP.** Os rótulos CLIENT/ADMIN/ambos nos dados identificam contas de fixture; autorização vigente, guarda do serviço,404 indistinto e serialização final exigem as tarefas futuras próprias. Optional vazio para conta ausente no repositório não decide a resposta401 do serviço/endpoint. Nenhuma leitura HTTP foi repetida ou tratada como aprovada nesta T020; o endpoint permanece sem implementação de detalhe.

Diretório próprio ignorado `target/feature007-validation/t020-detail-repository-20261020/`: estado inicial/manifestos, documentos e dois fontes recebidos, comando/HEAD/diff/ambiente/horários, cópias e hashes dos15 fontes capturados, console/exit code, relatórios recebidos/novos e auditorias. `& 'target/feature007-validation/t020-detail-repository-20261020/audit.ps1'`: exit0, DetailRepositoryApproved=true, OwnOrderDetailEndpointApproved=false e Feature007Complete=false;17 casos/31 consultas reconciliados,15 fontes capturados intactos após a execução, quatro Java recebidos e1.780 arquivos de evidência anterior preservados. Não houve correção, nova execução ou falha a reclassificar.

Revisados diff, fontes alterados, quatro Java recebidos não rastreados, referências/links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0; os quatro não rastreados conferidos individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 por arquivo novo). Prefixo integral do quickstart recebido preservado byte a byte, incluindo T018/T019, F1-RED T010, duas execuções T013 e justificativa SQL, duas execuções/correções T015 e F1-GREEN T016. Tasks recebida difere somente pelo marcador T020. `final-review.json` confere os demais290 rastreados intactos, inclusive o teste DTO já modificado recebido, fontes capturados, quatro Java, relatórios e evidências anteriores, referências Git/seleção007 e índice vazio.

**Somente T020 marcada nesta execução: T001–T020 concluídas; T021–T034 abertas,14 pendências.** Alterados nesta tarefa somente OrderRepository, extensão do teste JPA, este acréscimo e o marcador T020. Git final: cinco rastreados modificados (incluindo teste DTO recebido intacto) e os mesmos quatro Java não rastreados, nada staged; branch/HEAD/main/seleção007 preservados. Sem impedimento, stage, commit, push, CI, execução T021+ ou declaração de detalhe/US2/feature concluídos. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T021 — Serviço e fronteira MVC do detalhe próprio — 2026-10-20

**Escopo executado: exclusivamente T021, dependente de T019–T020.** Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, seleção local Feature007; somente OrderRepository, teste JPA, teste DTO, quickstart e tasks modificados, nada staged. Os quatro Java recebidos foram conferidos por caminho/SHA-256 em `received-java.json`: `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java`, `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderItemDTO.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailApiHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java`. Permanecem intactos e não rastreados. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Alterados [OwnOrderQueryService](../../src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java), [OwnOrderQueryController](../../src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java) e [OwnOrderQueryExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java). `GET /orders/{id}` exige Long positivo na fronteira MVC; sintaxe/overflow/valor não positivo resultam400. O serviço chama primeiro a guarda atual e usa seu owner na consulta restrita da T020, sem lookup global/exists ou owner recebido do cliente. Ausente/alheio geram a mesma exceção de negócio e envelope404 fixo. Nova [OwnOrderNotFoundException](../../src/main/java/com/devsuperior/dscommerce/services/exceptions/OwnOrderNotFoundException.java) no pacote existente de exceções distingue essa ausência das falhas internas, sem acoplar o serviço ao status HTTP nem reutilizar exceção de produto.

O cálculo/validação de linhas e total anteriormente usado pelo resumo foi extraído, preservado e compartilhado com o detalhe: quantity/unitPrice registrados, subtotal/total exatos decimal19,2, duas casas sem arredondamento e saída textual. Mapeamento completo antes do retorno do método readOnly; itens seguem a ordem position ASC da consulta/entidade existente, sem fusão por nome. Nome atual é copiado integralmente, sem trim ou constraints administrativas; preço/categorias atuais não são usados para reprecificar/validar a compra. Estado é somente o armazenado. Raiz inconsistente, falha de leitura/cálculo/mapeamento ou conclusão transacional não é convertida em ausência: advice mantém500 fixo seguro, além dos mapeamentos401/403 existentes. HEAD conserva status e corpo vazio. Entidades, enum, POST, contrato004/Location, paginação, proteção, consultas e DTOs recebidos não foram alterados.

Estendidos [OwnOrderQueryServiceTest](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java) com métodos `detail*` exigidos e [OwnOrderQueryControllerTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryControllerTest.java) com focal `detail*` para conferir os critérios da própria T021 de ID/status/envelope/HEAD na fronteira. Reutilizados os suportes aprovados, sem editar fixture/probe/configuração H2. `original-us1-preserved.json` confirma reconstrução integral dos fontes recebidos de serviço e dos dois testes, removendo somente adições/imports/contadores e revertendo a extração do cálculo, normalizando EOL para comparar. Orquestração da lista e asserções/helpers/ciclo de vida anteriores foram preservados.

### Execução focal e conciliação dos resultados

Skill `speckit-implement` limitada ao pedido; lidos T021, dependências e artefatos pertinentes de constituição/spec/plano/pesquisa/modelo/contrato/evidências. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007; checklist16/16, PASS documental. `.specify/extensions.yml` ausente, sem hooks; ignores Java existentes suficientes. Java21.0.12.1 Azul Zulu, Wrapper Maven3.9.16; sem execução Java/Maven concorrente.

Única execução pelo Wrapper, na raiz, sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryServiceTest#detail*,OwnOrderQueryControllerTest#detail*' test
```

Executada por `& 'target/feature007-validation/t021-detail-service-20261020/run.ps1'`, início `2026-10-20T16:01:11.1563522-03:00`, fim `2026-10-20T16:02:33.5432181-03:00`; duração externa82,387s, Maven01:17. **BUILD SUCCESS, exit code0:62 testes,0 falhas,0 erros,0 ignorados.** Sem falha/correção/repetição. Compilação dos fontes necessários não significa seleção de suas outras classes de teste. Não executados F2-GREEN/T022, HTTP da US2, métodos JUnit anteriores, checkpoints US1, baseline ou suíte completa; nenhum RED artificial.

Console/XML/TXT conciliados: somente `OwnOrderQueryServiceTest` (44) e `OwnOrderQueryControllerTest` (18),19 métodos `detail*`,62 identidades únicas, todos os índices parametrizados1..N, sem omissões/duplicatas. Não há `@Nested` nos dois arquivos nem classes aninhadas esperadas/selecionadas. Nomes/índices/resultados em `case-audit.json`, contagem de cada método em `method-invocations.json` e associação por invocação das23 janelas JPA em `read-windows.json`.

| Seleção efetiva | Invocações JUnit | Prova e limites |
| --- | ---: | --- |
| Serviço real: mapeamento dos nove pedidos dos titulares |1| Nove leituras completas comparadas com manifesto explícito: cabeçalho, estado, IDs, ordem/nomes, quantidades, preços/subtotais/totais; serialização após retorno sem SQL adicional. |
| Serviço real: catálogo alterado |3| Duas leituras por caso, antes/depois de renomear; nomes curtos, longos, espaços/acento e nomes iguais mantêm conteúdo/ordem/IDs distintos. Preços atuais B37.00/A0 e remoção das associações de categorias não alteram histórico; DTO anterior permanece com o nome que recebeu. |
| Serviço real: ausente/alheio, guarda e raiz incoerente |4| Dois casos CLIENT/ADMIN fazem duas rejeições de ausência cada; um caso verifica revogação de perfis, conta ausente e Authentication nula antes do fetch; um caso com raiz sem itens falha internamente sem reparo. São exceções de serviço, sem login/JWT/status HTTP real. |
| Serviço com mocks locais |36| Transação readOnly durante guarda/fetch/cálculo/nome via proxy (1), ausência com consulta restrita (1), corrupções (15), valores históricos válidos (5), inválidos (7), overflow total (1), falhas propagadas por etapa (4), guarda sem repositório (2). Um caso usa proxy transacional com H2, mas colaboradores simulados; não é leitura JPA real de pedido. |
| MVC com serviços simulados |18| Delegação/JSON exato (2), seis IDs inválidos (6), envelope404 (2), HEAD200/404 (2),500 seguro (4),401/403 (2). São22 dispatches MockMvc, sem filtros/login/banco/servidor. Prefixo `/api` é contextPath simulado no mesmo contexto MVC, sem segundo servidor. |

Nos cinco casos monetários simulados, detalhe e lista são chamados sobre o mesmo agregado fornecido e têm total igual, inclusive0.01,9007199254740993.01 e máximo decimal19,2; formas BigDecimal exatas `1E+1`/`7.500` são apresentadas como10.00/7.50 sem arredondar. Essa comparação reutiliza helper mockList, não executa os métodos JUnit antigos ou janelas JPA da lista, nem comprova concordância entre duas requisições HTTP. Mocks rejeitam acesso ao preço/categorias atuais e verificam guarda antes do fetch. As falhas500 MVC são exceções lançadas pelo serviço simulado, inclusive TransactionSystemException/HttpMessageNotWritableException: não são falhas reais injetadas em commit/serialização HTTP; T024 permanece futura.

### JPA real e limites de observação

**44 testes de serviço:8 invocações com guarda/repositório/JPA reais e36 com colaboradores simulados locais. Mais18 testes MVC,22 dispatches MockMvc, zero requisições HTTP reais.** Mocks não substituem beans do contexto real. Banco exclusivo `jdbc:h2:mem:admin-product-test-3597550b-fbad-4fb1-b7c7-17f11059ddc9`, um ApplicationContext, OSIV false, sem seed, fixture JDBC commitada/limpeza própria e sem transação do chamador. Alterações explícitas de catálogo/perfis/itens são preparação fora das janelas observadas, não ações do endpoint.

Foram15 janelas de sucesso, cada uma com dois SELECTs (guarda e um fetch), e8 de rejeição: quatro ausências com dois SELECTs cada, raiz sem itens com dois, conta ausente com um, perfis revogados com um e Authentication nula com zero. Total observado: **42 SELECTs e20 fetches de pedido em23 janelas**. Ordem da guarda antes do fetch, transação ativa nos eventos e ausência de SQL de escrita foram conferidas. Sucessos serializados após retorno mantiveram os eventos SQL iguais; snapshots independentes antes/depois permaneceram iguais também nas rejeições. Marcadores T014/T015 reutilizados identificam somente a infraestrutura: `T014_COUNTS realServiceReadWindows=0` confirma ausência de leituras antigas da lista; `T015_COUNTS mockMvcDispatches=22` é desta seleção nova, sem repetir T015. `T021_COUNTS` registra15 sucessos/8 rejeições e nenhum HTTP.

Mantidos os limites da T020: snapshot composto por duas transações não é fotografia global atômica concorrente; inspector Hibernate não vê JDBC direto; igualdade antes/depois não detecta escrita transitória restaurada. Preparação/limpeza não entram nas janelas; serialização após serviço foi observada separadamente. Contagens são destas janelas de serviço, sem orçamento global de HTTP ou garantia de snapshot imutável entre requisições. ReadOnly sozinho não foi usado como prova de ausência de mutação. Esta seleção não encerra US4, segurança transversal/precedência de filtros ou falhas reais de infraestrutura.

### Preservação e fechamento da T021

**T018 permanece RED histórico por ausência da rota naquela revisão, sem comprovar ownership HTTP ou asserções interrompidas. T019 permanece prova isolada DTO/JSON de82 casos; T020 permanece prova JPA de17 casos/31 janelas.** T021 implementa serviço/fronteira e acrescenta as provas focais acima, sem transformar essas evidências em F2-GREEN ou integração HTTP completa. T022 continua necessária, assim como T023+; detalhe/US2/Feature007 não são declarados concluídos.

Diretório próprio ignorado `target/feature007-validation/t021-detail-service-20261020/`: estado inicial/manifestos SHA-256, documentos e cinco fontes recebidos, comando/HEAD/ambiente/diff/horários, cópias e hashes de18 fontes capturados, console/exit code e auditorias. Antes do Maven, os dois relatórios recebidos da T020 foram movidos para `received-surefire-reports/`, com origem/destino absolutos conferidos dentro de target, sem sobreposição/sobrescrita; hashes/tamanhos preservados (`archive-preservation.json`). Os quatro novos XML/TXT estão em `reports/` e `target/surefire-reports/`; resultados anteriores não foram somados.

`& 'target/feature007-validation/t021-detail-service-20261020/audit.ps1'`: exit0, ComponentDetailApproved=true, HttpIntegrationApproved=false, T022Executed=false e Feature007Complete=false;62 invocações/23 janelas conciliadas. `final-review.ps1`/`final-review.json` conferem os18 fontes capturados, quatro Java recebidos,1.826 arquivos históricos e relatórios recebidos/novos intactos; demais287 rastreados preservados em relação ao início, incluindo repositório/testes JPA/DTO já modificados recebidos. Prefixo integral do quickstart recebido preservado byte a byte, incluindo F1-RED T010, duas execuções T013/justificativa SQL, duas execuções/correções T015, F1-GREEN T016 e T017–T020. Tasks difere somente pelo marcador T021.

Revisados diff, fontes alterados, conteúdo dos cinco Java não rastreados, referências/links locais e whitespace. `git diff --check` e `git diff --cached --exit-code`: exit0. Conferência individual dos cinco não rastreados por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 por arquivo novo). Branch/HEAD/main/seleção007 e índice vazio preservados.

**Somente T021 marcada nesta execução: T001–T021 concluídas; T022–T034 abertas,13 pendências.** Alterados nesta tarefa serviço/controller/advice, seus dois testes focais, nova exceção de negócio, este acréscimo e o marcador T021: oito arquivos. Git final: dez rastreados modificados e cinco Java não rastreados (quatro recebidos e a exceção nova), nada staged. Sem impedimento, stage, commit, push, CI ou execução T022+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

## T022 — F2-GREEN conjunto da US2 — 2026-10-20

**Escopo executado: exclusivamente T022, dependente de T021.** Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `189ffdcfed5e9434dafafa029a9b3199d50994bf`, seleção local Feature007, nada staged. Dez rastreados modificados recebidos: `specs/007-consultar-meus-pedidos/quickstart.md`, `specs/007-consultar-meus-pedidos/tasks.md`, `src/main/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryController.java`, `src/main/java/com/devsuperior/dscommerce/controllers/handlers/OwnOrderQueryExceptionHandler.java`, `src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java`, `src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryControllerTest.java`, `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java` e `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`.

Cinco Java não rastreados recebidos e conferidos por caminho/SHA-256: `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java`, `src/main/java/com/devsuperior/dscommerce/dto/OwnOrderItemDTO.java`, `src/main/java/com/devsuperior/dscommerce/services/exceptions/OwnOrderNotFoundException.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailApiHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java`. Nenhum fonte de produção/teste/suporte/configuração foi alterado nesta tarefa; não houve correção ou enfraquecimento de asserção. Main permanece `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

### Preparação, comando e resultado

Skill `speckit-implement` limitada ao pedido; T022, dependência T021 e procedimentos F2, constituição/spec/plano/pesquisa/modelo/contrato e evidências T017–T021 revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007; checklist16/16, PASS documental. `.specify/extensions.yml` ausente antes/depois, sem hooks. Ignores Java existentes suficientes; Java21.0.12.1 Azul Zulu e distribuição Maven3.9.16 pelo Wrapper. Sem execução Java/Maven concorrente.

Única execução, exatamente a seleção F2-GREEN de [tasks.md](tasks.md), na raiz e sem clean:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryDetail*,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest#detail*,OwnOrderQueryServiceTest#detail*' test
```

Executada por `& 'target/feature007-validation/t022-f2-green-20261020/run.ps1'`: início `2026-10-20T16:11:19.9107149-03:00`, fim `2026-10-20T16:12:36.8923261-03:00`, duração externa76,982s, Maven01:11. **BUILD SUCCESS, exit code0:198 testes,0 falhas,0 erros,0 ignorados.** Nenhuma repetição, falha de preparação/infraestrutura ou ajuste necessário. Não executados baseline, suíte completa, checkpoints US1 separados ou T023+. Os37 casos JSON de resumo foram incluídos porque o seletor exige a classe DTO inteira; não representam repetição do checkpoint US1.

Os quatro relatórios recebidos da T021 foram movidos antes do Maven para destino inédito `target/feature007-validation/t022-f2-green-20261020/received-surefire-reports/`. Origem/destino absolutos resolvidos dentro de target, sem sobreposição/sobrescrita, tamanhos e SHA-256 conferidos após mover (`archive-preservation.json`: Count4, HashesMatch=true). Os dez novos relatórios (cinco XML/cinco TXT) estão em `target/surefire-reports/` e na cópia própria `reports/`; nenhum relatório anterior entra na contagem atual.

### Seleção efetiva e relação com as evidências anteriores

| Classe concreta Surefire | Métodos distintos por classe | Invocações aprovadas |
| --- | ---: | ---: |
| `OwnOrderQueryDetailApiHttpIntegrationTest` |7|9|
| `OwnOrderQueryDetailHttpIntegrationTest` |7|9|
| `OwnOrderQueryDtoContractTest` |19|119|
| `OwnOrderQueryRepositoryTest` — somente detail* |10|17|
| `OwnOrderQueryServiceTest` — somente detail* |13|44|
| **Total** |**56 por classe concreta**|**198**|

Console/XML/TXT e cada nó testcase conciliados:198 identidades únicas, sem omissões/duplicatas/falhas/erros/ignorados; índices parametrizados iguais aos conjuntos históricos correspondentes. Os conjuntos esperados vieram de T018 (18 HTTP), T016 (37 summary JSON), T019 (82 detail JSON), T020 (17 JPA) e T021 (44 detail serviço). A aprovação atual vem destes novos relatórios, sem somar resultados históricos. Não há testes `@Nested` nos fontes selecionados; suporte abstrato HTTP/configurações/helpers não são suítes ou testes adicionais. Nenhum teste `list*` de JPA/serviço, controller MockMvc, resolver ou segurança transversal foi selecionado. Compilação não comprova execução de classes fora desse conjunto.

`red-http-source-comparison.json` e `historical-source-comparison.json` confirmam os dois fontes HTTP byte a byte iguais aos executados na T018, incluindo todas as asserções e o suporte compartilhado: API SHA-256 `5F01E2ABEE9BCCE9DA5A1511C6F4B386E6B936BFDC75871E6DEABA7CD19AC251`, raiz/suporte `B3A223ED421D4A9762D2533A561E36B3D7C9D776E013588E535F37E69B6807D5`. Também conferidos os testes selecionados de DTO/JPA/serviço com seus fontes históricos capturados. Nenhum teste foi modificado para obter GREEN.

**T018 permanece RED histórico:18 falhas funcionais200 esperado/404 recebido pela rota de detalhe então ausente. Esse404 não provou titularidade, ausência/alheio ou envelope404 de negócio.** Nesta T022 os mesmos18 testes completos passaram e os28 GET de detalhe retornaram200 com JSON conferido; etapas anteriormente interrompidas foram alcançadas, conforme a tabela abaixo. Quantidades por cenário são de cada contexto, raiz e API.

| Cenário / método HTTP | Invocações por contexto | GET lista/detalhe por invocação | Verificações agora alcançadas e aprovadas |
| --- | ---: | --- | --- |
| US2.1 / `detailUs21ReturnsExactFieldsAndRecordedBAOrderWithHistoricalMoney` |1|1/1| Cabeçalho com exatamente cinco campos, itens com cinco cada; B/A, IDs e nomes integrais, quantidades2/3, preços10.00/7.50, subtotais20.00/22.50, total42.50, instante UTC/milissegundos e estado armazenado; quatro campos comuns iguais à lista. |
| US2.2 / `detailUs22ReadsOwnOrderOutsideThePreviouslyRequestedPage` |1|3/2| Detalhe do pedido mais antigo fora da página0/size1, página2 da mesma compra e cabeçalho igual, página1 intermediária, detalhe repetido após outra consulta de lista; totais3/3, conteúdo e ordem exatos. |
| US2.3 / `detailUs23UsesRenamedCurrentProductWithoutReplacingHistoricalPrice` |1|2/2| Detalhe antes da alteração, renome/preço atual12.00 realmente executados e confirmados no setup; leituras posteriores mostram nome atual e unitPrice10.00/subtotal/total20.00, com concordância lista/detalhe. |
| US2.4 / `detailUs24KeepsDistinctProductIdsAndPositionsAfterNamesBecomeEqual` |1|2/2| Renomes posteriores para o mesmo texto com espaços realmente executados; itens continuam B/A, IDs distintos, nome integral igual, linhas/valores sem fusão ou reordenação. |
| US2.5 / `detailUs25ReadsLegacyNameZeroCurrentPriceAndNoCategoriesWithoutRepair` |2|1/1| Variantes nome curto `Á` e longo87 caracteres, preço atual0.00 e sem categorias: detalhe legível, texto integral e histórico20.00; comparação com lista e verificação explícita de nome/preço atual após o detalhe, sem reparo. |
| US2.6 / `detailUs26PreservesExactSmallAndLargeTextualMoneyInListAndDetail` |2|1/1|0.01 e9007199254740993.01 textuais exatos em unitPrice/subtotal/total, duas casas, sem expoente/float/arredondamento; cabeçalho concorda com a lista. |
| US2.7 / `detailUs27KeepsEachPurchasesOwnQuantitiesPricesSubtotalsAndTotal` |1|1/3| Dois pedidos dos mesmos produtos mantêm quantidades/preços/subtotais próprios e totais52.00/42.50; ambos os detalhes, comparação dos cabeçalhos e repetição do mais recente aprovados. |

### HTTP real, persistência, representação e erros: alcance efetivo

**18 testes HTTP completos,72 requisições HTTP reais:18 POST/login,26 GET de lista e28 GET de detalhe; zero dispatches MockMvc.** Por contexto:9 logins,13 listas e14 detalhes,36 requisições. Os54 GET e os18 logins têm200 aprovado; GET com Content-Type JSON, campos/valores/status de negócio conferidos por asserções após o transporte. Contadores, console e saída por testcase reconciliados em `http-case-results.json`/`http-contexts.json`. Os marcadores T017 são nomes do suporte reutilizado, não repetição de escrita T017 nem evidência antiga. A fixture monta suporte MockMvc mas não o despacha nem usa seu login nesta seleção.

Aplicação completa, portas aleatórias, JWT/decoder/filtros e JPA reais, OSIV false, sem transação externa ao HTTP; nenhum mock substitui esses beans nos contextos reais. Contextos/bancos exclusivos, todos diferentes:

| Contexto | Porta | Banco H2 desta execução |
| --- | ---: | --- |
| HTTP API `/api` |61954| `jdbc:h2:mem:admin-product-test-8d0f62fe-346b-4839-ac7d-550942ff7078` |
| HTTP raiz |61959| `jdbc:h2:mem:admin-product-test-3075815e-3fb5-4a4d-b5f4-f93831208b51` |
| Repositório, ambiente MOCK | sem servidor | `jdbc:h2:mem:admin-product-test-025545b9-1830-4d8b-aea3-27a9ef1d96c4` |
| Serviço, ambiente MOCK | sem servidor | `jdbc:h2:mem:admin-product-test-4718e50f-defa-4426-a4fc-4de2028575e2` |

URLs estáveis por ApplicationContext, create-drop/sem seed e preparação JDBC commitada/limpeza própria aprovadas pelo suporte existente; nenhuma alteração de cache/ordem/seed/perfil/H2 global. DTO usa um slice JSON sem datasource, MVC ou cadeia de segurança (verificação por invocação), sem servidor ou persistência. Portanto são quatro contextos com H2, além do slice JSON.

**119 casos JSON:37 de resumo e82 de detalhe**, sem mocks/banco/HTTP; campos exatos, valores fornecidos, formato UTC/dinheiro, coleções defensivas, nomes integrais, rejeições monetárias e comparação de cabeçalho fornecido. Isoladamente não provam carregamento, titularidade ou cálculo, que são exercitados pelas demais partes do checkpoint.

**17 testes JPA reais:**31 janelas/31 SELECTs de detalhe, inclusive ausência/alheio; filtro `id AND buyer_id` da mesma raiz, DISTINCT, dois LEFT JOINs items/product e ORDER BY position, sem paginação/count/exists/lookup global/categorias. Confirmados itens completos (inclusive51), posições registradas mesmo com IDs internos invertidos, nomes atuais iguais/legados, valores de compras distintas e raiz sem itens preservada para diagnóstico. Controles de acesso ao mesmo ID sob titulares distintos e Optional vazio para alheio/inexistente passam no repositório; esses parâmetros de titular não representam autenticação HTTP.

**44 testes de serviço:8 invocações com guarda/JPA reais e36 com mocks locais.** Quinze janelas de sucesso e8 de rejeição,42 SELECTs observados e20 fetches de pedido; nenhum `realList`/teste JUnit list* executado. CLIENT e ADMIN recebem a mesma OwnOrderNotFoundException de mensagem fixa para alheio/inexistente (quatro janelas), sem leitura global ou reparo. Guardas reais rejeitam perfis revogados/conta ausente/Authentication nula antes do fetch; raiz sem itens falha internamente. DTO completo permanece serializável após retorno sem SQL adicional, nome atual/ordem/histórico comparados com manifesto independente. Mocks locais cobrem invariantes, escala/capacidade, guarda primeiro, falhas propagadas e cinco comparações de cálculo lista/detalhe sobre agregado fornecido; um desses testes de infraestrutura usa proxy transacional com colaboradores simulados, não leitura JPA real.

**Limite de erros:** nenhum GET HTTP desta seleção espera400/401/403/404/500; os54 retornaram200. Rejeições DTO e exceções de serviço/JPA passaram no nível descrito, sem comprovar por si status/envelopes/headers HTTP. As18 provas MVC da T021 (22 dispatches,400/404/401/403/500/HEAD) permanecem evidência histórica isolada e não foram selecionadas/reclassificadas como HTTP real. Consolidação HTTP de alheio/inexistente inclusive ADMIN, perfis/precedência e falhas reais por etapa continua T023–T027; não adicionar essa história ou seleção nesta T022. O antigo404 da T018 continua sem valor probatório de ownership. Proteção existente não foi alterada.

### Ausência de mutações, listagem e consistência

Todos os54 GET HTTP passaram por `assertNoWrites`: snapshots JDBC independentes de produtos/categorias/associações/contas completas/perfis/pedidos/itens iguais e somente SELECT Hibernate observado na janela. Observações JPA de repositório/serviço também conservaram snapshots e ausência de DML, inclusive rejeições. Setup/login/renomes/alterações de catálogo/perfis/limpeza ficam fora dessas janelas; são preparação explícita, não ações da consulta. Materialização HTTP bem-sucedida com OSIV false e serialização direta após retorno do serviço corroboram o DTO completo, sem depender de sessão aberta pela apresentação.

Preservados os limites documentados em T020/T021: snapshot composto por duas transações, sem fotografia global atômica sob concorrência; inspector Hibernate não observa JDBC direto; igualdade antes/depois não detecta escrita transitória restaurada. As31 consultas de repositório e42 SELECTs de serviço pertencem a janelas distintas, não orçamento geral de HTTP; não há contagem SQL HTTP detalhada emitida pelo suporte T017 para inferir esse orçamento. ReadOnly não foi tomado sozinho como prova. Nenhuma garantia de snapshot imutável entre requisições ou serialização de revogação posterior à leitura autorizadora foi adicionada.

As26 consultas reais de lista nos próprios cenários US2 conservam campos exatos, conteúdo/ordem/metadados esperados e total igual ao detalhe, inclusive após alterações de catálogo e mudança de página. Essa preservação efetiva é limitada a esses cenários; não substitui a seleção completa US1/P2 ou a regressão001–006. F1-RED T010, duas execuções T013/justificativa SQL, duas execuções/correções T015 e F1-GREEN T016 permanecem históricos íntegros, sem repetição ou nova atribuição de aprovação. T018 e os GREEN isolados T019/T020/T021 continuam identificados por suas revisões/seleções originais; F2-GREEN é uma nova execução conjunta, não soma/reclassificação dessas evidências.

### Auditoria e fechamento da T022

Diretório próprio ignorado `target/feature007-validation/t022-f2-green-20261020/`: estado inicial/manifestos, documentos recebidos, comando/ambiente/HEAD/diff/horários,31 fontes capturados, console/exit code, quatro relatórios recebidos/dez novos e auditorias por classe/caso/método/HTTP/SQL. `& 'target/feature007-validation/t022-f2-green-20261020/audit.ps1'`: exit0, Pass=true/F2GreenApproved=true,198 casos e transportes reconciliados, Feature007Complete=false, US3HttpOwnershipApproved=false. Conferidos294 rastreados antes do fechamento documental, cinco Java recebidos,31 fontes capturados e1.881 arquivos históricos intactos. Não houve falha/correção/repetição de teste.

A primeira chamada de `final-review.ps1` terminou exit1 na conferência auxiliar `git -c core.autocrlf=false diff --check`: o override desativou a normalização do repositório (`core.autocrlf=true`) e sinalizou CRLF recebidos no repositório/trechos históricos do quickstart como whitespace. Os mesmos bytes já estavam nos manifestos iniciais, sem mudança desses fontes nesta tarefa. Conferido o comando padrão exigido `git diff --check`: exit0. Preservados script/tentativa em `final-review-attempt1.ps1` e `review-attempt1.json`; corrigido somente o comando do auxiliar para usar a configuração normal do projeto, sem editar/normalizar arquivos recebidos ou alterar configuração Git. Essa falha de revisão estática não é falha/RED da aplicação e não exigiu novo Maven.

Fechamento estático final em `final-review.ps1`/`final-review.json`: exit0; prefixo integral do quickstart recebido preservado byte a byte, tasks difere somente pelo marcador T022; demais292 rastreados, cinco Java,31 fontes,1.881 arquivos históricos e relatórios recebidos/novos íntegros. Diff/arquivos novos, referências/links locais e whitespace revisados. `git diff --check` e `git diff --cached --exit-code`: exit0; cinco Java não rastreados conferidos individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 pela diferença de arquivo novo). Branch/HEAD/main/seleção007 e índice vazio preservados.

**Somente T022 marcada nesta execução: T001–T022 concluídas; T023–T034 abertas,12 pendências.** Checkpoint C3/F2-GREEN atendido no recorte US2; segurança transversal/falhas/US4/regressões/fechamento ainda obrigatórios para concluir a feature. Alterados nesta tarefa somente este acréscimo e o marcador T022; fontes/asserções recebidos intactos. Git final: os mesmos dez rastreados modificados e cinco Java não rastreados, nada staged. Sem impedimento, stage, commit, push, troca de branch/main, CI, baseline, suíte completa ou execução T023+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.


## T023 — Escrita da consolidação de titularidade e perfis atuais — 2026-10-20

**Escopo executado: exclusivamente T023, escrita de testes e revisão estática, dependente de T022.** Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `80dc6b1741d4624508610e8516471100745fc425`, Git limpo, nada staged e seleção local Feature007. Esse HEAD registra o checkpoint US2/T017–T022 (`feat: view own order details`); main permanece em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Skill `speckit-implement` limitada ao pedido; T023 integral, dependência T022, procedimentos F3 e artefatos pertinentes de constituição/spec/plano/pesquisa/modelo/contrato/quickstart revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, diretório absoluto Feature007; checklist de requisitos16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente, sem hooks. Não executar tarefas paralelas nem interpretar `[P]` como autorização para T024.

### Testes HTTP escritos, ainda sem execução

Estendidos [OwnOrderQuerySecurityHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java) e [OwnOrderQuerySecurityApiHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java). A classe raiz contém o suporte abstrato novo `OwnOrderQueryUs3HttpTestSupport`, herdado pelas duas classes concretas e derivado do [suporte HTTP aprovado](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryHttpTestSupport.java). O arquivo do suporte anterior permanece byte a byte intacto: métodos, providers, asserções e controles `authenticationGate*` são herdados uma única vez, sem copiar seus casos. As anotações/configurações e identidades raiz/API anteriores permanecem; a única mudança na classe API é a superclasse intermediária.

| Cenário / método novo compartilhado | Invocações previstas por contexto | Expectativas escritas, não resultados obtidos |
| --- | ---: | --- |
| US3.1 / `ownershipUs31ListsCountsAndDetailsOnlyTheCurrentOwnersPurchases` |3| CLIENT, ADMIN isolado e ambos: páginas de tamanho2 até página vazia posterior, conteúdo/ordem/metadados/contagem exclusivamente próprios, todos os detalhes próprios200, repetição da lista e HEAD próprios200 sem corpo. Parâmetros accountId/buyerId/sort alheios não ampliam conteúdo/count. |
| US3.4–US3.5 / `ownershipUs34And35ForeignAndAbsentIdsHaveTheSame404EvenForAdmin` |3| Mesmos três perfis: provar primeiro200 do ID existente sob seu titular real; sob outra conta, esse ID e Long.MAX_VALUE confirmado ausente exigem404 de negócio com os mesmos status/error/message/campos. path conserva a URI específica sem query; GET e HEAD, JSON fixo/corpo vazio, nenhum dado de compra/titular. Lista/detalhe próprio continuam200. |
| US3.1–US3.3 / `ownershipUsesSubjectAndCurrentDatabaseRolesInsteadOfSignedNameOrRoleClaims` |4| JWT assinado pelo encoder real com subject da conta, name de outro titular e roles enganosos: três combinações autorizadas continuam vendo somente suas compras, sem acesso ao detalhe alheio; conta sem perfil com claims privilegiados continua403 em lista/detalhe. Login original e token reemitido são distinguidos. |
| US3.6 / `ownershipUs36AuthenticatesAndAuthorizesBeforeAdditionalInvalidInputs` |6| Casos adicionais sem duplicar os targets legados: page negativa, size51, page repetida, ID0, negativo e overflow de Long. Controle autorizado exige400; sem credencial401 e conta sem perfil403, em GET/HEAD, challenge Bearer apenas no401 e nenhum corpo HEAD. Envelope e path sem query exatos. |
| Perfis atuais / `ownershipCurrentRoleGrantAndRevocationApplyToTheSamePreviouslyIssuedJwt` |3| Conta sem perfil com compra própria faz login real uma vez. Mesmo token:403 inicial, concessão commitada CLIENT/ADMIN/ambos permite lista/detalhe e HEAD200, revogação volta a403 inclusive ID inválido e HEAD; nova concessão restaura200. Nenhuma reemissão/relogin entre fases. |

US3.2/US3.3 e precedência original de US3.6 também continuam nos `authenticationGate*` herdados: credencial ausente, malformada, assinatura inválida, expirada, conta removida e conta sem perfil; GET/HEAD de lista/segmento de detalhe válidos e malformados, envelope seguro e challenge. O antigo controle autorizado que aceita200/404/405 continua identificado como prova da barreira, não como prova de sucesso de negócio/ownership. Os novos cenários exigem explicitamente200/404 de negócio conforme o caso, sem enfraquecer asserções anteriores nem fabricar RED.

Preparadas **cinco funções HTTP novas,19 invocações por contexto,38 novas no total**; essas contagens são revisão dos fontes/providers, não descoberta Surefire. O suporte abstrato não é terceira suíte. Métodos herdados não são novos casos. Contadores `T005_*` continuam pertencendo ao suporte original; `T023_HTTP/T023_COUNTS` separam as novas consultas/logins por contexto, sem imprimir token, senha, hash ou corpo. A descoberta das duas classes e a contagem efetiva dependerão do checkpoint futuro.

### Suporte, persistência e leitura: procedimento preparado

Reutilizados sem alteração `AdminProductTestConfig`, `OwnOrderQueryTestFixture`, `OwnOrderQueryTestObservationConfig`, `OwnOrderQueryDatabaseProbe` e a configuração global. HTTP previsto por `HttpClient`, porta aleatória, login/JWT/decoder/filtros/JPA reais, OSIV false; nenhum mock de bean ou transação externa ao HTTP. As propriedades raiz/API e URLs H2 exclusivas/estáveis por ApplicationContext continuam as aprovadas, com create-drop e SQL/import desabilitados somente no contexto isolado. O contexto JPA novo tem identidade própria distinta. Não há mudança de cache/ordem/seed/schema global, produção, entidades, políticas POST/admin/público ou poderes de ADMIN.

Cada caso novo usa sua própria fixture commitada e manifesto independente de pedidos/itens. O setup legado de contas/tokens permanece vivo no superclass; a limpeza local remove somente as linhas da fixture nova, itens/pedidos antes de produtos/contas. Uma asserção adicional após cada caso compara o snapshot completo com o setup legado recebido, incluindo quando o caso é herdado, e confere URL estável. Não duplicar o login MockMvc da fixture: seu suporte é construído, mas as novas requisições são escritas para transporte real.

Cada GET/HEAD novo fica entre snapshots JDBC independentes de produtos/categorias/associações/contas completas/perfis/pedidos/itens, com `assertNoWrites` e observação Hibernate somente SELECT. Login/setup/concessão/revogação/limpeza ficam fora dessas janelas. Esperados vêm dos manifestos, não de getTotal/getSubtotal/mapper produtivo. Igualdade JSON cobre todos os campos, itens/ordem, valores, totais e metadados; os dois lados são lidos como JSON para não confundir pequenos IDs int64 com classes numéricas internas de JsonNode. Diagnósticos de divergência não imprimem o corpo possivelmente sensível. Erros exigem exatamente os quatro campos, mensagens/status e path sem query; nenhum dado alheio ou credencial é permitido como campo extra.

### Projeção escalar: teste JPA separado, ainda sem execução

Criado [OwnOrderQueryCurrentRolesIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java), SpringBoot completo `webEnvironment=MOCK` com H2 próprio, OSIV false e mesmo inicializador/probe aprovados, sem servidor, login, cliente HTTP ou mocks. `scalarRolesReflectCommittedGrantRevocationOrReplacementWhileTheManagedCollectionStaysStale` prepara seis transições: vazio→CLIENT, vazio→ADMIN, CLIENT→vazio, ADMIN→vazio, ambos→CLIENT e CLIENT→ADMIN. Carrega conta/roles gerenciadas; altera o banco em REQUIRES_NEW commitado; conserva a mesma instância e coleção inicial obsoleta na transação externa READ_COMMITTED. Consulta diretamente `findEffectiveAccountRolesById` e a guarda real: linhas escalares atuais, left join com role nulo para conta existente sem perfil, autoridades exatas/negação, nenhuma leitura de pedidos e nenhum DML na janela. Authentication fornecida tem autoridade ADMIN deliberadamente enganosa; não é login/JWT nem prova HTTP.

`scalarLookupDoesNotAutoFlushAnUnsavedManagedRoleChangeOverCommittedPermissions` acrescenta duas variantes com coleção gerenciada suja e deliberadamente oposta à permissão commitada, para exigir que a consulta escalar/guarda não faça autoflush nem regrave perfis. As transações externas são sempre encerradas com rollback; comparações JDBC independentes durante/depois conservam a preparação commitada. Cleanup é da fixture própria. **Dois métodos, oito invocações previstas de JPA; ainda não compilados/executados.** Nenhuma requisição HTTP é simulada dentro dessas transações; não há promessa de serialização de revogação concorrente depois da leitura autorizadora.

### Validação atual e próximos checkpoints

**Nesta T023: zero execuções Maven/compilador, zero testes JUnit, zero inicializações Spring/H2 da aplicação, zero requisições HTTP e zero dispatches MockMvc.** Escrita/revisão estática não comprova compilação, descoberta Surefire, setup, status real, concessão/revogação ou projeção JPA. Nenhum console, exit code Maven ou relatório Surefire novo foi produzido; os dez relatórios recebidos continuam em seus lugares, sem movimentação ou reclassificação. Não foram executados baseline, checkpoints anteriores ou suíte completa.

A aprovação histórica T022 continua sendo uma execução de198 aprovados/zero falhas/erros/ignorados/exit0, restrita àquela revisão/seleção. Titularidade alheio/inexistente já tem prova JPA/serviço T020–T022; **a consolidação HTTP da US3 permanece pendente**, agora com as asserções escritas. T018 continua RED por endpoint ausente, sem prova de titularidade. As evidências e asserções de T001–T022 permanecem históricas, sem repetição, enfraquecimento ou nova atribuição de aprovação.

Futuro procedimento **não executado nesta T023**: concluir a escrita T024 e somente então executar F3 pela T025, na raiz/Java21, com Wrapper Maven3.9.16 e protocolo de preservação de relatórios de [tasks.md](tasks.md). Não criar uma seleção focal alternativa nesta etapa. Seleção prevista para esse checkpoint posterior:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*,OwnOrderQueryDetail*,OwnOrderQuerySecurity*,OwnOrderQueryFailure*,OwnOrderQueryCurrentRolesIntegrationTest,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest' test
```

Conferir nos futuros XML/TXT/console os métodos herdados e novos em ambas as classes concretas, variantes parametrizadas e oito casos JPA, sem somar suporte abstrato ou requisições como testes JUnit. Falhas/omissões exigem diagnóstico e preservação segundo T025–T027; não fabricar RED de proteção existente. Falhas reais por etapa/erros500 continuam T024; regressões/fechamento seguem T028+.

Limites de leitura permanecem: snapshots compostos em duas transações não são fotografia global atômica concorrente; inspector não observa JDBC direto; igualdade antes/depois não detecta escrita transitória restaurada. readOnly não é prova isolada. Não inferir orçamento SQL HTTP ou equivalência temporal de404, snapshot imutável entre requisições, conclusão US3/US4 ou conformidade integral da Feature007 a partir da escrita.

### Preservação e fechamento estático

Diretório ignorado próprio `target/feature007-validation/t023-security-tests-20261020/`: manifesto inicial dos299 rastreados,1.959 arquivos históricos e dez relatórios recebidos, cópias de quickstart/tasks e revisão estática. A primeira tentativa de preparar hashes por Get-FileHash não resolveu cinco caminhos históricos longos no Windows; o helper `preserve.ps1` usou caminhos estendidos para completar a leitura, sem editar/mover esses arquivos. Esse impedimento do helper não é falha/RED da aplicação. Nenhum Maven foi iniciado.

Diff integral, novo Java, imports/assinaturas/providers, herança/ciclo de vida, links locais e whitespace revisados. Conferências estáticas em `review.ps1`/`final-review.json`: exit0, preservação dos demais295 rastreados, suporte anterior e todas as fontes de produção,1.959 arquivos históricos e dez relatórios, prefixo integral de272.078 bytes do quickstart e único marcador T023 em tasks;24 imports locais e90 links locais conferidos, branch/HEAD/main/seleção007 e índice vazio. `git diff --check` e `git diff --cached --exit-code`: exit0. Java novo conferido por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnóstico (exit1 pela diferença de arquivo novo). São verificações estáticas, não compilação/testes nem checkpoint F3.

A tentativa de revisão preservada em `review-attempt1.ps1`/`review-attempt1.json` terminou exit1 porque PowerShell com ErrorActionPreference Stop tratou o aviso nativo de conversão futura LF/CRLF de `git diff --check` como NativeCommandError. Ajustada somente a captura de stderr do helper para conferir o exit code do Git; não foi desativada a normalização do repositório, editada configuração Git ou normalizado arquivo histórico. A revisão estática seguinte concluiu exit0; nenhuma falha/RED da aplicação nem execução Maven decorreu disso.

**Somente T023 marcada nesta tarefa: T001–T023 concluídas; T024–T034 abertas,11 pendências.** Critério de escrita atendido; aprovação executável das novas provas permanece futura. Alterados somente os dois Java HTTP, o novo Java JPA, este acréscimo e o marcador T023: cinco arquivos, sendo quatro rastreados modificados e um Java não rastreado, nada staged. Sem stage, commit, push, troca de branch/main ou execução T024+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.


## T024 — Escrita de falhas reais por etapa e erros de contrato — 2026-10-20

**Escopo executado: exclusivamente T024, escrita de testes e revisão estática, dependente de T022.** Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `80dc6b1741d4624508610e8516471100745fc425`, seleção local Feature007 e nada staged. Recebidos exatamente quatro rastreados modificados: os dois testes HTTP de segurança da T023, quickstart/tasks; somente `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java` não rastreado. T023 permanece escrita/revisão estática, sem aprovação executável das invocações previstas. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

Aplicada a skill `speckit-implement` somente à T024; lidas tarefa integral, dependência T022, procedimento F3/T025 e fontes pertinentes de constituição/spec/plano/pesquisa/modelo/contrato/quickstart. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, diretório absoluto Feature007; checklist requirements16/16, PASS documental. `.specify/extensions.yml` ausente; ignores Java existentes suficientes. A T025 determina executar F3 depois da escrita T023–T024; **nenhum Maven, compilador ou checkpoint foi executado nesta T024**.

Criados somente [OwnOrderQueryFailureInjectionTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryFailureInjectionTestConfig.java), [OwnOrderQueryFailureHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureHttpIntegrationTest.java) e [OwnOrderQueryFailureApiHttpIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureApiHttpIntegrationTest.java). O arquivo raiz contém o suporte abstrato compartilhado `OwnOrderQueryFailureHttpTestSupport`; as duas classes concretas herdam seus casos, sem `@Nested` nem herança/repetição de testes dos checkpoints anteriores. Os três Java recebidos da T023 e todo o suporte anterior ficam byte a byte intactos; produção, asserções anteriores, entidades e contratos não foram alterados.

### Injeção escopada preparada, ainda sem exercício

Configuração `@TestConfiguration`/perfil test importada explicitamente apenas pelas duas classes novas. H2 exclusivo/estável por ApplicationContext via `AdminProductTestConfig`; propriedades de identidade `feature007.own-order-failure-test-context=root/api`, distintas das anteriores; portas aleatórias, OSIV false, create-drop, SQL/import desabilitados somente nesses contextos. Reutilizados fixture, probe e observador SQL aprovados. Sem mudança de perfil/seed/cache/ordem global, cadeias de segurança, decoder, políticas POST/admin/público ou poderes de ADMIN.

O probe envolve os proxies Spring transacionais/repositórios existentes e o datasource/JDBC reais; não substitui serviço/repositório por mocks. Um filtro de teste anterior à segurança vincula por ThreadLocal somente a requisição cujo header de caso coincide com o Attempt armado. Sem o marcador, tudo é delegado sem injeção. Setup, login, snapshots independentes e cleanup ficam fora do armamento; um latch confirma término da requisição antes de ler contadores. A configuração não é descoberta globalmente por component scan.

| Etapa escrita | Ponto exato preparado | Expectativa futura |
| --- | --- | --- |
| Lookup inicial | Advice do `findByIdWithRoles`, chamado pela resolução real do subject antes do adaptador de leitura. |500 fixo; não alcançar serviço de consulta nem leituras de pedido. Não converter falha de resolução em401. |
| Escalar no filtro / guarda tardia | Advice de `findEffectiveAccountRolesById`, distinguindo entrada anterior ao serviço e chamada dentro de list/detail. |500 fixo e contador da etapa específica; guarda tardia dentro da transação real, antes do fetch. |
| Página de IDs, count e fetch | Delegação real de PreparedStatement.executeQuery; contagem por fase de repositório e SQL. Após retorno do driver, fecha o ResultSet e lança SQLException controlada no ponto selecionado. |500 fixo, nunca vazio/parcial/404; SQL efetivamente delegado, inclusive count, e sem avanço a etapas posteriores ao ponto falho. |
| Cálculo | Agregado carregado pelo fetch real recebe decorador transitório que falha ao alcançar getTotal no serviço. |500 fixo após validação das linhas, sem reprecificação, reparo ou alteração de entidade gerenciada. |
| Mapeamento do detalhe | Decoradores transitórios de pedido/item/produto delegam ao agregado real e falham no getName usado pelo DTO de detalhe. |500 fixo após fetch/cálculo, sem substituir consulta nem alterar campos da entidade gerenciada. Essa etapa específica do nome não é atribuída à lista. |
| Conclusão transacional | Synchronization real em beforeCommit, após o corpo devolver o DTO e antes do proxy transacional devolvê-lo ao controller. | Corpo retornou uma vez; proxy não retornou; rollback observado;500 sem resposta parcial. Não é simulação de falha física de commit já delegado/confirmado pelo driver. |

A fixture usa cinco pedidos próprios e outros intercalados; page0/size2 é completa. Além da condição escrita que exige count no caminho paginado, as asserções exigem `countSqlExecuted=1` tanto no controle de sucesso quanto nas falhas posteriores ao count. A injeção COUNT só ocorre **depois de o executeQuery do driver retornar**; lançar uma exceção antes de chamar o repositório não é aceito como prova desse count. Caminhos anteriores ao count exigem contadores zero nas etapas ainda não alcançadas. Esses mecanismos estão escritos, ainda sem SQL observado em execução nesta tarefa.

### Casos preparados para raiz e API

| Método compartilhado | Invocações previstas por contexto | Expectativas escritas, não resultados obtidos |
| --- | ---: | --- |
| `failureUs37IsSafe500AtTheReachedRealStage` |30| GET e HEAD: lookup inicial/escalar do filtro/escalar tardio/cálculo/conclusão em lista e detalhe; página de IDs/count/fetch da lista; fetch/mapeamento do detalhe. Exige500 e envelope status/error/message/path fixo, Content-Type JSON, sem challenge401 ou dados internos; HEAD sem corpo. Um hit na etapa, transação no hit e ausência de retorno do proxy, sem falso sucesso parcial. |
| `failureDisarmedStageControlUsesRealQueriesAndCompleteSuccessfulDto` |4| NONE significa injeção desativada, observação do mesmo transporte/proxies ativa: GET/HEAD próprios200 em lista/detalhe, count real da lista, DTO completo/campos/valores/metadados exatos, conclusão transacional aprovada pelo controle escrito e nenhum hit. Não é reexecução de checkpoint nesta tarefa. |
| `failureLateCurrentAccountOrProfileLossRetains401Or403InsteadOf500` |8| GET/HEAD de lista/detalhe; após conversão do JWT no filtro, antes da guarda tardia real: remoção commitada da conta→401 com Bearer; remoção somente do CLIENT→403 sem challenge. Nenhum fetch de pedido nem body/proxy return; rollback da transação leitora não desfaz a preparação independente. |
| `failureInvalidInputIs400BeforeAnyServiceOrCompletionInjection` |4| Paginação negativa ou ID0 após autorização:400 fixo, query fora do path, HEAD vazio; serviço/guarda/hit não alcançados mesmo com COMPLETION armado. Autenticação do filtro continua distinta da validação MVC. |
| `failureNativeMethodAndNegotiationKeepTheirSemanticsBeforeTheService` |6| PUT em lista/detalhe→405, Allow comparado ao header nativo do Spring para os métodos declarados GET/POST da lista e GET do detalhe, sem impor ordem ou acrescentar métodos ao contrato nativo; GET/HEAD com Accept text/plain→406. Serviço/injeção não alcançados; preservar semântica/headers nativos sem forçar JSON de negócio no406; HEAD sem corpo. |

**Cinco métodos compartilhados,52 invocações escritas por contexto,104 novas previstas no total.** São contagens estáticas dos providers, não descoberta Surefire, resultados JUnit ou contagem de HTTP realizado. Configuração/suporte abstrato não são suítes adicionais. Nenhuma quantidade prevista na T023 foi presumida como executada ou aprovada. `T024_CONTEXT/HTTP/REACHED/COUNTS` registram futuramente contexto, transporte, etapa e contadores, sem imprimir corpo, credencial, JWT ou hash. Mensagens privadas controladas das injeções não podem aparecer no envelope.

Cada caso terá fixture própria e login por HttpClient/JWT/decoder/filtros reais, sem transação do chamador envolvendo HTTP e sem dispatch MockMvc. O manifesto fornece esperados de cabeçalho/itens/finanças/ordem; não usa mapper/cálculo produtivo como oráculo. No controle GET, ambos os lados são parseados como JSON para comparar IDs numéricos pequenos sem confundir IntNode/LongNode. Diagnósticos de corpo divergente usam mensagem fixa segura.

### Preparação tardia deliberada e limites dos snapshots

Nos casos ordinários, snapshots JDBC completos antes/depois devem ser iguais e o StatementInspector deve observar somente SELECT. Nos casos LATE_REMOVED/LATE_NO_ROLE, o callback de teste faz uma **mutação deliberada durante a requisição, antes da guarda tardia**, em conexão independente commitada. Isso é preparação injetada, não escrita de produção nem prova de que o GET permaneceu imutável em toda a janela externa. Revogação exclui só o perfil; remoção da conta exclui primeiro seus itens/pedidos próprios para respeitar FKs, depois perfil/conta da fixture, sem tocar terceiros. Asserções conferem catálogo, pedidos/itens e identidades/perfis alheios preservados; para revogação, todos os dados de compra permanecem iguais.

O snapshot capturado imediatamente depois dessa preparação passa a ser a referência para o restante da requisição; exige-se igualdade dele com o estado final e somente SELECT Hibernate. O JDBC direto do callback não é visível ao inspector e não é apresentado como zero DML global. Cleanup continua restrito às linhas da fixture e ocorre depois da observação. Mantidos os limites conhecidos: snapshots compostos não são fotografia global atômica, inspector não observa JDBC direto e igualdade antes/depois não detecta escrita transitória restaurada. Não prometer serialização de revogação após a leitura autorizadora, equivalência temporal de404 ou fotografia imutável entre requisições.

### Resultados atuais e execução futura

**Nesta T024 foram executados zero Maven/compiladores, zero testes JUnit, zero inicializações Spring/H2 da aplicação, zero requisições HTTP e zero dispatches MockMvc.** Nenhum console, exit code Maven ou relatório Surefire novo existe desta escrita. Os dez relatórios recebidos não foram movidos/regravados/contados como novos. Não há RED/GREEN funcional atual, aprovação de compilação/preparação ou observação real dos pontos de injeção; comportamento existente aprovado, falha funcional e impedimento de compilação/setup só poderão ser classificados depois do checkpoint efetivo. Nenhum RED artificial foi fabricado.

A dependência T022 permanece o GREEN histórico de198 aprovados/zero falhas/erros/ignorados/exit0 na revisão/seleção registrada. Titularidade alheio/inexistente já foi validada em JPA/serviço; sua consolidação HTTP segue escrita na T023 e pendente de F3. O antigo404 da T018 por endpoint ausente não prova proteção. Os controles novos apontam aos endpoints implementados, mas não foram executados. Nenhuma prova JPA/serviço ou contagem prevista foi reclassificada como HTTP real aprovado.

**Procedimento posterior, não executado:** F3 é a T025; usar exatamente o seletor aprovado em [tasks.md](tasks.md), Wrapper sequencial na raiz/Java21, preservando relatórios recebidos em destino inédito dentro de target antes de iniciar. Não executar seleção focal alternativa, baseline, checkpoints anteriores ou suíte completa nesta T024.

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*,OwnOrderQueryDetail*,OwnOrderQuerySecurity*,OwnOrderQueryFailure*,OwnOrderQueryCurrentRolesIntegrationTest,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest' test
```

No F3, capturar console, exit code e novos XML/TXT; conferir ambas as classes concretas de falhas, métodos/providers e invocações efetivamente executadas, suites aninhadas dos demais fontes selecionados, sem duplicar agregadores/contêineres vazios. Conferir hits, count JDBC delegado, body/proxy returns e beforeCommit/rollback, além de status/headers/path/JSON; a intenção do provider não basta. Verificar separadamente os casos HTTP da T023 e seu JPA de coleção obsoleta, sem herdar aprovação de suas contagens previstas. Erro de compilação, filtro, fixture, schema, SQL não reconhecido ou etapa não alcançada é impedimento de preparação/observação ou teste falho a diagnosticar, nunca prova automática de desvio funcional nem RED válido. T026 só corrige desvios demonstrados no checkpoint autorizado.

### Revisão estática e preservação

Diretório ignorado `target/feature007-validation/t024-failure-tests-20261020/`: estado inicial, manifesto dos299 rastreados recebidos, hash do Java não rastreado recebido,1.971 arquivos históricos, dez relatórios e cópias de quickstart/tasks. Sem mover/deletar/normalizar evidências. O acréscimo mantém integralmente o prefixo documental recebido, incluindo T023, e tasks muda somente o marcador T024. Produção e demais297 rastreados, inclusive os dois HTTP recebidos, e o Java JPA recebido são conferidos contra os hashes iniciais.

Revisados integralmente diff e três Java novos, imports locais/assinaturas/providers, alvos dos advice/JDBC, isolamento dos contextos, tratamento de ResultSet e ciclo de vida, snapshots e limites, referências/links/whitespace. A revisão é estática e não comprova compilação nem funcionamento da instrumentação. `review.ps1`/`final-review.json` registram o fechamento estático; comandos/exit codes finais são registrados ao concluir a revisão abaixo.

Fechamento estático por `& 'target/feature007-validation/t024-failure-tests-20261020/review.ps1'`: exit0, Pass=true;297 rastreados e o Java recebido intactos,1.971 arquivos históricos/dez relatórios preservados, prefixo de286.310 bytes do quickstart intacto,23 imports locais e94 links locais conferidos, somente marcador T024 alterado. `git diff --check` e `git diff --cached --exit-code`: exit0. Os quatro Java não rastreados foram conferidos individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <arquivo>`: nenhum diagnóstico, exit1 pela diferença de arquivo novo. Branch/HEAD/main/seleção007 e índice vazio preservados; nenhuma validação executável da aplicação inferida dessas verificações.

As três tentativas anteriores do helper, preservadas em `review-attempt1/2/3.ps1` e respectivos JSON, terminaram exit1 com PathTooLongException em GetFullPath/OpenRead. A inspeção da estrutura mostrou a causa: `@(ConvertFrom-Json)` reteve os1.971 registros como um único array; seus caminhos foram convertidos em uma string agregada, não em um caminho individual. Corrigida a enumeração do manifesto no helper. As tentativas intermediárias de lidar com caminhos longos não resolveram essa causa; a revisão final dispensa os switches .NET tentados, sem mudar configuração do repositório/Windows ou qualquer arquivo histórico. Essas falhas estáticas não são falha funcional, RED, compilação ou preparação da aplicação e não motivaram Maven.

**Somente T024 marcada nesta tarefa: T001–T024 concluídas; T025–T034 abertas,10 pendências.** Critério de escrita atendido; US3/Feature007 e as provas executáveis novas permanecem pendentes. Nesta tarefa: três Java novos e acréscimo/checkbox em quickstart/tasks. Estado recebido preservado: Git final com os mesmos quatro rastreados modificados e quatro Java não rastreados (um recebido mais três novos), nada staged. Sem stage, commit, push, troca de branch/main ou execução T025+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.


## T025 — Execução F3 conjunta e diagnóstico do ciclo de limpeza — 2026-10-20

**Escopo executado: exclusivamente T025, dependente da escrita T023–T024; nenhuma correção T026+ realizada.** Conferidos inicialmente: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `80dc6b1741d4624508610e8516471100745fc425`, seleção local Feature007 e índice vazio. Quatro rastreados modificados recebidos: quickstart/tasks e os dois HTTP de segurança. Os quatro Java não rastreados foram resolvidos e capturados por caminho completo/SHA-256:

- `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryFailureInjectionTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureApiHttpIntegrationTest.java`

Skill `speckit-implement` limitada ao pedido; T025 integral, dependências, protocolo/seletor F3 e fontes pertinentes de constituição/spec/plano/pesquisa/modelo/contrato/quickstart revisados. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, diretório absoluto Feature007; checklist requirements16/16, PASS documental. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignores existentes suficientes. Java21.0.12.1 Azul Zulu confirmado por `java -version`; Wrapper seleciona Maven3.9.16. Não havia Java/Maven concorrente antes da execução.

### Comando único, preservação e resultado

Executado exatamente o F3 aprovado, na raiz, sequencialmente, sem clean, sem seleção alternativa e sem baseline/suíte completa:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*,OwnOrderQueryDetail*,OwnOrderQuerySecurity*,OwnOrderQueryFailure*,OwnOrderQueryCurrentRolesIntegrationTest,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest' test
```

Runner `& 'target/feature007-validation/t025-f3-20261020/run.ps1'`: início `2026-10-20T19:59:27.7445651-03:00`, fim `2026-10-20T20:04:28.2229886-03:00`, duração externa300,478s; Maven04:51. **BUILD FAILURE, exit code1:1.107 testes,1.067 aprovados,30 falhas,10 erros,zero ignorados.** Não houve repetição Maven, correção de teste/suporte/produção ou tentativa de fabricar RED. Os115 fontes de teste compilaram com javac release21; produção já estava compilada. Surefire e os contextos Spring/H2/servidores iniciaram; o resultado não é impedimento de compilação ou inicialização. Avisos existentes de agente/depreciação/EOL não foram tomados como falha funcional.

Antes do Maven, os dez relatórios recebidos foram movidos de `target/surefire-reports/` para o destino inédito `target/feature007-validation/t025-f3-20261020/received-surefire-reports/`. Origem/destino absolutos conferidos dentro de target, disjuntos e sem sobrescrita; tamanhos e SHA-256 idênticos depois de mover (`archive-preservation.json`: Count10, HashesMatch=true). Os resultados atuais vêm somente dos32 novos arquivos,16 XML/16 TXT, em `reports/` e na área Surefire recriada; nenhum resultado anterior foi somado. Console, comando, revisão/diff/ambiente/horários/exit code, manifestos e41 fontes capturados ficam no diretório próprio ignorado.

### Seleção efetiva reconciliada

| Classe concreta Surefire | Executados | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| config.OwnOrderQuerySecurityConfigurationTest |58|0|0|0|
| controllers.OwnOrderQueryDetailApiHttpIntegrationTest |9|0|0|0|
| controllers.OwnOrderQueryDetailHttpIntegrationTest |9|0|0|0|
| controllers.OwnOrderQueryFailureApiHttpIntegrationTest |52|0|0|0|
| controllers.OwnOrderQueryFailureHttpIntegrationTest |52|0|0|0|
| controllers.OwnOrderQueryListApiHttpIntegrationTest |115|0|0|0|
| controllers.OwnOrderQueryListHttpIntegrationTest |115|0|0|0|
| controllers.OwnOrderQueryPageArgumentResolverTest |254|0|0|0|
| controllers.OwnOrderQuerySecurityApiHttpIntegrationTest |79|13|3|0|
| controllers.OwnOrderQuerySecurityHttpIntegrationTest |79|13|3|0|
| dto.OwnOrderQueryDtoContractTest |119|0|0|0|
| repositories.OwnOrderQueryRepositoryTest |33|0|0|0|
| security.OwnOrderQueryJwtAuthenticationConverterTest |26|0|0|0|
| services.OwnOrderQueryAccessServiceTest |16|0|0|0|
| services.OwnOrderQueryCurrentRolesIntegrationTest |8|4|4|0|
| services.OwnOrderQueryServiceTest |83|0|0|0|
| **Total** |**1107**|**30**|**10**|**0**|

Prefixo das classes: `com.devsuperior.dscommerce`. Foram16 classes,157 combinações classe/método e1.107 identidades testcase únicas; console, atributos de suite, XML/TXT e nós testcase concordam. `selection-audit.json` compara as classes concretas do seletor e os métodos do fonte, resolvendo a herança HTTP: zero diferenças de classes/métodos, zero lacunas nos índices parametrizados, zero duplicatas, zero suites vazias. Não há `@Nested` nesses fontes nem relatórios aninhados/agregadores a somar. Helpers abstratos/configuração de injeção não são testes adicionais. `OwnOrderQuerySecurityConfigurationTest` foi incluído pelo wildcard Security*, não é HTTP/JPA. As quantidades agora são resultados reais, não reaproveitamento das previsões T023/T024. Nenhum teste fora do seletor foi contado por ter sido compilado.

### Classificação das40 ocorrências: desmontagem e resíduo da fixture

`issue-classification.json` e os stacks originais classificam **todos os10 erros e30 failures JUnit como problemas de cleanup/estado residual dos testes; zero ocorrência sem classificação e nenhum desvio funcional de endpoint demonstrado**. Não são RED funcional nem justificativa para alterar produção. A execução falhou e a aprovação F3/US3 permanece bloqueada por esse impedimento do harness.

| Origem | Erros | Failures | Local/causa observados |
| --- | ---: | ---: | --- |
| Perfis atuais HTTP, três variantes em raiz e API |6|0| Método `ownershipCurrentRoleGrantAndRevocationApplyToTheSamePreviouslyIssuedJwt` chega ao fechamento da fixture, que tenta excluir conta ainda referenciada por perfil concedido por JDBC. H2 SQLState23503, FK_USER_ACCOUNT_ROLE_ACCOUNT, DataIntegrityViolationException. |
| Demais casos novos de segurança após as limpezas malsucedidas |0|26| Após executar as asserções dos corpos, `preserveInheritedAccountsAndCleanOnlyEachUs3Fixture` falha no `@AfterEach`: produtos/categorias/associações/contas/perfis residuais diferem do snapshot original. São13 por contexto: seis variantes de entrada, três de alheio/inexistente e quatro de claims. |
| Coleção obsoleta/projeção escalar JPA |3|3| Três erros de fechamento com a mesma FK; três outras invocações falham em `verifyOwnedCleanupAndStableContext/assertEmpty` pelo resíduo acumulado. |
| Coleção suja/ausência de autoflush JPA |1|1| Concessão/revogação executadas, mas uma variante termina no erro de fechamento e outra na asserção de banco vazio posterior. |

A fonte [AdminProductTestFixture](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java) registra os pares accountId/role que criou em `owned.roles`; cleanup apaga somente esses pares antes de apagar as contas, preservando sua disciplina de propriedade. A T023 alterou perfis diretamente, fora desse registro: a conta originalmente sem perfil termina o cenário HTTP com a concessão restaurada; variantes JPA também deixam papéis diferentes dos preparados. Esses novos pares continuam no banco e impedem o DELETE da conta. A transação de cleanup do catálogo é revertida pela FK, retendo as demais linhas; a limpeza de pedidos/itens já ocorreu em etapa própria. Isso explica os resíduos nas verificações seguintes. A falha está no ciclo de vida dos novos testes de perfis recebidos, não em escrita inesperada de um GET, indisponibilidade de H2 ou DROP entre contextos.

Os dez erros também carregam falhas suprimidas de `@AfterEach`, preservadas nos XMLs; não foram contadas como testes ou failures adicionais. As30 failures primárias apontam exclusivamente aos hooks de verificação pós-teste. Não houve violação de status esperado, JSON de resposta ou cálculo identificada nos40 registros. Nenhuma asserção foi retirada para ocultar esses problemas; nenhum fonte foi corrigido. O tratamento dessa limpeza e eventual nova execução precisam de escopo posterior autorizado; não antecipar T026/T027 nem pressupor desvio produtivo pela cor vermelha do build.

### HTTP real: aprovações completas e evidência parcial de titularidade/perfis

Oito classes HTTP concretas executaram510 invocações JUnit:478 aprovadas e32 não aprovadas por cleanup/resíduo. Contadores, console e saída por testcase reconciliados: **974 requisições HTTP reais,200 logins e774 consultas/ensaios de método (588 GET,182 HEAD e4 PUT)**. As774 respostas têm status compatível com a expectativa específica do caso; não implica que todos os testes completos tenham passado. Separadamente, o resolver efetuou217 dispatches MockMvc; eles não são HTTP de rede nem testes JUnit adicionais. A fixture monta suporte MockMvc, mas os logins/consultas das oito classes usam HttpClient/porta/JWT/filtros reais.

- **Lista/detalhe:**230 e18 testes completos aprovados, respectivamente, em raiz/API; conteúdo/totais/ordem/representações e histórico financeiro preservados no recorte F3. São resultados novos desta revisão/seleção, não soma dos históricos F1/F2.
- **Proteção herdada:**120 `authenticationGate*` completos aprovados, mantendo autenticação/permissão/precedência e HEAD. Seus controles autorizados de roteamento ainda aceitam200/404/405 e não substituem prova estrita de ownership.
- **Titularidade estrita própria:**seis novos testes completos aprovados, três por contexto, CLIENT/ADMIN/ambos; páginas/count/detalhes próprios e HEAD, inclusive parâmetros de titular alheio ignorados.
- **Demais32 casos novos HTTP da T023:**os passos de resposta/JSON/status esperados foram alcançados antes do erro de fechamento ou do hook residual, mas esses casos JUnit não são GREEN. Incluem mesmo ID200 sob dono real e404 sob outra conta/inexistente, inclusive ADMIN; claims enganosos, entrada inválida e concessão/revogação/reconcessão sob o mesmo JWT. São observações parciais num ciclo de isolamento falho; não aprovar a consolidação integral US3 nem reclassificá-los como testes aprovados. O404 antigo da T018 por endpoint ausente continua sem valor probatório de titularidade.

Foram11 contextos de aplicação com H2 distintos: oito HTTP, repositório, serviço e JPA de perfis. URLs reais/portas estão em `transport-console.json`; sem compartilhamento entre configurações. OSIV false e inicializador seed-free permanecem. Há ainda o H2 independente do teste de proxy transacional do serviço de acesso, sem JPA; slices JSON, testes de configuração/JWT e mocks locais não são consultas JPA ou HTTP reais. O resolver tem MockMvc próprio e não usa servidor/banco. Não transformar essas métricas em cobertura integral de regressões001–006 ou suíte completa.

### JPA/serviço e falhas injetadas: alcances distintos

**Repositório:**33 casos reais aprovados, incluindo lista/detalhe, titular obrigatório, count/fetch, ordenação, ausência/alheio e não mutação nas janelas. Contadores51 janelas,13 páginas de IDs/sete lotes e31 detalhes se referem às janelas desse teste, não orçamento global HTTP. **Serviço:**83 aprovados;16 invocações com JPA/guarda reais (oito listReal/oito detailReal) e67 com colaboradores simulados locais. Foram12 janelas reais antigas de lista mais15 sucessos/oito rejeições de detalhe. Não atribuir aos mocks a prova de carregamento ou autorização de HTTP real.

**JPA novo de perfis:**oito invocações executadas, nenhuma integralmente aprovada (quatro failures/quatro errors). As marcações `T023_JPA` e stacks mostram que os passos de projeção escalar com coleção gerenciada obsoleta/suja, commit independente e rollback externo foram alcançados, antes de a limpeza/banco vazio falhar. Isso é evidência parcial de corpo, sem autenticação HTTP e sem GREEN da classe. Não atribuir a ela aprovação das jornadas HTTP de JWT ou vice-versa. Unitários de acesso/conversor/configuração aprovados são provas complementares com seus próprios limites.

**Falhas T024:**104 testes HTTP completos aprovados,52 por contexto. Asserções de etapa e transporte passaram: lookup inicial, escalar do filtro/tardio, IDs/count/fetch, cálculo e nome no mapeamento do detalhe;60 respostas500 (30 GET e30 HEAD), envelope fixo com path sem query, HEAD vazio e sem challenge401. Count JDBC efetivamente delegado quando exigido; falhas posteriores não viraram vazio/404/sucesso parcial. COMPLETION alcançou beforeCommit após retorno do corpo/DTO, com zero retorno do proxy e rollback. Essa simulação não representa falha física de commit já delegado/confirmado no driver nem prova de toda falha possível de serialização/transporte.

Os controles NONE passaram com200 real e DTO completo, nas mesmas configurações instrumentadas. Os16 casos de perda tardia de conta/perfil passaram com401/Bearer ou403, sem fetch; oito controles de entrada passaram com400 antes do serviço. Os12 ensaios nativos preservaram405/Allow ou406, incluindo HEAD sem corpo. Log de SQL controladamente injetado pode conter nível ERROR; isso não é failure JUnit dos104 aprovados. Janelas ordinárias conservaram snapshots completos/SELECT-only. Nos16 casos de mutação tardia, callback JDBC independente faz preparação deliberada antes da guarda; a referência do restante da requisição é o snapshot após essa mutação, não o estado anterior ao GET inteiro. Não apresentar esses casos como ausência global de DML.

Persistem os limites já registrados: snapshots compostos não são fotografia global atômica; inspector Hibernate não observa JDBC direto; igualdade antes/depois não detecta escrita transitória restaurada. Não há promessa de serialização de revogação depois da leitura autorizadora, equivalência temporal de404, snapshot imutável entre requisições ou orçamento SQL HTTP derivado dos componentes.

### Fechamento da T025 e preservação

`audit.ps1`: exit0 como auditoria dos relatórios, **não** aprovação da aplicação. `audit.json`: MavenRuns1/MavenExitCode1, SelectionReconciled=true, ApplicationApproved=false, Feature007Complete=false. `diagnose.ps1`: exit0; zero divergência de status nas774 observações, classificação dos40 problemas e F3GreenApproved=false/US3HttpCheckpointApproved=false. Registros por caso/método/suite, seleção e índices, HTTP/etapas/contextos, diagnóstico e fontes capturadas ficam separados no diretório ignorado. A preservação final compara297 rastreados fora dos dois documentos, quatro Java recebidos,41 fontes capturados,1.988 arquivos históricos, dez relatórios arquivados e os32 novos com seus hashes. Prefixo integral do quickstart recebido preservado; único marcador modificado em tasks é T025.

**Somente T025 marcada nesta execução, como checkpoint diagnóstico executado e reconciliado.** Sua descrição exige executar F3 e registrar os resultados antes de corrigir; não exige GREEN para registrar a execução, diferentemente da consolidação posterior. A checkbox não aprova F3, não conclui US3/Feature007 e não fecha o impedimento do harness. T001–T025 marcadas; T026–T034 abertas,nove pendências. Alterados nesta tarefa somente este acréscimo e o marcador T025. Os quatro rastreados modificados e quatro Java não rastreados recebidos continuam nessa condição, nada staged; HEAD/branch/main/seleção007 preservados. Sem correção, novo Maven, baseline, suíte completa, CI, stage, commit, push ou execução T026+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

Fechamento estático: `& 'target/feature007-validation/t025-f3-20261020/final-review.ps1'`, exit0. `final-review.json` confirma todos os hashes acima, prefixo recebido do quickstart intacto e tasks alterado somente em T025; 95 links locais existentes conferidos. `git diff --check` e `git diff --cached --exit-code`: exit0. Cada um dos quatro Java não rastreados foi revisado e conferido com `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <caminho>`, sem diagnóstico de whitespace (exit1 pela diferença de arquivo novo). Avisos Git de futura conversão LF/CRLF não são erros de whitespace. Diff integral e referências revistos; esta auditoria documental não executa testes da aplicação. Uma leitura auxiliar anterior do manifesto de hashes agregou indevidamente caminhos ao envolver o array JSON e encontrou PathTooLong; corrigida somente a leitura do helper, sem alteração de entradas nem repetição Maven. Conferência posterior completa: exit0. Hooks pós-execução novamente ausentes. Estado final: quatro rastreados modificados, quatro Java não rastreados nos caminhos iniciais, nada staged; branch/HEAD/main/seleção007 preservados.


## T026 — Correção focal da limpeza de perfis concedidos pelos testes — 2026-10-20

**Escopo: exclusivamente T026, dependente do diagnóstico T025.** Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `80dc6b1741d4624508610e8516471100745fc425`, seleção local Feature007 e índice vazio. Recebidos quatro rastreados modificados (quickstart/tasks e os dois HTTP de segurança) e quatro Java não rastreados, exatamente os caminhos do fechamento T025. `session-start.json` registra a lista completa e main em `b092a6849177a7d72c40c67f896f0b70a56e0f10`. Aplicada a skill `speckit-implement` somente neste escopo; lidas T026 integral, dependências e condições T025/T027, artefatos pertinentes e evidências F3, incluindo console, XML/TXT Surefire, diagnóstico e `final-review.json` recebidos. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007 absoluta; checklist requirements16/16, PASS documental. Ignores Java existentes suficientes; `.specify/extensions.yml` ausente, sem hooks.

### Causa inicial comprovada e consequências

A reanálise dos 16 XMLs F3 confirma 1.107 casos, 30 failures e dez errors. Os dez errors são `DataIntegrityViolationException`, SQLState23503/FK_USER_ACCOUNT_ROLE_ACCOUNT, no DELETE da conta em `AdminProductTestFixture.cleanUp`; os 30 failures primários estão em `preserveInheritedAccountsAndCleanOnlyEachUs3Fixture` ou `verifyOwnedCleanupAndStableContext/assertEmpty`, após o corpo de cada teste. TXT e console confirmam 13 failures/três errors por classe HTTP e quatro failures/quatro errors no JPA. Não é impedimento de compilação/inicialização, falha de infraestrutura externa ou RED funcional do endpoint.

Os testes T023 concedem/substituem perfis por JDBC em transação independente commitada. A [fixture de catálogo aprovada](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java) registra somente os pares conta/perfil originalmente criados em `owned.roles`; remove esses pares antes da conta. O cenário HTTP termina com a reconcessão CLIENT/ADMIN/ambos à conta originalmente sem perfil. No JPA, vazio→CLIENT, vazio→ADMIN e CLIENT→ADMIN também deixam pares diferentes dos originais. Esses pares novos não são removidos pela limpeza rastreada, que encontra a FK ao excluir a conta. A transação de limpeza do catálogo reverte, deixando contas/perfis/produtos/categorias/associações; pedidos/itens já foram removidos pela etapa própria da fixture007.

**Origem:** seis erros nos três casos HTTP de concessão por contexto, três nas transições JPA citadas e um na variante suja CLIENT→ADMIN. **Consequências:** 26 failures HTTP nos casos posteriores de precedência, alheio/inexistente e claims; quatro failures JPA nas demais invocações, pela permanência dos resíduos anteriores. Failures suprimidos nos dez errors são preservados e não somados novamente. A ordem recebida distingue as seis aprovações HTTP próprias anteriores à concessão dos cenários contaminados posteriores; variantes JPA que não deixam perfil extra também falharam por resíduo precedente. Não são 40 defeitos independentes da produção.

`diagnose-received.ps1`/`received-diagnosis.json` recontam os casos e conferem os stacks dos 40 problemas; exit0. Uma tentativa auxiliar anterior por comando PowerShell inline terminou em ParserError/chave ausente, exit1, antes de executar o corpo; corrigida somente a sintaxe do helper. Não foi teste da aplicação, nova execução RED ou alteração das evidências recebidas.

### Menor correção aplicada, restrita aos testes

Alterados nesta T026 somente dois fontes, além deste acréscimo documental e do marcador T026:

- [OwnOrderQuerySecurityHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java): `try/finally` no cenário de concessão/revogação/reconcessão; restaura somente a conta modificada ao conjunto original vazio antes de fechar sua fixture. A classe concreta API herda a mesma correção, sem editar seu arquivo.
- [OwnOrderQueryCurrentRolesIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java): `finally` nos dois métodos restaura o conjunto inicial da transição ou CLIENT no cenário de coleção suja. A restauração acontece após o término/rollback da transação externa e após conferir os perfis correntes commitados.

Reutilizadas as funções JDBC existentes, com DELETE/INSERT restritos ao ID da conta própria e confirmação por snapshot independente. `finally` também trata saída excepcional após a alteração. Restauração/cleanup ficam fora de todas as janelas de GET/HEAD e da consulta escalar observada. Os snapshots, SQL somente SELECT, coleções obsoletas/sujas, ausência de autoflush, rollback externo, estabilidade do H2 e verificações `@AfterEach` continuam intactos. Nenhum baseline é recalculado para aceitar resíduos. Não há limpeza global, mudança de ordem/cache/seed, recriação de contexto entre invocações ou ampliação da fixture compartilhada para apagar linhas não registradas.

**Correção produtiva da T026: não aplicação justificada.** A T025 não demonstrou desvio produtivo, e a correção focal autorizada pelo pedido resolve o ciclo de vida dos testes. Nenhum fonte `src/main`, entidade, repositório, serviço, filtro, handler, contrato ou política de ADMIN/outra operação foi editado. Não alterar produção para contornar a FK dos testes. Todas as asserções de titularidade/perfis/ausência de mutação recebidas foram mantidas; apenas os três blocos de restauração e a indentação necessária foram acrescentados.

### Validação focal executada uma vez

Java21.0.12.1 Azul Zulu confirmado por `java -version`; Wrapper Maven3.9.16 pelas propriedades existentes. Nenhum Java/Maven concorrente antes de iniciar. Executado sequencialmente na raiz, sem clean nem parâmetros de ordenação:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQuerySecurityHttpIntegrationTest,OwnOrderQuerySecurityApiHttpIntegrationTest,OwnOrderQueryCurrentRolesIntegrationTest' test
```

Seleção limitada às três classes afetadas por origem/consequências da T025, por inteiro; não é F3/T027, baseline ou suíte completa. Não repetir casos isolados com banco novo no lugar da sequência contaminante. Antes do Maven, os 32 arquivos recebidos em `target/surefire-reports/` foram movidos para o destino inédito `target/feature007-validation/t026-cleanup-20261020/received-surefire-reports/`; origem/destino absolutos conferidos dentro de target, disjuntos, sem sobrescrever, hashes e tamanhos preservados. A cópia histórica da T025 continua intacta em seu diretório próprio.

Runner `& 'target/feature007-validation/t026-cleanup-20261020/run.ps1'`: início `2026-10-20T20:17:57.8310946-03:00`, fim `2026-10-20T20:20:06.1295198-03:00`, duração externa128,298s; Maven02:03. **BUILD SUCCESS, exit0: 166 aprovados, zero failures/errors/skipped.** Compilados os115 fontes de teste com release21; compilar fontes não selecionados não conta como executá-los. Produção já compilada. Console e seis relatórios novos (três XML/três TXT), manifestos,41 fontes da revisão executada, horários/comando/diff/exit code preservados no diretório próprio ignorado. Sem outro Maven nesta T026.

| Classe concreta, prefixo com.devsuperior.dscommerce | Casos efetivos | Aprovados | Failures/errors/skipped | Não aprovados T025 agora aprovados |
| --- | ---: | ---: | --- | ---: |
| controllers.OwnOrderQuerySecurityApiHttpIntegrationTest |79|79|0/0/0|16|
| controllers.OwnOrderQuerySecurityHttpIntegrationTest |79|79|0/0/0|16|
| services.OwnOrderQueryCurrentRolesIntegrationTest |8|8|0/0/0|8|
| **Total** |**166**|**166**|**0/0/0**|**40**|

`audit.ps1`: exit0; seleção concreta/herança conferidas no fonte, 18 combinações classe/método,166 identidades testcase únicas, zero duplicatas, diferenças de métodos/classes, lacunas parametrizadas ou suites vazias. Nenhum `@Nested` ou agregador a somar. Console, atributos/nós XML e TXT concordam. `order-comparison.json` confirma exatamente os mesmos casos e a mesma sequência de invocações dentro de cada classe da T025; todos os40 casos antes não aprovados passaram.

`transport-and-order.ps1`: exit0; também confirmou mesma ordem relativa API→raiz→JPA do console F3, sem impor ordenação nova. Nos HTTP, concessão/revogação/reconcessão continua antes de precedência/alheio/claims; no JPA, as seis transições continuam antes das duas coleções sujas. Portanto o GREEN inclui a condição sequencial que antes propagava resíduos, com os mesmos hooks pós-teste, não apenas aprovação isolada dos erros iniciais. Três contextos H2 próprios/distintos e estáveis, OSIV false, sem seeds; URLs e portas efetivas registradas em `transport-console.json`/`transport-and-order.json`.

### Alcance da aprovação e limites preservados

158 invocações HTTP completas aprovadas:120 barreiras herdadas e38 cenários novos de titularidade/perfis,19 por contexto. São436 requisições reais:66 logins e370 GET/HEAD; contadores e observações por caso XML/console concordam, zero status divergente e zero dispatch MockMvc executado. Suporte MockMvc construído pela fixture não equivale a dispatch. As janelas de leitura conservam snapshots completos e SELECT-only; restauração JDBC explícita depois das asserções é cleanup de teste, não mutação pela consulta.

Os38 casos novos incluem próprios/count/detalhe com CLIENT, ADMIN e ambos; ID implementado primeiro200 sob seu dono real e404 sob alheio/inexistente, inclusive ADMIN; claims enganosos e precedência; concessão/revogação/reconcessão com o mesmo JWT em raiz/API. Essas são provas HTTP reais integralmente aprovadas **neste recorte focal**. Os controles herdados que aceitam200/404/405 continuam apenas prova da barreira; o antigo404 da T018 por endpoint ausente não se transforma em prova de titularidade.

As oito invocações JPA também passaram integralmente (seis de coleção obsoleta/duas de coleção suja), inclusive guarda/projeção escalar atual, commit independente, não autoflush e rollback externo, sem transação envolvendo HTTP. Não atribuir essa prova à autenticação JWT/HTTP nem confundir o recorte focal com todos os componentes F3. Falhas injetadas T024 não foram selecionadas ou reexecutadas: seus104 resultados na T025 permanecem históricos, com os limites de simulação já descritos, sem aprovação nova ou alcance maior sobre commit físico/serialização.

**O F3 da T025 continua reprovado: 1.107 casos,30 failures/dez errors,exit1.** Não foi reescrito, somado aos166 ou convertido em GREEN. A aprovação focal demonstra a correção do impedimento de limpeza na sequência afetada, sem consolidar US3/F3, regressões001–006 ou Feature007. T027 deverá consolidar o checkpoint conjunto na revisão corrigida conforme seu próprio escopo autorizado; não foi executada antecipadamente. Permanecem os limites de snapshots compostos, SQL JDBC fora do inspector, escrita transitória restaurada, revogação posterior à leitura e ausência de promessa de equivalência temporal404.

### Preservação e fechamento

Manifestos iniciais:299 rastreados, quatro Java não rastreados e2.101 arquivos históricos sob `target/feature007-validation/`, incluindo todo o F3 recebido. Cópias anteriores dos dois fontes alterados e dos dois documentos foram preservadas antes da edição. O fechamento compara296 rastreados fora dos três alterados nesta tarefa, os três Java não rastreados não editados, os41 fontes capturados da execução, os2.101 históricos,32 relatórios arquivados e seis novos nas duas localizações. A cópia recebida do quickstart é mantida como prefixo byte a byte; tasks muda somente T026. Diff integral, diferenças em relação aos fontes recebidos, arquivos novos, referências/links e whitespace revisados; resultado estático final em `final-review.json`.

**Somente T026 marcada nesta tarefa.** T001–T026 marcadas; T027–T034 abertas,oito pendências. Git mantém quatro rastreados modificados e quatro Java não rastreados nos mesmos caminhos recebidos, nada staged. Branch/HEAD/main/seleção007 preservados; nenhum stage, commit, push, CI ou execução T027+. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

Fechamento estático final: `& 'target/feature007-validation/t026-cleanup-20261020/final-review.ps1'`, exit0. `final-review.json` confirma os hashes acima,98 links locais válidos, prefixo integral recebido do quickstart e único marcador T026 em tasks. As52 linhas de asserções/chamadas de verificação HTTP e53 JPA têm conteúdo e ordem idênticos após remover apenas indentação; o diff confirma que argumentos, continuações, providers e verificações pós-teste foram preservados. `git diff --check` e `git diff --cached --exit-code`: exit0. Os quatro Java não rastreados conferidos individualmente por `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <caminho>` não emitiram diagnóstico (exit1 pela diferença de arquivo novo). Avisos nativos LF/CRLF não são falha de whitespace. São auditorias estáticas, sem novo Maven. Hooks pós-execução novamente ausentes. Estado final conferido: os mesmos quatro rastreados modificados e quatro Java não rastreados, nada staged; HEAD/branch/main/seleção007 preservados. Nenhum impedimento remanescente observado na seleção focal; aprovação conjunta F3/T027 continua pendente.


## T027 — Consolidação conjunta F3-GREEN na revisão corrigida — 2026-10-20

**Exclusivamente T027, dependente de T026; T028+ não executadas.** Conferidos inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `007-consultar-meus-pedidos`, HEAD `80dc6b1741d4624508610e8516471100745fc425`, seleção local Feature007 e índice vazio. Quatro rastreados modificados recebidos: `specs/007-consultar-meus-pedidos/quickstart.md`, `specs/007-consultar-meus-pedidos/tasks.md`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityApiHttpIntegrationTest.java`. Quatro Java não rastreados conferidos:

- `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryCurrentRolesIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/config/OwnOrderQueryFailureInjectionTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryFailureApiHttpIntegrationTest.java`

Skill `speckit-implement` limitada à tarefa; T027 integral, dependências T025/T026 e protocolo/seletor F3 revisados com os artefatos pertinentes já lidos e evidências recebidas. `check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, Feature007 absoluta; checklist requirements16/16, PASS documental. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignores Java existentes suficientes. Java21.0.12.1 Azul Zulu confirmado por `java -version`; Wrapper seleciona Maven3.9.16. Main preservada em `b092a6849177a7d72c40c67f896f0b70a56e0f10`.

### Necessidade da execução e preservação

O F3 da T025 foi reprovado (1.107 casos,30 failures/dez errors,exit1); a T026 corrigiu exclusivamente o cleanup de perfis nos testes e aprovou166 casos focais. Esses166 não equivalem à seleção conjunta; a condição de reutilizar um F3 já aprovado não se aplica. Executado agora F3 completo na mesma revisão corrigida de fontes da T026:41 fontes capturados/SHA-256 idênticos aos do focal, incluindo os três blocos `finally`, asserções e suporte. Nenhum fonte de teste ou produção foi alterado nesta T027.

Diretório próprio ignorado: `target/feature007-validation/t027-f3-green-20261020/`. Antes do Maven, os seis relatórios recebidos da T026 foram movidos de `target/surefire-reports/` para o destino inédito `received-surefire-reports/` nesse diretório. Origem/destino absolutos resolvidos dentro de target, disjuntos, sem sobrescrita; hashes/tamanhos conferidos após movimentação (`archive-preservation.json`: Count6/HashesMatch=true). As cópias próprias e todos os arquivos históricos do F3 reprovado e da validação focal permanecem intactos. Os32 relatórios novos (16 XML/16 TXT) estão em `reports/` e na área Surefire recriada; não somar arquivos arquivados ou resultados anteriores.

### Comando e resultado atuais

Executado uma vez, sequencialmente na raiz, sem clean, ordenação/cache/seed novos, baseline ou suíte completa; ausência de Java/Maven concorrente conferida antes de iniciar:

```powershell
.\mvnw.cmd '-Dtest=OwnOrderQueryList*,OwnOrderQueryDetail*,OwnOrderQuerySecurity*,OwnOrderQueryFailure*,OwnOrderQueryCurrentRolesIntegrationTest,OwnOrderQueryAccessServiceTest,OwnOrderQueryJwtAuthenticationConverterTest,OwnOrderQueryPageArgumentResolverTest,OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest' test
```

Runner `& 'target/feature007-validation/t027-f3-green-20261020/run.ps1'`: início `2026-10-20T20:25:58.4968884-03:00`, fim `2026-10-20T20:30:05.5584721-03:00`, duração externa247.062s; tempo Maven registrado no console. **BUILD SUCCESS, exit0:1.107 aprovados,zero falhas/erros/ignorados.** Comando idêntico ao F3 aprovado no procedimento; resultado desta execução, sem soma dos166 da T026. Console, ambiente, HEAD/diff, horários/exit code, manifestos e fontes da revisão executada preservados. Compilação/preparação não foram tomadas como aprovação; o resultado vem dos casos efetivamente executados.

| Classe concreta Surefire | Executados/aprovados | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| config.OwnOrderQuerySecurityConfigurationTest |58|0|0|0|
| controllers.OwnOrderQueryDetailApiHttpIntegrationTest |9|0|0|0|
| controllers.OwnOrderQueryDetailHttpIntegrationTest |9|0|0|0|
| controllers.OwnOrderQueryFailureApiHttpIntegrationTest |52|0|0|0|
| controllers.OwnOrderQueryFailureHttpIntegrationTest |52|0|0|0|
| controllers.OwnOrderQueryListApiHttpIntegrationTest |115|0|0|0|
| controllers.OwnOrderQueryListHttpIntegrationTest |115|0|0|0|
| controllers.OwnOrderQueryPageArgumentResolverTest |254|0|0|0|
| controllers.OwnOrderQuerySecurityApiHttpIntegrationTest |79|0|0|0|
| controllers.OwnOrderQuerySecurityHttpIntegrationTest |79|0|0|0|
| dto.OwnOrderQueryDtoContractTest |119|0|0|0|
| repositories.OwnOrderQueryRepositoryTest |33|0|0|0|
| security.OwnOrderQueryJwtAuthenticationConverterTest |26|0|0|0|
| services.OwnOrderQueryAccessServiceTest |16|0|0|0|
| services.OwnOrderQueryCurrentRolesIntegrationTest |8|0|0|0|
| services.OwnOrderQueryServiceTest |83|0|0|0|
| **Total** |**1107**|**0**|**0**|**0**|

Prefixo das classes: `com.devsuperior.dscommerce`. `audit.ps1`: exit0;16 classes concretas,157 combinações classe/método,1.107 identidades testcase únicas. Console, atributos/nós XML e TXT concordam; zero omissões de classes/métodos, lacunas parametrizadas, duplicatas ou suites vazias. Herança HTTP resolvida no fonte; nenhum `@Nested` ou agregador a somar. `OwnOrderQuerySecurityConfigurationTest` é selecionado pelo wildcard Security*, não é HTTP/JPA. Fontes apenas compilados e suportes abstratos não contam como testes adicionais.

`consolidate.ps1`: exit0; mesmas1.107 identidades e mesma ordem de invocações dentro de cada classe da T025, todos os40 casos antes não aprovados agora passaram; todos os166 do focal também foram incluídos/aprovados no conjunto atual. Sem reduzir a seleção nem isolar os casos de cleanup dos seus sucessores. `consolidation.json` registra F3GreenApproved=true/US3HttpCheckpointApproved=true/Feature007Complete=false.

### Provas HTTP atuais e ausência de resíduos

Oito classes HTTP concretas,510 invocações JUnit completas aprovadas:230 de lista,18 de detalhe,158 de segurança e104 de falhas. **974 requisições HTTP reais:200 logins e774 consultas/ensaios (588 GET,182 HEAD,quatro PUT)**. Observações por caso XML e console e contadores concordam; zero status divergente. Separadamente,217 dispatches MockMvc do resolver; não são HTTP de rede ou testes JUnit adicionais. O suporte MockMvc montado pela fixture não é login interno executado pelas oito classes.

Nos158 de segurança,120 barreiras herdadas e38 novos cenários (19 por contexto) passaram em raiz e `/api`: CLIENT, ADMIN isolado e ambos só obtêm conteúdo/count/detalhe próprios; ID implementado200 sob o titular real e404 sob outra conta/inexistente, inclusive ADMIN; claims/parâmetros enganosos não ampliam acesso; concessão/revogação/reconcessão commitadas alteram o acesso sob o mesmo JWT. Precedência401/403 sobre entrada inválida, mensagens fixas, path sem query, challenge Bearer no401 e HEAD vazio preservados. Controles antigos que aceitam200/404/405 continuam prova da barreira, não substituem as asserções estritas de titularidade; o404 da T018 por rota ausente continua sem valor probatório de ownership.

Os testes e hooks pós-teste mantêm snapshots/SQL somente SELECT nas janelas observadas. Restaurações dos perfis em `finally` ocorrem depois das asserções e, no JPA, depois do rollback externo, antes da limpeza rastreada. Não houve erro de FK nem failure por estado residual nessa execução conjunta; todos os casos posteriores à concessão passaram com as verificações de isolamento existentes. Onze contextos de aplicação H2 próprios/distintos/estáveis (oito HTTP, repositório, serviço e JPA de perfis), OSIV false e sem seeds; URLs/portas em `transport-console.json`/`consolidation.json`. Nenhum contexto foi recriado entre invocações para ocultar resíduos.

Listagem/detalhe conservaram conteúdo, totais, ordem, representações, histórico financeiro e controles anteriores no recorte F3. Nenhuma política de ADMIN, POST, domínio, contrato ou fluxo anterior foi ampliada. Isso não substitui as jornadas US4/regressões001–006 ainda reservadas às próximas tarefas.

### JPA/serviço e alcance das falhas simuladas

**JPA de perfis:** oito casos completos aprovados, seis de coleção gerenciada obsoleta e dois de coleção suja; projeção escalar/perfis atuais, commit independente, ausência de autoflush e rollback externo, sem HTTP dentro da transação. **Repositório:**33 casos reais aprovados. **Serviço:**83 aprovados,16 com JPA/guarda reais e67 com colaboradores simulados locais. Os16 testes de acesso,26 do conversor,58 de configuração,119 de DTO e254 do resolver são provas complementares em seus próprios contextos; não atribuir aos mocks/slices autenticação ou persistência HTTP reais. Há H2 independente adicional no controle transacional do serviço de acesso, sem contexto JPA.

**Injeções T024:**104 casos HTTP reais aprovados,52 por contexto, com60 respostas500 (30 GET/30 HEAD). Exercitados lookup inicial, escalar do filtro/tardio, IDs/count/fetch, cálculo e mapeamento; count realmente delegado ao JDBC quando exigido. Erro fixo seguro, nunca vazio/404/parcial/401, path sem query e HEAD vazio. COMPLETION atinge beforeCommit após o corpo/DTO, antes do retorno do proxy, com rollback. Isso não comprova falha física de commit já delegado/confirmado pelo driver, nem todas as falhas possíveis de serialização/transporte. Logs SQL controladamente injetados em nível ERROR não são falhas JUnit.

Controles NONE com200/DTO completo;16 perdas tardias de conta/perfil com401/Bearer ou403, sem fetch; oito controles400 antes do serviço e12 controles nativos405/Allow ou406 passaram. A remoção JDBC intencional nos casos tardios é preparação independente: o snapshot de referência do restante da requisição é o estado após essa mutação, não uma prova de ausência de DML no GET inteiro. Nas demais janelas, snapshots completos/SELECT-only permaneceram. Mantidos os limites de snapshots compostos não atômicos, inspector que não vê JDBC direto e escrita transitória restaurada; sem promessa de serialização de revogação depois da leitura autorizadora, equivalência temporal404 ou orçamento SQL global HTTP deduzido dos componentes.

### Fechamento e limites

F3-GREEN consolidado pela execução atual; o F3 reprovado da T025 permanece histórico com exit1 e a T026 permanece focal com166/exit0, sem reclassificação retroativa. Nenhuma falha funcional, de preparação/compilação ou infraestrutura impediu esta seleção; nenhum ajuste produtivo ou de teste foi necessário. Alterados nesta tarefa somente este acréscimo e o marcador T027 em tasks. **Somente T027 marcada: T001–T027 marcadas; T028–T034 abertas,sete pendências.** US4/FINAL/revisões posteriores e conclusão da Feature007 continuam pendentes. Feature005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito no Gate IV.

Preservação final:297 rastreados fora dos dois documentos, quatro Java recebidos,41 fontes capturados,2.220 arquivos históricos, seis relatórios arquivados e32 novos nas duas localizações conferidos por hash. Prefixo integral recebido do quickstart mantido byte a byte; tasks muda somente T027. Diff/arquivos novos, referências/links e whitespace revisados; registro estático final em `final-review.json`. Git mantém os quatro rastreados modificados e quatro Java não rastreados recebidos, nada staged, HEAD/branch/main/seleção007 preservados. Sem stage, commit, push, CI, baseline, suíte completa ou execução T028+.

Fechamento estático: `& 'target/feature007-validation/t027-f3-green-20261020/final-review.ps1'`, exit0. `final-review.json` confirma todos os hashes acima,98 links locais válidos, prefixo recebido do quickstart intacto e tasks alterado somente em T027. `git diff --check` e `git diff --cached --exit-code`: exit0; os quatro Java não rastreados conferidos individualmente com `git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --check -- /dev/null <caminho>` não emitiram diagnóstico (exit1 pela diferença de arquivo novo). Avisos Git de futura conversão LF/CRLF não são erros de whitespace. Diff documental e fontes/arquivos novos preservados revisados; auditorias estáticas não são outro Maven. Hooks pós-execução novamente ausentes. Estado final: quatro rastreados modificados, quatro Java não rastreados nos caminhos iniciais, nada staged; HEAD/branch/main/seleção007 preservados. Maven total04:01; sem impedimento remanescente neste checkpoint, sem concluir Feature007 ou executar T028+.
