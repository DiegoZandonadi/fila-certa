from support_agent.text import contains_any, normalize


EMERGENCY_TERMS = (
    "dor no peito", "falta de ar", "desmai", "sangramento intenso",
    "acidente grave", "convuls", "infarto", "overdose", "parada cardiaca",
)
MEDICAL_TERMS = (
    "o que tomo", "qual remedio", "que remedio", "medicamento", "dose",
    "dosagem", "tratamento", "diagnost", "qual exame", "me receit",
)
RECOMMENDATION_TERMS = (
    "por que recomend", "porque recomend", "motivo da recomend",
    "unidade recomend", "recomendada", "recomendou", "qual unidade", "melhor unidade", "melhor fila",
    "fila mais rapida", "menos cheia", "menor espera", "onde ir",
)
APP_TERMS = (
    "como funciona", "como entrar", "entro na fila", "entrar na fila", "sair da fila",
    "cancelar", "meu ticket", "minha posicao", "hora de sair", "localizacao",
    "mapa", "painel", "previsao", "metricas", "aplicativo", "app",
    "carteirinha", "senha", "cadastro", "login",
)
PUBLIC_SERVICE_TERMS = (
    "ubs", "upa", "sus", "unidade basica", "pronto atendimento",
    "servico publico", "atencao primaria",
)


def classify_message(message: str) -> str:
    """Classifica por intenção, sempre dando precedência às barreiras de segurança."""
    text = normalize(message)
    if contains_any(text, EMERGENCY_TERMS):
        return "emergency"
    if contains_any(text, MEDICAL_TERMS):
        return "medical_request"
    if contains_any(text, RECOMMENDATION_TERMS):
        return "system_recommendation"
    if contains_any(text, APP_TERMS):
        return "app_feature"
    if contains_any(text, PUBLIC_SERVICE_TERMS):
        return "public_service"
    return "not_found"
