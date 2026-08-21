package com.onfilm.domain.kafka.repository;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaEncodeJobRepository extends JpaRepository<MediaEncodeJob, String> {
    Optional<MediaEncodeJob> findByRequestId(String requestId);
    Optional<MediaEncodeJob> findByIdAndRequestedByUserId(String id, Long requestedByUserId);
    List<MediaEncodeJob> findTop100ByStatusInAndRequestedAtBefore(
            List<MediaEncodeJobStatus> statuses,
            Instant cutoff
    );

    @Modifying
    @Query("delete from MediaEncodeJob j where j.status in :statuses and j.completedAt < :cutoff")
    int deleteTerminalBefore(@Param("statuses") List<MediaEncodeJobStatus> statuses,
                             @Param("cutoff") Instant cutoff);
}
