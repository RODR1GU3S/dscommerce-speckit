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
