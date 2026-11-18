package br.com.filacerta.importacao;

import br.com.filacerta.domain.Categoria;
import br.com.filacerta.domain.Estabelecimento;
import br.com.filacerta.repository.EstabelecimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Transforma unidades importadas em estabelecimentos do dominio (sem duplicar os que ja existem). */
@Service
public class ImportadorService {

    private final EstabelecimentoRepository estabelecimentos;

    public ImportadorService(EstabelecimentoRepository estabelecimentos) {
        this.estabelecimentos = estabelecimentos;
    }

    @Transactional
    public int salvar(List<UnidadeImportada> unidades) {
        Set<String> existentes = new HashSet<>();
        for (Estabelecimento e : estabelecimentos.findAll()) existentes.add(chave(e.getNome(), e.getLatitude(), e.getLongitude()));
        int novos = 0;
        for (UnidadeImportada u : unidades) {
            if (!existentes.add(chave(u.nome(), u.latitude(), u.longitude()))) continue;
            estabelecimentos.save(new Estabelecimento(u.nome(), u.categoria(), u.endereco(), u.bairro(),
                    u.latitude(), u.longitude(), guiches(u), tempoBaseMin(u)));
            novos++;
        }
        return novos;
    }

    /**
     * OpenStreetMap nao informa guiches nem tempo de atendimento: sao parametros ESTIMADOS por tipo de lugar
     * e servem so para a simulacao da fila. Os tempos reais vem do atendimento (previsor) ou do painel da Prefeitura.
     */
    static int guiches(UnidadeImportada u) {
        if (u.categoria() == Categoria.SAUDE) return "hospital".equals(u.tipo()) ? 6 : 3;
        return switch (u.categoria()) {
            case BANCO -> 4;
            case GOVERNO -> 5;
            case CARTORIO -> 2;
            default -> 3;
        };
    }

    static double tempoBaseMin(UnidadeImportada u) {
        if (u.categoria() == Categoria.SAUDE) return "hospital".equals(u.tipo()) ? 14 : 10;
        return switch (u.categoria()) {
            case BANCO -> 7;
            case GOVERNO -> 12;
            case CARTORIO -> 10;
            default -> 8;
        };
    }

    private static String chave(String nome, double lat, double lng) {
        return nome.toLowerCase() + "|" + Math.round(lat * 1000) + "|" + Math.round(lng * 1000);
    }
}
