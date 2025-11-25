package com.onfilm.domain.movie.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class MovieExtraInfoResponse {

    private String synopsis;
    private List<MovieActorNameResponse> actors;
    private List<MovieDirectorNameResponse> directors;

    public MovieExtraInfoResponse(String synopsis, List<MovieActorNameResponse> actors, List<MovieDirectorNameResponse> directors) {
        this.synopsis = synopsis;
        this.actors = actors;
        this.directors = directors;
    }
}
