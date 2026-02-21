package src.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for DijkstraAlgorithm.
 *
 * WHY TEST DIJKSTRA SEPARATELY FROM A*?
 * Even though they're similar, bugs could exist in one and not the other.
 * For example: Dijkstra should ALWAYS find the optimal path (it has no
 * heuristic to mislead it). A* can be suboptimal with certain heuristics.
 * Testing both proves they each behave correctly for their own guarantees.
 */
@DisplayName("Dijkstra Algorithm Tests")
class DijkstraAlgorithmTest {

    private Grid grid;
    private DijkstraAlgorithm dijkstra;

    @BeforeEach
    void setUp() {
        grid     = new Grid(7, 7);
        dijkstra = new DijkstraAlgorithm(grid);
    }

    // ================================================================
    //  GROUP 1: Basic correctness
    // ================================================================
    @Nested
    @DisplayName("Basic Correctness")
    class BasicCorrectness {

        @Test
        @DisplayName("Finds a path on a fully open grid")
        void findsPathOnOpenGrid() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> path = dijkstra.findPath(grid, start, goal);

            assertFalse(path.isEmpty(), "Dijkstra must find a path on an open grid");
            assertEquals(start, path.get(0));
            assertEquals(goal,  path.get(path.size() - 1));
        }

        @Test
        @DisplayName("Path is optimal (no shorter path exists)")
        void pathIsOptimal() {
            // On an unweighted open grid, optimal = Manhattan distance steps
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(2, 5);

            List<Node> path = dijkstra.findPath(grid, start, goal);

            int expectedLength = 2 + 5 + 1; // Manhattan + 1 for start node
            assertEquals(expectedLength, path.size(),
                "Dijkstra guarantees optimal path; expected length " + expectedLength);
        }

        @Test
        @DisplayName("Path cost equals sum of node weights")
        void pathCostMatchesWeights() {
            // All weights are 1.0 by default, so path cost = number of steps
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 4);

            List<Node> path = dijkstra.findPath(grid, start, goal);
            assertFalse(path.isEmpty());

            // 4 steps from col 0 to col 4
            double expectedCost = 4.0;
            assertEquals(expectedCost, dijkstra.getLastPathCost(), 0.001,
                "Path cost on unit-weight grid should equal step count");
        }

        @Test
        @DisplayName("Dijkstra finds optimal path even with weighted nodes")
        void findsOptimalWithWeights() {
            // Set up a grid where the "direct" path is more expensive than a detour
            // Direct: row 0, col 0->6 but middle cells have weight 3.0
            // Detour: go down to row 1 then across (all weight 1.0)
            // Make cells (0,2), (0,3), (0,4) expensive
            grid.getNodeAt(0, 2).setWeight(3.0);
            grid.getNodeAt(0, 3).setWeight(3.0);
            grid.getNodeAt(0, 4).setWeight(3.0);

            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);

            List<Node> path = dijkstra.findPath(grid, start, goal);
            assertFalse(path.isEmpty());

            // The path should NOT go through the expensive cells (or its cost
            // should be less than the direct weighted route)
            double directCost = 1 + 1 + 3 + 3 + 3 + 1 + 1; // 13.0
            assertTrue(dijkstra.getLastPathCost() < directCost,
                "Dijkstra should find the cheaper detour around expensive cells");
        }
    }

    // ================================================================
    //  GROUP 2: Wall handling (same guarantees as A*)
    // ================================================================
    @Nested
    @DisplayName("Wall Handling")
    class WallHandling {

        @Test
        @DisplayName("Returns empty when no path exists")
        void returnsEmptyWhenBlocked() {
            // Wall off column 3 completely
            for (int r = 0; r < 7; r++) grid.setWall(r, 3, true);

            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);

            List<Node> path = dijkstra.findPath(grid, start, goal);

            assertTrue(path.isEmpty(), "Should return empty path when completely blocked");
        }

        @Test
        @DisplayName("Routes around obstacles correctly")
        void routesAroundObstacle() {
            // Block all of column 2 except row 6
            for (int r = 0; r < 6; r++) grid.setWall(r, 2, true);

            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 4);

            List<Node> path = dijkstra.findPath(grid, start, goal);

            assertFalse(path.isEmpty(), "Should find path around obstacle");
            // Verify no wall cells in path
            for (Node n : path) {
                assertFalse(grid.isWall(n.getRow(), n.getCol()),
                    "Path must not go through walls");
            }
        }
    }

    // ================================================================
    //  GROUP 3: Comparing Dijkstra vs A* guarantees
    // ================================================================
    @Nested
    @DisplayName("Dijkstra vs A* Comparison")
    class ComparisonWithAStar {

        @Test
        @DisplayName("Dijkstra and A* find equal-cost paths on unweighted grid")
        void samePathCostAsAStar() {
            AStarAlgorithm astar = new AStarAlgorithm(grid);
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> astarPath    = astar.findPath(grid, start, goal);
            List<Node> dijkstraPath = dijkstra.findPath(grid, start, goal);

            assertFalse(astarPath.isEmpty());
            assertFalse(dijkstraPath.isEmpty());

            // Both must find the same optimal cost (paths may differ but cost must match)
            assertEquals(astar.getLastPathCost(), dijkstra.getLastPathCost(), 0.001,
                "Both algorithms must find equal-cost optimal paths");
        }

        @Test
        @DisplayName("Dijkstra explores MORE nodes than A* on open grid (heuristic advantage)")
        void dijkstraExploresMoreThanAStar() {
            AStarAlgorithm astar = new AStarAlgorithm(grid);
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            astar.findPath(grid, start, goal);
            dijkstra.findPath(grid, start, goal);

            int astarExplored    = astar.getExploredNodes().size();
            int dijkstraExplored = dijkstra.getExploredNodes().size();

            // This is the KEY insight: A*'s heuristic means it explores less
            assertTrue(dijkstraExplored >= astarExplored,
                "Dijkstra should explore >= nodes vs A* (heuristic advantage). " +
                "Dijkstra=" + dijkstraExplored + " A*=" + astarExplored);
        }
    }

    // ================================================================
    //  GROUP 4: Null safety / edge cases
    // ================================================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Returns empty path for null start")
        void nullStart() {
            List<Node> path = dijkstra.findPath(grid, null, grid.getNodeAt(3, 3));
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Returns empty path for null goal")
        void nullGoal() {
            List<Node> path = dijkstra.findPath(grid, grid.getNodeAt(0, 0), null);
            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("Start == Goal returns single-node path")
        void startEqualsGoal() {
            Node n = grid.getNodeAt(3, 3);
            List<Node> path = dijkstra.findPath(grid, n, n);

            assertFalse(path.isEmpty());
            assertEquals(1, path.size());
            assertEquals(n, path.get(0));
        }

        @Test
        @DisplayName("Stats are 0 before any search")
        void statsZeroBeforeSearch() {
            assertEquals(0, dijkstra.getLastNodesExplored());
            assertEquals(0.0, dijkstra.getLastPathCost(), 0.001);
            assertEquals(0, dijkstra.getLastPathLength());
        }

        @Test
        @DisplayName("Stats are populated after a search")
        void statsPopulatedAfterSearch() {
            dijkstra.findPath(grid, grid.getNodeAt(0, 0), grid.getNodeAt(6, 6));

            assertTrue(dijkstra.getLastNodesExplored() > 0);
            assertTrue(dijkstra.getLastPathCost() > 0);
            assertTrue(dijkstra.getLastPathLength() > 0);
            assertTrue(dijkstra.getLastRunTimeMs() >= 0);
        }
    }
}
