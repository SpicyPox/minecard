package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.ui.Dialogs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlackjackGames {
	private static final Map<UUID, BlackjackSession> SESSIONS = new ConcurrentHashMap<>();
	private static boolean registered;

	private BlackjackGames() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(BlackjackGames::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BlackjackSession session = SESSIONS.get(handler.player.getUUID());
			if (session != null) {
				// Keep escrow + snapshot; timer keeps running (emergency semantics).
				session.emergencyAway();
			}
		});
	}

	public static void start(ServerPlayer player) {
		register();
		BlackjackSession existing = SESSIONS.get(player.getUUID());
		if (existing != null) {
			BlackjackDialog.open(player, existing);
			player.sendSystemMessage(Component.translatable("minecard.bj.resumed"));
			return;
		}
		BlackjackSession session = new BlackjackSession(player.getUUID());
		SESSIONS.put(player.getUUID(), session);
		BlackjackDialog.open(player, session);
	}

	public static void onClick(ServerPlayer player, String action) {
		if ("leave".equals(action) || "close".equals(action)) {
			BlackjackSession session = SESSIONS.remove(player.getUUID());
			if (session != null) {
				session.leave();
			}
			Dialogs.clear(player);
			player.sendSystemMessage(Component.translatable("minecard.bj.left"));
			return;
		}
		if ("emergency".equals(action)) {
			BlackjackSession session = SESSIONS.get(player.getUUID());
			if (session == null) {
				Dialogs.clear(player);
				return;
			}
			if (session.phase() == BlackjackSession.Phase.RESOLVED) {
				SESSIONS.remove(player.getUUID());
				session.leave();
				Dialogs.clear(player);
				return;
			}
			session.emergencyAway();
			Dialogs.clear(player);
			player.sendSystemMessage(Component.translatable("minecard.bj.emergency_hint"));
			pushTimerChat(player, session);
			return;
		}

		BlackjackSession session = SESSIONS.get(player.getUUID());
		if (session == null) {
			start(player);
			return;
		}
		if (session.phase() == BlackjackSession.Phase.DEALING
			|| session.phase() == BlackjackSession.Phase.COLLECTING) {
			return;
		}
		switch (action) {
			case "hit" -> session.hit();
			case "stand" -> session.stand();
			case "double" -> session.doubleDown();
			case "split" -> session.split();
			case "again" -> session.playAgain();
			case "wait", "pad" -> {
				return;
			}
			default -> {
				return;
			}
		}
		BlackjackDialog.open(player, session);
	}

	private static void pushTimerChat(ServerPlayer player, BlackjackSession session) {
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return;
		}
		player.sendSystemMessage(Component.translatable(
			"minecard.bj.timer.chat",
			session.turnSecondsLeft()
		));
	}

	public static void stop(UUID playerId) {
		BlackjackSession session = SESSIONS.remove(playerId);
		if (session != null) {
			session.leave();
		}
	}

	/** Expose live session for tests / future room restore. */
	public static BlackjackSession session(UUID playerId) {
		return SESSIONS.get(playerId);
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, BlackjackSession>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, BlackjackSession> entry = it.next();
			UUID id = entry.getKey();
			BlackjackSession session = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(id);

			boolean animStep = session.tick();
			boolean timerPulse = session.tickTurnTimer();
			boolean dirty = session.consumeDirty();
			boolean refresh = animStep || timerPulse || dirty;

			if (player == null) {
				// Offline: keep session; emergency timeout may resolve to lose.
				continue;
			}
			if (session.dialogAway()) {
				if (timerPulse && session.phase() == BlackjackSession.Phase.PLAYER_TURN) {
					pushTimerChat(player, session);
				}
				if (session.phase() == BlackjackSession.Phase.RESOLVED && dirty) {
					player.sendSystemMessage(Component.translatable("minecard.bj.emergency_timeout"));
				}
				continue;
			}
			if (refresh) {
				BlackjackDialog.open(player, session);
			}
		}
	}
}
