package com.spicypox.minecard.room;

import com.spicypox.minecard.config.MinecardConfig;

/** Per-room blackjack rules chosen in the create-room wizard. */
public record BjRoomRules(
	int turnSeconds,
	int decks,
	boolean insuranceEnabled,
	boolean dealerHitsSoft17,
	boolean surrenderEnabled,
	int maxPlayers
) {
	public static BjRoomRules defaults() {
		return new BjRoomRules(
			Math.max(0, MinecardConfig.turnSeconds),
			clamp(MinecardConfig.roomDecks, 1, 8),
			MinecardConfig.insuranceEnabled,
			MinecardConfig.dealerHitsSoft17,
			MinecardConfig.surrenderEnabled,
			BjRoom.MAX_PLAYERS
		);
	}

	public BjRoomRules sanitized() {
		return new BjRoomRules(
			// 0 = no countdown; otherwise 5–120s
			turnSeconds <= 0 ? 0 : clamp(turnSeconds, 5, 120),
			clamp(decks, 1, 8),
			insuranceEnabled,
			dealerHitsSoft17,
			surrenderEnabled,
			clamp(maxPlayers, 1, BjRoom.MAX_PLAYERS)
		);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
