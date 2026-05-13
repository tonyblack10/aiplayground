# Spring AI Playground — Manual do Usuário

**Versão 1.1.3**

---

## Sumário

1. [Introdução](#1-introdução)
2. [Primeiros Passos — Como Fazer Login](#2-primeiros-passos--como-fazer-login)
3. [Navegando pela Aplicação](#3-navegando-pela-aplicação)
4. [Chat — Conversando com o Assistente de IA](#4-chat--conversando-com-o-assistente-de-ia)
   - 4.1 [Enviando sua Primeira Mensagem](#41-enviando-sua-primeira-mensagem)
   - 4.2 [Escolhendo o Modelo de IA](#42-escolhendo-o-modelo-de-ia)
   - 4.3 [Ajustando o Estilo das Respostas (Temperatura)](#43-ajustando-o-estilo-das-respostas-temperatura)
   - 4.4 [Usando seus Documentos para Melhorar as Respostas (RAG)](#44-usando-seus-documentos-para-melhorar-as-respostas-rag)
   - 4.5 [Limpando a Conversa](#45-limpando-a-conversa)
5. [Gerenciamento de RAG — Trabalhando com seus Documentos](#5-gerenciamento-de-rag--trabalhando-com-seus-documentos)
   - 5.1 [Entendendo os Repositórios de Documentos (Vector Stores)](#51-entendendo-os-repositórios-de-documentos-vector-stores)
   - 5.2 [Adicionando Documentos — Upload de Arquivo](#52-adicionando-documentos--upload-de-arquivo)
   - 5.3 [Adicionando Documentos — Importação do GitHub](#53-adicionando-documentos--importação-do-github)
   - 5.4 [Adicionando Documentos — Importação do Confluence](#54-adicionando-documentos--importação-do-confluence)
   - 5.5 [Adicionando Documentos — Importação do Monday.com](#55-adicionando-documentos--importação-do-mondaycom)
   - 5.6 [Adicionando Documentos — Importação do AWS S3](#56-adicionando-documentos--importação-do-aws-s3)
   - 5.7 [Adicionando Documentos — Importação por Links](#57-adicionando-documentos--importação-por-links)
   - 5.8 [Pesquisando seus Documentos](#58-pesquisando-seus-documentos)
   - 5.9 [Gerenciando Documentos Existentes](#59-gerenciando-documentos-existentes)
   - 5.10 [Restaurando Documentos](#510-restaurando-documentos)
6. [Token de API — Gerando Credenciais de Acesso](#6-token-de-api--gerando-credenciais-de-acesso)
7. [Saindo da Aplicação](#7-saindo-da-aplicação)
8. [Perguntas Frequentes](#8-perguntas-frequentes)
9. [Glossário](#9-glossário)

---

## 1. Introdução

O **Spring AI Playground** é uma aplicação web que permite ter conversas inteligentes com um assistente de inteligência artificial e usar os documentos da sua própria organização para obter respostas mais precisas e relevantes.

Pense nele como um assistente inteligente que não apenas possui um vasto conhecimento geral, mas também consegue ler os documentos da sua empresa — relatórios, manuais, políticas, atas de reunião — e usar esse conteúdo para responder às suas perguntas de forma muito mais específica.

### Para quem é este manual?

Este manual foi escrito para profissionais das áreas de **tecnologia** e **governança** que desejam:

- Fazer perguntas e obter respostas com o suporte de inteligência artificial.
- Enviar documentos (como políticas, procedimentos e relatórios) e torná-los pesquisáveis pela IA.
- Importar conhecimento de ferramentas internas como Confluence, Monday.com, GitHub ou armazenamento em nuvem (AWS S3).
- Gerar credenciais de acesso para conectar outros sistemas ao assistente de IA.

Nenhuma experiência prévia com inteligência artificial é necessária.

---

## 2. Primeiros Passos — Como Fazer Login

Ao abrir a aplicação no seu navegador, você verá a **página de Login**.

> **[IMAGEM: Captura de tela da página de login mostrando os campos de nome de usuário e senha e o botão "Entrar"]**

### Passos para fazer login:

1. No campo **Nome de usuário**, digite seu usuário (por exemplo: `user01`).
2. No campo **Senha**, digite sua senha.
3. Clique no botão **Entrar**.

Se suas credenciais estiverem corretas, você será redirecionado automaticamente para a **página de Chat**.

> **Observação:** Se aparecer uma mensagem de erro em vermelho após clicar em "Entrar", verifique novamente seu nome de usuário e senha. As senhas diferenciam letras maiúsculas de minúsculas.

### Qual é o meu nome de usuário e senha?

Suas credenciais de acesso são fornecidas pelo administrador do sistema. Se você não souber seu usuário ou senha, entre em contato com a equipe de TI ou suporte.

### Níveis de acesso

Diferentes usuários podem ter acesso a diferentes partes da aplicação, dependendo das permissões atribuídas pelo administrador. Alguns usuários têm acesso a todas as funcionalidades; outros podem visualizar apenas determinados repositórios de documentos. Isso é normal — trata-se de um recurso de segurança que controla o que cada usuário pode ver e fazer.

---

## 3. Navegando pela Aplicação

Após fazer login, você verá uma **barra de navegação** no topo de todas as páginas.

> **[IMAGEM: Captura de tela da barra de navegação superior com o nome da aplicação, os links Chat, Gerenciamento de RAG e Token de API, o nome do usuário e o botão Sair]**

A barra de navegação contém os seguintes links:

| Link | O que faz |
|---|---|
| **Chat** | Abre a página de conversa com o assistente de IA |
| **Gerenciamento de RAG** | Abre a área de gerenciamento de documentos (pode não aparecer caso você não tenha a permissão necessária) |
| **Token de API** | Abre a página para gerar um token de acesso para integrações externas |
| **Sair** | Encerra sua sessão e retorna para a página de login |

Seu **nome de usuário** é exibido no lado direito da barra de navegação, para que você saiba sempre qual conta está ativa.

---

## 4. Chat — Conversando com o Assistente de IA

A **página de Chat** é onde você conversa com o assistente de IA. Você pode fazer perguntas, solicitar resumos, pedir explicações ou pedir ao assistente que escreva textos para você.

> **[IMAGEM: Captura de tela completa da página de Chat, mostrando o painel de configurações à esquerda e a área de conversa à direita]**

### 4.1 Enviando sua Primeira Mensagem

1. Clique no link **Chat** na barra de navegação.
2. Na parte inferior da página, você verá uma caixa de texto com o texto de exemplo *"Digite sua mensagem... (Ctrl+Enter para enviar)"*.
3. Clique dentro da caixa de texto e digite sua pergunta ou mensagem.
4. Pressione **Ctrl+Enter** no teclado ou clique no **botão de envio** azul (ícone de avião de papel) à direita.
5. Sua mensagem aparecerá no lado direito da tela (em um balão azul) e a resposta do assistente aparecerá no lado esquerdo (em um balão cinza) conforme for sendo gerada.

> **[IMAGEM: Captura de tela da área de chat com uma mensagem do usuário à direita e uma resposta do assistente à esquerda, mostrando a conversa em andamento]**

> **Dica:** A caixa de texto se expande automaticamente conforme você digita mensagens mais longas. Você pode escrever mensagens de várias linhas e a caixa crescerá para acomodar o texto.

### 4.2 Escolhendo o Modelo de IA

No lado esquerdo da página de Chat, há um **painel de configurações**. A primeira opção é um menu suspenso chamado **Modelo**.

> **[IMAGEM: Captura de tela do menu suspenso de seleção de modelo na barra lateral esquerda, mostrando as opções disponíveis]**

O modelo de IA é o "cérebro" por trás do assistente. Modelos diferentes têm capacidades diferentes:

| Modelo | Descrição |
|---|---|
| **gpt-4o-mini** | Mais rápido e econômico. Ótimo para perguntas simples e tarefas do dia a dia. |
| **gpt-4o** | Mais poderoso. Melhor para raciocínio complexo, análises detalhadas e redação elaborada. |
| **gpt-4-turbo** | Equilibrado entre velocidade e capacidade. Uma boa escolha para uso geral. |

**Como trocar o modelo:**

1. Na barra lateral esquerda, clique no menu suspenso **Modelo**.
2. Selecione o modelo que deseja usar.
3. O modelo selecionado será usado na próxima mensagem e em todas as mensagens seguintes da conversa.

> **Recomendação para equipes de governança e políticas:** Para revisar documentos longos, redigir textos formais ou realizar análises complexas, use o **gpt-4o**. Para perguntas rápidas e tarefas simples, o **gpt-4o-mini** é mais rápido e suficiente.

### 4.3 Ajustando o Estilo das Respostas (Temperatura)

Abaixo do seletor de modelo, há um controle deslizante chamado **Temperatura**. Ele controla o quanto as respostas da IA serão criativas ou precisas.

> **[IMAGEM: Captura de tela do controle de Temperatura na barra lateral, mostrando o controle posicionado no meio e o valor numérico exibido]**

- **Mover o controle para a esquerda (em direção a 0,0):** O assistente dará respostas mais **precisas e consistentes**. Ideal para perguntas factuais, explicações técnicas ou quando você precisa de resultados confiáveis e repetíveis.
- **Mover o controle para a direita (em direção a 1,0):** O assistente dará respostas mais **criativas e variadas**. Ideal para brainstorming, geração de ideias ou criação de conteúdo criativo.

**Como ajustar a temperatura:**

1. Na barra lateral esquerda, localize o controle deslizante de **Temperatura**.
2. Clique e arraste o controle para a esquerda ou para a direita.
3. O valor atual é exibido ao lado do controle (por exemplo: `0,7`).

> **Recomendação:** Para a maioria das tarefas de governança e conformidade (resumir políticas, responder perguntas sobre regulamentos), mantenha a temperatura entre **0,2 e 0,5** para obter respostas mais precisas e confiáveis.

### 4.4 Usando seus Documentos para Melhorar as Respostas (RAG)

Um dos recursos mais poderosos desta aplicação é o **RAG** (sigla em inglês para Geração com Recuperação Aumentada). Em termos simples, significa que a IA pode pesquisar nos documentos da sua organização e usar o que encontrar para dar uma resposta melhor e mais específica — em vez de depender apenas do seu conhecimento geral.

> **[IMAGEM: Captura de tela da seção RAG na barra lateral, mostrando a caixa de seleção "Habilitar RAG" marcada e o menu suspenso de Repositório de Documentos revelado abaixo]**

**Exemplo:** Em vez de perguntar à IA "Qual é a política de férias da nossa empresa?", você pode ativar o RAG, apontá-lo para seus documentos e fazer a mesma pergunta — a IA irá pesquisar nos seus documentos de política enviados e responder com base no conteúdo real.

**Como ativar o RAG:**

1. Na barra lateral esquerda, localize a caixa de seleção **Habilitar RAG**.
2. Clique na caixa para ativá-la. Uma marca de seleção aparecerá e uma nova opção surgirá abaixo.
3. No menu suspenso **Repositório de Documentos** que aparecer, selecione qual coleção de documentos você deseja que a IA pesquise.
4. Agora envie sua mensagem normalmente. A IA pesquisará seus documentos e usará os resultados para embasar sua resposta.

> **Observação:** O menu suspenso de Repositório de Documentos mostra apenas as coleções que sua conta tem permissão para acessar. Se estiver vazio ou não aparecer, entre em contato com o seu administrador.

> **Importante:** Para que o RAG seja útil, os documentos precisam ter sido previamente enviados ou importados para o repositório selecionado. Consulte a [Seção 5](#5-gerenciamento-de-rag--trabalhando-com-seus-documentos) para aprender como adicionar documentos.

### 4.5 Limpando a Conversa

O assistente de IA se lembra do contexto da sua conversa atual (até as últimas 20 mensagens). Se quiser iniciar uma conversa completamente nova sobre outro assunto, você pode limpar o histórico do chat.

**Como limpar a conversa:**

1. Na barra lateral esquerda, clique no botão **Limpar Conversa** (ícone de lixeira).
2. Uma caixa de confirmação aparecerá perguntando se você tem certeza.
3. Clique em **Confirmar** para apagar o histórico da conversa.
4. A área de chat será reiniciada e o assistente começará do zero.

> **Observação:** Limpar a conversa afeta apenas a sua sessão atual. Nenhum documento é removido dos repositórios.

---

## 5. Gerenciamento de RAG — Trabalhando com seus Documentos

A seção **Gerenciamento de RAG** é onde você gerencia os documentos que a IA pode usar para responder às suas perguntas. Você pode adicionar novos documentos, pesquisar nos existentes e remover os que não precisar mais.

> **[IMAGEM: Captura de tela da página de Gerenciamento de RAG, mostrando a barra lateral esquerda com a lista de repositórios e a área de conteúdo principal com as abas]**

### 5.1 Entendendo os Repositórios de Documentos (Vector Stores)

Um **repositório de documentos** (também chamado de *vector store*) é um tipo especial de banco de dados que armazena seus documentos de forma a torná-los pesquisáveis por significado — não apenas por palavras-chave exatas.

Ao navegar para o **Gerenciamento de RAG**, você verá uma lista de repositórios na barra lateral esquerda. Cada repositório é identificado por uma cor e um nome:

| Repositório | Cor | Descrição |
|---|---|---|
| **Simple** | Verde | Armazena os documentos na memória. Rápido e simples, adequado para testes e conjuntos pequenos de documentos. |
| **pgvector** | Azul | Baseado em banco de dados PostgreSQL. Mais robusto e adequado para coleções maiores de documentos. |
| **Redis** | Vermelho | Baseado em Redis. Alto desempenho, adequado para consultas frequentes. |

Clique em qualquer repositório na barra lateral para selecioná-lo. O número ao lado do nome mostra quantos documentos estão armazenados nele no momento.

> **Observação:** Você pode não ver os três repositórios, dependendo das permissões da sua conta.

Ao clicar em um repositório, você verá a área de conteúdo principal com até quatro abas: **Adicionar**, **Buscar**, **Gerenciar Documentos** e **Restaurar**.

### 5.2 Adicionando Documentos — Upload de Arquivo

Você pode enviar arquivos diretamente do seu computador. A aplicação aceita uma grande variedade de formatos, incluindo PDF, documentos Word, planilhas Excel, apresentações PowerPoint, arquivos Markdown, HTML e XML.

> **[IMAGEM: Captura de tela da aba "Adicionar" com a sub-aba "Upload de Arquivo" selecionada, mostrando a área de arrastar e soltar e a lista de arquivos]**

**Passos para enviar arquivos:**

1. Acesse o **Gerenciamento de RAG** e clique no repositório desejado na barra lateral esquerda.
2. Clique na aba **Adicionar**.
3. Certifique-se de que a sub-aba **Upload de Arquivo** está selecionada (é o padrão).
4. Faça uma das seguintes opções:
   - **Arraste e solte** seus arquivos diretamente na caixa tracejada com o texto *"Arraste arquivos ou clique para selecionar"*, ou
   - Clique no botão **Escolher arquivos** para abrir o gerenciador de arquivos e selecionar um ou mais arquivos.
5. Os arquivos selecionados aparecerão em uma lista abaixo da área de upload, mostrando o nome e o tamanho de cada um.
   - Se você adicionou um arquivo por engano, clique no botão **X** ao lado dele para removê-lo.
6. Verifique o tamanho total na parte inferior. O limite é de **12 MB** no total. Se exceder esse limite, remova alguns arquivos antes de continuar.
7. Quando estiver pronto, clique no botão **Ingerir Documentos**.
8. Uma mensagem de carregamento (*"Processando arquivos..."*) aparecerá enquanto os arquivos são processados. Aguarde a conclusão.
9. Um resumo dos resultados aparecerá, mostrando se cada arquivo foi processado com sucesso ou se houve algum erro.

> **[IMAGEM: Captura de tela dos resultados do upload, mostrando os selos verdes "Sucesso" e o número de fragmentos criados por arquivo]**

> **O que é um "fragmento" (chunk)?** Quando um documento é ingerido, ele é automaticamente dividido em partes menores (chamadas de fragmentos) para que a IA possa pesquisar nele de forma mais eficiente. Um documento típico pode ser dividido em dezenas ou centenas de fragmentos, dependendo do seu tamanho.

Após revisar os resultados, clique em **Novo Upload** para enviar mais arquivos ou navegue para outra seção.

### 5.3 Adicionando Documentos — Importação do GitHub

Você pode importar documentação diretamente de um repositório do GitHub. Somente **arquivos Markdown (.md)** são importados dos repositórios.

> **[IMAGEM: Captura de tela da aba de importação do GitHub mostrando as duas opções de modo (Clonar Repositório e Importar por Links) e os campos do formulário]**

Há duas formas de importar do GitHub:

---

#### Opção A: Clonar Repositório

Esta opção baixa o repositório inteiro (ou pastas específicas dentro dele) e importa todos os arquivos Markdown encontrados.

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do GitHub**.
2. Certifique-se de que **Clonar Repositório** está selecionado.
3. No campo **URL HTTPS do Repositório**, cole a URL completa do repositório GitHub (por exemplo: `https://github.com/sua-org/seu-repositorio.git`).
4. No campo **Branch**, digite o nome do branch que deseja importar (o padrão é `main`). Se não tiver certeza, deixe como `main`.
5. *(Opcional)* No campo **Pastas a importar**, você pode especificar quais pastas dentro do repositório incluir. Digite um caminho de pasta por linha (por exemplo: `docs` ou `guias/politicas`). Se deixar em branco, o repositório inteiro será verificado.
6. Clique no botão **Clonar e Importar**.
7. Aguarde a conclusão do processo. Uma mensagem de sucesso ou erro aparecerá.

> **Observação:** A importação usa um token do GitHub configurado pelo administrador. Se o repositório for privado e a importação falhar, verifique com sua equipe de TI.

---

#### Opção B: Importar por Links

Esta opção importa arquivos específicos pelas suas URLs individuais. Aceita links de arquivos do GitHub, links de conteúdo bruto (*raw*) e links para páginas HTML ou Markdown.

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do GitHub**.
2. Selecione **Importar por Links**.
3. Na área de texto **Links para importar**, cole um link por linha. Por exemplo:
   ```
   https://github.com/sua-org/seu-repositorio/blob/main/docs/politica.md
   https://github.com/sua-org/seu-repositorio/blob/main/guias/integracao.md
   ```
4. Clique no botão **Importar Links**.
5. Os resultados aparecerão para cada link, mostrando o sucesso ou os erros individualmente.

### 5.4 Adicionando Documentos — Importação do Confluence

Você pode importar páginas do seu espaço de trabalho Confluence diretamente para o repositório de documentos da IA.

> **[IMAGEM: Captura de tela da aba de importação do Confluence, mostrando o campo Chave do Space, o botão de alternância de modo (Space Completo / Páginas Específicas) e o botão de envio]**

**Antes de começar:** O administrador deve ter configurado a conexão com o Confluence (URL base, e-mail e token de API) nas configurações da aplicação. Em caso de dúvida, entre em contato com a equipe de TI.

---

#### Opção A: Importar Space Completo

Esta opção importa todas as páginas de um determinado Space do Confluence.

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do Confluence**.
2. No campo **Chave do Space**, insira a chave do Space do Confluence que deseja importar (por exemplo: `PROJ`, `RH` ou `JURIDICO`).
   > **Onde encontro a Chave do Space?** Abra o Space do Confluence no navegador. A Chave do Space aparece na URL, por exemplo: `.../wiki/spaces/PROJ`.
3. Certifique-se de que **Importar Space Completo** está selecionado.
4. Clique no botão **Importar Space**.
5. Aguarde a conclusão do processo. Um resumo aparecerá mostrando quantas páginas foram encontradas, quantas foram importadas com sucesso e quantos fragmentos foram adicionados.

---

#### Opção B: Importar Páginas Específicas

Esta opção importa apenas as páginas que você especificar pelos seus IDs.

**Passos:**

1. Siga os passos 1 e 2 da Opção A.
2. Selecione **Páginas Específicas**.
3. No campo **IDs das Páginas** que aparecer, insira um ID de página por linha (por exemplo: `123456`, `789012`).
   > **Onde encontro o ID de uma página?** Abra a página no Confluence. O ID da página está na URL, por exemplo: `.../pages/123456/Titulo+da+Pagina`.
4. Clique no botão **Importar Páginas**.
5. Um resumo dos resultados aparecerá.

### 5.5 Adicionando Documentos — Importação do Monday.com

Você pode importar itens de um quadro do Monday.com para o repositório de documentos. Isso é útil quando sua equipe acompanha projetos, requisitos ou decisões no Monday.com e você quer que a IA possa responder perguntas sobre eles.

> **[IMAGEM: Captura de tela da aba de importação do Monday.com, mostrando o campo ID do Board, o botão de alternância de modo e o botão de envio]**

**Antes de começar:** O administrador deve ter configurado o token de API do Monday.com nas configurações da aplicação.

---

#### Opção A: Importar Board Completo

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do Monday**.
2. No campo **ID do Board**, insira o ID do quadro Monday.com que deseja importar.
   > **Onde encontro o ID do Board?** Abra o quadro no Monday.com. O ID está na URL, por exemplo: `.../boards/1234567890`.
3. Certifique-se de que **Importar Board Completo** está selecionado.
4. Clique no botão **Importar Board**.
5. Aguarde a conclusão do processo. Um resumo mostrará quantos itens foram encontrados e importados.

---

#### Opção B: Importar Itens Específicos

**Passos:**

1. Siga os passos 1 e 2 da Opção A.
2. Selecione **Itens Específicos**.
3. No campo **IDs dos Itens**, insira um ID de item por linha.
   > **Onde encontro o ID de um item?** Abra o item no Monday.com. O ID aparece na URL.
4. Clique no botão **Importar Itens**.
5. Os resultados aparecerão para cada item.

### 5.6 Adicionando Documentos — Importação do AWS S3

Você pode importar documentos armazenados em um bucket de armazenamento em nuvem AWS S3. Isso é útil para organizações que mantêm seus arquivos na nuvem.

> **[IMAGEM: Captura de tela da aba de importação do S3, mostrando o campo Nome do Bucket, o campo opcional Pasta e as caixas de seleção de formatos de arquivo]**

**Antes de começar:** O administrador deve ter configurado as credenciais AWS (Chave de Acesso, Chave Secreta e Região) nas configurações da aplicação.

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do S3**.
2. No campo **Nome do Bucket**, insira o nome exato do seu bucket S3 (por exemplo: `documentos-da-empresa`).
3. *(Opcional)* No campo **Pasta**, insira o caminho de uma pasta se quiser importar apenas documentos de uma pasta específica dentro do bucket (por exemplo: `politicas/2024/`). Deixe em branco para importar do bucket inteiro.
4. Na seção **Formatos de arquivo**, marque as caixas dos tipos de arquivo que deseja importar. Os formatos disponíveis incluem:
   - **PDF** — Formato de Documento Portátil
   - **DOCX / DOC** — Documentos Microsoft Word
   - **MD** — Arquivos Markdown
   - **TXT** — Arquivos de texto simples
   - **HTML** — Páginas da web
   - **XLSX** — Planilhas Microsoft Excel
   - **PPTX** — Apresentações Microsoft PowerPoint
   - **XML** — Arquivos XML
5. Você deve selecionar **pelo menos um formato**. Se nenhum for selecionado, o formulário exibirá uma mensagem de erro.
6. Clique no botão **Importar do S3**.
7. Aguarde a conclusão do processo. Um resumo dos resultados aparecerá.

### 5.7 Adicionando Documentos — Importação por Links

Você pode importar conteúdo de qualquer página da web publicamente acessível fornecendo sua URL. A aplicação buscará o conteúdo de cada link e o importará para o repositório de documentos.

> **[IMAGEM: Captura de tela da sub-aba de Importação por Links mostrando a área de texto onde os links são inseridos]**

**Passos:**

1. Na aba **Adicionar**, clique na sub-aba **Importar do GitHub** e selecione **Importar por Links** (esta sub-aba gerencia todas as importações por URL, não apenas do GitHub).
2. Na área de texto, cole uma URL por linha. Por exemplo:
   ```
   https://www.exemplo.com.br/relatorio-anual.html
   https://www.exemplo.com.br/politica-de-governanca.pdf
   ```
3. Os links devem começar com `http://` ou `https://`. Links inválidos ou inacessíveis serão reportados individualmente nos resultados.
4. Clique no botão **Importar Links**.
5. Os resultados aparecerão para cada URL, mostrando se foi importado com sucesso ou se houve algum erro.

### 5.8 Pesquisando seus Documentos

A aba **Buscar** permite encontrar conteúdo relevante em todos os documentos de um repositório — mesmo que você não se lembre das palavras exatas usadas nos documentos. A pesquisa funciona por significado, não apenas por palavras-chave.

> **[IMAGEM: Captura de tela da aba de Busca mostrando o campo de pesquisa, o botão Buscar e uma lista de resultados com selos de classificação e prévias do conteúdo]**

**Pesquisa básica:**

1. Acesse o **Gerenciamento de RAG** e clique no repositório desejado.
2. Clique na aba **Buscar**.
3. Na caixa de pesquisa, digite uma pergunta ou uma frase descrevendo o que você está procurando (por exemplo: *"política de férias e licenças"* ou *"procedimento de resposta a incidentes de segurança"*).
4. Clique no botão **Buscar**.
5. Os resultados aparecerão abaixo, ordenados por relevância. Cada resultado mostra:
   - Um **número de classificação** (o resultado mais relevante é o #1).
   - O **arquivo de origem** de onde o conteúdo veio.
   - Uma **prévia** do texto relevante.

**Opções avançadas de pesquisa:**

Clique na seção **Filtros Avançados** (uma área que pode ser expandida abaixo do botão de busca) para acessar controles adicionais:

| Opção | O que faz | Valor recomendado |
|---|---|---|
| **Número de resultados** | Quantos resultados retornar. | 5 (padrão); aumente para pesquisas mais amplas |
| **Limiar de similaridade** | Quão próximos os resultados devem estar da sua busca (0 = qualquer correspondência, 1 = exata). | 0 (padrão); aumente para 0,5–0,7 para filtragem mais rigorosa |
| **Expressão de filtro** | Avançado: filtra por fonte do documento. | Deixe em branco a menos que seja orientado de outra forma |

> **Dica:** Se a pesquisa retornar muitos resultados irrelevantes, tente aumentar o limiar de similaridade para 0,4 ou 0,5 e pesquise novamente.

### 5.9 Gerenciando Documentos Existentes

A seção **Gerenciar Documentos** exibe todos os documentos atualmente importados no repositório selecionado.

> **[IMAGEM: Captura de tela da tabela de gerenciamento de documentos mostrando linhas com nomes de origem, prévias do conteúdo, datas de ingestão e botões de ação]**

**Visualizando documentos:**

A tabela exibe:
- **Fonte** — O nome do arquivo ou a origem de onde o documento veio.
- **Prévia** — Um trecho curto do conteúdo do documento.
- **Ingerido em** — A data e hora em que o documento foi adicionado.
- **Ações** — Botões para gerenciar documentos individualmente.

**Excluindo um único documento:**

1. Localize o documento que deseja remover.
2. Clique no botão **Remover** (em vermelho) na coluna de Ações daquela linha.
3. Uma caixa de confirmação aparecerá. Confirme que deseja excluí-lo.
4. O documento será removido do repositório.

**Excluindo vários documentos de uma vez:**

1. Marque as caixas de seleção ao lado dos documentos que deseja excluir.
   - Use a caixa de seleção no cabeçalho da tabela para selecionar todos os documentos de uma vez.
2. Clique no botão **Remover Selecionados** (em vermelho, no canto superior direito da tabela).
3. Uma caixa de confirmação mostrará quantos documentos serão excluídos. Confirme para prosseguir.

> **Atenção:** A exclusão de documentos do repositório é permanente. Uma vez removidos, a IA não terá mais acesso a esse conteúdo, a menos que seja reimportado.

### 5.10 Restaurando Documentos

A aba **Restaurar** permite reimportar todos os documentos que foram adicionados anteriormente a um repositório por meio de fontes externas (GitHub, Confluence, Monday.com, S3 ou links). Isso é útil quando um repositório precisa ser reconstruído após perda de dados ou quando você deseja atualizar todo o conteúdo para sua versão mais recente.

> **[IMAGEM: Captura de tela da aba Restaurar mostrando o banner de aviso, o botão "Iniciar Restauração" e uma lista de progresso com indicadores de sucesso e falha]**

> **Avisos importantes antes de restaurar:**
> - Documentos originalmente adicionados por **upload de arquivo** **não são restaurados** — os arquivos originais não são retidos pela aplicação.
> - O processo de restauração **reexecuta** cada importação original (clonar repositórios, buscar páginas do Confluence, etc.), o que pode levar um tempo considerável.
> - Se já existirem documentos no repositório, eles podem ser **duplicados**. Considere remover os documentos existentes antes de iniciar uma restauração.

**Passos para restaurar:**

1. Acesse o **Gerenciamento de RAG** e clique no repositório desejado.
2. Clique na aba **Restaurar**.
3. Leia os banners de aviso com atenção.
4. Clique no botão **Iniciar Restauração**.
5. Uma lista de progresso aparecerá, mostrando o status de cada item sendo reimportado (sucesso ou falha), juntamente com um contador de quantos foram processados.
6. Quando o processo for concluído, um cartão de resumo mostrará:
   - **Total** de registros encontrados
   - **Restaurados** (verde) — reimportados com sucesso
   - **Falhas** (vermelho) — itens que não puderam ser restaurados

**Cancelando uma restauração em andamento:**

Se precisar interromper a restauração antes de concluir, clique no botão **Cancelar**. O processo será interrompido e o resumo mostrará os resultados até aquele ponto.

---

## 6. Token de API — Gerando Credenciais de Acesso

A página **Token de API** permite gerar uma credencial de acesso temporária (chamada de token JWT) que sistemas externos ou desenvolvedores podem usar para se conectar a esta aplicação de forma automatizada.

> **[IMAGEM: Captura de tela da página de Token de API mostrando o cartão de informações do usuário, o botão "Gerar Token JWT" e o token resultante em uma área de texto com um botão Copiar]**

> **Quem precisa disso?** Esse recurso é destinado principalmente a equipes de TI e desenvolvedores que desejam conectar outras ferramentas ou sistemas automatizados ao assistente de IA. Se você for um usuário comum, provavelmente não precisará usar esta página.

**Passos para gerar um token:**

1. Clique em **Token de API** na barra de navegação superior.
2. Você verá seu **nome de usuário** e suas **permissões de conta** listados na página.
3. Clique no botão **Gerar Token JWT**.
4. Um token (uma longa sequência de letras e números) aparecerá na área de texto abaixo.
5. Clique no botão **Copiar** para copiar o token para a área de transferência. O botão mudará brevemente para **Copiado!** como confirmação.

**Configuração do cliente MCP:**

Abaixo do token, um bloco de configuração pré-formatado também é fornecido para conexão via padrão MCP (Model Context Protocol). Clique em **Copiar JSON** para copiar essa configuração.

> **Aviso de segurança:** Trate seu token como uma senha. Não o compartilhe por e-mail ou aplicativos de mensagens. Os tokens expiram após 24 horas, após o que você precisará gerar um novo.

---

## 7. Saindo da Aplicação

Para encerrar sua sessão com segurança:

1. Clique no botão **Sair** no lado direito da barra de navegação superior.
2. Você será redirecionado para a página de login.
3. Uma mensagem de confirmação aparecerá brevemente na página de login indicando que você saiu com sucesso.

> **Boas práticas:** Sempre saia da aplicação ao terminar de usá-la, especialmente em computadores compartilhados ou públicos.

---

## 8. Perguntas Frequentes

**P: A IA me deu uma resposta errada ou desatualizada. O que devo fazer?**

R: A IA pode ocasionalmente produzir respostas imprecisas. Sempre verifique informações importantes em fontes autoritativas. Se você estiver perguntando sobre as políticas ou procedimentos da sua organização, ative o recurso RAG e aponte-o para o repositório de documentos relevante — isso melhorará significativamente a precisão.

---

**P: Como sei qual repositório usar com o RAG?**

R: Pergunte ao seu administrador qual repositório contém os documentos relevantes para o seu trabalho. Normalmente, as organizações organizam documentos por tema, departamento ou projeto em diferentes repositórios.

---

**P: A IA consegue ver o histórico das minhas conversas anteriores?**

R: Não. Cada vez que você faz login e inicia uma nova conversa, o histórico do chat começa do zero. As conversas não são salvas permanentemente.

---

**P: O upload do meu arquivo falhou. O que pode estar errado?**

R: Os motivos mais comuns incluem:
- O tamanho total dos arquivos excede o limite de 12 MB. Tente enviar menos arquivos de uma vez.
- O formato do arquivo não é suportado. Verifique a lista de formatos aceitos na [Seção 5.2](#52-adicionando-documentos--upload-de-arquivo).
- O arquivo está corrompido ou protegido por senha. Tente abrir o arquivo no seu computador primeiro para confirmar que está intacto.

---

**P: A importação do Confluence ou do Monday.com não está funcionando. O que devo fazer?**

R: Essas importações exigem credenciais (tokens de API, URLs base) que devem ser pré-configuradas pelo administrador. Entre em contato com a equipe de TI para confirmar que a integração está configurada corretamente.

---

**P: Qual é a diferença entre os repositórios "Simple", "pgvector" e "Redis"?**

R: Do ponto de vista do usuário, todos funcionam da mesma forma — você adiciona documentos a eles e a IA pode pesquisá-los. A diferença é técnica: eles usam tecnologias subjacentes diferentes, com características distintas de desempenho e capacidade. Seu administrador decidirá qual é o mais adequado para cada caso de uso.

---

**P: Não consigo ver o link de Gerenciamento de RAG na barra de navegação.**

R: O acesso ao Gerenciamento de RAG requer uma permissão específica atribuída pelo administrador. Se você precisar de acesso, entre em contato com a equipe de TI ou de suporte.

---

## 9. Glossário

| Termo | Definição |
|---|---|
| **IA (Inteligência Artificial)** | Tecnologia que permite aos computadores realizar tarefas que normalmente exigem inteligência humana, como compreender linguagem e responder perguntas. |
| **Token de API** | Uma credencial digital (como uma senha) usada por sistemas externos para se autenticar e se comunicar com esta aplicação. |
| **Branch** | No GitHub, uma versão de um repositório. O nome de branch mais comum é `main`. |
| **Fragmento (Chunk)** | Uma parte pequena de um documento. Quando os arquivos são ingeridos, são automaticamente divididos em fragmentos para tornar a pesquisa mais eficiente. |
| **Confluence** | Plataforma de colaboração e documentação em equipe da Atlassian, comumente usada para wikis internas e bases de conhecimento. |
| **Ingestão** | O processo de carregar um documento no sistema para que a IA possa pesquisá-lo. |
| **JWT (JSON Web Token)** | Um formato padrão de token de API. Contém informações codificadas sobre a identidade e as permissões do usuário. |
| **Markdown (.md)** | Uma linguagem simples de formatação de texto comumente usada para arquivos de documentação (especialmente em repositórios GitHub). |
| **MCP (Model Context Protocol)** | Um protocolo padrão que permite que ferramentas externas se conectem e interajam com o assistente de IA. |
| **Monday.com** | Plataforma de gestão de trabalho comumente usada para acompanhar projetos e tarefas. |
| **Modelo** | O sistema de IA subjacente que processa suas perguntas e gera respostas. Modelos diferentes têm capacidades diferentes. |
| **RAG (Geração com Recuperação Aumentada)** | Técnica que melhora as respostas da IA pesquisando primeiro em documentos relevantes e usando o conteúdo encontrado para embasar a resposta. |
| **S3 (Simple Storage Service)** | Serviço de armazenamento em nuvem da Amazon Web Services (AWS) onde organizações armazenam arquivos. |
| **SSE (Server-Sent Events)** | Tecnologia que permite que a resposta da IA apareça palavra por palavra em tempo real, em vez de tudo de uma vez. |
| **Temperatura** | Configuração que controla o quanto as respostas da IA são criativas ou previsíveis. Menor = mais preciso; maior = mais criativo. |
| **Repositório de Documentos (Vector Store)** | Banco de dados especializado que armazena documentos de forma otimizada para pesquisa por significado (em vez de correspondência por palavras-chave). |

---

*Este manual foi escrito para o Spring AI Playground v1.1.3. Para suporte técnico, entre em contato com o administrador de TI.*
