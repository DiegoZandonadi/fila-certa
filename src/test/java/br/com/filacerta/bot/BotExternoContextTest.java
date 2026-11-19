package br.com.filacerta.bot;

import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BotExternoContextTest {

    @Test
    void montaContratoOperacionalCompletoSemRecalcularARecomendacao() {
        EstabelecimentoResumo unidade = new EstabelecimentoResumo(
                7L, "UBS Central", "SAUDE", "Saúde", "Rua Teste", "Centro",
                -23.5, -46.8, 2, 0, 3, 18, 8, 2.4, 12, 30, 18, 12, "AMARELO", true);

        List<Map<String, Object>> dados = BotExterno.dadosOperacionais(List.of(unidade));

        assertThat(dados).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("nome", "UBS Central")
                    .containsEntry("distanciaKm", 2.4)
                    .containsEntry("tempoEstimadoEsperaMin", 18)
                    .containsEntry("servicoDisponivel", "Saúde")
                    .containsEntry("status", "FILA_ATIVA")
                    .containsEntry("recomendadaPeloSistema", true)
                    .containsEntry("dadosSimulados", true);
        });
    }
}
