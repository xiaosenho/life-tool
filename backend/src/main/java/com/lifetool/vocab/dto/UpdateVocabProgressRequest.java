package com.lifetool.vocab.dto;

public record UpdateVocabProgressRequest(String bookCode, String variant, Integer lastSeqNo, Boolean hideMeaning) {
}
