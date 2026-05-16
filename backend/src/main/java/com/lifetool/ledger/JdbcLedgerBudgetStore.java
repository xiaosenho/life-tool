package com.lifetool.ledger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcLedgerBudgetStore implements LedgerBudgetStore {

    private final DataSource dataSource;

    public JdbcLedgerBudgetStore(
            DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static Date toBudgetDate(String month) {
        return Date.valueOf(month + "-01");
    }

    @Override
    public LedgerBudget save(LedgerBudget budget) {
        String sql = """
                INSERT INTO ledger_budgets (id, user_id, budget_month, category, amount, \
                currency, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?::date, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  category = EXCLUDED.category,
                  amount = EXCLUDED.amount,
                  currency = EXCLUDED.currency,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, budget.getId());
            stmt.setString(2, budget.getUserId());
            stmt.setDate(3, toBudgetDate(budget.getMonth()));
            stmt.setString(4, budget.getCategory());
            stmt.setBigDecimal(5, BigDecimal.valueOf(budget.getAmount()));
            stmt.setString(6, budget.getCurrency());
            stmt.setTimestamp(7, budget.getCreatedAt() != null ? Timestamp.from(budget.getCreatedAt()) : null);
            stmt.setTimestamp(8, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return budget;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save budget", ex);
        }
    }

    @Override
    public Optional<LedgerBudget> findByUserIdAndMonthAndCategory(String userId, String month, String category) {
        String sql = """
                SELECT id, user_id, budget_month, category, amount, currency, created_at, updated_at
                FROM ledger_budgets
                WHERE user_id = ?::uuid
                  AND budget_month = ?::date
                  AND category IS NOT DISTINCT FROM ?
                  AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setDate(2, toBudgetDate(month));
            stmt.setString(3, category);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapBudget(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load budget by user, month and category", ex);
        }
    }

    @Override
    public List<LedgerBudget> findByUserIdAndMonth(String userId, String month) {
        String sql = """
                SELECT id, user_id, budget_month, category, amount, currency, created_at, updated_at
                FROM ledger_budgets
                WHERE user_id = ?::uuid
                  AND budget_month = ?::date
                  AND deleted_at IS NULL
                """;
        List<LedgerBudget> budgets = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setDate(2, toBudgetDate(month));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    budgets.add(mapBudget(rs));
                }
            }
            return budgets;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load budgets by user and month", ex);
        }
    }

    private LedgerBudget mapBudget(ResultSet rs) throws SQLException {
        LedgerBudget budget = new LedgerBudget();
        budget.setId(rs.getString("id"));
        budget.setUserId(rs.getString("user_id"));
        budget.setMonth(rs.getDate("budget_month").toLocalDate().toString().substring(0, 7));
        budget.setCategory(rs.getString("category"));
        BigDecimal amount = rs.getBigDecimal("amount");
        if (amount != null) {
            budget.setAmount(amount.doubleValue());
        }
        budget.setCurrency(rs.getString("currency"));
        budget.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
        budget.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
        return budget;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
