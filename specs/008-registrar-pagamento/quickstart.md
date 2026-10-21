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
