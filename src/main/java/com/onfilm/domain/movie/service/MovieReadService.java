package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MovieGenreRepository;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.repository.TrailerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieReadService {

    private final MoviePersonRepository moviePersonRepository;
    private final MovieGenreRepository movieGenreRepository;
    private final TrailerRepository trailerRepository;
    private final PersonRepository personRepository;

    public List<MovieCardResponse> getFilmographyByPublicId(String publicId) {
        // 0) publicId -> Person 찾기
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        // 1) mp + movie 조회 (personId 사용)
        List<MoviePerson> moviePeople = moviePersonRepository.findFilmographyByPersonId(person.getId());
        if (moviePeople.isEmpty()) return List.of();

        // movieId 목록(중복 제거)
        List<Long> movieIds = moviePeople.stream()
                .map(mp -> mp.getMovie().getId())
                .distinct()
                .toList();

        // 2) genres IN 조회 -> movieId별로 그룹핑
        Map<Long, String> genreTextByMovieId =
                movieGenreRepository.findAllByMovieIds(movieIds).stream()
                        .collect(Collectors.groupingBy(
                                mg -> mg.getMovie().getId(),
                                Collectors.mapping(
                                        MovieGenre::getNormalizedText,
                                        Collectors.toList()
                                )
                        ))
                        .entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stream()
                                        .filter(s -> s != null && !s.isBlank())
                                        .distinct()
                                        .collect(Collectors.joining(" / "))
                        ));

        // 2) trailers IN 조회 -> movieId별 대표 1개 선정
        Map<Long, String> trailerUrlByMovieId =
                trailerRepository.findAllByMovieIds(movieIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getMovie().getId()))
                        .entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> pickRepresentativeTrailerUrl(e.getValue())
                        ));

        // 3) DTO 조립 (여기서 genres/trailers 접근 안 함!)
        return moviePeople.stream()
                .map(mp -> {
                    Movie m = mp.getMovie();
                    Long mid = m.getId();

                    return new MovieCardResponse(
                            m.getTitle(),
                            genreTextByMovieId.getOrDefault(mid, ""),
                            m.getRuntime(),
                            m.getReleaseYear(),
                            m.getAgeRating(),
                            m.getMovieUrl(),
                            m.getThumbnailUrl(),
                            trailerUrlByMovieId.getOrDefault(mid, ""),
                            mp.getRole(),
                            mp.getCastType(),
                            mp.getCharacterName()
                    );
                })
                .toList();
    }

    private String pickRepresentativeTrailerUrl(List<Trailer> trailers) {
        if (trailers == null || trailers.isEmpty()) return "";

        // ✅ 정책 예시: MAIN 우선 -> 없으면 첫번째
        // (Trailer에 type 필드가 있다면)
        // return trailers.stream()
        //         .sorted(Comparator.comparing(t -> t.getType() == MovieTrailerType.MAIN ? 0 : 1))
        //         .map(Trailer::getUrl)
        //         .filter(u -> u != null && !u.isBlank())
        //         .findFirst()
        //         .orElse("");

        return trailers.stream()
                .map(Trailer::getUrl)
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElse("");
    }
}
