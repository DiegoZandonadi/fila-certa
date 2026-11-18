package br.com.filacerta.importacao;

import br.com.filacerta.domain.Categoria;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Estabelecimento real lido de uma fonte aberta (OpenStreetMap), antes de virar {@code Estabelecimento}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnidadeImportada(
        String nome,
        Categoria categoria,
        String tipo,
        String endereco,
        String bairro,
        double latitude,
        double longitude) {
}
