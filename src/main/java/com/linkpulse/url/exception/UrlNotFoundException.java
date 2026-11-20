package com.linkpulse.url.exception;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException() {
        super("Shortened URL was not found");
    }
}