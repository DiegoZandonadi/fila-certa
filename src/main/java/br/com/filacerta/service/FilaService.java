package br.com.filacerta.service;

import br.com.filacerta.config.RelogioApp;
import br.com.filacerta.domain.Categoria;
import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.domain.ModoDeslocamento;
import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import br.com.filacerta.repository.EstabelecimentoRepository;
import br.com.filacerta.repository.TicketRepository;
import br.com.filacerta.service.previsao.PrevisorTempoEspera;
import br.com.filacerta.service.previsao.PrevisorTempoEspera.Previsao;
import br.com.filacerta.web.dto.Dtos.EntrarFilaRequest;
import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import br.com.filacerta.web.dto.Dtos.GuicheView;
import br.com.filacerta.web.dto.Dtos.LocalizacaoRequest;
import br.com.filacerta.web.dto.Dtos.PainelView;
import br.com.filacerta.web.dto.Dtos.TicketView;
import br.com.filacerta.web.exception.RecursoNaoEncontradoException;
import br.com.filacerta.web.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Regras da fila: entrar, chamar, finalizar e o calculo do "hora de sair"
 * (cruza a metrica de tempo de espera com a de localizacao).
 */
@Service
public class FilaService {

    /** Folga para nao chegar "em cima da hora". */
    private static final int MARGEM_CHEGADA_MIN = 2;
    private static final int PREPARE_SE_MIN = 6;

    private final EstabelecimentoRepository estabelecimentos;
    private final TicketRepository tickets;
    private final PrevisorTempoEspera previsor;
    private final DistanciaService distancia;
    private final RelogioApp relogio;

    public FilaService(EstabelecimentoRepository estabelecimentos, TicketRepository tickets,
                       PrevisorTempoEspera previsor, DistanciaService distancia, RelogioApp relogio) {
        this.estabelecimentos = estabelecimentos;
        this.tickets = tickets;
        this.previsor = previsor;
        this.distancia = distancia;
        this.relogio = relogio;
    }

    // ================================================================== consultas

    @Transactional(readOnly = true)
    public List<EstabelecimentoResumo> listar(Double lat, Double lng, ModoDeslocamento modo, Categoria categoria) {
        List<EstabelecimentoResumo> lista = new ArrayList<>();
        for (Estabelecimento e : estabelecimentos.findAll()) {
            if (categoria != null && e.getCategoria() != categoria) continue;
            lista.add(resumo(e, lat, lng, modo, false));
        }
        // Melhor "fila certa" = menor tempo ate ser atendido considerando fila + deslocamento
        lista.sort(Comparator.comparingInt(r -> r.tempoFilaCertaMin() != null ? r.tempoFilaCertaMin() : r.esperaMin()));
        if (!lista.isEmpty()) lista.set(0, recomendado(lista.get(0)));
        return lista;
    }

    private static EstabelecimentoResumo recomendado(EstabelecimentoResumo r) {
        return new EstabelecimentoResumo(r.id(), r.nome(), r.categoria(), r.categoriaRotulo(), r.endereco(), r.bairro(),
                r.latitude(), r.longitude(), r.guiches(), r.guichesLivres(), r.pessoasNaFila(), r.esperaMin(),
                r.tempoServicoMin(), r.distanciaKm(), r.deslocamentoMin(), r.tempoPresencialMin(),
                r.tempoFilaCertaMin(), r.economiaMin(), r.nivel(), true);
    }

    @Transactional(readOnly = true)
    public EstabelecimentoResumo detalhar(Long id, Double lat, Double lng, ModoDeslocamento modo) {
        return resumo(buscarEstabelecimento(id), lat, lng, modo, false);
    }

    @Transactional(readOnly = true)
    public TicketView consultar(String ticketId) {
        return visao(buscarTicket(ticketId));
    }

    @Transactional(readOnly = true)
    public PainelView painel(Long estabelecimentoId) {
        Estabelecimento e = buscarEstabelecimento(estabelecimentoId);
        List<Ticket> fila = aguardandoOrdenado(e.getId());
        List<Ticket> chamados = tickets.findByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.CHAMADO);
        int livres = Math.max(0, e.getGuiches() - chamados.size());

        List<GuicheView> guiches = new ArrayList<>();
        for (int n = 1; n <= e.getGuiches(); n++) {
            final int numero = n;
            TicketView atual = chamados.stream()
                    .filter(t -> t.getGuiche() != null && t.getGuiche() == numero)
                    .findFirst().map(t -> montar(t, -1, livres)).orElse(null);
            guiches.add(new GuicheView(numero, atual));
        }
        List<TicketView> views = new ArrayList<>();
        for (int i = 0; i < fila.size(); i++) views.add(montar(fila.get(i), i, livres));

        LocalDateTime inicioDia = relogio.agora().toLocalDate().atStartOfDay();
        int atendidosHoje = (int) tickets.findByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.ATENDIDO).stream()
                .filter(t -> t.getFinalizadoEm() != null && t.getFinalizadoEm().isAfter(inicioDia)).count();
        return new PainelView(resumo(e, null, null, null, false), guiches, views, atendidosHoje);
    }

    // ================================================================== comandos do cliente

    @Transactional
    public synchronized TicketView entrar(Long estabelecimentoId, EntrarFilaRequest req) {
        Estabelecimento e = buscarEstabelecimento(estabelecimentoId);
        Double km = null;
        if (req.latitude() != null && req.longitude() != null) {
            km = distancia.km(req.latitude(), req.longitude(), e.getLatitude(), e.getLongitude());
        }
        Ticket t = novoTicket(e, req.nome().trim(), req.prioritario(), req.modo(), req.latitude(), req.longitude(), km);
        return visao(t);
    }

    @Transactional
    public TicketView atualizarLocalizacao(String ticketId, LocalizacaoRequest req) {
        Ticket t = buscarTicket(ticketId);
        Estabelecimento e = t.getEstabelecimento();
        double km = distancia.km(req.latitude(), req.longitude(), e.getLatitude(), e.getLongitude());
        t.atualizarLocalizacao(req.latitude(), req.longitude(), km, req.modo());
        return visao(tickets.save(t));
    }

    @Transactional
    public synchronized TicketView cancelar(String ticketId) {
        Ticket t = buscarTicket(ticketId);
        if (t.getStatus() != StatusTicket.AGUARDANDO) {
            throw new RegraNegocioException("Só é possível sair de uma fila enquanto você está aguardando.");
        }
        t.setStatus(StatusTicket.CANCELADO);
        t.setFinalizadoEm(relogio.agora());
        return visao(tickets.save(t));
    }

    // ================================================================== comandos do atendente

    @Transactional
    public synchronized TicketView chamarProximo(Long estabelecimentoId) {
        Estabelecimento e = buscarEstabelecimento(estabelecimentoId);
        List<Ticket> fila = aguardandoOrdenado(e.getId());
        if (fila.isEmpty()) throw new RegraNegocioException("Ninguém aguardando nesta fila.");

        Set<Integer> ocupados = new HashSet<>();
        for (Ticket c : tickets.findByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.CHAMADO)) {
            if (c.getGuiche() != null) ocupados.add(c.getGuiche());
        }
        Integer livre = null;
        for (int n = 1; n <= e.getGuiches(); n++) {
            if (!ocupados.contains(n)) { livre = n; break; }
        }
        if (livre == null) throw new RegraNegocioException("Todos os guichês estão ocupados. Finalize um atendimento.");

        Ticket t = fila.get(0);
        t.setStatus(StatusTicket.CHAMADO);
        t.setChamadoEm(relogio.agora());
        t.setGuiche(livre);
        return visao(tickets.save(t));
    }

    @Transactional
    public synchronized TicketView finalizar(String ticketId) {
        Ticket t = buscarTicket(ticketId);
        if (t.getStatus() != StatusTicket.CHAMADO) {
            throw new RegraNegocioException("Só é possível finalizar um atendimento em andamento.");
        }
        t.setStatus(StatusTicket.ATENDIDO);
        t.setFinalizadoEm(relogio.agora());
        return visao(tickets.save(t));
    }

    @Transactional
    public synchronized TicketView marcarAusente(String ticketId) {
        Ticket t = buscarTicket(ticketId);
        if (t.getStatus() != StatusTicket.CHAMADO) {
            throw new RegraNegocioException("Só é possível marcar ausência de quem foi chamado.");
        }
        t.setStatus(StatusTicket.AUSENTE);
        t.setFinalizadoEm(relogio.agora());
        return visao(tickets.save(t));
    }

    // ================================================================== usado pelo simulador / seeder

    @Transactional
    public synchronized Ticket novoTicket(Estabelecimento e, String nome, boolean prioritario, ModoDeslocamento modo,
                                          Double lat, Double lng, Double km) {
        LocalDateTime agora = relogio.agora();
        long hoje = tickets.countByEstabelecimentoIdAndCriadoEmAfter(e.getId(), agora.toLocalDate().atStartOfDay());
        String codigo = e.getCategoria().getPrefixo() + String.format("%03d", hoje + 1);
        Ticket t = new Ticket(UUID.randomUUID().toString(), codigo, e, nome, prioritario,
                modo == null ? ModoDeslocamento.TRANSPORTE : modo, agora, lat, lng, km);
        return tickets.save(t);
    }

    // ================================================================== internos

    private EstabelecimentoResumo resumo(Estabelecimento e, Double lat, Double lng, ModoDeslocamento modo, boolean recomendado) {
        int aguardando = (int) tickets.countByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.AGUARDANDO);
        int chamados = (int) tickets.countByEstabelecimentoIdAndStatus(e.getId(), StatusTicket.CHAMADO);
        int livres = Math.max(0, e.getGuiches() - chamados);
        Previsao p = previsor.prever(e, aguardando, livres);
        int espera = (int) Math.ceil(p.esperaMin());

        Double km = null;
        Integer desloc = null, presencial = null, filaCerta = null, economia = null;
        if (lat != null && lng != null) {
            km = arredondar(distancia.km(lat, lng, e.getLatitude(), e.getLongitude()));
            desloc = distancia.minutosDeslocamento(km, modo);
            presencial = desloc + espera;          // chega, entra na fila e espera
            filaCerta = Math.max(desloc, espera);  // entra na fila de casa e chega quando chega a vez
            economia = presencial - filaCerta;
        }
        String nivel = espera <= 10 ? "VERDE" : espera <= 25 ? "AMARELO" : "VERMELHO";
        return new EstabelecimentoResumo(e.getId(), e.getNome(), e.getCategoria().name(), e.getCategoria().getRotulo(),
                e.getEndereco(), e.getBairro(), e.getLatitude(), e.getLongitude(), e.getGuiches(), livres, aguardando,
                espera, (int) Math.round(p.tempoServicoMin()), km, desloc, presencial, filaCerta, economia, nivel, recomendado);
    }

    private TicketView visao(Ticket t) {
        if (t.getStatus() == StatusTicket.AGUARDANDO) {
            List<Ticket> fila = aguardandoOrdenado(t.getEstabelecimento().getId());
            int idx = 0;
            for (int i = 0; i < fila.size(); i++) {
                if (fila.get(i).getId().equals(t.getId())) { idx = i; break; }
            }
            int chamados = (int) tickets.countByEstabelecimentoIdAndStatus(t.getEstabelecimento().getId(), StatusTicket.CHAMADO);
            return montar(t, idx, Math.max(0, t.getEstabelecimento().getGuiches() - chamados));
        }
        return montar(t, -1, 0);
    }

    /** idxNaFila = posicao (base 0) entre os que aguardam; -1 se nao estiver aguardando. */
    private TicketView montar(Ticket t, int idxNaFila, int guichesLivres) {
        Estabelecimento e = t.getEstabelecimento();
        Integer posicao = null, naFrente = null, espera = null, desloc = null, saiEm = null;
        String aviso;
        String mensagem;
        Integer esperouMin = null, economizou = null;

        if (t.getDistanciaKm() != null) {
            desloc = distancia.minutosDeslocamento(t.getDistanciaKm(), t.getModo());
        }

        switch (t.getStatus()) {
            case AGUARDANDO -> {
                posicao = idxNaFila + 1;
                naFrente = idxNaFila;
                espera = (int) Math.ceil(previsor.prever(e, idxNaFila, guichesLivres).esperaMin());
                if (desloc != null) saiEm = Math.max(0, espera - desloc - MARGEM_CHEGADA_MIN);
                if (desloc == null) {
                    aviso = "AGUARDE";
                    mensagem = "Você está na fila. Ative a localização para eu te dizer a hora certa de sair.";
                } else if (saiEm == 0 && espera > 0) {
                    aviso = "SAIA_AGORA";
                    mensagem = "Saia agora! Levando " + desloc + " min até lá, você chega na hora da sua vez.";
                } else if (saiEm <= PREPARE_SE_MIN) {
                    aviso = "PREPARE_SE";
                    mensagem = "Prepare-se: saia em cerca de " + saiEm + " min.";
                } else {
                    aviso = "AGUARDE";
                    mensagem = "Aproveite o tempo livre. Eu aviso quando for a hora de sair.";
                }
                if (espera == 0 && desloc != null) {
                    aviso = "SAIA_AGORA";
                    mensagem = "Já pode ser chamado a qualquer momento. Venha agora!";
                }
            }
            case CHAMADO -> {
                aviso = "SUA_VEZ";
                mensagem = "É a sua vez! Dirija-se ao guichê " + t.getGuiche() + ".";
                esperouMin = minutos(t.esperaRealMin());
            }
            case ATENDIDO -> {
                aviso = "CONCLUIDO";
                mensagem = "Atendimento concluído. Obrigado por usar a Fila Certa!";
                esperouMin = minutos(t.esperaRealMin());
                if (t.getDistanciaKm() != null && esperouMin != null) economizou = Math.min(desloc, esperouMin);
            }
            case CANCELADO -> {
                aviso = "CANCELADO";
                mensagem = "Você saiu da fila.";
            }
            default -> {
                aviso = "AUSENTE";
                mensagem = "Você não compareceu quando foi chamado.";
            }
        }
        return new TicketView(t.getId(), t.getCodigo(), e.getId(), e.getNome(), e.getCategoria().name(), t.getNomeCliente(),
                t.isPrioritario(), t.getStatus(), posicao, naFrente, espera, desloc,
                t.getDistanciaKm() == null ? null : arredondar(t.getDistanciaKm()), saiEm, aviso, mensagem, t.getGuiche(),
                t.getModo(), t.getCriadoEm(), t.getChamadoEm(), t.getFinalizadoEm(), esperouMin, economizou);
    }

    private List<Ticket> aguardandoOrdenado(Long estabelecimentoId) {
        List<Ticket> lista = new ArrayList<>(tickets.findByEstabelecimentoIdAndStatus(estabelecimentoId, StatusTicket.AGUARDANDO));
        lista.sort(Comparator.comparing(Ticket::isPrioritario).reversed().thenComparing(Ticket::getCriadoEm));
        return lista;
    }

    private Estabelecimento buscarEstabelecimento(Long id) {
        return estabelecimentos.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + id));
    }

    private Ticket buscarTicket(String id) {
        return tickets.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ticket não encontrado."));
    }

    private static Integer minutos(Double v) {
        return v == null ? null : (int) Math.round(v);
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
