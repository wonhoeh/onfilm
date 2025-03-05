package toyproject.onfilm.moviedirector.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class MovieDirectorRequest {
    @NotNull
    private Long directorId;
}
