package br.com.filacerta.bot;

import br.com.filacerta.service.FilaService;
import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotLocalRecommendationTest {

    @Test
    void explicaSomenteOsDadosRecebidosDoSistema() {
        FilaService fila = mock(FilaService.class);
        when(fila.listar(null, null, null, null)).thenReturn(List.of(
                unidade("UBS Central", 1.8, 12, 1, true),
                unidade("UPA Norte", 3.4, 28, 0, false)));
        BotLocal bot = new BotLocal(fila);

        BotResposta resposta = bot.responder(new BotPergunta(
                "Por que recomendou essa unidade?", "sessao", null, null, null, null));

        assertThat(resposta.categoria()).isEqualTo("system_recommendation");
        assertThat(resposta.resposta()).contains("UBS Central", "1.8 km", "12 min", "Saúde", "GUICHE_DISPONIVEL");
        assertThat(resposta.resposta()).containsIgnoringCase("não faço triagem");
        assertThat(resposta.resposta()).doesNotContainIgnoringCase("gravidade baixa");
    }

    @Test
    void semDadosEncaminhaAoSuporteHumano() {
        FilaService fila = mock(FilaService.class);
        when(fila.listar(null, null, null, null)).thenReturn(List.of());
        BotLocal bot = new BotLocal(fila);

        BotResposta resposta = bot.responder(new BotPergunta(
                "Qual unidade está menos cheia?", "sessao", null, null, null, null));

        assertThat(resposta.categoria()).isEqualTo("system_recommendation");
        assertThat(resposta.resposta()).containsIgnoringCase("dados operacionais");
        assertThat(resposta.resposta()).containsIgnoringCase("suporte humano");
    }

    private static EstabelecimentoResumo unidade(String nome, Double distancia, int espera,
                                                   int guichesLivres, boolean recomendada) {
        return new EstabelecimentoResumo(
                1L, nome, "SAUDE", "Saúde", "Rua Teste", "Centro", -23.5, -46.8,
                2, guichesLivres, 3, espera, 8, distancia, 10, 22, 12, 10, "AMARELO", recomendada);
    }
}
