package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory wallet for unit tests and before a world is loaded. */
public final class MemoryWallet implements Wallet {
	private final Map<UUID, Map<Identifier, Long>> balances = new ConcurrentHashMap<>();

	@Override
	public long balance(UUID playerId, Identifier itemId) {
		Map<Identifier, Long> map = balances.get(playerId);
		if (map == null) {
			return 0L;
		}
		return map.getOrDefault(itemId, 0L);
	}

	@Override
	public void setBalance(UUID playerId, Identifier itemId, long amount) {
		long v = Math.max(0L, amount);
		Map<Identifier, Long> map = balances.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
		if (v == 0L) {
			map.remove(itemId);
		} else {
			map.put(itemId, v);
		}
	}

	@Override
	public boolean hasAccount(UUID playerId) {
		return balances.containsKey(playerId);
	}

	@Override
	public List<Map.Entry<Identifier, Long>> listBalances(UUID playerId) {
		Map<Identifier, Long> map = balances.get(playerId);
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
		balances.clear();
	}
}
