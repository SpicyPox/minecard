package com.spicypox.minecard.game.blackjack;

import java.util.UUID;

/** One seated player at a multiplayer table (may have split hands later). */
public final class TablePlayer {
	private final UUID playerId;
	private final String name;
	private final PlayerHandSeat seat = new PlayerHandSeat();
	private boolean dialogAway;
	private long insuranceBet;
	private boolean insuranceDecided;

	public TablePlayer(UUID playerId, String name, long bet) {
		this.playerId = playerId;
		this.name = name;
		seat.setBet(bet);
	}

	public UUID playerId() {
		return playerId;
	}

	public String name() {
		return name;
	}

	public PlayerHandSeat seat() {
		return seat;
	}

	public boolean dialogAway() {
		return dialogAway;
	}

	public void setDialogAway(boolean dialogAway) {
		this.dialogAway = dialogAway;
	}

	public long insuranceBet() {
		return insuranceBet;
	}

	public void setInsuranceBet(long insuranceBet) {
		this.insuranceBet = insuranceBet;
	}

	public boolean insuranceDecided() {
		return insuranceDecided;
	}

	public void setInsuranceDecided(boolean insuranceDecided) {
		this.insuranceDecided = insuranceDecided;
	}
}
