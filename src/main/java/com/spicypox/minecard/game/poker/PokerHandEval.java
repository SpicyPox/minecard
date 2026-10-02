package com.spicypox.minecard.game.poker;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import com.spicypox.minecard.card.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Best 5-card Hold'em hand from 5–7 cards. */
public final class PokerHandEval {
	private PokerHandEval() {
	}

	public static int pokerValue(Rank rank) {
		return switch (rank) {
			case ACE -> 14;
			case TWO -> 2;
			case THREE -> 3;
			case FOUR -> 4;
			case FIVE -> 5;
			case SIX -> 6;
			case SEVEN -> 7;
			case EIGHT -> 8;
			case NINE -> 9;
			case TEN -> 10;
			case JACK -> 11;
			case QUEEN -> 12;
			case KING -> 13;
		};
	}

	public static PokerHandRank bestOf(List<Card> hole, List<Card> board) {
		List<Card> all = new ArrayList<>(hole.size() + board.size());
		all.addAll(hole);
		all.addAll(board);
		return bestOf(all);
	}

	public static PokerHandRank bestOf(List<Card> cards) {
		if (cards == null || cards.size() < 5) {
			throw new IllegalArgumentException("need at least 5 cards");
		}
		if (cards.size() == 5) {
			return rankFive(cards);
		}
		PokerHandRank best = null;
		int n = cards.size();
		for (int a = 0; a < n - 4; a++) {
			for (int b = a + 1; b < n - 3; b++) {
				for (int c = b + 1; c < n - 2; c++) {
					for (int d = c + 1; d < n - 1; d++) {
						for (int e = d + 1; e < n; e++) {
							PokerHandRank r = rankFive(List.of(
								cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)
							));
							if (best == null || r.compareTo(best) > 0) {
								best = r;
							}
						}
					}
				}
			}
		}
		return best;
	}

	static PokerHandRank rankFive(List<Card> five) {
		int[] vals = new int[5];
		Suit[] suits = new Suit[5];
		for (int i = 0; i < 5; i++) {
			vals[i] = pokerValue(five.get(i).rank());
			suits[i] = five.get(i).suit();
		}
		boolean flush = suits[0] == suits[1] && suits[1] == suits[2]
			&& suits[2] == suits[3] && suits[3] == suits[4];
		int[] sorted = vals.clone();
		Arrays.sort(sorted);
		int[] desc = new int[]{sorted[4], sorted[3], sorted[2], sorted[1], sorted[0]};
		int straightHigh = straightHigh(sorted);

		if (flush && straightHigh > 0) {
			return new PokerHandRank(PokerHandCategory.STRAIGHT_FLUSH, straightHigh);
		}

		int[] counts = new int[15];
		for (int v : vals) {
			counts[v]++;
		}
		List<Integer> quads = new ArrayList<>();
		List<Integer> trips = new ArrayList<>();
		List<Integer> pairs = new ArrayList<>();
		List<Integer> singles = new ArrayList<>();
		for (int v = 14; v >= 2; v--) {
			switch (counts[v]) {
				case 4 -> quads.add(v);
				case 3 -> trips.add(v);
				case 2 -> pairs.add(v);
				case 1 -> singles.add(v);
				default -> {
				}
			}
		}

		if (!quads.isEmpty()) {
			int q = quads.getFirst();
			int kicker = !singles.isEmpty() ? singles.getFirst()
				: (!trips.isEmpty() ? trips.getFirst() : (!pairs.isEmpty() ? pairs.getFirst() : 0));
			return new PokerHandRank(PokerHandCategory.FOUR_OF_A_KIND, q, kicker);
		}
		if (!trips.isEmpty() && (!pairs.isEmpty() || trips.size() >= 2)) {
			int t = trips.getFirst();
			int p = !pairs.isEmpty() ? pairs.getFirst() : trips.get(1);
			return new PokerHandRank(PokerHandCategory.FULL_HOUSE, t, p);
		}
		if (flush) {
			return new PokerHandRank(PokerHandCategory.FLUSH, desc);
		}
		if (straightHigh > 0) {
			return new PokerHandRank(PokerHandCategory.STRAIGHT, straightHigh);
		}
		if (!trips.isEmpty()) {
			int t = trips.getFirst();
			return new PokerHandRank(
				PokerHandCategory.THREE_OF_A_KIND,
				t,
				singles.size() > 0 ? singles.get(0) : 0,
				singles.size() > 1 ? singles.get(1) : 0
			);
		}
		if (pairs.size() >= 2) {
			return new PokerHandRank(
				PokerHandCategory.TWO_PAIR,
				pairs.get(0),
				pairs.get(1),
				singles.isEmpty() ? 0 : singles.getFirst()
			);
		}
		if (pairs.size() == 1) {
			int p = pairs.getFirst();
			return new PokerHandRank(
				PokerHandCategory.ONE_PAIR,
				p,
				singles.size() > 0 ? singles.get(0) : 0,
				singles.size() > 1 ? singles.get(1) : 0,
				singles.size() > 2 ? singles.get(2) : 0
			);
		}
		return new PokerHandRank(PokerHandCategory.HIGH_CARD, desc);
	}

	/** High card of straight, or 0; wheel A-2-3-4-5 → 5. */
	private static int straightHigh(int[] ascending) {
		if (ascending[0] == 2 && ascending[1] == 3 && ascending[2] == 4
			&& ascending[3] == 5 && ascending[4] == 14) {
			return 5;
		}
		for (int i = 1; i < 5; i++) {
			if (ascending[i] != ascending[i - 1] + 1) {
				return 0;
			}
		}
		return ascending[4];
	}
}
