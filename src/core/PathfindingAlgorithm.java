package src.core;

import java.util.List;

/**
 * Interface for pathfinding algorithms (A*, Dijkstra, etc.)
 */
public interface PathfindingAlgorithm {
    /**
     * Find a path from start to goal on the given grid.
     * Returns nodes from start (inclusive) to goal (inclusive), or empty list if no path found.
     */
    List<Node> findPath(Grid grid, Node start, Node goal);

    /**
     * Returns nodes explored during the last search, in visit order.
     */
    List<Node> getExploredNodes();

    /**
     * Returns the name of this algorithm (for UI display).
     */
    default String getName() { return getClass().getSimpleName(); }
}
