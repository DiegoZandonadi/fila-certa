package br.com.filacerta.repository;

import br.com.filacerta.domain.StatusTicket;
import br.com.filacerta.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    List<Ticket> findByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusTicket status);

    long countByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusTicket status);

    long countByEstabelecimentoIdAndCriadoEmAfter(Long estabelecimentoId, LocalDateTime desde);

    List<Ticket> findTop30ByEstabelecimentoIdAndStatusOrderByFinalizadoEmDesc(Long estabelecimentoId, StatusTicket status);

    List<Ticket> findByStatusIn(Collection<StatusTicket> status);

    List<Ticket> findByStatus(StatusTicket status);
}
