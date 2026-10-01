package com.spicypox.minecard.wallet;

import com.spicypox.minecard.game.blackjack.StakeItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide wallet accessor. Uses {@link MemoryWallet} in tests;
 * binds to {@link WalletSavedData} when a dedicated/integrated server starts.
 */
public final class Wallets {
	private static final AtomicReference<Wallet> ACTIVE = new AtomicReference<>(new MemoryWallet());
	private static boolean lifecycleRegistered;

	private Wallets() {
	}

	public static void register() {
		if (lifecycleRegistered) {
			return;
		}
		lifecycleRegistered = true;
		ServerLifecycleEvents.SERVER_STARTED.register(Wallets::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.set(new MemoryWallet()));
	}

	private static void onServerStarted(MinecraftServer server) {
		ACTIVE.set(WalletSavedData.get(server));
	}

	public static Wallet get() {
		return ACTIVE.get();
	}

	/** Unit tests: force a fresh memory wallet. */
	public static void useMemoryForTests() {
		ACTIVE.set(new MemoryWallet());
	}

	public static long balance(UUID playerId, Identifier itemId) {
		return get().balance(playerId, itemId);
	}

	public static void setBalance(UUID playerId, Identifier itemId, long amount) {
		get().setBalance(playerId, itemId, amount);
	}

	public static java.util.List<java.util.Map.Entry<Identifier, Long>> listBalances(UUID playerId) {
		return get().listBalances(playerId);
	}

	/**
	 * One-time starter grant for brand-new accounts only.
	 * Never refills after the player empties their bank (that was free-item exploit).
	 */
	public static void ensureStartingBalance(UUID playerId) {
		if (get().hasAccount(playerId)) {
			return;
		}
		setBalance(playerId, StakeItem.DEFAULT_ID, WalletConstants.STARTING_BALANCE);
	}

	public static void resetAll() {
		get().clearAll();
	}
}
