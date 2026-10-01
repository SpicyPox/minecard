package com.spicypox.minecard.wallet;

import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.ui.BalanceMenuDialog;
import com.spicypox.minecard.ui.Dialogs;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bank via sgui: deposit stages items then Confirm; withdraw takes from displayed wallet stacks.
 */
public final class BankGui extends SimpleGui {
	public enum Mode {
		DEPOSIT,
		WITHDRAW
	}

	public static final int PAGE_SLOTS = 45;
	public static final int SLOT_PREV = 45;
	public static final int SLOT_SWITCH = 47;
	public static final int SLOT_CONFIRM = 49;
	public static final int SLOT_CLOSE = 51;
	public static final int SLOT_NEXT = 53;

	private final Mode mode;
	private final SimpleContainer pageItems = new SimpleContainer(PAGE_SLOTS);
	private int page;
	private boolean switching;
	private boolean settled;
	private final Map<Identifier, Long> pageSnapshot = new HashMap<>();

	private BankGui(ServerPlayer player, Mode mode) {
		super(MenuType.GENERIC_9x6, player, false);
		this.mode = mode;
		setTitle(Component.translatable(
			mode == Mode.DEPOSIT ? "minecard.bank.title.deposit" : "minecard.bank.title.withdraw"
		));
		for (int i = 0; i < PAGE_SLOTS; i++) {
			setSlot(i, new Slot(pageItems, i, 0, 0));
		}
		placeFunctionRow();
		if (mode == Mode.WITHDRAW) {
			fillWithdrawPage();
		}
	}

	public static void openDeposit(ServerPlayer player) {
		open(player, Mode.DEPOSIT);
	}

	public static void openWithdraw(ServerPlayer player) {
		open(player, Mode.WITHDRAW);
	}

	public static void open(ServerPlayer player, Mode mode) {
		Dialogs.clear(player);
		Wallets.ensureStartingBalance(player.getUUID());
		new BankGui(player, mode).open();
	}

	@Override
	public void onRemoved() {
		if (switching || settled) {
			return;
		}
		settled = true;
		if (mode == Mode.DEPOSIT) {
			returnStaged();
		} else {
			applyWithdrawDelta();
		}
		BalanceMenuDialog.open(player);
	}

	private void placeFunctionRow() {
		setSlot(SLOT_PREV, navButton(Items.ARROW, "minecard.bank.prev", mode == Mode.WITHDRAW && page > 0, this::prevPage));
		setSlot(46, filler());
		setSlot(
			SLOT_SWITCH,
			button(
				Items.COMPASS,
				mode == Mode.DEPOSIT ? "minecard.bank.switch_withdraw" : "minecard.bank.switch_deposit",
				this::switchMode
			)
		);
		setSlot(48, filler());
		setSlot(SLOT_CONFIRM, button(Items.CONCRETE.lime(), "minecard.bank.confirm", this::confirm));
		setSlot(50, filler());
		setSlot(SLOT_CLOSE, button(Items.BARRIER, "minecard.bank.close", this::closeToMenu));
		setSlot(52, filler());
		setSlot(SLOT_NEXT, navButton(Items.ARROW, "minecard.bank.next", mode == Mode.WITHDRAW && page < maxPage(), this::nextPage));
	}

	private void prevPage() {
		if (mode != Mode.WITHDRAW || page <= 0) {
			return;
		}
		applyWithdrawDelta();
		page--;
		fillWithdrawPage();
		placeFunctionRow();
	}

	private void nextPage() {
		if (mode != Mode.WITHDRAW || page >= maxPage()) {
			return;
		}
		applyWithdrawDelta();
		page++;
		fillWithdrawPage();
		placeFunctionRow();
	}

	private void switchMode() {
		switching = true;
		if (mode == Mode.DEPOSIT) {
			returnStaged();
		} else {
			applyWithdrawDelta();
		}
		settled = true;
		Mode next = mode == Mode.DEPOSIT ? Mode.WITHDRAW : Mode.DEPOSIT;
		close();
		open(player, next);
	}

	private void confirm() {
		if (mode == Mode.DEPOSIT) {
			long n = depositStaged();
			if (n > 0L) {
				player.sendSystemMessage(Component.translatable("minecard.bank.deposit_ok", n), true);
			} else {
				player.sendSystemMessage(Component.translatable("minecard.bank.deposit_hint"), true);
			}
			placeFunctionRow();
			return;
		}
		applyWithdrawDelta();
		fillWithdrawPage();
		placeFunctionRow();
		player.sendSystemMessage(Component.translatable("minecard.bank.withdraw_ok"), true);
	}

	private void closeToMenu() {
		switching = true;
		if (mode == Mode.DEPOSIT) {
			returnStaged();
		} else {
			applyWithdrawDelta();
		}
		settled = true;
		close();
		BalanceMenuDialog.open(player);
	}

	private long depositStaged() {
		long total = 0L;
		for (int i = 0; i < PAGE_SLOTS; i++) {
			ItemStack stack = pageItems.getItem(i);
			if (stack.isEmpty() || isControlItem(stack.getItem())) {
				continue;
			}
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			int count = stack.getCount();
			Wallets.get().add(player.getUUID(), id, count);
			total += count;
			pageItems.setItem(i, ItemStack.EMPTY);
		}
		return total;
	}

	private void returnStaged() {
		for (int i = 0; i < PAGE_SLOTS; i++) {
			ItemStack stack = pageItems.getItem(i);
			if (stack.isEmpty() || isControlItem(stack.getItem())) {
				pageItems.setItem(i, ItemStack.EMPTY);
				continue;
			}
			returnOne(stack.copy());
			pageItems.setItem(i, ItemStack.EMPTY);
		}
	}

	private void applyWithdrawDelta() {
		if (mode != Mode.WITHDRAW) {
			return;
		}
		Map<Identifier, Long> now = new HashMap<>();
		for (int i = 0; i < PAGE_SLOTS; i++) {
			ItemStack stack = pageItems.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			long allowed = pageSnapshot.getOrDefault(id, 0L);
			long already = now.getOrDefault(id, 0L);
			int count = stack.getCount();
			if (allowed <= 0L || already >= allowed) {
				returnOne(stack.copy());
				pageItems.setItem(i, ItemStack.EMPTY);
				continue;
			}
			long room = allowed - already;
			if (count > room) {
				returnOne(new ItemStack(stack.getItem(), count - (int) room));
				stack.setCount((int) room);
				count = (int) room;
			}
			if (count > 0) {
				now.merge(id, (long) count, Long::sum);
			} else {
				pageItems.setItem(i, ItemStack.EMPTY);
			}
		}
		for (Identifier id : pageSnapshot.keySet()) {
			long before = pageSnapshot.get(id);
			long after = now.getOrDefault(id, 0L);
			long taken = before - after;
			if (taken > 0L) {
				long bal = Wallets.balance(player.getUUID(), id);
				Wallets.setBalance(player.getUUID(), id, Math.max(0L, bal - taken));
			}
		}
		pageSnapshot.clear();
		pageSnapshot.putAll(now);
	}

	private void fillWithdrawPage() {
		for (int i = 0; i < PAGE_SLOTS; i++) {
			pageItems.setItem(i, ItemStack.EMPTY);
		}
		pageSnapshot.clear();
		List<ItemStack> stacks = expandStacks(Wallets.listBalances(player.getUUID()));
		if (page > maxPage()) {
			page = maxPage();
		}
		int from = page * PAGE_SLOTS;
		for (int i = 0; i < PAGE_SLOTS && from + i < stacks.size(); i++) {
			ItemStack stack = stacks.get(from + i).copy();
			pageItems.setItem(i, stack);
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			pageSnapshot.merge(id, (long) stack.getCount(), Long::sum);
		}
	}

	private int maxPage() {
		if (mode == Mode.DEPOSIT) {
			return 0;
		}
		int n = expandStacks(Wallets.listBalances(player.getUUID())).size();
		if (n <= 0) {
			return 0;
		}
		return (n - 1) / PAGE_SLOTS;
	}

	private void returnOne(ItemStack stack) {
		long leftover = new PlayerItemSource(player).give(
			BuiltInRegistries.ITEM.getKey(stack.getItem()),
			stack.getCount()
		);
		if (leftover > 0L) {
			player.drop(stack.copyWithCount((int) leftover), false, Prediction.SERVER_ONLY);
		}
	}

	private static boolean isControlItem(Item item) {
		return item == Items.ARROW
			|| item == Items.BARRIER
			|| item == Items.COMPASS
			|| item == Items.CONCRETE.lime()
			|| item == Items.STAINED_GLASS_PANE.gray();
	}

	static List<ItemStack> expandStacks(List<Map.Entry<Identifier, Long>> balances) {
		List<ItemStack> out = new java.util.ArrayList<>();
		for (Map.Entry<Identifier, Long> e : balances) {
			long left = e.getValue();
			if (left <= 0L) {
				continue;
			}
			var item = StakeItem.item(e.getKey());
			while (left > 0L) {
				int c = (int) Math.min(64L, left);
				out.add(new ItemStack(item, c));
				left -= c;
			}
		}
		return out;
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).setName(Component.literal(" "));
	}

	private static GuiElementBuilder button(Item item, String key, Runnable action) {
		return new GuiElementBuilder(item)
			.setName(Component.translatable(key))
			.setCallback(GuiElement.callback(action));
	}

	private static GuiElementBuilder navButton(Item item, String key, boolean active, Runnable action) {
		if (!active) {
			return filler().setName(Component.translatable(key));
		}
		return button(item, key, action);
	}
}
