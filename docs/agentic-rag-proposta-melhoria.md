# Proposta de Melhoria: RAG Agêntico para o `searchRagDocuments`

> Documento técnico-didático descrevendo os problemas observados na ferramenta MCP de busca RAG,
> o diagnóstico das causas e a solução implementada com base em técnicas de **RAG Agêntico**.

## 1. O problema

A ferramenta MCP `searchRagDocuments` é o único ponto de acesso que agentes de IA externos têm
para consultar as bases vetoriais (RAG) desta aplicação. Foram relatadas quatro limitações:

1. **Consultas complexas não retornam todos os resultados relevantes.**
2. **Consultas quantitativas** (ex.: "quantos documentos temos sobre X?") **não funcionam.**
3. **A busca aparentemente não percorre todos os dados disponíveis** na base.
4. **O parâmetro `filterExpression` é texto livre e frágil** — fácil de errar, sem validação.

Este documento explica **por que** isso acontecia e **como** cada problema foi resolvido.

## 2. Diagnóstico: por que isso acontecia

Antes da mudança, a ferramenta fazia exatamente uma coisa: pegava a pergunta do usuário,
gerava um único embedding e executava uma única chamada
`VectorStore.similaritySearch(SearchRequest)`. Esse é o padrão clássico de **RAG "ingênuo"**
(*naive RAG*), e ele tem limitações estruturais conhecidas:

- **Uma pergunta, um embedding, uma "visão" do espaço vetorial.** Uma pergunta complexa como
  *"compare a configuração do Redis com a do pgvector e explique as diferenças de autenticação"*
  na verdade contém várias sub-perguntas. Um único embedding tende a "misturar" esses aspectos e
  frequentemente não recupera trechos relevantes para todas as partes da pergunta — daí o
  problema (1) e, em parte, o (3): não é que os dados não existam na base, é que uma única
  formulação da pergunta não consegue "alcançá-los" semanticamente.

- **Busca por similaridade não é agregação.** Um `VectorStore` responde "quais trechos são mais
  parecidos com esta pergunta?" — ele não tem noção de "quantos documentos existem que satisfazem
  X". Perguntas quantitativas (2) são, por natureza, incompatíveis com busca por similaridade;
  elas exigem uma operação diferente (contagem/agrupamento sobre metadados).

- **`filterExpression` sem nenhuma validação.** O parâmetro era passado literalmente para o
  `FilterExpressionTextParser` do Spring AI, sem checar se os campos citados (`source`,
  `spaceKey` etc.) sequer existiam nos metadados dos documentos. Pior: a documentação de exemplo
  da ferramenta (`docs/mcp-search-rag-documents-examples.md`) continha **exemplos incorretos**
  — referenciava campos que nunca são gravados nos documentos (`branch`, `folders`,
  `source == 'github'`) e usava sintaxe errada para o operador `IN` (parênteses em vez de
  colchetes). Um agente de IA seguindo esses exemplos praticamente sempre falhava ou recebia
  zero resultados silenciosamente — o que explica boa parte do problema (4).

## 3. O que é RAG Agêntico

RAG Agêntico é uma evolução do RAG tradicional em que, em vez de um único passo
"pergunta → busca → resposta", o sistema de recuperação incorpora **decisões e etapas
adicionais** antes e depois da busca vetorial propriamente dita — normalmente orquestradas com o
apoio de um LLM. As técnicas mais estabelecidas (ver Seção 11, [Referências](#11-referências))
incluem:

- **Expansão/decomposição de consultas** (*query expansion* / *multi-query retrieval*): gerar
  várias reformulações da pergunta original e buscar por todas, combinando os resultados. É a
  ideia central da técnica conhecida como **RAG-Fusion**.
- **Auto-consulta / *self-querying retrieval***: em vez de o agente "adivinhar" a sintaxe de um
  filtro estruturado, o sistema expõe o esquema de metadados disponível para que o filtro seja
  construído (e validado) de forma guiada.
- **Ferramentas especializadas além da busca vetorial**: reconhecer que nem toda pergunta é uma
  pergunta de "similaridade" — perguntas quantitativas devem ser roteadas para uma ferramenta de
  agregação, não para o buscador semântico.

A ideia geral de dar a um agente **múltiplas ferramentas especializadas e a capacidade de
descobrir como usá-las corretamente** (em vez de uma única ferramenta genérica e opaca) é também
um princípio central de projetos de agentes de IA eficazes, independente de RAG especificamente
(ver Anthropic, *Building Effective Agents*, na Seção 11).

## 4. Solução proposta

A solução tem quatro frentes, implementadas com componentes já prontos do módulo `spring-ai-rag`
(adicionado como nova dependência) em vez de lógica própria reinventada.

### 4.1. Reescrita, expansão e fusão de consultas (resolve os problemas 1 e 3)

Nova classe `AgenticRagSearchService`:

- Uma heurística simples e barata (`shouldExpand`) decide se a pergunta é "complexa" (mais de 6
  palavras, ou contém "and"/"or"/vírgula). Perguntas simples continuam funcionando exatamente
  como antes — sem custo ou latência extra.
- Para perguntas complexas, a pergunta passa primeiro pelo `RewriteQueryTransformer` do Spring AI
  (reescreve perguntas verbosas/ambíguas para um formato melhor otimizado para busca — recurso do
  módulo `spring-ai-rag` a partir do Spring AI 2.0, ver Seção 10) e só então pelo
  `MultiQueryExpander`, que usa um LLM para gerar ~3 reformulações da pergunta já reescrita.
  Reescrever antes de expandir segue exatamente o padrão "Advanced RAG" documentado pelo próprio
  Spring AI, e melhora a qualidade de cada uma das reformulações geradas em seguida.
- Cada reformulação é buscada **em paralelo** contra a base vetorial, pedindo o `topK` completo
  solicitado pelo chamador (não dividido entre as reformulações) — isso amplia deliberadamente a
  cobertura de recuperação.
- Os resultados de todas as reformulações são combinados e deduplicados por id de documento com
  o `ConcatenationDocumentJoiner` do Spring AI (mantendo a maior pontuação de similaridade de
  cada documento).
- O conjunto final é ordenado por pontuação e truncado de volta ao `topK` que o chamador pediu —
  o contrato da ferramenta ("no máximo N resultados") não muda; o que muda é a amplitude da busca
  interna.

Essa técnica é essencialmente RAG-Fusion (com um passo de reescrita antes) aplicado à ferramenta
MCP: mais "ângulos" de busca → mais chance de encontrar trechos relevantes que uma única
formulação da pergunta não alcançaria.

### 4.2. Validação e autodescoberta de filtros (resolve o problema 4)

Três peças novas:

- **`RagFilterSchema`**: catálogo estático dos campos de metadados que os importadores desta
  aplicação realmente gravam (`source`, `spaceKey`, `pageId`, `title`, `boardId`, `itemId`,
  `itemName`, `groupId`, `groupTitle`), com descrição e exemplo de valor para cada um.
- **`FilterExpressionValidator`**: reaproveita o próprio `FilterExpressionTextParser` do Spring
  AI (o mesmo parser usado na execução real da busca, garantindo que validação e execução nunca
  divirjam) para transformar o texto do filtro em uma árvore sintática, percorre essa árvore
  coletando todos os nomes de campo referenciados, e rejeita a chamada **antes** de qualquer
  busca acontecer se algum campo for desconhecido — retornando uma mensagem de erro acionável com
  a lista de campos válidos e um exemplo corrigido.
- **Nova ferramenta `getRagFilterSchema`**: permite que o agente **descubra** os campos válidos
  antes de escrever um filtro, em vez de adivinhar. Esse é o padrão "descobrir, depois
  consultar" (self-querying) mencionado na Seção 3.

Como efeito colateral dessa investigação, também foi descoberto que a sintaxe do operador `IN`
usa **colchetes** (`campo IN ['a', 'b']`), não parênteses como os exemplos antigos sugeriam —
mais um motivo pelo qual filtros com `IN` sempre falhavam silenciosamente.

### 4.3. Ferramenta de agregação para perguntas quantitativas (resolve o problema 2)

Nova ferramenta `aggregateRagDocuments`, apoiada por `RagAggregationService`:

- Em vez de tentar (e falhar) responder "quantos documentos..." através de busca por
  similaridade, essa ferramenta conta/agrupa os documentos já ingeridos, usando o registro em
  memória (`DocumentRegistry`) da aplicação — que agora também guarda o mapa completo de
  metadados de cada documento (`DocumentEntry.metadata`), não apenas o campo `source` como antes.
- Aceita um `filterExpression` opcional (validado da mesma forma que a busca) para restringir
  quais documentos entram na contagem.
- É explicitamente documentada como **não sendo** uma busca vetorial: é uma operação sobre o
  índice de metadados desta instância da aplicação em memória, portanto reflete apenas os
  documentos ingeridos nesta instância em execução.

### 4.4. Documentação corrigida

O arquivo `docs/mcp-search-rag-documents-examples.md` foi totalmente reescrito para: (a) descrever
corretamente a seleção de vector store via header HTTP `X-Vector-Store` (não mais um parâmetro
`vectorStoreName` inexistente), (b) usar apenas campos de metadados que realmente existem, (c)
corrigir a sintaxe do `IN`, e (d) documentar as duas novas ferramentas com exemplos.

## 5. Mapeamento problema → solução

| Problema relatado | Causa raiz | Solução |
|---|---|---|
| Consultas complexas perdem resultados relevantes | Um único embedding não cobre todos os aspectos da pergunta | Expansão multi-query + fusão (4.1) |
| Consultas quantitativas não funcionam | Busca por similaridade não faz contagem/agregação | Ferramenta `aggregateRagDocuments` (4.3) |
| Nem todos os dados parecem ser consultados | Mesma causa da complexidade + `topK` limitado a uma única formulação | Expansão multi-query amplia a cobertura de busca (4.1) |
| `filterExpression` frágil | Texto livre sem validação; documentação com exemplos incorretos (campos inexistentes, sintaxe errada do `IN`) | Validação de campos + ferramenta de autodescoberta `getRagFilterSchema` (4.2) + documentação corrigida (4.4) |

## 6. Arquitetura resumida

```
searchRagDocuments(query, topK, threshold, filterExpression)
        │
        ├─ filterExpression? ──► FilterExpressionValidator
        │                          (valida contra RagFilterSchema; erro acionável se inválido)
        │
        └─ AgenticRagSearchService.agenticSearch(...)
                   │
                   ├─ consulta "simples"? ──► busca única (VectorStoreDocumentRetriever)
                   │
                   └─ consulta "complexa"
                            │
                            ├─ RewriteQueryTransformer (LLM) ──► pergunta reescrita/limpa
                            ├─ MultiQueryExpander (LLM) ──► N reformulações da pergunta reescrita
                            ├─ busca paralela por reformulação (topK completo cada)
                            └─ ConcatenationDocumentJoiner (dedup) ──► ordena por score ──► trunca em topK

getRagFilterSchema()          ──► retorna RagFilterSchema.fields()

aggregateRagDocuments(groupByField, filterExpression)
        │
        ├─ filterExpression? ──► FilterExpressionValidator
        └─ RagAggregationService.aggregate(...)
                   └─ agrupa/conta DocumentEntry.metadata() do DocumentRegistry (em memória)
```

Principais arquivos novos: `AgenticRagSearchService`, `RagFilterSchema`,
`FilterExpressionValidator`, `RagAggregationService`, `AgenticRagProperties` (ajustes de
`número de consultas`/`limiar de complexidade` via `application.yaml`, chave
`app.rag.agentic`). Arquivos modificados: `RagSearchMcpTools` (fiação das novas ferramentas),
`DocumentEntry`/`DocumentRegistry` (metadados completos), `pom.xml` (dependência
`spring-ai-rag`), `docs/mcp-search-rag-documents-examples.md`.

## 7. Escopo e trabalho futuro

Esta mudança foca exclusivamente na ferramenta MCP `searchRagDocuments`, que é o caminho usado
por agentes de IA externos. O chat web da aplicação usa um caminho de RAG separado e mais simples
(`ChatService` → `QuestionAnswerAdvisor`, busca única, sem filtro configurável), desacoplado da
ferramenta MCP. Fica como recomendação futura substituir esse advisor por um
`RetrievalAugmentationAdvisor` do Spring AI, reaproveitando os mesmos componentes construídos
aqui (`VectorStoreDocumentRetriever`, catálogo de filtros) — não foi feito agora para manter o
escopo desta mudança restrito ao problema relatado.

Também vale registrar como observação (não implementada aqui): a qualidade da recuperação também
é influenciada pela estratégia de *chunking* (atualmente `TokenTextSplitter` com parâmetros
padrão em todos os importadores). Um *chunking* mais consciente da estrutura do documento
(markdown, cabeçalhos) poderia complementar as melhorias de recuperação descritas aqui, mas está
fora do escopo desta proposta.

## 8. Como verificar

- Testes unitários novos: `FilterExpressionValidatorTest`, `RagAggregationServiceTest`,
  `AgenticRagSearchServiceTest` (cobrindo validação de filtros, agregação e o comportamento de
  expansão/fusão, incluindo o caminho "consulta simples não expande").
- Teste manual via cliente MCP: chamar `getRagFilterSchema`, `searchRagDocuments` com uma
  consulta curta (sem expansão) e uma longa/composta (com expansão, visível nos logs), e
  `aggregateRagDocuments` para conferir contagens.

## 9. Validação do upgrade para Spring Boot 4.1.1 / Spring AI 2.0.1

O projeto foi atualizado para `spring-boot-starter-parent` `4.1.1` e `spring-ai-bom` `2.0.1`
(anteriormente `3.5.11`/`1.1.8`), com `java.version` `25`. Esta seção documenta a validação
dessa atualização — o que quebrou, por quê, e o que foi corrigido — já que **não é um upgrade
"drop-in"**: uma compilação limpa e totalmente offline (`rm -rf target && mvn -o compile`)
revelou quebras em cinco áreas, todas em infraestrutura pré-existente do projeto, **nenhuma no
código RAG agêntico** descrito neste documento.

**Achado principal:** `AgenticRagSearchService`, `RagFilterSchema`, `FilterExpressionValidator`
e `RagAggregationService` compilaram sem nenhuma alteração. Toda a superfície de API do
`spring-ai-rag` usada aqui (`MultiQueryExpander`, `VectorStoreDocumentRetriever`,
`ConcatenationDocumentJoiner`, `Query`) e do parser de filtros
(`FilterExpressionTextParser`/`Filter.Expression`) permaneceu estável entre as versões 1.1.8 e
2.0.1. As quebras encontradas foram todas em código de infraestrutura não relacionado ao RAG:

1. **Artefato Maven renomeado**: `spring-ai-advisors-vector-store` → `spring-ai-vector-store-advisor`.
2. **Integração MCP reestruturada**: o SDK do MCP saltou de `0.18.3` para `2.0.0`. A classe
   `WebFluxStatelessServerTransport` migrou de um artefato próprio do SDK
   (`io.modelcontextprotocol.sdk:mcp-spring-webflux`) para um artefato agora mantido pelo próprio
   Spring AI (`org.springframework.ai:mcp-spring-webflux`); `JacksonMcpJsonMapper` migrou de
   pacote (Jackson 3); `McpSchema.CallToolResult` trocou seu construtor simples por uma API de
   builder; `ModelOptionsUtils.jsonToObject`/`toJsonString` foram removidos. **Boa notícia**: o
   bug do Spring AI 1.1.5 que originalmente motivou a criação de uma `McpServerConfig` completa
   (contornando `McpStatelessAsyncServer` sem `.jsonMapper(...)`) está **corrigido oficialmente**
   a partir do Spring AI 2.0 (via `McpServerJsonMapperAutoConfiguration`). Por isso
   `McpServerConfig` foi drasticamente simplificada — hoje só sobrescreve um bean (o transporte
   WebFlux, unicamente para capturar os headers HTTP usados na seleção de vector store) — e a
   autoconfiguração padrão do Spring AI (`McpServerStatelessAutoConfiguration`) voltou a cuidar
   do resto, deixando de ser excluída em `application.yaml`.
3. **API do `ChatClient` mudou**: `.options(ChatOptions)` agora exige um `ChatOptions.Builder<?>`
   em vez de um objeto `ChatOptions` já construído. Correção trivial em `ChatService` (remover o
   `.build()` final antes de passar para `.options(...)`).
4. **Jackson 3 por padrão**: o Spring Boot 4.1 não auto-configura mais um `ObjectMapper` Jackson 2
   (`com.fasterxml.jackson.*`); o padrão agora é Jackson 3 (`tools.jackson.*`), cujo
   `jackson-databind` já inclui suporte nativo a `java.time` (sem precisar de um módulo JSR-310
   separado). Dois arquivos que dependiam de um `ObjectMapper` Jackson 2 (`ImportRecordS3Repository`
   e `RedisChatMemory`) foram migrados para `tools.jackson.databind.json.JsonMapper`.
5. **Cliente Redis trocado**: o Jedis pulou para a versão 7, que introduziu `RedisClient` como
   substituto moderno de `JedisPooled` (ambos estendem a mesma classe-base `UnifiedJedis`, então
   os mesmos comandos usados por `RedisChatMemory` continuam disponíveis). `RedisVectorStoreConfig`
   e `RedisChatMemory` — que compartilham a mesma conexão Redis — foram migrados para `RedisClient`.

**Resultado da validação**: após as correções acima, uma compilação limpa e offline
(`mvn -o compile`) resulta em zero erros, e a suíte de testes completa (`mvn -o test`) passa
integralmente — a única falha remanescente é a mesma falha pré-existente e não relacionada de
antes deste upgrade (`AiplaygroundApplicationTests.contextLoads`, que depende de um recurso
`docs/faq.txt` que nunca existiu no repositório, e de variáveis de ambiente como
`OPENAI_API_KEY`/Docker Compose não disponíveis neste ambiente de validação).

## 10. Spring AI 2.0: novidades relevantes e melhorias adicionais

O módulo `spring-ai-rag` cresceu significativamente da versão 1.1.8 para a 2.0.1. Esta seção
mapeia o que há de novo contra as melhorias já propostas neste documento, indicando o que foi
efetivamente incorporado e o que fica como sugestão para trabalho futuro.

### 10.1. Novos transformadores de consulta (`preretrieval.query.transformation`)

Esse pacote inteiro (`QueryTransformer` e três implementações) **não existia** no Spring AI
1.1.8 — é uma adição do Spring AI 2.0:

- **`RewriteQueryTransformer`** — reescreve perguntas verbosas/ambíguas/irrelevantes para um
  formato melhor otimizado para busca. **Incorporado** nesta atualização: agora compõe com a
  expansão multi-query no caminho de consultas complexas de `AgenticRagSearchService` (Seção 4.1),
  reescrevendo a pergunta antes de gerar as reformulações — o padrão "Advanced RAG" mostrado na
  própria documentação de referência do Spring AI.
- **`TranslationQueryTransformer`** — traduz a pergunta para o idioma-alvo do modelo de
  embeddings, retornando-a inalterada se já estiver no idioma certo. **Sugerido, não
  implementado**: os documentos ingeridos por esta aplicação misturam português (contexto da
  interface/chat) e inglês (READMEs técnicos, documentação de código), então fixar um único
  idioma-alvo exigiria saber o idioma predominante de cada vector store — informação que não é
  rastreada hoje. Recomenda-se, como trabalho futuro, tornar isso configurável por store (por
  exemplo `app.rag.redis.stores[].embedding-language`) antes de habilitar este transformador.
- **`CompressionQueryTransformer`** — comprime histórico de conversa + pergunta de acompanhamento
  em uma única pergunta autônoma. Não se aplica ao `searchRagDocuments` (ferramenta MCP
  *stateless*, sem histórico de conversa por chamada) — mas é a peça natural para levar RAG
  agêntico ao caminho do chat web, reforçando a recomendação já feita na Seção 7.

### 10.2. Ainda não há reranker/pós-processador pronto

`spring-ai-rag` 2.0.1 continua expondo apenas a **interface** `DocumentPostProcessor`, sem
nenhuma implementação concreta embutida (nem reranker, nem compressor, nem deduplicador —
confirmado inspecionando diretamente o conteúdo do jar `spring-ai-rag-2.0.1.jar`). Ou seja, a
etapa manual de ordenar por score e truncar em `topK` (`AgenticRagSearchService.fuse()`)
continua sendo a única forma de "pós-processamento" disponível nesta ferramenta.
**Sugestão de trabalho futuro**: implementar um `DocumentPostProcessor` customizado — por
exemplo, um reranker leve baseado em LLM que pontua a relevância de cada trecho recuperado
contra a pergunta original antes do corte final em `topK` — e conectá-lo em
`AgenticRagSearchService`.

### 10.3. Pista investigada e descartada: "Tool Search"

O Spring AI 2.0 introduziu um novo módulo (`spring-ai-tool-search-tool`,
`spring-ai-tool-search-advisor`, com backends em vector store/Lucene/regex,
pacote `org.springframework.ai.tool.toolsearch`). Ele aparece na barra de navegação da
documentação de RAG, o que motivou a investigação — mas resolve um **problema diferente**:
permitir que um agente com um catálogo muito grande de ferramentas (`@Tool` methods) *pesquise*
qual ferramenta chamar, em vez de receber todas de uma vez no contexto. Não tem relação com
busca de documentos RAG e **não é aplicável** ao `searchRagDocuments`.

### 10.4. Por que não usar `RetrievalAugmentationAdvisor` diretamente

O `RetrievalAugmentationAdvisor` 2.0.1 confirma que a arquitetura desta proposta está correta:
ele orquestra exatamente os mesmos blocos usados manualmente em `AgenticRagSearchService`
(`queryTransformers`, `queryExpander`, `documentRetriever`, `documentJoiner`,
`documentPostProcessors`, `queryAugmenter`). A diferença é que esse advisor só pode ser
plugado em uma cadeia de advisors do `ChatClient` (ele implementa `BaseAdvisor` sobre
`ChatClientRequest`/`ChatClientResponse`) — e a ferramenta MCP `searchRagDocuments` não passa
por um `ChatClient.prompt()`, ela é um método Java chamado diretamente pelo protocolo MCP. Por
isso a orquestração manual dos componentes individuais (em vez de compor um
`RetrievalAugmentationAdvisor`) continua sendo a abordagem correta aqui — mas é exatamente o que
tornaria trivial reaproveitar os mesmos componentes na recomendação da Seção 7 (levar RAG
agêntico para o `ChatService`, que já passa por um `ChatClient.prompt()`).

## 11. Referências

- Spring AI Reference — *Retrieval Augmented Generation* (reflete a versão 2.0.1, usada nesta
  proposta — inclui `RewriteQueryTransformer`, `TranslationQueryTransformer`,
  `CompressionQueryTransformer` e `ContextualQueryAugmenter`, todos ausentes na 1.1.8):
  https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- Spring AI Reference — *Advisors* (incluindo `QuestionAnswerAdvisor` e
  `RetrievalAugmentationAdvisor`): https://docs.spring.io/spring-ai/reference/api/advisors.html
- Spring AI Reference — *Vector Databases* (sintaxe de `filterExpression`):
  https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- Rackauckas, Z. (2024). *RAG-Fusion: A New Take on Retrieval-Augmented Generation.*
  International Journal on Natural Language Computing. https://arxiv.org/abs/2402.03367
- LangChain — *Multi-Query Retriever* (documentação conceitual sobre expansão de consultas):
  https://python.langchain.com/docs/how_to/MultiQueryRetriever/
- LangChain — *Self-querying retrievers* (documentação conceitual sobre filtros auto-descobertos):
  https://python.langchain.com/docs/how_to/self_query/
- Anthropic — *Building Effective Agents* (princípios de ferramentas especializadas e
  descobríveis para agentes de IA): https://www.anthropic.com/research/building-effective-agents
