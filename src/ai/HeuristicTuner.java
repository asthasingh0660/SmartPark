package src.ai;

/**
 * AI Heuristic Tuner — dynamically adjusts edge costs and heuristic weights
 * based on real-time congestion data from TrafficPredictor.
 *
 * This is the "AI brain" that bridges traffic prediction with pathfinding.
 * It makes routes avoid congested areas intelligently.
 */
public class HeuristicTuner {

    // Congestion penalty model
    private double congestionPenaltyScale = 1.5;  // multiplier applied to congestion
    private double heuristicInflation = 1.0;       // inflate heuristic for faster (less optimal) search
    private boolean avoidCongestion = true;

    public HeuristicTuner() {}

    // ---- Configuration ----
    public void setCongestionPenaltyScale(double s) { this.congestionPenaltyScale = Math.max(0, s); }
    public double getCongestionPenaltyScale() { return congestionPenaltyScale; }

    public void setHeuristicInflation(double i) { this.heuristicInflation = Math.max(1.0, i); }
    public double getHeuristicInflation() { return heuristicInflation; }

    public void setAvoidCongestion(boolean b) { this.avoidCongestion = b; }
    public boolean isAvoidCongestion() { return avoidCongestion; }

    /**
     * Adjust edge traversal cost based on congestion.
     * @param baseCost     Base movement cost (from grid.movementCost)
     * @param congestion   Normalized congestion factor [0.0 = free, 1.0 = jammed]
     * @return Adjusted cost
     */
    public double tuneEdgeCost(double baseCost, double congestion) {
        if (!avoidCongestion) return baseCost;
        double penalty = congestionPenaltyScale * congestion;
        return baseCost * (1.0 + penalty);
    }

    /**
     * Inflate the heuristic value to trade optimality for speed (weighted A*).
     * @param rawHeuristic  The raw heuristic distance estimate
     * @return Inflated value
     */
    public double tuneHeuristic(double rawHeuristic, double congestionFactor) {
        // When congestion is high, inflate heuristic more to escape the area faster
        double dynamicInflation = heuristicInflation + (avoidCongestion ? 0.3 * congestionFactor : 0);
        return rawHeuristic * dynamicInflation;
    }

    /**
     * Score a parking spot candidate considering distance + congestion risk.
     * Lower score = better candidate.
     *
     * @param manhattan     Distance to spot (Manhattan)
     * @param spotWeight    Spot node's current weight (traffic around spot)
     * @param isHandicapped Bonus for handicapped driver using handicapped spot
     * @param isEV          Bonus for EV driver using EV spot
     */
    public double scoreSpot(double manhattan, double spotWeight, boolean isHandicapped, boolean isEV) {
        double distScore = manhattan;
        double congestionScore = congestionPenaltyScale * (spotWeight - 1.0);
        double typeBonus = (isHandicapped ? -5.0 : 0) + (isEV ? -3.0 : 0);
        return distScore + congestionScore + typeBonus;
    }


}
