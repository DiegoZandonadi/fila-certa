package br.com.filacerta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
        this.nome = nome;
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
}
