package sknetwork.proxy.core;

final class Numbers {

	static boolean isNumeric(String type) {
		return isIntegral(type) || isFloating(type);
	}

	static boolean isIntegral(String type) {
		return switch (type) {
			case "long", "integer", "short", "byte" -> true;
			default -> false;
		};
	}

	private static boolean isFloating(String type) {
		return type.equals("double") || type.equals("float");
	}

	private static long bits(byte[] value) {
		long bits = 0;
		for (byte b : value)
			bits = (bits << 8) | (b & 0xFFL);
		return bits;
	}

	/** @throws IllegalArgumentException if the type is not a whole number we know */
	static long readLong(String type, byte[] value) {
		if (!isIntegral(type))
			throw new IllegalArgumentException("'" + type + "' is not a whole number");
		return signed(bits(value), value.length);
	}

	/** @throws IllegalArgumentException if the type is not a number we know */
	static double readDouble(String type, byte[] value) {
		long bits = bits(value);
		return switch (type) {
			case "double" -> Double.longBitsToDouble(bits);
			case "float" -> Float.intBitsToFloat((int) bits);
			case "long", "integer", "short", "byte" -> signed(bits, value.length);
			default -> throw new IllegalArgumentException("'" + type + "' is not a number");
		};
	}

	private static long signed(long bits, int width) {
		int unused = 64 - width * 8;
		return unused <= 0 ? bits : (bits << unused) >> unused;
	}

	/**
	 * How the proxy prints a number it worked out itself. Skript trims a whole
	 * double to "2" rather than "2.0", so this does too.
	 */
	static String display(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15)
			return Long.toString((long) value);
		return Double.toString(value);
	}

	static byte[] writeLong(long value) {
		byte[] out = new byte[8];
		for (int i = 7; i >= 0; i--) {
			out[i] = (byte) (value & 0xFF);
			value >>= 8;
		}
		return out;
	}

	static byte[] writeDouble(double value) {
		return writeLong(Double.doubleToLongBits(value));
	}

	private Numbers() {
	}
}
