import re
import unicodedata


def normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value or "")
    without_accents = "".join(char for char in decomposed if unicodedata.category(char) != "Mn")
    return re.sub(r"\s+", " ", without_accents.lower()).strip()


def contains_any(text: str, terms: tuple[str, ...]) -> bool:
    return any(term in text for term in terms)
