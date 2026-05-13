package com.lifetool.ledger;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lifetool.ledger.dto.CategoryExpenseResponse;
import com.lifetool.ledger.dto.CreateLedgerTransactionRequest;
import com.lifetool.ledger.dto.LedgerBudgetRequest;
import com.lifetool.ledger.dto.LedgerBudgetResponse;
import com.lifetool.ledger.dto.LedgerSummaryResponse;
import com.lifetool.ledger.dto.LedgerTransactionResponse;
import com.lifetool.ledger.dto.UpdateLedgerTransactionRequest;

@Service
public class LedgerService {

    private static final List<String> TYPES = List.of("income", "expense", "transfer");

    private final LedgerTransactionStore transactionStore;
    private final LedgerBudgetStore budgetStore;

    public LedgerService(LedgerTransactionStore transactionStore, LedgerBudgetStore budgetStore) {
        this.transactionStore = transactionStore;
        this.budgetStore = budgetStore;
    }

    public List<LedgerTransactionResponse> listTransactions(String userId, String month) {
        validateMonth(month);
        return transactionStore.findByUserIdAndMonth(userId, month).stream()
                .sorted(Comparator.comparing(LedgerTransaction::getOccurredAt).reversed())
                .map(LedgerTransactionResponse::from)
                .toList();
    }

    public LedgerTransactionResponse createTransaction(String userId, CreateLedgerTransactionRequest request) {
        validateType(request.type());

        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCurrency(normalizeCurrency(request.currency()));
        transaction.setCategory(blankToNull(request.category()));
        transaction.setAccount(blankToNull(request.account()));
        transaction.setOccurredAt(request.occurredAt());
        transaction.setNote(blankToNull(request.note()));
        transaction.setMediaAssetId(blankToNull(request.mediaAssetId()));

        transactionStore.save(transaction);
        return LedgerTransactionResponse.from(transaction);
    }

    public LedgerTransactionResponse updateTransaction(String userId, String id, UpdateLedgerTransactionRequest request) {
        LedgerTransaction transaction = findOwnedTransaction(userId, id);

        if (request.type() != null) {
            validateType(request.type());
            transaction.setType(request.type());
        }
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.currency() != null) {
            transaction.setCurrency(normalizeCurrency(request.currency()));
        }
        if (request.category() != null) {
            transaction.setCategory(blankToNull(request.category()));
        }
        if (request.account() != null) {
            transaction.setAccount(blankToNull(request.account()));
        }
        if (request.occurredAt() != null) {
            transaction.setOccurredAt(request.occurredAt());
        }
        if (request.note() != null) {
            transaction.setNote(blankToNull(request.note()));
        }
        if (request.mediaAssetId() != null) {
            transaction.setMediaAssetId(blankToNull(request.mediaAssetId()));
        }

        transaction.setUpdatedAt(Instant.now());
        transactionStore.save(transaction);
        return LedgerTransactionResponse.from(transaction);
    }

    public void deleteTransaction(String userId, String id) {
        LedgerTransaction transaction = findOwnedTransaction(userId, id);
        transaction.setDeleted(true);
        transaction.setUpdatedAt(Instant.now());
        transactionStore.save(transaction);
    }

    public LedgerSummaryResponse getSummary(String userId, String month) {
        validateMonth(month);
        List<LedgerTransaction> transactions = transactionStore.findByUserIdAndMonth(userId, month);

        double income = transactions.stream()
                .filter(t -> "income".equals(t.getType()))
                .mapToDouble(LedgerTransaction::getAmount)
                .sum();
        double expense = transactions.stream()
                .filter(t -> "expense".equals(t.getType()))
                .mapToDouble(LedgerTransaction::getAmount)
                .sum();

        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        transactions.stream()
                .filter(t -> "expense".equals(t.getType()))
                .forEach(t -> {
                    String category = t.getCategory() == null ? "未分类" : t.getCategory();
                    categoryTotals.merge(category, t.getAmount(), Double::sum);
                });
        List<CategoryExpenseResponse> categoryExpenses = categoryTotals.entrySet().stream()
                .map(e -> new CategoryExpenseResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoryExpenseResponse::amount).reversed())
                .toList();

        double budget = budgetStore.findByUserIdAndMonthAndCategory(userId, month, null)
                .map(LedgerBudget::getAmount)
                .orElse(0.0);

        return new LedgerSummaryResponse(month, income, expense, income - expense, budget, categoryExpenses);
    }

    public List<LedgerBudgetResponse> listBudgets(String userId, String month) {
        validateMonth(month);
        return budgetStore.findByUserIdAndMonth(userId, month).stream()
                .map(LedgerBudgetResponse::from)
                .toList();
    }

    public LedgerBudgetResponse saveBudget(String userId, String month, LedgerBudgetRequest request) {
        validateMonth(month);
        String category = blankToNull(request.category());
        LedgerBudget budget = budgetStore.findByUserIdAndMonthAndCategory(userId, month, category)
                .orElseGet(() -> new LedgerBudget(userId, month, category));

        budget.setAmount(request.amount());
        budget.setCurrency(normalizeCurrency(request.currency()));
        budget.setUpdatedAt(Instant.now());
        budgetStore.save(budget);

        return LedgerBudgetResponse.from(budget);
    }

    private LedgerTransaction findOwnedTransaction(String userId, String id) {
        LedgerTransaction transaction = transactionStore.findById(id)
                .orElseThrow(() -> new LedgerException("NOT_FOUND", "Transaction not found"));
        if (transaction.isDeleted()) {
            throw new LedgerException("NOT_FOUND", "Transaction not found");
        }
        if (!transaction.getUserId().equals(userId)) {
            throw new LedgerException("FORBIDDEN", "Access denied");
        }
        return transaction;
    }

    private static void validateType(String type) {
        if (!TYPES.contains(type)) {
            throw new LedgerException("VALIDATION_ERROR", "type must be income, expense or transfer");
        }
    }

    private static void validateMonth(String month) {
        try {
            YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new LedgerException("VALIDATION_ERROR", "month must be YYYY-MM");
        }
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
