package com.spicypox.minecard;

import com.spicypox.minecard.card.CardGlyphs;
import com.spicypox.minecard.command.MinecardCommands;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.pack.CardPackOffers;
import com.spicypox.minecard.ui.CardTableLoop;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Minecard implements ModInitializer {
	public static final String MOD_ID = "minecard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CardGlyphs.bootstrap();
		CardPackOffers.register();
		CardTableLoop.register();
		BlackjackGames.register();
		CommandRegistrationCallback.EVENT.register(MinecardCommands::register);
		LOGGER.info("Minecard loaded (cards GUI + solo blackjack)");
	}
}
