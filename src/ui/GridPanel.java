package src.ui;

import src.ai.ParkingAllocator;
import src.ai.TrafficPredictor;
import src.core.*;
import src.utils.AppConfig;
import src.utils.MazeGenerator;
import src.utils.ParkingLotLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main grid rendering panel for the Smart Parking & Route Planning Visualizer.
 *
 * Renders:
 * - Parking spots (by type and occupancy)
 * - Traffic heatmap (weight-based color gradient)
 * - A* / Dijkstra path and explored nodes
 * - Reservations, start/goal, walls
 * - On-screen legend and status bar
 */
public class GridPanel extends JPanel {

    // === Core ===
    private final Grid grid;
    private final int cellSize;

    // === AI Components ===
    private final TrafficPredictor predictor;
    private final ParkingAllocator allocator;

    // === Algorithm Selection ===
    private PathfindingAlgorithm activeAlgorithm;
    private final AStarAlgorithm astar;
    private final DijkstraAlgorithm dijkstra;

    // === Visualization State ===
    private List<Node> lastExplored = null;
    private List<Node> lastPath     = null;
    private List<Node> compareExplored = null;
    private List<Node> comparePath     = null;
    private boolean showHeatmap  = true;
    private boolean showCompare  = false;

    // === Interaction State ===
    private boolean painting = false;
    private final String clientId = UUID.randomUUID().toString();
    private Node currentTarget   = null;
    private String statusMessage = "Welcome — click 'Load Parking Lot' or right-click to set Start/Goal";

    // === Performance Stats ===
    private String lastStatsMsg = "";

    // === Constants ===
    private static final Color COL_WALL          = new Color(30,  30,  40);
    private static final Color COL_BACKGROUND    = new Color(245, 245, 248);
    private static final Color COL_GRID_LINE     = new Color(210, 210, 215);
    private static final Color COL_START         = new Color(34,  197, 94);   // green
    private static final Color COL_GOAL          = new Color(239, 68,  68);   // red
    private static final Color COL_PATH          = new Color(251, 191, 36);   // yellow
    private static final Color COL_COMPARE_PATH  = new Color(147, 51,  234);  // purple
    private static final Color COL_EXPLORED_BASE = new Color(147, 197, 253);  // light blue
    private static final Color COL_RESERVED      = new Color(255, 165, 0);    // orange
    private static final Color COL_SPOT_REGULAR  = new Color(220, 240, 255);
    private static final Color COL_SPOT_HANDICAP = new Color(100, 160, 255);
    private static final Color COL_SPOT_EV       = new Color(100, 220, 130);
    private static final Color COL_SPOT_VIP      = new Color(255, 215, 0);
    private static final Color COL_SPOT_OCCUPIED = new Color(200, 60,  60);
    private static final int LEGEND_WIDTH  = 190;
    private static final int STATUS_BAR_H  = 32; // px — sits below grid rows

    public GridPanel(Grid grid, int cellSize, TrafficPredictor predictor, AppConfig cfg) {
        this.grid      = grid;
        this.cellSize  = cellSize;
        this.predictor = predictor;
        this.astar     = new AStarAlgorithm(grid);
        this.dijkstra  = new DijkstraAlgorithm(grid);
        this.activeAlgorithm = astar;
        this.allocator = new ParkingAllocator(grid, predictor);

        // Apply config-driven settings
        showHeatmap = cfg.isShowHeatmap();
        allocator.setTopK(cfg.getAllocatorTopK());
        allocator.getTuner().setCongestionPenaltyScale(cfg.getCongestionPenaltyScale());

        setPreferredSize(new Dimension(grid.getCols() * cellSize + LEGEND_WIDTH, grid.getRows() * cellSize + STATUS_BAR_H));
        setBackground(COL_BACKGROUND);

        // Traffic update timer — interval from config
        Timer trafficTimer = new Timer(cfg.getTrafficUpdateIntervalMs(), e -> {
            predictor.applyToGrid(grid, cfg.getTrafficBlendFactor());
            repaint();
        });
        trafficTimer.start();

        // Reservation cleanup timer
        Timer reservationTimer = new Timer(10000, e -> {
            LocalServer.clearExpired();
            repaint();
        });
        reservationTimer.start();

        setupMouseHandling();
        setupKeyHandling();
    }

    // Backward-compat constructor (uses defaults)
    public GridPanel(Grid grid, int cellSize, TrafficPredictor predictor) {
        this(grid, cellSize, predictor, AppConfig.load());
    }

    // ===================== Mouse / Key Handling =====================

    private void setupMouseHandling() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int r = e.getY() / cellSize, c = e.getX() / cellSize;
                if (!grid.inBounds(r, c)) return;
                if (e.isPopupTrigger()) { showPopup(e, r, c); return; }

                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(r, c);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.isShiftDown()) { painting = true; grid.setWall(r, c, true); }
                    else { grid.toggleWall(r, c); }
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                painting = false;
                if (e.isPopupTrigger()) {
                    int r = e.getY() / cellSize, c = e.getX() / cellSize;
                    if (grid.inBounds(r, c)) showPopup(e, r, c);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!painting) return;
                int r = e.getY() / cellSize, c = e.getX() / cellSize;
                if (!grid.inBounds(r, c)) return;
                grid.setWall(r, c, true);
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void setupKeyHandling() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE -> runPathfinding(40);
                    case KeyEvent.VK_C     -> clearPath();
                    case KeyEvent.VK_R     -> resetAll();
                    case KeyEvent.VK_H     -> { showHeatmap = !showHeatmap; repaint(); }
                    case KeyEvent.VK_A     -> allocateBestSpot();
                }
            }
        });
    }

    private void handleRightClick(int r, int c) {
        Node n = grid.getNodeAt(r, c);
        if (n == null) return;
        if (grid.getStartNode() == null) {
            grid.setStartNode(n);
            grid.setWall(r, c, false);
            statusMessage = "Start set at (" + r + "," + c + ")";
        } else if (grid.getGoalNode() == null) {
            grid.setGoalNode(n);
            grid.setWall(r, c, false);
            currentTarget = n;
            statusMessage = "Goal set at (" + r + "," + c + ")";
        } else {
            // Move whichever is closer
            Node start = grid.getStartNode(), goal = grid.getGoalNode();
            double ds = Math.hypot(r - start.getRow(), c - start.getCol());
            double dg = Math.hypot(r - goal.getRow(), c - goal.getCol());
            if (ds < dg) { grid.setStartNode(n); statusMessage = "Start moved."; }
            else         { grid.setGoalNode(n);  currentTarget = n; statusMessage = "Goal moved."; }
            grid.setWall(r, c, false);
        }
        repaint();
    }

    private void showPopup(MouseEvent e, int r, int c) {
        JPopupMenu menu = new JPopupMenu();
        Node n = grid.getNodeAt(r, c);

        JMenuItem setStart = new JMenuItem("Set as Start");
        setStart.addActionListener(ae -> { grid.setStartNode(n); grid.setWall(r,c,false); repaint(); });
        menu.add(setStart);

        JMenuItem setGoal = new JMenuItem("Set as Goal");
        setGoal.addActionListener(ae -> { grid.setGoalNode(n); grid.setWall(r,c,false); currentTarget=n; repaint(); });
        menu.add(setGoal);

        menu.addSeparator();

        if (n.isParkingSpot() && !n.isOccupied() && !LocalServer.isReserved(r, c)) {
            JMenuItem reserve = new JMenuItem("Reserve this Spot");
            reserve.addActionListener(ae -> tryReserveSpot(r, c));
            menu.add(reserve);
        } else if (LocalServer.isReserved(r, c) &&
                   clientId.equals(LocalServer.getOwnerId(r, c))) {
            JMenuItem release = new JMenuItem("Release My Reservation");
            release.addActionListener(ae -> {
                LocalServer.release(r, c, clientId);
                statusMessage = "Reservation released.";
                repaint();
            });
            menu.add(release);
        }

        if (n.isParkingSpot() && n.isOccupied()) {
            JMenuItem vacate = new JMenuItem("Vacate Spot (Sim)");
            vacate.addActionListener(ae -> { n.vacate(); statusMessage = "Spot vacated."; repaint(); });
            menu.add(vacate);
        }

        menu.addSeparator();
        JMenuItem info = new JMenuItem(String.format("Info: (%d,%d) w=%.2f %s", r, c, n.getWeight(), n.getSpotType()));
        info.setEnabled(false);
        menu.add(info);

        menu.show(this, e.getX(), e.getY());
    }

    // ===================== Public API for ControlPanel =====================

    public void setAlgorithm(PathfindingAlgorithm alg) { this.activeAlgorithm = alg; }
    public AStarAlgorithm getAstar() { return astar; }
    public DijkstraAlgorithm getDijkstra() { return dijkstra; }
    public TrafficPredictor getPredictor() { return predictor; }

    public void setShowHeatmap(boolean b) { showHeatmap = b; repaint(); }
    public void setShowCompare(boolean b) { showCompare = b; repaint(); }

    public void runPathfinding(int delayMs) {
        Node start = grid.getStartNode(), goal = grid.getGoalNode();
        if (start == null || goal == null) {
            statusMessage = "Set Start (right-click) and Goal (right-click) first!";
            repaint(); return;
        }
        statusMessage = "Running " + activeAlgorithm.getName() + "...";
        repaint();

        SwingWorker<List<Node>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Node> doInBackground() {
                return activeAlgorithm.findPath(grid, start, goal);
            }
            @Override
            protected void done() {
                try {
                    List<Node> path = get();
                    lastExplored = new ArrayList<>(activeAlgorithm.getExploredNodes());
                    buildStats();
                    animateExplored(lastExplored, delayMs, () -> animatePath(path, delayMs / 2));
                } catch (Exception ex) { statusMessage = "Error: " + ex.getMessage(); repaint(); }
            }
        };
        worker.execute();
    }

    public void compareAlgorithms(int delayMs) {
        Node start = grid.getStartNode(), goal = grid.getGoalNode();
        if (start == null || goal == null) {
            statusMessage = "Set Start and Goal first to compare!";
            repaint(); return;
        }
        statusMessage = "Comparing A* vs Dijkstra...";
        repaint();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                List<Node> astarPath  = astar.findPath(grid, start, goal);
                List<Node> dijkPath   = dijkstra.findPath(grid, start, goal);
                lastExplored    = new ArrayList<>(astar.getExploredNodes());
                lastPath        = astarPath;
                compareExplored = new ArrayList<>(dijkstra.getExploredNodes());
                comparePath     = dijkPath;

                lastStatsMsg = String.format(
                    "A*: %d nodes, %.1f cost, %dms  |  Dijkstra: %d nodes, %.1f cost, %dms",
                    astar.getLastNodesExplored(), astar.getLastPathCost(), astar.getLastRunTimeMs(),
                    dijkstra.getLastNodesExplored(), dijkstra.getLastPathCost(), dijkstra.getLastRunTimeMs()
                );
                return null;
            }
            @Override
            protected void done() {
                showCompare = true;
                statusMessage = "Comparison done — yellow=A*, purple=Dijkstra";
                repaint();
            }
        };
        worker.execute();
    }

    public void allocateBestSpot() {
        Node start = grid.getStartNode();
        if (start == null) {
            statusMessage = "Set Start position first (right-click)!";
            repaint(); return;
        }
        statusMessage = "AI allocating best spot...";
        repaint();

        SwingWorker<ParkingAllocator.AllocationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ParkingAllocator.AllocationResult doInBackground() {
                return allocator.allocate(start, ParkingAllocator.DriverType.REGULAR, activeAlgorithm);
            }
            @Override
            protected void done() {
                try {
                    ParkingAllocator.AllocationResult result = get();
                    if (result == null || result.bestSpot == null) {
                        statusMessage = "No available parking spots found!";
                        repaint(); return;
                    }
                    grid.setGoalNode(result.bestSpot);
                    currentTarget = result.bestSpot;
                    lastExplored = result.exploredNodes != null ? result.exploredNodes : new ArrayList<>();
                    lastStatsMsg = String.format(
                        "Best spot: (%d,%d) [%s] | %s | %d nodes explored | cost=%.1f | %dms",
                        result.bestSpot.getRow(), result.bestSpot.getCol(),
                        result.bestSpot.getSpotType(), result.algorithmUsed,
                        result.nodesExplored, result.pathCost, result.computeTimeMs
                    );
                    statusMessage = "AI found spot at (" + result.bestSpot.getRow() + "," + result.bestSpot.getCol() + ")";
                    animatePath(result.path, 30);
                } catch (Exception ex) { statusMessage = "Allocation error: " + ex.getMessage(); repaint(); }
            }
        };
        worker.execute();
    }

    public void tryReserveSpot(int r, int c) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                LocalServer.clearExpired();
                return LocalServer.reserve(r, c, clientId, "You", Node.SpotType.REGULAR, 5 * 60 * 1000L) != null;
            }
            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        LocalServer.Reservation res = LocalServer.getReservation(r, c);
                        statusMessage = ok
                            ? "Reserved (" + r + "," + c + ") — Token: " + (res != null ? res.token : "N/A")
                            : "Spot (" + r + "," + c + ") already taken!";
                    } else {
                        statusMessage = "Spot (" + r + "," + c + ") already taken!";
                    }
                } catch (Exception ignored) {}
                repaint();
            }
        };
        worker.execute();
    }

    public void applyParkingLayout() {
        grid.resetAll(false);
        ParkingLotLayout.apply(grid);
        ParkingLotLayout.randomlyOccupy(grid, 0.45, System.currentTimeMillis());
        predictor.applyToGrid(grid, 1.0);
        lastExplored = null; lastPath = null;
        compareExplored = null; comparePath = null;
        statusMessage = "Parking lot loaded — " + grid.countAvailableSpots()
                + " of " + grid.countTotalSpots() + " spots available";
        repaint();
    }

    public void clearPath() {
        grid.resetSearchState();
        lastExplored = null; lastPath = null;
        compareExplored = null; comparePath = null;
        showCompare = false;
        lastStatsMsg = "";
        statusMessage = "Cleared";
        repaint();
    }

    public void resetAll() {
        LocalServer.clearAll();
        grid.resetAll(true);
        ParkingLotLayout.apply(grid);
        ParkingLotLayout.randomlyOccupy(grid, 0.45, System.currentTimeMillis());
        predictor.applyToGrid(grid, 1.0);
        lastExplored = null; lastPath = null;
        compareExplored = null; comparePath = null;
        showCompare = false; lastStatsMsg = "";
        currentTarget = null;
        statusMessage = "Reset — Parking lot reloaded";
        repaint();
    }

    public void randomWalls(double density) { grid.randomWalls(density); repaint(); }
    public void generateMaze() { MazeGenerator.generate(grid); repaint(); }

    // ===================== Animation =====================

    private void animateExplored(List<Node> explored, int delayMs, Runnable onDone) {
        if (explored == null || explored.isEmpty()) { if (onDone != null) onDone.run(); return; }
        Timer t = new Timer(Math.max(5, delayMs), null);
        final int[] idx = {0};
        t.addActionListener(e -> {
            int batch = Math.max(1, explored.size() / 120); // cap animation to ~120 frames
            for (int i = 0; i < batch && idx[0] < explored.size(); i++) idx[0]++;
            repaint();
            if (idx[0] >= explored.size()) {
                ((Timer) e.getSource()).stop();
                if (onDone != null) SwingUtilities.invokeLater(onDone);
            }
        });
        t.start();
    }

    private void animatePath(List<Node> path, int delayMs) {
        if (path == null || path.isEmpty()) {
            statusMessage = "No path found!";
            repaint(); return;
        }
        lastPath = new ArrayList<>();
        Timer t = new Timer(Math.max(10, delayMs), null);
        final int[] idx = {0};
        t.addActionListener(e -> {
            if (idx[0] < path.size()) { lastPath.add(path.get(idx[0]++)); repaint(); }
            else {
                ((Timer) e.getSource()).stop();
                statusMessage = String.format("Path found! Length=%d | %s", path.size(), lastStatsMsg);
                repaint();
            }
        });
        t.start();
    }

    private void buildStats() {
        if (activeAlgorithm instanceof AStarAlgorithm a) {
            lastStatsMsg = String.format("%s | %d nodes | cost=%.1f | %dms",
                    a.getName(), a.getLastNodesExplored(), a.getLastPathCost(), a.getLastRunTimeMs());
        } else if (activeAlgorithm instanceof DijkstraAlgorithm d) {
            lastStatsMsg = String.format("%s | %d nodes | cost=%.1f | %dms",
                    d.getName(), d.getLastNodesExplored(), d.getLastPathCost(), d.getLastRunTimeMs());
        }
    }

    // ===================== Painting =====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int gridW = grid.getCols() * cellSize;

        // Background
        g2.setColor(COL_BACKGROUND);
        g2.fillRect(0, 0, gridW, grid.getRows() * cellSize);

        // Grid cells
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                drawCell(g2, r, c);
            }
        }

        // Grid lines
        g2.setColor(COL_GRID_LINE);
        g2.setStroke(new BasicStroke(0.5f));
        for (int r = 0; r <= grid.getRows(); r++)
            g2.drawLine(0, r * cellSize, gridW, r * cellSize);
        for (int c = 0; c <= grid.getCols(); c++)
            g2.drawLine(c * cellSize, 0, c * cellSize, grid.getRows() * cellSize);

        // Explored nodes
        drawExplored(g2, lastExplored, COL_EXPLORED_BASE);

        // Compare explored (Dijkstra)
        if (showCompare && compareExplored != null) {
            drawExplored(g2, compareExplored, new Color(200, 180, 255, 100));
        }

        // Paths
        if (lastPath != null) drawPath(g2, lastPath, COL_PATH, 4);
        if (showCompare && comparePath != null) drawPath(g2, comparePath, COL_COMPARE_PATH, 3);

        // Start / Goal overlays
        drawStartGoal(g2);

        // Legend panel
        drawLegend(g2, gridW);

        // Status bar
        drawStatus(g2);
    }

    private void drawCell(Graphics2D g, int r, int c) {
        int x = c * cellSize, y = r * cellSize;
        Node n = grid.getNodeAt(r, c);

        if (grid.isWall(r, c)) {
            g.setColor(COL_WALL);
            g.fillRect(x, y, cellSize, cellSize);
            return;
        }

        // Heatmap base
        if (showHeatmap && n != null) {
            double w = n.getWeight();
            float ratio = (float) Math.min(1.0, (w - 1.0) / 2.5);
            Color heatColor = blend(new Color(220, 240, 255), new Color(255, 80, 60), ratio);
            g.setColor(heatColor);
            g.fillRect(x, y, cellSize, cellSize);
        }

        // Parking spots
        if (n != null && n.isParkingSpot()) {
            Color spotColor;
            if (n.isOccupied())                               spotColor = COL_SPOT_OCCUPIED;
            else if (LocalServer.isReserved(r, c))           spotColor = COL_RESERVED;
            else spotColor = switch (n.getSpotType()) {
                case HANDICAPPED -> COL_SPOT_HANDICAP;
                case EV_CHARGING -> COL_SPOT_EV;
                case VIP         -> COL_SPOT_VIP;
                default          -> COL_SPOT_REGULAR;
            };
            g.setColor(spotColor);
            int margin = 2;
            g.fillRoundRect(x + margin, y + margin, cellSize - margin * 2, cellSize - margin * 2, 4, 4);

            // Spot icon
            if (cellSize >= 14) {
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, Math.max(7, cellSize / 3)));
                String icon = switch (n.getSpotType()) {
                    case HANDICAPPED -> "♿";
                    case EV_CHARGING -> "⚡";
                    case VIP         -> "★";
                    default          -> n.isOccupied() ? "✖" : "P";
                };
                FontMetrics fm = g.getFontMetrics();
                g.drawString(icon, x + (cellSize - fm.stringWidth(icon)) / 2,
                             y + (cellSize + fm.getAscent()) / 2 - 1);
            }
        }
    }

    private void drawExplored(Graphics2D g, List<Node> explored, Color baseColor) {
        if (explored == null) return;
        int maxVisit = explored.size();
        for (int i = 0; i < explored.size(); i++) {
            Node n = explored.get(i);
            if (isStartOrGoal(n)) continue;
            float alpha = 0.3f + 0.5f * ((float) i / Math.max(1, maxVisit));
            Color c = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), (int)(alpha * 200));
            g.setColor(c);
            g.fillRect(n.getCol() * cellSize + 1, n.getRow() * cellSize + 1, cellSize - 1, cellSize - 1);
        }
    }

    private void drawPath(Graphics2D g, List<Node> path, Color color, int width) {
        if (path == null || path.size() < 2) return;
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < path.size() - 1; i++) {
            Node a = path.get(i), b = path.get(i + 1);
            int x1 = a.getCol() * cellSize + cellSize / 2;
            int y1 = a.getRow() * cellSize + cellSize / 2;
            int x2 = b.getCol() * cellSize + cellSize / 2;
            int y2 = b.getRow() * cellSize + cellSize / 2;
            g.drawLine(x1, y1, x2, y2);
        }
        g.setStroke(new BasicStroke(1));
        // Draw dots on path
        for (Node n : path) {
            if (isStartOrGoal(n)) continue;
            int cx = n.getCol() * cellSize + cellSize / 2;
            int cy = n.getRow() * cellSize + cellSize / 2;
            int r = Math.max(3, cellSize / 5);
            g.setColor(color);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    private void drawStartGoal(Graphics2D g) {
        Node start = grid.getStartNode(), goal = grid.getGoalNode();
        if (start != null) drawMarker(g, start, COL_START, "S");
        if (goal != null)  drawMarker(g, goal,  COL_GOAL,  "G");
    }

    private void drawMarker(Graphics2D g, Node n, Color color, String label) {
        int x = n.getCol() * cellSize, y = n.getRow() * cellSize;
        g.setColor(color);
        g.fillOval(x + 2, y + 2, cellSize - 4, cellSize - 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, cellSize / 2)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + (cellSize - fm.stringWidth(label)) / 2, y + (cellSize + fm.getAscent()) / 2 - 2);
    }

    private void drawLegend(Graphics2D g, int startX) {
        int x = startX + 8, y = 10, lh = 20;
        int panelH = grid.getRows() * cellSize;

        g.setColor(new Color(240, 240, 245));
        g.fillRoundRect(startX + 2, 0, LEGEND_WIDTH - 4, panelH, 8, 8);
        g.setColor(new Color(180, 180, 190));
        g.drawRoundRect(startX + 2, 0, LEGEND_WIDTH - 4, panelH, 8, 8);

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("LEGEND", x, y + 12); y += 22;

        y = drawLegendItem(g, x, y, COL_START,         "S", "Start Node");
        y = drawLegendItem(g, x, y, COL_GOAL,          "G", "Goal / Spot");
        y = drawLegendItem(g, x, y, COL_PATH,          "",  "A* Path");
        y = drawLegendItem(g, x, y, COL_COMPARE_PATH,  "",  "Dijkstra Path");
        y = drawLegendItem(g, x, y, COL_EXPLORED_BASE, "",  "Explored (A*)");
        y += 5;

        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(60,60,80));
        g.drawString("Parking Spots:", x, y); y += 16;

        y = drawLegendItem(g, x, y, COL_SPOT_REGULAR,  "P",  "Regular");
        y = drawLegendItem(g, x, y, COL_SPOT_HANDICAP, "♿", "Handicapped");
        y = drawLegendItem(g, x, y, COL_SPOT_EV,       "⚡", "EV Charging");
        y = drawLegendItem(g, x, y, COL_SPOT_VIP,      "★",  "VIP");
        y = drawLegendItem(g, x, y, COL_SPOT_OCCUPIED, "✖",  "Occupied");
        y = drawLegendItem(g, x, y, COL_RESERVED,      "R",  "Reserved");
        y += 5;

        // Traffic indicator
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(60,60,80));
        g.drawString("Traffic Heatmap:", x, y); y += 15;
        drawHeatmapBar(g, x, y, LEGEND_WIDTH - 20); y += 20;
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(Color.GRAY);
        g.drawString("Low           High", x, y); y += 20;

        // Stats
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(60,60,80));
        g.drawString("Parking Status:", x, y); y += 15;
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(Color.DARK_GRAY);
        g.drawString("Total: " + grid.countTotalSpots(), x, y); y += lh - 4;
        int avail = grid.countAvailableSpots();
        g.setColor(avail > 5 ? new Color(0,140,0) : new Color(180,0,0));
        g.drawString("Available: " + avail, x, y); y += lh - 4;
        g.setColor(Color.DARK_GRAY);
        g.drawString("Reserved: " + LocalServer.countReservations(), x, y); y += lh - 4;

        // Time of day
        y += 5;
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(60,60,80));
        g.drawString("Traffic Mode:", x, y); y += 14;
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(new Color(80,80,180));
        // Word-wrap the time of day label
        String tod = predictor.getTimeOfDay().label;
        g.drawString(tod, x, y); y += 14;
        g.setColor(Color.DARK_GRAY);
        g.drawString("Congestion: " + predictor.getCongestionLabel(), x, y);
    }

    private int drawLegendItem(Graphics2D g, int x, int y, Color color, String icon, String label) {
        g.setColor(color);
        g.fillRoundRect(x, y, 16, 14, 3, 3);
        g.setColor(Color.DARK_GRAY);
        g.drawRoundRect(x, y, 16, 14, 3, 3);
        if (!icon.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 8));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(icon, x + (16 - fm.stringWidth(icon)) / 2, y + 11);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(Color.DARK_GRAY);
        g.drawString(label, x + 21, y + 11);
        return y + 18;
    }

    private void drawHeatmapBar(Graphics2D g, int x, int y, int width) {
        for (int i = 0; i < width; i++) {
            float ratio = (float) i / width;
            Color c = blend(new Color(220, 240, 255), new Color(255, 80, 60), ratio);
            g.setColor(c);
            g.fillRect(x + i, y, 1, 12);
        }
        g.setColor(Color.GRAY);
        g.drawRect(x, y, width, 12);
    }

    private void drawStatus(Graphics2D g) {
        int sw = grid.getCols() * cellSize + LEGEND_WIDTH;
        int sy = grid.getRows() * cellSize;
        // Status bar sits BELOW grid rows (not overlapping cells)
        g.setColor(new Color(25, 30, 50));
        g.fillRect(0, sy, sw, STATUS_BAR_H);
        if (!lastStatsMsg.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(150, 200, 255));
            g.drawString("  " + lastStatsMsg, 4, sy + 13);
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(240, 245, 255));
        g.drawString("  \u25b6  " + statusMessage, 4, sy + STATUS_BAR_H - 6);
    }

    // ===================== Helpers =====================

    private boolean isStartOrGoal(Node n) {
        Node s = grid.getStartNode(), go = grid.getGoalNode();
        return (s != null && s.equals(n)) || (go != null && go.equals(n));
    }

    private Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int)(a.getRed()   + t * (b.getRed()   - a.getRed())),
                (int)(a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int)(a.getBlue()  + t * (b.getBlue()  - a.getBlue()))
        );
    }

    // runAStar kept for backward compat
    public void runAStar(int delayMs) {
        activeAlgorithm = astar;
        runPathfinding(delayMs);
    }
}
