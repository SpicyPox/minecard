package com.spicypox.minecard.ui;

import com.spicypox.minecard.card.CardGlyphs;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardCatalogDialogTest {
	@BeforeAll
	static void boot() {
		CardGlyphs.bootstrap();
	}

	@Test
	void catalogExposes52DistinctFaceGlyphs() {
		var glyphs = CardCatalogDialog.faceGlyphs();
		assertEquals(52, glyphs.size());

		Set<String> chars = new HashSet<>();
		for (Component glyph : glyphs) {
			String plain = glyph.getString();
			assertEquals(1, plain.length(), "glyph should be one BMP char: " + plain);
			assertTrue(chars.add(plain), "duplicate glyph char " + plain);
			assertEquals(CardGlyphs.FONT, glyph.getStyle().getFont());
		}
		assertEquals(52, chars.size());
	}
}
