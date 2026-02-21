# 🅿 Smart Parking & Route Planning Visualizer
**An AI-powered Java application combining A\*, Dijkstra, traffic prediction, and dynamic parking allocation.**

---

## 🚀 How to Run

```bash
# Compile (requires JDK 17+)
mkdir -p out
find src -name "*.java" -print0 | xargs -0 javac -d out

# Run
java -cp out src.ui.MainFrame
```

Or via IDE: Open the project, set `src.ui.MainFrame` as the main class and run.

---

## 🎯 Use Case

A driver enters a parking lot. The system:
1. **Predicts traffic/congestion** on each lane using time-of-day patterns
2. **Finds available spots** that match the driver type (Regular / Handicapped / EV / VIP)
3. **Runs A\* or Dijkstra** to find the optimal route to the best spot
4. **Lets the driver reserve** the spot with a token and TTL
5. **Replans dynamically** as congestion changes (heatmap updates every 5s)

---

## 🏗 Architecture

```
src/
├── core/
│   ├── Node.java              — Grid cell with parking metadata + A* costs
│   ├── Grid.java              — Grid logic: walls, spots, reservations, neighbors
│   ├── Heuristic.java         — Manhattan / Euclidean / Chebyshev / Weighted
│   ├── AStarAlgorithm.java    — A* with pluggable heuristic + AI-tuned edge costs
│   ├── DijkstraAlgorithm.java — Dijkstra (optimal, explores more than A*)
│   ├── PathfindingAlgorithm.java — Shared interface
│   └── LocalServer.java       — In-memory reservation manager (token + TTL)
│
├── ai/
│   ├── TrafficPredictor.java  — AI congestion prediction (time-of-day + hotspots)
│   ├── HeuristicTuner.java    — Dynamically adjusts edge costs for congestion avoidance
│   └── ParkingAllocator.java  — Core AI: scores spots, runs pathfinding, returns best result
│
├── ui/
│   ├── GridPanel.java         — Renders grid, heatmap, paths, spots; handles interaction
│   ├── ControlPanel.java      — Algorithm selector, driver type, AI controls
│   └── MainFrame.java         — App entry point (main method here)
│
└── utils/
    ├── ParkingLotLayout.java  — Generates realistic parking lot (lanes + bays + special spots)
    ├── MazeGenerator.java     — Recursive backtracker maze (for algorithm demos)
    └── TimerUtil.java         — High-resolution timing utility
```

---

## 🧠 AI Components

### TrafficPredictor
- Simulates real-world traffic patterns based on **time of day** (Morning Rush, Midday, Evening Rush, Night, Weekend)
- Applies **spatial hotspots** (entrance/exits are busier)
- Uses **Gaussian noise** for realistic variation
- **Smooth blending** — weights update gradually every 5 seconds (no jarring jumps)

### HeuristicTuner
- Bridges traffic prediction with pathfinding
- Applies **congestion penalty** to edge costs: `tunedCost = baseCost × (1 + scale × congestion)`
- Supports **inflated heuristic** (Weighted A\*) for speed/optimality tradeoff

### ParkingAllocator
- Scores all available spots: `score = distance + congestionPenalty + typeBonus`
- Evaluates **Top-K candidates** to avoid dead-ends
- Filters by **driver type** preference (e.g., EV driver gets EV spots)
- Returns full result: best spot, path, explored nodes, timing stats

---

## 🎮 Controls

| Action | Effect |
|--------|--------|
| **Right-click** | Set Start → then Goal |
| **Left-click** | Toggle wall |
| **Shift + Drag** | Draw walls |
| **Right-click on spot** | Context menu: Reserve / Info |
| **Space** | Run selected algorithm |
| **A** | AI auto-allocate best parking spot |
| **H** | Toggle traffic heatmap |
| **C** | Clear path |
| **R** | Reset everything |

---

## 📊 Algorithm Comparison (built-in)

Click **"⚖ Compare A\* vs Dijkstra"** to run both on the same grid:
- **Yellow path** = A\* result
- **Purple path** = Dijkstra result
- Stats panel shows: nodes explored, path cost, runtime for each

Typically A\* explores far fewer nodes than Dijkstra while finding the same (or similar) cost path.

---

## 🗂 Spot Types

| Symbol | Type | Color |
|--------|------|-------|
| P | Regular | Light blue |
| ♿ | Handicapped | Blue |
| ⚡ | EV Charging | Green |
| ★ | VIP | Gold |
| ✖ | Occupied | Red |
| R | Reserved | Orange |

---

## 📦 Dependencies
- **Java 17+** (uses `switch` expressions)
- **Swing** (built-in, no external libraries needed)
