from fastapi import FastAPI

from support_agent.models import ChatRequest, ChatResponse
from support_agent.orchestrator import orchestrate


app = FastAPI(title="Fila Certa Support Agent", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    return orchestrate(request)
