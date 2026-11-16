package br.com.filacerta.ml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Mesmos resultados das funcoes extrair_unidade / extrair_setor do notebook. */
class ClassificadorLocalTest {

    @Test
    void classificaOsLocaisDaPrefeitura() {
        String l1 = "PRONTO ATENDIMENTO - ENGENHO NOVO- CLINICO GERAL";
        assertThat(ClassificadorLocal.unidade(l1)).isEqualTo("ENGENHO NOVO");
        assertThat(ClassificadorLocal.setor(l1)).isEqualTo("CLINICO");

        String l2 = "PRONTO ATENDIMENTO ADULTO - PAA- CLINICO - SINTOMAS RESPIRATORIOS";
        assertThat(ClassificadorLocal.unidade(l2)).isEqualTo("PAA");
        assertThat(ClassificadorLocal.sintomas(l2)).isEqualTo(1);

        String l3 = "PRONTO ATENDIMENTO CAMARGO- EMERGÊNCIA";
        assertThat(ClassificadorLocal.unidade(l3)).isEqualTo("CAMARGO");
        assertThat(ClassificadorLocal.setor(l3)).isEqualTo("EMERGENCIA");

        String l4 = "PRONTO ATENDIMENTO INFANTIL - PAI - ATENDIMENTO";
        assertThat(ClassificadorLocal.unidade(l4)).isEqualTo("PAI");
        assertThat(ClassificadorLocal.setor(l4)).isEqualTo("ATENDIMENTO");
        assertThat(ClassificadorLocal.sintomas(l4)).isZero();
    }
}
