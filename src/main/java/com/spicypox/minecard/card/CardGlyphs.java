package com.spicypox.minecard.card;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spicypox.minecard.Minecard;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CardGlyphs {
	public static final Identifier FONT_ID = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "cards");
	public static final FontDescription FONT = new FontDescription.Resource(FONT_ID);

	private static final Map<String, Integer> CODEPOINTS = new HashMap<>();
	private static final Map<Card, Integer> BY_CARD = new HashMap<>();

	private CardGlyphs() {
	}

	public static void bootstrap() {
		try (var in = CardGlyphs.class.getClassLoader().getResourceAsStream("assets/minecard/cards_codepoints.json")) {
			Objects.requireNonNull(in, "missing assets/minecard/cards_codepoints.json");
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var entry : root.entrySet()) {
				CODEPOINTS.put(entry.getKey(), entry.getValue().getAsInt());
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load card codepoints", e);
		}

		for (Card card : Card.standard52()) {
			Integer cp = CODEPOINTS.get(card.textureKey());
			if (cp == null) {
				throw new IllegalStateException("Missing codepoint for " + card.textureKey());
			}
			BY_CARD.put(card, cp);
		}

		if (BY_CARD.size() != 52) {
			throw new IllegalStateException("Expected 52 card glyphs, got " + BY_CARD.size());
		}
		Minecard.LOGGER.info("Loaded {} card glyphs (+ back/jokers in font)", BY_CARD.size());
	}

	public static Component glyph(Card card) {
		int cp = BY_CARD.get(card);
		return Component.literal(String.valueOf((char) cp)).withStyle(Style.EMPTY.withFont(FONT));
	}

	public static Component back() {
		int cp = CODEPOINTS.get("back");
		return Component.literal(String.valueOf((char) cp)).withStyle(Style.EMPTY.withFont(FONT));
	}

	public static MutableComponent labeled(Card card) {
		return Component.empty()
			.append(glyph(card))
			.append(Component.literal(" " + card.displayNameVi()));
	}

	public static int count() {
		return BY_CARD.size();
	}
}
