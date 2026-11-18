# Registro de prompts (IA como copiloto)

O enunciado da GS permite usar IA "para aprender e revisar", desde que os prompts sejam registrados e que o grupo **saiba explicar cada linha**. Este arquivo registra como a IA foi usada na parte de **Java** (Diego Zandonadi).

- **Ferramenta:** Claude Code (assistente de programação), usado pelo Diego durante a conversa de 16 a 17/11/2026.
- **Como usamos:** a IA escreveu e refatorou código a partir dos pedidos abaixo; o Diego definiu o escopo, conferiu o resultado na tela e nos testes, e decidiu o que entrava.
- **O que foi verificado por nós:** a aplicação rodando no navegador, os 29 testes automáticos (`./mvnw test`) e o repositório clonado do GitHub em uma pasta limpa.
- **Os outros integrantes** devem acrescentar aqui os prompts que usaram nas suas partes (Data Science, banco, bot, Unreal etc.).

## Prompts (resumo fiel, na ordem em que foram feitos)

| # | Prompt (palavras do integrante) | O que resultou | Como conferimos |
|---|---|---|---|
| 1 | "Preciso fazer um aplicativo de filas, minha parte é a do Diego, fazer o back e o front e depois disso realizar a implementação do bot que outra pessoa fez, deixe bonito e estilizado, pensando em algo diferente. O nome do app é Fila certa" | Back-end Spring Boot (domínio, API, simulador), front-end web e a ideia central "hora de sair" (`max(deslocamento, espera)`) | Rodamos o app e testamos entrar na fila, ticket, painel e métricas |
| 2 | "Mas n é para estar dentro da totvs, é um a parte, global solution" | Projeto movido para pasta própria, separado do Challenge TOTVS | Conferido no explorador de arquivos |
| 3 | "não tem como colocar em alguma plataforma pra deixar o app aberto e entrar pelo celular?" | Dockerfile, manifesto PWA e instruções de publicação (Render) | Dockerfile **não testado** em ambiente real |
| 4 | "faça também o github desse projeto" | Repositório git local e, depois, `DiegoZandonadi/fila-certa` no GitHub | Clone limpo do GitHub, compilação e testes |
| 5 | "esse é o machine learning do grupo que outras pessoas fizeram" (notebook) | Leitura do notebook; modelos exportados em JSON e reproduzidos em Java; teste de paridade com o scikit-learn | Teste automático com tolerância de 1e-6 |
| 6 | "esse é o arquivo da gs, veja a parte de JS [Java]" (PDF) | Conferência dos requisitos da disciplina (domínio, arquitetura, integração, documentação) e da Ficha da CitizenTech | Comparação item a item com o enunciado |
| 7 | "deixe com menos cara de IA, tire os emotes e coloque pluggins de verdade, deixe algo profissional e estiloso" | Interface refeita: Leaflet (mapa), Lucide (ícones), Chart.js (gráficos), tema claro/escuro | Revisão visual no navegador e no tamanho de celular |
| 8 | "precisa ter uma parte de pesquisa para pesquisar um estabelecimento específico" | Busca por nome, bairro ou endereço (ignora acentos) | Testada com nomes e termos inexistentes |
| 9 | "pq não consigo colocar minha localização? e se uma pessoa quiser saber como está a fila em algum lugar longe?" | Mensagens claras para falha do GPS, campo de endereço (Nominatim) e atalhos de região | Testado trocando a região de origem |
| 10 | "não teria como utilizar os dados reais de estabelecimentos reais?" | Importador do OpenStreetMap e 36 unidades reais de Barueri | Dados conferidos na tela e no JSON; testes do importador |
| 11 | "primeiro resolva o problema do botão garantir minha vez, não faz sentido algo que vc fez n saber resolver" | Teste de carga, reenvio seguro com chave (idempotência), limites de tempo e teste de concorrência. A causa raiz **não foi comprovada** (indícios de pausa do computador) | Teste de carga (40 mil tickets) e teste automático de concorrência |
| 12 | "o meu amigo do assist bot me deu permissão no git dele com o bot para implementar, o que faço agora?" | Integração com o bot do grupo (`POST /chat`) e roteamento: fila no app, emergência/funções no bot | Teste com o bot real rodando |
| 13 | "Pode tratar os dados reais, transformar eles como precisa em data science, sobre o bairro de barueri" | Histórico real do painel (Wayback Machine), coletor contínuo, tratamento, análise, notebook executado e modelo de espera típica | Notebook executado; resultados e limitações documentados |
| 14 | "o readme está certo? o projeto está correto?" | Auditoria: clone limpo, testes, persistência em arquivo; correção de afirmações imprecisas do README; Maven Wrapper | Reexecução completa a partir do GitHub |
| 15 | "o que seria enriquecer?" / "deixe tudo certo" | Domínio rico: `Ticket` e `Estabelecimento` protegem as próprias regras; 14 testes novos | Testes de domínio e conferência da API |

## Limites do que a IA fez (para a apresentação)
- A **fila, os clientes e os tickets são simulados**; só os estabelecimentos (OpenStreetMap) e as esperas da aba Previsão (painel da Prefeitura de Barueri) são reais.
- A **previsão de 15 minutos** usa um modelo treinado com dados simulados até a coleta contínua juntar dados reais.
- Itens **não testados**: publicação no Render/Docker e o GPS com permissão real.
- A causa raiz do travamento ocasional do botão **não foi identificada**; o app foi blindado para não duplicar tickets nem ficar preso.
