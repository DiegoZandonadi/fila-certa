package br.com.filacerta.bot;

import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Escolhe o bot externo (se configurado) e cai no assistente local em caso de falha. */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final BotExterno externo;
    private final BotLocal local;

    public BotService(BotExterno externo, BotLocal local) {
        this.externo = externo;
        this.local = local;
    }

    public BotResposta responder(BotPergunta pergunta) {
        if (externo.configurado()) {
            try {
                return externo.responder(pergunta);
            } catch (RuntimeException ex) {
                log.warn("Bot externo indisponível ({}). Usando assistente local.", ex.getMessage());
            }
        }
        return local.responder(pergunta);
    }

    public boolean externoConfigurado() {
        return externo.configurado();
    }
}
