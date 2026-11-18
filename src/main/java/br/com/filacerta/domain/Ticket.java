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
        if (estabelecimento == null) throw new RegraDeDominioException("O ticket precisa de um estabelecimento.");
        if (criadoEm == null) throw new RegraDeDominioException("O ticket precisa de uma data de criacao.");
        if (nomeCliente == null || nomeCliente.isBlank()) throw new RegraDeDominioException("Informe o nome do cliente.");
        if (nomeCliente.trim().length() > 60) throw new RegraDeDominioException("Nome muito longo (maximo de 60 caracteres).");
        this.id = id;
        this.codigo = codigo;
        this.estabelecimento = estabelecimento;
        this.nomeCliente = nomeCliente.trim();
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
    public ModoDeslocamento getModo() { return modo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getChamadoEm() { return chamadoEm; }
    public LocalDateTime getFinalizadoEm() { return finalizadoEm; }
    public Integer getGuiche() { return guiche; }
    public Double getDuracaoPrevistaMin() { return duracaoPrevistaMin; }
    public Double getDistanciaKm() { return distanciaKm; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    // ------------------------------------------------------------------ ciclo de vida (as regras moram aqui)
    //   AGUARDANDO --chamar--> CHAMADO --finalizar--> ATENDIDO
    //        |                    \--marcarAusente--> AUSENTE
    //        \--cancelar--> CANCELADO

    public boolean aguardando() { return status == StatusTicket.AGUARDANDO; }

    public boolean chamado() { return status == StatusTicket.CHAMADO; }

    /** Um guiche chama este cliente. Exige que ele esteja aguardando e que o guiche exista no estabelecimento. */
    public void chamar(int numeroGuiche, LocalDateTime agora) {
        exigir(StatusTicket.AGUARDANDO, "Só é possível chamar quem está aguardando.");
        if (numeroGuiche < 1 || numeroGuiche > estabelecimento.getGuiches()) {
            throw new RegraDeDominioException("Guichê inexistente neste estabelecimento: " + numeroGuiche);
        }
        this.status = StatusTicket.CHAMADO;
        this.guiche = numeroGuiche;
        this.chamadoEm = agora;
    }

    public void finalizar(LocalDateTime agora) {
        exigir(StatusTicket.CHAMADO, "Só é possível finalizar um atendimento em andamento.");
        this.status = StatusTicket.ATENDIDO;
        this.finalizadoEm = agora;
    }

    public void marcarAusente(LocalDateTime agora) {
        exigir(StatusTicket.CHAMADO, "Só é possível marcar ausência de quem foi chamado.");
        this.status = StatusTicket.AUSENTE;
        this.finalizadoEm = agora;
    }

    /** O cliente desiste: so e possivel enquanto aguarda (depois de chamado, o guiche ja esta ocupado). */
    public void cancelar(LocalDateTime agora) {
        exigir(StatusTicket.AGUARDANDO, "Só é possível sair de uma fila enquanto você está aguardando.");
        this.status = StatusTicket.CANCELADO;
        this.finalizadoEm = agora;
    }

    /** Duracao sorteada pelo simulador; quando preenchida, o atendimento termina sozinho. */
    public void definirDuracaoPrevistaMin(double minutos) {
        if (minutos <= 0) throw new RegraDeDominioException("A duracao prevista deve ser positiva.");
        this.duracaoPrevistaMin = minutos;
    }

    /** Carga de historico: um atendimento que ja aconteceu por inteiro (usado pela base inicial). */
    public void registrarAtendimentoHistorico(int numeroGuiche, LocalDateTime chamadoEm, LocalDateTime finalizadoEm) {
        if (chamadoEm.isBefore(criadoEm) || finalizadoEm.isBefore(chamadoEm)) {
            throw new RegraDeDominioException("Datas do atendimento fora de ordem (criado <= chamado <= finalizado).");
        }
        exigir(StatusTicket.AGUARDANDO, "O historico so pode ser registrado em um ticket novo.");
        chamar(numeroGuiche, chamadoEm);
        finalizar(finalizadoEm);
    }

    private void exigir(StatusTicket esperado, String mensagem) {
        if (status != esperado) throw new RegraDeDominioException(mensagem);
    }

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
