from typing import Any

from pydantic import BaseModel, Field, model_validator


class ChatRequest(BaseModel):
    """Contrato compatível com o backend Java e com o agente original."""

    message: str | None = None
    mensagem: str | None = None
    sessaoId: str | None = None
    ticketId: str | None = None
    contexto: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_message(self):
        selected = (self.message or self.mensagem or "").strip()
        if not selected:
            raise ValueError("message ou mensagem deve ser informado")
        self.message = selected
        return self


class ChatResponse(BaseModel):
    category: str
    answer: str
    flow: list[str]
    requiresHumanSupport: bool = False
    sources: list[str] = Field(default_factory=list)
