package toyproject.onfilm.genre.dto;

import lombok.Getter;

@Getter
public class GenreResponse {
    private String name;

    public GenreResponse(String name) {
        this.name = name;
    }
}
