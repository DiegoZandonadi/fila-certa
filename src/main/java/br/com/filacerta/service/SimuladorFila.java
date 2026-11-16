package br.com.filacerta.service;

import br.com.filacerta.config.RelogioApp;
import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.domain.ModoDeslocamento;
import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import br.com.filacerta.repository.EstabelecimentoRepository;
import br.com.filacerta.repository.TicketRepository;
import br.com.filacerta.web.exception.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Piloto automatico" para demonstracao: clientes chegam, guiches chamam e finalizam.
 * Faz o papel do mundo real enquanto nao ha estabelecimentos conectados de verdade.
 */
@Component
public class SimuladorFila {

    private static final Logger log = LoggerFactory.getLogger(SimuladorFila.class);
    private static final String[] NOMES = {
            "Ana", "Bruno", "Carla", "Diego", "Elisa", "Fábio", "Gabi", "Hugo", "Iara", "João", "Karen", "Leo",
            "Marina", "Nando", "Olívia", "Paulo", "Quésia", "Rafa", "Sônia", "Tiago", "Úrsula", "Vitor", "Wanda", "Yuri"};

    private final EstabelecimentoRepository estabelecimentos;
    private final TicketRepository tickets;
    private final FilaService fila;
    private final RelogioApp relogio;
    private final AtomicBoolean habilitada;
    private final Random random = new Random();
    private LocalDateTime ultimoTick;

    public SimuladorFila(EstabelecimentoRepository estabelecimentos, TicketRepository tickets, FilaService fila,
                         RelogioApp relogio, @Value("${filacerta.simulacao.habilitada:true}") boolean habilitada) {
        this.estabelecimentos = estabelecimentos;
        this.tickets = tickets;
        this.fila = fila;
        this.relogio = relogio;
        this.habilitada = new AtomicBoolean(habilitada);
    }

    public boolean isHabilitada() {
        return habilitada.get();
    }

    public void setHabilitada(boolean valor) {
        habilitada.set(valor);
        if (valor) ultimoTick = null; // evita "rajada" de chegadas pelo tempo parado
    }

    // Sem @Transactional de proposito: cada chamada ao FilaService e sua propria transacao
    // (uma regra de negocio recusada nao pode marcar o tick inteiro como rollback).
    @Scheduled(fixedDelayString = "${filacerta.simulacao.intervalo-ms:2000}", initialDelay = 3000)
    public void tick() {
        if (!habilitada.get()) return;
        try {
            LocalDateTime agora = relogio.agora();
            double deltaMin = ultimoTick == null ? 0 : Math.min(30, java.time.Duration.between(ultimoTick, agora).toMillis() / 60000.0);
            ultimoTick = agora;
            for (Estabelecimento e : estabelecimentos.findAll()) {
                finalizarVencidos(e, agora);
                chegadas(e, agora, deltaMin);
                chamarEnquantoHouverGuiche(e);
            }
        } catch (RuntimeException ex) {
            log.warn("Falha no simulador: {}", ex.getMessage());
        }
    }

    /** Estado inicial "vivo": algumas pessoas ja na fila e guiches ocupados. */
    public void semear() {
        LocalDateTime agora = relogio.agora();
        for (Estabelecimento e : estabelecimentos.findAll()) {
            int n = 2 + random.nextInt(e.getGuiches() * 3 + 4);
            for (int i = 0; i < n; i++) criarCliente(e);
            chamarEnquantoHouverGuiche(e);
        }
        ultimoTick = agora;
    }

    private void finalizarVencidos(Estabelecimento e, LocalDateTime agora) {
        for (Ticket t : tickets.findByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.CHAMADO)) {
            if (t.getDuracaoPrevistaMin() == null) {
                // sorteia a duracao do atendimento (varia de 60% a 160% da media)
                t.setDuracaoPrevistaMin(e.getTempoMedioBaseMin() * (0.6 + random.nextDouble()));
                tickets.save(t);
            }
            LocalDateTime fim = t.getChamadoEm().plusSeconds((long) (t.getDuracaoPrevistaMin() * 60));
            if (!fim.isAfter(agora)) fila.finalizar(t.getId());
        }
    }

    private void chegadas(Estabelecimento e, LocalDateTime agora, double deltaMin) {
        double capacidadePorMin = e.getGuiches() / e.getTempoMedioBaseMin();
        double lambda = capacidadePorMin * utilizacao(agora.getHour()) * (0.7 + random.nextDouble() * 0.6) * deltaMin;
        for (int i = poisson(lambda); i > 0; i--) criarCliente(e);
    }

    private void chamarEnquantoHouverGuiche(Estabelecimento e) {
        while (true) {
            try {
                fila.chamarProximo(e.getId());
            } catch (RegraNegocioException fim) {
                return; // fila vazia ou guiches ocupados
            }
        }
    }

    private void criarCliente(Estabelecimento e) {
        double raioKm = 0.4 + random.nextDouble() * 6.5;
        double ang = random.nextDouble() * Math.PI * 2;
        double lat = e.getLatitude() + (raioKm * Math.sin(ang)) / 111.0;
        double lng = e.getLongitude() + (raioKm * Math.cos(ang)) / (111.0 * Math.cos(Math.toRadians(e.getLatitude())));
        ModoDeslocamento modo = ModoDeslocamento.values()[random.nextInt(ModoDeslocamento.values().length)];
        fila.novoTicket(e, NOMES[random.nextInt(NOMES.length)], random.nextInt(10) == 0, modo, lat, lng, raioKm);
    }

    /** Fracao da capacidade que chega em cada hora (picos no almoco e no fim da tarde). */
    static double utilizacao(int hora) {
        if (hora < 8 || hora >= 19) return 0.35;
        if (hora >= 11 && hora <= 13) return 1.15;
        if (hora >= 16 && hora <= 17) return 1.05;
        return 0.75;
    }

    private int poisson(double lambda) {
        if (lambda <= 0) return 0;
        double limite = Math.exp(-lambda), p = 1;
        int k = 0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > limite && k < 20);
        return k - 1;
    }
}
