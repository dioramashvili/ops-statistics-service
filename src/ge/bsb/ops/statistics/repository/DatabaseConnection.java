package ge.bsb.ops.statistics.repository;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    private static final String URL;

    static {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MSSQL JDBC Driver not found", e);
        }

        Properties props = new Properties();
        try {
            props.load(new FileInputStream("config.properties"));
        } catch (IOException e) {
            try {
                props.load(DatabaseConnection.class.getClassLoader().getResourceAsStream("config.properties"));
            } catch (IOException ex) {
                throw new RuntimeException("Could not load config.properties", ex);
            }
        }
        URL = props.getProperty("sqlserver.url");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}