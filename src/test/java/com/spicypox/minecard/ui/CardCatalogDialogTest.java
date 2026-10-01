package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.CardGlyphs;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardCatalogDialogTest {
	@BeforeAll
	static void boot() {
		CardGlyphs.bootstrap();
	}

	@Test
	void catalogExposes52DistinctFaceGlyphs() {
		var glyphs = CardCatalogDialog.faceGlyphs();
		assertEquals(52, glyphs.size());

		Set<String> chars = new HashSet<>();
		for (Component glyph : glyphs) {
			String plain = glyph.getString();
			assertEquals(1, plain.length(), "glyph should be one BMP char: " + plain);
			assertTrue(chars.add(plain), "duplicate glyph char " + plain);
			assertEquals(CardGlyphs.FONT, glyph.getStyle().getFont());
		}
		assertEquals(52, chars.size());
	}

	@Test
	void pokerHasFiveHorizontalGrids() {
		List<CardGrid> grids = GameTableShowcases.poker(GameTableShowcases.SEED);
		assertEquals(5, grids.size());
		assertEquals(5, grids.getFirst().cards().size());
		for (int i = 1; i < 5; i++) {
			assertEquals(2, grids.get(i).cards().size());
		}
	}

	@Test
	void blackjackHasDealerAndFourPlayers() {
		List<CardGrid> grids = GameTableShowcases.blackjack(GameTableShowcases.SEED);
		assertEquals(5, grids.size());
		assertEquals(2, grids.getFirst().cards().size());
		assertTrue(grids.getFirst().faceUp()[0]);
		assertFalse(grids.getFirst().faceUp()[1]);
	}

	@Test
	void cardLayerSingleRowForSmallHands() {
		List<Card> hand = Card.standard52().subList(0, 5);
		assertEquals(1, CardLayer.wrap(hand, 5, true).size());
		assertEquals(5, CardLayer.row(hand, true).getString().replace(" ", "").length());
	}
}
