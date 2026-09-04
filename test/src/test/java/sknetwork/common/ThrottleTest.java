package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ThrottleTest {

	@Test
	void letsTheFirstOneThrough() {
		assertTrue(new Throttle(10_000).allow());
	}

	@Test
	void holdsBackTheRestOfTheWindow() {
		Throttle throttle = new Throttle(10_000);

		assertTrue(throttle.allow());
		assertFalse(throttle.allow());
		assertFalse(throttle.allow());
	}

	@Test
	void opensAgainOnceTheWindowPasses() throws InterruptedException {
		Throttle throttle = new Throttle(30);

		assertTrue(throttle.allow());
		assertFalse(throttle.allow());
		Thread.sleep(60);
		assertTrue(throttle.allow());
	}

	@Test
	void letsOnlyOneThreadThrough() throws InterruptedException {
		Throttle throttle = new Throttle(10_000);
		AtomicInteger allowed = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(16);

		for (int i = 0; i < 16; i++) {
			Thread thread = new Thread(() -> {
				try {
					start.await();
					if (throttle.allow())
						allowed.incrementAndGet();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
			thread.setDaemon(true);
			thread.start();
		}

		start.countDown();
		assertTrue(done.await(10, TimeUnit.SECONDS));
		assertTrue(allowed.get() >= 1);
		assertTrue(allowed.get() <= 2);
	}
}
