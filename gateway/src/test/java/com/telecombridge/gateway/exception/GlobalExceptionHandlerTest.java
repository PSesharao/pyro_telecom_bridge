package com.telecombridge.gateway.exception;

import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterProtocolException;
import com.telecombridge.codec.DiameterTimeoutException;
import com.telecombridge.gateway.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler verifying correct HTTP status codes
 * and error response bodies for each error category.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleDiameterTimeoutException_returns504WithMessage() {
        DiameterTimeoutException ex = new DiameterTimeoutException(
                "Request timed out after 5000ms for session: test-session");

        ResponseEntity<ErrorResponse> response = handler.handleDiameterTimeoutException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Gateway Timeout");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0)).contains("timed out after 5000ms");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleDiameterConnectionException_returns503() {
        DiameterConnectionException ex = new DiameterConnectionException(
                "Diameter connection not established");

        ResponseEntity<ErrorResponse> response = handler.handleDiameterConnectionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0)).isEqualTo("Diameter connection not established");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleDiameterProtocolException_returns502() {
        DiameterProtocolException ex = new DiameterProtocolException(
                "CCA missing Result_Code AVP");

        ResponseEntity<ErrorResponse> response = handler.handleDiameterProtocolException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Bad Gateway");
        assertThat(response.getBody().details()).hasSize(1);
        assertThat(response.getBody().details().get(0)).isEqualTo("Protocol error: CCA missing Result_Code AVP");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleCompletionException_unwrapsTimeoutException_returns504() {
        DiameterTimeoutException cause = new DiameterTimeoutException(
                "Request timed out after 5123ms for session: sess-1");
        CompletionException ex = new CompletionException(cause);

        ResponseEntity<ErrorResponse> response = handler.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Gateway Timeout");
        assertThat(response.getBody().details().get(0)).contains("timed out after 5123ms");
    }

    @Test
    void handleCompletionException_unwrapsConnectionException_returns503() {
        DiameterConnectionException cause = new DiameterConnectionException(
                "Diameter connection not established");
        CompletionException ex = new CompletionException(cause);

        ResponseEntity<ErrorResponse> response = handler.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Service Unavailable");
    }

    @Test
    void handleCompletionException_unwrapsProtocolException_returns502() {
        DiameterProtocolException cause = new DiameterProtocolException(
                "missing Result_Code AVP");
        CompletionException ex = new CompletionException(cause);

        ResponseEntity<ErrorResponse> response = handler.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Bad Gateway");
        assertThat(response.getBody().details().get(0)).contains("missing Result_Code AVP");
    }

    @Test
    void handleCompletionException_unknownCause_returns500() {
        RuntimeException cause = new RuntimeException("unexpected error");
        CompletionException ex = new CompletionException(cause);

        ResponseEntity<ErrorResponse> response = handler.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().details().get(0)).isEqualTo("unexpected error");
    }
}
