package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.ui.Dialogs;
import com.spicypox.minecard.wallet.SessionSavedData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
		ServerLifecycleEvents.SERVER_STOPPING.register(BlackjackGames::persistAll);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BlackjackSession session = SESSIONS.get(handler.player.getUUID());
			if (session != null) {
				session.emergencyAway();
				persist(server, session);
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
		BlackjackSession session = SessionSavedData.get(player.level().getServer())
			.getSolo(player.getUUID())
			.map(BlackjackSession::fromState)
			.orElseGet(() -> new BlackjackSession(player.getUUID()));
		SESSIONS.put(player.getUUID(), session);
		BlackjackDialog.open(player, session);
		if (session.phase() != BlackjackSession.Phase.DEALING
			|| session.reveal().dealStep() > 0) {
			player.sendSystemMessage(Component.translatable("minecard.bj.resumed"));
		}
	}

	private static void persist(MinecraftServer server, BlackjackSession session) {
		if (server == null) {
			return;
		}
		SessionSavedData.get(server).putSolo(session.playerId(), session.snapshot());
	}

	private static void persistAll(MinecraftServer server) {
		for (BlackjackSession session : SESSIONS.values()) {
			persist(server, session);
		}
	}

	public static void onClick(ServerPlayer player, String action) {
		if ("leave".equals(action) || "close".equals(action) || "emergency".equals(action)) {
			handleLeave(player);
			return;
		}

		BlackjackSession session = SESSIONS.get(player.getUUID());
		if (session == null) {
			start(player);
			return;
		}
		if (session.phase() == BlackjackSession.Phase.DEALING
			|| session.phase() == BlackjackSession.Phase.COLLECTING
			|| session.phase() == BlackjackSession.Phase.DEALER_TURN) {
			return;
		}
		if (session.phase() == BlackjackSession.Phase.INSURANCE) {
			switch (action) {
				case "insurance_yes" -> session.takeInsurance();
				case "insurance_no" -> session.declineInsurance();
				default -> {
					return;
				}
			}
			BlackjackDialog.open(player, session);
			return;
		}
		switch (action) {
			case "hit" -> session.hit();
			case "stand" -> session.stand();
			case "double" -> session.doubleDown();
			case "split" -> session.split();
			case "surrender" -> session.surrender();
			case "again" -> session.playAgain();
			case "wait" -> {
				return;
			}
			default -> {
				return;
			}
		}
		BlackjackDialog.open(player, session);
	}

	/**
	 * Footer Leave / ESC: mid-round steps away (state kept, timer in chat);
	 * resolved / idle ends the table and refunds held stake.
	 */
	private static void handleLeave(ServerPlayer player) {
		BlackjackSession session = SESSIONS.get(player.getUUID());
		if (session == null) {
			Dialogs.clear(player);
			return;
		}
		if (session.phase() == BlackjackSession.Phase.RESOLVED
			|| session.phase() == BlackjackSession.Phase.COLLECTING) {
			SESSIONS.remove(player.getUUID());
			session.leave();
			SessionSavedData.get(player.level().getServer()).removeSolo(player.getUUID());
			Dialogs.clear(player);
			player.sendSystemMessage(styled(
				"minecard.bj.left",
				ChatFormatting.GRAY
			));
			return;
		}
		session.emergencyAway();
		persist(player.level().getServer(), session);
		Dialogs.clear(player);
		player.sendSystemMessage(styled("minecard.bj.leave_away", ChatFormatting.YELLOW));
		pushTimerChat(player, session);
	}

	private static void pushTimerChat(ServerPlayer player, BlackjackSession session) {
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return;
		}
		MutableComponent line = Component.empty()
			.append(Component.translatable("minecard.bj.timer.prefix").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.append(Component.translatable("minecard.bj.timer.chat", session.turnSecondsLeft())
				.withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(line);
	}

	private static Component styled(String key, ChatFormatting color) {
		return Component.empty()
			.append(Component.translatable("minecard.bj.timer.prefix").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.append(Component.translatable(key).withStyle(color));
	}

	public static void stop(UUID playerId) {
		BlackjackSession session = SESSIONS.remove(playerId);
		if (session != null) {
			session.leave();
		}
	}

	public static void stop(ServerPlayer player) {
		stop(player.getUUID());
		SessionSavedData.get(player.level().getServer()).removeSolo(player.getUUID());
	}

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
			if (dirty || animStep) {
				persist(server, session);
			}

			if (player == null) {
				continue;
			}

			if (session.dialogAway()) {
				// Chat countdown only while away (Leave/ESC); stops when /bj reopens the dialog.
				if (timerPulse && (session.phase() == BlackjackSession.Phase.PLAYER_TURN
					|| session.phase() == BlackjackSession.Phase.INSURANCE)) {
					pushTimerChat(player, session);
				}
				if (session.phase() == BlackjackSession.Phase.RESOLVED && dirty) {
					player.sendSystemMessage(styled("minecard.bj.emergency_timeout", ChatFormatting.RED));
				}
				continue;
			}

			// Live timer on action bar — do NOT reopen the dialog each second (resets scroll).
			if (timerPulse && (session.phase() == BlackjackSession.Phase.PLAYER_TURN
				|| session.phase() == BlackjackSession.Phase.INSURANCE)) {
				player.sendSystemMessage(
					Component.translatable("minecard.bj.timer.actionbar", session.turnSecondsLeft())
						.withStyle(ChatFormatting.GOLD),
					true
				);
			}

			// Refresh only when the table actually changes (deal / hit / buttons).
			if (animStep || dirty) {
				BlackjackDialog.open(player, session);
			}
		}
	}
}
