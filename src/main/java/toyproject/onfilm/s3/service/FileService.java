package toyproject.onfilm.s3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import toyproject.onfilm.exception.FileUploadException;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.s3.entity.FileEntity;
import toyproject.onfilm.s3.repository.FileRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Service s3Service;
    private final MovieRepository movieRepository;

    @Transactional
    public String uploadFile(MultipartFile multipartFile, String fileType) {
        // 1. 파일 S3 업로드
        String fileUrl = s3Service.upload(multipartFile, fileType);

        return fileUrl;
    }

    @Transactional
    public String uploadFile(MultipartFile multipartFile, String fileType, Long movieId) {
        // 1. 파일 S3 업로드
        String fileUrl = s3Service.upload(multipartFile, fileType);

        // 2. 파일 타입이 "movie"라면 movieUrl 업데이트
        if ("movie".equalsIgnoreCase(fileType) && movieId != null) {
            updateMovieUrl(movieId, fileUrl);
        }

        return fileUrl;
    }

    private void updateMovieUrl(Long movieId, String fileUrl) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다ㅣ " + movieId));
        movie.addMovieUrl(fileUrl);
    }
}
