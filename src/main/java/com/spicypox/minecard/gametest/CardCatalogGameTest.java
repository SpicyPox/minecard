package com.spicypox.minecard.gametest;

import com.spicypox.minecard.card.CardGlyphs;
import com.spicypox.minecard.ui.CardCatalogDialog;
import com.spicypox.minecard.ui.CardGrid;
import com.spicypox.minecard.ui.GameTableShowcases;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.NoticeDialog;

import java.util.HashSet;
import java.util.List;
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

		List<CardGrid> poker = GameTableShowcases.poker(GameTableShowcases.SEED);
		helper.assertTrue(poker.size() == 5, "poker: board + 4 players");
		helper.assertTrue(poker.getFirst().cards().size() == 5, "poker board is 5 cards");
		for (int i = 1; i < 5; i++) {
			helper.assertTrue(poker.get(i).cards().size() == 2, "poker player hole is 2");
		}

		List<CardGrid> bj = GameTableShowcases.blackjack(GameTableShowcases.SEED);
		helper.assertTrue(bj.size() == 5, "blackjack: dealer + 4 players");
		helper.assertTrue(bj.getFirst().cards().size() == 2, "dealer has 2 cards");
		helper.assertTrue(bj.getFirst().faceUp()[0] && !bj.getFirst().faceUp()[1], "dealer hole face-down");

		NoticeDialog pokerDialog = CardCatalogDialog.buildNotice(CardCatalogDialog.Mode.POKER);
		helper.assertTrue(pokerDialog.common().body().size() == 6, "intro + 5 grids");
		NoticeDialog bjDialog = CardCatalogDialog.buildNotice(CardCatalogDialog.Mode.BLACKJACK);
		helper.assertTrue(bjDialog.common().body().size() == 6, "intro + 5 grids");
		helper.succeed();
	}
}
