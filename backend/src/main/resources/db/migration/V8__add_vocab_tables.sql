CREATE TABLE vocab_books (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text        NOT NULL,
    name            text        NOT NULL,
    version         text        NOT NULL DEFAULT '2026.1',
    word_count      int         NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_vocab_books_code ON vocab_books (code);

CREATE TABLE vocab_entries (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id         uuid        NOT NULL REFERENCES vocab_books(id) ON DELETE CASCADE,
    seq_no          int         NOT NULL,
    word            text        NOT NULL,
    phonetic        text        NULL,
    meaning_zh      text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_vocab_entries_book_seq ON vocab_entries (book_id, seq_no);
CREATE INDEX idx_vocab_entries_book_word ON vocab_entries (book_id, word);

CREATE TABLE user_vocab_progress (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id         uuid        NOT NULL REFERENCES vocab_books(id) ON DELETE CASCADE,
    last_seq_no     int         NOT NULL DEFAULT 0,
    hide_meaning    boolean     NOT NULL DEFAULT false,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_vocab_progress ON user_vocab_progress (user_id, book_id);
