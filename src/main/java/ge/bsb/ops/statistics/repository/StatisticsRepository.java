package ge.bsb.ops.statistics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public void upsert(String debitSegment, String creditSegment, int channelId, LocalDate date, String routingKey) {
        int delta = routingKey.equals("b6.transaction.create") ? 1 : -1;

        log.info("Upserting stats - debit: {}, credit: {}, channel: {}, date: {}, delta: {}",
                debitSegment, creditSegment, channelId, date, delta);

        try {
            int rowsAffected = jdbcTemplate.update(
                    """
                    MERGE basis.OPS_SEGMENT_STATISTICS_DAVIT AS target
                    USING (VALUES (?, ?, ?, ?)) AS source (debit_segment, credit_segment, channel_id, doc_date)
                    ON target.debit_segment = source.debit_segment
                    AND target.credit_segment = source.credit_segment
                    AND target.channel_id = source.channel_id
                    AND target.doc_date = source.doc_date
                    WHEN MATCHED THEN
                        UPDATE SET op_count = target.op_count + ?
                    WHEN NOT MATCHED AND ? > 0 THEN
                        INSERT (debit_segment, credit_segment, channel_id, doc_date, op_count)
                        VALUES (?, ?, ?, ?, ?);
                    """,
                    debitSegment,
                    creditSegment,
                    channelId,
                    java.sql.Date.valueOf(date),
                    delta,
                    delta,
                    debitSegment,
                    creditSegment,
                    channelId,
                    java.sql.Date.valueOf(date),
                    delta
            );

            if (rowsAffected == 0) {
                log.warn("Delete ignored — statistics row does not exist for debit: {}, credit: {}, channel: {}, date: {}",
                        debitSegment, creditSegment, channelId, date);
            } else {
                log.info("Upsert successful");
            }

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("CHK_op_count_non_negative")) {
                log.warn("Skipping delete — op_count would go below zero for debit: {}, credit: {}, channel: {}, date: {}",
                        debitSegment, creditSegment, channelId, date);
            } else {
                throw e;
            }
        }
    }
}