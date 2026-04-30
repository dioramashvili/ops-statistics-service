package ge.bsb.ops.statistics.service;

import ge.bsb.ops.statistics.repository.DatabaseConnection;

import java.sql.*;

public class SegmentResolver {
    DatabaseConnection databaseConnection = new DatabaseConnection();

    public String resolve(int customerId) {
        if (customerId == 0) return "N/A";

        try (Connection con = databaseConnection.getConnection()) {
            if (isJuridical(con, customerId)) return "Company";

            if (hasAttribute(con, customerId, "UNIQUE_BANKER")) return "Unique";
            if (hasAttribute(con, customerId, "PREMIUM_BANKER")) return "Premium";

            return "Mass";
        } catch (SQLException e) {
            e.printStackTrace();
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
