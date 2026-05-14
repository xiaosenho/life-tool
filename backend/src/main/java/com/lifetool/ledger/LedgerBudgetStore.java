package com.lifetool.ledger;

import java.util.List;
import java.util.Optional;

public interface LedgerBudgetStore {
    LedgerBudget save(LedgerBudget budget);

    Optional<LedgerBudget> findByUserIdAndMonthAndCategory(String userId, String month, String category);

    List<LedgerBudget> findByUserIdAndMonth(String userId, String month);
}
