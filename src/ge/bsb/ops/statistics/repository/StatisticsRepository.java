package ge.bsb.ops.statistics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

public class StatisticsRepository {
    private static final Logger log = LoggerFactory.getLogger(StatisticsRepository.class);
    DatabaseConnection databaseConnection = new DatabaseConnection();

    public void upsert(String debitSegment, String creditSegment, int channelId, LocalDate date, String routingKey) {
        int delta = routingKey.equals("b6.transaction.create") ? 1 : -1;

        log.info("Upserting stats - debit: {}, credit: {}, channel: {}, date: {}, delta: {}", debitSegment, creditSegment, channelId, date, delta);

        try (Connection con = databaseConnection.getConnection()) {
            try (PreparedStatement stmt = con.prepareStatement(
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
                            """)) {
                // USING (VALUES (?, ?, ?, ?)) — source values
                stmt.setString(1, debitSegment);
                stmt.setString(2, creditSegment);
                stmt.setInt(3, channelId);
                stmt.setDate(4, java.sql.Date.valueOf(date));

                // UPDATE SET count = target.count + ?
                stmt.setInt(5, delta);
                stmt.setInt(6, delta);


                // INSERT VALUES (?, ?, ?, ?, ?)
                stmt.setString(7, debitSegment);
                stmt.setString(8, creditSegment);
                stmt.setInt(9, channelId);
                stmt.setDate(10, java.sql.Date.valueOf(date));
                stmt.setInt(11, delta);

                stmt.executeUpdate();
                log.info("Upsert successful");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("CHK_op_count_non_negative")) {
                log.warn("Skipping delete — op_count would go below zero for debit: {}, credit: {}, channel: {}, date: {}", debitSegment, creditSegment, channelId, date);
            } else {
                log.error("Upsert failed", e);
                throw new RuntimeException(e);
            }
        }
    }
}
