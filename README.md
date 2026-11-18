# Fila Certa

> **Chegue na hora. Nunca na fila.**

Global Solution 2026.2 · CitizenTech · Engenharia de Software (2º ano) · disciplina **Domain Driven Design – Java**

## Ficha da CitizenTech

> Rascunho: o grupo deve revisar os campos marcados com **(preencher)** antes da entrega.

| Campo | Resposta |
|---|---|
| **Nome da CitizenTech** | Fila Certa |
| **Cidade e bairro** | Barueri/SP — Prontos-Atendimentos (Engenho Novo, Imperial, Camargo, Mutinga, PAA e PAI) **(confirmar bairro)** |
| **Cidadão beneficiado** | Quem precisa de atendimento de urgência na rede pública e não sabe quanto vai esperar em cada unidade **(descrever a pessoa entrevistada)** |
| **Problema, em uma frase** | Pessoas gastam horas em filas de pronto-atendimento sem saber qual unidade está mais vazia nem quando sair de casa, enquanto outras unidades ficam lotadas. |
| **Como funciona** | O cidadão vê a espera prevista em cada unidade, entra na fila de onde estiver e o app cruza **tempo de espera × localização** para avisar a hora exata de sair. |
| **O que cada parte faz** | **Java (esta aplicação):** domínio da fila, API REST, painel do atendente, métricas e assistente. **Data Science:** regressão linear (espera em 15 min) e logística (chance de espera longa), integradas em `/api/ml`. **Database:** modelo relacional da fila. **Agile:** backlog e retrospectiva. **AR-VR / Dynamic Programming / Network:** demais partes da mesma solução **(preencher)**. |
| **Link do repositório** | https://github.com/DiegoZandonadi/fila-certa |
| **Indicador de impacto** | **Minutos de fila economizados por atendimento** = `min(deslocamento, espera)` (já calculado e exibido pelo app) e **% de atendimentos em que a espera real ficou abaixo do previsto**. |
| **ODS conectados** | **ODS 3** (Saúde e bem-estar: acesso mais rápido ao atendimento), **ODS 10** (Redução das desigualdades: informação a quem não pode "esperar para ver"), **ODS 11** (Cidades sustentáveis: serviços públicos melhor distribuídos) e **ODS 9** (Inovação e infraestrutura digital). |

**Integrantes**

| Nome | RM |
|---|---|
| Davi Alves dos Santos (representante) | 566279 |
| Diego Zandonadi | 561488 |
| Henrique de Lima Martins | 561876 |
| Luis Miguel Gonçalves Pinto | 561232 |
| Rafael Joda da Costa e Silva | 561939 |

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
| **Descobrir** | Mapa (OpenStreetMap) com todas as unidades, coloridas pelo tempo de espera, e busca por nome, bairro ou endereço. A origem pode ser o GPS, um endereço (busca no OpenStreetMap/Nominatim), um atalho de região (Centro, Oeste, Leste, Norte e Sul, calculados a partir das unidades da cidade) ou o ponto azul arrastado. É possível consultar a fila de qualquer unidade, mesmo longe de onde você está. Cada card compara "ir e esperar" com "Fila Certa". |
| **Meu ticket** | Senha com contagem "saia em X min", plano da jornada (livre, a caminho, vez), mapa do trajeto e simulação de deslocamento. |
| **Painel** | Visão do atendente: guichês, chamar próximo, finalizar, ausente. Prioritários passam na frente. |
| **Previsão** | Dados reais de Barueri: espera atual e prevista em 15 min, chance de espera longa, "onde ir agora" e alertas de sobrecarga. |
| **Métricas** | Espera por horário, por unidade e distância média dos clientes (gráficos Chart.js). |
| **Suporte** | Chat de suporte (bot externo ou assistente local). |

A chave **Simulação** (menu lateral) liga/desliga o piloto automático da fila (clientes e guichês simulados, tempo 10x mais rápido).

> **Dados reais e simulados:** os estabelecimentos (nome, endereço e coordenadas) são **reais**, importados do OpenStreetMap (Barueri/SP). As **filas e clientes** são **simulados** (não existe fonte pública de fila por unidade); guichês e tempo de atendimento são **parâmetros estimados** por tipo de lugar. Só a aba **Previsão** usa esperas **reais** (painel da Prefeitura de Barueri).

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
| GET/POST | `/api/importacao/osm?cidade=` | Pré-visualiza / importa estabelecimentos reais de uma cidade |
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

## Estabelecimentos reais (OpenStreetMap)

A base inicial vem de `src/main/resources/data/estabelecimentos.json`: 36 unidades reais de **Barueri/SP** (hospitais, prontos-socorros, UBSs, bancos e órgãos públicos), com nome, endereço e coordenadas do OpenStreetMap (licença ODbL, © colaboradores do OpenStreetMap).

Para usar **outra cidade**, o app tem um importador (consulta a API Overpass):

```bash
# ver o que seria importado (não grava)
curl "http://localhost:8080/api/importacao/osm?cidade=Osasco"

# importar e gravar no banco
curl -X POST "http://localhost:8080/api/importacao/osm?cidade=Osasco"
```

Para trocar a base inicial, salve o resultado do primeiro comando em `src/main/resources/data/estabelecimentos.json`. O importador limita a quantidade por categoria (20 de saúde, 10 bancos, 6 serviços públicos, 4 cartórios) e ignora consultórios particulares. O OpenStreetMap não informa guichês nem tempo de atendimento, por isso esses valores são **estimados** por tipo de lugar (ver `ImportadorService`).

## Data Science e Machine Learning

Tudo sobre **Barueri/SP** (unidades com nome de bairro: Engenho Novo, Imperial, Camargo, Mutinga; PAA e PAI), com dados **públicos e agregados** (nenhum dado pessoal) do painel de tempo de espera da Prefeitura.

| Arquivo (pasta `ml/`) | O que é |
|---|---|
| `baixar_historico_wayback.py` | Baixa as capturas do painel guardadas no Internet Archive (2017–2026) → `dados/historico_wayback.csv` (1.532 leituras de 60 capturas) |
| `coletar_barueri.py` | Coletor contínuo (a cada 5 min) → `dados/espera_barueri_ao_vivo.csv`. Independe do app: pode rodar por dias |
| `GS_FilaDigital_dados_reais.ipynb` | **Notebook executado**: tratamento, análise, testes estatísticos, modelos e conclusões (com os gráficos em `figuras/`) |
| `gerar_notebook_real.py` | Gera e executa o notebook acima (`python gerar_notebook_real.py`) |
| `GS_FilaDigital_DEMAS_corrigido.ipynb` | Notebook original do grupo + correção do parser + célula de exportação (modelo de 15 min) |

### Tratamento dos dados reais
Descartados e corrigidos (contagens no notebook): conversão de `"1 h e 36 min"` (o original lia 1 min), espera impossível (2.400 min), **painel parado** (leitura até 114 dias velha), duplicatas; nomes de unidade normalizados (`PS CAMARGO` × `PARQUE DOS CAMARGOS`); fuso do Archive (UTC) convertido para a hora local.

### Resultados (dados reais, divisão temporal treino/teste)
- A espera depende **muito do setor** (Clínico geral: mediana de 22 min; Atendimento: 3 min; tamanho do efeito ε² = 0,34) e **pouco da unidade** (ε² = 0,02).
- Regressão linear (espera típica): R² = 0,31 contra 0,11 do baseline, mas o erro médio quase não melhora (10,3 × 10,6 min).
- Regressão logística (espera ≥ 20 min): ROC AUC = 0,84 e recall = 0,84, com precisão de 0,50.
- **Leitura honesta:** o perfil (unidade, setor, horário) explica parte da espera, mas não substitui saber o estado atual da fila. Por isso existe o modelo de 15 min.

### Dois modelos no app
1. **Espera típica** (`ml/modelo-perfil.json`): treinado com o histórico **real**. A aba Previsão mostra "típico neste horário" e marca setores **acima/abaixo do normal**.
2. **Previsão em 15 min** (`ml/modelo.json`): usa a espera atual e a de 15 min atrás. Exige uma **série contínua**, que só o coletor ao vivo produz, e o modelo que vem no repositório foi treinado com dados **simulados** (a tela avisa). Para treinar com dados reais: deixe `coletar_barueri.py` rodando por algumas horas (mínimo de 60 instantes) e execute o notebook de novo; ele treina e exporta quando houver série suficiente.

Os dois modelos são **lineares**: o Java usa só intercepto, coeficientes e padronizador e reproduz o scikit-learn (testes `ModeloEsperaParidadeTest`, tolerância 1e-6).

> **Correção feita no notebook original:** `"1 h e 36 min"` era lido como **1 minuto**. O painel real tem muitas esperas assim; a versão em `ml/` converte para 96 min. O original em Downloads não foi alterado.

## Integração do bot de suporte

O bot do grupo é o repositório [public-services-support-agent](https://github.com/DaviAlves06/public-services-support-agent) (FastAPI, porta 6969). O app conversa com ele por `POST /chat`:

```json
{ "message": "Como acesso minha carteirinha?" }
```
```json
{ "category": "app_feature", "answer": "Abra a aba Carteirinha..." }
```

O app envia também `mensagem`, `sessaoId`, `ticketId` e `contexto` (ticket ao vivo e unidades com espera e distância); o bot atual ignora esses campos extras, e a [próxima integração prevista no README dele](https://github.com/DaviAlves06/public-services-support-agent#próxima-integração-backend-java-e-entrega-6) é justamente usá-los.

**Quem responde cada pergunta** (`BotService`):

| Pergunta | Quem responde |
|---|---|
| Fila, ticket, posição, espera, hora de sair, fila mais rápida | O próprio app (só ele tem esses dados) |
| Emergência, pedido de remédio/tratamento, funções do aplicativo, UBS/UPA/SUS | O bot de suporte |
| Bot fora do ar, ou resposta `not_found` | Assistente local do app, que também encaminha emergências ao SAMU 192 |

**Como rodar o bot** (PowerShell, na pasta do bot):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install fastapi "uvicorn[standard]" pydantic numpy ollama python-dotenv langchain-text-splitters
python -m uvicorn supagent:app --port 6969
```

As respostas sobre UBS, UPA e SUS usam busca semântica local e exigem o [Ollama](https://ollama.com/) com `ollama pull nomic-embed-text` e `python .\rag\embeddings.py` (veja o README do bot). Sem isso, o bot responde emergência, orientação médica e funções do app, e o app usa o plano B nas demais.

Endereço do bot: `filacerta.bot.url` em `application.properties` (padrão `http://127.0.0.1:6969/chat`; em branco desativa). Para chave de acesso: `filacerta.bot.api-key` (enviada como `Bearer`).

## Publicar online (celular)

Há um `Dockerfile` pronto: no **Render**, crie um *Web Service* a partir do repositório com runtime **Docker**. O link HTTPS permite o GPS real e "Adicionar à tela inicial" no celular (o app tem manifesto PWA)

Bibliotecas do front-end (via CDN, versões fixas): Leaflet 1.9.4 e Leaflet.markercluster 1.5.3 (mapas), Lucide 0.469 (ícones) e Chart.js 4.4.1 (gráficos); fonte IBM Plex Sans..

## Trocar o banco

Troque a dependência `h2` pelo driver (Oracle/PostgreSQL) e ajuste `spring.datasource.*` e `spring.jpa.hibernate.ddl-auto`. O `DataSeeder` só popula banco vazio.

## Dados e privacidade

O endereço digitado no campo de localização é enviado pelo navegador ao serviço público Nominatim (OpenStreetMap) para obter as coordenadas; o servidor do app não recebe nem guarda esse texto. O GPS só funciona em HTTPS ou localhost.

Apenas dados **públicos e agregados** (tempo médio de espera por setor, Prefeitura de Barueri). O app não guarda dados pessoais reais: o nome informado na fila é livre e a localização do cliente é aproximada e usada só para calcular o trajeto.
