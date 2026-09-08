package com.tmt.TMLibrary.common.utils;

import org.springframework.data.redis.core.types.Expiration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RandomExpirationTimeWithOffset {
    private static final Random RAND = new Random();
    public static Expiration get(long time, TimeUnit timeUnit) {
        if (time <= 0) {
            throw new RuntimeException("expiration time must be greater than zero");
        }

        if (timeUnit == null) {
            timeUnit = TimeUnit.SECONDS;
        }

        if  (!timeUnit.equals(TimeUnit.SECONDS)) {
            time = timeUnit.toSeconds(time);
        }

        long ttl = time + RAND.nextInt(300);

        return Expiration.from(ttl, timeUnit);
    }
}
