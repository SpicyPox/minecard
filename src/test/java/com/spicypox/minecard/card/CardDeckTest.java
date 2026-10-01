package com.spicypox.minecard.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardDeckTest {
	@Test
	void standardDeckHas52UniqueCards() {
		var cards = Card.standard52();
		assertEquals(52, cards.size());
		Set<String> keys = new HashSet<>();
		for (Card card : cards) {
			assertTrue(keys.add(card.textureKey()), "duplicate " + card.textureKey());
		}
		assertEquals(4, Suit.values().length);
		assertEquals(13, Rank.values().length);
	}

	@Test
	void glyphsBootstrapLoads52Faces() {
		CardGlyphs.bootstrap();
		assertEquals(52, CardGlyphs.count());
	}
}
