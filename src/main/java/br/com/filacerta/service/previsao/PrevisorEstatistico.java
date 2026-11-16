package br.com.filacerta.service.previsao;

import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import br.com.filacerta.repository.TicketRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Previsao baseada em media movel exponencial (EWMA) dos ultimos atendimentos reais,
 * misturada com o tempo "de fabrica" enquanto o historico e pequeno.
 */
@Component
public class PrevisorEstatistico implements PrevisorTempoEspera {

    private static final double ALFA = 0.25;
    private static final int AMOSTRAS_CONFIAVEIS = 10;

    private final TicketRepository tickets;

    public PrevisorEstatistico(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    public String nome() {
        return "Média móvel exponencial dos últimos atendimentos";
    }

    @Override
    public Previsao prever(Estabelecimento e, int pessoasNaFrente, int guichesLivres) {
        double ts = tempoServicoMin(e);
        double espera;
        if (pessoasNaFrente < guichesLivres) {
            espera = 0; // ha guiche livre para esta pessoa agora
        } else {
            int fila = pessoasNaFrente - guichesLivres;
            // em media, um guiche ocupado termina "no meio" do atendimento atual
            espera = ts * (fila / (double) e.getGuiches() + 0.5);
        }
        return new Previsao(espera, ts);
    }

    private double tempoServicoMin(Estabelecimento e) {
        List<Ticket> recentes = new ArrayList<>(
                tickets.findTop30ByEstabelecimentoIdAndStatusOrderByFinalizadoEmDesc(e.getId(), StatusTicket.ATENDIDO));
        Collections.reverse(recentes); // do mais antigo para o mais novo
        Double ewma = null;
        int n = 0;
        for (Ticket t : recentes) {
            Double d = t.atendimentoRealMin();
            if (d == null || d <= 0) continue;
            ewma = ewma == null ? d : ALFA * d + (1 - ALFA) * ewma;
            n++;
        }
        if (ewma == null) return e.getTempoMedioBaseMin();
        double peso = Math.min(1.0, n / (double) AMOSTRAS_CONFIAVEIS);
        return peso * ewma + (1 - peso) * e.getTempoMedioBaseMin();
    }
}
