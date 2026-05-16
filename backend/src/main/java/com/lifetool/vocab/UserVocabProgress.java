package com.lifetool.vocab;

import java.time.Instant;

public class UserVocabProgress {
    private String id;
    private String userId;
    private String bookId;
    private int lastSeqNo;
    private boolean hideMeaning;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public int getLastSeqNo() { return lastSeqNo; }
    public void setLastSeqNo(int lastSeqNo) { this.lastSeqNo = lastSeqNo; }
    public boolean isHideMeaning() { return hideMeaning; }
    public void setHideMeaning(boolean hideMeaning) { this.hideMeaning = hideMeaning; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
