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
