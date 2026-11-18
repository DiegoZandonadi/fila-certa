package br.com.filacerta.config;

import br.com.filacerta.domain.Categoria;
import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.domain.ModoDeslocamento;
import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import br.com.filacerta.importacao.ImportadorService;
import br.com.filacerta.importacao.UnidadeImportada;
import br.com.filacerta.repository.EstabelecimentoRepository;
import br.com.filacerta.repository.TicketRepository;
import br.com.filacerta.service.SimuladorFila;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gera a base "pre-existente": estabelecimentos reais-ish de Sao Paulo e uma semana de historico de
 * atendimentos (espera, duracao e de onde o cliente veio). E isso que alimenta as metricas e o previsor.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int DIAS_HISTORICO = 4;

    private final EstabelecimentoRepository estabelecimentos;
    private final TicketRepository tickets;
    private final SimuladorFila simulador;
    private final RelogioApp relogio;
    private final ImportadorService importador;
    private final ObjectMapper json;
    private final Random random = new Random(42);

    public DataSeeder(EstabelecimentoRepository estabelecimentos, TicketRepository tickets,
                      SimuladorFila simulador, RelogioApp relogio, ImportadorService importador, ObjectMapper json) {
        this.estabelecimentos = estabelecimentos;
        this.tickets = tickets;
        this.simulador = simulador;
        this.relogio = relogio;
        this.importador = importador;
        this.json = json;
    }

    @Override
    public void run(String... args) {
        if (estabelecimentos.count() > 0) {
            log.info("Base ja populada ({} estabelecimentos): seed ignorado.", estabelecimentos.count());
            return; // banco em arquivo (perfil "arquivo") reaproveita os dados entre execucoes
        }
        List<Estabelecimento> lista = carregarBaseReal();
        if (lista.isEmpty()) {
            log.warn("Base real (data/estabelecimentos.json) nao encontrada: usando estabelecimentos ficticios.");
            lista = criarFicticios();
        }

        List<Ticket> historico = new ArrayList<>();
        LocalDateTime fim = relogio.agora().toLocalDate().atStartOfDay();
        for (Estabelecimento e : lista) {
            for (int d = DIAS_HISTORICO; d >= 1; d--) {
                LocalDateTime dia = fim.minusDays(d);
                for (int hora = 8; hora < 19; hora++) {
                    double util = utilizacao(hora);
                    int chegadas = (int) Math.round(e.getGuiches() * (60.0 / e.getTempoMedioBaseMin()) * util * (0.7 + random.nextDouble() * 0.6));
                    for (int i = 0; i < chegadas; i++) {
                        historico.add(atendimentoPassado(e, dia.plusHours(hora).plusMinutes(random.nextInt(60)), util));
                    }
                }
            }
        }
        tickets.saveAll(historico);
        simulador.semear();
        log.info("Fila Certa pronta: {} estabelecimentos, {} atendimentos de histórico.", lista.size(), historico.size());
    }

    /** Estabelecimentos reais (OpenStreetMap) gravados em src/main/resources/data/estabelecimentos.json. */
    private List<Estabelecimento> carregarBaseReal() {
        ClassPathResource recurso = new ClassPathResource("data/estabelecimentos.json");
        if (!recurso.exists()) return List.of();
        try (InputStream in = recurso.getInputStream()) {
            List<UnidadeImportada> unidades = json.readValue(in, new TypeReference<List<UnidadeImportada>>() { });
            importador.salvar(unidades);
            log.info("{} estabelecimentos reais carregados (dados do OpenStreetMap).", unidades.size());
            return estabelecimentos.findAll();
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao ler a base real: {}", e.getMessage());
            return List.of();
        }
    }

    /** Plano B quando nao ha base real: lugares ficticios de Sao Paulo, so para demonstracao. */
    private List<Estabelecimento> criarFicticios() {
        return estabelecimentos.saveAll(List.of(
                new Estabelecimento("Clínica Vida Plena", Categoria.SAUDE, "Av. Paulista, 1500", "Bela Vista", -23.5640, -46.6527, 4, 9),
                new Estabelecimento("UPA Vila Mariana", Categoria.SAUDE, "Rua Domingos de Morais, 2564", "Vila Mariana", -23.5894, -46.6350, 5, 12),
                new Estabelecimento("Banco Aurora - Faria Lima", Categoria.BANCO, "Av. Brig. Faria Lima, 3477", "Itaim Bibi", -23.5868, -46.6830, 6, 7),
                new Estabelecimento("Banco Aurora - Sé", Categoria.BANCO, "Praça da Sé, 111", "Sé", -23.5505, -46.6340, 4, 8),
                new Estabelecimento("Central do Cidadão", Categoria.GOVERNO, "Rua Barão de Itapetininga, 140", "República", -23.5436, -46.6425, 8, 14),
                new Estabelecimento("Cartório 3º Ofício", Categoria.CARTORIO, "Rua dos Pinheiros, 870", "Pinheiros", -23.5670, -46.7020, 3, 10),
                new Estabelecimento("Cantina da Liberdade", Categoria.RESTAURANTE, "Rua Galvão Bueno, 220", "Liberdade", -23.5586, -46.6348, 3, 6),
                new Estabelecimento("Sabor do Paraíso", Categoria.RESTAURANTE, "Rua Vergueiro, 1100", "Paraíso", -23.5760, -46.6446, 3, 5),
                // outras regiões da cidade (para consultar uma fila longe de onde a pessoa está)
                new Estabelecimento("Clínica Vida Plena - Tatuapé", Categoria.SAUDE, "Rua Tuiuti, 1200", "Tatuapé", -23.5400, -46.5760, 4, 9),
                new Estabelecimento("Banco Aurora - Lapa", Categoria.BANCO, "Rua Doze de Outubro, 300", "Lapa", -23.5258, -46.7016, 4, 8),
                new Estabelecimento("Cartório 7º Ofício - Santana", Categoria.CARTORIO, "Av. Cruzeiro do Sul, 800", "Santana", -23.5016, -46.6247, 3, 10),
                new Estabelecimento("UPA Santo Amaro", Categoria.SAUDE, "Av. Adolfo Pinheiro, 1000", "Santo Amaro", -23.6540, -46.7090, 5, 12),
                new Estabelecimento("Central do Cidadão - Itaquera", Categoria.GOVERNO, "Av. Pires do Rio, 2000", "Itaquera", -23.5480, -46.4560, 6, 14)));
    }

    private Ticket atendimentoPassado(Estabelecimento e, LocalDateTime criado, double util) {
        double servico = e.getTempoMedioBaseMin() * (0.6 + random.nextDouble() * 0.9);
        // quanto mais cheio o horario, maior a espera (cresce rapido perto/acima da capacidade)
        double espera = e.getTempoMedioBaseMin() * Math.pow(util, 3) * (0.4 + random.nextDouble() * 1.6) * 1.6 / Math.max(1, e.getGuiches() / 3.0);
        double km = 0.4 + Math.abs(random.nextGaussian()) * 2.8;
        Ticket t = new Ticket(UUID.randomUUID().toString(), e.getCategoria().getPrefixo() + String.format("%03d", random.nextInt(900) + 100),
                e, "Cliente", random.nextInt(12) == 0, ModoDeslocamento.values()[random.nextInt(3)], criado, null, null, km);
        LocalDateTime chamado = criado.plusSeconds((long) (espera * 60));
        t.setChamadoEm(chamado);
        t.setFinalizadoEm(chamado.plusSeconds((long) (servico * 60)));
        t.setGuiche(1 + random.nextInt(e.getGuiches()));
        t.setStatus(StatusTicket.ATENDIDO);
        return t;
    }

    private static double utilizacao(int hora) {
        if (hora >= 11 && hora <= 13) return 1.15;
        if (hora >= 16 && hora <= 17) return 1.05;
        return 0.7;
    }
}
