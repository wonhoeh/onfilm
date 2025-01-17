package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.genre.dto.GenreDto;
import toyproject.onfilm.genre.entity.Genre;
import toyproject.onfilm.movie.entity.Movie;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class MovieGenreDto {

    private Long id;
    private String title;
    private int runtime;
    private String ageRating;
    private List<GenreDto> genres;

    public MovieGenreDto(Movie movie, List<Genre> genres) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.runtime = movie.getRuntime();
        this.genres = genres.stream()
                .map(genre -> new GenreDto(genre.getId(), genre.getName()))
                .collect(Collectors.toList());
    }

}
