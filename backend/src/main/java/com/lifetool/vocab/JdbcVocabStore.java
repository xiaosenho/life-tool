package com.lifetool.vocab;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcVocabStore implements VocabStore {

    private final DataSource dataSource;

    public JdbcVocabStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<VocabBook> listBooks() {
        String sql = "SELECT id, code, variant, name, version, word_count, created_at, updated_at FROM vocab_books ORDER BY code, variant";
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            List<VocabBook> items = new ArrayList<>();
            while (rs.next()) items.add(mapBook(rs));
            return items;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list vocab books", ex);
        }
    }

    @Override
    public Optional<VocabBook> findBookByCodeAndVariant(String code, String variant) {
        String sql = "SELECT id, code, variant, name, version, word_count, created_at, updated_at FROM vocab_books WHERE code = ? AND variant = ?";
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            stmt.setString(2, variant);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapBook(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find vocab book", ex);
        }
    }

    @Override
    public List<VocabEntry> listEntries(String bookId, int offset, int limit) {
        String sql = "SELECT id, book_id, seq_no, word, phonetic, meaning_zh, created_at, updated_at FROM vocab_entries WHERE book_id = ?::uuid ORDER BY seq_no ASC OFFSET ? LIMIT ?";
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bookId);
            stmt.setInt(2, Math.max(0, offset));
            stmt.setInt(3, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                List<VocabEntry> items = new ArrayList<>();
                while (rs.next()) items.add(mapEntry(rs));
                return items;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list vocab entries", ex);
        }
    }

    @Override
    public Optional<UserVocabProgress> findProgress(String userId, String bookId) {
        String sql = "SELECT id, user_id, book_id, last_seq_no, hide_meaning, created_at, updated_at FROM user_vocab_progress WHERE user_id = ?::uuid AND book_id = ?::uuid";
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapProgress(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find vocab progress", ex);
        }
    }

    @Override
    public UserVocabProgress saveProgress(UserVocabProgress progress) {
        String sql = """
                INSERT INTO user_vocab_progress (id, user_id, book_id, last_seq_no, hide_meaning, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?)
                ON CONFLICT (user_id, book_id) DO UPDATE SET
                  last_seq_no = EXCLUDED.last_seq_no,
                  hide_meaning = EXCLUDED.hide_meaning,
                  updated_at = EXCLUDED.updated_at
                """;
        if (progress.getId() == null) progress.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (progress.getCreatedAt() == null) progress.setCreatedAt(now);
        progress.setUpdatedAt(now);
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, progress.getId());
            stmt.setString(2, progress.getUserId());
            stmt.setString(3, progress.getBookId());
            stmt.setInt(4, progress.getLastSeqNo());
            stmt.setBoolean(5, progress.isHideMeaning());
            stmt.setTimestamp(6, Timestamp.from(progress.getCreatedAt()));
            stmt.setTimestamp(7, Timestamp.from(progress.getUpdatedAt()));
            stmt.executeUpdate();
            return progress;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save vocab progress", ex);
        }
    }

    @Override
    public boolean hasAnyBooks() {
        String sql = "SELECT EXISTS (SELECT 1 FROM vocab_books)";
        try (Connection conn = dataSource.getConnection(); var stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getBoolean(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check vocab books", ex);
        }
    }

    @Override
    public void replaceBookData(List<VocabBookSeed> books) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var deleteEntries = conn.prepareStatement("DELETE FROM vocab_entries");
                 var deleteBooks = conn.prepareStatement("DELETE FROM vocab_books");
                 var insertBook = conn.prepareStatement("INSERT INTO vocab_books (id, code, variant, name, version, word_count, created_at, updated_at) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)");
                 var insertEntry = conn.prepareStatement("INSERT INTO vocab_entries (id, book_id, seq_no, word, phonetic, meaning_zh, created_at, updated_at) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)") ) {
                deleteEntries.executeUpdate();
                deleteBooks.executeUpdate();
                Instant now = Instant.now();
                for (VocabBookSeed book : books) {
                    String bookId = UUID.randomUUID().toString();
                    insertBook.setString(1, bookId);
                    insertBook.setString(2, book.code());
                    insertBook.setString(3, book.variant());
                    insertBook.setString(4, book.name());
                    insertBook.setString(5, book.version());
                    insertBook.setInt(6, book.entries().size());
                    insertBook.setTimestamp(7, Timestamp.from(now));
                    insertBook.setTimestamp(8, Timestamp.from(now));
                    insertBook.executeUpdate();
                    for (VocabEntrySeed entry : book.entries()) {
                        insertEntry.setString(1, UUID.randomUUID().toString());
                        insertEntry.setString(2, bookId);
                        insertEntry.setInt(3, entry.seqNo());
                        insertEntry.setString(4, entry.word());
                        insertEntry.setString(5, entry.phonetic());
                        insertEntry.setString(6, entry.meaningZh());
                        insertEntry.setTimestamp(7, Timestamp.from(now));
                        insertEntry.setTimestamp(8, Timestamp.from(now));
                        insertEntry.addBatch();
                    }
                    insertEntry.executeBatch();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to replace vocab seed data", ex);
        }
    }

    private VocabBook mapBook(ResultSet rs) throws SQLException {
        VocabBook book = new VocabBook();
        book.setId(rs.getString("id"));
        book.setCode(rs.getString("code"));
        book.setVariant(rs.getString("variant"));
        book.setName(rs.getString("name"));
        book.setVersion(rs.getString("version"));
        book.setWordCount(rs.getInt("word_count"));
        book.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        book.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return book;
    }

    private VocabEntry mapEntry(ResultSet rs) throws SQLException {
        VocabEntry entry = new VocabEntry();
        entry.setId(rs.getString("id"));
        entry.setBookId(rs.getString("book_id"));
        entry.setSeqNo(rs.getInt("seq_no"));
        entry.setWord(rs.getString("word"));
        entry.setPhonetic(rs.getString("phonetic"));
        entry.setMeaningZh(rs.getString("meaning_zh"));
        entry.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        entry.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return entry;
    }

    private UserVocabProgress mapProgress(ResultSet rs) throws SQLException {
        UserVocabProgress progress = new UserVocabProgress();
        progress.setId(rs.getString("id"));
        progress.setUserId(rs.getString("user_id"));
        progress.setBookId(rs.getString("book_id"));
        progress.setLastSeqNo(rs.getInt("last_seq_no"));
        progress.setHideMeaning(rs.getBoolean("hide_meaning"));
        progress.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        progress.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return progress;
    }
}
