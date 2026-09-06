package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class NamePatternsTest {

	@Test
	void matchesNothingWhenEmpty() {
		assertFalse(NamePatterns.none().matches("anything"));
		assertTrue(NamePatterns.of(List.of()).isEmpty());
		assertTrue(NamePatterns.of(null).isEmpty());
	}

	@Test
	void withoutAStarTheNameHasToMatchExactly() {
		NamePatterns patterns = NamePatterns.of(List.of("apikey"));

		assertTrue(patterns.matches("apikey"));
		assertFalse(patterns.matches("apikey::live"));
		assertFalse(patterns.matches("my apikey"));
	}

	@Test
	void aStarCoversAWholeTree() {
		NamePatterns patterns = NamePatterns.of(List.of("session::*"));

		assertTrue(patterns.matches("session::eult"));
		assertTrue(patterns.matches("session::a::b"));
		assertFalse(patterns.matches("session"));
		assertFalse(patterns.matches("mysession::eult"));
	}

	@Test
	void anyOnePatternIsEnough() {
		NamePatterns patterns = NamePatterns.of(List.of("session::*", "apikey"));

		assertTrue(patterns.matches("session::x"));
		assertTrue(patterns.matches("apikey"));
		assertFalse(patterns.matches("coins::eult"));
	}

	@Test
	void blankEntriesDoNotMatchEverything() {
		NamePatterns patterns = NamePatterns.of(Arrays.asList("", "   ", null));

		assertTrue(patterns.isEmpty());
		assertFalse(patterns.matches("coins"));
		assertFalse(patterns.matches(""));
	}

	@Test
	void regexPunctuationInANameIsLiteral() {
		NamePatterns patterns = NamePatterns.of(List.of("a.b(c)"));

		assertTrue(patterns.matches("a.b(c)"));
		assertFalse(patterns.matches("axbxc"));
	}

	@Test
	void aBareStarCoversEverything() {
		NamePatterns patterns = NamePatterns.of(List.of("*"));

		assertTrue(patterns.matches("coins::eult"));
		assertTrue(patterns.matches(""));
	}
}
