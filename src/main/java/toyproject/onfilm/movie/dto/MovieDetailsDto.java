package toyproject.onfilm.movie.dto;

import lombok.Getter;

@Getter
public class MovieDetailsDto {
    //=== Movie ===//
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;

    //=== MovieTrailer ===//
    private String trailerUrl;
    private String thumbnailUrl;

    //=== MovieActor ===//
    private String name;
    private int age;
    private String sns;
    private String actorRole;

    public MovieDetailsDto(Long id, String title, int runtime,
                           String ageRating, String trailerUrl,
                           String thumbnailUrl, String name,
                           int age, String sns, String actorRole) {
        this.id = id;
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.trailerUrl = trailerUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.name = name;
        this.age = age;
        this.sns = sns;
        this.actorRole = actorRole;
    }
}
