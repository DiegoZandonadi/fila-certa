# Fila Certa 🎯

> **Chegue na hora. Nunca na fila.**

Global Solution 2026.2 · CitizenTech · Engenharia de Software (2º ano) · disciplina **Domain Driven Design – Java**

## Ficha da CitizenTech

> ✏️ Rascunho: o grupo deve revisar os campos marcados com **(preencher)** antes da entrega.

| Campo | Resposta |
|---|---|
| **Nome da CitizenTech** | Fila Certa |
| **Cidade e bairro** | Barueri/SP — Prontos-Atendimentos (Engenho Novo, Imperial, Camargo, Mutinga, PAA e PAI) **(confirmar bairro)** |
| **Cidadão beneficiado** | Quem precisa de atendimento de urgência na rede pública e não sabe quanto vai esperar em cada unidade **(descrever a pessoa entrevistada)** |
| **Problema, em uma frase** | Pessoas gastam horas em filas de pronto-atendimento sem saber qual unidade está mais vazia nem quando sair de casa, enquanto outras unidades ficam lotadas. |
| **Como funciona** | O cidadão vê a espera prevista em cada unidade, entra na fila de onde estiver e o app cruza **tempo de espera × localização** para avisar a hora exata de sair. |
| **O que cada parte faz** | **Java (esta aplicação):** domínio da fila, API REST, painel do atendente, métricas e assistente. **Data Science:** regressão linear (espera em 15 min) e logística (chance de espera longa), integradas em `/api/ml`. **Database:** modelo relacional da fila. **Agile:** backlog e retrospectiva. **AR-VR / Dynamic Programming / Network:** demais partes da mesma solução **(preencher)**. |
| **Link do repositório** | **(preencher)** |
| **Indicador de impacto** | **Minutos de fila economizados por atendimento** = `min(deslocamento, espera)` (já calculado e exibido pelo app) e **% de atendimentos em que a espera real ficou abaixo do previsto**. |
| **ODS conectados** | **ODS 3** (Saúde e bem-estar: acesso mais rápido ao atendimento), **ODS 10** (Redução das desigualdades: informação a quem não pode "esperar para ver"), **ODS 11** (Cidades sustentáveis: serviços públicos melhor distribuídos) e **ODS 9** (Inovação e infraestrutura digital). |

**Integrantes:** nome completo — RM **(preencher)**

## A ideia

Sem a Fila Certa, o tempo até ser atendido é `deslocamento + espera`. Com ela é `max(deslocamento, espera)`: a pessoa entra na fila de longe e sai de casa só quando a vez dela está chegando. O app mostra essa economia em cada local e, no ticket, a contagem "saia em X min".

## Rodando

Requisitos: **Java 17+**.

```bash
mvn spring-boot:run
```

ou, com o jar já gerado (`mvn package`):

```bash
java -jar target/fila-certa-1.0.0.jar
```

Abra <http://localhost:8080>. Sem nada para instalar: usa H2 em memória.

**Dados que sobrevivem a reinícios** (persistência em arquivo):

```bash
java -jar target/fila-certa-1.0.0.jar --spring.profiles.active=arquivo
```

Testes: `mvn test` (inclui a verificação de que o Java reproduz o scikit-learn).

## Telas

| Tela | O que faz |
|---|---|
| 📡 **Descobrir** | Radar com os locais ao redor (cor = tempo de espera). Arraste o radar para simular outra localização. Cada card compara "ir e esperar" × "Fila Certa". |
| 🎟️ **Meu ticket** | Senha com contagem "saia em X min", plano da jornada (livre → a caminho → vez), mini mapa e simulação de trajeto. |
| 🧑‍💼 **Painel** | Visão do atendente: guichês, chamar próximo, finalizar, ausente. Prioritários passam na frente. |
| 🧠 **Previsão IA** | Dados reais de Barueri: espera atual → prevista em 15 min, chance de espera longa, "onde ir agora" e alertas de sobrecarga. |
| 📊 **Métricas** | Espera por horário, por local e distância média dos clientes. |
| 💬 **Assistente** | Chat de suporte (bot externo ou assistente local). |

O botão **Demo ao vivo ×10** liga/desliga o piloto automático da fila (clientes e guichês simulados, tempo 10× mais rápido).

> Os estabelecimentos do radar são **fictícios** (apenas para a demonstração da fila). Os dados da aba **Previsão IA** são **reais e públicos** (Prefeitura de Barueri).

## Arquitetura (Spring Boot 3.3 · Java 17 · JPA)

```
br.com.filacerta
├─ domain/       Estabelecimento, Ticket + enums (Categoria, StatusTicket, ModoDeslocamento)
├─ repository/   Spring Data JPA
├─ service/      FilaService (regras), MetricasService, DistanciaService, SimuladorFila
│  └─ previsao/  PrevisorTempoEspera  ← ponto de extensão (fila local)
├─ ml/           Coleta de Barueri, ModeloEspera (regressões do notebook), PrevisaoService, /api/ml
├─ bot/          BotService → BotExterno (HTTP) | BotLocal (regras, fallback)
├─ web/          Controllers REST + DTOs + tratamento de erros
└─ config/       DataSeeder, RelogioApp
```

O **modelo de domínio** (diagramas e responsabilidades) está em [docs/modelo-de-dominio.md](docs/modelo-de-dominio.md).

## API

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/estabelecimentos?lat&lng&modo&categoria` | Lista ordenada por (deslocamento + espera) |
| POST | `/api/estabelecimentos/{id}/fila` | Entra na fila → ticket (`201`) |
| GET | `/api/estabelecimentos/{id}/painel` | Guichês + fila (atendente) |
| POST | `/api/estabelecimentos/{id}/chamar-proximo` | Chama o próximo |
| GET | `/api/tickets/{id}` | Ticket com posição, espera, trajeto e "saia em" |
| PUT | `/api/tickets/{id}/localizacao` | Atualiza onde o cliente está / modo |
| POST | `/api/tickets/{id}/cancelar` · `/finalizar` · `/ausente` | Mudança de status |
| GET | `/api/metricas?estabelecimentoId=` | KPIs, espera por hora, por local, distância média |
| GET | `/api/ml/previsoes` | Previsão por local/setor, recomendações e alertas |
| POST | `/api/ml/coletar` | Força uma leitura do painel de Barueri |
| GET | `/api/ml/dados.csv` | Série coletada (formato do notebook, para treinar) |
| POST | `/api/bot/mensagem` | Conversa com o assistente |
| GET/POST | `/api/simulacao` | Liga/desliga o piloto automático |

`modo` = `A_PE` · `TRANSPORTE` · `CARRO`. Erros: `404`, `422` (regra de negócio), `400` (validação), sempre com `{ "mensagem": "..." }`.

## Machine Learning (integração com o notebook do grupo)

1. O app lê o painel público da Prefeitura de Barueri a cada 5 min e guarda as leituras (`TB_FC_LEITURA_ESPERA`).
2. Os dois modelos do notebook são **lineares**, então o Java só precisa de intercepto, coeficientes e padronizador (`src/main/resources/ml/modelo.json`) e reproduz o scikit-learn (teste `ModeloEsperaParidadeTest`, tolerância 1e-6).
3. **Treinar com dados reais** (o `modelo.json` que vem no repositório foi treinado com dados **simulados**, só para demonstração):
   1. Deixe o app rodando por horas/dias e baixe `GET /api/ml/dados.csv` → salve como `espera_barueri.csv`.
   2. Abra `ml/GS_FilaDigital_DEMAS_corrigido.ipynb` (notebook do grupo + correção do parser + célula 7.1), troque `MODO = "real"` e rode até a célula **7.1**, que gera `modelo.json`.
   3. Copie para `src/main/resources/ml/modelo.json` (ou use `--filacerta.ml.modelo-path=C:\caminho\modelo.json`).
4. `ml/executar_e_exportar.py` roda o mesmo pipeline fora do Jupyter (`python executar_e_exportar.py real`).

> **Correção feita no notebook:** o texto `"1 h e 36 min"` era lido como **1 minuto** (só o primeiro número). O painel real tem esperas assim, então a versão em `ml/` converte corretamente para 96 min. O original em Downloads não foi alterado.

## Integração do bot de suporte

Configure `filacerta.bot.url` em `application.properties` (ou `--filacerta.bot.url=...`). O backend faz `POST` JSON:

```json
{
  "mensagem": "quando devo sair?",
  "sessaoId": "…",
  "ticketId": "…",
  "contexto": { "ticket": { "...": "ticket ao vivo" }, "estabelecimentos": [ { "...": "locais com espera e distância" } ] }
}
```

e lê a resposta em `resposta`, `reply`, `message`, `answer`, `response`, `text` ou `output`. Se o bot cair, o assistente local assume. Se o contrato for outro, só [`BotExterno`](src/main/java/br/com/filacerta/bot/BotExterno.java) muda.

## Publicar online (celular)

Há um `Dockerfile` pronto: no **Render**, crie um *Web Service* a partir do repositório com runtime **Docker**. O link HTTPS permite o GPS real e "Adicionar à tela inicial" no celular (o app tem manifesto PWA).

## Trocar o banco

Troque a dependência `h2` pelo driver (Oracle/PostgreSQL) e ajuste `spring.datasource.*` e `spring.jpa.hibernate.ddl-auto`. O `DataSeeder` só popula banco vazio.

## Dados e privacidade

Apenas dados **públicos e agregados** (tempo médio de espera por setor, Prefeitura de Barueri). O app não guarda dados pessoais reais: o nome informado na fila é livre e a localização do cliente é aproximada e usada só para calcular o trajeto.
