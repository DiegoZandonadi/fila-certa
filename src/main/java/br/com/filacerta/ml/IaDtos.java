package br.com.filacerta.ml;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Contratos da API de previsao ("/api/ml"). Tempos em minutos. */
public final class IaDtos {

    private IaDtos() {
    }

    /** origem: "modelo" | "modelo-sem-tendencia" (sem leitura de 15 min atras) | "baseline" (sem modelo carregado). */
    public record PrevisaoLocal(
            String local,
            String unidade,
            String setor,
            int esperaAtualMin,
            Integer esperaPassadaMin,
            Integer tendenciaMin,
            int previstoMin,
            Double probLonga,
            String nivel,
            String origem) {
    }

    public record MetaIa(
            boolean modeloAtivo,
            String fonteTreino,
            boolean treinoSimulado,
            String geradoEm,
            int horizonteMin,
            int limiarLongaMin,
            LocalDateTime ultimaLeitura,
            long leiturasTotal,
            boolean coletaAtiva,
            Map<String, Double> metricas,
            String aviso) {
    }

    public record IaView(
            MetaIa meta,
            List<PrevisaoLocal> previsoes,
            Map<String, List<PrevisaoLocal>> recomendacoes,
            List<PrevisaoLocal> alertas) {
    }
}
