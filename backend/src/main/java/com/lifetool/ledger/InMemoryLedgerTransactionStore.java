package com.lifetool.ledger;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryLedgerTransactionStore implements LedgerTransactionStore {

    private final Map<String, LedgerTransaction> transactionsById = new ConcurrentHashMap<>();

    @Override
    public LedgerTransaction save(LedgerTransaction transaction) {
        transactionsById.put(transaction.getId(), transaction);
        return transaction;
    }

    @Override
    public Optional<LedgerTransaction> findById(String id) {
        return Optional.ofNullable(transactionsById.get(id));
    }

    @Override
    public List<LedgerTransaction> findByUserIdAndMonth(String userId, String month) {
        List<LedgerTransaction> result = new ArrayList<>();
        YearMonth yearMonth = YearMonth.parse(month);

        for (LedgerTransaction tx : transactionsById.values()) {
            if (tx.getUserId().equals(userId) && !tx.isDeleted()) {
                Instant occurredAt = tx.getOccurredAt();
                if (occurredAt != null) {
                    YearMonth txMonth = YearMonth.from(occurredAt.atZone(ZoneOffset.UTC));
                    if (txMonth.equals(yearMonth)) {
                        result.add(tx);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<LedgerTransaction> findByUserId(String userId) {
        List<LedgerTransaction> result = new ArrayList<>();
        for (LedgerTransaction tx : transactionsById.values()) {
            if (tx.getUserId().equals(userId) && !tx.isDeleted()) {
                result.add(tx);
            }
        }
        return result;
    }

    @Override
    public void deleteById(String id) {
        transactionsById.remove(id);
    }

    public void clear() {
        transactionsById.clear();
    }
}
