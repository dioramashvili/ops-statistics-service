package ge.bsb.ops.statistics.service;

import ge.bsb.ops.statistics.consumer.RabbitMQConsumer;
import ge.bsb.ops.statistics.repository.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SegmentResolver {
    private static final Logger log = LoggerFactory.getLogger(SegmentResolver.class);
    DatabaseConnection databaseConnection = new DatabaseConnection();



    public String resolve(int customerId) {
        if (customerId == 0) return "N/A";



        try (Connection con = databaseConnection.getConnection()) {
            String segment;

            if (isJuridical(con, customerId)) segment = "Company";
            else if (hasAttribute(con, customerId, "UNIQUE_BANKER")) segment = "Unique";
            else if (hasAttribute(con, customerId, "PREMIUM_BANKER")) segment = "Premium";
            else segment = "Mass";

            log.info("Resolved segment for customerId: {} -> {}", customerId, segment);

            return segment;

        } catch (SQLException e) {
            log.error("Failed to resolve segment for customerId: {}", customerId, e);
        }
        return "N/A";
    }
    private boolean isJuridical(Connection con, int customerId) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "SELECT IS_JURIDICAL FROM dbo.CLIENTS WHERE CLIENT_NO = ?")) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("IS_JURIDICAL") == 1;
                }
            }
        }
        return false;
    }

    private boolean hasAttribute(Connection con, int customerId, String attribCode) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "SELECT 1 FROM dbo.CLIENT_ATTRIBUTES WHERE CLIENT_NO = ? AND ATTRIB_CODE = ?")) {
            stmt.setInt(1, customerId);
            stmt.setString(2, attribCode);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
