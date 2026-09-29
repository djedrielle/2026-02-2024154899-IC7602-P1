package com.tec.dnsapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AsyncConfig")
class AsyncConfigTest {

    @Test
    @DisplayName("crea el pool con el tamaño configurado y hilos daemon con nombre")
    void buildsConfiguredPool() throws Exception {
        ExecutorService executor = new AsyncConfig().dnsResolverExecutor(2, 4, 10);
        try {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            assertThat(pool.getCorePoolSize()).isEqualTo(2);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(10);

            Future<String> name = executor.submit(() -> Thread.currentThread().getName() + "|" + Thread.currentThread().isDaemon());
            assertThat(name.get(2, TimeUnit.SECONDS)).startsWith("dns-resolver-").endsWith("|true");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("con el pool y la cola llenos rechaza tareas (RejectedExecutionException)")
    void rejectsWhenSaturated() {
        ExecutorService executor = new AsyncConfig().dnsResolverExecutor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Runnable blocker = () -> {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            };
            executor.execute(blocker);
            executor.execute(blocker);

            assertThatThrownBy(() -> executor.execute(blocker)).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
