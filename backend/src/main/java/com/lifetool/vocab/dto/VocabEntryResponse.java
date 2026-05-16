package com.lifetool.vocab.dto;

import com.lifetool.vocab.VocabEntry;

public record VocabEntryResponse(int seqNo, String word, String phonetic, String meaningZh) {
    public static VocabEntryResponse from(VocabEntry entry) {
        return new VocabEntryResponse(entry.getSeqNo(), entry.getWord(), entry.getPhonetic(), entry.getMeaningZh());
    }
}
