package src.core;

/**
 * Heuristic functions for A* algorithm.
 * Supports Manhattan, Euclidean, and Chebyshev distances.
 */
public class Heuristic {

    public enum Type {
        MANHATTAN("Manhattan"),
        EUCLIDEAN("Euclidean"),
        CHEBYSHEV("Chebyshev"),
        WEIGHTED("Weighted Manhattan");

        public final String label;
        Type(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    private Type type;
    private double weight; // for weighted heuristic (inflated A*)

    public Heuristic(Type type) {
        this.type = type;
        this.weight = 1.0;
    }

    public Heuristic(Type type, double weight) {
        this.type = type;
        this.weight = Math.max(1.0, weight);
    }

    public double calculate(Node a, Node b) {
        if (a == null || b == null) return 0;
        int dr = Math.abs(a.getRow() - b.getRow());
        int dc = Math.abs(a.getCol() - b.getCol());
        switch (type) {
            case MANHATTAN:  return dr + dc;
            case EUCLIDEAN:  return Math.sqrt(dr * dr + dc * dc);
            case CHEBYSHEV:  return Math.max(dr, dc);
            case WEIGHTED:   return weight * (dr + dc);
            default:         return dr + dc;
        }
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public double getWeight() { return weight; }
    public void setWeight(double w) { this.weight = Math.max(1.0, w); }
}
