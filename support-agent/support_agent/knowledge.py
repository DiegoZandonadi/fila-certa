from dataclasses import dataclass

from support_agent.text import normalize


@dataclass(frozen=True)
class Article:
    key: str
    aliases: tuple[str, ...]
    summary: str
    source: str


ARTICLES = (
    Article(
        "ubs",
        ("ubs", "unidade basica", "atencao primaria"),
        "A UBS é a porta de entrada preferencial da Atenção Primária do SUS e oferece acompanhamento e ações contínuas de saúde conforme a estrutura local.",
        "https://www.gov.br/saude/pt-br/assuntos/noticias/2025/outubro/ubs-ou-upa-saiba-quando-procurar-a-unidade-mais-proxima-de-voce/",
    ),
    Article(
        "upa",
        ("upa", "pronto atendimento"),
        "A UPA 24h integra a rede de urgência e oferece atendimento ininterrupto para situações agudas, conforme sua estrutura local.",
        "https://www.gov.br/saude/pt-br/assuntos/saude-de-a-a-z/u/upa-24h",
    ),
    Article(
        "sus",
        ("sus", "sistema unico"),
        "O SUS é o sistema público de saúde brasileiro, com acesso universal e gratuito e serviços organizados em diferentes níveis de atenção.",
        "https://www.gov.br/saude/pt-br/sus",
    ),
)


def retrieve(message: str) -> list[Article]:
    """Recuperação local fundamentada; perguntas sem tópico não retornam conteúdo."""
    text = normalize(message)
    return [article for article in ARTICLES if any(alias in text for alias in article.aliases)]


def answer_public_service(message: str) -> tuple[str, list[str]] | None:
    articles = retrieve(message)
    if not articles:
        return None
    body = " ".join(article.summary for article in articles)
    sources = list(dict.fromkeys(article.source for article in articles))
    return f"{body} As informações são gerais; disponibilidade, horário e espera devem ser confirmados no sistema ou com a unidade.", sources
