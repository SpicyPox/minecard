package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.game.poker.PokerRules;
import com.spicypox.minecard.game.poker.PokerSeat;
import com.spicypox.minecard.game.poker.TablePoker;
import com.spicypox.minecard.ui.Dialogs;
import com.spicypox.minecard.ui.MainMenuDialog;
import com.spicypox.minecard.wallet.Escrow;
import com.spicypox.minecard.wallet.EscrowHold;
import com.spicypox.minecard.wallet.ItemSource;
import com.spicypox.minecard.wallet.PlayerItemSource;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PokerRooms {
	private static final Map<String, PokerRoom> BY_ID = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_ROOM = new ConcurrentHashMap<>();

	private PokerRooms() {
	}

	public static boolean occupied(UUID playerId) {
		return PLAYER_ROOM.containsKey(playerId);
	}

	public static Optional<PokerRoom> get(String roomId) {
		return Optional.ofNullable(BY_ID.get(roomId));
	}

	public static Optional<PokerRoom> roomOf(UUID playerId) {
		String id = PLAYER_ROOM.get(playerId);
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static void createFromDraft(ServerPlayer host, CreateRoomDraft draft) {
		Wallets.ensureStartingBalance(host.getUUID());
		PokerRules rules = draft.pokerRules().sanitized();
		Identifier stake = draft.stakeItem();
		long buyIn = Math.max(rules.minBuyIn(), Math.min(rules.maxBuyIn(), draft.betAmount()));
		long available = Rooms.availableForStake(host, stake);
		if (available < buyIn) {
			host.sendSystemMessage(Component.translatable(
				"minecard.create.stake.insufficient",
				buyIn,
				StakeItem.displayName(stake),
				available
			));
			CreateRoomDialog.openStake(host);
			return;
		}
		String id = UUID.randomUUID().toString().substring(0, 8);
		PokerRoom room = new PokerRoom(id, host.getUUID(), host.getGameProfile().name(), stake, true, rules);
		room.ensureHostSeat(host.getGameProfile().name());
		room.seat(host.getUUID()).ifPresent(s -> s.setBet(buyIn));
		BY_ID.put(id, room);
		PLAYER_ROOM.put(host.getUUID(), id);
		CreateRoomDraft.clear(host.getUUID());
		broadcastPublic(host.level().getServer(), room);
		host.sendSystemMessage(Component.translatable(
			"minecard.poker.room.created",
			id,
			StakeItem.amountLine(stake, buyIn)
		));
		openLobby(host);
	}

	public static void join(ServerPlayer player, String roomId) {
		PokerRoom room = BY_ID.get(roomId);
		if (room == null || !room.canJoin()) {
			player.sendSystemMessage(Component.translatable("minecard.room.join_fail"));
			return;
		}
		if (Rooms.occupied(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("minecard.room.already_in"));
			return;
		}
		BlackjackGames.stop(player.getUUID());
		Wallets.ensureStartingBalance(player.getUUID());
		if (!room.join(player.getUUID(), player.getGameProfile().name())) {
			player.sendSystemMessage(Component.translatable("minecard.room.join_fail"));
			return;
		}
		PokerRules rules = room.rules();
		room.seat(player.getUUID()).ifPresent(s -> s.setBet(rules.minBuyIn()));
		PLAYER_ROOM.put(player.getUUID(), roomId);
		player.sendSystemMessage(Component.translatable("minecard.room.joined", roomId));
		refreshLobby(room, player.level().getServer());
	}

	public static void leave(ServerPlayer player) {
		leaveInternal(player, player.level().getServer(), true);
	}

	/** Disconnect: fold/cash-out or close if host — no dialog reopen for the leaving player. */
	public static void onDisconnect(ServerPlayer player, MinecraftServer server) {
		leaveInternal(player, server, false);
	}

	public static boolean adminEnd(ServerPlayer target) {
		return roomOf(target.getUUID()).map(room -> {
			closeRoom(room, target.level().getServer(), "minecard.room.admin_end");
			return true;
		}).orElse(false);
	}

	private static void leaveInternal(ServerPlayer player, MinecraftServer server, boolean notifyPlayer) {
		String id = PLAYER_ROOM.remove(player.getUUID());
		if (id == null) {
			if (notifyPlayer) {
				MainMenuDialog.open(player);
			}
			return;
		}
		PokerRoom room = BY_ID.get(id);
		if (room == null) {
			if (notifyPlayer) {
				MainMenuDialog.open(player);
			}
			return;
		}
		if (room.hostId().equals(player.getUUID())) {
			closeRoom(room, server, "minecard.room.host_left");
			return;
		}
		TablePoker live = room.table();
		if (room.phase() == PokerRoom.Phase.PLAYING && live != null) {
			live.forceFold(player.getUUID());
		}
		cashOutSeat(room, player.getUUID(), notifyPlayer ? player : null);
		if (live != null) {
			live.seat(player.getUUID()).ifPresent(s -> s.setStack(0L));
		}
		room.leave(player.getUUID());
		if (notifyPlayer) {
			Dialogs.clear(player);
			player.sendSystemMessage(Component.translatable("minecard.room.left"));
		}
		if (room.phase() == PokerRoom.Phase.PLAYING && room.table() != null) {
			refreshTable(room, server);
		} else {
			refreshLobby(room, server);
		}
	}

	public static void setReady(ServerPlayer player, boolean ready) {
		roomOf(player.getUUID()).ifPresent(room -> {
			if (room.phase() != PokerRoom.Phase.LOBBY) {
				return;
			}
			room.seat(player.getUUID()).ifPresent(seat -> {
				if (ready) {
					lockBuyIn(player, room, seat);
				} else {
					unlockBuyIn(player, seat);
					seat.setReady(false);
				}
				refreshLobby(room, player.level().getServer());
			});
		});
	}

	public static void hostStart(ServerPlayer host) {
		roomOf(host.getUUID()).ifPresent(room -> {
			if (!room.hostId().equals(host.getUUID()) || room.phase() != PokerRoom.Phase.LOBBY) {
				return;
			}
			List<RoomSeat> ready = room.seats().stream().filter(RoomSeat::ready).toList();
			if (ready.size() < 2) {
				host.sendSystemMessage(Component.translatable("minecard.poker.need_ready"));
				return;
			}
			List<PokerSeat> pokerSeats = new ArrayList<>();
			for (RoomSeat seat : ready) {
				long stack = seat.lock() != null ? seat.lock().total() : seat.bet();
				pokerSeats.add(new PokerSeat(seat.playerId(), seat.displayName(), stack));
			}
			TablePoker table = new TablePoker(room.roomId(), room.rules(), pokerSeats);
			table.startHand();
			room.setTable(table);
			room.setPhase(PokerRoom.Phase.PLAYING);
			refreshTable(room, host.level().getServer());
		});
	}

	public static void dealNext(ServerPlayer host) {
		roomOf(host.getUUID()).ifPresent(room -> {
			if (!room.hostId().equals(host.getUUID()) || room.phase() != PokerRoom.Phase.PLAYING) {
				return;
			}
			TablePoker table = room.table();
			if (table == null || table.phase() != TablePoker.Phase.HAND_OVER) {
				return;
			}
			long live = table.seats().stream().filter(s -> s.stack() > 0L).count();
			if (live < 2) {
				host.sendSystemMessage(Component.translatable("minecard.poker.need_chips"));
				return;
			}
			table.advanceButton();
			table.startHand();
			refreshTable(room, host.level().getServer());
		});
	}

	public static void onTableClick(ServerPlayer player, String action, CompoundTag payload) {
		roomOf(player.getUUID()).ifPresent(room -> {
			TablePoker table = room.table();
			if (table == null || room.phase() != PokerRoom.Phase.PLAYING) {
				return;
			}
			UUID id = player.getUUID();
			boolean ok = switch (action) {
				case "fold" -> table.fold(id);
				case "check_call" -> table.checkOrCall(id);
				case "all_in" -> table.allIn(id);
				case "raise" -> table.raiseTo(id, readRaise(payload, table, id));
				case "raise_min" -> table.raisePreset(id, "min");
				case "raise_half" -> table.raisePreset(id, "half_pot");
				case "raise_pot" -> table.raisePreset(id, "pot");
				case "deal_next" -> {
					dealNext(player);
					yield true;
				}
				case "leave" -> {
					leave(player);
					yield true;
				}
				case "wait" -> true;
				default -> false;
			};
			if (!ok && !"deal_next".equals(action) && !"leave".equals(action) && !"wait".equals(action)) {
				player.sendSystemMessage(Component.translatable("minecard.poker.action_fail"));
			}
			if (PLAYER_ROOM.containsKey(player.getUUID())) {
				refreshTable(room, player.level().getServer());
			}
		});
	}

	private static long readRaise(CompoundTag payload, TablePoker table, UUID id) {
		String text = payload.getStringOr("raise_amount", "");
		if (!text.isBlank()) {
			try {
				return Long.parseLong(text.trim().replace(",", "").replace("_", ""));
			} catch (NumberFormatException ignored) {
			}
		}
		float f = payload.getFloatOr("raise_amount", -1f);
		if (f >= 0f) {
			return Math.round(f);
		}
		return table.minRaiseTo(id);
	}

	private static void lockBuyIn(ServerPlayer player, PokerRoom room, RoomSeat seat) {
		if (seat.lock() != null) {
			seat.setReady(true);
			return;
		}
		PokerRules rules = room.rules();
		long buyIn = Math.max(rules.minBuyIn(), Math.min(rules.maxBuyIn(),
			seat.bet() > 0 ? seat.bet() : rules.minBuyIn()));
		seat.setBet(buyIn);
		Optional<EscrowHold> hold = Escrow.lock(
			Wallets.get(),
			new PlayerItemSource(player),
			player.getUUID(),
			room.stakeItem(),
			buyIn
		);
		if (hold.isEmpty()) {
			player.sendSystemMessage(Component.translatable("minecard.room.bet_fail", buyIn));
			seat.setReady(false);
			return;
		}
		seat.setLock(hold.get());
		seat.setReady(true);
	}

	private static void unlockBuyIn(ServerPlayer player, RoomSeat seat) {
		EscrowHold hold = seat.lock();
		if (hold != null) {
			Escrow.release(Wallets.get(), new PlayerItemSource(player), hold);
			seat.setLock(null);
		}
	}

	private static void cashOutSeat(PokerRoom room, UUID playerId, ServerPlayer online) {
		room.seat(playerId).ifPresent(seat -> {
			long stack = room.table() != null
				? room.table().seat(playerId).map(PokerSeat::stack).orElse(-1L)
				: -1L;
			EscrowHold hold = seat.lock();
			if (stack >= 0L && room.phase() == PokerRoom.Phase.PLAYING) {
				// Consume escrow; credit current stack to wallet.
				seat.setLock(null);
				Escrow.creditWallet(Wallets.get(), playerId, room.stakeItem(), stack);
			} else if (hold != null) {
				ItemSource items = online != null ? new PlayerItemSource(online) : ItemSource.empty();
				Escrow.release(Wallets.get(), items, hold);
				seat.setLock(null);
			}
		});
	}

	private static void closeRoom(PokerRoom room, MinecraftServer server, String reasonKey) {
		TablePoker table = room.table();
		if (table != null && table.inHand()) {
			// Return pot commitments to stacks before cash-out (do not destroy chips in pot).
			table.abortHand();
		}
		for (RoomSeat seat : room.seats()) {
			ServerPlayer p = server != null ? server.getPlayerList().getPlayer(seat.playerId()) : null;
			cashOutSeat(room, seat.playerId(), p);
			PLAYER_ROOM.remove(seat.playerId());
			if (p != null) {
				Dialogs.clear(p);
				p.sendSystemMessage(Component.translatable(reasonKey));
				MainMenuDialog.open(p);
			}
		}
		PLAYER_ROOM.remove(room.hostId());
		BY_ID.remove(room.roomId());
	}

	public static void openLobby(ServerPlayer player) {
		roomOf(player.getUUID()).ifPresentOrElse(
			room -> PokerLobbyDialog.open(player, room),
			() -> MainMenuDialog.open(player)
		);
	}

	static void refreshLobby(PokerRoom room, MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (RoomSeat seat : room.seats()) {
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			if (p != null) {
				PokerLobbyDialog.open(p, room);
			}
		}
	}

	static void refreshTable(PokerRoom room, MinecraftServer server) {
		if (server == null || room.table() == null) {
			return;
		}
		for (PokerSeat seat : room.table().seats()) {
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			if (p != null) {
				TablePokerDialog.open(p, room);
			}
		}
	}

	private static void broadcastPublic(MinecraftServer server, PokerRoom room) {
		if (server == null) {
			return;
		}
		MutableComponent code = Component.literal(room.roomId()).withStyle(ChatFormatting.AQUA);
		MutableComponent stake = StakeItem.amountLine(room.stakeItem(), room.rules().minBuyIn()).copy()
			.withStyle(ChatFormatting.GOLD);
		MutableComponent msg = Component.translatable("minecard.poker.room.public_chat", room.hostName(), code, stake);
		MutableComponent join = Component.translatable("minecard.room.click_join")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/minecard join " + room.roomId()))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("minecard.poker.room.join_hover"))));
		server.getPlayerList().broadcastSystemMessage(
			Component.empty().append(msg).append(" ").append(join),
			false
		);
		Minecard.LOGGER.info("Poker room {} created by {}", room.roomId(), room.hostName());
	}
}
