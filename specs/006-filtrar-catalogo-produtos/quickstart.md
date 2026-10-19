# Quickstart: validação planejada do filtro público por nome

**Estado deste documento**: guia de verificações futuras da Feature 006. **Nenhum comando Maven, teste, servidor ou chamada HTTP descritos abaixo foi executado neste planejamento.** Não é relatório GREEN nem entrega funcional. [Spec](spec.md) | [Plano](plan.md) | [Modelo](data-model.md) | [Contrato](contracts/public-products-openapi.yaml)

## Base, pré-requisitos e preparação futura

- Raiz: `C:/Users/ronal/dscommerce-speckit`; branch `006-filtrar-catalogo-produtos`; base de planejamento `c3caf26eb9936aea379908118ae44b1201ff1614`. Conferir Git antes de executar e registrar o HEAD/working tree efetivamente testados; implementação futura terá outra revisão.
- JDK 21 instalado e Maven Wrapper existente, que seleciona Maven 3.9.16. Rede pode ser necessária no primeiro download; não trocar pelo Maven global. Os comandos abaixo são para PowerShell na raiz; em Linux usar `./mvnw` com os mesmos argumentos.
- Nome proposto do grupo HTTP novo: `PublicCatalogNameFilterIntegrationTest`; **a classe ainda não existe**. Não executar seleção desse nome como se já fosse uma prova disponível. Criá-la na fase autorizada de implementação com os cenários abaixo e sem depender da nova assinatura interna para obter RED.
- Usar contexto Spring completo com filtros reais, perfil `test` e ativação explícita de `AdminProductTestConfig` por `@ContextConfiguration(initializers = AdminProductTestConfig.class)`. O initializer existente não é bean nem deve ser importado como configuração comum.
- O initializer gera URL H2 e chave JWT descartáveis por contexto, `create-drop`, `spring.sql.init.mode=never` e `hibernate.hbm2ddl.import_files` vazio. Reaproveitar esse suporte sem trocar `application-test.properties` global, cujo contexto histórico possui 52 seeds.
- Nos cenários focais, desativar OSIV por propriedade do teste, sem mudar produção. Usar MockMvc com filtros reais para a matriz e HTTP servlet real `RANDOM_PORT` para a fronteira/context path, conversão não numérica e preservação dos erros nativos. Não tratar um dispatch MockMvc como prova do fluxo servlet `/error`.
- Contextos HTTP reais em raiz e `/api` devem ser distintos e declarar suas propriedades antes da criação. Reaproveitar a configuração existente e dar identidade explícita ao contexto focal (por exemplo, propriedade de teste da 006), evitando reaproveitar involuntariamente outro contexto com estado. Não contaminar contexto público sem filtro, MVC slice ou banco dos testes anteriores.

Preparação de execução futura, sem Maven neste checkpoint:

```powershell
Set-Location -LiteralPath C:/Users/ronal/dscommerce-speckit
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git status --short --untracked-files=all
```

## Fixtures determinísticas e oráculos

Reaproveitar [AdminProductTestFixture](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java) e [AdminProductDatabaseProbe](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductDatabaseProbe.java). Os nomes administrativos desses suportes não alteram o endpoint público ou sua segurança; não é necessário um segundo framework de fixture.

| Grupo | Preparação conhecida | Oráculo independente |
| --- | --- | --- |
| Trecho e caixa | Fone Azul, Telefone Preto, Cabo | IDs capturados das inserções; somente os dois primeiros para `fone`. |
| Acentos | Café Especial, Cafe Claro | `CAFÉ` seleciona o primeiro; `cafe` o segundo. |
| Literais | Oferta 10%, Oferta 100, Kit_A, KitXA | `10%` e `kit_` selecionam seus nomes literais, sem ampliar resultado. Acrescentar controles de barra/apóstrofo ao reaproveitamento administrativo quando pertinente. |
| Espaços | Fone Azul, Fone com dois espaços internos Azul, nome com espaços externos e dois internos, Fone com NBSP interno Azul | Manifestos separados por cenário para impedir que a fixture altere a seleção esperada; guardar strings exatas. |
| Comprimento/legado | Nomes com 80 e 81 letras `a`, nome curto, preço zero e produto sem categorias | Criar via suporte JDBC commitado já existente; não enfraquecer API de escrita para preparar legado. Filtro de 81 `A` seleciona somente o nome de 81. |
| Navegação V03 | `prepareV03()`: seis produtos; cinco contêm `fone`; Fone B repetido com IDs distintos; Fone A em duas categorias | Usar `filteredOrder`/`unfilteredOrder` explícitos do manifesto, sem executar consulta de produção como oráculo. Cinco correspondências em 2/2/1, empate por ID e produto não duplicado por categorias. |
| Detalhes e outras jornadas | Categorias distintas com mesmo nome, produto excluído da busca mas existente, contas de teste e pedido controlado | ID capturado, nomes públicos deduplicados e dados/valores históricos conhecidos. Reaproveitar as regressões existentes para escritas, login e pedidos. |

Não fixar IDs absolutos dependentes de sequences ou ordem global dos testes. Preparar dados com transação própria commitada antes de HTTP/proxy. Não envolver chamada pública em `@Transactional` externo. Após cada operação, observar resposta e ler snapshot independente **antes** de limpar. Comparar produtos, categorias, pares, pedidos e itens; contas e perfis com leituras adicionais do probe quando presentes. Limpar somente os IDs de propriedade da fixture na ordem referencial e conferir isolamento antes/depois.

Para provar ausência de mutações, combinar snapshots iguais e observação SQL Hibernate por contexto com zero DML na janela de GET, excluindo preparação/limpeza. Reaproveitar `AdminProductDatabaseProbe.SqlObservation`, registrando o inspector somente no contexto focal, conforme o exemplo existente de `HibernatePropertiesCustomizer` em `AdminProductQueryIntegrationTest`. Não exigir número fixo de SELECT/count: o Spring Data pode otimizar contagem em página parcial. A observação não prova ausência universal de JDBC direto, efeitos assíncronos ou durabilidade; snapshots não substituem o controle DML. Não criar alterações globais de Logback: H2 isolado não isola LoggerContext da JVM.

## Matriz dos 27 cenários a validar

Cada linha representa um cenário da spec. Variantes por token, contexto, tamanho ou espaços podem produzir vários casos de teste; a contagem de cenários continua 27. Asserts devem verificar valores/IDs esperados, campos/tipos e totais, sem obter o esperado do método sob teste.

| Cenário | Verificação futura e resultado esperado | Cobertura |
| --- | --- | --- |
| US1.1 | `fone` encontra Fone Azul e Telefone Preto, exclui Cabo. | FR-002; SC-001 |
| US1.2 | `fone`, `FONE`, `FoNe` têm mesmos IDs, ordem e totais. | FR-002; SC-001 |
| US1.3 | `CAFÉ` e `cafe` distinguem Café Especial/Cafe Claro. | FR-002; SC-001 |
| US1.4 | `10%` não corresponde a Oferta 100. Enviar percent corretamente codificado no HTTP real. | FR-003; SC-001 |
| US1.5 | `kit_` não corresponde a KitXA. | FR-003; SC-001 |
| US1.6 | `f` é aceito e encontra Fone Azul. | FR-013; SC-001 |
| US1.7 | 81 letras `A` encontram somente o nome de 81 `a`, sem rejeitar/truncar. | FR-013; SC-001 |
| US2.1 | ` FONE ` equivale a `FONE`. | FR-004; SC-002 |
| US2.2 | Omitido, vazio e somente espaços externos equivalem à mesma página sem filtro. | FR-004–005; SC-002 |
| US2.3 | Um e dois espaços internos selecionam somente suas formas. | FR-004; SC-002 |
| US2.4 | Aparar filtro, não nome legado com espaços externos; resposta conserva texto exato e snapshot igual. | FR-006; SC-002 |
| US2.5 | Tabulação, NBSP e U+2003 externos, isolados e combinados: remover só extremidades; somente espaços equivale a omitido. | FR-004–005; SC-002 |
| US2.6 | Espaço comum e NBSP internos não são intercambiáveis. | FR-004; SC-002 |
| US3.1 | V03: páginas 0/1/2 size2 contêm 2/2/1, totais 5/3; união exata sem duplicação. | FR-005/007; SC-003 |
| US3.2 | V03: nome armazenado crescente e empate Fone B por ID, inclusive entre páginas. | FR-008; SC-003 |
| US3.3 | Sem correspondências: 200, vazio e totais zero; page/size conservados inclusive page não zero. | FR-009; SC-004 |
| US3.4 | V03 page3 size2: vazio, totais 5/3, sem clamp. | FR-007/009; SC-004 |
| US3.5 | Banco sem produtos: com/sem filtro, vazio e totais zero. | FR-009; SC-004 |
| US3.6 | Filtrar e omitir depois: segunda consulta corresponde ao catálogo completo, sem estado de busca. | FR-005; SC-002/005 |
| US4.1 | Com/sem filtro e filtro vazio: defaults 0/12; manter também default público de paginação vazia, sem parser administrativo. | FR-005/010; SC-005 |
| US4.2 | Com/sem name: size1/50 aceitos; page-1, size0/51, texto não numérico recusados como antes; preservar status/corpo conforme mecanismo aplicável. | FR-010; SC-005 |
| US4.3 | Chaves exatas dos quatro campos do item e cinco da página, tipos e preço numérico. | FR-011; SC-005 |
| US4.4 | Sem token, token inválido irrelevante e consumidor com token válido: mesmo filtro/detalhe público; administração continua protegida. | FR-001/012; SC-005/006 |
| US4.5 | Produto não correspondente continua acessível em detalhe por seu ID. | FR-012; SC-006 |
| US4.6 | Detalhe com categorias de nomes repetidos mantém nomes únicos; ID inexistente mantém 404/erro atual. | FR-012; SC-006 |
| US4.7 | Legado curto/longo/espaçado, preço zero/sem categorias legível sem correção; resposta exata e snapshot sem mutação. | FR-006/013; SC-006 |
| US4.8 | Consultas admin, login/pedido/histórico preservados; GET não escreve nem concede ADMIN. | FR-014; SC-006 |

Provas integradas do fluxo público são necessárias para todos os comportamentos centrais. Unitários de serviço comprovam filtro normalizado/seleção do ramo e mapeamento; repository real comprova substring/contagem/ordem. Mocks não substituem essas provas. Os casos HTTP devem conferir o mecanismo público de segurança real.

## Sequência futura: caracterização, RED e GREEN

1. Antes da alteração produtiva, caracterizar com HTTP real os erros de conversão de paginação, com `Accept: application/json`, com e sem `name`, em raiz e `/api`. Guardar status, campos/corpo e cabeçalhos relevantes sem confundir erro nativo com Bean Validation. O envelope dos limites numéricos segue os quatro campos existentes; não supor o mesmo para conversão nativa.
2. Escrever primeiro cenários HTTP usando a rota já existente. Confirmar RED por seleção incorreta: por exemplo, `name=fone` retorna seis em vez de cinco na V03 ou total sem restrição. Os testes devem compilar; falha de import, assinatura, contexto, seed ou autenticação não demonstra comportamento ausente.
3. Executar a implementação futura conforme plano; adaptar assinaturas técnicas e as duas expectativas intencionalmente substituídas da pesquisa R6. Não apagar os testes ou alterar requisitos para obter GREEN.
4. Obter GREEN focal dos 27 cenários e das expectativas atualizadas; executar regressões das consultas compartilhadas, detalhe/segurança e jornadas proporcionais ao impacto. Encerrar a implementação somente após suíte completa exigida abaixo e revisão do diff.
5. Reavaliar gates próprios da 006 com código e evidências. Gate IV da 005 continua EXCEÇÃO DOCUMENTADA, sem PASS estrito; não alterar seu registro histórico.

Comandos **sugeridos para a fase futura**, ainda não executados:

```powershell
# RED funcional antes da implementação; classe HTTP deverá existir e compilar.
.\mvnw.cmd '-Dtest=PublicCatalogNameFilterIntegrationTest' '-Dsurefire.reportNameSuffix=feature006-red' test

# GREEN focal: 27 cenários, assinaturas e expectativas anteriores atualizadas.
.\mvnw.cmd '-Dtest=PublicCatalogNameFilterIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,AdminProductQueryIntegrationTest,AdminProductPublicRegressionIntegrationTest' '-Dsurefire.reportNameSuffix=feature006-focused' test

# Regressões do normalizador/consulta compartilhados, detalhes e jornadas públicas.
.\mvnw.cmd '-Dtest=AdminProductRepositoryTest,ProductRepositoryAdminQueryComponentTest,AdminQueryServicesTest,AdminQueryServiceComponentIntegrationTest,ProductRepositoryTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,AdminProductGetHttpSecurityTest,GlobalExceptionHandlerRegressionTest,OrderPublicRegressionIntegrationTest' '-Dsurefire.reportNameSuffix=feature006-regression' test

# Gate funcional final futuro: suíte completa, sem filtros ou skips.
.\mvnw.cmd test
```

A suíte completa também reexercita os testes existentes de login, pedidos, escrita administrativa e histórico, sem inventar testes novos para todos os caminhos não modificados. Se o alcance real mudar ou aparecer interferência, ampliar a seleção proporcionalmente e registrar a razão. Nenhum resultado local substitui nova CI no HEAD efetivamente proposto.

## Evidências que deverão ser registradas

Registrar por execução futura: comando literal, horário, raiz/branch/HEAD e alterações, seleção efetiva, total/falhas/erros/ignorados, exit code, relatórios Surefire e observações HTTP/SQL/snapshots. RED deve registrar assertion e causa esperada; GREEN e suíte devem corresponder à revisão final. Separar erro de preparação, falha funcional e resultado histórico.

Os 2.614 testes anteriores citados no pedido não foram reexecutados, recontados ou vinculados a esta implementação neste checkpoint. Não aprovam a Feature 006. Documentos históricos 001–005 permanecem intactos. H2 em memória, observadores de SQL e snapshots têm os limites descritos na pesquisa; não demonstram outros bancos, SLA, durabilidade ou ausência universal de efeitos fora da janela observada.

## Evidência atual deste checkpoint de planejamento

- Conferidos raiz, branch real `006-filtrar-catalogo-produtos`, HEAD `c3caf26eb9936aea379908118ae44b1201ff1614`, Git inicialmente limpo e índice sem staged.
- Lidas integralmente spec/checklist e constituição, resolvido template de plano pelo setup com Feature 006 explícita; ponteiro local anterior restaurado sem mudança de conteúdo. `.specify/extensions.yml` ausente na verificação inicial, sem hook prévio aplicável.
- Inspecionados implementação, testes e suportes existentes; consultadas fontes primárias indicadas na pesquisa. Decisões, modelo, contrato planejado e matriz dos 27 cenários escritos.
- Revisão documental executada: parsing YAML com PyYAML já instalado no ambiente do Specify CLI, rejeitando chaves duplicadas; quatro referências internas do contrato resolvidas; campos e tipos das representações comparados ao contrato histórico 001; exemplos e metadados conferidos. A matriz contém os 27 cenários da spec uma única vez, e o contrato referencia os 14 FR. Links locais e âncoras resolvidos. Essa checagem de consistência não equivale a validação integral por ferramenta de schema OpenAPI, que não foi executada.
- `git diff --check` executado sem diagnósticos; os cinco arquivos novos foram também examinados individualmente por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`, sem diagnósticos de whitespace (exit 1 indica os arquivos novos/diferenças). Nenhum teste funcional ou Maven executado. O estado final do Git é informado na entrega; a lista de procedimentos futuros acima não é evidência de execução.

## Revisão para registro do planejamento

Revisão documental em 2026-10-18, na branch `006-filtrar-catalogo-produtos`, base `c3caf26eb9936aea379908118ae44b1201ff1614`. Estado inicial conferido: somente os cinco artefatos de planejamento não rastreados, nenhum staged ou arquivo rastreado modificado; `main` em `20495b487379e260a4c967823997bff872920dc9`.

Os cinco artefatos foram lidos integralmente e confrontados com a spec e os componentes existentes. Confirmados os 27 cenários, os 14 FR, a normalização pura compartilhada somente entre consultas e a preservação da guarda/validação administrativa. As duas expectativas antigas de `name` ignorado permanecem identificadas para substituição futura, sem alterar testes ou registros históricos. Gates continuam sendo avaliação de desenho; execução funcional permanece futura.

Correção documental: a descrição do schema aberto de erro nativo explicita que a união `anyOf` não impõe, sozinha, os quatro campos aos erros de limites. Esses casos exigem validação direta contra `ErrorResponse`. Isso corrige a explicação do alcance do schema, sem mudar o tratamento de erros, os requisitos ou a implementação planejada.

Validação integral do documento OpenAPI 3.0 executada nesta revisão com `openapi-spec-validator 0.9.0`, em ambiente isolado de `uvx`, fora das dependências do projeto:

```powershell
& 'C:/Users/ronal/.local/bin/uvx.exe' --from 'openapi-spec-validator==0.9.0' openapi-spec-validator --validation-errors all 'C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos/contracts/public-products-openapi.yaml'
```

Resultado: `OK`, exit 0. Depois da correção documental, a API `OpenAPIV30SpecValidator.iter_errors()` confirmou zero erros; `openapi-schema-validator 0.9.0` conferiu os quatro schemas de componentes e os dez exemplos de parâmetros/respostas, incluindo `invalidSize` diretamente contra `ErrorResponse`. As quatro referências internas foram resolvidas. Também conferidos: chaves YAML sem duplicação, campos/tipos versus contrato histórico 001, metadados dos exemplos, ausência de restrições de tamanho no filtro, matriz com os 27 cenários uma única vez, referência aos 14 FR, links locais e âncoras.

A validação integral acima é do documento OpenAPI e de seus schemas/exemplos; não demonstra implementação, resposta real do serviço ou o envelope nativo ainda sujeito à caracterização HTTP futura. O registro inicial de validação apenas por parsing permanece histórico. Nenhuma dependência do projeto foi alterada, e nenhum Maven, teste de aplicação ou chamada HTTP do sistema foi executado nesta revisão.

## Execução da T001 — contexto e escopo confirmados

**Data**: 2026-10-18, America/Sao_Paulo. Referência horária observada por `Get-Date -Format o`: `2026-10-18T18:08:52.0939072-03:00`. **Revisão conferida**: `ae009756da296f85712595e078e4ae1738821478`, branch real `006-filtrar-catalogo-produtos`, raiz `C:/Users/ronal/dscommerce-speckit`. Execução autorizada exclusivamente de T001, sem dependências; esta seção acrescenta evidência documental atual aos registros históricos acima.

### Comandos e resultados atuais

| Comando/verificação executada | Resultado observado |
| --- | --- |
| `git rev-parse --show-toplevel`; `git branch --show-current`; `git rev-parse HEAD` | Raiz, branch e HEAD iguais aos solicitados acima. |
| `git status --short`; `git status --short --untracked-files=all`; `git diff --cached --name-only` | Sem saída no início: árvore limpa, nenhum arquivo não rastreado e índice sem staged. A conferência repetida antes da edição documental continuou limpa. |
| `git rev-parse main`; `git log -3 --format='%H %s'` | `main` em `20495b487379e260a4c967823997bff872920dc9`; HEAD das tarefas `ae009756da296f85712595e078e4ae1738821478`, precedido pelo planejamento `d2f76b0edaeebf070b861dc38038fd42d0e83a62` e especificação `c3caf26eb9936aea379908118ae44b1201ff1614`. |
| `Get-Content -Raw AGENTS.md`; leitura da skill `.agents/skills/speckit-implement/SKILL.md` e de `tasks.md` por `Get-Content` | Instruções e tarefa integral lidas; T001 exige orientação, preservação, distinção de evidências e registro neste quickstart. T002–T030 não integram esta autorização. |
| `Test-Path .specify/extensions.yml` | `False` nas conferências anterior e posterior à leitura: sem hooks `before_implement`/`after_implement` a executar. |
| `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` | Exit 0; `FEATURE_DIR=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos`; `AVAILABLE_DOCS`: `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, `tasks.md`. JSON convertido com `ConvertFrom-Json` e diretório conferido. |
| Leitura e contagem de marcadores de todos os arquivos de `checklists/` por `Get-ChildItem`/`Select-String` | Somente `requirements.md`: 16 itens, 16 marcados, zero abertos; PASS de qualidade documental. Checklist mantido sem alterações. |
| `Get-Content -Raw` dos artefatos indicados abaixo e da constituição | Fontes disponíveis e lidas; nenhum conflito material que impeça concluir a orientação da T001. |
| `git rev-parse --git-dir`; leitura de `.gitignore`; `git check-ignore target/t001-inspection.class build/t001-inspection.class t001-inspection.class t001-inspection.jar .gradle/t001-inspection` | Repositório `.git`; todos os cinco caminhos hipotéticos ignorados, sem criá-los. Padrões Java/Maven e universais já presentes; nenhuma alteração de ignore necessária. Inspeção de arquivos de configuração na raiz e caminhos rastreados não identificou Docker/Terraform/Helm ou configuração Node/ESLint/Prettier que exigisse outro ignore neste escopo. |

O script de pré-requisitos foi lido antes de executar: seu resolvedor pode persistir a seleção. O ponteiro `.specify/feature.json` indicava a Feature 005 (`specs/005-manter-produtos`); por isso foi usado `SPECIFY_FEATURE_DIRECTORY` com o caminho absoluto da 006 somente no processo da conferência. Os bytes anteriores foram guardados em memória e restaurados em `finally`, assim como o valor anterior da variável. `Get-FileHash -Algorithm SHA256` confirmou o mesmo hash antes/depois: `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. O ponteiro não foi usado como prova da branch real.

Limitação de ferramenta observada: `Get-Command pwsh` informou comando não encontrado. A conferência foi concluída com o Windows PowerShell disponível (`$PSVersionTable.PSVersion.ToString()` → `5.1.19041.7663`), sem instalar ferramenta ou mudar configuração. Isso não bloqueou T001 e não verifica os pré-requisitos de execução Java/Maven da T002.

### Fontes, escopo preservado e critérios de conclusão

Consultados integralmente [spec](spec.md), [plan](plan.md), [research](research.md), [data-model](data-model.md), [contrato](contracts/public-products-openapi.yaml), [checklist](checklists/requirements.md), este quickstart, [tasks](tasks.md), [constituição](../../.specify/memory/constitution.md) e `AGENTS.md`.

- O escopo aprovado permanece a extensão pública de `GET /products` com `name` opcional por trecho literal, caixa ignorada e acentos significativos; `%`/`_` literais. Somente os espaços externos do filtro são removidos; espaços internos e nomes armazenados/devolvidos são preservados. Ausência/vazio mantém a consulta sem filtro. Paginação, contagem e ordem `name ASC/id ASC` descrevem o conjunto filtrado, inclusive páginas vazias, preservando resposta, padrões, validações, acesso e detalhes por ID.
- A evolução intencional substitui somente a expectativa anterior de `name` ignorado. As duas expectativas existentes continuam reservadas à T011; contratos e evidências históricos das Features 001–005 permanecem intactos. Não se acrescentam limites, coerções, equivalência Unicode canônica ou regras administrativas de parâmetros ao público.
- Os gates próprios da 006 continuam PASS **de desenho**, conforme o plano aprovado; não foram convertidos em aprovação funcional. A exceção documentada do Gate IV da 005 permanece histórica e não é herdada pela 006. O checklist 16/16 e a validação OpenAPI já registrada são evidências documentais; o validador OpenAPI não foi reexecutado nesta tarefa.
- Os 2.614 testes mencionados anteriormente permanecem evidência histórica. Nenhum teste, Maven, servidor, chamada HTTP ou baseline foi executado agora; também não se criaram fixtures, helper, refatoração ou filtro. Relatórios existentes não foram regenerados, removidos ou tratados como execução atual. Compilação, JDK, seleções de teste, comportamento HTTP, SQL e snapshots não foram validados nesta tarefa.

**Conclusão da T001**: contexto correto, fontes e limites conferidos, árvore inicialmente limpa e ausência de impedimento documental confirmados. Somente T001 é marcada `[X]`; T002–T030 permanecem abertas, sem início do baseline. Alterações desta execução limitadas ao acréscimo desta seção e ao marcador de T001 em `tasks.md`; sem stage, commit, push, troca de branch ou alteração de `main`. A conferência final documental e o estado do Git serão informados na entrega desta tarefa.

Conferências documentais de fechamento executadas: `git diff --no-color -- specs/006-filtrar-catalogo-produtos/tasks.md specs/006-filtrar-catalogo-produtos/quickstart.md` revisado integralmente; `git diff --check` com exit 0, sem erros de whitespace. Verificação em memória por `$documentCheck | & 'C:/Users/ronal/AppData/Roaming/uv/tools/specify-cli/Scripts/python.exe' -B -` confirmou apenas T001 marcada, demais 29 abertas, conteúdo anterior do quickstart preservado, links locais existentes, ponteiro íntegro e somente os dois documentos modificados. Não é teste de aplicação. `Test-Path .specify/extensions.yml` permaneceu `False` após a execução.

Estado observado por `git status --short --untracked-files=all`: somente ` M specs/006-filtrar-catalogo-produtos/quickstart.md` e ` M specs/006-filtrar-catalogo-produtos/tasks.md`; `git diff --cached --name-only` sem saída. Branch/HEAD e `main` mantiveram os valores acima; nenhum impedimento remanescente para T001.

## Execução da T002 — baseline focal anterior à mudança

**Contexto conferido em 2026-10-18**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `ae009756da296f85712595e078e4ae1738821478`. `git status --short --untracked-files=all` mostrou somente os dois documentos desta feature modificados pela T001; `git diff --cached --name-only` sem saída. T001 estava `[X]`, com seu registro preservado integralmente. Nenhuma edição em produção ou testes antes ou durante esta baseline.

### Seleção, ambiente e preservação

- Repetida a conferência `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` somente no processo da conferência: exit 0, `FEATURE_DIR` da 006 e os cinco documentos opcionais esperados. Ponteiro anterior e variável restaurados pelo procedimento da T001; SHA-256 do ponteiro permaneceu `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido: 16/16, zero abertos. `.specify/extensions.yml` ausente antes e depois, sem hooks aplicáveis.
- Ambiente observado: Windows 10 amd64 (`10.0.19045.0`), Windows PowerShell `5.1.19041.7663`; `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, OpenJDK Zulu `21.0.12.1+1-LTS`, fornecedor Azul Systems. `.\mvnw.cmd -version` confirmou Maven `3.9.16`, locale `pt_BR` e encoding UTF-8; saída preservada em `maven-version.txt`. A checagem isolada `java -version` escreve a versão em stderr, apresentada pelo PowerShell como `NativeCommandError`; o Wrapper confirmou o JDK e executou os testes com exit 0, sem impedimento de ambiente.
- Antes de executar Maven, os **192 arquivos anteriores** de `target/surefire-reports/` foram copiados com `Copy-Item -Recurse` para [surefire-before](../../target/feature006-t002-baseline-20261018T181411/surefire-before/). Comprimento e SHA-256 por arquivo estão no [manifesto anterior](../../target/feature006-t002-baseline-20261018T181411/surefire-before-manifest.json). Os hashes da cópia foram conferidos antes da execução; depois, nova conferência validou os 192 arquivos tanto na origem quanto na cópia. Nenhum relatório anterior foi sobrescrito ou removido.
- A execução usou sufixo novo, previamente verificado como livre: `feature006-t002-baseline`. Também foram guardadas cópias dos dois documentos no estado recebido (`quickstart-before.md`, `tasks-before.md`) no mesmo diretório de evidências, para verificar a preservação da T001. `target/` já era ignorado pelo Git; não se alteraram ignores.
- Conferência de processos não identificou outra execução Maven/Surefire ativa. Houve **uma única execução de testes**, sequencial, com exatamente as sete classes da T002; sem suíte completa ou ampliação da seleção. A consulta de versão do Wrapper foi anterior e não executa testes.

Comando efetivamente executado na raiz, via `cmd.exe /d /c`, com stdout/stderr redirecionados ao console preservado:

```powershell
.\mvnw.cmd "-Dtest=ProductControllerTest,ProductCatalogServiceTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,AdminQueryServicesTest,AdminProductRepositoryTest,ProductRepositoryAdminQueryComponentTest" "-Dsurefire.reportNameSuffix=feature006-t002-baseline" test
```

### Resultado conferido no console e no Surefire

Início local: **2026-10-18T18:14:29.7776923-03:00**. Fim local: **2026-10-18T18:16:28.6659573-03:00**. Duração externa: **118,888 segundos**; tempo reportado pelo Maven: **01:51 min**. **Exit code 0, BUILD SUCCESS**. Horários, comando literal e resultado constam de [run-start.json](../../target/feature006-t002-baseline-20261018T181411/run-start.json) e [run-result.json](../../target/feature006-t002-baseline-20261018T181411/run-result.json); saída integral em [console.log](../../target/feature006-t002-baseline-20261018T181411/console.log).

| Classe selecionada (incluindo suas classes aninhadas) | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| ProductControllerTest | 11 | 0 | 0 | 0 |
| ProductCatalogServiceTest | 6 | 0 | 0 | 0 |
| PublicCatalogSecurityIntegrationTest | 33 | 0 | 0 | 0 |
| PublicProductDetailSecurityIntegrationTest | 38 | 0 | 0 | 0 |
| AdminQueryServicesTest | 32 | 0 | 0 | 0 |
| AdminProductRepositoryTest | 12 | 0 | 0 | 0 |
| ProductRepositoryAdminQueryComponentTest | 12 | 0 | 0 | 0 |
| **Total** | **144** | **0** | **0** | **0** |

Foram gerados **nove XMLs e nove TXT** com o sufixo desta execução. Os grupos `PublicCatalogSecurityIntegrationTest$EmptyCatalog` e `PublicProductDetailSecurityIntegrationTest$DuplicateCategoryNames` reportam 33 e 38 testes; os respectivos XMLs das classes externas reportam zero. São classes aninhadas dos testes selecionados, não duas seleções adicionais. Uma conferência auxiliar inicial foi interrompida por presumir sete XMLs; ajustada à estrutura efetiva dos relatórios, concluiu sem reexecutar Maven nem alterar testes.

A verificação em memória com Python `xml.etree.ElementTree` comparou os atributos `tests/failures/errors/skipped` e os casos de cada XML às nove linhas correspondentes do console, conferiu a seleção por nomes e somou os XMLs contra o resumo final **144/0/0/0**. Nenhum elemento de falha/erro encontrado. [verification.json](../../target/feature006-t002-baseline-20261018T181411/verification.json) contém os resultados por relatório; cópias dos 18 arquivos atuais estão em [surefire-current](../../target/feature006-t002-baseline-20261018T181411/surefire-current/). Estes são artefatos locais ignorados, sem stage ou publicação.

### Alcance e conclusão

Baseline focal **aprovada nesta revisão**, antes de mudar comportamento ou expectativas: consultas públicas existentes e componentes administrativos compartilháveis passaram na seleção autorizada. Essa evidência não comprova o filtro público novo, os 27 cenários futuros ou os gates funcionais finais; os 2.614 testes anteriores continuam históricos. Caracterização HTTP da T004, fixtures da T003 e implementação não foram iniciadas. Nenhuma produção, teste ou expectativa foi corrigida para obter aprovação; não houve falha da baseline a diagnosticar.

Somente T002 é agora marcada `[X]`, preservando T001 `[X]` e T003–T030 abertas. Além dos artefatos de build/relatórios em `target/`, as alterações desta tarefa limitam-se ao acréscimo desta seção e ao marcador de T002. Sem stage, commit ou push. Revisão final de diff/whitespace e estado do Git são informados na entrega; nenhum impedimento remanescente para T002.

## Execução da T003 — preparação isolada dos contextos públicos

**Base conferida**: `55ad4a0f7d906c6fe0ca7336409c7db650e68796`, branch real `006-filtrar-catalogo-produtos`, raiz `C:/Users/ronal/dscommerce-speckit`, Git inicialmente limpo. T001/T002 estavam concluídas; seus registros permanecem intactos. Autorização restrita à T003, sem alterar produção, testes existentes, contratos ou configuração global.

Pré-requisitos conferidos com `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, seleção explícita de `SPECIFY_FEATURE_DIRECTORY` para a 006 e restauração de ponteiro/variável em `finally`, conforme T001: exit 0, diretório correto. Checklist 16/16, zero abertos; `.specify/extensions.yml` ausente. O ponteiro anterior foi preservado com SHA-256 `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`.

### Preparação criada e limites

Criado somente [PublicCatalogNameFilterIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java) no código de testes. O grupo contém dois contextos servlet reais `RANDOM_PORT`, com identidades explícitas `feature006.catalog-test-context=root` e `api`, perfis `test` e caminhos de contexto vazio e `/api`. Cada um ativa explicitamente o initializer existente `AdminProductTestConfig`; não se modificaram seus defaults, `application-test.properties`, `import.sql`, segurança ou Logback global.

O initializer fornece H2 exclusivo, `spring.sql.init.mode=never` e importação Hibernate vazia. OSIV é desativado somente nas propriedades desses testes. A configuração do inspector é uma classe estática aninhada `@TestConfiguration`, importada explicitamente por `@Import` nos dois contextos; não é configuração comum descoberta pelo component scan. A classe abstrata de controles e as fixtures não são componentes Spring. A revisão dessa estrutura confirma o isolamento de carregamento; nenhuma suíte de outros contextos foi repetida para esta etapa.

O controle herdado `preparesAnIsolatedCommittedCatalogWithoutNameFilter` executa uma vez por contexto. Ele comprova banco inicialmente vazio pelas conexões independentes do `AdminProductDatabaseProbe`, propriedades de isolamento, contexto servlet efetivo, cadeia real de segurança, inspector focal único e ausência do interceptor OSIV. Reutiliza `AdminProductTestFixture` sem modificações: dois produtos determinísticos (`T003 Alpha`/`T003 Beta`), uma categoria/associação e um terceiro produto de outra fixture proprietária. Todos os IDs são capturados da preparação JDBC commitada, sem IDs absolutos ou consulta de produção como oráculo.

Cada controle realiza apenas um GET real sem `name`, com `page=0&size=12`, conferindo os IDs/nomes conhecidos. A chamada ocorre fora de transação externa; snapshot independente antes/depois permanece igual, e o inspector observa somente SELECTs em transação na janela do GET, sem impor número fixo de consultas. A limpeza da primeira fixture preserva o produto da segunda; após fechar ambas, banco vazio novamente. Nenhuma conta é necessária neste controle público. Isso prepara infraestrutura e comprova o controle sem filtro; não caracteriza erros/defaults de paginação, variantes de token ou busca por nome da T004+.

### Verificação mínima efetivamente executada

Antes da execução, os **210 arquivos Surefire existentes** foram copiados e conferidos por SHA-256 em [surefire-before](../../target/feature006-t003-preparation-20261018T183008/surefire-before/), com [manifesto](../../target/feature006-t003-preparation-20261018T183008/surefire-before-manifest.json). Após executar, todos os 210 arquivos da origem e da cópia continuaram com tamanho/hash iguais ao manifesto, incluindo os relatórios da T002. Não houve Maven concorrente, repetição de baseline ou suíte completa. Sufixo novo, previamente livre, preserva os nomes dos relatórios anteriores.

Comando literal, na raiz, via `cmd.exe /d /c`, com stdout/stderr em [console.log](../../target/feature006-t003-preparation-20261018T183008/console.log):

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t003-preparation" test
```

Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`; console registra Java `21.0.12.1`. Wrapper do projeto configurado para Maven `3.9.16`, sem modificar dependências. Início **2026-10-18T18:30:24.6628373-03:00**, fim **2026-10-18T18:31:33.8963921-03:00**, duração externa **69,234 segundos**; Maven reporta **01:03 min**. **Uma execução, exit 0, BUILD SUCCESS**. Metadados em [run-start.json](../../target/feature006-t003-preparation-20261018T183008/run-start.json) e [run-result.json](../../target/feature006-t003-preparation-20261018T183008/run-result.json).

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total** | **2** | **0** | **0** | **0** |

Os três XMLs e o resumo final foram comparados às linhas do console e aos casos reportados por `xml.etree.ElementTree`, sem falhas/erros. Os dois registros `T003_CONTEXT` confirmam os critérios e URLs diferentes: raiz `jdbc:h2:mem:admin-product-test-40aa2768-8a98-4ce4-acec-6e215a448ea1`; `/api` `jdbc:h2:mem:admin-product-test-fce76e5b-1d3e-40da-b73a-304ae0a8cc55` (ambas com as opções locais do initializer). [verification.json](../../target/feature006-t003-preparation-20261018T183008/verification.json) registra a conferência e o SHA-256 do fonte efetivamente executado. Cópias dos seis relatórios atuais em [surefire-current](../../target/feature006-t003-preparation-20261018T183008/surefire-current/); evidências locais ignoradas em `target/`, não versionadas.

**Conclusão**: critérios da T003 atendidos, sem impedimento; T001–T003 `[X]`, T004–T030 abertas. A classe agora existe; as menções anteriores à sua inexistência permanecem como histórico do planejamento. Nenhum comportamento produtivo mudou. Os dois controles não aprovam o filtro público, os 27 cenários, a futura caracterização HTTP de T004 ou os gates funcionais finais. Não se criou RED artificial para infraestrutura existente. Alterações limitadas ao novo teste, ao registro desta seção e ao marcador T003; sem stage, commit, push ou mudança de branch/main. Diff, arquivo novo, links e whitespace serão conferidos antes da entrega.

## Execução da T004 — caracterização HTTP anterior ao filtro

**Contexto recebido e preservado**: raiz `C:/Users/ronal/dscommerce-speckit`, branch `006-filtrar-catalogo-produtos`, HEAD `55ad4a0f7d906c6fe0ca7336409c7db650e68796`; somente quickstart/tasks modificados e `PublicCatalogNameFilterIntegrationTest.java` não rastreado, nenhum staged. T001–T003 concluídas. Os dois controles da T003, configurações dos contextos e helpers anteriores foram preservados; suas partes de fonte foram comparadas à cópia anterior à T004, sem mudança de conteúdo.

Pré-requisitos repetidos com `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, seleção temporária explícita de `SPECIFY_FEATURE_DIRECTORY` para a 006: exit 0, diretório correto; ponteiro/variável restaurados como em T001. SHA-256 do ponteiro: `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16 e zero abertos; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

Acrescentado somente `characterizesExistingPublicPaginationHttp` e seus auxiliares ao grupo existente. O método herdado é executado em raiz e `/api`, com tag exclusiva `feature006-t004`. Reutiliza os contextos da T003, HTTP servlet real, filtros reais, perfil `test`, H2 sem seeds e OSIV local desativado. Fixture commitada de dois produtos, `T004 Alpha`/`T004 Beta`, com IDs capturados e preços numéricos 10/20; não se modificam produção, expectativas de testes existentes ou configurações globais.

### Matriz HTTP observada

Cada linha abaixo foi exercitada nos dois contextos, sem/com `name=T004` e sem token/com Bearer deliberadamente inválido: **11 casos × 2 contextos × 2 variantes de name × 2 variantes de token = 88 solicitações**. Cada combinação foi registrada integralmente com status, query, cabeçalhos e corpo. `T004` pertence aos dois nomes da fixture: este controle não testa seleção por nome nem cria uma nova expectativa de que `name` deva continuar ignorado após a implementação.

| Paginação enviada | Status | Resultado observado com dois produtos |
| --- | ---: | --- |
| Ausente | 200 | page 0, size 12, dois itens, totalElements 2, totalPages 1. |
| `page=&size=2` | 200 | page 0, size 2, dois itens, totais 2/1. |
| `page=1&size=` | 200 | page 1, size 12, conteúdo vazio, totais 2/1 preservados. |
| `page=&size=` | 200 | page 0, size 12, dois itens, totais 2/1. |
| `page=0&size=1` | 200 | Somente Alpha, size 1, totais 2/2. |
| `page=0&size=50` | 200 | Dois itens, size 50, totais 2/1. |
| `page=-1&size=12` | 400 | ErrorResponse com mensagem `page must be greater than or equal to 0`. |
| `page=0&size=0` ou `page=0&size=51` | 400 | ErrorResponse com mensagem `size must be between 1 and 50`. |
| `page=abc&size=12` ou `page=0&size=abc` | 400 | Erro nativo MVC/servlet, com timestamp e sem campo message. |

Nas 48 respostas 200, página contém exatamente `content/page/size/totalElements/totalPages`; itens contêm exatamente `id/name/image/price`, preço numérico e nomes originais. Os IDs/nomes/valores foram comparados ao manifesto da fixture, não a outra consulta de produção. As 40 respostas 400 são rejeições esperadas, não falhas dos testes.

**Limites numéricos**: corpo exato com quatro campos `status`, `error`, `message`, `path`, conferido diretamente conforme ErrorResponse, sem depender do `anyOf` permissivo do contrato. Exemplo efetivamente observado em raiz:

```json
{"status":400,"error":"Bad Request","message":"size must be between 1 and 50","path":"/products"}
```

**Conversão não numérica**: corpo efetivamente observado com `timestamp`, `status`, `error`, `path`; sem `message` nem array administrativo `errors`. Exemplo de `page=abc` em raiz:

```json
{"timestamp":"2026-10-18T21:40:05.167+00:00","status":400,"error":"Bad Request","path":"/products"}
```

Em `/api`, ambos os mecanismos devolvem `path=/api/products`. O console registra `MethodArgumentTypeMismatchException` resolvida por `DefaultHandlerExceptionResolver` para conversões inválidas; esses WARNs são compatíveis com os 400 observados. A distinção prevista em pesquisa/plano/contrato foi confirmada, sem divergência relevante que exija editar esses artefatos. O formato nativo acima é uma baseline observada nesta revisão/ambiente, não um novo DTO ou promessa de padronização.

Cabeçalhos observados: `Content-Type: application/json`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache`, `Expires: 0`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 0`, `X-Frame-Options: SAMEORIGIN`; sem `WWW-Authenticate` ou redirecionamento. Também registrados `Transfer-Encoding: chunked`, `Date` e cabeçalhos de conexão: nesta execução, respostas 200 usaram keep-alive e respostas 400, close. Cabeçalhos de transporte não são promovidos a regra de negócio ou novo contrato público.

As quatro variantes de cada caso no mesmo contexto produziram status, corpo semântico e cabeçalhos relevantes equivalentes. Timestamp nativo foi conferido como texto ISO-8601 e substituído por marcador somente na comparação; `Date` foi validado em formato RFC 1123, sem igualdade literal entre instantes. Valores brutos permanecem nas evidências. Snapshot independente antes/depois foi igual, somente SELECTs observados na janela HTTP, sem transação externa e com limpeza apenas dos IDs da fixture.

### Execução mínima e preservação das evidências

Os **216 relatórios anteriores** foram copiados antes de executar para [surefire-before](../../target/feature006-t004-characterization-20261018T183724/surefire-before/) e conferidos por SHA-256. Após executar, tamanho/hash dos 216 arquivos da origem e da cópia permaneceram iguais ao [manifesto](../../target/feature006-t004-characterization-20261018T183724/surefire-before-manifest.json). Nenhum relatório de T002/T003 ou histórico anterior foi substituído; sufixo novo previamente livre. Fonte e documentos recebidos também foram copiados para comparação de preservação.

Uma única execução sequencial pelo Wrapper, sem outra execução Maven/Surefire ativa:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t004" "-Dsurefire.reportNameSuffix=feature006-t004-characterization" test
```

Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` registrado pelo console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-18T18:38:55.5246748-03:00**, fim **2026-10-18T18:40:05.7360170-03:00**, duração externa **70,211 segundos**, Maven **01:04 min**. **Exit 0, BUILD SUCCESS; 2 testes, zero falhas, erros e ignorados**. ApiContext e RootContext reportam um teste cada; contêiner externo, zero. Três XMLs e três TXT, conferidos contra console e resumo final; todos os casos executados nos XMLs se chamam `characterizesExistingPublicPaginationHttp`. Os dois testes de preparação T003 foram preservados e não executados nesta seleção.

Evidências locais ignoradas, não versionadas: [console.log](../../target/feature006-t004-characterization-20261018T183724/console.log), [88 observações HTTP](../../target/feature006-t004-characterization-20261018T183724/http-observations.json), [run-start.json](../../target/feature006-t004-characterization-20261018T183724/run-start.json), [run-result.json](../../target/feature006-t004-characterization-20261018T183724/run-result.json), [verification.json](../../target/feature006-t004-characterization-20261018T183724/verification.json) e [cópias Surefire atuais](../../target/feature006-t004-characterization-20261018T183724/surefire-current/). A verificação inclui hash do fonte executado, total de 88 observações sem duplicação, totais dos XMLs/console, preservação dos 216 relatórios e das partes anteriores do teste.

**Conclusão T004**: caracterização GREEN concluída antes de qualquer mudança produtiva. T001–T004 `[X]`, T005–T030 abertas. O comportamento observado é do catálogo anterior à implementação do filtro; não demonstra os cenários de busca nem substitui o RED funcional da T007 ou validação futura. Não houve repetição do baseline T002, suíte completa, refatoração ou alteração de expectativas anteriores; contratos e demais artefatos aprovados preservados. Somente o teste em preparação, este registro e o marcador T004 foram atualizados. Nenhum stage, commit ou push; nenhum impedimento remanescente para esta tarefa.

## Execução da T005 — testes de trecho, caixa, acentos e literais

**Base conferida**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`, Git inicialmente limpo. T004 concluída como dependência. Escopo restrito à criação e execução dos testes US1.1–US1.5, sem implementar o filtro, refatorar produção ou atualizar as duas expectativas antigas reservadas à T011.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, seleção temporária explícita da 006 por `SPECIFY_FEATURE_DIRECTORY`, exit 0 e diretório correto. Ponteiro/variável restaurados em `finally`; SHA-256 do ponteiro preservado: `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist 16/16, zero abertos; `.specify/extensions.yml` ausente antes/depois.

### Testes criados e comportamento alcançado

Acrescentado `matchesNameSubstringCaseAccentsAndLiterals` ao [grupo existente](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java), parametrizado em cinco cenários e herdado por raiz e `/api`: **10 invocações JUnit**. A tag exclusiva `feature006-t005` seleciona somente essa tarefa. As duas preparações T003 e as duas caracterizações T004 permanecem preservadas e não foram executadas novamente.

Fixtures separadas por cenário, usando o suporte commitado já existente. Nomes e correspondências são definidos expressamente nos testes; IDs são capturados nas inserções, sem consulta produtiva como oráculo. O esperado contém somente as correspondências, na ordem conhecida, com os cinco campos da página e os quatro campos de cada item, preço numérico e totais filtrados. As variantes de uma mesma fixture são requisitadas antes do `assertAll`, para que uma falha de seleção não impeça observar as demais; cada cenário/contexto permanece uma invocação JUnit.

Uma observação servlet passiva, declarada apenas na `@TestConfiguration` já importada explicitamente pelos novos contextos, captura query bruta, `name` decodificado, URI e context path para requisições com o identificador de observação. Não modifica requisição, resposta ou autorização; não é componente produtivo nem configuração descoberta globalmente. O estado é por contexto, removido após cada captura e limpo ao terminar. A finalidade é separar codificação/transporte da falta de filtro, inclusive para `%` e acentos.

Todas as **16 solicitações HTTP** passaram pelos guards antes das asserções funcionais: parâmetro decodificado igual ao enviado, rota/contexto corretos, HTTP 200/JSON, DTOs dos produtos preparados, IDs sem duplicação, snapshots independentes iguais, SQL Hibernate somente SELECT em transação e ausência de transação externa. O controle de fixture foi confirmado antes das chamadas por conexão independente; limpeza por ownership foi concluída antes do `assertAll`. Não houve falha de compilação, fixture, autenticação ou instrumentação a classificar como RED.

### RED funcional efetivamente observado

| Cenário | Solicitações por contexto | Esperado | Observado nos dois contextos |
| --- | ---: | --- | --- |
| US1.1 — SUBSTRING | 1 | `fone`: Fone Azul/Telefone Preto, total 2. | Cabo/Fone Azul/Telefone Preto, total 3. |
| US1.2 — CASE | 3 | `fone`, `FONE`, `FoNe`: mesmos dois produtos correspondentes e total 2. | As três consultas devolvem Cabo/Fone Azul/Telefone Preto, total 3. |
| US1.3 — ACCENTS | 2 | `CAFÉ`: somente Café Especial; `cafe`: somente Cafe Claro; total 1 em cada. | Cafe Claro/Café Especial em ambas, total 2. |
| US1.4 — PERCENT | 1 | `10%`: somente Oferta 10%, total 1. | Oferta 10%/Oferta 100, total 2. |
| US1.5 — UNDERSCORE | 1 | `kit_`: somente Kit_A, total 1. | KitXA/Kit_A, total 2. |

Contagem HTTP: **(1 + 3 + 2 + 1 + 1) × 2 contextos = 16**, distinta dos 10 testes. Os 16 corpos diferem do esperado pela seleção não aplicada e pelos totais sem restrição. A URL usa UTF-8 com `URLEncoder` e é passada como `URI`, evitando reencodificação de string: `name=10%25&page=0&size=12` chegou ao servidor como `10%`; `name=CAF%C3%89&page=0&size=12` chegou como `CAFÉ`. Esses valores brutos/decodificados estão registrados, afastando uma causa de transporte para as falhas de literalidade/acentos.

**GREEN existente, sem fabricar falha**: os 16 guards de transporte/representação/leitura passaram; em ambos os contextos, as três respostas de caixa foram idênticas (`T005_CASE_EQUIVALENCE ... equal=true`). Essa igualdade inclui Cabo e total 3, portanto é uma propriedade parcial já existente quando `name` é ignorado, não prova de comparação filtrada sem distinguir caixa. Nenhum dos 10 testes completos ficou GREEN. As asserções estritas de seleção e metadados foram mantidas, sem mudar a fixture para fazer todos os nomes corresponderem ou enfraquecer o esperado.

### Execução, relatórios e limitações

Os **222 relatórios Surefire anteriores** foram copiados e conferidos antes da execução em [surefire-before](../../target/feature006-t005-name-tests-20261018T185337/surefire-before/), com [manifesto SHA-256](../../target/feature006-t005-name-tests-20261018T185337/surefire-before-manifest.json). Após executar, origem e cópia dos 222 arquivos conservaram tamanho e hash, incluindo evidências T002–T004. Sufixo novo previamente livre; nenhuma execução Maven/Surefire concorrente.

Comando literal, uma única execução na raiz, via `cmd.exe /d /c`:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t005" "-Dsurefire.reportNameSuffix=feature006-t005-name-tests" test
```

Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-18T18:55:05.0944355-03:00**, fim **2026-10-18T18:56:16.7454887-03:00**; duração externa **71,651 segundos**, tempo Maven **01:06 min**. **Exit 1, BUILD FAILURE por falhas funcionais esperadas**, não execução aprovada da feature.

| Relatório | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 5 | 5 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 5 | 5 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total** | **10** | **10** | **0** | **0** |

Os três XMLs foram comparados às linhas do console e ao resumo final. Cada invocação tem falha de `assertAll` contendo a comparação de seleção/ordem/totais do cenário, sem elemento `error`. Todos os casos executados são do método parametrizado T005. Evidências locais ignoradas: [console.log](../../target/feature006-t005-name-tests-20261018T185337/console.log), [16 observações HTTP](../../target/feature006-t005-name-tests-20261018T185337/http-observations.json), [run-start.json](../../target/feature006-t005-name-tests-20261018T185337/run-start.json), [run-result.json](../../target/feature006-t005-name-tests-20261018T185337/run-result.json), [verification.json](../../target/feature006-t005-name-tests-20261018T185337/verification.json) e [cópias dos seis relatórios atuais](../../target/feature006-t005-name-tests-20261018T185337/surefire-current/). A verificação registra hash do fonte executado, guards das 16 chamadas, invariância parcial de caixa e preservação dos relatórios anteriores.

**Conclusão T005**: tarefa de testes concluída — testes compilam contra a rota atual, usam oráculos independentes e alcançam o comportamento pretendido; RED funcional confirmado antes de produção. T001–T005 `[X]`, T006–T030 abertas. Isso não declara implementação ou critérios de busca aprovados: as dez falhas continuam presentes e aguardam a integração planejada. Não se executou T006 nem o checkpoint completo da T007; a seleção atual foi somente a solicitada para T005. Sem baseline repetido, suíte completa, mudanças em produção/contratos/expectativas antigas, stage, commit ou push. Nenhum impedimento de preparação remanescente; o comportamento ausente é a causa funcional registrada.

## Execução da T006 — testes de filtros de uma e 81 letras

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Somente este quickstart, tasks e o teste público estavam modificados, sem staged ou não rastreados. T005 e suas dependências T001–T004 estavam concluídas; código e evidências recebidos foram preservados. Escopo exclusivo da T006, sem produção ou antecipação da T007.

O primeiro `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit 0 com o contexto local da **005**; isso não foi usado para escolher a tarefa. Reexecutado por `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, `FEATURE_DIR` correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 permaneceu `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido: 16/16, zero abertos. Artefatos aprovados, descrição integral da tarefa, dependências, suportes e rota atual conferidos. Ignores Java existentes suficientes; nenhuma configuração alterada. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Cobertura e distinção dos resultados

Acrescentado somente `acceptsShortAndLongNameFilters`, parametrizado por `SHORT`/`LONG`, com tag `feature006-t006`, herdado por raiz e `/api`: **quatro invocações JUnit e quatro GETs reais**. Reutiliza os contextos isolados, perfil test, filtros reais, OSIV local desativado e observador servlet anteriores. Cada caso prepara dois produtos via `AdminProductTestFixture.createProduct`, que usa JDBC em transação própria commitada; IDs capturados e nomes explícitos formam o oráculo, sem consulta produtiva como esperado ou mudança na validação administrativa.

| Cenário | Filtro enviado e observado no servlet | Esperado estrito | Observado nos dois contextos |
| --- | --- | --- | --- |
| US1.6 — SHORT | `f`, uma letra | Somente Fone Azul; totalElements 1. | Cabo e Fone Azul; totalElements 2. |
| US1.7 — LONG | 81 letras `A` completas | Somente o nome de 81 letras `a`; totalElements 1. | Nomes de 80 e 81 letras `a`; totalElements 2. |

Em todos os casos, page 0, size 12 e totalPages 1; resposta esperada compara integralmente página e itens, IDs, nome original, imagem e preço numérico. A fixture longa contém exatamente 80 e 81 letras, confirmadas por conexão independente antes do HTTP. Truncar futuramente o filtro para 80 letras incluiria o produto de 80 e continuaria falhando contra o esperado de somente 81.

**Comportamento já existente observado**: as quatro requisições aceitam os comprimentos com 200/JSON; os valores chegam completos ao servlet, sem truncamento no transporte. Os itens devolvidos conservam os nomes preparados, inclusive 81 letras. Guards de codificação/rota/contexto, DTOs, IDs sem duplicação, snapshot independente igual e somente SELECTs Hibernate em transação passaram. Não havia transação externa; limpeza por ownership e banco vazio foram conferidos antes da comparação funcional. Essa aceitação/leitura não prova que a aplicação já use o filtro inteiro para selecionar.

**RED funcional**: as quatro comparações finais falharam por conteúdo e totalElements sem restrição por nome. O esperado exige a correspondência única; não foi enfraquecido para acomodar o endpoint atual. **Preparação**: compilação, contextos, fixtures, transporte, acesso e cleanup válidos; zero erros de teste e nenhum impedimento remanescente. Nenhum RED artificial foi criado para a aceitação de comprimento já existente.

### Execução mínima e preservação

Uma única execução sequencial pelo Wrapper, sem outro Maven/Surefire ativo, com sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t006" "-Dsurefire.reportNameSuffix=feature006-t006-length-tests" test
```

Executado na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t006-length-tests-20261019T055523/console.log). Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` registrado no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T05:56:19.7119136-03:00**, fim **2026-10-19T05:58:36.0322409-03:00**, duração externa **136,320 segundos**, Maven **02:03 min**. **Exit code 1, BUILD FAILURE por RED funcional esperado**.

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 2 | 2 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 2 | 2 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total** | **4** | **4** | **0** | **0** |

Os três XMLs e o console foram confrontados: somente o método T006 foi executado, com `AssertionFailedError` na comparação final de seleção/totais. Não se executaram T005, controles anteriores, baseline, grupo completo T007 ou suíte completa. Fonte executado: SHA-256 `CD87E58687C212D702DBDADB4442A6E00C0F25AC0C63F7B0159F7600E656DBE4`.

Os **228 relatórios anteriores**, incluindo o RED da T005, foram copiados para [surefire-before](../../target/feature006-t006-length-tests-20261019T055523/surefire-before/) antes do Maven; origem e cópia preservaram tamanho/SHA-256 do [manifesto](../../target/feature006-t006-length-tests-20261019T055523/surefire-before-manifest.json) após executar. O teste recebido também foi comparado byte a byte após remover somente os acréscimos T006, sem diferença: nenhuma assertion ou instrumentação da T005 mudou. Evidências locais ignoradas: [quatro observações HTTP](../../target/feature006-t006-length-tests-20261019T055523/http-observations.json), [run-start.json](../../target/feature006-t006-length-tests-20261019T055523/run-start.json), [run-result.json](../../target/feature006-t006-length-tests-20261019T055523/run-result.json), [verification.json](../../target/feature006-t006-length-tests-20261019T055523/verification.json) e [cópias dos seis relatórios atuais](../../target/feature006-t006-length-tests-20261019T055523/surefire-current/). Esses arquivos em `target/` não são versionados e podem faltar em outro checkout.

**Conclusão T006**: tarefa de testes concluída, com oráculo capaz de distinguir exatamente 81 de 80 letras e RED funcional registrado. T001–T006 `[X]`; T007–T030 abertas. Os critérios funcionais US1.6–US1.7 continuam pendentes de implementação; a marcação não significa filtro aprovado. Produção, suportes globais, contratos, requisitos e expectativas reservadas à T011 intactos; histórico do quickstart e RED da T005 preservados. Limites: execução local H2/HTTP nos dois contextos, sem prova para outros bancos, CI, efeitos fora da janela observada ou feature completa. Sem stage, commit ou push.

Conferência final documental: diff e acréscimos revisados; `git diff --check` sem diagnósticos de whitespace, exit 0. Auditoria local confirmou os 61 links locais existentes nos dois documentos, preservação dos bytes anteriores do teste/quickstart e mudança exclusiva do marcador T006 em relação às tasks recebidas. Nenhum arquivo novo não rastreado; evidências novas somente em `target/`, ignorado. `git status --short --untracked-files=all` manteve somente os três arquivos autorizados com ` M`, índice vazio e raiz/branch/HEAD acima inalterados. Conferência pós-execução de extensões: arquivo ausente, sem hooks a executar.

## Execução da T007 — checkpoint RED do grupo público completo

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Somente este quickstart, tasks e `PublicCatalogNameFilterIntegrationTest.java` modificados, sem staged ou não rastreados. T001–T006 estavam `[X]`; descrição integral, dependências e artefatos aprovados necessários conferidos. Produção e fonte de testes não foram editados nesta tarefa.

Pré-requisitos executados por `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, `FEATURE_DIR` correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Seleção necessária e comando executado

A T007 exige executar o **grupo completo** antes de produção. As seleções anteriores por tags T005/T006 não exercitaram o grupo junto com os controles T003/T004 na mesma execução. Essa é a necessidade concreta para incluir novamente seus casos neste checkpoint; não houve execuções separadas de T005/T006, baseline, regressões de outras classes ou suíte completa. Uma única execução Maven, sequencial, após conferir ausência de outro Maven/Surefire ativo, sem filtro de tags, exclusões ou skips:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t007-group-red" test
```

Executado na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t007-group-red-20261019T060350/console.log). Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:04:05.7215064-03:00**, fim **2026-10-19T06:04:44.6445135-03:00**, duração externa **38,923 segundos**, Maven **33.555 s**. **Exit code 1, BUILD FAILURE por falhas funcionais esperadas**.

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 9 | 7 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 9 | 7 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total** | **18** | **14** | **0** | **0** |

Os três XMLs, seus casos e o resumo do console foram confrontados. Seleção efetiva: dois controles `preparesAnIsolatedCommittedCatalogWithoutNameFilter`, duas caracterizações `characterizesExistingPublicPaginationHttp`, dez invocações `matchesNameSubstringCaseAccentsAndLiterals` e quatro `acceptsShortAndLongNameFilters`. São os quatro métodos herdados pelos dois contextos raiz e `/api`; nenhuma outra classe foi exercitada.

### GREEN existente, RED funcional e preparação

**Quatro testes GREEN existentes**: os dois controles sem filtro e as duas caracterizações HTTP passaram. Contextos H2 sem seeds, fixtures commitadas, OSIV local desativado, filtros reais, limpeza por ownership, snapshots e ausência de transação externa válidos. A caracterização registrou 88 solicitações: 48 respostas 200 e 40 respostas 400 esperadas, com variantes de `name` e token inválido; os 400 de paginação não são falhas de teste. Preservada a distinção entre erros de limites e conversão nativa, sem redefinir envelopes.

**Quatorze testes RED funcionais**: dez invocações de T005 e quatro de T006 falharam nas comparações finais de seleção/metadados. Nas 20 solicitações de busca, os guards passaram: filtro decodificado igual ao enviado, rota/contexto corretos, 200/JSON, produtos preparados sem duplicação, nomes originais, snapshots iguais e somente SELECTs Hibernate em transação na janela observada. As fixtures foram limpas antes das assertions funcionais. Resultados:

| Casos | Esperado | Observado em raiz e `/api` |
| --- | --- | --- |
| Trecho/caixa | Fone Azul e Telefone Preto, total 2. | Inclui Cabo, total 3. |
| Acentos | Uma forma correspondente a cada filtro, total 1. | Cafe Claro e Café Especial, total 2. |
| Percentual/sublinhado | Somente Oferta 10% ou Kit_A, total 1. | Inclui Oferta 100 ou KitXA, total 2. |
| Uma letra `f` | Somente Fone Azul, total 1. | Cabo e Fone Azul, total 2. |
| 81 letras `A` | Somente o nome de 81 letras `a`, total 1. | Nomes de 80 e 81 letras `a`, total 2. |

O transporte de `%`/acentos foi validado pelo valor decodificado; não explica as falhas. Os filtros de uma e 81 letras chegam completos e são aceitos com 200, comportamento já existente. Isso **não comprova seleção correta nem uso do filtro inteiro pela aplicação**: os dois produtos continuam retornando em cada caso. A igualdade parcial das três variantes de caixa também permanece observada nos dois contextos, mas inclui Cabo e não prova busca filtrada sem distinguir caixa. Nenhum desses comportamentos foi artificialmente tornado RED.

**Preparação**: fases de compilação e execução sem erro; zero elementos `error` ou `skipped` nos XMLs. As falhas correspondem às assertions funcionais esperadas, sem problema de assinatura, contexto, seed, autenticação, codificação ou cleanup. Nenhum impedimento remanescente para a T007; o comportamento ausente continua reservado às tarefas de implementação.

### Preservação, evidências e alcance

Antes do Maven, os **234 relatórios anteriores**, incluindo o RED de T005/T006, foram copiados para [surefire-before](../../target/feature006-t007-group-red-20261019T060350/surefire-before/), com [manifesto SHA-256](../../target/feature006-t007-group-red-20261019T060350/surefire-before-manifest.json). Após executar, todos conservaram tamanho/hash na origem e na cópia; sufixo T007 novo, previamente livre. O fonte permaneceu byte a byte igual ao recebido, SHA-256 `CD87E58687C212D702DBDADB4442A6E00C0F25AC0C63F7B0159F7600E656DBE4`; nenhuma assertion foi enfraquecida.

Evidências locais ignoradas: [observações HTTP](../../target/feature006-t007-group-red-20261019T060350/http-observations.json), [run-start.json](../../target/feature006-t007-group-red-20261019T060350/run-start.json), [run-result.json](../../target/feature006-t007-group-red-20261019T060350/run-result.json), [verification.json](../../target/feature006-t007-group-red-20261019T060350/verification.json) e [cópias dos seis relatórios atuais](../../target/feature006-t007-group-red-20261019T060350/surefire-current/). Não são versionadas e podem faltar em outro checkout. A validação é local no H2/HTTP configurado; não demonstra outros bancos, CI, durabilidade, efeitos fora da janela observada ou os 27 cenários completos da feature.

**Conclusão T007**: checkpoint RED concluído conforme seus critérios, com controles válidos e falha funcional esperada por `name` ignorado. T001–T007 `[X]`; T008–T030 abertas. Isso não aprova a funcionalidade de busca nem inicia renomeação, extração ou integração produtiva. Nesta tarefa, somente esta seção e o marcador T007 foram acrescentados; histórico T005/T006, fonte, produção, contratos e demais artefatos preservados. Sem stage, commit ou push.

Conferência final: diff e novos artefatos locais revisados; `git diff --check` exit 0, sem problemas de whitespace. Auditoria confirmou os 69 links locais dos dois documentos, fonte idêntico ao recebido, conteúdo anterior do quickstart preservado e somente T007 alterada nas tasks recebidas. Git final com os mesmos três arquivos ` M`, nenhum staged ou não rastreado; raiz/branch/HEAD inalterados. Evidências novas somente em `target/`, ignorado; nenhum hook pós-execução aplicável.

## Execução da T008 — renomeação da consulta filtrada

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Somente `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks modificados; nenhum staged ou não rastreado. T007 concluída e seus critérios RED conferidos, com T001–T007 `[X]`. Escopo exclusivo da renomeação prevista na T008, conforme decisão R3 da pesquisa e desenho do plano.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos. Ignores existentes suficientes; `.specify/extensions.yml` ausente nas conferências anterior/posterior, sem hooks aplicáveis.

### Alterações e referências

Renomeado somente o método `findAdminPageByName` para `findPageByName`, sem renomear arquivos. A busca `rg -n 'findAdminPageByName|findPageByName' src` identificou dez ocorrências em seis arquivos; todas foram adaptadas:

| Arquivo | Substituições | Papel |
| --- | ---: | --- |
| [ProductRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java) | 1 | Declaração da consulta filtrada. |
| [AdminProductQueryService.java](../../src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java) | 1 | Chamada no ramo com filtro. |
| [AdminQueryServicesTest.java](../../src/test/java/com/devsuperior/dscommerce/services/AdminQueryServicesTest.java) | 2 | Stub e verificação da chamada. |
| [AdminProductRepositoryTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/AdminProductRepositoryTest.java) | 1 | Helper de página. |
| [ProductRepositoryAdminQueryComponentTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/ProductRepositoryAdminQueryComponentTest.java) | 4 | Chamadas dos casos de seleção/count, página posterior e literalidade. |
| [AdminProductQueryIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java) | 1 | Referência adicional do helper `repositoryPage`, também prevista na descrição revisada da T008. |

Comparação byte a byte com as cópias recebidas confirmou que, nos seis arquivos, a única mudança foi a substituição do identificador. Preservados predicado parametrizado `locate(lower(:name), lower(p.name)) > 0`, count equivalente, ordem `p.name ASC, p.id ASC`, transação read-only e consulta sem filtro `findAdminPage`. O repositório continua sem autorização ou validação administrativa; nenhuma dependência nova, consulta duplicada, alias ou fetch de categorias foi acrescentado.

O normalizador privado `trimFilter`, suas regras Unicode e a guarda ADMIN permanecem no serviço administrativo, sem extração ou alteração. Controllers/serviço públicos e as duas expectativas antigas de `name` ignorado permanecem intactos. As asserções dos testes adaptados não mudaram; `PublicCatalogNameFilterIntegrationTest.java` conservou integralmente os bytes recebidos. A busca em todo o repositório, incluindo arquivos ocultos e excluindo `.git/`/`target/`, também foi revisada: as referências restantes ao nome antigo são descrições do planejamento e registros históricos, preservados como tais. No código Java sob `src/`, zero ocorrências antigas e dez novas.

### Compilação e regressão proporcional

Uma única execução sequencial pelo Wrapper, após conferir ausência de Maven/Surefire concorrente, com sufixo novo previamente livre. O goal `test` executou as fases de compilação de produção e testes; não houve comando de compilação separado, baseline repetido ou suíte completa:

```powershell
.\mvnw.cmd "-Dtest=AdminQueryServicesTest,AdminProductRepositoryTest,ProductRepositoryAdminQueryComponentTest,AdminProductQueryIntegrationTest#shouldNavigateTheExactSixProductFixtureWithoutDuplicatesOrLostProducts+shouldDistinguishAccentsAndInternalSpacesAndTreatSpecialCharactersLiterally" "-Dsurefire.reportNameSuffix=feature006-t008-rename" test
```

Seleção justificada pelos pontos alterados: três classes de serviço/repositório e somente os dois métodos de integração que usam `repositoryPage`. Compilação de **72 fontes de produção e 91 de testes**, Java release 21, sem erros; isso inclui referências fora da seleção de execução. Nenhum teste novo, filtro de tags ou skip foi acrescentado.

| Classe/seleção efetivamente executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| AdminQueryServicesTest | 32 | 0 | 0 | 0 |
| AdminProductRepositoryTest | 12 | 0 | 0 | 0 |
| ProductRepositoryAdminQueryComponentTest | 12 | 0 | 0 | 0 |
| AdminProductQueryIntegrationTest — navegação (4) e literais (18) | 22 | 0 | 0 | 0 |
| **Total** | **78** | **0** | **0** | **0** |

Execução na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t008-rename-20261019T060910/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:09:45.5055737-03:00**, fim **2026-10-19T06:11:12.5323123-03:00**, duração externa **87,027 segundos**, Maven **01:21 min**. **BUILD SUCCESS, exit code 0**.

Os quatro XMLs e o console foram confrontados: somente as classes/métodos acima, zero elementos de falha/erro/skip. Navegação HTTP/repositório/oráculo em raiz e `/api` produziu quatro comparações válidas; literalidade/acentos/espaços, 18. Regressões mantêm normalização externa, guarda antes da consulta, seleção/count/ordem e snapshots/observação SQL existentes; os testes diretos do repositório usam dados commitados sem exigir conta ADMIN. A leitura auxiliar inicial dos XMLs precisou reconhecer o sufixo no atributo do nome da suíte (`KeyError` no conferidor local); corrigida e concluída sem reexecutar Maven ou alterar testes. Não houve falha de preparação ou impedimento remanescente da aplicação.

### Preservação e limites

Os **240 relatórios anteriores**, incluindo RED T005–T007, foram copiados antes da execução para [surefire-before](../../target/feature006-t008-rename-20261019T060910/surefire-before/), com [manifesto SHA-256](../../target/feature006-t008-rename-20261019T060910/surefire-before-manifest.json). Após executar, origem e cópia conservaram todos os tamanhos/hashes; nenhum relatório anterior foi sobrescrito. Os testes públicos RED não foram reexecutados e seus resultados continuam identificados como evidências anteriores, não como GREEN desta refatoração. Nenhum RED artificial foi exigido para a consulta administrativa já implementada.

Evidências locais ignoradas: [run-start.json](../../target/feature006-t008-rename-20261019T060910/run-start.json), [run-result.json](../../target/feature006-t008-rename-20261019T060910/run-result.json), [verification.json](../../target/feature006-t008-rename-20261019T060910/verification.json) e [cópias dos oito relatórios atuais](../../target/feature006-t008-rename-20261019T060910/surefire-current/). A verificação registra hashes dos seis fontes e substituições exatas. Esses arquivos em `target/` não são versionados e podem faltar em outro checkout. Resultado local no H2 configurado; não comprova CI, outros bancos, suíte completa ou integração do filtro público.

**Conclusão T008**: renomeação e todas as referências compilam, regressões diretamente afetadas aprovadas e sem alteração de comportamento intencional. T001–T008 `[X]`; T009–T030 abertas. Nesta execução foram alterados seis arquivos Java e os dois documentos de evidência/acompanhamento; o teste público já modificado foi preservado. Nenhum arquivo foi renomeado, criado fora de `target/`, staged ou commitado; sem push, troca de branch ou avanço às tarefas seguintes.

Conferência final: diff dos seis arquivos Java revisado, substituições confirmadas byte a byte e nenhuma referência antiga no código Java; diff documental e artefatos locais conferidos. `git diff --check` exit 0, sem problemas de whitespace; 82 links locais dos dois documentos resolvidos. Auditoria confirmou histórico do quickstart preservado e mudança exclusiva do marcador T008 nas tasks recebidas. Git final: nove arquivos ` M` (os três recebidos mais os seis da renomeação), nenhum staged ou não rastreado; raiz/branch/HEAD acima mantidos. Conferência pós-execução de extensões: arquivo ausente, sem hooks a executar.

## Execução da T009 — extração do normalizador de consulta

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Conferidos pelo Git nove arquivos modificados, sem staged ou não rastreados: quickstart, tasks, `ProductRepository.java`, `AdminProductQueryService.java`, `AdminProductQueryIntegrationTest.java`, `PublicCatalogNameFilterIntegrationTest.java`, `AdminProductRepositoryTest.java`, `ProductRepositoryAdminQueryComponentTest.java` e `AdminQueryServicesTest.java`. Cópias recebidas preservadas antes da edição. T001–T008 `[X]`; descrição integral da T009, dependência T008 e decisões R2/plano/modelo conferidos.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, `FEATURE_DIR` correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente nas conferências anterior/posterior, sem hooks aplicáveis.

### Mudança efetiva e preservação

Criado [ProductNameFilter.java](../../src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java), classe final package-private, sem estado, imports ou anotações Spring; construtor privado e operação estática package-private `normalize(String)`. Movidos os corpos existentes de `trimFilter` e `isExternalSpace`, alterando somente o nome/visibilidade da entrada para `normalize`. Comparação textual do algoritmo extraído confirmou identidade com o código anterior. Mantidos `null`→vazio e remoção nas extremidades por pontos de código conforme `Character.isWhitespace || Character.isSpaceChar`, sem compactar espaços internos, converter caixa/acentos ou normalizar Unicode.

[AdminProductQueryService.java](../../src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java) chama `ProductNameFilter.normalize(name)` após `access.requireAdmin(principal)`; os métodos privados movidos foram removidos. Restante do serviço preservado, incluindo transação read-only, guarda, DTO/preço e `findPageByName` da T008. O helper depende somente de String/Character da linguagem, sem autorização, validação de escrita, banco ou acesso ao serviço administrativo.

Nenhum teste ou asserção foi editado. Os demais fontes recebidos foram comparados byte a byte e permaneceram iguais, inclusive o teste público RED e todas as adaptações da T008. Controllers/serviço públicos, validação de escrita e expectativas antigas de `name` ignorado intactos. A busca por `ProductNameFilter`, `trimFilter` e `findAdminPageByName` confirmou um único consumidor atual, o serviço administrativo, e nenhuma referência Java aos nomes privados/consulta antiga. A extração não integra o filtro público nem executa T010+.

### Verificação proporcional e resultados

A baseline T002 permanece a evidência anterior da normalização existente, conforme a própria tarefa; não foi repetida nem se fabricou RED de refatoração. Seleção atual: somente os dois métodos unitários de normalização de `AdminQueryServicesTest` (cinco casos de ausência/vazio/espaços Unicode e quatro de remoção externa com conteúdo literal) e os dois testes de `AdminQueryServiceComponentIntegrationTest` (proxies reais com DTOs/leituras e rejeição CLIENT antes de SQL de produto/categoria). Isso verifica o impacto da extração sem repetir automaticamente os 78 testes da T008, o grupo público RED, regressões do repositório inalterado ou a suíte completa.

Uma única execução sequencial pelo Wrapper, após conferir ausência de Maven/Surefire concorrente, com sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=AdminQueryServicesTest#shouldChooseUnrestrictedQueryForAbsentEmptyOrUnicodeWhitespace+shouldTrimOnlyRecognizedExternalCodePointsAndKeepLiteralInterior,AdminQueryServiceComponentIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t009-normalizer" test
```

Executado na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t009-normalizer-20261019T061848/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:19:18.7015570-03:00**, fim **2026-10-19T06:20:27.8998266-03:00**, duração externa **69,198 segundos**, Maven **01:03 min**. **BUILD SUCCESS, exit code 0**. O goal `test` compilou **73 fontes de produção e 91 de testes**, release 21, sem erro; não houve comando separado de compilação.

| Seleção efetivamente executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| AdminQueryServicesTest — dois métodos de normalização | 9 | 0 | 0 | 0 |
| AdminQueryServiceComponentIntegrationTest | 2 | 0 | 0 | 0 |
| **Total** | **11** | **0** | **0** | **0** |

Os dois XMLs, os quatro métodos e o resumo do console foram confrontados, sem elementos de falha/erro/skip. Os unitários preservaram caracteres internos, texto com acento decomposto, caractere suplementar e U+200B não removível, além de espaços Unicode externos, metadados e ordem da resolução da conta antes da consulta. As quatro leituras reais `filtered-page`, `detail`, `category-options` e `unfiltered-page` registraram `readOnly=true`, `snapshotEqual=true` e `callerTransaction=false`, com somente SELECTs. O teste CLIENT manteve rejeição antes das consultas de produto/categoria. Nenhum problema de preparação ou impedimento remanescente observado.

### Evidências e limites

Os **248 relatórios anteriores**, incluindo RED T005–T007 e regressão T008, foram copiados antes do Maven para [surefire-before](../../target/feature006-t009-normalizer-20261019T061848/surefire-before/), com [manifesto SHA-256](../../target/feature006-t009-normalizer-20261019T061848/surefire-before-manifest.json). Após executar, origem e cópia preservaram todos os tamanhos/hashes. O histórico deste quickstart também foi mantido integralmente.

Evidências locais ignoradas: [run-start.json](../../target/feature006-t009-normalizer-20261019T061848/run-start.json), [run-result.json](../../target/feature006-t009-normalizer-20261019T061848/run-result.json), [verification.json](../../target/feature006-t009-normalizer-20261019T061848/verification.json) e [cópias dos quatro relatórios atuais](../../target/feature006-t009-normalizer-20261019T061848/surefire-current/). A verificação inclui identidade do algoritmo e hashes dos fontes executados. Esses arquivos em `target/` não são versionados e podem faltar em outro checkout.

**Conclusão T009**: helper puro extraído, uso administrativo validado e comportamento existente preservado; T001–T009 `[X]`, T010–T030 abertas. GREEN do componente e regressão administrativa não constituem prova do filtro público: os RED anteriores não foram reexecutados, corrigidos ou convertidos em aprovação atual. Alcance local Java/H2 e proxies dos serviços, sem HTTP público, CI, outros bancos ou suíte completa. Alterações desta tarefa somente no novo helper, serviço administrativo e dois documentos; sem stage, commit, push ou troca de branch.

Conferência final: diff, extração e referências revisados; arquivo novo lido integralmente e conferido separadamente. `git diff --check` exit 0; `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java` sem diagnósticos de whitespace, exit 1 pela diferença do arquivo novo; verificação direta das linhas também confirmou ausência de espaços finais. Os 91 links locais dos dois documentos resolvem. Auditoria confirmou preservação das alterações recebidas fora do escopo e mudança exclusiva do marcador T009 nas tasks recebidas. Git final: os mesmos nove arquivos rastreados ` M`, mais somente `ProductNameFilter.java` com `??`; nenhum staged. Raiz/branch/HEAD mantidos; extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T010 — integração de name no controller e serviço públicos

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Git confirmou os nove arquivos rastreados modificados registrados na T009 e somente `src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java` não rastreado, nenhum staged. Relação real e cópias dos arquivos recebidos preservadas antes da edição. T001–T009 `[X]`; lidas descrição integral da T010, dependências e decisões correspondentes de plano/pesquisa/modelo/contrato.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos. Ignores existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Implementação e testes dentro do escopo

Adaptados e ampliados **primeiro os testes**, depois alterada produção; não se executou Maven com assinaturas temporariamente incompatíveis. O RED funcional anterior continua sendo o da T007, preservado; adaptação de assinatura não foi apresentada como novo RED.

- [ProductController.java](../../src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java): recebe `@RequestParam(name = "name", required = false) String name` e encaminha o valor original a `listProducts(name, page, size)`. Anotações/defaults/limites de page/size, detalhe e tratamento de erros mantidos.
- [ProductCatalogService.java](../../src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java): normaliza somente o filtro com `ProductNameFilter.normalize`; vazio usa `findAll(PageRequest)` com `name ASC, id ASC`; preenchido usa `findPageByName(filter, PageRequest)` com ordenação fixa da consulta existente. Mapeamento para DTO e todos os metadados continuam copiados de Page, em transação read-only. Não foi mantido overload da assinatura antiga.
- [ProductControllerTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java): stubs anteriores recebem `null` como novo argumento. Um novo teste verifica encaminhamento de `" FONE "` sem trim no controller, page/size explícitos, campos públicos exatos, nome original e preço numérico.
- [ProductCatalogServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java): chamadas anteriores adaptadas com `null`. Acrescentados dois casos parametrizados de vazio/somente espaços para o ramo sem filtro e um teste do ramo filtrado, verificando texto normalizado, PageRequest, DTO/preço exato, nome armazenado e metadados; `verifyNoMoreInteractions` restringe acesso ao repositório à consulta esperada.

Todas as assertions anteriores dos dois arquivos foram preservadas: ao remover apenas os acréscimos e reverter o argumento `null` da adaptação de assinatura, o conteúdo anterior é recuperado integralmente. Os demais fontes recebidos e o helper conservaram os bytes originais. Repositório, algoritmo compartilhado, código administrativo, DTOs, entidades, segurança, handler e validação de escrita não foram alterados. Nenhum limite/truncamento/coerção de name, normalização de nomes armazenados, parser administrativo, escrita, dependência ou fetch adicional foi introduzido. Referências à nova assinatura conferidas em `src/`; compilação completa dos fontes/testes valida seus consumidores.

### Execução necessária e resultados

Uma única execução sequencial pelo Wrapper, sem outro Maven/Surefire ativo e com sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=ProductControllerTest,ProductCatalogServiceTest" "-Dsurefire.reportNameSuffix=feature006-t010-public-integration" test
```

Seleção restrita às duas classes alteradas, incluindo suas regressões de catálogo sem filtro, detalhes, paginação, ordem, DTOs e preços. Não houve execução do grupo HTTP público, testes antigos reservados à T011, regressão administrativa inalterada, baseline ou suíte completa. O GREEN focal HTTP e a regressão compartilhada continuam pertencendo à T012 após T011.

| Classe executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| ProductControllerTest | 12 | 0 | 0 | 0 |
| ProductCatalogServiceTest | 9 | 0 | 0 | 0 |
| **Total** | **21** | **0** | **0** | **0** |

São 17 invocações anteriores preservadas e quatro novas invocações em três métodos. Os dois XMLs, os casos e o resumo do console foram confrontados, sem elementos de falha/erro/skip. O goal `test` compilou **73 fontes de produção e 91 de testes**, release 21, sem erros. Não houve execução separada de compilação.

Comando na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t010-public-integration-20261019T062857/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:30:22.3997055-03:00**, fim **2026-10-19T06:31:22.6051819-03:00**, duração externa **60,205 segundos**, Maven **54.823 s**. **BUILD SUCCESS, exit code 0**.

A leitura auxiliar dos hashes precisou reconhecer o agrupamento de arrays serializado pelo PowerShell (`TypeError` no conferidor inicial); ajustada e concluída com os quatro hashes conferidos, preservando os metadados brutos e sem repetir Maven ou alterar código. Nenhuma falha de compilação/teste ou impedimento de preparação da aplicação observado.

### Preservação, limites e pendências

Os **252 relatórios anteriores**, inclusive RED T005–T007 e regressões T008/T009, foram copiados antes da execução para [surefire-before](../../target/feature006-t010-public-integration-20261019T062857/surefire-before/), com [manifesto SHA-256](../../target/feature006-t010-public-integration-20261019T062857/surefire-before-manifest.json). Após executar, origem e cópia conservaram todos os tamanhos/hashes. O teste HTTP novo da feature e as duas expectativas antigas mantiveram seu conteúdo; resultados RED anteriores continuam históricos e não foram reescritos como GREEN.

Evidências locais ignoradas: [run-start.json](../../target/feature006-t010-public-integration-20261019T062857/run-start.json), [run-result.json](../../target/feature006-t010-public-integration-20261019T062857/run-result.json), [verification.json](../../target/feature006-t010-public-integration-20261019T062857/verification.json) e [cópias dos quatro relatórios atuais](../../target/feature006-t010-public-integration-20261019T062857/surefire-current/). São arquivos locais em `target/`, não versionados, que podem faltar em outro checkout.

**Conclusão T010**: integração controller/serviço e suas verificações focais concluídas; T001–T010 `[X]`, T011–T030 abertas. Os testes atuais usam MVC slice com serviço simulado e serviço real com repositório simulado; não provam o fluxo completo HTTP/H2, ausência de DML no banco, CI ou os 27 cenários. Permanecem pendentes a atualização das expectativas públicas antigas na T011 e o GREEN focal da T012. Como essas expectativas ainda registram name ignorado, sua execução posterior pode conflitar com a evolução intencional até T011; elas não foram executadas ou alteradas neste checkpoint. Nenhuma aprovação integral da feature ou da suíte é alegada. Sem stage, commit, push ou troca de branch.

Conferência final: diff dos quatro fontes, referências, acréscimos de teste e registro documental revisados; helper não rastreado lido e confirmado idêntico ao recebido, com whitespace válido. `git diff --check` exit 0, sem diagnósticos; 102 links locais dos dois documentos resolvidos. Auditoria confirmou histórico do quickstart e demais fontes recebidos preservados, sem enfraquecimento de assertions, e somente T010 alterada nas tasks recebidas. Git final: 13 arquivos rastreados ` M` (nove recebidos mais os quatro desta tarefa), somente `ProductNameFilter.java` com `??`, nenhum staged; raiz/branch/HEAD mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T011 — substituição das expectativas públicas de name ignorado

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`. Git confirmou os 13 arquivos rastreados modificados registrados na T010, somente `ProductNameFilter.java` não rastreado e nenhum staged. Caminhos e cópias recebidos preservados antes da edição. T001–T010 `[X]`; lidas descrição integral da T011, dependência T010 e decisões correspondentes, especialmente pesquisa R6 e evolução intencional descrita na spec.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Expectativas substituídas e justificativa

As duas expectativas ativas foram substituídas conforme o contrato aprovado da Feature 006 e a integração T010. Isso sucede intencionalmente name ignorado no catálogo público da Feature 005, preservando seus documentos/contratos e resultados históricos. Produção não foi alterada nesta tarefa.

| Arquivo e método anterior | Método atualizado | Mudança específica |
| --- | --- | --- |
| [AdminProductPublicRegressionIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java): `adminFilterAndCategoryIdentifiersDoNotChangePublicContract` | `publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation` | Antes, as quatro consultas públicas retornavam Alpha/Beta. Agora, sem filtro mantém ambos; `ALP` retorna somente Alpha; `does-not-exist` e `%_` retornam vazio. |
| [AdminProductQueryIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java): `shouldKeepThePublicCatalogNumericAndUnfilteredByTheAdministrativeNameParameter` | `shouldFilterThePublicCatalogByNameWithNumericPricesAndCoherentPagination` | Antes, `name=fone` percorria os seis produtos/totais 6/3. Agora, usa os cinco IDs do manifesto filtrado, quantidades 2/2/1/0 e totais 5/3. |

No primeiro teste, quatro chamadas explícitas usam o helper estrito de página: IDs/ordem, nome, imagem, preço numérico, campos exatos e page/size/totais. Sem filtro: page 0, size 12, totalElements 2/totalPages 1; ALP: 1/1; sem correspondência: 0/0, conteúdo vazio, sucesso. Mantidos acesso anônimo/token inválido, consulta administrativa autenticada com ALP selecionando Alpha e preço textual, detalhe administrativo com os dois IDs de categorias homônimas, detalhe público com nome de categoria deduplicado e snapshot independente antes/depois. A codificação `%25_` da URL representa o filtro literal `%_`.

No segundo teste, cada página é comparada ao JSON completo esperado por `assertPage`, incluindo price numérico e metadados 5/3 até na página 3 vazia. O manifesto conhecido de seis produtos/cinco correspondências fornece os IDs capturados, sem consulta de produção como oráculo. Preservada `assertCompletePages` para união exata, sem perdas/duplicação e com desempate por ID; acrescentada a verificação explícita de tamanhos 2/2/1/0. Mantidas as quatro variantes: raiz e `/api`, cada uma sem token e com Bearer inválido. Snapshots, SQL observado e cleanup existentes permanecem ativos.

A comparação dos fontes confirmou que somente esses dois métodos (incluindo seus nomes e o display do primeiro) mudaram; conteúdo fora deles preservado. Toda produção, integração T010, normalizador, repositório e autorização administrativa conservaram os bytes recebidos. Nenhuma assertion específica foi substituída por checagem genérica; helpers, fixtures, demais testes e contratos históricos intactos. As referências antigas restantes no planejamento/histórico foram preservadas como registro; no código Java os nomes novos identificam os métodos ativos.

### Execução exclusiva da seleção T011

Uma única execução sequencial pelo Wrapper, sem Maven/Surefire concorrente, com sufixo novo previamente livre e seleção de **somente os dois métodos atualizados**:

```powershell
.\mvnw.cmd "-Dtest=AdminProductPublicRegressionIntegrationTest#publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation,AdminProductQueryIntegrationTest#shouldFilterThePublicCatalogByNameWithNumericPricesAndCoherentPagination" "-Dsurefire.reportNameSuffix=feature006-t011-public-expectations" test
```

| Método executado | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation — duas variantes de token | 2 | 0 | 0 | 0 |
| shouldFilterThePublicCatalogByNameWithNumericPricesAndCoherentPagination — quatro variantes contexto/token | 4 | 0 | 0 | 0 |
| **Total** | **6** | **0** | **0** | **0** |

Comando na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t011-public-expectations-20261019T063543/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:36:17.3353107-03:00**, fim **2026-10-19T06:37:30.9632948-03:00**, duração externa **73,628 segundos**, Maven **01:08 min**. **BUILD SUCCESS, exit code 0**. Produção sem recompilação por estar atualizada; **91 fontes de teste recompilados**, release 21, sem erros. Nenhum comando separado de compilação.

Os dois XMLs, os seis casos e o console foram confrontados, sem elementos de falha/erro/skip. O primeiro método passou em HTTP servlet real sob `/api`, preservando os dois registros `T069_PROOF group=scoped-filter`. O segundo passou via MockMvc/filtros reais com contexto informado em cada chamada: 16 páginas observadas, todas com `snapshotEqual=true` e `hibernateDml=0`. Essa observação SQL pertence à janela das chamadas desse teste; o primeiro mantém snapshot independente, sem novo inspector. Nenhum problema de preparação ou impedimento remanescente observado.

### Preservação e limites

Os **256 relatórios anteriores** foram copiados antes do Maven para [surefire-before](../../target/feature006-t011-public-expectations-20261019T063543/surefire-before/), com [manifesto SHA-256](../../target/feature006-t011-public-expectations-20261019T063543/surefire-before-manifest.json). Após executar, origem e cópia conservaram todos os tamanhos/hashes, inclusive RED T005–T007 e resultados T008–T010. O histórico deste quickstart foi mantido integralmente.

Evidências locais ignoradas: [run-start.json](../../target/feature006-t011-public-expectations-20261019T063543/run-start.json), [run-result.json](../../target/feature006-t011-public-expectations-20261019T063543/run-result.json), [verification.json](../../target/feature006-t011-public-expectations-20261019T063543/verification.json) e [cópias dos quatro relatórios atuais](../../target/feature006-t011-public-expectations-20261019T063543/surefire-current/). Incluem hashes dos dois fontes executados; arquivos em `target/` são locais, não versionados e podem faltar em outro checkout.

**Conclusão T011**: as duas expectativas foram atualizadas e aprovadas na seleção exigida; T001–T011 `[X]`, T012–T030 abertas. Não se executou T012+, o grupo `PublicCatalogNameFilterIntegrationTest`, baseline ou suíte completa. Alcance: esses dois métodos, variantes e H2 configurado; não comprova os 27 cenários, regressão administrativa completa, demais jornadas, outros bancos ou CI. Alterações desta tarefa somente nos dois testes, neste registro e no marcador T011; sem mudanças de produção, stage, commit, push ou troca de branch.

Conferência final: diff, nomes dos métodos, referências e garantias remanescentes revisados; arquivo não rastreado recebido conferido e preservado. `git diff --check` exit 0, sem problemas de whitespace; 111 links locais dos dois documentos resolvidos. Auditoria confirmou conteúdo anterior do quickstart intacto e somente T011 alterada nas tasks recebidas. Git final: 14 arquivos rastreados ` M` (os 13 recebidos mais `AdminProductPublicRegressionIntegrationTest.java`), somente `ProductNameFilter.java` com `??`, nenhum staged. Raiz/branch/HEAD mantidos; extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T012 — GREEN focal de US1 e regressões compartilhadas

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `c090c528e8daf099acc90e50636eb6a476520319`, com as alterações locais de T001–T011 ainda sem commit. Git confirmou os 14 arquivos rastreados modificados registrados na T011, somente `ProductNameFilter.java` não rastreado e nenhum staged. Caminhos, fontes e documentos recebidos copiados antes da execução. Lidas integralmente T012, dependências e critérios; nenhuma mudança em produção, testes, fixtures ou assertions nesta tarefa.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Comandos e seleção efetiva

Primeiro comando, contendo exatamente os seletores de classes/métodos previstos na T012, executado pelo Wrapper na raiz:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,AdminQueryServicesTest,AdminProductRepositoryTest,ProductRepositoryAdminQueryComponentTest,AdminProductPublicRegressionIntegrationTest#publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation,AdminProductQueryIntegrationTest#shouldFilterThePublicCatalogByNameWithNumericPricesAndCoherentPagination" "-Dsurefire.reportNameSuffix=feature006-t012-us1-green" test
```

**Problema de seleção observado**: `BUILD SUCCESS`, exit 0, mas console/Surefire registraram somente **83 testes** em sete XMLs; nenhuma execução das classes aninhadas do grupo público nem observações T005/T006. A omissão ocorreu nesta combinação de seletores de classe e método; zero skips reportados não comprovava seleção completa. A conferência inicial esperava dez XMLs e identificou a lacuna. Isso não foi tratado como GREEN da T012 nem como defeito de produção. A causa observada é seleção/discovery incompleta das classes aninhadas, sem alegar diagnóstico interno adicional do Surefire.

Após preservar os relatórios dessa primeira execução, executado **somente o grupo faltante**, sem repetir os 83 testes:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t012-public-green" test
```

O complemento executou os **18 testes públicos**, incluindo as duas classes aninhadas e os controles, com `BUILD SUCCESS`, exit 0. A união das duas execuções corresponde exatamente ao grupo da T012: seis classes completas e os dois métodos da T011, sem sobreposição ou seleção adicional. Não se executou baseline, suíte completa ou T013+. Maven sempre sequencial, sem outro Maven/Surefire ativo; sufixos novos previamente livres. Os fontes permaneceram iguais entre as execuções.

| Execução | Testes | Falhas | Erros | Ignorados | Exit code |
| --- | ---: | ---: | ---: | ---: | ---: |
| Seleção mista inicial, incompleta | 83 | 0 | 0 | 0 | 0 |
| Complemento público faltante | 18 | 0 | 0 | 0 | 0 |
| **Agregado, sem duplicação** | **101** | **0** | **0** | **0** | **0 em ambas** |

**101 é o agregado dos XMLs de duas execuções, não um resumo único de console Maven.** Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Ambos os comandos executados via `cmd.exe /d /c`, preservando stdout/stderr. As fases de compilação reutilizaram classes atualizadas, sem erro; não houve recompilação nova nem comando separado de compilação.

- Inicial: **2026-10-19T06:42:26.3271464-03:00** a **06:43:20.2921579-03:00**, duração externa **53,965 s**, Maven **48.787 s**; [console.log](../../target/feature006-t012-us1-green-20261019T064209/console.log), [run-start.json](../../target/feature006-t012-us1-green-20261019T064209/run-start.json) e [run-result.json](../../target/feature006-t012-us1-green-20261019T064209/run-result.json).
- Complemento: **2026-10-19T06:45:12.6161412-03:00** a **06:45:53.4823972-03:00**, duração externa **40,866 s**, Maven **35.595 s**; [console-public.log](../../target/feature006-t012-us1-green-20261019T064209/console-public.log), [run-complement-start.json](../../target/feature006-t012-us1-green-20261019T064209/run-complement-start.json) e [run-complement-result.json](../../target/feature006-t012-us1-green-20261019T064209/run-complement-result.json).

### Console/Surefire e correspondência RED → GREEN

Conferidos dez XMLs e dez TXTs contra os dois consoles, casos e seletores efetivos, sem elementos de falha/erro/skip. Contagens: grupo público **18** (ApiContext 9, RootContext 9; contêiner zero), ProductControllerTest **12**, ProductCatalogServiceTest **9**, AdminQueryServicesTest **32**, AdminProductRepositoryTest **12**, ProductRepositoryAdminQueryComponentTest **12**, método atualizado de AdminProductPublicRegressionIntegrationTest **2** e método atualizado de AdminProductQueryIntegrationTest **4**. Nenhum outro método das duas últimas classes foi executado.

| Evidência anterior preservada | Antes: testes/falhas | Correspondência atual | Agora: testes/falhas |
| --- | --- | --- | --- |
| T005 — trecho/caixa/acentos/literais | 10/10 | Mesmo método e casos nos dois contextos | 10/0 |
| T006 — filtros de 1/81 letras | 4/4 | Mesmo método e casos nos dois contextos | 4/0 |
| T007 — grupo público completo | 18/14 | Mesmos 14 casos funcionais e quatro controles | 18/0 |

As linhas acima se sobrepõem historicamente; representam **14 testes antes RED**, agora GREEN, não 28 testes distintos. Os XMLs anteriores foram lidos, e nomes de classe/caso da T007 correspondem exatamente aos atuais. O fonte público permanece byte a byte igual ao da T007, SHA-256 `CD87E58687C212D702DBDADB4442A6E00C0F25AC0C63F7B0159F7600E656DBE4`. Nenhuma assertion foi enfraquecida.

Nas **20 solicitações públicas de busca**, parâmetro decodificado e guards válidos, status 200 e **corpo integral igual ao esperado**, incluindo IDs capturados, ordem, nomes originais, imagem, preço numérico e metadados. Trecho/caixa selecionam somente Fone Azul/Telefone Preto, total 2; CAFÉ e cafe selecionam suas formas distintas, total 1; `10%` e `kit_` selecionam somente Oferta 10% e Kit_A, total 1. Percentual chega codificado por `%25` e decodificado literalmente. Os oráculos esperados foram confrontados com T005/T006 e T007, preservando valores e ordenação; IDs absolutos de fixtures de execuções distintas não foram tratados como constantes globais.

**Comprimentos**: `f` chega com uma letra e seleciona somente Fone Azul, excluindo Cabo; 81 letras `A` chegam completas e selecionam somente o nome de 81 letras `a`, excluindo o de 80. Ambas as respostas têm page 0, size 12, totalElements 1 e totalPages 1. Os dados armazenados de 80/81 letras são confirmados pela fixture/probe. HTTP 200 já existia no RED; a evidência nova é seleção e metadados corretos, sem truncamento ou enfraquecimento do esperado.

**Controles preservados**: dois controles T003 confirmaram H2s distintos, fixtures commitadas, cleanup por ownership, ausência de transação externa e snapshots intactos. Duas caracterizações T004 passaram com 88 requisições (48 respostas 200 e 40 respostas 400 esperadas), variantes de name/token em raiz e `/api`. Comparação com as observações históricas da T004 confirmou corpos semânticos e cabeçalhos relevantes, preservando limites Bean Validation e conversão nativa. Apenas IDs absolutos de fixture e instantes voláteis foram normalizados no conferidor entre execuções; os testes continuam comparando os IDs capturados de cada fixture e validando formato/presença dos instantes.

Na janela das buscas do grupo público, guards de SQL somente SELECTs Hibernate em transação, snapshots independentes iguais, IDs sem duplicação e cleanup passaram antes das assertions funcionais. As regressões reais de repositório e os testes atualizados da T011 também passaram com seus snapshots/controles existentes, incluindo zero DML observado onde o inspector está instalado. O teste servlet de categorias mantém snapshot independente sem novo inspector; a validação não promete observação universal de JDBC direto ou efeitos fora dessas janelas.

### Preservação e alcance do checkpoint

Antes da primeira execução, os **260 relatórios anteriores** foram copiados para [surefire-before](../../target/feature006-t012-us1-green-20261019T064209/surefire-before/) e conferidos pelo [manifesto SHA-256](../../target/feature006-t012-us1-green-20261019T064209/surefire-before-manifest.json). Antes do complemento, foram preservados os **274 relatórios então presentes**, incluindo os 14 arquivos da primeira execução, em [surefire-before-complement](../../target/feature006-t012-us1-green-20261019T064209/surefire-before-complement/) e seu [manifesto](../../target/feature006-t012-us1-green-20261019T064209/surefire-before-complement-manifest.json). Após ambas, origem e cópias mantiveram todos os tamanhos/hashes. Histórico RED e resultados anteriores não foram substituídos ou reescritos.

[verification.json](../../target/feature006-t012-us1-green-20261019T064209/verification.json) registra agregação por suíte/execução, seleção exata, comparação RED/GREEN, controles e preservação; [http-observations.json](../../target/feature006-t012-us1-green-20261019T064209/http-observations.json) contém as 108 observações T004/T005/T006, e [surefire-current](../../target/feature006-t012-us1-green-20261019T064209/surefire-current/) guarda os 20 relatórios atuais. Os 13 fontes recebidos, incluindo o helper não rastreado, conservaram seus hashes. Esses artefatos em `target/` são locais, ignorados e podem faltar em outro checkout.

**Conclusão T012**: grupo exigido completo e GREEN na mesma revisão de trabalho, após corrigir somente a omissão de seleção com o complemento disjunto. T001–T012 `[X]`; T013–T030 abertas. Primeiro checkpoint funcional de US1 e regressões compartilhadas, sem encerrar as quatro histórias P1 ou os 27 cenários da feature. Não há impedimento remanescente nem defeito de produção demonstrado nesta tarefa. Limites: Windows/H2 configurado, sem prova de outros bancos, CI, SLA, concorrência, durabilidade ou suíte completa. Somente quickstart e marcador T012 alterados nesta execução; fontes/testes preservados, sem stage, commit, push ou troca de branch.

Conferência final: diff documental, seleção real, XMLs/TXTs/consoles, correspondência RED/GREEN e artefatos novos locais revisados. Helper não rastreado recebido permanece idêntico e com whitespace válido. `git diff --check` exit 0, sem diagnósticos; 124 links locais dos dois documentos resolvidos. Auditoria confirmou conteúdo anterior do quickstart e todos os fontes recebidos intactos, com somente T012 alterada nas tasks recebidas. Git final: os mesmos 14 arquivos rastreados ` M`, somente `ProductNameFilter.java` com `??`, nenhum staged; raiz/branch/HEAD mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.
