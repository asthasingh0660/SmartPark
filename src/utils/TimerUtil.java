package src.utils;

/**
 * Simple high-resolution timer utility.
 */
public class TimerUtil {
    private long startMs;
    private long endMs;

    public void start() { startMs = System.currentTimeMillis(); }
    public void stop()  { endMs   = System.currentTimeMillis(); }
    public long elapsedMs() { return endMs - startMs; }

    public static long measure(Runnable r) {
        long t = System.currentTimeMillis();
        r.run();
        return System.currentTimeMillis() - t;
    }
}
