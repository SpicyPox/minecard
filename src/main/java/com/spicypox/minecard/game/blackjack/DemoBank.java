package com.spicypox.minecard.game.blackjack;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory demo balances by item id until milestone 2 (SavedData wallet).
 */
public final class DemoBank {
	public static final long STARTING_BALANCE = 1_000L;
	public static final long DEFAULT_BET = 10L;

	private static final Map<UUID, Map<Identifier, Long>> BALANCES = new ConcurrentHashMap<>();

	private DemoBank() {
	}

	public static long balance(UUID playerId, Identifier itemId) {
		return balances(playerId).computeIfAbsent(itemId, id ->
			StakeItem.DEFAULT_ID.equals(id) ? STARTING_BALANCE : 0L
		);
	}

	public static void setBalance(UUID playerId, Identifier itemId, long amount) {
		balances(playerId).put(itemId, Math.max(0L, amount));
	}

	/** Clear all balances (tests). */
	public static void resetAll() {
		BALANCES.clear();
	}

	private static Map<Identifier, Long> balances(UUID playerId) {
		return BALANCES.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
	}
}
