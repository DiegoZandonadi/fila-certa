package br.com.filacerta.domain;

/** Uma regra do dominio foi violada (ex.: chamar um ticket que ja foi cancelado). Vira HTTP 422 na API. */
public class RegraDeDominioException extends RuntimeException {
    public RegraDeDominioException(String mensagem) {
        super(mensagem);
    }
}
