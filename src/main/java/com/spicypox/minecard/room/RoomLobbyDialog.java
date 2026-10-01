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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RoomLobbyDialog {
	public static final Identifier READY = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/ready");
	public static final Identifier UNREADY = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/unready");
	public static final Identifier START = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/start");
	public static final Identifier LEAVE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "room/leave");

	private RoomLobbyDialog() {
	}

	public static void open(ServerPlayer player, BjRoom room) {
		player.openDialog(Holder.direct(build(player, room)));
	}

	public static MultiActionDialog build(ServerPlayer player, BjRoom room) {
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(
			Component.translatable(
				"minecard.room.lobby_header",
				room.roomId(),
				room.hostName(),
				StakeItem.amountLine(room.stakeItem(), room.minBet())
			),
			CardLayer.DIALOG_WIDTH
		));
		body.add(new PlainMessage(
			Component.translatable("minecard.room.seats_title"),
			CardLayer.DIALOG_WIDTH
		));
		if (room.seats().isEmpty()) {
			body.add(new PlainMessage(Component.translatable("minecard.room.no_seats"), CardLayer.DIALOG_WIDTH));
		} else {
			for (RoomSeat seat : room.seats()) {
				body.add(new PlainMessage(
					Component.translatable(
						seat.ready() ? "minecard.room.seat_ready" : "minecard.room.seat_wait",
						seat.displayName(),
						seat.bet()
					),
					CardLayer.DIALOG_WIDTH
				));
			}
		}

		boolean host = room.hostId().equals(player.getUUID());
		int w = DialogUi.BUTTON_WIDTH;
		List<ActionButton> actions = new ArrayList<>();
		if (!host) {
			actions.add(btn("minecard.room.ready", READY, w));
			actions.add(btn("minecard.room.unready", UNREADY, w));
		} else {
			actions.add(btn("minecard.room.start", START, w));
		}

		ActionButton leave = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.room.leave"), w),
			Optional.of(new CustomAll(LEAVE, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.room.lobby_title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.of(leave), 1);
	}

	private static ActionButton btn(String key, Identifier id, int w) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), w),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
