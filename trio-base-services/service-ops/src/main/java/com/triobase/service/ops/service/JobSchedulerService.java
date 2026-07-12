package com.triobase.service.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.ops.dto.SaveJobRequest;
import com.triobase.service.ops.entity.OpsJobDefinition;
import com.triobase.service.ops.entity.OpsJobExecutionLog;
import com.triobase.service.ops.mapper.JobDefinitionMapper;
import com.triobase.service.ops.mapper.JobExecutionLogMapper;
import com.triobase.service.ops.service.job.OpsJobHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private static final short ENABLED = 1;
    private static final short DISABLED = 0;
    private static final String MANUAL = "MANUAL";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final JobDefinitionMapper jobDefinitionMapper;
    private final JobExecutionLogMapper executionLogMapper;
    private final ThreadPoolTaskScheduler opsTaskScheduler;
    private final RequestContextService contextService;
    private final List<OpsJobHandler> jobHandlers;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    private Map<String, OpsJobHandler> handlerMap;

    @PostConstruct
    public void init() {
        handlerMap = jobHandlers.stream().collect(Collectors.toMap(OpsJobHandler::name, Function.identity()));
        jobDefinitionMapper.selectList(new LambdaQueryWrapper<OpsJobDefinition>()
                        .eq(OpsJobDefinition::getEnabled, ENABLED))
                .forEach(this::schedule);
    }

    public PageResult<OpsJobDefinition> page(int page, int size, String keyword, Short enabled) {
        LambdaQueryWrapper<OpsJobDefinition> wrapper = new LambdaQueryWrapper<OpsJobDefinition>()
                .eq(OpsJobDefinition::getTenantId, contextService.tenantId())
                .and(StringUtils.hasText(keyword),
                        w -> w.like(OpsJobDefinition::getJobCode, keyword)
                                .or()
                                .like(OpsJobDefinition::getJobName, keyword))
                .eq(enabled != null, OpsJobDefinition::getEnabled, enabled)
                .orderByDesc(OpsJobDefinition::getUpdatedAt);
        IPage<OpsJobDefinition> result = jobDefinitionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Transactional
    public OpsJobDefinition create(SaveJobRequest request) {
        validateCron(request.getCronExpression());
        OpsJobDefinition job = new OpsJobDefinition();
        job.setId(UlidGenerator.nextUlid());
        job.setTenantId(contextService.tenantId());
        apply(job, request);
        job.setEnabled(ENABLED == safeEnabled(request.getEnabled()) ? ENABLED : DISABLED);
        job.setNextRunAt(nextRunAt(job.getCronExpression()));
        jobDefinitionMapper.insert(job);
        if (ENABLED == job.getEnabled()) {
            schedule(job);
        }
        return job;
    }

    @Transactional
    public OpsJobDefinition update(String id, SaveJobRequest request) {
        validateCron(request.getCronExpression());
        OpsJobDefinition job = requireJob(id);
        apply(job, request);
        job.setEnabled(ENABLED == safeEnabled(request.getEnabled()) ? ENABLED : DISABLED);
        job.setNextRunAt(nextRunAt(job.getCronExpression()));
        jobDefinitionMapper.updateById(job);
        reschedule(job);
        return job;
    }

    @Transactional
    public OpsJobDefinition updateEnabled(String id, Short enabled) {
        OpsJobDefinition job = requireJob(id);
        job.setEnabled(ENABLED == safeEnabled(enabled) ? ENABLED : DISABLED);
        job.setNextRunAt(ENABLED == job.getEnabled() ? nextRunAt(job.getCronExpression()) : null);
        jobDefinitionMapper.updateById(job);
        reschedule(job);
        return job;
    }

    @Transactional
    public void delete(String id) {
        OpsJobDefinition job = requireJob(id);
        cancel(job.getId());
        jobDefinitionMapper.deleteById(id);
    }

    public OpsJobExecutionLog trigger(String id) {
        OpsJobDefinition job = requireJob(id);
        return runJob(job, MANUAL);
    }

    public PageResult<OpsJobExecutionLog> logs(String jobId, int page, int size, String status) {
        LambdaQueryWrapper<OpsJobExecutionLog> wrapper = new LambdaQueryWrapper<OpsJobExecutionLog>()
                .eq(OpsJobExecutionLog::getTenantId, contextService.tenantId())
                .eq(OpsJobExecutionLog::getJobId, jobId)
                .eq(StringUtils.hasText(status), OpsJobExecutionLog::getStatus, status)
                .orderByDesc(OpsJobExecutionLog::getStartedAt);
        IPage<OpsJobExecutionLog> result = executionLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    private void apply(OpsJobDefinition job, SaveJobRequest request) {
        job.setJobCode(request.getJobCode());
        job.setJobName(request.getJobName());
        job.setHandlerName(request.getHandlerName());
        job.setCronExpression(request.getCronExpression());
        job.setJobParams(request.getJobParams());
        job.setDescription(request.getDescription());
    }

    private void schedule(OpsJobDefinition job) {
        if (DISABLED == job.getEnabled()) {
            return;
        }
        cancel(job.getId());
        ScheduledFuture<?> future = opsTaskScheduler.schedule(
                () -> runJob(jobDefinitionMapper.selectById(job.getId()), SCHEDULED),
                new CronTrigger(job.getCronExpression())
        );
        if (future != null) {
            futures.put(job.getId(), future);
        }
    }

    private void reschedule(OpsJobDefinition job) {
        cancel(job.getId());
        if (ENABLED == job.getEnabled()) {
            schedule(job);
        }
    }

    private void cancel(String jobId) {
        ScheduledFuture<?> future = futures.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private OpsJobExecutionLog runJob(OpsJobDefinition job, String triggerType) {
        if (job == null) {
            return null;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        OpsJobExecutionLog log = new OpsJobExecutionLog();
        log.setId(UlidGenerator.nextUlid());
        log.setTenantId(job.getTenantId());
        log.setJobId(job.getId());
        log.setJobCode(job.getJobCode());
        log.setTriggerType(triggerType);
        log.setStatus(RUNNING);
        log.setStartedAt(startedAt);
        log.setRunInstance(runInstance());
        log.setTriggeredBy(MANUAL.equals(triggerType) ? contextService.userId() : "SYSTEM");
        executionLogMapper.insert(log);

        try {
            OpsJobHandler handler = handlerMap.get(job.getHandlerName());
            if (handler == null) {
                throw new BizException(45402, "JOB_HANDLER_NOT_FOUND");
            }
            String summary = handler.run(job.getJobParams());
            finishLog(log, startedAt, SUCCESS, summary, null);
        } catch (Exception ex) {
            finishLog(log, startedAt, FAILED, null, ex.getMessage());
        }

        job.setLastRunAt(startedAt);
        job.setNextRunAt(nextRunAt(job.getCronExpression()));
        jobDefinitionMapper.updateById(job);
        return log;
    }

    private void finishLog(OpsJobExecutionLog log,
                           LocalDateTime startedAt,
                           String status,
                           String resultSummary,
                           String errorMessage) {
        LocalDateTime endedAt = LocalDateTime.now();
        log.setStatus(status);
        log.setEndedAt(endedAt);
        log.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        log.setResultSummary(resultSummary);
        log.setErrorMessage(errorMessage);
        executionLogMapper.updateById(log);
    }

    private OpsJobDefinition requireJob(String id) {
        OpsJobDefinition job = jobDefinitionMapper.selectById(id);
        if (job == null) {
            throw new BizException(45401, "JOB_NOT_FOUND");
        }
        return job;
    }

    private short safeEnabled(Short enabled) {
        return enabled != null && enabled == ENABLED ? ENABLED : DISABLED;
    }

    private void validateCron(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException ex) {
            throw new BizException(45403, "JOB_CRON_INVALID");
        }
    }

    private LocalDateTime nextRunAt(String cronExpression) {
        try {
            return CronExpression.parse(cronExpression).next(LocalDateTime.now());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String runInstance() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }
}
