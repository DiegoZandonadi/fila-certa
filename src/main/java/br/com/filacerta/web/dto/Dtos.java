package br.com.filacerta.web.dto;

import br.com.filacerta.domain.ModoDeslocamento;
import br.com.filacerta.domain.StatusTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Contratos da API (entrada e saida). Tempos sempre em minutos, distancias em km. */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ estabelecimentos

    public record EstabelecimentoResumo(
            Long id,
            String nome,
            String categoria,
            String categoriaRotulo,
            String endereco,
            String bairro,
            double latitude,
            double longitude,
            int guiches,
            int guichesLivres,
            int pessoasNaFila,
            int esperaMin,
            int tempoServicoMin,
            Double distanciaKm,
            Integer deslocamentoMin,
            Integer tempoPresencialMin,
            Integer tempoFilaCertaMin,
            Integer economiaMin,
            String nivel,
            boolean recomendado) {
    }

    // ------------------------------------------------------------------ tickets

    public record EntrarFilaRequest(
            @NotBlank(message = "Informe seu nome") @Size(max = 60, message = "Nome muito longo") String nome,
            boolean prioritario,
            Double latitude,
            Double longitude,
            ModoDeslocamento modo,
            /** UUID gerado pelo cliente. Se o pedido for repetido (ex.: resposta perdida), devolve o mesmo ticket. */
            String chave) {
    }

    public record LocalizacaoRequest(
            @NotNull Double latitude,
            @NotNull Double longitude,
            ModoDeslocamento modo) {
    }

    public record TicketView(
            String id,
            String codigo,
            Long estabelecimentoId,
            String estabelecimentoNome,
            String categoria,
            String nomeCliente,
            boolean prioritario,
            StatusTicket status,
            Integer posicao,
            Integer pessoasNaFrente,
            Integer esperaMin,
            Integer deslocamentoMin,
            Double distanciaKm,
            Integer saiEmMin,
            String aviso,
            String mensagem,
            Integer guiche,
            ModoDeslocamento modo,
            LocalDateTime criadoEm,
            LocalDateTime chamadoEm,
            LocalDateTime finalizadoEm,
            Integer esperouMin,
            Integer economizouMin) {
    }

    // ------------------------------------------------------------------ painel do atendente

    public record GuicheView(int numero, TicketView atual) {
    }

    public record PainelView(
            EstabelecimentoResumo estabelecimento,
            List<GuicheView> guiches,
            List<TicketView> fila,
            int atendidosHoje) {
    }

    // ------------------------------------------------------------------ metricas

    public record MetricaEstabelecimento(
            Long id,
            String nome,
            String categoriaRotulo,
            String bairro,
            double esperaMediaMin,
            double tempoServicoMedioMin,
            double distanciaMediaClientesKm,
            int atendimentos,
            int filaAgora) {
    }

    public record EsperaPorHora(int hora, double esperaMediaMin, int amostras) {
    }

    public record MetricasView(
            int atendimentosTotal,
            double esperaMediaMin,
            double tempoServicoMedioMin,
            double distanciaMediaClientesKm,
            int pessoasEmFilaAgora,
            int ticketsAtivos,
            Integer melhorHorario,
            Integer piorHorario,
            String previsor,
            boolean simulacao,
            double fatorRelogio,
            List<MetricaEstabelecimento> estabelecimentos,
            List<EsperaPorHora> esperaPorHora) {
    }

    // ------------------------------------------------------------------ bot

    public record BotPergunta(
            @NotBlank(message = "Digite uma mensagem") @Size(max = 500) String mensagem,
            String sessaoId,
            String ticketId,
            Double latitude,
            Double longitude,
            ModoDeslocamento modo) {
    }

    /** categoria: emergency, medical_request, app_feature, public_service, system_recommendation ou not_found. */
    public record BotResposta(String resposta, String origem, List<String> sugestoes, String categoria) {
        public BotResposta(String resposta, String origem, List<String> sugestoes) {
            this(resposta, origem, sugestoes, null);
        }
    }

    public record SimulacaoRequest(boolean habilitada) {
    }
}
