# Agente de suporte do Fila Certa

Serviço FastAPI integrado ao backend Java. O código evolui o projeto `public-services-support-agent` para consumir o contexto real enviado pelo Fila Certa, mantendo respostas fundamentadas e barreiras de segurança.

## Fluxo

`classificação → segurança → app/RAG/recomendação → suporte humano`

Categorias: `emergency`, `medical_request`, `app_feature`, `public_service`, `system_recommendation` e `not_found`.

O agente não diagnostica, não recomenda tratamento, não faz triagem e não escolhe ou prioriza unidades. Em uma recomendação, ele apenas explica os campos recebidos do backend: distância, espera estimada, serviço, status e a marcação de unidade recomendada.

## Instalação e execução

```powershell
cd support-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -e ".[dev]"
python -m uvicorn support_agent.main:app --reload --port 6969
```

API: `POST http://127.0.0.1:6969/chat`. Saúde: `GET http://127.0.0.1:6969/health`.

## Testes

```powershell
python -m pytest
```

Os testes cobrem emergência, pedido médico, funcionalidade do app, UBS/UPA/SUS, recomendação com e sem dados, precedência de segurança e pergunta desconhecida.

## Dados simulados

`data/simulated_units.json` contém unidades fictícias apenas para demonstração e teste. Em execução integrada, a resposta usa exclusivamente `contexto.dadosOperacionais` enviado pelo backend Java. Na ausência desses dados, encaminha ao suporte humano.

## Limitações

- A classificação é determinística e não é um classificador clínico.
- O RAG local cobre apenas UBS, UPA e SUS; não consulta disponibilidade externa.
- Distância, espera e status podem ser simulados pelo Fila Certa e são identificados como tal.
- O agente não mantém prontuário, dados clínicos ou histórico conversacional persistente.
