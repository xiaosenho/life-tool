package com.lifetool.ledger.dto;

import java.util.List;

public record LedgerSummaryResponse(
        String month,
        double income,
        double expense,
        double balance,
        double budget,
        List<CategoryExpenseResponse> categoryExpenses) {
}
