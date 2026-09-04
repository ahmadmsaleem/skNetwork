package sknetwork.spigot.elements.types;

import sknetwork.common.MutationMode;

/**
 * One atomic change on its way to the proxy, already serialised.
 * {@code localName} keeps the prefix; {@code display} is what /sknet dump prints.
 */
public record AtomicChange(MutationMode mode, String localName, String type, byte[] value,
		String expectedType, byte[] expectedValue, String display) {
}
