package com.example.lowcode.asset.application;

public class StorageFailureException extends RuntimeException {
    public StorageFailureException(String message) {
        super(message);
    }

    public StorageFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
