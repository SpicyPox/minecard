package com.spicypox.minecard.game.poker;

import com.spicypox.minecard.card.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * No-Limit Hold'em hand engine (MVP: single pot, no multi side-pots).
 */
public final class TablePoker {
	public enum Phase {
		PREFLOP,
		FLOP,
		TURN,
		RIVER,
		SHOWDOWN,
		HAND_OVER
	}

	private final String roomId;
	private final PokerRules rules;
	private final List<PokerSeat> seats;
	private final List<Card> deck = new ArrayList<>(52);
	private final List<Card> board = new ArrayList<>(5);
	private final Random random;
	private Phase phase = Phase.HAND_OVER;
	private long pot;
	private long currentBet;
	private long lastRaiseSize;
	private int buttonIndex;
	private int actingIndex = -1;
	private List<UUID> winners = List.of();

	public TablePoker(String roomId, PokerRules rules, List<PokerSeat> seats, long seed) {
		this.roomId = roomId;
		this.rules = rules.sanitized();
		this.seats = new ArrayList<>(seats);
		this.random = new Random(seed);
		this.buttonIndex = 0;
	}

	public TablePoker(String roomId, PokerRules rules, List<PokerSeat> seats) {
		this(roomId, rules, seats, System.nanoTime());
	}

	public String roomId() {
		return roomId;
	}

	public PokerRules rules() {
		return rules;
	}

	public List<PokerSeat> seats() {
		return List.copyOf(seats);
	}

	public Optional<PokerSeat> seat(UUID id) {
		return seats.stream().filter(s -> s.playerId().equals(id)).findFirst();
	}

	public List<Card> board() {
		return List.copyOf(board);
	}

	public Phase phase() {
		return phase;
	}

	public long pot() {
		return pot;
	}

	public long currentBet() {
		return currentBet;
	}

	public int buttonIndex() {
		return buttonIndex;
	}

	public UUID actingPlayerId() {
		if (actingIndex < 0 || actingIndex >= seats.size()) {
			return null;
		}
		return seats.get(actingIndex).playerId();
	}

	public List<UUID> winners() {
		return winners;
	}

	public boolean inHand() {
		return phase != Phase.HAND_OVER;
	}

	public boolean bettingOpen() {
		return phase == Phase.PREFLOP || phase == Phase.FLOP
			|| phase == Phase.TURN || phase == Phase.RIVER;
	}

	/** Start a new hand. Needs ≥2 seats with chips. */
	public void startHand() {
		long live = seats.stream().filter(s -> s.stack() > 0L).count();
		if (live < 2) {
			phase = Phase.HAND_OVER;
			return;
		}
		for (PokerSeat s : seats) {
			s.resetForHand();
		}
		// Sit-outs / busted: out for this hand (do not block betting rounds).
		for (PokerSeat s : seats) {
			if (s.stack() <= 0L) {
				s.fold();
			}
		}
		board.clear();
		pot = 0L;
		winners = List.of();
		shuffleDeck();
		phase = Phase.PREFLOP;
		lastRaiseSize = rules.bigBlind();
		currentBet = 0L;

		int liveCount = (int) seats.stream().filter(s -> !s.folded()).count();
		int sb = nextWithStack(buttonIndex + (liveCount == 2 ? 0 : 1));
		int bb = nextWithStack(sb + 1);
		postBlind(seats.get(sb), rules.smallBlind());
		postBlind(seats.get(bb), rules.bigBlind());
		currentBet = Math.max(seats.get(sb).streetCommitted(), seats.get(bb).streetCommitted());

		for (PokerSeat s : seats) {
			if (!s.folded()) {
				s.dealHole(draw(), draw());
			}
		}

		actingIndex = nextActor(bb);
		if (actingIndex < 0 || !playersCanAct()) {
			runOutAndShowdown();
		}
	}

	private int nextWithStack(int from) {
		for (int i = 0; i < seats.size(); i++) {
			int idx = Math.floorMod(from + i, seats.size());
			if (seats.get(idx).stack() > 0L) {
				return idx;
			}
		}
		return Math.floorMod(from, seats.size());
	}

	public void advanceButton() {
		buttonIndex = nextWithStack(buttonIndex + 1);
	}

	public long toCall(UUID playerId) {
		return seat(playerId).map(s -> Math.max(0L, currentBet - s.streetCommitted())).orElse(0L);
	}

	/** Minimum total street commitment for a legal raise (raise-to). */
	public long minRaiseTo(UUID playerId) {
		long minTo = currentBet + lastRaiseSize;
		return seat(playerId).map(s -> Math.min(s.streetCommitted() + s.stack(), Math.max(minTo, currentBet + 1))).orElse(0L);
	}

	public long maxRaiseTo(UUID playerId) {
		return seat(playerId).map(s -> s.streetCommitted() + s.stack()).orElse(0L);
	}

	public boolean fold(UUID actor) {
		if (!isActing(actor)) {
			return false;
		}
		PokerSeat s = seats.get(actingIndex);
		s.fold();
		if (countActive() == 1) {
			awardFoldWin();
			return true;
		}
		advanceAction();
		return true;
	}

	public boolean checkOrCall(UUID actor) {
		if (!isActing(actor)) {
			return false;
		}
		PokerSeat s = seats.get(actingIndex);
		long need = currentBet - s.streetCommitted();
		if (need <= 0L) {
			s.setStatus(PokerActionStatus.CHECK);
			s.setActedThisStreet(true);
		} else {
			long paid = s.commit(need);
			pot += paid;
			s.setStatus(s.allIn() ? PokerActionStatus.ALL_IN : PokerActionStatus.CALL);
			s.setActedThisStreet(true);
		}
		advanceAction();
		return true;
	}

	/**
	 * Raise or bet to {@code raiseTo} street total. Short all-in allowed.
	 */
	public boolean raiseTo(UUID actor, long raiseTo) {
		if (!isActing(actor)) {
			return false;
		}
		PokerSeat s = seats.get(actingIndex);
		long max = s.streetCommitted() + s.stack();
		long target = Math.min(Math.max(0L, raiseTo), max);
		long need = target - s.streetCommitted();
		if (need <= 0L) {
			return checkOrCall(actor);
		}
		boolean isAllIn = target >= max;
		long minTo = currentBet + lastRaiseSize;
		if (!isAllIn && target < minTo && target < max) {
			return false;
		}
		if (!isAllIn && target <= currentBet) {
			return false;
		}
		boolean wasBet = currentBet == 0L;
		long paid = s.commit(need);
		pot += paid;
		long newBet = s.streetCommitted();
		if (newBet > currentBet) {
			long raiseBy = newBet - currentBet;
			if (raiseBy >= lastRaiseSize) {
				lastRaiseSize = raiseBy;
			}
			currentBet = newBet;
			for (PokerSeat o : seats) {
				if (o != s && !o.folded() && !o.allIn()) {
					o.setActedThisStreet(false);
				}
			}
			s.setStatus(s.allIn() ? PokerActionStatus.ALL_IN
				: (wasBet ? PokerActionStatus.BET : PokerActionStatus.RAISE));
		} else {
			s.setStatus(s.allIn() ? PokerActionStatus.ALL_IN : PokerActionStatus.CALL);
		}
		s.setActedThisStreet(true);
		advanceAction();
		return true;
	}

	public boolean allIn(UUID actor) {
		return seat(actor).map(s -> raiseTo(actor, s.streetCommitted() + s.stack())).orElse(false);
	}

	/**
	 * Room closed / admin end mid-hand: return every seat's committed chips to their stack.
	 * Does not award a winner (unlike fold-win).
	 */
	public void abortHand() {
		if (phase == Phase.HAND_OVER) {
			return;
		}
		for (PokerSeat s : seats) {
			s.returnCommitted();
		}
		pot = 0L;
		currentBet = 0L;
		actingIndex = -1;
		winners = List.of();
		phase = Phase.HAND_OVER;
	}

	/** Leave / disconnect mid-hand. */
	public void forceFold(UUID playerId) {
		seat(playerId).ifPresent(s -> {
			if (s.folded() || phase == Phase.HAND_OVER) {
				return;
			}
			boolean wasActing = playerId.equals(actingPlayerId());
			s.fold();
			if (countActive() == 1) {
				awardFoldWin();
			} else if (wasActing) {
				advanceAction();
			}
		});
	}

	public boolean raisePreset(UUID actor, String preset) {
		long minTo = minRaiseTo(actor);
		long maxTo = maxRaiseTo(actor);
		long to = switch (preset) {
			case "min" -> minTo;
			case "half_pot" -> Math.min(maxTo, Math.max(minTo, currentBet + Math.max(1L, pot / 2L)));
			case "pot" -> Math.min(maxTo, Math.max(minTo, currentBet + Math.max(1L, pot)));
			default -> -1L;
		};
		if (to < 0L) {
			return false;
		}
		return raiseTo(actor, to);
	}

	private void postBlind(PokerSeat s, long amount) {
		long paid = s.commit(amount);
		pot += paid;
		s.setStatus(s.allIn() ? PokerActionStatus.ALL_IN : PokerActionStatus.BLIND);
		s.setActedThisStreet(false);
	}

	private boolean isActing(UUID actor) {
		return bettingOpen() && actor != null && actor.equals(actingPlayerId());
	}

	private void advanceAction() {
		if (countActive() == 1) {
			awardFoldWin();
			return;
		}
		if (streetComplete()) {
			nextStreet();
			return;
		}
		actingIndex = nextActor(actingIndex);
		if (actingIndex < 0) {
			nextStreet();
		}
	}

	private boolean streetComplete() {
		long bet = currentBet;
		for (PokerSeat s : seats) {
			if (s.folded() || s.allIn()) {
				continue;
			}
			if (!s.actedThisStreet()) {
				return false;
			}
			if (s.streetCommitted() != bet) {
				return false;
			}
		}
		return true;
	}

	private boolean playersCanAct() {
		int n = 0;
		for (PokerSeat s : seats) {
			if (!s.folded() && !s.allIn() && s.stack() > 0L) {
				n++;
			}
		}
		return n >= 2 || (n == 1 && someoneNeedsToCall());
	}

	private boolean someoneNeedsToCall() {
		for (PokerSeat s : seats) {
			if (!s.folded() && !s.allIn() && s.streetCommitted() < currentBet) {
				return true;
			}
		}
		return false;
	}

	private void nextStreet() {
		for (PokerSeat s : seats) {
			s.resetStreet();
		}
		currentBet = 0L;
		lastRaiseSize = rules.bigBlind();
		actingIndex = -1;

		switch (phase) {
			case PREFLOP -> {
				burn();
				board.add(draw());
				board.add(draw());
				board.add(draw());
				phase = Phase.FLOP;
			}
			case FLOP -> {
				burn();
				board.add(draw());
				phase = Phase.TURN;
			}
			case TURN -> {
				burn();
				board.add(draw());
				phase = Phase.RIVER;
			}
			case RIVER -> {
				showdown();
				return;
			}
			default -> {
				return;
			}
		}

		if (countActive() == 1) {
			awardFoldWin();
			return;
		}
		if (!playersCanAct()) {
			runOutAndShowdown();
			return;
		}
		actingIndex = nextActor(buttonIndex);
		if (actingIndex < 0) {
			runOutAndShowdown();
		}
	}

	private void runOutAndShowdown() {
		while (board.size() < 5) {
			if (board.isEmpty()) {
				burn();
				board.add(draw());
				board.add(draw());
				board.add(draw());
				phase = Phase.FLOP;
			} else if (board.size() == 3) {
				burn();
				board.add(draw());
				phase = Phase.TURN;
			} else if (board.size() == 4) {
				burn();
				board.add(draw());
				phase = Phase.RIVER;
			}
		}
		showdown();
	}

	private void showdown() {
		phase = Phase.SHOWDOWN;
		actingIndex = -1;
		List<PokerSeat> contenders = seats.stream().filter(s -> !s.folded()).toList();
		if (contenders.isEmpty()) {
			phase = Phase.HAND_OVER;
			return;
		}
		if (contenders.size() == 1) {
			award(List.of(contenders.getFirst()));
			return;
		}
		PokerHandRank best = null;
		List<PokerSeat> tops = new ArrayList<>();
		for (PokerSeat s : contenders) {
			if (s.hole().size() < 2 || board.size() < 3) {
				continue;
			}
			PokerHandRank r = PokerHandEval.bestOf(s.hole(), board);
			if (best == null || r.compareTo(best) > 0) {
				best = r;
				tops.clear();
				tops.add(s);
			} else if (r.compareTo(best) == 0) {
				tops.add(s);
			}
		}
		if (tops.isEmpty()) {
			awardFoldWin();
			return;
		}
		award(tops);
	}

	private void awardFoldWin() {
		PokerSeat winner = seats.stream().filter(s -> !s.folded()).findFirst().orElse(null);
		if (winner == null) {
			phase = Phase.HAND_OVER;
			return;
		}
		award(List.of(winner));
	}

	private void award(List<PokerSeat> tops) {
		long share = pot / tops.size();
		long rem = pot % tops.size();
		// Odd chips to earliest seat after button
		List<PokerSeat> order = new ArrayList<>(tops);
		order.sort(Comparator.comparingInt(s -> seatDistanceFromButton(indexOf(s))));
		for (int i = 0; i < order.size(); i++) {
			PokerSeat s = order.get(i);
			long add = share + (i < rem ? 1L : 0L);
			s.credit(add);
			s.setStatus(PokerActionStatus.WIN);
		}
		for (PokerSeat s : seats) {
			if (!tops.contains(s) && !s.folded()) {
				s.setStatus(PokerActionStatus.LOSE);
			}
		}
		winners = tops.stream().map(PokerSeat::playerId).toList();
		pot = 0L;
		phase = Phase.HAND_OVER;
		actingIndex = -1;
	}

	private int seatDistanceFromButton(int idx) {
		if (idx < 0) {
			return 99;
		}
		return (idx - buttonIndex + seats.size()) % seats.size();
	}

	private int indexOf(PokerSeat s) {
		return seats.indexOf(s);
	}

	private int countActive() {
		int n = 0;
		for (PokerSeat s : seats) {
			if (!s.folded()) {
				n++;
			}
		}
		return n;
	}

	private int nextActor(int afterIndex) {
		for (int i = 1; i <= seats.size(); i++) {
			int idx = Math.floorMod(afterIndex + i, seats.size());
			PokerSeat s = seats.get(idx);
			if (!s.folded() && !s.allIn() && s.stack() > 0L) {
				return idx;
			}
		}
		return -1;
	}

	private void shuffleDeck() {
		deck.clear();
		deck.addAll(Card.standard52());
		Collections.shuffle(deck, random);
	}

	private Card draw() {
		return deck.removeLast();
	}

	private void burn() {
		if (!deck.isEmpty()) {
			deck.removeLast();
		}
	}
}
