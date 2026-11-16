package br.com.filacerta.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Garante que o Java reproduz o scikit-learn: mesmas variaveis -> mesma previsao e mesma probabilidade. */
class ModeloEsperaParidadeTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void javaReproduzOsModelosDoNotebook() throws Exception {
        ModeloEspera modelo;
        try (InputStream in = new ClassPathResource("ml/modelo.json").getInputStream()) {
            modelo = json.readValue(in, ModeloEspera.class);
        }
        JsonNode casos;
        try (InputStream in = new ClassPathResource("ml/paridade.json").getInputStream()) {
            casos = json.readTree(in);
        }
        assertThat(casos.size()).isGreaterThan(5);
        for (JsonNode caso : casos) {
            Map<String, Double> num = new HashMap<>();
            caso.get("num").fields().forEachRemaining(e -> num.put(e.getKey(), e.getValue().asDouble()));
            ModeloEspera.Previsao p = modelo.prever(num, caso.get("unidade").asText(), caso.get("setor").asText());
            assertThat(p.esperaFuturaMin()).isCloseTo(Math.max(0, caso.get("reg").asDouble()), within(1e-6));
            assertThat(p.probLonga()).isCloseTo(caso.get("prob").asDouble(), within(1e-6));
        }
    }
}
