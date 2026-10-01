package com.spicypox.minecard.ui;

import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.level.ServerPlayer;

public final class Dialogs {
	private Dialogs() {
	}

	public static void clear(ServerPlayer player) {
		player.connection.send(ClientboundClearDialogPacket.INSTANCE);
	}
}
