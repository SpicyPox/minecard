package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.history.CardJson;
import com.spicypox.minecard.history.HistoryDb;
import com.spicypox.minecard.room.BjRoomRules;
import com.spicypox.minecard.wallet.Escrow;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Shared multiplayer blackjack table: one dealer, many players, house pays from host wallet.
 */
public final class TableBlackjack {
	public enum Phase {
		DEALING,
		INSURANCE,
		PLAYER_TURN,
		DEALER_TURN,
		RESOLVED
	}

	private final String tableId;
	private final UUID hostId;
	private final Identifier stakeItem;
	private final BjRoomRules rules;
	private final Random random = new Random();
	private final List<Card> shoe = new ArrayList<>();
	private final BlackjackHand dealer = new BlackjackHand();
	private final List<TablePlayer> players = new ArrayList<>();
	private final TableReveal reveal = new TableReveal();

	private Phase phase = Phase.DEALING;
	private int activePlayer;
	private boolean dirty = true;
	private int turnTicksLeft;

	public TableBlackjack(String tableId, UUID hostId, Identifier stakeItem, List<TablePlayer> seated) {
		this(tableId, hostId, stakeItem, seated, BjRoomRules.defaults());
	}

	public TableBlackjack(
		String tableId,
		UUID hostId,
		Identifier stakeItem,
		List<TablePlayer> seated,
		BjRoomRules rules
	) {
		this.tableId = tableId;
		this.hostId = hostId;
		this.stakeItem = stakeItem;
		this.rules = rules != null ? rules.sanitized() : BjRoomRules.defaults();
		this.players.addAll(seated);
		reshuffle();
		beginDeal();
	}

	/** 0 = no turn countdown (host/players act freely). */
	private int turnTicks() {
		int sec = rules.turnSeconds();
		if (sec <= 0) {
			return 0;
		}
		return sec * 20;
	}

	public String tableId() {
		return tableId;
	}

	public UUID hostId() {
		return hostId;
	}

	public Identifier stakeItem() {
		return stakeItem;
	}

	public Phase phase() {
		return phase;
	}

	public TableReveal reveal() {
		return reveal;
	}

	public List<TablePlayer> players() {
		return List.copyOf(players);
	}

	public Optional<TablePlayer> player(UUID id) {
		return players.stream().filter(p -> p.playerId().equals(id)).findFirst();
	}

	public int activePlayerIndex() {
		return activePlayer;
	}

	public UUID activePlayerId() {
		if (activePlayer < 0 || activePlayer >= players.size()) {
			return null;
		}
		return players.get(activePlayer).playerId();
	}

	public boolean consumeDirty() {
		boolean d = dirty;
		dirty = false;
		return d;
	}

	private void markDirty() {
		dirty = true;
	}

	public List<Card> visibleDealerCards() {
		List<Card> all = dealer.cards();
		return all.subList(0, Math.min(reveal.dealerShown(), all.size()));
	}

	public boolean[] dealerFaceUpFlags() {
		return reveal.dealerFaceUpFlags(visibleDealerCards().size());
	}

	public boolean dealerHoleHidden() {
		return reveal.dealerHoleHidden();
	}

	public int visibleDealerScore() {
		return scorePrefix(dealer.cards(), reveal.dealerShown());
	}

	public int turnSecondsLeft() {
		return Math.max(0, (turnTicksLeft + 19) / 20);
	}

	public boolean tick() {
		if (!reveal.tickCooldown()) {
			return false;
		}
		return switch (reveal.mode()) {
			case DEALING -> tickDeal();
			case DEALER_PLAY -> tickDealerPlay();
			default -> false;
		};
	}

	public boolean tickTurnTimer() {
		// Countdown disabled when turnSeconds <= 0.
		if (turnTicksLeft <= 0) {
			return false;
		}
		if (phase != Phase.PLAYER_TURN && phase != Phase.INSURANCE && phase != Phase.DEALER_TURN) {
			return false;
		}
		turnTicksLeft--;
		if (turnTicksLeft <= 0) {
			if (phase == Phase.INSURANCE) {
				declineInsurance(activePlayerId());
			} else if (phase == Phase.DEALER_TURN) {
				if (dealerCanHit()) {
					dealerHit(hostId);
				} else {
					dealerStand(hostId);
				}
			} else {
				stand(activePlayerId());
			}
			return true;
		}
		return turnTicksLeft % 20 == 0;
	}

	/** Host may hit while under 21 (including soft/hard 17). */
	public boolean dealerCanHit() {
		return phase == Phase.DEALER_TURN && !dealer.isBust() && dealer.score() < 21;
	}

	/** Host may stand any time on dealer turn (after players finished). */
	public boolean dealerCanStand() {
		return phase == Phase.DEALER_TURN && !dealer.isBust();
	}

	/** Host Hit — flips hole on first action, then draws. */
	public void dealerHit(UUID actor) {
		if (phase != Phase.DEALER_TURN || !hostId.equals(actor) || !dealerCanHit()) {
			return;
		}
		ensureDealerHoleUp();
		dealer.add(draw());
		reveal.idleFullyShown(dealer.size(), true);
		if (dealer.isBust()) {
			settle();
			return;
		}
		turnTicksLeft = turnTicks();
		markDirty();
	}

	/** Host Stand — flips hole on first action, then settles. */
	public void dealerStand(UUID actor) {
		if (phase != Phase.DEALER_TURN || !hostId.equals(actor) || !dealerCanStand()) {
			return;
		}
		ensureDealerHoleUp();
		reveal.idleFullyShown(dealer.size(), true);
		settle();
	}

	private void ensureDealerHoleUp() {
		if (!reveal.holeFaceUp()) {
			reveal.revealHole();
			markDirty();
		}
	}

	public void takeInsurance(UUID playerId) {
		if (phase != Phase.INSURANCE || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		if (p.insuranceDecided()) {
			return;
		}
		long half = p.seat().bet() / 2L;
		long bal = Wallets.balance(playerId, stakeItem);
		if (half > 0L && bal >= half) {
			Wallets.setBalance(playerId, stakeItem, bal - half);
			p.setInsuranceBet(half);
		}
		p.setInsuranceDecided(true);
		advanceInsurance();
	}

	public void declineInsurance(UUID playerId) {
		if (phase != Phase.INSURANCE || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		p.setInsuranceDecided(true);
		advanceInsurance();
	}

	private void advanceInsurance() {
		int next = -1;
		for (int i = activePlayer + 1; i < players.size(); i++) {
			if (!players.get(i).seat().finished()) {
				next = i;
				break;
			}
		}
		if (next >= 0) {
			activePlayer = next;
			turnTicksLeft = turnTicks();
			markDirty();
			return;
		}
		finishInsurancePeek();
	}

	private void finishInsurancePeek() {
		boolean dealerBj = dealer.isBlackjack();
		long hostBal = Wallets.balance(hostId, stakeItem);
		for (TablePlayer p : players) {
			if (p.insuranceBet() <= 0L) {
				continue;
			}
			if (dealerBj) {
				long pay = p.insuranceBet() * 3L;
				long give = Math.min(hostBal, pay);
				hostBal -= give;
				Wallets.setBalance(hostId, stakeItem, hostBal);
				Escrow.creditWallet(Wallets.get(), p.playerId(), stakeItem, give);
			}
			// else insurance lost (already deducted)
			p.setInsuranceBet(0L);
		}
		if (dealerBj) {
			reveal.idleFullyShown(dealer.size(), true);
			for (TablePlayer p : players) {
				if (!p.seat().finished()) {
					p.seat().setFinished(true);
				}
				if (p.seat().outcome() == null) {
					p.seat().setOutcome(compare(p.seat()));
				}
			}
			settle();
			return;
		}
		phase = Phase.PLAYER_TURN;
		activePlayer = firstUnfinished();
		turnTicksLeft = turnTicks();
		markDirty();
	}

	public void hit(UUID playerId) {
		if (phase != Phase.PLAYER_TURN || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		PlayerHandSeat seat = p.seat();
		if (seat.finished()) {
			return;
		}
		seat.hand().add(draw());
		seat.setShown(seat.hand().size());
		if (seat.hand().isBust()) {
			seat.setOutcome(BlackjackOutcome.PLAYER_BUST);
			seat.setFinished(true);
			advancePlayer();
		}
		markDirty();
	}

	public void stand(UUID playerId) {
		if (phase != Phase.PLAYER_TURN || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		p.seat().setFinished(true);
		markDirty();
		advancePlayer();
	}

	public void doubleDown(UUID playerId) {
		if (phase != Phase.PLAYER_TURN || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		PlayerHandSeat seat = p.seat();
		if (seat.hand().size() != 2 || seat.doubled() || seat.finished()) {
			return;
		}
		long extra = seat.bet();
		long bal = Wallets.balance(playerId, stakeItem);
		if (bal < extra) {
			return;
		}
		Wallets.setBalance(playerId, stakeItem, bal - extra);
		seat.setBet(seat.bet() + extra);
		seat.setDoubled(true);
		seat.hand().add(draw());
		seat.setShown(seat.hand().size());
		seat.setFinished(true);
		if (seat.hand().isBust()) {
			seat.setOutcome(BlackjackOutcome.PLAYER_BUST);
		}
		markDirty();
		advancePlayer();
	}

	/** One split per player seat (second hand played immediately after first finishes). */
	public void split(UUID playerId) {
		if (phase != Phase.PLAYER_TURN || !playerId.equals(activePlayerId())) {
			return;
		}
		TablePlayer p = players.get(activePlayer);
		PlayerHandSeat seat = p.seat();
		if (seat.fromSplit() || seat.hand().size() != 2 || seat.finished()) {
			return;
		}
		Card a = seat.hand().cards().get(0);
		Card b = seat.hand().cards().get(1);
		if (a.rank() != b.rank()) {
			return;
		}
		long extra = seat.bet();
		long bal = Wallets.balance(playerId, stakeItem);
		if (bal < extra) {
			return;
		}
		Wallets.setBalance(playerId, stakeItem, bal - extra);
		seat.hand().clear();
		seat.hand().add(a);
		seat.hand().add(draw());
		seat.setShown(2);
		seat.setFromSplit(true);
		boolean aces = a.rank() == com.spicypox.minecard.card.Rank.ACE;
		if (aces) {
			seat.setAceSplit(true);
			seat.setFinished(true);
		}
		TablePlayer second = new TablePlayer(playerId, p.name() + "#2", extra);
		second.seat().hand().add(b);
		second.seat().hand().add(draw());
		second.seat().setShown(2);
		second.seat().setFromSplit(true);
		second.seat().setBet(extra);
		if (aces) {
			second.seat().setAceSplit(true);
			second.seat().setFinished(true);
		}
		players.add(activePlayer + 1, second);
		if (aces) {
			advancePlayer();
		}
		markDirty();
	}

	private void beginDeal() {
		dealer.clear();
		for (TablePlayer p : players) {
			p.seat().hand().clear();
			p.seat().setFinished(false);
			p.seat().setOutcome(null);
			p.seat().setDoubled(false);
			p.seat().setShown(0);
		}
		activePlayer = 0;
		phase = Phase.DEALING;
		reveal.startDeal();
		if (shoe.size() < 15) {
			reshuffle();
		}
		for (TablePlayer p : players) {
			p.seat().hand().add(draw());
		}
		dealer.add(draw());
		for (TablePlayer p : players) {
			p.seat().hand().add(draw());
		}
		dealer.add(draw());
		markDirty();
	}

	private boolean tickDeal() {
		// Simplified: uncover all after a few pulses, then enter play.
		reveal.setDealerShown(Math.min(2, dealer.size()));
		for (TablePlayer p : players) {
			p.seat().setShown(p.seat().hand().size());
		}
		reveal.idleFullyShown(Math.min(2, dealer.size()), false);
		boolean anyPlay = false;
		for (TablePlayer p : players) {
			p.setInsuranceDecided(false);
			p.setInsuranceBet(0L);
			if (p.seat().hand().isBlackjack()) {
				p.seat().setFinished(true);
			} else {
				anyPlay = true;
			}
		}
		boolean dealerAce = !dealer.cards().isEmpty()
			&& dealer.cards().getFirst().rank() == com.spicypox.minecard.card.Rank.ACE;
		if (rules.insuranceEnabled() && dealerAce && anyPlay) {
			phase = Phase.INSURANCE;
			activePlayer = firstUnfinished();
			turnTicksLeft = turnTicks();
			markDirty();
			return true;
		}
		if (dealer.isBlackjack() || !anyPlay) {
			reveal.idleFullyShown(dealer.size(), true);
			settle();
			return true;
		}
		phase = Phase.PLAYER_TURN;
		activePlayer = firstUnfinished();
		turnTicksLeft = turnTicks();
		markDirty();
		return true;
	}

	private void advancePlayer() {
		int next = firstUnfinishedFrom(activePlayer + 1);
		if (next < 0) {
			beginDealer();
			return;
		}
		activePlayer = next;
		turnTicksLeft = turnTicks();
		markDirty();
	}

	private int firstUnfinished() {
		return firstUnfinishedFrom(0);
	}

	private int firstUnfinishedFrom(int start) {
		for (int i = start; i < players.size(); i++) {
			if (!players.get(i).seat().finished()) {
				return i;
			}
		}
		return -1;
	}

	private void beginDealer() {
		boolean allBust = players.stream().allMatch(p ->
			p.seat().outcome() == BlackjackOutcome.PLAYER_BUST || p.seat().hand().isBust()
		);
		if (allBust) {
			reveal.idleFullyShown(dealer.size(), true);
			settle();
			return;
		}
		phase = Phase.DEALER_TURN;
		// Keep hole face-down until host presses Hit/Stand.
		reveal.idleFullyShown(Math.max(2, dealer.size()), false);
		turnTicksLeft = turnTicks();
		markDirty();
	}

	private boolean tickDealerPlay() {
		// Manual dealer: host presses Hit/Stand. No auto-draw here.
		return false;
	}

	private boolean dealerShouldHit() {
		int s = dealer.score();
		if (s < 17) {
			return true;
		}
		return s == 17 && rules.dealerHitsSoft17() && dealer.isSoft();
	}

	private void settle() {
		long hostBal = Wallets.balance(hostId, stakeItem);
		for (TablePlayer p : players) {
			PlayerHandSeat seat = p.seat();
			BlackjackOutcome o = seat.outcome();
			if (o == null) {
				o = compare(seat);
				seat.setOutcome(o);
			}
			long payout = BlackjackSession.settleAmount(seat.bet(), o);
			if (payout > 0L) {
				long pay = Math.min(hostBal, payout);
				hostBal -= pay;
				Wallets.setBalance(hostId, stakeItem, hostBal);
				Escrow.creditWallet(Wallets.get(), p.playerId(), stakeItem, pay);
			} else {
				// Player already lost escrow; credit stake to house.
				hostBal += seat.bet();
				Wallets.setBalance(hostId, stakeItem, hostBal);
			}
			String handId = UUID.randomUUID().toString();
			HistoryDb.recordBjSettle(
				p.playerId(),
				handId,
				tableId,
				o.name(),
				seat.bet(),
				payout,
				payout - seat.bet(),
				stakeItem.toString(),
				Wallets.balance(p.playerId(), stakeItem),
				CardJson.of(dealer.cards()),
				CardJson.of(seat.hand().cards())
			);
		}
		phase = Phase.RESOLVED;
		markDirty();
	}

	private BlackjackOutcome compare(PlayerHandSeat seat) {
		if (seat.hand().isBust()) {
			return BlackjackOutcome.PLAYER_BUST;
		}
		boolean natural = !seat.fromSplit() && seat.hand().isBlackjack();
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
		int ps = seat.hand().score();
		int ds = dealer.score();
		if (ps > ds) {
			return BlackjackOutcome.WIN;
		}
		if (ps < ds) {
			return BlackjackOutcome.LOSE;
		}
		return BlackjackOutcome.PUSH;
	}

	private Card draw() {
		if (shoe.isEmpty()) {
			reshuffle();
		}
		return shoe.remove(shoe.size() - 1);
	}

	private void reshuffle() {
		shoe.clear();
		for (int d = 0; d < rules.decks(); d++) {
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
