package sknetwork.spigot;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ch.njol.skript.registrations.Classes;
import sknetwork.common.Display;
import ch.njol.skript.variables.Variables;
import ch.njol.skript.variables.VariablesStorage;

/**
 * Puts an inbound delta into Skript's variable map without it bouncing straight
 * back out as a write.
 * Sets and deletes need different calls. Do not merge them: {@code variableLoaded}
 * skips the save queue but ignores null values, so a delete has to go through
 * {@code setVariable} and be suppressed by name instead.
 */
public final class SkriptBridge {

	private static final Method VARIABLE_LOADED = findVariableLoaded();

	/** Names we are about to delete on Skript's behalf. The storage drops one write each. */
	private static final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();

	/** Same idea for sets, used only when the reflective hook is missing. */
	private static final ConcurrentHashMap<String, byte[]> pendingSets = new ConcurrentHashMap<>();

	private static Method findVariableLoaded() {
		try {
			Method method = Variables.class.getDeclaredMethod("variableLoaded",
					String.class, Object.class, VariablesStorage.class);
			method.setAccessible(true);
			return method;
		} catch (NoSuchMethodException | RuntimeException e) {
			return null;
		}
	}

	static boolean hasFastPath() {
		return VARIABLE_LOADED != null;
	}

	/**
	 * Skript lowercases every name a script reads or writes, unless 'case-insensitive
	 * variables' is off in its config, but leaves a name handed to it by a storage
	 * alone. So anything that puts a name into Skript's map, sends one on a script's
	 * behalf, or compares one against a script's has to apply the same rule, or the
	 * two spellings never meet: the value sits in the map under {?coins::Notch} and
	 * every script asks for {?coins::notch}.
	 */
	public static String normalize(String name) {
		return Variables.caseInsensitiveVariables ? name.toLowerCase(Locale.ENGLISH) : name;
	}

	/**
	 * @param name the local Skript name, prefix included
	 * @return false if the value would not deserialise on this server
	 */
	static boolean applySet(String name, String type, byte[] value, VariablesStorage source) {
		if (source == null)
			return false;

		Object deserialized;
		try {
			deserialized = Classes.deserialize(type, value);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
		if (deserialized == null)
			return false;

		if (VARIABLE_LOADED != null) {
			try {
				VARIABLE_LOADED.invoke(null, name, deserialized, source);
				return true;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}

		try {
			pendingSets.put(name, value);
			Variables.setVariable(name, deserialized, null, false);
			return true;
		} catch (RuntimeException e) {
			pendingSets.remove(name);
			return false;
		}
	}

	/** @return null if the value will not deserialise on this server */
	static Object read(String type, byte[] value) {
		if (type == null || value == null)
			return null;
		try {
			return Classes.deserialize(type, value);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** How a value reads, for the proxy's admin commands. */
	static String describe(String type, byte[] value) {
		if (value == null || value.length > Display.MAX_VALUE_BYTES)
			return null;
		return describe(read(type, value));
	}

	static String describe(Object value) {
		if (value == null)
			return null;
		try {
			return Display.shorten(Classes.toString(value));
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	static void applyDelete(String name) {
		pendingDeletes.add(name);
		try {
			Variables.setVariable(name, null, null, false);
		} catch (RuntimeException e) {
			pendingDeletes.remove(name);
			throw e;
		}
	}

	/** @return true if this write is our own delta coming back and must not be sent. */
	static boolean isEcho(String name, byte[] value) {
		if (value == null)
			return pendingDeletes.remove(name);

		// match the bytes too, so a real local write racing an inbound delta still sends
		byte[] expected = pendingSets.get(name);
		if (expected == null || !java.util.Arrays.equals(expected, value))
			return false;
		pendingSets.remove(name, expected);
		return true;
	}

	private SkriptBridge() {
	}
}
