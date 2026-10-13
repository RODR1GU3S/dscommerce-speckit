# Quickstart: validação futura de Manter produtos

**Branch**: `005-manter-produtos` | **Base documental**: `fba63732ddb2f27dc03da350d5684c05f3517b75` | **Data**: 2026-10-11

[Spec](spec.md), [checklist](checklists/requirements.md), [plano e matriz integral](plan.md), [pesquisa](research.md), [modelo](data-model.md), [OpenAPI](contracts/product-maintenance-openapi.yaml) e [regras do contrato](contracts/README.md) são as fontes. Spec mantém 20 requisitos, 37 cenários, 10 critérios; checklist 16/16 é qualidade documental, não implementação.

## Estado e evidência histórica

Feature 005 ainda não implementada. Nenhum teste da aplicação, demonstração ou comando Maven executado neste planejamento. Todos os procedimentos funcionais abaixo são futuros; nomes de classes novas são propostas, não evidência de arquivos existentes.

Linha de base histórica: [quickstart da Feature 004](../004-registrar-pedido/quickstart.md), seção da correção HTTP de 2026-10-11, registra suíte completa no Windows com Wrapper: **926 testes, 0 falhas, 0 erros, 0 ignorados**, BUILD SUCCESS, 19:08:34–19:11:32 UTC−03. Evidência de working tree anterior e fontes daquela execução, não nova execução em `fba6373` nem aprovação de manutenção administrativa. A CI #6 mencionada nesta sessão é histórica; não foi consultada/disparada nesta etapa. Não somar testes de execuções focalizadas e completas como casos distintos.

## Preparação futura

1. Confirmar raiz, branch real, HEAD e status/índice antes de implementar/validar; preservar trabalho existente. Ler AGENTS, constituição e artefatos da feature. Gerar tasks somente em checkpoint autorizado posterior.
2. Java 21 instalado; Wrapper existente seleciona Maven 3.9.16. Seguir requisitos de rede/cache/Unix no [harness](../../docs/harness.md). Não substituir evidência antiga de `mvn test` por execução fictícia do Wrapper.
3. Implementar antes de esperar GREEN das verificações novas. Quando exigido pelo fluxo futuro, registrar RED por comportamento faltante, não erro de compilação/preparação. Não alterar assertions ou baseline apenas para obter GREEN.
4. Preparar suporte de teste isolado, em H2 de nome único por contexto: contas com ADMIN, CLIENT, ambos e nenhum; categorias A/B de mesmo nome, categoria sem produto; produtos para filtros, legado, exclusão e compras. Fixtures com ownership/limpeza própria, sem acionar `OrderDemoApplication` ou substituir seeds compartilhadas indiscriminadamente.
5. Configurar segredo descartável somente no ambiente de teste/demo autorizado, sem registrar token/segredo nos logs. Obter JWT pelo login real de contas preparadas; payload não fornece conta/perfis. Não presumir que produção já possui Admin/categorias ou alterar provisionamento por esta feature.

Comandos abaixo são previstos, **não executados**. Na raiz, Windows/PowerShell:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd '-Dtest=AdminProductInputContractTest,ProductMaintenanceServiceTest,AdminProductRepositoryTest,AdminProductSecurityIntegrationTest,AdminProductMaintenanceIntegrationTest,AdminProductRollbackIntegrationTest,AdminProductConcurrencyIntegrationTest' test
.\mvnw.cmd '-Dtest=ProductControllerTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,LoginControllerTest,OrderSecurityIntegrationTest,OrderRegistrationIntegrationTest,OrderFrameworkHttpErrorIntegrationTest,OrderRollbackIntegrationTest,OrderPublicRegressionIntegrationTest,GlobalExceptionHandlerRegressionTest' test
.\mvnw.cmd test
```

Os nomes novos precisam ser concretizados em tasks/implementação; até lá a execução focalizada não é reproduzível como teste existente. Executar suíte completa depois das verificações afetadas aprovadas, preservando relatórios anteriores antes de cada invocação. Linux futuro: `./mvnw --batch-mode --no-transfer-progress test`. Nenhum comando de push, dispatch de CI ou implantação é necessário a este planejamento.

## Grupos de verificação futura e o que comprovam

| Grupo | Procedimento/oráculo | Pode comprovar | Limite |
| --- | --- | --- | --- |
| V01 — segurança | Full-context MockMvc com filtros/decoder/banco reais, JWT obtido em /login; todos os seis métodos/operações, perfis e credenciais; revogar ADMIN/remover conta entre filtro e diagnóstico do boundary e entre boundary/serviço de escrita; exercitar ValidationException envolvendo guarda; falha real de lookup em H2 isolado | ADMIN sem CLIENT, claims sem poder, 401/403/500, precedência e guardas, inclusive wrapper no validator; nenhum dado restrito/escrita em rejeição | Slice sem filtros ou token mockado não comprova autenticação real; guarda é avaliação naquele instante. |
| V02 — estrutura/HTTP | JSON literal com tipos inválidos simultâneos, extra, chave repetida, array parcialmente inválido, null/ausente, trailing JSON/lixo, raiz inválida; combinar corpo inválido com falta de acesso; 405/406/415 e context path; instrumentar diagnóstico rejeitado sem DML/entrada no serviço de manutenção | Leitura scoped completa, não coerção, erros seguros/por campo, status e headers; US2.6 expõe os cinco campos incluindo categoria inexistente, sem escrita durante diagnóstico | Parser/schema isolado não comprova lookup/segurança; corpo truncado não permite inventar campos; provas futuras do alcance da exceção IV exigem instrumentação e snapshot independente. |
| V03 — busca | Repository H2 real e HTTP: Fone/Telefone, Café/Cafe, espaços simples/duplos, %, _, barra/apóstrofo, filtro externo/vazio, empates, limites e páginas além; fixture com produto associado a várias categorias e navegação completa conforme procedimento abaixo | Ocorrência única por produto, totalElements correto e páginas sem repetição/perda; predicado literal, caixa/acento/espaços e ordem name/id | Dataset estável; não comprova linguística/Unicode canônico ou performance/SLA inexistente. |
| V04 — categorias/leitura | IDs distintos de mesmo nome, sem produtos e nenhuma categoria; GET opções/detalhe | ID/nome completo, ordem determinística, associações atuais sem CRUD/busca | Ver opções não prova proteção de FK ou associação gravada. |
| V05 — inclusão válida | HTTP 201/Location seguido de leitura administrativa/pública e SQL por outra conexão; A,A,B e A,A | Novo ID, textos normalizados, dinheiro exato, associação única, confirmação visível e categorias intactas | DTO/mocks não provam commit; sequência pode consumir ID em rollback. |
| V06 — limites/invalidade | Parametrizar limites 3/80/2/81 e 10/9, externos Unicode/internos, 0.01/máximo/overflow/zero/negativo/1.000/1.001, URL relativa/ftp/http/https/malformada/indisponível e >255, IDs inexistentes/tipos/seleção vazia | Regras administrativas completas, todos os campos detectáveis juntos, sem acesso externo; escrita rejeitada sem delta | Rejeição anterior à mutação não comprova rollback depois de DML. Instrumentar ausência de chamadas externas, não depender do site responder. |
| V07 — substituição | PUT válido muda os cinco dados com mesmo ID; omitir cada obrigatório; alvo válido/inexistente com corpo válido/inválido; legado; A,A,B/A,A/inexistente repetido | Atualização integral, não upsert, US3.5/SC-010 separado de DELETE, revalidação de referências e preservação integral em rejeição | Ler na mesma sessão pode refletir cache; releitura independente obrigatória. |
| V08 — schema/SQL direto | JDBC metadata PK/unique `(product_id,category_id)`, NOT NULL/FKs e NUMERIC(19,2); instruções SQL inválidas diretas para par duplicado, referência ausente, apagar pai referenciado | Constraints realmente no banco, ultrapassando Set/Bean Validation; rejeição real de FK e ausência de cascata | Anotação/exception mockada não prova DDL; constraint temporária só comprova ramo específico simulado. |
| V09 — exclusão | Produto livre →204/GET404; inexistente→404; produto com OrderItem→409; outra FK de entrada criada somente no teste→409; snapshots antes/depois | Só produto e pares removidos em sucesso; categorias/outros vínculos/compras preservados em falhas | Outra FK temporária não prova todas as relações futuras; testar SQL real conhecido. |
| V10 — histórico | Registrar pedido real com item por 10.00 × 2; ler IDs, vínculos, quantity e unit_price persistidos por conexão independente; calcular exatamente unitPrice × quantity por item e soma dos itens e comparar com valores esperados; repetir leitura/cálculo após PUT do preço do catálogo para 12.00, tentativa de DELETE e novo pedido posterior | Item antigo conserva unit_price de 10.00, quantity de 2 e subtotal calculado de 20.00; total calculado permanece igual ao esperado; catálogo passa a 12.00 e pedido posterior usa o novo preço, sem modificar o anterior | Subtotal/total são calculados, não colunas SQL; oráculo independente descrito abaixo. Não requer GET de pedido nem snapshot de nome/descrição/imagem. |
| V11 — falhas/rollback | Falha após DML real, após flush, beforeCommit, commit interceptado antes de delegar; controle positivo e HTTP/proxy; snapshots independentes antes da limpeza | Atomicidade no alcance de cada falha injetada, nenhum sucesso, envelope seguro inclusive falha tardia | Não prova falha física durante commit, depois de commit ou recuperação de queda de energia; ver tabela abaixo. |
| V12 — concorrência | Duas conexões/transações reais, barreiras sem sleeps arbitrários, compra/delete e Admin/Admin, ambos interleavings; observar SQL, resultados e estado final independente | FK final impede órfão; rollback de delete quando referência vence; atualizações integrais serializadas entre Admins | Não exigir um vencedor universal nem novo erro para POST/orders; timeout/deadlock é falha segura, não aprovação do caminho de sucesso. |
| V13 — regressões | Reexecutar Features001–004: GET público com token inválido irrelevante, preços zero/legado, forma/categories por nome, login, CLIENT|ADMIN pedidos, 415/headers, erros e histórico | Contratos anteriores preservados; regras novas só em escrita administrativa | 926 históricos não substituem nova execução; quantidade de testes não mede cobertura integral. |

Cada cenário e requisito aparece na matriz do plano, inclusive FR-014/US3.5/SC-010. Complementos estruturais/SQL/concorrência não alteram os 37 cenários da spec.

**V03 — fixture e oráculo futuros (FR-003/FR-004/FR-005, US1.1–US1.6, SC-002)**: preparar cinco produtos correspondentes ao mesmo filtro literal e um produto não correspondente, com nomes/IDs conhecidos e ordenação esperada `name ASC,id ASC`; associar um dos cinco a pelo menos duas categorias distintas. Em dataset estável, solicitar `size=2` e repetir o mesmo filtro nas páginas 0, 1 e 2: esperar tamanhos 2/2/1, `totalElements=5` e `totalPages=3` em todas. Comparar a sequência concatenada de IDs com os cinco IDs esperados em ordem, verificando cada produto exatamente uma vez e ausência de repetição ou perda entre páginas, inclusive o produto com várias categorias. Página 3 deve ter content vazio e conservar os totais. Repetir sem filtro: seis produtos, `totalElements=6`, três páginas, cada ID uma vez. Exercitar repository H2 e resposta HTTP; associação múltipla não pode multiplicar linhas nem contagem. A fixture será criada somente na implementação futura, não neste ajuste documental.

**V10 — oráculo histórico futuro (FR-018, US3.7/US4.2/US5.7, SC-007)**: após registrar o pedido real e concluir a chamada, usar outra conexão para ler `tb_order` e todos os seus itens, incluindo IDs/vínculos, posições, `quantity` e `unit_price`. Calcular cada subtotal com decimal exato (`BigDecimal`, sem double/arredondamento) como `unitPrice × quantity`, e o total como soma desses resultados. Comparar campos persistidos e cálculos com a fixture esperada; no pedido com um único item de 10.00 × 2, subtotal e total esperados são 20.00. Não usar `getSubtotal`/`getTotal` da aplicação como único oráculo, pois ambos são `@Transient` e não existem como colunas SQL. Após alterar o preço do catálogo para 12.00, reler independentemente catálogo e pedido anterior: catálogo a 12.00, item antigo a 10.00 × 2, cálculos históricos de 20.00. Após DELETE impedido e novo pedido, conferir novamente o snapshot histórico completo; novo pedido do mesmo produto com quantidade 2 deve usar 12.00 e resultar em 24.00 sem alterar o antigo. Incluir outras linhas quando presentes na fixture e somar todas, sem tratar total como coluna persistida. São provas funcionais ainda não executadas.

**Gate IV — acompanhamento futuro**: conforme a exceção delimitada no [plano](plan.md), V01/V02/V06 devem observar que diagnóstico de solicitação rejeitada pode ler banco, mas não muta DTO/entidades nem emite DML/entra na manutenção. Cobrir US2.6 completo, seleção parcialmente malformada, guarda tardia e causas de ValidationException; comparar snapshots por outra conexão. V07/V08 verificam revalidação/constraints finais. Registrar resultados e revisar o alcance constitucional antes do fechamento da implementação; mutação durante diagnóstico exige reabrir o gate. Nenhuma dessas provas decorre da validação OpenAPI histórica.

## Persistência: leitura independente e limites de rollback/commit

Integração não deve ter `@Transactional` externo no teste HTTP/proxy. Confirmar proxy real e ausência de transação do chamador. Fixtures devem estar commitadas antes do request. Criar DataSource/DriverManager diferente apontando ao mesmo H2 isolado, abrir conexão independente após retorno e antes da limpeza; não reutilizar EntityManager/cache ou conexão vinculada ao serviço.

Snapshot inclui produtos e seus cinco dados, categorias, pares, pedidos e itens/valores. Comparar por IDs/valores, não apenas contagens (update parcial pode conservar contagem). Observar estado após HTTP/proxy, registrar falha realmente atingida e constraint/callback instrumentados. Limpeza por ownership só após assertions; limpeza que apaga estado não é rollback. Controle positivo deve mostrar gravação visível após sucesso.

| Prova futura | Preparação | Conclusão permitida | Não permite concluir |
| --- | --- | --- | --- |
| Rejeição de validação/guardas | Dados inválidos/sem acesso, antes de DML | Sem mutação por solicitação rejeitada | Rollback após alteração já executada. |
| SQL tardio real | Permitir DML do produto/pares e provocar constraint de teste posterior; comprovar SQL atingido | Rollback dos DML desse caminho se snapshot externo igual | Todas as falhas internas ou commit físico. |
| Falha após flush | Confirmar flush real, lançar erro de processamento antes de conclusão | SQL enviado revertido pela transação nesse ponto | Flush bem-sucedido é commit. |
| beforeCommit | TransactionSynchronization de teste dispara falha após DTO interno | Interceptor falha e reverte antes do commit; controller não confirma | Falha JDBC efetiva no commit ou rollback depois de commit. |
| Connection.commit antes de delegate | Wrapper exclusivo de teste registra alcance e lança SQLException antes de chamar driver | Tradução segura de falha tardia e tentativa/resultado de rollback da transação não commitada | Falha do banco após aplicar commit, estado indeterminado ou recuperação pós-queda. |
| Sucesso HTTP/proxy | Sem transação externa; releitura SQL independente | Gravação visível após conclusão efetiva no H2 atual | Durabilidade após encerrar processo/H2 em memória. |
| Falha afterCommit/transmissão | Se exercitada, separar estado já confirmado e erro de resposta | Limite de confirmação ao consumidor e possibilidade de estado commitado | Desfazer transação já commitada; idempotência/reconciliação não previstas. |

Instrumentação é exclusivamente de teste: não alterar produção para obter ramo artificial. Falha física/resultado indeterminado do commit não será apresentada como comprovada por beforeCommit ou wrapper pré-delegação. Nenhuma simulação de queda de energia é autorizada ou necessária a este planejamento.

## Exercício HTTP futuro

Somente após implementação e preparação explícita de ambiente descartável. JWT/categorias dos exemplos precisam ser obtidos desse ambiente, sem token real no documento/logs. Não tentar estas rotas hoje como prova de implementação.

```powershell
curl.exe -i 'http://localhost:8080/admin/categories' -H 'Authorization: Bearer <JWT_ADMIN>'
curl.exe -i 'http://localhost:8080/admin/products?name=%20FONE%20&page=0&size=12' -H 'Authorization: Bearer <JWT_ADMIN>'
```

POST futuro com JSON do exemplo no OpenAPI: nome `  Fone  Azul  `, descrição `  Descrição  completa  `, price `12.000`, image http/https válida e categoryIds A,A,B. Esperar 201/Location, textos sem externos/duplos internos, price `12.00` e duas associações. Capturar ID retornado, consultar, enviar PUT dos cinco dados, consultar novamente, excluir somente se não usado e conferir 204/404. Não usar IDs arbitrários como se fossem fixtures aprovadas.

Para erros agregados, enviar nome de 2, descrição de 9, preço `0`, URL relativa e categoria comprovadamente ausente: 400 com todos os cinco campos, nenhum delta de produto/pares. Repetir sem token e com CLIENT: 401/403 antes de diagnóstico. Registrar contexto externo `/api` em ambiente de teste separado e validar path/Location. Não mudar configuração produtiva neste checkpoint.

## Registro futuro de resultados

Registrar comando, data, branch/HEAD/diff, versões efetivas, exit, console, relatórios e fontes exercitadas. Preservar Surefire anteriores em diretório próprio antes de cada execução; total somente dos relatórios atualizados daquela invocação. Não declarar CI nova nem associar resultado remoto a SHA sem conferir event/revisão; qualquer consulta remota ficará para checkpoint autorizado.

Falha de compilação/ambiente deve ser diagnosticada como tal. Parser YAML/OpenAPI e revisão de links/whitespace são verificação documental separada, nunca teste de comportamento ou prova de transação. Não acrescentar resultados fictícios ao checklist aprovado.

## Verificações documentais históricas do planejamento de 2026-10-11

- Raiz/branch/HEAD esperados e Git limpo confirmados antes da geração. `setup-plan.ps1 -Json` resolveu spec/plan/diretório/branch da Feature 005 e copiou o template, posteriormente preenchido. Branch real também conferida pelo Git. `.specify/extensions.yml` ausente antes/depois: nenhum hook registrado ou executado.
- Lidos constituição, AGENTS, harness, spec/checklist e código/contratos necessários. Pesquisa e revisão cruzada corrigiram precedência da guarda e preservação de 401/403 em ValidationException do diagnóstico. Na geração foram declarados PASS pré/pós-desenho sem exceção; a revisão posterior contestou o fundamento do gate IV. Em 2026-10-12, o [plano](plan.md) retirou esse PASS e documentou exceção à ordem literal de execução, com requisito, limites, consequências e acompanhamento. A declaração original não comprova conformidade estrita.
- OpenAPI completo validado por `openapi-spec-validator 0.7.2`, CLI com `--schema 3.0 --errors all`, chamado em Python isolado com bytecode desativado; exit 0. Reutilizado o venv temporário existente da validação 004, sem instalação/dependência/configuração nova. Conexões de rede bloqueadas no processo de validação. YAML lido com rejeição de chaves duplicadas: nenhuma duplicação.
- **64 ocorrências de referências internas** resolvidas por jsonschema-path/SchemaPath, sem referência externa; **38 links locais** dos cinco documentos Markdown novos com destino existente. Exemplos de entrada com categorias repetidas e resposta legada com preço zero/categoria vazia conferidos contra os schemas por openapi-schema-validator 0.6.3. Essa verificação não demonstra HTTP/normalização/validação de negócio.
- Contagem efetiva preservada: **20 FR, 37 cenários, 10 SC e checklist 16/16**. As **37 linhas** da matriz do plano correspondem exatamente aos cenários, cobrem todos os FR/SC e apontam os grupos futuros V01–V13. Não há marcador de clarificação pendente.
- `git diff --check` exit 0. Os seis arquivos novos conferidos separadamente com `git -c core.safecrlf=false -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>`: sem diagnóstico de whitespace; código 1 por diferença do arquivo novo contra vazio. Opções limitadas ao comando, sem alteração da configuração Git. A tentativa inicial da guarda documental tratou aviso LF→CRLF como erro; o aviso era de conversão, não whitespace, e foi distinguido na conferência final.
- Arquivos rastreados/índice sem diff: spec, checklist, AGENTS, código, testes, POM e configurações preservados. Somente os seis artefatos deste planejamento são novos/não rastreados. Sem tasks, testes de aplicação, stage, commit, fetch, push ou ação no GitHub.

### Invocação OpenAPI recuperada em 2026-10-12

Execução documental histórica em **2026-10-11 às 21:06:41–21:06:44 UTC−03**, não repetida nesta correção. Fonte local: `C:/Users/ronal/.codex/sessions/2026/10/11/rollout-2026-10-11T19-44-57-01a12d23-d138-7251-a227-a157ceb79ee5.jsonl`, linhas 795 (chamada) e 799 (resultado); identificação das versões no mesmo registro, saída da chamada da linha 821. O caminho temporário descreve o ambiente efetivamente utilizado; sua disponibilidade futura não é garantida nem exige recriar ambiente para provar o resultado histórico.

O script de conferência documental foi definido em `$documentReviewCode` por here-string PowerShell e enviado ao stdin. Invocação efetiva do Python isolado, com bytecode desativado:

```powershell
$documentReviewCode | & 'C:/Users/ronal/AppData/Local/Temp/dscommerce-openapi-56c22ffd71ed46e7b43c2623d82b3345/Scripts/python.exe' -B -I -; if ($LASTEXITCODE -ne 0) { throw 'Verificação documental falhou' }
```

Trecho relevante do script enviado, preservando arquivo e argumentos do validador integral:

```python
from pathlib import Path
from openapi_spec_validator.__main__ import main

root = Path('C:/Users/ronal/dscommerce-speckit/specs/005-manter-produtos')
contract = root / 'contracts/product-maintenance-openapi.yaml'
main(['--schema', '3.0', '--errors', 'all', str(contract)])
```

Resultado recuperado: `openapi-spec-validator=0.7.2`, saída `C:\Users\ronal\dscommerce-speckit\specs\005-manter-produtos\contracts\product-maintenance-openapi.yaml: OK`, resumo `"openapi": "VALID"` e **exit code 0**. O script completo também bloqueava conexões de rede via socket e fazia conferências separadas de chaves YAML, referências, links, matriz e exemplos; o trecho acima não representa esses auxiliares. Validação integral OpenAPI é distinta de parsing YAML e não comprova autorização, agregação de negócio, paginação, persistência, histórico, rollback ou conformidade do gate IV. Essas provas permanecem futuras.

## Execução de T001 — contexto, versões e preservação — 2026-10-12

**Escopo autorizado e resultado**: somente T001, sem dependências, concluída como preparação documental/de ambiente. A tarefa registra contexto, versões e preservação de evidências; não prevê execução de baseline. T005 contém as regressões focalizadas e T083 a suíte completa, ambas ainda futuras. Nenhuma fixture, teste, funcionalidade, validador OpenAPI, stage, commit ou ação remota foi executada neste checkpoint.

- **Contexto anterior à execução**: raiz `C:/Users/ronal/dscommerce-speckit`; branch real `005-manter-produtos`; HEAD `0d4753dc2f1a97ef58f125e239476fd913f85646`; working tree limpa e índice vazio. Ao registrar esta evidência, o diff fica limitado a quickstart e ao marcador de T001 em tasks; HEAD/branch permanecem iguais.
- **Fontes conferidas**: `AGENTS.md`, `docs/harness.md` e descrição exata de T001 em `tasks.md`. `pom.xml` fixa Java 21 e Spring Boot 3.3.4; `.mvn/wrapper/maven-wrapper.properties` fixa Wrapper 3.3.4, tipo only-script e distribuição Maven 3.9.16, com SHA-256 `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`. Nenhum desses arquivos foi alterado.
- **Contexto da skill**: `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu `C:/Users/ronal/dscommerce-speckit/specs/005-manter-produtos`. Checklist requirements: 16 revisados, zero abertos, apenas qualidade documental. `.specify/extensions.yml` ausente nas conferências anterior/posterior, sem hooks; ignore existente cobre `target/`. Nenhuma alteração de configuração/ignore foi necessária.

**Única invocação Maven**, na raiz, PowerShell, entre **2026-10-12T20:56:36.7565030+00:00** e **2026-10-12T20:56:42.8875667+00:00**:

```powershell
.\mvnw.cmd --version
```

**Exit code 0**; consulta de versões concluída, sem lifecycle de build/testes. Saída efetiva:

```text
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: C:\Users\ronal\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0
Java version: 21.0.12.1, vendor: Azul Systems, Inc., runtime: C:\Program Files\Zulu\zulu-21
Default locale: pt_BR, platform encoding: UTF-8
OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"
```

Console integral: [wrapper-version.log](../../target/feature-005-t001/20261012T205606812Z/wrapper-version.log); comando/horários/exit: [wrapper-version-result.json](../../target/feature-005-t001/20261012T205606812Z/wrapper-version-result.json). SHA-256 do console: `8DF53246B4C1E944F8DBD4D723159F4DEAB411AB250439DE64D0C595B4A0C336`. A consulta usou a instalação do Wrapper no cache local identificada na saída; nenhuma segunda invocação Maven foi realizada.

**Preservação anterior à invocação**: os 78 arquivos existentes de `target/surefire-reports/` (39 XML e 39 TXT) foram copiados para `target/feature-005-t001/20261012T205606812Z/prior-surefire-reports/`, ignorado pelo Git. O [manifesto anterior](../../target/feature-005-t001/20261012T205606812Z/prior-surefire-manifest.json) registra caminho relativo, tamanho, lastWriteTime UTC e SHA-256 de cada arquivo; SHA-256 do manifesto: `11211808F07B527779747598E00BECA635CC4D914364A6E901E5627B720FB488`. Conferência concluída em **2026-10-12T20:57:36.0126928+00:00**: cópias idênticas aos originais e originais sem alteração de conteúdo, tamanho, timestamps ou conjunto de arquivos após a consulta de versões. Evidências anteriores não foram sobrescritas.

A primeira conferência auxiliar do manifesto retornou exit 1 com `Surefire file set changed`: `@(Get-Content ... | ConvertFrom-Json)` encapsulou o array em um item no PowerShell (Count=1 contra 78 arquivos). A leitura direta de ConvertFrom-Json recuperou Count=78; a conferência corrigida verificou todos os hashes/metadados com exit 0. Foi erro do auxiliar de leitura, não mudança nos relatórios ou falha Maven; não houve repetição do Wrapper nem alteração de testes/produção para contornar resultado.

**Leitura dos relatórios anteriores, sem executar testes**: os 39 `TEST-*.xml` existentes somam **926 testes, 0 falhas, 0 erros, 0 ignorados**, pelos atributos tests/failures/errors/skipped de testsuite. A contagem coincide com a baseline histórica de 926 da Feature 004 citada acima; não é nova baseline, execução no HEAD atual, GREEN da Feature 005 ou prova de seus requisitos. Não foram produzidos relatórios Surefire novos nesta T001.

**Conclusão**: contexto esperado, versões exigidas e preservação conferidos; nenhum impedimento pendente de T001. Somente T001 recebe `[X]`; T002–T086 permanecem `[ ]`. As provas funcionais, regressões e reavaliação do gate IV continuam futuras; a exceção documentada não se transforma em PASS estrito pela consulta de versões.

## Execução de T002 — configuração explícita dos novos contextos — 2026-10-12

**Contexto**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `9806dfe0b33d19d455784793fba855f284c1f98a`, working tree limpa e índice vazio antes da alteração. O hash informado no pedido omitia o caractere `6`; o HEAD real corresponde ao commit do checkpoint de T001. Dependência T001 concluída. Pré-requisito da skill `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; checklist requirements 16/16, sem itens abertos, é revisão documental.

**Configuração criada**: [AdminProductTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java) implementa ApplicationContextInitializer, sem stereotype de componente/configuração e sem descoberta automática. Exige perfil `test` e aplica propriedades locais ao Environment antes da criação de DataSource, EntityManagerFactory e beans JWT. Cada inicialização de contexto gera uma URL H2 `admin-product-test-<UUID>` estável para suas conexões e uma chave descartável de 32 bytes via SecureRandom, codificada em Base64, sem registro do segredo. DDL `create-drop`, driver H2 e credenciais locais ficam restritos ao contexto explícito.

**Seeds**: `spring.sql.init.mode=never` desativa a inicialização SQL do Spring; `spring.jpa.properties.hibernate.hbm2ddl.import_files=""` desativa separadamente a lista de importação do Hibernate, substituindo o padrão `/import.sql`. A leitura local das constantes/bytecode de SchemaCreatorImpl no Hibernate 6.5.3.Final confirmou a propriedade e o fallback; não foi iniciado contexto para comprovar ausência de linhas. `src/test/resources/application-test.properties`, `src/test/resources/import.sql`, configuração de pedidos e demais fontes existentes permanecem intactos.

**Ativação explícita prevista para os testes consumidores futuros** (não criada/executada nesta T002):

```java
@SpringBootTest(classes = DscommerceApplication.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
```

O inicializador precisa ser registrado em `initializers`; `@Import` sozinho não executa sua configuração. O bootstrap completo mantém SecurityConfig, JwtConfig, converter, encoders e decoder reais, sem mocks, substituições ou desligamento de filtros neste suporte. A cadeia administrativa continua futura (T006–T013); esta preparação não a implementa nem comprova autorização ADMIN. Nenhum teste existente foi alterado para ativar o inicializador, e OrderDemoApplication não foi acionada.

**Única invocação Maven de T002**, na raiz, PowerShell, entre **2026-10-12T21:13:07.5491043+00:00** e **2026-10-12T21:13:49.4578758+00:00**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress test-compile
```

**BUILD SUCCESS, exit code 0**, duração Maven **35.297 s**. Compiladas 45 fontes de produção e 40 fontes de teste com `javac [debug parameters release 21]`, incluindo a configuração nova. O Wrapper seleciona Maven 3.9.16 pelas propriedades existentes; versões detalhadas de Maven/JDK são as evidências de T001, sem repetir `--version`. Aviso de operações unchecked/unsafe em OrderServiceTest existente, sem erro ou alteração para contorná-lo. Compilação offline usou o cache local; não executou goal Surefire, testes, validadores ou aplicação. Console: [test-compile.log](../../target/feature-005-t002/20261012T211250129Z/test-compile.log); comando/horários/exit: [test-compile-result.json](../../target/feature-005-t002/20261012T211250129Z/test-compile-result.json). SHA-256 do console: `F552D9092445D553986203C825628684BD47DCB71FCDE34672F8369EA0032ECA`.

**Preservação**: antes do Maven, os 78 relatórios Surefire existentes (39 XML/39 TXT) foram copiados, com hashes conferidos, para `target/feature-005-t002/20261012T211250129Z/prior-surefire-reports/`. [Manifesto anterior](../../target/feature-005-t002/20261012T211250129Z/prior-surefire-manifest.json), SHA-256 `47AA37DB07FCE4CF96675BE5262C2A77A03E52424BB5F2833BBC00DCD5DB9A4A`, registra caminhos, tamanhos, lastWriteTime UTC e hashes. Conferência posterior em **2026-10-12T21:14:17.6231663+00:00**, exit 0: mesmo conjunto de 78 arquivos, conteúdo/tamanho/timestamps originais inalterados e cópias idênticas. Os 169 arquivos rastreados também mantiveram os hashes durante o build, antes do registro documental. Evidências de T001 preservadas; arquivos de evidência desta execução permanecem ignorados em target, sem stage.

**Resultado e limites**: preparação de T002 concluída e compilável, sem fixtures, contas, JWT emitido, consultas de banco, infraestrutura de T004 ou funcionalidade administrativa. A compilação não prova inicialização de contexto, isolamento efetivo, ausência de seeds por leitura independente nem filtros/decoder em operação; essas provas continuam em T004 e nos checkpoints funcionais pertinentes. Os 926 testes permanecem evidência histórica, sem nova execução. Exceção e acompanhamento do gate IV permanecem inalterados e futuros. Somente T002 recebe agora `[X]`, preservando T001 `[X]` e T003–T086 `[ ]`.

## Execução de T003 — fixture explícita por cenário — 2026-10-12

**Contexto e escopo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `9806dfe0b33d19d455784793fba855f284c1f98a`, índice vazio. Antes desta execução, quickstart/tasks modificados e AdminProductTestConfig não rastreado eram as alterações de T002; foram preservados. T001/T002 concluídas como preparação. Pré-requisito da skill `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; checklist requirements 16/16, sem itens abertos, é qualidade documental.

**Implementado somente em** [AdminProductTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java), sem annotation de componente nem ativação automática. O consumidor futuro instancia a fixture com seu WebApplicationContext completo, inicializado explicitamente conforme T002. O construtor exige perfil test, fonte `adminProductTestContext` e URL JDBC correspondente ao H2 `admin-product-test-<UUID>`; apenas verifica metadata e monta o suporte, sem inserir dados. AdminProductTestConfig permanece byte a byte igual.

- `prepareAccessAccounts` prepara ADMIN, CLIENT, ambos e nenhum perfil; `prepareBuyer` prepara separadamente um CLIENT, sem pedido. Contas descartáveis têm nomes únicos, senha descartável e hash pelo PasswordEncoder real. `createAccount` permite dados explícitos de um caso. Não há criação automática de contas/produtos/categorias no construtor.
- `prepareCategorySelection` oferece duas categorias distintas com o mesmo nome e uma sem produto. `createCategory/createProduct/associate` permitem cenários específicos; associações só podem envolver IDs de produtos/categorias pertencentes à mesma fixture. `prepareLegacyProduct/prepareWithoutCategories` inserem somente um legado de preço 0.00, descrição/imagem fora das regras administrativas futuras e nenhuma associação, sem criar categoria. Nenhum helper apaga categorias globais para obter um contexto vazio.
- `prepareV03` prepara atomicamente os seis produtos na ordem Telefone D, Fone C, Fone B, Fone B, Fone A, Cabo, com descrição/imagem/preço aprovados. Cria duas categorias Categoria V03, ambas associadas somente a Fone A. O manifesto imutável contém todos os IDs/nomes/valores gerados, categorias, pares e ordens esperadas: filtro fone = Fone A, os dois Fone B por ID, Fone C, Telefone D; sem filtro = Cabo seguido dessa ordem. Não usa a consulta/mapping da aplicação nem IDs fixos para construir o esperado.
- `prepareAccentScenario/prepareSpacingScenario/prepareSpecialCharactersScenario` são preparações separadas para Café/Cafe, espaços simples/duplos e %/_/barra/apóstrofo. Cada uma retorna manifesto imutável com produtos e IDs esperados para seus filtros literais. Os preparadores de catálogo exigem ausência de cenário de catálogo ainda pertencente à fixture; não assumem nem removem dados de outro owner. T004 deve conferir o estado real do contexto por conexão independente.
- Preparação JDBC via `REQUIRES_NEW` retorna após conclusão transacional; ownership só é incorporado após sucesso. `cleanUp/close` remove somente pares, produtos, categorias, roles e contas registrados por essa instância, por chaves específicas, numa transação. Ownership permanece em caso de falha. Dependências criadas pelo consumidor/terceiros precisam ser removidas por ele antes; a fixture não varre pedidos/itens nem apaga linhas alheias. A instância é de um owner, sem promessa de setup/cleanup concorrente.
- `login` faz POST /login no MockMvc privado do contexto completo, com FilterChainProxy real, sem printer ou token mockado. Usa controller/serviço/encoders existentes e confere Bearer/identidade com o JwtDecoder real. Recusa transação ativa do chamador. Não registra credenciais/token/corpo/causa sensível; Account.toString oculta credenciais. Não foi emitido token nesta execução.

**Verificação executada — somente compilação**, na raiz, PowerShell, duas invocações offline **sequenciais** do mesmo comando:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress test-compile
```

| Tentativa | Início/fim UTC | Resultado real |
| --- | --- | --- |
| 1 | 2026-10-12T21:27:28.1634886+00:00 → 2026-10-12T21:27:54.8405652+00:00 | BUILD FAILURE, exit 1, 21.722 s. WebApplicationContext expõe Environment; chamada getPropertySources não compilava na linha 58 da fixture. |
| 2 | 2026-10-12T21:28:28.6994170+00:00 → 2026-10-12T21:28:55.5478111+00:00 | BUILD SUCCESS, exit 0, 21.894 s, após verificar o tipo ConfigurableEnvironment na própria fixture. 41 fontes de teste compiladas com javac release 21; produção já compilada, sem alteração. |

A primeira falha é erro de preparação, não RED funcional. Apenas a fixture foi corrigida, sem stubs ou mudanças de produção/testes anteriores. Aviso unchecked/unsafe de OrderServiceTest existente permaneceu. Maven 3.9.16 é selecionado pelas propriedades do Wrapper; versões detalhadas do ambiente continuam evidência de T001, sem nova consulta --version. Nenhum goal Surefire, teste, contexto de aplicação, validador ou demonstração foi executado em qualquer tentativa.

Console/resultados da primeira: [test-compile.log](../../target/feature-005-t003/20261012T212452007Z/test-compile.log), [test-compile-result.json](../../target/feature-005-t003/20261012T212452007Z/test-compile-result.json), SHA-256 do console `1E5EA8261A545536B0D492A0FEC469710D6F0D6C7EF17E0D9BD0BD39A294EB79`. Segunda: [test-compile.log](../../target/feature-005-t003/20261012T212452007Z/attempt-2/test-compile.log), [test-compile-result.json](../../target/feature-005-t003/20261012T212452007Z/attempt-2/test-compile-result.json), SHA-256 `270CD25B67CB2E78E98F83EA4D6E083467B07F82DE27AD44547D159578B2264B`. Fonte compilada final: SHA-256 `9892921CBE335CFE1E1DE83DE717FFCFB8F60CFD8447234931CFC591D5A9DD45`.

**Preservação**: os 78 arquivos Surefire anteriores (39 XML/39 TXT) foram copiados com hashes iguais para `target/feature-005-t003/20261012T212452007Z/prior-surefire-reports/`, antes do Maven. [Manifesto](../../target/feature-005-t003/20261012T212452007Z/prior-surefire-manifest.json), SHA-256 `47AA37DB07FCE4CF96675BE5262C2A77A03E52424BB5F2833BBC00DCD5DB9A4A`. Conferência após a primeira tentativa e novamente em **2026-10-12T21:29:21.1675999+00:00**, exit 0: mesmo conjunto, conteúdo/tamanho/timestamps originais inalterados e cópias idênticas. Os 170 arquivos existentes monitorados (169 rastreados mais configuração T002) mantiveram seus hashes durante a implementação/build, antes deste registro. Logs e relatórios permanecem ignorados em target, sem sobrescrever evidências de T001/T002.

**Conclusão limitada**: T003 implementada, revisada e compilável; nenhuma preparação de dados ou emissão de JWT foi efetivamente executada. A compilação não prova commit, isolamento, ausência de seeds/categorias, limpeza por ownership, autenticação/perfis, paginação V03 ou preservação histórica V10. Leitura independente, probe, observação de SQL, comparação de páginas e AdminProductTestSupportTest continuam exclusivamente em T004; nenhuma dessas classes/provas foi antecipada. A exceção e o acompanhamento do gate IV permanecem futuros e inalterados; 926 testes continuam históricos. T003 recebe `[X]`, T001/T002 permanecem `[X]`, T004–T086 permanecem `[ ]`.

## Execução de T004 — provas focadas do suporte e leitura independente — 2026-10-12

**Contexto**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `9806dfe0b33d19d455784793fba855f284c1f98a`, índice vazio. Pendências recebidas: quickstart/tasks e os dois arquivos novos de T002/T003, preservados salvo a correção necessária da guarda JDBC de T003 abaixo. Pré-requisito da skill `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; checklist requirements 16/16, sem itens abertos, é gate documental.

**Arquivos de T004**: [AdminProductDatabaseProbe.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductDatabaseProbe.java) e [AdminProductTestSupportTest.java](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductTestSupportTest.java). Probe cria seu próprio DriverManagerDataSource para o mesmo H2 e abre conexão nova diretamente, sem DataSourceUtils, pool da aplicação ou EntityManager. Cada snapshot usa uma transação de leitura REPEATABLE_READ, devolve IDs/valores imutáveis de produto, categoria, pares, pedido e item, sem ocultar pares duplicados. As leituras complementares de contas/perfis também são independentes. Não lê subtotal/total como colunas: campos históricos persistidos são quantity/unit_price/IDs/vínculos/posição e dados do pedido.

**Ativação do teste**: WebApplicationContextRunner completo, com ConfigDataApplicationContextInitializer, filtro de descoberta de TestComponent/TestConfiguration e AdminProductTestConfig explícitos, DscommerceApplication e holder de beans de observação registrados explicitamente. O holder final não tem stereotype e não pode ser descoberto por component scan em runners anteriores. Produção, seeds globais, configurações anteriores, demo e POM não foram alterados.

### Execuções reais e diagnóstico

Quatro invocações offline, sequenciais, somente da classe de suporte, na raiz, PowerShell:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductTestSupportTest' '-Dsurefire.reportNameSuffix=feature005-t004-attempt1' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductTestSupportTest' '-Dsurefire.reportNameSuffix=feature005-t004-attempt2' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductTestSupportTest' '-Dsurefire.reportNameSuffix=feature005-t004-attempt3' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductTestSupportTest' '-Dsurefire.reportNameSuffix=feature005-t004-attempt4' test
```

| Tentativa | Início/fim UTC | Surefire: testes/falhas/erros/ignorados | Exit | Tempo Maven |
| --- | --- | --- | --- | --- |
| 1 | 2026-10-12T21:44:03.7018644+00:00 → 2026-10-12T21:45:13.6924405+00:00 | 9/7/0/0 | 1 | 01:04 min |
| 2 | 2026-10-12T21:47:54.3936326+00:00 → 2026-10-12T21:48:51.5386874+00:00 | 9/0/7/0 | 1 | 52.299 s |
| 3 | 2026-10-12T21:50:09.1581091+00:00 → 2026-10-12T21:51:12.1212737+00:00 | 9/0/0/0 | 0 | 58.051 s |
| 4 | 2026-10-12T21:51:53.5884898+00:00 → 2026-10-12T21:52:55.2869743+00:00 | 9/0/0/0 | 0 | 56.786 s |

- **1 — preparação do runner**: sete assertions de contexto falharam no startup porque o runner não instalava o filtro de componentes de teste usado pelo bootstrap usual; OrderTestConfig e outras configurações de teste foram descobertas. OrderTestFixture recusou a URL administrativa. Dois controles unitários de páginas passaram. Corrigido somente o bootstrap de T004 com TypeExcludeFilter local, sem alterar classes/configurações antigas.
- **2 — defeito de T003 comprovado**: contextos iniciaram, mas sete casos encontraram `Admin fixtures require their own configured H2 database`. A guarda comparava metadata H2 com a URL completa contendo opções após `;`. Corrigida apenas essa comparação em AdminProductTestFixture: normalizar a URL configurada para a identidade do banco, preservando perfil test, ativação explícita, prefixo e UUID exato. Assertion executada na revisão final confirma metadata igual à URL configurada sem opções. Não se reduziu a guarda a aceitar qualquer banco do mesmo prefixo.
- **3 — suporte corrigido**: nove testes passaram. **4 — fonte final**: repetição focada necessária após tornar o holder de observação não descobrível e acrescentar controle de sua ausência no contexto sem inicializador administrativo. Nove testes passaram novamente; fontes finais conferidas contra [manifesto da revisão testada](../../target/feature-005-t004/20261012T213731110Z/attempt-4/tested-sources.json).

As falhas 1/2 são de preparação/suporte, não RED de comportamento administrativo ausente. Nenhuma assertion foi enfraquecida para obter sucesso. Os mesmos nove casos foram reexecutados; não somar as tentativas como 36 testes distintos. A execução final representa **9 testes, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS, exit 0**. Maven 3.9.16 selecionado pelo Wrapper existente; compilação em Java release 21. Versões detalhadas de ambiente permanecem evidência de T001, sem repetir --version. Aviso unchecked/unsafe de OrderServiceTest existente permaneceu, sem alteração.

### Provas realizadas na revisão final

1. **Contexto e seeds**: cinco tabelas vazias e nenhuma conta antes/depois de construir fixture; Spring SQL init never e lista import_files vazia. Beans reais únicos de encoder/decoder/PasswordEncoder/FilterChainProxy, Nimbus/BCrypt e segredo de 32 bytes. Contexto de controle sem inicializador administrativo conserva 52 produtos/três categorias importados e não tem fonte administrativa nem observador; fixture administrativa recusa esse contexto. AdminProductTestConfig/OrderTestConfig não são descobertos como beans no contexto administrativo.
2. **V03 — preparação e manifesto**: SQL independente confere exatamente seis produtos, nomes/descrição/imagem/preço 10.00 aprovados, cinco correspondentes pelo dataset literal conhecido, duas categorias Categoria V03 e dois pares somente de Fone A. IDs gerados, empate por ID, ordens de cinco/seis IDs e imutabilidade conferidos; nenhuma busca administrativa foi usada para formar o esperado.
3. **Ownership/limpeza**: após assertions, cleanup do primeiro owner elimina somente seus dados; produto/categoria/par e comprador CLIENT de outro owner conservam IDs/valores/perfil. Associação de IDs alheios recusada, cleanup repetido idempotente e estado vazio após ambos os owners encerrarem. Casos de contas verificam os quatro perfis no banco e removem somente suas contas/roles.
4. **Isolamento**: dois contextos simultâneos têm URLs e chaves diferentes; o segundo começa vazio, rejeita assinatura do primeiro e mantém seu snapshot integral quando o primeiro limpa seus dados.
5. **Commit e conexão independente**: dentro de uma transação do chamador, probe não vê sua linha não commitada; vê a linha criada pela fixture REQUIRES_NEW. Rollback do chamador elimina só a primeira, e a linha da fixture permanece visível depois; duas conexões diretas são distintas.
6. **Cenários separados**: legado a 0.00 e sem associações, zero categorias, categorias de nomes iguais/sem produto, Café/Cafe, espaços simples/duplos e %/_/barra/apóstrofo conferidos por IDs/valores SQL contra manifestos e dados esperados explícitos. Limpeza entre cenários impede contaminação dos totais; listas/mapas internos são imutáveis.
7. **Login real/perfis**: tokens obtidos por POST /login de ADMIN, CLIENT, ambos e nenhum perfil são decodificados e ligados aos IDs/perfis atuais por decoder/converter reais. Não há transação ativa do chamador no HTTP; helper recusa login quando o chamador abre uma. Credenciais não aparecem no toString da conta; nenhum printer ou registro de token foi adicionado.
8. **Controle positivo HTTP/proxy e cinco tabelas**: POST /orders existente e OrderService proxy real executados sem transação externa, retornando antes da leitura independente. Snapshot confere produto/categoria/par, pedidos/itens, comprador, IDs/vínculos, instante/estado/posição, unit_price 10.00 e quantidades 2/3; multiplicação independente BigDecimal resulta 20.00/30.00. Consumidor remove seus pedidos/itens por IDs antes do cleanup da fixture. Isso prova o suporte com fluxo existente, não T005/regressão completa nem V10 após alteração de preço.
9. **Páginas e SQL — auxiliares**: comparação de respostas sintéticas 2/2/1 mais página vazia, 2/2/2 e dataset vazio contra IDs conhecidos; controles negativos rejeitam repetição dentro/entre páginas, perda/reordenação, navegação incompleta, page/size/totalElements/totalPages inconsistentes. Hibernate StatementInspector explícito captura SELECT/INSERT reais no controle positivo, com transação ativa nos INSERTs. Não altera SQL nem captura binds; não observa JDBC direto/DDL/Connection.commit. As páginas são sintéticas: V03 HTTP/repository continua futura.

### Evidências e preservação

O parâmetro suportado reportNameSuffix identifica cada tentativa no diretório compartilhado Surefire; cada XML/TXT foi copiado e conferido por hash em seu diretório de evidência, sem substituir os relatórios anteriores.

- Tentativa 1: [console](../../target/feature-005-t004/20261012T213731110Z/attempt-1/maven.log), [comando/horários/exit](../../target/feature-005-t004/20261012T213731110Z/attempt-1/result.json), [XML Surefire](../../target/feature-005-t004/20261012T213731110Z/attempt-1/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductTestSupportTest-feature005-t004-attempt1.xml); SHA-256 XML `7C2F662A34BEB0C452D76CFAA7F09CFAE3A5B5FB2EDFA765824554F23BB9827C`.
- Tentativa 2: [console](../../target/feature-005-t004/20261012T213731110Z/attempt-2/maven.log), [comando/horários/exit](../../target/feature-005-t004/20261012T213731110Z/attempt-2/result.json), [XML Surefire](../../target/feature-005-t004/20261012T213731110Z/attempt-2/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductTestSupportTest-feature005-t004-attempt2.xml); SHA-256 XML `2555CC493A15BA1B688E3E0D196CCDD03A1F576D64E6A738703C17C21DC42253`.
- Tentativa 3: [console](../../target/feature-005-t004/20261012T213731110Z/attempt-3/maven.log), [comando/horários/exit](../../target/feature-005-t004/20261012T213731110Z/attempt-3/result.json), [XML Surefire](../../target/feature-005-t004/20261012T213731110Z/attempt-3/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductTestSupportTest-feature005-t004-attempt3.xml); SHA-256 XML `6E45F316E9166DFD49F37580F34DF0B1EA003E0A1EA9F869DAF2C19ABF7C1788`.
- Tentativa 4: [console](../../target/feature-005-t004/20261012T213731110Z/attempt-4/maven.log), [comando/horários/exit](../../target/feature-005-t004/20261012T213731110Z/attempt-4/result.json), [XML Surefire](../../target/feature-005-t004/20261012T213731110Z/attempt-4/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductTestSupportTest-feature005-t004-attempt4.xml); SHA-256 XML `38020328B78CF5906522E6E16F646EF4AE361A6A298669289D82B2F8948CA879`.

[Resumo das conferências](../../target/feature-005-t004/20261012T213731110Z/verification-summary.json). Antes do Maven, os 78 arquivos anteriores (39 XML/39 TXT) foram copiados para `target/feature-005-t004/20261012T213731110Z/prior-surefire-reports/`; [manifesto anterior](../../target/feature-005-t004/20261012T213731110Z/prior-surefire-manifest.json), SHA-256 `47AA37DB07FCE4CF96675BE5262C2A77A03E52424BB5F2833BBC00DCD5DB9A4A`. Conferências entre execuções e após a última em **2026-10-12T21:54:09.0862959+00:00**, exit 0: originais e cópias idênticos, conteúdo/tamanho/timestamps anteriores inalterados. Diretório compartilhado agora tem esses 78 mais oito novos (quatro XML/quatro TXT), identificados por tentativa; não os somar à baseline histórica de 926, que não foi reexecutada. Logs/relatórios/manifestos em target continuam ignorados.

Os 171 arquivos existentes monitorados preservaram seus hashes durante as implementações/builds, antes deste registro documental, exceto a guarda JDBC de T003 autorizada e diagnosticada. Registros anteriores de quickstart e marcadores de T001–T003 preservados. AdminProductTestConfig permaneceu intacto; nenhuma mudança em spec/checklist/contratos/produção/seeds/testes anteriores.

**Conclusão**: critérios de suporte de T004 atendidos por execução funcional focada e leitura independente; T004 recebe `[X]`, T001–T003 continuam `[X]`, T005–T086 continuam `[ ]`. Sem pendência interna de T004. Endpoints administrativos, regressões T005, busca/paginação reais V03, alteração de preço/preservação histórica V10, diagnóstico/revalidação e provas da exceção do gate IV continuam nas tarefas futuras; o gate não recebe PASS estrito. Nenhuma suíte completa, validador, T005+, stage, commit, fetch, push ou ação remota foi executada neste checkpoint.

## Execução de T005 — baseline atual das regressões focalizadas — 2026-10-12

**Contexto e escopo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `427b5e0cd23d2b888172979762f9f64d4986e96a`, Git limpo e índice vazio inicialmente. Dependência T004 concluída e suporte T002–T004 registrado nesse HEAD. AGENTS, constituição, tarefa integral e planejamento pertinente conferidos. Pré-requisito `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16, sem caixas abertas, continua revisão documental. T005 prevê execução das seis classes existentes e registro da baseline, sem alteração de comportamento, fixtures ou assertions.

**Preparação de evidências**: antes do Maven, copiados e conferidos por SHA-256 todos os **86 relatórios anteriores (43 XML/43 TXT)**, incluindo os 78 históricos e os oito das quatro tentativas de T004. Cópias em `target/feature-005-t005/20261012T221026436Z/prior-surefire-reports/`; [manifesto anterior](../../target/feature-005-t005/20261012T221026436Z/prior-surefire-manifest.json), SHA-256 `3512B9C54A619BDF09642AB44A28A90B208AF28C5343A011B9004C693A3CBB3F`. Essa cópia é preparação, não nova prova funcional. [Manifesto de fontes anterior](../../target/feature-005-t005/20261012T221026436Z/sources-before.json) registra os 173 arquivos rastreados.

**Única invocação Maven de T005**, na raiz, PowerShell, offline e sem outra execução simultânea, entre **2026-10-12T22:10:46.7498083Z** e **2026-10-12T22:12:42.9677272Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=ProductControllerTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,LoginControllerTest,OrderSecurityIntegrationTest,OrderPublicRegressionIntegrationTest' '-Dsurefire.reportNameSuffix=feature005-t005' test
```

**BUILD SUCCESS, exit code 0**, duração Maven **01:50 min**. O sufixo identifica os novos resultados sem sobrescrever XML/TXT anteriores. Não foi executada a suíte completa nem AdminProductTestSupportTest. Maven 3.9.16 selecionado pelas propriedades existentes do Wrapper, sem nova consulta `--version`; os XMLs atuais registram Java **21.0.12.1**, runtime **21.0.12.1+1-LTS**, fornecedor **Azul Systems, Inc.**, sistema **Windows 10**. Produção e testes já estavam compilados (`Nothing to compile` nos dois goals); o sucesso abaixo decorre de execução Surefire, não de compilação apenas.

### Resultado funcional observado

| Classe selecionada, incluindo classes internas | Testes | Falhas | Erros | Ignorados |
| --- | --- | --- | --- | --- |
| ProductControllerTest | 11 | 0 | 0 | 0 |
| PublicCatalogSecurityIntegrationTest | 33 | 0 | 0 | 0 |
| PublicProductDetailSecurityIntegrationTest | 38 | 0 | 0 | 0 |
| LoginControllerTest | 83 | 0 | 0 | 0 |
| OrderSecurityIntegrationTest | 185 | 0 | 0 | 0 |
| OrderPublicRegressionIntegrationTest | 14 | 0 | 0 | 0 |
| **Total desta invocação** | **364** | **0** | **0** | **0** |

Os oito XMLs novos somam 364 pelos atributos `tests/failures/errors/skipped`, em acordo com o console. Conferidos também **364 elementos testcase com classname/name únicos**. Surefire consolida 31 casos externos + dois de EmptyCatalog no XML interno de catálogo, e 36 externos + dois de DuplicateCategoryNames no XML interno de detalhe; os dois XMLs externos desses grupos registram zero, sem significar classe não exercitada. Não duplicar seus casos ao consolidar. Todas as seis classes previstas estão representadas, sem classe adicional de suporte.

Esta execução estabelece baseline focalizada atual das assertions existentes de catálogo/detalhe públicos, login, segurança dos pedidos e jornadas públicas após rejeições de pedidos. ProductControllerTest mantém seu MVC slice com mocks; integrações mantêm contextos, filtros, fixtures e configurações próprios, sem ativar o suporte administrativo por descoberta. Os oito XMLs anteriores correspondentes ao mesmo grupo também somavam 364 sem falhas/erros/ignorados: comparação histórica, não segunda execução nesta T005. Assertions já GREEN preservadas, sem fabricar RED. Não houve falha de ambiente ou funcional nesta invocação nem correção de fontes.

### Evidências e limites

- [Console integral](../../target/feature-005-t005/20261012T221026436Z/maven.log), SHA-256 `DF3A2E8BB0FC706E5D80AC15841C2AA3B14A6F61152FD80B9CC86E0401F226FB`.
- [Comando, horários e exit code](../../target/feature-005-t005/20261012T221026436Z/result.json).
- [Resumo com contagens por XML e ambiente observado](../../target/feature-005-t005/20261012T221026436Z/verification-summary.json) e [manifesto dos novos relatórios](../../target/feature-005-t005/20261012T221026436Z/current-reports-manifest.json). Os oito XML/oito TXT foram copiados para `target/feature-005-t005/20261012T221026436Z/surefire-reports/` e conferidos por hash.

Conferência após Maven em **2026-10-12T22:13:23.7764337Z**, exit 0: conteúdo, tamanho e timestamps UTC dos 86 relatórios anteriores inalterados; cópias idênticas. Os 173 arquivos rastreados conservaram seus hashes durante o build/verificação, antes deste registro documental. Diretório compartilhado agora contém os 86 anteriores mais **16 novos arquivos**, identificados pelo sufixo; consolidar somente os oito XMLs desta invocação. Os 926 testes da suíte completa anterior e os nove de suporte T004 permanecem evidências separadas, não reexecutadas nem somadas aos 364 atuais. Logs/relatórios/manifestos continuam locais e ignorados em target.

**Alterações deste checkpoint**: somente este registro acrescentado ao quickstart e checkbox de T005 em tasks. Suporte T002–T004, testes anteriores, produção, seeds, configuração do build, spec/checklist e contratos preservados. A exceção do gate IV continua **EXCEÇÃO DOCUMENTADA**, sem PASS estrito; diagnóstico sem mutação/DML, decisões nos serviços e revalidação transacional exigem as provas futuras já planejadas. Também permanecem futuras a fundação administrativa T006–T013, operações administrativas, V03 real, V10 após alteração de preço e suíte completa T083.

**Conclusão limitada**: critérios de T005 atendidos pela execução das regressões existentes e registro da baseline atual; T005 recebe `[X]`, T001–T004 permanecem `[X]`, T006–T086 permanecem `[ ]`. Nenhuma preparação de fixture administrativa, implementação nova, validador, T006+, stage, commit, fetch, push ou ação remota neste checkpoint. Sem pendência interna de T005; resultados deste grupo não declaram a Feature 005 implementada.

## Execução de T006 — testes da cadeia administrativa em RED funcional — 2026-10-12

**Contexto e escopo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, Git limpo e índice vazio inicialmente. Dependência T005 concluída; baseline focalizada de 364 e suporte T002–T004 preservados, sem reexecução desses grupos. AGENTS, T006 integral, requisitos de segurança e precedência no planejamento/contrato e constituição conferidos. Pré-requisito `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; checklist requirements 16/16, sem itens abertos, é revisão documental. Nenhuma extensão registrada em `.specify/extensions.yml` na preparação.

**Criado somente** [AdminProductSecurityChainTest.java](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductSecurityChainTest.java). SpringBootTest com DscommerceApplication e perfil test, AdminProductTestConfig ativado explicitamente por ContextConfiguration; nenhuma cadeia/decoder/serviço de segurança substituído. MockMvc usa o WebApplicationContext completo e FilterChainProxy real, precedido por observador local que conta acesso a getInputStream/getReader sem consumir/logar conteúdo. Não é bean descobrível nem printer. Não há transação externa no teste HTTP.

Cada caso começa conferindo as cinco tabelas/contas vazias por AdminProductDatabaseProbe independente, cria somente comprador CLIENT com a fixture validada e obtém token por POST /login real; IDs/perfil são conferidos no banco e identidade no decoder. Produtos/categoria são preparados apenas nos controles públicos/pedido que os consomem. Snapshots por IDs/valores são comparados após retorno, antes da limpeza por ownership; somente o controle de pedido confirmado remove seus próprios IDs de pedido/item antes de fechar a fixture. Essa preparação não implementa segurança ou endpoints administrativos.

### Única execução focada e resultado

Na raiz, PowerShell, offline e sem outra invocação Maven simultânea, entre **2026-10-12T22:25:25.7112480Z** e **2026-10-12T22:26:42.7995887Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductSecurityChainTest' '-Dsurefire.reportNameSuffix=feature005-t006' test
```

**BUILD FAILURE esperado por assertions, exit code 1**, duração Maven **01:11 min**. Surefire/console/XML: **103 testes, 96 falhas, 0 erros, 0 ignorados; 7 aprovados**. A compilação das 44 fontes de teste em javac release 21 e o startup do contexto completo tiveram sucesso; não houve falha de compilação, contexto, fixture ou ambiente, nem necessidade de corrigir preparação. Wrapper seleciona Maven 3.9.16 conforme propriedades existentes, sem repetir --version. XML registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10.

### Falhas funcionais esperadas, examinadas individualmente

| Grupo | Casos | Esperado | Comportamento atual |
| --- | --- | --- | --- |
| Sem token, seis caminhos × sete verbos, JSON truncado | 42 | 401 no filtro, sem acesso ao corpo e sem dispatch MVC | 404; requisição alcança ResourceHttpRequestHandler. |
| JWT CLIENT real, mesmos caminhos/verbos/corpo | 42 | 403 no filtro, sem acesso ao corpo e sem dispatch MVC | 404; requisição alcança ResourceHttpRequestHandler. |
| Sem token, POST /admin/products e PUT /admin/products/1 × outros três corpos | 6 | 401 precede corpo bem formado, ausente ou media type não suportado | 404 e dispatch ao mesmo handler MVC. |
| JWT CLIENT real, mesmas escritas/corpos | 6 | 403 precede essas condições de corpo | 404 e dispatch ao mesmo handler MVC. |

Os caminhos da matriz são `/admin/products`, `/admin/products/1`, `/admin/categories`, `/admin/products/1/unsupported`, `/admin/categories/1` e `/admin/unsupported`; verbos GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS. Os três corpos adicionais são JSON bem formado (não prova validade de referências), vazio e text/plain. Assim, métodos/rotas não suportados também devem ser protegidos antes do MVC; nenhum handler administrativo foi criado para obter esse RED.

[Catálogo completo das 96 falhas](../../target/feature-005-t006/20261012T222343285Z/functional-red-cases.json) identifica F001–F096, testcase parametrizado, identidade, método, caminho, corpo, status esperado/observado e as duas assertions que falharam em cada caso. Todas são MultipleFailuresError por status e dispatch; ausência de acesso ao corpo, observador ativo, snapshot independente, contas/perfil e preparação/limpeza não falharam. O corpo não foi acessado nem mesmo no fallback atual, pois não há endpoint administrativo: isoladamente esse zero não comprova rejeição de segurança. O RED é o 404/MVC em vez de 401/403 no filtro.

**Causa de comportamento conferida em SecurityConfig existente**: somente POST /orders possui cadeia JWT/CLIENT|ADMIN em prioridade 1; o fallback em prioridade 2 permite todas as outras requisições e não processa JWT. Falta a cadeia /admin/** planejada; alcançar recurso inexistente não substitui rejeição administrativa. Produção permanece intacta; implementação/correções e GREEN pertencem às tarefas posteriores, incluindo T009/T013, não a este checkpoint.

### Controles GREEN preservados dentro desta nova classe

- Quatro casos GET público (lista/detalhe × anônimo/Bearer inválido irrelevante): 200, ID/preço numérico/dados preparados e categorias por nome; cadeia pública sem BearerTokenAuthenticationFilter, sem delta no snapshot independente.
- POST /orders sem token e JSON truncado: 401 antes de acesso ao corpo e dispatch MVC; banco intacto.
- POST /orders com JWT CLIENT real e produto por 10.00 × 2: 201, cadeia Bearer existente e leitura independente confirma ID/comprador/vínculos, quantity 2 e unit_price 10.00 após retorno.
- POST /orders autenticado com JSON truncado: 400 no MVC, acesso ao corpo detectado e snapshot intacto. Esse controle positivo demonstra que o observador não retorna zero artificialmente quando o corpo é examinado.

Esses sete casos não foram quebrados para fabricar RED e não constituem repetição da regressão de 364 ou dos nove testes de suporte. Não provam V10 após alteração de preço nem a segurança administrativa completa.

### Evidências e preservação

Antes do Maven, **102 relatórios anteriores (51 XML/51 TXT)** copiados e conferidos por SHA-256 em `target/feature-005-t006/20261012T222343285Z/prior-surefire-reports/`; [manifesto anterior](../../target/feature-005-t006/20261012T222343285Z/prior-surefire-manifest.json), SHA-256 `035018244C610B015CF5DBC4457FEAF9602FDEDBB7B8659448E9B039E8916EC4`. O sufixo identifica separadamente os novos XML/TXT, copiados e conferidos em `target/feature-005-t006/20261012T222343285Z/surefire-reports/`.

- [Console integral](../../target/feature-005-t006/20261012T222343285Z/maven.log), SHA-256 `9B94E354548A4E55C7F55EF9B5597FDAE0106C3198367FFC8D3F5A38E334739B`.
- [Comando, horários e exit code](../../target/feature-005-t006/20261012T222343285Z/result.json).
- [XML Surefire](../../target/feature-005-t006/20261012T222343285Z/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductSecurityChainTest-feature005-t006.xml), SHA-256 `3B8ABD6A24D031167656697E808898464F9CF4F787BCDF7BB0A7A8B53F76347A`; [manifesto dos dois novos relatórios](../../target/feature-005-t006/20261012T222343285Z/current-reports-manifest.json).
- [Fonte executada](../../target/feature-005-t006/20261012T222343285Z/tested-source.json), SHA-256 do teste `9B6594FCFB1CF37BC53D34673DC1BF1699E5498B72BE05651B488C992E3FDBCA`; [resumo da conferência](../../target/feature-005-t006/20261012T222343285Z/verification-summary.json).

Conferência após Maven em **2026-10-12T22:28:08.1705301Z**, exit 0: os 102 relatórios anteriores preservam conteúdo/tamanho/timestamps UTC e cópias idênticas; diretório compartilhado tem esses 102 mais dois novos. Todos os 173 arquivos rastreados conservaram seus hashes antes deste registro documental; fonte nova coincide com a revisão executada. Evidências anteriores de 926 históricos, nove de suporte e 364 regressões permanecem separadas; não somar execuções. Logs/relatórios/manifestos locais em target continuam ignorados, sem stage.

**Conclusão da tarefa de testes, em RED**: T006 escreveu e executou as provas previstas, com falhas pela ausência real da cadeia administrativa e controles existentes GREEN; por isso T006 recebe `[X]` como tarefa de teste RED concluída, não como implementação/aprovação da segurança. T001–T005 continuam `[X]`; T007–T086 permanecem `[ ]`. Não há impedimento de preparação pendente. Segurança administrativa continua não implementada e o GREEN futuro de T013 é obrigatório. Envelope/guardas/perfis adicionais, V03/V10 e provas de diagnóstico/revalidação da exceção do gate IV permanecem futuros; exceção documentada não recebe PASS estrito. Nenhuma alteração de produção, suporte validado, seeds, testes/contratos anteriores ou artefatos aprovados; somente classe nova, registro no quickstart e checkbox de T006. Sem T007+, suíte completa, validador, stage, commit, fetch, push ou ação remota.

## Execução de T007 — DTOs de erro administrativo — 2026-10-12

**Contexto e objetivo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Preservadas as alterações pendentes de T006 em quickstart/tasks/AdminProductSecurityChainTest. AGENTS, T007 integral e dependência T006, constituição, modelo de dados e contrato de erros conferidos. Pré-requisito Spec Kit resolveu Feature 005; checklist requirements 16/16, sem itens abertos, é aprovação documental. Criar somente os DTOs, sem executar T008+ ou transformar o RED de segurança em GREEN.

**Implementado**: [AdminErrorResponseDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/AdminErrorResponseDTO.java), record com exatamente `status/error/message/path/errors`, e [AdminFieldErrorDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/AdminFieldErrorDTO.java), record com exatamente `field/code/message`. Lista nula é normalizada para lista vazia; lista fornecida recebe cópia imutável, preservando valores e ordem. Nenhum valor rejeitado/campo interno foi acrescentado. Esses DTOs apenas representam a saída: seleção de mensagens/códigos, sanitização do path, ordenação/deduplicação de diagnósticos e integração HTTP continuam nas tarefas correspondentes. `ErrorResponseDTO` e os envelopes públicos/de pedidos permanecem intactos.

### Verificações efetivamente executadas

Única invocação Maven, sequencial, na raiz, entre **2026-10-12T22:35:38.8830070Z** e **2026-10-12T22:36:07.6357311Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress test-compile
```

**BUILD SUCCESS, exit code 0**, duração Maven **23.719 s**. Compiladas **47 fontes de produção e 44 de teste**, javac release 21. Wrapper permanece configurado para Maven 3.9.16; não houve nova execução de --version. Aviso de operações unchecked em OrderServiceTest preexistente, sem alteração nessa fonte. O goal test-compile **não executou testes**, não produziu relatórios Surefire e não demonstra comportamento HTTP, segurança, persistência ou GREEN de T006.

Para verificar especificamente o contrato serializado, executado um verificador Java local em target, sem adicionar fonte de teste permanente, dependência, bean ou contexto Spring. [Fonte das verificações](../../target/feature-005-t007/20261012T223531220Z/AdminErrorDtoVerification.java). Utilizou o JDK existente `C:/Program Files/Zulu/zulu-21` e ObjectMapper/Jackson **2.17.2**: databind, annotations e core já presentes no cache local. Os caminhos desses três JARs foram recuperados do classpath do XML histórico de T006; isso não reexecutou seus testes. `$componentClasspath` foi composto por target/classes atual mais esses três JARs; `$verifierClasses` e `$taskEvidence` apontaram para o diretório desta execução em target. Invocações efetivas, com executáveis e argumentos completos nos registros vinculados:

```powershell
& 'C:\Program Files\Zulu\zulu-21\bin\javac.exe' --release 21 -cp $componentClasspath -d $verifierClasses (Join-Path $taskEvidence 'AdminErrorDtoVerification.java')
& 'C:\Program Files\Zulu\zulu-21\bin\java.exe' -cp ($verifierClasses + ';' + $componentClasspath) AdminErrorDtoVerification
```

**javac exit 0; java exit 0**, execução do componente entre **2026-10-12T22:36:58.4021891Z** e **2026-10-12T22:36:59.3397068Z**, Java **21.0.12.1**. **Seis verificações do componente aprovadas, zero falhas**; são assertions locais de serialização/imutabilidade, não testes JUnit/Surefire nem resultados da suíte:

1. Erro sem campo serializa exatamente os cinco campos, com `errors: []`.
2. Lista nula também serializa como array vazio.
3. Dois diagnósticos serializam somente os três campos por entrada, conservando valores, índice categoryIds[1] e ordem fornecida.
4. Diagnóstico isolado serializa exatamente field/code/message, sem propriedades adicionais.
5. Alteração da lista de origem não modifica o DTO; sua lista rejeita mutação.
6. ErrorResponseDTO antigo conserva exatamente status/error/message/path, sem errors.

**Diagnóstico de preparação corrigido**: a primeira tentativa de compilar o verificador falhou porque a composição PowerShell do classpath converteu o array aninhado de JARs em `System.Object[]`, ocultando Jackson de javac. A chamada PowerShell terminou com exit 1; o exit nativo de javac não foi capturado porque ErrorActionPreference Stop interrompeu a coleta. [Log preservado da tentativa](../../target/feature-005-t007/20261012T223531220Z/javac-attempt1.log) e [argumentos/diagnóstico](../../target/feature-005-t007/20261012T223531220Z/javac-attempt1-result.json). Corrigida somente a composição do classpath do verificador; compilação e execução focadas então tiveram exit 0. Nenhuma mudança de produção/testes/assertions para contornar resultado, nenhuma repetição Maven. Esse erro de preparação não é RED funcional.

### Evidências e limites

- [Console Maven](../../target/feature-005-t007/20261012T223531220Z/maven.log) e [comando/horários/exit code](../../target/feature-005-t007/20261012T223531220Z/maven-result.json).
- [Compilação do verificador: executável/argumentos/resultado](../../target/feature-005-t007/20261012T223531220Z/javac-result.json); [execução do componente](../../target/feature-005-t007/20261012T223531220Z/component-result.json) e [saída integral com as seis verificações](../../target/feature-005-t007/20261012T223531220Z/component.log).
- [Manifesto dos 104 relatórios anteriores](../../target/feature-005-t007/20261012T223531220Z/prior-surefire-manifest.json), copiados para prior-surefire-reports nesta evidência antes do Maven. Conferência de conteúdo SHA-256, tamanho, timestamp UTC e cópias: **104 preservados (52 XML/52 TXT), zero relatórios novos**.
- [Resumo da conferência e hashes dos DTOs](../../target/feature-005-t007/20261012T223531220Z/verification-summary.json), realizada em **2026-10-12T22:37:17.1183281Z**: todos os 173 arquivos rastreados intactos antes deste registro; fonte pendente de T006 preservada, SHA-256 `9B6594FCFB1CF37BC53D34673DC1BF1699E5498B72BE05651B488C992E3FDBCA`. Evidências locais permanecem ignoradas em target.

**Conclusão delimitada**: T007 `[X]` porque os dois componentes e seu formato foram implementados e verificados. T001–T006 permanecem `[X]`; T008–T086 ficam `[ ]`. Isso é implementação parcial do suporte a erros administrativos, **não segurança administrativa validada**. O resultado RED histórico de T006 (96 falhas/7 aprovados) não foi reexecutado; a ausência de cadeia continua no código, pois os adaptadores T008 e a cadeia T009 ainda não existem. Guardas T010–T012 e checkpoint GREEN T013 permanecem obrigatórios. Não há impedimento no escopo de T007. Provas administrativas, V03/V10 e limites/reavaliação da exceção documentada do gate IV continuam futuros; nenhum PASS estrito foi declarado. Suporte T002–T004, seeds, produção anterior, assertions e contratos permanecem preservados. Não executados T008+, testes de suporte, regressão de 364, suíte completa ou validadores; sem stage, commit, fetch, push ou ação remota.

## Execução de T008 — adaptadores de erros administrativos — 2026-10-13

**Contexto e autorização**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio. Estado inicial: quickstart/tasks modificados e AdminProductSecurityChainTest/AdminErrorResponseDTO/AdminFieldErrorDTO não rastreados, provenientes de T006/T007; preservados. Lidos AGENTS aplicável, skill speckit-implement, constituição, T008 integral/dependência T007 e planejamento/contratos pertinentes. Pré-requisito Spec Kit com `-Json -RequireTasks -IncludeTasks` resolveu esta feature, exit 0; checklist requirements 16/16, sem abertos, permanece revisão documental. `.specify/extensions.yml` ausente antes/depois: sem hooks. `.gitignore` já cobre os artefatos Java/Maven e evidências locais; nenhuma alteração de setup necessária. Autorização exclusiva de T008, sem stage, commit, fetch, push ou integração remota.

**Arquivos criados**: [AdminAuthenticationEntryPoint.java](../../src/main/java/com/devsuperior/dscommerce/security/AdminAuthenticationEntryPoint.java), [AdminAccessDeniedHandler.java](../../src/main/java/com/devsuperior/dscommerce/security/AdminAccessDeniedHandler.java) e teste focalizado [AdminSecurityErrorHandlersTest.java](../../src/test/java/com/devsuperior/dscommerce/security/AdminSecurityErrorHandlersTest.java). Entry point retorna 401/`Authentication required or invalid` com `WWW-Authenticate: Bearer`; quando a AuthenticationException envolve diretamente a exceção existente OrderAccountResolutionException, retorna 500/`Product maintenance could not be completed`, sem challenge. Denied handler retorna 403/`Product maintenance not permitted`, sem challenge. Ambos escrevem JSON com exatamente status/error/message/path/errors, reason phrase correspondente e errors vazio, sem usar mensagens internas nem ler corpo, query ou Authorization. `getRequestURI()` conserva o context path externo e exclui query. Classes sem registro global; a conexão aos filtros continua na T009.

### Verificação exclusiva do componente

Teste unitário chama os adaptadores com request/response Servlet de teste e ObjectMapper real; não cria contexto Spring, banco, JWT ou cadeia substituta. Os quatro casos 401 usam InsufficientAuthenticationException, BadCredentialsException, InvalidBearerTokenException e OAuth2AuthenticationException com causa não relacionada à resolução interna. Outros dois casos verificam a resolução interna encapsulada como OAuth2AuthenticationException e AccessDeniedException. Todos conferem igualdade do JSON completo, status/content type/challenge, `/api/admin/products/9` com context path preservado e query excluída. Request contém marcadores descartáveis em corpo/header/query e exceções; o spy comprova que somente getRequestURI foi consultado. Não há leitura de corpo/token nem exposição desses marcadores na resposta. O teste 403 comprova o adaptador, não a decisão hasRole/consulta de perfis.

Duas invocações Maven **sequenciais**, ambas somente para a mesma classe:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminSecurityErrorHandlersTest' '-Dsurefire.reportNameSuffix=feature005-t008' test
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminSecurityErrorHandlersTest' '-Dsurefire.reportNameSuffix=feature005-t008-attempt2' test
```

**Primeira tentativa**: compilação de 49 fontes de produção/45 de teste, javac release 21, e relatórios Surefire com **6 testes, 0 falhas, 0 erros, 0 ignorados**. A captura PowerShell com ErrorActionPreference Stop foi interrompida pelo aviso stderr de carregamento dinâmico do agente Java do Mockito/Byte Buddy: shell exit 1, sem captura do exit nativo/summary terminal Maven. [Console preservado](../../target/feature-005-t008/20261013T102335602Z/maven.log), [comando/diagnóstico](../../target/feature-005-t008/20261013T102335602Z/attempt1-result.json) e [XML da primeira tentativa](../../target/feature-005-t008/20261013T102335602Z/attempt1-surefire-reports/TEST-com.devsuperior.dscommerce.security.AdminSecurityErrorHandlersTest-feature005-t008.xml). Isso é falha de captura, não RED funcional nem resultado BUILD SUCCESS comprovado dessa tentativa.

**Repetição justificada**: após término da primeira invocação e preservação dos seus relatórios, repetir somente o componente para obter exit nativo e resumo terminal. Ajustado ErrorActionPreference para Continue somente durante a captura nativa; fontes/assertions/argumentos de seleção intactos, sufixo distinto. Entre **2026-10-13T10:27:28.0510635Z** e **2026-10-13T10:27:46.9090494Z**, **BUILD SUCCESS, exit 0**, duração Maven **13.585 s**, **6 testes, 0 falhas, 0 erros, 0 ignorados**. Não somar as duas tentativas como 12 testes distintos. Avisos de agente Java/CDS permanecem registrados, sem alteração para silenciá-los. Wrapper configurado para Maven 3.9.16; sem repetir --version. XML atual registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10.

- [Console terminal Maven](../../target/feature-005-t008/20261013T102335602Z/maven-attempt2.log) e [argumentos/horários/exit/branch/HEAD](../../target/feature-005-t008/20261013T102335602Z/maven-attempt2-result.json).
- [XML final Surefire](../../target/feature-005-t008/20261013T102335602Z/surefire-reports/TEST-com.devsuperior.dscommerce.security.AdminSecurityErrorHandlersTest-feature005-t008-attempt2.xml) e [manifesto dos novos relatórios](../../target/feature-005-t008/20261013T102335602Z/current-reports-manifest.json).
- [Manifesto dos 104 relatórios anteriores](../../target/feature-005-t008/20261013T102335602Z/prior-surefire-manifest.json), copiados antes do Maven para prior-surefire-reports; [manifesto anterior à repetição, 106 relatórios](../../target/feature-005-t008/20261013T102335602Z/before-retry-manifest.json).
- [Conferência de preservação/fontes/versões](../../target/feature-005-t008/20261013T102335602Z/verification-summary.json), em **2026-10-13T10:29:29.0896723Z**, exit 0: 104 relatórios anteriores e cópias com SHA-256/tamanho/timestamps preservados; os 106 anteriores à repetição também intactos. Diretório compartilhado contém 108 relatórios (104 anteriores + 2 de cada tentativa). Antes do registro/checkbox, todos os **176 arquivos iniciais rastreados/não rastreados** mantiveram hashes, incluindo pendências de T006/T007, SecurityConfig, envelopes antigos, assertions, seeds e suporte T002–T004. Auditoria local inicialmente confundiu array aninhado de ConvertFrom-Json com arquivo alterado; corrigida sua enumeração, sem mudança de fontes nem outra execução Maven. Manifesto inicial e cópias de quickstart/tasks preservam o estado anterior.

**Conclusão e limites**: T008 `[X]` pelos dois adaptadores implementados e seis provas do componente aprovadas. T001–T007 mantidas `[X]`; T009–T086 permanecem abertas. Nenhum novo RED pré-implementação foi executado em T008; RED histórico de T006 não foi repetido nem declarado GREEN. T009 deve instalar a cadeia /admin/**/hasRole ADMIN e traduzir a falha de resolução para AuthenticationException com causa OrderAccountResolutionException, como a fronteira verificada aqui. T010–T012 devem completar guardas; T013 deve comprovar segurança/regressões em contexto real. Estes testes não validam autenticação JWT, ausência/revogação de ADMIN, filtros, banco ou endpoints administrativos. A exceção documentada do gate IV e as demais provas futuras permanecem no alcance original. Nenhuma antecipação de T009+, alteração de comportamento de catálogo/detalhes/login/pedidos ou suporte validado; nenhum teste de suporte/regressão, suíte completa ou validador executado. Revisados os três arquivos novos, diff, preservação dos textos anteriores e whitespace; evidências em target permanecem ignoradas, índice vazio e HEAD inalterado.

## Execução de T009 — integração da cadeia administrativa — 2026-10-13

**Contexto e escopo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Preservadas as pendências de T006–T008: quickstart/tasks modificados e seis fontes não rastreadas (DTOs, adaptadores administrativos e dois testes). AGENTS aplicável, constituição, T009 integral/dependência T008, planejamento de segurança/erros, modelo e contrato conferidos. Pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16, sem abertos, permanece aprovação documental. `.specify/extensions.yml` ausente antes/depois, sem hooks. Ignore Java/Maven existente adequado, sem alteração de setup. Executada exclusivamente a integração de T009; nenhum stage, commit, fetch, push ou ação remota.

**Implementação**: [SecurityConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java) recebe adminProductSecurityFilterChain em prioridade 2, matcher `/admin/**` sem restrição de verbo e `anyRequest().hasRole("ADMIN")`. Integra os adaptadores de T008 tanto em exceptionHandling quanto em oauth2ResourceServer, com o JwtDecoder e OrderJwtAuthenticationConverter existentes. Falha OrderAccountResolutionException do converter é encapsulada como OAuth2AuthenticationException/server_error com a causa preservada e mensagem administrativa segura, para o entry point escrever 500. POST /orders conserva integralmente sua cadeia em prioridade 1; somente a prioridade do fallback público passa de 2 para 3. Nenhum resource server global, nova identidade/claims, mudança de converter, guards, advice ou endpoint.

**Teste novo estritamente da integração**: [AdminProductSecurityChainWiringTest.java](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductSecurityChainWiringTest.java), 13 casos com DscommerceApplication completo, AdminProductTestConfig explícito e filtros/decoder/converter reais. Sem transação externa, fixtures commitadas via suporte existente, leitura independente das cinco tabelas e limpeza por ownership. Não substitui beans de aplicação nem adiciona controller. No único caso interno, advice temporário no proxy real de UserAccountRepository lança DataAccessResourceFailureException somente em findByIdWithRoles da conta escolhida, antes do SQL; removido após o teste. Comprova tradução do caminho de resolução, não indisponibilidade física do H2 nem guarda tardia.

- Seis controles (ADMIN isolado/ADMIN+CLIENT × três recursos GET) obtêm JWT pelo login real, conferem perfis no banco e selecionam cadeia com Bearer. Retorno 404 com ResourceHttpRequestHandler e NoResourceFoundException demonstra **acesso autorizado ao MVC e ausência do endpoint**. Não é rejeição do filtro, Product not found administrativo implementado ou sucesso de CRUD. O envelope MVC desses recursos inexistentes permanece fora desta integração; a adaptação central administrativa continua futura.
- Um controle confere exatamente três cadeias na ordem planejada, matcher administrativo para sete verbos/quatro caminhos com context path, POST /orders isolado e fallback/login sem Bearer.
- Quatro controles de envelope: anônimo/token inválido → 401 com challenge; CLIENT/sem perfis → 403 sem challenge. URI `/api/admin/products/1/unsupported` conserva context path e exclui query. JSON tem exatamente status/error/message/path/errors, errors vazio; handler MVC nulo e nenhum acesso a getInputStream/getReader.
- Resolução interna de ADMIN → 500 seguro sem challenge, lookup instrumentado exatamente uma vez, sem MVC/leitura de corpo/mudança do snapshot nem marcador SQL/corpo/query, token ou senha no output normal capturado.
- Token CLIENT corretamente assinado com claims name/roles indicando ADMIN continua 403 pelos perfis atuais do banco. Derivado do JWT obtido no login e assinado com o encoder real do contexto descartável; não é JWT mockado nem segredo/token registrado como evidência.

### RED e GREEN executados sequencialmente

Antes da implementação, somente o teste novo, entre **2026-10-13T10:38:13.1603861Z** e **2026-10-13T10:39:14.5053276Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductSecurityChainWiringTest' '-Dsurefire.reportNameSuffix=feature005-t009-red' test
```

**BUILD FAILURE, exit 1**, duração Maven **56.229 s**, **13 testes/13 falhas/0 erros/0 ignorados**. Compilação/startup/fixture concluídos; falhas de assertions pela ausência real da integração: seis controles autorizados não encontram Bearer no fallback, quatro rejeições e a prova de claims recebem 404 em vez de 401/403, resolução recebe 404 em vez de 500 e seleção encontra duas cadeias em vez de três. O 404 preexistente não foi tratado como autenticação comprovada. [Casos RED examinados individualmente](../../target/feature-005-t009/20261013T103612578Z/red-functional-cases.json). Fonte nova/assertions permaneceram idênticas entre RED e GREEN; T006 original foi preservado, sem repetir seu RED histórico antes da implementação.

Após integrar, entre **2026-10-13T10:40:09.2416044Z** e **2026-10-13T10:42:34.1606230Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductSecurityChainTest,AdminProductSecurityChainWiringTest,OrderSecurityIntegrationTest,PublicCatalogSecurityIntegrationTest,PublicProductDetailSecurityIntegrationTest,LoginControllerTest' '-Dsurefire.reportNameSuffix=feature005-t009-green' test
```

**BUILD SUCCESS, exit 0**, duração Maven **02:19 min**, **455 testes/0 falhas/0 erros/0 ignorados**. Somente as seis classes selecionadas e seus nested tests; não é suíte completa. Os quatro grupos antigos exercitam as cadeias/fallback afetados e preservação dos contratos, não antecipam o checkpoint T013. Compiladas 49 fontes de produção/46 de teste, javac release 21. Wrapper configurado para Maven 3.9.16; sem repetir --version. XML atual registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10. Captura nativa com ErrorActionPreference Continue somente durante cada Maven; sem interrupção/perda de exit code ou repetição de verificação por problema de captura. Avisos preexistentes de unchecked, H2Dialect/open-in-view e agente Java/CDS permanecem no log; nenhuma fonte/configuração alterada para silenciá-los.

| Grupo da execução GREEN | Testes | Resultado |
| --- | ---: | --- |
| AdminProductSecurityChainTest (T006, assertions intactas) | 103 | GREEN: 96 rejeições administrativas antes de corpo/MVC e sete controles públicos/pedidos. |
| AdminProductSecurityChainWiringTest | 13 | GREEN: integração/adaptadores, perfis e distinção entre rejeição do filtro e 404 autorizado no MVC. |
| OrderSecurityIntegrationTest | 185 | GREEN, contrato e segurança de pedidos preservados. |
| PublicCatalogSecurityIntegrationTest | 33 | GREEN, incluindo nested EmptyCatalog. |
| PublicProductDetailSecurityIntegrationTest | 38 | GREEN, incluindo nested DuplicateCategoryNames. |
| LoginControllerTest | 83 | GREEN, login/envelopes existentes preservados. |

Os XML dos containers externos dos dois grupos com nested tests registram zero casos próprios; os respectivos 33/38 estão nos XML nested e são contados uma vez, conforme console/Surefire. Não somar RED/GREEN, 926 históricos, baseline de 364 ou testes anteriores como casos distintos desta execução.

### Evidências, preservação e pendências

- [Console RED](../../target/feature-005-t009/20261013T103612578Z/maven-red.log), [comando/horários/exit/hash da fonte](../../target/feature-005-t009/20261013T103612578Z/maven-red-result.json) e [XML RED](../../target/feature-005-t009/20261013T103612578Z/red-surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductSecurityChainWiringTest-feature005-t009-red.xml).
- [Console GREEN](../../target/feature-005-t009/20261013T103612578Z/maven-green.log), [comando/horários/exit/branch/HEAD](../../target/feature-005-t009/20261013T103612578Z/maven-green-result.json), [manifesto dos 16 relatórios GREEN](../../target/feature-005-t009/20261013T103612578Z/green-reports-manifest.json) e [fontes efetivamente executadas](../../target/feature-005-t009/20261013T103612578Z/tested-sources.json).
- [XML de T006 agora GREEN](../../target/feature-005-t009/20261013T103612578Z/green-surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductSecurityChainTest-feature005-t009-green.xml) e [XML da integração](../../target/feature-005-t009/20261013T103612578Z/green-surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductSecurityChainWiringTest-feature005-t009-green.xml); demais XML/TXT próprios no mesmo diretório.
- [Manifesto dos 108 relatórios anteriores](../../target/feature-005-t009/20261013T103612578Z/prior-surefire-manifest.json), copiados antes do RED para prior-surefire-reports; [manifesto anterior ao GREEN, 110 relatórios](../../target/feature-005-t009/20261013T103612578Z/before-green-manifest.json).
- [Auditoria por SHA-256/tamanho/timestamps e resumo de contagens/versões](../../target/feature-005-t009/20261013T103612578Z/verification-summary.json), em **2026-10-13T10:43:23.1175579Z**, exit 0: 108 anteriores e cópias preservados, inclusive T006–T008; 110 anteriores ao GREEN intactos. Diretório compartilhado tem 126 relatórios: 108 anteriores + 2 RED + 16 GREEN. Antes deste registro, 178 dos 179 arquivos iniciais mantinham hashes; única mudança era SecurityConfig. Assim, todas as pendências de T006–T008, suporte T002–T004, assertions anteriores, seeds e envelopes antigos foram preservados. Quickstart/tasks anteriores copiados no diretório desta evidência e mantidos como prefixo/checkboxes, sem reescrever histórico. Evidências locais em target continuam ignoradas.

**Conclusão delimitada**: T009 `[X]` por integrar a cadeia/admin adapters conforme contrato e passar as verificações focadas. T001–T008 permanecem `[X]`; T010–T086 ficam abertas. T010/T011/T012 devem construir/testar a guarda atual em AdminAccessService antes de qualquer lookup administrativo e adaptar os erros centrais sem alterar as Features 001–004; T013 continua pendente porque exige GREEN também dessa guarda e checkpoint completo da fundação. Revogação/remoção entre filtro e serviço, guardas durante diagnóstico, tradução de ValidationException e demais provas reais de V01/V02 aguardam as tarefas correspondentes, inclusive T066/T067/T070/T078. Resultado atual comprova segurança na fronteira do filtro, não serviços inexistentes, CRUD, SC-001 integral ou entrega da feature. Exceção documentada do gate IV permanece inalterada e sem PASS estrito. Nenhum T010+, teste de suporte, suíte completa, validador ou ação remota executado. Revisados diff, arquivo novo, caminhos/links e whitespace; índice vazio e HEAD inalterado ao final, sem stage/commit/fetch/push.

## Execução de T010 — superfície compilável da guarda administrativa — 2026-10-13

**Contexto e objetivo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Pendências de T006–T009 preservadas: quickstart/tasks/SecurityConfig modificados e sete fontes não rastreadas (DTOs, adaptadores e testes administrativos). Lidos AGENTS aplicável, T010 integral/dependência T009, constituição e requisitos de guarda atual no planejamento/modelo/research/contrato. Pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16 sem abertos, sem alterar checklist. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignore Java/Maven existente adequado. Autorização exclusiva de T010, sem ações remotas ou integração Git.

T010 é preparação da superfície compilável; testes RED da guarda pertencem a T011 e sua implementação a T012. Criado somente [AdminAccessService.java](../../src/main/java/com/devsuperior/dscommerce/services/AdminAccessService.java): `@Service`, construtor com AuthenticatedAccountService existente e método público `void requireAdmin(AuthenticatedAccountPrincipal principal)`. Reutiliza o principal mínimo existente; nenhuma identidade/claim/parâmetro de perfil novo. A dependência fica disponível para a implementação posterior, sem executar resolve/lookup nesta etapa. Método provisório interrompe qualquer chamada com UnsupportedOperationException de mensagem fixa, indicando guarda ainda não implementada; não decide conta/ADMIN nem concede acesso. Não ligado a controllers, diagnóstico, manutenção ou cadeia de segurança. Nenhuma anotação transacional ou regra de autorização antecipada.

### Única verificação Maven

Na raiz, sequencialmente, entre **2026-10-13T10:49:46.6865871Z** e **2026-10-13T10:50:00.8932705Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress compile
```

**BUILD SUCCESS, exit 0**, duração Maven **8.968 s**. Compiladas **50 fontes de produção**, javac release 21; classe AdminAccessService.class presente em target/classes e fonte compilada coincide com o hash registrado. Wrapper configurado para Maven 3.9.16, sem repetir --version. O goal compile não compila nem executa testes, não inicia contexto Spring/fixture/banco e não produz relatórios Surefire. **Zero testes executados, nenhum RED/GREEN funcional declarado**. Compilação demonstra a superfície exigida por T010, não a guarda implementada, a criação do bean em contexto real ou segurança administrativa completa. Não houve falha de compilação, ambiente ou captura, nem repetição Maven. Os 455 testes de T009 permanecem resultado histórico dessa execução, não nova verificação de T010.

- [Console Maven](../../target/feature-005-t010/20261013T104926944Z/maven.log) e [comando/horários/exit/branch/HEAD/hash da fonte](../../target/feature-005-t010/20261013T104926944Z/maven-result.json).
- [Manifesto dos 126 relatórios anteriores](../../target/feature-005-t010/20261013T104926944Z/prior-surefire-manifest.json), copiados antes do Maven para prior-surefire-reports e preservados por SHA-256/tamanho/timestamps; zero relatórios novos.
- [Auditoria da preservação e classe compilada](../../target/feature-005-t010/20261013T104926944Z/verification-summary.json), em **2026-10-13T10:50:50.1647910Z**, exit 0: todos os **180 arquivos iniciais rastreados/não rastreados** mantinham hashes antes deste registro/checkbox. Assim, cadeia administrativa de T009, pedidos, contratos/envelopes públicos, assertions, seeds, suporte e pendências T006–T009 permaneceram intactos. Manifesto inicial e cópias de quickstart/tasks guardam o estado anterior. Evidências locais em target permanecem ignoradas.

**Conclusão delimitada**: T010 `[X]` pela superfície mínima compilável concluída. T001–T009 mantidas `[X]`; T011–T086 permanecem abertas. T011 deverá testar a regra ainda ausente por assertions comportamentais (ADMIN isolado, CLIENT/sem perfis, conta ausente, revogação e falha de lookup); nenhum teste dessa tarefa foi escrito/executado aqui. T012 deverá implementar a guarda usando AuthenticatedAccountService e adaptar erros centrais administrativos, e T013 comprovar o checkpoint integrado. Remoção/revogação entre filtro e serviço e guardas no diagnóstico continuam pendentes; a exceção documentada do gate IV permanece inalterada e sem PASS estrito. Não executados T011+, testes de suporte/regressão, os 455 testes ou suíte completa, validador, stage, commit, fetch ou push. Revisados o arquivo novo, diff, caminhos/links e whitespace; somente quickstart/tasks recebem o registro/checkbox desta tarefa, índice permanece vazio e HEAD inalterado.

## Execução de T011 — testes da guarda administrativa em RED — 2026-10-13

**Contexto e autorização**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Preservadas todas as pendências T006–T010: quickstart/tasks/SecurityConfig modificados e oito fontes não rastreadas. Lidos AGENTS aplicável, T011 integral/dependência T010, constituição e requisitos de identidade/perfis/guarda atual do planejamento, research, modelo e contrato. Pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16 sem abertos, sem alterar checklist. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignore Java/Maven existente adequado. Escopo somente de testes RED, sem autorização/execução de T012+, stage, commit, fetch, push ou ações remotas.

**Criado somente** [AdminAccessServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/AdminAccessServiceTest.java), 13 testes unitários. Instância real de AdminAccessService preparada em T010; somente AuthenticatedAccountService é mockado na fronteira de resolução atual. Nenhum método da guarda é stubado, nenhum bean/decoder/filtro substituído, nenhum contexto Spring, JWT, repository, H2, fixture ou seed utilizado. Principal usa o record existente/accountId Long; snapshots simulados de autoridades são construídos com ROLE_ADMIN/ROLE_CLIENT a partir de UserRole, sem novas claims/identidade. Verify exige a resolução do ID fornecido, ausência de lookup para identidade inválida e nova resolução em chamadas sucessivas. AssertAll mantém observáveis as expectativas de comportamento e de lookup mesmo quando a primeira assertion falha. Exceções são examinadas por assertions, não deixadas escapar como erros de execução.

### Única execução focalizada

Na raiz, sequencialmente, entre **2026-10-13T10:59:10.9656585Z** e **2026-10-13T10:59:47.1345661Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminAccessServiceTest' '-Dsurefire.reportNameSuffix=feature005-t011' test
```

**BUILD FAILURE esperado, exit 1**, duração Maven **31.244 s**. Console/Surefire/XML: **13 testes, 13 falhas, 0 erros, 0 ignorados**. Compiladas **47 fontes de teste**, javac release 21; produção já compilada, sem alteração. Wrapper configurado para Maven 3.9.16, sem repetir --version; XML atual registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10. Avisos preexistentes de unchecked em OrderServiceTest e agente Java/CDS permanecem no log, sem correções/silenciamento de fontes. Nenhuma falha de compilação, preparação ou ambiente; contexto/fixture não fazem parte destes testes. Captura nativa com ErrorActionPreference Continue durante Maven, exit capturado corretamente; sem repetição de verificação.

### Expectativas e causas do RED examinadas por caso

| Cenário | Casos | Esperado | Atual e causa da falha |
| --- | ---: | --- | --- |
| ADMIN isolado e ADMIN+CLIENT | 2 | Resolver a conta do principal uma vez e aceitar sem exceção; CLIENT não é obrigatório. | UnsupportedOperationException antes do lookup; falham aceitação e chamada ao resolver. |
| CLIENT isolado e conta sem perfis | 2 | Resolver uma vez; AccessDeniedException com Product maintenance not permitted. | UnsupportedOperationException em vez da exceção de permissão, zero resoluções. |
| Principal ausente, ID nulo, zero ou negativo | 4 | BadCredentialsException com Authentication required or invalid, sem lookup. | UnsupportedOperationException em vez de credencial inválida; ausência de lookup já passa, mas o tratamento de identidade está ausente. |
| Conta atual inexistente | 1 | Resolver uma vez e preservar a BadCredentialsException fornecida pelo resolver. | UnsupportedOperationException diferente da exceção esperada, resolver não chamado. |
| ADMIN → sem perfis e ADMIN+CLIENT → CLIENT | 2 | Primeira chamada aceita; segunda consulta o estado atual e recusa com AccessDeniedException; duas resoluções do mesmo ID. | Ambas chamadas interrompidas pelo stub; primeira não aceita, segunda não distingue revogação e nenhuma resolução ocorre. |
| Conta removida depois de autorização anterior | 1 | Primeira chamada aceita; segunda resolve novamente e preserva BadCredentialsException de conta ausente; duas resoluções. | UnsupportedOperationException em ambas, zero resoluções; remoção não é distinguida. |
| Falha interna de resolução | 1 | Resolver uma vez e propagar a mesma OrderAccountResolutionException e causa, distintas de credencial/permissão. | UnsupportedOperationException do stub, sem alcançar a falha de lookup configurada. |

Todos os 13 casos registram MultipleFailuresError por assertions. **Nove** incluem Wanted but not invoked pela resolução ausente; os quatro de identidade inválida não esperam lookup. [Catálogo completo das falhas](../../target/feature-005-t011/20261013T105738231Z/functional-red-cases.json) preserva nome parametrizado, tipo, mensagens integrais e causa observada de cada testcase. Causa comum confirmada na fonte intacta de T010: requireAdmin sempre lança UnsupportedOperationException com Administrative access guard is not implemented antes de consultar AuthenticatedAccountService. A falha vem da regra ausente no componente novo; AuthenticatedAccountService/chain/contratos não foram modificados para fabricar RED.

Sequências de estados/exceções simulados estão configuradas nos testes, mas **não são consumidas nesta execução**, porque a guarda não chama resolve. Portanto, este RED especifica a reavaliação exigida e detecta sua ausência; não comprova revogação/remoção efetiva no banco, passagem pelo filtro ou guarda integrada. Essas provas e o GREEN continuam nas tarefas posteriores. Nenhuma assertion passou a aceitar o stub ou foi enfraquecida depois do resultado.

### Evidências, preservação e limites

- [Console Maven](../../target/feature-005-t011/20261013T105738231Z/maven.log) e [comando/horários/exit/branch/HEAD](../../target/feature-005-t011/20261013T105738231Z/maven-result.json).
- [XML Surefire RED](../../target/feature-005-t011/20261013T105738231Z/surefire-reports/TEST-com.devsuperior.dscommerce.services.AdminAccessServiceTest-feature005-t011.xml) e [manifesto dos dois relatórios novos](../../target/feature-005-t011/20261013T105738231Z/current-reports-manifest.json).
- [Fontes efetivamente executadas e hashes](../../target/feature-005-t011/20261013T105738231Z/tested-sources.json), incluindo teste novo e os dois serviços; permanecem idênticas após a execução.
- [Manifesto dos 126 relatórios anteriores](../../target/feature-005-t011/20261013T105738231Z/prior-surefire-manifest.json), copiados antes do Maven para prior-surefire-reports e preservados por SHA-256/tamanho/timestamps. Diretório compartilhado contém 128 relatórios: 126 anteriores + dois próprios de T011, sem sobrescrever evidência anterior.
- [Auditoria de preservação/causas/versões](../../target/feature-005-t011/20261013T105738231Z/verification-summary.json), em **2026-10-13T11:00:42.2307386Z**, exit 0: os **181 arquivos iniciais rastreados/não rastreados** mantinham hashes antes deste registro/checkbox, inclusive pendências T006–T010, ambos serviços, cadeia validada, assertions, contratos públicos, seeds e suporte. Manifesto inicial e cópias de quickstart/tasks preservam o estado anterior; evidências locais em target continuam ignoradas.

**Conclusão da tarefa de testes em RED**: T011 `[X]` porque os casos previstos foram escritos e executados com falhas de assertions pela guarda ausente. T001–T010 mantidas `[X]`; T012–T086 continuam abertas. Nenhum impedimento de preparação/ambiente; **guarda ainda não implementada**, sem GREEN funcional declarado. T012 deverá implementar a regra e adaptação central administrativa mantendo estas assertions; T013 deverá comprovar GREEN da guarda/cadeia e regressões integradas. Provas de estado real entre filtro e serviço, diagnóstico/ValidationException e revisão da exceção documentada do gate IV continuam pendentes; a exceção não recebe PASS estrito. Não executados T012+, os 455 testes, suporte/regressões, suíte completa, validador ou ações remotas. Revisados teste novo, diff, caminhos/links e whitespace; única fonte criada é o teste, registro documental acrescentado e somente checkbox T011 modificada nesta execução. Índice vazio e HEAD inalterado; sem stage, commit, fetch ou push.

## Execução de T012 — guarda atual e erros administrativos básicos — 2026-10-13

**Contexto e autorização**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Preservadas as pendências T006–T011, incluindo SecurityConfig, DTOs/adaptadores, serviços e todas as assertions anteriores. Lidos AGENTS aplicável, T012 integral/dependência T011, constituição e requisitos de guarda/erros no planejamento, research, modelo e contrato. Pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16 sem abertos, sem editar checklist. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignore Java/Maven existente adequado. Escopo exclusivo de T012 e verificações de seus componentes; sem executar checkpoint T013+, stage, commit, fetch, push ou ações remotas.

**Guarda implementada** em [AdminAccessService.java](../../src/main/java/com/devsuperior/dscommerce/services/AdminAccessService.java), substituindo o stub de T010 sem mudar sua assinatura. Principal ausente/ID nulo, zero ou negativo lança BadCredentialsException com Authentication required or invalid antes de lookup. ID válido é resolvido a cada chamada por AuthenticatedAccountService existente, que consulta conta/perfis atuais no banco. Somente ROLE_ADMIN retornado por essa resolução permite acesso, sem exigir CLIENT; ausência de ADMIN lança AccessDeniedException com Product maintenance not permitted. Conta ausente e falha interna mantêm as exceções originais do resolver, inclusive causa; nenhum cache, nova claim/identidade, consulta administrativa de produto/categoria ou DML. Guarda pronta para uso antes de lookup nos serviços administrativos das tarefas seguintes; consumidores ainda inexistentes não foram criados nesta execução.

**Adaptação central básica** em [GlobalExceptionHandler.java](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java): BadCredentialsException → 401 com Bearer challenge, AccessDeniedException → 403 sem challenge, resolução interna/erro inesperado → 500 seguro sem challenge, somente no namespace administrativo da URI relativa ao context path. Envelope próprio contém exatamente status/error/message/path/errors, errors vazio e mensagens fixas do contrato; path externo conserva context path e não inclui query. URI /administrator não é ADMIN, e context path chamado /admin não transforma /products em recurso administrativo. Os três métodos existentes passam a ResponseEntity<?> para suportar ambos os DTOs; branches de pedidos/públicos mantêm seu comportamento. Erros nativos ErrorResponse 4xx são relançados intactos, preservando a resolução MVC/status/headers e o 404 de recurso não implementado. Nenhuma mudança nas outras branches do advice, SecurityConfig, OrderService, AuthenticatedAccountService ou adaptadores de filtros.

**Teste adicional estritamente necessário ao advice**: criado [AdminGuardExceptionHandlerTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminGuardExceptionHandlerTest.java), 22 casos diretos de handler/serialização com ObjectMapper real. Oito respostas administrativas (quatro falhas × sem/com context path), seis exclusões de escopo público/prefixo parecido, context path /admin com rota pública, três controles do envelope de pedidos e quatro preservações de erros MVC nativos 404/405/406/415. Confere igualdade do JSON completo, mensagens/status/challenge, errors vazio, path sem query e ausência de marcadores descartáveis de exceção/query/token. Não registra controller/bean nem inicia filtros/banco. Verifica a seleção de respostas diretamente; não é prova de dispatch administrativo real nem de logs do servidor. T011 permanece byte a byte igual ao RED histórico.

### Execuções Maven sequenciais e diagnóstico separado

**1. Preparação do teste do advice, antes de implementar** — entre **2026-10-13T11:29:58.5377857Z** e **2026-10-13T11:30:27.1587230Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminGuardExceptionHandlerTest' '-Dsurefire.reportNameSuffix=feature005-t012-red' test
```

**BUILD FAILURE, exit 1**, duração Maven **23.516 s**, por erro de compilação na assertion de mapper.valueToTree: inferência genérica tornou assertThat ambígua entre overloads de Predicate/IntPredicate. **Nenhum teste executado/relatório novo; não é RED funcional**. Corrigido somente o tipo explícito JsonNode no teste novo; as mesmas comparações de conteúdo/sanitização foram mantidas. Fonte de produção intacta nesse momento. [Log de preparação](../../target/feature-005-t012/20261013T112822904Z/maven-red.log) e [argumentos/horários/exit/hash anterior à correção](../../target/feature-005-t012/20261013T112822904Z/maven-red-result.json).

**2. Repetição justificada para obter RED funcional**, mesmo comando/seletor/sufixo, entre **2026-10-13T11:31:08.6583897Z** e **2026-10-13T11:31:41.6014218Z**. **BUILD FAILURE esperado, exit 1**, duração Maven **27.543 s**. **22 testes, 8 falhas de assertions, 0 erros, 0 ignorados; 14 controles GREEN**. As oito respostas administrativas esperadas não existem: advice relança BadCredentialsException/AccessDeniedException/OrderAccountResolutionException/IllegalStateException, com e sem context path. Assertions assertDoesNotThrow falham pelo comportamento ausente, sem exceção escapar como erro de execução. [Casos RED examinados individualmente](../../target/feature-005-t012/20261013T112822904Z/advice-red-cases.json), [console funcional](../../target/feature-005-t012/20261013T112822904Z/maven-red-attempt2.log), [comando/horários/exit/hash corrigido](../../target/feature-005-t012/20261013T112822904Z/maven-red-attempt2-result.json) e [XML RED](../../target/feature-005-t012/20261013T112822904Z/red-surefire-reports/TEST-com.devsuperior.dscommerce.controllers.AdminGuardExceptionHandlerTest-feature005-t012-red.xml). Produção ainda intacta até esse RED; depois foram implementadas apenas guarda e adaptação básica. Assertions do advice permaneceram idênticas desde esse RED até o GREEN.

O **RED de 13/13 da guarda em T011 é histórico**, [XML original preservado](../../target/feature-005-t011/20261013T105738231Z/surefire-reports/TEST-com.devsuperior.dscommerce.services.AdminAccessServiceTest-feature005-t011.xml); não foi repetido antes da implementação de T012. Hash do teste atual coincide com a fonte daquela execução. O stub foi substituído somente após os testes previstos estarem escritos/executados em RED.

**3. GREEN da guarda/advice e regressões dos componentes alterados**, entre **2026-10-13T11:33:18.0008515Z** e **2026-10-13T11:34:17.7109896Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminAccessServiceTest,AdminGuardExceptionHandlerTest,GlobalExceptionHandlerRegressionTest,OrderFrameworkHttpErrorIntegrationTest' '-Dsurefire.reportNameSuffix=feature005-t012-green' test
```

**BUILD SUCCESS, exit 0**, duração Maven **54.694 s**, **120 testes/0 falhas/0 erros/0 ignorados**, somente os quatro grupos selecionados:

| Grupo | Testes | Resultado/alcance |
| --- | ---: | --- |
| AdminAccessServiceTest (T011 intacto) | 13 | GREEN: ADMIN isolado/ambos, rejeição CLIENT/sem perfis, identidade inválida antes de lookup, conta ausente, reconsulta após revogação/remoção e distinção da falha interna. Resolver simulado; não prova banco/filtro real. |
| AdminGuardExceptionHandlerTest | 22 | GREEN: tradução/DTO administrativo 401/403/500 e controles de escopo/pedidos/nativos. Chamadas diretas ao advice. |
| GlobalExceptionHandlerRegressionTest | 84 | GREEN: contratos públicos de catálogo/detalhes/login e handlers existentes via MVC slice, envelopes antigos, context path e Bearer inválido irrelevante. Slice não é prova de autenticação real. |
| OrderFrameworkHttpErrorIntegrationTest | 1 | GREEN: HTTP/contexto/filtros reais; login CLIENT e controle 201, seguido de 415 nativo autorizado sem alteração de pedidos/itens por leitura independente. |

Os dois grupos antigos são necessários porque GlobalExceptionHandler é compartilhado: preservam os envelopes públicos e a passagem do erro HTTP nativo de pedidos no servidor real. Não são execução de T013 ou repetição do grupo de 455. Nenhuma suíte completa. Não somar tentativas RED/GREEN nem resultados históricos como testes distintos. Wrapper configurado para Maven 3.9.16, sem repetir --version; XML GREEN registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10. Captura nativa com ErrorActionPreference Continue durante cada Maven, sem perda de exit code; avisos preexistentes de unchecked/agente Java/CDS/H2Dialect preservados, sem alterações para silenciá-los.

### Evidências, preservação e limites

- [Console GREEN](../../target/feature-005-t012/20261013T112822904Z/maven-green.log) e [comando/horários/exit/branch/HEAD](../../target/feature-005-t012/20261013T112822904Z/maven-green-result.json).
- [XML dos 13 testes da guarda agora GREEN](../../target/feature-005-t012/20261013T112822904Z/green-surefire-reports/TEST-com.devsuperior.dscommerce.services.AdminAccessServiceTest-feature005-t012-green.xml), [XML do advice GREEN](../../target/feature-005-t012/20261013T112822904Z/green-surefire-reports/TEST-com.devsuperior.dscommerce.controllers.AdminGuardExceptionHandlerTest-feature005-t012-green.xml), [manifesto dos oito relatórios GREEN](../../target/feature-005-t012/20261013T112822904Z/green-reports-manifest.json) e [fontes efetivamente executadas](../../target/feature-005-t012/20261013T112822904Z/tested-sources.json).
- [Manifesto dos 128 relatórios anteriores](../../target/feature-005-t012/20261013T112822904Z/prior-surefire-manifest.json), copiados antes da primeira chamada para prior-surefire-reports; [manifesto anterior ao GREEN, 130 relatórios](../../target/feature-005-t012/20261013T112822904Z/before-green-manifest.json). Preservados por SHA-256/tamanho/timestamps. Diretório compartilhado tem 138 relatórios: 128 anteriores + dois RED do advice + oito GREEN; compilação falha não gerou relatório.
- [Auditoria de preservação e contagens/fontes/versões](../../target/feature-005-t012/20261013T112822904Z/verification-summary.json), em **2026-10-13T11:35:44.8060916Z**, exit 0: 180 dos 182 arquivos iniciais mantinham hashes antes deste registro; únicas fontes iniciais alteradas eram AdminAccessService e GlobalExceptionHandler, dentro de T012. Preservados todos os demais itens pendentes T006–T011, incluindo assertions de T011/anteriores, cadeia, OrderService, AuthenticatedAccountService, seeds, suporte e contratos. Teste novo não mudou após RED funcional. Quickstart/tasks anteriores copiados e mantidos como prefixo/checkboxes; evidências locais em target permanecem ignoradas.

**Conclusão delimitada**: T012 `[X]` pela guarda atual implementada e adaptação administrativa básica 401/403/500 verificada; T001–T011 mantidas `[X]`, T013–T086 abertas. O componente consulta perfis do banco pelo resolver de produção; seus 13 testes atuais simulam a fronteira de resolução. T013 continua responsável pelo checkpoint completo da fundação com cadeia/guarda e regressões selecionadas. Não criados consumidores administrativos, CRUD, validação/query/path, diagnóstico agregado ou novas regras de negócio. Erros de campos/domínio e wrappers ValidationException continuam nas tarefas correspondentes, incluindo T021/T033; envelopes administrativos dos erros MVC nativos continuam futuros, com status/headers preservados aqui. Provas reais de remoção/revogação entre filtro e serviço e no diagnóstico aguardam integração posterior, inclusive T066/T067; teste direto não comprova concorrência, DML/rollback ou sanitização de logs em servidor. Exceção documentada do gate IV inalterada e sem PASS estrito. Sem impedimento atual de ambiente/preparação; erro de compilação do teste novo foi corrigido e registrado separadamente. Revisados diff, novas fontes/arquivo não rastreado, caminhos/links e whitespace; sem T013+, 455 testes, suíte completa, stage, commit, fetch ou push. Índice vazio e HEAD inalterado ao final.

## Execução de T013 — checkpoint com pendência na tradução do diagnóstico — 2026-10-13

**Resultado do checkpoint: não aprovado; T013 permanece `[ ]`.** Os quatro grupos nominalmente previstos em T013 passaram, mas as provas adicionais de integração/diagnóstico expressamente solicitadas nesta execução revelaram oito falhas funcionais de tradução de ValidationException. Nenhuma assertion foi enfraquecida nem implementação futura antecipada para obter GREEN.

**Contexto e escopo**: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio inicialmente. Preservadas todas as pendências T006–T012: quickstart/tasks/SecurityConfig/GlobalExceptionHandler modificados e dez fontes não rastreadas. Lidos AGENTS aplicável, T013 integral/dependência T012 e procedimentos de V01/V02/guardas/diagnóstico no plano, research, modelo, contratos, constituição e quickstart. Pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; requirements 16/16 sem abertos, sem editar checklist. `.specify/extensions.yml` ausente antes/depois, sem hooks; ignore Java/Maven existente adequado. Autorização exclusivamente de provas do checkpoint, sem alterar produção ou executar T014+, stage, commit, fetch, push ou ações remotas.

### Prova de integração restrita aos testes

Criado somente [AdminFoundationIntegrationProbeTest.java](../../src/test/java/com/devsuperior/dscommerce/config/AdminFoundationIntegrationProbeTest.java), **32 casos**: dois momentos × quatro condições da conta × ADMIN isolado/ADMIN+CLIENT × contexto raiz/`/api`. Usa DscommerceApplication completo, AdminProductTestConfig explícito, filtros/converter/decoder/encoder reais, JWT obtido por POST /login real e resolução JPA atual. Não há token mockado, substituição de bean de autenticação/guarda ou transação externa do teste HTTP. Conta/perfis, produto, categoria e associação são preparados/commitados somente na fixture própria, em H2 isolado sem seeds importados. Snapshot independente das cinco tabelas tem produto/categoria/associação não vazios; pedidos/itens ficam vazios nessa fixture administrativa e são exercitados separadamente pela regressão de pedidos.

Auxiliares ficam integralmente no arquivo de teste, registrados apenas por @Import de TestConfiguration: controller em `/admin/test-only-foundation/diagnostic` e `/admin/test-only-foundation/service`, DTO/constraint/validator locais, interceptor de observação e serviço de leitura com proxy transacional read-only. Perfil inativo do controller impede component scanning; sua única instância vem do @Bean explícito dessa configuração. Nenhum GET/POST/PUT/DELETE de manutenção foi criado, nenhum endpoint futuro foi simulado como pronto. Resposta 200 dos controles locais contém somente probeAccepted e demonstra que a instrumentação alcança leitura autorizada, sem comprovar qualquer uma das seis operações administrativas.

- **Filtro → diagnóstico**: interceptor MVC registra o principal/authorities reais já autorizados pelo filtro e, antes de Bean Validation, remove ADMIN ou a conta em transação REQUIRES_NEW commitada. A constraint local chama a guarda real em ConstraintValidator.isValid. Hibernate Validator real envolve a exceção da guarda em ValidationException; nenhuma ValidationException é fabricada pelo teste.
- **Boundary → serviço**: Bean Validation passa com a conta inicial; somente depois disso o controller local aplica a alteração commitada. Serviço local via proxy Spring inicia transação e chama AdminAccessService antes de qualquer leitura restrita. Só quando a guarda passa faz um SELECT de contagem de produto via JDBC, que funciona como controle positivo do ponto protegido.
- **Falha interna**: advice temporário no proxy real de UserAccountRepository observa findByIdWithRoles e lança DataAccessResourceFailureException apenas para a conta escolhida, após o ponto de alteração estar alcançado. AuthenticatedAccountService real converte para OrderAccountResolutionException. É falha injetada na fronteira do lookup antes do SQL, não falha física do H2 nem erro SQL real; não substitui prova desses cenários. O advice é removido depois de cada caso.
- **Observabilidade**: principal/roles do filtro são conferidos, chamadas de resolução têm transações reais, etapas diagnóstico/controller/serviço são contadas, exceção original é identificada na cadeia de causas, DTO local permanece com o marcador fornecido e snapshots/conta/perfis são relidos por conexão independente antes da limpeza. StatementInspector observa SQL Hibernate somente nesta configuração; não observa JDBC direto/binds. Após a preparação, não há DML Hibernate; as exclusões JDBC de conta/perfil são mudanças intencionais do teste, externas à guarda e à leitura protegida. Não apresentar essa observação limitada como prova completa do gate IV.

### Única execução Maven focalizada

Na raiz, sequencialmente, entre **2026-10-13T11:55:23.9973946Z** e **2026-10-13T11:57:44.0400077Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductSecurityChainTest,AdminAccessServiceTest,OrderSecurityIntegrationTest,PublicCatalogSecurityIntegrationTest,AdminFoundationIntegrationProbeTest' '-Dsurefire.reportNameSuffix=feature005-t013' test
```

**BUILD FAILURE, exit 1**, duração Maven **02:14 min**. Console/Surefire/XML: **366 testes, 8 falhas, 0 erros, 0 ignorados; 358 aprovados**. Compiladas 49 fontes de teste, javac release 21; produção já compilada e intacta. Nenhuma falha de compilação, contexto, fixture, ambiente ou captura, nenhuma repetição Maven. Wrapper configurado para Maven 3.9.16; sem repetir --version. XML registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10. Avisos preexistentes de unchecked/agente Java/CDS/H2Dialect mantidos, sem modificar fontes para silenciá-los. Selecionados somente os quatro grupos de T013 e a prova adicional autorizada; não houve suíte completa.

| Grupo | Testes | Falhas | Resultado |
| --- | ---: | ---: | --- |
| AdminProductSecurityChainTest | 103 | 0 | GREEN de T006: rejeições antes de corpo/MVC e controles públicos/pedidos, assertions intactas. |
| AdminAccessServiceTest | 13 | 0 | GREEN de T011: contrato unitário da guarda, assertions intactas. |
| OrderSecurityIntegrationTest | 185 | 0 | GREEN, filtros/login/perfis atuais/guardas reais de pedidos CLIENT ou ADMIN preservados. |
| PublicCatalogSecurityIntegrationTest | 33 | 0 | GREEN, nested EmptyCatalog; container externo tem zero casos próprios e não é contado novamente. |
| AdminFoundationIntegrationProbeTest | 32 | 8 | 24 GREEN; oito falhas de tradução de credencial/permissão envolvidas pelo diagnóstico. |

Os **334 testes nominalmente previstos em T013 estão GREEN nesta execução**. Isso não aprova sozinho o checkpoint ampliado pelas provas explicitamente solicitadas. Não somar esses 334/358 aos resultados históricos de 455, 120, RED anteriores ou outros checkpoints como testes distintos.

### Provas efetivas e oito falhas examinadas

| Momento/condição | Casos | Esperado | Observado |
| --- | ---: | --- | --- |
| Controles ADMIN isolado/ambos sem alteração, nos dois momentos/contextos | 8 | Filtro, diagnóstico e serviço aceitam; três resoluções atuais e uma leitura restrita local. | 200; três lookups em transações, diagnóstico e serviço exercitados, leitura positiva, snapshot igual. |
| Revogar ADMIN após boundary/antes do serviço | 4 | 403, AccessDeniedException, sem leitura restrita. | GREEN: 403 com envelope próprio; três lookups; serviço alcançado e guarda rejeita antes do SELECT restrito. |
| Remover conta após boundary/antes do serviço | 4 | 401 com Bearer, BadCredentialsException, sem leitura restrita. | GREEN: 401 com challenge/envelope próprios; três lookups; conta removida visível por outra conexão. |
| Falha de lookup após boundary/antes do serviço | 4 | 500 seguro, OrderAccountResolutionException distinta. | GREEN: 500, causa interna preservada, sem challenge/leitura restrita. |
| Falha de lookup antes do diagnóstico | 4 | ValidationException envolvendo resolução interna, 500 seguro. | GREEN: wrapper real e causa OrderAccountResolutionException confirmados; diagnóstico alcançado, controller/serviço não entram. |
| Revogar ADMIN antes do diagnóstico | 4 | ValidationException com causa AccessDeniedException deve preservar 403. | **RED: 500** com mensagem interna genérica. Falham status e JSON; duas resoluções, causa conhecida, zero serviço/leitura e snapshot intacto passam. |
| Remover conta antes do diagnóstico | 4 | ValidationException com causa BadCredentialsException deve preservar 401/Bearer. | **RED: 500 sem challenge**. Falham status, JSON e challenge; duas resoluções, causa conhecida, remoção commitada e ausência de leitura/DML de aplicação passam. |

As oito falhas são MultipleFailuresError por assertions, nos casos parametrizados **[5]–[12]**. Não há escape de exceção/contexto defeituoso. [Catálogo integral das oito falhas](../../target/feature-005-t013/20261013T114844401Z/diagnostic-failures.json) identifica cada testcase e todas as assertions que falharam. [Resumo dos 32 caminhos efetivamente exercitados](../../target/feature-005-t013/20261013T114844401Z/integration-proofs.log) registra momento, condição, perfil preparado, contexto, status esperado/real, lookups, diagnóstico/serviço/leitura, wrapper/causa e igualdade do snapshot, sem IDs/credenciais/tokens.

**Causa confirmada**: o catch genérico administrativo de GlobalExceptionHandler trata ValidationException como falha inesperada 500 e não examina a causa conhecida BadCredentialsException/AccessDeniedException. A guarda real já rejeita corretamente; os dados restritos não são lidos e os dados de negócio permanecem intactos. O defeito é a classificação HTTP/envelope/challenge no wrapper. Reconhecer essas causas está previsto em **T033**, com provas futuras T027/T039/T067/T072. Não antecipar esses componentes neste checkpoint; nenhum arquivo de produção foi alterado. Preservada a implementação básica de T012 e suas assertions; não transformar o 500 em expectativa aceitável para fechar T013.

### Preservação e limites do checkpoint

- [Console Maven](../../target/feature-005-t013/20261013T114844401Z/maven.log) e [comando/horários/exit/branch/HEAD](../../target/feature-005-t013/20261013T114844401Z/maven-result.json).
- [XML das provas integradas](../../target/feature-005-t013/20261013T114844401Z/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminFoundationIntegrationProbeTest-feature005-t013.xml), [XML de T006 GREEN](../../target/feature-005-t013/20261013T114844401Z/surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminProductSecurityChainTest-feature005-t013.xml), [XML de T011 GREEN](../../target/feature-005-t013/20261013T114844401Z/surefire-reports/TEST-com.devsuperior.dscommerce.services.AdminAccessServiceTest-feature005-t013.xml) e [manifesto dos 12 relatórios atuais](../../target/feature-005-t013/20261013T114844401Z/current-reports-manifest.json); demais XML/TXT da mesma invocação no diretório próprio.
- [Fontes efetivamente executadas](../../target/feature-005-t013/20261013T114844401Z/tested-sources.json) e [manifesto dos 138 relatórios anteriores](../../target/feature-005-t013/20261013T114844401Z/prior-surefire-manifest.json), copiados antes do Maven para prior-surefire-reports.
- [Auditoria de preservação/contagens/versões](../../target/feature-005-t013/20261013T114844401Z/verification-summary.json), em **2026-10-13T11:58:45.8190613Z**, exit 0: todos os **183 arquivos iniciais rastreados/não rastreados** mantinham hashes antes deste registro; todas as pendências T006–T012, produção, assertions, suporte e seeds preservados. Relatórios anteriores e cópias mantêm SHA-256/tamanho/timestamps. Diretório compartilhado tem 150 relatórios: 138 anteriores + 12 atuais, sem sobrescrever evidências. Quickstart/tasks anteriores copiados; este registro é acrescentado sem reescrever histórico. Evidências locais em target continuam ignoradas.

**Limite do diagnóstico/prova**: constraint local testa somente a guarda e o wrapper real do framework. Não implementa ProductWriteRequestDTO, política de entrada, ProductCategoryDiagnosticService ou ValidProductInput; não avalia os cinco campos/categorias, não realiza manutenção nem prova US2.6, concorrência geral, revalidação de referências ou rollback após DML. Sanitização de resposta/logs normais foi conferida somente no alcance desses probes; fault injection anterior ao SQL não é indisponibilidade física do banco. Guardas são avaliações no instante da consulta, sem promessa de serializar toda mudança concorrente de perfil. **V03 e V10 permanecem pendentes**, assim como as provas futuras da exceção do gate IV (T027/T039/T040/T051/T067/T079 e fechamento); não houve PASS estrito ou aprovação integral de V01/V02.

**Estado final da tarefa**: T001–T012 permanecem `[X]`; **T013 permanece `[ ]`** porque os critérios de diagnóstico solicitados não estão todos GREEN; T014–T086 abertas. tasks.md não foi editado nesta execução. Há dependência funcional concreta da tradução das causas conhecidas de ValidationException, não impedimento de ambiente/preparação. Somente teste novo e este registro foram produzidos; nenhuma produção corrigida, assertion antiga/nova enfraquecida ou T014+ executada. Diff, arquivo novo, caminhos/links e whitespace revisados; índice vazio e HEAD inalterado. Sem stage, commit, fetch, push, suíte completa ou ações remotas.

## Correção mínima do bloqueio de T013 — parcela de segurança de T033 — 2026-10-13

**Resultado atual: T013 aprovado e `[X]`; T033 permanece `[ ]`.** Este registro sucede, sem substituir, o RED de integração acima. Raiz `C:/Users/ronal/dscommerce-speckit`, branch real `005-manter-produtos`, HEAD `a9853816226d5abc43edfc2ec0fc81b74400ad7e`, índice vazio. Autorização restrita ao handler, seu teste direto e tasks/quickstart. AGENTS aplicável conferido; pré-requisito Spec Kit `-Json -RequireTasks -IncludeTasks`, exit 0, resolveu esta feature; checklist requirements 16/16, sem alteração. Constituição, planejamento, research, modelo e contrato consultados. `.specify/extensions.yml` ausente, sem hooks; ignore Maven existente adequado.

**Conclusão recuperada da análise anterior**: os 334 testes nominais de T013 estavam GREEN; o checkpoint ampliado pelas provas solicitadas tinha oito falhas funcionais, todas na tradução de ValidationException antes do diagnóstico. A guarda rejeitava corretamente e nenhuma leitura restrita era realizada. Quatro casos de ADMIN revogado esperavam 403; quatro de conta removida esperavam 401/Bearer; todos recebiam 500. Não eram falhas de compilação/contexto/fixture/ambiente. O probe foi autorizado e usa autenticação e wrapper reais, com auxiliares somente de teste; não entrega diagnóstico de produto ou CRUD. A tradução estava agrupada com a agregação de campos em T033, dependente de T032, cuja sequência já depende de T013. Exigir T033 inteira criaria ciclo. A dependência omitida foi resolvida explicitando em tasks a sequência T012 → parcela de segurança de T033 → T013 → T014–T032 → restante de T033, preservando os 86 IDs.

**Alterações delimitadas**: GlobalExceptionHandler adapta somente o ramo administrativo genérico. Remove apenas wrappers da classe conhecida jakarta.validation.ValidationException (inclusive encadeados); reconhece BadCredentialsException como 401/Bearer e AccessDeniedException como 403. Para na primeira causa que não seja esse wrapper: resolução interna, exceção inesperada, wrapper sem causa ou tipo de validação não reconhecido permanecem 500 seguro. Há proteção contra ciclo de causas. Não atravessa OrderAccountResolutionException/IllegalStateException para aproveitar causa profunda de segurança. Reutiliza o envelope administrativo e mensagens fixas, errors vazio, path externo sem query; não usa texto de exceção para classificação. Tratamentos diretos e passagem dos erros HTTP nativos, filtros/converter/guardas, contratos públicos, login, pedidos e seeds não foram alterados nesta correção.

AdminGuardExceptionHandlerTest recebeu **44 casos complementares**, preservando seus **22 casos anteriores**: 16 combinações de wrappers simples/duplos e causas 401/403/500 em raiz e /api; 16 de falha interna ou inesperada com causa profunda de segurança, com/sem wrapper externo; seis controles públicos/login/namespace parecido; dois de pedidos, dois de contexto chamado /admin e dois de wrapper sem causa. As assertions originais e o arquivo integral de AdminFoundationIntegrationProbeTest permaneceram idênticos. Os testes diretos usam exceções preparadas; a prova integrada mantém Hibernate Validator, login/JWT/filtros e guarda reais.

### Execuções sequenciais pelo Maven Wrapper

**1. RED focado antes de implementar o handler**, de **2026-10-13T12:18:21.5193457Z** a **12:18:54.9376328Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminGuardExceptionHandlerTest' '-Dsurefire.reportNameSuffix=feature005-t013-correction-red' test
```

**BUILD FAILURE esperado, exit 1**, duração Maven 28.364 s: **66 testes, oito falhas de assertions, zero erros/ignorados; 58 controles GREEN**. Os oito casos de wrapper simples/duplo com causa de credencial/permissão recebiam 500 em vez de 401/403. Os controles de falha interna com causa profunda de segurança já passavam. Produção permaneceu idêntica à cópia inicial até a confirmação desse RED. [Console RED](../../target/feature-005-t013-correction/20261013T121711488Z/focused-red.log), [comando/horários/exit](../../target/feature-005-t013-correction/20261013T121711488Z/focused-red-result.json), [oito falhas examinadas](../../target/feature-005-t013-correction/20261013T121711488Z/focused-red-cases.json) e [XML RED preservado](../../target/feature-005-t013-correction/20261013T121711488Z/red-surefire-reports/TEST-com.devsuperior.dscommerce.controllers.AdminGuardExceptionHandlerTest-feature005-t013-correction-red.xml).

**2. GREEN focado após a correção**, de **2026-10-13T12:19:55.8868605Z** a **12:20:30.9081015Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminGuardExceptionHandlerTest' '-Dsurefire.reportNameSuffix=feature005-t013-correction-green' test
```

**BUILD SUCCESS, exit 0**, duração Maven 29.985 s: **66 testes, zero falhas/erros/ignorados**. GREEN com as mesmas assertions do RED. [Console GREEN](../../target/feature-005-t013-correction/20261013T121711488Z/focused-green.log), [comando/horários/exit](../../target/feature-005-t013-correction/20261013T121711488Z/focused-green-result.json) e [XML focado](../../target/feature-005-t013-correction/20261013T121711488Z/green-surefire-reports/TEST-com.devsuperior.dscommerce.controllers.AdminGuardExceptionHandlerTest-feature005-t013-correction-green.xml).

**3. Conjunto de T013 e prova adicional após o GREEN focado**, de **2026-10-13T12:21:13.7170867Z** a **12:23:16.0015668Z**:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress '-Dtest=AdminProductSecurityChainTest,AdminAccessServiceTest,OrderSecurityIntegrationTest,PublicCatalogSecurityIntegrationTest,AdminFoundationIntegrationProbeTest' '-Dsurefire.reportNameSuffix=feature005-t013-corrected-checkpoint' test
```

**BUILD SUCCESS, exit 0**, duração Maven 01:57 min: **366 testes, zero falhas/erros/ignorados**. Não foi suíte completa, repetição dos 455 testes ou execução de outras partes de T014+. A repetição dos seletores de T013 é necessária pela alteração do handler compartilhado e comprova a passagem do RED integrado para GREEN. Não somar tentativas RED/GREEN nem resultados históricos como testes distintos. Wrapper seleciona Maven 3.9.16; XML registra Java 21.0.12.1, runtime 21.0.12.1+1-LTS, Azul Systems, Inc., Windows 10; javac release 21. Sem repetir --version ou alterar avisos preexistentes de agentes/CDS/H2Dialect/unchecked para silenciá-los.

| Grupo | Testes | Falhas/erros | Resultado atual |
| --- | ---: | ---: | --- |
| AdminProductSecurityChainTest | 103 | 0/0 | GREEN, bloqueio antes do MVC e controles autorizados preservados. |
| AdminAccessServiceTest | 13 | 0/0 | GREEN da guarda atual. |
| OrderSecurityIntegrationTest | 185 | 0/0 | GREEN, pedidos CLIENT ou ADMIN e autenticação real preservados. |
| PublicCatalogSecurityIntegrationTest | 33 | 0/0 | GREEN, casos do nested EmptyCatalog; container externo zero não contado novamente. |
| AdminFoundationIntegrationProbeTest | 32 | 0/0 | GREEN, mesmas assertions da execução com oito falhas. |

**Provas efetivas atuais**: as quatro revogações antes do diagnóstico agora retornam 403; as quatro remoções de conta retornam 401 com Bearer. Nos oito casos, o wrapper/cause original é conferido, há duas resoluções atuais, diagnóstico alcançado e zero entrada no serviço/leitura restrita. Falhas internas antes do diagnóstico continuam 500 sem challenge. Após o boundary, revogação/remoção/falha interna preservam 403/401/500, com três resoluções atuais e guarda do serviço antes da leitura. Os oito controles sem alteração chegam à leitura autorizada local. Todos os 32 caminhos conferem snapshot independente intacto e sanitização de resposta/logs normais no alcance da prova. [Console do checkpoint](../../target/feature-005-t013-correction/20261013T121711488Z/checkpoint-green.log), [comando/horários/exit](../../target/feature-005-t013-correction/20261013T121711488Z/checkpoint-green-result.json), [resumo por grupo](../../target/feature-005-t013-correction/20261013T121711488Z/checkpoint-test-summary.json), [32 caminhos](../../target/feature-005-t013-correction/20261013T121711488Z/integration-proofs-green.log) e [XML integrado GREEN](../../target/feature-005-t013-correction/20261013T121711488Z/checkpoint-surefire-reports/TEST-com.devsuperior.dscommerce.config.AdminFoundationIntegrationProbeTest-feature005-t013-corrected-checkpoint.xml).

**Preservação e revisão**: [manifesto inicial dos 184 arquivos](../../target/feature-005-t013-correction/20261013T121711488Z/initial-files.json), [150 relatórios anteriores](../../target/feature-005-t013-correction/20261013T121711488Z/prior-surefire-manifest.json), cópias em prior-surefire-reports e dos quatro arquivos permitidos em before. Sufixos distintos impediram sobrescrita: dois relatórios RED + dois GREEN focados + 12 do checkpoint; diretório compartilhado passa a **166 relatórios**. Preservados os relatórios anteriores por hash/tamanho/timestamp e o RED de integração em target/feature-005-t013/20261013T114844401Z. [Manifesto anterior ao checkpoint](../../target/feature-005-t013-correction/20261013T121711488Z/before-checkpoint-reports.json), [fontes efetivamente verificadas](../../target/feature-005-t013-correction/20261013T121711488Z/tested-sources.json) e [auditoria final](../../target/feature-005-t013-correction/20261013T121711488Z/verification-summary.json). Diff revisado contra as cópias iniciais, inclusive o teste não rastreado; assertions anteriores preservadas. git diff --check e comparação de whitespace do teste direto sem erros, apenas avisos LF/CRLF preexistentes nos documentos/arquivo novo.

**Limites e pendências**: T001–T013 `[X]`; T014–T086 abertas, incluindo T033. Sua agregação/ordenação/deduplicação, distinção body/campos e integração com ProductWriteRequestDTO/diagnóstico efetivo continuam após T032. A resposta 200 dos controles é de rota local de teste, não de endpoint administrativo futuro. A falha de lookup é injetada no proxy antes do SQL, não indisponibilidade física do H2; prova futura T067 ainda deve cobrir o escopo real previsto. Não implementados CRUD, leitor/DTO/validator/diagnóstico de produto ou regras adicionais. **V03/V10 e provas futuras da exceção do gate IV permanecem pendentes**; não há aprovação integral de V01/V02, US2.6 ou PASS estrito do gate IV. Guardas continuam avaliações no instante da consulta. Alterados somente os quatro arquivos autorizados, preservadas todas as outras pendências. Índice vazio, HEAD/branch inalterados; sem stage, commit, fetch ou push.
