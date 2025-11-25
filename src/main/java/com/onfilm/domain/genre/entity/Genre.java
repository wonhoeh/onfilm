package com.onfilm.domain.genre.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;         // 장르 이름

    private String description;  // 예시) "가족": 전체 관람가 영화 및 가족과 함께 볼만한 영화

    @Column(nullable = false)
    private Boolean isActive = true;  // 사용하지 않는 장르는 delete 대신 deactivate

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public Genre(String name, String description) {
        this.name = name;
        this.description = description;
        this.isActive = true;
    }
}