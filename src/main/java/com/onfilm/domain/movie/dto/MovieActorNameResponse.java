package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MovieActor;
import lombok.Getter;

import java.util.Objects;

@Getter
public class MovieActorNameResponse {

    private String name;

    public MovieActorNameResponse(MovieActor movieActor) {
        this.name = movieActor.getActor().getName();
    }

    /**
     * 극중 1인 2역인 경우, 출연 배우의 이름은 1개만 나타내도록 중복을 제거
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovieActorNameResponse that = (MovieActorNameResponse) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
