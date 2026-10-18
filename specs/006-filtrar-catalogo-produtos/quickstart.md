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
