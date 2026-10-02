package com.spicypox.minecard.room;

import com.spicypox.minecard.config.MinecardConfig;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory create-room wizard state for one host. */
public final class CreateRoomDraft {
	public enum Game {
		BLACKJACK,
		POKER
	}

	private static final Map<UUID, CreateRoomDraft> OPEN = new ConcurrentHashMap<>();

	private Game game = Game.BLACKJACK;
	private BjRoomRules rules = BjRoomRules.defaults();
	private long betAmount = Math.max(1L, MinecardConfig.defaultBet);
	private Identifier stakeItem = Identifier.withDefaultNamespace("oak_log");

	private CreateRoomDraft() {
	}

	public static CreateRoomDraft getOrCreate(UUID playerId) {
		return OPEN.computeIfAbsent(playerId, id -> new CreateRoomDraft());
	}

	public static void clear(UUID playerId) {
		OPEN.remove(playerId);
	}

	public Game game() {
		return game;
	}

	public void setGame(Game game) {
		this.game = game;
	}

	public BjRoomRules rules() {
		return rules;
	}

	public void setRules(BjRoomRules rules) {
		this.rules = rules.sanitized();
	}

	public long betAmount() {
		return betAmount;
	}

	public void setBetAmount(long betAmount) {
		this.betAmount = Math.max(1L, betAmount);
	}

	public Identifier stakeItem() {
		return stakeItem;
	}

	public void setStakeItem(Identifier stakeItem) {
		this.stakeItem = stakeItem;
	}
}
