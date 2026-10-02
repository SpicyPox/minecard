package com.spicypox.minecard.game.poker;

/** NLHE room blinds / buy-in (item units of the room stake). */
public record PokerRules(
	long smallBlind,
	long bigBlind,
	long minBuyIn,
	long maxBuyIn,
	int maxPlayers
) {
	public static PokerRules defaults() {
		return new PokerRules(1L, 2L, 40L, 200L, 4).sanitized();
	}

	public PokerRules sanitized() {
		long sb = Math.max(1L, smallBlind);
		long bb = Math.max(sb + 1L, bigBlind);
		long min = Math.max(bb * 10L, minBuyIn);
		long max = Math.max(min, maxBuyIn);
		int seats = Math.clamp(maxPlayers, 2, 4);
		return new PokerRules(sb, bb, min, max, seats);
	}
}
