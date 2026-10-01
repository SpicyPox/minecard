package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.body.PlainMessage;

import java.util.Arrays;
import java.util.List;

/**
 * One horizontal seat/board row: label + cards in a tidy line.
 */
public record CardGrid(Component label, List<Card> cards, boolean[] faceUp) {
	public CardGrid {
		cards = List.copyOf(cards);
		faceUp = Arrays.copyOf(faceUp, faceUp.length);
		if (faceUp.length != cards.size()) {
			throw new IllegalArgumentException("faceUp length must match cards");
		}
	}

	public static CardGrid allFaceUp(Component label, List<Card> cards) {
		boolean[] flags = new boolean[cards.size()];
		Arrays.fill(flags, true);
		return new CardGrid(label, cards, flags);
	}

	public static CardGrid mixed(Component label, List<Card> cards, boolean... faceUp) {
		return new CardGrid(label, cards, faceUp);
	}

	public PlainMessage toBody() {
		// Blank line under the label so tall bitmap glyphs don't paint over the title.
		MutableComponent text = Component.empty()
			.append(label)
			.append(Component.literal("\n\n"))
			.append(CardLayer.handLine(cards, faceUp));
		return new PlainMessage(text, CardLayer.DIALOG_WIDTH);
	}
}
