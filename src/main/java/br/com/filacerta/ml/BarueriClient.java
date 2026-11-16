package br.com.filacerta.ml;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le o painel publico "Tempo medio de espera - Pronto Atendimento" da Prefeitura de Barueri/SP.
 * Sao dados agregados e publicos (nenhum dado pessoal).
 */
@Component
public class BarueriClient {

    public record Leitura(String local, int minutos, LocalDateTime ts) {
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Pattern HORAS = Pattern.compile("(\\d+)\\s*h");
    private static final Pattern MINUTOS = Pattern.compile("(\\d+)\\s*min");
    private static final Pattern NUMERO = Pattern.compile("(\\d+)");

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public BarueriClient(@Value("${filacerta.barueri.url:https://saudeservicos.barueri.sp.gov.br/AgendamentoOnline/TempoAtendimentoPS.aspx}") String url) {
        this.url = url;
    }

    public List<Leitura> buscar() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (projeto academico FIAP - GS CitizenTech)")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("Prefeitura respondeu HTTP " + resp.statusCode());
        return interpretar(resp.body());
    }

    /** Separado de buscar() para ser testavel sem rede. */
    static List<Leitura> interpretar(String html) {
        Document doc = Jsoup.parse(html);
        List<Leitura> saida = new ArrayList<>();
        for (Element linha : doc.select("tr")) {
            Elements celulas = linha.select("td");
            if (celulas.size() != 3) continue; // cabecalho (th) e outras tabelas ficam de fora
            String local = celulas.get(0).text().trim().replaceAll("\\s+", " ");
            Integer minutos = parseMinutos(celulas.get(1).text());
            LocalDateTime ts = parseData(celulas.get(2).text());
            if (!local.isEmpty() && minutos != null && ts != null) saida.add(new Leitura(local, minutos, ts));
        }
        return saida;
    }

    /**
     * "2 minutos" -> 2 | "1 h e 36 min" -> 96 | "Sem espera" -> 0.
     * (O notebook original pegava so o primeiro numero e lia "1 h e 36 min" como 1 minuto.)
     */
    static Integer parseMinutos(String texto) {
        String t = texto == null ? "" : texto.toLowerCase().trim();
        if (t.contains("sem ")) return 0;
        Matcher h = HORAS.matcher(t);
        Matcher m = MINUTOS.matcher(t);
        boolean temH = h.find();
        boolean temM = m.find();
        if (temH || temM) return (temH ? Integer.parseInt(h.group(1)) * 60 : 0) + (temM ? Integer.parseInt(m.group(1)) : 0);
        Matcher n = NUMERO.matcher(t);
        return n.find() ? Integer.parseInt(n.group(1)) : null;
    }

    static LocalDateTime parseData(String texto) {
        try {
            return LocalDateTime.parse(texto.trim(), DATA_HORA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
