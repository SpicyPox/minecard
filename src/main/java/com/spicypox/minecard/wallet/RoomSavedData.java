package com.spicypox.minecard.wallet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.room.BjRoom;
import com.spicypox.minecard.room.RoomSeat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists lobby blackjack rooms (seats + escrow locks) across restarts.
 * Mid-hand tables are not resumed — callers refund playing rooms to lobby first.
 */
public final class RoomSavedData extends SavedData {
	private static final Codec<Map<String, String>> BLOBS =
		Codec.unboundedMap(Codec.STRING, Codec.STRING);

	public static final Codec<RoomSavedData> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			BLOBS.optionalFieldOf("rooms", Map.of()).forGetter(d -> d.roomBlobs)
		).apply(instance, RoomSavedData::new)
	);

	public static final SavedDataType<RoomSavedData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "rooms"),
		RoomSavedData::new,
		CODEC,
		null
	);

	private final Map<String, String> roomBlobs;

	public RoomSavedData() {
		this(new HashMap<>());
	}

	public RoomSavedData(Map<String, String> roomBlobs) {
		this.roomBlobs = new HashMap<>(roomBlobs);
	}

	public static RoomSavedData get(MinecraftServer server) {
		ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
		if (level == null) {
			return new RoomSavedData();
		}
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void replaceAll(List<BjRoom> rooms) {
		roomBlobs.clear();
		for (BjRoom room : rooms) {
			if (room.phase() != BjRoom.Phase.CLOSED) {
				roomBlobs.put(room.roomId(), encode(room));
			}
		}
		setDirty();
	}

	/**
	 * Restores lobby rooms. Rooms saved mid-hand are returned with phase LOBBY and
	 * {@code needsRefund=true} so callers can release escrow (table state is not resumed).
	 */
	public List<LoadedRoom> loadAll() {
		List<LoadedRoom> out = new ArrayList<>();
		for (Map.Entry<String, String> e : roomBlobs.entrySet()) {
			try {
				LoadedRoom loaded = decode(e.getValue());
				if (loaded != null) {
					out.add(loaded);
				}
			} catch (RuntimeException ex) {
				Minecard.LOGGER.warn("Bad room blob {}: {}", e.getKey(), ex.toString());
			}
		}
		return out;
	}

	public record LoadedRoom(BjRoom room, boolean needsRefund) {
	}

	private static String sanitize(String s) {
		return s == null ? "" : s.replace(';', '_').replace('|', '_');
	}

	private static String encode(BjRoom room) {
		var rules = room.rules();
		StringBuilder sb = new StringBuilder();
		sb.append("v3;").append(room.roomId()).append(';')
			.append(room.hostId()).append(';')
			.append(sanitize(room.hostName())).append(';')
			.append(room.stakeItem()).append(';')
			.append(room.publicRoom() ? '1' : '0').append(';')
			.append(room.minBet()).append(';')
			.append(room.maxBet()).append(';')
			.append(room.phase().name()).append(';')
			.append(rules.turnSeconds()).append(';')
			.append(rules.decks()).append(';')
			.append(rules.insuranceEnabled() ? '1' : '0').append(';')
			.append(rules.dealerHitsSoft17() ? '1' : '0').append(';')
			.append(rules.surrenderEnabled() ? '1' : '0').append(';')
			.append(rules.maxPlayers()).append(';')
			.append(room.seats().size());
		for (RoomSeat seat : room.seats()) {
			EscrowHold lock = seat.lock();
			long bal = lock != null ? lock.balancePart() : 0L;
			long inv = lock != null ? lock.inventoryPart() : 0L;
			sb.append(';')
				.append(seat.playerId()).append('|')
				.append(sanitize(seat.displayName())).append('|')
				.append(seat.bet()).append('|')
				.append(seat.ready() ? '1' : '0').append('|')
				.append(bal).append('|')
				.append(inv);
		}
		return sb.toString();
	}

	private static LoadedRoom decode(String blob) {
		String[] p = blob.split(";", -1);
		if (p.length < 9) {
			return null;
		}
		boolean v3 = "v3".equals(p[0]);
		boolean v2 = "v2".equals(p[0]);
		boolean v1 = "v1".equals(p[0]);
		if (!v1 && !v2 && !v3) {
			return null;
		}
		String roomId = p[1];
		UUID hostId = UUID.fromString(p[2]);
		String hostName = p[3];
		Identifier stake = Identifier.parse(p[4]);
		boolean pub = "1".equals(p[5]);
		long min = Long.parseLong(p[6]);
		long max = Long.parseLong(p[7]);
		BjRoom.Phase savedPhase = BjRoom.Phase.LOBBY;
		int seatCount;
		int seatBase;
		com.spicypox.minecard.room.BjRoomRules rules = com.spicypox.minecard.room.BjRoomRules.defaults();
		if (v3) {
			savedPhase = BjRoom.Phase.valueOf(p[8]);
			rules = new com.spicypox.minecard.room.BjRoomRules(
				Integer.parseInt(p[9]),
				Integer.parseInt(p[10]),
				"1".equals(p[11]),
				"1".equals(p[12]),
				"1".equals(p[13]),
				Integer.parseInt(p[14])
			).sanitized();
			seatCount = Integer.parseInt(p[15]);
			seatBase = 16;
		} else if (v2) {
			savedPhase = BjRoom.Phase.valueOf(p[8]);
			seatCount = Integer.parseInt(p[9]);
			seatBase = 10;
		} else {
			seatCount = Integer.parseInt(p[8]);
			seatBase = 9;
		}
		BjRoom room = new BjRoom(roomId, hostId, hostName, stake, pub, min, max, rules);
		boolean needsRefund = savedPhase == BjRoom.Phase.PLAYING;
		for (int i = 0; i < seatCount; i++) {
			String[] s = p[seatBase + i].split("\\|", -1);
			UUID pid = UUID.fromString(s[0]);
			RoomSeat seat = new RoomSeat(pid, s[1]);
			seat.setBet(Long.parseLong(s[2]));
			boolean ready = "1".equals(s[3]);
			long bal = Long.parseLong(s[4]);
			long inv = Long.parseLong(s[5]);
			if (bal > 0L || inv > 0L) {
				seat.setLock(new EscrowHold(pid, stake, bal, inv));
			}
			if (needsRefund) {
				seat.setReady(false);
			} else {
				seat.setReady(ready && seat.lock() != null);
			}
			room.addRestoredSeat(seat);
		}
		room.setPhase(BjRoom.Phase.LOBBY);
		return new LoadedRoom(room, needsRefund);
	}
}
