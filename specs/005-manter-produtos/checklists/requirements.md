# Specification Quality Checklist: Manter produtos

**Purpose**: Validar qualidade e completude da especificação antes do planejamento.

**Created**: 2026-10-11

**Feature**: [spec.md](../spec.md)

**Marker Semantics**: `[x]` registra critério de qualidade documental revisado e atendido. Não comprova implementação, teste executado ou entrega funcional.

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

- Revisão documental após correções concluída: **16/16 critérios atendidos**. O critério sobre resultados mensuráveis registra cobertura documental dos resultados por cenários e SC-001–010, sem afirmar que o comportamento foi executado ou entregue. A avaliação considera conteúdo, coerência e rastreabilidade, não somente contagens.
- A especificação contém cinco histórias, **37 cenários de aceitação**, **20 requisitos funcionais** e **10 critérios de sucesso**, com as seções obrigatórias na ordem do template ativo resolvido. Não há placeholders nem marcador de clarificação no spec.md.
- Os requisitos do PDF estão separados das decisões expressas da sessão. Em particular, a busca administrativa **distingue acentos**, substituindo a proposta anterior, sem alterar consultas públicas. Imagem exige URL absoluta válida http/https sem acesso externo; nome/descrição são validados e armazenados sem espaços externos.
- A consulta auxiliar limitada à identificação de opções e associações atuais, sem busca própria, hierarquia ou relatórios, permanece explicitamente como assunção de recorte. Categorias repetidas passaram a decisão expressamente adotada nesta sessão, sem atribuição ao PDF: cada identificador distinto corresponde a uma única associação, todos devem existir e pelo menos uma categoria deve permanecer. Não há dúvida material bloqueante neste recorte; apresentação concreta dos erros fica para desenho posterior.
- US5.2 distingue Cliente sem ADMIN e conta sem nenhum dos dois perfis; US5.1 e FR-001 preservam permissão para ADMIN sem CLIENT. FR-014 tem rastreabilidade específica para atualização inexistente em US3.5/SC-010 e para exclusão inexistente em US4.3/SC-006, sem confundir as operações.
- Limites de nome/descrição e preço, URL, ausência de dados, categorias inexistentes e vários campos inválidos simultâneos possuem aceitação. Atualização é integral, sem criar produto inexistente ou aceitar somente parte das categorias. Exclusão preserva categorias e rejeita produto usado em OrderItem.
- A leitura dos produtos anteriores, inclusive preço zero, os valores históricos das compras e os contratos das Features 001–004 são preservados. Provisionamento, manutenção de categorias, busca pública nova, migração automática, consulta de pedidos, verificação externa de imagens, interface e implantação ficam fora do escopo.
- Não foram definidos endpoints, formatos de dados, anotações, classes de implementação ou mecanismos técnicos. URL e capacidade monetária são restrições de negócio solicitadas, sem nova escolha de infraestrutura.

| Requisitos | Cenários que os cobrem | Critérios de sucesso |
| --- | --- | --- |
| FR-001–002 | US5.1–4 | SC-001 |
| FR-003–005 | US1.1–6 | SC-002 |
| FR-006 | US1.7–8, US3.1 | SC-003 |
| FR-007–012 | US2.1–9, US3.3–4, US3.8 | SC-004–005 |
| FR-011 — identificadores repetidos e categorias distintas existentes | US2.9, US3.8; ausência/inexistência em US1.8, US2.7–8, US3.4 | SC-004–005 |
| FR-013 | US3.1–4, US3.6, US3.8 | SC-003–005, SC-008 |
| FR-014 — atualização de produto inexistente | US3.5 | SC-010 |
| FR-014 — exclusão de produto inexistente | US4.3 | SC-006 |
| FR-015–016 | US4.1–4 | SC-006–007 |
| FR-017 | US2.6–8, US3.4–5, US3.8, US4.2–4, US5.5 | SC-005–006, SC-009–010 |
| FR-018 | US3.7, US4.2 | SC-007 |
| FR-019–020 | US3.6, US5.6–8 | SC-008 |

- Correção documental: leitura integral dos dois documentos, revisão dos três achados, conferência de contagens, referências e links e comparação com o conteúdo anterior. US2.9 e US3.8 acrescentam aceitação de identificadores repetidos; SC-010 cobre especificamente atualização inexistente. Contratos anteriores e exclusões de escopo foram preservados.
- Nesta correção, raiz, branch real `005-manter-produtos`, HEAD `6c654931350a8f54a0a968edde409ab8d175478c`, status e índice foram conferidos. Somente estes dois arquivos foram alterados; continuam não rastreados e o índice permanece vazio. `git diff --check` e a verificação de whitespace dos dois arquivos novos por `git -c core.autocrlf=false diff --no-index --check -- /dev/null <arquivo>` não apresentaram diagnósticos.
- Evidências históricas da criação: resolução do template pela pilha ativa, prévia sem duplicação, revisão dos cinco links da especificação e do link do checklist e verificação de whitespace dos arquivos novos. Raiz, branch inicial main, mesmo HEAD e checkout limpo foram conferidos antes da criação autorizada de `005-manter-produtos`. O fluxo selecionou a feature no ponteiro local ignorado `.specify/feature.json`; `.specify/extensions.yml` estava ausente nas conferências anterior e posterior e nenhum hook foi executado. Essas etapas não foram repetidas na correção.
- Nenhum teste de aplicação, planejamento, implementação, stage, commit, fetch, push ou ação no GitHub realizado nesta correção.
