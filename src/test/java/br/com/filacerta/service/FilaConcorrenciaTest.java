package br.com.filacerta.service;

import br.com.filacerta.web.dto.Dtos.EntrarFilaRequest;
import br.com.filacerta.web.dto.Dtos.EstabelecimentoResumo;
import br.com.filacerta.web.dto.Dtos.TicketView;
import br.com.filacerta.web.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressao do "botao que trava": muitas pessoas entrando, sendo chamadas e finalizando ao mesmo tempo
 * (com o simulador rodando) precisam terminar sem deadlock e dentro do prazo.
 */
@SpringBootTest(properties = {
        "filacerta.barueri.habilitado=false",
        "filacerta.simulacao.habilitada=true",
        "filacerta.simulacao.intervalo-ms=300",
        "filacerta.seed.dias-historico=0",
        "filacerta.relogio.fator=60"})
class FilaConcorrenciaTest {

    @Autowired
    private FilaService fila;

    @Test
    void entradasEChamadasSimultaneasNaoTravam() throws Exception {
        List<EstabelecimentoResumo> unidades = fila.listar(null, null, null, null);
        assertThat(unidades).isNotEmpty();

        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<?>> tarefas = new ArrayList<>();
        for (int t = 0; t < 12; t++) {
            final int n = t;
            tarefas.add(pool.submit(() -> {
                for (int i = 0; i < 40; i++) {
                    long id = unidades.get((n + i) % unidades.size()).id();
                    try {
                        TicketView v = fila.entrar(id, new EntrarFilaRequest("Cliente " + n, i % 3 == 0, -23.51, -46.87, null, null));
                        if (i % 4 == 0) fila.chamarProximo(id);
                        if (i % 5 == 0) fila.consultar(v.id());
                    } catch (RegraNegocioException regra) {
                        // fila vazia / guiches ocupados: resposta de negocio normal, nao e falha
                    }
                }
            }));
        }
        pool.shutdown();
        for (Future<?> f : tarefas) f.get(60, TimeUnit.SECONDS); // estourar o prazo = travamento
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void reenviarOMesmoPedidoNaoCriaOutroTicket() {
        long id = fila.listar(null, null, null, null).get(0).id();
        String chave = UUID.randomUUID().toString();
        EntrarFilaRequest pedido = new EntrarFilaRequest("Diego", false, -23.51, -46.87, null, chave);

        TicketView primeiro = fila.entrar(id, pedido);
        TicketView segundo = fila.entrar(id, pedido); // "resposta perdida": cliente reenvia

        assertThat(primeiro.id()).isEqualTo(chave);
        assertThat(segundo.id()).isEqualTo(primeiro.id());
        assertThat(segundo.codigo()).isEqualTo(primeiro.codigo());
    }
}
