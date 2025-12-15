package com.onfilm.domain.movie.entity;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.genre.entity.Genre;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movie_genre",
        indexes = {
                @Index(name = "idx_movie_genre_movie", columnList = "movie_id"),
                @Index(name = "idx_movie_genre_genre", columnList = "genre_id"),
                @Index(name = "idx_movie_genre_norm", columnList = "normalized_text")
        })
public class MovieGenre {

    public enum Source { USER, SYSTEM }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    // ✅ 사용자가 입력한 원문 (무조건 저장)
    @Column(name = "raw_text", nullable = false, length = 60)
    private String rawText;

    // ✅ 중복 제거/검색용 정규화 텍스트
    @Column(name = "normalized_text", nullable = false, length = 60)
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Builder(access = AccessLevel.PRIVATE)
    private MovieGenre(Movie movie, Genre genre, String rawText, Source source) {
        this.movie = movie;
        this.genre = genre; // null 가능
        this.rawText = rawText.trim();
        this.normalizedText = TextNormalizer.normalizeTag(rawText);
        this.source = (source == null) ? Source.USER : source;
    }

    public static MovieGenre fromRaw(Movie movie, String rawText, Source source) {
        if (movie == null) throw new IllegalArgumentException("movie is required");
        if (rawText == null || rawText.isBlank()) throw new IllegalArgumentException("rawText is required");
        return MovieGenre.builder()
                .movie(movie)
                .rawText(rawText)
                .source(source)
                .build();
    }

    // 나중에 표준 장르 매핑할 때 사용
    public void mapToGenre(Genre genre) {
        this.genre = genre;
    }
}
