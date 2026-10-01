package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot of a solo blackjack round — ready to persist into SavedData later.
 * Demo economy fields ({@code balance}/{@code held}) mirror wallet + escrow.
 */
public final class BlackjackRoundState {
	public final UUID playerId;
	public final BlackjackSession.Phase phase;
	public final List<Card> shoe;
	public final List<Card> dealerCards;
	public final int dealerShown;
	public final boolean holeFaceUp;
	public final List<HandSnapshot> playerHands;
	public final int activeHand;
	public final boolean splitUsed;
	public final long unitBet;
	public final long held;
	public final long balance;
	public final Identifier stakeItemId;
	public final int turnTicksLeft;
	public final boolean dialogAway;
	public final int dealIndex;
	public final int stepCooldown;
	public final BlackjackOutcome primaryOutcome;

	public BlackjackRoundState(
		UUID playerId,
		BlackjackSession.Phase phase,
		List<Card> shoe,
		List<Card> dealerCards,
		int dealerShown,
		boolean holeFaceUp,
		List<HandSnapshot> playerHands,
		int activeHand,
		boolean splitUsed,
		long unitBet,
		long held,
		long balance,
		Identifier stakeItemId,
		int turnTicksLeft,
		boolean dialogAway,
		int dealIndex,
		int stepCooldown,
		BlackjackOutcome primaryOutcome
	) {
		this.playerId = playerId;
		this.phase = phase;
		this.shoe = List.copyOf(shoe);
		this.dealerCards = List.copyOf(dealerCards);
		this.dealerShown = dealerShown;
		this.holeFaceUp = holeFaceUp;
		this.playerHands = List.copyOf(playerHands);
		this.activeHand = activeHand;
		this.splitUsed = splitUsed;
		this.unitBet = unitBet;
		this.held = held;
		this.balance = balance;
		this.stakeItemId = stakeItemId;
		this.turnTicksLeft = turnTicksLeft;
		this.dialogAway = dialogAway;
		this.dealIndex = dealIndex;
		this.stepCooldown = stepCooldown;
		this.primaryOutcome = primaryOutcome;
	}

	public long totalBet() {
		long sum = 0;
		for (HandSnapshot hand : playerHands) {
			sum += hand.bet;
		}
		return sum;
	}

	public Optional<BlackjackOutcome> outcome() {
		return Optional.ofNullable(primaryOutcome);
	}

	public record HandSnapshot(
		List<Card> cards,
		long bet,
		boolean doubled,
		boolean fromSplit,
		boolean aceSplit,
		boolean finished,
		BlackjackOutcome outcome,
		int shown
	) {
		public HandSnapshot {
			cards = List.copyOf(cards);
		}
	}

	/** Mutable builder used when capturing from a live session. */
	public static final class Builder {
		private UUID playerId;
		private BlackjackSession.Phase phase;
		private List<Card> shoe = List.of();
		private List<Card> dealerCards = List.of();
		private int dealerShown;
		private boolean holeFaceUp;
		private final List<HandSnapshot> playerHands = new ArrayList<>();
		private int activeHand;
		private boolean splitUsed;
		private long unitBet;
		private long held;
		private long balance;
		private Identifier stakeItemId = StakeItem.DEFAULT_ID;
		private int turnTicksLeft;
		private boolean dialogAway;
		private int dealIndex;
		private int stepCooldown;
		private BlackjackOutcome primaryOutcome;

		public Builder playerId(UUID id) {
			this.playerId = id;
			return this;
		}

		public Builder phase(BlackjackSession.Phase phase) {
			this.phase = phase;
			return this;
		}

		public Builder shoe(List<Card> shoe) {
			this.shoe = shoe;
			return this;
		}

		public Builder dealerCards(List<Card> dealerCards) {
			this.dealerCards = dealerCards;
			return this;
		}

		public Builder dealerShown(int dealerShown) {
			this.dealerShown = dealerShown;
			return this;
		}

		public Builder holeFaceUp(boolean holeFaceUp) {
			this.holeFaceUp = holeFaceUp;
			return this;
		}

		public Builder addHand(HandSnapshot hand) {
			this.playerHands.add(hand);
			return this;
		}

		public Builder activeHand(int activeHand) {
			this.activeHand = activeHand;
			return this;
		}

		public Builder splitUsed(boolean splitUsed) {
			this.splitUsed = splitUsed;
			return this;
		}

		public Builder unitBet(long unitBet) {
			this.unitBet = unitBet;
			return this;
		}

		public Builder held(long held) {
			this.held = held;
			return this;
		}

		public Builder balance(long balance) {
			this.balance = balance;
			return this;
		}

		public Builder stakeItemId(Identifier stakeItemId) {
			this.stakeItemId = stakeItemId;
			return this;
		}

		public Builder turnTicksLeft(int turnTicksLeft) {
			this.turnTicksLeft = turnTicksLeft;
			return this;
		}

		public Builder dialogAway(boolean dialogAway) {
			this.dialogAway = dialogAway;
			return this;
		}

		public Builder dealIndex(int dealIndex) {
			this.dealIndex = dealIndex;
			return this;
		}

		public Builder stepCooldown(int stepCooldown) {
			this.stepCooldown = stepCooldown;
			return this;
		}

		public Builder primaryOutcome(BlackjackOutcome primaryOutcome) {
			this.primaryOutcome = primaryOutcome;
			return this;
		}

		public BlackjackRoundState build() {
			return new BlackjackRoundState(
				playerId,
				phase,
				shoe,
				dealerCards,
				dealerShown,
				holeFaceUp,
				playerHands,
				activeHand,
				splitUsed,
				unitBet,
				held,
				balance,
				stakeItemId,
				turnTicksLeft,
				dialogAway,
				dealIndex,
				stepCooldown,
				primaryOutcome
			);
		}
	}
}
