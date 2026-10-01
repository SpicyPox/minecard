package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.config.MinecardConfig;
import com.spicypox.minecard.wallet.WalletConstants;
import com.spicypox.minecard.wallet.Wallets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackSessionTest {
	@BeforeEach
	void resetBank() {
		Wallets.useMemoryForTests();
		Wallets.resetAll();
		// Deterministic deal tests: skip insurance branch unless a test enables it.
		MinecardConfig.insuranceEnabled = false;
		MinecardConfig.surrenderEnabled = false;
	}

	private static void advancePastDeal(BlackjackSession session) {
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALING; i++) {
			session.tick();
		}
		if (session.phase() == BlackjackSession.Phase.INSURANCE) {
			session.declineInsurance();
		}
	}

	@Test
	void fixedSeedPreDealsDeterministicHands() {
		UUID id = UUID.randomUUID();
		BlackjackSession a = new BlackjackSession(id, 42L);
		Wallets.useMemoryForTests();
		Wallets.setBalance(id, StakeItem.DEFAULT_ID, WalletConstants.STARTING_BALANCE);
		BlackjackSession b = new BlackjackSession(id, 42L);
		assertEquals(a.playerHand().cards(), b.playerHand().cards());
		assertEquals(a.dealerHand().cards(), b.dealerHand().cards());
	}

	@Test
	void dealAnimationReachesPlayerTurnOrResolved() {
		BlackjackSession session = new BlackjackSession(7L);
		assertEquals(BlackjackSession.Phase.DEALING, session.phase());
		advancePastDeal(session);
		assertTrue(
			session.phase() == BlackjackSession.Phase.PLAYER_TURN
				|| session.phase() == BlackjackSession.Phase.RESOLVED
		);
		assertEquals(2, session.visiblePlayerCards(0).size());
	}

	@Test
	void standResolvesRound() {
		BlackjackSession session = new BlackjackSession(7L);
		advancePastDeal(session);
		if (session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
			session.stand();
		}
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALER_TURN; i++) {
			session.tick();
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
		assertTrue(session.outcome().isPresent());
		assertEquals(0L, session.held());
	}

	@Test
	void dealerHitsAreAnimatedOneByOne() {
		BlackjackSession session = new BlackjackSession(7L);
		advancePastDeal(session);
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return;
		}
		session.stand();
		if (session.phase() != BlackjackSession.Phase.DEALER_TURN) {
			return; // dealer already >= 17 with two cards — no hit animation
		}
		assertTrue(session.reveal().holeFaceUp());
		int shown = session.visibleDealerCards().size();
		assertEquals(2, shown);
		// First STEP_TICKS ticks must not dump hits (cooldown after hole flip).
		for (int i = 0; i < TableReveal.STEP_TICKS; i++) {
			session.tick();
			assertEquals(2, session.visibleDealerCards().size());
		}
		// Next pulse may hit (+1) or enter settle-pause / resolve if already ≥17.
		for (int i = 0; i < 80 && session.phase() == BlackjackSession.Phase.DEALER_TURN; i++) {
			session.tick();
			int now = session.visibleDealerCards().size();
			if (now > shown) {
				assertEquals(shown + 1, now);
				return;
			}
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
	}

	@Test
	void playAgainStartsCollectThenDeal() {
		BlackjackSession session = new BlackjackSession(99L);
		advancePastDeal(session);
		if (session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
			session.stand();
		}
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALER_TURN; i++) {
			session.tick();
		}
		session.playAgain();
		assertEquals(BlackjackSession.Phase.COLLECTING, session.phase());
	}

	@Test
	void playAgainCollectKeepsDealerHoleFaceUp() {
		BlackjackSession session = new BlackjackSession(99L);
		advancePastDeal(session);
		if (session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
			session.stand();
		}
		for (int i = 0; i < 200 && session.phase() == BlackjackSession.Phase.DEALER_TURN; i++) {
			session.tick();
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
		assertTrue(session.reveal().holeFaceUp());
		int dealerBefore = session.visibleDealerCards().size();
		assertTrue(dealerBefore >= 2);

		session.playAgain();
		assertEquals(BlackjackSession.Phase.COLLECTING, session.phase());
		// Bug regression: must not flip the hole while cards are still on the table.
		assertTrue(session.reveal().holeFaceUp());
		assertTrue(!session.dealerHoleHidden());
		boolean[] flags = session.dealerFaceUpFlags();
		assertEquals(dealerBefore, flags.length);
		for (boolean faceUp : flags) {
			assertTrue(faceUp);
		}
	}

	@Test
	void beginDealLocksUnitBetFromBalanceOfStakeTypeOnly() {
		UUID id = UUID.randomUUID();
		Wallets.setBalance(id, StakeItem.DEFAULT_ID, 100L);
		Wallets.setBalance(id, net.minecraft.resources.Identifier.withDefaultNamespace("apple"), 999L);
		BlackjackSession session = new BlackjackSession(id, 1L);
		assertEquals(StakeItem.DEFAULT_ID, session.stakeItemId());
		assertEquals(WalletConstants.DEFAULT_BET, session.totalBet());
		assertEquals(WalletConstants.DEFAULT_BET, session.held());
		assertEquals(100L - WalletConstants.DEFAULT_BET, session.balance());
		// Other item balances untouched.
		assertEquals(999L, Wallets.balance(id, net.minecraft.resources.Identifier.withDefaultNamespace("apple")));
	}

	@Test
	void snapshotRoundTripPreservesPhaseAndStakes() {
		UUID id = UUID.randomUUID();
		BlackjackSession session = new BlackjackSession(id, 11L);
		advancePastDeal(session);
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
		assertEquals(5L, BlackjackSession.settleAmount(10L, BlackjackOutcome.SURRENDER));
	}

	@Test
	void leaveRefundsHeld() {
		UUID id = UUID.randomUUID();
		Wallets.setBalance(id, StakeItem.DEFAULT_ID, 100L);
		BlackjackSession session = new BlackjackSession(id, 3L);
		long held = session.held();
		long bal = session.balance();
		session.leave();
		assertEquals(0L, session.held());
		assertEquals(bal + held, session.balance());
		assertEquals(bal + held, Wallets.balance(id, StakeItem.DEFAULT_ID));
	}

	@Test
	void emergencyTimeoutWhileAwayForfeits() {
		BlackjackSession session = new BlackjackSession(7L);
		advancePastDeal(session);
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return; // natural BJ — nothing to time out
		}
		long balBefore = session.balance();
		session.emergencyAway();
		assertTrue(session.dialogAway());
		for (int i = 0; i < BlackjackSession.turnTicks() + 5; i++) {
			session.tickTurnTimer();
		}
		assertEquals(BlackjackSession.Phase.RESOLVED, session.phase());
		assertEquals(BlackjackOutcome.LOSE, session.outcome().orElseThrow());
		assertEquals(0L, session.held());
		assertEquals(balBefore, session.balance());
		assertTrue(!session.dialogAway());
	}
}
