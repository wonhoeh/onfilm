package toyproject.onfilm.director.controller;

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
import toyproject.onfilm.director.dto.CreateDirectorRequest;
import toyproject.onfilm.director.service.DirectorService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/director")
@RequiredArgsConstructor
public class DirectorController {

    private final DirectorService directorService;

    @PostMapping()
    public ResponseEntity<Long> createDirector(@RequestBody @Validated CreateDirectorRequest request) {
        Long directorId = directorService.createDirector(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(directorId);
    }
}
