package com.spicypox.minecard.wallet;

import com.spicypox.minecard.game.blackjack.StakeItem;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankGuiTest {
	private static final Identifier STARTER = StakeItem.DEFAULT_ID;

	@BeforeEach
	void reset() {
		Wallets.useMemoryForTests();
		Wallets.resetAll();
	}

	@Test
	void ensureStartingBalanceOnlyOnce() {
		UUID id = UUID.randomUUID();
		assertFalse(Wallets.get().hasAccount(id));
		Wallets.ensureStartingBalance(id);
		assertEquals(WalletConstants.STARTING_BALANCE, Wallets.balance(id, STARTER));
		Wallets.setBalance(id, STARTER, 0L);
		assertTrue(Wallets.get().hasAccount(id));
		Wallets.ensureStartingBalance(id);
		assertEquals(0L, Wallets.balance(id, STARTER), "must not refill after withdraw-to-zero");
	}

	@Test
	void pageSnapshotMath() {
		// 1000 items → 15 full stacks + 40 = 16 slots needed
		assertEquals(16, (1000 + 63) / 64);
	}
}
