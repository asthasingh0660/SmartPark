package src.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for AStarAlgorithm.
 *
 * WHAT IS A UNIT TEST?
 * A unit test checks one small "unit" of your code in isolation.
 * You give it a known input → assert it produces the expected output.
 * If the output is wrong, the test FAILS and tells you exactly where.
 *
 * WHY DOES THIS IMPRESS INTERVIEWERS?
 * Most students just run the app and "see if it works."
 * Tests prove your code is correct systematically, and they keep working
 * even after you refactor things.
 *
 * HOW TO READ A TEST:
 *   @Test              — marks this method as a test case
 *   @BeforeEach        — runs before every @Test to set up fresh state
 *   @Nested            — groups related tests together
 *   @DisplayName       — human-readable name shown in test results
 *   assertEquals(a,b)  — FAILS if a != b
 *   assertFalse(x)     — FAILS if x is true
 *   assertTrue(x)      — FAILS if x is false
 *   assertNotNull(x)   — FAILS if x is null
 */
@DisplayName("A* Algorithm Tests")
class AStarAlgorithmTest {

    // We create a fresh grid and algorithm before each test
    // so tests never share state (they're fully independent)
    private Grid grid;
    private AStarAlgorithm astar;

    @BeforeEach
    void setUp() {
        // A 7x7 open grid — no walls by default
        grid  = new Grid(7, 7);
        astar = new AStarAlgorithm(grid);
    }

    // ================================================================
    //  GROUP 1: Basic path finding
    // ================================================================
    @Nested
    @DisplayName("Basic Pathfinding")
    class BasicPathfinding {

        @Test
        @DisplayName("Finds a path on a fully open grid")
        void findsPathOnOpenGrid() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            // A path must exist
            assertFalse(path.isEmpty(), "Expected a path but got none");
            // Path must start at start
            assertEquals(start, path.get(0), "Path should begin at start node");
            // Path must end at goal
            assertEquals(goal, path.get(path.size() - 1), "Path should end at goal node");
        }

        @Test
        @DisplayName("Path from a node to itself is just that node")
        void startEqualsGoal() {
            Node start = grid.getNodeAt(3, 3);
            List<Node> path = astar.findPath(grid, start, start);

            // The path should contain exactly the start/goal node
            assertFalse(path.isEmpty(), "Expected single-node path");
            assertEquals(1, path.size(), "Path should have exactly 1 node");
            assertEquals(start, path.get(0));
        }

        @Test
        @DisplayName("Path is optimal (Manhattan distance) on open grid")
        void pathIsOptimalLength() {
            // On an open grid, Manhattan distance = shortest possible path length
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(3, 4);

            List<Node> path = astar.findPath(grid, start, goal);

            // Manhattan distance from (0,0) to (3,4) = 3 + 4 = 7 steps = 8 nodes
            int expectedLength = 3 + 4 + 1; // +1 because start node is included
            assertEquals(expectedLength, path.size(),
                "A* should find the optimal (shortest) path on an open grid");
        }

        @Test
        @DisplayName("Path nodes are all adjacent (no teleporting)")
        void pathNodesAreAdjacent() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> path = astar.findPath(grid, start, goal);
            assertFalse(path.isEmpty());

            // Every consecutive pair of nodes must be exactly 1 step apart
            for (int i = 0; i < path.size() - 1; i++) {
                Node a = path.get(i);
                Node b = path.get(i + 1);
                int rowDiff = Math.abs(a.getRow() - b.getRow());
                int colDiff = Math.abs(a.getCol() - b.getCol());
                int stepDist = rowDiff + colDiff; // Manhattan step
                assertEquals(1, stepDist,
                    "Nodes in path must be adjacent, but step " + i + " jumped " + stepDist);
            }
        }
    }

    // ================================================================
    //  GROUP 2: Wall handling
    // ================================================================
    @Nested
    @DisplayName("Wall Handling")
    class WallHandling {

        @Test
        @DisplayName("Returns empty path when goal is a wall")
        void noPathWhenGoalIsWall() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);
            grid.setWall(0, 6, true); // block the goal

            List<Node> path = astar.findPath(grid, start, goal);

            assertTrue(path.isEmpty(), "Should return empty path when goal is blocked");
        }

        @Test
        @DisplayName("Returns empty path when start is a wall")
        void noPathWhenStartIsWall() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);
            grid.setWall(0, 0, true);

            List<Node> path = astar.findPath(grid, start, goal);

            assertTrue(path.isEmpty(), "Should return empty path when start is blocked");
        }

        @Test
        @DisplayName("Returns empty path when completely blocked off")
        void noPathWhenCompletelyBlocked() {
            // Wall off the entire right side — start is at 0,0, goal at 0,6
            for (int r = 0; r < 7; r++) grid.setWall(r, 3, true);

            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            assertTrue(path.isEmpty(), "Should return empty path when no route exists");
        }

        @Test
        @DisplayName("Routes around a wall obstacle")
        void routesAroundWall() {
            // Block column 3 from row 0 to 5 (leave row 6 open as a gap)
            for (int r = 0; r < 6; r++) grid.setWall(r, 3, true);

            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(0, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            assertFalse(path.isEmpty(), "Should find a path around the wall");
            assertEquals(start, path.get(0));
            assertEquals(goal,  path.get(path.size() - 1));
            // Path must not pass through any wall
            for (Node n : path) {
                assertFalse(grid.isWall(n.getRow(), n.getCol()),
                    "Path should never pass through a wall cell");
            }
        }
    }

    // ================================================================
    //  GROUP 3: Explored nodes (for visualization)
    // ================================================================
    @Nested
    @DisplayName("Explored Nodes")
    class ExploredNodes {

        @Test
        @DisplayName("getExploredNodes() is empty before any search")
        void exploredEmptyBeforeSearch() {
            assertTrue(astar.getExploredNodes().isEmpty(),
                "No nodes should be explored before findPath() is called");
        }

        @Test
        @DisplayName("getExploredNodes() is non-empty after a successful search")
        void exploredNonEmptyAfterSearch() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);
            astar.findPath(grid, start, goal);

            assertFalse(astar.getExploredNodes().isEmpty(),
                "Explored nodes should be populated after search");
        }

        @Test
        @DisplayName("A* explores fewer nodes than grid size on open grid (heuristic is working)")
        void aStarExploresFewNodes() {
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);
            astar.findPath(grid, start, goal);

            int explored = astar.getExploredNodes().size();
            int totalCells = 7 * 7;

            // A* should NOT explore all cells thanks to the heuristic
            assertTrue(explored < totalCells,
                "A* heuristic should prune the search — explored=" + explored + " total=" + totalCells);
        }
    }

    // ================================================================
    //  GROUP 4: Null / edge case safety
    // ================================================================
    @Nested
    @DisplayName("Edge Cases & Null Safety")
    class EdgeCases {

        @Test
        @DisplayName("Returns empty path for null start")
        void nullStartReturnsEmpty() {
            Node goal = grid.getNodeAt(3, 3);
            List<Node> path = astar.findPath(grid, null, goal);
            assertTrue(path.isEmpty(), "Null start should return empty path");
        }

        @Test
        @DisplayName("Returns empty path for null goal")
        void nullGoalReturnsEmpty() {
            Node start = grid.getNodeAt(0, 0);
            List<Node> path = astar.findPath(grid, start, null);
            assertTrue(path.isEmpty(), "Null goal should return empty path");
        }

        @Test
        @DisplayName("Works on a 1x1 grid (start == goal)")
        void singleCellGrid() {
            Grid tiny = new Grid(1, 1);
            AStarAlgorithm tinyAstar = new AStarAlgorithm(tiny);
            Node only = tiny.getNodeAt(0, 0);

            List<Node> path = tinyAstar.findPath(tiny, only, only);

            assertFalse(path.isEmpty());
            assertEquals(1, path.size());
        }
    }

    // ================================================================
    //  GROUP 5: Heuristic variants
    // ================================================================
    @Nested
    @DisplayName("Heuristic Variants")
    class HeuristicVariants {

        @Test
        @DisplayName("A* with Euclidean heuristic still finds a valid path")
        void euclideanFindsPath() {
            astar.setHeuristic(new Heuristic(Heuristic.Type.EUCLIDEAN));
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("A* with Chebyshev heuristic still finds a valid path")
        void chebyshevFindsPath() {
            astar.setHeuristic(new Heuristic(Heuristic.Type.CHEBYSHEV));
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }

        @Test
        @DisplayName("Weighted A* finds a path (may not be optimal but must reach goal)")
        void weightedFindsPath() {
            astar.setHeuristic(new Heuristic(Heuristic.Type.WEIGHTED, 2.0));
            Node start = grid.getNodeAt(0, 0);
            Node goal  = grid.getNodeAt(6, 6);

            List<Node> path = astar.findPath(grid, start, goal);

            assertFalse(path.isEmpty());
            assertEquals(goal, path.get(path.size() - 1));
        }
    }
}
