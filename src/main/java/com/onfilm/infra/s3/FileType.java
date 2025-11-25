package com.onfilm.infra.s3;

public enum FileType {
    MOVIE("movies/"),
    TRAILER("trailers/"),
    THUMBNAIL("thumbnails/"),
    PROFILE_IMAGE("profileImages/");

    private final String folder;

    FileType(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }
}
