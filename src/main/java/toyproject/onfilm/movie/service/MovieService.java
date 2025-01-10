package toyproject.onfilm.movie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.domain.actor.ActorRepository;
import toyproject.onfilm.domain.movie.MovieRepository;
import toyproject.onfilm.movie.repository.MovieRepository;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;

    /**
     * GET /movies/{movieId}
     *
     * Response:
     * {
     *   "id": 123,
     *   "title": "영화 제목",
     *   "rating": 4.5,
     *   "genre": ["액션", "스릴러"],
     *   "runtime": "120분",
     *   "ageRating": "15세 이상",
     *   "trailerUrl": "https://example.com/trailer.mp4"
     * }
     */
    public List<> findMovies() {

    }
}