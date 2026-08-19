package com.onfilm.domain.genre.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "genre",
        uniqueConstraints = @UniqueConstraint(name = "uk_genre_normalized", columnNames = "normalized"))
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = GenreName.MAX_LENGTH)
    private String name;

    @Column(nullable = false, length = GenreName.MAX_LENGTH)
    private String normalized;

    @Column(nullable = false)
    private boolean isActive = true;

    private Genre(String name) {
        GenreName value = GenreName.from(name);
        this.name = value.displayName();
        this.normalized = value.normalized();
        this.isActive = true;
    }

    public static Genre create(String name) {
        return new Genre(name);
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
