package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.config.MinecardConfig;
import com.spicypox.minecard.wallet.WalletConstants;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableBlackjackTest {
	private static final Identifier DIAMOND = StakeItem.DEFAULT_ID;

	@BeforeEach
	void reset() {
		Wallets.useMemoryForTests();
		Wallets.resetAll();
		MinecardConfig.insuranceEnabled = false;
	}

	@Test
	void twoPlayersReachResolvedAfterStands() {
		UUID host = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Wallets.setBalance(host, DIAMOND, 10_000L);
		Wallets.setBalance(a, DIAMOND, 100L);
		Wallets.setBalance(b, DIAMOND, 100L);

		TableBlackjack table = new TableBlackjack(
			"t1",
			host,
			DIAMOND,
			List.of(
				new TablePlayer(a, "A", WalletConstants.DEFAULT_BET),
				new TablePlayer(b, "B", WalletConstants.DEFAULT_BET)
			)
		);

		for (int i = 0; i < 40 && table.phase() == TableBlackjack.Phase.DEALING; i++) {
			table.tick();
		}
		assertTrue(
			table.phase() == TableBlackjack.Phase.PLAYER_TURN
				|| table.phase() == TableBlackjack.Phase.RESOLVED
		);

		if (table.phase() == TableBlackjack.Phase.PLAYER_TURN) {
			while (table.phase() == TableBlackjack.Phase.PLAYER_TURN) {
				UUID turn = table.activePlayerId();
				table.stand(turn);
			}
		}
		int guard = 0;
		while (table.phase() == TableBlackjack.Phase.DEALER_TURN && guard++ < 40) {
			if (table.dealerCanHit()) {
				table.dealerHit(host);
			} else {
				table.dealerStand(host);
			}
		}
		assertEquals(TableBlackjack.Phase.RESOLVED, table.phase());
	}
}
