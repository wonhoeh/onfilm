package com.onfilm.domain.search.controller;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhrasePrefixQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.onfilm.domain.search.document.MovieDocument;
import com.onfilm.domain.search.repository.MovieSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class MovieSearchController {

    private final MovieSearchRepository movieSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @PostMapping("/es/movies")
    public String saveMovie(@RequestBody MovieDocument movie) {
        movieSearchRepository.save(movie);
        return "saved";
    }

    @GetMapping("/es/movies/search")
    public List<MovieDocument> searchMovie(@RequestParam String keyword) {
        return movieSearchRepository.findByTitleContaining(keyword);
    }

    @GetMapping("/es/movies/actor")
    public List<MovieDocument> searchByActor(@RequestParam String actor) {
        return movieSearchRepository.findByActorsContaining(actor);
    }

    @GetMapping("/es/autocomplete")
    public List<MovieDocument> autocomplete(@RequestParam String keyword) {
        Query query = MatchPhrasePrefixQuery.of(m -> m
                .field("title")
                .query(keyword)
        )._toQuery();

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .build();

        return elasticsearchOperations
                .search(nativeQuery, MovieDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}
