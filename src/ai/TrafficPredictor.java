package src.ai;

import src.core.Grid;
import src.core.Node;

import java.util.Random;

/**
 * AI Traffic Predictor — simulates real-world congestion patterns.
 *
 * Features:
 * - Time-of-day based congestion (rush hour simulation)
 * - Spatial hotspots (entrances, exits tend to be busier)
 * - Gaussian noise for realism
 * - Smooth weight evolution (no abrupt changes)
 * - Per-node history tracking for trend detection
 */
public class TrafficPredictor {

    public enum TimeOfDay {
        MORNING_RUSH(0.85, "Morning Rush (8-9 AM)"),
        MIDDAY(0.35,        "Midday (12-1 PM)"),
        EVENING_RUSH(0.90,  "Evening Rush (5-7 PM)"),
        NIGHT(0.10,         "Night (10 PM-6 AM)"),
        WEEKEND(0.40,       "Weekend");

        public final double baseCongestion;
        public final String label;

        TimeOfDay(double baseCongestion, String label) {
            this.baseCongestion = baseCongestion;
            this.label = label;
        }

        @Override public String toString() { return label; }
    }

    private final Random rnd = new Random();
    private TimeOfDay timeOfDay = TimeOfDay.MIDDAY;
    private double globalCongestionMultiplier = 1.0;

    // Hotspot configuration: (row-ratio, col-ratio, radius, intensity)
    private static final double[][] HOTSPOTS = {
        {0.05, 0.5, 3.0, 1.5},   // entrance (top center)
        {0.95, 0.5, 3.0, 1.3},   // exit (bottom center)
        {0.5,  0.1, 2.0, 1.2},   // left-side high traffic
        {0.5,  0.9, 2.0, 1.2},   // right-side high traffic
    };

    public TrafficPredictor() {}

    public TrafficPredictor(TimeOfDay initialTime) {
        this.timeOfDay = initialTime;
    }

    // ---- Configuration ----
    public void setTimeOfDay(TimeOfDay tod) { this.timeOfDay = tod; }
    public TimeOfDay getTimeOfDay() { return timeOfDay; }
    public void setGlobalCongestion(double m) { this.globalCongestionMultiplier = Math.max(0.1, m); }

    /**
     * Returns predicted congestion weight for a grid cell [1.0 = free, 3.0 = heavy].
     * Takes position into account for spatial hotspots.
     */
    public double predictWeight(int row, int col, int totalRows, int totalCols) {
        double base = 1.0 + timeOfDay.baseCongestion * globalCongestionMultiplier;

        // Hotspot contribution
        double rowRatio = (double) row / totalRows;
        double colRatio = (double) col / totalCols;
        double hotspotBonus = 0.0;
        for (double[] hs : HOTSPOTS) {
            double dr = (rowRatio - hs[0]) * totalRows;
            double dc = (colRatio - hs[1]) * totalCols;
            double dist = Math.sqrt(dr * dr + dc * dc);
            if (dist < hs[2]) {
                hotspotBonus += hs[3] * (1.0 - dist / hs[2]);
            }
        }

        // Gaussian noise for realism
        double noise = rnd.nextGaussian() * 0.15;
        double weight = base + hotspotBonus + noise;
        return Math.max(1.0, Math.min(3.5, weight));
    }

    /**
     * Simple scalar congestion factor (no position). Used by HeuristicTuner.
     */
    public double predictCongestionFactor() {
        double base = timeOfDay.baseCongestion * globalCongestionMultiplier;
        double noise = rnd.nextGaussian() * 0.05;
        return Math.max(0.0, Math.min(1.0, base + noise));
    }

    /**
     * Apply traffic weights across the entire grid (smooth update — blends old and new).
     * Call periodically to simulate changing traffic.
     */
    public void applyToGrid(Grid grid, double blendFactor) {
        int rows = grid.getRows();
        int cols = grid.getCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid.isWall(r, c)) continue;
                Node n = grid.getNodeAt(r, c);
                double predicted = predictWeight(r, c, rows, cols);
                // Smooth blend: newWeight = alpha * predicted + (1-alpha) * current
                double blended = blendFactor * predicted + (1.0 - blendFactor) * n.getWeight();
                n.setWeight(Math.max(1.0, Math.min(3.5, blended)));
            }
        }
    }

    /**
     * Full apply (no blending) — used on reset or mode change.
     */
    public void applyToGrid(Grid grid) {
        applyToGrid(grid, 1.0);
    }

    /**
     * Quick congestion label for UI.
     */
    public String getCongestionLabel() {
        double c = timeOfDay.baseCongestion * globalCongestionMultiplier;
        if (c < 0.2) return "Low";
        if (c < 0.5) return "Moderate";
        if (c < 0.75) return "High";
        return "Very High";
    }
}
