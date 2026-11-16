package br.com.filacerta.web;

import br.com.filacerta.service.MetricasService;
import br.com.filacerta.web.dto.Dtos.MetricasView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metricas")
public class MetricasController {

    private final MetricasService metricas;

    public MetricasController(MetricasService metricas) {
        this.metricas = metricas;
    }

    @GetMapping
    public MetricasView resumo(@RequestParam(required = false) Long estabelecimentoId) {
        return metricas.resumo(estabelecimentoId);
    }
}
