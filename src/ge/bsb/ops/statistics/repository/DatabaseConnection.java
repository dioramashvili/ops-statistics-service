package ge.bsb.ops.statistics.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:sqlserver://devcluster\\devserv;databaseName=BANK2000;integratedSecurity=true;encrypt=false";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
