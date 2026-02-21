package src.core;

import java.util.*;

/**
 * In-memory reservation server for the parking allocation system.
 * Manages spot reservations with token-based ownership and TTL expiry.
 */
public class LocalServer {

    public static class Reservation {
        public final String token;
        public final String ownerId;
        public final String ownerName;
        public final Node.SpotType requestedType;
        public long expiresAtMs;
        public long createdAtMs;

        public Reservation(String token, String ownerId, String ownerName,
                           Node.SpotType requestedType, long expiresAtMs) {
            this.token = token;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.requestedType = requestedType;
            this.expiresAtMs = expiresAtMs;
            this.createdAtMs = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return expiresAtMs > 0 && System.currentTimeMillis() > expiresAtMs;
        }

        public long remainingMs() {
            if (expiresAtMs <= 0) return Long.MAX_VALUE;
            return Math.max(0, expiresAtMs - System.currentTimeMillis());
        }
    }

    private static final Map<String, Reservation> reservations = new HashMap<>();
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private static String key(int r, int c) { return r + "," + c; }

    public static synchronized void clearExpired() {
        reservations.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Reserve spot (r,c) for owner. Returns Reservation on success, null if already taken.
     */
    public static synchronized Reservation reserve(int r, int c, String ownerId) {
        return reserve(r, c, ownerId, "Driver", Node.SpotType.REGULAR, DEFAULT_TTL_MS);
    }

    public static synchronized Reservation reserve(int r, int c, String ownerId,
                                                     String ownerName, Node.SpotType type, long ttlMs) {
        clearExpired();
        String k = key(r, c);
        if (reservations.containsKey(k)) return null;
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long expires = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0;
        Reservation res = new Reservation(token, ownerId, ownerName, type, expires);
        reservations.put(k, res);
        System.out.printf("[Server] RESERVED (%d,%d) by '%s' [%s] token=%s TTL=%ds%n",
                r, c, ownerName, type, token, ttlMs / 1000);
        return res;
    }

    public static synchronized void release(int r, int c) {
        Reservation r2 = reservations.remove(key(r, c));
        if (r2 != null) System.out.printf("[Server] RELEASED (%d,%d) token=%s%n", r, c, r2.token);
    }

    public static synchronized void release(int r, int c, String clientId) {
        String k = key(r, c);
        Reservation res = reservations.get(k);
        if (res != null && res.ownerId.equals(clientId)) {
            reservations.remove(k);
            System.out.printf("[Server] RELEASED (%d,%d) by owner '%s'%n", r, c, clientId);
        }
    }

    public static synchronized boolean isReserved(int r, int c) {
        clearExpired();
        return reservations.containsKey(key(r, c));
    }

    public static synchronized String getOwnerId(int r, int c) {
        clearExpired();
        Reservation res = reservations.get(key(r, c));
        return res == null ? null : res.ownerId;
    }

    public static synchronized Reservation getReservation(int r, int c) {
        clearExpired();
        return reservations.get(key(r, c));
    }

    public static synchronized String getToken(int r, int c) {
        clearExpired();
        Reservation res = reservations.get(key(r, c));
        return res == null ? null : res.token;
    }

    public static synchronized int countReservations() {
        clearExpired();
        return reservations.size();
    }

    public static synchronized void clearAll() {
        reservations.clear();
        System.out.println("[Server] All reservations cleared.");
    }

    /** Returns a summary for the status panel */
    public static synchronized String getSummary() {
        clearExpired();
        return String.format("%d active reservation(s)", reservations.size());
    }
}
