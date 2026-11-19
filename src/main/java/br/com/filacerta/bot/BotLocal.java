package br.com.filacerta.bot;

import br.com.filacerta.service.FilaService;
import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import br.com.filacerta.web.dto.Dtos.TicketView;
import br.com.filacerta.web.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * Assistente por regras. Serve de "plano B" quando o bot externo nao esta configurado ou fora do ar,
 * e tambem mostra como usar os dados ao vivo da fila numa resposta.
 */
@Component
public class BotLocal implements BotAtendimento {

    private static final List<String> SUGESTOES = List.of(
            "Quanto tempo falta?", "Quando devo sair?", "Qual a fila mais rápida?", "Como funciona?");

    private final FilaService fila;

    public BotLocal(FilaService fila) {
        this.fila = fila;
    }

    private static final List<String> EMERGENCIA = List.of(
            "dor no peito", "falta de ar", "desmai", "sangramento", "acidente", "convuls", "infarto", "overdose", "parada cardiaca");
    private static final List<String> DADOS_AO_VIVO = List.of(
            "quanto tempo", "tempo de espera", "quanto falta", "espera", "quando devo sair", "hora de sair", "quando sair",
            "minha posicao", "na minha frente", "quantas pessoas");
    private static final List<String> RECOMENDACAO = List.of(
            "fila mais rapida", "melhor fila", "menos fila", "mais rapida", "qual a fila", "qual unidade",
            "unidade recomend", "por que recomend", "porque recomend", "menos cheia", "menor espera", "onde ir");

    /** Possivel emergencia: a resposta e sempre encaminhar para urgencia, sem avaliar gravidade. */
    public boolean emergencia(String mensagem) {
        return tem(normalizar(mensagem), EMERGENCIA.toArray(new String[0]));
    }

    /** Pergunta sobre dados que so o sistema tem (ticket, posicao, espera, trajeto, fila mais rapida). */
    public boolean dadoAoVivo(String mensagem) {
        return tem(normalizar(mensagem), DADOS_AO_VIVO.toArray(new String[0]));
    }

    /** Pedido que deve ser explicado pelo agente a partir dos dados operacionais enviados pelo app. */
    public boolean pedidoRecomendacao(String mensagem) {
        return tem(normalizar(mensagem), RECOMENDACAO.toArray(new String[0]));
    }

    @Override
    public BotResposta responder(BotPergunta p) {
        String msg = normalizar(p.mensagem());
        TicketView ticket = buscarTicket(p.ticketId());

        if (emergencia(p.mensagem())) {
            return resp("Busque atendimento de urgência imediatamente, como uma UPA ou pronto-socorro, ou acione o SAMU pelo número 192. "
                    + "Não consigo avaliar a gravidade da situação por aqui.", "emergency");
        }

        if (tem(msg, "oi", "ola", "bom dia", "boa tarde", "boa noite", "e ai")) {
            return resp("Oi! Eu sou o assistente da Fila Certa. Posso te dizer quanto falta, quando sair de casa "
                    + "ou qual é a fila mais rápida perto de você.");
        }
        if (tem(msg, "como funciona", "ajuda", "o que e", "para que serve", "ajudar")) {
            return resp("A Fila Certa te coloca na fila sem você estar lá. Eu cruzo o tempo de espera com a sua "
                    + "distância e aviso a hora certa de sair — assim você chega quando a sua vez chega.");
        }
        if (tem(msg, "sair", "saia", "hora de ir", "que horas", "quando devo", "quando ir", "partir")) {
            return ticket == null ? semTicket() : quandoSair(ticket);
        }
        if (tem(msg, "posicao", "quantas pessoas", "na minha frente", "meu lugar", "qual minha vez")) {
            return ticket == null ? semTicket() : posicao(ticket);
        }
        if (tem(msg, "tempo", "espera", "demora", "demorar", "falta", "quanto")) {
            return ticket == null ? melhorFila(p) : espera(ticket);
        }
        if (tem(msg, "cancel", "desist", "sair da fila")) {
            return resp("Para sair da fila, use o botão “Sair da fila” na aba Meu ticket. Fica registrado e libera "
                    + "o seu lugar para quem vem depois.");
        }
        if (tem(msg, "prioridade", "preferencial", "idoso", "gestante", "deficiencia")) {
            return resp("Ao entrar na fila você pode marcar “Atendimento prioritário” (idosos, gestantes, pessoas com "
                    + "deficiência). Quem é prioritário passa na frente de quem aguarda sem prioridade.");
        }
        if (pedidoRecomendacao(p.mensagem()) || tem(msg, "rapida", "melhor", "perto", "recomend", "onde", "menos fila", "vazia")) {
            return melhorFila(p);
        }
        if (tem(msg, "obrigad", "valeu", "tchau")) {
            return resp("Por nada. Se precisar de mais alguma coisa, é só chamar.");
        }
        if (tem(msg, "ubs", "upa", "sus", "posto de saude", "pronto socorro", "pronto atendimento")) {
            return resp("Não consegui consultar a base de informações sobre UBS, UPA e SUS agora. Tente de novo em instantes "
                    + "ou procure o suporte humano. Se for uma urgência, ligue 192 (SAMU) ou procure atendimento de urgência.", "public_service");
        }
        return resp("Não encontrei informação segura e documentada para responder. Procure o suporte humano do Fila Certa.", "not_found");
    }

    private BotResposta espera(TicketView t) {
        return switch (t.status()) {
            case AGUARDANDO -> resp("Faltam cerca de " + min(t.esperaMin()) + " para a sua vez em " + t.estabelecimentoNome()
                    + ". Você é o " + t.posicao() + "º da fila.");
            case CHAMADO -> resp("Já é a sua vez! Vá ao guichê " + t.guiche() + ".");
            default -> resp("O seu ticket " + t.codigo() + " já foi encerrado (" + t.status().name().toLowerCase() + ").");
        };
    }

    private BotResposta posicao(TicketView t) {
        if (t.posicao() == null) return espera(t);
        String frente = t.pessoasNaFrente() == 0 ? "Ninguém na sua frente!" : t.pessoasNaFrente() + " pessoa(s) na sua frente.";
        return resp("Você é o " + t.posicao() + "º em " + t.estabelecimentoNome() + ". " + frente);
    }

    private BotResposta quandoSair(TicketView t) {
        if (t.posicao() == null) return espera(t);
        if (t.deslocamentoMin() == null) {
            return resp("Ainda não sei onde você está. Permita a localização (ou arraste o seu ponto no radar) e eu calculo a hora exata de sair.");
        }
        if (t.saiEmMin() != null && t.saiEmMin() == 0) {
            return resp("Saia agora! São " + min(t.deslocamentoMin()) + " até " + t.estabelecimentoNome()
                    + " e faltam ~" + min(t.esperaMin()) + " para a sua vez.");
        }
        return resp("Saia em cerca de " + min(t.saiEmMin()) + ". O trajeto leva " + min(t.deslocamentoMin())
                + " e a sua vez chega em ~" + min(t.esperaMin()) + ".");
    }

    private BotResposta melhorFila(BotPergunta p) {
        List<EstabelecimentoResumo> lista = fila.listar(p.latitude(), p.longitude(), p.modo(), null);
        EstabelecimentoResumo r = lista.stream().filter(EstabelecimentoResumo::recomendado).findFirst().orElse(null);
        if (r == null) {
            return resp("Não recebi dados operacionais suficientes para explicar uma recomendação. "
                    + "Consulte novamente os resultados ou procure o suporte humano.", "system_recommendation");
        }
        StringBuilder sb = new StringBuilder("O sistema marcou ").append(r.nome())
                .append(" como recomendada com base nos dados disponíveis: espera estimada de ")
                .append(min(r.esperaMin())).append("; serviço: ").append(r.categoriaRotulo())
                .append("; status: ").append(statusOperacional(r)).append(".");
        if (r.distanciaKm() != null) sb.append(" Distância informada: ").append(r.distanciaKm()).append(" km.");
        sb.append(" Os dados de fila são simulados para demonstração. Eu não faço triagem nem altero prioridades.");
        return resp(sb.toString(), "system_recommendation");
    }

    private BotResposta semTicket() {
        return resp("Você ainda não tem um ticket ativo. Escolha um local na aba Descobrir e toque em “Entrar na fila”.");
    }

    private TicketView buscarTicket(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return fila.consultar(id);
        } catch (RecursoNaoEncontradoException e) {
            return null;
        }
    }

    private static BotResposta resp(String texto) {
        return new BotResposta(texto, "assistente-local", SUGESTOES);
    }

    private static BotResposta resp(String texto, String categoria) {
        return new BotResposta(texto, "assistente-local", SUGESTOES, categoria);
    }

    static String statusOperacional(EstabelecimentoResumo r) {
        return r.guichesLivres() > 0 ? "GUICHE_DISPONIVEL" : "FILA_ATIVA";
    }

    private static String min(Integer v) {
        return v == null ? "—" : v + " min";
    }

    private static boolean tem(String msg, String... chaves) {
        for (String c : chaves) if (msg.contains(c)) return true;
        return false;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
