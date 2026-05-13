package com.lifetool.ledger.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLedgerTransactionRequest(
        @NotBlank String type,
        @NotNull @Positive Double amount,
        String currency,
        String category,
        String account,
        @NotNull Instant occurredAt,
        String note,
        String mediaAssetId) {
}
