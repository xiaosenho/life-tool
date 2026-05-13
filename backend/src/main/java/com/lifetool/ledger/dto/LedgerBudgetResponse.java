package com.lifetool.ledger.dto;

import java.time.Instant;

import com.lifetool.ledger.LedgerBudget;

public record LedgerBudgetResponse(
        String id,
        String month,
        double amount,
        String currency,
        String category,
        Instant createdAt,
        Instant updatedAt) {

    public static LedgerBudgetResponse from(LedgerBudget budget) {
        return new LedgerBudgetResponse(
                budget.getId(),
                budget.getMonth(),
                budget.getAmount(),
                budget.getCurrency(),
                budget.getCategory(),
                budget.getCreatedAt(),
                budget.getUpdatedAt());
    }
}
