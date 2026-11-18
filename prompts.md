# Registro de uso de IA (copiloto)

O enunciado da GS permite usar IA "para aprender e revisar" e pede o registro dos prompts. Este arquivo registra como a IA **auxiliou** a parte de **Java** (Diego Zandonadi).

## Como a IA foi usada
- **Ferramenta:** Claude Code, como assistente de programação, em 16 e 17/11/2026.
- **Papel da IA:** auxiliou na escrita de código, nos testes e na documentação, a partir de pedidos feitos pelo Diego.
- **Papel do Diego:** definiu o problema e o escopo, escolheu o que entrava ou não, conferiu cada entrega rodando o app, pediu correções, integrou o trabalho dos colegas (notebook de Data Science e bot de suporte) e criou e administra o repositório.
- **Verificação:** o app foi executado no navegador, os 29 testes automáticos passam (`./mvnw test`) e o repositório foi clonado do GitHub em uma pasta limpa para conferir.
- **Responsabilidade:** o Diego se responsabiliza pelo projeto e deve saber explicar o código. Os demais integrantes registram aqui o que usaram nas suas partes, se usaram.

## Limites (para a apresentação)
- A **fila, os clientes e os tickets são simulados**; só os estabelecimentos (OpenStreetMap) e as esperas da aba Previsão (Prefeitura de Barueri) são reais.
- A **previsão de 15 minutos** usa um modelo treinado com dados simulados até a coleta contínua juntar dados reais.
- **Não testados:** publicação no Render/Docker e o GPS com permissão real.
- A causa do travamento ocasional do botão não foi identificada; o app foi protegido para não duplicar tickets nem ficar preso.
