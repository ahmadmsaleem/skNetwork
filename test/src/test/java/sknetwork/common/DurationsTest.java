package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationsTest {

	@ParameterizedTest
	@CsvSource({
			"100ms, 100",
			"5s, 5000",
			"2m, 120000",
			"2h, 7200000",
			"0s, 0",
			"1h, 3600000"})
	void readsEveryUnit(String written, long expected) {
		assertEquals(expected, Durations.millis(written, -1));
	}

	@Test
	void treatsABareNumberAsMilliseconds() {
		assertEquals(250, Durations.millis("250", -1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"  100ms  ", "100 ms", "100MS", "100Ms"})
	void ignoresCaseAndSpacing(String written) {
		assertEquals(100, Durations.millis(written, -1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "soon", "-5s", "5 seconds", "1.5s", "5d", "s", "5s5s"})
	void fallsBackOnAnythingItCannotRead(String written) {
		assertEquals(999, Durations.millis(written, 999));
	}

	@Test
	void fallsBackOnNothingAtAll() {
		assertEquals(999, Durations.millis(null, 999));
	}
}
