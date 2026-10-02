package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.config.MinecardConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * Card resource pack delivery.
 * <p>
 * Vanilla clients reject plain HTTP packs from remote hosts, and private GitHub
 * releases are not downloadable by players. For a private repo + VPS we:
 * <ul>
 *   <li>Serve the zip on the VPS HTTP port for <b>browser</b> download</li>
 *   <li>Only auto-push (in-game Accept prompt) when the URL is HTTPS or localhost</li>
 *   <li>Otherwise send a chat link + manual Resource Packs instructions</li>
 * </ul>
 */
public final class CardPackOffers {
	private static CardResourcePack pack;
	private static PackHttpServer http;
	/** URL advertised to players (browser and/or in-game push). */
	private static String packUrl;
	private static boolean autoPush;

	private CardPackOffers() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				pack = CardResourcePack.build();
				http = startHttp(pack);
				packUrl = resolvePackUrl(server, http.port());
				autoPush = canAutoPush(packUrl);
				Minecard.LOGGER.info("Card pack URL for clients: {} (autoPush={})", packUrl, autoPush);
				if (!autoPush) {
					Minecard.LOGGER.warn(
						"In-game pack Accept is disabled for this URL (vanilla blocks cleartext HTTP to "
							+ "public IPs; private GitHub releases are not reachable). Players use the chat "
							+ "download link (browser) then enable the pack in Options → Resource Packs. "
							+ "Or set packUrl in config/minecard.json to a public HTTPS URL you host."
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
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> offerOrGuide(handler.player));
	}

	/**
	 * Config {@code packUrl} if set; else {@code http://&lt;server-ip|packHost|detected&gt;:port/minecard-cards.zip}.
	 * Does <b>not</b> auto-use private GitHub releases.
	 */
	static String resolvePackUrl(MinecraftServer server, int port) {
		String configured = MinecardConfig.packUrl;
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		String host = resolveHost(server);
		return "http://" + host + ":" + port + "/minecard-cards.zip";
	}

	/**
	 * Config packHost → -Dminecard.packHost → server-ip → first non-loopback IPv4 → 127.0.0.1.
	 */
	public static String resolveHost(MinecraftServer server) {
		String fromConfig = MinecardConfig.packHost;
		if (fromConfig != null && !fromConfig.isBlank()) {
			return fromConfig.trim();
		}
		String prop = System.getProperty("minecard.packHost");
		if (prop != null && !prop.isBlank()) {
			return prop.trim();
		}
		if (server != null) {
			String localIp = server.getLocalIp();
			if (localIp != null && !localIp.isBlank()
				&& !"0.0.0.0".equals(localIp.trim())
				&& !"*".equals(localIp.trim())) {
				return localIp.trim();
			}
		}
		return detectNonLoopbackIpv4().orElse("127.0.0.1");
	}

	public static String resolveHost() {
		return resolveHost(null);
	}

	public static int resolvePort() {
		if (MinecardConfig.packPort > 0) {
			return MinecardConfig.packPort;
		}
		return Integer.getInteger("minecard.packPort", 8765);
	}

	static Optional<String> detectNonLoopbackIpv4() {
		try {
			List<String> publicIps = new ArrayList<>();
			List<String> privateIps = new ArrayList<>();
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				NetworkInterface ni = ifaces.nextElement();
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
					continue;
				}
				Enumeration<InetAddress> addrs = ni.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress a = addrs.nextElement();
					if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) {
						continue;
					}
					String host = a.getHostAddress();
					if (a.isSiteLocalAddress()) {
						privateIps.add(host);
					} else {
						publicIps.add(host);
					}
				}
			}
			if (!publicIps.isEmpty()) {
				return Optional.of(publicIps.getFirst());
			}
			if (!privateIps.isEmpty()) {
				return Optional.of(privateIps.getFirst());
			}
		} catch (Exception e) {
			Minecard.LOGGER.warn("Could not auto-detect pack host: {}", e.toString());
		}
		return Optional.empty();
	}

	/** True when vanilla will accept an in-game ResourcePackPush for this URL. */
	static boolean canAutoPush(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
			if ("https".equals(scheme)) {
				return true;
			}
			if ("http".equals(scheme)) {
				return isLoopbackHost(uri.getHost());
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	public static boolean autoPushEnabled() {
		return autoPush && pack != null && packUrl != null;
	}

	/** Browser / chat download URL (VPS HTTP is fine in a normal browser). */
	public static String manualDownloadUrl() {
		return packUrl;
	}

	static boolean isLoopbackHost(String host) {
		if (host == null || host.isBlank()) {
			return true;
		}
		String h = host.trim().toLowerCase();
		return "127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h);
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

	public static String packUrl() {
		return packUrl;
	}

	/**
	 * In-game Accept prompt when allowed; otherwise chat link for manual install.
	 * @return true if something useful was sent
	 */
	public static boolean offerOrGuide(ServerPlayer player) {
		if (pack == null || packUrl == null) {
			return false;
		}
		if (autoPush) {
			return pushPack(player);
		}
		sendManualGuide(player);
		return true;
	}

	/** @deprecated prefer {@link #offerOrGuide(ServerPlayer)} */
	public static boolean offer(ServerPlayer player) {
		return offerOrGuide(player);
	}

	private static boolean pushPack(ServerPlayer player) {
		player.connection.send(new ClientboundResourcePackPushPacket(
			pack.id(),
			packUrl,
			pack.sha1Hex(),
			false,
			Optional.of(Component.translatable("minecard.pack.prompt"))
		));
		return true;
	}

	public static void sendManualGuide(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("minecard.pack.manual_hint"));
		MutableComponent link = Component.translatable("minecard.pack.manual_click")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.GREEN)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(packUrl)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(packUrl))));
		player.sendSystemMessage(link);
		player.sendSystemMessage(Component.translatable("minecard.pack.manual_firewall", String.valueOf(http != null ? http.port() : resolvePort())));
	}
}
