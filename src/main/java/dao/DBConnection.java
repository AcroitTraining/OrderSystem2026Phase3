package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import config.Config;

public class DBConnection {

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBCドライバを読み込めませんでした", e);
        }
        return DriverManager.getConnection(
                Config.getDbUrl(),
                Config.getDbUser(),
                Config.getDbPassword()
        );
    }
}