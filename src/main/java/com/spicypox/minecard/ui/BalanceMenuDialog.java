package com.spicypox.minecard.ui;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.wallet.BankGui;
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

import java.util.List;
import java.util.Optional;

/** Bank hub: deposit, withdraw, view-only balance pages. */
public final class BalanceMenuDialog {
	public static final Identifier DEPOSIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/deposit");
	public static final Identifier WITHDRAW = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/withdraw");
	public static final Identifier CHECK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/check");
	public static final Identifier BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "balance/back");

	private BalanceMenuDialog() {
	}

	public static void open(ServerPlayer player) {
		player.openDialog(Holder.direct(build()));
	}

	public static MultiActionDialog build() {
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = List.of(
			new PlainMessage(Component.translatable("minecard.balance.hint"), CardLayer.DIALOG_WIDTH)
		);
		List<ActionButton> actions = List.of(
			button("minecard.balance.withdraw", WITHDRAW, w),
			button("minecard.balance.deposit", DEPOSIT, w),
			button("minecard.balance.check", CHECK, w)
		);
		ActionButton back = button("minecard.balance.back", BACK, w);
		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.balance.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			body,
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.of(back), 1);
	}

	public static boolean onClick(ServerPlayer player, String action, CompoundTag payload) {
		return switch (action) {
			case "deposit" -> {
				Dialogs.clear(player);
				BankGui.openDeposit(player);
				yield true;
			}
			case "withdraw" -> {
				Dialogs.clear(player);
				BankGui.openWithdraw(player);
				yield true;
			}
			case "check" -> {
				CheckBalanceDialog.open(player, 0);
				yield true;
			}
			case "back" -> {
				MainMenuDialog.open(player);
				yield true;
			}
			default -> false;
		};
	}

	private static ActionButton button(String key, Identifier id, int width) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), width),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
