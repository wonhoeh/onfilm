package com.onfilm.domain.genre.entity;

import com.onfilm.domain.common.TextNormalizer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "genre",
        uniqueConstraints = @UniqueConstraint(name = "uk_genre_normalized", columnNames = "normalized"))
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;              // 장르 이름

    @Column(nullable = false, length = 60)
    private String normalized;

    @Column(nullable = false)
    private boolean isActive = true;  // 사용하지 않는 장르는 delete 대신 deactivate

    @Builder(access = AccessLevel.PRIVATE)
    private Genre(String name, String description) {
        this.name = name.trim();
        this.normalized = TextNormalizer.normalizeTag(name);
        this.isActive = true;
    }

    public static Genre create(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("genre name is required");
        return Genre.builder().name(name).build();
    }
}