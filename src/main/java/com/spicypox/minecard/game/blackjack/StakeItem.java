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
	public static final Identifier DEFAULT_ID = Identifier.withDefaultNamespace("diamond");

	private StakeItem() {
	}

	public static Item item(Identifier id) {
		Item found = BuiltInRegistries.ITEM.getValue(id);
		return found == Items.AIR ? Items.DIAMOND : found;
	}

	public static Component displayName(Identifier id) {
		return new ItemStack(item(id)).getHoverName();
	}

	/**
	 * Dialog body: item icon + description (amounts for this stake type only).
	 */
	public static ItemBody iconWithDescription(Identifier id, Component description, int descWidth) {
		return new ItemBody(
			new ItemStackTemplate(item(id), 1),
			Optional.of(new PlainMessage(description, descWidth)),
			false,
			true,
			16,
			16
		);
	}
}
