package src.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * AppConfig — loads settings from config/app.properties at startup.
 *
 * WHY THIS EXISTS (the concept):
 *   Without a config file, every setting is hardcoded inside Java files.
 *   If you want to change the grid size, you open GridPanel.java, change a number,
 *   and recompile. That's bad — it mixes "what the app does" with "how it's configured."
 *
 *   With a config file:
 *     - Non-developers can change settings (grid size, TTL, occupancy) without touching code
 *     - You can have different configs for dev/test/production
 *     - Interviewers see you understand "separation of concerns"
 *
 * HOW TO USE:
 *   AppConfig cfg = AppConfig.load();      // reads the file once
 *   int rows = cfg.getGridRows();          // use typed getters
 *
 * FALLBACK BEHAVIOUR:
 *   If config/app.properties is missing, all getters return safe defaults.
 *   The app never crashes because of a missing config file.
 */
public class AppConfig {

    private final Properties props = new Properties();

    // ---- Private constructor: use AppConfig.load() ----
    private AppConfig() {}

    /**
     * Load config from config/app.properties (relative to working directory).
     * Falls back to built-in defaults if the file is missing.
     */
    public static AppConfig load() {
        AppConfig cfg = new AppConfig();

        // Try loading from file
        try (InputStream in = new FileInputStream("config/app.properties")) {
            cfg.props.load(in);
            System.out.println("[AppConfig] Loaded config/app.properties");
        } catch (IOException e) {
            // File not found — use defaults silently
            System.out.println("[AppConfig] config/app.properties not found — using defaults.");
        }

        return cfg;
    }

    // ============================================================
    //  Typed Getters — each reads a key and falls back to a default
    // ============================================================

    // ---- Grid ----
    public int getGridRows()     { return getInt("grid.rows",      25); }
    public int getGridCols()     { return getInt("grid.cols",      45); }
    public int getCellSize()     { return getInt("grid.cell_size", 22); }

    // ---- Parking ----
    public double getInitialOccupancy() { return getDouble("parking.initial_occupancy", 0.45); }
    public long   getOccupancySeed()    {
        long seed = getLong("parking.occupancy_seed", -1L);
        return seed == -1L ? System.currentTimeMillis() : seed;
    }

    // ---- Reservation ----
    /** Returns TTL in milliseconds */
    public long getReservationTtlMs() {
        return getLong("reservation.ttl_seconds", 300L) * 1000L;
    }

    // ---- Traffic ----
    public String getInitialTrafficMode()    { return getString("traffic.initial_mode",       "MIDDAY"); }
    public int    getTrafficUpdateIntervalMs(){ return getInt("traffic.update_interval_ms",   5000); }
    public double getTrafficBlendFactor()    { return getDouble("traffic.blend_factor",       0.25); }

    // ---- Allocator ----
    public int    getAllocatorTopK()              { return getInt("allocator.top_k",                    5); }
    public double getCongestionPenaltyScale()     { return getDouble("allocator.congestion_penalty_scale", 1.5); }

    // ---- Pathfinding ----
    public String getDefaultAlgorithm()   { return getString("pathfinding.default_algorithm", "ASTAR_MANHATTAN"); }
    public int    getDefaultSpeedMs()     { return getInt("pathfinding.default_speed_ms",     40); }

    // ---- UI ----
    public boolean isShowHeatmap() { return getBool("ui.show_heatmap", true); }

    // ============================================================
    //  Internal helpers — read + parse + fallback
    // ============================================================

    private String getString(String key, String fallback) {
        return props.getProperty(key, fallback).trim();
    }

    private int getInt(String key, int fallback) {
        try { return Integer.parseInt(getString(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) {
            System.err.printf("[AppConfig] Bad int for key '%s', using default %d%n", key, fallback);
            return fallback;
        }
    }

    private double getDouble(String key, double fallback) {
        try { return Double.parseDouble(getString(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) {
            System.err.printf("[AppConfig] Bad double for key '%s', using default %.2f%n", key, fallback);
            return fallback;
        }
    }

    private long getLong(String key, long fallback) {
        try { return Long.parseLong(getString(key, String.valueOf(fallback))); }
        catch (NumberFormatException e) {
            System.err.printf("[AppConfig] Bad long for key '%s', using default %d%n", key, fallback);
            return fallback;
        }
    }

    private boolean getBool(String key, boolean fallback) {
        String val = getString(key, String.valueOf(fallback));
        return val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes") || val.equals("1");
    }

    @Override
    public String toString() {
        return String.format(
            "AppConfig{rows=%d, cols=%d, cellSize=%d, occupancy=%.0f%%, ttl=%ds, traffic=%s, topK=%d}",
            getGridRows(), getGridCols(), getCellSize(),
            getInitialOccupancy() * 100, getReservationTtlMs() / 1000,
            getInitialTrafficMode(), getAllocatorTopK()
        );
    }
}
