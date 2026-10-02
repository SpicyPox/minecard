package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowTest {
	private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond");
	private MemoryWallet wallet;
	private UUID player;

	@BeforeEach
	void setUp() {
		wallet = new MemoryWallet();
		player = UUID.randomUUID();
	}

	@Test
	void refusesWhenShortWithoutTouchingFunds() {
		wallet.setBalance(player, DIAMOND, 3L);
		FakeItems items = new FakeItems(2L);
		Optional<EscrowHold> hold = Escrow.lock(wallet, items, player, DIAMOND, 10L);
		assertTrue(hold.isEmpty());
		assertEquals(3L, wallet.balance(player, DIAMOND));
		assertEquals(2L, items.count(DIAMOND));
	}

	@Test
	void takesBalanceThenInventory() {
		wallet.setBalance(player, DIAMOND, 6L);
		FakeItems items = new FakeItems(10L);
		EscrowHold hold = Escrow.lock(wallet, items, player, DIAMOND, 10L).orElseThrow();
		assertEquals(6L, hold.balancePart());
		assertEquals(4L, hold.inventoryPart());
		assertEquals(0L, wallet.balance(player, DIAMOND));
		assertEquals(6L, items.count(DIAMOND));
	}

	@Test
	void releaseRestoresParts() {
		wallet.setBalance(player, DIAMOND, 5L);
		FakeItems items = new FakeItems(5L);
		EscrowHold hold = Escrow.lock(wallet, items, player, DIAMOND, 8L).orElseThrow();
		Escrow.release(wallet, items, hold);
		assertEquals(5L, wallet.balance(player, DIAMOND));
		assertEquals(5L, items.count(DIAMOND));
	}

	@Test
	void ensureWalletCoverageMovesInventoryWhenBalanceShort() {
		wallet.setBalance(player, DIAMOND, 0L);
		FakeItems items = new FakeItems(25L);
		assertTrue(Escrow.ensureWalletCoverage(wallet, items, player, DIAMOND, 20L));
		assertEquals(20L, wallet.balance(player, DIAMOND));
		assertEquals(5L, items.count(DIAMOND));
	}

	@Test
	void ensureWalletCoverageRefusesWhenInventoryShort() {
		wallet.setBalance(player, DIAMOND, 2L);
		FakeItems items = new FakeItems(3L);
		assertFalse(Escrow.ensureWalletCoverage(wallet, items, player, DIAMOND, 10L));
		assertEquals(2L, wallet.balance(player, DIAMOND));
		assertEquals(3L, items.count(DIAMOND));
	}

	/** Simple countable bag for tests (no Minecraft player). */
	private static final class FakeItems implements ItemSource {
		private final AtomicLong count;

		FakeItems(long start) {
			this.count = new AtomicLong(start);
		}

		@Override
		public long count(Identifier itemId) {
			return count.get();
		}

		@Override
		public long take(Identifier itemId, long amount) {
			long c = count.get();
			long n = Math.min(c, amount);
			count.addAndGet(-n);
			return n;
		}

		@Override
		public long give(Identifier itemId, long amount) {
			count.addAndGet(amount);
			return 0L;
		}
	}
}
