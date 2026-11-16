# Fila Certa 🎯

> **Chegue na hora. Nunca na fila.**

Fila virtual inteligente. O cliente entra na fila de onde estiver e o app cruza duas métricas —
**tempo de espera** e **localização** — para dizer a **hora exata de sair de casa**: ele chega
quando a vez dele chega.

A ideia central: sem Fila Certa o tempo até ser atendido é `deslocamento + espera`; com ela é
`max(deslocamento, espera)`. O app mostra essa economia em cada local.

## Rodando

Requisitos: **Java 17+** (o Maven vem pelo IntelliJ, ou use um Maven local).

```bash
mvn spring-boot:run
```

Abra <http://localhost:8080>. Sem banco para instalar: usa H2 em memória e gera sozinho uma base
"pré-existente" (8 locais de São Paulo + ~17 mil atendimentos de histórico).

## Telas

| Tela | O que faz |
|---|---|
| 📡 **Descobrir** | Radar com os locais ao seu redor (cor = tempo de espera). Arraste o radar para simular outra localização. Cada card compara "ir e esperar" × "Fila Certa". |
| 🎟️ **Meu ticket** | Senha com anel de contagem "saia em X min", plano da jornada (livre → a caminho → vez), mini mapa e simulação de trajeto. Avisa (toast + som) em *prepare-se*, *saia agora* e *sua vez*. |
| 🧑‍💼 **Painel** | Visão do atendente: guichês, chamar próximo, finalizar, ausente. Prioritários passam na frente. |
| 📊 **Métricas** | Espera média por horário, por local e distância média dos clientes (localização). |
| 💬 **Assistente** | Chat de suporte (veja "Integração do bot"). |

O botão **Demo ao vivo ×10** (topo) liga/desliga o piloto automático: clientes chegam e guichês
atendem sozinhos, com o tempo 10× mais rápido (`filacerta.relogio.fator`).

## Arquitetura (Spring Boot 3.3 / Java 17)

```
br.com.filacerta
├─ domain/       Estabelecimento, Ticket, enums (Categoria, StatusTicket, ModoDeslocamento)
├─ repository/   Spring Data JPA
├─ service/      FilaService (regras), MetricasService, DistanciaService, SimuladorFila
│  └─ previsao/  PrevisorTempoEspera  ← ponto de extensão do Machine Learning
├─ bot/          BotService → BotExterno (HTTP) | BotLocal (regras, fallback)
├─ web/          Controllers REST + DTOs + tratamento de erros
└─ config/       DataSeeder (base pré-existente), RelogioApp
```

Front-end estático (sem build) em `src/main/resources/static`: HTML + CSS + JS puro.

## API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/estabelecimentos?lat&lng&modo&categoria` | Lista ordenada por (deslocamento + espera) |
| GET | `/api/estabelecimentos/{id}` | Detalhe |
| POST | `/api/estabelecimentos/{id}/fila` | Entra na fila → ticket (`201`) |
| GET | `/api/estabelecimentos/{id}/painel` | Guichês + fila (atendente) |
| POST | `/api/estabelecimentos/{id}/chamar-proximo` | Chama o próximo |
| GET | `/api/tickets/{id}` | Ticket com posição, espera, trajeto e "saia em" |
| PUT | `/api/tickets/{id}/localizacao` | Atualiza onde o cliente está / modo |
| POST | `/api/tickets/{id}/cancelar` · `/finalizar` · `/ausente` | Mudança de status |
| GET | `/api/metricas?estabelecimentoId=` | KPIs, espera por hora, por local, distância média |
| POST | `/api/bot/mensagem` | Conversa com o assistente |
| GET/POST | `/api/simulacao` | Liga/desliga o piloto automático |

`modo` = `A_PE` · `TRANSPORTE` · `CARRO`. Erros: `404` não encontrado, `422` regra de negócio,
`400` validação — sempre com `{ "mensagem": "..." }`.

## Integração do bot de suporte

Configure em `application.properties` (ou `--filacerta.bot.url=...` na linha de comando):

```properties
filacerta.bot.url=http://localhost:5005/chat
filacerta.bot.api-key=          # opcional (enviado como Bearer)
filacerta.bot.timeout-ms=4000
```

O backend faz `POST` JSON para essa URL:

```json
{
  "mensagem": "quando devo sair?",
  "sessaoId": "…",
  "ticketId": "…",
  "contexto": {
    "ticket": { "...": "ticket ao vivo (posição, espera, saiEmMin…)" },
    "estabelecimentos": [ { "...": "locais com espera e distância" } ]
  }
}
```

e lê a resposta em qualquer um destes campos: `resposta`, `reply`, `message`, `answer`,
`response`, `text`, `output`. Se o bot estiver fora do ar, o app cai sozinho no assistente local —
a demo nunca fica sem suporte. Se o contrato do bot for outro, só o
[`BotExterno`](src/main/java/br/com/filacerta/bot/BotExterno.java) precisa mudar.

## Integração do modelo de Machine Learning

A espera hoje vem de uma média móvel exponencial dos últimos atendimentos
([`PrevisorEstatistico`](src/main/java/br/com/filacerta/service/previsao/PrevisorEstatistico.java)).
Para plugar o modelo do grupo: implemente `PrevisorTempoEspera`, anote com `@Primary` e pronto —
tela, bot e métricas passam a usar a nova previsão.

## Trocar o banco

Troque a dependência `h2` pelo driver (Oracle/PostgreSQL) no `pom.xml` e ajuste
`spring.datasource.*` e `spring.jpa.hibernate.ddl-auto` no `application.properties`.
O `DataSeeder` pode ser removido quando houver base real.
