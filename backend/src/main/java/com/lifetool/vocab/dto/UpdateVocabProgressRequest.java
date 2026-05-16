package com.lifetool.vocab.dto;

public record UpdateVocabProgressRequest(String bookCode, Integer lastSeqNo, Boolean hideMeaning) {
}
