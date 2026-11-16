package br.com.filacerta.ml;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface LeituraEsperaRepository extends JpaRepository<LeituraEspera, Long> {

    boolean existsByLocalAndTs(String local, LocalDateTime ts);

    @Query("select max(l.ts) from LeituraEspera l")
    LocalDateTime ultimoInstante();

    List<LeituraEspera> findByTs(LocalDateTime ts);

    List<LeituraEspera> findByTsBetween(LocalDateTime de, LocalDateTime ate);

    List<LeituraEspera> findAllByOrderByTsAscLocalAsc();
}
