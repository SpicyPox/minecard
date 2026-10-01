package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.CardGlyphs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTableLoopTest {
	@BeforeAll
	static void boot() {
		CardGlyphs.bootstrap();
	}

	@Test
	void dealOrderIsThirteenCards() {
		assertEquals(13, CardTableLoop.dealOrder(GameTableShowcases.SEED).size());
	}

	@Test
	void sessionProgressesDealThenFlip() {
		CardTableLoop.Session session = new CardTableLoop.Session(CardTableLoop.dealOrder(GameTableShowcases.SEED));
		assertEquals(0, session.onTable());

		boolean sawDeal = false;
		boolean sawFlip = false;
		for (int i = 0; i < CardTableLoop.PHASE_TICKS * 2; i++) {
			session.advance();
			if (session.onTable() > 0 && session.faceUpCount() == 0) {
				sawDeal = true;
			}
			if (session.faceUpCount() > 0) {
				sawFlip = true;
				break;
			}
		}
		assertTrue(sawDeal);
		assertTrue(sawFlip);
		assertEquals(13, session.onTable());
	}
}
