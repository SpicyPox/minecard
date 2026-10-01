package com.spicypox.minecard;

import com.spicypox.minecard.card.CardGlyphs;
import com.spicypox.minecard.command.MinecardCommands;
import com.spicypox.minecard.config.MinecardConfig;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.history.HistoryDb;
import com.spicypox.minecard.pack.CardPackOffers;
import com.spicypox.minecard.room.Rooms;
import com.spicypox.minecard.ui.CardTableLoop;
import com.spicypox.minecard.wallet.Wallets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Minecard implements ModInitializer {
	public static final String MOD_ID = "minecard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MinecardConfig.load();
		CardGlyphs.bootstrap();
		Wallets.register();
		HistoryDb.register();
		Rooms.register();
		CardPackOffers.register();
		CardTableLoop.register();
		BlackjackGames.register();
		CommandRegistrationCallback.EVENT.register(MinecardCommands::register);
		LOGGER.info("Minecard loaded (wallet, history, rooms, blackjack)");
	}
}
