package com.onfilm.domain.file.service;

import java.nio.file.Path;

public interface MediaEncodingService {

    Path encodeVideo(Path input, int targetHeight, int targetBitrateKbps);

    Path encodeImage(Path input, int targetWidth, int targetHeight);
}
