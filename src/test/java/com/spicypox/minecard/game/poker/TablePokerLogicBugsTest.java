package com.spicypox.minecard.game.poker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for logic holes found in audit. */
class TablePokerLogicBugsTest {
	private static PokerSeat seat(String name, long stack) {
		return new PokerSeat(UUID.randomUUID(), name, stack);
	}

	@Test
	void bustedSeatDoesNotBlockFoldWin() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		PokerSeat broke = seat("Broke", 0);
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 4), List.of(a, b, broke), 1L);
		table.startHand();
		assertEquals(TablePoker.Phase.PREFLOP, table.phase());
		UUID actor = table.actingPlayerId();
		assertTrue(table.fold(actor));
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
		assertEquals(1, table.winners().size());
		assertEquals(200L, a.stack() + b.stack() + broke.stack());
	}

	@Test
	void advanceButtonSkipsBustedSeats() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		PokerSeat broke = seat("Broke", 0);
		// Seat order: A(button=0), Broke, B — after hand, button must not land on Broke.
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 4), List.of(a, broke, b), 2L);
		table.startHand();
		while (table.phase() != TablePoker.Phase.HAND_OVER) {
			UUID actor = table.actingPlayerId();
			if (actor == null) {
				break;
			}
			table.checkOrCall(actor);
		}
		assertEquals(0, table.buttonIndex());
		table.advanceButton();
		assertNotEquals(1, table.buttonIndex(), "button must skip busted seat");
		assertEquals(2, table.buttonIndex());
	}

	@Test
	void halfPotPresetNeverBelowMinRaise() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 2), List.of(a, b), 3L);
		table.startHand();
		UUID actor = table.actingPlayerId();
		assertTrue(table.raiseTo(actor, 10L));
		UUID other = table.actingPlayerId();
		long minTo = table.minRaiseTo(other);
		// half pot may be below min — must still succeed as legal min raise (or all-in)
		assertTrue(table.raisePreset(other, "half_pot"));
		assertTrue(table.currentBet() >= minTo || table.seat(other).orElseThrow().allIn());
	}

	@Test
	void chipsConservedThroughShowdown() {
		PokerSeat a = seat("A", 50);
		PokerSeat b = seat("B", 50);
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 2), List.of(a, b), 11L);
		table.startHand();
		int guard = 0;
		while (table.phase() != TablePoker.Phase.HAND_OVER && guard++ < 50) {
			UUID actor = table.actingPlayerId();
			if (actor == null) {
				break;
			}
			assertTrue(table.checkOrCall(actor));
		}
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
		assertEquals(0L, table.pot());
		assertEquals(100L, a.stack() + b.stack());
	}

	@Test
	void abortHandReturnsPotToStacks() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 2), List.of(a, b), 13L);
		table.startHand();
		assertTrue(table.pot() > 0L);
		assertTrue(table.inHand());
		table.abortHand();
		assertFalse(table.inHand());
		assertEquals(0L, table.pot());
		assertEquals(200L, a.stack() + b.stack());
	}

	@Test
	void threeWayFoldAwardsOnlyLiveWinner() {
		PokerSeat a = seat("A", 100);
		PokerSeat b = seat("B", 100);
		PokerSeat c = seat("C", 100);
		TablePoker table = new TablePoker("r", new PokerRules(1, 2, 40, 200, 4), List.of(a, b, c), 5L);
		table.startHand();
		assertTrue(table.fold(table.actingPlayerId()));
		assertTrue(table.fold(table.actingPlayerId()));
		assertEquals(TablePoker.Phase.HAND_OVER, table.phase());
		assertEquals(1, table.winners().size());
		assertEquals(300L, a.stack() + b.stack() + c.stack());
		assertFalse(table.winners().isEmpty());
	}
}
