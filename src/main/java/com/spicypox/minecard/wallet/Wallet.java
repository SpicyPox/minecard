package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player balances keyed by item id. Hot path for stakes; history goes to SQLite ledger.
 */
public interface Wallet {
	long balance(UUID playerId, Identifier itemId);

	void setBalance(UUID playerId, Identifier itemId, long amount);

	default long add(UUID playerId, Identifier itemId, long delta) {
		long next = Math.max(0L, balance(playerId, itemId) + delta);
		setBalance(playerId, itemId, next);
		return next;
	}

	/** Non-zero balances for {@code playerId}, sorted by item id string. */
	List<Map.Entry<Identifier, Long>> listBalances(UUID playerId);

	/** True if this player already has a wallet record (even if all balances are zero). */
	boolean hasAccount(UUID playerId);

	/** Tests / demo bootstrap: clear all balances. */
	void clearAll();
}
