package br.com.filacerta.web;

import br.com.filacerta.bot.BotService;
import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final BotService bot;

    public BotController(BotService bot) {
        this.bot = bot;
    }

    @PostMapping("/mensagem")
    public BotResposta mensagem(@Valid @RequestBody BotPergunta pergunta) {
        return bot.responder(pergunta);
    }
}
