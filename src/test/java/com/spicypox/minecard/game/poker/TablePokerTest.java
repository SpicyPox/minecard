package com.spicypox.minecard.game.poker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablePokerTest {
	private static PokerSeat seat(String name, long stack) {
		return new PokerSeat(UUID.randomUUID(), name, stack);
	}

	@Test
	void headsUpDealAndFoldWinsPot() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		TablePoker table = new TablePoker("r1", PokerRules.defaults(), List.of(a, b), 42L);
		table.startHand();
		assertEquals(TablePoker.Phase.PREFLOP, table.phase());
		assertEquals(2, a.hole().size());
		assertEquals(2, b.hole().size());
		assertTrue(table.pot() >= 3L);

		UUID actor = table.actingPlayerId();
		assertNotNull(actor);
		assertTrue(table.fold(actor));
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
		assertEquals(1, table.winners().size());
		long total = a.stack() + b.stack();
		assertEquals(200L, total);
	}

	@Test
	void callThroughToShowdown() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		TablePoker table = new TablePoker("r1", new PokerRules(1, 2, 40, 200, 2), List.of(a, b), 7L);
		table.startHand();
		// Preflop: both match / check down every street
		int guard = 0;
		while (table.phase() != TablePoker.Phase.HAND_OVER && guard++ < 40) {
			UUID actor = table.actingPlayerId();
			if (actor == null) {
				break;
			}
			assertTrue(table.checkOrCall(actor));
		}
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
		assertEquals(5, table.board().size());
		assertEquals(200L, a.stack() + b.stack());
	}

	@Test
	void customRaiseIncreasesPot() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		TablePoker table = new TablePoker("r1", new PokerRules(1, 2, 40, 200, 2), List.of(a, b), 99L);
		table.startHand();
		UUID actor = table.actingPlayerId();
		long before = table.pot();
		assertTrue(table.raiseTo(actor, 10L));
		assertTrue(table.pot() > before);
		assertEquals(10L, table.currentBet());
		UUID other = table.actingPlayerId();
		assertNotNull(other);
		assertFalse(other.equals(actor));
		assertTrue(table.fold(other));
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
	}
}
