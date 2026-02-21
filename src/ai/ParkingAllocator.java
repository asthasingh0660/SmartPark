package src.ai;

import src.core.*;

import java.util.*;

/**
 * AI Parking Allocator — finds the best available parking spot for a driver.
 *
 * Combines:
 * - Traffic prediction (avoid congested routes)
 * - Heuristic tuning (score spots intelligently)
 * - Pathfinding (A* or Dijkstra to reach the spot)
 * - Spot type matching (handicapped, EV, VIP, regular)
 *
 * This is the central AI component of the Smart Parking system.
 */
public class ParkingAllocator {

    public enum DriverType { REGULAR, HANDICAPPED, EV_OWNER, VIP }

    public static class AllocationResult {
        public final Node bestSpot;
        public final List<Node> path;
        public final List<Node> exploredNodes;
        public final double score;
        public final String algorithmUsed;
        public final long computeTimeMs;
        public final int nodesExplored;
        public final double pathCost;

        public AllocationResult(Node bestSpot, List<Node> path, List<Node> exploredNodes,
                                double score, String algorithmUsed, long computeTimeMs,
                                int nodesExplored, double pathCost) {
            this.bestSpot = bestSpot;
            this.path = path;
            this.exploredNodes = exploredNodes;
            this.score = score;
            this.algorithmUsed = algorithmUsed;
            this.computeTimeMs = computeTimeMs;
            this.nodesExplored = nodesExplored;
            this.pathCost = pathCost;
        }

        public boolean hasPath() { return path != null && !path.isEmpty(); }
    }

    private final Grid grid;
    private final TrafficPredictor predictor;
    private final HeuristicTuner tuner;

    // Config
    private int topK = 5;                    // evaluate top-K candidate spots
    private boolean preferCloser = true;

    public ParkingAllocator(Grid grid, TrafficPredictor predictor) {
        this.grid = grid;
        this.predictor = predictor;
        this.tuner = new HeuristicTuner();
    }

    public void setTopK(int k) { this.topK = Math.max(1, k); }
    public HeuristicTuner getTuner() { return tuner; }

    /**
     * Main entry point: find the best spot and path for a driver.
     *
     * @param driverStart  Driver's current position
     * @param driverType   Type of driver (affects spot preference)
     * @param algorithm    Pathfinding algorithm to use
     * @return AllocationResult with best spot, path, and stats
     */
    public AllocationResult allocate(Node driverStart, DriverType driverType,
                                      PathfindingAlgorithm algorithm) {
        long t0 = System.currentTimeMillis();

        if (driverStart == null) return null;

        List<Node> available = grid.getAvailableSpots();
        if (available.isEmpty()) return null;

        // Filter by driver type preference
        List<Node> preferred = filterByType(available, driverType);
        List<Node> candidates = preferred.isEmpty() ? available : preferred;

        // Score candidates
        candidates.sort(Comparator.comparingDouble(spot -> scoreCandidate(spot, driverStart, driverType)));

        // Try top-K candidates, pick first with a valid path
        Node bestSpot = null;
        List<Node> bestPath = null;
        List<Node> bestExplored = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < Math.min(topK, candidates.size()); i++) {
            Node candidate = candidates.get(i);
            grid.setGoalNode(candidate);

            List<Node> path = algorithm.findPath(grid, driverStart, candidate);
            if (!path.isEmpty()) {
                double score = scoreCandidate(candidate, driverStart, driverType);
                if (bestSpot == null || score < bestScore) {
                    bestSpot = candidate;
                    bestPath = new ArrayList<>(path);
                    bestExplored = new ArrayList<>(algorithm.getExploredNodes());
                    bestScore = score;
                }
                break; // first valid path from sorted list is already best
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        int explored = bestExplored == null ? 0 : bestExplored.size();
        double pathCost = (bestPath != null && bestSpot != null) ? bestSpot.getG() : 0;

        return new AllocationResult(bestSpot, bestPath, bestExplored,
                bestScore, algorithm.getName(), elapsed, explored, pathCost);
    }

    /** Score a candidate spot (lower = better) */
    private double scoreCandidate(Node spot, Node from, DriverType driverType) {
        double manhattan = Math.abs(spot.getRow() - from.getRow()) + Math.abs(spot.getCol() - from.getCol());
        double weight = spot.getWeight();
        boolean isHandicapped = (driverType == DriverType.HANDICAPPED)
                && (spot.getSpotType() == Node.SpotType.HANDICAPPED);
        boolean isEV = (driverType == DriverType.EV_OWNER)
                && (spot.getSpotType() == Node.SpotType.EV_CHARGING);
        return tuner.scoreSpot(manhattan, weight, isHandicapped, isEV);
    }

    /** Filter spots to those matching driver type preference */
    private List<Node> filterByType(List<Node> spots, DriverType driverType) {
        Node.SpotType preferred = switch (driverType) {
            case HANDICAPPED -> Node.SpotType.HANDICAPPED;
            case EV_OWNER    -> Node.SpotType.EV_CHARGING;
            case VIP         -> Node.SpotType.VIP;
            default          -> Node.SpotType.REGULAR;
        };
        List<Node> matching = new ArrayList<>();
        for (Node n : spots) if (n.getSpotType() == preferred) matching.add(n);
        return matching;
    }

    /**
     * Find nearest available spot to a position (simple, fast).
     */
    public Node findNearestAvailable(Node from, DriverType driverType) {
        List<Node> available = filterByType(grid.getAvailableSpots(), driverType);
        if (available.isEmpty()) available = grid.getAvailableSpots();
        return available.stream()
                .min(Comparator.comparingDouble(n ->
                        Math.abs(n.getRow() - from.getRow()) + Math.abs(n.getCol() - from.getCol())))
                .orElse(null);
    }
}
