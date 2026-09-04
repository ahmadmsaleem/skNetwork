package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MutationModeTest {

	@Test
	void pinsTheWireOrder() {
		assertEquals(0, MutationMode.SET.ordinal());
		assertEquals(1, MutationMode.DELETE.ordinal());
		assertEquals(2, MutationMode.ADD.ordinal());
		assertEquals(3, MutationMode.REMOVE.ordinal());
		assertEquals(4, MutationMode.REMOVE_ALL.ordinal());
		assertEquals(5, MutationMode.RESET.ordinal());
		assertEquals(6, MutationMode.SET_IF_ABSENT.ordinal());
		assertEquals(7, MutationMode.COMPARE_AND_SET.ordinal());
		assertEquals(8, MutationMode.REMOVE_IF_ABOVE.ordinal());
		assertEquals(9, MutationMode.values().length);
	}

	@ParameterizedTest
	@EnumSource(MutationMode.class)
	void survivesARoundTrip(MutationMode mode) {
		assertEquals(mode, MutationMode.byId(mode.id()));
	}

	@Test
	void refusesAnIdItDoesNotKnow() {
		assertThrows(IllegalArgumentException.class, () -> MutationMode.byId((byte) -1));
		assertThrows(IllegalArgumentException.class,
				() -> MutationMode.byId((byte) MutationMode.values().length));
		assertThrows(IllegalArgumentException.class, () -> MutationMode.byId((byte) 120));
	}
}
