package com.spicypox.minecard.game.blackjack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackSessionTest {
	@BeforeEach
	void resetBank() {
		DemoBank.resetAll();
	}

	@Test
	void fixedSeedPreDealsDeterministicHands() {
		UUID id = UUID.randomUUID();
		BlackjackSession a = new BlackjackSession(id, 42L);
		DemoBank.resetAll();
		DemoBank.setBalance(id, StakeItem.DEFAULT_ID, DemoBank.STARTING_BALANCE);
		BlackjackSession b = new BlackjackSession(id, 42L);
		assertEquals(a.playerHand().cards(), b.playerHand().cards());
		assertEquals(a.dealerHand().cards(), b.dealerHand().cards());
	}

	@Test
	void dealAnimationReachesPlayerTurnOrResolved() {
		BlackjackSession session = new BlackjackSession(7L);
		assertEquals(BlackjackSession.Phase.DEALING, session.phase());
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		assertTrue(
			session.phase() == BlackjackSession.Phase.PLAYER_TURN
				|| session.phase() == BlackjackSession.Phase.RESOLVED
		);
		assertEquals(2, session.visiblePlayerCards(0).size());
	}

	@Test
	void standResolvesRound() {
		BlackjackSession session = new BlackjackSession(7L);
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		if (session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
			session.stand();
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
		assertTrue(session.outcome().isPresent());
		assertEquals(0L, session.held());
	}

	@Test
	void playAgainStartsCollectThenDeal() {
		BlackjackSession session = new BlackjackSession(99L);
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		if (session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
			session.stand();
		}
		session.playAgain();
		assertEquals(BlackjackSession.Phase.COLLECTING, session.phase());
	}

	@Test
	void beginDealLocksUnitBetFromBalanceOfStakeTypeOnly() {
		UUID id = UUID.randomUUID();
		DemoBank.setBalance(id, StakeItem.DEFAULT_ID, 100L);
		DemoBank.setBalance(id, net.minecraft.resources.Identifier.withDefaultNamespace("apple"), 999L);
		BlackjackSession session = new BlackjackSession(id, 1L);
		assertEquals(StakeItem.DEFAULT_ID, session.stakeItemId());
		assertEquals(DemoBank.DEFAULT_BET, session.totalBet());
		assertEquals(DemoBank.DEFAULT_BET, session.held());
		assertEquals(100L - DemoBank.DEFAULT_BET, session.balance());
		// Other item balances untouched.
		assertEquals(999L, DemoBank.balance(id, net.minecraft.resources.Identifier.withDefaultNamespace("apple")));
	}

	@Test
	void snapshotRoundTripPreservesPhaseAndStakes() {
		UUID id = UUID.randomUUID();
		BlackjackSession session = new BlackjackSession(id, 11L);
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		BlackjackRoundState state = session.snapshot();
		BlackjackSession restored = BlackjackSession.fromState(state);
		assertEquals(session.phase(), restored.phase());
		assertEquals(session.totalBet(), restored.totalBet());
		assertEquals(session.held(), restored.held());
		assertEquals(session.balance(), restored.balance());
		assertEquals(session.stakeItemId(), restored.stakeItemId());
		assertEquals(session.playerHand().cards(), restored.playerHand().cards());
		assertEquals(session.dealerHand().cards(), restored.dealerHand().cards());
	}

	@Test
	void settleBlackjackPaysThreeToTwo() {
		assertEquals(25L, BlackjackSession.settleAmount(10L, BlackjackOutcome.PLAYER_BLACKJACK));
		assertEquals(20L, BlackjackSession.settleAmount(10L, BlackjackOutcome.WIN));
		assertEquals(10L, BlackjackSession.settleAmount(10L, BlackjackOutcome.PUSH));
		assertEquals(0L, BlackjackSession.settleAmount(10L, BlackjackOutcome.LOSE));
	}

	@Test
	void leaveRefundsHeld() {
		UUID id = UUID.randomUUID();
		DemoBank.setBalance(id, StakeItem.DEFAULT_ID, 100L);
		BlackjackSession session = new BlackjackSession(id, 3L);
		long held = session.held();
		long bal = session.balance();
		session.leave();
		assertEquals(0L, session.held());
		assertEquals(bal + held, session.balance());
		assertEquals(bal + held, DemoBank.balance(id, StakeItem.DEFAULT_ID));
	}

	@Test
	void emergencyTimeoutWhileAwayForfeits() {
		BlackjackSession session = new BlackjackSession(7L);
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return; // natural BJ — nothing to time out
		}
		long balBefore = session.balance();
		session.emergencyAway();
		assertTrue(session.dialogAway());
		for (int i = 0; i < BlackjackSession.TURN_TICKS + 5; i++) {
			session.tickTurnTimer();
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
		assertEquals(BlackjackOutcome.LOSE, session.outcome().orElseThrow());
		assertEquals(0L, session.held());
		assertEquals(balBefore, session.balance());
		assertTrue(!session.dialogAway());
	}
}
