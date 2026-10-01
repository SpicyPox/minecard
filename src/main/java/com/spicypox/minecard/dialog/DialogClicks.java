package com.spicypox.minecard.dialog;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.ui.CardTableLoop;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Routes vanilla dialog {@code custom} click ids to game handlers. */
public final class DialogClicks {
	private DialogClicks() {
	}

	public static boolean handle(ServerPlayer player, Identifier id) {
		if (CardTableLoop.STOP_ACTION.equals(id)) {
			if (CardTableLoop.stop(player.getUUID())) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("minecard.loop.stopped"));
			}
			return true;
		}
		if (id.getNamespace().equals(Minecard.MOD_ID) && id.getPath().startsWith("bj/")) {
			BlackjackGames.onClick(player, id.getPath().substring("bj/".length()));
			return true;
		}
		return false;
	}
}
