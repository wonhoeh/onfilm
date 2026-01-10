package com.onfilm.domain.movie.dto;

import java.util.List;

public record GalleryReorderRequest(
        List<String> keys
) {}
