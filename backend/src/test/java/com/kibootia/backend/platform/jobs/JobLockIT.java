package com.kibootia.backend.platform.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.kibootia.backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lock-protected jobs on a real MySQL (ADR 0031). Two callers stand in for two application instances: they share only
 * the database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobLockIT {

	@Autowired
	LockingTaskExecutor executor;

	private static LockConfiguration lock(String name) {
		return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
	}

	@Test
	@DisplayName("while one instance runs a job, another that tries to start it does nothing")
	void secondInstanceIsSkipped() throws Exception {
		CountDownLatch firstIsRunning = new CountDownLatch(1);
		CountDownLatch letFirstFinish = new CountDownLatch(1);
		AtomicInteger runs = new AtomicInteger();

		Thread first = Thread.ofPlatform().start(() -> executor.executeWithLock((Runnable) () -> {
			runs.incrementAndGet();
			firstIsRunning.countDown();
			await(letFirstFinish);
		}, lock("test-exclusive-job")));
		assertThat(firstIsRunning.await(10, TimeUnit.SECONDS)).isTrue();

		executor.executeWithLock((Runnable) runs::incrementAndGet, lock("test-exclusive-job"));
		assertThat(runs.get()).as("the second instance did not run the job").isEqualTo(1);

		letFirstFinish.countDown();
		first.join(10_000);
		executor.executeWithLock((Runnable) runs::incrementAndGet, lock("test-exclusive-job"));
		assertThat(runs.get()).as("the lock is free again once the first run ends").isEqualTo(2);
	}

	@Test
	@DisplayName("jobs with different names do not block each other")
	void differentJobsRunTogether() throws Exception {
		CountDownLatch firstIsRunning = new CountDownLatch(1);
		CountDownLatch letFirstFinish = new CountDownLatch(1);
		AtomicInteger otherRuns = new AtomicInteger();

		Thread first = Thread.ofPlatform().start(() -> executor.executeWithLock((Runnable) () -> {
			firstIsRunning.countDown();
			await(letFirstFinish);
		}, lock("test-job-one")));
		assertThat(firstIsRunning.await(10, TimeUnit.SECONDS)).isTrue();

		executor.executeWithLock((Runnable) otherRuns::incrementAndGet, lock("test-job-two"));

		assertThat(otherRuns.get()).isEqualTo(1);
		letFirstFinish.countDown();
		first.join(10_000);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
