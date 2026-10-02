package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.config.MinecardConfig;
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

	private CardPackOffers() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				pack = CardResourcePack.build();
				http = startHttp(pack);
				String host = resolveHost();
				packUrl = "http://" + host + ":" + http.port() + "/minecard-cards.zip";
				Minecard.LOGGER.info("Card pack URL for clients: {}", packUrl);
				if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
					Minecard.LOGGER.warn(
						"packHost is localhost — only clients on this machine can download the card pack. "
							+ "Set packHost in config/minecard.json to a LAN/public IP clients can reach."
					);
				}
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

	/**
	 * Config {@code packHost} if set; else {@code -Dminecard.packHost}; else {@code 127.0.0.1}.
	 */
	public static String resolveHost() {
		String fromConfig = MinecardConfig.packHost;
		if (fromConfig != null && !fromConfig.isBlank()) {
			return fromConfig.trim();
		}
		String prop = System.getProperty("minecard.packHost");
		if (prop != null && !prop.isBlank()) {
			return prop.trim();
		}
		return "127.0.0.1";
	}

	public static int resolvePort() {
		if (MinecardConfig.packPort > 0) {
			return MinecardConfig.packPort;
		}
		return Integer.getInteger("minecard.packPort", 8765);
	}

	private static PackHttpServer startHttp(CardResourcePack pack) throws Exception {
		Exception last = null;
		int base = resolvePort();
		for (int port = base; port < base + 16; port++) {
			try {
				return PackHttpServer.start(pack, port);
			} catch (Exception e) {
				last = e;
			}
		}
		throw last != null ? last : new IllegalStateException("no pack port available");
	}

	/** URL clients use to download the zip, or null if hosting failed. */
	public static String packUrl() {
		return packUrl;
	}

	/**
	 * Re-offer (or first-offer) the optional card resource pack.
	 * @return true if the push packet was sent
	 */
	public static boolean offer(ServerPlayer player) {
		if (pack == null || packUrl == null) {
			return false;
		}
		// Optional: never force-kick if the player declines the pack.
		player.connection.send(new ClientboundResourcePackPushPacket(
			pack.id(),
			packUrl,
			pack.sha1Hex(),
			false,
			Optional.of(Component.translatable("minecard.pack.prompt"))
		));
		return true;
	}
}
