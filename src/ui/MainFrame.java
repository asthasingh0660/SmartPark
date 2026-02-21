package src.ui;

import src.ai.TrafficPredictor;
import src.core.Grid;
import src.utils.AppConfig;
import src.utils.ParkingLotLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Smart Parking & Route Planning Visualizer — Main Application Frame.
 *
 * All startup settings (grid size, occupancy, TTL, traffic mode, etc.)
 * are read from config/app.properties via AppConfig — no hardcoded values here.
 */
public class MainFrame extends JFrame {

    public MainFrame(AppConfig cfg) {
        super("Smart Parking & Route Planning Visualizer - AI-Powered");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- Read all settings from config ---
        int rows     = cfg.getGridRows();
        int cols     = cfg.getGridCols();
        int cellSize = cfg.getCellSize();

        // Parse traffic mode from config string
        TrafficPredictor.TimeOfDay tod;
        try { tod = TrafficPredictor.TimeOfDay.valueOf(cfg.getInitialTrafficMode()); }
        catch (IllegalArgumentException e) { tod = TrafficPredictor.TimeOfDay.MIDDAY; }

        // --- Core setup ---
        Grid grid = new Grid(rows, cols);
        TrafficPredictor predictor = new TrafficPredictor(tod);

        ParkingLotLayout.apply(grid);
        ParkingLotLayout.randomlyOccupy(grid, cfg.getInitialOccupancy(), cfg.getOccupancySeed());
        predictor.applyToGrid(grid);

        // --- UI ---
        GridPanel gridPanel = new GridPanel(grid, cellSize, predictor, cfg);
        ControlPanel controlPanel = new ControlPanel(gridPanel, cfg);

        // --- Layout ---
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(controlPanel, BorderLayout.SOUTH);
        getContentPane().add(buildTitleBar(), BorderLayout.NORTH);

        pack();
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(15, 23, 42));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        JLabel title = new JLabel("\uD83C\uDD7F Smart Parking & Route Planning \u2014 AI Pathfinding Visualizer");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(new Color(226, 232, 240));

        JLabel subtitle = new JLabel("A\u2605 Pathfinding  \u00b7  Dijkstra  \u00b7  AI Traffic Prediction  \u00b7  Dynamic Spot Allocation");
        subtitle.setFont(new Font("SansSerif", Font.ITALIC, 11));
        subtitle.setForeground(new Color(148, 163, 184));

        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(title, BorderLayout.CENTER);
        text.add(subtitle, BorderLayout.SOUTH);

        bar.add(text, BorderLayout.CENTER);
        return bar;
    }

    public static void main(String[] args) {
        // Load config FIRST — before anything else
        AppConfig cfg = AppConfig.load();
        System.out.println("[Main] " + cfg);

        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new MainFrame(cfg).setVisible(true);
        });
    }
}
