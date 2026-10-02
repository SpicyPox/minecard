package com.spicypox.minecard.game.poker;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import com.spicypox.minecard.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerHandEvalTest {
	private static Card c(Suit s, Rank r) {
		return new Card(s, r);
	}

	@Test
	void royalFlushBeatsStraightFlush() {
		PokerHandRank royal = PokerHandEval.bestOf(List.of(
			c(Suit.SPADES, Rank.TEN), c(Suit.SPADES, Rank.JACK), c(Suit.SPADES, Rank.QUEEN),
			c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE)
		));
		PokerHandRank sf = PokerHandEval.bestOf(List.of(
			c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.TEN), c(Suit.HEARTS, Rank.JACK),
			c(Suit.HEARTS, Rank.QUEEN), c(Suit.HEARTS, Rank.KING)
		));
		assertEquals(PokerHandCategory.STRAIGHT_FLUSH, royal.category());
		assertTrue(royal.compareTo(sf) > 0);
	}

	@Test
	void wheelStraight() {
		PokerHandRank wheel = PokerHandEval.bestOf(List.of(
			c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.THREE),
			c(Suit.CLUBS, Rank.FOUR), c(Suit.SPADES, Rank.FIVE)
		));
		assertEquals(PokerHandCategory.STRAIGHT, wheel.category());
		assertEquals(5, wheel.kickers()[0]);
	}

	@Test
	void bestOfSevenPicksFlushOverLowerBoard() {
		List<Card> hole = List.of(c(Suit.HEARTS, Rank.ACE), c(Suit.HEARTS, Rank.KING));
		List<Card> board = List.of(
			c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.NINE),
			c(Suit.CLUBS, Rank.THREE), c(Suit.DIAMONDS, Rank.FOUR)
		);
		PokerHandRank r = PokerHandEval.bestOf(hole, board);
		assertEquals(PokerHandCategory.FLUSH, r.category());
		assertEquals(14, r.kickers()[0]);
	}

	@Test
	void fullHouseBeatsFlush() {
		PokerHandRank fh = PokerHandEval.bestOf(List.of(
			c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE),
			c(Suit.CLUBS, Rank.KING), c(Suit.SPADES, Rank.KING)
		));
		PokerHandRank flush = PokerHandEval.bestOf(List.of(
			c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.NINE),
			c(Suit.HEARTS, Rank.JACK), c(Suit.HEARTS, Rank.KING)
		));
		assertEquals(PokerHandCategory.FULL_HOUSE, fh.category());
		assertTrue(fh.compareTo(flush) > 0);
	}

	@Test
	void pairKickersCompare() {
		PokerHandRank a = PokerHandEval.bestOf(List.of(
			c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING),
			c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.TWO)
		));
		PokerHandRank b = PokerHandEval.bestOf(List.of(
			c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING),
			c(Suit.CLUBS, Rank.JACK), c(Suit.SPADES, Rank.THREE)
		));
		assertTrue(a.compareTo(b) > 0);
	}
}
