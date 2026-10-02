package com.spicypox.minecard.room;

import com.spicypox.minecard.game.poker.PokerRules;
import com.spicypox.minecard.game.poker.TablePoker;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Peer NLHE room: host creates/starts; host may also sit and play. */
public final class PokerRoom {
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
	private final PokerRules rules;
	private Phase phase = Phase.LOBBY;
	private final List<RoomSeat> seats = new ArrayList<>();
	private TablePoker table;

	public PokerRoom(
		String roomId,
		UUID hostId,
		String hostName,
		Identifier stakeItem,
		boolean publicRoom,
		PokerRules rules
	) {
		this.roomId = roomId;
		this.hostId = hostId;
		this.hostName = hostName;
		this.stakeItem = stakeItem;
		this.publicRoom = publicRoom;
		this.rules = rules != null ? rules.sanitized() : PokerRules.defaults();
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

	public PokerRules rules() {
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

	public TablePoker table() {
		return table;
	}

	public void setTable(TablePoker table) {
		this.table = table;
	}

	public boolean canJoin() {
		return phase == Phase.LOBBY && seats.size() < rules.maxPlayers();
	}

	public boolean join(UUID playerId, String name) {
		if (!canJoin() || seat(playerId).isPresent()) {
			return false;
		}
		seats.add(new RoomSeat(playerId, name));
		return true;
	}

	/** Ensure host has a seat (host plays at the table). */
	public void ensureHostSeat(String name) {
		if (seat(hostId).isEmpty() && seats.size() < rules.maxPlayers()) {
			seats.add(0, new RoomSeat(hostId, name));
		}
	}

	public void leave(UUID playerId) {
		seats.removeIf(s -> s.playerId().equals(playerId));
	}
}
