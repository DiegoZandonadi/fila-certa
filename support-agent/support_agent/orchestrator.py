from support_agent.app_knowledge import answer_app_feature
from support_agent.classifier import classify_message
from support_agent.knowledge import answer_public_service
from support_agent.models import ChatRequest, ChatResponse
from support_agent.recommendation import explain_recommendation


HUMAN_SUPPORT = "Não encontrei informação segura e documentada para responder. Procure o suporte humano do Fila Certa."


def orchestrate(request: ChatRequest) -> ChatResponse:
    """Fluxo explícito: classificação → segurança → fonte de resposta → suporte."""
    category = classify_message(request.message or "")
    flow = ["classification"]

    flow.append("safety")
    if category == "emergency":
        return ChatResponse(
            category=category,
            answer=(
                "Busque atendimento de urgência imediatamente, como uma UPA ou pronto-socorro, "
                "ou acione o SAMU pelo número 192. Não consigo avaliar a gravidade da situação por aqui."
            ),
            flow=flow,
        )
    if category == "medical_request":
        return ChatResponse(
            category=category,
            answer=(
                "Não posso diagnosticar, indicar medicamentos, doses, exames ou tratamentos. "
                "Procure um profissional de saúde qualificado. Se houver possível emergência, acione o SAMU pelo 192."
            ),
            flow=flow,
        )

    if category == "app_feature":
        flow.append("app_knowledge")
        answer = answer_app_feature(request.message or "")
        if answer:
            return ChatResponse(category=category, answer=answer, flow=flow)

    if category == "public_service":
        flow.append("rag")
        retrieved = answer_public_service(request.message or "")
        if retrieved:
            answer, sources = retrieved
            return ChatResponse(category=category, answer=answer, flow=flow, sources=sources)

    if category == "system_recommendation":
        flow.append("recommendation")
        answer, requires_support = explain_recommendation(request.contexto)
        if not requires_support:
            return ChatResponse(category=category, answer=answer, flow=flow)

    flow.append("human_support")
    if category == "system_recommendation":
        answer, _ = explain_recommendation(request.contexto)
    else:
        answer = HUMAN_SUPPORT
    return ChatResponse(
        category=category,
        answer=answer,
        flow=flow,
        requiresHumanSupport=True,
    )
