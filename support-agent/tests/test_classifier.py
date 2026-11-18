import pytest

from support_agent.classifier import classify_message


@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("Tive um desmaio", "emergency"),
        ("Você pode me diagnosticar?", "medical_request"),
        ("Por que recomendou esta unidade?", "system_recommendation"),
        ("Como funciona o meu ticket?", "app_feature"),
        ("O que é atenção primária?", "public_service"),
        ("Qual é a capital da França?", "not_found"),
    ],
)
def test_classification(message, expected):
    assert classify_message(message) == expected
