package com.spicypox.minecard.game.blackjack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableRevealTest {
	@Test
	void collectNeverHidesAlreadyRevealedHole() {
		TableReveal reveal = new TableReveal();
		reveal.idleFullyShown(3, true);
		assertFalse(reveal.dealerHoleHidden());

		reveal.startCollect();
		assertTrue(reveal.holeFaceUp());
		assertFalse(reveal.dealerHoleHidden());

		reveal.collectDealerOne();
		assertFalse(reveal.dealerHoleHidden());
		boolean[] flags = reveal.dealerFaceUpFlags(reveal.dealerShown());
		for (boolean up : flags) {
			assertTrue(up);
		}
	}

	@Test
	void dealingHidesHoleUntilReveal() {
		TableReveal reveal = new TableReveal();
		reveal.startDeal();
		reveal.tickCooldown();
		reveal.pulseDeal(); // player 1
		reveal.tickCooldown();
		reveal.pulseDeal(); // dealer 1
		reveal.tickCooldown();
		reveal.pulseDeal(); // player 2
		reveal.tickCooldown();
		reveal.pulseDeal(); // dealer hole
		assertTrue(reveal.dealerHoleHidden());
		reveal.revealHole();
		assertFalse(reveal.dealerHoleHidden());
	}

	@Test
	void dealerPlayWaitsFullStepBeforeFirstPulse() {
		TableReveal reveal = new TableReveal();
		reveal.startDealerPlay(2);
		assertEquals(TableReveal.STEP_TICKS, reveal.cooldown());
		// Must not pulse on the very next tick after Stand.
		assertFalse(reveal.tickCooldown());
		assertEquals(TableReveal.STEP_TICKS - 1, reveal.cooldown());
	}
}
