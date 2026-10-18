# Data Model: Filtrar catálogo público de produtos por nome

**Estado**: desenho planejado sobre `c3caf26eb9936aea379908118ae44b1201ff1614`; nenhuma migração ou implementação neste checkpoint. [Spec](spec.md) | [Pesquisa](research.md) | [Contrato](contracts/public-products-openapi.yaml)

## Persistência existente, sem alterações

| Conceito existente | Campos/relações relevantes | Uso nesta feature |
| --- | --- | --- |
| `Product` / `tb_product` | `id: Long`, `name: String`, `description: String`, `image: String`, `price: BigDecimal`, categorias many-to-many | Somente leitura; pesquisar `name` e paginar produtos. Nenhum campo, índice, constraint ou relação novo. |
| `Category` / `tb_category` e `tb_product_category` | ID/nome e pares produto/categoria | Inalterados; não participam da consulta paginada. Continuam no detalhe já existente. |
| Contas, perfis, pedidos e itens | Estruturas das Features 003–005 | Inalteradas. Consulta pública não exige conta/perfil nem altera preços históricos. |

O nome armazenado não sofre trim, compactação, remoção de acentos, atualização de caixa ou transformação Unicode. Produtos legados com nome além do limite da escrita, espaços externos, preço zero e categorias vazias continuam legíveis. Não se invoca validação de escrita durante leitura. O preço continua numérico nas representações públicas.

## Entrada transitória: filtro e paginação

| Dado | Recebimento | Regra de negócio/validação |
| --- | --- | --- |
| `name` | String opcional de query, ausente representado internamente por `null` | Normalizar somente extremidades reconhecidas por `Character.isWhitespace` ou `Character.isSpaceChar`; preservar internos. Resultado vazio significa nenhuma restrição. Sem comprimento mínimo/máximo de negócio, truncamento ou coerção nova. |
| `page` | `int`, padrão 0 | Conservar binding e Bean Validation públicos atuais; valor negativo é inválido. Não reinterpretar com parser administrativo. |
| `size` | `int`, padrão 12 | Conservar binding e Bean Validation públicos atuais; intervalo 1–50. |

Para os parâmetros de paginação, manter também o fallback atual de `defaultValue` quando omitidos ou vazios e os erros atuais para valores não conversíveis. Não se acrescenta teto próprio de offset, clamp, regra sobre desconhecidos/repetidos ou interpretação numérica diferente.

O filtro normalizado é um valor local à solicitação. Não há persistência, sessão, histórico de busca ou estado compartilhado. Cada página informa seu próprio filtro. A string textual `null`, se fornecida, é texto literal, não instrução de ausência; não se acrescenta coerção.

## Seleção, ordem e contagem

Com filtro preenchido, uma correspondência existe quando o nome contém o trecho contínuo, comparando caixa conforme a semântica administrativa existente e sem remover acentos. O texto completo do filtro é literal, inclusive `%`, `_`, barra e apóstrofo. Seleção e count usam o mesmo predicado parametrizado; a página não faz join de categorias que multiplique produtos.

A ordem é a atual sobre o nome armazenado crescente, depois ID crescente. Comparar caixa para selecionar não muda a ordenação. Para catálogo estável, os IDs do conjunto filtrado são distribuídos pelas páginas sem perdas/duplicações; não se promete snapshot entre solicitações diante de alterações concorrentes.

## Representações públicas preservadas

| Representação | Campos existentes | Invariantes |
| --- | --- | --- |
| `ProductCatalogItemDTO` | `id`, `name`, `image`, `price` | Somente esses quatro campos; nome original; preço JSON numérico. Sem categorias, descrição ou campos administrativos. |
| `PageResponseDTO<ProductCatalogItemDTO>` | `content`, `page`, `size`, `totalElements`, `totalPages` | Conteúdo e totais do mesmo conjunto; página solicitada não é ajustada; tipos existentes preservados. |
| `ProductDetailDTO` | `id`, `name`, `description`, `image`, `price`, `categories` | Inalterado; categorias são nomes distintos, ordenados como antes; preço numérico. Acesso por ID independe da busca. |
| Erro público por violação de limites | `status`, `error`, `message`, `path` | 400 já existente. Não converter erros nativos de binding para esse DTO sem requisito. |

Para `totalElements=N`, `totalPages` corresponde ao arredondamento para cima de `N/size`, com zero quando `N=0`. Sem correspondências, `content=[]` e totais zero em qualquer página válida. Além do último resultado, `content=[]`, mas os totais continuam sendo os do filtro. Exemplos completos no contrato.

## Estados, transações e ausência de mutações

Não há transição de estado de domínio. A única transformação é transitória: entrada opcional → filtro com espaços externos removidos → consulta sem filtro ou filtrada → DTOs existentes.

O serviço público conserva transação de leitura. Nenhum método de manutenção, save/delete/flush, lock de escrita ou mutação de entidade/coleção participa do fluxo. A extração do normalizador não muda regras nem dados de escrita da Feature 005. Ausência de mutações será verificada por observação DML durante a consulta e comparação independente antes/depois; `readOnly` não substitui essas provas.

Não são necessárias novas entidades, tabelas, dependências ou alterações em configuração global. O [quickstart](quickstart.md) delimita fixtures de teste e limpeza, que não são alterações do modelo produtivo.
