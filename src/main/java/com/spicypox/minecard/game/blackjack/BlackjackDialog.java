package com.spicypox.minecard.game.blackjack;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.ui.CardGrid;
import com.spicypox.minecard.ui.CardLayer;
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

/**
 * Body = stake icon + status + cards (timer lives in chat).
 * Action grid = game controls only.
 * Footer {@code exit_action} = Leave (also ESC).
 */
public final class BlackjackDialog {
	public static final Identifier HIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/hit");
	public static final Identifier STAND = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/stand");
	public static final Identifier DOUBLE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/double");
	public static final Identifier SPLIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/split");
	public static final Identifier SURRENDER = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/surrender");
	public static final Identifier INSURANCE_YES = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/insurance_yes");
	public static final Identifier INSURANCE_NO = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/insurance_no");
	public static final Identifier AGAIN = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/again");
	public static final Identifier LEAVE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/leave");
	public static final Identifier WAIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/wait");

	private BlackjackDialog() {
	}

	public static void open(ServerPlayer player, BlackjackSession session) {
		session.showDialog();
		player.openDialog(Holder.direct(build(session)));
	}

	public static MultiActionDialog build(BlackjackSession session) {
		List<DialogBody> body = new ArrayList<>();
		Component stakes = stakesLine(session);
		// Keep description maxWidth near text length — wide boxes center the label and
		// leave a big gap after the item icon.
		body.add(StakeItem.iconWithDescription(
			session.stakeItemId(),
			stakes,
			stakesDescWidth(stakes)
		));
		body.add(new PlainMessage(statusLine(session), CardLayer.DIALOG_WIDTH));

		List<com.spicypox.minecard.card.Card> dealerCards = session.visibleDealerCards();
		String dealerKey = session.dealerHoleHidden()
			? "minecard.bj.dealer_hidden"
			: "minecard.bj.dealer_score";
		Object dealerArg = session.dealerHoleHidden() ? "?" : session.visibleDealerScore();
		body.addAll(new CardGrid(
			Component.translatable(dealerKey, dealerArg),
			dealerCards,
			session.dealerFaceUpFlags()
		).toBodies());

		List<PlayerHandSeat> seats = session.seats();
		for (int i = 0; i < seats.size(); i++) {
			PlayerHandSeat seat = seats.get(i);
			Component label;
			if (seats.size() == 1) {
				label = Component.translatable("minecard.bj.player_score", session.visiblePlayerScore(i));
			} else {
				String key = i == session.activeHandIndex() && session.phase() == BlackjackSession.Phase.PLAYER_TURN
					? "minecard.bj.player_hand_active"
					: "minecard.bj.player_hand";
				label = Component.translatable(key, i + 1, session.visiblePlayerScore(i), seat.bet());
			}
			body.addAll(CardGrid.allFaceUp(label, session.visiblePlayerCards(i)).toBodies());
		}

		List<ActionButton> actions = functionButtons(session);
		int columns = Math.min(2, Math.max(1, actions.size()));

		ActionButton leave = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.bj.leave"), 200),
			Optional.of(new CustomAll(LEAVE, Optional.empty()))
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.bj.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.of(leave), columns);
	}

	private static Component stakesLine(BlackjackSession session) {
		String time = session.phase() == BlackjackSession.Phase.PLAYER_TURN
			|| session.phase() == BlackjackSession.Phase.INSURANCE
			? session.turnSecondsLeft() + "s"
			: "—";
		return Component.translatable(
			"minecard.bj.stakes",
			session.totalBet(),
			session.held(),
			session.balance(),
			time
		);
	}

	private static int stakesDescWidth(Component stakes) {
		int approx = 24 + stakes.getString().length() * 6;
		return Math.min(CardLayer.DIALOG_WIDTH - 24, Math.max(140, approx));
	}

	private static List<ActionButton> functionButtons(BlackjackSession session) {
		List<ActionButton> actions = new ArrayList<>();
		switch (session.phase()) {
			case INSURANCE -> {
				if (session.canTakeInsurance()) {
					actions.add(button("minecard.bj.insurance_yes", INSURANCE_YES, 150));
				}
				actions.add(button("minecard.bj.insurance_no", INSURANCE_NO, 150));
			}
			case PLAYER_TURN -> {
				actions.add(button("minecard.bj.hit", HIT, 150));
				actions.add(button("minecard.bj.stand", STAND, 150));
				if (session.canDouble()) {
					actions.add(button("minecard.bj.double", DOUBLE, 150));
				}
				if (session.canSplit()) {
					actions.add(button("minecard.bj.split", SPLIT, 150));
				}
				if (session.canSurrender()) {
					actions.add(button("minecard.bj.surrender", SURRENDER, 150));
				}
			}
			case RESOLVED -> actions.add(button("minecard.bj.play_again", AGAIN, 200));
			case DEALING, COLLECTING, DEALER_TURN ->
				actions.add(button("minecard.bj.wait", WAIT, 200));
		}
		return actions;
	}

	private static Component statusLine(BlackjackSession session) {
		return switch (session.phase()) {
			case DEALING -> Component.translatable("minecard.bj.status.dealing");
			case COLLECTING -> Component.translatable("minecard.bj.status.collecting");
			case INSURANCE -> Component.translatable("minecard.bj.status.insurance");
			case DEALER_TURN -> Component.translatable("minecard.bj.status.dealer");
			case PLAYER_TURN -> Component.translatable("minecard.bj.status.playing");
			case RESOLVED -> switch (session.outcome().orElse(BlackjackOutcome.PUSH)) {
				case PLAYER_BLACKJACK -> Component.translatable("minecard.bj.result.blackjack");
				case WIN, DEALER_BUST -> Component.translatable("minecard.bj.result.win");
				case LOSE, PLAYER_BUST -> Component.translatable("minecard.bj.result.lose");
				case SURRENDER -> Component.translatable("minecard.bj.result.surrender");
				case PUSH -> Component.translatable("minecard.bj.result.push");
			};
		};
	}

	private static ActionButton button(String langKey, Identifier actionId, int width) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(langKey), width),
			Optional.of(new CustomAll(actionId, Optional.empty()))
		);
	}
}
