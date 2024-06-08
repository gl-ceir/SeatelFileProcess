package com.imsi_main.database;

import java.sql.*;
import java.util.logging.Logger;

public class Database {

    private static final Logger logger = Logger.getLogger(Database.class.getName());
    private Connection connection;

    public Database(String url, String user, String password) {
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            logger.severe("Failed to connect to the database: " + e.getMessage());
        }
    }

    public String getImsi(String msisdn) {
        String imsi = null;
        String query = "SELECT imsi FROM active_msisdn_list WHERE msisdn = ?"; // Replace 'your_table' with the actual table name

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, msisdn);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    imsi = rs.getString("imsi");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get IMSI from database: " + e.getMessage());
        }

        return imsi;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.severe("Failed to close the database connection: " + e.getMessage());
        }
    }
}

