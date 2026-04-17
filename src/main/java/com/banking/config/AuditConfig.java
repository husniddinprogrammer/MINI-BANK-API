package com.banking.config;

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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
