package br.com.filacerta.bot;

import br.com.filacerta.service.FilaService;
import br.com.filacerta.web.dto.Dtos.BotPergunta;
import br.com.filacerta.web.dto.Dtos.BotResposta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quem responde cada pergunta: dados da fila no app, o resto no bot de suporte, com plano B local. */
class BotServiceTest {

    private BotExterno externo;
    private BotLocal local;
    private BotService servico;

    private static BotPergunta pergunta(String texto) {
        return new BotPergunta(texto, "sessao", null, null, null, null);
    }

    private static BotResposta daApp(String t) { return new BotResposta(t, "assistente-local", List.of()); }
    private static BotResposta doBot(String t, String categoria) { return new BotResposta(t, "bot-externo", List.of(), categoria); }

    @BeforeEach
    void preparar() {
        externo = mock(BotExterno.class);
        when(externo.configurado()).thenReturn(true);
        // BotLocal real (regras de texto) com FilaService falso: so as regras de roteamento importam aqui
        local = new BotLocal(mock(FilaService.class));
        servico = new BotService(externo, local);
    }

    @Test
    void perguntaSobreFilaVaiParaOApp() {
        BotResposta r = servico.responder(pergunta("Quanto tempo falta para a minha vez?"));
        assertThat(r.origem()).isEqualTo("assistente-local");
        verify(externo, never()).responder(any());
    }

    @Test
    void pedidoDeRecomendacaoVaiParaOAgenteComCategoriaPropria() {
        when(externo.responder(any())).thenReturn(doBot(
                "O sistema sugeriu a unidade com base nos dados recebidos.", "system_recommendation"));

        BotResposta r = servico.responder(pergunta("Por que esta unidade foi recomendada?"));

        assertThat(r.origem()).isEqualTo("bot-externo");
        assertThat(r.categoria()).isEqualTo("system_recommendation");
        verify(externo).responder(any());
    }

    @Test
    void emergenciaVaiParaOBotDeSuporte() {
        when(externo.responder(any())).thenReturn(doBot("Busque atendimento de urgencia. SAMU 192.", "emergency"));
        BotResposta r = servico.responder(pergunta("Estou com dor no peito, quanto tempo de espera?"));
        assertThat(r.origem()).isEqualTo("bot-externo");
        assertThat(r.categoria()).isEqualTo("emergency");
    }

    @Test
    void servicoPublicoEFuncaoDoAppVaoParaOBot() {
        when(externo.responder(any())).thenReturn(doBot("Abra a aba Carteirinha.", "app_feature"));
        assertThat(servico.responder(pergunta("Como acesso minha carteirinha?")).origem()).isEqualTo("bot-externo");
    }

    @Test
    void botQueNaoEntendeCaiNoAssistenteLocal() {
        when(externo.responder(any())).thenReturn(doBot("Nao consegui identificar.", "not_found"));
        BotResposta r = servico.responder(pergunta("bom dia, tudo bem?"));
        assertThat(r.origem()).isEqualTo("assistente-local");
    }

    @Test
    void botForaDoArNaoDeixaOUsuarioSemResposta() {
        when(externo.responder(any())).thenThrow(new IllegalStateException("conexao recusada"));
        BotResposta r = servico.responder(pergunta("Como acesso minha carteirinha?"));
        assertThat(r.origem()).isEqualTo("assistente-local");
        assertThat(r.resposta()).isNotBlank();
    }

    @Test
    void semBotConfiguradoEmergenciaAindaEncaminhaAoSamu() {
        when(externo.configurado()).thenReturn(false);
        BotResposta r = servico.responder(pergunta("tive um desmaio agora"));
        assertThat(r.resposta()).contains("192");
        assertThat(r.resposta()).containsIgnoringCase("urgência");
    }

    @Test
    void semBotConfiguradoPedidoMedicoEncaminhaAoSuporteSemInventar() {
        when(externo.configurado()).thenReturn(false);
        BotResposta r = servico.responder(pergunta("Qual remédio e dose devo tomar?"));
        assertThat(r.categoria()).isEqualTo("not_found");
        assertThat(r.resposta()).containsIgnoringCase("suporte humano");
        assertThat(r.resposta()).doesNotContainIgnoringCase("dipirona");
    }
}
