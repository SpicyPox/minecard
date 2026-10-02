package com.spicypox.minecard.game.poker;

import com.spicypox.minecard.card.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PokerSeat {
	private final UUID playerId;
	private final String name;
	private long stack;
	private final List<Card> hole = new ArrayList<>(2);
	private long streetCommitted;
	private long handCommitted;
	private boolean folded;
	private boolean allIn;
	private PokerActionStatus status = PokerActionStatus.WAITING;
	private boolean actedThisStreet;

	public PokerSeat(UUID playerId, String name, long stack) {
		this.playerId = playerId;
		this.name = name;
		this.stack = Math.max(0L, stack);
	}

	public UUID playerId() {
		return playerId;
	}

	public String name() {
		return name;
	}

	public long stack() {
		return stack;
	}

	public void setStack(long stack) {
		this.stack = Math.max(0L, stack);
	}

	public List<Card> hole() {
		return List.copyOf(hole);
	}

	void clearHole() {
		hole.clear();
	}

	void dealHole(Card a, Card b) {
		hole.clear();
		hole.add(a);
		hole.add(b);
	}

	public long streetCommitted() {
		return streetCommitted;
	}

	public long handCommitted() {
		return handCommitted;
	}

	public boolean folded() {
		return folded;
	}

	public boolean allIn() {
		return allIn;
	}

	public PokerActionStatus status() {
		return status;
	}

	void setStatus(PokerActionStatus status) {
		this.status = status;
	}

	boolean actedThisStreet() {
		return actedThisStreet;
	}

	void setActedThisStreet(boolean acted) {
		this.actedThisStreet = acted;
	}

	void resetForHand() {
		hole.clear();
		streetCommitted = 0L;
		handCommitted = 0L;
		folded = false;
		allIn = false;
		status = PokerActionStatus.WAITING;
		actedThisStreet = false;
	}

	void resetStreet() {
		streetCommitted = 0L;
		actedThisStreet = false;
		if (!folded && !allIn) {
			status = PokerActionStatus.WAITING;
		}
	}

	/** Move chips from stack into pot commitment. Returns amount moved. */
	long commit(long amount) {
		long pay = Math.min(amount, stack);
		stack -= pay;
		streetCommitted += pay;
		handCommitted += pay;
		if (stack == 0L) {
			allIn = true;
		}
		return pay;
	}

	void fold() {
		folded = true;
		status = PokerActionStatus.FOLD;
		actedThisStreet = true;
	}

	void credit(long amount) {
		if (amount > 0L) {
			stack += amount;
		}
	}

	/** Abort hand: put committed chips back on stack. */
	void returnCommitted() {
		stack += handCommitted;
		handCommitted = 0L;
		streetCommitted = 0L;
		allIn = false;
	}
}
