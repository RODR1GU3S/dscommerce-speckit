# Quickstart: validação da Feature 008

**Estado**: guia de verificação futura. A operação008 não está implementada. Nenhum comando Maven, teste da aplicação, servidor ou chamada HTTP deste guia foi executado neste planejamento. Evidências documentais realmente obtidas ficam na seção final. [Spec](spec.md) | [Plano](plan.md) | [Research](research.md) | [Modelo](data-model.md) | [Contratos](contracts/README.md)

## Pré-condições e ambiente futuro

- Java21 e Wrapper Maven3.9.16 do projeto; Windows/PowerShell local, CI Linux conforme [harness](../../docs/harness.md). Comandos abaixo partem da raiz; não mudar main ou misturar artefatos de outra revisão.
- Implementação008 e respectivas classes de verificação ainda precisam existir. Não executar seleção vazia como aprovação.
- H2 real2.2.224, banco exclusivo por contexto/instância, READ_COMMITTED observado na conexão. Fixtures commitadas antes das chamadas; sem @Transactional externo no teste/controller; proxy real precisa possuir commit.
- Contextos008 novos mantêm URL exclusiva e estável, sem reaproveitar banco de outro create-drop. DB_CLOSE_DELAY=-1 não impede DROP por outro contexto. Não alterar perfil/seed/cache global, H2ConsoleSecurityIntegrationTest ou JwtTokenServiceTest para esconder interferência.
- ADMIN isolado, ADMIN+CLIENT, CLIENT, conta sem perfil, titular diferente e pedidos WAITING_PAYMENT sem pagamento preparados explicitamente. Para consultas isoladas PAID, preparar estado e pagamento coerentes; para jornada, usar operação real008.
- Limpeza seletiva: pagamentos próprios antes de itens/pedidos, depois demais dados da fixture. Não apagar tabelas globais. Probe008 fotografa as sete tabelas já observadas pelo probe007 e tb_payment, inclusive contas/perfis e compras não alvo.

## Execução futura por grupos

Executar apenas após implementação; uma invocação Maven por vez no workspace. Preservar relatórios antes da próxima chamada, porque target/surefire-reports é compartilhado. Registrar comando, data, branch, HEAD/diff, versões, exit, casos e causa de eventual impedimento.

~~~powershell
.\mvnw.cmd test
~~~

Em Linux, a CI usa ./mvnw --batch-mode --no-transfer-progress test. Esse comando é procedimento futuro aqui, não execução atual. Primeiramente executar grupos focalizados/classes reais da008 quando criados; depois a suíte completa, sem filtros que excluam regressões ou classes aninhadas.

| Grupo | Prova futura e resultados exigidos |
| --- | --- |
| V01 — fronteira/HTTP | Reader específico e Bean Validation MVC antes de serviço: objeto/campo único, ausência/null/tipo, propriedade extra (amount/buyer/status/orderId no corpo), duplicatas, trailing JSON, query e ID inválidos. 400 seguro; 201 só três campos; 401 com Bearer, 403/404/409/500 e headers corretos; 405/406/415 nativos preservados. Incluir ROOT e contexto /api, sem alterar ObjectMapper global. |
| V02 — tempo | Clock fixo e ocorrência nos limites inclusivos, antes da criação e depois da referência. Offset equivalente e .123000000 conservam Instant; .123000001, sem offset, -00:00, leap second, hora24, datas/anoUTC inválidos e espaços rejeitados. Conferir UTC.SSS e round-trip JDBC sem perda. Referência conserva nanos nativos e nunca é recapturada. |
| V03 — conta/perfil | ADMIN isolado/ambos aceitos, CLIENT/sem perfil403, token ausente/inválido/expirado/conta removida401, lookup falho500. Alterar roles depois da emissão e entre filtro/serviço. Durante lock, remover ADMIN por transação independente; releitura escalar após espera precisa impedir a escrita. Não prometer serializar revogação depois da última leitura. |
| V04 — elegibilidade/histórico | WAITING_PAYMENT sem pagamento aceita; qualquer pagamento anterior rejeita, inclusive estado inconsistente WAITING_PAYMENT; PAID sem pagamento rejeita sem reparar. Pedido ausente404. Confirmar 2*10,00+3*7,50=42,50 e 2*10,00=20,00 mesmo catálogo12,00; nomes/preço zero/categorias atuais não alteram compra histórica. Preservar dados completos. |
| V05 — unicidade/concorrência | Repetir com ocorrência igual/diferente e outro ADMIN:409 sem sobrescrita. Duas transações/conexões reais no mesmo pedido, com ocorrências iguais/diferentes: um201/um409, uma linha Payment e ocorrência vencedora. Em pedidos diferentes ambas201, vínculos exclusivos. PK duplicada/FK órfã/NOT NULL falham no banco; lock/refresh não lê entidade antiga. |
| V06 — atomicidade/commit | Falhas após primeiro DML real, após flush, beforeCommit com DTO interno já criado e commit JDBC antes de delegar ao driver. Nenhuma entrega de sucesso e baseline restaurada por conexão independente. Controle positivo efetiva commit, devolve201 e fica visível. Não continuar consultando em transação falha. |
| V07 — depois do commit | Instrumentar confirmação deliberadamente impedida após retorno do proxy e commit comprovado; Payment/PAID permanecem, novo POST409 e titular GET/HEAD vê PAID. Separar camada HTTP simulada de experimento de socket real, conforme limites abaixo. |
| V08 — consultas007 | Titular vê PAID em lista/detalhe, mesmas chaves, dinheiro e ordem dos itens. ADMIN que quitou compra alheia continua sem conteúdo/count alheios e recebe404 igual ao inexistente; titular consegue ler. Cinco compras em2/2/1 com empates preservam criação DESC/ID DESC depois de quitar uma antiga. GET/HEAD não fazem DML nem alteram qualquer Payment. |
| V09 — regressões | Catálogo público001/002/006, busca literal e paginação, login003, registro004 novo pedido por envio válido e WAITING_PAYMENT, manutenção005 e proteção histórica, consultas007. Nenhum pagamento movimenta dinheiro, toca produto/conta/perfil/outro pedido ou esvazia carrinho. |
| V10 — conformidade | Reavaliar gates pelo código efetivo; schema gerado/precisão/SQL de lock, proxy dono, ausência de IO em validação, snapshots e restrições. Contratos008 vigentes após entrega,007 histórico intacto. GateIV005 continua exceção documentada; não herdá-la. |

V01–V10 e a matriz do plano cobrem todos os 30 cenários,22 FR e8 SC. Os 100% da spec se referem aos casos definidos, não a cobertura universal, SLA ou prova já obtida.

## Concorrência e referência: coordenação exigida

Não usar sleeps como prova de ordenação. Usar latches/barreiras e instrumentação de SQL/conexão para observar entrada no serviço, referência capturada, tentativa de lock e liberação. Timeout do teste deve ser finito e operacional; configurar timeout H2 apenas nos contextos008 de modo a não transformar a espera deliberada em falha não pretendida.

Para mesmo pedido, segurar a raiz em transação auxiliar; iniciar a tentativa e observar sua captura de referência antes de esperar. Avançar Clock enquanto o lock permanece: uma ocorrência posterior à referência inicial continua inválida mesmo quando seria passada no novo relógio; em outro caso, igualdade com a referência inicial permanece válida. Liberar lock e verificar resultado e snapshots após término.

As verificações de chamada única de Clock são scoped à avaliação do serviço: segurança JWT também usa Clock e não deve ser confundida com recaptura da referência de negócio. Com referência nativa submilissegundo, verificar comparação exata e não truncagem; igualdade superior usa Clock representável no domínio de entrada.

Para dois pedidos distintos, manter A sob lock enquanto B conclui em outra conexão; depois liberar A e exigir ambas as aceitações. Isso comprova ausência de mutex/lock global nesse desenho, não um SLA. Conferir que constraints e observadores de teste não introduzem serialização falsa.

## Rollback, commit e transporte: provas separadas

- [OrderRollbackIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java) e [AdminProductRollbackIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductRollbackIntegrationTest.java) são referências de instrumentação, não aprovação008. Falha SQL/pós-flush ocorre antes de commit; beforeCommit após DTO interno também é anterior à efetivação.
- Instrumentar DataSource/Connection somente de teste para lançar antes de delegar Connection.commit, identificando tentativa, delegação e commit bem-sucedido. Isso prova falha conhecida anterior à efetivação, não falha física do banco ou resultado desconhecido. Executar controle positivo sem injeção.
- Simulação determinística de entrega: resposta envolvida em buffer de teste e failpoint após retorno transacional; confirmar commit por conexão independente antes de impedir que a confirmação seja entregue. MockMvc/filtro prova o caminho da aplicação e preservação dos dados, não perda física de rede.
- Experimento complementar HTTP real: servidor em porta aleatória e proxy/socket de teste que receba confirmação a montante após commit observado, mas encerre conexão a jusante sem entregar o corpo ao cliente. Documentar o que foi realmente impedido, bytes recebidos e tempos, sem atribuir isso a falha real de rede/servidor.
- Apenas descartar o DTO/response no teste não simula transmissão interrompida. Não marcar V07 completo com essa substituição.
- Exceção depois de o driver efetivar commit, ou desconexão com resultado indeterminado, não admite promessa de rollback. Nunca compensar apagando pagamento. O titular pode consultar PAID e novo envio, se o pagamento existir, é409; isso não implementa recuperação idempotente da confirmação.

## Ambientes, observação e limites

HTTP real ROOT e /api verifica filtros, binding, proxy e serialização; MockMvc é complementar para falhas difíceis de acionar. H2 real e conexões distintas verificam o comportamento da configuração testada. Não há PostgreSQL configurado; modo de compatibilidade não comprova outro banco, durabilidade ou crash recovery.

Snapshots são feitos após conclusão das tentativas e antes da limpeza, sem escritores não controlados. StatementInspector não observa JDBC direto, e igualdade de fotografias não detecta toda mudança transitória revertida; combinar instrumentos sem alegar observação universal. Probe008 deve ter teste de sensibilidade que detecte alteração de Payment, e as janelas de GET começam depois de setup/login/quitação.

Após implementação, registrar os testes concretos alterados: OwnOrderQueryDtoContractTest, OwnOrderQueryRepositoryTest, OwnOrderQueryServiceTest, classes HTTP de lista/detalhe/segurança/regressão e aliasesApi, fixtures/probe008. Expectativas de criação em OrderRegistrationIntegrationTest, OrderRepositoryTest e regressões continuam WAITING_PAYMENT; não alterar a regra de novo pedido por envio válido.

Resultados locais futuros não são CI. Cada execução remota requer URL/evento/revisão/ambiente/relatórios; push/merge preparado pode testar outro SHA. Relatórios históricos e gates de desenho não aprovam008.

## Validação documental desta fase

A execução efetiva de validação documental é registrada abaixo após geração e revisão dos artefatos. Ela não executa Maven nem valida comportamento da aplicação.

### Execuções documentais observadas em 2026-10-21

1. Estado inicial conferido por Git em 24a29a9c031fc554f2b6deb6a4b5c2fde708719d, limpo na008; seleção local preservada. setup-plan.ps1 -Json criou somente o template do plano008. Arquivo .specify/extensions.yml ausente nas conferências anteriores e posteriores: nenhum hook before_plan/after_plan registrado para executar.
2. Validação executada via Python existente fora do projeto, sem instalação nem dependência nova da aplicação. Invocação usada, com script documental via stdin: & 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 -. Versões observadas: openapi-spec-validator0.9.0, openapi-schema-validator0.9.0, PyYAML6.0.3 e jsonschema4.26.0. Resultado: exit0.
3. O script leu YAML com rejeição de chaves duplicadas; validou OpenAPI, schemas, exemplos/defaults e referências internas/arquivos de metadados; comparou estruturalmente o snapshot de consultas com007, admitindo somente o delta declarado. Não fez chamadas à aplicação.
4. Foram conferidos triplets e campos de exemplos de erro, dinheiro com Decimal, metadados de páginas e ambos os estados nos dois schemas de consulta; PAID é rejeitado pelo contrato antigo e aceito pelo alvo008.

| Documento | OpenAPI | Schemas | Exemplos | Defaults | Referências internas |
| --- | --- | --- | --- | --- | --- |
| payment-registration-openapi.yaml | válido | 5 | 12 | 0 | 16 |
| own-orders-openapi.yaml | válido | 7 | 15 | 2 | 26 |

Sete verificações negativas de schema rejeitaram estado desconhecido em resumo/detalhe, novos campos de pagamento nas consultas, amount no corpo de registro, paymentId na confirmação e combinação incorreta de mensagem/status de erro400. O oráculo documental de tempo aceitou oito casos e rejeitou onze, cobrindo offset equivalente, zeros adicionais, submilissegundo, data impossível, leap second, hora24, offset/ano e espaços. Esse oráculo é Python sobre o contrato e calendário: **não executa nem comprova o futuro parser Java, Bean Validation, banco, referência de negócio ou endpoint**.

Foram conferidos 68 links locais Markdown na feature, sem ausências, e ausência de placeholders de template e tasks.md. No fechamento executado, a matriz cobriu os 22 FR; os sete arquivos novos foram revisados e passaram na conferência de whitespace por git diff --no-index --check contra /dev/null, sem diagnósticos (exit1 nesse modo indica arquivo novo diferente). git diff --check, git diff --exit-code e git diff --cached --exit-code retornaram0. Spec/checklist, código, configuração, testes e contratos históricos permaneceram intactos; índice vazio, HEAD/branch/main e hash da seleção008 preservados. Nenhuma dessas conferências aprova implementação ou gate em runtime.

Para reproduzir somente a validação OpenAPI básica no ambiente observado, sem Maven:

~~~powershell
@'
from pathlib import Path
import yaml
from openapi_spec_validator import validate
base = Path("C:/Users/ronal/dscommerce-speckit/specs/008-registrar-pagamento/contracts")
for name in ("payment-registration-openapi.yaml", "own-orders-openapi.yaml"):
    validate(yaml.safe_load((base / name).read_text(encoding="utf-8")))
    print(name, "OK")
'@ | & 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 -
~~~

Esse comando básico não substitui o complemento efetivamente executado para exemplos, duplicatas, referências, delta e oráculos. O caminho de cache é o observado neste ambiente, não uma dependência portátil garantida; outro ambiente precisa de ferramentas documentais equivalentes fora do projeto.

### Pendências de execução

Continuam futuras todas as provas V01–V10, geração/inspeção do schema, round-trip temporal JPA, SQL de lock/refresh, constraints, perfis atuais, concorrência real, rollback/commit, entrega de resposta, leituras PAID e regressões. Windows/Linux/CI de aplicação não foram executados nesta fase; PostgreSQL não foi preparado ou presumido. Gates008 estão aprovados somente como desenho/estratégia; o GateIV005 permanece exceção histórica documentada.

## T001 — Conferência de contexto e ambiente em 2026-10-21

**Escopo executado**: somente [T001](tasks.md), por autorização expressa do usuário. A seção anterior permanece como registro histórico do planejamento. Esta seção registra conferências documentais e de ambiente; não comprova funcionamento da008 nem executa o baseline da T002.

### Estado inicial e fontes

| Conferência efetivamente realizada | Resultado observado |
| --- | --- |
| `git rev-parse --show-toplevel` | `C:/Users/ronal/dscommerce-speckit` |
| `git branch --show-current` | `008-registrar-pagamento` |
| `git rev-parse HEAD` | `463ccaf006763d5e636eaeabe47031e669a48a96` |
| `git status --short --untracked-files=all` | Saída vazia: Git inicialmente limpo, sem alterações staged ou não rastreadas a preservar. |
| `git rev-parse main` | `5cd16709ea531ccae27230db19753e6c5533d12d`; branch não alterada. |
| `git merge-base --is-ancestor db5b790db0a7cd2338d964ae4f322eda93243937 HEAD` | Exit0: base do planejamento é ancestral do HEAD efetivo. Histórico imediato: tarefas463ccaf, planejamento db5b790 e especificação24a29a9. |
| Leitura de `.specify/feature.json` e dos overrides de feature no ambiente | Seleção `specs/008-registrar-pagamento`; SPECIFY_FEATURE e SPECIFY_FEATURE_DIRECTORY ausentes. SHA-256 da seleção: `FF185E0FF10D010A4E45B56A3666C1829F365EF09898107AA374D6CAC34E4BF4`. |
| `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` | Exit0; FEATURE_DIR absoluto da008; AVAILABLE_DOCS: research.md, data-model.md, contracts/, quickstart.md e tasks.md. A resolução usou a seleção existente, sem persistir override. |

Foram lidos T001 e o contexto de tarefas, [AGENTS.md](../../AGENTS.md), [harness](../../docs/harness.md), [constituição1.0.0](../../.specify/memory/constitution.md), [spec](spec.md), [checklist](checklists/requirements.md), [plano](plan.md), [pesquisa](research.md), [modelo](data-model.md), este quickstart e os três arquivos de [contracts/](contracts/README.md). O único checklist é requirements.md:16 itens marcados,0 abertos, **PASS documental**, sem alteração dos marcadores e sem equivalência a implementação/testes. A exceção histórica do GateIV005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito.

`.specify/extensions.yml` ausente na conferência: nenhum hook before_implement registrado. A leitura de [.gitignore](../../.gitignore) confirmou os padrões Java existentes target/, *.class, *.jar, build/ e .gradle/; não houve criação ou ajuste de ignore, configuração ou infraestrutura.

### Versões efetivamente observadas

Comandos apenas de versão, executados em Windows/PowerShell5.1; a chamada de versões foi iniciada às `2026-10-21T15:18:25-03:00`:

| Comando/leitura | Resultado | Exit |
| --- | --- | --- |
| `java --version` | OpenJDK21.0.12.1 LTS, Azul Zulu21.52+203-CA, build21.0.12.1+1-LTS,64 bits. | 0 |
| `.\mvnw.cmd --version` | Apache Maven3.9.16 pelo cache `.m2/wrapper/dists`; Java21.0.12.1, vendor Azul Systems, Inc., runtime `C:\Program Files\Zulu\zulu-21`; Windows/amd64. | 0 |
| Leitura de [.mvn/wrapper/maven-wrapper.properties](../../.mvn/wrapper/maven-wrapper.properties) | wrapperVersion3.3.4, only-script, distribuição Maven3.9.16 e checksum configurado preservados, coerentes com a versão executada. | Não aplicável: leitura. |

`--version` não executou goals de build/teste, não iniciou servidor e não criou relatórios Surefire. Não foram analisados resultados antigos como baseline desta revisão. Uma tentativa adicional somente em leitura de inventariar hashes por `Get-ChildItem target -Recurse -File` e `Get-FileHash -Algorithm SHA256` foi interrompida por duração desproporcional (processo encerrado, exit-1), sem digest concluído nem arquivo de inventário produzido. Não é evidência de comparação integral dos relatórios; nenhum relatório foi regenerado ou editado por esses procedimentos.

### Contrato vigente, preservações e conclusão da T001

Conforme a leitura dos contratos e de sua precedência, permanece vigente o [OpenAPI007 de consultas](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml), versão0.1.0, com status limitado a WAITING_PAYMENT. O [contrato POST008](contracts/payment-registration-openapi.yaml), versão0.1.0, e o [snapshot completo de consultas008](contracts/own-orders-openapi.yaml), versão0.2.0, continuam **alvos planned**. Após a entrega008, o segundo substituirá integralmente o contrato de consultas007, preservado como histórico; não há dois contratos simultaneamente vigentes. PAID não autoriza novos campos ou leitura alheia nas consultas. Nenhum desses artefatos foi alterado ou aprovado funcionalmente nesta T001.

Critérios da T001 atendidos, sem impedimento para seu encerramento: contexto, ancestralidade, fontes, ambiente e vigência registrados. Somente o marcador T001 foi concluído em tasks.md; T002–T036 permanecem abertas, totalizando **1 concluída e 35 abertas**. O inventário opcional interrompido não condiciona esses critérios nem autoriza alegar preservação comprovada por hashes de todo target/.

Alterações desta execução limitadas a esta seção acrescentada e ao marcador T001. Especificação/checklist, planejamento, contratos, produção, testes, seleção local e relatórios existentes não foram modificados. Não houve preparação de fixtures/H2/Clock, implementação de pagamento, testes, baseline T002, validação OpenAPI nova, CI, stage, commit, push ou mudança de main. A revisão final do diff e whitespace é documental; os testes e gates funcionais continuam futuros.

Conferência final: diff revisado, com o texto anterior deste quickstart preservado e somente a checkbox T001 alterada em tasks.md;15 links locais da seção conferidos por resolução de caminho, todos existentes; `git diff --check` sem diagnósticos, exit0. Git com apenas quickstart.md e tasks.md modificados, nenhum arquivo novo ou staged; HEAD, main e SHA-256 da seleção iguais aos iniciais. Contagem dos marcadores:36 tarefas, somente T001 concluída e35 abertas. `.specify/extensions.yml` também ausente na conferência posterior: nenhum hook after_implement registrado.

## T002 — Baseline BASE em 2026-10-21

**Escopo executado**: exclusivamente [T002](tasks.md), dependente da T001 já concluída. O registro integral da T001 e o prefixo histórico acima foram preservados. Esta execução estabelece uma referência das regressões existentes; **não comprova o funcionamento da Feature 008** e não antecipa T003–T036.

### Estado e prevenção de duplicação

Conferências iniciais efetivamente executadas: `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short` e `git diff --cached --name-only`. Resultado: raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `463ccaf006763d5e636eaeabe47031e669a48a96`; somente este quickstart e tasks.md da008 modificados, sem staged ou não rastreados. A conferência adicional com `git status --short --untracked-files=all` confirmou o mesmo estado. Seleção local008 em `.specify/feature.json`, SHA-256 `FF185E0FF10D010A4E45B56A3666C1829F365EF09898107AA374D6CAC34E4BF4`; main em `5cd16709ea531ccae27230db19753e6c5533d12d`, sem alteração.

Foram lidos integralmente T002, sua dependência T001 e a seleção BASE/disciplina de execução em tasks.md, além do contexto aprovado de plano, quickstart, constituição e harness. O comando `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0, FEATURE_DIR absoluto da008 e os documentos esperados. O checklist requirements.md permaneceu somente em leitura:16/16 marcados,0 abertos, PASS documental. `.gitignore` já cobre target/ e os padrões Java; nenhuma mudança de configuração foi necessária.

Antes de iniciar Maven, `Get-CimInstance Win32_Process` foi filtrado por nomes Java/Maven e linhas de comando Maven/Wrapper/Surefire; não havia execução ativa. A inspeção proporcional de `target/feature008-evidence/`, dos relatórios relevantes em `target/surefire-reports/` e deste quickstart não encontrou execução anterior da T002. Os arquivos relevantes encontrados tinham sufixo histórico `t030-final-green-20261021`, da007; não foram promovidos a baseline confiável do HEAD atual. Por isso foi executado uma única vez o seletor BASE aprovado, sem ampliar a seleção. Não foi repetido o inventário global de target/ nem utilizada a tentativa incompleta da T001 como prova.

### Comandos, ambiente e preservação de evidências

Diretório exclusivo desta execução: [BASE-20261021T172512676](../../target/feature008-evidence/BASE-20261021T172512676/), ignorado pelo Git. Antes dos comandos, os218 arquivos então existentes diretamente em target/surefire-reports foram copiados para [previous-reports/](../../target/feature008-evidence/BASE-20261021T172512676/previous-reports/). Os14 arquivos históricos relevantes — sete XMLs e sete TXTs — foram identificados com tamanho, data e SHA-256 em [previous-relevant.csv](../../target/feature008-evidence/BASE-20261021T172512676/previous-relevant.csv). Ao final, seus hashes foram conferidos tanto nos originais quanto nas cópias:14/14 iguais. Essa conferência não afirma digest integral de target/ ou dos demais relatórios. As cópias iniciais dos dois documentos e initial.diff também estão nesse diretório, distinguindo as alterações já presentes das desta tarefa.

Comandos executados na raiz em Windows/PowerShell5.1.19041.7663, sequencialmente:

~~~powershell
java --version
.\mvnw.cmd --version
.\mvnw.cmd "-Dtest=OrderAggregateTest,OwnOrderQueryDtoContractTest,EffectiveAccountRolesRepositoryTest,OwnOrderQueryList*HttpIntegrationTest,OwnOrderQueryDetail*HttpIntegrationTest" "-Dsurefire.reportNameSuffix=BASE-20261021T172512676" test
~~~

`--version` confirmou Apache Maven3.9.16 pelo cache do Wrapper e Java Azul Zulu21.0.12.1 LTS, runtime `C:\Program Files\Zulu\zulu-21`, Windows/amd64; exit0. O sufixo adicional somente distingue os nomes dos relatórios, sem mudar a seleção, as asserções ou o ambiente de aplicação. Não houve clean nem Maven simultâneo. A chamada de testes foi capturada por `2>&1 | Tee-Object -FilePath <diretório exclusivo>/console.log`; `$LASTEXITCODE` foi registrado imediatamente após a pipeline. Os14 relatórios novos foram copiados para [reports/](../../target/feature008-evidence/BASE-20261021T172512676/reports/) após o término.

Janela registrada da preparação/versões/testes: `2026-10-21T17:25:19.3988793-03:00` a `2026-10-21T17:27:42.1612010-03:00`. Maven informou duração `02:08 min` e término `2026-10-21T17:27:41-03:00`. [metadata.json](../../target/feature008-evidence/BASE-20261021T172512676/metadata.json), [result.json](../../target/feature008-evidence/BASE-20261021T172512676/result.json), [console.log](../../target/feature008-evidence/BASE-20261021T172512676/console.log), java-version.txt e maven-version.txt registram comando, revisão/diff inicial, ambiente, horários e exit code. São evidências locais ignoradas pelo Git, sujeitas a ausência em outro checkout.

### Seleção efetiva e resultado

Console, sete XMLs e sete TXTs novos concordam com os métodos e as fontes de parâmetros das classes selecionadas:

| Classe efetivamente executada | Contexto/alcance | Métodos por classe | Invocações JUnit | Falhas | Erros | Ignorados |
| --- | --- | --- | --- | --- | --- | --- |
| OrderAggregateTest | Domínio sem Spring/banco | 14 | 18 | 0 | 0 | 0 |
| OwnOrderQueryDtoContractTest | JSON/DTO; sem MVC/banco | 19 | 119 | 0 | 0 | 0 |
| EffectiveAccountRolesRepositoryTest | Consulta escalar e transações H2 independentes | 3 | 6 | 0 | 0 | 0 |
| OwnOrderQueryListHttpIntegrationTest | HTTP real ROOT | 11 | 115 | 0 | 0 | 0 |
| OwnOrderQueryListApiHttpIntegrationTest | HTTP real /api | 11 | 115 | 0 | 0 | 0 |
| OwnOrderQueryDetailHttpIntegrationTest | HTTP real ROOT | 7 | 9 | 0 | 0 | 0 |
| OwnOrderQueryDetailApiHttpIntegrationTest | HTTP real /api | 7 | 9 | 0 | 0 | 0 |
| **Total** | **7 classes** | **72 métodos por classe/contexto** | **391** | **0** | **0** | **0** |

**Resultado atual: BUILD SUCCESS, exit0.** A compilação de117 fontes de teste não significa execução dessas117 classes: as linhas Running e os relatórios confirmam somente as sete classes da seleção BASE. Os métodos de lista/detalhe herdados de OwnOrderQueryListHttpTestSupport e OwnOrderQueryDetailHttpTestSupport foram verificados em ambas as subclasses ROOT/Api. Não há @Nested ou @Disabled nos fontes selecionados; os tipos aninhados AliasCase/PageCase são records auxiliares, sem testes. A configuração aninhada ProbeConfig importada pelo teste de perfis também é suporte, não classe adicional de testes.

O [method-audit.csv](../../target/feature008-evidence/BASE-20261021T172512676/method-audit.csv) relaciona cada classe/método e quantidade esperada/observada; [invocations.csv](../../target/feature008-evidence/BASE-20261021T172512676/invocations.csv) conserva as391 identidades de invocação; [suite-audit.csv](../../target/feature008-evidence/BASE-20261021T172512676/suite-audit.csv) aponta os XMLs/TXTs específicos. As contagens parametrizadas conferidas incluem: agregado3+3; DTO4/9/19/2/4/9/57/4; perfis4; lista2/28/9/50/20 por contexto e detalhe2/2 por contexto. Os demais métodos executaram uma vez por classe. A lista combina25 variantes de aliases com dois modos, e quatro queries inválidas com cinco variantes combinadas; dinheiro inválido no detalhe DTO combina três campos com19 entradas. Nenhuma omissão, classe extra, método extra ou duplicata de classe/método/invocação foi encontrada. A execução dos mesmos métodos em ROOT e Api corresponde às duas classes exigidas, sem contar seus suportes abstratos outra vez.

O console registra contextos HTTP com H2s distintos: detalhe /api porta65519 e ROOT65521; lista /api65523 e ROOT49192; todos com mockMvcDispatches=0. Para detalhe, cada contexto registrou9 logins,13 requests de lista e14 de detalhe; esses requests não são uma contagem alternativa de testes JUnit. O teste de perfis registrou leitura escalar CLIENT com entidade gerenciada ainda ADMIN, nome pendente preservado e dml=0 na janela observada. Essas são provas dos testes existentes, sem extrapolação para pagamento008.

A conferência automatizada somente de leitura dos resultados/fontes foi executada com `& 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 target/feature008-evidence/BASE-20261021T172512676/audit_t002.py`; resultado final exit0, [audit.json](../../target/feature008-evidence/BASE-20261021T172512676/audit.json) PASS. A primeira leitura desse auxiliar retornou exit1 por UnicodeDecodeError ao assumir UTF-8 para console.log; Tee-Object no PowerShell5.1 gravou UTF-16LE. Corrigida somente a decodificação do auxiliar, a conferência passou sem repetir Maven nem modificar testes/produção.

### Diagnóstico, limites e encerramento

Não houve falha de teste a corrigir nem RED artificial de regressão existente. O console contém avisos de depreciação de SecurityManager na compilação, H2Dialect explícito, carregamento dinâmico de agente Byte Buddy e compartilhamento de classes da JVM. O stderr do agente aparece formatado pelo PowerShell como RemoteException na pipeline; o término Maven e o exit0, confirmados pelos relatórios, distinguem esse aviso de falha de teste. Nenhuma asserção, fonte, perfil, seed ou configuração global foi alterada para ocultá-los.

Esta BASE cobre histórico do agregado, representações atuais, perfis efetivos e consultas007 ROOT/Api na revisão indicada. Não comprova POST008, PAID, Payment/schema, parser temporal008, locks de pagamento, atomicidade/commit/transporte008 ou os gates V01–V10. O contrato007 continua vigente com WAITING_PAYMENT; os dois contratos008 permanecem alvos planned. Não foi executada suíte completa, CI ou validação Linux nesta T002; FINAL permanece em T034.

Critérios da T002 atendidos. Somente seu marcador foi acrescentado aos concluídos; T001 preservada, T003–T036 abertas: **2 concluídas e34 abertas**. Alterações versionáveis desta tarefa limitadas a esta seção nova e à checkbox T002, preservando as alterações iniciais. Sem stage, commit, push, merge, troca de branch ou alteração de main. A revisão de diff, referências e whitespace e o estado final do Git são conferidos no fechamento desta execução.

Fechamento efetivamente conferido: diff e seção nova revisados;12 links locais da seção resolvidos,0 ausentes; `git diff --check` exit0. O auxiliar verify_docs.py retornou exit0 e gravou docs-audit.json no diretório da execução, confirmando o prefixo inicial do quickstart preservado byte a byte e tasks.md diferente do inicial somente pelo marcador T002. Git final: apenas ` M specs/008-registrar-pagamento/quickstart.md` e ` M specs/008-registrar-pagamento/tasks.md`, nenhum staged ou não rastreado; branch, HEAD, main e hash da seleção iguais aos iniciais. Sem Maven/Surefire ativo após a execução. `.specify/extensions.yml` ausente também na conferência posterior: nenhum hook after_implement registrado.

## T003 — Preparação do suporte de testes em 2026-10-21

**Escopo realizado**: somente a escrita do suporte previsto em [T003](tasks.md), dependente da T002 concluída no checkpoint `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`. Esta seção é acrescentada ao registro existente; T001, os limites do inventário opcional interrompido e todas as evidências da BASE/T002 permanecem históricos intactos. Não é uma execução de teste nem comprovação funcional da 008.

### Conferências iniciais e fronteira da tarefa

`git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short --untracked-files=all` confirmaram, respectivamente, `C:/Users/ronal/dscommerce-speckit`, `008-registrar-pagamento`, `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279` e saída vazia. `.specify/feature.json` seleciona `specs/008-registrar-pagamento`. O comando `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0 e o diretório absoluto da 008 com os artefatos esperados. Checklist requirements.md: 16 itens, 16 marcados, nenhum aberto; PASS documental, sem alterar marcadores.

Foram considerados T003 integral, a dependência T002, o planejamento e a disciplina das tarefas, plano, pesquisa R2–R3/R5–R9, modelo, contratos e procedimentos pertinentes deste quickstart, sob a constituição e o harness. A T003 pede preparar o suporte; os primeiros cenários e seleções executáveis estão previstos em T006–T007. Por isso esta entrega não executa Maven, javac, testes, servidor ou validação OpenAPI. Também não cria cenários artificiais para obter GREEN, fixtures de T004 ou observadores/failpoints de T005+. `.specify/extensions.yml` ausente na conferência anterior; nenhum hook before_implement registrado. O ignore Java existente cobre target/; não houve ajuste de infraestrutura.

### Suporte escrito e uso futuro

| Arquivo novo | Preparação realizada, ainda sem execução |
| --- | --- |
| [PaymentRegistrationTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java) | @TestConfiguration com perfil test e import explícito. Anotações compostas RootHttpContext e ApiHttpContext selecionam a aplicação real, RANDOM_PORT, ROOT ou /api, OSIV desativado e identidade feature008 própria na chave de contexto. |
| [PaymentRegistrationHttpTestSupport.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java) | Base abstrata sem métodos @Test: transporte HTTP, login/JWT reais, ciclo de vida do Clock e asserções para conexões, proxy e transação a serem exercitadas posteriormente. |

As duas variantes reutilizam [AdminProductTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductTestConfig.java) sem alterá-lo: UUID/URL H2 e segredo JWT descartável gerados uma vez por ApplicationContext, SQL init desativado, import.sql desativado e create-drop local. As propriedades ROOT/Api e a identidade feature008 distinguem os contextos da 008 entre si e dos históricos; cada novo contexto recebe sua URL, estável nas leituras e conexões daquele contexto. O prefixo `admin-product-test-` é conservado deliberadamente para compatibilidade com a composição posterior da fixture007, sem reutilizar a URL fixa do perfil global. DB_CLOSE_DELAY não é tratado como proteção contra DROP de outro contexto.

O bean @Primary reutiliza somente [OrderTestConfig.ControlledClock](../../src/test/java/com/devsuperior/dscommerce/config/OrderTestConfig.java), inicialmente `2030-01-01T12:00:00.123Z`. Não importa OrderTestConfig como configuração nem instala sua fixture de pedidos. O Clock permite setInstant/advance e conserva precisão nativa; é restaurado antes de cada cenário futuro e no fechamento. Não há contador de referência de negócio nesta etapa. O JWT existente continua emitido/validado pelos beans reais de produção; atualmente JwtTokenService usa Instant.now, não o Clock injetado de negócio. Nenhum decoder, encoder, filtro ou serviço foi substituído.

Uso futuro: cada classe concreta de HTTP deverá estender o suporte, aplicar explicitamente `@PaymentRegistrationTestConfig.RootHttpContext` ou `@PaymentRegistrationTestConfig.ApiHttpContext` e declarar expectedContextPath como vazio ou `/api`, respectivamente. O suporte verificará identidade/path, porta, URL H2, perfil de inicialização e isolamento observado. O helper realHttpLogin recebe ID/nome/senha de uma conta já commitada pela fixture do cenário, envia POST /login pela rede local, exige 200/Bearer e verifica o subject pelo JwtDecoder real. Não cria contas, não usa o login MockMvc da fixture histórica e não registra credenciais. Cada cenário deve assertar seu próprio status/corpo; o helper send retorna a resposta real e propaga falhas de transporte, sem convertê-las em sucesso.

O cliente usa timeout de conexão de 10 segundos, requests JSON com timeout de 30 segundos e nenhum redirecionamento automático. É fechado em @AfterAll, inclusive após falha de preparação; o finally restaura o Clock. Conexões do setup são encerradas por try-with-resources. openIndependentConnection usa DriverManager para uma conexão física separada, fecha-a se a própria preparação falhar e exige que o chamador a encerre com try-with-resources quando retornada. Não há linhas próprias criadas nesta T003 para apagar; a limpeza seletiva Payment→itens→pedidos→demais linhas e o probe de oito tabelas continuam em T004, sem alteração das fixtures/probes históricos.

### Asserções preparadas e limites da observação

- assertPaymentConnection compara a URL real via metadata e exige Connection.TRANSACTION_READ_COMMITTED em conexões pooled, independentes ou de negócio observadas; não muda o isolamento para fazer a asserção passar.
- assertNoExternalTransaction exige ausência de transação, sincronização e recurso DataSource vinculado na thread corrente. Antes/depois de send, verifica somente a thread do chamador de teste. Para a entrada do request, a instrumentação futura precisa invocá-la na thread do servidor antes do interceptor transacional.
- requirePaymentServiceProxy busca obrigatoriamente o futuro bean paymentRegistrationService, exige a classe-alvo real, proxy AOP/Advised e TransactionInterceptor. Não é chamado no setup da fundação; quando usado, serviço ausente causa falha, sem lookup opcional, bean falso ou teste omitido. A forma do proxy não comprova transação física ou commit.
- assertOwnedPaymentTransaction deverá ser chamada pela observação futura dentro do interceptor na thread do request: exige transação/sincronização ativas, isNewTransaction, conexão vinculada ao DataSource, autoCommit=false e READ_COMMITTED. Não foi instalada nem exercitada instrumentação nesta etapa; retorno do proxy e commit ainda precisam das provas posteriores.

Esses métodos são preparação de asserções, não resultados observados de banco/HTTP. Nenhuma conexão foi aberta, nenhum contexto Spring foi iniciado, nenhum login ou POST de pagamento foi enviado por esta execução da T003. Não há mocks, persistência exercitada, MockMvc ou HTTP real executados nesta entrega. Não se inferem isolamento efetivo, schema Payment, segurança, transação dona, commit, rollback, concorrência ou gates V01–V10 somente pela autoria do suporte.

### Revisão e conclusão

Revisão estática dos arquivos novos e suas referências às APIs já usadas no projeto. Comandos de whitespace executados: `git diff --check` sem diagnósticos, exit0; `git diff --no-index --check -- /dev/null <cada arquivo Java novo>` sem diagnósticos de whitespace, exit1 em cada chamada por serem arquivos novos diferentes de /dev/null. Os avisos de conversão futura LF→CRLF são da configuração Git existente. Compilação e testes não foram executados nesta tarefa de escrita; não há novo resultado Surefire, contagem JUnit ou console Maven a conciliar. A BASE de 391 testes em sete classes permanece a evidência anterior da T002, sem repetição ou aprovação retroativa destes arquivos novos.

Critérios de preparação da T003 atendidos, sem impedimento de escrita; somente T003 é acrescentada às concluídas. T001–T002 preservadas, T004–T036 abertas: **3 concluídas e 33 abertas**. Alterações limitadas aos dois suportes de teste novos, a esta seção e à checkbox T003. Produção, testes/asserções preexistentes, perfil/seed globais, contratos, seleção local e relatórios anteriores permanecem preservados. Não houve inventário global de target/, stage, commit, push, merge ou alteração de main. A conferência final de referências, preservação do prefixo, diff, arquivos novos, whitespace e Git é registrada no fechamento abaixo.

Fechamento observado: diff completo e conteúdo dos dois Java novos revisados. Conferência documental via script em stdin com `& 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 -`, exit0: prefixo de 32.753 bytes do quickstart igual ao HEAD, tasks.md alterado somente em T003, cinco links locais novos existentes, três tarefas concluídas e 33 abertas, nenhum método @Test/@ParameterizedTest/@Nested/@Disabled novo e whitespace sem diagnósticos. Git final com quickstart.md/tasks.md modificados e somente os dois Java previstos não rastreados; índice vazio. Branch/HEAD/main e seleção 008 preservados. `.specify/extensions.yml` ausente também no fechamento; nenhum hook after_implement registrado. Nenhum resultado de execução da aplicação é alegado por essa revisão documental.

## T004 — Preparação de fixture e probe em 2026-10-21

**Escopo realizado**: exclusivamente a escrita prevista em [T004](tasks.md), dependente da BASE/T002 já registrada: 391 testes em sete classes, zero falhas/erros/ignorados e exit0. Essa evidência anterior não aprova os suportes novos. **Os dois arquivos recebidos da T003 continuam sem compilação ou execução**, preservados integralmente; T001–T003 e seus limites permanecem como prefixo deste quickstart. Não se declara comportamento funcional da 008 por esta preparação.

### Estado inicial e fontes

`git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short --untracked-files=all` e `git diff --cached --name-only` confirmaram a raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, somente quickstart.md/tasks.md da 008 modificados, os dois Java da T003 não rastreados e nada staged. `.specify/feature.json` continua selecionando a 008. O script `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0, FEATURE_DIR absoluto da 008 e os artefatos esperados. Checklist requirements.md: 16/16 marcados, zero abertos, PASS documental; marcadores preservados.

Foram lidos T004 integral e sua dependência, as restrições de progressão até T007/T008 e os artefatos pertinentes: plano, pesquisa R8/R9, modelo, contratos e procedimentos de preparação/observação deste quickstart. A composição considerou as implementações existentes da fixture/probe007, da fixture de catálogo e do inicializador H2, além do suporte recebido da T003. Constituição e harness permanecem as fontes de governança. `.gitignore` Java já cobre target/; nenhuma mudança de ignore ou infraestrutura. `.specify/extensions.yml` ausente na conferência anterior; nenhum hook before_implement registrado.

### Arquivos escritos e procedimentos futuros

| Arquivo novo | Preparação realizada, sem execução |
| --- | --- |
| [PaymentRegistrationTestFixture.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java) | AutoCloseable, sem inserts no construtor. Reutiliza contas/perfis/catálogo da fixture aprovada e os manifests da 007; prepara pedidos completos WAITING_PAYMENT, rastreia IDs próprios e acrescenta a limpeza de Payment antes dos demais dados. |
| [PaymentRegistrationDatabaseProbe.java](../../src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java) | Compõe [OwnOrderQueryDatabaseProbe](../../src/test/java/com/devsuperior/dscommerce/fixtures/OwnOrderQueryDatabaseProbe.java), acrescentando leitura obrigatória e comparação de tb_payment. Nenhuma entidade produtiva futura, schema artificial ou observador de T005 é instalado. |

A fixture reutiliza [AdminProductTestFixture](../../src/test/java/com/devsuperior/dscommerce/fixtures/AdminProductTestFixture.java) sem alterá-la, incluindo suas verificações de perfil/inicializador/URL H2 exclusiva e seu setup REQUIRES_NEW de contas/perfis e catálogo. O novo código rejeita transação/sincronização externa ao setup/cleanup. A criação do pedido usa sua própria transação REQUIRES_NEW/READ_COMMITTED; só registra os IDs do pedido e dos itens depois do retorno commitado. Comprador e produtos precisam pertencer à fixture. Quantidades, preços históricos, subtotais e total esperados vêm do cenário, sem derivação pelo serviço/mapper de produção ou preço atual. Não é criado pagamento de setup nem alterado o estado inicial para PAID.

Uso futuro: atribuir a fixture antes de iniciar a preparação e mantê-la alcançável por finally, @AfterEach ou @AfterAll; em teste local, também pode ser encerrada por try-with-resources. Criar contas/perfis e pedido antes do request, aguardar o término de todos os escritores e fotografar antes da limpeza. O login HTTP real continua no suporte da T003; esta fixture não expõe nem executa o login MockMvc da fixture interna. Construir esse suporte histórico instala seus objetos de apoio, mas não dispara requests. Nenhuma preparação descrita neste parágrafo foi executada nesta T004.

A limpeza escrita usa DELETEs parametrizados na ordem Payment→itens→pedidos→vínculos de perfil das contas próprias, depois delega produtos/categorias/demais linhas próprias à fixture aprovada. Os pagamentos são removidos pelos IDs dos pedidos rastreados, inclusive se forem criados posteriormente pelo endpoint; itens usam ID e order_id. Vínculos de perfil usam somente IDs de contas próprias para tolerar concessões/revogações feitas pelo cenário, sem apagar vínculos de outras contas. Não há TRUNCATE, limpeza de tabela inteira ou exclusão de compras de outro proprietário de fixture.

Payment, itens, pedidos e vínculos de perfil são limpos na primeira transação. Os IDs dos pedidos só são liberados após seu commit; falha nessa fase conserva o rastreamento para nova tentativa. A limpeza de catálogo é uma fase commitada separada e conserva seu próprio rastreamento em caso de falha; não se promete atomicidade entre as duas fases. Contas/produtos do wrapper só são liberados depois do sucesso dessa delegação. As exceções propagam; limpeza não transforma falha de cenário em aprovação. ResultSets/Statements de metadata e leitura usam try-with-resources; JdbcTemplate/TransactionTemplate gerenciam as conexões vinculadas.

### Schema ausente, snapshots e limites

A única tolerância à ausência de tb_payment está no método privado paymentTableExistsForCleanup: consulta metadata PUBLIC/TB_PAYMENT durante a limpeza, antes de T008 disponibilizar a tabela. Ausência permite remover o restante dos dados próprios anteriores ao schema. Erro de metadata ou DELETE quando a tabela existe propaga, sem fallback silencioso. O código não cria DDL para fazer testes passarem.

O snapshot funcional sempre executa `select order_id, occurred_at from tb_payment order by order_id`. Se a tabela/colunas estiverem ausentes ou não puderem ser lidas, lança IllegalStateException explícita exigindo o schema real, sem retornar lista vazia ou omitir Payment. Lista vazia só é resultado válido da consulta executada com sucesso sobre tabela existente. A ocorrência é lida como OffsetDateTime e convertida a Instant, sem arredondamento. O snapshot contém listas imutáveis e inclui o snapshot007 integral, com contas/perfis e as sete tabelas anteriores. Credenciais permanecem opacas no probe007; toString expõe somente contagens e redação de credenciais, e os diagnósticos de comparação identificam tabelas, sem valores de conta, hashes ou causa SQL sensível.

As conexões do probe são físicas e independentes, delegadas ao mecanismo aprovado. A nova leitura Payment exige READ_COMMITTED observado e tem commit/rollback e fechamento; o probe007 conserva suas próprias transações de leitura REPEATABLE_READ. A composição usa várias leituras/conexões: **não é um snapshot global atômico sob escritores concorrentes**. Chamadores precisam encerrar os escritores e manter setup/login/limpeza fora da janela. Fotografias iguais também não provam ausência de toda mutação transitória revertida. Não foi acrescentada observação SQL de Hibernate nem observação de JDBC/threads de T005.

No primeiro RED de rota, o cenário futuro deve assertar o resultado HTTP esperado antes de solicitar o snapshot que exige oito tabelas. Ausência de tb_payment não pode substituir a asserção da operação ausente por falha de preparação, nem ser aceita como ausência funcional comprovada por lista vazia. A sensibilidade do probe a mudanças de Payment e das sete tabelas anteriores, e a limpeza após falha de teste, permanecem provas de T007/T013; a escrita dos métodos não as executa.

### Validações e conclusão

Revisão estática de conteúdo, APIs e referências dos dois arquivos novos. `git diff --check` retornou exit0 sem diagnósticos de whitespace; `git diff --no-index --check -- /dev/null <cada Java da T004>` retornou exit1 em cada chamada, sem diagnóstico de whitespace, por comparar arquivo novo com /dev/null. Os avisos LF→CRLF decorrem da configuração Git existente. Foram registrados hashes dos dois Java da T003 para conferir sua preservação, sem inventário global de target/.

Não houve Maven, javac, compilação, servidor, persistência executada, mocks, MockMvc, HTTP real ou novo relatório Surefire. Não se fabricou RED nem se classificou preparação/compilação como falha funcional. A T004 é de escrita; as execuções previstas começam nos cenários posteriores. T003 e T004 continuam sem comprovação executável, com os limites explicitados, e a BASE não foi repetida. Produção, testes/asserções históricos, perfil/seed global, contratos e relatórios anteriores permanecem intactos.

Critérios de autoria da T004 atendidos, sem impedimento de escrita. Somente T004 acrescentada às concluídas nesta execução; T001–T003 preservadas e T005–T036 abertas: **4 concluídas e 32 abertas**. Alterações desta tarefa limitadas aos dois Java novos acima, à seção acrescentada neste quickstart e à checkbox T004. Nada de T005+ foi executado. Sem stage, commit, push, merge ou alteração de main; estado final, preservações e referências são conferidos no fechamento abaixo.

Fechamento observado: diff e arquivos novos revisados. A conferência documental em stdin por `& 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 -` retornou exit0: prefixo inicial de 42.438 bytes do quickstart preservado por SHA-256, dois Java da T003 com hashes iniciais idênticos, tasks.md diferente do recebido somente pelo marcador T004, cinco links novos existentes, quatro tarefas concluídas e 32 abertas, nenhum método de teste executável acrescentado e whitespace sem diagnósticos. Git final: quickstart.md/tasks.md modificados; quatro Java não rastreados, sendo os dois recebidos da T003 e os dois criados nesta T004; nada staged. HEAD, branch, main e seleção 008 preservados. `.specify/extensions.yml` ausente também na conferência posterior: nenhum hook after_implement registrado. Compilação e execução dos suportes da T003/T004 continuam pendentes das etapas aprovadas, sem nova prova funcional.

## T005 — Preparação de observação e coordenação em 2026-10-21

**Escopo realizado**: somente a escrita prevista em [T005](tasks.md), dependente de T003–T004. Os quatro suportes recebidos permanecem intactos e **ainda não foram compilados ou executados**. A BASE/T002 continua sendo evidência anterior de 391 testes em sete classes, zero falhas/erros/ignorados e exit0; não aprova estes suportes novos. Esta seção preserva o prefixo T001–T004 e não afirma entrega funcional da 008.

### Estado inicial e fontes

`git rev-parse --show-toplevel`, `git branch --show-current` e `git rev-parse HEAD` confirmaram `C:/Users/ronal/dscommerce-speckit`, `008-registrar-pagamento` e `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`. `git status --short --untracked-files=all` mostrou somente quickstart.md/tasks.md da 008 modificados e estes quatro Java não rastreados, com caminhos completos conferidos:

- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java`

`git diff --cached --name-only` teve saída vazia. Seleção local em `.specify/feature.json`: Feature 008. O comando `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0, FEATURE_DIR absoluto da 008 e os documentos esperados. Checklist requirements.md: 16/16 marcados, zero abertos, PASS documental, sem alterar marcadores. `.specify/extensions.yml` ausente antes da escrita; nenhum hook before_implement registrado. Ignore Java existente adequado, sem alteração.

Foram considerados T005 integral, suas dependências T003/T004, os limites e seleções de tarefas, plano, pesquisa R3/R5/R6/R8/R9, modelo, contratos e procedimentos pertinentes deste quickstart, sob a constituição e o harness. Foram lidas as referências existentes de observação/falhas 005/007 e o Clock controlável aprovado. T005 prepara a instrumentação; os cenários e seleções executáveis pertencem a T006+. Não se executou Maven, javac, servidor, teste ou validação OpenAPI, nem se iniciou inventário global de target/.

### Configuração escrita e ativação futura

Novo arquivo: [PaymentRegistrationObservationConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java), @TestConfiguration com perfil test e ativação somente por import explícito. O consumidor futuro deverá acrescentar `@Import(PaymentRegistrationObservationConfig.class)` ao contexto isolado que já seleciona [PaymentRegistrationTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java). Não foram alterados esses suportes recebidos, configurações produtivas, perfil/seed globais, filtros de segurança, repositórios ou serviços. Nenhum bean de pagamento foi criado para simular uma implementação ausente.

A configuração mantém o ControlledClock original, inclusive sua injeção concreta no suporte T003 e seus métodos setInstant/advance. Somente no contexto que importar esta configuração, um BeanFactoryPostProcessor retira o atributo primary do bean de teste paymentTestClock; um novo Clock @Primary delega ao mesmo objeto controlável e observa o valor exato de instant(), sem nova leitura, truncamento ou arredondamento. Isso não altera o arquivo da T003 nem o bean produtivo OrderClockConfig. O contador só avança durante o corpo do serviço de pagamento observado na thread do request; chamadas de filtro/JWT, fixture e outros serviços fora desse corpo não contam como referência de negócio. Atualmente JwtTokenService usa Instant.now, mas mesmo uma chamada ao Clock na fase de filtro ficaria fora dessa contagem.

Uma propriedade apenas desse contexto configura `spring.datasource.hikari.connection-init-sql=set lock_timeout 20000` para as novas conexões Hikari do H2 isolado. Configuração de init SQL conflitante é rejeitada. URL, segredo, seed, create-drop e os contextos históricos não são alterados; não se usa DB_CLOSE_DELAY como garantia contra DROP alheio. A aplicação efetiva dessa configuração ainda não foi executada nem verificada em banco.

### Janelas, SQL e proxy real

Uso futuro: concluir setup/login, abrir `observation.openWindow(Duration)` em try-with-resources, criar um Attempt por `window.newRequest()` e marcar somente o request desejado com `X-Feature008-Observation: attempt.id()`. Não registrar esse identificador, credenciais ou JWTs em logs. Um filtro de teste associa a tentativa à thread do request e remove o ThreadLocal em finally; também sinaliza o fim da passagem pelo filtro. Requests não marcados ou fora da janela continuam pela cadeia normal sem serem capturados. Uma tentativa não pode ser reutilizada para dois requests.

O StatementInspector retorna o SQL sem modificá-lo e registra somente SQL de requests marcados, com thread, estado transacional e fase OUTSIDE_SERVICE, SERVICE_BODY ou PROXY_BOUNDARY. Não observa bind values nem JDBC direto, inclusive as conexões auxiliares. O toString dos eventos não imprime SQL e o da tentativa não imprime resultado. Conflito já presente na propriedade do inspector é rejeitado em vez de substituir silenciosamente outro observador. O isolamento entre observadores e a passagem dos requests ainda precisam de execução nos contextos concretos.

O BeanPostProcessor seleciona somente paymentRegistrationService, exige a classe-alvo planejada e o proxy Spring/Advised real com um TransactionInterceptor único. Acrescenta observação imediatamente antes e depois desse interceptor original; não cria proxy substituto nem chama o alvo diretamente. Na thread do servidor, registra ausência de transação/sincronização/recurso DataSource externo na entrada/saída, entrada no corpo, transação ativa e nova, readOnly, conexão efetivamente vinculada, autoCommit e isolamento JDBC. A conexão vinculada é obtida/liberada por DataSourceUtils; sua obtenção pode antecipar a aquisição física durante a instrumentação, antes da primeira instrução do corpo. Isso não mede latência de pool nem prova ausência de espera de infraestrutura.

Retorno e falha do proxy são registrados separadamente, sem guardar DTO ou exceção sensível. `attempt.assertOwnedTransactionObserved()` exige serviço instalado, uma entrada de proxy/corpo, término do proxy, ausência externa, transação dona READ_COMMITTED, conexão vinculada/autoCommit=false e exatamente uma leitura do Clock com referência não nula. Serviço ausente, nenhuma entrada ou nenhuma leitura não viram PASS. A forma do proxy, um retorno ou o encerramento do filtro **não são prova isolada de commit efetivo ou entrega da resposta**; essas provas permanecem posteriores.

Abrir uma janela não exige antecipadamente o serviço ainda inexistente, permitindo que o futuro primeiro RED compare o status HTTP da rota. O cenário deverá assertar primeiro o resultado HTTP esperado e só então as provas específicas de serviço/schema aplicáveis. Espera por fase não alcançada falha por timeout, e a asserção transacional falha explicitamente se o serviço não existir. Nenhuma seleção vazia ou observação sem invocação é tomada como comprovação funcional.

### Coordenação e recursos independentes

Cada tentativa pode instalar um Gate, antes de submeter o request, em PROXY_ENTRY, BODY_ENTRY, REFERENCE_READ, ORDER_LOCK_SQL ou PROXY_RETURN. A API usa CountDownLatch para awaitReached/release; não há sleeps arbitrários. Em REFERENCE_READ, o valor já foi lido do Clock e será devolvido sem recaptura após a liberação; não se afirma que a variável local do serviço já tenha sido atribuída durante a pausa. ORDER_LOCK_SQL é reconhecimento de `tb_order ... for update` no inspector **antes da execução**: não confirma aquisição, espera física ou lock retido. PROXY_RETURN é posterior ao retorno do interceptor original; timeout nessa fase não autoriza inferir rollback de um possível commit já efetivado.

Uma janela tem um proprietário para seu ciclo de vida e permite múltiplos requests/trabalhos concorrentes por executor de virtual threads Java 21. A restrição de uma janela ativa por observador é apenas controle do teste; não é mutex de negócio nem serializa os requests dentro dela. O estado é de instância/contexto, sem registradores estáticos mutáveis ou mudança em outros contextos. O escopo preparado acompanha a thread servlet síncrona; não promete propagação automática a processamento assíncrono da aplicação.

`window.independentTransaction(probe)` reutiliza [PaymentRegistrationDatabaseProbe](../../src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java) para uma conexão física DriverManager, fora do pool e da transação vinculada da aplicação. Exige ausência de transação/sincronização no chamador e READ_COMMITTED observado; configura somente essa sessão H2 com lock_timeout=10000, autoCommit=false e statements preparados com queryTimeout de 10 segundos. Não monta SQL de pagamento/lock produtivo nem executa DDL. Chamadores futuros devem fechar statements/resultsets por try-with-resources e efetivar ou reverter explicitamente sua preparação auxiliar; close reverte a transação ainda pendente e fecha a conexão em finally.

Timeouts de gates, resultado de Future e conclusão de request usam a duração positiva da janela, limitada a 10 segundos; a espera de término de workers pode ter duas tentativas limitadas. `awaitResult` propaga falhas do trabalho e cancela por timeout. O fechamento libera gates e conexões/locks auxiliares antes de aguardar trabalhos, interrompe/cancela se necessário, confere também exceções dos Futures e término do filtro e desarma a janela em finally. Erros de limpeza são propagados/agregados, sem convertê-los em resultado aprovado. Fechar a janela antes da fixture, preferencialmente depois de aguardar explicitamente todos os requests; se faltar término, a limpeza falha em vez de presumir sucesso. Esses limites são de instrumentação/teste, não SLA ou garantia de concorrência da aplicação.

### Validações e conclusão

Conteúdo do Java novo e suas referências revistos estaticamente. `git diff --check` retornou exit0 sem diagnóstico de whitespace; `git diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java` retornou exit1, sem diagnóstico de whitespace, por ser arquivo novo diferente de /dev/null. Avisos LF→CRLF são da configuração Git existente. Os quatro arquivos recebidos tiveram comprimento/SHA-256 registrados para a conferência de preservação, sem varrer target/.

**Sem compilação ou execução de T003–T005 nesta tarefa de escrita.** Nenhum contexto Spring, Clock decorado, filtro, SQL, transação, gate, worker, conexão, MockMvc ou HTTP foi exercitado. Não há console Maven/Surefire novo nem casos aninhados executados a conciliar; as classes aninhadas do novo arquivo são suporte, não cenários JUnit. Revisão estática não aprova bootstrap, wiring AOP/Clock/Hikari, precisão, ausência de vazamento, locks, commit, rollback ou segurança. Eventuais erros futuros de compilação/preparação deverão ser classificados separadamente de RED funcional, sem enfraquecer asserções. Baseline e suíte completa não foram repetidas; relatórios anteriores preservados.

Critérios de autoria da T005 atendidos, sem impedimento de escrita. Somente T005 acrescentada às concluídas nesta execução: **T001–T005 concluídas, T006–T036 abertas (5/31)**. Não foram escritos cenários T006+, modelo, serviço ou endpoint de pagamento, nem os failpoints de T023. Alterações limitadas ao Java novo acima, à seção acrescentada neste quickstart e à checkbox T005; quatro Java recebidos e evidências anteriores preservados. Sem stage, commit, push, merge ou alteração de main. Referências, diff, arquivos novos, preservações e estado final do Git são conferidos no fechamento abaixo.

Fechamento observado: revisão estática e conferência documental via stdin com `& 'C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe' -B -I -X utf8 -`, exit0. Prefixo inicial de 52.852 bytes do quickstart preservado por SHA-256; quatro Java recebidos com hashes iniciais idênticos; tasks.md diferente do recebido somente pela checkbox T005; quatro links novos existentes; cinco tarefas concluídas e 31 abertas; nenhum método @Test/@ParameterizedTest/@Nested/@Disabled acrescentado; whitespace sem diagnósticos. Diff e conteúdo completo do arquivo novo revisados. Git final: dois documentos modificados, cinco Java não rastreados (quatro recebidos e somente PaymentRegistrationObservationConfig.java novo nesta tarefa), índice vazio; HEAD/branch/main e seleção 008 preservados. `.specify/extensions.yml` ausente também no fechamento, sem hook after_implement registrado. Sem nova evidência executável de aplicação, compilação, Maven ou Surefire.

## Execução T006 — testes HTTP US1 e S1-RED (2026-10-21)

### Estado recebido, escopo e preservação

Uso de `speckit-implement` limitado à T006. Conferência inicial: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279` e seleção local Feature 008 em `.specify/feature.json`. Somente este quickstart e tasks.md estavam modificados; índice vazio. Os cinco Java não rastreados recebidos eram:

- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java`
- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java`

As execuções T003–T005 preservam seu alcance histórico de **autoria e revisão estática, sem compilação ou execução naquela ocasião**. A compilação e o uso parcial desses suportes descritos abaixo são evidência nova, exclusivamente desta T006, sem aprovação retroativa de todas as suas capacidades. Os cinco arquivos recebidos foram preservados sem alterações; comprimentos e SHA-256 iniciais registrados para conferência final. T001–T002 e seus limites, inclusive o inventário opcional incompleto da T001, permanecem intactos. A BASE histórica de 391 testes em sete classes, exit0, não foi repetida e não comprova a funcionalidade008.

Foram lidos T006 integralmente, dependências T003–T005, condições de S1-RED, spec/US1, plano, pesquisa, modelo e contrato POST008 pertinentes, sob constituição, AGENTS.md e harness. Pré-requisitos do Spec Kit confirmaram o diretório absoluto008; requirements.md permaneceu 16/16 marcado, zero aberto, PASS documental. Nenhum hook registrado: `.specify/extensions.yml` ausente antes da execução. Ignore Java adequado, sem alteração. Antes do Maven, a consulta de processos Java/Maven/Surefire não encontrou execução ativa; não havia evidência de execução anterior T006 no diretório de evidências. Não se refez inventário global de target/.

### Testes escritos e fronteiras de prova

Novos arquivos [PaymentRegistrationHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java) e [PaymentRegistrationApiHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationApiHttpIntegrationTest.java). O suporte de cenários está no primeiro arquivo, com herança dos mesmos 11 métodos em cada classe concreta, sem @Nested, @Disabled, mocks de pagamento ou import de tipos produtivos ainda inexistentes. Parâmetros expandem cada classe para 16 invocações. A matriz abaixo foi conciliada com os testcase XML, não inferida somente pelo seletor.

| Cenário / fronteira | Método herdado em ROOT e Api | Invocações por contexto |
| --- | --- | --- |
| US1.1: ADMIN isolado paga pedido alheio, histórico42,50 | us11AdminSettlesForeignOrderWithExactlyThreeConfirmationFieldsAndHistorical4250 | 1 |
| US1.2: próprio/alheio, ADMIN sem CLIENT e ambos os perfis | us12OwnAdminWithoutClientAndBothProfilesUseTheSameSettlementRules | 3: ADMIN/próprio, BOTH/próprio, BOTH/alheio |
| US1.3: preço atual12 não substitui histórico20 | us13CatalogPriceTwelveDoesNotReplaceHistoricalTwentyOrOrderData | 1 |
| US1.4: ocorrência anterior à referência, offset+03:00 e nove dígitos exatos em ms | us14EquivalentOffsetWithExtraZerosPreservesOccurrenceRatherThanBusinessReference | 1 |
| US1.5: igualdade à criação | us15OccurrenceEqualToCreationIsAccepted | 1 |
| US1.6: igualdade à referência inicial, avanço posterior do Clock | us16OccurrenceEqualToInitialReferenceSurvivesClockAdvanceBeforeCompletion | 1 |
| Credencial ausente e Bearer malformado:401 | basicAuthenticationRejectsMissingAndMalformedBearerWithSafePaymentEnvelope | 2 |
| CLIENT e conta sem perfil:403 | basicAuthorizationRejectsClientAndNoProfileWithoutDisclosingOrderData | 2 |
| Campo amount adicional:400 | basicClosedRequestRejectsAnAttemptToSupplyAmount | 1 |
| Criação−1ms e referência+1ms:400 | basicWindowRejectsInstantsOutsideInclusiveBoundsWithoutChangingHistoricalTables | 2 |
| Nova tentativa por outro ADMIN após aceitação:409, sem substituir quitação | basicNewAttemptAfterAcceptanceReturns409AndPreservesTheFirstPayment | 1 |

As expectativas são 201 com exatamente orderId/occurredAt/status, número integral para o ID, PAID e UTC.SSS preservando o instante, sem Location/valor/comprador; rejeições exigem seu próprio status, envelope fechado de quatro campos, mensagem/path contratuais e Bearer somente no 401. Após um 201, o código exige observação do proxy real, transação dona READ_COMMITTED e única leitura da referência; probe JDBC estrito exige Payment e alteração exclusiva do status do pedido alvo, preservando catálogo, contas, perfis, pedidos e itens, inclusive montante histórico. A repetição exige 409 e snapshot de oito tabelas inalterado. **Essas asserções posteriores ao 201 estão escritas, mas não foram alcançadas neste RED.**

Setup usa fixtures commitadas e registros próprios, com canário de pedido não alvo; AfterEach fecha a fixture após as janelas, inclusive em falha. A ordem de limpeza recebida não foi alterada. Login e requisições usam HttpClient contra servidor real em porta aleatória; os controles GET administrativos existentes precisam retornar 200 com credenciais ADMIN/BOTH antes dos POSTs. A composição histórica da fixture constrói internamente suporte MockMvc, mas os logins/controles/POSTs desta T006 são HTTP real, sem perform de MockMvc nem stub do serviço008. Preparação JDBC e controles de login não equivalem a registro de pagamento.

Para compatibilizar JWTs reais emitidos pelo relógio de parede com o conversor existente, o cenário fixa o ControlledClock em Instant.now truncado a ms **após** os quatro logins e antes dos controles/POSTs; criação e ocorrência são anteriores a essa referência. Não se alterou o suporte recebido nem o relógio/configuração produtiva. O cenário US1.6 instala gate em REFERENCE_READ, avança o Clock somente se alcançar a fase e então aguarda o resultado. Sem serviço/rota, o gate expira em 10s; o teste recupera a resposta real e compara 201 antes de relatar o timeout como informação suprimida. Se houvesse 201 sem observação, a falha de instrumentação continuaria explícita. Não há sleep nem sucesso presumido por ausência de eventos.

Conforme a progressão aprovada, antes dos POSTs ainda não há snapshot que exija tb_payment inexistente: fotografias das rejeições básicas cobrem as sete tabelas históricas. Snapshot estrito de oito tabelas só ocorre após o 201 e na repetição subsequente. Completar as fotografias das rejeições após T008 continua pendente para S1-GREEN; não se usa metadata condicional para transformar Payment ausente em lista vazia. Nenhum 404 inicial aprova as guardas individuais, nenhum snapshot igual prova ausência de mutação transitória e nenhuma observação de H2 prova outro banco ou rede de produção.

### Comando e evidências da única execução

Identificador: `S1-RED-T006-20261021T182253894`. Ambiente: Windows/PowerShell, Java Zulu 21.0.12.1+1-LTS; Wrapper configurado para Maven 3.9.16. Metadados iniciados às 18:22:59−03:00; término às 18:25:22−03:00, em 2026-10-21; Maven informou duração 02:11. HEAD acima e diff não commitado recebido, acrescido dos dois testes novos. Nenhuma implementação produtiva foi modificada.

A linha S1-RED/T006–T007 manda selecionar classes somente quando escritas. Portanto, a execução T006 incluiu **exatamente as duas classes HTTP existentes**, sem antecipar os dois testes de persistência/probe pertencentes à T007:

```powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationHttpIntegrationTest,PaymentRegistrationApiHttpIntegrationTest" "-Dsurefire.reportNameSuffix=S1-RED-T006-20261021T182253894" test
```

Console capturado com Tee-Object, preservando o LASTEXITCODE imediatamente após a chamada; `exit.json` registra **1**. Diretório exclusivo ignorado pelo Git: `target/feature008-evidence/S1-RED-T006-20261021T182253894/`:

- `console.log`: console completo em UTF-16LE; `run.json` e `run-normalized.json`: comando, horário, ambiente, branch/HEAD; o segundo normaliza a serialização de propriedades PowerShell, sem substituir o original. `git-status-before.txt` e `tracked-diff-before.patch`: estado recebido.
- `previous-surefire/` e `previous-report-hashes.json`: cópia anterior e SHA-256 dos 232 arquivos diretamente em target/surefire-reports. É uma preservação proporcional de relatórios, não inventário global de target/ nem correção retroativa do inventário T001.
- `new-surefire/`: somente os quatro arquivos novos (XML+TXT de cada classe), com sufixo exclusivo; os mesmos resultados permanecem em target/surefire-reports, sem sobrescrever os anteriores.
- `reconcile.py` e `reconciliation.json`: conferência estática do console/XML, classes, identidades de testcase, multiplicidades, falhas e hashes dos relatórios. A primeira conferência identificou que o sufixo Surefire também integra name/classname no XML; a conferência foi corrigida para remover apenas esse sufixo conhecido, sem alterar testes ou reexecutar Maven. A execução final da conferência retornou exit0.

| Classe efetivamente executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentRegistrationApiHttpIntegrationTest | 16 | 16 | 0 | 0 |
| PaymentRegistrationHttpIntegrationTest | 16 | 16 | 0 | 0 |
| Total conciliado com console | **32** | **32** | **0** | **0** |

Os 11 métodos e suas multiplicidades acima constam uma vez por identidade de invocação em cada contexto; nenhuma classe adicional, omissão ou duplicata. Não há cenários @Nested previstos nestes dois arquivos. A reconciliação também confirmou que todos os 232 relatórios anteriores conservam SHA-256 idêntico. Foram gerados somente quatro relatórios com o novo sufixo. BASE e suíte completa não foram repetidas; nenhum outro Maven foi iniciado.

### Diagnóstico e alcance do RED

A compilação das 124 fontes de teste passou, inclusive dos cinco suportes recebidos e dos dois cenários novos. Os contextos ROOT e /api iniciaram com H2s exclusivos diferentes; testes passaram pela preparação de contas/pedidos, logins reais e controles 200. Não houve erro de compilação, bootstrap, fixture, SQL de tabela ausente ou limpeza substituindo a asserção HTTP. Avisos de depreciação SecurityManager, Byte Buddy/CDS e H2Dialect foram preservados no console; não foram classificados como falhas funcionais nem corrigidos fora do escopo.

Em cada contexto houve nove comparações 201→404 (incluindo a primeira tentativa do teste de repetição), três 400→404, duas 401→401 e duas 403→403. Total: **24 falhas pelo status 404 da operação ausente e oito pelo envelope 401/403 com chave extra errors**, contrário ao objeto fechado POST008. Nos oito últimos casos o status e os headers anteriores à comparação de campos passaram; a mensagem/path exatos ficam depois da asserção de campos e não foram comprovados. O diagnóstico não infere mensagens corretas nem prova que todas as regras de autenticação estejam atendidas.

Em US1.6, o timeout de gate aparece suprimido em cada contexto: sem entrada no serviço ausente, não houve leitura da referência de negócio. A falha primária registrada é 201 esperado versus 404 observado. Isso **não comprova** igualdade temporal sob espera, leitura única do Clock ou comportamento transacional. Do mesmo modo, não se alcançaram o 409 da nova tentativa,201 fechado, Payment persistido, PAID, precisão armazenada, snapshots de sucesso ou oito tabelas, commit/rollback, locks ou sensibilidade/limpeza do probe. O RED demonstra ausência da operação e incompatibilidade atual do envelope seguro; futuras execuções deverão isolar cada regra com a rota existente.

### Conclusão limitada à tarefa

T006 cumpriu autoria compilável, cobertura escrita e execução S1-RED funcional exigida; **não houve GREEN da Feature008**. Somente T006 é acrescentada às concluídas: **T001–T006 [X], T007–T036 abertas (6/30)**. T007 e implementação T008+ continuam pendentes. Não foi fabricada falha, modificado suporte recebido, alterada produção/teste histórico ou enfraquecida asserção para aprovação. Não há impedimento de compilação/preparação nesta execução; as lacunas funcionais observadas são esperadas para o incremento ainda não implementado.

Alterações desta T006 limitadas aos dois Java novos, a esta seção e à checkbox T006. Revisão final de conteúdo/diff, referências, links, whitespace e preservação dos arquivos recebidos registrada no fechamento. Nenhum stage, commit, push, merge ou alteração de main autorizado/executado.

Fechamento observado: conferencia estatica via stdin com o Python ja usado nas tarefas anteriores, exit0; `final-static-review.json` e `source-hashes.json` no diretorio desta execucao registram o resultado. Prefixo recebido de 66.141 bytes do quickstart preservado por SHA-256; os cinco Java recebidos conservaram comprimento/hash; tasks.md difere do recebido somente pela checkbox T006. Dois links novos existentes, seis tarefas concluidas e 30 abertas. Conteudo completo dos dois testes novos e diff revistos. `git diff --check`: exit0, sem diagnostico de whitespace; `git diff --no-index --check -- /dev/null <cada Java nao rastreado>`: exit1 em cada um dos sete arquivos, por serem novos, sem diagnostico de whitespace. Avisos LF/CRLF decorrem da configuracao Git existente. O console confirmou Api na porta49518 e ROOT na49525 com URLs H2 distintas; nao ha Maven/Surefire ativo no fechamento. Git final: somente os dois documentos modificados e sete Java nao rastreados (cinco recebidos, dois novos desta T006), nada staged; branch/HEAD/main e selecao008 preservados. `.specify/extensions.yml` ausente tambem apos a execucao, sem hook after_implement. Sem stage, commit, push ou alteracao de main.

## Execução T007 — modelo/probe, metadata e JDBC antes da implementação (2026-10-21)

### Conferência inicial e escopo preservado

Skill speckit-implement aplicada somente à T007. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279` e seleção local Feature 008. Somente quickstart.md/tasks.md estavam modificados; nada staged. Os sete Java não rastreados recebidos, todos preservados por comprimento/SHA-256, eram:

- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java`

Lidos T007 integralmente, dependências T005/T003/T004, progressão tests-first e seleções focais, constituição, AGENTS.md, harness e artefatos pertinentes: spec, plano, pesquisa R3/R4/R8/R9, modelo, contratos e procedimentos/evidências deste guia. Check-prerequisites com -Json -RequireTasks -IncludeTasks retornou exit0 e FEATURE_DIR absoluto 008; checklist requirements.md 16/16, zero aberto, PASS documental, sem alterar marcadores. Ignore Java existente adequado; extensions.yml ausente antes da execução, sem hook registrado.

Antes de iniciar, a consulta de processos Java/Maven/Surefire não encontrou Maven ativo; o diretório de evidências continha somente BASE e S1-RED-T006, sem execução anterior T007. Não houve inventário global de target/. T001–T006, asserções e suportes recebidos, produção, perfis/seed/configuração globais e contratos001–007 foram preservados. T003–T005 continuam historicamente limitadas à revisão estática naquela ocasião; compilação/uso parcial novos pertencem à T006 e à execução atual.

Os **dois motivos históricos do S1-RED HTTP permanecem distintos**: em T006, 24 falhas pelo POST ausente, retornando 404, e oito pelo envelope 401/403 contendo a chave extra errors. Esta T007 não executa novamente essas classes, não corrige segurança/handlers e não atribui o segundo motivo ao roteamento. A BASE histórica 391/7 classes/exit0 também não foi repetida nem aprova pagamento.

### Autoria realizada e alcance dos instrumentos

Novos arquivos: [PaymentPersistenceIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/PaymentPersistenceIntegrationTest.java) e [PaymentRegistrationDatabaseProbeTest.java](../../src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationDatabaseProbeTest.java). O primeiro contém o helper Database/records reutilizados somente pelos dois testes T007. Esses tipos aninhados são suporte, não classes @Nested JUnit. Não há @Disabled, assumptions, seleção vazia, imports de Payment/APIs ainda inexistentes ou DDL artificial.

Cada classe usa contexto Spring Boot web MOCK próprio, perfil test e imports explícitos de PaymentRegistrationTestConfig/AdminProductTestConfig, com chave de contexto distinta (persistence-t007/probe-t007). O inicializador cria URL H2 exclusiva e estável, create-drop, SQL init never e import Hibernate vazio. O setup confere ausência de transação externa e duas conexões físicas DriverManager distintas, READ_COMMITTED e autoCommit=true. As mutações de teste têm transação JDBC própria, commit/rollback explícitos e statements com timeout 10s; recursos fecham por try-with-resources. Não se instalou bean produtivo falso nem se importou o observador do serviço008.

Os cenários compõem as fixtures/probes aprovados. Preparação de contas/produtos/pedidos é commitada; operações sensíveis exigem primeiro asserção explícita de existência da tabela **real** PUBLIC.TB_PAYMENT gerada pelo mapeamento produtivo. Ausência falha como AssertionError antes da fixture/SQL/probe, sem tabela ausente escondendo o diagnóstico. O controle negativo de probe usa outro H2 vazio, sem DDL e sem substituir o schema da aplicação: exige a exceção segura por schema ilegível, nunca snapshot vazio aprovado.

Os testes de limpeza guardam duas fixtures independentes e lançam uma AssertionError deliberada dentro de try-with-resources. Essa falha interna é o estímulo do teste de infraestrutura, capturado e conferido; não é RED fabricado da operação de pagamento. O oráculo exige que nenhuma falha de limpeza seja suprimida, os IDs próprios sejam liberados, close repetido seja seguro e o snapshot da fixture canário continue igual. Há variantes sem Payment e com Payment/PAID; somente a primeira foi alcançada nesta execução.

Nenhum login, perform MockMvc, POST ou HTTP real foi executado nesta seleção; a fixture histórica apenas constrói seu suporte MockMvc interno. Nenhum servidor em porta aleatória foi iniciado. JDBC explícito nesta T007 prepara/examina persistência e falhas de integridade; não comprova a operação administrativa, autorização, referência de negócio, lock/refresh, atomicidade do serviço ou confirmação HTTP. Os cenários SQL podem criar Payment com WAITING_PAYMENT para isolar o schema, pois a equivalência entre tabelas é regra futura do serviço, não CHECK universal do banco.

### Matriz escrita e invocações efetivamente conciliadas

| Classe / método | Invocações | Resultado atual / limite |
| --- | ---: | --- |
| PaymentPersistenceIntegrationTest.generatedPaymentSchemaHasOnlySharedPrimaryForeignKeyAndRequiredMillisecondOccurrence | 1 | RED: tabela ausente antes de inspecionar duas colunas, PK/FK nomeada, NOT NULL, ausência de ID independente/amount e precisão3. |
| orderKeepsItsFourOriginalRequiredColumnsWithoutPaymentOrVersionColumns | 1 | GREEN: quatro colunas, nullability e tipos/identidade atuais preservados. |
| domainStatusContainsExactlyWaitingPaymentAndPaid | 1 | RED: enum Java ainda sem PAID. |
| paymentIsRegisteredInTheRealJpaMetamodel | 1 | RED: Payment ausente do metamodel JPA real. |
| generatedOrderStatusConstraintAcceptsPaidAndRejectsUnknownWithoutChangingOtherColumns | 1 | RED: schema gerado rejeita PAID; rejeição do estado desconhecido/inspeção completa ficam depois dessa asserção. |
| realPaymentRoundTripsAcceptedMillisecondsAcrossIndependentConnections | 3 | RED por tabela ausente; escritos instantes nos anos 0001/2026/9999 e frações .000/.123/.999, leitura OffsetDateTime→Instant por conexão distinta. |
| duplicatePrimaryKeyIsRejectedByJdbcAndPreservesTheOriginalOccurrence | 1 | RED por tabela ausente; SQLState 23505 e preservação do primeiro pagamento não alcançados. |
| unknownOrderIsRejectedByTheNamedForeignKeyWithoutCreatingAnyPayment | 1 | RED por tabela ausente; SQLState 23506/FK nomeada não alcançados. |
| requiredPaymentColumnsRejectNullInTheDatabase | 2 | RED por tabela ausente; order_id/occurred_at e SQLState 23502 não alcançados. |
| deletingTheReferencedOrderCannotCascadeAwayItsPayment | 1 | RED por tabela ausente; teste retira os itens próprios antes de tentar remover pedido para isolar a FK de Payment; SQLState 23503 não alcançado. |
| PaymentRegistrationDatabaseProbeTest.missingSchemaNegativeControlFailsClosedInsteadOfReturningAnEmptyPaymentSnapshot | 1 | GREEN somente do controle negativo sem schema; não aprova mapeamento. |
| realSnapshotIncludesCommittedPaymentAndTheUnchangedSevenHistoricalTables | 1 | RED por tabela ausente; snapshot completo e imutabilidade de Payment ainda não exercitados. |
| detectsAControlledPaymentOccurrenceChangeWithoutAttributingItToTheOtherTables | 1 | RED por tabela ausente; sensibilidade Payment e restauração posterior não alcançadas. |
| composedProbeStillDetectsEachOfTheSevenHistoricalTables | 7 | RED por tabela ausente; uma invocação por produto, categoria, associação, conta/hash, perfil, pedido e item. |
| cleanupAfterAnAssertionFailureWithoutPaymentPreservesUnrelatedHistoricalRows | 1 | GREEN: setup commitado, falha interna controlada, limpeza seletiva/idempotente e canário preservado nas sete tabelas históricas. |
| cleanupAfterAnAssertionFailureRemovesOnlyOwnedPaymentBeforeItsOrder | 1 | RED por tabela ausente; variante com Payment/PAID e canário de oito tabelas não alcançada. |

A futura inspeção do enum consulta ENUM_VALUES quando o tipo é ENUM nativo; para VARCHAR exige o CHECK gerado. Não aceitar qualquer erro como rejeição válida: os SQLStates esperados são específicos (22030 no ENUM nativo ou 23513 no CHECK), assim como os de PK/FK/NOT NULL. Os corpos posteriores ao schema guardado e à aceitação de PAID estão compilados e revistos estaticamente, **mas ainda não executados nem aprovados**.

### Única execução focal e preservação de relatórios

Identificador `S1-RED-T007-20261021T183748334`. Metadados iniciados em 2026-10-21 às 18:37:56−03:00; término às 18:39:18−03:00; duração informada pelo Maven 01:11. Ambiente observado Java Zulu 21.0.12.1+1-LTS, Windows/PowerShell e Wrapper configurado para Maven 3.9.16. Base HEAD acima com alterações recebidas e os dois novos testes, sem mudanças produtivas.

Seleção exigida para a parte nova T007 de S1-RED; as classes HTTP já executadas em T006 foram excluídas para não repetir aquele checkpoint:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentPersistenceIntegrationTest,PaymentRegistrationDatabaseProbeTest" "-Dsurefire.reportNameSuffix=S1-RED-T007-20261021T183748334" test
~~~

A chamada foi sequencial e única. Console capturado com Tee-Object em UTF-16LE; LASTEXITCODE capturado imediatamente após o Wrapper. Resultado: **BUILD FAILURE, exit1**, por falhas de asserção.

| Classe observada no console e XML/TXT | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentRegistrationDatabaseProbeTest | 12 | 10 | 0 | 0 |
| PaymentPersistenceIntegrationTest | 13 | 12 | 0 | 0 |
| Total | **25** | **22** | **0** | **0** |

Diretório ignorado e exclusivo `target/feature008-evidence/S1-RED-T007-20261021T183748334/`:

- run.json/exit.json registram comando, horário, branch/HEAD, Java/Wrapper e exit; git-status-before.txt/tracked-diff-before.patch identificam o estado recebido.
- previous-surefire/ preserva os 236 arquivos diretamente presentes em target/surefire-reports antes da chamada; previous-report-hashes.json registra os hashes. Inclui resultados BASE/T006; não é inventário global de target/ nem substitui os limites da T001.
- new-sources/ conserva os dois Java executados; source-hashes-before.json registra todos os nove Java recebidos/novos antes da compilação. received-file-hashes.json registra também o estado inicial dos documentos.
- new-surefire/ contém somente quatro arquivos novos (XML+TXT de cada classe), também mantidos com sufixo exclusivo em target/surefire-reports.
- reconcile.py/reconciliation.json conciliam console, classes, nomes/multiplicidades e identidades únicas de testcase, resultados, causas e hashes. Conferência documental executada com o Python externo já utilizado, exit0; considera o sufixo acrescentado pelo Surefire a name/classname.

Foram confirmados exatamente 16 métodos (10+6), expandidos para 25 invocações, sem omissões, duplicatas, ignorados ou classe adicional/aninhada JUnit. Os 236 relatórios anteriores e os nove Java conservaram SHA-256 idêntico ao início da chamada. Compilação das 126 fontes de teste passou; contagem de compilação não foi tratada como GREEN de execução. Os dois contextos iniciaram com H2s exclusivos distintos, sem interferência de create-drop. Nenhuma suíte completa, BASE, HTTP T006 ou outro grupo foi executado.

### Diagnóstico observado e pendências reais

As 22 falhas se repartem em **19 asserções de PUBLIC.TB_PAYMENT ausente**, uma de Payment ausente no metamodel JPA, uma do enum Java sem PAID e uma da aceitação de PAID pelo schema gerado. Nesta última, H2 retornou SQLState 22030; a operação sob teste é a aceitação do novo estado e a asserção doesNotThrowAnyException a classifica como falha funcional. Não é exceção SQL não tratada na preparação, nem demonstração isolada de integridade de Payment. Não houve erro de compilação/bootstrap/fixture ou limpeza substituindo o RED. Avisos históricos de compilação/SecurityManager, Byte Buddy/CDS e H2Dialect foram preservados, sem correções fora do escopo.

Os três GREEN têm alcance estrito: colunas atuais de Order, controle negativo do probe vazio e limpeza após falha **sem** pagamento. Não demonstram PK/FK/NOT NULL de Payment, precisão armazenada, sensibilidade positiva do probe de oito tabelas ou limpeza de pagamento antes do pedido. As 19 falhas na guarda de schema impedem alcançar essas asserções; não devem ser contadas como 19 regras de integridade individualmente reproduzidas.

Continuam pendentes o modelo real de T008, testes específicos das APIs Java (construção, @MapsId, relação/LAZY/cascades, persist/round-trip JPA e transição restrita) assim que compiláveis **antes dos respectivos corpos produtivos**, e posterior execução das provas escritas de constraints/precisão/probe/limpeza. Não foram usados reflexão construtiva, tipos fictícios ou schema manual para fingir essas APIs. JDBC round-trip futuro não substitui round-trip ORM; nenhum resultado desta T007 aprova commit HTTP, rollback do serviço ou outro banco/durabilidade.

**T007 permanece [ ]**: autoria compilável e RED inicial concluídos, mas seus critérios de comprovação positiva de sensibilidade/limpeza com Payment real ainda não foram atendidos. Esta seção não declara aprovação integral da tarefa ou do incremento. T001–T006 [X] preservadas; **T007–T036 abertas (6/30)**, sem qualquer checkbox modificada nesta execução. O RED pertinente ao início planejado de T008 está identificado; iniciar T008 exige autorização separada e não ocorreu aqui.

Alterações desta execução: somente dois Java novos e esta seção acrescentada ao quickstart. tasks.md mantém exatamente o conteúdo recebido, ainda modificado por tarefas anteriores. Nenhuma produção, suporte, teste/asserção histórica, segurança ou contrato foi alterado; nada staged, sem commit/push/merge ou alteração de main.

Fechamento observado: diff incremental do quickstart e conteúdo completo dos dois Java novos revistos; conferência estática via Python externo, exit0, registrada em final-static-review.json. Prefixo recebido de 80.494 bytes do quickstart e sete Java recebidos preservados por comprimento/SHA-256; todos os nove Java continuam idênticos aos executados, segundo source-hashes-before.json. tasks.md preservado integralmente, sem checkbox alterada; seis tarefas marcadas e 30 abertas. Dois links novos existentes. git diff --check retornou exit0; git diff --no-index --check contra /dev/null dos nove Java não rastreados retornou exit1 por arquivos novos, sem diagnóstico de whitespace; avisos LF→CRLF decorrem da configuração existente. Relatórios anteriores preservados e novos resultados arquivados separadamente. Git final: dois documentos modificados (tasks.md já recebido assim), nove Java não rastreados (sete recebidos e dois novos), índice vazio; branch/HEAD/main e seleção008 preservados. Nenhum Maven/Surefire ativo; extensions.yml ausente também após a execução, sem hook after_implement. Sem stage, commit, push, merge ou alteração de main.

## Execução T008 — modelo Payment, estado PAID e transição restrita (2026-10-21)

### Contexto confirmado e preservações

Skill speckit-implement aplicada exclusivamente à T008. Estado inicial: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local Feature 008; somente quickstart.md/tasks.md modificados, índice vazio. Os nove Java recebidos de T003–T007 eram:

- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationDatabaseProbeTest.java`
- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationObservationConfig.java`
- `src/test/java/com/devsuperior/dscommerce/config/PaymentRegistrationTestConfig.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpTestSupport.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationDatabaseProbe.java`
- `src/test/java/com/devsuperior/dscommerce/fixtures/PaymentRegistrationTestFixture.java`
- `src/test/java/com/devsuperior/dscommerce/repositories/PaymentPersistenceIntegrationTest.java`

Esses nove arquivos, suas asserções e os registros anteriores foram preservados integralmente por comprimento/SHA-256. T007 permaneceu aberta: seu RED pertinente autoriza a etapa de modelo, sem exigir GREEN antecipado do componente ausente. Foram lidas integralmente T007/T008 e as condições da fase, sob constituição, AGENTS.md, harness, spec, plano, pesquisa/modelo e contratos pertinentes. Check-prerequisites -Json -RequireTasks -IncludeTasks retornou exit0, diretório absoluto 008; requirements.md 16/16, zero aberto, PASS documental sem modificar checklist. Ignore Java adequado; extensions.yml ausente antes da execução.

Antes do Maven, consulta de processos Java/Maven/Surefire não encontrou execução ativa e não havia evidência anterior T008; estavam presentes BASE, S1-RED-T006 e S1-RED-T007. Não se refez inventário global de target/. A BASE histórica 391/7 classes/exit0 permanece histórica. O RED HTTP de T006 continua com causas separadas: POST ausente 404 e envelope 401/403 com errors. O RED original de T007 continua registrado como 25 testes, três aprovados/22 falhas/zero erros ou ignorados/exit1; seus relatórios não foram substituídos pela execução abaixo.

### Implementação limitada ao modelo

Novo [Payment.java](../../src/main/java/com/devsuperior/dscommerce/entities/Payment.java): entidade tb_payment com somente orderId/order/occurredAt; order_id é chave compartilhada @Id/@MapsId e FK nomeada fk_payment_order. Relação @OneToOne LAZY, obrigatória, sem cascades. occurred_at é obrigatório, TIMESTAMP(3) WITH TIME ZONE e @FractionalSeconds(3). Construtor exige pedido/ocorrência não nulos, conserva o Instant fornecido e não altera estado do pedido; identidade é derivada pelo Hibernate na persistência. Nenhum ID gerado independente, amount, comprador duplicado, setter de quitação, associação inversa ou version foi introduzido.

[OrderStatus.java](../../src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java) acrescenta somente PAID. [Order.java](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java) conserva campos/mapeamento/construtor/coleção e a criação WAITING_PAYMENT; markPaid só permite WAITING_PAYMENT→PAID, rejeitando estado incompatível por IllegalStateException e modificando somente status. Não verifica ADMIN, relógio, Payment anterior ou janela temporal; essas decisões e a classificação HTTP pertencem ao serviço futuro. Criar Payment ou chamar markPaid isoladamente não constitui a operação008 completa nem garante equivalência entre tabelas.

Não foram criados repository/fragmento, lock, serviço, DTO, parser, controller, cadeia de segurança, handler ou endpoint de T009+. Configuração global, seed, contratos001–007 e a exceção histórica do GateIV005 permanecem intactos. Nenhuma vigência contratual foi publicada.

### Testes das APIs e estratégia de validação

Antes dos corpos produtivos, foram escritos em arquivos novos [PaymentModelTest.java](../../src/test/java/com/devsuperior/dscommerce/entities/PaymentModelTest.java) e [PaymentModelJpaIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/PaymentModelJpaIntegrationTest.java). Os testes tipados passaram a compilar com o modelo real, sem introduzir stubs produtivos para fabricar RED. O RED anterior confiável de metadata/metamodel/enum da T007 foi reutilizado; não se executou uma tentativa de compilação inválida para rotulá-la como falha funcional, nem se repetiu o antigo RED HTTP.

PaymentModelTest é unitário puro: quatro métodos, cinco invocações; verifica construção sem transição implícita, dois argumentos obrigatórios, transição alterando só status e rejeição de nova transição. Preserva comprador, criação, ordem/referências das duas linhas e produtos, quantidades/preços e total histórico 42,50 mesmo com preços atuais 12/99.

PaymentModelJpaIntegrationTest usa contexto MOCK separado, H2 exclusivo e imports explícitos dos suportes aprovados. Reutiliza somente o helper Database da T007; não o modifica. Três métodos/cinco invocações: três round-trips com anos 0001/2026/9999 e frações .000/.123/.999; uma transação explícita insere Payment/marca PAID, preservando outro pedido e histórico; uma remoção JPA de Payment não remove pedido/itens. As transações de teste são REQUIRES_NEW/READ_COMMITTED explícitas, terminam antes da leitura por probe JDBC independente e da limpeza. Há flush, clear, reload e conferência real de LAZY e relação obrigatória; getId do proxy de Order não o inicializa.

Os instantes extremos são oráculos de armazenamento exato pelo JDBC/ORM, não pedidos temporalmente elegíveis ao serviço. A transação explicitamente controlada pelo teste demonstra o mapeamento e seu commit observado no H2; **não comprova proxy do futuro serviço, commit na entrada HTTP ou atomicidade sob falhas da operação administrativa**. Não há @Transactional externo envolvendo a suíte, @Nested, @Disabled, assumptions ou mocks de pagamento.

### Comando e resultados da única execução

Identificador `MODEL-T008-20261021T185237831`. Metadados iniciados em 2026-10-21 às 18:52:40−03:00; término às 18:54:21−03:00; Maven informou 01:29. Ambiente: Windows/PowerShell, Java Zulu 21.0.12.1+1-LTS e Wrapper configurado para Maven 3.9.16. Revisão: HEAD acima, diff recebido mais modelo/testes novos, sem commit.

Seleção focal do modelo T008 e dos testes existentes de metadata/probe afetados; os grupos HTTP, BASE e suíte completa não foram executados:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentModelTest,PaymentModelJpaIntegrationTest,PaymentPersistenceIntegrationTest,PaymentRegistrationDatabaseProbeTest" "-Dsurefire.reportNameSuffix=MODEL-T008-20261021T185237831" test
~~~

| Classe efetivamente observada no console/XML/TXT | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentModelTest | 5 | 0 | 0 | 0 |
| PaymentModelJpaIntegrationTest | 5 | 0 | 0 | 0 |
| PaymentPersistenceIntegrationTest | 13 | 0 | 0 | 0 |
| PaymentRegistrationDatabaseProbeTest | 12 | 0 | 0 | 0 |
| Total | **35** | **0** | **0** | **0** |

**BUILD SUCCESS, exit0**, com LASTEXITCODE capturado imediatamente após o Wrapper/Tee-Object. Compilaram 88 fontes produtivas e 128 fontes de teste; essas contagens não foram confundidas com número de testes. Nenhum impedimento de compilação, bootstrap, fixture, SQL, limpeza ou asserção foi observado. Avisos históricos SecurityManager/Byte Buddy/CDS/H2Dialect foram preservados sem alterações fora do escopo.

Conciliação estática por reconcile.py, exit0: exatamente quatro classes, 23 métodos e 35 identidades de testcase, sem duplicação, omissão ou classe aninhada JUnit. Multiplicidades: dois argumentos nulos no unitário, três instantes JPA, três instantes JDBC, duas colunas NOT NULL e sete tabelas de sensibilidade. A matriz/métodos recebidos da T007 foram comparados ao reconciliation.json histórico, não somente ao nome do seletor. Foram confirmados três contextos de aplicação H2 distintos; nenhum servidor em porta aleatória, login, perform MockMvc ou HTTP real foi usado. A fixture histórica constrói seu suporte MockMvc interno sem despachar requisições.

### Critérios da T007 agora comprovados e fronteiras restantes

| Critério recebido | Evidência atual |
| --- | --- |
| Schema real, PK/FK/NOT NULL, sem ID independente/amount; precisão3 | Metadata JDBC em PaymentPersistenceIntegrationTest: somente order_id/occurred_at, PK compartilhada, FK fk_payment_order sem DELETE CASCADE, nullability, tipo temporal e escala3. Metamodel JPA inclui Payment. |
| Colunas de Order preservadas e enum ampliado | As quatro colunas antigas permanecem; enum Java exatamente WAITING_PAYMENT/PAID. PAID é aceito no H2; estado desconhecido é rejeitado por 22030; ENUM_VALUES foi inspecionado. |
| Round-trip exato | Três instantes JDBC e três JPA preservados por reload e conexão independente após commit. @MapsId atribuiu exatamente o ID do pedido existente; associação permaneceu LAZY e obrigatória. |
| Constraints físicas | Duplicidade 23505, órfão 23506 pela FK nomeada, ocorrência/ID nulos 23502 e remoção de pedido referenciado 23503 rejeitados; snapshots confirmam preservação do estado anterior. Itens são retirados somente no teste de remoção para isolar a FK de Payment. |
| Sensibilidade positiva do probe | Alteração controlada de occurredAt detectada como tb_payment; sete invocações detectam cada tabela histórica, inclusive hash de conta, com diagnóstico seguro/sem credenciais. Alterações de estímulo restauradas em finally; nenhuma mudança nos probes recebidos. |
| Limpeza após cenário falho | Variantes sem/com Payment passaram, inclusive canário Payment/PAID de outra fixture preservado, ausência de exceção suprimida de cleanup e close repetido seguro. A AssertionError interna é estímulo capturado, não RED artificial da operação. |
| APIs disponíveis nesta etapa | Construção real, transição restrita, persist/clear/reload, identidade derivada, LAZY e ausência de cascata de remoção comprovadas nos dez testes novos. |

Todos os 25 testes recebidos da T007 estão GREEN **nesta revisão local do modelo**, sem alterar qualquer byte de seus arquivos. O antigo resultado 3/22 continua histórico e rastreável. **O marcador T007 permanece aberto por instrução expressa deste checkpoint**, mesmo com essas provas positivas. Permanecem futuras as provas específicas das APIs de inserção/lock da T009 (persist explícito no fragmento real, ausência de merge/save e refresh/lock); elas devem preceder os respectivos corpos quando compiláveis. As provas atuais não os substituem.

Também não estão comprovados parser/validação temporal da entrada, referência única, autorização atual, guards, serviço/rollback, concorrência de requests, entrega 201/erros ou consultas PAID007. Não completar fotografias HTTP/reexecutar S1-GREEN nesta etapa; isso exige os componentes e escopos posteriores. O modelo/constraints não impedem sozinho SQL arbitrário incoerente e não demonstram outro banco, crash recovery ou rede de produção.

### Relatórios, preservação e encerramento da tarefa

Diretório exclusivo ignorado pelo Git: `target/feature008-evidence/MODEL-T008-20261021T185237831/`:

- console.log UTF-16LE, run.json/exit.json: console, comando, ambiente, horários, HEAD/diff e exit; git-status-before.txt/tracked-diff-before.patch registram o estado testado.
- previous-surefire/ e previous-report-hashes.json: cópia anterior e SHA-256 dos 240 arquivos diretamente em target/surefire-reports; todos preservados com hashes idênticos, inclusive relatórios RED/BASE. Essa conferência é proporcional aos relatórios, sem inventário global de target/.
- new-sources/ registra os cinco Java desta implementação; source-hashes-before.json confere os 14 Java envolvidos, incluindo os nove recebidos, idênticos desde o início da execução. received-file-hashes.json registra comprimentos/hashes do estado recebido.
- new-surefire/ contém somente oito arquivos novos (XML+TXT das quatro classes), também mantidos com sufixo exclusivo no diretório Surefire original. reconcile.py/reconciliation.json registram a conferência estática de classes/métodos/invocações e hashes.

Critérios da **T008 atendidos** pelo código e validação focal: somente sua checkbox passa para [X]. T001–T006 preservadas; **T007 [ ], T008 [X], T009–T036 [ ]: sete concluídas/29 abertas**. Nenhuma aprovação integral da 008, alteração de vigência contratual, implementação de repository/serviço/endpoint ou fechamento de T007 foi antecipado.

Arquivos desta tarefa: Payment.java, Order.java, OrderStatus.java, os dois novos testes de modelo, esta seção do quickstart e somente a checkbox T008 em tasks.md. Nove Java recebidos e prefixo documental/histórico preservados; sem enfraquecer asserções, DDL sintético ou contornar constraints. Sem stage, commit, push, merge ou alteração de main.

Fechamento T008 observado: conteúdo dos cinco Java desta implementação e diff incremental revistos; review.py executado com Python externo, exit0, com resultado em final-static-review.json. Prefixo recebido de 96.234 bytes do quickstart e nove Java recebidos preservados por SHA-256; os 14 Java envolvidos permanecem idênticos à revisão executada. Order.java difere somente por markPaid e OrderStatus.java somente pela inclusão de PAID; tasks.md difere do recebido somente pela checkbox T008. Sete tarefas marcadas/29 abertas, com T007 aberta. Cinco links novos existentes; git diff --check exit0, sem diagnóstico de whitespace; conferência --no-index --check dos 12 Java não rastreados contra /dev/null retornou exit1 por arquivos novos, sem diagnóstico de whitespace. Avisos LF→CRLF são da configuração Git existente. Console/Surefire conciliados, 240 relatórios anteriores preservados e oito novos arquivados separadamente. Git final: quatro arquivos rastreados modificados e 12 Java não rastreados (nove recebidos, três novos), nada staged; branch/HEAD/main e seleção008 preservados. Maven/Surefire inativos; extensions.yml ausente após a execução, sem hook after_implement. Sem stage, commit, push, merge ou alteração de main.

## Retomada documental da T007 — conclusão pelas evidências da T008

Escopo desta retomada: conferir os critérios integrais da T007 e o registro da T008 usando somente evidências existentes. Estado confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local Feature 008 e índice vazio. Os quatro rastreados modificados são este quickstart, tasks.md, src/main/java/com/devsuperior/dscommerce/entities/Order.java e OrderStatus.java. Os 12 Java não rastreados conferidos correspondem a Payment.java em src/main/java/com/devsuperior/dscommerce/entities/ e aos 11 testes/suportes da 008 já recebidos; nenhum caminho novo foi criado nesta retomada.

**Nenhum Maven, teste, servidor, validador ou script de validação anterior foi reexecutado.** A conferência leu console/XML/TXT, metadados e manifestos de hashes existentes; não escreveu nos relatórios nem alterou código, testes ou asserções. A única execução reutilizada é `MODEL-T008-20261021T185237831`, concluída em 2026-10-21 às 18:54:21−03:00, com o comando já registrado na seção T008 e em run.json. Não se acrescenta uma segunda execução nem nova aprovação de aplicação à contagem histórica.

### Evidência efetivamente conferida

Diretório da execução: `target/feature008-evidence/MODEL-T008-20261021T185237831/`. Leitura direta de console.log, exit.json, run.json e dos quatro pares XML/TXT em new-surefire/:

| Classe | Testes aprovados | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentPersistenceIntegrationTest — T007 | 13 | 0 | 0 | 0 |
| PaymentRegistrationDatabaseProbeTest — T007 | 12 | 0 | 0 | 0 |
| Subtotal das classes recebidas da T007 | **25** | **0** | **0** | **0** |
| PaymentModelTest — APIs do modelo, acrescentado na T008 | 5 | 0 | 0 | 0 |
| PaymentModelJpaIntegrationTest — ORM, acrescentado na T008 | 5 | 0 | 0 | 0 |
| Total da execução única | **35** | **0** | **0** | **0** |

Console informa BUILD SUCCESS; exit.json registra exitCode 0. Foram conferidas 35 identidades únicas de testcase e as quatro classes em Running, sem omissões/duplicatas, elementos failure/error/skipped ou classe JUnit aninhada prevista ausente. Os 16 métodos e as multiplicidades dos 25 casos T007 coincidem com a matriz histórica: três instantes JDBC, duas colunas NOT NULL e sete invocações de sensibilidade, além dos casos individuais. Os outros dez casos complementam a prova das APIs Java disponíveis nesta etapa, sem serem contados dentro dos 25.

Hashes dos oito relatórios T008 arquivados coincidem com reconciliation.json; hashes dos 14 Java envolvidos coincidem com source-hashes-before.json. Também coincidem os hashes dos nove Java registrados na execução RED original T007, comprovando preservação das asserções recebidas. Essas leituras usam os manifestos já existentes; reconcile.py/review.py não foram executados novamente.

### Critérios da T007 reconciliados

| Critério integral da tarefa | Evidência suficiente já produzida |
| --- | --- |
| Começar por metadata/JDBC e RED de schema ausente, sem erro de preparação | Histórico S1-RED-T007: 25 testes, três aprovados/22 falhas, zero erros/ignorados, exit1; causas discriminadas na seção original. XML/TXT RED arquivados conservam os hashes registrados. |
| Schema/mapeamento real, PK/FK/NOT NULL e ausência de ID independente/amount | generatedPaymentSchemaHasOnlySharedPrimaryForeignKeyAndRequiredMillisecondOccurrence e paymentIsRegisteredInTheRealJpaMetamodel aprovados: somente order_id/occurred_at, chave compartilhada, FK nomeada, nullability e tipo/precisão temporal reais. Nenhum DDL sintético usado como aprovação. |
| Colunas antigas de Order e enum ampliado | orderKeepsItsFourOriginalRequiredColumnsWithoutPaymentOrVersionColumns, domainStatusContainsExactlyWaitingPaymentAndPaid e generatedOrderStatusConstraintAcceptsPaidAndRejectsUnknownWithoutChangingOtherColumns aprovados, incluindo inspeção do enum gerado e rejeição de estado desconhecido. |
| Round-trip temporal exato | Três invocações realPaymentRoundTripsAcceptedMillisecondsAcrossIndependentConnections e três persistDerivesTheSharedIdentityAndReloadsLazyOrderAndExactOccurrence aprovadas: anos 0001/2026/9999, frações .000/.123/.999, leitura independente após commit e reload ORM. |
| Duplicidade, órfão e ocorrência nula rejeitados pelo banco | duplicatePrimaryKeyIsRejectedByJdbcAndPreservesTheOriginalOccurrence, unknownOrderIsRejectedByTheNamedForeignKeyWithoutCreatingAnyPayment e requiredPaymentColumnsRejectNullInTheDatabase[1–2] aprovados, com SQLStates específicos e snapshots preservados; deletingTheReferencedOrderCannotCascadeAwayItsPayment também aprovado. |
| Probe sensível a Payment e às sete tabelas anteriores | detectsAControlledPaymentOccurrenceChangeWithoutAttributingItToTheOtherTables e composedProbeStillDetectsEachOfTheSevenHistoricalTables[1–7] aprovados; restauração dos estímulos e diagnósticos sem credenciais. Probes históricos não foram alterados. |
| Limpeza seletiva inclusive após teste falho | cleanupAfterAnAssertionFailureWithoutPaymentPreservesUnrelatedHistoricalRows e cleanupAfterAnAssertionFailureRemovesOnlyOwnedPaymentBeforeItsOrder aprovados, com canário de outra fixture preservado, close repetido e nenhuma falha de limpeza suprimida. |
| Testes específicos das APIs Java quando compiláveis, antes dos corpos respectivos | Registro T008 documenta autoria prévia dos novos testes do modelo; seus dez casos aprovados cobrem construção, transição restrita/histórico, @MapsId por persist real, LAZY obrigatório, round-trip JPA e remoção sem cascata. Nenhum tipo produtivo fictício ou erro de compilação foi contado como RED. |

**Todos os critérios da T007 relativos ao modelo e ao suporte agora disponível estão comprovados pela execução já realizada na T008.** A manutenção anterior de seu marcador aberto foi uma instrução específica daquele checkpoint, preservada como histórico; esta retomada autoriza seu fechamento documental. As provas futuras das APIs de inserção/lock, quando existirem, continuam exigidas antes dos corpos de T009; não são APIs disponíveis nem trabalho executado nesta retomada.

Somente a checkbox T007 passa para [X]. Resultado conferido: **T001–T008 [X], T009–T036 abertas (8 concluídas/28 abertas)**. T008 e seu registro não foram reescritos. O RED original T007 permanece como evidência histórica 3/22, sem conversão retroativa para GREEN; o GREEN vem da execução T008 identificada acima. O RED HTTP T006, com POST 404 e envelope 401/403 contendo errors, também permanece preservado e não foi repetido.

Não há lacuna remanescente para o fechamento solicitado da T007 neste checkpoint. A operação administrativa 008 completa, repositórios/lock/serviço/endpoint e demais provas de T009+ continuam pendentes; os 35 testes do modelo não equivalem a S1-GREEN HTTP, suíte completa, CI ou entrega da feature.

Alterações desta retomada limitadas a esta seção acrescentada e à checkbox T007 em [tasks.md](tasks.md). Prefixo histórico do quickstart preservado; nenhum código, teste, relatório ou outra checkbox alterado. Revisão limitada ao diff documental, referência local e whitespace, sem stage, commit ou push.

Fechamento desta retomada: diff documental incremental, referência tasks.md e preservação do prefixo recebido de 110.925 bytes conferidos; somente a checkbox T007 mudou. git diff --check limitado aos dois documentos retornou exit0, sem diagnóstico de whitespace; avisos LF→CRLF decorrem da configuração existente. Estado final conferido: T001–T008 marcadas, T009–T036 abertas; quatro rastreados modificados e 12 Java não rastreados nos mesmos caminhos recebidos, índice vazio, branch/HEAD/main e seleção008 preservados. Apenas quickstart.md/tasks.md foram alterados nesta retomada. Sem nova execução Maven/validadores, alteração de código/testes/relatórios, stage, commit, push ou avanço de T009+.

## Execução T009 — repositórios de pagamento, lock/refresh e projeção histórica

### Conferência inicial e limites do escopo

Skill speckit-implement aplicada exclusivamente à T009. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local Feature 008 e índice vazio. Quatro rastreados já modificados: Order.java, OrderStatus.java, este quickstart e tasks.md, nos caminhos recebidos; doze Java não rastreados recebidos: Payment.java e os onze testes/suportes T003–T008. Caminhos completos, comprimentos e SHA-256 registrados em received-file-hashes.json da evidência GREEN; o modelo e esses testes não foram modificados.

Foram lidos T009 integralmente, dependências T007/T008 e condições tests-first, plano/pesquisa R4/R5/R6, modelo, spec/contratos pertinentes e procedimentos/evidências anteriores, sob constituição, AGENTS.md e harness. Check-prerequisites -Json -RequireTasks -IncludeTasks retornou exit0 e FEATURE_DIR absoluto 008. requirements.md: 16/16, zero aberto, PASS documental sem alterar checklist. Ignore Java existente adequado; extensions.yml ausente antes da execução. Não havia Maven/Surefire ativo nem evidência anterior T009 antes de iniciar; as duas invocações abaixo foram sequenciais.

T001–T008 e seus registros foram preservados. A execução MODEL-T008 de 35 aprovados não foi repetida, assim como BASE 391, grupos HTTP, S1-GREEN ou suíte completa. Permanecem os dois diagnósticos HTTP da T006: POST ausente 404 e envelope 401/403 contendo errors. Nenhuma mudança de segurança, handler, serviço, DTO, parser ou endpoint foi feita; T010+ não foi antecipada.

### Código implementado

Novos arquivos produtivos:

- [PaymentRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/PaymentRepository.java): interface Spring Data mínima Repository<Payment,Long>, lookup por chave compartilhada e composição do fragmento de inserção. Não expõe save/merge.
- [PaymentInsertRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepository.java) e [PaymentInsertRepositoryImpl.java](../../src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepositoryImpl.java): insert chama somente EntityManager.persist. Sem flush/commit, merge, retry ou consulta de recuperação; falha propaga.
- [OrderPaymentLockRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepository.java) e [OrderPaymentLockRepositoryImpl.java](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryImpl.java): find(Order,id,PESSIMISTIC_WRITE), retorno vazio se ausente; para raiz encontrada, refresh com o mesmo lock antes de devolver a entidade. Não consulta campos antes do refresh, coleções, Product, titular ou estado na aquisição.

[OrderRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java) compõe o novo fragmento e acrescenta findPaymentItemsByOrderId com PaymentItemView interna, somente productId/position/quantity/unitPrice ordenados por position. Projeção não retorna OrderItem/Product nem calcula montante; não há JOIN FETCH/DISTINCT. As três consultas próprias existentes, seus predicates, count e ordenação foram preservados.

As novas operações públicas dos repositórios exigem transação do chamador por MANDATORY; não abrem uma transação própria que liberaria o lock antes das próximas decisões. Lookup/projeção usam readOnly nessa fronteira, sem substituir os atributos da transação já existente. A API não impõe por si só que toda chamada já tenha bloqueado a raiz: a ordem lock→lookup Payment→projeção foi exercitada pelo teste; sua orquestração produtiva é responsabilidade futura do serviço. READ_COMMITTED e retenção do lock até o término dependem da transação do chamador. Nenhuma regra de autorização/elegibilidade/tempo foi implementada no repository.

Payment, PK/FK, precisão, markPaid e modelos anteriores continuam idênticos aos recebidos. Repositórios não marcam PAID nem concluem a operação administrativa; inserir por eles isoladamente não constitui quitação funcional.

### Tests first e RED funcional observado

Novo [PaymentRepositoriesIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/PaymentRepositoriesIntegrationTest.java) começou com um teste executável de presença do bean paymentRepository, sem imports de APIs então ausentes. Execução `REPO-T009-RED-20261021T190656864`: metadados iniciados em 2026-10-21 às 19:06:59−03:00, término 19:08:17−03:00; duração Maven 01:07.

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRepositoriesIntegrationTest#paymentRepositoryIsRegisteredInTheRealSpringContext" "-Dsurefire.reportNameSuffix=REPO-T009-RED-20261021T190656864" test
~~~

Console/Surefire conciliados: **1 teste, 1 falha, zero erros/ignorados, exit1**. Causa: asserção explícita de bean produtivo ausente após bootstrap real bem-sucedido. Não houve erro de compilação/preparação ou stub lançando erro para fabricar RED. Essa evidência demonstra ausência de registro do repository; não demonstra isoladamente falhas de refresh, projeção, unicidade ou transação.

Após esse RED, foram escritos os cenários tipados das APIs e o novo [PaymentRepositoryFragmentsTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/PaymentRepositoryFragmentsTest.java), antes dos corpos produtivos. O teste inicial e sua asserção foram mantidos; os doze Java recebidos não foram editados. Os novos cenários passaram a compilar com os componentes reais, sem tipos produtivos fictícios ou compilação inválida apresentada como RED.

### GREEN focal e conciliação

Execução `REPO-T009-GREEN-20261021T191102143`: metadados iniciados em 2026-10-21 às 19:11:04−03:00, término 19:12:25−03:00; duração Maven 01:09. Ambiente de ambas: Windows/PowerShell, Java Zulu 21.0.12.1+1-LTS e Wrapper configurado para Maven 3.9.16; HEAD acima, com diff recebido e alterações desta T009.

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRepositoryFragmentsTest,PaymentRepositoriesIntegrationTest" "-Dsurefire.reportNameSuffix=REPO-T009-GREEN-20261021T191102143" test
~~~

| Classe efetivamente executada | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentRepositoriesIntegrationTest | 11 | 0 | 0 | 0 |
| PaymentRepositoryFragmentsTest | 5 | 0 | 0 | 0 |
| Total GREEN | **16** | **0** | **0** | **0** |

**BUILD SUCCESS, exit0**; LASTEXITCODE capturado imediatamente após o Wrapper/Tee-Object. Compilação GREEN: 93 fontes produtivas e 130 fontes de teste, sem confundir compilação com execução. reconcile.py conferiu console/XML, nomes de classes/métodos, parâmetros e identidades únicas, exit0: sete métodos de integração expandidos para 11 invocações e cinco métodos unitários. Sem omissões, duplicatas, ignorados ou classes @Nested previstas; os tipos aninhados de suporte/projeção não são cenários JUnit.

| Cenário / método | Invocações GREEN | Prova e alcance |
| --- | ---: | --- |
| paymentRepositoryIsRegisteredInTheRealSpringContext | 1 | Bean real agora registrado; injeções dos repositories também concluídas. |
| lockAndRefreshOnlyTheRequestedRootWithoutLoadingBuyerOrItems | 2 | WAITING_PAYMENT e PAID adquiridos sem filtro de estado/titular; dois SELECT da raiz com FOR UPDATE, entidade gerenciada com PESSIMISTIC_WRITE, buyer/items não carregados, oito tabelas preservadas. |
| missingRootReturnsEmptyWithoutRefreshOrUnrelatedReads | 1 | Ausente retorna vazio; somente um SELECT de raiz, sem refresh/leituras alheias/DML. |
| lookupThenScalarHistoricalProjectionFollowTheRootLocksWithoutReadingProductOrLoadingItems | 1 | Dois SELECT de lock/refresh, depois lookup Payment e projeção escalar; quatro statements nessa ordem. Histórico 2×10+3×7,50, posições 0/1 e productIds em ordem própria, mesmo com catálogo 12/99. Sem JOIN/Product/coleção carregada ou mudança de snapshots. |
| explicitInsertCommitsOneRowAndDuplicateKeyCannotOverwriteTheOriginalOccurrence | 1 | Após lock/refresh, um INSERT sem SELECT de merge/UPDATE. Payment commitado visível no probe, leitura por chave com Order LAZY; nova inserção em outra transação falha 23505 e preserva ocorrência original e oito tabelas. |
| paymentRepositoryOperationsRequireTheCallersTransaction | 4 | lock/items/lookup/insert fora da transação rejeitados por IllegalTransactionStateException, sem SQL ou mutação. |
| existingOwnQueriesKeepTheirOwnerPredicatesAfterAddingThePaymentFragment | 1 | Página, batch e detalhe mantêm titularidade e exclusão do alheio após composição; snapshot preservado. É regressão focal dos métodos tocados, não repetição da BASE/HTTP007. |
| findThenRefreshBothUsePessimisticWriteWithoutReadingAnyOrderFields | 1 | Mock de EntityManager confirma sequência exata e ausência de qualquer acesso a campos do Order antes do retorno. |
| missingOrderDoesNotRefreshOrAttemptAnotherLookup | 1 | Mock confirma só find e retorno vazio, sem tentativa adicional. |
| insertUsesOnlyPersistWithoutMergeSaveOrImplicitFlush | 1 | Mock confirma persist exato; nenhuma chamada merge/flush ou interação extra. |
| refreshFailurePropagatesWithoutReadingTheRootOrContinuing | 1 | Falha controlada de refresh propaga, sem ler raiz/continuar. |
| persistFailurePropagatesWithoutRetryMergeOrFallbackQueries | 1 | Falha controlada de persist propaga, sem retry, merge ou consulta posterior. |

O log H2 SQLState 23505/PRIMARY KEY no console GREEN é o estímulo esperado de duplicidade, capturado pela asserção; Surefire registra zero erros e exit0. Não foi classificado como falha de preparação nem confundido com conflito 409 de domínio. SQL da janela foi capturado em memória pelo StatementInspector aprovado e assertado; os statements completos das janelas não foram exportados como log separado. As fontes executadas e seus hashes preservam as asserções desses formatos/contagens. JDBC direto de fixture/probe não é capturado por esse inspector.

### Isolamento, preservação e limites da evidência

Integração em contexto web MOCK, perfil test, imports explícitos PaymentRegistrationTestConfig/OwnOrderQueryTestObservationConfig e inicializador AdminProductTestConfig. URL H2 única por contexto, create-drop sem seed/import global, OSIV=false; cada execução usou seu contexto próprio e não houve sobreposição de Maven. Helper Database recebido confere conexões independentes READ_COMMITTED e ausência de transação externa; TransactionTemplate do teste exige nova transação READ_COMMITTED. Fixtures commitam antes da janela, snapshots por conexões independentes ocorrem após término e antes da limpeza seletiva Payment→itens→pedidos. Não se mudou o suporte, propriedade global ou seed para obter GREEN.

Os cinco testes unitários usam Mockito/EntityManager para verificar chamadas e propagação; não provam SQL, lock físico ou commit. Os onze casos reais usam JPA/H2, proxies Spring dos repositories e transação explicitamente dona do teste; não usam mock de repository/serviço. Não houve perform MockMvc, login ou HTTP real; a fixture recebida apenas constrói suporte MockMvc interno.

Não se coordenaram duas transações concorrentes, espera por lock ou contexto pré-carregado obsoleto. A sequência refresh foi comprovada por interação/SQL, não como prova de atualização sob corrida. As garantias de refresh diante de alteração concorrente, mesmo pedido/pedidos distintos e ausência de serialização global permanecem nos experimentos posteriores, sem antecipar T020. Tampouco se comprovou atomicidade de Payment+PAID, proxy do futuro serviço, guarda ADMIN/relógio, rollback sob failpoints, confirmação HTTP ou outro banco. Esses resultados não são S1-GREEN, FINAL, CI ou entrega da 008.

Diretórios exclusivos ignorados sob target/feature008-evidence/:

- `REPO-T009-RED-20261021T190656864/`: console.log UTF-16LE, run.json/exit.json, red-test.java da versão executável inicial, estado/diff imediatamente anterior, previous-surefire/ com 248 relatórios anteriores e seus hashes; new-surefire/ com somente XML+TXT RED.
- `REPO-T009-GREEN-20261021T191102143/`: console/metadados/estado/diff, new-sources/ com os oito Java desta T009, source-hashes-before.json com 22 Java envolvidos, received-file-hashes.json do estado recebido; previous-surefire/ preserva 250 relatórios, incluindo RED desta tarefa, com SHA-256 idêntico.
- new-surefire/ GREEN contém somente quatro arquivos novos XML+TXT, também mantidos com sufixo exclusivo em target/surefire-reports. reconcile.py/reconciliation.json registram conferência documental de contagens, casos e hashes. Não se refez inventário global de target/ nem se tratou o inventário opcional incompleto da T001 como prova.

Critérios da **T009 atendidos** pelo código e pela validação focal: somente a checkbox T009 passa para [X]. **T001–T009 [X], T010–T036 abertas: 9 concluídas/27 abertas**. Nenhum impedimento de compilação/preparação ou falha não resolvida nesta seleção. Os diagnósticos HTTP históricos permanecem pendentes para seus componentes próprios.

Alterações limitadas aos cinco novos arquivos de repository/fragmento, integração/projeção em OrderRepository.java, dois testes novos, esta seção e checkbox T009. Modelo, markPaid, testes/asserções recebidos, histórico de T001–T008 e consultas próprias anteriores preservados; sem stage, commit, push, merge ou alteração de main.

Fechamento T009: revisão do diff produtivo/documental e dos Java novos concluída; os 119.033 bytes históricos do quickstart e os 14 Java recebidos (Order/OrderStatus e doze não rastreados) conservaram seus SHA-256. Removendo apenas as adições desta tarefa de OrderRepository, seu conteúdo coincide com HEAD: consultas próprias, predicates, count e ordenação preservados. Em tasks.md, a única mudança desta tarefa foi [ ]→[X] de T009; conferidos 36 marcadores, T001–T009 concluídas e T010–T036 abertas. Oito links novos resolvidos, 22 fontes idênticas às executadas no GREEN, 250 relatórios anteriores e quatro novos preservados; evidência em final-static-review.json. git diff --check e conferência --no-index --check dos 19 Java não rastreados sem diagnóstico de whitespace; avisos LF→CRLF refletem configuração existente. extensions.yml continua ausente, sem hooks após implementação. Estado final: cinco rastreados modificados e 19 Java não rastreados, nada staged; branch, HEAD, main e seleção008 preservados. Nenhuma execução Maven adicional após a conciliação GREEN, nem stage, commit ou push.

## Execução T010 — DTOs e leitura estrita da ocorrência

### Contexto confirmado e escopo autorizado

Skill speckit-implement aplicada exclusivamente à [T010](tasks.md), após T009 e os testes prévios pertinentes T006–T007. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279` e seleção local Feature 008. Cinco rastreados já modificados: Order.java, OrderStatus.java, OrderRepository.java, este quickstart e tasks.md; 19 Java não rastreados nos caminhos recebidos, nada staged. received-file-hashes.json/received-status.txt guardam os 24 caminhos completos, comprimentos e SHA-256; nenhum dos 22 Java recebidos foi alterado nesta tarefa.

Leituras pertinentes: critérios integrais T010, dependências e disciplina tests-first/checkpoints; constituição, AGENTS.md, harness, spec, plano, [pesquisa R2/R3](research.md), modelo e contrato POST008, procedimentos/evidências anteriores e leitores/DTOs existentes. Check-prerequisites -Json -RequireTasks -IncludeTasks retornou exit0 com FEATURE_DIR absoluto 008. requirements.md permaneceu 16/16 marcado, zero aberto, PASS documental; nenhum checklist alterado. Ignore Java existente adequado. extensions.yml ausente antes da execução; nenhum Maven/Surefire ativo ou evidência anterior T010 encontrada antes de iniciar.

Preservados modelo Payment/PK-FK/precisão/markPaid, repositórios/lock-refresh/projeção/persist e todos os testes/asserções/evidências anteriores. BASE (391), MODEL-T008 (35), REPO-T009 (16), grupos HTTP, checkpoints conjuntos e suíte completa não foram repetidos. Permanecem os diagnósticos HTTP históricos T006: POST ausente 404 e envelope 401/403 contendo errors. Serviço, segurança, endpoint, advice e demais componentes T011+ não foram implementados.

### Componentes desta tarefa

- [PaymentRegistrationRequestDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/PaymentRegistrationRequestDTO.java): record somente Instant occurredAt com @NotNull. Ausência/null de propriedade permanecem disponíveis à Bean Validation local; o controller futuro deve aplicar @Valid. Não há consulta, Clock ou regra de janela dependente de pedido.
- [PaymentConfirmationDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/PaymentConfirmationDTO.java): somente orderId/occurredAt/status, com Instant formatado UTC e exatamente três casas por anotação restrita ao campo. Construção exige ID positivo, PAID, ocorrência não nula, ano UTC 0001–9999 e precisão exata em ms; resultado interno inválido falha antes de formatação que pudesse truncar. Construção não é prova de commit nem resposta 201.
- [PaymentRequestJsonConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/PaymentRequestJsonConfig.java): @JsonComponent registra streaming deserializer somente para PaymentRegistrationRequestDTO. Objeto único, campo único, detecção de duplicatas inclusive nomes escapados, rejeição de propriedades extras/tipos/coerções e trailing JSON. Null na raiz é rejeitado; objeto vazio/campo null seguem para @NotNull.
- [PaymentRegistrationDtoContractTest.java](../../src/test/java/com/devsuperior/dscommerce/dto/PaymentRegistrationDtoContractTest.java): slice Boot @JsonTest e ValidatorFactory local fechada em AfterAll; sem serviço/controller/DataSource/EntityManagerFactory/Clock no contexto.

Reader usa gramática ASCII explícita, T/Z maiúsculos, segundos, offset Z ou ±HH:MM até ±18:00 e fração opcional de 1–9 dígitos. OffsetDateTime/ISO_OFFSET_DATE_TIME estrito confere calendário; conversão conserva o mesmo Instant e também confere ano UTC. nano %1000000 precisa ser zero. Não há trim, arredondamento, truncagem, aceitação de -00:00, leap second ou hora 24. Frações adicionais zeradas são normalizadas sem perda. Mensagem local de falha é fixa: Invalid payment data, sem propagar diagnóstico textual do parser como mensagem da aplicação; o envelope/status HTTP ainda dependem da tarefa própria.

Nenhum flag global do mapper, serializer global de Instant ou DTO anterior foi modificado. A configuração é exclusiva do novo tipo, seguindo o padrão dos leitores existentes. Guardas de saída só asseguram representação válida do DTO; não avaliam permissão, histórico ou janela. Não se captura/reduz referência temporal no reader/DTO. A captura única com precisão nativa e sua comparação pertencem a T011. Não há montante na entrada/saída; a rejeição de amount e uma regressão local da string monetária máxima preservam esse contrato, sem antecipar o cálculo histórico BigDecimal da T011.

### Tests first e RED funcional

O teste completo foi escrito primeiro; em seguida foram disponibilizados os dois records tipados, com @NotNull no request, ainda sem reader específico, formato canônico ou guardas de confirmação. Esses records são APIs reais mantidas no resultado, sem tipos fictícios ou corpos que lançassem erro para fabricar RED. A compilação concluída permitiu observar o comportamento permissivo do mapper existente antes dos corpos correspondentes.

Execução `DTO-T010-RED-20261021T192512466`, metadados iniciados 2026-10-21 às 19:27:16−03:00, término 19:28:22−03:00, duração Maven 01:00:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationDtoContractTest" "-Dsurefire.reportNameSuffix=DTO-T010-RED-20261021T192512466" test
~~~

**141 testes: 91 aprovados, 50 falhas de asserção, zero erros/ignorados, exit1, BUILD FAILURE.** Console/XML/TXT conciliados; bootstrap/compilação válidos, nenhuma falha de preparação contada como ausência funcional.

| Método com falhas RED | Falhas |
| --- | ---: |
| rejectsInvalidGrammarCalendarRangeAndSubmillisecondsWithoutRounding | 22 |
| rejectsNonobjectsWrongTypesUnknownOrDuplicateFieldsAndTrailingContent | 13 |
| confirmationContainsExactlyThreeFieldsAndCanonicalUtcMilliseconds | 7 |
| confirmationRejectsUnrepresentableInstantsInsteadOfSilentlyTruncating | 6 |
| confirmationRejectsMissingValuesInvalidIdentityAndAnyStatusOtherThanPaid | 1 |
| paymentReaderFailuresKeepAFixedMessageWithoutEchoingReceivedValues | 1 |
| Total | **50** |

O RED demonstra permissividade real em parte da gramática/estrutura, ausência de guardas de saída e representação variável de fração. Os outros casos já satisfeitos pelo Jackson/calendário/Bean Validation permaneceram GREEN, sem regressão artificial. As 50 falhas não são 50 erros de parse nem 50 causas independentes de negócio.

### GREEN focal e conciliação

Após implementar o reader e a confirmação, sem alterar o teste/asserções, execução `DTO-T010-GREEN-20261021T192922718`, iniciada 19:29:25−03:00, término 19:30:30−03:00 em 2026-10-21, duração Maven 59.647s:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationDtoContractTest" "-Dsurefire.reportNameSuffix=DTO-T010-GREEN-20261021T192922718" test
~~~

**141 aprovados, zero falhas/erros/ignorados, exit0, BUILD SUCCESS.** Ambiente das duas execuções: Windows/PowerShell, Java Zulu 21.0.12.1+1-LTS e Wrapper com Maven 3.9.16 configurado. LASTEXITCODE capturado imediatamente após Wrapper/Tee-Object. RED compilou 95 fontes produtivas/131 de teste; GREEN 96/131, sem contar compilação como execução. Avisos históricos de APIs depreciadas/ByteBuddy não alteram o resultado Surefire.

| Método efetivamente executado em PaymentRegistrationDtoContractTest | Invocações GREEN | Alcance |
| --- | ---: | --- |
| acceptsOnlyLosslessOccurrencesAndKeepsTheExactInstant | 24 | Frações 1–9 exatas, zeros adicionais, offsets equivalentes/limite ±18:00, anos 0001/9999 e calendários bissextos 2000/2400; Instant conservado. |
| rejectsInvalidGrammarCalendarRangeAndSubmillisecondsWithoutRounding | 48 | Sintaxe/capitalização/offset/segundos, espaços, ano local e UTC, calendário inválido, leap second/hora 24, subms e mais de 9 dígitos rejeitados. |
| rejectsNonobjectsWrongTypesUnknownOrDuplicateFieldsAndTrailingContent | 32 | Raiz/tipos inválidos, amount/buyer/reference/status/case, duplicatas iguais/null/escapadas, documentos incompletos e conteúdo posterior rejeitados. |
| leavesMissingOrNullOccurrenceForLocalNotNullBeanValidation | 2 | Ausência/null produz violação @NotNull em occurredAt, inclusive construção direta nula. |
| confirmationContainsExactlyThreeFieldsAndCanonicalUtcMilliseconds | 24 | ID int64 máximo exato, PAID e UTC.SSS somente; saída parseada conserva o Instant aceito. |
| confirmationRejectsUnrepresentableInstantsInsteadOfSilentlyTruncating | 6 | Ano UTC fora do contrato, extremos Instant e subms não são truncados/formatados. |
| confirmationRejectsMissingValuesInvalidIdentityAndAnyStatusOtherThanPaid | 1 | Múltiplas asserções para ID null/zero/negativo, ocorrência null e status null/WAITING_PAYMENT. |
| paymentReaderFailuresKeepAFixedMessageWithoutEchoingReceivedValues | 1 | Mensagem fixa em campo extra, texto inválido e JSON interno malformado; não equivale a envelope HTTP seguro. |
| paymentSpecificRulesDoNotChangeOtherInstantCoercionOrDocumentPolicies | 1 | Tipo alheio conserva nanos, coerção anterior, tolerância a campo extra, duplicata e trailing JSON; Instant global preservado. |
| existingOwnRepresentationKeepsItsCreationFormatAndExactMoneyStrings | 1 | Resumo próprio anterior conserva campos, criação UTC.SSS, WAITING_PAYMENT e string 99999999999999999.99. |
| jsonSliceHasNoPersistenceClockOrApplicationController | 1 | Ausência desses beans no slice. |
| Total | **141** | **11 métodos, nenhuma classe JUnit @Nested prevista.** |

reconcile.py/reconciliation.json conferem somente a classe selecionada no console, 141 identidades únicas de testcase, onze métodos e multiplicidades, ausência de failure/error/skipped, totais e exit. UnrelatedInput é record auxiliar aninhado, não classe de teste omitida. A primeira passagem do conferidor documental precisou ajustar a expectativa do classname ao sufixo Surefire real `Classe(sufixo)`; corrigida a leitura do metadado, o conferidor retornou exit0. Nenhum Java/teste foi alterado e nenhum Maven foi repetido por esse ajuste.

### Evidências preservadas e limites

Diretórios exclusivos ignorados sob target/feature008-evidence/:

- `DTO-T010-RED-20261021T192512466/`: received-file-hashes.json/received-status.txt/received-tracked-diff.patch, run.json/exit.json, console.log UTF-16LE, sources/ com os três Java executados no RED e hashes; previous-surefire/ preserva 254 relatórios anteriores, new-surefire/ somente XML+TXT RED, reconciliation.json com falhas/contagens.
- `DTO-T010-GREEN-20261021T192922718/`: console/metadados/estado/diff, cópias dos quatro Java executados e source-hashes-before.json, manifesto recebido, previous-surefire/ preserva 256 relatórios incluindo RED, new-surefire/ somente XML+TXT GREEN, reconcile.py/reconciliation.json. SHA-256 dos relatórios anteriores conferidos e teste GREEN idêntico ao RED.

Conferência proporcional dos relatórios Surefire XML/TXT relevantes; não houve inventário global de target/ nem uso do inventário opcional incompleto da T001 como prova. Ambos os runs foram sequenciais e preservam seus resultados separados.

Esta seleção prova JSON e Bean Validation local, sem mocks de serviço/repository e sem MockMvc/HTTP real. Não iniciou H2, conexão/JPA, fixture, transação ou servidor web; portanto isolamento/limpeza H2 não foram exercitados novamente. Não prova @Valid MVC, ID/query/segurança HTTP, status 400/201, envelope, captura única de Clock, janela criação–referência, valores calculados, persistência, lock, concorrência, rollback ou commit. Essas provas continuam nas tarefas próprias; o RED HTTP T006 permanece histórico pendente. Não é S1-GREEN, BASE, FINAL, CI ou entrega funcional da 008.

Critérios da **T010 atendidos**: somente sua checkbox passa para [X]. **T001–T010 [X], T011–T036 abertas: 10 concluídas/26 abertas**. Alterações desta tarefa limitadas aos três Java produtivos novos, ao teste novo, esta seção e checkbox T010. Sem stage, commit, push, merge ou alteração de main.

Fechamento T010: diff incremental e quatro Java novos revisados; histórico do quickstart recebido (133.877 bytes), 22 Java anteriores e teste/asserções do RED preservados por SHA-256. Somente a checkbox T010 mudou em tasks.md; conferidos os 36 marcadores, dez concluídos e 26 abertos. Seis links novos resolvidos; git diff --check retornou exit0 e a conferência --no-index --check dos 23 Java não rastreados não apontou whitespace (exit1 nessa modalidade indica diferenças, sem diagnóstico). final-static-review.json registra essas conferências; os quatro Java finais coincidem com os executados no GREEN. extensions.yml segue ausente, sem hooks posteriores; nenhum Maven/Surefire permaneceu ativo. Estado final: cinco rastreados modificados, 23 Java não rastreados, nada staged; branch/HEAD/main e seleção008 preservados. Nenhum impedimento remanescente nesta tarefa e nenhuma execução Maven adicional após GREEN. Sem stage, commit ou push.

## Execução T011 — guarda atual e serviço transacional de pagamento

### Contexto recebido e escopo

Em 2026-10-22, skill speckit-implement aplicada exclusivamente à T011. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local008 e nada staged. Cinco rastreados recebidos modificados: este quickstart, tasks.md, src/main/java/com/devsuperior/dscommerce/entities/Order.java, src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java e src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java.

Os 23 Java não rastreados recebidos foram conferidos nestes caminhos, relativos à raiz:

| Diretório | Arquivos recebidos |
| --- | --- |
| src/main/java/com/devsuperior/dscommerce/config/ | PaymentRequestJsonConfig.java |
| src/main/java/com/devsuperior/dscommerce/dto/ | PaymentConfirmationDTO.java; PaymentRegistrationRequestDTO.java |
| src/main/java/com/devsuperior/dscommerce/entities/ | Payment.java |
| src/main/java/com/devsuperior/dscommerce/repositories/ | OrderPaymentLockRepository.java; OrderPaymentLockRepositoryImpl.java; PaymentInsertRepository.java; PaymentInsertRepositoryImpl.java; PaymentRepository.java |
| src/test/java/com/devsuperior/dscommerce/config/ | PaymentRegistrationDatabaseProbeTest.java; PaymentRegistrationObservationConfig.java; PaymentRegistrationTestConfig.java |
| src/test/java/com/devsuperior/dscommerce/controllers/ | PaymentRegistrationApiHttpIntegrationTest.java; PaymentRegistrationHttpIntegrationTest.java; PaymentRegistrationHttpTestSupport.java |
| src/test/java/com/devsuperior/dscommerce/dto/ | PaymentRegistrationDtoContractTest.java |
| src/test/java/com/devsuperior/dscommerce/entities/ | PaymentModelTest.java |
| src/test/java/com/devsuperior/dscommerce/fixtures/ | PaymentRegistrationDatabaseProbe.java; PaymentRegistrationTestFixture.java |
| src/test/java/com/devsuperior/dscommerce/repositories/ | PaymentModelJpaIntegrationTest.java; PaymentPersistenceIntegrationTest.java; PaymentRepositoriesIntegrationTest.java; PaymentRepositoryFragmentsTest.java |

O manifesto received-file-hashes.json em target/feature008-evidence/SERVICE-T011/ registra os 28 caminhos recebidos, comprimentos e SHA-256. Os 26 Java recebidos, incluindo os três rastreados, foram preservados integralmente; o prefixo recebido deste quickstart tem 146.982 bytes. Modelo, repositórios, reader exclusivo, mapper anterior, DTOs, asserções, suportes e evidências anteriores permanecem preservados.

Lidos T011 integralmente, dependências T009–T010 e seus testes/evidências, tasks/plan, constituição, spec, research R1–R9, data-model, contratos pertinentes e procedimentos do quickstart/harness. check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks retornou exit0 e FEATURE_DIR absoluto008. requirements.md: 16/16, zero aberto, PASS documental, sem modificar checklist. Ignore Java existente suficiente; extensions.yml ausente na conferência inicial. Sem Maven/Surefire ativo antes da execução. Não se executaram BASE, seleções T006–T010, S1-GREEN ou suíte completa.

### Implementação da tarefa

- [PaymentAccessService.java](../../src/main/java/com/devsuperior/dscommerce/services/PaymentAccessService.java): aceita somente identidade autenticada válida e relê findEffectiveAccountRolesById a cada chamada. ADMIN isolado basta; authorities do token e coleção gerenciada não substituem a projeção escalar. Ausência de conta gera categoria401, ausência de ADMIN403 e falha/inconsistência da projeção500 com mensagem fixa e causa preservada. Não altera a guarda007.
- [PaymentRegistrationService.java](../../src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java): primeira instrução de negócio captura clock.instant sem truncagem. Guarda antes de consultar pedido; aquisição pelo fragmento lock/refresh recebido; nova guarda depois de seu retorno, antes de mutação. Sob a mesma transação, Payment anterior precede estado incompatível, com conflitos distintos; ausente404, janela inválida400, raiz/histórico corrompidos500. A referência local não avança durante consultas/esperas.
- A soma integral deriva somente de productId/position/quantity/unitPrice históricos, sem Product, comprador ou coleção de itens. Verifica sequência, produtos distintos, quantidades e preços positivos; calcula preços, subtotais e soma exatos com BigDecimal/setScale(2, UNNECESSARY), capacidade19,2 e sem double. Payment continua sem montante persistido ou recebido.
- register declara REQUIRED/READ_COMMITTED/rollbackFor Exception. Inserção pelo fragmento persist recebido, markPaid, flush e construção do DTO ocorrem no mesmo corpo transacional. Falhas de lock/lookup/projeção/persist/flush propagam sem retry ou consulta de recuperação; exceção inesperada de PK/FK não vira repetição por inferência.
- [PaymentRegistrationException.java](../../src/main/java/com/devsuperior/dscommerce/services/exceptions/PaymentRegistrationException.java): sete categorias com status e mensagens fixos do contrato, incluindo dois conflitos distintos. A futura fronteira HTTP deverá adaptar essas categorias e as falhas propagadas; esta tarefa não cria controller, cadeia de segurança ou advice.

A releitura não serializa revogação posterior à última guarda. REQUIRED pode participar de transação externa; esse chamador não recebe garantia de commit quando o corpo retorna. Flush e DTO interno tampouco demonstram commit.

### Tests first, RED e coleta corrigida

[PaymentRegistrationServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/PaymentRegistrationServiceTest.java) foi escrito antes dos corpos produtivos. As APIs reais e a primeira implementação executável continham captura de referência, guardas, aquisição da raiz, conflitos e persist/PAID/flush/DTO; ainda faltavam validações temporais e históricas. Não se usou tipo fictício, stub que lança falha intencional ou erro de compilação como RED. As guardas já satisfeitas permaneceram GREEN.

Primeira tentativa: SERVICE-T011-RED-20261022T073732805, iniciada às 07:37:41−03:00. O coletor PowerShell tinha ErrorActionPreference=Stop; um aviso nativo do ByteBuddy em stderr interrompeu o pipeline e a captura de LASTEXITCODE. Os oito XML/TXT gerados foram preservados, mas o console ficou incompleto e o exit do Wrapper não foi obtido. capture-failure.json registra essa falha de coleta; ela não é aprovação nem substitui o RED completo. Não restou processo Java antes da próxima invocação. Corrigido somente o coletor para tolerar stderr nativo e capturar o exit imediatamente após Wrapper/Tee-Object, repetiu-se a mesma seleção com fontes e asserções idênticas.

RED completo SERVICE-T011-RED-20261022T073951492: 07:39:53–07:40:14−03:00, Maven15.570s:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationServiceTest" "-Dsurefire.reportNameSuffix=SERVICE-T011-RED-20261022T073951492" test
~~~

**77 testes: 42 aprovados, 35 falhas de asserção, zero erros/ignorados, exit1, BUILD FAILURE.** Falhas: leitura histórica ausente na sequência de sucesso(1); referência nativa/ocorrência posterior e relógio avançado(2); entradas temporais inválidas(5) e null(1); raiz corrompida(3); histórico corrompido(20); caminho histórico ausente nos cenários de propagação(3). Algumas entradas inválidas atingiam indevidamente Payment/DTO e falhavam na categoria esperada, evidenciando guarda ausente, não erro de preparação. Essas 35 falhas não representam 35 requisitos independentes nem prova de concorrência.

### GREEN focal e conciliação

Após completar as regras, sem mudar o teste ou enfraquecer asserções, SERVICE-T011-GREEN-20261022T074159319: 07:42:02–07:43:26−03:00, Maven01:18:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationServiceTest" "-Dsurefire.reportNameSuffix=SERVICE-T011-GREEN-20261022T074159319" test
~~~

| Grupo JUnit efetivamente executado | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentRegistrationServiceTest$CurrentAccess | 18 | 0 | 0 | 0 |
| PaymentRegistrationServiceTest$RegistrationRules | 51 | 0 | 0 | 0 |
| PaymentRegistrationServiceTest$TransactionAndCategories | 8 | 0 | 0 | 0 |
| Total | **77** | **0** | **0** | **0** |

**BUILD SUCCESS, exit0.** O relatório externo PaymentRegistrationServiceTest registra zero casos próprios porque contém os três grupos @Nested; não é seleção vazia. reconcile.ps1/reconciliation.json conferiram quatro XML e quatro TXT por execução completa, console/exit, os 24 métodos, multiplicidades e 77 identidades únicas, sem omissões/duplicatas/failure/error/skipped no GREEN. A conferência documental inicialmente precisou corrigir a montagem do nome do TXT; nenhum Maven foi iniciado por esse ajuste.

| Critério T011 | Métodos/casos locais aprovados |
| --- | --- |
| ADMIN atual e falhas seguras | Sete métodos CurrentAccess/18 invocações: ADMIN isolado/ambos, CLIENT/sem perfil, conta ausente, sete identidades inválidas, releitura/revogação, lookup falho e quatro projeções corrompidas. |
| Referência única antes de consultas | nativeReferenceComesFirstAndCompleteHistoricalSettlementPreservesTheAggregate; inclusiveBoundsRemainValidWhenTheClockAdvancesDuringControlledLookup[1–2]; elapsedLookupTimeCannotMakeAnOccurrenceAfterTheInitialReferenceValid; nativeNanosecondsAreComparedWithoutRoundingTheReferenceUp. Clock verificado uma vez; avanço ocorre em callback controlado, sem espera física. |
| Guardas e elegibilidade antes de escrita | firstGuardFailurePreventsEveryOrderRead[1–3]; revalidationAfterRootAcquisitionPreventsWritesOnRevocationOrLookupFailure[1–3]; absentOrderIsNotFoundWithoutAnyPaymentHistoryOrWrite; existingPaymentAlwaysPrecedesIncompatibleStateAndIsNeverReusedOrRepaired[1–2]; paidWithoutPaymentIsASeparateConflictWithoutAutomaticRepair. |
| Janela e corrupção distintas | invalidOccurrenceNeverMutatesOrSilentlyRounds[1–5]; nullOccurrenceIsInvalidInputEvenForDirectServiceCalls; corruptRootIsProcessingFailureWithoutRepair[1–3]. |
| Histórico exato sem catálogo | Sucesso com linhas2×10,00+3×7,50; historicalCorruptionIsInternalFailureBeforeMutation[1–20], incluindo fração de centavo e overflow de preço/subtotal/soma; exactHistoricalMoneyAcceptsTrailingZerosAndTheDecimal19Scale2Limit[1–2]. Ausência de interação com Product/comprador e preservação do agregado no sucesso; o montante não integra o DTO. |
| Persist/PAID/flush/DTO e propagação | Sequência no cenário de sucesso; operationalFailuresPropagateUnchangedWithoutRetryOrRecoveryQueries[1–5], sem chamadas posteriores à falha; registrationDeclaresRequiredReadCommittedAndRollbackForCheckedExceptions inspeciona os atributos Spring; domainFailuresKeepDistinctFixedCategoriesAndMessages[1–7]. |

Ambiente observado: Windows/PowerShell, Java Zulu21.0.12.1+1-LTS, Maven3.9.16 selecionado pelo Wrapper existente. HEAD acima e diff recebido acrescido dos quatro Java T011. Compilação da primeira tentativa e GREEN: 99 fontes produtivos/132 de teste; não são contagens de testes. Avisos de APIs depreciadas/ByteBuddy permaneceram visíveis e não alteraram o resultado Surefire.

### Preservação e limites

Evidências ignoradas sob target/feature008-evidence/: diretório SERVICE-T011/ com manifesto/estado/diff recebido, cópias dos dois documentos anteriores, scripts de coleta/conciliação e revisão; três diretórios de execução nomeados acima com console, metadados, fontes/hash, previous-surefire e new-surefire. As execuções completas também têm exit.json e reconciliation.json; a primeira tem capture-failure.json e exit do Maven desconhecido. run-summary.json contém metadados concisos. Relatórios originais permanecem em target/surefire-reports com sufixos exclusivos.

Os 258 relatórios anteriores à T011 foram copiados na primeira tentativa; o RED completo preservou266 e o GREEN274, incluindo as tentativas desta tarefa, com hashes conferidos. O teste, a guarda e a exceção são idênticos entre RED completo e GREEN; só o serviço ganhou as regras ausentes. Os quatro fontes finais coincidem com os executados no GREEN.

Esta seleção usa objetos reais de domínio/DTO e mocks de Clock/guarda/repositories/EntityManager; a guarda real recebe projeções simuladas. Verifica regras locais, sequência e atributos transacionais. Não sobe Spring/H2/servidor, não exerce lock/refresh físico, SQL, espera concorrente, revogação em outra conexão, proxy dono da transação, rollback/commit ou HTTP. Não se afirma estado commitado a partir do objeto marcado PAID, nem segurança JWT/envelope/status HTTP a partir das categorias locais. Os contextos H2, URLs, configuração e seed recebidos não foram modificados ou acionados por esta seleção. Concorrência, commit, fronteira HTTP e checkpoints conjuntos permanecem nas tarefas próprias; o RED HTTP T006 continua histórico pendente. Não é S1-GREEN, FINAL, CI ou entrega da feature.

Critérios da **T011 atendidos** por implementação e testes de regras focais; somente T011 passa para [X] em [tasks.md](tasks.md). **T001–T011 [X], T012–T036 abertas: 11 concluídas/25 abertas.** T012+ não foi executada. Alterações desta execução: quatro Java novos acima, esta seção acrescentada e somente o marcador T011. Não houve stage, commit, push, merge ou alteração de main.

Fechamento T011: diff incremental dos dois documentos e quatro Java novos revisados; cinco links novos resolvidos, prefixo histórico de146.982 bytes e26 Java recebidos preservados por comparação/hash. git diff --check retornou exit0; git -c core.autocrlf=false diff --no-index --check contra /dev/null não apontou whitespace nos27 Java não rastreados (opção somente nesta invocação, sem alterar configuração Git; exit1 significa arquivo novo diferente). O conferidor documental foi ajustado para enumerar corretamente o array JSON de hashes e para evitar que avisos LF→CRLF fossem tratados como falha nativa pelo PowerShell; nenhuma fonte/asserção foi alterada ou execução Maven repetida por esses ajustes. final-static-review.json registra fontes idênticas ao GREEN, relatórios anteriores preservados e somente T011 marcada. extensions.yml segue ausente, sem hooks posteriores; nenhum Maven/Surefire permanece ativo. Estado final: cinco rastreados modificados e27 Java não rastreados, nada staged; branch/HEAD/main e seleção008 preservados. Nenhum impedimento remanescente da T011; limites de prova e T012–T036 permanecem explícitos acima.

## Execução T012 — exposição HTTP, segurança e erros específicos de pagamento

### Contexto e preservação

Em 2026-10-22, skill speckit-implement aplicada exclusivamente à T012. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local008 e índice vazio. Cinco rastreados recebidos modificados: specs/008-registrar-pagamento/quickstart.md, specs/008-registrar-pagamento/tasks.md, src/main/java/com/devsuperior/dscommerce/entities/Order.java, src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java e src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java.

Conferidos os 27 Java não rastreados recebidos: os 23 enumerados na seção T011 acima, mais src/main/java/com/devsuperior/dscommerce/services/PaymentAccessService.java, src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java, src/main/java/com/devsuperior/dscommerce/services/exceptions/PaymentRegistrationException.java e src/test/java/com/devsuperior/dscommerce/services/PaymentRegistrationServiceTest.java. received-file-hashes.json em target/feature008-evidence/HTTP-T012/ registra os 32 caminhos recebidos, comprimentos e SHA-256. Os 30 Java recebidos permaneceram idênticos; serviços, modelo, repositórios, DTOs, reader exclusivo, mapper anterior, testes e asserções não foram editados. O prefixo histórico recebido deste quickstart tem 161.661 bytes.

Lidos T012 integralmente, suas dependências T006/T010/T011, condições de execução e separação de T013+, plano, constituição, spec/contratos pertinentes, pesquisa, modelo e quickstart/harness. check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks retornou exit0, FEATURE_DIR absoluto008 e documentos esperados. requirements.md: 16/16, zero aberto, PASS documental, sem mudar checklist. Ignore Java existente suficiente; extensions.yml ausente antes da execução. Nenhum Maven/Surefire ativo inicialmente. A skill já havia sido lida na execução anterior e suas regras foram reaplicadas ao escopo autorizado.

Os dois diagnósticos históricos da T006 foram conferidos separadamente: 24 falhas pelo POST ausente/404 e oito pelo envelope401/403 com errors. As evidências originais e suas limitações permanecem intactas; não se repetiram a seleção T006, DTO/serviço/repositories, BASE ou suíte completa. A coleta nova preserva a correção da T011: ErrorActionPreference=Continue somente durante Wrapper/Tee-Object e LASTEXITCODE capturado imediatamente depois. Nenhuma interrupção da coleta ocorreu nestas duas invocações; avisos nativos permaneceram visíveis.

### Implementação restrita à T012

- [AdminPaymentController.java](../../src/main/java/com/devsuperior/dscommerce/controllers/AdminPaymentController.java): somente POST /admin/orders/{id}/payments, JSON de entrada/saída, Long @Positive e @Valid no corpo. Rejeita query; encaminha ID, DTO e Authentication ao serviço injetado. Sem transação externa, monta201 somente depois do retorno bem-sucedido; resultado interno nulo falha como500. Não cria Location, GET administrativo, regra financeira ou leitura alheia.
- [SecurityConfig.java](../../src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java): cadeia @Order(-1) somente /admin/orders/*/payments, sem restrição de método no matcher. Reutiliza decoder/conversor existentes; exige a guarda escalar PaymentAccessService antes da MVC. A autorização ADMIN deriva dessa guarda, inclusive se a coleção lida pelo conversor estiver antiga; CLIENT não é exigido. Falhas conhecidas de resolução e da guarda são adaptadas para o entry point específico. As cadeias0–3 e a guarda007 não foram alteradas.
- [PaymentAuthenticationEntryPoint.java](../../src/main/java/com/devsuperior/dscommerce/security/PaymentAuthenticationEntryPoint.java) e [PaymentAccessDeniedHandler.java](../../src/main/java/com/devsuperior/dscommerce/security/PaymentAccessDeniedHandler.java): envelopes fixos de quatro campos,401/403/500 distintos; somente401 traz WWW-Authenticate: Bearer. Sem errors de produtos, valores recebidos, conta, SQL ou detalhe interno. HEAD não escreve corpo.
- [PaymentRegistrationExceptionHandler.java](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java): advice limitado ao novo controller. Binding/validação local e categorias do serviço seguem status/mensagens do contrato; validação de retorno, constraints persistentes, falhas internas e de conclusão são500. Sem diagnóstico de produtos, consulta de recuperação ou retry. 405/406/415 preservam status/headers sem impor JSON.
- [GlobalExceptionHandler.java](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java): somente reconhecimento do caminho de pagamento no fallback dos erros nativos405/406/415, que podem ocorrer antes da seleção de controller. Acrescentados pattern e ramo específico; os handlers e diagnósticos anteriores foram preservados.

O fluxo produtivo chama o serviço Spring injetado e constrói201 depois de seu retorno. A comprovação física do proxy dono/commit com o serviço real continua na T013; a ordenação do código, uma anotação ou o mock usado abaixo não a substituem. Não se ampliou o contrato vigente das consultas007 ou se publicou a008 como entregue.

### Tests first e RED focal

Novos [PaymentRegistrationEndpointHttpTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationEndpointHttpTest.java) e [PaymentRegistrationEndpointApiHttpTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationEndpointApiHttpTest.java) escritos antes da implementação. As duas classes herdam17 métodos/48 invocações cada de PaymentEndpointScenarios. Não há grupos JUnit @Nested novos; a classe abstrata e o suporte HTTP não são casos omitidos. Seus nomes também são alcançados pela seleção futura PaymentRegistration*Test, sem alterar a tarefa T013.

Os testes usam servidor real em porta aleatória, login/JWT reais, decoder/conversor existentes e resolução/guarda reais por padrão. PaymentRegistrationService é substituído por @MockBean somente nesses contextos para testar a fronteira HTTP sem executar quitação. Spies de AuthenticatedAccountService/PaymentAccessService delegam às implementações recebidas, exceto nas duas falhas controladas de resolução/guarda. O teste observa ausência de transação externa no thread do request ao chegar ao mock, sem alegar proxy ou commit. Nenhum failpoint foi acrescentado à produção.

Execução HTTP-T012-RED-20261022T075834699, 07:58:37–08:00:37−03:00; duração Maven01:52:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationEndpointHttpTest,PaymentRegistrationEndpointApiHttpTest" "-Dsurefire.reportNameSuffix=HTTP-T012-RED-20261022T075834699" test
~~~

**96 testes: quatro aprovados, 92 falhas de asserção, zero erros/ignorados, exit1, BUILD FAILURE.** Cada contexto teve46 falhas e dois aprovados: HEAD não autenticado já tinha401/sem corpo, e os controles vizinhos já passavam. As demais falhas vieram da rota ausente e dos envelopes/mensagens anteriores, incluindo errors em401/403. Um404 de roteamento não comprova ausência isolada de cada regra simulada; este RED demonstra que a fronteira ainda não satisfazia as expectativas escritas. Não houve falha de compilação/preparação contada como RED.

### GREEN focal e resultados efetivos

Após implementar somente os componentes acima, sem alterar os dois testes/asserções, execução HTTP-T012-GREEN-20261022T080256515, 08:03:00–08:04:38−03:00; duração Maven01:32:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistrationEndpointHttpTest,PaymentRegistrationEndpointApiHttpTest" "-Dsurefire.reportNameSuffix=HTTP-T012-GREEN-20261022T080256515" test
~~~

| Classe/contexto | Testes | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| PaymentRegistrationEndpointHttpTest — ROOT | 48 | 0 | 0 | 0 |
| PaymentRegistrationEndpointApiHttpTest — /api | 48 | 0 | 0 | 0 |
| Total | **96** | **0** | **0** | **0** |

**BUILD SUCCESS, exit0.** reconcile.ps1/reconciliation.json conferiram console/exit, dois XML e dois TXT por execução,17 métodos herdados por classe com multiplicidades e96 identidades únicas, sem omissões/duplicatas/ignorados. Os dois Java de teste são idênticos entre RED e GREEN; fontes finais conferidas contra source-hashes-before.json. Não houve nova execução Maven depois desse GREEN.

| Métodos/casos por contexto | Invocações | Prova local da fronteira no GREEN |
| --- | ---: | --- |
| authorizedPostBindsLosslesslyAndReturnsOnlyTheThreeConfirmationFields; positiveLongMaximumReachesTheServiceWithoutIdentityLoss | 3 | ADMIN isolado/ambos, DTO recebido exato, ID int64 máximo,201 somente orderId/occurredAt/status, UTC.SSS, sem Location. Request thread sem transação externa ao chegar ao mock. |
| everyPaymentMethodRequiresAuthenticationBeforeMvc; malformedBearerUsesThePayment401EnvelopeWithoutProductErrors; nonAdminGetsThePayment403BeforeInvalidBodyAndQueryAreExamined | 10 | POST/GET/HEAD/PUT/DELETE/PATCH/OPTIONS passam primeiro pela segurança;401/403 corretos, Bearer somente401, envelopes fechados onde há corpo e nenhuma chamada ao serviço. |
| invalidPathIdentityIs400BeforeTheSettlementService; bodyReaderAndValidRejectBasicInvalidInputBeforeTheService; anyQueryParameterIsRejectedAndIsNotEchoedInTheErrorPath | 11 | ID zero/negativo/texto/overflow, corpo ausente/null/tipo/amount e query rejeitados400 sem chamar o serviço; path conserva contexto e exclui query. Não repete a matriz completa da gramática T010. |
| serviceCategoriesHaveTheirExactSafeContractResponse | 7 | Adaptação das sete categorias recebidas, inclusive401/403/404 e dois409 distintos. Causas de negócio são simuladas; não se afirma que o serviço real as produziu nesta seleção. |
| internalServiceOrResultFailuresStay500WithoutProductOrInputDiagnostics; methodReturnValidationIs500WhileInputMethodValidationIs400 | 7 | Falhas simuladas de banco/constraint/validator/mapping/resultado nulo são500; HandlerMethodValidationException simulada distingue retorno500 de entrada400. Não é falha real de flush/commit nem geração automática de violação de retorno. |
| accountResolutionFailureInTheJwtConverterIs500RatherThan401; scalarGuardFailureBeforeMvcIs500RatherThanAuthenticationFailure | 2 | Exceções controladas nos spies atravessam a segurança real como500 seguro, sem Bearer ou chamada ao serviço de registro. |
| authorizedUnsupportedMethodsRemainNative405AndExposeNoAdministrativeQuery; unsupportedRequestMediaTypeKeeps415AndItsNativeAcceptHeader; unacceptableResponseMediaTypeKeeps406WithoutForcingJson | 7 | Cinco métodos autorizados dão405/Allow POST;415 preserva Accept application/json;406 não força JSON incompatível. Sem corpo administrativo ou chamada ao serviço. |
| neighboringPublicOrderAndProductSecurityContractsKeepTheirOwnResponses | 1 | Catálogo público e GET administrativo existente200; produto não autenticado conserva401 com errors; consultas próprias401 fechado; registro de pedido sem perfil conserva sua mensagem própria. Controles pontuais, não regressão completa001–007. |
| Total por contexto | **48** | ROOT e /api executam as mesmas invocações. |

Os dois diagnósticos T006 foram enfrentados pela fronteira específica: agora o POST chega ao serviço simulado e devolve201 mínimo;401/403 usam quatro campos e mensagens de pagamento, sem errors. A T006 original não foi reexecutada ou reinterpretada como GREEN de quitação/commit.

### Ambiente, isolamento, evidências e limites

Windows/PowerShell, Java Zulu21.0.12.1+1-LTS, Wrapper selecionando Maven3.9.16; HEAD acima com alterações recebidas e diff desta T012. O GREEN compilou103 fontes produtivos e134 de teste; essas contagens não são casos executados. Avisos históricos de APIs depreciadas/ByteBuddy/Hibernate ficaram visíveis, sem interrupção da coleta ou erro Surefire.

Imports explícitos RootHttpContext/ApiHttpContext recebidos, perfil test, OSIV=false, create-drop, sem seed/import global e URLs H2 exclusivas e estáveis por contexto. GREEN: /api na porta59489 com H2 admin-product-test-6ec7e104-3c96-4bf5-b3e8-b4d2429d28e6; ROOT na59513 com H2 admin-product-test-9ac8c176-7d4e-4a9b-90b4-807b1e1c3d19. RED também teve dois bancos distintos, registrados na conciliação. Cada fixture cria contas próprias commitadas e as limpa seletivamente em AfterAll, sem limpeza global. Os helpers recebidos conferem as conexões e isolamento READ_COMMITTED; nenhuma propriedade global ou suporte foi alterado.

Há persistência real somente para preparação/limpeza das contas e consultas de autenticação/autorização/controles vizinhos; não foram criados pedidos/pagamentos de cenário. Todos os logins e requests dos cenários usam HttpClient/Tomcat. A fixture recebida constrói internamente MockMvc, mas os testes novos não chamam perform; esse bootstrap não é contado como prova HTTP simulada. A simulação está no serviço de registro e nas exceções indicadas, não no transporte.

Diretório target/feature008-evidence/HTTP-T012/ contém manifesto/estado/diff recebidos, cópias dos documentos e dos dois produtivos anteriormente limpos, hash do coletor T011 e scripts novos de coleta/conciliação/revisão. Cada diretório HTTP-T012-RED-20261022T075834699/ e HTTP-T012-GREEN-20261022T080256515/ contém run.json/exit.json, console.log, status/diff, fontes/hash, previous-surefire e new-surefire com reconciliação. RED preservou282 relatórios anteriores; GREEN preservou286, incluindo o RED, por SHA-256. Os originais continuam em target/surefire-reports com sufixos exclusivos; nenhuma evidência anterior foi sobrescrita.

Esta seleção comprova exposição/binding/filtros/serialização/erros na configuração testada e controles vizinhos descritos. Não executa o serviço de quitação real, seu proxy, Payment+PAID, janela temporal de negócio, snapshots funcionais, locks, concorrência, rollback/commit, perda de transmissão ou leitura posterior de PAID. O201 do mock não prova pagamento commitado. As matrizes completas de autorização/entrada/tempo, cenários US1 e demais regressões continuam nas tarefas próprias. Não é checkpoint S1-GREEN/C1, FINAL, CI ou entrega da008.

Critérios da **T012 atendidos** por implementação e validação focal da fronteira; somente T012 marcada em [tasks.md](tasks.md). **T001–T012 [X], T013–T036 abertas: 12 concluídas/24 abertas.** T013+ não foi executada. Alterações desta tarefa: quatro produtivos novos, dois produtivos ajustados, dois testes novos, esta seção e marcador T012. Sem stage, commit, push, merge ou alteração de main.

Fechamento T012: diff incremental dos dois documentos e oito Java desta tarefa revisados; nove links novos resolvidos. Conferidos prefixo histórico de161.661 bytes,30 Java recebidos e282 relatórios anteriores preservados; dois testes/asserções idênticos ao RED e38 Java envolvidos idênticos aos fontes do GREEN. Cadeias0–3 comparadas com a cópia recebida; GlobalExceptionHandler tem somente sete linhas acrescentadas no recorte descrito. git diff --check retornou exit0; git -c core.autocrlf=false diff --no-index --check contra /dev/null não apontou whitespace nos33 Java não rastreados (opção somente por comando, sem alteração da configuração Git; exit1 significa arquivo novo diferente). final-static-review.json registra essas conferências, coletor T011 preservado, índice vazio, ausência de processo Java/Maven e hooks posteriores inexistentes: extensions.yml segue ausente. Git final: sete rastreados modificados e33 Java não rastreados, nada staged; branch/HEAD/main e seleção008 preservados. Nenhum impedimento remanescente desta tarefa; limites da validação e T013–T036 permanecem explícitos acima.

## Execução T013 — S1-GREEN e checkpoint interno C1

### Conferência inicial e escopo

Em 2026-10-22, skill speckit-implement aplicada exclusivamente à T013. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`, seleção local008 e índice vazio. Sete rastreados recebidos modificados: specs/008-registrar-pagamento/quickstart.md, specs/008-registrar-pagamento/tasks.md, src/main/java/com/devsuperior/dscommerce/config/SecurityConfig.java, src/main/java/com/devsuperior/dscommerce/controllers/handlers/GlobalExceptionHandler.java, src/main/java/com/devsuperior/dscommerce/entities/Order.java, src/main/java/com/devsuperior/dscommerce/entities/OrderStatus.java e src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java.

Os33 Java não rastreados recebidos correspondem aos27 conferidos na T012 mais seus seis novos arquivos: AdminPaymentController.java, PaymentRegistrationExceptionHandler.java, PaymentAuthenticationEntryPoint.java, PaymentAccessDeniedHandler.java e os dois PaymentRegistrationEndpoint*HttpTest.java, nos caminhos completos enumerados naquela seção. Os40 caminhos recebidos e seus comprimentos/SHA-256 estão em target/feature008-evidence/S1-T013/received-file-hashes.json; status/diff e cópias dos dois documentos também foram preservados. Prefixo histórico recebido deste quickstart:177.686 bytes.

Lidos T013 integralmente, dependências T008–T012 e asserções T006/T007, seleção aprovada, condições do C1 e limites das histórias seguintes, sob constituição, plano, spec, pesquisa, modelo, contratos e procedimentos do quickstart/harness. check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks retornou exit0 e FEATURE_DIR absoluto008. requirements.md:16/16, zero aberto, PASS documental, sem modificar checklist. Ignore Java existente suficiente; extensions.yml ausente antes da execução; nenhum Maven/Surefire ativo inicialmente. Não houve baseline, suíte completa, seleção ampliada ou avanço de T014+.

### Complemento previsto de observação, preservando a T006

Somente [PaymentRegistrationHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRegistrationHttpIntegrationTest.java), no suporte herdado por ROOT e /api, recebeu complemento de teste. A versão recebida foi copiada antes de editar e conferida contra o SHA-256 do S1-RED original T006. Todas as asserções anteriores foram mantidas, incluindo status/campos/headers, preservação histórica, transação própria, referência única, limpeza e repetição409.

O próprio suporte recebido ainda indicava a complementação T013 das fotografias das rejeições: conservar a comparação anterior de sete tabelas e acrescentar a estrita de oito. Acrescentados também baseline de oito tabelas antes dos dados da fixture e comparação após sua limpeza seletiva, verificações locais do SQL de sucesso e exportação de templates sem valores de bind. Não se criou cenário ou componente de história posterior, nem se corrigiu produção por hipótese. Os outros37 Java recebidos, incluindo toda a produção, serviços, repositórios, reader, mapper, DTOs e demais testes/suportes, permaneceram idênticos.

As evidências T006 RED, modelo/repositórios isolados, DTO/serviço e T012 continuam com seus comandos/resultados originais. Em particular, T012 permanece HTTP real com registro simulado, sem prova de Payment persistido; seus testes agora compõem o seletor conjunto, mantendo esse alcance. Não houve novo RED artificial ou reexecução isolada redundante.

### Única execução Maven desta tarefa

Execução `S1-T013-GREEN-20261022T081608099`, metadados iniciados às08:16:10−03:00 e concluídos às08:18:46−03:00, em2026-10-22; duração Maven02:30, coleta155,623s:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRegistration*Test,PaymentPersistenceIntegrationTest" "-Dsurefire.reportNameSuffix=S1-T013-GREEN-20261022T081608099" test
~~~

**BUILD SUCCESS, exit0;371 testes, zero falhas/erros/ignorados.** Seleção literal exigida, incluindo as duas classes de fronteira T012; nenhum teste de T014+ existe/foi selecionado. Wrapper seleciona Maven3.9.16, Java Zulu21.0.12.1+1-LTS, Windows/PowerShell, Boot3.3.4/Hibernate6.5.3.Final/H2 2.2.224. HEAD acima, diff recebido mais o complemento de teste. Produção estava compilada e não foi recompilada;134 fontes de teste foram compilados, sem confundir essa contagem com testes executados. Avisos anteriores de APIs depreciadas/ByteBuddy/Hibernate ficaram visíveis e não interromperam a coleta, que preserva a correção documentada na T011.

| Classe/grupo efetivamente executado | Testes | Alcance |
| --- | ---: | --- |
| PaymentRegistrationHttpIntegrationTest — ROOT | 16 | HTTP real, serviço/proxy/JPA reais, fotografias independentes e limpeza. |
| PaymentRegistrationApiHttpIntegrationTest — /api | 16 | Mesmos11 métodos herdados e multiplicidades do ROOT. |
| PaymentRegistrationEndpointHttpTest — ROOT | 48 | Fronteira HTTP real, registro simulado da T012. |
| PaymentRegistrationEndpointApiHttpTest — /api | 48 | Mesmos17 métodos herdados da T012. |
| PaymentRegistrationDtoContractTest | 141 | Slice JSON e Bean Validation local, sem banco/HTTP. |
| PaymentRegistrationServiceTest e três grupos @Nested | 77 | CurrentAccess18, RegistrationRules51 e TransactionAndCategories8; regras/interações locais com mocks. Relatório externo tem zero casos próprios. |
| PaymentRegistrationDatabaseProbeTest | 12 | Sensibilidade do probe e limpeza seletiva, JDBC; inclui controle negativo de schema ausente. |
| PaymentPersistenceIntegrationTest | 13 | Schema/metamodelo real e integridade/round-trip JDBC independentes. |
| Total | **371** | **Oito classes principais e três grupos aninhados.** |

reconcile.ps1/reconciliation.json conferiram console/exit,11 XML e11 TXT,371 identidades únicas e os métodos/multiplicidades herdados/aninhados. As identidades foram comparadas com os relatórios históricos correspondentes: T006 RED, MODEL-T008, DTO-T010 GREEN, SERVICE-T011 GREEN e HTTP-T012 GREEN. Sem casos omitidos, renomeados, duplicados, vazios indevidamente, desabilitados ou ignorados. A aprovação atual não altera os resultados daqueles runs. Não houve falha nesta execução ou necessidade de correção/repetição Maven.

### US1 com serviço real: confirmação e estado após conclusão

| Cenário | Método T006, executado em ROOT e /api | Resultado e observação |
| --- | --- | --- |
| US1.1 | us11AdminSettlesForeignOrderWithExactlyThreeConfirmationFieldsAndHistorical4250 | ADMIN quita pedido alheio;201 fechado e uma linha Payment; total histórico2×10,00+3×7,50=42,50 preservado. |
| US1.2 | us12OwnAdminWithoutClientAndBothProfilesUseTheSameSettlementRules[1–3] | ADMIN isolado próprio e ambos os perfis próprio/alheio aceitos pelas mesmas regras. |
| US1.3 | us13CatalogPriceTwelveDoesNotReplaceHistoricalTwentyOrOrderData | Catálogo alterado para12,00 antes da janela; compra2×10,00 continua20,00, sem reprecificação ou mudança do agregado. |
| US1.4 | us14EquivalentOffsetWithExtraZerosPreservesOccurrenceRatherThanBusinessReference | Offset equivalente e zeros adicionais conservam o Instant informado; resposta UTC.SSS e ocorrência JDBC exata. |
| US1.5 | us15OccurrenceEqualToCreationIsAccepted | Igualdade inferior aceita sem deslocar criação/ocorrência. |
| US1.6 | us16OccurrenceEqualToInitialReferenceSurvivesClockAdvanceBeforeCompletion | Latch na referência capturada, avanço controlado do Clock e liberação; igualdade superior conserva a referência original. Não é experimento de espera em lock concorrente. |

Por contexto,16 invocações JUnit fizeram17 POSTs de pagamento: nove201, três400, dois401, dois403 e um409. Os nove sucessos incluem a primeira aceitação do teste de repetição; portanto são18 quitações reais aprovadas nos dois contextos e34 respostas de pagamento, sem confundir essas quantidades com371 testes. Além desses POSTs, a preparação usa login HTTP real e controles administrativos existentes.

Em cada sucesso, as asserções recebidas exigiram: confirmação com exatamente orderId/occurredAt/status PAID e sem Location; request marcado concluído; proxy produtivo original com uma entrada no corpo; ausência de transação externa na entrada/saída; transação física nova, não readOnly, conexão vinculada, autoCommit desativado e READ_COMMITTED; uma leitura de Clock de negócio. O proxy retorna antes de o controller construir201 e a observação o envolve dos dois lados do interceptor transacional real, sem substituí-lo.

Depois da resposta e da conclusão observada, o probe usa novas conexões JDBC físicas, sem EntityManager da aplicação: Payment único com a ocorrência exata e pedido PAID ficam visíveis. A lista inteira de pedidos coincide com o baseline alterando somente o status do alvo; comprador/criação e itens permanecem iguais. A comparação opaca das sete tabelas acusa exatamente tb_order, conservando catálogo/categorias/associações/contas/perfis/itens e pedido canário. Assim, a prova de sucesso combina proxy dono/conclusão e leitura independente do estado efetivado; não deriva de flush ou DTO interno isoladamente. Não se instrumentou a delegação de Connection.commit nesta tarefa.

### SQL, schema, rejeições e limpeza

Nos18 sucessos foram arquivados162 templates SQL SERVICE_BODY,81 por contexto, todos observados com transação ativa e sem valores de bind. Cada janela teve nove templates nesta execução: duas guardas escalares, dois SELECT FOR UPDATE somente da raiz, lookup de Payment, projeção escalar histórica, INSERT de Payment, SELECT adicional de itens históricos e UPDATE de Order. A leitura adicional aparece entre INSERT/UPDATE na janela de flush; a validação em cascata @Valid do agregado é sua explicação por inspeção do modelo, não um novo acesso ao preço de catálogo. Nenhuma consulta a tb_product foi observada/assertada nessas janelas.

Exemplos de templates realmente observados, sem parâmetros:

~~~sql
select o1_0.id,o1_0.buyer_id,o1_0.created_at,o1_0.status from tb_order o1_0 where o1_0.id=? for update
select oi1_0.product_id,oi1_0.position,oi1_0.quantity,oi1_0.unit_price from tb_order_item oi1_0 where oi1_0.order_id=? order by oi1_0.position
insert into tb_payment (occurred_at,order_id) values (?,?)
update tb_order set buyer_id=?,created_at=?,status=? where id=?
~~~

StatementInspector observa preparação de SQL Hibernate, não JDBC direto nem confirmação do driver. A prova dos efeitos finais vem dos snapshots após conclusão, que também mostram os campos preservados apesar das colunas presentes no UPDATE. A ordem específica do DML acima não é promessa geral nem substitui a instrumentação de falhas da US4.

PaymentPersistenceIntegrationTest verificou tb_payment somente com order_id/occurred_at, PK compartilhada, FK nomeada fk_payment_order, NOT NULL, BIGINT e TIMESTAMP WITH TIME ZONE de escala3, sem ID independente/amount. Confirmou as quatro colunas de Order e enum ampliado, rejeições de duplicidade/órfão/null/remoção referenciada no banco e três round-trips temporais exatos, incluindo anos0001/2026/9999. São provas de schema/JDBC gerado, separadas da quitação via serviço. O probe confirmou sensibilidade a Payment e às sete tabelas anteriores, incluindo limpeza seletiva após falha de asserção controlada; esse estímulo não é falha de processamento HTTP.

Nos testes reais, rejeições básicas401/403, campo amount e janela temporal400 conservam as oito tabelas, com14 comparações estritas novas. A repetição409 já exigia snapshot estrito recebido e conserva a primeira ocorrência em ambos os contextos. Após cada uma das32 invocações, a limpeza seletiva Payment→itens→pedidos→demais linhas próprias restaura o baseline completo de oito tabelas, incluindo o canário. Fotografias iguais não provam ausência de toda mutação transitória; não se atribui a elas observação universal de DML.

Os dois diagnósticos T006 foram resolvidos nesta execução com o serviço real: POST agora201 e Payment/PAID visíveis após conclusão;401/403 passam pelas mesmas asserções fechadas de quatro campos, mensagens/path e Bearer somente401, sem errors. Além desses códigos,400 temporal/estrutural e409 de repetição foram exercitados em HTTP real ROOT/Api. A adaptação404/500 e erros nativos continua coberta pelos96 testes de fronteira simulada T012 incluídos na seleção; isso não comprova causas internas/rollback reais ou amplia a matriz de US2/US4.

### Isolamento, evidências e limites do C1

Seis contextos JPA tiveram URLs H2 exclusivas, registradas em reconciliation.json: probe, persistência, dois HTTP reais e dois HTTP de fronteira simulada. ROOT real usou porta59683 e /api real59627; os simulados usaram59659/59636. O controle negativo de probe usa outro banco vazio deliberado, sem DDL de aprovação. Slice DTO e unitários de serviço não usam H2. Imports/perfis/OSIV/create-drop/seed recebidos permaneceram intactos, sem interferência entre contextos; snapshots compostos são feitos após terminar escritores, não como fotografia atômica sob concorrência.

Logins, controles e POSTs T006/T012 são HttpClient contra Tomcat real. Algumas fixtures constroem MockMvc internamente, sem perform nesses cenários; os unitários usam mocks e o slice JSON usa Bean Validation local. Só os32 testes T006 exercitam a operação de quitação real na entrada HTTP; não se atribui essa prova aos96 da T012,141 do DTO ou77 do serviço local.

Evidências ignoradas em target/feature008-evidence/S1-T013/: manifesto/estado/diff recebidos, cópias dos documentos/teste original, scripts e revisão incremental. Diretório S1-T013-GREEN-20261022T081608099/ contém run.json/exit.json, console.log, status/diff, fontes/hash, previous-surefire com290 relatórios anteriores preservados e new-surefire com22 arquivos desta execução. reconciliation.json inclui identidades e comparação histórica; real-settlement-sql.log e real-payment-statuses.log extraem as janelas SQL e34 status reais. Não houve sobrescrita de relatórios, alteração de evidências anteriores ou interrupção da coleta.

**C1/US1 aprovado no alcance desta seleção.** Não se comprovam concorrência real no mesmo pedido/pedidos distintos, refresh com entidade obsoleta, revogação durante espera de lock, falhas reais de DML/flush/commit, commit indeterminado, transmissão perdida, leitura de PAID nas consultas próprias ou regressões completas001–007. Os controles JDBC de constraints e as falhas simuladas de fronteira não substituem essas provas. C1 é incremento interno, não entrega/publicação da008, FINAL ou CI; contratos planejados e artefatos históricos continuam preservados.

Somente **T013** marcada em [tasks.md](tasks.md): **T001–T013 [X], T014–T036 abertas,13 concluídas/23 abertas**. Arquivos desta tarefa: complemento do suporte HTTP em PaymentRegistrationHttpIntegrationTest.java, esta seção acrescentada e marcador T013; nenhuma mudança produtiva ou Java novo. Sem stage, commit, push, merge ou alteração de main.

Fechamento T013: diff incremental do teste e documentos revisado; duas referências novas resolvidas. A cópia recebida do teste coincide com o hash original T006 e suas29 expressões assertThat/assertThatThrownBy foram preservadas integralmente, além das verificações herdadas de status/proxy/snapshot. Conferidos37 Java recebidos inalterados,38 fontes finais idênticos aos executados e177.686 bytes históricos do quickstart preservados; somente o marcador T013 mudou em tasks.md. git diff --check retornou exit0; git -c core.autocrlf=false diff --no-index --check contra /dev/null não apontou whitespace nos33 Java não rastreados (opção somente nesta invocação; exit1 significa arquivo novo diferente). final-static-review.json registra essas conferências e290 relatórios anteriores intactos. extensions.yml segue ausente, sem hooks posteriores; nenhum Maven/Surefire permanece ativo. Estado final: sete rastreados modificados e33 Java não rastreados, nada staged; branch/HEAD/main e seleção008 preservados. Nenhum impedimento ou falha remanescente da T013;23 tarefas posteriores seguem abertas e não houve outro Maven após o S1-GREEN.

## T014 — Autoria da matriz de autorização HTTP (2026-10-22)

### Estado inicial confirmado e escopo

Skill speckit-implement aplicada exclusivamente à T014. Conferidos raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, Git limpo, índice vazio e seleção local008. HEAD real: `2f053fef08725da34ed04f28d77b2fe52b90cec4`; último commit `feat: register full order payments`, pai `3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279`. A lista de git diff-tree foi comparada integralmente com os40 caminhos explícitos conferidos no checkpoint T003–T013: sete modificados e33 Java acrescentados, sem arquivo adicional ou ausente. Não houve divergência nem criação/repetição de commit.

Lidos T014 integral, dependência T013, condições da fase US2 e de T015–T018, constituição, AGENTS.md, harness, spec/US2/FR-001/002/012/015, plano, research R1/R2/R6/R8, modelo, [contrato POST008](contracts/payment-registration-openapi.yaml) e procedimentos/evidências pertinentes deste guia. O comando `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0 e FEATURE_DIR absoluto008. Checklist requirements.md:16/16 marcados, zero aberto, PASS documental, sem alterar marcadores. Ignore Java existente adequado; extensions.yml ausente na entrada.

**T014 é autoria de testes.** A seleção S2 está prevista para T018, com execução anterior a eventuais correções de T017; não foi antecipada nesta tarefa. Não houve Maven/Wrapper, javac, compilação, bootstrap Spring/H2, servidor, request, teste ou reconciliação de Surefire. Não se fabricou RED para guardas já presentes. Nenhum exit code Maven ou novo GREEN é alegado.

### Arquivos novos e matriz escrita, ainda não executada

- [PaymentAuthorizationHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java): classe ROOT e suporte package-private PaymentAuthorizationScenarios.
- [PaymentAuthorizationApiHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationApiHttpIntegrationTest.java): variante /api herdando a mesma matriz, com import explícito do observador008.

| Método herdado, com multiplicidade prevista por contexto | Casos escritos |
| --- | --- |
| missingInvalidExpiredAndRemovedAccountCredentialsAre401WithoutRestrictedReads — 5 | Sem token, token malformado, assinatura adulterada, expirado assinado e conta efetivamente removida após emissão:401/Bearer. Decoder real controla a validade/invalidade das credenciais de preparação. |
| clientAndNoProfileCannotSettleEvenTheirOwnPurchase — 4 | CLIENT/sem perfil, pedido próprio/alheio:403. |
| currentAdminAloneOrWithClientSettlesOwnAndForeignOrders — 4 | ADMIN isolado/ambos, pedido próprio/alheio:201 mínimo e estado independente esperado. |
| signedRoleNameAndAuthorityClaimsCannotGrantOrRemoveDatabasePermission — 4 | ADMIN/ambos com claims sem ADMIN continuam autorizados; CLIENT/sem perfil com roles/authorities/scope de ADMIN continuam negados; name alheio não troca subject. Claims deliberadamente falsos são assinados pelo encoder real de teste; não equivalem a assinatura inválida. |
| revocationAfterIssuanceDefeatsTheSameSignedAdminClaims — 4 | ADMIN/ambos passam a CLIENT/sem perfil após emissão; mesmo token com claims antigos de ADMIN:403. |
| currentGrantAllowsTheSamePreviouslyDeniedCredentialWithoutReissuingIt — 4 | CLIENT/sem perfil passam a ADMIN isolado/ambos; mesmo token primeiro403, depois201, sem reemissão ou claim atualizada. |
| revocationBetweenFilterAndServiceIsRereadBeforeAnyOrderLookup — 4 | Filtro real já autorizou ADMIN/ambos; mudança commitada para CLIENT/sem perfil antes do corpo do serviço:403, sem leitura de pedido. |
| accountRemovedBetweenFilterAndServiceBecomes401WithoutExposingThePurchase — 1 | Filtro já passou; remoção commitada da conta antes do serviço:401/Bearer, sem leitura restrita. |
| controlledRepositoryLookupFailuresStay500RatherThan401OrBusinessConflicts — 4 | Falha controlada na resolução de entidade do filtro, guarda escalar do filtro, guarda na entrada do serviço e guarda após lock:500 seguro. |
| invalidInputsStillAuthenticateAndAuthorizeBeforeTheirLocal400 — 6 | JSON malformado, corpo ausente, campo extra, query, ID não positivo e overflow. Cada caso tem controle ADMIN400 e tentativas sem token401, CLIENT403 e sem perfil403, sem alcançar serviço. Não é a matriz de gramática da T015. |
| Total escrito | **10 métodos;40 invocações previstas por classe/contexto,80 nos dois. Nenhum grupo JUnit @Nested ou caso desabilitado.** |

Essa é contagem estática dos parâmetros, não resultado JUnit. Se todos os caminhos escritos forem alcançados futuramente, são62 POSTs de pagamento por contexto (124 no total): dez201, seis400, doze401, trinta403 e quatro500. Há mais POSTs que invocações porque concessões têm duas tentativas e cada controle de entrada tem quatro. Login HTTP de preparação é separado dessas quantidades; contagens efetivas, métodos herdados e ausência de omissões/duplicatas precisam ser conferidos no futuro console/XML/TXT.

### Observação preparada e preservações

Os dois testes compõem sem editar os suportes008 recebidos: RootHttpContext/ApiHttpContext, Clock controlável, observador scoped de SQL/proxy, fixture e probe estrito de oito tabelas. Usam futuro HttpClient/Tomcat real, login/JWT/decoder/conversor reais e serviço/repositories/JPA reais; nenhum @MockBean/@SpyBean substitui a quitação. A fixture histórica constrói internamente MockMvc, mas os métodos novos não fazem perform; isso não é execução MockMvc ou HTTP nesta autoria.

Preparação escrita cria dados próprios commitados e pedido canário; histórico2×10,00 permanece20,00 apesar do catálogo12,00. Antes da fixture há baseline completo; AfterEach remove somente seus dados, Payment antes de itens/pedidos, e exige restauração de oito tabelas. Não muda URL/seed/perfil/mapper globais, nem os contextos históricos. Isolamento H2 e limpeza foram somente reutilizados no código, não exercitados de novo.

Cada rejeição tem seu status/envelope fechado status/error/message/path, mensagens fixas, path com contexto sem query, Bearer somente401 e nenhuma Location. Janelas exigem somente SELECT observado; negativas anteriores ao serviço exigem zero Clock de negócio, SQL fora do serviço e nenhuma referência a pedidos/itens/Payment/catálogo. Nas negativas de guarda do serviço, exigem proxy original, transação dona READ_COMMITTED e referência única. Só o caso de lookup após lock permite os dois SELECT FOR UPDATE de raiz, ainda sem Payment/itens/DML. StatementInspector observa preparação Hibernate; snapshots JDBC independentes após término complementam essa observação, sem prometer detectar toda mutação transitória/JDBC arbitrário.

A mudança entre filtro/serviço usa gate PROXY_ENTRY do suporte existente, antes da transação/captura de referência do corpo, sem sleep ou espera em lock. O teste confirma primeiro que o gate foi atingido, com zero Clock/leituras restritas. Mudanças de conta/perfil usam REQUIRES_NEW/READ_COMMITTED no chamador e são confirmadas pelo probe. O baseline da rejeição é tomado **após a alteração externa commitada**; Payment e dados do pedido/catálogo têm de permanecer iguais também durante essa preparação. Não se atribui a alteração externa ao POST. Gates/janelas/requests têm limites finitos e liberação/encerramento por try-with-resources antes da limpeza.

As quatro falhas de lookup usam somente interceptor temporário no repository real, restrito ao ID da conta própria e à chamada escolhida; todas as demais chamadas delegam. A asserção exige um hit e a contagem esperada de guardas; finally remove apenas esse advice, após fechar a janela. É estímulo controlado de falha de consulta, não falha física de banco, DML, flush, commit ou instrumentação antecipada de T023.

Sucessos escritos exigem201 de três campos, proxy real concluído e Payment/PAID visíveis por novas conexões JDBC, com ocorrência exata e preservação de comprador/criação/itens/histórico, contas/perfis/catálogo e canário. **Essas asserções não foram executadas nesta T014** e não acrescentam prova de persistência ou commit ao C1.

### Revisão e conclusão limitada à autoria

Preservados integralmente a implementação inicial,38 Java recebidos do checkpoint, asserções e evidências T001–T013. O prefixo histórico deste quickstart tem194.106 bytes. Permanecem históricos o S1-RED da T006 e seus dois diagnósticos; a correção de coleta T011; T012 com HTTP real e registro simulado; e C1/T013 com371 aprovados, zero falhas/erros/ignorados, exit0,18 quitações reais e limites explícitos. Esse resultado anterior não aprova os testes novos.

A revisão estática cobre conteúdo dos dois Java, referências às APIs existentes, herança/contextos, matriz, recursos/finally, diff documental, links e whitespace. Evidências dessa revisão ficam no diretório ignorado target/feature008-evidence/AUTH-T014/: estado inicial/manifesto dos40 caminhos, cópias dos documentos e hashes dos312 relatórios recebidos. Nenhum relatório ou fonte anterior foi reescrito; sem inventário global de target/.

Critérios de **autoria da T014 atendidos**; somente seu marcador passa para [X] em [tasks.md](tasks.md). **T001–T014 [X], T015–T036 abertas:14 concluídas/22 abertas.** A compilação e a comprovação executável da matriz continuam pendentes para a etapa aprovada, antes de qualquer correção T017 e no checkpoint S2/T018. Não há GREEN/C2, segurança adicional comprovada, falha funcional demonstrada ou autorização para corrigir produção nesta autoria.

T015/gramática completa, T016/perfis sob espera de lock e cache antigo, concorrência US3, falhas transacionais/transporte US4, consultas PAID e regressões completas continuam nas tarefas próprias. Não ampliar permissão de leitura ao ADMIN, alterar contratos/vigência001–007 ou declarar entrega da008. Sem stage, commit, push, merge ou alteração de main.

Fechamento T014: conteúdo completo dos dois Java e diff dos documentos revisados; review.py executado com Python externo em modo -B -I -X utf8, exit0, somente para conferência estática de arquivos/metadados. static-review.json registra38 Java recebidos inalterados,312 relatórios anteriores com SHA-256 idênticos, prefixo histórico de194.106 bytes conservado, somente marcador T014 alterado,10 métodos/40 invocações previstas por contexto, quatro links resolvidos e índice vazio. Essa revisão não compila nem executa Java ou aplicação.

Comandos de whitespace: `git diff --check` retornou exit0; `git -c core.autocrlf=false diff --no-index --check -- /dev/null <cada um dos dois Java novos>` retornou exit1 em cada arquivo por serem novos/diferentes de /dev/null, sem diagnóstico de whitespace. Avisos LF→CRLF refletem a configuração Git existente, não mudança de configuração. A revisão identificou perda de acentos no acréscimo inicial via pipeline PowerShell; somente a seção T014 foi regravada em UTF-8 com escapes Unicode no transporte, mantendo o prefixo histórico intacto. Nenhum fonte/asserção ou relatório foi afetado.

Estado final: quickstart.md/tasks.md modificados e somente os dois Java T014 não rastreados; nada staged. HEAD/branch/main e seleção008 preservados. extensions.yml ausente também no fechamento, sem hooks after_implement. Nenhuma compilação, teste, Wrapper, C1, baseline ou suíte completa executada; não há console/Surefire novo a conciliar. Não há impedimento remanescente de autoria; execução e comprovação funcional seguem pendentes nas etapas posteriores autorizadas. Sem stage, commit, push ou alteração de main.

## T015 — Autoria de entrada HTTP e validação de fronteira (2026-10-22)

### Conferência inicial, dependência e alcance

Skill speckit-implement aplicada exclusivamente à T015. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `2f053fef08725da34ed04f28d77b2fe52b90cec4`, seleção local008 e índice vazio. Recebidos somente specs/008-registrar-pagamento/quickstart.md e tasks.md modificados e os dois Java T014 não rastreados: src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java e PaymentAuthorizationApiHttpIntegrationTest.java. Seus caminhos completos, comprimentos e SHA-256 constam no manifesto desta tarefa.

Lidos T015 integralmente, dependência T013 e suas evidências, fronteiras T014/T016–T018, constituição, AGENTS.md, harness e artefatos pertinentes: spec/US2.4/5/8/9, plano, research R1–R3/R5/R6/R8, modelo e [contrato POST008](contracts/payment-registration-openapi.yaml). O comando `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0, FEATURE_DIR absoluto008 e documentos esperados. requirements.md:16/16, zero aberto, PASS documental, sem alterar checklist. Ignore Java existente adequado; extensions.yml ausente antes da autoria.

**Somente escrita/revisão estática de testes.** Nenhum Maven/Wrapper, javac, compilação, servidor, contexto Spring/H2, MockMvc, HTTP ou teste foi executado nesta T015. A fase agenda execução S2 antes de eventual correção T017 e no checkpoint T018; não foi antecipada. Os80 casos previstos na T014 continuam **não compilados/não executados**, sem novo RED/GREEN. C1, baseline e suíte completa não foram repetidos.

### Três arquivos novos

- [PaymentInputHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java): ROOT e suporte herdado PaymentInputScenarios; helper package-private PaymentInputCases contém somente os oráculos compartilhados desta T015.
- [PaymentInputApiHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputApiHttpIntegrationTest.java): /api, mesmos métodos e fontes de argumentos, import explícito do observador008.
- [PaymentBoundaryValidationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentBoundaryValidationTest.java): MVC local/Bean Validation, controller/advice/reader produtivos e serviço de registro simulado, sem filtros ou banco.

Nenhum arquivo produtivo, suporte compartilhado, teste recebido, parser/DTO/mapper anterior ou contrato foi alterado. PaymentInputCases centraliza apenas os dados dos novos testes HTTP e MockMvc; não chama predicados da implementação para calcular a expectativa. Os testes DTO anteriores permanecem íntegros.

### Matriz HTTP escrita e ainda não executada

| Método herdado em ROOT e /api | Invocações previstas por contexto |
| --- | ---: |
| rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness | 39 |
| rejectsEveryInvalidR3GrammarCalendarRangeAndPrecisionBeforeBusiness | 48 |
| allValidR3FormsReachTheRealServiceRatherThanFailingInputParsing | 24 |
| selectedPaymentRouteRejectsNonpositiveMalformedOrOutOfRangeIds | 7 |
| everyQueryParameterIsInvalidAndExcludedFromTheSafePath | 5 |
| absentPositiveIdentityIsBusiness404WithoutCreatingOrChangingAnyRow | 1 |
| paidWithoutPaymentIsIncompatibleState409AndIsNeverAutomaticallyRepaired | 1 |
| omittedOrDifferentOperationSegmentsAreRouting404NotInputValidationProof | 3 |
| positiveSelectedRouteControlConservesEquivalentOffsetAndExtraZeroMilliseconds | 1 |
| Total estático | **9 métodos;129 por contexto,258 nos dois.** |

Os39 documentos incluem corpo ausente/branco, raiz null/escalar/array, propriedade ausente/null/tipo incorreto, campos amount/buyer/buyerId/createdAt/items/price/status/orderId/reference, duplicatas inclusive nome escapado e JSON posterior/incompleto. Os48 textos inválidos cobrem a gramática R3: T/Z e segundos, espaços, calendário/bissextos, anos local/UTC, offsets e ±18:00, -00:00, leap second/hora24, fração até9 e exatidão em ms, sem coerção ou arredondamento.

As24 formas válidas são controles de parsing que usam ID positivo comprovadamente ausente: exigem **404 do serviço real**, entrada/proxy/transação observados e consulta da raiz. Isso isola sintaxe de janela temporal; anos0001/9999 e2400 não são declarados quitações elegíveis. Exatidão do Instant efetivamente ligado ao parâmetro e UTC.SSS são verificadas no complemento MockMvc para os24 pares. Um controle HTTP separado exige201 real no pedido elegível com offset equivalente/zeros adicionais, confirmação fechada, Payment/PAID por JDBC independente após conclusão e preservação histórica. O404 das24 formas válidas não é usado como prova de quitação nem confundido com ausência de roteamento.

Os sete IDs selecionados na rota cobrem zero, negativo, texto, overflow/underflow int64, decimal e exponencial; cinco queries devem400 sem ecoar query em path. Ausência de pedido tem404 de negócio seguro, sem criação. PAID sem Payment é preparado por transação JDBC própria e commitada antes do baseline; exige409 **Order is not awaiting payment**, sem reparo. Não se cria outro estado ou caso de concorrência/repetição da US3.

Os três caminhos sem a operação completa têm somente controle404 de roteamento, zero entrada de negócio e oito tabelas preservadas. Não exigem envelope de pagamento quando nenhum controller de pagamento foi selecionado, nem aprovam US2.5 por esse404. As rejeições de entrada400 na rota efetiva e o controle201 são as provas escritas correspondentes.

### Complemento MockMvc/validação, ainda não executado

PaymentBoundaryValidationTest contém10 métodos e122 invocações previstas: os mesmos39 documentos/48 textos inválidos e24 pares válidos, dois IDs para @Positive nativo, dois casos ausência/null para @Valid/@NotNull e sete casos individuais/parametrizados de resultado interno, categorias e ausência de IO de domínio. Total desta autoria: **380 invocações JUnit previstas**,258 HTTP e122 locais; não são resultados Surefire ou contagem observada de requests.

O contexto local é AnnotationConfigWebApplicationContext/@EnableWebMvc, sem auto-configuração de aplicação. Instala o reader produtivo explicitamente só no mapper MVC local, sem mudar flags globais. Usa Bean Validation real e Authentication fornecida ao request; não verifica JWT, filtro, persistência ou commit. Para cada forma válida, o mock precisa receber exatamente o ID int64 máximo, o Instant do oráculo e a mesma autenticação; confirmação tem somente três campos e UTC.SSS.

Ausência/null desserializa primeiro, depois exige violação @NotNull e rejeição MVC sem chamar serviço. IDs0/−1 precisam gerar HandlerMethodValidationException de entrada e400 antes do serviço; reflexão também confere @Positive/@Valid no controller produtivo. Parsing/validação local exige serviço sem interações e ausência de DataSource, EntityManagerFactory, repositories e Clock no contexto, além da inspeção do reader puro e do único constraint @NotNull. Isso limita IO de domínio, sem alegar instrumentação universal de rede/arquivos.

Entrada temporal subms deve400 antes do serviço; a mesma precisão inválida gerada internamente pelo construtor real de PaymentConfirmationDTO deve500 após delegação válida. Metadata real de HandlerMethodValidationException de retorno e exceção de constraint de entidade são estímulos controlados separados, também500; não provam geração automática de constraint no retorno atual ou falha física de persistência. As categorias ALREADY_REGISTERED, INCOMPATIBLE_STATE e PROCESSING_FAILURE conservam respectivamente409/409/500 e suas mensagens distintas, sem converter falha interna em repetição ou entrada400. Esse teste de adaptação não executa repetição real da US3.

### Observação, preservações e limites

HTTP futuro reutiliza os suportes008 sem edição, login/JWT/segurança reais, serviço/proxy/JPA reais e H2 exclusivo por contexto ROOT/Api. Não substitui quitação por mock nesses dois contextos. Fixture cria somente dados próprios commitados e pedido canário; limpeza seletiva é Payment→itens→pedidos→demais dados próprios, com comparação final do baseline completo. Nenhuma propriedade/seed/OSIV/mapper global foi modificada.

Rejeições locais exigem zero Clock de negócio, nenhuma fase de serviço e nenhuma leitura restrita/pagamento/catálogo no SQL observado. Rejeições de negócio exigem transação dona READ_COMMITTED, referência única e proxy falho; todos os statements observados são SELECT, com uma aquisição de raiz no404 e aquisição/refresh no409. Cada tentativa compara oito tabelas após terminar, antes da limpeza. StatementInspector captura preparação Hibernate, não JDBC direto, execução universal de DML ou commit do driver; snapshots compostos não são fotografia atômica concorrente nem detectam toda mutação transitória revertida.

Preservados40 Java recebidos, incluindo os dois da T014 ainda não executados, e o prefixo histórico de206.020 bytes do quickstart. Mantidos o RED T006 e seus dois diagnósticos, a coleta corrigida T011, a fronteira simulada T012 e C1/T013 com371 aprovados/zero falhas/erros/ignorados/exit0,18 quitações reais e seus limites. GREEN histórico não aprova arquivos novos. Nenhuma falha funcional ou impedimento de compilação/preparação foi observado por execução nesta autoria, pois tais execuções não ocorreram; compilabilidade e comportamento continuam pendentes.

Manifesto/estado inicial, cópias dos documentos, hashes dos312 relatórios anteriores e revisão estática estão em target/feature008-evidence/INPUT-T015/, ignorado pelo Git. Não foi alterado relatório anterior nem feito inventário global de target/. Somente **T015** é acrescentada às concluídas em [tasks.md](tasks.md): **T001–T015 [X], T016–T036 abertas,15 concluídas/21 abertas**. São critérios de autoria atendidos, sem C2, correção de produção ou avanço de T016+. Sem stage, commit, push, merge ou alteração de main.

Fechamento T015: conteúdo completo dos três Java e diff incremental dos documentos revisados. review.py executado com Python externo em modo -B -I -X utf8, exit0:40 Java recebidos inalterados,312 relatórios preservados por SHA-256,206.020 bytes históricos do quickstart intactos, somente marcador T015 alterado e cinco links locais resolvidos. Conferidas referências de imports locais, herança/factories e contagens estáticas39/48/24 dos provedores,129 invocações HTTP previstas por contexto e122 locais. Isso não prova compilabilidade, execução JUnit, wiring Spring ou comportamento funcional.

`git diff --check` retornou exit0. `git -c core.autocrlf=false diff --no-index --check -- /dev/null <cada um dos três Java novos>` retornou exit1 por serem arquivos novos diferentes de /dev/null, sem diagnóstico de whitespace. Avisos LF→CRLF refletem configuração Git já existente; nenhuma configuração foi alterada. A seção nova foi gravada em UTF-8 por escapes Unicode no transporte, preservando o prefixo recebido, inclusive toda a T014.

Estado final: quickstart.md/tasks.md continuam modificados; cinco Java não rastreados, sendo os dois T014 preservados e somente os três T015 novos nesta tarefa. Nada staged; HEAD/branch/main e seleção008 preservados. extensions.yml ausente no fechamento, sem hook after_implement. Nenhum Wrapper, compilação, teste, servidor, C1, baseline ou suíte completa; não há novo console/Surefire a conciliar. T014 e T015 seguem aguardando comprovação executável na etapa autorizada; nenhum impedimento de autoria ou nova falha funcional demonstrada. T016–T036 permanecem abertas. Sem stage, commit, push ou alteração de main.

## T016 — Autoria de referência temporal e perfis atuais sob espera (2026-10-22)

### Conferência inicial e limite da tarefa

Skill speckit-implement aplicada exclusivamente à T016. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `2f053fef08725da34ed04f28d77b2fe52b90cec4`, seleção local008 e índice vazio. Somente quickstart.md/tasks.md da008 estavam modificados; cinco Java recebidos não rastreados, todos em src/test/java/com/devsuperior/dscommerce/controllers/: PaymentAuthorizationHttpIntegrationTest.java, PaymentAuthorizationApiHttpIntegrationTest.java, PaymentInputHttpIntegrationTest.java, PaymentInputApiHttpIntegrationTest.java e PaymentBoundaryValidationTest.java. Caminhos completos e hashes recebidos estão no manifesto desta tarefa.

Lidos T016 integral, dependência T013/evidência C1, condições da fase US2/T017–T018 e procedimentos aprovados de referência, locks e snapshots, sob constituição, AGENTS.md, harness, spec/US1.5–6/US2.3/6–7, plano, research R2/R3/R5/R8, modelo e contrato008. `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou exit0 e FEATURE_DIR absoluto008. requirements.md:16/16, zero aberto, PASS documental, sem modificar checklist. Ignore Java existente adequado; extensions.yml ausente antes da autoria.

A T016 descreve **criar o teste**; a tabela de seleções identifica S2/T018 e exige execução antes de eventuais correções T017. Esta entrega limita-se à autoria/revisão, sem executar esse checkpoint posterior ou repetir o núcleo C1. Nenhum Wrapper, javac, compilação, contexto, HTTP, banco ou teste foi executado. Não há contagem real JUnit, console/Surefire ou exit code Maven novo; tampouco diagnóstico funcional ou impedimento de compilação observado por execução. As80 invocações previstas T014 e380 T015 continuam **não compiladas/não executadas**. Nenhum GREEN foi presumido ou RED fabricado.

### Arquivo novo e casos escritos

Novo [PaymentTemporalAndCurrentRolesIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java), no pacote services previsto na tarefa. Usa RootHttpContext e herda o suporte HTTP008 público, com import explícito do observador existente. Não cria alias Api, configuração produtiva, novo suporte compartilhado ou substituto de serviço/repository.

| Método / parâmetro escrito | Expectativa futura |
| --- | --- |
| initialReferenceAndInclusiveWindowSurviveARealRootLockWait — LOWER_EQUAL | Ocorrência igual à criação,201. |
| Mesmo método — BEFORE_CREATION | Criação−1ms,400 de janela de negócio após a espera. |
| Mesmo método — UPPER_EQUAL | Ocorrência igual à referência inicial representável,201, mesmo após avanço do Clock. |
| Mesmo método — AFTER_REFERENCE | Referência inicial+1ms,400 apesar de o Clock posterior já admitir o instante. |
| Mesmo método — NATIVE_FLOOR | Referência .123000001 e ocorrência .123,201; nanos lidos do Clock permanecem observados. |
| Mesmo método — NATIVE_CEILING | Referência .123999999 e ocorrência .124, apenas1ns posterior,400; sem arredondar a referência para cima. |
| scalarGuardAfterTheWaitRejectsRevocationDespiteInitializedManagedRoles — ADMIN | ADMIN removido por outra transação durante a espera,403; coleção gerenciada ainda contém ADMIN. |
| Mesmo método — BOTH | Remover somente ADMIN conserva CLIENT no banco,403, apesar de coleção gerenciada antiga com ambos. |
| Total estático | **Dois métodos parametrizados, oito invocações previstas, somente ROOT.** Enum e AcquisitionState são auxiliares, não grupos JUnit @Nested. |

Esses números são de autoria, não testes aprovados ou requests realizados. Se todos os caminhos forem alcançados futuramente, esperam-se três201, três400 e dois403, um POST de pagamento por invocação e login real separado. Autenticação401, pedido ausente404, conflitos409 e falhas internas500 não recebem prova nova nesta tarefa. Timeout, falha de infraestrutura ou instrumentação deve falhar o experimento, nunca ser convertido em rejeição de negócio bem-sucedida.

### Coordenação escrita, antes de qualquer prova executável

O código prepara contas/perfis/pedidos próprios commitados, alvo e canário, histórico2×10,00=20,00 com catálogo12,00. O holder obtém conexão física independente READ_COMMITTED pelo suporte008, adquire SELECT FOR UPDATE somente da raiz alvo e mantém a transação aberta. O request usa HttpClient/Tomcat, JWT e cadeia de segurança reais, proxy produtivo original e JPA reais; nenhum mock ou bean falso.

Gate REFERENCE_READ exige uma leitura de negócio, valor nativo esperado, nenhuma aquisição de pedido e nenhum SQL do corpo até então. A leitura do Clock já ocorreu no gate, mas o teste não afirma que a atribuição local do serviço já terminou durante essa pausa. Após liberar esse gate, ORDER_LOCK_SQL identifica preparação do primeiro lock; isso sozinho não é prova de bloqueio físico.

Um interceptor temporário no repository real, restrito ao ID alvo/método de aquisição, captura session_id() da conexão vinculada ao serviço e delega sem alterar o resultado. Com o gate de SQL liberado, o teste exige metadados H2 de SESSION_STATE=BLOCKED, BLOCKER_ID igual à sessão do holder e statement de tb_order/FOR UPDATE. IDs devem ser distintos; isDone=false apenas complementa a prova. A técnica segue a [observação de sessões H2 do harness de produtos](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductConcurrencyIntegrationTest.java), sem alterar esse arquivo.

Só depois desse bloqueio comprovado pelo futuro teste o Clock avança cinco minutos. A referência observada e a contagem de uma leitura devem continuar iguais. O holder não altera linhas e termina por rollback para liberar a raiz; somente depois o teste aguarda resposta, término do filtro e proxy, exige transação dona READ_COMMITTED e compara estado por novas conexões JDBC.

Nos dois cenários de perfis, o interceptor pré-carrega UserAccount/roles **dentro do mesmo contexto JPA da transação de serviço**, antes de tentar o lock. Exige entidade gerenciada e coleção inicializada com ADMIN. Enquanto o serviço está bloqueado, outra conexão/transação independente remove somente o ADMIN da conta própria e commita. Snapshot exige alteração exclusiva de tb_user_account_role, com pedidos/itens/Payment/catálogo/contas preservados. Esse estado já alterado é o baseline da rejeição, sem atribuir a mudança externa ao POST.

Depois de lock/refresh, ainda antes da segunda guarda, a instrumentação exige a mesma entidade gerenciada com roles antigas contendo ADMIN. O serviço deve rejeitar403 usando a guarda escalar atual. SQL do corpo precisa mostrar duas guardas escalares, uma antes e outra depois dos dois locks de raiz; a consulta extra de pré-carga de entidade não é contada como guarda. A rejeição não pode consultar Payment/histórico nem produzir DML; snapshot final deve coincidir com o baseline após a revogação. Não se promete serializar revogação depois da última leitura de permissão.

### Recursos, observação e limites

Gates/janela/conexões/statements/resultsets usam try-with-resources. O holder fecha antes da janela aguardar workers em falha; o finally remove somente o advice local depois de fechar a janela. Conexão vinculada é obtida/liberada por DataSourceUtils, sem fechar diretamente a transação de aplicação. O writer externo é commitado/fechado antes do snapshot correspondente; cleanup da fixture ocorre após concluir janelas/threads.

O polling verifica metadados reais, com prazo8s, queryTimeout2s e cadência5ms por LockSupport.parkNanos, conforme o padrão existente. A cadência não constitui evidência de ordenação ou espera; só os metadados satisfazem a asserção. Gates/Futures usam limite10s, statements auxiliares timeout10s e Hikari conserva o lock_timeout20s do contexto008. Não há Thread.sleep, wait ilimitado, NOWAIT/SKIP LOCKED, mutex de negócio, timeout global novo ou alteração de URL/seed/perfil/OSIV/mapper.

Sucessos escritos exigem confirmação fechada201 com ocorrência UTC.SSS, sem Location ou WWW-Authenticate; Payment único e PAID visíveis após retorno/conclusão, com comprador/criação/itens/histórico e demais tabelas/canário preservados. Rejeições400/403 têm mensagens próprias e quatro campos seguros, proxy falho, somente SELECT observado e snapshots completos iguais ao baseline apropriado. Flush/DTO não são usados como prova de commit.

A captura nativa e o controle .123999999→.124 confrontam arredondamento para cima e recaptura após espera. Com entrada restrita a milissegundos, apenas esses resultados de fronteira não distinguiriam matematicamente uma truncagem interna para baixo; a exigência de não truncar também foi conferida por inspeção do serviço recebido, que mantém diretamente a variável retornada por clock.instant(). Não se altera esse código ou enfraquece a asserção da referência exata.

StatementInspector observa preparação Hibernate, não JDBC direto, execução universal de DML ou commit do driver. Leituras de sessão/holder/writer são JDBC de instrumentação separado. Snapshots compostos não são atômicos sob escritores arbitrários; nesta coordenação o writer externo termina e o serviço permanece bloqueado até o baseline ser obtido. Nenhuma dessas observações foi executada agora, nem comprova outro banco, durabilidade, concorrência entre duas quitações, falhas de gravação/commit ou transporte; essas provas continuam nas tarefas próprias.

### Preservação e conclusão limitada à autoria

Preservados43 Java recebidos, incluindo todos os cinco não rastreados de T014/T015, e217.995 bytes históricos do quickstart. Permanecem intactos asserções, suporte e evidências recebidos: RED T006 e seus diagnósticos, correção de coleta T011, T012 com registro simulado e C1/T013 histórico de371 aprovados/zero falhas/erros/ignorados/exit0 e18 quitações reais, com todos os seus limites. C1 não aprova o novo teste.

target/feature008-evidence/TEMPORAL-T016/ guarda manifesto/estado inicial dos45 caminhos recebidos, cópias dos documentos, hashes dos312 relatórios anteriores e revisão estática, sem inventário global de target/. Não há relatório de teste novo nem reescrita de evidência anterior.

Critérios de **autoria da T016 atendidos**; somente seu marcador passa para [X] em [tasks.md](tasks.md). **T001–T016 [X], T017–T036 abertas:16 concluídas/20 abertas.** As comprovações executáveis T014–T016 continuam pendentes na etapa S2 aprovada, antes de qualquer correção T017 e no checkpoint T018. Esta tarefa não é aprovação C2 ou checkpoint diagnóstico executado; não há causa funcional nova a corrigir. Sem stage, commit, push, merge ou alteração de main.

Fechamento T016: conteúdo completo do Java novo, APIs/referências locais, coordenação de recursos e diff incremental dos documentos revisados. review.py executado com Python externo -B -I -X utf8, exit0; static-review.json registra43 Java recebidos e312 relatórios preservados por SHA-256,217.995 bytes históricos intactos, somente marcador T016 alterado, três links existentes e nenhuma alteração de HEAD/branch/main/seleção ou índice. Conferidas estaticamente duas factories de parâmetros, seis casos temporais e dois perfis, sem testes herdados adicionais, @Nested JUnit, desabilitados ou assumptions. As oito invocações são previstas; **zero execuções Java nesta tarefa**, sem alegar compilabilidade ou aprovação funcional.

`git diff --check` retornou exit0. `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java` retornou exit1 pelo arquivo novo diferente de /dev/null, sem diagnóstico de whitespace. Avisos LF→CRLF são da configuração existente. A seção acrescentada foi gravada em UTF-8, com preservação byte a byte do prefixo recebido.

Estado final: quickstart.md/tasks.md modificados e seis Java não rastreados — os cinco T014/T015 preservados e somente PaymentTemporalAndCurrentRolesIntegrationTest.java novo, no diretório services previsto. Nada staged. extensions.yml ausente também no fechamento, sem hooks after_implement. Não houve Wrapper, compilação, teste ou checkpoint diagnóstico executável, nem repetição de C1/BASE/suíte completa. Não há console/Surefire novo, falha funcional demonstrada ou correção antecipada de T017. Sem impedimento remanescente de autoria; comprovações executáveis continuam pendentes. Sem stage, commit, push ou alteração de main.

## T017 — Confronto diagnóstico de US2 com a produção (2026-10-22)

### Estado recebido e escopo efetivo

Aplicada speckit-implement exclusivamente à [T017](tasks.md). Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `2f053fef08725da34ed04f28d77b2fe52b90cec4`, seleção local008 e índice vazio. Somente este quickstart e tasks.md da008 estavam modificados. Seis Java não rastreados recebidos, com caminhos conferidos pelo Git:

- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentBoundaryValidationTest.java`
- `src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java`

Lidos T017 integral, dependências T014–T016, condições T018 e seleção aprovada, constituição, AGENTS.md/harness e artefatos pertinentes: spec/US2, plano, research, modelo, contrato POST008/precedência, procedimentos e evidências anteriores deste guia. Conferidos os seis testes completos, componentes atribuídos à T017, controller, fragmento de lock, categorias e segurança de T012. Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` executados com exit0, FEATURE_DIR absoluto008. Checklist requirements.md:16/16, zero aberto, PASS documental; ignore Java adequado e extensions.yml ausente.

A dependência da T017 exige testes escritos/executados antes de correção; a tabela S2 exige testar antes de T017. Por isso foi executada **uma seleção S2 diagnóstica anterior a qualquer alteração**, incluindo o núcleo expressamente previsto. Isso não encerra T018/C2, não é repetição autônoma de C1 e não autoriza T018+. As notas T014–T016 de autoria sem compilação/execução permanecem históricas intactas: esta é a primeira execução desses seis arquivos, separada das revisões estáticas anteriores.

### Comando, ambiente, coleta e resultado observado

Windows/PowerShell5.1, Java21.0.12.1 Zulu, Wrapper configurado para Maven3.9.16; nenhuma dependência, propriedade global, seed, URL ou suporte alterado. Não havia processo Java/Maven ativo na conferência anterior. Uma invocação Maven por vez, sem baseline, suíte completa ou seleções adicionais:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentAuthorization*Test,PaymentInput*Test,PaymentBoundaryValidationTest,PaymentTemporalAndCurrentRolesIntegrationTest,PaymentRegistration*Test,PaymentPersistenceIntegrationTest" "-Dsurefire.reportNameSuffix=S2-T017-DIAG-20261022T094650162" test
~~~

Coletor `target/feature008-evidence/run-t017.ps1`: manifesto e cópias dos46 caminhos recebidos (40 do checkpoint mais seis Java novos), estado/diff inicial e hashes em `target/feature008-evidence/REVIEW-T017/`. Copiados os312 relatórios anteriores antes do Wrapper; mantidos também em seus caminhos originais. Evidência nova em `target/feature008-evidence/S2-T017-DIAG-20261022T094650162/`: run.json, console.txt, exit.json, previous-surefire-reports/, surefire-reports/, analysis.json, coverage.json e diagnosis.json. Diretórios ignorados pelo Git, sem inventário global de target/.

O console foi coletado com stderr mesclado por Tee-Object e ErrorActionPreference=Continue durante a chamada nativa. LASTEXITCODE capturado imediatamente após a pipeline, antes de qualquer outra chamada nativa; depois restaurado Stop. Mantida a correção de coleta documentada em T011. Console em UTF-16 do Tee-Object/PowerShell5.1; JSON em UTF-8. Avisos de agente/VM e depreciação não foram tratados como falhas funcionais.

Execução iniciada em2026-10-22T09:46:50−03:00 e encerrada pelo coletor às09:52:29−03:00,338,092s; console Maven informa05:31min e término09:52:28−03:00. Compilação de140 fontes de teste concluída; Surefire/JUnit executado. **BUILD FAILURE, exit1:839 invocações,814 aprovadas,25 falhas,0 erros,0 ignoradas.** Não houve impedimento de compilação ou preparação nem seleção vazia.

| Classe/grupo efetivamente executado | Invocações | Aprovadas | Falhas |
| --- | ---: | ---: | ---: |
| PaymentAuthorizationHttpIntegrationTest — ROOT | 40 | 30 | 10 |
| PaymentAuthorizationApiHttpIntegrationTest — /api | 40 | 30 | 10 |
| PaymentInputHttpIntegrationTest — ROOT | 129 | 128 | 1 |
| PaymentInputApiHttpIntegrationTest — /api | 129 | 128 | 1 |
| PaymentBoundaryValidationTest — MVC local | 122 | 122 | 0 |
| PaymentTemporalAndCurrentRolesIntegrationTest — ROOT | 8 | 5 | 3 |
| PaymentRegistrationHttpIntegrationTest e Api — serviço/persistência reais | 32 | 32 | 0 |
| PaymentRegistrationEndpointHttpTest e Api — HTTP real, serviço simulado | 96 | 96 | 0 |
| PaymentRegistrationDtoContractTest | 141 | 141 | 0 |
| PaymentPersistenceIntegrationTest | 13 | 13 | 0 |
| PaymentRegistrationDatabaseProbeTest | 12 | 12 | 0 |
| PaymentRegistrationServiceTest$CurrentAccess | 18 | 18 | 0 |
| PaymentRegistrationServiceTest$RegistrationRules | 51 | 51 | 0 |
| PaymentRegistrationServiceTest$TransactionAndCategories | 8 | 8 | 0 |
| PaymentRegistrationServiceTest — container externo | 0 | 0 | 0 |
| Total | 839 | 814 | 25 |

Console,17 XML e17 TXT próprios conciliados;839 identidades classe/método/invocação únicas, sem duplicatas ou omissões da seleção. Conferidos10 métodos herdados de autorização por contexto, nove de entrada por contexto, dez métodos locais e dois temporais; matrizes39/48/24 atingiram129 HTTP por contexto e122 locais. No núcleo,11 métodos herdados de integração real por contexto e17 da fronteira com serviço simulado; os três grupos @Nested somam77, sem contar novamente o container externo de zero casos. Comparadas as371 identidades do núcleo com os relatórios C1: correspondência exata, todas aprovadas nesta execução. Contagens de invocações não são contagem presumida de requests, pois alguns casos fazem várias chamadas.

### Diagnóstico das25 falhas, sem alteração dos testes

Todas as falhas são AssertionFailedError no oráculo de confirmação201, depois de a asserção de status201 ter passado. Cada par JSON esperado/recebido exibido é textualmente igual e contém exatamente orderId/occurredAt/status. O ponto de falha está nos testes recebidos:

| Local | Falhas | Alcance interrompido |
| --- | ---: | --- |
| [PaymentAuthorizationHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java), assertAccepted, linha366 | 20 | Dez controles positivos por contexto: quatro ADMIN próprio/alheio, quatro concessões atuais e dois claims enganosos com ADMIN real. |
| [PaymentInputHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java), controle positivo, linha196 | 2 | Um controle de offset/zeros adicionais por contexto. |
| [PaymentTemporalAndCurrentRolesIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java), assertSuccess, linha395 | 3 | LOWER_EQUAL, UPPER_EQUAL e NATIVE_FLOOR após espera real. |

Esses três locais constroem o esperado com ObjectNode.put("orderId", long), produzindo LongNode; readTree da resposta com IDs pequenos produz IntNode no mapper existente. A igualdade de ObjectNode delega aos filhos e IntNode/LongNode não são iguais entre si. Todos os25 IDs observados são positivos e cabem em int32; o contrato int64 admite esses valores e não impõe uma classe Java ao consumidor JSON. Isso explica a igualdade textual com falha de equals, sem demonstrar divergência contratual da resposta.

Diagnóstico apoiado por relatórios, fontes locais e inspeção de bytecode do **Jackson2.17.2 realmente presente no classpath Surefire**, sem alterar flags do mapper nem executar teste adicional:

~~~powershell
$jackson = 'C:/Users/ronal/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar'
javap -classpath $jackson -c com.fasterxml.jackson.databind.node.IntNode com.fasterxml.jackson.databind.node.LongNode com.fasterxml.jackson.databind.node.JsonNodeFactory com.fasterxml.jackson.databind.node.ObjectNode com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer
javap -classpath $jackson -p -c com.fasterxml.jackson.databind.deser.std.BaseNodeDeserializer
~~~

Ambas as inspeções retornaram exit0; saídas preservadas em jackson-bytecode.txt e jackson-reader-bytecode.txt. Não são testes de aplicação nem prova runtime adicional. analyze.py, coverage.py e diagnose.py foram executados com Python externo `-B -I -X utf8`, exit0, apenas para conciliar relatórios, fontes/hashes, pares de JSON e marcos do console; não substituem JUnit.

Classificação: **falhas de asserção do oráculo recebido, sem RED funcional demonstrado na produção**. Não são erro de compilação, setup ou indisponibilidade de banco. Os25 casos continuam falhos; não foram omitidos, reinterpretados como GREEN ou corrigidos por mudança produtiva/mapper global. Como o pedido exige preservar testes/asserções e T017 atribui componentes produtivos específicos, os três oráculos ficaram byte-preservados. Corrigi-los exige recorte autorizado próprio, mantendo campos fechados, número integral e valor exato do ID, instante exato, status, headers e todas as verificações posteriores; depois será necessária execução justificada. Nenhum rerun nesta tarefa.

### Confronto da produção e alcance das provas obtidas

PaymentRegistrationService conserva clock.instant nativo como primeira instrução, uma única variável local durante guardas/consultas/espera, sem truncar ou recapturar. PaymentAccessService consulta conta/perfis escalares atuais antes de pedido e depois de find+refresh, sem confiar em roles gerenciadas/claims. Permanecem existência de pagamento antes de estado incompatível, janela inclusiva, histórico exato e mutações somente após elegibilidade. Falhas internas não viram entrada400 nem repetição409; não há lookup em Bean Validation.

Reader exclusivo conserva gramática R3 e validação pura; advice scoped distingue validação de entrada400 de retorno/entidade500. SecurityConfig e handlers de T012 preservam ADMIN atual antes de MVC e categorias401/403/500, sem ampliar permissões ou matcher de consultas/produtos. Não foi demonstrada lacuna que justifique modificar esses componentes. Revisão estática não aprova regressões001–007; o fechamento específico de T018 e as tarefas posteriores continuam necessários.

Passaram as negativas de autorização/claims/perfis, mudanças entre filtro/serviço, lookup controlado500 nos quatro pontos e precedência de segurança antes de entrada inválida em ROOT e /api. Passaram as rejeições locais de documentos/gramática/ID/query,404 de negócio e409 PAID sem Payment, sem reparo, nos dois contextos. Controles de rota omitida permaneceram prova de roteamento404, não substituto de validação. Os122 casos MockMvc locais usam serviço simulado e validadores/reader/MVC reais; não provam JWT, socket, persistência ou commit. As categorias locais repetição409/incompatibilidade409/falha interna500 passaram sem tornar esse estímulo controlado prova de repetição real/concorrência da US3.

Os oito casos T016 registraram bloqueio físico H2 com sessões distintas e blocker correspondente, antes do avanço do Clock. As três rejeições temporais BEFORE_CREATION, AFTER_REFERENCE e NATIVE_CEILING passaram integralmente; a última conserva referência .123999999 e rejeita .124. Os dois casos ADMIN/BOTH revogados durante a espera passaram integralmente: roles gerenciadas antigas pré-carregadas, mudança externa commitada separada do baseline e guarda escalar atual403 sem mutação causada pela tentativa. Esses cinco casos registraram T016_RESULT após snapshots independentes. Nos três controles201, passaram até a comparação de confirmação a referência única, bloqueio, guardas e status; as asserções posteriores à linha395 não foram alcançadas. Não atribuir a eles prova completa de snapshots/histórico após a transação.

O núcleo manteve371 aprovações, com18 T013_SUCCESS (nove por contexto) exigindo transação dona READ_COMMITTED, uma leitura de negócio e Payment/PAID por JDBC independente, além da preservação histórica. Essa é evidência atual limitada aos mesmos casos, sem substituir o C1 histórico ou reparar os25 testes. Os96 casos de endpoint com registro simulado permanecem HTTP real com serviço mock, sem prova de persistência. Flush e DTO interno continuam distintos de commit.

Sem prova nova de duas quitações concorrentes no mesmo pedido, independência de pedidos, falhas físicas de DML/flush/commit, resultado indeterminado ou perda de transporte. A prova H2 de espera/revogação não comprova outro banco nem serializa revogação posterior à última guarda. Observação Hibernate é de preparação SQL; snapshots não detectam toda mutação transitória revertida. Permanecem os limites de nanos descritos na T016 e as tarefas próprias de US3–US5/FINAL.

### Estado da tarefa e preservações

**T017 permanece aberta**, pois seu GREEN exigido não foi obtido. Sem correção produtiva demonstrada; os oráculos estão fora dos componentes atribuídos e foram preservados por exigência do pedido. **T001–T016 [X]; T017–T036 abertas,16 concluídas/20 abertas.** tasks.md não recebeu nenhuma edição nesta tarefa. T018/C2 não executada como fechamento; nenhuma antecipação de T018+.

Única edição de arquivo versionável nesta execução: acréscimo desta seção ao quickstart. Produção, suporte,44 Java recebidos (incluindo os seis não rastreados), tasks.md recebido e312 relatórios anteriores preservados por SHA-256; histórico do quickstart preservado como prefixo byte a byte. RED T006/dois diagnósticos, correção de coleta T011, T012 simulada e C1 histórico371/18 quitações mantidos. Nada staged; sem commit, push, merge ou alteração de main.

Fechamento documental T017: revisados diff, conteúdo completo dos seis Java não rastreados e referências. review.py executado com Python externo `-B -I -X utf8`, exit0:44 Java recebidos, tasks.md e312 relatórios preservados,230.843 bytes históricos do quickstart intactos,115 links locais resolvidos, HEAD/branch/main/seleção e índice conferidos. `git diff --check` retornou exit0. Para cada um dos seis Java novos, `git -c core.autocrlf=false diff --no-index --check -- /dev/null <caminho>` não produziu diagnóstico de whitespace; exit1 corresponde ao arquivo novo diferente de /dev/null. Avisos LF→CRLF são da configuração existente, preservada. extensions.yml ausente também ao final, sem hook after_implement.

Git final mantém os mesmos oito caminhos do estado recebido: quickstart.md/tasks.md modificados e seis Java não rastreados, nada staged. Nesta tarefa somente quickstart recebeu conteúdo novo; a única pendência impeditiva de GREEN observada são os três oráculos de confirmação responsáveis pelas25 falhas. Nenhuma alteração ou execução adicional de produção/testes, T018+, stage, commit, push ou main.

## T017 — Retomada autorizada: representação inteira dos três oráculos (2026-10-22)

Retomada exclusiva da [T017](tasks.md), usando speckit-implement. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `2f053fef08725da34ed04f28d77b2fe52b90cec4`, seleção local008 e índice vazio. Estado recebido: somente quickstart.md/tasks.md modificados e os mesmos seis Java T014–T016 não rastreados. Pré-requisitos do Spec Kit executados com `-Json -RequireTasks -IncludeTasks`, exit0 e FEATURE_DIR absoluto008; requirements.md16/16, zero aberto, PASS documental. Constituição, T017/dependências/limite T018, plano, pesquisa, modelo, contrato POST e harness confrontados; ignore Java adequado, sem alteração de configuração.

### Causa confirmada antes da edição e correção restrita

Nova conciliação somente de leitura dos17 XML e console do S2 diagnóstico `S2-T017-DIAG-20261022T094650162`: **839 casos únicos,814 aprovados,25 falhas,0 erros/ignorados,BUILD FAILURE/exit1**. O histórico anterior continua válido como registro reprovado; não houve nova execução S2/BASE/suíte completa. As25 falhas atingiam, após status201 aprovado, exatamente:

| Oráculo recebido | Falhas S2 | Correção nesta retomada |
| --- | ---: | --- |
| [PaymentAuthorizationHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java), `PaymentAuthorizationScenarios.assertAccepted`, antiga linha366 | 20, ROOT+/api | Uma comparação estrutural. |
| [PaymentInputHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java), `positiveSelectedRouteControlConservesEquivalentOffsetAndExtraZeroMilliseconds`, antiga linha196 | 2, ROOT+/api | Uma comparação estrutural. |
| [PaymentTemporalAndCurrentRolesIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java), `assertSuccess`, antiga linha395 | 3, ROOT | Uma comparação estrutural. |

`ObjectNode.put(orderId,long)` produzia LongNode; readTree da resposta com ID pequeno produzia IntNode. A igualdade dos nós depende da classe numérica, embora os25 pares fossem textualmente iguais. A única alteração de cada Java é um comentário e `isEqualTo(mapper.readTree(expected.toString()))` no lugar de `isEqualTo(expected)`: o esperado inteiro é serializado sem perda e lido pelo mesmo parser da resposta. Não se converte o ID recebido para comparar nem se reconfigura o mapper. Valores pequenos recebem IntNode de ambos os lados; grandes conservam LongNode e todos os bits de int64. O esperado continua um número JSON inteiro: strings, decimais inclusive `.0`, expoentes, IDs vizinhos e outras estruturas não são iguais. Permanecem a asserção explícita `isIntegralNumber`, todas as chaves/valores esperados e a comparação da árvore inteira, sem remover campos. Ordem e verificações de itens históricos permanecem intactas.

Nenhuma mudança em produção, mapper global, coerções da aplicação, suporte compartilhado, aliasesApi ou PaymentBoundaryValidationTest. Sem conversão por ponto flutuante, tolerância, truncamento ou alteração da ocorrência. Headers, autorização, referência única, janelas inclusivas, SQL, proxy, snapshots e limpeza posteriores à comparação foram preservados integralmente.

### Validações focais novas, sequenciais e com relatórios próprios

Windows/PowerShell5.1; Java21.0.12.1 Zulu; Wrapper/Maven3.9.16. Coletor `target/feature008-evidence/RESUME-T017/run-focal.ps1`; uma invocação Maven por vez, copiando console e todo `target/surefire-reports/` para o diretório próprio antes da próxima. Sufixos exclusivos conservaram os relatórios reprovados originais. `run.json` contém comando/ambiente/HEAD/branch/main/início; `exit.json` registra término/duração/exit; também foram guardados estado/diff e hashes dos três Java usados. Console via Tee-Object em UTF-16, stderr mesclado e LASTEXITCODE capturado imediatamente; JSON UTF-8. Advertências de agente/VM/depreciação não foram tratadas como falhas funcionais.

Comandos efetivamente executados, nesta ordem:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentAuthorizationHttpIntegrationTest,PaymentAuthorizationApiHttpIntegrationTest" "-Dsurefire.reportNameSuffix=T017-AUTH-20261022T161026931" test
.\mvnw.cmd "-Dtest=PaymentInputHttpIntegrationTest,PaymentInputApiHttpIntegrationTest,PaymentBoundaryValidationTest,PaymentRegistrationEndpointHttpTest#positiveLongMaximumReachesTheServiceWithoutIdentityLoss,PaymentRegistrationEndpointApiHttpTest#positiveLongMaximumReachesTheServiceWithoutIdentityLoss" "-Dsurefire.reportNameSuffix=T017-INPUT-20261022T161443116" test
.\mvnw.cmd "-Dtest=PaymentTemporalAndCurrentRolesIntegrationTest" "-Dsurefire.reportNameSuffix=T017-TEMPORAL-20261022T161819093" test
~~~

| Execução/diretório sob `target/feature008-evidence/` | Casos aprovados | Contextos/controles efetivamente executados | XML/TXT próprios | Resultado |
| --- | ---: | --- | ---: | --- |
| `T017-AUTH-20261022T161026931` | 80/80 | 40 por ROOT e /api; dez métodos herdados por contexto, incluindo positivos, credenciais/claims/perfis atuais, mudanças filtro→serviço, lookup500 e segurança antes da MVC. | 2/2 | BUILD SUCCESS,exit0;0 falhas/erros/ignorados. |
| `T017-INPUT-20261022T161443116` | 382/382 | 129 HTTP por ROOT e /api;122 MVC locais;somente um controle HTTP de Long.MAX_VALUE por contexto com serviço simulado. Matrizes39 documentos/48 ocorrências inválidas/24 válidas e categorias400/404/409/500 mantidas. | 5/5 | BUILD SUCCESS,exit0;0 falhas/erros/ignorados. |
| `T017-TEMPORAL-20261022T161819093` | 8/8 | ROOT;seis casos temporais e dois de revogação ADMIN/BOTH durante espera física H2. | 1/1 | BUILD SUCCESS,exit0;0 falhas/erros/ignorados. |
| Total focal | **470/470** | Identidades únicas conferidas;os25 casos reprovados originais estão incluídos e passaram. | 8/8 | **0 falhas,0 erros,0 ignorados.** |

- `AUTH`: 2026-10-22T16:10:27.6674709-03:00 até 2026-10-22T16:14:17.0206594-03:00; duração do coletor 229.3531885s, exit0.
- `INPUT`: 2026-10-22T16:14:43.5551859-03:00 até 2026-10-22T16:17:15.4835694-03:00; duração do coletor 151.9283835s, exit0.
- `TEMPORAL`: 2026-10-22T16:18:19.5019923-03:00 até 2026-10-22T16:19:02.5887508-03:00; duração do coletor 43.086758499999995s, exit0.


Console e Surefire XML/TXT conciliados por `RESUME-T017/analyze.ps1`: totais globais, contagem de testcase por XML, classes/métodos/parâmetros, identidades sem duplicatas e exit real. Para relacionar execuções, removido somente o sufixo próprio de cada execução que o Surefire também acrescenta ao classname dos testcases; XML/TXT originais intactos. Resultados detalhados em `AUTH-analysis.json`, `INPUT-analysis.json`, `TEMPORAL-analysis.json`; `DIAG-analysis.json` mantém a análise nova do S2 antigo sem modificar sua evidência. Os470 casos focais correspondem a identidades do diagnóstico anterior, sem seleção vazia, desabilitação, remoção ou sucesso de compilação contado como teste. Os dois controles Long.MAX_VALUE são seleção justificada desse método específico, não repetição do núcleo S1.

Os20 controles de autorização e os dois de entrada antes interrompidos agora alcançaram proxy/referência e snapshots independentes após confirmação, preservação de itens/histórico e cleanup completo. Os três controles temporais LOWER_EQUAL/UPPER_EQUAL/NATIVE_FLOOR agora concluíram as verificações posteriores; também passaram BEFORE_CREATION/AFTER_REFERENCE/NATIVE_CEILING e ADMIN/BOTH revogados. O console temporal contém oito `T016_WAIT` com sessões distintas e blocker correspondente e oito `T016_RESULT` após asserções, com uma leitura de negócio e referência nativa conservada. A tentativa NATIVE_FLOOR aceita `.123` sob referência `.123000001`; NATIVE_CEILING rejeita `.124` sob `.123999999`, sem deslocar limite ou arredondar.

Controle auxiliar de representação, separado das470 invocações JUnit: `java --class-path <classpath Jackson extraído do XML AUTH> target/feature008-evidence/RESUME-T017/OracleControls.java`, exit0, **50 controles/zero falhas**. Fonte, classpath efetivo2.17.2, comando e console preservados no recibo. Exercitados1/int32máximo/int32+1/2^53/2^53+1/Long.MAX_VALUE−1/Long.MAX_VALUE, inteiro vizinho diferente, string, decimal `.0`/`.5`, expoente, alteração de status/instante/formato temporal, campo extra/ausente e objeto/array incorretos. Reproduzida também a desigualdade original IntNode/LongNode. Não é teste JUnit, HTTP, persistência ou transação; não substitui os controles pelo Wrapper. A tentativa auxiliar `python -B -I -X utf8 -c "import sys; print(sys.executable)"` encontrou somente o alias WindowsApps sem Python instalado; a análise/revisão foi feita com PowerShell/.NET, sem teste da aplicação bloqueado.

### Critérios da T017, preservação e limites

Revisados PaymentAccessService, PaymentRegistrationService, PaymentRequestJsonConfig, PaymentRegistrationExceptionHandler e os handlers/cadeia de segurança T012. A produção já atende aos critérios confrontados: referência nativa capturada uma vez na primeira instrução e mantida após espera; guardas escalares atuais antes de pedido e após lock/refresh, antes de mutação; elegibilidade conjunta e categorias/mensagens fechadas; parser/Bean Validation locais sem IO de domínio; falhas de retorno/entidade/internas permanecem500 em vez de400/401/409. GREEN focal obtido sem mudança cosmética de produção nem enfraquecimento dos testes. As dependências T014–T016 haviam sido escritas/executadas no S2 reprovado antes desta correção.

Recibo `RESUME-T017/` guarda captura, originais e revisão incremental. A captura inicial dos três Java terminou após o patch; essa cópia foi retida em `captured-after-edit/`. Seus originais foram recuperados de `REVIEW-T017/received/`, conferidos pelo SHA-256 do manifesto histórico e por comparação exata que permite somente a mudança descrita em cada arquivo; procedência em `original-recovery.json`. Nenhuma evidência histórica foi reescrita. Preservados por hash os1.012 arquivos de evidência/relatório inventariados nesta retomada e também os312 relatórios anteriores já inventariados no diagnóstico. O quickstart recebido permanece prefixo byte a byte, inclusive a seção anterior que deixou T017 aberta; esta nova seção registra sua resolução posterior.

Limites preservados: HTTP ROOT+/api usa serviço/persistência reais nos cenários de autorização/entrada; MVC local e os dois controles HTTP int64 usam serviço simulado. Temporal observa H2, conexões independentes e espera real; não comprova outro banco nem revogação posterior à última guarda. Observação Hibernate é de preparação SQL; snapshots não detectam toda mutação transitória revertida. Parser auxiliar não comprova aceitação HTTP de pedidos grandes existentes. Sem prova nova de concorrência US3, failpoints/commit indeterminado/transporte US4, consultas pós-pagamento US5 ou regressões completas. **T018/C2 permanece pendente:** não foi executado o checkpoint conjunto S2+S1, nem repetidos BASE ou suíte completa; estes resultados focais não o substituem.

Critérios completos da **T017 atendidos**, com causa de oráculo corrigida, revisão produtiva sem lacuna demonstrada e GREEN focal. Alterado somente seu marcador em tasks.md: **T001–T017[X];T018–T036 abertas,17 concluídas/19 abertas**. Nesta retomada os únicos arquivos de trabalho editados são os três Java indicados, quickstart.md e esse marcador; os outros três Java não rastreados recebidos permanecem byte-preservados. Diff e conteúdo completo dos seis Java novos revisados; links locais, referências de métodos/relatórios e whitespace conferidos. `git diff --check` exit0; os seis `git -c core.autocrlf=false diff --no-index --check -- /dev/null <Java>` sem diagnóstico de whitespace, com exit1 por serem arquivos novos. `final-review.json` registra preservações, identidades e estado Git. extensions.yml ausente também no fechamento, sem hooks before/after_implement.

Git final: mesma raiz/branch/HEAD/seleção e main; quickstart.md/tasks.md modificados e os mesmos seis Java não rastreados. Nada staged; sem stage, commit, push, merge ou alteração de main. Pendência seguinte é T018, sem antecipação de T018–T036 ou declaração de entrega da Feature008.

## T018 — Checkpoint conjunto US1+US2 / C2 (2026-10-22)

Aplicada speckit-implement exclusivamente à [T018](tasks.md), lida integralmente com T013–T017, condições C2 e tabela de seleções. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `2f053fef08725da34ed04f28d77b2fe52b90cec4`, seleção local008 e índice vazio. Recebidos somente quickstart.md/tasks.md modificados e os mesmos seis Java não rastreados:

- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentAuthorizationApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentInputApiHttpIntegrationTest.java`
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentBoundaryValidationTest.java`
- `src/test/java/com/devsuperior/dscommerce/services/PaymentTemporalAndCurrentRolesIntegrationTest.java`

Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. requirements.md16/16, zero aberto, PASS documental; constituição, plano, procedimentos/limites do quickstart e precedência contratual conferidos. Ignore Java adequado; extensions.yml ausente antes e depois, sem hooks. T017 já atendida com revisão e GREEN focal; isso foi tratado como dependência, não como aprovação conjunta. Seus três oráculos corrigidos, igualdade estrutural, número JSON inteiro e precisão int64 permaneceram byte-preservados nesta tarefa.

### Execução nova, seleção exata e coleta

Uma única invocação Maven, nenhuma outra simultânea; não havia processo Java ativo na conferência inicial. Windows/PowerShell5.1, Java21.0.12.1 Zulu, Wrapper/Maven3.9.16 conforme propriedades existentes. Executado exatamente o seletor S2/T018, reunindo T014–T016 e todo o núcleo S1 aprovado, sem BASE, suíte completa ou T019+:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentAuthorization*Test,PaymentInput*Test,PaymentBoundaryValidationTest,PaymentTemporalAndCurrentRolesIntegrationTest,PaymentRegistration*Test,PaymentPersistenceIntegrationTest" "-Dsurefire.reportNameSuffix=S2-T018-20261022T162604150" test
~~~

Início registrado: `2026-10-22T16:26:05.0425685-03:00`; término do coletor: `2026-10-22T16:30:59.2050275-03:00`; duração observada do coletor: 294.162459s. Console Maven:04:47min, término16:30:58−03:00. Exit0.

Coletor `target/feature008-evidence/run-t018.ps1`; recibo `target/feature008-evidence/REVIEW-T018/` com manifesto/hashes dos354 arquivos recebidos, cópias dos dois documentos/seis Java, status/diff e referência main. Registrados hashes de2.155 arquivos de evidência anteriores: relatórios originais, diagnóstico S2 reprovado, três execuções focais T017 e recibo/controles separados do parser. A captura terminou antes do Wrapper e de qualquer edição documental desta tarefa.

Evidência nova em `target/feature008-evidence/S2-T018-20261022T162604150/`: run.json (comando/ambiente/HEAD/branch/main/início), console.txt, exit.json, previous-surefire-reports/, surefire-reports/, analysis.json e coverage.json. Relatórios anteriores copiados antes do Wrapper; console e todo Surefire copiados após término, sem clean nem sobrescrita dos sufixos antigos. Console UTF-16 via Tee-Object, stderr mesclado com ErrorActionPreference=Continue e LASTEXITCODE capturado imediatamente; JSON UTF-8. Advertências conhecidas de agente/VM/depreciação não foram contadas como falhas de comportamento.

### Resultado conjunto efetivamente conferido

**BUILD SUCCESS,exit0:839 invocações aprovadas,0 falhas,0 erros,0 ignoradas.** Console,17 XML e17 TXT próprios conciliados por `REVIEW-T018/analyze.ps1`, exit0; fontes inalteradas, sem sucesso presumido a partir da compilação ou dos grupos focais.

| Classe/grupo executado | Casos aprovados |
| --- | ---: |
| PaymentAuthorizationHttpIntegrationTest / Api | 40+40 |
| PaymentInputHttpIntegrationTest / Api | 129+129 |
| PaymentBoundaryValidationTest — MVC local | 122 |
| PaymentTemporalAndCurrentRolesIntegrationTest — ROOT | 8 |
| PaymentRegistrationHttpIntegrationTest / Api — serviço/persistência reais | 16+16 |
| PaymentRegistrationEndpointHttpTest / Api — HTTP real, serviço simulado | 48+48 |
| PaymentRegistrationDtoContractTest | 141 |
| PaymentPersistenceIntegrationTest | 13 |
| PaymentRegistrationDatabaseProbeTest | 12 |
| PaymentRegistrationServiceTest$CurrentAccess | 18 |
| PaymentRegistrationServiceTest$RegistrationRules | 51 |
| PaymentRegistrationServiceTest$TransactionAndCategories | 8 |
| PaymentRegistrationServiceTest — container externo | 0, sem duplicar os77 aninhados |
| **Total** | **839** |

Conferidos839 pares classe/método/invocação únicos, sem omissões/duplicatas: correspondência exata com as identidades do diagnóstico S2 reprovado. Normalizado somente o sufixo próprio que Surefire acrescenta ao classname; arquivos brutos permanecem intactos. Todos os25 casos então falhos passaram agora, sem reinterpretar o diagnóstico histórico. Os371 casos do núcleo correspondem exatamente às identidades C1/T013; os468 restantes são US2. As470 identidades focais T017 também estão presentes, como subconjunto desta execução, sem somar novamente seus resultados históricos.

Métodos herdados por contexto ROOT/Api: autorização10, entrada9, integração real US1 onze e fronteira com serviço simulado17; matrizes e contagens de invocações iguais em cada par. MVC local:10 métodos/122 casos; temporal:dois métodos/oito casos. Os três grupos @Nested do serviço somam18+51+8=77; seu container externo tem zero e não infla o total. Todas as17 suítes esperadas e seus TXT foram conferidos; nenhuma seleção vazia, classe excluída, teste desabilitado ou omissão de parâmetros foi usada para obter GREEN.

### Critérios C2 e controles preservados

| Critério/cenários | Evidência nesta execução conjunta |
| --- | --- |
| US2.1–3: credenciais e ADMIN atual | Autorização40 por contexto:401 com Bearer para ausência/invalidez/expiração/conta removida;403 para CLIENT/sem perfil;ADMIN isolado/ambos em compra própria/alheia;claims enganosos e mudança de perfis após emissão/filtro;lookup falho500. SQL/snapshots e controle de segredos preservados. |
| Segurança anterior à MVC | Entradas inválidas têm400 com ADMIN atual,401 sem credencial e403 sem ADMIN antes do serviço; asserções de ausência de leitura restrita/DML nas rejeições precoces, envelope e path seguro passaram em ROOT e /api. |
| US2.4–5,8–9: entrada e erros | Matrizes39 documentos/48 ocorrências inválidas/24 válidas;IDs/faixa/query/JSON fechado/duplicatas/trailing;offset e zeros exatos;404 de negócio e409 PAID sem Payment sem reparo. Rotas omitidas continuam somente prova de roteamento. MVC local distingue entrada400 de validação de retorno/entidade500 e conserva reader/validator sem IO de domínio. |
| US2.6–7 e limites inclusivos | LOWER_EQUAL/UPPER_EQUAL aceitos,BEFORE_CREATION/AFTER_REFERENCE rejeitados. NATIVE_FLOOR aceita ocorrência `.123` com referência `.123000001`;NATIVE_CEILING rejeita `.124` com `.123999999`. Oito T016_WAIT comprovaram sessões distintas e estado BLOCKED com blocker correspondente antes do avanço do Clock. |
| Revogação após espera | ADMIN/BOTH pré-carregados no contexto JPA perderam ADMIN em transação externa commitada durante o bloqueio;guarda escalar atual rejeitou403,sem mutação causada pela tentativa. Mudança externa foi isolada no baseline. Oito T016_RESULT ocorreram após asserções completas,com clockCalls=1 e snapshots independentes. |
| Seis cenários US1 e núcleo | 32 casos de HTTP/serviço/persistência reais ROOT/Api;18 T013_SUCCESS,nove por contexto,com proxy dono READ_COMMITTED,uma leitura de negócio e Payment/PAID por JDBC independente. Confirmação de três campos,ocorrência,42,50/histórico20,00 versus catálogo12,00,headers,limites e rejeições permaneceram aprovados. DTO141,serviço77,persistência13 e probe12 completam o núcleo371 com fronteira simulada96. |
| Isolamento de rotas/handlers | `neighboringPublicOrderAndProductSecurityContractsKeepTheirOwnResponses` passou nos dois contextos:GET público/products,GET admin/products autorizado/401 com envelope de produtos,GET/orders401 ePOST/orders com rejeição própria;nenhuma interação com serviço de pagamento. Revisão de SecurityConfig confirmou matcher exclusivo `/admin/orders/*/payments`,cadeias GET/HEAD/orders e/admin/** próprias;advice limitado a AdminPaymentController e fallback global de405/406/415 somente no path de pagamento. |

Rejeições conferem snapshots completos de oito tabelas e SQL observado; o núcleo registrou ainda14 T013_REJECTION e32 T013_CLEANUP, após as respectivas asserções. Limpeza seletiva exercitada sem alterar seed/DB/perfil/mapper globais. Os três oráculos conservam a comparação integral da árvore e isIntegralNumber;nenhuma asserção de autorização,janela,histórico ou ausência de mutação foi alterada. **Não houve falha nova a diagnosticar ou corrigir;nenhum Java ou componente produtivo recebeu edição.**

Os **50 controles adicionais do parser continuam sendo a execução isolada histórica da T017**, com fonte/comando/console próprios em RESUME-T017, exit0. Não foram reexecutados nem incluídos nas839 invocações JUnit desta T018. O diagnóstico anterior839/814/25/exit1 e os focais470/470 permanecem registros separados e preservados.

### Limites e fechamento exclusivo da T018

Checkpoint C2 aprovado localmente na seleção e revisão descritas;nenhuma execução CI/Linux nesta tarefa. HTTP de autorização/entrada/US1 usa serviço e H2 reais;fronteira96 e MVC122 usam serviço simulado. DTO/serviço unitários não provam commit por si sós. A espera física e JDBC independente comprovam o H2 observado,não outro banco nem revogação posterior à última guarda. SQL observado é preparação Hibernate;snapshots não detectam toda mudança transitória revertida. Contagens JUnit não equivalem a contagem de requests ou pagamentos. Os controles vizinhos e revisão de matcher não substituem a suíte completa de consultas007/produtos ou as histórias posteriores.

T019–T036 permanecem abertas:sem antecipar concorrência US3,failpoints/rollback/transporte US4,leituras PAID US5 ou FINAL. Contratos008 continuam alvos planned;sem publicação de vigência ou declaração de entrega da Feature008. BASE e suíte completa não foram repetidas.

Acrescentada somente esta seção ao quickstart, preservando seu prefixo recebido byte a byte; em tasks.md,alterado somente o marcador T018 para[X]. **T001–T018[X],T019–T036 abertas:18 concluídas/18 abertas.** Revisados diff incremental,conteúdo/referências dos seis Java recebidos,links locais e whitespace;`git diff --check` exit0 e seis verificações `git -c core.autocrlf=false diff --no-index --check -- /dev/null <Java>` sem diagnóstico (exit1 pelo arquivo novo). REVIEW-T018/final-review.json registra preservação por SHA-256 dos352 demais arquivos recebidos e das2.155 evidências inventariadas, incluindo oráculos e controles anteriores.

Git final mantém raiz/branch/HEAD/seleção e main;somente quickstart.md/tasks.md modificados e os mesmos seis Java não rastreados;nada staged. Sem stage,commit,push,merge ou alteração de main. Pendência seguinte:T019,fora do escopo executado.

## T019 — Autoria de repetição e disputa concorrente pelo mesmo pedido (2026-10-22)

Aplicada speckit-implement exclusivamente à [T019](tasks.md). Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `1b49dc1b61f27ccecbe7aa561dfc56fc4da93125`, Git limpo, índice vazio e seleção local008. O commit recebido é `test: verify payment authorization and validation`, com os oito arquivos do checkpoint US2, sem alteração de produção. Lidos T019 integralmente, dependência T018 e limites T020–T022, constituição, AGENTS.md/harness, spec US3.1–5, plano, pesquisa/modelo de locks e unicidade, contrato POST e procedimentos do quickstart. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Checklist requirements.md:16/16, zero aberto, PASS documental. Ignore Java adequado; extensions.yml ausente antes e depois, sem hooks.

### Limite desta entrega

T019 é **criação do teste**; execução S3 está prevista antes de eventual correção T021 e no checkpoint T022. Nesta tarefa houve somente escrita e revisão estática. **Nenhum Wrapper/Maven, javac, compilação, teste, contexto Spring/H2, login ou request foi executado.** Não há novo RED/GREEN, console/Surefire ou exit code de testes; não foi fabricada falha para comportamento já presente. A compilabilidade e as comprovações concorrentes continuam pendentes.

Criado somente [PaymentConcurrencyIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java), no caminho atribuído à T019. Seleciona ROOT/HTTP real com o suporte008 existente e observação explícita; não cria aliasApi nem altera suporte compartilhado, produção, mapper, seed, banco ou propriedades globais. A observação temporária está inteira no arquivo novo e delega aos proxies originais.

O pedido também solicitou duas aceitações para pedidos distintos. Incluído um controle positivo básico no mesmo arquivo: ambas as transações adquirem suas raízes distintas enquanto estão abertas e depois são liberadas para concluir. Isso **não conclui nem implementa a prova própria da T020** de B confirmar commit enquanto A continua bloqueado, nem o teste de contexto pré-carregado/refresh. Os dois arquivos atribuídos à T020 não foram criados; T020 permanece aberta.

### Matriz escrita, ainda não executada

| Método | Casos previstos | Expectativa futura |
| --- | ---: | --- |
| `sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal` | 4 | Quitação original real201; repetição por mesmo/outro ADMIN e ocorrência igual/diferente409, sem sucesso reutilizado nem substituição da ocorrência. São chamadas sequenciais deliberadas, sem alegar concorrência. |
| `existingPaymentPrecedesWaitingStateWithoutRepair` | 4 | Depois de quitação real, setup JDBC commitado altera somente o status próprio para WAITING_PAYMENT; Payment anterior é conservado. As quatro variantes exigem409 `Payment already registered`, sem reparar estado ou sobrescrever pagamento. O baseline da rejeição é posterior à preparação inconsistente. |
| `overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment` | 4 | Primeiro detentor da raiz ADMIN A/B × ocorrências iguais/diferentes. Duas chamadas HTTP sobre o mesmo banco, transações/sessões distintas e sobrepostas; exatamente201+409, um Payment com ocorrência vencedora, PAID e perdedora sem escrita. |
| `validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions` | 1 | Controle solicitado: duas raízes, sessões e transações simultaneamente abertas, ambos201, vínculos/ocorrências próprios e totais históricos20,00/22,50 conservados. |
| Total estático | **13** | Quatro métodos, três matrizes4 e um @Test, somente ROOT. Sem testes herdados adicionais, @Nested, desabilitados ou assumptions. |

Se todos os caminhos escritos forem alcançados futuramente, são26 POSTs de pagamento previstos:14 respostas201 e12 respostas409. Login de preparação fica separado. Esses valores não são contagens JUnit/HTTP observadas. Falha de setup, instrumentação, autorização inesperada, transporte, pool, timeout ou deadlock deve falhar o experimento, sem ser convertida em concorrência bem-sucedida ou409 presumido.

### Coordenação e observação preparadas

Fixture prepara somente dados próprios commitados, dois ADMINs atuais (ADMIN isolado e ADMIN+CLIENT), comprador alheio, alvo20,00 e outro pedido22,50, com catálogo12,00. Os dois tokens vêm do login HTTP real quando o teste for executado; snapshots estritos incluem as oito tabelas. Clock representável é fixado após login; ocorrências são válidas entre criação e referência, com diferença de1ms nas variantes diferentes. Cada tentativa exige a mesma referência e uma leitura de negócio.

Um interceptor scoped ao par conta/pedido mantém apenas a associação entre request e observação por ThreadLocal. Outro observa `findPaymentOrderForUpdate` no repository real: exige transação nova READ_COMMITTED, conexão vinculada sem autoCommit, sessão H2, thread e URL do mesmo banco. O retorno do find+refresh real é observado antes de liberar o serviço; nenhum resultado é substituído. A conexão é devolvida por DataSourceUtils, sem encerrar a transação de aplicação.

Na disputa, a primeira chamada adquire a raiz e permanece suspensa. Só então a segunda é enviada, mas a primeira ainda não concluiu: são duas solicitações sobrepostas, não dois POSTs sequenciais. Após liberar o gate de preparação SQL da segunda, o teste exige metadados H2 SESSION_STATE=BLOCKED e BLOCKER_ID da primeira sessão. Sessões e threads são distintas e a URL é igual à da terceira conexão observadora. `Future.isDone=false` e SQL preparado apenas complementam essa prova física; não a substituem.

Primeiro ambas estão sem DML e o snapshot deve ser idêntico ao baseline. Depois a primeira avança até `beforeCommit`, após o flush explícito do serviço. O commit continua suspenso; são exigidos insert de Payment/update de Order, segundo request ainda fisicamente bloqueado e dados commitados ainda iguais ao baseline. Liberado o commit, a segunda aquisição/refresh precisa devolver PAID e permanece retida antes da elegibilidade. A primeira deve201, retornar do proxy sem transação externa e ser visível por JDBC independente. Só então a segunda avança para409 e seu término deve conservar o snapshot vencedor integralmente.

`beforeCommit`, `afterCommit` e `afterCompletion` são callbacks Spring registrados dentro da transação real: o vencedor deve ter um commit confirmado pelo callback e completion COMMITTED; a perdedora, zero commits/beforeCommit e completion ROLLED_BACK. Asserções de HTTP, proxy e JDBC independente complementam os callbacks. **Isso não instrumenta a chamada física Connection.commit do driver**, não simula falha de commit e não antecipa o suporte/failpoints de T023. Todas essas observações são apenas código escrito nesta T019.

O controle de pedidos distintos aguarda ambas as aquisições com transações ainda abertas, compara o baseline antes de mutar e libera ambos os serviços; exige dois201 e dois pagamentos exclusivos, sem misturar ocorrências ou históricos. Não mantém um pedido bloqueado enquanto exige o commit do outro: essa prova mais forte continua na T020. Ao final dos cenários concorrentes, conexões novas devem conseguir readquirir as raízes com FOR UPDATE e rollback, verificando liberação de locks.

### Asserções, recursos e limites

Confirmação compara a árvore inteira com o esperado lido pelo mesmo parser, conservando a correção IntNode/LongNode da T017, `isIntegralNumber`, capacidade long e identidade exata. Não aceita string/decimal para o ID, não remove campos e não usa ponto flutuante ou tolerância. Há somente orderId/occurredAt/status, UTC.SSS, sem Location ou WWW-Authenticate;409 tem envelope fechado e mensagem própria. Guardas, referência e proxy são exigidos por tentativa. SQL da rejeição deve ser somente SELECT, sem leitura de itens/catálogo; o sucesso admite somente um insert de Payment e um update de Order.

Snapshots exigem exatamente o Payment esperado e apenas status dos pedidos aceitos alterado. O probe007 exige diagnóstico exclusivo `tb_order`, portanto mantém também comparação opaca de credenciais, contas/perfis, catálogo e itens; comparação adicional conserva comprador/criação e totais dos dois pedidos. A rejeição compara as oito tabelas contra seu baseline apropriado. StatementInspector observa preparação Hibernate, não todo JDBC/DML; snapshots não detectam qualquer mutação transitória revertida. Leituras durante os gates são feitas com escritores deliberadamente parados; após as respostas, todos os escritores devem ter terminado antes da comparação final.

CountDownLatch/gates/Futures têm limites10s; polling de metadados tem prazo8s e queryTimeout2s, com cadência5ms por LockSupport.parkNanos. A cadência não prova espera e não há Thread.sleep, NOWAIT/SKIP LOCKED, mutex de negócio ou espera ilimitada. Reaquisição usa timeout local2s. O lock_timeout20s do suporte008 permanece inalterado.

Em falha, try-with-resources primeiro libera os latches locais, depois fecha a Window (libera seus gates, aguarda/cancela trabalho e aguarda término dos requests) e só então remove ambos os advices. ThreadLocal é removido no finally do request. Interceptor parcialmente instalado é removido em falha de construção. Statements/resultsets/conexões auxiliares são fechados e a reaquisição termina em rollback. AfterEach limpa somente a fixture, Payment antes de itens/pedidos e demais linhas próprias, e exige restauração completa do baseline. Essa confiabilidade foi revisada estaticamente, não exercitada nesta tarefa.

### Preservações, revisão e conclusão de autoria

Recibo ignorado `target/feature008-evidence/CONCURRENCY-T019/` guarda manifesto/hashes dos354 arquivos recebidos, cópias dos documentos, main/status inicial,412 arquivos de evidência inventariados e revisão estática. A revisão também confere o manifesto anterior de2.155 evidências. C2 continua histórico separado:839 aprovados,zero falhas/erros/ignorados,exit0,17 XML/17 TXT na execução `S2-T018-20261022T162604150`; não foi repetido nem promovido a aprovação do Java novo. Diagnóstico839/814/25 e correção dos três oráculos, focais470/470,50 controles adicionais do parser e evidências T001–T018 permanecem preservados.

Critérios de **autoria da T019 atendidos**. Somente seu marcador em tasks.md passa para[X]: **T001–T019[X],T020–T036 abertas,19 concluídas/17 abertas**. Não há compilação, prova concorrente executada, novo GREEN/C3 ou causa funcional observada para corrigir produção. Procedimento futuro aprovado: seleção S3/T022 inclui PaymentConcurrencyIntegrationTest,PaymentDistinctOrdersIntegrationTest,OrderPaymentLockRepositoryTest,PaymentPersistenceIntegrationTest,PaymentTemporalAndCurrentRolesIntegrationTest, quando todas existirem e na etapa autorizada, antes de eventual correção T021 e depois se necessária. Não executar seleção com classes ausentes ou omitir os controles posteriores para declarar C3.

Diff, conteúdo completo do Java novo, APIs/referências locais, links e whitespace revisados. Revisão PowerShell/.NET em `review.ps1`, exit0, somente estática:352 arquivos recebidos fora dos dois documentos inalterados por hash, prefixo histórico do quickstart intacto e somente checkbox T019 alterada. `git diff --check` exit0; `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java` sem diagnóstico de whitespace (exit1 pelo arquivo novo). Isso não aprova compilabilidade, wiring ou comportamento.

Git final: quickstart.md/tasks.md modificados e somente PaymentConcurrencyIntegrationTest.java não rastreado. Mesma raiz/branch/HEAD/seleção e main;nada staged. Sem stage,commit,push,merge ou alteração de main;sem C2,BASE ou suíte completa repetidos. T020–T036 e as comprovações executáveis permanecem pendentes;sem execução ou antecipação de suas entregas.

## Autoria T020 — contexto obsoleto e pedidos distintos sob lock

### Escopo recebido e decisão de execução

Aplicada speckit-implement exclusivamente à [T020](tasks.md). Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `1b49dc1b61f27ccecbe7aa561dfc56fc4da93125`, seleção local008 e índice vazio. Estado recebido: somente quickstart.md/tasks.md modificados e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java` não rastreado. Checklist requirements.md:16/16, zero aberto. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Lidos T020 integralmente, dependência T018, limites T019/T021/T022, constituição, spec/plan/research/data-model, contratos, harness e procedimentos aprovados. Ignore Java adequado; extensions.yml ausente, sem hooks.

T020 exige **criação dos dois testes**. A seleção conjunta S3 está prevista antes de eventual correção T021 e no checkpoint T022. Nesta tarefa houve autoria e revisão estática, **sem Wrapper/Maven, javac, compilação, teste, Spring/H2, login ou HTTP executado**. Não há novo console/Surefire, exit code de testes, RED/GREEN ou C3. Nenhuma falha funcional, impedimento de preparação ou timeout de coordenação foi observado por execução; compilabilidade e funcionamento dos experimentos permanecem por verificar.

### Casos escritos; contagens previstas, não executadas

| Arquivo / método | Autoria | Invocações previstas | Executadas nesta T020 |
| --- | --- | --- | --- |
| [OrderPaymentLockRepositoryTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryTest.java): lockAndRefreshReplacePreloadedWaitingStateWithAnotherTransactionsCommittedSettlement | Um @Test de repository/JPA | 1 | 0 |
| [PaymentDistinctOrdersIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentDistinctOrdersIntegrationTest.java): distinctOrderCommitsWhileTheOtherStillWaitsForItsRootLock | Um @ParameterizedTest, ValueSource A/B alterna a raiz bloqueada | 2 | 0 |

Total novo: **dois métodos, três invocações previstas e zero executadas**, sem @Nested/@Disabled/assumptions. O suporte HTTP herdado fornece lifecycle/helpers, sem métodos @Test herdados. As **13 invocações previstas da T019 continuam somente autoria recebida**, preservada byte a byte, sem presumir execução nem somar previsões como resultado Surefire. Repetição sequencial, disputa no mesmo pedido e controle básico de pedidos distintos da T019 continuam separados da prova forte escrita aqui.

### Refresh de contexto JPA pré-carregado

O teste de repository prepara fixture commitada, pré-carrega WAITING_PAYMENT em uma Session e conserva esse mesmo objeto gerenciado sem clear/evict. Outra Session/transação resource-local efetiva Payment+PAID e commit. Exige sessões H2 físicas distintas, mesma URL, READ_COMMITTED e autoCommit=false, sem transação Spring externa. O probe independente deve ver a quitação enquanto o primeiro contexto continua exibindo o estado antigo. O fragmento produtivo real recebe o EntityManager desse contexto por injeção local de teste; find+refresh com PESSIMISTIC_WRITE precisa devolver a mesma entidade agora PAID, mantendo ID, comprador e criação.

StatementInspector instalado somente nessa Session captura a aquisição: dois SELECTs de lock somente da raiz por ID, sem JOIN/DISTINCT/Product/Payment/coleção ou filtro de estado/titular. Nenhum DML no leitor. Rollback do leitor deve preservar o commit independente do escritor. Snapshots exigem exatamente uma nova quitação/ocorrência e a mudança de status do alvo, conservando os demais dados das oito tabelas; limpeza remove somente dados próprios. Essa intercalação controlada no mesmo thread usa duas transações reais sobrepostas, **não é corrida de duas chamadas HTTP nem prova de commit HTTP com transação externa**.

### Outro pedido confirma enquanto a raiz permanece bloqueada

O teste HTTP usa dois ADMINs com credenciais reais e pedidos/históricos distintos:20,00 e42,50, ocorrências diferentes em1ms, mais pedido canário8,00. Uma transação JDBC auxiliar mantém somente a raiz escolhida sob FOR UPDATE. O primeiro POST entra no serviço real/proxy dono da transação; gate na preparação do SQL permite identificar sua conexão e observar `INFORMATION_SCHEMA.SESSIONS` com SESSION_STATE=BLOCKED e BLOCKER_ID da auxiliar, no mesmo banco. Só então o segundo POST é lançado para a outra raiz.

Antes de liberar a auxiliar, exige201 do segundo POST, retorno do proxy/afterCompletion, commit observado por callback e quitação visível por conexão JDBC independente; o primeiro continua fisicamente BLOCKED e sem retorno da aquisição/DML. O snapshot intermediário admite somente a quitação/status do segundo pedido e preserva o primeiro e o canário. Depois do rollback auxiliar que libera o lock, o primeiro também precisa201. Exige duas sessões/thread de serviço diferentes e outra sessão auxiliar/observadora, mesma URL, transações novas READ_COMMITTED, uma leitura do Clock por request e referência constante. Observadores locais delegam integralmente ao repository/proxy; nenhum resultado é substituído e nenhum mutex comum é instalado.

Resultado final esperado: duas linhas Payment exclusivas com os IDs/ocorrências correspondentes, somente esses dois status alterados, históricos20,00/42,50 e canário8,00 preservados, assim como produtos, itens/ordem/valores, contas, credenciais e perfis. Resposta201 compara a árvore JSON completa por parser comum nos dois lados (correção IntNode/LongNode preservada), exige orderId numérico integral convertível para int64 e igualdade exata long; sem strings/fracionários, float/double, tolerância ou remoção de campos. Confere headers, SQL de dois locks somente da raiz por ID e exatamente insert Payment/update Order por request, sem leitura de Product ou filtro de elegibilidade no lock.

### Coordenação, limpeza e classificação futura

Gates e futures têm espera limitada10s; consultas de metadados têm timeout2s e deadline8s. A consulta a BLOCKED/BLOCKER_ID é o oráculo; parkNanos5ms apenas limita a frequência desse polling, sem pressupor ordenação pelo tempo decorrido. Não há Thread.sleep, NOWAIT, SKIP LOCKED ou retry da operação para obter aprovação. Timeout de coordenação deve ser registrado como tal, não como409 funcional ou sucesso concorrente. Falhas de bootstrap/compilação não demonstram comportamento ausente; status/snapshot/SQL incompatíveis após preparação válida são diagnósticos funcionais a examinar antes de eventual ajuste.

Nos caminhos de falha, liberar gates/rollback auxiliar antes de aguardar/cancelar workers; janela termina antes de retirar advice. Sessions/transactions resource-local têm rollback em finally e fechamento por try-with-resources. Fixtures limpam somente seus registros e comparam o baseline anterior. Após os cenários, conexões novas tentam readquirir as raízes com lock_timeout/queryTimeout2s e rollback, exigindo liberação dos locks. Timeout local do teste de repository também2s; não alterar configuração produtiva/global.

### Comandos, preservação e limites

Recibo local ignorado: `target/feature008-evidence/LOCKS-T020/`, com received.json, cópias dos documentos/Java recebidos, previous-evidence.json, main/status iniciais e review.ps1/static-review.json. Preservação conferida por SHA256, sem regenerar ou sobrescrever relatórios anteriores. Mantidos diagnóstico S2 inicial839/814/25, correção dos três oráculos, C2 conjunto839 aprovados/zero falhas-erros-ignorados/exit0 e50 controles adicionais do parser identificados separadamente, além das demais evidências históricas. Esses resultados continuam históricos, não novas execuções desta autoria.

Comandos desta etapa documental/estática, na raiz PowerShell:

~~~powershell
.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks
powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/LOCKS-T020/review.ps1
git diff --check
git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java
git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/controllers/PaymentDistinctOrdersIntegrationTest.java
git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryTest.java
git status --short
~~~

Pré-requisitos exit0. Revisão estática e git diff --check: resultados registrados abaixo após execução; diff --no-index exit1 sem diagnóstico apenas sinaliza arquivo novo contra /dev/null. Nenhum exit code de teste é atribuído a esses comandos.

Procedimento **futuro**, fora desta T020: S3/T022 usa exatamente `PaymentConcurrencyIntegrationTest,PaymentDistinctOrdersIntegrationTest,OrderPaymentLockRepositoryTest,PaymentPersistenceIntegrationTest,PaymentTemporalAndCurrentRolesIntegrationTest` pelo Wrapper sequencialmente, com diretório de relatórios próprio, diagnóstico anterior a eventual correção T021 e repetição somente se necessária. Conciliar console/Surefire, métodos parametrizados/aninhados/herdados e contagens efetivas; as previsões13+3 não substituem essa inspeção. Não repetir C2, BASE ou suíte completa para esta autoria.

Limites: somente revisão estática local Windows; não comprovados compilação, wiring, SQL efetivo, independência/espera/commit executados, ausência de mutex global em execução, limpeza real ou C3. Callbacks não instrumentam o commit do driver; StatementInspector não captura JDBC direto; snapshots compostos não são uma fotografia atômica universal. O desenho usa H2 isolado, não valida PostgreSQL/CI/Linux, durabilidade/crash recovery ou SLA. Não há correção produtiva/mapper/suporte recebido nem antecipação T021+. Critérios de **autoria T020** atendidos após revisão; somente seu marcador muda: **T001–T020[X], T021–T036 abertas**. Sem stage/commit/push/merge ou mudança de main.

Resultado efetivo da revisão: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/LOCKS-T020/review.ps1` **exit0**;353 arquivos recebidos preservados (todos exceto os dois documentos autorizados),7.077 arquivos de evidência com SHA256 idêntico, inclusive inventário exato dos396 arquivos Surefire anteriores, e prefixo integral recebido do quickstart com282.835 bytes preservado. Conferidos125 links locais e27 imports locais dos novos Java, dois métodos/três invocações somente previstas, zero casos herdados/aninhados. SHA256 do Java T019 recebido permanece `9C9736AE5936FA9E3FF0C920D5B4B19B388027AC847997F72CC7E5F9A9114234`. `git diff --check`:exit0;cada um dos três Java não rastreados em `diff --no-index --check`:exit1 sem diagnóstico de whitespace. Os avisos de Git sobre conversão futura LF/CRLF não alteraram os arquivos recebidos.

Revisados conteúdo integral dos novos testes, diff documental, referências, recursos/limpeza e preservação das asserções recebidas. Git final: quickstart.md/tasks.md modificados; PaymentConcurrencyIntegrationTest.java recebido, PaymentDistinctOrdersIntegrationTest.java e OrderPaymentLockRepositoryTest.java não rastreados; índice vazio. Raiz/branch/HEAD/seleção/main permanecem os recebidos. T021–T036 e toda comprovação executável S3 continuam pendentes; nenhum impedimento de execução foi constatado porque ela não integra esta autoria.

## T021 — revisão do núcleo após diagnóstico de repetição, locks e refresh (2026-10-22)

### Estado recebido, escopo e seleção

Aplicada speckit-implement exclusivamente à [T021](tasks.md), lida integralmente com as dependências T019/T020, T018 e os limites T022+. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `1b49dc1b61f27ccecbe7aa561dfc56fc4da93125`, seleção local008 e índice vazio. Recebidos somente quickstart.md/tasks.md modificados e três Java não rastreados, nos caminhos completos relativos à raiz:

- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java`;
- `src/test/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryTest.java`;
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentDistinctOrdersIntegrationTest.java`.

Lidos constituição, AGENTS.md/harness, spec US3, plan/research/data-model, contrato POST e procedimentos/evidências anteriores. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Checklist requirements.md:16/16, zero aberto, PASS documental. Ignore Java adequado; extensions.yml ausente antes/depois, sem hooks.

A tabela de tasks determina a seleção S3 **antes de eventual correção T021**, incluindo os controles de constraints T007 e tempo/perfis T016. Executada uma única invocação dessa seleção exata como diagnóstico e fundamento da revisão T021. **Não se conclui o checkpoint C3/T022**; seu marcador permanece aberto por escopo desta solicitação. Sem C2, BASE ou suíte completa repetidos, sem segunda execução Maven após o GREEN.

### Comando, ambiente e relatórios

Na raiz/PowerShell, `.\mvnw.cmd -v` confirmou Maven3.9.16 pelo Wrapper e Java Azul Zulu21.0.12.1, Windows10 amd64, exit0. Boot3.3.4, Hibernate6.5.3.Final e H2 2.2.224 existentes, sem mudar POM, ambiente global, perfil ou seed.

~~~powershell
.\mvnw.cmd "-Dtest=PaymentConcurrencyIntegrationTest,PaymentDistinctOrdersIntegrationTest,OrderPaymentLockRepositoryTest,PaymentPersistenceIntegrationTest,PaymentTemporalAndCurrentRolesIntegrationTest" "-Dsurefire.reportNameSuffix=S3-T021-DIAG-20261022T171300981" test
~~~

Início `2026-10-22T17:13:31.9872671-03:00`; término `2026-10-22T17:15:19.1897706-03:00`; Maven reportou01:42min. **BUILD SUCCESS, exit0,37 testes aprovados, zero falhas/erros/ignorados**. Compilação dos143 fontes de teste foi preparação do build, não contagem de testes nem RED. Advertências existentes de SecurityManager, ByteBuddy/VM e H2Dialect não constituem falhas funcionais.

Recibo exclusivo ignorado `target/feature008-evidence/S3-T021-DIAG-20261022T171300981/`: run.json, environment.txt, console.txt, exit.json, received.json/cópias/diff/status/main/HEAD iniciais, previous-evidence.json, previous-surefire-reports/, surefire-reports/, analysis.json, cases.json e coverage.json. Copiados os396 relatórios anteriores antes do Wrapper; sufixo novo gerou somente cinco XML e cinco TXT, arquivados após término junto dos anteriores. Console UTF-16 via Tee-Object, stderr mesclado com ErrorActionPreference=Continue; LASTEXITCODE capturado imediatamente. Nenhum clean, remoção, sobrescrita de sufixo anterior ou relatório fabricado.

### Quantidades efetivamente conciliadas

| Classe | Métodos | Invocações XML/console | Falhas/erros/ignorados |
| --- | ---: | ---: | --- |
| PaymentConcurrencyIntegrationTest | 4 | 13 | 0/0/0 |
| PaymentDistinctOrdersIntegrationTest | 1 | 2 | 0/0/0 |
| OrderPaymentLockRepositoryTest | 1 | 1 | 0/0/0 |
| PaymentPersistenceIntegrationTest | 10 | 13 | 0/0/0 |
| PaymentTemporalAndCurrentRolesIntegrationTest | 2 | 8 | 0/0/0 |
| Total | **18** | **37** | **0/0/0** |

Inspecionados cinco XML, cinco TXT e console dessa execução, sem agregar históricos. Os37 elementos testcase correspondem aos contadores das suites e ao resumo final. Conferidos os18 métodos: matrizes4+4+4 e controle1 da T019; duas variantes A/B do teste distinto; refresh1; oito métodos simples mais parâmetros3+2 de persistência; enum temporal6 e perfis2. Parâmetros aparecem como índices[1]…[n], contíguos e sem identidades repetidas. As classes não têm @Nested; suporte HTTP herdado fornece somente lifecycle/helpers, sem testes adicionais. Nenhuma omissão, classe adicional, duplicata, @Disabled, assumption ou seleção vazia. **As13 invocações da T019 e três da T020 agora têm prova executada nesta revisão**, distinta dos registros históricos de autoria.

O leitor local inicial de metadados dos relatórios terminou exit1 porque esperava sufixo com hífen no nome interno da suite; o Surefire usa `Classe(sufixo)` no XML, mantendo hífen no nome do arquivo. Corrigido somente esse script local ignorado de análise, preservando analysis/cases/coverage-initial-parser.json. Nova análise dos mesmos relatórios terminou exit0 e conciliou as18 entradas. Isso não foi falha da aplicação, preparação JPA, oráculo dos testes ou coordenação; nenhum Java/assertion foi alterado e Maven não foi repetido.

### Evidência de disputa e independência

As quatro variantes de mesmo pedido (primeiro ADMIN A/B × ocorrência igual/diferente) concluíram201+409, com um commit e rollback da perdedora, Payment/ocorrência originais preservados e snapshots sem mutação na rejeição. Marcadores T019_WAIT observaram duas vezes por variante a sessão4 BLOCKED pela3: antes da mutação e depois do flush, ainda em beforeCommit. Só após liberar o commit a segunda aquisição/refresh devolveu PAID. Asserções exigiram transações novas READ_COMMITTED, conexões/sessões e threads distintas, mesma URL, sem transação externa HTTP, uma leitura do Clock e releitura de elegibilidade sob lock. A suspensão é local e determinística, com latches/gates/futures limitados; não é uma sequência de dois POSTs já terminados.

Repetição sequencial e WAITING_PAYMENT com Payment anterior são oito invocações separadas, cada uma preparando quitação original201 e exigindo novo409 `Payment already registered`, inclusive outro ADMIN/ocorrência, sem sobrescrita ou reparo. O controle básico T019 de pedidos distintos é outra invocação: ambas as transações adquirem raízes diferentes enquanto abertas e depois201/201, históricos20,00/22,50. Ele continua distinto da prova forte T020 abaixo.

Na T020 HTTP, antes da liberação do holder, B confirmou201/commit/JDBC enquanto A continuava BLOCKED; depois A também201. Executadas ambas as escolhas de raiz retida. Console registrou `committedOrder=28 session=4 blockedOrder=27 session=3 holder=295` e `committedOrder=30 session=3 blockedOrder=31 session=4 holder=318`. O bloqueio foi exigido por SESSION_STATE/BLOCKER_ID antes e depois da confirmação do outro pedido, não inferido apenas de Future pendente ou tempo decorrido. Snapshots intermediário/final conservaram o pedido retido e o canário8,00, ocorrências próprias e totais20,00/42,50, além dos demais dados. Readquisição posterior das raízes em conexões novas passou, com rollback e timeouts locais2s.

O refresh JPA passou com `reader=3 committedWriter=4 order=1`: mesma entidade gerenciada permaneceu WAITING_PAYMENT após commit da outra transação e mudou para PAID pelo fragmento real find+refresh. Sessions resource-local independentes, mesma URL, READ_COMMITTED/autoCommit=false; rollback do leitor conservou o commit do escritor. Essa prova intercala duas transações no mesmo thread e banco; **não é experimento de duas chamadas HTTP nem garante commit no retorno de um serviço chamado dentro de transação externa**. IDs de sessão são locais a cada banco/contexto.

Contextos reais isolados: HTTP ROOT compartilhou `admin-product-test-c1d353f2-44f3-4502-abdb-4adc638f8e5f`; repository usou `admin-product-test-ccaea786-6ecd-4b57-8d79-00f30c7a2a03`; constraints usaram `admin-product-test-1a28b289-05dc-413d-97d2-d334c8887a86`. Nenhuma inferência de conexões compartilhadas entre bancos distintos. Casos HTTP desta seleção usam ROOT; /api não foi executado novamente.

Os oito controles T016 passaram: limites inclusivos201, imediatamente fora400, referências nativas `.123000001`/`.123999999` conservadas durante avanço do Clock e espera física; revogação de ADMIN isolado/ambos sob lock produziu403 apesar de roles antigas pré-carregadas. Rejeições conservaram o baseline apropriado, separado da alteração externa de perfil. Os13 controles T007 passaram: schema real/PK compartilhada/FK nomeada/NOT NULL, status/colunas, precisão exata, duplicata23505, órfão23506, nulos23502 e remoção referenciada23503, sem sobrescrita ou mutações alheias. Erros SQL esperados nesses controles JDBC não são falhas JUnit ou respostas409 da operação HTTP.

### Revisão T021 do núcleo e classificação segura

Revisados [OrderPaymentLockRepositoryImpl.java](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryImpl.java), [PaymentInsertRepositoryImpl.java](../../src/main/java/com/devsuperior/dscommerce/repositories/PaymentInsertRepositoryImpl.java) e [PaymentRegistrationService.java](../../src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java). O núcleo recebido já atende: find PESSIMISTIC_WRITE seguido de refresh, lock somente da raiz por ID, sem filtro de estado/titular/JOIN/DISTINCT/Product/coleção; estado e Payment atuais verificados sob o lock mantido até conclusão. Inserção é EntityManager.persist, sem merge/save/UPDATE de Payment. SQL observado pelos testes exigiu dois locks de raiz, um insert Payment e um update Order no sucesso, apenas SELECT na rejeição e nenhuma leitura de Product. Históricos, estrutura e inteiros exatos dos oráculos permaneceram inalterados.

Somente `payments.findById(orderId).isPresent()` gera ALREADY_REGISTERED/409 `Payment already registered`. Sem Payment, estado diferente de WAITING_PAYMENT gera INCOMPATIBLE_STATE/409 `Order is not awaiting payment`. Não há catch genérico de DataIntegrityViolationException/PK/FK/flush/timeout/deadlock/pool, consulta/retry em transação falha, compensação ou sucesso reaproveitado. Exceção de banco sai do corpo/proxy para rollback e o handler scoped a trata como500 seguro. Falha interna não vira entrada400 nem repetição409.

A classificação interna foi confrontada com o código do handler e testes de serviço recebidos: asserções existentes de falhas em lock/payment-lookup/history/persist/flush exigem propagação da mesma causa e ausência de recuperação; são evidências históricas do C2, **não novos casos dessa seleção S3**. PAID sem Payment/409 específico também permanece coberto pelos testes anteriores preservados. Nesta execução não se injetaram falhas HTTP de commit, deadlock, pool ou transporte; essas provas posteriores não foram antecipadas. Constraints JDBC e sucesso concorrente não são apresentados como injeção HTTP500.

**Nenhuma correção foi necessária**, produtiva ou de teste. GREEN observado uma vez com os três Java recebidos intactos; não se criou RED artificial ou alteração cosmética, não se enfraqueceu comparação/assertion e não se mudou suporte, mapper, produção ou configuração. Somente o script derivado local de análise de relatórios foi ajustado conforme descrito acima.

### Preservação, revisão e limites finais

Evidências históricas, inclusive diagnóstico S2 inicial839/814/25, correção dos três oráculos, C2 conjunto839 aprovados/exit0 e50 controles adicionais do parser identificados separadamente, continuam históricas e preservadas, sem reexecução. O recibo contém hashes de357 arquivos recebidos e7.088 arquivos anteriores de evidência/relatórios. A revisão final confere355 arquivos recebidos fora dos dois documentos autorizados, três Java recebidos byte a byte, prefixo integral do quickstart, somente checkbox T021 alterada e preservação dos396 arquivos Surefire anteriores; os dez novos relatórios têm o sufixo próprio. Diff, arquivos novos, referências/links e whitespace são conferidos sem stage.

Comandos auxiliares realmente executados: `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` exit0; `.\mvnw.cmd -v` exit0; scripts locais `T021-prepare.ps1`/`T021-run.ps1` exit0; `T021-analyze.ps1` exit1 inicial de parser, depois exit0 sobre os mesmos relatórios. Revisão final e seus resultados registrados abaixo; esses exits documentais não são contagens/exit codes de testes.

**T021 atendida por revisão e diagnóstico GREEN, sem alteração de núcleo. Somente T021 é marcada; T001–T021[X], T022–T036 abertas.** C3/T022, histórias posteriores e FINAL continuam pendentes. Resultado local Windows/H2/ROOT, sem CI/Linux/PostgreSQL, throughput, SLA ou durabilidade/crash recovery. Callbacks Spring não instrumentam commit físico do driver; StatementInspector captura preparação Hibernate, não todo JDBC; snapshots compostos não detectam toda alteração transitória revertida. A validação observada é dos caminhos e intercalamentos controlados desta seleção.

Sem stage/commit/push/merge, troca de branch ou alteração de main. Git final esperado conserva os dois documentos modificados e os mesmos três Java não rastreados, índice vazio, mesma raiz/branch/HEAD/seleção. Nenhum impedimento funcional, de preparação ou coordenação permaneceu nesta execução.

Resultado final efetivo: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/T021-review.ps1` exit0. final-review.json confirma355 arquivos recebidos preservados fora dos documentos,7.088 evidências anteriores idênticas por SHA256,396 relatórios antigos preservados e somente5XML+5TXT novos idênticos às cópias arquivadas. Prefixo recebido do quickstart294.593 bytes intacto; somente checkbox T021 alterada em tasks. Conferidos129 links locais e45 referências de imports dos três Java,18 métodos/37 casos sem duplicatas ou omissões, índices parametrizados contíguos e nenhum caso herdado/aninhado omitido. `git diff --check` exit0; os três `git -c core.autocrlf=false diff --no-index --check -- /dev/null <Java>` terminaram exit1 sem diagnóstico de whitespace (arquivo novo contra /dev/null). Avisos LF/CRLF do Git são de conversão futura e não modificaram os bytes recebidos.

Git final conferido: somente quickstart.md/tasks.md modificados e os mesmos três Java não rastreados nos caminhos listados acima, sem mudança de conteúdo; nada staged. Mesma raiz, branch, HEAD, seleção e main. As únicas alterações desta T021 são a seção acrescentada no quickstart e seu marcador em tasks; relatórios/metadados locais ficam ignorados em target. Sem correção de produção/testes, repetição de Maven, execução T022+, stage, commit ou push.

## T022 — checkpoint conjunto C3/US3 (2026-10-22)

### Escopo, preparação e execução nova

Aplicada speckit-implement exclusivamente à [T022](tasks.md), lida integralmente com T021 e as dependências T019/T020, constraints T007 e controles T016. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `1b49dc1b61f27ccecbe7aa561dfc56fc4da93125`, seleção local008 e nada staged. Recebidos somente quickstart.md/tasks.md modificados e os três Java não rastreados nos caminhos exatos:

- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentConcurrencyIntegrationTest.java`;
- `src/test/java/com/devsuperior/dscommerce/controllers/PaymentDistinctOrdersIntegrationTest.java`;
- `src/test/java/com/devsuperior/dscommerce/repositories/OrderPaymentLockRepositoryTest.java`.

Confrontados spec US3.1–5, plan/research/data-model, contrato POST, constituição/harness e procedimentos aprovados. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Checklist requirements.md:16/16, zero aberto. Ignore Java adequado; extensions.yml ausente, sem hooks antes/depois. T021 foi recebida concluída sem correção de código. Os37 testes do diagnóstico T021 permanecem históricos separados; **C3 abaixo decorre de uma nova execução conjunta solicitada expressamente nesta T022**.

Na raiz/PowerShell, uma única invocação de testes, sem builds simultâneos, baseline, C2, seleção adicional ou suíte completa:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentConcurrencyIntegrationTest,PaymentDistinctOrdersIntegrationTest,OrderPaymentLockRepositoryTest,PaymentPersistenceIntegrationTest,PaymentTemporalAndCurrentRolesIntegrationTest" "-Dsurefire.reportNameSuffix=S3-T022-20261022T172253803" test
~~~

Wrapper3.9.16 e Java Azul Zulu21.0.12.1 confirmados por `.\mvnw.cmd -v`, exit0; Windows10 amd64, Boot3.3.4/Hibernate6.5.3.Final/H2 2.2.224 existentes. Início `2026-10-22T17:23:24.0573001-03:00`, término `2026-10-22T17:24:28.7070885-03:00`; Maven reportou59.582s. **BUILD SUCCESS, exit0,37 aprovados, zero falhas/erros/ignorados**. Sem nova compilação necessária. Avisos conhecidos de VM/ByteBuddy/H2Dialect não são falha de teste ou timeout operacional.

Recibo exclusivo ignorado `target/feature008-evidence/S3-T022-20261022T172253803/`: run.json/environment.txt/console.txt/exit.json, received.json/cópias/diff/status/HEAD/main iniciais, previous-evidence.json, previous-surefire-reports/, surefire-reports/, analysis.json/cases.json/coverage.json e final-review.json. Os406 arquivos Surefire recebidos foram copiados antes do Wrapper; cinco XML e cinco TXT novos usam somente o sufixo T022 e foram arquivados após término. Nenhum relatório anterior foi apagado/sobrescrito. Console UTF-16 via Tee-Object com stderr mesclado e LASTEXITCODE capturado imediatamente; metadados JSON UTF-8.

### Conciliação efetiva, sem inferir contagem da T021

| Classe | Métodos | Invocações desta execução | Falhas/erros/ignorados |
| --- | ---: | ---: | --- |
| PaymentConcurrencyIntegrationTest | 4 | 13 | 0/0/0 |
| PaymentDistinctOrdersIntegrationTest | 1 | 2 | 0/0/0 |
| OrderPaymentLockRepositoryTest | 1 | 1 | 0/0/0 |
| PaymentPersistenceIntegrationTest | 10 | 13 | 0/0/0 |
| PaymentTemporalAndCurrentRolesIntegrationTest | 2 | 8 | 0/0/0 |
| Total | **18** | **37** | **0/0/0** |

Conferidos console, cinco TXT e cinco XML próprios:37 elementos testcase correspondem ao resumo global e aos contadores das cinco suites; não somados arquivos históricos. coverage.json concilia cada um dos18 métodos com seu número efetivo: T0194+4+4+1; T0202+1; persistência oito simples mais parâmetros3+2; tempo/perfis6+2. cases.json identifica classname/nome/índice de cada invocação. Índices parametrizados contíguos, zero identidades duplicadas, sem classe/método omitido ou extra. As classes selecionadas não têm @Nested e o suporte HTTP herdado não acrescenta testes; sem @Disabled/assumption/seleção vazia. `T022-analyze.ps1`: exit0 sobre os relatórios novos, sem correção do leitor ou repetição do Wrapper.

### Cinco cenários US3 e provas complementares

| Cenário / método principal | Resultado nesta execução |
| --- | --- |
| US3.1–2: sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal, quatro variantes | Quitação original201; repetição igual/diferente e mesmo/outro ADMIN409 `Payment already registered`; ocorrência original e snapshot inteiro preservados, sem sucesso reutilizado. São repetições sequenciais, não provas concorrentes. |
| US3.3: existingPaymentPrecedesWaitingStateWithoutRepair, quatro variantes | Setup commitado põe WAITING_PAYMENT com Payment anterior; novo409 por repetição, sem sobrescrever ocorrência nem reparar estado. Baseline da rejeição posterior ao setup inconsistente. |
| US3.4: overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment, quatro variantes | ADMIN A/B como primeiro detentor e ocorrências iguais/diferentes; exatamente201+409, um commit, rollback da perdedora, um Payment/PAID e ocorrência vencedora conservada. |
| US3.5: validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions, um controle | Duas raízes adquiridas com transações simultaneamente abertas, ambas201, históricos20,00/22,50 e vínculos próprios preservados. |
| US3.5: distinctOrderCommitsWhileTheOtherStillWaitsForItsRootLock, duas variantes | Um pedido confirma201/commit/JDBC enquanto a outra raiz continua BLOCKED; depois da liberação, outro201, históricos20,00/42,50 e canário8,00 conservados. |

Na disputa de mesmo pedido, sessões H2 físicas3/4 e threads de serviço distintas, mesma URL e transações novas READ_COMMITTED, sem transação externa HTTP. Metadados T019_WAIT observaram sessão4 bloqueada pela3 antes da mutação e novamente após flush em beforeCommit. Liberado o commit, aquisição/refresh da perdedora retornou PAID; somente depois ela avançou para409. Callback/retorno do proxy e JDBC independente confirmaram o vencedor antes da rejeição final sem mutações. Esses marcos foram alcançados nas quatro variantes, não deduzidos de dois POSTs sequenciais ou de Future pendente.

Na prova forte de pedidos distintos, console registrou `committedOrder=28 session=4 blockedOrder=27 session=3 holder=295` e `committedOrder=30 session=4 blockedOrder=31 session=3 holder=318`. A transação JDBC auxiliar mantinha a raiz escolhida; SESSION_STATE=BLOCKED/BLOCKER_ID foram exigidos antes e depois da confirmação do outro pedido. Snapshot intermediário admitiu só a quitação livre; final exigiu exatamente os dois vínculos/ocorrências e somente os dois status alterados. Ambas as transações e a auxiliar usaram conexões independentes no mesmo banco. Isso comprova independência por pedido nesses intercalamentos, sem mutex global no desenho observado, não throughput ou SLA.

O teste lockAndRefreshReplacePreloadedWaitingStateWithAnotherTransactionsCommittedSettlement passou com `reader=3 committedWriter=4 order=1`: entidade gerenciada pré-carregada WAITING_PAYMENT continuou obsoleta após commit de outra Session; fragmento real find+refresh com PESSIMISTIC_WRITE devolveu o mesmo objeto PAID. Rollback do leitor não desfez a quitação do escritor. Duas transações resource-local sobrepostas no mesmo thread, READ_COMMITTED/autoCommit=false, sessões distintas no mesmo banco; prova de repository, **não de commit HTTP com transação externa**. SQL de aquisição exigiu dois locks só da raiz por ID, sem predicado de estado/titular, JOIN/DISTINCT/Product/coleção. Asserções também confirmaram readquisição das raízes por conexão nova após conclusão, com rollback e timeouts locais2s.

Os13 controles de persistência passaram no schema gerado real: PK compartilhada/FK nomeada/NOT NULL, colunas/status preservados e round-trip temporal integral. Duplicata23505, órfão23506, nulos23502 e remoção referenciada23503 rejeitados no JDBC, com snapshots preservados. Esses erros SQL esperados não são falhas JUnit nem conflito409 inferido da API. Equivalência Payment/PAID é regra do serviço, não CHECK universal entre tabelas; o caso inconsistente preparado para US3.3 continua deliberado e não é reparado.

Os oito controles T016 passaram: limites inferior/superior inclusivos201, imediatamente fora400, referência nativa `.123000001`/`.123999999` conservada durante espera e avanço do Clock, uma leitura de negócio. Revogação de ADMIN isolado/ambos em outra transação durante o lock produziu403 mesmo com roles antigas gerenciadas; baseline separou a alteração externa da tentativa, sem mutação por rejeição. Coordenação usa gates/latches/futures finitos10s e metadados H2 com prazo8s/queryTimeout2s; parkNanos5ms limita apenas a cadência de polling. Sem sleeps arbitrários, NOWAIT, SKIP LOCKED ou retry para obter sucesso. Nenhum timeout/deadlock/pool/erro de preparação foi contado como sucesso concorrente.

Snapshots e SQL dos sucessos/rejeições conservaram históricos, ordem/valores dos itens, catálogo, compradores/criação, contas/perfis/credenciais e demais pedidos. Somente Payment e status do alvo mudaram no sucesso; o probe das oito tabelas e a limpeza seletiva/baseline passaram. Mantidos os oráculos de igualdade estrutural JSON completa, IDs numéricos inteiros exatos/int64, sem strings/fracionários, ponto flutuante, tolerância ou remoção de campos; correção IntNode/LongNode recebida intacta.

### Classificação, ambientes e limites

Repetição409 é decidida pela constatação de Payment anterior e usa `Payment already registered`. Sem Payment, estado incompatível mantém409 `Order is not awaiting payment`; falha de persistência/interna mantém500 seguro, sem catch genérico que converta PK/FK/flush/timeout/deadlock/pool em duplicidade e sem consulta/retry na transação falha. Código/revisão T021 e controles históricos C2 dessa distinção preservados. Esta seleção S3 **não injeta nova falha HTTP500 nem executa o caso HTTP PAID sem Payment**; não atribuir os controles JDBC ao handler HTTP. Failpoints de commit/rollback/transporte continuam em T023+.

Ambientes efetivos nesta execução: HTTP real ROOT, porta53537, banco `admin-product-test-4793d2e4-5265-46c7-9f34-55cb421ea637`; repository em `admin-product-test-6ada09fd-db9c-4ba3-84c1-c9480ed73b11`; constraints em `admin-product-test-0720e76f-4fb2-4be1-8630-992e12517307`, todos H2 isolados pelo initializer/perfil test. IDs de sessão são locais a cada banco. **Nenhum HTTP /api nesta seleção**, nem CI/Linux/PostgreSQL, falha física de rede, durabilidade/crash recovery ou teste de carga. Callbacks Spring não instrumentam commit físico do driver; StatementInspector observa preparação Hibernate, não JDBC universal; snapshots compostos não detectam toda mutação transitória revertida.

**C3/US3 aprovado no alcance desta execução conjunta local. T022 atendida; somente seu marcador passa para[X]: T001–T022[X], T023–T036 abertas.** Não conclui C4, SC que depende também de C4, histórias posteriores, FINAL ou entrega/publicação da feature. Nenhuma correção necessária; produção, testes, asserções, suporte e mapper recebidos não foram alterados.

### Preservação e revisão final

Recibo inventaria357 arquivos recebidos e7.930 arquivos anteriores de evidência/relatórios, incluindo diagnóstico T021, S2 reprovado839/814/25, correção dos três oráculos, C2 conjunto839 aprovados/exit0 e50 controles adicionais do parser separados. Prefixo histórico do quickstart preservado; apenas checkbox T022 muda sobre tasks recebido. Revisão final verifica hashes dos355 demais arquivos recebidos, três Java byte a byte, todos os relatórios anteriores e os novos arquivados, diff/arquivos novos/imports/links/whitespace e estado Git.

Comandos auxiliares efetivos: pré-requisitos exit0; `.\mvnw.cmd -v` exit0; `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/T022-prepare.ps1`, `T022-run.ps1` e `T022-analyze.ps1`, todos exit0. Revisão final via `T022-review.ps1`, `git diff --check` e `git -c core.autocrlf=false diff --no-index --check -- /dev/null <cada Java recebido>` registrada abaixo. Exits documentais não substituem exit0 do Wrapper/contagens Surefire.

Sem stage/commit/push/merge ou alteração de main; nada T023+ executado, sem baseline/suíte completa. Resultado e Git final efetivos registrados após revisão abaixo.

Revisão final efetiva: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/T022-review.ps1` exit0. final-review.json confirmou355 arquivos recebidos fora dos dois documentos idênticos por SHA256, inclusive os três Java;7.930 evidências anteriores preservadas,406 arquivos Surefire antigos inalterados e5XML+5TXT novos idênticos às cópias arquivadas. Prefixo recebido do quickstart309.501 bytes intacto; tasks difere do recebido somente pelo marcador T022. Conferidos130 links locais,45 referências de imports dos Java,18 métodos/37 invocações e índices parametrizados, sem omissões/duplicatas ou casos herdados/aninhados adicionais. `git diff --check` exit0; cada Java em `diff --no-index --check` exit1 sem diagnóstico de whitespace, pela comparação de arquivo novo contra /dev/null. Avisos LF/CRLF do Git não alteraram os bytes recebidos.

Git final conferido: quickstart.md/tasks.md modificados; somente PaymentConcurrencyIntegrationTest.java, PaymentDistinctOrdersIntegrationTest.java e OrderPaymentLockRepositoryTest.java não rastreados nos caminhos recebidos, sem alteração de conteúdo; nada staged. Mesma raiz/branch/HEAD/seleção/main. As únicas alterações desta T022 são esta seção do quickstart e seu marcador em tasks; metadados/relatórios ficam ignorados em target. Sem stage/commit/push ou execução T023+; nenhum impedimento funcional, de preparação ou coordenação observado nesta seleção.

## T023 — autoria do suporte de falhas transacionais (2026-10-22)

Aplicada speckit-implement exclusivamente à [T023](tasks.md). Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `6111c6ba04b9f642bb2963f81c2b7bf333e4eb40`, seleção local008, Git limpo e índice vazio. T022 recebida concluída no commit US3, com C3 conjunto37 aprovados/zero falhas-erros-ignorados/exit0 como evidência histórica, sem reexecução. Lidos integralmente T023, dependência T022 e limites T024–T027, além de spec US4, plano/pesquisa/modelo, contrato POST, quickstart, constituição/AGENTS/harness. Referências examinadas: [instrumentação de produtos](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductFailureInjectionTestConfig.java), [OrderRollbackIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/OrderRollbackIntegrationTest.java) e observação008 existente.

Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Checklist requirements.md:16/16, zero aberto, PASS documental. Ignore Java adequado; extensions.yml ausente antes/depois, sem hooks. T023 é **criação de suporte**, não checkpoint executável: **nenhum Maven/Wrapper, javac, compilação, contexto Spring/H2, teste, login ou HTTP foi executado**. Nenhum novo RED/GREEN, console/Surefire, exit de testes ou comprovação de atomicidade é atribuído a esta autoria. Não foram repetidos C3, C2, BASE ou suíte completa.

Criado somente [PaymentFailureInjectionTestConfig.java](../../src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java). Configuração de teste com import explícito e perfil test; não contém @Test ou casos JUnit. Nenhum arquivo anterior de produção, suporte ou teste foi editado. Asserções/evidências T001–T022 e inteiros/estrutura dos oráculos anteriores preservados.

### API e isolamento escritos, ainda não exercitados

`FailureProbe.open(orderId, point, observation)` cria Run AutoCloseable com identificador exclusivo. O consumidor futuro deve preparar fixture/login antes de abrir o Run e enviar exatamente um POST do pedido com `PaymentFailureInjectionTestConfig.HEADER` e `run.headerValue()`. O filtro associa esse request ao Run por ThreadLocal, remove-o em finally e sinaliza término. Request sem header, setup/login/cleanup e demais serviços não estão armados. Escopo errado/reutilizado/desconhecido falha explicitamente. O caminho é relativo ao servlet context; isso prepara ROOT/Api, **sem comprovar execução em nenhum deles nesta T023**.

O probe exige serviço PaymentRegistrationService real, proxy Spring original mutável com um TransactionInterceptor e target singleton. Insere observadores imediatamente fora/dentro desse interceptor, delegando ao corpo real. Verifica ausência de transação externa, transação nova READ_COMMITTED gravável, conexão vinculada sem autoCommit, URL H2 isolada/create-drop, sessão física e thread. Registra entrada, DTO retornado pelo corpo, saída/falha do proxy e ausência de recursos transacionais depois da saída. Não cria serviço/repository falso nem substitui o resultado de negócio.

Para observar o flush explícito existente, decora **somente a referência EntityManager do serviço008 no contexto de teste**, por reflexão no target original; o decorador delega ao mesmo shared EntityManager transacional e registra tentativa/retorno de flush. Não troca a factory, mapper ou EntityManager global, nem muda propagação/flush mode. Ao destruir o contexto, remove seus advices e restaura a referência original; falha de instalação desfaz a alteração parcial. O DataSource local é decorado para observar Connection/Statement reais, preservando delegação/fechamento; captura/injeção só ocorre na conexão dona do serviço marcado. Configuração não importada permanece fora desses contextos.

### Pontos e alcance exato previstos

| Point | Interceptação escrita | O que não comprova |
| --- | --- | --- |
| NONE | Nenhuma injeção; registrar execução normal, flush/DTO/completion e tentativa/delegação/sucesso de commit/rollback | A existência do modo não é controle positivo executado. |
| SQL_BEFORE_FIRST_DML | SQLException sintética08006 antes de delegar o primeiro execute DML | Não é SQL efetivamente rejeitado pelo H2, FK/PK real ou pane física. |
| AFTER_FIRST_DML | Após o driver retornar do primeiro DML com contagem positiva; guardar SQL e linhas afetadas, independentemente de ser insert Payment ou update Order | Preparação SQL não basta; não presumir ordem das duas mutações nem commit. |
| AFTER_FLUSH | Depois do retorno do EntityManager.flush explícito do serviço | Flush não é commit; flush interno automático do Hibernate não é esse ponto. |
| AFTER_BODY_DTO | DTO PaymentConfirmationDTO real já retornado pelo corpo, ainda dentro do TransactionInterceptor | Não intercepta dentro do construtor/mapeador e não prova entrega HTTP. Não usar como prova de falha interna de construção do DTO. |
| BEFORE_COMMIT | Callback Spring com transação/conexão ainda ativas, antes do commit JDBC | Não é falha do driver. |
| JDBC_COMMIT_BEFORE_DELEGATE | Tentativa de Connection.commit observada; SQLException sintética antes de invocar o driver, delegação/sucesso permanecem separados | Não reproduz commit físico falho, crash ou resultado indeterminado. |

Sete modos, seis pontos de injeção; **zero testes/invocações/failpoints executados nesta tarefa**, sem previsão de contagem JUnit para classes T024/T025 ainda não escritas. Falhas SQL realmente lançadas pelo delegate são registradas separadamente em FailedDml com SQLState; a injeção anterior à delegação não se apresenta como erro vindo do driver. Batches/contagens indeterminadas/unwrap nativo que impediriam observar a primeira mutação falham como instrumentação não suportada, sem inferir sucesso. Não há mudança para presumir insert antes de update, retry, sleeps ou mutex global.

PointObservation recebe a conexão da própria transação para **leituras locais ainda não commitadas** e State com cópias dos eventos/contadores. O observador não pode executar DML ou finalizar/reconfigurar essa conexão; falha de callback vira InstrumentationFailure distinta de InjectedFailure, fica registrada e é propagada ao consumidor por verifyInstrumentation/close, mesmo que a fronteira HTTP apresente500. Não se aceita envelope500 sozinho como prova de que o ponto correto foi alcançado. Preserva causa sentinela por ponto, quantidade de hits e indicação de injeção efetiva.

PreparedStatement/Statement/CallableStatement delegam ao driver; contagem positiva é registrada só depois de execute retornar. Batches são rejeitados explicitamente no escopo instrumentado. `getConnection` dos statements conserva a conexão observada; Object methods usam identidade. Statements criados no escopo têm queryTimeout10s; identificação de sessão usa2s. A espera por término do request é limitada10s, sem threads/executores próprios. Falha ao montar wrapper/timeout fecha o recurso adquirido. Run.close exige observação completa e remove o registro; se houver request pendente, o filtro remove seu registro ao terminar e a falha de timeout permanece explícita. Contexto acusa escopos esquecidos, sem limpar tabelas globais.

### Flush, DTO, commit e leitura posterior

State distingue flushReturns/bodyReturns de proxyReturns e dos contadores commitAttempts/delegatedCommits/successfulCommits, rollbackAttempts/delegatedRollbacks/successfulRollbacks, afterCommit e afterCompletion. `bodyScopeFailures` inclui exceções do observador de corpo, inclusive injeção após o DTO. O retorno externo bem-sucedido exige observação coerente de commit JDBC bem-sucedido e completion COMMITTED; não se transforma flush ou DTO em commit. Completion UNKNOWN é registrado como tal, sem inventar rollback. Os pontos são anteriores à efetivação; este suporte não injeta falha após commit para prometer desfazimento.

Procedimento futuro do consumidor: aguardar a resposta/filtro com `run.awaitFinished()`, verificar a instrumentação e chamar `run.requireOutsideTransaction()` antes da leitura por `PaymentRegistrationDatabaseProbe`/conexão física independente. Comparar baseline das oito tabelas depois da saída do proxy, antes da limpeza seletiva. Para um futuro filtro de resposta ainda no mesmo request, `run.stateAfterProxy()` só disponibiliza estado após saída do proxy e ausência de transação; não espera o próprio request terminar. Commit/completion e a leitura independente continuam necessários para distinguir commitado, revertido e desconhecido. Nenhuma leitura final, baseline ou assertion de resposta HTTP é fabricada automaticamente pelo suporte.

Payment e PAID continuam responsabilidade da mesma transação produtiva recebida. T024 deverá escrever as provas de falhas/controles e snapshots; T025, os experimentos separados de entrega/transporte após commit, sem apagar quitação nem reaproveitar sucesso. Nenhum filtro de buffer/socket/proxy de transporte, teste HTTP/Api, failpoint dentro do construtor DTO ou constraint SQL temporária desses experimentos foi implementado nesta T023. Os pontos acima só podem provar o estágio que realmente interceptarem quando executados. Não se declara perda física de resposta, falha de banco, crash recovery ou durabilidade por essas simulações.

### Preservação, revisão e conclusão de autoria

Recibo ignorado `target/feature008-evidence/FAILURE-SUPPORT-T023/` guarda hashes dos357 arquivos recebidos, documentos recebidos, main/status inicial e8.784 arquivos anteriores de evidência/relatórios. Revisão final estática confere355 arquivos fora dos documentos autorizados, prefixo integral do quickstart, somente checkbox T023 alterada, inventário416 Surefire inalterado, conteúdo/referências/imports/links do Java novo e whitespace. Isso não verifica compilabilidade, criação de beans, decoração/ordem de interceptores, propagação das exceções ou comportamento JDBC/rollback/cleanup em execução.

Comandos desta tarefa: pré-requisitos exit0; leitura/revisão PowerShell/.NET e Git, sem comando Java/Maven. `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/FAILURE-SUPPORT-T023/review.ps1`, `git diff --check` e `git -c core.autocrlf=false diff --no-index --check -- /dev/null src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java` têm resultados efetivos registrados após execução abaixo. Exit1 de diff contra /dev/null sem diagnóstico representa arquivo novo, não teste reprovado.

Critérios de **autoria T023** atendidos após revisão. Somente T023 marcada: **T001–T023[X], T024–T036 abertas**. Nenhuma nova comprovação executável/RED/GREEN/C4, Linux/CI ou outro banco. C3/C2 e controles adicionais do parser continuam evidências históricas separadas. Compilação, ativação do suporte e provas de atomicidade/transporte permanecem pendentes nas etapas próprias. Sem stage, commit, push, merge, troca de branch ou alteração de main.

Resultado efetivo da revisão: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/FAILURE-SUPPORT-T023/review.ps1` exit0. static-review.json confirma355 arquivos recebidos fora dos dois documentos preservados por SHA256,8.784 evidências anteriores idênticas e inventário exato dos416 arquivos Surefire inalterado. Prefixo recebido do quickstart323.504 bytes intacto; somente marcador T023 alterado em tasks. Conferidos134 links locais, dois imports locais do Java novo, zero métodos JUnit/casos executados e zero failpoints executados. `git diff --check` exit0; Java novo em `diff --no-index --check` exit1 sem diagnóstico de whitespace. Revisão do conteúdo integral, exceções checked/unchecked, delegação/recursos e limites foi estática, sem estabelecer compilabilidade ou funcionamento dos instrumentos.

Git final: somente quickstart.md/tasks.md modificados e `src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java` não rastreado; nada staged. Raiz/branch/HEAD/seleção/main preservados. Nenhum stage/commit/push ou trabalho T024+; sem impedimento de ambiente diagnosticado por execução, porque compilação/testes não integram esta autoria.

## T024 — autoria das provas de rollback (2026-10-22)

Aplicada speckit-implement exclusivamente à [T024](tasks.md), dependente da T023. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `6111c6ba04b9f642bb2963f81c2b7bf333e4eb40` e seleção local008. Estado recebido: somente este quickstart/tasks modificados, somente `src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java` não rastreado e índice vazio. Lidos tarefa/dependência, limites T025–T027, spec US4, plano, pesquisa/modelo, contrato POST, suporte recebido, constituição/AGENTS/harness e referências de rollback. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0; checklist requirements16/16, zero aberto. Ignore Java adequado; extensions.yml ausente, sem hooks.

T024 determina **escrever os testes**, sem checkpoint executável próprio. Criados [PaymentRollbackIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackIntegrationTest.java) e [PaymentRollbackApiHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackApiHttpIntegrationTest.java). **Nenhum Maven/Wrapper, javac, compilação, contexto Spring/H2, login, HTTP ou teste foi executado.** Não há resultado RED/GREEN, failpoint atingido, contagem Surefire ou exit de testes desta autoria. Compilabilidade, descoberta JUnit, instalação/ordem dos decoradores, resposta500/201, rollback/commit e limpeza em execução continuam pendentes. C3/C2/BASE e suíte completa não foram repetidos. A seleção conjunta S4/T027 permanece futura e não foi reduzida nem antecipada.

### Matriz escrita, sem contagem executada

Os dois concretos herdam `PaymentRollbackScenarios`, que estende o suporte HTTP real recebido; a única diferença é servlet context ROOT ou `/api`. Cinco métodos JUnit compartilhados: três @Test e dois @ParameterizedTest, com matrizes explícitas6+5. Cada concreto declara zero métodos de teste próprios; não há @Nested, @Disabled ou assumptions. **Previsão estática:14 invocações por contexto,28 no total; efetivamente executadas:zero.** Esta contagem não comprova descoberta pelo Surefire e deverá ser conciliada com console/XML na etapa executável.

| Método compartilhado | Previstas por contexto | Oráculo escrito |
| --- | ---: | --- |
| reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows | 6 | Ponto solicitado atingido uma vez, causa sentinela, estágio/SQL/flush/DTO/commit compatíveis,500 seguro e baseline integral após saída do proxy. |
| realDatabaseConstraintRejectsPaymentInsertWithoutBeingClassifiedAsRepeat | 1 | CHECK temporário exclusivo rejeita insert real com SQLState23513 e nome da constraint na causa;500, sem classificação genérica409. |
| confirmationConstructorFailureAfterRealFlushRollsBackPaymentAndPaidTogether | 1 | Flush real retornou; construtor produtivo real rejeita valor interno inválido, sem retorno do DTO e sem commit. |
| internalFailureIsNotAuthenticationNotFoundOrRepeatAndLeavesNoCommittedMutation | 5 | Falhas internas em consulta de perfis atuais, lock da raiz, lookup Payment, persistência e antes da delegação do flush continuam500 seguro, não401/404/409. |
| positiveControlConfirmsCommitBefore201AndPreservesOtherOrdersAndHistory | 1 | Sem injeção,201 com estrutura/valores exatos, commit JDBC delegado/bem-sucedido, completion COMMITTED, PAID+um Payment e históricos preservados. |

### Estágios e limites da simulação

Os seis Points recebidos da T023 são consumidos sem modificar seu Java. SQL_BEFORE_FIRST_DML é SQLException sintética08006 antes da delegação; não é erro SQL físico. AFTER_FIRST_DML exige retorno positivo de exatamente um DML real e lê seu efeito na própria conexão, admitindo insert Payment ou update Order como primeiro, sem impor ordem. AFTER_FLUSH exige ambos os DML e retorno do flush explícito; ainda não há commit. AFTER_BODY_DTO exige DTO real já construído e é tratado como falha posterior à construção, **não como falha dentro do mapeamento**. BEFORE_COMMIT exige callback Spring anterior à tentativa JDBC, sem atribuí-lo ao driver. JDBC_COMMIT_BEFORE_DELEGATE exige uma tentativa observada, zero delegações e zero commits bem-sucedidos; a SQLException sintética não reproduz falha física durante/depois do commit nem resultado indeterminado do banco.

A asserção posterior exige rollback bem-sucedido e completion ROLLED_BACK nos erros anteriores à chamada de commit. No ponto JDBC pré-delegação, completion do framework pode ser ROLLED_BACK ou UNKNOWN; contadores de rollback permanecem distintos, sem inventar chamada ao driver. Em ambos os casos exige zero commits delegados/bem-sucedidos, zero afterCommit e baseline restaurada por conexão independente. UNKNOWN do framework, isoladamente, não é prova de rollback nem transforma esta injeção conhecida pré-delegação em experimento de commit físico incerto.

A falha SQL real é separada: conexão independente instala CHECK com UUID somente para o ID da fixture em tb_payment antes do Run NONE. O insert deve chegar ao driver e falhar23513; SQLState/nome da constraint/FailedDml e causa SQLException são exigidos. DML anterior permitido deve ser update da raiz, sem pressupor sua presença. A constraint própria é removida em finally antes da limpeza da fixture; não se aceita500 por falha de preparação como prova do cenário.

Para a falha **dentro do mapeamento**, configuração local dos dois testes decora a referência EntityManager do serviço por fora do decorador T023 recebido. Após o flush real retornar, exige PAID+Payment com ocorrência original na mesma conexão e a mesma entidade gerenciada. Altera somente o occurredAt desse Payment de teste em memória para original+1ns e devolve normalmente do flush. O construtor produtivo PaymentConfirmationDTO deve então lançar IllegalArgumentException `Invalid payment confirmation`, com frame `<init>` da classe real; exige zero DTO retornado e rollback/baseline posteriores. A preparação registra eventual erro próprio e deve ser saudável antes de interpretar500. Isso exercitará a validação do construtor por corrupção interna controlada, não falha de serialização, rede ou driver, e não modifica mapper, produção ou suporte recebido.

As outras cinco injeções locais são sentinelas pré-delegação em advices dos repositories reais ou na referência EntityManager do serviço. A de persistência usa DataIntegrityViolationException inesperada para exigir500 seguro, sem transformar qualquer erro de gravação em repetição. Nenhuma finge falha SQL do delegate; exigem FailedDml vazio e causa exata. Perfis só são interceptados dentro do corpo do serviço marcado; autenticação JWT/filtros/login não são substituídos. Repetição409 e estado incompatível409 continuam contratos distintos recebidos, sem catch produtivo novo, retry ou continuação em transação falha.

### Observação independente, isolamento e controles

Cada requisição usa o proxy original e HTTP real com prazo finito. Após receber a resposta, `run.requireOutsideTransaction()` espera o filtro terminar, verifica instrumentação, saída do proxy e ausência de transação externa; só então ocorre leitura por nova conexão física DriverManager, na mesma URL, READ_COMMITTED/autoCommit e sessão diferente da transação observada. A leitura final exige WAITING_PAYMENT/nenhum Payment nas rejeições ou PAID/um Payment/ocorrência exata no controle. Readquisição da raiz FOR UPDATE, seguida de rollback da conexão leitora, verifica liberação do lock. QueryTimeout2s/lock_timeout2000, espera do Run10s e HTTP30s limitam operações; sem sleep, executores, futures ou coordenação concorrente nova. Leituras no callback da conexão da própria transação são explicitamente locais e não são apresentadas como estado commitado.

Snapshots independentes das oito tabelas são comparados após conclusão e antes da limpeza seletiva. Controle sem injeção exige sequência flush/DTO/beforeCommit/tentativa/delegação/sucesso JDBC/afterCommit/completion/saída do proxy antes de aceitar201. Só admite novo Payment do alvo e seu status PAID, com demais pedidos, vínculo do comprador, criação, itens, ordem, valores históricos42,50/20,00, catálogo, contas/perfis/credenciais e demais dados intactos. Rejeições exigem igualdade integral da baseline. JSON mantém estrutura inteira e valores exatos; ID deve ser número JSON inteiro convertível a int64, comparado por long exato, sem string, fração, float/double, tolerância ou remoção de campos. O esperado de sucesso é relido pelo mesmo parser, preservando a correção de representação IntNode/LongNode.

A configuração adicional está no próprio arquivo T024, @TestConfiguration importada explicitamente somente pelos dois concretos. Run/header/thread/serviço delimitam injeções; tráfego sem scope apenas delega. A referência recebida e os advices são restaurados/removidos na destruição, com reversão de instalação parcial. Scopes AutoCloseable removem seu registro mesmo se a verificação falhar, filtros limpam ThreadLocal em finally, fixture remove somente suas linhas (Payment antes de Order) e exige baseline de limpeza. Essas garantias estão **escritas, não demonstradas em execução**; falha de instrumentação/preparação/timeout deverá ser diagnosticada separadamente de defeito funcional.

Falha de transporte **posterior ao commit está fora da T024**: nenhum filtro de descarte de resposta, socket/proxy ou simulação de perda após efetivação foi criado; isso permanece T025. Nenhuma simulação daqui autoriza compensação, apagar pagamento ou inferir rollback por500/ausência de resposta. Não há prova física de rede/banco, crash, durabilidade, CI/Linux ou outro SGBD. ROOT e `/api` são contextos escritos, ainda não contextos executados nesta tarefa.

### Revisão de autoria e preservação

Recibo ignorado `target/feature008-evidence/ROLLBACK-T024/` preserva documentos e suporte recebidos, hashes de358 arquivos recebidos e8.792 arquivos anteriores de evidência/relatórios. Revisão estática PowerShell/.NET/Git confere os356 arquivos fora dos dois documentos, inclusive suporte T023 byte a byte; prefixo histórico integral do quickstart, apenas checkbox T024 sobre tasks recebido, inventário416 Surefire sem alteração, imports/links/conteúdo integral dos três Java não rastreados e whitespace. Nenhum desses comandos estabelece compilabilidade ou funcionamento dos failpoints.

Comandos desta autoria: pré-requisitos exit0; leituras/inspeções PowerShell/.NET/Git; `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/ROLLBACK-T024/review.ps1`, `git diff --check` e `git -c core.autocrlf=false diff --no-index --check -- /dev/null <cada um dos três Java não rastreados>` com resultados efetivos registrados após a revisão abaixo. Exit1 de diff contra /dev/null sem diagnóstico indica arquivo novo, não teste reprovado. Não há exit code Maven nem relatório novo de teste.

Critérios de **autoria T024** atendidos após revisão; somente T024 passa a[X]: **T001–T024[X], T025–T036 abertas**. Suporte T023, produção, mapper, implementação/assertions T001–T022 e evidências anteriores preservados. Nenhuma aprovação C4/S4, RED/GREEN ou entrega da Feature008 é atribuída à autoria. Pendentes compilação/instrumentação/provas executáveis nas etapas próprias, além de T025–T036. Sem stage, commit, push, merge, alteração de main ou trabalho T025+.

Resultado efetivo: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/ROLLBACK-T024/review.ps1` exit0. static-review.json confirmou356 arquivos recebidos fora dos dois documentos idênticos por SHA256, inclusive Java T023 (`EA05D181254D015361E7F7C5258562D0956F75BEB42C05056B45AFB203A025C0`);8.792 evidências antigas preservadas e inventário exato416 Surefire inalterado, sem relatórios novos. Prefixo recebido do quickstart335.983 bytes intacto; tasks recebido mudou somente em T024. Conferidos137 links locais,26 referências de imports dos três Java, cinco métodos compartilhados e matrizes6+5;14/28 são somente previsões, zero executado. `git diff --check` exit0; os três `diff --no-index --check` exit1 sem diagnóstico de whitespace, pela comparação contra /dev/null. Avisos LF/CRLF do Git não alteraram os bytes recebidos. Diff completo documental e conteúdo dos arquivos novos revisados; revisão estática não substitui compilação ou prova funcional.

Git final desta autoria: quickstart.md/tasks.md modificados; três não rastreados nos caminhos completos `src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java` (recebido intacto), `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackApiHttpIntegrationTest.java` (novos T024). Nada staged; raiz/branch/HEAD/seleção/main preservados. Alterações desta tarefa: dois testes novos, esta seção e marcador T024; recibos ficam ignorados em target. Sem stage/commit/push ou execução T025+; nenhum impedimento funcional/de preparação foi diagnosticado em execução, porque esta tarefa foi somente autoria.

## T025 — autoria das provas de entrega após commit (2026-10-22)

Aplicada speckit-implement exclusivamente à [T025](tasks.md), dependente da T023. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `6111c6ba04b9f642bb2963f81c2b7bf333e4eb40`, seleção local008 e índice vazio. Recebidos somente quickstart/tasks modificados e três Java não rastreados nos caminhos esperados: config/PaymentFailureInjectionTestConfig e controllers/PaymentRollbackIntegrationTest/PaymentRollbackApiHttpIntegrationTest. Lidos T025 integral, T023 e limites T024/T026–T027, artefatos pertinentes da spec US4/FR-014, plano/pesquisa/modelo, contratos POST/consultas008, quickstart, constituição/AGENTS/harness e suportes de observação/fixture/rollback. Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008. Checklist requirements16/16, zero aberto; ignore Java adequado; extensions.yml ausente, sem hooks.

T025 é **autoria de dois componentes de teste**, não execução S4. Criados somente [PaymentResponseDeliveryIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseDeliveryIntegrationTest.java) e [PaymentResponseTransportHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseTransportHttpIntegrationTest.java), além desta seção e marcador T025. **T023/T024 continuam sem validação executável. Nesta T025 também não houve Maven/Wrapper, javac, compilação, criação de contexto Spring/H2, login, MockMvc, HTTP, socket ou teste executado.** Não há failpoint atingido, byte de experimento medido, console/Surefire novo, exit code de teste ou RED/GREEN desta autoria. C3, C2, BASE e suíte completa não foram repetidos; T026 e S4/T027 não foram antecipados.

### Seleção escrita e resultados somente previstos

| Classe / método | Matriz escrita | Previsão estática |
| --- | --- | ---: |
| PaymentResponseDeliveryIntegrationTest.bufferedConfirmationIsDeliveredOrPreventedOnlyAfterIndependentCommitProof | FORWARD e WITHHOLD, MockMvc/filtro/buffer com serviço e persistência reais | 2 |
| PaymentResponseTransportHttpIntegrationTest.proxyReceivesCommittedConfirmationThenForwardsOrClosesWithoutDeliveringIt | FORWARD e WITHHOLD, HTTP/1.1 real e proxy/socket local | 2 |
| Total | Dois métodos @ParameterizedTest, nenhum caso adicional no suporte herdado, sem @Nested/@Disabled/assumptions | **4 previstas;0 executadas** |

Os modos FORWARD são controles positivos no mesmo caminho de cada experimento, não chamados sequenciais apresentados como concorrência. Classes usam ROOT explícito e contextos isolados pela configuração/imports existentes; nenhuma classe `/api` de transporte foi criada ou executada. A seleção conjunta aprovada S4/T027 permanece a de tasks, incluindo rollback, response e núcleo HTTP/concorrência; estes dois nomes são alcançados por PaymentResponse*Test. A contagem4 é apenas inspeção do código, não descoberta JUnit/Surefire. Compilação, métodos/índices/contextos efetivos e ausência de omissões/duplicatas deverão ser conciliados na execução própria.

### Experimento MockMvc: impedir entrega no buffer após concluir a transação

Configuração @TestConfiguration local, importada somente por PaymentResponseDeliveryIntegrationTest, registra filtro dentro do scope recebido T023 e antes de Security/MVC. Só o header Run exclusivo que possui BufferScope arma a interceptação; login/setup/reenvio/GET/HEAD e requests sem scope apenas delegam. O ContentCachingResponseWrapper recebe a resposta da aplicação real. Antes de copiar qualquer corpo para MockHttpServletResponse, exige201, JSON fechado de confirmação, ausência de Location/WWW-Authenticate, corpo gerado não vazio, resposta ainda não committed e corpo a jusante vazio.

Após retorno da cadeia, `run.stateAfterProxy()` exige saída do proxy original e ausência de transação ativa, sem esperar o próprio request. O oráculo então verifica commit JDBC e abre conexão física independente para confirmar Payment/PAID. Só depois o controle FORWARD chama copyBodyToResponse; WITHHOLD não copia e lança IOException sentinela exclusiva do filtro. O teste deverá exigir essa causa exata, um hit, nenhuma falha de preparação/instrumentação e **zero bytes de corpo no destino MockMvc**, além de confirmar novamente os dados após término do filtro. Portanto não se limita a ignorar um DTO/response devolvido ao teste; a entrega do corpo é efetivamente interceptada no filtro. A exceção de entrega ocorre fora do proxy dono da transação e não é tratada como rollback ou falha de commit.

T025_BUFFER foi escrito para registrar modo, bytes de confirmação gerados/entregues no MockMvc, eventos APPLICATION_RETURNED→JDBC_COMMIT_CONFIRMED→BUFFER_COPIED/BUFFER_DELIVERY_FAILED, marcos nanoTime e duração. Esses números ainda não existem como resultados. Trata-se de simulação da camada de aplicação sem socket, não observação física da transmissão; status interno201 no buffer não implica resposta recebida pelo consumidor. Erro do oráculo é registrado separadamente de IOException sentinela e faz a verificação falhar, mesmo que perform escape com exceção.

### Experimento HTTP/socket: confirmação recebida a montante, nenhum byte a jusante

OneShotProxy usa listener loopback em porta aleatória, um executor/uma aceitação e exatamente uma requisição a montante para o Tomcat real ROOT. O cliente é Socket cru, sem retry automático. Encaminha POST com login/JWT reais, header Run NONE e Content-Length explícito; Connection: close permite observar EOF do frame completo. O proxy lê resposta limitada, valida201/headers/corpo exato, espera término do Run/filtro, exige commit observado e leitura independente. Só então FORWARD escreve o frame recebido; WITHHOLD escreve **zero bytes de status/headers/corpo**, faz shutdownOutput e fecha os sockets. O cliente deverá receber EOF com array vazio; EOF antecipado por falha de preparação não basta, porque Future.get deverá retornar observação completa com os oráculos aprovados.

T025_SOCKET foi escrito para registrar bytes do request/body, resposta HTTP a montante (wire e JSON decodificado), bytes encaminhados e recebidos pelo cliente, marcos receivedAt/commitObservedAt/deliveryFinishedAt e duração nanoTime. No controle, exige confirmação201 completa e contagens de frame iguais; na interrupção exige montante não vazio, zero encaminhado e zero recebido, com commit conferido antes de encerrar a entrega. Nenhum payload de Authorization/credencial é impresso; metadados de contagem/marcos não incluem os headers do request.

O leitor de framing é local ao teste: HTTP/1.1, cabeçalhos até16KiB, resposta até64KiB, request body até2KiB, Content-Length exato ou chunked completo (alternativamente fechamento explícito), rejeitando frames incompletos/ambíguos. Aceita repetição legal de cabeçalhos como Vary; duplicatas de framing/scope falham. Chunk extensions/trailers, compressão, TLS, HTTP/2 e falhas físicas não são simulados; um formato não suportado é impedimento de preparação a diagnosticar, não aprovação ou defeito produtivo inferido.

Accept/request/read a montante têm limites8s; connect2s, leitura total do cliente12s, Future.get12s e término do worker3s. Deadline nanoTime recalcula SO_TIMEOUT, não sleep; queryTimeout2s nas leituras JDBC explícitas e limites T023/HTTP recebidos continuam. @Timeout120s limita cada caso nos dois concretos. Try-with-resources/finally fecham cliente, sockets a montante/jusante e listener; close cancela Future, encerra executor, espera término limitado e só depois permite limpeza da fixture. Registro sincronizado fecha também socket adquirido durante cancelamento. Interrupções preservam o sinal e timeouts/ExecutionException/erros de parsing/oráculo devem reprovar como preparação/observação, nunca ser aceitos no lugar do ponto planejado.

### Commit, dados finais e recuperação escrita

PaymentResponseRecoverySupport, incluído no primeiro arquivo, não contém testes JUnit; compartilha somente fixture e oráculos entre os dois concretos T025. Setup cria contas/perfis, login real de ADMIN pagador de terceiro, titular CLIENT e conta sem perfil; pedidos e dados próprios estão commitados antes de armar qualquer Run. Usa apenas Point.NONE; callback de failpoint transacional é proibido. Não injeta erro em Connection.commit/beforeCommit nem importa a instrumentação local adicional da T024.

O oráculo comum exige proxy real com transação nova READ_COMMITTED, ausência de transação externa antes/depois, flush/DTO, uma tentativa/delegação/sucesso de commit JDBC, afterCommit/completion COMMITTED e zero rollback. Nenhum flush ou DTO substitui esses marcos. Nova conexão DriverManager, mesma URL H2 e sessão física distinta, confirma PAID/um Payment/ocorrência exata depois da saída do proxy e antes da interrupção. Snapshot das oito tabelas só admite Payment do alvo e seu status PAID; conserva demais pedidos/canário, comprador/criação, catálogo, itens/ordem/dinheiro histórico42,50/20,00, contas/perfis/credenciais e demais valores. JSON mantém igualdade estrutural completa e inteiros exatos; IDs são números JSON inteiros/int64, sem string, fração, float/double, tolerância ou remoção de campos. A releitura do esperado conserva a correção IntNode/LongNode recebida sem alterar mapper.

Depois de cada experimento e seu controle, novo POST HTTP real com o mesmo corpo deverá produzir409 `Payment already registered`, sem retornar sucesso anterior, DML, flush ou commit novo; a transação rejeitada termina ROLLED_BACK e snapshot commitado permanece. GET real `/orders/{id}` do titular deverá devolver detalhe PAID fechado e histórico exato, sem dados novos de pagamento. HEAD do titular200, anônimo401/Bearer, conta sem perfil403 e ADMIN pagador de terceiro404 exigem corpo vazio; HEAD não é usado para ler estado no corpo. GET do pagador de terceiro continua404 seguro. Snapshots após reenvio e consultas devem permanecer idênticos. Esse recorte de recuperação é exigência da T025, **não comprovação da matriz completa de consultas/paginação/regressões US5**, ainda pendente de T028–T032/FINAL.

Não há compensação, remoção de Payment ou retry automático por perda da resposta; a limpeza posterior é só descarte dos IDs próprios da fixture, Payment antes de Order, após concluir os recursos e as asserções, seguida de baseline de limpeza. A prova planejada é de preservação de uma operação cujo commit já foi observado, não de pane física de banco/rede, commit de resultado desconhecido, durabilidade/crash recovery, CI/Linux ou outro SGBD. 500/ausência de resposta nunca são usados sozinhos para inferir ausência de pagamento.

### Revisão estática e preservação

Recibo ignorado `target/feature008-evidence/DELIVERY-T025/` guarda cópias dos dois documentos e três Java recebidos, hashes de360 arquivos recebidos e8.801 evidências anteriores, inclusive inventário416 Surefire. Revisão estática PowerShell/.NET/Git confere358 arquivos recebidos fora dos dois documentos, três Java byte a byte, prefixo histórico integral do quickstart, somente marcador T025 sobre tasks recebido, imports/referências/links/conteúdo dos novos e whitespace dos cinco Java não rastreados. Não altera produção, mapper, suporte ou testes/assertions T001–T024; recibos/relatórios anteriores, inclusive S2 reprovado, correção dos três oráculos, C2/C3 e50 controles adicionais do parser separados permanecem evidências históricas.

Comandos desta autoria: pré-requisitos exit0; leitura/inspeção PowerShell/.NET/Git; `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/DELIVERY-T025/review.ps1`, `git diff --check` e `git -c core.autocrlf=false diff --no-index --check -- /dev/null <cada um dos cinco Java não rastreados>`, com resultados efetivos registrados abaixo após execução. Exit1 do diff de arquivo novo sem diagnóstico de whitespace não representa falha de teste. Nenhum Wrapper, teste ou validador executável de aplicação foi antecipado.

Critérios de **autoria T025** atendidos após revisão; somente T025 passa a[X]: **T001–T025[X], T026–T036 abertas**. T023/T024/T025 permanecem sem prova de compilação/descoberta/wiring/instrumentação/transação/entrega/limpeza em execução. Nenhuma aprovação C4/S4/V07, GREEN funcional ou entrega da Feature008 é atribuída a esta escrita. Sem stage, commit, push, merge, mudança de branch/main ou trabalho T026+.

Resultado efetivo da revisão: `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/DELIVERY-T025/review.ps1` exit0. static-review.json confirmou358 arquivos recebidos fora dos documentos idênticos por SHA256, inclusive os três Java recebidos;8.801 evidências anteriores preservadas e inventário exato416 Surefire inalterado, sem relatórios novos. Prefixo recebido do quickstart349.467 bytes intacto; tasks difere do recebido somente por T025. Conferidos140 links locais,45 referências de imports dos cinco Java e dois métodos/matrizes de autoria (4 previstas,0 executadas), sem casos adicionais no suporte ou @Nested. `git diff --check` exit0; os cinco `diff --no-index --check` exit1 sem diagnóstico de whitespace. Revisão adicional dos diffs documentais contra as cópias recebidas mostrou somente esta seção e marcador T025; exit1 dessas comparações indica diferença existente. Conteúdo integral dos arquivos novos, delegação, exceções, recursos e limites revisados estaticamente. Avisos LF/CRLF do Git não alteraram os bytes recebidos; nenhuma dessas verificações estabelece compilabilidade ou funcionamento.

Git final: quickstart.md/tasks.md modificados; cinco Java não rastreados: `src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackApiHttpIntegrationTest.java` (três recebidos intactos), `src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseDeliveryIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseTransportHttpIntegrationTest.java` (novos desta autoria). Nada staged; raiz/branch/HEAD/seleção/main preservados. Alterações exclusivas T025: dois Java novos, seção do quickstart e seu checkbox; recibos locais ignorados em target. T026–T036 continuam abertas; validação executável T023–T025 permanece pendente. Sem stage/commit/push ou trabalho T026+; nenhum impedimento funcional/de preparação foi diagnosticado por execução, pois esta tarefa foi somente autoria.

## T026 — revisão da fronteira transacional, diagnóstico focal (2026-10-23)

Aplicada speckit-implement **exclusivamente à [T026](tasks.md)**. Estado inicial confirmado: raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `6111c6ba04b9f642bb2963f81c2b7bf333e4eb40`, seleção local008, somente quickstart.md/tasks.md modificados e índice vazio. Os cinco Java recebidos, cujos caminhos absolutos foram conferidos, são:

- `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java`;
- `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackIntegrationTest.java`;
- `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/PaymentRollbackApiHttpIntegrationTest.java`;
- `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseDeliveryIntegrationTest.java`;
- `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/PaymentResponseTransportHttpIntegrationTest.java`.

Lidas T026 integral, dependências T024–T025/T023, disciplina e seleções das tasks, procedimentos de rollback/commit/transporte acima, spec US4/FR-013–015, plano/R6/R8/modelo, contratos e constituição/AGENTS/harness. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks` retornou FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md:16/16, zero aberto, somente leitura; ignore Java existente adequado. `.specify/extensions.yml` ausente, sem hooks before_implement. Os registros T023–T025 permanecem autoria histórica sem execução naquelas tarefas; os resultados novos abaixo pertencem à T026.

### Preparação, comando e relatórios próprios

Recibo ignorado [T026-20261023](../../target/feature008-evidence/T026-20261023/) guarda received.json (SHA256 dos362 arquivos recebidos), cópias integrais dos dois documentos/cinco Java, initial.diff/status/main e cópia dos416 arquivos Surefire anteriores. Nenhum clean, configuração global, asserção ou fonte Java foi alterado. `.\mvnw.cmd -v` confirmou Maven3.9.16, Java Azul Zulu21.0.12.1 e Windows10 amd64, exit0; Boot3.3.4/Hibernate6.5.3.Final/H2 2.2.224 existentes.

A primeira chamada do auxiliar `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/T026-20261023/run.ps1 -Label DIAG -Selector 'PaymentRollbackIntegrationTest,PaymentRollbackApiHttpIntegrationTest,PaymentResponseDeliveryIntegrationTest,PaymentResponseTransportHttpIntegrationTest'` terminou exit1 **antes de iniciar Maven**: Copy-Item/DirectoryNotFoundException ao arquivar relatório histórico de nome longo em `T026-20261023/T026-DIAG-20261023T091843975/previous-surefire-reports/`. Diagnóstico de preparação do arquivo de evidência no Windows, sem compilação, teste ou RED funcional. A cópia inicial416/416 já estava preservada no recibo. Corrigido somente o auxiliar ignorado para usar diretório plano e sufixo mais curto; mantida a cópia parcial da tentativa. A segunda chamada do mesmo auxiliar executou exatamente:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRollbackIntegrationTest,PaymentRollbackApiHttpIntegrationTest,PaymentResponseDeliveryIntegrationTest,PaymentResponseTransportHttpIntegrationTest" "-Dsurefire.reportNameSuffix=T026-DIAG-091925077" test
~~~

Uma única invocação Maven de testes, sequencial, **diagnóstico focal prévio à decisão de ajuste da T026**, sem seleção S4 conjunta, núcleo S1/S3, BASE ou suíte completa. Início `2026-10-23T09:19:27.9378214-03:00`, término `2026-10-23T09:22:55.0100927-03:00`; Maven03:13min, **BUILD SUCCESS/exit0,32 testes, zero falhas/erros/ignorados**. Compilados148 fontes de teste; isso não significa executar148 classes. Avisos de SecurityManager/ByteBuddy/VM/H2Dialect e logs esperados de falhas de persistência injetadas não são falhas do build.

[T026-DIAG-091925077](../../target/feature008-evidence/T026-DIAG-091925077/) contém run.json/exit.json, diff/status da revisão, console.txt (UTF-16 do Tee-Object), previous-surefire-reports/ e os quatro XML/quatro TXT próprios em surefire-reports/. Sufixo exclusivo conserva os relatórios antigos. analysis.json lista todas as32 identidades e contagens; markers.txt conserva os32 marcos T024/T025 do console. O auxiliar de leitura `analyze.py` no recibo foi executado pelo Python externo já disponível, exit0, conciliando XML/TXT/console/exit e a matriz exata, sem repetir Maven.

| Classe efetiva | Contexto | Métodos por concreto | Invocações | Falhas/erros/ignorados |
| --- | --- | ---: | ---: | --- |
| PaymentRollbackIntegrationTest | HTTP real ROOT | 5 herdados | 14 | 0/0/0 |
| PaymentRollbackApiHttpIntegrationTest | HTTP real /api | 5 herdados | 14 | 0/0/0 |
| PaymentResponseDeliveryIntegrationTest | MockMvc/filtro ROOT; recuperação por HTTP real | 1 próprio | 2 | 0/0/0 |
| PaymentResponseTransportHttpIntegrationTest | HTTP/socket/proxy ROOT | 1 próprio | 2 | 0/0/0 |
| Total | 4 contextos/H2 isolados | 12 por classe/contexto | 32 | 0/0/0 |

Rollback herda cinco métodos de PaymentRollbackScenarios: `reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows`6, `realDatabaseConstraintRejectsPaymentInsertWithoutBeingClassifiedAsRepeat`1, `confirmationConstructorFailureAfterRealFlushRollsBackPaymentAndPaidTogether`1, `internalFailureIsNotAuthenticationNotFoundOrRepeatAndLeavesNoCommittedMutation`5 e `positiveControlConfirmsCommitBefore201AndPreservesOtherOrdersAndHistory`1, em **cada** concreto. As classes de entrega executaram `bufferedConfirmationIsDeliveredOrPreventedOnlyAfterIndependentCommitProof`2 e `proxyReceivesCommittedConfirmationThenForwardsOrClosesWithoutDeliveringIt`2 (FORWARD/WITHHOLD). São sete métodos distintos de fonte,12 por contexto,32 invocações. Suportes não acrescentam casos; configurações/records aninhados não são testes @Nested. Fontes e relatórios conferidos sem @Nested, @Disabled, assumptions, seleção vazia, omissão ou duplicata.

### Estágios realmente alcançados e leituras independentes

Os seis pontos foram atingidos/injetados **uma vez por ponto em ROOT e /api**, com sentinela exata e instrumentação saudável. Todos retornaram500 seguro, zero commit delegado/bem-sucedido/afterCommit, completion ROLLED_BACK (valor1 nesta execução) e baseline integral restaurada após saída do proxy. Não se inferiu rollback apenas pelo500.

| Ponto observado em ambos os contextos | DML concluído / flush retornado / DTO retornado | Tentativa JDBC commit | Alcance efetivo |
| --- | --- | ---: | --- |
| SQL_BEFORE_FIRST_DML | 0 / 0 / 0 | 0 | SQLException08006 sintética antes do execute; não falha física do H2. |
| AFTER_FIRST_DML | 1 / 0 / 0 | 0 | Driver retornou uma linha afetada; leitura local conferiu o efeito do primeiro SQL, admitindo insert Payment ou update Order sem presumir a ordem. |
| AFTER_FLUSH | 2 / 1 / 0 | 0 | Ambos os DML concluídos e visíveis na própria transação; ainda sem commit. |
| AFTER_BODY_DTO | 2 / 1 / 1 | 0 | DTO real já construído; não é injeção dentro do construtor. |
| BEFORE_COMMIT | 2 / 1 / 1 | 0 | Callback Spring anterior ao JDBC; não falha do driver. |
| JDBC_COMMIT_BEFORE_DELEGATE | 2 / 1 / 1 | 1 | Zero delegações ao driver; injeção conhecida pré-efetivação, não resultado físico indeterminado. |

O teste de constraint temporária chegou ao insert real no H2 e observou SQLState23513/nome da constraint/FailedDml, com500 e rollback/baseline; não foi classificado como repetição409. A constraint própria foi removida em finally. A falha no construtor foi separada: flush real retornado, Payment/PAID lidos localmente e Payment gerenciado; corrupção de teste de occurredAt em+1ns chegou ao construtor produtivo PaymentConfirmationDTO, que lançou `Invalid payment confirmation` com frame `<init>`, zero DTO retornado e rollback conjunto. Os cinco pontos locais de perfis/lock/lookup Payment/persist/flush pré-delegação atingiram suas sentinelas uma vez e permaneceram500, sem se converter em401/404/409; são falhas de API simuladas, não SQL do delegate.

Os dois controles positivos deram201 somente após sequência flush→DTO→beforeCommit→tentativa/delegação/sucesso de Connection.commit→afterCommit→completion COMMITTED→retorno/saída do proxy. Proxy físico original com transação nova READ_COMMITTED, conexão vinculada/autoCommit desligado e nenhuma transação externa na entrada/saída; nenhuma continuação ou consulta produtiva depois de transação falha. JDBC independente, mesma URL H2 e sessão física diferente, confirmou WAITING_PAYMENT/nenhum Payment nas rejeições ou PAID/um Payment/ocorrência exata nos controles. Reaquisição da raiz FOR UPDATE verificou lock liberado após conclusão.

Snapshots das oito tabelas exigiram igualdade integral após rejeição; no sucesso, exatamente Payment do alvo e seu status PAID, conservando comprador/criação/itens/ordem/valores históricos42,50/20,00, canário, produtos/categorias/associações e contas/perfis/credenciais. Conferido que OwnOrderQueryDatabaseProbe.assertUnchanged acumula **todas** as tabelas alteradas antes de lançar: exigir mensagem exata `Observed database state changed: tb_order`, junto da comparação de todos os pedidos esperados e pagamentos, não deixa as demais tabelas sem comparação. Limpeza seletiva Payment antes de Order e baseline final passaram em todos os casos.

### Entrega posterior ao commit: medições separadas

| Experimento ROOT | Modo | Corpo gerado/recebido a montante | Bytes entregues ao destino | Resultado posterior |
| --- | --- | ---: | ---: | --- |
| MockMvc/ContentCachingResponseWrapper | FORWARD | 69 | 69 de corpo MockMvc | Commit1; reenvio409; GET titular PAID. |
| MockMvc/ContentCachingResponseWrapper | WITHHOLD | 69 | 0 de corpo MockMvc | IOException sentinela após prova independente; Payment/PAID preservados. |
| Socket HTTP/1.1/proxy local | FORWARD | 389 wire / 69 JSON | 389 wire no cliente | Commit1; confirmação201 íntegra; reenvio409; GET titular PAID. |
| Socket HTTP/1.1/proxy local | WITHHOLD | 389 wire / 69 JSON | 0 wire no cliente/EOF | Commit1 observado antes do fechamento; Payment/PAID preservados; reenvio409; GET titular PAID. |

Request body do socket41bytes. Marcos nanoTime completos no console/markers.txt; tempos dos caminhos de entrega: buffer FORWARD1.236.833.300ns, WITHHOLD67.920.900ns; socket FORWARD145.976.000ns, WITHHOLD58.898.600ns. São medidas do experimento local, sem SLA. Em todos, ordem aplicação/resposta a montante→prova de commit/JDBC independente→entrega/bloqueio foi assertada. O buffer impediu copyBodyToResponse; o socket recebeu confirmação completa a montante antes de fechar a jusante sem enviar status/headers/corpo, com Future concluído e worker encerrado antes da limpeza. Não se limitou a descartar um DTO ou aceitar EOF por erro de preparação.

Nos quatro casos de entrega, reenvio real409 sem DML/novo commit, ocorrência original preservada e rollback da tentativa repetida; GET real do titular200 com detalhe fechado PAID e histórico exato. HEAD titular200/anônimo401-Bearer/sem perfil403/ADMIN pagador de terceiro404, todos sem corpo; GET desse terceiro404 seguro. Snapshots pós-reenvio/leituras iguais ao estado commitado. HEAD não foi usado para ler estado de negócio no corpo. Esse recorte confirma recuperação US4, sem aprovar paginação/lista/matriz completa US5.

### Decisão da T026, limites e revisão

Revisados [PaymentRegistrationService](../../src/main/java/com/devsuperior/dscommerce/services/PaymentRegistrationService.java), [AdminPaymentController](../../src/main/java/com/devsuperior/dscommerce/controllers/AdminPaymentController.java) e [PaymentRegistrationExceptionHandler](../../src/main/java/com/devsuperior/dscommerce/controllers/handlers/PaymentRegistrationExceptionHandler.java): corpo REQUIRED/READ_COMMITTED/rollbackFor Exception reúne persistência/PAID/flush/DTO; controller sem transação constrói201 depois do proxy; advice centraliza falhas internas em500 seguro, sem catch/continuação na transação, consulta de recuperação, compensação ou retry automático. Guardas de conta/elegibilidade/tempo/repetição preservadas. **Nenhuma falha funcional ou de compilação/instrumentação dos testes foi diagnosticada; nenhuma correção de produção/testes foi necessária.** A única correção foi o caminho do auxiliar local de arquivamento, antes do Maven. GREEN existente foi observado sem RED artificial ou ajuste cosmético; sem mudança de código, não houve segunda execução redundante.

Flush/DTO/leitura na conexão da transação não foram tratados como commit. A prova cobre H2/configuração Windows observados, falhas conhecidas anteriores à efetivação e perda deliberada de entrega após commit confirmado. Não reproduz falha física de banco/rede, commit delegado com resultado desconhecido, exceção física pós-commit, crash recovery, durabilidade de produção, outro SGBD, CI/Linux ou toda mutação transitória. 500/ausência de resposta não demonstram ausência de pagamento e não autorizam desfazimento, compensação ou replay. O resultado indeterminado continua fora da garantia de rollback.

Critérios da **T026 atendidos por revisão e diagnóstico focal32/32**. Somente T026 marcada[X]; T027–T036 continuam abertas. Não executado/aprovado o checkpoint conjunto S4/C4 da T027, nem T028+, BASE, suíte completa ou publicação/vigência de contratos008. As evidências históricas e asserções recebidas permanecem preservadas; recibos locais ignorados não são artefatos portáveis de CI.

Revisão final: comparação do prefixo recebido do quickstart, alteração exclusiva do marcador T026 em tasks, hashes dos360 arquivos recebidos fora dos dois documentos (inclusive cinco Java),416 relatórios anteriores, referências/links locais, diff e whitespace executados no fechamento e registrados em final-review.json no recibo. `git diff --check` e os cinco `git -c core.autocrlf=false diff --no-index --check -- /dev/null <Java recebido>` são as verificações de whitespace; exit1 sem diagnóstico no segundo modo significa arquivo novo diferente. Estado final esperado e conferido no fechamento: somente quickstart.md/tasks.md modificados e os mesmos cinco Java não rastreados, nada staged; mesma raiz/branch/HEAD/seleção/main. Sem stage, commit, push, merge ou alteração de main. `.specify/extensions.yml` ausente também no fechamento, sem hooks after_implement.

Resultado efetivo: execução de `target/feature008-evidence/T026-20261023/review.py` pelo Python externo com `-B -I -X utf8`, exit0/PASS. Confirmados360/360 arquivos fora dos documentos e416/416 relatórios anteriores idênticos por SHA256, prefixo recebido364.442bytes intacto, somente T026 sobre tasks recebido,146 links locais existentes e oito relatórios novos. `git diff --check` exit0; cinco no-index exit1 sem qualquer diagnóstico de whitespace. Diff documental contra HEAD e contra cópias recebidas e conteúdo integral dos cinco Java revisados; alterações anteriores T023–T025 preservadas. Sem processo Maven/Surefire ativo na conferência posterior. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`; Git final com os mesmos sete caminhos recebidos e índice vazio. Nenhum impedimento pendente para a T026; o erro inicial de arquivamento foi resolvido antes dos testes.

## T027 — checkpoint conjunto S4/C4 de US4 (2026-10-23)

Executada speckit-implement **exclusivamente para [T027](tasks.md)**, dependente da revisão T026 concluída sem correção de código. Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `6111c6ba04b9f642bb2963f81c2b7bf333e4eb40`, seleção local008 e índice vazio. Somente quickstart.md/tasks.md estavam modificados. Conferidos os caminhos absolutos dos mesmos cinco Java não rastreados: `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/config/PaymentFailureInjectionTestConfig.java` e, sob `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/`, `PaymentRollbackIntegrationTest.java`, `PaymentRollbackApiHttpIntegrationTest.java`, `PaymentResponseDeliveryIntegrationTest.java` e `PaymentResponseTransportHttpIntegrationTest.java`.

Lidas integralmente T027, dependência T026 e suporte/provas T023–T025, disciplina/seleção S4, procedimentos C4 e limites acima, em conjunto com os artefatos aprovados de plano/modelo/pesquisa/contratos, spec US4 e constituição/AGENTS/harness. `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: FEATURE_DIR absoluto008 e documentos esperados, sem erro observado. Checklist requirements.md16/16, zero aberto, mantido somente em leitura. Ignore Java existente adequado; extensions.yml ausente nas conferências anterior/posterior, sem hooks before/after_implement. O diagnóstico focal32/32 da T026 e seus limites permanecem históricos intactos; **o C4 abaixo decorre da execução conjunta nova**, sem promover aquele resultado focal a checkpoint.

### Execução exata e preservação

[Recibo T027-20261023](../../target/feature008-evidence/T027-20261023/) contém SHA256 dos362 arquivos recebidos, cópias dos dois documentos/cinco Java, diff/status/main/seleção iniciais e cópia dos424 arquivos Surefire anteriores. t026-evidence.json inventaria977 arquivos dos diretórios T026-20261023 e T026-DIAG-091925077 para comparação posterior. Auxiliar prepare.py pelo Python externo existente (`-B -I -X utf8`): exit0. Não foram alterados os arquivos/evidências antigos; somente diretórios novos ignorados sob target foram usados para esta execução. Não havia Maven/Surefire ativo antes do início.

`.\mvnw.cmd -v`: exit0, Maven3.9.16, Java Azul Zulu21.0.12.1, Windows10 amd64; Boot3.3.4/Hibernate6.5.3.Final/H2 2.2.224 existentes. O auxiliar `powershell -NoProfile -ExecutionPolicy Bypass -File target/feature008-evidence/T027-20261023/run.ps1` executou **uma única invocação Maven de testes**, exatamente com o seletor S4 aprovado:

~~~powershell
.\mvnw.cmd "-Dtest=PaymentRollback*Test,PaymentResponse*Test,PaymentRegistrationHttpIntegrationTest,PaymentRegistrationApiHttpIntegrationTest,PaymentConcurrencyIntegrationTest" "-Dsurefire.reportNameSuffix=S4-T027-093159353" test
~~~

Início `2026-10-23T09:31:59.4320347-03:00`, término `2026-10-23T09:34:05.3845863-03:00`; Maven02:00min. **BUILD SUCCESS, exit0;77 testes, zero falhas/erros/ignorados**. Classes de produção/teste estavam atualizadas; nenhuma recompilação necessária. Sem clean, builds simultâneos, seleção adicional, BASE ou suíte completa. Não houve falha funcional/de compilação/preparação/instrumentação a corrigir nesta T027, alteração de produção/testes/asserções ou repetição do Wrapper.

[S4-T027-093159353](../../target/feature008-evidence/S4-T027-093159353/) guarda run.json/exit.json, diff/status da revisão, console.txt UTF-16 e sete XML/sete TXT próprios em surefire-reports/. Sufixo exclusivo preserva os anteriores;14 relatórios novos foram copiados após o término. `analyze.py` no recibo T027, executado pelo Python externo com `-B -I -X utf8`, exit0, conciliou console, XML, TXT, exit code, nomes/métodos/índices e os marcos reais. analysis.json conserva77 identidades, matriz completa de38 grupos método/contexto e os contextos físicos; markers.txt extrai os marcos, sem substituir o console integral.

| Classe efetivamente executada | Contexto/porta | Métodos por classe | Invocações | Falhas/erros/ignorados |
| --- | --- | ---: | ---: | --- |
| PaymentConcurrencyIntegrationTest | HTTP ROOT54501 | 4 próprios | 13 | 0/0/0 |
| PaymentRegistrationHttpIntegrationTest | HTTP ROOT54501, contexto reutilizado | 11 herdados | 16 | 0/0/0 |
| PaymentRegistrationApiHttpIntegrationTest | HTTP /api54506 | 11 herdados | 16 | 0/0/0 |
| PaymentResponseDeliveryIntegrationTest | MockMvc/filtro ROOT, recuperação HTTP54517 | 1 próprio | 2 | 0/0/0 |
| PaymentResponseTransportHttpIntegrationTest | Socket/proxy HTTP ROOT54520 | 1 próprio | 2 | 0/0/0 |
| PaymentRollbackApiHttpIntegrationTest | HTTP /api54528 | 5 herdados | 14 | 0/0/0 |
| PaymentRollbackIntegrationTest | HTTP ROOT54544 | 5 herdados | 14 | 0/0/0 |
| Total | 7 concretos,6 contextos Spring/H2 | 38 | 77 | 0/0/0 |

Os seis bancos H2 têm UUIDs distintos registrados no console/analysis.json; registro ROOT e concorrência compartilham o contexto/cache compatível e URL iniciada por `admin-product-test-27373aeb-`, sem outro create-drop concorrente. As outras cinco URLs são exclusivas por contexto. Não houve classe de transporte `/api`: as duas provas de entrega foram ROOT, enquanto registro e rollback executaram ROOT e `/api`.

Conferência de descoberta: PaymentRegistrationScenarioSupport fornece11 métodos em cada concreto, seis US1 (8 invocações) mais cinco guardas básicas/repetição (8). PaymentRollbackScenarios fornece cinco métodos em cada concreto, matriz6+1+5+1+1=14. Concorrência: `sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal`4, `existingPaymentPrecedesWaitingStateWithoutRepair`4, `overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment`4 e `validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions`1. Cada classe de entrega tem um método com FORWARD/WITHHOLD,2 invocações. São22 métodos distintos de fonte,38 grupos por classe/contexto,77 casos efetivos; índices parametrizados contíguos, zero duplicatas/omissões/classes extras. Os suportes restantes não acrescentam testes. Configurações/records aninhados não são @Nested; conferidos fontes e relatórios sem @Nested, @Disabled, assumptions, seleção vazia, fábricas ou repetições adicionais. A aprovação não soma resultados históricos de outras execuções.

### US4.1/2/4 — antes do commit

Os seis Points da instrumentação original foram realmente alcançados/injetados uma vez **em cada contexto ROOT e /api**, com sentinela correta e observador saudável. Todos os12 casos deram500 seguro, baseline restaurada por leitura independente após saída do proxy e completion ROLLED_BACK (valor1), sem commits delegados/bem-sucedidos ou afterCommit.

| Ponto interceptado | DML concluído / flush retornado / DTO retornado | Tentativas de commit JDBC | Limite |
| --- | --- | ---: | --- |
| SQL_BEFORE_FIRST_DML | 0 / 0 / 0 | 0 | SQLException08006 sintética antes de delegar execute; não rejeição física do H2. |
| AFTER_FIRST_DML | 1 / 0 / 0 | 0 | Primeiro execute real retornou uma linha afetada; leitura na própria conexão conferiu seu efeito, sem presumir ordem insert/update. |
| AFTER_FLUSH | 2 / 1 / 0 | 0 | Payment e PAID visíveis localmente após flush; ainda sem commit. |
| AFTER_BODY_DTO | 2 / 1 / 1 | 0 | DTO já construído dentro da transação; não falha dentro do construtor. |
| BEFORE_COMMIT | 2 / 1 / 1 | 0 | Callback Spring anterior ao commit JDBC; não falha do driver. |
| JDBC_COMMIT_BEFORE_DELEGATE | 2 / 1 / 1 | 1 | Zero delegações; SQLException pré-driver conhecida, sem resultado físico indeterminado. |

Mais dois casos chegaram ao insert real rejeitado por CHECK temporário do H2: SQLState23513, constraint própria e FailedDml conferidos;500 e baseline, sem classificar a falha como repetição409. Constraint removida em finally. Dois casos separados comprovaram a falha real no construtor produtivo PaymentConfirmationDTO após flush: Payment/PAID e entidade gerenciada observados localmente, corrupção de teste em+1ns, IllegalArgumentException `Invalid payment confirmation` com frame `<init>`, nenhum DTO retornado e rollback integral. Dez injeções locais (perfis atuais, lock, lookup Payment, persist e flush antes de delegar) atingiram uma vez a sentinela esperada, sem erro do observador; permaneceram500, não401/404/409. Essas injeções locais não se apresentam como SQL físico falho.

Os dois controles sem injeção confirmaram201 depois de flush/DTO, beforeCommit, uma tentativa/delegação/sucesso de Connection.commit, afterCommit, completion COMMITTED e retorno/saída do proxy. A instrumentação exige transação nova READ_COMMITTED, conexão vinculada/autoCommit desligado, proxy original dono e nenhuma transação externa na entrada/saída. Falhas anteriores não entregaram sucesso nem continuaram consultando em transação inválida. Flush, DTO e leitura local nunca foram usados como substitutos de commit.

JDBC independente após conclusão, com mesma URL e sessão física diferente da transação, confirmou WAITING_PAYMENT/zero Payment nas falhas ou PAID/um Payment/ocorrência exata nos controles. Reaquisição da raiz FOR UPDATE confirmou liberação do lock. Snapshots das oito tabelas conservaram integralmente baseline nas rejeições; sucesso admitiu somente Payment do alvo e mudança de seu status, preservando comprador/criação/itens/ordem/valores42,50/20,00, canário, catálogo/categorias/associações e contas/perfis/credenciais. O probe acumula todas as tabelas alteradas antes de lançar: exigir diagnóstico exato apenas tb_order, somado à comparação integral dos pedidos/pagamentos esperados, conserva a verificação das demais tabelas. Limpeza seletiva e baseline de limpeza passaram.

### US4.3 — falha de entrega após commit, com controles

| Experimento ROOT | Modo | Montante gerado/recebido | Entrega ao destino | Evidência posterior |
| --- | --- | ---: | ---: | --- |
| MockMvc/filtro com buffer | FORWARD | 69 bytes JSON | 69 bytes de corpo MockMvc | Commit1; novo POST409; titular GET PAID. |
| MockMvc/filtro com buffer | WITHHOLD | 69 bytes JSON | 0 bytes de corpo MockMvc | IOException sentinela após prova independente; quitação preservada. |
| HTTP/1.1/socket/proxy local | FORWARD | 389 bytes wire / 69 JSON | 389 bytes wire no cliente | Confirmação201 íntegra; commit1; novo POST409; titular GET PAID. |
| HTTP/1.1/socket/proxy local | WITHHOLD | 389 bytes wire / 69 JSON | 0 bytes wire no cliente, EOF | Confirmação recebida a montante e commit conferido antes de fechar a jusante. |

Request body do socket41bytes. Marcos completos nanoTime estão no console/markers.txt; duração medida buffer FORWARD99.890.400ns/WITHHOLD38.095.700ns, socket FORWARD111.537.500ns/WITHHOLD38.095.600ns. Medidas locais sem SLA. Em ambos os WITHHOLD, o bloqueio ocorreu **depois** do retorno transacional, commit JDBC delegado/bem-sucedido/completion COMMITTED e leitura em conexão física independente. O filtro não copiou o buffer; o proxy recebeu o frame completo a montante e enviou zero bytes de status/headers/corpo ao cliente. Oráculos/sentinela, limites finitos, Future concluído e encerramento de recursos/worker foram exigidos; não se aceitou EOF causado por preparação falha nem mero descarte de response/DTO.

Nos quatro casos, Payment/PAID e ocorrência original permaneceram após a entrega/interrupção; novo POST HTTP real devolveu409 `Payment already registered`, sem reutilizar201, sem DML/flush/novo commit e com rollback da tentativa repetida. GET do titular200 mostrou detalhe fechado PAID e histórico exato; HEAD titular200/anônimo401-Bearer/sem perfil403/ADMIN pagador de terceiro404 exigiu corpo vazio, e GET desse terceiro404 seguro. HEAD não foi usado para ler status de negócio no corpo. Snapshots após reenvio e leituras permaneceram iguais ao estado commitado. Essa recuperação focal de US4 não aprova a matriz completa de consultas US5.

### Núcleo S1/S3 e alcance de C4

Registro ROOT e `/api` confirmou seis cenários US1, incluindo ADMIN isolado/ambos, próprios/alheios, histórico42,50/20,00 mesmo catálogo12,00, offset equivalente/zeros adicionais, limites inclusivos e referência original conservada após avanço do Clock. Confirmação fechada201 e Payment/PAID por snapshot independente após proxy, guardas401/403/400 e repetição409 sem alteração passaram;18 marcos T013_SUCCESS e14 T013_REJECTION conferidos, sem confundi-los com contagem JUnit ou instrumentação física de commit da T023.

Concorrência ROOT confirmou quatro disputas com ADMIN A/B primeiro e ocorrências iguais/diferentes: sessões físicas3/4, espera BLOCKED/BLOCKER_ID observada antes da mutação e após flush/beforeCommit (oito T019_WAIT), um201/um409, um afterCommit, rollback da perdedora e ocorrência vencedora preservada. O controle em dois pedidos distintos concluiu201/201 com transações sobrepostas e vínculos/históricos20,00/22,50 independentes. Repetições sequenciais e quatro variantes do setup deliberadamente inconsistente WAITING_PAYMENT com Payment anterior deram409 sem sobrescrever ou reparar: essa incoerência pertence ao setup commitado, não é alteração parcial produzida pela operação. Não se atribui a este seletor a prova mais forte de T020 de um pedido concluir enquanto outro continua bloqueado, ausente de S4. Os contadores T019 são callbacks Spring, complementados por proxy/snapshots; não são instrumentação de Connection.commit.

**C4 aprovado nesta execução77/77**, nos contextos e estágios observados: quatro cenários US4, sem sucesso parcial, separando falha pré-efetivação de entrega posterior a commit comprovado, com núcleo S1/S3 da seleção. Nenhuma correção necessária; somente T027 passa a[X], T028–T036 continuam abertas. Não executados T028+, BASE, suíte completa, CI/Linux ou publicação dos contratos008.

Limites preservados: H2/Windows/configuração testada e simulações de aplicação/proxy local não reproduzem pane física de rede/banco, commit delegado de resultado desconhecido, exceção física pós-commit, crash recovery, durabilidade de produção ou outro SGBD. A falha JDBC pré-delegação é conhecida anterior à efetivação; resultado indeterminado continua fora da garantia de rollback. 500/ausência de resposta não comprovam ausência do pagamento e não autorizam compensação, apagamento ou retry automático. Snapshot igual não exclui toda mutação transitória; StatementInspector não observa JDBC direto. Leituras completas US5/FINAL e entrega da Feature008 continuam pendentes.

Revisão final desta tarefa compara hashes dos360 arquivos recebidos fora dos documentos (inclusive cinco Java),424 relatórios Surefire anteriores e977 arquivos das evidências T026; exige quickstart recebido como prefixo integral e tasks diferente somente pelo marcador T027. Conferência de conteúdo integral dos cinco Java, diff documental contra HEAD/recebidos, referências/links e whitespace: `git diff --check` e cinco `git -c core.autocrlf=false diff --no-index --check -- /dev/null <Java recebido>`. Exit1 sem diagnóstico no segundo modo indica arquivo novo diferente. Os resultados efetivos e estado final são registrados após a execução do auxiliar de revisão abaixo. Sem stage, commit, push, merge, mudança de branch ou alteração de main.

Resultado efetivo: `target/feature008-evidence/T027-20261023/review.py`, pelo Python externo com `-B -I -X utf8`, exit0/PASS; final-review.json confirma360/360 arquivos fora dos documentos,424/424 relatórios anteriores e977/977 evidências T026 idênticos por SHA256, prefixo recebido379.986bytes intacto, somente checkbox T027 alterada sobre tasks recebido e149 links locais existentes. `git diff --check` exit0; cinco no-index exit1 sem diagnóstico de whitespace. Os avisos LF/CRLF do Git não mudaram os bytes recebidos; avisos de VM/ByteBuddy/H2Dialect e logs das falhas esperadas no console não constituem falha de teste. Não havia Maven/Surefire ativo na conferência posterior.

Git final: somente ` M specs/008-registrar-pagamento/quickstart.md`, ` M specs/008-registrar-pagamento/tasks.md` e os mesmos cinco Java `??` nos caminhos recebidos, conteúdo preservado; nada staged. Raiz/branch/HEAD/seleção008 mantidos; main continua `5cd16709ea531ccae27230db19753e6c5533d12d`. Alterações desta tarefa: esta seção e marcador T027; recibos novos ignorados em target. T028–T036 abertas, sem impedimento pendente para T027; US5/FINAL e a entrega da feature continuam futuras. Sem stage, commit, push ou trabalho T028+.

## T028 — autoria das variantes PAID em DTO/repository/service (2026-10-23)

Aplicada speckit-implement exclusivamente à [T028](tasks.md), dependente da T027. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `cf19e90747ad57565081fcc8b03b644dfdbe6fb1`, Git limpo/índice vazio e seleção local008. Lidos tarefa/dependência, limites T029–T032, spec US5/FR-016–021, plano/R7/R8/modelo, contratos e procedimentos pertinentes, sob constituição/AGENTS/harness. C4/T02777 aprovados e T02632 focais permanecem evidências históricas do checkpoint anterior, sem reexecução ou aprovação atribuída aos novos casos.

Pré-requisitos `.specify/scripts/powershell/check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: FEATURE_DIR absoluto008 e AVAILABLE_DOCS esperados, sem erro observado. Checklist requirements.md16/16, zero aberto, somente leitura. Ignore Java existente adequado; extensions.yml ausente antes/depois, sem hooks before/after_implement. A tarefa determina **estender testes**; a execução conjunta de DTO/repository/service e HTTP está prevista em T032, após T029–T031. **Nesta T028 não houve Maven/Wrapper, javac, compilação, descoberta JUnit, criação de contexto Spring/H2, teste, login, HTTP ou validação OpenAPI executável.** Não há novo console/Surefire, RED/GREEN, failpoint ou estado de banco medido. Não se fabrica RED para a projeção de status já existente; eventual GREEN será registrado quando os testes forem executados na etapa própria.

### Contrato alvo e preservação histórica

As extensões usam [own-orders-openapi.yaml008](contracts/own-orders-openapi.yaml), versão0.2.0, alvo completo que admite WAITING_PAYMENT e PAID em resumo/detalhe. Conforme [precedência](contracts/README.md), esse snapshot substituirá integralmente007 após a entrega008; o [contrato007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml) permanece vigente até lá e histórico intacto. Nenhum contrato, spec007, README público ou metadado de vigência foi alterado. Não são contratos alternativos simultâneos. Não se publicou entrega008 nesta autoria.

Modificados somente [OwnOrderQueryDtoContractTest.java](../../src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java), [OwnOrderQueryRepositoryTest.java](../../src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java) e [OwnOrderQueryServiceTest.java](../../src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java), além desta seção e marcador T028. Alterações Java são aditivas: imports, novos métodos e auxiliares locais. Casos/assertions WAITING_PAYMENT, providers, métodos de leitura, configuração, perfis, URLs isoladas, fixture/probes007/008, produção e suporte T001–T027 foram preservados.

| Componente | Autoria nova | Previsão adicional, não executada |
| --- | --- | ---: |
| DTO/JSON | post008ContractExtendsOnlyStoredStatusesAndKeepsClosedQueryShapes; paidSummaryAndDetailKeepPost008FieldsAndExactMoneyWithoutPaymentData; mixedPaidAndWaitingDtosKeepSuppliedPageMetadataAndRecordedItemSequence | 1+9+1=11 |
| Repository/JPA | paidListStillPagesTwoTwoOneByCreationAndIdRatherThanPaymentOccurrence; paidContentCountBatchAndDetailRemainRestrictedToEachOwnerIncludingAdmin; paidDetailKeepsCurrentNamesAndExactHistoricalAmounts | 1+4+3=8 |
| Serviço/JPA | paidRealListAndDetailKeepCreationOrderInsteadOfPaymentOccurrence; paidRealContentAndCountStayOwnAndForeignDetailMatchesAbsentEvenForAdmin; paidRealDetailKeepsCurrentNamesCreationAndRecordedMoney | 1+4+3=8 |
| Total | 9 métodos de fonte; nenhuma classe concreta nova | 27 previstas;0 executadas |

As matrizes novas usam nove valores monetários no DTO, quatro identidades client/other/admin/both e três totais42,50/0,01/9007199254740993,01 nos testes de banco. Auxiliares PaidOrders aninhados não têm @Test/@Nested; não foram acrescentados casos herdados, assumptions, @Disabled ou seleção que esconda os originais. Contagens são inspeção estática, não descoberta Surefire; nomes/índices/contextos efetivos continuam por conferir na execução.

### DTO: formato fechado pós008, sem inferir persistência

O teste documental escrito lê o arquivo008 e a origem007 por caminhos locais explícitos, confere enum ampliado do alvo, enum histórico WAITING_PAYMENT, versão/origem e chaves required/fechamento de summary/detail/item/page. Usa leitura textual dos blocos nomeados, sem dependência nova ou parser YAML de aplicação; não substitui validação estrutural completa de OpenAPI. Os contratos aprovados não mudaram.

Variantes PAID deverão serializar exatamente quatro campos no resumo, cinco no detalhe e cinco por item, IDs inteiros int64 e dinheiro textual exato, inclusive extremos decimal19,2 e acima de2^53. Round-trip de resumo/detalhe deve preservar todos os valores. Página mista WAITING_PAYMENT/PAID conserva metadados fornecidos e sequência B/A com42,50. Nenhuma chave de pagamento, ocorrência, comprador ou montante adicional é admitida. O contexto @JsonTest continua sem DataSource/MVC/Security: esses são DTOs fornecidos ao mapper, **não fixtures de persistência, cálculo de paginação ou prova de autorização**.

### Repository/service: setup coerente e consultas sem mutação

Cada arquivo de banco recebeu um auxiliar PaidOrders local, sem alterar o significado da fixture007. Ele aceita somente pedidos do cenário recebido e ocorrência representável em ms, não anterior à criação; insere tb_payment e muda apenas tb_order.status para PAID **na mesma transação de setup REQUIRES_NEW/READ_COMMITTED**, antes da consulta. Só após retorno transacional registra o ID e verifica por probe008 independente Payment/ocorrência e estado PAID, além da preservação das outras tabelas. O manifesto esperado é ajustado depois dessa preparação/verificação; não há PAID somente em manifesto nem schema artificial.

Esse setup JDBC declarado pertence aos testes isolados de consulta; não chama o POST, não comprova autorização do ADMIN pagador nem repete a prova do proxy de pagamento/commit da US4. A jornada por operação real e HTTP ROOT/Api continua T029. Asserção/erro de preparação futura deve ser diagnosticado como tal, sem contar como RED funcional de consulta. Nada desses marcos foi alcançado nesta autoria.

As leituras novas reutilizam as asserções originais de SQL, transação somente leitura/proxy quando aplicáveis, carregamento completo, nomes atuais e valores históricos; um snapshot008 adicional cerca cada chamada para incluir tb_payment junto das sete tabelas anteriores. Exige ausência de alteração no retorno/falha e que o SQL Hibernate observado de consulta não use tb_payment. O probe lê por conexão física independente após setup/chamadas concluídos; não se confunde com SQL observado por StatementInspector. No serviço, serialização de DTOs após retorno exige chaves fechadas e nenhuma consulta tardia.

Pedidos próprios continuam cinco em2/2/1, com empate por criação e desempate ID. O mais antigo recebe ocorrência posterior à do mais novo, mas deve continuar por último; createdAt, histórico e ordem dos itens não mudam. Páginas além do fim e conta sem pedidos mantêm posição/totais vazios. Os casos de titularidade exercitam CLIENT, outro comprador, ADMIN isolado e ADMIN+CLIENT; page/count/batch/detail conservam o mesmo dono. A variante de serviço prepara todos os pedidos pagos e exige conteúdo/count somente próprios, detalhe alheio/ausente com OwnOrderNotFoundException `Order not found`, inclusive ADMIN. Não se apresenta esse teste direto como verificação de filtros HTTP/HEAD ou do papel de quem pagou.

Detalhes PAID com42,50, um centavo e9007199254740993,01 devem manter criação, posições/quantidades/preços/subtotais históricos e nomes atuais depois de mudanças de catálogo para preços0,00/37,00. Status vem do estado persistido; não se acrescentam campos de Payment aos DTOs nem se recalcula pelo catálogo. Comparação de snapshots usa o probe que acumula todas as tabelas alteradas, junto da comparação integral esperada de pedidos/pagamentos, conservando contas/perfis/credenciais e dados não alvo.

Try-with-resources fecha o PaidOrders antes da limpeza da fixture proprietária: remove somente os Payment IDs inseridos pelo próprio escopo, verifica preservação dos pagamentos anteriores e depois a fixture007 remove itens/pedidos/demais linhas próprias. O ID é rastreado antes de qualquer oráculo pós-setup poder falhar, garantindo a tentativa de limpeza em finally. Essa remoção é descarte da fixture, não compensação da operação produtiva. Nenhum DELETE global, alteração de seed/perfil/mapper ou relaxamento de asserção antiga foi introduzido.

### Procedimento futuro, revisão estática e conclusão de autoria

A seleção S5/T032 aprovada em [tasks.md](tasks.md) continua futura e inclui estes três componentes e as classes HTTP de lista/detalhe/segurança/regressão ROOT/Api. Deve conferir novas e antigas invocações, não apenas27 previstas; compilação/preparação não contam como GREEN. T030 deverá confrontar os testes com o comportamento existente antes de eventual ajuste; se já atender, registrar GREEN real sem RED artificial. Não foi executado S5 ou comando focal adicional nesta T028, nem antecipado T029–T036, C4, BASE, suíte completa ou CI.

Recibo ignorado `target/feature008-evidence/T028-AUTHOR-20261023/` guarda hashes dos362 arquivos recebidos, cópias dos cinco arquivos autorizados e inventário438 Surefire. Revisão somente estática por PowerShell/Git/Python externo (`-B -I -X utf8`), sem compilador/JUnit: compara357 arquivos fora do recorte, fontes originais como adições sem remoção, prefixo histórico do quickstart, somente checkbox T028 sobre tasks, relatórios anteriores idênticos, imports/caminhos/links/conteúdo novo e `git diff --check`. Resultados efetivos são registrados no fechamento após executar review.py; nenhum desses checks estabelece compilabilidade, descoberta, wiring, limpeza funcional ou aprovação das consultas.

Critérios de **autoria T028** atendidos após a revisão estática. Somente T028 marcada[X]; T029–T036 permanecem abertas. Nenhuma aprovação GREEN/S5/C5, schema, endpoint, consulta real, CI/Linux ou entrega008 é atribuída à escrita. Sem stage, commit, push, merge, mudança de branch ou alteração de main.

Resultado efetivo: `target/feature008-evidence/T028-AUTHOR-20261023/review.py` pelo Python externo com `-B -I -X utf8`, exit0/PASS estático; static-review.json confirma357 arquivos recebidos fora do recorte idênticos por SHA256, prefixo396.727bytes do quickstart intacto, somente marcador T028 em tasks,438 relatórios antigos idênticos/zero novos,40 referências locais de imports e157 links locais existentes. Conferidos métodos de fonte19→22 no DTO,17→20 no repository e26→29 no serviço; isso não é contagem de execução. Todos os trechos originais foram mantidos; diff Java só acrescenta imports e os novos blocos. `git diff --check` exit0. Não há Java novo não rastreado para revisar; recibos ficam ignorados em target.

Na revisão estática de referências, foi identificada uma chamada nova a committedSetup inexistente na classe de serviço; substituída por TransactionTemplate local REQUIRES_NEW/READ_COMMITTED com as mesmas asserções de preparação. Essa correção de autoria não é resultado de compilação ou RED funcional, pois nenhum compilador/teste foi executado. Revisados os diffs completos dos três Java e documentos, recursos/fechamento, caminhos e limites, sem enfraquecer asserções.

Git final: cinco arquivos modificados — quickstart.md/tasks.md e os três Java atribuídos à T028 —, nenhum não rastreado ou staged; raiz/branch/HEAD/seleção mantidos, main em `5cd16709ea531ccae27230db19753e6c5533d12d`. T029–T036 abertas. Sem impedimento para encerrar a autoria; compilabilidade, instrumentação/preparação e resultados funcionais continuam pendentes da execução própria. Sem stage, commit, push ou trabalho T029+.

## T029 — autoria das jornadas HTTP PAID próprias, ROOT/Api (2026-10-23)

Aplicada speckit-implement exclusivamente à [T029](tasks.md), dependente da T027. Conferências iniciais por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short --untracked-files=all` e `git diff --cached --name-only`: raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `cf19e90747ad57565081fcc8b03b644dfdbe6fb1`, seleção local008. Recebidos somente os cinco arquivos modificados da T028: DTO/repository/service e quickstart/tasks; nenhum staged ou não rastreado. Essas alterações foram preservadas. T02632 focais e C4/T02777 aprovados são evidências anteriores, sem reexecução; as27 invocações previstas na T028 **não foram executadas nem presumidas aprovadas**.

Lidos T029 integralmente, dependência T027 e condições T028–T032, spec US5/FR-016–021, plano, modelo, research R7/R8, contratos pós008, procedimentos/evidências pertinentes deste quickstart, constituição/AGENTS/harness e os suportes/fixtures/probes relacionados. Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md16/16, zero aberto, somente leitura. `.gitignore` Java existente adequado. `.specify/extensions.yml` ausente nas conferências anterior/posterior; sem hooks before/after_implement.

A T029 é **autoria de testes**, antes do confronto T030, regressões T031 e execução conjunta S5/T032. Não executados Wrapper/Maven, javac, compilação, descoberta JUnit, contexto Spring/H2, login, request HTTP, teste, validador OpenAPI, C4, BASE, suíte completa ou CI. Não há novo console/Surefire, RED/GREEN ou failpoint medido. Escrita/revisão estática não comprovam compilabilidade, preparação, wiring, autorização, SQL, commit ou endpoint. Nenhuma mudança produtiva ou trabalho T030+ foi feito.

### Alvo contratual e arquivos

Os testes escritos exigem o [snapshot completo pós008](contracts/own-orders-openapi.yaml), versão0.2.0, que admite WAITING_PAYMENT/PAID mantendo os campos fechados anteriores. A [precedência aprovada](contracts/README.md) continua:007 vigente até entrega008, depois substituição integral pelo snapshot008, com [origem007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml) histórica intacta; não há alternativas simultaneamente vigentes. Contratos/spec007, metadados planned, README público e evidências anteriores não foram editados/publicados.

Alterados nesta tarefa somente os três arquivos de suportes HTTP abaixo e esta seção/marcador T029. As classes Api, suporte genérico de autenticação, produção, fixtures/probes007/008, perfis, seed, relógio, configurações/mapper e os três Java recebidos da T028 permanecem byte-preservados. Imports e blocos novos são aditivos; nenhuma asserção, provider, método, lifecycle ou anotação anterior foi removido/modificado.

| Suporte de fonte | Método novo | Previsão adicional ROOT + Api, não executada |
| --- | --- | ---: |
| [OwnOrderQueryListHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java) | listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount | 1+1=2 |
| [OwnOrderQueryDetailHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java) | detailPost008RealPaymentKeepsCurrentNamesRecordedItemsAndExactMoney; totais42.50/0.01/9007199254740993.01 | 3+3=6 |
| [OwnOrderQuerySecurityHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java) | ownershipPost008AdminPaymentDoesNotGrantForeignQueriesOrChangeGetHeadErrors; titulares client/admin/both | 3+3=6 |
| Total | 3 métodos de fonte, herdados pelas seis classes concretas existentes | 14 previstas;0 executadas |

PaidOwnOrderHttpJourney é auxiliar final package-private no arquivo de lista, compartilhado por composição pelos três suportes. Não tem @Test/@Nested, não é classe concreta nova de teste nem contexto adicional. Os testes de segurança mantêm inclusive os métodos herdados de OwnOrderQueryHttpTestSupport. Não acrescentados @Disabled, assumptions, filtro de métodos ou seleção que oculte os originais. Na execução própria, conferir classes, contextos, invocações herdadas/parametrizadas, console e XML/TXT Surefire;14 é previsão estática adicional, não contagem descoberta/aprovada nem total da seleção S5.

### Jornadas e oráculos escritos

Cada caso novo usa PaymentRegistrationTestFixture008 própria no H2 exclusivo/estável do contexto007 existente e mantém os dados anteriores. Construtor do auxiliar insere nada; prepare ocorre dentro de try-with-resources para que falha de preparação/assertion ainda tente limpeza seletiva. A fixture008 internamente constrói suporte histórico MockMvc, mas o auxiliar não chama seu login/perform: login, POST e GET/HEAD novos são enviados por java.net.http.HttpClient à porta aleatória, com contextPath ROOT ou /api herdado, redirect desativado e timeouts finitos10s/conexão e30s/request. O marcador futuro mockMvcDispatches=0 descreve esse caminho, sem afirmar execução atual.

Login real por nome/senha deve retornar Bearer200, subject do titular e perfis efetivamente persistidos. ADMIN isolado distinto do titular paga pelo POST /admin/orders/{id}/payments real com somente occurredAt;201 deve ter exatamente orderId/occurredAt/status PAID, sem Location. **Não há INSERT de pagamento ou UPDATE de PAID simulando essa jornada.** Depois do response completo e sem transação/sincronização externa, probe008 por novas conexões físicas deve encontrar exatamente o Payment/ocorrência e somente o status do pedido alvo alterado. Oráculo compara todos os pedidos esperados e pagamentos integrais; o probe007 composto deve acusar exclusivamente tb_order, preservando outras seis tabelas, inclusive colunas opacas de contas/perfis. Só então o manifesto de consulta passa a PAID.

Paginação prepara cinco compras do titular e duas de terceiros, além dos dados antigos do contexto. Exige 2/2/1, totais5/3, página3 vazia mantendo totais, nenhum ID omitido/duplicado e desempate de três criações iguais por ID DESC. Quita a compra mais antiga com ocorrência posterior à da mais nova, mais uma compra empatada; deve manter criação DESC/ID DESC e estados mistos. Selectors de buyerId/accountId e sort solicitados continuam ignorados. Conta sem compras mantém páginas0 e3 vazias com totalElements/totalPages zero. Marcadores scenarioOwnOrders/scenarioForeignOrders referem-se somente à fixture nova, não ao total de linhas do banco.

Detalhe compara integralmente objetos JSON de resumo/detalhe/itens/página, normalizando somente tipos de nós numéricos pelo wire JSON. Mesmas quatro chaves de resumo, cinco do detalhe, cinco de cada item e cinco da página; dinheiro permanece string exata de duas casas, sem chave de pagamento, ocorrência ou comprador. Basket B/A mantém IDs/ordem, quantidades2/3, preços10.00/7.50, subtotais20.00/22.50 e total42.50; outras variantes mantêm um centavo e9007199254740993.01. Após pagar, preparação explícita em transação local REQUIRES_NEW/READ_COMMITTED muda apenas os produtos próprios para nomes atuais `Á`/` Nome atual B ` e preços0.00/37.00; snapshot independente deve confirmar somente essas alterações de catálogo, sem mudança de Payment/histórico. As consultas repetidas devem refletir nomes atuais, criação original e valores históricos, inclusive catálogo legado zero, sem reparação.

Segurança parametriza titular CLIENT, ADMIN isolado e ambos. ADMIN pagador de terceiro deve ver somente sua própria compra WAITING_PAYMENT na lista/count/detail, com página seguinte vazia; selectors de outro dono não ampliam o resultado. Compra PAID alheia e ID comprovadamente inexistente devem produzir404 com mesmo envelope seguro, exceto path específico; HEAD404 sem corpo. Titular continua lendo PAID. Após quitação, GET/HEAD válidos e inválidos exigem401 para ausência/token malformado,403 para conta sem perfil e400 de consulta para titular autorizado, com mensagens próprias da consulta. Só401 tem WWW-Authenticate: Bearer; HEAD de sucesso/erro é vazio. Casos históricos de credenciais/perfis/precedência permanecem herdados intactos.

Cada GET/HEAD novo é cercado por fotografias008 antes/depois, fora de setup/login/pagamento/renomeação/limpeza, exige oito tabelas inalteradas e reutiliza Observation007 sem alterar seu significado: somente SELECT no SQL Hibernate observado. Exige também ausência de consulta Hibernate a tb_payment. A limpeza008 remove exclusivamente Payment dos pedidos próprios antes de itens/pedidos e demais linhas da fixture; snapshot final deve coincidir integralmente com o recebido e URL do banco permanecer igual. Isso é descarte de fixture, sem compensação da operação produtiva.

### Procedimentos, revisão e limites

Comandos efetivos desta autoria: conferências Git acima; pré-requisitos Spec Kit; leituras por Get-Content/rg e revisão `git diff --`/`git diff --stat`/`git diff --numstat`; auxiliares estáticos prepare.py/review.py pelo Python externo `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T029-AUTHOR-20261023/<auxiliar>.py`; `git diff --check`. Não houve comando de execução de testes. Recibo ignorado `target/feature008-evidence/T029-AUTHOR-20261023/` guarda cópias recebidas dos cinco arquivos atribuídos à tarefa, hashes362 arquivos rastreados, inventário438 Surefire e hashes das evidências anteriores presentes nos diretórios inventariados, inclusive T028. prepare.py exit0; resultados de review.py e diff-check são registrados abaixo após sua execução efetiva.

Revisão estática compara357 arquivos fora do recorte (incluindo três Java recebidos, classes Api, produção e contratos) por SHA256, fontes originais mantidas em ordem sem remoção, prefixo integral do quickstart e somente marcador T029 sobre tasks recebido; confere imports locais, referências/links, novos métodos/auxiliar, whitespace, relatórios antigos idênticos e ausência de novos. Não há Java novo não rastreado. Eventual erro de compilação/preparação futuro deverá ser classificado como tal; não é RED funcional. Projeção existente que já satisfaça os casos pode obter GREEN real, sem fabricar falha.

Nenhum contexto, estágio de failpoint, SQL, leitura independente, limpeza, HTTP ou resultado de banco foi efetivamente exercitado nesta T029. Os seis contextos previstos são os já declarados pelos testes ROOT/Api de lista/detalhe/segurança; sua inicialização/identidade e contagens reais permanecem por conferir. Não se repetem os experimentos de rollback/transporte da US4 nem se amplia sua cobertura. Asserções após resposta usam leitura independente, não flush como commit, e não instalam a telemetria transacional/failpoints da T023. Snapshot composto não é fotografia atômica sob escritores concorrentes; StatementInspector não vê JDBC direto e igualdade final não exclui toda mutação transitória. H2 local não demonstra outro SGBD, durabilidade, pane física, crash recovery ou commit de resultado desconhecido.

Critérios de **autoria T029** atendidos após revisão estática; somente T029 passa a[X], mantendo T030–T036 abertas. A seleção exata S5/T032 de [tasks.md](tasks.md) permanece futura, sequencial pelo Wrapper com relatórios próprios/preservados e conferência de todos os originais e novos, sem deduzir aprovação de14 ou27 invocações previstas. Regressões adicionais pertencem à T031; confronto/possível ajuste produtivo à T030; execução funcional a S5/T032 e FINAL na etapa autorizada. Não executados stage, commit, push, merge, alteração de main ou publicação da Feature008.

Resultado efetivo da revisão: review.py pelo Python externo com `-B -I -X utf8`, exit0/PASS **somente estático**. static-review.json confirma357/357 arquivos fora do recorte idênticos, prefixo408.938bytes do quickstart recebido intacto, somente marcador T029 em tasks,438 relatórios Surefire anteriores idênticos/zero novos e463 arquivos das evidências anteriores inventariadas idênticos. Conferidos18 imports locais distintos e165 links locais existentes; métodos de fonte11→12/lista,7→8/detalhe,5→6/segurança, sem contar os métodos herdados de autenticação como novos. `git diff --check` exit0; avisos LF/CRLF do Git não são erro de whitespace. Revisados os diffs completos de fontes/documentos e o conteúdo do auxiliar novo dentro do Java rastreado; nenhum arquivo novo fora de target ignorado. Na autoria, retirado um import JdbcTemplate duplicado recém-adicionado e explicitada a rejeição de JSON vazio no oráculo; não são diagnósticos de compilação/teste, pois não foram executados.

Git final: oito arquivos modificados — os cinco recebidos da T028 e os três suportes HTTP de lista/detalhe/segurança acrescentados pela T029 —; nenhum staged ou não rastreado. HEAD/branch/raiz/seleção008 mantidos, main em `5cd16709ea531ccae27230db19753e6c5533d12d`. T001–T029[X], T030–T036 abertas. Sem impedimento para encerrar a autoria; compilabilidade, descoberta, contextos, preparação/limpeza, console/Surefire e comportamento continuam pendentes da execução própria. Sem stage, commit, push ou trabalho T030+.

## T030 — confronto das consultas com os testes pós008 (2026-10-23)

Executada exclusivamente [T030](tasks.md), após autoria T028/T029; lidos integralmente sua descrição, dependências, seleção e condições de execução, além de spec US5/FR-016–021, plano, pesquisa/modelo, contratos, procedimentos deste quickstart e componentes pertinentes, sob constituição/AGENTS/harness. Confirmados inicialmente `git rev-parse --show-toplevel` = `C:/Users/ronal/dscommerce-speckit`, `git branch --show-current` = `008-registrar-pagamento`, `git rev-parse HEAD` = `cf19e90747ad57565081fcc8b03b644dfdbe6fb1` e seleção local008. `git status --short --untracked-files=all` mostrou somente os oito arquivos recebidos abaixo; `git diff --cached --name-only` vazio e nenhum não rastreado.

Caminhos completos conferidos: `C:/Users/ronal/dscommerce-speckit/specs/008-registrar-pagamento/quickstart.md`; `C:/Users/ronal/dscommerce-speckit/specs/008-registrar-pagamento/tasks.md`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java`; `C:/Users/ronal/dscommerce-speckit/src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java`.

Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md16/16, zero aberto, somente leitura; `.gitignore` Java adequado. extensions.yml ausente antes/depois, sem hooks before/after_implement. Mantidos raiz/branch/HEAD/seleção e main em `5cd16709ea531ccae27230db19753e6c5533d12d`.

### Alcance autorizado e comando executado

T030 não é somente autoria: confronta os testes com a implementação e permite ajuste apenas se necessário. A linha S5 da tabela aprovada em tasks determina executar **antes de eventual T030 e após ajuste; se nenhuma alteração necessária, um GREEN basta**. Por isso foi executada uma única vez a seleção S5 exata, com todos os componentes atuais, antes de qualquer alteração produtiva. Isso **não executa a autoria T031 nem encerra T032/C5**: as classes de regressão já existentes participam do seletor obrigatório, mas ainda não contêm as extensões pós-quitação da T031. Não executados C4, BASE, suíte completa, FINAL, CI, publicação, T031+ ou execução focal adicional.

Comando efetivo pelo Wrapper, na raiz PowerShell, encaminhado como array de argumentos pelo auxiliar run.ps1:

~~~powershell
.\mvnw.cmd "-Dtest=OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest,OwnOrderQueryList*HttpIntegrationTest,OwnOrderQueryDetail*HttpIntegrationTest,OwnOrderQuerySecurity*HttpIntegrationTest,OwnOrderQueryRegression*HttpIntegrationTest" "-Dsurefire.reportNameSuffix=S5-T030-102637807" test
~~~

Antes da chamada: nenhum processo Maven/Surefire ativo observado;438 relatórios anteriores inventariados e copiados para `target/feature008-evidence/T030-20261023/surefire-before/`, com conferência SHA256 das cópias pelo runner. Nenhuma chamada Maven em paralelo. Recibo preserva hashes362 arquivos rastreados, cópias dos oito modificados e inventário9863 arquivos das evidências anteriores. Console exclusivo, metadados e relatórios novos em `target/feature008-evidence/T030-20261023/S5-T030-102637807/`: `run.json`, `console.log`, `exit.json`, `analysis.json`, `all-reports-after.json` e `surefire-reports/` com11 XML+11 TXT próprios. Console mantém a codificação UTF-16 gerada pelo redirecionamento PowerShell; análise a reconhece sem reescrever o original.

Início `2026-10-23T10:26:37.8226901-03:00`; término do runner `2026-10-23T10:31:52.3189600-03:00`; Maven terminou às10:31:51 com duração05:08. **Exit0, BUILD SUCCESS:750 testes,0 falhas,0 erros,0 ignorados**, conforme console e Surefire. Java observado por `java --version`/propriedades XML: Zulu21.0.12.1, Azul Systems, JVM em `C:/Program Files/Zulu/zulu-21`; os.name reportado `Windows 10`. Wrapper configurado para Maven3.9.16 nas propriedades preservadas; Boot3.3.4/Hibernate6.5.3.Final nos logs, H2 2.2.224 do ambiente aprovado. Compilação normal recompilou148 fontes de teste com release21; **148 fontes compiladas não são148 testes nem execução da suíte inteira**. Não houve erro de compilação/preparação ou falha funcional a diagnosticar/corrigir.

### Seleção efetiva, métodos e contextos

| Classe concreta (prefixo OwnOrderQuery) | Contexto | Testes efetivos | Métodos de fonte efetivos, incluindo herança |
| --- | --- | ---: | ---: |
| DtoContractTest | JsonTest, sem DataSource/MVC/Security | 130 | 22 |
| RepositoryTest | MOCK/JPA, H2 exclusivo | 41 | 20 |
| ServiceTest | MOCK/JPA + casos locais controlados | 91 | 29 |
| ListHttpIntegrationTest | ROOT | 116 | 12 |
| ListApiHttpIntegrationTest | /api | 116 | 12 |
| DetailHttpIntegrationTest | ROOT | 12 | 8 |
| DetailApiHttpIntegrationTest | /api | 12 | 8 |
| SecurityHttpIntegrationTest | ROOT | 82 | 9 |
| SecurityApiHttpIntegrationTest | /api | 82 | 9 |
| RegressionHttpIntegrationTest | ROOT, casos existentes | 34 | 5 |
| RegressionApiHttpIntegrationTest | /api, casos existentes | 34 | 5 |
| Total | 11 classes concretas | 750 | Parâmetros não são novos métodos de fonte |

XML/TXT/console conferidos: nenhuma classe esperada omitida/excedente, nenhum par classe+nome de invocação duplicado; conjunto de métodos do XML igual ao declarado/herdado. Matrizes de métodos/quantidades iguais entre ROOT e Api. Nenhuma classe @Nested/@Disabled nos componentes selecionados; auxiliares PaidOrders/PaidOwnOrderHttpJourney não têm testes aninhados descobertos a somar. As3 rotinas herdadas de autenticação continuam em cada classe de segurança:40 credenciais ausentes/inválidas/expiradas/conta removida,8 sem perfil e12 perfis autorizados. Somam60 das82 invocações por contexto; as demais são19 de negócio anteriores+3 novas. A guarda histórica permite200/404/405 apenas para alcançar routing; as jornadas de negócio novas exigem200/400/401/403/404 exatos, sem relaxar a guarda antiga.

As27 invocações T028 foram **agora executadas/aprovadas**, não aprovadas retroativamente pela autoria: DTO11 (1 contrato textual+9 dinheiro+1 página mista), repository8 (1 paginação+4 titulares+3 históricos) e service8 (1 paginação+4 titulares+3 históricos). As14 da T029 também executadas/aprovadas: lista1 por contexto, detalhe3 e segurança3 por contexto. Portanto750 =709 invocações anteriores+41 novas. A análise cruzou as quantidades por método anterior com os relatórios históricos correspondentes preservados (119 DTO,33 repository,83 service;115 lista,9 detalhe,79 segurança e34 regressão por contexto), apenas para conferir ausência de perda de invocações, sem usar resultado antigo como aprovação nova. Nenhuma assertion, provider, classe Api ou suporte recebido foi alterado.

Contextos observados por marcadores e URLs H2 distintas: detalhe/api porta54813 banco `d1503e97-d98f-4fb4-b58c-73a6a9edff79`, detalhe/ROOT54818 `621c292f-d2dd-4ae1-9ffc-40f8507401f8`; lista/api54823 `0fc4a7cd-0972-4106-a7fb-c9712418b601`, lista/ROOT54875 `4ca22188-f38b-49ce-b648-1cab76d962a1`; regressão/api54927 `a8445928-64cc-4620-a32b-98c30254dd1c`, regressão/ROOT54947 `b8d3b0f7-e753-4c48-8dae-22cc47d42bc9`; segurança/api54966 `36d9684a-7714-48bd-9e17-f683dacb49a2`, segurança/ROOT54992 `4a0533c9-cce6-4fa7-8813-3e90287c1081`; repository/MOCK `6334990a-6249-4961-aa08-236fab2d9ea9` e service/MOCK `2b58c343-ba4f-4e7f-b9bc-1fe4715f3b84`. Cada UUID recebe o prefixo `jdbc:h2:mem:admin-product-test-`, confirmado no console. São10 bancos/contextos de persistência mais o contexto JSON sem banco; os14 marcadores T029_CONTEXT reutilizam os seis contextos de lista/detalhe/segurança, não representam14 bancos.

Contagens observadas nas jornadas novas T029:18 POST201 com confirmação independente Payment/PAID e340 GET/HEAD cercados pelo probe008; todos os expected/actual concordam, HEAD sem corpo. Esses números não são750 testes nem telemetria de commit/failpoint. As contagens históricas T009/T017/T005/T023 continuam separadas: o auxiliar de composição novo usa transporte próprio e não incrementa seus contadores; marcadores T028/T029 das classes históricas de regressão são rótulos da Feature007, não prova de que as tarefas008 homônimas foram executadas por esses marcadores.

### Confronto com a implementação e decisão

| Componente revisto | Evidência de código + resultado desta execução |
| --- | --- |
| [OwnOrderQueryService.java](../../src/main/java/com/devsuperior/dscommerce/services/OwnOrderQueryService.java) | list/detail readOnly exigem conta atual e usam ownerId da guarda; summary/detail projetam order.getStatus(), sem restringir WAITING_PAYMENT. Remonta conteúdo na sequência dos IDs paginados; valida total histórico e mapeia DTO dentro da transação. PAID aprovado sem alteração. |
| [OrderRepository.java](../../src/main/java/com/devsuperior/dscommerce/repositories/OrderRepository.java) | Página de IDs e count têm o mesmo buyer.id=:ownerId, ordenação createdAt DESC/id DESC. Lote também restringe ownerId+ids; detalhe id+ownerId, sem probe global de existência. Não há filtro que exclua PAID nem join de Payment nas consultas próprias; itens completos vêm após paginar raízes. |
| [OwnOrderSummaryDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderSummaryDTO.java) e [OwnOrderDetailDTO.java](../../src/main/java/com/devsuperior/dscommerce/dto/OwnOrderDetailDTO.java) | Mesmas4/5 chaves; status OrderStatus já admite PAID; createdAt mantém UTC.SSS; total string monetária validada e lista defensiva no detalhe. Nenhuma chave ou referência de pagamento adicionada. |
| Order/itens, guarda e handlers existentes | Itens por position ASC; nome atual do produto, quantidades/unitPrice/subtotais/total históricos exatos. ADMIN continua limitado ao próprio ownerId no GET, inclusive após pagar terceiro. Erros continuam próprios da consulta e HEAD vazio. Sem nova consulta administrativa geral. |

Asserções atuais aprovaram cinco pedidos2/2/1 com empate criação/ID, página além do fim e conta vazia, sem reordenar a compra antiga por ocorrência do pagamento. Leram PAID em lista/detalhe com os mesmos campos, nomes atuais e dinheiro42.50/0.01/9007199254740993.01; mudanças explícitas de catálogo para nomes legados/preços0.00/37.00 não substituíram histórico nem criação. ADMIN pagador não recebeu conteúdo/count alheios; detalhe alheio teve404 seguro igual ao inexistente, enquanto CLIENT/ADMIN/ambos titulares continuaram acessando. GET/HEAD preservaram autorização/precedência, erros e todas as tabelas observadas, incluindo tb_payment nas novas janelas008.

**Nenhuma correção necessária.** A implementação recebida já satisfaz o alvo testado da T030; não se criou RED artificial, alteração cosmética produtiva, relaxamento de assertion ou reexecução para obter verde. Os seis Java T028/T029 e todo src/main permanecem byte-idênticos aos recebidos. Alterações desta tarefa restringem-se a esta seção e marcador T030; recibos em target são ignorados.

### Precedência, limites e fechamento

O [contrato alvo008](contracts/own-orders-openapi.yaml) versão0.2.0 admite PAID; o teste textual dele é uma das130 invocações de DTO, **não uma validação estrutural OpenAPI completa ou prova isolada de endpoint**. A execução real de JPA/serviço/HTTP fornece a prova comportamental desta revisão. Mantida a [precedência](contracts/README.md):007 continua vigente até entrega008; o snapshot008 completo o substituirá integralmente após entrega, mantendo [origem007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml) histórica intacta. Nenhum contrato, spec, README ou metadado editorial foi alterado/publicado; admissão de PAID no código não amplia silenciosamente o contrato007 fechado.

Repository/service usam setup JDBC declarado Payment+PAID na mesma transação local concluída, não o POST nem autorização do pagador. As jornadas HTTP T029 fazem POST real, conferem snapshots independentes após a resposta e mantêm Payment+PAID; não equiparam flush a commit nem reexecutam os failpoints/telemetria de commit/transporte da US4. Nenhum failpoint novo ou falha física foi provocado nesta seleção. Snapshots compostos pressupõem término dos escritores; não são fotografia atômica sob concorrência livre. StatementInspector só observa SQL Hibernate e snapshots iguais não excluem toda mutação transitória por JDBC direto. Limpeza seletiva de Payment antes dos pedidos funcionou nos casos novos, preservando o estado recebido; isso é descarte de fixture, não compensação produtiva.

Os34 casos de regressão por contexto são os existentes; sua aprovação não demonstra as futuras janelas pós-quitação de regressão, observador financeiro e demais critérios T031. Não há prova nova de cobrança/gateway, pane física, crash recovery, outro SGBD ou CI/Linux. C4/T026 e seus limites permanecem históricos, sem repetição. T032/C5 e FINAL continuam pendentes. Advertências de SecurityManager deprecated na compilação de fontes antigas, H2Dialect, agente ByteBuddy/VM e registros esperados de violações referenciais na regressão não são erro de compilação/teste; console completo preservado.

Procedimentos auxiliares somente de leitura/análise/documentação: prepare.py (inventário/cópias), analyze.py (reconciliação console/XML/TXT e fontes), verify-prior.py (SHA256 das9863 evidências anteriores, exit0/PASS) e review.py, pelo Python externo `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T030-20261023/<auxiliar>.py`; leituras Get-Content/rg; `git diff --`, `git diff --stat`, `git diff --numstat`, `git diff --check` e estado final. analyze.py exit0, confirmou11 classes/750 invocações/nenhuma omissão ou duplicata,27+14 novas e438 relatórios antigos intactos, mais22 novos próprios (460 ao todo). Revisão final de prefixo, diffs, links, hashes e whitespace fica registrada abaixo após execução efetiva.

Critérios T030 atendidos por confronto de código e GREEN real nesta seleção, sem mudança produtiva; somente T030 passa a[X], T031–T036 permanecem abertas. Sem stage, commit, push, merge, alteração de branch/main ou execução de tarefas posteriores.

Resultado efetivo do fechamento: review.py exit0/PASS; final-review.json confirma360/360 arquivos rastreados fora dos documentos idênticos por SHA256, inclusive seis Java recebidos, classes Api, implementação, configurações, contratos e testes de regressão; prefixo422.590bytes recebido do quickstart intacto e tasks diferente do recebido somente no marcador T030. Links locais173/173 existentes; `git diff --check` exit0, nenhum diagnóstico de whitespace (avisos LF/CRLF não alteram esse resultado). Surefire460 arquivos =438 antigos preservados+22 próprios novos; cópias novas conferidas com os originais. Evidências anteriores9863/9863 idênticas conforme verify-prior.py; nenhum Java novo não rastreado. Diffs documental e recebido→final revisados, sem alterações dos trechos anteriores; diff Java acumulado desde HEAD mantém integralmente a autoria T028/T029 recebida. Nenhum Maven/Surefire ativo observado após a execução.

Git final: os mesmos oito arquivos modificados, nenhum staged ou não rastreado; somente quickstart/tasks receberam alterações desta T030, seis testes permanecem como recebidos. Raiz, branch, HEAD `cf19e90747ad57565081fcc8b03b644dfdbe6fb1`, seleção008 e main preservados. T001–T030[X], T031–T036 abertas. Sem impedimento funcional, de compilação ou preparação observado nesta seleção; continuam pendentes extensões T031, checkpoint T032/C5, auditoria/FINAL e publicação nas etapas autorizadas. Sem stage, commit, push, alteração de main ou trabalho T031+.

## T031 — autoria das regressões após quitação e observação008 (2026-10-23)

Aplicada speckit-implement exclusivamente à [T031](tasks.md), dependente da T030. Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `cf19e90747ad57565081fcc8b03b644dfdbe6fb1`, seleção local008, os oito arquivos modificados recebidos e nenhum staged/não rastreado. Comandos: `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short --untracked-files=all`, `git diff --cached --name-only`, leitura de `.specify/feature.json` e resolução dos caminhos completos por Join-Path/GetFullPath.

Os oito caminhos recebidos, sob `C:/Users/ronal/dscommerce-speckit/`, são `specs/008-registrar-pagamento/quickstart.md`, `specs/008-registrar-pagamento/tasks.md`, `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java`, `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java`. Todos conferidos por caminho absoluto; as alterações recebidas permanecem preservadas.

Lidos integralmente T031, sua dependência T030 e condições T032/S5, além dos artefatos pertinentes: spec US5/FR-016–022, plano, pesquisa/modelo, contratos/precedência, procedimentos/evidências deste quickstart, constituição/AGENTS/harness, suportes de regressão ROOT/Api, fixtures/probes e caminhos produtivos de pagamento. Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md16/16, zero aberto, somente leitura. `.gitignore` Java adequado; extensions.yml ausente antes/depois, sem hooks before/after_implement.

### Escopo e evidência recebida

T031 exige **estender testes**, não é tarefa exclusivamente documental. A execução conjunta das extensões é T032, dependente desta autoria; não há exigência de invocação separada nesta T031. Não executados Maven/Wrapper, javac, compilação, descoberta JUnit, servidor/contexto Spring/H2, login, HTTP, teste ou validador OpenAPI. Não repetidos S5, C4, BASE, suíte completa, FINAL ou CI. Nenhum failpoint, hook de rede ou resultado de aplicação novo foi medido. A revisão estática não comprova compilabilidade, preparo/limpeza, wiring, sensibilidade efetiva ou aprovação funcional dos casos novos.

Conferida em leitura a evidência anterior `target/feature008-evidence/T030-20261023/S5-T030-102637807/`: exit.json0,11 XML/TXT e console BUILD SUCCESS com **750 testes,0 falhas,0 erros,0 ignorados**, preservados. Essa execução incluiu as27 novas invocações T028 e14 T029 na revisão então recebida, sem alterações produtivas. Os34 casos por contexto da classe de regressão eram os antigos;750 não aprova as extensões agora escritas, não encerra T031 funcional, T032/C5 ou a Feature008. Relatórios antigos não foram regenerados/reinterpretados como aprovação de fonte nova.

### Arquivos e invocações previstas

A extensão está no suporte compartilhado de [OwnOrderQueryRegressionHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionHttpIntegrationTest.java). [OwnOrderQueryRegressionApiHttpIntegrationTest.java](../../src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionApiHttpIntegrationTest.java) continua byte-idêntico: já herda esse suporte e mantém porta aleatória/contexto `/api`, portanto recebe as mesmas jornadas por herança. Não há duplicação de métodos, edição de sua configuração ou classe Api nova. Os seis Java recebidos de T028/T029, suporte genérico, fixtures/probes antigos, configurações/POM e produção estão intactos.

| Método novo, herdado pelos dois contextos | Autoria de cobertura | Previsão ROOT+Api |
| --- | --- | ---: |
| regressionPost008RepeatedGetHeadSuccessEmptyAndErrorsKeepAllEightTables | 13 alvos históricos x GET/HEAD x3 após quitação, mais conteúdo/count e404 do ADMIN pagador | 1+1 |
| regressionPost008PaymentRejectionsPreserveAllTablesAndTheOriginalSettlementWithoutExternalAttempts | 403/400/404,201 real, repetição409 igual/diferente/outro ADMIN, leitura PAID e oito tabelas | 1+1 |
| regressionPost008PublicCatalogFilterPagesDetailsAndRealLoginRemainIndependentOfPaidQueries | Catálogo/detalhe/filtro/páginas públicos e login real/exato após PAID | 1+1 |
| regressionPost008MaintenanceAndRepeatedRegistrationsPreservePaidHistoryAndCreateNewWaitingOrders | Manutenção autorizada/rejeitada, proteção referencial e dois POST iguais com novas compras WAITING_PAYMENT | 1+1 |
| Total | 4 métodos de fonte novos | 8 previstas;0 executadas |

Os26 pares de readCases no primeiro método são um loop dentro de cada novo @Test, com três repetições; não são26 novas invocações JUnit. Os cinco métodos históricos (34 invocações por contexto) continuam com providers, lifecycle e asserções originais. A previsão passa a38 por contexto da regressão quando executada integralmente, sem atribuir essa contagem a Surefire nesta autoria. PaidRegression e ExternalAttemptProbe são auxiliares privados aninhados sem @Test/@Nested; não acrescentados @Disabled, assumptions, seleção de métodos ou omissão de ROOT/Api. A seleção exata S5 de T032 permanece a aprovada em tasks, incluindo todos os componentes, com Wrapper sequencial, console e relatórios próprios/preservados na execução futura.

### Estado de banco, leituras e jornadas escritas

PaidRegression compõe PaymentRegistrationDatabaseProbe008 sobre o mesmo H2 exclusivo e estável do contexto de regressão007. Usa o cenário JDBC commitado existente, que começa WAITING_PAYMENT e sem Payment próprio; não modifica o significado da fixture/probe007 nem fabrica PAID. Quitação passa pelo POST real em porta aleatória, usando JWT obtido por login HTTP real e corpo somente occurredAt.201 exige exatamente orderId/occurredAt/status PAID, sem Location/challenge. Após resposta completa e sem transação/sincronização externa, a leitura008 por conexões independentes deve conter exatamente um novo Payment/ocorrência e somente status PAID do pedido alvo; todos os pedidos completos e pagamentos são comparados e o probe007 deve acusar exclusivamente tb_order, preservando outras seis tabelas/colunas opacas. Só depois o manifesto esperado local admite PAID; originais não são alterados.

Rejeições escritas: CLIENT403, ocorrência anterior à criação400, ID comprovadamente ausente404, repetição409 da mesma ocorrência e outra ocorrência por outro ADMIN. Cada uma exige envelope seguro específico, oito tabelas idênticas e ausência de DML no SQL Hibernate observado; a ocorrência original permanece por comparação integral após repetição. Não há confirmação reutilizada como sucesso ou correção de estado. Códigos/macros de failpoint e falhas físicas não são presumidos nem provocados por esses casos.

GET/HEAD novos reutilizam o read original, que exige status/header/body exatos, estado007 inalterado e SQL SELECT-only; wrapper008 acrescenta fotografias antes/depois incluindo tb_payment em finally. Setup, login preparatório, POST, manutenção intencional e limpeza ficam fora das janelas GET/HEAD. Os13 alvos abrangem sucesso de lista/detalhe, conta vazia/página vazia e além do fim,401/403, entrada inválida400, alheio/ausente404 e remoção da query no path. Oráculos JSON são cópias locais com somente status dos pedidos comprovadamente pagos atualizado. ADMIN que quitou terceiro continua com uma compra própria/count próprio e404 para o PAID alheio, inclusive HEAD. Chaves, totais, conteúdo e dinheiro anteriores permanecem fechados; nenhuma chave de pagamento nova é admitida.

Jornada pública exige filtro literal `PRODUTO`, páginas tamanho1 em1/1/0 com contagem2, filtro `B atual`, detalhes e404 sem token e com Bearer irrelevante inválido. Preserva preços públicos numéricos e nomes/categorias atuais. Login após PAID continua por nome/senha exatos:200 só accessToken/tokenType com subject real; nomes/senhas com espaços alterados têm401 idêntico e envelope seguro; entrada incompleta400. Token anterior continua consultando o próprio PAID. Login é cercado por snapshots008, sem alegar janela GET/HEAD para POST.

Manutenção compara cinco campos de erro administrativos, incluindo errors, ao rejeitar CLIENT403 e propriedade desconhecida400. ADMIN pode renomear somente o produto próprio; oráculo permite exclusivamente essa mudança de catálogo e conserva Payment/histórico. DELETE de produto referenciado pelo pedido pago exige409 seguro e oito tabelas intactas. Registro rejeita campo extra400; dois envios válidos com os mesmos bytes devem criar IDs novos, ambos WAITING_PAYMENT, sem Payment novo. O register histórico já verifica raiz/itens completos e todos os anteriores; wrapper008 acrescenta preservação de pagamentos e comparação das colunas opacas, com somente tb_order/tb_order_item alteradas. Página própria tem sete pedidos em criação DESC/ID DESC, incluindo os dois novos, mantendo PAID antigo, nomes atuais e valores históricos42.50/10.00/7.50 em vez do preço atual12.00/99.00. Nenhuma tabela/rota de carrinho foi criada: seu estado pertence ao consumidor e não é observado por este banco.

Limpeza em try-with-resources remove exclusivamente Payment dos IDs pertencentes ao cenário e dos novos pedidos HTTP rastreados, antes de o @AfterEach original remover itens/pedidos/demais dados próprios. O cenário exige que esses IDs não tenham pagamentos pré-existentes; todos os pagamentos anteriores fora desse conjunto devem permanecer iguais. A limpeza tenta alcançar também um novo pedido próprio erroneamente pago antes de uma assertion falhar; não apaga dados alheios. Remover Payment antes de remover a raiz é descarte de fixture, sem restaurar status para simular rollback ou compensação produtiva. Não há INSERT de Payment/UPDATE de PAID de setup nos casos novos.

### Observação de tentativas de integração e limites

ExternalAttemptProbe usa os hooks Java21 de SecurityManager para DNS/connect e URLPermission, com estado volatile e contadores seguros entre a thread do teste e workers do servidor. É instalado somente ao redor de cada POST de pagamento, para sucesso e rejeições, e restaurado em finally/close. Exige ausência de outro manager e instalação efetiva; indisponibilidade, zero hooks reais ou falta de restauração falham explicitamente, sem skip ou aprovação presumida. POM permanece intacto: já contém `-Djava.security.manager=allow` pelo suporte histórico005. Esse mecanismo deprecated está restrito ao JDK21 aprovado, sem promessa para outro runtime.

Controles escritos chamam **diretamente** checkConnect com DNS `.invalid`, endereço documental203.0.113.1 e checkPermission URLPermission; exigem detecção/bloqueio dos três tipos e depois zeram os contadores antes da janela real. Não resolvem DNS, abrem socket ou enviam request financeiro para testar o sensor. Transporte do POST é HttpClient novo, criado com observer instalado e sem reutilizar socket anterior, redirect desativado, conexão10s/request30s. Só loopback da porta aleatória do harness (e sua resolução local) é permitido. Demais destinos são registrados por tipo e bloqueados antes da operação JDK; nenhuma credencial/host de terceiro é impressa. A janela armada vai do envio à leitura completa da resposta; exige hooks reais >0 e eventos proibidos vazios, mesmo se uma biblioteca capturar sua exceção.

O marcador futuro T031_NETWORK descreve **hooks JDK**, não uma contagem física universal. Não identifica comercialmente cada destino como financeiro; exige ausência de qualquer tentativa não pertencente ao harness no alcance observado. Não cobre chamadas nativas/processos externos, UDP/multicast, conexões preexistentes que contornem hooks ou trabalho assíncrono posterior à janela. O runner/cliente novo reduz o ponto cego de reutilização no próprio harness; não transforma isso em prova geral de rede. Nenhuma dessas verificações/controles foi executada nesta autoria.

Inspeção estática dos caminhos AdminPaymentController → PaymentRegistrationService → PaymentAccessService/repositórios mostra somente autenticação, relógio, persistência/lock, histórico, transição e DTO; sem gateway, cliente financeiro ou cobrança. Busca `rg -n -g '*.java' 'HttpClient|RestClient|WebClient|RestTemplate|URLConnection|new Socket|ProcessBuilder|Runtime.getRuntime|gateway|stripe|paypal|adyen' src/main/java` não retornou correspondências (exit1 de ausência de matches). Isso complementa a leitura de dependências, não prova sozinho exclusão de toda IO possível. O código do JDK local em `C:/Program Files/Zulu/zulu-21/lib/src.zip` foi consultado em leitura: Exchange.java usa sm.checkPermission(URLPermission, acc); por isso o observador escrito trata também a sobrecarga com contexto. Não se compilou/executou Java nessa consulta. Os trechos ficam em jdk-permission-source.txt no recibo.

Fotografias008 são compostas, após fim dos escritores conhecidos, não snapshot atômico sob concorrência livre. StatementInspector não vê JDBC direto; igualdade final não exclui mutação transitória restaurada. Resposta201 mais leitura independente não equivale à instrumentação de commit da US4 e não trata flush como commit. Não reproduz pane física, resultado indeterminado, crash recovery, durabilidade de produção ou outro SGBD. Limites T026/C4/S5 anteriores permanecem intactos. Não se publica contrato, C5 ou entrega008 por escrever esses testes.

### Procedimentos e revisão de autoria

Recibo ignorado `target/feature008-evidence/T031-AUTHOR-20261023/` guarda cópias dos três arquivos do recorte, hashes362 rastreados,460 relatórios Surefire e10350 arquivos das evidências anteriores. Comandos efetivos: conferências Git/Spec Kit acima, Get-Content/rg, `git diff --`/`git diff --numstat`/`git diff --check`, e prepare.py/inspect-jdk.py/verify-prior.py/review.py pelo Python externo `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T031-AUTHOR-20261023/<auxiliar>.py`. prepare.py, inspect-jdk.py e verify-prior.py exit0; último confirma10350/10350 evidências anteriores idênticas. Não são testes da aplicação ou validação estrutural OpenAPI.

Revisão estática exige359 arquivos rastreados fora do recorte idênticos (inclusive os seis testes recebidos, Api, produção/contratos/fixtures/configuração), preservação de todas as linhas originais do suporte em ordem, somente adições Java, prefixo recebido do quickstart e somente marcador T031 sobre tasks recebido. Confere métodos/imports locais, referências/links, recursos/limpeza, relatórios antigos idênticos/nenhum novo e whitespace. Dois cuidados de autoria corrigidos antes dessa revisão: cast explícito ObjectNode para acrescentar propriedade no oráculo de registro e passagem das fotografias reais before/after à Observation007 na rejeição; nenhuma assertion foi enfraquecida e isso não é diagnóstico de compilação/RED executado. Resultado efetivo de review.py fica registrado abaixo após execução.

Mantida a [precedência](contracts/README.md): [snapshot008 completo](contracts/own-orders-openapi.yaml) admite WAITING_PAYMENT/PAID e substituirá integralmente007 após a entrega; [contrato007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml) continua vigente até lá e histórico intacto, sem alternativas simultâneas. Nenhuma alteração de schema normativo, campos, titularidade ou metadado público. Critérios de **autoria T031** atendidos após revisão estática; somente T031 passa a[X], T032–T036 abertas. Compilação, observação efetiva, contextos e aprovação dos novos casos continuam pendentes da execução própria. Sem stage, commit, push, merge, alteração de main ou trabalho T032+.

Resultado efetivo: review.py exit0/PASS **somente estático**, com static-review.json confirmando359/359 arquivos rastreados fora do recorte idênticos, prefixo439.116bytes recebido do quickstart intacto, somente marcador T031 em tasks e todas as linhas originais do suporte mantidas em ordem. Métodos de fonte5→9, quatro novos herdados por ROOT/Api (oito previstos/zero executados);16 imports locais distintos e179 links locais existentes. Surefire460/460 e evidências anteriores10350/10350 idênticos, nenhum relatório novo. Leitura dos11 XML e console/exit S5 anterior confirma750/0/0/0/exit0 histórico, sem executar a seleção novamente. Diffs de Java/documentos, auxiliares novos no arquivo rastreado, referências, recursos e whitespace revisados; `git diff --check` exit0, sem diagnóstico (avisos LF/CRLF não são falha). Nenhum Java novo não rastreado; recibos permanecem ignorados em target. Nenhum processo Maven/Surefire ativo observado nesta tarefa.

Git final: nove arquivos modificados — os oito recebidos e OwnOrderQueryRegressionHttpIntegrationTest.java. Somente esse suporte e quickstart/tasks receberam alterações nesta T031; o aliasApi foi estendido por herança, mantendo seu arquivo byte-idêntico. Nada staged ou não rastreado; raiz/branch/HEAD/seleção008 mantidos, main em `5cd16709ea531ccae27230db19753e6c5533d12d`. T001–T031[X], T032–T036 abertas. Sem impedimento para encerrar a autoria; nenhum GREEN novo é declarado. Compilação/preparação/limpeza, sensibilidade/restauração do observer, contagens reais e validação funcional continuam pendentes de T032, seguida das etapas aprovadas de fechamento. Sem stage, commit, push, alteração de main ou trabalho T032+.

## T032 — S5 integral e checkpoint C5 (2026-10-23)

Executada exclusivamente [T032](tasks.md), dependente de T028–T031, com leitura integral da tarefa, dependências, seleção/procedimentos e critérios C5, spec US5/FR-016–022, plano, pesquisa/modelo, contratos/precedência, quickstart e componentes pertinentes, sob constituição/AGENTS/harness. Conferências iniciais: `git rev-parse --show-toplevel` = `C:/Users/ronal/dscommerce-speckit`; `git branch --show-current` = `008-registrar-pagamento`; `git rev-parse HEAD` = `cf19e90747ad57565081fcc8b03b644dfdbe6fb1`; seleção local008. `git status --short --untracked-files=all` mostrou somente os nove modificados recebidos, nenhum staged/não rastreado; índice vazio por `git diff --cached --name-only`.

Caminhos completos conferidos sob `C:/Users/ronal/dscommerce-speckit/`: `specs/008-registrar-pagamento/quickstart.md`, `specs/008-registrar-pagamento/tasks.md`, `src/test/java/com/devsuperior/dscommerce/dto/OwnOrderQueryDtoContractTest.java`, `src/test/java/com/devsuperior/dscommerce/repositories/OwnOrderQueryRepositoryTest.java`, `src/test/java/com/devsuperior/dscommerce/services/OwnOrderQueryServiceTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryListHttpIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryDetailHttpIntegrationTest.java`, `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQuerySecurityHttpIntegrationTest.java` e `src/test/java/com/devsuperior/dscommerce/controllers/OwnOrderQueryRegressionHttpIntegrationTest.java`. Caminhos resolvidos por Join-Path/GetFullPath; os sete Java recebidos e classes Api foram preservados integralmente.

Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md16/16, zero aberto, somente leitura. `.gitignore` Java adequado; extensions.yml ausente antes/depois, sem hooks before/after_implement. Raiz/branch/HEAD/seleção preservados e main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`.

### Comando, isolamento dos relatórios e resultado atual

Uma única chamada Maven/Wrapper, sequencial, sem outro Maven/Surefire ativo na conferência anterior. Comando exato, encaminhado como array de argumentos pelo runner PowerShell:

~~~powershell
.\mvnw.cmd "-Dtest=OwnOrderQueryDtoContractTest,OwnOrderQueryRepositoryTest,OwnOrderQueryServiceTest,OwnOrderQueryList*HttpIntegrationTest,OwnOrderQueryDetail*HttpIntegrationTest,OwnOrderQuerySecurity*HttpIntegrationTest,OwnOrderQueryRegression*HttpIntegrationTest" "-Dsurefire.reportNameSuffix=S5-T032-110331965" test
~~~

Recibo ignorado `target/feature008-evidence/T032-20261023/`: hashes362 rastreados, cópias dos nove arquivos modificados,460 relatórios anteriores copiados em `surefire-before/` e inventário10365 arquivos das evidências anteriores. Runner verificou SHA256 de cada cópia Surefire antes da chamada. Relatórios anteriores preservados sem limpeza/overwrite; sufixo exclusivo. Diretório próprio `target/feature008-evidence/T032-20261023/S5-T032-110331965/` contém `run.json`, `console.log`, `exit.json`, `analysis.json`, `all-reports-after.json` e `surefire-reports/` com11 XML+11 TXT novos. Console original UTF-16 do redirecionamento PowerShell preservado, reconhecido pela análise sem regravação.

Início `2026-10-23T11:03:31.9808969-03:00`; término do runner `2026-10-23T11:08:48.3249369-03:00`; Maven terminou às11:08:47, duração05:10. **Exit0, BUILD SUCCESS:758 testes aprovados,0 falhas,0 erros,0 ignorados**, conforme console/Surefire. Java Zulu21.0.12.1/Azul, JVM em `C:/Program Files/Zulu/zulu-21`, os.name reportado `Windows 10`; propriedades XML confirmam `java.security.manager=allow`. Wrapper configurado para Maven3.9.16 nas propriedades preservadas, Boot3.3.4/Hibernate6.5.3.Final nos logs e H2 2.2.224 do ambiente aprovado. Compilação normal das148 fontes de teste com release21 não é contagem JUnit nem suíte completa. Não houve problema de compilação/preparação ou falha funcional; **nenhuma correção de código, fixture, observador ou asserção foi necessária**.

Não executados C4, BASE, suíte completa, FINAL, CI, comandos de validação OpenAPI adicionais ou T033+. Esta é uma execução S5 nova exigida pela autoria T031 posterior à S5/T030; não repetição sem mudança. A S5 anterior750 continua evidência histórica da revisão anterior do diff, no mesmo HEAD; não aprovou os oito casos novos. Nenhuma alteração ou interpretação retroativa de evidências anteriores.

### Seleção efetiva e ausência de omissões

| Classe concreta (prefixo OwnOrderQuery) | Contexto | Testes atuais | Métodos efetivos, incluindo herança |
| --- | --- | ---: | ---: |
| DtoContractTest | JsonTest, sem banco/MVC/Security | 130 | 22 |
| RepositoryTest | MOCK/JPA | 41 | 20 |
| ServiceTest | MOCK/JPA + cenários locais controlados | 91 | 29 |
| ListHttpIntegrationTest | ROOT | 116 | 12 |
| ListApiHttpIntegrationTest | /api | 116 | 12 |
| DetailHttpIntegrationTest | ROOT | 12 | 8 |
| DetailApiHttpIntegrationTest | /api | 12 | 8 |
| SecurityHttpIntegrationTest | ROOT | 82 | 9 |
| SecurityApiHttpIntegrationTest | /api | 82 | 9 |
| RegressionHttpIntegrationTest | ROOT | 38 | 9 |
| RegressionApiHttpIntegrationTest | /api | 38 | 9 |
| Total | 11 classes concretas | 758 | Parâmetros não são novos métodos de fonte |

Conferidos console, XML e TXT:11 classes esperadas, nenhuma extra/omitida; total de testcase igual ao total informado; zero par classe+nome duplicado. Conjuntos de métodos no XML iguais aos declarados/herdados, índices parametrizados contínuos e matrizes de contagens iguais entre ROOT/Api. Nenhuma classe @Nested/@Disabled nos componentes selecionados; PaidOrders, PaidRegression, ExternalAttemptProbe e PaidOwnOrderHttpJourney são auxiliares, sem testes aninhados descobertos a adicionar. Sem filtro de método, assumption ou failIfNoTests=false. Segurança inclui as3 rotinas herdadas do gate em cada contexto:40+8+12=60, além de19 casos de negócio antigos+3 novos, total82/contexto; as jornadas próprias exigem os status exatos previstos.

Verificadas novamente nesta execução as27 invocações T028 (DTO11/repository8/service8),14 T029 e agora8 T031. Total758 =709 anteriores+49 de US5; também corresponde a750 da seleção T030+8 recém-escritos. Quantidades por método antigo cruzadas com relatórios preservados somente para conferir ausência de perda, sem herdar aprovação de revisão passada. Os oito novos estão em ambos os XML de regressão, cada método uma vez/contexto, sem erro/falha/skip.

### Oito casos T031 e observação efetiva

| Método herdado em ROOT e /api | JUnit por contexto | POSTs de pagamento e janelas do observer, por contexto | GET/HEAD observados, por contexto |
| --- | ---: | ---: | ---: |
| regressionPost008RepeatedGetHeadSuccessEmptyAndErrorsKeepAllEightTables | 1 | 2 | 81 |
| regressionPost008PaymentRejectionsPreserveAllTablesAndTheOriginalSettlementWithoutExternalAttempts | 1 | 6 | 2 |
| regressionPost008PublicCatalogFilterPagesDetailsAndRealLoginRemainIndependentOfPaidQueries | 1 | 1 | 16 |
| regressionPost008MaintenanceAndRepeatedRegistrationsPreservePaidHistoryAndCreateNewWaitingOrders | 1 | 1 | 7 |
| Total por contexto | 4 | 10 | 106 |

Somando os dois contextos:8 invocações T031,20 tentativas de pagamento,20 janelas de rede e212 GET/HEAD novos. As26 combinações de readCases repetidas3 vezes no primeiro método são requests dentro de um @Test, não78 testes JUnit; suas81 leituras incluem ainda3 do ADMIN pagador. Os38 testes por classe de regressão são34 anteriores+4 novos. Marcadores e contagens foram extraídos do system-out **por testcase** nos XML e comparados ao console; a representação XML CDATA foi lida como texto, sem inferir ausência do observer de uma conversão genérica de nó do PowerShell.

T031_PAYMENT registrou10 sucessos201 (5/contexto) e10 rejeições:403x2,400x2,404x2,409x4.201 aprovou o oráculo completo das oito tabelas, com única alteração permitida Payment+status do alvo, e ocorrência preservada. Rejeições aprovaram oito tabelas inalteradas e SELECT-only no SQL Hibernate; repetição igual/diferente por outro ADMIN conservou a ocorrência original. Os212 GET/HEAD novos aprovaram o probe008 em torno do helper007 intacto; login/POST/manutenção/limpeza permaneceram fora dessas janelas de consulta. T029 manteve separadamente18 POST201 com prova independente e340 GET/HEAD em seus seis contextos de lista/detalhe/segurança. Não misturar esses números com758 testes ou contadores de commit.

O **observador funcionou nesta execução**:20 marcadores T031_NETWORK, cada um com jdkChecks=3 (>0), forbiddenAttempts=0 e controlsUseNetwork=false;60 verificações JDK reais no conjunto. Cada instalação executou antes da janela os controles diretos de DNS/CONNECT/HTTP_PERMISSION, exigindo detecção/bloqueio dos três tipos; zerou o estado antes do POST real. Esses controles não resolveram hosts, abriram sockets ou enviaram requests externos. A criação de um HttpClient novo com observer instalado e o POST loopback alcançaram os hooks efetivos. Construção, instalação, controles, envio, encerramento e restauração do manager passaram; disponibilidade do Java21 e `allow` foram preparação comprovada, não resultado de consulta. Zero hooks ou falha de restauração teriam falhado explicitamente, sem skip; isso não ocorreu.

As janelas armadas são do envio à leitura completa da resposta do POST, em todas as threads JVM alcançadas pelos hooks; somente loopback/porta do harness é autorizado. Controles de sensibilidade pertencem à preparação e não são tentativas financeiras observadas. Snapshot/JDBC independente posterior ao HTTP e janelas GET/HEAD não são contados como hooks ou falhas de rede. O sensor detecta/bloqueia tentativas não pertencentes ao harness no alcance JDK, não as classifica comercialmente como financeiras. Não houve solicitação financeira externa de controle. A inspeção dos caminhos produtivos feita em T031, byte-preservados nesta execução, complementa esse alcance: não há gateway/cobrança ou cliente financeiro na operação.

### Contextos efetivamente usados

| Componente/contexto | Porta HTTP | UUID H2 observado (prefixo jdbc:h2:mem:admin-product-test-) |
| --- | ---: | --- |
| detalhe/api | 55166 | 3890324e-2869-4b40-b26c-5fd1a142d5c2 |
| detalhe/ROOT | 55174 | 27669d45-0ed4-44ea-8c63-315effd20f69 |
| lista/api | 55181 | d8f2eeb1-b73d-455f-ad69-903fe3d6f09b |
| lista/ROOT | 55233 | a8aee724-6402-46a2-8277-026639b02e6a |
| regressão/api | 55285 | a7c14b58-42fd-493f-b3e3-e805efc0c1d3 |
| regressão/ROOT | 55332 | 85cbfa6a-4a75-4727-ab58-1f64d8cef86b |
| segurança/api | 55376 | 59350ad1-3c8d-418a-9e1e-a8e075debb5d |
| segurança/ROOT | 55402 | 4eb7765f-00b4-402e-96dd-4df25e8840a3 |
| repository/MOCK | — | 11b93361-2da5-458f-970a-3249c0373ada |
| service/MOCK | — | cebfe510-3a26-4bec-a235-cbde6090b5d4 |

São10 bancos/contextos de persistência exclusivos/estáveis mais o JsonTest sem banco. Novos HttpClient/observer não criaram contextos Spring adicionais. Asserções de isolamento, seed desativado, OSIVfalse, transação do chamador ausente e limpeza seletiva passaram; Payment foi removido antes dos pedidos próprios. Contadores históricos de regressão, agora incluindo os novos requests: por contexto42 logins preparatórios,264 queries,9 registros e25 requests de compatibilidade. Esses rótulos T028/T029 são históricos da007 e não IDs de tarefas008; logs completos e analysis.json separam-nos das janelas novas e invocações JUnit.

### Critérios US5 e C5 no ambiente observado

| Cenário | Resultado atual e prova principal |
| --- | --- |
| US5.1 — PAID e mesmas respostas | DTO130, repository41/service91 e jornadas T029/T031 aprovaram estado armazenado PAID, quatro campos de resumo/cinco de detalhe/cinco de item e dinheiro textual exato; nenhuma chave de pagamento acrescentada. |
| US5.2 — titularidade inclusive ADMIN pagador | Conteúdo/count/lote/detalhe continuam do mesmo titular. CLIENT/ADMIN/ambos leem seus PAID; pagador alheio tem lista/count próprios e404 igual ao ausente, sem privilégios novos. |
| US5.3 — paginação e criação/ID | Cinco compras2/2/1, totais5/3, empates por ID DESC e página além do fim preservados depois de quitar a antiga com ocorrência posterior à nova. Pagamento não substituiu createdAt nem ordenação. |
| US5.4 — leituras, histórico e erros | GET/HEAD bem-sucedidos/vazios/rejeitados, inclusive26 alvos repetidos após quitação, aprovaram ausência de DML e fotografias008 iguais; nomes atuais/ordem dos itens/histórico42.50,0.01 e9007199254740993.01 preservados. Headers/envelopes próprios e HEAD vazio mantidos. |
| US5.5 — jornadas001–007 | Catálogo/detalhes públicos, filtro/páginas1/1/0, login real por nome/senha e rejeições exatas; manutenção autorizada/validada, referenciados protegidos409; dois envios válidos idênticos geraram novas raízes WAITING_PAYMENT distintas, sem Payment novo e sem tocar PAID antigo. |
| US5.6 — efeitos restritos e exclusões | Sucesso/rejeições compararam oito tabelas; somente Payment+status do alvo mudaram no sucesso. Observer real com controles aprovados não detectou tentativas externas no alcance descrito, complementado pela leitura dos caminhos sem gateway. Carrinho permanece do consumidor: não foi criada tabela/rota nova nem se afirma observar sua memória remota. |

**C5 aprovado nesta execução758/758**, para esses cenários/contextos e limites. Não é resultado herdado dos750 anteriores, validação puramente documental, auditoria final ou entrega da feature. Nenhuma alteração produtiva ou de testes necessária; todos os sete Java recebidos, classes Api, fixtures/configurações/POM e contratos ficam byte-preservados. Somente esta seção e marcador T032 são mudanças rastreadas desta tarefa.

### Contrato alvo e referência preparada para publicação futura

O [snapshot completo de consultas008](contracts/own-orders-openapi.yaml), versão0.2.0, foi o alvo dos testes; admite WAITING_PAYMENT/PAID e mantém demais campos/regras. O teste textual do contrato no DTO faz parte das130 invocações JSON; não substitui validação estrutural OpenAPI completa nem prova sozinho endpoint. A execução JPA/serviço/HTTP atual fornece a evidência comportamental. Não foram executados novamente validadores documentais para arquivos normativos inalterados.

Conforme [precedência](contracts/README.md), o [contrato007](../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml) permanece vigente até entrega008 e depois histórico intacto; o snapshot008 o substituirá integralmente, sem contratos alternativos simultâneos. Referência preparada aqui para T035, após FINAL: apontar README/documentação vigente para `specs/008-registrar-pagamento/contracts/own-orders-openapi.yaml` como contrato completo das consultas e `specs/008-registrar-pagamento/contracts/payment-registration-openapi.yaml` para POST, usando `specs/008-registrar-pagamento/contracts/README.md` para explicar substituição/histórico. README atual não publica links de consultas007/008; nenhum apontamento ou metadado público foi alterado nesta tarefa. Não se anuncia vigência/entrega008 antes de T035 e FINAL.

### Limites, preservação e fechamento

Sem falha funcional, compilação/preparação impedida ou observador inoperante nesta seleção; não houve correção nem RED artificial/reexecução Maven para obter verde. Advertências de SecurityManager deprecated (fontes antigas/instalação do observer), H2Dialect, agente ByteBuddy/VM e logs de rejeições/violações referenciais esperadas não são falhas de teste; console integral preservado. O observer usa mecanismo deprecated restrito ao Java21 com allow; aprovação não promete funcionamento em outro JDK.

Observer não cobre chamadas nativas/processos externos, UDP/multicast, conexões preexistentes que contornem hooks ou trabalho assíncrono posterior à janela. Zero eventos no alcance observado não é cobertura física universal, prova de toda rede ou de outro ambiente. Fotografias compostas pressupõem fim dos escritores conhecidos, não snapshot atômico sob concorrência livre; StatementInspector não vê JDBC direto e igualdade final não exclui mutação transitória restaurada. Janelas GET/HEAD não abrangem preparação, login ou DML intencional de manutenção/registro/limpeza. Novos20 POSTs observados são distintos dos18 POSTs T029 sem esse observer; não estender o alcance do sensor a todos os requests da seleção.

PAID/Payment foram lidos independentemente após resposta/transação concluída; flush não foi tratado como commit. Nenhum failpoint ou falha física foi acionado por esta seleção. C4/US4 e seus limites de rollback/transporte continuam evidências anteriores, sem repetição nem extrapolação. Não foram provados crash recovery, resultado de commit desconhecido, durabilidade de produção, outro SGBD ou CI/Linux. Auditoria T033, FINAL T034 e publicação/fechamento T035–T036 continuam obrigatórios e abertos.

Auxiliares ignorados prepare.py/run.ps1/analyze.py/verify-prior.py/review.py, pelo Python externo quando aplicável (`C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T032-20261023/<auxiliar>.py`), preservam e reconciliam evidências. prepare.py exit0; analyze.py exit0 reconciliou console/XML/TXT, parâmetros/herança, oito casos T031 e marcadores por testcase; verify-prior.py exit0/PASS confirmou10365 arquivos anteriores idênticos. Análise estática de arquivos/relatórios foi repetida para conferir a captura de resultados, sem nova execução de testes ou regravação dos originais. Conferências Git, Get-Content/rg, `git diff --`/`git diff --stat`/`git diff --numstat` e `git diff --check` completam a revisão. Resultado efetivo de review.py é registrado abaixo após sua execução.

Critérios T032 atendidos: somente T032 passa a[X], T033–T036 permanecem abertas. Sem stage, commit, push, merge, alteração de branch/main ou trabalho T033+.

Revisão final executada: `review.py` exit0/PASS e `git diff --check` exit0. Confirmados 360 rastreados fora destes dois documentos idênticos ao recebimento, incluindo os sete testes Java e classes Api; prefixo de 457302 bytes do quickstart recebido intacto; única mudança adicional em tasks.md é T032 marcada. Conferidos 183 links locais existentes, diff, referências, comandos, arquivos novos (nenhum não rastreado) e whitespace. Permanecem 482 relatórios Surefire (460 anteriores intactos + 22 próprios desta execução), 10365 arquivos de evidência anterior preservados e os mesmos nove arquivos modificados no Git. Índice vazio; nenhum stage/commit/push ou alteração de main. Resultado guardado em `target/feature008-evidence/T032-20261023/final-review.json`; sem impedimentos para T032 e sem executar os fechamentos T033–T036.


## T033 — auditoria conjunta e retomada após interrupção (2026-10-23)

Executada exclusivamente [T033](tasks.md), dependente de T032/C5. Conferências iniciais por `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD`, `git status --short --untracked-files=all` e `git diff --cached --name-only`: raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd`, Git limpo e índice vazio. Seleção local `.specify/feature.json` aponta a008; SHA256 `ff185e0ff10d010a4e45b56a3666c1829f365ef09898107aa374d6cac34e4bf4`. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`.

Lidos tarefa, dependências e procedimentos, [spec](spec.md), [plano](plan.md), [pesquisa](research.md), [modelo](data-model.md), os dois contratos e sua [precedência](contracts/README.md), matriz de tasks e procedimentos/evidências deste quickstart, sob [constituição](../../.specify/memory/constitution.md), [AGENTS](../../AGENTS.md) e [harness](../../docs/harness.md). Pré-requisitos `.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks`: exit0, FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md:16/16, zero aberto, somente leitura. Ignore Java adequado; nenhum outro stack exige ignore novo. `.specify/extensions.yml` ausente antes/depois, sem hooks before/after_implement.

**Esta T033 é auditoria por leitura de código e relatórios existentes.** FINAL está expressamente atribuído à T034; não houve chamada Maven/Wrapper, javac, descoberta JUnit, servidor, request HTTP, baseline, checkpoint repetido, validador OpenAPI ou trabalho T034+. Nesta retomada, a consulta de processos Win32_Process não encontrou Maven/Java/Surefire ativo; nenhuma execução precisou ser acompanhada ou encerrada. Os resultados abaixo são históricos identificados, reconciliados agora; não são testes recém-executados nem aprovação da revisão atual por uma suíte completa.

### Estado recuperado e trabalho desta retomada

A interrupção por limite de uso ocorreu durante a auditoria documental: `T033-AUDIT-20261023/` contém initial.json, inventários, evidence.py, evidence-reconciliation.json, methods.txt e appendix.md, mas nenhum run/console/exit de Maven da T033 nem review.py concluído. O appendix.md era rascunho, não conclusão publicada: quickstart ainda terminava em T032 e T033 estava aberta. Todos esses arquivos foram preservados; esta seção aproveita o rascunho somente depois de conferir código, requisitos e relatórios novamente.

O inventário da retomada não encontrou mudanças rastreadas, staged ou não rastreadas desde HEAD96126db; os362 arquivos rastreados conferem byte a byte com tracked.json da auditoria interrompida. Também conferem os482 relatórios Surefire e10876 arquivos de evidências anteriores dos inventários originais. O novo recibo ignorado é `target/feature008-evidence/T033-RESUME-20261023/`; os auxiliares novos produzem seus próprios relatórios e não sobrescrevem os anteriores.

Foram examinados24 recibos run.json da008 por identificação, comando, seletor/argumentos e relatórios próprios: nenhum corresponde a FINAL sem filtro. Há109 XML históricos com sufixo `t030-final-green-20261021` em target/surefire-reports e cópias de evidência:3991/0/0/0, da Feature007. Sua origem está registrada no [quickstart007](../007-consultar-meus-pedidos/quickstart.md): comando `mvnw.cmd '-Dsurefire.reportNameSuffix=t030-final-green-20261021' test`, revisão fb7b77625e44fe5832d73bb87f713a77840d3e1e mais diff depois incorporado em a9ccf94. Não correspondem ao código008 atual nem substituem FINAL/T034. Nenhuma soma dos checkpoints foi tratada como suíte completa.

A execução mais recente aplicável é C5/S5-T032-110331965, iniciada às11:03:31.9808969-03:00 e encerrada pelo runner às11:08:48.3249369-03:00: seletor explícito,22 relatórios próprios,758/0/0/0, BUILD SUCCESS e exitCode0 em exit.json. O manifesto C5 coincide com o código/build e os testes atuais; nenhum arquivo de src tem LastWriteTime posterior ao início dessa execução. Esse cruzamento com hashes/histórico não indica mudança de código/testes durante ou após a execução. Nas provas C1–C4, as sete extensões OwnOrderQuery posteriores são identificadas separadamente abaixo e aprovadas pela execução C5, sem retroagir sua aprovação. O commit96126db incorporou o estado testado de US5.

Nesta retomada foram executados somente leitura/reconciliação estática, comparação de hashes, revisão de fontes/documentos e conferências Git. recover2.py terminou com exit0; o leitor recuperado conferiu console completo, comandos, seletores, XML/TXT próprios, totais, conclusão e exit disponível das sete execuções abaixo. Não houve Maven, novo teste, correção de código/asserção, execução funcional interrompida a repetir ou tentativa de reconstruir documentação por testes. A interrupção recuperada é da auditoria; os limites e resultados parciais históricos continuam preservados em suas seções. FINAL e T034–T036 permanecem pendentes.

### Revisões e execuções existentes conciliadas

Recibo ignorado `target/feature008-evidence/T033-AUDIT-20261023/`: o inventário recuperado de prepare.py contém362 rastreados,482 arquivos Surefire e10876 arquivos anteriores de evidência, com hashes e cópias dos dois documentos recebidos. `initial.json` registra coleta às `2026-10-23T11:56:07.863833-03:00`. Nenhum relatório foi limpo, sobrescrito ou regenerado.

| Referência desta auditoria | Diretório de execução anterior, relativo a target/feature008-evidence | HEAD do run (inclui diff então registrado) | XML/TXT próprios | Resultado anterior conferido agora |
| --- | --- | --- | ---: | --- |
| C1/T013 | S1-T013-GREEN-20261022T081608099 | 3d8d1a4a99cfa3ea28e9aa7acff19e1d0008c279 | 11+11 | 371/0/0/0, exit0 |
| C2/T018 | S2-T018-20261022T162604150 | 2f053fef08725da34ed04f28d77b2fe52b90cec4 | 17+17 | 839/0/0/0, exit0 |
| C3/T022 | S3-T022-20261022T172253803 | 1b49dc1b61f27ccecbe7aa561dfc56fc4da93125 | 5+5 | 37/0/0/0, exit0 |
| Diagnóstico T026 | T026-DIAG-091925077 | 6111c6ba04b9f642bb2963f81c2b7bf333e4eb40 | 4+4 | 32/0/0/0, exit0 |
| C4/T027 | S4-T027-093159353 | 6111c6ba04b9f642bb2963f81c2b7bf333e4eb40 | 7+7 | 77/0/0/0, exit0 |
| S5 anterior/T030 | T030-20261023/S5-T030-102637807 | cf19e90747ad57565081fcc8b03b644dfdbe6fb1 | 11+11 | 750/0/0/0, exit0 |
| C5/T032 | T032-20261023/S5-T032-110331965 | cf19e90747ad57565081fcc8b03b644dfdbe6fb1 | 11+11 | 758/0/0/0, exit0 |

Notação resultado: testes/falhas/erros/ignorados, sem somar execuções que se sobrepõem. `run.json` conserva comandos Wrapper, seletores, horários e revisão; `exit.json`, console e XML/TXT concordam. Comandos originais permanecem nas respectivas seções T013/T018/T022/T026/T027/T030/T032, sem reescrita. Na leitura atual, selecionou-se exclusivamente o sufixo de cada run, inclusive nos arquivos cujo classname recebe esse sufixo. Os371 do núcleo aparecem em C2; T02632 não é C477; S5/T030750 não aprova os oito casos escritos posteriormente em T031, efetivamente aprovados em C5758.

O leitor evidence-recovered.py, executado por recover2.py com exit0 nesta retomada, reconciliou essas sete execuções: quantidades de testcase iguais aos resumos console/XML/TXT, nenhum failure/error/skipped, identidades únicas dentro de cada execução, classes concretas selecionadas iguais às fontes alcançadas pelos seletores e matrizes ROOT/Api iguais onde previstas. Cada método observado existe nas fontes; a conferência de métodos/providers e índices dos recibos originais complementa esse inventário, sem tratar contagem de compilação como execução. `evidence-reconciliation.json` e `methods.txt` guardam classes/métodos/multiplicidades e metadados completos. A análise estática não executou os métodos.

Hashes produtivos/build comparados com os manifestos dos runs: C1 possui21 arquivos inventariados desse recorte, todos idênticos; C2/C3/C4/C5 possuem106 cada, todos idênticos, incluindo src/main, POM e Wrapper quando presentes. Não se atribui ao manifesto C1 cobertura de arquivos que ele não inventariou. Nenhum teste inventariado do C1 mudou; nos manifestos C2–C4, somente os sete OwnOrderQuery de US5 mudaram depois desses checkpoints, fora das seleções de pagamento então executadas. O manifesto C5 já contém os sete testes atuais, todos idênticos. O commit96126db registra o checkpoint US5 sem alterar seu conteúdo testado; mudança de SHA não foi apresentada como nova execução.

### Classes, herança, aninhados e contextos

| Grupo de classes (nomes sem prefixo de pacote) | Quantidade efetiva e métodos | Contexto e alcance |
| --- | --- | --- |
| PaymentRegistrationHttpIntegrationTest / PaymentRegistrationApiHttpIntegrationTest | 16 cada;11 métodos herdados | HTTP ROOT e /api, serviço/proxy/JPA reais; C1/C2/C4. Os seis métodos US1 produzem8 invocações por contexto, as cinco guardas/repetição outras8. |
| PaymentRegistrationEndpointHttpTest / PaymentRegistrationEndpointApiHttpTest | 48 cada;17 métodos herdados | HTTP ROOT e /api, segurança/MVC reais e registro simulado; C1/C2. Não provam persistência por si. |
| PaymentRegistrationDtoContractTest | 141;11 métodos | JSON/Bean Validation local, sem banco/MVC; C1/C2. |
| PaymentRegistrationServiceTest | container0; CurrentAccess18 + RegistrationRules51 + TransactionAndCategories8 =77 | Três @Nested reais, regras/interações locais com mocks; C1/C2. Container não é contado novamente. |
| PaymentRegistrationDatabaseProbeTest / PaymentPersistenceIntegrationTest | 12/13;6/10 métodos | Probe de oito tabelas, sensibilidade/limpeza e schema/JDBC H2; C1/C2; persistência também C3. |
| PaymentAuthorizationHttpIntegrationTest / Api | 40 cada;10 herdados | HTTP ROOT e /api, perfis atuais/claims/revogação/lookup; C2. |
| PaymentInputHttpIntegrationTest / Api | 129 cada;9 herdados | HTTP ROOT e /api; documentos39, ocorrências inválidas48, válidas24 e outros18 por contexto; C2. |
| PaymentBoundaryValidationTest | 122;10 métodos | MVC local e serviço mock, reader/Bean Validation/advice reais; C2. |
| PaymentTemporalAndCurrentRolesIntegrationTest | 8;2 métodos (6 temporais+2 revogações) | HTTP ROOT, espera H2 observada; C2/C3. Sem variante /api. |
| PaymentConcurrencyIntegrationTest | 13;4 métodos (4+4+4+1) | HTTP ROOT, disputa/repetição e duas raízes sobrepostas; C3/C4. |
| PaymentDistinctOrdersIntegrationTest / OrderPaymentLockRepositoryTest | 2/1;1 método cada | ROOT enquanto outra raiz está BLOCKED; repository/JPA com contexto obsoleto; C3. |
| PaymentRollbackIntegrationTest / PaymentRollbackApiHttpIntegrationTest | 14 cada;5 herdados (6+1+1+5+1) | HTTP ROOT e /api com transação/driver reais e falhas de teste scoped; T026/C4. |
| PaymentResponseDeliveryIntegrationTest / PaymentResponseTransportHttpIntegrationTest | 2 cada;1 método parametrizado FORWARD/WITHHOLD | Somente ROOT: buffer MockMvc e socket/proxy HTTP local respectivamente; T026/C4. Recuperação por HTTP real/JDBC. |
| OwnOrderQueryDtoContractTest / RepositoryTest / ServiceTest | 130/41/91;22/20/29 métodos | JSON sem banco; JPA/MOCK de repository; serviço/JPA e cenários controlados; C5. |
| OwnOrderQueryListHttpIntegrationTest / ListApiHttpIntegrationTest | 116 cada;12 herdados | HTTP ROOT e /api; C5. |
| OwnOrderQueryDetailHttpIntegrationTest / DetailApiHttpIntegrationTest | 12 cada;8 herdados | HTTP ROOT e /api; C5. |
| OwnOrderQuerySecurityHttpIntegrationTest / SecurityApiHttpIntegrationTest | 82 cada;9 efetivos | HTTP ROOT e /api; inclui os3 métodos de autenticação herdados (60 casos) +19 antigos de negócio+3 novos; C5. |
| OwnOrderQueryRegressionHttpIntegrationTest / RegressionApiHttpIntegrationTest | 38 cada;9 herdados | HTTP ROOT e /api;34 antigos+4 novos em cada; C5. |

C3 tem cinco classes/18 grupos método-classe; C4 sete classes/38 grupos; C5 onze classes. Helpers/records/configurações privados e PaidOrders/PaidRegression/ExternalAttemptProbe não são testes @Nested adicionais. Nos grupos selecionados, somente o serviço possui os três @Nested JUnit acima; as outras estruturas aninhadas não acrescentam casos. As fontes/recibos não apresentam exclusão/Disabled/assumption para obter os resultados. Classes Api e todos os providers/asserções foram preservados.

Contextos foram conciliados com os marcadores/URLs/portas dos consoles originais, não inferidos do nome do filtro. C1 usa seis bancos JPA isolados, além de JSON/unitários e controle vazio de probe; ROOT real59683 e /api59627. C2 acrescenta autorização/entrada ROOT/Api e temporal ROOT, mantendo MVC local separado. C3 usa três bancos (HTTP ROOT53537 compartilhado entre as jornadas compatíveis, repository e persistência); não tem HTTP /api. C4 reutiliza registro ROOT/concorrência na porta54501 e possui outros cinco contextos próprios: registro/api54506, entrega/buffer54517, socket54520, rollback/api54528 e rollback/ROOT54544. C5 usa oito contextos HTTP ROOT/Api (lista, detalhe, segurança, regressão) e dois MOCK/JPA, dez H2 distintos mais JsonTest; UUIDs/portas completos permanecem na T032. URL estável e distinta por contexto com create-drop, sem alterar seed, perfil ou cache global. Instanciar HttpClient não cria contexto Spring novo.

### Reconciliação dos 30 cenários

Referências abreviadas nesta matriz: R = PaymentRegistrationHttpIntegrationTest e Api; A = PaymentAuthorizationHttpIntegrationTest e Api; I = PaymentInputHttpIntegrationTest e Api; T = PaymentTemporalAndCurrentRolesIntegrationTest; Q = variantes OwnOrderQuery de C5. Os nomes abaixo são métodos reais conferidos no inventário, com as multiplicidades da tabela anterior; não são novas invocações. Estados aprovados limitam-se às execuções/contextos identificados.

| Cenário | Classe/método principal e prova conciliada |
| --- | --- |
| US1.1 | R.us11AdminSettlesForeignOrderWithExactlyThreeConfirmationFieldsAndHistorical4250, uma vez/contexto;201 fechado,42.50, Payment/PAID independentes; C1/C2/C4. |
| US1.2 | R.us12OwnAdminWithoutClientAndBothProfilesUseTheSameSettlementRules,3/contexto, e A.currentAdminAloneOrWithClientSettlesOwnAndForeignOrders,4/contexto; ADMIN isolado/ambos, próprio/alheio; C1/C2/C4. |
| US1.3 | R.us13CatalogPriceTwelveDoesNotReplaceHistoricalTwentyOrOrderData; histórico20.00 versus catálogo12.00, comprador/criação/itens intactos; C1/C2/C4. |
| US1.4 | R.us14EquivalentOffsetWithExtraZerosPreservesOccurrenceRatherThanBusinessReference; Instant exato/UTC.SSS; DTO141 e persistência13 complementam; C1/C2/C4. |
| US1.5 | R.us15OccurrenceEqualToCreationIsAccepted e T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait/LOWER_EQUAL; limite inclusivo; C2/C3/C4. |
| US1.6 | R.us16OccurrenceEqualToInitialReferenceSurvivesClockAdvanceBeforeCompletion e T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait/UPPER_EQUAL; distinguir avanço coordenado de Clock em R de espera física em T; C2/C3/C4. |
| US2.1 | A.missingInvalidExpiredAndRemovedAccountCredentialsAre401WithoutRestrictedReads (5/contexto) e accountRemovedBetweenFilterAndServiceBecomes401WithoutExposingThePurchase;401 Bearer, sem leitura restrita; C2. |
| US2.2 | A.clientAndNoProfileCannotSettleEvenTheirOwnPurchase (4/contexto);403 sem dados/mutação; C2. |
| US2.3 | A.revocationAfterIssuanceDefeatsTheSameSignedAdminClaims, currentGrantAllowsTheSamePreviouslyDeniedCredentialWithoutReissuingIt, revocationBetweenFilterAndServiceIsRereadBeforeAnyOrderLookup e signedRoleNameAndAuthorityClaimsCannotGrantOrRemoveDatabasePermission; T.scalarGuardAfterTheWaitRejectsRevocationDespiteInitializedManagedRoles (ADMIN/BOTH), após espera física; C2/C3. |
| US2.4 | I.absentPositiveIdentityIsBusiness404WithoutCreatingOrChangingAnyRow; ID positivo inexistente, não404 por rota omitida; C2. |
| US2.5 | I.rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness (39/contexto), rejectsEveryInvalidR3GrammarCalendarRangeAndPrecisionBeforeBusiness (48/contexto) e selectedPaymentRouteRejectsNonpositiveMalformedOrOutOfRangeIds (7/contexto); MVC122 confirma @Valid/@NotNull/@Positive antes do serviço; C2. |
| US2.6 | T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait/BEFORE_CREATION e R.basicWindowRejectsInstantsOutsideInclusiveBoundsWithoutChangingHistoricalTables;400 sem correção; C2/C3/C4. |
| US2.7 | T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait/AFTER_REFERENCE e NATIVE_CEILING; referência nativa conservada durante espera/avanço,400; C2/C3. |
| US2.8 | I.rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness e everyQueryParameterIsInvalidAndExcludedFromTheSafePath; só ID no path e occurredAt; C2. |
| US2.9 | I.paidWithoutPaymentIsIncompatibleState409AndIsNeverAutomaticallyRepaired;409 próprio, sem reparo; C2. |
| US3.1 | PaymentConcurrencyIntegrationTest.sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal,4 variantes igual/diferente e mesmo/outro ADMIN;409 e original intacto; C3/C4. |
| US3.2 | Mesmo método anterior, variantes de outra ocorrência/outro ADMIN; nenhuma confirmação reutilizada; C3/C4. |
| US3.3 | PaymentConcurrencyIntegrationTest.existingPaymentPrecedesWaitingStateWithoutRepair,4 variantes; inconsistente WAITING_PAYMENT+Payment deliberado somente no setup, rejeição sem reparo; C3/C4. |
| US3.4 | PaymentConcurrencyIntegrationTest.overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment,4 variantes; BLOCKED/BLOCKER_ID antes/depois de flush,201+409, ocorrência vencedora; C3/C4. |
| US3.5 | validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions e PaymentDistinctOrdersIntegrationTest.distinctOrderCommitsWhileTheOtherStillWaitsForItsRootLock,2 variantes; ambas201, outra raiz ainda bloqueada, vínculos próprios; C3. |
| US4.1 | Rollback ROOT/Api.reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows,6/contexto, mais confirmationConstructorFailureAfterRealFlushRollsBackPaymentAndPaidTogether;500/rollback/snapshots independentes; C4. |
| US4.2 | Rollback ROOT/Api.realDatabaseConstraintRejectsPaymentInsertWithoutBeingClassifiedAsRepeat e os6 Points; primeiro DML realmente executado, flush distinto de commit e conclusão; C4. |
| US4.3 | PaymentResponseDeliveryIntegrationTest.bufferedConfirmationIsDeliveredOrPreventedOnlyAfterIndependentCommitProof e PaymentResponseTransportHttpIntegrationTest.proxyReceivesCommittedConfirmationThenForwardsOrClosesWithoutDeliveringIt; FORWARD/WITHHOLD em ROOT, commit e JDBC antes de bloquear entrega; novo409 e GET PAID; Q/C5 complementa leitura contratual. |
| US4.4 | A.controlledRepositoryLookupFailuresStay500RatherThan401OrBusinessConflicts (4/contexto) e Rollback.internalFailureIsNotAuthenticationNotFoundOrRepeatAndLeavesNoCommittedMutation (5/contexto);500 seguro, causas distintas; C2/C4. |
| US5.1 | Q.listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount e detailPost008RealPaymentKeepsCurrentNamesRecordedItemsAndExactMoney, mais DTO/repository/service PAID; C5. |
| US5.2 | Q.ownershipPost008AdminPaymentDoesNotGrantForeignQueriesOrChangeGetHeadErrors,3/contexto; conteúdo/count/detalhe próprios, ADMIN pagador404 igual ao ausente; C5. |
| US5.3 | Q.listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount e variantes paidListStillPagesTwoTwoOneByCreationAndIdRatherThanPaymentOccurrence / paidRealListAndDetailKeepCreationOrderInsteadOfPaymentOccurrence;2/2/1 por criação/ID, não ocorrência; C5. |
| US5.4 | Q.regressionPost008RepeatedGetHeadSuccessEmptyAndErrorsKeepAllEightTables,1/contexto, e matrizes lista/detalhe/segurança; leituras repetidas/snapshots008/SQL, dinheiro e campos; C5. |
| US5.5 | Q.regressionPost008PublicCatalogFilterPagesDetailsAndRealLoginRemainIndependentOfPaidQueries e regressionPost008MaintenanceAndRepeatedRegistrationsPreservePaidHistoryAndCreateNewWaitingOrders;1 de cada/contexto; catálogo/login/manutenção/registro preservados; C5. Regressão integral continua T034. |
| US5.6 | Q.regressionPost008PaymentRejectionsPreserveAllTablesAndTheOriginalSettlementWithoutExternalAttempts,1/contexto, e20 janelas dos quatro métodos T031; oito tabelas/somente alvo e observer JDK; C5. Carrinho do consumidor não é observado pelo banco; exclusões também verificadas no código. |

### Reconciliação dos22 FR e oito SC

| FR | Código efetivo e evidência aplicável, com limites anteriores |
| --- | --- |
| FR-001 | SecurityConfig/PaymentAccessService, projeção escalar atual; US2.1–3/C2 e espera T/C3. |
| FR-002 | Guarda de escrita sem titular e guarda007 própria; US1.1–2/C1/C2/C4 e US5.2/C5. |
| FR-003 | DTO/reader exclusivos e path/query no controller; US2.5/8/C2, DTO141 e MVC122. |
| FR-004 | validateFullHistoricalAmount com BigDecimal/UNNECESSARY/19,2; projeção escalar histórica sem Product; US1.1/3 e serviço RegistrationRules/C2. Não existe amount persistido/selecionável. |
| FR-005 | Lock/refresh, Payment anterior antes do estado, sem reparo; US2.4/9 e US3.3/C2/C3. |
| FR-006 | Primeira instrução clock.instant(), referência local única nativa; reader estrito e janela inclusiva no serviço; US1.4–6/US2.5–7, DTO141, T8/C2/C3. |
| FR-007 | PaymentConfirmationDTO de três campos e controller201 após proxy; R/C1/C2/C4 e fronteira96/C2. |
| FR-008 | REQUIRED/READ_COMMITTED/rollbackFor Exception, persist+markPaid+flush+DTO, proxy dono/commit físico observado; rollback/controles C4. |
| FR-009 | Somente markPaid/Payment, snapshots integrais de pedido/itens e sete tabelas opacas; US1.3/C1/C4 e US5.4/C5. |
| FR-010 | Lookup existente409, sem save/merge/update de Payment; US3.1–3/C3/C4 e pós-entrega US4.3. |
| FR-011 | PESSIMISTIC_WRITE/find+refresh por raiz e PK final; US3.4–5/C3/C4, sem mutex global ou SLA. |
| FR-012 | Rejeições anteriores ao DML e snapshots de oito tabelas; C2/C3/C4/C5, setups externos explicitamente separados. |
| FR-013 | Exceção propaga, advice seguro e rollback C4; SQL23513 real no H2, falhas sintéticas anteriores ao driver e construtor real diferenciados. |
| FR-014 | Sem compensação/retry; buffer/socket ROOT C4 e leitura C5; resultado de commit indeterminado fora da garantia de rollback. |
| FR-015 | Reason fixa, advice scoped e handlers de segurança; C2/C4;401 Bearer,403/404/409/500 distintos, sem diagnóstico/segredo no wire. |
| FR-016 | Status persistido projetado em lista/detalhe, variantes PAID C5; snapshot008 alvo, publicação vigente condicionada a T035. |
| FR-017 | DTOs007 inalterados, JSON fechado4/5/5 sem Payment; C5. |
| FR-018 | Mesmo owner em conteúdo/count/lote/detalhe; ADMIN pagador não ganha leitura alheia,404 seguro; C5. |
| FR-019 | ORDER BY criação DESC/ID DESC e itens position ASC, histórico textual19,2/nome atual; C5. |
| FR-020 | readOnly, SQL observado e snapshots008, somente alvo no sucesso; C1–C5. Carrinho externo ao backend permanece fora da fotografia. |
| FR-021 | Jornadas pós-quitação C5 e fronteiras vizinhas C2 preservadas; criação WAITING_PAYMENT e novo pedido por envio válido. Suíte integral001–007 permanece T034. |
| FR-022 | Caminho produtivo sem cliente/gateway/cobrança; observador JDK nas20 janelas C5, limitado como descrito abaixo; sem chamada financeira externa para testar a exclusão. |

| SC | Estado da auditoria e fronteira de conclusão |
| --- | --- |
| SC-001 | Casos válidos US1 reconciliados em C1/C2/C4; não é nova execução nem entrega008. |
| SC-002 | Matriz de credenciais/perfis atuais e revogações C2, complementada pela espera C3; sem serializar revogação depois da última leitura. |
| SC-003 | C1+C2 e T8/C3 conferem referência única/precisão/limites; ocorrência não foi substituída por recebimento/commit. |
| SC-004 | C3+C4: repetições, concorrência e nova tentativa após entrega impedida; pares válidos separados de falhas operacionais. |
| SC-005 | C2+C3+C4 distinguem rejeição, rollback pré-commit e preservação pós-commit; não garantem rollback de resultado indeterminado. |
| SC-006 | C1+C5 conferem42.50/histórico20.00 versus catálogo12.00, criação/comprador/itens preservados. |
| SC-007 | C5 cobre US5.1–4 nos contextos observados; vigência documental pós-entrega ainda depende de T035. |
| SC-008 | C5 fornece prova focal de US5.5–6; fechamento integral **pendente de FINAL/T034**. Não inferir todos os testes001–007 pela aprovação focal. |

### Gates da008 pelo código e pela prova

| Gate | Resultado desta auditoria, restrito à008 | Fundamento efetivo |
| --- | --- | --- |
| I — Java/Spring Boot | Conforme no recorte auditado | Java21/Boot3.3.4, MVC/Security/Validation/Data JPA/Jackson do POM preservado, Wrapper3.9.16; ambientes dos XML/console identificados. |
| II — camadas | Conforme | AdminPaymentController trata binding/query/HTTP; PaymentRegistrationService decide conta, referência, elegibilidade, dinheiro e transação; fragments fazem find/refresh/persist e projeções de persistência. Sem regra financeira/controller ou DTO público mapeado no repository. |
| III — DTO | Conforme | PaymentRegistrationRequestDTO/PaymentConfirmationDTO específicos, mapeamento no serviço; nenhum endpoint retorna entidade. DTOs de consultas conservam quatro/cinco campos e itens cinco; C2/C5. |
| IV — JPA/Bean Validation | Conforme estrito **da008**, no alcance revisado | @Valid/@NotNull/@Positive na fronteira MVC antes do serviço; reader/constraints locais sem serviço/repository/Clock/IO de domínio. PaymentBoundaryValidationTest.controllerAnnotationsAndPureLocalParsingAndValidationNeedNoDomainIo e actualValidAndNotNullRejectMissingOccurrenceBeyondSuccessfulDeserialization passaram em C2; contexto local não possui banco, EntityManagerFactory ou Repository e exige serviço sem interações nas negativas. Mapeamento JPA/precisão/constraints reais em C1/C3 e proxy/commit em C4. |
| V — REST/erros | Conforme | Advice e handlers centralizados/scoped, categorias fixas e headers, sem catch/continuação de transação falha; método/negociação405/406/415 nativos preservados; C2/C4. |
| Requisitos, abstrações e governança | Conforme no recorte auditado | Spec/desenho anteriores ao código; fragments, projeção, guarda e probes têm necessidade concreta; nenhuma integração/fluxo excluído acrescentado. Matriz reconciliada sem reescrever requisitos. |
| Verificações pertinentes | Suficientes para esta auditoria; fechamento ainda aberto | Checkpoints identificados e preservados; FINAL/T034, publicação T035 e revisão T036 continuam obrigatórios. Não registrar PASS funcional global por esta tabela. |

O **Gate IV005 permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito**, com o fundamento FR-012/US2.6 de diagnóstico em serviços durante isValid; seu plano, constituição e evidências não mudaram. A008 não chama esse diagnóstico nem redefine serviço como somente escrita. Consulta de conta/perfil na segurança antes de MVC é infraestrutura de autorização, não IO dentro do reader/validator. Janela dependente da compra é decisão posterior do serviço, distinta de validação local. No flush, a cascata JPA @Valid do agregado pode provocar leitura dos itens já observada em C1; isso não foi apresentado como consulta de domínio durante validação de entrada nem como ausência universal de IO em toda validação persistente.

Schema e SQL foram revistos contra fontes e provas: Payment @MapsId/order_id PK+FK fk_payment_order, ocorrido TIMESTAMP(3) WITH TIME ZONE/NOT NULL, sem ID independente/amount/cascade REMOVE; Order conserva quatro colunas e criação WAITING_PAYMENT. Persist explícito, nunca merge/save; lock/refresh só da raiz por ID, sem estado/titular/coleção/Product na aquisição. Projeção de itens lê FK/posição/quantidade/preço histórico, sem preço atual. BigDecimal exato valida soma/subtotal/total decimal19,2 sem double/arredondamento. PK/FK/NOT NULL não impõem equivalência universal PAID/Payment ou janela temporal entre tabelas; essas garantias pertencem ao serviço transacional.

### Diagnósticos, alcance e pendências preservadas

Nenhuma lacuna funcional nova, falha de compilação/preparação ou necessidade de correção produtiva/de teste foi identificada nesta auditoria. Não foi executado caso novo para obter RED/GREEN artificial. O histórico continua distinguindo: T006 operação ausente/envelope incompatível; T007 schema/modelo ausentes por asserções funcionais (não bootstrap); T011 primeira coleta interrompida por stderr/sem exit confiável versus RED posterior completo77/35 falhas/exit1; T01725 falhas IntNode/LongNode em oráculos, com correção autorizada preservando número inteiro/árvore fechada e posteriores focais/C2 verdes. Não se reclassificaram essas falhas antigas como aprovações ou causas produtivas.

Evolução intencional: contrato007 fechado somente WAITING_PAYMENT permanece histórico/vigente até entrega; testes de criação continuam exigindo WAITING_PAYMENT. Novas variantes PAID usam o snapshot0080.2.0 completo, sem novos campos/privilégios; o POST continua0.1.0. `git diff main HEAD --` nos artefatos007, plano005/constituição, POM/Wrapper/perfis globais e H2ConsoleSecurityIntegrationTest/JwtTokenServiceTest retornou vazio. Não foram editados metadados planned, README ou referências públicas; ajustes de vigência são T035 após FINAL.

Achado de cobertura a levar à **T034**, sem alterar fonte nesta T033: AdminProductDeletionIntegrationTest.orderReferences usa Arrays.stream(OrderStatus.values()), dois contextos e quantidades1/2. Agora enumera8 variantes (quatro WAITING_PAYMENT e quatro PAID), embora seu comentário histórico diga que só existe WAITING_PAYMENT. O provider e as asserções de proteção/histórico estão corretos para enumerar os estados; não existe asserção exigindo enum unitário a remover. C5 não selecionou essa classe, portanto os quatro casos PAID ali gerados **não estão aprovados por C5**. É cobertura de regressão pendente da suíte FINAL, não falha funcional observada. OrderRepositoryTest conserva expectativas de criação e metadados STRING/quatro colunas, sem obrigar enum unitário; não há motivo demonstrado para enfraquecê-las. T034 deve conferir a descoberta integral e eventuais novas multiplicidades, sem fixar o total da suíte pela soma dos focais.

C4 diferencia estágio real de falha: SQL_BEFORE_FIRST_DML é SQLException sintética antes do execute; AFTER_FIRST_DML é posterior a execute real de uma linha; AFTER_FLUSH/AFTER_BODY_DTO e BEFORE_COMMIT são anteriores à efetivação; JDBC_COMMIT_BEFORE_DELEGATE registra tentativa mas zero delegação. CHECK temporário23513 é rejeição real do H2; construtor PaymentConfirmationDTO falhou de fato após corrupção de teste+1ns. Injeções locais de lookup/lock/persist/flush não simulam defeito físico universal. Controles positivos registraram delegação/sucesso JDBC, afterCommit/completion e leitura independente. Buffer MockMvc e socket/proxy ROOT retiveram69 bytes JSON, e no socket389 bytes wire foram recebidos a montante/zero entregues em WITHHOLD; novo409 e Payment/PAID preservados. Nenhuma prova de pane física, crash recovery ou commit delegado de resultado desconhecido foi fabricada. Flush/DTO não equivalem a commit;500/ausência de resposta não demonstram ausência da quitação.

C5 confirmou os oito casos novos T031: quatro métodos herdados em ROOT e /api, uma vez em cada, sem omissões. São20 POSTs/janelas com60 hooks JDK reais, forbiddenAttempts=0; controles diretos DNS/CONNECT/HTTP_PERMISSION ocorreram antes da janela e zeraram contadores, sem DNS/socket/request externo de controle. Zero hooks/instalação/restauração defeituosa falhariam sem skip. A janela cobre envio à resposta completa com SecurityManager Java21/allow, incluindo hooks alcançados pelos workers; não cobre native/processos, UDP/multicast, conexões que contornem hooks ou trabalho assíncrono posterior. Não ampliar a conclusão às18 quitações T029 sem esse sensor, a toda rede ou a outra JVM. Busca produtiva `rg -n -g '*.java' 'HttpClient|RestClient|WebClient|RestTemplate|URLConnection|new Socket|ProcessBuilder|Runtime.getRuntime|gateway|stripe|paypal|adyen' src/main/java`: exit1, zero matches; leitura da cadeia produtiva confirma ausência de gateway/cobrança. Busca estática complementa a observação, não é rastreamento universal de IO.

Leituras independentes do probe008 ocorrem após conclusão dos escritores conhecidos, antes da limpeza seletiva Payment→itens→pedidos. As fotografias de oito tabelas são compostas, não snapshot atômico sob concorrência livre; StatementInspector observa preparação Hibernate, não JDBC direto, e igualdade final não exclui mutação transitória restaurada. Janelas GET/HEAD não incluem setup/login/pagamento/manutenção/limpeza; o registro de hooks refere-se aos POSTs T031. Carrinho continua do consumidor, sem tabela/rota nova ou prova de sua memória remota.

**Ambientes distintos:** provas funcionais conciliadas são locais Windows/Java21/H2 2.2.224; esta auditoria é estática local. Não há PostgreSQL configurado ou executado, modo de compatibilidade não o substitui e não se infere durabilidade/migração/locks/precisão de outro banco. A CI Linux está configurada e o harness registra uma execução remota histórica de79 testes, com sua URL/revisão própria; isso não valida008 ou HEAD96126db. Nenhuma consulta/disparo remoto, push, PR ou CI foi realizado nesta tarefa. FINAL local ainda está pendente; mesmo um futuro FINAL local não se tornará automaticamente prova remota/PostgreSQL.

### Comandos da retomada, revisão e encerramento

Comandos efetivamente usados nesta retomada: conferências Git/seleção e check-prerequisites; Get-CimInstance Win32_Process, Get-Content/rg de artefatos/fontes/relatórios; leitura XML por ElementTree e JSON dos recibos; `git diff main HEAD -- <arquivos preservados>`; leitores Python externos em `target/feature008-evidence/T033-RESUME-20261023/`, com `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 <auxiliar>`; revisão de `git diff --`, referências, links e `git diff --check`. Nenhum auxiliar invoca Maven/JUnit ou inicializa banco/aplicação. recover2.py/evidence-recovered.py exit0/PASS fundamentam a reconciliação atual; os resultados da revisão documental final são registrados abaixo após sua execução.

Dois problemas dos leitores desta retomada foram separados dos resultados da aplicação: uma leitura auxiliar tentou ordenar atributos timestamp ausentes nos XML históricos e terminou com TypeError; a leitura corrigida apenas inventariou os109 XML históricos. recover.py confirmou os inventários362/482/10876, mas terminou com exit1 na busca ampla por FINAL porque metadados PowerShell continham IsFinal. A busca foi restringida a identificação/comando/argumentos em recover2.py e confirmou24 execuções focais e zero FINAL008; recovery-attempt1.json conserva a causa e o exit1 observado. Não se atribui aprovação à tentativa incompleta nem se altera qualquer resultado bruto. O rascunho anterior também relata ajustes de leitura de sufixos e grafias exit/exitCode/Path/Hash; somente a reconciliação completa executada agora é usada como conferência atual.

**Critérios da T033 comprovados no escopo de auditoria:** matrizes, classes/métodos/contextos, gates e limites reconciliados, sem lacuna funcional nova que exija correção/reprodução. Após revisão documental, somente T033 passa a[X]; T034–T036 permanecem abertas. SC-008/FINAL, publicação da vigência e fechamento global não são antecipados. Somente esta seção e marcador T033 são mudanças rastreadas; código, testes, asserções, classes Api, contratos, evidências anteriores e prefixo histórico do quickstart preservados. Sem stage, commit, push, PR, merge ou alteração de main.

Revisão efetivamente executada nesta retomada: `review.py` e `review.py --final`, no diretório T033-RESUME-20261023, terminaram com exit0/PASS; `git diff --check` exit0, sem diagnóstico de whitespace. Conferidos diff, comandos e193 links locais existentes nos dois documentos,360 arquivos rastreados fora do recorte idênticos, prefixo de476493 bytes do quickstart intacto,482 relatórios Surefire e11 arquivos da auditoria parcial original idênticos. Os10876 arquivos de evidência anterior já haviam sido conferidos integralmente antes da edição; nenhum comando da retomada os escreveu. tasks.md difere do recebido somente pelo marcador T033. Recibos preliminary-review.json/final-review.json e reconciliação atual ficam em target ignorado, sem arquivos novos não rastreados no projeto. Git final: somente quickstart.md/tasks.md desta feature modificados, índice vazio, branch/HEAD/main/seleção008 mantidos. A interrupção documental foi resolvida; não há impedimento restante da T033. FINAL/T034, publicação T035 e fechamento T036 permanecem pendentes e não foram executados.

## T034 — FINAL completa executada, reprovada e tarefa aberta (2026-10-23)

Executada exclusivamente a seleção de [T034](tasks.md), dependente da auditoria T033 concluída acima. Raiz confirmada `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd`, seleção local008. O estado inicial tinha somente quickstart.md/tasks.md da008 modificados pela T033, nenhum staged ou não rastreado. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`. O registro recebido da T033 permanece como prefixo byte a byte, sem revisão retroativa de suas conclusões ou evidências históricas.

Lidos integralmente T034, dependência T033, regras de preservação/execução, seleção FINAL e procedimentos aprovados deste quickstart, com o contexto de spec/plano/modelo/pesquisa/contratos, constituição, AGENTS e harness já auditado na T033. O escopo confirmado antes da chamada foi uma suíte completa sem filtro, seguida de conferência e registro, sem iniciar T035–T036. check-prerequisites retornou FEATURE_DIR absoluto008 e documentos previstos. Checklist requirements.md16/16, zero aberto, somente leitura; ignore Java adequado; extensions.yml ausente, sem hooks before/after_implement.

### Pré-execução, comando e revisão testada

Win32_Process não mostrou Maven/Java/Surefire ativo antes da chamada. A busca por identificação/comando/argumentos nos24 recibos run.json anteriores da008 não encontrou FINAL aplicável. O FINAL007 com3991 casos e os checkpoints focais foram preservados e não reutilizados como aprovação desta revisão nem somados para substituir a suíte completa. Nenhuma seleção focal foi repetida antes de FINAL.

Diretório novo ignorado `target/feature008-evidence/T034-20261023/`: prepare.py exit0 registrou362 hashes de arquivos rastreados, estado inicial e seleção/main; copiou e conferiu por SHA256 os482 relatórios anteriores em surefire-before/; inventariou tamanho/mtime de10899 arquivos de evidências anteriores. received-quickstart.md/received-tasks.md conservam o estado recebido, incluindo T033. Não foi exigida árvore limpa nem descartada qualquer alteração.

Uma chamada Maven, sequencial, por run.ps1, com identificação exclusiva:

~~~powershell
.\mvnw.cmd "-Dsurefire.reportNameSuffix=FINAL-T034-20261023T162000739" test
~~~

Não há -Dtest, exclusão, skip ou alteração de asserção. run.json registra início `2026-10-23T16:20:00.7391447-03:00` (valor exato confirmado no recibo), HEAD e os dois documentos modificados. Esse HEAD mais o diff documental recebido identifica o estado executado; código/build/testes eram os do HEAD. Ao terminar, os362 hashes recebidos ainda coincidiam, antes de acrescentar esta seção. Não houve edição de implementação, teste, fixture, configuração, contrato ou documento durante a execução.

Console Maven terminou em `2026-10-23T16:42:24-03:00`, com duração informada `22:17 min`. **BUILD FAILURE:4957 testes,39 falhas,218 erros,0 ignorados.** Console e137 XML+137 TXT próprios concordam. Não é aprovação da008, não é soma de checkpoints e não se confunde teste descoberto/contabilizado com asserções executadas quando o contexto não inicializou.

Ambiente efetivo: Java21.0.12.1 Azul Zulu em `C:/Program Files/Zulu/zulu-21`, os.name `Windows 10`, amd64 e `java.security.manager=allow`, conforme propriedades dos XML. O processo Maven observado usa a distribuição3.9.16 selecionada pelo Wrapper; runtime-processes.json registra sua linha de comando e o único fork Surefire. Boot3.3.4/Hibernate6.5.3.Final/H2 2.2.224 permanecem os do projeto. Não houve Maven concorrente ou servidor externo de banco.

### Arquivamento recuperado e exit code disponível

Após Maven concluir, Copy-Item do runner falhou ao arquivar o TXT de PublicProductDetailSecurityIntegrationTest$DuplicateCategoryNames em um destino longo, com DirectoryNotFoundException/limite de caminho do Windows, na linha32 de run.ps1. Isso aconteceu depois dos testes e não explica suas39 falhas/218 erros. O exit1 observado pela ferramenta é **do runner**, que terminou nessa cópia; não é apresentado como o exit code exato do Maven.

O runner havia guardado LASTEXITCODE apenas em uma variável transitória e não gravou exit.json antes da exceção. **O código numérico do Maven não ficou disponível de forma durável e não foi inferido de BUILD FAILURE.** completion.json registra mavenExitCode=null, runnerExitCode=1, a causa, conclusão e totais do console. Essa limitação adicional permanece explícita; não houve repetição de Maven para reconstruir o exit ausente.

A pasta parcial de80 arquivos permanece intacta em FINAL-T034-20261023T162000739/surefire-reports/. Uma primeira cópia auxiliar por shutil.copy2 no mesmo caminho também encontrou WinError3; o leitor reflect.py então não encontrou XML nessa cópia parcial. A recuperação final usou o caminho mais curto `target/feature008-evidence/T034-20261023/reports/`: recover-archive.py exit0 copiou274 arquivos,137 XML+137 TXT, todos com SHA256 igual aos originais em target/surefire-reports. Console original UTF-16 e run.json continuam preservados no diretório da execução. Não houve limpeza, movimentação ou substituição de resultados anteriores.

### Descoberta, aninhados, herança e preservação

reflect.py/InspectTests.java terminaram com exit0 e inspecionaram metadados das classes compiladas, usando Class.forName com initialize=false. Não houve descoberta JUnit adicional, inicialização de Spring ou nova invocação de teste nesse auxiliar. O inventário resultou em137 classes/containers (127 principais e10 aninhados),906 grupos de método por classe e212 grupos herdados. analyze.py final exit0 conciliou esse inventário com cada testcase e com os resumos XML/TXT/console:4957 identidades classe+nome únicas, nenhum método/classe omitido ou extra e índices parametrizados contínuos.

Foi necessário distinguir o nome do arquivo/suite XML do classname de cada testcase. Por exemplo, o XML físico de H2Console$ServletContextPath contém15 casos:8 do ROOT e7 do aninhado/api, enquanto o arquivo externo tem0. A primeira versão do leitor apontou uma divergência por agrupar somente pelo nome da suite; a conferência final agrupa pelos classnames reais, sem somar casos duas vezes ou interpretar container0 como teste omitido. Isso foi ajuste do leitor, não mudança/reexecução de teste.

Os10 aninhados efetivamente presentes são H2ConsoleSecurityIntegrationTest$ServletContextPath; OrderControllerTest$InputBoundary e $SuccessfulRegistration; PublicCatalogNameFilterIntegrationTest$ApiContext e $RootContext; PublicCatalogSecurityIntegrationTest$EmptyCatalog; PublicProductDetailSecurityIntegrationTest$DuplicateCategoryNames; PaymentRegistrationServiceTest$CurrentAccess, $RegistrationRules e $TransactionAndCategories. Todos os métodos esperados de cada classe concreta, inclusive os herdados pelas variantes Api, aparecem nos testcase. As multiplicidades de método das66 linhas de suite dos sete recibos reconciliados na T033 coincidem individualmente com esta FINAL; essa comparação verifica descoberta e não transfere aprovação de execução antiga.

| Recorte observado nesta FINAL | Casos | Resultado/alcance |
| --- | ---: | --- |
| Classes Payment* e OrderPaymentLockRepositoryTest | 913,28 classes/containers | Zero falhas/erros/ignorados; inclui os três Nested do serviço e provas HTTP, MVC, tempo, perfis, schema/JDBC, locks, rollback e transporte nos contextos previstos. Não substitui o resultado global reprovado. |
| Onze classes concretas de S5/OwnOrderQuery | 758 | Zero falhas/erros/ignorados; lista/detalhe/segurança/regressão ROOT/Api e DTO/repository/service, incluindo os oito casos T031. OwnOrderQuerySecurityConfigurationTest é outro componente e está reprovado abaixo. |
| H2ConsoleSecurityIntegrationTest e aninhado ServletContextPath | 8+7 | Todos aprovados, ROOT e/api, com servlet/filtros reais e métodos previstos; arquivo e asserções intactos. |
| JwtTokenServiceTest | 1 | Aprovado; emissão/verificação HS256 e claims seguros; arquivo e asserções intactos. |

Os20 marcadores T031_NETWORK desta execução têm jdkChecks=3 e forbiddenAttempts=0, com controles DNS/CONNECT/HTTP_PERMISSION sem chamadas externas. São20 janelas/60 hooks, não60 testes JUnit. Permanecem os limites do sensor JDK e das fotografias/SQL descritos em T031–T033; MockMvc, socket e leitura JDBC não são provas intercambiáveis. Nenhuma falha física, PostgreSQL, crash recovery ou durabilidade de produção foi simulada por esta execução.

### Diagnóstico completo da reprovação

diagnose.py exit0 classificou os257 resultados não aprovados dos XML sem casos sem classificação. Os exemplos/causas, classes e métodos completos estão em diagnostic.json; analysis.json conserva também os detalhes originais por caso. As categorias abaixo explicam o que foi observado, sem declarar aprovadas as verificações bloqueadas.

| Classe/método | Falhas/erros | Causa observada e classificação |
| --- | ---: | --- |
| AdminProductSecurityChainWiringTest.shouldSelectFourScopedChainsInTheRequiredOrderForEveryAdministrativeMethod | 1 falha | Exige hasSize(4)/ordem e índices anteriores; contexto real tem5 cadeias, incluindo paymentSecurityFilterChain antes das quatro existentes conforme plano008. Expectativa anterior superada, não prova de defeito da nova cadeia. |
| OwnOrderQuerySecurityConfigurationTest | 34 falhas | Seus contextos locais não fornecem PaymentAccessService exigido pela cadeia nova. A asserção de contexto inicializado falha antes de verificar seleção/políticas. Preparação:12 selectsFirstChainForOnlyQueryMethodsAndSingleSegment,10 configuresQueryAndExistingAuthorizationPolicies,2 preservesExistingChainSelectionAndPublicFallback e10 configuredProviderKeepsLookupFailuresDistinct. O arquivo também contém expectativas antigas de quatro cadeias que não chegaram a ser avaliadas nesses casos; não se afirma sua aprovação. |
| AdminProductDeletionIntegrationTest.anyOrderItemPreventsDeletionAndPreservesTheEntireHistory, índices3/4/7/8 | 4 falhas | Provider enumera PAID, mas registerOrder apenas POSTa/orders e espera PAID imediatamente; o resultado correto de criação é WAITING_PAYMENT. Fixture sem preparação da quitação, falhando antes do DELETE. Não é prova de exclusão de produto referenciado ou falha da transição de pagamento. As quatro variantes WAITING_PAYMENT permanecem aprovadas. |
| GlobalExceptionHandlerRegressionTest | 84 erros | Slice MVC:1 falha inicial por PaymentAccessService ausente e83 erros por ApplicationContext failure threshold. Asserções não executadas. |
| LoginControllerTest | 83 erros | Mesmo tipo de preparação:1 erro inicial e82 em cascata. Não demonstra regressão funcional de login; esses casos não estão aprovados. |
| ProductControllerTest | 51 erros | Mesmo tipo de preparação:1 erro inicial e50 em cascata. Não demonstra regressão funcional de catálogo; esses casos não estão aprovados. |

Totais:1 expectativa anterior+34 falhas de preparação local+4 fixtures incoerentes=39 falhas;3 erros iniciais de slices+215 em cascata=218 erros. OrderSecuritySliceTestConfig fornece os mocks históricos, mas não PaymentAccessService; o SecurityTestConfiguration de OwnOrderQuerySecurityConfigurationTest tem a mesma omissão. Não foi observado conflito de URL/create-drop nos resultados reprovados; suas causas demonstradas são as acima. Os testes bloqueados não permitem excluir defeitos funcionais ainda não exercitados.

O achado de cobertura PAID levado da T033 à T034 foi efetivamente executado e agora tem essas quatro falhas de setup; o registro anterior permanece histórico intacto. Nenhuma asserção foi enfraquecida, nenhum teste excluído/desabilitado, nenhum serviço mockado para substituir uma prova real existente, nenhum limite de tentativas/cache ampliado, nem perfil/seed/OSIV/mapper global alterado. Não foram implementadas correções nesta tarefa de execução, sob a instrução de preservar código e testes. Nenhuma segunda FINAL foi iniciada para obter verde.

### Estado da tarefa, limites e pendências

**T034 permanece aberta**: FINAL foi executada integralmente, mas reprovou, e o exit numérico exato do Maven não foi preservado pelo runner. A conferência de descoberta/arquivamento não transforma reprovação em PASS. Falta uma revisão apropriada de preparação/expectativas, mantendo garantias explícitas das cinco cadeias e rotas, mocks restritos aos contextos locais e PAID+Payment coerentes antes da exclusão; criação de pedido deve continuar exigindo WAITING_PAYMENT. Depois das correções verificadas caberá nova FINAL identificada, gravando seu exit antes de operações de arquivamento. Esses são próximos trabalhos, não executados ou autorizados por efeito deste diagnóstico.

T033 continua marcada e seu texto/evidências preservados; T035–T036 continuam abertas e não foram iniciadas. README e metadados planned/vigência dos contratos não foram alterados: publicação continua condicionada à aprovação. Nenhum resultado local foi apresentado como CI remota/Linux ou PostgreSQL. Não houve consulta/disparo remoto, push, PR, commit, stage ou alteração de main.

Antes da escrita documental, analyze.py confirmou362 rastreados idênticos ao recebimento,482 relatórios anteriores byte-idênticos e10899 arquivos de evidência anterior com tamanho/mtime intactos. target/surefire-reports contém756 arquivos:482 anteriores+274 desta execução, sem outro relatório novo. O único incremento rastreado desta T034 é esta seção no quickstart; tasks.md continua exatamente como recebido, com T034 aberta. Recibos inicial, run.json, console.log, completion.json, reports/, inventories, reflection e analysis/diagnostic ficam no diretório ignorado novo. Revisão de diff/referências/links/whitespace e estado final são registrados abaixo após sua execução.

Revisão documental efetivamente executada: review.py exit0 e `git diff --check` exit0, sem diagnóstico de whitespace. Conferidos o diff completo contra HEAD e o incremento contra received-quickstart.md,194 links locais existentes,361 rastreados fora do incremento idênticos, prefixo de513667 bytes recebido intacto, tasks.md sem alteração nesta T034,482 relatórios anteriores byte-idênticos,756 relatórios atuais preservados após a análise e10899 arquivos anteriores com tamanho/mtime intactos. final-review.json e os dois diffs ficam no diretório novo ignorado. Git final: somente quickstart.md/tasks.md da008 modificados (tasks recebido da T033), índice vazio e nenhum não rastreado; raiz/branch/HEAD/main/seleção008 preservados. T034–T036 continuam abertas. A revisão documental passou; a FINAL permanece reprovada e não houve correção ou nova execução de testes.

## Retomada T034 — correções de testes, focal e FINAL aprovados (2026-10-23)

Retomada exclusivamente a T034 por autorização para corrigir os testes/preparação diagnosticados. Conferidos raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd`, seleção local008, somente quickstart.md/tasks.md modificados, índice vazio e nenhum não rastreado. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`. T034 e dependências/procedimentos/diagnóstico foram lidos; o contexto aprovado de spec/plano/contratos/constituição/harness continua o auditado na T033. Pré-requisitos confirmaram FEATURE_DIR absoluto008; checklist requirements.md16/16, zero aberto, somente leitura; ignore Java adequado e extensions.yml ausente antes/depois, sem hooks de implementação.

### Causas confirmadas e correções restritas

Antes da edição foram relidos integralmente console (27539380 bytes),137 XML e diagnóstico da FINAL-T034-20261023T162000739:4957 casos únicos,39 falhas,218 erros,0 ignorados e BUILD FAILURE. Esse registro e a T033 permanecem byte a byte como prefixo. A execução antiga conserva **mavenExitCode=null e runnerExitCode=1**; seu exit1 não foi retroativamente atribuído ao Maven e a aprovação abaixo pertence a outra execução, depois das correções.

| Causa e fontes corrigidas | Mudança efetiva, mantendo os oráculos |
| --- | --- |
| Expectativa antiga em [AdminProductSecurityChainWiringTest](../../src/test/java/com/devsuperior/dscommerce/config/AdminProductSecurityChainWiringTest.java) e [OwnOrderQuerySecurityConfigurationTest](../../src/test/java/com/devsuperior/dscommerce/config/OwnOrderQuerySecurityConfigurationTest.java) | hasSize(5) e containsExactly das cinco cadeias, índices atualizados, métodos de seleção renomeados para shouldSelectFiveScopedChainsInTheRequiredOrderForEveryAdministrativeMethod e selectsOwnQueryChainForOnlyQueryMethodsAndSingleSegment. Acrescentadas asserções do matcher de pagamento em todos os verbos, incluindo ID inválido antes de MVC e caminhos fora do recorte que continuam na cadeia administrativa. Preservadas seleção de consultas/registro/admin/público e autorização; política ADMIN da cadeia de pagamento incluída na matriz de perfis. |
| PaymentAccessService ausente em34 contextos locais e nos três slices MVC (84+83+51 erros) | Bean simulado explícito no SecurityTestConfiguration local e em [OrderSecuritySliceTestConfig](../../src/test/java/com/devsuperior/dscommerce/config/OrderSecuritySliceTestConfig.java), este ainda condicional e restrito aos slices públicos existentes. requireAdmin lança FORBIDDEN por padrão: não há permissão silenciosa pelo retorno void do mock. As provas do provider de consultas exigem nenhuma interação com esse bean; suas falhas401/403/500 continuam próprias. GlobalExceptionHandlerRegressionTest, LoginControllerTest e ProductControllerTest não receberam mudanças nem desativação de filtros; a dependência de preparação foi completada pelo suporte compartilhado. |
| Quatro variantes PAID em [AdminProductDeletionIntegrationTest](../../src/test/java/com/devsuperior/dscommerce/controllers/AdminProductDeletionIntegrationTest.java) | registerOrder exige WAITING_PAYMENT na resposta e no JDBC. Somente a variante que precisa de PAID faz POST real/admin/orders/{id}/payments com ADMIN, filtros/serviço/proxy/transação reais e ocorrência igual à criação. Exige201 com três campos, ocorrência conservada, Payment por leitura independente e alteração somente do status da raiz, mantendo os demais dados. Depois disso exige PAID e a rejeição409 da exclusão, sem reduzir o provider a WAITING_PAYMENT. |

As cadeias produtivas conferidas, sem editar SecurityConfig ou outro código de produção:

| Ordem | Bean/matcher relativo ao contexto | Responsabilidade preservada |
| ---: | --- | --- |
| -1 | paymentSecurityFilterChain: /admin/orders/*/payments, todos os métodos | ADMIN atual; tratamento específico de pagamento, antes de MVC. |
| 0 | ownOrderQuerySecurityFilterChain: GET/HEAD/orders e/orders/* | CLIENT ou ADMIN, com titularidade própria na guarda/consulta. |
| 1 | orderSecurityFilterChain: POST/orders | Registro por CLIENT ou ADMIN. |
| 2 | adminProductSecurityFilterChain: /admin/** | Manutenção administrativa com ADMIN. |
| 3 | securityFilterChain: demais caminhos | Fallback público, preservando catálogo/login/console. |

O mock de pagamento existe somente na configuração local de teste e nos slices indicados; a preparação PAID e as provas008 continuam com a guarda real. Os34 resultados de OwnOrderQuerySecurityConfigurationTest eram falhas de asserção de contexto, antes das políticas; os218 erros dos slices eram3 iniciais e215 em cascata. As quatro variantes de exclusão falhavam no setup, antes de DELETE. Nenhuma dessas causas foi atribuída à regra produtiva de criação ou ocultada como aprovação anterior.

O probe008 foi composto no teste de exclusão, sem modificar os probes históricos. Cada leitura/DELETE e o setup de criação exigem Payment inalterado; a quitação válida é uma etapa separada. Snapshot JDBC verifica Payment, comprador/criação/itens/valores e catálogo; a limpeza remove somente Payment dos IDs próprios antes de itens/pedidos. Cada execução das18 variantes de exclusão fez seis quitações201 (três ROOT, três/api) nos índices3/4/7/8: são requests de preparação dentro dos testes, não seis testes adicionais. O provider, contagens, envelopes e proteção de compras permanecem exigidos. Não se mudou produção, contrato, POM/Wrapper, perfis globais, seed, cache, filtros, H2ConsoleSecurityIntegrationTest ou JwtTokenServiceTest.

### Execuções novas e estado efetivamente testado

Recibo novo ignorado `target/feature008-evidence/T034R-20261023/`. prepare.py exit0 guardou362 hashes rastreados, cópias recebidas dos documentos e756 relatórios anteriores copiados/conferidos por SHA256 em before/; inventariou11767 arquivos anteriores por tamanho/mtime. O RED funcional/preparação utilizado foi a FINAL reprovada já concluída, não uma repetição antes da correção. O focal novo foi exigido expressamente nesta retomada, sem baseline ou checkpoint redundante.

Antes de cada chamada, Win32_Process não encontrou Maven/Surefire ativo. Antes de FINAL, preflight-final.json conferiu que a FINAL antiga não correspondia ao estado corrigido: quatro arquivos de teste/suporte diferentes, produção/build iguais; não havia FINAL concluída aplicável. O hash de todos os rastreados antes de FINAL coincidiu com o do focal aprovado. O HEAD mais esse diff de quatro testes e os documentos recebidos identifica a revisão executada; mudança de SHA não foi presumida nem houve edição durante as execuções.

Comandos efetivos, sequenciais, pelo Wrapper:

~~~powershell
.\mvnw.cmd "-Dtest=AdminProductSecurityChainWiringTest,OwnOrderQuerySecurityConfigurationTest,AdminProductDeletionIntegrationTest,GlobalExceptionHandlerRegressionTest,LoginControllerTest,ProductControllerTest" "-Dsurefire.reportNameSuffix=FOCAL-T034R-20261023T170156614" test
.\mvnw.cmd "-Dsurefire.reportNameSuffix=FINAL-T034R-20261023T170507809" test
~~~

| Execução | Início e término (-03:00) | Resultado console/XML/TXT | Exit Maven |
| --- | --- | --- | ---: |
| FOCAL-T034R-20261023T170156614 | 17:01:56.614295 → runner17:04:03.289584; Maven02:00 min | 307/0/0/0, BUILD SUCCESS;6 XML+6 TXT | 0 |
| FINAL-T034R-20261023T170507809 | 17:05:07.809670 → runner17:26:25.018445; Maven terminou17:26:22,21:08 min | 4957/0/0/0, BUILD SUCCESS;137 XML+137 TXT | 0 |

Notação: testes/falhas/erros/ignorados. Focal e FINAL são execuções distintas; os307 estão incluídos na FINAL, não são somados aos4957. O focal selecionou exatamente seis classes concretas,62 grupos de método, nenhum aninhado ou herdado, sem seleção vazia/extra. Todos os307 casos passaram e foram conferidos antes de iniciar FINAL. Seus métodos/multiplicidades coincidem com os observados nas mesmas seis classes da FINAL.

O novo runner Python captura o resultado numérico do processo Wrapper/Maven e grava **exit.json imediatamente após wait(), antes de qualquer cópia**, com capturedBeforeArchiving=true. Arquivamento e integridade ficam em archive.json, separados. FOCAL/ e FINAL/ guardam run.json, console.log UTF-8, exit.json, fontes antes, inventários de relatórios, reports/ e analysis.json. Diretórios curtos e sufixos exclusivos evitaram a falha de caminho anterior;12 e274 relatórios foram arquivados integralmente com hashes iguais aos originais. Nenhuma limpeza ou sobrescrita de arquivo anterior.

### Conferência completa e critérios da T034

reflect.py/InspectTests.java exit0 inspecionou metadados compilados sem inicializar as classes ou executar JUnit;137 classes/containers,906 grupos de método por classe e212 herdados. analyze.py FOCAL e FINAL exit0 reconciliaram console completo, totais e nós de falha/erro/skip, cada TXT/XML e testcase classname. A FINAL tem **4957 identidades classe+nome únicas**,127 classes/containers principais e10 aninhados, nenhum método/classe esperado omitido ou extra, índices parametrizados contínuos e nenhum teste ignorado. Containers e arquivos físicos não foram contados novamente como testes: ROOT/aninhados são atribuídos pelo classname de cada caso, inclusive o H2 Console. O código compilado é o mesmo entre focal e FINAL.

Os10 aninhados continuam os identificados na FINAL anterior: H2Console$ServletContextPath; OrderController$InputBoundary/$SuccessfulRegistration; PublicCatalogNameFilter$ApiContext/$RootContext; PublicCatalogSecurity$EmptyCatalog; PublicProductDetailSecurity$DuplicateCategoryNames; PaymentRegistrationService$CurrentAccess/$RegistrationRules/$TransactionAndCategories. As variantes Api herdam os métodos previstos. A comparação individual de multiplicidades com C1–C5/recibos da T033 permaneceu idêntica; ela verifica descoberta, sem importar sua aprovação nem somar checkpoints para substituir FINAL.

| Casos afetados, aprovados novamente dentro da FINAL | Casos |
| --- | ---: |
| AdminProductSecurityChainWiringTest | 13 |
| OwnOrderQuerySecurityConfigurationTest | 58 |
| AdminProductDeletionIntegrationTest | 18 |
| GlobalExceptionHandlerRegressionTest | 84 |
| LoginControllerTest | 83 |
| ProductControllerTest | 51 |
| Total desse recorte, já incluído nos4957 | 307 |

H2ConsoleSecurityIntegrationTest executou8 casos ROOT e7 do aninhado/api; JwtTokenServiceTest executou1: todos aprovados e ambos os arquivos byte-idênticos ao recebimento. As classes008 e regressões001–007 foram incluídas na suíte sem filtro, preservando os métodos de concorrência, precisão, SQL, rollback/commit, transmissão e consultas ROOT/Api. Os seis marcadores T034_SETTLED da FINAL comprovam o alcance da nova preparação; os20 T031_NETWORK existentes mantiveram jdkChecks=3 e forbiddenAttempts=0. Não se confundem requests, hooks ou failpoints com contagem JUnit; permanecem os limites do sensor, dos snapshots e da distinção entre MockMvc, socket e JDBC já documentados. Não houve falha nova a diagnosticar ou repetição para obter verde depois desta revisão.

Ambiente efetivo dos XML: Windows10/amd64, Java21.0.12.1 Azul Zulu em C:/Program Files/Zulu/zulu-21, java.security.manager=allow, Wrapper Maven3.9.16 e stack/H2 do projeto preservados. É validação **local Windows/Java21/H2**, sem nova execução remota/CI/Linux ou PostgreSQL, sem prova de outro SGBD, crash recovery ou durabilidade de produção. Nenhum gateway/cobrança foi introduzido pelas correções; os mocks locais não constituem prova de banco ou JWT real.

### Preservação e fechamento restrito

A análise confirmou os fontes iguais durante cada execução, os756 relatórios recebidos preservados byte a byte e os768 anteriores à FINAL (incluindo os12 focais) idênticos. Agora target/surefire-reports tem1042 arquivos:756 recebidos+12 focais+274 da nova FINAL; nenhum extra, parcial ou histórico foi misturado ao resultado novo. Os11767 arquivos anteriores mantêm tamanho/mtime e os recibos da FINAL reprovada permanecem intactos. O prefixo recebido inteiro do quickstart, incluindo T033 e a reprovação, foi preservado.

Critérios da T034 comprovados: causas confirmadas antes das quatro correções de teste, focal aprovado, nova FINAL da revisão estabilizada aprovada sem filtro/omissões/duplicatas, H2 Console/JWT íntegros e exit numérico disponível. Após revisão documental, somente T034 passa a[X]. **T035–T036 permanecem abertas**; README, metadados planned/vigência e contratos não foram alterados nem publicados. A entrega documental/global dessas etapas não é antecipada pela aprovação local. Sem stage, commit, push, PR, merge ou alteração de main.

Revisão efetivamente executada: review.py exit0/PASS, diff de código e documentos revisado e `git diff --check` exit0, sem diagnóstico de whitespace. Confirmados356 rastreados fora do recorte idênticos, os quatro testes/suportes iguais aos hashes aprovados na FINAL, prefixo de528623 bytes recebido intacto e198 links locais existentes. tasks.md difere do recebido somente pelo marcador T034; T033 preservada e T035–T036 abertas. Os756 relatórios recebidos são byte-idênticos,1042 atuais conferem com o inventário após FINAL e11767 arquivos anteriores conservam tamanho/mtime. Não há arquivo novo não rastreado; os auxiliares/evidências novos ficam em target ignorado. Git final tem seis modificados: quickstart.md/tasks.md da008 e os quatro Java deste recorte; índice vazio, raiz/branch/HEAD/main/seleção008 mantidos, nenhum Maven ativo. Sem impedimento restante da T034; pendências limitadas às etapas T035–T036 e aos ambientes não executados, sem publicação ou integração presumida. final-review.json e reviewed.diff conservam a revisão.


## T035 — consolidação documental das evidências (2026-10-23)

Escopo exclusivo de [T035](tasks.md), dependente da T034 aprovada; T036 não iniciada. Aplicada a skill speckit-implement ao recorte solicitado. Confirmados raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd` e seleção local008. Estado inicial: exatamente seis modificados — este quickstart, tasks.md e os quatro testes/suportes da retomada T034 acima —, índice vazio e nenhum não rastreado. Main `5cd16709ea531ccae27230db19753e6c5533d12d` preservada. Lidos T035, dependências, procedimentos, contratos e registros existentes. Pré-requisitos confirmaram FEATURE_DIR008; checklist requirements.md:16/16 concluídos, zero aberto, somente leitura. Ignore Java suficiente; extensions.yml ausente, sem hooks.

Esta tarefa consolida provas existentes: **nenhum Maven, JUnit, aplicação ou validador OpenAPI foi executado novamente**. Revisão de texto, XML/TXT e hashes não constitui nova comprovação funcional. prepare.py registrou362 hashes rastreados,1042 relatórios atuais e tamanho/mtime dos12850 arquivos de evidência anteriores; os258 arquivos de fonte/teste/build conferem byte a byte com sources-before.json da FINAL aprovada. Os quatro Java recebidos, produção, contratos, asserções e artefatos007 permanecem preservados. Recibo novo ignorado: `target/feature008-evidence/T035-20261023/`.

### Catálogo das execuções reutilizadas e revisão

**S** identifica HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd` mais o diff dos quatro Java recebido da T034: AdminProductSecurityChainWiringTest, OwnOrderQuerySecurityConfigurationTest, OrderSecuritySliceTestConfig e AdminProductDeletionIntegrationTest. Não há commit novo. Os hashes em `target/feature008-evidence/T034R-20261023/FINAL/sources-before.json` identificam os conteúdos efetivamente testados; initial.json/tracked.json desta T035 conferem sua igualdade atual. As únicas mudanças desta T035 são documentais. **F** é exclusivamente FINAL-T034R-20261023T170507809, não a FINAL reprovada ou FINAL007.

Comandos históricos efetivos, já concluídos na T034, exibidos para rastreabilidade e **não reexecutados**:

~~~powershell
.\mvnw.cmd "-Dtest=AdminProductSecurityChainWiringTest,OwnOrderQuerySecurityConfigurationTest,AdminProductDeletionIntegrationTest,GlobalExceptionHandlerRegressionTest,LoginControllerTest,ProductControllerTest" "-Dsurefire.reportNameSuffix=FOCAL-T034R-20261023T170156614" test
.\mvnw.cmd "-Dsurefire.reportNameSuffix=FINAL-T034R-20261023T170507809" test
~~~

| Execução/revisão | Seleção, conclusão e resultado reutilizado | Evidência local | Exit Maven |
| --- | --- | --- | ---: |
| FOCAL-T034R-20261023T170156614 / S | Seis classes concretas selecionadas acima,62 grupos de método;307/0/0/0; BUILD SUCCESS;2026-10-23 de17:01:56.614295 a17:04:03.289584 -03:00 | T034R-20261023/FOCAL: run.json, console.log UTF-8, exit.json, analysis.json e reports/ (6 XML+6 TXT) | 0 |
| F / S | Suíte completa sem filtro,4957/0/0/0; BUILD SUCCESS;2026-10-23 de17:05:07.809670 a17:26:25.018445 -03:00; Maven terminou17:26:22 | T034R-20261023/FINAL: run.json, console.log UTF-8, exit.json, archive.json, analysis.json, sources-before.json e reports/ (137 XML+137 TXT) | 0 |
| FINAL-T034-20261023T162000739 / revisão anterior às quatro correções | 4957/39/218/0; BUILD FAILURE; Maven terminou16:42:24 -03:00; reprovação histórica preservada | T034-20261023/FINAL-T034-20261023T162000739: console.log UTF-16, run.json, completion.json e cópia parcial intacta; relatórios completos recuperados em T034-20261023/reports/ | Indisponível; runner=1 |

Notação: testes/falhas/erros/ignorados. Todas as bases acima são relativas a `target/feature008-evidence/`. Focal307 está incluído em F4957 e não é somado. C1–C5 e FINAL007 não substituem F. O exit1 histórico pertence ao runner que falhou no arquivamento; não foi atribuído ao Maven. Os exits0 das duas execuções aprovadas foram capturados numericamente antes do arquivamento, com capturedBeforeArchiving=true.

verify-evidence.py exit0 releu os consoles, cada XML/TXT e exits já existentes: totais coincidentes, zero nós failure/error/skipped,307 e4957 identidades classe+nome únicas, sem duplicata entre arquivos da mesma execução. A reconciliação de descoberta já concluída na T034 permanece aplicável: F tem127 classes/containers principais+10 aninhados,906 grupos de método por classe e212 herdados; nenhum método/classe esperado omitido/extra e índices parametrizados contínuos. H2ConsoleSecurityIntegrationTest (8 ROOT+7 no aninhado/api) e JwtTokenServiceTest (1) permanecem íntegros/aprovados. O manifesto compilado foi apenas reutilizado; não se inicializou classe nem se executou reflexão/compilação nesta tarefa. reused-evidence.json conserva a conferência documental atual.

matrix.py exit0 reconciliou30 cenários com21 classes concretas e91 referências método/contexto nos137 XML existentes de F. Não é contagem nova de testes nem nova execução. ROOT e/api têm relatórios concretos distintos quando indicados; temporal/concorrência/socket somente ROOT não são apresentados como cobertura/api.

### Matriz final dos 30 cenários

Todas as linhas abaixo se referem à execução **F**, revisão **S**, resultado **PASS**, com os XML/TXT arquivados de F; não houve nova execução nesta T035. Métodos repetidos em mais de um cenário representam as mesmas invocações, sem somá-las novamente. Os índices entre colchetes são os índices JUnit efetivos. Os demais métodos parametrizados incluem todas as invocações indicadas pelos XML.

| Cenário | Classe abreviada e método/invocações efetivas | Contexto e quantidade | Execução/revisão/resultado e relatório | Prova/limite |
| --- | --- | --- | --- | --- |
| US1.1 | `R.us11AdminSettlesForeignOrderWithExactlyThreeConfirmationFieldsAndHistorical4250` | R: 1 ROOT, 1 /api | F/S, PASS; relatórios R | 201 fechado,42.50 e commit observado |
| US1.2 | `R.us12OwnAdminWithoutClientAndBothProfilesUseTheSameSettlementRules` | R: 3 ROOT, 3 /api | F/S, PASS; relatórios R | ADMIN isolado/ambos; próprio/alheio |
| US1.3 | `R.us13CatalogPriceTwelveDoesNotReplaceHistoricalTwentyOrOrderData` | R: 1 ROOT, 1 /api | F/S, PASS; relatórios R | Histórico20.00 versus catálogo12.00 |
| US1.4 | `R.us14EquivalentOffsetWithExtraZerosPreservesOccurrenceRatherThanBusinessReference` | R: 1 ROOT, 1 /api | F/S, PASS; relatórios R | Mesmo Instant, sem substituir ocorrência |
| US1.5 | `R.us15OccurrenceEqualToCreationIsAccepted`; `T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait[1]` | R: 1 ROOT, 1 /api; T: 1 ROOT | F/S, PASS; relatórios R,T | LOWER_EQUAL inclusive, também após espera |
| US1.6 | `R.us16OccurrenceEqualToInitialReferenceSurvivesClockAdvanceBeforeCompletion`; `T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait[3]` | R: 1 ROOT, 1 /api; T: 1 ROOT | F/S, PASS; relatórios R,T | UPPER_EQUAL, referência fixa durante espera |
| US2.1 | `A.missingInvalidExpiredAndRemovedAccountCredentialsAre401WithoutRestrictedReads`; `A.accountRemovedBetweenFilterAndServiceBecomes401WithoutExposingThePurchase` | A: 5 ROOT, 5 /api; A: 1 ROOT, 1 /api | F/S, PASS; relatórios A | 401 sem leitura restrita; conta removida também após filtro |
| US2.2 | `A.clientAndNoProfileCannotSettleEvenTheirOwnPurchase` | A: 4 ROOT, 4 /api | F/S, PASS; relatórios A | 403 sem ADMIN |
| US2.3 | `A.revocationAfterIssuanceDefeatsTheSameSignedAdminClaims`; `A.currentGrantAllowsTheSamePreviouslyDeniedCredentialWithoutReissuingIt`; `A.revocationBetweenFilterAndServiceIsRereadBeforeAnyOrderLookup`; `A.signedRoleNameAndAuthorityClaimsCannotGrantOrRemoveDatabasePermission`; `T.scalarGuardAfterTheWaitRejectsRevocationDespiteInitializedManagedRoles` | A: 4 ROOT, 4 /api; A: 4 ROOT, 4 /api; A: 4 ROOT, 4 /api; A: 4 ROOT, 4 /api; T: 2 ROOT | F/S, PASS; relatórios A,T | Perfil atual e releitura escalar após espera; não serializa revogação depois da última leitura |
| US2.4 | `I.absentPositiveIdentityIsBusiness404WithoutCreatingOrChangingAnyRow` | I: 1 ROOT, 1 /api | F/S, PASS; relatórios I | 404 de negócio para ID positivo ausente |
| US2.5 | `I.rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness`; `I.rejectsEveryInvalidR3GrammarCalendarRangeAndPrecisionBeforeBusiness`; `I.selectedPaymentRouteRejectsNonpositiveMalformedOrOutOfRangeIds` | I: 39 ROOT, 39 /api; I: 48 ROOT, 48 /api; I: 7 ROOT, 7 /api | F/S, PASS; relatórios I | Documento/gramática/ID inválidos antes de negócio |
| US2.6 | `R.basicWindowRejectsInstantsOutsideInclusiveBoundsWithoutChangingHistoricalTables[1]`; `T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait[2]` | R: 1 ROOT, 1 /api; T: 1 ROOT | F/S, PASS; relatórios R,T | BEFORE_CREATION;400 sem ajuste |
| US2.7 | `R.basicWindowRejectsInstantsOutsideInclusiveBoundsWithoutChangingHistoricalTables[2]`; `T.initialReferenceAndInclusiveWindowSurviveARealRootLockWait[4,6]` | R: 1 ROOT, 1 /api; T: 2 ROOT | F/S, PASS; relatórios R,T | AFTER_REFERENCE e NATIVE_CEILING;400 conservando referência |
| US2.8 | `I.rejectsMissingNullWrongTypeExtraDuplicateAndTrailingJsonBeforeBusiness`; `I.everyQueryParameterIsInvalidAndExcludedFromTheSafePath` | I: 39 ROOT, 39 /api; I: 5 ROOT, 5 /api | F/S, PASS; relatórios I | Somente path e occurredAt; nenhum query aceito |
| US2.9 | `I.paidWithoutPaymentIsIncompatibleState409AndIsNeverAutomaticallyRepaired` | I: 1 ROOT, 1 /api | F/S, PASS; relatórios I | 409 sem reparo de PAID sem Payment |
| US3.1 | `C.sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal` | C: 4 ROOT | F/S, PASS; relatórios C | 409, mesma ocorrência ou outra e mesmo/outro ADMIN; casos compartilhados comUS3.2 |
| US3.2 | `C.sequentialRepeatNeverReusesSuccessOrOverwritesTheOriginal` | C: 4 ROOT | F/S, PASS; relatórios C | Ocorrência original conservada; mesmas quatro invocações deUS3.1 |
| US3.3 | `C.existingPaymentPrecedesWaitingStateWithoutRepair` | C: 4 ROOT | F/S, PASS; relatórios C | Payment anterior prevalece sobre WAITING_PAYMENT, sem reparo |
| US3.4 | `C.overlappingTransactionsForTheSameOrderAcceptExactlyOnePayment` | C: 4 ROOT | F/S, PASS; relatórios C | Disputa real na mesma raiz:201+409, uma ocorrência vencedora |
| US3.5 | `C.validRequestsForDistinctOrdersBothSettleWithOverlappingTransactions`; `O.distinctOrderCommitsWhileTheOtherStillWaitsForItsRootLock` | C: 1 ROOT; O: 2 ROOT | F/S, PASS; relatórios C,O | Duas raízes aceitas; uma conclui enquanto outra espera |
| US4.1 | `B.reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows`; `B.confirmationConstructorFailureAfterRealFlushRollsBackPaymentAndPaidTogether` | B: 6 ROOT, 6 /api; B: 1 ROOT, 1 /api | F/S, PASS; relatórios B | 500/rollback antes da efetivação conhecida |
| US4.2 | `B.reachedFailurePointReturnsSafe500AndPreservesAllCommittedRows`; `B.realDatabaseConstraintRejectsPaymentInsertWithoutBeingClassifiedAsRepeat` | B: 6 ROOT, 6 /api; B: 1 ROOT, 1 /api | F/S, PASS; relatórios B | Primeiro DML real, flush, beforeCommit, pré-delegação e constraint H2 |
| US4.3 | `M.bufferedConfirmationIsDeliveredOrPreventedOnlyAfterIndependentCommitProof`; `W.proxyReceivesCommittedConfirmationThenForwardsOrClosesWithoutDeliveringIt` | M: 2 ROOT; W: 2 ROOT | F/S, PASS; relatórios M,W | FORWARD/WITHHOLD; MockMvc e socket ROOT separados; commit, novo409 e GET PAID |
| US4.4 | `A.controlledRepositoryLookupFailuresStay500RatherThan401OrBusinessConflicts`; `B.internalFailureIsNotAuthenticationNotFoundOrRepeatAndLeavesNoCommittedMutation` | A: 4 ROOT, 4 /api; B: 5 ROOT, 5 /api | F/S, PASS; relatórios A,B | 500 seguro, sem conversão em401/404/409 |
| US5.1 | `L.listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount`; `D.detailPost008RealPaymentKeepsCurrentNamesRecordedItemsAndExactMoney` | L: 1 ROOT, 1 /api; D: 3 ROOT, 3 /api | F/S, PASS; relatórios L,D | PAID em lista/detalhe, mesmos campos e dinheiro |
| US5.2 | `S.ownershipPost008AdminPaymentDoesNotGrantForeignQueriesOrChangeGetHeadErrors` | S: 3 ROOT, 3 /api | F/S, PASS; relatórios S | ADMIN pagador sem leitura/count alheios;404 seguro |
| US5.3 | `L.listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount` | L: 1 ROOT, 1 /api | F/S, PASS; relatórios L | Cinco pedidos2/2/1 por criação/ID; mesmas invocações também emUS5.1 |
| US5.4 | `G.regressionPost008RepeatedGetHeadSuccessEmptyAndErrorsKeepAllEightTables` | G: 1 ROOT, 1 /api | F/S, PASS; relatórios G | GET/HEAD repetidos, oito tabelas e ausência de DML nas janelas |
| US5.5 | `G.regressionPost008PublicCatalogFilterPagesDetailsAndRealLoginRemainIndependentOfPaidQueries`; `G.regressionPost008MaintenanceAndRepeatedRegistrationsPreservePaidHistoryAndCreateNewWaitingOrders` | G: 1 ROOT, 1 /api; G: 1 ROOT, 1 /api | F/S, PASS; relatórios G | Catálogo/login/manutenção/registro preservados; suíte F completa aprovada |
| US5.6 | `G.regressionPost008RepeatedGetHeadSuccessEmptyAndErrorsKeepAllEightTables`; `G.regressionPost008PaymentRejectionsPreserveAllTablesAndTheOriginalSettlementWithoutExternalAttempts`; `G.regressionPost008PublicCatalogFilterPagesDetailsAndRealLoginRemainIndependentOfPaidQueries`; `G.regressionPost008MaintenanceAndRepeatedRegistrationsPreservePaidHistoryAndCreateNewWaitingOrders` | G: 1 ROOT, 1 /api; G: 1 ROOT, 1 /api; G: 1 ROOT, 1 /api; G: 1 ROOT, 1 /api | F/S, PASS; relatórios G | Somente alvo no sucesso;20 janelas/60 hooks JDK, sem tentativas externas no alcance; carrinho externo não observado |

### Chave de classes e relatórios da matriz

Base local dos relatórios: `target/feature008-evidence/T034R-20261023/FINAL/reports/`. Cada classe qualificada abaixo corresponde exatamente a `TEST-<classe>-FINAL-T034R-20261023T170507809.xml` e `<classe>-FINAL-T034R-20261023T170507809.txt`, conferidos nesta consolidação. São arquivos locais ignorados pelo Git; este resumo e a matriz ficam versionados, mas os recibos precisam ser preservados/transferidos separadamente para outro checkout. Nenhum nome é uma classe futura ou somente um filtro.

| Chave | Classe qualificada real e contexto |
| --- | --- |
| R | `com.devsuperior.dscommerce.controllers.PaymentRegistrationHttpIntegrationTest` — ROOT |
| R | `com.devsuperior.dscommerce.controllers.PaymentRegistrationApiHttpIntegrationTest` — /api |
| A | `com.devsuperior.dscommerce.controllers.PaymentAuthorizationHttpIntegrationTest` — ROOT |
| A | `com.devsuperior.dscommerce.controllers.PaymentAuthorizationApiHttpIntegrationTest` — /api |
| I | `com.devsuperior.dscommerce.controllers.PaymentInputHttpIntegrationTest` — ROOT |
| I | `com.devsuperior.dscommerce.controllers.PaymentInputApiHttpIntegrationTest` — /api |
| T | `com.devsuperior.dscommerce.services.PaymentTemporalAndCurrentRolesIntegrationTest` — ROOT, HTTP e espera física H2 |
| C | `com.devsuperior.dscommerce.controllers.PaymentConcurrencyIntegrationTest` — ROOT |
| O | `com.devsuperior.dscommerce.controllers.PaymentDistinctOrdersIntegrationTest` — ROOT |
| B | `com.devsuperior.dscommerce.controllers.PaymentRollbackIntegrationTest` — ROOT |
| B | `com.devsuperior.dscommerce.controllers.PaymentRollbackApiHttpIntegrationTest` — /api |
| M | `com.devsuperior.dscommerce.controllers.PaymentResponseDeliveryIntegrationTest` — ROOT, MockMvc/buffer; recuperação HTTP/JDBC |
| W | `com.devsuperior.dscommerce.controllers.PaymentResponseTransportHttpIntegrationTest` — ROOT, socket/proxy HTTP e JDBC |
| L | `com.devsuperior.dscommerce.controllers.OwnOrderQueryListHttpIntegrationTest` — ROOT |
| L | `com.devsuperior.dscommerce.controllers.OwnOrderQueryListApiHttpIntegrationTest` — /api |
| D | `com.devsuperior.dscommerce.controllers.OwnOrderQueryDetailHttpIntegrationTest` — ROOT |
| D | `com.devsuperior.dscommerce.controllers.OwnOrderQueryDetailApiHttpIntegrationTest` — /api |
| S | `com.devsuperior.dscommerce.controllers.OwnOrderQuerySecurityHttpIntegrationTest` — ROOT |
| S | `com.devsuperior.dscommerce.controllers.OwnOrderQuerySecurityApiHttpIntegrationTest` — /api |
| G | `com.devsuperior.dscommerce.controllers.OwnOrderQueryRegressionHttpIntegrationTest` — ROOT |
| G | `com.devsuperior.dscommerce.controllers.OwnOrderQueryRegressionApiHttpIntegrationTest` — /api |

### Procedimentos, preservação e limites da T035

Procedimento efetivo desta consolidação: comandos Git de raiz/branch/HEAD/status/índice/não rastreados/seleção; check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks; leitura integral dos artefatos e registros; comparação de hashes e leitura de XML/TXT/JSON por auxiliares Python externos. Comando dos auxiliares: `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T035-20261023/<auxiliar>.py`, para prepare.py, verify-evidence.py e matrix.py, todos exit0. São inventário/reconciliação documental; não iniciam Maven, validadores, banco ou servidor. A revisão final de diff, links, arquivos novos e whitespace será registrada abaixo após executada.

O README da raiz passa a apontar explicitamente POST008, snapshot completo de consultas008, regras de precedência e esta matriz. Corrigida sua afirmação antiga de que JWT não autoriza rota protegida: catálogo continua público e pedidos/admin usam bearer/perfis atuais. Isso descreve implementação já testada, sem mudar contrato ou código. O snapshot008 é o substituto completo previsto para consultas007 após entrega, não um complemento simultaneamente vigente. Entretanto a publicação da vigência continua impedida pelo conflito descrito abaixo; os contratos007 permanecem históricos intocados.

**Impedimento documental: T035 permanece aberta.** A instrução atual manda preservar integralmente contratos; T035 exige metadados públicos sem contradição de vigência. contracts/README.md ainda diz desenho futuro/ausência de runtime e os dois YAML008 conservam x-status planned e descrições não implementado/não testado. Alterar apenas esses metadados editoriais conciliaria a entrega local comprovada com a vigência, mantendo schemas/regras, mas exige esclarecer se essa alteração está incluída na preservação solicitada. A clarificação foi apresentada; até resposta, contratos preservados byte a byte e nenhuma declaração de vigência concluída. A checkbox T035 continua aberta e T036 não foi iniciada. Não se presume aprovação por tempo decorrido.

Conteúdo normativo dos contratos não mudou. Reutilizada a validação estrutural documental registrada em **Validação documental desta fase** (2026-10-21): openapi-spec-validator0.9.0/openapi-schema-validator0.9.0/PyYAML6.0.3/jsonschema4.26.0, exit0; POST:5 schemas/12 exemplos/0 defaults/16 refs; consultas:7 schemas/15 exemplos/2 defaults/26 refs. Preservados os7 negativos de schema e o oráculo temporal documental8 aceitos/11 rejeitados. Não são provas novas nem substituem parser/segurança/transação/runtime; não houve nova chamada desses validadores.

F comprova **local Windows10/amd64, Java21.0.12.1 Azul Zulu, Maven Wrapper3.9.16/H2** nos limites do projeto. Nenhuma validação remota da008 foi realizada ou disparada nesta T035; o CI histórico Linux do harness não aprova esta revisão. **PostgreSQL não validado**, sem inferência sobre outro SGBD, produção/crash recovery/durabilidade. Não houve push/consulta/disparo de CI ou publicação externa. Mocks de slices, teste de buffer MockMvc, socket/proxy HTTP e leitura JDBC mantêm seus alcances distintos; failpoints não provam falha física do commit. Os20 marcadores T031_NETWORK/60 hooks JDK sem tentativa externa valem nas janelas observadas e não constituem60 casos JUnit ou monitoramento ilimitado de qualquer integração/carrinho externo.

O Gate IV da Feature005 permanece **EXCEÇÃO DOCUMENTADA**, conforme [plano005](../005-manter-produtos/plan.md); não foi promovido a PASS estrito nem herdado pela008. T033 e seus gates/provas locais permanecem como auditados. A reprovação anterior, sua recuperação e as correções da T034 continuam integralmente no prefixo recebido. Os quatro arquivos de teste/suporte da T034 não receberam alteração nesta T035. Nenhum stage, commit, push, PR, merge ou alteração de main.

Revisão documental executada: review.py exit0 e `git diff --check` exit0, sem diagnóstico de whitespace. Conferidos o diff de código/documentos e os auxiliares novos ignorados,216 links locais e a âncora do README para esta seção. Os360 rastreados fora do incremento desta T035,258 fontes/testes/build iguais aos testados, prefixo recebido de542563 bytes,1042 relatórios Surefire e tamanho/mtime dos12850 arquivos anteriores permanecem íntegros. Contracts/README.md e os dois YAML008 byte-idênticos; nenhum artefato007 alterado. tasks.md byte-idêntico ao recebido, com T033/T034 concluídas e T035/T036 abertas. Get-CimInstance Win32_Process não encontrou Maven/Surefire ativo. final-review.json, reviewed.diff e quickstart-increment.md registram esta conferência documental, sem aprovação funcional nova.

Estado final desta execução limitada pelo impedimento editorial: somente README.md e o incremento deste quickstart foram alterados nesta T035. Git tem sete modificados no total: README.md, quickstart.md/tasks.md da008 e os quatro Java recebidos da T034, índice vazio e nenhum não rastreado. Raiz/branch/HEAD/main/seleção008 preservados; nenhum contrato publicado como vigente, sem alteração de produção/contratos/testes ou início da T036. Falta esclarecer o ajuste restrito dos metadados editoriais e, se autorizado, alinhar vigência e concluir os critérios restantes da T035. Validações remota008 e PostgreSQL continuam não realizadas.

## Retomada T035 — vigência editorial alinhada à implementação local (2026-10-23)

Retomada somente a pendência editorial da T035, expressamente esclarecida pelo usuário: autorizada a correção de planned e de alegações de ausência de implementação/runtime incompatíveis com as provas locais. A seção anterior conserva o histórico do impedimento; **ele está resolvido nesta retomada**, sem reescrever suas evidências. T036 não foi iniciada e permanece aberta.

Confirmados novamente raiz `C:/Users/ronal/dscommerce-speckit`, branch `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd`, seleção local008 e exatamente os sete modificados recebidos: README.md; quickstart.md/tasks.md da008; AdminProductSecurityChainWiringTest.java, OwnOrderQuerySecurityConfigurationTest.java, OrderSecuritySliceTestConfig.java e AdminProductDeletionIntegrationTest.java. Nada staged ou não rastreado. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`. T035, dependência T034, procedimentos e pendência registrados foram relidos. check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks confirmou FEATURE_DIR008; requirements.md16/16 concluídos, zero aberto, somente leitura. Ignore Java adequado e extensions.yml ausente, sem hooks.

### Ajustes editoriais e precedência preservada

| Arquivo | Incremento desta retomada |
| --- | --- |
| [payment-registration-openapi.yaml](contracts/payment-registration-openapi.yaml) | Prefixo editorial de info.description informa implementado e testado localmente em Windows/Java21/H2; declara ausência de validação remota/PostgreSQL e integração em main. x-status passa de planned para implemented-and-validated-locally. Versão0.1.0 preservada. |
| [own-orders-openapi.yaml](contracts/own-orders-openapi.yaml) | Mesmo estado/limites locais, mantendo o snapshot completo como contrato vigente para a revisão008 validada localmente. Substituição do contrato007 continua condicionada à entrega008 na revisão/ambiente considerado. x-status editorial atualizado; versão0.2.0 e x-effective-after-delivery intactos. |
| [README dos contratos](contracts/README.md) | Estado, tabela de referência, texto de vigência, título editorial e identificação das evidências locais alinhados à entrega comprovada. Contrato007 permanece histórico das revisões anteriores, sem dois snapshots alternativos simultaneamente vigentes. Aplicação em outro ambiente não é presumida. Corpo normativo e parágrafo de origem mantidos. T036, remoto e PostgreSQL continuam pendentes. |
| [README da raiz](../../README.md) | Preservados links008, descrição pública, resultados e limites recebidos; apenas o parágrafo de impedimento foi alinhado à vigência local e à condição de aplicação após entrega008. |
| Este quickstart e [tasks.md](tasks.md) | Somente este registro adicional e, após conferência dos critérios, marcador T035. Matriz dos30 cenários e registros T033/T034 preservados. T036 aberta. |

editorial-check.py exit0 comparou os dois YAML com cópias recebidas: somente os dois prefixos de descrição e x-status diferem; a reversão dessas substituições recompõe exatamente os bytes originais, inclusive quebras de linha. Todo o restante é idêntico: rotas, métodos, schemas, campos/tipos, regras, respostas, exemplos, segurança, requisitos, origem e demais extensões, incluindo x-effective-after-delivery. No README dos contratos, corpo normativo de POST/erros/parser/consultas/commit e parágrafo de origem são byte-idênticos. A conferência do texto de precedência mantém substituição integral após entrega008 e007 aplicável às revisões anteriores; não remove a condição nem anuncia integração em main. É comparação documental, sem parser/validador OpenAPI ou teste funcional novo.

### Evidências reutilizadas, procedimentos e limites

Mantidos os comandos, revisão **S**, execução **F**, catálogo e matriz dos30 cenários da seção T035 acima. Os258 arquivos de fonte/teste/build atuais são byte-idênticos aos hashes da FINAL aprovada; nenhum dos quatro Java da T034 recebeu alteração. As bases e os resultados reutilizados continuam distintos:

| Evidência existente | Resultado preservado | Exit/conclusão |
| --- | --- | --- |
| FOCAL-T034R-20261023T170156614 | 307 aprovados,0 falhas/erros/ignorados;6 XML+6 TXT; casos incluídos na FINAL, sem soma | Maven0 capturado antes do arquivamento; BUILD SUCCESS |
| FINAL-T034R-20261023T170507809 (**F**, revisão **S**) | 4957 aprovados,0 falhas/erros/ignorados;137 XML+137 TXT; suíte completa; aninhados/herança/omissões/duplicatas já reconciliados na T034/T035 | Maven0 capturado antes do arquivamento; BUILD SUCCESS |
| FINAL-T034-20261023T162000739, anterior às correções | 4957/39/218/0, reprovação histórica e recuperação preservadas | BUILD FAILURE; exit Maven indisponível/null, runner1; nenhum exit atribuído retroativamente ao Maven |

**Nenhum Maven, JUnit, aplicação ou validador estrutural foi executado nesta retomada.** Reutilizados os recibos/conferências existentes, sem repetir verify-evidence.py, matrix.py ou os validadores OpenAPI. Como o conteúdo normativo não mudou, permanece aplicável a validação estrutural documental de2026-10-21 identificada acima; ela não prova runtime. Comparação de bytes, hashes, recibos e links nesta retomada apenas comprova preservação/coerência documental.

Recibo novo ignorado `target/feature008-evidence/T035R-20261023/`: prepare.py exit0 salvou362 hashes rastreados, cópias recebidas dos seis documentos afetáveis, inventário/hash dos1042 relatórios Surefire e tamanho/mtime dos12871 arquivos de evidência anteriores. Comando efetivo dos auxiliares documentais: `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T035R-20261023/<auxiliar>.py`, para prepare.py, editorial-check.py e review.py. Outros procedimentos: comandos Git de raiz/branch/HEAD/status/índice/não rastreados/main, leitura da seleção local, pré-requisitos, checklist, artefatos e recibos; revisão de diff/referências/links e git diff --check. Não há novo comando funcional disfarçado de revisão documental.

O estado comprovado é **implementado e validado localmente em Windows/Java21/H2**, sem validação remota008, PostgreSQL ou integração em main. A documentação de vigência nesta revisão não anuncia implantação em outro ambiente. Permanecem os limites de SQL/snapshots, sensor JDK, concorrência H2, falhas controladas, MockMvc versus socket e commit/transmissão já registrados; nenhuma prova foi ampliada para produção ou outro SGBD. T036 continua trabalho futuro verdadeiro. Não se disparou CI, consulta remota, stage, commit, push, PR ou merge.

O Gate IV da Feature005 permanece **EXCEÇÃO DOCUMENTADA**, conforme [plano005](../005-manter-produtos/plan.md), sem promoção a PASS estrito ou herança pela008. Artefatos/contratos históricos001–007, implementação, testes, asserções, prefixo recebido deste quickstart e evidências anteriores preservados. A matriz dos30 cenários continua íntegra e vinculada à mesma FINAL aprovada, sem apresentação de revisão documental como nova comprovação funcional.

Os critérios restantes da T035 estão atendidos: resultados/matriz/comandos/revisão/relatórios já consolidados, README aponta POST008 e snapshot completo vigente na revisão local, precedência e limites explícitos, metadados compatíveis com as provas e conteúdo normativo intacto. Após a revisão documental abaixo, somente T035 passa a[X]; T036 permanece aberta, sem antecipar o fechamento global.

Revisão documental executada nesta retomada: editorial-check.py exit0, review.py exit0/PASS e `git diff --check` exit0, sem diagnóstico de whitespace. Conferidos o incremento contra as cópias recebidas, diff completo e diff dos contratos,223 links Markdown locais/âncoras e11 referências locais dos metadados YAML. Confirmados356 rastreados fora dos seis documentos idênticos,258 fontes/testes/build iguais à FINAL,58 artefatos históricos001–007 intactos, prefixo recebido de564416 bytes e matriz dos30 cenários preservados. Os1042 relatórios Surefire permanecem byte-idênticos e os12871 arquivos de evidências anteriores conservam tamanho/mtime, incluindo a revisão T035 interrompida pelo impedimento e ambas as FINAL. Auxiliares novos foram revisados e ficam somente em target ignorado; nenhum arquivo novo não rastreado. Recibos editorial-check.json, preliminary-review.json/final-review.json, reviewed.diff, contracts-reviewed.diff e quickstart-increment.md ficam na pasta T035R nova, sem sobrescrever recibos anteriores.

**T035 concluída; T036 aberta.** tasks.md difere do recebido somente no marcador T035; T033/T034 e todos os demais marcadores preservados. Estado Git final: dez modificados — README.md; contracts/README.md, own-orders-openapi.yaml, payment-registration-openapi.yaml, quickstart.md e tasks.md da008; os quatro Java recebidos da T034, sem nova edição. Índice vazio, nenhum não rastreado; raiz/branch/HEAD/main/seleção008 mantidos. Sem impedimento restante da T035; pendentes T036 e as validações remota/PostgreSQL/integração em main, sem afirmar execução ou autorização para essas etapas. A exceção histórica do Gate IV005 permanece documentada.

## T036 — revisão final e encerramento local (2026-10-23)

Executada exclusivamente [T036](tasks.md), dependente da T035 concluída. Confirmados inicialmente raiz `C:/Users/ronal/dscommerce-speckit`, branch real `008-registrar-pagamento`, HEAD `96126dbfb8c01cbe8ae6b005d6226a6c7503bffd`, seleção local008 e exatamente os dez arquivos modificados informados: README.md; quickstart.md/tasks.md, contracts/README.md e os dois YAML da008; AdminProductSecurityChainWiringTest.java, OwnOrderQuerySecurityConfigurationTest.java, OrderSecuritySliceTestConfig.java e AdminProductDeletionIntegrationTest.java. Nada staged ou não rastreado. Main permanece `5cd16709ea531ccae27230db19753e6c5533d12d`. Nenhuma alteração recebida foi descartada.

T036, dependência T035, procedimentos aprovados, artefatos e registros históricos foram lidos/revistos. A skill speckit-implement foi aplicada somente a este fechamento; pré-requisitos confirmaram FEATURE_DIR absoluto008 e documentos esperados. Checklist requirements.md16/16 concluídos, zero aberto, somente leitura. Ignore Java suficiente; extensions.yml ausente antes/depois, sem hooks. Não houve chamada Maven, JUnit, compilação, aplicação, banco ou validador estrutural: **esta T036 é revisão documental/estática**, reutilizando os resultados já concluídos e sem nova comprovação funcional.

### Revisão do diff, implementação e rastreabilidade

Revisto o diff completo contra HEAD dos dez modificados, inclusive todos os494 acréscimos recebidos no quickstart, os quatro Java e as alterações editoriais dos contratos; o incremento desta T036 é conferido separadamente contra cópias recebidas. Preservados o prefixo histórico integral, a matriz final dos30 cenários e os registros de reprovação/recuperação/correção. Artefatos de planejamento descrevem sua fase histórica e não são tratados como aprovação funcional atual. Uma nota de encerramento em tasks.md distingue o estado atual desses textos de geração, sem reescrever requisitos, tarefas ou procedimentos históricos.

| Recorte conferido | Resultado e fundamento reutilizado |
| --- | --- |
| Spec/plano/modelo/pesquisa, constituição e rastreabilidade |30 cenários (6+9+5+4+6),22 FR e8 SC identificados nas matrizes; audit.py exit0 cruzou IDs de tasks/spec/quickstart com scenario-matrix.json existente, classes/métodos/fontes e os recibos da FINAL. São21 classes concretas/91 referências método-contexto; herança inclui suportes package-private no mesmo arquivo Java, sem presumir um arquivo por classe. Não se executou descoberta JUnit ou os testes. |
| Operação administrativa e invariantes | Controller mantém POST com Long positivo/@Valid, somente occurredAt, query rejeitada e201 após retorno do proxy. Serviço mantém referência nativa única antes de consultas/esperas, ADMIN escalar atual antes/depois de lock, Payment anterior antes do estado, janela inclusiva, cálculo histórico exato e persist+PAID/flush na transação. Fragments fazem find+refresh PESSIMISTIC_WRITE por raiz e persist explícito; Payment usa MapsId/PK/FK/NOT NULL/precisão3. Leituras/revisão da T033 e provas da FINAL continuam aplicáveis pelos hashes, sem nova execução. |
| Correções T034 | Conferidas asserções explícitas das cinco cadeias/matchers/ordem/políticas, mocks PaymentAccessService com FORBIDDEN nos contextos restritos e nenhuma interação indevida nas provas de consultas. Exclusão conserva criação WAITING_PAYMENT e prepara PAID por POST administrativo real, com Payment/JDBC/histórico e limpeza seletiva. Nenhum filtro, autorização, provider ou asserção foi enfraquecido. Os quatro arquivos permanecem iguais aos testados. |
| Consultas próprias e preservações | Snapshot008 completo admite WAITING_PAYMENT/PAID e mantém campos/ownership/contagens/paginação/ordenação/dinheiro; ADMIN pagador não recebe leitura alheia. CenáriosUS5 e regressões001–007 estão incluídos na FINAL, sem soma de checkpoints. H2 Console e JWT permanecem íntegros, com resultados já registrados na T034. |
| Vigência e conteúdo normativo | README aponta POST0080.1.0 e consultas0080.2.0 como substituto integral após entrega na revisão/ambiente considerado, sem alternativas simultâneas. Ambos os YAML são byte-idênticos ao recebido e aos hashes aprovados na T035; nenhuma rota, método, schema, campo/tipo, regra, resposta, exemplo, segurança ou condição de precedência mudou. A validação estrutural anterior é reutilizada; não foi repetida. |
| Gates e limites | Reutilizada auditoria008 da T033 em conjunto com FINAL posterior aprovada, sem herdar exceções. O **Gate IV da Feature005 permanece EXCEÇÃO DOCUMENTADA**, conforme [plano005](../005-manter-produtos/plan.md), sem PASS estrito. Não se alteraram plano005, constituição ou contratos históricos001–007. |

Os únicos ajustes editoriais desta revisão, além do registro e marcador T036, removem avisos atuais de T036 pendente no README da raiz e no README dos contratos, substituindo-os por links para este fechamento local. Preservados integralmente os demais ajustes T035, inclusive estado implemented-and-validated-locally nos YAML, precedência e limites. As declarações históricas de tarefas então abertas nas seções anteriores permanecem como registros daquelas execuções; este é o estado posterior identificado.

### Resultados existentes reutilizados e ambientes

| Execução existente | Resultado e exit preservados | Alcance |
| --- | --- | --- |
| FOCAL-T034R-20261023T170156614 |307 aprovados, zero falhas/erros/ignorados; BUILD SUCCESS, Maven0 capturado antes do arquivamento | Seis classes afetadas;6 XML+6 TXT. Incluído nos4957, sem soma. |
| FINAL-T034R-20261023T170507809 |4957 aprovados, zero falhas/erros/ignorados; BUILD SUCCESS, Maven0 capturado antes do arquivamento | Suíte completa sem filtro;137 XML+137 TXT,127 containers principais+10 aninhados,906 grupos método-classe/212 herdados, sem omissões/duplicatas conforme reconciliação existente. |
| FINAL-T034-20261023T162000739, anterior às correções |4957/39/218/0, BUILD FAILURE; **exit do runner1 e exit Maven indisponível/null** | Console,274 relatórios recuperados e80 arquivos da cópia parcial preservados. O exit1 do runner não foi atribuído ao Maven; nenhuma aprovação retroativa. |

Comandos, revisão **S** (HEAD mais quatro correções de teste T034), horários e relatórios permanecem identificados na T034/T035 acima e em `target/feature008-evidence/T034R-20261023/{FOCAL,FINAL}/`. Os258 arquivos atuais de fonte/teste/build coincidem byte a byte com sources-before.json da FINAL, inclusive os quatro testes/suportes corrigidos. Os ajustes documentais posteriores não invalidam essa aplicabilidade nem constituem nova prova funcional. Não houve reexecução de Maven, validadores OpenAPI, verify-evidence.py, matrix.py ou dos leitores antigos para reconstruir resultados.

**Conclusão local Windows/Java21/H2**: implementação, provas e revisão satisfeitas no alcance registrado. **Validação remota008 ainda não realizada**, **PostgreSQL não validado** e **integração em main pendente** são estados distintos. CI Linux histórica do harness não aprova esta revisão. Não se inferem durabilidade de produção, crash recovery, precisão/locks de outro SGBD ou garantia de rollback quando o resultado do commit é indeterminado. Buffer MockMvc, socket/proxy HTTP e JDBC mantêm alcances próprios;20 janelas/60 hooks JDK não equivalem a60 testes ou observação universal de integrações/carrinho. Recibos locais sob target são ignorados pelo Git e precisam de preservação/transferência própria para outro checkout; este fechamento não os publica remotamente.

### Procedimentos, preservação e conclusão

Recibo novo ignorado `target/feature008-evidence/T036-20261023/`: prepare.py exit0 guardou362 hashes rastreados, cópias dos dez modificados, diff recebido completo,1042 hashes Surefire e tamanho/mtime dos12890 arquivos de evidência anteriores. audit.py exit0 confirmou aplicabilidade da revisão testada, rastreabilidade, recibos e integridade; não chama aplicação ou validador. Sua primeira leitura supôs arquivo próprio para PaymentRegistrationScenarioSupport e encontrou FileNotFoundError; o suporte package-private está em PaymentRegistrationHttpIntegrationTest.java. A busca foi corrigida para indexar declarações e herança, com a tentativa anterior preservada em audit-attempt1.py. Foi problema do leitor estático, sem falha ou repetição de teste da aplicação.

Comandos efetivos desta T036: Git de raiz/branch/HEAD/status/índice/não rastreados/main; seleção local; check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks; Get-Content/rg e git diff dos documentos/fontes; auxiliares documentais externos por `C:/Users/ronal/AppData/Local/uv/cache/archive-v0/In_Ay6QzfEkllxlV/Scripts/python.exe -B -I -X utf8 target/feature008-evidence/T036-20261023/<auxiliar>.py`, para prepare.py, audit.py e review.py; conferência de links/âncoras/referências/arquivos novos e `git diff --check`. Não houve consulta/disparo remoto, stage, commit, push, PR, merge ou alteração de main.

Critérios da T036 satisfeitos após a conferência final abaixo: diff completo e incremento revistos, referências/links/whitespace e não rastreados conferidos, alterações restritas ao escopo, vigência coerente, fontes/contratos/evidências preservados e limitações explícitas. **Somente T036 recebe o novo marcador; T001–T036 ficam concluídas,36/36, no âmbito local documentado.** Não há impedimento de fechamento local identificado nem aprovação remota/PostgreSQL/main presumida.

Conferência final executada: review.py e review.py --marked exit0/PASS; `git diff --check` exit0, sem diagnóstico de whitespace. Revistos diff completo e incremento,281 links locais/âncoras em9 Markdown,11 referências locais dos YAML e62 caminhos Java explícitos em tasks.md. Confirmados358 rastreados fora dos quatro documentos de fechamento idênticos,258 fontes/testes/build iguais aos da FINAL,58 artefatos históricos001–007 intactos e prefixo recebido de573814 bytes do quickstart preservado. Matriz dos30 cenários, os dois YAML, correções T034 e ajustes T035 mantidos, exceto os avisos editoriais de conclusão local já identificados. Os1042 relatórios permanecem byte-idênticos e os12890 arquivos de evidência anteriores conservam tamanho/mtime. Nenhum arquivo novo não rastreado; novos auxiliares/recibos sob target ignorado foram revisados. received.diff, audit.json, preliminary-review.json, final-review.json, reviewed.diff e quickstart-increment.md conservam as conferências desta T036 sem sobrescrever evidências anteriores.

**T036 concluída;36/36 tarefas únicas T001–T036 marcadas, zero aberta.** O novo marcador é somente T036; os35 anteriores permanecem intactos. Estado Git final: os mesmos dez modificados recebidos, índice vazio e nenhum não rastreado. Nesta T036, apenas README.md, contracts/README.md da008, quickstart.md e tasks.md receberam os ajustes de fechamento; os quatro Java e os dois YAML ficaram byte-idênticos. Raiz/branch/HEAD/main/seleção008 preservados. Encerramento local Windows/Java21/H2 registrado, com validação remota008 ainda não realizada, PostgreSQL não validado e integração em main pendente. Gate IV005 continua EXCEÇÃO DOCUMENTADA. Nenhum stage, commit, push, PR, merge ou alteração de main.
