package toyproject.onfilm.movieactor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class MovieActorRequest {
    @NotNull
    private Long actorId;
    @NotNull
    private String actorRole;
}
