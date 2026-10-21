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
