package com.spicypox.minecard.ui;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.history.HistoryDb;
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
import java.util.Map;
import java.util.Optional;

/** Player profile: BJ record, readable net by item, recent hands. */
public final class ProfileDialog {
	public static final Identifier BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "profile/back");

	private ProfileDialog() {
	}

	public static void open(ServerPlayer player) {
		player.openDialog(Holder.direct(buildSummary(player)));
	}

	public static void openHand(ServerPlayer player, String handId) {
		player.openDialog(Holder.direct(buildHand(player, handId)));
	}

	public static void onClick(ServerPlayer player, String action) {
		if ("back".equals(action)) {
			MainMenuDialog.open(player);
			return;
		}
		if (action.startsWith("hand/")) {
			openHand(player, action.substring("hand/".length()));
			return;
		}
		open(player);
	}

	private static MultiActionDialog buildSummary(ServerPlayer player) {
		List<DialogBody> body = new ArrayList<>();
		HistoryDb.StatsSummary stats = HistoryDb.stats(player.getUUID())
			.orElse(new HistoryDb.StatsSummary(0, 0, 0, 0, 0, "{}"));
		body.add(new PlainMessage(
			Component.translatable(
				"minecard.profile.stats",
				stats.handsPlayed(),
				stats.wins(),
				stats.losses(),
				stats.pushes(),
				stats.blackjacks()
			),
			CardLayer.DIALOG_WIDTH
		));

		body.add(new PlainMessage(
			Component.translatable("minecard.profile.net_title"),
			CardLayer.DIALOG_WIDTH
		));
		List<Map.Entry<Identifier, Long>> bals = Wallets.listBalances(player.getUUID());
		if (bals.isEmpty()) {
			body.add(new PlainMessage(
				Component.translatable("minecard.profile.net_empty"),
				CardLayer.DIALOG_WIDTH
			));
		} else {
			for (Map.Entry<Identifier, Long> e : bals) {
				body.add(new PlainMessage(
					Component.translatable(
						"minecard.profile.net_line",
						StakeItem.displayName(e.getKey()),
						e.getValue()
					),
					CardLayer.DIALOG_WIDTH
				));
			}
		}

		appendNetFromStats(body, stats.netByItemJson());

		body.add(new PlainMessage(
			Component.translatable("minecard.profile.recent_hands"),
			CardLayer.DIALOG_WIDTH
		));

		int w = DialogUi.BUTTON_WIDTH;
		List<ActionButton> actions = new ArrayList<>();
		for (HistoryDb.HandRow row : HistoryDb.recentHands(player.getUUID(), 10)) {
			Identifier id = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "profile/hand/" + row.handId());
			actions.add(new ActionButton(
				new CommonButtonData(
					Component.translatable(
						"minecard.profile.hand_row",
						row.outcome(),
						row.bet(),
						row.payout()
					),
					w
				),
				Optional.of(new CustomAll(id, Optional.empty()))
			));
		}
		if (actions.isEmpty()) {
			body.add(new PlainMessage(
				Component.translatable("minecard.profile.no_hands"),
				CardLayer.DIALOG_WIDTH
			));
		}

		ActionButton back = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.profile.back"), w),
			Optional.of(new CustomAll(BACK, Optional.empty()))
		);
		actions.add(back);
		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.profile.title"),
			Optional.empty(),
			true,
			true,
			DialogAction.CLOSE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, actions, Optional.of(back), 1);
	}

	private static void appendNetFromStats(List<DialogBody> body, String netJson) {
		if (netJson == null || netJson.isBlank() || "{}".equals(netJson.trim())) {
			return;
		}
		body.add(new PlainMessage(
			Component.translatable("minecard.profile.lifetime_net", netJson),
			CardLayer.DIALOG_WIDTH
		));
	}

	private static MultiActionDialog buildHand(ServerPlayer player, String handId) {
		List<DialogBody> body = new ArrayList<>();
		Optional<HistoryDb.HandDetail> detail = HistoryDb.handDetail(player.getUUID(), handId);
		if (detail.isEmpty()) {
			body.add(new PlainMessage(
				Component.translatable("minecard.profile.hand_missing"),
				CardLayer.DIALOG_WIDTH
			));
		} else {
			HistoryDb.HandDetail d = detail.get();
			body.add(new PlainMessage(
				Component.translatable(
					"minecard.profile.hand_detail",
					d.outcome(),
					d.bet(),
					d.payout()
				),
				CardLayer.DIALOG_WIDTH
			));
			body.add(new PlainMessage(
				Component.translatable("minecard.profile.hand_you", d.playerCardsJson()),
				CardLayer.DIALOG_WIDTH
			));
			body.add(new PlainMessage(
				Component.translatable("minecard.profile.hand_dealer", d.dealerCardsJson()),
				CardLayer.DIALOG_WIDTH
			));
		}
		int w = DialogUi.BUTTON_WIDTH;
		ActionButton back = new ActionButton(
			new CommonButtonData(Component.translatable("minecard.profile.back"), w),
			Optional.of(new CustomAll(
				Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "profile/summary"),
				Optional.empty()
			))
		);
		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.profile.hand_title"),
			Optional.empty(),
			true,
			true,
			DialogAction.CLOSE,
			List.copyOf(body),
			List.of()
		);
		return new MultiActionDialog(data, List.of(back), Optional.of(back), 1);
	}
}
