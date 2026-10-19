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

## Execução da T013 — espaços externos e ausência de filtro

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `529aefd18b0e5afaedb6c849f88cb6541f48273c`, Git limpo, nenhum staged ou não rastreado. T001–T012 `[X]`; dependência T012 conferida com o checkpoint US1 registrado no commit recebido. Lidas integralmente T013 e suas condições, US2.1/US2.2/US2.5 da spec, decisões R2/R4/R7 e desenho correspondente. A execução atual é exclusiva de T013; as evidências históricas de US1 não foram reexecutadas.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, `FEATURE_DIR` correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos. Ignores Java/Maven existentes suficientes; `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Cobertura acrescentada e oráculos

Acrescentados somente o método parametrizado `normalizesOnlyExternalFilterWhitespace`, o record de entradas e seu provider em [PublicCatalogNameFilterIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java), com tag `feature006-t013`. São **19 variantes em cada contexto HTTP servlet real**, raiz e `/api`, com filtros reais, perfil test, H2 isolado sem seeds e OSIV local desativado:

| Variantes por contexto | Quantidade | Oráculo da página `page=0&size=1` |
| --- | ---: | --- |
| Controle `FONE`, ` FONE `, tab/NBSP/U+2003 só à esquerda, só à direita ou dos dois lados, combinação de todos com espaço comum | 12 | Somente o ID capturado de Fone Azul; totalElements 1, totalPages 1. |
| `name` omitido, vazio, somente espaços comuns, somente tab, somente NBSP, somente U+2003 e combinação desses espaços | 7 | Somente o ID capturado de Cabo na primeira página sem filtro; totalElements 2, totalPages 2. |

Cada invocação prepara e commita sua própria fixture com Fone Azul e Cabo, em ordem de inserção diferente da ordem pública. O esperado usa esses IDs e valores conhecidos, sem consultar produção para calcular seleção ou copiar o algoritmo de normalização. O nome armazenado crescente coloca Cabo primeiro na página sem filtro; o catálogo completo continua tendo dois produtos. FONE seleciona Fone Azul e exclui Cabo. A paginação explícita é a mesma para todas as variantes; tamanho 1 distingue também os totais de páginas 1 e 2. A prova é a igualdade com o **JSON integral esperado**, incluindo ID, nome original, imagem, preço numérico e todos os metadados, não apenas HTTP 200 ou igualdade entre respostas.

O observador servlet passivo confere valor decodificado idêntico ao enviado, inclusive `null` no omitido, vazio e caracteres Unicode; o transporte usa `URLEncoder` UTF-8. Os dois produtos preparados são verificados por conexão independente antes da chamada. Na janela de cada GET, somente SELECTs Hibernate em transação e snapshot independente igual depois do retorno; ausência de transação externa e cleanup por ownership conferidos antes da assertion funcional final. Nenhum nome armazenado ou devolvido passa por limpeza no teste.

O diff do fonte contém somente **duas inserções**, preservando integralmente os testes, helpers, preparação e assertions anteriores de US1/T003/T004. Os outros **166 fontes rastreados** conservaram seus hashes, incluindo toda produção e os demais testes. Nenhum método de T014, caso de serviço de T015 ou correção/checkpoint T016 foi executado ou implementado.

### Comando e resultado efetivamente executados

Uma única execução Maven, sequencial, após conferir ausência de outro Maven/Surefire ativo, usando Wrapper e sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t013" "-Dsurefire.reportNameSuffix=feature006-t013-whitespace" test
```

O seletor de tag limita a execução ao novo método herdado pelos dois contextos; os testes anteriores da classe não foram executados. A leitura dos casos XML confirmou a seleção efetiva, e o console não contém observações T003–T006. Não houve repetição da validação US1, baseline, grupo HTTP completo, testes administrativos/de serviço ou suíte completa; nenhuma exclusão ou skip novo foi acrescentado aos fontes/configuração.

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 19 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 19 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total da única execução** | **38** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**. Console, três XMLs e três TXTs confrontados, sem elementos de falha/erro/skip; somente os 38 casos do novo método. As **38 observações HTTP** (24 preenchidas e 14 sem restrição após normalização) têm os produtos/corpos/metadados exatos esperados, com transporte e guards válidos. Todos os testes passaram na primeira execução; **GREEN de comportamento já existente após US1**, sem RED funcional, RED artificial ou problema de preparação observado. Produção permaneceu intacta.

Execução na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t013-whitespace-20261019T065910/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` observado no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T06:59:44.2633669-03:00**, fim **07:00:57.2051654-03:00**, duração externa **72,942 s**, Maven **01:07 min**. Produção atualizada sem recompilação; **91 fontes de teste recompilados**, release 21, sem erro. Nenhum comando separado de compilação.

### Preservação, evidências e limites

Antes do Maven, os **280 relatórios anteriores**, incluindo RED T005–T007 e os GREENs de US1, foram copiados para [surefire-before](../../target/feature006-t013-whitespace-20261019T065910/surefire-before/), com [manifesto SHA-256](../../target/feature006-t013-whitespace-20261019T065910/surefire-before-manifest.json). Após executar, todos conservaram tamanho/hash na origem e na cópia. O sufixo atual gerou seis arquivos novos, preservados em [surefire-current](../../target/feature006-t013-whitespace-20261019T065910/surefire-current/); nenhum relatório anterior foi sobrescrito.

Evidências locais adicionais: [run-start.json](../../target/feature006-t013-whitespace-20261019T065910/run-start.json), [run-result.json](../../target/feature006-t013-whitespace-20261019T065910/run-result.json), [observações HTTP](../../target/feature006-t013-whitespace-20261019T065910/http-observations.json) e [verification.json](../../target/feature006-t013-whitespace-20261019T065910/verification.json). A verificação registra seleção/casos, fontes executados e preservação dos testes/relatórios anteriores. Artefatos em `target/` são locais, ignorados e podem faltar em outro checkout.

**Conclusão T013**: US2.1, US2.2 e US2.5 verificadas com oráculos de IDs e metadados para cada variante. T001–T013 `[X]`; T014–T030 abertas. Isso não encerra US2 nem a feature: distinção entre espaços internos, nome legado com extremos e NBSP interno permanece em T014; cobertura adicional do serviço e checkpoint conjunto permanecem T015/T016. Alcance atual: HTTP real nos dois contextos Windows/H2, página 0/tamanho 1, sem credenciais, nomes da fixture e janelas síncronas observadas. Não prova outros bancos, CI, todas as páginas, efeitos fora da janela, durabilidade ou ausência universal de JDBC direto. Sem impedimento remanescente; sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff dos três arquivos, dados/provider e novos artefatos locais revisados; `git diff --check` exit 0, sem problemas de whitespace, e 133 links locais dos dois documentos resolvidos. Auditoria confirmou todo o conteúdo anterior do quickstart preservado, somente T013 alterada nas tasks e nenhum arquivo novo rastreável. Git final: somente este quickstart, tasks e `PublicCatalogNameFilterIntegrationTest.java` modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T014 — espaços internos e nomes armazenados

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `529aefd18b0e5afaedb6c849f88cb6541f48273c`. Git confirmou somente quickstart, tasks e `src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java` modificados, nenhum staged ou não rastreado. Alterações recebidas copiadas antes da edição. T001–T013 `[X]`; descrição integral de T014, dependência T013, US2.3/US2.4/US2.6 e decisões correspondentes da spec/plano/pesquisa/modelo/contrato conferidos. Escopo exclusivo de testes de T014, sem iniciar T015+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Casos, isolamento e resultados concretos

Acrescentados o método parametrizado `preservesInternalSpacesAndStoredNames`, seu enum e provider em [PublicCatalogNameFilterIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java), com tag `feature006-t014`. São três cenários independentes herdados pelos contextos HTTP servlet reais de raiz e `/api`, sem credenciais e com filtros reais, perfil test, H2 isolado sem seeds e OSIV local desativado. Cada invocação prepara dados commitados próprios via suporte JDBC existente; não invoca nem enfraquece validação administrativa para preparar o legado.

| Cenário | Fixture independente | Consultas e oráculos explícitos em cada contexto |
| --- | --- | --- |
| US2.3 / COMMON | Fone Azul e Fone com dois espaços internos Azul | `fone azul` seleciona somente o primeiro ID; `fone  azul` somente o segundo. Cada resposta tem totalElements 1 e totalPages 1. |
| US2.4 / LEGACY | Somente `"  Fone  Azul  "`: dois espaços externos de cada lado e dois internos | `" fone  azul "` seleciona esse ID e devolve o nome original integral; `fone azul` não encontra produto, com conteúdo vazio e totais 0/0. |
| US2.6 / NBSP | Fone Azul e `Fone\u00A0Azul`, com um NBSP interno | `fone azul` seleciona somente o primeiro ID; `fone\u00A0azul` somente o segundo. Cada resposta tem totais 1/1, sem conversão do NBSP em espaço comum. |

As aspas delimitam o exemplo legado e não integram o nome/filtro; `\u00A0` representa um único caractere U+00A0, não seis caracteres literais. As três fixtures nunca coexistem: em particular, o legado com dois espaços internos não interfere na seleção do cenário COMMON. Banco vazio conferido antes e depois de cada invocação; cleanup por ownership concluído antes das assertions funcionais finais.

São **seis casos de teste e doze requisições HTTP**, duas por cenário/contexto, todas em `page=0&size=12`. Os esperados usam IDs capturados das inserções e manifestos fixos de seleção, sem consulta de produção como oráculo. O JSON completo deve coincidir: ID exato, texto original, imagem, preço numérico, campos públicos e page/size/totais. Assim, 200 e simples igualdade entre respostas não substituem a prova de seleção. A fixture e os snapshots independentes conferem os nomes exatos, inclusive o legado e o NBSP, sem limpar ou compactar seus textos.

O observador servlet passivo confirmou query/valor decodificado idênticos ao enviado, com `URLEncoder` UTF-8 e rota/contexto corretos. Após cada GET e antes da limpeza, snapshot igual ao anterior; na janela observada, somente SELECTs Hibernate em transação e nenhuma transação externa do chamador. Nenhuma contagem rígida de SELECTs. Os guards passaram também no caso de resposta vazia, cuja ausência é esperada pelo filtro com um único espaço interno sobre o legado.

Comparação com as cópias recebidas confirmou somente **duas inserções** no fonte; todo o conteúdo anterior, inclusive T013 e US1, permanece intacto. Os outros **166 fontes rastreados** conservaram seus hashes, incluindo toda produção e os demais testes. Nenhum teste de serviço, normalizador ou componente produtivo foi alterado.

### Seleção executada pelo Wrapper

Uma única execução Maven sequencial, após conferir ausência de outro Maven/Surefire ativo e sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t014" "-Dsurefire.reportNameSuffix=feature006-t014-internal-spaces" test
```

A tag selecionou somente o método novo nos dois contextos. Console e Surefire confirmam três cenários em cada classe aninhada; nenhum caso de T013/US1, baseline, serviço ou suíte completa foi reexecutado. O console não contém observações T003–T006 ou T013; zero skips não foi usado como substituto da conferência da seleção.

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 3 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 3 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total da única execução** | **6** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**. Três XMLs, três TXTs e console confrontados, sem elementos de falha/erro/skip; doze observações com corpo integral igual ao esperado. **GREEN de comportamento já existente após US1**, na primeira execução, sem RED funcional ou artificial. Compilação e preparação sem falhas observadas; nenhum impedimento remanescente. As assertions foram acrescentadas antes da execução e não foram enfraquecidas; produção permaneceu intacta.

Comando executado na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t014-internal-spaces-20261019T070511/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T07:05:27.4535807-03:00**, fim **07:06:36.6846855-03:00**, duração externa **69,231 s**, Maven **01:04 min**. Produção atualizada sem recompilação; **91 fontes de teste recompilados**, release 21, sem erro. Sem comando separado de compilação.

### Preservação e limites da evidência

Os **286 relatórios anteriores**, inclusive T013 e os RED/GREEN de US1, foram copiados antes do Maven para [surefire-before](../../target/feature006-t014-internal-spaces-20261019T070511/surefire-before/), com [manifesto SHA-256](../../target/feature006-t014-internal-spaces-20261019T070511/surefire-before-manifest.json). Após executar, origem e cópia conservaram todos os tamanhos/hashes. Seis relatórios novos preservados em [surefire-current](../../target/feature006-t014-internal-spaces-20261019T070511/surefire-current/); nenhum relatório anterior sobrescrito.

Evidências locais: [run-start.json](../../target/feature006-t014-internal-spaces-20261019T070511/run-start.json), [run-result.json](../../target/feature006-t014-internal-spaces-20261019T070511/run-result.json), [observações HTTP](../../target/feature006-t014-internal-spaces-20261019T070511/http-observations.json) e [verification.json](../../target/feature006-t014-internal-spaces-20261019T070511/verification.json). Registram seleção efetiva, resultados exatos, hashes dos fontes executados e preservação do conteúdo recebido/relatórios. Artefatos locais ignorados em `target/`, que podem faltar em outro checkout. O histórico deste quickstart foi mantido integralmente.

**Conclusão T014**: seleção distingue um/dois espaços e NBSP interno, preserva resposta textual original e snapshots, com fixtures independentes nos dois contextos. T001–T014 `[X]`; T015–T030 abertas. Cobertura adicional do serviço e checkpoint conjunto de US2 permanecem T015/T016. Alcance desta evidência: cenários US2.3/US2.4/US2.6 no Windows/H2 configurado, página 0/tamanho 12 e janelas síncronas observadas; não aprova toda US2/feature, outros bancos, CI, durabilidade, efeitos fora da janela ou ausência universal de JDBC direto. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff incremental contra as cópias recebidas, diff acumulado, dados/provider e novos artefatos locais revisados; `git diff --check` exit 0, sem problemas de whitespace, e 142 links locais dos dois documentos resolvidos. Auditoria confirmou histórico do quickstart e alterações T013 preservados, somente T014 alterada nas tasks recebidas e nenhum arquivo novo rastreável. Git final: somente quickstart, tasks e `PublicCatalogNameFilterIntegrationTest.java` modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T015 — decisões do serviço para ausência e espaços

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `529aefd18b0e5afaedb6c849f88cb6541f48273c`. Git confirmou somente quickstart, tasks e `PublicCatalogNameFilterIntegrationTest.java` modificados, sem staged ou não rastreados. Cópias recebidas e hashes dos fontes preservados antes de editar. T001–T014 `[X]`; descrição integral de T015, dependência T014 e decisões correspondentes da spec/plano/pesquisa/modelo/contrato conferidos. Nenhuma execução de T016+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Alterações e oráculos de serviço

Acrescentados dois métodos parametrizados, provider e imports em [ProductCatalogServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/ProductCatalogServiceTest.java), com tag `feature006-t015`. Os testes instanciam o serviço real com repositório simulado pelo Mockito; chamam somente a operação pública de serviço e observam seus argumentos/resultados, sem reflexão sobre normalizador ou algoritmo copiado.

| Método novo | Casos | Expectativa concreta |
| --- | ---: | --- |
| `listProductsShouldUseUnfilteredQueryForAbsentOrUnicodeWhitespaceName` | 5 | `null`, vazio, espaço/tab/LF, NBSP/U+2007/U+202F e U+2003/U+3000: uma chamada exata a `findAll`, PageRequest 1/2 com Sort nome/ID crescente, sem chamada filtrada ou interação extra. |
| `listProductsShouldForwardExternallyTrimmedNameWithLiteralInterior` | 8 | Tab, NBSP e U+2003 externos isolados ou combinados removidos; um/dois espaços comuns, NBSP e tab internos preservados literalmente no argumento de `findPageByName`; PageRequest 1/2 sem Sort adicional e nenhuma interação extra. |

O primeiro conjunto usa as mesmas cinco entradas de ausência/vazio/espaços do método administrativo `shouldChooseUnrestrictedQueryForAbsentEmptyOrUnicodeWhitespace`, lido em [AdminQueryServicesTest.java](../../src/test/java/com/devsuperior/dscommerce/services/AdminQueryServicesTest.java). As expectativas preenchidas mantêm a semântica do método administrativo de remoção exclusiva de extremos, com pares de entrada/saída explícitos; não calculam o esperado usando `normalize`, trim ou consulta de produção. Caixa original de `FONE` e caracteres internos são encaminhados como escritos no manifesto.

Todos os casos verificam DTO e metadados concretos: ramo sem filtro preserva produto ID 7, nome `" X "`, imagem, preço `0.00` e totais 3/2; ramo preenchido preserva produto ID 42, nome original com dois espaços de cada lado, imagem, preço `123.4500` e totais 5/3. Nome e preço do objeto original permanecem iguais depois da chamada. `verifyNoMoreInteractions` restringe o acesso ao repositório à operação de leitura esperada. Esses dados são objetos de teste em memória e páginas simuladas, não produtos selecionados por uma consulta real.

O diff contém somente acréscimos; todos os métodos/assertions de serviço anteriores foram mantidos. Os outros **166 fontes rastreados** conservaram seus hashes, incluindo toda produção, `AdminQueryServicesTest.java` e o grupo HTTP público completo com T013/T014/US1. Nenhuma alteração administrativa, de normalizador, DTO, segurança ou validação de escrita; nenhum teste aprovado foi enfraquecido.

### Execução necessária e resultado observado

Uma única execução Maven sequencial, após conferir ausência de outro Maven/Surefire ativo e sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=ProductCatalogServiceTest" "-Dgroups=feature006-t015" "-Dsurefire.reportNameSuffix=feature006-t015-service-spaces" test
```

O filtro de tag selecionou somente os dois métodos novos. O XML confirma cinco invocações do primeiro e oito do segundo; não se executaram os nove casos anteriores da classe, testes administrativos, T013/T014, validação de US1, baseline, grupo HTTP ou suíte completa. Não houve ampliação de regressão porque produção e fontes dos demais testes permaneceram intactos. O checkpoint conjunto de US2 continua reservado à T016.

| Seleção Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| ProductCatalogServiceTest — somente os dois métodos T015 | 13 | 0 | 0 | 0 |

**BUILD SUCCESS, exit code 0**, na primeira execução. Console, um XML e um TXT confrontados; seleção exata e ausência de elementos de falha/erro/skip confirmadas. **GREEN de comportamento já existente**, sem RED funcional ou artificial, sem falha de preparação observada e sem mudança produtiva. As assertions foram escritas antes da execução e não alteradas para obter aprovação.

Comando na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t015-service-spaces-20261019T071122/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`; compilação release 21 e Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T07:11:44.1193553-03:00**, fim **07:12:32.3981325-03:00**, duração externa **48,279 s**, Maven **42.972 s**. Produção atualizada sem recompilação; **91 fontes de teste recompilados**, sem erro. Sem comando separado de compilação. Warnings do agente Byte Buddy/CDS no console não causaram falha e não foram tratados como RED.

### Tipos de evidência, preservação e limites

- **Testes atuais**: treze invocações unitárias dos dois métodos, com serviço real e persistência simulada. Provam escolha de ramo, texto encaminhado, paginação e mapeamento/conservação dos objetos de teste.
- **HTTP atual**: zero consultas, servidores ou testes HTTP executados. T013/T014 e US1 permanecem evidências históricas preservadas; suas contagens não foram somadas ao resultado atual nem apresentadas como execução desta tarefa.
- **Verificações documentais/estáticas**: leitura de artefatos, comparação de fontes/hashes, preservação dos relatórios, seleção XML/console, links, marcadores e whitespace. Não são testes adicionais de aplicação.

Os **292 relatórios anteriores** foram copiados antes do Maven para [surefire-before](../../target/feature006-t015-service-spaces-20261019T071122/surefire-before/), com [manifesto SHA-256](../../target/feature006-t015-service-spaces-20261019T071122/surefire-before-manifest.json). Após executar, todos conservaram tamanho/hash na origem e na cópia, inclusive T013/T014 e RED/GREEN de US1. Os dois relatórios novos estão em [surefire-current](../../target/feature006-t015-service-spaces-20261019T071122/surefire-current/); nenhum anterior foi sobrescrito.

Evidências locais adicionais: [run-start.json](../../target/feature006-t015-service-spaces-20261019T071122/run-start.json), [run-result.json](../../target/feature006-t015-service-spaces-20261019T071122/run-result.json) e [verification.json](../../target/feature006-t015-service-spaces-20261019T071122/verification.json). Artefatos em `target/` são ignorados e podem faltar em outro checkout. Histórico deste quickstart e casos/evidências recebidos foram preservados integralmente.

**Conclusão T015**: critérios dos testes de decisão do serviço atendidos; T001–T015 `[X]`, T016–T030 abertas. Nenhum impedimento remanescente. Limites: a seleção atual não comprova filtragem SQL/HTTP, ausência de DML ou snapshot de banco, proxies/transações reais, integração pública/administrativa conjunta, CI ou suíte completa. Não encerra o checkpoint de US2 nem aprova a feature integral. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff do serviço e diff documental incremental revisados, provider/tag e suas referências conferidos, novos artefatos locais examinados; `git diff --check` exit 0, sem problemas de whitespace, e 151 links locais dos dois documentos resolvidos. Auditoria confirmou somente T015 alterada nas tasks recebidas, histórico do quickstart preservado e nenhum arquivo novo rastreável. Git final: quatro arquivos modificados — os três recebidos mais `ProductCatalogServiceTest.java` —, nenhum staged ou não rastreado. O grupo HTTP recebido permanece byte a byte igual; raiz/branch/HEAD e main mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T016 — checkpoint conjunto de US2

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `529aefd18b0e5afaedb6c849f88cb6541f48273c`. Git confirmou somente quickstart, tasks, `PublicCatalogNameFilterIntegrationTest.java` e `ProductCatalogServiceTest.java` modificados, nenhum staged ou não rastreado. Cópias dos quatro arquivos e hashes de todos os 167 arquivos rastreados de `src/` preservados antes da execução. T001–T015 `[X]`; lidas integralmente T016, dependência T015 e critérios, com os seis cenários US2 e decisões correspondentes conferidos. Nenhum fonte, fixture ou assertion foi editado nesta tarefa.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Seleção conferida antes da execução

A conferência estática encontrou as classes aninhadas compiladas `RootContext` e `ApiContext`, seus context paths, os dois métodos herdados e providers/tags de T013/T014. O [manifesto prévio de seleção](../../target/feature006-t016-us2-checkpoint-20261019T071700/selection-preflight.json) fixa **44 testes HTTP e 22 unitários**, em duas seleções complementares planejadas antes de executar. HTTP seleciona a classe externa isoladamente com as duas tags de US2; os seletores de método dos unitários ficam em outra execução. Isso evita a combinação de seleção externa HTTP com seletores de método que omitiu os aninhados na T012, sem alterar fontes ou configurações para descobrir testes.

O checkpoint exige reexercitar conjuntamente os casos US2 de T013/T014, os treze casos públicos de serviço de T015 e os nove casos dos dois métodos administrativos de normalização. Essa é a necessidade concreta desta execução; não se repetiram baseline, validação de US1, grupo HTTP completo, testes de detalhe/jornadas ou suíte completa. Não foram incluídos métodos anteriores de serviço alheios ao checkpoint, nem toda a classe administrativa. Maven sempre sequencial, ausência de outro Maven/Surefire ativo conferida e sufixos novos previamente livres.

**Execução 1 — HTTP público com persistência real**:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t013|feature006-t014" "-Dsurefire.reportNameSuffix=feature006-t016-us2-http" test
```

Console e Surefire foram conferidos antes de iniciar os unitários: **22 casos em raiz e 22 em `/api`**, cada contexto com 19 de `normalizesOnlyExternalFilterWhitespace` e três de `preservesInternalSpacesAndStoredNames`. O contêiner externo tem zero testes. Seleção completa, **44 aprovados, zero falhas/erros/ignorados; BUILD SUCCESS, exit code 0**. Nenhuma observação de T003–T006/US1 foi produzida.

**Execução 2 — decisões dos serviços público e administrativo**:

```powershell
.\mvnw.cmd "-Dtest=ProductCatalogServiceTest#listProductsShouldUseUnfilteredQueryForAbsentOrUnicodeWhitespaceName+listProductsShouldForwardExternallyTrimmedNameWithLiteralInterior,AdminQueryServicesTest#shouldChooseUnrestrictedQueryForAbsentEmptyOrUnicodeWhitespace+shouldTrimOnlyRecognizedExternalCodePointsAndKeepLiteralInterior" "-Dsurefire.reportNameSuffix=feature006-t016-us2-services" test
```

O XML confirma somente os quatro métodos: cinco + oito casos públicos e cinco + quatro administrativos. **22 aprovados, zero falhas/erros/ignorados; BUILD SUCCESS, exit code 0**. Nenhuma chamada HTTP nesta seleção. Os relatórios HTTP já aprovados foram preservados antes desta segunda execução.

| Execução e seleção efetiva | Testes | Falhas | Erros | Ignorados | Exit code |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 — PublicCatalogNameFilterIntegrationTest$ApiContext | 22 | 0 | 0 | 0 | 0 |
| 1 — PublicCatalogNameFilterIntegrationTest$RootContext | 22 | 0 | 0 | 0 | 0 |
| 2 — ProductCatalogServiceTest, dois métodos T015 | 13 | 0 | 0 | 0 | 0 |
| 2 — AdminQueryServicesTest, dois métodos de normalização | 9 | 0 | 0 | 0 | 0 |
| **Agregado de duas execuções complementares** | **66** | **0** | **0** | **0** | **0 em ambas** |

**66 é o agregado das duas seleções disjuntas na mesma revisão de trabalho, não um resumo único de console Maven.** Foram examinados cinco XMLs e cinco TXTs contra os dois consoles, nomes de classe/método e casos efetivos. Nenhuma omissão, complemento corretivo, falha funcional ou problema de preparação observado. Tudo passou na primeira tentativa de cada seleção; **GREEN existente**, sem RED artificial ou correção produtiva. Todos os 167 arquivos de `src/` mantiveram seus hashes antes, entre e depois das execuções.

### Cobertura e ausência de mutações: provas distintas

**HTTP com H2 real**: 44 testes geraram **50 requisições** (38 de T013 e 12 de T014), 25 em cada contexto. Perfil test, filtros reais, bancos isolados sem seeds, OSIV local desativado, fixtures JDBC commitadas e nenhum chamador transacional externo. Os corpos completos coincidem com os esperados independentes: IDs capturados, nome original, imagem, preço numérico e metadados. Não se usou apenas 200 ou igualdade entre respostas para aprovar seleção.

US2.1/US2.2/US2.5: os filtros preenchidos, com espaços externos comuns/tab/NBSP/U+2003 isolados ou combinados, selecionam Fone Azul com page 0/size 1/totais 1/1. Ausente/vazio/somente espaços retorna Cabo na mesma primeira página sem filtro, com totais 2/2. US2.3/US2.4/US2.6: um/dois espaços internos e NBSP selecionam somente a forma correspondente em page 0/size 12, totais 1/1; o legado `"  Fone  Azul  "` conserva todo o texto e não corresponde a `fone azul`, cujo resultado é vazio com totais 0/0.

As 50 observações têm parâmetro decodificado e guards válidos, snapshot independente igual após o GET e antes do cleanup, somente SELECTs Hibernate em transação na janela observada, zero DML observado e ausência de transação externa. Fixtures independentes e cleanup por ownership passaram. Oráculos semânticos de nomes/corpos/metadados foram também confrontados com as evidências anteriores T013/T014, mantendo o esperado; somente IDs entre execuções foram desconsiderados nessa comparação auxiliar. Os próprios testes continuam exigindo os IDs capturados de cada fixture.

**Unitários com mocks**: os treze casos públicos comprovam ramo, argumento normalizado, espaços internos literais, PageRequest/Sort, DTO e objetos originais preservados. Os nove administrativos usam serviço e guarda reais, mas resolução de conta e repositórios simulados; conferem normalização já existente, DTO/metadados e resolução da conta antes da consulta, sem interações extras. Incluem texto decomposto, caractere suplementar e U+200B não removível. Não constituem prova de seleção SQL, autenticação HTTP, ausência de DML real ou transação de proxy. Essas limitações não foram ocultadas pela agregação com HTTP.

### Horários, relatórios e preservação

Ambos os comandos executados na raiz via `cmd.exe /d /c`, stdout/stderr preservados. Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` observado no console HTTP; Wrapper configurado para Maven `3.9.16`. Produção e testes atualizados em ambas as execuções, **sem recompilação nova** e sem comando separado de compilação.

- HTTP: **2026-10-19T07:17:24.7032840-03:00** a **07:18:04.8081658-03:00**, duração externa **40,105 s**, Maven **34.946 s**; [console-http.log](../../target/feature006-t016-us2-checkpoint-20261019T071700/console-http.log), [run-http-start.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/run-http-start.json) e [run-http-result.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/run-http-result.json).
- Serviços: **2026-10-19T07:18:39.8495450-03:00** a **07:18:57.0512302-03:00**, duração externa **17,202 s**, Maven **12.135 s**; [console-services.log](../../target/feature006-t016-us2-checkpoint-20261019T071700/console-services.log), [run-services-start.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/run-services-start.json) e [run-services-result.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/run-services-result.json).

Antes da seleção HTTP, os **294 relatórios anteriores** foram copiados para [surefire-before-http](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-before-http/) e conferidos pelo [manifesto SHA-256 HTTP](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-before-http-manifest.json). Antes dos unitários, os **300 então presentes**, incluindo os seis arquivos do HTTP atual, foram preservados em [surefire-before-services](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-before-services/) e seu [manifesto](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-before-services-manifest.json). Após executar, origem e cópias mantiveram todos os tamanhos/hashes, inclusive RED/GREEN históricos de US1 e T013–T015. Nenhum relatório anterior sobrescrito.

Os dez relatórios novos estão em [surefire-current-http](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-current-http/) e [surefire-current-services](../../target/feature006-t016-us2-checkpoint-20261019T071700/surefire-current-services/). [http-observations.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/http-observations.json) guarda as 50 observações e [verification.json](../../target/feature006-t016-us2-checkpoint-20261019T071700/verification.json) registra seleção exata, agregação, ausência de omissão, comparação com oráculos anteriores e preservação dos fontes/relatórios. Artefatos locais ignorados em `target/`, que podem faltar em outro checkout; conferências documentais/estáticas não são testes adicionais.

**Conclusão T016**: checkpoint conjunto US2 GREEN nos seis cenários e regressões unitárias afetadas, com ausência de mutações observada nas janelas HTTP e nomes originais preservados. Nenhuma correção necessária; produção e assertions intactas. T001–T016 `[X]`; T017–T030 abertas, sem iniciar US3 ou encerrar a feature completa. Limites: Windows/H2 configurado, variantes/páginas dos casos US2 e janelas síncronas; sem prova de outros bancos, CI, durabilidade, efeitos fora da janela, JDBC direto universal ou suíte completa. Sem impedimento remanescente, stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff documental incremental contra as cópias recebidas, referências dos seletores, relatórios e novos artefatos locais revisados; `git diff --check` exit 0, sem problemas de whitespace, e 166 links locais dos dois documentos resolvidos. Auditoria confirmou somente T016 alterada nas tasks recebidas, conteúdo anterior do quickstart preservado e todos os fontes/testes recebidos idênticos. Git final: os mesmos quatro arquivos rastreados modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. Alterações desta tarefa somente no quickstart e marcador T016; extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T017 — paginação filtrada e desempate por ID

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `b5c0a241806acb437ba10a80b7174b5cc35fd72d`, Git limpo, nenhum staged ou não rastreado. T001–T016 `[X]`; lidas integralmente T017 e suas condições, dependência T016 e cenários US3.1/US3.2, com as decisões correspondentes da spec/plano/pesquisa/modelo/contrato conferidas. Escopo exclusivo de teste T017, sem iniciar T018+; cópias e hashes recebidos preservados antes de editar.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero abertos; ignores existentes suficientes. `.specify/extensions.yml` ausente antes/depois, sem hooks aplicáveis.

### Teste acrescentado e resultados concretos

Acrescentado somente `paginatesFilteredResultsInStoredNameIdOrder`, com tag `feature006-t017`, em [PublicCatalogNameFilterIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java). O mesmo método é herdado pelos contextos aninhados reais raiz e `/api`, com perfil test, filtros reais, H2 isolado sem seeds e OSIV local desativado. Reutiliza `prepareV03()` de [AdminProductTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java), sem alterar esse suporte ou seus defaults.

A V03 prepara e commita seis produtos em ordem de inserção diferente da ordem pública: Telefone D, Fone C, dois Fone B, Fone A e Cabo. Fone A tem duas associações com categorias distintas; os IDs, nomes, categorias e pares são conferidos por snapshot independente antes do HTTP. O oráculo é o `filteredOrder` do manifesto de inserção, com nomes esperados fixos e IDs capturados; não é calculado por consulta de produção. O teste confere também que os dois IDs de Fone B no manifesto estão em ordem crescente.

| Página, sempre `name=fone&size=2` | Conteúdo exato esperado e observado | Quantidade | totalElements / totalPages |
| --- | --- | ---: | --- |
| 0 | Fone A; Fone B com menor ID | 2 | 5 / 3 |
| 1 | Fone B com maior ID; Fone C | 2 | 5 / 3 |
| 2 | Telefone D | 1 | 5 / 3 |

Cada resposta é comparada ao **JSON integral esperado**, incluindo IDs capturados, nomes originais, imagem, preço numérico e todos os metadados; HTTP 200 isolado não aprova seleção. A união das três páginas deve coincidir exatamente com os cinco IDs do manifesto e não conter duplicação. Isso exclui Cabo, mantém Fone A uma única vez apesar das duas categorias e verifica o desempate de Fone B atravessando a fronteira entre páginas 0 e 1. Nesta execução, ambos os contextos produziram a sequência capturada `[5,3,4,2,1]`, com empate `[3,4]`; esses números são observações locais, não constantes fixadas no teste.

Nas seis chamadas, filtro decodificado igual a `fone`, query/rota/contexto corretos, snapshots independentes iguais após cada GET e antes da limpeza, somente SELECTs Hibernate em transação na janela observada e ausência de transação externa do chamador. Nenhum número rígido de SELECT/count é exigido. Cleanup por ownership e banco vazio conferidos antes das assertions funcionais finais; preparação e limpeza não integram a janela de leitura.

O diff do fonte contém uma única inserção: todos os testes, helpers e assertions anteriores de US1/US2 foram mantidos. Os outros **166 arquivos rastreados de `src/`** conservaram seus hashes, incluindo toda produção e suportes de fixture. Nenhuma alteração produtiva, de consulta/count/ordem, segurança ou representação foi necessária.

### Seleção executada e resultado

Uma única execução Maven sequencial pelo Wrapper, após conferir ausência de outro Maven/Surefire ativo e sufixo novo previamente livre:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t017" "-Dsurefire.reportNameSuffix=feature006-t017-pagination" test
```

A seleção usa a classe externa isoladamente e somente a tag do novo método, mantendo a descoberta dos contextos aninhados. Console, três XMLs e três TXTs confirmaram um caso em raiz e um em `/api`; o contêiner externo tem zero testes. Nenhum método anterior da classe, checkpoint US1/US2, baseline, serviço/repositório ou suíte completa foi reexecutado. As seis requisições são três páginas em cada um dos dois casos de teste, não seis testes distintos.

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total da única execução** | **2** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**, na primeira execução, sem elementos de falha/erro/skip. As seis observações HTTP têm corpos e metadados exatos; a conferência adicional dos relatórios/observações confirmou 2/2/1, união exata, ausência de duplicação e os dois IDs empatados crescentes. **GREEN de comportamento já existente**, sem fabricar RED, alterar produção ou enfraquecer assertions. Compilação/preparação sem falhas observadas; nenhum impedimento remanescente.

Comando na raiz via `cmd.exe /d /c`, stdout/stderr em [console.log](../../target/feature006-t017-pagination-20261019T073140/console.log). Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T07:31:57.9696365-03:00**, fim **07:33:06.8042524-03:00**, duração externa **68,835 s**, Maven **01:03 min**. Produção atualizada sem recompilação; **91 fontes de teste recompilados**, release 21, sem erro. Sem comando separado de compilação.

### Preservação, evidências e limites

Os **304 relatórios anteriores**, inclusive RED/GREEN de US1 e evidências de US2, foram copiados antes do Maven para [surefire-before](../../target/feature006-t017-pagination-20261019T073140/surefire-before/), com [manifesto SHA-256](../../target/feature006-t017-pagination-20261019T073140/surefire-before-manifest.json). Após executar, origem e cópia mantiveram todos os tamanhos/hashes. Os seis relatórios novos estão em [surefire-current](../../target/feature006-t017-pagination-20261019T073140/surefire-current/); nenhum relatório anterior sobrescrito.

Evidências locais adicionais: [run-start.json](../../target/feature006-t017-pagination-20261019T073140/run-start.json), [run-result.json](../../target/feature006-t017-pagination-20261019T073140/run-result.json), [observações HTTP](../../target/feature006-t017-pagination-20261019T073140/http-observations.json) e [verification.json](../../target/feature006-t017-pagination-20261019T073140/verification.json). Registram seleção efetiva, navegação/IDs/nomes/metadados, fontes executados e preservação dos testes/relatórios recebidos. Artefatos em `target/` são locais, ignorados e podem faltar em outro checkout. Conferências documentais/estáticas não contam como testes adicionais.

**Conclusão T017**: critérios US3.1/US3.2 atendidos; T001–T017 `[X]`, T018–T030 abertas. Isso não encerra US3: páginas vazias/banco vazio, independência entre solicitações e checkpoint conjunto permanecem T018/T019/T020. Alcance: HTTP real Windows/H2, V03 estável, filtro `fone`, páginas 0/1/2 tamanho 2, sem credenciais e janelas síncronas observadas; não prova outros bancos, mutações concorrentes, CI, durabilidade, JDBC direto universal, efeitos fora da janela ou suíte completa. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff completo dos três arquivos, referências de método/tag/fixture e novos artefatos locais revisados; `git diff --check` exit 0, sem problemas de whitespace, e 176 links locais dos dois documentos resolvidos. Auditoria confirmou somente T017 alterada nas tasks recebidas, conteúdo anterior do quickstart e testes US1/US2 preservados, sem arquivo novo rastreável. Git final: somente quickstart, tasks e `PublicCatalogNameFilterIntegrationTest.java` modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. Extensões ausentes na conferência pós-execução, sem hooks aplicáveis.

## Execução da T018 — páginas vazias e metadados coerentes

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `b5c0a241806acb437ba10a80b7174b5cc35fd72d`. Confirmados por Git somente `PublicCatalogNameFilterIntegrationTest.java`, este quickstart e tasks modificados, nenhum staged ou não rastreado. As alterações recebidas da T017 foram preservadas. T001–T017 estavam `[X]`; lidas T018, sua dependência T017 e condições, cenários US3.3–US3.5 e regras correspondentes da spec, plano, pesquisa, modelo e contrato. Escopo exclusivo de testes T018, sem produção ou antecipação de T019+.

Pré-requisitos: `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` inicialmente retornou a Feature 005 pelo seletor local, sem definir o escopo deste pedido. Reexecutado por `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e os cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. `.specify/extensions.yml` ausente na conferência inicial; conferência pós-execução registrada abaixo.

### Casos acrescentados e resultados concretos

Acrescentado `returnsCoherentEmptyFilteredPages`, parametrizado por `emptyPageScenarios`, com tag exclusiva `feature006-t018`, ao [grupo HTTP existente](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java). Sete variantes herdadas por raiz e `/api`, em contextos servlet reais distintos, perfil test, filtros reais, H2 isolado sem seeds e OSIV local desativado. Cada invocação começa e termina com banco vazio e usa fixture própria; nenhum caso depende de pesquisa anterior. Isso não implementa a sequência com catálogo preenchido da T019.

| Cenário | Preparação e filtro | page / size enviados e observados | totalElements / totalPages observados |
| --- | --- | --- | --- |
| US3.3 | V03, `name=inexistente` | 0 / 2 | 0 / 0 |
| US3.3, página não zero | V03, `name=inexistente` | 2 / 2 | 0 / 0 |
| US3.4 | V03, `name=fone`, além das cinco correspondências | 3 / 2 | 5 / 3 |
| US3.5 | Banco vazio, `name=fone` | 0 / 2 | 0 / 0 |
| US3.5, página não zero | Banco vazio, `name=fone` | 2 / 2 | 0 / 0 |
| US3.5 | Banco vazio, `name` omitido | 0 / 2 | 0 / 0 |
| US3.5, página não zero | Banco vazio, `name` omitido | 2 / 2 | 0 / 0 |

**As sete linhas passaram nos dois contextos**: todas retornaram HTTP 200/JSON e `content=[]`, com os cinco campos exatos e valores numéricos de paginação/totais comparados ao JSON integral esperado. A página 3 permaneceu 3 e conservou totais 5/3; nenhuma correspondência e banco vazio conservaram totais 0/0 inclusive em página 2. Sem clamp, 404 ou substituição por outra página.

Nos casos preenchidos, `prepareV03()` commitou seis produtos (Telefone D, Fone C, dois Fone B, Fone A e Cabo), com cinco correspondências conhecidas para `fone` e duas associações de categorias. IDs capturados, nomes, categorias e pares foram conferidos em snapshot por conexão independente antes do GET; os totais esperados são constantes do cenário, sem consulta produtiva como oráculo. Nos casos vazios não se inseriu produto ou seed global.

Em cada uma das 14 janelas de GET: query bruta, `name` decodificado (inclusive ausência), rota e context path conferidos; snapshot independente após retorno e antes do cleanup igual ao anterior; somente SELECTs Hibernate em transação observados, zero DML observado e nenhuma transação externa do chamador. Não se fixa quantidade de SELECT/count. Limpeza por ownership e banco vazio após cada caso aprovados. Os nomes/IDs de dados da T017 não são reutilizados como estado entre testes.

### Única seleção Maven executada

Conferidos ausência de outro processo Java/Maven ativo e sufixo livre; execução sequencial pelo Wrapper, na raiz via `cmd.exe /d /c`, com stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t018" "-Dsurefire.reportNameSuffix=feature006-t018-empty-pages" test
```

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 7 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 7 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total da única execução** | **14** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**, primeira execução. **14 invocações JUnit e 14 requisições HTTP**, contagens distintas que coincidem porque cada teste novo faz exatamente um GET (sete por contexto). Três XMLs e três TXTs comparados ao console; todos os testcases pertencem somente a `returnsCoherentEmptyFilteredPages`, sem falha/erro/skip ou omissão de contexto/variante. A seleção não repetiu T017, US1/US2, baseline, serviços, checkpoints anteriores ou suíte completa. GREEN de comportamento existente, sem RED artificial, correção produtiva ou enfraquecimento de assertions.

Ambiente observado: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T10:57:24.0685793-03:00**, fim **10:59:05.3279185-03:00**, duração externa **101,259 s**, Maven **01:31 min**. Produção atualizada sem recompilação; **91 fontes de teste recompilados**, release 21, sem erro. Sem comando separado de compilação ou execução de versão do Maven.

### Preservação, conferências e limites

Antes de editar, guardadas cópias dos três arquivos recebidos e hashes dos 257 arquivos rastreados. A remoção em memória somente das duas inserções da T018 reproduziu exatamente o fonte recebido, preservando T017 e todos os casos/assertions US1/US2. Os outros **254 arquivos rastreados** conservaram hashes, incluindo toda produção, fixtures, configuração e artefatos aprovados.

Os **310 relatórios Surefire anteriores** foram copiados antes do Maven para [surefire-before](../../target/feature006-t018-empty-pages-20261019T105700/surefire-before/) com [manifesto de tamanho/SHA-256](../../target/feature006-t018-empty-pages-20261019T105700/surefire-before-manifest.json). Após executar, origem e cópia mantiveram todos os tamanhos/hashes. Os seis novos relatórios estão em [surefire-current](../../target/feature006-t018-empty-pages-20261019T105700/surefire-current/); nenhum relatório anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t018-empty-pages-20261019T105700/console.log), [run-start.json](../../target/feature006-t018-empty-pages-20261019T105700/run-start.json), [run-result.json](../../target/feature006-t018-empty-pages-20261019T105700/run-result.json), [14 observações HTTP](../../target/feature006-t018-empty-pages-20261019T105700/http-observations.json) e [verification.json](../../target/feature006-t018-empty-pages-20261019T105700/verification.json). A conferência adicional comparou todas as sete combinações por contexto a um manifesto fixo de página/tamanho/totais, query/rota e guards, além dos XMLs e preservação de relatórios/fontes. Comando auxiliar `& target/feature006-t018-empty-pages-20261019T105700/verify.ps1`: primeira tentativa exit 1 por tratar o array JSON do manifesto como um único objeto no PowerShell 5.1; corrigida a enumeração no script local, nova conferência exit 0. Isso foi falha do verificador auxiliar, sem falha de teste, edição de fonte ou repetição Maven. Artefatos em `target/` podem faltar em outro checkout; conferências estáticas não contam como testes adicionais.

**Conclusão T018**: US3.3–US3.5 atendidos nos contextos previstos, sem impedimento remanescente; somente T018 passa a `[X]`, preservando T001–T017 `[X]` e T019–T030 abertas. Não encerra US3, a feature ou seus gates finais. Limites: Windows/H2 configurado, V03 estável e banco vazio, variantes/páginas descritas, sem credenciais e janelas síncronas; não prova outros bancos, concorrência, CI, durabilidade, JDBC direto universal, efeitos fora da janela ou suíte completa. Nenhum stage, commit, push ou troca de branch.

Conferência final: diff completo dos três arquivos e incremento contra as cópias recebidas revisados, método/tag/provider/fixture e 185 links locais dos dois documentos conferidos. `git diff --check` exit 0, sem problemas de whitespace; nenhum arquivo novo rastreável. Histórico do quickstart preservado integralmente; nas tasks recebidas mudou somente T018. Fonte executado e testes anteriores preservados, demais 254 hashes rastreados iguais. Git final: somente os mesmos três arquivos modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T019 — ausência de retenção do filtro entre consultas

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `b5c0a241806acb437ba10a80b7174b5cc35fd72d`. Confirmados por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short --untracked-files=all` e `git diff --cached --name-only`: somente `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. T001–T018 `[X]`; lidas integralmente T019, dependência T018 e condições, US3.6 e regras correspondentes de FR-005 e dos artefatos aprovados, já lidos nesta sessão. Preservados casos e evidências T017/T018 e US1/US2; escopo exclusivo da T019, sem iniciar T020+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e os cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 preservado `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. Extensões ausentes na conferência inicial, sem hook prévio aplicável.

### Sequência comprovada e oráculos independentes

Acrescentado somente `doesNotRetainNameBetweenRequests`, tag `feature006-t019`, ao [grupo HTTP existente](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java). O método é herdado por raiz e `/api`, com HTTP servlet real, filtros reais, perfil test, H2 isolado sem seeds e OSIV local desativado. Cada contexto prepara sua V03 commitada e usa o mesmo `TestRestTemplate`, servidor, contexto e dados nos dois GETs consecutivos; não há limpeza, recriação do contexto ou mudança da fixture entre as requisições.

| Ordem no mesmo contexto | Query enviada | Conteúdo esperado e observado | page / size | totalElements / totalPages |
| --- | --- | --- | --- | --- |
| 1 — pesquisa restritiva | `name=fone&page=0&size=2` | Fone A; Fone B de menor ID | 0 / 2 | 5 / 3 |
| 2 — parâmetro omitido após a pesquisa | `page=0&size=2` | Cabo; Fone A | 0 / 2 | 6 / 3 |

**As duas etapas passaram em ambos os contextos**. Cada resposta HTTP 200/JSON foi comparada ao corpo integral esperado: cinco campos da página, IDs capturados, nomes originais, imagem e preço numérico. A primeira comparação comprova a restrição antes da segunda chamada. A segunda exige a página do catálogo completo, com Cabo antes de Fone A e total 6; não apenas igualdade/diferença entre respostas. A observação servlet passiva confirmou query bruta, `name=fone` na primeira e `name` efetivamente ausente (`null`) na segunda, além de `/products` ou `/api/products` conforme o contexto.

Os dois esperados vêm dos manifestos de inserção `filteredOrder` e `unfilteredOrder` de `prepareV03()`, nunca da consulta produtiva ou da primeira resposta. O manifesto completo tem seis IDs únicos, com nomes conferidos como Cabo/Fone A/Fone B/Fone B/Fone C/Telefone D; a ordem filtrada corresponde aos cinco itens após Cabo. Snapshot independente antes do HTTP conferiu os IDs/nomes inseridos, categorias e dois pares de associação. Nesta execução, em cada contexto, os IDs completos capturados foram `[6,5,3,4,2,1]`, primeira página filtrada `[5,3]` e segunda página sem filtro `[6,5]`; são observações locais, não constantes do teste.

Após cada GET e antes de qualquer cleanup, snapshot independente igual ao anterior; somente SELECTs Hibernate em transação e zero DML observados na janela da chamada, sem transação externa do chamador. Não se exige quantidade rígida de SELECT/count. Limpeza por ownership somente após a sequência; banco vazio antes da preparação e depois da limpeza. Nenhuma produção, fixture, consulta, segurança ou configuração global foi alterada.

### Única execução Maven e seleção efetiva

Conferidos ausência de outro processo Java/Maven ativo e sufixo previamente livre. Comando na raiz, sequencial pelo Wrapper via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t019" "-Dsurefire.reportNameSuffix=feature006-t019-request-independence" test
```

| Relatório Surefire | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 1 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| **Total da única execução** | **2** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**, primeira execução: **2 testes de integração e 4 requisições HTTP** (dois GETs por teste/contexto); **nenhum teste unitário executado**. Três XMLs e três TXTs conferidos contra console e testcases; somente `doesNotRetainNameBetweenRequests`, um caso em cada contexto, sem omissão ou elementos de falha/erro/skip. Não se repetiram T017/T018, US1/US2, seleções anteriores, checkpoint conjunto T020 ou suíte completa. **GREEN de comportamento existente**, sem RED artificial, correção de produção ou enfraquecimento de assertions.

Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T11:08:16.8865478-03:00**, fim **11:09:32.9525353-03:00**, duração externa **76,066 s**, Maven **01:10 min**. Produção atualizada sem recompilação; 91 fontes de teste recompilados, release 21, sem erro. Sem comando separado de compilação/versão.

### Preservação e limites

Cópias dos três arquivos recebidos e hashes de 257 arquivos rastreados guardados antes da edição. Remover em memória somente a inserção T019 reproduziu exatamente o fonte recebido, preservando todos os casos/helpers/assertions anteriores. Os outros **254 arquivos rastreados** mantiveram hashes, incluindo produção e artefatos aprovados. Histórico do quickstart preservado por acréscimo; nas tasks recebidas muda somente T019.

Os **316 relatórios anteriores** foram copiados e conferidos antes do Maven em [surefire-before](../../target/feature006-t019-request-independence-20261019T110800/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t019-request-independence-20261019T110800/surefire-before-manifest.json). Depois da execução, origem e cópia mantiveram todos os tamanhos/hashes, inclusive T017/T018 e US1/US2. Os seis novos relatórios estão em [surefire-current](../../target/feature006-t019-request-independence-20261019T110800/surefire-current/); nenhum relatório anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t019-request-independence-20261019T110800/console.log), [run-start.json](../../target/feature006-t019-request-independence-20261019T110800/run-start.json), [run-result.json](../../target/feature006-t019-request-independence-20261019T110800/run-result.json), [quatro observações HTTP](../../target/feature006-t019-request-independence-20261019T110800/http-observations.json) e [verification.json](../../target/feature006-t019-request-independence-20261019T110800/verification.json). Conferência auxiliar `& target/feature006-t019-request-independence-20261019T110800/verify.ps1`, exit 0: sequência por contexto, IDs/nomes e metadados contra manifesto, campos/valores, ausência do parâmetro na segunda query, guards, XML/TXT/console e preservação de relatórios/fontes. Essa conferência estática não é novo teste ou execução Maven; `target/` pode faltar em outro checkout.

**Conclusão T019**: US3.6 e os critérios da tarefa atendidos; T001–T019 `[X]`, T020–T030 abertas. Sem pendência funcional remanescente da T019; checkpoint conjunto US3 e fechamento da feature continuam futuros. Alcance: sequência síncrona pública sem credenciais, V03 estável, página 0 tamanho 2 em Windows/H2, raiz e `/api`; não prova outras paginações, concorrência, outros bancos, CI, durabilidade, JDBC direto universal ou efeitos fora das janelas observadas. Sem stage, commit, push ou troca de branch.

Conferência final: diff dos três arquivos, incremento contra as cópias recebidas, referências de método/tag/fixture, novos artefatos locais e 194 links locais dos dois documentos revisados. `git diff --check` exit 0, sem problemas de whitespace. Histórico anterior do quickstart e fonte anterior preservados, somente T019 alterada nas tasks recebidas, demais 254 hashes rastreados iguais. Git final: somente os mesmos três arquivos modificados, nenhum staged ou não rastreado; raiz/branch/HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T020 — validação conjunta de US3

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `b5c0a241806acb437ba10a80b7174b5cc35fd72d`. Conferidos por Git raiz, branch, revisão, status e índice: somente `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. T001–T019 `[X]`; lidas integralmente T020, dependência T019 e critérios, com T017/T018 e US3.1–US3.6 conferidos nas fontes aprovadas já lidas nesta sessão. Escopo exclusivo do checkpoint conjunto US3, sem T021+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`; SHA-256 mantido `AE411ADECBDD12D3B7495C8CC3FA5D944BD7A8214735B0FF399E68C2CB8BADE5`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. Extensões ausentes na conferência inicial, sem hook prévio aplicável.

### Seleção conferida antes da execução

O [manifesto prévio](../../target/feature006-t020-us3-checkpoint-20261019T111300/selection-preflight.json) registra somente as três tags/métodos novos de US3: T017 `paginatesFilteredResultsInStoredNameIdOrder`, T018 `returnsCoherentEmptyFilteredPages` e T019 `doesNotRetainNameBetweenRequests`. Conferidos no fonte os métodos/tags únicos, sete variantes do provider T018 e herança de `ContextControl` pelos dois contextos aninhados `RootContext` e `ApiContext`, com caminhos vazio e `/api`; respectivas classes compiladas presentes. Seleção pela classe externa e expressão de tags, preservando a descoberta dos aninhados, sem misturar seletores de método de outras classes.

Expectativa prévia: **9 testes por contexto (1 + 7 + 1), 18 no total**, com **12 requisições por contexto (3 + 7 + 2), 24 no total**. A necessidade de reexecutar conjuntamente T017–T019 decorre expressamente da T020; não é repetição automática de baselines ou checkpoints anteriores. Nenhum serviço unitário, teste US1/US2, grupo completo ou suíte completa foi selecionado.

### Única execução e resultados efetivos

Conferidos ausência de outro processo Java/Maven ativo e sufixo previamente livre. Comando na raiz, sequencial pelo Wrapper via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t017|feature006-t018|feature006-t019" "-Dsurefire.reportNameSuffix=feature006-t020-us3-checkpoint" test
```

| Método efetivamente executado | Testes raiz | Testes /api | Total de testes | Requisições HTTP |
| --- | ---: | ---: | ---: | ---: |
| paginatesFilteredResultsInStoredNameIdOrder | 1 | 1 | 2 | 6 |
| returnsCoherentEmptyFilteredPages | 7 | 7 | 14 | 14 |
| doesNotRetainNameBetweenRequests | 1 | 1 | 2 | 4 |
| **Total da única execução** | **9** | **9** | **18** | **24** |

**BUILD SUCCESS, exit code 0**, primeira execução, **18 testes de integração, zero falhas/erros/ignorados**. Console e três XMLs/três TXTs Surefire conferidos por classe, método, testcase e resumo: nove casos em cada contexto e zero no contêiner externo; nenhuma omissão, skip ou teste fora da seleção. As 24 requisições são operações dentro desses 18 testes, não 24 testes adicionais. Nenhum teste unitário executado. **Não houve execução complementar**, correção de fonte ou repetição Maven.

Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T11:13:45.4395942-03:00**, fim **11:14:24.8147908-03:00**, duração externa **39,375 s**, Maven **34.012 s**. Produção e testes atualizados, sem recompilação; sem comando separado de compilação/versão.

### Critérios de US3 conferidos conjuntamente

| Cenários | Resultado concreto nos dois contextos |
| --- | --- |
| US3.1–US3.2 | V03 com cinco correspondências: páginas 0/1/2 size2 em 2/2/1, totais 5/3. Nomes Fone A/Fone B/Fone B/Fone C/Telefone D, IDs capturados em ordem exata, sem perdas/duplicações, empate dos Fone B por ID crescente. Produto com duas categorias não duplicado. |
| US3.3 | V03 e trecho `inexistente`: páginas 0 e 2 size2, HTTP 200, conteúdo vazio e totais 0/0; página e tamanho conservados. |
| US3.4 | V03 e `fone`: página 3 size2 vazia, HTTP 200, page 3 preservada e totais 5/3, sem clamp ou 404. |
| US3.5 | Banco vazio com `fone` e com parâmetro omitido, páginas 0 e 2 size2: HTTP 200, vazio e totais 0/0, sem seeds globais. |
| US3.6 | Mesmo contexto/fixture, `fone` seguido de `name` omitido, ambos page0 size2: primeiro Fone A/Fone B, totais 5/3; depois Cabo/Fone A, totais 6/3. Sem reiniciar contexto ou alterar dados entre GETs; segunda resposta esperada pelo manifesto completo independente. |

Todas as respostas foram confrontadas com o JSON integral esperado nos testes (campos, IDs, nomes originais, imagem, preço numérico e metadados), sem usar consulta de produção como oráculo. A conferência adicional das 24 observações auditou as variantes por contexto, queries/rotas, valores/metadados, navegação e união de IDs, empate, páginas vazias e sequência filtrada→omitida. Controles de transporte/decodificação passaram, incluindo ausência efetiva de `name` na segunda consulta T019.

Nas 24 janelas síncronas: snapshots independentes após retorno e antes de cleanup iguais aos anteriores, somente SELECTs Hibernate em transação, zero DML observado e nenhuma transação externa do chamador. Preparação JDBC commitada, fixtures isoladas e limpeza por ownership conferidas pelos testes; não se exige número fixo de SELECT/count. Os IDs podem diferir das execuções isoladas anteriores porque vários casos usam o mesmo contexto com limpeza entre fixtures; as assertions continuam usando IDs capturados de cada preparação, sem números absolutos.

Leitura atual de `ProductCatalogService.listProducts` e `ProductRepository.findPageByName` confirmou paginação no banco por `PageRequest`, predicado literal parametrizado equivalente em seleção/count, ordem `p.name ASC, p.id ASC` sobre o nome armazenado, sem fetch de coleção na consulta paginada ou pós-filtro em memória. O stream do serviço apenas mapeia a página para DTO; transação de leitura mantida. Essa revisão estática complementa as provas HTTP/SQL/snapshots e não é nova execução de teste. Tudo já estava GREEN; produção e asserções foram preservadas, sem fabricar RED.

### Evidências, preservação e limites

Cópias dos três arquivos recebidos e hashes dos 257 arquivos rastreados guardados antes de executar. **Todos os 167 arquivos de `src/` permaneceram byte a byte iguais**, incluindo o grupo HTTP recebido, serviços, repositório, fixtures e testes anteriores. Alterações da T020 somente neste registro e no marcador da task; os outros **255 arquivos rastreados** mantiveram hashes.

Os **322 relatórios anteriores** foram copiados e conferidos antes do Maven em [surefire-before](../../target/feature006-t020-us3-checkpoint-20261019T111300/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t020-us3-checkpoint-20261019T111300/surefire-before-manifest.json). Depois da execução, origem e cópia mantiveram todos os tamanhos/hashes, incluindo evidências isoladas T017–T019 e US1/US2. Seis novos relatórios em [surefire-current](../../target/feature006-t020-us3-checkpoint-20261019T111300/surefire-current/); nenhum anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t020-us3-checkpoint-20261019T111300/console.log), [run-start.json](../../target/feature006-t020-us3-checkpoint-20261019T111300/run-start.json), [run-result.json](../../target/feature006-t020-us3-checkpoint-20261019T111300/run-result.json), [24 observações HTTP](../../target/feature006-t020-us3-checkpoint-20261019T111300/http-observations.json) e [verification.json](../../target/feature006-t020-us3-checkpoint-20261019T111300/verification.json). Comando auxiliar `& target/feature006-t020-us3-checkpoint-20261019T111300/verify.ps1`, exit 0, conferiu seleção exata, XML/TXT/console, seis cenários, preservação de relatórios e fontes. Conferência estática não conta como teste adicional; `target/` pode faltar em outro checkout.

**Conclusão T020**: checkpoint conjunto US3 GREEN nos seis cenários, sem pendência ou falha a corrigir. T001–T020 `[X]`, T021–T030 abertas. Não encerra US4, gates finais ou feature integral. Alcance: Windows/H2 configurado, V03 estável/banco vazio, parâmetros e páginas descritos, sem credenciais, dois contextos reais e janelas síncronas; não prova outros bancos, concorrência, CI, durabilidade, JDBC direto universal, efeitos fora da janela ou suíte completa. Sem stage, commit, push, troca de branch ou execução de T021+.

Conferência final: diff dos três arquivos e incremento documental contra as cópias recebidas revisados, referências de seleção/métodos/tags/fixture e 203 links locais conferidos. `git diff --check` exit 0, sem problemas de whitespace. Histórico do quickstart preservado; nas tasks recebidas mudou somente T020; todo `src/` e demais 255 hashes rastreados mantidos. Git final: somente os mesmos três arquivos modificados, sendo o fonte HTTP alteração recebida e intacta nesta tarefa; nenhum staged ou não rastreado. Raiz/branch/HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T021 — cobertura MVC de binding, paginação e representação

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Conferidos por Git raiz, branch, HEAD e status: árvore limpa, nenhum staged ou não rastreado. T001–T020 `[X]`; lidas integralmente T021, dependência T020 e critérios, US4.1–US4.3 e garantias históricas pertinentes, com os artefatos aprovados já lidos nesta sessão. Escopo exclusivo de teste MVC T021; sem editar o grupo HTTP ou antecipar T022+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. `.specify/extensions.yml` ausente na conferência inicial, sem hook prévio aplicável.

### Garantias históricas e mudança intencional

Conferidos a [especificação histórica 001](../001-consulta-catalogo-produtos/spec.md), FR-001/002/004–008, e seu [contrato público](../001-consulta-catalogo-produtos/contracts/products-openapi.yaml): paginação zero-based, defaults 0/12, size 1–50, rejeição 400, metadados e campos públicos. A ausência de `name` nesse contrato não é regra nova de rejeição do parâmetro. A evolução intencional da 006 é encaminhar `name` ao serviço para aplicar o filtro, já integrado em US1; não se mantém a expectativa anterior de parâmetro ignorado. Defaults, limites, representação e tratamento nativo permanecem preservados.

A caracterização HTTP T004 continua histórica e intacta: limites Bean Validation devolvem quatro campos, enquanto conversão não numérica segue MVC/servlet com corpo nativo. O `anyOf` permissivo do contrato 006 não substitui assertions diretas sobre os quatro campos dos erros de limite. A T021 verifica a fronteira MVC; não redefine o corpo servlet nativo nem altera handler, parser administrativo ou segurança.

### Testes acrescentados e seleção prevista

Acrescentados quatro métodos parametrizados com tag exclusiva `feature006-t021` em [ProductControllerTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/ProductControllerTest.java). Mantidos todos os métodos/assertions anteriores. Reutilizado somente o contexto existente `@WebMvcTest(ProductController.class)` com suas configurações de segurança de slice e `@MockBean ProductCatalogService`, rota `/products`, sem perfil test explícito (console registra perfil default). Não há servidor `RANDOM_PORT`, banco, fixture JDBC ou contexto aninhado `/api` nesta tarefa; esses contextos pertencem à T022.

Cada matriz usa três variantes: `name` ausente (`null` ao serviço), vazio (`""`) e preenchido com espaço/tab/FONE/NBSP/espaço (`" \tFONE\u00a0 "`), preservando o texto recebido no encaminhamento. O [manifesto prévio da seleção](../../target/feature006-t021-mvc-20261019T112600/selection-preflight.json) registra quatro métodos e **39 invocações/dispatches MockMvc esperados**:

| Método | Cobertura nova | Testes / dispatches |
| --- | --- | ---: |
| listProductsShouldBindNameAndIndependentDefaultsWithExactPublicTypes | Seis combinações por variante: ambos omitidos; somente page=1; somente size=2; page vazio/size2; page1/size vazio; ambos vazios. Defaults independentes 0/12; argumento name exato; cinco campos da página e quatro de cada um dos dois itens, tipos JSON e valores, inclusive preço zero. | 18 |
| listProductsShouldAcceptPublicPageSizeBoundsForEachNameVariant | size1 e size50 com page2 por variante; argumentos exatos, página/size sem clamp, corpo vazio e metadados preservados do DTO controlado. | 6 |
| listProductsShouldRejectPublicNumericBoundsBeforeCallingService | page-1, size0 e size51 por variante; 400, quatro campos exatos com tipos, mensagem pública e path; serviço não chamado. | 9 |
| listProductsShouldPreserveNativeConversionRejectionBeforeCallingService | page=abc e size=abc por variante; 400 e MethodArgumentTypeMismatchException com parâmetro, valor e tipo int esperados; serviço não chamado. | 6 |
| **Total** | **24 sucessos e 15 rejeições esperadas** | **39** |

Nos sucessos, Mockito verifica a única chamada `listProducts` com `name/page/size` exatos e nenhuma outra interação; nos erros, zero interação com o serviço. A representação é comparada a DTOs controlados, com nome `"  Fone  Azul  "` mantido, imagem e preços numéricos 123.45/0; isso comprova serialização/encaminhamento, não leitura de nomes persistidos. Os testes de consulta SQL e integração pública de US1–US3 continuam sendo as provas reais já identificadas nos registros anteriores, preservadas sem reexecução. Mocks desta tarefa não são apresentados como prova de filtragem ou contagem no banco.

### Única execução efetiva e resultados

Conferidos ausência de outro processo Java/Maven ativo e sufixo previamente livre. Comando sequencial pelo Wrapper, na raiz via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=ProductControllerTest" "-Dgroups=feature006-t021" "-Dsurefire.reportNameSuffix=feature006-t021-mvc" test
```

**BUILD SUCCESS, exit code 0**, primeira execução: **39 testes MVC de slice, zero falhas/erros/ignorados**. Um XML e um TXT Surefire conferidos contra console e testcases: somente os quatro métodos novos, em 18/6/9/6 invocações, sem omissão. **39 dispatches MockMvc, zero requisições a servidor HTTP**, 24 respostas 200 e 15 respostas 400 esperadas. Não são 39 testes de integração com banco/servidor nem unitários isolados de serviço; controller, binding, validação, serialização e resolvers MVC são reais, serviço simulado. Nenhum teste anterior, checkpoint US1–US3, contexto servlet T022, serviço unitário ou suíte completa foi executado. Sem execução complementar Maven.

Ambiente: Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T11:26:36.7626070-03:00**, fim **11:27:43.3361886-03:00**, duração externa **66,574 s**, Maven **01:00 min**. Produção atualizada sem recompilação; 91 fontes de teste recompilados, release 21, sem erro. Sem comando separado de compilação/versão.

Nos seis erros nativos, o registro MockMvc observado tem status 400, corpo vazio e Content-Type ausente; a exception resolvida é `MethodArgumentTypeMismatchException`. Os testes não exigem esse corpo vazio como contrato nem simulam o dispatch servlet `/error`. O corpo HTTP nativo com timestamp/status/error/path da T004 não é contradito pelo resultado do slice: são mecanismos distintos. Comparação HTTP posterior em raiz e `/api` permanece T022, não executada agora. Os nove erros de limites, por sua vez, foram conferidos diretamente como JSON com status/error/message/path exatos, sem padronização nova.

**GREEN de comportamento existente**, sem RED artificial, alteração produtiva ou enfraquecimento de assertions. A conferência de preservação antes do Maven removeu em memória somente imports/campo/trecho acrescentados e reproduziu o fonte anterior, inclusive todos os testes históricos.

### Evidências e preservação

Antes de editar, guardadas cópias dos três arquivos afetados e hashes dos 257 arquivos rastreados. Os outros **254 arquivos rastreados** conservaram hashes, incluindo toda produção, grupo `PublicCatalogNameFilterIntegrationTest.java`, suportes, demais testes e artefatos aprovados. Preservados implementação, testes e evidências US1–US3. Alterações restritas ao teste MVC, acréscimo deste registro e marcador T021.

Os **328 relatórios anteriores** foram copiados e conferidos antes do Maven em [surefire-before](../../target/feature006-t021-mvc-20261019T112600/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t021-mvc-20261019T112600/surefire-before-manifest.json). Após executar, origem e cópia mantiveram tamanhos/hashes. Dois novos relatórios em [surefire-current](../../target/feature006-t021-mvc-20261019T112600/surefire-current/); nenhum anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t021-mvc-20261019T112600/console.log), [run-start.json](../../target/feature006-t021-mvc-20261019T112600/run-start.json), [run-result.json](../../target/feature006-t021-mvc-20261019T112600/run-result.json), [39 observações MVC](../../target/feature006-t021-mvc-20261019T112600/mvc-observations.json) e [verification.json](../../target/feature006-t021-mvc-20261019T112600/verification.json). Comando auxiliar `& target/feature006-t021-mvc-20261019T112600/verify.ps1`: primeira conferência exit 1 por wildcard ambíguo que confundia a variante de name vazio com labels de paginação vazia; corrigido para selecionar o prefixo exato da variante, nova conferência exit 0. Não houve falha de teste, mudança de fonte ou nova execução Maven por esse ajuste. Conferidos métodos/contagens, 39 combinações, argumentos observados, corpos/campos/tipos/metadados, ramos de erro e preservação de relatórios/fontes. Conferência estática não é teste adicional; artefatos `target/` podem faltar em outro checkout.

**Conclusão T021**: critérios MVC de US4.1–US4.3 atendidos; T001–T021 `[X]`, T022–T030 abertas. Sem impedimento remanescente. Limites: slice MVC único em raiz com serviço simulado, parâmetros já fornecidos ao MockMvc; não prova transporte/codificação HTTP real, `/api`, autenticação completa por servidor, seleção/count SQL, mutação/DML, snapshots, outros bancos, CI ou suíte completa. As evidências anteriores permanecem históricas e não foram reexecutadas ou convertidas em nova aprovação integral da feature. Sem stage, commit, push, troca de branch ou execução de T022+.

Conferência final: diff completo dos três arquivos, incremento contra as cópias recebidas, referências de métodos/providers/tag, artefatos locais novos e 215 links locais revisados. `git diff --check` exit 0, sem problemas de whitespace. Histórico anterior do quickstart e testes existentes preservados; nas tasks mudou somente T021; demais 254 hashes rastreados iguais. Git final: somente `ProductControllerTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. Raiz/branch/HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T022 — paginação, representação e acesso por HTTP real

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Git confirmou somente `ProductControllerTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. T001–T021 `[X]`; lidas integralmente T022, dependência T020 e critérios, US4.1–US4.4 e decisões pertinentes dos artefatos aprovados já lidos nesta sessão. T021 preservada; escopo exclusivo T022, incluindo acesso básico a detalhes de US4.4, sem os casos US4.5–US4.7 da T023 ou tarefas seguintes.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. Extensões ausentes na conferência inicial, sem hook prévio aplicável.

### Preparação e seleção conferidas

Acrescentado `preservesPublicPaginationShapeAndAccess`, tag exclusiva `feature006-t022`, e seu helper de resposta estável ao [grupo HTTP existente](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java). Um teste herdado por cada contexto aninhado real raiz e `/api`, `RANDOM_PORT`, filtros/serviços/persistência reais, perfil test, H2 isolado sem seeds, OSIV local desativado. A remoção em memória somente das inserções/imports novos reproduziu exatamente o fonte recebido, preservando todas as assertions US1–US3 e a caracterização T004.

Cada contexto prepara e commita Cabo/Fone Azul/Fone Preto, IDs capturados, preços 10/20/30, e uma conta CLIENT proprietária da fixture. Um POST HTTP real em `/login` ou `/api/login` prepara o token; o JwtDecoder real confere assinatura/validade e subject correspondente à conta, cujo papel CLIENT é verificado por conexão independente. Não se chamam mocks ou o helper `fixture.login` que despacharia MockMvc. A fixture constrói seu suporte MockMvc existente, mas ele não despacha requisição nesta seleção. Credenciais e tokens não são registrados; os eventos de preparação contêm somente contexto, rota, status e confirmação de identidade.

O [manifesto prévio inicial](../../target/feature006-t022-public-http-20261019T113600/selection-preflight.json) fixou 2 testes, 198 GETs de catálogo e 2 POSTs de login, 200 requisições HTTP, zero dispatches MockMvc. A primeira execução passou, mas a revisão identificou que US4.4 também exigia acesso básico a detalhes. Acrescentado esse controle para as três credenciais e os dois contextos, usando Fone Azul, que corresponde ao filtro. Os casos de produto excluído da busca, 404, categorias repetidas e legado permanecem T023. O [manifesto anterior à execução final](../../target/feature006-t022-public-http-20261019T113600/selection-access-preflight.json) registra **2 testes, 198 GETs de catálogo, 6 GETs de detalhes e 2 POSTs de login, 206 requisições HTTP reais**, zero dispatches MockMvc.

A matriz do catálogo é **11 casos de paginação × 3 variantes de name × 3 acessos × 2 contextos**. `name` omitido e vazio usam o catálogo completo; `name=fone` seleciona os dois Fone, excluindo Cabo. Acesso anônimo, Bearer inválido irrelevante e token válido de consumidor são exercitados em cada combinação.

### Resultados de US4.1–US4.4

| Paginação enviada, nos dois contextos e em todas as variantes de name/acesso | Resultado observado |
| --- | --- |
| Parâmetros omitidos ou `page=&size=` | 200, page0 size12; Cabo/Fone Azul/Fone Preto com totais 3/1 sem filtro, ou os dois Fone com totais 2/1. |
| `page=&size=2` | 200, page0 size2; Cabo/Fone Azul com totais 3/2 sem filtro, ou os dois Fone com totais 2/1. |
| `page=1&size=` | 200, page1 size12, conteúdo vazio; totais 3/1 ou 2/1 conservados, sem clamp. |
| `page=0&size=1` | 200, Cabo e totais 3/3 sem filtro; Fone Azul e totais 2/2 com filtro. |
| `page=0&size=50` | 200, todos os três produtos e totais 3/1 sem filtro; dois Fone e totais 2/1 com filtro. |
| `page=-1&size=12` | 400, ErrorResponse exato com mensagem `page must be greater than or equal to 0`. |
| `page=0&size=0` ou `page=0&size=51` | 400, ErrorResponse exato com mensagem `size must be between 1 and 50`. |
| `page=abc&size=12` ou `page=0&size=abc` | 400 nativo servlet, campos timestamp/status/error/path, sem padronização nova. |

Nos **108 GETs 200**, JSON integral comparado ao manifesto independente de inserção: página somente com content/page/size/totalElements/totalPages; itens somente com id/name/image/price, IDs/metadados integrais, nomes/imagens textuais e preço numérico, valores/ordem/totais exatos. Ausência e vazio equivalem sem filtro; o preenchido aplica a mudança intencional de name. Anônimo/token inválido/token válido produzem a mesma resposta semântica para a mesma consulta, com cabeçalhos relevantes equivalentes, sem desafio de autenticação ou redirecionamento. O token válido é enviado à rota pública cujo fallback preservado não exige autenticação; não se alterou a cadeia para forçar processamento Bearer.

Nos **54 erros de limites**, comparação direta do corpo exato status/error/message/path contra ErrorResponse, incluindo path contextual, sem depender do `anyOf` permissivo de 400. Nos **36 erros nativos**, timestamp textual ISO-8601 validado e normalizado apenas para comparação, com status/error/path e conjunto de campos iguais à [baseline HTTP T004 preservada](../../target/feature006-t004-characterization-20261018T183724/http-observations.json). Os valores voláteis brutos permanecem nas observações; não se exige instante literal igual ao de T004. Essas são respostas servlet reais, distinguindo-as dos seis corpos vazios observados no slice MVC da T021.

O teste contém o oráculo estável transcrito da T004 e asserções diretas de seus cabeçalhos relevantes. A auditoria adicional comparou os 90 erros atuais à baseline original correspondente por contexto/caso: 36 comparações nativas e 54 de limites; os cabeçalhos estáveis de todos os 198 GETs também coincidem com a T004. Cache-Control/Pragma/Expires/X-Content-Type-Options/X-XSS-Protection/X-Frame-Options e Content-Type mantidos; sem Location, WWW-Authenticate ou Vary nesses controles. Date é validado por formato RFC 1123 quando presente; transporte/conexão e instante de Date não são novas garantias de negócio. A cópia local usada na auditoria e o original da T004 conservaram SHA-256 `D1AAE035C586D61EA1024633EED8E605A32FF400E858E9B26DC3917EC3FE5BCA`; não há dependência do teste JUnit em arquivo de `target/`.

Nos seis GETs básicos de detalhes, o produto Fone Azul recebe HTTP 200 com as três credenciais em raiz e `/api`, sem desafio de autenticação ou redirecionamento. Corpo exato com id capturado, name, description, image, price numérico e categories vazio, comparado à fixture; não se usam o produto excluído pelo filtro nem dados legados/404/categorias repetidas da T023.

Após cada GET e antes da limpeza: snapshot independente de produtos/categorias/associações/pedidos/itens igual ao inicial, IDs de contas e papel CLIENT preservados. Nos sucessos, SQL Hibernate somente SELECT em transação; nos 400, nenhum SQL Hibernate observado. Zero DML observado nas 204 janelas GET da execução final e nenhuma transação externa do chamador; não se fixa número de SELECT/count. Preparação de conta/login excluída dessas janelas. Limpeza por ownership e banco vazio antes/depois conferidos. A verificação de conta abrange IDs/papéis; não constitui snapshot de todos os atributos de credenciais.

### Execuções Maven sequenciais e complemento necessário

Conferidos antes de cada comando ausência de outro processo Java/Maven ativo, tag única e contextos herdados, sufixos previamente livres e cópias dos relatórios. Comandos sequenciais pelo Wrapper, na raiz via `cmd.exe /d /c`, stdout/stderr preservados. **Execução 1, cobertura de catálogo**, preservada como evidência parcial da T022:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t022" "-Dsurefire.reportNameSuffix=feature006-t022-public-http" test
```

**Execução 2, validação final após acrescentar acesso básico a detalhes de US4.4**. Foi necessário reexecutar o método alterado completo nos dois contextos; nenhuma ampliação para outros métodos/tasks:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t022" "-Dsurefire.reportNameSuffix=feature006-t022-public-http-access" test
```

| Execução | Testes | Falhas | Erros | Ignorados | GETs catálogo | GETs detalhes | POSTs login | HTTP total | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 — seleção inicial | 2 | 0 | 0 | 0 | 198 | 0 | 2 | 200 | 0 |
| 2 — seleção final | 2 | 0 | 0 | 0 | 198 | 6 | 2 | 206 | 0 |

Na execução final, ApiContext e RootContext reportam **um teste, 99 GETs de catálogo, três GETs de detalhes e um POST de login cada**; contêiner externo com zero testes. O agregado operacional das duas execuções é quatro invocações JUnit e 406 requisições HTTP sobre duas revisões do mesmo método, com cobertura sobreposta; não é um resumo único Maven ou quatro casos distintos. A aprovação final corresponde somente à execução 2.

**BUILD SUCCESS, exit code 0 em ambas**. A execução final tem **dois testes de integração e 206 requisições HTTP reais**: 198 GETs de catálogo (108 sucessos e 90 rejeições esperadas), seis GETs de detalhes 200 e dois POSTs 200 para preparar tokens. **Nenhum teste com mocks, unitário de serviço ou dispatch MockMvc executado**. Os três XMLs e três TXTs de cada execução foram conferidos contra console/testcases; somente `preservesPublicPaginationShapeAndAccess`, um caso por contexto, sem omissão. Não se repetiram T021, testes anteriores do grupo, checkpoints US1–US3 ou suíte completa. O complemento corrigiu lacuna de cobertura identificada em revisão, não falha funcional. GREEN existente, sem fabricar RED, alterar produção ou enfraquecer assertions.

Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` nos consoles; Wrapper configurado para Maven `3.9.16`. Execução 1: **2026-10-19T11:35:38.7367024-03:00** a **11:36:56.2998774-03:00**, duração externa **77,563 s**, Maven **01:12 min**. Execução 2: **2026-10-19T11:40:25.9496915-03:00** a **11:41:42.3126984-03:00**, duração externa **76,363 s**, Maven **01:11 min**. Produção atualizada sem recompilação; 91 fontes de teste recompilados em cada execução, release 21, sem erro. Sem comando separado de compilação/versão.

### Evidências e preservação

Antes da edição, cópias dos quatro arquivos envolvidos/recebidos e hashes dos 257 arquivos rastreados. Os outros **254 arquivos rastreados** mantiveram hashes, incluindo toda produção, `ProductControllerTest.java` recebido da T021, fixtures, artefatos aprovados e demais testes. Preservados código/assertions anteriores do grupo HTTP e histórico do quickstart; nas tasks muda somente T022.

Os **330 relatórios anteriores** foram copiados e conferidos antes da execução 1 em [surefire-before](../../target/feature006-t022-public-http-20261019T113600/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t022-public-http-20261019T113600/surefire-before-manifest.json). Seis relatórios dessa execução em [surefire-current](../../target/feature006-t022-public-http-20261019T113600/surefire-current/). Antes da execução 2, os **336 então presentes**, incluindo os seis da primeira, foram copiados para [surefire-before-access](../../target/feature006-t022-public-http-20261019T113600/surefire-before-access/) com [manifesto próprio](../../target/feature006-t022-public-http-20261019T113600/surefire-before-access-manifest.json). Após a execução final, origem e cópia mantiveram todos os tamanhos/hashes, inclusive T021, US1–US3 e primeira execução T022. Seis relatórios finais em [surefire-current-access](../../target/feature006-t022-public-http-20261019T113600/surefire-current-access/); nenhum anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t022-public-http-20261019T113600/console.log), [run-start.json](../../target/feature006-t022-public-http-20261019T113600/run-start.json), [run-result.json](../../target/feature006-t022-public-http-20261019T113600/run-result.json), [200 observações HTTP, separadas por finalidade](../../target/feature006-t022-public-http-20261019T113600/http-observations.json) e [verification.json](../../target/feature006-t022-public-http-20261019T113600/verification.json). Comando auxiliar `& target/feature006-t022-public-http-20261019T113600/verify.ps1`, exit 0, conferiu seleção, matrizes sem omissão/duplicação, metadados/seleção/erros/headers contra baseline T004, guards e preservação de relatórios/fontes. Conferência estática não é teste adicional; `target/` pode faltar em outro checkout.

Evidências da execução final: [console-access.log](../../target/feature006-t022-public-http-20261019T113600/console-access.log), [run-access-start.json](../../target/feature006-t022-public-http-20261019T113600/run-access-start.json), [run-access-result.json](../../target/feature006-t022-public-http-20261019T113600/run-access-result.json), [206 observações HTTP, por finalidade](../../target/feature006-t022-public-http-20261019T113600/http-access-observations.json) e [verification-access.json](../../target/feature006-t022-public-http-20261019T113600/verification-access.json). Comando auxiliar `& target/feature006-t022-public-http-20261019T113600/verify-access.ps1`, exit 0, repetiu a auditoria da matriz final e conferiu os seis acessos básicos aos detalhes, mantendo a comparação T004 e preservação de fontes/relatórios. Os hashes dos fontes executados distintos estão nos metadados de cada execução; a revisão final não é atribuída retroativamente à primeira.

**Conclusão T022**: critérios US4.1–US4.4 da tarefa atendidos na execução final, sem pendência remanescente. T001–T022 `[X]`, T023–T030 abertas. Alcance: catálogo e acesso básico a detalhes HTTP reais Windows/H2 em raiz e `/api`, fixture conhecida, paginações e credenciais descritas; não comprova os casos específicos de detalhes/legado US4.5–US4.7/T023, jornadas administrativas ou checkpoint final, outros bancos, concorrência, durabilidade, JDBC direto universal, efeitos fora das janelas GET, CI ou suíte completa. Sem stage, commit, push, troca de branch ou execução de T023+.

Conferência final: diff completo e incremento contra os quatro arquivos recebidos revisados, referências de método/tag/casos/baseline, artefatos locais novos e 235 links locais conferidos. `git diff --check` exit 0, sem problemas de whitespace. Histórico do quickstart e assertions anteriores do grupo HTTP preservados; nas tasks mudou somente T022; demais 254 hashes rastreados iguais, incluindo a T021 recebida. Git final: somente `ProductControllerTest.java` (alteração recebida intacta), `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. Raiz/branch/HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T023 — detalhes independentes e leitura de legado

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Git confirmou somente `PublicCatalogNameFilterIntegrationTest.java`, `ProductControllerTest.java`, quickstart e tasks modificados, nenhum staged ou não rastreado. T001–T022 `[X]`; lidas integralmente T023, dependência T022 e critérios, US4.5–US4.7, modelo de dados e garantias do [contrato histórico de detalhes da 002](../002-visualizar-detalhes-produto/contracts/product-detail-openapi.yaml), com os demais artefatos aprovados já lidos nesta sessão. Escopo exclusivo T023, sem executar T024+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, FEATURE_DIR correto e cinco documentos opcionais disponíveis. Variável e bytes do ponteiro restaurados em `finally`. Checklist somente lido, 16/16, zero pendentes; ignores existentes suficientes. Extensões ausentes na conferência inicial, sem hook prévio aplicável.

### Teste e preparação conhecidos

Acrescentado somente `preservesDetailsAndLegacyIndependentlyOfFilter`, tag `feature006-t023`, com seus auxiliares de oráculo em [PublicCatalogNameFilterIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java). O método é herdado pelos contextos reais raiz e `/api`, perfil test, H2 isolado sem seeds, OSIV local desativado e filtros/serviços/repositórios reais. Não se alteraram produção, contratos, suportes ou assertions existentes. O [manifesto prévio](../../target/feature006-t023-details-legacy-20261019T115000/selection-preflight.json) registra dois testes, dez GETs por contexto e somente a tag nova.

Fixture JDBC commitada com cinco produtos: Fone Atual (12.34), Cabo Antigo (29.90), `x`, 81 letras `a` e `"  Fone  Azul  "`. Os três últimos têm preço 0.00 e nenhuma categoria. A escrita administrativa permanece com limites 3–80 para nome; o suporte direto existente prepara nomes legados sem relaxar a API ou executar correção retroativa. Cabo Antigo possui três associações com IDs distintos de categorias: Zeta, Alpha e Alpha.

Snapshot independente antes dos GETs confere integralmente as linhas dos produtos (ID/nome/descrição/imagem/preço), categorias e três pares; todas as associações pertencem a Cabo Antigo. Pedidos/itens e contas vazios. IDs são capturados da inserção; o ID inexistente é calculado como maior ID preparado +1000 e sua ausência é confirmada no snapshot. Manifestos de conteúdo/ordem e categorias esperadas são explícitos, sem consulta produtiva ou deduplicação/ordenação de produção como oráculo.

### Sequência e resultados nos dois contextos

| Ordem | Operação | Resultado esperado e observado |
| --- | --- | --- |
| 1 | Catálogo `name=fone&page=0&size=12` | 200, somente nome espaçado e Fone Atual, nessa ordem; totais 2/1. Cabo Antigo ausente da seleção. |
| 2 | Detalhe de Cabo Antigo por ID capturado | 200, dados originais e categorias exatamente `["Alpha","Zeta"]`; produto acessível apesar da pesquisa anterior e sem duplicar o nome Alpha. |
| 3 | Detalhe do ID comprovadamente inexistente | 404 com corpo exato status/error/message/path, mensagem `Product not found` e path contextual; sem dados de outro produto. |
| 4 | Catálogo sem filtro, page0 size12 | 200, cinco itens originais: nome espaçado/Cabo Antigo/Fone Atual/81 letras a/x; totais 5/1. |
| 5–6 | Busca `name=x` e detalhe do legado curto | 200, somente seu ID na busca; detalhe com nome x, descrição/imagem originais, preço numérico zero e categories vazio. |
| 7–8 | Busca com 81 letras A e detalhe do legado longo | 200, somente o nome completo de 81 letras a; sem truncamento/rejeição, descrição/imagem originais, preço zero e categories vazio. |
| 9–10 | Busca `" fone  azul "` e detalhe do legado espaçado | 200, somente o produto espaçado; nome devolvido exatamente `"  Fone  Azul  "`, descrição/imagem originais, preço zero e categories vazio. |

Cada resposta é comparada ao JSON integral esperado: cinco campos da página e quatro de cada item no catálogo; seis campos dos detalhes; quatro do erro 404. IDs, nomes, descrições, imagens, preços e categorias conferidos com valores originais e preço JSON numérico. Nas buscas, query UTF-8 codificada e parâmetro decodificado conferidos pelo observador passivo existente; por exemplo, `name=+fone++azul+&page=0&size=12` chegou com os espaços pretendidos. Os detalhes usam somente o ID, sem exigir presença no resultado ou repetir filtro na rota.

Após cada uma das 20 chamadas, antes de cleanup: snapshot independente igual ao inicial, contas ainda vazias, SQL Hibernate somente SELECT em transação, zero DML observado e nenhuma transação externa do chamador. Sem contagem rígida de SELECT. Fixture limpa por ownership após a sequência, banco vazio novamente. Isso comprova leitura sem alteração nas janelas observadas; não é validação administrativa de escrita, nem execução das jornadas T024.

### Única execução efetiva

Conferidos ausência de outro processo Java/Maven ativo, método/tag únicos, herança nos dois contextos e sufixo previamente livre. Comando sequencial pelo Wrapper na raiz, via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest" "-Dgroups=feature006-t023" "-Dsurefire.reportNameSuffix=feature006-t023-details-legacy" test
```

| Relatório efetivo | Testes | Falhas | Erros | Ignorados | GETs catálogo | GETs detalhes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 1 | 0 | 0 | 0 | 5 | 5 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 1 | 0 | 0 | 0 | 5 | 5 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 | 0 | 0 |
| **Total** | **2** | **0** | **0** | **0** | **10** | **10** |

**BUILD SUCCESS, exit code 0**, primeira e única execução. **Dois testes de integração, 20 requisições GET a servidor HTTP**, sendo 18 respostas 200 e dois 404 esperados; os 404 não são falhas de teste. Zero mocks, dispatches MockMvc ou POSTs de login executados. O suporte da fixture constrói seu MockMvc existente, mas não o utiliza para requisições. Três XMLs e três TXTs conferidos contra console/testcases: somente `preservesDetailsAndLegacyIndependentlyOfFilter`, um por contexto, sem omissão ou skips. Nenhuma execução complementar, repetição de T021/T022/checkpoints/suíte completa ou tarefa T024+.

Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T11:51:03.7780791-03:00**, fim **11:52:17.1923336-03:00**, duração externa **73,414 s**, Maven **01:08 min**. Produção atualizada sem recompilação; 91 fontes de teste recompilados, release 21, sem erro. Sem comando separado de compilação/versão. GREEN de comportamento existente, sem fabricar RED, enfraquecer assertions ou mudar produção.

### Preservação, evidências e limites

Cópias dos quatro arquivos recebidos e hashes dos 257 arquivos rastreados preservados antes de editar. Remover em memória somente o bloco novo reproduziu exatamente o fonte HTTP recebido, inclusive T022 e US1–US3. Os outros **254 arquivos rastreados** conservaram hashes, incluindo produção, `ProductControllerTest.java` recebido da T021, fixtures e artefatos aprovados. Histórico do quickstart preservado por acréscimo; nas tasks muda somente T023.

Os **342 relatórios anteriores** foram copiados e conferidos antes do Maven em [surefire-before](../../target/feature006-t023-details-legacy-20261019T115000/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t023-details-legacy-20261019T115000/surefire-before-manifest.json). Após executar, origem e cópia mantiveram tamanhos/hashes, incluindo as duas execuções T022 e os checkpoints anteriores. Seis novos relatórios em [surefire-current](../../target/feature006-t023-details-legacy-20261019T115000/surefire-current/); nenhum anterior sobrescrito.

Evidências locais ignoradas: [console.log](../../target/feature006-t023-details-legacy-20261019T115000/console.log), [run-start.json](../../target/feature006-t023-details-legacy-20261019T115000/run-start.json), [run-result.json](../../target/feature006-t023-details-legacy-20261019T115000/run-result.json), [20 observações HTTP](../../target/feature006-t023-details-legacy-20261019T115000/http-observations.json) e [verification.json](../../target/feature006-t023-details-legacy-20261019T115000/verification.json). Comando auxiliar `& target/feature006-t023-details-legacy-20261019T115000/verify.ps1`, exit 0, conferiu seleção, sequência de dez casos por contexto, JSON/categorias/nomes/preços/totais/404, preservação de relatórios/fontes e guards. Conferência estática não é novo teste ou nova execução Maven; `target/` pode faltar em outro checkout.

**Conclusão T023**: US4.5–US4.7 e critérios atendidos, sem pendência remanescente. T001–T023 `[X]`, T024–T030 abertas. Alcance: HTTP real Windows/H2, raiz e `/api`, fixture estável, catálogo page0 size12, leituras anônimas e janelas síncronas; variantes de credenciais continuam na evidência T022, sem reexecução agora. Não comprova outros bancos, concorrência, durabilidade, JDBC direto universal, efeitos fora da janela, CI ou suíte completa. Sem stage, commit, push ou troca de branch.

Conferência final: diff e incremento contra os arquivos recebidos revisados, referências de método/tag/cenários e 246 links locais com destinos existentes. `git diff --check` exit 0. Histórico e assertions anteriores preservados; somente T023 alterada nas tasks e demais 254 hashes rastreados iguais. Git final com os mesmos quatro arquivos modificados, incluindo `ProductControllerTest.java` recebido intacto; nenhum staged ou não rastreado. Raiz, branch, HEAD e main mantidos. `.specify/extensions.yml` ausente também após execução, sem hooks aplicáveis.

## Execução da T024 — regressões administrativas, login, pedidos e histórico

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Git confirmou exatamente os quatro arquivos modificados previstos: `PublicCatalogNameFilterIntegrationTest.java`, `ProductControllerTest.java`, quickstart e tasks; nenhum staged ou não rastreado. T001–T023 `[X]`, dependência T023 conferida com sua evidência já registrada, sem repeti-la. Escopo exclusivo T024, sem T025+.

Lidas integralmente tarefa/dependências, spec, plano, pesquisa, modelo, contrato 006 e constituição; revisados matriz/procedimentos pertinentes, evidências T011/T023 e testes/suportes envolvidos. `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, diretório correto e cinco documentos opcionais. Variável e bytes do ponteiro restaurados em `finally`; checklist somente lido, 16/16, zero pendentes. Ignores Java/Maven existentes suficientes. Extensões ausentes na conferência inicial, sem hooks prévios aplicáveis.

### Revisão das garantias e lacuna específica

`git diff d2f76b0edaeebf070b861dc38038fd42d0e83a62 -- src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java` conferiu a evolução desde a base aprovada. T011 substituiu somente os dois resultados de `name` ignorado: Alpha/Beta sem filtro, Alpha para ALP, vazio para inexistente/`%_`; V03 filtrado em 2/2/1/0 com totais 5/3. Preservados preço numérico público versus textual administrativo, IDs de categorias homônimas versus nomes públicos deduplicados, tokens/contextos, paginação, snapshots, helpers e demais assertions. A única outra diferença anterior é a referência técnica à consulta neutra `findPageByName` da T008. Contratos e evidências históricos permanecem intactos.

Lacuna concreta: a jornada existente de pedido/histórico consultava detalhe após atualizar o preço, mas não o catálogo **filtrado com histórico preenchido**. Acrescentada essa leitura em [ordersKeepAuthorizationNewOrderPerSendAndHistoricalValuesAfterMaintenance](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java), sem criar outra jornada ou remover assertions. Nos três perfis CLIENT, ADMIN e ambos: primeiro pedido guarda preço 10.00/total 29.75; manutenção ADMIN muda somente preço atual para 12.00; detalhe e filtro `Product old price`, com token inválido irrelevante, mostram preço numérico 12.00, somente o produto esperado, defaults 0/12 e totais 1/1. Snapshot das cinco tabelas permanece igual ao pós-PUT, incluindo pedido/itens originais; IDs das duas contas e seus perfis permanecem os preparados. DELETE continua 409; dois pedidos posteriores distintos usam 12.00/33.75, enquanto o primeiro conserva os valores antigos. Controle de manutenção e preparação existentes reaproveitados, sem validação retroativa ou correção de dados.

Acrescentado ao helper HTTP existente somente log de método/path/status, sem corpo, credenciais ou JWT, para contar chamadas reais. Sem alteração em produção, fixtures, configuração, `AdminProductQueryIntegrationTest`, grupo público ou teste MVC recebido. GREEN de comportamento existente, sem fabricar RED ou enfraquecer assertions.

Auditados no grupo público os snapshots independentes antes/depois e antes da limpeza, janelas delimitadas por `sql.clear()`, SELECTs Hibernate em transação, ausência de DML e transação externa, e cleanup por ownership. T022 também confere IDs/perfis de CLIENT após cada GET; T023 confere contas vazias. Fonte e relatórios dessas evidências foram preservados, **sem reexecutar o grupo público/T021–T023**. Os guards continuam presentes em US1–US3. `SecurityConfig` mantém ADMIN em `/admin/**`, CLIENT ou ADMIN para POST `/orders` e fallback público sem exigir Bearer. Não se alterou segurança para fazer testes passarem.

### Seleção e única execução Maven

Seleção focal para T024: dois métodos da T011 expressamente sujeitos à conferência, controles administrativos de navegação/detalhe/categorias, login e pedidos/histórico/rejeições. Somente `shouldRejectAnonymousOrClientBeforeTheNewGetHandler` de [AdminProductGetHttpSecurityTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductGetHttpSecurityTest.java) complementa a prova negativa ADMIN: as três rotas GET administrativas rejeitam visitante 401 e CLIENT 403 antes do handler, conservando snapshots e contas. Essa seleção de seis casos atende o critério de acesso da T024; não executa a classe completa ou a seleção agregada futura T026.

Conferidos métodos/providers e contagens antes, ausência de outro processo Java/Maven, sufixo livre e backup íntegro. Uma execução sequencial pelo Wrapper, na raiz via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=AdminProductPublicRegressionIntegrationTest#publicNameFilterPreservesCategoryRepresentationsAndAdministrativeIsolation+loginKeepsExactCredentialsTokenAndSafeRejections+ordersKeepAuthorizationNewOrderPerSendAndHistoricalValuesAfterMaintenance+rejectedOrdersKeepExistingEnvelopeAndNoPartialRows+securityStillPrecedesInvalidOrderBody,AdminProductQueryIntegrationTest#shouldFilterThePublicCatalogByNameWithNumericPricesAndCoherentPagination+shouldNavigateTheExactSixProductFixtureWithoutDuplicatesOrLostProducts+shouldReadAllProductDataAndKeepBothCategoryIdsWhenTheirNamesAreEqual+shouldListEveryCategoryInNameIdOrderIncludingUnusedAndEqualNameOptions,AdminProductGetHttpSecurityTest#shouldRejectAnonymousOrClientBeforeTheNewGetHandler" "-Dsurefire.reportNameSuffix=feature006-t024-regression" test
```

| Classe e seleção efetiva | Testes | Falhas | Erros | Ignorados | Transporte |
| --- | ---: | ---: | ---: | ---: | --- |
| AdminProductPublicRegressionIntegrationTest — 2 filtro/categorias, 8 login, 3 histórico, 12 rejeições de pedido, 3 segurança de pedido | 28 | 0 | 0 | 0 | 111 requisições a servidor HTTP `/api` |
| AdminProductQueryIntegrationTest — 4 filtro público, 4 navegação admin, 2 detalhe admin, 2 categorias admin | 12 | 0 | 0 | 0 | 48 dispatches MockMvc, raiz e `/api` nos GETs |
| AdminProductGetHttpSecurityTest — três rotas × visitante/CLIENT | 6 | 0 | 0 | 0 | 9 dispatches MockMvc; seis GETs em `/api` |
| **Total** | **46** | **0** | **0** | **0** | **111 HTTP reais e 57 dispatches MockMvc** |

**BUILD SUCCESS, exit code 0**, primeira e única execução Maven, sem complemento. Console e três XMLs/três TXTs conferidos por classe, método e número de invocações, sem seleção omitida, inesperada ou skipped. Nenhum unitário com mocks executado: todos os 46 são testes de integração Spring com filtros/serviços/banco reais. Os contextos MockMvc não são servidores HTTP e não demonstram o fluxo servlet `/error`.

Os 111 logs `T024_HTTP` conferem: 61 POSTs `/login`, 24 POSTs `/orders`, três PUTs e três DELETEs administrativos, 11 GETs de catálogo, cinco de detalhes públicos e quatro administrativos. Status: 52×200, 9×201, 11×400, 26×401, 1×403, 3×404, 3×409 e 6×422; rejeições esperadas não são falhas JUnit. Incluem exatamente três novos GETs filtrados com histórico. Os 28 registros de prova por cenário também conferem integralmente. Login mantém credenciais exatas, JWT/identidade e rejeições; pedidos mantêm autorização CLIENT/ADMIN, envelope, atomicidade das rejeições e histórico.

Dos 57 dispatches MockMvc, **36 GETs** estão registrados como `T014_HTTP`, todos 200/snapshot igual/zero DML Hibernate observado. Os outros **21** são contados pelos fluxos selecionados e seus setups: 12 logins de preparação em AdminProductQueryIntegrationTest e seis GETs de rejeição/três logins CLIENT em AdminProductGetHttpSecurityTest. Estes 21 não têm um log individual novo de requisição; a contagem cruza fontes/providers com invocações efetivas do Surefire. Total: 42 GETs e 15 logins MockMvc. A navegação administrativa ainda executa **16 controles diretos de repositório**, com IDs/metadados comparados ao manifesto e às páginas; não são testes JUnit adicionais nem HTTP/MockMvc. Não se contam construções ociosas de MockMvc da fixture como dispatches.

Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Início **2026-10-19T12:00:59.9777944-03:00**, fim **12:02:34.4149194-03:00**, duração externa **94,437 s**, Maven **01:28 min**. Produção atualizada sem recompilação; 91 fontes de teste recompilados, release 21, sem erro. SHA-256 do fonte alterado executado: `5FC765759F451191F2B3909FABE3F258AB351DC935A8C7F2F376D996CB0ECBD1`. Nenhum comando separado de compilação/versão, checkpoint, suíte completa ou execução T025+.

### Evidências, preservação e limites

Antes de editar/executar, cópias dos arquivos envolvidos/recebidos e hashes dos 257 rastreados. Os outros **254 arquivos** conservaram hashes, incluindo produção, grupo público com T022/T023, `ProductControllerTest.java` com T021, AdminProductQueryIntegrationTest, segurança e suportes. Todas as assertions recebidas do teste alterado permanecem; a separação da variável da conta ADMIN só permite conferir seus perfis. Histórico do quickstart mantido por acréscimo; somente T024 muda nas tasks.

Os **348 relatórios anteriores** foram copiados e conferidos antes do Wrapper em [surefire-before](../../target/feature006-t024-regression-20261019T120019/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t024-regression-20261019T120019/surefire-before-manifest.json). Após executar, origem e cópia conservam todos os tamanhos/hashes, inclusive T021–T023/checkpoints. Seis relatórios novos em [surefire-current](../../target/feature006-t024-regression-20261019T120019/surefire-current/), sem sobrescrever os anteriores.

Evidências locais ignoradas: [seleção prévia](../../target/feature006-t024-regression-20261019T120019/selection-preflight.json), [console.log](../../target/feature006-t024-regression-20261019T120019/console.log), [run-start.json](../../target/feature006-t024-regression-20261019T120019/run-start.json), [run-result.json](../../target/feature006-t024-regression-20261019T120019/run-result.json), [111 observações HTTP](../../target/feature006-t024-regression-20261019T120019/http-observations.json) e [verification.json](../../target/feature006-t024-regression-20261019T120019/verification.json). `& target/feature006-t024-regression-20261019T120019/verify.ps1` foi usado para conferência estática dos artefatos: primeira tentativa exit 1 por não retirar o sufixo parentético do nome de suite XML; segunda exit 1 ao serializar chaves numéricas no PowerShell 5.1; terceira exit 0 após corrigir somente o auxiliar local. Essas conferências não executam testes, HTTP ou Maven. Não houve falha funcional, alteração do fonte executado ou reexecução Maven decorrente delas. `target/` não é versionado e pode faltar em outro checkout.

**Conclusão T024**: US4.8 e critérios aplicáveis atendidos; somente T024 marcada nesta execução, T001–T024 `[X]`, T025–T030 abertas. Evidência atual limitada aos métodos/variantes acima, Windows/H2 isolado e janelas síncronas. O teste servlet de histórico usa snapshots e IDs/perfis de contas, sem inspector novo; zero DML Hibernate atual pertence às 36 janelas MockMvc e aos controles diretos de repositório. Os guards do grupo público e seus resultados anteriores permanecem auditados como evidência anterior, sem nova execução. Não se alega snapshot integral de credenciais, ausência universal de JDBC direto/efeitos assíncronos, concorrência, durabilidade, outros bancos, CI, checkpoint US4 completo ou suíte completa. Sem pendência remanescente para T024; T025+ permanece fora do escopo. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff incremental e comparação das alterações T011 revisados, referências de métodos/seleção/guards e 257 links locais com destinos existentes conferidos. `git diff --check` exit 0. Reverter em memória apenas os acréscimos T024 e a variável ADMIN reproduz exatamente o fonte recebido; nenhuma assertion anterior removida. Histórico do quickstart, marcadores T001–T023 e outros 254 hashes rastreados preservados. Git final: cinco arquivos modificados, os quatro recebidos mais `AdminProductPublicRegressionIntegrationTest.java`; nenhum staged ou não rastreado. Raiz, branch, HEAD e main mantidos; ponteiro com SHA-256 original. `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis.

## Execução da T025 — GREEN focal conjunto das quatro histórias

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Git confirmou exatamente os cinco arquivos modificados solicitados: `AdminProductPublicRegressionIntegrationTest.java`, `ProductControllerTest.java`, `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks; nenhum staged ou não rastreado. T001–T024 `[X]`; lidas integralmente T025, dependências T021/T024 e seus critérios, cruzando os artefatos aprovados já lidos nesta sessão com a matriz dos 27 cenários e os fontes/providers atuais. Escopo exclusivo T025; nenhuma mudança produtiva ou de teste/assertion, nem execução T026+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0; JSON convertido e FEATURE_DIR absoluto conferido, cinco documentos opcionais disponíveis. Variável e bytes anteriores do ponteiro restaurados em `finally`. Checklist somente lido, 16/16, zero pendentes; ignores Java/Maven existentes suficientes, sem outras configurações aplicáveis. Extensões ausentes antes da execução. Não se repetiu validador OpenAPI ou baseline isolada.

### Preparação, comando e descoberta integral

T025 exige expressamente as cinco classes completas, incluindo casos exercitados antes. Essa repetição conjunta é o GREEN focal da tarefa; não é ampliação automática ou suíte completa. A [seleção prévia](../../target/feature006-t025-focused-20261019T120835/selection-preflight.json) enumera todos os métodos e quantidades de seus providers, total previsto 287. Conferidos os dois contextos herdados, raiz e `/api`, com 42 testes cada. Usada seleção somente por classes, sem filtros de método/tag/grupo, para incluir os aninhados; a omissão histórica da combinação de seletores em T012 não foi presumida resolvida sem conferir os relatórios atuais.

Antes do Wrapper: nenhum outro processo Java/Maven ativo, sufixo livre e backup verificado dos relatórios. **Uma única execução sequencial**, na raiz, via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=PublicCatalogNameFilterIntegrationTest,ProductControllerTest,ProductCatalogServiceTest,AdminProductQueryIntegrationTest,AdminProductPublicRegressionIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t025-focused" test
```

| Suite efetivamente executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PublicCatalogNameFilterIntegrationTest$ApiContext | 42 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest$RootContext | 42 | 0 | 0 | 0 |
| PublicCatalogNameFilterIntegrationTest (contêiner) | 0 | 0 | 0 | 0 |
| ProductControllerTest | 51 | 0 | 0 | 0 |
| ProductCatalogServiceTest | 22 | 0 | 0 | 0 |
| AdminProductQueryIntegrationTest | 95 | 0 | 0 | 0 |
| AdminProductPublicRegressionIntegrationTest | 35 | 0 | 0 | 0 |
| **Total** | **287** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**, sem execução complementar. Console, sete XMLs e sete TXTs conferidos por suite/método/número de invocações contra o manifesto prévio, sem casos omitidos, inesperados ou skipped. Os zeros do contêiner externo não substituem os 84 testes aninhados efetivamente executados.

Início **2026-10-19T12:09:17.4648846-03:00**, fim **12:10:59.7094427-03:00**, duração externa **102,245 s**, Maven **01:36 min**. Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Produção e testes já compilados e atualizados, sem recompilação nesta execução. Os hashes dos cinco fontes são os recebidos, conferidos após executar em [executed-sources.json](../../target/feature006-t025-focused-20261019T120835/executed-sources.json), contra o manifesto anterior dos 257 arquivos rastreados. Nenhuma correção funcional necessária: GREEN existente na mesma revisão de trabalho, sem RED artificial ou assertions enfraquecidas.

### Cenários e expectativas comprovados nesta execução

| Cenários da spec | Método/grupo executado e prova |
| --- | --- |
| US1.1–US1.5 | `matchesNameSubstringCaseAccentsAndLiterals`, 10 testes: trecho/caixa/acentos/%/_, IDs/ordem/totais exatos. |
| US1.6–US1.7 | `acceptsShortAndLongNameFilters`, quatro testes: um caractere e 81 caracteres completos, seleção sem truncamento. |
| US2.1, US2.2, US2.5 | `normalizesOnlyExternalFilterWhitespace`, 38 testes: omitido/vazio/espaços externos comuns e Unicode; nomes intactos. |
| US2.3, US2.4, US2.6 | `preservesInternalSpacesAndStoredNames`, seis testes: um/dois espaços, legado e NBSP interno distintos. |
| US3.1–US3.2 | `paginatesFilteredResultsInStoredNameIdOrder`, dois testes: páginas 2/2/1, totais 5/3, IDs sem perda/duplicação e desempate. |
| US3.3–US3.5 | `returnsCoherentEmptyFilteredPages`, 14 testes: ausência em páginas 0/2, página posterior e banco vazio com/sem filtro. |
| US3.6 | `doesNotRetainNameBetweenRequests`, dois testes: nova consulta omitida recupera manifesto completo, sem estado retido. |
| US4.1–US4.4 | `preservesPublicPaginationShapeAndAccess`, dois testes com 206 HTTP, mais caracterização atual T004 e MVC: defaults/limites/tipos/envelopes e anonimato/token inválido/CLIENT. |
| US4.5–US4.7 | `preservesDetailsAndLegacyIndependentlyOfFilter`, dois testes/20 HTTP: ID fora da busca, categorias deduplicadas, 404 e legado original. |
| US4.8 | Classes administrativas completas: expectativas T011 atualizadas, consultas/login/pedidos/rejeições/histórico, incluindo os três GETs filtrados com histórico da T024; snapshots/SQL do grupo público. |

São **27 cenários**, exercitados pelas invocações e assertions preservadas acima, não 27 testes JUnit. Serviços e MVC com mocks complementam o fluxo real; não são usados como única prova de consulta. Os dois métodos T011 passaram integralmente: sem filtro Alpha/Beta, ALP somente Alpha e inexistente/`%_` vazio; V03 público com cinco correspondências em 2/2/1/0, totais 5/3, em raiz e `/api`, sem token/token inválido. Categorias públicas continuam nomes únicos e preço numérico; administração mantém IDs e preço textual. Os comportamentos anteriores de `name` ignorado permanecem apenas no histórico, sem reescrever evidências antigas.

### Erros confrontados com a baseline histórica T004

Lidas as [88 observações originais da T004](../../target/feature006-t004-characterization-20261018T183724/http-observations.json), anteriores à mudança produtiva, sem reexecutar essa revisão histórica. A caracterização T004 incluída no grupo completo agora é uma execução atual sobre o filtro integrado, com seus controles de nomes correspondentes; não substitui o arquivo histórico.

Conferência estática comparou **130 respostas 400 atuais** com o caso/contexto correspondente da baseline: 40 da caracterização atual e 90 de T022. **52 são nativas de conversão**: 16 atuais T004 e 36 T022; as outras 78 são limites Bean Validation. Todos mantêm status, corpo e cabeçalhos relevantes iguais. Nativos: timestamp textual ISO-8601 validado e normalizado só para comparação; campos timestamp/status/error/path. Limites: corpo exato de quatro campos status/error/message/path conferido diretamente, sem confiar no `anyOf` aberto. Datas de transporte não exigem igualdade de instante; Content-Type, cache, cabeçalhos de segurança e ausência de autenticação/redirecionamento são preservados. Nenhum handler/envelope foi padronizado para obter GREEN. Comparações por origem/contexto/caso em [t004-error-comparisons.json](../../target/feature006-t025-focused-20261019T120835/t004-error-comparisons.json); cópia/hash da baseline no diretório local da execução.

### Testes e operações: contagens distintas

Os 287 testes compreendem **214 de integração Spring com produção real**, **51 de MVC slice com serviço mockado** e **22 unitários de serviço com repositório mockado**. Essas categorias não equivalem a requisições:

- **594 requisições HTTP reais**: 410 no grupo público (205 por contexto) e 184 na regressão servlet `/api`. No público, 408 registros individuais JSON e dois GETs de preparação comprovados por `T003_CONTEXT`/fluxo do fonte; incluem dois POSTs reais de login. Demais chamadas estão nos 184 registros `T024_HTTP`. Por tag no grupo público: T004=88, T005=16, T006=4, T013=38, T014=12, T017=6, T018=14, T019=4, T022 catálogo=198/detalhe=6/login=2 e T023=20, além dos dois controles T003. Não somar novamente os mesmos registros copiados nos XMLs.
- **266 dispatches MockMvc**: 52 no controller slice (51 testes, com duas páginas no caso de ordenação) e 214 na integração administrativa (119 GETs e 95 logins de preparação). Há logs individuais de 39 dispatches T021 e 119 GETs T014; os outros 108 são contados pelos fluxos/providers e setups cruzados com as invocações Surefire. Não são HTTP a servidor, nem prova de `/error` servlet.
- **34 controles diretos de repositório** dentro de AdminProductQueryIntegrationTest: 16 páginas V03 e 18 consultas literais, comparadas ao manifesto e às respostas. Não são testes adicionais ou dispatches.

As rejeições 400/401/403/404/409/415/422 esperadas nos fluxos reais são sucesso das respectivas assertions, não falhas JUnit. No grupo público, continuam snapshots independentes, janelas Hibernate sem DML, nomes originais e cleanup antes de concluir os casos; T022 confere contas/perfis e T023 contas vazias. Na jornada servlet de histórico, snapshots/IDs/perfis permanecem íntegros após leituras filtradas, e valores anteriores do pedido permanecem 10.00/29.75 enquanto preço atual/pedidos posteriores usam 12.00/33.75. Essa classe não ganhou inspector novo; a observação SQL não é estendida a janelas que não a possuem.

### Preservação e conclusão

Os **354 relatórios anteriores** foram copiados e conferidos antes do Wrapper em [surefire-before](../../target/feature006-t025-focused-20261019T120835/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t025-focused-20261019T120835/surefire-before-manifest.json). Após executar, origem e cópia mantiveram os 354 tamanhos/hashes, inclusive T021–T024 e checkpoints anteriores. Os 14 novos relatórios estão em [surefire-current](../../target/feature006-t025-focused-20261019T120835/surefire-current/), com sufixo exclusivo; nenhum anterior removido/sobrescrito. Os outros **255 arquivos rastreados**, incluindo todos os fontes, produção, testes, fixtures e artefatos aprovados, preservaram seus hashes recebidos. Histórico do quickstart mantido integralmente; somente o marcador T025 muda nas tasks.

Evidências locais ignoradas: [console.log](../../target/feature006-t025-focused-20261019T120835/console.log), [run-start.json](../../target/feature006-t025-focused-20261019T120835/run-start.json), [run-result.json](../../target/feature006-t025-focused-20261019T120835/run-result.json), [observações públicas](../../target/feature006-t025-focused-20261019T120835/public-http-observations.json), [observações servlet de regressão](../../target/feature006-t025-focused-20261019T120835/regression-http-observations.json) e [verification.json](../../target/feature006-t025-focused-20261019T120835/verification.json). Comando auxiliar `powershell -NoProfile -File target/feature006-t025-focused-20261019T120835/verify.ps1`, uma conferência estática, exit 0: seleção integral por método/provider, logs, comparação com T004 e preservação de relatórios/fontes. O resumo salvo foi lido por `Get-Content`/`ConvertFrom-Json`; apenas a projeção de exibição do auxiliar foi ajustada após emitir valores nulos no console, sem mudar o JSON correto, verificações ou executar Maven novamente. Os artefatos de `target/` são locais e podem faltar em outro checkout.

**Conclusão T025**: GREEN focal conjunto dos 27 cenários e expectativas atualizadas na mesma revisão de trabalho, com comparação efetiva dos erros nativos à T004. Sem discrepância funcional ou pendência remanescente desta tarefa. T001–T025 `[X]`, T026–T030 abertas. Alcance: cinco classes, contextos/fixtures Windows/H2 e janelas síncronas descritas; não comprova outros bancos, concorrência, durabilidade, ausência universal de JDBC direto/efeitos fora da janela, CI ou suíte completa. Regressão agregada T026, auditoria final, suíte e fechamento permanecem futuros. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff incremental, referências de métodos/providers/cenários/baseline, relatórios e artefatos locais novos revisados; 270 links locais com destinos existentes conferidos. `git diff --check` exit 0. Histórico documental e todos os fontes/assertions recebidos preservados por comparação/hash; somente T025 mudou nas tasks. Git final com os mesmos cinco arquivos modificados recebidos, nenhum staged ou não rastreado. Raiz/branch/HEAD e main mantidos; ponteiro com SHA-256 original. `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis.

## Execução da T026 — regressão do compartilhamento, detalhes, segurança e jornadas

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `feb09d770617a18442259d41033bbe1ed752b7cf`. Git confirmou exatamente os cinco arquivos modificados previstos: `AdminProductPublicRegressionIntegrationTest.java`, `ProductControllerTest.java`, `PublicCatalogNameFilterIntegrationTest.java`, quickstart e tasks; nenhum staged ou não rastreado. T001–T025 `[X]`. Lidas integralmente T026, dependência T025 e seus critérios; conferidos seleção de regressão do guia, artefatos aprovados já lidos nesta sessão e fontes/providers/suportes pertinentes. T026 é executável, não apenas revisão documental. Escopo exclusivo, sem T027+.

Pré-requisitos: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, JSON convertido, FEATURE_DIR absoluto correto e cinco documentos opcionais. Variável e bytes do ponteiro restaurados em `finally`. Checklist somente lido, 16/16, zero pendentes. Ignores Java/Maven existentes suficientes; nenhuma configuração adicional identificada. Extensões ausentes inicialmente, sem hook prévio aplicável.

**Evidência anterior reutilizada**: os metadados e relatórios T025 confirmam a dependência GREEN, 287 testes/exit 0 naquela execução; os fontes recebidos e suas evidências T021–T025 foram preservados. Não se reexecutou T025, sua seleção ou seus 594 HTTP reais, nem se somaram esses resultados ao total atual. Nenhuma suíte completa ou validação OpenAPI executada agora.

### Seleção, preservação prévia e única execução

A [conferência prévia](../../target/feature006-t026-regression-20261019T121843/selection-preflight.json) fixa somente as dez classes exigidas. Relatórios históricos completos foram usados **como inventário de métodos/providers**, não como aprovação atual, cruzados com os dez fontes e suas quantidades de métodos JUnit; previsão de 253 invocações, incluindo duas classes aninhadas. Conferência posterior exigiu todos esses métodos/casos nos relatórios atuais. Não se filtraram métodos, tags ou grupos.

Antes do Maven: cópias dos cinco arquivos recebidos, hashes dos 257 rastreados, nenhum processo Java/Maven concorrente, sufixo livre e backup conferido dos 368 relatórios anteriores. Uma única execução sequencial pelo Wrapper, na raiz via `cmd.exe /d /c`, stdout/stderr preservados:

```powershell
.\mvnw.cmd "-Dtest=AdminProductRepositoryTest,ProductRepositoryAdminQueryComponentTest,AdminQueryServicesTest,AdminQueryServiceComponentIntegrationTest,ProductRepositoryTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,AdminProductGetHttpSecurityTest,GlobalExceptionHandlerRegressionTest,OrderPublicRegressionIntegrationTest" "-Dsurefire.reportNameSuffix=feature006-t026-regression" test
```

| Classe selecionada, incluindo seus aninhados | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| AdminProductRepositoryTest | 12 | 0 | 0 | 0 |
| ProductRepositoryAdminQueryComponentTest | 12 | 0 | 0 | 0 |
| AdminQueryServicesTest | 32 | 0 | 0 | 0 |
| AdminQueryServiceComponentIntegrationTest | 2 | 0 | 0 | 0 |
| ProductRepositoryTest | 6 | 0 | 0 | 0 |
| PublicCatalogSecurityIntegrationTest — 31 externos + 2 EmptyCatalog | 33 | 0 | 0 | 0 |
| PublicProductDetailSecurityIntegrationTest — 36 externos + 2 DuplicateCategoryNames | 38 | 0 | 0 | 0 |
| AdminProductGetHttpSecurityTest | 20 | 0 | 0 | 0 |
| GlobalExceptionHandlerRegressionTest | 84 | 0 | 0 | 0 |
| OrderPublicRegressionIntegrationTest | 14 | 0 | 0 | 0 |
| **Total** | **253** | **0** | **0** | **0** |

**BUILD SUCCESS, exit code 0**, primeira e única execução, sem complemento/repetição/ampliação. Console, 12 XMLs e 12 TXTs conferidos por suite e por `testcase.classname`/método/quantidade de invocações, sem omissões, métodos extras ou skips. Particularidade concreta do Surefire: o relatório de EmptyCatalog agrega os 33 casos de catálogo, dos quais somente dois pertencem ao aninhado; DuplicateCategoryNames agrega os 38, dos quais somente dois são do aninhado. Os dois relatórios externos têm zero. A distribuição real foi conferida pelos atributos de cada testcase; não foram atribuídos 33/38 testes somente aos aninhados nem somados duas vezes.

Início **2026-10-19T12:19:15.4436985-03:00**, fim **12:20:45.8874142-03:00**, duração externa **90,444 s**, Maven **01:25 min**. Windows/PowerShell `5.1.19041.7663`, `JAVA_HOME=C:\Program Files\Zulu\zulu-21`, Java `21.0.12.1` no console; Wrapper configurado para Maven `3.9.16`. Produção e testes atualizados, sem recompilação nesta execução. Os [hashes dos dez fontes executados](../../target/feature006-t026-regression-20261019T121843/executed-sources.json) são os recebidos, conferidos contra o manifesto anterior. Sem alteração de produção/testes/assertions ou correção funcional necessária.

### Garantias verificadas e natureza das operações

Passaram seleção/count/ordem e literalidade da consulta compartilhada, normalização administrativa, mapeamento monetário/categorias e leitura de legado; detalhes existentes/ausentes, acesso público com token inválido irrelevante e catálogo sem filtro; precedência da proteção ADMIN nas três rotas GET administrativas, inclusive parâmetros inválidos, sem conceder permissão por causa do filtro público. Mantidos envelopes/mensagens/path de catálogo, detalhe e login, sem tratar falhas públicas não relacionadas como erros de pedido. A expectativa intencional de `name` ignorado não foi restaurada nem usada para enfraquecer o filtro; os casos filtrados da T025 permanecem evidência anterior preservada.

Nos proxies administrativos: quatro leituras registradas `T018_PROXY`, transações readOnly/SELECTs e snapshots iguais, sem transação externa; três leituras CLIENT rejeitadas antes de SQL de produto/categoria. Os testes de persistência mantiveram seus controles de snapshots/SQL e fixtures isoladas; o teste de repositório legado usa o contexto JPA/seeds histórico próprio. Não se alteraram defaults globais de teste ou contagem rígida de SELECTs.

Em OrderPublicRegressionIntegrationTest, **14 registros T046_PERSISTENCE** confirmam snapshots independentes restaurados, ordersDelta=0/itemsDelta=0 após sete rejeições × raiz/`/api`, antes da limpeza. Os **28 registros T046_PUBLIC** confirmam, após essas rejeições, catálogo/detalhes/login reais da aplicação por MockMvc, sem token ou com token inválido, no mesmo contexto, preservando dados e sem criar pedidos/itens. Nos **dois cenários de falha controlada pós-flush**, observados três INSERTs concluídos (pedido e dois itens) na transação real, seguidos de rollback completo, falha de teste injetada por spy/suporte existente e desarmamento confirmado. Isso não é zero DML global nem defeito de produção: a escrita/rollback deliberados pertencem à prova de rejeição; as jornadas públicas posteriores preservam a baseline.

Os 253 testes compreendem **137 de integração/persistência**, **32 unitários de serviço com dependências simuladas** e **84 de MVC slice com serviços mockados**. A integração de rollback usa a instrumentação/spy existente para injetar sua falha controlada. Não se confundem testes com chamadas:

- **Zero requisições a servidor HTTP real nesta seleção**: os testes web usam MockMvc; as outras verificações chamam repositórios/proxies diretamente. Não demonstram despacho servlet `/error` ou transporte de rede. O HTTP real T025 permanece identificado como resultado anterior.
- **920 dispatches MockMvc calculados pelos fluxos atuais e invocações efetivas**: AdminProductGetHttpSecurityTest=31 (20 GETs e 11 logins), GlobalExceptionHandlerRegressionTest=84, PublicCatalogSecurityIntegrationTest=43, PublicProductDetailSecurityIntegrationTest=146 e OrderPublicRegressionIntegrationTest=616. Não existe contador individual novo para todos eles; a conferência cruza fontes/providers/setups com Surefire. Dentro dos 616 últimos, **588 são confirmados pelo contador/log existente**, 28 variantes ×21 jornadas públicas, além de 14 logins preparatórios e 14 POSTs de rejeição. Não se contam construções ociosas de MockMvc das fixtures como dispatches.
- Chamadas diretas de serviços/repositórios e observações JDBC/SQL não são HTTP ou testes adicionais. Rejeições esperadas, inclusive 500 da falha controlada pós-flush, são sucesso das assertions dos casos respectivos.

### Evidências, limites e conclusão

Os **368 relatórios anteriores** foram copiados e conferidos antes do Wrapper em [surefire-before](../../target/feature006-t026-regression-20261019T121843/surefire-before/), com [manifesto de tamanho/SHA-256](../../target/feature006-t026-regression-20261019T121843/surefire-before-manifest.json). Após executar, origem e cópia conservam todos os tamanhos/hashes, inclusive os 14 relatórios T025 e as evidências T021–T024/checkpoints. Os 24 relatórios novos estão em [surefire-current](../../target/feature006-t026-regression-20261019T121843/surefire-current/), sem sobrescrever os anteriores. Outros **255 arquivos rastreados** mantiveram hashes, incluindo toda produção, testes, assertions, fixtures e artefatos aprovados. Histórico do quickstart preservado por acréscimo; nas tasks muda somente T026.

Evidências locais ignoradas: [console.log](../../target/feature006-t026-regression-20261019T121843/console.log), [run-start.json](../../target/feature006-t026-regression-20261019T121843/run-start.json), [run-result.json](../../target/feature006-t026-regression-20261019T121843/run-result.json), [testcases efetivos por classe/método/suite](../../target/feature006-t026-regression-20261019T121843/executed-testcases.json), [28 observações das jornadas após rejeição](../../target/feature006-t026-regression-20261019T121843/order-public-observations.json) e [verification.json](../../target/feature006-t026-regression-20261019T121843/verification.json). Comando auxiliar `powershell -NoProfile -File target/feature006-t026-regression-20261019T121843/verify.ps1`, uma conferência estática, exit 0: seleção integral, distribuição dos aninhados, contadores/guards de rollback/proxies e preservação de relatórios/fontes. Não executa testes, dispatches ou Maven; arquivos em `target/` podem faltar em outro checkout.

**Conclusão T026**: seleção de regressão exigida integralmente GREEN, sem falha, necessidade de ampliação ou pendência remanescente desta tarefa. T001–T026 `[X]`, T027–T030 abertas. Alcance: dez classes, seus contextos/fixtures Windows/H2 e janelas síncronas descritas, com limites dos mocks/spy/SQL indicados. Não comprova outros bancos, concorrência, durabilidade, ausência universal de JDBC direto/efeitos fora da janela, nova execução HTTP real, CI ou suíte completa. Auditoria T027 e fechamento seguintes permanecem futuros. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff incremental, referências de seleção/métodos/providers, distribuição dos aninhados, relatórios e arquivos locais novos revisados; 281 links locais com destinos existentes conferidos. `git diff --check` exit 0. Histórico documental e todos os fontes/assertions recebidos preservados; somente T026 mudou nas tasks. Git final com os mesmos cinco arquivos modificados recebidos, nenhum staged ou não rastreado. Raiz/branch/HEAD e main mantidos; ponteiro com SHA-256 original. `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis.

## Auditoria da T027 — rastreabilidade, contrato e gates da Feature 006

**Revisão auditada em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95`. Git inicialmente limpo, nenhum staged ou não rastreado; T001–T026 `[X]`. Lidas integralmente T027, dependência T026 e critérios; cruzadas matriz das tasks, spec, contrato 006, plano/gates, modelo, pesquisa e constituição já lidos nesta sessão com implementação, assertions e evidências. Escopo documental exclusivo T027; nenhum Maven, compilação, teste de aplicação, HTTP novo ou tarefa T028+ executado.

Pré-requisitos executados: `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY=C:/Users/ronal/dscommerce-speckit/specs/006-filtrar-catalogo-produtos` temporário: exit 0, JSON convertido, FEATURE_DIR absoluto correto e cinco documentos opcionais. Variável e bytes do ponteiro restaurados em `finally`. Checklist somente lido: 16/16, zero pendentes. Ignores Java/Maven existentes conferidos e suficientes; nenhuma configuração adicional identificada. Extensões ausentes inicialmente.

### Evidência reutilizada e vínculo com o código atual

T025 e T026 continuam **duas execuções anteriores distintas**, com 287 e 253 testes, respectivamente, ambas exit 0/zero falhas/erros/ignorados. Foram executadas na working tree anterior ao commit US4, com HEAD `feb09d7…`; esta auditoria não as apresenta como novas execuções no HEAD atual. Os 255 hashes rastreados fora de quickstart/tasks de **ambos os manifestos prévios** coincidem com os arquivos atuais: produção, todos os testes/suportes e artefatos aprovados são os mesmos bytes. Isso vincula a prova existente ao código commitado `0f6a47d…`, sem alegar novo teste ou CI deste commit.

Lidos novamente comandos/metadados/consoles e os 19 XMLs T025/T026, conferindo totais, zero falhas/erros/skips e os métodos/testcases usados abaixo. Conferência estática dos registros públicos T025 comparou **318 corpos completos** com os esperados de fixtures independentes, incluindo o corpo normalizado dos erros nativos. Também conferiu flags presentes de snapshot/contas/perfis/zero DML/ausência de transação externa e existência dos métodos nos XMLs de raiz e `/api`. A matriz abaixo contém exatamente os **27 IDs únicos** das tasks, com seus casos observáveis; não soma novamente chamadas compartilhadas por vários requisitos.

### Matriz cenário → método/assertion → execução existente

Salvo US4.8, métodos do [grupo público](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogNameFilterIntegrationTest.java), com linha de início indicada; `Txxx_HTTP` identifica registros da **execução T025**, não nova execução da tarefa original. Cada linha tem prova em raiz e `/api`. Manifestos, seletores de casos, linhas e quantidades constam de [audit.json](../../target/feature006-t027-audit-20261019T124044/audit.json); relatórios em [T025 Surefire](../../target/feature006-t025-focused-20261019T120835/surefire-current/) e [T026 Surefire](../../target/feature006-t026-regression-20261019T121843/surefire-current/).

| Cenário | Método e assertion concreta | Registros/casos reutilizados |
| --- | --- | --- |
| US1.1 | `matchesNameSubstringCaseAccentsAndLiterals`, L270/355: JSON exato seleciona Fone Azul/Telefone Preto e exclui Cabo. | T005_HTTP/SUBSTRING, 2 chamadas. |
| US1.2 | Mesmo método, L355/361: cada resposta exata e três páginas iguais para fone/FONE/FoNe. | T005_HTTP/CASE, 6 chamadas. |
| US1.3 | Mesmo método: CAFÉ somente Café Especial; cafe somente Cafe Claro; IDs/totais distintos. | T005_HTTP/ACCENTS, 4 chamadas. |
| US1.4 | Mesmo método: 10% literal somente Oferta 10%, sem Oferta 100; query decodificada conferida. | T005_HTTP/PERCENT, 2 chamadas. |
| US1.5 | Mesmo método: kit_ somente Kit_A, sem KitXA. | T005_HTTP/UNDERSCORE, 2 chamadas. |
| US1.6 | `acceptsShortAndLongNameFilters`, L376/447: f completo e somente Fone Azul. | T006_HTTP/SHORT, 2 chamadas. |
| US1.7 | Mesmo método: 81 A selecionam somente 81 a, não 80; texto/filtro e metadados completos. | T006_HTTP/LONG, 2 chamadas. |
| US2.1 | `normalizesOnlyExternalFilterWhitespace`, L458/516: padding comum equivale a FONE, mesmo manifesto. | T013_HTTP/trimmed-control/common-padding, 4 chamadas. |
| US2.2 | Mesmo método: sete variantes, incluindo omitido/vazio e whitespace, devolvem catálogo completo. | T013_HTTP/omitted/empty/common-only/tab-only/nbsp-only/em-only/mixed-only, 14 chamadas. |
| US2.3 | `preservesInternalSpacesAndStoredNames`, L527/599: um e dois espaços selecionam formas distintas. | T014_HTTP/COMMON, 4 chamadas. |
| US2.4 | Mesmo método: legado com extremos e espaços internos mantido no JSON e no snapshot; filtro simples não o seleciona. | T014_HTTP/LEGACY, 4 chamadas. |
| US2.5 | `normalizesOnlyExternalFilterWhitespace`: tab/NBSP/U+2003 à esquerda/direita/ambos, mistos e somente Unicode. | T013_HTTP, 14 variantes pertinentes ×2 contextos =28 chamadas. |
| US2.6 | `preservesInternalSpacesAndStoredNames`: espaço comum e NBSP internos não intercambiáveis. | T014_HTTP/NBSP, 4 chamadas. |
| US3.1 | `paginatesFilteredResultsInStoredNameIdOrder`, L613/690: JSON 2/2/1, totais 5/3 e união exata de IDs. | T017_HTTP/page0/1/2, 6 chamadas. |
| US3.2 | Mesmo método: ordem do manifesto armazenado, empate Fone B por ID; sem duplicar produto com duas categorias. | Mesmas 6 chamadas, assertions de ordem/união sem duplicação. |
| US3.3 | `returnsCoherentEmptyFilteredPages`, L707/769: 200/vazio/totais zero, page0 ou page2 size2 preservados. | T018_HTTP/no-matches-first/no-matches-nonzero, 4 chamadas. |
| US3.4 | Mesmo método: page3 size2 vazio com totais 5/3, sem clamp. | T018_HTTP/beyond-five-matches, 2 chamadas. |
| US3.5 | Mesmo método: banco vazio, filtro presente/ausente e page0/2, totais zero. | Quatro casos empty-*, 8 chamadas. |
| US3.6 | `doesNotRetainNameBetweenRequests`, L781/853: filtro primeiro, omitido depois, mesma paginação e manifesto completo independente. | T019_HTTP/step, 4 chamadas. |
| US4.1 | `preservesPublicPaginationShapeAndAccess`, L866/966: defaults independentes 0/12 e paginação vazia; omitido/vazio/preenchido. | T022_HTTP/defaults/empty-page/empty-size/empty-both, 72 chamadas; MVC T021 complementa binding. |
| US4.2 | Mesmo método: size1/50; page-1/size0/51 e texto abc; limites com ErrorResponse direto e nativos preservados. | Sete casos T022_HTTP, 126 chamadas; 130 comparações históricas T004 em T025, 52 nativas. |
| US4.3 | Mesmo método: JSON exato com cinco campos da página/quatro do item, tipos e price numérico. | 108 respostas 200 T022_HTTP; testes MVC complementares. |
| US4.4 | Mesmo método, L1012: igualdade semântica anônimo/Bearer inválido/CLIENT, catálogo e detalhe com seis campos. | 198 T022_HTTP +6 T022_DETAIL; identidade CLIENT verificada no login real. |
| US4.5 | `preservesDetailsAndLegacyIndependentlyOfFilter`, L1068/1139: Cabo excluído da busca seguido de detalhe acessível por ID. | T023_HTTP/filtered-before-excluded-detail/excluded-detail-deduplicated, 4 chamadas. |
| US4.6 | Mesmo método: Alpha/Zeta únicos apesar de três categorias; erro público 404 exato com path contextual. | T023_HTTP/excluded-detail-deduplicated/missing-detail, 4 chamadas. |
| US4.7 | Mesmo método: catálogo/detalhes legados curto/longo/espaçado, descrição/imagem/preço zero/categories vazias originais, snapshot igual. | T023_HTTP/unfiltered-legacy e seis casos search/detail, 14 chamadas. |
| US4.8 | `ordersKeepAuthorizationNewOrderPerSendAndHistoricalValuesAfterMaintenance` e controles de consulta/login/segurança; grupo público observa leitura sem DML. | T025: 3 variantes de histórico e 3 GETs filtrados com histórico preenchido, consultas admin reais; T026: 6 rejeições ADMIN básicas, 12 com parâmetros inválidos, 14 rejeições de pedido e 28 jornadas públicas posteriores. |

US4.8 também foi conferida em [AdminProductPublicRegressionIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductPublicRegressionIntegrationTest.java), [AdminProductQueryIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductQueryIntegrationTest.java), [AdminProductGetHttpSecurityTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductGetHttpSecurityTest.java), [OrderPublicRegressionIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderPublicRegressionIntegrationTest.java) e testes administrativos de serviço/repositório. As assertions de snapshots, perfis, categoria pública versus IDs administrativos, preços atuais/históricos e rollback permanecem presentes e GREEN nas execuções citadas. Nenhuma prova central depende somente de mocks.

### Cobertura dos 14 FR e seis SC

| FR | Cenários/prova e responsabilidade de código conferida |
| --- | --- |
| FR-001 | US4.4: name opcional no controller, fallback público sem Bearer obrigatório; matriz de três acessos. |
| FR-002 | US1.1–US1.3: `locate(lower(:name),lower(p.name)) > 0`, trecho contínuo/caixa e acentos observados. |
| FR-003 | US1.4–US1.5: parâmetro vinculado, sem LIKE/regex/interpolação; literais confirmados em HTTP e regressão do repositório. |
| FR-004 | US2.1/US2.3/US2.5/US2.6: helper percorre somente extremidades por pontos de código, união isWhitespace/isSpaceChar. |
| FR-005 | US2.2/US3.6: normalização local à chamada, ramo findAll para vazio, sem campo/estado de pesquisa. |
| FR-006 | US2.4/US4.7: DTO usa getName original; sem setter/save; snapshots/SQL nas leituras. |
| FR-007 | US3.1/US3.4: PageRequest e count com mesmo predicado, metadados copiados de Page, sem pós-filtro. |
| FR-008 | US3.2: name ASC/id ASC no ramo findAll e JPQL, sem normalizar ordem ou fazer join de categorias na página. |
| FR-009 | US3.3–US3.5: Page/200 preserva posição/tamanho/totais, sem clamp ou 404 indevido. |
| FR-010 | US4.1/US4.2: int/defaults 0/12/Min/Max públicos existentes; binding nativo e limites confrontados. |
| FR-011 | US4.3: ProductCatalogItemDTO/PageResponseDTO inalterados, JSON estrito e BigDecimal numérico. |
| FR-012 | US4.5/US4.6: detalhe usa somente ID, DTO de nomes distintos/ordenados e ProductNotFoundException existente. |
| FR-013 | US1.6/US1.7/US4.7: name sem Size/truncamento de negócio; nenhuma validação administrativa de escrita na leitura. |
| FR-014 | US4.8: guarda ADMIN anterior à consulta administrativa; público separado; leituras sem mutação e jornadas/histórico preservados. |

| SC | Evidência focal do resultado mensurável |
| --- | --- |
| SC-001 | Os sete cenários US1, seleção exata de IDs/nomes/totais nas duas variantes de contexto. |
| SC-002 | Os seis cenários US2, mais US3.6: exterior normalizado, interior/original preservado e ausência sem estado. |
| SC-003 | US3.1/US3.2: cinco correspondências uma vez, 2/2/1, totais 5/3 e ordem nome/ID. |
| SC-004 | US3.3–US3.5: ausência/banco vazio/página posterior coerentes em todos os casos previstos. |
| SC-005 | US4.1–US4.4: defaults/limites/tipos/acesso preservados; única evolução intencional é aplicar name. |
| SC-006 | US4.5–US4.8: ID independente/404/legado/jornadas, snapshots e histórico, dentro das fixtures e janelas observadas. |

Os IDs foram confrontados com as definições da spec: 14 FR e 6 SC únicos, nenhum ausente ou alterado. Os resultados se referem aos cenários/fixtures aprovados; não significam desempenho, todos os dados possíveis, concorrência ou validação universal de outro banco.

### Confronto da implementação com o contrato

Inspecionados [ProductController](../../src/main/java/com/devsuperior/dscommerce/controllers/ProductController.java), [ProductCatalogService](../../src/main/java/com/devsuperior/dscommerce/services/ProductCatalogService.java), [ProductNameFilter](../../src/main/java/com/devsuperior/dscommerce/services/ProductNameFilter.java), [ProductRepository](../../src/main/java/com/devsuperior/dscommerce/repositories/ProductRepository.java) e [AdminProductQueryService](../../src/main/java/com/devsuperior/dscommerce/services/AdminProductQueryService.java), DTOs, segurança e handler existentes. O diff de produção desde a base aprovada `d2f76b0…` limita-se a esses cinco arquivos: entrada name, helper compartilhado, integração de consulta e renomeação neutra. `pom.xml`, entidades, DTOs, segurança e handler são idênticos à base; `git diff --exit-code d2f76b0edaeebf070b861dc38038fd42d0e83a62 HEAD -- pom.xml src/main/java/com/devsuperior/dscommerce/dto src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java src/main/java/com/devsuperior/dscommerce/entities` retornou exit 0.

| Contrato 006 | Confronto e resultado no recorte |
| --- | --- |
| GET /products, name opcional sem min/max de filtro; literal, caixa/acentos/espaços delimitados | Controller delega texto; serviço normaliza só exterior; JPQL vinculado preserva literalidade. US1/US2 e comprimento GREEN, sem alteração de dados ou regra de escrita. |
| page/size e ordem/totais do conjunto | Defaults/Bean Validation mantidos; paginação/count no banco; metadados Page, ordem nome armazenado/ID; US3/US4.1–2 coerentes. |
| 200 e schemas ProductCatalogPage/ProductCatalogItem | Records têm os cinco/quatro campos exigidos; preços BigDecimal numéricos, sem descrição/categorias na página; corpos completos conferidos. |
| 400 de limites versus erro nativo aberto | ErrorResponse conferido diretamente em US4.2; nativeClientError público relançado ao MVC/servlet pelo handler preservado. T025 compara 130 erros à T004, 52 nativos, com timestamp validado/normalizado apenas na comparação. `anyOf` não é usado como aprovação permissiva. |
| Acesso público e contrato histórico de detalhes 002 | Fallback permitAll sem resource server global; cadeia administrativa exige ADMIN. Detalhes somente por ID, nomes de categorias distinct/sorted e 404 existentes; US4.4–7/T026 confirmam o recorte. |
| Leitura sem escrita/correção retroativa | Transações readOnly, consultas paginadas sem fetch de coleção; detail fetch restrito ao ID como antes; DTOs leem texto/preço original. SQL/snapshots complementam inspeção, com limites abaixo. |

Não foi encontrada divergência funcional de contrato a corrigir. O contrato/spec/plano permanecem aprovados e intactos, inclusive seus descritores históricos de planejamento; esta conclusão é registrada aqui, sem reescrever requisitos para acomodar código. A validação OpenAPI 0.9.0 continua a evidência documental anterior, não foi reexecutada nem apresentada como teste HTTP.

### Gates próprios reavaliados com código e evidência

| Gate do plano/constituição | Resultado da auditoria T027 e fundamento |
| --- | --- |
| I — Java/Spring Boot | Conforme no recorte 006: Java 21/Boot existentes, pom inalterado; ambientes locais dos consoles T025/T026, nenhuma dependência nova. |
| II — camadas | Conforme: controller faz binding/validação/delegação; normalização/ramo/mapeamento no serviço; seleção/count/ordem no repositório. Helper puro compartilhado somente entre serviços de consulta. |
| III — DTOs | Conforme: records públicos preservados, nenhuma entidade exposta; name é query escalar opcional, sem novo corpo. JSON exato coberto por HTTP real e MVC complementar. |
| IV — persistência/validação antes de negócio | Conforme **ao fluxo novo da 006**, sem exceção nova: Bean Validation/binding page/size antes da consulta; name sem restrição nova a validar; normalização posterior no serviço; JPA/readOnly/sem manutenção/diagnóstico de escrita. SQL e snapshots focalmente corroboram ausência de mutação. |
| V — erros/REST | Conforme: GET/200 inclusive vazio, limites/404 preservados e nativos não padronizados; handler central existente, sem try/catch em controller. |
| Backend Constraints | Conforme: 27 cenários/14 FR/6 SC vinculados; extração pequena e renomeação reaproveitam regra/consulta; sem novo domínio, schema, entidade, migração, estado ou infraestrutura. |
| Workflow/Governance | Conforme **até esta auditoria**: RED/planejamento/checkpoints/evidências distinguíveis e arquivos preservados. T028–T030 continuam pendentes; não há aprovação antecipada de suíte final, fechamento global ou CI. |

**Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, conforme [plano histórico](../005-manter-produtos/plan.md), [pesquisa histórica](../005-manter-produtos/research.md) e [fechamento histórico](../005-manter-produtos/quickstart.md). `isValid` da escrita administrativa executa diagnóstico de serviços antes de concluir validação, exceção delimitada naquela feature. O filtro público não chama esse caminho nem muda escrita/normalização administrativa de entrada, suas guardas ou DTOs. A conformidade da nova leitura 006 não apaga a exceção existente nem a converte em PASS global. Nenhum desses documentos ou a constituição foi alterado.

Ausência de mutação: guards das buscas conferem SQL Hibernate SELECT em transação e snapshots de produtos/categorias/pares/pedidos/itens antes/depois, antes da limpeza; T022 verifica IDs/perfis da conta CLIENT, T023 contas vazias. T004 tem janela coletiva de caracterização, não um novo snapshot após cada variante. Histórico servlet usa snapshots/IDs/perfis sem inspector novo. Em T026, os dois testes de rollback deliberadamente escrevem três linhas e as revertem antes das jornadas públicas: não são declarados zero DML global. `readOnly` sozinho, snapshots sozinhos ou observação Hibernate não provam ausência universal de JDBC direto, efeitos assíncronos, todos os campos de credenciais ou durabilidade. Esses limites permanecem explícitos.

### Comandos, resultado e preservação

`Get-Content`/`Select-String`/`rg` conferiram fontes, métodos/assertions, contrato e registros; `git diff --name-only d2f76b0… HEAD -- src/main pom.xml` identificou os cinco impactos de produção já commitados, sem edição nesta tarefa. O auxiliar local `powershell -NoProfile -File target/feature006-t027-audit-20261019T124044/audit.ps1` fez **conferências estáticas**, sem Maven/testes/HTTP: primeira tentativa exit 1 por precedência de concatenação/vírgula na lista PowerShell de exclusão dos dois documentos, que comparou indevidamente o quickstart anterior; corrigida somente a lista do auxiliar, segunda tentativa exit 0. Nenhum fonte produtivo/teste foi alterado para passar a auditoria. Resultado salvo: 27 cenários, 14 FR, 6 SC, 318 corpos registrados iguais, código atual igual aos manifestos T025/T026 e **392 relatórios preservados por tamanho/SHA-256**. Não houve falha funcional nova ou necessidade de execução complementar.

Em T027, **zero execuções Maven, zero testes novos/executados e zero requisições HTTP/dispatches novos**. Os resultados reutilizados mantêm suas categorias: T025, 287 testes com 594 HTTP reais e 266 dispatches MockMvc (parte contada pelos fluxos); T026, 253 testes, zero HTTP real e 920 dispatches MockMvc calculados, dos quais 588 confirmados pelo contador existente. Não somar essas execuções como uma suíte ou os mesmos registros da tabela como chamadas independentes. Nenhuma validação Linux/CI é inferida dos grupos Windows/H2.

Cópias iniciais dos dois documentos, hashes dos 257 rastreados, manifesto dos 392 relatórios e [audit.json](../../target/feature006-t027-audit-20261019T124044/audit.json) em diretório local ignorado; artefatos `target/` podem faltar em outro checkout. Lacuna documental de rastreabilidade efetiva foi suprida por esta matriz e avaliação de gates, sem mexer na matriz planejada ou artefatos aprovados. **Nenhuma lacuna funcional ou de evidência focal bloqueante encontrada para T027**; os limites não cobertos continuam reservados às etapas próprias.

**Conclusão T027**: critérios desta auditoria atendidos; T001–T027 `[X]`, T028–T030 abertas. Somente quickstart e marcador T027 em tasks alterados, preservando implementação, testes, assertions, documentos históricos e relatórios. **Suíte completa T028 ainda não executada**, auditoria não é entrega funcional global ou encerramento da feature. Sem stage, commit, push, troca de branch ou alteração de main.

Conferência final: diff, referências de métodos/linhas/casos/relatórios/contrato e artefatos locais novos revisados; 298 links locais com destinos existentes conferidos. `git diff --check` exit 0. Histórico integral do quickstart preservado; apenas T027 mudou nas tasks; demais 255 hashes rastreados e todos os 392 relatórios mantidos. Git final: somente quickstart e tasks modificados, nenhum staged ou não rastreado. Raiz/branch/HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95` e main mantidos; ponteiro com SHA-256 original. `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis.

## Execução da T028 — suíte completa final reprovada

**Contexto recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95`. Git confirmou somente quickstart e tasks modificados pela T027, nenhum staged ou não rastreado; produção e testes estabilizados no HEAD. Lidas integralmente T028, sua dependência T027, critérios e artefatos pertinentes. A auditoria anterior foi preservada, sem convertê-la em aprovação da suíte. Escopo exclusivo T028, sem T029–T030.

Pré-requisitos: a consulta inicial padrão resolveu a Feature 005 pelo ponteiro local; a resolução com `-Json -PathsOnly` e override temporário confirmou a 006 sem persistência. Depois, `powershell -NoProfile -File .specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, com `SPECIFY_FEATURE_DIRECTORY` absoluto da 006 temporário, retornou exit 0 e os cinco documentos opcionais. Variável e bytes do ponteiro foram restaurados em `finally`; [prerequisites.json](../../target/feature006-t028-final-20261019T1601/prerequisites.json) registra o diretório correto. Checklist somente lido: 16/16, zero pendentes. Ignores Java/Maven suficientes, nenhuma configuração editada; extensões ausentes.

### Única execução completa e resultado observado

Antes do Maven, conferida ausência de outro Maven/Surefire ativo; os **392 relatórios existentes** foram copiados para [surefire-before](../../target/feature006-t028-final-20261019T1601/surefire-before/) e conferidos por tamanho/SHA-256 conforme [manifesto anterior](../../target/feature006-t028-final-20261019T1601/surefire-before-manifest.json). Foram salvos os hashes dos 257 arquivos rastreados e cópias integrais dos dois documentos recebidos. Sufixo previamente inexistente, usado somente para nomes dos relatórios, sem seleção de classes/métodos/tags ou skips:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-t028-final" test
```

Comando executado na raiz pelo [run.ps1](../../target/feature006-t028-final-20261019T1601/run.ps1), sequencialmente, sem outra execução Maven complementar. Wrapper configurado para Maven 3.9.16; XMLs confirmam Java Azul `21.0.12.1`, Windows 10 amd64. Início `2026-10-19T16:01:35.3549064-03:00`, término `2026-10-19T16:13:54.7624231-03:00`; duração do processo 739,4075 s, tempo informado pelo Maven `12:04 min`. Revisão/estado/comando em [run-start.json](../../target/feature006-t028-final-20261019T1601/run-start.json); resultado em [run-result.json](../../target/feature006-t028-final-20261019T1601/run-result.json), saída integral em [console.log](../../target/feature006-t028-final-20261019T1601/console.log).

| Resultado desta execução | Testes | Falhas de asserção | Erros | Ignorados | Exit code |
| --- | ---: | ---: | ---: | ---: | ---: |
| **BUILD FAILURE** | **2754** | **0** | **64** | **0** | **1** |

Contagens obtidas dos **88 XMLs atuais**, confrontadas com cada resumo de suíte do console e com o agregado final: 2.754 testcases únicos, sem somar relatórios históricos. Conferidos os nomes de 80 classes concretas com testes pelos padrões de descoberta do Surefire, propriedades sem filtros adicionais/skips e elementos de erro/falha/skip contra os atributos dos XMLs. Os **176 arquivos novos (88 XML + 88 TXT)** permanecem em `target/surefire-reports/`, com cópias em [surefire-current](../../target/feature006-t028-final-20261019T1601/surefire-current/), [manifesto atual](../../target/feature006-t028-final-20261019T1601/current-manifest.json) e [verification.json](../../target/feature006-t028-final-20261019T1601/verification.json). Existem agora 568 relatórios, dos quais somente os 176 de sufixo T028 pertencem a esta execução.

| Grupo reportado nesta execução | Testes | Erros | Leitura correta |
| --- | ---: | ---: | --- |
| `PublicCatalogNameFilterIntegrationTest$ApiContext` | 42 | 0 | HTTP real em `/api`, zero falhas/skips. |
| `PublicCatalogNameFilterIntegrationTest$RootContext` | 42 | 0 | HTTP real na raiz, zero falhas/skips. |
| `PublicCatalogSecurityIntegrationTest$EmptyCatalog` | 33 | 28 | Relatório agrega casos externos e aninhados; os 28 erros têm classname externo. |
| `PublicProductDetailSecurityIntegrationTest$DuplicateCategoryNames` | 38 | 36 | Relatório agrega casos externos e aninhados; os 36 erros têm classname externo. |

Os contêineres externos desses grupos têm relatórios com zero; não somar novamente nem atribuir 33/38 casos ou os erros somente aos aninhados. O GREEN dos 84 testes do filtro público é resultado parcial desta suíte reprovada. As aprovações de T025/T026 e os 2.614 testes históricos continuam evidências anteriores, sem serem atribuídos a esta execução ou usados como contagem esperada.

### Impedimento observado e diagnóstico limitado

[errors.json](../../target/feature006-t028-final-20261019T1601/errors.json) identifica os 64 testcases, tipos, mensagens e stack traces: 40 `jakarta.servlet.ServletException` e 24 `org.springframework.jdbc.BadSqlGrammarException`; **todos os 64 contêm, na mensagem ou causa, `Table "TB_PRODUCT" not found (this database is empty)` e código H2 42104**. Consultas públicas e a verificação `assertSeedUnchanged()` dos detalhes encontram o schema ausente. Não é RED funcional planejado do filtro nem erro de compilação: a suíte foi executada e reprovou durante as regressões de catálogo/detalhe.

Evidência concreta em [diagnosis-log.txt](../../target/feature006-t028-final-20261019T1601/diagnosis-log.txt): `HikariPool-12` abriu `jdbc:h2:mem:dscommerce-test` às 16:03:59.658; `HikariPool-53`, criado pelo contexto de `PublicCatalogSecurityIntegrationTest`, abriu o mesmo banco às 16:12:39.548. Às 16:12:40.126, logo após a inicialização desse contexto, houve fechamento de EntityManagerFactory e shutdown do pool 12; às 16:12:40.173 surgiu o primeiro erro de tabela ausente. O [perfil test](../../src/test/resources/application-test.properties) usa URL H2 fixa e `ddl-auto=create-drop`; os grupos externos de [catálogo](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicCatalogSecurityIntegrationTest.java) e [detalhes](../../src/test/java/com/devsuperior/dscommerce/controllers/PublicProductDetailSecurityIntegrationTest.java) o reutilizam, enquanto seus aninhados têm bancos próprios.

**Inferência**, sem nova reprodução/instrumentação: o fechamento de contexto com `create-drop` sobre banco compartilhado é compatível com a perda do schema usado por outro contexto; expulsão do cache de contextos é uma hipótese de gatilho. O log não identifica sozinho a política do cache ou uma correção comprovada. Não foi demonstrado defeito da regra de filtro, nem aprovação global de comportamento. Não alterados fontes, testes, assertions, seeds, cache, ordem de execução ou configuração para contornar a falha; não houve repetição Maven. A exigência de preservar produção/testes foi mantida.

**Pendência necessária para concluir T028**: corrigir o isolamento/ciclo de vida do H2 dos contextos afetados em escopo que autorize essa mudança, preservar suas assertions e evidências, e obter nova suíte completa sem filtros/skips na revisão resultante. A causa observada foi registrada; correção e GREEN continuam pendentes. T028 permanece `[ ]`; tasks não foi editado nesta execução, preservando o marcador anterior da T027. T029–T030 permanecem `[ ]` e não foram executadas.

### Natureza da validação e preservação

- **Execução de comportamento**: esta única suíte Windows/H2, com resultado global FAILURE e resultados parciais acima. Não comprova CI/Linux, outros bancos, concorrência, durabilidade, desempenho ou ausência universal de efeitos fora das janelas dos testes.
- **Validação estrutural do contrato OpenAPI**: nenhuma nova execução; contrato inalterado. A validação OpenAPI 0.9.0 do planejamento permanece histórica e documental, sem substituir estes testes/HTTP.
- **Validação documental/estática**: leitura de dependências/artefatos, conferência de console/XMLs/testcases, hashes e preservação; exit 0 do conferidor significa consistência da evidência de **falha**, sem aprovar o build. O auxiliar local precisou de quatro ajustes/tentativas reprovadas (formato do sufixo no XML, identificação de classe de suporte sem testes e array JSON no PowerShell 5.1); a quinta conferência concluiu exit 0, sem executar Maven nem mudar testes. XMLs conferidos nativamente por .NET/PowerShell; a tentativa auxiliar `python --version` encontrou o alias indisponível da Microsoft Store, sem bloquear o Maven.

Os 392 relatórios anteriores conservaram tamanho/SHA-256 tanto na origem quanto na cópia após a execução; todos os **255 arquivos rastreados fora de quickstart/tasks** conservaram seus hashes. Isso inclui produção, testes, assertions, artefatos aprovados e documentos históricos. **Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**; esta falha ou os resultados parciais não reclassificam aquele registro. O conteúdo anterior integral do quickstart, inclusive T027, é preservado por acréscimo. Evidências locais em `target/` são ignoradas e podem faltar em outro checkout; nenhuma publicação foi feita.

**Situação da T028: executada, mas não concluída**, pois seu critério exige suíte aprovada. Sem stage, commit, push, troca de branch ou alteração de main. A revisão documental final desta entrega é registrada a seguir, sem concluir T030.
Revisão documental final da entrega T028: diff recebido da T027 e acréscimo desta execução revisados, referências de comandos/classes/relatórios conferidas e **313 links locais com destinos existentes** verificados. `git diff --check` exit 0; histórico anterior do quickstart preservado byte a byte e tasks idêntico ao recebido. Conferência registrada em `target/feature006-t028-final-20261019T1601/review.json`. Somente quickstart foi editado nesta execução; tasks mantém a alteração anterior da T027. Git final: somente `specs/006-filtrar-catalogo-produtos/quickstart.md` e `specs/006-filtrar-catalogo-produtos/tasks.md` modificados, nenhum staged ou não rastreado. Branch/HEAD e main mantidos; ponteiro preservado; `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis. Nenhuma nova execução Maven após a documentação. T028–T030 continuam abertas.

## Retomada da T028 — causa reproduzida, isolamento corrigido e suíte aprovada

**Revisão recebida em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95`; somente quickstart/tasks modificados, nenhum staged ou não rastreado. T028 e o registro malsucedido acima foram lidos antes de novos testes, juntamente com console, XMLs Surefire, configurações e classes afetadas. T027, a falha anterior e os artefatos aprovados foram preservados. Pré-requisitos da 006 novamente resolvidos com override temporário e restauração dos bytes do ponteiro; [prerequisites.json](../../target/feature006-t028-recovery-20261019T1626/prerequisites.json), exit 0. Checklist somente lido, 16/16, zero pendentes; ignores suficientes e extensões ausentes. Escopo exclusivo T028.

Antes de executar testes, os **568 relatórios recebidos**, incluindo os 176 da suíte reprovada, foram copiados e conferidos por tamanho/SHA-256 em [surefire-before](../../target/feature006-t028-recovery-20261019T1626/surefire-before/), com [manifesto](../../target/feature006-t028-recovery-20261019T1626/surefire-before-manifest.json). Também preservados documentos recebidos, fonte anterior do console e hashes dos 257 rastreados; contexto em [session-start.json](../../target/feature006-t028-recovery-20261019T1626/session-start.json). Todas as execuções abaixo foram pelo Wrapper, sequenciais, com sufixos novos e checagem de ausência de Maven/Surefire concorrente.

### Diagnóstico e reprodução antes da correção

A primeira causa relevante dos 64 erros anteriores é ausência do schema usado pelas regressões públicas, não seleção incorreta por `name`: todos os 64 stack traces chegam a H2 42104/`TB_PRODUCT` ausente. O pool 12 da execução original pertence ao contexto raiz `RANDOM_PORT` de `H2ConsoleSecurityIntegrationTest`; o pool 53 pertence ao contexto público MockMvc. Ambos usavam a URL fixa do perfil test, `jdbc:h2:mem:dscommerce-test`, com `ddl-auto=create-drop`. Os contextos são distintos apesar do banco nomeado ser o mesmo. Os aninhados já possuem URLs próprias. Catálogo e detalhes externos reutilizam o mesmo contexto público; `JwtTokenServiceTest` também usa o perfil padrão, mas não originou os 64 erros observados e não foi alterado.

Para reproduzir o fechamento sem executar dezenas de contextos, selecionados **três métodos existentes**, em ordem alfabética: console H2 → catálogo → detalhe. Cache limitado a 1 **somente neste ensaio controlado**, com TRACE de cache e DEBUG de SQL, sem editar código, seed ou asserções para provocar a falha:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-t028-repro-red" "-Dtest=H2ConsoleSecurityIntegrationTest#shouldRegisterTheConfiguredConsoleInTheRealPublicSecurityChain,PublicCatalogSecurityIntegrationTest#anonymousRequestShouldListProductsWithDefaultPagination,PublicProductDetailSecurityIntegrationTest#anonymousRequestShouldReturnExistingProductDetails" "-Dsurefire.runOrder=alphabetical" "-Dspring.test.context.cache.maxSize=1" "-Dlogging.level.org.springframework.test.context.cache=TRACE" "-Dlogging.level.org.hibernate.SQL=DEBUG" test
```

Resultado: **3 testes, 0 falhas, 2 erros, 0 skips, BUILD FAILURE/exit 1**, de 16:25:44.581 a 16:26:21.091 (-03:00). Console e XMLs em [repro-red](../../target/feature006-t028-recovery-20261019T1626/repro-red/); [eventos de ciclo de vida](../../target/feature006-t028-recovery-20261019T1626/repro-red/lifecycle-events.txt). O console passa; catálogo e detalhe reproduzem a causa H2 42104.

**Mecanismo confirmado no ensaio**: os pools 1 e 2 abrem `dscommerce-test`; o contexto público cria `tb_product` e termina sua inicialização às 16:26:19.904. Ao exceder o cache, o contexto antigo é fechado às 16:26:19.934; Hibernate executa `drop table if exists tb_product cascade` às 16:26:19.950, antes do shutdown do pool 1. O GET seguinte falha às 16:26:20.418; detalhe reutiliza o contexto público e falha pelo mesmo schema ausente. `DB_CLOSE_DELAY=-1` mantém o banco em memória, mas não impede esse DROP explícito. Assim, URLs compartilhadas mais `create-drop` de um contexto descartado removiam o schema ainda necessário ao outro.

A biblioteca efetivamente usada, Spring Test 6.1.13, também foi inspecionada por `javap -c -p`: `DefaultContextCache$LruCache.removeEldestEntry` remove o contexto mais antigo ao ultrapassar o limite; `ContextCacheUtils.retrieveMaxCacheSize` usa padrão 32 e a propriedade `spring.test.context.cache.maxSize`. Saídas em [spring-cache-lru.txt](../../target/feature006-t028-recovery-20261019T1626/spring-cache-lru.txt) e [spring-cache-defaults.txt](../../target/feature006-t028-recovery-20261019T1626/spring-cache-defaults.txt). O log original não tinha métricas/TRACE do cache: não se atribuem a ele hits/misses medidos agora. Seu fechamento do pool 12 imediatamente após iniciar o pool 53 e antes do primeiro erro é coerente com o mecanismo reproduzido; a expulsão controlada é evidência nova, não uma instrumentação retroativa da execução anterior. [diagnosis.json](../../target/feature006-t028-recovery-20261019T1626/diagnosis.json) reúne os eventos e identidades de contexto dos ensaios.

### Correção mínima e validação da mesma sequência

Acrescentado somente um `@DynamicPropertySource` de oito linhas ao contexto raiz de [H2ConsoleSecurityIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java): URL `h2-console-root-<UUID>` gerada por inicialização de contexto e estável em seus acessos, capturada pelo supplier. O contexto passa a possuir seu schema e pode removê-lo sem atingir o seed público. Mantidos `create-drop`, inicialização, perfil test, filtros, servlet real, contexto `/api`, métodos e todas as asserções. O [diff da correção](../../target/feature006-t028-recovery-20261019T1626/fix.diff) contém somente esse acréscimo; removê-lo recupera integralmente o fonte anterior, após normalizar apenas terminações de linha. Produção, configuração global de testes, cache padrão e demais testes não foram alterados.

Reexecutado **o mesmo comando focal, mesmos três métodos, ordem e cache 1**, trocando somente o sufixo por `feature006-t028-repro-green`. Resultado: **3 testes, 0 falhas, 0 erros, 0 skips, BUILD SUCCESS/exit 0**, de 16:26:50.297 a 16:28:05.063 (-03:00). [Comando literal e resultado](../../target/feature006-t028-recovery-20261019T1626/repro-green/result.json), [console/XMLs](../../target/feature006-t028-recovery-20261019T1626/repro-green/) e [eventos](../../target/feature006-t028-recovery-20261019T1626/repro-green/lifecycle-events.txt).

O pool do console agora abre `h2-console-root-a70575d3-c5a3-4bdf-a6c4-35ddb2eb276c`; o público continua em `dscommerce-test`. A expulsão e o DROP continuam ocorrendo após iniciar o público, às 16:28:03.396–03.443, mas os GETs de catálogo/detalhe e suas asserções passam. Isso comprova sobrevivência à **mesma ordem e ao mesmo fechamento**, não apenas aprovação isolada de classe ou remoção do `create-drop`. As duas execuções focais preservaram 568 e 574 relatórios anteriores, respectivamente, por SHA-256.

### Nova suíte completa final, sem parâmetros diagnósticos

Após resolver a reprodução focal, executada a suíte exigida pela T028, sem seleção de classes/métodos/tags, skips, ordem forçada, cache reduzido ou logging diagnóstico:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-t028-full-green" test
```

**Revisão testada**: HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95` mais exclusivamente o acréscimo no teste do console; SHA-256 do fonte `37A537619328152ABAE19948091DA69E13EB058C16E90E2269AADB4BC12CF65E`, igual no GREEN focal e no completo. Os documentos são evidências na working tree, sem commit novo. Wrapper configurado Maven 3.9.16, Java Azul `21.0.12.1`, Windows 10 amd64. Processo de `2026-10-19T16:28:33.6420777-03:00` a `2026-10-19T16:40:15.5086373-03:00`, 701,8666 s; Maven informou `11:35 min`.

| Execução atual | Testes | Falhas | Erros | Ignorados | Exit code |
| --- | ---: | ---: | ---: | ---: | ---: |
| **Suíte completa — BUILD SUCCESS** | **2754** | **0** | **0** | **0** | **0** |

[Comando/revisão inicial](../../target/feature006-t028-recovery-20261019T1626/full-green/start.json), [resultado](../../target/feature006-t028-recovery-20261019T1626/full-green/result.json), [console integral](../../target/feature006-t028-recovery-20261019T1626/full-green/console.log), [176 relatórios atuais](../../target/feature006-t028-recovery-20261019T1626/full-green/surefire-current/) e [auditoria dos XMLs](../../target/feature006-t028-recovery-20261019T1626/full-green/audit-full.json). O console foi confrontado por suíte com os 88 XMLs, seus elementos de resultado e testcases; 80 classes concretas identificadas, sem filtros/skips adicionais. Todos os **2.754 pares classname/método-invocação** coincidem com a suíte que falhou, normalizando somente sufixos: [preservação dos casos](../../target/feature006-t028-recovery-20261019T1626/full-green/case-preservation.json). Nenhum teste foi excluído, ignorado ou teve cobertura removida para obter aprovação.

Catálogo e detalhes de segurança agora têm zero erros nos agregados de 33 e 38 casos (relatórios aninhados agregam também os externos, conforme explicado anteriormente); os 84 testes HTTP da 006 continuam GREEN em raiz e `/api`. O console H2 soma os mesmos 15 casos anteriores, incluindo seu aninhado, sem dupla contagem dos contêineres de zero. A suíte atual contém os mesmos casos da falha anterior, mas é uma **nova execução**; não converte retroativamente aquele FAILURE em GREEN nem usa 2.614 como contagem esperada.

### Preservação, limites e conclusão da tarefa

Os **580 relatórios anteriores** à suíte final (568 recebidos + 6 RED + 6 GREEN) conservaram tamanho/SHA-256 após executar; os 176 novos elevam o diretório Surefire a 756 arquivos. Preservados os **254 rastreados fora de quickstart/tasks/teste do console**, inclusive toda produção, outros testes, configuração global, especificação, contratos, constituição e documentos das features anteriores. O histórico do quickstart recebido permanece integral por acréscimo; o marcador da T027 recebido permanece intacto. **Gate IV da Feature 005 segue EXCEÇÃO DOCUMENTADA, sem PASS estrito**, sem reclassificação por esta correção.

A causa e a correção têm comprovação em execução: RED/GREEN com fechamento induzido e nova suíte completa com configuração habitual. A redução do cache é uma condição do ensaio focal, não configuração da entrega nem prova de todos os tamanhos/ordens possíveis. Limites: Windows/H2 e cenários existentes; sem nova CI/Linux, validação em outros bancos, garantia de concorrência/durabilidade ou desempenho. Contrato OpenAPI inalterado: não foi reexecutado validador estrutural; sua validação 0.9.0 anterior continua documental/histórica. A revisão de fontes, hashes, links e diff é validação estática, separada dos testes executados.

Os auxiliares locais tiveram um erro de parsing antes de iniciar Maven (delimitação da variável `Phase`) e uma coleta JSON interrompida por serialização de propriedades estendidas das linhas PowerShell; foram corrigidos apenas os auxiliares ignorados em `target/`. Isso não acrescentou execução Maven, não alterou asserções nem foi usado como RED da aplicação. Foram exatamente **três execuções Maven nesta retomada**: reprodução RED, mesma reprodução GREEN e suíte completa final, pelo [run.ps1](../../target/feature006-t028-recovery-20261019T1626/run.ps1).

**Conclusão T028: critérios atendidos**, reprodução focal resolvida e suíte completa aprovada na revisão de código entregue. Marcar somente T028; T029–T030 permanecem abertas e não foram executadas. A revisão da entrega solicitada nesta retomada não conclui T030. Sem stage, commit, push, troca de branch ou alteração de main; evidências locais em `target/` são ignoradas e podem faltar em outro checkout. Nenhum Maven adicional é necessário pelas alterações documentais posteriores.

Revisão final desta retomada: diff recebido e acréscimos revisados, referências de métodos/comandos/relatórios conferidas, **334 links locais com destinos existentes** validados e `git diff --check` exit 0. Histórico recebido do quickstart preservado byte a byte; tasks difere do recebido somente pelo marcador T028. O fonte do console difere apenas pelo inicializador registrado, sem remoção de asserções. Conferência em `target/feature006-t028-recovery-20261019T1626/review.json`. Git final: modificados somente quickstart, tasks e `src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java`; nenhum staged ou não rastreado. Branch e HEAD mantidos, main em `20495b487379e260a4c967823997bff872920dc9`, ponteiro preservado. `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis. T028 `[X]`, T029–T030 `[ ]`; nenhum Maven repetido pela edição documental.

## Consolidação da T029 — evidências verificadas da Feature 006

**Estado consolidado em 2026-10-19, revisão documental exclusiva T029**: raiz `C:/Users/ronal/dscommerce-speckit`, branch `006-filtrar-catalogo-produtos`, HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95`. Recebidos somente quickstart/tasks e o teste do console H2 modificados, sem staged ou não rastreados. T028 está concluída pela nova suíte completa, não pela primeira tentativa. Lidas integralmente T029, sua dependência, registros e artefatos pertinentes. Os textos iniciais de planejamento, procedimentos sugeridos e conclusões datadas continuam históricos; esta seção apresenta o estado posterior sem reescrevê-los. T030 permanece fora do escopo.

Pré-requisitos da 006 conferidos com override temporário e restauração do ponteiro; [prerequisites.json](../../target/feature006-t029-consolidation-20261019T1646/prerequisites.json), exit 0. Checklist somente lido, 16/16 e zero pendentes; ignores suficientes, extensões ausentes. Preservados documentos recebidos, hashes dos 257 rastreados e dos 756 arquivos Surefire. **Nesta T029: zero Maven, zero testes/compilações, zero requisições HTTP/dispatches novos e nenhum validador OpenAPI executado.**

### Inventário das execuções anteriores: checkpoints não são uma suíte única

Confrontados comandos/revisões/exit codes dos metadados com **32 consoles e 282 XMLs de execuções históricas distintas** em `target/surefire-reports/`, sem contar novamente suas cópias de backup. Por execução, atributos e elementos de testcase/failure/error/skipped coincidem com o resumo final do console; não se somam invocações repetidas de checkpoints diferentes como testes únicos. Os 192 relatórios anteriores à Feature 006 não entram nesse inventário. [runs.json](../../target/feature006-t029-consolidation-20261019T1646/runs.json) contém comandos literais, horários, HEADs completos, seleções e casos de cada linha; [audit.json](../../target/feature006-t029-consolidation-20261019T1646/audit.json) registra a conferência estática desta tarefa.

Na tabela, T/F/E/I = testes/falhas de asserção/erros/ignorados. O nome após `feature006-` identifica o sufixo Surefire e seu alcance; o link abre o console **da execução anterior**, não uma nova execução da T029. HEAD identifica a base Git daquele momento; as working trees com alterações e suas condições constam dos metadados/seções datadas, sem atribuir todos os checkpoints ao HEAD atual.

| Checkpoint/sufixo de relatório | T/F/E/I | Exit | HEAD base | Console preservado |
| --- | --- | ---: | --- | --- |
| `t002-baseline` | 144/0/0/0 | 0 | `ae00975` | [console](../../target/feature006-t002-baseline-20261018T181411/console.log) |
| `t003-preparation` | 2/0/0/0 | 0 | `55ad4a0` | [console](../../target/feature006-t003-preparation-20261018T183008/console.log) |
| `t004-characterization` | 2/0/0/0 | 0 | `55ad4a0` | [console](../../target/feature006-t004-characterization-20261018T183724/console.log) |
| `t005-name-tests` | 10/10/0/0 | 1 | `c090c52` | [console](../../target/feature006-t005-name-tests-20261018T185337/console.log) |
| `t006-length-tests` | 4/4/0/0 | 1 | `c090c52` | [console](../../target/feature006-t006-length-tests-20261019T055523/console.log) |
| `t007-group-red` | 18/14/0/0 | 1 | `c090c52` | [console](../../target/feature006-t007-group-red-20261019T060350/console.log) |
| `t008-rename` | 78/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t008-rename-20261019T060910/console.log) |
| `t009-normalizer` | 11/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t009-normalizer-20261019T061848/console.log) |
| `t010-public-integration` | 21/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t010-public-integration-20261019T062857/console.log) |
| `t011-public-expectations` | 6/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t011-public-expectations-20261019T063543/console.log) |
| `t012-us1-green` | 83/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t012-us1-green-20261019T064209/console.log) |
| `t012-public-green` | 18/0/0/0 | 0 | `c090c52` | [console](../../target/feature006-t012-us1-green-20261019T064209/console-public.log) |
| `t013-whitespace` | 38/0/0/0 | 0 | `529aefd` | [console](../../target/feature006-t013-whitespace-20261019T065910/console.log) |
| `t014-internal-spaces` | 6/0/0/0 | 0 | `529aefd` | [console](../../target/feature006-t014-internal-spaces-20261019T070511/console.log) |
| `t015-service-spaces` | 13/0/0/0 | 0 | `529aefd` | [console](../../target/feature006-t015-service-spaces-20261019T071122/console.log) |
| `t016-us2-http` | 44/0/0/0 | 0 | `529aefd` | [console](../../target/feature006-t016-us2-checkpoint-20261019T071700/console-http.log) |
| `t016-us2-services` | 22/0/0/0 | 0 | `529aefd` | [console](../../target/feature006-t016-us2-checkpoint-20261019T071700/console-services.log) |
| `t017-pagination` | 2/0/0/0 | 0 | `b5c0a24` | [console](../../target/feature006-t017-pagination-20261019T073140/console.log) |
| `t018-empty-pages` | 14/0/0/0 | 0 | `b5c0a24` | [console](../../target/feature006-t018-empty-pages-20261019T105700/console.log) |
| `t019-request-independence` | 2/0/0/0 | 0 | `b5c0a24` | [console](../../target/feature006-t019-request-independence-20261019T110800/console.log) |
| `t020-us3-checkpoint` | 18/0/0/0 | 0 | `b5c0a24` | [console](../../target/feature006-t020-us3-checkpoint-20261019T111300/console.log) |
| `t021-mvc` | 39/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t021-mvc-20261019T112600/console.log) |
| `t022-public-http` | 2/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t022-public-http-20261019T113600/console.log) |
| `t022-public-http-access` | 2/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t022-public-http-20261019T113600/console-access.log) |
| `t023-details-legacy` | 2/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t023-details-legacy-20261019T115000/console.log) |
| `t024-regression` | 46/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t024-regression-20261019T120019/console.log) |
| `t025-focused` | 287/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t025-focused-20261019T120835/console.log) |
| `t026-regression` | 253/0/0/0 | 0 | `feb09d7` | [console](../../target/feature006-t026-regression-20261019T121843/console.log) |
| `t028-final` | 2754/0/64/0 | 1 | `0f6a47d` | [console](../../target/feature006-t028-final-20261019T1601/console.log) |
| `t028-repro-red` | 3/0/2/0 | 1 | `0f6a47d` | [console](../../target/feature006-t028-recovery-20261019T1626/repro-red/console.log) |
| `t028-repro-green` | 3/0/0/0 | 0 | `0f6a47d` | [console](../../target/feature006-t028-recovery-20261019T1626/repro-green/console.log) |
| `t028-full-green` | 2754/0/0/0 | 0 | `0f6a47d` | [console](../../target/feature006-t028-recovery-20261019T1626/full-green/console.log) |

T002 comprova a baseline anterior à integração; T003 prepara controles. T004 tem **2 testes JUnit e 88 HTTP reais**, não 88 testes: 48 respostas 200 e 40 rejeições 400 esperadas, em raiz e `/api`, com/sem name e token inválido. Defaults/vazios 0/12, size 1/50, campos/preço e cabeçalhos preservados. Limites Bean Validation usam diretamente `status/error/message/path`; conversões nativas usam `timestamp/status/error/path`, sem impor DTO novo. A [baseline bruta T004](../../target/feature006-t004-characterization-20261018T183724/http-observations.json) permanece anterior à mudança. Em T025, [130 comparações com a baseline](../../target/feature006-t025-focused-20261019T120835/t004-error-comparisons.json), 52 nativas, confirmam corpo/status/cabeçalhos; timestamps são validados e normalizados somente para comparar instantes diferentes.

**RED funcional da busca**: T005 (10/10 falhas), T006 (4/4) e T007 (18 testes, 14 falhas, quatro controles GREEN) registram `name` ainda ignorado, com erros zero e preparação válida. Os 14 casos de negócio se sobrepõem entre essas execuções; não são 28 casos distintos. T008/T009 caracterizam renomeação/extração existentes, sem RED artificial; T010/T011 integram o filtro e substituem somente as expectativas públicas de name ignorado. T012 corresponde a duas execuções: a seleção mista inicial passou 83 testes, mas omitiu aninhados públicos; o complemento executou os 18 faltantes. **101 é agregado disjunto, não resumo de um único Maven**. A omissão de discovery não é apagada nem tratada como GREEN integral antecipado.

T013–T016 comprovam US2; T016 usa duas seleções disjuntas, **44 HTTP JUnit + 22 unitários = 66 agregados**, sem um console único de 66. T017–T020 comprovam US3; T021–T024 acrescentam fronteira MVC, acesso HTTP, detalhes/legado e histórico. T022 tem duas execuções de 2 testes cada: paginação/representação primeiro e complemento necessário para comparar também CLIENT autenticado em catálogo/detalhe, mantendo o diagnóstico de cobertura original. T025 e T026 são dois GREENs separados, **287 e 253**, nas respectivas seleções; não uma suíte de 540 testes. As contagens pequenas dos métodos parametrizados que realizam várias chamadas não devem ser confundidas com o número de cenários, requisições ou assertions.

T027 é **auditoria documental**, sem execução Maven: 27 cenários, 14 FR e 6 SC, matriz/assertions e gates confrontados com código e evidências T025/T026. Seus [resultados detalhados](../../target/feature006-t027-audit-20261019T124044/audit.json), inclusive 318 corpos registrados conferidos, são preservados. Na T029, a matriz abaixo foi vinculada também aos métodos efetivamente presentes na suíte final, sem reexecutá-los.

### As quatro execuções de T028 e a revisão realmente aprovada

1. **Primeira suíte completa**: `feature006-t028-final`, **2.754/0/64/0, exit 1**, BUILD FAILURE. Os 28 erros de catálogo e 36 de detalhe chegam à ausência de TB_PRODUCT (H2 42104); o GREEN parcial do grupo do filtro não aprova a suíte. [Resultado original](../../target/feature006-t028-final-20261019T1601/run-result.json) e [64 erros/causas](../../target/feature006-t028-final-20261019T1601/errors.json) permanecem intactos.
2. **Reprodução antes da correção**: `repro-red`, **3/0/2/0, exit 1**. Mesmos métodos e ordem console H2 → catálogo → detalhe; cache 1 apenas no ensaio, TRACE de cache e DEBUG de SQL. [Comando e resultado RED](../../target/feature006-t028-recovery-20261019T1626/repro-red/result.json).
3. **Mesma reprodução após a correção**: `repro-green`, **3/0/0/0, exit 0**, mesma seleção, ordem, cache e logs; muda somente o sufixo e o inicializador corrigido. [Comando e resultado GREEN](../../target/feature006-t028-recovery-20261019T1626/repro-green/result.json). A aprovação é conjunta e posterior ao fechamento induzido, não de uma classe isolada.
4. **Nova suíte completa final**: `full-green`, **2.754/0/0/0, exit 0**, BUILD SUCCESS, **sem filtros/skips/ordem forçada/cache reduzido/logging diagnóstico**. [Resultado final](../../target/feature006-t028-recovery-20261019T1626/full-green/result.json), [conferência de 88 XMLs](../../target/feature006-t028-recovery-20261019T1626/full-green/audit-full.json) e [mesmos 2.754 casos da suíte reprovada](../../target/feature006-t028-recovery-20261019T1626/full-green/case-preservation.json). Zero casos excluídos; contêineres externos com zero e relatórios agregadores aninhados não são somados duas vezes.

Comando da suíte aprovada, **executado na T028 e não nesta T029**:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-t028-full-green" test
```

A causa reproduzida é o fechamento do contexto do console, cujo `create-drop` executa DROP no banco nomeado compartilhado com o novo contexto público; `DB_CLOSE_DELAY=-1` não impede DROP explícito. O [diagnóstico comparativo](../../target/feature006-t028-recovery-20261019T1626/diagnosis.json) mostra criação do schema público, fechamento do console, DROP e GETs falhando antes da correção. A política/métricas de cache da primeira suíte não foram instrumentadas retroativamente; o cache 1 é condição do ensaio controlado, explicitamente distinta da configuração habitual da suíte final.

A correção preservada acrescenta somente o inicializador de oito linhas em [H2ConsoleSecurityIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java), com URL H2 exclusiva e estável por contexto raiz. O fechamento e o DROP continuam no GREEN focal, agora sobre o banco próprio, enquanto catálogo e detalhe conservam seu schema. Não se removeram `create-drop`, seed, asserções, cobertura ou testes, nem se alterou produção/configuração global para mascarar a ausência de schema.

**Código aprovado pela suíte final**: HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95` + correção local de H2, SHA-256 `37A537619328152ABAE19948091DA69E13EB058C16E90E2269AADB4BC12CF65E`, igual no GREEN focal, no final e agora. Execução final de 16:28:33.642 a 16:40:15.509 (-03:00), 701,8666 s externos/11:35 min Maven, Windows 10 amd64, Java Azul 21.0.12.1 e Wrapper configurado Maven 3.9.16. O commit-base sozinho, uma checkbox ou o Draft não substituem essa identificação da working tree. A conferência documental posterior não requer repetir os testes e não transforma a primeira falha em sucesso retroativo.

### Matriz consolidada: cenário → método/assertion → execução preservada

Métodos M1–M9 pertencem ao grupo HTTP `PublicCatalogNameFilterIntegrationTest`, sempre nos contextos raiz e `/api`. M10 pertence a `AdminProductPublicRegressionIntegrationTest`. **C** significa a execução completa `feature006-t028-full-green`, já realizada. Cada linha mantém o oráculo concreto da auditoria T027 e aponta o método encontrado em C; a presença no XML é conferência estática de execução anterior, não uma nova execução de assertions. T025 preserva os registros HTTP individuais e T026 as regressões específicas.

- **M1**: `matchesNameSubstringCaseAccentsAndLiterals`.
- **M2**: `acceptsShortAndLongNameFilters`.
- **M3**: `normalizesOnlyExternalFilterWhitespace`.
- **M4**: `preservesInternalSpacesAndStoredNames`.
- **M5**: `paginatesFilteredResultsInStoredNameIdOrder`.
- **M6**: `returnsCoherentEmptyFilteredPages`.
- **M7**: `doesNotRetainNameBetweenRequests`.
- **M8**: `preservesPublicPaginationShapeAndAccess`.
- **M9**: `preservesDetailsAndLegacyIndependentlyOfFilter`.
- **M10**: `ordersKeepAuthorizationNewOrderPerSendAndHistoricalValuesAfterMaintenance`.

| Cenário | Método e assertion/oráculo preservado | Execução que sustenta a prova |
| --- | --- | --- |
| US1.1 | M1: Somente Fone Azul/Telefone Preto para fone, excluindo Cabo. | T025; C em RootContext + ApiContext. |
| US1.2 | M1: fone/FONE/FoNe: IDs, ordem e totais iguais. | T025; C em RootContext + ApiContext. |
| US1.3 | M1: CAFÉ e cafe selecionam as formas acentuada e não acentuada distintas. | T025; C em RootContext + ApiContext. |
| US1.4 | M1: 10% seleciona Oferta 10%, sem expansão para Oferta 100. | T025; C em RootContext + ApiContext. |
| US1.5 | M1: kit_ seleciona Kit_A, excluindo KitXA. | T025; C em RootContext + ApiContext. |
| US1.6 | M2: Filtro f de um caractere é aceito com seleção exata. | T025; C em RootContext + ApiContext. |
| US1.7 | M2: 81 letras selecionam somente o nome de 81, sem truncar para 80. | T025; C em RootContext + ApiContext. |
| US2.1 | M3: Padding externo comum equivale ao trecho FONE. | T025; C em RootContext + ApiContext. |
| US2.2 | M3: Omitido, vazio e somente whitespace equivalem à página sem filtro. | T025; C em RootContext + ApiContext. |
| US2.3 | M4: Um e dois espaços internos selecionam nomes distintos. | T025; C em RootContext + ApiContext. |
| US2.4 | M4: Nome legado original com espaços devolvido intacto e snapshot igual. | T025; C em RootContext + ApiContext. |
| US2.5 | M3: Tab/NBSP/U+2003 externos, isolados/combinados, removidos só nas extremidades. | T025; C em RootContext + ApiContext. |
| US2.6 | M4: NBSP e espaço comum internos não são intercambiáveis. | T025; C em RootContext + ApiContext. |
| US3.1 | M5: Cinco correspondências distribuídas 2/2/1, totais 5/3 e união exata. | T025; C em RootContext + ApiContext. |
| US3.2 | M5: Nome armazenado/ID ordenam sem duplicar produto com duas categorias. | T025; C em RootContext + ApiContext. |
| US3.3 | M6: Nenhuma correspondência: 200/vazio/totais zero, page0 e page2 preservadas. | T025; C em RootContext + ApiContext. |
| US3.4 | M6: Page3 size2 vazia mantém totais 5/3, sem clamp. | T025; C em RootContext + ApiContext. |
| US3.5 | M6: Banco vazio com/sem filtro e page0/2 mantém totais zero. | T025; C em RootContext + ApiContext. |
| US3.6 | M7: Omitir filtro após pesquisa recupera o manifesto completo independente. | T025; C em RootContext + ApiContext. |
| US4.1 | M8: Defaults independentes 0/12 e paginação vazia, name omitido/vazio/preenchido. | T025; C em RootContext + ApiContext. |
| US4.2 | M8: Size1/50, limites e abc; ErrorResponse direto e nativos comparados à T004. | T025; C em RootContext + ApiContext. |
| US4.3 | M8: Cinco campos da página/quatro do item e preço numérico, JSON exato. | T025; C em RootContext + ApiContext. |
| US4.4 | M8: Anônimo/Bearer inválido/CLIENT: dados equivalentes e detalhe público acessível. | T025; C em RootContext + ApiContext. |
| US4.5 | M9: Produto excluído da busca continua acessível por ID. | T025; C em RootContext + ApiContext. |
| US4.6 | M9: Categorias Alpha/Zeta únicas e 404/context path exatos. | T025; C em RootContext + ApiContext. |
| US4.7 | M9: Legado curto/longo/espaçado, preço zero/sem categorias, texto e snapshots intactos. | T025; C em RootContext + ApiContext. |
| US4.8 | M10: Autorização, novo pedido por envio, valores históricos e GETs filtrados sem mutação; regressões admin/login/pedido. | T025 + T026; C (M10 e regressões), guards do grupo público. |

Os 27 IDs são únicos (7 US1, 6 US2, 6 US3, 8 US4); os 14 FR e 6 SC conservam a vinculação detalhada na seção T027 e em seu audit.json. [matrix.json](../../target/feature006-t029-consolidation-20261019T1646/matrix.json) preserva linha de fonte, método, seletores e observações daquela auditoria. M1–M9 foram encontrados em ambos os contextos na suíte final e M10 nas regressões; essa relação não implica um teste independente para cada linha nem permite somar registros compartilhados como chamadas adicionais.

### Gates, contrato e limites consolidados

| Gate próprio da 006 | Resultado sustentado pela auditoria T027 e código preservado na T028/T029 |
| --- | --- |
| I — Java/Spring Boot | Conforme no recorte: stack/pom preservados, Java 21 e Wrapper existentes; suíte local final aprovada. |
| II — camadas | Conforme: binding/validação/delegação no controller, normalização/ramo/mapeamento no serviço, seleção/count/ordem no repositório; helper puro compartilhado. |
| III — DTOs | Conforme: campos públicos preservados e preço numérico, sem exposição de entidades; HTTP real e MVC complementar. |
| IV — persistência/validação | Conforme ao novo fluxo 006, sem exceção nova: paginação validada antes de negócio, name sem restrição nova; JPA/readOnly e leitura sem manutenção/validação retroativa. Isolamento corrigido apenas no teste do console. |
| V — erros/REST | Conforme: GET/200 inclusive vazio, 400/404 e erros nativos anteriores preservados; handler central e segurança sem alteração. |
| Backend Constraints | Conforme ao recorte documentado: 27 cenários/14 FR/6 SC, sem novo domínio/schema produtivo, dependência ou estado de busca. |
| Workflow/Governance | Evidências T002–T029 identificadas; falha e correção preservadas, nova suíte final aprovada. **T030 ainda pendente**; não é fechamento global, integração em main ou validação remota. |

**Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, pelos diagnósticos de serviços durante `isValid` da escrita, conforme [plano](../005-manter-produtos/plan.md), [pesquisa](../005-manter-produtos/research.md) e [fechamento históricos](../005-manter-produtos/quickstart.md). A conformidade do fluxo de leitura 006 e o GREEN da suíte não reclassificam essa exceção. Esses documentos e a constituição permanecem intactos.

**Tipos de evidência distintos**:

- **Documental nesta T029**: leitura/consolidação, parsing de JSON/XML existentes, conferência de contagens/casos/métodos, hashes, caminhos, referências e diff. O [auxiliar estático](../../target/feature006-t029-consolidation-20261019T1646/audit.ps1) foi chamado por `powershell -NoProfile -File target/feature006-t029-consolidation-20261019T1646/audit.ps1`: primeira tentativa interrompida ao contar propriedades do JSON no PowerShell; ajustada somente essa enumeração, segunda tentativa exit 0. Não foi falha de teste ou execução Maven.
- **Estrutural de contrato, histórica**: OpenAPI 3.0 validado no planejamento com openapi-spec-validator 0.9.0, schemas/exemplos conferidos e invalidSize validado diretamente contra ErrorResponse. Contrato 006 inalterado; nenhum validador repetido agora. Essa prova é do documento, não dos envelopes HTTP. O anyOf aberto de 400 não substitui as assertions específicas de limites nem a comparação dos erros nativos.
- **Comportamento, nas execuções anteriores**: testes e HTTP/MockMvc/consultas efetivamente exercitados nos checkpoints e na suíte final, com escopos indicados. Os 27 cenários não são contagem JUnit. T025 tem 287 testes, 594 HTTP reais e 266 dispatches MockMvc; T026 tem 253 testes e zero HTTP real, com 920 dispatches calculados pelos fluxos, dos quais 588 confirmados pelo contador existente. Essas operações não são testes extras, nem foram repetidas nesta T029.

Ambiente comprovado é **Windows/H2 local**. Não há validação desta entrega em **PostgreSQL**, outro banco ou execução remota/CI/Linux; resultados históricos do harness em outras revisões não aprovam este HEAD/working tree. Não se inferem desempenho/SLA, concorrência ou durabilidade. Ausência de mutação refere-se às janelas de GET com inspector Hibernate e snapshots independentes antes da limpeza; readOnly/snapshot isolados não provam ausência universal de JDBC direto ou efeitos assíncronos. Os INSERTs deliberados e rollback dos testes de pedido não são declarados zero DML global. Mocks complementam os oráculos reais e não substituem SQL/HTTP. Artefatos em `target/` são locais ignorados e podem faltar em outro checkout; links existentes nesta máquina não equivalem a publicação.

Os procedimentos futuros que permanecem no início do guia continuam sugestões do planejamento; os comandos literais das tabelas/metadados identificam execuções já realizadas. T030 continua o próximo item aberto, sem autorização implícita para executá-lo, stage/commit/push ou alterar main.

**Conclusão T029**: evidências T002–T028 consolidadas e conferidas nos artefatos existentes, com diagnóstico/limites/gates e matriz de rastreabilidade preservados; nenhum impedimento documental encontrado. Marcar somente T029 nesta execução e manter T030 aberta. Produção, testes, asserções, correção H2 e os registros históricos recebidos permanecem preservados.

Revisão final da entrega T029: acréscimo documental e diff recebidos revisados; comandos, fontes da matriz, referências e **386 links locais com destinos existentes** conferidos. `git diff --check` exit 0. O histórico recebido do quickstart permanece byte a byte como prefixo; tasks mudou somente em T029; os outros **255 rastreados**, incluindo a correção H2 e todas as asserções, e os **756 relatórios Surefire** mantiveram seus hashes. Conferência em `target/feature006-t029-consolidation-20261019T1646/review.json`. Nesta execução foram editados somente quickstart/tasks. Git final mantém os mesmos três arquivos modificados recebidos (quickstart, tasks, teste do console H2), nenhum staged ou não rastreado; branch/HEAD e main preservados. Ponteiro idêntico e `.specify/extensions.yml` ausente após execução, sem hooks aplicáveis. T029 `[X]`, T030 `[ ]`. Validação desta tarefa exclusivamente documental/estática, sem repetição de testes ou do validador de contrato.

## Fechamento local da T030 — revisão final da Feature 006

**Contexto confirmado em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `006-filtrar-catalogo-produtos`, HEAD `0f6a47d6c831edf01c6dcccb8a43e4adb733fe95`. Recebidos exatamente quickstart/tasks e `H2ConsoleSecurityIntegrationTest.java` modificados, nenhum staged ou não rastreado. Lidas integralmente T030, sua dependência T029 e as condições de conclusão; revisão limitada ao fechamento, preservando todo o histórico recebido.

Pré-requisitos executados pelo script PowerShell com `-Json -RequireTasks -IncludeTasks` e `SPECIFY_FEATURE_DIRECTORY` absoluto da 006 temporário: exit 0, FEATURE_DIR correto, cinco documentos opcionais. Variável e bytes do ponteiro restaurados em `finally`; [prerequisites.json](../../target/feature006-t030-final-review-20261019T1706/prerequisites.json). Checklist somente lido: 16/16, zero pendentes. Ignores Java/Maven suficientes; nenhum arquivo de configuração necessário. `.specify/extensions.yml` ausente, sem hooks aplicáveis. [Estado inicial e referências Git](../../target/feature006-t030-final-review-20261019T1706/session-start.json), manifestos de fontes/relatórios e cópias dos documentos recebidos preservados no mesmo diretório local ignorado.

### Procedimentos e coerência conferidos

Revisados o diff completo da working tree (`git diff`, incluindo os oito acréscimos do console H2), os caminhos de arquivos novos já commitados e o escopo da implementação desde a base `d2f76b0edaeebf070b861dc38038fd42d0e83a62` por `git diff --name-status <base> HEAD -- src/main src/test specs/006-filtrar-catalogo-produtos`. Esse recorte contém os cinco arquivos produtivos previstos, oito arquivos de teste e quickstart/tasks; a correção local posterior acrescenta somente o isolamento no teste do console. Nenhum arquivo não rastreado a incorporar e nenhum trabalho alheio incluído.

| Fontes confrontadas | Resultado da revisão final |
| --- | --- |
| Spec, checklist, tasks e matrizes T027/T029 | 27 cenários (7/6/6/8), 14 FR e 6 SC com métodos/assertions e execuções anteriores identificados; dependências T001–T029 atendidas antes deste fechamento. O checklist é de qualidade documental, separado da comprovação funcional. |
| Plano, pesquisa, modelo e implementação | Controller recebe `name` opcional; helper puro normaliza apenas extremidades; serviço escolhe ramo vazio/filtrado; repositório reaproveita predicado literal parametrizado, count equivalente e ordem nome armazenado/ID. Sem entidade, migração, dependência, estado de busca ou configuração produtiva nova. |
| Contrato 006 e contratos históricos 001/002 | Campos/tipos públicos, paginação 0/12 e size 1–50, acesso, erros e detalhe por ID coerentes com T027/T029 e fontes atuais. Limites conferidos contra ErrorResponse diretamente nas provas existentes; `anyOf` aberto não é tomado como prova suficiente. Três referências locais do YAML resolvidas; contrato inalterado. |
| Código atual e revisão da suíte aprovada | **255 hashes rastreados fora de quickstart/tasks iguais** ao manifesto anterior à suíte `full-green`, incluindo produção, todos os testes/assertions e artefatos protegidos. Não há mudança funcional posterior que justifique repetir os testes. |
| Diff do console H2 | Somente o inicializador `@DynamicPropertySource` de oito linhas: URL exclusiva e estável por contexto raiz. Removendo esse bloco, recupera-se o fonte anterior integralmente, normalizando só terminações de linha. SHA-256 atual `37A537619328152ABAE19948091DA69E13EB058C16E90E2269AADB4BC12CF65E`, igual aos GREENs focal/completo; asserções, seed, create-drop e configuração global preservados. |
| Gates e governança | Aderência do fluxo novo 006 conforme T027/T029; revisão de fechamento agora concluída. **Gate IV da Feature 005 continua EXCEÇÃO DOCUMENTADA, sem PASS estrito**; não se transforma em PASS global. Spec/checklist, plano/pesquisa/modelo/contratos, documentos históricos e constituição intactos. |

Procedimentos estáticos executados por `powershell -NoProfile -File target/feature006-t030-final-review-20261019T1706/audit.ps1`, exit 0: comparação de hashes com a suíte aprovada, comparação integral do fonte H2 sem o bloco acrescentado, diffs de proteção, referências Git e existência dos destinos de **421 links Markdown locais nos sete documentos da 006**, mais as três referências locais do contrato, antes deste acréscimo. [audit.json](../../target/feature006-t030-final-review-20261019T1706/audit.json) registra resultados e caminhos. `git diff --exit-code HEAD -- <artefatos protegidos>` e comparação desde a base dos DTOs, entidades, configuração produtiva, handlers e pom retornaram exit 0. A conferência de destinos locais não verifica disponibilidade de URLs externas nem publica os arquivos ignorados em `target/`.

Os descritores `Draft`, `planned`, os textos de geração das tasks e os procedimentos futuros no início do guia permanecem registros dos respectivos checkpoints. A conclusão local atual decorre de código, execuções identificadas e revisão final; não se reescrevem requisitos aprovados ou conclusões datadas para lhes atribuir execução posterior.

### Evidências preservadas e limites da conclusão

**Nenhum Maven, seleção focal, HTTP novo ou validador OpenAPI executado nesta T030.** A auditoria consolidada da T029 (32 execuções históricas/282 XMLs) foi reutilizada; não repetida como teste. Continuam distintos:

- Checkpoints focais e regressões anteriores, com seleções/limites próprios, conforme inventário T029.
- Primeira suíte completa T028: **2.754 testes, 0 falhas, 64 erros, 0 ignorados, exit 1**, preservada como falha.
- Reprodução controlada na mesma ordem console → catálogo → detalhe: **3 testes/2 erros antes** e **3 aprovados depois**, cache 1 somente no ensaio. O mecanismo reproduzido foi DROP pelo fechamento do contexto sobre H2 compartilhado; métricas de cache não foram retroativamente atribuídas à primeira suíte.
- Nova suíte completa T028: **2.754 testes, zero falhas/erros/ignorados, exit 0**, sem seleção ou parâmetros diagnósticos. Mesmos 2.754 casos da suíte reprovada, na working tree identificada acima. Essa é a execução anterior aprovada, não uma execução da T030.

A validação desta tarefa é **documental/estática**. A validação **estrutural OpenAPI 0.9.0** continua histórica, do planejamento; a comprovação de **comportamento em execução** pertence aos testes/HTTP efetivamente executados e datados. O resultado local comprovado é Windows/H2; não há validação desta revisão em PostgreSQL, CI remoto ou Linux, nem garantia adicional de concorrência, durabilidade ou SLA. Permanecem os limites de observação SQL/snapshots/janelas e de mocks registrados anteriormente.

`git rev-parse main` confirmou `20495b487379e260a4c967823997bff872920dc9`; `git merge-base --is-ancestor HEAD main` retornou **1**, portanto este HEAD não está integrado à **main local**. Não se consultou estado remoto nem se abriu PR. Conclusão local da feature, validação remota e integração são estados distintos. CI desta entrega e eventual integração/publicação permanecem fora do escopo autorizado; não há impedimento funcional ou documental local remanescente identificado pela revisão. Nenhum stage, commit, push, troca de branch, alteração de main ou início de outra feature.

**Conclusão T030**: critérios locais atendidos; marcar somente T030 nesta execução e conferir T001–T030 `[X]`. Alterados nesta tarefa somente quickstart/tasks, preservando a correção H2 recebida e todas as evidências anteriores. A Feature 006 fica concluída localmente na working tree descrita, sem alegação de entrega remota ou integração.

Conferência final por `powershell -NoProfile -File target/feature006-t030-final-review-20261019T1706/review.ps1`: exit 0; **T001–T030, exatamente 30 IDs únicos, todos `[X]`**; diff e acréscimo revisados; **424 links locais** com destinos existentes nos sete documentos da 006 e três referências do YAML conferidas separadamente; `git diff --check` exit 0. Quickstart recebido preservado byte a byte como prefixo; tasks alterado somente no marcador T030; os outros **255 rastreados** e os **756 relatórios Surefire** preservados por tamanho/hash conforme aplicável. Resultado em `target/feature006-t030-final-review-20261019T1706/review.json`. Git final: somente quickstart, tasks e `src/test/java/com/devsuperior/dscommerce/config/H2ConsoleSecurityIntegrationTest.java` modificados, nenhum staged ou não rastreado. Branch/HEAD, main e ponteiro mantidos; extensões continuam ausentes após a execução. Nenhum teste ou validador de contrato repetido pelo fechamento documental.

## Correção local do impedimento remoto do PR #6 — contexto JWT e schema H2

**Estado recebido em 2026-10-19**: raiz `C:/Users/ronal/dscommerce-speckit`, branch `006-filtrar-catalogo-produtos`, HEAD `ddc2ab35110beefca5bc0d45f385c37d1187530b`, Git limpo. O [PR #6](https://github.com/RODR1GU3S/dscommerce-speckit/pull/6) foi consultado: aberto em **Draft**, mesmo HEAD, base `20495b487379e260a4c967823997bff872920dc9`. Main local/origin preservadas nessa base. Escopo desta retomada: investigar e corrigir somente o impedimento da CI, sem stage/commit/push, rerun remoto ou alteração do PR/main. T001–T030 continuam `[X]` como histórico de conclusão local; isso não encerra a pendência remota identificada posteriormente.

### Evidência remota examinada antes de novos testes

A [execução automática 39319142778](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/39319142778), evento `pull_request`, tentativa 1, terminou **FAILURE/exit 1**: **2.754 testes, zero falhas de asserção, 36 erros e zero ignorados**. Logs confirmam checkout de `refs/pull/6/merge`, SHA `f69d9fb1a9693fc3a0b441d98070fe5643b55840`, cujos pais são a base `20495b4…` e o HEAD `ddc2ab3…`; árvore igual à da feature. Ambiente remoto: Ubuntu 24.04, Zulu Java 21.0.12, Maven Wrapper 3.9.16; comando `./mvnw --batch-mode --no-transfer-progress test`.

Console integral e os **88 XMLs/88 TXTs** do [artefato remoto](https://github.com/RODR1GU3S/dscommerce-speckit/actions/runs/39319142778/artifacts/12220105350) foram obtidos somente por leitura e preservados localmente, fora de `target/surefire-reports/`: [console decodificado](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-console.log), [ZIP original](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-surefire-reports.zip), [relatórios extraídos](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-surefire/), [auditoria](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-audit.json) e [manifesto](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-manifest.json). ZIP SHA-256 `FB8526A382483959E807D4E971E42D926AD3B0548376BBDA403DEC4AE9A3E2F8`, igual ao digest do GitHub; retenção remota anunciada até 2026-11-02. O sucesso do upload não aprova o build.

A primeira causa relevante dos **36 erros** é **H2 42104, `Table "TB_PRODUCT" not found (this database is empty)`**; todos os elementos de erro contêm essa causa. São 18 `BadSqlGrammarException` e 18 `ServletException`, todos com classname externo `PublicProductDetailSecurityIntegrationTest`. O primeiro caso listado é `shouldPreserveExactDetailShapeAndValuesForTheSelectedProduct(boolean, long)[1]`, falhando em `assertSeedUnchanged()`/`select count(*) from tb_product`. O relatório aninhado `DuplicateCategoryNames` agrega 38 casos, dos quais dois próprios passam e 36 externos erram; não atribuir os 36 ao banco isolado do aninhado nem somar contêineres de zero. Console e XMLs concordam. Console H2 (15), filtro público (84) e segurança do catálogo (33) passam, como resultados parciais da suíte reprovada.

### Diagnóstico: outro contexto compartilhava o banco

Inspecionados perfil test, classes afetadas, demais configurações de contexto e URLs, antes de executar Maven. `JwtTokenServiceTest` usa `@SpringBootTest`/perfil test sem MockMvc ou URL própria: inicializa JPA mesmo sem consultar produtos. Catálogo e detalhe externos usam configuração pública MockMvc equivalente e reutilizam seu contexto, mas são distintos do contexto JWT. O perfil mantém URL nomeada `jdbc:h2:mem:dscommerce-test`, `ddl-auto=create-drop` e inicialização de seeds.

Na CI, **HikariPool-2 pertence ao contexto JWT**: abre `dscommerce-test` às 20:25:40.321 UTC. **HikariPool-41 pertence ao contexto público do catálogo**: abre o mesmo banco às 20:27:15.212. Às 20:29:05.903–05.905 há fechamento do EntityManagerFactory e shutdown do pool 2; depois os detalhes externos reutilizam o contexto público sem nova criação de seu schema e encontram TB_PRODUCT ausente. Os eventos selecionados estão em [remote-lifecycle-events.txt](../../target/feature006-pr6-ci-recovery-20261019T1738/remote-lifecycle-events.txt).

A correção anterior do console está presente e funciona no recorte observado: pool 33 usa `h2-console-root-5b4a5c2c-b4e1-4a8b-bb30-02ed26c05261`; seu fonte permanece SHA-256 `37A537619328152ABAE19948091DA69E13EB058C16E90E2269AADB4BC12CF65E`. Portanto não se atribuiu a nova falha automaticamente ao console. O compartilhamento restante é JWT versus público. O console remoto não tinha TRACE de cache/SQL: a correlação acima não fornece métricas de expulsão ou DROP medidos retroativamente. O mecanismo foi confirmado no ensaio controlado seguinte, também em Windows; não há evidência de causa específica do Linux.

O [console da suíte local aprovada anterior](../../target/feature006-t028-recovery-20261019T1626/full-green/console.log) mostra console → catálogo → detalhe → JWT (linhas 1169, 12970, 13042 e 14838), enquanto na CI JWT precede catálogo/detalhe. Essa é uma diferença de ordem observada, sem ordem configurada pela entrega: aquela aprovação local não exercitou o descarte do contexto JWT antes da leitura de detalhe. A reprodução abaixo confirma a interação em Windows, sem depender de atribuição ao sistema operacional ou de repetir toda a ordem/quantidade de contextos remotos.

### Reprodução mínima antes/depois, com mesma seleção e ordem

Antes do ensaio, os **756 relatórios locais recebidos**, incluindo todas as falhas e aprovações anteriores, foram copiados para [surefire-before](../../target/feature006-pr6-ci-recovery-20261019T1738/surefire-before/) com [manifesto de hashes](../../target/feature006-pr6-ci-recovery-20261019T1738/reports-before.json); salvos os 257 hashes rastreados, documentos e fonte JWT anteriores. O ensaio seleciona somente **dois métodos existentes**, JWT → detalhe, usando `reversealphabetical` para fixar essa ordem. Cache 1 e TRACE/SQL DEBUG são parâmetros somente do diagnóstico, sem editar configuração global ou asserções para provocar o erro:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-pr6-ci-repro-red" "-Dtest=JwtTokenServiceTest#shouldIssueNonEmptyVerifiableHs256TokenWithRequiredSafeClaims,PublicProductDetailSecurityIntegrationTest#anonymousRequestShouldReturnExistingProductDetails" "-Dsurefire.runOrder=reversealphabetical" "-Dspring.test.context.cache.maxSize=1" "-Dlogging.level.org.springframework.test.context.cache=TRACE" "-Dlogging.level.org.hibernate.SQL=DEBUG" test
```

**RED:** 17:38:16.928–17:38:56.758 (-03:00), **2 testes, 0 falhas, 1 erro, 0 ignorados, exit 1**. JWT passa; detalhe falha com H2 42104. Ambos os pools abrem `dscommerce-test`. O público cria `tb_product` às 17:38:54.756 e termina a inicialização às 17:38:55.680; o fechamento do contexto JWT às 17:38:55.695 executa `drop table if exists tb_product cascade`, seguido de shutdown do pool 1. O GET falha às 17:38:56.180. Cache mostra `maxSize=1`, dois contextos criados. [Comando/resultado](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-red/result.json), [console e quatro relatórios](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-red/), [eventos](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-red/lifecycle-events.txt).

**Correção mínima:** somente [JwtTokenServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java) recebe três imports e um inicializador `@DynamicPropertySource` de oito linhas, com URL `jdbc:h2:mem:jwt-token-service-<UUID>;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`. O UUID é gerado uma vez ao inicializar o contexto e capturado pelo supplier; acessos posteriores conservam a URL. Cada contexto distinto passa a possuir o schema que cria/remove. Mantidos JPA, create-drop, seeds, métodos, asserções de token/claims e cobertura; nenhuma mudança de produção, do detalhe, da configuração global ou do console. Removendo somente bloco/imports acrescentados, recupera-se integralmente o fonte anterior, normalizando só terminações de linha.

**GREEN:** reexecutado o mesmo comando, trocando somente o sufixo por `feature006-pr6-ci-repro-green`, de 17:39:15.854 a 17:40:26.455 (-03:00): **2 testes, zero falhas/erros/ignorados, exit 0**. O pool JWT usa `jwt-token-service-e2304b09-78b5-4b95-99af-7e3441bbd56d`, enquanto o público mantém `dscommerce-test`. O público termina a inicialização às 17:40:24.843; o fechamento/DROP do JWT às 17:40:24.859–24.906 continua ocorrendo, mas o GET e suas asserções passam. Prova conjunta na mesma ordem e após o mesmo fechamento, não aprovação isolada de classe. [Comando/resultado](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-green/result.json), [console/relatórios](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-green/) e [eventos](../../target/feature006-pr6-ci-recovery-20261019T1738/repro-green/lifecycle-events.txt). Preservados 756 e 760 relatórios anteriores em cada execução, respectivamente.

[diagnosis.json](../../target/feature006-pr6-ci-recovery-20261019T1738/diagnosis.json) registra o par e a conferência estática por `verify-reproduction.ps1`, exit 0: comandos iguais salvo sufixo, ordem criação → fechamento → DROP → resultado, URLs e fonte/assertions preservados. Isso confirma o mecanismo de interferência por schema compartilhado; `DB_CLOSE_DELAY=-1` não impede DROP explícito. Não se transformam os parâmetros controlados em configuração da entrega ou em instrumentação retroativa da CI.

### Suíte completa desta correção e pendência remota

Após resolver o ensaio focal, iniciada **uma suíte completa local**, às `2026-10-19T17:40:41.6709897-03:00`, pelo [run.ps1](../../target/feature006-pr6-ci-recovery-20261019T1738/run.ps1), sequencialmente e com sufixo novo, sem seletores/skips/ordem forçada/cache reduzido/logging diagnóstico:

```powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=feature006-pr6-ci-full-green" test
```

Este parágrafo registra o início, não aprovação antecipada; resultado e conferências finais são acrescentados abaixo após o término. Revisão sob teste: HEAD `ddc2ab3…` + somente a correção JWT (SHA-256 `3A6F21227DA6EDD783AEA44ACA85837141CC9A953633C3AD1663B11D7D173DA7`, igual ao GREEN focal). Alterações documentais posteriores não são novas execuções. **A CI remota permanece reprovada/pendente até uma nova execução aprovada na revisão corrigida**; nenhum teste local pode reclassificar a execução 39319142778.

**Limites e preservação:** provas novas em execução são o par focal Windows/H2 e a suíte local identificada, conforme seu resultado final abaixo. Leitura/conferência de JSON/XML, hashes, diff e links é validação documental/estática; nenhum validador OpenAPI reexecutado, contrato intacto e validação estrutural 0.9.0 histórica. Sem validação desta correção em PostgreSQL ou nova CI/Linux, nem garantia adicional de todas as ordens/cache, concorrência, durabilidade ou desempenho. **Gate IV da Feature 005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**; histórico, spec, contratos, constituição e evidências anteriores preservados. Relatórios em `target/` são locais ignorados e podem faltar em outro checkout. Não houve stage, commit, push, rerun remoto, retirada do Draft, merge ou alteração de main.

**Resultado final da única suíte completa desta correção:** término `2026-10-19T17:52:07.4889913-03:00`, duração externa 685,8180 s; Maven informou `11:19 min`. **BUILD SUCCESS/exit 0, 2.754 testes, zero falhas, erros ou ignorados**. Ambiente dos XMLs: Windows 10 amd64, Java Azul 21.0.12.1; Wrapper configurado Maven 3.9.16. [Resultado/horários](../../target/feature006-pr6-ci-recovery-20261019T1738/full-green/result.json), [console integral](../../target/feature006-pr6-ci-recovery-20261019T1738/full-green/console.log) e [auditoria completa](../../target/feature006-pr6-ci-recovery-20261019T1738/full-green/audit-full.json). Conferidos os 88 XMLs e 88 TXTs novos contra os resumos por suíte e agregado, elementos de resultado, 80 classes concretas, ausência de seleção/skips e 2.754 casos únicos. Detalhe agora soma 38 casos sem erro no agregado (36 externos e dois do aninhado); os 84 casos do filtro, 33 de catálogo e 15 do console continuam aprovados, sem dupla contagem de contêineres vazios.

Conferências estáticas adicionais: `powershell -NoProfile -File target/feature006-pr6-ci-recovery-20261019T1738/verify-full.ps1` e `powershell -NoProfile -File target/feature006-pr6-ci-recovery-20261019T1738/review.ps1`, ambas exit 0, sem novo Maven. [Preservação de casos e fonte](../../target/feature006-pr6-ci-recovery-20261019T1738/full-green/case-preservation.json) confirma os mesmos 2.754 casos da suíte local inicialmente reprovada; a revisão também confrontou os mesmos 2.754 pares classname/método-invocação com os XMLs da **falha remota**, normalizando somente o sufixo local. Fonte JWT igual no GREEN focal, no completo e na entrega, SHA-256 `3A6F21227DA6EDD783AEA44ACA85837141CC9A953633C3AD1663B11D7D173DA7`; asserções/cobertura preservadas. Os 764 relatórios anteriores à suíte (756 recebidos + quatro RED + quatro GREEN) conservam hashes; com os 176 novos há **940 arquivos Surefire locais**. Preservados também os 176 relatórios remotos extraídos, ZIP e console originais, e todos os **255 rastreados fora de quickstart/JWT**, inclusive tasks, produção e correção H2 anterior. Quickstart recebido permanece byte a byte como prefixo. [review.json](../../target/feature006-pr6-ci-recovery-20261019T1738/review.json) registra a conferência de preservação e 442 links locais existentes antes destes acréscimos finais.

**Conclusão desta correção:** mecanismo reproduzido e resolvido no par focal; suíte completa local aprovada uma vez na revisão corrigida. Foram exatamente três execuções Maven novas, sequenciais: RED de dois métodos, GREEN da mesma seleção/ordem e suíte completa. As falhas remota de 36 erros e local anterior de 64 erros, a correção do console e os checkpoints prévios continuam históricos, sem conversão retroativa para GREEN. A leitura auxiliar de um console ainda em gravação encontrou uma vez bloqueio de compartilhamento; substituída por leitura compatível com arquivo em uso, sem afetar o Maven ou acrescentar execução de testes.

**Pendência obrigatória:** validar esta correção em nova CI remota após publicação autorizada. Consulta de leitura posterior confirmou PR #6 aberto em Draft, HEAD remoto ainda `ddc2ab35110beefca5bc0d45f385c37d1187530b`, mesma base `20495b487379e260a4c967823997bff872920dc9`; a execução 39319142778 continua FAILURE. A correção está somente na working tree, sem stage/commit/push ou rerun remoto. Não se declara aprovação remota, integração em main, validação PostgreSQL ou implantação.

Revisão final: diff completo dos dois arquivos, comandos, seleções, resultados, referências e **447 links locais com destinos existentes** conferidos; `git diff --check` exit 0. Conferência documental final em `target/feature006-pr6-ci-recovery-20261019T1738/document-review.json`, reutilizando a conferência já aprovada de preservação dos relatórios, sem repetir testes. Histórico anterior intacto como prefixo, fonte JWT igual ao testado e T001–T030 `[X]` sem alteração de tasks. Git final: somente `specs/006-filtrar-catalogo-produtos/quickstart.md` e `src/test/java/com/devsuperior/dscommerce/services/JwtTokenServiceTest.java` modificados; nenhum staged ou não rastreado. Branch, HEAD e main mantidos; correção H2 anterior, produção, asserções e exceção histórica da 005 preservadas. Pendência remota continua explícita.
