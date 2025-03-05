package toyproject.onfilm.genre.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.exception.GenreNotFoundException;
import toyproject.onfilm.genre.dto.CreateGenreRequest;
import toyproject.onfilm.genre.dto.GenreResponse;
import toyproject.onfilm.genre.entity.Genre;
import toyproject.onfilm.genre.repository.GenreRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository;

    public String createGenre(CreateGenreRequest request) {
        Genre genre = genreRepository.save(Genre.builder().name(request.getName()).build());
        return genre.getId();
    }

    public GenreResponse findByGenreName(String genreName) {
        Genre genre = genreRepository.findByName(genreName)
                .orElseThrow(() -> new GenreNotFoundException("장르를 찾을 수 없습니다: " + genreName));
        return new GenreResponse(genre.getId());
    }
}
