package br.com.filacerta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_FC_TICKET")
public class Ticket {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 12)
    private String codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id")
    private Estabelecimento estabelecimento;

    @Column(nullable = false, length = 60)
    private String nomeCliente;

    @Column(nullable = false)
    private boolean prioritario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusTicket status = StatusTicket.AGUARDANDO;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ModoDeslocamento modo;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime chamadoEm;
    private LocalDateTime finalizadoEm;

    private Integer guiche;

    /** Duracao sorteada pelo simulador; quando preenchida, o atendimento termina sozinho. */
    private Double duracaoPrevistaMin;

    /** Localizacao do cliente no momento em que entrou na fila (metrica de localizacao). */
    private Double distanciaKm;
    private Double latitude;
    private Double longitude;

    protected Ticket() {
    }

    public Ticket(String id, String codigo, Estabelecimento estabelecimento, String nomeCliente,
                  boolean prioritario, ModoDeslocamento modo, LocalDateTime criadoEm,
                  Double latitude, Double longitude, Double distanciaKm) {
        this.id = id;
        this.codigo = codigo;
        this.estabelecimento = estabelecimento;
        this.nomeCliente = nomeCliente;
        this.prioritario = prioritario;
        this.modo = modo;
        this.criadoEm = criadoEm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanciaKm = distanciaKm;
    }

    public String getId() { return id; }
    public String getCodigo() { return codigo; }
    public Estabelecimento getEstabelecimento() { return estabelecimento; }
    public String getNomeCliente() { return nomeCliente; }
    public boolean isPrioritario() { return prioritario; }
    public StatusTicket getStatus() { return status; }
    public void setStatus(StatusTicket status) { this.status = status; }
    public ModoDeslocamento getModo() { return modo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getChamadoEm() { return chamadoEm; }
    public void setChamadoEm(LocalDateTime chamadoEm) { this.chamadoEm = chamadoEm; }
    public LocalDateTime getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(LocalDateTime finalizadoEm) { this.finalizadoEm = finalizadoEm; }
    public Integer getGuiche() { return guiche; }
    public void setGuiche(Integer guiche) { this.guiche = guiche; }
    public Double getDuracaoPrevistaMin() { return duracaoPrevistaMin; }
    public void setDuracaoPrevistaMin(Double duracaoPrevistaMin) { this.duracaoPrevistaMin = duracaoPrevistaMin; }
    public Double getDistanciaKm() { return distanciaKm; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    /** O cliente se mexeu: recalcula de onde ele esta vindo (usado no "hora de sair"). */
    public void atualizarLocalizacao(Double latitude, Double longitude, Double distanciaKm, ModoDeslocamento modo) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanciaKm = distanciaKm;
        if (modo != null) this.modo = modo;
    }

    /** Minutos entre a entrada na fila e a chamada (null se ainda nao foi chamado). */
    public Double esperaRealMin() {
        if (chamadoEm == null) return null;
        return java.time.Duration.between(criadoEm, chamadoEm).toSeconds() / 60.0;
    }

    /** Minutos de atendimento no guiche (null se nao finalizado como atendido). */
    public Double atendimentoRealMin() {
        if (chamadoEm == null || finalizadoEm == null) return null;
        return java.time.Duration.between(chamadoEm, finalizadoEm).toSeconds() / 60.0;
    }
}
