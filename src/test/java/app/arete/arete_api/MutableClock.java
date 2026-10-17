package app.arete.arete_api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock tests can move forward, to cross expiry and grace-period boundaries without sleeping.
 */
public class MutableClock extends Clock {

    private Instant currentInstant;

    public MutableClock(final Instant startInstant) {
        this.currentInstant = startInstant;
    }

    public synchronized void advance(final Duration duration) {
        currentInstant = currentInstant.plus(duration);
    }

    @Override
    public synchronized Instant instant() {
        return currentInstant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is fixed to UTC");
    }
}
