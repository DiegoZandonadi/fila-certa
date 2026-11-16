package br.com.filacerta.web;

import br.com.filacerta.service.FilaService;
import br.com.filacerta.web.dto.Dtos.LocalizacaoRequest;
import br.com.filacerta.web.dto.Dtos.TicketView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final FilaService fila;

    public TicketController(FilaService fila) {
        this.fila = fila;
    }

    @GetMapping("/{id}")
    public TicketView consultar(@PathVariable String id) {
        return fila.consultar(id);
    }

    @PutMapping("/{id}/localizacao")
    public TicketView localizacao(@PathVariable String id, @Valid @RequestBody LocalizacaoRequest req) {
        return fila.atualizarLocalizacao(id, req);
    }

    @PostMapping("/{id}/cancelar")
    public TicketView cancelar(@PathVariable String id) {
        return fila.cancelar(id);
    }

    @PostMapping("/{id}/finalizar")
    public TicketView finalizar(@PathVariable String id) {
        return fila.finalizar(id);
    }

    @PostMapping("/{id}/ausente")
    public TicketView ausente(@PathVariable String id) {
        return fila.marcarAusente(id);
    }
}
