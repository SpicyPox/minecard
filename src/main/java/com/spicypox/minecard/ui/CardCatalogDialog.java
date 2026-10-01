package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.CardGlyphs;
import com.spicypox.minecard.card.Suit;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CardCatalogDialog {
	private CardCatalogDialog() {
	}

	public static void open(ServerPlayer player) {
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(
			Component.translatable("minecard.cards.intro", CardGlyphs.count()),
			300
		));

		for (Suit suit : Suit.values()) {
			MutableComponent line = Component.empty();
			boolean first = true;
			for (Card card : Card.standard52()) {
				if (card.suit() != suit) {
					continue;
				}
				if (!first) {
					line.append(Component.literal(" "));
				}
				first = false;
				line.append(CardGlyphs.glyph(card));
			}
			body.add(new PlainMessage(
				Component.empty()
					.append(Component.translatable("minecard.cards.suit." + suit.id()))
					.append(Component.literal("\n"))
					.append(line),
				300
			));
		}

		MutableComponent legend = Component.empty();
		boolean first = true;
		for (Card card : Card.standard52()) {
			if (!first) {
				legend.append(Component.literal("\n"));
			}
			first = false;
			legend.append(CardGlyphs.labeled(card));
		}
		body.add(new PlainMessage(legend, 300));

		ActionButton ok = new ActionButton(
			new CommonButtonData(Component.translatable("gui.ok"), 150),
			Optional.empty()
		);

		CommonDialogData data = new CommonDialogData(
			Component.translatable("minecard.cards.title"),
			Optional.empty(),
			true,
			false,
			DialogAction.CLOSE,
			List.copyOf(body),
			List.of()
		);

		player.openDialog(Holder.direct(new NoticeDialog(data, ok)));
	}
}
