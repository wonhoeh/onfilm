package com.onfilm.domain.kafka.repository;

import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaEncodeOutboxRepository extends JpaRepository<MediaEncodeOutbox, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from MediaEncodeOutbox o
            where (o.status = :pending and o.nextAttemptAt <= :now)
               or (o.status = :publishing and o.leaseUntil <= :now)
            order by o.createdAt
            """)
    List<MediaEncodeOutbox> findClaimable(
            @Param("pending") MediaEncodeOutboxStatus pending,
            @Param("publishing") MediaEncodeOutboxStatus publishing,
            @Param("now") Instant now,
            Pageable pageable
    );

    Optional<MediaEncodeOutbox> findByJobId(String jobId);

    @Modifying
    @Query("delete from MediaEncodeOutbox o where o.status = :status and o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("status") MediaEncodeOutboxStatus status, @Param("cutoff") Instant cutoff);
}
