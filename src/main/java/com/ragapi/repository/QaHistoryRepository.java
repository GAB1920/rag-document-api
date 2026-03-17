package com.ragapi.repository;

import com.ragapi.model.QaHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QaHistoryRepository extends JpaRepository<QaHistory, UUID> {

    Page<QaHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<QaHistory> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
