package ge.bsb.ops.statistics.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    static Properties props = new Properties();

    private static final String URL =props.getProperty("sqlserver.url");

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
