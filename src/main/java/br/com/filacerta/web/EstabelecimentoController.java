package br.com.filacerta.web;

import br.com.filacerta.domain.Categoria;
import br.com.filacerta.domain.ModoDeslocamento;
import br.com.filacerta.service.FilaService;
import br.com.filacerta.web.dto.Dtos.EntrarFilaRequest;
import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import br.com.filacerta.web.dto.Dtos.PainelView;
import br.com.filacerta.web.dto.Dtos.TicketView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estabelecimentos")
public class EstabelecimentoController {

    private final FilaService fila;

    public EstabelecimentoController(FilaService fila) {
        this.fila = fila;
    }

    /** Lista ja ordenada do "melhor" para o "pior" (fila + deslocamento) quando lat/lng sao informados. */
    @GetMapping
    public List<EstabelecimentoResumo> listar(@RequestParam(required = false) Double lat,
                                              @RequestParam(required = false) Double lng,
                                              @RequestParam(required = false) ModoDeslocamento modo,
                                              @RequestParam(required = false) Categoria categoria) {
        return fila.listar(lat, lng, modo, categoria);
    }

    @GetMapping("/{id}")
    public EstabelecimentoResumo detalhar(@PathVariable Long id,
                                          @RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lng,
                                          @RequestParam(required = false) ModoDeslocamento modo) {
        return fila.detalhar(id, lat, lng, modo);
    }

    @PostMapping("/{id}/fila")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView entrarNaFila(@PathVariable Long id, @Valid @RequestBody EntrarFilaRequest req) {
        return fila.entrar(id, req);
    }

    @GetMapping("/{id}/painel")
    public PainelView painel(@PathVariable Long id) {
        return fila.painel(id);
    }

    @PostMapping("/{id}/chamar-proximo")
    public TicketView chamarProximo(@PathVariable Long id) {
        return fila.chamarProximo(id);
    }
}
