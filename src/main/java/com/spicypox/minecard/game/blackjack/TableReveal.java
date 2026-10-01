package com.spicypox.minecard.game.blackjack;

/**
 * Presentation state for cards on the table — separate from logical hands in the shoe.
 */
public final class TableReveal {
	public enum Mode {
		IDLE,
		DEALING,
		DEALER_PLAY,
		COLLECTING
	}

	/** Ticks between each dealt / collected / dealer-hit card (~0.5s). */
	public static final int STEP_TICKS = 10;

	private Mode mode = Mode.IDLE;
	private int cooldown;
	private int dealStep;
	private int dealerShown;
	private boolean holeFaceUp;
	/** After dealer reaches ≥17, wait one beat before settling. */
	private boolean dealerSettlePause;

	public Mode mode() {
		return mode;
	}

	public int dealerShown() {
		return dealerShown;
	}

	public boolean holeFaceUp() {
		return holeFaceUp;
	}

	public int dealStep() {
		return dealStep;
	}

	public int cooldown() {
		return cooldown;
	}

	public boolean dealerSettlePause() {
		return dealerSettlePause;
	}

	public boolean dealerHoleHidden() {
		if (mode == Mode.COLLECTING || mode == Mode.DEALER_PLAY) {
			return false;
		}
		return !holeFaceUp && dealerShown >= 2;
	}

	public boolean[] dealerFaceUpFlags(int visibleCount) {
		boolean[] flags = new boolean[visibleCount];
		for (int i = 0; i < visibleCount; i++) {
			flags[i] = !(dealerHoleHidden() && i == 1);
		}
		return flags;
	}

	public void restore(
		int dealerShown,
		boolean holeFaceUp,
		int dealStep,
		int cooldown,
		Mode mode,
		boolean dealerSettlePause
	) {
		this.dealerShown = dealerShown;
		this.holeFaceUp = holeFaceUp;
		this.dealStep = dealStep;
		this.cooldown = cooldown;
		this.mode = mode;
		this.dealerSettlePause = dealerSettlePause;
	}

	public void idleFullyShown(int dealerCards, boolean revealHole) {
		mode = Mode.IDLE;
		cooldown = 0;
		dealStep = 0;
		dealerShown = dealerCards;
		holeFaceUp = revealHole;
		dealerSettlePause = false;
	}

	public void startCollect() {
		mode = Mode.COLLECTING;
		cooldown = 0;
		dealStep = 0;
		dealerSettlePause = false;
	}

	public void startDeal() {
		mode = Mode.DEALING;
		cooldown = 0;
		dealStep = 0;
		dealerShown = 0;
		holeFaceUp = false;
		dealerSettlePause = false;
	}

	/**
	 * Hole already face-up for the player to see; wait a full step before the first hit
	 * so Stand → flip does not instantly dump hit cards.
	 */
	public void startDealerPlay(int openingShown) {
		mode = Mode.DEALER_PLAY;
		cooldown = STEP_TICKS;
		dealStep = 0;
		holeFaceUp = true;
		dealerShown = Math.max(0, openingShown);
		dealerSettlePause = false;
	}

	public void revealHole() {
		holeFaceUp = true;
	}

	public void setDealerShown(int n) {
		dealerShown = Math.max(0, n);
	}

	public void showDealerHit() {
		dealerShown++;
	}

	public void beginDealerSettlePause() {
		dealerSettlePause = true;
		// Ensure a full beat after the last visible card before settle.
		cooldown = STEP_TICKS;
	}

	public void clearDealerSettlePause() {
		dealerSettlePause = false;
	}

	/**
	 * @return true when a presentation pulse should run (deal / hit / collect)
	 */
	public boolean tickCooldown() {
		if (mode != Mode.DEALING && mode != Mode.COLLECTING && mode != Mode.DEALER_PLAY) {
			return false;
		}
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		cooldown = STEP_TICKS;
		return true;
	}

	public DealPulse pulseDeal() {
		return switch (dealStep) {
			case 0 -> {
				dealStep = 1;
				yield DealPulse.PLAYER_1;
			}
			case 1 -> {
				dealerShown = 1;
				dealStep = 2;
				yield DealPulse.DEALER_1;
			}
			case 2 -> {
				dealStep = 3;
				yield DealPulse.PLAYER_2;
			}
			case 3 -> {
				dealerShown = 2;
				holeFaceUp = false;
				dealStep = 4;
				yield DealPulse.DEALER_HOLE;
			}
			default -> {
				mode = Mode.IDLE;
				yield DealPulse.DONE;
			}
		};
	}

	public boolean collectDealerOne() {
		if (dealerShown <= 0) {
			return false;
		}
		dealerShown--;
		return true;
	}

	public void finishCollect() {
		mode = Mode.IDLE;
		dealerShown = 0;
		holeFaceUp = false;
		dealStep = 0;
		cooldown = 0;
		dealerSettlePause = false;
	}

	public enum DealPulse {
		PLAYER_1,
		DEALER_1,
		PLAYER_2,
		DEALER_HOLE,
		DONE
	}
}
