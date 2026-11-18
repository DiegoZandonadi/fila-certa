package br.com.filacerta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

@Entity
@Table(name = "TB_FC_ESTABELECIMENTO")
public class Estabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @Column(length = 160)
    private String endereco;

    @Column(length = 80)
    private String bairro;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    /** Quantidade de guiches/atendentes ativos. */
    @Column(nullable = false)
    private int guiches;

    /** Tempo medio de atendimento "de fabrica" (minutos), usado ate haver historico suficiente. */
    @Column(nullable = false)
    private double tempoMedioBaseMin;

    protected Estabelecimento() {
    }

    public Estabelecimento(String nome, Categoria categoria, String endereco, String bairro,
                           double latitude, double longitude, int guiches, double tempoMedioBaseMin) {
        if (nome == null || nome.isBlank()) throw new RegraDeDominioException("O estabelecimento precisa de um nome.");
        if (nome.length() > 120) throw new RegraDeDominioException("Nome do estabelecimento muito longo.");
        if (categoria == null) throw new RegraDeDominioException("O estabelecimento precisa de uma categoria.");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new RegraDeDominioException("Coordenadas invalidas.");
        }
        if (guiches < 1) throw new RegraDeDominioException("O estabelecimento precisa de ao menos um guiche.");
        if (tempoMedioBaseMin <= 0) throw new RegraDeDominioException("O tempo medio de atendimento deve ser positivo.");
        this.nome = nome.trim();
        this.categoria = categoria;
        this.endereco = endereco;
        this.bairro = bairro;
        this.latitude = latitude;
        this.longitude = longitude;
        this.guiches = guiches;
        this.tempoMedioBaseMin = tempoMedioBaseMin;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public Categoria getCategoria() { return categoria; }
    public String getEndereco() { return endereco; }
    public String getBairro() { return bairro; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getGuiches() { return guiches; }
    public double getTempoMedioBaseMin() { return tempoMedioBaseMin; }

    /** Guiches sem ninguem sendo atendido, dado quantos estao ocupados. */
    public int guichesLivres(int ocupados) {
        return Math.max(0, guiches - ocupados);
    }

    /** Menor numero de guiche que nao aparece entre os tickets em atendimento (vazio se todos estao ocupados). */
    public OptionalInt proximoGuicheLivre(Collection<Ticket> emAtendimento) {
        Set<Integer> usados = new HashSet<>();
        for (Ticket t : emAtendimento) {
            if (t.getGuiche() != null) usados.add(t.getGuiche());
        }
        for (int n = 1; n <= guiches; n++) {
            if (!usados.contains(n)) return OptionalInt.of(n);
        }
        return OptionalInt.empty();
    }
}
