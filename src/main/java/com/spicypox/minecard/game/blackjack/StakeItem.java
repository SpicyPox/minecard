package com.spicypox.minecard.game.blackjack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Demo stake currency until rooms pick an item from the host's hand.
 */
public final class StakeItem {
	public static final Identifier DEFAULT_ID = Identifier.withDefaultNamespace("oak_log");

	private StakeItem() {
	}

	public static Item item(Identifier id) {
		Item found = BuiltInRegistries.ITEM.getValue(id);
		return found == Items.AIR ? Items.OAK_LOG : found;
	}

	public static Component displayName(Identifier id) {
		return new ItemStack(item(id)).getHoverName();
	}

	/** Friendly stake line, e.g. "Diamond × 10" — never the registry id. */
	public static Component amountLine(Identifier id, long amount) {
		return Component.translatable("minecard.stake.amount_line", displayName(id), amount);
	}

	/**
	 * Dialog body: item icon immediately left of description (stake amounts).
	 */
	public static ItemBody iconWithDescription(Identifier id, Component description, int descWidth) {
		return new ItemBody(
			new ItemStackTemplate(item(id), 1),
			Optional.of(new PlainMessage(description, Math.max(80, descWidth))),
			false,
			true,
			16,
			16
		);
	}
}
