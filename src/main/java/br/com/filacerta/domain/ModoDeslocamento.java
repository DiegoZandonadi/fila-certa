package br.com.filacerta.domain;

/** Velocidade media urbana de cada modo, usada para estimar o tempo de deslocamento ate o local. */
public enum ModoDeslocamento {
    A_PE(4.5),
    TRANSPORTE(17.0),
    CARRO(24.0);

    private final double kmPorHora;

    ModoDeslocamento(double kmPorHora) {
        this.kmPorHora = kmPorHora;
    }

    public double getKmPorHora() {
        return kmPorHora;
    }
}
