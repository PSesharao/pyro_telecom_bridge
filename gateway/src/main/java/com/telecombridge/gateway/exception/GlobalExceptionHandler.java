package com.telecombridge.gateway.exception;

import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterProtocolException;
import com.telecombridge.codec.DiameterTimeoutException;
import com.telecombridge.gateway.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * Global exception handler that translates exceptions into structured HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures (e.g., @NotBlank, @NotNull).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();

        ErrorResponse response = new ErrorResponse(
                "Validation failed",
                details,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles custom validation failures (MSISDN format, requestType range, serviceIdentifier format).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        ErrorResponse response = new ErrorResponse(
                "Validation failed",
                ex.getErrors(),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles Diameter connection unavailable — returns HTTP 503.
     */
    @ExceptionHandler(DiameterConnectionException.class)
    public ResponseEntity<ErrorResponse> handleDiameterConnectionException(DiameterConnectionException ex) {
        ErrorResponse response = new ErrorResponse(
                "Service Unavailable",
                List.of("Diameter connection not established"),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * Handles Diameter request timeout — returns HTTP 504.
     */
    @ExceptionHandler(DiameterTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleDiameterTimeoutException(DiameterTimeoutException ex) {
        ErrorResponse response = new ErrorResponse(
                "Gateway Timeout",
                List.of(ex.getMessage()),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    /**
     * Handles Diameter protocol errors — returns HTTP 502.
     */
    @ExceptionHandler(DiameterProtocolException.class)
    public ResponseEntity<ErrorResponse> handleDiameterProtocolException(DiameterProtocolException ex) {
        ErrorResponse response = new ErrorResponse(
                "Bad Gateway",
                List.of("Protocol error: " + ex.getMessage()),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    /**
     * Handles CompletionException by unwrapping and delegating to the appropriate handler.
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletionException(CompletionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof DiameterTimeoutException dte) {
            return handleDiameterTimeoutException(dte);
        } else if (cause instanceof DiameterConnectionException dce) {
            return handleDiameterConnectionException(dce);
        } else if (cause instanceof DiameterProtocolException dpe) {
            return handleDiameterProtocolException(dpe);
        }

        ErrorResponse response = new ErrorResponse(
                "Internal Server Error",
                List.of(cause != null ? cause.getMessage() : ex.getMessage()),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
