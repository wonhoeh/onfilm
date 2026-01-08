package com.onfilm.domain.file.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StorageKeyFactory {

    public String profileAvatar(Long personId, String ext) {
        return "profile/" + personId + "/avatar/" + UUID.randomUUID() + ext;
    }
    public String gallery(Long personId, String ext) {
        return "gallery/" + personId + "/" + UUID.randomUUID() + ext;
    }
    public String movieThumbnail(Long movieId, String ext) {
        return "movie/" + movieId + "/thumbnail/" + UUID.randomUUID() + ext;
    }
    public String movieTrailer(Long movieId, String ext) {
        return "movie/" + movieId + "/trailer/" + UUID.randomUUID() + ext;
    }
    public String movieFile(Long movieId, String ext) {
        return "movie/" + movieId + "/file/" + UUID.randomUUID() + ext;
    }
}
