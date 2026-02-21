package src.core;

import java.util.*;

/**
 * Grid logic (no UI). Manages nodes, walls, weights, start/goal, parking spots, and reservations.
 */
public class Grid {
    private final int rows;
    private final int cols;
    private final Node[][] nodes;
    private final boolean[][] walls;
    private final boolean[][] reserved;
    private final long[][] reservedUntilMs;

    private Node startNode = null;
    private Node goalNode  = null;

    public Grid(int rows, int cols) {
        if (rows < 1 || cols < 1) throw new IllegalArgumentException("rows/cols must be >= 1");
        this.rows = rows;
        this.cols = cols;
        this.nodes = new Node[rows][cols];
        this.walls = new boolean[rows][cols];
        this.reserved = new boolean[rows][cols];
        this.reservedUntilMs = new long[rows][cols];
        initNodes();
    }

    private void initNodes() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                nodes[r][c] = new Node(r, c);
            }
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }

    public Node getNodeAt(int row, int col) {
        return inBounds(row, col) ? nodes[row][col] : null;
    }

    // ---- Walls ----
    public boolean isWall(int r, int c) {
        return !inBounds(r, c) || walls[r][c];
    }

    public void setWall(int r, int c, boolean wall) {
        if (inBounds(r, c)) walls[r][c] = wall;
    }

    public void toggleWall(int r, int c) {
        if (inBounds(r, c)) walls[r][c] = !walls[r][c];
    }

    // ---- Reservations ----
    public boolean isReserved(int r, int c) {
        if (!inBounds(r, c)) return false;
        if (reservedUntilMs[r][c] != 0L && System.currentTimeMillis() > reservedUntilMs[r][c]) {
            reserved[r][c] = false;
            reservedUntilMs[r][c] = 0L;
        }
        return reserved[r][c];
    }

    public void setReserved(int r, int c, boolean val) {
        if (!inBounds(r, c)) return;
        reserved[r][c] = val;
        if (!val) reservedUntilMs[r][c] = 0L;
    }

    public void reserveTemporary(int r, int c, long durationMs) {
        if (!inBounds(r, c)) return;
        reserved[r][c] = true;
        reservedUntilMs[r][c] = durationMs > 0 ? System.currentTimeMillis() + durationMs : 0L;
    }

    public void clearReservation(int r, int c) { setReserved(r, c, false); }

    public void updateReservations() {
        long now = System.currentTimeMillis();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (reservedUntilMs[r][c] != 0L && now > reservedUntilMs[r][c]) {
                    reserved[r][c] = false;
                    reservedUntilMs[r][c] = 0L;
                }
    }

    // ---- Start / Goal ----
    public Node getStartNode() { return startNode; }
    public Node getGoalNode()  { return goalNode; }

    public void setStartNode(Node n) {
        if (n != null && !inBounds(n.getRow(), n.getCol()))
            throw new IllegalArgumentException("start out of bounds");
        this.startNode = n;
    }

    public void setGoalNode(Node n) {
        if (n != null && !inBounds(n.getRow(), n.getCol()))
            throw new IllegalArgumentException("goal out of bounds");
        this.goalNode = n;
    }

    public void clearStartNode() { startNode = null; }
    public void clearGoalNode()  { goalNode = null; }

    // ---- Bounds ----
    public boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    // ---- Neighbors (4-way, skip walls & reserved) ----
    public List<Node> getNeighbors(Node n) {
        if (n == null) return Collections.emptyList();
        int r = n.getRow(), c = n.getCol();
        List<Node> nb = new ArrayList<>(4);
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (!inBounds(nr, nc) || walls[nr][nc] || isReserved(nr, nc)) continue;
            nb.add(nodes[nr][nc]);
        }
        return nb;
    }

    /** Edge cost: node weight (traffic) + neighbor entry cost */
    public double movementCost(Node from, Node to) {
        if (from == null || to == null) return Double.POSITIVE_INFINITY;
        return to.getWeight();
    }

    // ---- Parking Spot Helpers ----
    public List<Node> getAllParkingSpots() {
        List<Node> spots = new ArrayList<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (nodes[r][c].isParkingSpot()) spots.add(nodes[r][c]);
        return spots;
    }

    public List<Node> getAvailableSpots() {
        List<Node> available = new ArrayList<>();
        for (Node n : getAllParkingSpots())
            if (!n.isOccupied() && !isReserved(n.getRow(), n.getCol()))
                available.add(n);
        return available;
    }

    public int countAvailableSpots() { return getAvailableSpots().size(); }
    public int countTotalSpots()     { return getAllParkingSpots().size(); }

    // ---- Reset ----
    public void resetSearchState() {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) nodes[r][c].resetSearchState();
    }

    public void resetAll(boolean clearStartGoal) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                walls[r][c] = false;
                reserved[r][c] = false;
                reservedUntilMs[r][c] = 0L;
                nodes[r][c].resetSearchState();
                nodes[r][c].setWeight(1.0);
                nodes[r][c].vacate();
            }
        if (clearStartGoal) { startNode = null; goalNode = null; }
    }

    // ---- Random Walls ----
    public void randomWalls(double density, long seed) {
        Random rnd = (seed == Long.MIN_VALUE) ? new Random() : new Random(seed);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                boolean isSpecial = (startNode != null && startNode.equals(nodes[r][c]))
                        || (goalNode != null && goalNode.equals(nodes[r][c]))
                        || nodes[r][c].isParkingSpot();
                if (!isSpecial) walls[r][c] = rnd.nextDouble() < density;
            }
    }

    public void randomWalls(double density) { randomWalls(density, System.nanoTime()); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (startNode != null && startNode.getRow() == r && startNode.getCol() == c) sb.append('S');
                else if (goalNode != null && goalNode.getRow() == r && goalNode.getCol() == c) sb.append('G');
                else if (walls[r][c]) sb.append('#');
                else if (isReserved(r, c)) sb.append('R');
                else if (nodes[r][c].isParkingSpot()) sb.append('P');
                else sb.append('.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
