package com.linkpulse.url.exception;

public class ReservedAliasException extends RuntimeException {

    public ReservedAliasException() {
        super("This alias is reserved by the application");
    }
}