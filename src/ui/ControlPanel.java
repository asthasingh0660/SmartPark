package src.ui;

import src.ai.TrafficPredictor;
import src.core.AStarAlgorithm;
import src.core.DijkstraAlgorithm;
import src.core.Heuristic;
import src.utils.AppConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Control panel for the Smart Parking Visualizer.
 * Groups controls by function: Pathfinding, Parking, AI/Traffic, Grid.
 */
public class ControlPanel extends JPanel {

    public ControlPanel(GridPanel gridPanel, AppConfig cfg) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        setBackground(new Color(28, 35, 56));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ---- Pathfinding Group ----
        JPanel pathGroup = group("Pathfinding");

        JComboBox<String> algBox = new JComboBox<>(new String[]{
                "A* (Manhattan)", "A* (Euclidean)", "A* (Chebyshev)", "A* (Weighted)", "Dijkstra"
        });
        // Set default from config
        switch (cfg.getDefaultAlgorithm()) {
            case "ASTAR_EUCLIDEAN" -> algBox.setSelectedIndex(1);
            case "ASTAR_CHEBYSHEV" -> algBox.setSelectedIndex(2);
            case "ASTAR_WEIGHTED"  -> algBox.setSelectedIndex(3);
            case "DIJKSTRA"        -> algBox.setSelectedIndex(4);
            default                -> algBox.setSelectedIndex(0);
        }
        algBox.setToolTipText("Select pathfinding algorithm");
        algBox.addActionListener(e -> {
            int sel = algBox.getSelectedIndex();
            AStarAlgorithm astar = gridPanel.getAstar();
            if (sel == 0) { astar.setHeuristic(new Heuristic(Heuristic.Type.MANHATTAN)); gridPanel.setAlgorithm(astar); }
            else if (sel == 1) { astar.setHeuristic(new Heuristic(Heuristic.Type.EUCLIDEAN)); gridPanel.setAlgorithm(astar); }
            else if (sel == 2) { astar.setHeuristic(new Heuristic(Heuristic.Type.CHEBYSHEV)); gridPanel.setAlgorithm(astar); }
            else if (sel == 3) { astar.setHeuristic(new Heuristic(Heuristic.Type.WEIGHTED, 1.5)); gridPanel.setAlgorithm(astar); }
            else               { gridPanel.setAlgorithm(gridPanel.getDijkstra()); }
        });
        pathGroup.add(label("Algorithm:"));
        pathGroup.add(algBox);

        JSlider speedSlider = new JSlider(0, 150, cfg.getDefaultSpeedMs());
        speedSlider.setToolTipText("Animation delay (ms) — lower = faster");
        speedSlider.setPreferredSize(new Dimension(80, 22));
        pathGroup.add(label("Speed:"));
        pathGroup.add(speedSlider);

        JButton runBtn = btn("▶ Run", new Color(34, 197, 94), e -> gridPanel.runPathfinding(speedSlider.getValue()));
        JButton compareBtn = btn("⚖ Compare A* vs Dijkstra", new Color(99, 102, 241), e -> gridPanel.compareAlgorithms(speedSlider.getValue()));
        JButton clearBtn = btn("✖ Clear Path", new Color(156, 163, 175), e -> gridPanel.clearPath());

        pathGroup.add(runBtn);
        pathGroup.add(compareBtn);
        pathGroup.add(clearBtn);

        // ---- Parking Group ----
        JPanel parkGroup = group("🅿 Smart Parking");

        JButton loadBtn = btn("Load Parking Lot", new Color(14, 165, 233), e -> gridPanel.applyParkingLayout());
        JButton allocBtn = btn("🤖 AI Allocate Best Spot", new Color(245, 158, 11), e -> gridPanel.allocateBestSpot());

        parkGroup.add(loadBtn);
        parkGroup.add(allocBtn);

        // ---- Traffic / AI Group ----
        JPanel aiGroup = group("📡 AI Traffic");

        JComboBox<TrafficPredictor.TimeOfDay> todBox = new JComboBox<>(TrafficPredictor.TimeOfDay.values());
        todBox.setSelectedItem(gridPanel.getPredictor().getTimeOfDay());
        todBox.setToolTipText("Simulate time-of-day traffic");
        todBox.addActionListener(e -> {
            TrafficPredictor.TimeOfDay tod = (TrafficPredictor.TimeOfDay) todBox.getSelectedItem();
            if (tod != null) {
                gridPanel.getPredictor().setTimeOfDay(tod);
                // Grid weights will update on next traffic timer tick (every 5s)
            }
        });
        aiGroup.add(label("Time of Day:"));
        aiGroup.add(todBox);

        JCheckBox heatmapCheck = new JCheckBox("Show Heatmap", true);
        heatmapCheck.setForeground(new Color(210, 225, 255));
        heatmapCheck.setBackground(new Color(38, 45, 70));
        heatmapCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        heatmapCheck.addActionListener(e -> gridPanel.setShowHeatmap(heatmapCheck.isSelected()));
        aiGroup.add(heatmapCheck);

        JCheckBox compareCheck = new JCheckBox("Show Compare", false);
        compareCheck.setForeground(new Color(210, 225, 255));
        compareCheck.setBackground(new Color(38, 45, 70));
        compareCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        compareCheck.addActionListener(e -> gridPanel.setShowCompare(compareCheck.isSelected()));
        aiGroup.add(compareCheck);

        // ---- Grid Group ----
        JPanel gridGroup = group("🗂 Grid");

        JButton mazeBtn  = btn("Maze", new Color(139, 92, 246), e -> gridPanel.generateMaze());
        JButton wallsBtn = btn("Random Walls", new Color(107, 114, 128), e -> gridPanel.randomWalls(0.30));
        JButton resetBtn = btn("⟳ Reset All", new Color(239, 68, 68), e -> gridPanel.resetAll());

        gridGroup.add(mazeBtn);
        gridGroup.add(wallsBtn);
        gridGroup.add(resetBtn);

        // ---- Keyboard Shortcuts hint ----
        JLabel shortcuts = new JLabel("  [Space]=Run  [C]=Clear  [R]=Reset  [H]=Heatmap  [A]=AI Allocate  |  Right-click=Set Start/Goal");
        shortcuts.setFont(new Font("SansSerif", Font.ITALIC, 10));
        shortcuts.setForeground(new Color(160, 185, 230));

        add(pathGroup);
        add(parkGroup);
        add(aiGroup);
        add(gridGroup);
        add(shortcuts);
    }

    /** Visible label for dark-background panels */
    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(210, 225, 255));
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        return l;
    }

    private JPanel group(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 100, 160), 1, true),
                title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10),
                new Color(180, 210, 255));
        p.setBorder(tb);
        p.setBackground(new Color(38, 45, 70));
        // Make all child labels visible
        p.setForeground(Color.WHITE);
        return p;
    }

    private JButton btn(String text, Color bg, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(listener);
        return b;
    }
}
