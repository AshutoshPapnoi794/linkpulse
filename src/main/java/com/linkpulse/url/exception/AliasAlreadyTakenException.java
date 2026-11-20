package com.linkpulse.url.exception;

public class AliasAlreadyTakenException
        extends RuntimeException {

    public AliasAlreadyTakenException() {
        super("This custom alias is already taken");
    }
}