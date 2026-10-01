package com.spicypox.minecard.gametest;

import com.spicypox.minecard.card.CardGlyphs;
import com.spicypox.minecard.ui.CardCatalogDialog;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

import java.util.HashSet;
import java.util.Set;

public final class CardCatalogGameTest {
	@GameTest(maxTicks = 40)
	public void openCardsDialogShows52Faces(GameTestHelper helper) {
		helper.assertTrue(CardGlyphs.count() == 52, "expected 52 loaded face glyphs");

		var glyphs = CardCatalogDialog.faceGlyphs();
		helper.assertTrue(glyphs.size() == 52, "catalog must expose 52 face glyphs");

		Set<String> chars = new HashSet<>();
		for (Component glyph : glyphs) {
			String plain = glyph.getString();
			helper.assertTrue(plain.length() == 1, "glyph must be one character");
			helper.assertTrue(chars.add(plain), "duplicate face glyph: " + plain);
			helper.assertTrue(CardGlyphs.FONT.equals(glyph.getStyle().getFont()), "glyph must use minecard:cards font");
		}
		helper.assertTrue(chars.size() == 52, "expected 52 unique face codepoints");

		// Mock players have no connection; assert the notice payload instead of openDialog.
		NoticeDialog notice = CardCatalogDialog.buildNotice();
		helper.assertTrue(notice.common().body().size() >= 5, "intro + 4 suit rows (+ legend)");
		int faceMentions = 0;
		for (DialogBody part : notice.common().body()) {
			if (part instanceof PlainMessage message) {
				String text = message.contents().getString();
				for (String ch : chars) {
					if (text.contains(ch)) {
						faceMentions++;
					}
				}
			}
		}
		helper.assertTrue(faceMentions >= 52, "dialog body must include all 52 face glyphs");
		helper.succeed();
	}
}
