package com.banking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.banking.audit.AuditAwareImpl;

import java.util.concurrent.Executor;

/**
 * Enables Spring Data JPA auditing and async execution.
 *
 * <p>{@code @EnableJpaAuditing} activates the {@code AuditingEntityListener}
 * referenced in {@link com.banking.entity.base.BaseEntity}, which auto-populates
 * {@code createdAt}, {@code updatedAt}, {@code createdBy}, {@code updatedBy}.
 *
 * <p>{@code @EnableAsync} enables the {@code @Async} annotation used by
 * {@link com.banking.audit.AuditLogService} to write audit records without
 * blocking the main business transaction thread.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
public class AuditConfig {

    /**
     * Provides the current authenticated user's email to the Spring Data auditing framework.
     *
     * @return the {@link AuditorAware} implementation backed by Spring Security context
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditAwareImpl();
    }

    /**
     * Dedicated thread pool for async audit log writes.
     * Bounded pool prevents thread exhaustion under high load;
     * named threads simplify identification in thread dumps.
     *
     * @return configured executor for {@code @Async} audit tasks
     */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        // RELIABILITY: Queue=500 handles ~500 concurrent users without degradation.
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-exec-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // RELIABILITY: CallerRunsPolicy ensures audit records are NEVER silently dropped
        // when the thread pool is saturated. In banking, every transaction must be audited.
        // Slows the request under extreme load, but preserves the audit trail.
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.error("Audit executor queue full — running on caller thread. " +
                "Consider increasing queue capacity. activeThreads={}, queueSize={}",
                pool.getActiveCount(), pool.getQueue().size());
            if (!pool.isShutdown()) {
                task.run();
            }
        });

        executor.initialize();
        return executor;
    }
}
