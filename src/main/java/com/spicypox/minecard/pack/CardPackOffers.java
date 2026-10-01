package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class CardPackOffers {
	private static CardResourcePack pack;
	private static PackHttpServer http;
	private static String packUrl;
	private static final int PORT = Integer.getInteger("minecard.packPort", 8765);
	private static final String HOST = System.getProperty("minecard.packHost", "127.0.0.1");

	private CardPackOffers() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				pack = CardResourcePack.build();
				http = startHttp(pack);
				packUrl = "http://" + HOST + ":" + http.port() + "/minecard-cards.zip";
				Minecard.LOGGER.info("Card pack URL for clients: {}", packUrl);
			} catch (Exception e) {
				Minecard.LOGGER.error("Failed to host Minecard card resource pack", e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (http != null) {
				http.close();
				http = null;
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> offer(handler.player));
	}

	private static PackHttpServer startHttp(CardResourcePack pack) throws Exception {
		Exception last = null;
		for (int port = PORT; port < PORT + 16; port++) {
			try {
				return PackHttpServer.start(pack, port);
			} catch (Exception e) {
				last = e;
			}
		}
		throw last != null ? last : new IllegalStateException("no pack port available");
	}

	public static void offer(ServerPlayer player) {
		if (pack == null || packUrl == null) {
			return;
		}
		player.connection.send(new ClientboundResourcePackPushPacket(
			pack.id(),
			packUrl,
			pack.sha1Hex(),
			true,
			Optional.of(Component.translatable("minecard.pack.prompt"))
		));
	}
}
