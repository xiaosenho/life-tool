package com.lifetool.ledger;

import java.util.List;
import java.util.Optional;

public interface LedgerTransactionStore {
    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(String id);

    List<LedgerTransaction> findByUserIdAndMonth(String userId, String month);

    List<LedgerTransaction> findByUserId(String userId);

    void deleteById(String id);
}
