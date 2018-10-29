package io.github.chinalhr.httpclientoptimization.schedule.impl;

import io.github.chinalhr.httpclientoptimization.schedule.FixedRateSchedule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xiang.rao created on 7/10/18 10:13 AM
 * @version $Id$
 */
@Slf4j
public class FixedRateScheduleImpl implements FixedRateSchedule {

    private ScheduledExecutorService executorService;

    private String poolTag;

    @Override
    public FixedRateSchedule setPoolTag(String poolTag) {
        this.poolTag = poolTag;
        return this;
    }

    @Override
    public FixedRateSchedule init() {
        executorService = new ScheduledThreadPoolExecutor(1, new BasicThreadFactory.Builder()
                .namingPattern(poolTag + "_executor_service-pool-%d").daemon(true)
                .build());
        return this;
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public void schedule(Runnable runnable, long initialDelay, Long period, TimeUnit timeUnit) {

        //严格时间调度
        executorService.scheduleAtFixedRate(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("调度任务执行异常", e);
            }
        }, initialDelay, period, timeUnit);
    }
}
