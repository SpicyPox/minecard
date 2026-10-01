package com.spicypox.minecard.ui;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** View-only wallet listing with prev/next pages (no deposit/withdraw). */
public final class CheckBalanceDialog {
	public static final int PAGE_SIZE = 8;

	public static final Identifier PREV = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/check_prev");
	public static final Identifier NEXT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/check_next");
	public static final Identifier BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/check_back");

	private static final Map<UUID, Integer> PAGE = new ConcurrentHashMap<>();

	private CheckBalanceDialog() {
	}

	public static void open(ServerPlayer player, int page) {
		Wallets.ensureStartingBalance(player.getUUID());
		int max = maxPage(player);
		int p = Math.max(0, Math.min(page, max));
		PAGE.put(player.getUUID(), p);
		player.openDialog(Holder.direct(build(player, p)));
	}

	public static boolean onClick(ServerPlayer player, String action, CompoundTag payload) {
		int page = PAGE.getOrDefault(player.getUUID(), 0);
		return switch (action) {
			case "check_prev" -> {
				open(player, page - 1);
				yield true;
			}
			case "check_next" -> {
				open(player, page + 1);
				yield true;
			}
			case "check_back" -> {
				PAGE.remove(player.getUUID());
				BalanceMenuDialog.open(player);
				yield true;
			}
			default -> false;
		};
	}

	private static MultiActionDialog build(ServerPlayer player, int page) {
		List<Map.Entry<Identifier, Long>> bals = Wallets.listBalances(player.getUUID());
		int max = maxPage(player);
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(
			Component.translatable("minecard.balance.check.hint", page + 1, max + 1),
			CardLayer.DIALOG_WIDTH
		));
		if (bals.isEmpty()) {
			body.add(new PlainMessage(
				Component.translatable("minecard.menu.balance_empty"),
				CardLayer.DIALOG_WIDTH
			));
		} else {
			int from = page * PAGE_SIZE;
			for (int i = from; i < Math.min(from + PAGE_SIZE, bals.size()); i++) {
				Map.Entry<Identifier, Long> e = bals.get(i);
				body.add(StakeItem.iconWithDescription(
					e.getKey(),
					Component.translatable(
						"minecard.menu.balance_line",
						StakeItem.displayName(e.getKey()),
						e.getValue()
					),
					CardLayer.DIALOG_WIDTH - 40
				));
			}
		}

		int w = DialogUi.BUTTON_WIDTH;
		// MultiActionDialog encode requires a non-empty actions list.
		List<ActionButton> actions = new ArrayList<>();
		if (page > 0) {
			actions.add(button("minecard.balance.check.prev", PREV, w));
		}
		if (page < max) {
			actions.add(button("minecard.balance.check.next", NEXT, w));
		}
		actions.add(button("minecard.balance.check.back", BACK, w));
		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.balance.check.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.empty(), 1);
	}

	private static int maxPage(ServerPlayer player) {
		int n = Wallets.listBalances(player.getUUID()).size();
		if (n <= 0) {
			return 0;
		}
		return (n - 1) / PAGE_SIZE;
	}

	private static ActionButton button(String key, Identifier id, int width) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), width),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
