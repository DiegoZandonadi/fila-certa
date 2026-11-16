package br.com.filacerta.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** Coleta periodicamente o painel de Barueri e guarda cada leitura (sem duplicar), formando a serie temporal. */
@Component
public class ColetorBarueri {

    private static final Logger log = LoggerFactory.getLogger(ColetorBarueri.class);

    private final BarueriClient cliente;
    private final LeituraEsperaRepository leituras;
    private final boolean habilitado;

    public ColetorBarueri(BarueriClient cliente, LeituraEsperaRepository leituras,
                          @Value("${filacerta.barueri.habilitado:true}") boolean habilitado) {
        this.cliente = cliente;
        this.leituras = leituras;
        this.habilitado = habilitado;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    @Scheduled(fixedDelayString = "${filacerta.barueri.intervalo-ms:300000}", initialDelay = 4000)
    public void agendado() {
        if (!habilitado) return;
        try {
            int novas = coletar();
            log.info("Coleta Barueri: {} leituras novas.", novas);
        } catch (RuntimeException e) {
            log.warn("Coleta Barueri falhou: {}", e.getMessage());
        }
    }

    /** @return quantas leituras novas foram gravadas */
    public synchronized int coletar() {
        List<BarueriClient.Leitura> lidas;
        try {
            lidas = cliente.buscar();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler o painel de Barueri: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coleta interrompida", e);
        }
        LocalDateTime agora = LocalDateTime.now();
        int novas = 0;
        for (BarueriClient.Leitura l : lidas) {
            if (leituras.existsByLocalAndTs(l.local(), l.ts())) continue;
            leituras.save(new LeituraEspera(l.local(), l.minutos(), l.ts(), agora));
            novas++;
        }
        return novas;
    }
}
