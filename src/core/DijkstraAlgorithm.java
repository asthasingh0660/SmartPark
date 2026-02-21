package src.core;

import src.ai.HeuristicTuner;

import java.util.*;

/**
 * Dijkstra's shortest path algorithm — guaranteed optimal, explores more than A*.
 * Used as a comparison baseline in the Smart Parking demo.
 *
 * Usage:
 *   DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(grid);
 *   List<Node> path = dijkstra.findPath(grid, start, goal);
 */
public class DijkstraAlgorithm implements PathfindingAlgorithm {

    private final Grid grid;
    private final HeuristicTuner tuner;
    private final List<Node> exploredNodes = new ArrayList<>();

    // Performance stats
    private long lastRunTimeMs = 0;
    private int lastNodesExplored = 0;
    private double lastPathCost = 0;
    private int lastPathLength = 0;

    public DijkstraAlgorithm(Grid grid) {
        if (grid == null) throw new IllegalArgumentException("grid cannot be null");
        this.grid = grid;
        this.tuner = new HeuristicTuner();
    }

    @Override
    public String getName() { return "Dijkstra"; }

    @Override
    public List<Node> findPath(Grid gridArg, Node start, Node goal) {
        Grid g = (gridArg != null) ? gridArg : this.grid;
        exploredNodes.clear();
        lastRunTimeMs = 0; lastNodesExplored = 0; lastPathCost = 0; lastPathLength = 0;

        if (g == null || start == null || goal == null) return Collections.emptyList();
        if (!g.inBounds(start.getRow(), start.getCol()) || !g.inBounds(goal.getRow(), goal.getCol()))
            return Collections.emptyList();
        if (g.isWall(start.getRow(), start.getCol()) || g.isWall(goal.getRow(), goal.getCol()))
            return Collections.emptyList();

        g.resetSearchState();
        long t0 = System.currentTimeMillis();

        // Dijkstra uses g-cost only (no heuristic) — we order by g
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::getG));
        start.setG(0.0);
        start.setH(0.0); // no heuristic
        start.setExplored(true);
        open.add(start);

        Set<Node> visited = new HashSet<>();
        int visitCounter = 0;

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (visited.contains(current)) continue;

            visited.add(current);
            current.setVisitOrder(visitCounter++);
            current.setExplored(true);
            exploredNodes.add(current);

            if (current.equals(goal)) {
                List<Node> path = reconstructPath(current);
                lastRunTimeMs = System.currentTimeMillis() - t0;
                lastNodesExplored = exploredNodes.size();
                lastPathLength = path.size();
                lastPathCost = current.getG();
                return path;
            }

            for (Node nbr : g.getNeighbors(current)) {
                if (visited.contains(nbr)) continue;

                double baseCost = g.movementCost(current, nbr);
                double congestion = Math.max(0, nbr.getWeight() - 1.0) / 2.0;
                double tunedCost = tuner.tuneEdgeCost(baseCost, congestion);

                double tentativeG = current.getG() + tunedCost;
                if (tentativeG < nbr.getG()) {
                    nbr.setParent(current);
                    nbr.setG(tentativeG);
                    nbr.setH(0.0); // Dijkstra: no heuristic component
                    open.remove(nbr);
                    open.add(nbr);
                }
            }
        }

        lastRunTimeMs = System.currentTimeMillis() - t0;
        lastNodesExplored = exploredNodes.size();
        return Collections.emptyList();
    }

    private List<Node> reconstructPath(Node end) {
        LinkedList<Node> path = new LinkedList<>();
        Node cur = end;
        while (cur != null) {
            path.addFirst(cur);
            cur = cur.getParent();
        }
        return path;
    }

    @Override
    public List<Node> getExploredNodes() { return Collections.unmodifiableList(exploredNodes); }

    // ---- Stats ----
    public long getLastRunTimeMs()    { return lastRunTimeMs; }
    public int getLastNodesExplored() { return lastNodesExplored; }
    public double getLastPathCost()   { return lastPathCost; }
    public int getLastPathLength()    { return lastPathLength; }
}
