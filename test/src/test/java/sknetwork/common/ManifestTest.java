package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ManifestTest {

	@Test
	void survivesARoundTrip() throws IOException {
		Manifest original = new Manifest(7, List.of(
				new ScriptEntry("global/a.sk", Manifest.hash("a")),
				new ScriptEntry("hubs/b.sk", Manifest.hash("b"))));

		PacketOut out = new PacketOut(Protocol.MANIFEST);
		original.write(out);
		Manifest read = Manifest.read(out.frame().reader());

		assertEquals(7, read.version());
		assertEquals(2, read.size());
		assertEquals(original.hashesByPath(), read.hashesByPath());
	}

	@Test
	void keepsTheOrderItWasGiven() throws IOException {
		List<ScriptEntry> entries = new ArrayList<>();
		for (int i = 0; i < 20; i++)
			entries.add(new ScriptEntry("global/" + i + ".sk", Manifest.hash("script " + i)));

		PacketOut out = new PacketOut(Protocol.MANIFEST);
		new Manifest(1, entries).write(out);

		assertEquals(entries, Manifest.read(out.frame().reader()).entries());
	}

	@Test
	void carriesAnEmptyLibrary() throws IOException {
		PacketOut out = new PacketOut(Protocol.MANIFEST);
		new Manifest(Manifest.NO_VERSION, List.of()).write(out);
		Manifest read = Manifest.read(out.frame().reader());

		assertEquals(0, read.size());
		assertEquals(Manifest.NO_VERSION, read.version());
	}

	@Test
	void hashesTheSameContentTheSameWay() {
		assertEquals(Manifest.hash("on load:\n\tbroadcast \"hi\""),
				Manifest.hash("on load:\n\tbroadcast \"hi\""));
	}

	@Test
	void noticesAOneCharacterEdit() {
		assertNotEquals(Manifest.hash("broadcast \"hi\""), Manifest.hash("broadcast \"ho\""));
	}

	@Test
	void hashesBytesAndTextAlike() {
		assertEquals(Manifest.hash("café"), Manifest.hash("café".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	@Test
	void producesLowercaseHex() {
		String hash = Manifest.hash("anything");

		assertEquals(64, hash.length());
		assertTrue(hash.matches("[0-9a-f]{64}"));
	}

	@Test
	void keepsTheLastEntryWhenAPathRepeats() {
		Manifest manifest = new Manifest(1, List.of(
				new ScriptEntry("global/a.sk", Manifest.hash("first")),
				new ScriptEntry("global/a.sk", Manifest.hash("second"))));

		assertEquals(1, manifest.size());
		assertEquals(Manifest.hash("second"), manifest.hashesByPath().get("global/a.sk"));
	}
}
