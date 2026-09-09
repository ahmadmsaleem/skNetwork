package sknetwork.spigot.elements.types;

import sknetwork.common.MutationMode;

public record AtomicChange(MutationMode mode, String localName, String type, byte[] value,
		String expectedType, byte[] expectedValue, String display) {
}
