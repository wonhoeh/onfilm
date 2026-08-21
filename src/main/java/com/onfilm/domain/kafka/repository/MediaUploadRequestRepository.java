package com.onfilm.domain.kafka.repository;

import com.onfilm.domain.kafka.entity.MediaUploadRequest;
import com.onfilm.domain.kafka.entity.MediaUploadRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;

import java.util.Optional;

public interface MediaUploadRequestRepository extends JpaRepository<MediaUploadRequest, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MediaUploadRequest r where r.id = :id")
    Optional<MediaUploadRequest> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("""
            delete from MediaUploadRequest r
            where (r.status <> :completed and r.expiresAt < :now)
               or (r.status = :completed and r.completedAt < :completedCutoff)
            """)
    int deleteExpiredBefore(@Param("completed") MediaUploadRequestStatus completed,
                            @Param("now") Instant now,
                            @Param("completedCutoff") Instant completedCutoff);
}
