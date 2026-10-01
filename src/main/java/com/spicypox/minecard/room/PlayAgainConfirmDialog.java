package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.ui.CardLayer;
import com.spicypox.minecard.ui.DialogUi;
import net.minecraft.core.Holder;
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

/** Confirm locking the room stake again for the next multiplayer round. */
public final class PlayAgainConfirmDialog {
	public static final Identifier YES = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/play_again_yes");
	public static final Identifier NO = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/play_again_no");

	private PlayAgainConfirmDialog() {
	}

	public static void open(ServerPlayer player, BjRoom room) {
		player.openDialog(Holder.direct(build(room)));
	}

	public static MultiActionDialog build(BjRoom room) {
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = List.of(
			new PlainMessage(
				Component.translatable(
					"minecard.table.play_again_confirm",
					StakeItem.amountLine(room.stakeItem(), room.minBet())
				),
				CardLayer.DIALOG_WIDTH
			)
		);
		List<ActionButton> actions = List.of(
			btn("minecard.table.play_again_yes", YES, w),
			btn("minecard.table.play_again_no", NO, w)
		);
		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.bj.play_again"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			body,
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.empty(), 1);
	}

	private static ActionButton btn(String key, Identifier id, int w) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), w),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
