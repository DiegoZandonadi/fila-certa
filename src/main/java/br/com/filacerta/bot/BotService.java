package br.com.filacerta.bot;

import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Orquestra o agente FastAPI (se configurado) e cai no assistente local em caso de falha. */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final BotExterno externo;
    private final BotLocal local;

    public BotService(BotExterno externo, BotLocal local) {
        this.externo = externo;
        this.local = local;
    }

    /**
     * Quem responde cada pergunta:
     * <ul>
     *   <li>dados da fila (ticket, posicao, espera, hora de sair, fila mais rapida): o proprio app, que tem esses dados;</li>
     *   <li>emergencia, orientacao medica, funcoes do aplicativo, servicos publicos e recomendacao: o agente de suporte;</li>
     *   <li>bot fora do ar ou sem resposta ("not_found"): assistente local, que tambem encaminha emergencias ao 192.</li>
     * </ul>
     */
    public BotResposta responder(BotPergunta pergunta) {
        boolean emergencia = local.emergencia(pergunta.mensagem());
        boolean recomendacao = local.pedidoRecomendacao(pergunta.mensagem());
        if (!emergencia && !recomendacao && local.dadoAoVivo(pergunta.mensagem())) {
            return local.responder(pergunta);
        }
        if (externo.configurado()) {
            try {
                BotResposta resposta = externo.responder(pergunta);
                if (!"not_found".equals(resposta.categoria())) return resposta;
                log.info("Bot externo nao classificou a mensagem (not_found). Usando assistente local.");
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
