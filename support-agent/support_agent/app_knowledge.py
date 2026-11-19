from support_agent.text import normalize


DOCUMENTED_FLOWS: tuple[tuple[tuple[str, ...], str], ...] = (
    (
        ("entrar na fila", "entro na fila", "garantir minha vez"),
        "Na aba Descobrir, escolha uma unidade, selecione ‘Entrar na fila’, informe como quer ser chamado e confirme em ‘Garantir minha vez’.",
    ),
    (
        ("sair da fila", "cancelar"),
        "Abra Meu ticket e use ‘Sair da fila’. O cancelamento encerra o ticket e libera a posição.",
    ),
    (
        ("meu ticket", "minha posicao", "hora de sair"),
        "Abra Meu ticket para acompanhar posição, espera estimada e o aviso de quando sair. A tela é atualizada automaticamente.",
    ),
    (
        ("localizacao", "gps", "endereco"),
        "Em Descobrir, use ‘Minha localização’, pesquise um endereço ou ajuste o ponto no mapa. A localização permite calcular distância e deslocamento.",
    ),
    (
        ("painel",),
        "A aba Painel é a visão do atendente: mostra guichês e fila e permite chamar, finalizar ou marcar ausência.",
    ),
    (
        ("previsao",),
        "A aba Previsão mostra leituras e estimativas agregadas de espera de Barueri. Ela não substitui avaliação ou orientação médica.",
    ),
    (
        ("metricas",),
        "A aba Métricas reúne indicadores agregados, como espera por horário e por unidade e distância média.",
    ),
    (
        ("como funciona",),
        "O Fila Certa permite comparar unidades, entrar remotamente em uma fila e acompanhar o momento de sair para chegar perto da sua vez.",
    ),
)


def answer_app_feature(message: str) -> str | None:
    text = normalize(message)
    for terms, answer in DOCUMENTED_FLOWS:
        if any(term in text for term in terms):
            return answer
    return None
