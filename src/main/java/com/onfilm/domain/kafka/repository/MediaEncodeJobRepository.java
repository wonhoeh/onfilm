package com.onfilm.domain.kafka.repository;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaEncodeJobRepository extends JpaRepository<MediaEncodeJob, String> {
}
