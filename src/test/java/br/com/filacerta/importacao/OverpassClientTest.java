package br.com.filacerta.importacao;

import br.com.filacerta.domain.Categoria;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverpassClientTest {

    private final OverpassClient cliente = new OverpassClient(new ObjectMapper(), "http://localhost/nao-usado");

    @Test
    void converteElementosDoOpenStreetMapEmUnidades() throws Exception {
        String corpo = """
                {"elements":[
                  {"type":"way","center":{"lat":-23.4878,"lon":-46.8923},
                   "tags":{"name":"Pronto Socorro Engenho Novo","amenity":"hospital","addr:street":"Rua Capitão Francisco César","addr:housenumber":"1338","addr:suburb":"Engenho Novo"}},
                  {"type":"node","lat":-23.5119,"lon":-46.8762,"tags":{"name":"Banco Exemplo","amenity":"bank"}},
                  {"type":"node","lat":-23.5119,"lon":-46.8762,"tags":{"name":"Banco Exemplo","amenity":"bank"}},
                  {"type":"node","lat":-23.50,"lon":-46.87,"tags":{"name":"Consultório Odontológico Fulana","amenity":"clinic"}},
                  {"type":"node","lat":-23.50,"lon":-46.86,"tags":{"amenity":"clinic"}},
                  {"type":"node","lat":-23.51,"lon":-46.87,"tags":{"name":"Cartório do 1º Ofício","office":"notary"}}
                ]}
                """;
        List<UnidadeImportada> unidades = cliente.interpretar(corpo, "Barueri");

        assertThat(unidades).extracting(UnidadeImportada::nome)
                .containsExactlyInAnyOrder("Pronto Socorro Engenho Novo", "Banco Exemplo", "Cartório do 1º Ofício");

        UnidadeImportada ps = unidades.stream().filter(u -> u.nome().startsWith("Pronto")).findFirst().orElseThrow();
        assertThat(ps.categoria()).isEqualTo(Categoria.SAUDE);
        assertThat(ps.endereco()).isEqualTo("Rua Capitão Francisco César 1338");
        assertThat(ps.bairro()).isEqualTo("Engenho Novo");
        assertThat(ps.latitude()).isEqualTo(-23.4878);

        UnidadeImportada banco = unidades.stream().filter(u -> u.nome().startsWith("Banco")).findFirst().orElseThrow();
        assertThat(banco.categoria()).isEqualTo(Categoria.BANCO);
        assertThat(banco.endereco()).isEqualTo("Barueri"); // sem endereco no mapa: usa a cidade
    }

    @Test
    void recusaNomeDeCidadeSuspeito() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> cliente.buscar("Barueri\"];out;//"));
    }
}
