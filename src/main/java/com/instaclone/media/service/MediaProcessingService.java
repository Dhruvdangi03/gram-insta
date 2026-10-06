package com.instaclone.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instaclone.config.properties.StorageProperties;
import com.instaclone.media.dto.TranscodeResult;
import com.instaclone.media.dto.VideoProbe;
import com.instaclone.media.exception.TranscodeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The FFmpeg/ffprobe "async worker" from the build doc's Section 4 — currently just a background
 * component inside the monolith (see MediaStreamConfig), not yet a separately deployed service.
 * Downloads the raw upload via the same S3Client the rest of the app uses, shells out to FFmpeg,
 * and uploads the 720p transcode + thumbnail back to the same bucket.
 */
@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);
    private static final int TARGET_HEIGHT = 720;
    private static final long PROCESS_TIMEOUT_MINUTES = 5;

    // A plain instance, not an injected Spring bean: this only ever parses ffprobe's fixed JSON
    // shape, unrelated to the app's HTTP-serialization ObjectMapper — and Boot 4's auto-configured
    // ObjectMapper bean is Jackson 3 (tools.jackson.databind), not this classic Jackson 2 API.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    public MediaProcessingService(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.storageProperties = storageProperties;
    }

    public TranscodeResult transcodeAndThumbnail(String sourceObjectKey, Long userId) {
        Path sourceFile = null;
        Path outputFile = null;
        Path thumbnailFile = null;
        try {
            log.info("Starting transcode for object key {} from R2", sourceObjectKey);
            sourceFile = Files.createTempFile("reel-source-", ".src");
            log.info("Created temp source file: {}", sourceFile);
            download(sourceObjectKey, sourceFile);
            log.info("Downloaded source video from R2, size: {} bytes", Files.size(sourceFile));

            outputFile = Files.createTempFile("reel-720p-", ".mp4");
            log.info("Starting FFmpeg transcode to 720p...");
            runProcess(List.of(
                    "ffmpeg",
                    "-y",
                    "-i",
                    sourceFile.toString(),
                    "-vf",
                    "scale=-2:" + TARGET_HEIGHT,
                    "-c:v",
                    "libx264",
                    "-preset",
                    "fast",
                    "-crf",
                    "23",
                    "-c:a",
                    "aac",
                    outputFile.toString()));
            log.info("FFmpeg transcode complete, output file size: {} bytes", Files.size(outputFile));

            // Probe the transcoded output, not the source — that's the file actually being served,
            // and its exact scaled width (from "-2") isn't something we should re-derive by hand.
            log.info("Probing transcoded video for metadata...");
            VideoProbe probe = parseProbe(runProcess(List.of(
                    "ffprobe",
                    "-v",
                    "error",
                    "-select_streams",
                    "v:0",
                    "-show_entries",
                    "stream=width,height:format=duration",
                    "-of",
                    "json",
                    outputFile.toString())));
            log.info("Video probe complete: {}x{} {} seconds", probe.width(), probe.height(), probe.durationSec());

            thumbnailFile = Files.createTempFile("reel-thumb-", ".jpg");
            double seekSeconds = Math.min(1.0, probe.durationSec() / 2.0);
            runProcess(List.of(
                    "ffmpeg",
                    "-y",
                    "-ss",
                    String.valueOf(seekSeconds),
                    "-i",
                    outputFile.toString(),
                    "-vframes",
                    "1",
                    thumbnailFile.toString()));

            String videoKey = "posts/%d/%s_720p.mp4".formatted(userId, UUID.randomUUID());
            String thumbnailKey = "posts/%d/%s_thumb.jpg".formatted(userId, UUID.randomUUID());
            log.info("Uploading transcode results to R2: video key={}, thumbnail key={}", videoKey, thumbnailKey);
            upload(outputFile, videoKey, "video/mp4");
            upload(thumbnailFile, thumbnailKey, "image/jpeg");
            log.info("Upload complete, transcode pipeline finished");

            String videoUrl = storageProperties.publicUrlFor(videoKey);
            String thumbnailUrl = storageProperties.publicUrlFor(thumbnailKey);
            log.info("Public URLs: video={}, thumbnail={}", videoUrl, thumbnailUrl);
            return new TranscodeResult(videoUrl, thumbnailUrl, probe.width(), probe.height(), probe.durationSec());
        } catch (IOException e) {
            throw new TranscodeException("Failed to transcode " + sourceObjectKey, e);
        } finally {
            deleteQuietly(sourceFile);
            deleteQuietly(outputFile);
            deleteQuietly(thumbnailFile);
        }
    }

    /** Pure parsing, kept separate from the process invocation above so it's unit-testable without FFmpeg. */
    VideoProbe parseProbe(String ffprobeJson) {
        try {
            JsonNode root = objectMapper.readTree(ffprobeJson);
            JsonNode stream = root.path("streams").path(0);
            double durationSec = root.path("format").path("duration").asDouble(0);
            return new VideoProbe(stream.path("width").asInt(), stream.path("height").asInt(), (int) Math.round(durationSec));
        } catch (IOException e) {
            throw new TranscodeException("Could not parse ffprobe output: " + ffprobeJson, e);
        }
    }

    private void download(String objectKey, Path destination) throws IOException {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(storageProperties.bucket()).key(objectKey).build();
        try (var in = s3Client.getObject(request)) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void upload(Path file, String objectKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromFile(file));
    }

    private String runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscodeException("%s timed out after %d minutes".formatted(command.get(0), PROCESS_TIMEOUT_MINUTES));
            }
            if (process.exitValue() != 0) {
                throw new TranscodeException("%s exited %d: %s".formatted(command.get(0), process.exitValue(), output));
            }
            return output;
        } catch (IOException e) {
            throw new TranscodeException("Failed to start " + command.get(0), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeException("Interrupted while running " + command.get(0), e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp file {}", path, e);
        }
    }
}
