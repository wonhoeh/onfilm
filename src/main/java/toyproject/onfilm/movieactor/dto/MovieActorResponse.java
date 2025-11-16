package toyproject.onfilm.movieactor.dto;

import lombok.Getter;
import toyproject.onfilm.movie.dto.MovieDetailsDto;
import toyproject.onfilm.movieactor.entity.MovieActor;

@Getter
public class MovieActorResponse {

    private String name;
    private int age;
    private String sns;
    private String actorRole;

    public MovieActorResponse(MovieActor movieActor) {
        this.name = movieActor.getActor().getName();
        this.age = movieActor.getActor().getAge();
        this.sns = movieActor.getActor().getSns();
        this.actorRole = movieActor.getActorRole();
    }

    /**
     * DTO로 직접 받을 때 사용
     */
    public MovieActorResponse(MovieDetailsDto movieDetailsDto) {
        this.name = movieDetailsDto.getName();
        this.age = movieDetailsDto.getAge();
        this.sns = movieDetailsDto.getSns();
        this.actorRole = movieDetailsDto.getActorRole();
    }
}
