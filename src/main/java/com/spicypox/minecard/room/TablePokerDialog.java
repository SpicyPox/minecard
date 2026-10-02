package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.game.poker.PokerActionStatus;
import com.spicypox.minecard.game.poker.PokerSeat;
import com.spicypox.minecard.game.poker.TablePoker;
import com.spicypox.minecard.ui.CardGrid;
import com.spicypox.minecard.ui.CardLayer;
import com.spicypox.minecard.ui.DialogUi;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TablePokerDialog {
	public static final Identifier FOLD = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/fold");
	public static final Identifier CHECK_CALL = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/check_call");
	public static final Identifier RAISE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/raise");
	public static final Identifier ALL_IN = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/all_in");
	public static final Identifier RAISE_MIN = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/raise_min");
	public static final Identifier RAISE_HALF = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/raise_half");
	public static final Identifier RAISE_POT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/raise_pot");
	public static final Identifier DEAL_NEXT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/deal_next");
	public static final Identifier LEAVE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/leave");

	private TablePokerDialog() {
	}

	public static void open(ServerPlayer player, PokerRoom room) {
		player.openDialog(Holder.direct(build(player, room)));
	}

	public static MultiActionDialog build(ServerPlayer player, PokerRoom room) {
		TablePoker table = room.table();
		UUID me = player.getUUID();
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = new ArrayList<>();

		body.add(new PlainMessage(
			Component.translatable(
				"minecard.poker.table.hud",
				room.roomId(),
				streetKey(table.phase()),
				table.pot(),
				table.seat(me).map(PokerSeat::stack).orElse(0L),
				room.rules().smallBlind(),
				room.rules().bigBlind(),
				table.toCall(me),
				table.minRaiseTo(me)
			),
			CardLayer.DIALOG_WIDTH
		));

		List<Card> boardCards = table.board();
		if (boardCards.isEmpty()) {
			body.add(new PlainMessage(Component.translatable("minecard.poker.table.board_empty"), CardLayer.DIALOG_WIDTH));
		} else {
			body.addAll(CardGrid.allFaceUp(
				Component.translatable("minecard.table.board"),
				boardCards
			).toBodies());
		}

		PokerSeat mySeat = null;
		for (PokerSeat seat : table.seats()) {
			if (seat.playerId().equals(me)) {
				mySeat = seat;
				continue;
			}
			boolean show = table.phase() == TablePoker.Phase.SHOWDOWN
				|| table.phase() == TablePoker.Phase.HAND_OVER
				|| seat.folded();
			Component label = Component.translatable(
				"minecard.poker.table.seat",
				seat.name(),
				seat.stack(),
				statusLabel(seat)
			);
			if (seat.hole().isEmpty()) {
				body.add(new PlainMessage(label, CardLayer.DIALOG_WIDTH));
			} else if (show && !seat.folded()) {
				body.addAll(CardGrid.allFaceUp(label, seat.hole()).toBodies());
			} else {
				body.addAll(CardGrid.mixed(label, seat.hole(), false, false).toBodies());
			}
		}
		if (mySeat != null) {
			Component label = Component.translatable(
				"minecard.poker.table.you",
				mySeat.stack(),
				statusLabel(mySeat)
			);
			if (mySeat.hole().isEmpty()) {
				body.add(new PlainMessage(label, CardLayer.DIALOG_WIDTH));
			} else {
				body.addAll(CardGrid.allFaceUp(label, mySeat.hole()).toBodies());
			}
		}

		List<ActionButton> actions = new ArrayList<>();
		List<Input> inputs = List.of();
		boolean myTurn = me.equals(table.actingPlayerId()) && table.bettingOpen();
		boolean host = room.hostId().equals(me);
		if (table.phase() == TablePoker.Phase.HAND_OVER) {
			if (host) {
				actions.add(btn("minecard.table.deal_next", DEAL_NEXT, w));
			} else {
				actions.add(btn("minecard.bj.wait", Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/wait"), w));
			}
		} else if (myTurn) {
			long toCall = table.toCall(me);
			actions.add(btn("minecard.poker.fold", FOLD, w));
			actions.add(btn(toCall > 0 ? "minecard.poker.call" : "minecard.poker.check", CHECK_CALL, w));
			actions.add(btn("minecard.poker.raise", RAISE, w));
			actions.add(btn("minecard.poker.all_in", ALL_IN, w));
			actions.add(btn("minecard.poker.raise_min", RAISE_MIN, w));
			actions.add(btn("minecard.poker.raise_half", RAISE_HALF, w));
			actions.add(btn("minecard.poker.raise_pot", RAISE_POT, w));
			long defRaise = Math.max(table.minRaiseTo(me), table.toCall(me));
			inputs = List.of(new Input(
				"raise_amount",
				new TextInput(
					w,
					Component.translatable("minecard.poker.raise_amount"),
					true,
					Long.toString(defRaise),
					10,
					Optional.empty()
				)
			));
		} else {
			actions.add(btn("minecard.bj.wait", Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table_poker/wait"), w));
		}

		ActionButton leave = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.bj.leave"), w),
			Optional.of(new CustomAll(LEAVE, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.poker.table.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			inputs
		);
		return new MultiActionDialog(data, actions, Optional.of(leave), 2);
	}

	private static Component streetKey(TablePoker.Phase phase) {
		String key = switch (phase) {
			case PREFLOP -> "minecard.poker.street.preflop";
			case FLOP -> "minecard.poker.street.flop";
			case TURN -> "minecard.poker.street.turn";
			case RIVER -> "minecard.poker.street.river";
			case SHOWDOWN -> "minecard.poker.street.showdown";
			case HAND_OVER -> "minecard.poker.street.over";
		};
		return Component.translatable(key);
	}

	private static Component statusLabel(PokerSeat seat) {
		PokerActionStatus st = seat.status();
		String key = switch (st) {
			case WAITING -> "minecard.poker.status.waiting";
			case BLIND -> "minecard.poker.status.blind";
			case CHECK -> "minecard.poker.status.check";
			case CALL -> "minecard.poker.status.call";
			case BET -> "minecard.poker.status.bet";
			case RAISE -> "minecard.poker.status.raise";
			case FOLD -> "minecard.poker.status.fold";
			case ALL_IN -> "minecard.poker.status.all_in";
			case WIN -> "minecard.poker.status.win";
			case LOSE -> "minecard.poker.status.lose";
		};
		if (st == PokerActionStatus.BET || st == PokerActionStatus.RAISE || st == PokerActionStatus.BLIND) {
			return Component.translatable(key, seat.streetCommitted());
		}
		return Component.translatable(key);
	}

	private static ActionButton btn(String key, Identifier id, int w) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), w),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
