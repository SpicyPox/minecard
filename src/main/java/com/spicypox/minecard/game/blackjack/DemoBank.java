package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.wallet.WalletConstants;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * @deprecated Use {@link Wallets} / {@link WalletConstants}. Kept as a thin facade
 * so existing call sites compile during the wallet migration.
 */
@Deprecated
public final class DemoBank {
	public static final long STARTING_BALANCE = WalletConstants.STARTING_BALANCE;
	public static final long DEFAULT_BET = WalletConstants.DEFAULT_BET;

	private DemoBank() {
	}

	public static long balance(UUID playerId, Identifier itemId) {
		return Wallets.balance(playerId, itemId);
	}

	public static void setBalance(UUID playerId, Identifier itemId, long amount) {
		Wallets.setBalance(playerId, itemId, amount);
	}

	public static void resetAll() {
		Wallets.useMemoryForTests();
		Wallets.resetAll();
	}
}
