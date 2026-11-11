package com.kibootia.backend;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Waits for something that happens on another thread, such as an asynchronous event delivery. */
public final class Await {

	private Await() {
	}

	/** Returns when the condition holds; fails the test if it does not within the timeout. */
	public static void until(String what, BooleanSupplier condition) {
		until(what, Duration.ofSeconds(20), condition);
	}

	public static void until(String what, Duration timeout, BooleanSupplier condition) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for " + what, ex);
			}
		}
		throw new AssertionError("Timed out after " + timeout + " waiting for " + what);
	}

}
