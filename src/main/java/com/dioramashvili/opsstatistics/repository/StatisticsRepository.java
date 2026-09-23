package com.dioramashvili.opsstatistics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class StatisticsRepository {
    private static final Logger log = LoggerFactory.getLogger(StatisticsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public StatisticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void increment(String debitSegment, String creditSegment, int channelId, LocalDate date) {
        try {
            jdbcTemplate.update(
                    """
                            BEGIN TRY
                                UPDATE dbo.SEGMENT_STATISTICS
                                SET op_count = op_count + 1
                                WHERE debit_segment  = ?
                                  AND credit_segment = ?
                                  AND channel_id     = ?
                                  AND doc_date       = ?;
                            
                                IF @@ROWCOUNT = 0
                                BEGIN
                                    INSERT INTO dbo.SEGMENT_STATISTICS
                                        (debit_segment, credit_segment, channel_id, doc_date, op_count)
                                    VALUES (?, ?, ?, ?, 1);
                                END
                            END TRY
                            BEGIN CATCH
                                IF ERROR_NUMBER() IN (2601, 2627)
                                BEGIN
                                    UPDATE dbo.SEGMENT_STATISTICS
                                    SET op_count = op_count + 1
                                    WHERE debit_segment  = ?
                                      AND credit_segment = ?
                                      AND channel_id     = ?
                                      AND doc_date       = ?;
                                END
                                ELSE THROW;
                            END CATCH
                            """,
                    // UPDATE
                    debitSegment, creditSegment, channelId, java.sql.Date.valueOf(date),
                    // INSERT
                    debitSegment, creditSegment, channelId, java.sql.Date.valueOf(date),
                    // CATCH UPDATE
                    debitSegment, creditSegment, channelId, java.sql.Date.valueOf(date)
            );
            log.info("Statistics incremented - debit: {}, credit: {}, channel: {}, date: {}",
                    debitSegment, creditSegment, channelId, date);
        } catch (
                DataAccessException e) {
            log.error("Failed to increment segment statistics for debitSegment={}, creditSegment={}, channelId={}, date={}",
                    debitSegment, creditSegment, channelId, date, e);
            throw e;
        }
    }

    public void decrement(String debitSegment, String creditSegment, int channelId, LocalDate date) {
        int rowsAffected = jdbcTemplate.update(
                """
                        UPDATE dbo.SEGMENT_STATISTICS
                        SET op_count = op_count - 1
                        WHERE debit_segment  = ?
                          AND credit_segment = ?
                          AND channel_id     = ?
                          AND doc_date       = ?
                          AND op_count       > 0
                        """,
                debitSegment,
                creditSegment,
                channelId,
                java.sql.Date.valueOf(date)
        );

        if (rowsAffected == 0) {
            log.warn("Decrement skipped — no positive statistics row for debit: {}, credit: {}, channel: {}, date: {} (row missing or already zero)",
                    debitSegment, creditSegment, channelId, date);
        } else {
            log.info("Statistics decremented - debit: {}, credit: {}, channel: {}, date: {}",
                    debitSegment, creditSegment, channelId, date);
        }
    }
}