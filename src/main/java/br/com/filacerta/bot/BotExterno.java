package br.com.filacerta.bot;

import br.com.filacerta.service.FilaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import br.com.filacerta.web.exception.RecursoNaoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador para o bot de suporte feito pelo colega (qualquer linguagem/stack).
 * <p>
 * Envia (POST JSON) para {@code filacerta.bot.url}:
 * <pre>
 * { "mensagem": "...", "sessaoId": "...", "ticketId": "...",
 *   "contexto": { "ticket": {...}, "estabelecimentos": [ {...}, ... ] } }
 * </pre>
 * e le a resposta de qualquer um destes campos: resposta, reply, message, answer, response, text, output.
 * Se o bot nao responder, o {@link BotService} cai automaticamente no assistente local.
 */
@Component
public class BotExterno implements BotAtendimento {

    private static final List<String> CAMPOS_RESPOSTA =
            List.of("resposta", "reply", "message", "answer", "response", "text", "output");

    private final String url;
    private final String apiKey;
    private final RestClient http;
    private final FilaService fila;
    private final ObjectMapper json;

    public BotExterno(FilaService fila, ObjectMapper json,
                      @Value("${filacerta.bot.url:}") String url,
                      @Value("${filacerta.bot.api-key:}") String apiKey,
                      @Value("${filacerta.bot.timeout-ms:4000}") int timeoutMs) {
        this.fila = fila;
        this.json = json;
        this.url = url == null ? "" : url.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configurado() {
        return !url.isEmpty();
    }

    @Override
    public BotResposta responder(BotPergunta p) {
        Map<String, Object> contexto = new LinkedHashMap<>();
        if (p.ticketId() != null && !p.ticketId().isBlank()) {
            try {
                contexto.put("ticket", fila.consultar(p.ticketId()));
            } catch (RecursoNaoEncontradoException ignorado) {
                // ticket inexistente: segue sem contexto de ticket
            }
        }
        contexto.put("estabelecimentos", fila.listar(p.latitude(), p.longitude(), p.modo(), null));

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("message", p.mensagem());   // campo exigido pelo bot (POST /chat)
        corpo.put("mensagem", p.mensagem());  // o mesmo texto em portugues, para outros bots
        corpo.put("sessaoId", p.sessaoId());
        corpo.put("ticketId", p.ticketId());
        corpo.put("contexto", contexto);

        RestClient.RequestBodySpec req = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
        if (!apiKey.isEmpty()) req.header("Authorization", "Bearer " + apiKey);

        // Corpo ja serializado => vai com Content-Length (alguns servidores nao aceitam "chunked")
        String payload;
        try {
            payload = json.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar a mensagem para o bot", e);
        }
        Map<String, Object> retorno = req.body(payload).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        String texto = extrair(retorno);
        if (texto == null || texto.isBlank()) throw new IllegalStateException("Bot externo respondeu sem texto");
        Object categoria = retorno.get("category");
        return new BotResposta(texto, "bot-externo", List.of(), categoria instanceof String c ? c : null);
    }

    private static String extrair(Map<String, Object> retorno) {
        if (retorno == null) return null;
        for (String campo : CAMPOS_RESPOSTA) {
            Object v = retorno.get(campo);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
