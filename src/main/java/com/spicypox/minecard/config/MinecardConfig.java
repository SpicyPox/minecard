package com.spicypox.minecard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spicypox.minecard.Minecard;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads {@code config/minecard.json}; writes defaults on first run. */
public final class MinecardConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static int soloDecks = 1;
	public static int roomDecks = 6;
	public static int reshuffleBelow = 15;
	/** 0 = no turn countdown in multiplayer/solo. */
	public static int turnSeconds = 0;
	/** When true, house may hit soft 17 (also host can always choose Hit under 21). */
	public static boolean dealerHitsSoft17 = true;
	public static boolean insuranceEnabled = true;
	public static boolean surrenderEnabled = false;
	public static int historyRetentionDays = 90;
	public static long defaultBet = 10L;
	public static long startingBalance = 128L;
	/**
	 * Hostname/IP for the pack HTTP server URL. Empty → {@code server-ip} / auto-detect / 127.0.0.1.
	 * Open this port on the VPS firewall so players can download the zip in a browser.
	 */
	public static String packHost = "";
	public static int packPort = 8765;
	/**
	 * Optional public HTTPS URL for in-game Accept prompt. Leave empty on a private GitHub repo —
	 * the VPS will serve HTTP for browser/manual install instead (vanilla blocks remote HTTP pushes).
	 */
	public static String packUrl = "";

	private MinecardConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minecard.json");
		try {
			if (!Files.exists(path)) {
				writeDefaults(path);
				Minecard.LOGGER.info("Wrote default {}", path);
				return;
			}
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("historyRetentionDays")) {
					historyRetentionDays = root.get("historyRetentionDays").getAsInt();
				}
				if (root.has("defaultBet")) {
					defaultBet = root.get("defaultBet").getAsLong();
				}
				if (root.has("startingBalance")) {
					startingBalance = root.get("startingBalance").getAsLong();
				}
				if (root.has("packHost")) {
					packHost = root.get("packHost").getAsString();
				}
				if (root.has("packPort")) {
					packPort = root.get("packPort").getAsInt();
				}
				if (root.has("packUrl")) {
					packUrl = root.get("packUrl").getAsString();
				}
				if (root.has("blackjack")) {
					JsonObject bj = root.getAsJsonObject("blackjack");
					soloDecks = getInt(bj, "soloDecks", soloDecks);
					roomDecks = getInt(bj, "decks", roomDecks);
					reshuffleBelow = getInt(bj, "reshuffleBelow", reshuffleBelow);
					turnSeconds = getInt(bj, "turnSeconds", turnSeconds);
					dealerHitsSoft17 = getBool(bj, "dealerHitsSoft17", dealerHitsSoft17);
					insuranceEnabled = getBool(bj, "insuranceEnabled", insuranceEnabled);
					surrenderEnabled = getBool(bj, "surrenderEnabled", surrenderEnabled);
				}
			}
			com.spicypox.minecard.wallet.WalletConstants.syncFromConfig(startingBalance, defaultBet);
			Minecard.LOGGER.info(
				"Config: roomDecks={} insurance={} surrender={} soft17={} packHost={} packPort={} packUrl={}",
				roomDecks, insuranceEnabled, surrenderEnabled, dealerHitsSoft17,
				packHost.isBlank() ? "(auto)" : packHost,
				packPort,
				packUrl.isBlank() ? "(auto)" : packUrl
			);
		} catch (Exception e) {
			Minecard.LOGGER.error("Failed to load minecard.json — using defaults", e);
		}
	}

	private static void writeDefaults(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		JsonObject root = new JsonObject();
		root.addProperty("historyRetentionDays", historyRetentionDays);
		root.addProperty("defaultBet", defaultBet);
		root.addProperty("startingBalance", startingBalance);
		root.addProperty("packHost", packHost);
		root.addProperty("packPort", packPort);
		root.addProperty("packUrl", packUrl);
		JsonObject bj = new JsonObject();
		bj.addProperty("soloDecks", soloDecks);
		bj.addProperty("decks", roomDecks);
		bj.addProperty("reshuffleBelow", reshuffleBelow);
		bj.addProperty("turnSeconds", turnSeconds);
		bj.addProperty("dealerHitsSoft17", dealerHitsSoft17);
		bj.addProperty("insuranceEnabled", insuranceEnabled);
		bj.addProperty("surrenderEnabled", surrenderEnabled);
		bj.addProperty("blackjackPayoutNumerator", 3);
		bj.addProperty("blackjackPayoutDenominator", 2);
		bj.addProperty("splitEnabled", true);
		bj.addProperty("maxSplitHands", 2);
		bj.addProperty("splitAcesOneCard", true);
		root.add("blackjack", bj);
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(root, writer);
		}
		com.spicypox.minecard.wallet.WalletConstants.syncFromConfig(startingBalance, defaultBet);
	}

	private static int getInt(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	private static boolean getBool(JsonObject o, String key, boolean def) {
		return o.has(key) ? o.get(key).getAsBoolean() : def;
	}
}
