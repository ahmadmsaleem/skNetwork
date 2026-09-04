package sknetwork.spigot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import sknetwork.spigot.elements.types.AtomicResult;

class AtomicRequestsTest {

	@Test
	void resumesTheTriggerThatWasWaiting() {
		AtomicRequests requests = new AtomicRequests(5_000);
		AtomicReference<AtomicResult> resumed = new AtomicReference<>();

		requests.expect(1, resumed::set);

		assertTrue(requests.complete(1, AtomicResult.accepted(100L)));
		assertNotNull(resumed.get());
		assertTrue(resumed.get().ok());
		assertEquals(100L, resumed.get().value());
		assertEquals(0, requests.pending());
	}

	@Test
	void hasNobodyToTellAboutAFireAndForgetChange() {
		AtomicRequests requests = new AtomicRequests(5_000);

		assertFalse(requests.complete(99, AtomicResult.refused("no")));
	}

	@Test
	void answersEachRequestOnce() {
		AtomicRequests requests = new AtomicRequests(5_000);
		List<AtomicResult> resumed = new ArrayList<>();

		requests.expect(1, resumed::add);

		assertTrue(requests.complete(1, AtomicResult.accepted(1L)));
		assertFalse(requests.complete(1, AtomicResult.accepted(2L)));
		assertEquals(1, resumed.size());
	}

	@Test
	void keepsRequestsApart() {
		AtomicRequests requests = new AtomicRequests(5_000);
		AtomicReference<AtomicResult> first = new AtomicReference<>();
		AtomicReference<AtomicResult> second = new AtomicReference<>();

		requests.expect(1, first::set);
		requests.expect(2, second::set);
		assertEquals(2, requests.pending());

		requests.complete(2, AtomicResult.accepted("second"));

		assertNull(first.get());
		assertEquals("second", second.get().value());
		assertEquals(1, requests.pending());
	}

	@Test
	void holdsATriggerUntilTheDeadline() {
		AtomicRequests requests = new AtomicRequests(60_000);
		AtomicReference<AtomicResult> resumed = new AtomicReference<>();

		requests.expect(1, resumed::set);
		requests.sweep(true);

		assertNull(resumed.get());
		assertEquals(1, requests.pending());
	}

	@Test
	void givesUpOnceTheDeadlinePasses() throws InterruptedException {
		AtomicRequests requests = new AtomicRequests(10);
		AtomicReference<AtomicResult> resumed = new AtomicReference<>();

		requests.expect(1, resumed::set);
		Thread.sleep(40);
		requests.sweep(true);

		assertNotNull(resumed.get());
		assertFalse(resumed.get().ok());
		assertFalse(resumed.get().answered());
		assertTrue(resumed.get().error().contains("did not answer within 10ms"));
		assertEquals(0, requests.pending());
	}

	@Test
	void failsEverythingWaitingWhenTheProxyGoesAway() {
		AtomicRequests requests = new AtomicRequests(60_000);
		List<AtomicResult> resumed = new ArrayList<>();

		requests.expect(1, resumed::add);
		requests.expect(2, resumed::add);
		requests.sweep(false);

		assertEquals(2, resumed.size());
		assertTrue(resumed.stream().noneMatch(AtomicResult::answered));
		assertTrue(resumed.get(0).error().contains("lost the proxy"));
		assertEquals(0, requests.pending());
	}

	@Test
	void survivesATriggerThatStartsAnotherChange() {
		AtomicRequests requests = new AtomicRequests(60_000);
		List<AtomicResult> resumed = new ArrayList<>();

		requests.expect(1, result -> {
			resumed.add(result);
			requests.expect(2, resumed::add);
		});
		requests.sweep(false);

		assertEquals(1, resumed.size());
		assertEquals(1, requests.pending());
	}

	@Test
	void doesNothingWhenNobodyIsWaiting() {
		AtomicRequests requests = new AtomicRequests(1);

		requests.sweep(true);
		requests.sweep(false);

		assertEquals(0, requests.pending());
	}

	@Test
	void tellsAnUnansweredChangeFromARefusedOne() {
		assertTrue(AtomicResult.accepted(1L).answered());
		assertTrue(AtomicResult.refused("no").answered());
		assertFalse(AtomicResult.unanswered("lost it").answered());
		assertFalse(AtomicResult.refused("no").ok());
	}
}
