package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.PersonReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/movie")
public class MovieFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final PersonReadService personReadService;

    @PostMapping("/{movieId}/thumbnail")
    public UploadResultResponse uploadThumbnail(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieThumbnail(movieId, extOf(file));
        storage.save(key, file);
        personReadService.updateMovieThumbnail(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @DeleteMapping("/{movieId}/thumbnail")
    public ResponseEntity<Void> deleteThumbnail(@PathVariable Long movieId) {
        personReadService.deleteMovieThumbnail(movieId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{movieId}/trailer")
    public UploadResultResponse uploadTrailer(@PathVariable Long movieId,
                                              @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieTrailer(movieId, extOf(file));
        storage.save(key, file);
        personReadService.addMovieTrailer(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @DeleteMapping("/{movieId}/trailer")
    public ResponseEntity<Void> deleteTrailer(@PathVariable Long movieId) {
        personReadService.deleteMovieTrailers(movieId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{movieId}/file")
    public UploadResultResponse uploadMovieFile(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieFile(movieId, extOf(file));
        storage.save(key, file);
        personReadService.updateMovieFile(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @DeleteMapping("/{movieId}/file")
    public ResponseEntity<Void> deleteMovieFile(@PathVariable Long movieId) {
        personReadService.deleteMovieFile(movieId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovieFiles(@PathVariable Long movieId) {
        personReadService.deleteMovieFiles(movieId);
        return ResponseEntity.noContent().build();
    }

    private String extOf(MultipartFile f) {
        String name = f.getOriginalFilename();
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i).toLowerCase();
    }
}
