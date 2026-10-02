package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.config.MinecardConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Card resource pack delivery.
 * Prefer public HTTPS GitHub Release {@code minecard-cards.zip} when the asset exists.
 * Fallback: local HTTP (browser/manual; in-game Accept for loopback clients).
 * Same fixed pack UUID + stable SHA — spam re-offer does not create new pack slots.
 */
public final class CardPackOffers {
	public enum OfferResult {
		UNAVAILABLE,
		PUSHED,
		MANUAL,
		/** Same pack already offered recently — no second push (avoids client re-download spam). */
		COOLDOWN
	}

	/** Re-offer cooldown for /minecard pack and menu (same content fingerprint). */
	private static final long OFFER_COOLDOWN_MS = 10_000L;

	private static CardResourcePack pack;
	private static PackHttpServer http;
	/** Preferred URL for clients (GitHub HTTPS, configured, or LAN HTTP). */
	private static String packUrl;
	/** Always-local Accept URL when HTTP server is up. */
	private static String loopbackHttpUrl;
	private static boolean autoPush;

	private static final Map<UUID, Long> LAST_OFFER_MS = new ConcurrentHashMap<>();
	private static final Map<UUID, String> LAST_OFFER_FP = new ConcurrentHashMap<>();

	private CardPackOffers() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				pack = CardResourcePack.build();
				http = startHttp(pack);
				loopbackHttpUrl = "http://127.0.0.1:" + http.port() + "/minecard-cards.zip";
				packUrl = resolvePackUrl(server, http.port());
				autoPush = canAutoPush(packUrl);
				Minecard.LOGGER.info(
					"Card pack URL for clients: {} (autoPush={}, id={}, sha1={})",
					packUrl,
					autoPush,
					pack.id(),
					pack.sha1Hex()
				);
				if (!autoPush) {
					Minecard.LOGGER.warn(
						"In-game pack Accept needs HTTPS or localhost. Remote players use the chat "
							+ "download link (browser) then enable the pack in Options → Resource Packs. "
							+ "Or set packUrl in config/minecard.json to a public HTTPS URL. "
							+ "Local clients still get Accept via {}.",
						loopbackHttpUrl
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
			LAST_OFFER_MS.clear();
			LAST_OFFER_FP.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			offerOrGuide(handler.player, true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			LAST_OFFER_MS.remove(id);
			LAST_OFFER_FP.remove(id);
		});
	}

	/**
	 * Config {@code packUrl} → reachable GitHub Release HTTPS → local HTTP fallback.
	 */
	static String resolvePackUrl(MinecraftServer server, int port) {
		String configured = MinecardConfig.packUrl;
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		String gh = githubReleasePackUrl();
		if (gh != null) {
			if (isUrlReachable(gh)) {
				return gh;
			}
			Minecard.LOGGER.warn("GitHub pack not reachable ({}), falling back to local HTTP", gh);
		}
		String host = resolveHost(server);
		return "http://" + host + ":" + port + "/minecard-cards.zip";
	}

	public static String githubReleasePackUrl() {
		try {
			String ver = FabricLoader.getInstance()
				.getModContainer(Minecard.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse(null);
			if (ver == null || ver.isBlank()) {
				return null;
			}
			return "https://github.com/SpicyPox/minecard/releases/download/v" + ver + "/minecard-cards.zip";
		} catch (Exception e) {
			return null;
		}
	}

	/** HEAD/GET probe — GitHub release assets often 302 then 200. */
	static boolean isUrlReachable(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		try {
			int code = httpStatus(url, "HEAD");
			if (code == HttpURLConnection.HTTP_BAD_METHOD
				|| code == HttpURLConnection.HTTP_FORBIDDEN
				|| code == -1) {
				code = httpStatus(url, "GET");
			}
			return code >= 200 && code < 400;
		} catch (Exception e) {
			Minecard.LOGGER.debug("Pack URL probe failed for {}: {}", url, e.toString());
			return false;
		}
	}

	private static int httpStatus(String url, String method) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		conn.setInstanceFollowRedirects(true);
		conn.setRequestMethod(method);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestProperty("User-Agent", "MinecardPackProbe/1.0");
		try {
			return conn.getResponseCode();
		} finally {
			conn.disconnect();
		}
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
		return pack != null && (autoPush || loopbackHttpUrl != null);
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

	static boolean isLoopbackPlayer(ServerPlayer player) {
		try {
			SocketAddress addr = player.connection.getRemoteAddress();
			if (addr instanceof InetSocketAddress isa) {
				InetAddress ip = isa.getAddress();
				return ip != null && ip.isLoopbackAddress();
			}
		} catch (Exception ignored) {
		}
		return false;
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

	/** Player-triggered offer ({@code /minecard pack} / menu) — cooldown applies. */
	public static OfferResult offerOrGuide(ServerPlayer player) {
		return offerOrGuide(player, false);
	}

	/**
	 * @param fromJoin {@code true} = one offer per connection without cooldown message spam;
	 *                 still skips identical re-push within the cooldown window.
	 */
	public static OfferResult offerOrGuide(ServerPlayer player, boolean fromJoin) {
		if (pack == null || packUrl == null) {
			return OfferResult.UNAVAILABLE;
		}
		String pushUrl = null;
		if (canAutoPush(packUrl)) {
			pushUrl = packUrl;
		} else if (isLoopbackPlayer(player) && loopbackHttpUrl != null) {
			pushUrl = loopbackHttpUrl;
		}

		String fingerprint = pack.sha1Hex() + "|" + (pushUrl != null ? pushUrl : "manual:" + packUrl);
		UUID id = player.getUUID();
		long now = System.currentTimeMillis();
		Long last = LAST_OFFER_MS.get(id);
		String prevFp = LAST_OFFER_FP.get(id);
		if (last != null && fingerprint.equals(prevFp) && now - last < OFFER_COOLDOWN_MS) {
			if (!fromJoin) {
				long leftSec = Math.max(1L, (OFFER_COOLDOWN_MS - (now - last) + 999L) / 1000L);
				player.sendSystemMessage(Component.translatable("minecard.pack.cooldown", leftSec));
			}
			return OfferResult.COOLDOWN;
		}

		LAST_OFFER_MS.put(id, now);
		LAST_OFFER_FP.put(id, fingerprint);

		if (pushUrl != null) {
			pushPack(player, pushUrl);
			return OfferResult.PUSHED;
		}
		sendManualGuide(player);
		return OfferResult.MANUAL;
	}

	/** @deprecated prefer {@link #offerOrGuide(ServerPlayer)} */
	public static boolean offer(ServerPlayer player) {
		return offerOrGuide(player) != OfferResult.UNAVAILABLE;
	}

	private static void pushPack(ServerPlayer player, String url) {
		// Fixed CardResourcePack.PACK_ID — vanilla replaces the same server-pack slot, does not stack copies.
		player.connection.send(new ClientboundResourcePackPushPacket(
			pack.id(),
			url,
			pack.sha1Hex(),
			false,
			Optional.of(Component.translatable("minecard.pack.prompt"))
		));
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
