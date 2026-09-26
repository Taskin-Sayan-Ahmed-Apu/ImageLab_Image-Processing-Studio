package com.imagelab.data;

import com.imagelab.util.Log;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String url;

    public DatabaseManager() {
        this.url = ConfigLoader.get().getDatabase().getUrl();
        init();
    }

    private void init() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    source_image TEXT,
                    details TEXT
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS image_metadata (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    filepath TEXT NOT NULL,
                    width INTEGER,
                    height INTEGER,
                    format TEXT,
                    created_at TEXT
                )
            """);
            Log.success("SQLite ready  →  " + url);
        } catch (SQLException e) {
            Log.error("DB init failed: " + e.getMessage());
        }
    }

    public void addHistory(HistoryEntry h) {
        String sql = "INSERT INTO history(operation, timestamp, source_image, details) VALUES(?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, h.getOperation());
            ps.setString(2, h.getTimestamp());
            ps.setString(3, h.getSourceImage());
            ps.setString(4, h.getDetails());
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.error("DB insert failed: " + e.getMessage());
        }
    }

    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM history ORDER BY id DESC")) {
            while (rs.next()) {
                HistoryEntry h = new HistoryEntry();
                h.setId(rs.getInt("id"));
                h.setOperation(rs.getString("operation"));
                h.setTimestamp(rs.getString("timestamp"));
                h.setSourceImage(rs.getString("source_image"));
                h.setDetails(rs.getString("details"));
                list.add(h);
            }
        } catch (SQLException e) {
            Log.error("DB read failed: " + e.getMessage());
        }
        return list;
    }

    public void clearHistory() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM history");
            Log.warn("History cleared");
        } catch (SQLException e) {
            Log.error("DB clear failed: " + e.getMessage());
        }
    }

    public void addMetadata(String path, int w, int h, String format) {
        String sql = "INSERT INTO image_metadata(filepath, width, height, format, created_at) VALUES(?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setInt(2, w);
            ps.setInt(3, h);
            ps.setString(4, format);
            ps.setString(5, java.time.LocalDateTime.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.error("DB metadata insert failed: " + e.getMessage());
        }
    }
}