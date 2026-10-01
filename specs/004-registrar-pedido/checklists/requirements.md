# Specification Quality Checklist: Registrar pedido

**Purpose**: Validar a qualidade e a completude da especificação antes do planejamento.

**Created**: 2026-10-01

**Feature**: [spec.md](../spec.md)

**Marker Semantics**: `[x]` registra um critério de qualidade documental revisado e atendido; não comprova implementação, teste executado ou entrega do comportamento.

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Revisão documental: após Q1, o checklist passou de 12/16 para 16/16; no fechamento deste checkpoint, permanece 16/16. Nenhuma checkbox mudou de estado e nenhuma regressão de qualidade foi identificada. A1 e a regra de preço estritamente positivo estão aprovadas; A2 e A3 são assunções adotadas para planejamento, sem nova declaração de aprovação explícita do usuário. O resultado documental não demonstra implementação.
- **FR-017 resolvida**: Cada solicitação válida cria um novo pedido com identificador próprio, inclusive com itens iguais aos de uma solicitação anterior e após perda da resposta de um pedido salvo. Clarifications registra a decisão aprovada; US1.9/10 e SC-008 definem sua aceitação. Reconhecimento de repetição e idempotência estão fora do escopo.
- **A1 aprovada**: FR-014 e US2.8 exigem rejeição integral do mesmo produto em mais de uma linha, sem consolidação de quantidades e sem salvar pedido ou itens. **Preço no pedido aprovado**: FR-005, US2.11/12 e SC-002/009 exigem preço vigente estritamente positivo e rejeição integral de zero ou negativo. US2.13 cobre um item inválido entre itens válidos. **A2 e A3 adotadas para planejamento**: preço vigente obtido pelo sistema no registro, sem congelamento no carrinho ou segunda confirmação; subtotal = preço registrado × quantidade; total = soma dos subtotais, sem frete, descontos ou acréscimos. US1.5 explicita que o preço alterado permanece estritamente positivo e mantém a rejeição integral quando zero ou negativo.
- **Permissões**: FR-001 e US1.2 incluem Admin sem Cliente; FR-011 e US2.6 rejeitam contas sem Cliente nem Admin. FR-003 e US2.7 impedem escolher outro titular.
- **Integridade**: FR-012, US2.1–13 e SC-004/009 cobrem rejeições integrais, inclusive produtos repetidos, preços zero ou negativos e um item inválido entre itens válidos, além de falha interna sem gravação parcial. Conforme Q1 aprovada, resposta perdida após registro completo não desfaz a compra; outro envio válido cria outro pedido e preserva o primeiro.
- **Valores e conservação**: FR-005/008/009, US1.1/5/6 e SC-002/003 cobrem preço, cálculos e preservação após mudança do catálogo sem introduzir consulta de pedidos.
- **Regressões**: FR-015, US3 e SC-006 referenciam as features 001–003. Nenhum cenário foi declarado executado nesta etapa.
- **Fronteira do carrinho**: FR-002/016 e US1.7 mantêm sua gestão no consumidor e o esvaziamento na futura interface. A especificação não promete essa pós-condição no backend.
- O critério sobre resultados mensuráveis foi avaliado como cobertura documental dos resultados por cenários e critérios SC-001–009, não como comprovação de resultados em execução.
- As seções obrigatórias seguem o template resolvido; endpoints, classes, banco, algoritmo de autorização, transações e soluções de validação não foram definidos neste artefato.
- **Prontidão e fronteiras**: Não há pendência de revisão de A2/A3 ou marcador de clarificação na especificação. A2/A3 estão adotadas como assunções, sem nova declaração de aprovação explícita do usuário. Product atualmente aceita zero; o alinhamento do cadastro fica para etapa própria, sem alterar Product, catálogo, detalhes ou suas validações neste checkpoint. Limites de quantidade e representação dos totais permanecem assuntos do planejamento. A especificação está pronta para planejamento; esta etapa termina com o commit documental, sem executar esse workflow.
- **Verificações documentais do checkpoint anterior de especificação (históricas)**: leitura integral dos arquivos novos, conferência dos links locais (todos existentes), ausência de placeholders do template, contagem de 17 requisitos, 7 critérios de sucesso e 22 cenários de aceitação, conferência do ponteiro local e `git diff --check` sem erros. Os arquivos novos também foram examinados com `git diff --no-index --check -- /dev/null <arquivo>`: código 1 pela diferença em relação ao arquivo vazio, sem diagnóstico de whitespace; houve aviso de conversão futura de LF para CRLF pela configuração local do Git. Não houve execução de testes da aplicação; os resultados históricos das features anteriores não aprovam esta feature.
- **Hooks**: `.specify/extensions.yml` ausente nas conferências anterior e posterior à especificação; nenhum hook executado. Planejamento, tasks e implementação não foram iniciados, conforme o limite solicitado.
- **Verificações anteriores de esclarecimento de Q1 (históricas)**: revisão do diff contra o conteúdo anterior daquela sessão, confirmação de que FR-001–016, US2, US3 e o texto de A1–A3 permaneciam inalterados; uma única resposta aprovada registrada em Clarifications; ausência de marcador de clarificação na especificação; 17 requisitos, 8 critérios de sucesso e 24 cenários de aceitação. Links locais conferidos e existentes; `git diff --check` e revisão de whitespace dos documentos não rastreados sem diagnósticos. Não houve nova execução de testes da aplicação. `.specify/extensions.yml` também ausente nas conferências de esclarecimento; nenhum hook executado.
- **Verificações anteriores de A1 e preço no pedido (históricas)**: duas decisões fornecidas pelo usuário integradas, sem novas perguntas; 17 requisitos, 9 critérios de sucesso e 27 cenários de aceitação. Q1 (registro em Clarifications, FR-017, US1.9/10 e SC-008), os textos então vigentes de A2/A3 e US3 conferidos e preservados. Checklist reavaliado: 16/16 → 16/16, sem alteração de markers nem regressões. Links locais existentes; `git diff --check` e revisão dos arquivos não rastreados sem diagnósticos de whitespace, com aviso de conversão futura LF/CRLF. Product lido naquela revisão e seu hash conferido sem alteração; `git diff --exit-code -- src/main src/test` confirmou ausência de diff de código e testes. Nenhum teste da aplicação foi executado; os resultados anteriores permanecem históricos. `.specify/extensions.yml` ausente antes e depois; nenhum hook executado. A referência ao estado atual de Product documenta uma dependência e a fronteira de escopo solicitadas, sem definir solução técnica de alinhamento do cadastro.
- **Verificações do fechamento deste checkpoint (atuais)**: leitura integral de AGENTS.md e dos dois documentos ainda não rastreados; branch real `004-registrar-pedido`, HEAD inicial `8203e97f277211bc5ed9ac3e42c719e723f7720e` e alterações existentes conferidos antes da edição. Revisão de consistência: A2/A3 adotadas para planejamento, sem pendência de revisão ou nova aprovação explícita; US1.5 exige novo preço estritamente positivo. Os 17 requisitos, 9 critérios de sucesso, decisões em Clarifications e cenários de US2/US3 foram comparados ao conteúdo inicial e preservados; os 27 cenários permanecem e o checklist foi reavaliado em 16/16. Os 6 links locais existem. `git diff --check`, `git diff --no-index --check -- /dev/null <arquivo>` para cada documento novo e revisão de whitespace não apresentaram diagnósticos de erro; houve apenas aviso de conversão futura LF/CRLF. `git diff --exit-code -- src/main src/test` confirmou ausência de alterações nesses caminhos. Nenhum teste da aplicação foi executado; plan.md e tasks.md da feature estão ausentes. As evidências anteriores permanecem identificadas como históricas.
