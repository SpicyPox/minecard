package com.spicypox.minecard.card;

import java.util.ArrayList;
import java.util.List;

public record Card(Suit suit, Rank rank) {
	public String textureKey() {
		return suit.id() + "_" + rank.id();
	}

	public String displayNameVi() {
		return rank.viLabel() + " " + suit.viName();
	}

	public static List<Card> standard52() {
		List<Card> cards = new ArrayList<>(52);
		for (Suit suit : Suit.values()) {
			for (Rank rank : Rank.values()) {
				cards.add(new Card(suit, rank));
			}
		}
		return List.copyOf(cards);
	}
}
