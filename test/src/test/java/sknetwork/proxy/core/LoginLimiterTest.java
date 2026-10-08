package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class LoginLimiterTest {

	private static final InetAddress A = address(1);
	private static final InetAddress B = address(2);

	private static InetAddress address(int last) {
		try {
			return InetAddress.getByAddress(new byte[] {10, 0, 0, (byte) last});
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void blocksAfterTooManyBadTokensAndOnlyReportsItOnce() {
		LoginLimiter limiter = new LoginLimiter();
		for (int i = 1; i < LoginLimiter.MAX_FAILURES; i++)
			assertFalse(limiter.failed(A, 0));
		assertFalse(limiter.blocked(A, 0));

		assertTrue(limiter.failed(A, 0));
		assertTrue(limiter.blocked(A, 1));
		assertFalse(limiter.failed(A, 1));
	}

	@Test
	void unblocksOnceTheTimeIsUp() {
		LoginLimiter limiter = new LoginLimiter();
		for (int i = 0; i < LoginLimiter.MAX_FAILURES; i++)
			limiter.failed(A, 0);

		assertTrue(limiter.blocked(A, LoginLimiter.BLOCK_MS - 1));
		assertFalse(limiter.blocked(A, LoginLimiter.BLOCK_MS));
	}

	@Test
	void aGoodTokenClearsTheCount() {
		LoginLimiter limiter = new LoginLimiter();
		for (int i = 1; i < LoginLimiter.MAX_FAILURES; i++)
			limiter.failed(A, 0);
		limiter.succeeded(A);

		assertFalse(limiter.failed(A, 0));
		assertFalse(limiter.blocked(A, 0));
	}

	@Test
	void oneAddressDoesNotBlockAnother() {
		LoginLimiter limiter = new LoginLimiter();
		for (int i = 0; i < LoginLimiter.MAX_FAILURES; i++)
			limiter.failed(B, 0);

		assertTrue(limiter.blocked(B, 0));
		assertFalse(limiter.blocked(A, 0));
	}

	@Test
	void neverBlocksLoopback() {
		LoginLimiter limiter = new LoginLimiter();
		InetAddress local = InetAddress.getLoopbackAddress();
		for (int i = 0; i < LoginLimiter.MAX_FAILURES * 2; i++)
			assertFalse(limiter.failed(local, 0));

		assertFalse(limiter.blocked(local, 0));
	}
}
