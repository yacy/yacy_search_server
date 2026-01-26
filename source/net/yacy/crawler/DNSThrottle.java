package net.yacy.crawler;

import java.util.concurrent.atomic.AtomicLong;

public final class DNSThrottle {

    private static final AtomicLong nextAllowed = new AtomicLong(0);

    private static volatile boolean enabled = true;
    private static volatile long intervalMs = 5; // default 200 Hz

    private DNSThrottle() {}

    public static void configure(boolean enable, int hz) {
        enabled = enable;
        if (hz <= 0) {
            intervalMs = Long.MAX_VALUE;
        } else {
            intervalMs = Math.max(1, 1000L / hz);
        }
    }

    /**
     * @return true if DNS is allowed NOW, false if caller must skip
     */
    public static boolean allow() {
        if (!enabled) return true;

        final long now = System.currentTimeMillis();
        final long allowed = nextAllowed.get();

        if (now < allowed) {
            return false;
        }

        return nextAllowed.compareAndSet(allowed, now + intervalMs);
    }
}
