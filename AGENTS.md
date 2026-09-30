# Orientações para agentes

## Fontes do projeto

- [Constituição](.specify/memory/constitution.md): princípios e governança técnica; prevalece sobre outros documentos do projeto.
- `specs/<feature>/spec.md`: requisitos, escopo e critérios de aceitação.
- `specs/<feature>/plan.md`: desenho e decisões técnicas; consulte também `research.md` e `data-model.md` quando relevantes.
- `specs/<feature>/tasks.md`: tarefas, dependências e condições de execução.
- `specs/<feature>/contracts/`: contratos de API; `quickstart.md`: procedimentos e evidências registradas.
- `src/main/`: implementação; `src/test/`: testes e dados de teste; `pom.xml`: configuração do build.
- [Guia do harness](docs/harness.md): fluxo de trabalho, linha de base e próximos passos.

## Antes de editar

Confirme raiz, branch real, HEAD e alterações com `git rev-parse --show-toplevel`, `git branch --show-current`, `git rev-parse HEAD` e `git status --short`. Preserve alterações existentes e limite o trabalho ao pedido. Não descarte, sobrescreva ou inclua trabalho alheio; não troque de branch, marque tasks ou faça commit, push ou merge sem autorização no escopo da solicitação.

Identifique a feature e a task pelo pedido do usuário e pelos artefatos correspondentes. Se houver ID de task, leia sua descrição, dependências e condições. Sem ID, não escolha automaticamente a primeira caixa aberta; esclareça ambiguidades que impeçam determinar o escopo.

O ponteiro local `.specify/feature.json`, quando existir, indica apenas a feature selecionada. A branch, uma checkbox e o status `Draft` não comprovam entrega. Cruze requisitos, código, verificações executadas e histórico de integração. O campo `BRANCH` dos scripts do Spec Kit pode representar a feature selecionada; confirme a branch real pelo Git.

## Validação

O projeto usa Java 21 e Maven instalado no `PATH`. O comando existente para a suíte completa, confirmado no diagnóstico, é:

```powershell
mvn test
```

O Maven Wrapper ainda está pendente. Para mudanças de comportamento, use testes que exercitem os critérios de aceitação e as regressões afetadas. Quando aplicável ou exigido pela task, execute o teste antes da implementação e confirme falha pela causa esperada; erro de compilação ou de preparação não demonstra comportamento ausente. Após implementar, execute os testes apropriados e a suíte completa quando exigida pelos artefatos ou pelo alcance da mudança.

Para alterações exclusivamente documentais, revise caminhos, links, comandos, consistência com as fontes e o diff. Execute `git diff --check`; revise também o conteúdo dos arquivos novos, pois arquivos não rastreados não aparecem no diff comum. Não repita testes de aplicação sem necessidade decorrente da mudança.

## Conclusão

Conclua quando o escopo estiver atendido, as verificações pertinentes tiverem sido executadas, os resultados e limitações estiverem registrados no local solicitado ou no relato de entrega, e o diff e os arquivos novos tiverem sido revisados. Informe o estado final do Git.

Nunca declare uma verificação aprovada sem executá-la. Distinga resultado histórico, execução atual, procedimento sugerido e verificação bloqueada; registre comando e causa observada de qualquer impedimento.
