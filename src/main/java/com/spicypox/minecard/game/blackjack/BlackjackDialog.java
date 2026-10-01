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
 * Body = stake icon + HUD + cards.
 * Upper action grid = game controls (2 columns).
 * Lower action row = Leave | Emergency; footer exit = Emergency (ESC).
 */
public final class BlackjackDialog {
	public static final Identifier HIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/hit");
	public static final Identifier STAND = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/stand");
	public static final Identifier DOUBLE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/double");
	public static final Identifier SPLIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/split");
	public static final Identifier AGAIN = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/again");
	public static final Identifier LEAVE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/leave");
	public static final Identifier EMERGENCY = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/emergency");
	public static final Identifier WAIT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/wait");
	/** Filler so Leave|Emergency stay alone on the bottom 2-col row. */
	public static final Identifier PAD = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "bj/pad");

	private BlackjackDialog() {
	}

	public static void open(ServerPlayer player, BlackjackSession session) {
		session.showDialog();
		player.openDialog(Holder.direct(build(session)));
	}

	public static MultiActionDialog build(BlackjackSession session) {
		List<DialogBody> body = new ArrayList<>();
		body.add(StakeItem.iconWithDescription(
			session.stakeItemId(),
			stakesAmounts(session),
			CardLayer.DIALOG_WIDTH
		));
		body.add(new PlainMessage(timerLine(session), CardLayer.DIALOG_WIDTH));
		body.add(new PlainMessage(statusLine(session), CardLayer.DIALOG_WIDTH));

		List<com.spicypox.minecard.card.Card> dealerCards = session.visibleDealerCards();
		boolean[] dealerFlags = new boolean[dealerCards.size()];
		for (int i = 0; i < dealerFlags.length; i++) {
			dealerFlags[i] = !(session.dealerHoleHidden() && i == 1);
		}
		String dealerKey = session.dealerHoleHidden()
			? "minecard.bj.dealer_hidden"
			: "minecard.bj.dealer_score";
		Object dealerArg = session.dealerHoleHidden() ? "?" : session.visibleDealerScore();
		body.add(new CardGrid(
			Component.translatable(dealerKey, dealerArg),
			dealerCards,
			dealerFlags
		).toBody());

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
			body.add(CardGrid.allFaceUp(label, session.visiblePlayerCards(i)).toBody());
		}

		List<ActionButton> actions = new ArrayList<>(functionButtons(session));
		// Pad to even count so Leave|Emergency occupy their own bottom row (2-col grid).
		if (actions.size() % 2 != 0) {
			actions.add(button("minecard.bj.pad", PAD, 100));
		}
		actions.add(button("minecard.bj.leave", LEAVE, 150));
		actions.add(button("minecard.bj.emergency", EMERGENCY, 150));

		// Footer + ESC → emergency (same action as the lower-grid Emergency button).
		ActionButton emergencyExit = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.bj.emergency"), 200),
			Optional.of(new CustomAll(EMERGENCY, Optional.empty()))
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
		return new MultiActionDialog(data, actions, Optional.of(emergencyExit), 2);
	}

	private static Component stakesAmounts(BlackjackSession session) {
		return Component.translatable(
			"minecard.bj.stakes",
			session.totalBet(),
			session.held(),
			session.balance()
		);
	}

	private static Component timerLine(BlackjackSession session) {
		if (session.phase() != BlackjackSession.Phase.PLAYER_TURN) {
			return Component.translatable("minecard.bj.timer.idle");
		}
		return Component.translatable("minecard.bj.timer", session.turnSecondsLeft());
	}

	private static List<ActionButton> functionButtons(BlackjackSession session) {
		List<ActionButton> actions = new ArrayList<>();
		switch (session.phase()) {
			case PLAYER_TURN -> {
				actions.add(button("minecard.bj.hit", HIT, 100));
				actions.add(button("minecard.bj.stand", STAND, 100));
				if (session.canDouble()) {
					actions.add(button("minecard.bj.double", DOUBLE, 100));
				}
				if (session.canSplit()) {
					actions.add(button("minecard.bj.split", SPLIT, 100));
				}
			}
			case RESOLVED -> actions.add(button("minecard.bj.play_again", AGAIN, 150));
			case DEALING, COLLECTING ->
				actions.add(button("minecard.bj.wait", WAIT, 150));
		}
		return actions;
	}

	private static Component statusLine(BlackjackSession session) {
		return switch (session.phase()) {
			case DEALING -> Component.translatable("minecard.bj.status.dealing");
			case COLLECTING -> Component.translatable("minecard.bj.status.collecting");
			case PLAYER_TURN -> Component.translatable("minecard.bj.status.playing");
			case RESOLVED -> switch (session.outcome().orElse(BlackjackOutcome.PUSH)) {
				case PLAYER_BLACKJACK -> Component.translatable("minecard.bj.result.blackjack");
				case WIN, DEALER_BUST -> Component.translatable("minecard.bj.result.win");
				case LOSE, PLAYER_BUST -> Component.translatable("minecard.bj.result.lose");
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
