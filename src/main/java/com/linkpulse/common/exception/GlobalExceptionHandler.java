package com.linkpulse.common.exception;

import com.linkpulse.auth.exception.EmailAlreadyRegisteredException;
import com.linkpulse.auth.exception.PasswordTooLongException;
import com.linkpulse.url.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(EmailAlreadyRegisteredException exception){
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Account already exists");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception){
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );

        problem.setTitle("Authentication failed");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(PasswordTooLongException.class)
    public ResponseEntity<ProblemDetail> handlePasswordTooLong(PasswordTooLongException exception){
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        problem.setTitle("Invalid password");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception){

        Map<String, String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error-> error.getDefaultMessage()==null ? "Invalid value" : error.getDefaultMessage(),
                        (first,second)->first
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid"
        );

        problem.setTitle("Validation failed");
        problem.setProperty("errors",errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }


    @ExceptionHandler({
            AliasAlreadyTakenException.class,
            ReservedAliasException.class
    })
    public ResponseEntity<ProblemDetail> handleUrlConflict(
            RuntimeException exception
    ) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("URL conflict");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUrlNotFound(
            UrlNotFoundException exception
    ) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );

        problem.setTitle("Resource not found");

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ProblemDetail> handleShortCodeFailure(
            ShortCodeGenerationException exception
    ) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage()
        );

        problem.setTitle("Short code generation unavailable");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception
    ) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        problem.setTitle("Invalid request");

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(UrlUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUrlUnavailable(
            UrlUnavailableException exception
    ) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.GONE,
                exception.getMessage()
        );

        problem.setTitle("URL no longer available");

        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(problem);
    }

}
