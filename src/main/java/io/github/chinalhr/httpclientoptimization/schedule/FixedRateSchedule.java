package io.github.chinalhr.httpclientoptimization.schedule;

import java.util.concurrent.TimeUnit;

/**
 * @author xiang.rao created on 7/10/18 10:10 AM
 * @version $Id$
 */
public interface FixedRateSchedule {


    FixedRateSchedule setPoolTag(String poolTag);

    FixedRateSchedule init();

    void shutdown();

    void schedule(Runnable runnable, long initialDelay, Long period, TimeUnit timeUnit);

}
