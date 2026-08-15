package com.docusphere.backend.support.repository;

import com.docusphere.backend.support.entity.SupportTicket;
import com.docusphere.backend.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUserIdOrderByUpdatedAtDesc(Long userId);
    List<SupportTicket> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, TicketStatus status);
    List<SupportTicket> findAllByOrderByUpdatedAtDesc();
    List<SupportTicket> findAllByStatusOrderByUpdatedAtDesc(TicketStatus status);
}
