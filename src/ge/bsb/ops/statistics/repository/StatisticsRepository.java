package ge.bsb.ops.statistics.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

public class StatisticsRepository {
    private static final String URL = "jdbc:sqlserver://devcluster\\devserv;databaseName=BANK2000;integratedSecurity=true;encrypt=false";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public void upsert(String debitSegment, String creditSegment, int channelId, LocalDate date, String routingKey){
        int delta = routingKey.equals("b6.transaction.create") ? 1 : -1;
        try (Connection con = getConnection()){
            try(PreparedStatement stmt = con.prepareStatement(
                 "MERGE basis.OPS_SEGMENT_STATISTICS_DAVIT AS target\n" +
                         "USING (VALUES (?, ?, ?, ?)) AS source (debit_segment, credit_segment, channel_id, doc_date)\n" +
                         "ON target.debit_segment = source.debit_segment\n" +
                         "AND target.credit_segment = source.credit_segment\n" +
                         "AND target.channel_id = source.channel_id\n" +
                         "AND target.doc_date = source.doc_date\n" +
                         "WHEN MATCHED THEN\n" +
                         "    UPDATE SET count = target.count + ?\n" +
                         "WHEN NOT MATCHED THEN\n" +
                         "    INSERT (debit_segment, credit_segment, channel_id, doc_date, count)\n" +
                         "    VALUES (?, ?, ?, ?, ?);"
            )){
                // USING (VALUES (?, ?, ?, ?)) — source values
                stmt.setString(1, debitSegment);
                stmt.setString(2, creditSegment);
                stmt.setInt(3, channelId);
                stmt.setDate(4, java.sql.Date.valueOf(date));

                // UPDATE SET count = target.count + ?
                stmt.setInt(5, delta);

                // INSERT VALUES (?, ?, ?, ?, ?)
                stmt.setString(6, debitSegment);
                stmt.setString(7, creditSegment);
                stmt.setInt(8, channelId);
                stmt.setDate(9, java.sql.Date.valueOf(date));
                stmt.setInt(10, delta);

                stmt.executeUpdate();
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
