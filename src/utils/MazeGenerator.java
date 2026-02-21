package src.utils;

import src.core.Grid;
import src.core.Node;

import java.util.*;

/**
 * Recursive backtracker maze generator.
 */
public class MazeGenerator {

    public static void generate(Grid grid) {
        if (grid == null) return;
        for (int r = 0; r < grid.getRows(); r++)
            for (int c = 0; c < grid.getCols(); c++)
                grid.setWall(r, c, true);

        Random rnd = new Random();
        int sr = Math.max(1, rnd.nextInt(Math.max(1, grid.getRows() / 2)) * 2 + 1);
        int sc = Math.max(1, rnd.nextInt(Math.max(1, grid.getCols() / 2)) * 2 + 1);
        if (!grid.inBounds(sr, sc)) { sr = 1; sc = 1; }

        carve(grid, sr, sc, rnd);

        if (grid.getStartNode() != null) {
            Node s = grid.getStartNode();
            grid.setWall(s.getRow(), s.getCol(), false);
        }
        if (grid.getGoalNode() != null) {
            Node g = grid.getGoalNode();
            grid.setWall(g.getRow(), g.getCol(), false);
        }
    }

    private static void carve(Grid grid, int r, int c, Random rnd) {
        grid.setWall(r, c, false);
        Integer[] dirs = {0, 1, 2, 3};
        List<Integer> order = Arrays.asList(dirs);
        Collections.shuffle(order, rnd);
        for (int d : order) {
            int dr = 0, dc = 0;
            if (d == 0) { dr = -2; } if (d == 1) { dr = 2; }
            if (d == 2) { dc = -2; } if (d == 3) { dc = 2; }
            int nr = r + dr, nc = c + dc;
            if (!grid.inBounds(nr, nc) || !grid.isWall(nr, nc)) continue;
            grid.setWall(r + dr / 2, c + dc / 2, false);
            carve(grid, nr, nc, rnd);
        }
    }
}
