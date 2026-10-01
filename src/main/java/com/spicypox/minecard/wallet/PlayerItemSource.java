package com.spicypox.minecard.wallet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hotbar + main inventory only. Accepts plain stacks matching registry item id.
 */
public final class PlayerItemSource implements ItemSource {
	private final ServerPlayer player;

	public PlayerItemSource(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public long count(Identifier itemId) {
		long total = 0L;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (matches(stack, itemId)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@Override
	public long take(Identifier itemId, long amount) {
		if (amount <= 0L) {
			return 0L;
		}
		long need = amount;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && need > 0L; i++) {
			ItemStack stack = inv.getItem(i);
			if (!matches(stack, itemId)) {
				continue;
			}
			int remove = (int) Math.min(stack.getCount(), need);
			stack.shrink(remove);
			need -= remove;
		}
		return amount - need;
	}

	@Override
	public long give(Identifier itemId, long amount) {
		if (amount <= 0L) {
			return 0L;
		}
		Item item = BuiltInRegistries.ITEM.getValue(itemId);
		if (item == null) {
			return amount;
		}
		long remaining = amount;
		while (remaining > 0L) {
			int max = item.getDefaultMaxStackSize();
			int batch = (int) Math.min(remaining, max);
			ItemStack stack = new ItemStack(item, batch);
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				ItemEntity entity = new ItemEntity(
					player.level(),
					player.getX(),
					player.getY() + 0.5,
					player.getZ(),
					stack.copy()
				);
				entity.setPickUpDelay(40);
				player.level().addFreshEntity(entity);
				stack.setCount(0);
			}
			remaining -= batch;
		}
		return 0L;
	}

	private static boolean matches(ItemStack stack, Identifier itemId) {
		if (stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!itemId.equals(id)) {
			return false;
		}
		return ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
	}
}
