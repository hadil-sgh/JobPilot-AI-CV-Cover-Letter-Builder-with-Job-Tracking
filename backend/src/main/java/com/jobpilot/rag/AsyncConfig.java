package com.jobpilot.rag;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String INDEXER = "indexerExecutor";

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
}
