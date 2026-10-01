package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;

import java.util.ArrayList;
import java.util.List;

public final class BlackjackHand {
	private final List<Card> cards = new ArrayList<>();

	public void add(Card card) {
		cards.add(card);
	}

	public List<Card> cards() {
		return List.copyOf(cards);
	}

	public int size() {
		return cards.size();
	}

	public void clear() {
		cards.clear();
	}

	/** Best total ≤ 21, or minimum bust total. */
	public int score() {
		int total = 0;
		int aces = 0;
		for (Card card : cards) {
			total += rankValue(card.rank());
			if (card.rank() == Rank.ACE) {
				aces++;
			}
		}
		while (total > 21 && aces > 0) {
			total -= 10;
			aces--;
		}
		return total;
	}

	public boolean isSoft() {
		int total = 0;
		int aces = 0;
		for (Card card : cards) {
			total += rankValue(card.rank());
			if (card.rank() == Rank.ACE) {
				aces++;
			}
		}
		return aces > 0 && total <= 21;
	}

	public boolean isBlackjack() {
		return cards.size() == 2 && score() == 21;
	}

	public boolean isBust() {
		return score() > 21;
	}

	private static int rankValue(Rank rank) {
		return switch (rank) {
			case ACE -> 11;
			case TWO -> 2;
			case THREE -> 3;
			case FOUR -> 4;
			case FIVE -> 5;
			case SIX -> 6;
			case SEVEN -> 7;
			case EIGHT -> 8;
			case NINE -> 9;
			case TEN, JACK, QUEEN, KING -> 10;
		};
	}
}
