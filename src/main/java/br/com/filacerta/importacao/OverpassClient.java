package br.com.filacerta.importacao;

import br.com.filacerta.domain.Categoria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Busca estabelecimentos reais de uma cidade no OpenStreetMap (API Overpass).
 * Dados abertos sob licenca ODbL: (c) colaboradores do OpenStreetMap.
 */
@Component
public class OverpassClient {

    private static final Pattern CIDADE_VALIDA = Pattern.compile("^[\\p{L} .'-]{2,60}$");
    private static final Pattern IGNORAR = Pattern.compile(
            "(?i)consult|\\bdra?\\.|odont|dentist|reprodu|est[eé]tic|veterin|laborat|[oó]tica|psic|fisioter");
    private static final Pattern PRIORIDADE = Pattern.compile("(?i)pronto|\\bupa\\b|hospital|\\bubs\\b|unidade b[aá]sica|prefeitura|cart[oó]rio");

    /** Quantos de cada categoria entram (mantem o mapa legivel e a simulacao leve). */
    private static final int LIMITE_SAUDE = 20, LIMITE_BANCO = 10, LIMITE_GOVERNO = 6, LIMITE_CARTORIO = 4;

    private final String endpoint;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OverpassClient(ObjectMapper json,
                          @Value("${filacerta.importacao.overpass-url:https://overpass-api.de/api/interpreter}") String endpoint) {
        this.json = json;
        this.endpoint = endpoint;
    }

    public List<UnidadeImportada> buscar(String cidade) throws IOException, InterruptedException {
        if (cidade == null || !CIDADE_VALIDA.matcher(cidade.trim()).matches()) {
            throw new IllegalArgumentException("Nome de cidade invalido.");
        }
        String c = cidade.trim();
        String consulta = "[out:json][timeout:60];"
                + "area[\"name\"=\"" + c + "\"][\"boundary\"=\"administrative\"]->.a;("
                + "nwr[\"amenity\"~\"^(hospital|clinic)$\"](area.a);"
                + "nwr[\"amenity\"=\"bank\"](area.a);"
                + "nwr[\"amenity\"~\"^(townhall|post_office)$\"](area.a);"
                + "nwr[\"office\"~\"^(government|notary)$\"](area.a););out center tags;";
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "fila-certa-projeto-academico-fiap")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("Overpass respondeu HTTP " + resp.statusCode());
        return interpretar(resp.body(), c);
    }

    /** Separado de buscar() para ser testavel sem rede. */
    List<UnidadeImportada> interpretar(String corpo, String cidade) throws IOException {
        JsonNode raiz = json.readTree(corpo);
        List<UnidadeImportada> todas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (JsonNode el : raiz.path("elements")) {
            JsonNode tags = el.path("tags");
            String nome = tags.path("name").asText("").trim();
            if (nome.isEmpty() || IGNORAR.matcher(nome).find()) continue;
            JsonNode centro = el.has("center") ? el.get("center") : el;
            if (!centro.has("lat") || !centro.has("lon")) continue;
            double lat = centro.get("lat").asDouble(), lon = centro.get("lon").asDouble();

            Categoria categoria = categoria(tags);
            if (categoria == null) continue;
            String chave = normalizar(nome) + "|" + Math.round(lat * 1000) + "|" + Math.round(lon * 1000);
            if (!vistos.add(chave)) continue;

            String rua = tags.path("addr:street").asText("").trim();
            String numero = tags.path("addr:housenumber").asText("").trim();
            String endereco = (rua + " " + numero).trim();
            String bairro = primeiroNaoVazio(tags.path("addr:suburb").asText(""), tags.path("addr:neighbourhood").asText(""), cidade);
            todas.add(new UnidadeImportada(nome, categoria, tipo(tags), endereco.isEmpty() ? cidade : endereco, bairro, lat, lon));
        }
        return limitar(todas);
    }

    private static Categoria categoria(JsonNode tags) {
        String amenity = tags.path("amenity").asText(""), office = tags.path("office").asText("");
        if (amenity.equals("hospital") || amenity.equals("clinic")) return Categoria.SAUDE;
        if (amenity.equals("bank")) return Categoria.BANCO;
        if (office.equals("notary")) return Categoria.CARTORIO;
        if (amenity.equals("townhall") || amenity.equals("post_office") || office.equals("government")) return Categoria.GOVERNO;
        return null;
    }

    private static String tipo(JsonNode tags) {
        String amenity = tags.path("amenity").asText("");
        return amenity.isEmpty() ? tags.path("office").asText("") : amenity;
    }

    private static List<UnidadeImportada> limitar(List<UnidadeImportada> todas) {
        Comparator<UnidadeImportada> ordem = Comparator
                .comparingInt((UnidadeImportada u) -> PRIORIDADE.matcher(u.nome()).find() ? 0 : 1)
                .thenComparingInt(u -> "hospital".equals(u.tipo()) ? 0 : 1)
                .thenComparing(UnidadeImportada::nome);
        List<UnidadeImportada> saida = new ArrayList<>();
        for (Categoria c : Categoria.values()) {
            int limite = switch (c) {
                case SAUDE -> LIMITE_SAUDE;
                case BANCO -> LIMITE_BANCO;
                case GOVERNO -> LIMITE_GOVERNO;
                case CARTORIO -> LIMITE_CARTORIO;
                default -> 0;
            };
            todas.stream().filter(u -> u.categoria() == c).sorted(ordem).limit(limite).forEach(saida::add);
        }
        return saida;
    }

    private static String primeiroNaoVazio(String... valores) {
        for (String v : valores) if (v != null && !v.isBlank()) return v.trim();
        return "";
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
