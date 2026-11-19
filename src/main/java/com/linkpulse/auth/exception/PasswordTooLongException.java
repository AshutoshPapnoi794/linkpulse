package com.linkpulse.auth.exception;

public class PasswordTooLongException extends RuntimeException {

    public PasswordTooLongException() {
        super("Password exceeds the supported UTF-8 byte length");
    }
}
