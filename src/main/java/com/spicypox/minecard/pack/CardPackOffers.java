package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.config.MinecardConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
				packUrl = resolvePackUrl(server, http.port());
				Minecard.LOGGER.info("Card pack URL for clients: {}", packUrl);
				if (packUrl.startsWith("http://") && !isLoopbackHost(hostFromUrl(packUrl))) {
					Minecard.LOGGER.warn(
						"Pack URL is plain HTTP to a remote host. Vanilla clients usually reject this. "
							+ "Set packUrl in config/minecard.json to an HTTPS URL "
							+ "(GitHub Release asset minecard-cards.zip), or leave packUrl empty "
							+ "to auto-use the GitHub release for this mod version."
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
	 * Priority: config {@code packUrl} → GitHub release HTTPS (when host is remote) →
	 * {@code http://packHost|server-ip|detected:port/minecard-cards.zip}.
	 */
	static String resolvePackUrl(MinecraftServer server, int port) {
		String configured = MinecardConfig.packUrl;
		if (configured != null && !configured.isBlank()) {
			return configured.trim();
		}
		String host = resolveHost(server);
		if (!isLoopbackHost(host)) {
			String gh = githubReleasePackUrl();
			if (gh != null) {
				Minecard.LOGGER.info(
					"packUrl empty and host {} is remote — using HTTPS GitHub pack (vanilla blocks cleartext HTTP).",
					host
				);
				return gh;
			}
		}
		return "http://" + host + ":" + port + "/minecard-cards.zip";
	}

	/**
	 * Config packHost → -Dminecard.packHost → server-ip ({@link MinecraftServer#getLocalIp()}) →
	 * first non-loopback IPv4 → 127.0.0.1.
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

	/** @deprecated use {@link #resolveHost(MinecraftServer)} */
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

	/** HTTPS GitHub asset for this mod version, or null if unknown. */
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

	/** Best URL to share for manual install (live offer URL, else GitHub release). */
	public static String manualDownloadUrl() {
		if (packUrl != null && !packUrl.isBlank()) {
			return packUrl;
		}
		return githubReleasePackUrl();
	}

	static boolean isLoopbackHost(String host) {
		if (host == null || host.isBlank()) {
			return true;
		}
		String h = host.trim().toLowerCase();
		return "127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h);
	}

	private static String hostFromUrl(String url) {
		try {
			return java.net.URI.create(url).getHost();
		} catch (Exception e) {
			return "";
		}
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
