package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One horizontal seat/board row: label + cards.
 * Label and glyphs are separate dialog body elements so tall bitmap cards
 * cannot paint over the title line.
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

	/** Label body + card body (preferred — avoids glyph overlap). */
	public List<DialogBody> toBodies() {
		List<DialogBody> bodies = new ArrayList<>(2);
		bodies.add(new PlainMessage(label, CardLayer.DIALOG_WIDTH));
		// One leading line under the title — enough for glyph ascent without a big gap.
		MutableComponent cardsLine = Component.literal("\n")
			.append(CardLayer.handLine(cards, faceUp));
		bodies.add(new PlainMessage(cardsLine, CardLayer.DIALOG_WIDTH));
		return bodies;
	}

	/** Combined body for simple call sites; prefer {@link #toBodies()}. */
	public PlainMessage toBody() {
		MutableComponent text = Component.empty()
			.append(label)
			.append(Component.literal("\n\n\n\n"))
			.append(CardLayer.handLine(cards, faceUp));
		return new PlainMessage(text, CardLayer.DIALOG_WIDTH);
	}
}
