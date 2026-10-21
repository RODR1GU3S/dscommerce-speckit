# Contratos da Feature 008

**Estado: desenho futuro; nenhum endpoint008 ou enum ampliado foi implementado/validado em runtime nesta fase.**

| Interface | Hoje | Contrato alvo após entrega008 |
| --- | --- | --- |
| Registro de quitação | Ausente | [payment-registration-openapi.yaml](payment-registration-openapi.yaml), versão0.1.0 |
| GET/HEAD de pedidos próprios | [contrato007](../../007-consultar-meus-pedidos/contracts/own-orders-openapi.yaml), versão0.1.0, só WAITING_PAYMENT | [own-orders-openapi.yaml](own-orders-openapi.yaml), versão0.2.0, WAITING_PAYMENT e PAID |

O snapshot008 é completo e **substituirá integralmente o contrato de consultas007 como contrato vigente após a entrega**. Não são dois contratos alternativos simultaneamente vigentes. Até a entrega,007 permanece vigente; ambos os arquivos008 conservam x-status planned. A implementação/publicação deverá apontar documentação e verificações contratuais atuais para o snapshot008, preservando007 como histórico, sem reescrever sua especificação ou evidências.

O snapshot deriva do arquivo007 byte-preservado nesta etapa. Mudanças permitidas no snapshot: title/version/description e referências de metadados para008, origem/precedência explícitas, enums dos dois status, descrições de estado e exemplos PAID. x-origin-requirements identifica FR da especificação007 de origem; x-requirements identifica FR-016–021 da008, sem misturar os dois espaços de IDs. Campos fechados, parâmetros, respostas, formatos monetários, ownership e demais restrições mantêm a semântica anterior. Não há $ref externo que herde o enum antigo.

## Registro administrativo proposto

POST /admin/orders/{id}/payments, relativo ao contexto servlet. ADMIN atual, isolado ou com CLIENT, pode qualquer titular somente nessa operação. ID positivo int64 no caminho, corpo somente occurredAt; nenhum query parameter. 201 confirma somente orderId/occurredAt/status PAID, depois do commit; não há Location ou GET administrativo de pagamento.

Erros de aplicação têm exatamente status/error/message/path. Mensagens fixas por categoria no OpenAPI. Autenticação precede MVC; perfis e conta são relidos no serviço. Só401 traz WWW-Authenticate: Bearer. 409 de repetição não devolve a confirmação original. 400 cobre sintaxe/estrutura e janela temporal;500 cobre falhas internas e operacionais. 405/406/415 são erros nativos de método/negociação, com status/headers preservados, sem promessa de envelope JSON fixo.

RFC3339 restrito com offset explícito e fração até9, somente sem valor submilissegundo; zeros adicionais podem ser normalizados sem perda. Datas reais, faixa de ano UTC, offset, ausência de -00:00/leap second e precisão exata exigem parser estrito além do schema. O schema não consegue expressar createdAt <= occurredAt <= referência local da tentativa nem rejeitar nomes JSON duplicados; são regras obrigatórias de runtime descritas no contrato e no [plano](../plan.md).

## Preservações e limites

GETs continuam próprios, inclusive para ADMIN que quitou compra alheia; mesmos campos, dinheiro histórico, paginação e ordem por criação/ID. PAID não acrescenta occurrence/payment ao detalhe e a consulta não cria transição.

Um 201 recebido confirma sucesso; ausência de resposta/500 não prova que um commit nunca ocorreu. Falha de transmissão posterior preserva quitação e o novo POST é rejeitado. Sem cobrança, gateway, consulta financeira externa, movimentação de dinheiro ou reversão automática.

[Research](../research.md), [modelo](../data-model.md) e [quickstart](../quickstart.md) definem decisões, ambientes e evidências futuras. Validação de OpenAPI e exemplos nesta fase é documental, sem comprovar segurança, SQL, transação ou transporte.
