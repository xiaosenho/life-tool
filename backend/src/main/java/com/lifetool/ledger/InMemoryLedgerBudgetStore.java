package com.lifetool.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryLedgerBudgetStore implements LedgerBudgetStore {

    private final Map<String, LedgerBudget> budgetsByKey = new ConcurrentHashMap<>();

    private String buildKey(String userId, String month, String category) {
        String cat = category == null ? "_total_" : category;
        return userId + ":" + month + ":" + cat;
    }

    @Override
    public LedgerBudget save(LedgerBudget budget) {
        String key = buildKey(budget.getUserId(), budget.getMonth(), budget.getCategory());
        budgetsByKey.put(key, budget);
        return budget;
    }

    @Override
    public Optional<LedgerBudget> findByUserIdAndMonthAndCategory(String userId, String month, String category) {
        String key = buildKey(userId, month, category);
        return Optional.ofNullable(budgetsByKey.get(key));
    }

    @Override
    public List<LedgerBudget> findByUserIdAndMonth(String userId, String month) {
        List<LedgerBudget> result = new ArrayList<>();
        for (LedgerBudget budget : budgetsByKey.values()) {
            if (budget.getUserId().equals(userId) && budget.getMonth().equals(month)) {
                result.add(budget);
            }
        }
        return result;
    }

    public void clear() {
        budgetsByKey.clear();
    }
}
