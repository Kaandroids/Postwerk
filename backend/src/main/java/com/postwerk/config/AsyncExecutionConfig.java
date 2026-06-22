package com.postwerk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for off-request automation execution.
 *
 * <p>Automation runs perform blocking external I/O (Gemini, outbound webhooks, IMAP/SMTP) and hold a
 * JDBC connection for their duration. Routing request-thread triggers (inbound webhook ingress, the
 * on-read email processing path) through this <em>small, bounded</em> pool means automation execution
 * can occupy at most {@code maxPoolSize} of the shared HikariCP connections concurrently, so a slow or
 * hung external endpoint can no longer starve the request-serving connection pool. Overflowing work is
 * run on the caller (back-pressure) rather than dropped.</p>
 *
 * @since 1.0
 */
@Configuration
public class AsyncExecutionConfig {

    /** Caps how many automation runs can hold a DB connection at once; keep well below the Hikari pool size. */
    @Bean(name = "automationExecutor")
    public Executor automationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("auto-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
