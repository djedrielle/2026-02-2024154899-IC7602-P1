package com.tec.dnsapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class AsyncConfig {

    @Bean(name = "dnsResolverExecutor", destroyMethod = "shutdown")
    public ExecutorService dnsResolverExecutor(
            @Value("${dns.executor.core-size:16}") int coreSize,
            @Value("${dns.executor.max-size:64}") int maxSize,
            @Value("${dns.executor.queue-capacity:200}") int queueCapacity) {

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "dns-resolver-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}