package toyproject.onfilm.moviewriter.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class MovieWriterRequest {
    @NotNull
    private Long writerId;
}
