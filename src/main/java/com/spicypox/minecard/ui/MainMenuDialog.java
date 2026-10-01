package com.spicypox.minecard.ui;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.history.HistoryDb;
import com.spicypox.minecard.room.CreateRoomDialog;
import com.spicypox.minecard.room.Rooms;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main menu: Balance hub, create room, join by code.
 * Bank deposit/withdraw/view live under {@link BalanceMenuDialog}.
 */
public final class MainMenuDialog {
	public static final Identifier BALANCE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "menu/balance");
	public static final Identifier CREATE_ROOM = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "menu/create_room");
	public static final Identifier JOIN_CODE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "menu/join_code");
	public static final Identifier PROFILE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "menu/profile");
	public static final Identifier CLOSE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "menu/close");

	private MainMenuDialog() {
	}

	public static void open(ServerPlayer player) {
		player.openDialog(Holder.direct(build(player)));
	}

	public static MultiActionDialog build(ServerPlayer player) {
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(
			Component.translatable("minecard.menu.hint"),
			CardLayer.DIALOG_WIDTH
		));
		HistoryDb.stats(player.getUUID()).ifPresent(s -> body.add(new PlainMessage(
			Component.translatable(
				"minecard.menu.stats_line",
				s.handsPlayed(),
				s.wins(),
				s.losses(),
				s.pushes()
			),
			CardLayer.DIALOG_WIDTH
		)));

		int w = DialogUi.BUTTON_WIDTH;
		List<Input> inputs = List.of(
			new Input(
				"room_code",
				new TextInput(
					w,
					Component.translatable("minecard.menu.room_code"),
					true,
					"",
					16,
					Optional.empty()
				)
			)
		);
		List<ActionButton> actions = List.of(
			button("minecard.menu.join_code", JOIN_CODE, w),
			button("minecard.menu.create_room", CREATE_ROOM, w),
			button("minecard.menu.balance", BALANCE, w)
		);

		ActionButton close = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.menu.close"), w),
			Optional.of(new CustomAll(CLOSE, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.menu.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			inputs
		);
		return new MultiActionDialog(data, actions, Optional.of(close), 1);
	}

	public static void onClick(ServerPlayer player, String action, CompoundTag payload) {
		switch (action) {
			case "balance" -> BalanceMenuDialog.open(player);
			case "create_room" -> CreateRoomDialog.openGameSelect(player);
			case "join_code" -> {
				String code = payload.getStringOr("room_code", "").trim();
				if (code.isEmpty()) {
					player.sendSystemMessage(Component.translatable("minecard.menu.room_code_empty"));
					return;
				}
				Rooms.join(player, code);
			}
			case "join_list" -> Rooms.openPublicList(player);
			case "profile" -> ProfileDialog.open(player);
			case "close" -> Dialogs.clear(player);
			default -> {
			}
		}
	}

	private static ActionButton button(String key, Identifier id, int width) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), width),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
