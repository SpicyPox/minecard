package com.spicypox.minecard.game.blackjack;

/**
 * One player hand (main or split) with its stake.
 */
public final class PlayerHandSeat {
	private final BlackjackHand hand = new BlackjackHand();
	private long bet;
	private boolean doubled;
	private boolean fromSplit;
	private boolean aceSplit;
	private boolean finished;
	private BlackjackOutcome outcome;
	private int shown;

	public BlackjackHand hand() {
		return hand;
	}

	public long bet() {
		return bet;
	}

	public void setBet(long bet) {
		this.bet = bet;
	}

	public boolean doubled() {
		return doubled;
	}

	public void setDoubled(boolean doubled) {
		this.doubled = doubled;
	}

	public boolean fromSplit() {
		return fromSplit;
	}

	public void setFromSplit(boolean fromSplit) {
		this.fromSplit = fromSplit;
	}

	public boolean aceSplit() {
		return aceSplit;
	}

	public void setAceSplit(boolean aceSplit) {
		this.aceSplit = aceSplit;
	}

	public boolean finished() {
		return finished;
	}

	public void setFinished(boolean finished) {
		this.finished = finished;
	}

	public BlackjackOutcome outcome() {
		return outcome;
	}

	public void setOutcome(BlackjackOutcome outcome) {
		this.outcome = outcome;
	}

	public int shown() {
		return shown;
	}

	public void setShown(int shown) {
		this.shown = shown;
	}

	public void clear() {
		hand.clear();
		bet = 0;
		doubled = false;
		fromSplit = false;
		aceSplit = false;
		finished = false;
		outcome = null;
		shown = 0;
	}
}
