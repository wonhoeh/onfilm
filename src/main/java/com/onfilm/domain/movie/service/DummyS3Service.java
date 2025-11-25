package com.onfilm.domain.movie.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DummyS3Service {

    public String uploadFile(String originalFileName) {
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;
        return fileName;
    }
}
