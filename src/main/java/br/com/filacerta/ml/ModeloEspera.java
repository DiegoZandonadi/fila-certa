package br.com.filacerta.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Os dois modelos treinados no notebook (regressao linear e regressao logistica), exportados em JSON.
 * Como ambos sao lineares, a previsao em Java e so: intercepto + soma(coeficiente * variavel padronizada).
 * Ordem das variaveis = a do ColumnTransformer do notebook: numericas, depois one-hot de unidade, depois de setor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModeloEspera {

    public record Linear(double intercept, List<Double> coef) {
    }

    public record Padronizador(List<Double> mean, List<Double> scale) {
    }

    public record Previsao(double esperaFuturaMin, double probLonga) {
    }

    @JsonProperty("fonte_treino") private String fonteTreino;
    @JsonProperty("gerado_em") private String geradoEm;
    @JsonProperty("horizonte_min") private int horizonteMin;
    @JsonProperty("limiar_longa") private int limiarLonga;
    @JsonProperty("num") private List<String> num;
    @JsonProperty("scaler") private Padronizador scaler;
    @JsonProperty("cat") private Map<String, List<String>> cat;
    @JsonProperty("reg") private Linear reg;
    @JsonProperty("clf") private Linear clf;
    @JsonProperty("metricas") private Map<String, Double> metricas;

    public String getFonteTreino() { return fonteTreino; }
    public String getGeradoEm() { return geradoEm; }
    public int getHorizonteMin() { return horizonteMin; }
    public int getLimiarLonga() { return limiarLonga; }
    public Map<String, Double> getMetricas() { return metricas; }
    public boolean treinadoComDadosSimulados() { return fonteTreino == null || fonteTreino.toUpperCase().contains("SIMUL"); }

    /** Variaveis na ordem exata do treino: numericas (nome -> valor) e categoricas (unidade, setor). */
    public Previsao prever(Map<String, Double> numericas, String unidade, String setor) {
        double[] x = vetor(numericas, unidade, setor);
        double espera = Math.max(0, produto(reg, x));
        double z = produto(clf, x);
        return new Previsao(espera, 1.0 / (1.0 + Math.exp(-z)));
    }

    double[] vetor(Map<String, Double> numericas, String unidade, String setor) {
        List<String> unidades = cat.get("unidade");
        List<String> setores = cat.get("setor");
        double[] x = new double[num.size() + unidades.size() + setores.size()];
        int i = 0;
        for (int k = 0; k < num.size(); k++) {
            double v = numericas.getOrDefault(num.get(k), 0.0);
            x[i++] = (v - scaler.mean().get(k)) / scaler.scale().get(k);
        }
        for (String u : unidades) x[i++] = u.equals(unidade) ? 1.0 : 0.0; // desconhecida = tudo zero (handle_unknown=ignore)
        for (String s : setores) x[i++] = s.equals(setor) ? 1.0 : 0.0;
        return x;
    }

    private static double produto(Linear m, double[] x) {
        if (m.coef().size() != x.length) {
            throw new IllegalStateException("Modelo incompativel: " + m.coef().size() + " coeficientes para " + x.length + " variaveis");
        }
        double soma = m.intercept();
        for (int i = 0; i < x.length; i++) soma += m.coef().get(i) * x[i];
        return soma;
    }
}
