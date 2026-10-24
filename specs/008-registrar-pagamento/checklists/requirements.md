# Specification Quality Checklist: Registrar pagamento

**Purpose**: Validar completude e qualidade dos requisitos antes do planejamento da Feature 008.

**Created**: 2026-10-21

**Feature**: [spec.md](../spec.md)

**Review Ownership**: Revisão documental dos requisitos neste checkpoint de especificação.

**Marker Semantics**: Uma marca [x] significa critério de qualidade documental revisado e atendido; não significa implementação concluída ou teste executado.

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

- Revisão documental após correção dos dois achados: **16/16 critérios atendidos**. A aprovação foi suspensa durante a correção e restabelecida após conferir a referência única no início da avaliação de negócio e a aceitação obrigatória das duas solicitações válidas de US3.5. Não restam ambiguidades nesses achados nem dúvidas materiais bloqueantes; prontidão documental não comprova comportamento implementado ou testes aprovados.
- Template ativo resolvido pela pilha do projeto: núcleo em .specify/templates/spec-template.md. Seções e ordem preservadas, com cinco histórias P1 e **30 cenários** (6/9/5/4/6), **22 requisitos funcionais** e **oito critérios de sucesso**.
- Fontes expressas do PDF, quatro decisões aprovadas e assunções estão separadas. As decisões de quitação integral única, ocorrência informada, repetição rejeitada e ADMIN para qualquer titular não permanecem abertas nem são atribuídas indevidamente ao PDF.
- Autorização considera conta e ADMIN atuais; CLIENT isolado é rejeitado. A permissão de quitar pedido alheio não permite leitura alheia pela 007. Campos restritos não são expostos a conta sem permissão.
- Referência temporal corrigida e alinhada em US1.4–6, US2.7, FR-006, SC-003, entidades e assunções: criação do pedido <= ocorrência do pagamento <= referência da tentativa. A referência é fixada uma única vez no início da avaliação de negócio, antes de qualquer alteração, e permanece até o término; recebimento e commit não são referências alternativas. US2.5–7 cobrem dados inválidos e limites rejeitados, inclusive quando o tempo avança depois da referência. Somente formato, precisão e mecanismo técnico ficam para planejamento.
- US3.5 corrigido e alinhado a FR-011 e SC-004: para dois pedidos elegíveis distintos, ambas as solicitações válidas sem outras falhas são aceitas, cada uma exclusivamente vinculada ao respectivo pedido. Para o mesmo pedido, US3.4 continua exigindo uma única aceitação; repetições continuam rejeitadas. Nenhum mecanismo de implementação foi escolhido.
- US4.1–3 e FR-013–014 separam falha anterior à conclusão de perda da resposta após conclusão definitiva. O trecho "pagamento e PAID permanecem registrados" impede prometer reversão de registro concluído; a repetição posterior continua rejeitada.
- FR-016 e as dependências registram a limitação atual do OpenAPI da 007 a WAITING_PAYMENT e a necessidade futura de admitir PAID no resumo e detalhe. A evolução pertence à 008; especificação histórica e contratos anteriores permanecem intactos nesta etapa.
- FR-017–021 e US5 preservam campos, titularidade de conteúdo/contagens/detalhe, paginação, ordenação por criação/ID, composição, nomes atuais, representação e valores históricos, leitura sem mutações e regressões. Não se acrescentam campos de pagamento às consultas.
- Revisão de SC-007: "pedidos quitados apresentam PAID" foi delimitado explicitamente, sem exigir apresentação de PAID em consultas vazias ou rejeitadas. SC-001–008 medem os cenários finitos; não inventam SLA, capacidade operacional ou resultados executados.
- Menção ao OpenAPI existente e ao identificador PAID documenta compatibilidade solicitada; não define rota, DTO, persistência, formato temporal, códigos concretos ou mecanismo de concorrência. Esses itens permanecem para planejamento.
- Escopo restrito ao registro de quitação declarada, sem cobrança ou movimentação financeira externa. Exclusões e dependências são explícitas. Planejamento, contratos, tarefas, implementação e testes não foram executados por esta revisão.

| Requisitos | Cenários de aceitação | Critérios de sucesso |
| --- | --- | --- |
| FR-001 | US1.2; US2.1–3 | SC-001–002 |
| FR-002 | US1.1–2; US5.2 | SC-001, SC-007 |
| FR-003 | US2.5, US2.8 | SC-005 |
| FR-004 | US1.1, US1.3; US2.8 | SC-001, SC-006 |
| FR-005 | US1.1–2; US2.4, US2.9; US3.1–3 | SC-001, SC-004–005 |
| FR-006 | US1.4–6; US2.5–7 | SC-003, SC-005 |
| FR-007 | US1.1–2, US1.4–6 | SC-001, SC-003 |
| FR-008 | US1.1; US4.1–2 | SC-001, SC-005 |
| FR-009 | US1.3; US5.4, US5.6 | SC-006–008 |
| FR-010 | US3.1–3; US4.3 | SC-004–005 |
| FR-011 | US3.4–5 | SC-004 |
| FR-012 | US2.1–9; US3.1–4 | SC-002–005 |
| FR-013 | US4.1–2, US4.4 | SC-005 |
| FR-014 | US4.3 | SC-004–005 |
| FR-015 | US2.1–9; US3.1–4; US4.4 | SC-002, SC-005 |
| FR-016–017 | US5.1 | SC-007 |
| FR-018 | US5.2, US5.4 | SC-007 |
| FR-019 | US5.3–4 | SC-006–007 |
| FR-020 | US5.4, US5.6 | SC-007–008 |
| FR-021 | US5.1–5 | SC-007–008 |
| FR-022 | US5.6 | SC-008 |

- Esta matriz é rastreabilidade de requisitos, não evidência de execução. Itens que deixem de atender à qualidade exigem corrigir a especificação antes de avançar; este checklist não inicia a fase seguinte.
