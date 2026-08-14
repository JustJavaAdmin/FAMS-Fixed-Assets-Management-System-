package com.example.fams.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for asynchronous processing with a dedicated thread pool for email sending.
 * This ensures email sending doesn't block the main application threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool executor for email sending operations.
     * Configured for high throughput with a reasonable queue to handle bursts.
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);           // Minimum threads always available
        executor.setMaxPoolSize(20);           // Maximum threads under load
        executor.setQueueCapacity(100);        // Queue for pending email tasks
        executor.setThreadNamePrefix("email-async-"); // Thread naming for debugging
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // Fallback if queue full
        executor.setWaitForTasksToCompleteOnShutdown(true); // Wait for emails to finish on shutdown
        executor.setAwaitTerminationSeconds(60); // Max wait time for pending emails
        executor.initialize();
        return executor;
    }
}