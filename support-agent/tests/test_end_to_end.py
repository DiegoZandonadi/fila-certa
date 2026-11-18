import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from support_agent.main import app


client = TestClient(app)
DATA = json.loads((Path(__file__).parents[1] / "data" / "simulated_units.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize(
    ("message", "category", "required"),
    [
        ("Estou com dor no peito e falta de ar", "emergency", ("SAMU", "192")),
        ("Qual remédio e dose devo tomar?", "medical_request", ("Não posso", "medicamentos")),
        ("Como entro na fila pelo aplicativo?", "app_feature", ("Descobrir", "Entrar na fila")),
        ("Qual a diferença entre UBS e UPA?", "public_service", ("UBS", "UPA")),
        ("Uma pergunta que o agente não conhece", "not_found", ("suporte humano",)),
    ],
)
def test_complete_flow_for_required_scenarios(message, category, required):
    response = client.post("/chat", json={"message": message})
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == category
    assert body["flow"][:2] == ["classification", "safety"]
    for fragment in required:
        assert fragment.casefold() in body["answer"].casefold()


def test_recommendation_uses_only_received_operational_data():
    response = client.post(
        "/chat",
        json={
            "message": "Por que essa unidade foi recomendada?",
            "contexto": {"dadosOperacionais": DATA},
        },
    )
    body = response.json()
    assert body["category"] == "system_recommendation"
    assert body["requiresHumanSupport"] is False
    assert "UBS Central (simulada)" in body["answer"]
    assert "1.8 km" in body["answer"]
    assert "12 min" in body["answer"]
    assert "Atendimento básico" in body["answer"]
    assert "GUICHE_DISPONIVEL" in body["answer"]
    assert "não faço triagem" in body["answer"]


def test_recommendation_without_data_routes_to_human_support():
    response = client.post("/chat", json={"message": "Qual unidade está menos cheia?"})
    body = response.json()
    assert body["category"] == "system_recommendation"
    assert body["requiresHumanSupport"] is True
    assert body["flow"][-1] == "human_support"
    assert "dados operacionais" in body["answer"]
    assert "suporte humano" in body["answer"]


def test_emergency_wins_over_recommendation_and_does_not_triage():
    response = client.post(
        "/chat",
        json={
            "message": "Estou com dor no peito; qual unidade está menos cheia?",
            "contexto": {"dadosOperacionais": DATA},
        },
    )
    body = response.json()
    assert body["category"] == "emergency"
    assert "SAMU" in body["answer"]
    assert "UBS Central" not in body["answer"]
    assert "diagnóstico" not in body["answer"].casefold()


def test_undocumented_app_feature_routes_to_support_instead_of_inventing():
    response = client.post("/chat", json={"message": "Como acesso minha carteirinha no app?"})
    body = response.json()
    assert body["category"] == "app_feature"
    assert body["requiresHumanSupport"] is True
    assert "suporte humano" in body["answer"]


def test_legacy_portuguese_message_field_is_accepted():
    response = client.post("/chat", json={"mensagem": "O que é o SUS?"})
    assert response.status_code == 200
    assert response.json()["category"] == "public_service"
