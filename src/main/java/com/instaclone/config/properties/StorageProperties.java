package com.instaclone.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl,
        boolean autoCreateBucket) {

    /** publicBaseUrl is already bucket-rooted (R2's r2.dev public domain or a custom domain mapped
     * straight to the bucket — unlike the account-wide S3 API endpoint, it never takes a bucket
     * segment in the path), so a public object URL is just publicBaseUrl + "/" + objectKey. */
    public String publicUrlFor(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    /** The inverse of {@link #publicUrlFor}: recovers the object key from a URL this app itself
     * produced. */
    public String objectKeyFromPublicUrl(String url) {
        return url.substring((publicBaseUrl + "/").length());
    }

    /** True when the url points at an object this specific user uploaded via /posts/upload-url —
     * not just any object in the app's bucket, which would let a user reference media another user
     * uploaded (object keys are "posts/{userId}/..." per StorageService.createUploadUrl). */
    public boolean isOwnedUrl(String url, Long userId) {
        return url != null && url.startsWith(publicBaseUrl + "/posts/" + userId + "/");
    }
}
