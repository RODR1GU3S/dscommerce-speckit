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
