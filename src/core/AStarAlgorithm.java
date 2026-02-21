package src.core;

import src.ai.HeuristicTuner;

import java.util.*;

/**
 * A* pathfinding algorithm with pluggable heuristic and AI-tuned edge costs.
 *
 * Usage:
 *   AStarAlgorithm astar = new AStarAlgorithm(grid, new Heuristic(Heuristic.Type.MANHATTAN));
 *   List<Node> path = astar.findPath(grid, start, goal);
 */
public class AStarAlgorithm implements PathfindingAlgorithm {

    private final Grid grid;
    private Heuristic heuristic;
    private final HeuristicTuner tuner;
    private final List<Node> exploredNodes = new ArrayList<>();

    // Performance stats from last run
    private long lastRunTimeMs = 0;
    private int lastNodesExplored = 0;
    private double lastPathCost = 0;
    private int lastPathLength = 0;

    public AStarAlgorithm(Grid grid) {
        this(grid, new Heuristic(Heuristic.Type.MANHATTAN));
    }

    public AStarAlgorithm(Grid grid, Heuristic heuristic) {
        if (grid == null) throw new IllegalArgumentException("grid cannot be null");
        this.grid = grid;
        this.heuristic = heuristic;
        this.tuner = new HeuristicTuner();
    }

    @Override
    public String getName() { return "A* (" + heuristic.getType().label + ")"; }

    public void setHeuristic(Heuristic h) { this.heuristic = h; }
    public Heuristic getHeuristic() { return heuristic; }

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

        start.setG(0.0);
        start.setH(heuristic.calculate(start, goal));
        start.setExplored(true);

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::getF));
        open.add(start);

        Set<Node> closed = new HashSet<>();
        int visitCounter = 0;

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (closed.contains(current)) continue;

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

            closed.add(current);

            for (Node nbr : g.getNeighbors(current)) {
                if (closed.contains(nbr)) continue;

                // AI-tuned edge cost: base movement + congestion penalty from HeuristicTuner
                double baseCost = g.movementCost(current, nbr);
                double congestion = Math.max(0, nbr.getWeight() - 1.0) / 2.0;
                double tunedCost = tuner.tuneEdgeCost(baseCost, congestion);

                double tentativeG = current.getG() + tunedCost;

                if (tentativeG < nbr.getG()) {
                    nbr.setParent(current);
                    nbr.setG(tentativeG);
                    nbr.setH(heuristic.calculate(nbr, goal));
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
