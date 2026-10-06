package com.instaclone.post.service;

import com.instaclone.config.properties.StorageProperties;
import com.instaclone.post.dto.PresignedUploadResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Presigned-upload flow: the client PUTs the file straight to storage using the returned URL,
 * then calls POST /posts referencing the resulting public URL. The API never touches the bytes.
 * Talks to Cloudflare R2's S3-compatible API — same client, local dev and production, since R2
 * is reachable directly from both (see app.storage.* / R2_* env vars).
 */
@Service
public class StorageService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    private final S3Presigner presigner;
    private final StorageProperties props;

    public StorageService(S3Presigner presigner, StorageProperties props) {
        this.presigner = presigner;
        this.props = props;
    }

    public PresignedUploadResponse createUploadUrl(Long userId, String contentType) {
        String extension = contentType.substring(contentType.indexOf('/') + 1);
        String objectKey = "posts/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);

        // builds the request
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        // specify TTL
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putRequest)
                .build();

        // signs the request
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        String publicUrl = props.publicUrlFor(objectKey);

        return new PresignedUploadResponse(presigned.url().toString(), objectKey, publicUrl);
    }
}
