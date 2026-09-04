package sknetwork.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VariableNameTest {

	@Test
	void recognisesATree() {
		assertTrue(VariableName.isTree("coins::*"));
		assertTrue(VariableName.isTree("::*"));
		assertFalse(VariableName.isTree("coins"));
		assertFalse(VariableName.isTree("coins::a"));
		assertFalse(VariableName.isTree("coins*"));
	}

	@Test
	void stripsTheTreeSuffix() {
		assertEquals("coins", VariableName.treeBase("coins::*"));
		assertEquals("a::b", VariableName.treeBase("a::b::*"));
		assertEquals("", VariableName.treeBase("::*"));
	}

	@Test
	void leavesAPlainNameAlone() {
		assertEquals("coins", VariableName.treeBase("coins"));
	}

	@Test
	void countsTheBaseItselfAsInTheTree() {
		assertTrue(VariableName.inTree("coins", "coins"));
	}

	@Test
	void countsEveryDescendant() {
		assertTrue(VariableName.inTree("coins::eult", "coins"));
		assertTrue(VariableName.inTree("coins::eult::gold", "coins"));
		assertTrue(VariableName.inTree("coins::eult::gold", "coins::eult"));
	}

	@Test
	void doesNotMatchOnAPrefixAlone() {
		assertFalse(VariableName.inTree("coinsplitter", "coins"));
		assertFalse(VariableName.inTree("coins2::eult", "coins"));
	}

	@Test
	void anEmptyBaseMeansTheWholeMap() {
		assertTrue(VariableName.inTree("anything", ""));
		assertTrue(VariableName.inTree("a::b::c", ""));
	}

	@Test
	void aSiblingIsNotInTheTree() {
		assertFalse(VariableName.inTree("party::5", "coins"));
	}
}
