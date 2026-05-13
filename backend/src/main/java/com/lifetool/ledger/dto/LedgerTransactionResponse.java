package com.lifetool.ledger.dto;

import java.time.Instant;

import com.lifetool.ledger.LedgerTransaction;

public record LedgerTransactionResponse(
        String id,
        String type,
        double amount,
        String currency,
        String category,
        String account,
        Instant occurredAt,
        String note,
        String mediaAssetId,
        Instant createdAt,
        Instant updatedAt) {

    public static LedgerTransactionResponse from(LedgerTransaction transaction) {
        return new LedgerTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCategory(),
                transaction.getAccount(),
                transaction.getOccurredAt(),
                transaction.getNote(),
                transaction.getMediaAssetId(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
