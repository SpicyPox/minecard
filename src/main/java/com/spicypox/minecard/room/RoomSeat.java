package com.spicypox.minecard.room;

import com.spicypox.minecard.wallet.EscrowHold;

import java.util.UUID;

public final class RoomSeat {
	private final UUID playerId;
	private String displayName;
	private long bet;
	private EscrowHold lock;
	private boolean ready;

	public RoomSeat(UUID playerId, String displayName) {
		this.playerId = playerId;
		this.displayName = displayName;
	}

	public UUID playerId() {
		return playerId;
	}

	public String displayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public long bet() {
		return bet;
	}

	public void setBet(long bet) {
		this.bet = bet;
	}

	public EscrowHold lock() {
		return lock;
	}

	public void setLock(EscrowHold lock) {
		this.lock = lock;
	}

	public boolean ready() {
		return ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}
}
