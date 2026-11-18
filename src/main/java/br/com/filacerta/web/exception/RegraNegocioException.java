package br.com.filacerta.web.exception;

import br.com.filacerta.domain.RegraDeDominioException;

/** Regra de negocio da aplicacao (ex.: "ninguem aguardando"); as regras das proprias entidades usam a excecao de dominio. */
public class RegraNegocioException extends RegraDeDominioException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
