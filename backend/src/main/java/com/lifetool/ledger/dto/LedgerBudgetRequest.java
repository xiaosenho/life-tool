package com.lifetool.ledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LedgerBudgetRequest(
        @NotNull @Positive Double amount,
        String currency,
        String category) {
}
