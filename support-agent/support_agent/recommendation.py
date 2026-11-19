from typing import Any


HUMAN_SUPPORT = (
    "Não recebi dados operacionais suficientes para explicar uma recomendação. "
    "Consulte novamente os resultados do Fila Certa ou procure o suporte humano."
)


def _units(context: dict[str, Any]) -> list[dict[str, Any]]:
    operational = context.get("dadosOperacionais")
    if isinstance(operational, list):
        return [unit for unit in operational if isinstance(unit, dict)]

    # Compatibilidade com versões anteriores do backend.
    establishments = context.get("estabelecimentos")
    if not isinstance(establishments, list):
        return []
    normalized = []
    for unit in establishments:
        if not isinstance(unit, dict):
            continue
        normalized.append({
            "nome": unit.get("nome"),
            "distanciaKm": unit.get("distanciaKm"),
            "tempoEstimadoEsperaMin": unit.get("esperaMin"),
            "servicoDisponivel": unit.get("categoriaRotulo"),
            "status": unit.get("statusOperacional"),
            "recomendadaPeloSistema": unit.get("recomendado", False),
            "dadosSimulados": unit.get("dadosSimulados"),
        })
    return normalized


def explain_recommendation(context: dict[str, Any]) -> tuple[str, bool]:
    units = _units(context)
    recommended = next((unit for unit in units if unit.get("recomendadaPeloSistema") is True), None)
    if not recommended or not recommended.get("nome"):
        return HUMAN_SUPPORT, True

    factors: list[str] = []
    if recommended.get("distanciaKm") is not None:
        factors.append(f"distância de {recommended['distanciaKm']} km")
    if recommended.get("tempoEstimadoEsperaMin") is not None:
        factors.append(f"espera estimada de {recommended['tempoEstimadoEsperaMin']} min")
    if recommended.get("servicoDisponivel"):
        factors.append(f"serviço disponível: {recommended['servicoDisponivel']}")
    if recommended.get("status"):
        factors.append(f"status informado: {recommended['status']}")

    if not factors:
        return HUMAN_SUPPORT, True

    simulation_notice = " Os dados operacionais são simulados para demonstração." if recommended.get("dadosSimulados") is True else ""
    answer = (
        f"O sistema marcou {recommended['nome']} como recomendada com base nestes dados recebidos: "
        f"{'; '.join(factors)}.{simulation_notice} Eu apenas explico a indicação do sistema; "
        "não faço triagem, não avalio gravidade e não altero prioridades de atendimento."
    )
    return answer, False
