# Registro de uso de IA (copiloto)

O enunciado da GS permite usar IA "para aprender e revisar" e pede o registro dos prompts. Este arquivo registra como a IA **auxiliou** a parte de **Java** (Diego Zandonadi).

## Como a IA foi usada
- **Ferramenta:** Claude Code, como assistente de programação, em 16 e 17/11/2026.
- **Papel da IA:** auxiliou na escrita de código, nos testes e na documentação, a partir de pedidos feitos pelo Diego.
- **Papel do Diego:** definiu o problema e o escopo, escolheu o que entrava ou não, conferiu cada entrega rodando o app, pediu correções, integrou o trabalho dos colegas (notebook de Data Science e bot de suporte) e criou e administra o repositório.
- **Verificação:** o app foi executado no navegador, os 29 testes automáticos passam (`./mvnw test`) e o repositório foi clonado do GitHub em uma pasta limpa para conferir.
- **Responsabilidade:** o Diego se responsabiliza pelo projeto e deve saber explicar o código. Os demais integrantes registram aqui o que usaram nas suas partes, se usaram.

## Principais pedidos feitos à IA (resumo)
| # | Pedido do Diego (resumo) | Resultado | Conferência |
|---|---|---|---|
| 1 | Criar o app de filas "Fila Certa" (back-end e front-end), com visual diferente, para depois integrar o bot do grupo | Back-end Spring Boot, API, simulador e a ideia da "hora de sair" | Uso do app no navegador |
| 2 | Interface profissional, sem emojis, com plugins de verdade | Mapa (Leaflet), ícones (Lucide), gráficos (Chart.js), tema claro/escuro | Revisão visual, inclusive em tamanho de celular |
| 3 | Busca de estabelecimento, localização por endereço e consulta de filas em outras regiões | Busca, campo de endereço e atalhos de região | Testes de uso no navegador |
| 4 | Usar estabelecimentos e dados reais | Importador do OpenStreetMap (36 unidades de Barueri) e tratamento do histórico real do painel da Prefeitura | Dados conferidos na tela e testes do importador |
| 5 | Integrar o notebook de Data Science do grupo | Modelos exportados em JSON e reproduzidos em Java, com teste de paridade | Teste automático (tolerância 1e-6) |
| 6 | Integrar o bot de suporte do colega | Ponte HTTP e roteamento das respostas (fila no app; emergência e funções no bot) | Teste com o bot real rodando |
| 7 | Resolver o travamento do botão "Garantir minha vez" | Teste de carga, reenvio seguro e teste de concorrência | Execução dos testes |
| 8 | Revisar README e projeto; enriquecer o domínio | Correções do README, Maven Wrapper e regras movidas para `Ticket` e `Estabelecimento` | Clone limpo e 29 testes |

## Limites (para a apresentação)
- A **fila, os clientes e os tickets são simulados**; só os estabelecimentos (OpenStreetMap) e as esperas da aba Previsão (Prefeitura de Barueri) são reais.
- A **previsão de 15 minutos** usa um modelo treinado com dados simulados até a coleta contínua juntar dados reais.
- **Não testados:** publicação no Render/Docker e o GPS com permissão real.
- A causa do travamento ocasional do botão não foi identificada; o app foi protegido para não duplicar tickets nem ficar preso.
