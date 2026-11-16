package br.com.filacerta.domain;

public enum StatusTicket {
    AGUARDANDO,
    CHAMADO,
    ATENDIDO,
    CANCELADO,
    AUSENTE;

    public boolean ativo() {
        return this == AGUARDANDO || this == CHAMADO;
    }
}
