package br.com.filacerta.ml;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BarueriClientTest {

    @Test
    void converteTextoDeTempoEmMinutos() {
        assertThat(BarueriClient.parseMinutos("2 minutos")).isEqualTo(2);
        assertThat(BarueriClient.parseMinutos("20 minutos")).isEqualTo(20);
        assertThat(BarueriClient.parseMinutos("1 h e 36 min")).isEqualTo(96); // o notebook original lia 1
        assertThat(BarueriClient.parseMinutos("2 h")).isEqualTo(120);
        assertThat(BarueriClient.parseMinutos("Sem espera")).isEqualTo(0);
        assertThat(BarueriClient.parseMinutos("indisponivel")).isNull();
    }

    @Test
    void leTabelaDaPrefeitura() {
        String html = """
                <table>
                  <tr><th>Local</th><th>Tempo médio de espera</th><th>Atualizado em</th></tr>
                  <tr><td>PRONTO ATENDIMENTO - ENGENHO NOVO- ORTOPEDIA</td><td>1 h e 36 min</td><td>16/11/2026 20:05:00</td></tr>
                  <tr><td>PRONTO ATENDIMENTO - IMPERIAL  - TRIAGEM</td><td>5 minutos</td><td>16/11/2026 20:05:00</td></tr>
                  <tr><td>linha quebrada</td><td>?</td><td>data invalida</td></tr>
                </table>
                """;
        List<BarueriClient.Leitura> lidas = BarueriClient.interpretar(html);
        assertThat(lidas).hasSize(2);
        assertThat(lidas.get(0).minutos()).isEqualTo(96);
        assertThat(lidas.get(1).local()).isEqualTo("PRONTO ATENDIMENTO - IMPERIAL - TRIAGEM"); // espacos duplicados removidos
        assertThat(lidas.get(1).ts()).isEqualTo(LocalDateTime.of(2026, 11, 16, 20, 5));
    }
}
