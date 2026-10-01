package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.ui.CardLayer;
import com.spicypox.minecard.ui.DialogUi;
import com.spicypox.minecard.ui.MainMenuDialog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RoomListDialog {
	public static final Identifier BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/list_back");

	private RoomListDialog() {
	}

	public static void open(ServerPlayer player, List<BjRoom> rooms) {
		player.openDialog(Holder.direct(build(rooms)));
	}

	public static MultiActionDialog build(List<BjRoom> rooms) {
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(Component.translatable("minecard.room.list_intro"), CardLayer.DIALOG_WIDTH));
		if (rooms.isEmpty()) {
			body.add(new PlainMessage(Component.translatable("minecard.room.list_empty"), CardLayer.DIALOG_WIDTH));
		}

		int w = DialogUi.BUTTON_WIDTH;
		List<ActionButton> actions = new ArrayList<>();
		for (BjRoom room : rooms) {
			Identifier joinId = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/join/" + room.roomId());
			actions.add(new ActionButton(
				new CommonButtonData(
					Component.translatable(
						"minecard.room.list_row",
						room.roomId(),
						room.hostName(),
						room.seats().size(),
						BjRoom.MAX_PLAYERS
					),
					w
				),
				Optional.of(new CustomAll(joinId, Optional.empty()))
			));
		}

		ActionButton back = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.profile.back"), w),
			Optional.of(new CustomAll(BACK, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.room.list_title"),
			Optional.empty(),
			true,
			true,
			DialogAction.CLOSE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions.isEmpty() ? List.of(back) : actions, Optional.of(back), 1);
	}

	public static void onBack(ServerPlayer player) {
		MainMenuDialog.open(player);
	}
}
