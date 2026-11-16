# Modelo de domínio — Fila Certa

O domínio gira em torno de **quem espera** (`Ticket`) e **onde espera** (`Estabelecimento`). As regras vivem em serviços (`FilaService`), não nos controllers, e a previsão de espera é uma **interface** que pode ser trocada.

## Diagrama de classes

```mermaid
classDiagram
    direction LR

    class Estabelecimento {
        -Long id
        -String nome
        -Categoria categoria
        -String endereco
        -double latitude
        -double longitude
        -int guiches
        -double tempoMedioBaseMin
    }

    class Ticket {
        -String id
        -String codigo
        -String nomeCliente
        -boolean prioritario
        -StatusTicket status
        -ModoDeslocamento modo
        -LocalDateTime criadoEm
        -LocalDateTime chamadoEm
        -LocalDateTime finalizadoEm
        -Integer guiche
        -Double distanciaKm
        +atualizarLocalizacao(lat, lng, km, modo)
        +esperaRealMin() Double
        +atendimentoRealMin() Double
    }

    class StatusTicket {
        <<enumeration>>
        AGUARDANDO
        CHAMADO
        ATENDIDO
        CANCELADO
        AUSENTE
    }
    class ModoDeslocamento {
        <<enumeration>>
        A_PE
        TRANSPORTE
        CARRO
    }
    class Categoria {
        <<enumeration>>
        SAUDE
        BANCO
        GOVERNO
        CARTORIO
        RESTAURANTE
    }

    class FilaService {
        +entrar(id, req) TicketView
        +chamarProximo(id) TicketView
        +finalizar(ticketId) TicketView
        +cancelar(ticketId) TicketView
        +atualizarLocalizacao(ticketId, req) TicketView
        +listar(lat, lng, modo, categoria) List
    }
    class PrevisorTempoEspera {
        <<interface>>
        +prever(estabelecimento, pessoasNaFrente, guichesLivres) Previsao
    }
    class PrevisorEstatistico {
        +prever(...) Previsao
    }
    class DistanciaService {
        +km(lat1, lng1, lat2, lng2) double
        +minutosDeslocamento(km, modo) int
    }
    class SimuladorFila {
        +tick()
    }

    Estabelecimento "1" --> "*" Ticket : recebe
    Ticket --> StatusTicket
    Ticket --> ModoDeslocamento
    Estabelecimento --> Categoria
    FilaService ..> Estabelecimento
    FilaService ..> Ticket
    FilaService --> PrevisorTempoEspera : usa
    FilaService --> DistanciaService : usa
    PrevisorEstatistico ..|> PrevisorTempoEspera
    SimuladorFila --> FilaService : simula clientes e guichês
```

## Módulo de previsão (Machine Learning)

```mermaid
classDiagram
    direction LR
    class ColetorBarueri {
        +coletar() int
    }
    class BarueriClient {
        +buscar() List~Leitura~
    }
    class LeituraEspera {
        -String local
        -int minutos
        -LocalDateTime ts
    }
    class ModeloEspera {
        +prever(numericas, unidade, setor) Previsao
    }
    class ClassificadorLocal {
        +unidade(local) String
        +setor(local) String
        +sintomas(local) int
    }
    class PrevisaoService {
        +visao() IaView
        +csv() String
    }
    ColetorBarueri --> BarueriClient : lê o painel público
    ColetorBarueri --> LeituraEspera : grava
    PrevisaoService --> LeituraEspera : lê a série
    PrevisaoService --> ModeloEspera : aplica regressões
    PrevisaoService --> ClassificadorLocal
```

## Responsabilidades

| Camada | Classes | Papel |
|---|---|---|
| Domínio | `Estabelecimento`, `Ticket`, enums | Estado e invariantes (atributos privados, construtores, comportamento em `Ticket`). |
| Serviço | `FilaService`, `MetricasService`, `DistanciaService`, `PrevisaoService` | Regras de negócio e cálculos (hora de sair, economia, previsão). |
| Persistência | `*Repository` (Spring Data JPA) | Acesso a dados (H2; trocável por Oracle/PostgreSQL). |
| Interface | Controllers REST + front estático | API demonstrável e telas. |
| Integração | `BotExterno`, `BotLocal`, `ColetorBarueri`, `ModeloEspera` | Bot de suporte, painel de Barueri e modelo de ML. |

## Decisões de modelagem

- **Interface `PrevisorTempoEspera`:** a fila local usa média móvel; um modelo de ML pode substituí-la com `@Primary`, sem mexer em `FilaService`.
- **Interface `BotAtendimento`:** duas implementações (`BotExterno`, `BotLocal`); `BotService` escolhe e faz fallback.
- **`Ticket` guarda a localização do cliente:** é a métrica de localização que alimenta o "hora de sair" e as métricas de distância.
- **`ModeloEspera` é lido de JSON:** os modelos são lineares, então não é preciso embutir Python; a equivalência com o scikit-learn é testada.
