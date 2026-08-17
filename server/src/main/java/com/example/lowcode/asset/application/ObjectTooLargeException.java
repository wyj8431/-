package com.example.lowcode.asset.application;

public class ObjectTooLargeException extends StorageFailureException {
    public ObjectTooLargeException(String message) {
        super(message);
    }
}
