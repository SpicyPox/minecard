package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Fixed-seed showcase deals.
 * Poker & Blackjack use 5 horizontal grids: 1 house + 4 players.
 */
public final class GameTableShowcases {
	public static final long SEED = 26_03_52L;
	public static final int PLAYERS = 4;

	private GameTableShowcases() {
	}

	/** Board (5) then P1–P4 (2 hole each), all face up. */
	public static List<CardGrid> poker(long seed) {
		List<Card> deck = shuffled(seed);
		int i = 0;
		List<CardGrid> grids = new ArrayList<>(5);
		grids.add(CardGrid.allFaceUp(Component.translatable("minecard.table.board"), take(deck, i, 5)));
		i += 5;
		for (int p = 1; p <= PLAYERS; p++) {
			grids.add(CardGrid.allFaceUp(Component.translatable("minecard.table.player", p), take(deck, i, 2)));
			i += 2;
		}
		return List.copyOf(grids);
	}

	/** Dealer (up + hole back) then P1–P4 (2 face up). */
	public static List<CardGrid> blackjack(long seed) {
		List<Card> deck = shuffled(seed);
		int i = 0;
		Card dealerUp = deck.get(i++);
		Card dealerHole = deck.get(i++);

		List<CardGrid> grids = new ArrayList<>(5);
		grids.add(CardGrid.mixed(
			Component.translatable("minecard.table.dealer"),
			List.of(dealerUp, dealerHole),
			true, false
		));
		for (int p = 1; p <= PLAYERS; p++) {
			grids.add(CardGrid.allFaceUp(Component.translatable("minecard.table.player", p), take(deck, i, 2)));
			i += 2;
		}
		return List.copyOf(grids);
	}

	private static List<Card> shuffled(long seed) {
		List<Card> deck = new ArrayList<>(Card.standard52());
		Collections.shuffle(deck, new Random(seed));
		return deck;
	}

	private static List<Card> take(List<Card> deck, int from, int n) {
		return List.copyOf(deck.subList(from, from + n));
	}
}
