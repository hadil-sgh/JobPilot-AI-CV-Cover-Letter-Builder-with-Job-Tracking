package com.jobpilot.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String INDEXER = "indexerExecutor";
    public static final String GENERATION = "generationExecutor";

    /** One thread: profile re-indexes run one after another, never concurrently. */
    @Bean(INDEXER)
    Executor indexerExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("indexer-");
        ex.initialize();
        return ex;
    }

    /** One thread: the local LLM serves one request at a time anyway; jobs queue up in order. */
    @Bean(GENERATION)
    Executor generationExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("generation-");
        ex.initialize();
        return ex;
    }
}
