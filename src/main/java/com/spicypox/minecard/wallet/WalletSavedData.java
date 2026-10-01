package com.spicypox.minecard.wallet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spicypox.minecard.Minecard;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-scoped wallet balances. Loaded via overworld {@link DimensionDataStorage}.
 */
public final class WalletSavedData extends SavedData implements Wallet {
	private static final Codec<Map<Identifier, Long>> ITEM_BALANCES =
		Codec.unboundedMap(Identifier.CODEC, Codec.LONG);

	private static final Codec<Map<UUID, Map<Identifier, Long>>> PLAYERS =
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, ITEM_BALANCES);

	public static final Codec<WalletSavedData> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			PLAYERS.optionalFieldOf("players", Map.of()).forGetter(d -> d.players)
		).apply(instance, WalletSavedData::new)
	);

	public static final SavedDataType<WalletSavedData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "wallet"),
		WalletSavedData::new,
		CODEC,
		null
	);

	private final Map<UUID, Map<Identifier, Long>> players;

	public WalletSavedData() {
		this(new HashMap<>());
	}

	public WalletSavedData(Map<UUID, Map<Identifier, Long>> players) {
		this.players = new HashMap<>();
		for (Map.Entry<UUID, Map<Identifier, Long>> e : players.entrySet()) {
			this.players.put(e.getKey(), new HashMap<>(e.getValue()));
		}
	}

	public static WalletSavedData get(MinecraftServer server) {
		ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
		if (level == null) {
			return new WalletSavedData();
		}
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	public long balance(UUID playerId, Identifier itemId) {
		Map<Identifier, Long> map = players.get(playerId);
		if (map == null) {
			return 0L;
		}
		return map.getOrDefault(itemId, 0L);
	}

	@Override
	public void setBalance(UUID playerId, Identifier itemId, long amount) {
		long v = Math.max(0L, amount);
		// Keep an empty map after full withdraw so hasAccount stays true across restarts.
		Map<Identifier, Long> map = players.computeIfAbsent(playerId, id -> new HashMap<>());
		if (v == 0L) {
			map.remove(itemId);
		} else {
			map.put(itemId, v);
		}
		setDirty();
	}

	@Override
	public boolean hasAccount(UUID playerId) {
		return players.containsKey(playerId);
	}

	@Override
	public List<Map.Entry<Identifier, Long>> listBalances(UUID playerId) {
		Map<Identifier, Long> map = players.get(playerId);
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		List<Map.Entry<Identifier, Long>> out = new ArrayList<>();
		for (Map.Entry<Identifier, Long> e : map.entrySet()) {
			if (e.getValue() != null && e.getValue() > 0L) {
				out.add(Map.entry(e.getKey(), e.getValue()));
			}
		}
		out.sort(Comparator.comparing(e -> e.getKey().toString()));
		return out;
	}

	@Override
	public void clearAll() {
		players.clear();
		setDirty();
	}
}
