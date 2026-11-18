package br.com.filacerta.importacao;

import br.com.filacerta.web.exception.RegraNegocioException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Importa estabelecimentos reais de uma cidade (OpenStreetMap). */
@RestController
@RequestMapping("/api/importacao")
public class ImportacaoController {

    private final OverpassClient overpass;
    private final ImportadorService importador;

    public ImportacaoController(OverpassClient overpass, ImportadorService importador) {
        this.overpass = overpass;
        this.importador = importador;
    }

    /** Mostra o que seria importado, sem gravar (serve tambem para gerar o arquivo data/estabelecimentos.json). */
    @GetMapping("/osm")
    public List<UnidadeImportada> prever(@RequestParam String cidade) {
        return buscar(cidade);
    }

    /** Importa e grava os estabelecimentos que ainda nao existem. */
    @PostMapping("/osm")
    public Map<String, Object> importar(@RequestParam String cidade) {
        List<UnidadeImportada> unidades = buscar(cidade);
        return Map.of("encontrados", unidades.size(), "novos", importador.salvar(unidades));
    }

    private List<UnidadeImportada> buscar(String cidade) {
        try {
            return overpass.buscar(cidade);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(e.getMessage());
        } catch (IOException e) {
            throw new RegraNegocioException("Nao foi possivel consultar o OpenStreetMap agora: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegraNegocioException("Consulta interrompida.");
        }
    }
}
