package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import com.spicypox.minecard.config.MinecardConfig;
import com.spicypox.minecard.history.CardJson;
import com.spicypox.minecard.history.HistoryDb;
import com.spicypox.minecard.wallet.ItemSource;
import com.spicypox.minecard.wallet.WalletConstants;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Solo blackjack. Logical hands live in seats/dealer; what the dialog shows is {@link TableReveal}.
 */
public final class BlackjackSession {
	public enum Phase {
		COLLECTING,
		DEALING,
		/** Dealer Ace up — offer insurance before peek / play. */
		INSURANCE,
		PLAYER_TURN,
		/** Dealer flips hole then draws to 17 — animated via {@link TableReveal.Mode#DEALER_PLAY}. */
		DEALER_TURN,
		RESOLVED
	}

	public static final int STEP_TICKS = TableReveal.STEP_TICKS;

	public static int turnTicks() {
		int sec = MinecardConfig.turnSeconds;
		if (sec <= 0) {
			return 0;
		}
		return sec * 20;
	}

	private final UUID playerId;
	private final Random random;
	private final List<Card> shoe = new ArrayList<>();
	private final BlackjackHand dealer = new BlackjackHand();
	private final List<PlayerHandSeat> seats = new ArrayList<>();
	private final TableReveal reveal = new TableReveal();

	private int activeHand;
	private boolean splitUsed;

	private Phase phase = Phase.DEALING;
	private BlackjackOutcome primaryOutcome;
	private boolean dirty = true;

	private long unitBet = WalletConstants.DEFAULT_BET;
	private long held;
	private long balance;
	private long insuranceBet;
	private Identifier stakeItemId = StakeItem.DEFAULT_ID;
	private int turnTicksLeft;
	private boolean dialogAway;

	public BlackjackSession(UUID playerId, long seed) {
		this(playerId, seed, true);
	}

	public BlackjackSession(UUID playerId) {
		this(playerId, System.nanoTime(), true);
	}

	public BlackjackSession(long seed) {
		this(new UUID(0L, seed), seed, true);
	}

	public BlackjackSession() {
		this(UUID.randomUUID(), System.nanoTime(), true);
	}

	private BlackjackSession(UUID playerId, long seed, boolean deal) {
		this.playerId = playerId;
		this.random = new Random(seed);
		this.stakeItemId = StakeItem.DEFAULT_ID;
		Wallets.ensureStartingBalance(playerId);
		this.balance = Wallets.balance(playerId, stakeItemId);
		if (deal) {
			reshuffle();
			beginDeal();
		}
	}

	public UUID playerId() {
		return playerId;
	}

	public Phase phase() {
		return phase;
	}

	public Optional<BlackjackOutcome> outcome() {
		return Optional.ofNullable(primaryOutcome);
	}

	public BlackjackHand dealerHand() {
		return dealer;
	}

	public List<PlayerHandSeat> seats() {
		return List.copyOf(seats);
	}

	public int activeHandIndex() {
		return activeHand;
	}

	public PlayerHandSeat activeSeat() {
		return seats.get(activeHand);
	}

	public BlackjackHand playerHand() {
		return seats.isEmpty() ? new BlackjackHand() : seats.get(0).hand();
	}

	public TableReveal reveal() {
		return reveal;
	}

	public boolean dealerHoleHidden() {
		return reveal.dealerHoleHidden();
	}

	public List<Card> visibleDealerCards() {
		List<Card> all = dealer.cards();
		return all.subList(0, Math.min(reveal.dealerShown(), all.size()));
	}

	public boolean[] dealerFaceUpFlags() {
		return reveal.dealerFaceUpFlags(visibleDealerCards().size());
	}

	public List<Card> visiblePlayerCards(int seatIndex) {
		PlayerHandSeat seat = seats.get(seatIndex);
		List<Card> all = seat.hand().cards();
		return all.subList(0, Math.min(seat.shown(), all.size()));
	}

	public int visibleDealerScore() {
		if (dealerHoleHidden()) {
			return scorePrefix(dealer.cards(), 1);
		}
		return scorePrefix(dealer.cards(), reveal.dealerShown());
	}

	public int visiblePlayerScore(int seatIndex) {
		PlayerHandSeat seat = seats.get(seatIndex);
		return scorePrefix(seat.hand().cards(), seat.shown());
	}

	public long unitBet() {
		return unitBet;
	}

	public long held() {
		return held;
	}

	public long balance() {
		return balance;
	}

	public long totalBet() {
		long sum = 0;
		for (PlayerHandSeat seat : seats) {
			sum += seat.bet();
		}
		return sum;
	}

	public Identifier stakeItemId() {
		return stakeItemId;
	}

	public int turnTicksLeft() {
		return turnTicksLeft;
	}

	public int turnSecondsLeft() {
		return Math.max(0, (turnTicksLeft + 19) / 20);
	}

	public boolean dialogAway() {
		return dialogAway;
	}

	public void showDialog() {
		dialogAway = false;
		markDirty();
	}

	public void emergencyAway() {
		dialogAway = true;
		dirty = false;
	}

	public boolean canDouble() {
		if (phase != Phase.PLAYER_TURN || seats.isEmpty()) {
			return false;
		}
		PlayerHandSeat seat = activeSeat();
		return seat.hand().size() == 2
			&& !seat.doubled()
			&& !seat.aceSplit()
			&& !seat.finished()
			&& balance >= seat.bet();
	}

	public boolean canSplit() {
		if (phase != Phase.PLAYER_TURN || splitUsed || seats.size() != 1) {
			return false;
		}
		PlayerHandSeat seat = seats.get(0);
		if (seat.hand().size() != 2 || seat.finished() || balance < seat.bet()) {
			return false;
		}
		List<Card> cards = seat.hand().cards();
		return cards.get(0).rank() == cards.get(1).rank();
	}

	public boolean consumeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	public void markDirty() {
		dirty = true;
	}

	public BlackjackRoundState snapshot() {
		BlackjackRoundState.Builder b = new BlackjackRoundState.Builder()
			.playerId(playerId)
			.phase(phase)
			.shoe(shoe)
			.dealerCards(dealer.cards())
			.dealerShown(reveal.dealerShown())
			.holeFaceUp(reveal.holeFaceUp())
			.activeHand(activeHand)
			.splitUsed(splitUsed)
			.unitBet(unitBet)
			.held(held)
			.balance(balance)
			.stakeItemId(stakeItemId)
			.turnTicksLeft(turnTicksLeft)
			.dialogAway(dialogAway)
			.dealIndex(reveal.dealStep())
			.stepCooldown(reveal.cooldown())
			.primaryOutcome(primaryOutcome);
		for (PlayerHandSeat seat : seats) {
			b.addHand(new BlackjackRoundState.HandSnapshot(
				seat.hand().cards(),
				seat.bet(),
				seat.doubled(),
				seat.fromSplit(),
				seat.aceSplit(),
				seat.finished(),
				seat.outcome(),
				seat.shown()
			));
		}
		return b.build();
	}

	public static BlackjackSession fromState(BlackjackRoundState state) {
		BlackjackSession session = new BlackjackSession(state.playerId, 0L, false);
		session.shoe.clear();
		session.shoe.addAll(state.shoe);
		session.dealer.clear();
		state.dealerCards.forEach(session.dealer::add);
		session.seats.clear();
		for (BlackjackRoundState.HandSnapshot snap : state.playerHands) {
			PlayerHandSeat seat = new PlayerHandSeat();
			snap.cards().forEach(seat.hand()::add);
			seat.setBet(snap.bet());
			seat.setDoubled(snap.doubled());
			seat.setFromSplit(snap.fromSplit());
			seat.setAceSplit(snap.aceSplit());
			seat.setFinished(snap.finished());
			seat.setOutcome(snap.outcome());
			seat.setShown(snap.shown());
			session.seats.add(seat);
		}
		session.activeHand = state.activeHand;
		session.splitUsed = state.splitUsed;
		session.unitBet = state.unitBet;
		session.held = state.held;
		session.balance = state.balance;
		session.stakeItemId = state.stakeItemId != null ? state.stakeItemId : StakeItem.DEFAULT_ID;
		session.turnTicksLeft = state.turnTicksLeft;
		session.dialogAway = state.dialogAway;
		session.phase = state.phase;
		session.primaryOutcome = state.primaryOutcome;

		TableReveal.Mode mode = switch (state.phase) {
			case DEALING -> TableReveal.Mode.DEALING;
			case DEALER_TURN -> TableReveal.Mode.DEALER_PLAY;
			case COLLECTING -> TableReveal.Mode.COLLECTING;
			default -> TableReveal.Mode.IDLE;
		};
		session.reveal.restore(
			state.dealerShown,
			state.holeFaceUp,
			state.dealIndex,
			state.stepCooldown,
			mode,
			false
		);
		session.dirty = true;
		Wallets.setBalance(state.playerId, session.stakeItemId, state.balance);
		return session;
	}

	/** Advance deal / dealer-draw / collect presentation. */
	public boolean tick() {
		if (!reveal.tickCooldown()) {
			return false;
		}
		return switch (reveal.mode()) {
			case COLLECTING -> tickCollect();
			case DEALING -> tickDeal();
			case DEALER_PLAY -> tickDealerPlay();
			case IDLE -> false;
		};
	}

	public boolean tickTurnTimer() {
		if (phase != Phase.PLAYER_TURN && phase != Phase.INSURANCE) {
			return false;
		}
		if (turnTicksLeft <= 0) {
			return false;
		}
		turnTicksLeft--;
		if (turnTicksLeft <= 0) {
			if (phase == Phase.INSURANCE) {
				declineInsurance();
			} else if (dialogAway) {
				forfeitLose();
			} else {
				stand();
			}
			return true;
		}
		return turnTicksLeft % 20 == 0;
	}

	public void hit() {
		if (phase != Phase.PLAYER_TURN) {
			return;
		}
		PlayerHandSeat seat = activeSeat();
		if (seat.finished() || seat.aceSplit()) {
			return;
		}
		seat.hand().add(draw());
		seat.setShown(seat.hand().size());
		markDirty();
		if (seat.hand().isBust()) {
			seat.setFinished(true);
			seat.setOutcome(BlackjackOutcome.PLAYER_BUST);
			advanceAfterHandDone();
		}
	}

	public void stand() {
		if (phase != Phase.PLAYER_TURN) {
			return;
		}
		PlayerHandSeat seat = activeSeat();
		if (seat.finished()) {
			return;
		}
		seat.setFinished(true);
		markDirty();
		advanceAfterHandDone();
	}

	public void doubleDown() {
		if (!canDouble()) {
			return;
		}
		PlayerHandSeat seat = activeSeat();
		long extra = seat.bet();
		balance -= extra;
		held += extra;
		seat.setBet(seat.bet() + extra);
		seat.setDoubled(true);
		persistBalance();
		seat.hand().add(draw());
		seat.setShown(seat.hand().size());
		seat.setFinished(true);
		if (seat.hand().isBust()) {
			seat.setOutcome(BlackjackOutcome.PLAYER_BUST);
		}
		markDirty();
		advanceAfterHandDone();
	}

	public void split() {
		if (!canSplit()) {
			return;
		}
		PlayerHandSeat first = seats.get(0);
		List<Card> cards = first.hand().cards();
		Card a = cards.get(0);
		Card b = cards.get(1);
		boolean aces = a.rank() == Rank.ACE;

		long extra = first.bet();
		balance -= extra;
		held += extra;
		persistBalance();

		first.hand().clear();
		first.hand().add(a);
		first.setFromSplit(true);
		first.setAceSplit(aces);
		first.setShown(1);

		PlayerHandSeat second = new PlayerHandSeat();
		second.setBet(extra);
		second.setFromSplit(true);
		second.setAceSplit(aces);
		second.hand().add(b);
		second.setShown(1);
		seats.add(second);
		splitUsed = true;

		first.hand().add(draw());
		first.setShown(2);
		second.hand().add(draw());
		second.setShown(2);

		if (aces) {
			first.setFinished(true);
			second.setFinished(true);
			markDirty();
			beginDealerResolution();
			return;
		}
		activeHand = 0;
		resetTurnTimer();
		markDirty();
	}

	public void playAgain() {
		if (phase != Phase.RESOLVED) {
			return;
		}
		primaryOutcome = null;
		// Keep holeFaceUp / reveal flags while collecting — never flip the hole mid-collect.
		if (anyCardsShown()) {
			phase = Phase.COLLECTING;
			reveal.startCollect();
		} else {
			beginDeal();
		}
		markDirty();
	}

	public void leave() {
		if (held > 0) {
			balance += held;
			held = 0;
			persistBalance();
		}
		dialogAway = false;
		turnTicksLeft = 0;
		phase = Phase.RESOLVED;
	}

	public void forfeitLose() {
		for (PlayerHandSeat seat : seats) {
			seat.setFinished(true);
			seat.setOutcome(BlackjackOutcome.LOSE);
			seat.setShown(seat.hand().size());
		}
		reveal.idleFullyShown(dealer.size(), true);
		held = 0;
		persistBalance();
		primaryOutcome = BlackjackOutcome.LOSE;
		turnTicksLeft = 0;
		dialogAway = false;
		phase = Phase.RESOLVED;
		markDirty();
	}

	private void resetTurnTimer() {
		turnTicksLeft = turnTicks();
	}

	public long insuranceBet() {
		return insuranceBet;
	}

	public boolean canSurrender() {
		if (!MinecardConfig.surrenderEnabled || phase != Phase.PLAYER_TURN || seats.isEmpty()) {
			return false;
		}
		PlayerHandSeat seat = activeSeat();
		return !seat.finished() && !seat.doubled() && !seat.fromSplit() && seat.hand().size() == 2;
	}

	public boolean canTakeInsurance() {
		if (phase != Phase.INSURANCE || !MinecardConfig.insuranceEnabled) {
			return false;
		}
		long half = unitBet / 2L;
		return half > 0L && balance >= half && insuranceBet == 0L;
	}

	public void takeInsurance() {
		if (!canTakeInsurance()) {
			return;
		}
		long half = unitBet / 2L;
		balance -= half;
		insuranceBet = half;
		held += half;
		persistBalance();
		resolveInsurancePeek();
	}

	public void declineInsurance() {
		if (phase != Phase.INSURANCE) {
			return;
		}
		resolveInsurancePeek();
	}

	public void surrender() {
		if (!canSurrender()) {
			return;
		}
		PlayerHandSeat seat = activeSeat();
		seat.setFinished(true);
		seat.setOutcome(BlackjackOutcome.SURRENDER);
		// Drop unused insurance (none in player turn).
		long payout = settleAmount(seat.bet(), BlackjackOutcome.SURRENDER);
		held = 0;
		balance += payout;
		persistBalance();
		primaryOutcome = BlackjackOutcome.SURRENDER;
		turnTicksLeft = 0;
		reveal.idleFullyShown(dealer.size(), true);
		phase = Phase.RESOLVED;
		markDirty();
		recordHistory(BlackjackOutcome.SURRENDER, seat.bet(), payout);
	}

	private void resolveInsurancePeek() {
		boolean dealerBj = dealer.isBlackjack();
		if (insuranceBet > 0L) {
			if (dealerBj) {
				// Insurance pays 2:1 → return stake + 2× profit = 3× insuranceBet total credit.
				balance += insuranceBet * 3L;
			}
			held -= insuranceBet;
			insuranceBet = 0L;
			persistBalance();
		}
		PlayerHandSeat seat = seats.get(0);
		if (dealerBj || seat.hand().isBlackjack()) {
			reveal.revealHole();
			reveal.idleFullyShown(dealer.size(), true);
			seat.setFinished(true);
			seat.setOutcome(compareHand(seat));
			long payout = settleAmount(seat.bet(), seat.outcome());
			held = 0;
			balance += payout;
			persistBalance();
			primaryOutcome = seat.outcome();
			phase = Phase.RESOLVED;
			markDirty();
			recordHistory(primaryOutcome, seat.bet(), payout);
			return;
		}
		reveal.idleFullyShown(dealer.size(), false);
		phase = Phase.PLAYER_TURN;
		resetTurnTimer();
		markDirty();
	}

	private boolean anyCardsShown() {
		if (reveal.dealerShown() > 0) {
			return true;
		}
		for (PlayerHandSeat seat : seats) {
			if (seat.shown() > 0) {
				return true;
			}
		}
		return false;
	}

	private void advanceAfterHandDone() {
		for (int i = activeHand + 1; i < seats.size(); i++) {
			if (!seats.get(i).finished()) {
				activeHand = i;
				resetTurnTimer();
				markDirty();
				return;
			}
		}
		turnTicksLeft = 0;
		beginDealerResolution();
	}

	/**
	 * After all player hands finish: if everyone busted, settle immediately;
	 * otherwise flip hole and animate dealer hits to 17.
	 */
	private void beginDealerResolution() {
		boolean allBust = true;
		for (PlayerHandSeat seat : seats) {
			if (seat.outcome() != BlackjackOutcome.PLAYER_BUST && !seat.hand().isBust()) {
				allBust = false;
				break;
			}
		}
		if (allBust) {
			reveal.idleFullyShown(dealer.size(), true);
			settleRound();
			return;
		}
		phase = Phase.DEALER_TURN;
		// Show upcard + hole face-up first; hits arrive in tickDealerPlay.
		reveal.startDealerPlay(Math.min(2, dealer.size()));
		markDirty();
	}

	private boolean tickDealerPlay() {
		// One-beat pause after the last hit so the final card is visible before result.
		if (reveal.dealerSettlePause()) {
			reveal.clearDealerSettlePause();
			reveal.idleFullyShown(dealer.size(), true);
			settleRound();
			return true;
		}
		if (reveal.dealerShown() < Math.min(2, dealer.size()) || !reveal.holeFaceUp()) {
			reveal.startDealerPlay(Math.min(2, dealer.size()));
			markDirty();
			return true;
		}
		if (dealerShouldHit()) {
			dealer.add(draw());
			reveal.showDealerHit();
			markDirty();
			return true;
		}
		// Soft/hard 17+: hold the table for one step, then settle (no instant result dump).
		reveal.beginDealerSettlePause();
		markDirty();
		return true;
	}

	private boolean dealerShouldHit() {
		int s = dealer.score();
		if (s < 17) {
			return true;
		}
		return s == 17 && MinecardConfig.dealerHitsSoft17 && dealer.isSoft();
	}

	private void settleRound() {
		long payout = 0;
		BlackjackOutcome firstOutcome = null;
		long totalBet = 0L;
		for (PlayerHandSeat seat : seats) {
			BlackjackOutcome o = seat.outcome();
			if (o == null) {
				o = compareHand(seat);
				seat.setOutcome(o);
			}
			if (firstOutcome == null) {
				firstOutcome = o;
			}
			totalBet += seat.bet();
			payout += settleAmount(seat.bet(), o);
		}
		held = 0;
		balance += payout;
		persistBalance();
		primaryOutcome = firstOutcome;
		phase = Phase.RESOLVED;
		markDirty();
		recordHistory(firstOutcome, totalBet, payout);
	}

	private void recordHistory(BlackjackOutcome outcome, long totalBet, long payout) {
		if (outcome == null) {
			return;
		}
		String handId = UUID.randomUUID().toString();
		String sessionId = "solo:" + playerId;
		long net = payout - totalBet;
		HistoryDb.recordBjSettle(
			playerId,
			handId,
			sessionId,
			outcome.name(),
			totalBet,
			payout,
			net,
			stakeItemId.toString(),
			balance,
			CardJson.of(dealer.cards()),
			CardJson.of(seats.isEmpty() ? List.of() : seats.getFirst().hand().cards())
		);
	}

	private BlackjackOutcome compareHand(PlayerHandSeat seat) {
		BlackjackHand hand = seat.hand();
		if (hand.isBust()) {
			return BlackjackOutcome.PLAYER_BUST;
		}
		boolean natural = !seat.fromSplit() && hand.isBlackjack();
		boolean dealerBj = dealer.isBlackjack();
		if (natural || dealerBj) {
			if (natural && dealerBj) {
				return BlackjackOutcome.PUSH;
			}
			if (natural) {
				return BlackjackOutcome.PLAYER_BLACKJACK;
			}
			return BlackjackOutcome.LOSE;
		}
		if (dealer.isBust()) {
			return BlackjackOutcome.DEALER_BUST;
		}
		int p = hand.score();
		int d = dealer.score();
		if (p > d) {
			return BlackjackOutcome.WIN;
		}
		if (p < d) {
			return BlackjackOutcome.LOSE;
		}
		return BlackjackOutcome.PUSH;
	}

	static long settleAmount(long bet, BlackjackOutcome outcome) {
		return switch (outcome) {
			case PLAYER_BLACKJACK -> bet + bet * 3 / 2;
			case WIN, DEALER_BUST -> bet * 2;
			case PUSH -> bet;
			case SURRENDER -> bet / 2L;
			case LOSE, PLAYER_BUST -> 0L;
		};
	}

	private boolean tickCollect() {
		for (PlayerHandSeat seat : seats) {
			if (seat.shown() > 0) {
				seat.setShown(seat.shown() - 1);
				markDirty();
				return true;
			}
		}
		if (reveal.collectDealerOne()) {
			markDirty();
			return true;
		}
		reveal.finishCollect();
		beginDeal();
		markDirty();
		return true;
	}

	private boolean tickDeal() {
		if (seats.isEmpty()) {
			return false;
		}
		PlayerHandSeat seat = seats.get(0);
		TableReveal.DealPulse pulse = reveal.pulseDeal();
		switch (pulse) {
			case PLAYER_1 -> seat.setShown(1);
			case DEALER_1, DEALER_HOLE -> {
				// dealerShown updated inside TableReveal
			}
			case PLAYER_2 -> seat.setShown(2);
			case DONE -> {
				finishDeal();
				markDirty();
				return true;
			}
		}
		markDirty();
		return true;
	}

	private void finishDeal() {
		PlayerHandSeat seat = seats.get(0);
		seat.setShown(seat.hand().size());
		reveal.setDealerShown(dealer.size());
		reveal.idleFullyShown(dealer.size(), false);
		insuranceBet = 0L;
		primaryOutcome = null;
		boolean dealerAce = !dealer.cards().isEmpty() && dealer.cards().getFirst().rank() == Rank.ACE;
		if (MinecardConfig.insuranceEnabled && dealerAce) {
			phase = Phase.INSURANCE;
			turnTicksLeft = turnTicks();
			markDirty();
			return;
		}
		if (seat.hand().isBlackjack() || dealer.isBlackjack()) {
			reveal.revealHole();
			reveal.idleFullyShown(dealer.size(), true);
			seat.setFinished(true);
			seat.setOutcome(compareHand(seat));
			long payout = settleAmount(seat.bet(), seat.outcome());
			held = 0;
			balance += payout;
			persistBalance();
			primaryOutcome = seat.outcome();
			phase = Phase.RESOLVED;
			recordHistory(primaryOutcome, seat.bet(), payout);
		} else {
			phase = Phase.PLAYER_TURN;
			resetTurnTimer();
		}
	}

	private void beginDeal() {
		dealer.clear();
		seats.clear();
		PlayerHandSeat seat = new PlayerHandSeat();
		seats.add(seat);
		primaryOutcome = null;
		activeHand = 0;
		splitUsed = false;
		turnTicksLeft = 0;
		phase = Phase.DEALING;
		reveal.startDeal();

		stakeItemId = StakeItem.DEFAULT_ID;
		Wallets.ensureStartingBalance(playerId);
		balance = Wallets.balance(playerId, stakeItemId);
		unitBet = Math.min(WalletConstants.DEFAULT_BET, balance);
		held = 0;
		if (unitBet > 0) {
			balance -= unitBet;
			held = unitBet;
			seat.setBet(unitBet);
			persistBalance();
		}

		if (shoe.size() < MinecardConfig.reshuffleBelow) {
			reshuffle();
		}
		// Logical hands pre-filled; TableReveal only uncovers them.
		seat.hand().add(draw());
		dealer.add(draw());
		seat.hand().add(draw());
		dealer.add(draw());
	}

	private void persistBalance() {
		Wallets.setBalance(playerId, stakeItemId, balance);
	}

	/** Inventory source for future escrow top-ups (solo currently balance-only). */
	@SuppressWarnings("unused")
	private static ItemSource noInventory() {
		return ItemSource.empty();
	}

	private Card draw() {
		if (shoe.isEmpty()) {
			reshuffle();
		}
		return shoe.remove(shoe.size() - 1);
	}

	private void reshuffle() {
		shoe.clear();
		for (int d = 0; d < MinecardConfig.soloDecks; d++) {
			shoe.addAll(Card.standard52());
		}
		Collections.shuffle(shoe, random);
	}

	private static int scorePrefix(List<Card> cards, int count) {
		BlackjackHand hand = new BlackjackHand();
		for (int i = 0; i < Math.min(count, cards.size()); i++) {
			hand.add(cards.get(i));
		}
		return hand.score();
	}
}
