package br.com.filacerta.domain;

public enum Categoria {
    SAUDE("Saúde", "S"),
    BANCO("Banco", "B"),
    GOVERNO("Serviços públicos", "G"),
    CARTORIO("Cartório", "C"),
    RESTAURANTE("Restaurante", "R");

    private final String rotulo;
    private final String prefixo;

    Categoria(String rotulo, String prefixo) {
        this.rotulo = rotulo;
        this.prefixo = prefixo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getPrefixo() {
        return prefixo;
    }
}
