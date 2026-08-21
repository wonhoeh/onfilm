package com.onfilm.domain.kafka.controller;

import com.onfilm.domain.kafka.dto.*;
import com.onfilm.domain.kafka.service.MediaEncodeJobInternalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Pattern;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/internal/api/media-jobs")
public class InternalMediaCallbackController {
    private final MediaEncodeJobInternalService service;

    @PostMapping("/{jobId}/processing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void processing(@PathVariable @Pattern(regexp = "^[0-9a-f-]{36}$") String jobId,
                           @Valid @RequestBody MediaEncodeProcessingRequest request) {
        service.markProcessing(jobId, request);
    }

    @PostMapping("/{jobId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable @Pattern(regexp = "^[0-9a-f-]{36}$") String jobId,
                         @Valid @RequestBody MediaEncodeCompletionRequest request) {
        service.complete(jobId, request);
    }

    @PostMapping("/{jobId}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable @Pattern(regexp = "^[0-9a-f-]{36}$") String jobId,
                     @Valid @RequestBody MediaEncodeFailureRequest request) {
        service.fail(jobId, request);
    }
}
