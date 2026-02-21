package src.utils;

import src.core.Grid;
import src.core.Node;

/**
 * Generates realistic parking lot layouts on a Grid.
 *
 * Layout:
 * - Outer walls (border)
 * - Driving lanes (horizontal corridors)
 * - Parking bays on either side of lanes
 * - Entrance at top-center, exit at bottom-center
 * - Special spots: handicapped (near entrance), EV charging, VIP
 */
public class ParkingLotLayout {

    public static void apply(Grid grid) {
        int rows = grid.getRows();
        int cols = grid.getCols();

        // --- 1. Border walls ---
        for (int r = 0; r < rows; r++) {
            grid.setWall(r, 0, true);
            grid.setWall(r, cols - 1, true);
        }
        for (int c = 0; c < cols; c++) {
            grid.setWall(0, c, true);
            grid.setWall(rows - 1, c, true);
        }

        // --- 2. Fill interior with walls (all parking bays start as walls) ---
        for (int r = 1; r < rows - 1; r++)
            for (int c = 1; c < cols - 1; c++)
                grid.setWall(r, c, true);

        // --- 3. Carve driving lanes (horizontal) and parking bays ---
        // Lanes every 4 rows: rows 2, 6, 10, 14, ...
        int laneSpacing = 4;
        for (int laneRow = 2; laneRow < rows - 2; laneRow += laneSpacing) {
            // Carve full horizontal lane
            for (int c = 1; c < cols - 1; c++) {
                grid.setWall(laneRow, c, false);
            }

            // Carve parking bays ABOVE the lane (if space)
            int bayRowAbove = laneRow - 1;
            if (bayRowAbove >= 1) {
                for (int c = 2; c < cols - 2; c += 2) {
                    grid.setWall(bayRowAbove, c, false);
                    Node n = grid.getNodeAt(bayRowAbove, c);
                    if (n != null) n.setSpotType(Node.SpotType.REGULAR);
                }
            }

            // Carve parking bays BELOW the lane (if space)
            int bayRowBelow = laneRow + 1;
            if (bayRowBelow < rows - 1) {
                for (int c = 2; c < cols - 2; c += 2) {
                    grid.setWall(bayRowBelow, c, false);
                    Node n = grid.getNodeAt(bayRowBelow, c);
                    if (n != null) n.setSpotType(Node.SpotType.REGULAR);
                }
            }
        }

        // --- 4. Vertical connector lane in center ---
        int centerCol = cols / 2;
        for (int r = 1; r < rows - 1; r++) {
            grid.setWall(r, centerCol, false);
        }

        // --- 5. Entrance (top) and Exit (bottom) ---
        // Entrance: top wall, column center
        grid.setWall(0, centerCol - 1, false);
        grid.setWall(0, centerCol, false);
        grid.setWall(0, centerCol + 1, false);
        // Exit: bottom wall
        grid.setWall(rows - 1, centerCol - 1, false);
        grid.setWall(rows - 1, centerCol, false);
        grid.setWall(rows - 1, centerCol + 1, false);

        // --- 6. Special spots: Handicapped (near entrance, first bay row) ---
        int firstBayRow = 3; // first bay row below top lane
        for (int c = 2; c <= 6 && c < cols - 2; c += 2) {
            Node n = grid.getNodeAt(firstBayRow, c);
            if (n != null && n.isParkingSpot()) {
                n.setSpotType(Node.SpotType.HANDICAPPED);
            }
        }

        // --- 7. EV Charging spots (last bay row, left side) ---
        int lastLaneRow = 2 + ((rows - 4) / laneSpacing) * laneSpacing;
        int evBayRow = lastLaneRow + 1;
        if (evBayRow < rows - 1) {
            for (int c = 2; c <= 8 && c < cols - 2; c += 2) {
                grid.setWall(evBayRow, c, false);
                Node n = grid.getNodeAt(evBayRow, c);
                if (n != null) n.setSpotType(Node.SpotType.EV_CHARGING);
            }
        }

        // --- 8. VIP spots (right side, first bay row) ---
        for (int c = cols - 4; c > cols - 10 && c > 1; c -= 2) {
            Node n = grid.getNodeAt(firstBayRow, c);
            if (n != null && n.isParkingSpot()) {
                n.setSpotType(Node.SpotType.VIP);
            }
        }

        System.out.printf("[Layout] Parking lot applied: %d total spots%n",
                grid.getAllParkingSpots().size());
    }

    /**
     * Mark some spots as occupied (simulates an existing state).
     */
    public static void randomlyOccupy(Grid grid, double occupancyRate, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        for (Node spot : grid.getAllParkingSpots()) {
            if (rnd.nextDouble() < occupancyRate) {
                spot.setOccupied(true, "sim-car-" + rnd.nextInt(999), 0L);
            }
        }
        System.out.printf("[Layout] Occupied %d of %d spots (%.0f%%)%n",
                (int)(grid.getAllParkingSpots().size() * occupancyRate),
                grid.getAllParkingSpots().size(), occupancyRate * 100);
    }
}
