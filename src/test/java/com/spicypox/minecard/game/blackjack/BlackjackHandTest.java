package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import com.spicypox.minecard.card.Suit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackHandTest {
	@Test
	void aceSoftThenHard() {
		BlackjackHand hand = new BlackjackHand();
		hand.add(new Card(Suit.SPADES, Rank.ACE));
		hand.add(new Card(Suit.HEARTS, Rank.SIX));
		assertEquals(17, hand.score());
		assertTrue(hand.isSoft());
		hand.add(new Card(Suit.CLUBS, Rank.KING));
		assertEquals(17, hand.score());
		assertFalse(hand.isBust());
	}

	@Test
	void naturalBlackjack() {
		BlackjackHand hand = new BlackjackHand();
		hand.add(new Card(Suit.SPADES, Rank.ACE));
		hand.add(new Card(Suit.HEARTS, Rank.KING));
		assertTrue(hand.isBlackjack());
		assertEquals(21, hand.score());
	}

	@Test
	void bust() {
		BlackjackHand hand = new BlackjackHand();
		hand.add(new Card(Suit.SPADES, Rank.KING));
		hand.add(new Card(Suit.HEARTS, Rank.QUEEN));
		hand.add(new Card(Suit.CLUBS, Rank.TWO));
		assertTrue(hand.isBust());
	}
}
