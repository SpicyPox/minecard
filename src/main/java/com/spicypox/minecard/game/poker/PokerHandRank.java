package com.spicypox.minecard.game.poker;

import java.util.Arrays;

/** Comparable Hold'em hand: category then kickers (14=A … 2). */
public final class PokerHandRank implements Comparable<PokerHandRank> {
	private final PokerHandCategory category;
	private final int[] kickers;

	public PokerHandRank(PokerHandCategory category, int... kickers) {
		this.category = category;
		this.kickers = kickers != null ? Arrays.copyOf(kickers, kickers.length) : new int[0];
	}

	public PokerHandCategory category() {
		return category;
	}

	public int[] kickers() {
		return Arrays.copyOf(kickers, kickers.length);
	}

	@Override
	public int compareTo(PokerHandRank o) {
		int c = Integer.compare(category.ordinal(), o.category.ordinal());
		if (c != 0) {
			return c;
		}
		int n = Math.max(kickers.length, o.kickers.length);
		for (int i = 0; i < n; i++) {
			int a = i < kickers.length ? kickers[i] : 0;
			int b = i < o.kickers.length ? o.kickers[i] : 0;
			int k = Integer.compare(a, b);
			if (k != 0) {
				return k;
			}
		}
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof PokerHandRank other && compareTo(other) == 0;
	}

	@Override
	public int hashCode() {
		return 31 * category.hashCode() + Arrays.hashCode(kickers);
	}

	@Override
	public String toString() {
		return category + Arrays.toString(kickers);
	}
}
