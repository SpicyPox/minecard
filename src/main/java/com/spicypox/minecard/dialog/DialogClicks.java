package com.spicypox.minecard.dialog;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.room.CreateRoomDialog;
import com.spicypox.minecard.room.RoomListDialog;
import com.spicypox.minecard.room.Rooms;
import com.spicypox.minecard.ui.BalanceMenuDialog;
import com.spicypox.minecard.ui.CardTableLoop;
import com.spicypox.minecard.ui.CheckBalanceDialog;
import com.spicypox.minecard.ui.MainMenuDialog;
import com.spicypox.minecard.ui.ProfileDialog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Routes vanilla dialog {@code custom} click ids to game handlers. */
public final class DialogClicks {
	private DialogClicks() {
	}

	public static boolean handle(ServerPlayer player, Identifier id, CompoundTag payload) {
		if (!id.getNamespace().equals(Minecard.MOD_ID)) {
			return false;
		}
		String path = id.getPath();
		CompoundTag nbt = payload != null ? payload : new CompoundTag();

		if (CardTableLoop.STOP_ACTION.equals(id)) {
			if (CardTableLoop.stop(player.getUUID())) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("minecard.loop.stopped"));
			}
			return true;
		}
		if (path.startsWith("bj/")) {
			BlackjackGames.onClick(player, path.substring("bj/".length()));
			return true;
		}
		if (path.startsWith("menu/")) {
			MainMenuDialog.onClick(player, path.substring("menu/".length()), nbt);
			return true;
		}
		if (path.startsWith("balance/")) {
			String bal = path.substring("balance/".length());
			if (bal.startsWith("check_")) {
				return CheckBalanceDialog.onClick(player, bal, nbt);
			}
			return BalanceMenuDialog.onClick(player, bal, nbt);
		}
		if (path.startsWith("create/")) {
			return CreateRoomDialog.onClick(player, path.substring("create/".length()), nbt);
		}
		if (path.startsWith("profile/")) {
			String action = path.substring("profile/".length());
			if ("summary".equals(action)) {
				ProfileDialog.open(player);
			} else {
				ProfileDialog.onClick(player, action);
			}
			return true;
		}
		if (path.startsWith("room/join/")) {
			Rooms.join(player, path.substring("room/join/".length()));
			return true;
		}
		if (path.equals("room/ready")) {
			Rooms.setReady(player, true);
			return true;
		}
		if (path.equals("room/unready")) {
			Rooms.setReady(player, false);
			return true;
		}
		if (path.equals("room/start")) {
			Rooms.hostStart(player);
			return true;
		}
		if (path.equals("room/leave") || path.equals("table/leave")) {
			Rooms.leave(player);
			return true;
		}
		if (path.equals("room/list_back")) {
			RoomListDialog.onBack(player);
			return true;
		}
		if (path.startsWith("table/")) {
			Rooms.onTableClick(player, path.substring("table/".length()));
			return true;
		}
		return false;
	}
}
