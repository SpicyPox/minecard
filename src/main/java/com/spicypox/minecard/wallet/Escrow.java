package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Lock stakes: balance first, then inventory. Refuse the whole amount if short.
 * Never deletes leftovers on failed return — leftover inventory returns become wallet credit.
 */
public final class Escrow {
	private Escrow() {
	}

	/**
	 * @return empty if total available &lt; amount (nothing deducted)
	 */
	public static Optional<EscrowHold> lock(
		Wallet wallet,
		ItemSource items,
		UUID playerId,
		Identifier itemId,
		long amount
	) {
		if (amount <= 0L) {
			return Optional.empty();
		}
		long bal = wallet.balance(playerId, itemId);
		long inv = items.count(itemId);
		if (bal + inv < amount) {
			return Optional.empty();
		}

		long fromBal = Math.min(bal, amount);
		long fromInv = amount - fromBal;

		if (fromBal > 0L) {
			wallet.setBalance(playerId, itemId, bal - fromBal);
		}
		if (fromInv > 0L) {
			long taken = items.take(itemId, fromInv);
			if (taken != fromInv) {
				// Roll back balance; inventory already partial — put taken back then abort.
				if (taken > 0L) {
					long leftover = items.give(itemId, taken);
					if (leftover > 0L) {
						wallet.add(playerId, itemId, leftover);
					}
				}
				if (fromBal > 0L) {
					wallet.add(playerId, itemId, fromBal);
				}
				return Optional.empty();
			}
		}
		return Optional.of(new EscrowHold(playerId, itemId, fromBal, fromInv));
	}

	/**
	 * Release hold: inventory part first (online pocket), remainder / failures → wallet.
	 */
	public static void release(Wallet wallet, ItemSource items, EscrowHold hold) {
		if (hold.inventoryPart() > 0L) {
			long leftover = items.give(hold.itemId(), hold.inventoryPart());
			if (leftover > 0L) {
				wallet.add(hold.playerId(), hold.itemId(), leftover);
			}
		}
		if (hold.balancePart() > 0L) {
			wallet.add(hold.playerId(), hold.itemId(), hold.balancePart());
		}
	}

	/** Credit a payout entirely to wallet (room/solo settle). */
	public static void creditWallet(Wallet wallet, UUID playerId, Identifier itemId, long amount) {
		if (amount > 0L) {
			wallet.add(playerId, itemId, amount);
		}
	}
}
