package com.typeahead.loader;

import com.opencsv.CSVReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatasetLoader {

    public static void main(String[] args) throws Exception {
        String csvPath = "data/queries.csv";
        String dbUrl = "jdbc:sqlite:typeahead.db";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS queries (" +
                "query TEXT PRIMARY KEY, count INTEGER DEFAULT 0, last_searched REAL DEFAULT 0)"
            );
        }

        String sql = "INSERT INTO queries (query, count, last_searched) VALUES (?, ?, 0) " +
                     "ON CONFLICT(query) DO UPDATE SET count = count + ?";

        int loaded = 0;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             CSVReader reader = new CSVReader(new FileReader(csvPath))) {

            conn.setAutoCommit(true);
            String[] line;
            boolean firstLine = true;

            while ((line = reader.readNext()) != null) {
                if (firstLine) { firstLine = false; continue; }
                if (line.length < 2) continue;

                String query = line[0].trim().toLowerCase();
                int count;
                try { count = Integer.parseInt(line[1].trim()); }
                catch (NumberFormatException e) { continue; }

                pstmt.setString(1, query);
                pstmt.setInt(2, count);
                pstmt.setInt(3, count);
                pstmt.executeUpdate();
                loaded++;

                if (loaded % 1000 == 0) {
                    System.out.println("[LOAD] Loaded " + loaded + " rows...");
                }
            }
        }

        System.out.println("[LOAD] Done. Total: " + loaded + " rows.");

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM queries");
            rs.next();
            System.out.println("[VERIFY] Rows in DB: " + rs.getInt(1));
        }
    }
}
