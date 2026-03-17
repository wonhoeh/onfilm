package com.onfilm.domain.kafka.controller;

import com.onfilm.domain.kafka.dto.MediaJobStatusUpdateRequest;
import com.onfilm.domain.kafka.dto.MovieMediaUpdateRequest;
import com.onfilm.domain.kafka.dto.TrailerMediaUpdateRequest;
import com.onfilm.domain.kafka.service.MediaEncodeJobInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/api")
public class InternalMediaCallbackController {

    private final MediaEncodeJobInternalService mediaEncodeJobInternalService;

    @PatchMapping("/media-jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMediaJobStatus(@PathVariable String jobId,
                                     @RequestBody MediaJobStatusUpdateRequest request) {
        mediaEncodeJobInternalService.updateJobStatus(jobId, request);
    }

    @PatchMapping("/movies/{movieId}/media")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMovieMedia(@PathVariable Long movieId,
                                 @RequestBody MovieMediaUpdateRequest request) {
        mediaEncodeJobInternalService.updateMovieMedia(movieId, request);
    }

    @PatchMapping("/trailers/{jobId}/media")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTrailerMedia(@PathVariable String jobId,
                                   @RequestBody TrailerMediaUpdateRequest request) {
        mediaEncodeJobInternalService.updateTrailerMedia(jobId, request);
    }
}
