package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.spicypox.minecard.Minecard;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Looped deal / flip / face-down / collect demo.
 * Vanilla has no real 3D flip — we swap face↔back glyphs and re-{@code openDialog} each step.
 */
public final class CardTableLoop {
	public static final int PHASE_SECONDS = 5;
	public static final int PHASE_TICKS = PHASE_SECONDS * 20;
	public static final Identifier STOP_ACTION = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "loop/stop");

	private static final int[] ROW_SIZES = {5, 2, 2, 2, 2};

	private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
	private static boolean registered;

	private CardTableLoop() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(CardTableLoop::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player.getUUID()));
	}

	public static void start(ServerPlayer player) {
		register();
		List<Card> cards = dealOrder(GameTableShowcases.SEED);
		SESSIONS.put(player.getUUID(), new Session(cards));
		push(player, SESSIONS.get(player.getUUID()));
	}

	public static boolean stop(UUID playerId) {
		return SESSIONS.remove(playerId) != null;
	}

	public static boolean isRunning(UUID playerId) {
		return SESSIONS.containsKey(playerId);
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Session session = entry.getValue();
			boolean changed = session.advance();
			if (changed) {
				push(player, session);
			}
		}
	}

	private static void push(ServerPlayer player, Session session) {
		player.openDialog(Holder.direct(session.buildDialog()));
	}

	/** Board 5 then each player 2 — stable showcase order. */
	static List<Card> dealOrder(long seed) {
		List<CardGrid> grids = GameTableShowcases.poker(seed);
		List<Card> order = new ArrayList<>(13);
		for (CardGrid grid : grids) {
			order.addAll(grid.cards());
		}
		return List.copyOf(order);
	}

	enum Phase {
		DEAL("minecard.loop.phase.deal"),
		FLIP("minecard.loop.phase.flip"),
		FACE_DOWN("minecard.loop.phase.facedown"),
		COLLECT("minecard.loop.phase.collect");

		final String langKey;

		Phase(String langKey) {
			this.langKey = langKey;
		}

		Phase next() {
			return switch (this) {
				case DEAL -> FLIP;
				case FLIP -> FACE_DOWN;
				case FACE_DOWN -> COLLECT;
				case COLLECT -> DEAL;
			};
		}
	}

	static final class Session {
		private final List<Card> cards;
		private Phase phase = Phase.DEAL;
		private int phaseTick;
		private int cycle;
		private int lastSignature = Integer.MIN_VALUE;

		Session(List<Card> cards) {
			this.cards = cards;
		}

		/** @return true if the visible table state changed (dialog should refresh) */
		boolean advance() {
			phaseTick++;
			if (phaseTick >= PHASE_TICKS) {
				phaseTick = 0;
				phase = phase.next();
				if (phase == Phase.DEAL) {
					cycle++;
				}
			}
			int signature = phase.ordinal() * 1_000_000 + onTable() * 1_000 + faceUpCount();
			if (signature == lastSignature) {
				return false;
			}
			lastSignature = signature;
			return true;
		}

		/** Cards still on the table (dealt, not collected). */
		int onTable() {
			int n = cards.size();
			return switch (phase) {
				case DEAL -> progressive(phaseTick, n);
				case FLIP, FACE_DOWN -> n;
				case COLLECT -> n - progressive(phaseTick, n);
			};
		}

		/** Among on-table cards (prefix of deal order), how many are face up. */
		int faceUpCount() {
			int n = cards.size();
			return switch (phase) {
				case DEAL -> 0;
				case FLIP -> progressive(phaseTick, n);
				case FACE_DOWN -> n - progressive(phaseTick, n);
				case COLLECT -> 0;
			};
		}

		private static int progressive(int tick, int total) {
			if (total <= 0) {
				return 0;
			}
			return Math.min(total, Math.max(0, (tick + 1) * total / PHASE_TICKS));
		}

		NoticeDialog buildDialog() {
			int onTable = onTable();
			int faceUp = Math.min(faceUpCount(), onTable);

			List<DialogBody> body = new ArrayList<>();
			Component phaseLine = switch (phase) {
				case DEAL, COLLECT -> Component.translatable(phase.langKey, onTable, cards.size());
				case FLIP, FACE_DOWN -> Component.translatable(phase.langKey, onTable, faceUp);
			};
			body.add(new PlainMessage(
				Component.translatable("minecard.loop.intro", cycle + 1, PHASE_SECONDS)
					.append(Component.literal("\n"))
					.append(phaseLine),
				CardLayer.DIALOG_WIDTH
			));
			for (CardGrid grid : toGrids(onTable, faceUp)) {
				body.add(grid.toBody());
			}

			// custom click → mixin stops the session (run_command is unreliable from dialogs).
			ActionButton stop = new ActionButton(
				new CommonButtonData(Component.translatable("minecard.loop.stop"), 150),
				Optional.of(new CustomAll(STOP_ACTION, Optional.empty()))
			);

			CommonDialogData data = new CommonDialogData(
				Component.translatable("minecard.loop.title"),
				Optional.empty(),
				true,
				false,
				DialogAction.CLOSE,
				List.copyOf(body),
				List.of()
			);
			return new NoticeDialog(data, stop);
		}

		private List<CardGrid> toGrids(int onTable, int faceUp) {
			Component[] labels = {
				Component.translatable("minecard.table.board"),
				Component.translatable("minecard.table.player", 1),
				Component.translatable("minecard.table.player", 2),
				Component.translatable("minecard.table.player", 3),
				Component.translatable("minecard.table.player", 4)
			};
			List<CardGrid> grids = new ArrayList<>(5);
			int base = 0;
			for (int g = 0; g < ROW_SIZES.length; g++) {
				int size = ROW_SIZES[g];
				List<Card> slice = new ArrayList<>(size);
				boolean[] flags = new boolean[size];
				int count = 0;
				for (int i = 0; i < size; i++) {
					int global = base + i;
					if (global < onTable) {
						slice.add(cards.get(global));
						flags[count] = global < faceUp;
						count++;
					}
				}
				if (count == 0) {
					grids.add(CardGrid.allFaceUp(labels[g], List.of()));
				} else {
					boolean[] used = new boolean[count];
					System.arraycopy(flags, 0, used, 0, count);
					grids.add(new CardGrid(labels[g], slice, used));
				}
				base += size;
			}
			return grids;
		}
	}
}
