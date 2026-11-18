package br.com.filacerta.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras do ciclo de vida do ticket, testadas direto na entidade (sem Spring, sem banco). */
class TicketTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 17, 10, 0);

    private Estabelecimento posto;
    private Ticket ticket;

    @BeforeEach
    void preparar() {
        posto = new Estabelecimento("Pronto Socorro Teste", Categoria.SAUDE, "Rua A, 1", "Centro", -23.51, -46.87, 3, 10);
        ticket = novo("Diego");
    }

    private Ticket novo(String nome) {
        return new Ticket("id-1", "S001", posto, nome, false, ModoDeslocamento.A_PE, T0, null, null, null);
    }

    @Test
    void ticketNovoComecaAguardando() {
        assertThat(ticket.aguardando()).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(StatusTicket.AGUARDANDO);
        assertThat(ticket.getGuiche()).isNull();
    }

    @Test
    void fluxoCompletoAguardandoChamadoAtendido() {
        ticket.chamar(2, T0.plusMinutes(12));
        assertThat(ticket.chamado()).isTrue();
        assertThat(ticket.getGuiche()).isEqualTo(2);
        assertThat(ticket.esperaRealMin()).isEqualTo(12.0);

        ticket.finalizar(T0.plusMinutes(20));
        assertThat(ticket.getStatus()).isEqualTo(StatusTicket.ATENDIDO);
        assertThat(ticket.atendimentoRealMin()).isEqualTo(8.0);
    }

    @Test
    void naoChamaQuemJaFoiChamado() {
        ticket.chamar(1, T0.plusMinutes(1));
        assertThatThrownBy(() -> ticket.chamar(2, T0.plusMinutes(2)))
                .isInstanceOf(RegraDeDominioException.class).hasMessageContaining("aguardando");
    }

    @Test
    void naoChamaEmGuicheQueNaoExiste() {
        assertThatThrownBy(() -> ticket.chamar(4, T0)).isInstanceOf(RegraDeDominioException.class).hasMessageContaining("inexistente");
        assertThatThrownBy(() -> ticket.chamar(0, T0)).isInstanceOf(RegraDeDominioException.class);
        assertThat(ticket.aguardando()).as("a tentativa invalida nao altera o ticket").isTrue();
    }

    @Test
    void naoFinalizaNemMarcaAusenteQuemNaoFoiChamado() {
        assertThatThrownBy(() -> ticket.finalizar(T0)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> ticket.marcarAusente(T0)).isInstanceOf(RegraDeDominioException.class);
    }

    @Test
    void clienteSoPodeDesistirEnquantoAguarda() {
        ticket.cancelar(T0.plusMinutes(3));
        assertThat(ticket.getStatus()).isEqualTo(StatusTicket.CANCELADO);

        Ticket chamado = novo("Ana");
        chamado.chamar(1, T0);
        assertThatThrownBy(() -> chamado.cancelar(T0.plusMinutes(1))).isInstanceOf(RegraDeDominioException.class);
    }

    @Test
    void ticketCanceladoNaoPodeSerChamado() {
        ticket.cancelar(T0);
        assertThatThrownBy(() -> ticket.chamar(1, T0.plusMinutes(1))).isInstanceOf(RegraDeDominioException.class);
    }

    @Test
    void ausenciaSoDepoisDeChamado() {
        ticket.chamar(1, T0.plusMinutes(5));
        ticket.marcarAusente(T0.plusMinutes(15));
        assertThat(ticket.getStatus()).isEqualTo(StatusTicket.AUSENTE);
        assertThat(ticket.getStatus().ativo()).isFalse();
    }

    @Test
    void nomeEObrigatorioEEAparado() {
        assertThatThrownBy(() -> novo("   ")).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> novo(null)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> novo("x".repeat(61))).isInstanceOf(RegraDeDominioException.class);
        assertThat(novo("  Maria  ").getNomeCliente()).isEqualTo("Maria");
    }

    @Test
    void historicoValidaAOrdemDasDatas() {
        ticket.registrarAtendimentoHistorico(1, T0.plusMinutes(5), T0.plusMinutes(15));
        assertThat(ticket.getStatus()).isEqualTo(StatusTicket.ATENDIDO);

        assertThatThrownBy(() -> novo("Bia").registrarAtendimentoHistorico(1, T0.minusMinutes(1), T0.plusMinutes(5)))
                .isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> novo("Cris").registrarAtendimentoHistorico(1, T0.plusMinutes(10), T0.plusMinutes(5)))
                .isInstanceOf(RegraDeDominioException.class);
    }
}
