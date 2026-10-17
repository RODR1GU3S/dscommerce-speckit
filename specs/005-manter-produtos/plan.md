# Implementation Plan: Manter produtos

**Branch**: `005-manter-produtos` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

**Input**: especificação revisada no commit `fba63732ddb2f27dc03da350d5684c05f3517b75`: 20 requisitos, 37 cenários e 10 critérios de sucesso. [Checklist](checklists/requirements.md): 16/16 critérios documentais. Esses documentos permanecem intactos.

## Summary

Acrescentar manutenção administrativa completa e consultas próprias em `/admin/products` e `/admin/categories`. Reutilizar JWT, identidade autenticada e perfis atuais do banco, exigindo ADMIN independentemente de CLIENT. DTOs administrativos, diagnóstico agregado na fronteira Bean Validation e serviços transacionais preservam dados anteriores, categorias e valores históricos. Não modificar os GETs públicos nem os contratos das Features 001–004.

Este plano descreve trabalho futuro, sem implementação, execução de testes ou entrega funcional. [Research](research.md) registra decisões e alternativas; [modelo](data-model.md), [OpenAPI](contracts/product-maintenance-openapi.yaml), [documentação do contrato](contracts/README.md) e [quickstart](quickstart.md) detalham o desenho. Nenhum `tasks.md` é gerado nesta fase.

## Technical Context

**Language/Version**: Java 21; Spring Boot 3.3.4, conforme POM existente.

**Primary Dependencies**: Spring MVC, Security/OAuth2 Resource Server, Data JPA/Hibernate, Bean Validation e Jackson já presentes. Não planejar atualização de versões nem nova biblioteca obrigatória de aplicação.

**Storage**: H2 em memória no ambiente existente, JPA/Hibernate. Produto em `NUMERIC(19,2)`; associações e referências protegidas pelo banco. Produção persistente/migrações operacionais não integram este recorte.

**Testing**: JUnit 5, Spring Boot Test/MockMvc, H2 real e JDBC independente; Maven Wrapper seleciona Maven 3.9.16. Os 926 testes aprovados anteriores são evidência histórica da Feature 004, não execução deste plano nem prova da Feature 005.

**Target Platform**: backend Java; desenvolvimento Windows/PowerShell e CI Linux conforme [harness](../../docs/harness.md).

**Project Type**: web service REST, projeto Maven único.

**Performance Goals**: não há SLA ou volume aprovado. Consulta de produtos paginada, sem fetch de coleção na paginação; diagnóstico de categorias distintas em lote para evitar uma consulta por referência. Não inventar índice, cache ou limite comercial de seleção.

**Constraints**: contratos antigos intactos; nome/descrição normalizados; moeda exata, sem arredondamento; nenhuma chamada externa para imagens; leitura do legado sem validação administrativa; rollback sem dados parciais; nenhuma garantia de rollback depois de commit confirmado.

**Scale/Scope**: seis operações HTTP novas: listar, detalhar, incluir, substituir e excluir produto; listar categorias existentes. Categoria não possui escrita nem busca própria. Sem interface, pedidos novos de consulta, migração corretiva, implantação ou manutenção de usuários.

## Constitution Check

Gate anterior à pesquisa e gate posterior ao desenho avaliados contra a [constituição 1.0.0](../../.specify/memory/constitution.md). PASS significa conformidade documental do desenho; não aprovação de código/testes ainda inexistentes.

| Princípio/gate | Antes da pesquisa | Após desenho | Evidência/condição futura |
| --- | --- | --- | --- |
| I — Java/Spring Boot | PASS | PASS | Stack existente; integração Spring de MVC, segurança, validação e transações. |
| II — Controller/Service/Repository | PASS | PASS | Controllers HTTP; serviços decidem validação/identidade/integridade/atomicidade; repositories somente consultas/persistência. |
| III — DTOs | PASS | PASS | Entrada/saídas administrativas próprias; entidades não aparecem no contrato. |
| IV — JPA e Bean Validation na fronteira | PASS anterior retirado | EXCEÇÃO DOCUMENTADA | JPA/relações/transações e enforcement de `@Valid` na fronteira preservados; diagnóstico em serviços antes de concluir a validação diverge da ordem literal do princípio IV. Justificativa, limites e acompanhamento abaixo. |
| V — erros centralizados/REST | PASS | PASS | Métodos/status resource-oriented; envelope administrativo próprio; filtros e advice centralizam falhas sem mudar envelopes anteriores. |
| Requisitos antes de implementação | PASS | PASS | Spec commitada; rastreabilidade integral abaixo; nenhuma regra de negócio nova atribuída ao PDF. |
| Revisão e testes pertinentes | PASS | PASS | Estratégia futura cobre sucesso, campos, inexistência, integridade, segurança, regressões e limites transacionais. Nenhum teste executado nesta fase. |

O gate IV não recebe PASS de conformidade estrita. A revisão posterior identificou fundamento insuficiente para os PASS pré/pós-desenho anteriormente declarados; esta correção registra a exceção conforme a seção Governance, sem alterar a constituição nem atribuir aprovação funcional ao desenho. Não há nova clarificação de negócio. A assunção da consulta auxiliar continua sendo assunção delimitadora, não nova aprovação de negócio. Caracteres/representações e precedência são decisões técnicas explicitadas no contrato.

### Gate IV — achado, exceção e acompanhamento

**Achado recuperado integralmente**: “Gate IV marcado PASS sem resolver a interpretação constitucional. O plano, linha 104, executa normalização, regras e consulta de referências em serviço durante a validação. A constituição, linha 51, exige validação na fronteira antes da lógica de serviço. O plano restringe isso ao serviço de escrita, sem justificar essa interpretação. É necessário demonstrar conformidade ou registrar a exceção e sua justificativa; o PASS atual não basta. Não implica nova decisão de negócio.” As linhas citadas identificam a versão revisada, anterior a esta correção.

**Regra e alcance**: o princípio IV exige Bean Validation por anotações, enforced no controller “before service logic executes”. Não permite concluir conformidade apenas porque o serviço invocado pelo validator é somente leitura. O desenho mantém JPA por repositories, relações/identificadores/transações deliberados e rejeição na fronteira antes da manutenção; a exceção incide exclusivamente na ordem de execução do diagnóstico, não nesses demais deveres.

**Justificativa por requisito**: FR-012 e US2.6 exigem detectar na mesma avaliação a categoria inexistente junto com nome, descrição, preço e imagem inválidos. Interromper com `@Valid` antes de qualquer serviço impediria o lookup conjunto previsto; consultar categorias só quando os campos locais passam não atende esse cenário. Transferir decisões de existência/autorização ao controller ou acesso direto a repository pelo validator contraria o princípio II e Backend Constraints. Mantém-se, portanto, a restrição anotada de classe que adapta o diagnóstico dos serviços em violations, sem fail-fast. Esta é uma exceção técnica explícita à ordem literal do IV, não uma redefinição de “service logic” como somente escrita.

**Limites e consequências**: durante `ConstraintValidator.isValid`, os serviços podem normalizar valores em objeto interno imutável, avaliar regras, repetir guarda da conta/ADMIN e ler as referências legíveis em lote. Não podem mutar DTO/entidades, emitir DML, gravar produto/vínculos, abrir transação de manutenção nem executar outras operações de negócio. A restrição fica scoped ao DTO administrativo; segurança precede diagnóstico e as Features 001–004 permanecem intactas. Mesmo uma entrada localmente inválida pode exigir leitura do banco, com custo e possibilidade de 401/403 por guarda ou 500 por falha interna, em vez de diagnóstico completo 400. A validação não elimina corrida: o serviço transacional repete guarda/regras/referências antes de mutar, e as FKs protegem a persistência final.

**Acompanhamento obrigatório futuro**: V01/V02/V06 devem comprovar US2.6 com os cinco campos, categorias parcialmente malformadas, precedência/guardas e tradução de `ValidationException`; instrumentar ausência de DML/entrada na manutenção durante diagnóstico rejeitado e conferir snapshots por conexão independente. V07/V08 cobrem revalidação e constraints finais. Antes de considerar tarefas de implementação completas, revisar o alcance real da exceção contra Governance e registrar evidências/limitações no quickstart. Se a implementação ampliar o diagnóstico para mutação/outra operação, o gate volta a pendente até revisão do desenho. Essas provas ainda não foram executadas; a exceção documentada resolve o fundamento documental, sem comprovar conformidade estrita ou aprovação funcional.

## Project Structure

### Documentation (this feature)

```text
specs/005-manter-produtos/
├── spec.md                         # aprovado, preservado
├── checklists/requirements.md      # aprovado, preservado
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── product-maintenance-openapi.yaml
    └── README.md
```

### Source Code (repository root)

```text
src/main/java/com/devsuperior/dscommerce/
├── controllers/                    # futuros AdminProductController/AdminCategoryController
│   └── handlers/                   # adaptação central administrativa de erros
├── dto/                            # DTOs administrativos separados dos públicos
├── services/                       # manutenção, diagnóstico e seleção de categorias
│   └── exceptions/                 # inválidos agregados/integridade referencial
├── repositories/                   # consultas administrativas e CategoryRepository/OrderItemRepository
├── entities/                       # substituição integral de Product e integridade de associações
├── security/                       # adaptadores administrativos, identidade existente reutilizada
└── config/                         # cadeia ADMIN e leitor JSON somente para DTO administrativo
src/test/java/com/devsuperior/dscommerce/
├── controllers/                    # futura integração HTTP/segurança/persistência/regressões
├── services/                       # políticas, guardas tardias e atomicidade
├── repositories/                   # busca real e constraints independentes
└── dto/                            # futuro contrato/leitura/validação estrutural
```

**Structure Decision**: continuar no pacote/projeto existentes. Os nomes acima são responsabilidades planejadas, não arquivos criados. Reutilizar `AuthenticatedAccountService`, `AuthenticatedAccountPrincipal`, `JwtDecoder` e `OrderJwtAuthenticationConverter` sem renomeação abrangente. DTOs de catálogo/detalhes, login e pedidos conservam os contratos atuais.

## Desenho e responsabilidades

### Segurança e consultas

Manter cadeia `POST /orders` em prioridade 1; inserir cadeia `/admin/**` em prioridade 2 e mover somente prioridade do fallback público para 3. Todos os métodos do namespace administrativo exigem `hasRole("ADMIN")`, inclusive método/rota não suportados. Resource server não é global: token inválido irrelevante continua sem restringir GET público. Adaptadores ADMIN próprios traduzem ausência/invalidez para 401, falta de ADMIN para 403 e falha de resolução interna para 500. Não reutilizar mensagens de pedidos na administração.

Converter existente valida assinatura/tempo/sub e carrega authorities do banco; claims de nome/perfis não concedem acesso. Serviços administrativos e diagnóstico de referências repetem a guarda da conta/perfil atual antes de consultar dados administrativos ou mutar. Remoção da conta ou revogação de ADMIN entre filtro e serviço rejeita a chamada. A guarda é avaliação naquele instante, não promessa de serializar toda alteração de perfil concorrente.

Busca nova: parâmetro `name`, remoção somente dos espaços externos, `locate(lower(:name), lower(p.name)) > 0`, parâmetro vinculado. Ausente/vazio usa consulta sem predicado. Não utilizar curingas, remoção de acentos, normalização Unicode nem compactação. `name ASC,id ASC`, mesmos defaults/limites de página do catálogo; count usa o mesmo filtro. Resposta de página mantém `content,page,size,totalElements,totalPages`, com DTO administrativo e preço string. GET de detalhe carrega categorias e distingue pelo ID.

Consulta auxiliar retorna todas as categorias como lista de `{id,name}`, ordenada `name,id`, inclusive sem produtos/nomes iguais. Não possui filtro, hierarquia, relatório ou escrita; associações atuais estão no detalhe administrativo. Não reutilizar os nomes deduplicados do detalhe público para seleção.

### Entrada, validação e escrita

DTO de escrita contém somente `name,description,price,image,categoryIds`. Leitor streaming específico desse DTO consome objeto JSON examinável completo, preserva diagnósticos de tipos/chaves desconhecidas ou repetidas e examina campos independentes, sem coerção. IDs são tokens inteiros positivos Long; preço é string decimal exata. Sintaxe quebrada/raiz inadequada/corpo ausente não permite inventar uma lista dos cinco campos: erro de corpo. O [contrato](contracts/README.md) fixa códigos e precedência.

Restrição Bean Validation de classe no DTO executa diagnóstico completo: normalização e regras em `ProductInputValidationService`, consulta de categorias válidas em formato em serviço somente leitura, adaptação em violations por campo. Validar antes da entrada no serviço de escrita; a invocação dos serviços de diagnóstico antes de concluir essa validação é a exceção ao princípio IV delimitada em Constitution Check, não conformidade estrita pela simples ausência de escrita. Não usar fail-fast/group sequence que impeça US2.6; categorias com um elemento malformado não impedem verificar outros IDs válidos. O validator não consulta repository diretamente nem decide regra de negócio. Dados de principal não vêm do corpo.

Serviço de manutenção começa repetindo a guarda de conta/ADMIN, antes de qualquer lookup administrativo. Para PUT/DELETE, localiza e locka o alvo; PUT válido sem alvo é 404. Reavalia dados/referências dentro da transação e só então altera a entidade. Atualização conserva ID e substitui os cinco dados e associações; não constrói outra entidade com mesmo ID para merge indiscriminado. Criação/atualização não tornam obrigatórias novas regras em leituras/entidades legadas. Descrição/imagem precisam de armazenamento textual amplo, sem máximo comercial de 255; ver [modelo](data-model.md).

### Atomicidade, exclusão e histórico

Serviços de escrita via proxy Spring usam transação REQUIRED com rollback de falhas; HTTP não acrescenta transação externa. Flush explícito, resposta mapeada na transação e entregue ao controller somente após sucesso do interceptor transacional. Nenhuma continuação após falha de persistência.

Exclusão: carregar/lockar produto, testar qualquer `OrderItem`, remover somente vínculos do produto e produto, flush, concluir. FK restritiva é autoridade final; precheck e lock ADMIN não impedem sozinhos compra concorrente, pois `OrderService` atual não locka o produto. Se compra vincular primeiro, exclusão é rejeitada integralmente; se exclusão prevalecer, não pode existir item órfão. Sem cascata REMOVE/ALL para categorias/pedidos/itens. Classificar somente FK conhecida/confirmada como 409; falhas não classificáveis, deadlock/timeout ou commit indeterminado são 500 seguro. Não alterar contrato de falhas do registro de pedidos nem prometer serialização global.

Preço vigente muda somente em Product. `OrderItem.unitPrice`, subtotais e totais persistidos/calculados das compras não são recalculados a partir dele. Associação categoria repetida resulta em um par único no modelo e banco. Critérios de concorrência e checks SQL diretos estão no quickstart.

### Erros e fronteiras operacionais

Contrato administrativo usa `status,error,message,path,errors`; `errors` tem campo/código/mensagem e fica vazio para falhas sem campo. Envelope/mensagens das quatro features anteriores permanecem com seu formato atual. Segurança precede leitura, diagnóstico completo precede existência do alvo, e integridade precede confirmação. PUT válido de inexistente é 404; PUT inválido desse mesmo ID é 400 agregado, sem upsert.

Advice centralizado deve alcançar falhas de flush/commit após o corpo do serviço; adaptadores tratam falhas de filtros. Não converter todo erro MVC em 500: manter status/cabeçalhos nativos de 405/406/415 em resposta administrativa segura. Distinguir Bean Validation de violation Hibernate e violação referencial comprovada de defeito interno. Não ecoar valores recebidos, tokens, senha/hash, segredo, SQL ou stack trace em resposta/logs normais.

Exceção runtime de `ConstraintValidator.isValid` pode chegar envolvida em `jakarta.validation.ValidationException`. A adaptação central somente ADMIN deve reconhecer causas de guardas conhecidas do diagnóstico: conta ausente/credencial inválida continua 401, falta de ADMIN continua 403; resolução interna e demais falhas inesperadas continuam 500 seguro. Testar revogação/remoção durante diagnóstico na fronteira, além da guarda de escrita. Normalização gera valor interno novo, sem modificar o DTO dentro de isValid.

Não acrescentar idempotência, optimistic version/ETag, PATCH ou retry automático. Atualizações administrativas concorrentes serializadas pelo lock substituem integralmente o estado; não há requisito de detectar edição baseada em leitura antiga. Falha de transmissão depois de commit não permite prometer rollback; repetição de POST pode criar outro produto.

## Rastreabilidade integral e estratégia de verificação

Cada linha é um cenário da spec preservada. V01–V13 são grupos de verificações futuras descritos no [quickstart](quickstart.md); não são tasks ou resultados executados. FR-001/002 e proteção transacional aplicam-se transversalmente a todas as operações.

| Cenário | Requisitos | Critérios | Verificação futura |
| --- | --- | --- | --- |
| US1.1 | FR-003, FR-004 | SC-002 | V03 |
| US1.2 | FR-004 | SC-002 | V03 |
| US1.3 | FR-004 | SC-002 | V03 |
| US1.4 | FR-004 | SC-002 | V03 |
| US1.5 | FR-004 | SC-002 | V03 |
| US1.6 | FR-003, FR-005 | SC-002 | V03 |
| US1.7 | FR-006 | SC-003 | V04 |
| US1.8 | FR-006, FR-011 | SC-003, SC-005 | V04, V06 |
| US2.1 | FR-007, FR-011 | SC-004 | V05 |
| US2.2 | FR-008 | SC-004 | V05, V06 |
| US2.3 | FR-008 | SC-004, SC-005 | V06 |
| US2.4 | FR-009 | SC-004, SC-005 | V06 |
| US2.5 | FR-010 | SC-004, SC-005 | V06 |
| US2.6 | FR-008, FR-009, FR-010, FR-011, FR-012, FR-017 | SC-005 | V02, V06 |
| US2.7 | FR-011, FR-012, FR-017 | SC-005 | V06 |
| US2.8 | FR-007, FR-008, FR-011, FR-012, FR-017 | SC-005 | V02, V06 |
| US2.9 | FR-011 | SC-004 | V05, V08 |
| US3.1 | FR-006, FR-013 | SC-003 | V04, V07 |
| US3.2 | FR-013 | SC-004 | V07 |
| US3.3 | FR-008, FR-013 | SC-004 | V06, V07 |
| US3.4 | FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-017 | SC-005 | V02, V06, V07 |
| US3.5 | FR-014 | SC-010 | V07 |
| US3.6 | FR-008, FR-009, FR-010, FR-011, FR-013, FR-019 | SC-004, SC-005, SC-008 | V07, V13 |
| US3.7 | FR-018 | SC-007 | V10 |
| US3.8 | FR-011, FR-013, FR-017 | SC-004, SC-005 | V07, V08 |
| US4.1 | FR-015 | SC-006 | V09 |
| US4.2 | FR-016, FR-017, FR-018 | SC-006, SC-007 | V09, V10 |
| US4.3 | FR-014 | SC-006 | V09 |
| US4.4 | FR-016, FR-017 | SC-006 | V08, V09 |
| US5.1 | FR-001 | SC-001 | V01 |
| US5.2 | FR-001, FR-002 | SC-001 | V01 |
| US5.3 | FR-002 | SC-001 | V01 |
| US5.4 | FR-001, FR-002, FR-012 | SC-001 | V01, V02 |
| US5.5 | FR-017 | SC-009 | V11 |
| US5.6 | FR-019, FR-020 | SC-008 | V13 |
| US5.7 | FR-018, FR-020 | SC-007, SC-008 | V10, V13 |
| US5.8 | FR-020 | SC-008 | V13 |

Complementos técnicos verificáveis: leitura estrutural agregada (V02); metadados, unicidade/FKs e instruções SQL diretas que ultrapassam a validação Java (V08); disputa de transações (V12); HTTP/cabeçalhos e segurança interna (V01/V02/V11). Teste unitário não comprova SQL, filtro real, commit ou concorrência. Persistência exige snapshots por outra conexão/contexto após retorno HTTP/proxy e antes da limpeza.

## Complexity Tracking

Exceção ao princípio IV registrada em Constitution Check: diagnóstico sem escrita em serviços durante a restrição anotada de classe, necessário para FR-012/US2.6 e para conservar decisões nos serviços conforme princípio II. `@Valid` interrompendo antes de todo serviço ocultaria a referência inexistente no cenário de múltiplos campos inválidos; acesso direto pelo controller/validator deslocaria responsabilidades. O custo adicional de leitura, falhas de diagnóstico e acompanhamento estão delimitados naquela seção. Leitor scoped e DTO de erro próprio preservam contratos, sem nova infraestrutura genérica.

## Pendências e limite da fase

Sem dúvida material de negócio restante. Gate IV com exceção documentada, não PASS irrestrito; revisão de conformidade/alcance e provas indicadas são obrigatórias antes do fechamento futuro da implementação. Falta implementar e executar as verificações planejadas, incluindo DDL real, concorrência e tratamento tardio de integridade. Esses resultados não são presumidos pelo plano. H2 em memória e `ddl-auto=create` são baseline de desenvolvimento, não estratégia de preservação/implantação de uma base produtiva existente. Não gerar tasks, alterar a spec/checklist nem configurar outro banco nesta fase.

## Acompanhamento documental T043 — 2026-10-14

O [checkpoint US2/T043](quickstart.md#checkpoint-us2-t043) consolida somente evidências já arquivadas: T038 224 GREEN de entrada/HTTP/persistência; T039 14 GREEN de invariantes do diagnóstico; os dois REDs reais da T040, suas correções delimitadas na T041 e a seleção T042 23 GREEN. Registra V02/V05/V06/V08 na parcela de inclusão e US1.8 para POST sem categorias, mantendo PUT futuro em T044/T051/T053. T043 não executou testes ou verificações de componentes, nem altera desenho/contratos/constituição.

**Gate IV permanece EXCEÇÃO DOCUMENTADA**, com a mesma justificativa FR-012/US2.6 e os mesmos limites. As afirmações de provas futuras da seção original pertencem ao desenho daquela data; o checkpoint identifica agora as parcelas de US2 executadas, sem reescrever esse histórico ou convertê-lo em PASS estrito. Imutabilidade em memória, zero DML e snapshot externo são provas distintas; guardas/revalidação/FK/rollback da inclusão não encerram conformidade de toda a feature. Metadata da FK é capturada na manutenção autorizada/revalidada, fora de isValid; não amplia a exceção.

Permanecem T051 (PUT/revalidação), T067 (guardas tardias/conta ausente/wrappers/lookup), T074–T077 (falhas/rollback em etapas delimitadas) e T079 (revisão Governance), além das matrizes/constraints/histórico das histórias futuras e fechamento T081–T086. Flush não é commit; beforeCommit ou falha injetada antes de Connection.commit delegar não comprova falha física do banco. Sem promessa de rollback depois de commit confirmado. **T001–T043 [X]; T044–T086 abertas**: checkpoint US2, não encerramento da Feature 005.


<a id="checkpoint-t084-plan"></a>

## Acompanhamento documental T084 — implementação local e validação — 2026-10-17

Este acréscimo atualiza o estado da implementação sem reescrever o desenho de 2026-10-11 ou o checkpoint US2/T043 acima. Expressões anteriores como “futuro”, “falta implementar” e “T044–T086 abertas” descrevem aquelas fases; o estado local agora é **T001–T084 concluídas, T085–T086 abertas**. A presente tarefa é documental, sem Maven, testes novos, alterações de arquitetura/código/contratos ou aprovação global antecipada.

As seis operações administrativas estão implementadas na working tree de `005-manter-produtos`, HEAD `e67601c42d07eb85b6dc2f0712a867c747936b45`, incluindo ajustes de teste/POM não commitados da T083. [T081](quickstart.md#checkpoint-t081) confrontou 37 cenários/20 FR/10 SC; [T082](quickstart.md#checkpoint-t082) revisou contrato/alinhamento; [T083](quickstart.md#checkpoint-t083) executou `.\mvnw.cmd test` no Windows, Java Zulu 21.0.12.1, Maven 3.9.16. A segunda invocação, 2026-10-17 12:06:18.324–12:18:39.858 UTC−03, passou **2.602 testes/0 falhas/0 erros/0 ignorados, exit 0**, com 85 XML/85 TXT concordantes com o console. Não repetida nesta T084, não apresentada como CI remota/Linux/macOS ou validação do HEAD sem seu diff.

Mantidos o registro da primeira invocação T083 (2.494/7/1/0, exit 1) e as correções necessárias: excluir configurações de teste apenas do scan dos runners preservando a aplicação real; atualizar Allow conforme DELETE já contratado, continuando sem PATCH; permitir o observador de rede somente no fork Surefire Java 21. Nenhuma alteração produtiva ou enfraquecimento de contrato/cobertura foi feita nessa correção ou nesta tarefa.

O [quadro V01–V13 consolidado](quickstart.md#t084-v01-v13) associa os grupos aos conjuntos efetivamente reexecutados; o [histórico RED/GREEN](quickstart.md#t084-red-green) separa comportamento ausente/incorreto de falha de preparação e expectativa antiga. A suíte T083 inclui integralmente segurança 302, OSIV16, diagnóstico13, framework226, rollback23 e regressões anteriores; supera, como evidência posterior, os limites temporais dos grupos focalizados T077/T078/520 sem reclassificar suas contagens ou somá-las. Fontes/POM e novo helper continuam correspondentes à entrada testada; após isso, somente documentação mudou.

**Gate IV permanece EXCEÇÃO DOCUMENTADA, sem PASS estrito.** FR-012/US2.6 continuam justificando exclusivamente a ordem do diagnóstico em serviços durante isValid: normalização interna imutável/regras/guarda/leitura, sem mutação de DTO/entidades, DML ou manutenção. T039/T051/T067, a prova OSIV e a revisão T079/T080, reexercitadas quando pertinentes na T083, sustentam os limites observados; não redefinem “service logic” nem alteram a constituição. A manutenção revalida em sua transação antes de mutar; a projeção escalar de perfis efetivos evita confiar no cache gerenciado, sem serializar toda revogação concorrente. Falha de guarda/lookup pode preceder o agregado400. Qualquer expansão comprovada exige reabrir o gate; Governance global e fechamento continuam sujeitos a T085–T086.

**Limites técnicos/operacionais preservados**: V03 depende de dataset estável, não prova SLA/volume ou ultrapassar o offset do provedor JPA. Snapshots JDBC por IDs/valores após HTTP/proxy e antes da limpeza, cálculo BigDecimal do histórico e os pontos de falha estão no [quadro de limites](quickstart.md#t084-snapshots-limites). Flush não é commit; pré-delegação de commit manteve snapshot igual e não observou rollback explícito no wrapper; falha de saída depois de commit não desfaz estado confirmado. H2 memória/create-drop não prova durabilidade/recuperação/migração de produção. Barreira/lock/FK não prometem vencedor universal, ETag, idempotência ou serialização global de compras. SecurityManager é instrumentação de teste Java21 marcada para remoção futura, não controle de produção nem observação universal de rede.

Preservados spec/checklist/modelo/contratos/constituição, fontes, correções de isolamento e arquivos das duas execuções, 432 relatórios anteriores e demais evidências. Somente documentação e checkbox T084 atualizadas. **T085–T086 não executadas**, sem stage/commit/fetch/push/merge/implantação. [Consolidação T084 e verificações documentais](quickstart.md#checkpoint-t084).
