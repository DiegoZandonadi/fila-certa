package br.com.filacerta.ml;

import br.com.filacerta.ml.IaDtos.IaView;
import br.com.filacerta.ml.IaDtos.MetaIa;
import br.com.filacerta.ml.IaDtos.PrevisaoLocal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aplica os modelos do notebook ao ultimo instante coletado: espera prevista para daqui a ~15 min
 * e probabilidade de a espera passar do limiar "longa". Reproduz as variaveis da secao 4.1 do notebook.
 */
@Service
public class PrevisaoService {

    private static final Logger log = LoggerFactory.getLogger(PrevisaoService.class);
    private static final int TOLERANCIA_MIN = 7;
    private static final List<String> SETORES_RECOMENDADOS = List.of("CLINICO", "PEDIATRIA", "ORTOPEDIA", "EMERGENCIA");

    private final LeituraEsperaRepository leituras;
    private final ColetorBarueri coletor;
    private final ModeloEspera modelo;

    public PrevisaoService(LeituraEsperaRepository leituras, ColetorBarueri coletor, ObjectMapper json,
                           @Value("${filacerta.ml.modelo-path:}") String caminhoExterno) {
        this.leituras = leituras;
        this.coletor = coletor;
        this.modelo = carregar(json, caminhoExterno);
    }

    private static ModeloEspera carregar(ObjectMapper json, String caminhoExterno) {
        try {
            if (caminhoExterno != null && !caminhoExterno.isBlank()) {
                ModeloEspera m = json.readValue(Files.readString(Path.of(caminhoExterno)), ModeloEspera.class);
                log.info("Modelo de ML carregado de {} (treino: {}).", caminhoExterno, m.getFonteTreino());
                return m;
            }
            ClassPathResource res = new ClassPathResource("ml/modelo.json");
            if (!res.exists()) {
                log.warn("ml/modelo.json nao encontrado: usando baseline (espera futura = espera atual).");
                return null;
            }
            try (InputStream in = res.getInputStream()) {
                ModeloEspera m = json.readValue(in, ModeloEspera.class);
                log.info("Modelo de ML carregado do classpath (treino: {}).", m.getFonteTreino());
                return m;
            }
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao carregar o modelo de ML: {}. Usando baseline.", e.getMessage());
            return null;
        }
    }

    public ModeloEspera modelo() {
        return modelo;
    }

    @Transactional(readOnly = true)
    public IaView visao() {
        int horizonte = modelo != null ? modelo.getHorizonteMin() : 15;
        int limiar = modelo != null ? modelo.getLimiarLonga() : 20;
        LocalDateTime ultimo = leituras.ultimoInstante();
        if (ultimo == null) {
            return new IaView(meta(null, horizonte, limiar), List.of(), Map.of(), List.of());
        }

        List<LeituraEspera> atuais = leituras.findByTs(ultimo);
        double mediaGeral = atuais.stream().mapToInt(LeituraEspera::getMinutos).average().orElse(0);
        Map<String, Double> mediaPorUnidade = atuais.stream().collect(Collectors.groupingBy(
                l -> ClassificadorLocal.unidade(l.getLocal()), Collectors.averagingInt(LeituraEspera::getMinutos)));
        long nAlta = atuais.stream().filter(l -> l.getMinutos() >= limiar).count();

        // leitura de ~horizonte minutos atras do mesmo local (a mais proxima, dentro da tolerancia)
        LocalDateTime alvo = ultimo.minusMinutes(horizonte);
        List<LeituraEspera> janela = leituras.findByTsBetween(alvo.minusMinutes(TOLERANCIA_MIN), alvo.plusMinutes(TOLERANCIA_MIN));
        Map<String, LeituraEspera> passadas = new HashMap<>();
        for (LeituraEspera l : janela) {
            passadas.merge(l.getLocal(), l, (a, b) -> distancia(a, alvo) <= distancia(b, alvo) ? a : b);
        }

        double hora = ultimo.getHour() + ultimo.getMinute() / 60.0;
        List<PrevisaoLocal> previsoes = new ArrayList<>();
        for (LeituraEspera l : atuais) {
            String unidade = ClassificadorLocal.unidade(l.getLocal());
            String setor = ClassificadorLocal.setor(l.getLocal());
            LeituraEspera passada = passadas.get(l.getLocal());
            int atual = l.getMinutos();
            Integer esperaPassada = passada == null ? null : passada.getMinutos();
            Integer tendencia = esperaPassada == null ? null : atual - esperaPassada;

            int previsto = atual;
            Double prob = null;
            String origem = "baseline";
            if (modelo != null) {
                double passadaUsada = esperaPassada == null ? atual : esperaPassada; // sem historico: assume estavel
                Map<String, Double> x = new HashMap<>();
                x.put("espera_atual", (double) atual);
                x.put("espera_passada", passadaUsada);
                x.put("tendencia", atual - passadaUsada);
                x.put("media_geral", mediaGeral);
                x.put("media_unidade", mediaPorUnidade.get(unidade));
                x.put("n_alta", (double) nAlta);
                x.put("hora_sen", Math.sin(2 * Math.PI * hora / 24));
                x.put("hora_cos", Math.cos(2 * Math.PI * hora / 24));
                x.put("fim_de_semana", ultimo.getDayOfWeek().getValue() >= 6 ? 1.0 : 0.0);
                x.put("sintomas", (double) ClassificadorLocal.sintomas(l.getLocal()));
                ModeloEspera.Previsao p = modelo.prever(x, unidade, setor);
                previsto = (int) Math.round(p.esperaFuturaMin());
                prob = Math.round(p.probLonga() * 100) / 100.0;
                origem = esperaPassada == null ? "modelo-sem-tendencia" : "modelo";
            }
            String nivel = previsto <= 10 ? "VERDE" : previsto < limiar ? "AMARELO" : "VERMELHO";
            previsoes.add(new PrevisaoLocal(l.getLocal(), unidade, setor, atual, esperaPassada, tendencia, previsto, prob, nivel, origem));
        }
        previsoes.sort(Comparator.comparing(PrevisaoLocal::unidade).thenComparing(PrevisaoLocal::setor).thenComparing(PrevisaoLocal::local));

        Comparator<PrevisaoLocal> melhor = Comparator.comparingInt(PrevisaoLocal::previstoMin)
                .thenComparingDouble(p -> p.probLonga() == null ? 0 : p.probLonga());
        Map<String, List<PrevisaoLocal>> recomendacoes = new LinkedHashMap<>();
        for (String setor : SETORES_RECOMENDADOS) {
            List<PrevisaoLocal> top = previsoes.stream().filter(p -> p.setor().equals(setor)).sorted(melhor).limit(3).toList();
            if (!top.isEmpty()) recomendacoes.put(setor, top);
        }
        List<PrevisaoLocal> alertas = previsoes.stream()
                .filter(p -> p.probLonga() != null ? p.probLonga() >= 0.5 : p.esperaAtualMin() >= limiar)
                .sorted(Comparator.comparingDouble((PrevisaoLocal p) -> p.probLonga() == null ? p.esperaAtualMin() : p.probLonga()).reversed())
                .limit(8).toList();

        return new IaView(meta(ultimo, horizonte, limiar), previsoes, recomendacoes, alertas);
    }

    /** CSV no mesmo formato que o notebook le (secao 2): local,tempo_espera,ts,coletado_em,fonte */
    @Transactional(readOnly = true)
    public String csv() {
        StringBuilder sb = new StringBuilder("local,tempo_espera,ts,coletado_em,fonte\n");
        for (LeituraEspera l : leituras.findAllByOrderByTsAscLocalAsc()) {
            sb.append('"').append(l.getLocal().replace("\"", "\"\"")).append("\",")
              .append(l.getMinutos()).append(',').append(l.getTs()).append(',').append(l.getColetadoEm())
              .append(",Prefeitura de Barueri - Tempo de espera PS\n");
        }
        return sb.toString();
    }

    private MetaIa meta(LocalDateTime ultimo, int horizonte, int limiar) {
        String aviso;
        if (modelo == null) {
            aviso = "Modelo não carregado: mostrando a espera atual como previsão (baseline).";
        } else if (modelo.treinadoComDadosSimulados()) {
            aviso = "Modelo de demonstração treinado com dados SIMULADOS. Treine o notebook com dados reais e troque o ml/modelo.json.";
        } else {
            aviso = null;
        }
        return new MetaIa(modelo != null, modelo == null ? null : modelo.getFonteTreino(),
                modelo != null && modelo.treinadoComDadosSimulados(), modelo == null ? null : modelo.getGeradoEm(),
                horizonte, limiar, ultimo, leituras.count(), coletor.isHabilitado(),
                modelo == null ? Map.of() : modelo.getMetricas(), aviso);
    }

    private static long distancia(LeituraEspera l, LocalDateTime alvo) {
        return Math.abs(Duration.between(l.getTs(), alvo).toSeconds());
    }
}
