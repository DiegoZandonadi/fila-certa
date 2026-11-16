package br.com.filacerta.service.previsao;

import br.com.filacerta.domain.Estabelecimento;

/**
 * Ponto de extensao para o modelo de Machine Learning do grupo.
 * <p>
 * Basta criar outra implementacao (ex.: chamando o modelo treinado via HTTP) e marca-la
 * com {@code @Primary}; o restante da aplicacao nao precisa mudar.
 */
public interface PrevisorTempoEspera {

    String nome();

    Previsao prever(Estabelecimento estabelecimento, int pessoasNaFrente, int guichesLivres);

    /** esperaMin = tempo ate ser chamado; tempoServicoMin = duracao estimada de cada atendimento. */
    record Previsao(double esperaMin, double tempoServicoMin) {
    }
}
