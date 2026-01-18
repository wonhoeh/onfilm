package com.onfilm.domain.file.infrastructure.local;

import com.onfilm.domain.file.service.MediaEncodingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class LocalMediaEncodingService implements MediaEncodingService {

    private static final int TARGET_FPS_LOW = 24;
    private static final int TARGET_FPS_HIGH = 30;
    private static final int FPS_THRESHOLD = 25;
    private static final float JPEG_QUALITY = 0.85f;

    private final String ffmpegPath;
    private final String ffprobePath;

    public LocalMediaEncodingService(
            @Value("${media.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${media.ffprobe.path:ffprobe}") String ffprobePath
    ) {
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    @Override
    public Path encodeVideo(Path input, int targetHeight, int targetBitrateKbps) {
        int fps = chooseTargetFps(input);
        Path output = createTempFile(".mp4");
        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-i", input.toString(),
                "-vf", "scale=-2:" + targetHeight,
                "-r", String.valueOf(fps),
                "-c:v", "libx264",
                "-profile:v", "high",
                "-preset", "medium",
                "-b:v", targetBitrateKbps + "k",
                "-maxrate", targetBitrateKbps + "k",
                "-bufsize", (targetBitrateKbps * 2) + "k",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-ac", "2",
                "-ar", "48000",
                output.toString()
        );
        runCommand(command, "FFMPEG_ENCODE_FAILED");
        return output;
    }

    @Override
    public Path encodeImage(Path input, int targetWidth, int targetHeight) {
        BufferedImage source;
        try {
            source = ImageIO.read(input.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (source == null) throw new IllegalArgumentException("UNSUPPORTED_IMAGE");

        double scale = Math.min(
                (double) targetWidth / source.getWidth(),
                (double) targetHeight / source.getHeight()
        );
        int newWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int newHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, targetWidth, targetHeight);
        int x = (targetWidth - newWidth) / 2;
        int y = (targetHeight - newHeight) / 2;
        g.drawImage(source, x, y, newWidth, newHeight, null);
        g.dispose();

        Path output = createTempFile(".jpg");
        writeJpeg(canvas, output);
        return output;
    }

    private int chooseTargetFps(Path input) {
        Optional<Double> fps = readFps(input);
        if (fps.isEmpty()) return TARGET_FPS_HIGH;
        return fps.get() < FPS_THRESHOLD ? TARGET_FPS_LOW : TARGET_FPS_HIGH;
    }

    private Optional<Double> readFps(Path input) {
        List<String> command = List.of(
                ffprobePath,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate",
                "-of", "default=nw=1:nk=1",
                input.toString()
        );
        String output = runCommand(command, "FFPROBE_FAILED");
        if (output == null || output.isBlank()) return Optional.empty();
        String line = output.trim().split("\\R")[0].trim();
        if (line.isBlank()) return Optional.empty();
        try {
            if (line.contains("/")) {
                String[] parts = line.split("/");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                if (den == 0) return Optional.empty();
                return Optional.of(num / den);
            }
            return Optional.of(Double.parseDouble(line));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String runCommand(List<String> command, String errorCode) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString();
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(errorCode + ": " + output.trim());
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorCode + ": interrupted");
        }
    }

    private Path createTempFile(String suffix) {
        try {
            return Files.createTempFile("onfilm-", suffix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeJpeg(BufferedImage image, Path output) {
        try (ImageOutputStream out = ImageIO.createImageOutputStream(output.toFile())) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
