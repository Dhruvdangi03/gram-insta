package com.instaclone.media.exception;

public class TranscodeException extends RuntimeException {

    public TranscodeException(String message) {
        super(message);
    }

    public TranscodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
