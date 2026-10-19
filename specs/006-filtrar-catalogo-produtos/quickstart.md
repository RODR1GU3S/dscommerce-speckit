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
