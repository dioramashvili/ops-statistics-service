USE STATISTICS_DB
GO

-- Segment statistics: one aggregate row per
-- (debit segment, credit segment, channel, document date).
CREATE TABLE dbo.SEGMENT_STATISTICS (
    debit_segment   VARCHAR(10)  NOT NULL,
    credit_segment  VARCHAR(10)  NOT NULL,
    channel_id      INT          NOT NULL,
    doc_date        DATE         NOT NULL,
    op_count        INT          NOT NULL DEFAULT 0,
    CONSTRAINT PK_SEGMENT_STATISTICS
        PRIMARY KEY (debit_segment, credit_segment, channel_id, doc_date),
    -- Defence-in-depth backstop; the service also guards `op_count > 0` in the
    -- decrement statement itself, so it never attempts to go negative.
    CONSTRAINT CHK_op_count_non_negative
        CHECK (op_count >= 0)
);
GO

-- Idempotency ledger: message ids that have already been processed. The primary
-- key makes a re-delivered message fail the insert (duplicate key), which the
-- service treats as "already processed" and skips.
CREATE TABLE dbo.PROCESSED_MESSAGES (
    message_id   NVARCHAR(255) NOT NULL,
    processed_at DATETIME2     NOT NULL
        CONSTRAINT DF_PROCESSED_MESSAGES_processed_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_PROCESSED_MESSAGES PRIMARY KEY (message_id)
);
GO

-- Dead letters: messages that exhausted their retries and were dead-lettered.
-- The dead-letter listener persists them here for inspection / replay.
CREATE TABLE dbo.DEAD_LETTERS (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    message_id           NVARCHAR(255) NULL,
    original_routing_key NVARCHAR(255) NULL,
    death_reason         NVARCHAR(255) NULL,
    death_count          INT           NULL,
    body                 NVARCHAR(MAX) NULL,
    created_at           DATETIME2     NOT NULL
        CONSTRAINT DF_DEAD_LETTERS_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_DEAD_LETTERS PRIMARY KEY (id)
);
GO
