package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Solo blackjack with deal/collect animation, double/split, and demo stakes.
 */
public final class BlackjackSession {
	public enum Phase {
		COLLECTING,
		DEALING,
		PLAYER_TURN,
		RESOLVED
	}

	/** Ticks between each dealt / collected card. */
	public static final int STEP_TICKS = 8;
	/** Player turn length (ke-hoach default 25s). */
	public static final int TURN_SECONDS = 25;
	public static final int TURN_TICKS = TURN_SECONDS * 20;

	private final UUID playerId;
	private final Random random;
	private final List<Card> shoe = new ArrayList<>();
	private final BlackjackHand dealer = new BlackjackHand();
	private final List<PlayerHandSeat> seats = new ArrayList<>();

	private int dealerShown;
	private boolean holeFaceUp;
	private int activeHand;
	private boolean splitUsed;

	private Phase phase = Phase.DEALING;
	private BlackjackOutcome primaryOutcome;
	private int stepCooldown;
	private int dealIndex;
	private boolean dirty = true;

	private long unitBet = DemoBank.DEFAULT_BET;
	private long held;
	private long balance;
	private Identifier stakeItemId = StakeItem.DEFAULT_ID;
	private int turnTicksLeft;
	/** Dialog closed via emergency / ESC — round state kept, timer keeps running. */
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
		this.balance = DemoBank.balance(playerId, stakeItemId);
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

	/** First seat hand (tests / single-hand helpers). */
	public BlackjackHand playerHand() {
		return seats.isEmpty() ? new BlackjackHand() : seats.get(0).hand();
	}

	public boolean dealerHoleHidden() {
		return !holeFaceUp && dealerShown >= 2;
	}

	public List<Card> visibleDealerCards() {
		List<Card> all = dealer.cards();
		return all.subList(0, Math.min(dealerShown, all.size()));
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
		return scorePrefix(dealer.cards(), dealerShown);
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

	/** Hide dialog, keep round; ESC / emergency button. */
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
			.dealerShown(dealerShown)
			.holeFaceUp(holeFaceUp)
			.activeHand(activeHand)
			.splitUsed(splitUsed)
			.unitBet(unitBet)
			.held(held)
			.balance(balance)
			.stakeItemId(stakeItemId)
			.turnTicksLeft(turnTicksLeft)
			.dialogAway(dialogAway)
			.dealIndex(dealIndex)
			.stepCooldown(stepCooldown)
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

	/** Restore a previously captured round (reconnect / SavedData). */
	public static BlackjackSession fromState(BlackjackRoundState state) {
		BlackjackSession session = new BlackjackSession(state.playerId, 0L, false);
		session.shoe.clear();
		session.shoe.addAll(state.shoe);
		session.dealer.clear();
		state.dealerCards.forEach(session.dealer::add);
		session.dealerShown = state.dealerShown;
		session.holeFaceUp = state.holeFaceUp;
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
		session.dealIndex = state.dealIndex;
		session.stepCooldown = state.stepCooldown;
		session.phase = state.phase;
		session.primaryOutcome = state.primaryOutcome;
		session.dirty = true;
		DemoBank.setBalance(state.playerId, session.stakeItemId, state.balance);
		return session;
	}

	/**
	 * Advance deal/collect animation. @return true if the dialog should refresh.
	 */
	public boolean tick() {
		if (phase != Phase.COLLECTING && phase != Phase.DEALING) {
			return false;
		}
		if (stepCooldown > 0) {
			stepCooldown--;
			return false;
		}
		stepCooldown = STEP_TICKS;
		if (phase == Phase.COLLECTING) {
			return tickCollect();
		}
		return tickDeal();
	}

	/**
	 * Countdown during {@link Phase#PLAYER_TURN}. Continues while dialog is away.
	 * @return true if UI should refresh (second boundary or timeout resolve)
	 */
	public boolean tickTurnTimer() {
		if (phase != Phase.PLAYER_TURN) {
			return false;
		}
		if (turnTicksLeft <= 0) {
			return false;
		}
		turnTicksLeft--;
		if (turnTicksLeft <= 0) {
			if (dialogAway) {
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
			resolveDealerAndSettle();
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
		holeFaceUp = false;
		if (anyCardsShown()) {
			phase = Phase.COLLECTING;
			stepCooldown = 0;
		} else {
			beginDeal();
		}
		markDirty();
	}

	/** Leave table: refund held stake into balance (demo). */
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

	/** Emergency timeout / forfeit: lose all stakes, no refund. */
	public void forfeitLose() {
		for (PlayerHandSeat seat : seats) {
			seat.setFinished(true);
			seat.setOutcome(BlackjackOutcome.LOSE);
			seat.setShown(seat.hand().size());
		}
		holeFaceUp = true;
		dealerShown = dealer.size();
		held = 0;
		persistBalance();
		primaryOutcome = BlackjackOutcome.LOSE;
		turnTicksLeft = 0;
		dialogAway = false;
		phase = Phase.RESOLVED;
		markDirty();
	}

	private void resetTurnTimer() {
		turnTicksLeft = TURN_TICKS;
	}

	private boolean anyCardsShown() {
		if (dealerShown > 0) {
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
		resolveDealerAndSettle();
	}

	private void resolveDealerAndSettle() {
		boolean allBust = true;
		for (PlayerHandSeat seat : seats) {
			if (seat.outcome() != BlackjackOutcome.PLAYER_BUST && !seat.hand().isBust()) {
				allBust = false;
				break;
			}
		}
		holeFaceUp = true;
		dealerShown = dealer.size();
		if (!allBust) {
			playDealer();
			dealerShown = dealer.size();
		}
		long payout = 0;
		BlackjackOutcome firstOutcome = null;
		for (PlayerHandSeat seat : seats) {
			BlackjackOutcome o = seat.outcome();
			if (o == null) {
				o = compareHand(seat);
				seat.setOutcome(o);
			}
			if (firstOutcome == null) {
				firstOutcome = o;
			}
			payout += settleAmount(seat.bet(), o);
		}
		held = 0;
		balance += payout;
		persistBalance();
		primaryOutcome = firstOutcome;
		phase = Phase.RESOLVED;
		markDirty();
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
		if (dealerShown > 0) {
			dealerShown--;
			markDirty();
			return true;
		}
		beginDeal();
		markDirty();
		return true;
	}

	private boolean tickDeal() {
		PlayerHandSeat seat = seats.get(0);
		switch (dealIndex) {
			case 0 -> seat.setShown(1);
			case 1 -> dealerShown = 1;
			case 2 -> seat.setShown(2);
			case 3 -> {
				dealerShown = 2;
				holeFaceUp = false;
			}
			default -> {
				finishDeal();
				markDirty();
				return true;
			}
		}
		dealIndex++;
		markDirty();
		return true;
	}

	private void finishDeal() {
		PlayerHandSeat seat = seats.get(0);
		seat.setShown(seat.hand().size());
		dealerShown = dealer.size();
		if (seat.hand().isBlackjack() || dealer.isBlackjack()) {
			holeFaceUp = true;
			seat.setFinished(true);
			seat.setOutcome(compareHand(seat));
			long payout = settleAmount(seat.bet(), seat.outcome());
			held = 0;
			balance += payout;
			persistBalance();
			primaryOutcome = seat.outcome();
			phase = Phase.RESOLVED;
		} else {
			phase = Phase.PLAYER_TURN;
			primaryOutcome = null;
			resetTurnTimer();
		}
	}

	private void beginDeal() {
		dealer.clear();
		seats.clear();
		PlayerHandSeat seat = new PlayerHandSeat();
		seats.add(seat);
		dealerShown = 0;
		holeFaceUp = false;
		primaryOutcome = null;
		dealIndex = 0;
		stepCooldown = 0;
		activeHand = 0;
		splitUsed = false;
		turnTicksLeft = 0;
		phase = Phase.DEALING;

		stakeItemId = StakeItem.DEFAULT_ID;
		balance = DemoBank.balance(playerId, stakeItemId);
		unitBet = Math.min(DemoBank.DEFAULT_BET, balance);
		held = 0;
		if (unitBet > 0) {
			balance -= unitBet;
			held = unitBet;
			seat.setBet(unitBet);
			persistBalance();
		}

		if (shoe.size() < 15) {
			reshuffle();
		}
		seat.hand().add(draw());
		dealer.add(draw());
		seat.hand().add(draw());
		dealer.add(draw());
	}

	private void playDealer() {
		while (dealer.score() < 17) {
			dealer.add(draw());
		}
	}

	private void persistBalance() {
		DemoBank.setBalance(playerId, stakeItemId, balance);
	}

	private Card draw() {
		if (shoe.isEmpty()) {
			reshuffle();
		}
		return shoe.remove(shoe.size() - 1);
	}

	private void reshuffle() {
		shoe.clear();
		shoe.addAll(Card.standard52());
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
