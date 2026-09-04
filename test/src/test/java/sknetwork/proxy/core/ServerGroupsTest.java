package sknetwork.proxy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ServerGroupsTest {

	@Test
	void reachesTheServersItLists() {
		ServerGroups groups = of("hubs", List.of("lobby", "lobby2"));

		assertTrue(groups.reaches("hubs", "lobby"));
		assertTrue(groups.reaches("hubs", "lobby2"));
		assertFalse(groups.reaches("hubs", "survival"));
	}

	@Test
	void globalReachesEveryServer() {
		ServerGroups groups = new ServerGroups(Map.of());

		assertTrue(groups.reaches(ServerGroups.GLOBAL, "lobby"));
		assertTrue(groups.reaches(ServerGroups.GLOBAL, "a server nobody configured"));
		assertTrue(groups.isDefined(ServerGroups.GLOBAL));
	}

	@Test
	void refusesToLetGlobalBeRedefined() {
		ServerGroups groups = of("global", List.of("lobby"));

		assertEquals(1, groups.problems().size());
		assertTrue(groups.problems().get(0).contains("reserved"));
		assertTrue(groups.reaches("global", "survival"));
		assertFalse(groups.names().contains("global"));
	}

	@Test
	void refusesToLetGlobalBeRedefinedInAnyCase() {
		assertEquals(1, of("GLOBAL", List.of("lobby")).problems().size());
		assertEquals(1, of("Global", List.of("lobby")).problems().size());
	}

	@Test
	void followsAReferenceToAnotherGroup() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("hubs", List.of("lobby", "lobby2"));
		configured.put("survival", List.of("survival1"));
		configured.put("everything", List.of("hubs/", "survival/", "creative"));

		ServerGroups groups = new ServerGroups(configured);

		assertEquals(Set.of("lobby", "lobby2", "survival1", "creative"),
				groups.resolve("everything", new LinkedHashSet<>()));
		assertTrue(groups.reaches("everything", "lobby"));
		assertTrue(groups.reaches("everything", "survival1"));
		assertTrue(groups.reaches("everything", "creative"));
	}

	@Test
	void followsAChainOfReferences() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("a", List.of("server-a"));
		configured.put("b", List.of("a/", "server-b"));
		configured.put("c", List.of("b/", "server-c"));

		ServerGroups groups = new ServerGroups(configured);

		assertEquals(Set.of("server-a", "server-b", "server-c"),
				groups.resolve("c", new LinkedHashSet<>()));
		assertTrue(groups.problems().isEmpty());
	}

	@Test
	void reportsAGroupThatIncludesItself() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("a", List.of("b/"));
		configured.put("b", List.of("a/"));

		ServerGroups groups = new ServerGroups(configured);

		assertFalse(groups.problems().isEmpty());
		assertTrue(groups.problems().get(0).contains("includes itself"));
	}

	@Test
	void reportsAGroupThatNamesItselfDirectly() {
		ServerGroups groups = of("loop", List.of("loop/"));

		assertFalse(groups.problems().isEmpty());
		assertTrue(groups.problems().get(0).contains("includes itself"));
	}

	@Test
	void resolvesACycleWithoutRunningOutOfStack() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("a", List.of("b/", "server-a"));
		configured.put("b", List.of("a/", "server-b"));

		ServerGroups groups = new ServerGroups(configured);

		assertEquals(Set.of("server-a", "server-b"), groups.resolve("a", new LinkedHashSet<>()));
	}

	@Test
	void handsBackNothingForAGroupNobodyDefined() {
		ServerGroups groups = of("hubs", List.of("lobby"));

		assertFalse(groups.isDefined("minigames"));
		assertTrue(groups.resolve("minigames", new LinkedHashSet<>()).isEmpty());
		assertFalse(groups.reaches("minigames", "lobby"));
	}

	@Test
	void treatsAMissingMemberListAsEmpty() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("empty", null);

		ServerGroups groups = new ServerGroups(configured);

		assertTrue(groups.isDefined("empty"));
		assertTrue(groups.resolve("empty", new LinkedHashSet<>()).isEmpty());
	}

	@Test
	void namesTheFoldersNoGroupCovers() {
		ServerGroups groups = of("hubs", List.of("lobby"));

		assertEquals(List.of("minigames"), groups.undefined(List.of("global", "hubs", "minigames")));
		assertTrue(groups.undefined(List.of("global", "hubs")).isEmpty());
	}

	@Test
	void keepsTheGroupNamesInFileOrder() {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put("zulu", List.of());
		configured.put("alpha", List.of());

		assertEquals(List.of("zulu", "alpha"), List.copyOf(new ServerGroups(configured).names()));
	}

	private static ServerGroups of(String name, List<String> members) {
		Map<String, List<String>> configured = new LinkedHashMap<>();
		configured.put(name, members);
		return new ServerGroups(configured);
	}
}
