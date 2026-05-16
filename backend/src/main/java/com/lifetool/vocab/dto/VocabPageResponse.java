package com.lifetool.vocab.dto;

import java.util.List;

public record VocabPageResponse(String bookCode, String bookName, int offset, int limit, int total, List<VocabEntryResponse> entries) {
}
