package com.telecombridge.gateway.controller;

import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.dto.ChargeResponse;
import com.telecombridge.gateway.exception.ValidationException;
import com.telecombridge.gateway.service.ChargeService;
import com.telecombridge.gateway.validation.ChargeRequestValidator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller exposing the charge endpoint for credit control requests.
 */
@RestController
@RequestMapping("/api/v1")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping("/charge")
    public CompletableFuture<ResponseEntity<ChargeResponse>> charge(
            @Valid @RequestBody ChargeRequest request) {

        // Perform custom validation beyond Bean Validation annotations
        List<String> validationErrors = ChargeRequestValidator.validate(request);
        if (!validationErrors.isEmpty()) {
            throw new ValidationException(validationErrors);
        }

        return chargeService.processCharge(request)
                .thenApply(ResponseEntity::ok);
    }
}
