package com.kb.app.scheduler;

import com.kb.app.module.task.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 僵尸任务定时检测。
 * <p>
 * 服务启动时的 {@link AppStartupRunner} 负责处理历史遗留异常任务；
 * 本定时任务负责处理服务运行期间新增的异常任务，例如线程池满、OOM 等导致任务卡住。
 * <p>
 * 本任务是幂等的：多次调用 {@code markStaleTasksFailed()} 效果相同，
 * 已经标记为 FAILED 的任务不会被重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleTaskScheduler {

    private final AsyncTaskService asyncTaskService;

    /**
     * 每 30 分钟检测一次僵尸任务。
     * <p>
     * fixedDelay = 1800000 毫秒 = 30 分钟。
     * <p>
     * fixedDelay 与 fixedRate 的区别：
     * fixedDelay：上次执行完成后等待 30 分钟再执行，推荐用于本任务，防止任务堆积。
     * fixedRate：不管上次是否完成，每 30 分钟触发一次，有堆积风险。
     */
    @Scheduled(fixedDelay = 1800000)
    public void checkStaleTasks() {
        LocalDateTime checkTime = LocalDateTime.now();
        asyncTaskService.markStaleTasksFailed();
        log.info("僵尸任务定时检测完成：checkTime={}", checkTime);
    }
}
