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
