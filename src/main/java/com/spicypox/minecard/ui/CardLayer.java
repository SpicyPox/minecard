package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.Card;
import com.spicypox.minecard.card.CardGlyphs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared glyph layout helpers for dialog hands.
 * Prefer one horizontal row per seat (set {@code perRow} to the hand size).
 */
public final class CardLayer {
	public static final int DIALOG_WIDTH = 400;

	private CardLayer() {
	}

	public static MutableComponent face(Card card) {
		return Component.empty().append(CardGlyphs.glyph(card));
	}

	public static MutableComponent back() {
		return Component.empty().append(CardGlyphs.back());
	}

	public static MutableComponent glyph(Card card, boolean faceUp) {
		return faceUp ? face(card) : back();
	}

	/** One horizontal row; every card uses the same face-up flag. */
	public static MutableComponent row(List<Card> cards, boolean faceUp) {
		boolean[] flags = new boolean[cards.size()];
		for (int i = 0; i < flags.length; i++) {
			flags[i] = faceUp;
		}
		return row(cards, flags);
	}

	/** One horizontal row with per-card face-up flags (e.g. dealer hole). */
	public static MutableComponent row(List<Card> cards, boolean[] faceUp) {
		if (faceUp.length != cards.size()) {
			throw new IllegalArgumentException("faceUp length must match cards");
		}
		MutableComponent line = Component.empty();
		for (int i = 0; i < cards.size(); i++) {
			if (i > 0) {
				line.append(Component.literal(" "));
			}
			line.append(glyph(cards.get(i), faceUp[i]));
		}
		return line;
	}

	public static List<MutableComponent> wrap(List<Card> cards, int perRow, boolean faceUp) {
		if (perRow < 1) {
			throw new IllegalArgumentException("perRow must be >= 1");
		}
		List<MutableComponent> rows = new ArrayList<>();
		for (int i = 0; i < cards.size(); i += perRow) {
			int end = Math.min(i + perRow, cards.size());
			rows.add(row(cards.subList(i, end), faceUp));
		}
		if (rows.isEmpty()) {
			rows.add(Component.literal("—"));
		}
		return rows;
	}

	public static MutableComponent handBlock(List<Card> cards, int perRow, boolean faceUp) {
		MutableComponent block = Component.empty();
		List<MutableComponent> rows = wrap(cards, perRow, faceUp);
		for (int i = 0; i < rows.size(); i++) {
			if (i > 0) {
				block.append(Component.literal("\n"));
			}
			block.append(rows.get(i));
		}
		return block;
	}

	/** Single-line hand with mixed visibility (no wrap). */
	public static MutableComponent handLine(List<Card> cards, boolean[] faceUp) {
		if (cards.isEmpty()) {
			return Component.literal("—");
		}
		return row(cards, faceUp);
	}
}
