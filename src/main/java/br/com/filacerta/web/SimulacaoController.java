package br.com.filacerta.web;

import br.com.filacerta.service.SimuladorFila;
import br.com.filacerta.web.dto.Dtos.SimulacaoRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Liga/desliga o "piloto automatico" usado na demonstracao. */
@RestController
@RequestMapping("/api/simulacao")
public class SimulacaoController {

    private final SimuladorFila simulador;

    public SimulacaoController(SimuladorFila simulador) {
        this.simulador = simulador;
    }

    @GetMapping
    public Map<String, Boolean> estado() {
        return Map.of("habilitada", simulador.isHabilitada());
    }

    @PostMapping
    public Map<String, Boolean> alterar(@RequestBody SimulacaoRequest req) {
        simulador.setHabilitada(req.habilitada());
        return estado();
    }
}
