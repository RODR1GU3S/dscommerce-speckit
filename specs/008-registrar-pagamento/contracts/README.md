# Contratos da Feature 008

**Estado: implementado e validado localmente em Windows/Java21/H2; sem validação remota, PostgreSQL ou integração em main.** As evidências da FINAL aprovada e a matriz dos30 cenários estão no [quickstart](../quickstart.md#t035--consolidação-documental-das-evidências-2026-10-23). O [fechamento local T036](../quickstart.md#t036--revisão-final-e-encerramento-local-2026-10-23) conclui as36 tarefas, sem ampliar esses limites.

| Interface | Contrato vigente na revisão008 validada localmente | Origem e aplicação |
| --- | --- | --- |
| Registro de quitação | [payment-registration-openapi.yaml](payment-registration-openapi.yaml), versão0.1.0 | Operação introduzida pela008; ausente na revisão anterior. |
| GET/HEAD de pedidos próprios | [own-orders-openapi.yaml](own-orders-openapi.yaml), versão0.2.0, WAITING_PAYMENT e PAID | Substituto completo após entrega008; [contrato007](../../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml), versão0.1.0, só WAITING_PAYMENT, preservado como histórico das revisões anteriores. |

O snapshot008 é completo e **substitui integralmente o contrato de consultas007 na revisão008 implementada e validada localmente**. A condição de aplicação permanece: a substituição ocorre após a entrega008 na revisão/ambiente considerado; até essa entrega,007 permanece o contrato da revisão anterior. Não são dois contratos alternativos simultaneamente vigentes. Os apontamentos desta revisão usam008 e ambos os arquivos declaram x-status implemented-and-validated-locally, preservando007 como histórico, sem reescrever sua especificação ou evidências. A vigência documental local não declara implantação em outro ambiente, validação remota/PostgreSQL ou integração em main.

O snapshot deriva do arquivo007 byte-preservado nesta etapa. Mudanças permitidas no snapshot: title/version/description e referências de metadados para008, origem/precedência explícitas, enums dos dois status, descrições de estado e exemplos PAID. x-origin-requirements identifica FR da especificação007 de origem; x-requirements identifica FR-016–021 da008, sem misturar os dois espaços de IDs. Campos fechados, parâmetros, respostas, formatos monetários, ownership e demais restrições mantêm a semântica anterior. Não há $ref externo que herde o enum antigo.

## Registro administrativo

POST /admin/orders/{id}/payments, relativo ao contexto servlet. ADMIN atual, isolado ou com CLIENT, pode qualquer titular somente nessa operação. ID positivo int64 no caminho, corpo somente occurredAt; nenhum query parameter. 201 confirma somente orderId/occurredAt/status PAID, depois do commit; não há Location ou GET administrativo de pagamento.

Erros de aplicação têm exatamente status/error/message/path. Mensagens fixas por categoria no OpenAPI. Autenticação precede MVC; perfis e conta são relidos no serviço. Só401 traz WWW-Authenticate: Bearer. 409 de repetição não devolve a confirmação original. 400 cobre sintaxe/estrutura e janela temporal;500 cobre falhas internas e operacionais. 405/406/415 são erros nativos de método/negociação, com status/headers preservados, sem promessa de envelope JSON fixo.

RFC3339 restrito com offset explícito e fração até9, somente sem valor submilissegundo; zeros adicionais podem ser normalizados sem perda. Datas reais, faixa de ano UTC, offset, ausência de -00:00/leap second e precisão exata exigem parser estrito além do schema. O schema não consegue expressar createdAt <= occurredAt <= referência local da tentativa nem rejeitar nomes JSON duplicados; são regras obrigatórias de runtime descritas no contrato e no [plano](../plan.md).

## Preservações e limites

GETs continuam próprios, inclusive para ADMIN que quitou compra alheia; mesmos campos, dinheiro histórico, paginação e ordem por criação/ID. PAID não acrescenta occurrence/payment ao detalhe e a consulta não cria transição.

Um 201 recebido confirma sucesso; ausência de resposta/500 não prova que um commit nunca ocorreu. Falha de transmissão posterior preserva quitação e o novo POST é rejeitado. Sem cobrança, gateway, consulta financeira externa, movimentação de dinheiro ou reversão automática.

[Research](../research.md), [modelo](../data-model.md) e [quickstart](../quickstart.md) registram decisões, ambientes, procedimentos e evidências locais, distinguindo as validações remota e PostgreSQL ainda não realizadas. A validação estrutural de OpenAPI e exemplos continua documental; as provas locais de segurança, SQL, transação e transporte são as execuções identificadas no quickstart, sem extensão automática para outros ambientes.
