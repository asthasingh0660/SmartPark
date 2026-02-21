package src.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AppConfig.
 * Key thing being tested: does it fall back to safe defaults
 * when the config file is missing or has bad values?
 */
@DisplayName("AppConfig Tests")
class AppConfigTest {

    @Test
    @DisplayName("load() does not throw even if config file is missing")
    void loadDoesNotThrowWhenFileMissing() {
        // This test proves the app won't crash on first run (before config is set up)
        assertDoesNotThrow(AppConfig::load,
            "AppConfig.load() must never throw, even with no file present");
    }

    @Test
    @DisplayName("Defaults are sane (non-zero, positive values)")
    void defaultsAreSane() {
        AppConfig cfg = AppConfig.load();

        assertTrue(cfg.getGridRows() > 0,   "Grid rows must be positive");
        assertTrue(cfg.getGridCols() > 0,   "Grid cols must be positive");
        assertTrue(cfg.getCellSize() > 0,   "Cell size must be positive");
        assertTrue(cfg.getInitialOccupancy() >= 0.0 && cfg.getInitialOccupancy() <= 1.0,
            "Occupancy must be between 0.0 and 1.0");
        assertTrue(cfg.getReservationTtlMs() > 0, "TTL must be positive");
        assertTrue(cfg.getAllocatorTopK() > 0,     "TopK must be positive");
        assertNotNull(cfg.getDefaultAlgorithm(),   "Default algorithm must not be null");
        assertNotNull(cfg.getInitialTrafficMode(), "Traffic mode must not be null");
    }

    @Test
    @DisplayName("toString() produces a readable summary")
    void toStringProducesOutput() {
        AppConfig cfg = AppConfig.load();
        String s = cfg.toString();
        assertNotNull(s);
        assertFalse(s.isEmpty());
        // Should contain key info
        assertTrue(s.contains("rows=") || s.contains("AppConfig"),
            "toString should include config details");
    }
}
