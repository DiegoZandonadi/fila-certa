package br.com.filacerta.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstabelecimentoTest {

    private static Estabelecimento com(int guiches) {
        return new Estabelecimento("Banco Teste", Categoria.BANCO, "Rua B, 2", "Centro", -23.5, -46.8, guiches, 7);
    }

    private static Ticket chamadoNoGuiche(Estabelecimento e, int guiche) {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 17, 9, 0);
        Ticket t = new Ticket("id-" + guiche, "B00" + guiche, e, "Cliente " + guiche, false, ModoDeslocamento.CARRO, t0, null, null, null);
        t.chamar(guiche, t0.plusMinutes(1));
        return t;
    }

    @Test
    void naoPermiteEstabelecimentoInvalido() {
        assertThatThrownBy(() -> new Estabelecimento(" ", Categoria.BANCO, "x", "y", 0, 0, 2, 5)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> new Estabelecimento("A", null, "x", "y", 0, 0, 2, 5)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> new Estabelecimento("A", Categoria.BANCO, "x", "y", 95, 0, 2, 5)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> new Estabelecimento("A", Categoria.BANCO, "x", "y", 0, 200, 2, 5)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> new Estabelecimento("A", Categoria.BANCO, "x", "y", 0, 0, 0, 5)).isInstanceOf(RegraDeDominioException.class);
        assertThatThrownBy(() -> new Estabelecimento("A", Categoria.BANCO, "x", "y", 0, 0, 2, 0)).isInstanceOf(RegraDeDominioException.class);
    }

    @Test
    void guichesLivresNuncaFicaNegativo() {
        Estabelecimento e = com(3);
        assertThat(e.guichesLivres(0)).isEqualTo(3);
        assertThat(e.guichesLivres(2)).isEqualTo(1);
        assertThat(e.guichesLivres(9)).isZero();
    }

    @Test
    void escolheOMenorGuicheLivre() {
        Estabelecimento e = com(4);
        assertThat(e.proximoGuicheLivre(List.of()).getAsInt()).isEqualTo(1);
        assertThat(e.proximoGuicheLivre(List.of(chamadoNoGuiche(e, 1), chamadoNoGuiche(e, 3))).getAsInt())
                .as("o guiche 2 liberou no meio da fila de guiches").isEqualTo(2);
    }

    @Test
    void semGuicheLivreRetornaVazio() {
        Estabelecimento e = com(2);
        List<Ticket> todos = new ArrayList<>(List.of(chamadoNoGuiche(e, 1), chamadoNoGuiche(e, 2)));
        assertThat(e.proximoGuicheLivre(todos)).isEmpty();
    }
}
