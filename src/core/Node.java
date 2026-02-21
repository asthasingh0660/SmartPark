package src.core;

import java.util.Objects;

/**
 * Represents a single cell on the grid.
 * Extended with ParkingSpot metadata for the Smart Parking use case.
 */
public class Node implements Comparable<Node> {
    private final int row;
    private final int col;

    // --- Pathfinding costs ---
    private double g = Double.POSITIVE_INFINITY;
    private double h = 0.0;
    private double f = Double.POSITIVE_INFINITY;
    private double weight = 1.0; // traversal cost multiplier (traffic weight)
    private Node parent = null;

    // --- Visualization ---
    private int visitOrder = -1;
    private boolean explored = false;

    // --- Parking Spot Metadata ---
    public enum SpotType { NONE, REGULAR, HANDICAPPED, EV_CHARGING, VIP }
    private SpotType spotType = SpotType.NONE;
    private boolean parkingSpot = false;
    private boolean occupied = false;
    private String occupantId = null;      // who reserved/parked here
    private long occupiedUntilMs = 0L;     // 0 = permanent

    public Node(int row, int col) {
        this.row = row;
        this.col = col;
    }

    // ---- Position ----
    public int getRow() { return row; }
    public int getCol() { return col; }

    // ---- g / h / f ----
    public double getG() { return g; }
    public void setG(double g) { this.g = g; updateF(); }
    public double getH() { return h; }
    public void setH(double h) { this.h = h; updateF(); }
    public double getF() { return f; }
    private void updateF() { this.f = (g == Double.POSITIVE_INFINITY) ? Double.POSITIVE_INFINITY : this.g + this.h; }

    // ---- Weight (traffic / congestion) ----
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = Math.max(1.0, weight); }

    // ---- Parent ----
    public Node getParent() { return parent; }
    public void setParent(Node parent) { this.parent = parent; }

    // ---- Visualization ----
    public int getVisitOrder() { return visitOrder; }
    public void setVisitOrder(int order) { this.visitOrder = order; }
    public boolean isExplored() { return explored; }
    public void setExplored(boolean explored) { this.explored = explored; }

    // ---- Parking Spot Properties ----
    public boolean isParkingSpot() { return parkingSpot; }
    public void setParkingSpot(boolean parkingSpot) { this.parkingSpot = parkingSpot; }

    public SpotType getSpotType() { return spotType; }
    public void setSpotType(SpotType spotType) {
        this.spotType = spotType;
        this.parkingSpot = (spotType != SpotType.NONE);
    }

    public boolean isOccupied() {
        if (occupiedUntilMs > 0 && System.currentTimeMillis() > occupiedUntilMs) {
            occupied = false;
            occupantId = null;
            occupiedUntilMs = 0L;
        }
        return occupied;
    }

    public void setOccupied(boolean occupied, String occupantId, long durationMs) {
        this.occupied = occupied;
        this.occupantId = occupied ? occupantId : null;
        this.occupiedUntilMs = (occupied && durationMs > 0) ? System.currentTimeMillis() + durationMs : 0L;
    }

    public void vacate() {
        this.occupied = false;
        this.occupantId = null;
        this.occupiedUntilMs = 0L;
    }

    public String getOccupantId() { return occupantId; }

    /** Base cost for entering this node (used by algorithms) */
    public double getEntryCost() {
        double base = weight;
        // Penalize congested areas for pathfinding
        return base;
    }

    // ---- Search State Reset ----
    public void resetSearchState() {
        this.g = Double.POSITIVE_INFINITY;
        this.h = 0.0;
        this.f = Double.POSITIVE_INFINITY;
        this.parent = null;
        this.visitOrder = -1;
        this.explored = false;
    }

    @Override
    public int compareTo(Node other) {
        return Double.compare(this.f, other.f);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Node)) return false;
        Node n = (Node) o;
        return n.row == this.row && n.col == this.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return String.format("Node(%d,%d)[g=%.2f h=%.2f f=%.2f w=%.2f type=%s occ=%b]",
                row, col, g, h, f, weight, spotType, occupied);
    }
}
