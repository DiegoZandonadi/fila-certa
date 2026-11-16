package br.com.filacerta.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Relogio da aplicacao. Com fator 1 e o horario real; com fator maior o tempo "corre"
 * mais rapido, o que permite demonstrar filas andando sem esperar minutos de verdade.
 */
@Component
public class RelogioApp {

    private final LocalDateTime inicioApp = LocalDateTime.now();
    private final long inicioRealMs = System.currentTimeMillis();
    private final double fator;

    public RelogioApp(@Value("${filacerta.relogio.fator:1}") double fator) {
        this.fator = Math.max(1, fator);
    }

    public LocalDateTime agora() {
        long decorridoMs = System.currentTimeMillis() - inicioRealMs;
        return inicioApp.plus(Duration.ofMillis((long) (decorridoMs * fator)));
    }

    public double fator() {
        return fator;
    }
}
