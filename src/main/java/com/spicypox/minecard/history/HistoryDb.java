package com.spicypox.minecard.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spicypox.minecard.Minecard;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite history at {@code world/minecard/minecard.db}. Optional until a world loads.
 */
public final class HistoryDb {
	private static Connection connection;
	private static boolean registered;

	private HistoryDb() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLifecycleEvents.SERVER_STARTED.register(HistoryDb::open);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> close());
	}

	private static void open(MinecraftServer server) {
		close();
		try {
			Path dir = server.getWorldPath(LevelResource.ROOT).resolve("minecard");
			Files.createDirectories(dir);
			Path db = dir.resolve("minecard.db");
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
			try (Statement st = connection.createStatement()) {
				st.execute("PRAGMA journal_mode=WAL");
				st.execute("""
					CREATE TABLE IF NOT EXISTS players (
					  uuid TEXT PRIMARY KEY NOT NULL,
					  last_name TEXT NOT NULL,
					  first_seen_ms INTEGER NOT NULL,
					  last_seen_ms INTEGER NOT NULL
					)
					""");
				st.execute("""
					CREATE TABLE IF NOT EXISTS ledger (
					  id INTEGER PRIMARY KEY AUTOINCREMENT,
					  ts_ms INTEGER NOT NULL,
					  player_uuid TEXT NOT NULL,
					  item_id TEXT NOT NULL,
					  delta INTEGER NOT NULL,
					  balance_after INTEGER NOT NULL,
					  source TEXT NOT NULL,
					  reason TEXT NOT NULL,
					  session_id TEXT,
					  hand_id TEXT,
					  idempotency_key TEXT NOT NULL UNIQUE
					)
					""");
				st.execute("""
					CREATE TABLE IF NOT EXISTS player_stats (
					  player_uuid TEXT NOT NULL,
					  game TEXT NOT NULL,
					  hands_played INTEGER NOT NULL DEFAULT 0,
					  wins INTEGER NOT NULL DEFAULT 0,
					  losses INTEGER NOT NULL DEFAULT 0,
					  pushes INTEGER NOT NULL DEFAULT 0,
					  blackjacks INTEGER NOT NULL DEFAULT 0,
					  net_by_item_json TEXT NOT NULL DEFAULT '{}',
					  last_hand_at_ms INTEGER,
					  PRIMARY KEY (player_uuid, game)
					)
					""");
				st.execute("""
					CREATE TABLE IF NOT EXISTS hands (
					  hand_id TEXT PRIMARY KEY NOT NULL,
					  session_id TEXT NOT NULL,
					  hand_index INTEGER NOT NULL,
					  started_at_ms INTEGER NOT NULL,
					  ended_at_ms INTEGER,
					  dealer_cards_json TEXT,
					  result_summary TEXT
					)
					""");
				st.execute("""
					CREATE TABLE IF NOT EXISTS hand_seats (
					  hand_id TEXT NOT NULL,
					  seat_index INTEGER NOT NULL,
					  player_uuid TEXT NOT NULL,
					  bet INTEGER NOT NULL,
					  cards_json TEXT NOT NULL,
					  outcome TEXT,
					  payout INTEGER NOT NULL DEFAULT 0,
					  actions_summary TEXT,
					  PRIMARY KEY (hand_id, seat_index)
					)
					""");
			}
			Minecard.LOGGER.info("History DB open at {}", db);
		} catch (Exception e) {
			Minecard.LOGGER.error("Failed to open history DB", e);
			connection = null;
		}
	}

	private static void close() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException ignored) {
			}
			connection = null;
		}
	}

	public static boolean available() {
		return connection != null;
	}

	public static void touchPlayer(UUID uuid, String name) {
		if (connection == null) {
			return;
		}
		long now = System.currentTimeMillis();
		try (PreparedStatement ps = connection.prepareStatement("""
			INSERT INTO players(uuid, last_name, first_seen_ms, last_seen_ms)
			VALUES(?,?,?,?)
			ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name, last_seen_ms=excluded.last_seen_ms
			""")) {
			ps.setString(1, uuid.toString());
			ps.setString(2, name);
			ps.setLong(3, now);
			ps.setLong(4, now);
			ps.executeUpdate();
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history touchPlayer: {}", e.toString());
		}
	}

	public static void writeLedger(
		UUID playerId,
		String itemId,
		long delta,
		long balanceAfter,
		String source,
		String reason,
		String sessionId,
		String handId,
		String idempotencyKey
	) {
		if (connection == null) {
			return;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
			INSERT OR IGNORE INTO ledger(
			  ts_ms, player_uuid, item_id, delta, balance_after, source, reason,
			  session_id, hand_id, idempotency_key
			) VALUES(?,?,?,?,?,?,?,?,?,?)
			""")) {
			ps.setLong(1, System.currentTimeMillis());
			ps.setString(2, playerId.toString());
			ps.setString(3, itemId);
			ps.setLong(4, delta);
			ps.setLong(5, balanceAfter);
			ps.setString(6, source);
			ps.setString(7, reason);
			ps.setString(8, sessionId);
			ps.setString(9, handId);
			ps.setString(10, idempotencyKey);
			ps.executeUpdate();
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history ledger: {}", e.toString());
		}
	}

	public static void recordBjSettle(
		UUID playerId,
		String handId,
		String sessionId,
		String outcome,
		long bet,
		long payout,
		long netDelta,
		String itemId,
		long balanceAfter,
		String dealerCardsJson,
		String playerCardsJson
	) {
		if (connection == null) {
			return;
		}
		long now = System.currentTimeMillis();
		try {
			try (PreparedStatement hand = connection.prepareStatement("""
				INSERT OR REPLACE INTO hands(
				  hand_id, session_id, hand_index, started_at_ms, ended_at_ms, dealer_cards_json, result_summary
				) VALUES(?,?,?,?,?,?,?)
				""")) {
				hand.setString(1, handId);
				hand.setString(2, sessionId);
				hand.setInt(3, 0);
				hand.setLong(4, now);
				hand.setLong(5, now);
				hand.setString(6, dealerCardsJson);
				hand.setString(7, outcome);
				hand.executeUpdate();
			}
			try (PreparedStatement seat = connection.prepareStatement("""
				INSERT OR REPLACE INTO hand_seats(
				  hand_id, seat_index, player_uuid, bet, cards_json, outcome, payout, actions_summary
				) VALUES(?,?,?,?,?,?,?,?)
				""")) {
				seat.setString(1, handId);
				seat.setInt(2, 0);
				seat.setString(3, playerId.toString());
				seat.setLong(4, bet);
				seat.setString(5, playerCardsJson);
				seat.setString(6, outcome);
				seat.setLong(7, payout);
				seat.setString(8, "");
				seat.executeUpdate();
			}
			writeLedger(
				playerId, itemId, netDelta, balanceAfter, "PAYOUT", "BJ_SETTLE",
				sessionId, handId, handId + ":payout:0"
			);
			bumpStats(playerId, outcome, itemId, netDelta);
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history recordBjSettle: {}", e.toString());
		}
	}

	public record StatsSummary(
		int handsPlayed,
		int wins,
		int losses,
		int pushes,
		int blackjacks,
		String netByItemJson
	) {
	}

	public record HandRow(String handId, String outcome, long bet, long payout) {
	}

	public record HandDetail(
		String handId,
		String outcome,
		long bet,
		long payout,
		String playerCardsJson,
		String dealerCardsJson
	) {
	}

	/** Recent ledger lines for admin / profile (empty if DB unavailable). */
	public static List<String> recentLedger(UUID playerId, int limit) {
		List<String> out = new ArrayList<>();
		if (connection == null) {
			return out;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
			SELECT ts_ms, item_id, delta, balance_after, reason FROM ledger
			WHERE player_uuid=? ORDER BY id DESC LIMIT ?
			""")) {
			ps.setString(1, playerId.toString());
			ps.setInt(2, Math.max(1, Math.min(limit, 50)));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(rs.getLong(1) + " " + rs.getString(5) + " "
						+ rs.getLong(3) + " " + rs.getString(2)
						+ " bal=" + rs.getLong(4));
				}
			}
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history recentLedger: {}", e.toString());
		}
		return out;
	}

	public static Optional<StatsSummary> stats(UUID playerId) {
		if (connection == null) {
			return Optional.empty();
		}
		try (PreparedStatement ps = connection.prepareStatement("""
			SELECT hands_played, wins, losses, pushes, blackjacks, net_by_item_json
			FROM player_stats WHERE player_uuid=? AND game='BJ'
			""")) {
			ps.setString(1, playerId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return Optional.empty();
				}
				String net = rs.getString(6);
				return Optional.of(new StatsSummary(
					rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
					net == null || net.isBlank() ? "{}" : net
				));
			}
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history stats: {}", e.toString());
			return Optional.empty();
		}
	}

	public static String statsLine(UUID playerId) {
		return stats(playerId)
			.map(s -> "hands=" + s.handsPlayed()
				+ " W/L/P/BJ=" + s.wins() + "/" + s.losses()
				+ "/" + s.pushes() + "/" + s.blackjacks()
				+ " net=" + s.netByItemJson())
			.orElse(connection == null ? "history offline" : "no BJ stats yet");
	}

	public static List<HandRow> recentHands(UUID playerId, int limit) {
		List<HandRow> out = new ArrayList<>();
		if (connection == null) {
			return out;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
			SELECT hs.hand_id, hs.outcome, hs.bet, hs.payout
			FROM hand_seats hs
			WHERE hs.player_uuid=?
			ORDER BY hs.hand_id DESC
			LIMIT ?
			""")) {
			ps.setString(1, playerId.toString());
			ps.setInt(2, Math.max(1, Math.min(limit, 30)));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new HandRow(
						rs.getString(1),
						rs.getString(2) == null ? "?" : rs.getString(2),
						rs.getLong(3),
						rs.getLong(4)
					));
				}
			}
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history recentHands: {}", e.toString());
		}
		return out;
	}

	public static Optional<HandDetail> handDetail(UUID playerId, String handId) {
		if (connection == null || handId == null || handId.isBlank()) {
			return Optional.empty();
		}
		try (PreparedStatement ps = connection.prepareStatement("""
			SELECT hs.hand_id, hs.outcome, hs.bet, hs.payout, hs.cards_json, h.dealer_cards_json
			FROM hand_seats hs
			JOIN hands h ON h.hand_id = hs.hand_id
			WHERE hs.player_uuid=? AND hs.hand_id=?
			""")) {
			ps.setString(1, playerId.toString());
			ps.setString(2, handId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return Optional.empty();
				}
				return Optional.of(new HandDetail(
					rs.getString(1),
					rs.getString(2) == null ? "?" : rs.getString(2),
					rs.getLong(3),
					rs.getLong(4),
					rs.getString(5) == null ? "[]" : rs.getString(5),
					rs.getString(6) == null ? "[]" : rs.getString(6)
				));
			}
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history handDetail: {}", e.toString());
			return Optional.empty();
		}
	}

	private static void bumpStats(UUID playerId, String outcome, String itemId, long netDelta) {
		boolean win = "WIN".equals(outcome) || "DEALER_BUST".equals(outcome) || "PLAYER_BLACKJACK".equals(outcome);
		boolean loss = "LOSE".equals(outcome) || "PLAYER_BUST".equals(outcome) || "SURRENDER".equals(outcome);
		boolean push = "PUSH".equals(outcome);
		boolean bj = "PLAYER_BLACKJACK".equals(outcome);
		String mergedNet = mergeNetJson(readNetJson(playerId), itemId, netDelta);
		try (PreparedStatement ps = connection.prepareStatement("""
			INSERT INTO player_stats(
			  player_uuid, game, hands_played, wins, losses, pushes, blackjacks, net_by_item_json, last_hand_at_ms
			) VALUES(?,?,1,?,?,?,?,?,?)
			ON CONFLICT(player_uuid, game) DO UPDATE SET
			  hands_played = hands_played + 1,
			  wins = wins + excluded.wins,
			  losses = losses + excluded.losses,
			  pushes = pushes + excluded.pushes,
			  blackjacks = blackjacks + excluded.blackjacks,
			  net_by_item_json = excluded.net_by_item_json,
			  last_hand_at_ms = excluded.last_hand_at_ms
			""")) {
			ps.setString(1, playerId.toString());
			ps.setString(2, "BJ");
			ps.setInt(3, win ? 1 : 0);
			ps.setInt(4, loss ? 1 : 0);
			ps.setInt(5, push ? 1 : 0);
			ps.setInt(6, bj ? 1 : 0);
			ps.setString(7, mergedNet);
			ps.setLong(8, System.currentTimeMillis());
			ps.executeUpdate();
		} catch (SQLException e) {
			Minecard.LOGGER.warn("history bumpStats: {}", e.toString());
		}
	}

	private static String readNetJson(UUID playerId) {
		try (PreparedStatement ps = connection.prepareStatement(
			"SELECT net_by_item_json FROM player_stats WHERE player_uuid=? AND game='BJ'")) {
			ps.setString(1, playerId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					String s = rs.getString(1);
					return s == null || s.isBlank() ? "{}" : s;
				}
			}
		} catch (SQLException ignored) {
		}
		return "{}";
	}

	private static String mergeNetJson(String current, String itemId, long delta) {
		try {
			JsonObject obj = JsonParser.parseString(current == null || current.isBlank() ? "{}" : current)
				.getAsJsonObject();
			long prev = obj.has(itemId) ? obj.get(itemId).getAsLong() : 0L;
			obj.addProperty(itemId, prev + delta);
			return obj.toString();
		} catch (RuntimeException e) {
			return "{\"" + itemId + "\":" + delta + "}";
		}
	}
}
