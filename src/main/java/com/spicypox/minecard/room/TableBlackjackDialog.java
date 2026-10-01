package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.TableBlackjack;
import com.spicypox.minecard.game.blackjack.TablePlayer;
import com.spicypox.minecard.ui.CardGrid;
import com.spicypox.minecard.ui.CardLayer;
import com.spicypox.minecard.ui.DialogUi;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TableBlackjackDialog {
	public static final Identifier HIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/hit");
	public static final Identifier STAND = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/stand");
	public static final Identifier DOUBLE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/double");
	public static final Identifier SPLIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/split");
	public static final Identifier INSURANCE_YES = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/insurance_yes");
	public static final Identifier INSURANCE_NO = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/insurance_no");
	public static final Identifier LEAVE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/leave");

	private TableBlackjackDialog() {
	}

	public static void open(ServerPlayer player, BjRoom room, boolean hostView) {
		player.openDialog(Holder.direct(build(player, room, hostView)));
	}

	public static MultiActionDialog build(ServerPlayer player, BjRoom room, boolean hostView) {
		TableBlackjack table = room.table();
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(
			Component.translatable(
				"minecard.table.hud",
				room.roomId(),
				table.phase().name(),
				Wallets.balance(player.getUUID(), room.stakeItem())
			),
			CardLayer.DIALOG_WIDTH
		));

		String dealerKey = table.dealerHoleHidden() ? "minecard.bj.dealer_hidden" : "minecard.bj.dealer_score";
		Object dealerArg = table.dealerHoleHidden() ? "?" : table.visibleDealerScore();
		body.addAll(new CardGrid(
			Component.translatable(dealerKey, dealerArg),
			table.visibleDealerCards(),
			table.dealerFaceUpFlags()
		).toBodies());

		for (TablePlayer tp : table.players()) {
			boolean self = tp.playerId().equals(player.getUUID());
			boolean active = table.phase() == TableBlackjack.Phase.PLAYER_TURN
				&& tp.playerId().equals(table.activePlayerId());
			Component label = Component.translatable(
				active ? "minecard.table.seat_active" : "minecard.table.seat",
				tp.name(),
				tp.seat().hand().score(),
				tp.seat().bet()
			);
			if (self || hostView || table.phase() == TableBlackjack.Phase.RESOLVED) {
				body.addAll(CardGrid.allFaceUp(label, tp.seat().hand().cards()).toBodies());
			} else {
				body.add(new PlainMessage(label, CardLayer.DIALOG_WIDTH));
				body.add(new PlainMessage(Component.translatable("minecard.table.hidden_hand"), CardLayer.DIALOG_WIDTH));
			}
		}

		int w = DialogUi.BUTTON_WIDTH;
		List<ActionButton> actions = new ArrayList<>();
		boolean myIns = !hostView
			&& table.phase() == TableBlackjack.Phase.INSURANCE
			&& player.getUUID().equals(table.activePlayerId());
		boolean myTurn = !hostView
			&& table.phase() == TableBlackjack.Phase.PLAYER_TURN
			&& player.getUUID().equals(table.activePlayerId());
		if (myIns) {
			actions.add(btn("minecard.bj.insurance_yes", INSURANCE_YES, w));
			actions.add(btn("minecard.bj.insurance_no", INSURANCE_NO, w));
		} else if (myTurn) {
			actions.add(btn("minecard.bj.hit", HIT, w));
			actions.add(btn("minecard.bj.stand", STAND, w));
			actions.add(btn("minecard.bj.double", DOUBLE, w));
			actions.add(btn("minecard.bj.split", SPLIT, w));
		} else {
			actions.add(btn("minecard.bj.wait", Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "table/wait"), w));
		}

		ActionButton leave = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.bj.leave"), w),
			Optional.of(new CustomAll(LEAVE, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.table.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.of(leave), 1);
	}

	private static ActionButton btn(String key, Identifier id, int w) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), w),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
