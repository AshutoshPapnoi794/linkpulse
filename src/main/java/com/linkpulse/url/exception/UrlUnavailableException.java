package com.linkpulse.url.exception;

public class UrlUnavailableException
        extends RuntimeException {

    public UrlUnavailableException() {
        super("This shortened URL is no longer available");
    }
}