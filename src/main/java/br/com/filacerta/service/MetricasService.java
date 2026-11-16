package br.com.filacerta.service;

import br.com.filacerta.config.RelogioApp;
import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import br.com.filacerta.repository.EstabelecimentoRepository;
import br.com.filacerta.repository.TicketRepository;
import br.com.filacerta.service.previsao.PrevisorTempoEspera;
import br.com.filacerta.web.dto.Dtos.EsperaPorHora;
import br.com.filacerta.web.dto.Dtos.MetricaEstabelecimento;
import br.com.filacerta.web.dto.Dtos.MetricasView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/** As duas metricas centrais do produto: tempo de espera e localizacao (de onde vem o cliente). */
@Service
public class MetricasService {

    private final EstabelecimentoRepository estabelecimentos;
    private final TicketRepository tickets;
    private final PrevisorTempoEspera previsor;
    private final SimuladorFila simulador;
    private final RelogioApp relogio;

    public MetricasService(EstabelecimentoRepository estabelecimentos, TicketRepository tickets,
                           PrevisorTempoEspera previsor, SimuladorFila simulador, RelogioApp relogio) {
        this.estabelecimentos = estabelecimentos;
        this.tickets = tickets;
        this.previsor = previsor;
        this.simulador = simulador;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public MetricasView resumo(Long estabelecimentoId) {
        List<Ticket> todos = tickets.findAll();
        List<Ticket> atendidos = todos.stream().filter(t -> t.getStatus() == StatusTicket.ATENDIDO).toList();
        Map<Long, List<Ticket>> porEstab = todos.stream().collect(Collectors.groupingBy(t -> t.getEstabelecimento().getId()));

        List<MetricaEstabelecimento> linhas = new ArrayList<>();
        for (Estabelecimento e : estabelecimentos.findAll()) {
            List<Ticket> doLocal = porEstab.getOrDefault(e.getId(), List.of());
            List<Ticket> feitos = doLocal.stream().filter(t -> t.getStatus() == StatusTicket.ATENDIDO).toList();
            int filaAgora = (int) doLocal.stream().filter(t -> t.getStatus() == StatusTicket.AGUARDANDO).count();
            linhas.add(new MetricaEstabelecimento(e.getId(), e.getNome(), e.getCategoria().getRotulo(), e.getBairro(),
                    r(media(feitos, Ticket::esperaRealMin)), r(media(feitos, Ticket::atendimentoRealMin)),
                    r(media(doLocal, Ticket::getDistanciaKm)), feitos.size(), filaAgora));
        }
        linhas.sort(Comparator.comparingDouble(MetricaEstabelecimento::esperaMediaMin));

        // espera media por hora do dia (opcionalmente de um unico estabelecimento)
        List<Ticket> base = estabelecimentoId == null ? atendidos
                : atendidos.stream().filter(t -> t.getEstabelecimento().getId().equals(estabelecimentoId)).toList();
        Map<Integer, List<Ticket>> porHora = base.stream().collect(Collectors.groupingBy(t -> t.getCriadoEm().getHour()));
        List<EsperaPorHora> horas = new ArrayList<>();
        for (int h = 7; h <= 19; h++) {
            List<Ticket> doHorario = porHora.getOrDefault(h, List.of());
            horas.add(new EsperaPorHora(h, r(media(doHorario, Ticket::esperaRealMin)), doHorario.size()));
        }
        List<EsperaPorHora> confiaveis = horas.stream().filter(h -> h.amostras() >= 5 && h.hora() >= 8 && h.hora() <= 18).toList();
        Integer melhor = confiaveis.stream().min(Comparator.comparingDouble(EsperaPorHora::esperaMediaMin)).map(EsperaPorHora::hora).orElse(null);
        Integer pior = confiaveis.stream().max(Comparator.comparingDouble(EsperaPorHora::esperaMediaMin)).map(EsperaPorHora::hora).orElse(null);

        int emFila = (int) todos.stream().filter(t -> t.getStatus() == StatusTicket.AGUARDANDO).count();
        int ativos = (int) todos.stream().filter(t -> t.getStatus().ativo()).count();
        return new MetricasView(atendidos.size(), r(media(atendidos, Ticket::esperaRealMin)),
                r(media(atendidos, Ticket::atendimentoRealMin)), r(media(todos, Ticket::getDistanciaKm)),
                emFila, ativos, melhor, pior, previsor.nome(), simulador.isHabilitada(), relogio.fator(), linhas, horas);
    }

    private static double media(Collection<Ticket> lista, Function<Ticket, Double> campo) {
        OptionalDouble m = lista.stream().map(campo).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return m.orElse(0);
    }

    private static double r(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
