package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplayTest {

	@Test
	void keepsAShortValueAsItIs() {
		assertEquals("100", Display.shorten("100"));
	}

	@Test
	void hasNothingToShowForNothing() {
		assertNull(Display.shorten(null));
		assertNull(Display.shorten(""));
	}

	@Test
	void flattensNewlinesAndControlCharacters() {
		assertEquals("a b c", Display.shorten("a\nb\tc"));
		assertEquals("first second", Display.shorten("first\r\nsecond".replace("\r\n", "\n")));
	}

	@Test
	void keepsALineThatFitsExactly() {
		String edge = "x".repeat(Display.MAX_LENGTH);

		assertEquals(edge, Display.shorten(edge));
		assertFalse(Display.shorten(edge).endsWith("…"));
	}

	@Test
	void trimsAnythingLonger() {
		String shortened = Display.shorten("x".repeat(Display.MAX_LENGTH + 400));

		assertEquals(Display.MAX_LENGTH + 1, shortened.length());
		assertTrue(shortened.endsWith("…"));
	}

	@Test
	void survivesAValueThatIsAllControlCharacters() {
		assertEquals("   ", Display.shorten("\n\r\t"));
	}
}
