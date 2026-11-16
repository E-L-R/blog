package io.github.elr.blog.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A clock that tests move forward by hand, to expire leases and reach backoff times without
 * waiting. It starts at the real time, in whole microseconds like the application's clock.
 */
class MutableClock extends Clock {

    private volatile Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Override
    public Instant instant() {
        return now;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
