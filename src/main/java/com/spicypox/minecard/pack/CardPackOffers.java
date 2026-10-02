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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Card resource pack delivery for vanilla clients.
 * <p>
 * Priority: {@code packUrl} (Google Drive / any HTTPS host) → jsDelivr/raw CDN → VPS HTTP
 * (browser/manual only). GitHub {@code releases/download} is never used for Accept (302 → S3).
 * Google Drive share links are normalized to {@code uc?export=download&id=...}.
 */
public final class CardPackOffers {
	public enum OfferResult {
		UNAVAILABLE,
		PUSHED,
		MANUAL,
		/** Same pack already offered recently — no second push (avoids client re-download spam). */
		COOLDOWN
	}

	private static final String GITHUB_OWNER_REPO = "SpicyPox/minecard";
	/** Re-offer cooldown for /minecard pack and menu (same content fingerprint). */
	private static final long OFFER_COOLDOWN_MS = 10_000L;
	private static final Pattern DRIVE_FILE_D = Pattern.compile(
		"https?://(?:drive|docs)\\.google\\.com/file/d/([a-zA-Z0-9_-]+)"
	);
	private static final Pattern DRIVE_OPEN_ID = Pattern.compile(
		"https?://(?:drive|docs)\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+)"
	);
	private static final Pattern DRIVE_UC_ID = Pattern.compile(
		"[?&]id=([a-zA-Z0-9_-]+)"
	);

	private static CardResourcePack pack;
	private static PackHttpServer http;
	/** Preferred URL for remote clients (direct HTTPS or LAN HTTP for manual). */
	private static String packUrl;
	/** SHA-1 of bytes at {@link #packUrl} (must match what the client downloads). */
	private static String packShaForUrl;
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
				ResolvedPack resolved = resolvePack(server, http.port(), pack.sha1Hex());
				packUrl = resolved.url();
				packShaForUrl = resolved.sha1Hex();
				autoPush = canAutoPush(packUrl);
				Minecard.LOGGER.info(
					"Card pack URL for clients: {} (autoPush={}, id={}, sha1={})",
					packUrl,
					autoPush,
					pack.id(),
					packShaForUrl
				);
				if (!autoPush) {
					Minecard.LOGGER.warn(
						"No direct HTTPS pack URL — vanilla cannot Accept HTTP to a public IP. "
							+ "Players use the chat browser link (VPS TCP {}). "
							+ "Fix: commit pack/minecard-cards.zip and release a tag (jsDelivr), "
							+ "or set packUrl in config/minecard.json to a direct HTTPS zip. "
							+ "Localhost clients still Accept via {}.",
						http.port(),
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

	record ResolvedPack(String url, String sha1Hex) {
	}

	/**
	 * {@code packUrl} (Drive/HTTPS) → CDN jsDelivr/raw → VPS HTTP (manual).
	 * Never auto-selects GitHub {@code releases/download} (302 → expiring S3).
	 */
	static ResolvedPack resolvePack(MinecraftServer server, int port, String localSha) {
		String configured = MinecardConfig.packUrl;
		if (configured != null && !configured.isBlank()) {
			Optional<ResolvedPack> cfg = tryUserPackUrl(configured.trim(), localSha);
			if (cfg.isPresent()) {
				return cfg.get();
			}
			Minecard.LOGGER.warn(
				"config packUrl unusable for in-game Accept (need public direct zip HTTPS; "
					+ "Drive: Anyone with the link + file under ~100MB). Trying CDN mirrors…"
			);
		}
		String ver = modVersion();
		if (ver != null) {
			for (String url : directHttpsCandidates(ver)) {
				Optional<ResolvedPack> hit = tryDirectHttps(url, localSha, "CDN " + url);
				if (hit.isPresent()) {
					return hit.get();
				}
			}
			Minecard.LOGGER.warn(
				"No direct HTTPS mirror matched local SHA {}. "
					+ "Falling back to VPS HTTP for browser/manual only.",
				localSha
			);
		}
		return localHttpPack(server, port, localSha);
	}

	/**
	 * Normalize share links and probe with redirect-follow (Drive → googleusercontent).
	 * Push URL stays the stable normalized link (not the temporary CDN hop).
	 */
	static Optional<ResolvedPack> tryUserPackUrl(String rawUrl, String localSha) {
		String url = normalizePackUrl(rawUrl);
		if (!canAutoPush(url)) {
			Minecard.LOGGER.warn("packUrl is not HTTPS/loopback — skip: {}", url);
			return Optional.empty();
		}
		Optional<byte[]> bytes = downloadZipFollowingRedirects(url);
		if (bytes.isEmpty()) {
			Minecard.LOGGER.warn("packUrl did not return a zip (HTML/confirm page?): {}", url);
			return Optional.empty();
		}
		String remoteSha = sha1Hex(bytes.get());
		if (!remoteSha.equalsIgnoreCase(localSha)) {
			Minecard.LOGGER.warn(
				"packUrl SHA mismatch (remote={} local={}) — upload the server's "
					+ "minecard-cards.zip (same build). url={}",
				remoteSha,
				localSha,
				url
			);
			return Optional.empty();
		}
		Minecard.LOGGER.info("packUrl OK for in-game Accept: {} (sha={})", url, localSha);
		return Optional.of(new ResolvedPack(url, localSha));
	}

	/**
	 * Convert Google Drive /view or /open links to a direct download URL.
	 * Already-direct {@code uc?export=download&id=} links are left as-is (https normalized).
	 */
	static String normalizePackUrl(String raw) {
		if (raw == null) {
			return "";
		}
		String url = raw.trim();
		if (url.isEmpty()) {
			return url;
		}
		Matcher file = DRIVE_FILE_D.matcher(url);
		if (file.find()) {
			return driveDirectUrl(file.group(1));
		}
		Matcher open = DRIVE_OPEN_ID.matcher(url);
		if (open.find()) {
			return driveDirectUrl(open.group(1));
		}
		if (url.contains("drive.google.com") || url.contains("docs.google.com")) {
			if (url.contains("export=download") || url.contains("uc?")) {
				Matcher id = DRIVE_UC_ID.matcher(url);
				if (id.find()) {
					return driveDirectUrl(id.group(1));
				}
			}
		}
		return url;
	}

	private static String driveDirectUrl(String fileId) {
		return "https://drive.google.com/uc?export=download&id=" + fileId;
	}

	private static Optional<byte[]> downloadZipFollowingRedirects(String url) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(20000);
			conn.setRequestProperty("User-Agent", "MinecardPackProbe/1.0");
			int code = conn.getResponseCode();
			if (code < 200 || code >= 400) {
				return Optional.empty();
			}
			try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				in.transferTo(bos);
				byte[] bytes = bos.toByteArray();
				if (!looksLikeZip(bytes)) {
					return Optional.empty();
				}
				return Optional.of(bytes);
			}
		} catch (Exception e) {
			Minecard.LOGGER.debug("packUrl download failed for {}: {}", url, e.toString());
			return Optional.empty();
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	static boolean looksLikeZip(byte[] bytes) {
		return bytes != null && bytes.length >= 4
			&& bytes[0] == 0x50 && bytes[1] == 0x4B
			&& (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07)
			&& (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08);
	}

	private static String sha1Hex(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** jsDelivr + raw.githubusercontent — both serve repo files with HTTP 200 (no S3 hop). */
	static List<String> directHttpsCandidates(String ver) {
		String tag = ver.startsWith("v") ? ver : "v" + ver;
		String path = "pack/minecard-cards.zip";
		return List.of(
			"https://cdn.jsdelivr.net/gh/" + GITHUB_OWNER_REPO + "@" + tag + "/" + path,
			"https://raw.githubusercontent.com/" + GITHUB_OWNER_REPO + "/" + tag + "/" + path
		);
	}

	static String modVersion() {
		try {
			return FabricLoader.getInstance()
				.getModContainer(Minecard.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Accept only HTTP 200 with matching SHA and <b>no redirect</b>.
	 * Redirecting hosts (GitHub releases/download → signed S3) fail in the Minecraft client.
	 */
	static Optional<ResolvedPack> tryDirectHttps(String url, String localSha, String label) {
		if (!canAutoPush(url)) {
			Minecard.LOGGER.warn("{} is not HTTPS/loopback — skip for Accept: {}", label, url);
			return Optional.empty();
		}
		Optional<Probe> probe = probeDirect(url);
		if (probe.isEmpty()) {
			Minecard.LOGGER.warn("{} not reachable: {}", label, url);
			return Optional.empty();
		}
		Probe p = probe.get();
		if (p.redirected()) {
			Minecard.LOGGER.warn(
				"{} returns HTTP {} redirect — vanilla pack download often fails. Use a direct 200 URL.",
				label,
				p.code()
			);
			return Optional.empty();
		}
		if (p.code() < 200 || p.code() >= 300 || p.sha1Hex() == null) {
			Minecard.LOGGER.warn("{} bad response code {}: {}", label, p.code(), url);
			return Optional.empty();
		}
		if (!p.sha1Hex().equalsIgnoreCase(localSha)) {
			Minecard.LOGGER.warn(
				"{} SHA mismatch (remote={} local={})",
				label,
				p.sha1Hex(),
				localSha
			);
			return Optional.empty();
		}
		Minecard.LOGGER.info("{} OK for in-game Accept (direct HTTPS, sha={})", label, localSha);
		return Optional.of(new ResolvedPack(url, localSha));
	}

	record Probe(int code, boolean redirected, String sha1Hex) {
	}

	/** GET without following redirects; hash body only on 200. */
	static Optional<Probe> probeDirect(String url) {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(8000);
			conn.setReadTimeout(15000);
			conn.setRequestProperty("User-Agent", "MinecardPackProbe/1.0");
			int code = conn.getResponseCode();
			if (code == HttpURLConnection.HTTP_MOVED_PERM
				|| code == HttpURLConnection.HTTP_MOVED_TEMP
				|| code == HttpURLConnection.HTTP_SEE_OTHER
				|| code == 307
				|| code == 308) {
				return Optional.of(new Probe(code, true, null));
			}
			if (code < 200 || code >= 300) {
				return Optional.of(new Probe(code, false, null));
			}
			try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				in.transferTo(bos);
				byte[] bytes = bos.toByteArray();
				if (bytes.length == 0) {
					return Optional.of(new Probe(code, false, null));
				}
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				return Optional.of(new Probe(code, false, HexFormat.of().formatHex(digest.digest(bytes))));
			}
		} catch (Exception e) {
			Minecard.LOGGER.debug("Pack probe failed for {}: {}", url, e.toString());
			return Optional.empty();
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static ResolvedPack localHttpPack(MinecraftServer server, int port, String localSha) {
		String host = resolveHost(server);
		return new ResolvedPack("http://" + host + ":" + port + "/minecard-cards.zip", localSha);
	}

	/** @deprecated kept for older call sites / docs */
	@Deprecated
	public static String githubReleasePackUrl() {
		String ver = modVersion();
		if (ver == null || ver.isBlank()) {
			return null;
		}
		return "https://github.com/" + GITHUB_OWNER_REPO + "/releases/download/v" + ver + "/minecard-cards.zip";
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

	/** True when vanilla will accept an in-game ResourcePackPush for this URL scheme/host. */
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
		// Local clients: always 127.0.0.1 + local SHA.
		String pushUrl = null;
		String pushSha = pack.sha1Hex();
		if (isLoopbackPlayer(player) && loopbackHttpUrl != null) {
			pushUrl = loopbackHttpUrl;
			pushSha = pack.sha1Hex();
		} else if (canAutoPush(packUrl) && autoPush) {
			pushUrl = packUrl;
			pushSha = packShaForUrl != null ? packShaForUrl : pack.sha1Hex();
		}

		String fingerprint = pushSha + "|" + (pushUrl != null ? pushUrl : "manual:" + packUrl);
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
			pushPack(player, pushUrl, pushSha);
			return OfferResult.PUSHED;
		}
		sendManualGuide(player);
		return OfferResult.MANUAL;
	}

	/** @deprecated prefer {@link #offerOrGuide(ServerPlayer)} */
	@Deprecated
	public static boolean offer(ServerPlayer player) {
		return offerOrGuide(player) != OfferResult.UNAVAILABLE;
	}

	private static void pushPack(ServerPlayer player, String url, String sha1Hex) {
		player.connection.send(new ClientboundResourcePackPushPacket(
			pack.id(),
			url,
			sha1Hex,
			false,
			Optional.of(Component.translatable("minecard.pack.prompt"))
		));
	}

	public static void sendManualGuide(ServerPlayer player) {
		boolean httpOnly = packUrl != null && packUrl.startsWith("http://") && !canAutoPush(packUrl);
		if (httpOnly) {
			player.sendSystemMessage(Component.translatable("minecard.pack.http_blocked"));
		}
		player.sendSystemMessage(Component.translatable("minecard.pack.manual_hint"));
		MutableComponent link = Component.translatable("minecard.pack.manual_click")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.GREEN)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(packUrl)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(packUrl))));
		player.sendSystemMessage(link);
		if (httpOnly) {
			player.sendSystemMessage(Component.translatable("minecard.pack.manual_firewall"));
		}
	}
}
