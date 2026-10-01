package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.CardGlyphs;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /minecard cards [poker|blackjack]} — 5-row horizontal table showcase.
 */
public final class CardCatalogDialog {
	public enum Mode {
		POKER,
		BLACKJACK
	}

	private CardCatalogDialog() {
	}

	public static void open(ServerPlayer player) {
		open(player, Mode.POKER);
	}

	public static void open(ServerPlayer player, Mode mode) {
		player.openDialog(Holder.direct(buildNotice(mode)));
	}

	public static NoticeDialog buildNotice() {
		return buildNotice(Mode.POKER);
	}

	public static NoticeDialog buildNotice(Mode mode) {
		return switch (mode) {
			case POKER -> TableLayout.build(
				Component.translatable("minecard.cards.title.poker"),
				Component.translatable("minecard.cards.intro.poker", CardGlyphs.count()),
				GameTableShowcases.poker(GameTableShowcases.SEED)
			);
			case BLACKJACK -> TableLayout.build(
				Component.translatable("minecard.cards.title.blackjack"),
				Component.translatable("minecard.cards.intro.blackjack", CardGlyphs.count()),
				GameTableShowcases.blackjack(GameTableShowcases.SEED)
			);
		};
	}

	/** One glyph Component per standard card — used by tests. */
	public static List<Component> faceGlyphs() {
		List<Component> glyphs = new ArrayList<>(52);
		for (Card card : Card.standard52()) {
			glyphs.add(CardGlyphs.glyph(card));
		}
		return glyphs;
	}
}
