# Data model: Registrar pagamento

**Estado**: modelo futuro da008; nenhuma entidade, schema ou dado foi alterado. [Spec](spec.md), [pesquisa R3–R6](research.md), [plano](plan.md).

## Estado atual observado

[Order](../../src/main/java/com/devsuperior/dscommerce/entities/Order.java) usa tb_order(id,buyer_id,created_at,status), com WAITING_PAYMENT e coleção de itens ordenada por position. [OrderItem](../../src/main/java/com/devsuperior/dscommerce/entities/OrderItem.java) registra product_id, quantity, unit_price decimal19,2 e position. Quantidade/preço históricos geram subtotais e total; catálogo atual não participa. Não existe entidade/tabela Payment nem PAID no enum atual.

## Entidades e relações propostas

| Entidade/campo | Representação | Validação/invariante |
| --- | --- | --- |
| Payment.orderId | Long; PK tb_payment.order_id; @MapsId do pedido | Int64 positivo; PK impede dois registros por pedido. Não é ID de pagamento independente. |
| Payment.order | @OneToOne LAZY obrigatório para Order | FK nomeada fk_payment_order; order_id NOT NULL; sem cascade REMOVE/ALL ou criação implícita de pedido. |
| Payment.occurredAt | Instant; occurred_at TIMESTAMP(3) WITH TIME ZONE NOT NULL; @FractionalSeconds(3) | Apenas instantes representáveis exatamente em milissegundos, parseados e validados antes de persistir. |
| Order.status | OrderStatus STRING existente | Acrescentar PAID; criação continua WAITING_PAYMENT. Método markPaid só aplicado após decisões do serviço. |
| Order comprador/criação/itens | Campos e relações existentes | Nenhuma substituição, mudança de dono, reordenação ou reprecificação. |
| Referência da tentativa | Instant local, precisão nativa de Clock | Não persistir, não receber nem devolver; fixar uma vez na primeira instrução de negócio, antes de consultas/esperas. |

Order não precisa de associação inversa Payment nem de nova coluna version. Isso conserva o schema de quatro colunas esperado em OrderRepositoryTest. Hibernate deve gerar a constraint de enum de status com ambos os valores; sua inspeção é obrigatória na implementação. Não criar outros estados.

Cardinalidade: Order 1 — 0..1 Payment; Payment pertence a exatamente um Order. PK/FK/NOT NULL devem ser comprovados pelo schema real e tentativas JDBC independentes. FK sem remoção em cascata impede apagar silenciosamente quitação ao remover pedido.

Inserção de Payment é explícita por EntityManager.persist em fragmento PaymentInsertRepository, com associação ao pedido gerenciado e chave fornecida por MapsId. Não usar merge/save de entidade com ID atribuído: uma quitação existente não pode ser tratada como alvo de atualização. Colisão final de PK deve falhar, não sobrescrever ocorrência.

## Valores e limites

Não persistir amount, subtotal, total, moeda, comprador ou preço atual em Payment. A quitação cobre integralmente o total histórico do pedido: soma de unit_price * quantity. Usar BigDecimal, setScale(2, UNNECESSARY) e capacidade decimal19,2 existente, sem conversão por double. Valores fora de invariantes históricos são falha interna500, não convite para corrigir pedido nem entrada arbitrária aceita.

Consulta de itens para pagamento retorna projeção escalar interna com productId, position, quantity e unitPrice, ordenada por position, sob a transação e depois do lock de raiz. Não é DTO público nem regra no repository. O serviço calcula/valida exatidão, posições, quantidades e preços históricos; não busca Product nem valida nome/categoria/preço atuais. O cálculo não altera as linhas.

## Semântica temporal exata

A referência é capturada antes de qualquer consulta que possa esperar, inclusive guardas de conta e lock do pedido. Ela não é truncada, reformatada ou atualizada. Receita de negócio preservada: criação <= ocorrência <= referência, inclusive igualdades. O instante do JWT, recebimento e commit não são substitutos.

O contrato aceita segundos e offset explícitos, ano0001–9999, fração opcional até9 dígitos, somente quando nano % 1000000 == 0. Entrada .123000000 equivale a .123; .123000001 é rejeitada400. Converter offset preservando o mesmo Instant, e validar também faixa de ano em UTC. Rejeitar leap second, -00:00, hora24, ausência de offset e espaços. Não fazer trim ou arredondamento.

Saída occurredAt é UTC canônica com três casas; criação nos GETs conserva o formato existente. TIMESTAMP(3) deve round-tripar exatamente a ocorrência aceita; não supor isso somente pela anotação. Referência com nanos nativos é comparada diretamente; igualdade superior só tem representação de entrada quando essa referência é exata em milissegundos. Isso não autoriza reduzir reference para fazer igualdade caber.

## Máquina de estados e invariantes

| Estado lido após lock/refresh | Payment existente | Resultado |
| --- | --- | --- |
| WAITING_PAYMENT | Não | Se ADMIN/tempo/dados válidos: inserir Payment e alterar somente status para PAID. |
| Qualquer, inclusive WAITING_PAYMENT | Sim | 409 Payment already registered; conservar ocorrência original; sem correção. |
| Diferente de WAITING_PAYMENT | Não | 409 Order is not awaiting payment; sem criar/reparar. |
| Pedido ausente | Não aplicável | 404 Order not found, somente após autorização. |

A PK/FK não impõe sozinha equivalência entre PAID e Payment nem janela temporal entre tabelas. Essas são garantias da operação de serviço e sua transação; SQL arbitrário que contorne o serviço pode produzir estado incoerente, que não será reparado automaticamente. Não adicionar triggers ou novos fluxos para isso.

## Acesso e transação propostos

PaymentRegistrationService, via proxy REQUIRED/READ_COMMITTED/rollbackFor Exception, captura referência primeiro. PaymentAccessService consulta projeção escalar de perfis antes de pedido e após o lock, imediatamente antes da mutação. Não usar roles/name do token ou coleção de conta já carregada como autoridade final. Falha de lookup500, conta ausente401 e sem ADMIN403.

OrderPaymentLockRepositoryImpl usa find(id,PESSIMISTIC_WRITE), seguido de refresh(order,PESSIMISTIC_WRITE) antes de acessar estado/criação. Sem coleção, DISTINCT, titular ou predicado WAITING_PAYMENT na aquisição. A segunda transação no mesmo pedido deve esperar e enxergar o pagamento/estado concluído; READ_COMMITTED e refresh evitam decidir por snapshot de entidade antigo. Consultas de Payment e projeção histórica ocorrem depois do lock. Sem locks de produto ou conta que serializem pedidos distintos.

A transação insere Payment, muda status, faz flush e mapeia DTO; o proxy que possui a transação conclui commit antes de devolver ao controller. Não capturar exceção para continuar consultando ou gravando após erro de banco. Nenhum retry automático.

## Fronteiras e evolução007

Antes da efetivação conhecida, falha desfaz alterações desta tentativa. Após commit efetivo, falha de resposta não apaga Payment/PAID. Falha com resultado de commit desconhecido não é prova de rollback; teste de exceção antes de delegar commit tem alcance menor que falha real do banco.

DTO de confirmação contém somente orderId, occurredAt e PAID. DTOs da007 não ganham Payment ou dados novos. [Snapshot008 das consultas](contracts/own-orders-openapi.yaml) admite WAITING_PAYMENT e PAID nos dois status após entrega, substituindo o contrato007 conforme [README](contracts/README.md). Conteúdo/contagens/detalhe continuam próprios, inclusive para ADMIN que quitou pedido alheio.

## Evolução e dados de teste

Aplicação atual é H2 em memória/create; não há migração de produção neste planejamento. A implementação deve gerar e inspecionar a nova tabela, FK/PK/NOT NULL, precisão3 e enum ampliado sem mutar dados históricos. Se outro banco/persistência durável for adotado, será necessária validação própria e eventual migração fora da prova H2.

Fixtures isoladas inserem dados commitados antes do request. Setup de leituras PAID precisa de Payment coerente; limpar somente pagamentos da fixture antes de remover seus pedidos. Probe008 inclui tb_payment e fotografia anterior existente; não mudar o significado dos probes históricos para alegar cobertura que eles não tinham.
