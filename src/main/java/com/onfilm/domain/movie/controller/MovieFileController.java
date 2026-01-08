package com.onfilm.domain.movie.controller;

import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.UploadResultResponse;
import com.onfilm.domain.movie.service.FileCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files/movie")
public class MovieFileController {

    private final StorageService storage;
    private final StorageKeyFactory keyFactory;
    private final FileCommandService commandService;

    @PostMapping("/{movieId}/thumbnail")
    public UploadResultResponse uploadThumbnail(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieThumbnail(movieId, extOf(file));
        storage.save(key, file);
        commandService.updateMovieThumbnail(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @PostMapping("/{movieId}/trailer")
    public UploadResultResponse uploadTrailer(@PathVariable Long movieId,
                                              @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieTrailer(movieId, extOf(file));
        storage.save(key, file);
        commandService.addMovieTrailer(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    @PostMapping("/{movieId}/file")
    public UploadResultResponse uploadMovieFile(@PathVariable Long movieId,
                                                @RequestParam("file") MultipartFile file) {
        String key = keyFactory.movieFile(movieId, extOf(file));
        storage.save(key, file);
        commandService.updateMovieFile(movieId, key);
        return new UploadResultResponse(key, storage.toPublicUrl(key));
    }

    private String extOf(MultipartFile f) {
        String name = f.getOriginalFilename();
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i).toLowerCase();
    }
}
