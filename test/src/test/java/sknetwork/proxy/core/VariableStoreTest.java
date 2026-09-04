package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sknetwork.common.VariableEntry;

class VariableStoreTest {

	private VariableStore store;

	@BeforeEach
	void setUp() {
		store = new VariableStore();
	}

	@Test
	void handsBackWhatItWasGiven() {
		store.set("coins::eult", "long", new byte[] {1, 2}, "100", 7);
		VariableEntry entry = store.get("coins::eult");

		assertNotNull(entry);
		assertEquals("long", entry.type);
		assertEquals("100", entry.display);
		assertEquals(7, entry.seq);
	}

	@Test
	void hasNothingForAKeyNobodyWrote() {
		assertNull(store.get("missing"));
		assertEquals(0, store.size());
	}

	@Test
	void overwritesRatherThanAppends() {
		store.set("coins", "long", new byte[] {1}, "1", 1);
		store.set("coins", "long", new byte[] {2}, "2", 2);

		assertEquals(1, store.size());
		assertEquals("2", store.get("coins").display);
	}

	@Test
	void deletesOneKey() {
		store.set("a", "long", new byte[] {1}, "1", 1);
		store.set("a::b", "long", new byte[] {1}, "1", 2);
		store.delete("a");

		assertNull(store.get("a"));
		assertNotNull(store.get("a::b"));
	}

	@Test
	void deletesAWholeBranch() {
		store.set("coins", "long", new byte[] {1}, "1", 1);
		store.set("coins::eult", "long", new byte[] {1}, "1", 2);
		store.set("coins::eult::gold", "long", new byte[] {1}, "1", 3);
		store.set("coinsplitter", "long", new byte[] {1}, "1", 4);
		store.set("party::5", "long", new byte[] {1}, "1", 5);

		store.delete("coins::*");

		assertNull(store.get("coins"));
		assertNull(store.get("coins::eult"));
		assertNull(store.get("coins::eult::gold"));
		assertNotNull(store.get("coinsplitter"));
		assertNotNull(store.get("party::5"));
	}

	@Test
	void deletesEverythingForTheRootTree() {
		store.set("a", "long", new byte[] {1}, "1", 1);
		store.set("b::c", "long", new byte[] {1}, "1", 2);

		store.delete("::*");

		assertEquals(0, store.size());
	}

	@Test
	void knowsWhetherADeleteWouldChangeAnything() {
		store.set("coins::eult", "long", new byte[] {1}, "1", 1);

		assertTrue(store.matches("coins::eult"));
		assertTrue(store.matches("coins::*"));
		assertFalse(store.matches("coins"));
		assertFalse(store.matches("party::*"));
		assertFalse(store.matches("missing"));
	}

	@Test
	void matchesEverythingWithABareStar() {
		store.set("a", "long", new byte[] {1}, "1", 1);
		store.set("b::c", "long", new byte[] {1}, "1", 2);

		assertEquals(2, store.matching("*").size());
	}

	@Test
	void matchesAWholeBranchByGlob() {
		store.set("coins::eult", "long", new byte[] {1}, "1", 1);
		store.set("coins::njol", "long", new byte[] {1}, "1", 2);
		store.set("party::5", "long", new byte[] {1}, "1", 3);

		List<Map.Entry<String, VariableEntry>> matched = store.matching("coins::*");

		assertEquals(2, matched.size());
		assertEquals("coins::eult", matched.get(0).getKey());
		assertEquals("coins::njol", matched.get(1).getKey());
	}

	@Test
	void needsAnExactMatchWithoutAStar() {
		store.set("coins", "long", new byte[] {1}, "1", 1);
		store.set("coins::eult", "long", new byte[] {1}, "1", 2);

		assertEquals(1, store.matching("coins").size());
	}

	@Test
	void matchesAStarInTheMiddle() {
		store.set("online::lobby::1", "long", new byte[] {1}, "1", 1);
		store.set("online::survival::1", "long", new byte[] {1}, "1", 2);
		store.set("online::lobby::2", "long", new byte[] {1}, "1", 3);

		assertEquals(2, store.matching("online::*::1").size());
	}

	@Test
	void quotesTheRestOfThePattern() {
		store.set("a.b", "long", new byte[] {1}, "1", 1);
		store.set("axb", "long", new byte[] {1}, "1", 2);

		assertEquals(1, store.matching("a.b").size());
		assertEquals("a.b", store.matching("a.b").get(0).getKey());
	}

	@Test
	void sortsAMatchByName() {
		store.set("c", "long", new byte[] {1}, "1", 1);
		store.set("a", "long", new byte[] {1}, "1", 2);
		store.set("b", "long", new byte[] {1}, "1", 3);

		assertEquals(List.of("a", "b", "c"), store.matching("*").stream().map(Map.Entry::getKey).toList());
	}

	@Test
	void handsOutACopyOfTheEntries() {
		store.set("a", "long", new byte[] {1}, "1", 1);
		List<Map.Entry<String, VariableEntry>> entries = store.entries();
		store.set("b", "long", new byte[] {1}, "1", 2);

		assertEquals(1, entries.size());
		assertEquals(2, store.size());
	}
}
