package com.lifetool.vocab.dto;

public record VocabProgressResponse(String bookCode, int lastSeqNo, boolean hideMeaning) {
}
