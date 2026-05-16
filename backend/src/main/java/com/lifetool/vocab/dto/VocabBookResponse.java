package com.lifetool.vocab.dto;

import com.lifetool.vocab.VocabBook;

public record VocabBookResponse(String code, String name, String version, int wordCount) {
    public static VocabBookResponse from(VocabBook book) {
        return new VocabBookResponse(book.getCode(), book.getName(), book.getVersion(), book.getWordCount());
    }
}
