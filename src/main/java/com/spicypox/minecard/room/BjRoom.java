package com.spicypox.minecard.room;

import com.spicypox.minecard.game.blackjack.TableBlackjack;
import com.spicypox.minecard.wallet.EscrowHold;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public/private blackjack room: host is house (no cards), up to {@link #MAX_PLAYERS} seats.
 */
public final class BjRoom {
	public static final int MAX_PLAYERS = 4;

	public enum Phase {
		LOBBY,
		PLAYING,
		CLOSED
	}

	private final String roomId;
	private final UUID hostId;
	private String hostName;
	private final Identifier stakeItem;
	private final boolean publicRoom;
	private final long minBet;
	private final long maxBet;
	private final BjRoomRules rules;
	private Phase phase = Phase.LOBBY;
	private final List<RoomSeat> seats = new ArrayList<>();
	private TableBlackjack table;
	private EscrowHold hostBankroll;
	/** After a hand resolves, clear consumed escrow once so Play again can lock again. */
	private boolean postSettleCleared;

	public BjRoom(
		String roomId,
		UUID hostId,
		String hostName,
		Identifier stakeItem,
		boolean publicRoom,
		long minBet,
		long maxBet
	) {
		this(roomId, hostId, hostName, stakeItem, publicRoom, minBet, maxBet, BjRoomRules.defaults());
	}

	public BjRoom(
		String roomId,
		UUID hostId,
		String hostName,
		Identifier stakeItem,
		boolean publicRoom,
		long minBet,
		long maxBet,
		BjRoomRules rules
	) {
		this.roomId = roomId;
		this.hostId = hostId;
		this.hostName = hostName;
		this.stakeItem = stakeItem;
		this.publicRoom = publicRoom;
		this.minBet = minBet;
		this.maxBet = maxBet;
		this.rules = rules != null ? rules.sanitized() : BjRoomRules.defaults();
	}

	public String roomId() {
		return roomId;
	}

	public UUID hostId() {
		return hostId;
	}

	public String hostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public Identifier stakeItem() {
		return stakeItem;
	}

	public boolean publicRoom() {
		return publicRoom;
	}

	public long minBet() {
		return minBet;
	}

	public long maxBet() {
		return maxBet;
	}

	public BjRoomRules rules() {
		return rules;
	}

	public Phase phase() {
		return phase;
	}

	public void setPhase(Phase phase) {
		this.phase = phase;
	}

	public List<RoomSeat> seats() {
		return List.copyOf(seats);
	}

	public Optional<RoomSeat> seat(UUID playerId) {
		return seats.stream().filter(s -> s.playerId().equals(playerId)).findFirst();
	}

	public boolean isMember(UUID playerId) {
		return hostId.equals(playerId) || seat(playerId).isPresent();
	}

	public boolean canJoin() {
		if (seats.size() >= rules.maxPlayers()) {
			return false;
		}
		if (phase == Phase.LOBBY) {
			return true;
		}
		// Between hands: allow rejoin via chat link while table is resolved (or cleared).
		if (phase == Phase.PLAYING) {
			return table == null || table.phase() == TableBlackjack.Phase.RESOLVED;
		}
		return false;
	}

	public boolean join(UUID playerId, String name) {
		if (!canJoin() || hostId.equals(playerId) || seat(playerId).isPresent()) {
			return false;
		}
		RoomSeat seat = new RoomSeat(playerId, name);
		seat.setBet(minBet);
		seats.add(seat);
		return true;
	}

	/** Used by {@link com.spicypox.minecard.wallet.RoomSavedData} restore. */
	public void addRestoredSeat(RoomSeat seat) {
		if (seat(seat.playerId()).isEmpty() && !hostId.equals(seat.playerId())) {
			seats.add(seat);
		}
	}

	public void leave(UUID playerId) {
		seats.removeIf(s -> s.playerId().equals(playerId));
	}

	public TableBlackjack table() {
		return table;
	}

	public void setTable(TableBlackjack table) {
		this.table = table;
		// New hand or leave table — allow a fresh post-settle clear next time.
		this.postSettleCleared = false;
	}

	/** True after RESOLVED escrow markers were cleared once (play-again locks must stay). */
	public boolean postSettleCleared() {
		return postSettleCleared;
	}

	public void setPostSettleCleared(boolean postSettleCleared) {
		this.postSettleCleared = postSettleCleared;
	}

	public EscrowHold hostBankroll() {
		return hostBankroll;
	}

	public void setHostBankroll(EscrowHold hostBankroll) {
		this.hostBankroll = hostBankroll;
	}

	/** Max house liability if every seat hits natural BJ 3:2. */
	public long maxHouseLiability() {
		long sum = 0L;
		for (RoomSeat seat : seats) {
			if (seat.ready() && seat.bet() > 0L) {
				sum += seat.bet() + (seat.bet() * 3L / 2L);
			}
		}
		return sum;
	}
}
