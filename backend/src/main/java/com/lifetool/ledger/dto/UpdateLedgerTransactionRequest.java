package com.lifetool.ledger.dto;

import java.time.Instant;

import jakarta.validation.constraints.Positive;

public record UpdateLedgerTransactionRequest(
        String type,
        @Positive Double amount,
        String currency,
        String category,
        String account,
        Instant occurredAt,
        String note,
        String mediaAssetId) {
}
