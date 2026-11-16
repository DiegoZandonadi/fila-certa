package br.com.filacerta.ml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/** Uma leitura do painel publico da Prefeitura: tempo medio de espera de um local/setor em um instante. */
@Entity
@Table(name = "TB_FC_LEITURA_ESPERA",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nome_local", "ts"}),
        indexes = @Index(columnList = "ts"))
public class LeituraEspera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_local", nullable = false, length = 160)
    private String local;

    /** Minutos de espera informados pela Prefeitura. */
    @Column(nullable = false)
    private int minutos;

    /** Instante informado pela fonte ("Atualizado em"). */
    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(nullable = false)
    private LocalDateTime coletadoEm;

    protected LeituraEspera() {
    }

    public LeituraEspera(String local, int minutos, LocalDateTime ts, LocalDateTime coletadoEm) {
        this.local = local;
        this.minutos = minutos;
        this.ts = ts;
        this.coletadoEm = coletadoEm;
    }

    public Long getId() { return id; }
    public String getLocal() { return local; }
    public int getMinutos() { return minutos; }
    public LocalDateTime getTs() { return ts; }
    public LocalDateTime getColetadoEm() { return coletadoEm; }
}
