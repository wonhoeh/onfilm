package toyproject.onfilm.writer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import toyproject.onfilm.writer.dto.CreateWriterRequest;
import toyproject.onfilm.writer.service.WriterService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/writer")
@RequiredArgsConstructor
public class WriterController {

    private final WriterService writerService;

    @PostMapping()
    public ResponseEntity<Long> createDirector(@RequestBody @Validated CreateWriterRequest request) {
        Long writerId = writerService.createWriter(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(writerId);
    }
}
