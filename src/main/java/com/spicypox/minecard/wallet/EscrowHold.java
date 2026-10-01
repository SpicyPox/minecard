package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * One locked stake: parts taken from wallet balance and from inventory.
 */
public record EscrowHold(
	UUID playerId,
	Identifier itemId,
	long balancePart,
	long inventoryPart
) {
	public long total() {
		return balancePart + inventoryPart;
	}
}
