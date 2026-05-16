package com.lifetool.vocab.dto;

public record VocabProgressResponse(String bookCode, String variant, int lastSeqNo, boolean hideMeaning) {
}
