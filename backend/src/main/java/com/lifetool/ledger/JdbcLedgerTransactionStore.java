package com.lifetool.ledger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.lifetool.common.TimeSupport;

@Repository
@Profile("postgres")
public class JdbcLedgerTransactionStore implements LedgerTransactionStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcLedgerTransactionStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public LedgerTransaction save(LedgerTransaction transaction) {
        String sql = """
                INSERT INTO ledger_transactions (id, user_id, type, category, amount, \
                currency, occurred_at, description, media_asset_id, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  type = EXCLUDED.type,
                  category = EXCLUDED.category,
                  amount = EXCLUDED.amount,
                  currency = EXCLUDED.currency,
                  occurred_at = EXCLUDED.occurred_at,
                  description = EXCLUDED.description,
                  media_asset_id = EXCLUDED.media_asset_id,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transaction.getId());
            stmt.setString(2, transaction.getUserId());
            stmt.setString(3, transaction.getType());
            stmt.setString(4, transaction.getCategory());
            stmt.setBigDecimal(5, BigDecimal.valueOf(transaction.getAmount()));
            stmt.setString(6, transaction.getCurrency());
            stmt.setTimestamp(7, transaction.getOccurredAt() != null ? Timestamp.from(transaction.getOccurredAt()) : null);
            stmt.setString(8, transaction.getNote());
            stmt.setString(9, transaction.getMediaAssetId());
            stmt.setTimestamp(10, transaction.getCreatedAt() != null ? Timestamp.from(transaction.getCreatedAt()) : null);
            stmt.setTimestamp(11, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return transaction;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save transaction", ex);
        }
    }

    @Override
    public Optional<LedgerTransaction> findById(String id) {
        String sql = """
                SELECT id, user_id, type, category, amount, currency, occurred_at, \
                description, media_asset_id, created_at, updated_at
                FROM ledger_transactions
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTransaction(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load transaction", ex);
        }
    }

    @Override
    public List<LedgerTransaction> findByUserIdAndMonth(String userId, String month) {
        String businessZone = TimeSupport.BUSINESS_ZONE.getId();
        String sql = """
                SELECT id, user_id, type, category, amount, currency, occurred_at, \
                description, media_asset_id, created_at, updated_at
                FROM ledger_transactions
                WHERE user_id = ?::uuid
                  AND to_char(occurred_at AT TIME ZONE '%s', 'YYYY-MM') = ?
                  AND deleted_at IS NULL
                """.formatted(businessZone);
        List<LedgerTransaction> transactions = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, month);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapTransaction(rs));
                }
            }
            return transactions;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load transactions by month", ex);
        }
    }

    @Override
    public List<LedgerTransaction> findByUserId(String userId) {
        String sql = """
                SELECT id, user_id, type, category, amount, currency, occurred_at, \
                description, media_asset_id, created_at, updated_at
                FROM ledger_transactions
                WHERE user_id = ?::uuid AND deleted_at IS NULL
                """;
        List<LedgerTransaction> transactions = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapTransaction(rs));
                }
            }
            return transactions;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load transactions by user", ex);
        }
    }

    @Override
    public void deleteById(String id) {
        String sql = "UPDATE ledger_transactions SET deleted_at = now(), updated_at = now() WHERE id = ?::uuid AND deleted_at IS NULL";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete transaction", ex);
        }
    }

    private LedgerTransaction mapTransaction(ResultSet rs) throws SQLException {
        LedgerTransaction transaction = new LedgerTransaction();
        transaction.setId(rs.getString("id"));
        transaction.setUserId(rs.getString("user_id"));
        transaction.setType(rs.getString("type"));
        transaction.setCategory(rs.getString("category"));
        BigDecimal amount = rs.getBigDecimal("amount");
        if (amount != null) {
            transaction.setAmount(amount.doubleValue());
        }
        transaction.setCurrency(rs.getString("currency"));
        transaction.setOccurredAt(rs.getTimestamp("occurred_at") != null ? rs.getTimestamp("occurred_at").toInstant() : null);
        transaction.setNote(rs.getString("description"));
        transaction.setMediaAssetId(rs.getString("media_asset_id"));
        transaction.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
        transaction.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
        return transaction;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
