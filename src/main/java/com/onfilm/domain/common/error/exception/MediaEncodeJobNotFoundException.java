package com.onfilm.domain.common.error.exception;

public class MediaEncodeJobNotFoundException extends RuntimeException {

    public MediaEncodeJobNotFoundException(String jobId) {
        super("Media encode job not found: " + jobId);
    }
}
