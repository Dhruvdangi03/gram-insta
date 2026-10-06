package com.instaclone.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wired against Cloudflare R2's S3-compatible API (endpoint/credentials in application.yml under
 * app.storage, from R2_* env vars) — the same client config for local dev and the deployed app,
 * since R2 is reachable directly from both.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                // chunkedEncodingEnabled(false): R2 doesn't support the AWS SDK's streaming/chunked
                // payload signing for PutObject — only matters for the direct server-side putObject
                // calls the media transcode worker makes; the client-side presigned-PUT upload flow
                // (StorageService) is unaffected either way.
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        // endpoint, not publicBaseUrl: unlike a generic CDN-fronted S3 bucket, R2's S3 API endpoint
        // (*.r2.cloudflarestorage.com) is itself directly reachable by any client, browsers
        // included — that's the whole point of it being S3-compatible, and it's the only host that
        // actually verifies a SigV4-signed request. R2's public r2.dev domain only serves anonymous
        // GETs for already-uploaded objects and has no signature verification at all, so a
        // presigned PUT/GET signed against it 401s. publicBaseUrl is used elsewhere purely to build
        // the plain (unsigned) URL stored/returned for later *viewing* the object once uploaded
        // (see StorageProperties.publicUrlFor) — it was never meant to be the signing host.
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public CommandLineRunner ensureBucketExists(S3Client s3Client, StorageProperties props) {
        return args -> {
            if (!props.autoCreateBucket()) {
                return;
            }
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
                log.info("Created storage bucket '{}'", props.bucket());
            } catch (BucketAlreadyOwnedByYouException e) {
                log.debug("Storage bucket '{}' already exists", props.bucket());
            } catch (Exception e) {
                log.warn("Could not verify/create storage bucket '{}': {}", props.bucket(), e.getMessage());
            }
        };
    }
}
