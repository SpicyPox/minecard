package com.spicypox.minecard.wallet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.Rank;
import com.spicypox.minecard.card.Suit;
import com.spicypox.minecard.game.blackjack.BlackjackOutcome;
import com.spicypox.minecard.game.blackjack.BlackjackRoundState;
import com.spicypox.minecard.game.blackjack.BlackjackSession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists solo {@link BlackjackSession} snapshots across restarts.
 */
public final class SessionSavedData extends SavedData {
	private static final Codec<Map<String, String>> BLOBS =
		Codec.unboundedMap(Codec.STRING, Codec.STRING);

	public static final Codec<SessionSavedData> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			BLOBS.optionalFieldOf("solo", Map.of()).forGetter(d -> d.soloBlobs)
		).apply(instance, SessionSavedData::new)
	);

	public static final SavedDataType<SessionSavedData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "sessions"),
		SessionSavedData::new,
		CODEC,
		null
	);

	private final Map<String, String> soloBlobs;

	public SessionSavedData() {
		this(new HashMap<>());
	}

	public SessionSavedData(Map<String, String> soloBlobs) {
		this.soloBlobs = new HashMap<>(soloBlobs);
	}

	public static SessionSavedData get(MinecraftServer server) {
		ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
		if (level == null) {
			return new SessionSavedData();
		}
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void putSolo(UUID playerId, BlackjackRoundState state) {
		soloBlobs.put(playerId.toString(), encode(state));
		setDirty();
	}

	public void removeSolo(UUID playerId) {
		if (soloBlobs.remove(playerId.toString()) != null) {
			setDirty();
		}
	}

	public Optional<BlackjackRoundState> getSolo(UUID playerId) {
		String blob = soloBlobs.get(playerId.toString());
		if (blob == null || blob.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(decode(blob, playerId));
		} catch (RuntimeException e) {
			Minecard.LOGGER.warn("Bad solo session blob for {}: {}", playerId, e.toString());
			return Optional.empty();
		}
	}

	private static String encode(BlackjackRoundState s) {
		StringBuilder sb = new StringBuilder();
		sb.append(s.phase.name()).append(';')
			.append(s.balance).append(';')
			.append(s.held).append(';')
			.append(s.unitBet).append(';')
			.append(s.stakeItemId).append(';')
			.append(s.turnTicksLeft).append(';')
			.append(s.dialogAway ? '1' : '0').append(';')
			.append(s.dealerShown).append(';')
			.append(s.holeFaceUp ? '1' : '0').append(';')
			.append(s.dealIndex).append(';')
			.append(s.stepCooldown).append(';')
			.append(s.activeHand).append(';')
			.append(s.splitUsed ? '1' : '0').append(';')
			.append(s.primaryOutcome == null ? "-" : s.primaryOutcome.name()).append(';')
			.append(cards(s.dealerCards)).append(';')
			.append(cards(s.shoe)).append(';')
			.append(s.playerHands.size());
		for (BlackjackRoundState.HandSnapshot h : s.playerHands) {
			sb.append(';')
				.append(cards(h.cards())).append('|')
				.append(h.bet()).append('|')
				.append(h.doubled() ? '1' : '0').append('|')
				.append(h.fromSplit() ? '1' : '0').append('|')
				.append(h.aceSplit() ? '1' : '0').append('|')
				.append(h.finished() ? '1' : '0').append('|')
				.append(h.outcome() == null ? "-" : h.outcome().name()).append('|')
				.append(h.shown());
		}
		return sb.toString();
	}

	private static BlackjackRoundState decode(String blob, UUID playerId) {
		String[] p = blob.split(";", -1);
		BlackjackSession.Phase phase = BlackjackSession.Phase.valueOf(p[0]);
		long balance = Long.parseLong(p[1]);
		long held = Long.parseLong(p[2]);
		long unitBet = Long.parseLong(p[3]);
		Identifier stake = Identifier.parse(p[4]);
		int turn = Integer.parseInt(p[5]);
		boolean away = "1".equals(p[6]);
		int dealerShown = Integer.parseInt(p[7]);
		boolean hole = "1".equals(p[8]);
		int dealIndex = Integer.parseInt(p[9]);
		int cool = Integer.parseInt(p[10]);
		int active = Integer.parseInt(p[11]);
		boolean splitUsed = "1".equals(p[12]);
		BlackjackOutcome primary = "-".equals(p[13]) ? null : BlackjackOutcome.valueOf(p[13]);
		List<Card> dealer = parseCards(p[14]);
		List<Card> shoe = parseCards(p[15]);
		int handCount = Integer.parseInt(p[16]);
		List<BlackjackRoundState.HandSnapshot> hands = new ArrayList<>();
		for (int i = 0; i < handCount; i++) {
			String[] h = p[17 + i].split("\\|", -1);
			hands.add(new BlackjackRoundState.HandSnapshot(
				parseCards(h[0]),
				Long.parseLong(h[1]),
				"1".equals(h[2]),
				"1".equals(h[3]),
				"1".equals(h[4]),
				"1".equals(h[5]),
				"-".equals(h[6]) ? null : BlackjackOutcome.valueOf(h[6]),
				Integer.parseInt(h[7])
			));
		}
		return new BlackjackRoundState(
			playerId, phase, shoe, dealer, dealerShown, hole, hands, active, splitUsed,
			unitBet, held, balance, stake, turn, away, dealIndex, cool, primary
		);
	}

	private static String cards(List<Card> cards) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cards.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			Card c = cards.get(i);
			sb.append(c.suit().name()).append('-').append(c.rank().name());
		}
		return sb.toString();
	}

	private static List<Card> parseCards(String s) {
		if (s == null || s.isBlank()) {
			return List.of();
		}
		List<Card> out = new ArrayList<>();
		for (String part : s.split(",")) {
			String[] sr = part.split("-", 2);
			out.add(new Card(Suit.valueOf(sr[0]), Rank.valueOf(sr[1])));
		}
		return out;
	}
}
