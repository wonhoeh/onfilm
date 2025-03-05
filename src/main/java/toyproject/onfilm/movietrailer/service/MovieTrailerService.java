package toyproject.onfilm.movietrailer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.movietrailer.dto.CreateTrailerRequest;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.movietrailer.repository.MovieTrailerRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieTrailerService {

    private final MovieTrailerRepository movieTrailerRepository;

    @Transactional
    public Long createTrailer(CreateTrailerRequest request) {
        MovieTrailer movieTrailer = movieTrailerRepository.save(MovieTrailer.builder()
                .trailerUrl(request.getTrailUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .build());

        return movieTrailer.getId();
    }
}
