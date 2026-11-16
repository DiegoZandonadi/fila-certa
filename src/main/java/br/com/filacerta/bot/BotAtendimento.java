package br.com.filacerta.bot;

import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;

/** Contrato do agente de suporte. Implementacoes: assistente local (regras) e bot externo (HTTP). */
public interface BotAtendimento {

    BotResposta responder(BotPergunta pergunta);
}
