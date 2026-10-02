package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.game.blackjack.TableBlackjack;
import com.spicypox.minecard.game.blackjack.TablePlayer;
import com.spicypox.minecard.ui.Dialogs;
import com.spicypox.minecard.ui.MainMenuDialog;
import com.spicypox.minecard.wallet.Escrow;
import com.spicypox.minecard.wallet.EscrowHold;
import com.spicypox.minecard.wallet.ItemSource;
import com.spicypox.minecard.wallet.PlayerItemSource;
import com.spicypox.minecard.wallet.RoomSavedData;
import com.spicypox.minecard.wallet.WalletConstants;
import com.spicypox.minecard.wallet.Wallets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Rooms {
	private static final Map<String, BjRoom> BY_ID = new ConcurrentHashMap<>();
	private static final Map<UUID, String> PLAYER_ROOM = new ConcurrentHashMap<>();
	private static boolean registered;

	private Rooms() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(Rooms::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(Rooms::loadFromWorld);
		ServerLifecycleEvents.SERVER_STOPPING.register(Rooms::persistOnShutdown);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			onDisconnect(handler.player, server));
	}

	private static void loadFromWorld(MinecraftServer server) {
		BY_ID.clear();
		PLAYER_ROOM.clear();
		for (RoomSavedData.LoadedRoom loaded : RoomSavedData.get(server).loadAll()) {
			BjRoom room = loaded.room();
			if (loaded.needsRefund()) {
				for (RoomSeat seat : room.seats()) {
					EscrowHold hold = seat.lock();
					if (hold != null) {
						Escrow.release(Wallets.get(), ItemSource.empty(), hold);
						seat.setLock(null);
					}
					seat.setReady(false);
					seat.setBet(WalletConstants.DEFAULT_BET);
				}
			}
			BY_ID.put(room.roomId(), room);
			PLAYER_ROOM.put(room.hostId(), room.roomId());
			for (RoomSeat seat : room.seats()) {
				PLAYER_ROOM.put(seat.playerId(), room.roomId());
			}
		}
		persist(server);
		Minecard.LOGGER.info("Loaded {} Minecard rooms from SavedData", BY_ID.size());
	}

	private static void persist(MinecraftServer server) {
		if (server == null) {
			return;
		}
		RoomSavedData.get(server).replaceAll(List.copyOf(BY_ID.values()));
	}

	/**
	 * Mid-hand tables cannot resume yet — refund escrow to wallet, keep lobby rooms + locks.
	 */
	private static void persistOnShutdown(MinecraftServer server) {
		for (BjRoom room : List.copyOf(BY_ID.values())) {
			if (room.phase() == BjRoom.Phase.PLAYING) {
				abortPlayingToLobby(room, server);
			}
		}
		persist(server);
		BY_ID.clear();
		PLAYER_ROOM.clear();
	}

	private static void abortPlayingToLobby(BjRoom room, MinecraftServer server) {
		for (RoomSeat seat : room.seats()) {
			EscrowHold hold = seat.lock();
			if (hold != null) {
				ServerPlayer online = server.getPlayerList().getPlayer(seat.playerId());
				ItemSource items = online != null ? new PlayerItemSource(online) : ItemSource.empty();
				Escrow.release(Wallets.get(), items, hold);
				seat.setLock(null);
			}
			seat.setReady(false);
			seat.setBet(WalletConstants.DEFAULT_BET);
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			if (p != null) {
				p.sendSystemMessage(Component.translatable("minecard.room.round_aborted"));
			}
		}
		room.setTable(null);
		room.setPhase(BjRoom.Phase.LOBBY);
		ServerPlayer host = server.getPlayerList().getPlayer(room.hostId());
		if (host != null) {
			host.sendSystemMessage(Component.translatable("minecard.room.round_aborted"));
		}
	}

	private static void onDisconnect(ServerPlayer player, MinecraftServer server) {
		roomOf(player.getUUID()).ifPresent(room -> {
			if (room.hostId().equals(player.getUUID())) {
				// Host grace: close room and refund seats (ke-hoach: host offline mid-lobby/hand).
				closeRoom(room, server, "minecard.room.host_left");
				return;
			}
			if (room.phase() == BjRoom.Phase.LOBBY) {
				refundSeat(room, player.getUUID(), null);
				room.leave(player.getUUID());
				PLAYER_ROOM.remove(player.getUUID());
				persist(server);
				refreshLobby(room, server);
				return;
			}
			if (room.phase() == BjRoom.Phase.PLAYING && room.table() != null) {
				// Auto-stand disconnected player's active turn; otherwise leave seat to resolve.
				room.table().stand(player.getUUID());
				refreshTable(room, server);
			}
		});
	}

	/** Opens the create-room wizard (kept for older call sites). */
	public static void createFromHeld(ServerPlayer host) {
		CreateRoomDialog.openGameSelect(host);
	}

	public static void createFromDraft(ServerPlayer host, CreateRoomDraft draft) {
		if (PLAYER_ROOM.containsKey(host.getUUID())) {
			host.sendSystemMessage(Component.translatable("minecard.room.already_in"));
			CreateRoomDraft.clear(host.getUUID());
			openLobby(host);
			return;
		}
		if (draft.game() != CreateRoomDraft.Game.BLACKJACK) {
			host.sendSystemMessage(Component.translatable("minecard.create.poker.soon"));
			CreateRoomDialog.openGameSelect(host);
			return;
		}
		Wallets.ensureStartingBalance(host.getUUID());
		Identifier stake = draft.stakeItem();
		long bet = Math.max(1L, draft.betAmount());
		long available = availableForStake(host, stake);
		if (available < bet) {
			host.sendSystemMessage(Component.translatable(
				"minecard.create.stake.insufficient",
				bet,
				StakeItem.displayName(stake),
				available
			));
			CreateRoomDialog.openStake(host);
			return;
		}
		BjRoomRules rules = draft.rules().sanitized();
		String id = UUID.randomUUID().toString().substring(0, 8);
		BjRoom room = new BjRoom(
			id,
			host.getUUID(),
			host.getGameProfile().name(),
			stake,
			true,
			bet,
			bet,
			rules
		);
		BY_ID.put(id, room);
		PLAYER_ROOM.put(host.getUUID(), id);
		CreateRoomDraft.clear(host.getUUID());
		persist(host.level().getServer());
		broadcastPublic(host.level().getServer(), room);
		host.sendSystemMessage(Component.translatable(
			"minecard.room.created",
			id,
			StakeItem.amountLine(stake, bet)
		));
		openLobby(host);
	}

	/**
	 * Balance first; if short, add plain main-hand + off-hand of the same item.
	 */
	public static long availableForStake(ServerPlayer player, Identifier stake) {
		long bal = Wallets.balance(player.getUUID(), stake);
		long hands = countPlainHand(player.getMainHandItem(), stake)
			+ countPlainHand(player.getOffhandItem(), stake);
		if (bal > 0L) {
			return bal + hands;
		}
		return hands;
	}

	private static long countPlainHand(ItemStack stack, Identifier stake) {
		if (stack.isEmpty()) {
			return 0L;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!stake.equals(id)) {
			return 0L;
		}
		if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()))) {
			return 0L;
		}
		return stack.getCount();
	}

	public static void openPublicList(ServerPlayer player) {
		RoomListDialog.open(player, publicRooms());
	}

	public static List<BjRoom> publicRooms() {
		List<BjRoom> list = new ArrayList<>();
		for (BjRoom room : BY_ID.values()) {
			if (room.publicRoom() && room.phase() == BjRoom.Phase.LOBBY) {
				list.add(room);
			}
		}
		return list;
	}

	public static Optional<BjRoom> roomOf(UUID playerId) {
		String id = PLAYER_ROOM.get(playerId);
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static void join(ServerPlayer player, String roomId) {
		BjRoom room = BY_ID.get(roomId);
		if (room == null || !room.canJoin()) {
			player.sendSystemMessage(Component.translatable("minecard.room.join_fail"));
			return;
		}
		if (PLAYER_ROOM.containsKey(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("minecard.room.already_in"));
			return;
		}
		BlackjackGames.stop(player.getUUID());
		Wallets.ensureStartingBalance(player.getUUID());
		if (!room.join(player.getUUID(), player.getGameProfile().name())) {
			player.sendSystemMessage(Component.translatable("minecard.room.join_fail"));
			return;
		}
		PLAYER_ROOM.put(player.getUUID(), roomId);
		persist(player.level().getServer());
		player.sendSystemMessage(Component.translatable("minecard.room.joined", roomId));
		if (room.phase() == BjRoom.Phase.PLAYING && room.table() != null) {
			// Rejoined between hands — stay on the table UI (Play again / wait for Deal).
			TableBlackjackDialog.open(player, room, false);
			refreshTable(room, player.level().getServer());
		} else {
			refreshLobby(room, player.level().getServer());
		}
	}

	public static void leave(ServerPlayer player) {
		String id = PLAYER_ROOM.remove(player.getUUID());
		if (id == null) {
			MainMenuDialog.open(player);
			return;
		}
		BjRoom room = BY_ID.get(id);
		if (room == null) {
			MainMenuDialog.open(player);
			return;
		}
		if (room.hostId().equals(player.getUUID())) {
			closeRoom(room, player.level().getServer(), "minecard.room.host_left");
			return;
		}
		refundSeat(room, player.getUUID(), player);
		room.leave(player.getUUID());
		persist(player.level().getServer());
		Dialogs.clear(player);
		player.sendSystemMessage(Component.translatable("minecard.room.left"));
		refreshLobby(room, player.level().getServer());
	}

	public static void setReady(ServerPlayer player, boolean ready) {
		roomOf(player.getUUID()).ifPresent(room -> {
			if (room.phase() != BjRoom.Phase.LOBBY) {
				return;
			}
			room.seat(player.getUUID()).ifPresent(seat -> {
				if (ready) {
					lockBet(player, room, seat);
				} else {
					unlockBet(player, seat);
					seat.setReady(false);
				}
				persist(player.level().getServer());
				refreshLobby(room, player.level().getServer());
			});
		});
	}

	public static void hostStart(ServerPlayer host) {
		roomOf(host.getUUID()).ifPresent(room -> {
			if (!room.hostId().equals(host.getUUID()) || room.phase() != BjRoom.Phase.LOBBY) {
				return;
			}
			startRoundWithReady(host, room);
		});
	}

	/** Host deals the next hand while players stay at the table after RESOLVED. */
	public static void dealNext(ServerPlayer host) {
		roomOf(host.getUUID()).ifPresent(room -> {
			if (!room.hostId().equals(host.getUUID()) || room.phase() != BjRoom.Phase.PLAYING) {
				return;
			}
			TableBlackjack table = room.table();
			if (table == null || table.phase() != TableBlackjack.Phase.RESOLVED) {
				return;
			}
			startRoundWithReady(host, room);
		});
	}

	private static void startRoundWithReady(ServerPlayer host, BjRoom room) {
		List<RoomSeat> ready = room.seats().stream().filter(RoomSeat::ready).toList();
		if (ready.isEmpty()) {
			host.sendSystemMessage(Component.translatable("minecard.room.need_ready"));
			return;
		}
		long liability = room.maxHouseLiability();
		if (Wallets.balance(host.getUUID(), room.stakeItem()) < liability) {
			host.sendSystemMessage(Component.translatable("minecard.room.host_broke", liability));
			return;
		}
		List<TablePlayer> seated = new ArrayList<>();
		for (RoomSeat seat : ready) {
			seated.add(new TablePlayer(seat.playerId(), seat.displayName(), seat.bet()));
		}
		TableBlackjack table = new TableBlackjack(
			room.roomId(),
			room.hostId(),
			room.stakeItem(),
			seated,
			room.rules()
		);
		room.setTable(table);
		room.setPhase(BjRoom.Phase.PLAYING);
		persist(host.level().getServer());
		refreshTable(room, host.level().getServer());
	}

	public static void onTableClick(ServerPlayer player, String action) {
		roomOf(player.getUUID()).ifPresent(room -> {
			TableBlackjack table = room.table();
			if (table == null || room.phase() != BjRoom.Phase.PLAYING) {
				return;
			}
			UUID id = player.getUUID();
			switch (action) {
				case "hit" -> table.hit(id);
				case "stand" -> table.stand(id);
				case "double" -> table.doubleDown(id);
				case "split" -> table.split(id);
				case "insurance_yes" -> table.takeInsurance(id);
				case "insurance_no" -> table.declineInsurance(id);
				case "dealer_hit" -> table.dealerHit(id);
				case "dealer_stand" -> table.dealerStand(id);
				case "play_again" -> {
					if (table.phase() == TableBlackjack.Phase.RESOLVED
						&& !room.hostId().equals(id)
						&& room.seat(id).map(s -> !s.ready()).orElse(false)) {
						PlayAgainConfirmDialog.open(player, room);
						return;
					}
				}
				case "play_again_yes" -> confirmPlayAgain(player, room);
				case "play_again_no" -> {
					if (table.phase() == TableBlackjack.Phase.RESOLVED) {
						TableBlackjackDialog.open(player, room, room.hostId().equals(id));
						return;
					}
				}
				case "deal_next" -> {
					dealNext(player);
					return;
				}
				case "leave" -> leave(player);
				default -> {
				}
			}
			if (PLAYER_ROOM.containsKey(player.getUUID())) {
				refreshTable(room, player.level().getServer());
			}
		});
	}

	private static void confirmPlayAgain(ServerPlayer player, BjRoom room) {
		TableBlackjack table = room.table();
		if (table == null || table.phase() != TableBlackjack.Phase.RESOLVED) {
			return;
		}
		if (room.hostId().equals(player.getUUID())) {
			return;
		}
		room.seat(player.getUUID()).ifPresent(seat -> {
			seat.setBet(room.minBet());
			lockBet(player, room, seat);
			persist(player.level().getServer());
			refreshTable(room, player.level().getServer());
		});
	}

	public static void invite(ServerPlayer host, ServerPlayer target) {
		roomOf(host.getUUID()).ifPresentOrElse(room -> {
			if (!room.hostId().equals(host.getUUID())) {
				host.sendSystemMessage(Component.translatable("minecard.room.invite_not_host"));
				return;
			}
			MutableComponent msg = Component.translatable(
				"minecard.room.invite_msg",
				host.getGameProfile().name(),
				room.roomId()
			).withStyle(ChatFormatting.AQUA);
			MutableComponent join = Component.translatable("minecard.room.click_join")
				.withStyle(Style.EMPTY
					.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent.RunCommand("/minecard join " + room.roomId()))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("minecard.room.join_hover"))));
			target.sendSystemMessage(Component.empty().append(msg).append(" ").append(join));
			host.sendSystemMessage(Component.translatable("minecard.room.invite_sent", target.getGameProfile().name()));
		}, () -> host.sendSystemMessage(Component.translatable("minecard.room.invite_no_room")));
	}

	public static void openLobby(ServerPlayer player) {
		roomOf(player.getUUID()).ifPresentOrElse(
			room -> RoomLobbyDialog.open(player, room),
			() -> MainMenuDialog.open(player)
		);
	}

	/** Admin: force-close the room a player is in (refund escrow). */
	public static boolean adminEnd(ServerPlayer target) {
		return roomOf(target.getUUID()).map(room -> {
			closeRoom(room, target.level().getServer(), "minecard.room.admin_end");
			return true;
		}).orElse(false);
	}

	private static void lockBet(ServerPlayer player, BjRoom room, RoomSeat seat) {
		if (seat.lock() != null) {
			seat.setReady(true);
			return;
		}
		long bet = Math.max(room.minBet(), Math.min(room.maxBet(),
			seat.bet() > 0 ? seat.bet() : WalletConstants.DEFAULT_BET));
		seat.setBet(bet);
		Optional<EscrowHold> hold = Escrow.lock(
			Wallets.get(),
			new PlayerItemSource(player),
			player.getUUID(),
			room.stakeItem(),
			bet
		);
		if (hold.isEmpty()) {
			player.sendSystemMessage(Component.translatable("minecard.room.bet_fail", bet));
			seat.setReady(false);
			return;
		}
		seat.setLock(hold.get());
		seat.setReady(true);
	}

	private static void unlockBet(ServerPlayer player, RoomSeat seat) {
		EscrowHold hold = seat.lock();
		if (hold != null) {
			Escrow.release(Wallets.get(), new PlayerItemSource(player), hold);
			seat.setLock(null);
		}
	}

	private static void refundSeat(BjRoom room, UUID playerId, ServerPlayer online) {
		room.seat(playerId).ifPresent(seat -> {
			EscrowHold hold = seat.lock();
			if (hold != null) {
				ItemSource items = online != null ? new PlayerItemSource(online) : ItemSource.empty();
				Escrow.release(Wallets.get(), items, hold);
				seat.setLock(null);
			}
		});
	}

	private static void closeRoom(BjRoom room, MinecraftServer server, String reasonKey) {
		for (RoomSeat seat : room.seats()) {
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			refundSeat(room, seat.playerId(), p);
			PLAYER_ROOM.remove(seat.playerId());
			if (p != null) {
				Dialogs.clear(p);
				p.sendSystemMessage(Component.translatable(reasonKey));
			}
		}
		PLAYER_ROOM.remove(room.hostId());
		BY_ID.remove(room.roomId());
		persist(server);
		ServerPlayer host = server.getPlayerList().getPlayer(room.hostId());
		if (host != null) {
			Dialogs.clear(host);
			host.sendSystemMessage(Component.translatable(reasonKey));
			MainMenuDialog.open(host);
		}
	}

	private static void refreshLobby(BjRoom room, MinecraftServer server) {
		if (server == null) {
			return;
		}
		ServerPlayer host = server.getPlayerList().getPlayer(room.hostId());
		if (host != null) {
			RoomLobbyDialog.open(host, room);
		}
		for (RoomSeat seat : room.seats()) {
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			if (p != null) {
				RoomLobbyDialog.open(p, room);
			}
		}
	}

	private static void refreshTable(BjRoom room, MinecraftServer server) {
		if (server == null) {
			return;
		}
		TableBlackjack table = room.table();
		if (table == null) {
			return;
		}
		if (table.phase() == TableBlackjack.Phase.RESOLVED) {
			clearConsumedLocks(room);
		}
		ServerPlayer host = server.getPlayerList().getPlayer(room.hostId());
		if (host != null) {
			TableBlackjackDialog.open(host, room, true);
		}
		// Keep every seated player on the table UI (including those sitting out this hand).
		for (RoomSeat seat : room.seats()) {
			ServerPlayer p = server.getPlayerList().getPlayer(seat.playerId());
			if (p != null) {
				TableBlackjackDialog.open(p, room, false);
			}
		}
	}

	/** Escrow already settled into wallets; drop lock markers once per resolved table. */
	private static void clearConsumedLocks(BjRoom room) {
		if (room.postSettleCleared()) {
			return;
		}
		for (RoomSeat seat : room.seats()) {
			seat.setLock(null);
			seat.setReady(false);
			seat.setBet(room.minBet());
		}
		room.setPostSettleCleared(true);
	}

	private static void tick(MinecraftServer server) {
		for (BjRoom room : List.copyOf(BY_ID.values())) {
			if (room.phase() != BjRoom.Phase.PLAYING || room.table() == null) {
				continue;
			}
			TableBlackjack table = room.table();
			boolean anim = table.tick();
			boolean timer = table.tickTurnTimer();
			boolean dirty = table.consumeDirty();
			if (anim || timer || dirty) {
				refreshTable(room, server);
			}
		}
	}

	private static void broadcastPublic(MinecraftServer server, BjRoom room) {
		if (server == null || !room.publicRoom()) {
			return;
		}
		MutableComponent line = Component.translatable(
			"minecard.room.public_chat",
			room.hostName(),
			room.roomId(),
			StakeItem.amountLine(room.stakeItem(), room.minBet())
		).withStyle(ChatFormatting.GOLD);
		MutableComponent code = Component.translatable("minecard.room.public_code", room.roomId())
			.withStyle(ChatFormatting.YELLOW);
		String cmd = "/minecard join " + room.roomId();
		MutableComponent join = Component.translatable("minecard.room.click_join")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand(cmd))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("minecard.room.join_hover"))));
		server.getPlayerList().broadcastSystemMessage(
			Component.empty().append(line).append(" ").append(code).append(" ").append(join),
			false
		);
	}
}
