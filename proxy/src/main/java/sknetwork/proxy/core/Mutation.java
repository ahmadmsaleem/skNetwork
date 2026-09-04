package sknetwork.proxy.core;

import sknetwork.common.MutationMode;

/** One MUTATE frame, decoded. */
record Mutation(long requestId, MutationMode mode, String name, String type, byte[] value,
		String expectedType, byte[] expectedValue, boolean returnable, String display) {
}
