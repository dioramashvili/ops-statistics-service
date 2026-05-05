USE BANK2000
GO

CREATE TABLE basis.OPS_SEGMENT_STATISTICS_DAVIT (
    debit_segment   VARCHAR(10)  NOT NULL,
    credit_segment  VARCHAR(10)  NOT NULL,
    channel_id      INT          NOT NULL,
    doc_date        DATE         NOT NULL,
    op_count        INT          NOT NULL DEFAULT 0,
    CONSTRAINT PK_OPS_SEGMENT_STATISTICS_DAVIT 
        PRIMARY KEY (debit_segment, credit_segment, channel_id, doc_date),
    CONSTRAINT CHK_op_count_non_negative 
        CHECK (op_count >= 0)
);