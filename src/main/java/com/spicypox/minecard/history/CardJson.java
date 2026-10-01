package com.spicypox.minecard.history;

import com.spicypox.minecard.card.Card;

import java.util.List;

public final class CardJson {
	private CardJson() {
	}

	public static String of(List<Card> cards) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < cards.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			Card c = cards.get(i);
			char suit = switch (c.suit()) {
				case SPADES -> 'S';
				case HEARTS -> 'H';
				case DIAMONDS -> 'D';
				case CLUBS -> 'C';
			};
			sb.append("{\"s\":\"").append(suit).append("\",\"r\":\"")
				.append(c.rank().shortLabel()).append("\"}");
		}
		sb.append(']');
		return sb.toString();
	}
}
