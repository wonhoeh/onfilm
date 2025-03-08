package toyproject.onfilm.s3.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import toyproject.onfilm.s3.entity.FileEntity;
import toyproject.onfilm.s3.repository.FileRepository;
import toyproject.onfilm.s3.service.FileService;
import toyproject.onfilm.s3.service.S3Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final S3Service s3Service;


    // fileType = "movie"인 경우 movieId를 받아서 movieUrl을 업데이트함
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file")MultipartFile file,
                                             @RequestParam("fileType") String fileType,
                                             @RequestParam(value = "movieId", required = false) Long movieId) {
        String fileName = "";
        if(movieId != null) {
            fileName = fileService.uploadFile(file, fileType, movieId);
        } else {
            fileName = fileService.uploadFile(file, fileType);
        }
        return ResponseEntity.ok(fileName);
    }

    @GetMapping()
    public String getFileUrl(@RequestParam String fileName) {
        return s3Service.getFileUrl(fileName);
    }
}
