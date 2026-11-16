package br.com.filacerta.ml;

import br.com.filacerta.ml.IaDtos.IaView;
import br.com.filacerta.web.exception.RegraNegocioException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ml")
public class IaController {

    private final PrevisaoService previsao;
    private final ColetorBarueri coletor;

    public IaController(PrevisaoService previsao, ColetorBarueri coletor) {
        this.previsao = previsao;
        this.coletor = coletor;
    }

    /** Previsoes por local/setor, recomendacoes ("onde ir agora") e alertas de sobrecarga. */
    @GetMapping("/previsoes")
    public IaView previsoes() {
        return previsao.visao();
    }

    /** Forca uma coleta agora (alem da coleta automatica). */
    @PostMapping("/coletar")
    public Map<String, Integer> coletar() {
        try {
            return Map.of("novas", coletor.coletar());
        } catch (IllegalStateException e) {
            throw new RegraNegocioException(e.getMessage());
        }
    }

    /** Serie coletada, no formato do notebook: serve para treinar com dados reais. */
    @GetMapping(value = "/dados.csv", produces = "text/csv")
    public ResponseEntity<byte[]> csv() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=espera_barueri.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(previsao.csv().getBytes(StandardCharsets.UTF_8));
    }
}
