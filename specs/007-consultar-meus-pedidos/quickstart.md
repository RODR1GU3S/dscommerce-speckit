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
